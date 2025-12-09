package tests.teams;


import Utils.Config;
import Utils.MailSlurpUtils;
import Utils.StripeCheckoutHelper;
import Utils.WaitUtils;
import base.BaseTest;
import com.mailslurp.models.Email;
import com.mailslurp.models.InboxDto;
import io.qameta.allure.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;
import pages.Individuals.IndividualsPage;
import pages.LoginPage;
import pages.Shop.AssessmentEntryPage;
import pages.Shop.OrderPreviewPage;
import pages.Shop.PurchaseInformation;
import pages.menuPages.DashboardPage;
import pages.teams.TeamDetailsPage;
import pages.teams.TeamsPage;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

import static Utils.Config.joinUrl;
import static Utils.WaitUtils.waitForLoadersToDisappear;
import static io.qameta.allure.Allure.step;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static tests.teams.TeamAssessmentPurchaseAndAssignment.extractSessionIdFromUrl;


@Epic("Team Assessment Purchase & Assignment")
@Feature("Add Team Member Flow (UX Fix Aug 2025)")
public class AddTeamMemberFlows extends BaseTest {



    @Test(description = "TC1 – Open Add Team Member modal from Team screen")
    @Severity(SeverityLevel.CRITICAL)
    @Story("TILT-739: Open Add Team Member from team’s Members page")
    public void openAddTeamMemberModal_fromTeamDetails() {

        // 1) Login → Dashboard
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        DashboardPage dashboard = login.login(Config.getAdminEmail(), Config.getAdminPassword());
        assertTrue(dashboard.isLoaded(), "Dashboard did not load after login");

        // 2) Navigate to Teams
        TeamsPage teams = dashboard.goToTeams();

        // 3) Open a specific team (first one or by name)
        //    Assuming we click the first team for now
        TeamDetailsPage teamDetails = teams.openTeamAtRow(1);

        assertTrue(teamDetails.isLoaded(),
                "TeamDetailsPage should be fully loaded before interacting.");

        // 4) Open Add Team Member modal
        teamDetails.openAddTeamMemberModal();

        // ASSERTIONS ----------------------------------------------

        // A) Modal is open
        assertTrue(teamDetails.isAddTeamMemberModalOpen(),
                "Add Team Member modal should be open.");

        // B) Search existing user input is visible
        assertTrue(teamDetails.isSearchExistingUserInputVisible(),
                "Search existing user field should be visible.");

        // C) Create New User CTA visible
        assertTrue(teamDetails.isAddMemberButtonVisible(),
                "'Create New User' button should appear.");

        // D) Add Member button should be disabled at this point (no selection yet)
        assertFalse(teamDetails.isAddMemberButtonEnabled(),
                "'Add Member' should be disabled before selecting a user.");

        // E) Team context preserved: still on /dashboard/teams/{id}
        assertTrue(driver().getCurrentUrl().contains("/dashboard/teams/"),
                "User should remain inside the selected team's context.");

        // F) Additional – modal should NOT show product selection step yet
        assertFalse(teamDetails.isProductSelectionStepVisible(),
                "Product selection cards should NOT be visible in step 1.");

    }


    @Test(description = "TC2 – Search finds existing user (no payment path)")
    @Severity(SeverityLevel.CRITICAL)
    @Story("TILT-740: Search finds existing user without purchase path")
    public void searchExistingUser_addToTeam_withoutPurchaseFlow() throws InterruptedException {

        final String EXISTING_USER_EMAIL = Config.getExistingTeamUserEmail();
        final String teamName = "AutoTeam-" + System.currentTimeMillis();
        final String baseFirst  = "Auto";
        final String baseLast   = "Member";
        final String baseEmail  = "auto+" + System.currentTimeMillis() + "@test.com";

        // 1) Create a fresh team using shop flow
        TeamDetailsPage teamDetails = createTeamViaShopFlow(teamName, baseFirst, baseLast, baseEmail);
        assertTrue(teamDetails.isLoaded(), "TeamDetailsPage did not load after team creation");

        // 2) Open Add Team Member modal
        teamDetails.openAddTeamMemberModal();
        assertTrue(teamDetails.isAddTeamMemberModalOpen(),
                "Add Team Member modal should be open.");

        // Sanity checks
        assertFalse(teamDetails.isAddMemberButtonEnabled());
        assertFalse(teamDetails.isProductSelectionStepVisible());

        // 3) Search existing user
        teamDetails
                .typeInExistingUserSearch(EXISTING_USER_EMAIL)
                .selectFirstExistingUserFromResults();

        // 4) Assert enabled (existing-user, no purchase)
        assertTrue(teamDetails.isAddMemberButtonEnabled(),
                "'Add Member' must be enabled after selecting an existing user.");

        assertTrue(teamDetails.isProductSelectionStepVisible(),
                "Product selection must be visible for existing users with a retake available.");

        assertTrue(teamDetails.hasAnyRetakeAvailableProduct(),
                "At least one product card (TTP or AGT) must show 'Retake available' for this user.");


        // 5) Add member
        teamDetails.clickModalAddMemberOrUser();
        teamDetails.waitForAddMemberModalToClose();
        assertFalse(teamDetails.isAddTeamMemberModalOpen(),
                "Modal should close after clicking Add Member.");

        // 6) Assert URL stays in team context
        String url = driver().getCurrentUrl();
        assertTrue(url.contains("/dashboard/teams/"), "Should remain in team details.");
        assertFalse(url.contains("/shop"), "Should NOT redirect to Shop purchase.");

        // 7) Validate member appears in table
        teamDetails.waitForMemberByEmail(EXISTING_USER_EMAIL, Duration.ofSeconds(20));
    }


