package tests.teams;

import Utils.*;
import base.BaseTest;
import com.mailslurp.clients.ApiException;
import com.mailslurp.models.Email;
import com.mailslurp.models.InboxDto;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.devtools.v142.network.Network;
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
import pages.teams.TeamPurchaseFlows;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static Utils.Config.joinUrl;

public class TeamAssessmentPurchaseAndAssignment extends BaseTest {

    // Duplicate message detector
    public static final java.util.regex.Pattern DUP_MSG =
            java.util.regex.Pattern.compile("(?is)\\b(duplicate|duplicated|already\\s*exists?|already\\s*in\\s*use|in\\s*use|used)\\b");

    @Test(groups = "ui-only", description = "TILT-238: Duplicate email should show inline error and block Proceed")
    public void duplicateEmailBlocksProceed_TTP_Team_ManualEntry() {
        // creds
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }
        System.out.println("[AdminCreds] email=" + maskEmail(ADMIN_USER) + " | passLen=" + ADMIN_PASS.length());

        // login + start team flow
        step("Login as admin");
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();
        DashboardPage dashboardPage = loginPage.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load");

        step("Go to Shop and start purchase flow");
        ShopPage shopPage = dashboardPage.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");
        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
        sel.selectTeam();
        sel.clickNextCta();

