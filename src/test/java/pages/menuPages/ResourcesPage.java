package pages.menuPages;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import pages.BasePage;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static java.lang.Thread.sleep;

public class ResourcesPage extends BasePage{





    // Main title
    private final By headingResources =
            By.xpath("//h1[contains(normalize-space(), 'Resources')]");

    // Hero CTA
    private final By heroCtaButton =
            By.xpath("//button[contains(., 'Read entire')]");

    // Resource cards (articles + learning aids)
    private final By resourceCards =
            By.xpath("//div[contains(@class,'ant-tabs-tabpane-active')]//a[@type='post']");

    // Inside each card
    private final By resourceTitle =
            By.xpath(".//h3 | .//h4 | .//div[contains(@class,'title')]");

    private final By resourceSubtitleOrFilename =
            By.xpath(".//p | .//span[contains(@class,'subtitle')]");

    // Admin-only actions (edit / delete / kebab)
    private final By resourceActions =
            By.xpath(".//button | .//span[contains(@class,'ant-dropdown')]");


    private final By tabLearningAids =
            By.xpath("//div[@role='tab' and normalize-space()='Learning aids']");

    private final By activeTabPane =
            By.xpath("//div[contains(@class,'ant-tabs-tabpane-active')]");

    // PDF cards inside active tab (Learning aids)
    private final By pdfCardsInActivePane =
            By.xpath("//div[contains(@class,'ant-tabs-tabpane-active')]//a[@type='pdf']");


    // Tabs
    private final By tabViewAll =
            By.xpath("//div[@data-node-key=\"view-all\"]");


    // Selected tab (Ant tabs usually add aria-selected=true)
    private final By selectedTabViewAll =
            By.xpath("//div[@role='tab' and (normalize-space()='View all' or normalize-space()='View All') and (@aria-selected='true' or contains(@class,'ant-tabs-tab-active'))]");


    // Cards in active pane
    private final By postCardsInActivePane =
            By.xpath("//div[contains(@class,'ant-tabs-tabpane-active')]//a[@type='post']");


    // Pagination (Ant)
    private final By paginationRoot =
            By.cssSelector(".ant-pagination");

    private final By paginationItems =
            By.cssSelector(".ant-pagination li.ant-pagination-item");

    // Optional: "total" text if present
    private final By paginationTotalText =
            By.cssSelector(".ant-pagination-total-text");

    private final By paginationNextBtn =
            By.cssSelector(".ant-pagination-next button");

    private final By paginationDisabledNext =
            By.cssSelector(".ant-pagination-next.ant-pagination-disabled, .ant-pagination-next button[disabled]");

    private final By paginationActiveItem =
            By.cssSelector(".ant-pagination li.ant-pagination-item-active");

    private final By paginationPrevBtn =
            By.cssSelector(".ant-pagination-prev button");

    private final By paginationDisabledPrev =
            By.cssSelector(".ant-pagination-prev.ant-pagination-disabled, .ant-pagination-prev button[disabled]");



    // Tab: Articles
    private final By tabArticles =
            By.xpath("//div[@role='tab' and normalize-space()='Articles']");


    // Image inside a post card (try a few common shapes)
    private final By postImageInCard =
            By.xpath(".//img | .//*[contains(@class,'image') or contains(@class,'thumbnail') or @role='img']");


    // Search input (Ant input usually has placeholder "Search" or aria-label)
    private final By searchInput =
            By.xpath("//input[@placeholder=\"Search by keyword\"]");

    // Any card in active pane (View All = mixed)
    private final By anyCardsInActivePane =
            By.xpath("//div[contains(@class,'ant-tabs-tabpane-active')]//a[@type='post' or @type='pdf']");

    // Optional empty state (Ant)
    private final By emptyState =
            By.xpath("//h4[contains(text(),\"No results found\")]");




    // Section heading
    private final By latestResourcesHeading =
            By.xpath("//*[self::h2 or self::h3][contains(normalize-space(),'Latest Resources')]");

    // The list container right after the heading (your HTML: h2 + div(list))
    private final By latestResourcesList =
            By.xpath("//h2[normalize-space()='Latest Resources']/following-sibling::div[1]");

    // Each card is a div that contains an img + h3 + p (your HTML: div.sc-e055effd-7)
    private final By latestResourcesCards =
            By.xpath("//h2[normalize-space()='Latest Resources']/following-sibling::div[1]//div[.//img and .//h3]");

    // Inside each card
    private final By latestCardTitle =
            By.xpath(".//h3[normalize-space()!='']");

    private final By latestCardThumbnail =
            By.xpath(".//img[@src or @srcset]");


    public ResourcesPage(WebDriver driver) {
        super(driver);
    }

    @Override
    public ResourcesPage waitUntilLoaded() {
        wait.waitForDocumentReady();
        wait.waitForLoadersToDisappear();
        isVisible(headingResources);
        return this;
    }

    @Override
    public boolean isLoaded() {
        return isPresent(headingResources);
    }

