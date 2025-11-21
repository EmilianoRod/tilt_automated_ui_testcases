package pages.Shop;

import io.qameta.allure.Step;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.BasePage;
import pages.Shop.PurchaseRecipientSelectionPage.Recipient;

import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;


public class AssessmentEntryPage extends BasePage {

    public AssessmentEntryPage(WebDriver driver) { super(driver); }

    // ========= Locators (robust & text-anchored) =========

    /** Header for identity checks (also exposed as MAIN_PANEL alias). */
    public static final By ENTRY_HEADER = By.xpath(
            "//*[@data-test='assessment-entry-header' or " +
                    " (self::h1 or self::h2 or self::h3)[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'assessment entry')]]"
    );
    public static final By MAIN_PANEL = ENTRY_HEADER;


    private static String lowerAlphabet = "abcdefghijklmnopqrstuvwxyz";
    private static String upperAlphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";


    /** Container → the radio’s wrapper lives in the same block as the visible option text. */
    private String optionContainerXp(String text) {
        // Case-insensitive contains on any of p/span/div inside the option block
        return String.format(
                "//div[.//label[contains(@class,'ant-radio-wrapper')] and " +
                        ".//*[self::p or self::span or self::div]" +
                        "[contains(translate(normalize-space(.),'%s','%s'),'%s')]]",
                upperAlphabet, lowerAlphabet, text.toLowerCase()
        );
    }






    /** Finds the <label.ant-radio-wrapper> for a radio with the given visible text.
     *  Handles both patterns from the UI:
     *   A) <div class="kFoKtg"><label/><p>Text</p></div>
     *   B) <div class="kFoKtg"><label/><div><p>Text</p>...</div></div>
     *   C) (fallback) Text inside the label itself.
     */
    private By radioLabelByText(String text) {
        String t = text == null ? "" : text.trim().toLowerCase(java.util.Locale.ROOT);
        String tLit = toXPathLiteral(t); // safe for quotes

        String xp =
                "(" +
                        // Scope to a row container that contains the wanted text in a P (direct or nested)
                        " //div[contains(@class,'kFoKtg')]" +
                        "   [ .//p[contains(translate(normalize-space(string(.)),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'), " + tLit + ")] ]" +
                        "   //label[contains(@class,'ant-radio-wrapper')][1]" +
                        " |" +
                        // Text is a sibling <p> right after the label
                        " //p[contains(translate(normalize-space(string(.)),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'), " + tLit + ")]" +
                        "   /preceding-sibling::label[contains(@class,'ant-radio-wrapper')][1]" +
                        " |" +
                        // Fallback: text inside label itself
                        " //label[contains(@class,'ant-radio-wrapper')]" +
                        "   [contains(translate(normalize-space(string(.)),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'), " + tLit + ")]" +
                        ")";

        return By.xpath(xp);
    }

    /** Produces a safe XPath string literal even if it contains both ' and " */
    private static String toXPathLiteral(String s) {
        if (!s.contains("'")) return "'" + s + "'";
        if (!s.contains("\"")) return "\"" + s + "\"";
        // concat('a', "'", 'b')
        return "concat('" + s.replace("'", "',\"'\",'") + "')";
    }





    /** The actual <input type='radio'> inside that label. */
    /** The <input type='radio'> inside that wrapper. */
    private By radioInputByText(String text) {
        String xp = optionContainerXp(text) +
                "/label[contains(@class,'ant-radio-wrapper')]//input[@type='radio' or contains(@class,'ant-radio-input')]";
        return By.xpath(xp);
    }




    /** Inner clickable circle (kept for backward-compat with callers). */
    private By radioInnerByText(String text) {
        return By.xpath("(" + radioLabelByText(text).toString().replaceFirst("^By\\.xpath: ", "") + ")//span[contains(@class,'ant-radio-inner')]");
    }



    /** Ensure the radio for {text} is checked; click the wrapper if needed and wait for it to stick. */
    /** Ensure the radio corresponding to {text} is checked. */
    private void ensureRadioChecked(String text) {
        ensureRadioChecked(radioInputByText(text), radioWrapperByText(text));
    }

