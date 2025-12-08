package tests;

import base.BaseTest;

import Utils.Config;
import com.google.common.collect.ImmutableList;
import io.qameta.allure.*;
import org.openqa.selenium.*;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v142.network.Network;
import org.openqa.selenium.devtools.v142.network.model.ConnectionType;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;

import pages.BasePage;
import pages.LoginPage;
import pages.Shop.AssessmentEntryPage;
import pages.Shop.OrderPreviewPage;
import pages.Shop.PurchaseRecipientSelectionPage;
import pages.Shop.Stripe.StripeCheckoutPage;
import pages.menuPages.DashboardPage;
import pages.menuPages.ShopPage;


import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

import static Utils.WaitUtils.isVisible;
import static io.qameta.allure.Allure.step;
import static org.testng.Assert.*;


@Epic("Tilt – Purchases")
@Feature("Recipient Selection & Manual Entry Validation")
@Owner("Emiliano")
public class ManualEntryValidationTests extends BaseTest {



    private AssessmentEntryPage openTeamManualEntryPage() {
        DashboardPage dashboard = startFreshSession(null);
        ShopPage shopPage = dashboard.goToShop();
        PurchaseRecipientSelectionPage recipients = shopPage.clickBuyNowForTrueTilt();
        recipients.waitUntilLoaded().selectTeam();
        return recipients.clickNext();   // deprecated but perfect for tests
    }


    @Test(groups = {"shop","preview","validation","smoke"})
    @Severity(SeverityLevel.CRITICAL)
    @Story("Manual entry – email field validation blocks checkout when invalid")
    public void testManualEntryInvalidEmailBlocksProceed_FullFlow() {
        // Login
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        DashboardPage dashboard = login.login(Config.getAdminEmail(), Config.getAdminPassword());
        assertTrue(dashboard.isLoaded(), "Dashboard did not load after login");

        // Start purchase
        ShopPage shop = dashboard.goToShop();
        PurchaseRecipientSelectionPage select = shop.clickBuyNowForTrueTilt();
        select.selectClientOrIndividual();
        select.clickNext();

        // Manual entry for a single recipient
        AssessmentEntryPage entry = new AssessmentEntryPage(driver());
        entry.selectManualEntry();
        entry.enterNumberOfIndividuals("1");

        // Fill names and BAD email
        entry.fillUserDetailsAtIndex(0, "Emi", "Rod", "not-an-email");

        // Assert: proceed disabled + inline validation message visible
        assertFalse(entry.isProceedToPaymentEnabled(),
                "'Proceed to payment' must be disabled for invalid email.");

        String err = entry.getEmailErrorAtRow(0);
        assertTrue(err == null || err.toLowerCase().contains("email"),
                "Expected an inline email validation message; got: " + err);

        // Fix the email → button should enable → proceed to preview
        String goodEmail = "qa+" + java.util.UUID.randomUUID().toString().substring(0,8) + "@example.com";
        entry.setEmailAtRow(0, goodEmail);

        assertTrue(entry.isProceedToPaymentEnabled(),
                "'Proceed to payment' should enable after valid email.");

        OrderPreviewPage preview = entry.clickProceedToPayment().waitUntilLoaded();
        assertTrue(preview.isLoaded(), "Order Preview did not load after fixing email.");
    }


    @Test(description = "TILT-956: Default state when TEAM is selected")
    @TmsLink("TILT-956")
    @Severity(SeverityLevel.NORMAL)
    public void teamManualEntry_defaultState_requiredFields() throws InterruptedException {
        AssessmentEntryPage entryPage = openTeamManualEntryPage();

        // Radios all deselected
        assertFalse(entryPage.isAddMembersExistingSelected(),
                "'Add members to existing team' should NOT be selected by default");
        assertFalse(entryPage.isCreateNewTeamSelected(),
                "'Create new team' should NOT be selected by default");
        assertFalse(entryPage.isManuallyEnterSelected(),
                "'Manually enter' should NOT be selected by default");
        assertFalse(entryPage.isDownloadTemplateSelected(),
                "'Download template' should NOT be selected by default");

        // Quantity = 0, fields empty, payment disabled
        assertEquals(entryPage.getNumberOfIndividuals(), 0,
                "Quantity should default to 0 for TEAM flow");
        assertEquals(entryPage.getGroupName(), "",
                "Team name (group name) should be empty by default");
        assertFalse(entryPage.isProceedToPaymentEnabled(),
                "Proceed to payment must be disabled when fields are empty");

        // Select Create new team + Manually enter + set quantity = 1
        entryPage
                .selectCreateNewTeam()
                .selectManualEntry()
                .enterNumberOfIndividuals("1");

        assertTrue(entryPage.renderedEmailRows() >= 1,
                "At least one member row should be rendered for quantity = 1");

        // All row-1 fields empty
        assertEquals(entryPage.getFirstNameAtRow(1), "",
                "First Name in row 1 should be empty");
        assertEquals(entryPage.getLastNameAtRow(1), "",
                "Last Name in row 1 should be empty");
        assertEquals(entryPage.getEmailAtRow(1), "",
                "Email in row 1 should be empty");

        // Team name still empty & payment disabled
        assertEquals(entryPage.getGroupName(), "",
                "Team name should still be empty at this point");
        assertFalse(entryPage.isProceedToPaymentEnabled(),
                "Proceed to payment must remain disabled while required fields are empty");
    }


    @Test(description = "QASE 957: Leave Team name blank → expect inline validation + Proceed disabled")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("957")
    public void requiredTeamNameBlank() {

        // ---- Login ----
        // ---- Navigate into Assessment Entry page for TEAM ----
        AssessmentEntryPage entry = openTeamManualEntryPage();
        entry.waitUntilLoaded();


        // ---- Step 1: Select the TEAM purchase mode ----
        entry.selectCreateNewTeam();

        // ---- Step 2: Quantity = 1 ----
        entry.selectManualEntry();
        entry.enterNumberOfIndividuals("1");

        // ---- Step 3: Fill one member row ----
        entry.fillUserDetailsAtIndex(
                1,
                "John",       // first
                "Tester",     // last
                "john.tester+" + System.currentTimeMillis() + "@mail.com"
        );

        // ---- Step 4: Leave Team Name EMPTY ----
        entry.setGroupName("");   // intentionally blank

        // ---- Trigger validation ----
        entry.triggerManualValidationBlurs(1);


        // ---- EXPECTATION: Proceed to Payment must be disabled ----
        boolean enabled = entry.isProceedToPaymentEnabled();
        Assert.assertFalse(enabled, "Proceed to payment should be disabled when Team Name is empty.");
    }


