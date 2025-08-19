package Utils;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static org.openqa.selenium.support.ui.ExpectedConditions.*;

public class WaitUtils {


    private final WebDriver driver;
    private final WebDriverWait wait;
    private final Duration defaultTimeout;

    public WaitUtils(WebDriver driver, int timeoutSeconds) {
        this.driver = driver;
        this.defaultTimeout = Duration.ofSeconds(timeoutSeconds);
        this.wait = new WebDriverWait(driver, defaultTimeout);
        this.wait.ignoring(StaleElementReferenceException.class)
                .ignoring(NoSuchElementException.class);
    }

    // ---------- Core pass-through ----------
    public <T> T until(ExpectedCondition<T> condition) {
        try {
            return wait.until(condition);
        } catch (TimeoutException e) {
            throw new RuntimeException("❌ Timeout waiting for condition: " + condition, e);
        }
    }

    public <T> T until(ExpectedCondition<T> condition, Duration timeout) {
        try {
            return new WebDriverWait(driver, timeout)
                    .ignoring(StaleElementReferenceException.class)
                    .ignoring(NoSuchElementException.class)
                    .until(condition);
        } catch (TimeoutException e) {
            throw new RuntimeException("❌ Timeout (" + timeout.toSeconds() + "s) waiting for condition: " + condition, e);
        }
    }

    // ---------- Visibility / Clickability ----------
    public WebElement waitForElementVisible(By locator) {
        return until(visibilityOfElementLocated(locator));
    }

    public WebElement waitForElementVisible(WebElement element) {
        return until(visibilityOf(element));
    }

