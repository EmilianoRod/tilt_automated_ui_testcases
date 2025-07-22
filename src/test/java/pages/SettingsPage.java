package pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class SettingsPage {


    private WebDriver driver;
    private String url = "https://tilt.com/settings";  // example URL for settings

    private By settingsHeader = By.xpath("//h1[text()='Settings']");

    public SettingsPage(WebDriver driver) {
        this.driver = driver;
    }

    public void open() {
        driver.get(url);
    }
    public boolean isLoaded() {
        return driver.findElements(settingsHeader).size() > 0;
    }
    // Additional interactions like changing a setting can be added as needed
}
