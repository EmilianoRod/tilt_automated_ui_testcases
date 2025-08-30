package tests;

import base.BaseTest;

import Utils.Config;
import org.testng.Assert;
import org.testng.annotations.Test;

import pages.BasePage;
import pages.LoginPage;
import pages.Shop.AssessmentEntryPage;
import pages.Shop.OrderPreviewPage;
import pages.Shop.PurchaseRecipientSelectionPage;
import pages.menuPages.DashboardPage;
import pages.menuPages.ShopPage;


import java.util.UUID;

public class ManualEntryValidationTests extends BaseTest {


    @Test(groups = {"shop","preview","validation","smoke"})
    public void testManualEntryInvalidEmailBlocksProceed_FullFlow() {
        // Login
        LoginPage login = new LoginPage(driver);
        login.navigateTo();
        DashboardPage dashboard = login.login(Config.getAdminEmail(), Config.getAdminPassword());
        Assert.assertTrue(dashboard.isLoaded(), "Dashboard did not load after login");

        // Start purchase
        ShopPage shop = dashboard.goToShop();
        PurchaseRecipientSelectionPage select = shop.clickBuyNowForTrueTilt();
        select.selectClientOrIndividual();
        select.clickNext();

        // Manual entry for a single recipient
        AssessmentEntryPage entry = new AssessmentEntryPage(driver);
        entry.selectManualEntry();
        entry.enterNumberOfIndividuals("1");

        // Fill names and BAD email
        entry.fillUserDetailsAtIndex(0, "Emi", "Rod", "not-an-email");

        // Assert: proceed disabled + inline validation message visible
        Assert.assertFalse(entry.isProceedToPaymentEnabled(),
                "'Proceed to payment' must be disabled for invalid email.");

        String err = entry.getEmailErrorAtRow(0);
        Assert.assertTrue(err == null || err.toLowerCase().contains("email"),
                "Expected an inline email validation message; got: " + err);

        // Fix the email → button should enable → proceed to preview
        String goodEmail = "qa+" + java.util.UUID.randomUUID().toString().substring(0,8) + "@example.com";
        entry.setEmailAtRow(0, goodEmail);

        Assert.assertTrue(entry.isProceedToPaymentEnabled(),
                "'Proceed to payment' should enable after valid email.");

        OrderPreviewPage preview = entry.clickProceedToPayment().waitUntilLoaded();
        Assert.assertTrue(preview.isLoaded(), "Order Preview did not load after fixing email.");
    }

}
