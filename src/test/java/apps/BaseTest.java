package apps;

import apps.common.CommandsADB;
import configs.AppiumConfig;
import configs.RuntimeConfig;
import configs.app.VpnApp;
import configs.devices.AndroidTv;
import configs.devices.Device;
import configs.devices.Model;
import configs.platformConfig.AndroidConfig;
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

        device = new AndroidTv(Model.GOOGLE_TV_STREAMER, uDID, new VpnApp());

        testContext = new AndroidConfig().initDriver(device, getAppiumHost(), getAppiumPort());
        appiumDriver = testContext.getAppiumDriver();
        appiumDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(1));

        // NOTE: no auto sign-in here. Reaching the main screen — logging in from the welcome/sign-in
        // pages if needed — is handled by MainScreen.navigateToMainScreen().
        System.out.println("🧪 Starting TV test class execution...");
        new CommandsADB()
                .allowVpnConnection(device.app.appPackage, device.uDID);
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
