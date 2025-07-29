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

    private static final String MAILSLURP_API_KEY = "c7e2bfac13ebd2a7a549dcc2d2fadbb6cc47beabd3a5d50ea27e72f231391675";

    private static final ApiClient apiClient;
    private static final InboxControllerApi inboxController;
    private static final WaitForControllerApi waitForController;

    static {
        apiClient = Configuration.getDefaultApiClient();
        apiClient.setApiKey(MAILSLURP_API_KEY);
        apiClient.setConnectTimeout(30000);
        apiClient.setReadTimeout(30000);
        apiClient.setWriteTimeout(30000);

        inboxController = new InboxControllerApi(apiClient);
        waitForController = new WaitForControllerApi(apiClient);
    }

    /**
     * Create a new disposable email inbox.
     * @return InboxDto containing the inbox ID and email address.
     * @throws ApiException if API call fails
     */
    public static InboxDto createInbox() throws ApiException {
        return inboxController.createInboxWithDefaults().execute(); // ✅ .execute() required
    }

    /**
     * Wait for a new email to arrive in the given inbox.
     * @param inboxId the UUID of the target inbox
     * @param timeoutMillis how long to wait (milliseconds)
     * @param unreadOnly true = only wait for unread emails
     * @return Email received
     * @throws ApiException if no email arrives or call fails
     */
    public static Email waitForLatestEmail(UUID inboxId, long timeoutMillis, boolean unreadOnly) throws InterruptedException {
        for (int i = 0; i < 5; i++) { // Retry 5 times with 5 seconds each
            Email email = waitForLatestEmail(inboxId, timeoutMillis, unreadOnly);
            if (email != null) {
                return email;
            }
            Thread.sleep(5000); // Wait before retry
        }
        throw new AssertionError("❌ No email received within the timeout");
    }
    /**
     * Extract a numeric OTP code from the email body.
     * @param email the Email object
     * @return the first 6-digit number found
     */
    public static String extractOtpCode(Email email) {
        String body = email.getBody();
        if (body == null) return null;
        Matcher matcher = Pattern.compile("\\b(\\d{6})\\b").matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Extract the first link (HTTP/HTTPS) from the email body.
     * @param email the Email object
     * @return first URL found, or null
     */
    public static String extractFirstLink(Email email) {
        String body = email.getBody();
        if (body == null) return null;
        Matcher matcher = Pattern.compile("https?://\\S+").matcher(body);
        return matcher.find() ? matcher.group() : null;
    }

}