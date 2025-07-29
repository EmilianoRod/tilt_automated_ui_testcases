package base;

import Utils.Config;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.safari.SafariDriver;

public class DriverFactory {



    public static WebDriver createDriver() {
        String browser = Config.getBrowser();
        boolean headless = Config.isHeadless();

        switch (browser.toLowerCase()) {
            case "chrome":
                WebDriverManager.chromedriver().setup();
                ChromeOptions options = new ChromeOptions();
                if (headless) options.addArguments("--headless=new");
                options.addArguments("--window-size=1920,1080");
                return new ChromeDriver(options);

            case "safari":
                return new SafariDriver();

            default:
                throw new IllegalArgumentException("❌ Unsupported browser: " + browser);
        }
    }


}
