package pages.Shop;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import pages.BasePage;
import pages.Shop.Stripe.StripeCheckoutPage;

public class OrderPreviewPage extends BasePage {



    public OrderPreviewPage(WebDriver driver) {
        super(driver);
    }

    private By couponCheckbox = By.xpath("//label[contains(., 'I have a coupon code')]");
    private By previousButton = By.xpath("//button[contains(., 'Previous')]");
    private By payWithStripeButton = By.xpath("//button[contains(., 'Pay With') and contains(., 'stripe')]");

    public void checkCouponCheckbox() {
        wait.waitForElementClickable(couponCheckbox).click();
    }

    public void clickPrevious() {
        wait.waitForElementClickable(previousButton).click();
    }

    public StripeCheckoutPage clickPayWithStripe() {
        wait.waitForElementClickable(payWithStripeButton).click();
        return new StripeCheckoutPage(driver);
    }




}
