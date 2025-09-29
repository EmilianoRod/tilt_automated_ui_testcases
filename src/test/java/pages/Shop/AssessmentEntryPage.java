package pages.Shop;

import io.qameta.allure.Step;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.BasePage;
import pages.Shop.PurchaseRecipientSelectionPage.Recipient;

import java.time.Duration;
import java.util.List;

public class AssessmentEntryPage extends BasePage {

    public AssessmentEntryPage(WebDriver driver) { super(driver); }

    // ========= Locators (robust & text-anchored) =========

    /** Header for identity checks (also exposed as MAIN_PANEL alias). */
    public static final By ENTRY_HEADER = By.xpath(
            "//*[@data-test='assessment-entry-header' or " +
                    " (self::h1 or self::h2 or self::h3)[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'assessment entry')]]"
    );
    public static final By MAIN_PANEL = ENTRY_HEADER;

    /** Radio options (click label or inner radio; tolerant by text). */
    private By radioLabelByText(String text) {
        String t = text.toLowerCase();
        return By.xpath(
                "//p[translate(normalize-space(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='" + t + "']" +
                        "/preceding-sibling::label[contains(@class,'ant-radio-wrapper')]"
        );
    }
    private By radioInnerByText(String text) {
        String t = text.toLowerCase();
        return By.xpath(
                "//p[translate(normalize-space(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='" + t + "']" +
                        "/preceding-sibling::label//span[contains(@class,'ant-radio-inner')]"
        );
    }
    private By radioCheckedBadgeByText(String text) {
        String t = text.toLowerCase();
        return By.xpath(
                "//p[translate(normalize-space(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='" + t + "']" +
                        "/preceding-sibling::label//span[contains(@class,'ant-radio') and contains(@class,'ant-radio-checked')]"
        );
    }

    /** Quantity / count input (tolerant). */
    private static final By QUANTITY_INPUT = By.xpath(
            "//*[@role='spinbutton' or " +
                    " (self::input and (" +
                    "   @type='number' or @inputmode='numeric' or " +
                    "   contains(translate(@name,'QUANTITY','quantity'),'quant') or " +
                    "   contains(translate(@id,'QUANTITY','quantity'),'quant') ))]"
    );

    /** Proceed button variations (Next / Proceed / Proceed to payment). */
    private static final By BTN_PROCEED = By.xpath(
            "//*[self::button or self::a][" +
                    " normalize-space()='Proceed to payment' or normalize-space()='Proceed' or normalize-space()='Next' or " +
                    " .//span[normalize-space()='Next' or contains(normalize-space(),'Proceed')]]"
    );

    /** Add person button (icon or text). */
    private static final By BTN_ADD_PERSON = By.xpath(
            "//*[@data-test='add-person' or " +
                    " normalize-space(.)='Add person' or normalize-space(.)='Add Person' or " +
                    " .//span[normalize-space(.)='Add person' or normalize-space(.)='Add Person']]"
    );

    /** Rows / inputs (used to confirm dynamic row creation). */
    private static final By PERSON_ROWS   = By.cssSelector("[data-test='person-row'], [role='row'], .person-row, table tr");
    private static final By EMAIL_INPUTS  = By.cssSelector("input[type='email'], input[name*='email' i], input[id*='email' i]");

    /** Flexible selectors for fields (row-agnostic). */
    private By firstNameInputs() { return By.cssSelector("input[aria-label='First name'], input[name='firstName'], input[placeholder*='First' i]"); }
    private By lastNameInputs()  { return By.cssSelector("input[aria-label='Last name'],  input[name='lastName'],  input[placeholder*='Last'  i]"); }
    private By emailInputs()     { return EMAIL_INPUTS; }

    /** Loaders/backdrop (Ant/MUI/ARIA). */
    private static final By POSSIBLE_LOADER = By.cssSelector(
            "[data-testid='loading'],[data-test='loading'],[role='progressbar']," +
                    ".MuiBackdrop-root,.MuiCircularProgress-root,.ant-spin,.ant-spin-spinning," +
                    ".overlay,.spinner,.backdrop,[aria-busy='true']"
    );

    // ========= Identity =========

    /** Identity (recipient-agnostic). */
    public static boolean isCurrent(WebDriver driver) {
        return BasePage.isCurrentPage(driver, "/shop/ttp", ENTRY_HEADER);
    }

    /** Identity w/ recipient banner check (usually Team/Clients). */
    public static boolean isCurrent(WebDriver driver, Recipient r) {
        AssessmentEntryPage p = new AssessmentEntryPage(driver);
        return BasePage.isCurrentPage(driver, "/shop/ttp", ENTRY_HEADER, p.purchaseForBanner(r.label()));
    }

    // ========= Read-only helpers =========

    public boolean headerVisible() { return isVisible(ENTRY_HEADER); }

    /** “Assessment purchase for: …” via BasePage helper. */
    public String getPurchaseForText() { return readPurchaseForSelection(); }

    // ========= Load state =========

