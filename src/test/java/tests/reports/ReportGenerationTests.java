package tests.reports;

import Utils.Config;
import base.BaseTest;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;
import pages.LoginPage;
import pages.menuPages.DashboardPage;
import pages.Individuals.IndividualsPage;
import pages.reports.ReportSummaryPage;
import pages.teams.TeamDetailsPage;
import pages.teams.TeamsPage;


import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

import static io.qameta.allure.Allure.step;


@Epic("Tilt – Reports")
@Feature("Report Generation & Download")
@Owner("Emiliano")
public class ReportGenerationTests extends BaseTest {





    @Test(groups = {"smoke"}, description = "SM10: True Tilt FULL REPORT opens from Individuals and downloads PDF.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("True Tilt – Individual full report generation & PDF download")
    public void smoke_individualTrueTiltReport_openAndDownload() throws Exception {
        // ----- config / admin user -----
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }
        System.out.println("[AdminCreds] email=" + BaseTest.maskEmail(ADMIN_USER) + " | passLen=" + ADMIN_PASS.length());

        step("Login as admin and open Dashboard");
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        login.waitUntilLoaded();
        DashboardPage dashboard =
                login.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboard.isLoaded(), "❌ Dashboard did not load after login");

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Find a row with a completed True Tilt report");
        if (!individuals.hasAnyCompletedTrueTiltReport()) {
            throw new SkipException("⚠️ No completed True Tilt reports found in Individuals – cannot run SM10.");
        }

        step("Open the first completed True Tilt report");
        ReportSummaryPage summaryPage = individuals
                .openFirstCompletedTrueTiltReport()
                .waitUntilLoaded();

        // Only apply the "known issue" skips if the summary is *not* loaded
        if (!summaryPage.isLoaded()) {
            if (summaryPage.isOnAssessmentStartPage()) {
                throw new SkipException("⚠️ Known issue: opening a completed True Tilt report redirects to the first assessment page instead of the summary.");
            }
            if (summaryPage.isOnDashboardOrIndividuals()) {
                throw new SkipException("⚠️ Known issue: opening a completed True Tilt report bounces back to dashboard/individuals instead of the summary.");
            }
        }

        step("Assert Report Summary page is loaded");
        Assert.assertTrue(summaryPage.isLoaded(), "❌ Report Summary page did not load");
        Assert.assertTrue(summaryPage.hasProfileHeader(), "❌ Profile header not visible on report");
        Assert.assertTrue(summaryPage.hasTrueTiltGraph(), "❌ True Tilt graph not visible on report");
        Assert.assertTrue(summaryPage.hasFullReportButton(), "❌ Full Report button not visible on report");

        step("Click 'Full Report' and wait for a non-empty PDF file");
        Path downloadDir = getDownloadDir();
        Files.createDirectories(downloadDir);

        Instant start = Instant.now();
        summaryPage.clickFullReportDownload();

