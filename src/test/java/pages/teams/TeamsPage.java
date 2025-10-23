package pages.teams;

import org.openqa.selenium.NoSuchElementException;
import pages.BasePage;

import io.qameta.allure.Step;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.BasePage;
import pages.Individuals.IndividualsPage;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;






public class TeamsPage extends BasePage {






    private final WebDriverWait wdw;

    public TeamsPage(WebDriver driver) {
        super(driver);
        this.wdw = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    // ===== Page + table =====

    private final By searchInput = By.xpath("//input[@placeholder='Search here' and not(@disabled)]");

    private final By tableRoot = By.cssSelector(".ant-table");
    private final By tableBody = By.cssSelector(".ant-table .ant-table-tbody");
    private final By tableRows = By.cssSelector(".ant-table .ant-table-tbody > tr.ant-table-row");
    private final By emptyState = By.cssSelector(".ant-table .ant-empty, .ant-empty-description, .ant-table-placeholder"); //

    // ===== Pagination (same pattern as Individuals) =====
    private final By pagination  = By.cssSelector(".ant-table-pagination");
    private final By nextPageLi  = By.cssSelector(".ant-table-pagination .ant-pagination-next");
    private final By prevPageLi  = By.cssSelector(".ant-table-pagination .ant-pagination-prev");
    private final By nextPageBtn = By.cssSelector(".ant-table-pagination .ant-pagination-next button");
    private final By pageItems   = By.cssSelector(".ant-table-pagination .ant-pagination-item");
    private final By totalText   = By.cssSelector(".ant-table-pagination .ant-pagination-total-text"); //

    // ===== Sort caret + option container (copied pattern) =====
    private final By sortCaretSvg = By.xpath(
            "(//p[starts-with(normalize-space(),'Sort by:')]/following-sibling::*[name()='svg'][1])[1]"
    );
    private final By sortOptionsContainer = By.xpath(
            "(//p[starts-with(normalize-space(),'Sort by:')]/following-sibling::*[name()='svg'][1]" +
                    "/following-sibling::div[1])[1]"
    ); //



    private final By firstColumn = By.xpath("//th[normalize-space()='Organization / Team name']");





    private By sortOptionByFullText(String fullText) {
        return By.xpath(".//p[normalize-space()='" + fullText + "']");
    }

    private String toOptionFullText(String label) {
        String s = label.trim().toLowerCase(Locale.ROOT);
        switch (s) {
            case "newest":
            case "date newest":
            case "date (newest)":
                return "Sort by: Newest";
            case "oldest":
            case "date oldest":
            case "date (oldest)":
                return "Sort by: Oldest";
            case "name (a–z)":
            case "name (a-z)":
            case "a–z":
            case "a-z":
                return "Sort by: A-Z";
            case "name (z–a)":
            case "name (z-a)":
            case "z–a":
            case "z-a":
                return "Sort by: Z-A";
            default:
                if (label.startsWith("Sort by:")) return label;
                throw new IllegalArgumentException("Unknown sort label: " + label);
        }
    } //

    // ===== Actions menu (same open-dropdown strategy) =====
    private static final By OPEN_MENU = By.xpath(
            "//*[" +
                    "contains(concat(' ', normalize-space(@class), ' '), ' ant-dropdown ') and " +
                    "(" +
                    "contains(concat(' ', normalize-space(@class), ' '), ' ant-dropdown-open ') or " +
                    "not(@hidden)" +
                    ")" +
                    "]"
    ); //

    // ===== Row cell helpers tuned to Teams table =====
    // Cols observed in your screenshot: [Team name] [Members] [Assessments] [Report] [Actions]
    private WebElement teamNameCellInRow(WebElement row) {
        // first column, often two-line (organization / team)
        return row.findElement(By.cssSelector("td:nth-of-type(1)"));
    }
    private WebElement membersCellInRow(WebElement row) {
        return row.findElement(By.cssSelector("td:nth-of-type(2)"));
    }
    private WebElement reportCellInRow(WebElement row) {
        return row.findElement(By.cssSelector("td:nth-of-type(4)"));
    }
    private WebElement kebabInRow(WebElement row) {
        return row.findElement(By.cssSelector("td:nth-of-type(5) .ant-dropdown-trigger"));
    }








    // HELPERS
    private void waitForTableSettled() {
        wdw.until(ExpectedConditions.presenceOfElementLocated(tableRoot));
        wdw.until(ExpectedConditions.presenceOfElementLocated(tableBody));
        // rows visible OR empty state visible
        wdw.until(d -> !d.findElements(tableRows).isEmpty() || !d.findElements(emptyState).isEmpty());
    }



    private String safeText(IndividualsPage.SupplierWithException<String> supplier) {
        try {
            return supplier.get();
        } catch (Throwable t){
            return "";
        }
    }



    @FunctionalInterface private interface SupplierWithException<T> {
        T get() throws Exception;
    }





    // ===== Page readiness & navigation =====
    @Step("Open Teams")
    public TeamsPage open(String baseUrl) {
        driver.navigate().to(baseUrl.replaceAll("/+$","") + "/dashboard/teams?ts=" + System.nanoTime());
        return waitUntilLoaded();
    }

    @Step("Wait until Teams page & table are visible")
    public TeamsPage waitUntilLoaded() {
        wait.waitForDocumentReady();
        wait.waitForLoadersToDisappear();
        wdw.until(ExpectedConditions.visibilityOfElementLocated(tableRoot));
        // Title sometimes loads later; tolerate either title or rows/empty state
        wdw.until(d -> isPresent(firstColumn) || !d.findElements(tableRows).isEmpty() || isPresent(emptyState));
        waitForTableSettled();
        return this;
    }

    // ===== Basics =====
    @Step("Search teams: {text}")
    public void search(String text) {
        WebElement input = wdw.until(ExpectedConditions.elementToBeClickable(searchInput));
        input.click();
        input.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        input.sendKeys(Keys.DELETE);
        input.sendKeys(text);
        input.sendKeys(Keys.ENTER);
        waitForTableSettled();
    }

    @Step("Open Sort menu")
    public void openSortMenu() {
        WebElement caret = wdw.until(ExpectedConditions.elementToBeClickable(sortCaretSvg));
        caret.click();
        wdw.until(ExpectedConditions.visibilityOfElementLocated(sortOptionsContainer));
    }

    @Step("Choose sort option: {label}")
    public void chooseSortOption(String label) {
        WebElement oldBody = null;
        int beforeCount = -1;
        try {
            oldBody = driver.findElement(tableBody);
            beforeCount = driver.findElements(tableRows).size();
        } catch (NoSuchElementException ignored) { }

        openSortMenu();
        WebElement container = wdw.until(ExpectedConditions.visibilityOfElementLocated(sortOptionsContainer));
        WebElement opt = wdw.until(ExpectedConditions.elementToBeClickable(container.findElement(sortOptionByFullText(toOptionFullText(label)))));
        opt.click();

        // Prefer tbody staleness, else row-count change, then settle (same approach you use)
        if (oldBody != null) {
            try { new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.stalenessOf(oldBody)); }
            catch (TimeoutException ignored) { /* fall through */ }
        }
        if (beforeCount >= 0) {
            try {
                int finalBeforeCount = beforeCount;
                new WebDriverWait(driver, Duration.ofSeconds(4)).until(d -> d.findElements(tableRows).size() != finalBeforeCount); }
            catch (TimeoutException ignored) { }
        }
        waitForTableSettled(); //
    }

