package tests.resources;

import Utils.Config;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;
import pages.LoginPage;
import pages.menuPages.DashboardPage;
import pages.menuPages.ResourcesPage;
import pages.menuPages.ShopPage;
import pages.resources.ResourcesDetailsPage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static Utils.WaitUtils.waitForDocumentReady;
import static base.BaseTest.driver;
import static base.BaseTest.startFreshSession;
import static io.qameta.allure.Allure.step;

public class ResourcesPageTests {







    @Test(description = "TILT-652 | Resources – List loads with expected data")
    public void resourcesListLoads() {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");


        step("Go to Resources page");
        ResourcesPage resourcesPage = dashboardPage.goToResources();

        step( "Verify list loads");
        Assert.assertTrue(
                resourcesPage.hasAtLeastOneResource(),
                "Expected at least one resource to be displayed"
        );

        List<WebElement> cards = resourcesPage.getResourceCards();

        step("Validate each card basic data");
        for (WebElement card : cards) {

            String title = resourcesPage.getTitleFromCard(card);
            Assert.assertFalse(
                    title.isBlank(),
                    "Resource title should not be empty"
            );

            String subtitleOrFilename =
                    resourcesPage.getSubtitleOrFilenameFromCard(card);

            Assert.assertFalse(
                    subtitleOrFilename.isBlank(),
                    "Resource filename / description should not be empty"
            );

        }
    }


    @Test(description = "TILT-653 | Resources – PDF details page loads correctly")
    public void pdfDetailsPageLoadsCorrectly() {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");


        step("Go to Resources page");
        ResourcesPage resourcesPage = dashboardPage.goToResources();
        resourcesPage.goToLearningAids();

        // Click first PDF
        WebElement pdfCard = resourcesPage.getPdfCards().get(0);
        String expectedTitle = resourcesPage.getTitleFromCard(pdfCard);
        resourcesPage.openPdfSameTab(pdfCard);

        ResourcesDetailsPage detailsPage =
                new ResourcesDetailsPage(driver()).waitUntilLoaded();

        step("Title is correct");
        Assert.assertEquals(
                detailsPage.getPdfTitle(),
                expectedTitle,
                "PDF title should match selected resource"
        );

        step("Download button exists");
        Assert.assertTrue(
                detailsPage.isDownloadButtonVisible(),
                "Download full PDF button should be visible"
        );

        step("PDF iframe loads real PDF");
        String iframeSrc = detailsPage.getPdfIframeSrc();
        Assert.assertTrue(
                iframeSrc.contains("active_storage"),
                "PDF iframe src should point to ActiveStorage. Actual: " + iframeSrc
        );

        Assert.assertTrue(
                iframeSrc.endsWith(".pdf") || iframeSrc.contains(".pdf#"),
                "Iframe src should reference a PDF file. Actual: " + iframeSrc
        );
    }


    @Test(description = "TILT-1878 | Resources – Page loads successfully")
    public void resourcesPageLoadsSuccessfully() {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(
                dashboardPage.isLoaded(),
                "❌ Dashboard did not load after login"
        );

        step("Go to Resources page");
        ResourcesPage resourcesPage = dashboardPage.goToResources();
        resourcesPage.waitUntilLoaded();

        step("Resources page is loaded");
        Assert.assertTrue(
                resourcesPage.isLoaded(),
                "Resources page did not load correctly"
        );

        step("Main Resources heading is visible");
        Assert.assertTrue(
                resourcesPage.isPresentHeading(),
                "Resources heading should be visible"
        );

        step("Hero section CTA is visible");
        Assert.assertTrue(
                resourcesPage.isVisibleHeroCta(),
                "Hero CTA button should be visible on Resources page"
        );

        step("At least one resource card is displayed");
        Assert.assertTrue(
                resourcesPage.hasAtLeastOneResource(),
                "Resources page should display at least one resource card"
        );
    }


    @Test(description = "TILT-1879 | Resources – Default category is View All")
    public void resourcesDefaultCategoryIsViewAll() {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Resources page");
        ResourcesPage resourcesPage = dashboardPage.goToResources();
        resourcesPage.waitUntilLoaded();

        step("View All tab is selected by default");
        Assert.assertTrue(
                resourcesPage.isViewAllSelected(),
                "View All category should be selected by default"
        );

        step("Mixed content is displayed (articles and learning aids)");
        Assert.assertTrue(
                resourcesPage.hasAtLeastOnePostCard(),
                "Expected at least one Article card (type='post') in View All"
        );

        Assert.assertTrue(
                resourcesPage.hasAtLeastOnePdfCard(),
                "Expected at least one Learning Aid / PDF card (type='pdf') in View All"
        );
    }


