package tests;

import base.BaseTest;

import Utils.Config;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import pages.BasePage;
import pages.LoginPage;
import pages.Shop.AssessmentEntryPage;
import pages.Shop.OrderPreviewPage;
import pages.Shop.PurchaseRecipientSelectionPage;
import pages.menuPages.DashboardPage;
import pages.menuPages.ShopPage;


import java.util.UUID;

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
    public void teamManualEntry_defaultState_requiredFields() {
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

}
