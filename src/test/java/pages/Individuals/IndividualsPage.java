package pages.Individuals;

import Utils.Config;
import api.ApiConfig;
import api.BackendApi;
import io.qameta.allure.Step;
import okhttp3.ResponseBody;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import pages.BasePage;
import pages.reports.ReportSummaryPage;
import retrofit2.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

public class IndividualsPage extends BasePage {

    // Selenium wait (renamed to avoid shadowing BasePage.wait)
    private final WebDriverWait wdw;

    public IndividualsPage(WebDriver driver) {
        super(driver);
        this.wdw = new WebDriverWait(driver, Duration.ofSeconds(30));
    }

    // ======= Locators =======
    private final By pageTitle   = By.xpath(
            "//h1[contains(normalize-space(.),'Manage individual client results')]"
                    + " | //h1[contains(normalize-space(.),'Individuals')]"
    );
    private final By searchInput = By.xpath("//input[@placeholder='Search here' and not(@disabled)]");

    private final By tableRoot = By.cssSelector(".ant-table");
    private final By tableBody = By.cssSelector(".ant-table .ant-table-tbody");
    private final By tableRows = By.cssSelector(".ant-table .ant-table-tbody > tr.ant-table-row");

    private final By pagination  = By.cssSelector(".ant-table-pagination");
    private final By nextPageLi  = By.cssSelector(".ant-table-pagination .ant-pagination-next");
    private final By prevPageLi  = By.cssSelector(".ant-table-pagination .ant-pagination-prev");
    private final By nextPageBtn = By.cssSelector(".ant-table-pagination .ant-pagination-next button");
    private final By pageItems   = By.cssSelector(".ant-table-pagination .ant-pagination-item");
    private final By totalText   = By.cssSelector(".ant-table-pagination .ant-pagination-total-text");


    // --- Sort controls (AntD-friendly, text-robust) ---
    private final By sortButton = By.xpath(
            "//p[starts-with(normalize-space(.), 'Sort by:')]/following-sibling::*[1][name()='svg']"
    );

    private By sortOption(String label) {
        // supports AntD dropdowns or menus
        return By.xpath(
                "(" +
                        "//div[contains(@class,'ant-dropdown') or contains(@class,'ant-select-dropdown')]" +
                        "//*[self::li or self::div or self::span or self::button]" +
                        "[normalize-space()='" + label + "']" +
                        ")[last()]"
        );
    }

    // exact CSS -> XPath
    private static final By OPEN_MENU = By.xpath(
            "//*[" +
                    "contains(concat(' ', normalize-space(@class), ' '), ' ant-dropdown ') and " +
                    "(" +
                    "contains(concat(' ', normalize-space(@class), ' '), ' ant-dropdown-open ') or " +
                    "not(@hidden)" +
                    ")" +
                    "]"
    );




    private WebElement emailCellInRow(WebElement row){
        return row.findElement(By.cssSelector("td:nth-of-type(2) h4"));
    }

    private WebElement reportCellInRow(WebElement row){
        return row.findElement(By.cssSelector("td:nth-of-type(3)"));
    }

    private WebElement kebabInRow(WebElement row){
        return row.findElement(By.cssSelector("td:nth-of-type(4) .ant-dropdown-trigger"));
    }

    private final By emptyState = By.cssSelector(".ant-table .ant-empty, .ant-empty-description, .ant-table-placeholder");

    private By emailCellAny(String emailLower) {
        return By.xpath(
                "((//div[contains(@class,'ant-table')])[1]//tbody)[1]" +
                        "//*[self::td or self::div or self::span or self::a or self::h4]" +
                        "[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'" + emailLower + "')]"
        );
    }



// ================= SORT (caret+options) tailored to your DOM =================

    /** <p>Sort by: …</p> → the adjacent SVG caret to open the dropdown */
    private final By sortCaretSvg = By.xpath(
            // find any P that starts with "Sort by:" and click its immediate svg sibling
            "(//p[starts-with(normalize-space(),'Sort by:')]/following-sibling::*[name()='svg'][1])[1]"
    );

    /** The container that holds the 4 <p> options once the caret is clicked */
    private final By sortOptionsContainer = By.xpath(
            // after the P and SVG, the *next* sibling DIV contains the <p> options
            "(//p[starts-with(normalize-space(),'Sort by:')]/following-sibling::*[name()='svg'][1]" +
                    "/following-sibling::div[1])[1]"
    );

    /** A single <p> option by its full label text, e.g. "Sort by: Newest" */
    private By sortOptionByFullText(String fullText) {
        return By.xpath(".//p[normalize-space()='" + fullText + "']");
    }



    /** Map friendly labels to actual option <p> text shown in the UI */
    private String toOptionFullText(String label) {
        String s = label.trim().toLowerCase(java.util.Locale.ROOT);
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
                // allow callers to pass the exact full text already
                if (label.startsWith("Sort by:")) return label;
                throw new IllegalArgumentException("Unknown sort label: " + label);
        }
    }



    /** A stable per-row signature (Name || Email) for order comparisons on the current page. */
    public java.util.List<String> getRowOrderSignature() {
        By nameCand = By.cssSelector("td:nth-of-type(1), td [data-col='name'], td .name, td h4:first-child");
        java.util.List<WebElement> rows = driver.findElements(tableRows);
        java.util.List<String> sigs = new java.util.ArrayList<>(rows.size());
        for (WebElement row : rows) {
            String name = "";
            try { name = row.findElement(nameCand).getText().trim(); } catch (Throwable ignored) {}
            String email = "";
            try { email = emailCellInRow(row).getText().trim(); } catch (Throwable ignored) {}
            sigs.add((name == null ? "" : name) + " || " + (email == null ? "" : email));
        }
        return sigs;
    }



    // 1) Row root by email (works for table or div-row grids)
    private String rowByEmailXpath(String email) {
        return "(//tr[.//td[normalize-space()='" + email + "']]" +
                " | //div[@role='row'][.//*[normalize-space()='" + email + "']])";
    }


    // 2) Open the ⋮ actions menu for a given email (robust + fallbacks)
    public void openActionsFor(String email) {
        // Row (simple + correct brackets)
        String rowXpath = "(//tr[.//td[normalize-space()='" + email + "']]" +
                " | //div[@role='row'][.//*[normalize-space()='" + email + "']])";
        By rowBy = By.xpath(rowXpath);
        // (//tr[.//td[normalize-space()='erodriguez+12002@effectussoftware.com']] | //div[@role='row'][.//*[normalize-space()='erodriguez+12002@effectussoftware.com']])

        WebElement row = waitForElementVisible(rowBy);
        scrollToElement(row);
        new Actions(driver).moveToElement(row).pause(java.time.Duration.ofMillis(120)).perform();


        // ----- SUPER-SIMPLE CANDIDATES -----
        By lastCellSvg  = By.xpath(rowXpath + "/*[self::td or @role='cell'][last()]//*[name()='svg']/ancestor::*[self::button or self::div or self::span][1]");
        By lastCellDots = By.xpath(rowXpath + "/*[self::td or @role='cell'][last()]//*[normalize-space()='⋮' or normalize-space()='...']");

        By[] cands = new By[] { lastCellSvg, lastCellDots };

        for (By c : cands) {
            List<WebElement> found = driver.findElements(c);
            if (!found.isEmpty()) {
                WebElement el = found.get(found.size()-1); // prefer the last match in the row
                try {
                    scrollToElement(el);
                    new Actions(driver).moveToElement(el).pause(java.time.Duration.ofMillis(80)).perform();
                    el.click();
                } catch (Exception clickFallback) {
                    // JS fallback in case it’s overlayed
                    ((JavascriptExecutor)driver).executeScript("arguments[0].click();", el);
                }
                return;
            }
        }

        // Helpful debug if nothing matched
        System.out.println("openActionsFor(): row HTML dump =>\n" +
                driver.findElement(By.xpath(rowXpath)).getAttribute("outerHTML"));
        throw new NoSuchElementException("Could not find row actions trigger for: " + email);
    }


    // 3) Click “Send reminder” in the currently open menu
    public void clickSendReminderInOpenMenu() {
        By item = By.xpath("(" +
                "(//*[@role='menu'] | //*[contains(@class,'ant-dropdown')])" +
                "//*[@role='menuitem' or self::li or self::button or self::div or self::span]" +
                "[normalize-space()='Send reminder' and not(ancestor-or-self::*[@aria-hidden='true' or contains(@style,'display: none')])]" +
                ")[last()]");
        click(item);
    }


    public By closeModalButton(){
        return By.xpath("//button[@aria-label='Close']");
    }


    // (Optional) success toast detector (AntD/message tolerant)
