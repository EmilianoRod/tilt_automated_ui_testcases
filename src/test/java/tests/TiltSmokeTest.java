package tests;

import Utils.Config;
import base.BaseTest;
import io.qameta.allure.*;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;
import pages.LoginPage;
import pages.menuPages.DashboardPage;
import pages.Individuals.IndividualsPage;
import pages.menuPages.ResourcesPage;
import pages.menuPages.SettingsPage;
import pages.menuPages.ShopPage;
import pages.teams.TeamsPage;

import java.time.Duration;

import static io.qameta.allure.Allure.step;


@Epic("Tilt – Smoke")
@Feature("Core Navigation & Profile")
@Owner("Emiliano")
public class TiltSmokeTest extends BaseTest {



    private DashboardPage loginAsAdmin(String email, String password) {
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();
        DashboardPage dashboardPage = loginPage.safeLoginAsAdmin(email, password, Duration.ofSeconds(30));
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard failed to load after login");
        return dashboardPage;
    }



//    @Test(groups = {"smoke"})
//    public void testInviteUserAndVerifyInIndividuals() {
//        DashboardPage dashboard = loginAsAdmin ("erodriguez@effectussoftware.com", "Password#1");
//        IndividualsPage individualsPage = dashboard.goToIndividuals();
//        Assert.assertTrue(individualsPage.isUserListedByEmail("test@example.com"), "❌ Invited user not found in Individuals list");
//    }

/*    @Test(groups = {"smoke"})
    public void testSettingsPageLoads() {
        DashboardPage dashboard = loginAsAdmin("erodriguez@effectussoftware.com", "Password#1");
        SettingsPage settingsPage = dashboard.goToSettings();
        Assert.assertTrue(settingsPage.isLoaded(), "❌ Settings page failed to load");
    }*/

    @Test(groups = {"smoke"})
    @Severity(SeverityLevel.CRITICAL)
    @Story("Navigation – Shop page opens from Dashboard")
    public void testShopPageLoads() {
        DashboardPage dashboard = loginAsAdmin("erodriguez+a@effectussoftware.com", "Password#1");
        ShopPage shopPage = dashboard.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page failed to load");
    }



    /**
     * SM01:
     *  - Login as admin and land on Dashboard.
     *  - Verify Dashboard is loaded (core widgets / layout).
     *  - Use top-nav to go to Shop and verify it loads.
     *  - Use top-nav to go to Individuals and verify it loads.
     */
    @Test(groups = {"smoke"}, description = "SM01: Dashboard, Shop, and Individuals nav links load.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Navigation – Dashboard, Shop and Individuals are reachable from main nav")
    public void smoke_dashboardShopIndividualsNav() throws Exception {

        // -------------------- CONFIG / ADMIN CREDS --------------------
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }
        System.out.println("[AdminCreds] email=" + ADMIN_USER + " | passLen=" + ADMIN_PASS.length());

        // -------------------- LOGIN → DASHBOARD --------------------
        step("Login as admin and land on Dashboard");
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();

        DashboardPage dashboardPage =
                loginPage.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(
                dashboardPage.isLoaded(),
                "❌ Dashboard did not load after login"
        );

        // If you have any quick sanity checks for widgets, call them here, e.g.:
        // dashboardPage.assertCoreWidgetsVisible();

        // -------------------- DASHBOARD → SHOP (NAV) --------------------
        step("Use top navigation to go to Shop");
        ShopPage shopPage = dashboardPage.goToShop();
        Assert.assertTrue(
                shopPage.isLoaded(),
                "❌ Shop page did not load via nav from Dashboard"
        );

        // -------------------- SHOP → INDIVIDUALS (NAV) --------------------
        // If the global header is shared, this can still be a helper on Dashboard/Header;
        // adjust to whatever nav helper you already use (e.g., shopPage.goToIndividuals()).
        step("Use top navigation to go to Individuals");
        IndividualsPage individualsPage = dashboardPage.goToIndividuals();
        individualsPage.waitUntilLoaded();
        Assert.assertTrue(
                individualsPage.isLoaded(),
                "❌ Individuals page did not load via nav"
        );

