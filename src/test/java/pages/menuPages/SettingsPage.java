package pages.menuPages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import pages.BasePage;

import java.time.Duration;

public class SettingsPage extends BasePage {

    // Relative path for this page from the base URL
    public static final String PATH = "/dashboard/settings";

    // ===== Locators =====

    private final By settingsTitle    = By.xpath("//main//h1[normalize-space()='Settings']");

    private final By saveButton       = By.xpath("//button[normalize-space()='Save changes']");
    private final By cancelButton     = By.xpath("//button[normalize-space()='Cancel']");

    private final By fullNameHeader   = By.xpath("//section//h3"); // top name (e.g., "John Doe")

    // Personal info inputs
    private final By firstNameInput   = By.xpath("//input[@id='firstName' or @name='firstName']");
    private final By lastNameInput    = By.xpath("//input[@id='lastName' or @name='lastName']");
    private final By emailInput       = By.xpath("//input[@id='email' or @name='email']");

    // Change password inputs
    private final By currentPasswordInput    = By.xpath("//input[@id='oldPassword' or @name='oldPassword']");
    private final By newPasswordInput        = By.xpath("//input[@id='password' or @name='password']");
    private final By repeatNewPasswordInput  = By.xpath("//input[@id='passwordConfirmation' or @name='passwordConfirmation']");

    private final By deleteAccountButton     = By.xpath("//button[normalize-space()='Delete Account']");

    public SettingsPage(WebDriver driver) {
        super(driver);
    }

    // ===== Page readiness =====

    @Override
    public SettingsPage waitUntilLoaded() {
        // Generic page-ready (DOM + loaders)
        pageReady();

        // Core identity for the page
        waitForElementVisible(settingsTitle);

        // Key interactive fields
        waitForElementVisible(firstNameInput);
        waitForElementVisible(lastNameInput);
        waitForElementVisible(emailInput);
        return this;
    }

    @Override
    public boolean isLoaded() {
        // URL fragment + header is usually enough
        return waitForUrlContains("/settings") && isVisible(settingsTitle);
    }

    /**
     * Open Settings by URL. Call with Config.getBaseUrl() from tests.
     */
    public SettingsPage open(String baseUrl) {
        driver.navigate().to(baseUrl + PATH);
        return waitUntilLoaded();
    }

    // ===== Personal info helpers =====

    public String getFullName() {
        try {
            return waitForElementVisible(fullNameHeader).getText();
        } catch (Exception e) {
            return "";
        }
    }

    public String getFirstName() {
        WebElement el = waitForElementVisible(firstNameInput);
        return el.getAttribute("value");
    }

    public String getLastName() {
        WebElement el = waitForElementVisible(lastNameInput);
        return el.getAttribute("value");
    }

    public String getEmail() {
        WebElement el = waitForElementVisible(emailInput);
        return el.getAttribute("value");
    }

    public SettingsPage setFirstName(String firstName) {
        type(firstNameInput, firstName);
        return this;
    }

    public SettingsPage setLastName(String lastName) {
        type(lastNameInput, lastName);
        return this;
    }

    /**
     * Typically you don't change email from here in tests, but method is handy.
     */
    public SettingsPage setEmail(String email) {
        type(emailInput, email);
        return this;
    }

    // ===== Password helpers =====

    public SettingsPage changePassword(String currentPassword, String newPassword) {
        type(currentPasswordInput, currentPassword);
        type(newPasswordInput, newPassword);
        type(repeatNewPasswordInput, newPassword);
        return this;
    }

    // ===== Actions =====

    public SettingsPage clickSaveChanges() {
        click(saveButton);  // this uses BasePage.click → waits for clickable
        waitForNetworkIdle(Duration.ofSeconds(10));
        return this;
    }


    public SettingsPage clickCancel() {
        click(cancelButton);
        return this;
    }

    public boolean isDeleteAccountVisible() {
        return isVisible(deleteAccountButton);
    }

    public void clickDeleteAccount() {
        click(deleteAccountButton);
        // TODO: handle confirmation modal if needed
    }
}
