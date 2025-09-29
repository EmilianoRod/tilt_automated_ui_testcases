package base;

import Utils.Config;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;

import java.util.logging.Level;

public class DriverFactory {

    public static WebDriver createDriver() {
        String browser = String.valueOf(Config.getBrowser()).toLowerCase();

        switch (browser) {
            case "chrome":
            default:
                // Ensure a compatible chromedriver is present
                WebDriverManager.chromedriver().setup();

                ChromeOptions options = new ChromeOptions();
                options.setAcceptInsecureCerts(true);

                // Faster & less flaky first-paints → avoids renderer stalls
                options.setPageLoadStrategy(PageLoadStrategy.EAGER);

                // Logs used by BaseTest teardown (browser + performance)
                LoggingPreferences logPrefs = new LoggingPreferences();
                logPrefs.enable(LogType.PERFORMANCE, Level.ALL);
                logPrefs.enable(LogType.BROWSER, Level.ALL);
                options.setCapability("goog:loggingPrefs", logPrefs);

                // Headless (from config)
                if (Config.isHeadless()) {
                    options.addArguments("--headless=new");
                }

                // Stable flags for local & CI
                options.addArguments(
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--disable-gpu",
                        "--disable-backgrounding-occluded-windows",
                        "--disable-features=PaintHolding",
                        "--window-size=1366,900"
                );

                // Optional: respect a custom Chrome binary if provided
                String chromeBinary = System.getProperty("CHROME_BINARY");
                if (chromeBinary != null && !chromeBinary.isBlank()) {
                    options.setBinary(chromeBinary);
                }

                return new ChromeDriver(options);
        }
    }
}
