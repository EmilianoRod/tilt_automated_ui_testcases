package base;

import Utils.Config;
import Utils.MailSlurpUtils;
import com.mailslurp.clients.ApiException;
import com.mailslurp.models.InboxDto;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.UUID;

public class BaseTest {

    protected WebDriver driver;
    protected static final Logger logger = LogManager.getLogger(BaseTest.class);

    // Suite-scoped fixed inbox (resolved once)
    protected static volatile InboxDto fixedInbox;

    // Track per-test duration (thread-safe for parallel)
    private static final ThreadLocal<Long> START = new ThreadLocal<>();

    @BeforeSuite(alwaysRun = true)
    public void mailSlurpSuiteInit() {
        // Ensure debug logs for quick fingerprint/userId (safe)
        System.setProperty("mailslurp.debug", System.getProperty("mailslurp.debug", "true"));

        final String fixedId = System.getProperty("MAILSLURP_INBOX_ID", System.getenv("MAILSLURP_INBOX_ID"));
        try {
            if (fixedId != null && !fixedId.isBlank()) {
                // Fetch fixed inbox once (preferred, zero quota usage)
                fixedInbox = MailSlurpUtils.getInboxById(UUID.fromString(fixedId.trim()));
                logger.info("[MailSlurp][Suite] Using fixed inbox {} <{}>",
                        fixedInbox.getId(), fixedInbox.getEmailAddress());
                // Best-effort clear mailbox to make unreadOnly waits deterministic
                MailSlurpUtils.clearInboxEmails(fixedInbox.getId());
            } else {
                // Only if you allow fallback: this will consume CreateInbox allowance
                logger.warn("[MailSlurp][Suite] MAILSLURP_INBOX_ID not set. Tests may fall back to inbox creation (when explicitly allowed).");
            }
        } catch (IllegalArgumentException e) {
            throw new SkipException("[MailSlurp][Suite] MAILSLURP_INBOX_ID is not a valid UUID: " + fixedId);
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                throw new SkipException("[MailSlurp][Suite] Fixed inbox not found: " + fixedId);
            }
            throw new SkipException("[MailSlurp][Suite] Could not resolve fixed inbox (" + e.getCode() + "): " + e.getMessage());
        } catch (Exception e) {
            throw new SkipException("[MailSlurp][Suite] Unexpected error resolving inbox: " + e.getMessage());
        }
    }

    @BeforeMethod(alwaysRun = true)
    public void setUp(Method method) {
        // 1) Thread-safe driver (parallel-ready)
        DriverManager.init();
        driver = DriverManager.get();

        // 2) Deterministic timeouts (no implicit waits)
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(Config.getTimeout() * 2L));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(Config.getTimeout()));

        // 3) Clean session per test (avoid sticky redirects)
        try { driver.manage().deleteAllCookies(); } catch (Exception ignored) {}
        try { ((JavascriptExecutor) driver).executeScript("try{localStorage.clear();sessionStorage.clear();}catch(e){}"); } catch (Exception ignored) {}

        START.set(System.currentTimeMillis());
        logger.info("========== STARTING TEST: {} ==========", method.getName());
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        long elapsed = 0L;
        Long s = START.get();
        if (s != null) elapsed = System.currentTimeMillis() - s;

        try {
            if (!result.isSuccess() && driver != null) {
                Path outDir = Paths.get("target", "artifacts", result.getName());
                Files.createDirectories(outDir);

                // 1) Screenshot
                try {
                    TakesScreenshot ts = (TakesScreenshot) driver;
                    Files.write(outDir.resolve("screenshot.png"),
                            ts.getScreenshotAs(OutputType.BYTES));
                    logger.info("📸 Saved screenshot for failed test {}", result.getName());
                } catch (Exception e) {
                    logger.warn("⚠️ Could not capture screenshot: {}", e.getMessage());
                }

                // 2) Browser console logs
                try {
                    String browserLog = driver.manage().logs().get(LogType.BROWSER).getAll()
                            .stream()
                            .map(e -> String.format("[%s] %s", e.getLevel(), e.getMessage()))
                            .collect(Collectors.joining(System.lineSeparator()));
                    Files.write(outDir.resolve("browser.log"), browserLog.getBytes(StandardCharsets.UTF_8));
                    logger.info("🧾 Saved browser logs for {}", result.getName());
                } catch (Exception e) {
                    logger.warn("⚠️ Could not capture browser logs: {}", e.getMessage());
                }

                // 3) Performance logs
                try {
                    String perfLog = driver.manage().logs().get(LogType.PERFORMANCE).getAll()
                            .stream()
                            .map(LogEntry::getMessage)
                            .collect(Collectors.joining(System.lineSeparator()));
                    Files.write(outDir.resolve("performance.jsonl"), perfLog.getBytes(StandardCharsets.UTF_8));
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
                DriverManager.quit();
                logger.info("Browser closed successfully.");
            }
            START.remove();
        }
    }
}
