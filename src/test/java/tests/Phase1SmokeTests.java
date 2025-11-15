package tests;

import Utils.Config;
import Utils.EncodingUtils;
import Utils.MailSlurpUtils;
import Utils.StripeCheckoutHelper;
import base.BaseTest;
import com.mailslurp.clients.ApiException;
import com.mailslurp.models.Email;
import com.mailslurp.models.InboxDto;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;
import pages.Individuals.IndividualsPage;
import pages.LoginPage;
import pages.Shop.AssessmentEntryPage;
import pages.Shop.OrderPreviewPage;
import pages.Shop.PurchaseRecipientSelectionPage;
import pages.menuPages.DashboardPage;
import pages.menuPages.ShopPage;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static Utils.Config.joinUrl;

public class Phase1SmokeTests extends BaseTest {

    // ==================== small utils ====================

    /** Console banner for readable logs. */
    private static void step(String title) {
        System.out.println("\n====== " + title + " ======\n");
    }

    /** Mask an email for logs. */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "(blank)";
        int at = email.indexOf('@');
        String user = at > -1 ? email.substring(0, at) : email;
        String dom  = at > -1 ? email.substring(at) : "";
        if (user.length() <= 2) return user.charAt(0) + "****" + dom;
        return user.charAt(0) + "****" + user.charAt(user.length() - 1) + dom;
    }

    /** Parse cs_test_... from a Stripe Checkout URL, or session_id=... */
    private static String extractSessionIdFromUrl(String url) {
        if (url == null) return null;
        Matcher m = Pattern.compile("(?i)(?:cs_test_[A-Za-z0-9_\\-]+)|(?:session_id=([^&]+))").matcher(url);
        if (m.find()) {
            String full = m.group();
            if (full.startsWith("cs_test_")) return full;
            if (m.groupCount() >= 1) return m.group(1);
        }
        return null;
    }

    /** Base64URL encode (no padding). */
    private static String b64Url(String s) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    // ===================== TESTS =====================

    /**
     * TC-1: Verify that newly added users receive an email notification with login instructions.
     * Uses a shared MailSlurp inbox with a unique +alias address to avoid quota limits.
     */
