package base;

import Utils.Config;
import Utils.MailSlurpUtils;
import com.mailslurp.clients.ApiException;
import com.mailslurp.models.InboxDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.*;
import pages.LoginPage;
import pages.menuPages.DashboardPage;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Collectors;

public class BaseTest {

    protected WebDriver driver;
    protected static final Logger logger = LogManager.getLogger(BaseTest.class);

    // Suite-scoped fixed inbox (resolved once)
    protected static volatile InboxDto fixedInbox;

    // Track per-test duration (thread-safe for parallel)
    private static final ThreadLocal<Long> START = new ThreadLocal<>();

    // -------- Suite setup: MailSlurp context --------
    @BeforeSuite(alwaysRun = true)
    public void mailSlurpSuiteInit() {
        System.setProperty("mailslurp.debug", System.getProperty("mailslurp.debug", "true"));

        final String fixedIdRaw = Config.getAny("mailslurp.inboxId", "MAILSLURP_INBOX_ID");
        final String fixedId = fixedIdRaw == null ? null : fixedIdRaw.trim();
        final boolean allowCreate = Config.getMailSlurpAllowCreate();

        logger.info("[MailSlurp][Suite] allowCreate={} | fixedIdPresent={}{}",
                allowCreate,
                (fixedId != null && !fixedId.isBlank()),
                (fixedId != null ? " | idPrefix=" + fixedId.substring(0, Math.min(8, fixedId.length())) : "")
        );

        try {
            if (fixedId != null && !fixedId.isBlank()) {
                fixedInbox = MailSlurpUtils.getInboxById(UUID.fromString(fixedId));
                if (fixedInbox == null) throw new SkipException("[MailSlurp][Suite] getInboxById returned null for id: " + fixedId);

                logger.info("[MailSlurp][Suite] Using fixed inbox {} <{}>", fixedInbox.getId(), fixedInbox.getEmailAddress());

                System.setProperty("mailslurp.inboxId", fixedInbox.getId().toString());
                System.setProperty("MAILSLURP_INBOX_ID", fixedInbox.getId().toString());

                try { MailSlurpUtils.clearInboxEmails(fixedInbox.getId()); }
                catch (Exception e) { logger.warn("[MailSlurp][Suite] Could not clear inbox emails: {}", e.getMessage()); }
            } else if (allowCreate) {
                fixedInbox = MailSlurpUtils.resolveFixedOrCreateInbox();
                if (fixedInbox == null) throw new SkipException("[MailSlurp][Suite] resolveFixedOrCreateInbox returned null (creation blocked/quota?)");

                logger.info("[MailSlurp][Suite] Resolved/created inbox {} <{}>", fixedInbox.getId(), fixedInbox.getEmailAddress());
                System.setProperty("mailslurp.inboxId", fixedInbox.getId().toString());
                System.setProperty("MAILSLURP_INBOX_ID", fixedInbox.getId().toString());
            } else {
                throw new SkipException("[MailSlurp][Suite] No fixed inbox configured and inbox creation is disabled (mailslurp.allowCreate=false).");
            }
        } catch (IllegalArgumentException e) {
            throw new SkipException("[MailSlurp][Suite] MAILSLURP_INBOX_ID is not a valid UUID: " + fixedId);
        } catch (ApiException e) {
            if (e.getCode() == 404)  throw new SkipException("[MailSlurp][Suite] Fixed inbox not found: " + fixedId);
            if (e.getCode() == 426)  throw new SkipException("[MailSlurp][Suite] CreateInbox quota exhausted (426). Provide MAILSLURP_INBOX_ID or upgrade quota.");
            throw new SkipException("[MailSlurp][Suite] MailSlurp API error (" + e.getCode() + "): " + e.getMessage());
        } catch (SkipException se) {
            throw se;
        } catch (Exception e) {
            throw new SkipException("[MailSlurp][Suite] Unexpected error resolving inbox: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // -------- Per-test setup --------
    @BeforeMethod(alwaysRun = true)
    public void setUp(Method method) {
        final String adminEmail = Config.getAdminEmail();
        final String adminPass  = Config.getAdminPassword();
        if (adminEmail == null || adminEmail.isBlank() || adminPass == null || adminPass.isBlank()) {
            throw new SkipException("[Config] Admin credentials are not set (admin.email/ADMIN_EMAIL/ADMIN_USER and admin.password/ADMIN_PASSWORD/ADMIN_PASS).");
        }
        if (fixedInbox == null) {
            throw new SkipException("[BaseTest][Guard] fixedInbox is null before test " + method.getName()
                    + " (check mailslurp.inboxId/MAILSLURP_INBOX_ID or allowCreate)");
        }

        // 1) Thread-safe driver
        DriverManager.init();
        driver = DriverManager.get();

        // 2) Deterministic timeouts (no implicit waits)
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(Math.max(60, Config.getTimeout() * 2L)));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(Math.max(30, Config.getTimeout())));

        // 3) Fresh session
        clearCookiesAndStorage(driver);

        // 4) Normalized viewport
        normalizeViewport(driver);

        START.set(System.currentTimeMillis());
        logger.info("========== STARTING TEST: {} ==========", method.getName());
        logger.info("[MailSlurp][TestCtx] Inbox {} <{}>", fixedInbox.getId(), fixedInbox.getEmailAddress());
    }

    // -------- Per-test teardown --------
    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        long elapsed = 0L;
        Long s = START.get();
        if (s != null) elapsed = System.currentTimeMillis() - s;

        try {
            if (result.getStatus() == ITestResult.SKIP && result.getThrowable() != null) {
                logger.warn("⤴️  SKIP REASON: {}", result.getThrowable().getMessage());
            }

            if (!result.isSuccess() && driver != null) {
                Path outDir = Paths.get("target", "artifacts", sanitize(result.getName()));
                Files.createDirectories(outDir);

                // 1) Screenshot
                try {
                    byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
                    safeWrite(outDir.resolve("screenshot.png"), png);
                    logger.info("📸 Saved screenshot for {}", result.getName());
                } catch (Exception e) {
                    logger.warn("⚠️ Could not capture screenshot: {}", e.getMessage());
                }

                // 2) Current URL & HTML
                try {
                    safeWriteString(outDir.resolve("url.txt"), safeCurrentUrl());
                    String html = (String) ((JavascriptExecutor) driver).executeScript("return document.documentElement.outerHTML;");
                    safeWriteString(outDir.resolve("page.html"), html);
                    logger.info("🧾 Saved page HTML and URL for {}", result.getName());
                } catch (Exception e) {
                    logger.warn("⚠️ Could not save page HTML/URL: {}", e.getMessage());
                }

                // 3) Browser console logs
                try {
                    String browserLog = driver.manage().logs().get(LogType.BROWSER).getAll()
                            .stream()
                            .map(e -> String.format("[%s] %s", e.getLevel(), e.getMessage()))
                            .collect(Collectors.joining(System.lineSeparator()));
                    safeWriteString(outDir.resolve("browser.log"), browserLog);
                    logger.info("🧾 Saved browser logs for {}", result.getName());
                } catch (Exception e) {
                    logger.warn("⚠️ Could not capture browser logs: {}", e.getMessage());
                }

                // 4) Performance logs
                try {
                    String perfLog = driver.manage().logs().get(LogType.PERFORMANCE).getAll()
                            .stream()
                            .map(LogEntry::getMessage)
                            .collect(Collectors.joining(System.lineSeparator()));
                    safeWriteString(outDir.resolve("performance.jsonl"), perfLog);
                    logger.info("📡 Saved performance logs for {}", result.getName());
                } catch (Exception e) {
                    logger.warn("⚠️ Could not capture performance logs: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("❌ Error while collecting teardown diagnostics: {}", e.getMessage(), e);
        } finally {
            switch (result.getStatus()) {
                case ITestResult.FAILURE -> logger.error("❌ TEST FAILED: {} ({} ms)", result.getName(), elapsed);
                case ITestResult.SKIP    -> logger.warn("⚠️ TEST SKIPPED: {} ({} ms)", result.getName(), elapsed);
                default                  -> logger.info("✅ TEST PASSED: {} ({} ms)", result.getName(), elapsed);
            }
            if (driver != null) {
                try { DriverManager.quit(); }
                finally { logger.info("Browser closed successfully."); }
            }
            START.remove();
        }
    }

    // ========================================================================
    // Public helper: start a brand-new authenticated session for tests
    // ========================================================================
    /**
     * Hard reset of browser session and authenticate as admin, returning a loaded DashboardPage.
     * Uses robust navigation to avoid Chrome renderer stalls on first load.
     */
    public static DashboardPage startFreshSession(WebDriver driver) {
        if (driver == null) throw new SkipException("[BaseTest] WebDriver is null.");

        final String baseUrl   = Config.getAny("base.url", "BASE_URL", "APP_BASE_URL", "BASEURL");
        final String adminUser = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String adminPass = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");

        if (baseUrl == null || baseUrl.isBlank())
            throw new SkipException("[Config] BASE_URL missing (base.url/BASE_URL/APP_BASE_URL).");
        if (adminUser == null || adminUser.isBlank() || adminPass == null || adminPass.isBlank())
            throw new SkipException("[Config] Admin credentials are not set (admin.email/ADMIN_EMAIL/ADMIN_USER and admin.password/ADMIN_PASSWORD/ADMIN_PASS).");

        // 1) Nuke session; bind origin; clear storages again (belt & suspenders)
        clearCookiesAndStorage(driver);
        robustGet(driver, baseUrl, 2, Duration.ofSeconds(20));  // <— robust navigation
        clearStorageOnly(driver);
        clearCookiesOnly(driver);

        // 2) Normalize viewport (in case the driver re-opened a new session)
        normalizeViewport(driver);

        // 3) Navigate to login and authenticate
        robustGet(driver, baseUrl + "/auth/sign-in", 2, Duration.ofSeconds(20));
        LoginPage login = new LoginPage(driver);
        DashboardPage dashboard = login.safeLoginAsAdmin(
                adminUser,
                adminPass,
                Duration.ofSeconds(Math.max(10, Config.getTimeout()))
        );

        if (dashboard == null || !dashboard.isLoaded()) {
            throw new SkipException("❌ Dashboard did not load after login with admin user: " + adminUser);
        }
        return dashboard;
    }

    // ========================================================================
    // Internal utilities
    // ========================================================================

    /** Robust navigation with one retry + DOM ready check + window.stop() fallback. */
    private static void robustGet(WebDriver driver, String url, int maxAttempts, Duration domReadyTimeout) {
        RuntimeException last = null;
        for (int i = 1; i <= Math.max(1, maxAttempts); i++) {
            try {
                driver.navigate().to(url);
                waitForDomInteractiveOrComplete(driver, domReadyTimeout);
                return;
            } catch (WebDriverException e) {
                last = e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
                // Stop the current load and retry once
                try { ((JavascriptExecutor) driver).executeScript("try{window.stop()}catch(e){}"); } catch (Exception ignore) {}
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }
        if (last != null) throw last;
    }

    /** Wait for document.readyState ∈ {interactive, complete}. */
    private static void waitForDomInteractiveOrComplete(WebDriver driver, Duration timeout) {
        new WebDriverWait(driver, timeout).until(d -> {
            try {
                String rs = String.valueOf(((JavascriptExecutor) d).executeScript("return document.readyState"));
                return "interactive".equals(rs) || "complete".equals(rs);
            } catch (Exception e) { return false; }
        });
    }

    /** Delete cookies + clear local/session storage. Safe best-effort. */
    private static void clearCookiesAndStorage(WebDriver driver) {
        clearCookiesOnly(driver);
        clearStorageOnly(driver);
    }

    private static void clearCookiesOnly(WebDriver driver) {
        try { driver.manage().deleteAllCookies(); } catch (Exception ignored) {}
    }

    private static void clearStorageOnly(WebDriver driver) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "try{localStorage.clear()}catch(e){}; try{sessionStorage.clear()}catch(e){};"
            );
        } catch (Exception ignored) {}
    }

    /** Maximize, or fall back to a deterministic size (useful in headless). */
    private static void normalizeViewport(WebDriver driver) {
        try { driver.manage().window().maximize(); }
        catch (Exception e) {
            try { driver.manage().window().setSize(new Dimension(1366, 900)); }
            catch (Exception ignored) {}
        }
    }

    private String safeCurrentUrl() {
        try { return driver.getCurrentUrl(); }
        catch (Exception e) { return "(no url available)"; }
    }

    private static String sanitize(String name) {
        return name == null ? "unknown" : name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void safeWrite(Path path, byte[] bytes) {
        try { Files.write(path, bytes); } catch (Exception ignored) {}
    }

    private static void safeWriteString(Path path, String text) {
        try { Files.write(path, (text == null ? "" : text).getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) {}
    }
}
