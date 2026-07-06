package apps.tv.regression;

import apps.BaseTest;
import apps.tv.pages.MainScreenPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

@Epic("Android TV")
public class MainScreenPageTest extends BaseTest {

    @Test(priority = 1, description = "validation feature TV main screen")
    @Story("2. Main screen")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Objective: verify the TV main screen shows all expected elements after sign-in

            Steps:
            1. go to main screen
            2. verify all elements are displayed: app name, settings, connect button + status,
               location selector, protocol grid (Auto / IKEv2 / OpenVPN), IP and time cards""")
    public void validation() {
        new MainScreenPage(testContext)
                .navigateToMainScreen()
                .verifyMainScreenDisplayed();
    }
}