    public ResourcesPage open(String baseUrl) {
        driver.navigate().to(baseUrl + "/dashboard/resources");
        waitUntilLoaded();
        return this;
    }

    public ResourcesPage goToArticles() {
        safeClick(tabArticles);
        wait.waitForLoadersToDisappear();
        wait.waitForDocumentReady();
        isVisible(activeTabPane);
        return this;
    }


    // ---------- List helpers ----------

    public List<WebElement> getResourceCards() {
        return driver.findElements(resourceCards);
    }

    public boolean hasAtLeastOneResource() {
        return getResourceCards().size() > 0;
    }

    public String getTitleFromCard(WebElement card) {
        return safeText(() -> card.findElement(resourceTitle).getText());
    }

    public String getSubtitleOrFilenameFromCard(WebElement card) {
        return safeText(() -> card.findElement(resourceSubtitleOrFilename).getText());
    }

    public boolean cardHasActions(WebElement card) {
        return !card.findElements(resourceActions).isEmpty();
    }


    public List<WebElement> getPdfCards() {
        return driver.findElements(pdfCardsInActivePane);
    }

    public String getPdfHref(WebElement pdfCard) {
        return pdfCard.getAttribute("href"); // returns: resources/pdf/7
    }

    public void openPdfSameTab(WebElement pdfCard) {
        try {
            safeClick(pdfCard);
        } catch (Exception ignored) {}
        wait.waitForDocumentReady();
    }



    public ResourcesPage goToLearningAids() {
        safeClick(tabLearningAids);
        wait.waitForLoadersToDisappear();
        // Ensure tab panel switched
        isVisible(activeTabPane);
        return this;
    }


    // ---------- Simple visibility helpers ----------

    public boolean isPresentHeading() {
        return isPresent(headingResources);
    }

    public boolean isVisibleHeroCta() {
        return isVisible(heroCtaButton);
    }

    public boolean isViewAllSelected() {
        return isPresent(selectedTabViewAll);
    }

    public boolean hasAtLeastOnePostCard() {
        return hasAtLeastOnePostCardAcrossPages(30);
    }

    public boolean hasAtLeastOnePostCardAcrossPages(int maxPages) {
        wait.waitForLoadersToDisappear();
        wait.waitForDocumentReady();

        for (int page = 1; page <= maxPages; page++) {

            // check current page
            if (!driver.findElements(postCardsInActivePane).isEmpty()) {
                return true;
            }

            // if there's no "next" (or it's disabled), stop
            if (isPresent(paginationDisabledNext)) {
                return false;
            }

            // go next page
            safeClick(driver.findElement(paginationNextBtn));
            wait.waitForLoadersToDisappear();
            wait.waitForDocumentReady();
            isVisible(activeTabPane); // ensure pane is still loaded
        }

        // safety stop
        return !driver.findElements(postCardsInActivePane).isEmpty();
    }


    public boolean hasAtLeastOnePdfCard() {
        return hasAtLeastOnePdfCardAcrossPages(30);
    }

    public boolean hasAtLeastOnePdfCardAcrossPages(int maxPages) {
        wait.waitForLoadersToDisappear();
        wait.waitForDocumentReady();

        for (int page = 1; page <= maxPages; page++) {

            // check current page
            if (!driver.findElements(pdfCardsInActivePane).isEmpty()) {
                return true;
            }

            // if there's no "next" (or it's disabled), stop
            if (isPresent(paginationDisabledNext)) {
                return false;
            }

            // go next page
            safeClick(driver.findElement(paginationNextBtn));
            wait.waitForLoadersToDisappear();
            wait.waitForDocumentReady();
            isVisible(activeTabPane); // ensure the list area is there
        }

        // safety stop (avoid infinite loops if UI is buggy)
        return !driver.findElements(pdfCardsInActivePane).isEmpty();
    }


    public boolean postCardHasImage(WebElement card) {
        if (!card.findElements(postImageInCard).isEmpty()) return true;
        String style = safeText(() -> card.getAttribute("style"));
        return style != null && style.contains("background-image");
    }



    public void openPostSameTab(WebElement postCard) {
        safeClick(postCard);
        wait.waitForDocumentReady();
        wait.waitForLoadersToDisappear();
    }



    public String clickPostAndSwitchToNewTab(WebElement postCard) throws InterruptedException {
        String originalHandle = driver.getWindowHandle();
        Set<String> before = driver.getWindowHandles();

        safeClick(postCard);

        // Wait until a new tab opens
        long end = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
        Set<String> after = driver.getWindowHandles();
        while (after.size() <= before.size() && System.currentTimeMillis() < end) {
            sleep(200);
            after = driver.getWindowHandles();
        }

        if (after.size() <= before.size()) {
            throw new AssertionError("No new tab was opened after clicking the post card.");
        }

        // Switch to the new tab
        for (String h : after) {
            if (!before.contains(h)) {
                driver.switchTo().window(h);
                break;
            }
        }

        wait.waitForDocumentReady();
        return originalHandle;
    }

