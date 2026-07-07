package apps.tv.regression;

import apps.BaseTest;
import apps.tv.api.serverlist.ServerV7;
import apps.tv.api.serverlist.ServerList;
import apps.tv.pages.MainScreenPage;
import apps.tv.pages.Protocols;
import io.qameta.allure.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

@Epic("Android TV")
public class ProtocolsTest extends BaseTest {
    private ServerV7 server;
    private List<Protocols> protocolList;

    @Story("3. Protocols")
    @BeforeClass()
    public void precondition() throws Exception {
        protocolList = new MainScreenPage(testContext)
                .navigateToMainScreen()
                .getProtocols();
        protocolList.removeIf(s -> s.equals(Protocols.Auto));

        server = new ServerList(testContext).getRandomNonUsServer();
        new MainScreenPage(testContext)
                .navigateToMainScreen()
                .disconnect()
                .selectProtocol(Protocols.Auto)
                .openServerList()
                .selectServer(server);
    }

    @DataProvider(name = "protocols")
    public Object[][] protocols() {
        return protocolList.stream()
                .map(protocol -> new Object[]{protocol})
                .toArray(Object[][]::new);
    }

    @Test(priority = 1, dataProvider = "protocols", description = "connect on each protocol")
    @Story("3. Protocols")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Objective: verify the VPN connects on each protocol while keeping the selected server's country

            Pre-cond: a random non-US VIP server is selected in @BeforeClass (fixes the location)

            Steps:
            1. select the protocol in the grid (D-pad); confirm the reconnect dialog if it appears
            2. press Connect (D-pad OK)
            3. verify status CONNECTED and timer running
            4. verify the real egress country matches the selected server
            5. disconnect (leave a clean state for the next protocol)""")
    public void checkProtocol(Protocols protocol) {
        new MainScreenPage(testContext)
                .selectProtocol(protocol)
                .verifyConnected()
                .verifyRealEgress(server);
    }
}
