package pages.assesstmens.ttp;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import pages.assesstmens.AbstractAssessmentPage;

public class TtpIntroPage extends AbstractAssessmentPage {


    private static final By HEADING =
            By.xpath("//h1[contains(normalize-space(),'True Tilt Personality Profile')]");
    private static final By LETS_GET_STARTED_BUTTON =
            By.xpath("//button[contains(.,\"Let's get started\")]");

    public TtpIntroPage(WebDriver driver) {
        super(driver);
    }

    /** We consider this page "loaded" if the intro heading + button are visible. */
    public boolean isLoaded() {
        return isElementDisplayed(HEADING) && isElementDisplayed(LETS_GET_STARTED_BUTTON);
    }

    public void clickLetsGetStarted() {
        click(LETS_GET_STARTED_BUTTON);
    }
}
