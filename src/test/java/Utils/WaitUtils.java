package Utils;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.openqa.selenium.support.ui.ExpectedConditions.*;

public class WaitUtils {

    private final WebDriver driver;
    private final WebDriverWait wait;
    private final Duration defaultTimeout;

    /** Single union selector for overlays/spinners/backdrops (faster than N separate waits). */
    private static final String LOADER_UNION_CSS =
            "[data-testid='loading'],[data-test='loading']," +
                    "[role='progressbar']," +
                    ".MuiBackdrop-root,.MuiCircularProgress-root," +
                    ".ant-spin,.ant-spin-spinning," +
                    ".overlay,.spinner,.backdrop,[aria-busy='true']";

    public WaitUtils(WebDriver driver, int timeoutSeconds) {
        this.driver = driver;
        this.defaultTimeout = Duration.ofSeconds(Math.max(3, timeoutSeconds));
        this.wait = baseWait(defaultTimeout);
    }

    private WebDriverWait baseWait(Duration timeout) {
        WebDriverWait w = new WebDriverWait(driver, timeout);
        w.pollingEvery(Duration.ofMillis(200));
        w.ignoring(StaleElementReferenceException.class)
                .ignoring(NoSuchElementException.class)
                .ignoring(ElementClickInterceptedException.class)
                .ignoring(JavascriptException.class);
        return w;
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
            return baseWait(timeout).until(condition);
        } catch (TimeoutException e) {
            throw new RuntimeException("❌ Timeout (" + timeout.toSeconds() + "s) waiting for condition: " + condition, e);
        }
    }

    /** Generic lambda-style until (handy for custom conditions). */
    public <T> T until(Function<WebDriver, T> condition, Duration timeout) {
        try {
            return baseWait(timeout).until(condition);
        } catch (TimeoutException e) {
            throw new RuntimeException("❌ Timeout (" + timeout.toSeconds() + "s) waiting for custom condition", e);
        }
    }

    // ---------- Visibility / Clickability ----------
    public WebElement waitForElementVisible(By locator) {
        return until(visibilityOfElementLocated(locator));
    }

    public WebElement waitForElementVisible(WebElement element) {
        return until(visibilityOf(element));
    }

    public List<WebElement> waitForAllVisible(By locator) {
        try {
            return wait.until(visibilityOfAllElementsLocatedBy(locator));
        } catch (TimeoutException e) {
            throw new RuntimeException("❌ Timeout: Elements not all visible: " + locator, e);
        }
    }

    /** Returns the first visible element among provided locators. */
    public WebElement waitForAnyVisible(By... locators) {
        return until(d -> {
            for (By by : locators) {
                for (WebElement el : d.findElements(by)) {
                    try {
                        if (el.isDisplayed() && el.getSize().height > 0 && el.getSize().width > 0) return el;
                    } catch (StaleElementReferenceException ignored) {}
                }
            }
            return null;
        }, defaultTimeout);
    }

    /** True/false wait (predicate style). */
    public boolean waitForTrue(Supplier<Boolean> condition) {
        return wait.until(dr -> Boolean.TRUE.equals(condition.get()));
    }

    public WebElement waitForElementClickable(By locator) { return until(elementToBeClickable(locator)); }
    public WebElement waitForElementClickable(WebElement element) { return until(elementToBeClickable(element)); }

    public boolean waitForElementInvisible(By locator) { return until(invisibilityOfElementLocated(locator)); }
    public boolean waitForElementInvisible(WebElement element) { return until(invisibilityOf(element)); }

    // ---------- Presence / Text / Attributes ----------
    public WebElement waitForPresence(By locator) { return until(presenceOfElementLocated(locator)); }

    public boolean waitForTextPresent(By locator, String text) {
        return until(textToBePresentInElementLocated(locator, text));
    }

    public boolean waitForAttributeContains(WebElement el, String attr, String value) {
        return until(attributeContains(el, attr, value));
    }

    public boolean waitForValueNotEmpty(WebElement el) {
        return until(d -> {
            try { String v = el.getAttribute("value"); return v != null && !v.isEmpty(); }
            catch (StaleElementReferenceException ignored) { return false; }
        });
    }

    // ---------- URL / Title ----------
    public boolean waitForUrlContains(String partialUrl) { return until(ExpectedConditions.urlContains(partialUrl)); }
    public boolean waitForTitleContains(String partialTitle) { return until(ExpectedConditions.titleContains(partialTitle)); }
    public boolean waitForUrlContains(String partialUrl, long timeoutSec) {
        return baseWait(Duration.ofSeconds(timeoutSec)).until(ExpectedConditions.urlContains(partialUrl));
    }

    // ---------- Page / DOM readiness ----------
    /** document.readyState === 'complete' (best-effort). */
    public void waitForDocumentReady() {
        try {
            baseWait(defaultTimeout).until(d ->
                    "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState"))
            );
        } catch (Exception ignored) {}
    }

    /** Unified loader/backdrop wait using a single OR selector. */
    public void waitForLoadersToDisappear() {
        try {
            baseWait(defaultTimeout).until(d -> {
                List<WebElement> overlays = d.findElements(By.cssSelector(LOADER_UNION_CSS));
                for (WebElement e : overlays) {
                    try {
                        if (e.isDisplayed() && e.getSize().height > 0 && e.getSize().width > 0) return false;
                    } catch (StaleElementReferenceException ignored) {}
                }
                return true;
            });
        } catch (Exception ignored) {}
    }

    /** Optional: wait until there are no running CSS/Web animations. */
    public void waitForAnimationsToFinish(Duration timeout) {
        try {
            baseWait(timeout).until(d -> {
                Object running = ((JavascriptExecutor) d).executeScript(
                        "try {" +
                                "  var a = (document.getAnimations ? document.getAnimations() : []);" +
                                "  return a.filter(x => x.playState === 'running').length;" +
                                "} catch(e){ return 0; }"
                );
                long n = (running instanceof Number) ? ((Number) running).longValue() : 0L;
                return n == 0L;
            });
        } catch (Exception ignored) {}
    }

    /**
     * “Network idle–ish”: document ready, loaders gone, no running animations,
     * and an optional window.__pendingRequests counter (if your app sets it).
     */
    public void waitForNetworkIdleLike(Duration timeout) {
        try {
            baseWait(timeout).until(d -> {
                JavascriptExecutor js = (JavascriptExecutor) d;

                // readyState
                String rs = String.valueOf(js.executeScript("return document.readyState"));
                if (!"complete".equals(rs)) return false;

                // overlays
                for (WebElement e : d.findElements(By.cssSelector(LOADER_UNION_CSS))) {
                    try { if (e.isDisplayed()) return false; } catch (StaleElementReferenceException ignored) {}
                }

                // animations
                Object anim = js.executeScript(
                        "try { var a=(document.getAnimations?document.getAnimations():[]);" +
                                "      return a.filter(x=>x.playState==='running').length; } catch(e){ return 0; }");
                long running = (anim instanceof Number) ? ((Number) anim).longValue() : 0L;
                if (running > 0) return false;

                // optional app counter
                Object inflight = js.executeScript("return window.__pendingRequests || 0;");
                long req = (inflight instanceof Number) ? ((Number) inflight).longValue() : 0L;
                return req == 0L;
            });
        } catch (Exception ignored) {}
    }

    /** (Optional) Install a tiny fetch/XMLHttpRequest counter; call once after login if desired. */
    public void installNetworkInstrumentation() {
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "if (!window.__pendingRequestsInstalled) {" +
                            "  window.__pendingRequestsInstalled = true;" +
                            "  window.__pendingRequests = 0;" +
                            "  (function(){ " +
                            "    var open = XMLHttpRequest.prototype.open, send = XMLHttpRequest.prototype.send;" +
                            "    XMLHttpRequest.prototype.open = function(){ this.__seleniumTracked = true; return open.apply(this, arguments); };" +
                            "    XMLHttpRequest.prototype.send = function(){ if (this.__seleniumTracked){ window.__pendingRequests++; this.addEventListener('loadend', function(){ window.__pendingRequests--; }); } return send.apply(this, arguments); };" +
                            "    if (window.fetch) {" +
                            "      var _fetch = window.fetch;" +
                            "      window.fetch = function(){ window.__pendingRequests++; return _fetch.apply(this, arguments).finally(function(){ window.__pendingRequests--; }); }" +
                            "    }" +
                            "  })();" +
                            "}"
            );
        } catch (Exception ignored) {}
    }

    // ---------- Utilities ----------
    public void pause(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    /** Run an action with a temporary timeout (does not mutate the default wait). */
    public <T> T withTimeout(Duration timeout, Supplier<T> action) {
        WebDriverWait temp = baseWait(timeout);
        try { return action.get(); }
        finally { /* no global state to restore */ }
    }

    // ---------- Frame switching ----------
    public WebDriver waitForFrameAndSwitch(By frameLocator) {
        return wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(frameLocator));
    }
    public void waitForFrameAndSwitch(WebElement frame) {
        baseWait(defaultTimeout).until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(frame));
    }
    public WebDriver waitForFrameAndSwitch(int index) {
        return wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(index));
    }

    // ---------- Static helpers (kept for compatibility) ----------
    public static boolean isVisible(WebDriver driver, By by, Duration max) {
        try {
            WebDriverWait w = new WebDriverWait(driver, max);
            w.pollingEvery(Duration.ofMillis(200));
            w.ignoring(StaleElementReferenceException.class).ignoring(NoSuchElementException.class);
            w.until(ExpectedConditions.visibilityOfElementLocated(by));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    public static void idle(WebDriver driver, Duration d) {
        try { Thread.sleep(d.toMillis()); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    public static WebElement waitForStripePaymentFrame(WebDriver driver, Duration timeout) {
        Wait<WebDriver> w = new FluentWait<>(driver)
                .withTimeout(timeout)
                .pollingEvery(Duration.ofMillis(200))
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class);

        return w.until(d -> {
            List<WebElement> frames = d.findElements(By.cssSelector("iframe"));
            for (WebElement f : frames) {
                String src = safeAttr(f, "src");
                if (src == null) continue;
                boolean looksLikePayment =
                        (src.contains("elements-inner-payment") || src.contains("elements-inner-card"))
                                && !src.contains("express-checkout");
                if (!looksLikePayment) continue;
                try {
                    Long h = (Long) ((JavascriptExecutor) d)
                            .executeScript("return Math.max(arguments[0].clientHeight, arguments[0].offsetHeight);", f);
                    if (h != null && h > 10) return f;
                } catch (Exception ignored) {}
            }
            ((JavascriptExecutor) d).executeScript("window.scrollBy(0, 300);");
            return null;
        });
    }

    private static String safeAttr(WebElement el, String name) {
        try { return el.getAttribute(name); } catch (Exception e) { return null; }
    }

    public static void waitForDocumentReady(WebDriver driver) {
        new WebDriverWait(driver, Duration.ofSeconds(15))
                .until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
    }

    /** Legacy; prefer {@link #waitForAnimationsToFinish(Duration)}. */
    @Deprecated
    public void tinyUiSettled(Duration max) {
        waitForAnimationsToFinish(max);
    }

    // --- Invisibility with default timeout (compat) ---
    public boolean waitForInvisibility(By locator) {
        try {
            return baseWait(defaultTimeout).until(ExpectedConditions.invisibilityOfElementLocated(locator));
        } catch (TimeoutException e) {
            return false;
        }
    }










    // Common loader/selectors you’ll likely see across pages.
    private static final List<By> DEFAULT_LOADERS = Arrays.asList(
            By.cssSelector("[data-testid='loading'], [data-testid='loader']"),
            By.cssSelector(".loading, .loader, .spinner, .lds-ring, .lds-ellipsis"),
            By.cssSelector("[role='progressbar']"),
            By.cssSelector(".overlay, .backdrop, .rdp-overlay, .ant-spin, .chakra-progress__indicator")
    );

    public static void waitForLoadersToDisappear(WebDriver driver) {
        waitForLoadersToDisappear(driver, Duration.ofSeconds(10));
    }

    public static void waitForLoadersToDisappear(WebDriver driver, Duration timeout) {
        WebDriverWait wait = new WebDriverWait(driver, timeout);
        wait.ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class)
                .until(allGoneOrHidden(DEFAULT_LOADERS));
    }

    private static ExpectedCondition<Boolean> allGoneOrHidden(List<By> locators) {
        return drv -> {
            for (By by : locators) {
                for (WebElement el : drv.findElements(by)) {
                    try {
                        if (el.isDisplayed()) return false;
                    } catch (StaleElementReferenceException ignored) { /* treat as gone */ }
                }
            }
            return true;
        };
    }


    // WaitUtils.java
    public static WebElement waitExactText(WebDriver d, By scope, String text, Duration t) {
        String xp = ".//*[self::p or self::span or self::div or self::label or self::strong]"
                + "[normalize-space(.)=" + org.openqa.selenium.By.xpath("'" + text + "'").toString().replace("By.xpath: ", "") + "]";
        return new WebDriverWait(d, t).until(w -> w.findElement(scope).findElement(By.xpath(xp)));
    }



}
