package apps.tv.pages;

import driver.TestContext;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.testng.Assert;

import java.time.Duration;

/**
 * Sign-out flow, reached from the settings menu ("Sign Out"):
 * <ol>
 *   <li>a confirmation dialog ("Are you sure you want to sign out?" — Confirm / Decline),</li>
 *   <li>on Confirm → the "You've been signed out" screen (TvSignOutActivity) with an "Okay" button,</li>
 *   <li>on Okay → back to the welcome screen (signed out).</li>
 * </ol>
 * Locators verified on device.
 */
public class SignOutPage extends BasePage {

    private static final String PKG = "com.free.vpn.super.hotspot.open:id/";

    // Confirmation dialog (overlay on the main screen)
    public final By dialogTitle = By.id(PKG + "tv_dialog_title");
    public final By confirmButton = By.id(PKG + "action_positive_btn");   // "Confirm" (focused)
    public final By declineButton = By.id(PKG + "action_negative_btn");   // "Decline"
    // Signed-out screen (TvSignOutActivity)
    public final By signedOutTitle = By.id(PKG + "sign_out_title");
    public final By signedOutDesc = By.id(PKG + "sign_out_desc");
    public final By okButton = By.id(PKG + "btn_ok");                     // "Okay"

    private static final String CONFIRM_TITLE = "Are you sure you want to sign out?";

    public SignOutPage(TestContext testContext) {
        super(testContext);
    }

    SignOutPage waitConfirmDialog() {
        fluentVisibility(confirmButton, Duration.ofSeconds(15));
        return this;
    }

    @Step("Verify the sign-out confirmation dialog (title + Confirm / Decline)")
    public SignOutPage verifyConfirmDialog() {
        Assert.assertEquals(textOf(dialogTitle), CONFIRM_TITLE, "Wrong sign-out confirmation title");
        Assert.assertTrue(isDisplayed(confirmButton), "'Confirm' button not displayed");
        Assert.assertTrue(isDisplayed(declineButton), "'Decline' button not displayed");
        return this;
    }

    @Step("Confirm sign out")
    public SignOutPage confirm() {
        dpad.focusOnAndSelect(confirmButton);
        fluentVisibility(signedOutTitle, Duration.ofSeconds(15));
        return this;
    }

    @Step("Decline sign out (stay signed in)")
    public MainScreenPage decline() {
        dpad.focusOnAndSelect(declineButton);
        return new MainScreenPage(testContext);
    }

    @Step("Verify the 'signed out' screen (title, description, Okay)")
    public SignOutPage verifySignedOutScreen() {
        Assert.assertTrue(isDisplayed(signedOutTitle), "'Signed out' title not displayed");
        Assert.assertTrue(textOf(signedOutTitle).toLowerCase().contains("signed out"),
                "Unexpected signed-out title: " + textOf(signedOutTitle));
        Assert.assertTrue(isDisplayed(signedOutDesc), "'Signed out' description not displayed");
        Assert.assertTrue(isDisplayed(okButton), "'Okay' button not displayed");
        attachScreenToReport("Signed out");
        return this;
    }

    @Step("Acknowledge sign out (Okay) → welcome screen")
    public SignInPage acknowledge() {
        dpad.focusOnAndSelect(okButton);
        SignInPage welcome = new SignInPage(testContext);
        welcome.fluentVisibility(welcome.signInButton, Duration.ofSeconds(15));
        return welcome;
    }
}
