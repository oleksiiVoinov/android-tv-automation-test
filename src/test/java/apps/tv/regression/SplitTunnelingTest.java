package apps.tv.regression;

import apps.BaseTest;
import apps.common.CommandsADB;
import apps.tv.api.serverlist.ServerList;
import apps.tv.api.serverlist.ServerV7;
import apps.tv.pages.MainScreenPage;
import apps.tv.pages.SplitTunnelingPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Settings menu → "Split Tunneling" (TvAppsManagerActivity). Validates the screen, and that
 * excluding a single app affects only that app and persists after reopening the screen.
 * Uses "Appium Settings" as the target (always installed) and "Apple TV" as a control app.
 */
@Epic("Android TV")
@Feature("4. Settings menu")
public class SplitTunnelingTest extends BaseTest {

    private static final String TARGET_APP = "Appium Settings";       // list label
    private static final String TARGET_PKG = "io.appium.settings";    // its package (debuggable → run-as)
    private static final String CONTROL_APP = "Apple TV";   // must stay included when we exclude the target

    /** Baseline: the master "all apps" checkbox on → every app included. Runs after BaseTest.tearUp. */
    @BeforeClass
    public void ensureBaseline() {
        new MainScreenPage(testContext)
                .navigateToMainScreen()
                .openSettingsMenu()
                .openSplitTunneling()
                .ensureAllAppsIncluded()
                .goBack();
    }

    @Test(priority = 1, description = "Split tunneling screen opens and shows the app list")
    @Story("Split Tunneling")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Objective: verify the 'Split Tunneling' menu item opens the app-manager screen

