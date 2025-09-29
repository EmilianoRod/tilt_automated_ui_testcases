package pages.Shop;

import io.qameta.allure.Step;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.BasePage;
import pages.Shop.Stripe.StripeCheckoutPage;

import java.time.Duration;

// For typing the recipient in identity checks (e.g., Recipient.MYSELF)
import pages.Shop.PurchaseRecipientSelectionPage.Recipient;

/**
 * "Purchase Information" step.
 *
 * Notes:
 * - URL fragment usually includes /dashboard/shop/ttp
 * - Header shows "Purchase Information"
 * - Banner shows "Assessment purchase for: <label>"
 * - For Team / Clients flows, option tiles are visible (Add to existing team, Create new team, etc.)
 * - For Myself, there is NO "Order preview" section; the primary CTA is "Pay With stripe"
 */
public class PurchaseInformation extends BasePage {

    public PurchaseInformation(WebDriver driver) { super(driver); }

    // ========= Locators =========

    /** Header “Purchase Information” (prefer a data-test when available). */
    public static final By HEADER = By.xpath("//h2[normalize-space()='Purchase Information']");

    /** Alias kept for backwards compatibility with existing tests. */
    public static final By H2_PURCHASE_INFORMATION = HEADER;

    /** Primary Stripe CTA (text may vary slightly). */
    private static final By PAY_WITH_STRIPE = By.xpath("//button[normalize-space()='Pay With']");

    /** Optional “Proceed to payment” CTA (seen on some Team/Clients paths). */
    private static final By BTN_PROCEED_TO_PAYMENT =
            By.xpath("//button[contains(normalize-space(.),'Proceed to payment')]");

    /** Optional “Order preview” block (absent for 'Myself'). */
    private static final By ORDER_PREVIEW_HEADER = By.xpath("//h3[normalize-space()='Order preview']");

    // Team/Clients option tiles (any of these can appear on this step)
// Clickable label for each option (adjacent-sibling <p> holds the text)
    private static final By OPT_ADD_TO_EXISTING = By.xpath("//label[contains(@class,'ant-radio-wrapper')][following-sibling::p[1][contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'add members to existing team')]]");
    private static final By OPT_CREATE_NEW_TEAM = By.xpath("//label[contains(@class,'ant-radio-wrapper')][following-sibling::p[1][contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'create new team')]]");
    private static final By OPT_MANUALLY_ENTER = By.xpath("//label[contains(@class,'ant-radio-wrapper')][following-sibling::p[1][contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'manually enter')]]");
    private static final By OPT_DOWNLOAD_TEMPLATE = By.xpath("//label[contains(@class,'ant-radio-wrapper')][following-sibling::p[1][contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'download template')]]");


    private static final By BTN_CANCEL = By.xpath("//button[normalize-space()='Cancel']");

    /** Broad overlay/backdrop guard */
    private static final By POSSIBLE_LOADER = By.cssSelector(
            "[data-testid='loading'],[data-test='loading'],[role='progressbar']," +
                    ".MuiBackdrop-root,.MuiCircularProgress-root,.ant-spin,.ant-spin-spinning," +
                    ".overlay,.spinner,.backdrop,[aria-busy='true']"
    );

    // ========= Identity & load state =========

    /** Strong load: header visible + overlay gone. */
    public PurchaseInformation waitUntilLoaded() {
        wait.waitForElementVisible(HEADER);
        waitForOverlayGone(Duration.ofSeconds(5));
        return this;
    }

    /** Light heuristic for “this page is present”. */
    public boolean isLoaded() {
        return headerVisible() && (
                payWithStripePresent()
                        || anyTeamClientsTileVisible()
                        || isOrderPreviewVisible() // some flows may still show a thin preview
        );
    }

