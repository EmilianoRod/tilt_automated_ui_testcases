package pages.teams;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import pages.BasePage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TeamClimatePage extends BasePage {


    private static final String TILT_IMPACT = "impact";
    private static final String TILT_CLARITY = "clarity";
    private static final String TILT_CONNECTION = "connection";
    private static final String TILT_STRUCTURE = "structure";


    


    // --- Locators ---

    // Header that contains "Analytics" in the title
    private static final By ANALYTICS_HEADER =
            By.xpath("//h1[contains(normalize-space(),'Analytics')]");

    // The big wheel / kite graph container (SVG inside AGT-Container)
    private static final By KITE_GRAPH_CARD =
            By.xpath("//div[@id='AGT-Container']//*[name()='svg']");

    // Nodes in the wheel (paths that represent the kite arcs)
    private static final By KITE_NODES =
            By.cssSelector("#AGT-Container svg path[id][class$='Arc']");

    // Selected member name – heading at the top of the side panel
    private static final By SELECTED_MEMBER_NAME =
            By.xpath("(//h2[@class='sc-3d430a05-16 etUKww'])[1]");

    // Selected member Tilt label/value (e.g. "Clarity")
    private static final By SELECTED_MEMBER_TILT =
            By.xpath("(//div[@class='sc-3d430a05-14 ePuJja'])[1]");

    // Optional: profile insight text
    private static final By PROFILE_INSIGHT_TEXT =
            By.xpath("(//p[@class='sc-3d430a05-20 hWKDSl'])[1]");

    // Entire line that contains "Tilt Profile: Impact"
    private static final By SELECTED_MEMBER_TILT_LINE =
            By.xpath("//p[contains(@class,'sc-3d430a05-17')]" +
                    "[strong[contains(normalize-space(.),'Tilt Profile')]]");



    // New – robust text-based locator
    private static final By BY_MEMBERS_TAB = By.xpath(
            "//*[normalize-space()='By Members' and " +
                    "(self::button or self::label or self::div)]"
    );
    // Search input in the By Members panel – good anchor that tab is active
    private static final By MEMBER_SEARCH_INPUT =
            By.xpath("//input[@placeholder='Search member']");

    // Generic "members group" root – each tilt block (Impact, Clarity, etc.)
    private By tiltGroupRoot(String tiltKey) {
        String t = tiltKey.toLowerCase(Locale.ROOT).trim(); // impact, clarity, connection, structure
        return By.xpath(
                // Find <div tilt="impact" ...>Impact</div> and go up to its group container
                "//div[@tilt='" + t + "']/ancestor::div[contains(@class,'sc-3d430a05-4')][1]"
        );
    }

    // Header <button> for that tilt group (the row that says "4 members  Impact")
    private By tiltGroupHeaderButton(String tiltKey) {
        String lower = tiltKey.toLowerCase(Locale.ROOT);
        return By.xpath(
                "//div[contains(@class,'sc-3d430a05-4')]" +
                        "[.//div[@tilt='" + lower + "']]" +
                        "//button[contains(@class,'sc-3d430a05-5')]"
        );
    }

    // Member rows inside the expanded group (name spans)
    private By tiltGroupMemberNameSpans(String tiltKey) {
        String lower = tiltKey.toLowerCase(Locale.ROOT);
        return By.xpath(
                "//div[contains(@class,'sc-3d430a05-4')]" +
                        "[.//div[@tilt='" + lower + "']]" +
                        "//div[contains(@class,'sc-3d430a05-10')]" +
                        "//span[contains(@class,'sc-3d430a05-11')]"
        );
    }

    // Inside a group, each member row
    private static final By MEMBER_ROW_IN_GROUP =
            By.xpath(".//div[contains(@class,'sc-3d430a05-10')]");

    // Inside a member row, the name text
    private static final By MEMBER_NAME_IN_ROW =
            By.xpath(".//span[contains(@class,'sc-3d430a05-11') and normalize-space()]");


    // Close button (X) in the selected-member detail card header
    private static final By MEMBER_DETAIL_CLOSE_BUTTON =
            By.xpath("//*[name()='rect' and contains(@width,'21')]");


    // Root of the member detail card (right panel when one member is selected)
    private static final By MEMBER_DETAIL_ROOT =
            By.cssSelector("div.sc-3d430a05-13");

    // The clickable “X” icon inside that card
    private static final By MEMBER_DETAIL_CLOSE_BUTTON2 =
            By.cssSelector("div.sc-3d430a05-13 svg");




    private final WebDriverWait wait20;
    private final WebDriverWait wait30;

    public TeamClimatePage(WebDriver driver) {
        super(driver);
        this.wait20 = new WebDriverWait(driver, Duration.ofSeconds(20));
        this.wait30 = new WebDriverWait(driver, Duration.ofSeconds(30));
    }

    // ===== Page readiness =====

    @Override
    public TeamClimatePage waitUntilLoaded() {
        // Use BasePage helpers if you have them
        try {
            wait.waitForDocumentReady();
        } catch (Throwable ignored) {}
        try {
            wait.waitForLoadersToDisappear();
        } catch (Throwable ignored) {}

        // Header + SVG container visible
        wait30.until(ExpectedConditions.visibilityOfElementLocated(ANALYTICS_HEADER));
        wait30.until(ExpectedConditions.visibilityOfElementLocated(KITE_GRAPH_CARD));
        return this;
    }

    /** Explicit wait for the kite graph + nodes to be present. */
    public void waitForKiteGraphLoaded() {
        waitUntilLoaded();
        wait20.until(ExpectedConditions.numberOfElementsToBeMoreThan(KITE_NODES, 0));
    }

    public boolean isKiteGraphVisible() {
        try {
            WebElement card = driver.findElement(KITE_GRAPH_CARD);
            return card.isDisplayed();
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    // ===== Nodes / selection =====

    /** Preferred name used from the test. */
    public int getKiteGraphNodeCount() {
        return driver.findElements(KITE_NODES).size();
    }

    /** Backwards-compat alias (if other code uses it). */
    public int getKiteNodeCount() {
        return getKiteGraphNodeCount();
    }

    /** Preferred name used from the test. */
    public void clickKiteNodeByIndex(int index1Based) {
        // Ensure nodes are present
        List<WebElement> nodes = wait20.until(d -> {
            List<WebElement> list = d.findElements(KITE_NODES);
            return (list.size() >= index1Based) ? list : null;
        });

        String previousName = getSelectedMemberName(); // may be empty for first click

        WebElement target = nodes.get(index1Based - 1);
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center', inline:'center'});", target);

        try {
            target.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", target);
        }

        // Wait until side panel selection actually changes
        waitSelectedMemberChangedFrom(previousName);
    }

    /** Backwards-compat alias (if some tests still call this). */
    public void clickMemberNodeByIndex(int index1Based) {
        clickKiteNodeByIndex(index1Based);
    }

    public String getSelectedMemberName() {
        try {
            return driver.findElement(SELECTED_MEMBER_NAME).getText().trim();
        } catch (NoSuchElementException e) {
            return "";
        }
    }

    public String getKiteSidePanelSelectedName() {
        // alias used by SM12 test
        return getSelectedMemberName();
    }

    public String getSelectedMemberTilt() {
        try {
            WebElement line = waitForElementVisible(SELECTED_MEMBER_TILT_LINE);
            String full = line.getText().trim();          // e.g. "Tilt Profile: Impact"
            // Strip the "Tilt Profile:" prefix (case-insensitive)
            return full.replaceFirst("(?i)^Tilt Profile:\\s*", "").trim();
        } catch (NoSuchElementException e) {
            return "";
        }
    }


    public String getProfileInsightText() {
        try {
            return driver.findElement(PROFILE_INSIGHT_TEXT).getText().trim();
        } catch (NoSuchElementException e) {
            return "";
        }
    }

    /** Wait until the selected member name changes (or becomes non-blank if it was empty). */
    public void waitSelectedMemberChangedFrom(String previousName) {
        wait20.until(d -> {
            String now = getSelectedMemberName();
            if (now == null || now.isBlank()) return false;
            return !now.equals(previousName);
        });
    }




    public void openByMembersView() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // 1) Already in "By Members" LIST mode?
        if (isPresent(MEMBER_SEARCH_INPUT)) {
            return;
        }

        // 2) In "By Members" DETAIL mode? (card with X, but no search input)
        if (isPresent(MEMBER_DETAIL_ROOT)) {
            closeMemberDetailIfOpen();
            wait.until(ExpectedConditions.visibilityOfElementLocated(MEMBER_SEARCH_INPUT));
            return;
        }

        // 3) In "By Dominance" mode → click the "By Members" tab
        WebElement tab = waitForElementClickable(BY_MEMBERS_TAB);
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({block:'center'});", tab);
        } catch (Exception ignored) {}

        try {
            tab.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", tab);
        }

        // Wait for list view (search bar) to appear
        wait.until(ExpectedConditions.visibilityOfElementLocated(MEMBER_SEARCH_INPUT));
    }






    /** Returns the root container for a tilt group (Impact / Clarity / Connection / Structure). */
    private WebElement getTiltGroup(String tiltKey) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        WebElement group = wait.until(
                ExpectedConditions.visibilityOfElementLocated(tiltGroupRoot(tiltKey))
        );

        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({block:'center'});", group);
        } catch (Exception ignored) {}

        return group;
    }


    /** All visible member rows inside a specific tilt group. */
    public List<WebElement> getMembersInTiltGroup(String tiltKey) {
        WebElement group = getTiltGroup(tiltKey);
        List<WebElement> rows = group.findElements(MEMBER_ROW_IN_GROUP);

        List<WebElement> visible = new ArrayList<>();
        for (WebElement row : rows) {
            try {
                if (row.isDisplayed() && !row.getText().trim().isEmpty()) {
                    visible.add(row);
                }
            } catch (StaleElementReferenceException ignored) {
            }
        }
        return visible;
    }

    /** Number of members inside a specific tilt group (e.g. "impact"). */
    public int getMemberCountInTiltGroup(String tiltKey) {
        openByMembersView();           // make sure correct tab is active
        ensureTiltGroupExpanded(tiltKey);

        List<WebElement> members = driver.findElements(tiltGroupMemberNameSpans(tiltKey));
        return members.size();
    }


    /**
     * Clicks the Nth member (0-based) inside the given tilt group and returns the member name
     * as shown in the list.
     */
    public String clickMemberInTiltGroupByIndex(String tiltKey, int indexZeroBased) {
        openByMembersView();
        ensureTiltGroupExpanded(tiltKey);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        List<WebElement> members = wait.until(d -> {
            List<WebElement> list = d.findElements(tiltGroupMemberNameSpans(tiltKey));
            return list.isEmpty() ? null : list;
        });

        if (indexZeroBased < 0 || indexZeroBased >= members.size()) {
            throw new IllegalArgumentException(
                    "Index " + indexZeroBased + " out of range for tilt '" + tiltKey +
                            "' (memberCount=" + members.size() + ")"
            );
        }

        WebElement target = members.get(indexZeroBased);
        String name = target.getText().trim();

        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center'});", target
        );
        try {
            target.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", target);
        }

        return name;
    }




    // Expand a tilt group if its member list is currently empty
    private void ensureTiltGroupExpanded(String tiltKey) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        WebElement root = wait.until(
                ExpectedConditions.visibilityOfElementLocated(tiltGroupRoot(tiltKey))
        );

        List<WebElement> currentMembers =
                root.findElements(By.cssSelector("div.sc-3d430a05-10"));

        boolean hasVisibleMembers = !currentMembers.isEmpty() && currentMembers.get(0).isDisplayed();
        if (hasVisibleMembers) {
            return; // already expanded
        }

        WebElement headerBtn = root.findElement(tiltGroupHeaderButton(tiltKey));
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center'});", headerBtn
        );

        try {
            headerBtn.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", headerBtn);
        }

        // Wait until at least one member row appears
        wait.until(d ->
                !d.findElements(tiltGroupMemberNameSpans(tiltKey)).isEmpty()
        );
    }


    /** Closes the member detail side panel if it is currently visible. */
    /** If a member detail card is open, click its X and wait for it to close. */
    public void closeMemberDetailIfOpen() {
        if (!isPresent(MEMBER_DETAIL_ROOT)) {
            return; // nothing to close
        }

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Wait until the X icon is clickable
        WebElement closeBtn = wait.until(
                ExpectedConditions.elementToBeClickable(MEMBER_DETAIL_CLOSE_BUTTON2)
        );

        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({block:'center'});", closeBtn);
        } catch (Exception ignored) {}

        // Normal Selenium click – no JS fallback here
        closeBtn.click();

        // Wait until the whole detail card disappears
        wait.until(ExpectedConditions.invisibilityOfElementLocated(MEMBER_DETAIL_ROOT));
    }



    /**
     * Asserts that the side panel reflects the expected member.
     */
    public void assertSidePanelMatchesMember(String expectedName, String expectedTiltKey) {
        String panelName = getSelectedMemberName();
        Assert.assertNotNull(panelName, "❌ Side panel name is null");
        Assert.assertFalse(panelName.isBlank(), "❌ Side panel shows empty name");
        Assert.assertTrue(
                panelName.toLowerCase(Locale.ROOT).contains(expectedName.toLowerCase(Locale.ROOT)),
                "❌ Side panel name mismatch. list='" + expectedName + "', panel='" + panelName + "'"
        );

        // Tilt label consistency
        String tiltLabel = getSelectedMemberTilt();
        Assert.assertTrue(
                tiltLabel.equalsIgnoreCase(expectedTiltKey),
                "❌ Side panel tilt label '" + tiltLabel + "' does not match chosen tilt group '" + expectedTiltKey + "'"
        );

        // Insight exists
        String insight = getProfileInsightText();
        Assert.assertFalse(
                insight.isBlank(),
                "❌ Profile Insight text is empty for member '" + panelName + "'"
        );
    }





}
