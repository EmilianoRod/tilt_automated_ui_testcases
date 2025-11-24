package pages.SignUp;



import Utils.MailSlurpUtils;
import base.BaseTest;
import com.mailslurp.clients.ApiException;
import com.mailslurp.models.InboxDto;
import org.openqa.selenium.WebDriver;
import pages.LoginPage;
import pages.SignUp.SignUpPage;
import pages.menuPages.DashboardPage;

import java.time.Duration;
import java.util.UUID;

public class TestUsers {




    public static UiUser newMailSlurpUser() throws ApiException {
        return newMailSlurpUserForSignup();
    }



    /**
     * Simple value object representing a UI user.
     */
    public static class UiUser {
        public final String firstName;
        public final String lastName;
        public final String email;
        public final String password;
        /** Optional tag (e.g., alias token, UUID) for debugging. */
        public final String tag;

        public UiUser(String firstName,
                      String lastName,
                      String email,
                      String password,
                      String tag) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.password = password;
            this.tag = tag;
        }

        @Override
        public String toString() {
            return "UiUser{" +
                    "firstName='" + firstName + '\'' +
                    ", lastName='" + lastName + '\'' +
                    ", email='" + email + '\'' +
                    ", tag='" + tag + '\'' +
                    '}';
        }
    }

    /**
     * Creates a new "logical user" backed by your fixed MailSlurp inbox +
     * a unique plus-alias email, e.g.:
     *   base inbox:  myinbox@mailslurp.com
     *   aliased:     myinbox+signup-1731626700000@mailslurp.com
     *
     * Uses:
     *  - MailSlurpUtils.resolveFixedOrCreateInbox()
     *  - MailSlurpUtils.uniqueAliasEmail(...)
     */
    public static UiUser newMailSlurpUserForSignup() throws ApiException {
        // Will use fixed inbox if configured, or create one if allowed.
        InboxDto inbox = MailSlurpUtils.resolveFixedOrCreateInbox();

        // Tag for the alias and logs
        String tag = "signup-" + System.currentTimeMillis();

        // Generate aliased email using your helper
        String aliasedEmail = MailSlurpUtils.uniqueAliasEmail(inbox, tag);

        // Random-ish names, mostly for clarity in UIs
        String suffix = UUID.randomUUID().toString().substring(0, 4);
        String firstName = "Emi" + suffix;
        String lastName = "Auto";

        // You can centralize this in Config later if you want
        String password = "Password#1";

        return new UiUser(firstName, lastName, aliasedEmail, password, tag);
    }

    /**
     * Full UI Sign Up + Login using:
     *  - SignUpPage (2-step flow)
     *  - LoginPage.safeLoginAsAdmin(...)
     *
     * Returns DashboardPage already logged in as the newly created user.
     */

    public static DashboardPage signUpAndLogin(WebDriver driver) throws Exception {

        // 1. Create alias user
        UiUser user = newMailSlurpUserForSignup();

        // 2. Sign Up
        SignUpPage signUp = new SignUpPage(driver);
        signUp.navigateTo();
        signUp.completeSignUp(
                user.firstName,
                user.lastName,
                user.email,
                user.password
        );

        // 3. Login
        LoginPage login = new LoginPage(driver);
        login.waitUntilLoaded();

        DashboardPage dashboard = login.safeLoginAsAdmin(
                user.email,
                user.password,
                Duration.ofSeconds(20)
        );

        dashboard.waitUntilLoaded();
        return dashboard;
    }





}
