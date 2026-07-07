package apps.tv.pages;

import configs.RuntimeConfig;
import driver.TestContext;
import io.qameta.allure.Step;
import org.openqa.selenium.NoSuchElementException;

import java.time.Duration;

/**
 * Screen detection + navigation for the Android TV app — the TV counterpart of the phone
 * {@code apps.multiplatform.pages.multiplatform.Navigator}.
 *
 * <p>The idea is the same as the phone framework: {@link #detectCurrentPage} figures out which
 * {@link Pages screen} the app is on by its unique locators, and the navigation methds
 * ({@link #toMainScreen}, {@link #toWelcome}) loop "detect current page → take one corrective
 * step" until the target screen is reached. This replaces the old blind BACK-loop.
 *
 * <p>Detection reads the page source once per attempt and matches resource-ids in it — one round
 * trip instead of many per-locator {@code findElement} waits.
 */
public class Navigator extends BasePage {

    private static final String PKG = "com.free.vpn.super.hotspot.open:id/";

    // Unique signature id (per screen) used to recognise it in the page source.
    private static final String MAIN_SIG = PKG + "tvConnectButton";
    private static final String WELCOME_SIG = PKG + "btn_sign_in";
    private static final String SIGN_IN_SIG = PKG + "tv_sign_in_code";
    private static final String SIGN_UP_SIG = PKG + "iv_sign_up_qr";
    private static final String SERVER_LIST_SIG = PKG + "tv_title";
    private static final String RECONNECT_DIALOG_SIG = PKG + "action_cancel_btn";
    // Info sub-screens (Help/Privacy/Terms) all carry a "Go back"; the settings popup carries this item;
    // the split-tunneling screen carries the "all apps" master checkbox. All are escapable with BACK.
    private static final String GO_BACK_SIG = PKG + "btn_go_back";
    private static final String SETTINGS_MENU_SIG = PKG + "tv_settings_help_support";
    private static final String SPLIT_TUNNELING_SIG = PKG + "check_all_app";
    // Debug/logger screen (LoggerActivity) — opened by holding Connect ~3s; BACK returns to main.
    private static final String DEBUG_MENU_SIG = PKG + "logs_recycler_view";

    public Navigator(TestContext testContext) {
        super(testContext);
    }

    /** One-shot detection: read the page source once and map it to a {@link Pages}. */
    public Pages detectPage() {
        String source;
        try {
            source = appiumDriver.getPageSource();
        } catch (Exception e) {
            // Instrumentation hiccup / mid-transition — treat as not-yet-known.
            return Pages.UNKNOWN;
        }
        if (source == null || source.isBlank()) {
            return Pages.UNKNOWN;
        }

        // Order matters: a dialog overlays the main screen, and the sign-in/up sub-screens must be
        // recognised before the welcome screen they were opened from.
        if (source.contains(RECONNECT_DIALOG_SIG)) {
            return Pages.RECONNECT_DIALOG;
        }
        if (source.contains(SIGN_UP_SIG)) {
            return Pages.SIGN_UP;
        }
        if (source.contains(SIGN_IN_SIG)) {
            return Pages.SIGN_IN;
        }
        if (source.contains(WELCOME_SIG)) {
            return Pages.WELCOME;
        }
        if (source.contains(SERVER_LIST_SIG)) {
            return Pages.SERVER_LIST;
        }
        if (source.contains(MAIN_SIG)) {
            return Pages.MAIN;
        }
        // Debug/logger screen (opened by a 3s hold on Connect) — escapable with BACK.
        if (source.contains(DEBUG_MENU_SIG)) {
            return Pages.DEBUG_MENU;
        }
        // Settings popup, an info sub-screen (Help/Privacy/Terms) or split tunneling — escapable with BACK.
        if (source.contains(GO_BACK_SIG) || source.contains(SETTINGS_MENU_SIG)
                || source.contains(SPLIT_TUNNELING_SIG)) {
            return Pages.INFO_SCREEN;
        }
        // Splash / loading / transition — nothing actionable yet.
        return Pages.UNKNOWN;
    }

    /**
     * Detects the current page, retrying while the app is on a loading/unknown screen.
     * Mirrors the phone {@code detectCurrentPage(retry, pollingInterval)}.
     */
    @Step("Detect current TV page")
    public Pages detectCurrentPage(int retry, Duration pollingInterval) {
        for (int i = 0; i <= retry; i++) {
            Pages page = detectPage();
            if (page != Pages.UNKNOWN) {
                System.out.println("🧭 detected page: " + page);
                return page;
            }
            pause(pollingInterval);
        }
        System.out.println("🧭 detected page: UNKNOWN (gave up after " + retry + " polls)");
        return Pages.UNKNOWN;
    }

    /**
     * Drives the app to the main connect screen from wherever it is: logs in from the welcome /
     * sign-in screens, cancels the reconnect dialog, backs out of the server list / sign-up, and
     * waits out loading screens.
     */
    @Step("Navigate to the main screen")
    public MainScreenPage toMainScreen() {
        // Generous poll to absorb the long post-launch loading screen.
        for (int attempt = 0; attempt < 12; attempt++) {
            Pages page = detectCurrentPage(20, Duration.ofSeconds(1));
            switch (page) {
                case MAIN -> {
                    return new MainScreenPage(testContext);
                }
                case WELCOME, SIGN_IN -> new SignInPage(testContext).ensureSignedIn(
                        RuntimeConfig.getRequired("tvEmail"),
                        RuntimeConfig.getRequired("tvPassword"));
                case SIGN_UP -> dpad.back();               // back to welcome, then login next loop
                case RECONNECT_DIALOG -> dpad.focusOnAndSelect(org.openqa.selenium.By.id(RECONNECT_DIALOG_SIG));
                case SERVER_LIST, INFO_SCREEN, DEBUG_MENU -> dpad.back();
                case UNKNOWN, LOADING -> pause(Duration.ofSeconds(1));
            }
        }
        // Could not settle on the main screen — fail with a clear, current-state message.
        throw new NoSuchElementException(
                "Navigator could not reach the main screen; last detected page: " + detectPage());
    }

    /**
     * Drives the app to the welcome screen (used by login / sign-up tests that start from a clean
     * install). Backs out of any sub-screen; if the app is already signed in (on main), that's a
     * setup error and we fail clearly.
     */
    @Step("Navigate to the welcome screen")
    public SignInPage toWelcome() {
        for (int attempt = 0; attempt < 12; attempt++) {
            Pages page = detectCurrentPage(20, Duration.ofSeconds(1));
            switch (page) {
                case WELCOME -> {
                    return new SignInPage(testContext);
                }
                case SIGN_IN, SIGN_UP, SERVER_LIST, RECONNECT_DIALOG, INFO_SCREEN, DEBUG_MENU -> dpad.back();
                case MAIN -> throw new IllegalStateException(
                        "Expected the welcome screen but the app is signed in (on main). "
                                + "Clear app data before login/sign-up tests.");
                case UNKNOWN, LOADING -> pause(Duration.ofSeconds(1));
            }
        }
        throw new NoSuchElementException(
                "Navigator could not reach the welcome screen; last detected page: " + detectPage());
    }
}
