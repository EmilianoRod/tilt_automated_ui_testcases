package pages.reports;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.SkipException;
import pages.BasePage;

import java.time.Duration;
import java.util.Locale;

public class ReportSummaryPage extends BasePage {


    // Robust markers seen in your screenshots
    private final By generatingSpinner = By.xpath("//*[contains(.,'We are generating your report')]");
    private final By fullReportTab = By.xpath("//*[normalize-space()='Full Report']");
    private final By whatIsTilt = By.xpath("//*[normalize-space()='What is Tilt 365']");


    // generic but robust markers for “profile header” and “graph”
    private final By profileHeader = By.xpath(
            "//*[self::h1 or self::h2 or self::h3]" +
                    "[contains(normalize-space(),'True Tilt') " +
                    " or contains(normalize-space(),'Profile Summary') " +
                    " or contains(normalize-space(),'Your Profile')]"
    );

    private final By kiteGraph = By.xpath(
            "//*[contains(@class,'kite') and .//svg] " +
                    " | //*[contains(@data-testid,'kite-graph')] " +
                    " | //canvas[contains(@data-testid,'kite-graph')]"
    );

    // 🔹 Buttons at the top-right of the report
    private final By ttpsnapshotBtn = By.xpath("//button[contains(normalize-space(),'Snapshot')]");
    private final By ttpfullReportBtn = By.xpath("//button[contains(normalize-space(),'Full Report')]");
    private final By ttpmobileImgBtn = By.xpath("//button[contains(normalize-space(),'Mobile Image')]");


    // --------------- AGT REPORT -----------------

    private static final By AGT_FULL_REPORT_PDF_BUTTON = By.xpath(
            "//button[normalize-space()=\"Download PDF\"]"
    );


    // Example locators – adjust text if wording changes slightly
    private static final By DOWNLOAD_PDF_BUTTON = By.xpath(
            "//button[contains(normalize-space(),'Download PDF')]"
    );

    // AGT-specific: top intro block and “Your Current Tilt Style” header
    private static final By AGT_RESULTS_INTRO = By.xpath(
            "//*[contains(normalize-space(),'Your results are ready') or " +
                    "  contains(normalize-space(),'before you begin')]"
    );

    private static final By AGT_CURRENT_TILT_STYLE_HEADER = By.xpath(
            "//*[contains(normalize-space(),'Your Current Tilt Style')]"
    );


    // --- Unique Amplifier tab locators ---
    private final By uniqueAmplifierTab = By.xpath(
            "//p[normalize-space()=\"Unique Amplifier\"]"
    );

    private final By uniqueAmplifierHeader = By.xpath(
            "//h2[normalize-space()=\"Your Unique Amplifier Strengths\"]"
    );


    public ReportSummaryPage(WebDriver driver) {
        super(driver);
    }


    @Override
    public ReportSummaryPage waitUntilLoaded() {
        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(20));

