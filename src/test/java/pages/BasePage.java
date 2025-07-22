package pages;

import Utils.Config;
import Utils.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public abstract class BasePage {

    protected WebDriver driver;
    protected WaitUtils wait;

    public BasePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WaitUtils(driver, Config.getTimeout()); // default timeout
    }

    // =========================
    // 🔹 Common UI Interactions
    // =========================

    protected void click(By locator) {
        wait.waitForElementClickable(locator).click();
    }

    protected void type(By locator, String text) {
        WebElement element = wait.waitForElementVisible(locator);
        element.clear();
        element.sendKeys(text);
    }

    protected String getText(By locator) {
        return wait.waitForElementVisible(locator).getText();
    }

    protected boolean isVisible(By locator) {
        try {
            return wait.waitForElementVisible(locator).isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    protected boolean isClickable(By locator) {
        try {
            wait.waitForElementClickable(locator);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // =========================
    // 🔹 Wait Wrappers
    // =========================

    protected WebElement waitForElementVisible(By locator) {
        return wait.waitForElementVisible(locator);
    }

    protected WebElement waitForElementClickable(By locator) {
        return wait.waitForElementClickable(locator);
    }

    protected boolean waitForElementInvisible(By locator) {
        wait.waitForElementInvisible(locator);
        return true;
    }

    protected void waitForInvisibility(By locator) {
        wait.waitForElementInvisible(locator);
    }

    protected boolean waitForUrlContains(String partialUrl) {
        return wait.waitForUrlContains(partialUrl);
    }

    protected boolean waitForTitleContains(String partialTitle) {
        return wait.waitForTitleContains(partialTitle);
    }

    protected void waitForAttributeToContain(By locator, String attribute, String value) {
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(d -> d.findElement(locator).getAttribute(attribute).contains(value));
    }

}
