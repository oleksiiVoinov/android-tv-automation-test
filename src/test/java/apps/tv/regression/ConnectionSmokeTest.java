package apps.tv.regression;

import apps.BaseTest;
import apps.tv.pages.ConnectTvPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

@Epic("Android TV")
public class ConnectionSmokeTest extends BaseTest {

    @Test(priority = 1, description = "validation feature TV connect on Auto")
    @Feature("1. Connection")
    @Story("1. Connect on Auto")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Objective: verify the VPN connects on the default Auto protocol from the TV main screen

            Steps:
            1. go to main screen
            2. verify disconnected state
            3. open location list, wait for servers to load, go back
            4. press Connect (D-pad OK)
            5. verify status CONNECTED and timer running""")
    public void connectsOnAutoProtocol() {
        new ConnectTvPage(testContext)
                .navigateToMainScreen()
                .verifyDisconnected()
                .openLocationListAndReturn()
                .pressConnect()
                .verifyConnected();
    }
}
