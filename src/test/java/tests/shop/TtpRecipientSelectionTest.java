package tests.shop;

import base.BaseTest;
import io.qameta.allure.*;
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





@Epic("Tilt – Purchases")
@Feature("TTP Assessment Purchase")
@Owner("Emiliano")
public class TtpRecipientSelectionTest extends BaseTest {





    @Test(groups = "ui-only", description = "Recipient selection: Next is disabled until a recipient is chosen; selecting 'Myself' enables it")
    @Severity(SeverityLevel.CRITICAL)
    @Story("TTP purchase – recipient selection must gate progress")
    public void cannotProceedWithoutSelection_thenSelectMyself() throws InterruptedException {
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

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
        sel.waitUntilNextEnabled(Duration.ofSeconds(5));
        Assert.assertTrue(sel.isNextEnabled(), "Next should enable after selecting 'Myself'");

        step("Optionally click Next to ensure it’s clickable (but don’t assert the next page)");
        sel.clickNextCta();
        // Here we deliberately do NOT assert Purchase Information vs Subscription modal.
    }



}
