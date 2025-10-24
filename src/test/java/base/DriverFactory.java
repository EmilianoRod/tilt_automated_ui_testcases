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

public final class DriverFactory {

    private DriverFactory() {}

    private static final boolean IS_CI =
            Optional.ofNullable(System.getenv("CI"))
                    .map(v -> !v.isBlank() && !"false".equalsIgnoreCase(v))
                    .orElse(false);

    public static WebDriver createDriver() {
        final String browser = String.valueOf(Config.getBrowser()).toLowerCase(Locale.ROOT);

        switch (browser) {
            case "chrome":
            default:
                // Selenium Manager by default; allow WebDriverManager via knob
                if (Config.useWebDriverManager()) {
                    String pin = Config.getAny("wdm.chromeMajor", "WDM_CHROME_MAJOR");
                    io.github.bonigarcia.wdm.WebDriverManager wdm = io.github.bonigarcia.wdm.WebDriverManager.chromedriver();
                    if (pin != null && !pin.isBlank()) {
                        wdm.browserVersion(pin + ".0");
                    } else {
                        wdm.browserVersion("138.0"); // sane default pin
                    }
                    wdm.setup();
                }

                ChromeOptions options = new ChromeOptions();
                options.setAcceptInsecureCerts(true);
                options.setPageLoadStrategy(parsePageLoad(Config.getPageLoadStrategyName()));

                // Locale & timezone for deterministic formats
                options.addArguments(
                        "--lang=" + Config.getUiLanguage(),
                        "--timezone-for-testing=" + Config.getUiTimezone(),
                        "--disable-extensions",
                        "--no-first-run",
                        "--no-default-browser-check",
                        "--disable-backgrounding-occluded-windows",
                        "--disable-features=PaintHolding",
                        "--disable-gpu",
                        "--disable-dev-shm-usage",
                        "--force-device-scale-factor=" + Config.getDeviceScale()
                );

                // Window size applied once; headless also needs an explicit size
                String windowSize = "--window-size=" + Config.getWindowSize();
                options.addArguments(windowSize);

                if (Config.isHeadless()) {
                    // Use new headless for modern Chrome
                    options.addArguments("--headless=new", windowSize);
                }

                // Gate sandbox for CI/root-in-container
                if (IS_CI) {
                    options.addArguments("--no-sandbox");
                }

                // Optional custom Chrome binary
                String chromeBinary = Config.getChromeBinaryPath(); // should return "" when unset
                if (chromeBinary != null && !chromeBinary.isBlank()) {
                    options.setBinary(chromeBinary);
                }

                // Chrome prefs (determinism + faster runs)
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
                    // prevent “dangerous download” prompts that can stall headless
                    prefs.put("safebrowsing.enabled", true);
                }
                options.setExperimentalOption("prefs", prefs);

                // Logging (opt-in)
                if (Config.isPerfLoggingEnabled() || Config.isBrowserLoggingEnabled()) {
                    LoggingPreferences logs = new LoggingPreferences();
                    if (Config.isPerfLoggingEnabled())   logs.enable(LogType.PERFORMANCE, Level.ALL);
                    if (Config.isBrowserLoggingEnabled()) logs.enable(LogType.BROWSER, Level.ALL);
                    options.setCapability("goog:loggingPrefs", logs);
                    options.setCapability("app:perfLoggingEnabled", Config.isPerfLoggingEnabled());
                }

                ChromeDriverService service = new ChromeDriverService.Builder()
                        .withVerbose(Boolean.parseBoolean(Config.getAny("chromedriver.verbose", "CHROMEDRIVER_VERBOSE")))
                        .build();

                WebDriver driver = new ChromeDriver(service, options);

                // Timeouts: prefer explicit waits in parallel runs
                long imp = Long.parseLong(Optional.ofNullable(Config.getAny("wd.implicitSec", "WD_IMPLICIT_SEC")).orElse("0"));
                long pl  = Long.parseLong(Optional.ofNullable(Config.getAny("wd.pageLoadSec", "WD_PAGELOAD_SEC")).orElse("60"));
                long js  = Long.parseLong(Optional.ofNullable(Config.getAny("wd.scriptSec", "WD_SCRIPT_SEC")).orElse("30"));

                driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(imp)); // ideally 0
                driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(pl));
                driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(js));

                // Best-effort cleanup if JVM is torn down
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
