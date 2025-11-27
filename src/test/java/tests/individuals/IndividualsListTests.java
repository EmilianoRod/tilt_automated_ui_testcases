package tests.individuals;

import Utils.Config;
import Utils.MailSlurpUtils;
import Utils.StripeCheckoutHelper;
import Utils.WaitUtils;
import base.BaseTest;
import com.mailslurp.models.Email;
import com.mailslurp.models.InboxDto;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v142.network.Network;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;
import pages.BasePage;
import pages.LoginPage;
import pages.Shop.AssessmentEntryPage;
import pages.Shop.OrderPreviewPage;
import pages.Shop.PurchaseRecipientSelectionPage;
import pages.assesstmens.ttp.TtpIntroPage;
import pages.assesstmens.ttp.TtpSurveyPage;
import pages.menuPages.DashboardPage;
import pages.Individuals.IndividualsPage;
import pages.menuPages.ShopPage;
import pages.reports.ReportSummaryPage;

import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;


import static Utils.Config.joinUrl;
import static io.qameta.allure.Allure.step;
import static tests.teams.TeamAssessmentPurchaseAndAssignment.extractSessionIdFromUrl;

public class IndividualsListTests extends BaseTest {





    @Test(description = "TC-434: Individuals list shows Name, assessment icon(s), Date taken, and a clickable Report link (spot-check first rows).")
    public void displayIndividualsList_showsRequiredColumns() {

        step("Start fresh session (login + land on Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page from Dashboard nav");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Verify the table/list is present and has at least 1 row (or skip)");
        if (!individuals.hasAnyRows()) {
            throw new SkipException("No Individuals to verify on this environment.");
        }

        step("Decide how many rows to spot-check");
        final int maxToCheck = Integer.getInteger("INDIV_ROWS_CHECK_MAX", 10);

        step("Collect first " + maxToCheck + " rows for validation");
        List<WebElement> rows = individuals.firstRows(maxToCheck);

        step("Begin soft assertions across selected rows");
        SoftAssert softly = new SoftAssert();

        for (int i = 0; i < rows.size(); i++) {
            final int rowNum = i + 1;
            final WebElement row = rows.get(i);

            step("Row #" + rowNum + " — verify Name");
            softly.assertTrue(
                    individuals.rowHasName(row),
                    "Row " + rowNum + " should have a non-empty Name"
            );

            step("Row #" + rowNum + " — verify assessment icon(s)");
            softly.assertTrue(
                    individuals.rowHasAssessmentIcon(row),
                    "Row " + rowNum + " should show at least one assessment icon"
            );

            step("Row #" + rowNum + " — verify Report column: Pending OR clickable link");
            if (individuals.rowReportIsPending(row)) {
                // ok: pending state is expected before completion
                // you can log if you want:
                // logger.info("Row {} report is Pending", rowNum);
            } else {
                softly.assertTrue(
                        individuals.rowHasReportLink(row),
                        "Row " + rowNum + " should have a clickable Report link when not Pending (saw: '" +
                                individuals.rowReportText(row) + "')"
                );
            }
        }

        step("Finalize soft assertions");
        softly.assertAll();
    }




    @Test(description = "TC-435: Search filters to only matching entries (name/email substring).")
    public void searchIndividuals_filtersToMatchingEntries() {

        step("Start fresh session (login + land on Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page from Dashboard nav");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Ensure there is at least one row (or skip)");
        if (!individuals.hasAnyRows()) {
            throw new SkipException("No Individuals present; cannot verify search.");
        }

        // -------------------- A) EMAIL SUBSTRING FILTER --------------------
        step("Pick a sample email from the current page");
        List<String> emailsBefore = individuals.getEmailsOnCurrentPage();
        if (emailsBefore.isEmpty()) {
            throw new SkipException("No visible email cells to use as anchor for email search.");
        }
        String sampleEmail = emailsBefore.get(0);

        step("Build a robust substring from the email local-part");
        String local = sampleEmail.contains("@") ? sampleEmail.substring(0, sampleEmail.indexOf('@')) : sampleEmail;
        String emailNeedle = pickMiddleSubstring(local, 3, 6);

        step("Execute search for email substring: '" + emailNeedle + "'");
        individuals.search(emailNeedle);

        step("Verify results exist after filtering by email substring");
        Assert.assertTrue(individuals.hasAnyRows(),
                "❌ Search returned zero rows for email substring: " + emailNeedle);

        step("Collect visible emails after filtering");
        List<String> filteredEmails = individuals.getEmailsOnCurrentPage();
        Assert.assertFalse(filteredEmails.isEmpty(),
                "❌ No email cells visible after email substring filter: " + emailNeedle);

        step("Assert every visible email contains the substring (case-insensitive)");
        String emailNeedleLc = emailNeedle.toLowerCase(Locale.ROOT);
        SoftAssert softly = new SoftAssert();
        for (int i = 0; i < filteredEmails.size(); i++) {
            final int rowNum = i + 1;
            final String email = filteredEmails.get(i);
            step("Row #" + rowNum + " — email should contain '" + emailNeedle + "': " + email);
            softly.assertTrue(
                    email.toLowerCase(Locale.ROOT).contains(emailNeedleLc),
                    "Row " + rowNum + " email does not contain '" + emailNeedle + "': " + email
            );
        }
        softly.assertAll();

        step("Tight check: search the exact email and verify it’s listed on the current page");
        individuals.search(sampleEmail);
        Assert.assertTrue(
                individuals.isUserListedByEmailOnCurrentPage(sampleEmail),
                "Expected to find exact email on current page after searching it: " + sampleEmail
        );

        // -------------------- B) NAME SUBSTRING FILTER --------------------
        // -------------------- B) NAME SUBSTRING FILTER --------------------
        step("Reload Individuals to clear filters before name search");
        individuals.reloadWithBuster(Config.getBaseUrl());
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals failed to reload before name search");

        step("Ensure there is at least one row again (or skip name path)");
        if (!individuals.hasAnyRows()) {
            throw new SkipException("No rows after reload; skipping name search portion.");
        }

        step("Pick a sample non-empty name from the current page");
        List<String> namesBefore = individuals.getNamesOnCurrentPage();
        String sampleName = namesBefore.stream()
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElseThrow(() -> new SkipException("No non-empty names visible to use as anchor for name search."));

        step("Build a robust substring from a single name token");
        // Use one token (first non-short word) so substring matches what UI shows
        String cleaned = sampleName.trim();
        String[] tokens = cleaned.split("\\s+");
        String core = tokens[0];
        for (String t : tokens) {
            if (t.length() >= 3) {
                core = t;
                break;
            }
        }

        // Now pick a middle substring from that single token
        String nameNeedle = pickMiddleSubstring(core, 3, 6);

        // Safety: ensure needle actually appears in the visible name (case-insensitive)
        String visibleLc = cleaned.toLowerCase(Locale.ROOT);
        String nameNeedleLc = nameNeedle.toLowerCase(Locale.ROOT);
        if (!visibleLc.contains(nameNeedleLc)) {
            // Fallback: first 3 characters of the first token
            nameNeedle = core.substring(0, Math.min(3, core.length()));
            nameNeedleLc = nameNeedle.toLowerCase(Locale.ROOT);
        }

        step("Execute search for name substring: '" + nameNeedle + "'");
        individuals.search(nameNeedle);

        step("Verify results exist after filtering by name substring");
        Assert.assertTrue(individuals.hasAnyRows(),
                "❌ Search returned zero rows for name substring: " + nameNeedle);

        step("Collect visible names after filtering");
        List<String> filteredNames = individuals.getNamesOnCurrentPage();
        Assert.assertFalse(filteredNames.isEmpty(),
                "❌ No name cells visible after name substring filter: " + nameNeedle);

        step("Assert every visible name contains the substring (case-insensitive)");
        for (int i = 0; i < filteredNames.size(); i++) {
            final int rowNum = i + 1;
            final String name = filteredNames.get(i);
            step("Row #" + rowNum + " — name should contain '" + nameNeedle + "': " + name);
            Assert.assertTrue(
                    name.toLowerCase(Locale.ROOT).contains(nameNeedleLc),
                    "Row " + rowNum + " name does not contain '" + nameNeedle + "': " + name
            );
        }


        // -------------------- C) NEGATIVE CHECK --------------------
        step("Negative check: improbable token returns empty state (no rows)");
        String impossible = "zzqxxyy" + System.currentTimeMillis();
        individuals.search(impossible);
        Assert.assertFalse(individuals.hasAnyRows(), "Expected zero rows for improbable token: " + impossible);

        step("Reload Individuals to clear filters for subsequent tests");
        individuals.reloadWithBuster(Config.getBaseUrl());
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals failed to reload after clearing search");
    }

    /** Picks a middle substring from input with length between [minLen, maxLen], robust to short strings. */
    private static String pickMiddleSubstring(String input, int minLen, int maxLen) {
        String s = input == null ? "" : input.trim();
        if (s.length() <= minLen) return s; // too short; use as-is
        int len = Math.min(Math.max(minLen, Math.min(maxLen, s.length())), s.length());
        int start = Math.max(0, (s.length() - len) / 2);
        return s.substring(start, start + len);
    }


    @Test(groups = {"known-bug"}, description = "TC-436: Sort by Name (A→Z, Z→A) and Date (Newest, Oldest) updates ordering correctly.")
    public void sortIndividuals_ordersUpdateCorrectly() {

        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Ensure we have at least 2 rows to validate ordering");
        if (!individialsHasAtLeast(individuals, 2)) {
            throw new SkipException("Need at least 2 rows to validate sorting.");
        }

        // ---------------- NAME SORT ----------------
        step("Sort by Name (A→Z)");
        individuals.chooseSortOption("Name (A–Z)"); // your page maps this to "Sort by: A-Z"
        List<String> namesAsc = individuals.getNamesOnCurrentPage();
        assumeNonEmpty(namesAsc, "namesAsc");

        step("Assert names are ascending (case-insensitive, locale-aware)");
        Assert.assertTrue(isSortedNamesAsc(namesAsc), "Names are not ascending: " + namesAsc);

        step("Sort by Name (Z→A)");
        individuals.chooseSortOption("Name (Z–A)");
        List<String> namesDesc = individuals.getNamesOnCurrentPage();
        assumeNonEmpty(namesDesc, "namesDesc");

        step("Assert names are descending");
        Assert.assertTrue(isSortedNamesDesc(namesDesc), "Names are not descending: " + namesDesc);

        step("If names are not all identical, ensure A→Z and Z→A produce different orders");
        if (hasAtLeastTwoDistinct(namesAsc)) {
            Assert.assertNotEquals(namesAsc, namesDesc, "Name sort did not change the ordering.");
        }

        // ---------------- DATE SORT (heuristic – no visible date column) ----------------
        step("Capture current order under 'Newest'");
        individuals.chooseSortOption("Newest");
        List<String> newestOrder1 = makeOrderSignature(individuals);
        assumeNonEmpty(newestOrder1, "newestOrder1");

        step("Choose 'Newest' again (idempotence check)");
        individuals.chooseSortOption("Newest");
        List<String> newestOrder2 = makeOrderSignature(individuals);
        Assert.assertEquals(newestOrder2, newestOrder1, "Order changed when applying 'Newest' twice; sort should be idempotent.");

        step("Switch to 'Oldest' and expect ordering to change");
        individuals.chooseSortOption("Oldest");
        List<String> oldestOrder = makeOrderSignature(individuals);
        assumeNonEmpty(oldestOrder, "oldestOrder");
        Assert.assertNotEquals(oldestOrder, newestOrder1, "Newest vs Oldest produced identical order on page 1.");

        step("Flip back to 'Newest' — order should match the first 'Newest' snapshot");
        individuals.chooseSortOption("Newest");
        List<String> newestOrder3 = makeOrderSignature(individuals);
        Assert.assertEquals(newestOrder3, newestOrder1, "Newest → Oldest → Newest did not restore original order.");
    }


