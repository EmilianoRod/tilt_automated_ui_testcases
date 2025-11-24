package tests.teams;


import Utils.Config;
import base.BaseTest;
import org.openqa.selenium.JavascriptExecutor;
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









    @Test(groups = {"smoke"}, description = "SM12: Large team Kite graph loads and selecting a member updates side panel.")
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

        // Allow overriding via config, default to the known fixture.
        final String DEFAULT_TEAM_PATH = "Org B / Validation Merge Test / Analytics";
        String teamPath = Config.getAny("climate.largeTeam.path", "CLIMATE_LARGE_TEAM", "CLIMATE_TEAM_PATH");
        if (teamPath == null || teamPath.isBlank()) {
            teamPath = DEFAULT_TEAM_PATH;
        }
        System.out.println("[Fixture] Large team path = " + teamPath);

        // -------------------- LOGIN → DASHBOARD --------------------
        step("Login as admin and open Dashboard");
        LoginPage login = new LoginPage(driver());
        login.navigateTo();
        login.waitUntilLoaded();
        DashboardPage dashboard =
                login.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboard.isLoaded(), "❌ Dashboard did not load after login");

        // -------------------- OPEN LARGE TEAM CLIMATE / ANALYTICS --------------------
        step("Open Teams page");
        TeamsPage teamsPage = dashboard.goToTeams();
        Assert.assertTrue(teamsPage.isLoaded(), "❌ Teams page did not load");

        step("Open Climate/Analytics for large team: " + teamPath);
        TeamClimatePage climatePage;
        try {
            climatePage = teamsPage.openTeamClimateDetails(teamPath);
        } catch (SkipException e) {
            throw e; // propagate SKIP with message from helper
        } catch (NoSuchElementException e) {
            throw new SkipException("Large team fixture not found in Teams list: '" + teamPath + "'", e);
        }

        // -------------------- CLIMATE / KITE GRAPH --------------------
        step("Verify Kite graph is visible and has 4 quadrant arcs");
        Assert.assertTrue(
                climatePage.isKiteGraphVisible(),
                "❌ Kite graph card is not visible"
        );

        int nodeCount = climatePage.getKiteNodeCount();
        Assert.assertEquals(
                nodeCount,
                4,
                "❌ Expected exactly 4 quadrant arcs in the Kite graph"
        );
        System.out.println("[Kite] nodeCount=" + nodeCount);

        // -------------------- BY MEMBERS PANEL --------------------
        step("Switch to 'By Members' view");
        climatePage.openByMembersView();

        step("Pick a tilt group (Impact/Clarity/Connection/Structure) with at least 2 members");
        String[] tiltOrder = {"impact", "clarity", "connection", "structure"};
        String chosenTilt = null;
        int chosenCount = 0;

        for (String tilt : tiltOrder) {
            try {
                int count = climatePage.getMemberCountInTiltGroup(tilt);
                System.out.println("[Kite] tilt group '" + tilt + "' count=" + count);
                if (count >= 2) {
                    chosenTilt = tilt;
                    chosenCount = count;
                    break;
                }
            } catch (Exception e) {
                System.out.println("[Kite] tilt group '" + tilt + "' not usable: " + e.getMessage());
            }
        }

        if (chosenTilt == null) {
            throw new SkipException("No tilt group has at least 2 members; cannot verify member selection behaviour.");
        }
        System.out.println("[Kite] Using tilt group '" + chosenTilt + "' with " + chosenCount + " members");

        // -------------------- FIRST SELECTION --------------------
        step("Click the first member in tilt group '" + chosenTilt + "' and verify side panel shows that member");
        String listName1 = climatePage.clickMemberInTiltGroupByIndex(chosenTilt, 0);
        climatePage.assertSidePanelMatchesMember(listName1, chosenTilt);
        String panelNameAfterFirst = climatePage.getSelectedMemberName();

        // -------------------- SECOND SELECTION --------------------
        step("Click a different member in the same tilt group and assert side panel updates accordingly");
        String listName2 = climatePage.clickMemberInTiltGroupByIndex(chosenTilt, 1);

        String panelNameAfterSecond = climatePage.getSelectedMemberName();
        Assert.assertNotEquals(
                panelNameAfterSecond.trim().toLowerCase(Locale.ROOT),
                panelNameAfterFirst.trim().toLowerCase(Locale.ROOT),
                "❌ Side panel did not update after clicking a different member"
        );

        // And then full checks for second member
        climatePage.assertSidePanelMatchesMember(listName2, chosenTilt);

    }
















}