    public void closeCurrentTabAndReturnTo(String originalHandle) {
        driver.close();
        driver.switchTo().window(originalHandle);
        wait.waitForDocumentReady();
    }

    public void openPost(WebElement postCard) {
        safeClick(postCard); // may open new tab
        wait.waitForDocumentReady();
    }




    // ---------- Search helpers ----------

    public ResourcesPage searchFor(String keyword) {
        safeClick(searchInput);
        type(searchInput, keyword);
        wait.waitForLoadersToDisappear();
        wait.waitForDocumentReady();
        isVisible(activeTabPane);
        return this;
    }

    public ResourcesPage clearSearch() {
        // robust clear: click + CTRL/CMD+A + DEL handled inside BasePage.type/clear logic,
        // so we can just type empty by directly clearing via JS fallback
        WebElement el = driver.findElement(searchInput);
        el.click();
        el.sendKeys(Keys.chord(Keys.COMMAND, "a"), Keys.DELETE);
        el.sendKeys(Keys.chord(Keys.CONTROL,  "a"), Keys.DELETE);
        wait.waitForLoadersToDisappear();
        wait.waitForDocumentReady();
        return this;
    }

    public List<WebElement> getAnyCardsInActivePane() {
        return driver.findElements(anyCardsInActivePane);
    }

    public boolean isEmptyStateVisible() {
        return isPresent(emptyState) && isDisplayedNow(emptyState);
    }

    public ResourcesPage search(String keyword) {
        // Use your BasePage.type() to clear+type + trigger React input events
        type(searchInput, keyword);
        wait.waitForLoadersToDisappear();
        wait.waitForDocumentReady();
        return this;
    }


    public boolean hasNextPage() {
        // If "disabled next" is present, there is no next page
        return !isPresent(paginationDisabledNext);
    }

    public void goToNextPage() {
        safeClick(driver.findElement(paginationNextBtn));
        wait.waitForLoadersToDisappear();
        wait.waitForDocumentReady();
        isVisible(activeTabPane);
    }

    public boolean isPaginationVisible() {
        return isVisible(paginationRoot);
    }

    public int getPaginationPageItemCount() {
        return driver.findElements(paginationItems).size();
    }


    public int getActivePageNumber() {
        try {
            String txt = driver.findElement(paginationActiveItem).getText().trim();
            return Integer.parseInt(txt);
        } catch (Exception e) {
            return -1;
        }
    }

    public boolean hasPrevPage() {
        return !isPresent(paginationDisabledPrev);
    }

    public void goToPrevPage() {
        safeClick(driver.findElement(paginationPrevBtn));
        wait.waitForLoadersToDisappear();
        wait.waitForDocumentReady();
        isVisible(activeTabPane);
    }

    /** Lightweight “list changed” signature: first N hrefs + titles. */
    public String getActivePaneListSignature(int maxItems) {
        wait.waitForLoadersToDisappear();
        wait.waitForDocumentReady();

        StringBuilder sb = new StringBuilder();

        // collect posts + pdfs, whichever exists in current tab
        List<WebElement> posts = driver.findElements(postCardsInActivePane);
        List<WebElement> pdfs  = driver.findElements(pdfCardsInActivePane);

        List<WebElement> cards = !posts.isEmpty() ? posts : pdfs;

        int limit = Math.min(maxItems, cards.size());
        for (int i = 0; i < limit; i++) {
            WebElement c = cards.get(i);
            String href = safeText(() -> c.getAttribute("href"));
            String title = safeText(() -> c.findElement(resourceTitle).getText());
            sb.append(i).append("|").append(href).append("|").append(title).append(";");
        }

        return sb.toString();
    }



    public boolean isLatestResourcesSectionVisible() {
        return isVisible(latestResourcesHeading);
    }

    public List<WebElement> getLatestResourcesCards() {
        // ensure list exists first (avoids false empty)
        isVisible(latestResourcesList);
        return driver.findElements(latestResourcesCards);
    }

    public String getLatestCardTitle(WebElement card) {
        return safeText(() -> card.findElement(latestCardTitle).getText()).trim();
    }

    public String getLatestCardThumbnailSrc(WebElement card) {
        try {
            WebElement img = card.findElement(latestCardThumbnail);
            String src = safeText(() -> img.getAttribute("src")).trim();
            if (src.isBlank()) src = safeText(() -> img.getAttribute("srcset")).trim();
            return src;
        } catch (Exception e) {
            return "";
        }
    }


    // Clickable card (whole block)
    public void clickLatestResourceCard(WebElement card) {
        safeClick(card);
        wait.waitForDocumentReady();
    }

    // Best-effort: find an URL-like text inside the card (not always present)
    public String getLatestCardAnyHrefIfPresent(WebElement card) {
        try {
            // If they ever wrap with <a>, we’ll catch it
            WebElement a = card.findElement(By.xpath(".//a[@href]"));
            return a.getAttribute("href");
        } catch (Exception ignored) {
            return "";
        }
    }




}
