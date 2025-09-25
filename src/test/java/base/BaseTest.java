package base;

import Utils.Config;
import Utils.MailSlurpUtils;
import com.mailslurp.clients.ApiException;
import com.mailslurp.models.InboxDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.*;

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

    @BeforeSuite(alwaysRun = true)
    public void mailSlurpSuiteInit() {
        // Ensure debug logs for quick fingerprint/userId (safe)
        System.setProperty("mailslurp.debug", System.getProperty("mailslurp.debug", "true"));

        // Read values via Config with aliases (covers -D, env, .properties, .env)
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
                // Preferred: reuse existing fixed inbox (no quota usage)
                fixedInbox = MailSlurpUtils.getInboxById(UUID.fromString(fixedId));
                if (fixedInbox == null) {
                    throw new SkipException("[MailSlurp][Suite] getInboxById returned null for id: " + fixedId);
                }
                logger.info("[MailSlurp][Suite] Using fixed inbox {} <{}>",
                        fixedInbox.getId(), fixedInbox.getEmailAddress());

                // Propagate ID in both styles so tests/utilities can read either
                System.setProperty("mailslurp.inboxId", fixedInbox.getId().toString());
                System.setProperty("MAILSLURP_INBOX_ID", fixedInbox.getId().toString());

                // Best-effort clear mailbox to make unreadOnly waits deterministic
                try {
                    MailSlurpUtils.clearInboxEmails(fixedInbox.getId());
                } catch (Exception e) {
                    logger.warn("[MailSlurp][Suite] Could not clear inbox emails: {}", e.getMessage());
                }
            } else if (allowCreate) {
                // Fallback only if explicitly allowed (may spend CreateInbox quota)
                fixedInbox = MailSlurpUtils.resolveFixedOrCreateInbox();
                if (fixedInbox == null) {
                    throw new SkipException("[MailSlurp][Suite] resolveFixedOrCreateInbox returned null (creation blocked/quota?)");
                }
                logger.info("[MailSlurp][Suite] Resolved/created inbox {} <{}>",
                        fixedInbox.getId(), fixedInbox.getEmailAddress());

                // Propagate
                System.setProperty("mailslurp.inboxId", fixedInbox.getId().toString());
                System.setProperty("MAILSLURP_INBOX_ID", fixedInbox.getId().toString());
            } else {
                // Hard stop to avoid burning quota or running flaky tests
                throw new SkipException("[MailSlurp][Suite] No fixed inbox configured and inbox creation is disabled (mailslurp.allowCreate=false).");
            }
        } catch (IllegalArgumentException e) {
            throw new SkipException("[MailSlurp][Suite] MAILSLURP_INBOX_ID is not a valid UUID: " + fixedId);
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                throw new SkipException("[MailSlurp][Suite] Fixed inbox not found: " + fixedId);
            }
            if (e.getCode() == 426) {
                throw new SkipException("[MailSlurp][Suite] CreateInbox quota exhausted (426). Provide MAILSLURP_INBOX_ID or upgrade quota.");
            }
            throw new SkipException("[MailSlurp][Suite] MailSlurp API error (" + e.getCode() + "): " + e.getMessage());
        } catch (SkipException se) {
            throw se; // bubble exact reason
        } catch (Exception e) {
            throw new SkipException("[MailSlurp][Suite] Unexpected error resolving inbox: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @BeforeMethod(alwaysRun = true)
    public void setUp(Method method) {
        // If some test throws a custom guard, show the exact reason in logs
        if (fixedInbox == null) {
            throw new SkipException("[BaseTest][Guard] fixedInbox is null before test " + method.getName()
                    + " (check mailslurp.inboxId/MAILSLURP_INBOX_ID or allowCreate)");
        }

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
        logger.info("[MailSlurp][TestCtx] Inbox {} <{}>", fixedInbox.getId(), fixedInbox.getEmailAddress());
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        long elapsed = 0L;
        Long s = START.get();
        if (s != null) elapsed = System.currentTimeMillis() - s;

        try {
            // If the test was skipped, log the reason explicitly
            if (result.getStatus() == ITestResult.SKIP && result.getThrowable() != null) {
                logger.warn("⤴️  SKIP REASON: {}", result.getThrowable().getMessage());
            }

            if (!result.isSuccess() && driver != null) {
                Path outDir = Paths.get("target", "artifacts", result.getName());
                Files.createDirectories(outDir);

                // 1) Screenshot
                try {
                    TakesScreenshot ts = (TakesScreenshot) driver;
                    Files.write(outDir.resolve("screenshot.png"), ts.getScreenshotAs(OutputType.BYTES));
                    logger.info("📸 Saved screenshot for {}", result.getName());
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

