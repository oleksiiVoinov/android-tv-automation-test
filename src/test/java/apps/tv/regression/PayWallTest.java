package apps.tv.regression;

import apps.BaseTest;
import apps.common.CommandsADB;
import apps.tv.pages.PayWallPage;
import apps.tv.pages.PayWallPage.Plan;
import apps.tv.pages.RestorePurchasePage;
import apps.tv.pages.SignInPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Pay wall (TvPaywallActivity) and the Google Play purchase / restore flows.
 *
 * <p>The class starts from a clean install (wiped app data → welcome screen) and the four tests
 * run as a chain, because Google Play subscription state is what links them:
 * <ol>
 *   <li>the pay wall itself — layout, the three plans and what the focused plan changes;</li>
 *   <li>Restore purchase while the Play account owns nothing → "No subscription found";</li>
 *   <li>buying the Weekly plan → the app opens the main screen as premium;</li>
 *   <li>Restore purchase after that purchase → "Subscription Restored" → main screen.</li>
 * </ol>
 *
 * <p><b>Purchases are licence-test purchases.</b> The Google account on the box is a licence
 * tester, so Play shows "Test card, always approves" and never charges;
 * {@code PlayBillingPage.verifySheet} asserts that, so a box signed in with a real account fails
 * instead of buying something. A test weekly subscription renews every ~5 minutes and expires on
 * its own within roughly half an hour — no cleanup is done here. Re-running the suite inside that
 * window would find a live subscription, so test 2 skips itself (instead of failing) when the
 * account still owns one.
 */
@Epic("Android TV")
@Feature("8. Pay wall")
//https://superunlimited.atlassian.net/browse/AB-3629
public class PayWallTest extends BaseTest {

    /** Runs after BaseTest.tearUp (@BeforeClass: superclass first) — the driver is ready here. */
    @Story("12. Pay wall")
    @BeforeClass
    public void resetToWelcome() {
        freshStart();
    }

    /** Wipes app data (→ welcome screen) and relaunches. The Play subscription is not affected. */
    private void freshStart() {
        new CommandsADB()
                .clearAppData(device.app.appPackage, device.uDID)
                .allowVpnConnection(device.app.appPackage, device.uDID);
        testContext.getAndroidDriver().activateApp(device.app.appPackage);
    }

    @Test(priority = 1, description = "validate the pay wall elements and the three plans")
    @Story("12. Pay wall")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Objective: verify the pay wall shows the headline, the six benefits, the Back button
            and the Weekly / Monthly / Yearly plans, and that focusing a plan updates its badge,
            its CTA and the footer wording

            Steps:
            1. go to the welcome screen
            2. press Sign up — the pay wall opens
            3. verify headline, benefits, Back and the three plan cards (title, price, period)
            4. focus each plan and verify its badge, the 'Purchase' CTA and the matching footer
            5. leave the pay wall with Back and verify the welcome screen""")
    public void validatePayWall() {
        PayWallPage payWall = new SignInPage(testContext)
                .navigateToWelcome()
                .openPayWall()
                .verifyPayWallDisplayed();

        for (Plan plan : Plan.values()) {
            payWall.focusPlan(plan).verifyPlanFocused(plan);
        }

        payWall.goBack().verifyWelcomeDisplayed();
    }

    @Test(priority = 2, description = "Restore purchase without a subscription shows the 'no subscription' dialog")
    @Story("12. Pay wall")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Objective: verify that Restore purchase on an account without a Google Play
            subscription reports it and keeps the user on the sign-in screen

            Steps:
            1. go to the welcome screen
            2. press Sign in
            3. press 'Restore purchase'
            4. verify the dialog: 'No subscription found' /
               'You do not have any subscription to restore' / 'Okay'
            5. press Okay and verify the app stays on the sign-in screen

            Skipped (not failed) when the Play account still owns a live test subscription from a
            previous run — those expire on their own within about half an hour.""")
    public void restorePurchaseWithoutSubscription() {
        SignInPage signIn = new SignInPage(testContext)
                .navigateToWelcome()
                .openSignIn();

        RestorePurchasePage dialog = signIn.tapRestorePurchase();

        if (!dialog.isNoSubscription()) {
            // A test subscription from an earlier run is still alive — restore succeeds instead.
            dialog.acknowledgeToMainScreen();
            freshStart();
            throw new SkipException("The Google Play account still owns an active subscription ('"
                    + RestorePurchasePage.RESTORED_TITLE + "'), so 'no subscription' cannot be verified. "
                    + "Test subscriptions expire on their own — re-run in ~30 minutes.");
        }

        dialog.verifyNoSubscription()
                .acknowledge()
                .verifySignInDisplayed();
    }

    @Test(priority = 3, description = "buy the Weekly plan through Google Play")
    @Story("12. Pay wall")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Objective: verify a Weekly subscription can be purchased from the pay wall and that the
            app becomes premium and opens the main screen

            Steps:
            1. go to the welcome screen
            2. press Sign up — the pay wall opens
            3. focus the Weekly plan and press OK — Google Play opens its TV billing sheet
            4. verify the sheet offers 'VPN Super Weekly Premium' as a TEST purchase
               (test card, no charge)
            5. press Subscribe
            6. verify the app closes the pay wall by itself and shows the main screen""")
    public void purchaseWeeklySubscription() {
        new SignInPage(testContext)
                .navigateToWelcome()
                .openPayWall()
                .purchase(Plan.WEEKLY)
                .verifySheet(Plan.WEEKLY)
                .subscribe()
                .waitPurchaseCompleted()
                .verifyOnMainScreen();
    }

    @Test(priority = 4, description = "Restore purchase with an active subscription opens the main screen")
    @Story("12. Pay wall")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Objective: verify that Restore purchase brings back the subscription bought in the
            previous test on a freshly wiped app and lands on the main screen

            Steps:
            1. wipe app data and relaunch (the Google Play subscription survives) → welcome screen
            2. press Sign in
            3. press 'Restore purchase'
            4. verify the dialog: 'Subscription Restored' / 'Okay'
            5. press Okay and verify the app opens the main screen""")
    public void restorePurchaseWithSubscription() {
        freshStart();

        new SignInPage(testContext)
                .navigateToWelcome()
                .openSignIn()
                .tapRestorePurchase()
                .verifyRestored()
                .acknowledgeToMainScreen()
                .verifyOnMainScreen();
    }
}