//    public boolean waitForSuccessToast() {
//        By toast = By.xpath("(" +
//                "//div[contains(@role,'alert') or contains(@class,'toast') or contains(@class,'notification')]" +
//                "[contains(.,'sent') or contains(.,'success') or contains(.,'reminder')]" +
//                " | //div[contains(@class,'ant-message')]//span[contains(.,'sent') or contains(.,'success')]" +
//                ")");
//        return isVisible(toast);
//    }







    @Step("Open Sort menu (click caret next to 'Sort by:')")
    public void openSortMenu() {
        WebElement caret = wdw.until(ExpectedConditions.elementToBeClickable(sortCaretSvg));
        caret.click();
        // wait for options container with <p> items to be visible
        wdw.until(ExpectedConditions.visibilityOfElementLocated(sortOptionsContainer));
    }

    @Step("Choose sort option: {label}")
    public void chooseSortOption(String label) {
        // Snapshot tbody or first-row text to detect refresh
        WebElement oldBody = null;
        String firstRowBefore = null;
        try {
            oldBody = driver.findElement(tableBody);
            java.util.List<WebElement> rows = driver.findElements(tableRows);
            if (!rows.isEmpty()) firstRowBefore = rows.get(0).getText();
        } catch (NoSuchElementException ignored) { }

        openSortMenu();

        String full = toOptionFullText(label);
        WebElement container = wdw.until(ExpectedConditions.visibilityOfElementLocated(sortOptionsContainer));

        // Find the <p> inside the container and click it
        WebElement opt = wdw.until(
                ExpectedConditions.elementToBeClickable(container.findElement(sortOptionByFullText(full)))
        );
        opt.click();

        // Wait for a refresh: prefer tbody staleness, else first-row text change
        boolean refreshed = false;
        if (oldBody != null) {
            try {
                new WebDriverWait(driver, Duration.ofSeconds(10))
                        .until(ExpectedConditions.stalenessOf(oldBody));
                refreshed = true;
            } catch (TimeoutException ignored) { }
        }

        // make an effectively-final snapshot for the lambda
        final String firstRowBeforeSnap = firstRowBefore;
        if (!refreshed && firstRowBeforeSnap != null) {
            try {
                new WebDriverWait(driver, Duration.ofSeconds(6)).until(d -> {
                    java.util.List<WebElement> rows = d.findElements(tableRows);
                    return !rows.isEmpty() && !rows.get(0).getText().equals(firstRowBeforeSnap);
                });
            } catch (TimeoutException ignored) { }
        }

        waitForTableSettled();
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
        try{
            waitUntilLoaded();
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    // ======= NAVIGATION =======

    @Step("Open Individuals")
    public IndividualsPage open(String baseUrl) {
        String url = Config.joinUrl(baseUrl, "/dashboard/individuals?ts=" + System.nanoTime());
        driver.navigate().to(url);
        return waitUntilLoaded();
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

        // (1) ensure the input value actually reflects our query (handles re-renders)
        try {
            new WebDriverWait(driver, Duration.ofSeconds(4)).until(d -> {
                WebElement el = d.findElement(searchInput);
                String v = el.getAttribute("value");
                return v != null && v.equals(text);
            });
        } catch (TimeoutException ignored) { /* not fatal; proceed */ }

        // accept any of: tbody staleness OR row-count change
        try {
            if (oldBody != null) new WebDriverWait(driver, Duration.ofSeconds(8))
                    .until(ExpectedConditions.stalenessOf(oldBody));
        } catch (TimeoutException ignored) { }
        try {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(d -> driver.findElements(tableRows).size() != before);
        } catch (TimeoutException ignored) { }

        // (2) global loader gate + final settle
        wait.waitForLoadersToDisappear();
        waitForTableSettled(); // see tweak below to include empty-state
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


    // Resolve the 1-based column index by header text ("Report")
    private int getColumnIndexByHeader(String headerText) {
        java.util.List<WebElement> ths = driver.findElements(By.cssSelector("table thead th"));
        int idx = -1;

        // Exact match first
        for (int i = 0; i < ths.size(); i++) {
            String t = ths.get(i).getText();
            if (t != null && t.trim().equalsIgnoreCase(headerText)) { idx = i + 1; break; }
        }
        // Fallback: contains "report"
        if (idx == -1) {
            String needle = headerText.toLowerCase(java.util.Locale.ROOT);
            for (int i = 0; i < ths.size(); i++) {
                String t = ths.get(i).getText();
                if (t != null && t.toLowerCase(java.util.Locale.ROOT).contains(needle)) { idx = i + 1; break; }
            }
        }
        if (idx <= 0) throw new AssertionError("❌ Could not locate '" + headerText + "' header.");
        return idx;
    }

    /**
     * Returns:
     *   - "Pending"           when the cell shows Pending text
     *   - "Link:<href>"       when a visible link has a non-empty href/data-href
     *   - "Link"              when a visible link exists but no href attribute (JS click)
     *   - "NotFound"          when row or cell cannot be resolved
     */
    public String getReportStatusByEmail(String email) {
        try {
            java.util.Optional<WebElement> rowOpt = findRowByEmail(email); // you already have this
            if (rowOpt.isEmpty()) return "NotFound";
            WebElement row = rowOpt.get();

            int reportCol = getColumnIndexByHeader("Report");
            WebElement cell = row.findElement(By.xpath("./td[" + reportCol + "]"));

            // Case 1: literal "Pending"
            String txt = cell.getText() == null ? "" : cell.getText().trim();
            if ("pending".equalsIgnoreCase(txt)) return "Pending";

            // Case 2: any visible link-ish element
            java.util.List<WebElement> linkish = cell.findElements(By.cssSelector("a, [role='link'], button[role='link']"));
            if (!linkish.isEmpty()) {
                WebElement a = linkish.get(0);
                if (a.isDisplayed()) {
                    // Prefer DOM attribute; then property; then data-href
                    String href = null;
                    try { href = a.getDomAttribute("href"); } catch (Throwable ignored) {}
                    if (href == null || href.isBlank()) { try { href = a.getAttribute("href"); } catch (Throwable ignored) {} }
                    if (href == null || href.isBlank()) { try { href = a.getDomAttribute("data-href"); } catch (Throwable ignored) {} }

                    if (href != null && !href.isBlank()) return "Link:" + href.trim();
                    return "Link"; // visible, JS-click style link
                }
            }
            return "NotFound";
        } catch (NoSuchElementException e) {
            return "NotFound";
        }
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
        // 0) Find the row
        Optional<WebElement> rowOpt = findRowByEmail(email);
        if (rowOpt.isEmpty()) return false;
        WebElement row = rowOpt.get();

        // 1) Bring row into view and hover (many UIs reveal the trigger on hover)
        try {
            scrollToElement(row);
        } catch (Throwable ignore) {}
        new Actions(driver).moveToElement(row).pause(Duration.ofMillis(120)).perform();

        // 2) Primary trigger in your DOM: actions cell kebab
        WebElement trigger = null;
        try {
            trigger = kebabInRow(row); // td:nth-of-type(4) .ant-dropdown-trigger
        } catch (NoSuchElementException ignored) {
            // try a couple of very small in-cell fallbacks without changing public API
            try {
                trigger = row.findElement(By.cssSelector("td:nth-of-type(4) [aria-label*='Action' i], td:nth-of-type(4) [aria-label*='More' i]"));
            } catch (NoSuchElementException ignored2) {
                try {
                    trigger = row.findElement(By.cssSelector("td:nth-of-type(4) button, td:nth-of-type(4) [role='button']"));
                } catch (NoSuchElementException ignored3) {
                    // last-resort: last cell anything clickable-looking
                    try {
                        trigger = row.findElement(By.cssSelector("td:last-child .ant-dropdown-trigger, td:last-child [role='button'], td:last-child button"));
                    } catch (NoSuchElementException ignored4) {
                        trigger = null;
                    }
                }
            }
        }
        if (trigger == null) return false;

        // 3) Try to click it (with hover + JS fallback)
        try {
            new Actions(driver).moveToElement(trigger).pause(Duration.ofMillis(80)).click(trigger).perform();
        } catch (Exception e) {
            try {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", trigger);
            } catch (Throwable ignored) { /* fall through */ }
        }

        // 4) Confirm menu is open; if not, retry once
        try {
            waitForMenuOpen();
            return true;
        } catch (Exception firstFail) {
            try {
                new Actions(driver).moveToElement(trigger).pause(Duration.ofMillis(80)).click(trigger).perform();
            } catch (Exception e2) {
                try { ((JavascriptExecutor) driver).executeScript("arguments[0].click();", trigger); } catch (Throwable ignored) {}
            }
            try {
                waitForMenuOpen();
                return true;
            } catch (Exception secondFail) {
                return false;
            }
        }
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


    /** Returns the highest numeric page shown in the pagination (fallback 1). */
    public int getMaxPageNumber() {
        int max = 1;
        for (WebElement li : driver.findElements(pageItems)) { // pageItems: existing locator
            String txt = li.getText() == null ? "" : li.getText().trim();
            if (txt.matches("\\d+")) {
                int n = Integer.parseInt(txt);
                if (n > max) max = n;
            }
        }
        return max;
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

    /* ---- tiny helpers (keep them private in this page object) ---- */

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

    private String getDisplayingRangeTextSafe() {
        try { return driver.findElement(totalText).getText().trim(); }
        catch (Exception e) { return ""; }
    }




    public int getTotalCount() {
        if (!isPresent(totalText)) return -1;
        String text = driver.findElement(totalText).getText();
        String digits = text.replaceAll(".*of\\s+(\\d+).*", "$1");
        try { return Integer.parseInt(digits); } catch (Exception ignore) { return -1; }
    }

    // ======= Utilities =======
    private boolean looksLikeEmail(String s) {
        return s != null && s.contains("@") && s.contains(".");
    }

    private void waitForMenuOpen() {
        By openMenu = By.cssSelector(".ant-dropdown.ant-dropdown-open, .ant-dropdown:not([hidden])");
        try { new WebDriverWait(driver, Duration.ofSeconds(20)).until(ExpectedConditions.presenceOfElementLocated(openMenu)); }
        catch (Exception ignored) { }
    }

    private void waitForTableSettled() {
        wdw.until(ExpectedConditions.presenceOfElementLocated(tableRoot));
        wdw.until(ExpectedConditions.presenceOfElementLocated(tableBody));
        // rows visible OR empty state visible
        wdw.until(d -> !d.findElements(tableRows).isEmpty() || !d.findElements(emptyState).isEmpty());
    }

    private void waitForTableRefreshed() {
        WebElement oldBody = null;
        try { oldBody = driver.findElement(tableBody); } catch (NoSuchElementException ignored) { }
        if (oldBody != null) {
            try {
                new WebDriverWait(driver, Duration.ofSeconds(20))
                        .until(ExpectedConditions.stalenessOf(oldBody));
            } catch (TimeoutException ignored) {
                int before = driver.findElements(tableRows).size();
                new WebDriverWait(driver, Duration.ofSeconds(20))
                        .until(d -> driver.findElements(tableRows).size() != before);
            }
        }
        waitForTableSettled();
    }

    private String safeText(SupplierWithException<String> supplier) {
        try {
            return supplier.get();
        } catch (Throwable t){
            return "";
        }
    }



    @FunctionalInterface
    public interface SupplierWithException<T> {
        T get() throws Exception;
    }

    // ======== Public helpers ========

    // tiny helper so other methods can self-heal if called on the wrong page
    private boolean isOnIndividuals() {
        String u = driver.getCurrentUrl().toLowerCase();
        return u.contains("/dashboard/individual");
    }

    @Step("Hard reload Individuals with cache-buster")
    public void reloadWithBuster(String baseUrl) {
        String url = baseUrl.replaceAll("/+$", "") + "/dashboard/individuals?ts=" + System.nanoTime();
        driver.navigate().to(url);
        waitUntilLoaded(); // your existing: wait for loaders + table
    }

    /**
     * Stale-buster polling: repeatedly hard-reload Individuals and look for the email.
     * Throws AssertionError if not found after attempts.
     */
    @Step("Assert individual appears with reloads: {email}")
    public void assertAppearsWithReload(String baseUrl, String email) {
        final String emailLc   = email.trim().toLowerCase(Locale.ROOT);
        final int    maxTries  = Integer.getInteger("INDIV_ATTEMPTS", 10);    // -DINDIV_ATTEMPTS=12
        final long   sleepMs   = Long.getLong("INDIV_SLEEP_MS", 5000L);       // -DINDIV_SLEEP_MS=3000

        for (int i = 1; i <= maxTries; i++) {
            reloadWithBuster(baseUrl);
            if (isUserListedByEmail(emailLc)) {
                return; // ✅ found
            }
            try { Thread.sleep(sleepMs); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
        }
        throw new AssertionError("❌ User not found in Individuals after retries: " + emailLc);
    }

    @Step("Assert individual appears (UI) with optional network evidence + backend cross-check: {email}")
    public void assertAppearsWithEvidence(String baseUrl, String email) throws InterruptedException {
//        Thread.sleep(5000);

        // Flags (override via -DINDIV_CDP_CAPTURE, -DINDIV_BACKEND_CHECK, -DINDIV_CDP_WAIT_SEC)
        final boolean captureNetwork = Boolean.parseBoolean(System.getProperty("INDIV_CDP_CAPTURE", "true"));
        final boolean backendCheck   = Boolean.parseBoolean(System.getProperty("INDIV_BACKEND_CHECK", "false")); // default off to run without API wiring
        final int     cdpWaitSec     = Integer.getInteger("INDIV_CDP_WAIT_SEC", 20);

        assertAppearsWithEvidence(baseUrl, email, Duration.ofSeconds(cdpWaitSec), captureNetwork, backendCheck);
    }

    /**
     * Core reusable helper:
     * 1) optional CDP capture of /api/v2/individuals
     * 2) stale-buster reload + UI wait
     * 3) optional backend GET /api/v2/individuals?email=...
     */
    public void assertAppearsWithEvidence(String baseUrl,
                                          String email,
                                          Duration cdpWait,
                                          boolean captureNetwork,
                                          boolean backendCheck) {

        final String emailLc = email.trim().toLowerCase(Locale.ROOT);

        // 1) Start CDP capture (optional; no-op if driver doesn’t support DevTools)
        final Optional<probes.NetworkCapture> capOpt =
                captureNetwork
                        ? probes.NetworkCapture.start(driver, url -> url.contains("/api/v2/individuals"))
                        : Optional.empty();

        if (capOpt.isPresent()) {
            // Ensure capture closes even if the UI assertion fails
            try (probes.NetworkCapture cap = capOpt.get()) {
                // 2) UI flow with stale-buster
                this.assertAppearsWithReload(baseUrl, emailLc);

                // 2b) Assert network body includes the email
                final boolean seen = cap.waitForAny(cdpWait) && cap.anyBodyContainsIgnoreCase(emailLc);
                if (!seen) {
                    throw new AssertionError("❌ /api/v2/individuals responses did not include "
                            + email + " (webhook race or filtering?)");
                }
            }
        } else {
            // No capture: just run the UI flow
            this.assertAppearsWithReload(baseUrl, emailLc);
        }

        // 3) Backend cross-check (optional; off by default)
        if (backendCheck) {
            try {
                final BackendApi be = BackendApi.create(ApiConfig.fromEnv()); // requires base URL + auth in env
                // NOTE: your generated interface expects Map<String,String>
                final Response<ResponseBody> resp =
                        be.individualsApiV2().index(Map.of("email", emailLc)).execute();

                if (!resp.isSuccessful()) {
                    throw new AssertionError("❌ /api/v2/individuals HTTP " + resp.code());
                }
                try (ResponseBody body = resp.body()) {
                    final String json = (body != null) ? body.string() : "";
                    if (!json.toLowerCase(Locale.ROOT).contains(emailLc)) {
                        throw new AssertionError("❌ Backend payload did not include " + email);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(
                        "❌ Backend check failed (connection/auth?). Set -DINDIV_BACKEND_CHECK=false to skip.",
                        e
                );
            }
        }
    }

    // ======== Private helpers (unique names) ========

    /**
     * Single-attempt UI check (no reload loop). Call inside the poller if you prefer.
     * Keep this name different from assertAppearsWithReload to avoid signature clashes.
     */
    private void searchOnceAndAssert(String baseUrl, String emailLc) {
        driver.navigate().to(baseUrl.replaceAll("/+$", "") + "/dashboard/individuals?ts=" + System.nanoTime());
        waitUntilLoaded();
        // searchBox.clear();
        // searchBox.sendKeys(emailLc + Keys.ENTER);
        // waitForResults();
        Assert.assertTrue(isUserListedByEmail(emailLc), "User not found in Individuals: " + emailLc);
    }



    public boolean rowReportIsPending(WebElement row) {
        try {
            WebElement cell = reportCellInRow(row);
            String txt = cell.getText();
            if (txt != null && txt.toLowerCase(Locale.ROOT).contains("pending")) return true;

            // also catch common AntD badges/tags that may render the text separately
            List<WebElement> badges = cell.findElements(By.cssSelector(
                    ".ant-tag, .ant-badge, .ant-badge-status, .ant-badge-status-text"
            ));
            for (WebElement b : badges) {
                String t = b.getText();
                if (t != null && t.toLowerCase(Locale.ROOT).contains("pending")) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Useful for debugging assertions/messages. */
    public String rowReportText(WebElement row) {
        try { return reportCellInRow(row).getText().trim(); }
        catch (Exception e) { return ""; }
    }


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

    // ======= Added general-purpose helpers for TC-434 and beyond =======

    /** @return true if at least one row exists in the current table page */
    public boolean hasAnyRows() {
        return !driver.findElements(tableRows).isEmpty();
    }

    /** Returns the first N rows for spot-checks (bounds-safe). */
    public List<WebElement> firstRows(int max) {
        List<WebElement> rows = driver.findElements(tableRows);
        return rows.subList(0, Math.min(max, rows.size()));
    }

    /** Name presence in the row (common selectors + fallback to row text). */
    public boolean rowHasName(WebElement row) {
        By nameCand = By.cssSelector("td:nth-of-type(1), td [data-col='name'], td .name, td h4:first-child");
        try {
            String t = row.findElement(nameCand).getText().trim();
            return !t.isEmpty();
        } catch (NoSuchElementException e) {
            String t = row.getText().trim();
            return !t.isEmpty();
        }
    }

    /** At least one assessment icon (img with alt/aria mentioning 'assess', or any SVG). */
    public boolean rowHasAssessmentIcon(WebElement row) {
        By icons = By.cssSelector("img[alt*='ssess' i], [aria-label*='ssess' i], svg");
        return !row.findElements(icons).isEmpty();
    }

    /** Date taken present & parse-ish: prefers <time> or date-col; falls back to row text regex. */
    public boolean rowHasDateTaken(WebElement row) {
        By dateCand = By.cssSelector("time, [data-col='date'], .date, td time");
        try {
            List<WebElement> dateEls = row.findElements(dateCand);
            if (!dateEls.isEmpty()) {
                for (WebElement el : dateEls) {
                    String txt = el.getText().isBlank() ? el.getAttribute("datetime") : el.getText();
                    if (looksLikeDate(txt)) return true;
                }
            }
            String text = row.getText();
            return looksLikeDate(text);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean looksLikeDate(String t) {
        if (t == null) return false;
        String s = t.trim();
        if (s.isEmpty()) return false;
        Pattern p = Pattern.compile(
                "(\\d{4}-\\d{2}-\\d{2})" +                  // 2025-09-29
                        "|([A-Za-z]{3,9}\\s+\\d{1,2},\\s+\\d{4})" + // Sep 29, 2025
                        "|(\\d{1,2}/\\d{1,2}/\\d{2,4})"             // 09/29/2025
        );
        return p.matcher(s).find();
    }

    /** Report link exists and has a non-empty href. */
    public boolean rowHasReportLink(WebElement row) {
        try {
            WebElement cell = reportCellInRow(row);
            List<WebElement> links = cell.findElements(By.tagName("a"));
            if (links.isEmpty()) return false;
            String href = links.get(0).getAttribute("href");
            return links.get(0).isDisplayed() && links.get(0).isEnabled() && href != null && !href.isBlank();
        } catch (Exception e) {
            return false;
        }
    }


    // Returns the visible emails from the current table page (trimmed, non-empty).
    public List<String> getEmailsOnCurrentPage() {
        return driver.findElements(tableRows)
                .stream()
                .map(row -> {
                    try { return emailCellInRow(row).getText().trim(); }
                    catch (Throwable t) { return ""; }
                })
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }


    public List<String> getNamesOnCurrentPage() {
        By nameCand = By.cssSelector("td:nth-of-type(1), td [data-col='name'], td .name, td h4:first-child");
        return driver.findElements(tableRows)
                .stream()
                .map(row -> {
                    try { return row.findElement(nameCand).getText().trim(); }
                    catch (Throwable t) { return ""; }
                })
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }



    // --- [ADD] helper: locate the Auto reminder switch inside the currently open menu ---
    /** The LI that owns the "Auto reminder" row inside the currently open dropdown. */
    private By autoReminderRowInOpenMenu() {
        return By.xpath(
                "(" +
                        "//*[@role='menu' or contains(@class,'ant-dropdown')]" +
                        "[not(contains(@style,'display: none')) and not(contains(@style,'visibility: hidden'))]" +
                        "[last()]" +
                        "//*[self::li or self::div or self::button]" +
                        "[.//*[normalize-space()='Auto reminder'] or normalize-space()='Auto reminder']" +
                        ")[last()]"
        );
    }





    /** The AntD switch element inside that row (button role=switch; fallback to checkbox). */
    private By autoReminderSwitchInOpenMenu() {
        return By.xpath(
                "(" +
                        openMenuXPathReminderRow() +
                        "//*[self::li or self::div or self::button]" +
                        "[.//p[normalize-space()='Auto reminder'] or .//*[normalize-space()='Auto reminder']]" +
                        "//*[self::button or self::input]" +
                        "[(@role='switch') or (@type='checkbox') or contains(@class,'ant-switch')]" +
                        ")[last()]"
        );
    }

    /** Helper to reuse the open menu scope string inside the other XPaths. */
    private String openMenuXPathReminderRow() {
        return "(" +
                "//*[@role='menu' and not(@aria-hidden='true')]" +
                " | //*[contains(@class,'ant-dropdown') and not(contains(@style,'display: none'))]" +
                ")[last()]";
    }








    /** Ensures Auto reminder is ON/OFF using the row-actions dropdown. Clicks the SWITCH button. */
    public void setAutoReminder(String email, boolean on) {
        final By OPEN_MENU = By.cssSelector(".ant-dropdown.ant-dropdown-open, .ant-dropdown:not([hidden])");

        // 1) Open actions menu for this row
        if (!openActionsMenuFor(email)) {
            throw new NoSuchElementException("Could not open actions menu for: " + email);
        }
        waitForMenuOpen();

        // 2) Scope in the open dropdown
        WebElement menu = wdw.until(ExpectedConditions.visibilityOfElementLocated(OPEN_MENU));
        WebElement sw   = wdw.until(
                ExpectedConditions.visibilityOfNestedElementsLocatedBy(menu, autoReminderSwitchInOpenMenu())
        ).get(0);

        boolean current = readSwitchState(sw);
        if (current == on) { closeMenuIfOpen(); return; }

        // 3) Fire ONCE with guarded fallback (no SPACE key, no row click)
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", sw);

        boolean flipped = false;
        try {
            new Actions(driver).moveToElement(sw).pause(Duration.ofMillis(70)).click(sw).perform();
            Thread.sleep(130); // let AntD apply aria/class
            // re-read in-place if menu still open
            flipped = readSwitchState(sw) == on;
        } catch (ElementNotInteractableException ignored) {
            // we'll attempt JS fallback below
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        if (!flipped) {
            // Re-find inside the (still) open menu to avoid stale refs
            try {
                List<WebElement> menus = driver.findElements(OPEN_MENU);
                if (!menus.isEmpty()) {
                    WebElement menuNow = menus.get(menus.size() - 1);
                    WebElement swNow   = menuNow.findElement(autoReminderSwitchInOpenMenu());

                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", swNow);
                    Thread.sleep(130);
                    flipped = readSwitchState(swNow) == on;

                    // Last-resort: click the switch handle only (prevents bubbling to the row)
                    if (!flipped) {
                        List<WebElement> handles = swNow.findElements(By.cssSelector(".ant-switch-handle"));
                        if (!handles.isEmpty()) {
                            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", handles.get(0));
                            Thread.sleep(130);
                            flipped = readSwitchState(swNow) == on;
                        }
                    }
                }
            } catch (NoSuchElementException | StaleElementReferenceException ignored) {
                // We'll confirm via the strict path below
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        // 4) STRICT confirm (does not treat "menu closed" as success)
        if (!confirmAutoReminderState(email, on, Duration.ofSeconds(6))) {
            // Retry once (cleanly reopen if needed, then click switch once)
            try {
                List<WebElement> menus = driver.findElements(OPEN_MENU);
                if (menus.isEmpty()) {
                    if (!openActionsMenuFor(email)) {
                        throw new NoSuchElementException("Could not reopen actions menu for retry: " + email);
                    }
                    waitForMenuOpen();
                    menus = driver.findElements(OPEN_MENU);
                }
                WebElement menu2 = menus.get(menus.size() - 1);
                WebElement sw2   = wdw.until(
                        ExpectedConditions.visibilityOfNestedElementsLocatedBy(menu2, autoReminderSwitchInOpenMenu())
                ).get(0);

                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", sw2);

                try {
                    new Actions(driver).moveToElement(sw2).pause(Duration.ofMillis(60)).click(sw2).perform();
                } catch (ElementNotInteractableException e) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", sw2);
                }
            } catch (TimeoutException | NoSuchElementException | StaleElementReferenceException ignored) { }

            if (!confirmAutoReminderState(email, on, Duration.ofSeconds(4))) {
                // Hard recovery: reload page and do one last clean toggle + confirm
                closeMenuIfOpen();
                reloadWithBuster(Config.getBaseUrl());

                if (!openActionsMenuFor(email)) {
                    throw new NoSuchElementException("Could not reopen actions menu for verification: " + email);
                }
                waitForMenuOpen();

                WebElement menu3 = wdw.until(ExpectedConditions.visibilityOfElementLocated(OPEN_MENU));
                WebElement sw3   = wdw.until(
                        ExpectedConditions.visibilityOfNestedElementsLocatedBy(menu3, autoReminderSwitchInOpenMenu())
                ).get(0);

                if (readSwitchState(sw3) != on) {
                    try {
                        new Actions(driver).moveToElement(sw3).pause(Duration.ofMillis(60)).click(sw3).perform();
                    } catch (ElementNotInteractableException e) {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", sw3);
                    }
                    if (!confirmAutoReminderState(email, on, Duration.ofSeconds(4))) {
                        throw new AssertionError("Auto reminder did not reach desired state=" + on + " for " + email);
                    }
                }
            }
        }

        // Optional toast wait
        try { waitForSuccessToast(); } catch (Exception ignore) {}

        closeMenuIfOpen();
    }

    /** Strictly verifies state = `on` by reopening and reading the switch inside the dropdown. */
    private boolean confirmAutoReminderState(String email, boolean on, Duration timeout) {
        final By OPEN_MENU = By.cssSelector(".ant-dropdown.ant-dropdown-open, .ant-dropdown:not([hidden])");
        try {
            // If menu got closed, reopen cleanly so we can *read* the switch state for sure
            if (driver.findElements(OPEN_MENU).isEmpty()) {
                if (!openActionsMenuFor(email)) return false;
                waitForMenuOpen();
            }

            WebElement menu = new WebDriverWait(driver, timeout)
                    .until(ExpectedConditions.visibilityOfElementLocated(OPEN_MENU));
            WebElement sw   = new WebDriverWait(driver, timeout)
                    .until(ExpectedConditions.visibilityOfNestedElementsLocatedBy(menu, autoReminderSwitchInOpenMenu()))
                    .get(0);

            // Allow short debounce for class/aria to settle
            try { Thread.sleep(120); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            return readSwitchState(sw) == on;
        } catch (TimeoutException | NoSuchElementException | StaleElementReferenceException e) {
            return false;
        }
    }


    /** Ensures the Send reminder button is interactable before clicking. */
    public void ensureModalSendEnabled() {
        WebElement modal = waitForSendReminderModal();
        WebDriverWait w = new WebDriverWait(driver, java.time.Duration.ofSeconds(6));
        WebElement btn = w.until(d -> modal.findElement(By.xpath(".//button[normalize-space()='Send reminder']")));
        w.until(d -> btn.isDisplayed() && btn.isEnabled());
    }

    /** Waits for the active AntD modal to disappear after submitting. */
    public void waitForModalToClose() {
        By lastVisibleModal = By.xpath("(" +
                "//*[contains(@class,'ant-modal') and not(contains(@style,'display: none'))]" +
                ")[last()]");
        new WebDriverWait(driver, java.time.Duration.ofSeconds(8))
                .until(ExpectedConditions.invisibilityOfElementLocated(lastVisibleModal));
    }













    private void closeMenuIfOpen() {
        try {
            // ESC first (AntD menus close on Escape)
            driver.switchTo().activeElement().sendKeys(Keys.ESCAPE);
            // tap body as a fallback
            driver.findElement(By.tagName("body")).click();
        } catch (Throwable ignore) { }
    }





// --- [ADD] public API: read state ---
    /** Returns true if the Auto reminder toggle is ON for the given email.
     *  Note: expects the actions menu for this row to be open (your tests already do openActionsFor(email)).
     *  If not open, we try to open it once.
     */
    public boolean isAutoReminderOn(String email) {
        // Try to find it in the current open menu
        WebElement sw;
        try {
            sw = new WebDriverWait(driver, java.time.Duration.ofSeconds(3))
                    .until(ExpectedConditions.visibilityOfElementLocated(autoReminderSwitchInOpenMenu()));
        } catch (TimeoutException te) {
            // If not visible, open the menu for this email and try again
            openActionsFor(email);
            sw = new WebDriverWait(driver, java.time.Duration.ofSeconds(5))
                    .until(ExpectedConditions.visibilityOfElementLocated(autoReminderSwitchInOpenMenu()));
        }

        String ariaChecked = sw.getAttribute("aria-checked");
        if (ariaChecked != null && !ariaChecked.isBlank()) {
            return "true".equalsIgnoreCase(ariaChecked.trim());
        }
        String ariaPressed = sw.getAttribute("aria-pressed");
        if (ariaPressed != null && !ariaPressed.isBlank()) {
            return "true".equalsIgnoreCase(ariaPressed.trim());
        }
        String checked = sw.getAttribute("checked");
        return checked != null && !checked.isBlank() && "true".equalsIgnoreCase(checked.trim());
    }



    /** Reads ON/OFF by common attributes of AntD switch. */
    private boolean readSwitchState(WebElement el) {
        String aria = safeAttr(el, "aria-checked");
        if (!aria.isBlank()) return "true".equalsIgnoreCase(aria);
        String cls = safeAttr(el, "class");
        if (!cls.isBlank()) return cls.contains("ant-switch-checked");
        String pressed = safeAttr(el, "aria-pressed");
        if (!pressed.isBlank()) return "true".equalsIgnoreCase(pressed);
        String checked = safeAttr(el, "checked");
        return "true".equalsIgnoreCase(checked);
    }



    private String safeAttr(WebElement el, String name) {
        try { String v = el.getAttribute(name); return v == null ? "" : v; } catch (Throwable t) { return ""; }
    }




    @Step("Set page size to {size} per page")
    public void setPageSize(int size) {
        final String want = String.valueOf(size);

        // Snapshot: current footer text + visible row count (fast change signals)
        String beforeFooter = "";
        try { beforeFooter = driver.findElement(totalText).getText().trim(); } catch (Throwable ignored) {}
        int beforeCount = driver.findElements(tableRows).size();

        // Find the page-size select trigger inside the table pagination (AntD v4/v5 tolerant)
        WebElement pager = wdw.until(ExpectedConditions.visibilityOfElementLocated(pagination));

        // Common AntD patterns:
        //  - v4/v5: div.ant-select (role=combobox)
        //  - fallback: button/span that contains "/ page" or the current size text
        By triggerCand = By.xpath(
                ".//*[contains(@class,'ant-select') and @role='combobox']" +
                        " | .//*[self::button or self::span or self::div][contains(normalize-space(),' / page')]" +
                        " | .//*[contains(@class,'ant-pagination-options')]//*[self::button or self::span or self::div]"
        );
        WebElement trigger = pager.findElements(triggerCand).stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("❌ Page size trigger not found in pagination."));

        // Open the dropdown (click, with JS fallback)
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", trigger);
        try { trigger.click(); } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", trigger);
        }

        // Select the option by visible text: support "10", "10 / page", "10/page"
        By option = By.xpath(
                "(" +
                        "//div[contains(@class,'ant-select-dropdown')]" +
                        "//*[self::div or self::span or self::li or self::p]" +
                        "[normalize-space()='" + want + "' " +
                        " or normalize-space()=concat('" + want + "', ' / page')" +
                        " or normalize-space()=concat('" + want + "','/','page')" +
                        " or starts-with(normalize-space(), concat('" + want + "',' '))" +
                        " or starts-with(normalize-space(), concat('" + want + "','/'))" +
                        "]" +
                        ")[last()]"
        );
        WebElement opt = wdw.until(ExpectedConditions.elementToBeClickable(option));
        opt.click();

        // Wait for size to apply: prefer footer change or row-count change, then settle
        boolean changed = false;

        // A) footer text change (e.g., 'Displaying 7–12 of 27')
        if (isPresent(totalText)) {
            try {
                String beforeSnap = beforeFooter;
                new WebDriverWait(driver, Duration.ofSeconds(3))
                        .until(d -> {
                            String now = "";
                            try { now = d.findElement(totalText).getText().trim(); } catch (Throwable ignored2) {}
                            return !now.isEmpty() && !now.equals(beforeSnap);
                        });
                changed = true;
            } catch (TimeoutException ignored) {}
        }

        // B) visible row count change
        if (!changed) {
            try {
                int beforeCntSnap = beforeCount;
                new WebDriverWait(driver, Duration.ofSeconds(3))
                        .until(d -> d.findElements(tableRows).size() != beforeCntSnap);
                changed = true;
            } catch (TimeoutException ignored) {}
        }

        // C) As a last resort, rely on your generic settle (covers virtualized tables)
        waitForTableSettled();
    }

    // Opens the Report link for the given email, forcing same-tab navigation.
// Returns a ReportSummaryPage (not yet waited).
    public ReportSummaryPage openReportLinkByEmail(String email) {
        java.util.Optional<WebElement> rowOpt = findRowByEmail(email);
        if (rowOpt.isEmpty()) throw new AssertionError("❌ Row not found for email: " + email);
        WebElement row = rowOpt.get();

        int reportCol = getColumnIndexByHeader("Report"); // the helper we added earlier
        WebElement cell = row.findElement(By.xpath("./td[" + reportCol + "]"));

        java.util.List<WebElement> linkish = cell.findElements(By.cssSelector("a, [role='link'], button[role='link']"));
        if (linkish.isEmpty()) throw new AssertionError("❌ No link element in Report cell for email: " + email);

        WebElement link = linkish.get(0);
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", link);
        try { ((JavascriptExecutor) driver).executeScript("arguments[0].setAttribute('target','_self');", link); } catch (Exception ignored) {}
        try { link.click(); } catch (Exception e) { ((JavascriptExecutor) driver).executeScript("arguments[0].click();", link); }

        return new ReportSummaryPage(driver);
    }





    // ============ SEND REMINDER MODAL HELPERS ============

    /** Opens row menu for the email and clicks 'Send reminder', then waits for the modal to be visible. */
    public IndividualsPage openSendReminderModalFor(String email) {
        if (!openActionsMenuFor(email)) {
            // keep your legacy fallback if present
            openActionsFor(email);
        }
        clickSendReminderInOpenMenu(); // you already have this
        waitForSendReminderModal();
        return this;
    }


    /** Waits for the last visible AntD modal whose title contains 'Send reminder'. */
    public WebElement waitForSendReminderModal() {
        WebDriverWait wdw = new WebDriverWait(driver, java.time.Duration.ofSeconds(8));
        By modalRoot = By.xpath("(" +
                "//*[contains(@class,'ant-modal')]" +
                "[not(contains(@style,'display: none'))]" +
                "[descendant::*[contains(@class,'ant-modal-content')]]" +
                ")[last()]");
        WebElement modal = wdw.until(ExpectedConditions.visibilityOfElementLocated(modalRoot));

        // Optional title check (id is dynamic sometimes; AntD title has .ant-modal-title)
        try {
            WebElement title = modal.findElement(By.cssSelector(".ant-modal-title, [id$='title']"));
            String t = title.getText() == null ? "" : title.getText().trim().toLowerCase();
            if (!t.contains("send reminder")) {
                // not fatal, but helps diagnose odd UIs
                System.out.println("⚠ Modal title not matching 'Send reminder': " + t);
            }
        } catch (NoSuchElementException ignore) {}
        return modal;
    }


    /** Clicks the primary button inside the Send reminder modal. */
    public void clickModalSendReminder() {
        WebElement modal = waitForSendReminderModal();
        // Your DOM shows exact text "Send reminder" on the button.
        // Try exact match first; fallback to data-variant or role=button.
        List<By> locators = List.of(
                By.xpath(".//button[normalize-space()='Send reminder']"),
                By.xpath(".//*[@role='button' and normalize-space()='Send reminder']"),
                // As a very last resort, the last primary-looking button in modal footer
                By.xpath(".//*[contains(@class,'ant-modal-footer')]//button[last()]")
        );
        WebElement sendBtn = null;
        for (By loc : locators) {
            List<WebElement> found = modal.findElements(loc);
            if (!found.isEmpty()) { sendBtn = found.get(0); break; }
        }
        if (sendBtn == null) throw new NoSuchElementException("Could not find 'Send reminder' button in modal.");
        sendBtn.click();
    }


    /** Clicks the 'Cancel' button in the currently open Send reminder modal. */
    public void clickModalCancel() {
        WebElement modal = waitForSendReminderModal();
        // Text-first, no test-level locators
        List<WebElement> btns = modal.findElements(By.xpath(".//button[normalize-space()='Cancel']"));
        if (btns.isEmpty()) {
            btns = modal.findElements(By.xpath(".//*[contains(@class,'ant-modal-footer')]//button[1]"));
        }
        if (btns.isEmpty()) throw new NoSuchElementException("Cancel button not found in Send reminder modal.");
        WebElement cancel = btns.get(0);
        new WebDriverWait(driver, java.time.Duration.ofSeconds(5))
                .until(d -> cancel.isDisplayed() && cancel.isEnabled());
        cancel.click();
    }




    /** Waits for a SUCCESS toast (AntD message/notification) and returns its text. */
    public String waitForSuccessToast() {
        WebDriverWait wdw = new WebDriverWait(driver, java.time.Duration.ofSeconds(12));

        // Visible AntD message/notification "notice" blocks
        By notices = By.xpath("("
                // AntD success alert (your screenshot)
                + "//*[contains(@class,'ant-alert') and contains(@class,'ant-alert-success')"
                + "  and @role='alert' and not(contains(@style,'display: none'))]"
                // Fallback: AntD message notice
                + " | //*[contains(@class,'ant-message') and not(contains(@style,'display: none'))]"
                + "    /*[contains(@class,'ant-message-notice') and not(contains(@style,'display: none'))]"
                // Fallback: AntD notification notice
                + " | //*[contains(@class,'ant-notification') and not(contains(@style,'display: none'))]"
                + "    /*[contains(@class,'ant-notification-notice') and not(contains(@style,'display: none'))]"
                + ")[last()]");

        WebElement container;
        try {
            container = wdw.until(drv -> {
                List<WebElement> all = drv.findElements(notices);
                if (all.isEmpty()) return null;
                WebElement last = all.get(all.size() - 1);
                return last.isDisplayed() ? last : null;
            });
        } catch (TimeoutException te) {
            return null;
        }

        // Get human text (prefer content/message slots)
        String text = null;
        try {
            WebElement content = container.findElement(
                    By.xpath(".//*[contains(@class,'-content') or contains(@class,'-message')][normalize-space()]")
            );
            text = content.getText();
        } catch (NoSuchElementException ignore) {
            text = container.getText();
        }
        if (text != null) text = text.trim();
        return (text == null || text.isBlank()) ? null : text;
    }








    /** Waits for an ERROR toast/alert (AntD alert/message/notification) and returns its text. */
    public String waitForErrorToast() {
        WebDriverWait wdw = new WebDriverWait(driver, java.time.Duration.ofSeconds(12));

        // 1) Prefer explicit AntD error ALERT (role=alert + .ant-alert-error)
        By antAlertError = By.xpath(
                "(" +
                        "//*[contains(@class,'ant-alert') and contains(@class,'ant-alert-error') and @role='alert' and " +
                        "  not(contains(@style,'display: none'))" +
                        "]" +
                        ")[last()]"
        );

        // 2) Fallbacks: message / notification "notices" (may be styled as error)
        By antMessageNotice = By.xpath(
                "(" +
                        "//*[contains(@class,'ant-message') and not(contains(@style,'display: none'))]" +
                        "//*[contains(@class,'ant-message-notice') and not(contains(@style,'display: none'))]" +
                        ")[last()]"
        );
        By antNotificationNotice = By.xpath(
                "(" +
                        "//*[contains(@class,'ant-notification') and not(contains(@style,'display: none'))]" +
                        "//*[contains(@class,'ant-notification-notice') and not(contains(@style,'display: none'))]" +
                        ")[last()]"
        );

        WebElement container;
        try {
            container = wdw.until(drv -> {
                // Prefer explicit error alert first
                java.util.List<WebElement> alerts = drv.findElements(antAlertError);
                if (!alerts.isEmpty()) {
                    WebElement last = alerts.get(alerts.size() - 1);
                    if (last.isDisplayed()) return last;
                }
                // Then message notice
                java.util.List<WebElement> msgs = drv.findElements(antMessageNotice);
                if (!msgs.isEmpty()) {
                    WebElement last = msgs.get(msgs.size() - 1);
                    if (last.isDisplayed()) return last;
                }
                // Then notification notice
                java.util.List<WebElement> notifs = drv.findElements(antNotificationNotice);
                if (!notifs.isEmpty()) {
                    WebElement last = notifs.get(notifs.size() - 1);
                    if (last.isDisplayed()) return last;
                }
                return null; // keep waiting
            });
        } catch (org.openqa.selenium.TimeoutException te) {
            return null;
        }

        // Extract readable text
        String text = null;
        try {
            // AntD Alert: message is inside .ant-alert-message
            java.util.List<WebElement> msg = container.findElements(By.cssSelector(".ant-alert-message"));
            if (!msg.isEmpty()) text = msg.get(0).getText();
        } catch (org.openqa.selenium.NoSuchElementException ignore) {}

        if (text == null || text.isBlank()) {
            // Generic content/message slot (works for message/notification too)
            try {
                WebElement content = container.findElement(
                        By.xpath(".//*[contains(@class,'-content') or contains(@class,'-message')][normalize-space()]")
                );
                text = content.getText();
            } catch (org.openqa.selenium.NoSuchElementException ignore) {
                text = container.getText();
            }
        }

        if (text != null) text = text.trim();
        return (text == null || text.isBlank()) ? null : text;
    }






    /** Dismisses the last visible AntD error alert if present. */
    public void closeErrorAlertIfPresent() {
        try {
            WebElement alert = driver.findElement(By.xpath(
                    "(" +
                            "//*[contains(@class,'ant-alert') and contains(@class,'ant-alert-error') and @role='alert' and " +
                            "  not(contains(@style,'display: none'))" +
                            "]" +
                            ")[last()]"
            ));
            java.util.List<WebElement> close = alert.findElements(By.cssSelector(".ant-alert-close-icon, .anticon-close"));
            if (!close.isEmpty() && close.get(0).isDisplayed()) {
                close.get(0).click();
            }
        } catch (org.openqa.selenium.NoSuchElementException ignore) { }
    }



    public void clickModalCloseIcon() {
        click(closeModalButton());
        waitForModalToClose();
    }


    /** Clicks “Edit info” in the currently open row-actions menu. */
    public void clickEditInfoInOpenMenu() {
        By item = By.xpath("(" +
                "(//*[@role='menu'] | //*[contains(@class,'ant-dropdown')])" +
                "//*[@role='menuitem' or self::li or self::button or self::div or self::span]" +
                "[normalize-space()='Edit info' and not(ancestor-or-self::*[@aria-hidden='true' or contains(@style,'display: none')])]" +
                ")[last()]");
        click(item);
    }

    /** Clicks “Remove user” in the currently open row-actions menu. */
    public void clickRemoveUserInOpenMenu() {
        By item = By.xpath("(" +
                "(//*[@role='menu'] | //*[contains(@class,'ant-dropdown')])" +
                "//*[@role='menuitem' or self::li or self::button or self::div or self::span]" +
                "[normalize-space()='Remove user' and not(ancestor-or-self::*[@aria-hidden='true' or contains(@style,'display: none')])]" +
                ")[last()]");
        click(item);
    }


    /** Reads the Edit info modal fields: firstName, lastName, email. */
    public java.util.Map<String,String> readEditInfoFields() {
        org.openqa.selenium.WebElement modal = waitForSendReminderModal();
        java.util.Map<String,String> vals = new java.util.HashMap<>();

        // First name
        java.util.List<org.openqa.selenium.WebElement> fn = modal.findElements(org.openqa.selenium.By.xpath(
                ".//input[" +
                        " (@type='text' or @type='search')" +
                        " and (contains(translate(@id,'FIRST','first'),'first') " +
                        "      or contains(translate(@name,'FIRST','first'),'first') " +
                        "      or contains(translate(@placeholder,'FIRST','first'),'first'))" +
                        "]"
        ));
        // Fallback via label 'First name'
        if (fn.isEmpty()) {
            fn = modal.findElements(org.openqa.selenium.By.xpath(
                    ".//*[self::label or self::div][normalize-space()='First name']" +
                            "/following::*[self::input][1]"
            ));
        }
        vals.put("firstName", fn.isEmpty() ? "" : safeAttr(fn.get(0), "value"));

        // Last name
        java.util.List<org.openqa.selenium.WebElement> ln = modal.findElements(org.openqa.selenium.By.xpath(
                ".//input[" +
                        " (@type='text' or @type='search')" +
                        " and (contains(translate(@id,'LAST','last'),'last') " +
                        "      or contains(translate(@name,'LAST','last'),'last') " +
                        "      or contains(translate(@placeholder,'LAST','last'),'last'))" +
                        "]"
        ));
        if (ln.isEmpty()) {
            ln = modal.findElements(org.openqa.selenium.By.xpath(
                    ".//*[self::label or self::div][normalize-space()='Last name']" +
                            "/following::*[self::input][1]"
            ));
        }
        vals.put("lastName", ln.isEmpty() ? "" : safeAttr(ln.get(0), "value"));

        // Email
        java.util.List<org.openqa.selenium.WebElement> em = modal.findElements(org.openqa.selenium.By.xpath(
                ".//input[" +
                        " @type='email' or " +
                        " contains(translate(@id,'EMAIL','email'),'email') or " +
                        " contains(translate(@name,'EMAIL','email'),'email') or " +
                        " contains(translate(@placeholder,'EMAIL','email'),'email')" +
                        "]"
        ));
        if (em.isEmpty()) {
            em = modal.findElements(org.openqa.selenium.By.xpath(
                    ".//*[self::label or self::div][normalize-space()='Email']" +
                            "/following::*[self::input][1]"
            ));
        }
        vals.put("email", em.isEmpty() ? "" : safeAttr(em.get(0), "value"));

        return vals;
    }



    /** Returns the 'Complete Name' cell text for the row that has the given email on Individuals. */
    public String getNameByEmail(String email) {
        Objects.requireNonNull(email, "email must not be null");
        String target = email.trim();
        if (target.isEmpty()) return "";

        // 1) Try exact-match row by email (normalized text in a <td>)
        By rowExact = By.xpath(
                "(" +
                        "//*[contains(@class,'ant-table')]" +
                        "//*[self::tbody or contains(@class,'ant-table-tbody')]" +
                        "//tr[contains(@class,'ant-table-row')]" +
                        "[.//td[normalize-space(.)='" + target + "']]" +
                        ")[1]"
        );

        WebElement row = null;
        try {
            row = new WebDriverWait(driver, java.time.Duration.ofSeconds(8))
                    .until(ExpectedConditions.visibilityOfElementLocated(rowExact));
        } catch (org.openqa.selenium.TimeoutException ignore) {
            // 2) Fallback: tolerate wrapping/spans or extra whitespace in the email cell
            By rowLoose = By.xpath(
                    "(" +
                            "//*[contains(@class,'ant-table')]" +
                            "//*[self::tbody or contains(@class,'ant-table-tbody')]" +
                            "//tr[contains(@class,'ant-table-row')]" +
                            "[.//td[.//*[normalize-space(text())='" + target + "'] or contains(normalize-space(.),'" + target + "')]]" +
                            ")[1]"
            );
            try {
                row = new WebDriverWait(driver, java.time.Duration.ofSeconds(6))
                        .until(ExpectedConditions.visibilityOfElementLocated(rowLoose));
            } catch (org.openqa.selenium.TimeoutException te) {
                return ""; // not found on current page
            }
        }

        // Bring row into view (helps when virtualized/partially hidden)
        try {
            ((org.openqa.selenium.JavascriptExecutor) driver)
                    .executeScript("arguments[0].scrollIntoView({block:'center',inline:'nearest'});", row);
        } catch (Throwable ignore) {}

        // 3) Read first column (Name). AntD tables render cells as <td>.
        try {
            WebElement nameCell = row.findElement(By.xpath("./td[1]"));
            String text = nameCell.getText();
            return text == null ? "" : text.trim();
        } catch (org.openqa.selenium.NoSuchElementException e) {
            // Fallback: any first visible cell-like descendant
            try {
                WebElement nameCell = row.findElement(By.xpath("(./td | ./div)[1]"));
                String text = nameCell.getText();
                return text == null ? "" : text.trim();
            } catch (org.openqa.selenium.NoSuchElementException e2) {
                return "";
            }
        }
    }


    /** Returns true if the Edit info modal shows both given buttons by text. */
    public boolean editInfoModalHasButtons(String cancelText, String saveText) {
        org.openqa.selenium.WebElement modal = waitForSendReminderModal();
        boolean hasCancel = !modal.findElements(org.openqa.selenium.By.xpath(".//button[normalize-space()='" + cancelText + "']")).isEmpty();
        boolean hasSave   = !modal.findElements(org.openqa.selenium.By.xpath(".//button[normalize-space()='" + saveText   + "']")).isEmpty();
        return hasCancel && hasSave;
    }


    /** Sets the Email field in the Edit info modal (tolerant: by type=email or label 'Email'). */
    public void setEditInfoEmail(String value) {
        WebElement modal = waitForSendReminderModal(); // your generic "last visible modal" waiter
        WebDriverWait w = new WebDriverWait(driver, java.time.Duration.ofSeconds(6));

        // Prefer the <input type="email">; fallback to label-based
        List<By> locs = List.of(
                By.xpath(".//input[@type='email']"),
                By.xpath(".//input[contains(translate(@id,'EMAIL','email'),'email') or " +
                        "          contains(translate(@name,'EMAIL','email'),'email') or " +
                        "          contains(translate(@placeholder,'EMAIL','email'),'email')]"),
                By.xpath(".//*[self::label or self::div][normalize-space()='Email']/following::input[1]")
        );

        WebElement field = locs.stream().map(modal::findElements).filter(found -> !found.isEmpty()).findFirst().map(found -> found.get(0)).orElse(null);
        if (field == null) throw new NoSuchElementException("Email field not found in Edit info modal.");

        w.until(d -> field.isDisplayed() && field.isEnabled());
        field.clear();
        field.sendKeys(value);
    }

    /** Clicks the 'Save changes' button in the Edit info modal. */
    public void clickEditInfoSaveChanges() {
        WebElement modal = waitForSendReminderModal();
        WebDriverWait w = new WebDriverWait(driver, java.time.Duration.ofSeconds(6));

        // Text-first; fallback to the last button in footer (AntD primary)
        List<By> locs = List.of(
                By.xpath(".//button[normalize-space()='Save changes']"),
                By.xpath(".//*[contains(@class,'ant-modal-footer')]//button[last()]")
        );
        WebElement btn = locs.stream().map(modal::findElements).filter(found -> !found.isEmpty()).findFirst().map(found -> found.get(0)).orElse(null);
        if (btn == null) throw new NoSuchElementException("'Save changes' button not found in Edit info modal.");

        w.until(d -> btn.isDisplayed() && btn.isEnabled());
        btn.click();
    }




    /** Moves focus away from the Email field to trigger client-side validation. */
    public void blurEditInfoEmail() {
        WebElement modal = waitForSendReminderModal();
        // Click the modal title area or any safe non-input element to blur
        List<WebElement> safe = modal.findElements(By.cssSelector(".ant-modal-title, .ant-modal-content"));
        WebElement target = safe.isEmpty() ? modal : safe.get(0);
        target.click();
        // tiny pause to allow validation render (or rely on waits below)
        try { Thread.sleep(150); } catch (InterruptedException ignored) {}
    }

    /** Returns true if the Edit info 'Save changes' button is enabled. */
    public boolean isEditInfoSaveEnabled() {
        WebElement modal = waitForSendReminderModal();
        // Prefer explicit text; fallback to primary button in footer
        List<By> locators = List.of(
                By.xpath(".//button[normalize-space()='Save changes']"),
                By.xpath(".//*[contains(@class,'ant-modal-footer')]//button[last()]")
        );
        WebElement btn = null;
        for (By loc : locators) {
            List<WebElement> found = modal.findElements(loc);
            if (!found.isEmpty()) { btn = found.get(0); break; }
        }
        if (btn == null) throw new NoSuchElementException("'Save changes' button not found in Edit info modal.");
        // Some UIs add disabled attribute or aria-disabled or class flags
        String disabled = btn.getAttribute("disabled");
        String ariaDis  = btn.getAttribute("aria-disabled");
        String cls      = btn.getAttribute("class");
        boolean classDisabled = cls != null && (cls.contains("disabled") || cls.contains("ant-btn-loading"));
        return btn.isDisplayed() && btn.isEnabled() && disabled == null && !"true".equalsIgnoreCase(ariaDis) && !classDisabled;
    }



    /** Returns the validation text shown for the Email field ("" if none). */
    /** Returns the inline validation text for the Email field in Edit info ("" if none). */
    public String getEditEmailValidationText() {
        WebElement modal = waitForSendReminderModal();

        // 1) Locate the email input (reuse your tolerant patterns)
        List<By> emailLocs = List.of(
                By.xpath(".//input[@type='email']"),
                By.xpath(".//input[contains(translate(@id,'EMAIL','email'),'email') or " +
                        "          contains(translate(@name,'EMAIL','email'),'email') or " +
                        "          contains(translate(@placeholder,'EMAIL','email'),'email')]"),
                By.xpath(".//*[self::label or self::div][normalize-space()='Email' or normalize-space()='Email*']/following::input[1]")
        );

        WebElement emailField = emailLocs.stream().map(modal::findElements).filter(found -> !found.isEmpty()).findFirst().map(found -> found.get(0)).orElse(null);
        if (emailField == null) return "";

        // 2) Define a *strict* container for just this field (nearest wrapper that owns the input)
        //    Then search only within that block for an error span/div — never in modal footer.
        String errorWithinField =
                "ancestor::*[(self::div or self::label or self::td) " +
                        "  and not(ancestor::*[contains(@class,'ant-modal-footer')])][1]" +
                        "//*[ (self::span or self::div) " +
                        "   and ( @type='error' " + // your screenshot pattern
                        "         or contains(translate(@class,'ERROR','error'),'error') " +
                        "         or contains(translate(@class,'INVALID','invalid'),'invalid') " +
                        "         or contains(translate(@class,'HELP','help'),'help') ) " +
                        "   and normalize-space() ]";

        // First choice: error inside same field container
        WebDriverWait wdw = new WebDriverWait(driver, java.time.Duration.ofSeconds(4));
        try {
            WebElement err = wdw.until(drv -> {
                List<WebElement> errs = emailField.findElements(By.xpath(errorWithinField));
                for (WebElement e : errs) {
                    if (e.isDisplayed()) return e;
                }
                // Secondary: immediate following sibling under same parent (common in your DOM)
                List<WebElement> sib = emailField.findElements(By.xpath(
                        "parent::*[not(ancestor::*[contains(@class,'ant-modal-footer')])]/" +
                                "following-sibling::*[(self::span or self::div) " +
                                "  and (@type='error' or contains(translate(@class,'ERROR','error'),'error')) " +
                                "  and normalize-space()][1]"
                ));
                for (WebElement e : sib) {
                    if (e.isDisplayed()) return e;
                }
                return null; // keep waiting
            });;
            String text = err.getText();
            return text == null ? "" : text.trim();
        } catch (org.openqa.selenium.TimeoutException te) {
            return ""; // no inline error visible
        }
    }






    /** Opens actions → clicks 'Remove user' → waits for the confirmation modal to be visible. */
    public IndividualsPage openRemoveUserModalFor(String email) {
        if (!openActionsMenuFor(email)) { openActionsFor(email); }
        clickRemoveUserInOpenMenu();
        waitForRemoveUserModal();
        return this;
    }


    // ===== Locators (scoped to the visible "Remove Member" modal content) =====
    private static final By REMOVE_MODAL_CONTENT = By.xpath(
            "(//div[contains(@class,'ant-modal-content')][.//h3[normalize-space(.)='Remove Member']])[last()]"
    );
    private static final By REMOVE_MODAL_TITLE_H3 = By.xpath(".//h3[normalize-space()]");
    private static final By REMOVE_MODAL_TITLE_FALLBACK = By.cssSelector(".ant-modal-title, [id$='title']");
    private static final By REMOVE_MODAL_CANCEL_BTN = By.xpath(".//div[contains(@class,'ant-modal-body')]//button[normalize-space()='Cancel']");
    private static final By REMOVE_MODAL_REMOVE_BTN = By.xpath(".//div[contains(@class,'ant-modal-body')]//button[normalize-space()='Remove']");

    /** Waits specifically for the 'Remove Member' modal to be visible and returns its content root (.ant-modal-content). */
    public WebElement waitForRemoveUserModal() {
        WebDriverWait wdw = new WebDriverWait(driver, java.time.Duration.ofSeconds(10));
        return wdw.until(org.openqa.selenium.support.ui.ExpectedConditions.visibilityOfElementLocated(REMOVE_MODAL_CONTENT));
    }

    /** Returns the visible Remove modal title text. */
    public String getRemoveUserModalTitle() {
        WebElement modal = waitForRemoveUserModal();
        // Prefer the <h3> (matches your HTML), then fall back to ant-modal-title if present.
        try {
            WebElement h3 = modal.findElement(REMOVE_MODAL_TITLE_H3);
            String t = h3.getText();
            return t == null ? "" : t.trim();
        } catch (org.openqa.selenium.NoSuchElementException e) {
            try {
                WebElement title = modal.findElement(REMOVE_MODAL_TITLE_FALLBACK);
                String t = title.getText();
                return t == null ? "" : t.trim();
            } catch (org.openqa.selenium.NoSuchElementException ignore) {
                return "";
            }
        }
    }

    /** Validates that the Remove modal shows the expected buttons by text (in the modal BODY, not the footer). */
    public boolean removeUserModalHasButtons(String cancelText, String removeText) {
        WebElement modal = waitForRemoveUserModal();
        boolean hasCancel = !modal.findElements(By.xpath(".//div[contains(@class,'ant-modal-body')]//button[normalize-space()='" + cancelText + "']")).isEmpty();
        boolean hasRemove = !modal.findElements(By.xpath(".//div[contains(@class,'ant-modal-body')]//button[normalize-space()='" + removeText + "']")).isEmpty();
        return hasCancel && hasRemove;
    }

    /** Clicks 'Cancel' in the Remove modal (body buttons). */
    public void clickRemoveCancel() {
        WebElement modal = waitForRemoveUserModal();
        WebDriverWait wdw = new WebDriverWait(driver, java.time.Duration.ofSeconds(8));
        WebElement btn = wdw.until(org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable(modal.findElement(REMOVE_MODAL_CANCEL_BTN)));
        btn.click();
    }

    /** Clicks 'Remove' (confirm) in the Remove modal (body buttons). */
    public void clickRemoveConfirm() {
        WebElement modal = waitForRemoveUserModal();
        WebDriverWait wdw = new WebDriverWait(driver, java.time.Duration.ofSeconds(8));
        WebElement btn = wdw.until(org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable(modal.findElement(REMOVE_MODAL_REMOVE_BTN)));
        btn.click();
    }

    /** Returns the email from the first visible row. Throws if no rows or empty cell. */
    public String getFirstRowEmailOrThrow() {
        // Ensure table is rendered
        wdw.until(d -> !driver.findElements(tableRows).isEmpty());
        java.util.List<WebElement> rows = driver.findElements(tableRows);
        if (rows.isEmpty()) throw new NoSuchElementException("No rows found in Individuals table.");

        String email = safeText(() -> emailCellInRow(rows.get(0)).getText()).trim();
        if (email.isEmpty()) throw new NoSuchElementException("First row email cell is empty.");
        return email;
    }

    /** True if the user row exists anywhere (handles pagination via existing helper). */
    public boolean isRowPresent(String email) {
        return isUserListedByEmail(email);
    }

    /** Waits until the row for the given email disappears (any page). */
    public boolean waitRowToDisappear(String email, java.time.Duration timeout) {
        try {
            new WebDriverWait(driver, timeout)
                    .until(d -> {
                        try { return !isUserListedByEmail(email); }
                        catch (org.openqa.selenium.StaleElementReferenceException ignored) { return false; }
                    });
            return true;
        } catch (org.openqa.selenium.TimeoutException e) {
            return false;
        }
    }





    // Take a small snapshot of the table so we can detect change after clicking next/prev.
    private String tableSignature() {
        // Use whatever you already have for rows; falls back to common AntD bodies.
        List<WebElement> rows = driver.findElements(
                By.cssSelector(".ant-table-tbody tr, .ant-table .ant-table-row")
        );

        StringBuilder sb = new StringBuilder();
        int take = Math.min(rows.size(), 5);
        for (int i = 0; i < take; i++) {
            String txt = rows.get(i).getText();
            if (txt != null) sb.append(txt.trim()).append("|");
        }
        sb.append("#").append(rows.size()); // include count
        return sb.toString();
    }

    /** Waits until the table signature actually changes (or spinner ends). Never throws; returns true if changed. */
// Never throws. True = table refreshed; false = no change / timed out.
    public boolean waitForTableRefreshedSafe() {
        try {
            waitForTableRefreshed();   // your existing method
            return true;
        } catch (org.openqa.selenium.TimeoutException ignore) {
            return false;
        }
    }

    /** Try go next; never throws. Returns true only if a refresh actually happened. */
    public boolean goToNextPageIfPossibleSafe() {
        try {
            // reuse your existing next-page method; only swallow its refresh timeout
            boolean clicked = goToNextPageIfPossible(); // if this method itself can throw, wrap below
            // if your goToNextPageIfPossible() doesn't call wait, do:
            // boolean clicked = clickNextPageIfEnabled(); return clicked && waitForTableRefreshedSafe();

            // if it returned true but no refresh was detected (rare), normalize to false
            if (!waitForTableRefreshedSafe()) return false;
            return clicked;
        } catch (org.openqa.selenium.TimeoutException ignore) {
            return false;
        }
    }



    /** Chequeo sólo en la página actual usando helpers existentes. */
    private boolean isEmailOnCurrentPage(String email) {
        try {
            java.util.List<WebElement> rows = driver.findElements(tableRows);
            for (WebElement r : rows) {
                try {
                    WebElement cell = emailCellInRow(r); // helper existente
                    if (cell == null) continue;
                    String t = cell.getText();
                    if (email.equals((t == null) ? "" : t.trim())) return true;
                } catch (org.openqa.selenium.StaleElementReferenceException ignored) {
                    // fila reflowed; seguimos
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }




}
