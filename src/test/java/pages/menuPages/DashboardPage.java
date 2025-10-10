package pages.menuPages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import pages.BasePage;
import pages.Individuals.IndividualsPage;

public class DashboardPage extends BasePage {


    // Example element to confirm dashboard is loaded
    private By dashboardHeader = By.xpath("//header[@class='sc-742c83c9-0 bMxPLh']");
    private By userName = By.xpath("//div[@id='__next']//div//div//main//header//div//div//a");
    private By newAssessmentBtn = By.xpath("//button[normalize-space()='New Assessment']");
    private By individualsButton = By.xpath("//nav//button[normalize-space()='Individuals']");
    private By shopButton = By.xpath("//button[normalize-space()='Shop']");




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


}
