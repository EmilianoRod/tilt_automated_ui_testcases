package pages.menuPages;

import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.BasePage;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class IndividualsPage extends BasePage {

    public IndividualsPage(WebDriver driver) {
        super(driver);
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    private final WebDriverWait wait;




    // ======= Locators (structure-based; avoid hashed classnames) =======

    // Header title “Manage individual client results”
    private final By pageTitle = By.xpath("//h1[contains(.,'Manage individual client results')]");

    // The enabled search box in the content header (there is a disabled one in the top bar)
    private final By searchInput = By.xpath("//section//input[@placeholder='Search here' and not(@disabled)]");

    // Ant table root + rows
    private final By tableRoot = By.cssSelector(".ant-table");
    private final By tableRows = By.cssSelector(".ant-table-tbody > tr.ant-table-row");


    // Email cell inside a given row
    private WebElement emailCellInRow(WebElement row) {
        return row.findElement(By.cssSelector("td:nth-of-type(2) h4"));
    }

    // Report cell (Pending or link)
    private WebElement reportCellInRow(WebElement row) {
        return row.findElement(By.cssSelector("td:nth-of-type(3)"));
    }

    // Kebab menu trigger
    private WebElement kebabInRow(WebElement row) {
        return row.findElement(By.cssSelector("td:nth-of-type(4) .ant-dropdown-trigger"));
    }

    // Pagination
    private final By pagination = By.cssSelector(".ant-table-pagination");
    private final By nextPageBtn = By.cssSelector(".ant-table-pagination .ant-pagination-next button");
    private final By prevPageBtn = By.cssSelector(".ant-table-pagination .ant-pagination-prev button");
    private final By pageItems = By.cssSelector(".ant-table-pagination .ant-pagination-item a");
    private final By totalText = By.cssSelector(".ant-table-pagination .ant-pagination-total-text"); // “Displaying 1-6 of 15”




// ======= Page readiness =======

    /** Waits for the Individuals page & table to be visible. */
    public IndividualsPage waitUntilLoaded() {
        try{
            wait.until(ExpectedConditions.visibilityOfElementLocated(pageTitle));
        }catch (TimeoutException ignore){
            throw new TimeoutException("Individuals page title not found.");
        }
        wait.until(ExpectedConditions.visibilityOfElementLocated(tableRoot));
        waitForTableStable();
        return this;
    }

    public boolean isLoaded() {
        try{
            waitUntilLoaded(); return true;
        } catch (TimeoutException e) {
            return false;
        }
    }


    // ======= Basic interactions =======

    /**
     * Types into the “Search here” box and gives the table a tick to re-render.
     */
    public void search(String text) {
        WebElement input = wait.until(ExpectedConditions.elementToBeClickable(searchInput));
        input.click();
        input.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        input.sendKeys(text);
        smallPause(250);
    }

    /** Returns true if any row on the *current page* matches the email exactly (case-insensitive). */
    public boolean isUserListedByEmailOnCurrentPage(String email) {
        for (WebElement row : driver.findElements(tableRows)) {
            String emailTxt = safeText(() -> emailCellInRow(row).getText());
            if (email.equalsIgnoreCase(emailTxt)) return true;
        }
        return false;
    }

    /** Looks for an email using the search box if available; otherwise paginates through the table. */
    public boolean isUserListedByEmail(String email) {
        if (isPresent(searchInput)) {
            search(email);
            return isUserListedByEmailOnCurrentPage(email);
        }
        goToFirstPageIfPossible();
        do {
            if (isUserListedByEmailOnCurrentPage(email)) return true;
        } while (goToNextPageIfPossible());
        return false;
    }

    /** Polls until the given email appears (default 20s). Throws if not found in time. */
    public void waitUntilUserInviteAppears(String email){
        waitUntilUserInviteAppears(email, Duration.ofSeconds(20));
    }

    public void waitUntilUserInviteAppears(String email, Duration timeout) {
        long end = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < end) {
            try {
                if (isUserListedByEmail(email)) return;
            } catch (StaleElementReferenceException ignored) {}
            smallPause(600);
        }
        throw new AssertionError("❌ User not listed in Individuals: " + email);
    }

    // ======= Row-level utilities =======

    /** Finds the row element for a given email on the *current page*. */
    public Optional<WebElement> findRowByEmailOnCurrentPage(String email) {
        for (WebElement row : driver.findElements(tableRows)) {
            String emailTxt = safeText(() -> emailCellInRow(row).getText());
            if (email.equalsIgnoreCase(emailTxt)) return Optional.of(row);
        }
        return Optional.empty();
    }

    /** Finds the row for an email (uses search if available; else paginates). */
    public Optional<WebElement> findRowByEmail(String email) {
        if (isPresent(searchInput)) {
            search(email);
            return findRowByEmailOnCurrentPage(email);
        }
        goToFirstPageIfPossible();
        do {
            Optional<WebElement> row = findRowByEmailOnCurrentPage(email);
            if (row.isPresent()) return row;
        } while (goToNextPageIfPossible());
        return Optional.empty();
    }

    /** Returns "Pending" or "Link:<href>" (or "NotFound") for the user's report column. */
    public String getReportStatusByEmail(String email) {
        Optional<WebElement> rowOpt = findRowByEmail(email);
        if (rowOpt.isEmpty()) return "NotFound";
        WebElement cell = reportCellInRow(rowOpt.get());
        List<WebElement> links = cell.findElements(By.tagName("a"));
        if (!links.isEmpty()) {
            String href = links.get(0).getAttribute("href");
            return "Link:" + (href == null ? "" : href);
        }
        String txt = cell.getText().trim();
        return txt.isEmpty() ? "Pending" : txt;
    }

    /** Clicks the report link (if available) in the same tab. Returns true if clicked. */
    public boolean openReportByEmail(String email) {
        Optional<WebElement> rowOpt = findRowByEmail(email);
        if (rowOpt.isEmpty()) return false;
        WebElement cell = reportCellInRow(rowOpt.get());
        List<WebElement> links = cell.findElements(By.tagName("a"));
        if (links.isEmpty()) return false;
        ((JavascriptExecutor) driver).executeScript("arguments[0].removeAttribute('target');", links.get(0));
        links.get(0).click();
        return true;
    }

    /** Opens the kebab/actions menu for a given email (returns false if the row isn’t found). */
    public boolean openActionsMenuFor(String email) {
        Optional<WebElement> rowOpt = findRowByEmail(email);
        if (rowOpt.isEmpty()) return false;
        WebElement kebab = kebabInRow(rowOpt.get());
        new Actions(driver).moveToElement(kebab).pause(Duration.ofMillis(80)).click(kebab).perform();
        smallPause(200);
        return true;
    }

    /** Clicks an item in the open actions menu by its visible text. */
    public boolean clickActionInMenu(String actionText) {
        By menuItem = By.xpath("//div[contains(@class,'ant-dropdown')]//li[normalize-space()='" + actionText + "']");
        try {
            WebElement item = wait.until(ExpectedConditions.elementToBeClickable(menuItem));
            item.click();
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    // ======= Pagination helpers =======

    public boolean goToNextPageIfPossible() {
        if (!isPresent(pagination)) return false;
        WebElement btn = driver.findElement(nextPageBtn);
        if (btn.getAttribute("disabled") != null) return false;
        btn.click();
        waitForTableStable();
        return true;
    }

    public void goToFirstPageIfPossible() {
        // if pagination block not present, bail
        if (!isPresent(pagination)) return;

        // if already on page 1, return
        WebElement firstPage = driver.findElements(pageItems).get(0);
        if (firstPage.getAttribute("aria-current") != null) {
            return; // already on first page
        }

        // click the first page link
        firstPage.click();
        waitForTableStable();
    }



    public void goToPage(int pageNumber) {
        List<WebElement> pages = driver.findElements(pageItems);
        for (WebElement p : pages) {
            if (p.getText().trim().equals(String.valueOf(pageNumber))) {
                p.click();
                waitForTableStable();
                return;
            }
        }
        throw new AssertionError("❌ Page number " + pageNumber + " not found in pagination");
    }



    /** Parses total count from “Displaying 1-6 of 15”. Returns -1 if missing. */
    public int getTotalCount() {
        if (!isPresent(totalText)) return -1;
        String text = driver.findElement(totalText).getText();
        String digits = text.replaceAll(".*of\\s+(\\d+).*", "$1");
        try { return Integer.parseInt(digits); } catch (Exception ignore) { return -1; }
    }

    // ======= Utilities =======

    private boolean isPresent(By by) {
        return !driver.findElements(by).isEmpty();
    }

    private void waitForTableStable() {
        wait.until(ExpectedConditions.presenceOfElementLocated(tableRoot));
        smallPause(150);
    }

    private void smallPause(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    private String safeText(SupplierWithException<String> supplier) {
        try { return supplier.get(); } catch (Throwable t) { return ""; }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> { T get() throws Exception; }

}
