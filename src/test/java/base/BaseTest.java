package base;

import Utils.Config;
import Utils.MailSlurpUtils;
import com.mailslurp.models.InboxDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.*;
import pages.LoginPage;
import pages.menuPages.DashboardPage;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WrapsDriver;

public class BaseTest {

    public static final Logger logger = LogManager.getLogger(BaseTest.class);
    /** Suite-scoped inbox prepared in @BeforeSuite (if email-required). */
    protected static volatile InboxDto fixedInbox;

    private static final ThreadLocal<Long> START = new ThreadLocal<>();

    // =========================================================
    // SUITE INITIALIZATION
    // =========================================================
    @BeforeSuite(alwaysRun = true)
    public void mailSlurpSuiteInit() {
        // Honor MAILSLURP_DEBUG/mailslurp.debug if present; default true for helpful logs
        String msDebug = Config.getAny("mailslurp.debug", "MAILSLURP_DEBUG");
        System.setProperty("mailslurp.debug", msDebug == null ? "true" : msDebug);

        if (!isEmailRequiredForSuite() && !isMailSlurpForceOn()) {
            logger.info("[MailSlurp][Suite] Email not required. Skipping inbox resolution.");
            fixedInbox = null;
            return;
        }

        try {
            final String fixedIdRaw   = Config.getMailSlurpFixedInboxId();
            final String fixedId      = fixedIdRaw == null ? null : fixedIdRaw.trim();
            final boolean allowCreate = Config.getMailSlurpAllowCreate();

            logger.info("[MailSlurp][Suite] allowCreate={} | fixedIdPresent={}{}",
                    allowCreate,
                    (fixedId != null && !fixedId.isBlank()),
                    (fixedId != null ? " | idPrefix=" + fixedId.substring(0, Math.min(8, fixedId.length())) : "")
            );

            if (fixedId != null && !fixedId.isBlank()) {
                fixedInbox = MailSlurpUtils.getInboxById(UUID.fromString(fixedId));
                if (fixedInbox != null) {
                    logger.info("[MailSlurp][Suite] Using fixed inbox {} <{}>",
                            fixedInbox.getId(), fixedInbox.getEmailAddress());
                    MailSlurpUtils.clearInboxEmails(fixedInbox.getId());
                }
            } else if (allowCreate || isMailSlurpForceOn()) {
                fixedInbox = MailSlurpUtils.resolveFixedOrCreateInbox();
                if (fixedInbox != null) {
                    logger.info("[MailSlurp][Suite] Created or resolved inbox {} <{}>",
                            fixedInbox.getId(), fixedInbox.getEmailAddress());
                }
            } else {
                logger.warn("[MailSlurp][Suite] No fixed inbox and creation disabled.");
                fixedInbox = null;
            }

        } catch (Exception e) {
            logger.warn("[MailSlurp][Suite] MailSlurp unavailable: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            fixedInbox = null;
        }
    }

    // =========================================================
    // TEST SETUP
    // =========================================================
    @BeforeMethod(alwaysRun = true)
    public void setUp(Method method) {
        final String adminEmail = Config.getAdminEmail();
        final String adminPass  = Config.getAdminPassword();
        if (adminEmail == null || adminEmail.isBlank() || adminPass == null || adminPass.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (ADMIN_EMAIL / ADMIN_PASSWORD).");
        }

        final boolean emailRequired = isEmailRequiredForTest(method);
        if (emailRequired && fixedInbox == null && !isMailSlurpForceOn()) {
            throw new SkipException("[MailSlurp][Guard] fixedInbox is null and no force flag set for test: " + method.getName());
        }

        if (!emailRequired && !isMailSlurpForceOn()) {
            logger.info("[MailSlurp][TestCtx] Email not required for this test (group=ui-only).");
        } else {
            logger.info("[MailSlurp][TestCtx] Inbox active: {} <{}>",
                    fixedInbox != null ? fixedInbox.getId() : "(none)",
                    fixedInbox != null ? fixedInbox.getEmailAddress() : "(unset)");
        }

        // ---------- Env health log (pre-driver) ----------
        String baseUrl = Config.getBaseUrl();
        boolean headless = Config.isHeadless();
        String chromeBin = Config.getChromeBinaryPath();
        String wdmBrowserVersion = Config.getAny("wdm.browserVersion", "WDM_BROWSER_VERSION");
        Duration timeout = Config.getTimeout();

        logger.info(
                "[Env] BASE_URL={} | HEADLESS={} | CHROME_BIN={} | WDM_BROWSER_VERSION={} | TIMEOUT={}s",
                baseUrl,
                headless,
                (chromeBin == null || chromeBin.isBlank() ? "(auto)" : chromeBin),
                (wdmBrowserVersion == null || wdmBrowserVersion.isBlank() ? "(auto)" : wdmBrowserVersion),
                timeout.toSeconds()
        );

        // --- WebDriver: one per thread via ThreadLocal ---
        DriverManager.init();
        WebDriver d = driver();

        d.manage().timeouts().implicitlyWait(Duration.ZERO);

        // Log detected browser/version after driver init
        try {
            Capabilities caps = null;

            if (d instanceof HasCapabilities) {
                caps = ((HasCapabilities) d).getCapabilities();
            } else if (d instanceof WrapsDriver) {
                WebDriver wd = ((WrapsDriver) d).getWrappedDriver();
                if (wd instanceof HasCapabilities) {
                    caps = ((HasCapabilities) wd).getCapabilities();
                }
            }

            if (caps != null) {
                String browserName    = String.valueOf(caps.getBrowserName());
                String browserVersion = String.valueOf(caps.getBrowserVersion());
                Object plStrategy     = caps.getCapability(CapabilityType.PAGE_LOAD_STRATEGY);
                Object platformName   = caps.getCapability("platformName");
                Object insecureCerts  = caps.getCapability("acceptInsecureCerts");

                logger.info("[Driver] {} {} | pageLoadStrategy={} | platformName={} | acceptInsecureCerts={}",
                        browserName,
                        browserVersion,
                        plStrategy == null ? "default" : plStrategy,
                        platformName,
                        insecureCerts);
            } else {
                logger.debug("[Driver] Capabilities not available (no HasCapabilities on driver or wrapped driver).");
            }
        } catch (Throwable t) {
            logger.debug("[Driver] Could not read capabilities: {}", t.getMessage());
        }

        // Timeouts (explicit waits should be preferred in tests)
        Duration base     = timeout;
        Duration pageLoad = max(Duration.ofSeconds(30), base.plusSeconds(20));
        Duration script   = max(Duration.ofSeconds(30), base);

        d.manage().timeouts().pageLoadTimeout(pageLoad);
        d.manage().timeouts().scriptTimeout(script);

        // Clean state + normalize viewport
        clearCookiesAndStorage(d);
        normalizeViewport(d);

        START.set(System.currentTimeMillis());
        logger.info("========== STARTING TEST: {} (admin={}) ==========",
                method.getName(), adminEmail);
    }

    // =========================================================
    // TEARDOWN
    // =========================================================
    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        Long st = START.get();
        double secs = st == null ? 0.0 : (System.currentTimeMillis() - st) / 1000.0;
        logger.info("========== FINISHED TEST: {} ({}s) ==========",
                result.getMethod().getMethodName(), secs);

        try {
            if (DriverManager.isInitialized()) {
                WebDriver d = DriverManager.get();
                try {
                    if (result.getStatus() == ITestResult.FAILURE) {
                        takeScreenshot(d, result.getMethod().getMethodName());
                    }
                } finally {
                    DriverManager.quit();
                }
            }
        } catch (Throwable t) {
            logger.warn("[Teardown] Suppressed exception during cleanup: {}", t.getMessage());
        }
    }

