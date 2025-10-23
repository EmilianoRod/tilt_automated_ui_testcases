package pages.Shop;

import io.qameta.allure.Step;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.BasePage;
import pages.Shop.PurchaseRecipientSelectionPage.Recipient;
import pages.Shop.Stripe.StripeCheckoutPage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;

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
    private static final By BTN_PAY_WITH_STRIPE = By.xpath("//button[normalize-space()=\"Pay With\"]");

    /** Totals */
    private static final By TOTAL_VALUE = By.xpath(
            "(//*[translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='total'])[last()]" +
                    "/following-sibling::*[self::p or self::span or self::div][1]"
    );


    private static final By SUBTOTAL_VALUE = By.xpath(
            "(" +
                    // exact 'Subtotal' (case-insensitive), tolerating hyphen as space
                    "(//*[translate(normalize-space(translate(.,'-',' ')),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='subtotal' " +
                    "or translate(normalize-space(translate(.,'-',' ')),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='sub total'])[last()]" +
                    // value right next to the label
                    "/following-sibling::*[self::p or self::span or self::div][1]" +
                    " | " +
                    // fallback: value is a sibling inside the same parent container
                    "(//*[translate(normalize-space(translate(.,'-',' ')),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='subtotal' " +
                    "or translate(normalize-space(translate(.,'-',' ')),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='sub total'])[last()]" +
                    "/parent::*/*[self::p or self::span or self::div][1]" +
                    ")"
    );


    private static final By MEMBER_ROWS = By.xpath("//tr[.//input[@type='checkbox'] or .//*[@role='checkbox']]");

    private static final By TOGGLE_INSIDE_ROW = By.xpath(".//input[@type='checkbox'] | .//*[@role='checkbox']");

    private static final By PREVIEW_ROWS = By.cssSelector("table tbody tr");
    // cost is the 3rd data cell in your markup (Email, Product, Cost, Checkbox)
    private static final By COST_CELL_IN_ROW = By.cssSelector("td:nth-of-type(3)");
    // the toggle is the 4th cell (native checkbox)
    private static final By CHECKBOX_IN_ROW = By.cssSelector("td input[type='checkbox']");



    /** Generic loaders/backdrops */
    private static final By POSSIBLE_LOADER = By.cssSelector(
            "[data-testid='loading'],[data-test='loading'],[role='progressbar']," +
                    ".MuiBackdrop-root,.MuiCircularProgress-root,.ant-spin,.ant-spin-spinning," +
                    ".overlay,.spinner,.backdrop,[aria-busy='true']"
    );



    // Any row that has a checkbox-ish control (native or ARIA)




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
        scrollToElement(driver.findElement(BTN_PAY_WITH_STRIPE));


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














    // ===== Money helpers (add) =====

    // --- quick, non-blocking text read (returns null if missing) ---
    // Visible element text (no blocking wait)
    private String queryTextQuick(By by) {
        try {
            WebElement el = driver.findElements(by).stream()
                    .filter(WebElement::isDisplayed).findFirst().orElse(null);
            return el == null ? null : el.getText().trim();
        } catch (Exception ignored) { return null; }
    }

    // normalize for comparisons and parsing
    private String normMoney(String s) {
        if (s == null) return "";
        // remove currency symbols, nbsp, spaces, and thousands separators
        return s.replace('\u00A0', ' ')
                .replaceAll("[^0-9.,-]", "")
                .replace(",", "")
                .trim();
    }


    // Sum $ values from selected rows
    private BigDecimal sumSelectedRowCosts() {
        BigDecimal sum = java.math.BigDecimal.ZERO;
        for (WebElement row : driver.findElements(PREVIEW_ROWS)) {
            try {
                WebElement cb  = row.findElement(CHECKBOX_IN_ROW);
                boolean on = cb.isSelected() || "true".equalsIgnoreCase(cb.getAttribute("checked"));
                if (!on) continue;

                WebElement costCell = row.findElement(COST_CELL_IN_ROW);
                sum = sum.add(parseMoney(costCell.getText()));
            } catch (Exception ignored) { /* skip broken rows */ }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }


    // ===== Money helpers (replace) =====

    // Prefer label; fallback to computed table sum; finally to Total
    public BigDecimal getSubtotal() {
        String fromLabel = queryTextQuick(SUBTOTAL_VALUE); // may be null on this layout
        if (fromLabel != null && !fromLabel.isBlank()) {
            return parseMoney(fromLabel);
        }
        // No label → compute from selected rows
        BigDecimal computed = sumSelectedRowCosts();
        if (computed.compareTo(BigDecimal.ZERO) > 0) return computed;

        // As a last resort (e.g., hidden rows), use Total
        return getTotal();
    }

    public BigDecimal getTotal() {
        String txt = queryTextQuick(TOTAL_VALUE);
        if (txt == null || normMoney(txt).isEmpty()) return BigDecimal.ZERO;
        return parseMoney(txt);
    }

    private BigDecimal parseMoney(String s) {
        if (s == null) return BigDecimal.ZERO;
        String clean = normMoney(s);
        if (clean.isBlank()) return BigDecimal.ZERO;
        // “98” -> 98.00, “98.5” -> 98.50, “98.50” unchanged
        return new BigDecimal(clean).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean equalsMoney(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return false;
        return a.setScale(2, java.math.RoundingMode.HALF_UP)
                .compareTo(b.setScale(2, java.math.RoundingMode.HALF_UP)) == 0;
    }


    private String readTotalsSignatureQuick() {
        String sub = queryTextQuick(SUBTOTAL_VALUE); // may be null
        String tot = queryTextQuick(TOTAL_VALUE);    // should exist
        return normMoney(sub) + "|" + normMoney(tot);
    }
    public void waitTotalsStable() {
        waitForOverlayGone(Duration.ofSeconds(5));

        final long deadline = System.currentTimeMillis() + 12000;
        String prev = null;
        int stable = 0;

        while (System.currentTimeMillis() < deadline) {
            String sig = readTotalsSignatureQuick();
            if (sig.equals(prev) && !sig.equals("|")) { // avoid empty signature
                if (++stable >= 3) return;              // 3 consecutive stable reads
            } else {
                stable = 0;
                prev = sig;
            }
            try { Thread.sleep(150); } catch (InterruptedException ignored) {}
        }
        throw new TimeoutException("Totals did not stabilize. Last signature: " + (prev == null ? "<none>" : prev));
    }



    // ===== Member row helpers =====


    private static By memberRowByIndex(int oneBased) {
        return By.xpath("(" + "//tr[.//input[@type='checkbox'] or .//*[@role='checkbox']]" + ")[" + oneBased + "]");
    }


    // Count how many member toggles are ON
    public int getSelectedCount() {
        int c = 0;
        for (WebElement row : driver.findElements(MEMBER_ROWS)) {
            try {
                WebElement t = row.findElement(TOGGLE_INSIDE_ROW);
                boolean on = "true".equalsIgnoreCase(t.getAttribute("aria-checked")) || t.isSelected();
                if (on) c++;
            } catch (Exception ignored) {}
        }
        return c;
    }

    // Click a member toggle by 1-based row index (scrolls and JS-clicks if needed)
    public void toggleMemberByIndex(int oneBased) {
        WebElement row = new WebDriverWait(driver, Duration.ofSeconds(8))
                .until(ExpectedConditions.presenceOfElementLocated(memberRowByIndex(oneBased)));
        WebElement t = row.findElement(TOGGLE_INSIDE_ROW);
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'})", t);
        try { t.click(); } catch (Exception e) { ((JavascriptExecutor) driver).executeScript("arguments[0].click()", t); }
    }

    // ===== Tax / Discount (safe: return ZERO if not present) =====
    public BigDecimal getTaxOrZero() {
        try {
            WebElement el = driver.findElement(By.xpath(
                    "//*[normalize-space()='Tax' or normalize-space()='Taxes']" +
                            "/following::*[self::p or self::span or self::div][1]"
            ));
            return parseMoney(el.getText());
        } catch (NoSuchElementException e) {
            return BigDecimal.ZERO;
        }
    }

    public BigDecimal getDiscountOrZero() {
        try {
            WebElement el = driver.findElement(By.xpath(
                    "//*[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'discount')]" +
                            "/following::*[self::p or self::span or self::div][1]"
            ));
            return parseMoney(el.getText());
        } catch (NoSuchElementException e) {
            return BigDecimal.ZERO;
        }
    }

    // ===== Derived price =====
    public BigDecimal deriveUnitPrice() {
        int n = Math.max(1, getSelectedCount());
        return getSubtotal().divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
    }








    // ====== Product assignment helpers (ADD) ======

    // 1) Email cell hook (first column in your markup)
    private static final By EMAIL_CELL_IN_ROW = By.cssSelector("td:nth-of-type(1)");

    // ---- helpers ----
    private static String cleanText(String t) {
        if (t == null) return "";
        return t.replace('\u00A0',' ')
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .trim()
                .toLowerCase();
    }




    // Canonicalize email for matching: lowercase, strip "+tag" in local part
    private static String canonEmail(String email) {
        String s = cleanText(email);
        int at = s.indexOf('@');
        if (at < 0) return s;
        String local = s.substring(0, at);
        String domain = s.substring(at + 1);
        int plus = local.indexOf('+');
        if (plus >= 0) local = local.substring(0, plus);
        return local + "@" + domain;
    }

    // Optional: try to read visible text via JS (more reliable on React UIs)
    private static String innerText(WebDriver d, WebElement el) {
        try { return String.valueOf(((JavascriptExecutor) d).executeScript("return arguments[0].innerText;", el)); }
        catch (Exception ignored) { return el.getText(); }
    }

    // Extract parts of an email: local (before '+'), tag (between '+' and '@'), and domain
    private static class EmailParts {
        final String local, tag, domain;
        EmailParts(String email) {
            String s = cleanText(email);
            int at = s.indexOf('@');
            if (at < 0) { local = s; tag = ""; domain = ""; return; }
            String left = s.substring(0, at);
            domain = s.substring(at + 1);
            int plus = left.indexOf('+');
            if (plus >= 0) { local = left.substring(0, plus); tag = left.substring(plus + 1); }
            else { local = left; tag = ""; }
        }
    }


    // Find row index (1-based) by email; waits for rows and uses innerText




    private static final By PRODUCT_CELL_IN_ROW = By.cssSelector("td:nth-of-type(2)");
    private static final By ROW_MAIN_TOGGLE = By.cssSelector("td:last-of-type input[type='checkbox']");

    // Safely quote any string for XPath 1.0 (handles single quotes)
    private static String xpLit(String s) {
        if (s == null) return "''";
        if (!s.contains("'")) return "'" + s + "'";
        // concat('a', "'", 'b')
        String[] parts = s.split("'");
        StringBuilder sb = new StringBuilder("concat(");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(", \"'\", ");
            sb.append("'").append(parts[i]).append("'");
        }
        sb.append(")");
        return sb.toString();
    }

    // Quick check: does this row's Product cell mention the token?
    private boolean rowLooksLikeProduct(WebElement row, String productName) {
        if (productName == null || productName.isBlank()) return true; // accept any
        String tok = productName.trim().toLowerCase();

        // 1) image attributes (typical for this UI)
        for (WebElement img : row.findElements(By.cssSelector("td:nth-of-type(2) img"))) {
            String alt  = String.valueOf(img.getAttribute("alt")).toLowerCase();
            String tit  = String.valueOf(img.getAttribute("title")).toLowerCase();
            String aria = String.valueOf(img.getAttribute("aria-label")).toLowerCase();
            String src  = String.valueOf(img.getAttribute("src")).toLowerCase();
            if (alt.contains(tok) || tit.contains(tok) || aria.contains(tok) || src.contains(tok)) return true;
        }
        // 2) visible text fallback
        try {
            String txt = row.findElement(PRODUCT_CELL_IN_ROW).getText().toLowerCase();
            if (txt.contains(tok)) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private WebElement findProductToggleInRow(WebElement row, String productName) {
        // If the row doesn't even mention the product, bail early
        if (!rowLooksLikeProduct(row, productName)) return null;

        // --- Try future-proof dedicated toggles first (none on this layout, but keep for later) ---
        String tok = productName == null ? "" : productName.trim();
        String tokLit = xpLit(tok);
        try {
            WebElement byAria = row.findElements(By.xpath(
                    ".//*[@role='checkbox' or self::input[@type='checkbox']]" +
                            "[contains(translate(@aria-label,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'), " +
                            " translate(" + tokLit + ",'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'))]"
            )).stream().filter(WebElement::isDisplayed).findFirst().orElse(null);
            if (byAria != null) return byAria;
        } catch (Exception ignored) {}

        try {
            WebElement byIdName = row.findElements(By.xpath(
                    ".//input[@type='checkbox' and (" +
                            "  contains(translate(@id,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'), " +
                            "          translate(" + tokLit + ",'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')) or " +
                            "  contains(translate(@name,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'), " +
                            "          translate(" + tokLit + ",'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'))" +
                            ")]"
            )).stream().filter(WebElement::isDisplayed).findFirst().orElse(null);
            if (byIdName != null) return byIdName;
        } catch (Exception ignored) {}

        try {
            WebElement byLabel = row.findElements(By.xpath(
                    ".//label[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'), " +
                            "                translate(" + tokLit + ",'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'))]" +
                            "//input[@type='checkbox']"
            )).stream().filter(WebElement::isDisplayed).findFirst().orElse(null);
            if (byLabel != null) return byLabel;
        } catch (Exception ignored) {}

        // --- Fallback for this UI: use the row's main checkbox in col 4 ---
        try {
            return row.findElement(ROW_MAIN_TOGGLE);
        } catch (NoSuchElementException e) {
            return row.findElement(By.cssSelector("input[type='checkbox']")); // ultimate fallback
        }
    }





    // Read assignment state for a given email/product. Falls back to row main checkbox.
    public boolean isProductAssigned(String email, String productName) {
        WebElement row = new WebDriverWait(driver, Duration.ofSeconds(8))
                .until(d -> findRowByEmailLive(d, email));
        WebElement toggle = findProductToggleInRow(row, productName);
        WebElement cb = toggle != null ? toggle : row.findElement(ROW_MAIN_TOGGLE);
        String aria = String.valueOf(cb.getAttribute("aria-checked"));
        return (!aria.isBlank() && "true".equalsIgnoreCase(aria))
                || cb.isSelected()
                || "true".equalsIgnoreCase(String.valueOf(cb.getAttribute("checked")));
    }



    // Ensure product assigned state for email; returns true if a dedicated product toggle was used, false if fallback (row toggle).
// Ensure product assigned state for email; returns this page
    public OrderPreviewPage setProductAssigned(String email, String productLabel, boolean desired) {
        // normalize once (your normTxt should lower-case & trim; if not, do email.toLowerCase(Locale.ROOT).trim())
        final String want = normTxt(email);

        // 1) find row now (for scroll) using the robust live finder
        WebElement row0 = new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(d -> findRowByEmailLive(d, want));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", row0);

        // 2) read current state via property/selected (never @checked)
        WebElement cb0 = rowCheckbox(row0);
        boolean current = isChecked(cb0);
        System.out.println("[setProductAssigned] email=" + want + " current=" + current + " desired=" + desired);
        if (current == desired) {
            System.out.println("[setProductAssigned] no-op (already " + desired + ")");
            return this;
        }

        // 3) click to change
        cb0.click();

        // 4) wait until the checkbox PROPERTY reflects the desired state, re-finding each poll
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(12));
        wait.ignoreAll(java.util.List.of(
                org.openqa.selenium.StaleElementReferenceException.class,
                org.openqa.selenium.NoSuchElementException.class
        ));

        boolean ok = wait.until(d -> {
            WebElement r = findRowByEmailLive(d, want);  // reacquire row after potential re-render
            if (r == null) return false;
            WebElement c = rowCheckbox(r);
            return isChecked(c) == desired;
        });

        if (!ok) {
            // rich diagnostics
            debugDumpPreviewTable("timeout:await-assignment:" + want + ":" + desired);
            WebElement t = driver.findElement(By.cssSelector("table"));
            String html = String.valueOf(((JavascriptExecutor) driver).executeScript("return arguments[0].outerHTML;", t));
            System.out.println("──── table.outerHTML [timeout:" + want + ":" + desired + "] ────\n" + html + "\n──────────────────────────────────────");
            throw new TimeoutException("Failed to reach desired checkbox state for " + want + " -> " + desired);
        }

        return this;
    }



    // Small, reliable reader that copes with React controlled inputs
    private boolean readToggleChecked(WebElement el) {
        try {
            String aria = String.valueOf(el.getAttribute("aria-checked"));
            if (!aria.isBlank()) return "true".equalsIgnoreCase(aria);
            if ("input".equalsIgnoreCase(el.getTagName()))
                return el.isSelected() || "true".equalsIgnoreCase(String.valueOf(el.getAttribute("checked")));
            // final JS fallback
            return Boolean.TRUE.equals(((JavascriptExecutor) driver).executeScript("return !!arguments[0].checked;", el));
        } catch (StaleElementReferenceException sere) {
            return false; // next poll will re-find
        }
    }






    // Wait until table has at least one row
    private void waitForRows() {
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(d -> !d.findElements(PREVIEW_ROWS).isEmpty());
    }

    // JS-backed text read (more reliable than getText() on some layouts)
    private String jsText(WebElement el) {
        try {
            Object o = ((JavascriptExecutor) driver)
                    .executeScript("return (arguments[0].textContent || arguments[0].innerText || '');", el);
            return String.valueOf(o);
        } catch (Exception e) {
            return el.getText();
        }
    }

    private String safeCellText(WebElement row, By by) {
        try { return normTxt(jsText(row.findElement(by))); } catch (Exception e) { return "<err>"; }
    }


    // Call this anywhere to log what Selenium can see right now.
    public void debugDumpPreviewTable(String tag) {
        try {
            System.out.println("──── PreviewTable [" + tag + "] ────");
            List<WebElement> rows = driver.findElements(PREVIEW_ROWS);
            System.out.println("Row count: " + rows.size());
            int i = 0;
            for (WebElement r : rows) {
                i++;
                String email = safeCellText(r, EMAIL_CELL_IN_ROW);
                String product = safeCellText(r, PRODUCT_CELL_IN_ROW);
                String cost = safeCellText(r, COST_CELL_IN_ROW);
                String chk = "?";
                try {
                    WebElement cb = r.findElement(ROW_MAIN_TOGGLE);
                    boolean on = cb.isSelected() || "true".equalsIgnoreCase(String.valueOf(cb.getAttribute("checked")));
                    chk = on ? "ON" : "OFF";
                } catch (Exception ignored) {}
                System.out.printf(" [%d] email='%s' | product='%s' | cost=%s | main=%s%n",
                        i, email, product, cost, chk);
            }
            System.out.println("subtotal=" + getSubtotal() + " total=" + getTotal());
            System.out.println("────────────────────────────────────");
        } catch (Exception e) {
            System.out.println("[debugDumpPreviewTable] failed: " + e);
        }
    }


    public void debugDumpTableHtml(String tag) {
        try {
            WebElement table = driver.findElement(By.cssSelector("table"));
            String outer = (String) ((JavascriptExecutor) driver)
                    .executeScript("return arguments[0].outerHTML;", table);
            System.out.println("──── table.outerHTML [" + tag + "] ────");
            System.out.println(outer);
            System.out.println("──────────────────────────────────────");
        } catch (Exception e) {
            System.out.println("[debugDumpTableHtml] failed: " + e);
        }
    }

    private <T> T untilWithLogging(Duration timeout, Function<WebDriver, T> fn, String label) {
        long start = System.currentTimeMillis();
        try {
            return new WebDriverWait(driver, timeout)
                    .pollingEvery(Duration.ofMillis(250))
                    .ignoring(StaleElementReferenceException.class)
                    .until(d -> {
                        T v = fn.apply(d);
                        System.out.printf("[wait:%s] +%dms -> %s%n",
                                label, (System.currentTimeMillis() - start), String.valueOf(v));
                        return v;
                    });
        } catch (TimeoutException te) {
            System.out.println("[wait:" + label + "] TIMEOUT after " + (System.currentTimeMillis() - start) + "ms");
            debugDumpPreviewTable("timeout:" + label);
            debugDumpTableHtml("timeout:" + label);
            throw te;
        }
    }


    // aggressive normalization
    private static String normTxt(String s) {
        if (s == null) return "";
        return s.replace('\u00A0', ' ')     // nbsp
                .replace("\u200B","")       // zero-width space
                .replace("\u200C","")       // zero-width non-joiner
                .replace("\u200D","")       // zero-width joiner
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }



    private static int levenshtein1(String a, String b) {
        // tiny fast path: we only care about distance <= 1
        if (a.equals(b)) return 0;
        if (Math.abs(a.length() - b.length()) > 1) return 2; // >1 for sure
        // try one insert/delete/substitute
        int i = 0, j = 0, edits = 0;
        while (i < a.length() && j < b.length()) {
            if (a.charAt(i) == b.charAt(j)) { i++; j++; continue; }
            edits++;
            if (edits > 1) return edits;
            if (a.length() > b.length()) i++;         // delete in a
            else if (a.length() < b.length()) j++;    // insert in a
            else { i++; j++; }                        // substitute
        }
        if (i < a.length() || j < b.length()) edits++;
        return edits;
    }

    private WebElement findRowByEmailLive(WebDriver d, String email) {
        String want = normTxt(email);
        for (WebElement r : d.findElements(PREVIEW_ROWS)) {
            try {
                String got = normTxt(jsText(r.findElement(EMAIL_CELL_IN_ROW)));
                if (got.equals(want)) return r;                         // exact
                if (levenshtein1(got, want) <= 1) return r;            // ~exact
                if (got.contains("+p2@") && want.contains("+p2@")
                        && got.endsWith(want.substring(Math.max(0,want.length()-20)))) return r; // suffix guard
            } catch (Exception ignored) {}
        }
        return null;
    }

    private WebElement rowCheckbox(WebElement row) {
        // last cell has the checkbox in your outerHTML
        return row.findElement(By.cssSelector("td:last-child input[type='checkbox']"));
    }

    // React keeps the checked *attribute* static; use the property or isSelected().
    private boolean isChecked(WebElement checkbox) {
        try {
            if (checkbox.isSelected()) return true;
        } catch (StaleElementReferenceException ignored) { /* fall through to JS */ }
        Object v = ((JavascriptExecutor) driver).executeScript(
                "return !!arguments[0].checked;", checkbox);
        return Boolean.TRUE.equals(v);
    }

    private ExpectedCondition<WebElement> rowForEmail(String email) {
        return d -> findRowByEmailLive(d, email);
    }

    // Reliable JS read of checkbox state
    private boolean jsChecked(WebElement el) {
        try { return Boolean.TRUE.equals(((JavascriptExecutor) driver).executeScript("return !!arguments[0].checked;", el)); }
        catch (Exception e) { return false; }
    }


    // Stable row locator by exact email (case-sensitive match of rendered text)
    private By rowByExactEmail(String email) {
        String x = xpLit(email.trim());
        return By.xpath("//table//tbody//tr[normalize-space(td[1]) = " + x + "]");
    }

    public WebElement findRowByExactEmailFromTable(String exactFromTable) {
        String want = normTxt(exactFromTable);
        for (WebElement r : driver.findElements(PREVIEW_ROWS)) {
            String got = normTxt(jsText(r.findElement(EMAIL_CELL_IN_ROW)));
            if (got.equals(want)) return r;
        }
        return null;
    }




    private WebElement findRowByEmailSuffix(String suffix) {
        String want = normTxt(suffix);
        for (WebElement r : driver.findElements(PREVIEW_ROWS)) {
            String got = normTxt(jsText(r.findElement(EMAIL_CELL_IN_ROW)));
            if (got.endsWith(want)) return r;
        }
        return null;
    }







    /** Returns true if the CTA is showing a loading/disabled indicator. */
    /** Returns true if the CTA is showing a loading/disabled indicator (robust). */
    public boolean isPayBusy() {
        try {
            // Prefer explicit Stripe button; fall back to generic proceed if Stripe isn’t present.
            WebElement btn = null;
            try {
                btn = driver.findElement(BTN_PAY_WITH_STRIPE);
                if (!btn.isDisplayed()) btn = null;
            } catch (NoSuchElementException ignored) {}

            if (btn == null) {
                try {
                    btn = driver.findElement(BTN_PROCEED_GENERIC);
                    if (!btn.isDisplayed()) btn = null;
                } catch (NoSuchElementException ignored) {}
            }

            // If the button was re-rendered or temporarily gone after click, treat as busy.
            if (btn == null) return true;

            String disabled     = btn.getAttribute("disabled");
            String ariaBusy     = btn.getAttribute("aria-busy");
            String ariaDisabled = btn.getAttribute("aria-disabled");
            String dataLoading  = btn.getAttribute("data-loading");
            String cls          = String.valueOf(btn.getAttribute("class"));
            String text         = String.valueOf(btn.getText()).trim();

            boolean hasSpinnerChild = !btn.findElements(
                    By.xpath(".//*[contains(@class,'spinner') or contains(@class,'loading') or @role='status']")
            ).isEmpty();

            boolean loaderVisible = driver.findElements(POSSIBLE_LOADER).stream()
                    .anyMatch(el -> {
                        try { return el.isDisplayed(); } catch (Exception e) { return false; }
                    });

            boolean looksDisabled =
                    (disabled != null) ||
                            "true".equalsIgnoreCase(ariaBusy) ||
                            "true".equalsIgnoreCase(ariaDisabled) ||
                            "true".equalsIgnoreCase(dataLoading) ||
                            (cls != null && cls.matches(".*\\b(disabled|loading|is-loading)\\b.*")) ||
                            hasSpinnerChild ||
                            text.matches("(?i).*(processing|loading|please\\s*wait|submitting).*");

            return looksDisabled || loaderVisible;
        } catch (StaleElementReferenceException | NoSuchElementException e) {
            // Button disappeared or got replaced → consider it busy during submit.
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public void waitPayBusyShort() {
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .ignoring(StaleElementReferenceException.class, NoSuchElementException.class)
                .until(d -> isPayBusy());
    }



    public void waitPayIdleLong() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(20)).until(d -> !isPayBusy());
        } catch (TimeoutException ignored) { /* best-effort */ }
    }





    /** Snapshot: email -> selected? (lowercased emails) */
    public LinkedHashMap<String, Boolean> selectionByEmail() {
        LinkedHashMap<String, Boolean> out = new LinkedHashMap<>();
        waitForRows(); // you already have this
        for (WebElement r : driver.findElements(PREVIEW_ROWS)) {
            try {
                String email = normTxt(jsText(r.findElement(EMAIL_CELL_IN_ROW)));
                WebElement cb = rowCheckbox(r);
                out.put(email, isChecked(cb));
            } catch (Exception ignored) {}
        }
        return out;
    }

    /** Idempotent set by email (works even if table re-renders). */
    public OrderPreviewPage setSelectedByEmail(String email, boolean desired) {
        String want = normTxt(email);
        WebElement row = new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(d -> findRowByEmailLive(d, want));
        WebElement cb  = rowCheckbox(row);
        boolean cur    = isChecked(cb);
        if (cur == desired) return this;

        cb.click();

        // re-query until property reflects the desired state
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .ignoring(StaleElementReferenceException.class, NoSuchElementException.class)
                .until(d -> {
                    WebElement rr = findRowByEmailLive(d, want);
                    return rr != null && isChecked(rowCheckbox(rr)) == desired;
                });
        return this;
    }










}

