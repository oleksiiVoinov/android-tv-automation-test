package apps.tv.pages;

import apps.tv.api.TvWebAuth;
import driver.TestContext;
import io.appium.java_client.android.nativekey.AndroidKey;
import io.qameta.allure.Step;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * Welcome / Sign-in screens of the Android TV app.
 * Used by the precondition to bring the app from a clean-slate (logged-out) state
 * to a signed-in state via the API device-code flow (see {@link TvWebAuth}) — no manual QR.
 */
public class TvSignInPage extends TvBasePage {

    private static final String PKG = "com.free.vpn.super.hotspot.open:id/";

    public final By signInButton = By.id(PKG + "btn_sign_in");   // welcome screen
    public final By signInCode = By.id(PKG + "tv_sign_in_code");  // sign-in screen (device code)
    public final By connectButton = By.id(PKG + "tvConnectButton"); // main screen

    public TvSignInPage(TestContext testContext) {
        super(testContext);
    }

    public boolean isSignedIn() {
        return isPresent(connectButton);
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

        dpad.focus(signInButton, AndroidKey.DPAD_DOWN, 3).center();

        String code = fluentVisibility(signInCode, Duration.ofSeconds(15)).getText().trim();
        System.out.println("📺 TV device code: " + code);

        TvWebAuth.dev().signInWithCode(email, password, code);

        // The app polls its auth session; wait for it to complete and show the main screen.
        fluentVisibility(connectButton, Duration.ofSeconds(40));
        System.out.println("✅ TV signed in via API");
    }
}
