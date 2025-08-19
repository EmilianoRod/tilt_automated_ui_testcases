package base;

import Utils.Config;
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


public class BaseTest {


    protected WebDriver driver;
    protected static final Logger logger = LogManager.getLogger(BaseTest.class);

    @BeforeMethod(alwaysRun = true)
    public void setUp(Method method) {
        logger.info("========== STARTING TEST: {} ==========", method.getName());
        driver = DriverFactory.createDriver();
        driver.manage().window().maximize();
        driver.get(Config.getBaseUrl());
        logger.info("Navigated to URL: {}", Config.getBaseUrl());
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        if (ITestResult.FAILURE == result.getStatus()) {
            takeScreenshot(result.getName());
            logger.error("❌ TEST FAILED: {}", result.getName());
        } else {
            logger.info("✅ TEST PASSED: {}", result.getName());
        }
        if (driver != null) {
            driver.quit();
            logger.info("Browser closed successfully.");
        }
    }

    @Attachment(value = "Screenshot on Failure", type = "image/png")
    public byte[] takeScreenshot(String testName) {
        return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
    }


}