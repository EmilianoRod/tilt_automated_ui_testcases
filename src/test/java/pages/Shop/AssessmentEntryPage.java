package pages.Shop;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import pages.BasePage;

import java.util.List;

public class AssessmentEntryPage extends BasePage {

    public AssessmentEntryPage(WebDriver driver) {
        super(driver);
    }

    private By manualEntryOption = By.xpath("(//input[@type='radio'])[1]");
    private By downloadTemplateOption = By.xpath("(//input[@type='radio'])[2]");
    private By numberInput = By.xpath("//input[@placeholder='0']");
    private By cancelButton = By.xpath("//button[contains(., 'Cancel')]");
    private By proceedToPaymentButton = By.xpath("//button[contains(., 'Proceed to payment')]");

    // Dynamic field locators using index
    private String firstNameFieldXPath = "(//input[@placeholder='First Name'])[%d]";
    private String lastNameFieldXPath = "(//input[@placeholder='Last Name'])[%d]";
    private String emailFieldXPath = "(//input[@placeholder='Email'])[%d]";


    public void selectManualEntry() {
        wait.waitForElementClickable(manualEntryOption).click();
    }

    public void selectDownloadTemplate() {
        wait.waitForElementClickable(downloadTemplateOption).click();
    }

    public void enterNumberOfIndividuals(String count) {
        WebElement input = wait.waitForElementVisible(numberInput);
        input.clear();
        input.sendKeys(count);
    }

    public void fillUserDetailsAtIndex(int index, String firstName, String lastName, String email) {
        wait.waitForElementVisible(By.xpath(String.format(firstNameFieldXPath, index))).sendKeys(firstName);
        wait.waitForElementVisible(By.xpath(String.format(lastNameFieldXPath, index))).sendKeys(lastName);
        wait.waitForElementVisible(By.xpath(String.format(emailFieldXPath, index))).sendKeys(email);
    }

    public void clickCancel() {
        wait.waitForElementClickable(cancelButton).click();
    }

    public OrderPreviewPage clickProceedToPayment() {
        wait.waitForElementClickable(proceedToPaymentButton).click();
        return new OrderPreviewPage(driver);
    }


    public int getTotalInputRowsRendered() {
        List<WebElement> emailFields = driver.findElements(By.xpath("//input[@placeholder='Email']"));
        return emailFields.size(); // All rows should have email field; use it to count total rows
    }

    public boolean isExpectedRowCountRendered(int expected) {
        return getTotalInputRowsRendered() == expected;
    }

}
