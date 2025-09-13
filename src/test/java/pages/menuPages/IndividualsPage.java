package pages.menuPages;

import io.qameta.allure.Step;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.BasePage;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class IndividualsPage extends BasePage {


    // Selenium wait (renamed to avoid shadowing BasePage.wait)
    private final WebDriverWait wdw;

    public IndividualsPage(WebDriver driver) {
        super(driver);
        this.wdw = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    // ======= Locators =======
    private final By pageTitle   = By.xpath(
            "//h1[contains(normalize-space(.),'Manage individual client results')]"
                    + " | //h1[contains(normalize-space(.),'Individuals')]"
    );
    private final By searchInput = By.xpath("//section//input[@placeholder='Search here' and not(@disabled)]");

    private final By tableRoot = By.cssSelector(".ant-table");
    private final By tableBody = By.cssSelector(".ant-table .ant-table-tbody");
    private final By tableRows = By.cssSelector(".ant-table .ant-table-tbody > tr.ant-table-row");

    private final By pagination  = By.cssSelector(".ant-table-pagination");
    private final By nextPageLi  = By.cssSelector(".ant-table-pagination .ant-pagination-next");
    private final By prevPageLi  = By.cssSelector(".ant-table-pagination .ant-pagination-prev");
    private final By nextPageBtn = By.cssSelector(".ant-table-pagination .ant-pagination-next button");
    private final By pageItems   = By.cssSelector(".ant-table-pagination .ant-pagination-item");
    private final By totalText   = By.cssSelector(".ant-table-pagination .ant-pagination-total-text");

    private WebElement emailCellInRow(WebElement row){ return row.findElement(By.cssSelector("td:nth-of-type(2) h4")); }
    private WebElement reportCellInRow(WebElement row){ return row.findElement(By.cssSelector("td:nth-of-type(3)")); }
    private WebElement kebabInRow(WebElement row)     { return row.findElement(By.cssSelector("td:nth-of-type(4) .ant-dropdown-trigger")); }

    private By emailCellAny(String emailLower) {
        return By.xpath(
                "((//div[contains(@class,'ant-table')])[1]//tbody)[1]" +
                        "//*[self::td or self::div or self::span or self::a or self::h4]" +
                        "[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'" + emailLower + "')]"
        );
    }

    // ======= Page readiness =======

    @Step("Wait until Individuals page & table are visible")
    public IndividualsPage waitUntilLoaded() {
        wait.waitForDocumentReady();
        wait.waitForLoadersToDisappear();
        wdw.until(ExpectedConditions.visibilityOfElementLocated(pageTitle));
        wdw.until(ExpectedConditions.visibilityOfElementLocated(tableRoot));
        waitForTableSettled();
        return this;
    }

    public boolean isLoaded() {
        try { waitUntilLoaded(); return true; } catch (TimeoutException e) { return false; }
    }

    // ======= Basic interactions =======

    @Step("Search for: {text}")
    public void search(String text) {
        WebElement input = wdw.until(ExpectedConditions.elementToBeClickable(searchInput));

        // clear cross-platform
        try { input.sendKeys(Keys.chord(Keys.COMMAND, "a")); input.sendKeys(Keys.DELETE); } catch (Exception ignored) {}
        try { input.sendKeys(Keys.chord(Keys.CONTROL,  "a")); input.sendKeys(Keys.DELETE); } catch (Exception ignored) {}

        // snapshot before typing (Ant sometimes updates in place)
        WebElement oldBody = null;
        try { oldBody = driver.findElement(tableBody); } catch (NoSuchElementException ignored) {}
        int before = driver.findElements(tableRows).size();

        input.sendKeys(text);
        input.sendKeys(Keys.ENTER);

        // accept any of: tbody staleness OR row-count change
        try {
            if (oldBody != null) new WebDriverWait(driver, Duration.ofSeconds(8))
                    .until(ExpectedConditions.stalenessOf(oldBody));
        } catch (TimeoutException ignored) { }
        try {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(d -> driver.findElements(tableRows).size() != before);
        } catch (TimeoutException ignored) { }

        waitForTableSettled();
    }


    public boolean isUserListedByEmailOnCurrentPage(String email) {
        for (WebElement row : driver.findElements(tableRows)) {
            String emailTxt = safeText(() -> emailCellInRow(row).getText());
            if (email.equalsIgnoreCase(emailTxt)) return true;
        }
        return false;
    }

    @Step("Check if user is listed by email (any page): {email}")
    public boolean isUserListedByEmail(String email) {
        // only use the page search if it probably searches by email (ours does NOT)
        if (isPresent(searchInput) && !looksLikeEmail(email)) {
            search(email); // name or fragment
            return isUserListedByEmailOnCurrentPage(email);
        }
        // email path: skip search, scan/paginate
        goToFirstPageIfPossible();
        do {
            if (isUserListedByEmailOnCurrentPage(email)) return true;
        } while (goToNextPageIfPossible());
        return false;
    }


    @Step("Wait until user invite appears: {email}")
    public void waitUntilUserInviteAppears(String email) {
        waitUntilUserInviteAppears(email, Duration.ofSeconds(20));
    }

    public void waitUntilUserInviteAppears(String email, Duration timeout) {
        new WebDriverWait(driver, timeout)
                .until(d -> {
                    try { return isUserListedByEmail(email); }
                    catch (StaleElementReferenceException ignored) { return false; }
                });
    }

    // ======= Row-level utilities =======

    public Optional<WebElement> findRowByEmailOnCurrentPage(String email) {
        for (WebElement row : driver.findElements(tableRows)) {
            String emailTxt = safeText(() -> emailCellInRow(row).getText());
            if (email.equalsIgnoreCase(emailTxt)) return Optional.of(row);
        }
        return Optional.empty();
    }



    public Optional<WebElement> findRowByEmail(String email) {
        if (isPresent(searchInput) && !looksLikeEmail(email)) {
            search(email); // name fragment
            return findRowByEmailOnCurrentPage(email);
        }
        // email path: skip search, scan/paginate
        goToFirstPageIfPossible();
        do {
            Optional<WebElement> row = findRowByEmailOnCurrentPage(email);
            if (row.isPresent()) return row;
        } while (goToNextPageIfPossible());
        return Optional.empty();
    }




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

    public boolean openActionsMenuFor(String email) {
        Optional<WebElement> rowOpt = findRowByEmail(email);
        if (rowOpt.isEmpty()) return false;
        WebElement kebab = kebabInRow(rowOpt.get());
        new Actions(driver).moveToElement(kebab).pause(Duration.ofMillis(80)).click(kebab).perform();
        waitForMenuOpen();
        return true;
    }

    public boolean clickActionInMenu(String actionText) {
        By menuItem = By.xpath("//div[contains(@class,'ant-dropdown') and contains(@class,'ant-dropdown-open')]//li[normalize-space()='" + actionText + "']");
        try {
            WebElement item = wdw.until(ExpectedConditions.elementToBeClickable(menuItem));
            item.click();
            waitForTableSettled();
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    // ======= Pagination helpers =======

    public boolean goToNextPageIfPossible() {
        if (!isPresent(pagination)) return false;
        WebElement li = driver.findElement(nextPageLi);
        String cls = li.getAttribute("class");
        if (cls != null && cls.contains("ant-pagination-disabled")) return false;
        safeClick(nextPageBtn);     // BasePage.safeClick(By)
        waitForTableRefreshed();
        return true;
    }

    public void goToFirstPageIfPossible() {
        if (!isPresent(pagination)) return;
        List<WebElement> pages = driver.findElements(pageItems);
        if (pages.isEmpty()) return;

        WebElement firstLi = pages.get(0);
        String cls = firstLi.getAttribute("class");
        if (cls != null && cls.contains("ant-pagination-item-active")) return;

        WebElement link = firstLi.findElement(By.tagName("a"));
        link.click();
        waitForTableRefreshed();
    }

    public void goToPage(int pageNumber) {
        for (WebElement li : driver.findElements(pageItems)) {
            WebElement a = li.findElement(By.tagName("a"));
            if (a.getText().trim().equals(String.valueOf(pageNumber))) {
                a.click();
                waitForTableRefreshed();
                return;
            }
        }
        throw new AssertionError("❌ Page number " + pageNumber + " not found in pagination");
    }

    public int getTotalCount() {
        if (!isPresent(totalText)) return -1;
        String text = driver.findElement(totalText).getText();
        String digits = text.replaceAll(".*of\\s+(\\d+).*", "$1");
        try { return Integer.parseInt(digits); } catch (Exception ignore) { return -1; }
    }

    // ======= Utilities =======

    private boolean isPresent(By by) { return !driver.findElements(by).isEmpty(); }


    private boolean looksLikeEmail(String s) {
        return s != null && s.contains("@");
    }


    private void waitForMenuOpen() {
        By openMenu = By.cssSelector(".ant-dropdown.ant-dropdown-open, .ant-dropdown:not([hidden])");
        try { new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.presenceOfElementLocated(openMenu)); }
        catch (Exception ignored) { }
    }

    private void waitForTableSettled() {
        wdw.until(ExpectedConditions.presenceOfElementLocated(tableRoot));
        wdw.until(ExpectedConditions.presenceOfElementLocated(tableBody));
    }

    private void waitForTableRefreshed() {
        WebElement oldBody = null;
        try { oldBody = driver.findElement(tableBody); } catch (NoSuchElementException ignored) { }
        if (oldBody != null) {
            try {
                new WebDriverWait(driver, Duration.ofSeconds(10))
                        .until(ExpectedConditions.stalenessOf(oldBody));
            } catch (TimeoutException ignored) {
                int before = driver.findElements(tableRows).size();
                new WebDriverWait(driver, Duration.ofSeconds(5))
                        .until(d -> driver.findElements(tableRows).size() != before);
            }
        }
        waitForTableSettled();
    }

    private String safeText(SupplierWithException<String> supplier) {
        try { return supplier.get(); } catch (Throwable t) { return ""; }
    }
    @FunctionalInterface private interface SupplierWithException<T> { T get() throws Exception; }



    // ======= Utilities FOR DEBUGGING =======
    public int debugCountRows() {
        List<WebElement> rows = driver.findElements(By.cssSelector("table tbody tr"));
        return rows.size();
    }

    public List<String> debugEmailsSeen() {
        return driver.findElements(By.cssSelector("table tbody tr"))
                .stream()
                .map(tr -> tr.getText())
                .collect(Collectors.toList());
    }
}