    /** Ensure the radio found by {radioInput}/{radioWrapper} is checked. */
    private void ensureRadioChecked(By radioInput, By radioWrapper) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(8));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                WebElement input   = wait.until(ExpectedConditions.presenceOfElementLocated(radioInput));
                WebElement wrapper = wait.until(ExpectedConditions.elementToBeClickable(radioWrapper));

                // already checked?
                Boolean checked = (Boolean) js.executeScript("return arguments[0].checked===true;", input);
                if (Boolean.TRUE.equals(checked)) return;

                js.executeScript("arguments[0].scrollIntoView({block:'center'});", wrapper);
                try {
                    wrapper.click();
                } catch (Exception e) {
                    js.executeScript("arguments[0].click();", wrapper);
                }

                boolean ok = wait.until(d -> {
                    try {
                        WebElement i = d.findElement(radioInput);
                        Boolean c = (Boolean) ((JavascriptExecutor) d).executeScript("return arguments[0].checked===true;", i);
                        if (Boolean.TRUE.equals(c)) return true;

                        WebElement w = d.findElement(radioWrapper);
                        // AntD adds .ant-radio-checked on the span
                        return w.findElements(By.cssSelector(".ant-radio-checked")).size() > 0;
                    } catch (StaleElementReferenceException ignore) {
                        return false;
                    }
                });
                if (ok) return;
            } catch (StaleElementReferenceException ignore) {
                // retry with fresh refs
            }
        }
        throw new TimeoutException("Failed to ensure radio is checked: " + radioWrapper);
    }


    private String toLower(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    /** The radio <label.ant-radio-wrapper> that belongs to the option whose text block says {text}. */
    /** The <label.ant-radio-wrapper> for the option that says {text}. */
    private By radioWrapperByText(String text) {
        String xp = optionContainerXp(text) + "/label[contains(@class,'ant-radio-wrapper')]";
        return By.xpath(xp);
    }



    /** Checked badge/span under that label. */
    private By radioCheckedBadgeByText(String text) {
        String t = text.toLowerCase();
        return By.xpath(
                "(" +
                        "//label[contains(@class,'ant-radio-wrapper')" +
                        "  and contains(translate(normalize-space(string(.)),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'" + t + "')" +
                        "]" +
                        " | " +
                        "//p[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'" + t + "')]" +
                        "/parent::div/preceding-sibling::label[contains(@class,'ant-radio-wrapper')][1]" +
                        ")" +
                        "//span[contains(@class,'ant-radio') and contains(@class,'ant-radio-checked')]"
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


    private static final By QUANTITY_BY_LABEL = By.xpath(
            // label "How many ..." → its following input
            "//*[self::label or self::p or self::span][contains(translate(normalize-space(.),'HOWMANY','how many'),'how many')]" +
                    "/following::input[1]"
    );

    // Small helper to reliably commit spinner edits
    private void commitQuantity(WebElement el) {
        try { el.sendKeys(Keys.ENTER); } catch (Throwable ignored) {}
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));" +
                            "arguments[0].dispatchEvent(new Event('blur',{bubbles:true}));", el);
        } catch (Throwable ignored) {}
    }



    /** Proceed button variations (Next / Proceed / Proceed to payment). */
    private static final By BTN_PROCEED = By.xpath("//button[normalize-space()='Proceed to payment']");

    /** Add person button (icon or text). */
    private static final By BTN_ADD_PERSON = By.xpath(
            "//*[@data-test='add-person' or " +
                    " normalize-space(.)='Add person' or normalize-space(.)='Add Person' or " +
                    " .//span[normalize-space(.)='Add person' or normalize-space(.)='Add Person']]"
    );

    /** Rows / inputs (used to confirm dynamic row creation). */
    private static final By PERSON_ROWS   = By.cssSelector("[data-test='person-row'], [role='row'], .person-row, table tr");
    private static final By EMAIL_INPUTS  = By.cssSelector("input[type='email'], input[name*='email' i], input[id*='email' i]");



    // ---- top-level convenience ----
    private static final int MAX_TEAM_MEMBERS = 20;


    /** Read the numeric value currently shown in the quantity input. */
    public int getNumberOfIndividuals() {
        try {
            WebElement qty = new WebDriverWait(driver, Duration.ofSeconds(6))
                    .until(ExpectedConditions.presenceOfElementLocated(QUANTITY_INPUT));
            String v = String.valueOf(qty.getAttribute("value")).trim();
            return v.isEmpty() ? 0 : Integer.parseInt(v);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Inline error message associated to the quantity input (null if none). */
    public String getNumberOfIndividualsError() {
        try {
            WebElement qty = new WebDriverWait(driver, Duration.ofSeconds(6))
                    .until(ExpectedConditions.presenceOfElementLocated(QUANTITY_INPUT));
            return nearestErrorText(qty);
        } catch (Throwable t) {
            return null;
        }
    }



    /** Optional: wait until the UI has rendered ≤ N rows (useful if it clamps). */
    public AssessmentEntryPage waitRowsRenderedAtMost(int n) {
        new WebDriverWait(driver, Duration.ofSeconds(8))
                .until(d -> renderedEmailRows() <= n);
        return this;
    }


    /** Simple + fast: return the email input for a 1-based row (no card scope). */
    private WebElement findEmailInput(int oneBasedRow, Duration timeout) {
        if (oneBasedRow < 1) oneBasedRow = 1;
        final int idx = oneBasedRow - 1;
        WebDriverWait w = new WebDriverWait(driver, timeout);

        // Try exact id first (escape dots in CSS)
        for (By by : new By[] {
                By.cssSelector("input#users\\." + idx + "\\.email"),
                By.cssSelector("input[name='users." + idx + ".email']")
        }) {
            for (WebElement el : driver.findElements(by)) {
                try { if (el.isDisplayed()) return el; } catch (StaleElementReferenceException ignored) {}
            }
        }

        // Fallback: nth visible users.* email input
        By usersEmails = By.cssSelector("input[id^='users.'][id$='.email'], input[name^='users.'][name$='.email']");
        w.until(d -> d.findElements(usersEmails).stream().anyMatch(WebElement::isDisplayed));
        int seen = 0;
        for (WebElement el : driver.findElements(usersEmails)) {
            try {
                if (!el.isDisplayed()) continue;
                if (seen++ == idx) return el;
            } catch (StaleElementReferenceException ignored) {}
        }
        return null;
    }





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




    /** TEAMS LOCATORS */
    // ========== Create New Team inputs ==========
    private static final By ORG_NAME_INPUT = By.xpath("//input[@id='organization']");
    private static final By GROUP_NAME_INPUT = By.xpath("//input[@id='team']");

    // Optional: Radio for 'Add members to existing team'
    private By radioAddMembersExisting() { return radioLabelByText("Add members to existing team"); }




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
    @Override
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
        waitForOverlayGone(Duration.ofSeconds(2));

        // Use your existing helpers:
        // - radioInputByText("Manually enter")
        // - radioWrapperByText("Manually enter")
        // - ensureRadioChecked(By input, By wrapper)  -> clicks wrapper if needed and waits for checked
        ensureRadioChecked(
                radioInputByText("Manually enter"),
                radioWrapperByText("Manually enter")
        );

        // Confirm it stuck (label gets ant-radio-wrapper-checked)
        new WebDriverWait(driver, Duration.ofSeconds(8)).until(d -> {
            try {
                WebElement lbl = d.findElement(radioWrapperByText("Manually enter"));
                String cls = String.valueOf(lbl.getAttribute("class"));
                return cls.contains("ant-radio-wrapper-checked");
            } catch (StaleElementReferenceException | NoSuchElementException e) {
                return false;
            }
        });

        waitForOverlayGone(Duration.ofSeconds(1));
        return this;
    }












    @Step("Select 'Create new team'")
    public AssessmentEntryPage selectCreateNewTeam() {
        waitForOverlayGone(Duration.ofSeconds(2));

        // Click the <label.ant-radio-wrapper> whose next sibling contains "create new team"
        JavascriptExecutor js = (JavascriptExecutor) driver;
        boolean clicked = Boolean.TRUE.equals(js.executeScript(
                "const target='create new team';" +
                        "for (const lbl of document.querySelectorAll('label.ant-radio-wrapper')) {" +
                        "  const sib = lbl.nextElementSibling;" +
                        "  const txt = (sib && sib.textContent || '').trim().toLowerCase().replace(/\\s+/g,' ');" +
                        "  if (txt.includes(target)) { lbl.click(); return true; }" +
                        "}" +
                        "return false;"
        ));
        if (!clicked) throw new IllegalStateException("Radio 'Create new team' not found/clickable.");

        // Wait until that radio's <input> becomes checked
        new WebDriverWait(driver, Duration.ofSeconds(6)).until(d ->
                Boolean.TRUE.equals(((JavascriptExecutor) d).executeScript(
                        "const target='create new team';" +
                                "for (const lbl of document.querySelectorAll('label.ant-radio-wrapper')) {" +
                                "  const sib = lbl.nextElementSibling;" +
                                "  const txt = (sib && sib.textContent || '').trim().toLowerCase().replace(/\\s+/g,' ');" +
                                "  if (txt.includes(target)) {" +
                                "    const inp = lbl.querySelector('input[type=radio]');" +
                                "    return !!(inp && inp.checked);" +
                                "  }" +
                                "}" +
                                "return false;"
                ))
        );

        waitForOverlayGone(Duration.ofSeconds(1));
        return this;
    }

    @Step("Select 'Download template'")
    public AssessmentEntryPage selectDownloadTemplate() {
        waitForOverlayGone(Duration.ofSeconds(2));

        JavascriptExecutor js = (JavascriptExecutor) driver;
        boolean clicked = Boolean.TRUE.equals(js.executeScript(
                "const target='download template';" +
                        "for (const lbl of document.querySelectorAll('label.ant-radio-wrapper')) {" +
                        "  const sib = lbl.nextElementSibling;" +
                        "  const txt = (sib && sib.textContent || '').trim().toLowerCase().replace(/\\s+/g,' ');" +
                        "  if (txt.includes(target)) { lbl.click(); return true; }" +
                        "}" +
                        "return false;"
        ));
        if (!clicked) throw new IllegalStateException("Radio 'Download template' not found/clickable.");

        new WebDriverWait(driver, Duration.ofSeconds(6)).until(d ->
                Boolean.TRUE.equals(((JavascriptExecutor) d).executeScript(
                        "const target='download template';" +
                                "for (const lbl of document.querySelectorAll('label.ant-radio-wrapper')) {" +
                                "  const sib = lbl.nextElementSibling;" +
                                "  const txt = (sib && sib.textContent || '').trim().toLowerCase().replace(/\\s+/g,' ');" +
                                "  if (txt.includes(target)) {" +
                                "    const inp = lbl.querySelector('input[type=radio]');" +
                                "    return !!(inp && inp.checked);" +
                                "  }" +
                                "}" +
                                "return false;"
                ))
        );

        waitForOverlayGone(Duration.ofSeconds(1));
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
        logInputConstraints(firstNameEl);
        logInputConstraints(lastNameEl);
        logInputConstraints(emailEl);
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
    @Step("Set email at row {row}: {email}")
    public AssessmentEntryPage setEmailAtRow(int row, String email) {
        WebElement input = findEmailInput(row, Duration.ofSeconds(8));
        if (input == null) {
            int visible = visibleCount(emailInputs());
            throw new NoSuchElementException("Email input not found for row " + row +
                    " (visible email inputs: " + visible + ")");
        }
        scrollToElement(input);
        // Click once to ensure focus (helps some masks)
        try { input.click(); } catch (Throwable ignored) {}

        clearAndTypeCross(input, email); // now JS path => instant
        jsBlur(input);                   // one-shot validation
        return this;
    }

    @Step("Set multiple emails quickly")
    public AssessmentEntryPage setEmailsFast(List<String> emails) {
        ensureAtLeastNRows(emails.size());
        for (int i = 0; i < emails.size(); i++) {
            WebElement input = findEmailInput(i + 1, Duration.ofSeconds(4));
            if (input != null) {
                scrollToElement(input);
                clearAndTypeCross(input, emails.get(i));
                jsBlur(input);
            }
        }
        return this;
    }




    /** Get inline error text near the email input of a given row. */
    public String getEmailErrorAtRow(int row) {
        try {
            WebElement input = findEmailInput(row, Duration.ofSeconds(6));
            return input == null ? null : nearestErrorText(input);
        } catch (Throwable t) {
            return null;
        }
    }


    // ========= Internals =========

    private void waitForOverlayGone(Duration timeout) {
        try {
            new WebDriverWait(driver, timeout)
                    .until(ExpectedConditions.invisibilityOfElementLocated(POSSIBLE_LOADER));
        } catch (Throwable ignore) {}
    }

    /** Super-fast value set for React/AntD inputs (no per-keystroke lag). */
    private void jsSetInputValue(WebElement el, String value) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        // Use the native setter so React tracks changes
        js.executeScript(
                "const el = arguments[0], val = arguments[1];" +
                        "const proto = Object.getPrototypeOf(el);" +
                        "const desc = Object.getOwnPropertyDescriptor(proto, 'value');" +
                        "if (desc && desc.set) { desc.set.call(el, val); } else { el.value = val; }" +
                        // React 16+ value tracker (safe to no-op if missing)
                        "if (el._valueTracker) { el._valueTracker.setValue(''); }" +
                        // Fire a single input event (bubbles = true so validators run once)
                        "el.dispatchEvent(new Event('input', { bubbles: true }));",
                el, value
        );
    }

    /** Optional: one-shot blur to force any async validation/render. */
    private void jsBlur(WebElement el) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].dispatchEvent(new Event('blur', { bubbles:true }));", el);
        } catch (Throwable ignored) {}
    }




