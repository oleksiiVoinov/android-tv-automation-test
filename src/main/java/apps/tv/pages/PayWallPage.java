package apps.tv.pages;

import driver.TestContext;
import io.appium.java_client.AppiumBy;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.testng.Assert;

import java.time.Duration;
import java.util.List;

/**
 * Pay wall screen (TvPaywallActivity) — "Choose your Premium plan".
 *
 * <p>Reached from the welcome screen with <b>Sign up</b> (the old TV sign-up QR screen is no longer
 * shown there). Layout: a left panel with the headline and six benefit tiles, a right column with a
 * "Back" pill and three plan cards (Weekly / Monthly / Yearly), and a footer whose wording follows
 * the currently focused plan.
 *
 * <p><b>Focus matters.</b> Every card is an {@code include} of the same layout, so
 * {@code card_root}, {@code plan_name}, {@code plan_price} and {@code plan_suffix} exist three
 * times — always address them through {@link #child(Plan, String)}, which scopes the lookup to the
 * card container. The badge ({@code plan_badge}) and the CTA ({@code plan_cta}) are rendered
 * <b>only on the focused card</b>, and {@code tv_paywall_footer} switches between the weekly /
 * monthly / yearly wording with the focus. Locators verified on a Google TV Streamer.
 */
public class PayWallPage extends BasePage {

    private static final String PKG = "com.free.vpn.super.hotspot.open:id/";

    public final By payWallRoot = By.id(PKG + "paywall_root");
    public final By leftPanel = By.id(PKG + "paywall_left");
    public final By rightPanel = By.id(PKG + "paywall_right");
    public final By backButton = By.id(PKG + "btn_paywall_back");
    public final By footer = By.id(PKG + "tv_paywall_footer");
    /** Full-screen spinner shown while Google Play billing is being processed. */
    public final By progressOverlay = By.id(PKG + "paywall_progress_overlay");
    public final By headline = text("Choose your Premium plan");

    public static final String HEADLINE = "Choose your Premium plan";
    public static final String CTA_PURCHASE = "Purchase";

    /** The six benefit tiles of the left panel (text carries a hard line break). */
    public static final List<String> BENEFITS = List.of(
            "Ultra-fast\nspeeds",
            "Servers built\nfor streaming",
            "Apps for PC,\nTV, and more",
            "50+ global\nlocations",
            "Ad-free\nexperience",
            "Protect up to\n10 devices");

    /** The three subscription plans of the pay wall, top to bottom. */
    public enum Plan {
        WEEKLY("plan_weekly", "Weekly", "/wk", "Lowest Price",
                "Your plan renews every week until canceled. "
                        + "Cancel anytime in your Google Play account settings."),
        MONTHLY("plan_monthly", "Monthly", "/mo", "Most Popular",
                "Your plan renews every month until canceled. "
                        + "Cancel anytime in your Google Play account settings."),
        YEARLY("plan_yearly", "Yearly", "/yr", "Best Value",
                "Your plan renews every year until canceled. "
                        + "Cancel anytime in your Google Play account settings.");

        public final String containerId;
        public final String title;
        public final String suffix;
        public final String badge;
        public final String footer;

        Plan(String containerId, String title, String suffix, String badge, String footer) {
            this.containerId = containerId;
            this.title = title;
            this.suffix = suffix;
            this.badge = badge;
            this.footer = footer;
        }
    }

    public PayWallPage(TestContext testContext) {
        super(testContext);
    }

    private static By text(String value) {
        return AppiumBy.androidUIAutomator("new UiSelector().text(\"" + value + "\")");
    }

    /**
     * A benefit tile. Its label carries a hard line break, and a UiSelector cannot express a
     * newline, so match every line of the label with {@code contains()} instead.
     */
    private static By benefit(String label) {
        StringBuilder xpath = new StringBuilder("//android.widget.TextView");
        for (String line : label.split("\n")) {
            xpath.append("[contains(@text,'").append(line).append("')]");
        }
        return By.xpath(xpath.toString());
    }

    /** A child of one plan card — the ids repeat across the three cards, so scope by container. */
    private By child(Plan plan, String childId) {
        return By.xpath("//*[@resource-id='" + PKG + plan.containerId + "']"
                + "//*[@resource-id='" + PKG + childId + "']");
    }

    public By card(Plan plan) {
        return child(plan, "card_root");
    }

    public By planName(Plan plan) {
        return child(plan, "plan_name");
    }

    public By planPrice(Plan plan) {
        return child(plan, "plan_price");
    }

    public By planSuffix(Plan plan) {
        return child(plan, "plan_suffix");
    }

    public By planBadge(Plan plan) {
        return child(plan, "plan_badge");
    }

    public By planCta(Plan plan) {
        return child(plan, "plan_cta");
    }

    @Step("Wait for the pay wall to load")
    public PayWallPage waitLoaded() {
        fluentVisibility(payWallRoot, Duration.ofSeconds(30));
        fluentVisibility(footer, Duration.ofSeconds(30));
        return this;
    }

