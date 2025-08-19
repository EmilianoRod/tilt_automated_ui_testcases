package pages.Shop;

   import org.openqa.selenium.*;
   import org.openqa.selenium.support.ui.ExpectedCondition;
   import org.openqa.selenium.support.ui.ExpectedConditions;
   import org.openqa.selenium.support.ui.WebDriverWait;
   import pages.BasePage;
   import pages.Shop.Stripe.StripeCheckoutPage;

   import java.time.Duration;
   import java.util.Set;


public class OrderPreviewPage extends BasePage {

        // Constructor
        public OrderPreviewPage(WebDriver driver) {
            super(driver);
        }


        // --- Tolerant locators ---
        // Header: some builds say "Order Preview/Order review", yours had "Purchase Information"
        private final By header = By.xpath(
                "//*[self::h1 or self::h2][contains(normalize-space(.),'Order Preview') " +
                        " or contains(normalize-space(.),'Order review') " +
                        " or contains(normalize-space(.),'Purchase Information')]"
        );

        private final By couponLabel = By.xpath(
                "//label[contains(normalize-space(.),'I have a coupon code') or contains(normalize-space(.),'coupon')]"
        );

        // Underlying input if you want to assert state toggled
        private final By couponInput = By.xpath(
                "//input[@type='checkbox' and (contains(@id,'coupon') or contains(@name,'coupon') or contains(@aria-label,'coupon'))]"
        );

        private final By previousButton = By.xpath("//button[contains(normalize-space(.),'Previous')]");

        // IMPORTANT: click the BUTTON, not the inner SVG
        private final By payWithStripeButton = By.xpath(
                "//button[normalize-space()='Pay With']"
        );

        // Optional totals (nice for assertions)
        private final By subtotalValue = By.xpath("//*[contains(.,'Subtotal')]/following::*[self::span or self::div][1]");
        private final By totalValue    = By.xpath("//*[contains(.,'Total')]/following::*[self::span or self::div][1]");

        // Optional overlay/spinner your app shows between steps
        private final By blockingOverlay = By.xpath(
                "//*[self::div or self::span][contains(@class,'loading') or contains(@class,'spinner') or contains(@class,'overlay') or @role='progressbar']"
        );


        // ---------- Page readiness ----------

        /** Preferred: call this after you land on preview. */
        public OrderPreviewPage waitUntilLoaded() {
            try { wait.waitForElementInvisible(blockingOverlay); } catch (Exception ignore) {}
            wait.waitForElementVisible(header);
            return this;
        }

        /** Backward‑compatible boolean check (used in your current tests). */
        public boolean isLoaded() {
            try { wait.waitForElementInvisible(blockingOverlay); } catch (Exception ignore) {}
            try { wait.waitForElementVisible(header); return true; } catch (Exception e) { return false; }
        }

        // ---------- Actions ----------

        /** Toggle the "I have a coupon code" checkbox by clicking its label. */
        public void checkCouponCheckbox() {
            try { wait.waitForElementInvisible(blockingOverlay); } catch (Exception ignore) {}
            WebElement label = wait.waitForElementClickable(couponLabel);
            scrollIntoViewCenter(label);
            try { label.click(); } catch (ElementClickInterceptedException e) { jsClick(label); }

            // Optional: verify the input is selected (best effort)
            try {
                WebElement input = driver.findElement(couponInput);
                long end = System.currentTimeMillis() + 1500;
                while (System.currentTimeMillis() < end && !input.isSelected()) {
                    Thread.sleep(100);
                }
            } catch (Exception ignore) {}
        }

        public void clickPrevious() {
            try { wait.waitForElementInvisible(blockingOverlay); } catch (Exception ignore) {}
            WebElement btn = wait.waitForElementClickable(previousButton);
            scrollIntoViewCenter(btn);
            try { btn.click(); } catch (ElementClickInterceptedException e) { jsClick(btn); }
        }

        /** Proceed to Stripe (returns the Stripe page object). */
        public StripeCheckoutPage clickPayWithStripe() {
            try { wait.waitForElementInvisible(blockingOverlay); } catch (Exception ignore) {}
            WebElement btn = wait.waitForElementClickable(payWithStripeButton);
            scrollIntoViewCenter(btn);
            try { btn.click(); } catch (ElementClickInterceptedException e) { jsClick(btn); }
            return new StripeCheckoutPage(driver);
        }