            Steps:
            1. go to the main screen
            2. open the settings menu (gear) → Split Tunneling
            3. verify the master 'all apps' checkbox, the app list and the Back control""")
    public void validateSplitTunnelingDisplayed() {
        new MainScreenPage(testContext)
                .navigateToMainScreen()
                .openSettingsMenu()
                .openSplitTunneling()
                .verifyDisplayed()
                .goBack();
    }

    @Test(priority = 2, description = "Excluding one app affects only that app and persists")
    @Story("Split Tunneling")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Objective: verify excluding a single app unchecks only that app (not the whole list)
            and that the exclusion persists across reopening the screen

            Pre-cond: @BeforeClass enables the 'all apps' master, so every app starts included.

            Steps:
            1. open Split Tunneling; assert the target and control apps start included
            2. exclude the target app (tap its checkbox); assert the target is now excluded AND
               the control app is unchanged
            3. go back to the main screen, reopen Split Tunneling
            4. assert the target app is still excluded (persisted)
            5. restore the baseline (all apps included)""")
    public void excludingOneAppAffectsOnlyThatApp() {
        SplitTunnelingPage page = new MainScreenPage(testContext)
                .navigateToMainScreen()
                .openSettingsMenu()
                .openSplitTunneling();

        Assert.assertTrue(page.isAppChecked(TARGET_APP),
                "Precondition: '" + TARGET_APP + "' should start included");
        Assert.assertTrue(page.isAppChecked(CONTROL_APP),
                "Precondition: '" + CONTROL_APP + "' should start included");

        page.excludeApp(TARGET_APP);
        Assert.assertFalse(page.isAppChecked(TARGET_APP), "'" + TARGET_APP + "' should be excluded");
        Assert.assertTrue(page.isAppChecked(CONTROL_APP),
                "Control app '" + CONTROL_APP + "' must stay included when excluding another app");
        page.goBack();

        // navigateToMainScreen() is safe from any screen — the Navigator backs out of split tunneling.
        boolean stillExcluded = new MainScreenPage(testContext)
                .navigateToMainScreen()
                .openSettingsMenu()
                .openSplitTunneling()
                .isAppExcluded(TARGET_APP);
        Assert.assertTrue(stillExcluded, "'" + TARGET_APP + "' should stay excluded after reopening");

        // Restore the baseline for the next run.
        new MainScreenPage(testContext)
                .navigateToMainScreen()
                .openSettingsMenu()
                .openSplitTunneling()
                .ensureAllAppsIncluded()
                .goBack();
    }

    @Test(priority = 3, description = "Split tunneling actually re-routes a specific app's traffic")
    @Story("Split Tunneling")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Objective: prove split tunneling has a REAL effect on traffic for one app, measured
            from that app's own uid (run-as io.appium.settings → ip-api.com).

            Steps:
            1. disconnect VPN → read the target app's real egress country
            2. connect to a server in a different country
            3. read the target app's egress country → expect the SERVER's country (app is included)
            4. exclude the target app in Split Tunneling → read again → expect the REAL country back""")
    public void splitTunnelingReroutesAppTraffic() throws Exception {
        CommandsADB adb = new CommandsADB();
        ObjectMapper mapper = new ObjectMapper();
        String udid = device.uDID;

        // 1. Real country of the target app (VPN off).
        new MainScreenPage(testContext).navigateToMainScreen().ensureDisconnected();
        JsonNode realEgress = mapper.readTree(adb.appEgressJson(udid, TARGET_PKG));
        String realCode = countryCode(realEgress);
        reportCountry("Real country of " + TARGET_APP + " (VPN off)", realEgress);

        // 2. Connect to a server in a different country (target app is included by the baseline).
        ServerV7 server = pickServerNotIn(realCode);
        System.out.println("🎯 connecting via server country = " + server.getCountryName() + " (" + server.getCountry() + ")");
        new MainScreenPage(testContext)
                .navigateToMainScreen()
                .openServerList()
                .selectServer(server)
                .ensureConnected();

        // 3. Included → the app egresses via the server's country.
        JsonNode vpnEgress = mapper.readTree(adb.appEgressJson(udid, TARGET_PKG));
        reportCountry(TARGET_APP + " country while included + connected", vpnEgress);
        Assert.assertEquals(countryCode(vpnEgress), server.getCountry(),
                "Included app should egress via the server country");
        Assert.assertNotEquals(countryCode(vpnEgress), realCode, "VPN country must differ from the real country");

        // 4. Exclude the target, then reconnect so the VpnService re-applies the disallowed-app set
        //    (the change does NOT take effect on a live tunnel — verified: it stays routed until reconnect).
        new MainScreenPage(testContext)
                .navigateToMainScreen()
                .openSettingsMenu()
                .openSplitTunneling()
                .excludeApp(TARGET_APP)
                .goBack();
        new MainScreenPage(testContext).navigateToMainScreen().reconnect();

        // Now the excluded app should bypass the tunnel → real country again.
        JsonNode excludedEgress = pollAppEgress(adb, mapper, udid, realCode);
        reportCountry(TARGET_APP + " country after excluding + reconnect", excludedEgress);
        Assert.assertEquals(countryCode(excludedEgress), realCode,
                "Excluded app should egress via the real country (bypass the VPN)");

        // Cleanup: re-include all apps, disconnect.
        new MainScreenPage(testContext)
                .navigateToMainScreen()
                .openSettingsMenu()
                .openSplitTunneling()
                .ensureAllAppsIncluded()
                .goBack();
        new MainScreenPage(testContext).navigateToMainScreen().ensureDisconnected();
    }

    private String countryCode(JsonNode egress) {
        Assert.assertEquals(egress.path("status").asText(), "success", "egress lookup failed: " + egress);
        return egress.path("countryCode").asText();
    }

    /** Prints the country name (+ code and IP) to the console and attaches it to the Allure report. */
    private void reportCountry(String label, JsonNode egress) {
        String line = label + ": " + egress.path("country").asText()
                + " (" + egress.path("countryCode").asText() + "), IP " + egress.path("query").asText();
        System.out.println("🌍 " + line);
        io.qameta.allure.Allure.addAttachment("Egress — " + label, "text/plain", line);
    }

    /** Polls the target app's egress, allowing time for the routing change to settle. */
    private JsonNode pollAppEgress(CommandsADB adb, ObjectMapper mapper, String udid, String expectedCode) throws Exception {
        JsonNode last = null;
        for (int i = 0; i < 8; i++) {
            last = mapper.readTree(adb.appEgressJson(udid, TARGET_PKG));
            if (expectedCode.equalsIgnoreCase(last.path("countryCode").asText())) {
                return last;
            }
            Thread.sleep(2500);
        }
        return last;
    }

    private ServerV7 pickServerNotIn(String avoidCountryCode) throws Exception {
        ServerList list = new ServerList(testContext);
        for (int i = 0; i < 10; i++) {
            ServerV7 s = list.getRandomNonUsServer();
            if (!s.getCountry().equalsIgnoreCase(avoidCountryCode)) {
                return s;
            }
        }
        throw new IllegalStateException("Could not find a server outside country " + avoidCountryCode);
    }
}