//    private void clearAndTypeCross(WebElement el, String text) {
//        // Fast path: JS set (React-safe)
//        try {
//            jsSetInputValue(el, text == null ? "" : text);
//            return; // 🚀 done
//        } catch (Throwable ignore) {}
//
//        // Fallbacks if JS blocked
//        try { el.sendKeys(Keys.chord(Keys.COMMAND, "a"), Keys.DELETE); } catch (Throwable ignored) {}
//        try { el.sendKeys(Keys.chord(Keys.CONTROL,  "a"), Keys.DELETE); } catch (Throwable ignored) {}
//        try {
//            if (!String.valueOf(el.getAttribute("value")).isEmpty()) {
//                ((JavascriptExecutor) driver).executeScript(
//                        "arguments[0].value=''; arguments[0].dispatchEvent(new Event('input',{bubbles:true}));", el);
//            }
//        } catch (Throwable ignored) {}
//        el.sendKeys(text == null ? "" : text);
//    }


    /** Robust clear+type for React/AntD-controlled inputs (email-safe).
     *  - Prefers native property setter (React tracks it)
     *  - Updates _valueTracker
     *  - Fires input + change (once)
     *  - Falls back to sendKeys
     *  - Verifies final value == expected (else throws with context)
     */
    private void clearAndTypeCross(WebElement el, String text) {
        final String target = (text == null) ? "" : text;

        JavascriptExecutor js = (JavascriptExecutor) driver;

        // 0) Focus and scroll into view (avoid offscreen/overlay issues)
        try { js.executeScript("arguments[0].scrollIntoView({block:'center'})", el); } catch (Throwable ignored) {}
        try { js.executeScript("arguments[0].focus()", el); } catch (Throwable ignored) {}

        // 1) Native setter path (React-safe)
        try {
            js.executeScript(
                    "const el = arguments[0]; const val = arguments[1];" +
                            // select-all then clear (prevents concatenation on some masks)
                            "try{el.setSelectionRange(0, el.value?.length||0);}catch(e){}" +
                            // native setter (so React sees it)
                            "const proto = Object.getPrototypeOf(el);" +
                            "const desc  = Object.getOwnPropertyDescriptor(proto, 'value');" +
                            "if (desc && desc.set) { desc.set.call(el, ''); } else { el.value = ''; }" +
                            "if (el._valueTracker) { el._valueTracker.setValue(''); }" +
                            // set final value
                            "if (desc && desc.set) { desc.set.call(el, val); } else { el.value = val; }" +
                            "if (el._valueTracker) { el._valueTracker.setValue(''); }" +
                            // fire one input + one change
                            "el.dispatchEvent(new Event('input',  { bubbles:true }));" +
                            "el.dispatchEvent(new Event('change', { bubbles:true }));",
                    el, target
            );
        } catch (Throwable ignore) {
            // 2) Fallback: sendKeys path (works when JS blocked by CSP)
            try {
                // CMD/CTRL+A delete
                try { el.sendKeys(Keys.chord(Keys.COMMAND, "a"), Keys.DELETE); } catch (Throwable ignored) {}
                try { el.sendKeys(Keys.chord(Keys.CONTROL,  "a"), Keys.DELETE); }  catch (Throwable ignored) {}
                el.sendKeys(target);
            } catch (Throwable e) {
                throw new RuntimeException("clearAndTypeCross: unable to set value via JS or sendKeys", e);
            }
        }

        // 3) Verify exact value stuck (no trimming/ellipsis allowed)
        boolean ok = false;
        String actual = "";
        long end = System.currentTimeMillis() + 1500;
        while (System.currentTimeMillis() < end) {
            try {
                actual = String.valueOf(el.getAttribute("value").trim());
                if (target.trim().equals(actual)) { ok = true; break; }
            } catch (Throwable ignored) {}
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }

        // Helpful diagnostics if mismatch
        if (!ok) {
            String maxl = "";
            String patt = "";
            String type = "";
            try { maxl = String.valueOf(el.getAttribute("maxlength")); } catch (Throwable ignored) {}
            try { patt = String.valueOf(el.getAttribute("pattern"));   } catch (Throwable ignored) {}
            try { type = String.valueOf(el.getAttribute("type"));      } catch (Throwable ignored) {}

            System.out.println("[TYPE-MISMATCH] wanted=" + target +
                    " | actual=" + actual +
                    " | type=" + type +
                    " | maxlength=" + maxl +
                    " | pattern=" + patt);

            if (maxl != null && !maxl.isBlank()) {
                try {
                    int ml = Integer.parseInt(maxl);
                    if (ml > 0 && target.length() > ml) {
                        throw new AssertionError("Input maxlength (" + ml + ") truncated email; length=" +
                                target.length() + ". Value now: '" + actual + "'");
                    }
                } catch (NumberFormatException ignored) {}
            }
            // One more attempt: set via property again without firing change, then re-fire input
            try {
                js.executeScript(
                        "const el = arguments[0], val = arguments[1];" +
                                "const proto = Object.getPrototypeOf(el);" +
                                "const desc  = Object.getOwnPropertyDescriptor(proto, 'value');" +
                                "if (desc && desc.set) { desc.set.call(el, val); } else { el.value = val; }" +
                                "if (el._valueTracker) { el._valueTracker.setValue(''); }" +
                                "el.dispatchEvent(new Event('input', { bubbles:true }));",
                        el, target
                );
                actual = String.valueOf(el.getAttribute("value"));
            } catch (Throwable ignored) {}

            if (!target.equals(actual)) {
                throw new AssertionError("clearAndTypeCross: value mismatch after set. " +
                        "expected='" + target + "' actual='" + actual + "'");
            }
        }

        // 4) Blur to commit validations (some forms rely on blur)
        try { js.executeScript("arguments[0].blur()", el); } catch (Throwable ignored) {}
    }


    private void logInputConstraints(WebElement el) {
        System.out.println("[ATTR] id=" + el.getAttribute("id") +
                " type=" + el.getAttribute("type") +
                " maxlength=" + el.getAttribute("maxlength") +
                " pattern=" + el.getAttribute("pattern"));
    }






    public AssessmentEntryPage ensureAtLeastNRows(int n) {
        int have = visibleCount(emailInputs());
        if (have >= n) return this;

        // First choice: use quantity input
        List<WebElement> qtyEls = driver.findElements(QUANTITY_INPUT);
        if (!qtyEls.isEmpty()) {
            WebElement qty = qtyEls.get(0);
            clearAndTypeCross(qty, String.valueOf(n));
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(d -> visibleCount(emailInputs()) >= n);
            return this;
        }

        // Fallback: click "Add person" until we reach N
        int guard = 0;
        while (visibleCount(emailInputs()) < n && guard++ < n * 2) {
            safeClick(BTN_ADD_PERSON);
            waitForOverlayGone(Duration.ofSeconds(1));
        }
        new WebDriverWait(driver, Duration.ofSeconds(8))
                .until(d -> visibleCount(emailInputs()) >= n);
        return this;
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
        return By.cssSelector("input#users\\." + row + "\\.email");
    }



    private String nearestErrorText(WebElement input) {
        // 1) aria-describedby targets
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
        // 2) Common inline error under same field container (role/class)
        try {
            WebElement el = input.findElement(By.xpath(
                    "ancestor::*[self::div or self::td][1]" +
                            "//*[self::div or self::span or self::p]" +
                            "[(@role='alert') or (@type='error') or " +
                            " contains(@class,'-error') or contains(translate(@class,'ERROR','error'),'error')][1]"
            ));
            String txt = el.getText();
            if (txt != null && !txt.isBlank()) return txt.trim();
        } catch (NoSuchElementException ignored) {}

        // 3) Next sibling-ish fallback
        try {
            WebElement el = input.findElement(By.xpath(
                    "following::*[self::div or self::p or self::span]" +
                            "[(@role='alert') or (@type='error') or " +
                            " contains(@class,'-error') or contains(translate(@class,'ERROR','error'),'error')][1]"
            ));
            String txt = el.getText();
            if (txt != null && !txt.isBlank()) return txt.trim();
        } catch (NoSuchElementException ignored) {}

        return null;
    }



    private int safeParseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }








    // TEAM METHODS ACTIONS
    @Step("Select 'Add members to existing team'")
    public AssessmentEntryPage selectAddMembersToExistingTeam() {
        waitForOverlayGone(Duration.ofSeconds(2));
        try { safeClick(radioAddMembersExisting()); }
        catch (Throwable ignored) { safeClick(radioInnerByText("Add members to existing team")); }
        new WebDriverWait(driver, Duration.ofSeconds(6))
                .until(ExpectedConditions.visibilityOfElementLocated(radioCheckedBadgeByText("Add members to existing team")));
        waitForOverlayGone(Duration.ofSeconds(1));
        return this;
    }

    @Step("Type Organization name: {name}")
    public AssessmentEntryPage setOrganizationName(String name) {
        waitForOverlayGone(Duration.ofSeconds(1));
        WebElement el = new WebDriverWait(driver, Duration.ofSeconds(8))
                .until(ExpectedConditions.elementToBeClickable(ORG_NAME_INPUT));
        scrollToElement(el);
        clearAndTypeCross(el, name == null ? "" : name);
        blur(el);
        return this;
    }

    @Step("Type Group name: {name}")
    public AssessmentEntryPage setGroupName(String name) {
        waitForOverlayGone(Duration.ofSeconds(1));
        WebElement el = new WebDriverWait(driver, Duration.ofSeconds(8))
                .until(ExpectedConditions.elementToBeClickable(GROUP_NAME_INPUT));
        scrollToElement(el);
        clearAndTypeCross(el, name == null ? "" : name);
        blur(el);
        return this;
    }

    public String getOrganizationNameError() {
        try {
            WebElement el = new WebDriverWait(driver, Duration.ofSeconds(6))
                    .until(ExpectedConditions.presenceOfElementLocated(ORG_NAME_INPUT));
            return nearestErrorText(el);
        } catch (Throwable t) { return null; }
    }

    public String getGroupNameError() {
        try {
            WebElement el = new WebDriverWait(driver, Duration.ofSeconds(6))
                    .until(ExpectedConditions.presenceOfElementLocated(GROUP_NAME_INPUT));
            return nearestErrorText(el);
        } catch (Throwable t) { return null; }
    }

    /** True if 'Create new team' radio is currently selected. */
    public boolean isCreateNewTeamSelected() {
        return isVisible(radioCheckedBadgeByText("Create new team"));
    }

    /** Generic blur helper to trigger validations. */
    private void blur(WebElement el) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].dispatchEvent(new Event('blur', {bubbles:true}));", el);
        } catch (Throwable ignored) {}
    }



    /** Matches common duplicate-email phrasings shown inline. */
    private static final Pattern DUP_MSG =
            Pattern.compile("(?is)\\b(duplicate|duplicated|already\\s*exists?|already\\s*in\\s*use|in\\s*use|used)\\b");

    public boolean emailRowHasDuplicateError(int row) {
        String msg = getEmailErrorAtRow(row);
        return msg != null && DUP_MSG.matcher(msg).find();
    }


    @Step("Enter number of individuals: {count}")
    public AssessmentEntryPage enterNumberOfIndividuals2(String count) {
        final String value = (count == null || count.isBlank()) ? "1" : count.trim();
        waitForOverlayGone(Duration.ofSeconds(3));

        // Find the spinner with a tolerant strategy
        WebElement qty = null;
        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(12));
        try {
            qty = w.until(ExpectedConditions.visibilityOfElementLocated(QUANTITY_INPUT));
        } catch (TimeoutException ignore) {
            try { qty = w.until(ExpectedConditions.visibilityOfElementLocated(QUANTITY_BY_LABEL)); }
            catch (TimeoutException e2) {
                // As a last resort, try any numeric-ish input visible above the email grid
                qty = w.until(ExpectedConditions.visibilityOfElementLocated(
                        By.xpath("(//input[@type='number' or @inputmode='numeric' or @role='spinbutton'])[1]")));
            }
        }

        scrollToElement(qty);
        clearAndTypeCross(qty, value);
        commitQuantity(qty);             // ← ensure React/AntD applies it

        // Now wait for UI reaction: either inline error OR rows matching the value (or clamped ≤ 20)
        final int want = safeParseInt(value, 1);
        WebElement finalQty = qty;
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> {
            String err = nearestErrorText(finalQty);
            int rows   = renderedEmailRows();   // your existing helper
            // accept: error present OR rows reacted (>= want) OR clamped (≤20 if want>20)
            return (err != null && !err.isBlank())
                    || (rows >= Math.min(want, 20))
                    || (want > 20 && rows <= 20);
        });

        waitForOverlayGone(Duration.ofSeconds(1));
        return this;
    }










    // ---------- Upload helpers (paste into AssessmentEntryPage) ----------

    // Locator for tolerant file input (visible or hidden). Kept here so tests can reuse it.
    private static final By TOLERANT_FILE_INPUT = By.xpath(
            "(" +
                    // original heuristic
                    "//input[@type='file' and (contains(translate(@accept,'CSV','csv'),'csv') " +
                    " or contains(translate(@name,'FILE','file'),'file') " +
                    " or contains(translate(@id,'FILE','file'),'file') " +
                    " or contains(translate(@class,'UPLOAD','upload'),'upload'))] | " +
                    // common Ant Upload / Dropzone containers
                    "//div[contains(@class,'ant-upload') or contains(@class,'dropzone') or " +
                    "      contains(@class,'upload') or contains(@class,'uploader')]" +
                    "//input[@type='file'] | " +
                    // ultimate fallback – any file input on the page
                    "//input[@type='file']" +
                    ")[1]"
    );


    private static final By TEMPLATE_DOWNLOAD_BTN = By.xpath(
            "(" +
                    // <button>Download …</button>  (text may include icon/whitespace)
                    "//button[.//text()[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'download')]]" +
                    " | " +
                    // <a download>Download</a> or <a> with text 'Download'
                    "//a[@download or .//text()[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'download')]]" +
                    ")[1]"
    );



    public AssessmentEntryPage clickDownloadButton() {
        By downloadBtn = By.xpath("//button[normalize-space()='Download']");
        wait.until(ExpectedConditions.elementToBeClickable(downloadBtn)).click();
        return this;
    }



    /**
     * Create a temporary CSV file with UTF-8 content. Caller responsible for handling IOException.
     * Returns a File whose deleteOnExit() is set.
     */
    public java.io.File createTempCsv(String prefix, String content) throws java.io.IOException {
        java.nio.file.Path p = java.nio.file.Files.createTempFile(prefix, ".csv");
        java.nio.file.Files.write(p, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.io.File f = p.toFile();
        f.deleteOnExit();
        return f;
    }





    @Step("Upload team CSV file: {absolutePathToCsv}")
    public AssessmentEntryPage uploadCsvFile(String absolutePathToCsv) {
        By uploadBtn  = By.xpath("//button[normalize-space()='Upload file']");
        By uploadWrap = By.xpath("//div[contains(@class,'ant-upload')][.//button[normalize-space()='Upload file']]");
        By fileInput  = By.xpath("("
                + "//div[contains(@class,'ant-upload')][.//button[normalize-space()='Upload file']]"
                + "//input[@type='file' and not(@disabled)]) [last()]");

        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(12));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Ensure the upload area is rendered
        WebElement anchor = w.until(ExpectedConditions.presenceOfElementLocated(uploadBtn));
        js.executeScript("arguments[0].scrollIntoView({block:'center'});", anchor);

        if (driver.findElements(fileInput).isEmpty()) {
            selectDownloadTemplate(); // your existing helper
            w.until(ExpectedConditions.presenceOfElementLocated(uploadWrap));
            w.until(ExpectedConditions.presenceOfElementLocated(fileInput));
        }

        WebElement input = driver.findElement(fileInput);

        js.executeScript(
                "arguments[0].style.display='block';"
                        + "arguments[0].style.visibility='visible';"
                        + "arguments[0].style.opacity='1';"
                        + "arguments[0].style.width='1px';"
                        + "arguments[0].style.height='1px';",
                input
        );

        input.sendKeys(absolutePathToCsv);

        // Wait for some sign that upload was processed
        new WebDriverWait(driver, Duration.ofSeconds(12)).until(d -> {
            boolean hasListItem = !d.findElements(
                    By.xpath("//div[contains(@class,'ant-upload-list')]//span[contains(@class,'ant-upload-list-item-name')]")
            ).isEmpty();
            int rows = renderedEmailRows();
            return hasListItem || rows > 0;
        });

        return this;
    }


