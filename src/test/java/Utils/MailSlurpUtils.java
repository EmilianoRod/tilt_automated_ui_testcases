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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MailSlurp helper with CI-safe key resolution and fingerprint guard.
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
 */
public class MailSlurpUtils {

    // ========= Configuration resolution =========

    private static String resolveApiKey() {
        // 1) Hard override wins (lets CI force the paid key regardless of env/files)
        String forced = System.getProperty("mailslurp.forceKey");
        if (forced != null && !forced.isBlank()) {
            return verifyFingerprintOrThrow(forced.trim());
        }

        // 2) CI/ENV (preferred under normal circumstances)
        String key = System.getenv("MAILSLURP_API_KEY");

        // 3) System property fallback (local runs)
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
        apiClient.setApiKey(resolveApiKey());           // secret string (NOT a UUID)
        apiClient.setConnectTimeout(30_000);
        apiClient.setReadTimeout(30_000);
        apiClient.setWriteTimeout(30_000);

        inboxController = new InboxControllerApi(apiClient);
        waitForController = new WaitForControllerApi(apiClient);

        // One-time identity print to prove which key the Java SDK is using.
        if (isDebug()) {
            try {
                String fp = safeSha12(resolveApiKey());
                System.out.println("[MailSlurp][Java SDK] key fingerprint: " + fp);

                // lightweight identity probe: try to fetch 1 inbox (works even if none exist)
                List<InboxDto> one = inboxController.getInboxes().size(1).execute();
                if (one != null && !one.isEmpty() && one.get(0) != null) {
                    System.out.println("[MailSlurp][Java SDK] account userId: " + one.get(0).getUserId());
                } else {
                    System.out.println("[MailSlurp][Java SDK] getInboxes() returned empty list (no inboxes yet).");
                }
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

    // ========= Inbox management =========

    /**
     * Create a new disposable inbox. If quota/plan errors occur (426/402/429),
     * or any 4xx we can reasonably bypass, reuse the first existing inbox so CI can proceed.
     */
    public static InboxDto createInbox() throws ApiException {
        try {
            return inboxController.createInboxWithDefaults().execute();
        } catch (ApiException ex) {
            final int code = ex.getCode();
            if (isDebug()) {
                System.out.println("[MailSlurp] createInbox failed HTTP " + code + ": " + safeMsg(ex));
            }
            // Quota/plan/transient throttling → attempt reuse
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
        } catch (Exception ignored) {
            // If listing fails, just return null and let caller rethrow original error.
        }
        return null;
    }

    // ========= Email waiting =========

    /**
     * Wait for latest email in the given inbox.
     * @param inboxId UUID of the inbox (use inbox.getId())
     * @param timeoutMillis total time the server can wait (MailSlurp long-poll)
     * @param unreadOnly consider only unread messages (set false if reusing inboxes in CI)
     */
    public static Email waitForLatestEmail(UUID inboxId, long timeoutMillis, boolean unreadOnly) throws ApiException {
        Objects.requireNonNull(inboxId, "inboxId must not be null");
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis must be > 0");
        }

        // Single call long-polls server side; we keep it simple and let the SDK do the wait.
        return waitForController
                .waitForLatestEmail()
                .inboxId(inboxId)
                .timeout(timeoutMillis)
                .unreadOnly(unreadOnly)
                .execute();
    }

    // ========= Parsers =========

    /** Extract first 6-digit OTP from email body. */
    public static String extractOtpCode(Email email) {
        if (email == null || email.getBody() == null) return null;
        Matcher matcher = Pattern.compile("\\b(\\d{6})\\b").matcher(email.getBody());
        return matcher.find() ? matcher.group(1) : null;
        // If you sometimes get textBody only, consider email.getBody() + email.getTextBody()
    }

    /** Extract first http/https URL from email body. */
    public static String extractFirstLink(Email email) {
        if (email == null || email.getBody() == null) return null;
        Matcher matcher = Pattern.compile("https?://\\S+").matcher(email.getBody());
        return matcher.find() ? matcher.group() : null;
    }

    /** Extract link by visible anchor text from HTML body. */
    public static String extractLinkByAnchorText(Email email, String anchorText) {
        if (email == null || email.getBody() == null || anchorText == null) return null;
        String pattern = "<a[^>]*href=[\"']([^\"']+)[\"'][^>]*>\\s*" + Pattern.quote(anchorText) + "\\s*</a>";
        Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(email.getBody());
        return m.find() ? m.group(1) : null;
    }

    // ========= Small utility =========

    /** Safer message extractor for logging exceptions. */
    private static String safeMsg(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.isBlank()) ? t.getClass().getSimpleName() : m;
    }

    // ========= Convenience for tests/logs =========

    public static String currentKeyFingerprint() {
        try {
            return safeSha12(resolveApiKey());
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** For local quick checks. Not used by tests. */
    public static void main(String[] args) throws Exception {
        System.out.println("MailSlurp basePath: " + basePath());
        System.out.println("MailSlurp key FP : " + currentKeyFingerprint());

        if (isDebug()) {
            InboxDto inbox = createInbox();
            System.out.println("Inbox ready: " + inbox.getId() + " <" + inbox.getEmailAddress() + ">");
        }
    }
}
