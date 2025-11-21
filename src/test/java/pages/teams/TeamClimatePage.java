package pages.teams;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.BasePage;

import java.time.Duration;
import java.util.List;

public class TeamClimatePage extends BasePage {



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
            return driver.findElement(SELECTED_MEMBER_TILT).getText().trim();
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

}
