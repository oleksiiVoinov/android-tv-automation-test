package apps.tv.pages;

import apps.common.CommandsADB;
import apps.tv.api.serverlist.ServerV7;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import driver.TestContext;
import io.appium.java_client.AppiumBy;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.testng.Assert;

import java.time.Duration;
import java.util.List;

/**
 * Main Android TV screen (TvMainActivity): connect button, status, location selector,
 * protocol grid (Auto / IKEv2 / OpenVPN) and the connection info cards.
 * Locators verified against a live device dump.
 */
public class MainScreen extends TvBasePage {

    private static final String PKG = "com.free.vpn.super.hotspot.open:id/";

    public final By appName = By.id(PKG + "app_name");
    public final By connectButton = By.id(PKG + "tvConnectButton");
    public final By connectStatus = By.id(PKG + "tvConnectStatus");
    public final By locationSelector = By.id(PKG + "vpn_location_selector");
    public final By fastestServerLabel = By.id(PKG + "tv_fastest_server");
    public final By protocolGrid = By.id(PKG + "connect_mode");

    // Server list screen (TvServerListActivity)
    public final By serverListTitle = By.id(PKG + "tv_title");   // "Select Server Location"
    public final By serverName = By.id(PKG + "tv_name");         // a server row — signals the list loaded

    // "Do you want to reconnect with the new protocol?" dialog (shown when changing protocol while connected)
    public final By reconnectDialogTitle = By.id(PKG + "tv_dialog_title");
    public final By reconnectOkButton = By.id(PKG + "action_ok_btn");       // "Reconnect" (focused by default)
    public final By dialogCancelButton = By.id(PKG + "action_cancel_btn");  // "Cancel"
    public final By settingsButton = By.id(PKG + "tv_settings");
    public final By originalIpValue = By.id(PKG + "tvOriginalIpValue");
    public final By timeConnectedValue = By.id(PKG + "tvTimeConnectedValue");

    private static final String STATUS_CONNECTED = "CONNECTED";
    private static final String STATUS_DISCONNECTED = "CONNECT";
    private static final String TIME_IDLE = "--:--:--";

    public MainScreen(TestContext testContext) {
        super(testContext);
    }

    @Step("Go to main screen")
    public MainScreen navigateToMainScreen() {
        // Robustly reach the main connect screen from wherever the app is (server list, search,
        // sort dialog, reconnect prompt, etc.) — mirrors the phone Navigator idea.
        for (int attempt = 0; attempt < 8; attempt++) {
            // Dismiss any modal dialog first (e.g. "reconnect with new protocol?").
            if (isPresent(dialogCancelButton)) {
                dpad.focusOnAndSelect(dialogCancelButton);
                continue;
            }
            if (waitForConnectButton(Duration.ofSeconds(2))) {
                return this;
            }
            dpad.back();
        }
        fluentVisibility(connectButton, Duration.ofSeconds(15)); // final attempt → clear failure
        return this;
    }