    public java.util.List<WebElement> waitForAllVisible(By locator) {
        try {
            return wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(locator));
        } catch (TimeoutException e) {
            throw new RuntimeException("❌ Timeout: Elements not all visible: " + locator, e);
        }
    }

    /**
     * Generic true/false wait if you prefer a predicate style
     */
    public boolean waitForTrue(java.util.function.Supplier<Boolean> condition) {
        return wait.until(driver -> Boolean.TRUE.equals(condition.get()));
    }


    public WebElement waitForElementClickable(By locator) {
        return until(elementToBeClickable(locator));
    }

    public WebElement waitForElementClickable(WebElement element) {
        return until(elementToBeClickable(element));
    }

    public boolean waitForElementInvisible(By locator) {
        return until(invisibilityOfElementLocated(locator));
    }

    public boolean waitForElementInvisible(WebElement element) {
        return until(invisibilityOf(element));
    }

    // ---------- Presence / Text / Attributes ----------
    public WebElement waitForPresence(By locator) {
        return until(presenceOfElementLocated(locator));
    }

    public boolean waitForTextPresent(By locator, String text) {
        return until(textToBePresentInElementLocated(locator, text));
    }

    public boolean waitForAttributeContains(WebElement el, String attr, String value) {
        return until(attributeContains(el, attr, value));
    }

    public boolean waitForValueNotEmpty(WebElement el) {
        return until(driver -> {
            try {
                String val = el.getAttribute("value");
                return val != null && !val.isEmpty();
            } catch (StaleElementReferenceException ignored) {
                return false;
            }
        });
    }

    // ---------- URL / Title ----------
    public boolean waitForUrlContains(String partialUrl) {
        return until(ExpectedConditions.urlContains(partialUrl));
    }

    public boolean waitForTitleContains(String partialTitle) {
        return until(ExpectedConditions.titleContains(partialTitle));
    }

    // ---------- Any-of helper ----------

    /**
     * Returns the first visible element among the provided locators.
     */
    public WebElement waitForAnyVisible(By... locators) {
        return until(driver -> {
            for (By by : locators) {
                List<WebElement> els = driver.findElements(by);
                for (WebElement el : els) {
                    try {
                        if (el.isDisplayed() && el.getSize().height > 0 && el.getSize().width > 0) return el;
                    } catch (StaleElementReferenceException ignored) {
                    }
                }
            }
            return null;
        });
    }

    // ---------- Page / DOM readiness ----------
    public void waitForDocumentReady() {
        until(driver -> "complete".equals(
                ((JavascriptExecutor) driver).executeScript("return document.readyState")));
    }

    /**
     * Best-effort small idle wait for reactive UIs.
     */
    public void tinyUiSettled(Duration max) {
        until(driver -> {
            Long calls = (Long) ((JavascriptExecutor) driver).executeScript(
                    "return (window.__seleniumTick || 0);");
            ((JavascriptExecutor) driver).executeScript(
                    "window.__seleniumTick = (window.__seleniumTick||0) + 1;");
            return calls != null && calls > 0;
        }, max);
    }

    // ---------- Utilities ----------
    public void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Temporarily use a different timeout just for the supplied action.
     */
    public <T> T withTimeout(Duration timeout, java.util.function.Supplier<T> action) {
        WebDriverWait temp = new WebDriverWait(driver, timeout);
        temp.ignoring(StaleElementReferenceException.class)
                .ignoring(NoSuchElementException.class);
        try {
            return action.get();
        } finally {
            // no global state to restore; 'wait' stays as default instance
        }
    }


    // ---------- Frame switching ----------

    // Switch by locator (most common)
    public WebDriver waitForFrameAndSwitch(By frameLocator) {
        return wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(frameLocator));
    }

    // Switch by WebElement you already found
    public void waitForFrameAndSwitch(WebElement frame) {
        new WebDriverWait(driver, defaultTimeout)
                .until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(frame));
    }

    // Switch by index (handy fallback)
    public WebDriver waitForFrameAndSwitch(int index) {
        return wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(index));
    }


    public static boolean isVisible(WebDriver driver, By by, Duration max) {
        try {
            new WebDriverWait(driver, max)
                    .ignoring(StaleElementReferenceException.class)
                    .until(ExpectedConditions.visibilityOfElementLocated(by));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    public static void idle(WebDriver driver, Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException ignored) {
        }
    }

    public static WebElement waitForStripePaymentFrame(WebDriver driver, Duration timeout) {
        Wait<WebDriver> wait = new FluentWait<>(driver)
                .withTimeout(timeout)
                .pollingEvery(Duration.ofMillis(200))
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class);

        return wait.until(d -> {
            List<WebElement> frames = d.findElements(By.cssSelector("iframe"));
            for (WebElement f : frames) {
                String src = safeAttr(f, "src");
                if (src == null) continue;

                // Buscamos EL Payment Element, no los express/hcaptcha/controller
                boolean looksLikePayment =
                        (src.contains("elements-inner-payment") || src.contains("elements-inner-card"))
                                && !src.contains("express-checkout");

                if (!looksLikePayment) continue;

                // Confirmar que tenga altura visible (>10px)
                Long h = (Long) ((JavascriptExecutor) d)
                        .executeScript("return Math.max(arguments[0].clientHeight, arguments[0].offsetHeight);", f);
                if (h != null && h > 10) {
                    return f;
                }
            }

            // Scroll suave por si Stripe hace lazy-load del iframe al entrar en viewport
            ((JavascriptExecutor) d).executeScript("window.scrollBy(0, 300);");
            return null;
        });
    }

    private static String safeAttr(WebElement el, String name) {
        try {
            return el.getAttribute(name);
        } catch (Exception e) {
            return null;
        }
    }

    public static void waitForDocumentReady(WebDriver driver) {
        new WebDriverWait(driver, Duration.ofSeconds(15)).until(d ->
                "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState"))
        );
    }





    /**
     * Polls until the condition is true or the timeout expires.
     *
     * @param timeout   how long to keep trying
     * @param interval  how often to retry
     * @param condition the condition to check
     * @return true if condition became true within timeout, false otherwise
     */
    public static boolean pollFor(Duration timeout, Duration interval, Supplier<Boolean> condition) {
        long end = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < end) {
            try {
                if (Boolean.TRUE.equals(condition.get())) {
                    return true; // ✅ Success
                }
            } catch (Exception ignored) {
                // ignore errors during condition checks (e.g., stale element)
            }

            try {
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("❌ Polling interrupted", e);
            }
        }
        return false; // ⏰ Timeout
    }



}
