package pages.menuPages;

import Utils.Config;
import api.ApiConfig;
import api.BackendApi;
import io.qameta.allure.Step;
import okhttp3.ResponseBody;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import pages.BasePage;
import retrofit2.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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



    private final By emptyState = By.cssSelector(".ant-table .ant-empty, .ant-empty-description, .ant-table-placeholder");






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
        // rows visible OR empty state visible
        wdw.until(d -> !d.findElements(tableRows).isEmpty() || !d.findElements(emptyState).isEmpty());
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
    public void assertAppearsWithEvidence(String baseUrl, String email) {
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
