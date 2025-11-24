package pages.assesstmens;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import pages.BasePage;

public class AbstractAssessmentPage extends BasePage {

    protected AbstractAssessmentPage(WebDriver driver) {
        super(driver);
    }

    @Override
    public BasePage waitUntilLoaded() {
        return null;
    }

    // Common generic helpers if you want them later…
    protected boolean isElementDisplayed(By locator) {
        return !driver.findElements(locator).isEmpty()
                && driver.findElement(locator).isDisplayed();
    }


}
