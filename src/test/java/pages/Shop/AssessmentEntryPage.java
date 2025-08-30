package pages.Shop;

import io.qameta.allure.Step;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.BasePage;

import java.time.Duration;
import java.util.List;

public class AssessmentEntryPage extends BasePage {



    // ---------- Constructor ----------
    public AssessmentEntryPage(WebDriver driver) {
        super(driver);
    }

    // ---------- Locators (robust & text-anchored) ----------

    // Ant Design often hides the input; click the LABEL or the .ant-radio-inner
    // Structure from your DOM:
    // <div class="jrmjRs">
    //    <label class="ant-radio-wrapper"> ... <input type="radio"> ... </label>
    //    <p>Manually enter</p>
    // </div>
    private By radioLabelByText(String text) {
        String t = text.toLowerCase();
        return By.xpath("//p[translate(normalize-space(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz')='" + t + "']" +
                "/preceding-sibling::label[contains(@class,'ant-radio-wrapper')]");
    }

    private By radioInnerByText(String text) {
        String t = text.toLowerCase();
        return By.xpath("//p[translate(normalize-space(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz')='" + t + "']" +
                "/preceding-sibling::label//span[contains(@class,'ant-radio-inner')]");
    }

    private By radioCheckedBadgeByText(String text) {
        String t = text.toLowerCase();
        return By.xpath("//p[translate(normalize-space(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz')='" + t + "']" +
                "/preceding-sibling::label//span[contains(@class,'ant-radio') and contains(@class,'ant-radio-checked')]");
    }

    // Quantity / count input (tolerant)
    private final By quantityInput = By.xpath(
            "//*[@role='spinbutton' or " +
                    " self::input[@type='number' or @inputmode='numeric' or " +
                    "             contains(translate(@name,'QUANTITY','quantity'),'quant') or " +
                    "             contains(translate(@id,'QUANTITY','quantity'),'quant')]]"
    );

    // Proceed button variations (Next / Proceed / Proceed to payment)
    private final By proceedButton = By.xpath(
            "//button[normalize-space()='Proceed to payment' or normalize-space()='Proceed' or normalize-space()='Next' or " +
                    "        .//span[normalize-space()='Next' or contains(normalize-space(),'Proceed')]]"
    );

    // Add person (your DOM shows an icon button with aria-label)
    private final By addPersonButton = By.xpath(
            "//button[@data-test='add-person' " +
                    " or normalize-space(.)='Add person' or normalize-space(.)='Add Person' " +
                    " or .//span[normalize-space(.)='Add person' or normalize-space(.)='Add Person']]"
    );

    // Person rows & email inputs (used to confirm a row appeared)
    private final By personRowsLoc   = By.cssSelector("[data-test='person-row'], [role='row'], .person-row, table tr");
    private final By emailInputsLoc  = By.cssSelector("input[type='email'], input[name*='email' i], input[id*='email' i]");

    // Generic overlay/spinner patterns (Ant/MUI/ARIA)
    private final By possibleLoader = By.cssSelector(
            "[data-testid='loading'],[data-test='loading'],[role='progressbar']," +
                    ".MuiBackdrop-root,.MuiCircularProgress-root,.ant-spin,.ant-spin-spinning," +
                    ".overlay,.spinner,.backdrop,[aria-busy='true']"
    );

    // Flexible selectors for fields (don’t depend on rows)
    private By firstNameInputs() {
        return By.cssSelector("input[aria-label='First name'], input[name='firstName'], input[placeholder*='First' i]");
    }
    private By lastNameInputs() {
        return By.cssSelector("input[aria-label='Last name'], input[name='lastName'], input[placeholder*='Last' i]");
    }
    private By emailInputs() {
        return By.cssSelector("input[type='email'], input[name='email'], input[aria-label='Email'], input[placeholder*='mail' i]");
    }



    // ---------- Private utils ----------

    private WebElement waitClickable(By locator, long sec) {
        return new WebDriverWait(driver, Duration.ofSeconds(sec))
                .until(ExpectedConditions.elementToBeClickable(locator));
    }

