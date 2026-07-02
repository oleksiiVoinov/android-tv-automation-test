package driver;

import configs.devices.Device;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;

public interface TestContext {

    AppiumDriver getAppiumDriver();

    AndroidDriver getAndroidDriver();

    Device getDevice();
}
