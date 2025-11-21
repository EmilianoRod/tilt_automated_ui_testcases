package pages.menuPages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import pages.BasePage;

public class ResourcesPage extends BasePage{




    // Main title "Resources"
    private final By headingResources = By.xpath("//h1[contains(normalize-space(), 'Resources')]");

    // Example: big hero card button "Read entire article"
    private final By heroCtaButton = By.xpath("//button[contains(., 'Read entire article') or contains(., 'Read entire')]");

    public ResourcesPage(WebDriver driver) {
        super(driver);
    }

    @Override
    public ResourcesPage waitUntilLoaded() {
        wait.waitForDocumentReady();
        wait.waitForLoadersToDisappear();
        isVisible(headingResources);
        isVisible(heroCtaButton);
        return this;
    }

    @Override
    public boolean isLoaded() {
        return  isPresent(headingResources) && isPresent(heroCtaButton);
    }


    public ResourcesPage open(String baseUrl) {
        driver.navigate().to(baseUrl + "/dashboard/resources");
        waitUntilLoaded();
        return this;
    }


}
