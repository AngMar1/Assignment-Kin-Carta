import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxDriverLogLevel;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

public class BrokerSearchTest {

    private WebDriver driver;

    // Set up the WebDriver before any test methods are run
    @BeforeClass
    public void setUp() {
        FirefoxOptions options = new FirefoxOptions();

        // Automatically allow location access (handle the browser's geolocation prompt)
        options.addPreference("geo.enabled", true);
        options.addPreference("geo.provider.use_corelocation", true);
        options.addPreference("geo.prompt.testing", true);
        options.addPreference("geo.prompt.testing.allow", true);  // Automatically allow geolocation prompt

        // Suppress JavaScript warnings
        options.addPreference("dom.report_all_js_exceptions", false);

        // Suppress Firefox logs
        System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE, "NUL"); // Use "/dev/null" on Unix

        options.setLogLevel(FirefoxDriverLogLevel.ERROR);

        driver = new FirefoxDriver(options);
        driver.get("https://www.yavlena.com/en/broker?city=Sofia");

        // Click the 'Understood' button on the cookie consent popup
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10)); // Wait for up to 10 seconds for the button
        try {
            WebElement understoodButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Understood')]")));
            understoodButton.click();
        } catch (TimeoutException e) {
            System.out.println("Cookie consent banner 'Understood' button not found.");
        }
    }

    // Test method to search for each broker and verify their details
    @Test
    public void testBrokerSearch() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20)); // Wait for elements to load up to 20 seconds
        JavascriptExecutor js = (JavascriptExecutor) driver;
        SoftAssert softAssert = new SoftAssert(); // Create a SoftAssert instance

        // Find all brokers listed on the page initially
        List<WebElement> brokers = fluentWaitForElements(By.xpath("//div[contains(@class, 'MuiCardContent-root')]"));

        int processedCount = 0; // Track the number of brokers processed

        // Loop to ensure all brokers, including those loaded dynamically, are processed
        while (processedCount < brokers.size()) {
            for (int index = processedCount; index < brokers.size(); index++) {
                WebElement broker = brokers.get(index);
                String brokerName = fluentWaitForElement(() -> broker.findElement(By.xpath(".//a/h6"))).getText(); // Get broker's name

                WebElement searchBox = fluentWaitForElement(By.xpath("//input[@id='broker-keyword']"));
                searchBox.clear();
                searchBox.sendKeys(brokerName); // Enter broker name in search box
                searchBox.sendKeys(Keys.RETURN);

                // Wait until the search results are loaded
                wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[contains(@class, 'MuiCardContent-root')]")));

                // Store the search results in a final variable for use in lambda expressions
                final List<WebElement> searchResults = fluentWaitForElements(By.xpath("//div[contains(@class, 'MuiCardContent-root')]"));

                // Ensure correct broker is selected, even if there are duplicates
                WebElement resultBroker = searchResults.get(index);

                // Scroll to the broker's section in the page
                js.executeScript("arguments[0].scrollIntoView(true);", resultBroker);

                boolean clicked = false;
                for (int i = 0; i < 3; i++) { // Try to click the 'Details' button up to 3 times
                    try {
                        // Store the final broker reference for use in the lambda
                        final WebElement finalResultBroker = resultBroker;
                        WebElement detailsButton = fluentWaitForElement(() -> finalResultBroker.findElement(By.xpath(".//button[contains(., 'Details') and contains(@class, 'MuiButton-root')]")));
                        js.executeScript("arguments[0].click();", detailsButton); // Click the 'Details' button using JavaScript
                        clicked = true;
                        break;
                    } catch (StaleElementReferenceException e) {
                        System.out.println("Stale element found, retrying...");
                        // Re-fetch the search results after retry
                        List<WebElement> refreshedSearchResults = fluentWaitForElements(By.xpath("//div[contains(@class, 'MuiCardContent-root')]"));
                        resultBroker = refreshedSearchResults.get(index);
                    }
                }
                if (!clicked) {
                    softAssert.fail("Failed to click the 'Details' button after retries for broker: " + brokerName);
                    continue;
                }

                // Wait until the contact details (landline) are visible after clicking 'Details'
                wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(".//a[contains(@href, 'tel:')]")));

                // Verify the broker's contact details and properties count
                WebElement refreshedBroker = fluentWaitForElement(By.xpath("//div[contains(@class, 'MuiCardContent-root')]"));

                WebElement address = refreshedBroker.findElement(By.xpath(".//span[contains(@class, 'MuiTypography-root') and contains(text(), 'Office Center')]"));
                WebElement landline = findLandlineElementWithRetry(refreshedBroker); // Fetch landline with retry
                WebElement propertiesCount = refreshedBroker.findElement(By.xpath(".//a[contains(text(), 'properties')]"));

                // Soft assertions to ensure all details are displayed
                softAssert.assertTrue(retryAssertIsDisplayed(address), "Address is not displayed for broker: " + brokerName);
                if (landline != null) {
                    softAssert.assertTrue(retryAssertIsDisplayed(landline), "Landline is not displayed for broker: " + brokerName);
                } else {
                    softAssert.fail("Landline should be displayed but was not found for broker: " + brokerName);
                }
                softAssert.assertTrue(retryAssertIsDisplayed(propertiesCount), "Properties count is not displayed for broker: " + brokerName);

                processedCount++; // Increment the count of processed brokers
            }

            // Scroll back to the top to trigger loading of more brokers
            js.executeScript("window.scrollTo(0, 0);");
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[contains(@class, 'MuiCardContent-root')]")));

            // Refresh the list of brokers after new brokers are loaded
            brokers = fluentWaitForElements(By.xpath("//div[contains(@class, 'MuiCardContent-root')]"));
        }

        // Assert all the soft assertions
        softAssert.assertAll();
    }

    // Helper method to retry fetching landline element with retries to handle stale elements or late loading
    private WebElement findLandlineElementWithRetry(WebElement refreshedBroker) {
        int attempts = 0;
        while (attempts < 3) {
            try {
                WebElement landline = refreshedBroker.findElement(By.xpath(".//a[contains(@href, 'tel:')]"));
                if (landline != null && landline.isDisplayed()) {
                    return landline;
                }
            } catch (StaleElementReferenceException | NoSuchElementException e) {
                attempts++;
                System.out.println("Retrying to fetch landline due to StaleElementReferenceException or NoSuchElementException...");
            }
        }
        return null; // Return null if landline could not be located after retries
    }

    // Helper method to retry the isDisplayed assertion logic with retries to handle stale elements
    private boolean retryAssertIsDisplayed(WebElement element) {
        int attempts = 0;
        while (attempts < 3) {
            try {
                return element.isDisplayed(); // Check if the element is displayed
            } catch (StaleElementReferenceException e) {
                attempts++;
                System.out.println("Retrying due to StaleElementReferenceException on isDisplayed...");
            }
        }
        return false; // Return false if the element could not be verified as displayed after retries
    }

    // Helper method to find elements using FluentWait
    private List<WebElement> fluentWaitForElements(By locator) {
        FluentWait<WebDriver> wait = new FluentWait<>(driver)
                .withTimeout(Duration.ofSeconds(30))
                .pollingEvery(Duration.ofMillis(500))
                .ignoring(StaleElementReferenceException.class)
                .ignoring(NoSuchElementException.class);

        return wait.until(driver -> driver.findElements(locator));
    }

    // Helper method to find a single element using FluentWait
    private WebElement fluentWaitForElement(By locator) {
        FluentWait<WebDriver> wait = new FluentWait<>(driver)
                .withTimeout(Duration.ofSeconds(30))
                .pollingEvery(Duration.ofMillis(500))
                .ignoring(StaleElementReferenceException.class)
                .ignoring(NoSuchElementException.class);

        return wait.until(driver -> driver.findElement(locator));
    }

    // Overloaded helper method to find a single element using FluentWait and Supplier
    private WebElement fluentWaitForElement(Supplier<WebElement> elementSupplier) {
        FluentWait<WebDriver> wait = new FluentWait<>(driver)
                .withTimeout(Duration.ofSeconds(30))
                .pollingEvery(Duration.ofMillis(500))
                .ignoring(StaleElementReferenceException.class)
                .ignoring(NoSuchElementException.class);

        return wait.until(driver -> elementSupplier.get());
    }

    // Tear down the WebDriver after all test methods are completed
    @AfterClass
    public void tearDown() {
        if (driver != null) {
            driver.quit(); // Close the browser and end the WebDriver session
        }
    }
}