    @Test(description = "QASE 1791: Leave Organization name blank → expect inline validation + Proceed disabled")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("1791")
    public void requiredOrganizationNameBlank() {

        // ---- Login + navigate to TEAM manual entry flow ----
        AssessmentEntryPage entry = openTeamManualEntryPage();
        entry.waitUntilLoaded();

        // ---- Step 1: Select TEAM purchase mode ----
        entry.selectCreateNewTeam();

        // ---- Step 2: Quantity = 1 (manual entry) ----
        entry.selectManualEntry();
        entry.enterNumberOfIndividuals("1");

        // ---- Step 3: Fill one member row ----
        entry.fillUserDetailsAtIndex(
                1,
                "John",       // first
                "Tester",     // last
                "john.tester+" + System.currentTimeMillis() + "@mail.com"
        );

        // ---- Step 4: Make Org name EMPTY but Team name VALID ----
        entry.setGroupName("My Test Team"); // valid team name
        entry.setOrganizationName("");      // intentionally blank

        // ---- Trigger validation ----
        entry.triggerManualValidationBlurs(1);

        // ---- OPTIONAL EXPECTATION: Org inline validation should appear ----
        String orgError = entry.getOrganizationNameError();
        Assert.assertNotNull(orgError, "Expected Organization name inline validation error.");
        Assert.assertFalse(orgError.isBlank(), "Organization name error text should not be blank.");

        // ---- EXPECTATION: Proceed to Payment must be disabled ----
        boolean enabled = entry.isProceedToPaymentEnabled();
        Assert.assertFalse(enabled,
                "Proceed to payment should be disabled when Organization name is empty.");
    }


    @Test(description = "QASE 958: Member first name empty → inline error + Proceed disabled")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("958")
    public void requiredMemberFirstNameEmpty() {

        // ---- Login + go to TEAM manual entry flow ----
        AssessmentEntryPage entry = openTeamManualEntryPage();
        entry.waitUntilLoaded();

        // ---- Step 1: Select TEAM mode (Create new team + Manual entry) ----
        entry.selectCreateNewTeam();
        entry.selectManualEntry();

        // ---- Step 2: Quantity = 1 ----
        entry.enterNumberOfIndividuals("1");

        // ---- Step 3: Valid Org and Team ----
        entry.setOrganizationName("My Test Org");
        entry.setGroupName("My Test Team");

        // ---- Step 4: Member with empty First Name, all other fields empty----
        entry.fillUserDetailsAtIndex(
                1,
                "", // ← First Name vacío a propósito
                "Tester",
                "john.tester+" + System.currentTimeMillis() + "@mail.com"
        );

        // Sanity check: first name empty
        Assert.assertEquals(
                entry.getFirstNameAtRow(1),
                "",
                "First Name in row 1 should be empty"
        );

        // ---- Trigger validations ----
        entry.triggerManualValidationBlurs(1);

        // ---- EXPECTATION 1: Hay inline error relacionado al First Name ----
        var errors = entry.collectInlineErrorTexts();
        boolean hasFirstNameError = errors.stream()
                .map(String::toLowerCase)
                .anyMatch(msg ->
                        msg.contains("first name") ||
                                msg.contains("first") ||
                                msg.contains("required")
                );

        Assert.assertTrue(
                hasFirstNameError,
                "Expected an inline error mentioning first name/required. Got: " + errors
        );

        // ---- EXPECTATION 2: Proceed to payment deshabilitado ----
        boolean enabled = entry.isProceedToPaymentEnabled();
        Assert.assertFalse(
                enabled,
                "Proceed to payment should be disabled when First Name is empty."
        );
    }


    @Test(description = "QASE 959: Member last name empty → inline error + Proceed disabled")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("959")
    public void requiredMemberLastNameEmpty() {

        // ---- Login + go to TEAM manual entry flow ----
        AssessmentEntryPage entry = openTeamManualEntryPage();
        entry.waitUntilLoaded();

        // ---- Step 1: Select TEAM purchase mode ----
        entry.selectCreateNewTeam();
        entry.selectManualEntry();

        // ---- Step 2: Quantity = 1 ----
        entry.enterNumberOfIndividuals("1");

        // ---- Step 3: Org + Team names valid ----
        entry.setOrganizationName("My Test Org");
        entry.setGroupName("My Test Team");

        // ---- Step 4: Member with LAST NAME EMPTY, others valid ----
        entry.fillUserDetailsAtIndex(
                1,
                "John",  // First name valid
                "",      // Last name empty on purpose
                "john.tester+" + System.currentTimeMillis() + "@mail.com"
        );

        // Sanity check: last name is really empty in row 1
        Assert.assertEquals(
                entry.getLastNameAtRow(1),
                "",
                "Last Name in row 1 should be empty"
        );

        // ---- Trigger validations ----
        entry.triggerManualValidationBlurs(1);

        // ---- EXPECTATION 1: inline error related to Last Name ----
        var errors = entry.collectInlineErrorTexts();
        boolean hasLastNameError = errors.stream()
                .map(String::toLowerCase)
                .anyMatch(msg ->
                        msg.contains("last name") ||
                                msg.contains("last") ||
                                msg.contains("required")
                );

        Assert.assertTrue(
                hasLastNameError,
                "Expected an inline error mentioning last name/required. Got: " + errors
        );

        // ---- EXPECTATION 2: Proceed to payment must be disabled ----
        boolean enabled = entry.isProceedToPaymentEnabled();
        Assert.assertFalse(
                enabled,
                "Proceed to payment should be disabled when Last Name is empty."
        );
    }


