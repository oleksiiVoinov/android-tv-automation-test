package apps.tv.regression;

import apps.BaseTest;
import apps.tv.api.serverlist.ServerList;
import apps.tv.api.serverlist.ServerV7;
import apps.tv.pages.MainScreenPage;
import io.qameta.allure.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@Epic("Android TV")
@Feature("6. Server List")
public class RecentlyUsedServersTest extends BaseTest {

    private ServerV7 server;

    @Story("07. Recently used")
    @BeforeClass()
    public void precondition() throws Exception {
        server = new ServerList(testContext).getRandomNonUsServer();

        // Connect once through ALL Servers → the server lands at the top of Recently used.
        // Then disconnect, so the test really (re)connects from Recently used.
        new MainScreenPage(testContext)
                .navigateToMainScreen()
                .openServerList()
                .selectServer(server)
                .verifyConnected()
                .disconnect();
    }

    @Test(priority = 1, description = "recently used shows the last connected server")
    @Story("07. Recently used")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Objective: verify the server the user connected to shows up at the top of Recently used

            Pre-cond: connected to a random non-US VIP server via ALL Servers, then disconnected

            Steps:
            1. open the server list
            2. verify the server is the first row of Recently used
            3. go back to the main screen""")
    public void recentlyUsedShowsLastServer() {
        new MainScreenPage(testContext)
                .openServerList()
                .verifyRecentlyUsed(server.getAliasName())
                .backToMainScreen();
    }

    @Test(priority = 2, description = "reconnect to the server from recently used")
    @Story("07. Recently used")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Objective: verify selecting a Recently used server connects to it right away

            Pre-cond: the server is in Recently used, VPN disconnected

            Steps:
            1. open the server list and select the server in Recently used (D-pad)
            2. verify status CONNECTED and timer running (no cluster popup in between)
            3. verify the real egress matches the server
            4. disconnect""")
    public void reconnectToRecentServer() {
        new MainScreenPage(testContext)
                .openServerList()
                .selectRecentServer(server)
                .verifyConnected()
                .verifyRealEgress(server)
                .disconnect();
    }
}
