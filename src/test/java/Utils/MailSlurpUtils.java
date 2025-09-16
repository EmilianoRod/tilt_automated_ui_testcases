package Utils;

import com.mailslurp.clients.ApiClient;
import com.mailslurp.clients.ApiException;
import com.mailslurp.clients.Configuration;
import com.mailslurp.apis.InboxControllerApi;
import com.mailslurp.apis.WaitForControllerApi;
import com.mailslurp.models.InboxDto;
import com.mailslurp.models.Email;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MailSlurpUtils {
    private static final String API_KEY =
            System.getProperty("mailslurp.apiKey",
                    System.getenv().getOrDefault("MAILSLURP_API_KEY", ""));

    private static final String PRESET_INBOX_ID =
            System.getProperty("mailslurp.inboxId",
                    System.getenv().getOrDefault("MAILSLURP_INBOX_ID", ""));

    private static final ApiClient apiClient;
    private static final InboxControllerApi inboxController;
    private static final WaitForControllerApi waitForController;

    static {
        if (API_KEY == null || API_KEY.isBlank()) {
            throw new IllegalStateException("MailSlurp API key missing. Provide -Dmailslurp.apiKey or MAILSLURP_API_KEY.");
        }
        apiClient = Configuration.getDefaultApiClient();
        apiClient.setApiKey(API_KEY);
        apiClient.setConnectTimeout(30000);
        apiClient.setReadTimeout(30000);
        apiClient.setWriteTimeout(30000);

        inboxController = new InboxControllerApi(apiClient);
        waitForController = new WaitForControllerApi(apiClient);
    }

    /** Create a new inbox unless an existing one was provided (CI path). */
    public static InboxDto createInbox() throws ApiException {
        if (PRESET_INBOX_ID != null && !PRESET_INBOX_ID.isBlank()) {
            // Reuse existing inbox in CI to avoid monthly create-limit
            return inboxController.getInbox(UUID.fromString(PRESET_INBOX_ID)).execute();
        }
        // Local/dev path: create a fresh inbox
        return inboxController.createInboxWithDefaults().execute();
    }

    public static Email waitForLatestEmail(UUID inboxId, long timeoutMillis, boolean unreadOnly) throws ApiException {
        return waitForController.waitForLatestEmail()
                .inboxId(inboxId)
                .timeout(timeoutMillis)
                .unreadOnly(unreadOnly)
                .execute();
    }

    public static String extractOtpCode(Email email) {
        String body = email.getBody();
        if (body == null) return null;
        Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(body);
        return m.find() ? m.group(1) : null;
    }

    public static String extractFirstLink(Email email) {
        String body = email.getBody();
        if (body == null) return null;
        Matcher m = Pattern.compile("https?://\\S+").matcher(body);
        return m.find() ? m.group() : null;
    }

    public static String extractLinkByAnchorText(Email email, String anchorText) {
        if (email == null || email.getBody() == null || anchorText == null) return null;
        String pattern = "<a[^>]*href=[\"']([^\"']+)[\"'][^>]*>\\s*" + Pattern.quote(anchorText) + "\\s*</a>";
        Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(email.getBody());
        return m.find() ? m.group(1) : null;
    }
}
