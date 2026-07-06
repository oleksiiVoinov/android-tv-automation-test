package configs.platformConfig;

import configs.devices.Device;
import driver.AndroidContext;

import java.net.URL;

/**
 * Builds the AndroidDriver session for an Android TV device.
 * The app is assumed to be already installed on the box (installed manually or by CI via adb).
 * No auto-reset between tests: TV app state is managed by @BeforeClass/@AfterClass, mirroring the phone framework.
 */
public class AndroidConfig {

    public AndroidContext initDriver(Device device, String host, String port) throws Exception {
        device.capabilities.setCapability("appium:noReset", true);
        device.capabilities.setCapability("appium:newCommandTimeout", 0);
        device.capabilities.setCapability("appium:appPackage", device.app.appPackage);
        device.capabilities.setCapability("appium:appActivity", device.app.appActivity);
        // Launch via the exported splash. After a clean-slate reset the app lands on the
        // welcome screen; when already signed in it lands on the main screen — wait for either.
        device.capabilities.setCapability("appium:appWaitActivity",
                device.app.mainActivity + ",*TvWelcomeActivity,*TvSignInActivity");
        device.capabilities.setCapability("appium:appWaitDuration", 30000);

        System.out.println("🌐 Appium Server: " + host + ":" + port);
        System.out.println("📺 TV device: " + device.uDID + " → " + device.app.appActivity);

        return new AndroidContext(new URL("http://" + host + ":" + port + "/"), device);
    }
}
