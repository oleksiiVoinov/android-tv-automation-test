package apps.tv.regression;

import apps.BaseTest;
import apps.tv.pages.MainScreenPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

/**
 * Settings menu → "Help & Support" opens its own screen (TvHelpSupportActivity).
 * Validates the redirect: headline, support URL, QR and the Go back control.
 */
@Epic("Android TV")
@Feature("4. Settings menu")
public class HelpSupportTest extends BaseTest {

    @Test(priority = 1, description = "Help & Support menu item opens the Help & Support screen")
    @Story("Help & Support")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Objective: verify the 'Help & Support' menu item redirects to the Help & Support screen

            Steps:
            1. go to the main screen
            2. open the settings menu (gear)
            3. open 'Help & Support'
            4. verify the screen: headline 'Help and support', support URL, QR and 'Go back'""")
    public void openHelpSupport() {
        new MainScreenPage(testContext)
                .navigateToMainScreen()
                .openSettingsMenu()
                .openHelpSupport()
                .verifyDisplayed()
                .goBack();
    }
}