    @Step("Wait until Assessment Entry is loaded")
    public AssessmentEntryPage waitUntilLoaded() {
        wait.waitForDocumentReady();
        waitForOverlayGone(Duration.ofSeconds(6));
        new WebDriverWait(driver, Duration.ofSeconds(12))
                .until(ExpectedConditions.or(
                        ExpectedConditions.visibilityOfElementLocated(ENTRY_HEADER),
                        ExpectedConditions.visibilityOfElementLocated(radioLabelByText("Manually enter")),
                        ExpectedConditions.visibilityOfElementLocated(radioLabelByText("Download template"))
                ));
        return this;
    }

    // ========= Actions =========

    @Step("Select 'Manually enter'")
    public AssessmentEntryPage selectManualEntry() {
        waitForOverlayGone(Duration.ofSeconds(3));
        try { safeClick(radioLabelByText("Manually enter")); }
        catch (Throwable ignored) { safeClick(radioInnerByText("Manually enter")); }
        new WebDriverWait(driver, Duration.ofSeconds(6))
                .until(ExpectedConditions.visibilityOfElementLocated(radioCheckedBadgeByText("Manually enter")));
        waitForOverlayGone(Duration.ofSeconds(2));
        return this;
    }

    @Step("Select 'Download template'")
    public AssessmentEntryPage selectDownloadTemplate() {
        waitForOverlayGone(Duration.ofSeconds(3));
        try { safeClick(radioLabelByText("Download template")); }
        catch (Throwable ignored) { safeClick(radioInnerByText("Download template")); }
        new WebDriverWait(driver, Duration.ofSeconds(6))
                .until(ExpectedConditions.visibilityOfElementLocated(radioCheckedBadgeByText("Download template")));
        waitForOverlayGone(Duration.ofSeconds(2));
        return this;
    }

