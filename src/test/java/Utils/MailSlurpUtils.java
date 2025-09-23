package Utils;

import com.mailslurp.apis.InboxControllerApi;
import com.mailslurp.apis.WaitForControllerApi;
import com.mailslurp.clients.ApiClient;
import com.mailslurp.clients.ApiException;
import com.mailslurp.clients.Configuration;
import com.mailslurp.models.Email;
import com.mailslurp.models.InboxDto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Formatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MailSlurp helper — CI-safe key resolution, fixed-inbox reuse, and 426 guard.
 *
 * Priority for API key:
 *   1) -Dmailslurp.forceKey
 *   2) env MAILSLURP_API_KEY
 *   3) -Dmailslurp.apiKey
 *
 * Optional guards/knobs:
 *   -Dmailslurp.expectedFingerprint=<first-12-of-SHA256>
 *   -Dmailslurp.basePath=https://api.mailslurp.com (default)
 *   -Dmailslurp.debug=true|false (default true)
 *   -DMAILSLURP_INBOX_ID=<uuid> (or env var) to reuse a fixed inbox
 */
public class MailSlurpUtils {

    // ========= Configuration resolution =========

    private static String resolveApiKey() {
        String forced = System.getProperty("mailslurp.forceKey");
        if (forced != null && !forced.isBlank()) {
            return verifyFingerprintOrThrow(forced.trim());
        }
        String key = System.getenv("MAILSLURP_API_KEY");
        if (key == null || key.isBlank()) {
            key = System.getProperty("mailslurp.apiKey");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "MailSlurp API key is missing. Provide -Dmailslurp.apiKey=... or set MAILSLURP_API_KEY, or use -Dmailslurp.forceKey."
            );
        }
        return verifyFingerprintOrThrow(key.trim());
    }

    private static String verifyFingerprintOrThrow(String key) {
        String expectedFp = System.getProperty("mailslurp.expectedFingerprint", "").trim();
        if (!expectedFp.isEmpty()) {
            String actualFp = safeSha12(key);
            if (!expectedFp.equalsIgnoreCase(actualFp)) {
                throw new IllegalStateException(
                        "MailSlurp API key mismatch. Expected fingerprint=" + expectedFp +
                                " but resolved " + actualFp +
                                ". A config file, env, or JVM prop overrode your intended key."
                );
            }
        }
        return key;
    }

    private static boolean isDebug() {
        return Boolean.parseBoolean(System.getProperty("mailslurp.debug", "true"));
    }

    private static String basePath() {
        return System.getProperty("mailslurp.basePath", "https://api.mailslurp.com").trim();
    }

    // ========= MailSlurp SDK singletons =========

    private static final ApiClient apiClient;
    private static final InboxControllerApi inboxController;
    private static final WaitForControllerApi waitForController;

    static {
        apiClient = Configuration.getDefaultApiClient();
        apiClient.setBasePath(basePath());
        apiClient.setApiKey(resolveApiKey());
        apiClient.setConnectTimeout(30_000);
        apiClient.setReadTimeout(30_000);
        apiClient.setWriteTimeout(30_000);

        inboxController = new InboxControllerApi(apiClient);
        waitForController = new WaitForControllerApi(apiClient);

        if (isDebug()) {
            try {
                String fp = safeSha12(resolveApiKey());
                System.out.println("[MailSlurp][Java SDK] key fingerprint: " + fp);
                // quick auth probe
                try {
                    apiClient.setConnectTimeout(10_000);
                    apiClient.setReadTimeout(10_000);
                    inboxController.getInboxes().size(1).execute();
                    System.out.println("[MailSlurp][Java SDK] auth OK.");
                } catch (Exception ignore) {}
            } catch (Exception e) {
                System.out.println("[MailSlurp][Java SDK] identity probe failed: " + e.getMessage());
            }
        }
    }

    // ========= Helpers =========

    /** First 12 hex chars of SHA-256 — safe to log as a fingerprint. */
    private static String safeSha12(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            try (Formatter f = new Formatter()) {
                for (byte b : digest) f.format("%02x", b);
                String full = f.toString();
                return full.length() >= 12 ? full.substring(0, 12) : full;
            }
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String safeMsg(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.isBlank()) ? t.getClass().getSimpleName() : m;
    }

    // ========= Inbox management =========

    /**
     * Preferred: reuse fixed inbox if MAILSLURP_INBOX_ID is present (JVM prop or env).
     * Otherwise, create a new one. If the fixed ID is invalid/inaccessible, we fall back to create.
     */
    public static InboxDto resolveFixedOrCreateInbox() throws ApiException {
        String fixedIdStr = Optional.ofNullable(System.getProperty("MAILSLURP_INBOX_ID"))
                .orElse(System.getenv("MAILSLURP_INBOX_ID"));

        if (fixedIdStr != null && !fixedIdStr.isBlank()) {
            try {
                UUID fixedId = UUID.fromString(fixedIdStr.trim());
                InboxDto fixed = inboxController.getInbox(fixedId).execute();
                if (isDebug()) {
                    System.out.println("[MailSlurp] Using fixed inbox " + fixed.getId() +
                            " <" + fixed.getEmailAddress() + ">");
                }
                return fixed;
            } catch (IllegalArgumentException badUuid) {
                System.err.println("[MailSlurp] MAILSLURP_INBOX_ID is not a valid UUID: " + fixedIdStr +
                        " — falling back to createInbox().");
            } catch (ApiException ex) {
                System.err.println("[MailSlurp] Could not fetch fixed inbox " + fixedIdStr +
                        " (HTTP " + ex.getCode() + "): " + safeMsg(ex) +
                        " — falling back to createInbox().");
            }
        }

        // Fallback (may consume CreateInbox allowance)
        return createInbox();
    }

    /**
     * Create a new disposable inbox. If quota/plan/throttle errors occur, try to reuse an existing inbox.
     */
    public static InboxDto createInbox() throws ApiException {
        try {
            return inboxController.createInboxWithDefaults().execute();
        } catch (ApiException ex) {
            final int code = ex.getCode();
            if (isDebug()) {
                System.out.println("[MailSlurp] createInbox failed HTTP " + code + ": " + safeMsg(ex));
            }
            if (code == 426 || code == 402 || code == 429 || (code >= 400 && code < 500)) {
                InboxDto reused = getFirstExistingInboxOrNull();
                if (reused != null) {
                    if (isDebug()) {
                        System.out.println("[MailSlurp] Reusing inbox " + reused.getId() +
                                " <" + reused.getEmailAddress() + ">");
                    }
                    return reused;
                }
                if (isDebug()) System.out.println("[MailSlurp] No existing inboxes to reuse.");
            }
            throw ex;
        }
    }

    private static InboxDto getFirstExistingInboxOrNull() {
        try {
            List<InboxDto> all = inboxController.getInboxes().size(1).execute();
            if (all != null && !all.isEmpty()) return all.get(0);
        } catch (Exception ignored) {}
        return null;
    }

    /** Fetch inbox by ID string; throws if invalid or not found. */
    public static InboxDto getInboxById(String id) throws ApiException {
        return inboxController.getInbox(UUID.fromString(id)).execute();
    }

    /** Fetch inbox by ID; throws if not found. */
    public static InboxDto getInboxById(UUID id) throws ApiException {
        return inboxController.getInbox(id).execute();
    }

    /** Clear all emails from inbox (use at start of suite or test for deterministic waits). */
    public static void clearInboxEmails(UUID inboxId) {
        Objects.requireNonNull(inboxId, "inboxId must not be null");
        try {
            inboxController.deleteAllInboxEmails(inboxId).execute();
            if (isDebug()) System.out.println("[MailSlurp] Cleared emails for inbox " + inboxId);
        } catch (ApiException e) {
            throw new RuntimeException("Failed to clear MailSlurp inbox: " + inboxId + " — " + safeMsg(e), e);
        }
    }

    // ========= Email waiting =========

    /**
     * Wait for latest email in inbox.
     * If you cleared inbox just before, keep unreadOnly=true.
     */
    public static Email waitForLatestEmail(UUID inboxId, long timeoutMillis, boolean unreadOnly) throws ApiException {
        Objects.requireNonNull(inboxId, "inboxId must not be null");
        if (timeoutMillis <= 0) throw new IllegalArgumentException("timeoutMillis must be > 0");
        return waitForController
                .waitForLatestEmail()
                .inboxId(inboxId)
                .timeout(timeoutMillis)
                .unreadOnly(unreadOnly)
                .execute();
    }

    // ========= Parsers =========

    public static String extractOtpCode(Email email) {
        if (email == null || email.getBody() == null) return null;
        Matcher matcher = Pattern.compile("\\b(\\d{6})\\b").matcher(email.getBody());
        return matcher.find() ? matcher.group(1) : null;
    }

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

    // ========= Convenience =========

    public static String currentKeyFingerprint() {
        try {
            return safeSha12(resolveApiKey());
        } catch (Exception e) {
            return "unknown";
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("MailSlurp basePath: " + basePath());
        System.out.println("MailSlurp key FP : " + currentKeyFingerprint());
        if (isDebug()) {
            InboxDto inbox = resolveFixedOrCreateInbox();
            System.out.println("Inbox ready: " + inbox.getId() + " <" + inbox.getEmailAddress() + ">");
        }
    }
}
