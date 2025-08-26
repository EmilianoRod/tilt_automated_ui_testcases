package pages.Shop;

   import io.qameta.allure.Step;
   import org.openqa.selenium.*;
   import org.openqa.selenium.support.ui.ExpectedCondition;
   import org.openqa.selenium.support.ui.ExpectedConditions;
   import org.openqa.selenium.support.ui.WebDriverWait;
   import pages.BasePage;
   import pages.Shop.Stripe.StripeCheckoutPage;

   import java.time.Duration;
   import java.util.Set;


public class OrderPreviewPage extends BasePage {


    // ---------- Constructor ----------
    public OrderPreviewPage(WebDriver driver) { super(driver); }

    // ---------- Robust, text-anchored locators ----------

    // Case-insensitive match and accept h1/h2/h3
    private final By header = By.xpath(
            "//*[self::h1 or self::h2 or self::h3][" +
                    "contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'order preview') " +
                    "or contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'order review') " +
                    "or contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'purchase information') ]"
    );

    // Coupon: your markup has text like "I have a coupon code" next to a checkbox (often no <label>)
    // Anchor a wrapper that contains that text, then find the input within.
    private final By couponSection = By.xpath(
            "//*[.//*[self::p or self::label][contains(normalize-space(.),'I have a coupon') or contains(normalize-space(.),'coupon')]]"
    );
    private final By couponInput = By.xpath(
            "//input[@type='checkbox' and (contains(translate(@id,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'coupon') " +
                    " or contains(translate(@name,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'coupon') " +
                    " or contains(translate(@aria-label,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'coupon'))]"
    );
    // If Ant applies visual state, this will appear; we use it as a secondary signal (optional)
    private final By couponCheckedBadge = By.xpath(
            "(.//*[contains(normalize-space(.),'coupon')])[1]" +
                    "/ancestor::*[self::div or self::label][1]//span[contains(@class,'ant-checkbox') and contains(@class,'ant-checkbox-checked')]"
    );

    private final By previousButton = By.xpath(
            "//button[normalize-space()='Previous' or .//span[normalize-space()='Previous']]"
    );

    // Place/Proceed variants (covers multiple phrasings)
    private final By placeOrderOrProceedBtn = By.xpath(
            "//button[normalize-space()='Place Order' " +
                    "or normalize-space()='Complete Purchase' " +
                    "or normalize-space()='Proceed to payment' " +
                    "or normalize-space()='Proceed' " +
                    "or normalize-space()='Next' " +
                    "or .//span[normalize-space()='Place Order' or normalize-space()='Complete Purchase' or normalize-space()='Proceed']]"
    );

    // Some builds show explicit Pay With/Pay with Stripe
    private final By payWithStripeButton = By.xpath(
            "//button[contains(normalize-space(.),'Pay with Stripe') " +
                    "or normalize-space(.)='Pay With' " +
                    "or contains(normalize-space(.),'Pay with')]"
    );

    // Totals (label followed by value; your DOM uses <p>, but allow span/div too)
    private final By subtotalValue = By.xpath("//*[normalize-space()='Subtotal']/following::*[self::p or self::span or self::div][1]");
    private final By totalValue    = By.xpath("//*[normalize-space()='Total']/following::*[self::p or self::span or self::div][1]");

    // Broad overlay/backdrop guard
    private final By possibleLoader = By.cssSelector(
            "[data-testid='loading'],[data-test='loading'],[role='progressbar']," +
                    ".MuiBackdrop-root,.MuiCircularProgress-root,.ant-spin,.ant-spin-spinning," +
                    ".overlay,.spinner,.backdrop,[aria-busy='true']"
    );

    // ---------- Page readiness ----------

