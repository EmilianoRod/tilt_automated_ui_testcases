package tests.shop;

import base.BaseTest;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import pages.BasePage;
import pages.Individuals.IndividualsPage;
import pages.Shop.AssessmentEntryPage;
import pages.Shop.OrderPreviewPage;
import pages.Shop.PurchaseInformation;
import pages.Shop.PurchaseRecipientSelectionPage;
import pages.menuPages.DashboardPage;
import pages.menuPages.ShopPage;

import java.math.BigDecimal;
import java.time.Duration;

import static Utils.WaitUtils.waitForLoadersToDisappear;
import static io.qameta.allure.Allure.step;





@Epic("Tilt – Purchases")
@Feature("TTP Assessment Purchase")
@Owner("Emiliano")
public class TtpRecipientSelectionTest extends BaseTest {





    @Test(groups = "ui-only", description = "Recipient selection: Next is disabled until a recipient is chosen; selecting 'Myself' enables it")
    @Severity(SeverityLevel.CRITICAL)
    @Story("TTP purchase – recipient selection must gate progress")
    public void cannotProceedWithoutSelection_thenSelectMyself() throws InterruptedException {
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Go to Shop and start purchase flow of TTP");
        ShopPage shopPage = dashboard.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");
        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt().waitUntilLoaded();

        step("Verify Next/Continue is disabled initially (with wait)");
        sel.waitUntilNextDisabled(Duration.ofSeconds(5));
        Assert.assertFalse(sel.isNextEnabled(), "Next should be disabled before selection");

        step("Select 'Myself' as purchase recipient");
        sel.selectMyself();

        step("Verify Next/Continue becomes enabled (with wait)");
        sel.waitUntilNextEnabled(Duration.ofSeconds(5));
        Assert.assertTrue(sel.isNextEnabled(), "Next should enable after selecting 'Myself'");

        step("Optionally click Next to ensure it’s clickable (but don’t assert the next page)");
        sel.clickNextCta();
        // Here we deliberately do NOT assert Purchase Information vs Subscription modal.
    }


    @DataProvider(name = "invalidEmails")
    public Object[][] invalidEmails() {
        return new Object[][]{
                {"plainaddress"},
                {"missing-at.com"},
                {"missing.domain@"},
                {"name@domain"},
                {"name@domain..com"},
                {"name@@domain.com"},
                {"name@.com"},
                {"name@domain com"},
        };
    }

    @Test(groups = "ui-only", dataProvider = "invalidEmails", description = "Invite New Individual – Invalid Email: inline validation blocks proceeding (Shop flow)")
    @Severity(SeverityLevel.NORMAL)
    @Story("TTP purchase – manual entry email validation")
    public void inviteNewIndividual_invalidEmail_blocksProceed(String badEmail) {

        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Go to Shop and start purchase flow of TTP");
        ShopPage shopPage = dashboard.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");

        PurchaseRecipientSelectionPage sel =
                shopPage.clickBuyNowForTrueTilt().waitUntilLoaded();

        step("Select Client / Individual and continue");
        sel.selectClientOrIndividual();
        sel.waitUntilNextEnabled(Duration.ofSeconds(5));
        sel.clickNextCta();

        step("Manual entry for 1 individual");
        AssessmentEntryPage entry = new AssessmentEntryPage(driver())
                .waitUntilLoaded()
                .selectManualEntry()
                .enterNumberOfIndividuals("1");

        step("Enter invalid email: " + badEmail);
        entry.fillUserDetailsAtIndex(1, "Emi", "Rod", badEmail);

        // Optional but very stable if validation is lazy
        entry.triggerManualValidationBlurs();

        step("Verify inline email validation error is shown");
        String emailError = entry.errorTextForEmail(1);

        Assert.assertNotNull(
                emailError,
                "❌ Expected inline validation error, but none was shown. Email=" + badEmail
        );
        Assert.assertFalse(
                emailError.isBlank(),
                "❌ Inline validation error was empty. Email=" + badEmail
        );

        System.out.println("[InvalidEmail] " + badEmail + " → error: " + emailError);

        step("Verify user cannot proceed to payment");
        Assert.assertFalse(
                entry.isProceedToPaymentEnabled(),
                "❌ Proceed to payment should be disabled for invalid email: " + badEmail
        );


    }


    @Test(groups = "ui-only", description = "TILT-439 – Invite New Individual with duplicate email shows Free Retake and total price = 0")
    @Severity(SeverityLevel.CRITICAL)
    @Story("TTP purchase – duplicate email triggers free retake flow")
    public void inviteNewIndividual_duplicateEmail_freeRetake() {

        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page and capture an existing email");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();

        String duplicateEmail = individuals.getFirstRowEmailOrThrow();
        Assert.assertNotNull(duplicateEmail, "❌ Could not find an existing email in Individuals list");
        Assert.assertFalse(duplicateEmail.isBlank(), "❌ Existing email is blank");

        System.out.println("[DuplicateEmail] Using existing email: " + duplicateEmail);

        step("Go to Shop and start TTP purchase flow");
        ShopPage shopPage = dashboard.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");

        PurchaseRecipientSelectionPage sel =
                shopPage.clickBuyNowForTrueTilt().waitUntilLoaded();

        step("Select Client / Individual and continue");
        sel.selectClientOrIndividual();
        sel.waitUntilNextEnabled(Duration.ofSeconds(5));
        sel.clickNextCta();

        step("Manual entry for 1 individual using duplicate email");
        AssessmentEntryPage entry = new AssessmentEntryPage(driver())
                .waitUntilLoaded()
                .selectManualEntry()
                .enterNumberOfIndividuals("1");

        entry.fillUserDetailsAtIndex(1, "Emi", "Rod", duplicateEmail);

        step("Proceed to payment (duplicate email should be allowed)");
        Assert.assertTrue(
                entry.isProceedToPaymentEnabled(),
                "❌ Proceed should be enabled for duplicate email (free retake case)"
        );

        OrderPreviewPage preview = entry.clickProceedToPayment().waitUntilLoaded();

        step("Verify 'Free Retake Available' is displayed");
        Assert.assertTrue(
                preview.hasFreeRetakeAvailable(),
                "❌ Free Retake Available message was not shown for duplicate email"
        );

        step("Verify total price is 0");
        Assert.assertTrue(preview.equalsMoney(preview.getTotal(), BigDecimal.ZERO),
                "❌ Total price is not 0 for free retake flow. Total=" + preview.getTotal());

    }
















}