    // ===== Row resolvers =====
    private String rowByTeamNameXpath(String teamName) {
        String t = teamName.replace("'", "\\'");
        return "(//tr[.//td[normalize-space()='" + t + "'] or .//td//*[normalize-space()='" + t + "']]" +
                " | //div[@role='row'][.//*[normalize-space()='" + t + "']])";
    }

    /** Finds a row on current page by team name (exact, case-insensitive). */
    public Optional<WebElement> findRowByTeamNameOnCurrentPage(String teamName) {
        for (WebElement row : driver.findElements(tableRows)) {
            String txt = safeText(() -> teamNameCellInRow(row).getText());
            if (teamName.equalsIgnoreCase(txt.trim())
                    || txt.toLowerCase(Locale.ROOT).contains(teamName.toLowerCase(Locale.ROOT))) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    /** Scans pages (using search if present) to find a row by team name. */
    public Optional<WebElement> findRowByTeamName(String teamName) {
        if (isPresent(searchInput)) {
            search(teamName);
            return findRowByTeamNameOnCurrentPage(teamName);
        }
        goToFirstPageIfPossible();
        do {
            Optional<WebElement> r = findRowByTeamNameOnCurrentPage(teamName);
            if (r.isPresent()) return r;
        } while (goToNextPageIfPossible());
        return Optional.empty();
    }

    // ===== Actions menu open/click (same robust strategy as Individuals) =====
    public boolean openActionsMenuForTeam(String teamName) {
        Optional<WebElement> rowOpt = findRowByTeamName(teamName);
        if (rowOpt.isEmpty()) return false;
        WebElement row = rowOpt.get();

        try { scrollToElement(row); } catch (Throwable ignore) {}
        new Actions(driver).moveToElement(row).pause(Duration.ofMillis(120)).perform();

        WebElement trigger = null;
        try {
            trigger = kebabInRow(row);
        } catch (NoSuchElementException ignored) {
            try {
                trigger = row.findElement(By.cssSelector("td:last-child .ant-dropdown-trigger, td:last-child [role='button'], td:last-child button"));
            } catch (NoSuchElementException ignored2) { trigger = null; }
        }
        if (trigger == null) return false;

        try {
            new Actions(driver).moveToElement(trigger).pause(Duration.ofMillis(80)).click(trigger).perform();
        } catch (Exception e) {
            try { ((JavascriptExecutor) driver).executeScript("arguments[0].click();", trigger); }
            catch (Throwable ignored) {}
        }
        return waitForMenuOpen();
    }

    /** True if a dropdown is visible/open (shared OPEN_MENU logic). */
    public boolean waitForMenuOpen() {
        try {
            wdw.until(ExpectedConditions.visibilityOfElementLocated(OPEN_MENU));
            return true;
        } catch (TimeoutException e) { return false; }
    }

    /** Clicks a menu item by its visible text in the currently open dropdown. */
    public void clickMenuItemInOpenMenu(String text) {
        By item = By.xpath("(" +
                "(//*[@role='menu'] | //*[contains(@class,'ant-dropdown')])" +
                "//*[@role='menuitem' or self::li or self::button or self::div or self::span]" +
                "[normalize-space()='" + text + "']" +
                ")[last()]");
        click(item);
    } // pattern mirrors your Individuals clickers

    // ===== Row actions (shortcuts you’ll likely need) =====
    /** Opens the team detail by clicking on the team name cell. */
    public void openTeamDetails(String teamName) {
        Optional<WebElement> row = findRowByTeamName(teamName);
        if (row.isEmpty()) throw new NoSuchElementException("Row not found for team: " + teamName);
        WebElement cell = teamNameCellInRow(row.get());
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", cell);
        try { cell.click(); } catch (Exception e) { ((JavascriptExecutor) driver).executeScript("arguments[0].click();", cell); }
    }

    /** Clicks the “Team Climate” button in the row (Report column). */
    public void openTeamClimate(String teamName) {
        Optional<WebElement> row = findRowByTeamName(teamName);
        if (row.isEmpty()) throw new NoSuchElementException("Row not found for team: " + teamName);
        WebElement cell = reportCellInRow(row.get());
        List<WebElement> linkish = cell.findElements(By.cssSelector("a, [role='link'], button"));
        if (linkish.isEmpty()) throw new NoSuchElementException("No Team Climate link/button in row: " + teamName);
        WebElement btn = linkish.get(0);
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", btn);
        try { ((JavascriptExecutor) driver).executeScript("arguments[0].setAttribute('target','_self');", btn); } catch (Exception ignored) {}
        try { btn.click(); } catch (Exception e) { ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn); }
    } // same click discipline as Individuals report openers

    /** Opens kebab → “Add members” (or “Invite”) and returns when the invite modal is visible. */
    public void openAddMembers(String teamName) {
        if (!openActionsMenuForTeam(teamName))
            throw new NoSuchElementException("Could not open actions menu for: " + teamName);
        // Accept either copy
        List<String> labels = List.of("Add members", "Invite", "Invite members");
        boolean clicked = false;
        for (String l : labels) {
            try { clickMenuItemInOpenMenu(l); clicked = true; break; } catch (Throwable ignore) {}
        }
        if (!clicked) throw new NoSuchElementException("No 'Add members/Invite' option for: " + teamName);
        waitForInviteModal();
    }

    // ===== Invite modal (for your duplicate-email tests) =====
    public WebElement waitForInviteModal() {
        By modalRoot = By.xpath("(" +
                "//*[contains(@class,'ant-modal')]" +
                "[not(contains(@style,'display: none'))]" +
                "[descendant::*[contains(@class,'ant-modal-content')]]" +
                ")[last()]");
        return wdw.until(ExpectedConditions.visibilityOfElementLocated(modalRoot));
    }

    public WebElement emailInputInInvite() {
        WebElement modal = waitForInviteModal();
        // generic email/text input in modal
        List<WebElement> cands = modal.findElements(By.xpath(
                ".//input[" +
                        " @type='email' or @type='text' or @type='search' or @inputmode='email' " +
                        " or contains(translate(@placeholder,'EMAIL','email'),'email')" +
                        "]"
        ));
        if (cands.isEmpty()) throw new NoSuchElementException("Invite email input not found");
        return cands.get(0);
    }

    public void addEmailInInvite(String email) {
        WebElement input = emailInputInInvite();
        input.click();
        input.sendKeys(email);
        input.sendKeys(Keys.ENTER);
        try { input.sendKeys(","); } catch (Exception ignored) {}
    }

    public String readInlineErrorInInvite() {
        WebElement modal = waitForInviteModal();
        List<WebElement> errs = modal.findElements(By.xpath(
                ".//*[self::div or self::span or self::small]" +
                        "[contains(translate(@class,'ERRORINVALIDHELP','errorinvalidhelp'),'error') or @type='error' or contains(.,'duplicate') or contains(.,'already')]" +
                        "[normalize-space()]"
        ));
        for (WebElement e : errs) if (e.isDisplayed()) return e.getText().trim();
        return "";
    }

    public boolean isInvitePrimaryDisabled() {
        WebElement modal = waitForInviteModal();
        List<WebElement> primaries = modal.findElements(By.xpath(".//button[normalize-space()='Continue' or normalize-space()='Confirm']"));
        if (primaries.isEmpty()) return true;
        WebElement btn = primaries.get(0);
        String cls = String.valueOf(btn.getAttribute("class"));
        boolean disabledAttr = btn.getAttribute("disabled") != null;
        boolean ariaDisabled = "true".equalsIgnoreCase(btn.getAttribute("aria-disabled"));
        boolean looksDisabled = cls.contains("disabled");
        return disabledAttr || ariaDisabled || looksDisabled;
    }

    // ===== Utilities =====
    public List<String> getTeamNamesOnCurrentPage() {
        return driver.findElements(tableRows)
                .stream()
                .map(r -> safeText(() -> teamNameCellInRow(r).getText()).trim())
                .filter(s -> !s.isBlank())
                .toList();
    }

    /** Extracts the “+N” part from the “View all” / members bubble text, if present. */
    public int getApproxMembersExtra(String teamName) {
        Optional<WebElement> row = findRowByTeamName(teamName);
        if (row.isEmpty()) return -1;
        String txt = membersCellInRow(row.get()).getText();
        if (txt == null) return -1;
        Matcher m = Pattern.compile("\\+(\\d+)").matcher(txt);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    public void goToFirstPageIfPossible() {
        try {
            if (isPresent(prevPageLi) && driver.findElement(prevPageLi).getAttribute("aria-disabled") == null) {
                while (driver.findElement(prevPageLi).getAttribute("aria-disabled") == null) {
                    driver.findElement(prevPageLi).click();
                    waitForTableSettled();
                }
            }
        } catch (Throwable ignored) {}
    }

    public boolean goToNextPageIfPossible() {
        try {
            WebElement li = driver.findElement(nextPageLi);
            if ("true".equals(li.getAttribute("aria-disabled"))) return false;
            driver.findElement(nextPageBtn).click();
            waitForTableSettled();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Stable per-row signature for quick order asserts. */
    public List<String> getRowOrderSignature() {
        List<WebElement> rows = driver.findElements(tableRows);
        List<String> sigs = new ArrayList<>(rows.size());
        for (WebElement row : rows) {
            String team = safeText(() -> teamNameCellInRow(row).getText());
            String members = safeText(() -> membersCellInRow(row).getText());
            sigs.add((team == null ? "" : team.trim()) + " || " + (members == null ? "" : members.trim()));
        }
        return sigs;
    }

    // Minor shared helpers
    protected boolean isPresent(By by) {
        return !driver.findElements(by).isEmpty();
    }

}
