package pages.Shop;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import pages.BasePage;

public class PurchaseRecipientSelectionPage extends BasePage {




    // Constructor
    public PurchaseRecipientSelectionPage(WebDriver driver) {
        super(driver);
    }

    // Locators
    private final By title = By.xpath("//h3[normalize-space()='Selected Assessments']");
    private final By question = By.xpath("//h2[normalize-space()='Who is the purchase for?']");
    private final By myselfOption = By.xpath("//body/div[@id='__next']/div/div/main/section/div/div[@alignitems='flex-start']/div/div[@direction='column']/div[1]");
    private final By clientOption = By.xpath("//body/div[@id='__next']/div/div/main/section/div/div[@alignitems='flex-start']/div/div[@direction='column']/div[2]");
    private final By groupTeamOption = By.xpath("//body/div[@id='__next']/div/div/main/section/div/div[@alignitems='flex-start']/div/div[@direction='column']/div[3]");
    private final By cancelButton = By.xpath("//button[normalize-space()='Cancel']");
    private final By nextButton = By.xpath("//button[normalize-space()='Next']");



    // Actions
    public boolean isLoaded() {
        return isVisible(title) && isVisible(question);
    }

    public void selectMyself() {
        click(myselfOption);
    }

    public void selectClientOrIndividual() {
        click(clientOption);
    }

    public void selectGroupTeam() {
        click(groupTeamOption);
    }

    public void clickCancel() {
        click(cancelButton);
    }

    public AssessmentEntryPage clickNext() {
        click(nextButton);
        return new AssessmentEntryPage(driver);
    }

    public boolean isNextButtonDisabled() {
        return !isClickable(nextButton);
    }
}
