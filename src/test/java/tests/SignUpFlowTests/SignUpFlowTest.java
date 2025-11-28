package tests.SignUpFlowTests;



import Utils.Config;
import Utils.MailSlurpUtils;
import com.mailslurp.models.Email;
import com.mailslurp.models.InboxDto;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.SignUp.TestUsers;
import pages.SignUp.TestUsers.UiUser;
import base.BaseTest;
import org.testng.Assert;
import org.testng.annotations.Test;
import pages.LoginPage;
import pages.SignUp.SignUpPage;
import pages.menuPages.DashboardPage;

import java.time.Duration;
import java.util.Objects;

import static io.qameta.allure.Allure.step;

public class SignUpFlowTest extends BaseTest {


    @Test(groups = "ui-only",
            description = "New user can complete 2-step Sign Up and then log in")
    public void newUserCanSignUpAndLogin() throws Exception {

        UiUser user = TestUsers.newMailSlurpUserForSignup();

        // Step 1: navigate to Sign Up
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();

        // go to sign-up via UI link if needed
        // driver().findElement(By.linkText("Sign Up")).click();
        // or direct:
        SignUpPage signUp = new SignUpPage(driver());
        signUp.navigateTo();

        // Step 2: complete 2-step sign up
        signUp.completeSignUp(
                user.firstName,
                user.lastName,
                user.email,
                user.password
        );

        // After sign-up we should be on the Sign In page
        loginPage.waitUntilLoaded();
        Assert.assertTrue(loginPage.isLoaded(), "Login page should be visible after successful Sign Up");

        // Step 3: login with the newly created user
        DashboardPage dashboard = loginPage.safeLoginAsAdmin(
                user.email,
                user.password,
                Duration.ofSeconds(20)
        );

        // Basic sanity: dashboard should be loaded
        dashboard.waitUntilLoaded();
        Assert.assertTrue(dashboard.isLoaded(),
                "Dashboard should be visible after logging in with the newly created user");
    }



    /**
     * SM05: Dummy sign-up → full 2-step sign-up completes, no email is sent.
     *
     * Current real behavior:
     *  - /auth/sign-up is a 2-step flow (basic info → GDPR / consents).
     *  - After "Sign Up", user is redirected to Login.
     *  - No confirmation email is sent.
     */
    @Test(groups = {"smoke"}, description = "SM05: Dummy sign-up → completes 2-step flow, no email sent.")
    public void smoke_dummySignup_noEmailSent() throws Exception {

        final Duration EMAIL_TIMEOUT = Duration.ofSeconds(30);

        // ---------- MailSlurp inbox ----------
        step("Resolve shared MailSlurp inbox and generate unique +alias recipient");
        final InboxDto inbox = BaseTest.getSuiteInbox() != null
                ? BaseTest.getSuiteInbox()
                : BaseTest.requireInboxOrSkip();

        final String testEmail  = MailSlurpUtils.uniqueAliasEmail(inbox, "dummy");
        final String aliasToken = MailSlurpUtils.extractAliasToken(testEmail);
        System.out.println("📮 Using shared inbox: " + inbox.getId() + " <" + inbox.getEmailAddress() + ">");
        System.out.println("📧 Dummy sign-up email: " + testEmail);

        try { MailSlurpUtils.clearInboxEmails(inbox.getId()); } catch (Throwable ignored) {}

        // ---------- Navigate + complete sign-up via POM ----------
        step("Navigate to /auth/sign-up and complete the 2-step sign-up");
        SignUpPage signUpPage = new SignUpPage(driver());
        signUpPage.navigateTo();
        signUpPage.waitForFirstStepLoaded();
        Assert.assertTrue(signUpPage.isFirstStepLoaded(), "❌ Sign-up step 1 did not load.");

        String password = "Dummy#" + System.currentTimeMillis();

        // uses: fill step1 → Continue → load GDPR → check both checkboxes → Sign Up → redirect to Login
        LoginPage loginAfterSignup = signUpPage.completeSignUp(
                "DummyFirst",
                "DummyLast",
                testEmail,
                password
        );

        step("Verify user is redirected to Login after Sign Up");
        loginAfterSignup.waitUntilLoaded();
        Assert.assertTrue(loginAfterSignup.isLoaded(),
                "❌ Login page did not load after completing sign-up.");

        // ---------- Verify NO email sent ----------
        step("Verify that NO email was sent for this sign-up user (expected behavior)");
        Email possibleEmail = MailSlurpUtils.waitForEmailMatching(
                inbox.getId(),
                EMAIL_TIMEOUT.toMillis(),
                1500L,
                false, // do NOT fail if none found; we EXPECT null
                MailSlurpUtils.addressedToAliasToken(aliasToken)
        );

        Assert.assertNull(
                possibleEmail,
                "❌ This sign-up flow is not expected to send an email, but one arrived. Subject: " +
                        (possibleEmail != null ? possibleEmail.getSubject() : "")
        );

        System.out.println("✅ SM05: 2-step sign-up (auth/sign-up) completes and NO email was sent (current expected behavior).");
    }



    /**
     * SM06: Dummy sign-up → account can log in right after sign-up.
     *
     * Current behavior (no confirmation email):
     *  - User completes 2-step /auth/sign-up flow.
     *  - Account is created immediately.
     *  - The same email + password can be used to log in to Dashboard.
     */
    @Test(groups = {"smoke"}, description = "SM06: Dummy sign-up → newly created account can log in.")
    public void smoke_dummySignup_accountCanLogin() throws Exception {

        step("Generate a fresh dummy user");
        // Uses your existing test user factory (likely MailSlurp-backed)
        UiUser user = TestUsers.newMailSlurpUserForSignup();

        // ---------- 1) Complete dummy sign-up ----------
        step("Navigate to sign-up and complete 2-step dummy sign-up");
        SignUpPage signUpPage = new SignUpPage(driver());
        signUpPage.navigateTo();
        signUpPage.waitForFirstStepLoaded();
        Assert.assertTrue(signUpPage.isFirstStepLoaded(), "❌ Sign-up step 1 did not load.");

        // Step1 → Continue → GDPR → Sign Up → Login page
        LoginPage loginAfterSignup = signUpPage.completeSignUp(
                user.firstName,
                user.lastName,
                user.email,
                user.password
        );

        step("Verify we land on Login page after completing sign-up");
        loginAfterSignup.waitUntilLoaded();
        Assert.assertTrue(loginAfterSignup.isLoaded(), "❌ Login page did not load after sign-up.");

        // ---------- 2) Log in with the newly created credentials ----------
        step("Log in with newly created dummy account");
        DashboardPage dashboard = loginAfterSignup.safeLoginAsAdmin(
                user.email,
                user.password,
                Duration.ofSeconds(30)
        );

        dashboard.waitUntilLoaded();
        Assert.assertTrue(
                dashboard.isLoaded(),
                "❌ Dashboard not visible after logging in with newly created dummy user: " + user.email
        );

        System.out.println("✅ SM06: Dummy sign-up created an account that can log in successfully (" + user.email + ").");
    }
















}