    /** The light check: this really is the pay wall — root, headline and the three plan cards. */
    @Step("Verify the pay wall is shown")
    public PayWallPage verifyPayWallShown() {
        waitLoaded();
        Assert.assertTrue(isDisplayed(payWallRoot), "Pay wall root not displayed");
        Assert.assertTrue(isDisplayed(headline), "Headline '" + HEADLINE + "' not displayed");
        for (Plan plan : Plan.values()) {
            Assert.assertTrue(isDisplayed(card(plan)), plan + " plan card not displayed");
        }
        attachScreenToReport("Pay wall");
        return this;
    }

    @Step("Verify the pay wall (headline, benefits, three plans, Back, footer)")
    public PayWallPage verifyPayWallDisplayed() {
        waitLoaded();
        Assert.assertTrue(isDisplayed(leftPanel), "Pay wall left panel not displayed");
        Assert.assertTrue(isDisplayed(rightPanel), "Pay wall right panel not displayed");
        Assert.assertTrue(isDisplayed(headline), "Headline '" + HEADLINE + "' not displayed");

        for (String label : BENEFITS) {
            Assert.assertTrue(isDisplayed(benefit(label)),
                    "Benefit tile not displayed: " + label.replace("\n", " "));
        }

        Assert.assertTrue(isDisplayed(backButton), "'Back' button not displayed");
        Assert.assertEquals(textOf(backButton), "Back", "Wrong text on the pay wall Back button");

        for (Plan plan : Plan.values()) {
            Assert.assertTrue(isDisplayed(card(plan)), plan + " plan card not displayed");
            Assert.assertEquals(textOf(planName(plan)), plan.title, "Wrong title on the " + plan + " card");
            Assert.assertEquals(textOf(planSuffix(plan)), plan.suffix, "Wrong period suffix on the " + plan + " card");
            String price = textOf(planPrice(plan));
            Assert.assertFalse(price.isBlank(), plan + " plan price is empty");
            Assert.assertTrue(price.matches(".*\\d.*"), "The " + plan + " price has no digits: " + price);
        }

        Assert.assertFalse(textOf(footer).isBlank(), "Pay wall footer is empty");
        attachScreenToReport("Pay wall");
        return this;
    }

    @Step("Move focus to the {plan} plan card")
    public PayWallPage focusPlan(Plan plan) {
        dpad.focusOn(card(plan));
        return this;
    }

    /**
     * Verifies what the pay wall shows for the plan that currently holds focus: its badge, the
     * "Purchase" CTA and the matching footer wording. The other two cards must show neither badge
     * nor CTA — the app renders them only on the focused card.
     */
    @Step("Verify the focused {plan} plan (badge, CTA, footer)")
    public PayWallPage verifyPlanFocused(Plan plan) {
        Assert.assertTrue(dpad.isFocused(card(plan)), "The " + plan + " card does not hold focus");
        Assert.assertEquals(textOf(planBadge(plan)), plan.badge, "Wrong badge on the focused " + plan + " card");
        Assert.assertEquals(textOf(planCta(plan)), CTA_PURCHASE, "Wrong CTA on the focused " + plan + " card");
        Assert.assertEquals(textOf(footer), plan.footer, "Footer does not match the focused " + plan + " plan");

        for (Plan other : Plan.values()) {
            if (other == plan) {
                continue;
            }
            Assert.assertFalse(isPresent(planCta(other)),
                    "The unfocused " + other + " card must not show a CTA");
            Assert.assertFalse(isPresent(planBadge(other)),
                    "The unfocused " + other + " card must not show a badge");
        }
        attachScreenToReport("Pay wall — " + plan + " focused");
        return this;
    }

    /** Price as shown on the card, e.g. {@code UAH255.99}. */
    public String priceOf(Plan plan) {
        return textOf(planPrice(plan));
    }

    /** Focus the plan and press OK — Google Play opens its TV billing sheet. */
    @Step("Open the Google Play billing sheet for the {plan} plan")
    public PlayBillingPage purchase(Plan plan) {
        focusPlan(plan);
        dpad.center();
        return new PlayBillingPage(testContext).waitLoaded();
    }

    /**
     * After a successful purchase the app dismisses the pay wall itself and lands on the main
     * screen (no confirmation dialog on TV).
     */
    @Step("Wait for the purchase to complete and the app to open the main screen")
    public MainScreenPage waitPurchaseCompleted() {
        MainScreenPage main = new MainScreenPage(testContext);
        main.fluentVisibility(main.connectButton, Duration.ofSeconds(120));
        attachScreenToReport("Main screen after purchase");
        return main;
    }

    @Step("Leave the pay wall (Back) → welcome screen")
    public SignInPage goBack() {
        dpad.focusOnAndSelect(backButton);
        SignInPage welcome = new SignInPage(testContext);
        welcome.fluentVisibility(welcome.signInButton, Duration.ofSeconds(15));
        return welcome;
    }
}