//    public AssessmentEntryPage uploadCsvFile(String absolutePathToCsv) {
//        // Locators scoped to the upload area for the template path
//        By uploadBtn  = By.xpath("//button[normalize-space()='Upload file']");
//        By uploadWrap = By.xpath("//div[contains(@class,'ant-upload')][.//button[normalize-space()='Upload file']]");
//        By fileInput  = By.xpath("("
//                + "//div[contains(@class,'ant-upload')][.//button[normalize-space()='Upload file']]"
//                + "//input[@type='file' and not(@disabled)]) [last()]");
//
//        // 1) Fast path: if the upload UI is already there, use it. Otherwise, re-select the radio.
//        if (driver.findElements(fileInput).isEmpty()) {
//            // Make sure the section is on screen so the DOM mounts
//            WebElement anchor = new WebDriverWait(driver, Duration.ofSeconds(5))
//                    .until(ExpectedConditions.presenceOfElementLocated(uploadBtn));
//            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", anchor);
//
//            if (driver.findElements(fileInput).isEmpty()) {
//                // Force AntD to render the template-upload panel again
//                selectDownloadTemplate();                 // <-- use your existing method
//                new WebDriverWait(driver, Duration.ofSeconds(8))
//                        .until(ExpectedConditions.presenceOfElementLocated(uploadWrap));
//                new WebDriverWait(driver, Duration.ofSeconds(8))
//                        .until(ExpectedConditions.presenceOfElementLocated(fileInput));
//            }
//        }
//
//        WebElement input = driver.findElement(fileInput);
//
//        // 2) Make it keyable even if hidden
//        ((JavascriptExecutor) driver).executeScript(
//                "arguments[0].style.display='block';"
//                        + "arguments[0].style.visibility='visible';"
//                        + "arguments[0].style.opacity='1';"
//                        + "arguments[0].style.width='1px';"
//                        + "arguments[0].style.height='1px';", input);
//
//        // 3) Send the file path (bypasses the native picker)
//        input.sendKeys(absolutePathToCsv);
//
//        // 4) Wait for any reaction that indicates the upload was processed/rejected
//        new WebDriverWait(driver, Duration.ofSeconds(12)).until(d -> {
//            boolean hasListItem = !driver.findElements(
//                    By.xpath("//div[contains(@class,'ant-upload-list')]//span[contains(@class,'ant-upload-list-item-name')]")
//            ).isEmpty();
//            boolean inlineRequired = !driver.findElements(
//                    By.xpath("//*[contains(@class,'ant-form-item-explain')]"
//                            + "//*[contains(translate(normalize-space(.),"
//                            + "'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'required')]")
//            ).isEmpty();
//            boolean proceedDisabled = !isProceedToPaymentEnabled();
//            int rows = renderedEmailRows();
//            return hasListItem || inlineRequired || proceedDisabled || rows == 0;
//        });
//
//        return this;
//    }







    public String waitForUploadErrorText() {
        List<By> candidates = List.of(
                By.cssSelector(".ant-message-notice, .ant-message-error, .ant-alert-error"),
                By.xpath("//*[contains(@class,'error') or contains(@class,'invalid') or contains(@class,'alert')][string-length(normalize-space())>0]"),
                By.xpath("//div[contains(.,'csv') and (contains(.,'invalid') or contains(.,'missing') or contains(.,'parse'))]")
        );

        long end = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < end) {
            for (By by : candidates) {
                List<WebElement> els = driver.findElements(by);
                for (WebElement el : els) {
                    String txt = el.getText().trim();
                    if (!txt.isEmpty()) return txt;
                }
            }
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }
        return "";
    }


    public boolean isDownloadTemplateSelected() {
        List<WebElement> radios = driver.findElements(By.xpath(
                "//label[contains(@class,'ant-radio-wrapper')]" +
                        "[.//span[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'download template')]]" +
                        "//input[@type='radio']"
        ));
        if (radios.isEmpty()) return false; // grid mode: radio not in DOM
        WebElement input = radios.get(0);
        return Boolean.TRUE.equals(((JavascriptExecutor) driver).executeScript(
                "return arguments[0].checked === true;", input));
    }



    public boolean antUploadShowsSuccess() {
        // AntD marca ítems con clases de éxito; cubrimos varias
        return !driver.findElements(By.xpath(
                        "//div[contains(@class,'ant-upload-list')]//*[contains(@class,'-success') or contains(@class,'-done') or contains(@class,'-finished')]"))
                .isEmpty();
    }

    public boolean isUploadPanelVisible() {
        return !driver.findElements(By.xpath(
                        "//div[contains(@class,'ant-upload')][.//button[normalize-space()='Upload file']]"))
                .isEmpty();
    }







    // Root that contains the manual-entry grid
    private By manualSectionRoot() {
        // adjust if your page has a tighter container, but this works broadly
        return By.xpath("//div[.//input[@placeholder='Email'] and .//input[@placeholder='First Name']]");
    }



    // how many rows were rendered
    public int renderedEmailRows() {
        return driver.findElements(usersEmailInputs()).size();
    }




