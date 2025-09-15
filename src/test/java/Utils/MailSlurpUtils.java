package Utils;

import com.mailslurp.clients.ApiClient;
import com.mailslurp.clients.ApiException;
import com.mailslurp.clients.Configuration;
import com.mailslurp.apis.InboxControllerApi;
import com.mailslurp.apis.WaitForControllerApi;
import com.mailslurp.models.InboxDto;
import com.mailslurp.models.Email;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MailSlurpUtils {

    private static String resolveApiKey() {
        // 1) Maven/System property: -Dmailslurp.apiKey=...
        String key = System.getProperty("mailslurp.apiKey");
        if (key == null || key.isBlank()) {
            // 2) Environment var from CI: MAILSLURP_API_KEY
            key = System.getenv("MAILSLURP_API_KEY");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "MAILSLURP_API_KEY not provided. Set -Dmailslurp.apiKey or env MAILSLURP_API_KEY.");
        }
        return key.trim();
    }

    private static String shortFingerprint(String key) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(key.getBytes(StandardCharsets.UTF_8));
            String hex = Base64.getEncoder().encodeToString(dig);
            return hex.substring(0, 12);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static final ApiClient apiClient;
    private static final InboxControllerApi inboxController;
    private static final WaitForControllerApi waitForController;

    static {
        String apiKey = resolveApiKey();

        // Optional: log a tiny fingerprint so you can tell which key is being used without leaking it
        System.out.println("MailSlurp key fingerprint (base64 sha256, 12): " + shortFingerprint(apiKey));

        apiClient = Configuration.getDefaultApiClient();
        apiClient.setApiKey(apiKey);
        apiClient.setConnectTimeout(30000);
        apiClient.setReadTimeout(30000);
        apiClient.setWriteTimeout(30000);

        inboxController = new InboxControllerApi(apiClient);
        waitForController = new WaitForControllerApi(apiClient);
    }

    /** Create a new disposable inbox. */
    public static InboxDto createInbox() throws ApiException {
        return inboxController.createInboxWithDefaults().execute();
    }

    /** Wait for the latest email in the given inbox. */
    public static Email waitForLatestEmail(UUID inboxId, long timeoutMillis, boolean unreadOnly) throws ApiException {
        return waitForController
                .waitForLatestEmail()
                .inboxId(inboxId)
                .timeout(timeoutMillis)
                .unreadOnly(unreadOnly)
                .execute();
    }

    /** Extract a 6-digit OTP from the email body. */
    public static String extractOtpCode(Email email) {
        String body = email != null ? email.getBody() : null;
        if (body == null) return null;
        Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(body);
        return m.find() ? m.group(1) : null;
    }

    /** Extract the first http/https link from the email body. */
    public static String extractFirstLink(Email email) {
        String body = email != null ? email.getBody() : null;
        if (body == null) return null;
        Matcher m = Pattern.compile("https?://\\S+").matcher(body);
        return m.find() ? m.group() : null;
    }

    /** Extract an <a> link by its anchor text. */
    public static String extractLinkByAnchorText(Email email, String anchorText) {
        if (email == null || email.getBody() == null || anchorText == null) return null;
        String pattern = "<a[^>]*href=[\"']([^\"']+)[\"'][^>]*>\\s*" + Pattern.quote(anchorText) + "\\s*</a>";
        Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(email.getBody());
        return m.find() ? m.group(1) : null;
    }
}
