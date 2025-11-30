package pages;

import Utils.Config;
import Utils.WaitUtils;
import io.qameta.allure.Step;

import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.MoveTargetOutOfBoundsException;
import org.openqa.selenium.io.FileHandler;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.menuPages.ResourcesPage;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for all page objects.
 * - Centralizes robust UI interactions (safe click/type/scroll).
 * - Provides tolerant identity helpers and common waits.
 * - Exposes small utilities (data-test locators, checkbox/select setters, screenshots).
 *
 * Keep this page UI-centric. Session/auth lifecycle belongs in BaseTest.
 */
public abstract class BasePage {

    protected final WebDriver driver;
    protected final WaitUtils  wait;

    // Generic overlay/backdrop patterns used across Ant/MUI/ARIA apps (opt-in by pages)
    protected static final By POSSIBLE_LOADER = By.cssSelector(
            "[data-testid='loading'],[data-test='loading'],[role='progressbar']," +
                    ".MuiBackdrop-root,.MuiCircularProgress-root,.ant-spin,.ant-spin-spinning," +
                    ".overlay,.spinner,.backdrop,[aria-busy='true']"
    );

    // ---------- ctor ----------
    public BasePage(WebDriver driver) {
        if (driver == null) {
            throw new IllegalArgumentException("❌ WebDriver is NULL for " + getClass().getSimpleName());
        }
        this.driver = driver;
        // ✅ just pass the Duration directly
        this.wait = new WaitUtils(driver, Config.getTimeout());
    }


    // ======================================================================
    // Purchase-for banner (used by many pages)
    // ======================================================================

    /** Case/whitespace tolerant “Assessment purchase for: <label>” banner. */
    protected By purchaseForBanner(String label) {
        String lab = esc(label);
        return By.xpath("//*[contains(translate(normalize-space(string(.)),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'assessment purchase for') and .//*[normalize-space()='" + lab + "'] and not(.//*[contains(translate(normalize-space(string(.)),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'assessment purchase for') and .//*[normalize-space()='" + lab + "']])]");
    }

    /** Reads only the selected value, e.g., "Myself", "Team", "Client(s)/Individual(s)". */
    @Step("Read 'Assessment purchase for' selection")
    protected String readPurchaseForSelection() {
        try {
            WebElement el = driver.findElement(
                    By.xpath("//*[contains(translate(normalize-space(string(.)),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'assessment purchase') and contains(translate(normalize-space(string(.)),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'for') and not(.//*[contains(translate(normalize-space(string(.)),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'assessment purchase') and contains(translate(normalize-space(string(.)),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'for')])]")
            );
            String txt = el.getText();
            // Strip the prefix (handful of variants), keep the value
            String lower = txt.toLowerCase(Locale.ROOT);
            int idx = lower.indexOf("purchase for");
            if (idx >= 0) {
                String after = txt.substring(idx).replaceFirst("(?i)purchase for[:]?", "");
                return after.replace("Assessment", "").trim();
            }
            // Fallback: trim everything after the last colon
            int colon = txt.lastIndexOf(':');
            return (colon >= 0 ? txt.substring(colon + 1) : txt).trim();
        } catch (NoSuchElementException e) {
            return "";
        }
    }

    /** Public matcher you can use in tests. */
    @Step("Verify 'Assessment purchase for' is '{label}'")
    public boolean purchaseForIs(String label) {
        return isVisible(purchaseForBanner(label));
    }

    // ======================================================================
    // Page readiness
    // ======================================================================

    /** Wait for DOM ready + app-specific loaders gone (best-effort). */
    @Step("Wait for page to be ready")
    protected void pageReady() {
        try {
            wait.waitForDocumentReady();
        } catch (Throwable ignored) {

        }
        try {
            wait.waitForLoadersToDisappear();
        } catch (Throwable ignored) {

        }
    }

    // ======================================================================
    // UI Interactions (robust)
    // ======================================================================

    protected Actions actions() {
        return new Actions(driver);
    }
    protected JavascriptExecutor js() {
        return (JavascriptExecutor) driver;
    }

