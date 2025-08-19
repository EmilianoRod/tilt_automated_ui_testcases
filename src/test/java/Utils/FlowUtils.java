package Utils;

import org.openqa.selenium.WebDriver;
import pages.LoginPage;
import pages.Shop.AssessmentEntryPage;
import pages.Shop.OrderPreviewPage;
import pages.Shop.PurchaseRecipientSelectionPage;
import pages.Shop.Stripe.StripeCheckoutPage;
import pages.menuPages.DashboardPage;
import pages.menuPages.IndividualsPage;
import pages.menuPages.ShopPage;

public class FlowUtils {


    private WebDriver driver;

    public FlowUtils(WebDriver driver) {
        this.driver = driver;
    }

    public DashboardPage loginAsAdmin(String email, String password) {
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();
        return loginPage.login(email, password);
    }

    public void inviteUser(String email) {
        DashboardPage dashboardPage = new DashboardPage(driver);
        IndividualsPage individualsPage = dashboardPage.goToIndividuals();
        // If invite logic is needed, uncomment these lines:
        // individualsPage.enterInviteEmail(email);
        // individualsPage.sendInvite();
        individualsPage.waitUntilUserInviteAppears(email);
    }

    public void purchaseAssessmentForUser(String email) {
        // Go to Shop and start purchase flow
        DashboardPage dashboardPage = new DashboardPage(driver);
        ShopPage shopPage = dashboardPage.goToShop();
        PurchaseRecipientSelectionPage selectionPage = shopPage.clickBuyNowForTrueTilt();
        selectionPage.selectClientOrIndividual();
        selectionPage.clickNext();

        // Assessment Entry
        AssessmentEntryPage entryPage = new AssessmentEntryPage(driver)
                .waitUntilLoaded()
                .selectManualEntry()
                .enterNumberOfIndividuals("1");
        entryPage.fillUserDetailsAtIndex(1, "Test", "User", email);

        // Order Preview
        OrderPreviewPage previewPage = entryPage.clickProceedToPayment().waitUntilLoaded();

        // Stripe Checkout (UI-based: one iframe per field)
        StripeCheckoutPage stripePage = previewPage.clickPayWithStripe();
//        stripePage.waitUntilLoaded(te)
//                .payWithTestCard("4242 4242 4242 4242", "12/34", "123", "90210");
        // If you purposely use a 3DS test card, then also:
        // stripePage.complete3DSIfPresent();

        // (Optional) add a confirmation assertion if you’ve added the page object:
        // OrderConfirmationPage confirm = new OrderConfirmationPage(driver).waitUntilLoaded();
        // Assert.assertTrue(confirm.isSuccessBannerVisible(), "No success banner after payment");
    }

}
