package apps.tv.pages;

import driver.TestContext;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.testng.Assert;

import java.time.Duration;

/**
 * "Privacy Notice" screen (TvPrivacyNoticeActivity) — opened from the settings menu.
 * Shows a headline, the privacy-notice URL and a QR. Locators verified on device.
 */
public class PrivacyNoticePage extends BasePage {

    private static final String PKG = "com.free.vpn.super.hotspot.open:id/";

    public final By headline = By.id(PKG + "tv_privacy_headline");
    public final By privacySite = By.id(PKG + "tv_privacy_notice_site");
    public final By qr = By.id(PKG + "iv_privacy_notice_qr");
    public final By goBackButton = By.id(PKG + "btn_go_back");

    private static final String EXPECTED_HEADLINE = "Privacy Notice";
    private static final String EXPECTED_URL = "https://vpnsuper.com/privacy-notice";

    public PrivacyNoticePage(TestContext testContext) {
        super(testContext);
    }

    PrivacyNoticePage waitLoaded() {
        fluentVisibility(headline, Duration.ofSeconds(15));
        return this;
    }

    @Step("Verify the Privacy Notice screen is displayed with the correct headline and URL")
    public PrivacyNoticePage verifyDisplayed() {
        Assert.assertEquals(textOf(headline), EXPECTED_HEADLINE, "Wrong Privacy Notice headline");
        Assert.assertEquals(textOf(privacySite), EXPECTED_URL, "Wrong privacy notice URL");
        Assert.assertTrue(isDisplayed(qr), "Privacy Notice QR code not displayed");
        Assert.assertTrue(isDisplayed(goBackButton), "'Go back' button not displayed");
        attachScreenToReport("Privacy Notice");
        return this;
    }

    @Step("Go back from Privacy Notice")
    public MainScreenPage goBack() {
        dpad.focusOnAndSelect(goBackButton);
        return new MainScreenPage(testContext);
    }
}
