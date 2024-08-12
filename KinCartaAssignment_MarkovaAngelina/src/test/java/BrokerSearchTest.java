import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.testng.Assert;

import java.time.Duration;
import java.util.List;

public class BrokerSearchTest {

    private WebDriver driver;

    // This method sets up the Firefox WebDriver before running the tests
    @BeforeClass
    public void setUp() {
        FirefoxOptions options = new FirefoxOptions();  // Configure Firefox options if needed
        driver = new FirefoxDriver(options);  // Initialize the Firefox WebDriver
        driver.get("https://www.yavlena.com/en/broker?city=Sofia");  // Navigate to the broker search page
    }

    // This is the main test method for searching brokers and validating their details
    @Test
    public void testBrokerSearch() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));  // Set up an explicit wait with a timeout of 10 seconds

        // Locate all broker items on the page using an XPath expression
        List<WebElement> brokers = driver.findElements(By.xpath("//div[contains(@class, 'MuiCardContent-root')]"));

        // Iterate through each broker found on the page
        for (WebElement broker : brokers) {
            // Extract the broker's name from within an <h6> tag inside an <a> element
            String brokerName = broker.findElement(By.xpath(".//a/h6")).getText();

            // Locate the search box, clear it, and enter the broker's name, then press Enter
            WebElement searchBox = driver.findElement(By.xpath("//input[@id='broker-keyword']"));
            searchBox.clear();
            searchBox.sendKeys(brokerName);
            searchBox.sendKeys(Keys.RETURN);  // Simulate pressing the Enter key

            // Wait until the search results are loaded
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[contains(@class, 'MuiCardContent-root')]")));

            // Fetch the search results after the page reloads
            List<WebElement> searchResults = driver.findElements(By.xpath("//div[contains(@class, 'MuiCardContent-root')]"));

            // Filter the search results to find the broker with the exact name
            long matchingBrokers = searchResults.stream()
                    .filter(result -> {
                        String resultName = result.findElement(By.xpath(".//a/h6")).getText();
                        return resultName.equals(brokerName);
                    })
                    .count();

            // Validate that only one broker with the searched name is displayed
            Assert.assertEquals(matchingBrokers, 1, "More than one broker found for the search term");

            // Locate the specific broker in the search results
            WebElement resultBroker = searchResults.stream()
                    .filter(result -> {
                        String resultName = result.findElement(By.xpath(".//a/h6")).getText();
                        return resultName.equals(brokerName);
                    })
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No broker found after filtering the results"));

            // Click the "Details" button to expand and display the broker's contact information
            WebElement detailsButton = resultBroker.findElement(By.xpath(".//button[contains(., 'Details')]"));
            detailsButton.click();

            // Wait for the contact information (e.g., phone number) to appear
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(".//a[contains(@href, 'tel:')]")));

            // Refresh the reference to the broker element to avoid stale element exceptions
            resultBroker = driver.findElement(By.xpath("//div[contains(@class, 'MuiCardContent-root')]"));

            // Validate that the address, phone number, and properties count are displayed
            WebElement address = resultBroker.findElement(By.xpath(".//span[contains(@class, 'MuiTypography-root') and contains(text(), 'Office Center')]"));
            WebElement landline = resultBroker.findElement(By.xpath(".//a[contains(@href, 'tel:')]"));
            WebElement propertiesCount = resultBroker.findElement(By.xpath(".//a[contains(text(), 'properties')]"));

            Assert.assertTrue(address.isDisplayed(), "Address is not displayed");
            Assert.assertTrue(landline.isDisplayed(), "Landline is not displayed");
            Assert.assertTrue(propertiesCount.isDisplayed(), "Properties count is not displayed");
        }
    }

    // This method cleans up and closes the browser after all tests have been executed
    @AfterClass
    public void tearDown() {
        if (driver != null) {
            driver.quit();  // Close the browser and end the WebDriver session
        }
    }
}
