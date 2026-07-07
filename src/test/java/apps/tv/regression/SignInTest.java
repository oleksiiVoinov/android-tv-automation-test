package apps.tv.regression;

import apps.BaseTest;
import apps.common.CommandsADB;
import apps.tv.pages.SignInPage;
import configs.RuntimeConfig;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Login-screen tests. Uses BaseTest's driver lifecycle + recording, but starts from a clean slate
 * (precondition wipes data → welcome) and drives the login screens itself (no auto sign-in).
 * The last test performs the actual login and lands on the main screen.
 */
@Epic("Android TV")
@Feature("1. Login")
public class SignInTest extends BaseTest {

    // Runs after BaseTest.tearUp (@BeforeClass: superclass before subclass) — driver is ready here.
    @BeforeClass
    public void resetToWelcome() {
        // Wipe (logs out → welcome) + re-grant VPN consent, then relaunch the already-open app.
        new CommandsADB()
                .clearAppData(device.app.appPackage, device.uDID)
                .allowVpnConnection(device.app.appPackage, device.uDID);
        testContext.getAndroidDriver().activateApp(device.app.appPackage);
    }

    @Test(priority = 1, description = "validate welcome screen elements")
    @Story("1. Login")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Objective: verify the welcome screen shows headline, Sign in and Sign up

            Steps:
            1. go to the welcome screen
            2. verify headline, Sign in and Sign up are displayed""")
    public void validateWelcome() {
        new SignInPage(testContext)
                .navigateToWelcome()
                .verifyWelcomeDisplayed();
    }

    @Test(priority = 2, description = "validate sign-in screen elements")
    @Story("1. Login")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Objective: verify the sign-in screen shows the QR, device code and web link

            Steps:
            1. go to the welcome screen
            2. open Sign in
            3. verify headline, QR, device code and link are displayed""")
    public void validateSignIn() {
        new SignInPage(testContext)
                .navigateToWelcome()
                .openSignIn()
                .verifySignInDisplayed();
    }

    @Test(priority = 3, description = "log in to the app via device code")
    @Story("1. Login")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Objective: log in to the app and reach the main screen

            Steps:
            1. go to the welcome screen
            2. open Sign in and read the device code
            3. approve the code via the account API (verify → authorize)
            4. verify the app lands on the main screen""")
    public void signIn() {
        new SignInPage(testContext)
                .navigateToWelcome()
                .login(RuntimeConfig.getRequired("tvEmail"), RuntimeConfig.getRequired("tvPassword"))
                .verifyDisconnected();
    }
}
