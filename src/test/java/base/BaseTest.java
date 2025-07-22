package base;

import Utils.Config;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.safari.SafariDriver;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

public class BaseTest {
    protected WebDriver driver;


    @BeforeClass
    public void setUp() {
        String browser = Config.getBrowser(); // from config.properties or -Dbrowser=...
        boolean headless = Config.isHeadless();

        switch (browser.toLowerCase()) {
            case "chrome":
                WebDriverManager.chromedriver().setup();
                ChromeOptions options = new ChromeOptions();
                if (headless) {
                    options.addArguments("--headless=new"); // Use "--headless=new" for modern Chrome
                }
                options.addArguments("--window-size=1920,1080");
                driver = new ChromeDriver(options);
                break;

            case "safari":
                driver = new SafariDriver(); // make sure Remote Automation is enabled
                break;

            default:
                throw new IllegalArgumentException("❌ Unsupported browser: " + browser);
        }

        driver.manage().window().maximize();
    }

    @AfterClass
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }


}