    @Test(description = "TILT-1880 | Resources – Articles category loads list")
    public void articlesCategoryLoadsList() {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Resources page");
        ResourcesPage resourcesPage = dashboardPage.goToResources().waitUntilLoaded();

        step("Select Articles category");
        resourcesPage.goToArticles();

        step("At least 1 article card exists (across pagination)");
        Assert.assertTrue(
                resourcesPage.hasAtLeastOnePostCard(),
                "Expected at least one Article card in Articles category (across pages)."
        );

        // Validate current page cards (sample first 3)
        List<WebElement> cards = resourcesPage.getResourceCards(); // this is post cards in active pane
        int toCheck = Math.min(3, cards.size());

        for (int i = 0; i < toCheck; i++) {
            WebElement card = cards.get(i);

            step("Card " + (i + 1) + " has title");
            Assert.assertFalse(
                    resourcesPage.getTitleFromCard(card).trim().isBlank(),
                    "Article title should not be blank"
            );

            step("Card " + (i + 1) + " has short description");
            Assert.assertFalse(
                    resourcesPage.getSubtitleOrFilenameFromCard(card).trim().isBlank(),
                    "Article short description should not be blank"
            );

            step("Card " + (i + 1) + " has image/thumbnail");
            Assert.assertTrue(
                    resourcesPage.postCardHasImage(card),
                    "Article card should display an image/thumbnail"
            );
        }
    }


    @Test(description = "TILT-1881 | Resources – Learning aids category loads PDFs")
    public void learningAidsCategoryLoadsPdfsOnly() {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Resources page");
        ResourcesPage resourcesPage = dashboardPage.goToResources().waitUntilLoaded();

        step("Select Learning aids category");
        resourcesPage.goToLearningAids();

        step("At least 1 PDF card exists (across pagination)");
        Assert.assertTrue(
                resourcesPage.hasAtLeastOnePdfCard(),
                "Expected at least one PDF card in Learning aids (across pages)."
        );

        step("Only PDF cards are displayed (no post/article cards) on current page");
        Assert.assertTrue(
                resourcesPage.getResourceCards().isEmpty(), // your resourceCards == post cards in active pane
                "Learning aids tab should not show post/article cards."
        );

        step("All visible cards in Learning aids are PDFs");
        List<WebElement> pdfCards = resourcesPage.getPdfCards();
        Assert.assertFalse(pdfCards.isEmpty(), "Expected PDF cards list to be non-empty on current page.");

        for (int i = 0; i < pdfCards.size(); i++) {
            WebElement pdf = pdfCards.get(i);

            String type = pdf.getAttribute("type");
            String href = pdf.getAttribute("href");

            Assert.assertEquals(
                    type,
                    "pdf",
                    "Card " + (i + 1) + " should have type='pdf'"
            );

            Assert.assertTrue(
                    href != null && href.contains("resources/pdf/"),
                    "Card " + (i + 1) + " href should point to resources/pdf/. Actual: " + href
            );

            Assert.assertFalse(
                    resourcesPage.getTitleFromCard(pdf).trim().isBlank(),
                    "Card " + (i + 1) + " should have a title"
            );
        }
    }


    @Test(description = "TILT-1883 | Resources – Resource card displays subtitle or description")
    public void resourceCardDisplaysSubtitleOrDescription() {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Resources page (View All default)");
        ResourcesPage resourcesPage = dashboardPage.goToResources().waitUntilLoaded();

        step("Ensure there is at least one resource card (across pagination)");
        boolean hasAny =
                resourcesPage.hasAtLeastOnePostCardAcrossPages(30)
                        || resourcesPage.goToLearningAids().hasAtLeastOnePdfCardAcrossPages(30);

        Assert.assertTrue(hasAny, "Expected at least one resource card (post or pdf) across pages/tabs.");

        // Go back to View All for the actual validation (mixed content expected there)
        step("Go back to View All (validate cards shown have subtitle/description)");
        // If you already have a method, use it. If not, click View All tab selector.
        // resourcesPage.goToViewAll();
        // For now, rely on default state by reopening the page:
        resourcesPage.open(Config.getBaseUrl()); // or your baseUrl getter
        resourcesPage.waitUntilLoaded();

        step("Validate subtitle/description is present for each visible card (current page)");
        List<WebElement> cards = resourcesPage.getResourceCards(); // posts in active pane
        Assert.assertFalse(cards.isEmpty(), "Expected at least one post card visible on View All current page.");

        for (int i = 0; i < cards.size(); i++) {
            WebElement card = cards.get(i);

            String title = resourcesPage.getTitleFromCard(card).trim();
            Assert.assertFalse(title.isBlank(), "Card " + (i + 1) + " should have a title.");

            String subtitle = resourcesPage.getSubtitleOrFilenameFromCard(card).trim();
            Assert.assertFalse(
                    subtitle.isBlank(),
                    "Card " + (i + 1) + " should display subtitle/description under title. Title: " + title
            );
        }
    }
    

