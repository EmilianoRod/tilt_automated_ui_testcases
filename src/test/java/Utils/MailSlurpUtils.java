package Utils;

import com.mailslurp.apis.InboxControllerApi;
import com.mailslurp.apis.WaitForControllerApi;
import com.mailslurp.clients.ApiClient;
import com.mailslurp.clients.ApiException;
import com.mailslurp.clients.Configuration;
import com.mailslurp.models.Email;
import com.mailslurp.models.InboxDto;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MailSlurpUtils {

    // Resolved at static init from system property or environment variable.
    private static final String RESOLVED_API_KEY;

    private static final ApiClient apiClient;
    private static final InboxControllerApi inboxController;
    private static final WaitForControllerApi waitForController;

    static {
        // 1) Read key from preferred sources
        String key = System.getProperty("mailslurp.apiKey");
        if (key == null || key.isBlank()) {
            key = System.getenv("MAILSLURP_API_KEY");
        }

        // (Optional) Accept a couple of common fallbacks to be extra robust
        if (key == null || key.isBlank()) {
            key = System.getProperty("MAILSLURP_API_KEY");
        }
        if (key == null || key.isBlank()) {
            key = System.getenv("mailslurp.apiKey");
        }

        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "MAILSLURP_API_KEY not provided. " +
                            "Set JVM prop -Dmailslurp.apiKey=<key> or env MAILSLURP_API_KEY"
            );
        }

        RESOLVED_API_KEY = key;

        // 2) Configure client
        apiClient = Configuration.getDefaultApiClient();
        apiClient.setApiKey(RESOLVED_API_KEY);
        apiClient.setConnectTimeout(30_000);
        apiClient.setReadTimeout(30_000);
        apiClient.setWriteTimeout(30_000);

        // 3) Build APIs
        inboxController = new InboxControllerApi(apiClient);
        waitForController = new WaitForControllerApi(apiClient);
    }

    /** Creates a new disposable inbox using MailSlurp defaults. */
    public static InboxDto createInbox() throws ApiException {
        return inboxController.createInboxWithDefaults().execute();
    }

    /**
     * Waits for the latest email in the given inbox.
     * @param inboxId target inbox UUID
     * @param timeoutMillis maximum wait in milliseconds
     * @param unreadOnly if true, only consider unread emails
     */
    public static Email waitForLatestEmail(UUID inboxId, long timeoutMillis, boolean unreadOnly) throws ApiException {
        return waitForController
                .waitForLatestEmail()
                .inboxId(inboxId)
                .timeout(timeoutMillis)
                .unreadOnly(unreadOnly)
                .execute();
    }

    /** Extracts the first 6-digit OTP code from the email body. */
    public static String extractOtpCode(Email email) {
        String body = email != null ? email.getBody() : null;
        if (body == null) return null;
        Matcher matcher = Pattern.compile("\\b(\\d{6})\\b").matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Extracts the first HTTP/HTTPS link from the email body. */
    public static String extractFirstLink(Email email) {
        String body = email != null ? email.getBody() : null;
        if (body == null) return null;
        Matcher matcher = Pattern.compile("https?://\\S+").matcher(body);
        return matcher.find() ? matcher.group() : null;
    }

    /**
     * Finds a link whose anchor text matches {@code anchorText} and returns its href.
     * Example: <a href="...">Accept Assessment</a>
     */
    public static String extractLinkByAnchorText(Email email, String anchorText) {
        if (email == null || email.getBody() == null || anchorText == null) return null;
        String pattern = "<a[^>]*href=[\"']([^\"']+)[\"'][^>]*>\\s*" + Pattern.quote(anchorText) + "\\s*</a>";
        Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(email.getBody());
        return m.find() ? m.group(1) : null;
    }

    /** For troubleshooting: returns a short fingerprint of the API key without exposing it. */
    public static String apiKeyFingerprint() {
        // simple non-cryptographic mask to avoid leaking the key
        String k = RESOLVED_API_KEY;
        int len = k.length();
        return len <= 8 ? "****" : (k.substring(0, 4) + "…" + k.substring(len - 4));
    }
}
