package apps.tv.pages;

import driver.TestContext;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Common base for all Android TV page objects.
 * Holds the shared {@link DpadNavigator}, waiting helpers and Allure screenshot support.
 * Mirrors the phone framework's BasePage, but interactions go through the D-pad instead of touch.
 */
public abstract class TvBasePage extends Wait {

    public final AppiumDriver appiumDriver;
    protected final DpadNavigator dpad;

    protected TvBasePage(TestContext testContext) {
        super(testContext);
        this.appiumDriver = testContext.getAppiumDriver();
        this.dpad = new DpadNavigator(testContext);
    }

    protected String textOf(By by) {
        return fluentPresenceOfElementLocated(by).getText().trim();
    }

    protected boolean isPresent(By by) {
        return !appiumDriver.findElements(by).isEmpty();
    }

    @Step("Attach screenshot: {name}")
    public void attachScreenToReport(String name) {
        File file = testContext.getAndroidDriver().getScreenshotAs(OutputType.FILE);
        try (InputStream is = Files.newInputStream(Path.of(file.getPath()))) {
            Allure.addAttachment(name, is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