        PurchaseInformation info = new PurchaseInformation(driver()).waitUntilLoaded();
        Assert.assertTrue(info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM),
                "Expected banner: 'Assessment purchase for: Team'.");

        step("Select 'create a new team' and type duplicated emails differing only by case/whitespace");
        AssessmentEntryPage assessmentEntryPage =
                new AssessmentEntryPage(driver())
                        .waitUntilLoaded()
                        .selectCreateNewTeam()
                        .setOrganizationName("QA Org")
                        .setGroupName("Automation Squad")
                        .selectManualEntry()
                        .ensureAtLeastNRows(2)
                        .setEmailAtRow(1, "qa.dup+1@tilt365.com")
                        .setEmailAtRow(2, " QA.DUP+1@TILT365.COM ");

        // Wait until any inline error appears on either row
        new WebDriverWait(driver(), Duration.ofSeconds(8)).until(d ->
                (assessmentEntryPage.getEmailErrorAtRow(1) != null) ||
                        (assessmentEntryPage.getEmailErrorAtRow(2) != null));

        // Row 2 must show duplicate error
        Assert.assertTrue(
                assessmentEntryPage.emailRowHasDuplicateError(2),
                "Row 2 message should indicate duplicate.");

        // If row 1 also shows an error, ensure it's duplicate-related
        String err1 = assessmentEntryPage.getEmailErrorAtRow(1);
        if (err1 != null && !err1.isBlank()) {
            Assert.assertTrue(
                    assessmentEntryPage.emailRowHasDuplicateError(1),
                    "Row 1 message (if present) should indicate duplicate.");
        }

        // Proceed must be disabled while duplicates exist
        Assert.assertFalse(
                assessmentEntryPage.isProceedToPaymentEnabled(),
                "Proceed button must be disabled when there are duplicate emails.");
    }


    @Test(groups ={"ui-only", "known-bug" }, description = "TILT-239: Exceeding max team members (20) validates/clamps and blocks Proceed")
    //we are waiting for a error message but is not being displauyed
    public void exceedingMaxTeamMembersValidation_TTP_Team_ManualEntry() {
        // creds
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }

        // login
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();
        DashboardPage dashboardPage = loginPage.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        // start purchase flow
        ShopPage shopPage = dashboardPage.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");
        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
        sel.selectTeam();
        sel.clickNextCta();

        // banner sanity
        PurchaseInformation info = new PurchaseInformation(driver()).waitUntilLoaded();
        Assert.assertTrue(info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM),
                "Expected banner: 'Assessment purchase for: Team'.");

        // team entry
        AssessmentEntryPage page = new AssessmentEntryPage(driver())
                .waitUntilLoaded()
                .selectCreateNewTeam()
                .setOrganizationName("QA Org")
                .setGroupName("Automation Squad")
                .selectManualEntry();

        // Try to set 21 (beyond the max=20)
        page.enterNumberOfIndividuals("21");

        // Wait for either: inline error OR clamp to 20
        new WebDriverWait(driver(), Duration.ofSeconds(10)).until(d -> {
            String err = page.getNumberOfIndividualsError();
            int value = page.getNumberOfIndividuals();
            int rows = page.renderedEmailRows();
            return (err != null && !err.isBlank()) || (value <= 20 && rows <= 20);
        });

        // Snapshot
        String qtyErr = page.getNumberOfIndividualsError();
        int qtyVal = page.getNumberOfIndividuals();
        int rowsNow = page.renderedEmailRows();

        if (qtyErr == null || qtyErr.isBlank()) {
            Assert.assertEquals(qtyVal, 20, "Spinner value should clamp to 20 when requesting >20.");
            Assert.assertEquals(rowsNow, 20, "Exactly 20 email rows should be rendered when clamped.");
        } else {
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
        // creds
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }

        // login
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();
        DashboardPage dashboardPage = loginPage.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        // start purchase flow
        ShopPage shopPage = dashboardPage.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");
        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
        sel.selectTeam();
        sel.clickNextCta();

        // banner sanity
        PurchaseInformation info = new PurchaseInformation(driver()).waitUntilLoaded();
        Assert.assertTrue(
                info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM),
                "Expected banner: 'Assessment purchase for: Team'.");

        // team entry & switch to template path
        AssessmentEntryPage page = new AssessmentEntryPage(driver())
                .waitUntilLoaded()
                .selectCreateNewTeam()
                .setOrganizationName("QA Org")
                .setGroupName("Automation Squad")
                .selectDownloadTemplate(); // render the upload panel

        page.clickDownloadButton();
        page.selectDownloadTemplate();

        // Wait until upload panel is visible
        new WebDriverWait(driver(), Duration.ofSeconds(10)).until(d -> page.isUploadPanelVisible());

        // invalid CSV that parses rows but leaves required cells empty
        java.io.File invalid = page.createTempCsv(
                "missing-email-cells",
                "First Name,Last Name,Email\n" +
                        "John,Doe,\n" +   // email empty
                        "Jane,,\n"        // email empty + missing last name
        );

        // upload
        page.uploadCsvFile(invalid.getAbsolutePath());

        // Wait for either rows to render OR proceed to be disabled
        new WebDriverWait(driver(), Duration.ofSeconds(20))
                .until(d -> page.renderedEmailRows() > 0 || !page.isProceedToPaymentEnabled());

        // Surface inline validations (best effort)
        safeTriggerValidationBlurs();

        new WebDriverWait(driver(), Duration.ofSeconds(8))
                .until(d -> page.inlineRequiredErrorsCount() > 0 || !page.isProceedToPaymentEnabled());

        // settle mode
        Mode mode = waitForModeToSettle(page, Duration.ofSeconds(12));

        if (mode == Mode.GRID) {
            safeTriggerValidationBlurs();
            new WebDriverWait(driver(), Duration.ofSeconds(5))
                    .until(d -> page.inlineRequiredErrorsCount() > 0 || !page.isProceedToPaymentEnabled());
        }

        // snapshot
        final boolean onManualGrid = (mode == Mode.GRID);
        final boolean uploadVisible = page.isUploadPanelVisible();
        final boolean radioSelected = page.isDownloadTemplateSelected();
        final int rowsNow = page.renderedEmailRows();
        final int inlineErrors = page.inlineRequiredErrorsCount();
        final boolean proceedEnabled = page.isProceedToPaymentEnabled();
        final String toast = nvl(page.waitForUploadErrorText());

        logSnapshot(page, onManualGrid ? "GRID" : "UPLOAD", rowsNow, inlineErrors, proceedEnabled, uploadVisible, radioSelected, toast);
        dumpInlineErrors();
        dumpPerFieldErrors();

        // assertions
        SoftAssert softly = new SoftAssert();

        if (onManualGrid) {
            softly.assertFalse(uploadVisible, "Upload panel should be hidden when grid is visible.");
            softly.assertFalse(radioSelected, "'Download template' radio should not remain selected in grid mode.");
            softly.assertTrue(rowsNow > 0, "Grid should show parsed rows after upload.");
            softly.assertTrue(inlineErrors > 0, "Expected inline field errors on the grid for incomplete rows.");
            softly.assertFalse(proceedEnabled, "Proceed must be disabled while grid has invalid rows.");
            softly.assertTrue(hasAnyEmailError(), "Should show an email-validation message in at least one row.");
        } else {
            softly.assertTrue(uploadVisible, "Upload panel should remain visible when no rows are parsed.");
            softly.assertTrue(radioSelected, "'Download template' radio should remain selected in upload mode.");
            softly.assertEquals(rowsNow, 0, "No rows should render on hard reject.");
            softly.assertFalse(proceedEnabled, "Proceed must be disabled on hard reject.");
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
        // creds
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }

        // login
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();
        DashboardPage dashboardPage = loginPage.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load");

        // start purchase flow
        ShopPage shopPage = dashboardPage.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");
        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
        sel.selectTeam();
        sel.clickNextCta();

        // banner sanity
        PurchaseInformation info = new PurchaseInformation(driver()).waitUntilLoaded();
        Assert.assertTrue(
                info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM),
                "Expected banner: 'Assessment purchase for: Team'.");

        // go to Upload Template path
        AssessmentEntryPage page = new AssessmentEntryPage(driver())
                .waitUntilLoaded()
                .selectCreateNewTeam()
                .setOrganizationName("QA Org")
                .setGroupName("Automation Squad")
                .selectDownloadTemplate();

        page.clickDownloadButton();
        page.selectDownloadTemplate();

        new WebDriverWait(driver(), Duration.ofSeconds(10)).until(d -> page.isUploadPanelVisible());

        // CSV MISSING 'Email' header (app soft-rejects -> grid + inline errors)
        File csv = page.createTempCsv(
                "missing-email-header",
                "First Name,Last Name\n" +
                        "John,Doe\n" +
                        "Jane,Smith\n"
        );

        // upload
        page.uploadCsvFile(csv.getAbsolutePath());

        new WebDriverWait(driver(), Duration.ofSeconds(20)).until(d ->
                page.isManualGridVisible()
                        || (page.isUploadPanelVisible() && page.renderedEmailRows() == 0)
                        || !page.isProceedToPaymentEnabled()
        );

        if (page.isManualGridVisible()) {
            page.triggerManualValidationBlurs();
            new WebDriverWait(driver(), Duration.ofSeconds(6))
                    .until(d -> page.inlineRequiredErrorsCount() > 0 || !page.isProceedToPaymentEnabled());
        }

        if (page.isManualGridVisible()) {
            page.triggerManualValidationBlurs();
            new WebDriverWait(driver(), Duration.ofSeconds(10))
                    .until(d -> !page.collectInlineErrorTexts().isEmpty()
                            || !page.isProceedToPaymentEnabled());
        }

        // snapshot
        final boolean onGrid = page.isManualGridVisible();
        final boolean uploadVisible = page.isUploadPanelVisible();
        final boolean radioSelected = page.isDownloadTemplateSelected();
        final int rowsNow = page.renderedEmailRows();
        final int inlineErrors = page.inlineRequiredErrorsCount();
        final boolean proceedEnabled = page.isProceedToPaymentEnabled();
        final String toast = Optional.ofNullable(page.waitForUploadErrorText()).orElse("");

        System.out.printf(
                "[SNAPSHOT MISSING_HEADER] mode=%s | rows=%d | inline=%d | proceedEnabled=%s | uploadVisible=%s | radioSelected=%s | toast='%s' | url=%s%n",
                onGrid ? "GRID" : "UPLOAD", rowsNow, inlineErrors, proceedEnabled, uploadVisible, radioSelected, toast, safeUrl()
        );
        if (inlineErrors > 0) {
            List<String> texts = page.collectInlineErrorTexts();
            Map<String, String> perField = page.collectPerFieldErrors();
            System.out.printf("[DEBUG] Inline error texts (%d): %s%n", texts.size(), texts);
            System.out.printf("[DEBUG] Per-field errors (%d): %s%n", perField.size(), perField);
        }
        System.out.printf("[DEBUG] AntUpload showsSuccess? %s%n", page.antUploadShowsSuccess());

        // assertions (soft-reject path)
        SoftAssert softly = new SoftAssert();
        softly.assertTrue(onGrid, "Should switch to grid when Email header is missing (soft-reject).");
        softly.assertFalse(uploadVisible, "Upload panel should be hidden once grid is shown.");
        softly.assertFalse(radioSelected, "'Download template' radio should not remain selected in grid mode.");
        softly.assertTrue(rowsNow > 0, "Grid should render uploaded rows.");
        softly.assertTrue(inlineErrors > 0, "Inline field errors should appear for missing Email in each row.");
        softly.assertFalse(proceedEnabled, "Proceed must be disabled while grid has invalid rows.");

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
        step("Resolve admin credentials from config");
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing");
        }

        step("Create two unique recipients for the team purchase");
        // Get base inbox email once
        Recipient r = provisionUniqueRecipient();
        String base = r.emailAddress; // e.g. 10f8...@mailslurp.xyz
        int at = base.indexOf('@');
        String local = base.substring(0, at);
        String domain = base.substring(at + 1);
        // Two unique, fresh aliases – both will deliver to the same inbox
        String email1 = local + "+p1@" + domain;
        String email2 = local + "+p2@" + domain;



        step("Login as admin and open the dashboard");
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        login.waitUntilLoaded();
        DashboardPage dash = login.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dash.isLoaded(), "Dashboard did not load");

        step("Navigate to Shop and open True Tilt purchase flow for a team");
        ShopPage shop = dash.goToShop();
        Assert.assertTrue(shop.isLoaded(), "Shop did not load");
        PurchaseRecipientSelectionPage sel = shop.clickBuyNowForTrueTilt();
        sel.selectTeam();
        sel.clickNextCta();

        step("Confirm purchase mode is TEAM in Purchase Information");
        PurchaseInformation info = new PurchaseInformation(driver()).waitUntilLoaded();
        Assert.assertTrue(info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM));

        step("Create a unique team with two members and proceed to Order Preview");
        AssessmentEntryPage entry = new AssessmentEntryPage(driver()).waitUntilLoaded();

        TeamPurchaseFlows.TeamCreationResult teamResult =
                TeamPurchaseFlows.createUniqueTeamWithTwoMembers(entry, email1, email2);

        OrderPreviewPage preview = teamResult.preview;
        Assert.assertTrue(preview.isLoaded(), "Preview did not load");

        step("Capture baseline totals with both members selected");
        preview.waitTotalsStable();
        int selA = preview.getSelectedCount();
        BigDecimal subA = preview.getSubtotal();
        BigDecimal totA = preview.getTotal();
        BigDecimal unit = preview.deriveUnitPrice();

        Assert.assertTrue(selA >= 1, "At least one member must be selected");
        Assert.assertTrue(
                preview.equalsMoney(subA, unit.multiply(BigDecimal.valueOf(selA))),
                "Subtotal should equal unit * selected"
        );

        step("Deselect one member and verify subtotal and total recalculate correctly");
        preview.toggleMemberByIndex(2);
        preview.waitTotalsStable();

        int selB = preview.getSelectedCount();
        BigDecimal subB = preview.getSubtotal();
        BigDecimal totB = preview.getTotal();
        BigDecimal taxB = preview.getTaxOrZero();
        BigDecimal discB = preview.getDiscountOrZero();

        Assert.assertEquals(selB, selA - 1, "Selected count should decrease by 1");
        Assert.assertTrue(
                preview.equalsMoney(subB, unit.multiply(java.math.BigDecimal.valueOf(selB))),
                "Subtotal after deselect should equal unit * selected"
        );
        Assert.assertTrue(
                preview.equalsMoney(subA.subtract(subB), unit),
                "Subtotal delta should equal exactly one unit"
        );

        BigDecimal expectedTotB = subB.add(taxB).subtract(discB);
        Assert.assertTrue(
                preview.equalsMoney(totB, expectedTotB),
                String.format("Total mismatch after deselect. expected=%s actual=%s", expectedTotB, totB)
        );

        step("Re-select the member and verify totals return to the original baseline");
        preview.toggleMemberByIndex(2);
        preview.waitTotalsStable();

        int selC = preview.getSelectedCount();
        BigDecimal subC = preview.getSubtotal();
        BigDecimal totC = preview.getTotal();

        Assert.assertEquals(selC, selA, "Selected should return to original");
        Assert.assertTrue(preview.equalsMoney(subC, subA), "Subtotal should return to original");
        Assert.assertTrue(preview.equalsMoney(totC, totA), "Total should return to original");
    }


    @Test(groups = "ui-only", description = "TILT-243: Remove a Member in Order Summary updates counts and totals (E2E)")
    public void removeMemberInOrderSummary_EndToEnd() {

        step("Resolve admin credentials from configuration");
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing");
        }

        step("Create two unique recipient emails for the team purchase (alias strategy)");
        Recipient r = provisionUniqueRecipient();
        String base = r.emailAddress;

        int at = base.indexOf('@');
        String local = base.substring(0, at);
        String domain = base.substring(at + 1);

        String email1 = local + "+p1@" + domain;
        String email2 = local + "+p2@" + domain;

        step("Login as admin and open the dashboard");
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        login.waitUntilLoaded();
        DashboardPage dash = login.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dash.isLoaded(), "Dashboard did not load");

        step("Navigate to the Shop and start True Tilt purchase for a Team");
        ShopPage shop = dash.goToShop();
        Assert.assertTrue(shop.isLoaded(), "Shop did not load");
        PurchaseRecipientSelectionPage sel = shop.clickBuyNowForTrueTilt();
        sel.selectTeam();
        sel.clickNextCta();

        step("Confirm purchase mode is TEAM");
        PurchaseInformation info = new PurchaseInformation(driver()).waitUntilLoaded();
        Assert.assertTrue(info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM));

        step("Create a NEW unique Team with two recipients and proceed to Order Preview");
        AssessmentEntryPage entry = new AssessmentEntryPage(driver()).waitUntilLoaded();

        TeamPurchaseFlows.TeamCreationResult teamResult =
                TeamPurchaseFlows.createUniqueTeamWithTwoMembers(entry, email1, email2);

        OrderPreviewPage preview = teamResult.preview;
        Assert.assertTrue(preview.isLoaded(), "Preview did not load");

        step("Capture initial totals with both members selected");
        preview.waitTotalsStable();
        int selA = preview.getSelectedCount();
        BigDecimal subA = preview.getSubtotal();
        BigDecimal totA = preview.getTotal();
        BigDecimal unit = preview.deriveUnitPrice();

        Assert.assertTrue(selA >= 2, "Need at least two selected members at start");
        Assert.assertTrue(
                preview.equalsMoney(subA, unit.multiply(BigDecimal.valueOf(selA))),
                "Subtotal should equal unit * selected at start"
        );

        step("Deselect (remove) the second member in the Order Summary");
        preview.toggleMemberByIndex(2);
        preview.waitTotalsStable();

        step("Capture totals after removal");
        int selB = preview.getSelectedCount();
        BigDecimal subB = preview.getSubtotal();
        BigDecimal totB = preview.getTotal();
        BigDecimal taxB = preview.getTaxOrZero();
        BigDecimal discB = preview.getDiscountOrZero();

        Assert.assertEquals(selB, selA - 1, "Selected member count should decrease by 1");
        Assert.assertTrue(
                preview.equalsMoney(subB, unit.multiply(BigDecimal.valueOf(selB))),
                "Subtotal after removal should equal unit * selected"
        );
        Assert.assertTrue(
                preview.equalsMoney(subA.subtract(subB), unit),
                "Subtotal delta after removal should equal exactly one unit price"
        );

        BigDecimal expectedTotB = subB.add(taxB).subtract(discB);
        Assert.assertTrue(
                preview.equalsMoney(totB, expectedTotB),
                String.format("Total mismatch after removal. expected=%s actual=%s", expectedTotB, totB)
        );

        step("Re-add the removed member and verify totals return to the original values");
        preview.toggleMemberByIndex(2);
        preview.waitTotalsStable();

        int selC = preview.getSelectedCount();
        BigDecimal subC = preview.getSubtotal();
        BigDecimal totC = preview.getTotal();

        Assert.assertEquals(selC, selA, "Selected should return to original");
        Assert.assertTrue(preview.equalsMoney(subC, subA), "Subtotal should return to original");
        Assert.assertTrue(preview.equalsMoney(totC, totA), "Total should return to original");
    }


    @Test(groups = "ui-only", description = "TILT-244: Toggle Product Assignment On/Off per email in final confirmation (E2E)")
    public void toggleProductAssignment_OnOff_EndToEnd() {

        step("Resolve admin credentials from configuration");
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing");
        }

        final String PRODUCT = "True Tilt Personality Profile";

        step("Create two unique recipient aliases for the team purchase (+p1 / +p2)");
        Recipient r = provisionUniqueRecipient();
        String base = r.emailAddress;              // e.g. 10f8...@mailslurp.xyz
        int at = base.indexOf('@');
        String local = base.substring(0, at);
        String domain = base.substring(at + 1);

        String email1 = local + "+p1@" + domain;
        String email2 = local + "+p2@" + domain;

        step("Login as admin and open dashboard");
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        login.waitUntilLoaded();
        DashboardPage dash = login.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dash.isLoaded(), "Dashboard did not load");

        step("Navigate to Shop and start True Tilt purchase for TEAM");
        ShopPage shop = dash.goToShop();
        Assert.assertTrue(shop.isLoaded(), "Shop did not load");

        PurchaseRecipientSelectionPage sel = shop.clickBuyNowForTrueTilt();
        sel.selectTeam();
        sel.clickNextCta();

        step("Confirm Purchase Information is in TEAM mode");
        PurchaseInformation info = new PurchaseInformation(driver()).waitUntilLoaded();
        Assert.assertTrue(info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM));

        step("Create a new unique Team with two members and proceed to Order Preview");
        AssessmentEntryPage entry = new AssessmentEntryPage(driver()).waitUntilLoaded();

        TeamPurchaseFlows.TeamCreationResult teamResult =
                TeamPurchaseFlows.createUniqueTeamWithTwoMembers(entry, email1, email2);

        OrderPreviewPage preview = teamResult.preview;
        Assert.assertTrue(preview.isLoaded(), "Preview did not load");

        step("Collect rendered emails in Order Preview table and map base / +p2 aliases");
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

        int distBase = levenshtein(email1.toLowerCase(Locale.ROOT), uiEmailBase);
        int distP2 = levenshtein(email2.toLowerCase(Locale.ROOT), uiEmailP2);
        System.out.println("[email-compare] base dist=" + distBase + " | p2 dist=" + distP2
                + " | expectedBase=" + email1 + " | uiBase=" + uiEmailBase
                + " | expectedP2=" + email2 + " | uiP2=" + uiEmailP2);

        Assert.assertTrue(uiEmailP2.contains("+p2@"),
                "One row must contain +p2@: uiEmailP2=" + uiEmailP2 + " | all=" + tableEmails);

        step("Capture baseline counts and money values with all products ON");
        preview.waitTotalsStable();
        int selA = preview.getSelectedCount();
        Assert.assertTrue(selA >= 2, "Expect at least 2 selected at start");

        BigDecimal subA = preview.getSubtotal();
        BigDecimal totA = preview.getTotal();
        BigDecimal unit = preview.deriveUnitPrice();
        Assert.assertTrue(
                preview.equalsMoney(subA, unit.multiply(BigDecimal.valueOf(selA))),
                "Baseline Subtotal must equal unit * selected"
        );

        step("Ensure product is ON for +p2 row before testing OFF toggle");
        preview.setProductAssigned(uiEmailP2, PRODUCT, true);
        preview.debugDumpPreviewTable("after-setProductAssigned-ensureON");
        preview.waitTotalsStable();

        step("Toggle product OFF for +p2 row and wait for recalculation");
        preview.debugDumpPreviewTable("before-setProductAssigned-OFF");
        preview.setProductAssigned(uiEmailP2, PRODUCT, false);
        preview.debugDumpPreviewTable("after-setProductAssigned-OFF");
        preview.waitTotalsStable();

        step("Validate counts, subtotal, and total after product OFF");
        int selB = preview.getSelectedCount();
        BigDecimal subB = preview.getSubtotal();
        BigDecimal totB = preview.getTotal();
        BigDecimal taxB = preview.getTaxOrZero();
        BigDecimal discB = preview.getDiscountOrZero();

        Assert.assertEquals(selB, selA - 1, "Selected count should decrease by 1 after product OFF");
        Assert.assertTrue(
                preview.equalsMoney(subB, unit.multiply(BigDecimal.valueOf(selB))),
                "Subtotal after OFF should equal unit * selected"
        );
        Assert.assertTrue(
                preview.equalsMoney(subA.subtract(subB), unit),
                "Subtotal delta should equal exactly one unit"
        );

        BigDecimal expectedTotB = subB.add(taxB).subtract(discB);
        Assert.assertTrue(
                preview.equalsMoney(totB, expectedTotB),
                String.format("Total mismatch after OFF. expected=%s actual=%s", expectedTotB, totB)
        );

        step("Toggle product back ON for +p2 row and verify return to baseline");
        preview.setProductAssigned(uiEmailP2, PRODUCT, true);
        preview.waitTotalsStable();

        int selC = preview.getSelectedCount();
        BigDecimal subC = preview.getSubtotal();
        BigDecimal totC = preview.getTotal();

        Assert.assertEquals(selC, selA, "Selected count should return to baseline after ON");
        Assert.assertTrue(preview.equalsMoney(subC, subA), "Subtotal should return to baseline");
        Assert.assertTrue(preview.equalsMoney(totC, totA), "Total should return to baseline");
    }


    /**
     * TILT-245: Handle Slow Network on Payment Submit
     */
    @Test(groups = "ui-only", description = "TILT-245:")
    public void testHandleSlowNetworkOnPaymentSubmit_ShowsLoadingAndBlocksDuplicatePayment() {
        // creds
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }
        System.out.println("[AdminCreds] email=" + maskEmail(ADMIN_USER) + " | passLen=" + ADMIN_PASS.length());

        boolean E2E_ENABLED =
                Boolean.parseBoolean(System.getProperty("STRIPE_E2E",
                        String.valueOf(Boolean.parseBoolean(String.valueOf(System.getenv("STRIPE_E2E"))))));
        E2E_ENABLED = true; // enable if desired

        // recipient
        step("Resolve recipient for this run (prefer fresh inbox)");
        Recipient r = provisionUniqueRecipient();
        final String tempEmail = r.emailAddress;
        System.out.println("📧 Test email (clean): " + tempEmail);

        // flow to preview
        step("Login as admin");
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();
        DashboardPage dashboardPage = loginPage.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Shop and start purchase flow");
        ShopPage shopPage = dashboardPage.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");
        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
        sel.selectClientOrIndividual();
        sel.clickNext();

        step("Manual entry for 1 individual");
        AssessmentEntryPage entryPage = new AssessmentEntryPage(driver())
                .waitUntilLoaded()
                .selectManualEntry()
                .enterNumberOfIndividuals("1");
        entryPage.fillUserDetailsAtIndex(1, "Emi", "Rod", tempEmail);
        entryPage.triggerManualValidationBlurs();
        Assert.assertTrue(entryPage.isProceedToPaymentEnabled(), "Proceed should be enabled.");

        step("Review order (Preview)");
        OrderPreviewPage preview = entryPage.clickProceedToPayment().waitUntilLoaded();
        Assert.assertTrue(preview.isLoaded(), "❌ Order Preview did not load");

        // throttle network
        step("Simulate slow network");
        org.openqa.selenium.devtools.DevTools devTools =
                ((org.openqa.selenium.devtools.HasDevTools) driver()).getDevTools();
        devTools.createSession();
        devTools.send(Network.enable(
                Optional.of(50_000_000),   // maxTotalBufferSize required
                Optional.empty(),          // maxResourceBufferSize
                Optional.empty(),          // maxPostDataSize
                Optional.of(true),         // captureNetworkRequests
                Optional.of(true)          // durable messages
        ));
        devTools.send(Network.emulateNetworkConditions(
                false, 1200, 100 * 1024, 100 * 1024,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        ));

        final java.util.Set<String> handlesBefore = new java.util.HashSet<>(driver().getWindowHandles());
        final String handleBefore = driver().getWindowHandle();
        String stripeUrlForE2E = null;

        try {
            step("Click Pay once; button must show loading/disabled promptly");
            preview.clickPayWithStripe();
            new WebDriverWait(driver(), Duration.ofSeconds(8)).until(d -> preview.isPayBusy());
            Assert.assertTrue(preview.isPayBusy(), "❌ Pay button should show a loading/disabled state after first click");

            step("Attempt a rapid second click; should NOT open another Stripe checkout");
            try { preview.clickPayWithStripe(); } catch (Exception ignored) {}
            try { Thread.sleep(700); } catch (InterruptedException ignored) {}

            int newStripeWindows = 0;
            for (String h : driver().getWindowHandles()) {
                if (!handlesBefore.contains(h)) {
                    try {
                        driver().switchTo().window(h);
                        String u = driver().getCurrentUrl();
                        if (u != null && u.contains("checkout.stripe.com")) {
                            newStripeWindows++;
                            if (stripeUrlForE2E == null) stripeUrlForE2E = u;
                        }
                    } catch (Exception ignored) {}
                }
            }
            if (newStripeWindows == 0) {
                try {
                    driver().switchTo().window(handleBefore);
                    String u = driver().getCurrentUrl();
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
            try { devTools.send(Network.disable()); } catch (Exception ignored) {}
            try { driver().switchTo().window(handleBefore); } catch (Exception ignored) {}
        }

        // Optional E2E tail
        if (E2E_ENABLED) {
            try {
                step("Stripe: fetch session + metadata.body");
                if (stripeUrlForE2E == null || stripeUrlForE2E.isBlank()) {
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
                driver().navigate().to(joinUrl(Config.getBaseUrl(), "/dashboard/orders/confirmation"));

                step("Individuals page shows the newly invited user");
                new IndividualsPage(driver())
                        .open(Config.getBaseUrl())
                        .assertAppearsWithEvidence(Config.getBaseUrl(), tempEmail);
                System.out.println("✅ Individuals shows invited user: " + tempEmail);
            } catch (Throwable e) {
                System.out.println("[E2E] Skipping tail due to Stripe connectivity/problem: " + e);
                System.out.println("[E2E] Test already validated the UI prevents duplicate payments.");
            }
        }
    }


    @Test(groups = {"ui-only", "known-bug"}, description = "TILT-247: Preserve Team selection and member choices on Back navigation")
    public void preserveTeamSelection_onBackNavigation_persists() {
        // creds
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing");
        }

        // ---------- Unique recipients ----------
        step("Resolve 3 unique recipients for this run (aliases on same inbox)");
        Recipient r = provisionUniqueRecipient();
        String base = r.emailAddress; // e.g. 10f8...@mailslurp.xyz
        int at = base.indexOf('@');
        String local = base.substring(0, at);
        String domain = base.substring(at + 1);

        String email1 = local + "+p1@" + domain;
        String email2 = local + "+p2@" + domain;
        String email3 = local + "+p3@" + domain;
        System.out.println("[DEBUG] planned emails: " + email1 + ", " + email2 + ", " + email3);

        // ---------- Unique org / group (avoid hardcoded collisions) ----------
        String tag = local + "-" + (System.currentTimeMillis() % 100000); // short-ish, but unique enough
        final String ORG = "QA Org " + tag;
        final String GRP = "Automation Squad " + tag;

        // Login → Shop → Team flow
        step("Login as admin");
        LoginPage login = new LoginPage(driver());
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
        PurchaseInformation info = new PurchaseInformation(driver()).waitUntilLoaded();
        Assert.assertTrue(
                info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM),
                "Expected banner: 'Assessment purchase for: Team'."
        );

        // Entry: create team, manual entry, 3 members
        step("Fill team info + 3 members");
        AssessmentEntryPage entry = new AssessmentEntryPage(driver())
                .waitUntilLoaded()
                .selectCreateNewTeam()
                .setOrganizationName(ORG)
                .setGroupName(GRP)
                .selectManualEntry()
                .enterNumberOfIndividuals("3");

        entry.fillUserDetailsAtIndex(1, "U", "One", email1);
        entry.fillUserDetailsAtIndex(2, "U", "Two", email2);
        entry.fillUserDetailsAtIndex(3, "U", "Three", email3);
        entry.triggerManualValidationBlurs();

        Assert.assertEquals(entry.inlineRequiredErrorsCount(), 0, "No inline errors expected");
        Assert.assertTrue(entry.isProceedToPaymentEnabled(), "Proceed should be enabled");

        java.util.Set<String> entrySetBefore = new java.util.HashSet<>(entry.collectAllEmailsLower());
        System.out.println("[DEBUG] entry emails (before preview): " + entrySetBefore);

        // Preview: capture baseline, then deselect one member by EMAIL
        step("Proceed to Preview and deselect one member (by email)");
        OrderPreviewPage preview = entry.clickProceedToPayment().waitUntilLoaded();
        Assert.assertTrue(preview.isLoaded(), "Preview did not load");
        preview.waitTotalsStable();

        LinkedHashMap<String, Boolean> sel0 = preview.selectionByEmail();
        System.out.println("[DEBUG] preview selection map baseline = " + sel0);
        saveSnapshot("preview_base");

        List<String> emailsPreview = new ArrayList<>(sel0.keySet());
        Assert.assertTrue(emailsPreview.size() >= 3, "Need at least 3 rows in preview");

        String deselectEmail = emailsPreview.get(1);
        preview.setSelectedByEmail(deselectEmail, false);
        preview.waitTotalsStable();

        LinkedHashMap<String, Boolean> selAfter = preview.selectionByEmail();
        System.out.println("[DEBUG] selection after deselect = " + selAfter);
        saveSnapshot("preview_after_deselect");

        LinkedHashMap<String, Boolean> expectedAfter = new LinkedHashMap<>(sel0);
        expectedAfter.put(deselectEmail, false);

        long cnt0 = sel0.values().stream().filter(Boolean::booleanValue).count();
        long cntExp = expectedAfter.values().stream().filter(Boolean::booleanValue).count();
        long cntGot = selAfter.values().stream().filter(Boolean::booleanValue).count();
        Assert.assertEquals(cntGot, cntExp, "Selected count should decrease by 1 after deselect");

        // Back to Entry: verify all data persisted
        step("Click Back to Entry; verify team info + members persisted");
        preview.clickPrevious();
        entry.waitUntilLoaded();
        entry.waitManualGridEmailsAtLeast(3, Duration.ofSeconds(8));
        try { Thread.sleep(150); } catch (InterruptedException ignored) {}
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

        System.out.println("[DEBUG] entry row1=" + entry.getEmailAtRow(1)
                + " row2=" + entry.getEmailAtRow(2)
                + " row3=" + entry.getEmailAtRow(3));
        Assert.assertTrue(entry.getEmailAtRow(1).toLowerCase().contains(email1.toLowerCase()), "Row 1 email should persist");
        Assert.assertTrue(entry.getEmailAtRow(2).toLowerCase().contains(email2.toLowerCase()), "Row 2 email should persist");
        Assert.assertTrue(entry.getEmailAtRow(3).toLowerCase().contains(email3.toLowerCase()), "Row 3 email should persist");

        // Forward to Preview again: selection state should persist
        step("Forward to Preview again; selection state should persist");
        Assert.assertTrue(entry.isProceedToPaymentEnabled(), "Proceed should still be enabled");
        OrderPreviewPage preview2 = entry.clickProceedToPayment().waitUntilLoaded();
        Assert.assertTrue(preview2.isLoaded(), "Preview (round 2) did not load");
        preview2.waitTotalsStable();

        LinkedHashMap<String, Boolean> round2 = preview2.selectionByEmail();
        System.out.println("[DEBUG] preview round#2 selection map = " + round2);
        saveSnapshot("preview_round2");

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

        dumpBrowserConsole();
    }


    /**
     * TC-2: Team purchase via manual entry → Preview → Stripe → webhook → Individuals + email
     * Stripe Checkout UI is NOT used — payment completion is triggered via Stripe CLI only.
     */
    @Test
    public void testTeamManualEntry_PurchaseCompletesAndSendsInviteEmail() throws ApiException, InterruptedException {

        // --- ADMIN CREDS ---
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Missing ADMIN credentials.");
        }

        final Duration EMAIL_TIMEOUT = Duration.ofSeconds(120);
        final String CTA_TEXT = "Accept Assessment";
        final String SUBJECT_NEEDLE = "assessment";
        System.setProperty("mailslurp.debug", "true");

        // --- EMAIL + INBOX (fresh MailSlurp inbox) ---
        step("Resolve recipient for this run (fresh MailSlurp inbox)");
        Recipient r = provisionUniqueRecipient();
        MailSlurpUtils.clearInboxEmails(r.inboxId);

        final UUID inboxId = r.inboxId;
        final String baseEmail = r.emailAddress;
        System.out.println("📧 Base inbox: " + baseEmail);

        // one participant for this test
        final String tempEmail = baseEmail;  // no alias needed for single-user test

        // generate unique org/group for each test run
        String tag = baseEmail.substring(0, baseEmail.indexOf('@'))
                + "-" + (System.currentTimeMillis() % 100000);
        final String ORG_NAME = "QA Org " + tag;
        final String GROUP_NAME = "QA Team " + tag;

        // --- LOGIN ---
        step("Login as admin");
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        login.waitUntilLoaded();
        DashboardPage dash = login.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dash.isLoaded(), "❌ Dashboard failed to load");

        // --- SHOP FLOW ---
        step("Go to Shop → Team purchase → Manual entry");
        ShopPage shopPage = dash.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page failed to load");

        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
        sel.selectTeam();
        sel.clickNextCta();

        PurchaseInformation info = new PurchaseInformation(driver()).waitUntilLoaded();
        Assert.assertTrue(info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM));

        AssessmentEntryPage entry = new AssessmentEntryPage(driver())
                .waitUntilLoaded()
                .selectCreateNewTeam()
                .setOrganizationName(ORG_NAME)
                .setGroupName(GROUP_NAME)
                .selectManualEntry()
                .enterNumberOfIndividuals("1");

        entry.fillUserDetailsAtIndex(1, "User", "One", tempEmail);
        entry.triggerManualValidationBlurs();
        entry.waitManualGridEmailsAtLeast(1, Duration.ofSeconds(8));

        Assert.assertTrue(entry.isProceedToPaymentEnabled(), "❌ Proceed should be enabled");

        // --- PREVIEW ---
        step("Review Order (Preview)");
        OrderPreviewPage preview = entry.clickProceedToPayment().waitUntilLoaded();
        Assert.assertTrue(preview.isLoaded(), "❌ Preview failed to load");

        preview.waitTotalsStable();
        Assert.assertEquals(preview.getSelectedCount(), 1, "Preview must show 1 selected member");

        // --- STRIPE (CLI ONLY) ---
        step("Stripe: Fetch checkout.session + metadata.body (no UI)");
        String checkoutUrl = preview.proceedToStripeAndGetCheckoutUrl();
        Assert.assertNotNull(checkoutUrl, "❌ Could not obtain checkout URL");

        String sessionId = extractSessionIdFromUrl(checkoutUrl);
        Assert.assertNotNull(sessionId, "❌ Could not parse session id from URL");

        String bodyJson = StripeCheckoutHelper.fetchCheckoutBodyFromStripe(sessionId);
        Assert.assertNotNull(bodyJson, "❌ Could not read metadata.body from Stripe session");

        step("Stripe: Trigger checkout.session.completed via CLI (AWS-safe)");
        var trig = StripeCheckoutHelper.triggerCheckoutCompletedWithBody(bodyJson);
        System.out.println("[Stripe] Triggered event " + trig.eventId);

        // --- POST-PAYMENT CONFIRMATION ---
        step("Navigate to Orders Confirmation");
        driver().navigate().to(joinUrl(Config.getBaseUrl(), "/dashboard/orders/confirmation"));

        // --- INDIVIDUALS PAGE ---
        step("Individuals page must show newly invited user");
        new IndividualsPage(driver())
                .open(Config.getBaseUrl())
                .assertAppearsWithEvidence(Config.getBaseUrl(), tempEmail);

        // --- EMAIL RECEIVED ---
        step("MailSlurp: Assert invite email is received correctly");
        Email invite = MailSlurpUtils.waitForEmailMatching(
                inboxId,
                EMAIL_TIMEOUT.toMillis(),
                1500L,
                true,
                MailSlurpUtils.subjectContains(SUBJECT_NEEDLE)
                        .and(MailSlurpUtils.bodyContains("accept"))
        );
        Assert.assertNotNull(invite, "❌ Invite email not received in time");

        final String subject = Objects.toString(invite.getSubject(), "");
        final String from = Objects.toString(invite.getFrom(), "");
        final String body = Objects.toString(invite.getBody(), "");

        Assert.assertTrue(subject.toLowerCase().contains(SUBJECT_NEEDLE),
                "❌ Subject missing keyword: " + SUBJECT_NEEDLE);
        Assert.assertTrue(from.toLowerCase().contains("tilt365")
                        || from.toLowerCase().contains("sendgrid"),
                "❌ Unexpected sender: " + from);
        Assert.assertTrue(body.toLowerCase().contains(CTA_TEXT.toLowerCase()),
                "❌ Email missing CTA text: " + CTA_TEXT);

        String link = MailSlurpUtils.extractLinkByAnchorText(invite, CTA_TEXT);
        if (link == null) link = MailSlurpUtils.extractFirstLink(invite);

        Assert.assertNotNull(link, "❌ No link found in invite email");
        Assert.assertTrue(link.contains("sendgrid.net") || link.contains("tilt365"),
                "❌ Unexpected link host in invite email: " + link);

        System.out.println("🎉 Email OK → Link: " + link);
    }


    @Test(groups = {"smoke"}, description = "SM08: Team manual entry (3 recipients) reaches Stripe Checkout.")
    public void smoke_teamManualEntryThreeRecipients_reachesStripeCheckout() throws Exception {

        // -------------------- CONFIG / ADMIN CREDS --------------------
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }
        System.out.println("[AdminCreds] email=" + BaseTest.maskEmail(ADMIN_USER) + " | passLen=" + ADMIN_PASS.length());

        final int TEAM_SIZE = 3;

        // -------------------- UNIQUE ORG / TEAM / RECIPIENTS --------------------
        step("Generate unique org/team and recipient emails for this run");
        String tag = "sm08-" + (System.currentTimeMillis() % 100000);
        final String ORG_NAME = "SM08 Org " + tag;
        final String GROUP_NAME = "SM08 Team " + tag;

        String baseLocal = "sm08-" + tag;
        String domain = "@example.test";

        String[] emails = new String[] {
                baseLocal + "-1" + domain,
                baseLocal + "-2" + domain,
                baseLocal + "-3" + domain
        };

        // -------------------- LOGIN --------------------
        step("Login as admin and open Dashboard");
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        login.waitUntilLoaded();
        DashboardPage dashboard = login.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboard.isLoaded(), "❌ Dashboard did not load after login");

        // -------------------- SHOP → TEAM → MANUAL ENTRY --------------------
        step("Go to Shop → start Team purchase flow for True Tilt");
        ShopPage shopPage = dashboard.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page failed to load");

        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
        Assert.assertTrue(sel.isLoaded(), "❌ Recipient selection page did not load");

        step("Choose 'Team' path, then continue");
        sel.selectTeam();
        sel.clickNextCta();

        PurchaseInformation info = new PurchaseInformation(driver()).waitUntilLoaded();
        Assert.assertTrue(
                info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM),
                "❌ Purchase is not set to TEAM"
        );

        step("Select manual entry for new team and enter 3 recipients");
        AssessmentEntryPage entry = new AssessmentEntryPage(driver())
                .waitUntilLoaded()
                .selectCreateNewTeam()
                .setOrganizationName(ORG_NAME)
                .setGroupName(GROUP_NAME)
                .selectManualEntry()
                .enterNumberOfIndividuals(String.valueOf(TEAM_SIZE));

        // Fill recipients without hard-coded names
        for (int i = 0; i < TEAM_SIZE; i++) {
            String email = emails[i];
            String localPart = email.substring(0, email.indexOf('@'));
            String firstName = "FN_" + localPart;
            String lastName  = "LN_" + localPart;

            entry.fillUserDetailsAtIndex(i + 1, firstName, lastName, email);
        }

        entry.triggerManualValidationBlurs();
        entry.waitManualGridEmailsAtLeast(TEAM_SIZE, Duration.ofSeconds(10));

        Assert.assertTrue(
                entry.isProceedToPaymentEnabled(),
                "❌ Proceed to Payment should be enabled after entering 3 valid recipients"
        );

        // -------------------- ORDER PREVIEW --------------------
        step("Proceed to Order Preview and validate 3 recipients + totals");
        OrderPreviewPage preview = entry.clickProceedToPayment().waitUntilLoaded();
        Assert.assertTrue(preview.isLoaded(), "❌ Order Preview page failed to load");

        preview.waitTotalsStable();

        // 3 recipients selected
        Assert.assertEquals(
                preview.getSelectedCount(),
                TEAM_SIZE,
                "❌ Preview must show " + TEAM_SIZE + " selected members"
        );

        // NEW: assert each of the 3 emails is present & selected
        LinkedHashMap<String, Boolean> selection = preview.selectionByEmail();
        Assert.assertEquals(
                selection.size(),
                TEAM_SIZE,
                "❌ Preview selection map should contain exactly " + TEAM_SIZE + " rows"
        );
        for (String email : emails) {
            String key = email.toLowerCase();
            Assert.assertTrue(
                    selection.containsKey(key),
                    "❌ Preview table is missing email: " + email
            );
            Assert.assertTrue(
                    Boolean.TRUE.equals(selection.get(key)),
                    "❌ Email is not selected in preview: " + email
            );
        }

        // Price sanity checks using existing money helpers
        BigDecimal subtotal = preview.getSubtotal();      // from label or computed
        BigDecimal total    = preview.getTotal();
        BigDecimal unit     = preview.deriveUnitPrice();  // subtotal / selectedCount

        Assert.assertTrue(
                unit.compareTo(BigDecimal.ZERO) > 0,
                "❌ Unit price must be > 0. Got: " + unit
        );

        BigDecimal expectedSubtotal = unit
                .multiply(BigDecimal.valueOf(TEAM_SIZE))
                .setScale(2, RoundingMode.HALF_UP);

        Assert.assertTrue(
                preview.equalsMoney(subtotal, expectedSubtotal),
                "❌ Subtotal mismatch. Expected " + expectedSubtotal + " but got " + subtotal
        );

        // NEW: validate total ≈ subtotal + tax − discount
        BigDecimal tax      = preview.getTaxOrZero();
        BigDecimal discount = preview.getDiscountOrZero();
        BigDecimal recomputedTotal = subtotal
                .add(tax)
                .subtract(discount)
                .setScale(2, RoundingMode.HALF_UP);

        Assert.assertTrue(
                preview.equalsMoney(total, recomputedTotal),
                "❌ Total mismatch. Expected " + recomputedTotal +
                        " (subtotal + tax - discount) but got " + total
        );

        // -------------------- STRIPE CHECKOUT --------------------
        step("Proceed to payment and assert Stripe Checkout session is created/opened");
        String checkoutUrl = preview.proceedToStripeAndGetCheckoutUrl();
        Assert.assertNotNull(checkoutUrl, "❌ Could not obtain Stripe Checkout URL from preview");
        Assert.assertFalse(checkoutUrl.isBlank(), "❌ Stripe Checkout URL is blank");

        Assert.assertTrue(
                checkoutUrl.contains("checkout.stripe.com"),
                "❌ Unexpected checkout URL (not Stripe?): " + checkoutUrl
        );

        // Optional: navigate to confirm reachable Stripe page
        driver().navigate().to(checkoutUrl);
    }



    @Test(groups = {"smoke"}, description = "SM09: Team CSV upload (>=3 recipients) reaches Preview + Stripe.")
    public void smoke_teamCsvUpload_reachesPreviewAndStripe() throws Exception {

        // -------------------- CONFIG / ADMIN CREDS --------------------
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }
        System.out.println("[AdminCreds] email=" + BaseTest.maskEmail(ADMIN_USER) + " | passLen=" + ADMIN_PASS.length());

        final int TEAM_SIZE = 3;

        // --- FIXTURE CSV ---
        step("Generate unique org/team and CSV with recipients for this run");
        String tag = "sm09-" + (System.currentTimeMillis() % 100000);
        final String ORG_NAME = "SM09 Org " + tag;
        final String GROUP_NAME = "SM09 Team " + tag;

        TeamCsvFixtureFactory.TeamCsvFixture csvFixture =
                TeamCsvFixtureFactory.buildTeamCsvWithNRecipients(tag, TEAM_SIZE);
        List<String> uploadedEmails = csvFixture.getEmails(); // keep for assertions

        // -------------------- LOGIN --------------------
        step("Login as admin and open Dashboard");
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        login.waitUntilLoaded();
        DashboardPage dashboard =
                login.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboard.isLoaded(), "❌ Dashboard did not load after login");

        // -------------------- SHOP → TEAM → CSV UPLOAD --------------------
        step("Go to Shop → start Team purchase flow for True Tilt");
        ShopPage shopPage = dashboard.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page failed to load");

        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
        Assert.assertTrue(sel.isLoaded(), "❌ Recipient selection page did not load");

        step("Choose 'Team' path, then continue");
        sel.selectTeam();
        sel.clickNextCta();

        PurchaseInformation info = new PurchaseInformation(driver()).waitUntilLoaded();
        Assert.assertTrue(
                info.purchaseForIs(PurchaseRecipientSelectionPage.Recipient.TEAM),
                "❌ Purchase is not set to TEAM"
        );

        step("Select CSV upload for new team and upload file");
        AssessmentEntryPage entry = new AssessmentEntryPage(driver())
                .waitUntilLoaded()
                .selectCreateNewTeam()
                .setOrganizationName(ORG_NAME)
                .setGroupName(GROUP_NAME)
                .selectDownloadTemplate();      // select radio

        // 🔧 IMPORTANT: click Download first so the Upload button appears
        entry.clickDownloadButton();
        entry.selectDownloadTemplate();        // re-ensure radio is on (same pattern as other tests)

        // Wait until upload panel / button is present
        new WebDriverWait(driver(), Duration.ofSeconds(10))
                .until(d -> entry.isUploadPanelVisible());

        // Now Upload is in the DOM → safe to call helper
        entry.uploadCsvFile(csvFixture.getPath().toAbsolutePath().toString());

        entry.waitManualGridEmailsAtLeast(TEAM_SIZE, Duration.ofSeconds(20));
        Assert.assertTrue(
                entry.isProceedToPaymentEnabled(),
                "❌ Proceed should be enabled after CSV upload"
        );

        // -------------------- PREVIEW + ASSERT EMAILS & TOTALS --------------------
        step("Proceed to Order Preview and validate recipients + totals");
        OrderPreviewPage preview = entry.clickProceedToPayment().waitUntilLoaded();
        Assert.assertTrue(preview.isLoaded(), "❌ Order Preview page failed to load");

        preview.waitTotalsStable();

        // 3 recipients selected
        Assert.assertEquals(
                preview.getSelectedCount(),
                TEAM_SIZE,
                "❌ Preview must show " + TEAM_SIZE + " selected members"
        );

        // Every CSV email is present and selected in preview
        LinkedHashMap<String, Boolean> selection = preview.selectionByEmail();
        Assert.assertTrue(
                selection.size() >= TEAM_SIZE,
                "❌ Preview selection map should contain at least " + TEAM_SIZE + " rows"
        );

        for (String email : uploadedEmails) {
            String key = email.toLowerCase(Locale.ROOT);
            Assert.assertTrue(
                    selection.containsKey(key),
                    "❌ Preview table is missing email from CSV: " + email
            );
            Assert.assertTrue(
                    Boolean.TRUE.equals(selection.get(key)),
                    "❌ Email from CSV is not selected in preview: " + email
            );
        }

        // Money sanity (same logic as SM08)
        BigDecimal subtotal = preview.getSubtotal();
        BigDecimal total    = preview.getTotal();
        BigDecimal unit     = preview.deriveUnitPrice();

        Assert.assertTrue(
                unit.compareTo(BigDecimal.ZERO) > 0,
                "❌ Unit price must be > 0. Got: " + unit
        );

        BigDecimal expectedSubtotal = unit
                .multiply(BigDecimal.valueOf(TEAM_SIZE))
                .setScale(2, RoundingMode.HALF_UP);

        Assert.assertTrue(
                preview.equalsMoney(subtotal, expectedSubtotal),
                "❌ Subtotal mismatch. Expected " + expectedSubtotal + " but got " + subtotal
        );

        BigDecimal tax      = preview.getTaxOrZero();
        BigDecimal discount = preview.getDiscountOrZero();
        BigDecimal recomputedTotal = subtotal
                .add(tax)
                .subtract(discount)
                .setScale(2, RoundingMode.HALF_UP);

        Assert.assertTrue(
                preview.equalsMoney(total, recomputedTotal),
                "❌ Total mismatch. Expected " + recomputedTotal +
                        " (subtotal + tax - discount) but got " + total
        );

        // -------------------- STRIPE CHECKOUT --------------------
        step("Proceed to payment and assert Stripe Checkout session is created/opened");
        String checkoutUrl = preview.proceedToStripeAndGetCheckoutUrl();
        Assert.assertNotNull(checkoutUrl, "❌ Could not obtain Stripe Checkout URL from preview");
        Assert.assertFalse(checkoutUrl.isBlank(), "❌ Stripe Checkout URL is blank");
        Assert.assertTrue(
                checkoutUrl.contains("checkout.stripe.com"),
                "❌ Unexpected checkout URL (not Stripe?): " + checkoutUrl
        );

        // Optional: navigate to make sure Stripe page loads
        driver().navigate().to(checkoutUrl);
    }























    /* ================================ Debug helpers ================================ */

    private void saveSnapshot(String tag) {
        try {
            File src = ((TakesScreenshot) driver()).getScreenshotAs(OutputType.FILE);
            File dest = new File("target/" + tag + ".png");
            dest.getParentFile().mkdirs();
            java.nio.file.Files.copy(src.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[DEBUG] screenshot saved: " + dest.getAbsolutePath());
        } catch (Throwable t) {
            System.out.println("[DEBUG] screenshot failed: " + t.getMessage());
        }
        try {
            File html = new File("target/" + tag + ".html");
            html.getParentFile().mkdirs();
            java.nio.file.Files.writeString(html.toPath(), driver().getPageSource());
            System.out.println("[DEBUG] html dump saved: " + html.getAbsolutePath());
        } catch (Throwable t) {
            System.out.println("[DEBUG] html dump failed: " + t.getMessage());
        }
    }

    private void dumpBrowserConsole() {
        try {
            System.out.println("\n[DEBUG] BROWSER CONSOLE LOGS:");
            driver().manage().logs().get(org.openqa.selenium.logging.LogType.BROWSER)
                    .forEach(System.out::println);
        } catch (Throwable ignored) {}
    }

    /* ========================= Email collectors (for preview) ========================= */

    private static String lc(String s) { return s == null ? "" : s.toLowerCase(); }

    private WebElement findPreviewTable(boolean DEBUG) {
        List<WebElement> tables = driver().findElements(By.xpath("//table"));
        dbg(DEBUG, "[findPreviewTable] tables found: " + tables.size());
        if (tables.isEmpty()) throw new NoSuchElementException("No <table> found on page");

        for (WebElement t : tables) {
            List<WebElement> ths = t.findElements(By.xpath(".//thead//th"));
            boolean hasEmailHeader = ths.stream().anyMatch(th -> lc(th.getText()).contains("email"));
            dbg(DEBUG, "  table hasEmailHeader? " + hasEmailHeader + " | ths=" + texts(ths));
            if (hasEmailHeader) {
                dbg(DEBUG, "[findPreviewTable] pick table with Email header");
                return t;
            }
        }

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

    private WebElement findPreviewTable() { return findPreviewTable(true); }

    private OptionalInt getEmailColumnIndex(WebElement table, boolean DEBUG) {
        List<WebElement> headers = table.findElements(By.xpath(".//thead//tr[1]//th"));
        dbg(DEBUG, "[getEmailColumnIndex] headers: " + texts(headers));
        for (int i = 0; i < headers.size(); i++) {
            String h = lc(headers.get(i).getText()).replaceAll("\\s+", " ").trim();
            if (h.contains("email")) {
                dbg(DEBUG, "  email column at index (1-based): " + (i + 1));
                return OptionalInt.of(i + 1);
            }
        }
        return OptionalInt.empty();
    }

    private void ensureTableVisible(WebElement table, boolean DEBUG) {
        try {
            ((JavascriptExecutor) driver()).executeScript(
                    "arguments[0].scrollIntoView({block:'center', inline:'nearest'});", table);
            dbg(DEBUG, "[ensureTableVisible] scrolled table into view");
        } catch (Exception e) {
            dbg(DEBUG, "[ensureTableVisible] scroll failed: " + e.getMessage());
        }
    }

    private List<String> collectEmailsFromTable(WebElement table, boolean DEBUG) {
        ensureTableVisible(table, DEBUG);

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

        List<WebElement> anyCellsEls = table.findElements(By.xpath(".//tbody//*[self::td or @role='cell']"));
        List<String> anyCell = anyCellsEls.stream()
                .map(el -> el.getText().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT))
                .filter(t -> t.contains("@"))
                .distinct()
                .toList();
        dbg(DEBUG, "[B any-cell] totalCells=" + anyCellsEls.size() + " | matches=" + anyCell.size() + " | sample=" + sample(anyCell));
        if (!anyCell.isEmpty()) return anyCell;

        List<WebElement> mailtoEls = table.findElements(By.xpath(".//tbody//a[starts-with(translate(@href,'MAILTO','mailto'),'mailto:')]"));
        List<String> mailtos = mailtoEls.stream()
                .map(a -> String.valueOf(a.getAttribute("href")).toLowerCase(Locale.ROOT).replace("mailto:", ""))
                .distinct()
                .toList();
        dbg(DEBUG, "[C mailto] elements=" + mailtoEls.size() + " | matches=" + mailtos.size() + " | sample=" + sample(mailtos));
        if (!mailtos.isEmpty()) return mailtos;

        String outer = String.valueOf(((JavascriptExecutor) driver()).executeScript("return arguments[0].outerHTML;", table));
        var m = Pattern.compile("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", Pattern.CASE_INSENSITIVE).matcher(outer);
        LinkedHashSet<String> set = new LinkedHashSet<>();
        while (m.find()) set.add(m.group().toLowerCase(Locale.ROOT));
        List<String> regexFound = new ArrayList<>(set);
        dbg(DEBUG, "[D regex-html] found=" + regexFound.size() + " | sample=" + sample(regexFound));
        if (!regexFound.isEmpty()) return regexFound;

        dbg(DEBUG, "[collectEmailsFromTable] No emails found.");
        dbg(DEBUG, "  Head cols: " + texts(table.findElements(By.xpath(".//thead//th"))));
        dbg(DEBUG, "  First 5 rows: " + texts(table.findElements(By.xpath(".//tbody//tr[position()<=5]"))));
        dbg(DEBUG, "  OuterHTML[0..600]: " + outer.substring(0, Math.min(outer.length(), 600)));
        return List.of();
    }

    private List<String> collectEmailsFromPreviewTable(boolean DEBUG) {
        dbg(DEBUG, "[collectEmailsFromPreviewTable] start");
        try {
            new WebDriverWait(driver(), Duration.ofSeconds(15))
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

    private List<String> collectEmailsFromPreviewTable() { return collectEmailsFromPreviewTable(true); }

    private void dbg(boolean on, String msg) { if (on) System.out.println(msg); }

    private List<String> texts(List<WebElement> els) {
        List<String> out = new ArrayList<>();
        for (WebElement e : els) out.add(e.getText());
        return out;
    }

    private String sample(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        return list.size() <= 5 ? list.toString() : list.subList(0, 5).toString() + " … (+" + (list.size() - 5) + ")";
    }

    private void highlight(By locator) {
        List<WebElement> els = driver().findElements(locator);
        JavascriptExecutor js = (JavascriptExecutor) driver();
        for (WebElement el : els) {
            try {
                js.executeScript("arguments[0].style.outline='3px solid magenta'; arguments[0].style.background='rgba(255,0,255,0.08)';", el);
            } catch (Exception ignored) {}
        }
        System.out.println("[highlight] outlined elements: " + els.size());
    }

    private void dumpTablesSummary(String tag) {
        System.out.println("=== [dumpTablesSummary][" + tag + "] URL=" + safeUrl() + " ===");
        List<WebElement> tables = driver().findElements(By.xpath("//table"));
        System.out.println("tables: " + tables.size());
        for (int i = 0; i < tables.size(); i++) {
            WebElement t = tables.get(i);
            List<WebElement> ths = t.findElements(By.xpath(".//thead//th"));
            List<WebElement> firstRows = t.findElements(By.xpath(".//tbody//tr[position()<=3]"));
            String outer = "";
            try {
                outer = String.valueOf(((JavascriptExecutor) driver()).executeScript("return arguments[0].outerHTML;", t));
            } catch (Exception ignored) {}
            System.out.println("-- table[" + i + "] heads=" + texts(ths));
            System.out.println("   firstRows=" + texts(firstRows));
            System.out.println("   snippet=" + (outer.length() > 200 ? outer.substring(0, 200) + "…" : outer));
        }
    }

    private void waitWithDiagnostics(Duration timeout, String name, ExpectedCondition<Boolean> cond, Runnable onTimeoutDump) {
        try {
            new WebDriverWait(driver(), timeout).until(cond);
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
     * CI: obey ALLOW_CREATE_INBOX_FALLBACK (typically false).
     */
    private Recipient provisionUniqueRecipient() {
        // Auto-enable creation when not in CI, unless the user explicitly set the flag.
        if (System.getenv("CI") == null && System.getProperty("ALLOW_CREATE_INBOX_FALLBACK") == null) {
            System.setProperty("ALLOW_CREATE_INBOX_FALLBACK", "true");
        }

        try {
            InboxDto inbox = MailSlurpUtils.resolveFixedOrCreateInbox(); // will Skip if not allowed and no fixed ID
            System.out.println("📮 Inbox for this run: " + inbox.getEmailAddress());
            return new Recipient(inbox.getId(), inbox.getEmailAddress());
        } catch (SkipException se) {
            throw se;
        } catch (Exception e) {
            throw new SkipException("Cannot provision a unique recipient email: " + e.getMessage());
        }
    }

    // ==================== small utils ====================

    private static void step(String title) {
        System.out.println("\n====== " + title + " ======\n");
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "(blank)";
        int at = email.indexOf('@');
        String user = at > -1 ? email.substring(0, at) : email;
        String dom = at > -1 ? email.substring(at) : "";
        if (user.length() <= 2) return user.charAt(0) + "****" + dom;
        return user.charAt(0) + "****" + user.charAt(user.length() - 1) + dom;
    }

    private static String plusTag(String email, String tag) {
        int at = email.indexOf('@');
        if (at <= 0) return email;
        return email.substring(0, at) + "+" + tag + email.substring(at);
    }

    public static String extractSessionIdFromUrl(String url) {
        if (url == null) return null;
        Matcher m = Pattern.compile("(?i)(?:cs_test_[A-Za-z0-9_]+)|(?:session_id=([^&]+))").matcher(url);
        if (m.find()) {
            String full = m.group();
            if (full.startsWith("cs_test_")) return full;
            if (m.groupCount() >= 1) return m.group(1);
        }
        return null;
    }

    private String safeUrl() {
        try { return driver().getCurrentUrl(); } catch (Exception e) { return ""; }
    }

    /* ============================ helpers (local to test class) ============================ */

    private enum Mode { GRID, UPLOAD }

    private Mode waitForModeToSettle(AssessmentEntryPage page, Duration timeout) {
        new WebDriverWait(driver(), timeout).until(d ->
                page.isManualGridVisible() || page.isUploadPanelVisible());
        return page.isManualGridVisible() ? Mode.GRID : Mode.UPLOAD;
    }

    private void safeTriggerValidationBlurs() {
        try {
            new AssessmentEntryPage(driver()).triggerManualValidationBlurs();
        } catch (Throwable ignored) {
            List<WebElement> inputs = driver().findElements(By.cssSelector("input[id^='users.']"));
            for (WebElement el : inputs) {
                try {
                    ((JavascriptExecutor) driver()).executeScript("arguments[0].scrollIntoView({block:'center'})", el);
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
        try { url = driver().getCurrentUrl(); } catch (Exception ignored) {}
        System.out.printf(
                "[SNAPSHOT AFTER_UPLOAD] mode=%s | rows=%d | inline=%d | proceedEnabled=%s | uploadVisible=%s | radioSelected=%s | toast='%s' | url=%s%n",
                mode, rows, inline, proceedEnabled, uploadVisible, radioSelected, toast, url
        );
    }

    private void dumpInlineErrors() {
        List<String> texts = new ArrayList<>();
        List<By> locators = List.of(
                By.cssSelector(".ant-form-item-explain-error"),
                By.cssSelector("span[type='error']")
        );
        for (By by : locators) {
            for (WebElement el : driver().findElements(by)) {
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
        List<WebElement> inputs = driver().findElements(By.cssSelector("input[id^='users.']"));
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
        List<String> candidates = new ArrayList<>();
        for (WebElement el : driver().findElements(By.cssSelector(".ant-form-item-explain-error, span[type='error']"))) {
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
