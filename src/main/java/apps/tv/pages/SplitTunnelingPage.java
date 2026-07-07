package apps.tv.pages;

import driver.TestContext;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.testng.Assert;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * "Split tunneling" screen (TvAppsManagerActivity) — opened from the settings menu.
 * A master "all apps use VPN" checkbox ({@code check_all_app}) plus a scrollable list of apps,
 * each row = icon ({@code iv_app_icon}) + name ({@code tv_app_name}) + a checkbox
 * ({@code switch_proxy}). Checked = the app runs through the VPN; unchecked = the app is excluded.
 * <p>
 * Rows are addressed by <b>index</b>: the Nth {@code tv_app_name} pairs with the Nth
 * {@code switch_proxy} (document order). Checkboxes are toggled with a direct tap (like a real
 * click), not the D-pad — a D-pad center on this screen toggles the master, not a single row.
 * Locators verified on device.
 */
public class SplitTunnelingPage extends BasePage {

    private static final String PKG = "com.free.vpn.super.hotspot.open:id/";

    public final By allAppsCheckbox = By.id(PKG + "check_all_app");
    public final By appName = By.id(PKG + "tv_app_name");
    public final By appSwitch = By.id(PKG + "switch_proxy");
    public final By backButton = By.id(PKG + "back");

    public SplitTunnelingPage(TestContext testContext) {
        super(testContext);
    }

    SplitTunnelingPage waitLoaded() {
        fluentVisibility(allAppsCheckbox, Duration.ofSeconds(15));
        return this;
    }

    @Step("Verify the Split tunneling screen is displayed")
    public SplitTunnelingPage verifyDisplayed() {
        Assert.assertTrue(isDisplayed(allAppsCheckbox), "'All apps' master checkbox not displayed");
        Assert.assertFalse(appiumDriver.findElements(appName).isEmpty(), "No app rows displayed");
        Assert.assertTrue(isDisplayed(backButton), "Back button not displayed");
        attachScreenToReport("Split tunneling");
        return this;
    }

    // ---- master "all apps" ----

    public boolean isAllAppsChecked() {
        return "true".equalsIgnoreCase(
                fluentPresenceOfElementLocated(allAppsCheckbox).getAttribute("checked"));
    }

    /** Ensures the master "all apps run through VPN" is on (baseline where every app is included). */
    @Step("Ensure the 'all apps' master checkbox is enabled")
    public SplitTunnelingPage ensureAllAppsIncluded() {
        if (!isAllAppsChecked()) {
            fluentPresenceOfElementLocated(allAppsCheckbox).click();
            for (int i = 0; i < 10 && !isAllAppsChecked(); i++) {
                pause(Duration.ofMillis(300));
            }
        }
        Assert.assertTrue(isAllAppsChecked(), "Could not enable the 'all apps' master checkbox");
        return this;
    }

    // ---- per-app (by index) ----

    /** Checked state of a specific app, addressed by its row index (Nth name ↔ Nth checkbox). */
    @Step("Read checkbox state of app '{name}'")
    public boolean isAppChecked(String name) {
        return "true".equalsIgnoreCase(switchOf(name).getAttribute("checked"));
    }

    /** True when {@code name} is excluded from the VPN (its checkbox is unchecked). */
    public boolean isAppExcluded(String name) {
        return !isAppChecked(name);
    }

    @Step("Exclude app '{name}' from the VPN")
    public SplitTunnelingPage excludeApp(String name) {
        setApp(name, false);
        Assert.assertFalse(isAppChecked(name), "App '" + name + "' is still included after excluding");
        return this;
    }

    @Step("Include app '{name}' into the VPN")
    public SplitTunnelingPage includeApp(String name) {
        setApp(name, true);
        Assert.assertTrue(isAppChecked(name), "App '" + name + "' is still excluded after including");
        return this;
    }

    @Step("Go back from Split tunneling")
    public MainScreenPage goBack() {
        dpad.back();
        return new MainScreenPage(testContext);
    }

    // ---- helpers ----

    private void setApp(String name, boolean desiredChecked) {
        if (isAppChecked(name) == desiredChecked) {
            return;
        }
        // On this screen a first click only moves focus onto the row; a second click toggles it.
        switchOf(name).click();          // 1st: focus the target row
        pause(Duration.ofMillis(400));
        switchOf(name).click();          // 2nd: toggle its checkbox
        for (int i = 0; i < 10 && isAppChecked(name) != desiredChecked; i++) {
            pause(Duration.ofMillis(300));
        }
    }

    /** The checkbox element for {@code name}: the Nth switch matching the Nth app name. */
    private WebElement switchOf(String name) {
        int index = appNames().indexOf(name);
        if (index < 0) {
            throw new NoSuchElementException("App '" + name + "' not visible in the split-tunneling list");
        }
        List<WebElement> switches = appiumDriver.findElements(appSwitch);
        if (index >= switches.size()) {
            throw new NoSuchElementException(
                    "No checkbox at index " + index + " for app '" + name + "' (" + switches.size() + " switches)");
        }
        return switches.get(index);
    }

    private List<String> appNames() {
        return appiumDriver.findElements(appName).stream()
                .map(e -> e.getText().trim())
                .collect(Collectors.toList());
    }
}
