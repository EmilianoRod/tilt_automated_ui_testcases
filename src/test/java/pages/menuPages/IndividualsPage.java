package pages.menuPages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import pages.BasePage;

import java.util.List;

public class IndividualsPage extends BasePage {

    public IndividualsPage(WebDriver driver) {
        super(driver);
    }

    // Locators
    private final By title = By.xpath("//h1[1]");
    private final By searchInput = By.xpath("//div[@height='unset']//div//input[@placeholder='Search here']");
//    private final By sortDropdown = By.xpath("//button[contains(.,'Sort by')]");
//    private final By firstRowName = By.xpath("//tbody/tr[1]/td[1]");  // Adjust if column index changes
//    private final By firstRowEmail = By.xpath("//tbody/tr[1]/td[2]");
//    private final By firstRowReportTag = By.xpath("//tbody/tr[1]/td[3]/span[contains(@class, 'Tag')]");
//    private final By downloadDataBtn = By.xpath("//button[contains(.,'Download Data')]");
    private final By tableRows = By.xpath("//table//tr");


    // Actions
    public boolean isLoaded() {
        return isVisible(title);
    }

//    public String getFirstUserName() {
//        return getText(firstRowName);
//    }
//
//    public String getFirstUserEmail() {
//        return getText(firstRowEmail);
//    }
//
//    public String getFirstReportTag() {
//        return getText(firstRowReportTag);
//    }

    public void searchUser(String nameOrEmail) {
        type(searchInput, nameOrEmail);
    }

//    public void clickSortDropdown() {
//        click(sortDropdown);
//    }
//
//    public void clickDownloadData() {
//        click(downloadDataBtn);
//    }
//
//    public boolean isDownloadButtonVisible() {
//        return isVisible(downloadDataBtn);
//    }


/*
    public boolean isUserListedByEmail(String email) {
        List<WebElement> rows = driver.findElements(By.xpath("//table//tr"));
        for (WebElement row : rows) {
            if (row.getText().contains(email)) {
                return true;
            }
        }
        return false;
    }
*/


    public boolean isUserListedByEmail(String email) {
        return driver.findElements(By.xpath("//table//tr[td[contains(text(),'" + email + "')]]")).size() > 0;
    }

    public void waitUntilUserInviteAppears(String email) {
        for (int i = 0; i < 10; i++) { // Retry up to 10 times
            if (isUserListedByEmail(email)) {
                return;
            }
            try {
                Thread.sleep(1000); // 1-second polling
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("❌ Waiting for user invite was interrupted", e);
            }
        }
        throw new AssertionError("❌ User with email " + email + " not listed in Individuals table");
    }

}
