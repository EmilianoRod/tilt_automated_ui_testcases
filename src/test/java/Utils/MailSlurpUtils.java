package Utils;

import com.mailslurp.apis.EmailControllerApi;
import com.mailslurp.apis.InboxControllerApi;
import com.mailslurp.apis.WaitForControllerApi;
import com.mailslurp.clients.ApiClient;
import com.mailslurp.clients.ApiException;
import com.mailslurp.clients.Configuration;
import com.mailslurp.models.Email;
import com.mailslurp.models.EmailPreview;
import com.mailslurp.models.InboxDto;
import org.testng.SkipException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MailSlurpUtils {

    private static final org.apache.logging.log4j.Logger logger =
            org.apache.logging.log4j.LogManager.getLogger(MailSlurpUtils.class);

    // ---- Defer all SDK objects until we actually need them ----
    private static volatile ApiClient apiClient;
    private static volatile InboxControllerApi inboxController;
    private static volatile EmailControllerApi emailController;
    private static volatile WaitForControllerApi waitForController;

    // cached fingerprint for logs (optional)
    private static volatile String keyFingerprint;


    /** Selected MailSlurp pool index (1..N) when using MAILSLURP_API_KEY_n / MAILSLURP_INBOX_ID_n. */
    private static volatile Integer selectedPoolNumber = null;


    /* ------------------------------------------------------------------ */
    /* Public API                                                         */
    /* ------------------------------------------------------------------ */

    /** Resolve a specific inbox by its UUID. */
    public static InboxDto getInboxById(UUID inboxId) throws ApiException {
        ensureClientReadyOrThrow();
        return inboxController.getInbox(inboxId).execute();
    }

    /** Clear all emails in inbox (best-effort). */
    public static void clearInboxEmails(UUID inboxId) {
        try {
            ensureClientReadyOrThrow();
            inboxController.deleteAllInboxEmails(inboxId).execute();
            if (isDebug()) logger.info("[MailSlurp] Cleared emails for inbox {}", inboxId);
        } catch (ApiException e) {
            throw new RuntimeException("Failed to clear MailSlurp inbox: " + inboxId + " — " + safeMsg(e), e);
        }
    }




    /**
     * Use numbered pool first (MAILSLURP_API_KEY_n + MAILSLURP_INBOX_ID_n).
     * For each slot:
     *   - Try fixed inbox
     *   - If 404 + allowCreate => try createInbox
     *   - If createInbox hits 426/402/429 => move to next slot
     *
     * If no pool slots work, fallback to single-key behavior:
     *   - MAILSLURP_FIXED_INBOX_ID / MAILSLURP_INBOX_ID / Config.getMailSlurpFixedInboxId()
     *   - If fixed fails and creation allowed => createInboxReflectiveWithGuards()
     */
    public static InboxDto resolveFixedOrCreateInbox() throws ApiException {
        final boolean allowCreate = isCreateAllowed();

        // -----------------------------------------------------------------
        // 1) POOL MODE: MAILSLURP_API_KEY_n + MAILSLURP_INBOX_ID_n
        // -----------------------------------------------------------------
        java.util.List<Integer> poolSlots = new ArrayList<>();
        // must match resolveApiKeyFromPoolOrNull() range
        for (int i = 1; i <= 10; i++) {
            String key = Config.getMailSlurpApiKeyByNumber(i);
            if (key != null && !key.isBlank()) {
                poolSlots.add(i);
            }
        }

        if (!poolSlots.isEmpty()) {
            if (isDebug()) {
                logger.info("[MailSlurp][resolve] Pool slots detected: {} (allowCreate={})",
                        poolSlots, allowCreate);
            }

            for (Integer slot : poolSlots) {
                InboxDto inbox = tryResolveInboxForPoolSlot(slot, allowCreate);
                if (inbox != null) {
                    // ✅ success: pool slot selected and global client configured
                    return inbox;
                }
            }

            // If we get here, all pool slots failed (expired / 426 / 401 / etc.)
            logger.warn(
                    "[MailSlurp] No usable pool slot. All MAILSLURP_API_KEY_n / MAILSLURP_INBOX_ID_n " +
                            "combos either expired or hit create limits (426/402/429). " +
                            "Falling back to single-key / legacy resolution if configured."
            );
            // 👈 IMPORTANT: no return / throw here → execution falls through
        }


        // -----------------------------------------------------------------
        // 2) SINGLE-KEY / LEGACY BEHAVIOR (no pool configured)
        // -----------------------------------------------------------------
        ensureClientReadyOrThrow();

        String fixedIdStr = firstNonBlank(
                System.getProperty("MAILSLURP_FIXED_INBOX_ID"),
                System.getenv("MAILSLURP_FIXED_INBOX_ID"),
                System.getProperty("MAILSLURP_INBOX_ID"),
                System.getenv("MAILSLURP_INBOX_ID"),
                Config.getMailSlurpFixedInboxId()
        );

        final boolean hasFixed = isNonBlank(fixedIdStr);

        if (isDebug()) {
            String prefix = hasFixed ? fixedIdStr.trim() : "";
            if (prefix.length() > 8) prefix = prefix.substring(0, 8);
            logger.info(
                    "[MailSlurp][resolve-single] allowCreate={} | fixedIdPresent={} | idPrefix={}",
                    allowCreate,
                    hasFixed,
                    hasFixed ? prefix : "none"
            );
        }

        // 2a) Try fixed inbox (single-key mode)
        if (hasFixed) {
            try {
                UUID id = UUID.fromString(fixedIdStr.trim());
                InboxDto fixed = inboxController.getInbox(id).execute();
                if (isDebug()) {
                    logger.info("[MailSlurp] Using fixed inbox {} <{}>",
                            fixed.getId(), fixed.getEmailAddress());
                }
                return fixed;
            } catch (Exception ex) {
                logger.warn("[MailSlurp] Could not fetch fixed inbox {}: {}", fixedIdStr, safeMsg(ex));
                if (!allowCreate) {
                    throw new SkipException(
                            "MailSlurp fixed inbox \"" + fixedIdStr + "\" unavailable and inbox creation disabled " +
                                    "(set MAILSLURP_FIXED_INBOX_ID / MAILSLURP_INBOX_ID correctly " +
                                    "or enable MAILSLURP_ALLOW_CREATE_INBOX_FALLBACK=true)."
                    );
                }
            }
        } else if (!allowCreate) {
            // 2b) No fixed inbox configured and not allowed to create
            throw new SkipException(
                    "MailSlurp inbox creation disabled and no fixed inbox configured. " +
                            "Set MAILSLURP_FIXED_INBOX_ID / MAILSLURP_INBOX_ID or enable " +
                            "MAILSLURP_ALLOW_CREATE_INBOX_FALLBACK=true."
            );
        }

        // 2c) No usable fixed inbox → try to create (legacy behavior)
        return createInboxReflectiveWithGuards();
    }


    /**
     * Configure the shared ApiClient + controllers for a specific API key.
     * Used by pool slots so that once a slot is chosen, the same client is reused
     * by waitForEmailMatching, listRecentEmails, etc.
     */
    private static void configureClientForKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }

        ApiClient client = Configuration.getDefaultApiClient();
        client.setBasePath(basePath());
        client.setApiKey(apiKey.trim());
        client.setConnectTimeout(30_000);
        client.setReadTimeout(30_000);
        client.setWriteTimeout(30_000);

        apiClient = client;
        inboxController = new InboxControllerApi(client);
        emailController = new EmailControllerApi(client);
        waitForController = new WaitForControllerApi(client);

        keyFingerprint = safeSha12(apiKey.trim());

        if (isDebug()) {
            logger.info("[MailSlurp] Configured client for pool key (fingerprint={})",
                    keyFingerprint);
            try {
                inboxController.getInboxes().size(1).execute();
                logger.info("[MailSlurp] Pool key auth probe OK.");
            } catch (Exception e) {
                logger.warn("[MailSlurp] Pool key auth probe failed: {}", safeMsg(e));
            }
        }
    }

    /**
     * Try to resolve an inbox for a given pool slot:
     *   1) Configure client for MAILSLURP_API_KEY_n
     *   2) Try fixed inbox MAILSLURP_INBOX_ID_n
     *   3) On 404 + allowCreate => try createInbox
     *   4) On 426/402/429 during createInbox => return null (next slot)
     *
     * Returns a usable InboxDto or null if this slot is unusable.
     */
    private static InboxDto tryResolveInboxForPoolSlot(int slot, boolean allowCreate) {
        String apiKey = Config.getMailSlurpApiKeyByNumber(slot);
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }

        try {
            configureClientForKey(apiKey);
        } catch (Exception e) {
            logger.warn("[MailSlurp] Slot #{}: failed to configure client: {}", slot, safeMsg(e));
            return null;
        }

        selectedPoolNumber = slot;

        String fixedIdStr = Config.getMailSlurpInboxIdByNumber(slot);
        boolean hasFixed = isNonBlank(fixedIdStr);

        if (isDebug()) {
            String prefix = hasFixed ? fixedIdStr.trim() : "";
            if (prefix.length() > 8) prefix = prefix.substring(0, 8);
            logger.info("[MailSlurp][slot {}] allowCreate={} | fixedIdPresent={} | idPrefix={}",
                    slot, allowCreate, hasFixed, hasFixed ? prefix : "none");
        }

        // 1) Try fixed inbox for this slot
        if (hasFixed) {
            try {
                UUID id = UUID.fromString(fixedIdStr.trim());
                InboxDto fixed = inboxController.getInbox(id).execute();
                logger.info("[MailSlurp][slot {}] Using fixed inbox {} <{}>",
                        slot, fixed.getId(), fixed.getEmailAddress());
                return fixed;
            } catch (ApiException ex) {
                int code = ex.getCode();
                if (code == 404) {
                    logger.warn("[MailSlurp][slot {}] Fixed inbox {} expired/not found (404).",
                            slot, fixedIdStr);
                } else {
                    logger.warn("[MailSlurp][slot {}] getInbox failed (status={}): {}",
                            slot, code, safeMsg(ex));
                    if (!allowCreate) {
                        // Can't create, so this slot is unusable
                        return null;
                    }
                }
            } catch (Exception ex) {
                logger.warn("[MailSlurp][slot {}] getInbox unexpected error: {}", slot, safeMsg(ex));
                if (!allowCreate) {
                    return null;
                }
            }
        } else if (!allowCreate) {
            logger.warn("[MailSlurp][slot {}] No fixed inbox id and creation not allowed → skip slot.",
                    slot);
            return null;
        }

        // 2) No usable fixed inbox → try to create, if allowed
        if (!allowCreate) {
            return null;
        }

        try {
            InboxDto created = createInboxForCurrentSlotNoReuse();
            if (created != null) {
                logger.info("[MailSlurp][slot {}] Created inbox {} <{}>",
                        slot, created.getId(), created.getEmailAddress());
            }
            return created;
        } catch (ApiException ex) {
            int code = ex.getCode();
            if (code == 426 || code == 402 || code == 429) {
                // 👇 THIS IS THE IMPORTANT PART:
                // Treat plan/quota errors as "slot dead" and move on to next one.
                logger.warn("[MailSlurp][slot {}] createInbox limited (HTTP {}). Trying next slot.",
                        slot, code);
                return null;
            }
            logger.warn("[MailSlurp][slot {}] createInbox failed (status={}): {}",
                    slot, code, safeMsg(ex));
            return null;
        } catch (Exception ex) {
            logger.warn("[MailSlurp][slot {}] createInbox unexpected error: {}",
                    slot, safeMsg(ex));
            return null;
        }
    }

    /**
     * Create an inbox for the *current* client/slot.
     * This variant does NOT try to be clever on 426; it simply throws ApiException
     * so the caller (tryResolveInboxForPoolSlot) can decide whether to move to next slot.
     */
    private static InboxDto createInboxForCurrentSlotNoReuse() throws ApiException {
        try {
            if (isDebug()) {
                logger.info("[MailSlurp] Creating inbox via InboxControllerApi#createInboxWithDefaults (pool slot).");
            }

            Method m = InboxControllerApi.class.getMethod("createInboxWithDefaults");
            Object call = m.invoke(inboxController);
            Method exec = call.getClass().getMethod("execute");
            Object dto = exec.invoke(call);
            return (InboxDto) dto;

        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getTargetException();
            if (cause instanceof ApiException) {
                throw (ApiException) cause;
            }
            throw new RuntimeException("Reflection failed creating MailSlurp inbox for pool slot: " + safeMsg(cause), cause);
        } catch (ReflectiveOperationException roe) {
            throw new RuntimeException("Reflection failed creating MailSlurp inbox for pool slot: " + safeMsg(roe), roe);
        }
    }



    /**
     * Poll inbox until an email matching the predicate arrives, or timeout.
     * If unreadOnly=true, attempts to filter out read messages when API supports it.
     */
    public static Email waitForEmailMatching(
            UUID inboxId,
            long timeoutMillis,
            long pollIntervalMillis,
            boolean unreadOnly,
            java.util.function.Predicate<Email> predicate)
            throws ApiException, InterruptedException {

        Objects.requireNonNull(inboxId, "inboxId must not be null");
        Objects.requireNonNull(predicate, "predicate must not be null");
        if (timeoutMillis <= 0) throw new IllegalArgumentException("timeoutMillis must be > 0");
        if (pollIntervalMillis <= 0) pollIntervalMillis = 1500;

        ensureClientReadyOrThrow();

        final long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try {
                // Pull a small page; MailSlurp sorts newest-first by default
                List<EmailPreview> previews = inboxController.getEmails(inboxId).size(10).execute();
                if (previews != null) {
                    for (EmailPreview p : previews) {
                        // If unreadOnly is requested and preview has a read flag, skip read ones
                        try {
                            Boolean read = null;
                            try {
                                Method gm = p.getClass().getMethod("getRead");
                                Object rv = gm.invoke(p);
                                if (rv instanceof Boolean) read = (Boolean) rv;
                            } catch (NoSuchMethodException ignore) {}
                            if (unreadOnly && Boolean.TRUE.equals(read)) continue;
                        } catch (Exception ignore) {}

                        Email e = emailController.getEmail(p.getId()).execute();

                        // Also enforce unreadOnly at full Email level if getIsRead exists
                        if (unreadOnly) {
                            try {
                                Method gm = e.getClass().getMethod("getIsRead");
                                Object rv = gm.invoke(e);
                                if (rv instanceof Boolean && (Boolean) rv) continue;
                            } catch (NoSuchMethodException ignore) {}
                        }

                        if (predicate.test(e)) return e;
                    }
                }
            } catch (Exception e) {
                logger.warn("[MailSlurp][poll] list/get failed: {}", e.getMessage());
            }
            Thread.sleep(pollIntervalMillis);
        }
        return null;
    }

    /* Predicates / body helpers */
    public static java.util.function.Predicate<Email> subjectContains(String needle) {
        String n = (needle == null) ? "" : needle.toLowerCase(Locale.ROOT);
        return e -> Optional.ofNullable(e.getSubject()).orElse("").toLowerCase(Locale.ROOT).contains(n);
    }

    public static java.util.function.Predicate<Email> bodyContains(String needle) {
        String n = (needle == null) ? "" : needle.toLowerCase(Locale.ROOT);
        return e -> safeEmailBody(e).toLowerCase(Locale.ROOT).contains(n);
    }

    public static String safeEmailBody(Email email) {
        if (email == null) return "";
        String body = Optional.ofNullable(email.getBody()).orElse("");
        String cleaned = body.replaceAll("(?is)<style[^>]*>.*?</style>", "")
                .replaceAll("(?is)<script[^>]*>.*?</script>", "")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.isBlank() ? body : cleaned;
    }

    /* Link helpers */
    public static String extractFirstLink(Email email) {
        if (email == null || email.getBody() == null) return null;
        Matcher matcher = Pattern.compile("https?://\\S+").matcher(email.getBody());
        return matcher.find() ? matcher.group() : null;
    }

    public static String extractLinkByAnchorText(Email email, String anchorText) {
        if (email == null || email.getBody() == null || anchorText == null) return null;
        String pattern = "<a[^>]*href=[\"']([^\"']+)[\"'][^>]*>\\s*" + Pattern.quote(anchorText) + "\\s*</a>";
        Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(email.getBody());
        return m.find() ? m.group(1) : null;
    }

    /* Alias helpers */
    public static String addPlusAlias(String email, String suffix) {
        if (email == null || !email.contains("@")) return email;
        int at = email.indexOf('@');
        return email.substring(0, at) + "+" + suffix + email.substring(at);
    }

    public static String uniqueAliasEmail(InboxDto inbox, String tag) {
        String base = inbox.getEmailAddress();
        String suffix = (tag == null ? "t" : tag) + "-" + System.currentTimeMillis();
        return addPlusAlias(base, suffix);
    }

    public static java.util.function.Predicate<Email> addressedToAliasToken(String aliasToken) {
        String tok = Optional.ofNullable(aliasToken).orElse("").toLowerCase(Locale.ROOT);
        return e -> !tok.isBlank() &&
                Optional.ofNullable(e.getTo()).orElseGet(java.util.ArrayList::new).stream()
                        .filter(Objects::nonNull)
                        .map(s -> s.toLowerCase(Locale.ROOT))
                        .anyMatch(s -> s.contains(tok));
    }

    public static String extractAliasToken(String aliasedEmail) {
        if (aliasedEmail == null) return "";
        int plus = aliasedEmail.indexOf('+');
        int at = aliasedEmail.indexOf('@');
        if (plus > 0 && at > plus) return aliasedEmail.substring(plus, at).toLowerCase(Locale.ROOT);
        return "";
    }

    /** Short fingerprint for logs (12 hex chars). */
    public static String currentKeyFingerprint() { return keyFingerprint == null ? "" : keyFingerprint; }

    /* ------------------------------------------------------------------ */
    /* Internal: lazy client                                              */
    /* ------------------------------------------------------------------ */

    private static void ensureClientReadyOrThrow() {
        if (apiClient != null && inboxController != null && emailController != null && waitForController != null) return;

        synchronized (MailSlurpUtils.class) {
            if (apiClient != null && inboxController != null && emailController != null && waitForController != null) return;

            final String apiKey = resolveApiKeyOrNull();
            if (apiKey == null || apiKey.isBlank()) {
                throw new SkipException("[MailSlurp] API key missing. Provide via mailslurp.apiKey / MAILSLURP_API_KEY / .env.local");
            }

            keyFingerprint = safeSha12(apiKey);

            ApiClient client = Configuration.getDefaultApiClient();
            client.setBasePath(basePath());
            client.setApiKey(apiKey);
            client.setConnectTimeout(30_000);
            client.setReadTimeout(30_000);
            client.setWriteTimeout(30_000);

            apiClient = client;
            inboxController = new InboxControllerApi(client);
            emailController = new EmailControllerApi(client);
            waitForController = new WaitForControllerApi(client);

            if (isDebug()) {
                logger.info("[MailSlurp] key fingerprint: {}", keyFingerprint);
                logger.info("[MailSlurp] basePath: {}", client.getBasePath());
                try {
                    inboxController.getInboxes().size(1).execute();
                    logger.info("[MailSlurp] auth OK.");
                } catch (Exception e) {
                    logger.warn("[MailSlurp] identity probe failed: {}", safeMsg(e));
                }
            }
        }
    }


    private static String resolveApiKeyOrNull() {
        // 1) Existing single-key mechanisms (keep your current precedence)
        String key = firstNonBlank(
                System.getProperty("mailslurp.forceKey"),
                System.getProperty("mailslurp.apiKey"),
                System.getProperty("MAILSLURP_API_KEY"),
                System.getenv("MAILSLURP_API_KEY"),
                Config.getMailSlurpApiKey(),
                Config.getAny("mailslurp.apiKey", "MAILSLURP_API_KEY")
        );

        if (key != null && !key.isBlank()) {
            // ✅ Single-key mode: make sure we don't pretend a pool slot was used
            selectedPoolNumber = null;
        } else {
            // 2) No single key configured → try the numbered pool
            key = resolveApiKeyFromPoolOrNull();
            if (key == null || key.isBlank()) {
                if (isDebug()) {
                    logger.warn("[MailSlurp][Key] No usable API key found (single-key + pool both empty/invalid).");
                }
                selectedPoolNumber = null; // be explicit
                return null; // nothing usable
            }
        }

        key = key.trim();

        // 3) Optional: fingerprint check (keep whatever you already have)
        String expectedFpRaw = firstNonBlank(
                System.getProperty("mailslurp.expectedFingerprint"),
                System.getenv("MAILSLURP_EXPECTED_FINGERPRINT")
        );
        if (isNonBlank(expectedFpRaw)) {
            String actual = safeSha12(key);
            if (!expectedFpRaw.trim().equalsIgnoreCase(actual)) {
                throw new SkipException(
                        "MailSlurp API key mismatch. expected=" + expectedFpRaw + " actual=" + actual
                );
            }
        }

        return key;
    }






    private static String basePath() {
        return firstNonBlank(
                System.getProperty("mailslurp.basePath"),
                System.getenv("MAILSLURP_BASE_PATH"),
                "https://api.mailslurp.com"
        ).trim();
    }

    /**
     * Central toggle for whether test code is allowed to create new inboxes.
     * New high-priority flag: MAILSLURP_ALLOW_CREATE_INBOX_FALLBACK.
     */
    private static boolean isCreateAllowed() {
        String v = firstNonBlank(
                // NEW preferred flag
                System.getProperty("MAILSLURP_ALLOW_CREATE_INBOX_FALLBACK"),
                System.getenv("MAILSLURP_ALLOW_CREATE_INBOX_FALLBACK"),
                // legacy names kept for backwards compatibility
                System.getProperty("ALLOW_CREATE_INBOX_FALLBACK"),
                System.getenv("ALLOW_CREATE_INBOX_FALLBACK"),
                System.getProperty("mailslurp.allowCreate"),
                System.getenv("MAILSLURP_ALLOW_CREATE"),
                Config.getAny("mailslurp.allowCreate", "MAILSLURP_ALLOW_CREATE")
        );
        boolean result = v != null && (v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes"));

        if (isDebug()) {
            logger.info("[MailSlurp][resolve] allowCreate={} (raw={})", result, v);
        }
        return result;
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                            */
    /* ------------------------------------------------------------------ */

    private static boolean isDebug() {
        // Prefer unified source via Config; default true (useful during setup)
        String v = Config.getAny("mailslurp.debug", "MAILSLURP_DEBUG");
        if (v == null) v = "true";
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String s : vals) if (s != null && !s.isBlank()) return s;
        return null;
    }

    private static boolean isNonBlank(String s) { return s != null && !s.isBlank(); }

    /** SHA-256 first 12 hex chars (for safe fingerprint logs). */
    private static String safeSha12(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            String full = sb.toString();
            return full.length() >= 12 ? full.substring(0, 12) : full;
        } catch (Exception e) { return "unknown"; }
    }

    private static String safeMsg(Throwable t) {
        String m = (t == null) ? null : t.getMessage();
        return (m == null || m.isBlank()) ? (t == null ? "null" : t.getClass().getSimpleName()) : m;
    }

    /** Create inbox using reflective call to keep SDK compatibility; fallback to reuse. */
    private static InboxDto createInboxReflectiveWithGuards() throws ApiException {
        if (!isCreateAllowed()) {
            throw new SkipException(
                    "Inbox creation disabled by MAILSLURP_ALLOW_CREATE_INBOX_FALLBACK/ALLOW_CREATE_INBOX_FALLBACK/MAILSLURP_ALLOW_CREATE=false"
            );
        }
        try {
            if (isDebug()) {
                logger.info("[MailSlurp] Creating inbox via InboxControllerApi#createInboxWithDefaults (fallback path).");
            }

            Method m = InboxControllerApi.class.getMethod("createInboxWithDefaults");
            Object call = m.invoke(inboxController);
            Method exec = call.getClass().getMethod("execute");
            Object dto = exec.invoke(call);
            return (InboxDto) dto;
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getTargetException();
            if (cause instanceof ApiException ex) {
                int code = ex.getCode();
                if (isDebug()) logger.info("[MailSlurp] inbox-creation HTTP {}: {}", code, safeMsg(ex));
                if (code == 426 || code == 402 || code == 429) {
                    // Plan/quota issue – try to reuse first inbox if any
                    InboxDto reused = getFirstExistingInboxOrNull();
                    if (reused != null) {
                        logger.warn("[MailSlurp] Inbox creation blocked (HTTP {}). Reusing existing inbox {} <{}>",
                                code, reused.getId(), reused.getEmailAddress());
                        return reused;
                    }
                    if (code == 426) {
                        // For HTTP 426 specifically, treat as Skip to avoid noisy hard failures in CI
                        throw new SkipException("MailSlurp CreateInbox limit (426) and no existing inbox available to reuse.");
                    }
                }
                throw ex;
            }
            throw new RuntimeException("Reflection failed creating MailSlurp inbox: " + safeMsg(cause), cause);
        } catch (ReflectiveOperationException roe) {
            throw new RuntimeException("Reflection failed creating MailSlurp inbox: " + safeMsg(roe), roe);
        }
    }

    private static InboxDto getFirstExistingInboxOrNull() {
        try {
            List<InboxDto> all = inboxController.getInboxes().size(1).execute();
            if (all != null && !all.isEmpty()) return all.get(0);
        } catch (Exception ignored) {}
        return null;
    }

    /* ------------------------------------------------------------------ */
    /* TEST-ONLY accessors (no network, optional)                         */
    /* ------------------------------------------------------------------ */

    // Mirrors resolveApiKeyOrNull() precedence but never initializes the SDK or network.
    static String _resolveApiKeyForTestOnly() {
        String key = firstNonBlank(
                System.getProperty("mailslurp.forceKey"),
                System.getProperty("mailslurp.apiKey"),
                System.getProperty("MAILSLURP_API_KEY"),
                System.getenv("MAILSLURP_API_KEY"),
                Config.getMailSlurpApiKey(),
                Config.getAny("mailslurp.apiKey", "MAILSLURP_API_KEY")
        );
        if (key == null || key.isBlank()) return null;

        String expectedFpRaw = firstNonBlank(
                System.getProperty("mailslurp.expectedFingerprint"),
                System.getenv("MAILSLURP_EXPECTED_FINGERPRINT")
        );
        if (isNonBlank(expectedFpRaw)) {
            String actual = safeSha12(key.trim());
            if (!expectedFpRaw.trim().equalsIgnoreCase(actual)) {
                return "__FINGERPRINT_MISMATCH__";
            }
        }
        return key.trim();
    }

    static String _basePathForTestOnly() {
        return firstNonBlank(
                System.getProperty("mailslurp.basePath"),
                System.getenv("MAILSLURP_BASE_PATH"),
                "https://api.mailslurp.com"
        );
    }




    public static Predicate<Email> subjectOrBodyContainsAny(String... needles) {
        // If no needles provided, accept everything (shouldn't normally happen)
        if (needles == null || needles.length == 0) {
            return e -> true;
        }

        // Normalize all needles to lowercase once
        final java.util.List<String> norm = Arrays.stream(needles)
                .filter(Objects::nonNull)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();

        return e -> {
            if (e == null) return false;

            String subj = Optional.ofNullable(e.getSubject())
                    .orElse("")
                    .toLowerCase(Locale.ROOT);

            String body = safeEmailBody(e).toLowerCase(Locale.ROOT);

            for (String n : norm) {
                if (subj.contains(n) || body.contains(n)) {
                    return true;
                }
            }
            return false;
        };
    }


    /**
     * DEBUG ONLY:
     * List and fetch the most recent emails for an inbox.
     *
     * @param inboxId     MailSlurp inbox UUID
     * @param limit       max number of emails to fetch (defaults to 10 if <= 0)
     * @param unreadOnly  if true, tries to filter only unread messages
     * @return list of full Email objects (newest first)
     */
    public static java.util.List<Email> listRecentEmails(UUID inboxId, int limit, boolean unreadOnly) {
        Objects.requireNonNull(inboxId, "inboxId must not be null");
        if (limit <= 0) limit = 10;

        ensureClientReadyOrThrow();

        java.util.List<Email> result = new ArrayList<>();
        try {
            // MailSlurp returns newest-first by default
            java.util.List<EmailPreview> previews =
                    inboxController.getEmails(inboxId).size(limit).execute();

            if (previews == null || previews.isEmpty()) {
                logger.info("[MailSlurp][debug] Inbox {} has no emails.", inboxId);
                return result;
            }

            for (EmailPreview p : previews) {
                try {
                    // Optional unread filter at preview level
                    if (unreadOnly) {
                        try {
                            Method gm = p.getClass().getMethod("getRead");
                            Object rv = gm.invoke(p);
                            if (rv instanceof Boolean && (Boolean) rv) {
                                continue; // skip read
                            }
                        } catch (NoSuchMethodException ignore) {
                            // preview might not expose read flag; fall through
                        }
                    }

                    Email e = emailController.getEmail(p.getId()).execute();

                    // Optional unread filter at full Email level
                    if (unreadOnly) {
                        try {
                            Method gm = e.getClass().getMethod("getIsRead");
                            Object rv = gm.invoke(e);
                            if (rv instanceof Boolean && (Boolean) rv) {
                                continue; // skip read
                            }
                        } catch (NoSuchMethodException ignore) {
                            // not all SDK versions have getIsRead
                        }
                    }

                    result.add(e);

                    // Log a short summary for debugging
                    logger.info(
                            "[MailSlurp][debug] id={} | from={} | to={} | subj='{}' | snippet='{}'",
                            e.getId(),
                            e.getFrom(),
                            e.getTo(),
                            e.getSubject(),
                            debugSnippet(safeEmailBody(e), 140)
                    );
                } catch (Exception ex) {
                    logger.warn("[MailSlurp][debug] Failed to fetch email {}: {}",
                            p.getId(), safeMsg(ex));
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(
                    "[MailSlurp][debug] Failed to list recent emails for inbox " + inboxId + ": " + safeMsg(ex),
                    ex
            );
        }
        return result;
    }

    /** Small helper for logging body snippets. */
    private static String debugSnippet(String text, int maxLen) {
        if (text == null) return "";
        String trimmed = text.replaceAll("\\s+", " ").trim();
        if (trimmed.length() <= maxLen) return trimmed;
        return trimmed.substring(0, maxLen) + "…";
    }



// BEFO
    private static boolean hasQuotaForKey(String key, int slotIndex) {
        if (key == null || key.isBlank()) {
            if (isDebug()) {
                logger.info("[MailSlurp][Pool] slot #{} → empty / blank key, skipping.", slotIndex);
            }
            return false;
        }

        try {
            // ✅ use a fresh client instead of clone()
            ApiClient tmp = new ApiClient();
            tmp.setBasePath(basePath());
            tmp.setApiKey(key.trim());
            tmp.setConnectTimeout(5_000);
            tmp.setReadTimeout(5_000);
            tmp.setWriteTimeout(5_000);

            InboxControllerApi tmpInbox = new InboxControllerApi(tmp);
            // cheap call just to validate key/quota
            tmpInbox.getInboxes()
                    .size(1)
                    .execute();

            if (isDebug()) {
                logger.info("[MailSlurp][Pool] slot #{} → probe OK (key fp={})",
                        slotIndex, safeSha12(key));
            }
            return true;
        } catch (ApiException ex) {
            int code = ex.getCode();
            if (isDebug()) {
                logger.warn("[MailSlurp][Pool] slot #{} → probe FAILED (HTTP {}): {} → treating as NO-QUOTA",
                        slotIndex, code, safeMsg(ex));
            }
            // 401/402/429 etc => treat as "no quota / invalid"
            return false;
        } catch (Exception e) {
            if (isDebug()) {
                logger.warn("[MailSlurp][Pool] slot #{} → probe ERROR: {} → treating as NO-QUOTA",
                        slotIndex, safeMsg(e));
            }
            return false;
        }
    }




    /**
     * Try numbered pool MAILSLURP_API_KEY_1..10 (or mailslurp.apiKey.1..10)
     * and return the first key that passes the quota probe.
     * Also sets selectedPoolNumber with the chosen index (1..10).
     */
    private static String resolveApiKeyFromPoolOrNull() {
        int poolMax = 10; // keep in sync with resolveFixedOrCreateInbox()
        boolean anyConfigured = false;

        for (int i = 1; i <= poolMax; i++) {
            String candidate = Config.getMailSlurpApiKeyByNumber(i);
            if (candidate == null || candidate.isBlank()) {
                if (isDebug()) {
                    logger.info("[MailSlurp][Pool] slot #{} → no key configured.", i);
                }
                continue;
            }

            anyConfigured = true;
            if (isDebug()) {
                logger.info("[MailSlurp][Pool] slot #{} → found configured key (fp={})",
                        i, safeSha12(candidate));
            }

            if (hasQuotaForKey(candidate, i)) {
                selectedPoolNumber = i;
                if (isDebug()) {
                    logger.info("[MailSlurp][Pool] ✅ SELECTED slot #{} (key fp={})",
                            i, safeSha12(candidate));
                }
                return candidate.trim();
            } else {
                if (isDebug()) {
                    logger.info("[MailSlurp][Pool] slot #{} → rejected (no quota / invalid).", i);
                }
            }
        }

        if (anyConfigured) {
            logger.warn("[MailSlurp][Pool] All configured pool slots were rejected (no usable key). "
                    + "Falling back to single-key resolution if available.");
        } else if (isDebug()) {
            logger.info("[MailSlurp][Pool] No pool keys configured at all.");
        }

        return null;
    }




    /**
     * Wait a short time and confirm no *new* email arrives.
     * Returns the unexpected email if one appears (so the test can fail).
     */
    public static Email waitForNoNewEmail(UUID inboxId, Duration timeout) {
        Objects.requireNonNull(inboxId, "inboxId cannot be null");
        ensureClientReadyOrThrow();

        long deadline = System.currentTimeMillis() + timeout.toMillis();
        long pollMs = 1200;

        // Capture current top email ID to detect new arrivals
        UUID baselineId = null;
        try {
            List<EmailPreview> previews =
                    inboxController.getEmails(inboxId).size(1).execute();
            if (!previews.isEmpty()) {
                baselineId = previews.get(0).getId();
            }
        } catch (Exception ignored) {}

        while (System.currentTimeMillis() < deadline) {
            try {
                List<EmailPreview> previews =
                        inboxController.getEmails(inboxId).size(1).execute();

                if (!previews.isEmpty()) {
                    UUID newestId = previews.get(0).getId();
                    if (baselineId == null || !newestId.equals(baselineId)) {
                        // New email detected
                        return emailController.getEmail(newestId).execute();
                    }
                }
            } catch (Exception ignored) {}

            try { Thread.sleep(pollMs); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        return null; // 👍 No new email arrived
    }


    /** Selected MailSlurp pool index (1..N) when using MAILSLURP_API_KEY_n / MAILSLURP_INBOX_ID_n. */
    public static Integer getSelectedPoolNumber() {
        return selectedPoolNumber;
    }


    /**
     * Wait until a NEW email arrives in the inbox (newer than the current top email).
     * Returns the newest email, or null on timeout.
     */
    public static Email waitForLatestEmail(UUID inboxId, Duration timeout) {
        Objects.requireNonNull(inboxId, "inboxId cannot be null");
        Objects.requireNonNull(timeout, "timeout cannot be null");
        ensureClientReadyOrThrow();

        long deadline = System.currentTimeMillis() + timeout.toMillis();
        long pollMs = 1200;

        // Baseline: current newest email id (if any)
        UUID baselineId = null;
        try {
            List<EmailPreview> previews = inboxController.getEmails(inboxId).size(1).execute();
            if (previews != null && !previews.isEmpty()) {
                baselineId = previews.get(0).getId();
            }
        } catch (Exception ignored) {}

        while (System.currentTimeMillis() < deadline) {
            try {
                List<EmailPreview> previews = inboxController.getEmails(inboxId).size(1).execute();
                if (previews != null && !previews.isEmpty()) {
                    UUID newestId = previews.get(0).getId();
                    if (baselineId == null || !newestId.equals(baselineId)) {
                        return emailController.getEmail(newestId).execute();
                    }
                }
            } catch (Exception e) {
                logger.warn("[MailSlurp][waitForLatestEmail] poll failed: {}", safeMsg(e));
            }

            try { Thread.sleep(pollMs); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        return null;
    }






}
