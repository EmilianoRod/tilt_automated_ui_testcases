package pages.Shop.Stripe;

import Utils.WaitUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.BasePage;

import java.time.Duration;
import java.util.List;

public class StripeCheckoutPage extends BasePage {

    public StripeCheckoutPage(WebDriver driver) { super(driver); }

    // Local wait just for Stripe flows
    private WebDriverWait stripeWait() { return new WebDriverWait(driver, Duration.ofSeconds(30)); }

    // --- Elements (on-site) iframe locators ---
    private final By cardNumberFrame = By.cssSelector("iframe[title='Secure card number input']");
    private final By expDateFrame    = By.cssSelector("iframe[title='Secure expiration date input']");
    private final By cvcFrame        = By.cssSelector("iframe[title='Secure CVC input']");
    private final By postalFrame     = By.cssSelector("iframe[title='Secure postal code input']");
    private final By anyStripeFrame  = By.cssSelector(
            "iframe[src*='js.stripe.com'],iframe[src*='pay.stripe.com'],iframe[src*='m.stripe.com'],iframe[name^='__privateStripeFrame']"
    );
    private final By inputWithinFrame = By.cssSelector("input[name], input[autocomplete]");

    private final By payButton = By.xpath(
            "//button[@type='submit' or .//span[contains(normalize-space(.),'Pay')] or contains(normalize-space(.),'Pay')]"
    );
    private final By blockingOverlay = By.xpath(
            "//*[self::div or self::span][contains(@class,'loading') or contains(@class,'spinner') or contains(@class,'overlay') or @role='progressbar']"
    );

    // ===================== PUBLIC API =====================

    /** One entry point for tests. Routes to Hosted vs Elements automatically. */
    public StripeCheckoutPage payWithTestCard(String email, String number, String expMmYy, String cvc, String zip) {
        driver.switchTo().defaultContent();
        System.out.println("[DEBUG] Starting payWithTestCard...");

        // 1) Email
        By emailBox = By.cssSelector("input[type='email'], input[name='email'], input[autocomplete='email']");
        WebElement emailEl = new WebDriverWait(driver, Duration.ofSeconds(20))
                .until(ExpectedConditions.elementToBeClickable(emailBox));
        System.out.println("[DEBUG] Found email field: " + emailEl);
        emailEl.click();
        emailEl.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE, email, Keys.ENTER);
        System.out.println("[DEBUG] Typed email and pressed ENTER");

        // 2) Wait for Stripe iframes
        new WebDriverWait(driver, Duration.ofSeconds(15)).until(d ->
                ((Number) ((JavascriptExecutor) d)
                        .executeScript("return document.querySelectorAll('iframe').length")).intValue() >= 1);
        System.out.println("[DEBUG] Iframes detected after email step");

        ensureCardSectionOpen();
        System.out.println("[DEBUG] Card section ensured open");

        // 3) Find the Payment Element iframe
// 3) Find the Payment Element iframe deeply (can live inside stripe-origin-frame)
        WebElement cardFrame = findPaymentIframeDeep(Duration.ofSeconds(12));
        if (cardFrame == null) {
            throw new TimeoutException("Stripe card iframe not found (deep search).");
        }
        System.out.println("[DEBUG] Found card iframe (deep): title=" + cardFrame.getAttribute("title")
                + ", src=" + cardFrame.getAttribute("src"));

        System.out.println("[DEBUG] Found card iframe: title=" + cardFrame.getAttribute("title") + ", src=" + cardFrame.getAttribute("src"));

