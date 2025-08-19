package tests;

import Utils.BackendUtils;
import Utils.Config;
import Utils.MailSlurpUtils;
import Utils.WaitUtils;
import base.BaseTest;
import com.mailslurp.clients.ApiException;
import com.mailslurp.models.InboxDto;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;
import pages.Shop.AssessmentEntryPage;
import pages.Shop.OrderConfirmationPage;
import pages.Shop.PurchaseRecipientSelectionPage;
import pages.Shop.OrderPreviewPage;
import pages.Shop.Stripe.PlaywrightStripeBridge;
import pages.Shop.Stripe.StripeCheckoutPage;
import pages.menuPages.DashboardPage;
import pages.LoginPage;
import org.json.JSONObject;
import pages.menuPages.IndividualsPage;
import pages.menuPages.ShopPage;

import java.io.File;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static Utils.Config.joinUrl;
import static java.lang.String.format;

public class Phase1SmokeTests extends BaseTest {






    /**
     * TC-1: Verify that newly added users receive an email notification with login instructions
     * Ensure that after a **Super Admin** successfully invites a new user, the user receives an **email notification** containing a welcome message and instructions for logging in using the **OTP authentication system**. The email should be delivered promptly and include all necessary login details.
     */
    @Test
    public void testVerifyThatNewlyAddedUsersReceiveAnEmailNotificationWithLoginInstructions() throws ApiException, InterruptedException {


        // ===== Config / constants =====
        final String ADMIN_USER = System.getProperty("ADMIN_USER", Config.getAdminEmail());
        final String ADMIN_PASS = System.getProperty("ADMIN_PASS", Config.getAdminPassword());
        final String PW_WORKDIR = System.getProperty("PW_WORKDIR", "/Users/test/Desktop/stripe-checkout-playwright");
        final String PW_PROJECT = System.getProperty("PW_PROJECT", "Google Chrome");
        final String NPX_PATH   = System.getProperty("NPX_PATH", "/usr/local/bin/npx");
        final String CHECKOUT_EMAIL = System.getProperty("CHECKOUT_EMAIL", "qa+stripe@example.com");

        final Duration EMAIL_TIMEOUT = Duration.ofSeconds(120);
        final String CTA_TEXT       = "Accept Assessment";
        final String SUBJECT_NEEDLE = "assessment";


        // 🔹 Step 1: Create a disposable inbox
        InboxDto inbox = MailSlurpUtils.createInbox();
        String tempEmail = inbox.getEmailAddress();
        UUID inboxId = inbox.getId();
        System.out.println("📧 Temporary test email: " + tempEmail);

        // 🔹 Step 2: Log in as Super Admin
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();
        Thread.sleep(2390);
        DashboardPage dashboardPage = loginPage.login(ADMIN_USER, ADMIN_PASS);
        new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> dashboardPage.isLoaded());
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");


        // 🔹 Step 3: Navigate to Shop, click on Buy Now button from TTP, click on Individuals, click Next
        ShopPage shopPage = dashboardPage.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");
        PurchaseRecipientSelectionPage purchaseRecipientSelectionPage = shopPage.clickBuyNowForTrueTilt();
        purchaseRecipientSelectionPage.selectClientOrIndividual();
        purchaseRecipientSelectionPage.clickNext();


        // ◆ Step 4: Select "Manual entry" and input invitee information
        AssessmentEntryPage entryPage = new AssessmentEntryPage(driver)
                .waitUntilLoaded()
                .selectManualEntry()
                .enterNumberOfIndividuals("1");
        String firstName = "Emi";
        String lastName  = "Rod";
        entryPage.fillUserDetailsAtIndex(1, firstName, lastName, tempEmail);


        // → Order preview
        OrderPreviewPage preview = entryPage
                .clickProceedToPayment()   // clicks the form's submit/Proceed button
                .waitUntilLoaded();        // waits for the preview screen

       // ◆ Step 5: Proceed to Stripe and pay (handoff to Playwright)
        String stripeUrl = preview.proceedToStripeAndGetCheckoutUrl();


