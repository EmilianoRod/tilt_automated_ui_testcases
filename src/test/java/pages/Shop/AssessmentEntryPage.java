package pages.Shop;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.BasePage;

import java.time.Duration;
import java.util.List;

public class AssessmentEntryPage extends BasePage {

    public AssessmentEntryPage(WebDriver driver) {
        super(driver);
    }




    // ------- Stable, text-anchored locators -------
    // Click the LABEL (frameworks often hide the input)
    private final By manualEntryRadio = By.xpath(
            "//span[@class='ant-radio ant-wave-target ant-radio-checked']"
    );

    // Also add a label locator to get a clickable element.
    private final By manualEntryLabel = By.xpath("(//label)[1]");

    private final By manualEntryInput = By.xpath("(//input[@type='radio'])[1]");

    // Choose ONE rows locator. I’ll use a tolerant CSS:
    private final By personRowsLoc = By.cssSelector("[data-test='person-row'], [role='row'], .person-row");

    // Email inputs (used as a secondary signal that a row appeared)
    private final By emailInputsLoc = By.cssSelector("input[type='email'], input[name*='email' i], input[id*='email' i]");

    // Optional overlay/spinner
    private final By blockingOverlay = By.xpath(
            "//*[self::div or self::span][contains(@class,'loading') or contains(@class,'spinner') or contains(@class,'overlay') or @role='progressbar']"
    );

    // Add/Plus triggers that create the first row
    private final By addPersonButton = By.xpath("//span[@aria-label='Increase Value']//span"
    );


    // keep your broad row container too, if you have one:
    private final By rowContainers = By.xpath(
            "//form[@data-hs-cf-bound='true']"
    );

    // "Download template" radio
    private final By DOWNLOAD_TEMPLATE_RADIO = By.xpath(
            "//section[.//h2[normalize-space()='Purchase Information']]//label[normalize-space()='Download template']//preceding::input[@type='radio'][1]"
    );


    // flexible quantity selector (ids/names can vary)
    private final By quantityInput = By.xpath(
            "//section[.//h2[normalize-space()='Purchase Information']]"
                    + "//*[@role='spinbutton' or self::input[@type='number' or @inputmode='numeric'"
                    + " or contains(translate(@name,'QUANTITY','quantity'),'quant')"
                    + " or contains(translate(@id,'QUANTITY','quantity'),'quant')]][1]"
    );

    // Your UI copy varies; support both “Next” and “Proceed”
    private final By proceedButton = By.xpath(
            "//button[normalize-space()='Proceed to payment']"
    );

    By CANCEL_BTN = By.xpath(
            "//main//button[normalize-space()='Cancel']"
    );


    // Dynamic locators for participant rows
    private By firstNameFieldInRow(int rowIndex) {
        return By.xpath("(//table//tr[.//td]//input[contains(@placeholder,'First') " +
                "or @name='firstName' or @aria-label='First name'])[" + rowIndex + "]");
    }

    private By lastNameFieldInRow(int rowIndex) {
        return By.xpath("(//table//tr[.//td]//input[contains(@placeholder,'Last') " +
                "or @name='lastName' or @aria-label='Last name'])[" + rowIndex + "]");
    }

    private By emailFieldInRow(int rowIndex) {
        return By.xpath("(//table//tr[.//td]//input[contains(@type,'email') " +
                "or contains(@placeholder,'mail') or @name='email' or @aria-label='Email'])[" + rowIndex + "]");
    }





