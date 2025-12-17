package pages.teams;

import org.openqa.selenium.NoSuchElementException;
import org.testng.SkipException;
import pages.BasePage;

import io.qameta.allure.Step;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.BasePage;
import pages.Individuals.IndividualsPage;
import pages.reports.ReportSummaryPage;

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

    private final By pageSizeSelect = By.xpath("//*/ul/li/div[@aria-label='Page Size']/div[1]");
    private final By pageSizeOption100 = By.xpath("//*[normalize-space()='100 / page' or normalize-space()='100 / page ']");
    private final By pagingLabel = By.xpath("//span[@class='ant-select-selection-item']");


    // ===== Sort caret + option container (copied pattern) =====
    private final By sortCaretSvg = By.xpath(
            "(//p[starts-with(normalize-space(),'Sort by:')]/following-sibling::*[name()='svg'][1])[1]"
    );
    private final By sortOptionsContainer = By.xpath(
            "(//p[starts-with(normalize-space(),'Sort by:')]/following-sibling::*[name()='svg'][1]" +
                    "/following-sibling::div[1])[1]"
    ); //

    private final By firstColumn = By.xpath("//th[normalize-space()='Organization / Team name']");

    private By nextPageArrow = By.xpath(
            "//li[@title='Next Page']//button[@type='button']"
    );

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




    @FunctionalInterface private interface SupplierWithException<T> {
        T get() throws Exception;
    }




    public boolean isLoaded() {
        try {
            waitUntilLoaded();
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }


    /** True if this row has a Team True Tilt Aggregate report link/button in the Report column. */
    private boolean rowHasCompletedTrueTiltAggregate(WebElement row) {
        try {
            WebElement cell = reportCellInRow(row);

            // Quick text heuristic: anything with TTP in the badge / label
            String text = cell.getText();
            if (text != null && text.toUpperCase(Locale.ROOT).contains("TTP")) {
                return true;
            }

            // Fallback: any link whose href looks like a TTP / team aggregate report
            for (WebElement link : cell.findElements(By.cssSelector("a[href]"))) {
                String href = link.getAttribute("href");
                if (href != null && href.contains("/assess/ttp/")) {
                    return true;
                }
            }
        } catch (NoSuchElementException ignored) {
            // no report cell / link in this row
        }
        return false;
    }


    /**
     * Convenience alias for test readability – at Teams-page level.
     * It just tries to locate at least one team with TTP aggregate using the method above.
     */
    public boolean hasAnyCompletedTrueTiltAggregateReport() throws InterruptedException {
        try {
            // Try to find one, but restore state even if we don't keep the page
            TeamDetailsPage details = openFirstTeamWithCompletedAggregateReport();
            // We found at least one ⇒ return true, but go back so callers can decide navigation.
            driver.navigate().back();
            waitUntilLoaded();
            return true;
        } catch (org.testng.SkipException e) {
            return false;
        }
    }



    /** Opens the first Team True Tilt Aggregate report found and returns the summary page. */
    public ReportSummaryPage openFirstCompletedTrueTiltAggregateReport() {
        goToFirstPageIfPossible();

        do {
            List<WebElement> rows = driver.findElements(tableRows);
            for (WebElement row : rows) {
                if (!rowHasCompletedTrueTiltAggregate(row)) continue;

                WebElement cell = reportCellInRow(row);
                WebElement linkToClick = null;

                // Prefer a link whose text contains TTP
                for (WebElement link : cell.findElements(By.cssSelector("a[href]"))) {
                    String txt = link.getText();
                    String href = link.getAttribute("href");
                    if ((txt != null && txt.toUpperCase(Locale.ROOT).contains("TTP")) ||
                            (href != null && href.contains("/assess/ttp/"))) {
                        linkToClick = link;
                        break;
                    }
                }

                if (linkToClick == null) {
                    throw new NoSuchElementException("Could not locate Team True Tilt Aggregate link in report cell.");
                }

                // Try to force same-tab navigation (sometimes target='_blank')
                try {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].setAttribute('target','_self');", linkToClick);
                } catch (Exception ignored) {}

                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", linkToClick);

                try {
                    linkToClick.click();
                } catch (Exception e) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", linkToClick);
                }

                switchToNewestTab(); // just in case a new tab still opened

                return new ReportSummaryPage(driver).waitUntilLoaded();
            }
        } while (goToNextPageIfPossible());

        throw new NoSuchElementException("No Team True Tilt Aggregate report link found in Teams table.");
    }




    // ===== Page readiness & navigation =====
    @Step("Open Teams")
    public TeamsPage open(String baseUrl) {
        driver.navigate().to(baseUrl.replaceAll("/+$","") + "/dashboard/teams?ts=" + System.nanoTime());
        return waitUntilLoaded();
    }

    @Step("Wait until Teams page & table are visible")
    @Override
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


    /**
     * Opens Team Details by clicking the team link in the row at 1-based index rowNumber.
     * Example: openTeamAtRow(1) → first team in the table.
     */
    public TeamDetailsPage openTeamAtRow(int rowNumber) {
        // normalize
        if (rowNumber < 1) {
            throw new IllegalArgumentException("Row number must be >= 1, received: " + rowNumber);
        }

        // Always start from first page for deterministic behavior
        goToFirstPageIfPossible();
        waitForTableSettled();

        List<WebElement> rows = driver.findElements(tableRows);
        if (rows.isEmpty()) {
            throw new SkipException("No rows found in Teams table.");
        }
        if (rowNumber > rows.size()) {
            throw new SkipException("Row " + rowNumber + " does not exist. Only " + rows.size() + " rows found.");
        }

        WebElement row = rows.get(rowNumber - 1);
        WebElement link = teamLinkInRow(row);   // existing helper: a[href*='/dashboard/teams/']

        try { scrollToElement(link); } catch (Throwable ignored) {}

        try {
            ((JavascriptExecutor) driver)
                    .executeScript("arguments[0].scrollIntoView({block:'center'});", link);
        } catch (Exception ignored) {}

        // force same tab
        try {
            ((JavascriptExecutor) driver)
                    .executeScript("arguments[0].setAttribute('target','_self');", link);
        } catch (Exception ignored) {}

        try {
            link.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver)
                    .executeScript("arguments[0].click();", link);
        }

        return new TeamDetailsPage(driver).waitUntilLoaded();
    }



   // ===== Row actions (shortcuts you’ll likely need) =====
  /** Opens the team detail by clicking the real "View all" / team link in the row. */
    public void openTeamDetails(String teamName) {
        Duration timeout = Duration.ofSeconds(45); // adjust if needed
        WebDriverWait wait = new WebDriverWait(driver, timeout);

        // Wait until the row for this team actually exists
        WebElement row = wait.until(d -> {
            Optional<WebElement> candidate;

            if (isPresent(searchInput)) {
                // If search is on the page, try current page first
                candidate = findRowByTeamNameOnCurrentPage(teamName);
                if (!candidate.isPresent()) {
                    candidate = findRowByTeamName(teamName); // full scan (pagination, etc.)
                }
            } else {
                candidate = findRowByTeamName(teamName);
            }

            return candidate.orElse(null);  // null → WebDriverWait keeps polling
        });

        if (row == null) {
            throw new TimeoutException("Timed out waiting for team row: " + teamName);
        }

        WebElement link = teamLinkInRow(row); // <-- the "View all" / team link

        try {
            scrollToElement(link);
        } catch (Throwable ignored) {}

        try {
            ((JavascriptExecutor) driver)
                    .executeScript("arguments[0].scrollIntoView({block:'center'});", link);
        } catch (Exception ignored) {}

        // force same tab, just in case
        try {
            ((JavascriptExecutor) driver)
                    .executeScript("arguments[0].setAttribute('target','_self');", link);
        } catch (Exception ignored) {}

        try {
            link.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", link);
        }
    }


