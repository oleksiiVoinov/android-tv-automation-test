package configs.devices;

import configs.app.App;

public class AndroidTv extends Device {

    public AndroidTv(Model model, String uDID, App app) {
        this.model = model;
        this.app = app;
        this.uDID = uDID;

        this.capabilities.setCapability("appium:automationName", "UiAutomator2");
        this.capabilities.setCapability("appium:udid", uDID);
        // Android TV navigation is D-pad driven; no touch. See DpadNavigator.
        this.capabilities.setCapability("appium:appPackage", app.appPackage);
        this.capabilities.setCapability("appium:appActivity", app.appActivity);
    }
}
