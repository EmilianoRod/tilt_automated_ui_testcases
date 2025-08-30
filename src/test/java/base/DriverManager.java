package base;

import org.openqa.selenium.WebDriver;

public class DriverManager {

    private static final ThreadLocal<WebDriver> TL = new ThreadLocal<>();

    private DriverManager() {}

    public static void init() {
        if (TL.get() == null) {
            TL.set(DriverFactory.createDriver());
        }
    }

    public static WebDriver get() {
        WebDriver d = TL.get();
        if (d == null) {
            throw new IllegalStateException("WebDriver not initialized in this thread. Call DriverManager.init() first.");
        }
        return d;
    }

    public static void quit() {
        WebDriver d = TL.get();
        if (d != null) {
            try { d.quit(); } finally { TL.remove(); }
        }
    }
}
