package pages;

import Utils.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.menuPages.DashboardPage;

import java.time.Duration;

public class LoginPage extends BasePage {
    private static final Logger logger = LogManager.getLogger(LoginPage.class);


    // Locators for login form elements
    private By emailField = By.xpath("//input[@id='email']");
    private By passwordField = By.xpath("//input[@id='password']");
    private By loginButton = By.xpath("//button[normalize-space()='Sign In']");
    private By errorMsg = By.xpath("//span[@class='sc-d0ff2b78-4 kgDJIq']");

    public LoginPage(WebDriver driver) {
        super(driver); // Call the constructor of BasePage
    }


    // Navigate to the login page (explicit route, env-aware)
    public void navigateTo() {
        String url = Config.joinUrl(Config.getBaseUrl(), "/auth/sign-in");
        driver.get(url);

        new WebDriverWait(driver, Duration.ofSeconds(Config.getTimeout()))
                .until(ExpectedConditions.or(
                        ExpectedConditions.urlContains("/auth/sign-in"),
                        ExpectedConditions.urlContains("/auth-sign-in") // keep if your app sometimes uses this
                ));

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
        logger.info("Logging in as admin...");

        return new DashboardPage(driver); // Assuming successful login redirects to Dashboard
    }

    public void waitUntilLoaded() {
        wait.waitForDocumentReady();
        wait.waitForLoadersToDisappear();
        wait.waitForElementVisible(emailField);
    }

    public boolean isLoaded() {
        try {
            return isVisible(emailField);
        } catch (Exception e) {
            return false;
        }
    }


    // Check if error message is displayed (for invalid login attempts)
    public String getErrorMessage() {
        return wait.waitForElementVisible(errorMsg).getText();
    }


    public boolean isEmailFieldVisible() {
        return isVisible(emailField);
    }


}
