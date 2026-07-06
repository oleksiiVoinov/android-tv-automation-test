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
 * Settings menu → "Terms of Service" opens its own screen (TvServiceTermsActivity).
 * Validates the redirect: headline, terms-of-service URL, QR and the Go back control.
 */
@Epic("Android TV")
@Feature("4. Settings menu")
public class TermsOfServiceTest extends BaseTest {

    @Test(priority = 1, description = "Terms of Service menu item opens the Terms of Service screen")
    @Story("Terms of Service")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Objective: verify the 'Terms of Service' menu item redirects to the Terms of Service screen

            Steps:
            1. go to the main screen
            2. open the settings menu (gear)
            3. open 'Terms of Service'
            4. verify the screen: headline 'Terms of Service', terms URL, QR and 'Go back'""")
    public void openTermsOfService() {
        new MainScreenPage(testContext)
                .navigateToMainScreen()
                .openSettingsMenu()
                .openTermsService()
                .verifyDisplayed()
                .goBack();
    }
}
