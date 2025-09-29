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
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public class LoginPage extends BasePage {
    private static final Logger logger = LogManager.getLogger(LoginPage.class);


    // Locators for login form elements
    private By emailField = By.xpath("//input[@id='email']");
    private By passwordField = By.xpath("//input[@id='password']");
    private By loginButton = By.xpath("//button[normalize-space()='Sign In']");
    private By errorMsg = By.xpath("//span[@type='error']");

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

    // ==================== login helper ====================

    /** Throttle-aware login with diagnostics and a one-time retry (no “effectively final” lambda issues). */
    public DashboardPage safeLoginAsAdmin( String email, String pass, Duration wait) {

        // wait until we are on the login page (optional)
        new WebDriverWait(driver, wait).until(ExpectedConditions.titleContains("Sign In"));

        try {

            type(emailField,email);
            type(passwordField,pass);
            click(loginButton);

            new WebDriverWait(driver, wait).until(ExpectedConditions.urlContains("/dashboard"));
            return new DashboardPage(driver);
        } catch (org.openqa.selenium.TimeoutException te) {
            // dump a few lines for diagnosis and check banners
            String bodyText = "";
            try { bodyText = driver.findElement(By.tagName("body")).getText(); } catch (Exception ignore) {}
            System.out.println("[LoginFail] body preview:");
            Arrays.stream(Objects.toString(bodyText, "")
                            .split("\\R"))
                    .limit(10)
                    .map(String::trim)
                    .forEach(l -> System.out.println("  • " + l));

            String low = Objects.toString(bodyText, "").toLowerCase(Locale.ROOT);
            if (low.contains("too many attempts") || low.contains("try again later") || low.contains("rate")) {
                try { Thread.sleep(8_000L); } catch (InterruptedException ignored) {}
                // retry once
                click(loginButton);// or re-enter creds if needed
                new WebDriverWait(driver, wait).until(ExpectedConditions.urlContains("/dashboard"));
                return new DashboardPage(driver);
            }
            throw te;
        }
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
