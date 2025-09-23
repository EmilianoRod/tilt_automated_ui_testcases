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
 *   - After loading, propagates MailSlurp values into system properties:
 *       mailslurp.apiKey, mailslurp.forceKey, mailslurp.inboxId
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
        // primary
        v = System.getenv(primary);
        if (v != null && !v.isBlank()) return v;
        v = System.getenv(toEnv(primary));
        if (v != null && !v.isBlank()) return v;

        // aliases
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

    static {
        // ---- 1) Classpath properties (base then local overrides) ----
        loadFromClasspath(props, "config.properties");
        loadFromClasspath(props, "config.local.properties");

        // ---- 2) Filesystem properties (optional) ----
        for (String f : FS_FILES) {
            loadFromFilesystem(props, f);
        }

        // ---- 3) After everything is loaded, propagate critical aliases to -D ----
        // MailSlurp API key sources
        String mailSlurpKey = resolve(
                "mailslurp.apiKey",
                "mailslurp.forceKey",
                "MAILSLURP_API_KEY"
        );
        // MailSlurp inbox id sources
        String mailSlurpInbox = resolve(
                "mailslurp.inboxId",
                "MAILSLURP_INBOX_ID"
        );

        // Propagate to system properties so any static initializers (e.g., MailSlurpUtils) will find them
        setSysIfAbsent("mailslurp.apiKey", mailSlurpKey);
        setSysIfAbsent("mailslurp.forceKey", mailSlurpKey);
        setSysIfAbsent("mailslurp.inboxId", mailSlurpInbox);

        // Optional: Stripe common aliases to -D if you want JVM-wide access
        String stripeTestSecret = resolve("stripe.test.secret.key",
                "STRIPE_TEST_SECRET_KEY", "STRIPE_SECRET_KEY", "stripe.secret.key");
        setSysIfAbsent("stripe.test.secret.key", stripeTestSecret);

        String stripeWebhookSecret = resolve("stripe.webhook.secret", "STRIPE_WEBHOOK_SECRET");
        setSysIfAbsent("stripe.webhook.secret", stripeWebhookSecret);

        String stripePublishable = resolve("stripe.publishable.key", "STRIPE_PUBLISHABLE_KEY");
        setSysIfAbsent("stripe.publishable.key", stripePublishable);

        String baseUrl = resolve("baseUrl", "base.url", "BASE_URL");
        setSysIfAbsent("baseUrl", baseUrl);

        // Friendly logging for troubleshooting (single line)
        System.out.println("[Config] Effective sources prepared. "
                + "mailslurp.apiKey? " + (System.getProperty("mailslurp.apiKey") != null)
                + " | inboxId? " + (System.getProperty("mailslurp.inboxId") != null)
                + " | baseUrl=" + (baseUrl != null ? baseUrl : "(null)"));
    }

    // ---------- Public API ----------

    /** Generic get with priority: -D → ENV → props (dotted or UPPERCASE) */
    public static String get(String key) {
        return resolve(key);
    }

    /** Generic get with aliases (same precedence) */
    public static String getAny(String primary, String... aliases) {
        String v = resolve(primary, aliases);
        return v;
    }

    public static String get(String key, String def) {
        String v = get(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    public static int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public static boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    // ---------- Convenience getters (with aliases) ----------

    public static String getBaseUrl() {
        return get("baseUrl",        // dotted
                get("base.url",   // dotted variant
                        get("BASE_URL", "https://tilt-dashboard-dev.tilt365.com/")));
    }

    public static int getTimeout() {
        String v = getAny("timeout", "TIMEOUT");
        return Integer.parseInt(v != null ? v : "10");
    }

    public static boolean isHeadless() {
        String v = getAny("headless", "HEADLESS");
        return Boolean.parseBoolean(v != null ? v : "true");
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
        return getAny("admin.email", "ADMIN_EMAIL");
    }

    public static String getAdminPassword() {
        return getAny("admin.password", "ADMIN_PASSWORD");
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
                        + " | mailslurp.inboxId=" + System.getProperty("mailslurp.inboxId")
        );
    }
}
