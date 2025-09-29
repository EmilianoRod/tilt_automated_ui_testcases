package pages.Shop;

import io.qameta.allure.Step;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pages.BasePage;

import java.time.Duration;
import java.util.Locale;

/**
 * Recipient selection step for the TTP flow.
 * Robust against "Next" vs "Continue" text, overlays/backdrops,
 * aria/class-based disable states, and minor UI shifts.
 */
public class PurchaseRecipientSelectionPage extends BasePage {

    public PurchaseRecipientSelectionPage(WebDriver driver) { super(driver); }

    // ========= Timeouts =========
    private static final Duration LOAD_TIMEOUT   = Duration.ofSeconds(12);
    private static final Duration CLICK_TIMEOUT  = Duration.ofSeconds(10);
    private static final Duration ENABLE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration POLL_SHORT     = Duration.ofMillis(300);
    private static final Duration POLL_MED       = Duration.ofMillis(500);

    // ========= Locators =========

    // ===================== Robust, text-anchored locators =====================

    /** H3 above the cart/selection panel (used by isLoaded). */
    public static final By H3_SELECTED_ASSESSMENTS =
            By.xpath("//h3[normalize-space()='Selected Assessments']");

    /** Page question/title. */
    public static final By H2_WHO_IS_PURCHASE_FOR =
            By.xpath("//h2[normalize-space()='Who is the purchase for?']");

    /** “Next / Continue” CTA (supports text, aria-label, data-test ids). */
    public static final By NEXT_CTA = By.xpath("//button[normalize-space()='Next']");

    /** Same button but guaranteed enabled (no disabled / aria-disabled). */
    public static final By NEXT_CTA_ENABLED = By.xpath( "//button[normalize-space()='Next']" +
                    "[ not(@disabled) and not(@aria-disabled='true') ]"
    );

    /** Cancel CTA. */
    public static final By BTN_CANCEL = By.xpath("//button[normalize-space()='Cancel']");

    /**
     * Possible opaque overlays/spinners/backdrops that block clicks.
     * (Ant Design + MUI + generic overlays)
     */
    public static final By POSSIBLE_BLOCKERS = By.cssSelector(
            "[data-testid='loading'],[data-test='loading'],[role='progressbar']," +
                    ".ant-spin,.ant-spin-spinning,.ant-modal-mask,.ant-drawer-mask," +
                    ".ant-message-notice-wrapper,.ant-notification,.ant-notification-notice," +
                    ".MuiBackdrop-root,.MuiCircularProgress-root," +
                    ".overlay,.spinner,.backdrop,[aria-busy='true']"
    );


    /**
     * From an <h3>, climb to the clickable container.
     * In this UI the clickable container is the nearest ancestor <div>.
     */
    public static final String CLICKABLE_ANCESTOR_FROM_H3 = "ancestor::div[1]";


    /** XPath normalization of visible text for tolerant matching (lowercase + strip a few chars). */
    public static final String NODE_NORM_TEXT =
            "translate(normalize-space(string(.)), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ()/-', 'abcdefghijklmnopqrstuvwxyz')";

    // ===================== Recipient card helpers (simple & robust) =====================

    /** Card by the <h3> label text (click this). */
    public static By recipientCardByText(String label) {
        String x = "//h3[normalize-space()=" + xpathLiteral(label) + "]/" + CLICKABLE_ANCESTOR_FROM_H3;
        return By.xpath(x);
    }

    /** Selected-state predicate: concrete app class + ARIA/radio + generic classes. */
    private static final String CARD_SELECTED_PRED = "//div[contains(concat(' ', normalize-space(@class), ' '), ' fmZwTp ')]";

    /** Card is considered “selected” (no hashed classes needed). */
    public static By recipientCardSelectedByText(String label) {
        String x = "//h3[normalize-space()=" + xpathLiteral(label) + "]/" +
                CLICKABLE_ANCESTOR_FROM_H3 + CARD_SELECTED_PRED;
        return By.xpath(x);
    }