//    public int inlineRequiredErrorsCount() {
//        try {
//            JavascriptExecutor js = (JavascriptExecutor) driver;
//            String script =
//                    "const root = document;                                                       " +
//                            // candidates: your `span[type="error"]`, plus a few generic fallbacks
//                            "const candidates = Array.from(root.querySelectorAll(                        " +
//                            "  'span[type=\"error\"], [data-error=\"true\"], [role=\"alert\"], .error'   " +
//                            "));                                                                         " +
//                            // visible?
//                            "const vis = candidates.filter(n => {                                        " +
//                            "  const s = getComputedStyle(n);                                            " +
//                            "  const hasBox = !!(n.offsetParent || s.position === 'fixed');             " +
//                            "  return hasBox && n.textContent.trim().length > 0;                        " +
//                            "});                                                                         " +
//                            "return vis.length;";
//            Long count = (Long) js.executeScript(script);
//            return count == null ? 0 : count.intValue();
//        } catch (Exception e) {
//            System.out.println("[inlineRequiredErrorsCount] " + e.getMessage());
//            return 0;
//        }
//    }


    public int inlineRequiredErrorsCount() {
        try {
            // Prefer the manual-entry grid as the root; fall back to the main panel
            WebElement root = null;
            List<WebElement> roots = driver.findElements(manualSectionRoot());
            if (!roots.isEmpty()) {
                root = roots.get(0);
            } else {
                List<WebElement> panels = driver.findElements(MAIN_PANEL);
                root = panels.isEmpty() ? driver.findElement(By.tagName("body")) : panels.get(0);
            }

            String script =
                    "const root = arguments[0];                                                        " +
                            "const q = (sel) => Array.from(root.querySelectorAll(sel));                       " +
                            "const visible = (n) => {                                                          " +
                            "  const s = getComputedStyle(n);                                                  " +
                            "  if (s.visibility==='hidden' || s.display==='none' || +s.opacity===0) return false;" +
                            "  const r = n.getBoundingClientRect();                                            " +
                            "  return r.width>0 && r.height>0;                                                 " +
                            "};                                                                                " +
                            // candidates ONLY within the grid/panel
                            "const nodes = q('.ant-form-item-explain-error, span[type=\"error\"], [role=\"alert\"]');" +
                            // filter to meaningful field errors
                            "const re = /(required|invalid|email|duplicate|in use|exists)/i;                  " +
                            "const hits = nodes.filter(n => visible(n) && re.test((n.textContent||'').trim()));" +
                            "return hits.length;                                                               ";

            Long count = (Long) ((JavascriptExecutor) driver).executeScript(script, root);
            return count == null ? 0 : count.intValue();
        } catch (Exception e) {
            System.out.println("[inlineRequiredErrorsCount] " + e.getMessage());
            return 0;
        }
    }



    /**
     * Force AntD to "touch" inputs so field-level validation is shown.
     * We make a real change (SPACE then BACK_SPACE), then TAB away.
     */
    public void triggerManualValidationBlurs(int rows) {
        List<By> fields = Arrays.asList(
                By.xpath("//input[@placeholder='First Name']"),
                By.xpath("//input[@placeholder='Last Name']"),
                By.xpath("//input[@placeholder='Email']")
        );
        Actions a = new Actions(driver);

        int touched = 0;
        int target  = Math.max(1, rows) * fields.size();

        for (By by : fields) {
            List<WebElement> inputs = driver.findElements(by);
            for (WebElement el : inputs) {
                if (!el.isDisplayed()) continue;
                a.moveToElement(el).click().sendKeys(Keys.SPACE).pause(Duration.ofMillis(30))
                        .sendKeys(Keys.BACK_SPACE).pause(Duration.ofMillis(30))
                        .sendKeys(Keys.TAB).perform();
                if (++touched >= target) return;
            }
        }
    }



    // Optional sanity checks used in assertions
    public boolean isManualGridVisible() {
        return !driver.findElements(manualSectionRoot()).isEmpty();
    }



    // any manual grid field rendered from the CSV
    private By usersEmailInputs()     { return By.cssSelector("input[id^='users.'][id$='.email'],     input[name^='users.'][name$='.email']"); }
    private By usersFirstNameInputs() { return By.cssSelector("input[id^='users.'][id$='.firstName'], input[name^='users.'][name$='.firstName']"); }
    private By usersLastNameInputs()   { return By.cssSelector("input[id^='users.'][id$='.lastName'],  input[name^='users.'][name$='.lastName']"); }



