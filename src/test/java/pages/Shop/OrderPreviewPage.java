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


    // Container anchored on the literal text "I have a coupon code"
    private final By couponContainer = By.xpath(
            "(//*[self::label or self::div][contains(normalize-space(.),'I have a coupon')])[1]"
    );

    // Finds the element that actually displays the "I have a coupon" text,
// goes to its nearest label/div wrapper, *then* selects the checkbox within.
// Also excludes anything inside the preview table.
    private final By couponCheckbox = By.xpath(
            "(//*[contains(normalize-space(.),'I have a coupon')]" +
                    "/ancestor::*[self::label or self::div][1]" +
                    "//input[@type='checkbox' and not(ancestor::table)])[1]"
    );


    // If Ant applies visual state, this will appear; we use it as a secondary signal (optional)
    private final By couponCheckedBadge = By.xpath(
            "(.//*[contains(normalize-space(.),'coupon')])[1]" +
                    "/ancestor::*[self::div or self::label][1]//span[contains(@class,'ant-checkbox') and contains(@class,'ant-checkbox-checked')]"
    );

    // Optional: row checkbox (for debugging only)
    private final By rowCheckbox = By.cssSelector("input[id^='emails.'][id$='.checkbox']");


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



    // Find the "Order preview" block and scope queries to that container
    private WebElement previewSection() {
        By subHeader = By.xpath(
                "//*[self::h1 or self::h2 or self::h3]" +
                        "[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'order preview')]"
        );

        WebElement h = new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.visibilityOfElementLocated(subHeader));

        // ⬇️ nearest ancestor div/section that ALSO contains the <table> (the outer container you pasted)
        return h.findElement(By.xpath("ancestor::*[self::div or self::section][.//table][1]"));
    }






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
        waitForOverlayGone(5);

        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(8));
        WebElement box = w.until(ExpectedConditions.elementToBeClickable(couponCheckbox));

        scrollCenter(box);

        // 2) Early exit if already in desired state
        if (box.isSelected() == shouldBeChecked) return this;

        // 3) Click the real input (fallback to JS click if it’s visually hidden)
        try {
            safeClick(box);
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", box);
        }

        // 4) Wait by re-locating the element each poll (avoid stale references)
        w.until(d -> d.findElement(couponCheckbox).isSelected() == shouldBeChecked);
        return this;
    }




    @Step("Toggle coupon checkbox (no target state)")
    public OrderPreviewPage toggleCouponCheckbox() {
        waitForOverlayGone(10);

        WebElement input = findFirst(couponCheckbox, Duration.ofSeconds(2));
        if (input == null) {
            WebElement wrapper = new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.visibilityOfElementLocated(couponContainer));
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



    // ---------- Helpers ----------
    // ---------- Helpers ----------
    // ---------- Helpers ----------


    // Does the preview list the expected product name (partial match)?
    public boolean hasProductNamed(String namePart) {
        By productRow = By.xpath("//*[self::div or self::span or self::p or self::td]" +
                "[contains(normalize-space(.), \"" + namePart + "\")]");
        return !driver.findElements(productRow).isEmpty();
    }


    /** True if any product image inside the preview section has attributes that contain ANY of the tokens.
     *  We check alt/title/aria-label/src (case-insensitive).
     */
    public boolean hasProductImageMatching(String... tokensAny) {
        try {
            WebElement section = previewSection();
            for (WebElement img : section.findElements(By.cssSelector("img"))) {
                String alt  = (img.getAttribute("alt")         + "").toLowerCase();
                String tit  = (img.getAttribute("title")       + "").toLowerCase();
                String aria = (img.getAttribute("aria-label")  + "").toLowerCase();
                String src  = (img.getAttribute("src")         + "").toLowerCase();
                for (String t : tokensAny) {
                    String tok = t.toLowerCase();
                    if (alt.contains(tok) || tit.contains(tok) || aria.contains(tok) || src.contains(tok)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }


    /** True if there is at least one product image visible in the preview row. */
    public boolean hasAnyProductImage() {
        try {
            WebElement section = previewSection();
            return section.findElements(By.cssSelector("img")).stream().anyMatch(WebElement::isDisplayed);
        } catch (Exception e) {
            return false;
        }
    }

    // Is the primary continue/pay button enabled (don’t click it here)
    public boolean isProceedEnabled() {
        // Prefer an explicit “Pay with …” button if visible; otherwise use the generic proceed CTA
        try {
            WebElement btn;
            if (!driver.findElements(payWithStripeButton).isEmpty()
                    && driver.findElement(payWithStripeButton).isDisplayed()) {
                btn = driver.findElement(payWithStripeButton);
            } else {
                btn = new WebDriverWait(driver, Duration.ofSeconds(5))
                        .until(ExpectedConditions.presenceOfElementLocated(placeOrderOrProceedBtn));
            }
            String disabled = btn.getAttribute("disabled");
            return btn.isEnabled() && (disabled == null || disabled.equalsIgnoreCase("false"));
        } catch (Exception e) {
            return false;
        }
    }

    /** Read the coupon checkbox state without toggling it. */
    public boolean isCouponChecked() {
        waitForOverlayGone(5);

        WebElement input = findFirst(couponCheckbox, Duration.ofSeconds(2));
        if (input == null) {
            try {
                WebElement wrapper = new WebDriverWait(driver, Duration.ofSeconds(5))
                        .until(ExpectedConditions.visibilityOfElementLocated(couponContainer));
                input = wrapper.findElement(By.xpath(".//input[@type='checkbox']"));
            } catch (TimeoutException e) {
                return false; // section not present
            }
        }
        try {
            return input.isSelected();
        } catch (StaleElementReferenceException e) {
            // one retry
            WebElement refreshed = findFirst(couponCheckbox, Duration.ofSeconds(2));
            return refreshed != null && refreshed.isSelected();
        }
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


    // --- DEBUG UTILITIES (temporary) --------------------------------------------
    @io.qameta.allure.Attachment(value = "Screenshot — {label}", type = "image/png")
    private byte[] attachScreenshot(String label) {
        return ((org.openqa.selenium.TakesScreenshot) driver).getScreenshotAs(org.openqa.selenium.OutputType.BYTES);
    }

    @io.qameta.allure.Attachment(value = "{label}", type = "text/html")
    private String attachHtml(String label, String html) { return html; }

    /** Dump coupon checkbox diagnostics to Allure without changing behavior. */
    public void debugCoupon(String label) {
        try {
            // Scope to the coupon container so we never hit the row checkbox
            WebElement wrapper = new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.visibilityOfElementLocated(couponContainer));
            WebElement input = wrapper.findElement(By.xpath(".//input[@type='checkbox']"));

            JavascriptExecutor js = (JavascriptExecutor) driver;

            Boolean checkedJS = (Boolean) js.executeScript("return !!arguments[0].checked;", input);
            boolean displayed = input.isDisplayed();
            boolean enabled   = input.isEnabled();
            String disabled   = input.getAttribute("disabled");
            String wrapperCls = wrapper.getAttribute("class");

            // Which element sits on top at the input center? (detect hidden overlay)
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> top = (java.util.Map<String, Object>) js.executeScript(
                    "var r=arguments[0].getBoundingClientRect();" +
                            "var x=Math.floor(r.left + r.width/2), y=Math.floor(r.top + r.height/2);" +
                            "var el=document.elementFromPoint(x,y);" +
                            "return {x:x, y:y, html: el ? (el.outerHTML || el.tagName) : 'null'};", input);

            String containerHtml = (String) js.executeScript("return arguments[0].outerHTML;", wrapper);

            attachHtml("Coupon debug — " + label,
                    "<pre>" +
                            "checked(JS): " + checkedJS + "\n" +
                            "displayed   : " + displayed + "\n" +
                            "enabled     : " + enabled + "\n" +
                            "disabledAttr: " + disabled + "\n" +
                            "wrapperClass: " + wrapperCls + "\n" +
                            "topElement  : " + top.get("html") + "\n" +
                            "</pre>" + containerHtml);

            attachScreenshot("coupon " + label);
        } catch (Exception e) {
            attachHtml("Coupon debug — " + label + " (exception)",
                    "<pre>" + org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(e) + "</pre>");
            attachScreenshot("coupon " + label + " (exception)");
        }
    }


}
