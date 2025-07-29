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
 /*       individualsPage.enterInviteEmail(email);
        individualsPage.sendInvite();*/
        individualsPage.waitUntilUserInviteAppears(email);
    }

    public void purchaseAssessmentForUser(String email) {
        DashboardPage dashboardPage = new DashboardPage(driver);
        ShopPage shopPage = dashboardPage.goToShop();
        PurchaseRecipientSelectionPage selectionPage = shopPage.clickBuyNowForTrueTilt();
        selectionPage.selectClientOrIndividual();
        selectionPage.clickNext();

        AssessmentEntryPage entryPage = new AssessmentEntryPage(driver);
        entryPage.selectManualEntry();
        entryPage.enterNumberOfIndividuals("1");
        entryPage.fillUserDetailsAtIndex(1, "Test", "User", email);
        entryPage.clickProceedToPayment();

        OrderPreviewPage previewPage = new OrderPreviewPage(driver);
        previewPage.clickPayWithStripe();

        StripeCheckoutPage stripePage = new StripeCheckoutPage(driver);
        stripePage.enterEmail(email);
        stripePage.enterCardDetails("4242424242424242", "12/34", "123");
        stripePage.clickPay();
    }


}
