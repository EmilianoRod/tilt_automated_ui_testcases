package pages;

import Utils.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.menuPages.DashboardPage;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

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







    private void dumpLoginNetworkLogs(WebDriver driver) {
        try {
            Set<String> types = driver.manage().logs().getAvailableLogTypes();
            if (!types.contains(LogType.PERFORMANCE)) {
                System.out.println("[LoginNet] PERFORMANCE log type not available.");
                return;
            }

            System.out.println("[LoginNet] ---- Network logs around login ----");
            LogEntries perf = driver.manage().logs().get(LogType.PERFORMANCE);
            for (LogEntry e : perf.getAll()) {
                String msg = e.getMessage();
                // Filter to only login-related calls to keep noise low
                if (msg.contains("/auth/sign-in") || msg.contains("/auth/sign") || msg.contains("/login")) {
                    System.out.println("[LoginNet] " + msg);
                }
            }
            System.out.println("[LoginNet] ---- end ----");
        } catch (Throwable t) {
            System.out.println("[LoginNet] Failed to read performance logs: " + t.getMessage());
        }
    }


    /** Throttle-aware login with narrowed error waits + multi-try typing & clicking. */
    public DashboardPage safeLoginAsAdmin(String email, String pass, Duration baseWait) {
        // CI tends to be slower → give it more time after clicking login
        boolean isCi = Boolean.parseBoolean(System.getenv().getOrDefault("CI", "false"));
        Duration postClickWait = isCi ? baseWait.plusSeconds(20) : baseWait.plusSeconds(8);

        // basic debug about what we're sending
        System.out.println("[LoginDebug] isCi=" + isCi
                + " | email=" + email
                + " | passLen=" + (pass == null ? -1 : pass.length())
                + " | passBlank=" + (pass == null || pass.isBlank()));

        WebDriver driver = driver();
        WebDriverWait signInWait = new WebDriverWait(driver, baseWait);

        By errorBanner   = By.cssSelector("[data-test='login-error'], .ant-alert-error, .ant-message-error, [role='alert']");
        By dashboardRoot = By.xpath("//*[@id=\"__next\"]/div");

        // success = URL or known dashboard element
        ExpectedCondition<Boolean> successUrl = d -> {
            String u = d.getCurrentUrl();
            if (u == null) return false;
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
            } catch (Exception ignore) {}
            if (text == null || text.isBlank()) {
                try {
                    text = d.findElement(By.tagName("body")).getText();
                } catch (Exception ignore) {}
            }
            return java.util.Objects.toString(text, "");
        };

        int maxAttempts   = isCi ? 3 : 2;  // full page-level attempts
        int maxSubTries   = 2;             // how many times to retype+click within one attempt

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
                    System.out.println("[Login] Final failure to reach sign-in, dumping network logs…");
                    dumpLoginNetworkLogs(driver);
                    throw new TimeoutException("Could not reach sign-in page before login attempts.", te);
                } else {
                    continue;
                }
            }

            // --- inner sub-attempts: retype + click multiple times before giving up this page ---
            boolean lastBadCreds      = false;
            boolean lastThrottled     = false;
            String  lastErrorSnapshot = "";

            for (int sub = 1; sub <= maxSubTries; sub++) {
                if (sub > 1) {
                    System.out.println("[Login] Sub-attempt " + sub + "/" + maxSubTries +
                            " within attempt " + attempt + " — retyping credentials and clicking again.");
                }

                try {
                    WebElement emailEl = signInWait.until(ExpectedConditions.elementToBeClickable(emailField));
                    WebElement passEl  = signInWait.until(ExpectedConditions.elementToBeClickable(passwordField));

                    // Clear & retype
                    emailEl.click();
                    emailEl.clear();
                    emailEl.sendKeys(email);

                    passEl.click();
                    passEl.clear();
                    passEl.sendKeys(pass);

                    // Best-effort verification of what ended up in the fields
                    try {
                        String emailVal = emailEl.getAttribute("value");
                        String passVal  = passEl.getAttribute("value"); // usually returns the actual text for <input type="password">
                        int passLenAttr = passVal == null ? -1 : passVal.length();

                        System.out.println("[LoginDebug] After typing: emailVal=" + emailVal +
                                " | passLenAttr=" + passLenAttr);

                        if ((emailVal == null || !emailVal.equals(email)) ||
                                (passLenAttr != -1 && passLenAttr != pass.length())) {
                            System.out.println("[LoginDebug] Field value mismatch after typing (sub-try " + sub +
                                    "). Will retype on next sub-attempt.");
                            if (sub < maxSubTries) {
                                try { Thread.sleep(800L); } catch (InterruptedException ignored) {}
                                continue; // retype on next sub attempt
                            }
                        }
                    } catch (Exception verifyEx) {
                        System.out.println("[LoginDebug] Could not verify field values: " + verifyEx.getMessage());
                    }

                    // Click login: first normal, then JS fallback
                    try {
                        signInWait.until(ExpectedConditions.elementToBeClickable(loginButton)).click();
                    } catch (ElementClickInterceptedException e) {
                        System.out.println("[Login] ElementClickInterceptedException — retrying click via JS.");
                        try {
                            WebElement btn = driver.findElement(loginButton);
                            ((JavascriptExecutor) driver).executeScript("arguments[0].click()", btn);
                        } catch (Exception jsEx) {
                            System.out.println("[Login] JS click also failed: " + jsEx.getMessage());
                        }
                    }

                    // Wait until either success or a clear login error appears
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
                                " (sub " + sub + "/" + maxSubTries + ")" +
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

                    lastBadCreds      = badCreds;
                    lastThrottled     = throttled;
                    lastErrorSnapshot = low;

                    System.out.println("[LoginFail] Attempt " + attempt + "/" + maxAttempts +
                            " (sub " + sub + "/" + maxSubTries + ")");
                    System.out.println("[LoginFail] URL: " + driver.getCurrentUrl());
                    System.out.println("[LoginFail] Title: " + driver.getTitle());
                    System.out.println("[LoginFail] Detected text (trimmed): " +
                            low.substring(0, Math.min(low.length(), 300)));

                    String bodyPreview = "";
                    try {
                        bodyPreview = driver.findElement(By.tagName("body")).getText();
                    } catch (Exception ignore) {}
                    System.out.println("[LoginFail] body preview (first lines):");
                    java.util.Arrays.stream(java.util.Objects.toString(bodyPreview, "").split("\\R"))
                            .limit(12)
                            .map(String::trim)
                            .forEach(l -> System.out.println("  • " + l));

                    // If it's clearly bad credentials → don't keep poking
                    if (badCreds) {
                        System.out.println("[Login] badCreds detected on sub-attempt " + sub +
                                ". Dumping network logs and aborting.");
                        dumpLoginNetworkLogs(driver);
                        throw new TimeoutException("Login failed: invalid credentials or locked account.");
                    }

                    // If it's clearly throttling → break inner loop, outer will decide retry/backoff
                    if (throttled) {
                        System.out.println("[Login] Throttled (sub " + sub + ").");
                        break; // let outer attempt handle backoff
                    }

                    // Unknown reason; if we still have a sub-try left, retype and click again
                    if (sub < maxSubTries) {
                        System.out.println("[Login] Unknown login failure, will retry typing+clicking once more in this attempt.");
                        try { Thread.sleep(1200L); } catch (InterruptedException ignored) {}
                        continue;
                    }

                } catch (TimeoutException te) {
                    if (sub == maxSubTries) {
                        // Let outer loop decide; we just log here
                        System.out.println("[Login] TimeoutException within attempt " + attempt +
                                " on final sub-attempt; will escalate to outer attempts.");
                        break;
                    } else {
                        System.out.println("[Login] TimeoutException on sub-attempt " + sub +
                                ", will retry typing/clicking again.");
                        try { Thread.sleep(1200L); } catch (InterruptedException ignored) {}
                    }
                } catch (RuntimeException re) {
                    System.out.println("[Login] RuntimeException during attempt " + attempt +
                            " (sub " + sub + "): " + re.getMessage());
                    if (sub == maxSubTries) {
                        // Final sub-try of this attempt → let outer loop handle or fail
                        break;
                    }
                }
            } // end sub-attempts

            // --- after inner loop: decide what to do at outer attempt level ---

            if (lastBadCreds) {
                System.out.println("[Login] lastBadCreds=true after attempt " + attempt +
                        ". Dumping network logs and aborting.");
                dumpLoginNetworkLogs(driver);
                throw new TimeoutException("Login failed: invalid credentials or locked account. Last error: " +
                        (lastErrorSnapshot == null ? "" : lastErrorSnapshot));
            }

            if (lastThrottled) {
                if (attempt < maxAttempts) {
                    System.out.println("[Login] Throttled on attempt " + attempt +
                            "/" + maxAttempts + ". Backing off before next attempt.");
                    try { Thread.sleep(isCi ? 4000L : 2000L); } catch (InterruptedException ignored) {}
                    continue;
                } else {
                    System.out.println("[Login] Throttled on final attempt; dumping network logs.");
                    dumpLoginNetworkLogs(driver);
                    throw new TimeoutException("Login failed due to repeated throttling / rate limiting. Last error: " +
                            (lastErrorSnapshot == null ? "" : lastErrorSnapshot));
                }
            }

            // Unknown failure reason at this attempt level
            if (attempt < maxAttempts) {
                System.out.println("[Login] Unknown login failure at outer attempt " + attempt +
                        "/" + maxAttempts + ". Will refresh and retry.");
                try { Thread.sleep(1500L); } catch (InterruptedException ignored) {}
            } else {
                System.out.println("[Login] Final outer attempt failed with unknown reason; dumping network logs.");
                dumpLoginNetworkLogs(driver);
                throw new TimeoutException("Login did not reach dashboard after " + maxAttempts +
                        " attempts. Last error: " +
                        (lastErrorSnapshot == null ? "" : lastErrorSnapshot));
            }
        }

        // Should never reach here, but just in case
        System.out.println("[Login] Exited login loop without success, dumping network logs…");
        dumpLoginNetworkLogs(driver);
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
