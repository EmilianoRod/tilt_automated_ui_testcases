package Utils;


import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/**
 * Centralized test configuration facade.
 * Priority: System property (-D) > Environment variable > .env.local file > defaults.
 */
public final class Config {

    private static final Properties props = new Properties();
    private static boolean loaded = false;

    private Config() {}

    /* ===========================
     * Loader
     * =========================== */

    static {
        load();
    }

    private static void load() {
        if (loaded) return;

        Path envFile = Paths.get(".env.local");
        if (Files.exists(envFile)) {
            try {
                System.out.println("[Config] Loaded: " + envFile.toAbsolutePath());
                List<String> lines = Files.readAllLines(envFile);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    int eq = trimmed.indexOf('=');
                    if (eq > 0) {
                        String key = trimmed.substring(0, eq).trim();
                        String val = trimmed.substring(eq + 1).trim();
                        props.setProperty(key, val);
                    }
                }
            } catch (IOException e) {
                System.err.println("[Config] Failed to read .env.local: " + e.getMessage());
            }
        } else {
            System.out.println("[Config] No .env.local found — relying on system/env vars.");
        }

        loaded = true;
    }

    /* ===========================
     * Generic helpers
     * =========================== */

    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String s : vals) {
            if (s != null && !s.isBlank()) return s;
        }
        return null;
    }

    private static String getFromPropsCaseInsensitive(String key) {
        for (String k : props.stringPropertyNames()) {
            if (k.equalsIgnoreCase(key))
                return props.getProperty(k).trim();
        }
        return null;
    }

    private static String getFromEnvCaseInsensitive(String key) {
        for (Map.Entry<String, String> e : System.getenv().entrySet()) {
            if (e.getKey().equalsIgnoreCase(key))
                return e.getValue().trim();
        }
        return null;
    }

    /** Return the first non-blank among sysProp, env, .env.local. */
    public static String getAny(String... keys) {
        for (String key : keys) {
            if (key == null) continue;

            String sys = System.getProperty(key);
            if (sys != null && !sys.isBlank()) return sys.trim();

            String env = getFromEnvCaseInsensitive(key);
            if (env != null && !env.isBlank()) return env.trim();

            String prop = getFromPropsCaseInsensitive(key);
            if (prop != null && !prop.isBlank()) return prop.trim();
        }
        return null;
    }

    public static String get(String key, String envKey, String defaultVal) {
        String v = firstNonBlank(
                System.getProperty(key),
                getFromEnvCaseInsensitive(envKey),
                getFromPropsCaseInsensitive(key)
        );
        return (v == null || v.isBlank()) ? defaultVal : v.trim();
    }

    public static String get(String key, String env1, String env2, String defaultVal) {
        String v = firstNonBlank(
                System.getProperty(key),
                getFromEnvCaseInsensitive(env1),
                getFromEnvCaseInsensitive(env2),
                getFromPropsCaseInsensitive(key)
        );
        return (v == null || v.isBlank()) ? defaultVal : v.trim();
    }

    public static String joinUrl(String base, String path) {
        if (base == null) base = "";
        if (path == null) path = "";
        String a = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String b = path.startsWith("/") ? path : "/" + path;
        return a + b;
    }





    public static String getBaseUrl() {
        // Try sysprop, env var, then .env.local
        String baseUrl = getAny("baseUrl", "BASE_URL");

        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl.trim();
        }

        throw new IllegalStateException(
                "BASE_URL is not configured. Set it in .env.local or pass -DbaseUrl=..."
        );
    }









    public static String getAdminEmail() {
        return getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
    }

    public static String getAdminPassword() {
        return getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
    }

    /* ===========================
     * Browser / WebDriver
     * =========================== */

    public static String getBrowser() {
        return get("browser", "BROWSER", "chrome").toLowerCase(Locale.ROOT);
    }

    public static boolean useWebDriverManager() {
        String v = get("webdriver.manager", "WEBDRIVER_MANAGER", "true");
        return v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes");
    }

    public static String getPageLoadStrategyName() {
        return get("page.load.strategy", "PAGE_LOAD_STRATEGY", "normal").toLowerCase(Locale.ROOT);
    }

    public static String getUiLanguage() {
        return get("ui.lang", "UI_LANG", "en-US");
    }

    public static String getUiTimezone() {
        return get("ui.tz", "UI_TZ", "America/Montevideo");
    }

    public static double getDeviceScale() {
        String raw = get("device.scale", "DEVICE_SCALE", "1.0");
        try { return Double.parseDouble(raw); } catch (Exception ignore) { return 1.0; }
    }

    public static String getWindowSize() {
        return get("window.size", "WINDOW_SIZE", "1920x1080");
    }

    public static boolean isHeadless() {
        String ci = System.getenv("CI");
        String raw = get("headless", "HEADLESS",
                (ci != null && !ci.isBlank()) ? "true" : "false");
        return raw.equalsIgnoreCase("true") || raw.equals("1") || raw.equalsIgnoreCase("yes");
    }

    public static String getChromeBinaryPath() {
        return getAny("chrome.binary", "CHROME_BIN");
    }

    public static boolean isPerfLoggingEnabled() {
        String raw = get("perf.log", "PERF_LOG", "false");
        return raw.equalsIgnoreCase("true") || raw.equals("1") || raw.equalsIgnoreCase("yes");
    }

    public static boolean isBrowserLoggingEnabled() {
        String raw = get("browser.log", "BROWSER_LOG", "true");
        return raw.equalsIgnoreCase("true") || raw.equals("1") || raw.equalsIgnoreCase("yes");
    }

    /* ===========================
     * Timeouts
     * =========================== */

    public static Duration getTimeout() {
        String raw = getAny("timeout", "timeout.seconds", "TIMEOUT_SECONDS", "20");
        int seconds;
        try { seconds = Integer.parseInt(raw.trim()); } catch (Exception ignore) { seconds = 20; }
        return Duration.ofSeconds(seconds);
    }

    public static long getTimeoutMillis() {
        return getTimeout().toMillis();
    }

    /* ===========================
     * Stripe
     * =========================== */