//    @Test(groups = "ui-only")
    @Test
    public void testVerifyThatNewlyAddedUsersReceiveAnEmailNotificationWithLoginInstructions() throws Exception {
        // ----- config / constants -----
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }
        System.out.println("[AdminCreds] email=" + maskEmail(ADMIN_USER) + " | passLen=" + ADMIN_PASS.length());

        final Duration EMAIL_TIMEOUT = Duration.ofSeconds(120);
        final String CTA_TEXT       = "Accept Assessment";
        final String SUBJECT_NEEDLE = "assessment";

        System.setProperty("mailslurp.debug", "true");

        // ----- recipient (shared inbox + unique alias) -----
        step("Resolve shared MailSlurp inbox and generate unique +alias recipient");
        // Prefer the suite-prepared inbox:
        final InboxDto inbox = BaseTest.getSuiteInbox() != null
                ? BaseTest.getSuiteInbox()
                : BaseTest.requireInboxOrSkip();

        final String testEmail = MailSlurpUtils.uniqueAliasEmail(inbox, "invite");
        final String aliasToken = MailSlurpUtils.extractAliasToken(testEmail); // like "+invite-1730..."
        System.out.println("📮 Using shared inbox: " + inbox.getId() + " <" + inbox.getEmailAddress() + ">");
        System.out.println("📧 Test recipient (alias): " + testEmail);
        // Clean the inbox to reduce noise
        try { MailSlurpUtils.clearInboxEmails(inbox.getId()); } catch (Throwable ignored) {}

        // ----- app flow -----
        step("Login as admin");
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();
        DashboardPage dashboardPage =
                loginPage.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Shop and start purchase flow");
        ShopPage shopPage = dashboardPage.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");
        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
        sel.selectClientOrIndividual();
        sel.clickNext();

        step("Manual entry for 1 individual (use the aliased MailSlurp address)");
        AssessmentEntryPage entryPage = new AssessmentEntryPage(driver())
                .waitUntilLoaded()
                .selectManualEntry()
                .enterNumberOfIndividuals("1");
        entryPage.fillUserDetailsAtIndex(1, "Emi", "Rod", testEmail);

        step("Review order (Preview)");
        OrderPreviewPage preview = entryPage.clickProceedToPayment().waitUntilLoaded();

        step("Stripe: fetch session + metadata.body");
        String stripeUrl = preview.proceedToStripeAndGetCheckoutUrl();
        String sessionId = extractSessionIdFromUrl(stripeUrl);
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
        driver().navigate().to(joinUrl(Config.getBaseUrl(), "/dashboard/orders/confirmation"));

        step("Individuals page shows the newly invited user");
        new IndividualsPage(driver())
                .open(Config.getBaseUrl())
                .assertAppearsWithEvidence(Config.getBaseUrl(), testEmail);
        System.out.println("✅ User appears in Individuals: " + testEmail);

        // ----- email assertion (predicate-based; alias-targeted) -----
        step("Wait for invitation email addressed to our alias");
        System.out.println("[Email] Waiting up to " + EMAIL_TIMEOUT.toSeconds() + "s for message to " + testEmail + "…");

        Email email = MailSlurpUtils.waitForEmailMatching(
                inbox.getId(),
                EMAIL_TIMEOUT.toMillis(),
                1500L,
                true,
                // Must be addressed to our +alias and contain expected keywords
                MailSlurpUtils.addressedToAliasToken(aliasToken)
                        .and(MailSlurpUtils.subjectContains(SUBJECT_NEEDLE))
                        .and(MailSlurpUtils.bodyContains("accept"))
        );
        Assert.assertNotNull(email, "❌ No invitation email arrived addressed to alias " + aliasToken + " within " + EMAIL_TIMEOUT);

        final String subject = Objects.toString(email.getSubject(), "");
        final String from    = Objects.toString(email.getFrom(), "");
        final String rawBody = Objects.toString(email.getBody(), "");
        final String body    = MailSlurpUtils.safeEmailBody(email);

        System.out.printf("📨 Email — From: %s | Subject: %s%n", from, subject);
        Assert.assertTrue(subject.toLowerCase(Locale.ROOT).contains(SUBJECT_NEEDLE),
                "❌ Subject does not mention " + SUBJECT_NEEDLE + ". Got: " + subject);

        Assert.assertTrue(from.toLowerCase(Locale.ROOT).contains("tilt365")
                        || from.toLowerCase(Locale.ROOT).contains("sendgrid"),
                "❌ Unexpected sender: " + from);

        Assert.assertTrue(body.toLowerCase(Locale.ROOT).contains(CTA_TEXT.toLowerCase(Locale.ROOT)),
                "❌ Email body missing CTA text '" + CTA_TEXT + "'.");

        String ctaHref = MailSlurpUtils.extractLinkByAnchorText(email, CTA_TEXT);
        if (ctaHref == null) ctaHref = MailSlurpUtils.extractFirstLink(email);
        Assert.assertNotNull(ctaHref, "❌ Could not find a link in the email.");

        System.out.println("🔗 CTA link: " + ctaHref);
        Assert.assertTrue(ctaHref.contains("sendgrid.net") || ctaHref.contains("tilt365"),
                "❌ CTA link host unexpected: " + ctaHref);
    }

    /** Null-safe string helper for logs/asserts. */
    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * TC-2: Store access-token after login.
     * Verify that the frontend stores the access-token securely after a successful login (e.g., in memory or secure storage).
     */
    @Test(groups = "ui-only")
    public void testStoreAccessTokenAfterLogin() {
        // --- Config / expected ---
        final String USER_EMAIL = System.getProperty("USER_EMAIL", "erodriguez+a@effectussoftware.com");
        final String USER_ROLE  = System.getProperty("USER_ROLE",  "practitioner");
        final int    USER_ID    = Integer.parseInt(System.getProperty("USER_ID", "313820"));

        // --- 1) Login ---
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();
        DashboardPage dashboard = loginPage.login(USER_EMAIL, System.getProperty("USER_PASS", "Password#1"));
        new WebDriverWait(driver(), Duration.ofSeconds(15)).until(d -> dashboard.isLoaded());
        Assert.assertTrue(dashboard.isLoaded(), "Dashboard did not load after login");

        // --- 2) Wait for token to exist (no sleeps) ---
        WebDriverWait wait = new WebDriverWait(driver(), Duration.ofSeconds(10));
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

        // --- 4) Validate redux-persist root ---
        String persistRoot = (String) ((JavascriptExecutor) driver())
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
     * TC-3: Redirect unauthorized users to login page.
     * Ensure the frontend redirects the user to the login page when an invalid or expired token is detected.
     */
    @Test(groups = "ui-only")
    public void testRedirectUnauthorizedUsersToLoginPage() {
        final String USER_EMAIL = System.getProperty("USER_EMAIL", "erodriguez+a@effectussoftware.com");
        final String USER_PASS  = System.getProperty("USER_PASS",  "Password#1");
        final String LOGIN_PATH = "/auth/sign-in";
        final String PROTECTED  = "/dashboard/individuals"; //protected route

        // 1) Login (valid)
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        DashboardPage dashboard = login.login(USER_EMAIL, USER_PASS);
        new WebDriverWait(driver(), Duration.ofSeconds(25)).until(d -> dashboard.isLoaded());
        Assert.assertTrue(dashboard.isLoaded(), "Dashboard did not load after login");

        // 2) Tamper auth: set an EXPIRED JWT and clear persisted state
        JavascriptExecutor js = (JavascriptExecutor) driver();

        // Build an expired JWT-like string (doesn't need to be signed for client-side checks)
        long past = (System.currentTimeMillis() / 1000L) - 60; // 60s in the past
        String header   = EncodingUtils.decodeBase64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload  = EncodingUtils.decodeBase64Url("{\"sub\":\"313820\",\"exp\":" + past + "}");
        String expiredJwt = header + "." + payload + "."; // alg=none -> empty signature ok for our client check

        js.executeScript("window.localStorage.setItem('jwt', arguments[0]);", expiredJwt);
        js.executeScript("window.localStorage.removeItem('persist:root');");

        // 3) Hit a protected route to trigger guards (route-change & API call)
        driver().navigate().to(joinUrl(Config.getBaseUrl(), PROTECTED));

        // 4) Wait for redirect
        WebDriverWait wait = new WebDriverWait(driver(), Duration.ofSeconds(10));
        boolean redirected = wait.until(d ->
                d.getCurrentUrl().contains(LOGIN_PATH)
        );
        Assert.assertTrue(redirected, "User was not redirected to login page after token invalidation");

        // 5) Sanity: verify key login UI is visible
        boolean loginFormVisible = new WebDriverWait(driver(), Duration.ofSeconds(10)).until(d -> {
            try {
                return d.findElement(By.cssSelector("input[type='email']")).isDisplayed()
                        && d.findElement(By.cssSelector("input[type='password']")).isDisplayed();
            } catch (NoSuchElementException ignored) { return false; }
        });
        Assert.assertTrue(loginFormVisible, "Login form not visible after redirect");
    }

    /**
     * TC-4: Generate access-token on successful login.
     * Verify that an access-token is issued upon successful login with valid credentials.
     */
    @Test(groups = "ui-only")
    public void testGenerateAccessTokenOnSuccessfu0lLogin() {
        // Step 1: Login
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();
        DashboardPage dashboardPage = loginPage.login("erodriguez+a@effectussoftware.com", "Password#1");

        Assert.assertTrue(dashboardPage.isLoaded(), "Dashboard page did not load after login");
        new WebDriverWait(driver(), Duration.ofSeconds(10)).until(d -> dashboardPage.isLoaded());

        // Step 2: Access JWT from localStorage
        JavascriptExecutor js = (JavascriptExecutor) driver();
        String jwt = (String) js.executeScript("return window.localStorage.getItem('jwt');");

        // Step 3: Basic validations
        Assert.assertNotNull(jwt, "Access token (jwt) was not generated after login");
        Assert.assertEquals(jwt.split("\\.").length, 3, "JWT format is invalid (should be header.payload.signature)");
    }

    /**
     * TC-5: Access-token is not generated on failed login.
     * Verify that login attempts with invalid credentials do not produce a token.
     */
    @Test(groups = "ui-only")
    public void testAccessTokenIsNotGeneratedOnFailedLogin() throws InterruptedException {
        // Step 1: Navigate to login page
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();

        // Step 2: Try invalid login
        loginPage.login("wronguser@example.com", "wrongPassword");

        // Step 3: Assert error message is visible (optional UI feedback)
        String errorMsg = loginPage.getErrorMessage();
        Assert.assertTrue(errorMsg != null && !errorMsg.isEmpty(), "Invalid email or password.");

        // Step 4: Check that no JWT token exists
        JavascriptExecutor js = (JavascriptExecutor) driver();
        String jwt = (String) js.executeScript("return window.localStorage.getItem('jwt');");
        Assert.assertTrue(jwt == null || jwt.isEmpty(), "JWT was generated even though login failed");

        Thread.sleep(500); // small buffer for UI updates
    }

    /**
     * TC-6: Login success redirects user.
     * Upon successful OTP entry, redirect user to their dashboard or home screen.
     */
    @Test(groups = "ui-only")
    public void testLoginSuccessRedirectsUser() {
        // Step 1: Navigate to login page
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();

        // Step 2: Login with valid credentials
        DashboardPage dashboardPage = loginPage.login("erodriguez+a@effectussoftware.com", "Password#1");

        // Wait for the dashboard to load
        new WebDriverWait(driver(), Duration.ofSeconds(10)).until(d -> dashboardPage.isLoaded());

        // Step 3: Verify the user was redirected to the dashboard
        new WebDriverWait(driver(), Duration.ofSeconds(10))
                .until(d -> d.getCurrentUrl().contains("/dashboard"));
        Assert.assertTrue(dashboardPage.isLoaded(), "User was not redirected to dashboard after login");

        // Optional: Assert URL for clarity
        String currentUrl = driver().getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("/dashboard"),
                "Expected redirection to dashboard, but got: " + currentUrl);

        Assert.assertTrue(dashboardPage.isUserNameDisplayed(), "User name is not displayed on the dashboard");
        Assert.assertTrue(dashboardPage.isNewAssessmentButtonVisible(), "New Assessment button is not visible on the dashboard");
    }

    /**
     * TC-7: Redirect user appropriately post-login.
     */
    @Test(groups = "ui-only")
    public void testRedirectUserAppropriatelyPostLogin() throws InterruptedException {
        // Navigate to Login Page
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();

        // Perform Login with valid credentials
        DashboardPage dashboardPage = loginPage.login("erodriguez+a@effectussoftware.com", "Password#1");
        Thread.sleep(500);

        // Wait for the dashboard to load
        boolean isOnDashboard = dashboardPage.isLoaded();
        Assert.assertTrue(isOnDashboard, "Dashboard did not load successfully after login.");

        // Verify redirected URL
        String currentUrl = driver().getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("/dashboard"), "User was not redirected to the dashboard.");

        // Optional: Verify the user's name is shown
        String userName = dashboardPage.getUserName();
        Assert.assertTrue(userName.contains("Emiliano"), "User name not displayed correctly on dashboard.");
    }

    /**
     * TC-9: Show email input field on login screen.
     * Ensure user sees a field to enter their email.
     */
    @Test(groups = "ui-only")
    public void testShowEmailInputFieldOnLoginScreen() {
        // Navigate to Login Page
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();

        // Verify the email input field is visible
        boolean isEmailVisible = loginPage.isEmailFieldVisible();
        Assert.assertTrue(isEmailVisible, "Email input field is not visible on the login screen.");
    }

    /**
     * TC-10: Redirect to dashboard on successful login.
     * After password is successfully verified, redirect the user to the dashboard page.
     */
    @Test(groups = "ui-only")
    public void testRedirectToDashboardOnSuccessfulLogin() {
        // Step 1: Navigate to Login Page and login
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();
        DashboardPage dashboardPage = loginPage.safeLoginAsAdmin("erodriguez+a@effectussoftware.com", "Password#1", Duration.ofSeconds(30));

        // Step 2: Assert that the dashboard loaded
        Assert.assertTrue(dashboardPage.isLoaded(), "Dashboard did not load after successful login.");

        // Step 3 (optional): Confirm URL contains '/dashboard'
        String currentUrl = driver().getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("/dashboard"), "User was not redirected to the dashboard.");
    }
}
