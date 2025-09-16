package base;

import Utils.Config;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import io.qameta.allure.Attachment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.stream.Collectors;


public class BaseTest {


    protected WebDriver driver;
    protected static final Logger logger = LogManager.getLogger(BaseTest.class);

    // Track per-test duration (thread-safe for parallel)
    private static final ThreadLocal<Long> START = new ThreadLocal<>();



    @BeforeClass
    public static void forceMailSlurpKeyForCI() {
        // ⚠️ TEMPORAL para destrabar CI
        System.setProperty("mailslurp.forceKey", "4d9e6d8a17fefcb0585d2e0780d4ea882702e17fe0d683ac232b4e08a127ddfe");
        System.setProperty("mailslurp.debug", "true"); // opcional: imprime fingerprint/userId
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
        try {
            driver.manage().deleteAllCookies();
        } catch (Exception ignored) {}
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "try{localStorage.clear();sessionStorage.clear();}catch(e){}"
            );
        } catch (Exception ignored) {}

        // 4) Window sizing is handled in DriverFactory (headless/new headless)
        //    Avoid maximize() in headless; it's a no-op or can throw on some platforms.

        START.set(System.currentTimeMillis());
        logger.info("========== STARTING TEST: {} ==========", method.getName());
        // NOTE: Do NOT auto-navigate here. Let pages/tests call driver.get().
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

                // 3) Performance logs (CDP network + console)
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
            // Log pass/fail/skip + timing
            switch (result.getStatus()) {
                case ITestResult.FAILURE -> logger.error("❌ TEST FAILED: {} ({} ms)", result.getName(), elapsed);
                case ITestResult.SKIP    -> logger.warn("⚠️ TEST SKIPPED: {} ({} ms)", result.getName(), elapsed);
                default                  -> logger.info("✅ TEST PASSED: {} ({} ms)", result.getName(), elapsed);
            }

            // Thread-safe quit & cleanup
            if (driver != null) {
                DriverManager.quit();
                logger.info("Browser closed successfully.");
            }
            START.remove();
        }
    }

}