//    public static String getStripeSecretKey() {
//        return getAny("stripe.secretKey", "STRIPE_SECRET_KEY", "STRIPE_TEST_SECRET_KEY");
//    }

    public static String getStripeSecretKey() {
        String fromSys = System.getProperty("stripe.secretKey");
        String fromEnv1 = System.getenv("STRIPE_SECRET_KEY");
        String fromEnv2 = System.getenv("STRIPE_TEST_SECRET_KEY");
        String fromProps = getFromPropsCaseInsensitive("stripe.secretKey");

        System.out.println("[StripeDebug] sysProp=" + fromSys);
        System.out.println("[StripeDebug] STRIPE_SECRET_KEY=" + fromEnv1);
        System.out.println("[StripeDebug] STRIPE_TEST_SECRET_KEY=" + fromEnv2);
        System.out.println("[StripeDebug] props(.env.local)=" + fromProps);

        return getAny("stripe.secretKey", "STRIPE_SECRET_KEY", "STRIPE_TEST_SECRET_KEY");
    }


    public static boolean isStripeE2EEnabled() {
        String raw = get("STRIPE_E2E", "STRIPE_E2E", "false");
        return raw.equalsIgnoreCase("true") || raw.equals("1") || raw.equalsIgnoreCase("yes");
    }

    /* ===========================
     * MailSlurp
     * =========================== */

    public static String getMailSlurpApiKey() {
        return getAny("mailslurp.forceKey", "mailslurp.apiKey", "MAILSLURP_API_KEY");
    }

    public static boolean getMailSlurpAllowCreate() {
        String raw = getAny("ALLOW_CREATE_INBOX_FALLBACK",
                "mailslurp.allowCreate", "MAILSLURP_ALLOW_CREATE");
        if (raw == null) return System.getenv("CI") == null;
        return raw.equalsIgnoreCase("true") || raw.equals("1") || raw.equalsIgnoreCase("yes");
    }

    public static String getMailSlurpFixedInboxId() {
        return getAny("MAILSLURP_INBOX_ID", "mailslurp.inboxId");
    }

    /* ===========================
     * Misc quick getters
     * =========================== */

    public static boolean getBoolean(String sysProp, String env, boolean defVal) {
        String raw = get(sysProp, env, defVal ? "true" : "false");
        return raw.equalsIgnoreCase("true") || raw.equals("1") || raw.equalsIgnoreCase("yes");
    }

    public static int getInt(String sysProp, String env, int defVal) {
        String raw = get(sysProp, env, String.valueOf(defVal));
        try { return Integer.parseInt(raw.trim()); } catch (Exception ignore) { return defVal; }
    }

    public static double getDouble(String sysProp, String env, double defVal) {
        String raw = get(sysProp, env, String.valueOf(defVal));
        try { return Double.parseDouble(raw.trim()); } catch (Exception ignore) { return defVal; }
    }
}
