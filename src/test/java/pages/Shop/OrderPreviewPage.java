package pages.Shop;

import io.qameta.allure.Step;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.BasePage;
import pages.Shop.PurchaseRecipientSelectionPage.Recipient;
import pages.Shop.Stripe.StripeCheckoutPage;

import java.time.Duration;
import java.util.Set;

/**
 * Order Preview step (sometimes labeled "Order preview", "Order review", or "Please, review your order.").
 * In some flows this lives under /dashboard/shop/ttp; in others /shop/order-preview.
 * This page object is resilient to both.
 */
public class OrderPreviewPage extends BasePage {

    public OrderPreviewPage(WebDriver driver) { super(driver); }

    // ========= Locators =========

    /** Section header (robust, case-insensitive). Also used as MAIN_PANEL for identity checks. */
    public static final By MAIN_PANEL = By.xpath(
            "//*[self::h1 or self::h2 or self::h3][" +
                    " contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'order preview') or" +
                    " contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'order review') or" +
                    " normalize-space(.)='Please, review your order.' or" +
                    " @data-test='order-preview']"
    );

    /** Optional "Purchase Information" heading sometimes shown above preview. */
    private static final By PURCHASE_INFO_HEADER = By.xpath(
            "//*[@data-test='purchase-information-title' or (self::h1 or self::h2 or self::h3)[normalize-space()='Purchase Information']]"
    );

    /** Coupon block */
    private static final By COUPON_CONTAINER = By.xpath("(//*[self::label or self::div][contains(normalize-space(.),'I have a coupon')])[1]");
    private static final By COUPON_CHECKBOX  = By.xpath(
            "(//*[contains(normalize-space(.),'I have a coupon')]/ancestor::*[self::label or self::div][1]" +
                    "//input[@type='checkbox' and not(ancestor::table)])[1]"
    );
    private static final By COUPON_BADGE     = By.xpath(
            "(.//*[contains(normalize-space(.),'coupon')])[1]/ancestor::*[self::div or self::label][1]" +
                    "//span[contains(@class,'ant-checkbox') and contains(@class,'ant-checkbox-checked')]"
    );

    /** Navigation & CTAs */
    private static final By BTN_PREVIOUS = By.xpath("//button[normalize-space()='Previous' or .//span[normalize-space()='Previous']]");
    private static final By BTN_PROCEED_GENERIC = By.xpath(
            "//*[self::button or self::a][" +
                    " normalize-space()='Place Order' or " +
                    " normalize-space()='Complete Purchase' or " +
                    " normalize-space()='Proceed to payment' or " +
                    " normalize-space()='Proceed' or " +
                    " normalize-space()='Next' or " +
                    " .//span[normalize-space()='Place Order' or normalize-space()='Complete Purchase' or normalize-space()='Proceed']" +
                    "]"
    );
    private static final By BTN_PAY_WITH_STRIPE = By.xpath(
            "//*[self::button or self::a]" +
                    "[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'pay with') and " +
                    " contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'stripe')]"
    );

    /** Totals */
    private static final By SUBTOTAL_VALUE = By.xpath("//*[normalize-space()='Subtotal']/following::*[self::p or self::span or self::div][1]");
    private static final By TOTAL_VALUE    = By.xpath("//*[normalize-space()='Total']/following::*[self::p or self::span or self::div][1]");

    /** Generic loaders/backdrops */
    private static final By POSSIBLE_LOADER = By.cssSelector(
            "[data-testid='loading'],[data-test='loading'],[role='progressbar']," +
                    ".MuiBackdrop-root,.MuiCircularProgress-root,.ant-spin,.ant-spin-spinning," +
                    ".overlay,.spinner,.backdrop,[aria-busy='true']"
    );

    // ========= Page readiness / identity =========

    @Step("Wait until Order Preview is loaded")
    public OrderPreviewPage waitUntilLoaded() {
        wait.waitForDocumentReady();
        wait.waitForLoadersToDisappear();
        new WebDriverWait(driver, Duration.ofSeconds(12))
                .until(ExpectedConditions.or(
                        ExpectedConditions.visibilityOfElementLocated(MAIN_PANEL),
                        ExpectedConditions.visibilityOfElementLocated(PURCHASE_INFO_HEADER)
                ));
        return this;
    }