        System.out.println("✅ SM01: Dashboard, Shop, and Individuals nav links load correctly.");
    }


    /**
     * SM02:
     *  - Login as admin and land on Dashboard.
     *  - Use main nav → Teams and verify Teams page loads.
     *  - Use main nav → Resources and verify Resources page loads.
     */
    @Test(groups = {"smoke"}, description = "SM02: Teams and Resources pages load from main nav.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Navigation – Teams and Resources are reachable from main nav")
    public void smoke_teamsAndResourcesNav() throws Exception {

        // -------------------- CONFIG / ADMIN CREDS --------------------
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }
        System.out.println("[AdminCreds] email=" + ADMIN_USER + " | passLen=" + ADMIN_PASS.length());

        // -------------------- LOGIN → DASHBOARD --------------------
        step("Login as admin and land on Dashboard");
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();

        DashboardPage dashboardPage =
                loginPage.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(60));
        Assert.assertTrue(
                dashboardPage.isLoaded(),
                "❌ Dashboard did not load after login"
        );

        // -------------------- DASHBOARD → TEAMS (NAV) --------------------
        step("Use main navigation to go to Teams");
        TeamsPage teamsPage = dashboardPage.goToTeams();
        Assert.assertTrue(
                teamsPage.isLoaded(),
                "❌ Teams page did not load via nav from Dashboard"
        );

        // -------------------- TEAMS → RESOURCES (NAV) --------------------
        // If your header/nav helpers live on Dashboard only, replace with dashboardPage.goToResources().
        step("Use main navigation to go to Resources");
        ResourcesPage resourcesPage = dashboardPage.goToResources(); // or dashboardPage.goToResources()
        resourcesPage.waitUntilLoaded();
        Assert.assertTrue(
                resourcesPage.isLoaded(),
                "❌ Resources page did not load via nav"
        );

        System.out.println("✅ SM02: Teams and Resources pages load correctly from main nav.");
    }


    /**
     * SM03:
     *  - Login as admin.
     *  - If the maintenance popup appears, click Continue/OK.
     *  - Assert we reach the Dashboard normally.
     */
    @Test(groups = {"smoke"}, description = "SM03: Maintenance popup allows user to continue to Dashboard.")
    @Severity(SeverityLevel.NORMAL)
    @Story("Maintenance – optional maintenance popup does not block Dashboard access")
    public void smoke_maintenancePopupAllowsContinue() throws Exception {

        // -------------------- CONFIG / ADMIN CREDS --------------------
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }
        System.out.println("[AdminCreds] email=" + ADMIN_USER + " | passLen=" + ADMIN_PASS.length());

        // -------------------- LOGIN → DASHBOARD --------------------
        step("Navigate to Login and authenticate as admin");
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();

        DashboardPage dashboardPage = loginPage.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));

        // -------------------- CONDITIONAL: MAINTENANCE POPUP --------------------
        step("Handle optional maintenance popup (if present)");

        // The popup locator (adjust to your real DOM)
        By maintenanceModal = By.xpath("//*[contains(@class,'modal') and contains(., 'Maintenance')]");
        By continueButton   = By.xpath("//button[contains(., 'Continue') or contains(., 'OK') or contains(., 'Ok')]");

        try {
            // Short wait — popup may or may not exist
            WebDriverWait shortWait = new WebDriverWait(driver(), Duration.ofSeconds(3));

            // If modal becomes visible, handle it
            shortWait.until(ExpectedConditions.visibilityOfElementLocated(maintenanceModal));
            System.out.println("⚠️ Maintenance popup detected.");

            // Click Continue / OK
            driver().findElement(continueButton).click();
            System.out.println("🟢 Maintenance popup dismissed.");

        } catch (TimeoutException ignored) {
            System.out.println("ℹ️ No maintenance popup — continuing normally.");
        }

        // -------------------- ASSERT DASHBOARD LOADED --------------------
        step("Verify Dashboard is accessible and fully loaded");
        dashboardPage.waitUntilLoaded();

        Assert.assertTrue(
                dashboardPage.isLoaded(),
                "❌ Dashboard did not load — maintenance popup may have blocked access."
        );

        System.out.println("✅ SM03: User successfully bypassed maintenance popup and reached Dashboard.");
    }


    /**
     * SM38: Profile → update display name.
     *  - Login as admin/user.
     *  - Go to Settings.
     *  - Change display name (first name).
     *  - Save and verify it shows in header.
     */
    @Test(groups = {"smoke"}, description = "SM38: Profile → update display name.")
    @Severity(SeverityLevel.MINOR)
    @Story("Profile – updating display name is reflected in header and settings")
    public void smoke_profile_updateDisplayName() throws Exception {

        // ---------- CONFIG ----------
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing.");
        }

        // ---------- LOGIN ----------
        step("Login as admin and open Dashboard");
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        login.waitUntilLoaded();

        DashboardPage dashboard =
                login.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        dashboard.waitUntilLoaded();
        Assert.assertTrue(dashboard.isLoaded(), "❌ Dashboard did not load after login.");

        String originalHeaderName = dashboard.getUserName();
        System.out.println("[OriginalHeaderName] " + originalHeaderName);

        // ---------- SETTINGS + UPDATE ----------
        step("Navigate to Settings page");
        SettingsPage settings = dashboard.goToSettings();
        Assert.assertTrue(settings.isLoaded(), "❌ Settings page did not load.");

        String newFirstName = "Smoke" + System.currentTimeMillis();

        step("Update first name and save changes");
        settings
                .setFirstName(newFirstName)
                .clickSaveChanges();   // stays on /dashboard/settings

        // ---------- VERIFY HEADER UPDATED ----------
        step("Verify new display name is visible in the top-right header");

        DashboardPage headerContext = new DashboardPage(driver());

        // Wait up to 10s for the header name to contain the new first name
        WebDriverWait wait = new WebDriverWait(driver(), Duration.ofSeconds(10));
        wait.until(d -> {
            String headerName = headerContext.getUserName();
            System.out.println("[HeaderName] " + headerName);
            return headerName != null && headerName.contains(newFirstName);
        });

        String finalHeaderName = headerContext.getUserName();
        Assert.assertTrue(
                finalHeaderName != null && finalHeaderName.contains(newFirstName),
                "❌ Header name does not reflect updated first name. Expected to contain: "
                        + newFirstName + " | Actual: " + finalHeaderName
        );
        System.out.println("✅ SM38: Profile display name updated and visible in header.");


        step("Verify first name field in Settings also shows the new value");
        SettingsPage settingsAgain = dashboard.goToSettings();
        Assert.assertEquals(
                settingsAgain.getFirstName(),
                newFirstName,
                "❌ First name input does not match the updated value."
        );

    }



    /**
     * SM39: Profile → change password and re-login.
     *  - Login with current password.
     *  - Go to Settings and change password.
     *  - Logout.
     *  - Login with NEW password → must succeed.
     *  - Login with OLD password → must fail.
     *  - Cleanup: change password back to original.
     */
