package pages.teams;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.BasePage;
import pages.reports.ReportSummaryPage;


import java.time.Duration;
import java.util.List;

import static base.BaseTest.logger;

public class TeamDetailsPage extends BasePage {





    // ===== Existing locators =====

    // A simple anchor anywhere in the page that points to a TTP report
    private static final By TTP_REPORT_LINKS =
            By.cssSelector("a[href*='/assess/ttp/']");

    private final By teamNameHeader =
            By.xpath("//h1[contains(.,'Team') or contains(.,'Climate') or contains(.,'Overview')]");

    // Matches "ORG A / Team 1 (17)" or any "xxx / Team y (n)" breadcrumb/title
    private final By teamHeader =
            By.xpath("//h1[contains(normalize-space(), ' / ')]");

    // Matches the "Add Team Member +" button
    private final By addTeamMemberButton =
            By.xpath("//button[contains(normalize-space(),'Add Team Member')]");

    // Matches the "Complete Name" table header
    private final By completeNameHeader =
            By.xpath("//*[normalize-space()='Complete Name']");

    // ===== Kite graph / Analytics locators =====

    // Card title / wrapper near the wheel
    private static final By ANALYTICS_HEADER =
            By.xpath("//div[@class='sc-7aa73bb-1 coYWcg']");

    // The big wheel / kite graph container SVG
    private static final By KITE_GRAPH_CARD =
            By.xpath("//div[@id='AGT-Container']//*[name()='svg']");

    // Nodes in the wheel
    private static final By KITE_NODES =
            By.cssSelector("svg path[id][class$='Arc']");

    // Side panel selected member name
    private static final By SELECTED_MEMBER_NAME =
            By.xpath("(//h2[@class='sc-3d430a05-16 etUKww'])[1]");

    // Optional: selected member Tilt label (not used in SM12 but handy)
    private static final By SELECTED_MEMBER_TILT =
            By.xpath("(//div[@class='sc-3d430a05-14 ePuJja'])[1]");

    private static final By PROFILE_INSIGHT_TEXT =
            By.xpath("(//p[@class='sc-3d430a05-20 hWKDSl'])[1]");




    // Members table locators
    private static final By MEMBERS_TABLE_BODY =
            By.cssSelector(".ant-table-wrapper .ant-table-tbody");
    private static final By MEMBER_ROWS =
            By.cssSelector(".ant-table-wrapper .ant-table-tbody > tr.ant-table-row");
    private static final By NO_DATA_PLACEHOLDER =
            By.xpath("//*[contains(@class,'ant-empty') or contains(@class,'ant-table-placeholder')]");



    public TeamDetailsPage(WebDriver driver) {
        super(driver);
    }

    private boolean exists(By locator) {
        return !driver.findElements(locator).isEmpty();
    }

    // ===== Page readiness =====

    @Override
    public TeamDetailsPage waitUntilLoaded() {
        try {
            // Wait until URL is /dashboard/teams/{id}
            waitForUrlContains("/dashboard/teams/");

            // Wait until the main table header is visible
            waitForElementVisible(completeNameHeader);

            logger.info("[TeamDetailsPage] waitUntilLoaded OK. url={}", driver.getCurrentUrl());
        } catch (TimeoutException e) {
            logger.error("[TeamDetailsPage] waitUntilLoaded timeout. url={} msg={}",
                    driver.getCurrentUrl(), e.getMessage());
        }
        return this;
    }

    public boolean isLoaded() {
        try {
            boolean urlOk = driver.getCurrentUrl().contains("/dashboard/teams/");
            boolean headerVisible = exists(teamHeader);
            boolean tableHeaderVisible = exists(completeNameHeader);

            logger.info("[TeamDetailsPage] isLoaded? urlOk={} headerVisible={} tableHeaderVisible={}",
                    urlOk, headerVisible, tableHeaderVisible);

            return urlOk && headerVisible && tableHeaderVisible;
        } catch (Exception e) {
            logger.warn("[TeamDetailsPage] isLoaded() returned false. url={}", driver.getCurrentUrl(), e);
            return false;
        }
    }

    // ===== TTP helpers =====

    /** Returns true if this team details page has at least one TTP aggregate report link. */
    public boolean hasCompletedTrueTiltAggregate() {
        return !driver.findElements(TTP_REPORT_LINKS).isEmpty();
    }

    /** Opens the first TTP aggregate report found and returns the ReportSummaryPage. */
    public ReportSummaryPage openFirstCompletedTrueTiltAggregate() {
        List<WebElement> links = driver.findElements(TTP_REPORT_LINKS);
        if (links.isEmpty()) {
            throw new NoSuchElementException("No TTP aggregate report links found on Team Details page.");
        }
        WebElement link = links.get(0);

        try {
            ((JavascriptExecutor) driver)
                    .executeScript("arguments[0].setAttribute('target','_self');", link);
        } catch (Exception ignored) {}

        link.click();
        return new ReportSummaryPage(driver).waitUntilLoaded();
    }

    // ===== Analytics / Kite graph helpers (for SM12) =====

