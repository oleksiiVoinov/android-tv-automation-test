package apps.tv.pages;

import apps.tv.api.WebAuth;
import apps.tv.web.Browser;
import driver.TestContext;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.testng.Assert;

import java.time.Duration;
import java.util.List;

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
    public final By signInSubtitle = By.id(PKG + "tv_sign_in_subtitle"); // "Trouble scanning? Go to"
    public final By signInLink = By.id(PKG + "tv_sign_in_link");         // "https://vpnsuper.com/tv"
    public final By signInSubtitleLine2 = By.id(PKG + "tv_sign_in_subtitle_line_2"); // "and use this code:"
    public final By signInCode = By.id(PKG + "tv_sign_in_code");         // device code
    public final By signInQr = By.id(PKG + "iv_sign_in_qr");
    public final By backButton = By.id(PKG + "btn_back");
    /** "Restore purchase" — brings back a Google Play subscription without an account. */
    public final By restorePurchaseButton = By.id(PKG + "btn_restore_purchase");
    // Legacy sign-up screen (TvSignUpActivity). Still in the manifest, but nothing in the TV UI
    // opens it any more — "Sign up" shows the pay wall instead. Kept only so tests can assert its
    // absence; if a build brings the screen back, this is where to hang the flow again.
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
        Assert.assertTrue(isDisplayed(restorePurchaseButton), "'Restore purchase' button not displayed");
        return this;
    }

    /** True while the app still shows the legacy sign-up QR screen (it should not). */
    public boolean isLegacySignUpScreenShown() {
        return isPresent(signUpQr) || isPresent(signInInsteadButton);
    }

    /**
     * Opens the pay wall from the welcome screen. "Sign up" no longer leads to the TV sign-up QR
     * screen — the build shows {@code TvPaywallActivity} instead (see {@link PayWallPage}).
     */
    @Step("Open the pay wall (Sign up)")
    public PayWallPage openPayWall() {
        dpad.focusOnAndSelect(signUpButton);
        return new PayWallPage(testContext).waitLoaded();
    }

    /** Presses "Restore purchase" on the sign-in screen and waits for the resulting dialog. */
    @Step("Tap 'Restore purchase'")
    public RestorePurchasePage tapRestorePurchase() {
        dpad.focusOnAndSelect(restorePurchaseButton);
        return new RestorePurchasePage(testContext).waitDialog();
    }

    
    

    /** Full login: open Sign in, read the device code, approve it via the account API, land on main. */
    @Step("Log in via device code (API approve)")
    public MainScreenPage login(String email, String password) {
        openSignIn();
        String code = textOf(signInCode);
        System.out.println("📺 TV device code: " + code);
        WebAuth.forEnvironment(testContext.getEnvironment()).signInWithCode(email, password, code);
        fluentVisibility(connectButton, Duration.ofSeconds(40));
        System.out.println("✅ TV signed in via API");
        return new MainScreenPage(testContext);
    }

    /**
     * Full login via a real browser (instead of the API): open Sign in on the TV, read the device
     * code, then on the account web frontend — sign in (email → password) and approve the code on
     * the /tv page — and wait for the TV app to land on the main screen.
     */
    @Step("Log in via browser (approve the TV device code on the web)")
    public MainScreenPage loginBrowser(String email, String password) {
        openSignIn();
        System.out.println("📺 TV device code (initial): "
                + fluentVisibility(signInCode, Duration.ofSeconds(15)).getText().trim());

        String base = testContext.getEnvironment().getBaseUrl();
        try (Browser web = new Browser(true)) {          // headful: reCAPTCHA is friendlier than headless
            // The /tv link redirects to the account sign-in (carrying the device context).
            System.out.println("🌐 open /tv");
            web.open(base + "/tv");
            web.sleep(Duration.ofSeconds(2));

            // Cookie-consent banner (CookieYes) — dismiss it (best-effort: Reject, else Accept).
            if (!web.clickButtonByText("Reject All")) {
                web.sleep(Duration.ofSeconds(1));
                web.clickButtonByText("Accept All");
            }
            web.sleep(Duration.ofMillis(600));

            // 1. Sign in: email → Continue → "Continue with Password" → email + password → Sign in.
            System.out.println("🌐 email → Continue");
            web.type(By.cssSelector("input[type='email']"), email);
            System.out.println("   Continue: " + web.clickButtonByText("Continue"));
            web.sleep(Duration.ofSeconds(2));
            System.out.println("🌐 choose 'Continue with Password': " + web.clickButtonByText("Continue with Password"));
            // Wait for the password field to appear, then fill password (email is best-effort).
            web.waitVisible(By.cssSelector("input[name='password']"), Duration.ofSeconds(10));
            web.typeIfPresent(By.cssSelector("input[name='email']:not([type='hidden'])"), email);
            web.type(By.cssSelector("input[name='password']"), password);
            System.out.println("   Sign in: " + web.clickButtonByText("Sign in"));
            web.sleep(Duration.ofSeconds(4));

            // 2. Enter the TV device code. Re-read it FRESH now (the code rotates on the TV, and the
            //    browser login above takes tens of seconds — the initial code would be stale).
            if (!web.url().contains("/tv")) {
                System.out.println("🌐 open /tv (code entry). url was: " + web.url());
                web.open(base + "/tv");
                web.sleep(Duration.ofSeconds(2));
            }
            String code = fluentVisibility(signInCode, Duration.ofSeconds(10)).getText().trim();
            System.out.println("📺 TV device code (fresh): " + code);

            List<WebElement> boxes = web.findAll(By.cssSelector("input[type='text']"));
            System.out.println("🌐 code boxes found: " + boxes.size());
            for (int i = 0; i < code.length() && i < boxes.size(); i++) {
                pause(Duration.ofMillis(300));
                boxes.get(i).click();
                pause(Duration.ofMillis(600));
                boxes.get(i).sendKeys(String.valueOf(code.charAt(i)));
            }
            // Entering the 6th digit auto-submits (the button flips to "Sending…"); click as a fallback.
            web.sleep(Duration.ofSeconds(1));
            web.clickButtonByText("Confirm");
            web.waitVisible(By.xpath("//h1[contains(., 'successfully signed in on TV')]"), Duration.ofSeconds(20));
        }

        fluentVisibility(connectButton, Duration.ofSeconds(60));
        System.out.println("✅ TV signed in via browser");
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

        WebAuth.forEnvironment(testContext.getEnvironment()).signInWithCode(email, password, code);

        // The app polls its auth session; wait for it to complete and show the main screen.
        fluentVisibility(connectButton, Duration.ofSeconds(40));
        System.out.println("✅ TV signed in via API");
    }
}
