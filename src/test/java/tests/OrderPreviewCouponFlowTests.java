package tests;

import base.BaseTest;
import Utils.Config;
import base.BaseTest;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import pages.LoginPage;
import pages.Shop.AssessmentEntryPage;
import pages.Shop.OrderPreviewPage;
import pages.Shop.PurchaseRecipientSelectionPage;
import pages.menuPages.DashboardPage;
import pages.menuPages.ShopPage;


import java.util.UUID;




@Epic("Tilt – Purchases")
@Feature("Order Preview – Coupon Behaviour")
@Owner("Emiliano")
public class OrderPreviewCouponFlowTests extends BaseTest {



    @Test(groups = {"shop","preview","coupon","smoke"})
    @Severity(SeverityLevel.NORMAL)
    @Story("Coupon – default state is OFF on first load")
    public void testCouponDefaultUncheckedOnFirstLoad() throws InterruptedException {
        // Login
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        DashboardPage dashboard = login.login(Config.getAdminEmail(), Config.getAdminPassword());
        Assert.assertTrue(dashboard.isLoaded(), "Dashboard did not load after login");

        // Start purchase
        ShopPage shop = dashboard.goToShop();
        PurchaseRecipientSelectionPage select = shop.clickBuyNowForTrueTilt();
        select.selectClientOrIndividual();
        select.clickNext();

        // Manual entry for a single recipient
        AssessmentEntryPage entry = new AssessmentEntryPage(driver());
        entry.selectManualEntry();
        entry.enterNumberOfIndividuals("1");
        String email = "qa+" + UUID.randomUUID().toString().substring(0,8) + "@example.com";
        entry.fillUserDetailsAtIndex(0, "Emi", "Rod", email);

        // Preview
        OrderPreviewPage preview = entry.clickProceedToPayment().waitUntilLoaded();

        // Default should be OFF on first load
        Assert.assertFalse(preview.isCouponChecked(),
                "Coupon should be unchecked by default on first load of Order Preview.");

        // Sanity
        Assert.assertTrue(preview.isProceedEnabled(), "Proceed/Pay button should be enabled.");
    }


    @Test(groups = {"shop","preview","coupon"})
    @Severity(SeverityLevel.NORMAL)
    @Story("Coupon – toggled ON by user, but resets to default after Back/Forward")
    public void testCouponResetsWhenNavigatingBackAndForward_FullFlow() {
        // Login
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        DashboardPage dashboard = login.login(Config.getAdminEmail(), Config.getAdminPassword());
        Assert.assertTrue(dashboard.isLoaded(), "Dashboard did not load after login");

        // Start purchase
        ShopPage shop = dashboard.goToShop();
        PurchaseRecipientSelectionPage select = shop.clickBuyNowForTrueTilt();
        select.selectClientOrIndividual();
        select.clickNext();

        // Manual entry for a single recipient
        AssessmentEntryPage entry = new AssessmentEntryPage(driver());
        entry.selectManualEntry();
        entry.enterNumberOfIndividuals("1");
        String email = "qa+" + UUID.randomUUID().toString().substring(0,8) + "@example.com";
        entry.fillUserDetailsAtIndex(0, "Emi", "Rod", email);

        // Preview
        OrderPreviewPage preview = entry.clickProceedToPayment().waitUntilLoaded();

        // Default should be OFF
        Assert.assertFalse(preview.isCouponChecked(), "Coupon should be unchecked on first load.");

        // User checks it
        preview.setCouponChecked(true);
        Assert.assertTrue(preview.isCouponChecked(), "Coupon should be checked after user toggles it.");

        // Navigate away and back → expect reset to default (unchecked)
        preview.clickPrevious();
        entry.clickProceedToPayment().waitUntilLoaded();

        Assert.assertFalse(preview.isCouponChecked(),
                "Coupon should reset to UNCHECKED after Previous → Proceed → Preview.");

        // Sanity: proceed still enabled
        Assert.assertTrue(preview.isProceedEnabled(), "Proceed/Pay button should be enabled.");
    }


    @Test(groups = {"shop","preview","coupon"})
    @Severity(SeverityLevel.MINOR)
    @Story("Coupon – default OFF state persists when user does not interact with it")
    public void testCouponDefaultRemainsUncheckedAfterBackForward_FullFlow() {
        // Login
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        DashboardPage dashboard = login.login(Config.getAdminEmail(), Config.getAdminPassword());
        Assert.assertTrue(dashboard.isLoaded(), "Dashboard did not load after login");

        // Start purchase
        ShopPage shop = dashboard.goToShop();
        PurchaseRecipientSelectionPage select = shop.clickBuyNowForTrueTilt();
        select.selectClientOrIndividual();
        select.clickNext();

        // Manual entry for a single recipient
        AssessmentEntryPage entry = new AssessmentEntryPage(driver());
        entry.selectManualEntry();
        entry.enterNumberOfIndividuals("1");
        String email = "qa+" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        entry.fillUserDetailsAtIndex(0, "Emi", "Rod", email);

        // Preview (first load)
        OrderPreviewPage preview = entry.clickProceedToPayment().waitUntilLoaded();

        // Default should be OFF
        Assert.assertFalse(preview.isCouponChecked(), "Coupon should be unchecked on first load.");

        // Back → Forward should reset/keep default
        preview.clickPrevious();
        entry.clickProceedToPayment().waitUntilLoaded();

        // Expect UNCHECKED because state resets to default
        Assert.assertFalse(preview.isCouponChecked(),
                "Coupon should be UNCHECKED after Previous → Proceed → Preview (reset to default).");

        // Sanity
        Assert.assertTrue(preview.isProceedEnabled(), "Proceed/Pay button should be enabled.");
    }





}
