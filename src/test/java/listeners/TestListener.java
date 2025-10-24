package listeners;

import base.DriverManager;
import io.qameta.allure.Attachment;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public class TestListener implements ITestListener {

    // ---------- Core hooks ----------

    @Override
    public void onTestFailure(ITestResult result) {
        WebDriver driver = currentDriver();
        if (driver == null) return;

        attachUrl(driver);
        attachScreenshot(driver);
        attachPageSource(driver);
        attachBrowserConsole(driver);
        attachPerformanceLogIfAvailable(driver);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        // Bubble the SkipException message to CI logs for quick triage
        Throwable t = result.getThrowable();
        String reason = (t != null && t.getMessage() != null) ? t.getMessage() : "(no skip message)";
        System.out.println("[SKIP] " + result.getMethod().getQualifiedName() + " — " + reason);

        // Skips can still benefit from artifacts (e.g., guard trips); collect best-effort
        onTestFailure(result);
    }

    // ---------- Attachments ----------

    @Attachment(value = "Current URL", type = "text/plain")
    private byte[] attachUrl(WebDriver driver) {
        String url = "(no url available)";
        try { url = driver.getCurrentUrl(); } catch (Throwable ignored) {}
        return url.getBytes(StandardCharsets.UTF_8);
    }

    @Attachment(value = "Screenshot", type = "image/png")
    private byte[] attachScreenshot(WebDriver driver) {
        try { return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES); }
        catch (Throwable e) { return new byte[0]; }
    }

    @Attachment(value = "Page Source", type = "text/html")
    private byte[] attachPageSource(WebDriver driver) {
        try { return driver.getPageSource().getBytes(StandardCharsets.UTF_8); }
        catch (Throwable e) { return "<no page source>".getBytes(StandardCharsets.UTF_8); }
    }

    @Attachment(value = "Browser Console", type = "text/plain")
    private byte[] attachBrowserConsole(WebDriver driver) {
        try {
            String all = driver.manage().logs().get(LogType.BROWSER).getAll()
                    .stream()
                    .map(this::fmt)
                    .collect(Collectors.joining(System.lineSeparator()));
            return all.getBytes(StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return "Browser logs not available".getBytes(StandardCharsets.UTF_8);
        }
    }

    @Attachment(value = "Performance (CDP) JSONL", type = "application/json")
    private byte[] attachPerformanceLogIfAvailable(WebDriver driver) {
        try {
            Set<String> available = driver.manage().logs().getAvailableLogTypes();
            if (!available.contains(LogType.PERFORMANCE)) {
                return "performance log type not available".getBytes(StandardCharsets.UTF_8);
            }
            String perf = driver.manage().logs().get(LogType.PERFORMANCE).getAll()
                    .stream()
                    .map(LogEntry::getMessage)
                    .collect(Collectors.joining(System.lineSeparator()));
            return perf.getBytes(StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return "failed to read performance logs".getBytes(StandardCharsets.UTF_8);
        }
    }

    private String fmt(LogEntry e) {
        return String.format("[%s] %s", e.getLevel(), e.getMessage());
    }

    // ---------- Utilities ----------

    /** Get the thread's driver without throwing; returns null if not initialized or already quit. */
    private WebDriver currentDriver() {
        try { return DriverManager.peek(); } catch (Throwable ignored) { return null; }
    }


    // ---------- Allure env file ----------

    @Override
    public void onStart(ITestContext context) {
        writeAllureEnvironment();
    }

    private void writeAllureEnvironment() {
        try {
            Properties p = new Properties();
            p.setProperty("env", System.getProperty("env", "dev"));
            p.setProperty("baseUrl", System.getProperty("baseUrl", ""));
            p.setProperty("retry", System.getProperty("retry", "0"));
            p.setProperty("java.version", System.getProperty("java.version", ""));
            p.setProperty("os.name", System.getProperty("os.name", ""));
            p.setProperty("os.arch", System.getProperty("os.arch", ""));
            p.setProperty("headless", System.getProperty("headless", "")); // if you pass it
            p.setProperty("parallel", System.getProperty("parallel", "methods"));

            Path resultsDir = Path.of(System.getProperty("user.dir"), "target", "allure-results");
            Files.createDirectories(resultsDir);
            try (OutputStream out = Files.newOutputStream(resultsDir.resolve("environment.properties"))) {
                p.store(out, "Generated by TestListener");
            }
        } catch (Throwable ignored) {
            // optional file; ignore errors
        }
    }

    // ---------- Unused ITestListener methods (no-ops) ----------

    @Override public void onTestStart(ITestResult r) {}
    @Override public void onTestSuccess(ITestResult r) {}
    @Override public void onTestFailedButWithinSuccessPercentage(ITestResult r) {}
    @Override public void onFinish(ITestContext c) {}
}
