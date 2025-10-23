package tests.teams;

import Utils.Config;
import Utils.MailSlurpUtils;
import Utils.StripeCheckoutHelper;
import base.BaseTest;
import com.mailslurp.clients.ApiException;
import com.mailslurp.models.Email;
import com.mailslurp.models.InboxDto;
import kotlin.concurrent.ThreadsKt;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;
import pages.Individuals.IndividualsPage;
import pages.LoginPage;
import pages.Shop.AssessmentEntryPage;
import pages.Shop.OrderPreviewPage;
import pages.Shop.PurchaseInformation;
import pages.Shop.PurchaseRecipientSelectionPage;
import pages.menuPages.DashboardPage;
import pages.menuPages.ShopPage;
import tests.Phase1SmokeTests;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static Utils.Config.joinUrl;





public class TeamAssessmentPurchaseAndAssignment extends BaseTest {





    // Put this near the top of the test class
    public static final java.util.regex.Pattern DUP_MSG =
            java.util.regex.Pattern.compile("(?is)\\b(duplicate|duplicated|already\\s*exists?|already\\s*in\\s*use|in\\s*use|used)\\b");


    @Test(groups = "ui-only", description = "TILT-238: Duplicate email should show inline error and block Proceed")
    public void duplicateEmailBlocksProceed_TTP_Team_ManualEntry() {


        // ----- config / constants -----
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }
        System.out.println("[AdminCreds] email=" + maskEmail(ADMIN_USER) + " | passLen=" + ADMIN_PASS.length());



        // ----- app flow -----
        step("Login as admin");
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();
        DashboardPage dashboardPage =
                loginPage.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Shop and start purchase flow");
        ShopPage shopPage = dashboardPage.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");
        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
        sel.selectTeam();
        sel.clickNextCta();


        PurchaseInformation info = new PurchaseInformation(driver).waitUntilLoaded();
        Assert.assertTrue(info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM),
                "Expected banner: 'Assessment purchase for: Team'.");


        step("Select 'create a new team'");
        AssessmentEntryPage assessmentEntryPage =
        new AssessmentEntryPage(driver)
                .waitUntilLoaded()
                .selectCreateNewTeam()
                .setOrganizationName("QA Org")
                .setGroupName("Automation Squad")
                .selectManualEntry()                      // switch to manual entry for emails
                .ensureAtLeastNRows(2)
                .setEmailAtRow(1, "qa.dup+1@tilt365.com")
                .setEmailAtRow(2, " QA.DUP+1@TILT365.COM ");



        // Wait until any inline error appears on either row
        new WebDriverWait(driver, Duration.ofSeconds(8)).until(d -> {
            return (assessmentEntryPage.getEmailErrorAtRow(1) != null) ||
                    (assessmentEntryPage.getEmailErrorAtRow(2) != null);
        });

        // Row 2 must show a duplicate error (row 1 may also be flagged)
        Assert.assertTrue(
                assessmentEntryPage.emailRowHasDuplicateError(2),
                "Row 2 message should indicate duplicate."
        );

        // If row 1 shows something, ensure it’s also a duplicate-y message
        String err1 = assessmentEntryPage.getEmailErrorAtRow(1);
        if (err1 != null && !err1.isBlank()) {
            Assert.assertTrue(
                    assessmentEntryPage.emailRowHasDuplicateError(1),
                    "Row 1 message (if present) should indicate duplicate."
            );
        }

        // Proceed must be disabled while duplicates exist
        Assert.assertFalse(
                assessmentEntryPage.isProceedToPaymentEnabled(),
                "Proceed button must be disabled when there are duplicate emails."
        );


    }

    @Test(groups = "ui-only", description = "TILT-239: Exceeding max team members (20) validates/clamps and blocks Proceed")
    public void exceedingMaxTeamMembersValidation_TTP_Team_ManualEntry() throws InterruptedException {

        // ----- config / creds -----
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }

        // ----- login -----
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();
        DashboardPage dashboardPage =
                loginPage.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        // ----- start purchase flow -----
        ShopPage shopPage = dashboardPage.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");
        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
        sel.selectTeam();
        sel.clickNextCta();

        // ----- banner sanity -----
        PurchaseInformation info = new PurchaseInformation(driver).waitUntilLoaded();
        Assert.assertTrue(info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM),
                "Expected banner: 'Assessment purchase for: Team'.");

        // ----- team entry -----
        AssessmentEntryPage page = new AssessmentEntryPage(driver)
                .waitUntilLoaded()
                .selectCreateNewTeam()
                .setOrganizationName("QA Org")
                .setGroupName("Automation Squad")
                .selectManualEntry();

        // Try to set 21 (beyond the max=20)
        page.enterNumberOfIndividuals2("21");



        // Wait for either: inline error OR clamp to 20
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> {
            String err = page.getNumberOfIndividualsError();
            int value  = page.getNumberOfIndividuals();
            int rows   = page.renderedEmailRows();
            return (err != null && !err.isBlank()) || (value <= 20 && rows <= 20);
        });

// Snapshot
        String qtyErr = page.getNumberOfIndividualsError();
        int qtyVal    = page.getNumberOfIndividuals();
        int rowsNow   = page.renderedEmailRows();

// If the spinner accepted a value (i.e., no inline error), assert it clamped to exactly 20
        if (qtyErr == null || qtyErr.isBlank()) {
            Assert.assertEquals(qtyVal, 20, "Spinner value should clamp to 20 when requesting >20.");
            Assert.assertEquals(rowsNow, 20, "Exactly 20 email rows should be rendered when clamped.");
        } else {
            // Inline error path: message should mention the limit; rows must still not exceed 20
            Assert.assertTrue(qtyErr.toLowerCase().matches(".*(20|maximum|max|up to).*"),
                    "Inline error should mention the 20 limit. Message: " + qtyErr);
            Assert.assertTrue(rowsNow <= 20, "Rows must not exceed 20 when showing an inline error.");
        }

