package apps.tv.regression;

import apps.BaseTest;
import apps.tv.pages.MainScreenPage;
import configs.environment.envconfig.DevConfig;
import configs.environment.envconfig.ProdConfig;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;

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
    public void validationPage() {
        new MainScreenPage(testContext)
                .navigateToMainScreen()
                .verifyMainScreenDisplayed();
    }

    @Test(priority = 2, description = "checking for the existence of a debug menu")
    @Story("2. Main screen")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Objective: Check for the existence of a debug menu
                       For dev, a debug menu should exist
                       For production, a debug menu should not exist
            
            Steps:
            1. go to main screen
            2. hold down the connect button 3 second
            2. validate if debug menu exist""")
    public void validationDebug() {
        if (testContext.getEnvironment() instanceof DevConfig) {

            Assert.assertTrue(new MainScreenPage(testContext)
                            .navigateToMainScreen()
                            .holdConnect(Duration.ofSeconds(3))
                            .verifyDebugMenu(),
                    "debug menu not exist on debug version");

        } else if (testContext.getEnvironment() instanceof ProdConfig) {

            Assert.assertFalse(new MainScreenPage(testContext)
                            .navigateToMainScreen()
                            .holdConnect(Duration.ofSeconds(3))
                            .verifyDebugMenu(),
                    "debug menu exist on debug version");

        }
    }
}
