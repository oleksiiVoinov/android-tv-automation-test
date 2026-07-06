package apps.tv.pages;

import driver.TestContext;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.testng.Assert;

import java.time.Duration;

/**
 * "Help &amp; Support" screen (TvHelpSupportActivity) — opened from the settings menu.
 * Shows a headline, the support site URL and a QR to open it on a phone. Locators verified on device.
 */
public class HelpSupportPage extends BasePage {

    private static final String PKG = "com.free.vpn.super.hotspot.open:id/";

    public final By headline = By.id(PKG + "tv_help_headline");
    public final By supportSite = By.id(PKG + "tv_help_support_site");
    public final By qr = By.id(PKG + "iv_help_support_qr");
    public final By goBackButton = By.id(PKG + "btn_go_back");

    private static final String EXPECTED_HEADLINE = "Help and support";
    private static final String EXPECTED_URL = "https://vpnsuper.com/support";

    public HelpSupportPage(TestContext testContext) {
        super(testContext);
    }

    HelpSupportPage waitLoaded() {
        fluentVisibility(headline, Duration.ofSeconds(15));
        return this;
    }

    @Step("Verify the Help & Support screen is displayed with the correct headline and support URL")
    public HelpSupportPage verifyDisplayed() {
        Assert.assertEquals(textOf(headline), EXPECTED_HEADLINE, "Wrong Help & Support headline");
        Assert.assertEquals(textOf(supportSite), EXPECTED_URL, "Wrong support site URL");
        Assert.assertTrue(isDisplayed(qr), "Support QR code not displayed");
        Assert.assertTrue(isDisplayed(goBackButton), "'Go back' button not displayed");
        attachScreenToReport("Help & Support");
        return this;
    }

    @Step("Go back from Help & Support")
    public MainScreenPage goBack() {
        dpad.focusOnAndSelect(goBackButton);
        return new MainScreenPage(testContext);
    }
}
