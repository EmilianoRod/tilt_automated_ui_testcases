package pages.teams;

import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.BasePage;
import pages.reports.ReportSummaryPage;

import java.time.Duration;
import java.util.List;

import static base.BaseTest.logger;

public class TeamDetailsPage extends BasePage {

    private static final String TTP_TITLE = "True Tilt Personality Profile";
    private static final String AGT_TITLE = "Agility Growth Tracker";

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

    // Optional: selected member Tilt label
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

    // ===== Add Team Member modal locators =====

    private static final By MODAL_ROOT = By.xpath("//div[@role='dialog']");
    private static final By MODAL_HEADER_ADD_TEAM_MEMBER = By.xpath("//div[contains(text(),'Add Team Member')]");
    private static final By MODAL_CANCEL_BUTTON1 = By.xpath("(//button[normalize-space()='Cancel'])[1]");
    private static final By MODAL_CANCEL_BUTTON2 = By.xpath("(//button[normalize-space()='Cancel'])[2]");
    private static final By MODAL_CLOSE_BUTTON = By.xpath("//button[@aria-label='Close']");
    private static final By MODAL_CREATE_NEW_USER_BUTTON = By.xpath("//button[normalize-space()='Create New User']");
    private static final By MODAL_SEARCH_EXISTENT_USER_BY_NAME_INPUT_SEARCHBAR =
            By.xpath("//input[@placeholder='Click to write user name']");
    private static final By MODAL_SEARCH_EXISTENT_USER_BY_NAME_LIST_WITH_USER_EXPANDED =
            By.xpath("//body/div/div/div/div[@role='dialog']/div/div/div/div[@direction='column']/div/div[2]");
    private static final By MODAL_DELETE_SEARCH_BUTTON =
            By.xpath("//*[name()='rect' and contains(@width,'21')]");
    private static final By MODAL_FISTNAME_INPUT = By.xpath("//input[@id='firstName']");
    private static final By MODAL_LASTNAME_INPUT = By.xpath("//input[@id='lastName']");
    private static final By MODAL_EMAIL_INPUT = By.xpath("//input[@id='email']");
    private static final By MODAL_PREVIOUS_BUTTON = By.xpath("//button[normalize-space()='Previous']");
    private static final By MODAL_CONTINUE_BUTTON = By.xpath("//button[normalize-space()='Continue']");
    private static final By MODAL_ADD_USER_BUTTON = By.xpath("//button[normalize-space()='Add user']");
    private static final By MODAL_ADD_MEMBER_BUTTON = By.xpath("//button[normalize-space()='Add Member']");

    // Product-selection step
    private static final By MODAL_PRODUCT_CARD_TRUE_TILT =
            By.xpath("//h3[contains(text(),'True Tilt Personality Profile™')]");
    private static final By MODAL_PRODUCT_CARD_AGT =
            By.xpath("//h3[contains(text(),'Agility Growth Tracker™')]");
    private static final By MODAL_CONTINUE_TO_PURCHASE_BUTTON =
            By.xpath("//button[normalize-space()='Continue to purchase']");
    private static final By MODAL_EXISTING_USER_SPINNER =
            By.cssSelector(".ant-spin-spinning, .ant-spin-dot");

    // Generic product card inside the modal
    private static final By PRODUCT_CARD_IN_MODAL = By.xpath(
            "//div[contains(@class,'ant-modal-body')]//div[contains(@class,'sc-d2610f5f-1')]"
    );

    // True Tilt Personality Profile™ card root
    private static final By TTP_CARD_ROOT =
            By.xpath("//div[starts-with(@class,'sc-d2610f5f-1') and .//h3[contains(normalize-space(),'True Tilt Personality Profile')]]");
    // Agility Growth Tracker™ card root
    private static final By AGT_CARD_ROOT =
            By.xpath("//div[starts-with(@class,'sc-d2610f5f-1') and .//h3[contains(normalize-space(),'Agility Growth Tracker')]]");

    private static final By EMAIL_ALREADY_IN_USE_ERROR =
            By.xpath("//*[contains(normalize-space(),'Email already in use')]");

    // ===== Row → actions menu / Edit Info / Send reminder =====

