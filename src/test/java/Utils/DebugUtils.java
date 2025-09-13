package Utils;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;

public class DebugUtils {


    public static void dumpOnFailure(WebDriver driver, String tag) {
        try {
            // screenshot
            var scr = ((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES);
            java.nio.file.Files.write(java.nio.file.Path.of("target", "screenshots", tag + ".png"), scr);

            // console logs (Chrome)
            var logs = driver.manage().logs().get(LogType.BROWSER);
            var sb = new StringBuilder();
            logs.forEach(e -> sb.append(e.getLevel()).append(" ").append(e.getMessage()).append("\n"));
            java.nio.file.Files.write(java.nio.file.Path.of("target", "console", tag + ".log"),
                    sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // minimal DOM dump
            String html = (String)((JavascriptExecutor)driver).executeScript("return document.documentElement.outerHTML;");
            java.nio.file.Files.write(java.nio.file.Path.of("target", "html", tag + ".html"),
                    html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }



    public static void dumpPerfLogs(WebDriver driver, String needle) {
        try {
            LogEntries entries = driver.manage().logs().get(LogType.PERFORMANCE);
            System.out.println("=== [PERF LOGS] looking for: " + needle + " ===");
            for (LogEntry e : entries) {
                String msg = e.getMessage();
                if (msg.contains("Network.responseReceived") || msg.contains("Network.requestWillBeSent")) {
                    if (needle == null || msg.contains(needle)) {
                        System.out.println(msg);
                    }
                }
            }
        } catch (Exception ignore) { }
    }

    public static void dumpBrowserLogs(WebDriver driver) {
        try {
            LogEntries entries = driver.manage().logs().get(LogType.BROWSER);
            System.out.println("=== [BROWSER LOGS] ===");
            for (LogEntry e : entries) {
                System.out.printf("[%s] %s%n", e.getLevel(), e.getMessage());
            }
        } catch (Exception ignore) { }
    }

}