    /**
     * Opens the "Analytics"/"Climate" tab if present, then scrolls the Kite graph into view.
     * Safe to call even if you're already on the right tab.
     */
    public void openClimateTab() {
        By analyticsOrClimateTab = By.xpath(
                "//button[normalize-space()='Analytics' or normalize-space()='Climate']" +
                        " | //a[normalize-space()='Analytics' or normalize-space()='Climate']"
        );

        try {
            WebElement tab = new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.elementToBeClickable(analyticsOrClimateTab));
            tab.click();
            logger.info("[TeamDetailsPage] Clicked Analytics/Climate tab");
        } catch (TimeoutException | NoSuchElementException e) {
            logger.info("[TeamDetailsPage] Analytics/Climate tab not found; assuming already on Analytics. {}", e.toString());
        }

        // Best-effort scroll to the Analytics section
        try {
            WebElement header = driver.findElement(ANALYTICS_HEADER);
            ((JavascriptExecutor) driver)
                    .executeScript("arguments[0].scrollIntoView({block:'center'});", header);
        } catch (Exception ignored) {}
    }

    /** Waits for the Analytics section and Kite graph SVG to be present/visible. */
    public void waitForKiteGraphLoaded() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        wait.until(ExpectedConditions.visibilityOfElementLocated(ANALYTICS_HEADER));
        wait.until(ExpectedConditions.presenceOfElementLocated(KITE_GRAPH_CARD));
        logger.info("[TeamDetailsPage] Kite graph appears loaded.");
    }

    /** Returns true if the Kite graph SVG is visible on the page. */
    public boolean isKiteGraphVisible() {
        try {
            WebElement svg = driver.findElement(KITE_GRAPH_CARD);
            return svg.isDisplayed();
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    /** Returns how many clickable nodes (paths) we see in the Kite graph. */
    public int getKiteGraphNodeCount() {
        int count = driver.findElements(KITE_NODES).size();
        logger.info("[TeamDetailsPage] getKiteGraphNodeCount = {}", count);
        return count;
    }

    /**
     * Clicks a Kite node by 1-based index and waits briefly for the side panel
     * to update its selected name.
     */
    public void clickKiteNodeByIndex(int index) {
        List<WebElement> nodes = driver.findElements(KITE_NODES);
        if (nodes.isEmpty()) {
            throw new NoSuchElementException("No Kite nodes found in graph.");
        }
        if (index < 1 || index > nodes.size()) {
            throw new IllegalArgumentException(
                    "Kite node index out of bounds: " + index + " (nodes=" + nodes.size() + ")");
        }

        String before = safeGetText(SELECTED_MEMBER_NAME);

        WebElement node = nodes.get(index - 1);
        ((JavascriptExecutor) driver)
                .executeScript("arguments[0].scrollIntoView({block:'center'});", node);
        node.click();
        logger.info("[TeamDetailsPage] Clicked Kite node index {}", index);

        // Wait until side panel name is non-empty and (ideally) changed
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        try {
            wait.until(d -> {
                String after = safeGetText(SELECTED_MEMBER_NAME);
                if (after == null || after.isBlank()) return false;
                if (before == null || before.isBlank()) return true;
                return !after.trim().equalsIgnoreCase(before.trim());
            });
        } catch (TimeoutException e) {
            logger.warn("[TeamDetailsPage] Side panel name did not visibly update after clicking Kite node index {}", index);
        }
    }

    /** Returns the currently selected member name from the side panel (may be null/blank). */
    public String getKiteSidePanelSelectedName() {
        String text = safeGetText(SELECTED_MEMBER_NAME);
        logger.info("[TeamDetailsPage] getKiteSidePanelSelectedName='{}'", text);
        return text;
    }

    // Optional helper if you ever want the Tilt text:
    public String getKiteSidePanelSelectedTilt() {
        return safeGetText(SELECTED_MEMBER_TILT);
    }

    // --- private utility ---

    private String safeGetText(By locator) {
        try {
            WebElement el = driver.findElement(locator);
            return el.getText();
        } catch (NoSuchElementException e) {
            return null;
        }
    }




    // ---------- member assertion helpers ----------
    /**
     * Row locator by email – anchored on the <tbody class="ant-table-tbody">,
     * because <table> has no 'ant-table' class.
     */
    private By memberRowByEmail(String email) {
        String safe = email.replace("'", "\\'");
        return By.xpath(
                "//tbody[contains(@class,'ant-table-tbody')]" +
                        "//tr[" +
                        " .//td[contains(normalize-space(),'" + safe + "')]" +
                        " or .//a[contains(normalize-space(),'" + safe + "')]" +
                        " or .//*[contains(normalize-space(),'" + safe + "')]" +
                        "]"
        );
    }

    public void waitForMemberByEmail(String email, Duration timeout) {
        WebDriverWait wait = new WebDriverWait(driver, timeout);
        By locator = memberRowByEmail(email);

        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
            logger.info("[TeamDetailsPage] Member with email '{}' is visible in the table.", email);
        } catch (TimeoutException e) {
            // Debug dump on failure
            logger.error("[TeamDetailsPage] Member with email '{}' NOT found in table within {}s",
                    email, timeout.toSeconds());
            dumpMemberTableForDebug();
            throw e;
        }
    }

    private void dumpMemberTableForDebug() {
        try {
            List<WebElement> rows = driver.findElements(MEMBER_ROWS);
            logger.info("[TeamDetailsPage] Dumping member rows ({} rows):", rows.size());
            for (WebElement row : rows) {
                logger.info("  ROW TEXT => '{}'", row.getText().replace("\n", " | "));
            }
        } catch (Exception ex) {
            logger.warn("[TeamDetailsPage] Failed to dump member table: {}", ex.toString());
        }
    }


}
