package Utils;

import com.mailslurp.clients.ApiClient;
import com.mailslurp.clients.Configuration;
import com.mailslurp.apis.InboxControllerApi;
import com.mailslurp.apis.WaitForControllerApi;
import com.mailslurp.clients.ApiException;
import com.mailslurp.models.InboxDto;
import com.mailslurp.models.Email;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MailSlurpUtils {

    private static final String PROP_KEY = "mailslurp.apiKey";
    private static final String ENV_KEY  = "MAILSLURP_API_KEY";

    private static final ApiClient apiClient;
    private static final InboxControllerApi inboxController;
    private static final WaitForControllerApi waitForController;

    static {
        String key = System.getProperty(PROP_KEY);
        String source = "system property " + PROP_KEY;

        if (key == null || key.isBlank()) {
            key = System.getenv(ENV_KEY);
            source = "environment variable " + ENV_KEY;
        }

        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "MailSlurp API key not provided. Pass with -D" + PROP_KEY + "=<key> or set " + ENV_KEY
            );
        }

        // Small fingerprint for debug (does NOT print the key)
        String fp = fingerprint(key);
        System.out.println("MailSlurp key source: " + source + " (fingerprint: " + fp + ")");

        apiClient = Configuration.getDefaultApiClient();
        apiClient.setApiKey(key);
        apiClient.setConnectTimeout(30000);
        apiClient.setReadTimeout(30000);
        apiClient.setWriteTimeout(30000);

        inboxController = new InboxControllerApi(apiClient);
        waitForController = new WaitForControllerApi(apiClient);
    }

    private static String fingerprint(String key) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] d = sha256.digest(key.getBytes(StandardCharsets.UTF_8));
            // Short hex, first 12 chars
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.substring(0, 12);
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** Create a new disposable inbox. */
    public static InboxDto createInbox() throws ApiException {
        return inboxController.createInboxWithDefaults().execute();
    }

    /** Wait for latest email in an inbox. */
    public static Email waitForLatestEmail(UUID inboxId, long timeoutMillis, boolean unreadOnly) throws ApiException {
        return waitForController
                .waitForLatestEmail()
                .inboxId(inboxId)
                .timeout(timeoutMillis)
                .unreadOnly(unreadOnly)
                .execute();
    }

    /** Extract first 6-digit OTP from an email body. */
    public static String extractOtpCode(Email email) {
        String body = email.getBody();
        if (body == null) return null;
        Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(body);
        return m.find() ? m.group(1) : null;
    }

    /** Extract first link from an email body. */
    public static String extractFirstLink(Email email) {
        String body = email.getBody();
        if (body == null) return null;
        Matcher m = Pattern.compile("https?://\\S+").matcher(body);
        return m.find() ? m.group() : null;
    }

    /** Extract link by anchor text. */
    public static String extractLinkByAnchorText(Email email, String anchorText) {
        if (email == null || email.getBody() == null || anchorText == null) return null;
        String pattern = "<a[^>]*href=[\"']([^\"']+)[\"'][^>]*>\\s*" + Pattern.quote(anchorText) + "\\s*</a>";
        Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(email.getBody());
        return m.find() ? m.group(1) : null;
    }
}