    @Step("Wait until Order Preview / Purchase Information is loaded")
    public OrderPreviewPage waitUntilLoaded() {
        wait.waitForDocumentReady();
        wait.waitForLoadersToDisappear();
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.visibilityOfElementLocated(header));
        return this;
    }

    public boolean isLoaded() {
        try {
            wait.waitForLoadersToDisappear();
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.visibilityOfElementLocated(header));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    // ---------- Actions ----------

    /**
     * Ensure coupon checkbox is in the desired state. We prefer clicking the real input
     * (since your DOM may not use a <label>), with a wrapper fallback.
     */
    @Step("Set coupon checkbox to: {shouldBeChecked}")
    public OrderPreviewPage setCouponChecked(boolean shouldBeChecked) {
        waitForOverlayGone(10);

        WebElement checkbox = findFirst(couponInput, Duration.ofSeconds(2));
        if (checkbox == null) {
            WebElement wrapper = new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.visibilityOfElementLocated(couponSection));
            checkbox = wrapper.findElement(By.xpath(".//input[@type='checkbox']"));
        }

        scrollCenter(checkbox);
        if (safeIsSelected(checkbox) != shouldBeChecked) {
            safeClick(checkbox);
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.elementSelectionStateToBe(checkbox, shouldBeChecked));
            // ^ built-in EC solves the lambda + final issue
        }
        return this;
    }



    @Step("Toggle coupon checkbox (no target state)")
    public OrderPreviewPage toggleCouponCheckbox() {
        waitForOverlayGone(10);

        WebElement input = findFirst(couponInput, Duration.ofSeconds(2));
        if (input == null) {
            WebElement wrapper = new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.visibilityOfElementLocated(couponSection));
            input = wrapper.findElement(By.xpath(".//input[@type='checkbox']"));
        }

        scrollCenter(input);
        safeClick(input);

        // optional visual confirmation (if Ant class appears)
        try {
            new WebDriverWait(driver, Duration.ofSeconds(2))
                    .until(ExpectedConditions.or(
                            ExpectedConditions.presenceOfElementLocated(couponCheckedBadge),
                            ExpectedConditions.invisibilityOfElementLocated(possibleLoader)
                    ));
        } catch (TimeoutException ignored) { }
        return this;
    }

    @Step("Go back to previous step")
    public void clickPrevious() {
        waitForOverlayGone(5);
        safeClick(previousButton);
        waitForOverlayGone(5);
    }

    @Step("Place order / continue to next step")
    public void clickPlaceOrderOrProceed() {
        waitForOverlayGone(5);
        safeClick(placeOrderOrProceedBtn);
        waitForOverlayGone(5);
    }

    @Step("Click 'Pay with Stripe' (if present)")
    public StripeCheckoutPage clickPayWithStripe() {
        waitForOverlayGone(5);
        safeClick(payWithStripeButton);
        waitForOverlayGone(5);
        return new StripeCheckoutPage(driver);
    }

    /**
     * Clicks pay/proceed and returns the Stripe Checkout URL (handles same/new tab).
     */
    @Step("Proceed to Stripe and capture checkout URL")
    public String proceedToStripeAndGetCheckoutUrl() {
        waitForOverlayGone(5);

        // Prefer explicit Stripe button if visible; otherwise generic proceed
        boolean stripeBtnVisible =
                !driver.findElements(payWithStripeButton).isEmpty() &&
                        driver.findElement(payWithStripeButton).isDisplayed();

        By cta = stripeBtnVisible ? payWithStripeButton : placeOrderOrProceedBtn;

        WebElement btn = new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.elementToBeClickable(cta));

        // defensive: disabled state
        String disabled = btn.getAttribute("disabled");
        if (disabled != null && disabled.equalsIgnoreCase("true")) {
            throw new IllegalStateException("Payment button is disabled; cannot proceed to Stripe.");
        }

        Set<String> before = driver.getWindowHandles();
        String current = driver.getWindowHandle();

        safeClick(cta);

        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(45));
        ExpectedCondition<Boolean> stripeReached = d -> {
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
        };

        w.until(stripeReached);

        String url = driver.getCurrentUrl();
        if (!(url.contains("checkout.stripe.com") && url.contains("/pay/")))
            throw new IllegalStateException("Unexpected post-click URL: " + url);
        if (url.contains("error") || url.contains("something-went-wrong"))
            throw new IllegalStateException("Stripe returned an error page: " + url);
        return url;
    }

    // ---------- Getters for assertions ----------

    @Step("Read subtotal text")
    public String getSubtotalText() {
        return new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.visibilityOfElementLocated(subtotalValue))
                .getText().trim();
    }

    @Step("Read total text")
    public String getTotalText() {
        return new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.visibilityOfElementLocated(totalValue))
                .getText().trim();
    }

    // ---------- Local utilities ----------

    private void waitForOverlayGone(int seconds) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(seconds))
                    .until(ExpectedConditions.invisibilityOfElementLocated(possibleLoader));
        } catch (Exception ignored) { }
    }

    private WebElement findFirst(By by, Duration timeout) {
        try {
            return new WebDriverWait(driver, timeout)
                    .until(ExpectedConditions.presenceOfElementLocated(by));
        } catch (TimeoutException e) {
            return null;
        }
    }

    private void scrollCenter(WebElement el) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center', inline:'nearest'});", el);
    }

    private boolean safeIsSelected(WebElement el) {
        try { return el.isSelected(); } catch (StaleElementReferenceException e) { return false; }
    }

}
