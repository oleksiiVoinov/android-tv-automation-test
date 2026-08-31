package apps.tv.pages;

import driver.TestContext;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.testng.Assert;

import java.time.Duration;

/**
 * Google Play's TV billing sheet ({@code com.android.vending},
 * {@code TvUiBuilderHostActivity}) — the screen that opens when a pay wall plan is selected.
 *
 * <p>Play strips its own resource-ids in the release build (every node reports
 * {@code 0_resource_name_obfuscated}), so everything here is addressed by <b>text</b>. The
 * "Subscribe" label is a plain TextView; the focusable/clickable element is its parent container,
 * which is what the D-pad has to land on.
 *
 * <p>On the test box the Play account is a licence tester: the sheet says
 * "This is a test subscription … You will not be charged" and the payment method is
 * "Test card, always approves", so {@link #subscribe()} never spends real money. Renewal is
 * accelerated (a weekly plan renews every 5 minutes and expires on its own).
 */
public class PlayBillingPage extends BasePage {

    public static final String PLAY_PACKAGE = "com.android.vending";
    public static final String SUBSCRIBE = "Subscribe";
    public static final String TEST_SUBSCRIPTION_NOTICE = "This is a test subscription";
    public static final String TEST_PAYMENT_METHOD = "Test card, always approves";

    /** The focusable container that carries the "Subscribe" label. */
    public final By subscribeButton = By.xpath(
            "//*[@clickable='true'][.//android.widget.TextView[@text='" + SUBSCRIBE + "']]");
    public final By subscribeLabel = By.xpath(
            "//android.widget.TextView[@text='" + SUBSCRIBE + "']");
    /** Product headline, e.g. "VPN Super Weekly Premium". */
    public final By productTitle = By.xpath(
            "//*[@package='" + PLAY_PACKAGE + "']//android.widget.TextView[contains(@text,'Premium')]");
    public final By testSubscriptionNotice = By.xpath(
            "//android.widget.TextView[contains(@text,'" + TEST_SUBSCRIPTION_NOTICE + "')]");
    public final By paymentMethod = By.xpath(
            "//android.widget.TextView[@text='" + TEST_PAYMENT_METHOD + "']");

    public PlayBillingPage(TestContext testContext) {
        super(testContext);
    }

    @Step("Wait for the Google Play billing sheet")
    public PlayBillingPage waitLoaded() {
        fluentPresenceOfElementLocated(subscribeLabel, Duration.ofSeconds(45));
        return this;
    }

    /**
     * Verifies the sheet belongs to the requested plan and that it is a licence-test purchase —
     * a run that somehow reached a real payment method must fail loudly, not buy something.
     */
    @Step("Verify the billing sheet for the {plan} plan (test purchase)")
    public PlayBillingPage verifySheet(PayWallPage.Plan plan) {
        String title = textOf(productTitle);
        Assert.assertTrue(title.contains(plan.title),
                "Google Play offers '" + title + "' but the " + plan + " plan was selected");
        Assert.assertTrue(isPresent(subscribeLabel), "'Subscribe' button not shown on the billing sheet");
        Assert.assertTrue(isPresent(testSubscriptionNotice),
                "This is not a test purchase — the sheet has no '" + TEST_SUBSCRIPTION_NOTICE + "' notice. "
                        + "Check that the Google account on the box is a licence tester.");
        Assert.assertTrue(isPresent(paymentMethod),
                "Unexpected payment method — expected '" + TEST_PAYMENT_METHOD + "'");
        attachScreenToReport("Google Play billing sheet — " + plan);
        return this;
    }

    /**
     * Confirms the purchase. Play closes its sheet, the app shows its progress overlay and then
     * opens the main screen by itself — waiting for that is
     * {@link PayWallPage#waitPurchaseCompleted()}.
     */
    @Step("Confirm the purchase (Subscribe)")
    public PayWallPage subscribe() {
        dpad.focusOnAndSelect(subscribeButton);
        return new PayWallPage(testContext);
    }

    /** Abandons the purchase and returns to the pay wall. */
    @Step("Dismiss the billing sheet (BACK)")
    public PayWallPage cancel() {
        dpad.back();
        return new PayWallPage(testContext).waitLoaded();
    }
}
