package base;

import Utils.Config;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import io.qameta.allure.Attachment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.lang.reflect.Method;
import java.time.Duration;


public class BaseTest {


    protected WebDriver driver;
    protected static final Logger logger = LogManager.getLogger(BaseTest.class);

    // Track per-test duration (thread-safe for parallel)
    private static final ThreadLocal<Long> START = new ThreadLocal<>();

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

        switch (result.getStatus()) {
            case ITestResult.FAILURE -> logger.error("❌ TEST FAILED: {} ({} ms)", result.getName(), elapsed);
            case ITestResult.SKIP    -> logger.warn("⚠️ TEST SKIPPED: {} ({} ms)", result.getName(), elapsed);
            default                  -> logger.info("✅ TEST PASSED: {} ({} ms)", result.getName(), elapsed);
        }

        // Thread-safe quit & cleanup
        DriverManager.quit();
        logger.info("Browser closed successfully.");
        START.remove();
    }
}