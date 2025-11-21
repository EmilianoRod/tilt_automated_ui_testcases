package pages.menuPages;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.BasePage;
import pages.Shop.PurchaseRecipientSelectionPage;

import java.time.Duration;

/**
 * Shop landing page listing purchasable assessments.
 * - Robust identity check
 * - Card-scoped actions (avoid clicking the wrong “Buy”)
 * - Useful helpers for prices/visibility
 */
public class ShopPage extends BasePage {

    public ShopPage(WebDriver driver) { super(driver); }

    // ========= Identity =========

    /** Page title/header (accepts “Shop” or “Shop - Assessments”). */
    public static final By SHOP_HEADER = By.xpath(
            "//*[self::h1 or self::h2]" +
                    "[normalize-space()='Shop' or normalize-space()='Shop - Assessments' or " +
                    " contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'shop')]"
    );

    /** Quick identity helper for tests. */
    public static boolean isCurrent(WebDriver driver) {
        return BasePage.isCurrentPage(driver, "/dashboard", SHOP_HEADER)
                || BasePage.isCurrentPage(driver, "/shop", SHOP_HEADER);
    }

    // ========= Product card finders (tolerant) =========

    /** Card container for a product by (partial) name, tolerant to TM/whitespace/casing. */
    private By productCardByName(String namePart) {
        String needle = namePart.toLowerCase();
        // find a heading-like element that contains the product name, then bubble to a container with a button
        return By.xpath(
                "//*[self::h1 or self::h2 or self::h3 or self::h4 or self::p or self::span]" +
                        "[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'" + needle + "')]" +
                        "/ancestor::*[self::article or self::section or self::div][.//button][1]"
        );
    }

    /** “Buy” button within a product card (accepts several variants). */
    private static final By BUY_BUTTON_IN_CARD = By.xpath(
            ".//*[self::button or self::a]" +
                    "[normalize-space()='Buy Now' or normalize-space()='BUY NOW' or " +
                    " contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'buy') or " +
                    " contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'add to cart')]"
    );

    /** Price element within a product card (very tolerant). Prefer `[data-test=price]` when available. */
    private static final By PRICE_IN_CARD = By.xpath(
            ".//*[@data-test='price' or @data-testid='price' or " +
                    "     contains(.,'$') or contains(.,'€') or contains(.,'£')]"
    );

    // Common product names used by tests
    private static final String NAME_TRUE_TILT = "True Tilt Personality Profile";
    private static final String NAME_AGILITY   = "Agility Growth Tracker";

    // ========= Load state =========

    @Override
    public ShopPage waitUntilLoaded() {
        wait.waitForDocumentReady();
        wait.waitForLoadersToDisappear();
        new WebDriverWait(driver, Duration.ofSeconds(12))
                .until(ExpectedConditions.visibilityOfElementLocated(SHOP_HEADER));
        return this;
    }

    public boolean isLoaded() {
        return isVisible(SHOP_HEADER);
    }

    // ========= Visibility helpers =========

    public boolean isTrueTiltVisible() {
        return isVisible(productCardByName(NAME_TRUE_TILT));
    }

    public boolean isAgilityGrowthVisible() {
        return isVisible(productCardByName(NAME_AGILITY));
    }

    // ========= Actions =========

    /** Click “Buy” on True Tilt Personality Profile™ and go to recipient selection. */
    public PurchaseRecipientSelectionPage clickBuyNowForTrueTilt() {
        return clickBuyInCardAndGo(NAME_TRUE_TILT);
    }

    /** Click “Buy” on Agility Growth Tracker and go to recipient selection. */
    public PurchaseRecipientSelectionPage clickBuyNowForAgilityGrowth() {
        return clickBuyInCardAndGo(NAME_AGILITY);
    }

    private PurchaseRecipientSelectionPage clickBuyInCardAndGo(String productNamePart) {
        By cardBy = productCardByName(productNamePart);

        WebElement card = new WebDriverWait(driver, Duration.ofSeconds(12))
                .until(ExpectedConditions.visibilityOfElementLocated(cardBy));

        // scope the “Buy” search to the card element
        WebElement buy = new WebDriverWait(driver, Duration.ofSeconds(6))
                .until(ExpectedConditions.elementToBeClickable(card.findElement(BUY_BUTTON_IN_CARD)));

        safeClick(buy);
        // purchase flow starts on the recipient selection step
        return new pages.Shop.PurchaseRecipientSelectionPage(driver).waitUntilLoaded();
    }

    // ========= Price getters =========

    public String getPriceTrueTilt() {
        return getPriceFromCard(NAME_TRUE_TILT);
    }

    public String getPriceAgilityGrowth() {
        return getPriceFromCard(NAME_AGILITY);
    }

    private String getPriceFromCard(String productNamePart) {
        By cardBy = productCardByName(productNamePart);
        WebElement card = new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.visibilityOfElementLocated(cardBy));

        // prefer data-test hooks when present
        try {
            WebElement price = card.findElement(PRICE_IN_CARD);
            if (price.isDisplayed()) return price.getText().trim();
        } catch (NoSuchElementException ignored) { /* fallback below */ }

        // fallback: look for a $-prefixed text node
        try {
            WebElement anyMoney = card.findElement(By.xpath(".//*[contains(normalize-space(.),'$')]"));
            return anyMoney.getText().trim();
        } catch (NoSuchElementException e) {
            return ""; // not fatal—some environments hide prices
        }
    }
}
