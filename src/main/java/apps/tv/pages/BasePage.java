package apps.tv.pages;

import driver.TestContext;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Rectangle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Common base for all Android TV page objects.
 * Holds the shared {@link DpadNavigator}, waiting helpers and Allure screenshot support.
 * Mirrors the phone framework's BasePage, but interactions go through the D-pad instead of touch.
 */
public abstract class BasePage extends Wait {

    public final AppiumDriver appiumDriver;
    protected final DpadNavigator dpad;

    protected BasePage(TestContext testContext) {
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

    protected boolean isDisplayed(By by) {
        try {
            return fluentPresenceOfElementLocated(by).isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Taps an element by the center of its {@code bounds} via a UiAutomator2 click gesture.
     * Verified to work on Android TV (injected touch).
     * <p>
     * Kept as a spare / escape hatch only. Real test flows deliberately use D-pad
     * ({@code dpad.focusOnAndSelect(...)}) to mirror how an actual user drives the TV with a
     * remote — clicking coordinates bypasses the real focus/interaction path we want to cover.
     */
    @Step("Tap {target} (coordinate click)")
    protected void tap(By target) {
        Rectangle r = fluentPresenceOfElementLocated(target).getRect();
        int cx = r.getX() + r.getWidth() / 2;
        int cy = r.getY() + r.getHeight() / 2;
        testContext.getAndroidDriver().executeScript(
                "mobile: clickGesture", Map.of("x", cx, "y", cy));
    }

    /**
     * Presses and holds an element for {@code duration} via a UiAutomator2 long-click gesture
     * (touch down → hold → up). This is the only reliable way to hold for a specific time on TV:
     * a D-pad center long-press only fires a single {@code onLongClick} at the system long-press
     * timeout, not a controllable multi-second hold. Used e.g. for a hold-to-connect button.
     */
    @Step("Press and hold {target} for {duration}")
    protected void longPress(By target, java.time.Duration duration) {
        Rectangle r = fluentPresenceOfElementLocated(target).getRect();
        int cx = r.getX() + r.getWidth() / 2;
        int cy = r.getY() + r.getHeight() / 2;
        testContext.getAndroidDriver().executeScript(
                "mobile: longClickGesture",
                Map.of("x", cx, "y", cy, "duration", duration.toMillis()));
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
