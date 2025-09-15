package tests;
import Utils.*;
import base.BaseTest;
import com.mailslurp.clients.ApiException;
import com.mailslurp.models.InboxDto;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;
import pages.Shop.AssessmentEntryPage;
import pages.Shop.PurchaseRecipientSelectionPage;
import pages.Shop.OrderPreviewPage;
import pages.menuPages.DashboardPage;
import pages.LoginPage;
import org.json.JSONObject;
import pages.menuPages.IndividualsPage;
import pages.menuPages.ShopPage;
import java.time.Duration;
import java.util.UUID;
import static Utils.Config.joinUrl;




public class Phase1SmokeTests extends BaseTest {




    /**
     * TC-1: Verify that newly added users receive an email notification with login instructions
     */
    @Test
    public void testVerifyThatNewlyAddedUsersReceiveAnEmailNotificationWithLoginInstructions() throws ApiException {

        // ===== Config / constants =====
        final String ADMIN_USER   = System.getProperty("ADMIN_USER", Config.getAdminEmail());
        final String ADMIN_PASS   = System.getProperty("ADMIN_PASS", Config.getAdminPassword());
        final Duration EMAIL_TIMEOUT = Duration.ofSeconds(120);
        final String CTA_TEXT        = "Accept Assessment";
        final String SUBJECT_NEEDLE  = "assessment";

        step("Create disposable inbox");
        InboxDto inbox = MailSlurpUtils.createInbox();
        String tempEmail = inbox.getEmailAddress();
        UUID inboxId     = inbox.getId();
        System.out.println("📧 Temporary test email: " + tempEmail);

        step("Login as admin");
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();
        DashboardPage dashboardPage = loginPage.login(ADMIN_USER, ADMIN_PASS);
        new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> dashboardPage.isLoaded());
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Shop and start purchase flow");
        ShopPage shopPage = dashboardPage.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");
        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
        sel.selectClientOrIndividual();
        sel.clickNext();

        step("Manual entry for 1 individual");
        AssessmentEntryPage entryPage = new AssessmentEntryPage(driver)
                .waitUntilLoaded()
                .selectManualEntry()
                .enterNumberOfIndividuals("1");
        entryPage.fillUserDetailsAtIndex(1, "Emi", "Rod", tempEmail);

        step("Review order (Preview)");
        OrderPreviewPage preview = entryPage.clickProceedToPayment().waitUntilLoaded();

        step("Stripe: fetch session + metadata.body");
        String stripeUrl = preview.proceedToStripeAndGetCheckoutUrl();
        String sessionId = extractSessionIdFromUrl(stripeUrl); // cs_test_...
        Assert.assertNotNull(sessionId, "❌ Could not parse Stripe session id from URL");
        System.out.println("[Stripe] checkoutUrl=" + stripeUrl + " | sessionId=" + sessionId);

        String bodyJson = StripeCheckoutHelper.fetchCheckoutBodyFromStripe(sessionId);
        Assert.assertNotNull(bodyJson, "❌ metadata.body not found in Checkout Session");
        System.out.println("[Stripe] metadata.body length=" + bodyJson.length());

        step("Stripe: trigger checkout.session.completed via CLI");
        var trig = StripeCheckoutHelper.triggerCheckoutCompletedWithBody(bodyJson);
        System.out.println("[Stripe] Triggered eventId=" + trig.eventId +
                (trig.requestLogUrl != null ? " | requestLog=" + trig.requestLogUrl : ""));

        step("Navigate to post-payment confirmation");
        driver.navigate().to(Config.joinUrl(Config.getBaseUrl(), "/dashboard/orders/confirmation"));


        step("Individuals page shows the newly invited user");
        // open() handles navigation (with cache-buster) + waits
        new IndividualsPage(driver)
                .open(Config.getBaseUrl())
                .assertAppearsWithEvidence(Config.getBaseUrl(), tempEmail);
        System.out.println("✅ User appears in Individuals: " + tempEmail);


        step("Wait for email and assert contents");
        System.out.println("[Email] Waiting up to " + EMAIL_TIMEOUT.toSeconds() + "s for message to " + tempEmail + "…");
        com.mailslurp.models.Email email =
                MailSlurpUtils.waitForLatestEmail(inboxId, EMAIL_TIMEOUT.toMillis(), true);
        Assert.assertNotNull(email, "❌ No email received for " + tempEmail + " within " + EMAIL_TIMEOUT);

        final String subject = safe(email.getSubject());
        final String from    = safe(email.getFrom());
        final String body    = safe(email.getBody());
        System.out.println(String.format("📨 Email — From: %s | Subject: %s", from, subject));

