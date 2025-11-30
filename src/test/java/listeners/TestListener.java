package listeners;

import Utils.VideoRecorder;
import base.DriverManager;
import io.qameta.allure.Allure;
import io.qameta.allure.Attachment;
import org.openqa.selenium.*;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public class TestListener implements ITestListener {

    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyyMMdd_HHmmss");
    private static final String VIDEO_ATTR = "videoRecorder";

    // =========================================================
    // RETRY LABELING
    // =========================================================

    private void annotateRetryLabels(ITestResult result) {
        try {
            Object nextRunObj   = result.getAttribute(RetryAnalyzer.ATTR_NEXT_RUN_NUMBER);
            Object totalRunsObj = result.getAttribute(RetryAnalyzer.ATTR_TOTAL_RUNS);

            int attempt;
            int maxAttempts;
            boolean flaky = false;

            if (nextRunObj instanceof Integer && totalRunsObj instanceof Integer) {
                attempt     = (Integer) nextRunObj;
                maxAttempts = (Integer) totalRunsObj;
                flaky       = attempt > 1;
            } else {
                // legacy fallback
                Object rc = result.getAttribute("retryCount");
                Object mc = result.getAttribute("maxRetryCount");
                int retryCount = (rc instanceof Integer) ? (Integer) rc : 0;
                int maxRetry   = (mc instanceof Integer) ? (Integer) mc : 0;

                attempt     = retryCount + 1;
                maxAttempts = maxRetry + 1;
                flaky       = retryCount > 0;
            }

            if (maxAttempts <= 0) {
                attempt = 1;
                maxAttempts = 1;
            }

            Allure.label("attempt", attempt + "/" + maxAttempts);
            if (flaky) {
                Allure.label("flaky", "true");
            }

            Allure.addAttachment(
                    "Retry Info",
                    "text/plain",
                    "attempt=" + attempt + "/" + maxAttempts + " | flaky=" + flaky
            );

        } catch (Throwable ignored) {
            // never break tests on reporting
        }
    }

    // =========================================================
    // CORE HOOKS
    // =========================================================

    @Override
    public void onTestStart(ITestResult result) {
        annotateRetryLabels(result);

        // ---- Decide if we record this run or not (Solution A) ----
        boolean videoEnabled    = VideoRecorder.isEnabled();
        boolean retryOnlyMode   = Boolean.parseBoolean(System.getProperty("video.retryOnly", "true"));
        int invocation          = result.getMethod().getCurrentInvocationCount(); // 0 = first run, 1 = first retry, etc.
        String qName            = qualifiedName(result);

        System.out.printf(
                "[Video] onTestStart for %s | invocation=%d | videoEnabled=%s | retryOnly=%s%n",
                qName, invocation, videoEnabled, retryOnlyMode
        );

        if (!videoEnabled) {
            System.out.println("[Video] Video disabled via property → not starting recording.");
            return;
        }

        // In retry-only mode, we *only* record if this is a retry (invocation > 0).
        if (retryOnlyMode && invocation == 0) {
            System.out.println("[Video] First attempt (invocation=0) in retry-only mode → no recording for " + qName);
            return;
        }

        System.out.println("[Video] Starting video recording for " + qName + " (invocation=" + invocation + ")");

        VideoRecorder recorder = new VideoRecorder(qName);
        recorder.start();

        // store per-test instance (safe for parallel runs)
        result.setAttribute(VIDEO_ATTR, recorder);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        WebDriver driver = currentDriver();
        System.out.println("[FAIL] " + qualifiedName(result) +
                " (invocation=" + result.getMethod().getCurrentInvocationCount() + ")");

        annotateRetryLabels(result);

        // 🎥 Stop & attach video for ANY failure (failed or broken)
        try {
            Object attr = result.getAttribute(VIDEO_ATTR);
            if (attr instanceof VideoRecorder recorder) {
                Path videoPath = recorder.stop();
                if (videoPath != null && Files.exists(videoPath)) {
                    System.out.println("[Video] Attaching failure video: " + videoPath.toAbsolutePath());
                    try (InputStream in = Files.newInputStream(videoPath)) {
                        Allure.addAttachment("Video recording", "video/mp4", in, "mp4");
                    }
                } else {
                    System.out.println("[Video] No video file to attach on failure.");
                }
            } else {
                System.out.println("[Video] No recorder instance found on failure.");
            }
        } catch (Throwable t) {
            System.out.println("[Video] Failed to stop/attach recording: " + t.getMessage());
        }

        if (driver == null) {
            System.out.println("[FAIL] No WebDriver instance; skipping screenshot.");
            return;
        }

        // Local screenshot
        saveScreenshotToFile(driver, result);

        // Allure artifacts
        attachUrl(driver);
        attachScreenshot(driver);
        attachPageSource(driver);
        attachBrowserConsole(driver);
        attachPerformanceLogIfAvailable(driver);
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        // 🎥 Test passed → stop & delete video
        try {
            Object attr = result.getAttribute(VIDEO_ATTR);
            if (attr instanceof VideoRecorder recorder) {
                System.out.println("[Video] Test passed → stopping & deleting recording. " +
                        "(invocation=" + result.getMethod().getCurrentInvocationCount() + ")");
                Path videoPath = recorder.stop();
                if (videoPath != null && Files.exists(videoPath)) {
                    Files.deleteIfExists(videoPath);
                    System.out.println("[VideoRecorder] Deleted video (test passed).");
                }
            }
        } catch (Throwable t) {
            System.out.println("[Video] Failed to stop/delete recording: " + t.getMessage());
        }
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        Throwable t = result.getThrowable();
        String reason = (t != null && t.getMessage() != null)
                ? t.getMessage()
                : "(no skip message)";
        System.out.println("[SKIP] " + qualifiedName(result) +
                " (invocation=" + result.getMethod().getCurrentInvocationCount() + ") — " + reason);

        // 🎥 Skipped → stop & delete (no attachment)
        try {
            Object attr = result.getAttribute(VIDEO_ATTR);
            if (attr instanceof VideoRecorder recorder) {
                System.out.println("[Video] Test skipped → stopping & deleting recording.");
                Path videoPath = recorder.stop();
                if (videoPath != null && Files.exists(videoPath)) {
                    Files.deleteIfExists(videoPath);
                    System.out.println("[VideoRecorder] Deleted video (test skipped).");
                }
            }
        } catch (Throwable ex) {
            System.out.println("[Video] Failed to stop/delete recording on skip: " + ex.getMessage());
        }

        try {
            Allure.addAttachment("Skip Reason", "text/plain", reason);
        } catch (Throwable ignored) {}

        WebDriver driver = currentDriver();
        if (driver != null) attachUrl(driver);
    }

    // =========================================================
    // Allure attachments
    // =========================================================

    @Attachment(value = "Current URL", type = "text/plain")
    private byte[] attachUrl(WebDriver driver) {
        try {
            return driver.getCurrentUrl().getBytes(StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return "(no url)".getBytes(StandardCharsets.UTF_8);
        }
    }

    @Attachment(value = "Screenshot", type = "image/png")
    private byte[] attachScreenshot(WebDriver driver) {
        try {
            return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        } catch (Throwable ignored) {
            return new byte[0];
        }
    }

    @Attachment(value = "Page Source", type = "text/html")
    private byte[] attachPageSource(WebDriver driver) {
        try {
            return driver.getPageSource().getBytes(StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return "<no page source>".getBytes(StandardCharsets.UTF_8);
        }
    }

    @Attachment(value = "Browser Console", type = "text/plain")
    private byte[] attachBrowserConsole(WebDriver driver) {
        try {
            Set<String> available = driver.manage().logs().getAvailableLogTypes();
            if (!available.contains(LogType.BROWSER))
                return "browser log type not available".getBytes(StandardCharsets.UTF_8);

            String logs = driver.manage().logs().get(LogType.BROWSER).getAll()
                    .stream().map(this::fmt)
                    .collect(Collectors.joining(System.lineSeparator()));

            return logs.getBytes(StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return "Browser logs not available".getBytes(StandardCharsets.UTF_8);
        }
    }

    @Attachment(value = "Performance Log (CDP)", type = "application/json")
    private byte[] attachPerformanceLogIfAvailable(WebDriver driver) {
        try {
            if (!driver.manage().logs().getAvailableLogTypes().contains(LogType.PERFORMANCE))
                return "performance log not available".getBytes(StandardCharsets.UTF_8);

            String perf = driver.manage().logs().get(LogType.PERFORMANCE).getAll()
                    .stream().map(LogEntry::getMessage)
                    .collect(Collectors.joining("\n"));

            return perf.getBytes(StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return "performance log read failed".getBytes(StandardCharsets.UTF_8);
        }
    }

    private String fmt(LogEntry e) {
        return "[" + e.getLevel() + "] " + e.getMessage();
    }

    // =========================================================
    // UTIL
    // =========================================================

    private WebDriver currentDriver() {
        try {
            return DriverManager.peek();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String qualifiedName(ITestResult r) {
        return r.getMethod().getRealClass().getSimpleName() + "." + r.getMethod().getMethodName();
    }

    private void saveScreenshotToFile(WebDriver driver, ITestResult result) {
        try {
            Path dir = Path.of("target", "screenshots");
            Files.createDirectories(dir);

            String method = result.getMethod().getMethodName()
                    .replaceAll("[^a-zA-Z0-9_.-]", "_");

            String ts;
            synchronized (TS) {
                ts = TS.format(new Date());
            }

            Path out = dir.resolve(method + "_" + ts + ".png");
            Files.write(out, ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));

            System.out.println("[Screenshot] Saved " + out.toAbsolutePath());
        } catch (Throwable ignored) {
            System.out.println("[Screenshot] Failed to write: " + ignored.getMessage());
        }
    }

    // =========================================================
    // Allure env
    // =========================================================

    @Override
    public void onStart(ITestContext context) { writeEnv(); }

    private void writeEnv() {
        try {
            Properties p = new Properties();
            p.setProperty("env", System.getProperty("env", "dev"));
            p.setProperty("baseUrl", System.getProperty("baseUrl", ""));
            p.setProperty("retry", System.getProperty("retry", "0"));
            p.setProperty("headless", System.getProperty("headless", "false"));
            p.setProperty("java.version", System.getProperty("java.version", ""));
            p.setProperty("os.name", System.getProperty("os.name", ""));
            p.setProperty("parallel", System.getProperty("parallel", "methods"));

            Path results = Path.of("target", "allure-results");
            Files.createDirectories(results);

            try (OutputStream out = Files.newOutputStream(results.resolve("environment.properties"))) {
                p.store(out, "Generated by TestListener");
            }
        } catch (Throwable ignored) {}
    }

    // =========================================================
    // No-op overrides
    // =========================================================

    @Override public void onTestFailedButWithinSuccessPercentage(ITestResult r) {}
    @Override public void onFinish(ITestContext context) {}
}
