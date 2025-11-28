package pages.teams;

import Utils.NameFactory;
import org.testng.Assert;
import pages.Shop.AssessmentEntryPage;
import pages.Shop.OrderPreviewPage;

public class TeamPurchaseFlows {

    public static class TeamCreationResult {
        public final String orgName;
        public final String groupName;
        public final OrderPreviewPage preview;

        public TeamCreationResult(String orgName, String groupName, OrderPreviewPage preview) {
            this.orgName = orgName;
            this.groupName = groupName;
            this.preview = preview;
        }
    }

    private TeamPurchaseFlows() {
        // utility class
    }

    public static TeamCreationResult createUniqueTeamWithTwoMembers(
            AssessmentEntryPage entry,
            String email1,
            String email2
    ) {
        String suffix = NameFactory.uniqueSuffix();
        String orgName = "QA Org - " + suffix;
        String groupName = "Automation Squad - " + suffix;

        entry
                .selectCreateNewTeam()
                .setOrganizationName(orgName)
                .setGroupName(groupName)
                .selectManualEntry()
                .enterNumberOfIndividuals("2");

        entry.fillUserDetailsAtIndex(1, "U", "One", email1);
        entry.fillUserDetailsAtIndex(2, "U", "Two", email2);

        entry.triggerManualValidationBlurs();
        Assert.assertTrue(entry.isProceedToPaymentEnabled(), "Proceed should be enabled");

        OrderPreviewPage preview = entry.clickProceedToPayment().waitUntilLoaded();

        return new TeamCreationResult(orgName, groupName, preview);
    }


}
