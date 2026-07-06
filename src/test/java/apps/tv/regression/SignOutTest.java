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
 * Settings menu → "Sign Out". Two cases:
 * 1) Decline on the confirmation dialog keeps the user signed in (back on main).
 * 2) Confirm signs out → "You've been signed out" screen → Okay → welcome screen.
 * <p>
 * The sign-out case runs last (priority 2) because it leaves the app signed out; the next test
 * class logs back in automatically via the Navigator.
 */
@Epic("Android TV")
@Feature("4. Settings menu")
public class SignOutTest extends BaseTest {

    @Test(priority = 1, description = "Decline on the sign-out dialog keeps the user signed in")
    @Story("Sign Out")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Objective: verify declining the sign-out confirmation keeps the user signed in

            Steps:
            1. go to the main screen
            2. open the settings menu (gear) → Sign Out
            3. verify the confirmation dialog (title + Confirm / Decline)
            4. press Decline
            5. verify the app stays on the main screen (still signed in)""")
    public void declineKeepsSignedIn() {
        new MainScreenPage(testContext)
                .navigateToMainScreen()
                .openSettingsMenu()
                .signOut()
                .verifyConfirmDialog()
                .decline()
                .verifyOnMainScreen();
    }

    @Test(priority = 2, description = "Confirm sign out → signed-out screen → welcome")
    @Story("Sign Out")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Objective: verify the full sign-out flow lands on the welcome screen (signed out)

            Steps:
            1. go to the main screen
            2. open the settings menu (gear) → Sign Out
            3. verify the confirmation dialog, press Confirm
            4. verify the 'You've been signed out' screen, press Okay
            5. verify the welcome screen is shown (signed out)""")
    public void signOut() {
        new MainScreenPage(testContext)
                .navigateToMainScreen()
                .openSettingsMenu()
                .signOut()
                .verifyConfirmDialog()
                .confirm()
                .verifySignedOutScreen()
                .acknowledge()
                .verifyWelcomeDisplayed();
    }
}
