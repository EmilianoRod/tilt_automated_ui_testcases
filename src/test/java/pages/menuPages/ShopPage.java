package pages.menuPages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import pages.BasePage;

public class ShopPage extends BasePage {

    public ShopPage(WebDriver driver) {
        super(driver);
    }

    // Locators
//    private final By pageHeader = By.xpath("//h2[contains(text(),'Shop - Assessments')]");
    private final By pageTitle = By.xpath("//h1");


    // Product cards
    private final By trueTiltCard = By.xpath("");
    private final By agilityGrowthCard = By.xpath("/");

    // Buy Now buttons
    private final By buyNowTrueTilt = By.xpath("//h3[contains(text(), 'True Tilt Personality Profile™')]/following::button[contains(text(), 'BUY NOW')][1]");
    private final By buyNowAgilityGrowth = By.xpath("//h3[contains(text(), 'Agility Growth Tracker')]/following::button[contains(text(), 'BUY NOW')][1]");

    // Price tags
    private final By priceTrueTilt = By.xpath("");
    private final By priceAgilityGrowth = By.xpath("");


    // Actions
    public boolean isLoaded() {
        return isVisible(pageTitle);
    }

    public boolean isTrueTiltVisible() {
        return isVisible(trueTiltCard);
    }

    public boolean isAgilityGrowthVisible() {
        return isVisible(agilityGrowthCard);
    }

    public void clickBuyNowTrueTilt() {
        click(buyNowTrueTilt);
    }

    public void clickBuyNowAgilityGrowth() {
        click(buyNowAgilityGrowth);
    }

    public String getPriceTrueTilt() {
        return getText(priceTrueTilt);
    }

    public String getPriceAgilityGrowth() {
        return getText(priceAgilityGrowth);
    }




}