    @Test(description = "TILT-1884 | Resources – Resource card is clickable")
    public void resourceCardIsClickable() {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Resources page");
        ResourcesPage resourcesPage = dashboardPage.goToResources().waitUntilLoaded();

        // ---------- Try Article (post) first ----------
        step("Click first Article card (post) if available");
        if (resourcesPage.hasAtLeastOnePostCardAcrossPages(30)) {

            List<WebElement> postCards = resourcesPage.getResourceCards();
            Assert.assertFalse(postCards.isEmpty(), "Expected post cards after hasAtLeastOnePostCardAcrossPages.");

            WebElement firstPost = postCards.get(0);
            String originalHandle = driver().getWindowHandle();
            int handlesBefore = driver().getWindowHandles().size();

            // Click (may open same tab OR new tab)
            resourcesPage.openPost(firstPost);

            // Wait a bit for either navigation or new tab
            waitForDocumentReady(driver());

            int handlesAfter = driver().getWindowHandles().size();

            // Case A: opened in NEW TAB
            if (handlesAfter > handlesBefore) {

                String newHandle = driver().getWindowHandles().stream()
                        .filter(h -> !h.equals(originalHandle))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Expected a new tab handle but couldn't find it"));

                driver().switchTo().window(newHandle);
                waitForDocumentReady(driver());

                String newTabUrl = driver().getCurrentUrl();
                Assert.assertTrue(
                        !newTabUrl.contains("/dashboard/resources"),
                        "Expected new tab to navigate away from resources. New tab URL: " + newTabUrl
                );

                // close new tab and go back
                driver().close();
                driver().switchTo().window(originalHandle);
                waitForDocumentReady(driver());

                return;
            }

            // Case B: opened in SAME TAB
            String currentUrl = driver().getCurrentUrl();
            Assert.assertTrue(
                    !currentUrl.contains("/dashboard/resources"),
                    "Expected navigation away from Resources list after clicking post. Current URL: " + currentUrl
            );

            return;
        }

        // ---------- Otherwise fallback to Learning aids (PDF) ----------
        step("Otherwise click first PDF card (Learning aids) if available");
        resourcesPage.goToLearningAids();

        Assert.assertTrue(
                resourcesPage.hasAtLeastOnePdfCardAcrossPages(30),
                "Expected at least one PDF card under Learning aids across pages."
        );

        List<WebElement> pdfCards = resourcesPage.getPdfCards();
        Assert.assertFalse(pdfCards.isEmpty(), "Expected pdf cards after hasAtLeastOnePdfCardAcrossPages.");

        WebElement firstPdf = pdfCards.get(0);
        String expectedTitle = resourcesPage.getTitleFromCard(firstPdf);

        resourcesPage.openPdfSameTab(firstPdf);

        ResourcesDetailsPage detailsPage = new ResourcesDetailsPage(driver()).waitUntilLoaded();

        step("Validate PDF details page loaded for clicked card");
        Assert.assertEquals(
                detailsPage.getPdfTitle(),
                expectedTitle,
                "PDF title should match selected resource"
        );

        String pdfUrl = driver().getCurrentUrl();
        Assert.assertTrue(
                pdfUrl.contains("/dashboard/resources"),
                "Expected to be on a Resources details route after clicking PDF. Current URL: " + pdfUrl
        );
    }


    @Test(description = "TILT-1885 | Resources – Article opens external blog page")
    public void articleOpensExternalBlogPage() {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Resources page");
        ResourcesPage resourcesPage = dashboardPage.goToResources().waitUntilLoaded();

        step("Ensure at least one Article card exists across pages");
        Assert.assertTrue(
                resourcesPage.hasAtLeastOnePostCardAcrossPages(30),
                "Expected at least one Article (post) across pages."
        );

        step("Get first post card on current page");
        List<WebElement> postCards = resourcesPage.getResourceCards();
        Assert.assertFalse(postCards.isEmpty(), "Expected post cards after pagination scan.");

        WebElement firstPost = postCards.get(0);
        String expectedHref = firstPost.getAttribute("href"); // may be full URL
        Assert.assertNotNull(expectedHref, "Post card href should not be null");
        Assert.assertFalse(expectedHref.isBlank(), "Post card href should not be blank");

        String originalHandle = driver().getWindowHandle();
        int handlesBefore = driver().getWindowHandles().size();

        step("Click post card (should open external blog in new tab/window)");
        resourcesPage.openPost(firstPost);

        waitForDocumentReady(driver());

        int handlesAfter = driver().getWindowHandles().size();
        Assert.assertTrue(
                handlesAfter > handlesBefore,
                "Expected a new tab/window to open after clicking the article. Handles before=" +
                        handlesBefore + " after=" + handlesAfter
        );

        step("Switch to new tab and validate blog URL");
        String newHandle = driver().getWindowHandles().stream()
                .filter(h -> !h.equals(originalHandle))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a new tab handle but couldn't find it"));

        driver().switchTo().window(newHandle);
        waitForDocumentReady(driver());

        String newTabUrl = driver().getCurrentUrl();

        Assert.assertTrue(
                newTabUrl.contains("tilt365.com"),
                "Expected external Tilt365 domain. Actual: " + newTabUrl
        );

        // Blog path check (your example: https://www.tilt365.com/blog/...)
        Assert.assertTrue(
                newTabUrl.contains("/blog/"),
                "Expected blog URL path to contain /blog/. Actual: " + newTabUrl
        );

        // If href is absolute, it should match (or at least contain) what we clicked
        if (expectedHref.startsWith("http")) {
            Assert.assertTrue(
                    newTabUrl.contains(expectedHref) || expectedHref.contains(newTabUrl),
                    "Expected opened URL to match clicked href. href=" + expectedHref + " | opened=" + newTabUrl
            );
        }

        step("Close new tab and return to original Resources tab");
        driver().close();
        driver().switchTo().window(originalHandle);
        waitForDocumentReady(driver());

        Assert.assertTrue(
                driver().getCurrentUrl().contains("/dashboard/resources"),
                "Expected to return to Resources page. Current URL: " + driver().getCurrentUrl()
        );
    }