    public String proceedToStripeAndGetCheckoutUrl() {
             try { wait.waitForElementInvisible(blockingOverlay); } catch (Exception ignore) {}
             WebElement btn = wait.waitForElementClickable(payWithStripeButton);
             if ("true".equalsIgnoreCase(btn.getAttribute("disabled"))) {
                     throw new IllegalStateException("Pay button is disabled; cannot proceed to Stripe.");
                 }
             Set<String> before = driver.getWindowHandles();
             String current = driver.getWindowHandle();
             scrollIntoViewCenter(btn);
             try { btn.click(); } catch (ElementClickInterceptedException e) { jsClick(btn); }

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
            try { w.until(stripeReached); }
             catch (TimeoutException te) {
                     for (String h : driver.getWindowHandles()) if (!h.equals(current)) driver.switchTo().window(h);
                     throw new TimeoutException("Failed to reach Stripe Checkout. Current URL: " + driver.getCurrentUrl(), te);
                 }
             String url = driver.getCurrentUrl();
             if (!(url.contains("checkout.stripe.com") && url.contains("/pay/")))
                    throw new IllegalStateException("Unexpected post-click URL: " + url);
             if (url.contains("error") || url.contains("something-went-wrong"))
                     throw new IllegalStateException("Stripe returned an error page: " + url);
             return url;
         }


//    public String proceedToStripeAndGetCheckoutUrl() {
//        try {
//            wait.waitForElementInvisible(blockingOverlay);
//        } catch (Exception ignore) {
//            // Ignore if the overlay is not present
//            System.out.println(ignore.getStackTrace());
//        }
//
//
//
//        WebElement btn = wait.waitForElementClickable(payWithStripeButton);
//        // Ensure it's actually enabled (sometimes visually enabled but disabled attr present)
//        if ("true".equalsIgnoreCase(btn.getAttribute("disabled"))) {
//            throw new IllegalStateException("Pay button is disabled; cannot proceed to Stripe.");
//        }
//
//        // Record current window handles (to detect a popup/new tab)
//        Set<String> before = driver.getWindowHandles();
//        String currentHandle = driver.getWindowHandle();
//
//        // Click
//        scrollIntoViewCenter(btn);
//        try { btn.click(); } catch (ElementClickInterceptedException e) { jsClick(btn); }
//
//        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(45));
//
//        // Wait for either: same-tab URL becomes Stripe OR a new window appears with Stripe
//        ExpectedCondition<Boolean> stripeReached = d -> {
//            try {
//                // same tab?
//                String url = d.getCurrentUrl();
//                if (url != null && url.contains("checkout.stripe.com")) return true;
//
//                // new window?
//                Set<String> after = d.getWindowHandles();
//                if (after.size() > before.size()) {
//                    // switch to the new one and check
//                    for (String h : after) {
//                        if (!before.contains(h)) {
//                            d.switchTo().window(h);
//                            String u = d.getCurrentUrl();
//                            if (u != null && u.contains("checkout.stripe.com")) return true;
//                            // If it opened but not navigated yet, keep waiting
//                            d.switchTo().window(h);
//                            return false;
//                        }
//                    }
//                }
//                return false;
//            } catch (NoSuchWindowException ignored) {
//                return false;
//            }
//        };
//
//        try {
//            w.until(stripeReached);
//        } catch (TimeoutException te) {
//            // Last chance: if a new tab was opened but not yet switched, switch to any non-original
//            for (String h : driver.getWindowHandles()) {
//                if (!h.equals(currentHandle)) {
//                    driver.switchTo().window(h);
//                    break;
//                }
//            }
//            throw new TimeoutException("Did not reach Stripe Checkout after clicking Pay. Current URL: "
//                    + driver.getCurrentUrl(), te);
//        }
//
//        // We should now be on Stripe
//        String url = driver.getCurrentUrl();
//        // Permit both /c/pay/ and newer /pay/ paths
//        if (!(url.contains("checkout.stripe.com/c/pay/") || url.matches("https://checkout\\.stripe\\.com/.*/pay/.*"))) {
//            throw new IllegalStateException("Unexpected post-click URL: " + url);
//        }
//        if (url.contains("something-went-wrong") || url.contains("error")) {
//            throw new IllegalStateException("Stripe returned an error page: " + url);
//        }
//        return url;
//    }





        // ---------- Optional getters for assertions ----------

        public String getSubtotalText() {
            return wait.waitForElementVisible(subtotalValue).getText().trim();
        }

        public String getTotalText() {
            return wait.waitForElementVisible(totalValue).getText().trim();
        }

        // ---------- Small helpers (local, no BasePage edits) ----------

        private void scrollIntoViewCenter(WebElement el) {
            ((JavascriptExecutor) driver)
                    .executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});", el);
        }

        private void jsClick(WebElement el) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
        }

}