        // 4) Enter iframe and locate first input
        driver.switchTo().frame(cardFrame);
        By anyCardInput = By.cssSelector("input[name='cardnumber'], input[autocomplete='cc-number'], .InputElement");
        WebElement numberInput = new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.elementToBeClickable(anyCardInput));
        System.out.println("[DEBUG] Found card number input");

        // 5) Fill fields with step-by-step debug
        Actions a = new Actions(driver);
        numberInput.click();
        System.out.println("[DEBUG] Typing card number: " + number);
        a.sendKeys(number.replace(" ", "")).perform();

        a.sendKeys(Keys.TAB).perform();
        System.out.println("[DEBUG] TAB -> expiry field");
        quietSleep(150);

        a.sendKeys(expMmYy).perform();
        System.out.println("[DEBUG] Typed expiry: " + expMmYy);

        a.sendKeys(Keys.TAB).perform();
        System.out.println("[DEBUG] TAB -> CVC field");
        quietSleep(150);

        a.sendKeys(cvc).perform();
        System.out.println("[DEBUG] Typed CVC: " + cvc);

        if (zip != null && !zip.isBlank()) {
            a.sendKeys(Keys.TAB).perform();
            System.out.println("[DEBUG] TAB -> ZIP field");
            quietSleep(100);
            a.sendKeys(zip).perform();
            System.out.println("[DEBUG] Typed ZIP: " + zip);
        }

        // 6) Leave iframe and click Pay
        driver.switchTo().defaultContent();
        try { wait.waitForElementInvisible(blockingOverlay); } catch (Exception ignored) {}
        WebElement btn = wait.waitForElementClickable(payButton);
        scrollIntoViewCenter(btn);
        System.out.println("[DEBUG] Clicking Pay button...");
        try { btn.click(); } catch (ElementClickInterceptedException e) { jsClick(btn); }

        return complete3DSIfPresent();
    }


    /** Hosted Checkout: pure keyboard + a single click on the iframe. No frame switching. */
    public StripeCheckoutPage payHostedLikeAHumanLogged(
            String email, String number, String expMmYy, String cvc, String zipOrNull) {

        driver.switchTo().defaultContent();
        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(30));
        Actions keys = new Actions(driver);

        System.out.println("[DEBUG] Step 1 – Typing email...");
        WebElement emailEl = w.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("input[type='email'], input[name='email'], input[autocomplete='email']")));
        emailEl.click();
        emailEl.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE, email);
        emailEl.sendKeys(Keys.ENTER);

        System.out.println("[DEBUG] Step 2 – Waiting for Stripe iframes...");
        w.until(d -> ((Number)((JavascriptExecutor)d)
                .executeScript("return document.querySelectorAll('iframe').length")).intValue() >= 1);

        ensureCardSectionOpen();
        System.out.println("[DEBUG] Step 3 – Card section ensured open");

        System.out.println("[DEBUG] Step 4 – Tabbing to payment iframe...");
        WebElement frame = null;
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end) {
            ((JavascriptExecutor)driver).executeScript("document.body.focus()");
            keys.sendKeys(Keys.TAB).pause(Duration.ofMillis(90)).perform();

            frame = (WebElement)((JavascriptExecutor)driver).executeScript(
                    "return (document.activeElement && document.activeElement.tagName==='IFRAME') ? document.activeElement : null;");
            if (frame != null) {
                String src = String.valueOf(frame.getAttribute("src")).toLowerCase();
                String title = String.valueOf(frame.getAttribute("title")).toLowerCase();
                System.out.printf("[DEBUG]   Focused iframe: title='%s', src='%s'%n", title, src);
                if (!src.contains("express-checkout") && !src.contains("hcaptcha") && !src.contains("controller")) break;
                frame = null;
            }
        }
        if (frame == null) throw new TimeoutException("Could not focus Stripe payment iframe via TAB.");

        System.out.println("[DEBUG] Step 5 – Clicking iframe to give it focus");
        new Actions(driver).moveToElement(frame).click().pause(Duration.ofMillis(150)).perform();

        System.out.println("[DEBUG] Step 6 – Typing card data...");
        keys.
                sendKeys(number.replaceAll("\\s+", "")) // card number without spaces
                .pause(Duration.ofMillis(150))
                .sendKeys(Keys.TAB)
                .pause(Duration.ofMillis(120))
                .sendKeys(expMmYy)
                .pause(Duration.ofMillis(120))
                .sendKeys(Keys.TAB)
                .pause(Duration.ofMillis(120))
                .sendKeys(cvc)
                .perform();

        if (zipOrNull != null && !zipOrNull.isBlank()) {
            System.out.println("[DEBUG] Step 7 – Typing ZIP...");
            keys.pause(Duration.ofMillis(120)).sendKeys(Keys.TAB).pause(Duration.ofMillis(100)).sendKeys(zipOrNull).perform();
        }

        System.out.println("[DEBUG] Step 8 – Clicking Pay...");
        try { wait.waitForElementInvisible(blockingOverlay); } catch (Exception ignored) {}
        WebElement btn = wait.waitForElementClickable(payButton);
        scrollIntoViewCenter(btn);
        try { btn.click(); } catch (ElementClickInterceptedException e) { jsClick(btn); }

        System.out.println("[DEBUG] Payment flow complete");
        return this;
    }

    public StripeCheckoutPage payHostedNoTab(String email, String number, String expMmYy, String cvc, String zipOrNull) {
        driver.switchTo().defaultContent();
        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(30));

        System.out.println("[DEBUG] H0 – readyState=" + ((JavascriptExecutor)driver).executeScript("return document.readyState"));

        // 1) Email + ENTER to reveal Payment Element
        By emailSel = By.cssSelector("input[type='email'], input[name='email'], input[autocomplete='email']");
        WebElement emailEl = w.until(ExpectedConditions.elementToBeClickable(emailSel));
        scrollIntoViewCenter(emailEl);
        emailEl.click();
        selectAllAndClear(emailEl);
        emailEl.sendKeys(email, Keys.ENTER);
        System.out.println("[DEBUG] H1 – Email typed + ENTER");

        // 2) Wait for iframes to appear
        w.until(d -> ((Number)((JavascriptExecutor)d)
                .executeScript("return document.querySelectorAll('iframe').length")).intValue() > 0);
        debugDumpIframes("after ENTER");

        // 3) Open card tab if present
        ensureCardSectionOpen();
        System.out.println("[DEBUG] H2 – Card section ensured open");

        // 4) Try SPLIT iframes first (most reliable when present)
        if (fillUsingSplitFrames(number, expMmYy, cvc, zipOrNull)) {
            System.out.println("[DEBUG] H3 – Filled via split iframes");
            driver.switchTo().defaultContent();
            return this;
        }

        // 5) Fall back to UNIFIED Payment Element (single iframe with all fields)
        if (fillUsingUnifiedIframe(number, expMmYy, cvc, zipOrNull)) {
            System.out.println("[DEBUG] H4 – Filled via unified iframe");
            driver.switchTo().defaultContent();
            return this;
        }

        // 6) Nothing matched → hard fail with diagnostics
        debugDumpIframes("failure – no usable payment iframe found");
        throw new TimeoutException("Stripe payment fields not found (split nor unified).");
    }