    @Test(groups = {"teams", "add-member"})
    @Severity(SeverityLevel.CRITICAL)
    @Story("Existing user WITH active TTP/AGT or WITH retake → no purchase")
    public void testExistingUserWithActiveOrRetake_NoPurchase() throws Exception {
        // --- 0) Test data: existing user who already has TTP/AGT or a retake ---
//        final String existingUserEmail = Config.getAny(
//                "existingUserWithRetake.email",
//                "EXISTING_USER_WITH_RETAKE_EMAIL"
//        );
//        final String existingUserName = Config.getAny(
//                "existingUserWithRetake.name",
//                "EXISTING_USER_WITH_RETAKE_NAME"
//        );

        final String existingUserEmail = "erodriguez@effectussoftware.com";
        final String existingUserName = "Emiliano Rodriguez Tejera";

        Assert.assertNotNull(existingUserEmail, "Config missing existingUserWithRetake.email");
        Assert.assertFalse(existingUserEmail.isBlank(), "existingUserWithRetake.email is blank");
        Assert.assertNotNull(existingUserName, "Config missing existingUserWithRetake.name");
        Assert.assertFalse(existingUserName.isBlank(), "existingUserWithRetake.name is blank");

        // --- 1) Precondition: be on a valid TeamDetailsPage ready to add member ---
        // Plug here whatever helper you already use (e.g. createTeamViaShopFlow(...) or open existing team)
        TeamDetailsPage teamDetails = createTeamViaShopFlow(
                "UXFixRetakeTeam-" + System.currentTimeMillis(),
                "Base",
                "User",
                "base+" + System.currentTimeMillis() + "@example.com"
        ).waitUntilLoaded();

        Assert.assertTrue(teamDetails.isLoaded(), "Team Details page did not load.");

        // --- 2) Open "Add Team Member" modal ---
        teamDetails.openAddTeamMemberModal();
        Assert.assertTrue(teamDetails.isAddTeamMemberModalOpen(),
                "Add Team Member modal should be open.");

        Assert.assertTrue(teamDetails.isSearchExistingUserInputVisible(),
                "Search existing user input should be visible.");

        // --- 3) Search and select existing user with retake ---
        teamDetails
                .typeInExistingUserSearch(existingUserName)
                .selectFirstExistingUserFromResults();

        // Product-selection step *should* be visible for this UX
        Assert.assertTrue(teamDetails.isProductSelectionStepVisible(),
                "Product selection step should be visible for existing user with retake.");

        // --- 4) Assert “Retake available” in at least one product card ---
        boolean anyRetake = teamDetails.hasAnyRetakeAvailableProduct();
        Assert.assertTrue(anyRetake,
                "Expected 'Retake available' badge in either TTP or AGT product card.");


        // --- 5) No purchase flow (no Continue to purchase / Checkout) ---
        Assert.assertFalse(
                teamDetails.isContinueToPurchaseVisible(),
                "No 'Continue to purchase' button should be visible for existing user with retake."
        );

        // --- 6) Confirm: clicking Add Member adds user directly, no Shop/Stripe ---
        teamDetails.clickModalAddMemberOrUser();
        teamDetails.waitForAddMemberModalToClose();

        Assert.assertFalse(teamDetails.isAddTeamMemberModalOpen(),
                "Modal should be closed after clicking Add Member.");

        String currentUrl = driver().getCurrentUrl();
        Assert.assertFalse(
                currentUrl.contains("/shop/"),
                "Should NOT navigate to Shop for existing user with retake. URL was: " + currentUrl
        );
        Assert.assertTrue(teamDetails.isLoaded(), "Team Details page should still be loaded.");

        // --- 7) Verify member appears in the team table ---
        teamDetails.waitForMemberByEmail(existingUserEmail, Duration.ofSeconds(20));

        // --- 7b) Verify the member row shows at least one report link (TTP or AGT) ---
        Assert.assertTrue(
                teamDetails.memberRowHasAnyReportLink(existingUserEmail),
                "Expected at least one report link (TTP or AGT) in the row for " + existingUserEmail
        );

        // If wwe later add status column helpers, we can assert “Pending” or similar:
        // String status = teamDetails.getMemberStatusByEmail(existingUserEmail);
        // Assert.assertEquals(status, "Pending", "Unexpected status for member with retake.");
    }


    @Test(groups = {"teams", "add-member"})
    @Severity(SeverityLevel.CRITICAL)
    @Story("TC4 – Existing user WITH retake available → no purchase")
    public void testExistingUserWithRetakeAvailable_NoPurchase() throws Exception {
        // --- 0) Test data: existing user who specifically has a RETAKE available ---
        // You can keep hard-coded for now, or swap to config keys like:
        // existingUserWithRetakeOnly.email / existingUserWithRetakeOnly.name
        final String existingUserEmail = "erodriguez@effectussoftware.com";
        final String existingUserName  = "Emiliano Rodriguez Tejera";

        Assert.assertNotNull(existingUserEmail, "Config missing existingUserWithRetake.email");
        Assert.assertFalse(existingUserEmail.isBlank(), "existingUserWithRetake.email is blank");
        Assert.assertNotNull(existingUserName, "Config missing existingUserWithRetake.name");
        Assert.assertFalse(existingUserName.isBlank(), "existingUserWithRetake.name is blank");

        // --- 1) Precondition: be on a fresh TeamDetailsPage ready to add member ---
        TeamDetailsPage teamDetails = createTeamViaShopFlow(
                "UXFixRetakeOnlyTeam-" + System.currentTimeMillis(),
                "Base",
                "User",
                "base+" + System.currentTimeMillis() + "@example.com"
        ).waitUntilLoaded();

        Assert.assertTrue(teamDetails.isLoaded(), "Team Details page did not load.");

        // --- 2) Open "Add Team Member" modal ---
        teamDetails.openAddTeamMemberModal();
        Assert.assertTrue(
                teamDetails.isAddTeamMemberModalOpen(),
                "Add Team Member modal should be open."
        );
        Assert.assertTrue(
                teamDetails.isSearchExistingUserInputVisible(),
                "Search existing user input should be visible."
        );

        // --- 3) Search and select the existing user with RETAKE entitlement ---
        teamDetails
                .typeInExistingUserSearch(existingUserName)
                .selectFirstExistingUserFromResults();

        // Product-selection step MUST be visible for this UX
        Assert.assertTrue(
                teamDetails.isProductSelectionStepVisible(),
                "Product selection step should be visible for existing user with retake."
        );

        // --- 4) Assert “Retake available” is shown in at least one product card ---
        boolean retakeTtp = teamDetails.hasRetakeAvailableForTTP();
        boolean retakeAgt = teamDetails.hasRetakeAvailableForAGT();
        boolean anyRetake = teamDetails.hasAnyRetakeAvailableProduct();

        Assert.assertTrue(
                anyRetake,
                "Expected 'Retake available' badge in at least one product card (TTP or AGT)."
        );

        // debug log via TestNG / logger
         logger.info("[TC4] RetakeAvailable → TTP={} | AGT={}", retakeTtp, retakeAgt);

        // --- 5) No purchase flow (no 'Continue to purchase' / no Shop/Stripe) ---
        Assert.assertFalse(
                teamDetails.isContinueToPurchaseVisible(),
                "No 'Continue to purchase' button should be visible for existing user with retake."
        );

        // --- 6) Clicking Add Member adds user directly, without going to Shop ---
        teamDetails.clickModalAddMemberOrUser();
        teamDetails.waitForAddMemberModalToClose();

        Assert.assertFalse(
                teamDetails.isAddTeamMemberModalOpen(),
                "Modal should be closed after clicking Add Member."
        );

        String currentUrl = driver().getCurrentUrl();
        Assert.assertFalse(
                currentUrl.contains("/shop/"),
                "Should NOT navigate to Shop for existing user with retake. URL was: " + currentUrl
        );
        Assert.assertTrue(teamDetails.isLoaded(), "Team Details page should still be loaded.");

        // --- 7) Verify member appears in the team table ---
        teamDetails.waitForMemberByEmail(existingUserEmail, Duration.ofSeconds(20));

        // --- 8) Verify that user’s row shows at least one report link (TTP or AGT) ---
        Assert.assertTrue(
                teamDetails.memberRowHasAnyReportLink(existingUserEmail),
                "Expected at least one report link (TTP or AGT) in the row for " + existingUserEmail
        );

        // scenario is specifically TTP-retake, you can tighten it to:
        // Assert.assertTrue(
        //         teamDetails.memberRowHasTtpReportLink(existingUserEmail),
        //         "Expected a TTP report link in the row for " + existingUserEmail
        // );

        // --- 9) No reminder auto-sent ---
        // UI doesn’t currently expose “no reminder sent” explicitly.
        // TODO (future): once we have reminder logs / email hooks for this flow,
        //  assert that no reminder email is triggered on add (only manual reminders later).
    }


