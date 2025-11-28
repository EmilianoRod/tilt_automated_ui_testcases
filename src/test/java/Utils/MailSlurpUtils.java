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
     * Use fixed inbox if present; create one if allowed; otherwise Skip (if required).
     * Fixed ID lookup precedence:
     *   - MAILSLURP_FIXED_INBOX_ID (sysprop/env)
     *   - MAILSLURP_INBOX_ID (sysprop/env)  [legacy]
     *   - Config.getMailSlurpFixedInboxId()
     */
    public static InboxDto resolveFixedOrCreateInbox() throws ApiException {
        ensureClientReadyOrThrow();

        // New: support MAILSLURP_FIXED_INBOX_ID, but keep legacy sources
        String fixedIdStr = firstNonBlank(
                System.getProperty("MAILSLURP_FIXED_INBOX_ID"),
                System.getenv("MAILSLURP_FIXED_INBOX_ID"),
                System.getProperty("MAILSLURP_INBOX_ID"),
                System.getenv("MAILSLURP_INBOX_ID"),
                Config.getMailSlurpFixedInboxId()
        );

        final boolean allowCreate = isCreateAllowed();
        final boolean hasFixed = isNonBlank(fixedIdStr);

        if (isDebug()) {
            String prefix = hasFixed ? fixedIdStr.trim() : "";
            if (prefix.length() > 8) prefix = prefix.substring(0, 8);
            logger.info(
                    "[MailSlurp][resolve] allowCreate={} | fixedIdPresent={} | idPrefix={}",
                    allowCreate,
                    hasFixed,
                    hasFixed ? prefix : "none"
            );
        }

        // 1) Try fixed inbox if configured
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
                                    "or enable MAILSLURP_ALLOW_CREATE_INBOX_FALLBACK=true for local runs)."
                    );
                }
            }
        } else if (!allowCreate) {
            // 2) No fixed inbox configured and not allowed to create
            throw new SkipException(
                    "MailSlurp inbox creation disabled and no fixed inbox configured. " +
                            "Set MAILSLURP_FIXED_INBOX_ID / MAILSLURP_INBOX_ID or enable " +
                            "MAILSLURP_ALLOW_CREATE_INBOX_FALLBACK=true for local runs."
            );
        }

        // 3) No usable fixed inbox → try to create (if allowed)
        return createInboxReflectiveWithGuards();
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
        // priority: explicit overrides -> sysprop -> env -> Config
        String key = firstNonBlank(
                System.getProperty("mailslurp.forceKey"),
                System.getProperty("mailslurp.apiKey"),
                System.getProperty("MAILSLURP_API_KEY"),
                System.getenv("MAILSLURP_API_KEY"),
                Config.getMailSlurpApiKey(),
                Config.getAny("mailslurp.apiKey", "MAILSLURP_API_KEY")
        );
        if (key == null || key.isBlank()) return null;

        // Optional fingerprint enforcement
        String expectedFpRaw = firstNonBlank(
                System.getProperty("mailslurp.expectedFingerprint"),
                System.getenv("MAILSLURP_EXPECTED_FINGERPRINT")
        );
        if (isNonBlank(expectedFpRaw)) {
            String actual = safeSha12(key.trim());
            if (!expectedFpRaw.trim().equalsIgnoreCase(actual)) {
                throw new SkipException("MailSlurp API key mismatch. expected=" + expectedFpRaw + " actual=" + actual);
            }
        }
        return key.trim();
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



}
