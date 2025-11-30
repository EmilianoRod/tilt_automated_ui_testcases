package Utils;

import io.qameta.allure.Allure;              // ✅ NEW: for Allure attachments
import java.io.ByteArrayInputStream;         // ✅ NEW
import java.nio.charset.StandardCharsets;    // ✅ NEW
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.openqa.selenium.*;

public class DebugDumps {

    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS");

    public static void saveScreenshot(WebDriver driver, String name) {
        try {
            String ts = TS.format(new Date());
            String safeName = sanitize(name);
            Path out = Paths.get("target", "artifacts", ts + "_" + safeName + ".png");
            Files.createDirectories(out.getParent());

            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Files.write(out, png);
            System.out.println("📸 saved " + out.toAbsolutePath());

            // ✅ NEW: also attach to Allure (best-effort)
            try {
                Allure.addAttachment(
                        "Debug Screenshot - " + safeName,
                        "image/png",
                        new ByteArrayInputStream(png),
                        "png"
                );
            } catch (Throwable ignored) {
                // Reporting must never break tests
            }

        } catch (Exception e) {
            System.out.println("screenshot failed: " + e);
        }
    }

    public static void saveHtml(WebDriver driver, String name) {
        try {
            String ts = TS.format(new Date());
            String safeName = sanitize(name);
            Path out = Paths.get("target", "artifacts", ts + "_" + safeName + ".html");
            Files.createDirectories(out.getParent());

            String html = driver.getPageSource();
            Files.write(out, html.getBytes(StandardCharsets.UTF_8));
            System.out.println("🧾 saved " + out.toAbsolutePath());

            // ✅ NEW: also attach to Allure (best-effort)
            try {
                Allure.addAttachment(
                        "Debug HTML - " + safeName,
                        "text/html",
                        new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
                        "html"
                );
            } catch (Throwable ignored) {
                // Reporting must never break tests
            }

        } catch (Exception e) {
            System.out.println("html dump failed: " + e);
        }
    }

    private static String sanitize(String s) {
        return s == null ? "null" : s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
