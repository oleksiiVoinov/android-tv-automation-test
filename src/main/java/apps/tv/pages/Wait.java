package apps.tv.pages;

import driver.TestContext;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;

import java.time.Duration;

public class Wait {
    public final TestContext testContext;

    private final Duration explicit = Duration.ofSeconds(5);

    public Wait(TestContext testContext) {
        this.testContext = testContext;
    }

    public WebElement fluentPresenceOfElementLocated(By by, Duration timeout) {
        FluentWait<AppiumDriver> fluentWait = new FluentWait<>(testContext.getAppiumDriver())
                .withTimeout(timeout)
                .pollingEvery(Duration.ofMillis(500))
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class);
        return fluentWait.until(ExpectedConditions.presenceOfElementLocated(by));
    }

    public WebElement fluentPresenceOfElementLocated(By by) {
        return fluentPresenceOfElementLocated(by, explicit);
    }

    public WebElement fluentVisibility(By by, Duration timeout) {
        FluentWait<AppiumDriver> fluentWait = new FluentWait<>(testContext.getAppiumDriver())
                .withTimeout(timeout)
                .pollingEvery(Duration.ofMillis(500))
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class);
        return fluentWait.until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    public WebElement fluentVisibility(By by) {
        return fluentVisibility(by, explicit);
    }

    public boolean waitForText(By by, String expectedText, Duration timeout) {
        FluentWait<AppiumDriver> fluentWait = new FluentWait<>(testContext.getAppiumDriver())
                .withTimeout(timeout)
                .pollingEvery(Duration.ofMillis(500))
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class);
        try {
            return fluentWait.until(driver ->
                    expectedText.equalsIgnoreCase(driver.findElement(by).getText().trim()));
        } catch (org.openqa.selenium.TimeoutException e) {
            return false;
        }
    }

    @Step("make pause")
    public void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