//    public void openTeamDetails(String teamName) {
//        Optional<WebElement> row = findRowByTeamName(teamName);
//        if (row.isEmpty()) throw new NoSuchElementException("Row not found for team: " + teamName);
//        WebElement cell = teamNameCellInRow(row.get());
//        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", cell);
//        try { cell.click(); } catch (Exception e) { ((JavascriptExecutor) driver).executeScript("arguments[0].click();", cell); }
//    }

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
            if (!isPresent(prevPageLi)) return;

            int safety = 0;
            while (true) {
                WebElement li = driver.findElement(prevPageLi);
                String aria = li.getAttribute("aria-disabled");
                String cls  = String.valueOf(li.getAttribute("class"));

                boolean disabled = "true".equalsIgnoreCase(aria)
                        || cls.contains("ant-pagination-disabled");

                if (disabled) break;

                // Click the <button> if present; fall back to li.click()
                try {
                    WebElement btn = li.findElement(By.tagName("button"));
                    btn.click();
                } catch (NoSuchElementException e) {
                    li.click();
                }

                waitForTableSettled();

                if (++safety > 20) {
                    System.out.println("[TeamsPage] goToFirstPageIfPossible hit safety limit (20 clicks)");
                    break;
                }
            }
        } catch (Throwable t) {
            System.out.println("[TeamsPage] goToFirstPageIfPossible failed: " + t);
        }
    }


    public boolean goToNextPageIfPossible() {
        try {
            if (!isPresent(nextPageLi)) return false;

            WebElement li = driver.findElement(nextPageLi);
            String aria = li.getAttribute("aria-disabled");
            String cls  = String.valueOf(li.getAttribute("class"));

            boolean disabled = "true".equalsIgnoreCase(aria)
                    || cls.contains("ant-pagination-disabled");

            if (disabled) {
                return false;
            }

            // Click the button inside, or fallback
            try {
                WebElement btn = driver.findElement(nextPageBtn);
                btn.click();
            } catch (NoSuchElementException e) {
                li.click();
            }

            waitForTableSettled();
            return true;
        } catch (Throwable t) {
            System.out.println("[TeamsPage] goToNextPageIfPossible exception: " + t);
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




    /** Best-effort: switch to the newest browser tab/window if multiple are open. */
    private void switchToNewestTab() {
        try {
            Set<String> handles = driver.getWindowHandles();
            if (handles.size() <= 1) return;

            String current = driver.getWindowHandle();
            String newest = null;
            for (String h : handles) {
                newest = h; // last in iteration
            }
            if (newest != null && !Objects.equals(current, newest)) {
                driver.switchTo().window(newest);
            }
        } catch (Throwable ignored) {
            // If anything goes wrong, stay on current tab.
        }
    }


//    /**
//     * Opens Team Details for the first team (across pages) that has at least one
//     * TTP aggregate report. If none is found, throws SkipException so the smoke
//     * test is SKIPPED instead of FAILED.
//     */

    public TeamDetailsPage openFirstTeamWithCompletedAggregateReport() {
        goToFirstPageIfPossible();

        waitUntilLoaded();
        waitForTableSettled();

        // ✅ Reduce pages
        setPageSizeTo100IfPossible();

        int maxPages = 10; // with 100/page you’ll likely need very few
        String teamsUrl = driver.getCurrentUrl();

        for (int page = 1; page <= maxPages; page++) {
            waitUntilLoaded();
            waitForTableSettled();

            List<WebElement> rows = driver.findElements(tableRows);
            if (rows.isEmpty()) {
                System.out.println("[TeamsScan] page=" + page + " rows=0");
            } else {
                System.out.println("[TeamsScan] page=" + page + " rows=" + rows.size());
            }

            // ✅ collect hrefs first (stable)
            List<String> teamUrls = new ArrayList<>();
            for (WebElement row : rows) {
                WebElement link = teamLinkInRow(row);
                String href = link.getAttribute("href");
                if (href != null && !href.isBlank()) teamUrls.add(href);
            }

            // ✅ visit each team directly
            for (String url : teamUrls) {
                driver.get(url);

                TeamDetailsPage details = new TeamDetailsPage(driver).waitUntilLoaded();
                if (details.hasCompletedTrueTiltAggregate()) {
                    return details;
                }
            }

            // go back to teams list cleanly
            driver.get(teamsUrl);
            waitUntilLoaded();
            waitForTableSettled();

            // next page
            if (!goToNextPageArrowIfPossible()) {
                break;
            }
        }

        throw new SkipException("⚠ No team found with completed Team True Tilt Aggregate reports.");
    }




    public boolean goToNextPageArrowIfPossible() {
        try {
            WebElement next = driver.findElement(nextPageArrow);

            if (!next.isDisplayed() || !next.isEnabled()) {
                System.out.println("[Pagination] Next page disabled or not visible");
                return false;
            }

            scrollIntoView(next);
            next.click();

            waitUntilLoaded();
            waitForTableSettled();

            System.out.println("[Pagination] Moved to next page");
            return true;

        } catch (NoSuchElementException e) {
            System.out.println("[Pagination] Next page arrow not found");
            return false;
        }
    }


    private void forceSameTab(WebElement el) {
        try {
            ((JavascriptExecutor) driver)
                    .executeScript("arguments[0].setAttribute('target','_self');", el);
        } catch (Exception ignored) {}
    }


    public void goToPage(int pageNumber) {
        final String target = String.valueOf(pageNumber);
        final WebDriverWait fastWait = new WebDriverWait(driver, java.time.Duration.ofSeconds(3));
        final WebDriverWait slowWait = new WebDriverWait(driver, java.time.Duration.ofSeconds(6));

        // 0) Already on target? no-op (prevents dead waits)
        for (WebElement li : driver.findElements(pageItems)) {
            String text = li.getText().trim();
            if (!text.matches("\\d+")) continue; // ignore arrows/ellipsis
            if (target.equals(text)) {
                String cls = String.valueOf(li.getAttribute("class"));
                if (cls.contains("ant-pagination-item-active")) return;
            }
        }

        // Snapshot cheap "content changed" signal BEFORE clicking (footer)
        String beforeFooter = getDisplayingRangeTextSafe();

        // Also snapshot first row for staleness fallback
        WebElement oldFirstRow = null;
        try { oldFirstRow = driver.findElement(By.cssSelector("table tbody tr")); } catch (Exception ignored) {}

        // 1) Find target LI (numeric) and click tolerant (a/span/button)
        WebElement targetLi = driver.findElements(pageItems).stream()
                .filter(li -> {
                    String txt = li.getText().trim();
                    return txt.matches("\\d+") && target.equals(txt);
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("❌ Page number " + pageNumber + " not found in pagination"));

        WebElement clickEl = !targetLi.findElements(By.tagName("a")).isEmpty()
                ? targetLi.findElement(By.tagName("a"))
                : (!targetLi.findElements(By.tagName("span")).isEmpty()
                ? targetLi.findElement(By.tagName("span"))
                : targetLi);

        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", clickEl);
        try { clickEl.click(); } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", clickEl);
        }

        // 2) Wait A: active page class flips to target (deterministic & fast)
        fastWait.until(d -> isActivePage(target));

        // 3) Wait B: content actually changed (footer preferred, row staleness as fallback)
        boolean footerChanged = false;
        try {
            footerChanged = fastWait.until(d -> {
                String after = getDisplayingRangeTextSafe();
                return !after.equals(beforeFooter) && !after.isEmpty();
            });
        } catch (org.openqa.selenium.TimeoutException ignored) {
            // ignore and try staleness
        }

        if (!footerChanged && oldFirstRow != null) {
            try {
                slowWait.until(org.openqa.selenium.support.ui.ExpectedConditions.stalenessOf(oldFirstRow));
            } catch (org.openqa.selenium.TimeoutException ignored) {
                // Some AntD tables recycle <tr>; if both signals fail, accept active-page change only.
            }
        }

        // Do NOT call a generic waitForTableRefreshed() here — it’s the source of the long timeouts.
    }

    private String getDisplayingRangeTextSafe() {
        try { return driver.findElement(totalText).getText().trim(); }
        catch (Exception e) { return ""; }
    }

    private boolean isActivePage(String target) {
        for (WebElement li : driver.findElements(pageItems)) {
            String txt = li.getText().trim();
            if (!txt.matches("\\d+")) continue;
            if (target.equals(txt)) {
                String cls = String.valueOf(li.getAttribute("class"));
                return cls.contains("ant-pagination-item-active");
            }
        }
        return false;
    }


    /** In Teams table, the navigation to Team Details is the "View all" link in the Members column. */
    private WebElement teamLinkInRow(WebElement row) {
        // safest: any anchor that goes to /dashboard/teams/{id}
        return row.findElement(By.cssSelector("a[href*='/dashboard/teams/']"));
    }


    public TeamClimatePage openTeamClimateDetails(String teamPathOrName) {
        // Example input: "Org B / Validation Merge Test / Analytics"
        // or "Org B / Validation Merge Test"
        // or just "Validation Merge Test"
        String raw = Objects.requireNonNull(teamPathOrName, "teamPathOrName must not be null").trim();

        String orgSearchTerm;
        String teamFragment;

        String[] parts = raw.split("/");
        if (parts.length == 1) {
            // Only one chunk given: use it BOTH as org search term and row fragment
            orgSearchTerm = parts[0].trim();
            teamFragment  = parts[0].trim();
        } else {
            // At least "Org B / Validation Merge Test"
            orgSearchTerm = parts[0].trim();                 // "Org B"
            teamFragment  = parts[parts.length - 2].trim();  // "Validation Merge Test"
        }

        // 1) Filter table by ORGANIZATION (this is what the search bar understands)
        search(orgSearchTerm);

        // 2) On the filtered page, find the row whose first column text contains the team fragment
        Optional<WebElement> rowOpt = findRowByTeamNameOnCurrentPage(teamFragment);
        if (rowOpt.isEmpty()) {
            throw new SkipException("Large team row not found for path: '" + raw +
                    "' (orgSearchTerm='" + orgSearchTerm + "', teamFragment='" + teamFragment + "')");
        }
        WebElement row = rowOpt.get();

        // 3) From that row, click the Team Climate button/link in the Report column
        WebElement cell = reportCellInRow(row);
        List<WebElement> linkish = cell.findElements(By.cssSelector("a, [role='link'], button"));
        if (linkish.isEmpty()) {
            throw new NoSuchElementException("No Team Climate link/button in row for team path: " + raw);
        }

        WebElement btn = linkish.get(0);
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", btn);
        try {
            ((JavascriptExecutor) driver).executeScript("arguments[0].setAttribute('target','_self');", btn);
        } catch (Exception ignored) {}

        try {
            btn.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
        }

        // 4) Now we are on /dashboard/teams/{id}/report → handled by TeamClimatePage
        return new TeamClimatePage(driver).waitUntilLoaded();
    }




    public void setPageSizeTo100IfPossible() {
        try {
            String before = safeText(() -> driver.findElement(pagingLabel).getText()).trim();

            WebElement dd = driver.findElement(pageSizeSelect);
            scrollIntoView(dd);
            safeClick(dd);

            WebElement opt100 = new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.elementToBeClickable(pageSizeOption100));
            safeClick(opt100);

            // wait for table/paging to update
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(d -> {
                        String now = safeText(() -> driver.findElement(pagingLabel).getText()).trim();
                        return !now.equals(before);
                    });

            waitForTableSettled();
            System.out.println("[TeamsPageSize] set to 100 / page");

        } catch (Throwable t) {
            System.out.println("[TeamsPageSize] could not set 100/page: " + t.getMessage());
            // don’t fail the smoke for this
        }
    }




}
