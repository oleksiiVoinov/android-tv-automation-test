package driver;

import configs.devices.Device;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;

import java.net.URL;

public class AndroidContext extends AndroidDriver implements TestContext {
    public Device device;

    public AndroidContext(URL remoteAddress, Device device) {
        super(remoteAddress, device.capabilities);
        this.device = device;
    }

    @Override
    public AppiumDriver getAppiumDriver() {
        return this;
    }

    @Override
    public AndroidDriver getAndroidDriver() {
        return this;
    }

    @Override
    public Device getDevice() {
        return this.device;
    }
}
