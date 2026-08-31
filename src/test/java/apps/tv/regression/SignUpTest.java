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
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * The sign-up entry point of the TV app — what the welcome screen's "Sign up" button actually does.
 *
 * <p><b>The flow changed.</b> The build no longer offers account creation on TV: "Sign up" opens
 * {@code TvPaywallActivity} instead of the old sign-up QR screen ({@code TvSignUpActivity} is still
 * in the manifest but has no entry point left — not from welcome, not from sign-in, not from the
 * settings menu), and the app tells users to create an account in the mobile app. These two tests
 * are the actualised versions of the previous "validate sign-up screen" / "'Sign In Instead'
 * redirects" pair: the first pins the new destination and asserts the old screen is gone, the
 * second covers the way from there to signing in.
 *
 * <p>The pay wall's own content is verified by {@link PayWallTest} — this class only checks how it
 * is reached and left.
 */
@Epic("Android TV")
@Feature("2. Sign up")
public class SignUpTest extends BaseTest {

    // Runs after BaseTest.tearUp (@BeforeClass: superclass before subclass) — driver is ready here.
    @Story("02. Sign up")
    @BeforeClass
    public void resetToWelcome() {
        // Wipe (logs out → welcome) + re-grant VPN consent, then relaunch the already-open app.
        new CommandsADB()
                .clearAppData(device.app.appPackage, device.uDID)
                .allowVpnConnection(device.app.appPackage, device.uDID);
        testContext.getAndroidDriver().activateApp(device.app.appPackage);
    }

    @Test(priority = 1, description = "'Sign up' opens the pay wall, not the old sign-up QR screen")
    @Story("02. Sign up")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Objective: verify that account creation is no longer offered on TV — the welcome
            screen's "Sign up" button opens the pay wall

            Steps:
            1. go to the welcome screen
            2. press Sign up
            3. verify the pay wall is shown (root + "Choose your Premium plan" + the three plans)
            4. verify the legacy sign-up screen is NOT shown (no sign-up QR, no 'Sign In Instead')""")
    public void signUpOpensPayWall() {
        SignInPage welcome = new SignInPage(testContext).navigateToWelcome();

        welcome.openPayWall().verifyPayWallShown();

        Assert.assertFalse(welcome.isLegacySignUpScreenShown(),
                "The legacy TV sign-up screen is shown again — the pay wall was expected. "
                        + "If the build brought account creation back to TV, this test must be rewritten.");
    }

    @Test(priority = 2, description = "Back on the pay wall returns to welcome, where Sign in still works")
    @Story("02. Sign up")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Objective: verify a user who does not want to subscribe can leave the pay wall and
            sign in instead

            Steps:
            1. go to the welcome screen
            2. press Sign up — the pay wall opens
            3. press Back on the pay wall
            4. verify the welcome screen (headline, Sign in, Sign up)
            5. press Sign in and verify the sign-in screen (QR, device code, link, Restore purchase)""")
    public void payWallBackLeadsToSignIn() {
        new SignInPage(testContext)
                .navigateToWelcome()
                .openPayWall()
                .goBack()
                .verifyWelcomeDisplayed()
                .openSignIn()
                .verifySignInDisplayed();
    }
}