    @Test(groups = {"teams", "add-member"})
    @Severity(SeverityLevel.CRITICAL)
    @Story("TC5 – New user WITHOUT TTP/AGT/retake → payment required")
    public void testNewUserWithoutEntitlement_PaymentRequired() throws Exception {

        String uniq = String.valueOf(System.currentTimeMillis());
        final String teamName = "UXFixPaymentReq-" + uniq;
        final String newFirst = "UXFixNew";
        final String newLast  = "Member";
        final String newEmail = "uxfix-new-" + uniq + "@example.com";

        // 1) Precondition: be on a valid TeamDetailsPage
        TeamDetailsPage teamDetails = createTeamViaShopFlow(
                teamName,
                "Base",
                "User",
                "base+" + uniq + "@example.com"
        ).waitUntilLoaded();

        Assert.assertTrue(teamDetails.isLoaded(), "Team Details page did not load.");

        // 2) Open “Add Team Member” modal
        teamDetails.openAddTeamMemberModal();
        Assert.assertTrue(teamDetails.isAddTeamMemberModalOpen(),
                "Add Team Member modal should be open.");

        // 3) Create NEW user (no prior entitlements)
        teamDetails
                .clickModalCreateNewUser()
                .fillModalNewUser(newFirst, newLast, newEmail)
                .clickModalContinue();

        // 4) Product-selection step must appear
        Assert.assertTrue(teamDetails.isProductSelectionStepVisible(),
                "Product selection must be visible for a brand-new user.");

        // For this test we just buy TTP (could be AGT as well)
        teamDetails.selectTrueTiltProductForMember();

        // 5) “Continue to purchase” MUST be visible → payment required
        Assert.assertTrue(
                teamDetails.isContinueToPurchaseVisible(),
                "'Continue to purchase' must be visible for new user without entitlements."
        );

        // ---- 6) Go to Shop → proceed to Stripe checkout ----
        teamDetails.clickContinueToPurchase();

        // a) Wait until we are on the Shop purchase page
        new WebDriverWait(driver(), Duration.ofSeconds(20))
                .until(ExpectedConditions.urlContains("/dashboard/shop/"));
        AssessmentEntryPage assessmentEntryPage = new AssessmentEntryPage(driver()).waitUntilLoaded();

        // b) Click “Proceed to payment” → Order Preview
        OrderPreviewPage orderPreviewPage = assessmentEntryPage
                .clickProceedToPayment()
                .waitUntilLoaded();

        // c) Stripe: fetch Checkout URL + session + metadata.body, then trigger completed
        step("Stripe: fetch session + metadata.body");
        String stripeUrl = orderPreviewPage.proceedToStripeAndGetCheckoutUrl();
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

        // d) Simulate Stripe redirect back to Tilt
        driver().navigate().to(joinUrl(Config.getBaseUrl(), "/dashboard/orders/confirmation"));

        // ---- 7) Follow real UX back to the team (Dashboard → Teams → Team details) ----
        driver().navigate().to(joinUrl(Config.getBaseUrl(), "/dashboard"));
        DashboardPage dashboardAfter = new DashboardPage(driver()).waitUntilLoaded();
        Assert.assertTrue(dashboardAfter.isLoaded(), "Dashboard should be loaded after returning from confirmation.");

        TeamsPage teamsPage = dashboardAfter.goToTeams().waitUntilLoaded();
        teamsPage.openTeamDetails(teamName);

        TeamDetailsPage postPayTeam = new TeamDetailsPage(driver()).waitUntilLoaded();
        Assert.assertTrue(postPayTeam.isLoaded(), "Team Details page should be loaded after navigating from Teams.");

        // New member should appear in the table
        postPayTeam.waitForMemberByEmail(newEmail, Duration.ofSeconds(30));

        String status = postPayTeam.getMemberStatusByEmail(newEmail);
        Assert.assertTrue(
                status.equalsIgnoreCase("Pending"),
                "Expected status 'Pending' after purchase, but got: " + status
        );

    }



    @Test(groups = {"teams", "add-member"})
    @Severity(SeverityLevel.CRITICAL)
    @Story("TC6 – New user (not in system) → payment required")
    public void testNewUserNotInSystem_PaymentRequired() throws Exception {

        String uniq = String.valueOf(System.currentTimeMillis());
        final String newFirst = "TC6New";
        final String newLast  = "Member";
        final String newEmail = "tc6-new-" + uniq + "@example.com";

        // --- 1) Precondition: open a fresh team that can receive members ---
        TeamDetailsPage teamDetails = createTeamViaShopFlow(
                "TC6Team-" + uniq,
                "Base",
                "User",
                "base+" + uniq + "@example.com"
        ).waitUntilLoaded();

        Assert.assertTrue(teamDetails.isLoaded(), "Team Details page did not load.");

        // --- 2) Open Add Team Member modal ---
        teamDetails.openAddTeamMemberModal();
        Assert.assertTrue(teamDetails.isAddTeamMemberModalOpen(),
                "Add Team Member modal should be open.");

        // --- 3) CREATE A NEW USER (user NOT in system) ---
        teamDetails
                .clickModalCreateNewUser()
                .fillModalNewUser(newFirst, newLast, newEmail)
                .clickModalContinue();

        // --- 4) Product selection appears (new user has no entitlements) ---
        Assert.assertTrue(teamDetails.isProductSelectionStepVisible(),
                "Product selection must be visible for a brand-new user.");

        // Choose TTP for this test (AGT would also work)
        teamDetails.selectTrueTiltProductForMember();

        // Must show the Continue to purchase CTA
        Assert.assertTrue(teamDetails.isContinueToPurchaseVisible(),
                "'Continue to purchase' must be visible for new user.");

        // --- 5) Enter purchase flow ---
        teamDetails.clickContinueToPurchase();

        // Wait for Assessment Entry Page (shop flow)
        new WebDriverWait(driver(), Duration.ofSeconds(20))
                .until(ExpectedConditions.urlContains("/dashboard/shop/"));

        AssessmentEntryPage entry = new AssessmentEntryPage(driver());
        OrderPreviewPage preview = entry.clickProceedToPayment().waitUntilLoaded();

        // --- 6) Stripe Checkout ---
        String stripeUrl = preview.proceedToStripeAndGetCheckoutUrl();
        String sessionId = extractSessionIdFromUrl(stripeUrl);
        Assert.assertNotNull(sessionId, "Could not parse Stripe session id.");

        String bodyJson = StripeCheckoutHelper.fetchCheckoutBodyFromStripe(sessionId);
        Assert.assertNotNull(bodyJson, "metadata.body missing from Checkout Session.");

        StripeCheckoutHelper.triggerCheckoutCompletedWithBody(bodyJson);

        // Simulate Stripe redirect
        driver().navigate().to(joinUrl(Config.getBaseUrl(), "/dashboard/orders/confirmation"));

        // --- 7) Navigate to the team through Dashboard (NOT direct URL) ---
        DashboardPage dash = new DashboardPage(driver())
                .open(Config.getBaseUrl())
                .waitUntilLoaded();

        TeamsPage teamsPage = dash.goToTeams().waitUntilLoaded();
        teamsPage.openTeamDetails("TC6Team-" + uniq);

        TeamDetailsPage postPay = new TeamDetailsPage(driver()).waitUntilLoaded();

        // --- 8) Validate the member is added ---
        postPay.waitForMemberByEmail(newEmail, Duration.ofSeconds(30));

        // --- 9) Validate subscription exists: status should be PENDING ---
        String status = postPay.getMemberStatusByEmail(newEmail);
        Assert.assertTrue(
                status.equalsIgnoreCase("Pending"),
                "Expected status 'Pending' for newly purchased user, got: " + status
        );

        // No report link should be available yet
        Assert.assertFalse(
                postPay.memberRowHasAnyReportLink(newEmail),
                "Newly purchased user should NOT have a report link yet (must complete assessment)."
        );
    }