    // Test 1: Auto reminder toggle persists after refresh (ON then OFF)
    @Test(description = "Individuals: Auto reminder toggle persists after refresh (ON then OFF)")
    public void autoReminderToggle_persistsAfterRefresh_onThenOff() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Ensure we have at least 1 row to work with");
        ensureHasAtLeastRowsOrSkip(individuals, 1);

        step("Pick a target email (first visible on current page)");
        String email = pickTargetEmail(individuals);
        if (email == null) throw new SkipException("No rows available to toggle Auto reminder.");

        // ON -> refresh -> assert
        step("Set Auto reminder = ON for " + email);
        individuals.setAutoReminder(email, true);

        driver().navigate().refresh();
        individuals.waitUntilLoaded();

        step("Assert Auto reminder remains ON for " + email);
        Assert.assertTrue(individuals.isAutoReminderOn(email), "❌ Should remain ON after refresh: " + email);

        // OFF -> refresh -> assert
        step("Set Auto reminder = OFF for " + email);
        individuals.setAutoReminder(email, false);

        driver().navigate().refresh();
        individuals.waitUntilLoaded();

        step("Assert Auto reminder remains OFF for " + email);
        Assert.assertFalse(individuals.isAutoReminderOn(email), "❌ Should remain OFF after refresh: " + email);
    }
    // Keep it dead simple: if there is at least one row on the CURRENT page, proceed; else skip.
    private static void ensureHasAtLeastRowsOrSkip(IndividualsPage individuals, int minRows) {
        if (individuals.getEmailsOnCurrentPage().size() >= minRows) return;
        throw new SkipException("No Individuals rows available (need ≥ " + minRows + ").");
    }

    // Also simple: pick the first visible email on the CURRENT page.
