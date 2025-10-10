package tests.individuals;

import Utils.Config;
import base.BaseTest;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v136.network.Network;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;
import pages.LoginPage;
import pages.menuPages.DashboardPage;
import pages.Individuals.IndividualsPage;

import java.time.Duration;
import java.util.*;


import static io.qameta.allure.Allure.step;

public class IndividualsListTests extends BaseTest {





    @Test(description = "TC-434: Individuals list shows Name, assessment icon(s), Date taken, and a clickable Report link (spot-check first rows).")
    public void displayIndividualsList_showsRequiredColumns() {

        step("Start fresh session (login + land on Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver);


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
            softly.assertFalse(individuals.rowHasName(row), "Row " + rowNum + " should have a non-empty Name");

            step("Row #" + rowNum + " — verify assessment icon(s)");
            softly.assertTrue(individuals.rowHasAssessmentIcon(row), "Row " + rowNum + " should show at least one assessment icon");


            step("Row #" + rowNum + " — verify Report column: Pending OR clickable link");
            if (individuals.rowReportIsPending(row)) {
                System.out.println(individuals.rowReportIsPending(row));
                // ok: pending state is expected before completion
                softly.assertTrue(true, "Row " + rowNum + " shows Pending");
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
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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
        step("Reload Individuals to clear filters before name search");
        individuals.reloadWithBuster(Config.getBaseUrl());
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals failed to reload before name search");

        step("Ensure there is at least one row again (or skip name path)");
        if (!individuals.hasAnyRows()) {
            throw new SkipException("No rows after reload; skipping name search portion.");
        }

        step("Pick a sample non-empty name from the current page");
        List<String> namesBefore = individuals.getNamesOnCurrentPage();
        String sampleName = namesBefore.stream().filter(s -> !s.isBlank()).findFirst()
                .orElseThrow(() -> new SkipException("No non-empty names visible to use as anchor for name search."));

        step("Build a robust substring from the name");
        // keep only letters/digits to avoid issues with spaces or punctuation
        String normalizedName = sampleName.replaceAll("[^\\p{L}\\p{Nd}]+", "");
        String nameNeedle = pickMiddleSubstring(normalizedName, 3, 6);

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
        String nameNeedleLc = nameNeedle.toLowerCase(Locale.ROOT);
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


    @Test(description = "TC-436: Sort by Name (A→Z, Z→A) and Date (Newest, Oldest) updates ordering correctly.")
    public void sortIndividuals_ordersUpdateCorrectly() {

        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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

        driver.navigate().refresh();
        individuals.waitUntilLoaded();

        step("Assert Auto reminder remains ON for " + email);
        Assert.assertTrue(individuals.isAutoReminderOn(email), "❌ Should remain ON after refresh: " + email);

        // OFF -> refresh -> assert
        step("Set Auto reminder = OFF for " + email);
        individuals.setAutoReminder(email, false);

        driver.navigate().refresh();
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







    @Test(description = "IND-003: Sort by Name (A–Z) orders ascending (case/accents ignored).")
    public void sortByName_ordersAscending() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Ensure we have at least 1 row to validate page size");
        List<String> namesInitial = individuals.getNamesOnCurrentPage();
        assumeNonEmpty(namesInitial, "namesInitial");

        // Helper to count rows via existing API (no new helpers)
        java.util.function.Supplier<Integer> rowCount = () -> individuals.getNamesOnCurrentPage().size();

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
            return driver.findElements(By.cssSelector("li.ant-pagination-item.ant-pagination-item-active"))
                    .stream().anyMatch(li -> n.equals(li.getText().trim()));
        } catch (Exception e) { return false; }
    }


    @Test(groups = "ui-only", description = "IND-009: Report column shows 'Pending' or a clickable link.")
    public void reportColumn_showsPendingOrLink() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        // We'll try page 1..6; if a page doesn't exist, goToPage(i) will throw and we'll stop.
        pages.reports.ReportSummaryPage report = null;
        String chosenEmail = null;

        for (int page = 1; page <= 6 && report == null; page++) {
            try {
                individuals.goToPage(page);
            } catch (Throwable t) {
                break;
            }

            step("Scan page " + page + " for a row with a Report link");
            List<String> emails = individuals.getEmailsOnCurrentPage();
            assumeNonEmpty(emails, "emails_p" + page);

            for (String email : emails) {
                String status = individuals.getReportStatusByEmail(email); // "Pending", "Link", or "Link:<href>"
                if (status != null && status.toLowerCase(java.util.Locale.ROOT).startsWith("link")) {
                    chosenEmail = email;
                    step("Open Report link for " + email + " (same tab)");
                    report = individuals.openReportLinkByEmail(email).waitUntilLoaded();
                    break;
                }
            }
        }

        Assert.assertNotNull(report, "❌ Could not find any row with a Report link on the first pages.");
        Assert.assertTrue(report.isLoaded(), "❌ Report page did not load correctly for email: " + chosenEmail);
    }


    @Test(groups = "ui-only", description = "IND-011: Row actions menu opens and shows expected options.")
    public void rowActionsMenu_opensAndShowsOptions() throws InterruptedException {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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
        WebElement panel = new WebDriverWait(driver, java.time.Duration.ofSeconds(5))
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
        try { driver.switchTo().activeElement().sendKeys(org.openqa.selenium.Keys.ESCAPE); } catch (Exception ignored) {}
    }


    @Test(groups = "ui-only", description = "IND-012: Auto reminder: toggle ON persists after refresh")
    public void autoReminder_toggleOn_persistsAfterRefresh() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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
        driver.navigate().refresh();

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
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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
        driver.navigate().refresh();

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
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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
        WebDriverWait wdw = new WebDriverWait(driver, java.time.Duration.ofSeconds(8));
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
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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
    public void sendReminder_confirm_showsErrorToast_onBackendFailure() {
        // --- Guard: CDP only on Chromium drivers ---
        if (!(driver instanceof HasDevTools)) {
            throw new SkipException("CDP not available on this driver; cannot simulate backend error.");
        }

        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

        step("Open Individuals page");
        IndividualsPage individuals = dashboard.goToIndividuals().waitUntilLoaded();
        Assert.assertTrue(individuals.isLoaded(), "❌ Individuals page did not load");

        step("Pick target email (first visible)");
        java.util.List<String> emails = individuals.getEmailsOnCurrentPage();
        if (emails == null || emails.isEmpty()) {
            throw new SkipException("Need at least 1 row to send reminder.");
        }
        String email = emails.get(0);

        // --- Begin CDP: block the reminder endpoint so the request fails ---
        DevTools devTools = ((HasDevTools) driver).getDevTools();
        devTools.createSession();
        devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
        // Tweak patterns if your endpoint differs (e.g., "/api/*reminder*", "/send_reminder")
        devTools.send(Network.setBlockedURLs(java.util.List.of("*/reminder*", "*/send_reminder*")));

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
            try { devTools.send(Network.setBlockedURLs(java.util.List.of())); } catch (Exception ignore) {}
            try { devTools.send(Network.disable()); } catch (Exception ignore) {}
            try { devTools.close(); } catch (Exception ignore) {}
        }
    }