    @Test(groups = {"teams", "add-member"})
    @Severity(SeverityLevel.CRITICAL)
    @Story("TC7 – Mixed add (some entitled, some not)")
    public void testMixedAdd_EntitledAndUnentitled() throws Exception {

        String uuid = String.valueOf(System.currentTimeMillis());

        // -------------------------------------
        // Test Data
        // -------------------------------------

        // 1) Existing user WITH entitlement
        final String entitledName  = "Emiliano Rodriguez Tejera";
        final String entitledEmail = "erodriguez@effectussoftware.com";

        // 2) Brand new user WITHOUT entitlement
        final String newFirst = "MixedNew";
        final String newLast  = "User";
        final String newEmail = "mixed-no-ent-" + uuid + "@example.com";

        // -------------------------------------
        // 0) Precondition → Create a fresh team
        // -------------------------------------
        TeamDetailsPage teamDetails = createTeamViaShopFlow(
                "MixedTeam-" + uuid,
                "Base",
                "User",
                "base+" + uuid + "@example.com"
        ).waitUntilLoaded();

        Assert.assertTrue(teamDetails.isLoaded(), "Team page not loaded.");

        // -------------------------------------
        // 1) Open Add Team Member modal
        // -------------------------------------
        teamDetails.openAddTeamMemberModal();
        Assert.assertTrue(teamDetails.isAddTeamMemberModalOpen());

        // -------------------------------------
        // 2) Select the ENTITLED existing user
        // -------------------------------------
        teamDetails
                .typeInExistingUserSearch(entitledName)
                .selectFirstExistingUserFromResults();

        // DO NOT CLICK CONTINUE YET → We want MULTI-SELECTION

        // 2b) Click in the cancel button two times
        System.out.println("test1");
        teamDetails.clickModalCancel1();
        teamDetails.clickModalCancel1();
        System.out.println("test2");

        teamDetails.openAddTeamMemberModal();



        // -------------------------------------
        // 3) Create NEW (unentitled) user
        // -------------------------------------
        teamDetails
                .clickModalCreateNewUser()
                .fillModalNewUser(newFirst, newLast, newEmail)
                .clickModalContinue();

        // -------------------------------------
        // 4) Product-selection should appear for NEW USER ONLY
        // -------------------------------------
        Assert.assertTrue(
                teamDetails.isProductSelectionStepVisible(),
                "Product selection must show for UNENTITLED user, not for entitled one."
        );

        // Choose TTP for the new user
        teamDetails.selectTrueTiltProductForMember();

        Assert.assertTrue(
                teamDetails.isContinueToPurchaseVisible(),
                "'Continue to purchase' must appear since at least ONE user has no entitlements."
        );

        // -------------------------------------
        // 5) Continue to Purchase → Stripe
        // -------------------------------------
        teamDetails.clickContinueToPurchase();

        new WebDriverWait(driver(), Duration.ofSeconds(20))
                .until(ExpectedConditions.urlContains("/dashboard/shop/"));

        AssessmentEntryPage entry = new AssessmentEntryPage(driver());
        OrderPreviewPage preview = entry.clickProceedToPayment().waitUntilLoaded();

        // Stripe session
        String checkoutUrl = preview.proceedToStripeAndGetCheckoutUrl();
        String sessionId = extractSessionIdFromUrl(checkoutUrl);
        Assert.assertNotNull(sessionId);

        String bodyJson = StripeCheckoutHelper.fetchCheckoutBodyFromStripe(sessionId);
        Assert.assertNotNull(bodyJson);

        StripeCheckoutHelper.triggerCheckoutCompletedWithBody(bodyJson);

        // Simulate redirect
        driver().navigate().to(joinUrl(Config.getBaseUrl(), "/dashboard/orders/confirmation"));

        // -------------------------------------
        // 6) Back → Dashboard → Teams → Team details
        // -------------------------------------
        DashboardPage dash = new DashboardPage(driver())
                .open(Config.getBaseUrl())
                .waitUntilLoaded();

        TeamsPage teams = dash.goToTeams().waitUntilLoaded();
        teams.openTeamDetails("MixedTeam-" + uuid);

        TeamDetailsPage teamPostPay = new TeamDetailsPage(driver()).waitUntilLoaded();

        // -------------------------------------
        // 7) VALIDATE RESULTS
        // -------------------------------------

        // New (unentitled) user must be in the table
        teamPostPay.waitForMemberByEmail(newEmail, Duration.ofSeconds(30));

        // Should be in Pending state
        String newUserStatus = teamPostPay.getMemberStatusByEmail(newEmail);
        Assert.assertEquals(
                newUserStatus, "Pending",
                "New user (unentitled) must appear as 'Pending' after purchase."
        );

        // And should NOT yet have a completed report link
        Assert.assertFalse(
                teamPostPay.memberRowHasAnyReportLink(newEmail),
                "New user should not have completed report links until they take the assessment."
        );
    }


    @Test(groups = {"teams", "add-member"})
    @Severity(SeverityLevel.CRITICAL)
    @Story("TC9 – Prevent “ghost TTP” on add without purchase")
    public void testPreventGhostTtpOnAddWithoutPurchase() throws Exception {

        // --- 0) Test data: existing user WITH entitlement (TTP/AGT or retake) ---
        final String existingUserEmail = Config.getAny(
                "existingUserWithRetake.email",
                "EXISTING_USER_WITH_RETAKE_EMAIL"
        );
        final String existingUserName = Config.getAny(
                "existingUserWithRetake.name",
                "EXISTING_USER_WITH_RETAKE_NAME"
        );

        Assert.assertNotNull(existingUserEmail, "Config missing existingUserWithRetake.email");
        Assert.assertFalse(existingUserEmail.isBlank(), "existingUserWithRetake.email is blank");
        Assert.assertNotNull(existingUserName, "Config missing existingUserWithRetake.name");
        Assert.assertFalse(existingUserName.isBlank(), "existingUserWithRetake.name is blank");

        final String baseUrl = Config.getBaseUrl();

        // --- 1) BASELINE: Individuals – capture report status BEFORE adding to team ---

        IndividualsPage individualsBefore = new IndividualsPage(driver())
                .open(baseUrl); // this already waits until loaded

        // Status could be "Pending", "Link:/assess/ttp/...", etc.
        String statusBefore = individualsBefore.getReportStatusByEmail(existingUserEmail);
        Assert.assertNotNull(statusBefore,
                "Precondition failed: no report status row found for entitled user: " + existingUserEmail);
        int rowsBefore = individualsBefore.countRowsByEmail(existingUserEmail);
        Assert.assertTrue(rowsBefore >= 1,
                "Precondition failed: expected at least one row for entitled user in Individuals.");

        // --- 2) Create a fresh team and add this entitled user WITHOUT purchase ---
        String uniq = String.valueOf(System.currentTimeMillis());
        String teamName = "UXFixNoGhost-" + uniq;

        TeamDetailsPage teamDetails = createTeamViaShopFlow(
                teamName,
                "Base",
                "User",
                "base+" + uniq + "@example.com"
        ).waitUntilLoaded();

        Assert.assertTrue(teamDetails.isLoaded(), "Team Details page did not load.");

        // Open Add Team Member
        teamDetails.openAddTeamMemberModal();
        Assert.assertTrue(teamDetails.isAddTeamMemberModalOpen(),
                "Add Team Member modal should be open.");

        // Search for the entitled existing user
        teamDetails
                .typeInExistingUserSearch(existingUserName)
                .selectFirstExistingUserFromResults();

        // Product-selection step should appear for this UX
        Assert.assertTrue(
                teamDetails.isProductSelectionStepVisible(),
                "Product selection step should be visible for existing entitled user."
        );

        // For an entitled user, at least one product should show "Retake available"
        Assert.assertTrue(
                teamDetails.hasAnyRetakeAvailableProduct(),
                "Expected at least one 'Retake available' chip for entitled user."
        );

        // No "Continue to purchase" → no payment path for this scenario
        Assert.assertFalse(
                teamDetails.isContinueToPurchaseVisible(),
                "Entitled user must NOT see 'Continue to purchase' (no payment required)."
        );

        // Click Add Member → user should be added directly, modal closed, no Shop nav
        teamDetails.clickModalAddMemberOrUser();
        teamDetails.waitForAddMemberModalToClose();

        Assert.assertFalse(teamDetails.isAddTeamMemberModalOpen(),
                "Modal should be closed after clicking Add Member for entitled user.");

        String currentUrl = driver().getCurrentUrl();
        Assert.assertFalse(
                currentUrl.contains("/shop/"),
                "Must NOT navigate to Shop for entitled user. URL was: " + currentUrl
        );

        // User should now appear in this team
        teamDetails.waitForMemberByEmail(existingUserEmail, Duration.ofSeconds(20));

        // --- 3) POST-CONDITION: Individuals – verify NO new TTP/AGT ("no ghost") ---

        IndividualsPage individualsAfter = new IndividualsPage(driver())
                .open(baseUrl);

        String statusAfter = individualsAfter.getReportStatusByEmail(existingUserEmail);
        Assert.assertNotNull(statusAfter,
                "After add-to-team, report status disappeared for entitled user: " + existingUserEmail);

        int rowsAfter = individualsAfter.countRowsByEmail(existingUserEmail);

        // a) Status MUST be identical → we didn't create a new TTP/AGT record
        Assert.assertEquals(
                statusAfter,
                statusBefore,
                "Report status changed after adding entitled user to team (ghost TTP/AGT created?)."
        );

        // b) Row count MUST be the same → no duplicate row for that email
        Assert.assertEquals(
                rowsAfter,
                rowsBefore,
                "Row count for entitled user changed after add – possible 'ghost' TTP/AGT record."
        );
    }


