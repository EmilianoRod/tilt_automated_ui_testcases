package pages;

import Utils.Config;
import Utils.WaitUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.io.FileHandler;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

public abstract class BasePage {


    protected WebDriver driver;
    protected WaitUtils wait;

    public BasePage(WebDriver driver) {
        if (driver == null) {
            throw new IllegalArgumentException("❌ WebDriver is NULL for " + this.getClass().getSimpleName());
        }
        this.driver = driver;
        this.wait = new WaitUtils(driver, Config.getTimeout());
    }

    // =========================
    // UI Interactions
    // =========================

/*    protected void click(By locator) {
        WebElement element = wait.waitForElementClickable(locator);
        scrollIntoView(element);
        try {
            element.click();
        } catch (ElementClickInterceptedException e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        }
    }*/

    protected void click(By locator) {
        int attempts = 0;
        while (attempts < 2) {
            try {
                WebElement element = wait.waitForElementClickable(locator);
                scrollIntoView(element);
                element.click();
                return;
            } catch (ElementClickInterceptedException | StaleElementReferenceException e) {
                attempts++;
                           if (attempts == 2) {
                                    // last-chance JS click
                            WebElement element = driver.findElement(locator);
                               ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
                                 return;
                              }
            }
        }
    }

    protected String getText(By locator) {
        return wait.waitForElementVisible(locator).getText();
    }

    protected boolean isVisible(By locator) {
        try {
            return wait.waitForElementVisible(locator).isDisplayed();
        } catch (TimeoutException e) {
            return false;
        }
    }

    protected void type(By locator, String text) {
        WebElement element = wait.waitForElementVisible(locator);

           // cross‑platform select-all (Control on Win/Linux, Command on macOS)
                    Keys mod = System.getProperty("os.name").toLowerCase().contains("mac") ? Keys.COMMAND : Keys.CONTROL;
           element.sendKeys(Keys.chord(mod, "a"), Keys.DELETE);
           element.sendKeys(text);
    }

    protected boolean isClickable(By locator) {
        try {
            wait.waitForElementClickable(locator);
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    protected void scrollIntoView(WebElement element) {
            ((JavascriptExecutor) driver).executeScript(
                        "arguments[0].scrollIntoView({block:'center', inline:'nearest'});",
                        element
                            );
    }


    // =========================
    // 🔹 Screenshot Utility
    // =========================
    protected void takeScreenshot(String name) {
        File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        try {
            FileHandler.createDir(new File("screenshots"));
            FileHandler.copy(screenshot, new File("screenshots/" + name + ".png"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save screenshot: " + name, e);
        }
    }



    // =========================
    // Wait Wrappers
    // =========================

    protected WebElement waitForElementVisible(By locator) {
        return wait.waitForElementVisible(locator);
    }

    protected WebElement waitForElementClickable(By locator) {
        return wait.waitForElementClickable(locator);
    }

    protected boolean waitForElementInvisible(By locator) {
        return wait.waitForElementInvisible(locator);
    }

    protected boolean waitForUrlContains(String partialUrl) {
        return wait.waitForUrlContains(partialUrl);
    }

    protected boolean waitForTitleContains(String partialTitle) {
        return wait.waitForTitleContains(partialTitle);
    }

    protected void waitForAttributeToContain(By locator, String attribute, String value) {
           wait.until(ExpectedConditions.attributeContains(locator, attribute, value));
        ;
    }

}
