package base;

import Utils.Config;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.safari.SafariDriver;

public class DriverFactory {



    public static WebDriver createDriver() {
        String browser = Config.getBrowser().toLowerCase();

        switch (browser) {
            case "chrome":
            default:
                WebDriverManager.chromedriver().setup();
                ChromeOptions options = new ChromeOptions();

                // headless from config (defaults to true via your Config)
                if (Config.isHeadless()) {
                    options.addArguments("--headless=new");
                }

                // a couple of safe defaults for CI/local parity
                options.addArguments("--window-size=1920,1080");
                options.addArguments("--disable-dev-shm-usage");
                options.addArguments("--no-sandbox");

                options.setAcceptInsecureCerts(true);
                return new ChromeDriver(options);
        }
    }


}