    @Test(description = "QASE 960: Member email empty → inline error + Proceed disabled")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("960")
    public void requiredMemberEmailEmpty() {

        // ---- Login + enter TEAM manual-entry flow ----
        AssessmentEntryPage entry = openTeamManualEntryPage();
        entry.waitUntilLoaded();

        // ---- Step 1: Select TEAM purchase path ----
        entry.selectCreateNewTeam();
        entry.selectManualEntry();

        // ---- Step 2: Quantity = 1 ----
        entry.enterNumberOfIndividuals("1");

        // ---- Step 3: Valid organization + team names ----
        entry.setOrganizationName("My Test Org");
        entry.setGroupName("My Test Team");

        // ---- Step 4: Fill first + last name, leave EMAIL EMPTY ----
        entry.fillUserDetailsAtIndex(
                1,
                "John",          // valid first name
                "Tester",        // valid last name
                ""               // EMAIL EMPTY on purpose
        );

        // Confirm email field is actually empty
        Assert.assertEquals(
                entry.getEmailAtRow(1),
                "",
                "Email in row 1 should be empty"
        );

        // ---- Trigger validation ----
        entry.setEmailAtRow(1, "test");
        entry.clearEmailAtRow(1);
        entry.triggerManualValidationBlurs(1);

        // ---- EXPECTATION #1: Inline error about email ----
        List<String> errors = entry.collectInlineErrorTexts();

        boolean hasEmailError = errors.stream()
                .map(String::toLowerCase)
                .anyMatch(msg ->
                        msg.contains("email") ||
                                msg.contains("required") ||
                                msg.contains("valid")
                );

        Assert.assertTrue(
                hasEmailError,
                "Expected an inline error for empty Email. Got: " + errors
        );

        // ---- EXPECTATION #2: Proceed to payment must be disabled ----
        boolean enabled = entry.isProceedToPaymentEnabled();
        Assert.assertFalse(
                enabled,
                "Proceed to payment should be disabled when email is empty."
        );
    }


    @Test(description = "QASE 961: Invalid email formats → inline error + Proceed disabled")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("961")
    public void emailFormatValidation_invalidEmails() {

        // ---- Login + go to TEAM manual entry flow ----
        AssessmentEntryPage entry = openTeamManualEntryPage();
        entry.waitUntilLoaded();

        // ---- Step 1: Select TEAM purchase path ----
        entry.selectCreateNewTeam();
        entry.selectManualEntry();

        // ---- Step 2: Quantity = 1 ----
        entry.enterNumberOfIndividuals("1");

        // ---- Step 3: Valid Organization + Team names ----
        entry.setOrganizationName("My Test Org");
        entry.setGroupName("My Test Team");

        // ---- Step 4: Valid First + Last name in row 1 ----
        entry.fillUserDetailsAtIndex(
                1,
                "John",
                "Tester",
                "john.valid.initial@mail.com"   // will be overridden in the loop
        );

        // ---- Invalid email formats to try ----
        List<String> invalidEmails = Arrays.asList(
                "a@",
                "user@domain",
                "user@.com",
                "user@@domain.com"
        );

        for (String invalid : invalidEmails) {
            // Put the invalid email in row 1
            entry.setEmailAtRow(1, invalid);

            // Trigger validation
            entry.triggerManualValidationBlurs(1);

            // Inline error for this row
            String err = entry.getEmailErrorAtRow(1);
            Assert.assertNotNull(
                    err,
                    "Expected an inline error for invalid email: " + invalid
            );
            Assert.assertFalse(
                    err.trim().isEmpty(),
                    "Inline error text should not be empty for invalid email: " + invalid
            );

            String lower = err.toLowerCase(Locale.ROOT);
            Assert.assertTrue(
                    lower.contains("email")
                            || lower.contains("valid")
                            || lower.contains("format")
                            || lower.contains("address"),
                    "Expected error message to mention email/valid/format/address. " +
                            "Email='" + invalid + "', error='" + err + "'"
            );

            // Proceed to payment must remain disabled
            boolean enabled = entry.isProceedToPaymentEnabled();
            Assert.assertFalse(
                    enabled,
                    "Proceed to payment should be disabled for invalid email: " + invalid
            );
        }
    }


    @Test(description = "QASE 963: Plus-address email is valid and allows proceeding")
    @Severity(SeverityLevel.NORMAL)
    @TmsLink("963")
    public void plusAddressingAllowed_teamFlow() throws InterruptedException {

        // ---- Login + open TEAM manual entry ----
        AssessmentEntryPage entry = openTeamManualEntryPage();
        entry.waitUntilLoaded();

        // ---- TEAM + Manual Entry ----
        entry.selectCreateNewTeam();
        entry.selectManualEntry();
        entry.enterNumberOfIndividuals("1");

        // ---- Valid Org + Team ----
        entry.setOrganizationName("Effectus Software");
        entry.setGroupName("Plus Address Team");

        // ---- Member row with plus-address email ----
        final String plusEmail = "erodriguez+11035@effectussoftware.com";

        entry.fillUserDetailsAtIndex(
                1,
                "Emiliano",
                "Rodriguez",
                plusEmail
        );

        // Trigger client-side validation
        entry.triggerManualValidationBlurs(1);

        // ---- EXPECT: email is treated as valid (no inline error) ----
        String emailError = entry.getEmailErrorAtRow(1);

        Thread.sleep(2000);
        Assert.assertNull(
                emailError,
                "Plus-address email should NOT show an inline validation error. Error was: " + emailError
        );

        // Optional: sanity check that no generic required errors remain
        Assert.assertEquals(
                entry.inlineRequiredErrorsCount(),
                0,
                "There should be no remaining 'required' inline errors when all fields are valid."
        );

        // ---- EXPECT: Proceed to payment is ENABLED ----
        Assert.assertTrue(
                entry.isProceedToPaymentEnabled(),
                "Proceed to payment must be ENABLED when plus-address email and all required fields are valid."
        );
    }