// (No pagination, no "Pending" preference — avoids extra DOM churn.)
    private static String pickTargetEmail(IndividualsPage individuals) {
        var emails = individuals.getEmailsOnCurrentPage();
        return emails.isEmpty() ? null : emails.get(0);
    }



    @Test(groups = {"known-bug"}, description = "IND-003: Sort by Name (A–Z) orders ascending (case/accents ignored).")
    public void sortByName_ordersAscending() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Ensure we have at least 2 rows to validate ordering");
        if (!individialsHasAtLeast(individuals, 2)) {
            throw new SkipException("Need at least 2 rows to validate sorting.");
        }

        // ---------------- NAME (A→Z) ----------------
        step("Sort by Name (A→Z)");
        individuals.chooseSortOption("Name (A–Z)"); // same mapping as TC-436
        List<String> namesAsc = individuals.getNamesOnCurrentPage();
        assumeNonEmpty(namesAsc, "namesAsc");

        step("Assert names are ascending (case/accents ignored)");
        Assert.assertTrue(isSortedNamesAsc(namesAsc), "Names are not ascending: " + namesAsc);

        // ---------------- NAME (Z→A) ----------------
        step("Sort by Name (Z→A)");
        individuals.chooseSortOption("Name (Z–A)");
        List<String> namesDesc = individuals.getNamesOnCurrentPage();
        assumeNonEmpty(namesDesc, "namesDesc");

        step("Assert names are descending");
        Assert.assertTrue(isSortedNamesDesc(namesDesc), "Names are not descending: " + namesDesc);

        step("If there are ≥2 distinct names, orders should differ between A→Z and Z→A");
        if (hasAtLeastTwoDistinct(namesAsc)) {
            Assert.assertNotEquals(namesAsc, namesDesc, "Name sort did not change the ordering.");
        }

        // Optional quick idempotence check (same style as TC-436 but for A→Z)
        step("Re-apply A→Z and expect the same order (idempotence)");
        individuals.chooseSortOption("Name (A–Z)");
        List<String> namesAsc2 = individuals.getNamesOnCurrentPage();
        assumeNonEmpty(namesAsc2, "namesAsc2");
        Assert.assertEquals(namesAsc2, namesAsc, "Applying A→Z twice changed the order.");
    }


    @Test(description = "IND-005 & IND-006: Sort by Date (Newest/Oldest) in a single flow with idempotence checks.")
    public void sortByDate_newest_oldest_inOne() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Ensure we have at least 2 rows to validate ordering");
        if (!individialsHasAtLeast(individuals, 2)) {
            throw new SkipException("Need at least 2 rows to validate date sorting.");
        }

        // ----- NEWEST -----
        step("Choose Sort by: Newest");
        individuals.chooseSortOption("Newest");
        List<String> newestOrder1 = makeOrderSignature(individuals);
        assumeNonEmpty(newestOrder1, "newestOrder1");

        step("Re-apply Newest (idempotence)");
        individuals.chooseSortOption("Newest");
        List<String> newestOrder2 = makeOrderSignature(individuals);
        Assert.assertEquals(newestOrder2, newestOrder1, "Order changed when applying 'Newest' twice; should be idempotent.");

        // ----- OLDEST -----
        step("Switch to Sort by: Oldest");
        individuals.chooseSortOption("Oldest");
        List<String> oldestOrder = makeOrderSignature(individuals);
        assumeNonEmpty(oldestOrder, "oldestOrder");

        step("Newest vs Oldest should differ when the first page has ≥2 distinct rows");
        if (!newestOrder1.equals(newestOrder2)) {
            // already checked above, but keep symmetry
            Assert.fail("Newest was not idempotent.");
        }
        Assert.assertNotEquals(oldestOrder, newestOrder1, "Newest vs Oldest produced identical order on page 1.");

        // ----- ROUNDTRIP -----
        step("Flip back to Newest — order should match the first Newest snapshot");
        individuals.chooseSortOption("Newest");
        List<String> newestOrder3 = makeOrderSignature(individuals);
        Assert.assertEquals(newestOrder3, newestOrder1, "Newest → Oldest → Newest did not restore original order.");
    }


    @Test(description = "IND-007: Pagination: next/prev page updates rows and returning restores page 1 snapshot.")
    public void pagination_nextPrev_changesRows_andRestores() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Capture emails/order signature on Page 1");
        List<String> page1Sig = makeOrderSignature(individuals);
        assumeNonEmpty(page1Sig, "page1Sig");

        step("Go to Page 2");
        individuals.goToPage(2); // uses existing pagination control

        step("Capture signature on Page 2 and compare with Page 1");
        List<String> page2Sig = makeOrderSignature(individuals);
        assumeNonEmpty(page2Sig, "page2Sig");

        // If there aren't actually 2+ pages, many UIs keep you on page 1. Guard for that:
        if (page1Sig.equals(page2Sig)) {
            throw new SkipException("Need 2+ pages to validate pagination (page 2 equals page 1).");
        }

        Assert.assertNotEquals(page2Sig, page1Sig, "Page 2 rows should differ from Page 1.");

        step("Go back to Page 1");
        individuals.goToPage(1);

        step("Re-capture signature on Page 1 and ensure it matches the original snapshot");
        List<String> page1SigAgain = makeOrderSignature(individuals);
        assumeNonEmpty(page1SigAgain, "page1SigAgain");
        Assert.assertEquals(page1SigAgain, page1Sig, "Returning to Page 1 did not restore the original set/order.");
    }


    @Test(groups = "ui-only", description = "IND-008: Page size selector changes rows per page (10/20).")
    public void pageSize_changesRowCount() throws InterruptedException {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Ensure we have at least 1 row to validate page size");
        List<String> namesInitial = individuals.getNamesOnCurrentPage();
        assumeNonEmpty(namesInitial, "namesInitial");

        // Helper to count rows via existing API (no new helpers)
        Supplier<Integer> rowCount = () -> individuals.getNamesOnCurrentPage().size();

        // Snapshot signature to detect content change after size switch
        List<String> sigBefore = makeOrderSignature(individuals);
        assumeNonEmpty(sigBefore, "sigBefore");

        // === 10 per page ===
        step("Change page size to 10");
        setPageSize(individuals, 10);

        step("Wait for page-size change to reflect (signature or count change)");
        waitUntilSignatureOrCountChanges(individuals, sigBefore, rowCount, 3000);

        // AntD often resets to page 1 after size change — normalize if needed
        if (!isActivePage("1")) { individuals.goToPage(1); }

        int count10 = rowCount.get();
        step("Assert row count is ≤ 10 and ≥ 1");
        Assert.assertTrue(count10 >= 1 && count10 <= 10,
                "Expected 1..10 rows after selecting 10/page, got " + count10);

        // === 20 per page ===
        List<String> sigBefore20 = makeOrderSignature(individuals);
        assumeNonEmpty(sigBefore20, "sigBefore20");

        step("Change page size to 20");
        setPageSize(individuals, 20);

        step("Wait for page-size change to reflect (signature or count change)");
        waitUntilSignatureOrCountChanges(individuals, sigBefore20, rowCount, 3000);

        if (!isActivePage("1")) { individuals.goToPage(1); }

        int count20 = rowCount.get();
        step("Assert row count is ≤ 20 and ≥ previous count (unless there are fewer total rows)");
        Assert.assertTrue(count20 >= 1 && count20 <= 20,
                "Expected 1..20 rows after selecting 20/page, got " + count20);

        if (count10 == 10) {
            Assert.assertTrue(count20 >= 10, "With 10/page showing 10 rows, 20/page should show at least 10; got " + count20);
        }
    }

    /* ===================== tiny test-side shims (no page changes) ===================== */

    private void setPageSize(IndividualsPage individuals, int size) {
        // Use the page-object method you added earlier
        individuals.setPageSize(size);
    }

    private void waitUntilSignatureOrCountChanges(IndividualsPage individuals, List<String> beforeSig, java.util.function.Supplier<Integer> rowCount, long timeoutMs) {
        long start = System.currentTimeMillis();
        int beforeCount = rowCount.get();
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                List<String> nowSig = makeOrderSignature(individuals);
                int nowCount = rowCount.get();
                if (!nowSig.equals(beforeSig) || nowCount != beforeCount) return;
                Thread.sleep(120);
            } catch (Exception ignored) {
                // transient DOM shifts
            }
        }
        // no hard fail here; assertions that follow will catch mismatches
    }

    // If you already have an isActivePage helper elsewhere, use that; otherwise:
    private boolean isActivePage(String n) {
        try {
            return driver().findElements(By.cssSelector("li.ant-pagination-item.ant-pagination-item-active"))
                    .stream().anyMatch(li -> n.equals(li.getText().trim()));
        } catch (Exception e) { return false; }
    }


    @Test(groups = "ui-only", description = "IND-009: Report column shows 'Pending' or a clickable link.")
    public void reportColumn_showsPendingOrLink() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Collect emails on current page");
        List<String> emails = individuals.getEmailsOnCurrentPage();
        assumeNonEmpty(emails, "emails");

        int maxToCheck = Math.min(8, emails.size());
        step("Validate first " + maxToCheck + " rows in the Report column");
        org.testng.asserts.SoftAssert softly = new org.testng.asserts.SoftAssert();

        for (int i = 0; i < maxToCheck; i++) {
            String email = emails.get(i);

            // retry once if we get a flaky read
            String status = individuals.getReportStatusByEmail(email);
            if (status == null || "NotFound".equalsIgnoreCase(status)) {
                try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                status = individuals.getReportStatusByEmail(email);
            }

            boolean ok;
            if (status != null && status.toLowerCase(java.util.Locale.ROOT).startsWith("link")) {
                // Accept both "Link:<href>" and "Link" (JS-click link without href)
                ok = true;
            } else {
                ok = "pending".equalsIgnoreCase(status);
            }

            softly.assertTrue(
                    ok,
                    "Row with email " + email + " has invalid Report state: " + status
            );
        }

        softly.assertAll();
    }


    @Test(groups = "ui-only", description = "IND-010: Open Report link navigates to Report page.")
    public void openReportLink_navigatesToReportPage() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        // optional: show more rows to reduce pagination
        // individuals.setPageSize(12);

        int maxPage = Math.max(1, individuals.getMaxPageNumber());
        ReportSummaryPage reportPage = null;
        String chosenEmail = null;

        // Iterate deterministically by page number
        for (int page = 1; page <= maxPage && reportPage == null; page++) {
            step("Go to page " + page);
            individuals.goToPage(page); // this already waits for active-page + content change

            step("Scan page " + page + " for a visible Report link");
            // Work on the current page DOM directly to avoid goToFirstPageIfPossible()
            List<WebElement> rows = driver().findElements(By.cssSelector(".ant-table .ant-table-tbody > tr.ant-table-row"));
            Assert.assertTrue(!rows.isEmpty(), "❌ No rows on page " + page);

            for (WebElement row : rows) {
                // Email (2nd column)
                String email = "";
                try {
                    email = row.findElement(By.cssSelector("td:nth-of-type(2) h4")).getText().trim();
                } catch (Exception ignored) {}

                // Report cell (3rd column)
                WebElement reportCell;
                try {
                    reportCell = row.findElement(By.cssSelector("td:nth-of-type(3)"));
                } catch (Exception e) {
                    continue;
                }

                // Skip if it's Pending
                String cellText = (reportCell.getText() == null) ? "" : reportCell.getText().trim();
                if ("pending".equalsIgnoreCase(cellText)) continue;

                // Look for a visible link-ish element
                List<WebElement> links = reportCell.findElements(By.cssSelector("a, [role='link'], button[role='link']"));
                if (links.isEmpty()) continue;

                WebElement link = links.get(0);
                if (!link.isDisplayed() || !link.isEnabled()) continue;

                // Force same-tab nav and click
                ((JavascriptExecutor) driver()).executeScript("arguments[0].scrollIntoView({block:'center'});", link);
                try { ((JavascriptExecutor) driver()).executeScript("arguments[0].removeAttribute('target');", link); } catch (Exception ignored) {}
                try { link.click(); } catch (Exception e) { ((JavascriptExecutor) driver()).executeScript("arguments[0].click();", link); }

                chosenEmail = email;
                reportPage = new ReportSummaryPage(driver()).waitUntilLoaded();
                break;
            }
        }

        Assert.assertNotNull(reportPage, "❌ Could not find any row with a Report link after scanning " + maxPage + " page(s).");
        Assert.assertTrue(reportPage.isLoaded(), "❌ Report page did not load correctly for email: " + chosenEmail);
    }


    @Test(groups = "ui-only", description = "IND-011: Row actions menu opens and shows expected options.")
    public void rowActionsMenu_opensAndShowsOptions() throws InterruptedException {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Pick first email on current page");
        List<String> emails = individuals.getEmailsOnCurrentPage();
        assumeNonEmpty(emails, "emails");
        String email = emails.get(0);

        step("Open actions (kebab) menu using page helper");
        boolean opened = individuals.openActionsMenuFor(email);   // uses kebabInRow + waitForMenuOpen
        if (!opened) { // fallback to the other existing opener
            individuals.openActionsFor(email);
        }

        step("Collect visible menu items from the open AntD dropdown/popover");
        // Grab the last visible dropdown/popover (AntD)
        By panelBy = By.cssSelector(".ant-dropdown:not([hidden]), .ant-popover:not([hidden])");
        WebElement panel = new WebDriverWait(driver(), java.time.Duration.ofSeconds(5))
                .until(org.openqa.selenium.support.ui.ExpectedConditions.visibilityOfElementLocated(panelBy));

        // Items usually are role='menuitem' or AntD item classes
        List<WebElement> items = panel.findElements(By.cssSelector("[role='menuitem'], .ant-dropdown-menu-item, .ant-menu-item"));
        List<String> labels = new java.util.ArrayList<>();
        for (WebElement it : items) {
            String t = it.getText();
            if (t != null && !t.trim().isBlank()) labels.add(t.trim());
        }

        // Check for the auto-reminder switch
        boolean hasSwitch = !panel.findElements(By.cssSelector(".ant-switch, [role='switch'], input[type='checkbox']")).isEmpty();

        step("Assert expected options are present");
        org.testng.asserts.SoftAssert softly = new org.testng.asserts.SoftAssert();
        softly.assertTrue(labels.stream().anyMatch(s -> s.equalsIgnoreCase("Edit info")),
                "Missing 'Edit info' in: " + labels);
        softly.assertTrue(labels.stream().anyMatch(s -> s.equalsIgnoreCase("Send reminder")),
                "Missing 'Send reminder' in: " + labels);
        softly.assertTrue(labels.stream().anyMatch(s -> s.equalsIgnoreCase("Remove user")),
                "Missing 'Remove user' in: " + labels);
        softly.assertTrue(hasSwitch || labels.stream().anyMatch(s -> s.toLowerCase().contains("auto reminder")),
                "Missing Auto reminder switch/label. Labels: " + labels);
        softly.assertAll();

        // optional cleanup (close the menu)
        try { driver().switchTo().activeElement().sendKeys(org.openqa.selenium.Keys.ESCAPE); } catch (Exception ignored) {}
    }

    @Test(groups = "ui-only", description = "IND-012: Auto reminder: toggle ON persists after refresh")
    public void autoReminder_toggleOn_persistsAfterRefresh() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Pick first email on current page");
        List<String> emails = individuals.getEmailsOnCurrentPage();
        assumeNonEmpty(emails, "emails");
        String email = emails.get(0);

        step("Open row actions menu for the chosen email");
        boolean opened = individuals.openActionsMenuFor(email);
        if (!opened) {
            // fallback to legacy opener if needed
            individuals.openActionsFor(email);
        }

        step("Ensure Auto reminder is ON using page helper (no-op if already ON)");
        individuals.setAutoReminder(email, true);

        step("Refresh the browser");
        driver().navigate().refresh();

        step("Wait for Individuals to reload and reopen the same row’s actions menu");
        individuals.waitUntilLoaded();
        boolean reOpened = individuals.openActionsMenuFor(email);
        if (!reOpened) {
            individuals.openActionsFor(email);
        }

        step("Assert the switch remains ON after refresh");
        boolean on = individuals.isAutoReminderOn(email);
        Assert.assertTrue(on, "❌ Auto reminder was not ON after refresh for: " + email);
    }


    @Test(groups = "ui-only", description = "IND-013: Auto reminder: toggle OFF persists after refresh")
    public void autoReminder_toggleOff_persistsAfterRefresh() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Pick first email on current page");
        List<String> emails = individuals.getEmailsOnCurrentPage();
        if (emails == null || emails.isEmpty()) {
            throw new SkipException("Need at least 1 row to validate Auto reminder persistence.");
        }
        String email = emails.get(0);

        step("Open row actions menu for the chosen email");
        boolean opened = individuals.openActionsMenuFor(email);
        if (!opened) {
            // fallback if you keep both helpers around
            individuals.openActionsFor(email);
        }

        step("Ensure Auto reminder is OFF (no-op if already OFF)");
        individuals.setAutoReminder(email, false);

        step("Refresh the browser");
        driver().navigate().refresh();

        step("Wait for Individuals to reload and reopen the same row’s actions menu");
        individuals.waitUntilLoaded();
        boolean reOpened = individuals.openActionsMenuFor(email);
        if (!reOpened) {
            individuals.openActionsFor(email);
        }

        step("Assert the switch remains OFF after refresh");
        boolean on = individuals.isAutoReminderOn(email);
        Assert.assertFalse(on, "❌ Auto reminder was not OFF after refresh for: " + email);
    }


    @Test(groups = "ui-only", description = "IND-014: Send reminder: opens modal with preview")
    public void sendReminder_opensModal_withPrefilledPreview() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Pick first email on current page");
        List<String> emails = individuals.getEmailsOnCurrentPage();
        if (emails == null || emails.isEmpty()) {
            throw new SkipException("Need at least 1 row to open actions.");
        }
        String email = emails.get(0);

        step("Open row actions menu for the chosen email");
        boolean opened = individuals.openActionsMenuFor(email);
        if (!opened) {
            individuals.openActionsFor(email); // fallback if you keep both helpers
        }

        step("Click 'Send reminder' in the open menu");
        individuals.clickSendReminderInOpenMenu();

        step("Wait for the 'Send reminder' modal to appear");
        WebDriverWait wdw = new WebDriverWait(driver(), java.time.Duration.ofSeconds(8));
        By modalRoot = By.xpath("(" +
                "//*[contains(@class,'ant-modal') and " +
                "  not(contains(@style,'display: none')) and " +
                "  descendant::*[contains(@class,'ant-modal-content')]" +
                "]" +
                ")[last()]");
        WebElement modal = wdw.until(org.openqa.selenium.support.ui.ExpectedConditions.visibilityOfElementLocated(modalRoot));

        step("Assert modal title mentions 'Send reminder'");
        String titleText = "";
        try {
            WebElement title = modal.findElement(By.cssSelector(".ant-modal-title, [id$='title']"));
            titleText = title.getText().trim();
        } catch (Throwable ignore) {}
        Assert.assertTrue(titleText.toLowerCase().contains("send reminder"),
                "❌ Modal title does not indicate 'Send reminder': '" + titleText + "'");

        step("Verify Body/Message is present and prefilled");
        // Prefer explicit placeholder from your DOM
        WebElement bodyArea = null;
        List<By> bodyLocators = List.of(
                By.xpath(".//textarea[@placeholder='Enter email content']"),
                By.xpath("(./descendant::textarea)[last()]"),
                By.xpath(".//*[@contenteditable='true' and normalize-space(string())!='']")
        );
        for (By loc : bodyLocators) {
            List<WebElement> found = modal.findElements(loc);
            if (!found.isEmpty()) { bodyArea = found.get(0); break; }
        }
        Assert.assertNotNull(bodyArea, "❌ Could not find the email body field in the modal.");

        String bodyText = bodyArea.getText();
        if (bodyText == null || bodyText.isBlank()) bodyText = bodyArea.getAttribute("value");
        Assert.assertTrue(bodyText != null && !bodyText.isBlank(), "❌ Body/preview appears empty.");

        step("Assert Cancel and 'Send reminder' buttons exist");
        List<WebElement> cancelBtns = modal.findElements(By.xpath(".//button[normalize-space()='Cancel']"));
        List<WebElement> sendBtns   = modal.findElements(By.xpath(".//button[normalize-space()='Send reminder']"));
        Assert.assertFalse(cancelBtns.isEmpty(), "❌ Cancel button not found in the reminder modal.");
        Assert.assertFalse(sendBtns.isEmpty(),   "❌ 'Send reminder' button not found in the reminder modal.");
    }

    @Test(groups = "ui-only", description = "IND-015: Send reminder: confirm shows success toast")
    public void sendReminder_confirm_showsSuccessToast() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Pick target email (prefer Pending, else first)");
        String email = null;
        for (String e : individuals.getEmailsOnCurrentPage()) {
            String status = safeLower(individuals.getReportStatusByEmail(e));
            if ("pending".equals(status)) { email = e; break; }
        }
        if (email == null) {
            List<String> emails = individuals.getEmailsOnCurrentPage();
            if (emails == null || emails.isEmpty()) throw new SkipException("No rows to test Send reminder.");
            email = emails.get(0);
        }

        step("Open actions → Send reminder (wait modal visible)");
        individuals.openSendReminderModalFor(email);

        step("Click 'Send reminder' in the modal (ensure enabled)");
        individuals.ensureModalSendEnabled();
        individuals.clickModalSendReminder();

        step("Wait for the modal to close (stability)");
        individuals.waitForModalToClose();

        step("Wait for success toast");
        String toast = individuals.waitForSuccessToast(); // returns String per your latest code
        Assert.assertNotNull(toast, "❌ Success toast did not appear.");
        step("Toast text: " + toast);
        Assert.assertTrue(
                toast.toLowerCase().contains("sent") ||
                        toast.toLowerCase().contains("success") ||
                        toast.toLowerCase().contains("reminder"),
                "❌ Unexpected toast text: " + toast
        );
    }
    private String safeLower(String s) { return s == null ? null : s.toLowerCase(); }

    @Test(groups = "ui-only", description = "IND-016: Send reminder: backend error shows error toast")
    public void sendReminder_confirm_showsErrorToast_onBackendFailure(){
        // --- Guard: CDP only on Chromium driver()s ---
        if (!(driver() instanceof HasDevTools)) {
            throw new SkipException("CDP not available on this driver(); cannot simulate backend error.");
        }

        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Pick target email (first visible)");
        List<String> emails = individuals.getEmailsOnCurrentPage();
        if (emails == null || emails.isEmpty()) {
            throw new SkipException("Need at least 1 row to send reminder.");
        }
        String email = emails.get(0);

        // --- Begin CDP: block the reminder endpoint so the request fails ---
        DevTools devTools = ((HasDevTools) driver()).getDevTools();
        devTools.createSession();
        devTools.send(Network.enable(
                Optional.of(10_000_000),  // maxTotalBufferSize (10 MB, pick whatever is sane)
                Optional.empty(),         // maxResourceBufferSize
                Optional.empty(),         // maxPostDataSize
                Optional.empty(),         // captureNetworkRequests (let Chrome default)
                Optional.empty()          // reportRawHeaders (let Chrome default)
        ));
        // Tweak patterns if your endpoint differs (e.g., "/api/*reminder*", "/send_reminder")
        devTools.send(Network.setBlockedURLs(List.of("*/reminder*", "*/send_reminder*")));


        try {
            step("Open actions → Send reminder (wait modal visible)");
            individuals.openSendReminderModalFor(email);

            step("Click 'Send reminder' in the modal (ensure enabled)");
            individuals.ensureModalSendEnabled();
            individuals.clickModalSendReminder();

            step("Wait for the modal to close (stability)");
            individuals.waitForModalToClose();

            step("Wait for error toast/alert");
            String toast = individuals.waitForErrorToast();
            Assert.assertNotNull(toast, "❌ Error toast/alert did not appear.");
            step("Error text: " + toast);
            Assert.assertTrue(
                    toast.toLowerCase().contains("error")
                            || toast.toLowerCase().contains("failed")
                            || toast.toLowerCase().contains("couldn")
                            || toast.toLowerCase().contains("unable"),
                    "❌ Unexpected (non-error) text: " + toast
            );

        } finally {
            // Always unblock + disable CDP to avoid side effects on later tests
            try { devTools.send(Network.setBlockedURLs(List.of())); } catch (Exception ignore) {}
            try { devTools.send(Network.disable()); } catch (Exception ignore) {}
            try { devTools.close(); } catch (Exception ignore) {}
        }
    }

    @Test(groups = "ui-only", description = "IND-017: Send reminder: cancel modal keeps state unchanged")
    public void sendReminder_cancel_keepsStateUnchanged() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Pick target email (first row)");
        List<String> emails = individuals.getEmailsOnCurrentPage();
        if (emails == null || emails.isEmpty()) {
            throw new SkipException("Need at least 1 row to open actions.");
        }
        String email = emails.get(0);

        step("Snapshot current row state");
        String beforeStatus = individuals.getReportStatusByEmail(email);
        Boolean beforeAuto  = null;
        try { beforeAuto = individuals.isAutoReminderOn(email); } catch (Throwable ignore) {}

        step("Open actions → 'Send reminder'");
        individuals.openSendReminderModalFor(email);   // opens menu + waits modal

        step("Click 'Cancel' and wait for modal to close");
        individuals.clickModalCancel();                // NEW helper (PO)
        individuals.waitForModalToClose();             // PO helper from IND-015

        step("Assert no toast/alert appears after Cancel (short window)");
        String anyToast = individuals.waitForSuccessToast();
        if (anyToast == null || anyToast.isEmpty()) {
            anyToast =   individuals.waitForErrorToast();
        }
        Assert.assertTrue(anyToast == null || anyToast.isBlank(), "❌ A toast/alert appeared after Cancel: " + anyToast);

        step("Re-read row state to confirm unchanged");
        String afterStatus = individuals.getReportStatusByEmail(email);
        Boolean afterAuto  = null;
        try { afterAuto = individuals.isAutoReminderOn(email); } catch (Throwable ignore) {}

        Assert.assertEquals(afterStatus, beforeStatus, "❌ Report status changed after Cancel.");
        if (beforeAuto != null && afterAuto != null) {
            Assert.assertEquals(afterAuto, beforeAuto, "❌ Auto reminder state changed after Cancel.");
        }
    }

    @Test(groups = "ui-only", description = "IND-018: Send reminder: close (X) keeps state unchanged")
    public void sendReminder_closeX_keepsStateUnchanged() throws InterruptedException {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Pick target email (first row)");
        List<String> emails = individuals.getEmailsOnCurrentPage();
        if (emails == null || emails.isEmpty()) throw new SkipException("Need at least 1 row to open actions.");
        String email = emails.get(0);

        step("Snapshot current row state");
        String beforeStatus = individuals.getReportStatusByEmail(email);
        Boolean beforeAuto = null;
        try { beforeAuto = individuals.isAutoReminderOn(email); } catch (Throwable ignore) {}

        step("Open actions → 'Send reminder' (modal visible)");
        individuals.openSendReminderModalFor(email);

        step("Close with the modal 'X' icon and wait for modal to close");
        individuals.clickModalCloseIcon();
        individuals.waitForModalToClose();

        step("Assert no toast/alert appears (short window)");
        String anyToast = individuals.waitForSuccessToast();
        if (anyToast == null || anyToast.isEmpty()) {
            anyToast =   individuals.waitForErrorToast();
        }
        Assert.assertTrue(anyToast == null || anyToast.isBlank(), "❌ A toast/alert appeared after closing with X: " + anyToast);

        step("Re-read row state to confirm unchanged");
        String afterStatus = individuals.getReportStatusByEmail(email);
        Boolean afterAuto = null;
        try { afterAuto = individuals.isAutoReminderOn(email); } catch (Throwable ignore) {}

        Assert.assertEquals(afterStatus, beforeStatus, "❌ Report status changed after closing with X.");
        if (beforeAuto != null && afterAuto != null) {
            Assert.assertEquals(afterAuto, beforeAuto, "❌ Auto reminder state changed after closing with X.");
        }
    }

    @Test(groups = "ui-only", description = "IND-018: Edit info: opens with current values")
    public void editInfo_opensWithCurrentValues() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Pick a target row (first email on current page)");
        List<String> emails = individuals.getEmailsOnCurrentPage();
        if (emails == null || emails.isEmpty()) {
            throw new SkipException("Need at least 1 row to open Edit info.");
        }
        String email = emails.get(0);

        step("Open row menu → 'Edit info' (modal visible)");
        individuals.openActionsMenuFor(email);
        individuals.clickEditInfoInOpenMenu();

        step("Read Edit info modal fields");
        Map<String, String> modalVals = individuals.readEditInfoFields();

        step("Verify Email matches the table row");
        Assert.assertEquals(
                modalVals.getOrDefault("email","").trim(),
                email.trim(),
                "❌ Modal Email does not match table Email."
        );

        step("Verify First/Last fields are present (allow blanks if Pending)");
        Assert.assertNotNull(modalVals.get("firstName"), "❌ First name field missing.");
        Assert.assertNotNull(modalVals.get("lastName"),  "❌ Last name field missing.");

