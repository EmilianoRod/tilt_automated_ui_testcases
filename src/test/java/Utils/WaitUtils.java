package Utils;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class WaitUtils {

    private WebDriver driver;
    private WebDriverWait wait;

    public WaitUtils(WebDriver driver, int timeoutSeconds) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
    }


    public WebElement waitForElementVisible(By locator) {
        try {
            return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
        } catch (TimeoutException e) {
            throw new RuntimeException("❌ Timeout: Element not visible: " + locator, e);
        }
    }

    public WebElement waitForElementClickable(By locator) {
        try {
            return wait.until(ExpectedConditions.elementToBeClickable(locator));
        } catch (TimeoutException e) {
            throw new RuntimeException("❌ Timeout: Element not clickable: " + locator, e);
        }
    }

    public boolean waitForUrlContains(String partialUrl) {
        return wait.until(ExpectedConditions.urlContains(partialUrl));
    }

    public boolean waitForTitleContains(String partialTitle) {
        return wait.until(ExpectedConditions.titleContains(partialTitle));
    }

    public boolean waitForElementInvisible(By locator) {
        return wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }

}
