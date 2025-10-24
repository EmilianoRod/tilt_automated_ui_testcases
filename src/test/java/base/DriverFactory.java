package base;

import Utils.Config;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public class DriverFactory {

    private static final boolean IS_CI =
            Optional.ofNullable(System.getenv("CI"))
                    .map(v -> !v.isBlank() && !"false".equalsIgnoreCase(v))
                    .orElse(false);

    public static WebDriver createDriver() {
        String browser = String.valueOf(Config.getBrowser()).toLowerCase(Locale.ROOT);

        switch (browser) {
            case "chrome":
            default:
                // Selenium Manager by default; allow WebDriverManager via knob
                if (Config.useWebDriverManager()) {
                    String pin = Config.getAny("wdm.chromeMajor", "WDM_CHROME_MAJOR");
                    io.github.bonigarcia.wdm.WebDriverManager wdm = io.github.bonigarcia.wdm.WebDriverManager.chromedriver();
                    if (pin != null && !pin.isBlank()) {
                        wdm.browserVersion(pin + ".0");
                    }
                    wdm.setup();
                }

                ChromeOptions options = new ChromeOptions();
                options.setAcceptInsecureCerts(true);

                // Page load strategy (normal|eager|none)
                options.setPageLoadStrategy(parsePageLoad(Config.getPageLoadStrategyName()));

                // Locale & timezone for deterministic formats
                options.addArguments("--lang=" + Config.getUiLanguage());
                options.addArguments("--timezone-for-testing=" + Config.getUiTimezone());

                // Headless
                if (Config.isHeadless()) {
                    options.addArguments("--headless=new");
                }

                // Stable flags (gate --no-sandbox to CI/root-in-container)
                options.addArguments(
                        "--disable-dev-shm-usage",
                        "--disable-gpu",
                        "--disable-features=PaintHolding",
                        "--disable-backgrounding-occluded-windows",
                        "--window-size=" + Config.getWindowSize(),
                        "--force-device-scale-factor=" + Config.getDeviceScale()
                );
                if (IS_CI) {
                    options.addArguments("--no-sandbox");
                }

                // Optional: custom Chrome binary (NULL-SAFE)
                String chromeBinary = Config.getChromeBinaryPath(); // should return "" when unset
                if (chromeBinary != null && !chromeBinary.isBlank()) {
                    options.setBinary(chromeBinary);
                }

                // Chrome prefs (speed-ups & determinism)
                Map<String, Object> prefs = new HashMap<>();
                if (Boolean.parseBoolean(Config.getAny("chrome.disableImages", "CHROME_DISABLE_IMAGES"))) {
                    Map<String, Object> content = Map.of("images", 2); // 2 = block
                    prefs.put("profile.managed_default_content_settings", content);
                }
                prefs.put("credentials_enable_service", false);
                prefs.put("profile.password_manager_enabled", false);
                prefs.put("profile.default_content_setting_values.notifications", 2);

                String downloadDir = Config.getAny("download.dir", "DOWNLOAD_DIR");
                if (downloadDir != null && !downloadDir.isBlank()) {
                    prefs.put("download.default_directory", Path.of(downloadDir).toAbsolutePath().toString());
                    prefs.put("download.prompt_for_download", false);
                }
                options.setExperimentalOption("prefs", prefs);

                // Logging (opt-in perf; browser logs on by default unless disabled)
                if (Config.isPerfLoggingEnabled() || Config.isBrowserLoggingEnabled()) {
                    LoggingPreferences logs = new LoggingPreferences();
                    if (Config.isPerfLoggingEnabled())   logs.enable(LogType.PERFORMANCE, Level.ALL);
                    if (Config.isBrowserLoggingEnabled()) logs.enable(LogType.BROWSER, Level.ALL);
                    options.setCapability("goog:loggingPrefs", logs);
                }

                ChromeDriverService service = new ChromeDriverService.Builder()
                        .withVerbose(Boolean.parseBoolean(Config.getAny("chromedriver.verbose", "CHROMEDRIVER_VERBOSE")))
                        .build();

                WebDriver driver = new ChromeDriver(service, options);

                // Timeouts: keep implicit at 0; prefer explicit waits
                long imp = Long.parseLong(Optional.ofNullable(Config.getAny("wd.implicitSec", "WD_IMPLICIT_SEC")).orElse("0"));
                long pl  = Long.parseLong(Optional.ofNullable(Config.getAny("wd.pageLoadSec", "WD_PAGELOAD_SEC")).orElse("60"));
                long js  = Long.parseLong(Optional.ofNullable(Config.getAny("wd.scriptSec", "WD_SCRIPT_SEC")).orElse("30"));

                driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(imp));
                driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(pl));
                driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(js));

                // Ensure clean shutdown even on abrupt exits
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try { driver.quit(); } catch (Throwable ignored) {}
                }));

                return driver;
        }
    }

    private static PageLoadStrategy parsePageLoad(String s) {
        switch (String.valueOf(s).toLowerCase(Locale.ROOT)) {
            case "eager":  return PageLoadStrategy.EAGER;
            case "none":   return PageLoadStrategy.NONE;
            case "normal":
            default:       return PageLoadStrategy.NORMAL;
        }
    }
}