    // ================== Helpers ==================
    private void scrollCenter(WebElement el) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center', inline:'center'})", el);
    }

    private void waitOverlayGone(Duration timeout) {
        new WebDriverWait(driver, timeout).until(d -> {
            List<WebElement> els = d.findElements(blockingOverlay);
            for (WebElement e : els) {
                if (e.isDisplayed()) return false;
            }
            return true;
        });
    }


    private int currentRowCount() {
        return driver.findElements(personRowsLoc).size();
    }


    // ---------------- Public API ----------------

    /** Ensure key elements are present and page isn't covered by an overlay. */
    public AssessmentEntryPage waitUntilLoaded() {
        try { wait.waitForElementInvisible(blockingOverlay); } catch (Exception ignore) {
            //ignore if not present
        }
        wait.waitForElementVisible(manualEntryLabel);
        return this;
    }


    /** Robustly select “Manual entry”. Click the label, with scroll + JS fallback. */
    public AssessmentEntryPage selectManualEntry() {
        // Wait section usable
        waitOverlayGone(Duration.ofSeconds(10));

        WebElement label = new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.elementToBeClickable(manualEntryLabel));

        scrollCenter(label);
        try {
            label.click();
        } catch (ElementClickInterceptedException e) {
            jsClick(label);
        }

        // Assert the INPUT is selected. If frameworks hide it, we still can read .isSelected()
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(d -> d.findElement(manualEntryInput).isSelected());

        return this;
    }

    public AssessmentEntryPage clickAddPerson() {
        waitOverlayGone(Duration.ofSeconds(10));

        int before = currentRowCount();

        WebElement addBtn = new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.elementToBeClickable(addPersonButton));

        scrollCenter(addBtn);
        try {
            addBtn.click();
        } catch (ElementClickInterceptedException e) {
            jsClick(addBtn);
        }

        // Either a new row appears or at least one email input shows up
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(d -> currentRowCount() > before || !d.findElements(emailInputsLoc).isEmpty());

        waitOverlayGone(Duration.ofSeconds(5));
        return this;
    }

    /** Set number of individuals (defaults to 1 if null/blank). */
    public AssessmentEntryPage enterNumberOfIndividuals(String count) {
        String value = (count == null || count.isBlank()) ? "1" : count.trim();
        wait.waitForElementInvisible(blockingOverlay);
        WebElement qty = wait.waitForElementVisible(quantityInput);
        scrollIntoViewCenter(qty);
        clearAndTypeCross(qty, value);
        return this;
    }



    /**
     * Fill First/Last/Email for the Nth row (1-based index).
     * Tolerant to minor DOM changes by scoping to a row that contains an email field.
     */
    public AssessmentEntryPage fillUserDetailsAtIndex(int oneBasedIndex, String first, String last, String email) {
        if (oneBasedIndex < 1) oneBasedIndex = 1;

        List<WebElement> rows = wait.waitForAllVisible(rowContainers); // make sure your WaitUtils has this
        if (rows.size() < oneBasedIndex) {
            throw new NoSuchElementException("Only " + rows.size() + " row(s) rendered; asked for row " + oneBasedIndex);
        }
        WebElement row = rows.get(oneBasedIndex - 1);

        WebElement firstNameEl = findInScope(row, By.xpath(".//input[contains(@placeholder,'First') or @name='firstName' or @aria-label='First name']"));
        WebElement lastNameEl  = findInScope(row, By.xpath(".//input[contains(@placeholder,'Last')  or @name='lastName'  or @aria-label='Last name']"));
        WebElement emailEl     = findInScope(row, By.xpath(".//input[@type='email' or contains(@placeholder,'mail') or @name='email' or @aria-label='Email']"));

        if (firstNameEl == null || lastNameEl == null || emailEl == null) {
            throw new NoSuchElementException("Could not locate all inputs in row " + oneBasedIndex);
        }

        scrollIntoViewCenter(row);
        clearAndTypeCross(firstNameEl, first);
        clearAndTypeCross(lastNameEl,  last);
        clearAndTypeCross(emailEl,     email);
        return this;
    }

    private void clearAndTypeCross(WebElement el, String text) {
        try {
            el.sendKeys(Keys.chord(Keys.COMMAND, "a"));
            el.sendKeys(Keys.DELETE);
        } catch (Exception ignored) {}
        try {
            el.sendKeys(Keys.chord(Keys.CONTROL, "a"));
            el.sendKeys(Keys.DELETE);
        } catch (Exception ignored) {}
        try {
            if (!el.getAttribute("value").isEmpty()) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].value=''; arguments[0].dispatchEvent(new Event('input',{bubbles:true}));", el);
            }
        } catch (Exception ignored) {}
        el.sendKeys(text);
    }




    /** Click proceed/next to reach the Order Preview step. */
    public OrderPreviewPage clickProceedToPayment() {
        try { wait.waitForElementInvisible(blockingOverlay); } catch (Exception ignore) {}
        WebElement btn = wait.waitForElementClickable(proceedButton);
        scrollIntoViewCenter(btn);
        try {
            btn.click();
        } catch (ElementClickInterceptedException e) {
            jsClick(btn);
        }
        return new OrderPreviewPage(driver);
    }

    // Optional utility some tests rely on
    public int getTotalInputRowsRendered() {
        return driver.findElements(rowContainers).size();
    }

    // ---------------- Private helpers ----------------

    private WebElement findInScope(WebElement scope, By locator) {
        try { return scope.findElement(locator); } catch (NoSuchElementException e) { return null; }
    }

    private void clearAndType(WebElement el, String text) {
        el.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        el.sendKeys(Keys.DELETE);
        el.sendKeys(text);
    }

    private void scrollIntoViewCenter(WebElement el) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center', inline:'nearest'});", el);
    }

    private void jsClick(WebElement el) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
    }

    /** Try to resolve the <input type='radio'> linked to a label via @for or proximity. */
    private WebElement resolveRadioFromLabel(WebElement label) {
        try {
            String forId = label.getAttribute("for");
            if (forId != null && !forId.isBlank()) return driver.findElement(By.id(forId));
        } catch (NoSuchElementException ignore) {}
        try {
            return label.findElement(By.xpath("(preceding::input[@type='radio'][1] | following::input[@type='radio'][1])"));
        } catch (NoSuchElementException ignore) {}
        return null;
    }

    private void waitUntilSelected(WebElement radio) {
        long end = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < end) {
            try {
                if (radio.isSelected()) return;
            } catch (StaleElementReferenceException ignored) {}
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
    }

}