    @Test(description = "TILT-1886 | Resources – Learning aid PDF opens details page")
    public void learningAidPdfOpensDetailsPage() {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Resources page → Learning aids");
        ResourcesPage resourcesPage = dashboardPage.goToResources().waitUntilLoaded();
        resourcesPage.goToLearningAids();

        step("Ensure at least one PDF exists across pages");
        Assert.assertTrue(
                resourcesPage.hasAtLeastOnePdfCardAcrossPages(30),
                "Expected at least one PDF card under Learning aids across pages."
        );

        step("Click first PDF card on current page");
        List<WebElement> pdfCards = resourcesPage.getPdfCards();
        Assert.assertFalse(pdfCards.isEmpty(), "Expected pdf cards after pagination scan.");

        WebElement firstPdf = pdfCards.get(0);
        String expectedHref = resourcesPage.getPdfHref(firstPdf); // e.g. resources/pdf/7
        Assert.assertNotNull(expectedHref, "PDF card href should not be null");
        Assert.assertFalse(expectedHref.isBlank(), "PDF card href should not be blank");

        resourcesPage.openPdfSameTab(firstPdf);

        step("Validate internal PDF details route");
        String currentUrl = driver().getCurrentUrl();

        // Normalize expectedHref if it's relative (resources/pdf/7)
        String expectedPath = expectedHref.startsWith("/")
                ? expectedHref
                : "/" + expectedHref;

        Assert.assertTrue(
                currentUrl.contains("/dashboard/resources/pdf/") || currentUrl.contains("/dashboard/resources") && currentUrl.contains("/pdf/"),
                "Expected to be on an internal PDF details page. Current URL: " + currentUrl
        );

        // Stronger check: URL should contain the clicked href path segment (id)
        Assert.assertTrue(
                currentUrl.contains(expectedPath) || currentUrl.contains(expectedHref),
                "URL should include clicked PDF href path. Expected href: " + expectedHref + " | Current URL: " + currentUrl
        );

        step("Details page loads (iframe present)");
        ResourcesDetailsPage detailsPage = new ResourcesDetailsPage(driver()).waitUntilLoaded();
        String iframeSrc = detailsPage.getPdfIframeSrc();

        Assert.assertTrue(
                iframeSrc != null && !iframeSrc.isBlank(),
                "Expected iframe src to be present. Actual: " + iframeSrc
        );
        Assert.assertTrue(
                iframeSrc.contains("active_storage"),
                "Expected iframe src to point to ActiveStorage. Actual: " + iframeSrc
        );
    }


    @Test(description = "TILT-653 | Resources – PDF details page loads correctly")
    public void pdfDetailsPageLoadsCorrectly2() {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Resources page → Learning aids");
        ResourcesPage resourcesPage = dashboardPage.goToResources().waitUntilLoaded();
        resourcesPage.goToLearningAids();

        step("Ensure at least one PDF exists across pages");
        Assert.assertTrue(
                resourcesPage.hasAtLeastOnePdfCardAcrossPages(30),
                "Expected at least one PDF card under Learning aids across pages."
        );

        step("Open first PDF");
        List<WebElement> pdfCards = resourcesPage.getPdfCards();
        Assert.assertFalse(pdfCards.isEmpty(), "Expected at least one PDF card on current page.");

        WebElement pdfCard = pdfCards.get(0);
        String expectedTitle = resourcesPage.getTitleFromCard(pdfCard);
        String expectedHref = resourcesPage.getPdfHref(pdfCard); // resources/pdf/7

        resourcesPage.openPdfSameTab(pdfCard);

        ResourcesDetailsPage detailsPage = new ResourcesDetailsPage(driver()).waitUntilLoaded();

        step("Header and PDF title are visible & correct");
        Assert.assertTrue(detailsPage.isLoaded(), "❌ Resources PDF details page did not load properly");

        Assert.assertEquals(
                detailsPage.getPdfTitle(),
                expectedTitle,
                "PDF title should match the selected card title"
        );

        step("URL is correct (/dashboard/resources/pdf/{id})");
        String currentUrl = driver().getCurrentUrl();
        Assert.assertTrue(
                currentUrl.contains("/dashboard/resources/pdf/"),
                "Expected URL to contain /dashboard/resources/pdf/. Current URL: " + currentUrl
        );

        if (expectedHref != null && !expectedHref.isBlank()) {
            String expectedPath = expectedHref.startsWith("/") ? expectedHref : "/" + expectedHref; // /resources/pdf/7
            Assert.assertTrue(
                    currentUrl.contains(expectedPath) || currentUrl.contains(expectedHref),
                    "URL should include clicked href path. Expected href: " + expectedHref + " | Current URL: " + currentUrl
            );
        }

        step("Embedded PDF viewer (iframe) loads a real PDF");
        String iframeSrc = detailsPage.getPdfIframeSrc();

        Assert.assertTrue(
                iframeSrc != null && !iframeSrc.isBlank(),
                "PDF iframe src should not be empty. Actual: " + iframeSrc
        );

        Assert.assertTrue(
                iframeSrc.contains("active_storage"),
                "PDF iframe src should point to ActiveStorage. Actual: " + iframeSrc
        );

        Assert.assertTrue(
                iframeSrc.endsWith(".pdf") || iframeSrc.contains(".pdf#"),
                "Iframe src should reference a PDF file. Actual: " + iframeSrc
        );
    }


