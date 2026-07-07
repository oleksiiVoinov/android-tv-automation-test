package apps.tv.pages;

import driver.TestContext;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.testng.Assert;

/**
 * Settings popup menu opened from the main screen's gear ({@code tv_settings}).
 * Items: Help &amp; Support, Split Tunneling, Privacy Notice, Terms of Service, Sign Out.
 * Each entry opens a dedicated screen — see {@link HelpSupportPage}, {@link PrivacyNoticePage},
 * {@link TermsOfServicePage}. Locators verified on device.
 */
public class SettingsMenuPage extends BasePage {

    private static final String PKG = "com.free.vpn.super.hotspot.open:id/";

    public final By helpSupport = By.id(PKG + "tv_settings_help_support");
    public final By splitTunneling = By.id(PKG + "tv_settings_split_tunneling");
    public final By privacyNotice = By.id(PKG + "tv_settings_privacy_notice");
    public final By termsService = By.id(PKG + "tv_settings_terms_service");
    public final By signOut = By.id(PKG + "tv_settings_sign_out");

    public SettingsMenuPage(TestContext testContext) {
        super(testContext);
    }

    @Step("Verify the settings menu items are displayed")
    public SettingsMenuPage verifyDisplayed() {
        for (By item : new By[]{helpSupport, splitTunneling, privacyNotice, termsService, signOut}) {
            Assert.assertTrue(isDisplayed(item), "Settings menu item not displayed: " + item);
        }
        return this;
    }

    @Step("Open 'Help & Support'")
    public HelpSupportPage openHelpSupport() {
        dpad.focusOnAndSelect(helpSupport);
        return new HelpSupportPage(testContext).waitLoaded();
    }

    @Step("Open 'Privacy Notice'")
    public PrivacyNoticePage openPrivacyNotice() {
        dpad.focusOnAndSelect(privacyNotice);
        return new PrivacyNoticePage(testContext).waitLoaded();
    }

    @Step("Open 'Terms of Service'")
    public TermsOfServicePage openTermsService() {
        dpad.focusOnAndSelect(termsService);
        return new TermsOfServicePage(testContext).waitLoaded();
    }

    @Step("Choose 'Sign Out' (opens the confirmation dialog)")
    public SignOutPage signOut() {
        dpad.focusOnAndSelect(signOut);
        return new SignOutPage(testContext).waitConfirmDialog();
    }

    @Step("Open 'Split Tunneling'")
    public SplitTunnelingPage openSplitTunneling() {
        dpad.focusOnAndSelect(splitTunneling);
        return new SplitTunnelingPage(testContext).waitLoaded();
    }
}