        //  configure Playwright handoff
        //  run the checkout in Playwright
        PlaywrightStripeBridge.Result r = PlaywrightStripeBridge.payReturning(
                PlaywrightStripeBridge.Options.defaultOptions()
                .setCheckoutUrl(stripeUrl)
                .setCheckoutEmail("qa+stripe@example.com")
                .setProject("Google Chrome")   // name from your playwright.config.ts project
                .setHeaded(true)              // run visible so you can watch
                .setWorkingDirectory(new File(PW_WORKDIR))
                .setNpxExecutable("/usr/local/bin/npx")
        );



        // ===== Step 5b: Back to Selenium – verify PW success & land back in app =====
        if (!r.isSuccess()) {
            throw new AssertionError("❌ Stripe payment failed (Playwright exit != 0). Check [PW] logs above.");
        }

        String successUrl = r.getSuccessUrl();
        if (successUrl != null && !successUrl.isBlank()) {
            System.out.println("↩️ Navigating Selenium to success URL: " + successUrl);
            driver.navigate().to(successUrl);
        } else {
            String fallback = joinUrl(Config.getBaseUrl(), "/dashboard/orders/confirmation");
            System.out.println("⚠️ No success URL captured by PW. Navigating to fallback: " + fallback);
            driver.navigate().to(fallback);
        }

        // sanity: not still on Stripe
        Assert.assertFalse(driver.getCurrentUrl().contains("checkout.stripe.com"),
                "❌ Still on Stripe Checkout after payment.");

        // ===== Step 6: Verify purchase reflected in Tilt (Individuals) =====
        driver.navigate().to(joinUrl(Config.getBaseUrl(), "/dashboard/individuals"));
        IndividualsPage individuals = new IndividualsPage(driver).waitUntilLoaded();

        boolean listed = WaitUtils.pollFor(
                Duration.ofSeconds(30),
                Duration.ofMillis(700),
                () -> individuals.isUserListedByEmail(tempEmail)
        );
        Assert.assertTrue(listed, "❌ Newly purchased/invited user not found in Individuals: " + tempEmail);
        System.out.println("✅ User appears in Individuals: " + tempEmail);

        // ===== Step 7: Verify invite/receipt email via MailSlurp =====
        com.mailslurp.models.Email email =
                MailSlurpUtils.waitForLatestEmail(inboxId, EMAIL_TIMEOUT.toMillis(), true);

        Assert.assertNotNull(email, "❌ No email received for " + tempEmail + " within " + EMAIL_TIMEOUT);

        final String subject = safe(email.getSubject());
        final String from    = safe(email.getFrom());
        final String body    = safe(email.getBody());

        System.out.println(format("📨 Email — From: %s | Subject: %s", from, subject));

        Assert.assertTrue(subject.toLowerCase().contains(SUBJECT_NEEDLE),
                "❌ Subject does not mention " + SUBJECT_NEEDLE + ". Got: " + subject);

        Assert.assertTrue(from.toLowerCase().contains("tilt365") || from.toLowerCase().contains("sendgrid"),
                "❌ Unexpected sender: " + from);

        Assert.assertTrue(body.toLowerCase().contains(CTA_TEXT.toLowerCase()),
                "❌ Email body missing CTA text '" + CTA_TEXT + "'.");

        Assert.assertTrue(email.getSubject().contains("Assessment"),
                "Expected subject containing 'Assessment'");

        Assert.assertTrue(email.getBody().contains("Accept Assessment"),
                "CTA button not found in email body");

        Assert.assertEquals(email.getFrom(), "no-reply@tilt365.com");



        String ctaHref = MailSlurpUtils.extractLinkByAnchorText(email, CTA_TEXT);
        if (ctaHref == null) ctaHref = MailSlurpUtils.extractFirstLink(email);

        Assert.assertNotNull(ctaHref, "❌ Could not find a link in the email.");
        System.out.println("🔗 CTA link: " + ctaHref);
        Assert.assertTrue(ctaHref.contains("sendgrid.net") || ctaHref.contains("tilt365"),
                "❌ CTA link host unexpected: " + ctaHref);