    private WebElement waitVisible(By locator, long sec) {
        return new WebDriverWait(driver, Duration.ofSeconds(sec))
                .until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    private boolean waitInvisibility(By locator, long sec) {
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(sec))
                    .until(ExpectedConditions.invisibilityOfElementLocated(locator));
        } catch (TimeoutException e) {
            return false;
        }
    }


    private void clearAndTypeCross(WebElement el, String text) {
        // CMD/CTRL+A + Delete (macOS/Linux/Windows) + JS fallback if needed
        try { el.sendKeys(Keys.chord(Keys.COMMAND, "a")); el.sendKeys(Keys.DELETE); } catch (Exception ignored) {}
        try { el.sendKeys(Keys.chord(Keys.CONTROL,  "a")); el.sendKeys(Keys.DELETE); } catch (Exception ignored) {}
        try {
            if (!String.valueOf(el.getAttribute("value")).isEmpty()) {
                ((JavascriptExecutor) driver).executeScript(
                        "arguments[0].value=''; arguments[0].dispatchEvent(new Event('input',{bubbles:true}));", el);
            }
        } catch (Exception ignored) {}
        el.sendKeys(text);
    }

    private int currentRowCount() { return visibleCount(emailInputs()); }

    // ---------- Public API ----------

    @Step("Wait until Assessment Entry page is ready")
    public AssessmentEntryPage waitUntilLoaded() {
        waitLoadersGone();
        // Expect at least one of the radio choices to be interactable
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.or(
                ExpectedConditions.visibilityOfElementLocated(radioLabelByText("Manually enter")),
                ExpectedConditions.visibilityOfElementLocated(radioLabelByText("Download template"))
        ));
        return this;
    }

    @Step("Select 'Manually enter'")
    public AssessmentEntryPage selectManualEntry() {
        waitLoadersGone();
        try {
            safeClick(radioLabelByText("Manually enter"));
        } catch (TimeoutException ignored) {
            safeClick(radioInnerByText("Manually enter"));
        }
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.visibilityOfElementLocated(radioCheckedBadgeByText("Manually enter")));
        waitLoadersGone();
        return this;
    }

    @Step("Select 'Download template'")
    public AssessmentEntryPage selectDownloadTemplate() {
        waitLoadersGone();
        try {
            safeClick(radioLabelByText("Download template"));
        } catch (TimeoutException ignored) {
            safeClick(radioInnerByText("Download template"));
        }
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.visibilityOfElementLocated(radioCheckedBadgeByText("Download template")));
        waitLoadersGone();
        return this;
    }

    @Step("Enter number of individuals: {count}")
    public AssessmentEntryPage enterNumberOfIndividuals(String count) {
        String value = (count == null || count.isBlank()) ? "1" : count.trim();
        waitLoadersGone();
        WebElement qty = waitVisible(quantityInput, 10);
        scrollToElement(qty);
        clearAndTypeCross(qty, value);
        return this;
    }


    @Step("Add a participant row")
    public AssessmentEntryPage clickAddPerson() {
        waitLoadersGone();
        int before = visibleCount(emailInputs());
        safeClick(addPersonButton);
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(d -> visibleCount(emailInputs()) > before);
        waitLoadersGone();
        return this;
    }


    /**
     * Fill First/Last/Email for the Nth row (1-based index).
     * Tolerant by locating inputs within that row scope.
     */
    @Step("Fill user at index {oneBasedIndex}: {first} {last} <{email}>")
    public AssessmentEntryPage fillUserDetailsAtIndex(int oneBasedIndex, String first, String last, String email) {
        if (oneBasedIndex < 1) oneBasedIndex = 1;

        waitLoadersGone(); // guard if you have this; otherwise keep wait.waitForLoadersToDisappear()

        // Ensure we have at least N visible email inputs (row-agnostic)
        ensureAtLeastNRows(oneBasedIndex);

        // Find the Nth *visible* inputs for each field
        WebElement firstNameEl = nthVisible(firstNameInputs(), oneBasedIndex);
        WebElement lastNameEl  = nthVisible(lastNameInputs(),  oneBasedIndex);
        WebElement emailEl     = nthVisible(emailInputs(),     oneBasedIndex);

        if (firstNameEl == null || lastNameEl == null || emailEl == null) {
            int f = visibleCount(firstNameInputs());
            int l = visibleCount(lastNameInputs());
            int e = visibleCount(emailInputs());
            throw new NoSuchElementException(
                    "Could not locate all inputs for row " + oneBasedIndex +
                            " (visible counts -> first:" + f + " last:" + l + " email:" + e + ")"
            );
        }

        scrollToElement(firstNameEl);
        clearAndTypeCross(firstNameEl, first);
        clearAndTypeCross(lastNameEl,  last);
        clearAndTypeCross(emailEl,     email);

        // Blur to trigger validations
        emailEl.sendKeys(Keys.TAB);

        waitLoadersGone();
        return this;
    }



    private void ensureAtLeastNRows(int n) {
        int have = visibleCount(emailInputs());
        if (have >= n) return;

        // First choice: set the quantity field directly (most reliable)
        List<WebElement> qtyEls = driver.findElements(quantityInput);
        if (!qtyEls.isEmpty()) {
            WebElement qty = qtyEls.get(0);
//            scrollIntoViewCenter(qty);
            clearAndTypeCross(qty, String.valueOf(n));
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(d -> visibleCount(emailInputs()) >= n);
            return;
        }

        // Fallback: click the explicit "Add person" button until we reach N
        int guard = 0;
        while (visibleCount(emailInputs()) < n && guard++ < n * 2) {
            safeClick(addPersonButton);
            waitLoadersGone();
        }
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(d -> visibleCount(emailInputs()) >= n);
    }



    // Return the 1-based Nth *visible* element for a selector, or null if not found.