//
//    public void triggerManualValidationBlurs() {
//        List<By> fields = Arrays.asList(usersFirstNameInputs(), usersLastNameInputs(), usersEmailInputs());
//        Actions a = new Actions(driver);
//        for (By by : fields) {
//            for (WebElement el : driver.findElements(by)) {
//                if (!el.isDisplayed()) continue;
//                a.moveToElement(el).click().sendKeys(Keys.SPACE).pause(Duration.ofMillis(25))
//                        .sendKeys(Keys.BACK_SPACE).sendKeys(Keys.TAB).perform();
//            }
//        }
//    }


    /** Triggers field validation without mutating values (safe for type=email). */
    public void triggerManualValidationBlurs() {
        List<WebElement> inputs = new ArrayList<>();
        inputs.addAll(driver.findElements(usersFirstNameInputs()));
        inputs.addAll(driver.findElements(usersLastNameInputs()));
        inputs.addAll(driver.findElements(usersEmailInputs()));

        JavascriptExecutor js = (JavascriptExecutor) driver;

        for (WebElement el : inputs) {
            if (el == null) continue;
            try {
                if (!el.isDisplayed()) continue;

                // Focus → (optional change) → Blur to mark as touched; no value edits
                js.executeScript("arguments[0].focus();", el);

                // If your form only validates on 'change', fire a synthetic change without altering value.
                js.executeScript(
                        "const e = new Event('change', {bubbles:true}); arguments[0].dispatchEvent(e);",
                        el
                );

                js.executeScript("arguments[0].blur();", el);

                // WebDriver-only fallback (no SPACE/BACK_SPACE): click then TAB
                try {
                    new org.openqa.selenium.interactions.Actions(driver)
                            .moveToElement(el).click().pause(Duration.ofMillis(20))
                            .sendKeys(Keys.TAB).perform();
                } catch (Throwable ignored) {}

            } catch (Throwable ignored) {}
        }
    }





    // --- Inline error collectors -------------------------------------------------

    /** Returns the visible inline error messages as raw texts (de-duplicated, trimmed). */
    public List<String> collectInlineErrorTexts() {
        List<By> probes = List.of(
                // Ant Design explain error line
                By.cssSelector(".ant-form-item-explain-error, .ant-form-item-explain .ant-form-item-explain-error"),
                // Generic “error” spans like: <span type="error">...</span>
                By.xpath("//*[self::span or self::div][@type='error' and normalize-space(string(.))!='']"),
                // Fallback: anything that looks like an error badge with 'Required'/'invalid'/'email'
                By.xpath("//*[self::span or self::div][contains(translate(.,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'required') " +
                        "or contains(translate(.,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'invalid') " +
                        "or contains(translate(.,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'email')]")
        );

        LinkedHashSet<String> texts = new LinkedHashSet<>();
        for (By by : probes) {
            for (WebElement el : driver.findElements(by)) {
                String t = safeVisibleText(el);
                if (t.isEmpty()) continue;
                // Filter obvious non-error noise (e.g., stray URLs/paths)
                if (t.startsWith("/") && !t.contains(" ")) continue;
                texts.add(t);
            }
        }
        return new ArrayList<>(texts);
    }

    /**
     * Maps field identifiers (usually the input id/name) -> visible inline error text.
     * Works with structures like:
     * <div> <input id="users.0.email" .../> <div><span type="error">Please enter a valid email...</span></div> </div>
     */
    public Map<String, String> collectPerFieldErrors() {
        Map<String, String> out = new LinkedHashMap<>();

        // Target the manual-entry grid inputs; adjust the root/container CSS if you scope the grid.
        List<WebElement> inputs = driver.findElements(By.cssSelector("input[id^='users.'], input[name^='users.']"));

        for (WebElement input : inputs) {
            String key = firstNonBlank(input.getAttribute("id"), input.getAttribute("name"));
            if (key == null) continue;

            // Find the closest container that might host an error node for this input
            // and then look for a visible error element inside it.
            List<By> errorLookups = List.of(
                    By.xpath("./ancestor::div[1]//*[self::span or self::div][@type='error' and normalize-space(string(.))!='']"),
                    By.xpath("./ancestor::div[contains(@class,'ant-form-item')][1]//*[contains(@class,'ant-form-item-explain-error') and normalize-space(string(.))!='']"),
                    By.xpath("./following-sibling::*[1]//*[self::span or self::div][@type='error' and normalize-space(string(.))!='']"),
                    By.xpath("./parent::*//*[contains(@class,'-error') and normalize-space(string(.))!='']")
            );

            String msg = null;
            for (By errBy : errorLookups) {
                for (WebElement cand : input.findElements(errBy)) {
                    String txt = safeVisibleText(cand);
                    if (!txt.isEmpty()) { msg = txt; break; }
                }
                if (msg != null) break;
            }

            if (msg != null) out.put(key, msg);
        }

        return out;
    }


    private String safeVisibleText(WebElement el) {
        try {
            if (!el.isDisplayed()) return "";
            String t = el.getText();
            if (t == null || t.isBlank()) t = el.getAttribute("textContent");
            return t == null ? "" : t.trim();
        } catch (org.openqa.selenium.StaleElementReferenceException ignored) {
            return "";
        }
    }

    private String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }



    // put these near your ORG_NAME_INPUT / GROUP_NAME_INPUT
    public String getOrganizationName() {
        try {
            WebElement el = new WebDriverWait(driver, Duration.ofSeconds(6))
                    .until(ExpectedConditions.presenceOfElementLocated(ORG_NAME_INPUT));
            return Objects.toString(el.getAttribute("value"), "").trim();
        } catch (Exception e) { return ""; }
    }

    public String getGroupName() {
        try {
            WebElement el = new WebDriverWait(driver, Duration.ofSeconds(6))
                    .until(ExpectedConditions.presenceOfElementLocated(GROUP_NAME_INPUT));
            return Objects.toString(el.getAttribute("value"), "").trim();
        } catch (Exception e) { return ""; }
    }

    /** Returns the email currently shown in row `index1` (1-based). Works for input or grid cell. */
    public String getEmailAtRow(int index1) {
        // Try the input first (covers manual grid edit mode)
        try {
            WebElement input = findEmailInput(index1, Duration.ofSeconds(3));
            if (input != null) {
                String v = input.getAttribute("value");
                if (v != null && !v.isBlank()) return v.trim();
            }
        } catch (Exception ignored) {}

        // Fallback: read the cell text in the table (covers read-only renderings)
        try {
            WebElement cell = driver.findElement(By.xpath("(//table//tbody//tr)[" + index1 + "]//td[.//input or .//* or text()]"));
            String txt = Objects.toString(cell.getText(), "").trim();
            if (!txt.isBlank()) return txt;
        } catch (Exception ignored) {}

        return "";
    }



    /** Collect all emails in the manual-entry area, lowercased & distinct.
     *  Prefers input values (truth) over rendered cell text to avoid zoom/ellipsis issues. */
    public List<String> collectAllEmailsLower() {
        final Set<String> out = new LinkedHashSet<>();
        final Pattern EMAIL_RE = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);

        // small normalizer to remove invisible/trimming chars and lowercase
        java.util.function.Function<String,String> norm = s -> {
            if (s == null) return null;
            String t = s
                    .replace('\u00A0',' ')                // nbsp -> space
                    .replace("\u200B","")                 // zero-width space
                    .replace("\u200C","")                 // zero-width non-joiner
                    .replace("\u200D","")                 // zero-width joiner
                    .replace("\uFEFF","")                 // zero-width no-break
                    .replace("\u00AD","")                 // soft hyphen
                    .trim()
                    .toLowerCase(Locale.ROOT);
            return t.isEmpty() ? null : t;
        };

        // Helper to add if it looks like an email
        java.util.function.Consumer<String> addIfEmail = raw -> {
            String n = norm.apply(raw);
            if (n != null && EMAIL_RE.matcher(n).find()) out.add(n);
        };

        // A) INPUTS (truth) — do NOT depend on visibility (zoom can hide/clip)
        try {
            for (WebElement el : driver.findElements(usersEmailInputs())) {
                try {
                    // value attribute/property
                    String v = el.getAttribute("value");
                    if (v == null || v.isBlank()) {
                        // fallback: direct JS property (sometimes more up-to-date for React)
                        v = String.valueOf(((JavascriptExecutor) driver).executeScript("return arguments[0].value;", el));
                    }
                    addIfEmail.accept(v);
                } catch (Exception ignored) {}
            }
            if (!out.isEmpty()) return new ArrayList<>(out);
        } catch (Exception ignored) {}

        // Scope root once (manual-entry section only)
        WebElement root = null;
        try { root = driver.findElement(manualSectionRoot()); } catch (Exception ignored) {}

        // B) CELLS: prefer title/tooltips first (often has the untruncated value)
        if (root != null) {
            try {
                List<WebElement> cells = root.findElements(By.xpath(".//*[self::td or self::div or self::span][contains(.,'@')]"));
                for (WebElement c : cells) {
                    try {
                        String title = c.getAttribute("title");
                        if (title != null && !title.isBlank()) addIfEmail.accept(title);

                        // also check common tooltip attrs
                        String dataTitle = c.getAttribute("data-original-title");
                        if (dataTitle != null && !dataTitle.isBlank()) addIfEmail.accept(dataTitle);

                        // LASTLY: full textContent via JS (not affected by CSS ellipsis)
                        String textContent = String.valueOf(((JavascriptExecutor) driver)
                                .executeScript("return arguments[0].textContent || '';", c));
                        addIfEmail.accept(textContent);
                    } catch (Exception ignored) {}
                }
                if (!out.isEmpty()) return new ArrayList<>(out);
            } catch (Exception ignored) {}
        }

        // C) HTML fallback (scoped) — regex over the section’s HTML
        if (root != null) {
            try {
                String outer = String.valueOf(((JavascriptExecutor) driver)
                        .executeScript("return arguments[0].outerHTML;", root));
                java.util.regex.Matcher m = EMAIL_RE.matcher(outer);
                while (m.find()) addIfEmail.accept(m.group());
            } catch (Exception ignored) {}
        }

        return new ArrayList<>(out);
    }




    /** Wait until manual grid shows at least N email fields or cells. */
    public void waitManualGridEmailsAtLeast(int n, Duration timeout) {
        new WebDriverWait(driver, timeout).until(d -> {
            try {
                int inputs = d.findElements(usersEmailInputs()).stream().filter(WebElement::isDisplayed).toList().size();
                if (inputs >= n) return true;
                // or visible cells with '@'
                int cells = d.findElements(By.xpath("//table//tbody//tr//td[contains(.,'@')]")).size();
                return cells >= n;
            } catch (Exception e) {
                return false;
            }
        });
    }








}
