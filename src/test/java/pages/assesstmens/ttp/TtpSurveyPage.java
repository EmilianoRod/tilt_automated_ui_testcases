package pages.assesstmens.ttp;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import pages.assesstmens.AbstractAssessmentPage;

public class TtpSurveyPage extends AbstractAssessmentPage {


    private static final By PROGRESS_STEP =
            By.xpath("//*[contains(normalize-space(),'Step') and contains(normalize-space(),'/22')]");
    private static final By NEXT_BUTTON =
            By.xpath("//button[contains(.,'Next')]");

    public TtpSurveyPage(WebDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        // very loose heuristic: progress bar + Next button
        return isElementDisplayed(PROGRESS_STEP) && isElementDisplayed(NEXT_BUTTON);
    }

}