    public boolean isLoaded() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.visibilityOfElementLocated(MAIN_PANEL));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /** Identity (recipient-agnostic). Accepts either /shop/order-preview or /dashboard/shop/ttp with the section header. */
    public static boolean isCurrent(WebDriver driver) {
        try {
            String url = driver.getCurrentUrl();
            if (url == null) return false;
            if (!url.contains("/shop/order-preview") && !url.contains("/dashboard/shop/ttp")) return false;
            return !driver.findElements(MAIN_PANEL).isEmpty();
        } catch (Throwable t) { return false; }
    }

    /** Identity + banner matches the selected recipient. */
    public static boolean isCurrent(WebDriver driver, Recipient r) {
        try {
            if (!isCurrent(driver)) return false;
            return new OrderPreviewPage(driver).purchaseForIs(r);
        } catch (Throwable t) { return false; }
    }

    // ========= Content helpers =========

    /** “Assessment purchase for: …” via BasePage helper. */
    public String getPurchaseForText() { return readPurchaseForSelection(); }

    public boolean purchaseForIs(Recipient r) { return purchaseForIs(r.label()); }

    /** Read subtotal/total text (e.g., "$49"). */
    @Step("Read subtotal")
    public String getSubtotalText() {
        return new WebDriverWait(driver, Duration.ofSeconds(6))
                .until(ExpectedConditions.visibilityOfElementLocated(SUBTOTAL_VALUE))
                .getText().trim();
    }

    @Step("Read total")
    public String getTotalText() {
        return new WebDriverWait(driver, Duration.ofSeconds(6))
                .until(ExpectedConditions.visibilityOfElementLocated(TOTAL_VALUE))
                .getText().trim();
    }

    /** Does the table/section contain this product name (partial match)? */
    public boolean hasProductNamed(String namePart) {
        By row = By.xpath("//*[self::div or self::span or self::p or self::td][contains(normalize-space(.), \"" + esc(namePart) + "\")]");
        return !driver.findElements(row).isEmpty();
    }

    /** Any product image present in the preview area? */
    public boolean hasAnyProductImage() {
        try {
            return driver.findElements(By.cssSelector("img")).stream().anyMatch(WebElement::isDisplayed);
        } catch (Throwable t) { return false; }
    }

    /** Match image attributes against any token (alt/title/aria-label/src). */
    public boolean hasProductImageMatching(String... tokensAny) {
        try {
            for (WebElement img : driver.findElements(By.cssSelector("img"))) {
                String alt  = (img.getAttribute("alt")        + "").toLowerCase();
                String tit  = (img.getAttribute("title")      + "").toLowerCase();
                String aria = (img.getAttribute("aria-label") + "").toLowerCase();
                String src  = (img.getAttribute("src")        + "").toLowerCase();
                for (String t : tokensAny) {
                    String tok = t.toLowerCase();
                    if (alt.contains(tok) || tit.contains(tok) || aria.contains(tok) || src.contains(tok)) return true;
                }
            }
            return false;
        } catch (Throwable t) { return false; }
    }

    // ========= Coupon =========

    /** Read coupon checkbox state (does not toggle). */
    public boolean isCouponChecked() {
        waitForOverlayGone(Duration.ofSeconds(5));
        WebElement input = findFirst(COUPON_CHECKBOX, Duration.ofSeconds(2));
        if (input == null) {
            try {
                WebElement wrapper = new WebDriverWait(driver, Duration.ofSeconds(5))
                        .until(ExpectedConditions.visibilityOfElementLocated(COUPON_CONTAINER));
                input = wrapper.findElement(By.xpath(".//input[@type='checkbox']"));
            } catch (TimeoutException e) { return false; }
        }
        try { return input.isSelected(); }
        catch (StaleElementReferenceException e) {
            WebElement refreshed = findFirst(COUPON_CHECKBOX, Duration.ofSeconds(2));
            return refreshed != null && refreshed.isSelected();
        }
    }

    /** Set coupon checkbox to desired state (idempotent). */
    @Step("Set coupon checkbox to: {state}")
    public OrderPreviewPage setCouponChecked(boolean state) {
        waitForOverlayGone(Duration.ofSeconds(5));
        WebElement box = new WebDriverWait(driver, Duration.ofSeconds(8))
                .until(ExpectedConditions.elementToBeClickable(COUPON_CHECKBOX));

        scrollCenter(box);
        if (box.isSelected() != state) {
            try { safeClick(box); }
            catch (Exception e) { ((JavascriptExecutor) driver).executeScript("arguments[0].click();", box); }
        }

        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until((ExpectedCondition<Boolean>) d -> d.findElement(COUPON_CHECKBOX).isSelected() == state);
        return this;
    }

    @Step("Toggle coupon checkbox")
    public OrderPreviewPage toggleCouponCheckbox() {
        waitForOverlayGone(Duration.ofSeconds(5));
        WebElement input = findFirst(COUPON_CHECKBOX, Duration.ofSeconds(3));
        if (input == null) {
            WebElement wrap = new WebDriverWait(driver, Duration.ofSeconds(6))
                    .until(ExpectedConditions.visibilityOfElementLocated(COUPON_CONTAINER));
            input = wrap.findElement(By.xpath(".//input[@type='checkbox']"));
        }
        scrollCenter(input);
        safeClick(input);
        try {
            new WebDriverWait(driver, Duration.ofSeconds(2))
                    .until(ExpectedConditions.or(
                            ExpectedConditions.presenceOfElementLocated(COUPON_BADGE),
                            ExpectedConditions.invisibilityOfElementLocated(POSSIBLE_LOADER)
                    ));
        } catch (TimeoutException ignored) {}
        return this;
    }

    // ========= Actions (navigation / payment) =========

    @Step("Click Previous")
    public void clickPrevious() {
        waitForOverlayGone(Duration.ofSeconds(3));
        safeClick(BTN_PREVIOUS);
        waitForOverlayGone(Duration.ofSeconds(3));
    }

    /** Smart primary CTA: prefer explicit Stripe button, else generic proceed. */
    @Step("Click primary payment CTA")
    public BasePage clickPrimaryPaymentCta() {
        waitForOverlayGone(Duration.ofSeconds(5));
        if (isElementVisible(BTN_PAY_WITH_STRIPE)) return clickPayWithStripe();
        safeClick(BTN_PROCEED_GENERIC);
        waitForOverlayGone(Duration.ofSeconds(5));
        return this;
    }

    @Step("Click 'Pay With stripe'")
    public StripeCheckoutPage clickPayWithStripe() {
        waitForOverlayGone(Duration.ofSeconds(3));
        safeClick(BTN_PAY_WITH_STRIPE);
        waitForOverlayGone(Duration.ofSeconds(3));
        return new StripeCheckoutPage(driver);
    }

    /** Is the primary proceed/pay button enabled (without clicking)? */
    public boolean isProceedEnabled() {
        try {
            WebElement btn = isElementVisible(BTN_PAY_WITH_STRIPE)
                    ? driver.findElement(BTN_PAY_WITH_STRIPE)
                    : new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.presenceOfElementLocated(BTN_PROCEED_GENERIC));
            String disabled = btn.getAttribute("disabled");
            return btn.isEnabled() && (disabled == null || disabled.equalsIgnoreCase("false"));
        } catch (Throwable t) { return false; }
    }

    /**
     * Clicks the primary pay/proceed CTA and returns the Stripe Checkout URL (handles same/new tab).
     */
    @Step("Proceed to Stripe and capture checkout URL")
    public String proceedToStripeAndGetCheckoutUrl() {
        waitForOverlayGone(Duration.ofSeconds(5));
        By cta = isElementVisible(BTN_PAY_WITH_STRIPE) ? BTN_PAY_WITH_STRIPE : BTN_PROCEED_GENERIC;

        WebElement btn = new WebDriverWait(driver, Duration.ofSeconds(12))
                .until(ExpectedConditions.elementToBeClickable(cta));

        String disabled = btn.getAttribute("disabled");
        if ("true".equalsIgnoreCase(disabled)) {
            throw new IllegalStateException("Payment button is disabled; cannot proceed to Stripe.");
        }

        Set<String> before = driver.getWindowHandles();
        String current     = driver.getWindowHandle();

        safeClick(cta);

        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(45));
        w.until(d -> {
            try {
                String url = d.getCurrentUrl();
                if (url != null && url.contains("checkout.stripe.com")) return true;

                Set<String> after = d.getWindowHandles();
                if (after.size() > before.size()) {
                    for (String h : after) {
                        if (!before.contains(h)) {
                            d.switchTo().window(h);
                            String u = d.getCurrentUrl();
                            return u != null && u.contains("checkout.stripe.com");
                        }
                    }
                }
                return false;
            } catch (NoSuchWindowException e) { return false; }
        });

        String url = driver.getCurrentUrl();
        if (!(url.contains("checkout.stripe.com") && url.contains("/pay/"))) {
            throw new IllegalStateException("Unexpected post-click URL: " + url);
        }
        if (url.contains("error") || url.contains("something-went-wrong")) {
            throw new IllegalStateException("Stripe returned an error page: " + url);
        }
        return url;
    }

    // ========= Private helpers =========

    private void waitForOverlayGone(Duration timeout) {
        try {
            new WebDriverWait(driver, timeout)
                    .until(ExpectedConditions.invisibilityOfElementLocated(POSSIBLE_LOADER));
        } catch (Throwable ignore) {}
    }

    private WebElement findFirst(By by, Duration timeout) {
        try {
            return new WebDriverWait(driver, timeout)
                    .until(ExpectedConditions.presenceOfElementLocated(by));
        } catch (TimeoutException e) { return null; }
    }

    private void scrollCenter(WebElement el) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center', inline:'nearest'});", el);
    }

    private boolean isElementVisible(By by) {
        try { return driver.findElement(by).isDisplayed(); }
        catch (NoSuchElementException e) { return false; }
    }
}
