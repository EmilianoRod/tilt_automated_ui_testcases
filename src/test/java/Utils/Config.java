package Utils;

import java.io.*;
        import java.nio.file.*;
        import java.util.*;

/**
 * Ultra-robust configuration loader.
 *
 * Precedence (highest to lowest) for each lookup:
 *   1) System property (-Dkey=...)
 *   2) Environment variable (KEY and dot->ENV form)
 *   3) Classpath props: config.local.properties, then config.properties
 *   4) Files in project root if present: env.local, .env.local, .env
 *
 * Also:
 *   - Supports alias keys for the same setting.
 *   - After loading, propagates critical values into system properties in BOTH styles:
 *       dotted (e.g., mailslurp.inboxId) AND UPPER_SNAKE (e.g., MAILSLURP_INBOX_ID)
 */
public class Config {

    private static final Properties props = new Properties();
    private static final List<String> FS_FILES = List.of(
            "env.local",
            ".env.local",
            ".env"
    );

    /** Convert dotted key to ENV style: admin.email -> ADMIN_EMAIL */
    private static String toEnv(String key) {
        return key == null ? null : key.replace('.', '_').toUpperCase(Locale.ROOT);
    }

    /** Load a classpath properties file if present, putting into target props. */
    private static void loadFromClasspath(Properties target, String resourceName) {
        try (InputStream in = Config.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                target.putAll(p);
                System.out.println("ℹ️  Loaded " + resourceName + " from classpath.");
            }
        } catch (IOException e) {
            throw new RuntimeException("❌ Failed to load " + resourceName + " from classpath: " + e.getMessage(), e);
        }
    }

    /** Load a filesystem properties file (KEY=VALUE), if present. */
    private static void loadFromFilesystem(Properties target, String path) {
        Path p = Paths.get(path);
        if (!Files.exists(p)) return;
        try (InputStream in = Files.newInputStream(p)) {
            Properties fp = new Properties();
            fp.load(in);
            target.putAll(fp);
            System.out.println("ℹ️  Loaded " + path + " from filesystem.");
        } catch (IOException e) {
            throw new RuntimeException("❌ Failed to read " + path + ": " + e.getMessage(), e);
        }
    }

    /** First non-blank helper. */
    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    /**
     * Try to resolve a key from: -D → ENV → props (dotted/upper) → filesystem props (already merged).
     * We also try aliases in the same order.
     */
    private static String resolve(String primary, String... aliases) {
        // 1) System properties
        String v = System.getProperty(primary);
        if (v != null && !v.isBlank()) return v;
        for (String a : aliases) {
            v = System.getProperty(a);
            if (v != null && !v.isBlank()) return v;
        }

        // 2) Environment variables (both raw and dotted→ENV)
        v = System.getenv(primary);
        if (v != null && !v.isBlank()) return v;
        v = System.getenv(toEnv(primary));
        if (v != null && !v.isBlank()) return v;

        for (String a : aliases) {
            v = System.getenv(a);
            if (v != null && !v.isBlank()) return v;
            v = System.getenv(toEnv(a));
            if (v != null && !v.isBlank()) return v;
        }

        // 3) Properties loaded (keep both dotted and uppercase)
        v = props.getProperty(primary);
        if (v != null && !v.isBlank()) return v;
        v = props.getProperty(toEnv(primary));
        if (v != null && !v.isBlank()) return v;

        for (String a : aliases) {
            v = props.getProperty(a);
            if (v != null && !v.isBlank()) return v;
            v = props.getProperty(toEnv(a));
            if (v != null && !v.isBlank()) return v;
        }

        return null;
    }

    /** Set a -D system property if absent and value is non-blank. */
    private static void setSysIfAbsent(String key, String value) {
        if (key == null || value == null || value.isBlank()) return;
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    /** Set -D for both dotted and UPPER_SNAKE variants. */
    private static void setBothStylesIfAbsent(String dottedKey, String value) {
        if (dottedKey == null) return;
        setSysIfAbsent(dottedKey, value);
        setSysIfAbsent(toEnv(dottedKey), value);
    }

    static {
        // ---- 1) Classpath properties (base then local overrides) ----
        loadFromClasspath(props, "config.properties");
        loadFromClasspath(props, "config.local.properties");

        // ---- 2) Filesystem properties (optional) ----
        for (String f : FS_FILES) {
            loadFromFilesystem(props, f);
        }

        // ---- 3) Resolve critical values then propagate to -D in BOTH styles ----

        // MailSlurp API key
        String mailSlurpKey = resolve(
                "mailslurp.apiKey",
                "mailslurp.forceKey",
                "MAILSLURP_API_KEY"
        );
        // MailSlurp inbox id
        String mailSlurpInbox = resolve(
                "mailslurp.inboxId",
                "MAILSLURP_INBOX_ID"
        );
        // MailSlurp allowCreate (boolean-ish)
        String mailSlurpAllowCreate = resolve(
                "mailslurp.allowCreate",
                "MAILSLURP_ALLOW_CREATE"
        );

        // Propagate: prefer setting BOTH dotted and UPPERCASE so any code path can read them
        setBothStylesIfAbsent("mailslurp.apiKey", mailSlurpKey);
        setBothStylesIfAbsent("mailslurp.forceKey", mailSlurpKey);
        setBothStylesIfAbsent("mailslurp.inboxId", mailSlurpInbox);
        setBothStylesIfAbsent("mailslurp.allowCreate", mailSlurpAllowCreate);

        // Optional: Stripe common aliases to -D (dotted form; most of our code reads dotted)
        String stripeTestSecret = resolve("stripe.test.secret.key",
                "STRIPE_TEST_SECRET_KEY", "STRIPE_SECRET_KEY", "stripe.secret.key");
        setBothStylesIfAbsent("stripe.test.secret.key", stripeTestSecret);

        String stripeWebhookSecret = resolve("stripe.webhook.secret", "STRIPE_WEBHOOK_SECRET");
        setBothStylesIfAbsent("stripe.webhook.secret", stripeWebhookSecret);

        String stripePublishable = resolve("stripe.publishable.key", "STRIPE_PUBLISHABLE_KEY");
        setBothStylesIfAbsent("stripe.publishable.key", stripePublishable);

        String baseUrl = resolve("baseUrl", "base.url", "BASE_URL");
        setBothStylesIfAbsent("baseUrl", baseUrl);

        // Friendly logging for troubleshooting (single line)
        System.out.println("[Config] Effective sources prepared. "
                + "mailslurp.apiKey? " + (System.getProperty("mailslurp.apiKey") != null)
                + " | MAILSLURP_API_KEY? " + (System.getProperty("MAILSLURP_API_KEY") != null)
                + " | inboxId(dotted)? " + (System.getProperty("mailslurp.inboxId") != null)
                + " | MAILSLURP_INBOX_ID? " + (System.getProperty("MAILSLURP_INBOX_ID") != null)
                + " | allowCreate=" + String.valueOf(getMailSlurpAllowCreate())
                + " | baseUrl=" + (baseUrl != null ? baseUrl : "(null)"));
    }

    // ---------- Public API ----------

    /** Generic get with priority: -D → ENV → props (dotted or UPPERCASE) */
    public static String get(String key) {
        return resolve(key);
    }

    /** Generic get with aliases (same precedence) */
    public static String getAny(String primary, String... aliases) {
        return resolve(primary, aliases);
    }

    public static String get(String key, String def) {
        String v = get(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    public static int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public static boolean getBoolean(String key) {
        String v = get(key);
        return v != null && (v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes"));
    }

    // ---------- Convenience getters ----------

    public static String getBaseUrl() {
        return firstNonBlank(
                get("baseUrl"),
                get("base.url"),
                get("BASE_URL"),
                "https://tilt-dashboard-dev.tilt365.com/"
        );
    }

    public static int getTimeout() {
        String v = getAny("timeout", "TIMEOUT");
        return Integer.parseInt(v != null ? v : "10");
    }

    public static boolean isHeadless() {
        String v = getAny("headless", "HEADLESS");
        return v == null ? true : getBoolean(v) || v.equals("true");
    }

    public static String getBrowser() {
        String v = getAny("browser", "BROWSER");
        return v != null ? v : "chrome";
    }

    public static String getScreenshotPath() {
        String v = getAny("screenshotPath", "SCREENSHOT_PATH");
        return v != null ? v : "screenshots";
    }



    public static String getAdminEmail() {
        // add ADMIN_USER as an alias
        return getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
    }

    public static String getAdminPassword() {
        // add ADMIN_PASS as an alias
        return getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
    }


    // ---------- Stripe helpers ----------

    /** Returns a Stripe *test* key, resolving multiple aliases. */
    public static String getStripeSecretKey() {
        String k = getAny(
                "stripe.test.secret.key",
                "STRIPE_TEST_SECRET_KEY",
                "stripe.secret.key",
                "STRIPE_SECRET_KEY"
        );
        if (k != null && !k.isBlank() && !(k.startsWith("rk_test_") || k.startsWith("sk_test_"))) {
            System.err.println("⚠️  Non-test Stripe key detected (expected rk_test_/sk_test_). Prefix: "
                    + k.substring(0, Math.min(10, k.length())) + "…");
        }
        return k;
    }

    public static String getStripeWebhookSecret() {
        return getAny("stripe.webhook.secret", "STRIPE_WEBHOOK_SECRET");
    }

    public static String getStripePublishableKey() {
        return getAny("stripe.publishable.key", "STRIPE_PUBLISHABLE_KEY");
    }

    public static String getStripeSuccessUrl() {
        return getAny("stripe.success.url", "STRIPE_SUCCESS_URL");
    }

    // ---------- MailSlurp helpers ----------

    public static String getMailSlurpApiKey() {
        return getAny("mailslurp.apiKey", "MAILSLURP_API_KEY", "mailslurp.forceKey");
    }

    public static String getMailSlurpInboxId() {
        return getAny("mailslurp.inboxId", "MAILSLURP_INBOX_ID");
    }

    public static boolean getMailSlurpAllowCreate() {
        String v = getAny("mailslurp.allowCreate", "MAILSLURP_ALLOW_CREATE");
        if (v == null) return true  ; // default: fixed inbox mode
        return v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes");
    }

    // ---------- Misc ----------

    public static int getDefaultTimeout() {
        return getTimeout();
    }

    public static String joinUrl(String base, String path) {
        if (base == null || base.isBlank()) return path;
        if (base.endsWith("/")) return base + (path.startsWith("/") ? path.substring(1) : path);
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    /** Optional helper for debugging: dump a few important values */
    public static void debugPrintImportant() {
        System.out.println(
                "[Config] baseUrl=" + getBaseUrl()
                        + " | headless=" + isHeadless()
                        + " | browser=" + getBrowser()
                        + " | mailslurp.apiKey? " + (System.getProperty("mailslurp.apiKey") != null)
                        + " | MAILSLURP_API_KEY? " + (System.getProperty("MAILSLURP_API_KEY") != null)
                        + " | mailslurp.inboxId=" + System.getProperty("mailslurp.inboxId")
                        + " | MAILSLURP_INBOX_ID=" + System.getProperty("MAILSLURP_INBOX_ID")
                        + " | allowCreate=" + getMailSlurpAllowCreate()
        );
    }




    // ---------- UI/runtime knobs for WebDriver ----------

    public static String getUiLanguage() {
        return firstNonBlank(getAny("ui.lang", "UI_LANG"), "en-US");
    }

    public static String getUiTimezone() {
        return firstNonBlank(getAny("ui.tz", "UI_TZ"), "America/Montevideo");
    }

    public static String getWindowSize() {
        // e.g., "1440,900"
        return firstNonBlank(getAny("ui.window", "UI_WINDOW"), "1440,900");
    }

    public static String getDeviceScale() {
        // string because Chrome flag expects string
        return firstNonBlank(getAny("ui.scale", "UI_SCALE"), "1");
    }

    public static String getPageLoadStrategyName() {
        // "normal" | "eager" | "none"
        return firstNonBlank(getAny("pageLoad", "PAGE_LOAD"), "normal");
    }

    public static boolean useWebDriverManager() {
        String v = getAny("useWDM", "USE_WDM");
        return v != null && (v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes"));
    }

    public static String getChromeBinaryPath() {
        return firstNonBlank(getAny("CHROME_BINARY", "chrome.binary"), "");
    }

    public static boolean isPerfLoggingEnabled() {
        String v = getAny("logs.performance", "LOGS_PERFORMANCE");
        return v != null && (v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes"));
    }

    public static boolean isBrowserLoggingEnabled() {
        String v = getAny("logs.browser", "LOGS_BROWSER");
        // default true unless explicitly disabled
        return v == null || v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes");
    }

}
