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

import static base.BaseTest.driver;

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
    public DashboardPage safeLoginAsAdmin(String email, String pass, Duration baseWait) {
        // CI tends to be slower → give it more time after clicking login
        boolean isCi = Boolean.parseBoolean(System.getenv().getOrDefault("CI", "true"));
        Duration postClickWait = isCi ? baseWait.plusSeconds(20) : baseWait.plusSeconds(8);

        WebDriver driver = driver();
        WebDriverWait signInWait = new WebDriverWait(driver, baseWait);

        // make sure we're actually on sign-in (once per attempt; loop below)
        By errorBanner   = By.cssSelector("[data-test='login-error'], .ant-alert-error, .ant-message-error, [role='alert']");
        By dashboardRoot = By.cssSelector("[data-test='dashboard-root']"); // adjust if needed

        // success = URL or known dashboard element
        ExpectedCondition<Boolean> successUrl = d -> {
            String u = d.getCurrentUrl();
            if (u == null) return false;
            // add here any post-login URLs your app might redirect to
            return u.contains("/dashboard")
                    || u.contains("/individuals")
                    || u.contains("/teams")
                    || u.contains("/home");
        };

        ExpectedCondition<Boolean> dashboardVisible = d -> {
            try {
                WebElement root = d.findElement(dashboardRoot);
                return root.isDisplayed();
            } catch (Exception e) {
                return false;
            }
        };

        // Small helper to grab whatever login error text we can see
        java.util.function.Function<WebDriver, String> extractErrorText = d -> {
            String text = "";
            try {
                for (WebElement el : d.findElements(errorBanner)) {
                    if (el.isDisplayed()) {
                        text = el.getText();
                        if (text != null && !text.isBlank()) break;
                    }
                }
            } catch (Exception ignore) {
            }
            if (text == null || text.isBlank()) {
                try {
                    text = d.findElement(By.tagName("body")).getText();
                } catch (Exception ignore) {
                }
            }
            return java.util.Objects.toString(text, "");
        };

        int maxAttempts = isCi ? 3 : 2;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            if (attempt > 1) {
                System.out.println("[Login] Attempt " + attempt + "/" + maxAttempts + " – refreshing and retrying.");
                driver.navigate().refresh();
                try { Thread.sleep(1200L); } catch (InterruptedException ignored) {}
            }

            // Ensure we’re on the sign-in page
            try {
                signInWait.until(ExpectedConditions.or(
                        ExpectedConditions.titleContains("Sign In"),
                        ExpectedConditions.visibilityOfElementLocated(emailField),
                        ExpectedConditions.urlContains("/auth/sign-in")
                ));
            } catch (TimeoutException te) {
                System.out.println("[Login] Could not reach sign-in page on attempt " + attempt + ". URL=" + driver.getCurrentUrl());
                if (attempt == maxAttempts) {
                    throw new TimeoutException("Could not reach sign-in page before login attempts.");
                } else {
                    continue;
                }
            }

            // ---- actual login attempt ----
            try {
                WebElement emailEl = signInWait.until(ExpectedConditions.elementToBeClickable(emailField));
                WebElement passEl  = signInWait.until(ExpectedConditions.elementToBeClickable(passwordField));
                emailEl.clear();
                emailEl.sendKeys(email);
                passEl.clear();
                passEl.sendKeys(pass);

                try {
                    signInWait.until(ExpectedConditions.elementToBeClickable(loginButton)).click();
                } catch (ElementClickInterceptedException e) {
                    WebElement btn = driver.findElement(loginButton);
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click()", btn);
                }

                // Wait until either:
                //  - we clearly landed somewhere "logged in" (URL / dashboardRoot), OR
                //  - we clearly see a login-related error message.
                try {
                    new WebDriverWait(driver, postClickWait).until(d -> {
                        if (Boolean.TRUE.equals(successUrl.apply(d)) ||
                                Boolean.TRUE.equals(dashboardVisible.apply(d))) {
                            return true;
                        }
                        String low = extractErrorText.apply(d).toLowerCase(Locale.ROOT);
                        boolean knownErr =
                                low.contains("invalid email or password") ||
                                        low.contains("too many attempts") ||
                                        low.contains("try again later") ||
                                        low.contains("rate limit");
                        return knownErr;
                    });
                } catch (TimeoutException ignore) {
                    // we'll classify below
                }

                // ---- classify outcome after the wait ----
                boolean success = Boolean.TRUE.equals(successUrl.apply(driver)) ||
                        Boolean.TRUE.equals(dashboardVisible.apply(driver));

                if (success) {
                    System.out.println("[Login] Success after attempt " + attempt + "/" + maxAttempts +
                            " | URL=" + driver.getCurrentUrl());
                    return new DashboardPage(driver);
                }

                String fullText = extractErrorText.apply(driver);
                String low      = fullText.toLowerCase(Locale.ROOT);
                boolean badCreds =
                        low.contains("invalid email or password");
                boolean throttled =
                        low.contains("too many attempts") ||
                                low.contains("try again later") ||
                                low.contains("rate limit");

                System.out.println("[LoginFail] Attempt " + attempt + "/" + maxAttempts);
                System.out.println("[LoginFail] URL: " + driver.getCurrentUrl());
                System.out.println("[LoginFail] Title: " + driver.getTitle());
                System.out.println("[LoginFail] Detected text (trimmed): " +
                        low.substring(0, Math.min(low.length(), 300)));

                String bodyPreview = "";
                try {
                    bodyPreview = driver.findElement(By.tagName("body")).getText();
                } catch (Exception ignore) {
                }
                System.out.println("[LoginFail] body preview (first lines):");
                java.util.Arrays.stream(java.util.Objects.toString(bodyPreview, "").split("\\R"))
                        .limit(12)
                        .map(String::trim)
                        .forEach(l -> System.out.println("  • " + l));

                if (badCreds) {
                    // hard fail: no point in retrying more
                    throw new TimeoutException("Login failed: invalid credentials or locked account.");
                }

                if (throttled) {
                    if (attempt < maxAttempts) {
                        System.out.println("[Login] Throttled / temporary error. Backing off before next attempt.");
                        try { Thread.sleep(isCi ? 4000L : 2000L); } catch (InterruptedException ignored) {}
                        continue; // next attempt in the for-loop
                    } else {
                        throw new TimeoutException("Login failed due to repeated throttling / rate limiting.");
                    }
                }

                // Unknown reason, but we already logged. Let next attempt retry if we have any left.
                if (attempt < maxAttempts) {
                    System.out.println("[Login] Unknown login failure, will retry once more.");
                    try { Thread.sleep(1500L); } catch (InterruptedException ignored) {}
                    continue;
                }

            } catch (TimeoutException te) {
                // rethrow if last attempt, otherwise loop will retry
                if (attempt == maxAttempts) {
                    throw te;
                } else {
                    System.out.println("[Login] TimeoutException during attempt " + attempt + ", will retry.");
                }
            } catch (RuntimeException re) {
                // Unexpected runtime, log & decide
                System.out.println("[Login] RuntimeException during attempt " + attempt + ": " + re.getMessage());
                if (attempt == maxAttempts) {
                    throw re;
                }
            }
        }

        // If we exit the loop without returning, we never reached dashboard
        throw new TimeoutException("Login did not reach dashboard after " + maxAttempts + " attempts.");
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