    // We reuse the same role='dialog' root for Edit Info / Send Reminder modals.
    // At any given time only one should be open.
    private static final By EDIT_MODAL_ROOT = By.xpath("//div[@role='dialog']");
    private static final By EDIT_FIRSTNAME_INPUT = By.id("firstName");
    private static final By EDIT_LASTNAME_INPUT = By.id("lastName");
    private static final By EDIT_EMAIL_INPUT = By.id("email");
    private static final By EDIT_SAVE_BUTTON = By.xpath("//button[normalize-space()='Save changes']");
    private static final By EDIT_CANCEL_BUTTON = By.xpath("//button[normalize-space()='Cancel']");

    // Send reminder modal (header/button texts may need tiny tweak if UI changes)
    private static final By SEND_REMINDER_MODAL = By.xpath("//div[@role='dialog']");
    private static final By SEND_REMINDER_BUTTON =
            By.xpath("//button[normalize-space()='Send reminder']");
    private static final By SEND_REMINDER_CANCEL =
            By.xpath("//button[normalize-space()='Cancel']");

    private WebElement kebabInTeamRow(WebElement row) {
        // actions column is the last one in Teams
        return row.findElement(By.cssSelector("td:last-child .ant-dropdown-trigger"));
    }

    private void waitForMenuOpen() {
        By openMenu = By.cssSelector(".ant-dropdown:not(.ant-dropdown-hidden)");
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.presenceOfElementLocated(openMenu));
        } catch (TimeoutException ignored) {
        }
    }

    /** Open the ⋮ actions menu for a member by email (Teams table). */
    public boolean openActionsMenuForMember(String email) {
        WebElement row = findMemberRowByEmail(email, Duration.ofSeconds(10));

        // hover to make the kebab visible
        try {
            scrollToElement(row);
        } catch (Throwable ignore) {
        }
        new Actions(driver).moveToElement(row)
                .pause(Duration.ofMillis(120))
                .perform();

        WebElement trigger = null;
        try {
            trigger = kebabInTeamRow(row);
        } catch (NoSuchElementException ignored) {
            // small fallbacks, same style as IndividualsPage
            try {
                trigger = row.findElement(By.cssSelector(
                        "td:last-child [aria-label*='Action' i], " +
                                "td:last-child [aria-label*='More' i], " +
                                "td:last-child button, " +
                                "td:last-child [role='button']"
                ));
            } catch (NoSuchElementException ignored2) {
                trigger = null;
            }
        }
        if (trigger == null) return false;

        try {
            new Actions(driver).moveToElement(trigger)
                    .pause(Duration.ofMillis(80))
                    .click(trigger)
                    .perform();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", trigger);
        }

        try {
            waitForMenuOpen();
            return true;
        } catch (Exception firstFail) {
            // one retry, same pattern as Individuals
            try {
                new Actions(driver).moveToElement(trigger)
                        .pause(Duration.ofMillis(80))
                        .click(trigger)
                        .perform();
            } catch (Exception e2) {
                try {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", trigger);
                } catch (Throwable ignored) {
                }
            }
            try {
                waitForMenuOpen();
                return true;
            } catch (Exception secondFail) {
                return false;
            }
        }
    }

    /** Generic helper: click an item in the open actions menu (Teams). */
    public boolean clickActionInMenu(String actionText) {
        By menuItem = By.xpath(
                "//div[contains(@class,'ant-dropdown') and contains(@class,'ant-dropdown-open')]" +
                        "//li[normalize-space()='" + actionText + "']"
        );
        try {
            WebDriverWait wdw = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement item = wdw.until(ExpectedConditions.elementToBeClickable(menuItem));
            item.click();
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    // Convenience wrappers for tests like TC14:

    /** Opens the actions menu for the given email and clicks "Edit info". */
    public void clickEditInfoForMember(String email) {
        if (!openActionsMenuForMember(email)) {
            throw new AssertionError("Could not open actions menu for member: " + email);
        }
        if (!clickActionInMenu("Edit info")) {
            throw new AssertionError("Could not click 'Edit info' in actions menu for: " + email);
        }
    }

    public void waitForEditModal() {
        waitForElementVisible(EDIT_MODAL_ROOT);
    }

    public void fillEditEmail(String newEmail) {
        WebElement em = waitForElementVisible(EDIT_EMAIL_INPUT);
        em.clear();
        em.sendKeys(newEmail);
    }

    public void clickEditSave() {
        safeClick(EDIT_SAVE_BUTTON);
        waitForElementInvisible(EDIT_MODAL_ROOT);
    }

    public void clickEditCancel() {
        safeClick(EDIT_CANCEL_BUTTON);
        try {
            waitForElementInvisible(EDIT_MODAL_ROOT);
        } catch (Exception ignored) {
        }
    }

    /** Open actions menu and click "Send reminder" for a given member. */
    public void clickSendReminderForMember(String email) {
        if (!openActionsMenuForMember(email)) {
            throw new AssertionError("Could not open actions menu for member: " + email);
        }
        if (!clickActionInMenu("Send reminder")) {
            throw new AssertionError("Could not click 'Send reminder' in actions menu for: " + email);
        }
    }

    public void waitForSendReminderModal() {
        waitForElementVisible(SEND_REMINDER_MODAL);
    }

    public boolean isSendReminderModalVisible() {
        return isVisible(SEND_REMINDER_MODAL);
    }

    public void clickSendReminderConfirm() {
        safeClick(SEND_REMINDER_BUTTON);
        // usually triggers a toast + closes modal; we at least wait for modal to disappear
        try {
            waitForElementInvisible(SEND_REMINDER_MODAL);
        } catch (Exception ignored) {
        }
    }

    public void clickSendReminderCancel() {
        safeClick(SEND_REMINDER_CANCEL);
        try {
            waitForElementInvisible(SEND_REMINDER_MODAL);
        } catch (Exception ignored) {
        }
    }

    // =====================================================================
    // ctor + small utils
    // =====================================================================

    private By productCardRoot(String titleFragment) {
        return By.xpath(
                "//div[starts-with(@class,'sc-d2610f5f-1') " +
                        " and .//h3[contains(normalize-space(),'" + titleFragment + "')]]"
        );
    }

    public TeamDetailsPage(WebDriver driver) {
        super(driver);
    }

    private boolean exists(By locator) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(3))
                    .until(d -> !d.findElements(locator).isEmpty());
            return true;
        } catch (TimeoutException e) {
            return false;
        }
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

    // <p>Retake available</p> inside the card
    private By retakeChipInsideCard() {
        return By.xpath(".//p[contains(normalize-space(),'Retake available')]");
    }

    // <p>Not available</p> inside the card (AGT case)
    private By notAvailableChipInsideCard() {
        return By.xpath(".//p[contains(normalize-space(),'Not available')]");
    }

    /** True if the TTP card is visible and shows "Retake available". */
    public boolean hasRetakeAvailableForTTP() {
        try {
            WebElement card = waitForElementVisible(productCardRoot(TTP_TITLE));
            return !card.findElements(retakeChipInsideCard()).isEmpty();
        } catch (TimeoutException | NoSuchElementException e) {
            return false;
        }
    }

    /** True if the AGT card is visible and shows "Retake available". */
    public boolean hasRetakeAvailableForAGT() {
        try {
            WebElement card = waitForElementVisible(productCardRoot(AGT_TITLE));
            return !card.findElements(retakeChipInsideCard()).isEmpty();
        } catch (TimeoutException | NoSuchElementException e) {
            return false;
        }
    }

    /** True if any product card (TTP or AGT) shows "Retake available". */
    public boolean hasAnyRetakeAvailableProduct() {
        return hasRetakeAvailableForTTP() || hasRetakeAvailableForAGT();
    }

    /** Optional: check AGT shows "Not available" chip. */
    public boolean agtShowsNotAvailable() {
        try {
            WebElement card = waitForElementVisible(productCardRoot(AGT_TITLE));
            return !card.findElements(notAvailableChipInsideCard()).isEmpty();
        } catch (TimeoutException | NoSuchElementException e) {
            return false;
        }
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
        } catch (Exception ignored) {
        }

        link.click();
        return new ReportSummaryPage(driver).waitUntilLoaded();
    }

    // ===== Analytics / Kite graph helpers =====

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
        } catch (Exception ignored) {
        }
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
     * Clicks a Kite node by 1-based index and waits briefly for the side panel to update its selected name.
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

    // =====================================================================
    // Add Team Member modal – open / basic assertions
    // =====================================================================

    /** Clicks "Add Team Member +" and waits for the Add Team Member modal to appear. */
    public TeamDetailsPage openAddTeamMemberModal() {
        safeClick(addTeamMemberButton);
        try {
            waitForElementVisible(MODAL_HEADER_ADD_TEAM_MEMBER);
        } catch (TimeoutException e) {
            // fallback: header sometimes changes; wait for search input instead
            waitForElementVisible(MODAL_SEARCH_EXISTENT_USER_BY_NAME_INPUT_SEARCHBAR);
        }
        logger.info("[TeamDetailsPage] Add Team Member modal opened.");
        return this;
    }

    /** True if the Add Team Member modal dialog is currently open. */
    public boolean isAddTeamMemberModalOpen() {
        try {
            WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(10));

            // 1) Wait until the modal body is visible
            w.until(ExpectedConditions.visibilityOfElementLocated(MODAL_ROOT));

            // 2a) Prefer content-based wait (cards rendered)
            try {
                w.withTimeout(Duration.ofSeconds(5))
                        .until(d -> !d.findElements(PRODUCT_CARD_IN_MODAL).isEmpty());
            } catch (TimeoutException ignore) {
                // 2b) Fallback: small delay if cards aren’t easy to detect
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    public boolean isSearchExistingUserInputVisible() {
        return exists(MODAL_SEARCH_EXISTENT_USER_BY_NAME_INPUT_SEARCHBAR)
                && isVisible(MODAL_SEARCH_EXISTENT_USER_BY_NAME_INPUT_SEARCHBAR);
    }

    public boolean isCreateNewUserButtonVisible() {
        return exists(MODAL_CREATE_NEW_USER_BUTTON)
                && isVisible(MODAL_CREATE_NEW_USER_BUTTON);
    }

    public boolean isAddMemberButtonVisible() {
        return exists(MODAL_ADD_MEMBER_BUTTON)
                && isVisible(MODAL_ADD_MEMBER_BUTTON);
    }

    /** "Add Member" CTA in step 1 – usually disabled until a user is selected. */
    public boolean isAddMemberButtonEnabled() {
        List<WebElement> btns = driver.findElements(MODAL_ADD_MEMBER_BUTTON);
        if (btns.isEmpty()) return false;

        WebElement btn = btns.get(0);
        String ariaDisabled = btn.getAttribute("aria-disabled");
        String disabled = btn.getAttribute("disabled");
        return btn.isDisplayed()
                && btn.isEnabled()
                && !"true".equalsIgnoreCase(String.valueOf(ariaDisabled))
                && (disabled == null || disabled.isBlank());
    }

    public TeamDetailsPage clickModalCancel1() {
        safeClick(MODAL_CANCEL_BUTTON1);
        return this;
    }

    public TeamDetailsPage clickModalCancel2() {
        safeClick(MODAL_CANCEL_BUTTON2);
        try {
            waitForElementInvisible(MODAL_ROOT);
        } catch (Exception ignored) {
        }
        logger.info("[TeamDetailsPage] Add Team Member modal closed with Cancel.");
        return this;
    }

    public TeamDetailsPage clickModalClose() {
        safeClick(MODAL_CLOSE_BUTTON);
        try {
            waitForElementInvisible(MODAL_ROOT);
        } catch (Exception ignored) {
        }
        logger.info("[TeamDetailsPage] Add Team Member modal closed with Close (X).");
        return this;
    }

    // =====================================================================
    // Step 1 – Search existing user by name
    // =====================================================================

    /** Types into the "Search Existing User by Name" input, clearing any previous value. */
    public TeamDetailsPage typeInExistingUserSearch(String query) {
        WebElement input = waitForElementVisible(MODAL_SEARCH_EXISTENT_USER_BY_NAME_INPUT_SEARCHBAR);

        // clear cross-platform
        try {
            input.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE);
        } catch (Exception ignored) {
        }
        try {
            input.sendKeys(Keys.chord(Keys.COMMAND, "a"), Keys.DELETE);
        } catch (Exception ignored) {
        }

        input.sendKeys(query);
        logger.info("[TeamDetailsPage] Typed '{}' into existing-user search.", query);
        return this;
    }

    /** Clicks the little "X" icon to clear the search input. */
    public TeamDetailsPage clearExistingUserSearch() {
        safeClick(MODAL_DELETE_SEARCH_BUTTON);
        return this;
    }

    /**
     * Clicks the main CTA at the bottom of the search step.
     * Prefers "Add Member", falls back to "Add user" (retake flow).
     */
    public TeamDetailsPage clickModalAddMemberOrUser() {
        if (!driver.findElements(MODAL_ADD_MEMBER_BUTTON).isEmpty()) {
            safeClick(MODAL_ADD_MEMBER_BUTTON);
            logger.info("[TeamDetailsPage] Clicked 'Add Member' in modal.");
        } else {
            safeClick(MODAL_ADD_USER_BUTTON);
            logger.info("[TeamDetailsPage] Clicked 'Add user' in modal.");
        }
        return this;
    }

    // =====================================================================
    // Step 2 – Create new user (First / Last / Email)
    // =====================================================================

    /** Clicks "+ Create New User" and waits for the First/Last/Email form. */
    public TeamDetailsPage clickModalCreateNewUser() {
        safeClick(MODAL_CREATE_NEW_USER_BUTTON);
        waitForElementVisible(MODAL_FISTNAME_INPUT);
        waitForElementVisible(MODAL_LASTNAME_INPUT);
        waitForElementVisible(MODAL_EMAIL_INPUT);
        logger.info("[TeamDetailsPage] Switched modal to Create New User form.");
        return this;
    }

    /** Returns true if the create-user form (First/Last/Email) is visible. */
    public boolean isCreateNewUserFormVisible() {
        return exists(MODAL_FISTNAME_INPUT)
                && exists(MODAL_LASTNAME_INPUT)
                && exists(MODAL_EMAIL_INPUT);
    }

    /** Fills First Name, Last Name and Email in the create-user form. */
    public TeamDetailsPage fillModalNewUser(String firstName, String lastName, String email) {
        WebElement fn = waitForElementVisible(MODAL_FISTNAME_INPUT);
        WebElement ln = waitForElementVisible(MODAL_LASTNAME_INPUT);
        WebElement em = waitForElementVisible(MODAL_EMAIL_INPUT);

        fn.clear();
        fn.sendKeys(firstName);
        ln.clear();
        ln.sendKeys(lastName);
        em.clear();
        em.sendKeys(email);

        logger.info("[TeamDetailsPage] Filled new user: {} {} <{}>", firstName, lastName, email);
        return this;
    }

    public TeamDetailsPage clickModalPrevious() {
        safeClick(MODAL_PREVIOUS_BUTTON);
        logger.info("[TeamDetailsPage] Clicked Previous in Add Team Member modal.");
        return this;
    }

    public TeamDetailsPage clickModalContinue() {
        safeClick(MODAL_CONTINUE_BUTTON);
        logger.info("[TeamDetailsPage] Clicked Continue in Add Team Member modal.");
        return this;
    }

    // =====================================================================
    // Step 3 – Product selection (TTP / AGT)
    // =====================================================================

    public boolean isProductSelectionStepVisible() {
        return exists(MODAL_PRODUCT_CARD_TRUE_TILT) || exists(MODAL_PRODUCT_CARD_AGT);
    }

    /** Selects the "True Tilt Personality Profile™" card. */
    public TeamDetailsPage selectTrueTiltProductForMember() {
        safeClick(MODAL_PRODUCT_CARD_TRUE_TILT);
        logger.info("[TeamDetailsPage] Selected True Tilt Personality Profile for member.");
        return this;
    }

    /** Selects the "Agility Growth Tracker™" card. */
    public TeamDetailsPage selectAgilityGrowthTrackerProductForMember() {
        safeClick(MODAL_PRODUCT_CARD_AGT);
        logger.info("[TeamDetailsPage] Selected Agility Growth Tracker for member.");
        return this;
    }

    public TeamDetailsPage clickContinueToPurchase() {
        safeClick(MODAL_CONTINUE_TO_PURCHASE_BUTTON);
        logger.info("[TeamDetailsPage] Clicked Continue to purchase in Add Team Member modal.");
        return this;
    }

    public TeamDetailsPage selectFirstExistingUserFromResults() {

        // 1) Wait for the expanded result list to be visible
        WebElement listRoot =
                waitForElementVisible(MODAL_SEARCH_EXISTENT_USER_BY_NAME_LIST_WITH_USER_EXPANDED);

        // 2) Wait for any spinner inside that list to disappear
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(d -> listRoot
                            .findElements(MODAL_EXISTING_USER_SPINNER)
                            .stream()
                            .noneMatch(WebElement::isDisplayed));
        } catch (TimeoutException ignored) {
            logger.warn("[TeamDetailsPage] Spinner inside existing-user results did not disappear in time.");
        }

        // 3) Click the first real option
        WebElement firstOption = listRoot.findElement(
                By.xpath(".//*[self::div or self::li][1]")
        );
        safeClick(firstOption);
        logger.info("[TeamDetailsPage] Selected first existing-user result from list.");
        return this;
    }

    public TeamDetailsPage waitForAddMemberModalToClose() {
        // Wait until the modal root becomes invisible or detached
        wait.waitForElementInvisible(MODAL_ROOT);
        return this;
    }

    public boolean isContinueToPurchaseVisible() {
        return exists(MODAL_CONTINUE_TO_PURCHASE_BUTTON);
    }

    // ---------- report link helpers per member row ----------

    /** Returns the row WebElement for a given email (already anchored to tbody). */
    private WebElement findMemberRowByEmail(String email, Duration timeout) {
        By locator = memberRowByEmail(email);
        WebDriverWait wait = new WebDriverWait(driver, timeout);
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    /** True if the member row for this email has at least one TTP report link. */
    public boolean memberRowHasTtpReportLink(String email) {
        WebElement row = findMemberRowByEmail(email, Duration.ofSeconds(10));
        List<WebElement> links = row.findElements(By.cssSelector("a[href*='/assess/ttp/']"));
        return !links.isEmpty();
    }

    /** True if the member row for this email has at least one AGT report link. */
    public boolean memberRowHasAgtReportLink(String email) {
        WebElement row = findMemberRowByEmail(email, Duration.ofSeconds(10));
        List<WebElement> links = row.findElements(By.cssSelector("a[href*='/assess/agt/']"));
        return !links.isEmpty();
    }

    /** True if the member row has *any* report link (TTP or AGT). */
    public boolean memberRowHasAnyReportLink(String email) {
        WebElement row = findMemberRowByEmail(email, Duration.ofSeconds(10));
        List<WebElement> links = row.findElements(
                By.cssSelector("a[href*='/assess/ttp/'], a[href*='/assess/agt/']")
        );
        return !links.isEmpty();
    }

    public String getMemberStatusByEmail(String email) {
        WebElement row = findMemberRowByEmail(email, Duration.ofSeconds(10));

        // Find the Status column cell – adjust selector if your UI differs!
        WebElement statusCell = row.findElement(
                By.xpath(".//td[contains(@class,'status') or position()=3]")
        );

        return statusCell.getText().trim();
    }

    // ---------- simple member table helpers for tests like TC11/TC14 ----------

    /** Returns the current number of member rows in the team members table. */
    public int getMemberCount() {
        List<WebElement> rows = driver.findElements(MEMBER_ROWS);
        logger.info("[TeamDetailsPage] getMemberCount = {}", rows.size());
        return rows.size();
    }

    /**
     * Lightweight presence check: true if any row for this email exists.
     * Does NOT wait; just inspects the current DOM.
     */
    public boolean isMemberListedByEmail(String email) {
        By locator = memberRowByEmail(email);
        boolean present = !driver.findElements(locator).isEmpty();
        logger.info("[TeamDetailsPage] isMemberListedByEmail('{}') = {}", email, present);
        return present;
    }

    public boolean waitForEmailAlreadyInUseError(Duration timeout) {
        WebDriverWait wait = new WebDriverWait(driver, timeout);
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(EMAIL_ALREADY_IN_USE_ERROR));
            logger.info("[TeamDetailsPage] 'Email already in use' error is visible.");
            return true;
        } catch (TimeoutException e) {
            logger.warn("[TeamDetailsPage] 'Email already in use' error did NOT appear in {}s", timeout.toSeconds());
            return false;
        }
    }

    /** "Continue" CTA in the Create New User step. */
    public boolean isContinueButtonEnabled() {
        List<WebElement> btns = driver.findElements(MODAL_CONTINUE_BUTTON);
        if (btns.isEmpty()) return false;

        WebElement btn = btns.get(0);
        String ariaDisabled = btn.getAttribute("aria-disabled");
        String disabled = btn.getAttribute("disabled");

        boolean enabled = btn.isDisplayed()
                && btn.isEnabled()
                && !"true".equalsIgnoreCase(String.valueOf(ariaDisabled))
                && (disabled == null || disabled.isBlank());

        logger.info("[TeamDetailsPage] isContinueButtonEnabled = {}", enabled);
        return enabled;
    }

    public boolean isEmailAlreadyInUseErrorVisible() {
        return !driver.findElements(EMAIL_ALREADY_IN_USE_ERROR).isEmpty();
    }
}
