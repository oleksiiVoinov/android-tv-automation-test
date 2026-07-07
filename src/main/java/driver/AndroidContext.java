package driver;

import configs.devices.Device;
import configs.environment.EnvironmentConfig;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;

import java.net.URL;

public class AndroidContext extends AndroidDriver implements TestContext {
    public Device device;
    public EnvironmentConfig environment;

    public AndroidContext(URL remoteAddress, Device device, EnvironmentConfig environment) {
        super(remoteAddress, device.capabilities);
        this.device = device;
        this.environment = environment;
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

    @Override
    public EnvironmentConfig getEnvironment() {
        return this.environment;
    }
}