    @Test(groups = {"teams", "add-member"})
    @Severity(SeverityLevel.CRITICAL)
    @Story("TC10 – Payment success → assessment + email behavior")
    public void testPaymentSuccessCreatesAssessmentAndEmail() throws Exception {

        String uuid = String.valueOf(System.currentTimeMillis());

        // -------------------------------------
        // 0) MailSlurp + email config
        // -------------------------------------
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }

        final Duration EMAIL_TIMEOUT = Duration.ofSeconds(120);
        final String CTA_TEXT       = "Accept Assessment";
        final String SUBJECT_NEEDLE = "assessment";

        // Prefer suite-shared inbox
        final InboxDto inbox = BaseTest.getSuiteInbox() != null
                ? BaseTest.getSuiteInbox()
                : BaseTest.requireInboxOrSkip();

        final String recipientEmail = MailSlurpUtils.uniqueAliasEmail(inbox, "add-member-pay-" + uuid);
        final String aliasToken     = MailSlurpUtils.extractAliasToken(recipientEmail);

        System.out.println("📮 Using shared inbox: " + inbox.getId() + " <" + inbox.getEmailAddress() + ">");
        System.out.println("📧 Test recipient (alias): " + recipientEmail);

        // Clean inbox to avoid old noise
        try {
            MailSlurpUtils.clearInboxEmails(inbox.getId());
        } catch (Throwable ignored) {}

        final String teamName  = "UXFixPayEmail-" + uuid;
        final String baseEmail = "base+" + uuid + "@example.com";

        final String newFirst = "Paid";
        final String newLast  = "Member";

        // -------------------------------------
        // 1) Precondition → Create a fresh team
        // -------------------------------------
        TeamDetailsPage teamDetails = createTeamViaShopFlow(
                teamName,
                "Base",
                "User",
                baseEmail
        ).waitUntilLoaded();

        Assert.assertTrue(teamDetails.isLoaded(), "Team Details page did not load for newly created team.");

        // -------------------------------------
        // 2) Open Add Team Member modal & create brand-new user
        // -------------------------------------
        teamDetails.openAddTeamMemberModal();
        Assert.assertTrue(
                teamDetails.isAddTeamMemberModalOpen(),
                "Add Team Member modal should be open."
        );

        teamDetails
                .clickModalCreateNewUser()
                .fillModalNewUser(newFirst, newLast, recipientEmail)
                .clickModalContinue();

        // Product-selection step MUST appear for a new user without entitlements
        Assert.assertTrue(
                teamDetails.isProductSelectionStepVisible(),
                "Product selection must be visible for a brand-new user."
        );

        // For TC10 we pick TTP (a product that should send an assessment email)
        teamDetails.selectTrueTiltProductForMember();

        Assert.assertTrue(
                teamDetails.isContinueToPurchaseVisible(),
                "'Continue to purchase' must appear since the new user has no entitlements."
        );

        // -------------------------------------
        // 3) Continue to Purchase → Stripe (same flow as TC7)
        // -------------------------------------
        teamDetails.clickContinueToPurchase();

        new WebDriverWait(driver(), Duration.ofSeconds(20))
                .until(ExpectedConditions.urlContains("/dashboard/shop/"));

        AssessmentEntryPage entry = new AssessmentEntryPage(driver());
        OrderPreviewPage preview  = entry.clickProceedToPayment().waitUntilLoaded();

        Assert.assertTrue(preview.isLoaded(), "Order Preview page did not load after Proceed to payment.");

        // Stripe session
        String checkoutUrl = preview.proceedToStripeAndGetCheckoutUrl();
        String sessionId   = extractSessionIdFromUrl(checkoutUrl);
        Assert.assertNotNull(sessionId, "❌ Could not parse Stripe session id from URL");

        String bodyJson = StripeCheckoutHelper.fetchCheckoutBodyFromStripe(sessionId);
        Assert.assertNotNull(bodyJson, "❌ metadata.body not found in Checkout Session");

        StripeCheckoutHelper.triggerCheckoutCompletedWithBody(bodyJson);

        // Simulate redirect
        driver().navigate().to(joinUrl(Config.getBaseUrl(), "/dashboard/orders/confirmation"));

        // -------------------------------------
        // 4) Back → Dashboard → Teams → Team details
        // -------------------------------------
        DashboardPage dash = new DashboardPage(driver())
                .open(Config.getBaseUrl())
                .waitUntilLoaded();

        Assert.assertTrue(dash.isLoaded(), "Dashboard did not load after payment confirmation.");

        TeamsPage teams = dash.goToTeams().waitUntilLoaded();
        Assert.assertTrue(teams.isLoaded(), "Teams page did not load from Dashboard.");

        teams.openTeamDetails(teamName);

        TeamDetailsPage teamPostPay = new TeamDetailsPage(driver()).waitUntilLoaded();
        Assert.assertTrue(teamPostPay.isLoaded(), "Team Details page should be loaded after payment.");

        // New member should appear in the team table
        teamPostPay.waitForMemberByEmail(recipientEmail, Duration.ofSeconds(30));

        // Optional: assert status if defined (e.g. "Pending" or similar)
        String memberStatus = teamPostPay.getMemberStatusByEmail(recipientEmail);
        System.out.println("[Team] Member status for " + recipientEmail + " = " + memberStatus);
        Assert.assertNotNull(memberStatus, "Expected new paid member to have a status in team table.");

