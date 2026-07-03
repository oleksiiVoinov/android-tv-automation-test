package apps;

import apps.common.CommandsADB;
import apps.tv.pages.TvSignInPage;
import configs.AppiumConfig;
import configs.RuntimeConfig;
import configs.app.TvVpnApp;
import configs.devices.AndroidTv;
import configs.devices.Device;
import configs.devices.Model;
import configs.platformConfig.AndroidTvConfig;
import driver.TestContext;
import io.appium.java_client.AppiumDriver;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Listeners;

import java.time.Duration;

@Listeners(ConfigRecordingListener.class)
public class BaseTest {
    private static final AppiumConfig APPIUM_CONFIG = AppiumConfig.fromRuntimeConfig();

    public Device device;
    public TestContext testContext;
    public AppiumDriver appiumDriver;

    @BeforeSuite(alwaysRun = true, description = "ensure device online, then start appium if managed")
    public void beforeSuite() {
        // Network adb to the TV box drops often — self-heal before the run.
        new CommandsADB().ensureDeviceOnline(RuntimeConfig.getRequired("udid"), Duration.ofSeconds(30));
        APPIUM_CONFIG.startIfNeeded();
    }

    @BeforeClass(description = "start TV test")
    protected void tearUp() throws Exception {
        String uDID = RuntimeConfig.getRequired("udid");
        System.out.printf("🔧 TV device → UDID: %s%n", uDID);

        device = new AndroidTv(Model.GOOGLE_TV_STREAMER, uDID, new TvVpnApp());

        // Precondition — start every test from a clean slate:
        // wipe app data (logs out), grant notifications, pre-approve the VPN consent dialog.
/*        new CommandsADB()
                .clearAppData(device.app.appPackage, uDID);*/

        testContext = new AndroidTvConfig().initDriver(device, getAppiumHost(), getAppiumPort());
        appiumDriver = testContext.getAppiumDriver();
        appiumDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(1));

        // A clean slate lands on the welcome screen — sign in via the account API
        // (device-code approve), so the app reaches the main screen with a premium session.
        // Credentials come from runtime config (local.properties / -D), never hardcoded.
/*        new TvSignInPage(testContext).ensureSignedIn(
                RuntimeConfig.getRequired("tvEmail"),
                RuntimeConfig.getRequired("tvPassword"));*/

        System.out.println("🧪 Starting TV test class execution...");
    }

    protected String getAppiumHost() {
        return APPIUM_CONFIG.getAppiumHost();
    }

    protected String getAppiumPort() {
        return APPIUM_CONFIG.getAppiumPort();
    }

    @AfterClass(alwaysRun = true, description = "end TV test")
    protected void tearDown() {
        if (testContext != null && testContext.getAppiumDriver() != null) {
            testContext.getAppiumDriver().quit();
        }
    }

    @AfterSuite(alwaysRun = true, description = "stop appium if managed")
    public void afterSuite() {
        APPIUM_CONFIG.stopIfNeeded();
    }
}
