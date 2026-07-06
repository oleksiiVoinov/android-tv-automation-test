package apps.tv.pages;

import driver.TestContext;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.testng.Assert;

import java.time.Duration;

/**
 * "Terms of Service" screen (TvServiceTermsActivity) — opened from the settings menu.
 * Shows a headline, the terms-of-service URL and a QR. Locators verified on device.
 * Note: the URL element id is {@code terms_service_site} (no {@code tv_} prefix, unlike the others).
 */
public class TermsOfServicePage extends BasePage {

    private static final String PKG = "com.free.vpn.super.hotspot.open:id/";

    public final By headline = By.id(PKG + "tv_terms_service_headline");
    public final By termsSite = By.id(PKG + "terms_service_site");
    public final By qr = By.id(PKG + "iv_terms_service_qr");
    public final By goBackButton = By.id(PKG + "btn_go_back");

    private static final String EXPECTED_HEADLINE = "Terms of Service";
    private static final String EXPECTED_URL = "https://vpnsuper.com/terms-of-service";

    public TermsOfServicePage(TestContext testContext) {
        super(testContext);
    }

    TermsOfServicePage waitLoaded() {
        fluentVisibility(headline, Duration.ofSeconds(15));
        return this;
    }

    @Step("Verify the Terms of Service screen is displayed with the correct headline and URL")
    public TermsOfServicePage verifyDisplayed() {
        Assert.assertEquals(textOf(headline), EXPECTED_HEADLINE, "Wrong Terms of Service headline");
        Assert.assertEquals(textOf(termsSite), EXPECTED_URL, "Wrong terms of service URL");
        Assert.assertTrue(isDisplayed(qr), "Terms of Service QR code not displayed");
        Assert.assertTrue(isDisplayed(goBackButton), "'Go back' button not displayed");
        attachScreenToReport("Terms of Service");
        return this;
    }

    @Step("Go back from Terms of Service")
    public MainScreenPage goBack() {
        dpad.focusOnAndSelect(goBackButton);
        return new MainScreenPage(testContext);
    }
}