        // -------------------------------------
        // 5) Email behavior – assessment invitation
        // -------------------------------------
        System.out.println("[Email] Waiting up to " + EMAIL_TIMEOUT.toSeconds() +
                "s for assessment email to " + recipientEmail + "…");

        Email email = MailSlurpUtils.waitForEmailMatching(
                inbox.getId(),
                EMAIL_TIMEOUT.toMillis(),
                1500L,
                true,
                MailSlurpUtils.addressedToAliasToken(aliasToken)
                        .and(MailSlurpUtils.subjectContains(SUBJECT_NEEDLE))
                        .and(MailSlurpUtils.bodyContains("accept"))
        );

        Assert.assertNotNull(
                email,
                "❌ No assessment email arrived addressed to alias " + aliasToken +
                        " within " + EMAIL_TIMEOUT.toSeconds() + "s after successful payment."
        );

        final String subject = Objects.toString(email.getSubject(), "");
        final String from    = Objects.toString(email.getFrom(), "");
        final String body    = MailSlurpUtils.safeEmailBody(email);

        System.out.printf("📨 Email — From: %s | Subject: %s%n", from, subject);

        Assert.assertTrue(
                subject.toLowerCase(Locale.ROOT).contains(SUBJECT_NEEDLE),
                "❌ Subject does not mention " + SUBJECT_NEEDLE + ". Got: " + subject
        );

        Assert.assertTrue(
                from.toLowerCase(Locale.ROOT).contains("tilt365")
                        || from.toLowerCase(Locale.ROOT).contains("sendgrid"),
                "❌ Unexpected sender: " + from
        );

        Assert.assertTrue(
                body.toLowerCase(Locale.ROOT).contains(CTA_TEXT.toLowerCase(Locale.ROOT)),
                "❌ Email body missing CTA text '" + CTA_TEXT + "'."
        );

        String ctaHref = MailSlurpUtils.extractLinkByAnchorText(email, CTA_TEXT);
        if (ctaHref == null) ctaHref = MailSlurpUtils.extractFirstLink(email);
        Assert.assertNotNull(ctaHref, "❌ Could not find a CTA link in the email.");

