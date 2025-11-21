package pages.SignUp;

import Utils.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.BasePage;
import pages.LoginPage;

import java.time.Duration;

/**
 * Page object for the 2-step Sign Up flow:
 *  Step 1 → Basic info (First/Last/Email/Password + Continue)
 *  Step 2 → GDPR Notice & required checkboxes + Sign Up
 */
public class SignUpPage extends BasePage {

    private static final Logger logger = LogManager.getLogger(SignUpPage.class);

    // --------------------------------------------------------
    // STEP 1 LOCATORS
    // --------------------------------------------------------
    private final By firstNameField  = By.id("firstName");
    private final By lastNameField   = By.id("lastName");
    private final By emailField      = By.id("email");
    private final By passwordField   = By.id("password");
    private final By continueButton  = By.xpath("//button[normalize-space()='Continue']");
    private final By firstStepHeader = By.xpath("//h1[normalize-space()='Create an account']");

    private final By firstStepErrorMsg = By.xpath("//span[@type='error']");

    // --------------------------------------------------------
    // STEP 2 LOCATORS (GDPR)
    // --------------------------------------------------------
    private final By termsCheckbox   = By.id("termsAndConditions");
    private final By older16Checkbox = By.id("confirmAge");
    private final By signUpButton    = By.xpath("//button[normalize-space()='Sign Up']");
    private final By gdprSection     = By.xpath("//div[contains(.,'Tilt365 launched a new version')]");

    public SignUpPage(WebDriver driver) {
        super(driver);
    }

    @Override
    public BasePage waitUntilLoaded() {
        return null;
    }

    // --------------------------------------------------------
    // NAVIGATION
    // --------------------------------------------------------
    public void navigateTo() {
        String url = Config.joinUrl(Config.getBaseUrl(), "/auth/sign-up");
        driver.get(url);

        new WebDriverWait(driver, Config.getTimeout())
                .until(ExpectedConditions.or(
                        ExpectedConditions.urlContains("/auth/sign-up"),
                        ExpectedConditions.urlContains("sign-up")
                ));

        waitForFirstStepLoaded();
    }

    // --------------------------------------------------------
    // STEP 1 METHODS
    // --------------------------------------------------------

    public void waitForFirstStepLoaded() {
        wait.waitForDocumentReady();
        wait.waitForLoadersToDisappear();
        wait.waitForElementVisible(firstStepHeader);
        wait.waitForElementVisible(firstNameField);
    }

    public boolean isFirstStepLoaded() {
        try {
            return isVisible(firstNameField) && isVisible(continueButton);
        } catch (Exception e) {
            return false;
        }
    }

    public SignUpPage typeFirstName(String txt) {
        WebElement f = wait.waitForElementVisible(firstNameField);
        f.clear(); f.sendKeys(txt);
        return this;
    }

    public SignUpPage typeLastName(String txt) {
        WebElement f = wait.waitForElementVisible(lastNameField);
        f.clear(); f.sendKeys(txt);
        return this;
    }

    public SignUpPage typeEmail(String txt) {
        WebElement f = wait.waitForElementVisible(emailField);
        f.clear(); f.sendKeys(txt);
        return this;
    }

    public SignUpPage typePassword(String txt) {
        WebElement f = wait.waitForElementVisible(passwordField);
        f.clear(); f.sendKeys(txt);
        return this;
    }

    public SignUpPage fillStep1(String first, String last, String email, String pass) {
        return typeFirstName(first)
                .typeLastName(last)
                .typeEmail(email)
                .typePassword(pass);
    }

    public boolean isContinueEnabled() {
        return wait.waitForElementVisible(continueButton).isEnabled();
    }

    public void waitUntilContinueEnabled(Duration timeout) {
        new WebDriverWait(driver, timeout)
                .until(d -> d.findElement(continueButton).isEnabled());
    }

    public void waitUntilContinueDisabled(Duration timeout) {
        new WebDriverWait(driver, timeout)
                .until(d -> !d.findElement(continueButton).isEnabled());
    }

    public SignUpPage clickContinue() {
        WebElement btn = wait.waitForElementClickable(continueButton);
        btn.click();
        logger.info("Clicked Continue on Sign Up Step 1");
        return this;
    }

    public String getStep1ErrorText() {
        try {
            return wait.waitForElementVisible(firstStepErrorMsg).getText().trim();
        } catch (Exception e) {
            return "";
        }
    }

    // --------------------------------------------------------
    // STEP 2 METHODS (GDPR CONSENT)
    // --------------------------------------------------------

    public void waitForConsentStepLoaded() {
        wait.waitForDocumentReady();
        wait.waitForLoadersToDisappear();
        wait.waitForElementVisible(gdprSection);
        wait.waitForElementVisible(signUpButton);
    }

    public boolean isConsentStepLoaded() {
        try {
            return isVisible(gdprSection) && isVisible(signUpButton);
        } catch (Exception e) {
            return false;
        }
    }

    private void toggleCheckbox(By locator, boolean expected) {
        WebElement cb = wait.waitForElementClickable(locator);
        if (cb.isSelected() != expected) {
            cb.click();
        }
    }

    public SignUpPage acceptTerms() {
        toggleCheckbox(termsCheckbox, true);
        return this;
    }

    public SignUpPage confirmAge() {
        toggleCheckbox(older16Checkbox, true);
        return this;
    }

    public SignUpPage acceptAllConsents() {
        return acceptTerms().confirmAge();
    }

    public boolean isSignUpEnabled() {
        return wait.waitForElementVisible(signUpButton).isEnabled();
    }

    public void waitUntilSignUpEnabled(Duration timeout) {
        new WebDriverWait(driver, timeout)
                .until(d -> d.findElement(signUpButton).isEnabled());
    }

    public LoginPage clickSignUp() {
        WebElement btn = wait.waitForElementClickable(signUpButton);
        btn.click();
        logger.info("Clicked Sign Up to complete account creation");

        // after Sign Up → redirected to Sign In
        return new LoginPage(driver);
    }

    // --------------------------------------------------------
    // HIGH-LEVEL END-TO-END SIGN-UP (MAILSLURP USES THIS)
    // --------------------------------------------------------

    public LoginPage completeSignUp(String firstName,
                                    String lastName,
                                    String email,
                                    String password) {

        waitForFirstStepLoaded();
        fillStep1(firstName, lastName, email, password);

        waitUntilContinueEnabled(Duration.ofSeconds(5));
        clickContinue();

        waitForConsentStepLoaded();
        acceptAllConsents();
        waitUntilSignUpEnabled(Duration.ofSeconds(5));

        return clickSignUp();
    }
}
