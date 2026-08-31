package apps.tv.pages;

import driver.TestContext;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.testng.Assert;

import java.time.Duration;

/**
 * The dialog raised by "Restore purchase" on the TV sign-in screen
 * ({@code dialog_generic_confirmation_layout_tv} — the same shell the sign-out confirmation uses,
 * here with a single "Okay" button).
 *
 * <p>Two outcomes, both verified on device:
 * <ul>
 *   <li>no Google Play subscription on the account → "No subscription found" /
 *       "You do not have any subscription to restore"; Okay keeps the user on the sign-in screen;</li>
 *   <li>an active subscription → "Subscription Restored"; Okay opens the main screen
 *       (premium without an account).</li>
 * </ul>
 */
public class RestorePurchasePage extends BasePage {

    private static final String PKG = "com.free.vpn.super.hotspot.open:id/";

    public final By dialogRoot = By.id(PKG + "dialog_root_view");
    public final By title = By.id(PKG + "tv_dialog_title");
    public final By subTitle = By.id(PKG + "tv_dialog_sub_title");
    public final By okButton = By.id(PKG + "action_positive_btn");

    public static final String NO_SUBSCRIPTION_TITLE = "No subscription found";
    public static final String NO_SUBSCRIPTION_MESSAGE = "You do not have any subscription to restore";
    public static final String RESTORED_TITLE = "Subscription Restored";
    public static final String OK = "Okay";

    public RestorePurchasePage(TestContext testContext) {
        super(testContext);
    }

    /** Restoring goes through Google Play, so give it a generous window. */
    @Step("Wait for the restore-purchase dialog")
    public RestorePurchasePage waitDialog() {
        fluentVisibility(okButton, Duration.ofSeconds(60));
        return this;
    }

    public String title() {
        return textOf(title);
    }

    /** True when the account has nothing to restore (used to guard the negative test). */
    public boolean isNoSubscription() {
        return NO_SUBSCRIPTION_TITLE.equalsIgnoreCase(title());
    }

    @Step("Verify the 'no subscription' dialog")
    public RestorePurchasePage verifyNoSubscription() {
        Assert.assertTrue(isDisplayed(dialogRoot), "Restore dialog not displayed");
        Assert.assertEquals(title(), NO_SUBSCRIPTION_TITLE, "Wrong restore dialog title");
        Assert.assertEquals(textOf(subTitle), NO_SUBSCRIPTION_MESSAGE, "Wrong restore dialog message");
        Assert.assertEquals(textOf(okButton), OK, "Wrong confirmation button on the restore dialog");
        attachScreenToReport("Restore purchase — no subscription");
        return this;
    }

    @Step("Verify the 'subscription restored' dialog")
    public RestorePurchasePage verifyRestored() {
        Assert.assertTrue(isDisplayed(dialogRoot), "Restore dialog not displayed");
        Assert.assertEquals(title(), RESTORED_TITLE, "Wrong restore dialog title");
        Assert.assertEquals(textOf(okButton), OK, "Wrong confirmation button on the restore dialog");
        attachScreenToReport("Restore purchase — restored");
        return this;
    }

    /** Okay after a failed restore — the app stays on the sign-in screen. */
    @Step("Acknowledge (Okay) → stay on the sign-in screen")
    public SignInPage acknowledge() {
        dpad.focusOnAndSelect(okButton);
        SignInPage signIn = new SignInPage(testContext);
        signIn.fluentVisibility(signIn.signInCode, Duration.ofSeconds(20));
        return signIn;
    }

    /** Okay after a successful restore — the app opens the main screen as a premium user. */
    @Step("Acknowledge (Okay) → main screen")
    public MainScreenPage acknowledgeToMainScreen() {
        dpad.focusOnAndSelect(okButton);
        MainScreenPage main = new MainScreenPage(testContext);
        main.fluentVisibility(main.connectButton, Duration.ofSeconds(60));
        return main;
    }
}
