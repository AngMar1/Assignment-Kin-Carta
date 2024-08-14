import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxDriverLogLevel;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

public class BrokerSearchTest {

    private WebDriver driver;

    // Set up the WebDriver before any test methods are run
    @BeforeClass
    public void setUp() {
        FirefoxOptions options = new FirefoxOptions();

        // Suppress JavaScript warnings
        options.addPreference("dom.report_all_js_exceptions", false);

        // Suppress Firefox logs
        System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE, "NUL"); // Use "/dev/null" on Unix

        options.setLogLevel(FirefoxDriverLogLevel.ERROR);

        driver = new FirefoxDriver(options);
        driver.get("https://www.yavlena.com/en/broker?city=Sofia");
    }

    // Test method to search for each broker and verify their details
    @Test
    public void testBrokerSearch() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20)); // Wait for elements to load up to 20 seconds
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Find all brokers listed on the page initially
        List<WebElement> brokers = retryingFindElements(By.xpath("//div[contains(@class, 'MuiCardContent-root')]"));

        int processedCount = 0; // Track the number of brokers processed

        // Loop to ensure all brokers, including those loaded dynamically, are processed
        while (processedCount < brokers.size()) {
            for (int index = processedCount; index < brokers.size(); index++) {
                WebElement broker = brokers.get(index);
                String brokerName = retryingGetText(() -> broker.findElement(By.xpath(".//a/h6"))); // Get broker's name

                WebElement searchBox = driver.findElement(By.xpath("//input[@id='broker-keyword']"));
                searchBox.clear();
                searchBox.sendKeys(brokerName); // Enter broker name in search box
                searchBox.sendKeys(Keys.RETURN);

                // Wait until the search results are loaded
                wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[contains(@class, 'MuiCardContent-root')]")));

                List<WebElement> searchResults = retryingFindElements(By.xpath("//div[contains(@class, 'MuiCardContent-root')]"));

                // Ensure correct broker is selected, even if there are duplicates
                WebElement resultBroker = searchResults.get(index);

                // Scroll to the broker's section in the page
                js.executeScript("arguments[0].scrollIntoView(true);", resultBroker);

                boolean clicked = false;
                for (int i = 0; i < 3; i++) { // Try to click the 'Details' button up to 3 times
                    try {
                        // Find the 'Details' button for this broker
                        WebElement finalResultBroker = resultBroker;
                        WebElement detailsButton = retryingFindElement(() -> finalResultBroker.findElement(By.xpath(".//button[contains(., 'Details') and contains(@class, 'MuiButton-root')]")));
                        wait.until(ExpectedConditions.elementToBeClickable(detailsButton)); // Wait until the button is clickable
                        js.executeScript("arguments[0].click();", detailsButton); // Click the 'Details' button using JavaScript
                        clicked = true;
                        break;
                    } catch (StaleElementReferenceException e) {
                        // Handle cases where the element becomes stale
                        System.out.println("Retrying click on 'Details' button due to stale element...");
                        searchResults = retryingFindElements(By.xpath("//div[contains(@class, 'MuiCardContent-root')]"));
                        resultBroker = searchResults.get(index);
                    }
                }
                if (!clicked) {
                    throw new AssertionError("Failed to click the 'Details' button after retries");
                }

                // Wait until the contact details (landline) are visible after clicking 'Details'
                wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(".//a[contains(@href, 'tel:')]")));

                // Verify the broker's contact details and properties count
                WebElement refreshedBroker = retryingFindElement(() -> driver.findElement(By.xpath("//div[contains(@class, 'MuiCardContent-root')]")));

                WebElement address = refreshedBroker.findElement(By.xpath(".//span[contains(@class, 'MuiTypography-root') and contains(text(), 'Office Center')]"));
                WebElement landline = refreshedBroker.findElement(By.xpath(".//a[contains(@href, 'tel:')]"));
                WebElement propertiesCount = refreshedBroker.findElement(By.xpath(".//a[contains(text(), 'properties')]"));

                // Assertions to ensure all details are displayed
                Assert.assertTrue(address.isDisplayed(), "Address is not displayed");
                Assert.assertTrue(landline.isDisplayed(), "Landline is not displayed");
                Assert.assertTrue(propertiesCount.isDisplayed(), "Properties count is not displayed");

                processedCount++; // Increment the count of processed brokers
            }

            // Scroll back to the top to trigger loading of more brokers
            js.executeScript("window.scrollTo(0, 0);");
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[contains(@class, 'MuiCardContent-root')]")));

            // Refresh the list of brokers after new brokers are loaded
            brokers = retryingFindElements(By.xpath("//div[contains(@class, 'MuiCardContent-root')]"));
        }
    }

    // Helper method to find elements with retry logic in case of stale elements
    private List<WebElement> retryingFindElements(By locator) {
        return retryingExecute(() -> driver.findElements(locator));
    }

    // Helper method to find a single element with retry logic in case of stale elements
    private WebElement retryingFindElement(Supplier<WebElement> elementSupplier) {
        return retryingExecute(elementSupplier);
    }

    // Helper method to get text from an element with retry logic in case of stale elements
    private String retryingGetText(Supplier<WebElement> elementSupplier) {
        return retryingExecute(() -> elementSupplier.get().getText());
    }

    // Generic retry logic for executing a supplier with up to 3 retries
    private <T> T retryingExecute(Supplier<T> supplier) {
        int attempts = 0;
        while (attempts < 3) {
            try {
                return supplier.get();
            } catch (StaleElementReferenceException e) {
                attempts++;
                System.out.println("Retrying due to StaleElementReferenceException...");
            }
        }
        throw new AssertionError("Element reference is stale after multiple retries");
    }

    // Tear down the WebDriver after all test methods are completed
    @AfterClass
    public void tearDown() {
        if (driver != null) {
            driver.quit(); // Close the browser and end the WebDriver session
        }
    }
}