        // Wait for DOM ready
        w.until(d -> {
            try {
                String rs = String.valueOf(((JavascriptExecutor) d).executeScript("return document.readyState"));
                return "interactive".equals(rs) || "complete".equals(rs);
            } catch (Exception ignore) {
                return false;
            }
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

        // ✅ add this:
        logCurrentContext("waitUntilLoaded");

        return this;
    }


    public boolean isLoaded() {
        try {
            if (driver.getCurrentUrl() != null && driver.getCurrentUrl().toLowerCase().contains("/summary"))
                return true;
        } catch (Exception ignored) {
        }
        return !driver.findElements(fullReportTab).isEmpty()
                || !driver.findElements(whatIsTilt).isEmpty();
    }



    /**
     * Wait for an AGT Full Report summary page.
     * Looser criteria: Download PDF + either results intro or Current Tilt Style.
     */
    public ReportSummaryPage waitUntilAgtLoaded() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));

        wait.until(d -> {
            boolean hasDownload =
                    !d.findElements(DOWNLOAD_PDF_BUTTON).isEmpty();
            boolean hasIntro =
                    !d.findElements(AGT_RESULTS_INTRO).isEmpty()
                            || !d.findElements(AGT_CURRENT_TILT_STYLE_HEADER).isEmpty();
            return hasDownload && hasIntro;
        });

        logCurrentContext("waitUntilAgtLoaded");
        return this;
    }


    public boolean hasProfileHeader() {
        try {
            if (!driver.findElements(profileHeader).isEmpty()) return true;
        } catch (Exception ignored) {
        }

        // Fallbacks – these are *already* signs that the report view is there
        if (!driver.findElements(fullReportTab).isEmpty()) return true;
        if (!driver.findElements(whatIsTilt).isEmpty()) return true;

        return false;
    }


    public boolean hasTrueTiltGraph() {
        try {
            if (!driver.findElements(kiteGraph).isEmpty()) return true;
        } catch (Exception ignored) {
        }

        // Very tolerant fallback: any SVG inside main/section that looks like a chart
        try {
            if (!driver.findElements(By.cssSelector("main svg, section svg, [data-testid*='graph'] svg")).isEmpty()) {
                return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }


    /**
     * Clicks a button that triggers a PDF download.
     * - For AGT (/assess/agt/...) uses the "Download PDF" button.
     * - For TTP (/assess/ttp/...) prefers "Full Report", then "Snapshot".
     * - Falls back to any legacy "Download PDF" button if labels change.
     */
    public void clickDownloadPdf() {
        String url = driver.getCurrentUrl();
        System.out.println("[ReportSummaryPage] clickDownloadPdf url=" + url);

        By[] candidates;

        if (url.contains("/assess/agt/")) {
            // AGT summary page – there is a single "Download PDF" button
            candidates = new By[]{AGT_FULL_REPORT_PDF_BUTTON};
        } else if (url.contains("/assess/ttp/")) {
            // TTP (incl. team True Tilt aggregate) – use Full Report PDF
            candidates = new By[]{ttpfullReportBtn, ttpsnapshotBtn, AGT_FULL_REPORT_PDF_BUTTON};
        } else {
            // Fallback: try everything, just in case
            candidates = new By[]{AGT_FULL_REPORT_PDF_BUTTON, ttpfullReportBtn, ttpsnapshotBtn};
        }

        WebElement btn = null;
        for (By by : candidates) {
            try {
                btn = wait.until(ExpectedConditions.elementToBeClickable(by));
                if (btn != null) {
                    System.out.println("[ReportSummaryPage] Using download button locator: " + by);
                    break;
                }
            } catch (TimeoutException ignored) {
                // try next candidate
            }
        }

        if (btn == null) {
            throw new TimeoutException(
                    "Could not find any PDF download button for url=" + url +
                            " (tried AGT Download PDF / TTP Full Report / Snapshot)"
            );
        }

        try {
            ((JavascriptExecutor) driver)
                    .executeScript("arguments[0].scrollIntoView({block:'center'});", btn);
        } catch (Exception ignored) {
        }

        try {
            btn.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
        }

    }


    // --- Presence helpers ---

    public boolean hasSnapshotButton() {
        return !driver.findElements(ttpsnapshotBtn).isEmpty();

    }

    public boolean hasFullReportButton() {
        return !driver.findElements(ttpfullReportBtn).isEmpty();
    }

    public boolean hasMobileImageButton() {
        return !driver.findElements(ttpmobileImgBtn).isEmpty();
    }

    // --- Click actions used by smokes ---

    public void clickFullReportDownload() {
        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(20));
        WebElement btn = w.until(ExpectedConditions.elementToBeClickable(ttpfullReportBtn));
        scrollAndSafeClick(btn);
    }

    public void clickSnapshotDownload() {
        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(20));
        WebElement btn = w.until(ExpectedConditions.elementToBeClickable(ttpsnapshotBtn));
        scrollAndSafeClick(btn);
    }

    public void clickMobileImageDownload() {
        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(20));
        WebElement btn = w.until(ExpectedConditions.elementToBeClickable(ttpmobileImgBtn));
        scrollAndSafeClick(btn);
    }

    private void scrollAndSafeClick(WebElement el) {
        ((JavascriptExecutor) driver)
                .executeScript("arguments[0].scrollIntoView({block:'center'});", el);
        try {
            el.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
        }
    }


    public void clickDownloadSnapshotPdf() {
        safeClick(By.xpath("//button[normalize-space()=\"Snapshot\"]"));
    }

    public void clickDownloadMobileImagePng() {
        safeClick(By.xpath("//button[normalize-space()=\"Mobile Image\"]"));
    }

    public ReportSummaryPage clickDownloadAgtFullReportPdf() {
        safeClick(AGT_FULL_REPORT_PDF_BUTTON);
        return this;
    }


    /**
     * True if the Unique Amplifier tab is present on this summary page.
     */
    public boolean hasUniqueAmplifierTab() {
        return !driver.findElements(uniqueAmplifierTab).isEmpty();
    }

    /**
     * Clicks the Unique Amplifier tab and waits for its content to be visible.
     */
    public ReportSummaryPage switchToUniqueAmplifierTab() {
        if (!hasUniqueAmplifierTab()) {
            throw new SkipException("No Unique Amplifier tab available on this summary page.");
        }

        WebElement tab = wait.until(ExpectedConditions.elementToBeClickable(uniqueAmplifierTab));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", tab);
        try {
            tab.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", tab);
        }

        // Wait for UA-specific content
        wait.until(ExpectedConditions.visibilityOfElementLocated(uniqueAmplifierHeader));
        return this;
    }


    /**
     * Ensure we’re on the Unique Amplifier tab.
     */
    public void openUniqueAmplifierTabIfNeeded() {
        try {
            WebElement tab = wait.until(ExpectedConditions.visibilityOfElementLocated(uniqueAmplifierTab));
            ((JavascriptExecutor) driver)
                    .executeScript("arguments[0].scrollIntoView({block:'center'});", tab);

            if (!tab.getAttribute("class").toLowerCase(Locale.ROOT).contains("active")) {
                try {
                    tab.click();
                } catch (Exception e) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", tab);
                }
            }

            // Wait for the UA section heading
            wait.until(ExpectedConditions.visibilityOfElementLocated(uniqueAmplifierHeader));
            System.out.println("[ReportSummaryPage] Unique Amplifier tab is active");
        } catch (TimeoutException e) {
            throw new SkipException("⚠️ Unique Amplifier tab not available on this report.");
        }
    }


    /**
     * For a TTP summary that has a Unique Amplifier tab:
     *  - switch to the UA tab
     *  - then trigger the PDF download (uses same top-right buttons as Full Report).
     */
    /**
     * Clicks the Unique Amplifier PDF download (Snapshot while UA tab is active).
     */
    public void clickDownloadUniqueAmplifierPdf() {
        System.out.println("[ReportSummaryPage] clickDownloadUniqueAmplifierPdf url=" + driver.getCurrentUrl());

        // Base TTP summary must be loaded
        waitUntilLoaded();

        // Switch main content to UA tab
        openUniqueAmplifierTabIfNeeded();

        System.out.println("[ReportSummaryPage] Using UA download button locator: " + ttpsnapshotBtn);

        WebElement btn = wait.until(ExpectedConditions.elementToBeClickable(ttpsnapshotBtn));
        ((JavascriptExecutor) driver)
                .executeScript("arguments[0].scrollIntoView({block:'center'});", btn);

        try {
            btn.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
        }

    }






    private void logCurrentContext(String from) {
        try {
            String url   = driver.getCurrentUrl();
            String title = driver.getTitle();
            String h1    = findTopHeadingSafe();
            System.out.printf("[ReportSummaryPage#%s] URL=%s | title=%s | h1=%s%n",
                    from, url, title, h1);
        } catch (Exception e) {
            System.out.println("[ReportSummaryPage#logCurrentContext] Error reading context: " + e);
        }
    }

    private String findTopHeadingSafe() {
        try {
            WebElement h1 = driver.findElement(By.cssSelector("h1, [data-testid='report-title']"));
            return h1.getText();
        } catch (Exception e) {
            return "<no heading>";
        }
    }

    /**
     * Detects if we were redirected to an assessment "start / first page"
     * instead of staying on the summary.
     */
    public boolean isOnAssessmentStartPage() {
        String url = driver.getCurrentUrl().toLowerCase(Locale.ROOT);
        String bodyText;
        try {
            bodyText = driver.findElement(By.tagName("body")).getText().toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            bodyText = "";
        }

        // If URL already contains "summary", this is NOT the start page
        if (url.contains("/summary")) {
            System.out.println("[ReportSummaryPage] isOnAssessmentStartPage? false (url has /summary)");
            return false;
        }

        // Start page URLs look like .../assess/agt/12345 or .../assess/ttp/12345 (no /summary)
        boolean looksLikeStartUrl =
                url.matches(".*/assess/(agt|ttp)/\\d+(/?$|\\?.*)");

        boolean looksLikeStartCopy =
                bodyText.contains("start your assessment")
                        || bodyText.contains("begin assessment")
                        || bodyText.contains("question 1 of")
                        || bodyText.contains("before you begin");

        boolean result = looksLikeStartUrl && looksLikeStartCopy;
        System.out.printf("[ReportSummaryPage] isOnAssessmentStartPage? %s (url=%s)%n",
                result, url);
        return result;
    }


    /**
     * Detects if we bounced back to dashboard / individuals.
     */
    public boolean isOnDashboardOrIndividuals() {
        String url = driver.getCurrentUrl().toLowerCase();
        return url.contains("/dashboard") || url.contains("/individuals");
    }

    // ---------- AGT HELPERS ----------



}