// --- Optional: strict compare when table shows a real name ---
        String tableName = individuals.getNameByEmail(email); // use your existing helper
        System.out.println(tableName);
        String tn = tableName == null ? "" : tableName.trim();
        boolean isPendingOrBlank = tn.isBlank() || tn.equalsIgnoreCase("pending name");

        if (!isPendingOrBlank) {
            // Split once: "First Last" (keep the rest in last)
            String[] parts = tn.split("\\s+", 2);
            String expectedFirst = parts[0];
            String expectedLast  = parts.length > 1 ? parts[1] : ""; // allow single-token names

            String actualFirst = modalVals.getOrDefault("firstName","").trim();
            String actualLast  = modalVals.getOrDefault("lastName","").trim();

            Assert.assertEquals(actualFirst, expectedFirst,
                    "❌ Modal First name does not match table First name.");
            // Only assert last if table actually has a last part
            if (!expectedLast.isBlank()) {
                Assert.assertEquals(actualLast, expectedLast,
                        "❌ Modal Last name does not match table Last name.");
            }
        }


        step("Verify Cancel / Save changes buttons exist");
        Assert.assertTrue(
                individuals.editInfoModalHasButtons("Cancel", "Save changes"),
                "❌ Expected Cancel/Save changes buttons not present in Edit info modal."
        );
    }

    @Test(groups = "ui-only", description = "IND-019: Edit info: update email persists and shows success")
    public void editInfo_updateEmail_persistsAndShowsSuccess() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Pick target row (first email on current page)");
        List<String> emails = individuals.getEmailsOnCurrentPage();
        if (emails == null || emails.isEmpty()) {
            throw new SkipException("Need at least 1 row to open Edit info.");
        }
        String oldEmail = emails.get(0);

        step("Open row menu → 'Edit info' (modal visible)");
        individuals.openActionsMenuFor(oldEmail);
        individuals.clickEditInfoInOpenMenu();

        step("Generate a unique, valid email and set it in the modal");
        String stamp = String.valueOf(System.currentTimeMillis());
        // keep same local-part if it already has '+', else add one; domain preserved
        String newEmail = oldEmail.replace("@", "+" + stamp + "@").replace("++", "+"); // unique but readable
        individuals.setEditInfoEmail(newEmail);  // tiny helper (or use your existing)

        step("Click 'Save changes' and wait for modal to close");
        individuals.clickEditInfoSaveChanges();  // tiny helper (or use your existing)
        individuals.waitForModalToClose();       // existing helper from earlier

        step("Wait for success toast");
        String toast = individuals.waitForSuccessToast(); // existing helper
        Assert.assertNotNull(toast, "❌ Success toast did not appear after saving Edit info.");
        step("Toast text: " + toast);

        step("Refresh Individuals and verify new Email appears in the table");
        driver().navigate().refresh();
        individuals.waitUntilLoaded();
        // Reuse your table accessor: row should be findable by the NEW email
        String nameForNewEmail = individuals.getNameByEmail(newEmail); // returns "" if not found
        Assert.assertFalse(
                nameForNewEmail == null || nameForNewEmail.isBlank(),
                "❌ Row with the new email was not found after refresh: " + newEmail
        );
    }

    @Test(groups = "ui-only", description = "IND-020: Edit info: invalid email blocks save")
    public void editInfo_invalidEmail_blocksSave() throws InterruptedException {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Pick target row (first email on current page)");
        List<String> emails = individuals.getEmailsOnCurrentPage();
        if (emails == null || emails.isEmpty()) {
            throw new SkipException("Need at least 1 row to open Edit info.");
        }
        String email = emails.get(0);

        step("Open row menu → 'Edit info' (modal visible)");
        individuals.openActionsMenuFor(email);
        individuals.clickEditInfoInOpenMenu();

        step("Type an invalid email and blur to trigger validation");
        individuals.setEditInfoEmail("invalid.email"); // missing '@'
        individuals.blurEditInfoEmail();               // tiny helper to trigger validation

        step("Assert no validation text appears");
        String err = individuals.getEditEmailValidationText();   // should return "" or null
        Assert.assertTrue(err == null || err.isBlank(),
                "❌ Validation text SHOULD NOT appear for invalid email, only Save should be disabled.");

        step("Assert 'Save changes' remains disabled");
        Assert.assertFalse(individuals.isEditInfoSaveEnabled(),
                "❌ 'Save changes' must remain disabled when email is invalid.");
    }

    @Test(groups = "ui-only", description = "IND-021: Edit info: duplicate email shows error and does not persist")
    public void editInfo_duplicateEmail_showsError_andDoesNotPersist() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Require at least 2 rows to pick a duplicate value");
        List<String> emails = individuals.getEmailsOnCurrentPage();
        if (emails == null || emails.size() < 2) {
            throw new SkipException("Need at least 2 rows to test duplicate email.");
        }
        String targetEmail   = emails.get(0); // row A (we will edit this one)
        String duplicateEmail = emails.get(1); // row B (existing value to trigger duplicate)

        // Safety: if somehow both are the same (shouldn't), pick another
        for (int i = 1; i < emails.size() && duplicateEmail.equalsIgnoreCase(targetEmail); i++) {
            duplicateEmail = emails.get(i);
        }
        if (duplicateEmail.equalsIgnoreCase(targetEmail)) {
            throw new SkipException("Could not find a different email on current page to use as duplicate.");
        }

        step("Open row menu → 'Edit info' (modal visible) for: " + targetEmail);
        individuals.openActionsMenuFor(targetEmail);
        individuals.clickEditInfoInOpenMenu();

        step("Set Email to an existing value (duplicate) and click Save");
        individuals.setEditInfoEmail(duplicateEmail);
        individuals.clickEditInfoSaveChanges();

        step("Expect error (inline or toast) and no success");
        String inlineErr = individuals.getEditEmailValidationText(); // "" if none
        String errToast  = individuals.waitForErrorToast();          // null if none
        String okToast   = individuals.waitForSuccessToast();        // null if none

        System.out.println(inlineErr);
        System.out.println(errToast);

        Assert.assertTrue(
                (inlineErr != null && !inlineErr.isBlank()) || (errToast != null && !errToast.isBlank()),
                "❌ Neither inline validation nor error toast appeared for duplicate email."
        );
        Assert.assertTrue(okToast == null || okToast.isBlank(), "❌ Unexpected success toast: " + okToast);

        step("Close the modal and verify change did not persist");
        try { individuals.clickModalCancel(); } catch (Throwable ignore) {} // if modal stayed open
        try { individuals.waitForModalToClose(); } catch (Throwable ignore) {}

        driver().navigate().refresh();
        individuals.waitUntilLoaded();

        // The original row should still be findable by its original email
        String name = individuals.getNameByEmail(targetEmail);
        Assert.assertFalse(name == null || name.isBlank(),
                "❌ Original row with email " + targetEmail + " not found after duplicate attempt.");
    }