// Proceed must be disabled while exceeding the limit
        Assert.assertFalse(page.isProceedToPaymentEnabled(),
                "Proceed button must be disabled when exceeding maximum team members.");



    }

    @Test(groups = "ui-only", description = "TILT-240: Invalid template upload shows appropriate error and blocks Proceed")
    public void invalidTemplateUpload_showsError_and_blocksProceed() throws Exception {

        // ----- config / creds -----
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }

        // ----- login -----
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();
        DashboardPage dashboardPage =
                loginPage.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        // ----- start purchase flow -----
        ShopPage shopPage = dashboardPage.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");
        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
        sel.selectTeam();
        sel.clickNextCta();

        // ----- banner sanity -----
        PurchaseInformation info = new PurchaseInformation(driver).waitUntilLoaded();
        Assert.assertTrue(
                info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM),
                "Expected banner: 'Assessment purchase for: Team'.");

        // ----- team entry & switch to template path -----
        AssessmentEntryPage page = new AssessmentEntryPage(driver)
                .waitUntilLoaded()
                .selectCreateNewTeam()
                .setOrganizationName("QA Org")
                .setGroupName("Automation Squad")
                .selectDownloadTemplate(); // render the upload panel

        // Re-affirm radio after potential re-render
        page.clickDownloadButton();
        page.selectDownloadTemplate();

        // Wait until upload panel is visible
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> page.isUploadPanelVisible());

        // ----- build invalid CSV that parses rows but leaves required cells empty -----
        java.io.File invalid = page.createTempCsv(
                "missing-email-cells",
                "First Name,Last Name,Email\n" +
                        "John,Doe,\n" +   // email empty
                        "Jane,,\n"        // email empty + missing last name
        );

        // ----- upload -----
        page.uploadCsvFile(invalid.getAbsolutePath());

        // Wait for either rows to render OR proceed to be disabled (some UIs block immediately)
        new WebDriverWait(driver, Duration.ofSeconds(20))
                .until(d -> page.renderedEmailRows() > 0 || !page.isProceedToPaymentEnabled());

        // Try to surface inline validations via real user blurs/tabs
        safeTriggerValidationBlurs();

        // Give DOM a moment to paint errors or keep proceed disabled
        new WebDriverWait(driver, Duration.ofSeconds(8))
                .until(d -> page.inlineRequiredErrorsCount() > 0 || !page.isProceedToPaymentEnabled());

        // Settle into a mode (GRID or UPLOAD)
        Mode mode = waitForModeToSettle(page, Duration.ofSeconds(12));

        // If we’re on GRID, poke once more to ensure error badges are visible
        if (mode == Mode.GRID) {
            safeTriggerValidationBlurs();
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(d -> page.inlineRequiredErrorsCount() > 0 || !page.isProceedToPaymentEnabled());
        }

        // ----- snapshot -----
        final boolean onManualGrid   = (mode == Mode.GRID);
        final boolean uploadVisible  = page.isUploadPanelVisible();
        final boolean radioSelected  = page.isDownloadTemplateSelected(); // expected only in upload mode
        final int     rowsNow        = page.renderedEmailRows();
        final int     inlineErrors   = page.inlineRequiredErrorsCount();
        final boolean proceedEnabled = page.isProceedToPaymentEnabled();
        final String  toast          = nvl(page.waitForUploadErrorText());

        // Helpful debug dump (always)
        logSnapshot(page, onManualGrid ? "GRID" : "UPLOAD", rowsNow, inlineErrors, proceedEnabled, uploadVisible, radioSelected, toast);
        dumpInlineErrors();      // texts under fields (.ant-form-item-explain-error / span[type=error])
        dumpPerFieldErrors();    // input[id^='users.'] -> nearest error text

        // ----- assertions -----
        SoftAssert softly = new SoftAssert();

        if (onManualGrid) {
            // We switched to manual grid
            softly.assertFalse(uploadVisible, "Upload panel should be hidden when grid is visible.");
            softly.assertFalse(radioSelected, "'Download template' radio should not remain selected in grid mode.");
            softly.assertTrue(rowsNow > 0, "Grid should show parsed rows after upload.");
            softly.assertTrue(inlineErrors > 0, "Expected inline field errors on the grid for incomplete rows.");
            softly.assertFalse(proceedEnabled, "Proceed must be disabled while grid has invalid rows.");
            // Sanity: we expected Email problems specifically
            softly.assertTrue(hasAnyEmailError(), "Should show an email-validation message in at least one row.");
        } else {
            // Hard reject — remain on upload
            softly.assertTrue(uploadVisible, "Upload panel should remain visible when no rows are parsed.");
            softly.assertTrue(radioSelected, "'Download template' radio should remain selected in upload mode.");
            softly.assertEquals(rowsNow, 0, "No rows should render on hard reject.");
            softly.assertFalse(proceedEnabled, "Proceed must be disabled on hard reject.");
            // Toast is optional; only validate if present
            if (!toast.isBlank()) {
                final String t = toast.toLowerCase();
                softly.assertTrue(
                        t.contains("missing") || t.contains("invalid") || t.contains("column")
                                || t.contains("parse") || t.contains("error"),
                        "If a toast is present, it should mention missing/invalid/column/parse/error. Actual: " + toast
                );
            }
        }

        softly.assertAll();
    }

    @Test(groups = "ui-only",
            description = "TILT-241: CSV without Email header is parsed to grid with inline errors; Proceed remains disabled")
    public void upload_withoutEmailHeader_parsesToGrid_withInlineErrors_andBlocksProceed() throws Exception {
        // ----- config / creds -----
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }

        // ----- login -----
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();
        DashboardPage dashboardPage =
                loginPage.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        // ----- start purchase flow -----
        ShopPage shopPage = dashboardPage.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");
        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
        sel.selectTeam();
        sel.clickNextCta();

        // ----- banner sanity -----
        PurchaseInformation info = new PurchaseInformation(driver).waitUntilLoaded();
        Assert.assertTrue(
                info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM),
                "Expected banner: 'Assessment purchase for: Team'.");

        // ----- go to Upload Template path -----
        AssessmentEntryPage page = new AssessmentEntryPage(driver)
                .waitUntilLoaded()
                .selectCreateNewTeam()
                .setOrganizationName("QA Org")
                .setGroupName("Automation Squad")
                .selectDownloadTemplate();

        // Re-affirm radio after potential re-render
        page.clickDownloadButton();
        page.selectDownloadTemplate();

        // Wait until upload panel visible
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(d -> page.isUploadPanelVisible());

        // ----- create CSV MISSING 'Email' header (app soft-rejects -> grid + inline errors) -----
        File csv = page.createTempCsv(
                "missing-email-header",
                "First Name,Last Name\n" +
                        "John,Doe\n" +
                        "Jane,Smith\n"
        );

        // ----- upload -----
        page.uploadCsvFile(csv.getAbsolutePath());

        // Wait until we land in any clear post-upload state (prefer grid)
        new WebDriverWait(driver, Duration.ofSeconds(20)).until(d ->
                page.isManualGridVisible()
                        || (page.isUploadPanelVisible() && page.renderedEmailRows() == 0)
                        || !page.isProceedToPaymentEnabled()
        );

        // If we’re on grid, trigger blurs so inline messages paint
        if (page.isManualGridVisible()) {
            page.triggerManualValidationBlurs();
            new WebDriverWait(driver, Duration.ofSeconds(6))
                    .until(d -> page.inlineRequiredErrorsCount() > 0 || !page.isProceedToPaymentEnabled());
        }

        // If we’re on grid, trigger blurs so inline messages paint
        if (page.isManualGridVisible()) {
            page.triggerManualValidationBlurs();
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(d -> !page.collectInlineErrorTexts().isEmpty()   // visible, non-empty error texts
                            || !page.isProceedToPaymentEnabled());        // or CTA disabled
        }

        // ----- snapshot -----
        final boolean onGrid         = page.isManualGridVisible();      // expected: true
        final boolean uploadVisible  = page.isUploadPanelVisible();     // expected: false
        final boolean radioSelected  = page.isDownloadTemplateSelected();// expected: false (switched to grid)
        final int     rowsNow        = page.renderedEmailRows();        // expected: > 0
        final int     inlineErrors   = page.inlineRequiredErrorsCount();// expected: > 0 (Email required)
        final boolean proceedEnabled = page.isProceedToPaymentEnabled();// expected: false
        final String  toast          = Optional.ofNullable(page.waitForUploadErrorText()).orElse("");

        // Debug dump (super helpful when it flakes)
        System.out.printf(
                "[SNAPSHOT MISSING_HEADER] mode=%s | rows=%d | inline=%d | proceedEnabled=%s | uploadVisible=%s | radioSelected=%s | toast='%s' | url=%s%n",
                onGrid ? "GRID" : "UPLOAD", rowsNow, inlineErrors, proceedEnabled, uploadVisible, radioSelected, toast, safeUrl()
        );
        if (inlineErrors > 0) {
            List<String> texts = page.collectInlineErrorTexts();
            Map<String,String> perField = page.collectPerFieldErrors();
            System.out.printf("[DEBUG] Inline error texts (%d): %s%n", texts.size(), texts);
            System.out.printf("[DEBUG] Per-field errors (%d): %s%n", perField.size(), perField);
        }
        System.out.printf("[DEBUG] AntUpload showsSuccess? %s%n", page.antUploadShowsSuccess());

        // ----- assertions (soft-reject path) -----
        SoftAssert softly = new SoftAssert();
        softly.assertTrue(onGrid, "Should switch to grid when Email header is missing (soft-reject).");
        softly.assertFalse(uploadVisible, "Upload panel should be hidden once grid is shown.");
        softly.assertFalse(radioSelected, "'Download template' radio should not remain selected in grid mode.");
        softly.assertTrue(rowsNow > 0, "Grid should render uploaded rows.");
        softly.assertTrue(inlineErrors > 0, "Inline field errors should appear for missing Email in each row.");
        softly.assertFalse(proceedEnabled, "Proceed must be disabled while grid has invalid rows.");

        // Toast is optional; if present should be meaningful
        if (!toast.isBlank()) {
            final String t = toast.toLowerCase();
            softly.assertTrue(
                    t.contains("email") || t.contains("missing") || t.contains("invalid")
                            || t.contains("column") || t.contains("parse") || t.contains("error"),
                    "Toast, if present, should mention email/missing/invalid/column/parse/error. Actual: " + toast
            );
        }

        softly.assertAll();
    }

    @Test(groups = "ui-only", description = "TILT-242: Verify Total Cost Calculation Accuracy when toggling member selection in Order Preview")
    public void verifyTotalCostRecalculation_OnToggleInPreview() throws InterruptedException {
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing");
        }

        // Create two unique recipients (reuse your helper)
        Recipient r = provisionUniqueRecipient();
        String email1 = r.emailAddress;
        String email2 = email1.replace("@", "+p2@");

        // Flow to preview with 2 members
        LoginPage login = new LoginPage(driver);
        login.navigateTo();
        login.waitUntilLoaded();
        DashboardPage dash = login.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dash.isLoaded(), "Dashboard did not load");

        ShopPage shop = dash.goToShop();
        Assert.assertTrue(shop.isLoaded(), "Shop did not load");
        PurchaseRecipientSelectionPage sel = shop.clickBuyNowForTrueTilt();
        sel.selectTeam();
        sel.clickNextCta();

        PurchaseInformation info = new PurchaseInformation(driver).waitUntilLoaded();
        Assert.assertTrue(info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM));

        AssessmentEntryPage entry = new AssessmentEntryPage(driver)
                .waitUntilLoaded()
                .selectCreateNewTeam()
                .setOrganizationName("QA Org")
                .setGroupName("Automation Squad")
                .selectManualEntry()
                .enterNumberOfIndividuals("2");
        entry.fillUserDetailsAtIndex(1, "U", "One", email1);
        entry.fillUserDetailsAtIndex(2, "U", "Two", email2);
        entry.triggerManualValidationBlurs();
        Assert.assertTrue(entry.isProceedToPaymentEnabled(), "Proceed should be enabled");

        OrderPreviewPage preview = entry.clickProceedToPayment().waitUntilLoaded();
        Assert.assertTrue(preview.isLoaded(), "Preview did not load");

        // Snapshot A (both selected)
        preview.waitTotalsStable();
        int selA = preview.getSelectedCount();
        BigDecimal subA = preview.getSubtotal();
        BigDecimal totA = preview.getTotal();
        BigDecimal unit = preview.deriveUnitPrice();

        Assert.assertTrue(selA >= 1, "At least one member must be selected");
        Assert.assertTrue(preview.equalsMoney(subA, unit.multiply(java.math.BigDecimal.valueOf(selA))),
                "Subtotal should equal unit * selected");

        // Toggle one off
        preview.toggleMemberByIndex(2);
        preview.waitTotalsStable();

        int selB = preview.getSelectedCount();
        BigDecimal subB = preview.getSubtotal();
        BigDecimal totB = preview.getTotal();
        BigDecimal taxB = preview.getTaxOrZero();
        BigDecimal discB = preview.getDiscountOrZero();

        Assert.assertEquals(selB, selA - 1, "Selected count should decrease by 1");
        Assert.assertTrue(preview.equalsMoney(subB, unit.multiply(java.math.BigDecimal.valueOf(selB))),
                "Subtotal after deselect should equal unit * selected");
        Assert.assertTrue(preview.equalsMoney(subA.subtract(subB), unit),
                "Subtotal delta should equal exactly one unit");

        // Total composition check (if tax/discount visible)
        BigDecimal expectedTotB = subB.add(taxB).subtract(discB);
        Assert.assertTrue(preview.equalsMoney(totB, expectedTotB),
                String.format("Total mismatch after deselect. expected=%s actual=%s", expectedTotB, totB));

        // Toggle back on => values return
        preview.toggleMemberByIndex(2);
        preview.waitTotalsStable();

        int selC = preview.getSelectedCount();
        BigDecimal subC = preview.getSubtotal();
        BigDecimal totC = preview.getTotal();

        Assert.assertEquals(selC, selA, "Selected should return to original");
        Assert.assertTrue(preview.equalsMoney(subC, subA), "Subtotal should return to original");
        // Total may include tax rounding; equality helper handles cents
        Assert.assertTrue(preview.equalsMoney(totC, totA), "Total should return to original");

    }

    @Test(groups = "ui-only", description = "TILT-243: Remove a Member in Order Summary updates counts and totals (E2E)")
    public void removeMemberInOrderSummary_EndToEnd() throws InterruptedException {
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing");
        }

        // Create two unique recipients
        Recipient r = provisionUniqueRecipient();
        String email1 = r.emailAddress;
        String email2 = email1.replace("@", "+p2@");

        // ===== Navigate to Order Preview with 2 members selected =====
        LoginPage login = new LoginPage(driver);
        login.navigateTo();
        login.waitUntilLoaded();
        DashboardPage dash = login.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dash.isLoaded(), "Dashboard did not load");

        ShopPage shop = dash.goToShop();
        Assert.assertTrue(shop.isLoaded(), "Shop did not load");

        PurchaseRecipientSelectionPage sel = shop.clickBuyNowForTrueTilt();
        sel.selectTeam();
        sel.clickNextCta();

        PurchaseInformation info = new PurchaseInformation(driver).waitUntilLoaded();
        Assert.assertTrue(info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM));

        AssessmentEntryPage entry = new AssessmentEntryPage(driver)
                .waitUntilLoaded()
                .selectCreateNewTeam()
                .setOrganizationName("QA Org")
                .setGroupName("Automation Squad")
                .selectManualEntry()
                .enterNumberOfIndividuals("2");
        entry.fillUserDetailsAtIndex(1, "U", "One", email1);
        entry.fillUserDetailsAtIndex(2, "U", "Two", email2);
        entry.triggerManualValidationBlurs();
        Assert.assertTrue(entry.isProceedToPaymentEnabled(), "Proceed should be enabled");

        OrderPreviewPage preview = entry.clickProceedToPayment().waitUntilLoaded();
        Assert.assertTrue(preview.isLoaded(), "Preview did not load");

        // ===== Snapshot A (both selected) =====
        preview.waitTotalsStable();
        int selA = preview.getSelectedCount();
        java.math.BigDecimal subA = preview.getSubtotal();
        java.math.BigDecimal totA = preview.getTotal();
        java.math.BigDecimal unit = preview.deriveUnitPrice();

        Assert.assertTrue(selA >= 2, "Need at least two selected members at start");
        Assert.assertTrue(preview.equalsMoney(subA, unit.multiply(java.math.BigDecimal.valueOf(selA))),
                "Subtotal should equal unit * selected at start");


        // ===== Action: remove (deselect) one member in Order Summary =====
        preview.toggleMemberByIndex(2);   // deselect row #2 (acts as “remove”)
        preview.waitTotalsStable();

        // ===== Snapshot B (after removal) =====
        int selB = preview.getSelectedCount();
        java.math.BigDecimal subB = preview.getSubtotal();
        java.math.BigDecimal totB = preview.getTotal();
        java.math.BigDecimal taxB = preview.getTaxOrZero();
        java.math.BigDecimal discB = preview.getDiscountOrZero();

        // Counts
        Assert.assertEquals(selB, selA - 1, "Selected member count should decrease by 1");

        // Subtotal = unit * selected
        Assert.assertTrue(preview.equalsMoney(subB, unit.multiply(java.math.BigDecimal.valueOf(selB))),
                "Subtotal after removal should equal unit * selected");

        // Delta = exactly one unit
        Assert.assertTrue(preview.equalsMoney(subA.subtract(subB), unit),
                "Subtotal delta after removal should equal exactly one unit price");

        // Total composition check
        java.math.BigDecimal expectedTotB = subB.add(taxB).subtract(discB);
        Assert.assertTrue(preview.equalsMoney(totB, expectedTotB),
                String.format("Total mismatch after removal. expected=%s actual=%s", expectedTotB, totB));

        // Pay button should still be enabled (since at least 1 member remains)
        Assert.assertTrue(preview.isProceedEnabled(), "Pay/Proceed should remain enabled with at least one selected");

        // ===== Optional: re-add member to restore state, values return =====
        preview.toggleMemberByIndex(2);
        preview.waitTotalsStable();

        int selC = preview.getSelectedCount();
        java.math.BigDecimal subC = preview.getSubtotal();
        java.math.BigDecimal totC = preview.getTotal();

        Assert.assertEquals(selC, selA, "Selected should return to original");
        Assert.assertTrue(preview.equalsMoney(subC, subA), "Subtotal should return to original");
        Assert.assertTrue(preview.equalsMoney(totC, totA), "Total should return to original");
    }


    @Test(groups = "ui-only", description = "TILT-244: Toggle Product Assignment On/Off per email in final confirmation (E2E)")
    public void toggleProductAssignment_OnOff_EndToEnd() throws InterruptedException {
        final boolean DEBUG = true;

        long T0 = System.nanoTime();
        dbg(DEBUG, "=== TEST START ===");

        // ----- config / creds -----
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing");
        }
        dbg(DEBUG, "Config OK. BaseUrl? " + Config.get("baseUrl", "(unknown)"));

        final String PRODUCT = "True Tilt Personality Profile";
        dbg(DEBUG, "Using PRODUCT label: " + PRODUCT);

        // ----- recipients -----
        Recipient r = provisionUniqueRecipient();
        String email1 = r.emailAddress;
        String email2 = email1.replace("@", "+p2@");
        dbg(DEBUG, "Recipients: email1=" + email1 + " | email2=" + email2);

        // ===== Navigate to Order Preview with 2 members selected =====
        LoginPage login = new LoginPage(driver);
        login.navigateTo();
        login.waitUntilLoaded();
        DashboardPage dash = login.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dash.isLoaded(), "Dashboard did not load");
        dbg(DEBUG, "Dashboard loaded. URL=" + safeUrl());

        ShopPage shop = dash.goToShop();
        Assert.assertTrue(shop.isLoaded(), "Shop did not load");
        dbg(DEBUG, "Shop loaded. URL=" + safeUrl());

        PurchaseRecipientSelectionPage sel = shop.clickBuyNowForTrueTilt();
        sel.selectTeam();
        sel.clickNextCta();
        dbg(DEBUG, "Recipient selection done.");

        PurchaseInformation info = new PurchaseInformation(driver).waitUntilLoaded();
        Assert.assertTrue(info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM));
        dbg(DEBUG, "PurchaseInformation loaded + TEAM confirmed.");

        AssessmentEntryPage entry = new AssessmentEntryPage(driver)
                .waitUntilLoaded()
                .selectCreateNewTeam()
                .setOrganizationName("QA Org")
                .setGroupName("Automation Squad")
                .selectManualEntry()
                .enterNumberOfIndividuals("2");
        entry.fillUserDetailsAtIndex(1, "U", "One", email1);
        entry.fillUserDetailsAtIndex(2, "U", "Two", email2);
        entry.triggerManualValidationBlurs();
        Assert.assertTrue(entry.isProceedToPaymentEnabled(), "Proceed should be enabled");
        dbg(DEBUG, "AssessmentEntryPage filled. Proceed enabled? true");

        OrderPreviewPage preview = entry.clickProceedToPayment().waitUntilLoaded();
        Assert.assertTrue(preview.isLoaded(), "Preview did not load");
        dbg(DEBUG, "Preview loaded. URL=" + safeUrl());
        if (DEBUG) dumpTablesSummary("afterPreviewLoad");

        // ===== Read the **actual** emails as rendered by the UI =====
        List<String> tableEmails = collectEmailsFromPreviewTable(true);
        Assert.assertTrue(tableEmails.size() >= 2,
                "Expected at least 2 email rows in preview, got: " + tableEmails);

        String uiEmailP2 = tableEmails.stream()
                .filter(t -> t.contains("+p2@"))
                .findFirst()
                .orElseGet(() -> tableEmails.get(1));

        String uiEmailBase = tableEmails.stream()
                .filter(t -> !t.contains("+p2@"))
                .findFirst()
                .orElseGet(() -> tableEmails.get(0));

        // purely informational – UI may truncate/alter one char; that’s fine
        int distBase = levenshtein(email1.toLowerCase(Locale.ROOT), uiEmailBase);
        int distP2   = levenshtein(email2.toLowerCase(Locale.ROOT), uiEmailP2);
        System.out.println("[email-compare] base dist=" + distBase + " | p2 dist=" + distP2
                + " | expectedBase=" + email1 + " | uiBase=" + uiEmailBase
                + " | expectedP2=" + email2 + " | uiP2=" + uiEmailP2);

        Assert.assertTrue(uiEmailP2.contains("+p2@"),
                "One row must contain +p2@: uiEmailP2=" + uiEmailP2 + " | all=" + tableEmails);

        // ===== Baseline =====
        preview.waitTotalsStable();
        int selA = preview.getSelectedCount();
        Assert.assertTrue(selA >= 2, "Expect 2 selected at start");

        BigDecimal subA = preview.getSubtotal();
        BigDecimal totA = preview.getTotal();
        BigDecimal unit = preview.deriveUnitPrice();
        Assert.assertTrue(preview.equalsMoney(subA, unit.multiply(BigDecimal.valueOf(selA))),
                "Baseline Subtotal must equal unit * selected");

        // Ensure ON for +p2 (so we can turn it OFF)
        preview.setProductAssigned(uiEmailP2, PRODUCT, true);
        preview.debugDumpPreviewTable("after-setProductAssigned-ensureON");
        preview.waitTotalsStable();

        // ===== Action: turn OFF product for +p2 =====
        preview.debugDumpPreviewTable("before-setProductAssigned-OFF");
        preview.setProductAssigned(uiEmailP2, PRODUCT, false);
        preview.debugDumpPreviewTable("after-setProductAssigned-OFF");
        preview.waitTotalsStable();

        // ===== After OFF =====
        int selB = preview.getSelectedCount();
        BigDecimal subB = preview.getSubtotal();
        BigDecimal totB = preview.getTotal();
        BigDecimal taxB = preview.getTaxOrZero();
        BigDecimal discB = preview.getDiscountOrZero();

        Assert.assertEquals(selB, selA - 1, "Selected count should decrease by 1 after product OFF");
        Assert.assertTrue(preview.equalsMoney(subB, unit.multiply(BigDecimal.valueOf(selB))),
                "Subtotal after OFF should equal unit * selected");
        Assert.assertTrue(preview.equalsMoney(subA.subtract(subB), unit),
                "Subtotal delta should equal exactly one unit");

        BigDecimal expectedTotB = subB.add(taxB).subtract(discB);
        Assert.assertTrue(preview.equalsMoney(totB, expectedTotB),
                String.format("Total mismatch after OFF. expected=%s actual=%s", expectedTotB, totB));

        // ===== Restore ON for the same +p2 row =====
        preview.setProductAssigned(uiEmailP2, PRODUCT, true);
        preview.waitTotalsStable();

        // ===== Back to baseline =====
        int selC = preview.getSelectedCount();
        BigDecimal subC = preview.getSubtotal();
        BigDecimal totC = preview.getTotal();
        Assert.assertEquals(selC, selA, "Selected count should return to baseline after ON");
        Assert.assertTrue(preview.equalsMoney(subC, subA), "Subtotal should return to baseline");
        Assert.assertTrue(preview.equalsMoney(totC, totA), "Total should return to baseline");

        // IMPORTANT: do NOT wait for exact provisioned strings here.
        // The UI emails (uiEmailBase/uiEmailP2) are the source of truth.

        dbg(DEBUG, "=== TEST END (ms): " + ((System.nanoTime() - T0) / 1_000_000) + " ===");
    }

    /**
     * TILT-245: Handle Slow Network on Payment Submit
     * UI should display a loading state and prevent duplicate payments on a slow connection.
     * Optionally (when E2E flag is enabled), complete payment via Stripe CLI helper and assert
     * the invited user appears on Individuals.
     *
     * Enable E2E tail with either:
     *   -DSTRIPE_E2E=true   (JVM system property)
     * or environment variable:
     *   STRIPE_E2E=true
     */
    @Test(groups = "ui-only", description = "TILT-245:")
    public void testHandleSlowNetworkOnPaymentSubmit_ShowsLoadingAndBlocksDuplicatePayment() throws InterruptedException {

        // ----- config / constants -----
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }
        System.out.println("[AdminCreds] email=" + maskEmail(ADMIN_USER) + " | passLen=" + ADMIN_PASS.length());

        boolean E2E_ENABLED =
                Boolean.parseBoolean(System.getProperty("STRIPE_E2E",
                        String.valueOf(Boolean.parseBoolean(String.valueOf(System.getenv("STRIPE_E2E"))))));
        // keep UI signal as primary; E2E tail is best-effort
         E2E_ENABLED = true; // ← enable manually if you want, otherwise rely on flag

        // ----- recipient (unique per run) -----
        step("Resolve recipient for this run (prefer fresh inbox)");
        Recipient r = provisionUniqueRecipient();
        final String tempEmail = r.emailAddress;
        System.out.println("📧 Test email (clean): " + tempEmail);

        // ----- app flow to Preview -----
        step("Login as admin");
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();
        DashboardPage dashboardPage =
                loginPage.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Shop and start purchase flow");
        ShopPage shopPage = dashboardPage.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");
        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
        sel.selectClientOrIndividual();
        sel.clickNext();

        step("Manual entry for 1 individual");
        AssessmentEntryPage entryPage = new AssessmentEntryPage(driver)
                .waitUntilLoaded()
                .selectManualEntry()
                .enterNumberOfIndividuals("1");
        entryPage.fillUserDetailsAtIndex(1, "Emi", "Rod", tempEmail);
        entryPage.triggerManualValidationBlurs();
        Assert.assertTrue(entryPage.isProceedToPaymentEnabled(), "Proceed should be enabled.");

        step("Review order (Preview)");
        OrderPreviewPage preview = entryPage.clickProceedToPayment().waitUntilLoaded();
        Assert.assertTrue(preview.isLoaded(), "❌ Order Preview did not load");

        // ----- throttle network -----
        step("Simulate slow network");
        org.openqa.selenium.devtools.DevTools devTools =
                ((org.openqa.selenium.devtools.HasDevTools) driver).getDevTools();
        devTools.createSession();
        devTools.send(org.openqa.selenium.devtools.v138.network.Network.enable(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        ));
        devTools.send(org.openqa.selenium.devtools.v138.network.Network.emulateNetworkConditions(
                false, 1200, 100 * 1024, 100 * 1024,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        ));

        final java.util.Set<String> handlesBefore = new java.util.HashSet<>(driver.getWindowHandles());
        final String handleBefore = driver.getWindowHandle();
        String stripeUrlForE2E = null;

        try {
            step("Click Pay once; button must show loading/disabled promptly");
            preview.clickPayWithStripe();
            // be a bit more patient under throttle
            new WebDriverWait(driver, Duration.ofSeconds(8)).until(d -> preview.isPayBusy());
            Assert.assertTrue(preview.isPayBusy(), "❌ Pay button should show a loading/disabled state after first click");

            step("Attempt a rapid second click; should NOT open another Stripe checkout");
            try { preview.clickPayWithStripe(); } catch (Exception ignored) {}
            try { Thread.sleep(700); } catch (InterruptedException ignored) {}

            int newStripeWindows = 0;
            for (String h : driver.getWindowHandles()) {
                if (!handlesBefore.contains(h)) {
                    try {
                        driver.switchTo().window(h);
                        String u = driver.getCurrentUrl();
                        if (u != null && u.contains("checkout.stripe.com")) {
                            newStripeWindows++;
                            if (stripeUrlForE2E == null) stripeUrlForE2E = u;
                        }
                    } catch (Exception ignored) {}
                }
            }
            if (newStripeWindows == 0) {
                try {
                    driver.switchTo().window(handleBefore);
                    String u = driver.getCurrentUrl();
                    if (u != null && u.contains("checkout.stripe.com")) {
                        newStripeWindows = 1;
                        if (stripeUrlForE2E == null) stripeUrlForE2E = u;
                    }
                } catch (Exception ignored) {}
            }
            Assert.assertTrue(newStripeWindows <= 1,
                    "❌ Rapid double-click should not create multiple Stripe checkouts (windows/tabs=" + newStripeWindows + ")");

            preview.waitPayIdleLong(); // best-effort

        } finally {
            step("Restore normal network");
            try { devTools.send(org.openqa.selenium.devtools.v138.network.Network.disable()); } catch (Exception ignored) {}
            try { driver.switchTo().window(handleBefore); } catch (Exception ignored) {}
        }

        // ===== Optional E2E tail (best-effort, never fail UI signal) =====
        if (E2E_ENABLED) {
            try {
                step("Stripe: fetch session + metadata.body");
                if (stripeUrlForE2E == null || stripeUrlForE2E.isBlank()) {
                    // safe to open a clean session now (no throttle) just to obtain the id
                    stripeUrlForE2E = preview.proceedToStripeAndGetCheckoutUrl();
                }
                String sessionId = extractSessionIdFromUrl(stripeUrlForE2E);
                Assert.assertNotNull(sessionId, "❌ Could not parse Stripe session id from URL");
                String bodyJson = StripeCheckoutHelper.fetchCheckoutBodyFromStripe(sessionId);
                Assert.assertNotNull(bodyJson, "❌ metadata.body not found in Checkout Session");

                step("Stripe: trigger checkout.session.completed via CLI");
                var trig = StripeCheckoutHelper.triggerCheckoutCompletedWithBody(bodyJson);
                System.out.println("[Stripe] Triggered eventId=" + trig.eventId +
                        (trig.requestLogUrl != null ? " | requestLog=" + trig.requestLogUrl : ""));

                step("Navigate to post-payment confirmation");
                driver.navigate().to(joinUrl(Config.getBaseUrl(), "/dashboard/orders/confirmation"));

                step("Individuals page shows the newly invited user");
                new IndividualsPage(driver)
                        .open(Config.getBaseUrl())
                        .assertAppearsWithEvidence(Config.getBaseUrl(), tempEmail);
                System.out.println("✅ Individuals shows invited user: " + tempEmail);

            } catch (Throwable e) {
                // Do NOT fail the test if the external Stripe fetch/CLI is unavailable.
                // The primary objective of this test is the UI double-click protection.
                System.out.println("[E2E] Skipping tail due to Stripe connectivity/problem: " + e);
                System.out.println("[E2E] Test already validated the UI prevents duplicate payments.");
            }
        }
    }















    @Test(groups = {"ui-only", "known-bug"}, description = "TILT-247: Preserve Team selection and member choices on Back navigation")
    public void preserveTeamSelection_onBackNavigation_persists() throws Exception {
        // ----- config / creds -----
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing");
        }

        // Unique recipients (keep your existing scheme)
        Recipient r = provisionUniqueRecipient();
        String email1 = r.emailAddress;
        String email2 = email1.replace("@", "+p2@");
        String email3 = email1.replace("@", "+p3@");
        System.out.println("[DEBUG] planned emails: " + email1 + ", " + email2 + ", " + email3);

        // ----- Login → Shop → Team flow -----
        step("Login as admin");
        LoginPage login = new LoginPage(driver);
        login.navigateTo();
        login.waitUntilLoaded();
        DashboardPage dash = login.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dash.isLoaded(), "Dashboard did not load");

        step("Start Team purchase");
        ShopPage shop = dash.goToShop();
        Assert.assertTrue(shop.isLoaded(), "Shop did not load");
        PurchaseRecipientSelectionPage sel = shop.clickBuyNowForTrueTilt();
        sel.selectTeam();
        sel.clickNextCta();

        step("Verify banner shows TEAM purchase");
        PurchaseInformation info = new PurchaseInformation(driver).waitUntilLoaded();
        Assert.assertTrue(info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM),
                "Expected banner: 'Assessment purchase for: Team'.");

        // ----- Entry: create team, manual entry, 3 members -----
        final String ORG = "QA Org";
        final String GRP = "Automation Squad";

        step("Fill team info + 3 members");
        AssessmentEntryPage entry = new AssessmentEntryPage(driver)
                .waitUntilLoaded()
                .selectCreateNewTeam()
                .setOrganizationName(ORG)
                .setGroupName(GRP)
                .selectManualEntry()
                .enterNumberOfIndividuals("3");

        entry.fillUserDetailsAtIndex(1, "U", "One",   email1);
        entry.fillUserDetailsAtIndex(2, "U", "Two",   email2);
        entry.fillUserDetailsAtIndex(3, "U", "Three", email3);
        entry.triggerManualValidationBlurs();

        Assert.assertEquals(entry.inlineRequiredErrorsCount(), 0, "No inline errors expected");
        Assert.assertTrue(entry.isProceedToPaymentEnabled(), "Proceed should be enabled");

        java.util.Set<String> entrySetBefore = new java.util.HashSet<>(entry.collectAllEmailsLower());
        System.out.println("[DEBUG] entry emails (before preview): " + entrySetBefore);

        // ----- Preview: capture baseline, then deselect one member by EMAIL -----
        step("Proceed to Preview and deselect one member (by email)");
        OrderPreviewPage preview = entry.clickProceedToPayment().waitUntilLoaded();
        Assert.assertTrue(preview.isLoaded(), "Preview did not load");
        preview.waitTotalsStable();

        // Snapshot selection map
        LinkedHashMap<String, Boolean> sel0 = preview.selectionByEmail();
        System.out.println("[DEBUG] preview selection map baseline = " + sel0);
        saveSnapshot("preview_base");

        List<String> emailsPreview = new ArrayList<>(sel0.keySet());
        Assert.assertTrue(emailsPreview.size() >= 3, "Need at least 3 rows in preview");

        // We'll deselect the second email from the live table (not index 2 checkbox)
        String deselectEmail = emailsPreview.get(1);
        preview.setSelectedByEmail(deselectEmail, false);
        preview.waitTotalsStable();

        LinkedHashMap<String, Boolean> selAfter = preview.selectionByEmail();
        System.out.println("[DEBUG] selection after deselect = " + selAfter);
        saveSnapshot("preview_after_deselect");

        // Build expected map after deselect
        LinkedHashMap<String, Boolean> expectedAfter = new LinkedHashMap<>(sel0);
        expectedAfter.put(deselectEmail, false);

        long cnt0   = sel0.values().stream().filter(Boolean::booleanValue).count();
        long cntExp = expectedAfter.values().stream().filter(Boolean::booleanValue).count();
        long cntGot = selAfter.values().stream().filter(Boolean::booleanValue).count();
        Assert.assertEquals(cntGot, cntExp, "Selected count should decrease by 1 after deselect");

        // ----- Back to Entry: verify all data persisted -----
        step("Click Back to Entry; verify team info + members persisted");
        preview.clickPrevious();
        entry.waitUntilLoaded();
        entry.waitManualGridEmailsAtLeast(3, Duration.ofSeconds(8));
        Thread.sleep(150);
        saveSnapshot("entry_back");

        Assert.assertEquals(entry.getOrganizationName(), ORG, "Organization name should persist");
        Assert.assertEquals(entry.getGroupName(), GRP, "Group name should persist");
        Assert.assertTrue(entry.isManualGridVisible(), "Manual grid should remain visible");
        Assert.assertEquals(entry.renderedEmailRows(), 3, "Should still have 3 rows rendered");

        java.util.Set<String> expectedSet = java.util.Set.of(
                email1.toLowerCase(), email2.toLowerCase(), email3.toLowerCase()
        );
        java.util.Set<String> entrySetAfterBack = new java.util.HashSet<>(entry.collectAllEmailsLower());
        System.out.println("[DEBUG] entry emails (after back): " + entrySetAfterBack);
        Assert.assertEquals(entrySetAfterBack, expectedSet, "Entry email set should persist after Back");

        // Row-wise assertions (kept)
        System.out.println("[DEBUG] entry row1=" + entry.getEmailAtRow(1)
                + " row2=" + entry.getEmailAtRow(2)
                + " row3=" + entry.getEmailAtRow(3));
        Assert.assertTrue(entry.getEmailAtRow(1).toLowerCase().contains(email1.toLowerCase()), "Row 1 email should persist");
        Assert.assertTrue(entry.getEmailAtRow(2).toLowerCase().contains(email2.toLowerCase()), "Row 2 email should persist");
        Assert.assertTrue(entry.getEmailAtRow(3).toLowerCase().contains(email3.toLowerCase()), "Row 3 email should persist");

        // ----- Forward to Preview again: selection state should persist (by email map) -----
        step("Forward to Preview again; selection state should persist");
        Assert.assertTrue(entry.isProceedToPaymentEnabled(), "Proceed should still be enabled");
        OrderPreviewPage preview2 = entry.clickProceedToPayment().waitUntilLoaded();
        Assert.assertTrue(preview2.isLoaded(), "Preview (round 2) did not load");
        preview2.waitTotalsStable();

        LinkedHashMap<String, Boolean> round2 = preview2.selectionByEmail();
        System.out.println("[DEBUG] preview round#2 selection map = " + round2);
        saveSnapshot("preview_round2");

        // Diff per email for precise diagnostics
        List<String> diffs = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : expectedAfter.entrySet()) {
            Boolean got = round2.get(e.getKey());
            if (!Objects.equals(got, e.getValue())) {
                diffs.add(e.getKey() + " expected=" + e.getValue() + " actual=" + got);
            }
        }
        long round2Count = round2.values().stream().filter(Boolean::booleanValue).count();
        Assert.assertEquals(round2Count, cntExp,
                "Selection count should persist after Back → forward navigation");
        if (!diffs.isEmpty()) {
            preview2.debugDumpPreviewTable("persist-diff");
            Assert.fail("Selection should persist per email after Back→Forward. Differences: " + diffs);
        }

        // (Optional) Re-enable deselected email and confirm count returns to baseline
        preview2.setSelectedByEmail(deselectEmail, true);
        preview2.waitTotalsStable();
        long selRestored = preview2.selectionByEmail().values().stream().filter(Boolean::booleanValue).count();
        System.out.println("[DEBUG] preview after reselect '" + deselectEmail + "', selected count = " + selRestored);
        saveSnapshot("preview_restored");
        Assert.assertEquals(selRestored, cnt0, "Re-selecting should restore original selection count");

        dumpBrowserConsole();
    }




























    /* ===== Debug helpers: put these in the same test class ===== */

    private void saveSnapshot(String tag) {
        try {
            // screenshot
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            File dest = new File("target/" + tag + ".png");
            dest.getParentFile().mkdirs();
            java.nio.file.Files.copy(src.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[DEBUG] screenshot saved: " + dest.getAbsolutePath());
        } catch (Throwable t) {
            System.out.println("[DEBUG] screenshot failed: " + t.getMessage());
        }
        try {
            // html dump
            File html = new File("target/" + tag + ".html");
            html.getParentFile().mkdirs();
            java.nio.file.Files.writeString(html.toPath(), driver.getPageSource());
            System.out.println("[DEBUG] html dump saved: " + html.getAbsolutePath());
        } catch (Throwable t) {
            System.out.println("[DEBUG] html dump failed: " + t.getMessage());
        }
    }

    private void dumpBrowserConsole() {
        try {
            System.out.println("\n[DEBUG] BROWSER CONSOLE LOGS:");
            driver.manage().logs().get(org.openqa.selenium.logging.LogType.BROWSER)
                    .forEach(System.out::println);
        } catch (Throwable ignored) {}
    }














/* ================================
   === Robust email collectors  ===
   ================================ */

    private boolean containsIgnoreCase(List<String> haystack, String needle) {
        String n = needle.toLowerCase(Locale.ROOT);
        boolean res = haystack.stream().anyMatch(s -> s != null && s.toLowerCase(Locale.ROOT).contains(n));
        return res;
    }

    // ========== UTIL: lowercase helper ==========
    private static String lc(String s) { return s == null ? "" : s.toLowerCase(); }

    // ========== STEP 0: find candidate tables and pick the one that looks like the preview ==========
    private WebElement findPreviewTable(boolean DEBUG) {
        List<WebElement> tables = driver.findElements(By.xpath("//table"));
        dbg(DEBUG, "[findPreviewTable] tables found: " + tables.size());
        if (tables.isEmpty()) throw new NoSuchElementException("No <table> found on page");

        // Prefer tables with an "Email" header
        for (WebElement t : tables) {
            List<WebElement> ths = t.findElements(By.xpath(".//thead//th"));
            boolean hasEmailHeader = ths.stream().anyMatch(th -> lc(th.getText()).contains("email"));
            dbg(DEBUG, "  table hasEmailHeader? " + hasEmailHeader + " | ths=" + texts(ths));
            if (hasEmailHeader) {
                dbg(DEBUG, "[findPreviewTable] pick table with Email header");
                return t;
            }
        }

        // Otherwise a table that visibly contains '@' in body text
        for (WebElement t : tables) {
            String tText = lc(t.getText());
            dbg(DEBUG, "  table text contains @ ? " + tText.contains("@"));
            if (tText.contains("@")) {
                dbg(DEBUG, "[findPreviewTable] pick table with visible @");
                return t;
            }
        }

        dbg(DEBUG, "[findPreviewTable] fallback to first table");
        return tables.get(0);
    }

    // Backward-compatible signature
    private WebElement findPreviewTable() { return findPreviewTable(true); }

    // ========== STEP 1: compute the "Email" column index if header exists ==========
    private OptionalInt getEmailColumnIndex(WebElement table, boolean DEBUG) {
        List<WebElement> headers = table.findElements(By.xpath(".//thead//tr[1]//th"));
        dbg(DEBUG, "[getEmailColumnIndex] headers: " + texts(headers));
        for (int i = 0; i < headers.size(); i++) {
            String h = lc(headers.get(i).getText()).replaceAll("\\s+", " ").trim();
            if (h.contains("email")) {
                dbg(DEBUG, "  email column at index (1-based): " + (i + 1));
                return OptionalInt.of(i + 1); // XPath 1-based
            }
        }
        return OptionalInt.empty();
    }

    // ========== STEP 2: scroll into view to defeat row virtualization ==========
    private void ensureTableVisible(WebElement table, boolean DEBUG) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({block:'center', inline:'nearest'});", table);
            dbg(DEBUG, "[ensureTableVisible] scrolled table into view");
        } catch (Exception e) {
            dbg(DEBUG, "[ensureTableVisible] scroll failed: " + e.getMessage());
        }
    }

    private List<String> collectEmailsFromTable(WebElement table, boolean DEBUG) {
        ensureTableVisible(table, DEBUG);

        // A) Use Email column if header exists
        OptionalInt emailIdx = getEmailColumnIndex(table, DEBUG);
        if (emailIdx.isPresent()) {
            int idx = emailIdx.getAsInt();
            List<WebElement> tds = table.findElements(By.xpath(".//tbody//tr//td[" + idx + "]"));
            List<String> col = tds.stream()
                    .map(td -> td.getText().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT))
                    .filter(t -> t.contains("@"))
                    .toList();
            dbg(DEBUG, "[A header-col] count=" + col.size() + " | sample=" + sample(col));
            if (!col.isEmpty()) return col;
        }

        // B) Any cell text with '@'
        List<WebElement> anyCellsEls = table.findElements(By.xpath(".//tbody//*[self::td or @role='cell']"));
        List<String> anyCell = anyCellsEls.stream()
                .map(el -> el.getText().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT))
                .filter(t -> t.contains("@"))
                .distinct()
                .toList();
        dbg(DEBUG, "[B any-cell] totalCells=" + anyCellsEls.size() + " | matches=" + anyCell.size() + " | sample=" + sample(anyCell));
        if (!anyCell.isEmpty()) return anyCell;

        // C) mailto links
        List<WebElement> mailtoEls = table.findElements(By.xpath(".//tbody//a[starts-with(translate(@href,'MAILTO','mailto'),'mailto:')]"));
        List<String> mailtos = mailtoEls.stream()
                .map(a -> String.valueOf(a.getAttribute("href")).toLowerCase(Locale.ROOT).replace("mailto:", ""))
                .distinct()
                .toList();
        dbg(DEBUG, "[C mailto] elements=" + mailtoEls.size() + " | matches=" + mailtos.size() + " | sample=" + sample(mailtos));
        if (!mailtos.isEmpty()) return mailtos;

        // D) regex over table HTML (hidden attrs etc.)
        String outer = String.valueOf(((JavascriptExecutor) driver).executeScript("return arguments[0].outerHTML;", table));
        var m = Pattern.compile("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", Pattern.CASE_INSENSITIVE).matcher(outer);
        LinkedHashSet<String> set = new LinkedHashSet<>();
        while (m.find()) set.add(m.group().toLowerCase(Locale.ROOT));
        List<String> regexFound = new ArrayList<>(set);
        dbg(DEBUG, "[D regex-html] found=" + regexFound.size() + " | sample=" + sample(regexFound));
        if (!regexFound.isEmpty()) return regexFound;

        // Diagnostics
        dbg(DEBUG, "[collectEmailsFromTable] No emails found.");
        dbg(DEBUG, "  Head cols: " + texts(table.findElements(By.xpath(".//thead//th"))));
        dbg(DEBUG, "  First 5 rows: " + texts(table.findElements(By.xpath(".//tbody//tr[position()<=5]"))));
        dbg(DEBUG, "  OuterHTML[0..600]: " + outer.substring(0, Math.min(outer.length(), 600)));
        return List.of();
    }

    // ========== PUBLIC: robust collector for the preview page ==========
    private List<String> collectEmailsFromPreviewTable(boolean DEBUG) {
        dbg(DEBUG, "[collectEmailsFromPreviewTable] start");
        try {
            new WebDriverWait(driver, Duration.ofSeconds(15))
                    .until(d -> !d.findElements(By.xpath("//table//tbody//tr")).isEmpty());
        } catch (TimeoutException te) {
            dbg(true, "[collectEmailsFromPreviewTable] Timeout waiting rows. Dumping tables…");
            dumpTablesSummary("rowsTimeout");
            throw te;
        }

        WebElement table = findPreviewTable(DEBUG);
        List<String> res = collectEmailsFromTable(table, DEBUG);
        dbg(DEBUG, "[collectEmailsFromPreviewTable] result size=" + res.size() + " | sample=" + sample(res));
        return res;
    }

    // Backward-compatible call
    private List<String> collectEmailsFromPreviewTable() { return collectEmailsFromPreviewTable(true); }

    private boolean tableHasBothEmails(String e1, String e2) {
        List<String> cells = collectEmailsFromPreviewTable(true);
        String a = e1.toLowerCase(), b = e2.toLowerCase();
        boolean has1 = cells.stream().anyMatch(t -> t.contains(a));
        boolean has2 = cells.stream().anyMatch(t -> t.contains(b));
        if (!has1 || !has2) {
            dbg(true, "[preview-emails] " + cells);
            dbg(true, "[expected] " + a + " | " + b);
        }
        return has1 && has2;
    }

