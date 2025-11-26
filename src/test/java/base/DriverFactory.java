package base;

import Utils.Config;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.SessionNotCreatedException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                ChromeOptions options = buildChromeOptions();

                // Build service with your existing verbosity knob
                ChromeDriverService service = new ChromeDriverService.Builder()
                        .withVerbose(Boolean.parseBoolean(Config.getAny("chromedriver.verbose", "CHROMEDRIVER_VERBOSE")))
                        .build();

                // ---- Stage 1: try Chrome-managed driver (best path on Chrome >=115) ----
                try {
                    return bootChrome(service, options);
                } catch (SessionNotCreatedException snce) {
                    // Typical mismatch message contains: "Current browser version is 142.0.x"
                    String major = extractChromeMajor(snce.getMessage());
                    String pinFromEnv = Config.getAny("wdm.chromeMajor", "WDM_CHROME_MAJOR"); // optional override

                    // ---- Stage 2: fall back to WebDriverManager with the parsed major (or your override) ----
                    try {
                        if (Config.useWebDriverManager()) {
                            io.github.bonigarcia.wdm.WebDriverManager wdm = io.github.bonigarcia.wdm.WebDriverManager.chromedriver();
                            if (major != null && !major.isBlank()) {
                                wdm.browserVersion(major + ".0");
                            } else if (pinFromEnv != null && !pinFromEnv.isBlank()) {
                                wdm.browserVersion(pinFromEnv.trim() + ".0");
                            } // else: let WDM decide latest suitable
                            wdm.setup();
                        }
                        return bootChrome(service, options);
                    } catch (SessionNotCreatedException retryFail) {
                        throw retryFail; // bubble up if even the fallback cannot start
                    }
                }
        }
    }

    // Build ChromeOptions exactly like you had, with all existing knobs preserved.
    private static ChromeOptions buildChromeOptions() {
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
                "--force-device-scale-factor=" + Config.getDeviceScale(),
                "--disable-pdf-viewer",
                "--pdfjs-disable",
                "--no-sandbox",
               "--remote-allow-origins=*"

        );

        // Window size applied once; headless also needs an explicit size
        String windowSizeArg = "--window-size=" + Config.getWindowSize();
        options.addArguments(windowSizeArg);

        if (Config.isHeadless()) {
            // Use modern headless
            options.addArguments("--headless=new", windowSizeArg);
        }

        // Gate sandbox for CI/root-in-container
        if (IS_CI) {
            options.addArguments("--no-sandbox");
        }

        // Optional custom Chrome binary
        String chromeBinary = Config.getChromeBinaryPath(); // returns null/blank when unset
        if (chromeBinary != null && !chromeBinary.isBlank()) {
            options.setBinary(chromeBinary);
        }

        // ---------- Chrome prefs (merged, single map) ----------
        Map<String, Object> prefs = new HashMap<>();

        // Optional: disable images
        if (Boolean.parseBoolean(Config.getAny("chrome.disableImages", "CHROME_DISABLE_IMAGES"))) {
            Map<String, Object> content = Map.of("images", 2); // 2 = block
            prefs.put("profile.managed_default_content_settings", content);
        }

        // Disable credential services / notifications
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.default_content_setting_values.notifications", 2);

        // Download directory: env override, else default to target/downloads
        String dlFromEnv = Config.getAny("download.dir", "DOWNLOAD_DIR");
        String resolvedDownloadDir;
        if (dlFromEnv != null && !dlFromEnv.isBlank()) {
            resolvedDownloadDir = Path.of(dlFromEnv).toAbsolutePath().toString();
        } else {
            resolvedDownloadDir = Path.of("target", "downloads").toAbsolutePath().toString();
        }

        // Core download prefs (for your report tests)
        prefs.put("download.default_directory", resolvedDownloadDir);
        prefs.put("download.prompt_for_download", false);
        prefs.put("download.directory_upgrade", true);
        // prevent “dangerous download” prompts that can stall headless
        prefs.put("safebrowsing.enabled", true);
        // Make PDFs download instead of opening with viewer
        prefs.put("plugins.always_open_pdf_externally", true);

        options.setExperimentalOption("prefs", prefs);
        // ---------- end prefs ----------

        // Logging (opt-in)
        if (Config.isPerfLoggingEnabled() || Config.isBrowserLoggingEnabled()) {
            LoggingPreferences logs = new LoggingPreferences();
            if (Config.isPerfLoggingEnabled())   logs.enable(LogType.PERFORMANCE, Level.ALL);
            if (Config.isBrowserLoggingEnabled()) logs.enable(LogType.BROWSER, Level.ALL);
            options.setCapability("goog:loggingPrefs", logs);
            options.setCapability("app:perfLoggingEnabled", Config.isPerfLoggingEnabled());
        }

        return options;
    }

    // Actually start ChromeDriver and apply your timeouts the same way you already do
    private static WebDriver bootChrome(ChromeDriverService service, ChromeOptions options) {
        WebDriver driver = new ChromeDriver(service, options);

        // Timeouts: prefer explicit waits in parallel runs; keep your knobs
        long imp = Long.parseLong(Optional.ofNullable(Config.getAny("wd.implicitSec", "WD_IMPLICIT_SEC")).orElse("0"));
        long pl  = Long.parseLong(Optional.ofNullable(Config.getAny("wd.pageLoadSec", "WD_PAGELOAD_SEC")).orElse("30"));
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

    private static String extractChromeMajor(String errorMsg) {
        if (errorMsg == null) return null;
        // look for "Current browser version is 142.0.7444.60"
        Matcher m = Pattern.compile("Current browser version is ([0-9]+)\\.", Pattern.CASE_INSENSITIVE).matcher(errorMsg);
        if (m.find()) return m.group(1);
        // fallback: look for "... is 142" (rare)
        m = Pattern.compile("version is\\s+([0-9]+)\\b", Pattern.CASE_INSENSITIVE).matcher(errorMsg);
        return m.find() ? m.group(1) : null;
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