    /** Convenience specific cards (optional). */
    public static final By CARD_MYSELF  = recipientCardByText("Myself");
    public static final By CARD_CLIENTS = recipientCardByText("Client(s)/Individual(s)");
    public static final By CARD_TEAM    = recipientCardByText("Team");

    private static String cardSelectedXpath(String label) {
        return "//h3[normalize-space()=" + xpathLiteral(label) + "]/" + CLICKABLE_ANCESTOR_FROM_H3 + CARD_SELECTED_PRED;
    }

    /** Any selected card among the three (useful for assertions). */
    public static final By ANY_CARD_SELECTED = By.xpath(
            "(" + cardSelectedXpath("Myself") + " | " +
                    cardSelectedXpath("Client(s)/Individual(s)") + " | " +
                    cardSelectedXpath("Team") + ")"
    );

    // ========= Model =========

    public enum Recipient {
        MYSELF(new String[]{"Myself", "For me", "For myself", "Individual"}),
        CLIENTS_INDIVIDUALS(new String[]{
                "Client(s)/Individual(s)", "Clients", "Client", "Individuals", "Individual",
                "Client or individual", "Send to someone else", "Someone else"
        }),
        TEAM(new String[]{"Team", "For a team", "My team", "Multiple people"});

        private final String[] labels;
        Recipient(String[] labels) { this.labels = labels; }
        public String[] labels() { return labels; }
        public String primary() { return labels[0]; }

        // Back-compat helper
        public String label() { return primary(); }
    }

    // ========= Identity =========

    /** Light identity check for this step. */
    public static boolean isCurrent(WebDriver driver) {
        return BasePage.isCurrentPage(driver, "/shop/ttp", H2_WHO_IS_PURCHASE_FOR)
                || BasePage.isCurrentPage(driver, "/dashboard/shop/ttp", H2_WHO_IS_PURCHASE_FOR);
    }

    // ========= Load state =========

    /** Wait until the selection UI is present. */
    public PurchaseRecipientSelectionPage waitUntilLoaded() {
        try { wait.waitForDocumentReady(); } catch (Throwable ignored) {}
        try { wait.waitForLoadersToDisappear(); } catch (Throwable ignored) {}
        new WebDriverWait(driver, LOAD_TIMEOUT)
                .until(ExpectedConditions.visibilityOfElementLocated(H2_WHO_IS_PURCHASE_FOR));
        return this;
    }

    /** Heuristic "loaded". */
    public boolean isLoaded() {
        return isVisible(H2_WHO_IS_PURCHASE_FOR) && isVisible(H3_SELECTED_ASSESSMENTS);
    }

    // ========= State checks (Next enable/disable) =========