        System.out.println("🔗 CTA link: " + ctaHref);
        Assert.assertTrue(
                ctaHref.contains("sendgrid.net") || ctaHref.contains("tilt365"),
                "❌ CTA link host unexpected: " + ctaHref
        );
    }


    @Test(groups = {"teams", "add-member"})
    @Severity(SeverityLevel.CRITICAL)
    @Story("TC11 – Payment canceled → no side effects")
    public void testPaymentCanceled_NoSideEffects() throws Exception {

        String uuid = String.valueOf(System.currentTimeMillis());

        // -------------------------------------
        // 0) MailSlurp + email config
        // -------------------------------------
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }

        final Duration EMAIL_TIMEOUT = Duration.ofSeconds(60); // shorter than success case
        final String CTA_TEXT       = "Accept Assessment";
        final String SUBJECT_NEEDLE = "assessment";

        // Prefer suite-shared inbox
        final InboxDto inbox = BaseTest.getSuiteInbox() != null
                ? BaseTest.getSuiteInbox()
                : BaseTest.requireInboxOrSkip();

        final String recipientEmail = MailSlurpUtils.uniqueAliasEmail(inbox, "add-member-cancel-" + uuid);
        final String aliasToken     = MailSlurpUtils.extractAliasToken(recipientEmail);

        System.out.println("📮 Using shared inbox: " + inbox.getId() + " <" + inbox.getEmailAddress() + ">");
        System.out.println("📧 Test recipient (alias): " + recipientEmail);

        // Clean inbox to avoid old noise
        try {
            MailSlurpUtils.clearInboxEmails(inbox.getId());
        } catch (Throwable ignored) {}

        final String teamName  = "UXFixCancel-" + uuid;
        final String baseEmail = "base+" + uuid + "@example.com";

        final String newFirst = "Cancel";
        final String newLast  = "Member";

        // -------------------------------------
        // 1) Precondition → Create a fresh team
        // -------------------------------------
        TeamDetailsPage teamDetails = createTeamViaShopFlow(
                teamName,
                "Base",
                "User",
                baseEmail
        ).waitUntilLoaded();

        Assert.assertTrue(teamDetails.isLoaded(), "Team Details page did not load for newly created team.");

        // Capture initial member count to ensure no change after canceled payment
        final int initialMemberCount = teamDetails.getMemberCount();
        System.out.println("[Team] Initial member count for " + teamName + " = " + initialMemberCount);

        // Sanity: the would-be member is not already present
        Assert.assertFalse(
                teamDetails.isMemberListedByEmail(recipientEmail),
                "Precondition failed: test email already present in team."
        );

        // -------------------------------------
        // 2) Open Add Team Member modal & create brand-new user
        // -------------------------------------
        teamDetails.openAddTeamMemberModal();
        Assert.assertTrue(
                teamDetails.isAddTeamMemberModalOpen(),
                "Add Team Member modal should be open."
        );

        teamDetails
                .clickModalCreateNewUser()
                .fillModalNewUser(newFirst, newLast, recipientEmail)
                .clickModalContinue();

        // Product-selection step MUST appear for a new user without entitlements
        Assert.assertTrue(
                teamDetails.isProductSelectionStepVisible(),
                "Product selection must be visible for a brand-new user."
        );

        // Select TTP (this would normally trigger email on success)
        teamDetails.selectTrueTiltProductForMember();

        Assert.assertTrue(
                teamDetails.isContinueToPurchaseVisible(),
                "'Continue to purchase' must appear since the new user has no entitlements."
        );

        // -------------------------------------
        // 3) Continue to Purchase → Stripe (but cancel / abandon payment)
        // -------------------------------------
        teamDetails.clickContinueToPurchase();

        new WebDriverWait(driver(), Duration.ofSeconds(20))
                .until(ExpectedConditions.urlContains("/dashboard/shop/"));

        AssessmentEntryPage entry = new AssessmentEntryPage(driver());
        OrderPreviewPage preview  = entry.clickProceedToPayment().waitUntilLoaded();

        Assert.assertTrue(preview.isLoaded(), "Order Preview page did not load after Proceed to payment.");

        // Go to Stripe but DO NOT trigger checkout.session.completed
        String checkoutUrl = preview.proceedToStripeAndGetCheckoutUrl();
        System.out.println("[Stripe] Opened checkout (for cancel test) url=" + checkoutUrl);

        // Simulate user cancel: they close Stripe / hit back → we just go back to Teams
        driver().navigate().to(joinUrl(Config.getBaseUrl(), "/dashboard/teams"));

        // -------------------------------------
        // 4) Back → Dashboard → Teams → Team details (no changes expected)
        // -------------------------------------
        DashboardPage dash = new DashboardPage(driver())
                .waitUntilLoaded();

        Assert.assertTrue(dash.isLoaded(), "Dashboard did not load correctly after cancel.");

        TeamsPage teams = dash.goToTeams().waitUntilLoaded();
        Assert.assertTrue(teams.isLoaded(), "Teams page did not load from Dashboard.");

        teams.openTeamDetails(teamName);

        TeamDetailsPage teamAfterCancel = new TeamDetailsPage(driver()).waitUntilLoaded();
        Assert.assertTrue(teamAfterCancel.isLoaded(), "Team Details page should be loaded after canceling payment.");

        final int finalMemberCount = teamAfterCancel.getMemberCount();
        System.out.println("[Team] Final member count for " + teamName + " after cancel = " + finalMemberCount);

        // ✅ NO SIDE EFFECT: member count unchanged
        Assert.assertEquals(
                finalMemberCount,
                initialMemberCount,
                "❌ Member count changed after canceled payment; a member might have been added unexpectedly."
        );

        // ✅ NO SIDE EFFECT: specific email must NOT be in the table
        Assert.assertFalse(
                teamAfterCancel.isMemberListedByEmail(recipientEmail),
                "❌ New member appears in team even though payment was canceled."
        );

        // -------------------------------------
        // 5) Email behavior – NO reminder / assessment email
        // -------------------------------------
        System.out.println("[Email] Waiting up to " + EMAIL_TIMEOUT.toSeconds() +
                "s to confirm no assessment email is sent to " + recipientEmail + "…");

        Email email = MailSlurpUtils.waitForEmailMatching(
                inbox.getId(),
                EMAIL_TIMEOUT.toMillis(),
                1500L,
                true,
                MailSlurpUtils.addressedToAliasToken(aliasToken)
                        .and(MailSlurpUtils.subjectContains(SUBJECT_NEEDLE))
                        .and(MailSlurpUtils.bodyContains("accept"))
        );

        // ✅ NO SIDE EFFECT: no email should arrive
        Assert.assertNull(
                email,
                "❌ Unexpected assessment email arrived for alias " + aliasToken +
                        " even though payment was canceled."
        );

        System.out.println("✅ No assessment email was sent after canceling payment (as expected).");
    }



    @Test(groups = {"teams", "add-member"})
    @Severity(SeverityLevel.CRITICAL)
    @Story("TC12 – Already in team (duplicate add)")
    public void testDuplicateAdd_AlreadyInTeamBlocked() throws Exception {

        String uuid = String.valueOf(System.currentTimeMillis());

        // -------------------------------------
        // 1) Precondition — Create a team with 1 base member
        // -------------------------------------
        final String teamName  = "UXFixDup-" + uuid;
        final String baseEmail = "base+" + uuid + "@example.com";

        TeamDetailsPage teamDetails = createTeamViaShopFlow(
                teamName,
                "Base",
                "User",
                baseEmail
        ).waitUntilLoaded();

        Assert.assertTrue(teamDetails.isLoaded(), "Team Details page did not load.");

        final int initialMemberCount = teamDetails.getMemberCount();
        Assert.assertTrue(teamDetails.isMemberListedByEmail(baseEmail),
                "Precondition failed: base member email missing.");

        // -------------------------------------
        // 2) Try to add the SAME email via Create New User
        // -------------------------------------
        teamDetails.openAddTeamMemberModal();
        Assert.assertTrue(teamDetails.isAddTeamMemberModalOpen());

        final String duplicateFirst = "Dup";
        final String duplicateLast  = "Member";
        final String duplicateEmail = baseEmail;  // already in team

        teamDetails
                .clickModalCreateNewUser()
                .fillModalNewUser(duplicateFirst, duplicateLast, duplicateEmail)
                .clickModalContinue();



        // Expect inline validation
        Assert.assertTrue(
                teamDetails.waitForEmailAlreadyInUseError(Duration.ofSeconds(10)),
                "Expected 'Email already in use' error for duplicate email."
        );

        // Continue must remain disabled
        Assert.assertFalse(
                teamDetails.isContinueButtonEnabled(),
                "'Continue' must be disabled when email is already in use."
        );

        // Product-selection step must NOT appear
        Assert.assertFalse(
                teamDetails.isProductSelectionStepVisible(),
                "Product selection must NOT appear for duplicate email."
        );

        // -------------------------------------
        // 3) Close modal — no changes expected
        // -------------------------------------
        teamDetails.clickModalClose().waitForAddMemberModalToClose();

        Assert.assertTrue(teamDetails.isLoaded(), "Team page should still be loaded.");

        final int finalMemberCount = teamDetails.getMemberCount();

        // No side effects — count unchanged
        Assert.assertEquals(
                finalMemberCount,
                initialMemberCount,
                "Member count changed after duplicate add attempt — should remain unchanged."
        );

        Assert.assertTrue(
                teamDetails.isMemberListedByEmail(baseEmail),
                "Original base member should still be present."
        );
    }


    @Test(groups = {"teams", "add-member"})
    @Severity(SeverityLevel.CRITICAL)
    @Story("TC13 – Exists in org but in different team")
    public void testExistingEntitledUserFromOtherTeam_NoPurchase() throws Exception {

        String uuid = String.valueOf(System.currentTimeMillis());

        // -------------------------------------
        // 1) Test data
        // -------------------------------------
        // Existing user in the org, already entitled and already in some other team.
        final String entitledName  = "Emiliano Rodriguez Tejera";
        final String entitledEmail = "erodriguez@effectussoftware.com";

        // Fresh team where we will add that existing user
        final String teamName  = "UXFixOtherTeam-" + uuid;
        final String baseEmail = "base+" + uuid + "@example.com";

        // -------------------------------------
        // 2) Precondition – create a fresh team via Shop
        // -------------------------------------
        TeamDetailsPage teamDetails = createTeamViaShopFlow(
                teamName,
                "Base",
                "User",
                baseEmail
        ).waitUntilLoaded();

        Assert.assertTrue(teamDetails.isLoaded(), "Team Details page did not load for newly created team.");

        final int initialMemberCount = teamDetails.getMemberCount();
        System.out.println("[Team] Initial member count for " + teamName + " = " + initialMemberCount);

        // Base member is present
        Assert.assertTrue(
                teamDetails.isMemberListedByEmail(baseEmail),
                "Precondition failed: base member email not present in team."
        );

        // Entitled user must NOT yet be in this team
        Assert.assertFalse(
                teamDetails.isMemberListedByEmail(entitledEmail),
                "Precondition failed: entitled user already present in this team."
        );

        // -------------------------------------
        // 3) Open Add Team Member for this team
        // -------------------------------------
        teamDetails.openAddTeamMemberModal();
        Assert.assertTrue(teamDetails.isAddTeamMemberModalOpen(),
                "Add Team Member modal should be open.");

        // -------------------------------------
        // 4) Search and select the existing entitled user
        // -------------------------------------
        teamDetails
                .typeInExistingUserSearch(entitledName)
                .selectFirstExistingUserFromResults();

        // After selecting an existing user, we should see the product cards view
        Assert.assertTrue(
                teamDetails.isProductSelectionStepVisible(),
                "Expected product cards (TTP/AGT) to be visible for existing entitled user."
        );

        // At least one of the products should show "Retake available" / similar entitlement chip
        Assert.assertTrue(
                teamDetails.hasAnyRetakeAvailableProduct(),
                "Expected at least one product card to show 'Retake available' for entitled user."
        );

        // No purchase path: there must NOT be a 'Continue to purchase' CTA
        Assert.assertFalse(
                teamDetails.isContinueToPurchaseVisible(),
                "'Continue to purchase' must NOT be visible for an entitled existing user."
        );

        // Bottom CTA should let us add the user directly (no shop/Stripe)
        Assert.assertTrue(
                teamDetails.isAddMemberButtonEnabled(),
                "'Add user' / 'Add Member' button should be enabled after selecting existing entitled user."
        );

        // -------------------------------------
        // 5) Add the existing user to this team (no purchase)
        // -------------------------------------
        teamDetails
                .clickModalAddMemberOrUser()
                .waitForAddMemberModalToClose();

        Assert.assertTrue(
                teamDetails.isLoaded(),
                "Team Details page should still be loaded after closing Add Team Member modal."
        );

        // -------------------------------------
        // 6) Validate results – user added, no purchase path used
        // -------------------------------------
        final int finalMemberCount = teamDetails.getMemberCount();
        System.out.println("[Team] Final member count for " + teamName + " = " + finalMemberCount);

        // Member count increased by exactly 1
        Assert.assertEquals(
                finalMemberCount,
                initialMemberCount + 1,
                "Member count should increase by 1 after adding an existing entitled user from another team."
        );

        // The entitled user must now appear in the team table
        teamDetails.waitForMemberByEmail(entitledEmail, Duration.ofSeconds(30));

        String status = teamDetails.getMemberStatusByEmail(entitledEmail);
        Assert.assertNotNull(status, "Expected a non-null status for the entitled user added to the team.");
        Assert.assertFalse(status.isBlank(), "Expected a non-blank status for the entitled user added to the team.");

        System.out.println("[Team] Status for existing entitled user " + entitledEmail + " = " + status);

        // Optional: log if any report links exist for evidence
        boolean hasAnyReportLink = false;
        try {
            hasAnyReportLink = teamDetails.memberRowHasAnyReportLink(entitledEmail);
        } catch (Exception ignore) {
            // OK if they are pending / no links yet.
        }
        System.out.println("[Team] memberRowHasAnyReportLink(" + entitledEmail + ") = " + hasAnyReportLink);

        // Key guarantee for TC13:
        // - We saw entitlement/retake cards
        // - There was NO 'Continue to purchase' button
        // - We added the user via 'Add user' only
        // → therefore "no purchase if entitled" is enforced while still allowing cross-team add.
    }





























    @Test(groups = {"teams", "add-member", "ux-fix-aug-2025"})
    @Severity(SeverityLevel.CRITICAL)
    @Story("TC14 – Email correction prior issue regression")
    public void testEmailCorrection_NoGhostTtp_NoDuplicate_NoSideEffects() throws Exception {

        String uuid = String.valueOf(System.currentTimeMillis());

        // -------------------------------------
        // MailSlurp setup – use fixed inbox + alias
        // -------------------------------------
        final InboxDto inbox = BaseTest.requireInboxOrSkip();
        String baseInboxEmail = inbox.getEmailAddress();
        String aliasTag = "tc14-" + uuid;

        // These both route into the same MailSlurp inbox, but are different addresses
        String wrongEmail     = MailSlurpUtils.addPlusAlias(baseInboxEmail, aliasTag + "-wrong");
        String correctedEmail = MailSlurpUtils.addPlusAlias(baseInboxEmail, aliasTag + "-corrected");

        String firstName = "TC14User";
        String lastName  = "Test";

        // -------------------------------------
        // 0) Precondition → Create fresh team
        // -------------------------------------
        TeamDetailsPage details = createTeamViaShopFlow(
                "TC14Team-" + uuid,
                "Base",
                "User",
                MailSlurpUtils.addPlusAlias(baseInboxEmail, aliasTag + "-owner")
        ).waitUntilLoaded();

        Assert.assertTrue(details.isLoaded(), "Team page must be loaded.");

        // -------------------------------------
        // 1) Add new user (wrong email) → Continue → Purchase TTP
        // -------------------------------------
        details.openAddTeamMemberModal();

        details.clickModalCreateNewUser()
                .fillModalNewUser(firstName, lastName, wrongEmail)
                .clickModalContinue();

        // Must show product selection
        Assert.assertTrue(
                details.isProductSelectionStepVisible(),
                "Product selection must be visible for new user."
        );

        details.selectTrueTiltProductForMember();
        Assert.assertTrue(details.isContinueToPurchaseVisible());

        details.clickContinueToPurchase();

        // Stripe handoff
        new WebDriverWait(driver(), Duration.ofSeconds(20))
                .until(ExpectedConditions.urlContains("/dashboard/shop"));

        AssessmentEntryPage entry = new AssessmentEntryPage(driver());
        OrderPreviewPage preview = entry.clickProceedToPayment().waitUntilLoaded();

        // Get Stripe session + simulate success
        String checkoutUrl = preview.proceedToStripeAndGetCheckoutUrl();
        String sessionId   = extractSessionIdFromUrl(checkoutUrl);
        Assert.assertNotNull(sessionId, "Stripe sessionId must be extracted");

        String bodyJson = StripeCheckoutHelper.fetchCheckoutBodyFromStripe(sessionId);
        StripeCheckoutHelper.triggerCheckoutCompletedWithBody(bodyJson);

        // redirect to confirmation
        driver().navigate().to(joinUrl(Config.getBaseUrl(), "/dashboard/orders/confirmation"));

        // -------------------------------------
        // 2) Navigate back → Team Details → Verify user added
        // -------------------------------------
        DashboardPage dash = new DashboardPage(driver())
                .open(Config.getBaseUrl())
                .waitUntilLoaded();

        TeamsPage teams = dash.goToTeams().waitUntilLoaded();
        teams.openTeamDetails("TC14Team-" + uuid);

        TeamDetailsPage post = new TeamDetailsPage(driver()).waitUntilLoaded();

        post.waitForMemberByEmail(wrongEmail, Duration.ofSeconds(20));
        Assert.assertEquals(post.getMemberStatusByEmail(wrongEmail), "Pending");

        int countBeforeEdit = post.getMemberCount();

        // -------------------------------------
        // 3) EDIT USER EMAIL
        // -------------------------------------
        post.clickEditInfoForMember(wrongEmail);
        post.waitForEditModal();

        post.fillEditEmail(correctedEmail);
        post.clickEditSave();

        // -------------------------------------
        // 4) VALIDATE EMAIL CHANGED CORRECTLY
        // -------------------------------------
        post.waitForMemberByEmail(correctedEmail, Duration.ofSeconds(20));

        Assert.assertFalse(
                post.isMemberListedByEmail(wrongEmail),
                "Old email should no longer appear after correction."
        );

        Assert.assertEquals(
                post.getMemberCount(),
                countBeforeEdit,
                "No duplicate or ghost member should be created when editing email."
        );

        // Status must remain Pending
        Assert.assertEquals(
                post.getMemberStatusByEmail(correctedEmail),
                "Pending",
                "Status must remain Pending after email correction."
        );

        // -------------------------------------
        // 5) VALIDATE NO GHOST TTP / NO NEW REPORT LINKS
        // -------------------------------------
        Assert.assertFalse(
                post.memberRowHasAnyReportLink(correctedEmail),
                "Corrected user should NOT suddenly get any completed report link."
        );

        // -------------------------------------
        // 6) VALIDATE SEND REMINDER MODAL STILL WORKS
        // -------------------------------------
        post.clickSendReminderForMember(correctedEmail);
        Assert.assertTrue(
                post.isSendReminderModalVisible(),
                "Send Reminder modal must open normally (regression fix)."
        );
        post.clickSendReminderCancel();

        // -------------------------------------
        // 7) MAILSLURP ASSERTION – NO NEW EMAIL SENT AFTER EDIT
        // -------------------------------------
        Email unexpected = MailSlurpUtils.waitForNoNewEmail(
                inbox.getId(),
                Duration.ofSeconds(15)
        );

        Assert.assertNull(
                unexpected,
                "No new invitation email should be sent after editing the user email."
        );
    }



















}