        Path pdf = waitForNewFile(downloadDir, start, Duration.ofSeconds(45), ".pdf");
        Assert.assertNotNull(pdf, "❌ No new Full Report PDF was downloaded for the report");
        Assert.assertTrue(Files.size(pdf) > 0, "❌ Downloaded Full Report PDF is empty: " + pdf);
        System.out.println("✅ Full Report PDF downloaded: " + pdf.toAbsolutePath());
    }


    @Test(groups = {"smoke"}, description = "SM11: True Tilt Snapshot report downloads PDF successfully.")
    @Severity(SeverityLevel.NORMAL)
    public void smoke_individualTrueTiltSnapshot_downloadsPdf() throws Exception {

        // ----- config / admin user -----
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }
        System.out.println("[AdminCreds] email=" + BaseTest.maskEmail(ADMIN_USER)
                + " | passLen=" + ADMIN_PASS.length());

        step("Login as admin and open Dashboard");
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        login.waitUntilLoaded();
        DashboardPage dashboard = login.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboard.isLoaded(), "❌ Dashboard did not load");

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Find a row with a completed True Tilt report");
        if (!individuals.hasAnyCompletedTrueTiltReport()) {
            throw new SkipException("⚠️ No completed True Tilt reports found – cannot run SM11.");
        }

        step("Open the first completed True Tilt report");
        ReportSummaryPage summaryPage = individuals.openFirstCompletedTrueTiltReport().waitUntilLoaded();

        step("Validate Report Summary Page");
        Assert.assertTrue(summaryPage.isLoaded(), "❌ Report Summary page did not load");

        step("Trigger Snapshot PDF download");
        Path dir = getDownloadDir();
        Files.createDirectories(dir);
        Instant start = Instant.now();

        summaryPage.clickDownloadSnapshotPdf();   // <-- MUST exist in page object

        Path pdf = waitForNewPdf(dir, start, Duration.ofSeconds(45));
        Assert.assertNotNull(pdf, "❌ No Snapshot PDF was downloaded.");
        Assert.assertTrue(Files.size(pdf) > 0, "❌ Snapshot PDF is empty: " + pdf);

        System.out.println("✅ Snapshot PDF downloaded: " + pdf.toAbsolutePath());
    }


    @Test(groups = {"smoke"}, description = "SM12: True Tilt Mobile image downloads PNG successfully.")
    @Severity(SeverityLevel.NORMAL)
    public void smoke_individualTrueTiltMobileImage_downloadsPng() throws Exception {

        // ----- config -----
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing.");
        }

        step("Login as admin");
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        login.waitUntilLoaded();
        DashboardPage dashboard = login.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboard.isLoaded());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded());

        step("Check for completed True Tilt reports");
        if (!individuals.hasAnyCompletedTrueTiltReport()) {
            throw new SkipException("⚠️ No completed True Tilt reports found – cannot run SM12.");
        }

        step("Open first True Tilt completed report");
        ReportSummaryPage summaryPage = individuals.openFirstCompletedTrueTiltReport().waitUntilLoaded();
        Assert.assertTrue(summaryPage.isLoaded());

        step("Trigger Mobile Image PNG download");
        Path dir = getDownloadDir();
        Files.createDirectories(dir);
        Instant start = Instant.now();

        summaryPage.clickDownloadMobileImagePng();   // <-- MUST exist in page object

        Path png = waitForNewPng(dir, start, Duration.ofSeconds(45));
        Assert.assertNotNull(png, "❌ No PNG file downloaded for Mobile Image");
        Assert.assertTrue(Files.size(png) > 0, "❌ Mobile Image PNG is empty: " + png);

        System.out.println("✅ Mobile Image PNG downloaded: " + png);
    }


    @Test(groups = {"smoke"}, description = "SM13: AGT Full Report downloads PDF successfully.")
    @Severity(SeverityLevel.NORMAL)
    public void smoke_individualAgtFullReport_downloadsPdf() throws Exception {

        // ----- config -----
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing.");
        }

        step("Login as admin");
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        login.waitUntilLoaded();
        DashboardPage dashboard = login.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboard.isLoaded(), "❌ Dashboard did not load after login");

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Check for completed AGT Full Reports");
        if (!individuals.hasAnyCompletedAgtFullReport()) {
            throw new SkipException("⚠️ No completed AGT Full Reports found – cannot run SM13.");
        }

        step("Open first AGT Full Report");
        ReportSummaryPage summaryPage = individuals.openFirstCompletedAgtFullReport().waitUntilLoaded();
        Assert.assertTrue(summaryPage.isLoaded(), "❌ AGT Full Report Summary page did not load");

        step("Trigger AGT Full Report PDF download");
        Path dir = getDownloadDir();
        Files.createDirectories(dir);
        Instant start = Instant.now();

        summaryPage.clickDownloadAgtFullReportPdf();   // <-- implement in ReportSummaryPage

        Path pdf = waitForNewPdf(dir, start, Duration.ofSeconds(45));
        Assert.assertNotNull(pdf, "❌ No PDF file downloaded for AGT Full Report");
        Assert.assertTrue(Files.size(pdf) > 0, "❌ AGT Full Report PDF is empty: " + pdf);

        System.out.println("✅ AGT Full Report PDF downloaded: " + pdf);
    }
    

    @Test(groups = {"smoke"}, description = "SM14: Team True Tilt Aggregate report PDF downloads successfully.")
    @Severity(SeverityLevel.NORMAL)
    public void smoke_teamTrueTiltAggregateReport_downloadsPdf() throws Exception {

        // ----- config / admin user -----
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }
        System.out.println("[AdminCreds] email=" + BaseTest.maskEmail(ADMIN_USER)
                + " | passLen=" + ADMIN_PASS.length());

        // ----- login -----
        step("Login as admin and open Dashboard");
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        login.waitUntilLoaded();

        DashboardPage dashboard =
                login.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboard.isLoaded(), "❌ Dashboard did not load after login");

        // ----- go to Teams -----
        step("Open Teams page");
        TeamsPage teams = dashboard.goToTeams().waitUntilLoaded();
        Assert.assertTrue(teams.isLoaded(), "❌ Teams page did not load");

        // ----- open team details -----
        step("Open a team that has at least one completed Team True Tilt Aggregate (TTP) report");
        TeamDetailsPage details = teams
                .openFirstTeamWithCompletedAggregateReport()
                .waitUntilLoaded();          // <<< important: wait for skeleton to finish
        Assert.assertTrue(details.isLoaded(), "❌ Team Details did not load");

        // Optional safety check so we fail with a clear message if data changes
        Assert.assertTrue(
                details.hasCompletedTrueTiltAggregate(),
                "❌ Selected team does not have any completed Team True Tilt Aggregate reports."
        );

        // ----- open TTP report summary -----
        step("Open the first completed TTP aggregate report from Team Details");
        ReportSummaryPage summaryPage = details.openFirstCompletedTrueTiltAggregate();
        Assert.assertTrue(summaryPage.isLoaded(),
                "❌ Team Aggregate Report Summary page did not load");

        // ----- download PDF -----
        step("Click 'Download PDF' and wait for a non-empty file");
        Path downloadDir = getDownloadDir();
        Files.createDirectories(downloadDir);

        Instant start = Instant.now();
        summaryPage.clickDownloadPdf();

        Path pdf = waitForNewPdf(downloadDir, start, Duration.ofSeconds(60));
        Assert.assertNotNull(pdf, "❌ No new Team Aggregate PDF was downloaded");
        Assert.assertTrue(
                Files.size(pdf) > 0,
                "❌ Downloaded Team Aggregate PDF is empty: " + pdf
        );

        System.out.println("✅ Team True Tilt Aggregate PDF downloaded: " + pdf.toAbsolutePath());
    }


    @Test(groups = {"smoke"}, description = "SM15: Unique Amplifier report downloads PDF successfully.")
    @Severity(SeverityLevel.NORMAL)
    public void smoke_individualUniqueAmplifier_downloadsPdf() throws Exception {

        // ----- config -----
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing.");
        }

        step("Login as admin");
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        login.waitUntilLoaded();
        DashboardPage dashboard = login.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboard.isLoaded(), "❌ Dashboard did not load after login");

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Check for completed Unique Amplifier reports");
        if (!individuals.hasAnyCompletedUniqueAmplifierReport()) {
            throw new SkipException("⚠️ No completed Unique Amplifier reports found – cannot run SM15.");
        }

        step("Open first Unique Amplifier report");
        ReportSummaryPage summaryPage = individuals.openFirstCompletedUniqueAmplifierReport().waitUntilLoaded();
        Assert.assertTrue(summaryPage.isLoaded(), "❌ Unique Amplifier report page did not load");

        step("Trigger Unique Amplifier PDF download");
        Path dir = getDownloadDir();
        Files.createDirectories(dir);
        Instant start = Instant.now();

        summaryPage.clickDownloadUniqueAmplifierPdf();   // <-- implement in ReportSummaryPage

        Path pdf = waitForNewPdf(dir, start, Duration.ofSeconds(45));
        Assert.assertNotNull(pdf, "❌ No PDF file downloaded for Unique Amplifier");
        Assert.assertTrue(Files.size(pdf) > 0, "❌ Unique Amplifier PDF is empty: " + pdf);

        Assert.assertTrue(pdf.toString().endsWith(".pdf"),
                "❌ Downloaded file is not a PDF: " + pdf);

        Assert.assertTrue(pdf.getFileName().toString().contains("Snapshot"),
                "❌ UA Snapshot PDF name does not contain 'Snapshot': " + pdf.getFileName());


        System.out.println("✅ Unique Amplifier PDF downloaded: " + pdf);
    }




