// ====== IMPLEMENTATIONS ======

    private boolean fillUsingSplitFrames(String number, String expMmYy, String cvc, String zipOrNull) {
        driver.switchTo().defaultContent();

        // exact titles Stripe uses for split fields
        By cardNumberFrame = By.cssSelector("iframe[title='Secure card number input']");
        By expDateFrame    = By.cssSelector("iframe[title='Secure expiration date input']");
        By cvcFrame        = By.cssSelector("iframe[title='Secure CVC input']");
        By postalFrame     = By.cssSelector("iframe[title='Secure postal code input']");

        List<WebElement> numFrames = driver.findElements(cardNumberFrame);
        List<WebElement> expFrames = driver.findElements(expDateFrame);
        List<WebElement> cvcFrames = driver.findElements(cvcFrame);

        if (numFrames.isEmpty() || expFrames.isEmpty() || cvcFrames.isEmpty()) {
            System.out.println("[DEBUG] S0 – split frames not all present");
            return false; // not in split-frame mode
        }

        System.out.println("[DEBUG] S1 – split frames detected");

        // Card number
        if (!typeInFrame(numFrames.get(0),
                By.cssSelector("input[name='cardnumber'], input[autocomplete='cc-number'], .InputElement"),
                number.replaceAll("\\s+",""), 15)) return false;

        // Expiry
        if (!typeInFrame(expFrames.get(0),
                By.cssSelector("input[autocomplete='cc-exp'], input[name='exp-date'], .InputElement"),
                expMmYy, 3)) return false;

        // CVC
        if (!typeInFrame(cvcFrames.get(0),
                By.cssSelector("input[autocomplete='cc-csc'], input[name='cvc'], .InputElement"),
                cvc, 2)) return false;

        // ZIP (optional)
        if (zipOrNull != null && !zipOrNull.isBlank()) {
            List<WebElement> zipFrames = driver.findElements(postalFrame);
            if (!zipFrames.isEmpty() && zipFrames.get(0).isDisplayed()) {
                typeInFrame(zipFrames.get(0),
                        By.cssSelector("input[autocomplete*='postal'], input[name*='postal'], .InputElement"),
                        zipOrNull, 2);
            }
        }
        driver.switchTo().defaultContent();
        return true;
    }

    private boolean fillUsingUnifiedIframe(String number, String expMmYy, String cvc, String zipOrNull) {
        driver.switchTo().defaultContent();

        // Find the single Payment Element iframe (skip express/controller/hcaptcha)
        WebElement frame = findUnifiedPaymentIframe(Duration.ofSeconds(12));
        if (frame == null) {
            System.out.println("[DEBUG] U0 – unified iframe not found");
            return false;
        }
        System.out.println("[DEBUG] U1 – unified iframe = title='" + frame.getAttribute("title") + "' src='" + frame.getAttribute("src") + "'");

        scrollIntoViewCenter(frame);
        try { new Actions(driver).moveToElement(frame).pause(Duration.ofMillis(80)).perform(); } catch (Exception ignore) {}

        driver.switchTo().frame(frame);

        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(20));
        By numberSel = By.cssSelector("[data-elements-stable-field-name='cardNumber'], input[name='cardnumber'], input[autocomplete='cc-number'], .InputElement");
        By expSel    = By.cssSelector("[data-elements-stable-field-name='cardExpiry'], input[autocomplete='cc-exp'], input[name='exp-date'], .InputElement");
        By cvcSel    = By.cssSelector("[data-elements-stable-field-name='cardCvc'], input[autocomplete='cc-csc'], input[name='cvc'], .InputElement");
        By zipSel    = By.cssSelector("[data-elements-stable-field-name='postalCode'], input[autocomplete*='postal'], input[name*='postal'], .InputElement");

        // Card number
        WebElement numberInput = w.until(ExpectedConditions.elementToBeClickable(numberSel));
        focus(numberInput);
        if (!typeAndVerify(numberInput, number.replaceAll("\\s+",""), 15)) {
            System.out.println("[DEBUG] U2 – Robot fallback: card number");
            typeWithRobot(number.replaceAll("\\s+",""));
        }

        // Expiry
        numberInput.sendKeys(Keys.TAB);
        quietSleep(120);
        WebElement expInput = w.until(ExpectedConditions.elementToBeClickable(expSel));
        focus(expInput);
        if (!typeAndVerify(expInput, expMmYy, 3)) {
            System.out.println("[DEBUG] U3 – Robot fallback: expiry");
            typeWithRobot(expMmYy);
        }

        // CVC
        expInput.sendKeys(Keys.TAB);
        quietSleep(120);
        WebElement cvcInput = w.until(ExpectedConditions.elementToBeClickable(cvcSel));
        focus(cvcInput);
        if (!typeAndVerify(cvcInput, cvc, 2)) {
            System.out.println("[DEBUG] U4 – Robot fallback: cvc");
            typeWithRobot(cvc);
        }

        // ZIP (optional)
        if (zipOrNull != null && !zipOrNull.isBlank()) {
            cvcInput.sendKeys(Keys.TAB);
            quietSleep(120);
            List<WebElement> zips = driver.findElements(zipSel);
            if (!zips.isEmpty() && zips.get(0).isDisplayed()) {
                WebElement zipInput = zips.get(0);
                focus(zipInput);
                if (!typeAndVerify(zipInput, zipOrNull, 2)) {
                    System.out.println("[DEBUG] U5 – Robot fallback: zip");
                    typeWithRobot(zipOrNull);
                }
            } else {
                System.out.println("[DEBUG] U5 – ZIP not present (skipped)");
            }
        }

        driver.switchTo().defaultContent();
        return true;
    }

    // Find the unified Payment Element iframe by checking inside each visible iframe for card inputs
    private WebElement findUnifiedPaymentIframe(Duration timeout) {
        long end = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < end) {
            driver.switchTo().defaultContent();
            List<WebElement> iframes = driver.findElements(By.cssSelector("iframe"));
            for (WebElement f : iframes) {
                if (!f.isDisplayed()) continue;
                String src = String.valueOf(f.getAttribute("src")).toLowerCase();
                String title = String.valueOf(f.getAttribute("title")).toLowerCase();
                if (src.contains("express-checkout") || src.contains("controller") || src.contains("hcaptcha")) continue;

                try {
                    scrollIntoViewCenter(f);
                    driver.switchTo().frame(f);
                    boolean hasCard = !driver.findElements(By.cssSelector(
                            "[data-elements-stable-field-name='cardNumber'], input[name='cardnumber'], input[autocomplete='cc-number'], .InputElement"
                    )).isEmpty();
                    driver.switchTo().defaultContent();
                    if (hasCard) return f;
                } catch (Exception e) {
                    driver.switchTo().defaultContent();
                }
            }
            quietSleep(150);
        }
        return null;
    }

    // Common: switch into given frame and type, with verification+fallback
    private boolean typeInFrame(WebElement frame, By inputSel, String text, int minDelta) {
        try {
            scrollIntoViewCenter(frame);
            driver.switchTo().frame(frame);
            WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(15));
            WebElement input = w.until(ExpectedConditions.elementToBeClickable(inputSel));
            focus(input);
            if (!typeAndVerify(input, text, minDelta)) {
                System.out.println("[DEBUG] F – Robot fallback for frame text");
                typeWithRobot(text);
            }
            driver.switchTo().defaultContent();
            return true;
        } catch (Exception e) {
            System.out.println("[DEBUG] F – typeInFrame exception: " + e);
            driver.switchTo().defaultContent();
            return false;
        }
    }

    private void focus(WebElement el) {
        try {
            scrollIntoViewCenter(el);
            el.click();
        } catch (Exception ignore) {
            ((JavascriptExecutor)driver).executeScript("arguments[0].focus();", el);
        }
    }

    // Select-all + clear works on Mac/Win
    private void selectAllAndClear(WebElement el) {
        try { el.sendKeys(Keys.chord(Keys.COMMAND, "a")); } catch (Exception ignore) {}
        try { el.sendKeys(Keys.chord(Keys.CONTROL, "a")); } catch (Exception ignore) {}
        el.sendKeys(Keys.DELETE);
    }

    // Slow typing with verification (returns true if value length increased sensibly)
    private boolean typeAndVerify(WebElement input, String text, int minimumDelta) {
        String before = safeValue(input); int start = before == null ? 0 : before.length();
        for (char ch : text.toCharArray()) { input.sendKeys(Character.toString(ch)); quietSleep(35); }
        String after = safeValue(input); int end = after == null ? 0 : after.length();
        System.out.println("[DEBUG]   typed='" + text + "' lenBefore=" + start + " lenAfter=" + end);
        return (end - start) >= minimumDelta || end >= Math.max(2, text.length()/2);
    }
    private String safeValue(WebElement el){ try { return el.getAttribute("value"); } catch(Exception e){ return null; } }

    // AWT Robot fallback
    private void typeWithRobot(String text) {
        try {
            java.awt.Robot robot = new java.awt.Robot();
            for (char ch : text.toCharArray()) {
                int code = java.awt.event.KeyEvent.getExtendedKeyCodeForChar(ch);
                if (Character.isLetter(ch)) code = java.awt.event.KeyEvent.getExtendedKeyCodeForChar(Character.toUpperCase(ch));
                robot.keyPress(code);
                robot.keyRelease(code);
                Thread.sleep(25);
            }
        } catch (Exception e) {
            throw new RuntimeException("Robot typing failed", e);
        }
    }

    // Simple iframe dump
    private void debugDumpIframes(String tag) {
        driver.switchTo().defaultContent();
        List<WebElement> all = driver.findElements(By.tagName("iframe"));
        System.out.println("[DEBUG] dump iframes (" + tag + ") count=" + all.size());
        int i = 0;
        for (WebElement f : all) {
            try {
                System.out.printf("[DEBUG]   #%d title='%s' name='%s' src='%s' displayed=%s%n",
                        ++i, f.getAttribute("title"), f.getAttribute("name"), f.getAttribute("src"), f.isDisplayed());
            } catch (Exception ignore) {}
        }
    }




    /** Find the Stripe Payment Element iframe by looking into top-level and nested frames (depth 3). */
    private WebElement findPaymentIframeDeep(Duration timeout) {
        long end = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < end) {
            driver.switchTo().defaultContent();

            List<WebElement> level1 = driver.findElements(By.tagName("iframe"));
            for (WebElement f1 : level1) {
                if (!f1.isDisplayed()) continue;

                String src1 = String.valueOf(f1.getAttribute("src")).toLowerCase();
                String title1 = String.valueOf(f1.getAttribute("title")).toLowerCase();
                // Quick skip of known non-payment frames
                if (src1.contains("hcaptcha") || src1.contains("controller") || title1.contains("express checkout")) {
                    continue;
                }

                try {
                    // Enter f1 (e.g., stripe-origin-frame)
                    driver.switchTo().frame(f1);

                    // Direct hit: the input is right here
                    if (!driver.findElements(By.cssSelector(
                            "[data-elements-stable-field-name='cardNumber'], input[name='cardnumber'], input[autocomplete='cc-number'], .InputElement"
                    )).isEmpty()) {
                        driver.switchTo().defaultContent();
                        return f1; // the payment input is inside this iframe
                    }

                    // Look one level deeper
                    List<WebElement> level2 = driver.findElements(By.tagName("iframe"));
                    for (WebElement f2 : level2) {
                        if (!f2.isDisplayed()) continue;

                        String src2 = String.valueOf(f2.getAttribute("src")).toLowerCase();
                        String title2 = String.valueOf(f2.getAttribute("title")).toLowerCase();
                        if (src2.contains("hcaptcha") || src2.contains("controller") || src2.contains("express-checkout")
                                || title2.contains("express checkout")) {
                            continue;
                        }

                        try {
                            driver.switchTo().frame(f2);

                            // Is the payment input here?
                            if (!driver.findElements(By.cssSelector(
                                    "[data-elements-stable-field-name='cardNumber'], input[name='cardnumber'], input[autocomplete='cc-number'], .InputElement"
                            )).isEmpty()) {
                                driver.switchTo().defaultContent();
                                return f2;
                            }

                            // Level 3 (rare, but some themes nest one more time)
                            List<WebElement> level3 = driver.findElements(By.tagName("iframe"));
                            for (WebElement f3 : level3) {
                                if (!f3.isDisplayed()) continue;

                                String src3 = String.valueOf(f3.getAttribute("src")).toLowerCase();
                                String title3 = String.valueOf(f3.getAttribute("title")).toLowerCase();
                                if (src3.contains("hcaptcha") || src3.contains("controller") || src3.contains("express-checkout")
                                        || title3.contains("express checkout")) {
                                    continue;
                                }

                                try {
                                    driver.switchTo().frame(f3);
                                    if (!driver.findElements(By.cssSelector(
                                            "[data-elements-stable-field-name='cardNumber'], input[name='cardnumber'], input[autocomplete='cc-number'], .InputElement"
                                    )).isEmpty()) {
                                        driver.switchTo().defaultContent();
                                        return f3;
                                    }
                                } finally {
                                    driver.switchTo().parentFrame(); // out of f3
                                }
                            }
                        } finally {
                            driver.switchTo().parentFrame(); // out of f2
                        }
                    }
                } finally {
                    driver.switchTo().defaultContent(); // out of f1
                }
            }

            // small poll
            quietSleep(150);
        }
        return null;
    }




    private void ensureCardSectionOpen() {
        List<By> toggles = List.of(
                By.cssSelector("[data-testid='card-tab'], [data-testid*='card'][role='tab']"),
                By.cssSelector("button[aria-controls*='card'], button[aria-label*='card']")
        );
        for (By by : toggles) {
            var els = driver.findElements(by);
            if (!els.isEmpty() && els.get(0).isDisplayed()) {
                try { els.get(0).click(); break; } catch (Exception ignore) {}
            }
        }
    }

    // ===================== HOSTED CHECKOUT (pay.stripe.com) =====================

    public StripeCheckoutPage payHostedByTabbing(String email, String number, String expMmYy, String cvc, String zip) {
        driver.switchTo().defaultContent();

        // a) Email first
        By emailBox = By.cssSelector("input[type='email'], input[name='email'], input[autocomplete='email']");
        WebElement emailEl = new WebDriverWait(driver, Duration.ofSeconds(20))
                .until(ExpectedConditions.elementToBeClickable(emailBox));
        scrollIntoViewCenter(emailEl);
        emailEl.click();
        emailEl.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE, email, Keys.TAB);

        // If TAB didn’t reveal payment, press ENTER and wait for iframes
        if (!waitForPaymentElementToExist(Duration.ofSeconds(2))) {
            emailEl.sendKeys(Keys.ENTER);
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(d -> ((Number)((JavascriptExecutor)d)
                            .executeScript("return document.querySelectorAll('iframe').length")).intValue() >= 1);
        }

        // b) Make sure card section is open
        ensureCardSectionOpen();

        // c) Focus payment iframe (skip express/controller/hcaptcha)
        WebElement cardFrame = activePaymentIframeOrTabToFind(Duration.ofSeconds(8));
        if (cardFrame == null) cardFrame = findCardIframeHeuristic(Duration.ofSeconds(5));
        if (cardFrame == null) throw new TimeoutException("Could not focus/find Stripe Payment Element iframe.");

        // d) Inside iframe → type all fields using slow, reliable keystrokes
        driver.switchTo().frame(cardFrame);

        // Card number
        By cardNumberSel = By.cssSelector(
                "[data-elements-stable-field-name='cardNumber'], " +
                        "input[name='cardnumber'], input[autocomplete='cc-number'], .InputElement"
        );
        WebElement numberInput = new WebDriverWait(driver, Duration.ofSeconds(15))
                .until(ExpectedConditions.elementToBeClickable(cardNumberSel));
        numberInput.click();
        typeSlowly(numberInput, number.replaceAll("\\s+", "")); // digits only

        // Expiry
        numberInput.sendKeys(Keys.TAB);
        quietSleep(120);
        By expSel = By.cssSelector(
                "[data-elements-stable-field-name='cardExpiry'], " +
                        "input[autocomplete='cc-exp'], input[name='exp-date'], .InputElement"
        );
        WebElement expiryInput = new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.elementToBeClickable(expSel));
        expiryInput.click();
        typeSlowly(expiryInput, expMmYy);

        // CVC
        expiryInput.sendKeys(Keys.TAB);
        quietSleep(120);
        By cvcSel = By.cssSelector(
                "[data-elements-stable-field-name='cardCvc'], " +
                        "input[autocomplete='cc-csc'], input[name='cvc'], .InputElement"
        );
        WebElement cvcInput = new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.elementToBeClickable(cvcSel));
        cvcInput.click();
        typeSlowly(cvcInput, cvc);

        // ZIP optional
        if (zip != null && !zip.isBlank()) {
            cvcInput.sendKeys(Keys.TAB);
            quietSleep(120);
            By zipSel = By.cssSelector(
                    "[data-elements-stable-field-name='postalCode'], " +
                            "input[autocomplete*='postal'], input[name*='postal'], .InputElement"
            );
            WebElement zipInput = new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.elementToBeClickable(zipSel));
            zipInput.click();
            typeSlowly(zipInput, zip);
        }

        // e) Leave iframe and click Pay
        driver.switchTo().defaultContent();
        try { wait.waitForElementInvisible(blockingOverlay); } catch (Exception ignore) {}
        WebElement btn = wait.waitForElementClickable(payButton);
        scrollIntoViewCenter(btn);
        try { btn.click(); } catch (ElementClickInterceptedException e) { jsClick(btn); }

        return this;
    }

    // ===================== ON-SITE STRIPE ELEMENTS (multiple iframes) =====================

    /** Only used for the Elements (non-hosted) path. */
    private StripeCheckoutPage waitUntilElementsLoadedIfNeeded() {
        WaitUtils.waitForDocumentReady(driver);
        // If an email field is shown first on your site, fill it
        By emailBox = By.cssSelector("input[type='email'], input[name='email'], input[autocomplete='email']");
        if (WaitUtils.isVisible(driver, emailBox, Duration.ofSeconds(5))) {
            WebElement email = driver.findElement(emailBox);
            email.clear();
            email.sendKeys("erodriguez@effectussoftware.com" + Keys.ENTER);
        }
        // Wait for any Stripe iframe to exist
        By anyStripeIframe = By.cssSelector("iframe[src*='stripe'], iframe[name^='__privateStripeFrame']");
        new WebDriverWait(driver, Duration.ofSeconds(30))
                .until(ExpectedConditions.presenceOfElementLocated(anyStripeIframe));
        return this;
    }

    /** Fill all fields by switching into each Elements iframe. */
    private StripeCheckoutPage fillElementsCardDetails(String number, String expMmYy, String cvc, String postal) {
        typeIntoStripeFrame(cardNumberFrame, anyStripeFrame, number.replaceAll("\\s+",""));
        typeIntoStripeFrame(expDateFrame,    anyStripeFrame, expMmYy);
        typeIntoStripeFrame(cvcFrame,        anyStripeFrame, cvc);
        if (postal != null && !postal.isBlank()
                && (!driver.findElements(postalFrame).isEmpty() || hasAnotherStripeFrameBeyondTyped())) {
            typeIntoStripeFrame(postalFrame, anyStripeFrame, postal);
        }
        return this;
    }

    /** Click Pay/Submit (works for both paths). */
    public StripeCheckoutPage submitPayment() {
        driver.switchTo().defaultContent();
        try { wait.waitForElementInvisible(blockingOverlay); } catch (Exception ignore) {}
        WebElement btn = wait.waitForElementClickable(payButton);
        scrollIntoViewCenter(btn);
        try { btn.click(); } catch (ElementClickInterceptedException e) { jsClick(btn); }
        return this;
    }

    // ===================== 3DS =====================

    private final By threeDSFrame   = By.cssSelector("iframe[src*='3ds'], iframe[title*='challenge']");
    private final By threeDSApprove = By.xpath("//button[normalize-space()='Complete authentication' or normalize-space()='Authorize']");

    public StripeCheckoutPage complete3DSIfPresent() {
        try {
            driver.switchTo().defaultContent();
            WebElement f = stripeWait().until(d -> {
                d.switchTo().defaultContent();
                List<WebElement> fs = d.findElements(threeDSFrame);
                return fs.stream().filter(WebElement::isDisplayed).findFirst().orElse(null);
            });
            if (f != null) {
                driver.switchTo().frame(f);
                stripeWait().until(d -> d.findElement(threeDSApprove)).click();
            }
        } catch (Exception ignored) {
            // no challenge
        } finally {
            driver.switchTo().defaultContent();
        }
        return this;
    }

    // ===================== Helpers (shared) =====================

    private void typeIntoStripeFrame(By preferred, By fallback, String text) {
        driver.switchTo().defaultContent();
        WebElement frame = getFirstVisible(preferred, fallback);
        wait.waitForFrameAndSwitch(frame);
        try {
            WebElement input = stripeWait().until(d -> d.findElement(inputWithinFrame));
            input.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE);
            typeSlowly(input, text);
        } finally {
            driver.switchTo().defaultContent();
        }
    }

    private WebElement getFirstVisible(By preferred, By fallback) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            driver.switchTo().defaultContent();
            for (WebElement f : driver.findElements(preferred)) {
                if (f.isDisplayed()) return f;
            }
            quietSleep(100);
        }
        return stripeWait().until(d -> {
            d.switchTo().defaultContent();
            for (WebElement f : d.findElements(fallback)) {
                if (f.isDisplayed()) return f;
            }
            return null;
        });
    }

    private boolean hasAnotherStripeFrameBeyondTyped() {
        return driver.findElements(anyStripeFrame).size() >= 3;
    }

    private void scrollIntoViewCenter(WebElement el) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center', inline:'nearest'});", el);
    }

    private void jsClick(WebElement el) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
    }

    private static void quietSleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    // Dump iframes to console (useful when tuning selectors)
    @SuppressWarnings("unused")
    private void dumpIframeTree(WebDriver driver) {
        driver.switchTo().defaultContent();
        System.out.println("Top-level iframes: " + driver.findElements(By.tagName("iframe")).size());
        int i = 0;
        for (WebElement f : driver.findElements(By.tagName("iframe"))) {
            System.out.printf("F1[%d] name=%s title=%s src=%s%n",
                    i++, f.getAttribute("name"), f.getAttribute("title"), f.getAttribute("src"));
            try {
                driver.switchTo().frame(f);
                int j = 0;
                for (WebElement c : driver.findElements(By.tagName("iframe"))) {
                    System.out.printf("  F2[%d] name=%s title=%s src=%s%n",
                            j++, c.getAttribute("name"), c.getAttribute("title"), c.getAttribute("src"));
                }
            } finally {
                driver.switchTo().defaultContent();
            }
        }
    }

    // ----- Hosted helpers -----

    private boolean waitForPaymentElementToExist(Duration d) {
        long end = System.currentTimeMillis() + d.toMillis();
        while (System.currentTimeMillis() < end) {
            try {
                findInAnyFrame(By.cssSelector(
                        "[data-elements-stable-field-name='cardNumber'], input[name='cardnumber'], input[autocomplete='cc-number'], .InputElement"
                ), Duration.ofMillis(800));
                return true;
            } catch (TimeoutException ignore) {}
            quietSleep(100);
        }
        return false;
    }

    private WebElement activePaymentIframeOrTabToFind(Duration timeout) {
        long end = System.currentTimeMillis() + timeout.toMillis();
        Actions a = new Actions(driver);
        while (System.currentTimeMillis() < end) {
            WebElement focused = (WebElement)((JavascriptExecutor)driver).executeScript(
                    "return (document.activeElement && document.activeElement.tagName==='IFRAME') ? document.activeElement : null;");
            if (focused != null && focused.isDisplayed()) {
                String t = String.valueOf(focused.getAttribute("title")).toLowerCase();
                String s = String.valueOf(focused.getAttribute("src")).toLowerCase();
                if (!t.contains("express checkout") && !s.contains("express-checkout")
                        && !s.contains("hcaptcha") && !s.contains("controller")) {
                    return focused;
                }
            }
            a.sendKeys(Keys.TAB).pause(Duration.ofMillis(100)).perform();
        }
        return null;
    }

    private WebElement findCardIframeHeuristic(Duration timeout) {
        long end = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < end) {
            for (WebElement f : driver.findElements(By.cssSelector("iframe"))) {
                if (!f.isDisplayed()) continue;
                String src = String.valueOf(f.getAttribute("src")).toLowerCase();
                String title = String.valueOf(f.getAttribute("title")).toLowerCase();
                if (src.contains("express-checkout") || src.contains("hcaptcha") || src.contains("controller")) continue;
                try {
                    driver.switchTo().frame(f);
                    boolean hasCard = !driver.findElements(By.cssSelector(
                            "[data-elements-stable-field-name='cardNumber'], input[name='cardnumber'], input[autocomplete='cc-number'], .InputElement"
                    )).isEmpty();
                    driver.switchTo().defaultContent();
                    if (hasCard) return f;
                } catch (Exception ignore) {
                    driver.switchTo().defaultContent();
                }
            }
            quietSleep(120);
        }
        return null;
    }

    // Search all iframes (depth 2) for a locator
    private WebElement findInAnyFrame(By locator, Duration timeout) {
        long end = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < end) {
            driver.switchTo().defaultContent();

            WebElement fromTop = firstOrNull(driver.findElements(locator));
            if (fromTop != null) return fromTop;

            for (WebElement f1 : driver.findElements(By.cssSelector("iframe"))) {
                try {
                    driver.switchTo().frame(f1);
                    WebElement inF1 = firstOrNull(driver.findElements(locator));
                    if (inF1 != null) return inF1;

                    for (WebElement f2 : driver.findElements(By.cssSelector("iframe"))) {
                        try {
                            driver.switchTo().frame(f2);
                            WebElement inF2 = firstOrNull(driver.findElements(locator));
                            if (inF2 != null) return inF2;
                        } catch (StaleElementReferenceException ignored) {
                        } finally {
                            driver.switchTo().parentFrame();
                        }
                    }
                } catch (StaleElementReferenceException ignored) {
                } finally {
                    driver.switchTo().defaultContent();
                }
            }
            quietSleep(200);
        }
        throw new TimeoutException("Locator not found in any Stripe iframe(s): " + locator);
    }

    private WebElement firstOrNull(List<WebElement> list) {
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    // Slow typing to avoid Stripe dropping keys on fast sendKeys bursts
    private void typeSlowly(WebElement input, String text) {
        for (char ch : text.toCharArray()) {
            String before = input.getAttribute("value");
            input.sendKeys(Character.toString(ch));
            quietSleep(30);
            String after = input.getAttribute("value");
            if (before != null && after != null && after.equals(before)) {
                input.sendKeys(Character.toString(ch));
                quietSleep(30);
            }
        }
    }
}