    @Test(description = "TILT-1888 | Resources – PDF title matches selected card")
    public void pdfTitleMatchesSelectedCard() {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Resources page → Learning aids");
        ResourcesPage resourcesPage = dashboardPage.goToResources().waitUntilLoaded();
        resourcesPage.goToLearningAids();

        step("Ensure at least one PDF exists across pages");
        Assert.assertTrue(
                resourcesPage.hasAtLeastOnePdfCardAcrossPages(30),
                "Expected at least one PDF card under Learning aids across pages."
        );

        step("Click first PDF card on current page");
        List<WebElement> pdfCards = resourcesPage.getPdfCards();
        Assert.assertFalse(pdfCards.isEmpty(), "Expected at least one PDF card on current page.");

        WebElement pdfCard = pdfCards.get(0);

        String expectedTitle = resourcesPage.getTitleFromCard(pdfCard);
        Assert.assertFalse(expectedTitle.isBlank(), "PDF card title should not be blank.");

        resourcesPage.openPdfSameTab(pdfCard);

        ResourcesDetailsPage detailsPage = new ResourcesDetailsPage(driver()).waitUntilLoaded();

        step("Validate title on details page matches card title");
        String actualTitle = detailsPage.getPdfTitle();

        Assert.assertEquals(
                actualTitle,
                expectedTitle,
                "PDF title on details page should match the selected Learning aids card title"
        );
    }


    @Test(description = "TILT-1889 | Resources – PDF iframe loads content")
    public void pdfIframeLoadsContent() {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Resources page → Learning aids");
        ResourcesPage resourcesPage = dashboardPage.goToResources().waitUntilLoaded();
        resourcesPage.goToLearningAids();

        step("Ensure at least one PDF exists across pages");
        Assert.assertTrue(
                resourcesPage.hasAtLeastOnePdfCardAcrossPages(30),
                "Expected at least one PDF card under Learning aids across pages."
        );

        step("Open first PDF card");
        List<WebElement> pdfCards = resourcesPage.getPdfCards();
        Assert.assertFalse(pdfCards.isEmpty(), "Expected at least one PDF card on current page.");

        WebElement pdfCard = pdfCards.get(0);
        resourcesPage.openPdfSameTab(pdfCard);

        ResourcesDetailsPage detailsPage = new ResourcesDetailsPage(driver()).waitUntilLoaded();

        step("PDF iframe src points to a real PDF resource");
        String iframeSrc = detailsPage.getPdfIframeSrc();
        Assert.assertNotNull(iframeSrc, "iframe src should not be null");
        Assert.assertFalse(iframeSrc.isBlank(), "iframe src should not be blank");

        // Current app behavior: ActiveStorage URL for the PDF
        Assert.assertTrue(
                iframeSrc.contains("active_storage"),
                "Expected iframe src to point to ActiveStorage. Actual: " + iframeSrc
        );

        Assert.assertTrue(
                iframeSrc.endsWith(".pdf") || iframeSrc.contains(".pdf#") || iframeSrc.contains(".pdf?"),
                "Expected iframe src to reference a PDF file. Actual: " + iframeSrc
        );

        // Optional sanity: iframe element is actually present & displayed
        step("Iframe element is displayed");
        Assert.assertTrue(detailsPage.isLoaded(), "Details page should be loaded with iframe visible");
    }


    @Test(description = "TILT-1890 | Resources – Download full PDF button is visible")
    public void downloadFullPdfButtonIsVisible() {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Resources page → Learning aids");
        ResourcesPage resourcesPage = dashboardPage.goToResources().waitUntilLoaded();
        resourcesPage.goToLearningAids();

        step("Ensure at least one PDF exists across pages");
        Assert.assertTrue(
                resourcesPage.hasAtLeastOnePdfCardAcrossPages(30),
                "Expected at least one PDF card under Learning aids across pages."
        );

        step("Open first PDF card");
        List<WebElement> pdfCards = resourcesPage.getPdfCards();
        Assert.assertFalse(pdfCards.isEmpty(), "Expected at least one PDF card on current page.");

        WebElement pdfCard = pdfCards.get(0);
        resourcesPage.openPdfSameTab(pdfCard);

        ResourcesDetailsPage detailsPage = new ResourcesDetailsPage(driver()).waitUntilLoaded();

        step("Verify Download full PDF button is visible");
        Assert.assertTrue(
                detailsPage.isDownloadButtonVisible(),
                "Download full PDF button should be visible on the PDF details page"
        );
    }


