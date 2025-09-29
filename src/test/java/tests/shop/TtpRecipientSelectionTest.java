package tests.shop;

import base.BaseTest;
import org.testng.Assert;
import org.testng.annotations.Test;
import pages.BasePage;
import pages.Shop.PurchaseInformation;
import pages.Shop.PurchaseRecipientSelectionPage;
import pages.menuPages.DashboardPage;
import pages.menuPages.ShopPage;

import java.time.Duration;

import static Utils.WaitUtils.waitForLoadersToDisappear;
import static io.qameta.allure.Allure.step;

public class TtpRecipientSelectionTest extends BaseTest {





    @Test(description = "Next is disabled until a recipient is chosen; selecting 'Myself' enables it and advances to Purchase Information.")
    public void cannotProceedWithoutSelection_thenSelectMyself() throws InterruptedException {
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

        step("Go to Shop and start purchase flow of TTP");
        ShopPage shopPage = dashboard.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");

        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt().waitUntilLoaded();

        step("Verify Next/Continue is disabled initially (with wait)");
        sel.waitUntilNextDisabled(Duration.ofSeconds(5));
        Assert.assertFalse(sel.isNextEnabled(), "Next should be disabled before selection");


        step("Select 'Myself' as purchase recipient");
        sel.selectMyself();


        step("Verify Next/Continue becomes enabled (with wait)");
        Assert.assertTrue(sel.isNextEnabled(), "Next should enable after selecting 'Myself'");

        step("Click Next/Continue");
        sel.clickNextCta();

        step("Validate we are on Purchase Information (Myself path has no Order preview)");
        waitForLoadersToDisappear(driver, Duration.ofSeconds(10));
        boolean onPurchaseInfo = BasePage.isCurrentPage(
                driver,
                "/dashboard/shop/ttp",
                PurchaseInformation.H2_PURCHASE_INFORMATION
        );

        Assert.assertTrue(onPurchaseInfo, "Expected Purchase Information. URL: " + driver.getCurrentUrl());

        PurchaseInformation pi = new PurchaseInformation(driver).waitUntilLoaded();
        Assert.assertTrue(pi.headerVisible(), "Purchase Information header should be visible.");
        Assert.assertTrue(pi.payWithStripeVisible(), "'Pay With stripe' CTA should be visible.");
        Assert.assertEquals(pi.getPurchaseForText(), "Myself");
        Assert.assertFalse(pi.isOrderPreviewVisible(), "Order preview should NOT be visible for 'Myself'.");
    }
}