    @Step("Enter number of individuals: {count}")
    public AssessmentEntryPage enterNumberOfIndividuals(String count) {
        String value = (count == null || count.isBlank()) ? "1" : count.trim();
        waitForOverlayGone(Duration.ofSeconds(2));
        WebElement qty = new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.visibilityOfElementLocated(QUANTITY_INPUT));
        scrollToElement(qty);
        clearAndTypeCross(qty, value);
        // allow rows to render
        new WebDriverWait(driver, Duration.ofSeconds(6))
                .until(d -> visibleCount(emailInputs()) >= safeParseInt(value, 1));
        return this;
    }

    @Step("Add a participant row")
    public AssessmentEntryPage clickAddPerson() {
        waitForOverlayGone(Duration.ofSeconds(2));
        int before = visibleCount(emailInputs());
        safeClick(BTN_ADD_PERSON);
        new WebDriverWait(driver, Duration.ofSeconds(8))
                .until(d -> visibleCount(emailInputs()) > before);
        return this;
    }

    /**
     * Fill First/Last/Email for the Nth row (1-based index).
     */
    @Step("Fill user at index {oneBasedIndex}: {first} {last} <{email}>")
    public AssessmentEntryPage fillUserDetailsAtIndex(int oneBasedIndex, String first, String last, String email) {
        if (oneBasedIndex < 1) oneBasedIndex = 1;
        ensureAtLeastNRows(oneBasedIndex);

        WebElement firstNameEl = nthVisible(firstNameInputs(), oneBasedIndex);
        WebElement lastNameEl  = nthVisible(lastNameInputs(),  oneBasedIndex);
        WebElement emailEl     = nthVisible(emailInputs(),     oneBasedIndex);

        if (firstNameEl == null || lastNameEl == null || emailEl == null) {
            int f = visibleCount(firstNameInputs());
            int l = visibleCount(lastNameInputs());
            int e = visibleCount(emailInputs());
            throw new NoSuchElementException("Missing inputs for row " + oneBasedIndex +
                    " (visible counts -> first:" + f + " last:" + l + " email:" + e + ")");
        }

        scrollToElement(firstNameEl);
        clearAndTypeCross(firstNameEl, first);
        clearAndTypeCross(lastNameEl,  last);
        clearAndTypeCross(emailEl,     email);

        // Blur to trigger validations
        try { emailEl.sendKeys(Keys.TAB); } catch (Throwable ignored) {}
        return this;
    }

    @Step("Proceed to payment")
    public OrderPreviewPage clickProceedToPayment() {
        waitForOverlayGone(Duration.ofSeconds(2));
        safeClick(BTN_PROCEED);
        waitForOverlayGone(Duration.ofSeconds(2));
        return new OrderPreviewPage(driver);
    }

    /** CTA enabled without clicking. */
    public boolean isProceedToPaymentEnabled() {
        try {
            WebElement btn = new WebDriverWait(driver, Duration.ofSeconds(6))
                    .until(ExpectedConditions.presenceOfElementLocated(BTN_PROCEED));
            String aria = btn.getAttribute("aria-disabled");
            String dis  = btn.getAttribute("disabled");
            return btn.isEnabled()
                    && !"true".equalsIgnoreCase(String.valueOf(aria))
                    && !"true".equalsIgnoreCase(String.valueOf(dis));
        } catch (Throwable t) { return false; }
    }

    /** Set email in a specific row by id pattern (emails.N.email). */
    public void setEmailAtRow(int row, String email) {
        WebElement input = new WebDriverWait(driver, Duration.ofSeconds(6))
                .until(ExpectedConditions.elementToBeClickable(emailInputAt(row)));
        clearAndTypeCross(input, email);
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].dispatchEvent(new Event('blur', {bubbles:true}));", input);
        } catch (Throwable ignored) {}
    }

    /** Get inline error text near the email input of a given row. */
    public String getEmailErrorAtRow(int row) {
        try {
            WebElement input = new WebDriverWait(driver, Duration.ofSeconds(6))
                    .until(ExpectedConditions.presenceOfElementLocated(emailInputAt(row)));
            return nearestErrorText(input);
        } catch (Throwable t) { return null; }
    }

    // ========= Internals =========

    private void waitForOverlayGone(Duration timeout) {
        try {
            new WebDriverWait(driver, timeout)
                    .until(ExpectedConditions.invisibilityOfElementLocated(POSSIBLE_LOADER));
        } catch (Throwable ignore) {}
    }

    private void clearAndTypeCross(WebElement el, String text) {
        try { el.sendKeys(Keys.chord(Keys.COMMAND, "a"), Keys.DELETE); } catch (Throwable ignored) {}
        try { el.sendKeys(Keys.chord(Keys.CONTROL,  "a"), Keys.DELETE); } catch (Throwable ignored) {}
        try {
            if (!String.valueOf(el.getAttribute("value")).isEmpty()) {
                ((JavascriptExecutor) driver).executeScript(
                        "arguments[0].value=''; arguments[0].dispatchEvent(new Event('input',{bubbles:true}));", el);
            }
        } catch (Throwable ignored) {}
        el.sendKeys(text);
    }

    private void ensureAtLeastNRows(int n) {
        int have = visibleCount(emailInputs());
        if (have >= n) return;

        // First choice: use quantity input
        List<WebElement> qtyEls = driver.findElements(QUANTITY_INPUT);
        if (!qtyEls.isEmpty()) {
            WebElement qty = qtyEls.get(0);
            clearAndTypeCross(qty, String.valueOf(n));
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(d -> visibleCount(emailInputs()) >= n);
            return;
        }

        // Fallback: click "Add person" until we reach N
        int guard = 0;
        while (visibleCount(emailInputs()) < n && guard++ < n * 2) {
            safeClick(BTN_ADD_PERSON);
            waitForOverlayGone(Duration.ofSeconds(1));
        }
        new WebDriverWait(driver, Duration.ofSeconds(8))
                .until(d -> visibleCount(emailInputs()) >= n);
    }

    private WebElement nthVisible(By selector, int oneBasedIndex) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(d -> visibleCount(selector) >= oneBasedIndex);
        } catch (TimeoutException ignored) {}

        int seen = 0;
        for (WebElement el : driver.findElements(selector)) {
            try {
                if (el.isDisplayed()) {
                    if (++seen == oneBasedIndex) return el;
                }
            } catch (StaleElementReferenceException ignored) {}
        }
        return null;
    }

    private int visibleCount(By selector) {
        int c = 0;
        for (WebElement el : driver.findElements(selector)) {
            try { if (el.isDisplayed()) c++; } catch (StaleElementReferenceException ignored) {}
        }
        return c;
    }

    private By emailInputAt(int row) {
        // Likely pattern: emails.N.email
        return By.cssSelector("input[id='emails." + row + ".email']");
    }

    private String nearestErrorText(WebElement input) {
        // aria-describedby
        String desc = input.getAttribute("aria-describedby");
        if (desc != null && !desc.isBlank()) {
            for (String id : desc.split("\\s+")) {
                try {
                    WebElement el = driver.findElement(By.id(id));
                    String txt = el.getText();
                    if (txt != null && !txt.isBlank()) return txt.trim();
                } catch (NoSuchElementException ignored) {}
            }
        }
        // role="alert" near the field
        try {
            WebElement alert = input.findElement(By.xpath(
                    "ancestor::*[self::div or self::td][1]//*[(@role='alert') or contains(@class,'error') or contains(@class,'invalid')][1]"
            ));
            String txt = alert.getText();
            if (txt != null && !txt.isBlank()) return txt.trim();
        } catch (NoSuchElementException ignored) {}
        // next sibling with error-ish class
        try {
            WebElement sib = input.findElement(By.xpath(
                    "following::*[self::div or self::p or self::span][contains(@class,'error') or contains(@class,'invalid')][1]"
            ));
            String txt = sib.getText();
            if (txt != null && !txt.isBlank()) return txt.trim();
        } catch (NoSuchElementException ignored) {}
        return null;
    }

    private int safeParseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}