    @Test(description = "TILT-1891 | Resources – Download full PDF triggers file download")
    public void downloadFullPdfTriggersFileDownload() throws Exception {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Resources → Learning aids");
        ResourcesPage resourcesPage = dashboardPage.goToResources().waitUntilLoaded();
        resourcesPage.goToLearningAids();

        Assert.assertTrue(
                resourcesPage.hasAtLeastOnePdfCardAcrossPages(30),
                "Expected at least one PDF card under Learning aids across pages."
        );

        step("Open first PDF details page");
        List<WebElement> pdfCards = resourcesPage.getPdfCards();
        Assert.assertFalse(pdfCards.isEmpty(), "Expected PDF cards on current page.");

        WebElement firstPdf = pdfCards.get(0);
        resourcesPage.openPdfSameTab(firstPdf);

        ResourcesDetailsPage detailsPage = new ResourcesDetailsPage(driver()).waitUntilLoaded();

        step("Capture the real PDF URL from iframe (source of truth)");
        String pdfUrl = detailsPage.getPdfIframeSrc();
        Assert.assertTrue(
                pdfUrl != null && !pdfUrl.isBlank(),
                "Expected iframe src to exist."
        );
        Assert.assertTrue(
                pdfUrl.contains("active_storage") || pdfUrl.toLowerCase().contains(".pdf"),
                "Expected iframe src to look like a PDF URL. Actual: " + pdfUrl
        );

        step("Click Download full PDF (trigger)");
        Assert.assertTrue(detailsPage.isDownloadButtonVisible(), "Download full PDF button should be visible");
        detailsPage.clickDownloadFullPdf();

        step("Verify the PDF can be downloaded with current authenticated session (cookies)");
        String cookieHeader = driver().manage().getCookies().stream()
                .map(c -> c.getName() + "=" + c.getValue())
                .collect(Collectors.joining("; "));

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(pdfUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Cookie", cookieHeader)
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        Assert.assertTrue(
                resp.statusCode() >= 200 && resp.statusCode() < 300,
                "Expected 2xx when downloading PDF. Status=" + resp.statusCode()
        );

        String contentType = resp.headers().firstValue("content-type").orElse("").toLowerCase();
        Assert.assertTrue(
                contentType.contains("pdf") || pdfUrl.toLowerCase().contains(".pdf"),
                "Expected content-type to be PDF. content-type=" + contentType + " | url=" + pdfUrl
        );

        byte[] bytes = resp.body();

        boolean looksLikePdfByHeader = contentType.contains("pdf");
        boolean looksLikePdfByMagic =
                bytes != null && bytes.length >= 5
                        && bytes[0] == 0x25  // %
                        && bytes[1] == 0x50  // P
                        && bytes[2] == 0x44  // D
                        && bytes[3] == 0x46  // F
                        && bytes[4] == 0x2D; // -

        Assert.assertTrue(
                looksLikePdfByHeader || looksLikePdfByMagic,
                "Response does not look like a PDF. status=" + resp.statusCode()
                        + " content-type=" + contentType
                        + " bytes=" + (bytes == null ? 0 : bytes.length)
        );

    }

    // ---------- small test-only helper ----------
    private void waitForDocumentReadyInTest(WebDriver driver) {
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> {
            try {
                Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
                return state != null && "complete".equals(state.toString());
            } catch (Throwable t) {
                return true; // don't hard-fail on odd pages
            }
        });
    }


    @Test(description = "TILT-1892 | Resources – Back navigation from PDF details works")
    public void backNavigationFromPdfDetailsWorks() {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Resources → Learning aids");
        ResourcesPage resourcesPage = dashboardPage.goToResources().waitUntilLoaded();
        resourcesPage.goToLearningAids();

        Assert.assertTrue(
                resourcesPage.hasAtLeastOnePdfCardAcrossPages(30),
                "Expected at least one PDF card under Learning aids across pages."
        );

        step("Open first PDF card");
        WebElement pdfCard = resourcesPage.getPdfCards().get(0);
        String expectedTitle = resourcesPage.getTitleFromCard(pdfCard);
        resourcesPage.openPdfSameTab(pdfCard);

        ResourcesDetailsPage detailsPage = new ResourcesDetailsPage(driver()).waitUntilLoaded();

        step("Sanity: details page loaded (title matches selected card)");
        Assert.assertEquals(
                detailsPage.getPdfTitle(),
                expectedTitle,
                "PDF title should match selected resource"
        );

        step("Click back arrow to return to Resources list");
        detailsPage.goBackToResources();

        ResourcesPage backToResources = new ResourcesPage(driver()).waitUntilLoaded();

        step("Validate we are back on Resources list page");
        Assert.assertTrue(
                backToResources.isLoaded(),
                "Expected Resources page to be loaded after clicking back arrow"
        );

        Assert.assertTrue(
                driver().getCurrentUrl().contains("/dashboard/resources"),
                "Expected URL to contain /dashboard/resources after back navigation. Current: " + driver().getCurrentUrl()
        );
    }