    /** Scroll element into view (center) with Actions fallback. */
    @Step("Scroll to element")
    protected void scrollToElement(WebElement el) {
        try {
            actions().scrollToElement(el).pause(Duration.ofMillis(40)).perform();
        } catch (Throwable ignored) {
            try { js().executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});", el); }
            catch (Throwable ignored2) {}
        }
    }

    /** Click a locator with retries and JS fallback. */
    @Step("Click on element located by {locator}")
    protected void click(By locator) {
        int attempts = 0;
        while (attempts++ < 3) {
            try {
                WebElement el = wait.waitForElementClickable(locator);
                scrollToElement(el);
                el.click();
                return;
            } catch (ElementClickInterceptedException | StaleElementReferenceException e) {
                // try again
            } catch (Throwable t) {
                // last-chance JS click
                try {
                    WebElement el = driver.findElement(locator);
                    js().executeScript("arguments[0].click();", el);
                    return;
                } catch (Throwable ignored) { /* keep looping */ }
            }
        }
        // final fallback
        WebElement el = driver.findElement(locator);
        js().executeScript("arguments[0].click();", el);
    }

    /** Click an already-found element with realistic fallbacks. */
    @Step("Click on element")
    protected void safeClick(WebElement el) {
        scrollToElement(el);
        try {
            el.click();
            return;
        } catch (ElementClickInterceptedException | MoveTargetOutOfBoundsException e) {
            try {
                actions().moveToElement(el, 1, 1).click().perform();
                return;
            } catch (Throwable ignored) { /* fall through */ }
            js().executeScript("arguments[0].click();", el);
        }
    }

    /** Click a locator by first waiting for it, then using {@link #safeClick(WebElement)}. */
    @Step("Click (safe) on element located by {locator}")
    protected void safeClick(By locator) {
        WebElement el = new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.elementToBeClickable(locator));
        safeClick(el);
    }

    /** Clear + type with cross-platform select-all and input events. */
    @Step("Type '{text}' into element located by {locator}")
    protected void type(By locator, String text) {
        WebElement el = wait.waitForElementVisible(locator);
        clearWithSelectAll(el);
        el.sendKeys(text);
        // Dispatch input event (some React apps need it)
        try {js().executeScript("arguments[0].dispatchEvent(new Event('input', {bubbles:true}));", el); }
        catch (Throwable ignored) {}
    }

    /** Clear & type with an already-found element. */
    @Step("Type '{text}' into element")
    protected void type(WebElement el, String text) {
        clearWithSelectAll(el);
        el.sendKeys(text);
        try { js().executeScript("arguments[0].dispatchEvent(new Event('input', {bubbles:true}));", el); }
        catch (Throwable ignored) {}
    }

    /** Idempotent checkbox setter. */
    @Step("Set checkbox {locator} to {shouldBeChecked}")
    protected void setCheckbox(By locator, boolean shouldBeChecked) {
        WebElement box = wait.waitForElementVisible(locator);
        scrollToElement(box);
        boolean checked = false;
        try { checked = box.isSelected(); } catch (Throwable ignored) {}
        if (checked != shouldBeChecked) safeClick(box);
    }

    /** Select by visible text; falls back to sending keys. */
    @Step("Select '{visibleText}' from dropdown {selectLocator}")
    protected void selectByVisibleText(By selectLocator, String visibleText) {
        WebElement el = wait.waitForElementVisible(selectLocator);
        try {
            new Select(el).selectByVisibleText(visibleText);
        } catch (Throwable ignored) {
            safeClick(el);
            el.sendKeys(visibleText);
            el.sendKeys(Keys.ENTER);
        }
    }

    /** Send ENTER to an element. */
    @Step("Press ENTER on element located by {locator}")
    protected void pressEnter(By locator) {
        WebElement el = wait.waitForElementVisible(locator);
        el.sendKeys(Keys.ENTER);
    }

    /** Send ESCAPE to the focused element. */
    @Step("Press ESCAPE")
    protected void pressEscape() {
        try { actions().sendKeys(Keys.ESCAPE).perform(); } catch (Throwable ignored) {}
    }

    // ======================================================================
    // Reads / predicates
    // ======================================================================

    @Step("Read text from element located by {locator}")
    protected String getText(By locator) {
        return wait.waitForElementVisible(locator).getText();
    }

    protected boolean isVisible(By locator) {
        try { return wait.waitForElementVisible(locator).isDisplayed(); }
        catch (TimeoutException e) { return false; }
    }

    protected boolean isPresent(By locator) {
        try { return !driver.findElements(locator).isEmpty(); }
        catch (Throwable t) { return false; }
    }

    protected boolean isClickable(By locator) {
        try { wait.waitForElementClickable(locator); return true; }
        catch (TimeoutException e) { return false; }
    }

    // ======================================================================
    // Screenshots
    // ======================================================================

    /** Saves a screenshot under ./screenshots/{name}.png (best-effort). */
    @Step("Take screenshot '{name}'")
    protected void takeScreenshot(String name) {
        File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        try {
            File dir = new File("screenshots");
            FileHandler.createDir(dir);
            FileHandler.copy(screenshot, new File(dir, name + ".png"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save screenshot: " + name, e);
        }
    }

    // ======================================================================
    // Wait wrappers (thin delegates to WaitUtils + a few extras)
    // ======================================================================

    protected WebElement waitForElementVisible(By locator) { return wait.waitForElementVisible(locator); }
    protected WebElement waitForElementClickable(By locator){ return wait.waitForElementClickable(locator); }
    protected boolean    waitForElementInvisible(By locator){ return wait.waitForElementInvisible(locator); }
    protected boolean    waitForUrlContains(String partial)  { return wait.waitForUrlContains(partial); }
    protected boolean    waitForTitleContains(String partial){ return wait.waitForTitleContains(partial); }

    protected void waitForAttributeToContain(By locator, String attribute, String value) {
        wait.until(ExpectedConditions.attributeContains(locator, attribute, value));
    }

    /** Best-effort “network idle”: waits for document.readyState === 'complete' and no visible loaders. */
    @Step("Wait for network idle (timeout = {timeout})")
    protected void waitForNetworkIdle(Duration timeout) {
        WebDriverWait w = new WebDriverWait(driver, timeout);
        try {
            w.until((ExpectedCondition<Boolean>) d ->
                    "complete".equals(js().executeScript("return document.readyState"))
            );
        } catch (Throwable ignored) {}
        try {
            w.until(ExpectedConditions.invisibilityOfElementLocated(POSSIBLE_LOADER));
        } catch (Throwable ignored) {}
    }

    // ======================================================================
    // Identity helpers
    // ======================================================================

    /**
     * Generic "isCurrentPage" check:
     *  - URL must contain the given fragment (if not null/empty)
     *  - At least one of the locators must be visible within a short timeout (default 3s)
     */
    public static boolean isCurrentPage(WebDriver driver, String urlFragment, By... mustHaveLocators) {
        return isCurrentPage(driver, urlFragment, Duration.ofSeconds(3), mustHaveLocators);
    }

    /**
     * Overload with explicit timeout.
     */
    public static boolean isCurrentPage(WebDriver driver, String urlFragment, Duration timeout, By... mustHaveLocators) {
        try {
            if (urlFragment != null && !urlFragment.isBlank()) {
                String url = driver.getCurrentUrl();
                if (url == null || !url.contains(urlFragment)) return false;
            }
            if (mustHaveLocators == null || mustHaveLocators.length == 0) return true;

            WebDriverWait w = new WebDriverWait(driver, timeout);
            for (By locator : mustHaveLocators) {
                try {
                    WebElement el = w.until(ExpectedConditions.visibilityOfElementLocated(locator));
                    if (el != null && el.isDisplayed()) return true;
                } catch (TimeoutException ignored) { /* try next */ }
            }
            return false;
        } catch (Throwable ignore) {
            return false;
        }
    }

    // ======================================================================
    // Small utilities
    // ======================================================================

    /** Escapes single quotes for XPaths. */
    protected static String esc(String s) { return s == null ? "" : s.replace("'", "\\'"); }

    /** Quick data-test helpers. */
    protected By byDataTest(String value)   { return By.cssSelector("[data-test='" + value + "']"); }
    protected By byDataTestId(String value) { return By.cssSelector("[data-testid='" + value + "']"); }

    /** Clear input via CMD/CTRL+A + Delete with JS fallback. */
    private void clearWithSelectAll(WebElement el) {
        try { el.sendKeys(Keys.chord(Keys.COMMAND, "a"), Keys.DELETE); } catch (Throwable ignored) {}
        try { el.sendKeys(Keys.chord(Keys.CONTROL,  "a"), Keys.DELETE); } catch (Throwable ignored) {}
        try {
            String current = String.valueOf(el.getAttribute("value"));
            if (current != null && !current.isEmpty()) {
                js().executeScript("arguments[0].value=''; arguments[0].dispatchEvent(new Event('input',{bubbles:true}));", el);
            }
        } catch (Throwable ignored) {}
    }

    // --- small helper to pull the first email-looking token from a row's text ---
    private String extractEmail(String text) {
        if (text == null) return "";
        Matcher m = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE).matcher(text);
        return m.find() ? m.group() : "";
    }



    /**
     * Each concrete page must implement its own "wait until loaded" behavior.
     * Returning `this` enables fluent chaining in page classes.
     */
    public abstract BasePage waitUntilLoaded();

    /**
     * Generic "isLoaded" check using the page-specific waitUntilLoaded().
     * If the wait passes, we consider the page loaded; if it throws, we return false.
     */
    public boolean isLoaded() {
        try {
            waitUntilLoaded();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }


}