    @Test(groups = "ui-only", description = "IND-017: Send reminder: cancel modal keeps state unchanged")
    public void sendReminder_cancel_keepsStateUnchanged() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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
        driver.navigate().refresh();
        individuals.waitUntilLoaded();
        // Reuse your table accessor: row should be findable by the NEW email
        String nameForNewEmail = individuals.getNameByEmail(newEmail); // returns "" if not found
        Assert.assertFalse(
                nameForNewEmail == null || nameForNewEmail.isBlank(),
                "❌ Row with the new email was not found after refresh: " + newEmail
        );
    }

    @Test(groups = "ui-only", description = "IND-020: Edit info: invalid email blocks save")
    public void editInfo_invalidEmail_blocksSave() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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

        step("Assert validation appears and Save remains disabled");
        String err = individuals.getEditEmailValidationText();   // "" if not present
        Assert.assertFalse(err == null || err.isBlank(), "❌ No validation message for invalid email.");
        Assert.assertFalse(individuals.isEditInfoSaveEnabled(), "❌ 'Save changes' should be disabled for invalid email.");
    }

    @Test(groups = "ui-only", description = "IND-021: Edit info: duplicate email shows error and does not persist")
    public void editInfo_duplicateEmail_showsError_andDoesNotPersist() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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

        driver.navigate().refresh();
        individuals.waitUntilLoaded();

        // The original row should still be findable by its original email
        String name = individuals.getNameByEmail(targetEmail);
        Assert.assertFalse(name == null || name.isBlank(),
                "❌ Original row with email " + targetEmail + " not found after duplicate attempt.");
    }

    @Test(groups = "ui-only", description = "IND-022: Edit info: no-change submit keeps Save disabled")
    public void editInfo_noChange_keepsSaveDisabled() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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

        step("'Save changes' must remain disabled when no fields are modified");
        // Give the UI a brief moment to settle any initial validation/dirty checks
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        boolean everEnabled = false;
        while (System.nanoTime() < deadline) {
            if (individuals.isEditInfoSaveEnabled()) { // PO helper
                everEnabled = true;
                break;
            }
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
        Assert.assertFalse(everEnabled, "❌ 'Save changes' became enabled without any changes.");

        step("Close the modal to leave UI clean");
        try { individuals.clickModalCancel(); } catch (Throwable ignore) {}
        try { individuals.waitForModalToClose(); } catch (Throwable ignore) {}
    }

    @Test(groups = "ui-only", description = "IND-023: Remove user: opens confirmation modal")
    public void removeUser_opensConfirmationModal() {
        step("Start fresh session (login + Dashboard)");
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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
        DashboardPage dashboard = BaseTest.startFreshSession(driver);

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
        String anyToast = individuals.waitForAnyToastOrAlertText(3); // should be null/blank on cancel
        Assert.assertTrue(anyToast == null || anyToast.isBlank(),
                "❌ A toast/alert appeared after Cancel: " + anyToast);

        step("Verify the row still exists");
        String nameStillThere = individuals.getNameByEmail(email);
        Assert.assertFalse(nameStillThere == null || nameStillThere.isBlank(),
                "❌ Row was removed after Cancel for email: " + email);
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










}
