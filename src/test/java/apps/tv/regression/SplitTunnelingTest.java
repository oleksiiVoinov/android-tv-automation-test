package apps.tv.regression;

import apps.BaseTest;
import apps.tv.pages.MainScreenPage;
import apps.tv.pages.SplitTunnelingPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Settings menu → "Split Tunneling" (TvAppsManagerActivity). Validates the screen, and that
 * excluding a single app affects only that app and persists after reopening the screen.
 * Uses "Appium Settings" as the target (always installed) and "Apple TV" as a control app.
 */
@Epic("Android TV")
@Feature("4. Settings menu")
public class SplitTunnelingTest extends BaseTest {

    private static final String TARGET_APP = "Appium Settings";
    private static final String CONTROL_APP = "Apple TV";   // must stay included when we exclude the target

    /** Baseline: the master "all apps" checkbox on → every app included. Runs after BaseTest.tearUp. */
    @BeforeClass
    public void ensureBaseline() {
        new MainScreenPage(testContext)
                .navigateToMainScreen()
                .openSettingsMenu()
                .openSplitTunneling()
                .ensureAllAppsIncluded()
                .goBack();
    }

    @Test(priority = 1, description = "Split tunneling screen opens and shows the app list")
    @Story("Split Tunneling")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Objective: verify the 'Split Tunneling' menu item opens the app-manager screen

            Steps:
            1. go to the main screen
            2. open the settings menu (gear) → Split Tunneling
            3. verify the master 'all apps' checkbox, the app list and the Back control""")
    public void validateSplitTunnelingDisplayed() {
        new MainScreenPage(testContext)
                .navigateToMainScreen()
                .openSettingsMenu()
                .openSplitTunneling()
                .verifyDisplayed()
                .goBack();
    }

    @Test(priority = 2, description = "Excluding one app affects only that app and persists")
    @Story("Split Tunneling")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Objective: verify excluding a single app unchecks only that app (not the whole list)
            and that the exclusion persists across reopening the screen

            Pre-cond: @BeforeClass enables the 'all apps' master, so every app starts included.

            Steps:
            1. open Split Tunneling; assert the target and control apps start included
            2. exclude the target app (tap its checkbox); assert the target is now excluded AND
               the control app is unchanged
            3. go back to the main screen, reopen Split Tunneling
            4. assert the target app is still excluded (persisted)
            5. restore the baseline (all apps included)""")
    public void excludingOneAppAffectsOnlyThatApp() {
        SplitTunnelingPage page = new MainScreenPage(testContext)
                .navigateToMainScreen()
                .openSettingsMenu()
                .openSplitTunneling();

        Assert.assertTrue(page.isAppChecked(TARGET_APP),
                "Precondition: '" + TARGET_APP + "' should start included");
        Assert.assertTrue(page.isAppChecked(CONTROL_APP),
                "Precondition: '" + CONTROL_APP + "' should start included");

        page.excludeApp(TARGET_APP);
        Assert.assertFalse(page.isAppChecked(TARGET_APP), "'" + TARGET_APP + "' should be excluded");
        Assert.assertTrue(page.isAppChecked(CONTROL_APP),
                "Control app '" + CONTROL_APP + "' must stay included when excluding another app");
        page.goBack();

        // navigateToMainScreen() is safe from any screen — the Navigator backs out of split tunneling.
        boolean stillExcluded = new MainScreenPage(testContext)
                .navigateToMainScreen()
                .openSettingsMenu()
                .openSplitTunneling()
                .isAppExcluded(TARGET_APP);
        Assert.assertTrue(stillExcluded, "'" + TARGET_APP + "' should stay excluded after reopening");

        // Restore the baseline for the next run.
        new MainScreenPage(testContext)
                .navigateToMainScreen()
                .openSettingsMenu()
                .openSplitTunneling()
                .ensureAllAppsIncluded()
                .goBack();
    }
}
