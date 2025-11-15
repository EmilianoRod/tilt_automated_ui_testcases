package tests.purchases;

import base.BaseTest;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import pages.BasePage;
import pages.LoginPage;
import pages.Shop.PurchaseInformation;
import pages.Shop.PurchaseRecipientSelectionPage;
import pages.SignUp.SignUpPage;
import pages.SignUp.TestUsers;
import pages.SignUp.TestUsers.UiUser;
import pages.menuPages.DashboardPage;
import pages.menuPages.ShopPage;

import java.time.Duration;

import static Utils.WaitUtils.waitForLoadersToDisappear;
import static io.qameta.allure.Allure.step;

public class PurchaseFlowTests extends BaseTest {

    // One per suite; re-used across tests
    private static volatile UiUser freshUser;

    public void createFreshUserIfNeeded() throws Exception {
        if (freshUser != null) return;

        // 1) Build a fresh logical user (MailSlurp inbox + alias + names + password)
        freshUser = TestUsers.newMailSlurpUserForSignup();

        // 2) Go through Sign-up flow in UI with that user
        SignUpPage signUp = new SignUpPage(driver());
        signUp.navigateTo();
        signUp.completeSignUp(
                freshUser.firstName,
                freshUser.lastName,
                freshUser.email,
                freshUser.password
        );

        // Now freshUser has NO TTP subscription.
    }

    @Test(groups = "ui-only",
            description = "Myself path → lands on Purchase Information with no order preview")
    public void myselfFlow_goesToPurchaseInformation() throws Exception {

        // 🔹 driver() is safe to use here
        createFreshUserIfNeeded();


        step("Login with fresh MailSlurp user without TTP subscription");

        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        DashboardPage dashboard = login.safeLoginAsAdmin(
                freshUser.email,
                freshUser.password,
                Duration.ofSeconds(20)
        );

        step("Go to Shop and start purchase flow of TTP");
        ShopPage shopPage = dashboard.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");
        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt().waitUntilLoaded();

        step("Select 'Myself' and continue");
        sel.waitUntilNextDisabled(Duration.ofSeconds(5));
        sel.selectMyself();
        sel.waitUntilNextEnabled(Duration.ofSeconds(5));
        sel.clickNextCta();

        step("Validate we are on Purchase Information (Myself path has no Order preview)");
        waitForLoadersToDisappear(driver(), Duration.ofSeconds(10));
        boolean onPurchaseInfo = BasePage.isCurrentPage(
                driver(),
                "/dashboard/shop/ttp",
                PurchaseInformation.H2_PURCHASE_INFORMATION
        );

        Assert.assertTrue(onPurchaseInfo, "Expected Purchase Information. URL: " + driver().getCurrentUrl());

        PurchaseInformation pi = new PurchaseInformation(driver()).waitUntilLoaded();
        Assert.assertTrue(pi.headerVisible(), "Purchase Information header should be visible.");
        Assert.assertTrue(pi.payWithStripeVisible(), "'Pay With stripe' CTA should be visible.");
        Assert.assertEquals(pi.getPurchaseForText(), "Myself");
        Assert.assertFalse(pi.isOrderPreviewVisible(), "Order preview should NOT be visible for 'Myself'.");
    }
}