// Includes a short wait so we don’t race the DOM.
    private WebElement nthVisible(By selector, int oneBasedIndex) {
        // best-effort wait until at least N are visible
        try {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(d -> visibleCount(selector) >= oneBasedIndex);
        } catch (TimeoutException ignored) { }

        int seen = 0;
        for (WebElement el : driver.findElements(selector)) {
            try {
                if (el.isDisplayed()) {
                    seen++;
                    if (seen == oneBasedIndex) return el;
                }
            } catch (StaleElementReferenceException ignored) { }
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

    // Tiny guard alias (use your existing wait method if you prefer)
    private void waitLoadersGone() {
        try { wait.waitForLoadersToDisappear(); } catch (Exception ignored) {}
    }


    @Step("Proceed to payment")
    public OrderPreviewPage clickProceedToPayment() {
        waitLoadersGone();
        safeClick(proceedButton);
        waitLoadersGone();
        return new OrderPreviewPage(driver);
    }


    // --- Locators that work with your existing id scheme (e.g., emails.0.email) ---
    private By emailInputAt(int row) {
        // Prefer the explicit id pattern you already use for the row checkbox (emails.N.checkbox)
        // so email input is likely emails.N.email
        return By.cssSelector("input[id='emails." + row + ".email']");
    }

    // Tries common spots for inline error text near the email input
    private String nearestErrorText(WebElement input) {
        // 1) aria-describedby points to an error element
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
        // 2) role="alert" sibling/ancestor
        try {
            WebElement alert = input.findElement(By.xpath(
                    "ancestor::*[self::div or self::td][1]//*[(@role='alert') or contains(@class,'error') or contains(@class,'invalid')][1]"
            ));
            String txt = alert.getText();
            if (txt != null && !txt.isBlank()) return txt.trim();
        } catch (NoSuchElementException ignored) {}

        // 3) Next sibling with error-ish class
        try {
            WebElement sib = input.findElement(By.xpath(
                    "following::*[self::div or self::p or self::span][contains(@class,'error') or contains(@class,'invalid')][1]"
            ));
            String txt = sib.getText();
            if (txt != null && !txt.isBlank()) return txt.trim();
        } catch (NoSuchElementException ignored) {}

        return null;
    }

// --- API used by the test ---

    public boolean isProceedToPaymentEnabled() {
        try {
            WebElement btn = new WebDriverWait(driver, java.time.Duration.ofSeconds(5))
                    .until(org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated(proceedButton));
            String disabled = btn.getAttribute("disabled");
            return btn.isEnabled() && (disabled == null || disabled.equalsIgnoreCase("false"));
        } catch (Exception e) {
            return false;
        }
    }

    public void setEmailAtRow(int row, String email) {
        WebElement input = new WebDriverWait(driver, java.time.Duration.ofSeconds(5))
                .until(org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable(emailInputAt(row)));
        input.clear();
        input.sendKeys(email);
        // Blur to trigger validation if needed
        ((org.openqa.selenium.JavascriptExecutor) driver)
                .executeScript("arguments[0].dispatchEvent(new Event('blur', {bubbles:true}));", input);
    }

    public String getEmailErrorAtRow(int row) {
        try {
            WebElement input = new WebDriverWait(driver, java.time.Duration.ofSeconds(5))
                    .until(org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated(emailInputAt(row)));
            return nearestErrorText(input);
        } catch (Exception e) {
            return null;
        }
    }


    // ---------- Small helpers ----------

    private WebElement findIn(WebElement scope, By locator) {
        try { return scope.findElement(locator); }
        catch (NoSuchElementException e) { return null; }
    }



}