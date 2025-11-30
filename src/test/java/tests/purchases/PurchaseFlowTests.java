package tests.purchases;

import base.BaseTest;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import pages.BasePage;
import pages.LoginPage;
import pages.Shop.AssessmentEntryPage;
import pages.Shop.OrderPreviewPage;
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

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;



@Epic("Tilt – Purchases & Checkout")
@Feature("PurchaseFlow Tests")
@Owner("Emiliano")
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



    @Test(groups = "ui-only", description = "Myself path → lands on Purchase Information with no order preview")
    @Severity(SeverityLevel.NORMAL)
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






    @Test(groups = {"smoke"}, description = "SM07: Manual entry (1 recipient) reaches Stripe Checkout successfully.")
    @Severity(SeverityLevel.NORMAL)
    public void smoke_manualEntrySingleRecipient_reachesStripeCheckout() throws Exception {

        // Ensure we have a practitioner without TTP
        createFreshUserIfNeeded();

        step("Login with fresh MailSlurp user without TTP subscription");
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        login.waitUntilLoaded();
        DashboardPage dashboard = login.safeLoginAsAdmin(
                freshUser.email,
                freshUser.password,
                Duration.ofSeconds(60)
        );
        Assert.assertTrue(dashboard.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Shop and start purchase via Client / Individual");
        ShopPage shopPage = dashboard.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");

        PurchaseRecipientSelectionPage sel = shopPage
                .clickBuyNowForTrueTilt()
                .waitUntilLoaded();

        step("Select 'Client or Individual' and click Next");
        sel.waitUntilNextDisabled(Duration.ofSeconds(5));
        sel.selectClientOrIndividual();
        sel.waitUntilNextEnabled(Duration.ofSeconds(5));
        sel.clickNext();

        step("Manual entry → 1 recipient");
        waitForLoadersToDisappear(driver(), Duration.ofSeconds(10));

        AssessmentEntryPage entryPage = new AssessmentEntryPage(driver())
                .waitUntilLoaded()
                .selectManualEntry()
                .enterNumberOfIndividuals("1");

        // Use a deterministic but unique-ish email derived from the fresh user
        String recipientEmail = freshUser.email.replace("@", "+sm07@" );
        entryPage.fillUserDetailsAtIndex(1, "Smoke", "SM07", recipientEmail);

        step("Proceed to Order Preview");
        OrderPreviewPage previewPage = entryPage
                .clickProceedToPayment()
                .waitUntilLoaded();

        Assert.assertTrue(previewPage.isLoaded(), "❌ Order preview did not load");

        step("Proceed to Stripe and verify Checkout URL");
        String checkoutUrl = previewPage.proceedToStripeAndGetCheckoutUrl();
        Assert.assertNotNull(checkoutUrl, "❌ Stripe Checkout URL was null");
        Assert.assertTrue(
                checkoutUrl.contains("checkout.stripe.com"),
                "❌ Expected Stripe checkout URL, but got: " + checkoutUrl
        );

        // (Optional) Navigate back so we don't leave the browser stuck on Stripe
        driver().navigate().back();
    }






}
