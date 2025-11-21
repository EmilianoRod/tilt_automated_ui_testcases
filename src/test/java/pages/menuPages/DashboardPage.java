package pages.menuPages;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import pages.BasePage;
import pages.Individuals.IndividualsPage;
import pages.LoginPage;
import pages.teams.TeamDetailsPage;
import pages.teams.TeamsPage;

public class DashboardPage extends BasePage{


    // Example element to confirm dashboard is loaded
    private By dashboardHeader = By.xpath("//header[@class='sc-742c83c9-0 bMxPLh']");
    private By userName = By.xpath("//div[@id='__next']//div//div//main//header//div//div//a");
    private By newAssessmentBtn = By.xpath("//button[normalize-space()='New Assessment']");
    private By individualsButton = By.xpath("//nav//button[normalize-space()='Individuals']");
    private By shopButton = By.xpath("//button[normalize-space()='Shop']");
    private By teamsButton = By.xpath("//span[normalize-space()=\"Teams\"]");


    private By myjourneyTitle = By.xpath("//h1[normalize-space()=\"My journey\"]");
    private By welcomeText = By.xpath("//h4[normalize-space()=\"Welcome to Tilt365!\"]");
    private By startTrueTiltProfile = By.xpath("//button[normalize-space()=\"Start True Tilt Profile\"]");
    private By settingsButton = By.xpath("//span[normalize-space()='Settings']");
    private By logOutButton = By.xpath("//span[normalize-space()=\"Logout\"]");



    public DashboardPage(WebDriver driver) {
        super(driver); // Call the constructor of BasePage
    }

    public boolean isLoaded() {
        return  waitForUrlContains("/dashboard") &&
                wait.waitForElementVisible(dashboardHeader).isDisplayed();
    }


    public boolean isUserNameDisplayed() {
        return isVisible(userName);
    }

    public String getUserName() {
        return waitForElementVisible(userName).getText();
    }

    public boolean isNewAssessmentButtonVisible() {
        return isVisible(newAssessmentBtn);
    }


    public IndividualsPage goToIndividuals() {
        WebElement individualsButtonSideBarMenu = wait.waitForElementClickable(individualsButton);
        individualsButtonSideBarMenu.click();
        return new IndividualsPage(driver);
    }

    public ShopPage goToShop() {
        wait.waitForElementClickable(shopButton).click();
        return new ShopPage(driver); // ✅ driver comes from BasePage
    }

    public TeamsPage goToTeams() {
        wait.waitForElementVisible(teamsButton).click();
        return new TeamsPage(driver);
    }


    @Override
    public DashboardPage waitUntilLoaded() {
        wait.waitForDocumentReady();
        wait.waitForLoadersToDisappear();

        // We need to allow multiple possible dashboard layouts.
        // Try several “identity” elements and wait for ANY OF THEM to appear.

        By[] possibleDashboardMarkers = new By[] {
                userName,                // ALWAYS present for any logged-in user
                newAssessmentBtn,        // Appears for new OR existing users
                myjourneyTitle,          // Appears when at least 1 assessment exists
                welcomeText,             // Appears for brand new users
                startTrueTiltProfile     // CTA for brand new users
        };

        boolean anyVisible = false;

        for (By locator : possibleDashboardMarkers) {
            try {
                wait.waitForElementVisible(locator);
                anyVisible = true;
                break;  // if any element is visible → dashboard is considered loaded
            } catch (Exception ignored) { }
        }

        if (!anyVisible) {
            throw new TimeoutException("❌ Dashboard did not load — no known markers became visible.");
        }

        return this;
    }



    public ResourcesPage goToResources() {
        // adjust locator to your existing side-nav selector
        By resourcesNav = By.xpath("//span[normalize-space()=\"Resources\"]");
        safeClick(resourcesNav);
        ResourcesPage resourcesPage = new ResourcesPage(driver);
        return resourcesPage;
    }


    public SettingsPage goToSettings() {
        wait.waitForElementClickable(settingsButton).click();
        SettingsPage settingsPage = new SettingsPage(driver);
        return settingsPage.waitUntilLoaded();
    }

    public LoginPage logout() {
        wait.waitForElementClickable(logOutButton).click();
        return new LoginPage(driver);
    }



    /**
     * Convenience helper for tests:
     *  - Clicks the left-nav "Teams" entry.
     *  - Waits for TeamsPage to load.
     *  - Asks TeamsPage to open the team matching the given path/name.
     */
    public TeamDetailsPage openTeamByName(String teamPath) {
        // Ir a Teams desde el Dashboard
        WebElement teamsLink = waitForElementClickable(teamsButton);
        teamsLink.click();

        TeamsPage teamsPage = new TeamsPage(driver).waitUntilLoaded();
        return teamsPage.openTeamClimateDetails(teamPath);
    }


}