    @Test(description = "QASE 964: Mixed casing + accented characters allowed")
    @Severity(SeverityLevel.NORMAL)
    @TmsLink("964")
    public void mixedCasingAndI18NCharacters_teamFlow() {

        // ---- Open TEAM manual entry page ----
        AssessmentEntryPage entry = openTeamManualEntryPage();
        entry.waitUntilLoaded();

        // ---- Valid Team + Org ----
        entry.selectCreateNewTeam();
        entry.setOrganizationName("Institución Ñandú Internacional");
        entry.setGroupName("Equipo Ünico");

        // ---- Quantity = 1 ----
        entry.selectManualEntry();
        entry.enterNumberOfIndividuals("1");

        // ---- Fill member row with accented characters ----
        entry.fillUserDetailsAtIndex(
                1,
                "José Andrés",              // First Name
                "Muñoz Üribe",              // Last Name
                "test+" + System.currentTimeMillis() + "@mail.com" // valid email
        );

        // Trigger blur validations (ensures error spans render if any)
        entry.triggerManualValidationBlurs(1);

        // ---- Check for field-level errors ----
        String firstErr = entry.errorTextForFirstName(1);
        String lastErr  = entry.errorTextForLastName(1);
        String emailErr = entry.errorTextForEmail(1);

        Assert.assertNull(firstErr, "First name with accents should NOT show an error.");
        Assert.assertNull(lastErr,  "Last name with accents should NOT show an error.");
        Assert.assertNull(emailErr, "Valid email should NOT show an error.");

        // ---- Proceed to payment should be enabled ----
        boolean enabled = entry.isProceedToPaymentEnabled();
        Assert.assertTrue(enabled, "Proceed to payment MUST be enabled with valid i18n names.");
    }


    @Test(description = "QASE 965: Quantity=3 → three required rows; incomplete rows block payment")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("965")
    public void quantityIncreaseAddsRequiredRows() {

        // ---- Login + go to TEAM manual-entry ----
        AssessmentEntryPage entry = openTeamManualEntryPage();
        entry.waitUntilLoaded();

        // ---- TEAM + Manual Entry ----
        entry.selectCreateNewTeam();
        entry.selectManualEntry();

        // ---- Quantity = 3 ----
        entry.enterNumberOfIndividuals("3");

        // Expect 3 rows rendered
        entry.waitManualGridEmailsAtLeast(3, Duration.ofSeconds(10));
        Assert.assertEquals(
                entry.renderedEmailRows(),
                3,
                "Quantity=3 should render exactly 3 member rows."
        );

        // ---- Valid org + team names (so ONLY member rows block payment) ----
        entry.setOrganizationName("My Test Org");
        entry.setGroupName("My Test Team");

        // ---- Row 1: fully valid → should NOT block by itself ----
        entry.fillUserDetailsAtIndex(
                1,
                "Alice",
                "Tester",
                "alice+" + System.currentTimeMillis() + "@mail.com"
        );

        // ---- Rows 2 & 3: simulate user typing and then deleting email ----
        // We keep First/Last valid, but EMAIL empty → row is incomplete.

        // Row 2
        entry.fillUserDetailsAtIndex(
                2,
                "Bob",
                "Tester",
                "bob+" + System.currentTimeMillis() + "@mail.com"
        );
        entry.setEmailAtRow(2, "temp");  // type something
        entry.clearEmailAtRow(2);        // then clear it (like QASE-960)

        // Row 3
        entry.fillUserDetailsAtIndex(
                3,
                "Charlie",
                "Tester",
                "charlie+" + System.currentTimeMillis() + "@mail.com"
        );
        entry.setEmailAtRow(3, "temp");
        entry.clearEmailAtRow(3);

        // Sanity: emails really empty for rows 2 & 3
        Assert.assertEquals(entry.getEmailAtRow(2), "", "Row 2 email should be empty after clear.");
        Assert.assertEquals(entry.getEmailAtRow(3), "", "Row 3 email should be empty after clear.");

        // ---- Trigger validation on all 3 rows (blur/change) ----
        entry.triggerManualValidationBlurs(3);

        // ---- EXPECTATION: inline validation on rows 2 & 3 + Proceed disabled ----
        // You can use either per-field errors or the generic collector:

        List<String> errors = entry.collectInlineErrorTexts();
        System.out.println("[QASE-965] Inline errors: " + errors);

        boolean hasEmailRelatedError = errors.stream()
                .map(String::toLowerCase)
                .anyMatch(msg ->
                        msg.contains("email") ||
                                msg.contains("required") ||
                                msg.contains("valid")
                );

        Assert.assertTrue(
                hasEmailRelatedError,
                "Expected inline validation errors for incomplete rows (2 & 3). Got: " + errors
        );

        // And Pay with Stripe / Proceed to payment must be disabled
        Assert.assertFalse(
                entry.isProceedToPaymentEnabled(),
                "Proceed to payment must be disabled while rows 2 and 3 are incomplete."
        );
    }