//    @Test(groups = {"smoke"}, description = "SM39: Profile → change password and re-login.")
    public void smoke_profile_changePasswordAndRelogin() throws Exception {

        // ---------- CONFIG ----------
        final String USER_EMAIL = Config.getAny("profile.user.email", "PROFILE_USER_EMAIL", "ADMIN_EMAIL");
        final String USER_PASS  = Config.getAny("profile.user.password", "PROFILE_USER_PASS", "ADMIN_PASSWORD");

        if (USER_EMAIL == null || USER_EMAIL.isBlank() || USER_PASS == null || USER_PASS.isBlank()) {
            throw new SkipException("[Config] Profile user credentials missing.");
        }

        final String originalPassword = USER_PASS;
        final String newPassword = "NewPass#" + System.currentTimeMillis();

        try {
            // ---------- 1) LOGIN WITH CURRENT PASSWORD ----------
            step("Login with current password");
            LoginPage login = new LoginPage(driver());
            login.navigateTo();
            login.waitUntilLoaded();

            DashboardPage dashboard =
                    login.safeLoginAsAdmin(USER_EMAIL, originalPassword, Duration.ofSeconds(30));
            dashboard.waitUntilLoaded();
            Assert.assertTrue(dashboard.isLoaded(), "❌ Dashboard did not load after login with original password.");

            // ---------- 2) CHANGE PASSWORD IN SETTINGS ----------
            step("Navigate to Settings and change password");
            SettingsPage settings = dashboard.goToSettings();
            Assert.assertTrue(settings.isLoaded(), "❌ Settings page did not load.");

            settings
                    .changePassword(originalPassword, newPassword)
                    .clickSaveChanges();

            // ---------- 3) LOGOUT ----------
            step("Logout after changing password");
            LoginPage loginAfterChange = dashboard.logout();
            loginAfterChange.waitUntilLoaded();

            // ---------- 4) LOGIN WITH NEW PASSWORD (SHOULD SUCCEED) ----------
            step("Login with NEW password (should succeed)");
            DashboardPage dashboardWithNew =
                    loginAfterChange.safeLoginAsAdmin(USER_EMAIL, newPassword, Duration.ofSeconds(30));
            dashboardWithNew.waitUntilLoaded();
            Assert.assertTrue(dashboardWithNew.isLoaded(),
                    "❌ Dashboard did not load after login with NEW password.");

            // ---------- 5) LOGOUT AGAIN ----------
            step("Logout again to test OLD password");
            LoginPage loginForOld = dashboardWithNew.logout();
            loginForOld.waitUntilLoaded();

            // ---------- 6) LOGIN WITH OLD PASSWORD (SHOULD FAIL) ----------
            step("Login with OLD password (should fail)");
            loginForOld.login(USER_EMAIL, originalPassword);

            String errorMsg = loginForOld.getErrorMessage();
            System.out.println("[InvalidLoginError] " + errorMsg);
            Assert.assertTrue(errorMsg != null && !errorMsg.isBlank(),
                    "❌ Expected an error message when logging in with OLD password, but none was shown.");

        } finally {
            // ---------- CLEANUP: RESTORE ORIGINAL PASSWORD ----------
            try {
                step("Cleanup: login with NEW password and restore original password");
                LoginPage cleanupLogin = new LoginPage(driver());
                cleanupLogin.navigateTo();
                cleanupLogin.waitUntilLoaded();

                DashboardPage cleanupDashboard =
                        cleanupLogin.safeLoginAsAdmin(USER_EMAIL, newPassword, Duration.ofSeconds(30));
                cleanupDashboard.waitUntilLoaded();

                SettingsPage cleanupSettings = cleanupDashboard.goToSettings();
                cleanupSettings
                        .changePassword(newPassword, originalPassword)
                        .clickSaveChanges();

                cleanupDashboard.logout();
            } catch (Throwable t) {
                System.err.println("⚠️ Cleanup (restore original password) failed: " + t.getMessage());
            }
        }

        System.out.println("✅ SM39: Password change verified (new works, old fails) and original password restored.");
    }




    @Test
    @Severity(SeverityLevel.TRIVIAL)
    @Story("Debug – simple admin login for environment inspection")
    public void debug_adminLogin_only() throws InterruptedException {
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");

        System.out.println("[AdminCreds] email=" + ADMIN_USER
                + " | passLen=" + (ADMIN_PASS == null ? -1 : ADMIN_PASS.length())
                + " | blank=" + (ADMIN_PASS == null || ADMIN_PASS.isBlank()));
        System.out.println("[AdminCreds] baseUrl=" + Config.getAny("baseUrl"));

        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        login.waitUntilLoaded();
        login.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(60));

        // Hold a bit so screenshot / manual eyeball can see final state
        Thread.sleep(3000);
    }





}