    // =========================================================
    // PUBLIC SESSION HELPERS
    // =========================================================
    public static DashboardPage startFreshSession(WebDriver driver) {
        return startFreshSession(driver, 3); // 3 attempts for transient issues
    }

    public static DashboardPage startFreshSession(WebDriver driver, int maxAttempts) {
        // If caller passed null, try to bootstrap a driver via DriverManager
        if (driver == null) {
            try {
                System.out.println("[BaseTest] startFreshSession received null driver; initializing via DriverManager.");
                DriverManager.init();
                driver = DriverManager.get();
            } catch (Throwable t) {
                throw new SkipException("[BaseTest] Unable to initialize WebDriver in startFreshSession: " + t.getMessage(), t);
            }
        }

        // Use Config helpers so we always resolve the same way
        final String baseUrl   = Config.getBaseUrl();
        final String adminUser = Config.getAdminEmail();
        final String adminPass = Config.getAdminPassword();

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new SkipException("[Config] BASE_URL missing.");
        }
        if (adminUser == null || adminUser.isBlank() || adminPass == null || adminPass.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing.");
        }

        final boolean isCi = Optional.ofNullable(System.getenv("CI"))
                .map(v -> !v.isBlank() && !"false".equalsIgnoreCase(v))
                .orElse(false);

//        final boolean isCi = true;