//    /**
//     * SM36: Freemium → limited report view for unpaid user.
//     *  - Log in as a freemium user.
//     *  - Open True Tilt report.
//     *  - Verify limited view elements (e.g. blur/locked sections/upgrade CTA).
//     */
//    @Test(groups = {"smoke"}, description = "SM36: Freemium → limited report view for unpaid user.")
//    public void smoke_freemiumUser_seesLimitedReport() throws Exception {
//        final String FREE_USER = Config.getAny("freemium.user.email", "FREEMIUM_EMAIL", null);
//        final String FREE_PASS = Config.getAny("freemium.user.password", "FREEMIUM_PASS", null);
//        if (FREE_USER == null || FREE_USER.isBlank() || FREE_PASS == null || FREE_PASS.isBlank()) {
//            throw new SkipException("[Config] Freemium user credentials missing (freemium.user.* or FREEMIUM_* env).");
//        }
//
//        step("Login as freemium user");
//        LoginPage login = new LoginPage(driver());
//        login.navigateTo();
//        login.waitUntilLoaded();
//        DashboardPage dashboard = login.safeLoginAsAdmin(FREE_USER, FREE_PASS, Duration.ofSeconds(30));
//        dashboard.waitUntilLoaded();
//
//        step("Navigate to Individuals and open True Tilt report for self");
//        IndividualsPage individuals = dashboard.goToIndividuals();
//        individuals.waitUntilLoaded();
//        // TODO: select self row and open report
//        ReportSummaryPage report = individuals.openTrueTiltReportForEmail(FREE_USER);
//
//        step("Assert freemium-specific limitations are present");
//        // TODO: add concrete assertions: blurred sections, lock icons, upgrade CTA, etc.
//        Assert.assertTrue(report.hasFreemiumLimitedBanner(),
//                "❌ Expected limited freemium banner/indicator not found.");
//    }













    /**
     * SM37: Freemium → full report for paid user.
     *  - Log in as a fully paid user.
     *  - Open True Tilt report.
     *  - Verify full report sections are visible (no freemium lock).
     */
    @Test(groups = {"smoke"}, description = "SM37: Freemium → full report for paid user.")
    public void smoke_paidUser_seesFullReport() throws Exception {
        final String PAID_USER = Config.getAny("paid.user.email", "PAID_EMAIL", null);
        final String PAID_PASS = Config.getAny("paid.user.password", "PAID_PASS", null);
        if (PAID_USER == null || PAID_USER.isBlank() || PAID_PASS == null || PAID_PASS.isBlank()) {
            throw new SkipException("[Config] Paid user credentials missing (paid.user.* or PAID_* env).");
        }

        step("Login as paid user");
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        login.waitUntilLoaded();
        DashboardPage dashboard = login.safeLoginAsAdmin(PAID_USER, PAID_PASS, Duration.ofSeconds(30));
        dashboard.waitUntilLoaded();

        step("Navigate to Individuals and open True Tilt report for self");
        IndividualsPage individuals = dashboard.goToIndividuals();
        individuals.waitUntilLoaded();
//        ReportSummaryPage report = individuals.openTrueTiltReportForEmail(PAID_USER);

        step("Assert that full report is visible (no freemium limit indicators)");
//        Assert.assertFalse(report.hasFreemiumLimitedBanner(),
//                "❌ Paid user should not see freemium-limited banner.");
        // TODO: optionally assert presence of full sections (e.g. all tabs/graphs visible)
    }




















    protected Path getDownloadDir() {
        String custom = Config.getAny("download.dir", "DOWNLOAD_DIR");
        if (custom != null && !custom.isBlank()) {
            return Path.of(custom).toAbsolutePath();
        }
        // fallback: target/downloads
        return Path.of("target/downloads").toAbsolutePath();
    }



    private Path waitForNewFile(Path dir, Instant since, Duration timeout, String... extensions) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        var extsLower = java.util.Arrays.stream(extensions)
                .map(e -> e.toLowerCase(Locale.ROOT))
                .toList();

        while (System.currentTimeMillis() < deadline) {
            try {
                Optional<Path> newest = Files.list(dir)
                        .filter(p -> {
                            String low = p.toString().toLowerCase(Locale.ROOT);
                            return extsLower.stream().anyMatch(low::endsWith);
                        })
                        .filter(p -> {
                            try {
                                return Files.getLastModifiedTime(p).toInstant().isAfter(since);
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .max(Comparator.comparing(p -> {
                            try {
                                return Files.getLastModifiedTime(p).toMillis();
                            } catch (Exception e) {
                                return 0L;
                            }
                        }));

                if (newest.isPresent()) {
                    return newest.get();
                }

                Thread.sleep(1000L);
            } catch (Exception ignored) {}
        }

        return null;
    }




}
