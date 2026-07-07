package apps.tv.pages;

import apps.tv.api.WebAuth;
import driver.TestContext;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.testng.Assert;

import java.time.Duration;

/**
 * Welcome / Sign-in screens of the Android TV app (locators verified on device).
 * Also drives the API device-code sign-in (see {@link WebAuth}) — no manual QR.
 */
public class SignInPage extends BasePage {

    private static final String PKG = "com.free.vpn.super.hotspot.open:id/";

    // Welcome screen (TvWelcomeActivity)
    public final By welcomeHeadline = By.id(PKG + "tv_welcome_headline");
    public final By signInButton = By.id(PKG + "btn_sign_in");
    public final By signUpButton = By.id(PKG + "btn_sign_up");
    // Sign-in screen (TvSignInActivity)
    public final By signInHeadline = By.id(PKG + "tv_sign_in_headline"); // "Scan the QR code to sign in"
    public final By signInLink = By.id(PKG + "tv_sign_in_link");         // "https://vpnsuper.com/tv"
    public final By signInCode = By.id(PKG + "tv_sign_in_code");         // device code
    public final By signInQr = By.id(PKG + "iv_sign_in_qr");
    public final By backButton = By.id(PKG + "btn_back");
    // Sign-up screen (TvSignUpActivity)
    public final By signUpQr = By.id(PKG + "iv_sign_up_qr");
    public final By signInInsteadButton = By.id(PKG + "btn_signIn_instead");
    // Main screen (post-login)
    public final By connectButton = By.id(PKG + "tvConnectButton");

    public SignInPage(TestContext testContext) {
        super(testContext);
    }

    public boolean isSignedIn() {
        return isPresent(connectButton);
    }

    /** Robustly reach the welcome screen — screen detection + steps live in {@link Navigator}. */
    @Step("Go to the welcome screen")
    public SignInPage navigateToWelcome() {
        return new Navigator(testContext).toWelcome();
    }

    @Step("Verify welcome screen elements are displayed")
    public SignInPage verifyWelcomeDisplayed() {
        Assert.assertTrue(isDisplayed(welcomeHeadline), "Welcome headline not displayed");
        Assert.assertTrue(isDisplayed(signInButton), "Sign in button not displayed");
        Assert.assertTrue(isDisplayed(signUpButton), "Sign up button not displayed");
        return this;
    }

    @Step("Open the Sign in screen")
    public SignInPage openSignIn() {
        dpad.focusOnAndSelect(signInButton);
        fluentVisibility(signInCode, Duration.ofSeconds(15));
        return this;
    }

    @Step("Verify sign-in screen elements are displayed (QR, code, link)")
    public SignInPage verifySignInDisplayed() {
        Assert.assertTrue(isDisplayed(signInHeadline), "Sign-in headline not displayed");
        Assert.assertTrue(isDisplayed(signInQr), "QR code not displayed");
        Assert.assertTrue(isDisplayed(signInCode), "Device code not displayed");
        Assert.assertFalse(textOf(signInCode).isBlank(), "Device code is empty");
        Assert.assertTrue(isDisplayed(signInLink), "Sign-in web link not displayed");
        return this;
    }

    @Step("Open the Sign up screen")
    public SignInPage openSignUp() {
        dpad.focusOnAndSelect(signUpButton);
        fluentVisibility(signUpQr, Duration.ofSeconds(15));
        return this;
    }

    @Step("Verify sign-up screen elements are displayed (QR, Sign In Instead)")
    public SignInPage verifySignUpDisplayed() {
        Assert.assertTrue(isDisplayed(signUpQr), "Sign-up QR not displayed");
        Assert.assertTrue(isDisplayed(signInInsteadButton), "'Sign In Instead' button not displayed");
        Assert.assertTrue(isDisplayed(backButton), "Back button not displayed");
        return this;
    }

    /** Taps "Sign In Instead" on the sign-up screen and verifies the redirect to the sign-in screen. */
    @Step("Tap 'Sign In Instead' → expect the Sign in screen")
    public SignInPage tapSignInInstead() {
        dpad.focusOnAndSelect(signInInsteadButton);
        // Sign-in screen is confirmed by its unique device-code element.
        fluentVisibility(signInCode, Duration.ofSeconds(15));
        return this;
    }

    /** Full login: open Sign in, read the device code, approve it via the account API, land on main. */
    @Step("Log in via device code (API approve)")
    public MainScreenPage login(String email, String password) {
        openSignIn();
        String code = textOf(signInCode);
        System.out.println("📺 TV device code: " + code);
        WebAuth.forEnvironment().signInWithCode(email, password, code);
        fluentVisibility(connectButton, Duration.ofSeconds(40));
        System.out.println("✅ TV signed in via API");
        return new MainScreenPage(testContext);
    }

    /**
     * Guarantees the app is signed in. If already on the main screen, returns immediately.
     * Otherwise opens Sign in, reads the device code and approves it via the account API,
     * then waits for the app to land on the main screen.
     */
    @Step("Ensure the TV is signed in (API device-code sign-in if needed)")
    public void ensureSignedIn(String email, String password) {
        if (isSignedIn()) {
            return;
        }

        // On the welcome screen — open Sign in. If already on the sign-in screen, skip this.
        if (isPresent(signInButton)) {
            dpad.focusOnAndSelect(signInButton);
        }

        String code = fluentVisibility(signInCode, Duration.ofSeconds(15)).getText().trim();
        System.out.println("📺 TV device code: " + code);

        WebAuth.forEnvironment().signInWithCode(email, password, code);

        // The app polls its auth session; wait for it to complete and show the main screen.
        fluentVisibility(connectButton, Duration.ofSeconds(40));
        System.out.println("✅ TV signed in via API");
    }
}