        final Duration navTimeout =
                Config.getTimeout().compareTo(Duration.ofSeconds(20)) > 0
                        ? Config.getTimeout()
                        : Duration.ofSeconds(20);
        final Duration loginTimeout =
                Config.getTimeout().compareTo(Duration.ofSeconds(10)) > 0
                        ? Config.getTimeout()
                        : Duration.ofSeconds(10);

        // Pre-compute sign-in URL and log it clearly
        final String signInUrl = baseUrl.endsWith("/")
                ? baseUrl + "auth/sign-in"
                : baseUrl + "/auth/sign-in";

        logger.info("[BaseTest] startFreshSession bootstrap → BASE_URL={} | SIGN_IN_URL={} | admin={} | isCi={} | navTimeout={}s | loginTimeout={}s",
                baseUrl,
                signInUrl,
                adminUser,
                isCi,
                navTimeout.toSeconds(),
                loginTimeout.toSeconds()
        );

        System.out.println("[BaseTest] BASE_URL=" + baseUrl);
        System.out.println("[BaseTest] SIGN_IN_URL=" + signInUrl);
        System.out.println("[BaseTest] ADMIN_USER=" + adminUser);
        System.out.println("[BaseTest] CI=" + isCi);

        Throwable lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                logger.info("[BaseTest] Starting fresh session attempt {}/{} at {}",
                        attempt, maxAttempts, baseUrl);
                System.out.println("[BaseTest] === startFreshSession attempt " + attempt + "/" + maxAttempts + " ===");

                // On attempts > 1, do a HARD driver reset (full new browser)
                if (attempt > 1) {
                    System.out.println("[BaseTest] Attempt " + attempt + " → full WebDriver restart.");
                    try {
                        if (DriverManager.isInitialized()) {
                            DriverManager.quit();
                        }
                    } catch (Throwable t) {
                        logger.warn("[BaseTest] Suppressed exception while quitting driver on retry: {}", t.toString());
                    }

                    DriverManager.init();
                    driver = DriverManager.get();
                }

                if (driver == null) {
                    throw new SkipException("[BaseTest] WebDriver is null inside startFreshSession attempt " + attempt);
                }

                // 1) Hard reset browser session
                clearCookiesAndStorage(driver);
                normalizeViewport(driver);

                // 2) Go straight to sign-in
                logger.info("[BaseTest] Navigating to sign-in: {}", signInUrl);
                System.out.println("[BaseTest] Navigating to SIGN_IN_URL: " + signInUrl);

                robustGet(driver, signInUrl, /*retries*/ 2, navTimeout);

                try {
                    String current = driver.getCurrentUrl();
                    System.out.println("[BaseTest] After robustGet currentUrl=" + current);
                    logger.info("[BaseTest] After robustGet currentUrl={}", current);
                } catch (Throwable ignore) {
                    // best effort
                }

                // 3) Perform login
                LoginPage login = new LoginPage(driver);
                DashboardPage dashboard = login.safeLoginAsAdmin(adminUser, adminPass, loginTimeout);

                try {
                    String afterLoginUrl = driver.getCurrentUrl();
                    System.out.println("[BaseTest] After safeLoginAsAdmin currentUrl=" + afterLoginUrl);
                    logger.info("[BaseTest] After safeLoginAsAdmin currentUrl={}", afterLoginUrl);
                } catch (Throwable ignore) {
                    // best effort
                }

                if (dashboard != null && dashboard.isLoaded()) {
                    logger.info("[BaseTest] Login successful as {} on attempt {}", adminUser, attempt);
                    System.out.println("[BaseTest] ✅ Dashboard loaded successfully on attempt " + attempt);
                    return dashboard;
                }

