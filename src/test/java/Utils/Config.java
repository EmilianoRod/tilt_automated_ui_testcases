package Utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {

    private static final Properties props = new Properties();

    /** Convert dot keys to ENV style: admin.email -> ADMIN_EMAIL */
    private static String envKey(String key) {
        return key.replace('.', '_').toUpperCase();
    }

    static {
        // 1) Base config.properties
        try (InputStream input = Config.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                props.load(input);
            } else {
                System.err.println("⚠️  config.properties not found on classpath.");
            }
        } catch (IOException e) {
            throw new RuntimeException("❌ Failed to load config.properties: " + e.getMessage(), e);
        }

        // 2) Optional config.local.properties (overrides)
        try (InputStream local = Config.class.getClassLoader().getResourceAsStream("config.local.properties")) {
            if (local != null) {
                Properties override = new Properties();
                override.load(local);
                props.putAll(override);
                System.out.println("ℹ️  Loaded config.local.properties overrides.");
            }
        } catch (IOException e) {
            throw new RuntimeException("❌ Failed to load config.local.properties: " + e.getMessage(), e);
        }
    }

    /** Generic get with priority: -D → ENV → properties (dotted). */
    public static String get(String key) {
        // 1) -Dkey=...
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) return sys;

        // 2) ENV (dots->underscores, uppercased)
        String env = System.getenv(envKey(key));
        if (env != null && !env.isBlank()) return env;

        // 3) config props (dotted)
        return props.getProperty(key);
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

    // ---- Convenience getters ----
    public static String getBaseUrl() {
        return get("baseUrl", "https://tilt-dashboard-dev.tilt365.com/");
    }

    public static int getTimeout() {
        return Integer.parseInt(get("timeout", "10"));
    }

    public static boolean isHeadless() {
        return Boolean.parseBoolean(get("headless", "true"));
    }

    public static String getBrowser() {
        return get("browser", "chrome");
    }

    public static String getScreenshotPath() {
        return get("screenshotPath", "screenshots");
    }

    public static String getAdminEmail() {
        return get("admin.email");
    }

    public static String getAdminPassword() {
        return get("admin.password");
    }

    // ---- Stripe helpers ----

    /** Returns a Stripe *test* key. Supports ENV or UPPERCASE properties. */
    public static String getStripeSecretKey() {
        String k = firstNonBlank(
                // preferred test-mode vars
                System.getenv("STRIPE_TEST_SECRET_KEY"),
                props.getProperty("STRIPE_TEST_SECRET_KEY"),
                // legacy/common name (what you keep in config.local.properties)
                System.getenv("STRIPE_SECRET_KEY"),
                props.getProperty("STRIPE_SECRET_KEY"),
                // dotted fallbacks (if you ever switch to dotted keys)
                get("stripe.test.secret.key"),
                get("stripe.secret.key")
        );

        if (k != null && !k.isBlank() && !(k.startsWith("rk_test_") || k.startsWith("sk_test_"))) {
            System.err.println("⚠️  Non-test Stripe key detected. Expected rk_test_/sk_test_. Got: "
                    + k.substring(0, Math.min(10, k.length())) + "…");
        }
        return k;
    }

    /** Webhook signing secret (test). */
    public static String getStripeWebhookSecret() {
        return firstNonBlank(
                System.getenv("STRIPE_WEBHOOK_SECRET"),
                props.getProperty("STRIPE_WEBHOOK_SECRET"),
                get("stripe.webhook.secret")
        );
    }

    /** Publishable key. */
    public static String getStripePublishableKey() {
        return firstNonBlank(
                System.getenv("STRIPE_PUBLISHABLE_KEY"),
                props.getProperty("STRIPE_PUBLISHABLE_KEY"),
                get("stripe.publishable.key")
        );
    }

    /** Optional: success URL if you keep it in config. */
    public static String getStripeSuccessUrl() {
        return firstNonBlank(
                System.getenv("STRIPE_SUCCESS_URL"),
                props.getProperty("STRIPE_SUCCESS_URL"),
                get("stripe.success.url")
        );
    }

    /** tiny util */
    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    // ---- Misc ----
    public static int getDefaultTimeout() {
        return Integer.parseInt(get("timeout", "10"));
    }

    public static String joinUrl(String base, String path) {
        if (base.endsWith("/")) return base + (path.startsWith("/") ? path.substring(1) : path);
        return base + (path.startsWith("/") ? path : "/" + path);
    }
}
