package tests.teams;


import Utils.Config;
import base.BaseTest;
import org.openqa.selenium.NoSuchElementException;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;
import pages.LoginPage;
import pages.menuPages.DashboardPage;
import pages.teams.TeamClimatePage;
import pages.teams.TeamDetailsPage;
import pages.teams.TeamsPage;

import java.time.Duration;
import java.util.Locale;

import static io.qameta.allure.Allure.step;


public class TeamClimateTests  extends BaseTest{













    @Test(groups = {"smoke"}, description = "SM12: Large team Kite graph loads and selecting a node updates side panel.")
    public void smoke_kiteGraph_largeTeam_selectionUpdatesDetails() {

        // -------------------- ADMIN CREDS --------------------
        step("Resolve admin credentials from config");
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }
        System.out.println("[AdminCreds] email=" + BaseTest.maskEmail(ADMIN_USER) +
                " | passLen=" + ADMIN_PASS.length());

        // Name as it appears in the Teams table (Org / Team)
        final String DEFAULT_TEAM_NAME = "Org B / Validation Merge Test";
        String teamName = Config.getAny("climate.largeTeam.name", "CLIMATE_LARGE_TEAM", "CLIMATE_TEAM_NAME");
        if (teamName == null || teamName.isBlank()) {
            teamName = DEFAULT_TEAM_NAME;
        }
        System.out.println("[Fixture] Large team name = " + teamName);

        // -------------------- LOGIN → DASHBOARD --------------------
        step("Login as admin and open Dashboard");
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        login.waitUntilLoaded();

        DashboardPage dashboard =
                login.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboard.isLoaded(), "❌ Dashboard did not load after login");

        // -------------------- OPEN TEAMS → TEAM CLIMATE --------------------
        step("Open Teams page from Dashboard");
        TeamsPage teamsPage = dashboard.goToTeams();
        Assert.assertTrue(teamsPage.isLoaded(), "❌ Teams page did not load");

        step("Open Team Climate / Analytics report for: " + teamName);
        try {
            teamsPage.openTeamClimate(teamName);       // this navigates to /dashboard/teams/{id}/report
        } catch (NoSuchElementException e) {
            throw new SkipException("Large team fixture not found in Teams list: '" + teamName + "'", e);
        }

        // -------------------- KITE GRAPH PAGE OBJECT --------------------
        TeamClimatePage teamClimate = new TeamClimatePage(driver()).waitUntilLoaded();

        // -------------------- CLIMATE / KITE GRAPH --------------------
        step("Wait for Kite graph to render");
        teamClimate.waitForKiteGraphLoaded();

        Assert.assertTrue(
                teamClimate.isKiteGraphVisible(),
                "❌ Kite graph card is not visible"
        );

        int nodeCount = teamClimate.getKiteGraphNodeCount();
        Assert.assertTrue(
                nodeCount >= 5,
                "❌ Expected a 'large' team with at least 5 Kite nodes, but got " + nodeCount
        );
        System.out.println("[Kite] nodeCount=" + nodeCount);

        // -------------------- FIRST SELECTION --------------------
        step("Click the first Kite node and verify side panel shows that member");
        teamClimate.clickKiteNodeByIndex(1);

        String selectedName1 = teamClimate.getKiteSidePanelSelectedName();
        Assert.assertNotNull(selectedName1, "❌ Side panel name after first click is null");
        Assert.assertFalse(selectedName1.isBlank(), "❌ Side panel shows empty name after first click");
        System.out.println("[Kite] first selection = " + selectedName1);

        // -------------------- SECOND SELECTION --------------------
        step("Click a different Kite node and assert side panel updates accordingly");
        int secondIndex = (nodeCount >= 2) ? 2 : nodeCount;
        if (secondIndex == 1) {
            throw new SkipException("Only one Kite node detected, cannot verify change on second selection.");
        }

        teamClimate.clickKiteNodeByIndex(secondIndex);

        String selectedName2 = teamClimate.getKiteSidePanelSelectedName();
        Assert.assertNotNull(selectedName2, "❌ Side panel name after second click is null");
        Assert.assertFalse(selectedName2.isBlank(), "❌ Side panel shows empty name after second click");
        System.out.println("[Kite] second selection = " + selectedName2);

        Assert.assertNotEquals(
                selectedName2.trim().toLowerCase(Locale.ROOT),
                selectedName1.trim().toLowerCase(Locale.ROOT),
                "❌ Side panel did not update when selecting a different Kite node"
        );
    }













}
