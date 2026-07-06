package apps.tv.regression;

import apps.BaseTest;
import apps.common.CommandsADB;
import apps.tv.pages.SignInPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Sign-up screen tests. Uses BaseTest's driver lifecycle + recording, starts from a clean slate
 * (precondition wipes data → welcome) with no auto sign-in. Validates the sign-up screen and checks
 * that "Sign In Instead" redirects to the Sign in screen.
 */
@Epic("Android TV")
@Feature("1. Sign up")
public class SignUpTest extends BaseTest {

    // Runs after BaseTest.tearUp (@BeforeClass: superclass before subclass) — driver is ready here.
    @BeforeClass
    public void resetToWelcome() {
        new CommandsADB()
                .clearAppData(device.app.appPackage, device.uDID)
                .allowVpnConnection(device.app.appPackage, device.uDID);
        testContext.getAndroidDriver().activateApp(device.app.appPackage);
    }

    @Test(priority = 1, description = "validate sign-up screen elements")
    @Story("1. Sign up")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Objective: verify the sign-up screen shows the QR and its controls

            Steps:
            1. go to the welcome screen
            2. open Sign up
            3. verify QR, 'Sign In Instead' and Back are displayed""")
    public void validateSignUp() {
        new SignInPage(testContext)
                .navigateToWelcome()
                .openSignUp()
                .verifySignUpDisplayed();
    }

    @Test(priority = 2, description = "'Sign In Instead' redirects to the Sign in screen")
    @Story("1. Sign up")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Objective: verify 'Sign In Instead' on the sign-up screen redirects to Sign in

            Steps:
            1. go to the welcome screen
            2. open Sign up
            3. tap 'Sign In Instead'
            4. verify the Sign in screen is shown (device code visible)""")
    public void signInInsteadRedirects() {
        new SignInPage(testContext)
                .navigateToWelcome()
                .openSignUp()
                .tapSignInInstead()
                .verifySignInDisplayed();
    }
}