                String msg = String.format(
                        "[BaseTest] Dashboard not loaded after login (attempt %d/%d, user=%s)",
                        attempt, maxAttempts, adminUser
                );
                logger.warn(msg);
                System.out.println("[BaseTest] " + msg);
                lastError = new RuntimeException(msg);

            } catch (TimeoutException e) {
                lastError = e;
                logger.warn("[BaseTest] TimeoutException during startFreshSession attempt {}/{}: {}",
                        attempt, maxAttempts, e.toString());
                System.out.println("[BaseTest] TimeoutException on attempt " + attempt + ": " + e);

            } catch (WebDriverException e) {
                lastError = e;
                logger.warn("[BaseTest] WebDriverException during startFreshSession attempt {}/{}: {}",
                        attempt, maxAttempts, e.toString());
                System.out.println("[BaseTest] WebDriverException on attempt " + attempt + ": " + e);
            }

            // Small backoff between attempts (optional)
            try {
                Thread.sleep(isCi ? 2500L : 1500L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // If we exhausted all attempts, treat as real failure (not skip)
        String failMsg = "❌ Unable to start fresh session and reach Dashboard after "
                + maxAttempts + " attempts for user " + adminUser;
        logger.error(failMsg, lastError);
        System.out.println("[BaseTest] " + failMsg);
        throw new AssertionError(failMsg, lastError);
    }


    public static DashboardPage startFreshSession() {
        return startFreshSession(driver());
    }

    public static InboxDto getSuiteInbox() {
        return fixedInbox;
    }

    public static InboxDto requireInboxOrSkip() {
        if (fixedInbox == null) {
            throw new SkipException("[MailSlurp] Suite inbox not available. " +
                    "Ensure MAILSLURP_API_KEY is set and either MAILSLURP_INBOX_ID provided " +
                    "or mailslurp.allowCreate=true (or set MAILSLURP_FORCE=true).");
        }
        return fixedInbox;
    }

    // =========================================================
    // INTERNAL UTILITIES
    // =========================================================
    public static WebDriver driver() {
        return DriverManager.get();
    }

    /**
     * Navigate to the given URL with retries and a DOM-ready wait.
     * Retries only on transient WebDriver issues; bails out early if the session/window is invalid.
     */
    private static void robustGet(WebDriver driver, String url, int maxAttempts, Duration timeout) {
        if (driver == null) {
            throw new IllegalArgumentException("WebDriver is null in robustGet()");
        }

        int attempts = Math.max(1, maxAttempts);
        RuntimeException last = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                logger.info("[robustGet] Attempt {}/{} → {}", attempt, attempts, url);

                driver.navigate().to(url);

                // Wait until DOM is at least 'interactive' or 'complete'
                waitForDomInteractiveOrComplete(driver, timeout);

                logger.info("[robustGet] Successfully loaded: {}", driver.getCurrentUrl());
                return;

            } catch (WebDriverException e) {
                // Some WebDriverExceptions mean the session/window is unusable. Don't bother retrying.
                if (e instanceof org.openqa.selenium.NoSuchWindowException ||
                        e instanceof org.openqa.selenium.SessionNotCreatedException ||
                        e instanceof org.openqa.selenium.NoSuchSessionException) {

                    String msg = String.format(
                            "[robustGet] Non-recoverable WebDriverException on attempt %d/%d for '%s': %s",
                            attempt, attempts, url, e);
                    logger.error(msg, e);
                    throw new RuntimeException(msg, e);
                }

                String msg = String.format(
                        "[robustGet] Transient WebDriverException on attempt %d/%d for '%s': %s",
                        attempt, attempts, url, e);
                logger.warn(msg, e);
                last = new RuntimeException(msg, e);

                // Try to stop the current load
                try {
                    ((JavascriptExecutor) driver).executeScript("try{window.stop()}catch(e){}");
                } catch (Exception ignore) {
                    // ignore
                }

                // Exponential backoff: 500ms, 1000ms, 1500ms, ...
                long sleepMs = 500L * attempt;
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warn("[robustGet] Interrupted during backoff; aborting retries.");
                    throw last;
                }
            }
        }

        if (last != null) {
            logger.error("[robustGet] Exhausted {} attempts for URL: {}", attempts, url, last);
            throw last;
        } else {
            String msg = "[robustGet] Failed without capturing any exception for URL: " + url;
            logger.error(msg);
            throw new RuntimeException(msg);
        }
    }


    private static void waitForDomInteractiveOrComplete(WebDriver driver, Duration timeout) {
        new WebDriverWait(driver, timeout).until(d -> {
            try {
                String rs = String.valueOf(((JavascriptExecutor) d).executeScript("return document.readyState"));
                return "interactive".equals(rs) || "complete".equals(rs);
            } catch (Exception e) { return false; }
        });
    }

    private static void clearCookiesAndStorage(WebDriver driver) {
        if (driver == null) return;

        try {
            driver.manage().deleteAllCookies();
        } catch (Exception e) {
            logger.debug("[clearCookiesAndStorage] Failed to delete cookies: {}", e.toString());
        }

        try {
            ((JavascriptExecutor) driver).executeScript(
                    "try{localStorage.clear()}catch(e){}; try{sessionStorage.clear()}catch(e){};");
        } catch (Exception e) {
            logger.debug("[clearCookiesAndStorage] Failed to clear local/session storage: {}", e.toString());
        }
    }


    private static void normalizeViewport(WebDriver driver) {
        try { driver.manage().window().maximize(); }
        catch (Exception e) {
            try { driver.manage().window().setSize(new Dimension(1366, 900)); }
            catch (Exception ignored) {}
        }
    }

    private static Duration max(Duration a, Duration b) {
        return (a.compareTo(b) >= 0) ? a : b;
    }

    protected static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "(blank)";
        int at = email.indexOf('@');
        String user = at > -1 ? email.substring(0, at) : email;
        String dom  = at > -1 ? email.substring(at) : "";
        if (user.length() <= 2) return user.charAt(0) + "****" + dom;
        return user.charAt(0) + "****" + user.charAt(user.length() - 1) + dom;
    }

    // =========================================================
    // MAILSLURP FLAGS & GUARDS
    // =========================================================
    private static boolean isMailSlurpForceOn() {
        String raw = Config.getAny("mailslurp.force", "MAILSLURP_FORCE");
        boolean on = raw != null && (raw.equalsIgnoreCase("true") || raw.equals("1") || raw.equalsIgnoreCase("yes"));
        if (on) logger.info("[MailSlurp][Force] EMAIL CHECKS ENABLED globally via MAILSLURP_FORCE=true");
        return on;
    }

    private static boolean isEmailRequiredForSuite() {
        String raw = Config.getAny("mailslurp.required", "MAILSLURP_REQUIRED");
        return raw == null || raw.isBlank() || Boolean.parseBoolean(raw);
    }

    private static boolean isEmailRequiredForTest(Method m) {
        if (isMailSlurpForceOn()) return true;
        org.testng.annotations.Test ann = m.getAnnotation(org.testng.annotations.Test.class);
        if (ann == null) return true;
        for (String g : ann.groups()) {
            if ("ui-only".equalsIgnoreCase(g)) return false;
        }
        return true;
    }

    // =========================================================
    // SCREENSHOT
    // =========================================================
    private static void takeScreenshot(WebDriver driver, String methodName) {
        try {
            Path dir = Paths.get("target", "screenshots");
            Files.createDirectories(dir);
            Path file = dir.resolve(methodName + ".png");
            byte[] data = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Files.write(file, data);
            logger.info("[Screenshot] Saved: {}", file.toAbsolutePath());
        } catch (Exception e) {
            logger.warn("[Screenshot] Failed to capture: {}", e.getMessage());
        }
    }



    public static String getChromeDownloadDir() {
        return Paths.get("target/downloads").toAbsolutePath().toString();
    }



    protected Path waitForNewPdf(Path downloadDir,
                                 Instant start,
                                 Duration timeout) throws Exception {
        return waitForNewFile(downloadDir, start, timeout, ".pdf");
    }

    protected Path waitForNewPng(Path downloadDir,
                                 Instant start,
                                 Duration timeout) throws Exception {
        return waitForNewFile(downloadDir, start, timeout, ".png");
    }

    /**
     * Generic file waiter: waits for a new file with the given extension.
     */
    protected Path waitForNewFile(Path downloadDir,
                                  Instant start,
                                  Duration timeout,
                                  String extension) throws Exception {
        final long end = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < end) {
            try (var stream = Files.list(downloadDir)) {
                Optional<Path> newest = stream
                        .filter(p -> {
                            try {
                                return Files.isRegularFile(p)
                                        && p.getFileName().toString().toLowerCase().endsWith(extension)
                                        && Files.getLastModifiedTime(p).toMillis() >= start.toEpochMilli();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .findFirst();

                if (newest.isPresent()) {
                    return newest.get();
                }
            } catch (Throwable ignored) {}

            Thread.sleep(500);
        }

        return null;
    }


}