    @Test(description = "QASE 966: Reduce quantity from 3 to 1 removes extra rows safely")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("966")
    public void reduceQuantityRemovesExtraRowsSafely_teamFlow() {

        // ---- Login + go to TEAM manual-entry ----
        AssessmentEntryPage entry = openTeamManualEntryPage();
        entry.waitUntilLoaded();

        // ---- TEAM + Manual Entry ----
        entry.selectCreateNewTeam();
        entry.selectManualEntry();

        // ---- Valid org + team names (so only rows/quantity matter) ----
        entry.setOrganizationName("My Test Org");
        entry.setGroupName("My Test Team");

        // ---- Step 1: Quantity = 3 → expect 3 rows ----
        entry.enterNumberOfIndividuals("3");
        entry.waitManualGridEmailsAtLeast(3, Duration.ofSeconds(10));

        Assert.assertEquals(
                entry.renderedEmailRows(),
                3,
                "Quantity=3 should render exactly 3 member rows."
        );
        Assert.assertEquals(
                entry.getNumberOfIndividuals(),
                3,
                "Spinner value should show quantity=3."
        );

        // ---- Step 2: Fill all 3 rows with valid data ----
        long now = System.currentTimeMillis();
        entry.fillUserDetailsAtIndex(
                1,
                "Alice",
                "Tester",
                "alice+" + now + "@mail.com"
        );
        entry.fillUserDetailsAtIndex(
                2,
                "Bob",
                "Tester",
                "bob+" + now + "@mail.com"
        );
        entry.fillUserDetailsAtIndex(
                3,
                "Charlie",
                "Tester",
                "charlie+" + now + "@mail.com"
        );

        // Trigger validations so CTA can become enabled
        entry.triggerManualValidationBlurs(3);

        // ---- Sanity: with 3 fully valid rows, Proceed must be enabled ----
        Assert.assertTrue(
                entry.isProceedToPaymentEnabled(),
                "Proceed to payment should be enabled when all 3 rows + team/org are valid."
        );

        // ---- Step 3: Reduce quantity back to 1 ----
        entry.enterNumberOfIndividuals("1");

        // Wait until UI clamps the grid down to 1 row
        entry.waitRowsRenderedAtMost(1);

        Assert.assertEquals(
                entry.renderedEmailRows(),
                1,
                "After reducing quantity to 1, only one member row should remain."
        );
        Assert.assertEquals(
                entry.getNumberOfIndividuals(),
                1,
                "Spinner should show quantity=1 after reduction."
        );

        // Optional: ensure remaining row is still valid (email not cleared by clamp)
        String remainingEmail = entry.getEmailAtRow(1);
        Assert.assertTrue(
                remainingEmail.contains("@"),
                "Remaining row should still have a valid-looking email after quantity reduction. Got: " + remainingEmail
        );

        // ---- EXPECTATION: Pay with Stripe / Proceed stays enabled ----
        Assert.assertTrue(
                entry.isProceedToPaymentEnabled(),
                "Proceed to payment must remain enabled when the single remaining row + team/org are valid."
        );
    }


    @Test(description = "QASE 967: Quantity > 20 is capped at 20 (TEAM manual-entry)")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("967")
    public void maxQuantityCap_teamFlow() {

        // ---- Login + go to TEAM manual-entry ----
        AssessmentEntryPage entry = openTeamManualEntryPage();
        entry.waitUntilLoaded();

        // ---- TEAM + Manual Entry ----
        entry.selectCreateNewTeam();
        entry.selectManualEntry();

        // Valid org + team names so only quantity/rows matter
        entry.setOrganizationName("Cap Test Org");
        entry.setGroupName("Cap Test Team");

        // ---- Step 1: Try to set Quantity > 20 (e.g. 25) ----
        entry.enterNumberOfIndividuals2("25");

        // Wait for grid to react (rows rendered / clamped)
        entry.waitManualGridEmailsAtLeast(1, Duration.ofSeconds(10));

        int spinnerVal = entry.getNumberOfIndividuals();
        int rowCount   = entry.renderedEmailRows();

        // ---- EXPECTATION #1: spinner value is capped at 20 ----
        Assert.assertEquals(
                spinnerVal,
                20,
                "Quantity spinner should clamp to 20 when user attempts >20. Got: " + spinnerVal
        );

        // ---- EXPECTATION #2: grid rows are capped at 20 ----
        Assert.assertTrue(
                rowCount <= 20,
                "Rendered member rows should be capped at 20. Got: " + rowCount
        );
        Assert.assertEquals(
                rowCount,
                20,
                "Expected 20 rendered rows when quantity cap is hit."
        );

        // ---- EXPECTATION #3: UI shows 'Up to 20 individuals' helper text ----
        boolean hasCapHint = driver().findElements(By.xpath("//p[normalize-space()='Up to 20 individuals']")).stream().anyMatch(WebElement::isDisplayed);

        Assert.assertTrue(
                hasCapHint,
                "Expected helper text like 'Up to 20 individuals' when the quantity cap is reached."
        );

        // ---- Step 2: Try to set 21 → still capped at 20 ----
        entry.enterNumberOfIndividuals2("21");

        int spinnerAfter21 = entry.getNumberOfIndividuals();
        int rowsAfter21    = entry.renderedEmailRows();

        // Validation should prevent > 20
        Assert.assertEquals(
                spinnerAfter21,
                20,
                "Validation should prevent setting quantity above 20 (attempted 21)."
        );
        Assert.assertEquals(
                rowsAfter21,
                20,
                "Rendered rows should remain at 20 after trying to set 21."
        );
    }


    @Test(description = "QASE 968: Duplicate member emails → duplicate warning + Proceed disabled")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("968")
    public void duplicateMemberEmails_teamFlow() {

        // ---- Login + go to TEAM manual-entry ----
        AssessmentEntryPage entry = openTeamManualEntryPage();
        entry.waitUntilLoaded();

        // ---- TEAM + Manual Entry ----
        entry.selectCreateNewTeam();
        entry.selectManualEntry();

        // ---- Quantity = 2 rows ----
        entry.enterNumberOfIndividuals2("2");

        // ---- Valid org + team ----
        entry.setOrganizationName("Dup Test Org");
        entry.setGroupName("Dup Test Team");

        // ---- Same email for both rows ----
        final String dupEmail = "duplicate+" + System.currentTimeMillis() + "@effectussoftware.com";

        entry.fillUserDetailsAtIndex(1, "John", "One", dupEmail);
        entry.fillUserDetailsAtIndex(2, "Jane", "Two", dupEmail);

        // Sanity: we really have 2 rows rendered
        Assert.assertTrue(
                entry.renderedEmailRows() >= 2,
                "Expected at least 2 rendered rows when quantity=2."
        );

        // ---- Trigger field-level validation ----
        entry.triggerManualValidationBlurs(2);

        // ---- EXPECTATION #1: duplicate warning visible somewhere ----
        boolean row1Dup = entry.emailRowHasDuplicateError(1);
        boolean row2Dup = entry.emailRowHasDuplicateError(2);

        // Fallback: scan all inline errors for duplicate-style wording
        List<String> errors = entry.collectInlineErrorTexts();
        boolean hasDupMessage = row1Dup || row2Dup ||
                errors.stream()
                        .map(String::toLowerCase)
                        .anyMatch(msg ->
                                msg.contains("duplicate") ||
                                        msg.contains("duplicated") ||
                                        msg.contains("already in use") ||
                                        msg.contains("already exists") ||
                                        msg.contains("in use")
                        );

        Assert.assertTrue(
                hasDupMessage,
                "Expected a duplicate-email warning when using same email in 2 rows. Got: " + errors
        );

        // ---- EXPECTATION #2: Pay with Stripe / Proceed is blocked while duplicates present ----
        boolean proceedEnabled = entry.isProceedToPaymentEnabled();
        Assert.assertFalse(
                proceedEnabled,
                "Proceed to payment should be disabled when there are duplicate member emails in the order."
        );
    }


