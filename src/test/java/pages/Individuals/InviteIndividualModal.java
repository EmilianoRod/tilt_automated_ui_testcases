package pages.Individuals;

import Utils.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import pages.BasePage;

import java.time.Duration;

public class InviteIndividualModal extends BasePage {





    public InviteIndividualModal(WebDriver driver) { super(driver); }

    // ---------- Locators (text-based, resilient) ----------
    private By modalRoot() {
        return By.xpath("//*[contains(@role,'dialog') or contains(@class,'modal')]" +
                "[.//*[normalize-space()='Invite Individual'] or contains(.,'Invite Individual')]");
    }

    private By successToast() {
        return By.xpath("(" +
                "//div[contains(@role,'alert') or contains(@class,'toast') or contains(@class,'notification')]" +
                "[contains(.,'Invite') or contains(.,'invitation') or contains(.,'sent') or contains(.,'success')]" +
                " | //div[contains(@class,'ant-message')]//span[contains(.,'sent') or contains(.,'success')]" +
                ")");
    }

    private By inputByLabel(String label) {
        return By.xpath("//label[normalize-space()='" + label + "']/following::input[1]");
    }

    private By assessmentTrigger = By.xpath(
            "(//button[.//span[contains(.,'Select')]] | //div[@role='combobox'] | //select)[1]"
    );

    private By assessmentOption(String label) {
        return By.xpath("(" +
                "//*[self::label or self::span or self::div or self::button][normalize-space()='" + label + "']" +
                " | //option[normalize-space()='" + label + "']" +
                ")[last()]");
    }

    private By modalButton(String text) {
        return By.xpath("//button[normalize-space()='" + text + "']" +
                "[ancestor::*[contains(@role,'dialog') or contains(@class,'modal')]]");
    }

    // ---------- Actions ----------
    public InviteIndividualModal waitUntilOpen() {
        WaitUtils.isVisible(driver, modalRoot(), Duration.ofSeconds(10));
        return this;
    }

    public InviteIndividualModal typeFullName(String fullName) {
        type(inputByLabel("Full Name"), fullName);

        return this;
    }

    public InviteIndividualModal typeEmail(String email) {
        type(inputByLabel("Email"), email);
        return this;
    }

    public InviteIndividualModal selectAssessment(String label) {
        if (WaitUtils.isVisible(driver, assessmentTrigger, Duration.ofSeconds(2))) {
           click(assessmentTrigger);
        }
        click(assessmentOption(label));
        return this;
    }

    public InviteIndividualModal clickSend() {
        click(modalButton("Send Invite"));
        return this;
    }

    public boolean waitForInviteSuccess() {
        return WaitUtils.isVisible(driver, successToast(), Duration.ofSeconds(10));
    }

    public void closeIfVisible() {
        By closeBtn = By.xpath("//button[@aria-label='Close' or normalize-space()='Close']");
        if (WaitUtils.isVisible(driver, closeBtn, Duration.ofSeconds(2))) {
            driver.findElement(closeBtn).click();
        }
    }
}