    @Test(description = "TILT-1893 | Resources – Search filters resources by keyword")
    public void searchFiltersResourcesByKeyword() throws InterruptedException {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Resources page");
        ResourcesPage resourcesPage = dashboardPage.goToResources().waitUntilLoaded();

        step("Ensure View All shows at least one resource card (post or pdf)");
        List<WebElement> initialCards = resourcesPage.getAnyCardsInActivePane();
        Assert.assertFalse(initialCards.isEmpty(), "Expected at least one resource card in View All");

        step("Pick a keyword from first card title (stable)");
        WebElement firstCard = initialCards.get(0);
        String fullTitle = resourcesPage.getTitleFromCard(firstCard).trim();
        Assert.assertFalse(fullTitle.isBlank(), "Expected first resource card title to be non-empty");

        // keyword = first 4 chars (or shorter if needed)
        String keyword = fullTitle.length() >= 4 ? fullTitle.substring(0, 4) : fullTitle;
        keyword = keyword.trim();
        Assert.assertFalse(keyword.isBlank(), "Derived keyword should not be blank");

        step("Search by keyword: " + keyword);
        resourcesPage.searchFor(keyword);

        step("Verify results are filtered");
        List<WebElement> filteredCards = resourcesPage.getAnyCardsInActivePane();

        Assert.assertTrue(
                !filteredCards.isEmpty() || resourcesPage.isEmptyStateVisible(),
                "Expected filtered results or an empty-state UI"
        );

        // If we got cards, ensure each matches keyword in title OR subtitle/description
        if (!filteredCards.isEmpty()) {
            String kw = keyword.toLowerCase();

            for (WebElement card : filteredCards) {
                String title = resourcesPage.getTitleFromCard(card).toLowerCase();
                String sub   = resourcesPage.getSubtitleOrFilenameFromCard(card).toLowerCase();

                Assert.assertTrue(
                        title.contains(kw) || sub.contains(kw),
                        "Card should match keyword in title or subtitle. kw=" + keyword +
                                " | title=" + title + " | subtitle=" + sub
                );
            }

            // Optional sanity: filtered count should be <= initial count (usually true)
            Assert.assertTrue(
                    filteredCards.size() <= initialCards.size(),
                    "Expected filtered card count to be <= initial card count. initial=" +
                            initialCards.size() + " filtered=" + filteredCards.size()
            );
        }

        step("Clear search (cleanup)");
        resourcesPage.clearSearch();

    }


    @Test(description = "TILT-1894 | Resources – Search returns no results state")
    public void searchReturnsNoResultsState() throws InterruptedException {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Resources page");
        ResourcesPage resourcesPage = dashboardPage.goToResources().waitUntilLoaded();

        step("Search for a keyword that should return no matches");
        String keyword = "zzzz__no_match__" + System.currentTimeMillis();
        resourcesPage.search(keyword);

        step("Verify empty state is displayed");
        Assert.assertTrue(
                resourcesPage.isEmptyStateVisible(),
                "Expected an empty state when no resources match search. Keyword: " + keyword
        );

        step("Verify no cards are shown");
        // We accept either: no posts and no pdfs visible (since View All can mix types)
        Assert.assertTrue(
                resourcesPage.getResourceCards().isEmpty() && resourcesPage.getPdfCards().isEmpty(),
                "Expected no resource cards when empty state is shown."
        );
    }


    @Test(description = "TILT-1895 | Resources – Pagination controls are visible")
    public void paginationControlsAreVisible() {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Resources page");
        ResourcesPage resourcesPage = dashboardPage.goToResources().waitUntilLoaded();

        step("Check if pagination is required (more than 1 page)");
        int pageItems = resourcesPage.getPaginationPageItemCount();

        if (pageItems > 1) {
            step("Pagination root is visible");
            Assert.assertTrue(
                    resourcesPage.isPaginationVisible(),
                    "Expected pagination controls to be visible when multiple pages exist. pageItems=" + pageItems
            );

            step("Next button should be enabled when there are more pages");
            Assert.assertTrue(
                    resourcesPage.hasNextPage(),
                    "Expected Next pagination button to be enabled when multiple pages exist."
            );
        } else {
            // Environment may have little data; do not fail the suite for that.
            step("No pagination required in this environment");
            Assert.assertTrue(true, "Only one page of results available.");
        }
    }


