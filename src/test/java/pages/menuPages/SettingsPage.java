package pages.menuPages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import pages.BasePage;

public class SettingsPage extends BasePage {


    private String url = "https://tilt.com/settings";  // example URL for settings

    private By settingsHeader = By.xpath("//h1[text()='Settings']");
    private final By settingsTitle = By.xpath("//h1[normalize-space()='Settings']");
    private final By saveButton = By.xpath("//button[normalize-space()='Save']");




    public SettingsPage(WebDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isVisible(settingsTitle);
    }

    public void clickSaveButton() {
        click(saveButton);
    }

    public void open() {
        driver.get(url);
    }

    
    
}
