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
 * Settings menu → "Privacy Notice" opens its own screen (TvPrivacyNoticeActivity).
 * Validates the redirect: headline, privacy-notice URL, QR and the Go back control.
 */
@Epic("Android TV")
@Feature("4. Settings menu")
public class PrivacyNoticeTest extends BaseTest {

    @Test(priority = 1, description = "Privacy Notice menu item opens the Privacy Notice screen")
    @Story("Privacy Notice")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Objective: verify the 'Privacy Notice' menu item redirects to the Privacy Notice screen

            Steps:
            1. go to the main screen
            2. open the settings menu (gear)
            3. open 'Privacy Notice'
            4. verify the screen: headline 'Privacy Notice', privacy URL, QR and 'Go back'""")
    public void openPrivacyNotice() {
        new MainScreenPage(testContext)
                .navigateToMainScreen()
                .openSettingsMenu()
                .openPrivacyNotice()
                .verifyDisplayed()
                .goBack();
    }
}
