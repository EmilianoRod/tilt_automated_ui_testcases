package Utils;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.openqa.selenium.*;

public class DebugDumps {
    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS");

    public static void saveScreenshot(WebDriver driver, String name) {
        try {
            String ts = TS.format(new Date());
            Path out = Paths.get("target", "artifacts", ts + "_" + sanitize(name) + ".png");
            Files.createDirectories(out.getParent());
            byte[] png = ((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES);
            Files.write(out, png);
            System.out.println("📸 saved " + out.toAbsolutePath());
        } catch (Exception e) {
            System.out.println("screenshot failed: " + e);
        }
    }

    public static void saveHtml(WebDriver driver, String name) {
        try {
            String ts = TS.format(new Date());
            Path out = Paths.get("target", "artifacts", ts + "_" + sanitize(name) + ".html");
            Files.createDirectories(out.getParent());
            String html = driver.getPageSource();
            Files.write(out, html.getBytes());
            System.out.println("🧾 saved " + out.toAbsolutePath());
        } catch (Exception e) {
            System.out.println("html dump failed: " + e);
        }
    }

    private static String sanitize(String s){ return s.replaceAll("[^a-zA-Z0-9._-]", "_"); }
}
