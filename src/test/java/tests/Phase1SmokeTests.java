package tests;

import Utils.MailSlurpUtils;
import Utils.WaitUtils;
import base.BaseTest;
import com.mailslurp.clients.ApiException;
import com.mailslurp.models.Email;
import com.mailslurp.models.InboxDto;
import org.openqa.selenium.JavascriptExecutor;
import org.testng.Assert;
import org.testng.annotations.Test;
import pages.menuPages.DashboardPage;
import pages.LoginPage;
import org.json.JSONObject;

import java.util.Base64;
import java.util.UUID;

public class Phase1SmokeTests extends BaseTest {

    /**
     * TC-1: Verify that newly added users receive an email notification with login instructions
     * Ensure that after a **Super Admin** successfully invites a new user, the user receives an **email notification** containing a welcome message and instructions for logging in using the **OTP authentication system**. The email should be delivered promptly and include all necessary login details.
     */
    @Test
    public void testVerifyThatNewlyAddedUsersReceiveAnEmailNotificationWithLoginInstructions() throws ApiException {
        // 🔹 Step 1: Create a disposable inbox
        InboxDto inbox = MailSlurpUtils.createInbox();
        String tempEmail = inbox.getEmailAddress();
        UUID inboxId = inbox.getId();
        System.out.println("Temporary test email: " + tempEmail);

        // 🔹 Step 2: Log in as Admin and invite this email
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();
        DashboardPage dashboardPage = loginPage.login("erodriguez@effectussoftware.com", "Password#1");

        Assert.assertTrue(dashboardPage.isLoaded(), "Dashboard did not load after login");

        // 🔹 Step 3: Invite new user via UI
        dashboardPage.goToIndividuals();
        dashboardPage.enterInviteEmail(tempEmail);
        dashboardPage.sendInvite();

        // 🔹 Step 4: Wait for the invitation email
        Email email = MailSlurpUtils.waitForLatestEmail(inboxId, 60_000, true); // wait up to 60 seconds
        Assert.assertNotNull(email, "Did not receive any email");

        // 🔹 Step 5: Validate email content
        String subject = email.getSubject();
        Assert.assertTrue(subject.toLowerCase().contains("welcome") || subject.toLowerCase().contains("login"),
                "Email subject does not contain expected keywords: " + subject);

        // 🔹 Step 6: Extract link or OTP from email
        String loginLink = MailSlurpUtils.extractFirstLink(email);
        String otpCode = MailSlurpUtils.extractOtpCode(email);

        Assert.assertTrue(loginLink != null || otpCode != null,
                "Email does not contain a login link or OTP");

        System.out.println("Login link: " + loginLink);
        System.out.println("OTP: " + otpCode);
    }
    

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