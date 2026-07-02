package apps.tv.pages;

import driver.TestContext;
import io.appium.java_client.android.nativekey.AndroidKey;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.testng.Assert;

import java.time.Duration;

/**
 * Main Android TV screen (TvMainActivity): connect button, status, location selector,
 * protocol grid (Auto / IKEv2 / OpenVPN) and the connection info cards.
 * Locators verified against a live device dump.
 */
public class ConnectTvPage extends TvBasePage {

    private static final String PKG = "com.free.vpn.super.hotspot.open:id/";

    public final By connectButton = By.id(PKG + "tvConnectButton");
    public final By connectStatus = By.id(PKG + "tvConnectStatus");
    public final By locationSelector = By.id(PKG + "vpn_location_selector");
    public final By fastestServerLabel = By.id(PKG + "tv_fastest_server");
    public final By protocolGrid = By.id(PKG + "connect_mode");

    // Server list screen (TvServerListActivity)
    public final By serverListTitle = By.id(PKG + "tv_title");   // "Select Server Location"
    public final By serverName = By.id(PKG + "tv_name");         // a server row — signals the list loaded
    public final By settingsButton = By.id(PKG + "tv_settings");
    public final By originalIpValue = By.id(PKG + "tvOriginalIpValue");
    public final By timeConnectedValue = By.id(PKG + "tvTimeConnectedValue");

    private static final String STATUS_CONNECTED = "CONNECTED";
    private static final String STATUS_DISCONNECTED = "CONNECT";
    private static final String TIME_IDLE = "--:--:--";

    public ConnectTvPage(TestContext testContext) {
        super(testContext);
    }

    @Step("Go to main screen")
    public ConnectTvPage navigateToMainScreen() {
        fluentVisibility(connectButton, Duration.ofSeconds(30));
        return this;
    }

    @Step("Verify the app starts disconnected")
    public ConnectTvPage verifyDisconnected() {
        String status = textOf(connectStatus);
        Assert.assertEquals(status, STATUS_DISCONNECTED,
                "Expected the app to start disconnected, but status was: " + status);
        return this;
    }

    @Step("Open location list, wait for servers to load, then go back")
    public ConnectTvPage openLocationListAndReturn() {
        dpad.focus(locationSelector, AndroidKey.DPAD_DOWN, 5).center();

        // Server list screen — wait until the server rows have loaded.
        fluentVisibility(serverListTitle, Duration.ofSeconds(20));
        fluentVisibility(serverName, Duration.ofSeconds(20));

        // Back to the main screen.
        dpad.back();
        fluentVisibility(connectButton, Duration.ofSeconds(10));
        return this;
    }

    @Step("Press Connect on the TV")
    public ConnectTvPage pressConnect() {
        dpad.focus(connectButton, AndroidKey.DPAD_UP, 5).center();
        return this;
    }

    @Step("Verify the VPN is connected (status CONNECTED and timer running)")
    public ConnectTvPage verifyConnected() {
        boolean connected = waitForText(connectStatus, STATUS_CONNECTED, Duration.ofSeconds(30));
        attachScreenToReport("After connect");
        Assert.assertTrue(connected,
                "VPN did not report CONNECTED within 30s. Current status: " + textOf(connectStatus));
        Assert.assertNotEquals(textOf(timeConnectedValue), TIME_IDLE,
                "Connection timer is still idle (--:--:--) after connecting");
        return this;
    }

    @Step("Disconnect the VPN")
    public ConnectTvPage disconnect() {
        dpad.focus(connectButton, AndroidKey.DPAD_UP, 5).center();
        waitForText(connectStatus, STATUS_DISCONNECTED, Duration.ofSeconds(15));
        return this;
    }

    public String selectedLocation() {
        return textOf(fastestServerLabel);
    }
}