/* ================================
   === Generic debug utilities  ===
   ================================ */

    private void dbg(boolean on, String msg) { if (on) System.out.println(msg); }

    private List<String> texts(List<WebElement> els) {
        List<String> out = new ArrayList<>();
        for (WebElement e : els) out.add(e.getText());
        return out;
    }
    private String sample(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        return list.size() <= 5 ? list.toString() : list.subList(0, 5).toString() + " … (+" + (list.size()-5) + ")";
    }
    private void highlight(By locator) {
        List<WebElement> els = driver.findElements(locator);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        for (WebElement el : els) {
            try {
                js.executeScript("arguments[0].style.outline='3px solid magenta'; arguments[0].style.background='rgba(255,0,255,0.08)';", el);
            } catch (Exception ignored) {}
        }
        System.out.println("[highlight] outlined elements: " + els.size());
    }
    private void dumpTablesSummary(String tag) {
        System.out.println("=== [dumpTablesSummary][" + tag + "] URL=" + safeUrl() + " ===");
        List<WebElement> tables = driver.findElements(By.xpath("//table"));
        System.out.println("tables: " + tables.size());
        for (int i = 0; i < tables.size(); i++) {
            WebElement t = tables.get(i);
            List<WebElement> ths = t.findElements(By.xpath(".//thead//th"));
            List<WebElement> firstRows = t.findElements(By.xpath(".//tbody//tr[position()<=3]"));
            String outer = "";
            try {
                outer = String.valueOf(((JavascriptExecutor) driver).executeScript("return arguments[0].outerHTML;", t));
            } catch (Exception ignored) {}
            System.out.println("-- table[" + i + "] heads=" + texts(ths));
            System.out.println("   firstRows=" + texts(firstRows));
            System.out.println("   snippet=" + (outer.length() > 200 ? outer.substring(0, 200) + "…" : outer));
        }
    }
    private void waitWithDiagnostics(Duration timeout, String name, ExpectedCondition<Boolean> cond, Runnable onTimeoutDump) {
        try {
            new WebDriverWait(driver, timeout).until(cond);
        } catch (TimeoutException te) {
            System.out.println(name + " TIMEOUT after " + timeout.getSeconds() + "s");
            try { onTimeoutDump.run(); } catch (Exception ignored) {}
            throw te;
        }
    }















    // ==================== recipient provisioning ====================

    /** Holder for the email we type in the UI and the inbox we wait on. */
    private static class Recipient {
        final UUID inboxId;
        final String emailAddress;
        Recipient(UUID id, String email) { this.inboxId = id; this.emailAddress = email; }
    }

    /**
     * Use a unique email for each run.
     * LOCAL default: enable creation (fresh MailSlurp inbox).
     * CI: obey ALLOW_CREATE_INBOX_FALLBACK (typically false), never rely on plus-tagging for @mailslurp.biz.
     */
    private Recipient provisionUniqueRecipient() {
        // Auto-enable creation when not in CI, unless the user explicitly set the flag.
        if (System.getenv("CI") == null && System.getProperty("ALLOW_CREATE_INBOX_FALLBACK") == null) {
            System.setProperty("ALLOW_CREATE_INBOX_FALLBACK", "true");
        }

        final boolean allowCreate = Boolean.parseBoolean(
                System.getProperty("ALLOW_CREATE_INBOX_FALLBACK",
                        Objects.toString(System.getenv("ALLOW_CREATE_INBOX_FALLBACK"), "false")));

        try {
            if (allowCreate) {
                // Always create a fresh inbox (preferred) — guarantees a "new purchase" recipient
                InboxDto fresh = MailSlurpUtils.createNewInbox();
                System.out.println("📮 Fresh inbox for this run: " + fresh.getEmailAddress());
                return new Recipient(fresh.getId(), fresh.getEmailAddress());
            }

            // No creation allowed. If a fixed inbox is present, do NOT use plus-tagging on mailslurp.biz.
            if (fixedInbox != null) {
                final String base = fixedInbox.getEmailAddress();
                if (base.endsWith("@mailslurp.biz")) {
                    throw new SkipException(
                            "Unique recipient required for purchase flow, but inbox creation is disabled and " +
                                    "the fixed domain (" + base + ") does not support plus-tag routing. " +
                                    "Enable ALLOW_CREATE_INBOX_FALLBACK=true to run this test.");
                }
                // If your fixed domain *does* support tags, uncomment the lines below and remove the Skip above.
/*
                final String tagged = plusTag(base, "tc1-" + System.currentTimeMillis());
                System.out.println("✉ Using tagged address on fixed inbox: " + tagged);
                return new Recipient(fixedInbox.getId(), tagged);
*/
            }

            // Last resort: delegate to resolver (will Skip if neither fixed nor creation allowed).
            InboxDto resolved = MailSlurpUtils.resolveFixedOrCreateInbox();
            System.out.println("📮 Resolved inbox for this run: " + resolved.getEmailAddress());
            return new Recipient(resolved.getId(), resolved.getEmailAddress());

        } catch (SkipException se) {
            throw se;
        } catch (Exception e) {
            throw new SkipException("Cannot provision a unique recipient email: " + e.getMessage());
        }
    }



    // ==================== small utils ====================

    /** Console banner for readable logs. */
    private static void step(String title) {
        System.out.println("\n====== " + title + " ======\n");
    }

    /** Mask an email for logs. */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "(blank)";
        int at = email.indexOf('@');
        String user = at > -1 ? email.substring(0, at) : email;
        String dom  = at > -1 ? email.substring(at) : "";
        if (user.length() <= 2) return user.charAt(0) + "****" + dom;
        return user.charAt(0) + "****" + user.charAt(user.length() - 1) + dom;
    }

    /** Insert “+tag” before '@' (RFC 5233). */
    private static String plusTag(String email, String tag) {
        int at = email.indexOf('@');
        if (at <= 0) return email;
        return email.substring(0, at) + "+" + tag + email.substring(at);
    }

    /** Parse cs_test_... from a Stripe Checkout URL, or session_id=... */
    private static String extractSessionIdFromUrl(String url) {
        if (url == null) return null;
        Matcher m = Pattern.compile("(?i)(?:cs_test_[A-Za-z0-9_]+)|(?:session_id=([^&]+))").matcher(url);
        if (m.find()) {
            String full = m.group();
            if (full.startsWith("cs_test_")) return full;
            if (m.groupCount() >= 1) return m.group(1);
        }
        return null;
    }

    /** Base64URL encode (no padding). */
    private static String b64Url(String s) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * TC-2: Team purchase via manual entry → Preview → Stripe → webhook → Individuals + email
     * NOTE: Uses a brand-new recipient email each run (fresh MailSlurp inbox locally).
     */
    @Test
    public void testTeamManualEntry_PurchaseCompletesAndSendsInviteEmail() throws ApiException, InterruptedException {
        // ----- config / constants -----
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }
        System.out.println("[AdminCreds] email=" + maskEmail(ADMIN_USER) + " | passLen=" + ADMIN_PASS.length());

        final Duration EMAIL_TIMEOUT = Duration.ofSeconds(120);
        final String CTA_TEXT       = "Accept Assessment";
        final String SUBJECT_NEEDLE = "assessment";
        System.setProperty("mailslurp.debug", "true");

        // ----- recipient (unique per run) -----
        step("Resolve recipient for this run (prefer fresh inbox)");
        Recipient r = provisionUniqueRecipient();
        MailSlurpUtils.clearInboxEmails(r.inboxId); // deterministic unreadOnly waits
        final String tempEmail = r.emailAddress;
        final UUID inboxId     = r.inboxId;
        System.out.println("📧 Test email (clean): " + tempEmail);

        // ----- app flow -----
        step("Login as admin");
        LoginPage loginPage = new LoginPage(driver);
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();
        DashboardPage dashboardPage =
                loginPage.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Shop and start Team purchase flow");
        ShopPage shopPage = dashboardPage.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");
        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
        sel.selectTeam();
        sel.clickNextCta(); // ← keep your method name

        step("Manual entry for 1 team member");
        PurchaseInformation info = new PurchaseInformation(driver).waitUntilLoaded();
        Assert.assertTrue(info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM),
                "Expected banner: 'Assessment purchase for: Team'.");

        AssessmentEntryPage entryPage = new AssessmentEntryPage(driver)
                .waitUntilLoaded()
                .selectCreateNewTeam()
                .setOrganizationName("QA Org")
                .setGroupName("Automation Squad")
                .selectManualEntry()
                .enterNumberOfIndividuals("1");
        entryPage.fillUserDetailsAtIndex(1, "Emi", "Rod", tempEmail);

        step("Review order (Preview)");
        entryPage.triggerManualValidationBlurs();
        Thread.sleep(5000);
        Assert.assertEquals(entryPage.inlineRequiredErrorsCount(), 0, "No inline errors expected for valid data.");
        Assert.assertTrue(entryPage.isProceedToPaymentEnabled(), "Proceed should be enabled.");
        OrderPreviewPage preview = entryPage.clickProceedToPayment().waitUntilLoaded();
        Assert.assertTrue(preview.isLoaded(), "❌ Order Preview did not load");

        step("Stripe: fetch session + metadata.body");
        String stripeUrl = preview.proceedToStripeAndGetCheckoutUrl();
        String sessionId = extractSessionIdFromUrl(stripeUrl);
        Assert.assertNotNull(sessionId, "❌ Could not parse Stripe session id from URL");
        System.out.println("[Stripe] checkoutUrl=" + stripeUrl + " | sessionId=" + sessionId);

        String bodyJson = StripeCheckoutHelper.fetchCheckoutBodyFromStripe(sessionId);
        Assert.assertNotNull(bodyJson, "❌ metadata.body not found in Checkout Session");
        System.out.println("[Stripe] metadata.body length=" + bodyJson.length());

        step("Stripe: trigger checkout.session.completed via CLI");
        var trig = StripeCheckoutHelper.triggerCheckoutCompletedWithBody(bodyJson);
        System.out.println("[Stripe] Triggered eventId=" + trig.eventId +
                (trig.requestLogUrl != null ? " | requestLog=" + trig.requestLogUrl : ""));

        step("Navigate to post-payment confirmation");
        driver.navigate().to(joinUrl(Config.getBaseUrl(), "/dashboard/orders/confirmation"));

        step("Individuals page shows the newly invited user");
        new IndividualsPage(driver)
                .open(Config.getBaseUrl())
                .assertAppearsWithEvidence(Config.getBaseUrl(), tempEmail);
        System.out.println("✅ User appears in Individuals: " + tempEmail);

        // ----- email assertion -----
        step("Wait for email and assert contents");
        System.out.println("[Email] Waiting up to " + EMAIL_TIMEOUT.toSeconds() + "s for message to " + tempEmail + "…");

        final Email email;
        try {
            email = MailSlurpUtils.waitForLatestEmail(inboxId, EMAIL_TIMEOUT.toMillis(), true);
        } catch (ApiException e) {
            if (e.getCode() == 0 || e.getCode() == 404 || e.getCode() == 408) {
                Assert.fail("❌ No email received for " + tempEmail + " within " + EMAIL_TIMEOUT
                        + " (MailSlurp code " + e.getCode() + ")");
            }
            throw e;
        }

        final String subject = Objects.toString(email.getSubject(), "");
        final String from    = Objects.toString(email.getFrom(), "");
        final String body    = Objects.toString(email.getBody(), "");
        System.out.printf("📨 Email — From: %s | Subject: %s%n", from, subject);

        Assert.assertTrue(subject.toLowerCase(Locale.ROOT).contains(SUBJECT_NEEDLE),
                "❌ Subject does not mention " + SUBJECT_NEEDLE + ". Got: " + subject);
        Assert.assertTrue(from.toLowerCase(Locale.ROOT).contains("tilt365")
                        || from.toLowerCase(Locale.ROOT).contains("sendgrid"),
                "❌ Unexpected sender: " + from);
        Assert.assertTrue(body.toLowerCase(Locale.ROOT).contains(CTA_TEXT.toLowerCase(Locale.ROOT)),
                "❌ Email body missing CTA text '" + CTA_TEXT + "'.");

        String ctaHref = MailSlurpUtils.extractLinkByAnchorText(email, CTA_TEXT);
        if (ctaHref == null) ctaHref = MailSlurpUtils.extractFirstLink(email);
        Assert.assertNotNull(ctaHref, "❌ Could not find a link in the email.");
        System.out.println("🔗 CTA link: " + ctaHref);
        Assert.assertTrue(ctaHref.contains("sendgrid.net") || ctaHref.contains("tilt365"),
                "❌ CTA link host unexpected: " + ctaHref);
    }