        // (Optional) Continue to invite link:
        // driver.navigate().to(ctaHref);
    }




    private static String safe(String s) { return s == null ? "" : s; }


    /**
     * TC-2: Store access-token after login
     * Verify that the frontend stores the access-token securely after a successful login (e.g., in memory or secure storage).
     */
    @Test
    public void testStoreAccessTokenAfterLogin() throws InterruptedException {
        // Navigate to Login Page and perform login
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();
        DashboardPage dashboardPage = loginPage.login("erodriguez+a@effectussoftware.com", "Password#1");
        dashboardPage.isLoaded();
        Thread.sleep(5000);

        // Access the access token from the dashboard or wherever it is stored
        JavascriptExecutor js = (JavascriptExecutor) driver;
        String jwt = (String) js.executeScript("return window.localStorage.getItem('jwt');");
        Assert.assertNotNull(jwt, "JWT was not stored");
        Assert.assertTrue(jwt.split("\\.").length == 3, "JWT does not appear to be well-formed");

        String[] parts = jwt.split("\\.");
        String payloadJson = new String(Base64.getDecoder().decode(parts[1]));
        Assert.assertTrue(payloadJson.contains("\"sub\":\"313820\""), "JWT payload doesn't contain expected user ID");


        // Validate persist:root
        String persistRoot = (String) js.executeScript("return window.localStorage.getItem('persist:root');");
        System.out.println("persist:root = " + persistRoot);
        Assert.assertNotNull(persistRoot, "persist:root not stored");

        JSONObject persistJson = new JSONObject(persistRoot);
        String authString = persistJson.getString("auth");
        JSONObject authJson = new JSONObject(authString);
        JSONObject userJson = authJson.getJSONObject("user");

        Assert.assertEquals(userJson.getString("email"), "erodriguez+a@effectussoftware.com");
        Assert.assertEquals(userJson.getString("role"), "practitioner");
        Assert.assertEquals(userJson.getInt("id"), 313820);
    }
    

    /**
     * TC-3: Redirect unauthorized users to login page
     * Ensure the frontend redirects the user to the login page when an invalid or expired token is detected.
     */
    @Test
    public void testRedirectUnauthorizedUsersToLoginPage() throws InterruptedException {
        // Step 1: Navigate to Login Page and login with valid credentials
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();
        DashboardPage dashboardPage = loginPage.login("erodriguez+a@effectussoftware.com", "Password#1");

        Assert.assertTrue(dashboardPage.isLoaded(), "Dashboard page did not load after login");
        Thread.sleep(5000); // Wait for the dashboard to load completely

        // Step 2: Simulate token tampering by clearing JWT & persist:root
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("window.localStorage.removeItem('jwt');");
        js.executeScript("window.localStorage.removeItem('persist:root');");

        // Step 3: Reload page to trigger unauthorized state
        driver.navigate().refresh();

        // Step 4: Use WaitUtils to verify redirection to login
        WaitUtils waitUtils = new WaitUtils(driver, 10);
        boolean redirected = waitUtils.waitForUrlContains("/sign-in");
        Assert.assertTrue(redirected, "User was not redirected to login page after token removal.");

        // Step 5: Double-check URL manually
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("/sign-in"), "URL does not reflect redirection to login page.");
        Thread.sleep(5000);
    }
    

    /**
     * TC-4: Generate access-token on successful login
     * Verify that an access-token is issued upon successful login with valid credentials.
     */
    @Test
    public void testGenerateAccessTokenOnSuccessfulLogin() throws InterruptedException {
        // Step 1: Login
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();
        DashboardPage dashboardPage = loginPage.login("erodriguez+a@effectussoftware.com", "Password#1");

        Assert.assertTrue(dashboardPage.isLoaded(), "Dashboard page did not load after login");
        Thread.sleep(5000); // Wait for the dashboard to load completely

        // Step 2: Access JWT from localStorage
        JavascriptExecutor js = (JavascriptExecutor) driver;
        String jwt = (String) js.executeScript("return window.localStorage.getItem('jwt');");

        // Step 3: Basic validations
        Assert.assertNotNull(jwt, "Access token (jwt) was not generated after login");
        Assert.assertTrue(jwt.split("\\.").length == 3, "JWT format is invalid (should be header.payload.signature)");
    }
    

    /**
     * TC-5: Access-token is not generated on failed login
     * Verify that login attempts with invalid credentials do not produce a token.
     */
    @Test
    public void testAccessTokenIsNotGeneratedOnFailedLogin() throws InterruptedException {
        // Step 1: Navigate to login page
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();

        // Step 2: Try invalid login
        loginPage.login("wronguser@example.com", "wrongPassword");

        // Step 3: Assert error message is visible (optional UI feedback)
        String errorMsg = loginPage.getErrorMessage();
        Assert.assertTrue(errorMsg != null && !errorMsg.isEmpty(), "Invalid email or password.");

        // Step 4: Check that no JWT token exists
        JavascriptExecutor js = (JavascriptExecutor) driver;
        String jwt = (String) js.executeScript("return window.localStorage.getItem('jwt');");
        Assert.assertTrue(jwt == null || jwt.isEmpty(), "JWT was generated even though login failed");

        Thread.sleep(5000); // Wait for any potential UI updates
    }
    

    /**
     * TC-6: Login success redirects user
     * Upon successful OTP entry, redirect user to their dashboard or home screen.
     */
    @Test
    public void testLoginSuccessRedirectsUser() throws InterruptedException {
        // Step 1: Navigate to login page
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();

        // Step 2: Login with valid credentials
        DashboardPage dashboardPage = loginPage.login("erodriguez+a@effectussoftware.com", "Password#1");

        // Step 3: Verify the user was redirected to the dashboard
        Assert.assertTrue(dashboardPage.isLoaded(), "User was not redirected to dashboard after login");

        // Optional: Assert URL for clarity
        Thread.sleep(5000);
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("/dashboard"),
                "Expected redirection to dashboard, but got: " + currentUrl);

        Assert.assertTrue(dashboardPage.isUserNameDisplayed(), "User name is not displayed on the dashboard");
        Assert.assertTrue(dashboardPage.isNewAssessmentButtonVisible(), "New Assessment button is not visible on the dashboard");
    }
    

    /**
     * TC-7: Redirect user appropriately post-login
     * Redirect user appropriately post-login.
     */
    @Test
    public void testRedirectUserAppropriatelyPostLogin() throws InterruptedException {
        // Navigate to Login Page
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();

        // Perform Login with valid credentials
        DashboardPage dashboardPage = loginPage.login("erodriguez+a@effectussoftware.com", "Password#1");
        Thread.sleep(5000);

        // Wait for the dashboard to load
        boolean isOnDashboard = dashboardPage.isLoaded();
        Assert.assertTrue(isOnDashboard, "Dashboard did not load successfully after login.");

        // Verify redirected URL
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("/dashboard"), "User was not redirected to the dashboard.");

        // Optional: Verify the user's name is shown
        String userName = dashboardPage.getUserName();
        Assert.assertTrue(userName.contains("Emiliano"), "User name not displayed correctly on dashboard.");
    }
    

    /**
     * TC-9: Show email input field on login screen
     * Ensure user sees a field to enter their email.
     */
    @Test
    public void testShowEmailInputFieldOnLoginScreen() {
        // Navigate to Login Page
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();

        // Verify the email input field is visible
        boolean isEmailVisible = loginPage.isEmailFieldVisible();
        Assert.assertTrue(isEmailVisible, "Email input field is not visible on the login screen.");
    }
    

    /**
     * TC-10: Redirect to dashboard on successful login
     * After password is successfully verified, redirect the user to the dashboard page.
     */
    @Test
    public void testRedirectToDashboardOnSuccessfulLogin() {
        // Step 1: Navigate to Login Page and login
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();
        DashboardPage dashboardPage = loginPage.login("erodriguez+a@effectussoftware.com", "Password#1");

        // Step 2: Assert that the dashboard loaded
        Assert.assertTrue(dashboardPage.isLoaded(), "Dashboard did not load after successful login.");

        // Step 3 (optional): Confirm URL contains '/dashboard'
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("/dashboard"), "User was not redirected to the dashboard.");
    }
    
}