//    @Test(groups = "ui-only", description = "IND-022: Edit info: no-change submit keeps Save disabled")
//    public void editInfo_noChange_keepsSaveDisabled() {
//        step("Start fresh session (login + Dashboard)");
//        DashboardPage dashboard = BaseTest.startFreshSession(driver());
//
//        step("Open Individuals page");
//        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
//        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");
//
//        step("Pick target row (first email on current page)");
//        List<String> emails = individuals.getEmailsOnCurrentPage();
//        if (emails == null || emails.isEmpty()) {
//            throw new SkipException("Need at least 1 row to open Edit info.");
//        }
//        String email = emails.get(0);
//
//        step("Open row menu → 'Edit info' (modal visible)");
//        individuals.openActionsMenuFor(email);
//        individuals.clickEditInfoInOpenMenu();
//
//        step("'Save changes' must remain disabled when no fields are modified");
//        // Give the UI a brief moment to settle any initial validation/dirty checks
//        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
//        boolean everEnabled = false;
//        while (System.nanoTime() < deadline) {
//            if (individuals.isEditInfoSaveEnabled()) { // PO helper
//                everEnabled = true;
//                break;
//            }
//            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
//        }
//        Assert.assertFalse(everEnabled, "❌ 'Save changes' became enabled without any changes.");
//
//        step("Close the modal to leave UI clean");
//        try { individuals.clickModalCancel(); } catch (Throwable ignore) {}
//        try { individuals.waitForModalToClose(); } catch (Throwable ignore) {}
//    }

    @Test(groups = "ui-only", description = "IND-023: Remove user: opens confirmation modal")
    public void removeUser_opensConfirmationModal() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Pick target row (first email on current page)");
        List<String> emails = individuals.getEmailsOnCurrentPage();
        if (emails == null || emails.isEmpty()) {
            throw new SkipException("Need at least 1 row to open actions.");
        }
        String email = emails.get(0);

        step("Open row menu → 'Remove user' (wait confirmation modal)");
        individuals.openActionsMenuFor(email);
        individuals.clickRemoveUserInOpenMenu();

        step("Verify 'Remove Member' modal title and buttons");
        String title = individuals.getRemoveUserModalTitle();     // PO helper
        Assert.assertTrue(title.toLowerCase().contains("remove"),
                "❌ Modal title does not indicate removal: '" + title + "'");

        Assert.assertTrue(individuals.removeUserModalHasButtons("Cancel", "Remove"),
                "❌ Expected Cancel/Remove buttons not present in Remove modal.");

        step("Close the modal to leave UI clean");
        individuals.clickModalCancel();   // you already have this from prior tests
        individuals.waitForModalToClose();
    }

    @Test(groups = "ui-only", description = "IND-024: Remove user: cancel keeps row")
    public void removeUser_cancel_keepsRow() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Pick target row (first email on current page)");
        List<String> emails = individuals.getEmailsOnCurrentPage();
        if (emails == null || emails.isEmpty()) {
            throw new SkipException("Need at least 1 row to open actions.");
        }
        String email = emails.get(0);

        step("Open row menu → 'Remove user' (confirmation modal visible)");
        individuals.openRemoveUserModalFor(email);

        step("Click 'Cancel' and wait for modal to close");
        individuals.clickRemoveCancel();
        individuals.waitForModalToClose();

        step("Assert no success toast/alert appears (short window)");
        String anyToast = individuals.waitForSuccessToast();
        if (anyToast == null || anyToast.isEmpty()) {
            anyToast =   individuals.waitForErrorToast();
        }

        Assert.assertTrue(anyToast == null || anyToast.isBlank(),
                "❌ A toast/alert appeared after Cancel: " + anyToast);

        step("Verify the row still exists");
        String nameStillThere = individuals.getNameByEmail(email);
        Assert.assertFalse(nameStillThere == null || nameStillThere.isBlank(),
                "❌ Row was removed after Cancel for email: " + email);
    }

    @Test(groups = "ui-only", description = "IND-025: Remove user: confirm shows success and row disappears")
    public void removeUser_confirmShowsToastAndRowDisappears() throws InterruptedException {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Pick a visible email from first row (safe target to remove)");
        String email = individuals.getFirstRowEmailOrThrow();
        step("Target email to remove: " + email);

        step("Open row menu → 'Remove user'");
        Assert.assertTrue(individuals.openActionsMenuFor(email), "❌ Could not open actions menu for: " + email);
        individuals.clickRemoveUserInOpenMenu();

        step("Wait for 'Remove Member' modal and confirm");
        individuals.waitForRemoveUserModal();          // from previous helpers
        individuals.clickRemoveConfirm();              // from previous helpers

        step("Wait for success toast");
        String toast = individuals.waitForSuccessToast();   // <-- String, not WebElement
        Assert.assertTrue(toast != null && !toast.isBlank(),
                "❌ Success toast text was empty or null after removal");


        step("Verify the row is gone (iterate with goToPage)");
        individuals.goToFirstPageIfPossible(); // your existing helper

        int last = individuals.getMaxPageNumber();
        last = Math.min(last, 100); // cap if you want

        boolean present = false;
        for (int p = 1; p <= last; p++) {
            individuals.goToPage(p); // <— your resilient pager
            java.util.List<String> emails = individuals.getEmailsOnCurrentPage(); // existing getter
            if (emails.stream().anyMatch(e -> e != null && e.trim().equals(email))) {
                present = true;
                break;
            }
        }

        Assert.assertFalse(present, "❌ Row for " + email + " still present after confirm remove");

    }


    @Test(groups = {"smoke"}, description = "SM04: Invite New Individual via Shop – happy path, appears in Individuals list.")
    public void smoke_inviteNewIndividual_happyPath() throws Exception {

        // ----- config / constants -----
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }
        System.out.println("[AdminCreds] email=" + ADMIN_USER + " | passLen=" + ADMIN_PASS.length());

        // For SM04 we **can** still use MailSlurp alias so we don’t spam real inboxes,
        // but we STOP after verifying Individuals (no email assertions here).
        step("Resolve shared MailSlurp inbox and generate unique +alias recipient");
        final InboxDto inbox = BaseTest.getSuiteInbox() != null
                ? BaseTest.getSuiteInbox()
                : BaseTest.requireInboxOrSkip();

        final String testEmail = MailSlurpUtils.uniqueAliasEmail(inbox, "sm04");
        System.out.println("📮 Using shared inbox: " + inbox.getId() + " <" + inbox.getEmailAddress() + ">");
        System.out.println("📧 SM04 recipient (alias): " + testEmail);

        // (Optional) clean inbox to reduce noise for other tests
        try { MailSlurpUtils.clearInboxEmails(inbox.getId()); } catch (Throwable ignored) {}

        // -------------------- APP FLOW THROUGH SHOP --------------------
        step("Login as admin");
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();
        DashboardPage dashboardPage =
                loginPage.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Shop and start True Tilt purchase flow");
        ShopPage shopPage = dashboardPage.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");
        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
        sel.selectClientOrIndividual();
        sel.clickNext();

        step("Manual entry for 1 individual (use the aliased MailSlurp address)");
        String uniq = String.valueOf(System.currentTimeMillis());
        String firstName = "SM04";
        String lastName  = "Auto-" + uniq;

        AssessmentEntryPage entryPage = new AssessmentEntryPage(driver())
                .waitUntilLoaded()
                .selectManualEntry()
                .enterNumberOfIndividuals("1");

        entryPage.fillUserDetailsAtIndex(1, firstName, lastName, testEmail);

        step("Review order (Preview)");
        OrderPreviewPage preview = entryPage.clickProceedToPayment().waitUntilLoaded();

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
        driver().navigate().to(joinUrl(Config.getBaseUrl(), "/dashboard/orders/confirmation"));

        // -------------------- ASSERT ON INDIVIDUALS PAGE --------------------
        step("Open Individuals page and assert the newly invited user appears");
        new IndividualsPage(driver())
                .open(Config.getBaseUrl())
                .assertAppearsWithEvidence(Config.getBaseUrl(), testEmail);

        System.out.println("✅ SM04: User appears in Individuals: " + testEmail);
    }


    @Test(groups = {"smoke"}, description = "SM05: Resend invitation (Send reminder) for a pending individual.")
    public void smoke_resendInvitation_forPendingIndividual() throws Exception {

        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        // -------------------- FIND OR CREATE PENDING INDIVIDUAL --------------------
        step("Try to find a Pending individual on the current page");
        String targetEmail = individuals.findPendingEmailOnCurrentPage();

        if (targetEmail == null) {
            step("No Pending row found on this page → create a new pending individual via Shop (SM04-style)");
            targetEmail = createPendingIndividualViaShop(dashboard);

            step("Re-open Individuals page after creating the pending invite");
            individuals = new IndividualsPage(driver())
                    .open(Config.getBaseUrl())
                    .waitUntilLoaded();
        }

        Assert.assertNotNull(
                targetEmail,
                "❌ Could not find or create a Pending individual for SM05."
        );
        System.out.println("ℹ Using Pending individual for SM05: " + targetEmail);

        // -------------------- RESEND / SEND REMINDER FLOW --------------------
        step("Open 'Send reminder' modal via row actions for " + targetEmail);
        individuals.openSendReminderModalFor(targetEmail);   // uses openActionsMenuFor + clickSendReminderInOpenMenu
        individuals.ensureModalSendEnabled();                // makes sure the Send button is enabled

        step("Click 'Send reminder' in the modal");
        individuals.clickModalSendReminder();
        individuals.waitForModalToClose();

        step("Wait for success toast / notification");
        String toastText = individuals.waitForSuccessToast();
        Assert.assertNotNull(toastText, "❌ No success toast appeared after sending reminder.");
        String toastLc = toastText.toLowerCase(Locale.ROOT);
        Assert.assertTrue(
                toastLc.contains("sent") || toastLc.contains("reminder") || toastLc.contains("invitation"),
                "❌ Unexpected success toast text after resend/reminder: '" + toastText + "'"
        );

        System.out.println("✅ SM05: Reminder/resend confirmed by toast for " + targetEmail);
    }



    /**
     * SM05b: Resend invitation (Send reminder) sends a reminder email to the pending individual.
     * Flow:
     *  - Create a pending individual using a MailSlurp +alias address
     *  - Open Individuals and trigger "Send reminder" from the row actions
     *  - Assert success toast
     *  - Assert a reminder email arrives to that alias with correct subject/body/CTA
     */
    @Test(groups = {"smoke"}, description = "SM05b: Resend invitation sends reminder email to pending individual (MailSlurp).")
    public void smoke_resendInvitation_sendsReminderEmailToPendingIndividual() throws Exception {
        // ------------------------------------------------------------------
        // Admin config
        // ------------------------------------------------------------------
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }
        System.out.println("[AdminCreds] email=" + maskEmail(ADMIN_USER) + " | passLen=" + ADMIN_PASS.length());

        // ------------------------------------------------------------------
        // Mail / content expectations (from live reminder template)
        // ------------------------------------------------------------------
        final Duration EMAIL_TIMEOUT          = Duration.ofSeconds(120);
        final String SUBJECT_EXPECTED_PREFIX  = "Reminder:";  // "Reminder: Complete Your Assessment"
        final String SUBJECT_EXPECTED_NEEDLE  = "Assessment";
        final String BODY_HEADER_NEEDLE       = "Don't Forget to Complete Your Tilt365 Assessment";
        final String BODY_PENDING_NEEDLE      = "pending Tilt365 assessment";
        final String BODY_USERNAME_LABEL      = "Username";
        final String CTA_TEXT                 = "Complete Assessment";

        // ------------------------------------------------------------------
        // MailSlurp: shared inbox + unique alias
        // ------------------------------------------------------------------
        step("Resolve shared MailSlurp inbox + unique alias for SM05b");
        final InboxDto inbox = BaseTest.getSuiteInbox() != null
                ? BaseTest.getSuiteInbox()
                : BaseTest.requireInboxOrSkip();

        final String testEmail  = MailSlurpUtils.uniqueAliasEmail(inbox, "sm05b-resend");
        final String aliasToken = MailSlurpUtils.extractAliasToken(testEmail);

        System.out.println("📮 Using shared inbox: " + inbox.getId() + " <" + inbox.getEmailAddress() + ">");
        System.out.println("📧 SM05b recipient (alias): " + testEmail);

        // Keep inbox clean to avoid matching stale messages
        try { MailSlurpUtils.clearInboxEmails(inbox.getId()); } catch (Throwable ignored) {}

        // ------------------------------------------------------------------
        // A) Create a pending individual with that email (Shop → Stripe flow)
        // ------------------------------------------------------------------
        step("Login as admin");
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();
        DashboardPage dashboard =
                loginPage.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboard.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Shop and start True Tilt purchase flow (manual entry, 1 individual)");
        ShopPage shopPage = dashboard.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");
        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
        sel.selectClientOrIndividual();
        sel.clickNext();

        String uniq      = String.valueOf(System.currentTimeMillis());
        String firstName = "SM05b";
        String lastName  = "Resend-" + uniq;

        AssessmentEntryPage entryPage = new AssessmentEntryPage(driver())
                .waitUntilLoaded()
                .selectManualEntry()
                .enterNumberOfIndividuals("1");
        entryPage.fillUserDetailsAtIndex(1, firstName, lastName, testEmail);

        step("Proceed to payment preview");
        OrderPreviewPage preview = entryPage.clickProceedToPayment().waitUntilLoaded();

        step("Stripe: fetch session + metadata.body");
        String stripeUrl = preview.proceedToStripeAndGetCheckoutUrl();
        String sessionId = extractSessionIdFromUrl(stripeUrl);
        Assert.assertNotNull(sessionId, "❌ Could not parse Stripe session id from URL (SM05b)");

        String bodyJson = StripeCheckoutHelper.fetchCheckoutBodyFromStripe(sessionId);
        Assert.assertNotNull(bodyJson, "❌ metadata.body not found in Checkout Session (SM05b)");

        step("Stripe: trigger checkout.session.completed via CLI");
        StripeCheckoutHelper.TriggerResult trig =
                StripeCheckoutHelper.triggerCheckoutCompletedWithBody(bodyJson);
        System.out.println("[Stripe] Triggered eventId=" + trig.eventId +
                (trig.requestLogUrl != null ? " | requestLog=" + trig.requestLogUrl : ""));

        step("Navigate to post-payment confirmation");
        driver().navigate().to(joinUrl(Config.getBaseUrl(), "/dashboard/orders/confirmation"));

        step("Open Individuals and assert the user appears");
        IndividualsPage individuals = new IndividualsPage(driver())
                .open(Config.getBaseUrl())
                .waitUntilLoaded();
        individuals.assertAppearsWithEvidence(Config.getBaseUrl(), testEmail);

        // Optional sanity: ensure their report is Pending
        String reportStatus = individuals.getReportStatusByEmail(testEmail);
        System.out.println("ℹ Report status for " + testEmail + " = " + reportStatus);

        // ------------------------------------------------------------------
        // B) Resend invitation ("Send reminder") from Individuals
        // ------------------------------------------------------------------
        step("Clear inbox right before resend so we only capture the reminder email");
        try { MailSlurpUtils.clearInboxEmails(inbox.getId()); } catch (Throwable ignored) {}

        step("Open 'Send reminder' modal via row actions");
        individuals.openSendReminderModalFor(testEmail);  // POM helper: openActionsMenuFor + clickSendReminderInOpenMenu
        individuals.ensureModalSendEnabled();

        step("Click 'Send reminder' and wait for modal to close");
        individuals.clickModalSendReminder();
        individuals.waitForModalToClose();

        step("Wait for success toast");
        String toastText = individuals.waitForSuccessToast();
        Assert.assertNotNull(toastText, "❌ No success toast appeared after sending reminder.");
        System.out.println("✅ Toast after resend: " + toastText);

        // ------------------------------------------------------------------
        // C) Assert reminder email arrived to the alias with expected content
        // ------------------------------------------------------------------
        step("Wait for reminder email addressed to the alias");
        System.out.println("[Email] Waiting up to " + EMAIL_TIMEOUT.toSeconds() +
                "s for reminder email to " + testEmail + "…");

        Email email = MailSlurpUtils.waitForEmailMatching(
                inbox.getId(),
                EMAIL_TIMEOUT.toMillis(),
                1500L,
                true,
                // Must be to our alias + look like the reminder template
                MailSlurpUtils.addressedToAliasToken(aliasToken)
                        .and(MailSlurpUtils.subjectContains("Reminder"))
                        .and(MailSlurpUtils.subjectContains("Assessment"))
                        .and(MailSlurpUtils.bodyContains("Complete Assessment"))
        );
        Assert.assertNotNull(
                email,
                "❌ No reminder email arrived addressed to alias " + aliasToken +
                        " within " + EMAIL_TIMEOUT
        );

        final String subject = Objects.toString(email.getSubject(), "");
        final String from    = Objects.toString(email.getFrom(), "");
        final String rawBody = Objects.toString(email.getBody(), "");
        final String safeBody = MailSlurpUtils.safeEmailBody(email); // HTML or text normalized

        System.out.printf("📨 Reminder Email — From: %s | Subject: %s%n", from, subject);

        // Subject: "Reminder: Complete Your Assessment"
        String subjLc = subject.toLowerCase(Locale.ROOT);
        Assert.assertTrue(
                subjLc.startsWith(SUBJECT_EXPECTED_PREFIX.toLowerCase(Locale.ROOT)),
                "❌ Reminder subject should start with '" + SUBJECT_EXPECTED_PREFIX + "'. Got: " + subject
        );
        Assert.assertTrue(
                subjLc.contains(SUBJECT_EXPECTED_NEEDLE.toLowerCase(Locale.ROOT)),
                "❌ Reminder subject should mention '" + SUBJECT_EXPECTED_NEEDLE + "'. Got: " + subject
        );

        // Sender: tilt365 via sendgrid
        String fromLc = from.toLowerCase(Locale.ROOT);
        Assert.assertTrue(
                fromLc.contains("tilt365") || fromLc.contains("sendgrid"),
                "❌ Unexpected reminder sender: " + from
        );

        // Body content: header, "pending assessment", username line, alias email, CTA text
        String body = safeBody != null ? safeBody : rawBody;
        String bodyLc = body.toLowerCase(Locale.ROOT);

        Assert.assertTrue(
                body.contains(BODY_HEADER_NEEDLE),
                "❌ Body missing header line: '" + BODY_HEADER_NEEDLE + "'. Body snippet: " + snippet(body)
        );
        Assert.assertTrue(
                bodyLc.contains(BODY_PENDING_NEEDLE.toLowerCase(Locale.ROOT)),
                "❌ Body missing pending-assessment text like '" + BODY_PENDING_NEEDLE + "'. Body snippet: " + snippet(body)
        );
        Assert.assertTrue(
                body.contains(BODY_USERNAME_LABEL),
                "❌ Body missing 'Username' label. Body snippet: " + snippet(body)
        );
        Assert.assertTrue(
                body.contains(testEmail),
                "❌ Body missing the alias email as username: " + testEmail
        );
        Assert.assertTrue(
                body.contains(CTA_TEXT),
                "❌ Body missing CTA text '" + CTA_TEXT + "'. Body snippet: " + snippet(body)
        );

        // CTA link exists and points to expected host
        String ctaHref = MailSlurpUtils.extractLinkByAnchorText(email, CTA_TEXT);
        if (ctaHref == null) ctaHref = MailSlurpUtils.extractFirstLink(email);
        Assert.assertNotNull(ctaHref, "❌ Could not find a CTA link in the reminder email.");

        System.out.println("🔗 Reminder CTA link: " + ctaHref);
        String hrefLc = ctaHref.toLowerCase(Locale.ROOT);
        Assert.assertTrue(
                hrefLc.contains("sendgrid.net") || hrefLc.contains("tilt365"),
                "❌ Reminder CTA link host unexpected: " + ctaHref
        );

        System.out.println("✅ SM05b: Resend invitation produced a valid reminder email for " + testEmail);
    }

    /**
     * Small helper to print a short body snippet in assertion messages.
     */
    private static String snippet(String body) {
        if (body == null) return "";
        body = body.replaceAll("\\s+", " ").trim();
        return body.length() <= 200 ? body : body.substring(0, 200) + "…";
    }


    @Test(groups = {"smoke"}, description = "SM06: Cancel pending invitation removes/updates the invite.")
    public void smoke_cancelPendingInvitation() throws Exception {

        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver());

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        // -------------------- FIND OR CREATE PENDING INDIVIDUAL --------------------
        step("Try to find a Pending individual on the current page");
        String targetEmail = individuals.findPendingEmailOnCurrentPage();

        if (targetEmail == null) {
            step("No Pending row found → create a new pending individual via Shop flow");
            targetEmail = createPendingIndividualViaShop(dashboard);

            step("Re-open Individuals page to see the new pending invite");
            individuals = new IndividualsPage(driver())
                    .open(Config.getBaseUrl())
                    .waitUntilLoaded();
        }

        Assert.assertNotNull(targetEmail, "❌ Could not find or create a Pending individual for SM06.");
        System.out.println("ℹ Using Pending individual for SM06: " + targetEmail);

        // Sanity: before state is Pending (any page)
        String beforeStatus = individuals.getReportStatusByEmail(targetEmail);
        System.out.println("ℹ Before cancel, report status = " + beforeStatus);

        // -------------------- OPEN "CANCEL/REMOVE" MODAL --------------------
        step("Open Remove/Cancel invitation modal from row actions for " + targetEmail);
        individuals.openRemoveUserModalFor(targetEmail);

        String modalTitle = individuals.getRemoveUserModalTitle();
        System.out.println("ℹ Remove/Cancel modal title = " + modalTitle);
        Assert.assertTrue(
                modalTitle.toLowerCase(Locale.ROOT).contains("remove") ||
                        modalTitle.toLowerCase(Locale.ROOT).contains("cancel"),
                "❌ Unexpected modal title for cancel invitation: '" + modalTitle + "'"
        );

        Assert.assertTrue(
                individuals.removeUserModalHasButtons("Cancel", "Remove"),
                "❌ Remove/Cancel modal does not show expected buttons 'Cancel' and 'Remove'"
        );

        // -------------------- CONFIRM CANCELLATION --------------------
        step("Confirm removal/cancellation in the modal");
        individuals.clickRemoveConfirm();

        // Wait only on the *current* page – no pagination, no table refresh gymnastics
        step("Wait until the row disappears from the current page");
        IndividualsPage finalIndividuals = individuals;
        String finalTargetEmail = targetEmail;
        boolean disappearedOnCurrentPage = new WebDriverWait(driver(), Duration.ofSeconds(10))
                .until(d -> !finalIndividuals.isUserListedByEmailOnCurrentPage(finalTargetEmail));
        System.out.println("ℹ disappearedOnCurrentPage = " + disappearedOnCurrentPage);

        // Optional toast (does not fail if missing)
        String toast = individuals.waitForSuccessToast();
        if (toast != null) {
            System.out.println("✅ Success toast after cancel/remove: " + toast);
        } else {
            System.out.println("⚠ No success toast detected after cancel/remove (not treated as failure).");
        }

        // -------------------- ASSERT RESULT --------------------
        Assert.assertTrue(
                disappearedOnCurrentPage,
                "❌ Cancel invitation failed: row for " + targetEmail + " is still visible on the Individuals page."
        );

        // Optional light double-check without pagination scanning:
        // individuals.reloadWithBuster(Config.getBaseUrl());
        // Assert.assertFalse(
        //         individuals.isUserListedByEmailOnCurrentPage(targetEmail),
        //         "Row still visible on first page after reload for " + targetEmail
        // );
    }


    /**
     * SM06-v2:
     *  - Purchase a new True Tilt assessment for a fresh aliased email.
     *  - Verify the invite email arrives and capture the CTA link.
     *  - Cancel the pending invitation from Individuals.
     *  - Navigate to the original CTA link and assert we CANNOT start the assessment.
     */
    @Test(groups = {"smoke", "known-bug"}, description = "SM06-v2: Cancel pending invitation makes the email CTA unusable.")
    public void smoke_cancelPendingInvitation_emailLinkBecomesInvalid() throws Exception {

        // -------------------- CONFIG / ADMIN CREDS --------------------
        final String ADMIN_USER = Config.getAny("admin.email", "ADMIN_EMAIL", "ADMIN_USER");
        final String ADMIN_PASS = Config.getAny("admin.password", "ADMIN_PASSWORD", "ADMIN_PASS");
        if (ADMIN_USER == null || ADMIN_USER.isBlank() || ADMIN_PASS == null || ADMIN_PASS.isBlank()) {
            throw new SkipException("[Config] Admin credentials missing (admin.email/.password or ADMIN_* env).");
        }
        System.out.println("[AdminCreds] email=" + maskEmail(ADMIN_USER) + " | passLen=" + ADMIN_PASS.length());

        final Duration EMAIL_TIMEOUT   = Duration.ofSeconds(120);
        final String SUBJECT_NEEDLE    = "assessment";          // subject contains this
        final String REMINDER_NEEDLE   = "Reminder";            // many reminder templates contain this
        final String CTA_TEXT_PRIMARY  = "Complete Assessment"; // from your screenshot
        final String CTA_TEXT_FALLBACK = "Start Assessment";    // backup if template changes

        System.setProperty("mailslurp.debug", "true");

        // -------------------- MAILSLURP: PREP INBOX + ALIAS --------------------
        step("Resolve shared MailSlurp inbox and generate unique +alias recipient");
        final InboxDto inbox = BaseTest.getSuiteInbox() != null
                ? BaseTest.getSuiteInbox()
                : BaseTest.requireInboxOrSkip();

        final String testEmail  = MailSlurpUtils.uniqueAliasEmail(inbox, "cancel");
        final String aliasToken = MailSlurpUtils.extractAliasToken(testEmail);

        System.out.println("📮 Using shared inbox: " + inbox.getId() + " <" + inbox.getEmailAddress() + ">");
        System.out.println("📧 Test recipient (alias): " + testEmail);

        try { MailSlurpUtils.clearInboxEmails(inbox.getId()); } catch (Throwable ignored) {}

        // -------------------- APP FLOW: LOGIN + PURCHASE --------------------
        step("Login as admin");
        LoginPage loginPage = new LoginPage(driver());
        loginPage.navigateTo();
        loginPage.waitUntilLoaded();
        DashboardPage dashboard = loginPage.safeLoginAsAdmin(ADMIN_USER, ADMIN_PASS, Duration.ofSeconds(30));
        Assert.assertTrue(dashboard.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Shop and start purchase flow for True Tilt");
        ShopPage shopPage = dashboard.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load");
        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
        sel.selectClientOrIndividual();
        sel.clickNext();

        step("Manual entry for 1 individual (use the aliased MailSlurp address)");
        AssessmentEntryPage entryPage = new AssessmentEntryPage(driver())
                .waitUntilLoaded()
                .selectManualEntry()
                .enterNumberOfIndividuals("1");
        entryPage.fillUserDetailsAtIndex(1, "Emi", "CancelFlow", testEmail);

        step("Review order (Preview)");
        OrderPreviewPage preview = entryPage.clickProceedToPayment().waitUntilLoaded();

        step("Stripe: fetch Checkout Session + metadata.body");
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
        driver().navigate().to(joinUrl(Config.getBaseUrl(), "/dashboard/orders/confirmation"));

        // -------------------- INDIVIDUALS: VERIFY PENDING INVITE EXISTS --------------------
        step("Open Individuals page and assert the new email appears as Pending");
        IndividualsPage individuals = new IndividualsPage(driver())
                .open(Config.getBaseUrl())
                .waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        individuals.waitUntilUserInviteAppears(testEmail, Duration.ofSeconds(30));
        String statusBefore = individuals.getReportStatusByEmail(testEmail);
        System.out.println("ℹ Status before cancel = " + statusBefore);
        Assert.assertTrue(
                statusBefore != null && statusBefore.toLowerCase(Locale.ROOT).contains("pending"),
                "❌ Expected Pending status before cancel, got: " + statusBefore
        );

        // -------------------- EMAIL: CAPTURE CTA LINK BEFORE CANCELLATION --------------------
        step("Wait for invitation/reminder email addressed to our alias");
        System.out.println("[Email] Waiting up to " + EMAIL_TIMEOUT.toSeconds() + "s for message to " + testEmail + "…");

        Email email = MailSlurpUtils.waitForEmailMatching(
                inbox.getId(),
                EMAIL_TIMEOUT.toMillis(),
                1500L,
                true,
                MailSlurpUtils.addressedToAliasToken(aliasToken)
                        .and(MailSlurpUtils.subjectContains(SUBJECT_NEEDLE))
                        // many reminder templates include 'Reminder'; not strictly required
                        .and(MailSlurpUtils.subjectOrBodyContainsAny(REMINDER_NEEDLE, "Complete", "Start"))
        );
        Assert.assertNotNull(email,
                "❌ No assessment email arrived addressed to alias " + aliasToken + " within " + EMAIL_TIMEOUT);

        final String subject = Objects.toString(email.getSubject(), "");
        final String from    = Objects.toString(email.getFrom(), "");
        final String body    = MailSlurpUtils.safeEmailBody(email);

        System.out.printf("📨 Email — From: %s | Subject: %s%n", from, subject);
        Assert.assertTrue(subject.toLowerCase(Locale.ROOT).contains(SUBJECT_NEEDLE),
                "❌ Subject does not mention '" + SUBJECT_NEEDLE + "'. Got: " + subject);

        Assert.assertTrue(from.toLowerCase(Locale.ROOT).contains("tilt365")
                        || from.toLowerCase(Locale.ROOT).contains("sendgrid"),
                "❌ Unexpected sender: " + from);

        // Prefer "Complete Assessment" CTA, else "Start Assessment", else any link
        String ctaHref = MailSlurpUtils.extractLinkByAnchorText(email, CTA_TEXT_PRIMARY);
        if (ctaHref == null) {
            ctaHref = MailSlurpUtils.extractLinkByAnchorText(email, CTA_TEXT_FALLBACK);
        }
        if (ctaHref == null) {
            ctaHref = MailSlurpUtils.extractFirstLink(email);
        }
        Assert.assertNotNull(ctaHref, "❌ Could not find CTA link in assessment email.");
        System.out.println("🔗 Email CTA link (pre-cancel): " + ctaHref);

        // -------------------- CANCEL INVITATION IN INDIVIDUALS --------------------
        step("Open Remove/Cancel invitation modal from row actions for " + testEmail);
        individuals.openRemoveUserModalFor(testEmail);

        String modalTitle = individuals.getRemoveUserModalTitle();
        System.out.println("ℹ Remove/Cancel modal title = " + modalTitle);
        Assert.assertTrue(
                modalTitle.toLowerCase(Locale.ROOT).contains("remove") ||
                        modalTitle.toLowerCase(Locale.ROOT).contains("cancel"),
                "❌ Unexpected modal title for cancel invitation: '" + modalTitle + "'"
        );
        Assert.assertTrue(
                individuals.removeUserModalHasButtons("Cancel", "Remove"),
                "❌ Remove/Cancel modal does not show expected buttons 'Cancel' and 'Remove'"
        );

        step("Confirm removal/cancellation in the modal");
        individuals.clickRemoveConfirm();

        step("Wait until the row disappears from the current page");
        boolean disappearedOnCurrentPage =
                individuals.waitRowToDisappearOnCurrentPage(testEmail, Duration.ofSeconds(10));
        System.out.println("ℹ disappearedOnCurrentPage = " + disappearedOnCurrentPage);
        Assert.assertTrue(disappearedOnCurrentPage,
                "❌ Cancel invitation failed: row for " + testEmail + " is still visible on Individuals.");

        String toast = individuals.waitForSuccessToast();
        if (toast != null) {
            System.out.println("✅ Success toast after cancel/remove: " + toast);
        } else {
            System.out.println("⚠ No success toast detected after cancel/remove (not treated as failure).");
        }

        // -------------------- FOLLOW OLD EMAIL LINK: SHOULD *NOT* START ASSESSMENT --------------------
        step("Navigate to the ORIGINAL CTA link after cancellation");
        driver().navigate().to(ctaHref);

        WaitUtils wu = new WaitUtils(driver(), Duration.ofSeconds(20));
        wu.waitForDocumentReady();
        wu.waitForLoadersToDisappear();

        // Use assessment page objects as oracle for "can start?"
        TtpIntroPage ttpIntro   = new TtpIntroPage(driver());
        TtpSurveyPage ttpSurvey = new TtpSurveyPage(driver());

        boolean canStart =
                ttpIntro.isLoaded()
                        || ttpSurvey.isLoaded();

        System.out.println("ℹ canStartAssessmentAfterCancel = " + canStart);

        Assert.assertFalse(
                canStart,
                "❌ Invite email link still allows starting/completing the assessment after the invitation was cancelled."
        );
    }
















    /* ===================== local helpers (test-only) ===================== */

    /** Builds a per-row signature (Name || Email) to compare current order deterministically. */
    private static List<String> makeOrderSignature(IndividualsPage p) {
        List<String> names  = p.getNamesOnCurrentPage();
        List<String> emails = p.getEmailsOnCurrentPage();
        int n = Math.max(names.size(), emails.size());
        List<String> sig = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String name  = i < names.size()  ? names.get(i)  : "";
            String email = i < emails.size() ? emails.get(i) : "";
            sig.add(name + " || " + email);
        }
        return sig;
    }

    private static boolean individialsHasAtLeast(IndividualsPage p, int n) {
        return p.firstRows(n).size() >= n;
    }

    private static void assumeNonEmpty(List<?> list, String label) {
        if (list == null || list.isEmpty()) throw new SkipException("No values found for " + label);
    }

    private static boolean hasAtLeastTwoDistinct(List<String> vals) {
        return vals.stream().map(s -> s == null ? "" : s.trim().toLowerCase(Locale.ROOT))
                .distinct().limit(2).count() >= 2;
    }

    private static boolean isSortedNamesAsc(List<String> names) {
        java.text.Collator col = java.text.Collator.getInstance(Locale.ROOT);
        col.setStrength(java.text.Collator.PRIMARY); // ignore case/accents
        for (int i = 1; i < names.size(); i++) {
            String a = safe(names.get(i - 1)), b = safe(names.get(i));
            if (col.compare(a, b) > 0) return false;
        }
        return true;
    }
    private static boolean isSortedNamesDesc(List<String> names) {
        java.text.Collator col = java.text.Collator.getInstance(Locale.ROOT);
        col.setStrength(java.text.Collator.PRIMARY);
        for (int i = 1; i < names.size(); i++) {
            String a = safe(names.get(i - 1)), b = safe(names.get(i));
            if (col.compare(a, b) < 0) return false;
        }
        return true;
    }
    private static String safe(String s) { return s == null ? "" : s.trim(); }

    private static boolean isSortedLongDesc(List<Long> vals) {
        for (int i = 1; i < vals.size(); i++) if (vals.get(i - 1) < vals.get(i)) return false;
        return true;
    }
    private static boolean isSortedLongAsc(List<Long> vals) {
        for (int i = 1; i < vals.size(); i++) if (vals.get(i - 1) > vals.get(i)) return false;
        return true;
    }

    /** Parses a list of date strings to epoch millis (best effort). Unparseable entries are skipped. */
    private static List<Long> parseDateEpochs(List<String> texts) {
        java.util.ArrayList<Long> out = new java.util.ArrayList<>();
        for (String t : texts) {
            Long ms = tryParseEpochMillis(t);
            if (ms != null) out.add(ms);
        }
        return out;
    }

    private static Long tryParseEpochMillis(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        // ISO datetime with optional seconds
        java.time.format.DateTimeFormatter[] dts = new java.time.format.DateTimeFormatter[] {
                java.time.format.DateTimeFormatter.ISO_DATE_TIME,
                java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
        };
        for (var f : dts) {
            try { return java.time.ZonedDateTime.parse(s, f).toInstant().toEpochMilli(); } catch (Throwable ignored) {}
            try { return java.time.LocalDateTime.parse(s, f).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(); } catch (Throwable ignored) {}
        }

        // ISO date only (yyyy-MM-dd)
        try {
            return java.time.LocalDate.parse(s, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                    .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Throwable ignored) {}

        // 'MMM d, yyyy' or 'MMMM d, yyyy' (English)
        java.time.format.DateTimeFormatter[] mmm = new java.time.format.DateTimeFormatter[] {
                java.time.format.DateTimeFormatter.ofPattern("MMM d, uuuu", java.util.Locale.ENGLISH),
                java.time.format.DateTimeFormatter.ofPattern("MMMM d, uuuu", java.util.Locale.ENGLISH)
        };
        for (var f : mmm) {
            try {
                return java.time.LocalDate.parse(s, f)
                        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (Throwable ignored) {}
        }

        // Slashed dates: MM/dd/yyyy vs dd/MM/yyyy -> disambiguate by first token
        try {
            String[] parts = s.split("[/]");
            if (parts.length == 3) {
                int a = Integer.parseInt(parts[0]);
                int b = Integer.parseInt(parts[1]);
                String pat = (a > 12) ? "dd/MM/uuuu" : (b > 12 ? "MM/dd/uuuu" : "MM/dd/uuuu"); // default US
                return java.time.LocalDate.parse(s, java.time.format.DateTimeFormatter.ofPattern(pat))
                        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
        } catch (Throwable ignored) {}

        // Unparseable
        return null;
    }


    /** Creates a new pending individual via Shop → manual entry → Stripe, then returns its email. */
    private String createPendingIndividualViaShop(DashboardPage dashboard) throws Exception {
        String uniq      = String.valueOf(System.currentTimeMillis());
        String firstName = "SM05";
        String lastName  = "Auto-" + uniq;
        String email     = "sm05.auto+" + uniq + "@example.com"; // or your test domain

        step("[SM05 helper] Go to Shop and start True Tilt purchase flow");
        ShopPage shopPage = dashboard.goToShop();
        Assert.assertTrue(shopPage.isLoaded(), "❌ Shop page did not load in SM05 helper");

        PurchaseRecipientSelectionPage sel = shopPage.clickBuyNowForTrueTilt();
        sel.selectClientOrIndividual();
        sel.clickNext();

        step("[SM05 helper] Manual entry for 1 individual");
        AssessmentEntryPage entryPage = new AssessmentEntryPage(driver())
                .waitUntilLoaded()
                .selectManualEntry()
                .enterNumberOfIndividuals("1");
        entryPage.fillUserDetailsAtIndex(1, firstName, lastName, email);

        step("[SM05 helper] Proceed to payment preview");
        OrderPreviewPage preview = entryPage.clickProceedToPayment().waitUntilLoaded();

        step("[SM05 helper] Complete Stripe flow via metadata.body");
        String stripeUrl = preview.proceedToStripeAndGetCheckoutUrl();
        String sessionId = extractSessionIdFromUrl(stripeUrl);
        Assert.assertNotNull(sessionId, "❌ Could not parse Stripe session id from URL (SM05 helper)");

        String bodyJson = StripeCheckoutHelper.fetchCheckoutBodyFromStripe(sessionId);
        Assert.assertNotNull(bodyJson, "❌ metadata.body not found in Checkout Session (SM05 helper)");

        StripeCheckoutHelper.triggerCheckoutCompletedWithBody(bodyJson);

        driver().navigate().to(joinUrl(Config.getBaseUrl(), "/dashboard/orders/confirmation"));

        step("[SM05 helper] Sanity: assert new user appears in Individuals");
        new IndividualsPage(driver())
                .open(Config.getBaseUrl())
                .assertAppearsWithEvidence(Config.getBaseUrl(), email);

        return email;
    }









}