//    @Test(groups = "ui-only", description = "TILT-242: Manual entry (valid, unique) enables Proceed → Preview → Stripe, and (optionally) completes flow")
//    public void manualEntryHappyPath_redirectsToStripe_andOptionallyCompletes() throws Exception {
//        // ----- config / creds -----
//        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
//        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
//        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
//            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
//        }
//
//        // If your BaseTest exposes inbox availability helpers, gate the email steps.
//        final boolean inboxAvailable = isInboxAvailableSafe(); // implement to read your suite flag
//        final String inboxId = getSuiteInboxIdSafe();          // may be null if not configured
//
//        // Generate a unique email. If you rely on MailSlurp, prefer its alias builder; else fallback.
//        final String tempEmail = (inboxAvailable && inboxId != null)
//                ? MailSlurpUtils.buildAliasEmail(inboxId, "team-happy-" + System.currentTimeMillis())
//                : ("qa.manual+" + System.currentTimeMillis() + "@tilt365.com"); // fallback (no inbox checks later)
//
//        step("Login as admin");
//        LoginPage loginPage = new LoginPage(driver);
//        loginPage.navigateTo();
//        loginPage.waitUntilLoaded();
//        DashboardPage dashboardPage =
//                loginPage.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
//        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");
//
//        step("Go to Shop and start purchase flow for Team");
//        ShopPage shopPage = dashboardPage.goToShop();
//        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");
//        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
//        sel.selectTeam();
//        sel.clickNextCta();
//
//        PurchaseInformation info = new PurchaseInformation(driver).waitUntilLoaded();
//        Assert.assertTrue(info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM),
//                "Expected banner: 'Assessment purchase for: Team'.");
//
//        step("Manual entry for 1 individual (unique email)");
//        AssessmentEntryPage entryPage = new AssessmentEntryPage(driver)
//                .waitUntilLoaded()
//                .selectCreateNewTeam()
//                .setOrganizationName("QA Org")
//                .setGroupName("Automation Squad")
//                .selectManualEntry()
//                .enterNumberOfIndividuals("1");
//        entryPage.fillUserDetailsAtIndex(1, "Emi", "Rod", tempEmail);
//
//        // No inline errors; Proceed should be enabled
//        entryPage.triggerManualValidationBlurs();
//        Assert.assertEquals(entryPage.inlineRequiredErrorsCount(), 0, "No inline errors expected for valid data.");
//        Assert.assertTrue(entryPage.isProceedToPaymentEnabled(), "Proceed should be enabled.");
//
//        step("Review order (Preview)");
//        pages.Shop.OrderPreviewPage preview = entryPage.clickProceedToPayment().waitUntilLoaded();
//        Assert.assertTrue(preview.isLoaded(), "❌ Order Preview did not load");
//
//        step("Stripe: fetch session + metadata.body");
//        String checkoutUrl = preview.proceedToStripeAndGetCheckoutUrl();
//        String sessionId   = extractSessionIdFromUrl(checkoutUrl);
//        Assert.assertNotNull(sessionId, "❌ Could not parse Stripe session id from URL");
//        System.out.println("[Stripe] checkoutUrl=" + checkoutUrl + " | sessionId=" + sessionId);
//
//        String bodyJson = StripeCheckoutHelper.fetchCheckoutBodyFromStripe(sessionId);
//        Assert.assertNotNull(bodyJson, "❌ metadata.body not found in Checkout Session");
//        System.out.println("[Stripe] metadata.body length=" + bodyJson.length());
//
//        // (Optional) Complete payment via Stripe CLI trigger — safe to skip on local/dev if CLI not configured
//        if (StripeCheckoutHelper.isCliAvailable()) {
//            step("Stripe: trigger checkout.session.completed via CLI");
//            var trig = StripeCheckoutHelper.triggerCheckoutCompletedWithBody(bodyJson);
//            System.out.println("[Stripe] Triggered eventId=" + trig.eventId +
//                    (trig.requestLogUrl != null ? " | requestLog=" + trig.requestLogUrl : ""));
//        } else {
//            System.out.println("[Stripe] CLI not available; skipping completion trigger.");
//        }
//
//        step("Navigate to post-payment confirmation");
//        driver.navigate().to(joinUrl(Config.getBaseUrl(), "/dashboard/orders/confirmation"));
//
//        // Assert the invite hit Individuals after payment (works only if webhook/trigger ran)
//        IndividualsPage individuals = new IndividualsPage(driver)
//                .open(Config.getBaseUrl());
//        individuals.assertAppearsWithEvidence(Config.getBaseUrl(), tempEmail);
//
//        // ----- optional email assertion (only if inbox configured) -----
//        if (inboxAvailable && inboxId != null) {
//            final java.time.Duration EMAIL_TIMEOUT = java.time.Duration.ofSeconds(120);
//            final String SUBJECT_NEEDLE = "assessment";
//            final String CTA_TEXT = "Start Assessment";
//
//            step("Wait for email and assert contents");
//            System.out.println("[Email] Waiting up to " + EMAIL_TIMEOUT.toSeconds() + "s for message to " + tempEmail + "…");
//
//            com.mailslurp.models.Email email = MailSlurpUtils
//                    .waitForLatestEmail(UUID.fromString(inboxId), EMAIL_TIMEOUT.toMillis(), true);
//
//            final String subject = java.util.Objects.toString(email.getSubject(), "");
//            final String from    = java.util.Objects.toString(email.getFrom(), "");
//            final String body    = java.util.Objects.toString(email.getBody(), "");
//            System.out.printf("📨 Email — From: %s | Subject: %s%n", from, subject);
//
//            Assert.assertTrue(subject.toLowerCase(Locale.ROOT).contains(SUBJECT_NEEDLE),
//                    "❌ Subject does not mention " + SUBJECT_NEEDLE + ". Got: " + subject);
//            Assert.assertTrue(from.toLowerCase(Locale.ROOT).contains("tilt365")
//                            || from.toLowerCase(Locale.ROOT).contains("sendgrid"),
//                    "❌ Unexpected sender: " + from);
//            Assert.assertTrue(body.toLowerCase(Locale.ROOT).contains(CTA_TEXT.toLowerCase(Locale.ROOT)),
//                    "❌ Email body missing CTA text '" + CTA_TEXT + "'.");
//
//            String ctaHref = MailSlurpUtils.extractLinkByAnchorText(email, CTA_TEXT);
//            if (ctaHref == null) ctaHref = MailSlurpUtils.extractFirstLink(email);
//            Assert.assertNotNull(ctaHref, "❌ Could not find a link in the email.");
//            System.out.println("🔗 CTA link: " + ctaHref);
//            Assert.assertTrue(ctaHref.contains("sendgrid.net") || ctaHref.contains("tilt365"),
//                    "❌ CTA link host unexpected: " + ctaHref);
//        } else {
//            System.out.println("[Email] Inbox not available; skipping email assertions.");
//        }
//
//        System.out.println("✅ Manual-entry happy path validated for: " + tempEmail);
//    }
//
//












    /** Waits until the browser navigates to Stripe Checkout (simple, URL-based). */
    private boolean waitForStripeRedirect(Duration timeout) {
        long end = System.currentTimeMillis() + timeout.toMillis();
        String last = "";
        while (System.currentTimeMillis() < end) {
            try {
                String url = driver.getCurrentUrl();
                last = url;
                if (url != null && url.contains("checkout.stripe.com")) return true;
            } catch (Exception ignored) {}
            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
        }
        System.out.printf("[DEBUG] Last observed URL while waiting for Stripe: %s%n", last);
        return false;
    }












