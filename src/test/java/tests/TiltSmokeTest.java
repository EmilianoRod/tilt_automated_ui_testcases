package tests;

import base.BaseTest;
import org.testng.Assert;
import org.testng.annotations.Test;
import pages.LoginPage;
import pages.menuPages.DashboardPage;
import pages.Individuals.IndividualsPage;
import pages.menuPages.ShopPage;

public class TiltSmokeTest extends BaseTest {



    private DashboardPage loginAsAdmin(String email, String password) {
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();
        DashboardPage dashboardPage = loginPage.login(email, password);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard failed to load after login");
        return dashboardPage;
    }

    @Test(groups = {"smoke"})
    public void testInviteUserAndVerifyInIndividuals() {
        DashboardPage dashboard = loginAsAdmin("erodriguez@effectussoftware.com", "Password#1");
        IndividualsPage individualsPage = dashboard.goToIndividuals();
        Assert.assertTrue(individualsPage.isUserListedByEmail("test@example.com"), "❌ Invited user not found in Individuals list");
    }

/*    @Test(groups = {"smoke"})
    public void testSettingsPageLoads() {
        DashboardPage dashboard = loginAsAdmin("erodriguez@effectussoftware.com", "Password#1");
        SettingsPage settingsPage = dashboard.goToSettings();
        Assert.assertTrue(settingsPage.isLoaded(), "❌ Settings page failed to load");
    }*/

    @Test(groups = {"smoke"})
    public void testShopPageLoads() {
        DashboardPage dashboard = loginAsAdmin("erodriguez@effectussoftware.com", "Password#1");
        ShopPage shopPage = dashboard.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page failed to load");
    }

}
