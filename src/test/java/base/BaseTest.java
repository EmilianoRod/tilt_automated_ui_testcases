package base;

import Utils.Config;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import io.qameta.allure.Attachment;


public class BaseTest {


    protected WebDriver driver;


    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        driver = DriverFactory.createDriver();
        driver.manage().window().maximize();
        driver.get(Config.getBaseUrl());
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        if (ITestResult.FAILURE == result.getStatus()) {
            takeScreenshot(result.getName());
        }
        if (driver != null) {
            driver.quit();
        }
    }

    @Attachment(value = "Screenshot on Failure", type = "image/png")
    public byte[] takeScreenshot(String testName) {
        return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
    }


}