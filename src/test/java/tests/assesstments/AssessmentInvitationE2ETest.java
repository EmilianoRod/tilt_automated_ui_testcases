package tests.assesstments;

import Utils.MailSlurpUtils;
import base.BaseTest;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;
import pages.BasePage;
import pages.Shop.*;
import pages.menuPages.DashboardPage;
import pages.menuPages.ShopPage;


@Epic("Tilt – E2E")
@Feature("Purchase → Invite → Start Assessment")
public class AssessmentInvitationE2ETest extends BaseTest {

    @Test(groups = {"e2e", "shop", "email"})
    @Severity(SeverityLevel.CRITICAL)
    @Story("Purchase assessment, then start via invitation link, complete and submit")
    public void purchase_then_startAssessmentViaInvitationLink_then_submitSuccessfully() {

        // 1) Purchase flow in-app
        DashboardPage dash = startFreshSession(); // your helper

        ShopPage shop = dash.openShop();
        PurchaseRecipientSelectionPage rec = shop.openTtpPurchase(); // or openAgtPurchase()

        // choose "Invite new individual" etc.
        AssessmentEntryPage entry = rec.selectInviteNewIndividual();
        String invitedEmail = entry.fillInviteNewIndividualAndGetEmail(); // <-- you’ll implement or reuse

        OrderPreviewPage preview = entry.continueToOrderPreview().waitUntilLoaded();

        // IMPORTANT: totals assertion should use BigDecimal compare, not string/int equality
        // e.g. Assert.assertTrue(preview.getTotal().compareTo(BigDecimal.ZERO) == 0);

        BasePage next = preview.clickPrimaryPaymentCta(); // Stripe OR free-retake path
        // If Stripe: complete checkout (you already have StripeCheckoutHelper)
        // If free retake: it may skip Stripe and go straight to confirmation

        // 2) Wait for invitation email + extract link
        String inviteUrl = MailSlurpUtils.waitForLatestInviteLink(inboxId(), invitedEmail); // <-- helper you’ll add

        // 3) Start assessment via link
        driver().navigate().to(inviteUrl);

        AssessmentSurveyPage survey = new AssessmentSurveyPage(driver()).waitUntilLoaded();
        Assert.assertTrue(survey.isLoaded(), "❌ Survey did not load from invitation link");

        // 4) Complete + submit
        survey.completeAllQuestionsDefault();
        survey.submit();

        Assert.assertTrue(survey.isCompletionVisible(), "❌ Completion screen not visible");
    }
}

