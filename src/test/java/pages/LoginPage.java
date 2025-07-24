package pages;

import Utils.Config;
import Utils.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class LoginPage extends BasePage {


    // Locators for login form elements
    private By emailField = By.xpath("//input[@id='email']");
    private By passwordField = By.xpath("//input[@id='password']");
    private By loginButton = By.xpath("//button[normalize-space()='Sign In']");
    private By errorMsg = By.xpath("//span[@class='sc-d0ff2b78-4 kgDJIq']");

    public LoginPage(WebDriver driver) {
        super(driver); // Call the constructor of BasePage
    }

    // Navigate to the login page (if not already on it)
    public void navigateTo() {
        driver.get(Config.getBaseUrl()); // Use Config to get the base URL
        waitForElementVisible(emailField);
    }

    // Perform login action
    public DashboardPage login(String emailEntered, String passwordEntered) {

        WebElement email = wait.waitForElementVisible(emailField);
        email.sendKeys(emailEntered);

        WebElement password = wait.waitForElementVisible(passwordField);
        password.sendKeys(passwordEntered);

        WebElement login = wait.waitForElementClickable(loginButton);
        login.click();

        return new DashboardPage(driver); // Assuming successful login redirects to Dashboard
    }

    // Check if error message is displayed (for invalid login attempts)
    public String getErrorMessage() {
        return wait.waitForElementVisible(errorMsg).getText();
    }


    public boolean isEmailFieldVisible() {
        return isVisible(emailField);
    }


}
