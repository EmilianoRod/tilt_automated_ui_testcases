package pages.Shop.Stripe;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import pages.BasePage;

public class StripeCheckoutPage extends BasePage{

    public StripeCheckoutPage(WebDriver driver) {
        super(driver);
    }


    // Email and Pay
    private By emailField = By.xpath("//input[@type='email']");
    private By payButton = By.xpath("//button[contains(., 'Pay')]");

    // Stripe iframe locators
    private By cardNumberIframe = By.cssSelector("iframe[name^='__privateStripeFrame'][title*='card number']");
    private By expiryIframe = By.cssSelector("iframe[name^='__privateStripeFrame'][title*='expiration']");
    private By cvcIframe = By.cssSelector("iframe[name^='__privateStripeFrame'][title*='CVC']");

    // Input fields inside iframes
    private By cardNumberInput = By.name("cardNumber");
    private By expiryInput = By.name("cardExpiry");
    private By cvcInput = By.name("cardCvc");

    public void enterEmail(String email) {
        wait.waitForElementVisible(emailField).sendKeys(email);
    }

    public void enterCardDetails(String cardNumber, String expiry, String cvc) {
        enterValueInStripeIframe(cardNumberIframe, cardNumberInput, cardNumber);
        enterValueInStripeIframe(expiryIframe, expiryInput, expiry);
        enterValueInStripeIframe(cvcIframe, cvcInput, cvc);
    }

    private void enterValueInStripeIframe(By iframeSelector, By inputField, String value) {
        WebElement iframe = wait.waitForElementVisible(iframeSelector);
        driver.switchTo().frame(iframe);
        wait.waitForElementVisible(inputField).sendKeys(value);
        driver.switchTo().defaultContent();
    }

    public void clickPay() {
        wait.waitForElementClickable(payButton).click();
    }


}
