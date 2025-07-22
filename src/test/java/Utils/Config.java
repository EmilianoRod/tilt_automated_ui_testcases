package Utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {

    private static final Properties props = new Properties();


    public static int getDefaultTimeout() {
        String timeout = System.getProperty("timeout", "10"); // fallback to 10 if not set
        return Integer.parseInt(timeout);
    }


    static {
        try (InputStream input = Config.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                props.load(input);
            } else {
                System.err.println("❌ config.properties not found in classpath.");
            }
        } catch (IOException e) {
            throw new RuntimeException("❌ Failed to load config.properties: " + e.getMessage());
        }
    }

    public static String get(String key) {
        return System.getProperty(key, props.getProperty(key));
    }

    public static int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public static boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    public static String getBaseUrl() {
        return get("baseUrl");
    }

    public static int getTimeout() {
        return getInt("timeout");
    }

    public static boolean isHeadless() {
        return getBoolean("headless");
    }

    public static String getBrowser() {
        return get("browser");
    }

    public static String getScreenshotPath() {
        return get("screenshotPath");
    }



}
