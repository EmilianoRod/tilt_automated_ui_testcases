package pages.assesstmens.agt;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import pages.assesstmens.AbstractAssessmentPage;

public class AgtIntroPage extends AbstractAssessmentPage {



    private static final By HEADING =
            By.xpath("//h1[contains(.,'Goal Tilt Personality Profile') or contains(.,'AGT')]");
    private static final By LETS_GET_STARTED_BUTTON =
            By.xpath("//button[contains(.,\"Let's get started\")]");

    public AgtIntroPage(WebDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isElementDisplayed(HEADING) && isElementDisplayed(LETS_GET_STARTED_BUTTON);
    }



}