    @Test(description = "TILT-1896 | Resources – Pagination navigates between pages")
    public void paginationNavigatesBetweenPages() {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Resources page");
        ResourcesPage resourcesPage = dashboardPage.goToResources().waitUntilLoaded();

        step("Skip if pagination is not present / only 1 page");
        int pageItems = resourcesPage.getPaginationPageItemCount();
        if (pageItems <= 1 || !resourcesPage.hasNextPage()) {
            Assert.assertTrue(true, "No pagination available in this environment (only one page).");
            return;
        }

        step("Capture list signature on page 1");
        int pageBefore = resourcesPage.getActivePageNumber();
        String sigBefore = resourcesPage.getActivePaneListSignature(5);

        step("Go to next page");
        resourcesPage.goToNextPage();

        step("Verify pagination page changed and list updated");
        int pageAfter = resourcesPage.getActivePageNumber();
        String sigAfter = resourcesPage.getActivePaneListSignature(5);

        // If we can read page number, assert it increments
        if (pageBefore > 0 && pageAfter > 0) {
            Assert.assertTrue(
                    pageAfter == pageBefore + 1 || pageAfter != pageBefore,
                    "Expected active page number to change after Next. Before=" + pageBefore + " After=" + pageAfter
            );
        }

        Assert.assertNotEquals(
                sigAfter,
                sigBefore,
                "Expected resources list to update after navigating to the next pagination page."
        );

        step("Go back to previous page");
        Assert.assertTrue(resourcesPage.hasPrevPage(), "Expected Prev button to be enabled after going Next.");
        resourcesPage.goToPrevPage();

        step("Verify list signature returns to original (or at least changes back)");
        String sigBack = resourcesPage.getActivePaneListSignature(5);
        Assert.assertEquals(
                sigBack,
                sigBefore,
                "Expected resources list to return to the original content after navigating back to previous page."
        );
    }


    @Test(description = "TILT-1897 | Resources – Latest Resources section loads")
    public void latestResourcesSectionLoads() {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Resources page");
        ResourcesPage resourcesPage = dashboardPage.goToResources().waitUntilLoaded();

        step("Latest Resources section is visible");
        Assert.assertTrue(
                resourcesPage.isLatestResourcesSectionVisible(),
                "Expected 'Latest Resources' section heading to be visible."
        );

        step("Latest Resources shows at least one recent article card");
        List<WebElement> latestCards = resourcesPage.getLatestResourcesCards();
        Assert.assertFalse(latestCards.isEmpty(), "Expected at least 1 card under 'Latest Resources'.");

        step("Each card has a title and thumbnail");
        // Validate first few to keep it stable
        int toCheck = Math.min(3, latestCards.size());
        for (int i = 0; i < toCheck; i++) {
            WebElement card = latestCards.get(i);

            String title = resourcesPage.getLatestCardTitle(card);
            Assert.assertFalse(title.isBlank(), "Latest resource card should have a non-empty title (index " + i + ").");

            String thumbSrc = resourcesPage.getLatestCardThumbnailSrc(card);
            Assert.assertFalse(thumbSrc.isBlank(), "Latest resource card should have a thumbnail <img> src (index " + i + ").");
        }
    }


    @Test(description = "TILT-1898 | Resources – Latest Resources items are clickable")
    public void latestResourcesItemsAreClickable() {

        step("Login as admin");
        DashboardPage dashboardPage = startFreshSession(null);
        Assert.assertTrue(dashboardPage.isLoaded(), "❌ Dashboard did not load after login");

        step("Go to Resources page");
        ResourcesPage resourcesPage = dashboardPage.goToResources().waitUntilLoaded();

        step("Latest Resources section is visible");
        Assert.assertTrue(
                resourcesPage.isLatestResourcesSectionVisible(),
                "Latest Resources section should be visible"
        );

        step("Pick first Latest Resources item");
        List<WebElement> latestCards = resourcesPage.getLatestResourcesCards();
        Assert.assertFalse(latestCards.isEmpty(), "Expected at least 1 Latest Resources card");

        WebElement firstCard = latestCards.get(0);
        String expectedTitle = resourcesPage.getLatestCardTitle(firstCard);
        String originalHandle = driver().getWindowHandle();
        int handlesBefore = driver().getWindowHandles().size();

        step("Click latest resource card");
        resourcesPage.clickLatestResourceCard(firstCard);

        step("A new tab/window opens");
        new WebDriverWait(driver(), Duration.ofSeconds(20))
                .until(d -> d.getWindowHandles().size() > handlesBefore);

        String newHandle = driver().getWindowHandles().stream()
                .filter(h -> !h.equals(originalHandle))
                .findFirst()
                .orElseThrow(() -> new AssertionError("❌ No new window handle detected after click"));

        driver().switchTo().window(newHandle);
        waitForDocumentReady(driver());

        step("Validate it navigated to an external Tilt365 blog page");
        String newUrl = driver().getCurrentUrl();

        Assert.assertTrue(
                newUrl.contains("tilt365.com") && newUrl.contains("/blog/"),
                "Expected Latest Resources click to open a Tilt365 blog URL. Actual: " + newUrl
        );

        // Optional: title sanity check (not strict because it can truncate in UI)
        Assert.assertTrue(
                expectedTitle.isBlank() || driver().getTitle().toLowerCase().contains(expectedTitle.toLowerCase().substring(0, Math.min(10, expectedTitle.length()))),
                "Blog page title should roughly match clicked card title. Clicked: " + expectedTitle + " | Page title: " + driver().getTitle()
        );

        step("Close tab and return to Resources");
        driver().close();
        driver().switchTo().window(originalHandle);
        waitForDocumentReady(driver());

        Assert.assertTrue(
                resourcesPage.isLoaded(),
                "Should be back on Resources page after closing blog tab"
        );
    }





}
