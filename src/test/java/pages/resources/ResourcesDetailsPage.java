package pages.resources;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import pages.BasePage;

import static java.nio.file.Files.getAttribute;

public class ResourcesDetailsPage extends BasePage {

    // Back arrow to resources
    private final By backToResourcesLink =
            By.xpath("//a[@href='/dashboard/resources']");

    // Page title: Learning Aids / <title>
    private final By pageTitle =
            By.xpath("//h1[contains(normalize-space(),'Learning Aids')]");

    // PDF title (bold part)
    private final By pdfTitle =
            By.xpath("//h1//b");

    // Download full PDF button
    private final By downloadPdfButton =
            By.xpath("//button[normalize-space()=\"Download full PDF\"]");

    // Embedded PDF iframe
    private final By pdfIframe =
            By.xpath("//iframe[contains(@src,'active_storage')]");

    public ResourcesDetailsPage(WebDriver driver) {
        super(driver);
    }

    @Override
    public ResourcesDetailsPage waitUntilLoaded() {
        wait.waitForDocumentReady();
        wait.waitForLoadersToDisappear();
        isVisible(pageTitle);
        isVisible(pdfIframe);
        return this;
    }

    @Override
    public boolean isLoaded() {
        return isPresent(pageTitle) && isPresent(pdfIframe);
    }

    // ---------- Getters ----------

    public String getPdfTitle() {
        return getText(pdfTitle).trim();
    }

    public boolean isDownloadButtonVisible() {
        return isVisible(downloadPdfButton);
    }

    public String getPdfIframeSrc() {
        return driver.findElement(pdfIframe).getAttribute("src");
    }


    public void goBackToResources() {
        click(backToResourcesLink);
        wait.waitForDocumentReady();
    }


    public void clickDownloadFullPdf() {
        safeClick(downloadPdfButton);
        // do NOT wait for loaders here; download may open new tab or trigger native download
    }



    
}
