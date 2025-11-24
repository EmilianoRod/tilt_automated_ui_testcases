package pages;

import Utils.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedCondition;
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

        new WebDriverWait(driver, Config.getTimeout())
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

    /** Throttle-aware login with narrowed error waits + always-one refresh+retry. */
    public DashboardPage safeLoginAsAdmin(String email, String pass, Duration wait) {
        WebDriverWait w = new WebDriverWait(driver, wait);

        // make sure we're actually on sign-in
        try {
            w.until(ExpectedConditions.or(
                    ExpectedConditions.titleContains("Sign In"),
                    ExpectedConditions.visibilityOfElementLocated(emailField),
                    ExpectedConditions.urlContains("/auth/sign-in")
            ));
        } catch (TimeoutException te) {
            driver.navigate().refresh();
            w.until(ExpectedConditions.or(
                    ExpectedConditions.titleContains("Sign In"),
                    ExpectedConditions.visibilityOfElementLocated(emailField),
                    ExpectedConditions.urlContains("/auth/sign-in")
            ));
        }

        // selectors: avoid broad ".error" which can short-circuit
        By errorBanner = By.cssSelector("[data-test='login-error'], .ant-alert-error, .ant-message-error, [role='alert']");

        // condition: success URL reached?
        ExpectedCondition<Boolean> successUrl = d -> {
            String u = d.getCurrentUrl();
            return u != null && u.contains("/dashboard");
        };

        // condition: a *known* login error (text-based), not just any visible alert
        ExpectedCondition<Boolean> knownLoginError = d -> {
            String text = "";
            try {
                for (WebElement el : d.findElements(errorBanner)) {
                    if (el.isDisplayed()) { text = el.getText(); if (text != null && !text.isBlank()) break; }
                }
            } catch (Exception ignore) {}
            // fall back to body text (some apps render errors outside alerts)
            if (text == null || text.isBlank()) {
                try { text = d.findElement(By.tagName("body")).getText(); } catch (Exception ignore) {}
            }
            String low = Objects.toString(text, "").toLowerCase(Locale.ROOT);
            return low.contains("invalid email or password")
                    || low.contains("too many attempts")
                    || low.contains("try again later")
                    || low.contains("rate limit");
        };

        // single attempt (used twice)
        Runnable attempt = () -> {
            WebElement emailEl = w.until(ExpectedConditions.elementToBeClickable(emailField));
            WebElement passEl  = w.until(ExpectedConditions.elementToBeClickable(passwordField));
            emailEl.clear(); emailEl.sendKeys(email);
            passEl.clear();  passEl.sendKeys(pass);

            try {
                w.until(ExpectedConditions.elementToBeClickable(loginButton)).click();
            } catch (ElementClickInterceptedException e) {
                WebElement btn = driver.findElement(loginButton);
                ((JavascriptExecutor) driver).executeScript("arguments[0].click()", btn);
            }

            // wait for either success or a *real* login error
            try {
                new WebDriverWait(driver, wait.plusSeconds(8)).until(
                        ExpectedConditions.or(successUrl, knownLoginError)
                );
            } catch (TimeoutException ignore) { /* fall through and classify below */ }

            if (Boolean.TRUE.equals(successUrl.apply(driver))) return; // success

            // print a brief body preview for diagnostics
            String body = "";
            try { body = driver.findElement(By.tagName("body")).getText(); } catch (Exception ignore) {}
            System.out.println("[LoginFail] body preview (first lines):");
            Arrays.stream(Objects.toString(body, "").split("\\R")).limit(12)
                    .map(String::trim).forEach(l -> System.out.println("  • " + l));

            // throw to signal failure of this attempt (retry logic below will handle)
            throw new RuntimeException("__ATTEMPT_FAILED__");
        };

        // first attempt
        try {
            attempt.run();
            return new DashboardPage(driver);
        } catch (RuntimeException first) {
            System.out.println("[Login] First attempt failed. Forcing refresh + retry.");
        }

        // hard refresh + retry (always)
        driver.navigate().refresh();
        try { Thread.sleep(1200L); } catch (InterruptedException ignored) {}
        w.until(ExpectedConditions.or(
                ExpectedConditions.titleContains("Sign In"),
                ExpectedConditions.visibilityOfElementLocated(emailField),
                ExpectedConditions.urlContains("/auth/sign-in")
        ));

        try {
            attempt.run();
            return new DashboardPage(driver);
        } catch (RuntimeException second) {
            throw new TimeoutException("Login did not reach /dashboard after refresh+retry.");
        }
    }




    @Override
    public LoginPage waitUntilLoaded() {
        wait.waitForDocumentReady();
        wait.waitForLoadersToDisappear();
        wait.waitForElementVisible(emailField);
        return this;
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
