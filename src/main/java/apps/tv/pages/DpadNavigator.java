package apps.tv.pages;

import driver.TestContext;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.nativekey.AndroidKey;
import io.appium.java_client.android.nativekey.KeyEvent;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.Point;
import org.openqa.selenium.Rectangle;
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

    private static final int TOLERANCE_PX = 8;   // treat centers within this as aligned
    private static final int MAX_MOVES = 25;      // hard cap on presses
    private final By focusedSelector = AppiumBy.androidUIAutomator("new UiSelector().focused(true)");

    /**
     * Direction-free focus: figures out where to move by comparing the geometry
     * ({@code bounds}) of the currently focused element and the target, and presses
     * toward it — vertically first, then horizontally. Works from any starting focus
     * and for 2D layouts (e.g. the protocol grid). Fails fast if focus stops moving
     * (hit a wall / target unreachable), instead of spamming keys until a step cap.
     */
    @Step("Focus element {target} (auto direction)")
    public DpadNavigator focusOn(By target) {
        // Ensure the target exists in the hierarchy up front (clear error otherwise).
        rectOf(target);

        int stuck = 0;
        for (int move = 0; move < MAX_MOVES; move++) {
            if (isFocused(target)) {
                return this;
            }

            Rectangle focused = focusedRect();
            if (focused == null) {
                // Nothing focused yet — nudge to give the screen a focus anchor.
                press(AndroidKey.DPAD_DOWN);
                continue;
            }

            Rectangle targetRect = rectOf(target);
            AndroidKey direction = directionToward(center(focused), center(targetRect));

            press(direction);

            // Stuck detection: if focus didn't move after the press, we're wedged.
            Rectangle after = focusedRect();
            if (after != null && sameRect(focused, after)) {
                if (++stuck >= 2) {
                    throw new NoSuchElementException(
                            "focusOn: focus stuck at " + rectSig(after) + " while seeking " + target
                                    + " (wall reached or target not focusable)");
                }
            } else {
                stuck = 0;
            }
        }

        if (isFocused(target)) {
            return this;
        }
        throw new NoSuchElementException("focusOn: could not focus " + target + " within " + MAX_MOVES + " moves");
    }

    @Step("Focus {target} (auto direction) and press OK")
    public DpadNavigator focusOnAndSelect(By target) {
        focusOn(target);
        return center();
    }

    private AndroidKey directionToward(Point from, Point to) {
        int dy = to.getY() - from.getY();
        int dx = to.getX() - from.getX();
        if (Math.abs(dy) > Math.abs(dx)) {
            return dy > 0 ? AndroidKey.DPAD_DOWN : AndroidKey.DPAD_UP;
        }
        if (Math.abs(dx) > TOLERANCE_PX) {
            return dx > 0 ? AndroidKey.DPAD_RIGHT : AndroidKey.DPAD_LEFT;
        }
        // Centers vertically aligned but not on target yet — step vertically.
        return dy > 0 ? AndroidKey.DPAD_DOWN : AndroidKey.DPAD_UP;
    }

    private Rectangle rectOf(By by) {
        return driver.findElement(by).getRect();
    }

    /** Rectangle of the element that currently holds focus, or null if none. */
    private Rectangle focusedRect() {
        try {
            return driver.findElement(focusedSelector).getRect();
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    private Point center(Rectangle r) {
        return new Point(r.getX() + r.getWidth() / 2, r.getY() + r.getHeight() / 2);
    }

    private boolean sameRect(Rectangle a, Rectangle b) {
        return a.getX() == b.getX() && a.getY() == b.getY()
                && a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight();
    }

    private String rectSig(Rectangle r) {
        return "[" + r.getX() + "," + r.getY() + " " + r.getWidth() + "x" + r.getHeight() + "]";
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