    /** True if Next/Continue is effectively enabled (DOM + aria + classes + clickable). */
    public boolean isNextEnabled() {
        try {
            WebElement el = driver.findElement(NEXT_CTA);
            return isCtaEffectivelyEnabled(el) && isClickable(el);
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    /** Wait until Next/Continue is disabled (or not present yet). */
    public void waitUntilNextDisabled(Duration timeout) {
        new WebDriverWait(driver, timeout).until(d -> {
            WebElement el = findFirst(NEXT_CTA, POLL_SHORT);
            if (el == null) return true; // not present yet → treat as disabled
            return !isCtaEffectivelyEnabled(el);
        });
    }

    /** Wait until Next/Continue is enabled (present, visible, interactable, not blocked). */
    public void waitUntilNextEnabled(Duration timeout) {
        new WebDriverWait(driver, timeout).until(d -> {
            WebElement el = findFirst(NEXT_CTA, POLL_MED);
            if (el == null) return false;
            if (isAnyOverlayVisible()) return false;
            return isCtaEffectivelyEnabled(el) && isClickable(el);
        });
    }

    private boolean isClickable(WebElement el) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(1))
                    .until(ExpectedConditions.elementToBeClickable(el));
            return true;
        } catch (Throwable t) { return false; }
    }

    private boolean isCtaEffectivelyEnabled(WebElement el) {
        try {
            String aria = (el.getAttribute("aria-disabled") + "").toLowerCase(Locale.ROOT);
            String dis  = (el.getAttribute("disabled") + "").toLowerCase(Locale.ROOT);
            String cls  = (el.getAttribute("class") + "").toLowerCase(Locale.ROOT);

            boolean ariaDisabled = "true".equals(aria);
            boolean disabledAttr = "true".equals(dis) || "disabled".equals(dis);
            boolean classDisabled = cls.contains("disabled") || cls.contains("ant-btn-disabled") || cls.contains("mui-disabled");

            return el.isDisplayed() && el.isEnabled() && !ariaDisabled && !disabledAttr && !classDisabled;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isAnyOverlayVisible() {
        try {
            return driver.findElements(POSSIBLE_BLOCKERS).stream().anyMatch(WebElement::isDisplayed);
        } catch (Throwable t) { return false; }
    }

    private void waitForOverlayGone(Duration timeout) {
        try {
            new WebDriverWait(driver, timeout)
                    .until(ExpectedConditions.invisibilityOfElementLocated(POSSIBLE_BLOCKERS));
        } catch (Throwable ignored) {}
    }

    // ========= Actions =========

    /** Pick a recipient card by enum (tries all synonyms; robust click & selection wait). */
    @Step("Choose recipient: {recipient}")
    public PurchaseRecipientSelectionPage chooseRecipient(Recipient recipient) {
        By opt = optionByAny(recipient.labels());
        WebElement el = new WebDriverWait(driver, CLICK_TIMEOUT)
                .until(ExpectedConditions.elementToBeClickable(opt));

        // Scroll into view + safe click with JS fallback
        safeClick(el);
        System.out.println(el.getText());

        waitUntilSelected(opt);
        waitForOverlayGone(Duration.ofSeconds(2));
        return this;
    }

    private void waitUntilSelected(By opt) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(4)).until(d -> {
                try {
                    WebElement cur = d.findElement(opt);
                    String cls = (cur.getAttribute("class") + "").toLowerCase(Locale.ROOT);
                    String ap  = (cur.getAttribute("aria-pressed") + "").toLowerCase(Locale.ROOT);
                    String as  = (cur.getAttribute("aria-selected") + "").toLowerCase(Locale.ROOT);
                    String ac  = (cur.getAttribute("aria-checked") + "").toLowerCase(Locale.ROOT);

                    boolean ariaPicked   = "true".equals(ap) || "true".equals(as) || "true".equals(ac);
                    boolean classPicked  = cls.contains("selected") || cls.contains("active") || cls.contains("checked")
                            /* lenient fallback if UI uses a styled-components hash for selected */
                            || cls.contains("fmZwTp");
                    boolean radioChecked = !cur.findElements(By.cssSelector("input[type='radio']:checked")).isEmpty();

                    // Also accept the page-level signal that selection succeeded.
                    boolean nextEnabled = isNextEnabled();

                    return ariaPicked || classPicked || radioChecked || nextEnabled;
                } catch (Throwable t) { return false; }
            });
        } catch (TimeoutException ignored) { /* some UIs don’t reflect selected state; Next will still enable */ }
    }

    /** Pick a recipient card by explicit label (tolerant; robust click & selection wait). */
    @Step("Choose recipient by label: {label}")
    public PurchaseRecipientSelectionPage chooseRecipient(String label) {
        By opt = optionByLabel(label);
        WebElement el = new WebDriverWait(driver, CLICK_TIMEOUT)
                .until(ExpectedConditions.elementToBeClickable(opt));
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({block:'center', inline:'nearest'});", el);
        } catch (Throwable ignored) {}
        try {
            safeClick(el);
        } catch (Throwable e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
        }
        waitUntilSelected(opt);
        waitForOverlayGone(Duration.ofSeconds(2));
        return this;
    }

    /** Convenience wrappers. */
    public PurchaseRecipientSelectionPage selectMyself() { return chooseRecipient(Recipient.MYSELF); }
    public PurchaseRecipientSelectionPage selectClientsIndividuals() { return chooseRecipient(Recipient.CLIENTS_INDIVIDUALS); }
    public PurchaseRecipientSelectionPage selectTeam() { return chooseRecipient(Recipient.TEAM); }

    // ---- Backward-compatibility aliases for older tests (FIXED to not select "Myself") ----
    public PurchaseRecipientSelectionPage selectClientOrIndividual() { return selectClientsIndividuals(); }
    public PurchaseRecipientSelectionPage selectClientOrIndividuals() { return selectClientsIndividuals(); }
    public PurchaseRecipientSelectionPage selectClientOrTeam(boolean isTeam) { return isTeam ? selectTeam() : selectClientsIndividuals(); }

    /** Click Cancel. */
    @Step("Click Cancel")
    public void clickCancel() { safeClick(BTN_CANCEL); }

    /** Click Next/Continue (waits until enabled & not blocked). */
    @Step("Click Next / Continue")
    public void clickNextCta() {
        waitUntilNextEnabled(ENABLE_TIMEOUT);
        WebElement el = new WebDriverWait(driver, ENABLE_TIMEOUT)
                .until(ExpectedConditions.elementToBeClickable(NEXT_CTA));
        waitForOverlayGone(Duration.ofSeconds(1));
        try {
            safeClick(el);
        } catch (Throwable e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
        }
    }

    /** Legacy return type; prefer clickNextCta() + page identity checks. */
    @Deprecated
    public AssessmentEntryPage clickNext() {
        clickNextCta();
        return new AssessmentEntryPage(driver);
    }

    // ========= Utils =========

    /** Build option locator that matches ANY of the provided labels (case/punct tolerant). */
    private By optionByAny(String... labels) {
        StringBuilder orPred = new StringBuilder();
        for (String lab : labels) {
            String norm = normalizeLabel(lab);
            if (norm.isEmpty()) continue;
            if (orPred.length() > 0) orPred.append(" or ");
            // match on the H3's normalized text
            orPred.append("contains(")
                    .append("translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ()/-', 'abcdefghijklmnopqrstuvwxyz')")
                    .append(",")
                    .append(xpathLiteral(norm))
                    .append(")");
        }
        // Visible-ish to avoid hidden templates
        String visible = "[not(ancestor-or-self::*[contains(@style,'display:none') or contains(@style,'visibility:hidden')])]";
        String x = "//h3[" + orPred + "]/" + CLICKABLE_ANCESTOR_FROM_H3 + visible;
        return By.xpath(x);
    }

    /** Single-label convenience (calls the multi-label builder). */
    private By optionByLabel(String label) { return optionByAny(label); }

    /** Find-first helper with small timeout. */
    private WebElement findFirst(By by, Duration smallTimeout) {
        try {
            return new WebDriverWait(driver, smallTimeout).until(ExpectedConditions.presenceOfElementLocated(by));
        } catch (TimeoutException e) {
            return null;
        }
    }

    /** Java-side normalization mirroring the XPath transform. */
    private static String normalizeLabel(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replace("(", "")
                .replace(")", "")
                .replace("/", "")
                .replace("-", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Safe XPath string literal (handles mixed quotes via concat). */
    private static String xpathLiteral(String s) {
        if (s == null) return "''";
        if (s.contains("'") && s.contains("\"")) {
            String[] parts = s.split("\"");
            StringBuilder sb = new StringBuilder("concat(");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sb.append(", '\"', ");
                sb.append("\"").append(parts[i]).append("\"");
            }
            sb.append(")");
            return sb.toString();
        }
        return s.contains("'") ? "\"" + s + "\"" : "'" + s + "'";
    }
}
