package pages;

import Utils.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class DashboardPage extends BasePage {

    private WebDriver driver;
    private WaitUtils wait;

    // Example element to confirm dashboard is loaded
    private By dashboardHeader = By.xpath("//header[@class='sc-742c83c9-0 bMxPLh']");

    public DashboardPage(WebDriver driver) {
        super(driver); // Call the constructor of BasePage
        this.wait = new WaitUtils(driver, 10);
    }

    public boolean isLoaded() {
        return wait.waitForElementVisible(dashboardHeader).isDisplayed();
    }

    // Add more methods to interact with the dashboard as needed
}
