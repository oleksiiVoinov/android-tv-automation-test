package apps.tv.pages;

import driver.TestContext;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.nativekey.AndroidKey;
import io.appium.java_client.android.nativekey.KeyEvent;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

import java.time.Duration;

/**
 * D-pad (remote control) navigation for Android TV.
 *
 * On TV there is no touch: you cannot tap coordinates. Instead you move the
 * on-screen focus with the directional keys and confirm with the center key.
 * The interaction model is therefore: move focus onto the target element
 * (its {@code focused} attribute becomes "true") → press CENTER to activate.
 */
public class DpadNavigator {

    private final AndroidDriver driver;
    private final Duration keyDelay = Duration.ofMillis(400);

    public DpadNavigator(TestContext testContext) {
        this.driver = testContext.getAndroidDriver();
    }

    public DpadNavigator up() {
        return press(AndroidKey.DPAD_UP);
    }

    public DpadNavigator down() {
        return press(AndroidKey.DPAD_DOWN);
    }

    public DpadNavigator left() {
        return press(AndroidKey.DPAD_LEFT);
    }

    public DpadNavigator right() {
        return press(AndroidKey.DPAD_RIGHT);
    }

    @Step("Press D-pad center (OK)")
    public DpadNavigator center() {
        return press(AndroidKey.DPAD_CENTER);
    }

    public DpadNavigator back() {
        return press(AndroidKey.BACK);
    }

    public DpadNavigator press(AndroidKey key) {
        driver.pressKey(new KeyEvent(key));
        sleep(keyDelay);
        return this;
    }

    /** True when the located element (or a matching child) currently holds focus. */
    public boolean isFocused(By by) {
        try {
            WebElement element = driver.findElement(by);
            return "true".equalsIgnoreCase(element.getDomAttribute("focused"));
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    /**
     * Repeatedly presses {@code direction} until {@code target} holds focus.
     * Fails fast if the target is never reached within {@code maxSteps}.
     */
    @Step("Move focus to {target} via {direction}")
    public DpadNavigator focus(By target, AndroidKey direction, int maxSteps) {
        for (int step = 0; step <= maxSteps; step++) {
            if (isFocused(target)) {
                return this;
            }
            press(direction);
        }
        if (!isFocused(target)) {
            throw new NoSuchElementException(
                    "Could not move focus to " + target + " within " + maxSteps + " " + direction + " presses");
        }
        return this;
    }

    @Step("Focus {target} and press OK")
    public DpadNavigator focusAndSelect(By target, AndroidKey direction, int maxSteps) {
        focus(target, direction, maxSteps);
        return center();
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