    @Test(description = "QASE 970: All required fields valid → Proceed enabled + Stripe Checkout opens")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("970")
    public void allRequiredFieldsValidEnablesPayment_teamFlow() {

        // ---- Login + navigate into TEAM manual-entry mode ----
        AssessmentEntryPage entry = openTeamManualEntryPage();
        entry.waitUntilLoaded();

        // ---- TEAM + Manual Entry ----
        entry.selectCreateNewTeam();
        entry.selectManualEntry();

        // ---- Quantity = 1 ----
        entry.enterNumberOfIndividuals2("1");

        // ---- Valid org & team names ----
        entry.setOrganizationName("Valid Org");
        entry.setGroupName("Valid Team");

        // ---- Fill all member fields with valid data ----
        String email = "valid" + System.currentTimeMillis() + "@effectussoftware.com";
        entry.fillUserDetailsAtIndex(1, "John", "Tester", email);

        // Trigger validators (similar pattern as other manual-entry tests)
        entry.triggerManualValidationBlurs(1);

        // ---- EXPECTATION #1: Proceed to payment becomes enabled ----
        boolean enabled = entry.isProceedToPaymentEnabled();
        Assert.assertTrue(
                enabled,
                "Proceed to payment should be ENABLED when all required fields are valid."
        );

        // ---- Step into Order Preview (same CTA you use in other flows) ----
        OrderPreviewPage preview = entry.clickProceedToPayment();   // this already exists in your codebase
        preview.waitUntilLoaded();

        // ---- EXPECTATION #2: Quantity = 1 in the preview (selected rows) ----
        int selectedCount = preview.getSelectedCount();
        Assert.assertEquals(
                selectedCount,
                1,
                "Expected exactly 1 selected member in the order preview."
        );

        // ---- EXPECTATION #3: Total amount > 0 for that single member ----
        BigDecimal total = preview.getTotal();
        Assert.assertTrue(
                total.compareTo(BigDecimal.ZERO) > 0,
                "Total amount should be > 0 when purchasing 1 TEAM assessment. Got: " + total
        );

        // ---- EXPECTATION #4: Clicking Pay actually opens Stripe Checkout ----
        String stripeUrl = preview.proceedToStripeAndGetCheckoutUrl();
        Assert.assertTrue(
                stripeUrl.contains("checkout.stripe.com"),
                "Expected to land on Stripe Checkout URL, but got: " + stripeUrl
        );
    }


    @Test(description = "QASE 971: Network/API failure on submit → Chrome offline error, no Stripe redirect")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("971")
    public void networkFailureOnSubmitGuard_teamFlow() {

        // ---- Login + navigate into TEAM manual-entry mode ----
        AssessmentEntryPage entry = openTeamManualEntryPage();
        entry.waitUntilLoaded();

        // ---- TEAM + Manual Entry ----
        entry.selectCreateNewTeam();
        entry.selectManualEntry();

        // ---- Quantity = 1 ----
        entry.enterNumberOfIndividuals2("1");

        // ---- Valid org & team names ----
        entry.setOrganizationName("NetworkGuard Org");
        entry.setGroupName("NetworkGuard Team");

        // ---- Fill all member fields with valid data ----
        final String email = "network-guard-" + System.currentTimeMillis() + "@effectussoftware.com";
        entry.fillUserDetailsAtIndex(1, "John", "FailureCase", email);

        // Trigger validators so CTA can become enabled
        entry.triggerManualValidationBlurs(1);

        // ---- EXPECTATION #1: Proceed to payment becomes enabled ----
        Assert.assertTrue(
                entry.isProceedToPaymentEnabled(),
                "Proceed to payment should be ENABLED when all required fields are valid."
        );

        // ---- Step into Order Preview ----
        OrderPreviewPage preview = entry.clickProceedToPayment();
        preview.waitUntilLoaded();

        // ---- EXPECTATION #2: Payment CTA is enabled on Order Preview ----
        Assert.assertTrue(
                preview.isProceedEnabled(),
                "Pay/Proceed CTA should be enabled on Order Preview before simulating failure."
        );

        // Optional: remember whether Stripe is visible (for logging / branching)
        final boolean stripeVisible = preview.isPayWithStripeVisible();
        System.out.println("[networkFailureOnSubmitGuard] Stripe visible on preview = " + stripeVisible);

        final String urlBefore = driver().getCurrentUrl();

        // ---- Step: Simulate network/API failure via DevTools offline mode ----
        DevTools devTools = ((HasDevTools) driver()).getDevTools();
        devTools.createSession();

        devTools.send(Network.enable(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        ));

        // Fully offline → all requests (including Stripe) will fail
        devTools.send(Network.emulateNetworkConditions(
                true,                         // offline
                0,                            // latency
                0,                            // downloadThroughput
                0,                            // uploadThroughput
                Optional.of(ConnectionType.NONE),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        ));

        // ---- Step: Click payment CTA while offline ----
        preview.clickPayWithStripe();
//        if (stripeVisible) {
//            // Prefer explicit Stripe button when present
//            preview.clickPayWithStripe();
//            System.out.printf("<TESTTTTTTTTTTTT");
//        } else {
//            // Fallback: generic "Place order / Complete purchase" CTA
//            System.out.printf("<TESTTTTTTTTTTTT22222222");
//            preview.clickPrimaryPaymentCta();
//        }

        // ---- Wait for Chrome offline error page ----
        new WebDriverWait(driver(), Duration.ofSeconds(10))
                .until(d -> {
                    String source = d.getPageSource().toLowerCase();
                    String title  = d.getTitle().toLowerCase();
                    return source.contains("no internet")
                            || source.contains("err_internet_disconnected")
                            || title.contains("no internet");
                });

        // ---- EXPECTATION #3: Chrome offline error is shown ----
        String source     = driver().getPageSource().toLowerCase();
        String title      = driver().getTitle().toLowerCase();
        String currentUrl = driver().getCurrentUrl();

        Assert.assertTrue(
                source.contains("no internet")
                        || source.contains("err_internet_disconnected")
                        || title.contains("no internet"),
                "Expected Chrome 'No internet / ERR_INTERNET_DISCONNECTED' error page after going offline."
        );

        // ---- EXPECTATION #4: We never reached Stripe Checkout ----
        Assert.assertFalse(
                currentUrl.contains("checkout.stripe.com"),
                "Did not expect to be redirected to Stripe Checkout when browser is offline. URL = " + currentUrl
        );

        // Optional: still on Tilt origin (same host as before, just in error state)
        Assert.assertTrue(
                currentUrl.contains("tilt-dashboard-") || currentUrl.startsWith("https://tilt-dashboard-dev.tilt365.com"),
                "Expected to remain on Tilt origin; before=" + urlBefore + " | after=" + currentUrl
        );

        // ---- Restore network so other tests are not broken ----
        devTools.send(Network.emulateNetworkConditions(
                false,                         // online
                100,                           // latency
                5_000,                         // downloadThroughput
                5_000,                         // uploadThroughput
                Optional.of(ConnectionType.ETHERNET),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        ));
    }


