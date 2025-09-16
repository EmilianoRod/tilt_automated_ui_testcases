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
import java.util.Formatter;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MailSlurpUtils {

    // ---- Load API key (ENV first, then system prop). Optionally verify fingerprint. ----
    private static String resolveApiKey() {
        // Prefer CI environment variable so Jenkins wins over any local overrides inside the JVM
        String key = System.getenv("MAILSLURP_API_KEY");
        if (key == null || key.isBlank()) {
            key = System.getProperty("mailslurp.apiKey");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "MailSlurp API key is missing. Provide -Dmailslurp.apiKey=... or set MAILSLURP_API_KEY."
            );
        }
        key = key.trim();

        // Optional: fail fast if the key doesn’t match what the pipeline expected
        String expectedFp = System.getProperty("mailslurp.expectedFingerprint", "").trim();
        if (!expectedFp.isEmpty()) {
            String actualFp = safeSha12(key);
            if (!expectedFp.equalsIgnoreCase(actualFp)) {
                throw new IllegalStateException(
                        "MailSlurp API key mismatch. Expected fingerprint=" + expectedFp +
                                " but Java resolved " + actualFp +
                                ". A config file or System.setProperty likely overrode the key."
                );
            }
        }
        return key;
    }

    private static final ApiClient apiClient;
    private static final InboxControllerApi inboxController;
    private static final WaitForControllerApi waitForController;

    static {
        apiClient = Configuration.getDefaultApiClient();
        apiClient.setApiKey(resolveApiKey()); // secret string (NOT a UUID)
        apiClient.setConnectTimeout(30000);
        apiClient.setReadTimeout(30000);
        apiClient.setWriteTimeout(30000);

        inboxController = new InboxControllerApi(apiClient);
        waitForController = new WaitForControllerApi(apiClient);

        // One-time identity print to prove which key the Java SDK is using (can silence with -Dmailslurp.debug=false)
        if (Boolean.parseBoolean(System.getProperty("mailslurp.debug", "true"))) {
            try {
                String fp = safeSha12(resolveApiKey());
                System.out.println("[MailSlurp][Java SDK] key fingerprint: " + fp);
                List<InboxDto> one = inboxController.getInboxes().size(1).execute();
                if (one != null && !one.isEmpty()) {
                    System.out.println("[MailSlurp][Java SDK] account userId: " + one.get(0).getUserId());
                } else {
                    System.out.println("[MailSlurp][Java SDK] getInboxes() returned empty list.");
                }
            } catch (Exception e) {
                System.out.println("[MailSlurp][Java SDK] identity probe failed: " + e.getMessage());
            }
        }
    }

    private static String safeSha12(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            try (Formatter f = new Formatter()) {
                for (byte b : digest) f.format("%02x", b);
                return f.toString().substring(0, 12);
            }
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Create a new disposable inbox. If free-plan monthly create limit is hit (426),
     * reuse the first existing inbox so CI can proceed.
     */
    public static InboxDto createInbox() throws ApiException {
        try {
            return inboxController.createInboxWithDefaults().execute();
        } catch (ApiException ex) {
            // 426 UpgradeRequired → quota. For other 4xx, try reuse too.
            if (ex.getCode() == 426 || (ex.getCode() >= 400 && ex.getCode() < 500)) {
                System.out.println("[MailSlurp] createInbox failed (HTTP " + ex.getCode() + "); reusing existing inbox…");
                List<InboxDto> all = inboxController.getInboxes().size(1).execute();
                if (all != null && !all.isEmpty()) {
                    InboxDto reused = all.get(0);
                    System.out.println("[MailSlurp] Reusing inbox " + reused.getId() + " <" + reused.getEmailAddress() + ">");
                    return reused;
                }
                System.out.println("[MailSlurp] No existing inboxes to reuse.");
            }
            throw ex;
        }
    }

    /**
     * Wait for latest email in the given inbox.
     * @param inboxId UUID of the inbox (use inbox.getId())
     */
    public static Email waitForLatestEmail(UUID inboxId, long timeoutMillis, boolean unreadOnly) throws ApiException {
        return waitForController
                .waitForLatestEmail()
                .inboxId(inboxId)          // must be a real UUID
                .timeout(timeoutMillis)
                .unreadOnly(unreadOnly)
                .execute();
    }

    /** Extract first 6-digit OTP from email body. */
    public static String extractOtpCode(Email email) {
        if (email == null || email.getBody() == null) return null;
        Matcher matcher = Pattern.compile("\\b(\\d{6})\\b").matcher(email.getBody());
        return matcher.find() ? matcher.group(1) : null;
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
}
