package apps.tv.pages;

import driver.TestContext;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.testng.Assert;

import java.time.Duration;

/**
 * "Couldn't connect to server" dialog — raised when the app cannot establish the tunnel, e.g. after
 * the user switches to another protocol (reconnect) and the server does not answer on it.
 * It replaces the main screen entirely (no {@code tvConnectStatus} underneath), so a plain
 * "wait for CONNECTED" times out on a missing element instead of saying what really happened.
 *
 * <p>Verified on device (V2Ray on a Japan server, 2026-10-08):
 * <ul>
 *   <li>title {@code tv_dialog_title} "Couldn't connect to server";</li>
 *   <li>message {@code tv_dialog_sub_title} "Connection failed. Check your internet connection,
 *       switch servers, or try a different VPN protocol.";</li>
 *   <li>{@code action_change_server} "Change server" (focused by default) → server list;</li>
 *   <li>{@code action_home_page} "Back to main page" → main screen.</li>
 * </ul>
 */
public class ConnectionFailedPage extends BasePage {

    private static final String PKG = "com.free.vpn.super.hotspot.open:id/";

    public final By dialogRoot = By.id(PKG + "dialog_root_view");
    public final By title = By.id(PKG + "tv_dialog_title");
    public final By subTitle = By.id(PKG + "tv_dialog_sub_title");
    public final By changeServerButton = By.id(PKG + "action_change_server");
    public final By backToMainButton = By.id(PKG + "action_home_page");

    public static final String TITLE = "Couldn't connect to server";
    public static final String MESSAGE =
            "Connection failed. Check your internet connection, switch servers, or try a different VPN protocol.";
    public static final String CHANGE_SERVER = "Change server";
    public static final String BACK_TO_MAIN = "Back to main page";

    public ConnectionFailedPage(TestContext testContext) {
        super(testContext);
    }

    /** Instant check (no wait) — the dialog's buttons are unique to it. */
    public boolean isShown() {
        return isPresent(backToMainButton) || isPresent(changeServerButton);
    }

    @Step("Wait for the 'Couldn't connect to server' dialog")
    public ConnectionFailedPage waitDialog(Duration timeout) {
        fluentVisibility(backToMainButton, timeout);
        return this;
    }

    /** Asserts that the app is on the connection-failed dialog and its content is correct. */
    @Step("Verify connection failed ('Couldn't connect to server' dialog)")
    public ConnectionFailedPage verifyConnectionFailed() {
        attachScreenToReport("Connection failed");
        Assert.assertTrue(isDisplayed(dialogRoot), "Connection-failed dialog not displayed");
        Assert.assertEquals(textOf(title), TITLE, "Wrong connection-failed dialog title");
        Assert.assertEquals(textOf(subTitle), MESSAGE, "Wrong connection-failed dialog message");
        Assert.assertEquals(textOf(changeServerButton), CHANGE_SERVER, "Wrong 'Change server' button");
        Assert.assertEquals(textOf(backToMainButton), BACK_TO_MAIN, "Wrong 'Back to main page' button");
        return this;
    }

    @Step("Change server → server list")
    public ServerListPage changeServer() {
        dpad.focusOnAndSelect(changeServerButton);
        ServerListPage serverList = new ServerListPage(testContext);
        serverList.fluentVisibility(serverList.title, Duration.ofSeconds(15));
        return serverList;
    }

    @Step("Back to main page → main screen")
    public MainScreenPage backToMainPage() {
        dpad.focusOnAndSelect(backToMainButton);
        MainScreenPage main = new MainScreenPage(testContext);
        main.fluentVisibility(main.connectButton, Duration.ofSeconds(15));
        return main;
    }
}
