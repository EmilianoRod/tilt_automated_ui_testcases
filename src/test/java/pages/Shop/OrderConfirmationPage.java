package pages.Shop;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import pages.BasePage;

public class OrderConfirmationPage extends BasePage {


    public OrderConfirmationPage(WebDriver driver) {
        super(driver);
    }

    // ── Locators (tolerant to copy/markup changes) ─────────────────────────────
    // Header could be “Order Confirmation”, “Thank you…”, etc.
    private final By confirmationHeader = By.xpath(
            "//*[self::h1 or self::h2][contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'order confirmation') "
                    + "or contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'thank you')]"
    );

    // Generic success text somewhere on the page
    private final By successMessage = By.xpath(
            "//*[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'successfully') "
                    + "or contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'confirmed') "
                    + "or contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'thank you')]"
    );

    // Order number / ID (label on left, value next)
    private final By orderNumberValue = By.xpath(
            "//*[contains(normalize-space(.),'Order Number') or contains(normalize-space(.),'Order ID')]"
                    + "/following::*[self::span or self::strong or self::p][1]"
    );

    // Optional overlay/spinner guard
    private final By blockingOverlay = By.xpath(
            "//*[self::div or self::span][contains(@class,'loading') or contains(@class,'spinner') or contains(@class,'overlay') or @role='progressbar']"
    );

    // ── API ────────────────────────────────────────────────────────────────────
    public OrderConfirmationPage waitUntilLoaded() {
        try { wait.waitForElementInvisible(blockingOverlay); } catch (Exception ignore) {}
        // Header or any success message should be visible
        try { wait.waitForElementVisible(confirmationHeader); }
        catch (Exception e) { wait.waitForElementVisible(successMessage); }
        return this;
    }

    public boolean isSuccessBannerVisible() {
        try {
            wait.waitForElementVisible(confirmationHeader);
            return true;
        } catch (Exception e) {
            try {
                wait.waitForElementVisible(successMessage);
                return true;
            } catch (Exception ignore) {
                return false;
            }
        }
    }

    public boolean hasOrderId() {
        return !driver.findElements(orderNumberValue).isEmpty()
                && !driver.findElement(orderNumberValue).getText().trim().isEmpty();
    }

    public String getOrderIdText() {
        try {
            return driver.findElement(orderNumberValue).getText().trim();
        } catch (NoSuchElementException e) {
            return "";
        }
    }

    public String getHeaderText() {
        try {
            return driver.findElement(confirmationHeader).getText().trim();
        } catch (NoSuchElementException e) {
            return "";
        }
    }


}