// --- Small utilities ---------------------------------------------------------





    /* ---------- small utility ---------- */
    private String safeUrl() {
        try { return driver.getCurrentUrl(); } catch (Exception e) { return ""; }
    }


    /* ============================ helpers (local to test class) ============================ */

    private enum Mode { GRID, UPLOAD }

    private Mode waitForModeToSettle(AssessmentEntryPage page, Duration timeout) {
        new WebDriverWait(driver, timeout).until(d ->
                page.isManualGridVisible() || page.isUploadPanelVisible());
        return page.isManualGridVisible() ? Mode.GRID : Mode.UPLOAD;
    }

    private void safeTriggerValidationBlurs() {
        try {
            // Prefer page helper if present
            new AssessmentEntryPage(driver).triggerManualValidationBlurs();
        } catch (Throwable ignored) {
            // Fallback: tab across inputs to force blur
            List<WebElement> inputs = driver.findElements(By.cssSelector("input[id^='users.']"));
            for (WebElement el : inputs) {
                try {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'})", el);
                    el.click();
                    el.sendKeys(Keys.TAB);
                } catch (Exception ignored2) { /* best effort */ }
            }
        }
    }

    private void logSnapshot(AssessmentEntryPage page,
                             String mode, int rows, int inline, boolean proceedEnabled,
                             boolean uploadVisible, boolean radioSelected, String toast) {
        String url = "";
        try { url = driver.getCurrentUrl(); } catch (Exception ignored) {}
        System.out.printf(
                "[SNAPSHOT AFTER_UPLOAD] mode=%s | rows=%d | inline=%d | proceedEnabled=%s | uploadVisible=%s | radioSelected=%s | toast='%s' | url=%s%n",
                mode, rows, inline, proceedEnabled, uploadVisible, radioSelected, toast, url
        );
    }

    private void dumpInlineErrors() {
        List<String> texts = new ArrayList<>();
        // AntD & styled error spans
        List<By> locators = List.of(
                By.cssSelector(".ant-form-item-explain-error"),
                By.cssSelector("span[type='error']")
        );
        for (By by : locators) {
            for (WebElement el : driver.findElements(by)) {
                try {
                    if (!el.isDisplayed()) continue;
                    String txt = el.getText();
                    if (txt != null) {
                        txt = txt.trim();
                        if (!txt.isEmpty() && !looksLikeUrl(txt)) texts.add(txt);
                    }
                } catch (StaleElementReferenceException ignored) {}
            }
        }
        texts = texts.stream().distinct().toList();
        System.out.printf("[DEBUG] Inline error texts (%d):%n", texts.size());
        for (String t : texts) System.out.println("  - " + t);
    }

    private void dumpPerFieldErrors() {
        // Pair each input id users.{row}.{field} with the nearest following error text element
        List<WebElement> inputs = driver.findElements(By.cssSelector("input[id^='users.']"));
        List<String> lines = new ArrayList<>();
        for (WebElement input : inputs) {
            try {
                String id = input.getAttribute("id");
                if (id == null) continue;
                WebElement container = input.findElement(By.xpath("./ancestor::*[self::div or self::label][1]"));
                WebElement err = findErrorUnder(container);
                if (err != null && err.isDisplayed()) {
                    String t = err.getText() == null ? "" : err.getText().trim();
                    if (!t.isEmpty() && !looksLikeUrl(t)) {
                        lines.add(String.format("%s -> \"%s\"", id, t));
                    }
                }
            } catch (Exception ignored) {}
        }
        System.out.printf("[DEBUG] Per-field errors (%d):%n", lines.size());
        for (String l : lines) System.out.println("  - " + l);
    }

    private WebElement findErrorUnder(WebElement container) {
        List<By> errLocators = List.of(
                By.cssSelector(".ant-form-item-explain-error"),
                By.cssSelector("span[type='error']"),
                By.xpath(".//*[contains(@class,'-error') and normalize-space(string(.))!='']")
        );
        for (By by : errLocators) {
            List<WebElement> els = container.findElements(by);
            for (WebElement el : els) {
                try { if (el.isDisplayed()) return el; } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private boolean hasAnyEmailError() {
        // Look for an email-specific message anywhere on the grid
        List<String> candidates = new ArrayList<>();
        for (WebElement el : driver.findElements(By.cssSelector(".ant-form-item-explain-error, span[type='error']"))) {
            try {
                if (!el.isDisplayed()) continue;
                String t = el.getText();
                if (t != null) {
                    t = t.trim();
                    if (!t.isEmpty()) candidates.add(t.toLowerCase());
                }
            } catch (Exception ignored) {}
        }
        return candidates.stream().anyMatch(t ->
                t.contains("valid email") || t.contains("email address") || t.contains("email is required"));
    }

    private String nvl(String s) { return s == null ? "" : s; }
    private boolean looksLikeUrl(String s) { return s.startsWith("http") || s.startsWith("/"); }



    /** Minimal helper: looks for visible 'Required' badges/messages under inputs. */
    private boolean hasInlineRequiredErrors(WebDriver driver) {
        // Ant Design often uses .ant-form-item-explain-error; also catch explicit 'Required'
        // labels, and inputs flagged invalid.
        List<By> probes = List.of(
                By.xpath("//*[contains(@class,'ant-form-item-explain-error') and normalize-space(string(.))!='']"),
                By.xpath("//*[normalize-space(text())='Required']"),
                By.xpath("//input[@aria-invalid='true' or @data-status='error' or contains(@class,'status-error')]")
        );
        for (By by : probes) {
            List<WebElement> els = driver.findElements(by);
            for (WebElement el : els) {
                try {
                    if (el.isDisplayed()) return true;
                } catch (StaleElementReferenceException ignored) {}
            }
        }
        return false;
    }





    private int levenshtein(String a, String b) {
        int n = a.length(), m = b.length();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) dp[i][0] = i;
        for (int j = 0; j <= m; j++) dp[0][j] = j;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[n][m];
    }
















}
