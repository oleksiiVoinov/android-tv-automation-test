package apps.tv.regression;

import apps.BaseTest;
import apps.tv.api.serverlist.ServerV7;
import apps.tv.api.serverlist.ServerList;
import apps.tv.pages.MainScreenPage;
import apps.tv.pages.ServerListPage;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@Epic("Android TV")
public class ServerListTest extends BaseTest {

    ServerListPage list;

    @Story("4. Server List")
    @BeforeClass()
    public void precondition() {
        list = new MainScreenPage(testContext)
                .navigateToMainScreen()
                .openServerList();
    }

    @Test(priority = 1, description = "validate server list elements")
    @Story("4. Server List")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Objective: verify the server list screen shows its key elements
            
            Steps:
            1. go to main screen
            2. open the server list (location selector)
            3. verify title, search, sort and server rows are displayed""")
    public void validation() {
        new ServerListPage(testContext).verifyDisplayed();
    }

    @Test(priority = 2, description = "change server list sorting")
    @Story("4. Server List")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Objective: verify the sort control changes the server list ordering mode
            
            Steps:
            1. open the server list
            2. sort A - Z, verify the sort label
            3. sort Z - A, verify the sort label""")
    public void sortServer() {
        list.sortBy(ServerListPage.Sort.A_TO_Z);
        Assert.assertEquals(list.currentSortMode(), ServerListPage.Sort.A_TO_Z.label,
                "Sort mode did not switch to A - Z");

        list.sortBy(ServerListPage.Sort.Z_TO_A);
        Assert.assertEquals(list.currentSortMode(), ServerListPage.Sort.Z_TO_A.label,
                "Sort mode did not switch to Z - A");
    }

    @Test(priority = 3, description = "search a server and connect to it")
    @Story("4. Server List")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Objective: verify searching filters servers and a result can be selected to connect
            
            Steps:
            1. pick a random VIP server from the API (to get a real country)
            2. open the server list and open search
            3. type the country and select the matching result
            4. verify the app connects (redirect to main, status CONNECTED)""")
    public void searchServer() throws Exception {
        // Non-US: US clusters show as "US - City" in the UI, which diverges from the country name.
        ServerV7 server = new ServerList(testContext).getRandomNonUsServer();
        String country = server.getLocalizedCountryName();

        list.search(country)
                .selectSearchResult()
                .verifyConnected()
                .verifyRealEgress(server)
                .disconnect();
    }

/*    @Test(priority = 4, description = "select a specific server and verify real egress country")
    @Story("4. Server List")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Objective: verify connecting to a chosen server routes real traffic through its country

            Steps:
            1. pick a random VIP server from the API
            2. open the server list, scroll to the server and select it
            3. verify connected, then verify the real egress country matches the server
            4. disconnect""")
    public void selectServer() throws Exception {
        ServerV7 server = new TvServerList(testContext).getRandomServer();

        new ConnectTvPage(testContext)
                .navigateToMainScreen()
                .openServerList()
                .selectServer(server.getAliasName())
                .verifyConnected()
                .verifyRealEgress(server)
                .disconnect();
    }*/
}