        // Subject must mention "assessment" (case-insensitive)
        Assert.assertTrue(subject.toLowerCase().contains(SUBJECT_NEEDLE),
                "❌ Subject does not mention " + SUBJECT_NEEDLE + ". Got: " + subject);

        // Sender should be Tilt or SendGrid relay
        Assert.assertTrue(from.toLowerCase().contains("tilt365") || from.toLowerCase().contains("sendgrid"),
                "❌ Unexpected sender: " + from);

        // Body should include the CTA text
        Assert.assertTrue(body.toLowerCase().contains(CTA_TEXT.toLowerCase()),
                "❌ Email body missing CTA text '" + CTA_TEXT + "'.");

        // Extract CTA link
        String ctaHref = MailSlurpUtils.extractLinkByAnchorText(email, CTA_TEXT);
        if (ctaHref == null) ctaHref = MailSlurpUtils.extractFirstLink(email);
        Assert.assertNotNull(ctaHref, "❌ Could not find a link in the email.");
        System.out.println("🔗 CTA link: " + ctaHref);
        Assert.assertTrue(ctaHref.contains("sendgrid.net") || ctaHref.contains("tilt365"),
                "❌ CTA link host unexpected: " + ctaHref);
    }




// --- helpers ---

    /** Parse cs_test_... from the Stripe Checkout URL (for diagnostics). */
    private static String extractSessionIdFromUrl(String stripeUrl) {
        if (stripeUrl == null) return null;
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("(cs_test_[A-Za-z0-9]+)").matcher(stripeUrl);
        return m.find() ? m.group(1) : null;
    }

    /** Null-safe string helper for logs/asserts. */
    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** Simple console step banner for readable logs. */
    private static void step(String title) {
        System.out.println("\n====== " + title + " ======\n");
    }




    /**
     * TC-2: Store access-token after login
     * Verify that the frontend stores the access-token securely after a successful login (e.g., in memory or secure storage).
     */
    @Test
    public void testStoreAccessTokenAfterLogin() throws InterruptedException {
        // --- Config / expected ---
        final String USER_EMAIL = System.getProperty("USER_EMAIL", "erodriguez+a@effectussoftware.com");
        final String USER_ROLE  = System.getProperty("USER_ROLE",  "practitioner");
        final int    USER_ID    = Integer.parseInt(System.getProperty("USER_ID", "313820"));

        // --- 1) Login ---
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();
        DashboardPage dashboard = loginPage.login(USER_EMAIL, System.getProperty("USER_PASS", "Password#1"));
        new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> dashboard.isLoaded());
        Assert.assertTrue(dashboard.isLoaded(), "Dashboard did not load after login");

        // --- 2) Wait for token to exist (no sleeps) ---
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String jwt = wait.until(d -> (String) ((JavascriptExecutor) d)
                .executeScript("return window.localStorage.getItem('jwt');"));
        Assert.assertNotNull(jwt, "JWT not stored in localStorage['jwt']");

        // --- 3) Validate JWT shape & claims ---
        Assert.assertEquals(jwt.split("\\.").length, 3, "JWT should have 3 segments");

        String[] parts = jwt.split("\\.");
        String payloadJson = EncodingUtils.decodeBase64Url(parts[1]);
        Assert.assertNotNull(payloadJson, "JWT payload couldn't be Base64URL-decoded");

        JSONObject payload = new JSONObject(payloadJson);

        // required: subject matches expected user id
        Assert.assertEquals(
                payload.optString("sub"),
                String.valueOf(USER_ID),
                "JWT 'sub' (user id) mismatch"
        );

        // optional but great sanity checks
        long now = System.currentTimeMillis() / 1000L;
        long exp = payload.optLong("exp", 0L);
        Assert.assertTrue(exp > now, "JWT already expired (exp <= now)");

        // if your backend sets these, assert them (safe optString/optLong)
        // Assert.assertEquals(payload.optString("iss"), "https://tilt365.com", "Issuer mismatch");
        // Assert.assertEquals(payload.optString("aud"), "tilt-dashboard", "Audience mismatch");

        // --- 4) Validate redux-persist root ---
        String persistRoot = (String) ((JavascriptExecutor) driver)
                .executeScript("return window.localStorage.getItem('persist:root');");
        Assert.assertNotNull(persistRoot, "persist:root not stored");

        JSONObject root = new JSONObject(persistRoot);

        // In many redux-persist setups, each slice is stringified JSON
        String authSliceStr = root.optString("auth", null);
        Assert.assertNotNull(authSliceStr, "persist:root missing 'auth' slice");

        JSONObject authSlice = new JSONObject(authSliceStr);
        // Sometimes user itself is stringified too; handle both cases
        Object userObj = authSlice.opt("user");
        JSONObject userJson = (userObj instanceof String)
                ? new JSONObject((String) userObj)
                : (JSONObject) userObj;

        Assert.assertNotNull(userJson, "persist:root.auth.user missing");

        Assert.assertEquals(userJson.optString("email"), USER_EMAIL, "Persisted user email mismatch");
        Assert.assertEquals(userJson.optString("role"),  USER_ROLE,  "Persisted user role mismatch");
        Assert.assertEquals(userJson.optInt("id"),       USER_ID,    "Persisted user id mismatch");
    }
    

    /**
     * TC-3: Redirect unauthorized users to login page
     * Ensure the frontend redirects the user to the login page when an invalid or expired token is detected.
     */
    @Test
    public void testRedirectUnauthorizedUsersToLoginPage() throws InterruptedException {
        final String USER_EMAIL = System.getProperty("USER_EMAIL", "erodriguez+a@effectussoftware.com");
        final String USER_PASS  = System.getProperty("USER_PASS",  "Password#1");
        final String LOGIN_PATH = "/auth/sign-in";
        final String PROTECTED  = "/dashboard/individuals"; //protected route

        // 1) Login (valid)
        LoginPage login = new LoginPage(driver);
        login.navigateTo();
        DashboardPage dashboard = login.login(USER_EMAIL, USER_PASS);
        new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> dashboard.isLoaded());
        Assert.assertTrue(dashboard.isLoaded(), "Dashboard did not load after login");

        // 2) Tamper auth: set an EXPIRED JWT and clear persisted state
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Build an expired JWT-like string (doesn't need to be signed for client-side checks)
        long past = (System.currentTimeMillis() / 1000L) - 60; // 60s in the past
        String header   = EncodingUtils.decodeBase64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload  = EncodingUtils.decodeBase64Url("{\"sub\":\"313820\",\"exp\":" + past + "}");
        String expiredJwt = header + "." + payload + "."; // alg=none -> empty signature ok for our client check

        js.executeScript("window.localStorage.setItem('jwt', arguments[0]);", expiredJwt);
        js.executeScript("window.localStorage.removeItem('persist:root');");

        // 3) Hit a protected route to trigger guards (route-change & API call)
        driver.navigate().to(joinUrl(Config.getBaseUrl(), PROTECTED));


        // 4) Wait for redirect
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        boolean redirected = wait.until(d ->
                d.getCurrentUrl().contains(LOGIN_PATH)
        );
        Assert.assertTrue(redirected, "User was not redirected to login page after token invalidation");


        // 5) Sanity: verify key login UI is visible
        boolean loginFormVisible = new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> {
            try {
                return d.findElement(By.cssSelector("input[type='email']")).isDisplayed()
                        && d.findElement(By.cssSelector("input[type='password']")).isDisplayed();
            } catch (NoSuchElementException ignored) { return false; }
        });
        Assert.assertTrue(loginFormVisible, "Login form not visible after redirect");
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
        loginPage.waitUntilLoaded();
        DashboardPage dashboardPage = loginPage.login("erodriguez+a@effectussoftware.com", "Password#1");

        Assert.assertTrue(dashboardPage.isLoaded(), "Dashboard page did not load after login");
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(d -> dashboardPage.isLoaded());

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
        loginPage.waitUntilLoaded();

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
        loginPage.waitUntilLoaded();

        // Step 2: Login with valid credentials
        DashboardPage dashboardPage = loginPage.login("erodriguez+a@effectussoftware.com", "Password#1");

        // Wait for the dashboard to load
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(d -> dashboardPage.isLoaded());

        // Step 3: Verify the user was redirected to the dashboard
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(d -> d.getCurrentUrl().contains("/dashboard"));
        Assert.assertTrue(dashboardPage.isLoaded(), "User was not redirected to dashboard after login");

        // Optional: Assert URL for clarity
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
        loginPage.waitUntilLoaded();

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
        loginPage.waitUntilLoaded();

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
        loginPage.waitUntilLoaded();
        DashboardPage dashboardPage = loginPage.login("erodriguez+a@effectussoftware.com", "Password#1");

        // Step 2: Assert that the dashboard loaded
        Assert.assertTrue(dashboardPage.isLoaded(), "Dashboard did not load after successful login.");

        // Step 3 (optional): Confirm URL contains '/dashboard'
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("/dashboard"), "User was not redirected to the dashboard.");
    }
    
}