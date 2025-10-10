package pages.reports;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class ReportSummaryPage {




    private final WebDriver driver;

    // Robust markers seen in your screenshots
    private final By generatingSpinner = By.xpath("//*[contains(.,'We are generating your report')]");
    private final By fullReportTab     = By.xpath("//*[normalize-space()='Full Report']");
    private final By whatIsTilt        = By.xpath("//*[normalize-space()='What is Tilt 365']");

    public ReportSummaryPage(WebDriver driver) {
        this.driver = driver;
    }

    public ReportSummaryPage waitUntilLoaded() {
        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(20));

        // Wait for DOM ready
        w.until(d -> {
            try {
                String rs = String.valueOf(((JavascriptExecutor) d).executeScript("return document.readyState"));
                return "interactive".equals(rs) || "complete".equals(rs);
            } catch (Exception ignore) { return false; }
        });

        // If a spinner is shown, give it a little time to disappear
        try {
            w.withTimeout(Duration.ofSeconds(2))
                    .until(ExpectedConditions.presenceOfElementLocated(generatingSpinner));
            // spinner appeared -> wait for it to go away
            new WebDriverWait(driver, Duration.ofSeconds(20))
                    .until(ExpectedConditions.invisibilityOfElementLocated(generatingSpinner));
        } catch (TimeoutException ignored) {
            // spinner never showed, that's fine
        }

        // Accept any of these as a "loaded" signal
        new WebDriverWait(driver, Duration.ofSeconds(20)).until(d ->
                d.getCurrentUrl().toLowerCase().contains("/summary")
                        || !d.findElements(fullReportTab).isEmpty()
                        || !d.findElements(whatIsTilt).isEmpty()
        );
        return this;
    }

    public boolean isLoaded() {
        try {
            if (driver.getCurrentUrl() != null && driver.getCurrentUrl().toLowerCase().contains("/summary")) return true;
        } catch (Exception ignored) {}
        return !driver.findElements(fullReportTab).isEmpty()
                || !driver.findElements(whatIsTilt).isEmpty();
    }





}
