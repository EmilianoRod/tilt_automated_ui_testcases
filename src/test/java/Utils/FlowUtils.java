package Utils;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.LoginPage;
import pages.Shop.AssessmentEntryPage;
import pages.Shop.OrderPreviewPage;
import pages.Shop.PurchaseRecipientSelectionPage;
import pages.Shop.Stripe.StripeCheckoutPage;
import pages.menuPages.DashboardPage;
import pages.menuPages.IndividualsPage;
import pages.menuPages.ShopPage;

import java.time.Duration;

public class FlowUtils {


    private WebDriver driver;

    public FlowUtils(WebDriver driver) {
        this.driver = driver;
    }

    public static IndividualsPage goToSuccessUrlEnsureAuthAndOpenIndividuals(WebDriver driver, String successUrl, String baseUrl, String adminEmail, String adminPassword) {

        driver.navigate().to(successUrl);
        waitDocReady(driver);

        // If we landed on sign-in, log back in
        if (driver.getCurrentUrl().contains("/auth/sign-in")) {
            LoginPage login = new LoginPage(driver);
            login.waitUntilLoaded();
            login.login(adminEmail, adminPassword); // do the login
            waitDocReady(driver);
        }

        // Be explicit: navigate to Individuals
        String individualsUrl = baseUrl.replaceAll("/+$","") + "/dashboard/individuals";
        driver.navigate().to(individualsUrl);
        waitDocReady(driver);

        IndividualsPage page = new IndividualsPage(driver).waitUntilLoaded();
        return page;
    }

    private static void waitDocReady(WebDriver d) {
        new WebDriverWait(d, Duration.ofSeconds(15))
                .until(wd -> "complete".equals(
                        ((JavascriptExecutor) wd).executeScript("return document.readyState")));
        try {
            By loaders = By.cssSelector("[role='progressbar'], .ant-spin-spinning, .MuiBackdrop-root, [aria-busy='true']");
            new WebDriverWait(d, Duration.ofSeconds(5))
                    .until(ExpectedConditions.invisibilityOfElementLocated(loaders));
        } catch (Exception ignore) {}
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