    /**
     * Robust identity check requiring:
     *  - URL fragment
     *  - Header visible
     *  - “Assessment purchase for: <Recipient>” banner
     *  - At least one of: Stripe CTA, Proceed CTA, OR any Team/Clients tile
     */
    public static boolean isCurrent(WebDriver driver, Recipient r) {
        try {
            String url = driver.getCurrentUrl();
            if (url == null || !url.contains("/dashboard/shop/ttp")) return false;

            PurchaseInformation p = new PurchaseInformation(driver);
            if (!p.headerVisible()) return false;
            if (!p.purchaseForIs(r)) return false;

            return p.payWithStripePresent() ||
                    p.isProceedToPaymentPresent() ||
                    p.anyTeamClientsTileVisible();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Identity without recipient validation. */
    public static boolean isCurrent(WebDriver driver) {
        try {
            String url = driver.getCurrentUrl();
            if (url == null || !url.contains("/dashboard/shop/ttp")) return false;

            PurchaseInformation p = new PurchaseInformation(driver);
            if (!p.headerVisible()) return false;

            return p.payWithStripePresent() ||
                    p.isProceedToPaymentPresent() ||
                    p.anyTeamClientsTileVisible();
        } catch (Throwable t) {
            return false;
        }
    }

    // ========= Simple getters / checks =========

    public boolean headerVisible() { return isVisible(HEADER); }

    /** True if the "Pay With stripe" CTA is present (enabled state may vary). */
    public boolean payWithStripePresent() {
        try { return driver.findElement(PAY_WITH_STRIPE).isDisplayed(); }
        catch (NoSuchElementException e) { return false; }
    }

    public boolean payWithStripeVisible() { return isVisible(PAY_WITH_STRIPE); }

    public boolean isProceedToPaymentPresent() {
        try { return driver.findElement(BTN_PROCEED_TO_PAYMENT).isDisplayed(); }
        catch (NoSuchElementException e) { return false; }
    }

    /** Reads the “Assessment purchase for: …” banner via BasePage helper. */
    public String getPurchaseForText() { return readPurchaseForSelection(); }

    /** True if an Order preview section is shown (expect false for 'Myself'). */
    public boolean isOrderPreviewVisible() {
        try { return driver.findElement(ORDER_PREVIEW_HEADER).isDisplayed(); }
        catch (NoSuchElementException e) { return false; }
    }

    /** Convenience check: does the purchase-for banner match the enum? */
    public boolean purchaseForIs(Recipient r) { return purchaseForIs(r.label()); }

    /** Exposes the header By for isCurrentPage(...) style checks. */
    public By purchaseInformationHeader() { return HEADER; }

    private boolean anyTeamClientsTileVisible() {
        return isVisible(OPT_ADD_TO_EXISTING) ||
                isVisible(OPT_CREATE_NEW_TEAM) ||
                isVisible(OPT_MANUALLY_ENTER) ||
                isVisible(OPT_DOWNLOAD_TEMPLATE);
    }

    // ========= Actions =========

    @Step("Click 'Pay With stripe'")
    public StripeCheckoutPage clickPayWithStripe() {
        waitForOverlayGone(Duration.ofSeconds(5));
        safeClick(PAY_WITH_STRIPE);
        waitForOverlayGone(Duration.ofSeconds(5));
        return new StripeCheckoutPage(driver);
    }

    /** Proceeds when the CTA is “Proceed to payment” (Team/Clients). */
    @Step("Click 'Proceed to payment'")
    public OrderPreviewPage clickProceedToPayment() {
        waitForOverlayGone(Duration.ofSeconds(5));
        safeClick(BTN_PROCEED_TO_PAYMENT);
        waitForOverlayGone(Duration.ofSeconds(5));
        return new OrderPreviewPage(driver);
    }

    /** Smart primary CTA: Stripe if present, else Proceed to payment. */
    @Step("Click primary payment CTA")
    public BasePage clickPrimaryPaymentCta() {
        if (payWithStripePresent()) return clickPayWithStripe();
        if (isProceedToPaymentPresent()) return clickProceedToPayment();
        throw new NoSuchElementException("No primary payment CTA found (Stripe or Proceed).");
    }

    @Step("Choose 'Add members to existing team'")
    public void chooseAddMembersToExistingTeam() { click(OPT_ADD_TO_EXISTING); }

    @Step("Choose 'Create new team'")
    public void chooseCreateNewTeam() { click(OPT_CREATE_NEW_TEAM); }

    @Step("Choose 'Manually enter'")
    public void chooseManuallyEnter() { click(OPT_MANUALLY_ENTER); }

    @Step("Click 'Download template'")
    public void clickDownloadTemplate() { click(OPT_DOWNLOAD_TEMPLATE); }

    @Step("Click 'Cancel'")
    public PurchaseRecipientSelectionPage clickCancel() {
        click(BTN_CANCEL);
        return new PurchaseRecipientSelectionPage(driver);
    }

    // ========= State helpers =========

    /** Returns whether Proceed-to-payment is enabled (ignores overlay). */
    public boolean isProceedEnabled() {
        try {
            WebElement btn = new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.visibilityOfElementLocated(BTN_PROCEED_TO_PAYMENT));
            String aria = btn.getAttribute("aria-disabled");
            return btn.isEnabled() && !"true".equalsIgnoreCase(String.valueOf(aria));
        } catch (TimeoutException e) {
            return false;
        }
    }

    // ========= Internals =========

    private void waitForOverlayGone(Duration timeout) {
        try {
            new WebDriverWait(driver, timeout)
                    .until(ExpectedConditions.invisibilityOfElementLocated(POSSIBLE_LOADER));
        } catch (Throwable ignore) { /* best-effort */ }
    }
}