    private boolean waitForConnectButton(Duration timeout) {
        try {
            fluentVisibility(connectButton, timeout);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private By protocolLocator(Protocols protocol) {
        return AppiumBy.androidUIAutomator("new UiSelector().text(\"" + protocol.label + "\")");
    }

    @Step("Verify all main screen elements are displayed")
    public MainScreen verifyMainScreenDisplayed() {
        List<By> elements = List.of(
                appName, settingsButton, connectButton, connectStatus,
                locationSelector, fastestServerLabel, protocolGrid,
                originalIpValue, timeConnectedValue);
        for (By element : elements) {
            Assert.assertTrue(isDisplayed(element), "Main screen element not displayed: " + element);
        }
        for (Protocols protocol : Protocols.values()) {
            Assert.assertTrue(isDisplayed(protocolLocator(protocol)),
                    "Protocol not displayed in the grid: " + protocol.label);
        }
        return this;
    }

    @Step("Select protocol {protocol}")
    public MainScreen selectProtocol(Protocols protocol) {
        dpad.focusOnAndSelect(protocolLocator(protocol));
        confirmReconnectIfPresent();
        return this;
    }

    /**
     * Changing the protocol while the VPN is connected prompts
     * "Do you want to reconnect with the new protocol?". Confirm it (Reconnect is focused by default).
     * No-op when disconnected (no dialog appears).
     */
    @Step("Confirm reconnect-with-new-protocol dialog if present")
    public MainScreen confirmReconnectIfPresent() {
        try {
            fluentVisibility(reconnectDialogTitle, Duration.ofSeconds(2));
        } catch (Exception noDialog) {
            return this;
        }
        dpad.focusOnAndSelect(reconnectOkButton);
        return this;
    }

    @Step("Verify the app starts disconnected")
    public MainScreen verifyDisconnected() {
        String status = textOf(connectStatus);
        Assert.assertEquals(status, STATUS_DISCONNECTED,
                "Expected the app to start disconnected, but status was: " + status);
        return this;
    }

    @Step("Open the server list")
    public ServerListTvPage openServerList() {
        dpad.focusOnAndSelect(locationSelector);
        ServerListTvPage page = new ServerListTvPage(testContext);
        page.fluentVisibility(page.title, Duration.ofSeconds(15));
        return page;
    }

    @Step("Open location list, wait for servers to load, then go back")
    public MainScreen openLocationListAndReturn() {
        dpad.focusOnAndSelect(locationSelector);

        // Server list screen — wait until the server rows have loaded.
        fluentVisibility(serverListTitle, Duration.ofSeconds(20));
        fluentVisibility(serverName, Duration.ofSeconds(20));

        // Back to the main screen.
        dpad.back();
        fluentVisibility(connectButton, Duration.ofSeconds(10));
        return this;
    }

    @Step("Press Connect on the TV")
    public MainScreen pressConnect() {
        dpad.focusOnAndSelect(connectButton);
        return this;
    }

    @Step("Verify the VPN reports connected in the UI (status CONNECTED + timer running)")
    public MainScreen verifyConnected() {
        boolean connected = waitForText(connectStatus, STATUS_CONNECTED, Duration.ofSeconds(30));
        attachScreenToReport("After connect");
        Assert.assertTrue(connected,
                "VPN did not report CONNECTED within 30s. Current status: " + textOf(connectStatus));
        Assert.assertNotEquals(textOf(timeConnectedValue), TIME_IDLE,
                "Connection timer is still idle (--:--:--) after connecting");
        return this;
    }

    /**
     * Strongest check: makes an HTTP request FROM the device (through the tunnel) and asserts the
     * real egress country. Also cross-checks that the egress IP matches what the app displays,
     * proving the app isn't reporting a stale/wrong IP. Requires the location to be {@code expectedCountry}.
     */
    @Step("Verify real egress through the tunnel resolves to {expectedCountry}")
    public MainScreen verifyRealEgress(String expectedCountry) {
        JsonNode node = fetchEgress();
        String country = node.path("country").asText();
        String egressIp = node.path("query").asText();
        attachScreenToReport("Real egress: " + egressIp + " → " + country);

        Assert.assertTrue(country.equalsIgnoreCase(expectedCountry),
                "Real egress country is '" + country + "' but expected '" + expectedCountry
                        + "' (egress IP " + egressIp + ")");
        crossCheckAppIp(egressIp);
        return this;
    }

    /**
     * Same as {@link #verifyRealEgress(String)} but takes the expected server from the API model and
     * compares by ISO country code (robust for e.g. US cities). Used by server-list selection tests.
     */
    @Step("Verify real egress matches selected server {server}")
    public MainScreen verifyRealEgress(ServerV7 server) {
        JsonNode node = fetchEgress();
        String egressCode = node.path("countryCode").asText();
        String egressIp = node.path("query").asText();
        attachScreenToReport("Real egress: " + egressIp + " → " + node.path("country").asText()
                + " (" + egressCode + ")");

        Assert.assertTrue(egressCode.equalsIgnoreCase(server.getCountry()),
                "Real egress country code is '" + egressCode + "' but selected server is '"
                        + server.getCountry() + "' (" + server.getCountryName() + ", egress IP " + egressIp + ")");
        crossCheckAppIp(egressIp);
        return this;
    }

    /** Makes the on-device egress request (through the tunnel) and returns the parsed ip-api JSON. */
    private JsonNode fetchEgress() {
        verifyConnected();
        String json = new CommandsADB().deviceEgressJson(testContext.getDevice().uDID);
        try {
            JsonNode node = new ObjectMapper().readTree(json);
            Assert.assertEquals(node.path("status").asText(), "success",
                    "Device egress lookup failed: " + json);
            return node;
        } catch (Exception e) {
            throw new IllegalStateException("Unparseable egress JSON: " + json, e);
        }
    }

    private void crossCheckAppIp(String egressIp) {
        String appIp = textOf(originalIpValue);
        Assert.assertEquals(appIp, egressIp,
                "App shows VPN IP " + appIp + " but the real egress IP is " + egressIp);
    }

    @Step("Disconnect the VPN")
    public MainScreen disconnect() {
        dpad.focusOnAndSelect(connectButton);
        waitForText(connectStatus, STATUS_DISCONNECTED, Duration.ofSeconds(15));
        return this;
    }

    public String selectedLocation() {
        return textOf(fastestServerLabel);
    }
}