    @Test(description = "QASE 975: Cancel on TEAM manual-entry returns to TTP and clears draft")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("975")
    public void cancelButtonClearsDraftAndNavigatesBack_teamFlow() {

        // ---- Step 1: Navigate into TEAM manual-entry mode ----
        AssessmentEntryPage entry = openTeamManualEntryPage();
        entry.waitUntilLoaded();

        // ---- TEAM + Manual Entry ----
        entry.selectCreateNewTeam();
        entry.selectManualEntry();

        // ---- Step 2: Fill valid TEAM draft data ----
        entry.setOrganizationName("CancelTest Org");
        entry.setGroupName("CancelTest Team");

        entry.enterNumberOfIndividuals2("2");

        String email1 = "cancel1+" + System.currentTimeMillis() + "@effectussoftware.com";
        String email2 = "cancel2+" + System.currentTimeMillis() + "@effectussoftware.com";

        entry.fillUserDetailsAtIndex(1, "John", "Cancel", email1);
        entry.fillUserDetailsAtIndex(2, "Jane", "Cancel", email2);

        // Trigger field-level validation so CTA reflects enabled state
        entry.triggerManualValidationBlurs(2);

        // Sanity: Proceed must be enabled before we hit Cancel
        Assert.assertTrue(
                entry.isProceedToPaymentEnabled(),
                "Proceed to payment should be enabled with valid draft data before canceling."
        );

        // ---- Step 3: Click CANCEL ----
        entry.clickCancel();   // Uses the @Step("Click Cancel") helper in AssessmentEntryPage

        // ---- Step 4: Assert we navigated back to the TTP shop route ----
        WebDriver driver = driver();
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.urlContains("/dashboard/shop/ttp"));

        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(
                currentUrl.contains("/dashboard/shop/ttp"),
                "Expected to be back on TTP recipient page after Cancel. Got URL: " + currentUrl
        );

        Assert.assertTrue(
                entry.isWhoIsThisPurchaseForTitleVisible(),
                "Expected 'Who is the purchase for?' title to be visible after navigating back."
        );

        // ---- Step 5: Re-enter TEAM manual-entry flow from TTP ----
        AssessmentEntryPage fresh = openTeamManualEntryPage();
        fresh.waitUntilLoaded();

        fresh.selectCreateNewTeam();
        fresh.selectManualEntry();

        // ---- Step 6: Verify no draft data persisted ----

        // Org & team names should be empty
        Assert.assertTrue(
                fresh.getOrganizationName().isBlank(),
                "Org name should be empty after Cancel (no draft persisted)."
        );

        Assert.assertTrue(
                fresh.getGroupName().isBlank(),
                "Team name should be empty after Cancel (no draft persisted)."
        );

        // Quantity should be back to default (1)
        Assert.assertEquals(
                fresh.getNumberOfIndividuals(),
                0,
                "Quantity should reset to default (0) after Cancel."
        );

        // O member row
        Assert.assertEquals(
                fresh.renderedEmailRows(),
                0,
                "Expected 0 member row after Cancel."
        );

