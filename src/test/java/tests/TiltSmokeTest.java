package tests;

import base.BaseTest;
import org.testng.Assert;
import org.testng.annotations.Test;
import pages.LoginPage;
import pages.menuPages.SettingsPage;

public class TiltSmokeTest extends BaseTest {



    @Test
    public void testLandingLoginSettings() throws InterruptedException {

        // Navigate to Login Page and perform login
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();
        loginPage.login("erodriguez+a@effectussoftware.com", "Password#1");
        // (Assume credentials for a test account; in real usage, use secure storage for creds)
        String currentUrl = driver.getCurrentUrl();
        Thread.sleep(4000);
//        Assert.assertFalse(currentUrl.contains("/sign-in"), "Login failed – still on Login page");
        // Alternatively, assert that some element only visible post-login is present,
        // or that URL == expected logged-in URL.


//         3. Navigate to Settings page (after login) and verify it loads
        SettingsPage settingsPage = new SettingsPage(driver);
        settingsPage.open();  // Now it's initialized
        Assert.assertTrue(settingsPage.isLoaded(), "Settings page did not load for logged-in user");
    }

}