        // Proceed CTA must be disabled on a fresh/empty form
        Assert.assertFalse(
                fresh.isProceedToPaymentEnabled(),
                "Proceed to payment should be DISABLED on a fresh TEAM manual-entry form after Cancel."
        );
    }


    @Test(description = "QASE 977: Price & total reflect quantity (TEAM manual-entry)")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("977")
    public void priceAndTotalReflectQuantity_teamFlow() {

        // ---- Flow A: Quantity = 1 ----
        BigDecimal totalForOne = createTeamOrderAndCapturePreviewTotal(1);

        Assert.assertTrue(
                totalForOne.compareTo(BigDecimal.ZERO) > 0,
                "Total for quantity=1 should be > 0. Got: " + totalForOne
        );

        // ---- Flow B: Quantity = 3 ----
        BigDecimal totalForThree = createTeamOrderAndCapturePreviewTotal(3);

        Assert.assertTrue(
                totalForThree.compareTo(BigDecimal.ZERO) > 0,
                "Total for quantity=3 should be > 0. Got: " + totalForThree
        );

        // ---- EXPECTATION: total scales linearly with quantity (3 × qty=1) ----
        BigDecimal expectedForThree = totalForOne.multiply(BigDecimal.valueOf(3L));

        Assert.assertEquals(
                totalForThree,
                expectedForThree,
                "Total for qty=3 should be exactly 3× the total for qty=1. "
                        + "qty=1 total=" + totalForOne + ", qty=3 total=" + totalForThree
        );


        // ---- Flow C: Quantity = 20 ----
        BigDecimal totalForTwenty= createTeamOrderAndCapturePreviewTotal(20);

        Assert.assertTrue(
                totalForThree.compareTo(BigDecimal.ZERO) > 0,
                "Total for quantity=20 should be > 0. Got: " + totalForTwenty
        );

        // ---- EXPECTATION: total scales linearly with quantity (3 × qty=1) ----
        BigDecimal expectedForTweny = totalForOne.multiply(BigDecimal.valueOf(20L));

        Assert.assertEquals(
                totalForTwenty,
                expectedForTweny,
                "Total for qty=20 should be exactly 20× the total for qty=1. "
                        + "qty=1 total=" + totalForOne + ", qty=20 total=" + totalForTwenty
        );
    }

    /**
     * Creates a TEAM manual-entry order with the given quantity,
     * goes to Order Preview, and returns the preview total.
     *
     * Side-effects:
     *  - Asserts that selected count in preview == quantity
     *  - Optionally can be extended to assert Stripe Checkout shows same amount
     */
    private BigDecimal createTeamOrderAndCapturePreviewTotal(int quantity) {

        // ---- Login + go to TEAM manual-entry ----
        AssessmentEntryPage entry = openTeamManualEntryPage();
        entry.waitUntilLoaded();

        // ---- TEAM + Manual Entry ----
        entry.selectCreateNewTeam();
        entry.selectManualEntry();

        // ---- Quantity = {quantity} ----
        entry.enterNumberOfIndividuals2(String.valueOf(quantity));

        // ---- Valid org + team names ----
        entry.setOrganizationName("Price Qty Org " + quantity);
        entry.setGroupName("Price Qty Team " + quantity);

        // ---- Fill all member rows ----
        long now = System.currentTimeMillis();
        for (int i = 1; i <= quantity; i++) {
            entry.fillUserDetailsAtIndex(
                    i,
                    "User" + i,
                    "PriceTest",
                    "priceqty+" + quantity + "-" + i + "+" + now + "@effectussoftware.com"
            );
        }

        // Trigger validation so CTA can become enabled
        entry.triggerManualValidationBlurs(quantity);

        // ---- Sanity: Proceed must be enabled ----
        Assert.assertTrue(
                entry.isProceedToPaymentEnabled(),
                "Proceed to payment should be enabled for quantity=" + quantity
                        + " when all rows + team/org are valid."
        );

        // ---- Go to Order Preview ----
        OrderPreviewPage preview = entry.clickProceedToPayment();
        preview.waitUntilLoaded();

        // ---- EXPECTATION #1: Selected count == quantity ----
        int selectedCount = preview.getSelectedCount();
        Assert.assertEquals(
                selectedCount,
                quantity,
                "Order Preview selected count should match quantity entered. "
                        + "Expected " + quantity + ", got " + selectedCount
        );

        // ---- EXPECTATION #2: Total on preview is > 0 ----
        BigDecimal total = preview.getTotal();
        Assert.assertTrue(
                total.compareTo(BigDecimal.ZERO) > 0,
                "Preview total should be > 0 for quantity=" + quantity + ". Got: " + total
        );

        /*
         * OPTIONAL (if/when you add a helper):
         *
         * BigDecimal stripeTotal = preview.proceedToStripeAndGetDisplayedTotal();
         * Assert.assertEquals(
         *         stripeTotal,
         *         total,
         *         "Stripe Checkout amount should match Order Preview total for qty=" + quantity
         * );
         */

        return total;
    }


    @Test(groups = {"known-bug"}, description = "QASE 978: Mobile viewport sanity for required fields")
    @Severity(SeverityLevel.NORMAL)
    @TmsLink("978")
    public void mobileViewportRequiredFields_teamFlow() {

        // --- Save/restore window size ---
        Dimension original = driver().manage().window().getSize();
        driver().manage().window().setSize(new Dimension(430, 860)); // mobile viewport

        try {
            // ---- Step 1: Enter TEAM manual-entry flow ----
            AssessmentEntryPage entry = openTeamManualEntryPage();
            entry.waitUntilLoaded();

            entry.selectCreateNewTeam();
            entry.selectManualEntry();

            // Quantity = 1
            entry.enterNumberOfIndividuals2("1");

            // Trigger field validations so required errors appear
            entry.triggerManualValidationBlurs(1);

            // ---- ASSERT 1: Proceed button must be disabled ----
            Assert.assertFalse(
                    entry.isProceedToPaymentEnabled(),
                    "Proceed must remain disabled at mobile size while required fields are empty."
            );

            // ---- ASSERT 2: Required errors EXIST and are VISIBLE ----
            int errorCount = entry.inlineRequiredErrorsCount();
            Assert.assertTrue(
                    errorCount > 0,
                    "Expected at least one visible required-field error in mobile viewport."
            );

            // ---- OPTIONAL ASSERT: Error elements are inside the viewport ----
            assertErrorsInsideViewport();

            // ---- Step 2: Fill all required fields validly ----
            long now = System.currentTimeMillis();
            entry.setOrganizationName("Mobile Org " + now);
            entry.setGroupName("Mobile Team " + now);
            entry.fillUserDetailsAtIndex(
                    1,
                    "Mobile",
                    "Tester",
                    "mobile+" + now + "@example.com"
            );

            entry.triggerManualValidationBlurs(1);

            // ---- ASSERT 3: CTA becomes enabled ----
            Assert.assertTrue(
                    entry.isProceedToPaymentEnabled(),
                    "Proceed should become enabled once all required fields are filled on mobile viewport."
            );

        } finally {
            // Restore original viewport
            driver().manage().window().setSize(original);
        }
    }

    private void assertErrorsInsideViewport() {
        JavascriptExecutor js = (JavascriptExecutor) driver();

        List<WebElement> errors = driver().findElements(
                By.cssSelector(".ant-form-item-explain-error, span[type='error']")
        );

        for (WebElement e : errors) {
            boolean inside = (Boolean) js.executeScript(
                    "const r = arguments[0].getBoundingClientRect();" +
                            "return r.bottom <= window.innerHeight && r.top >= 0;",
                    e
            );

            Assert.assertTrue(
                    inside,
                    "Inline error is outside viewport (mobile): " + e.getText()
            );
        }
    }


































}

