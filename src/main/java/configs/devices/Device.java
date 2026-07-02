package configs.devices;

import configs.app.App;
import org.openqa.selenium.remote.DesiredCapabilities;

public abstract class Device {
    public App app;
    public Model model;
    public String uDID;
    public DesiredCapabilities capabilities = new DesiredCapabilities();

    @Override
    public String toString() {
        return "Device {" +
                "app=" + app +
                ", uDID='" + uDID + '\'' +
                '}';
    }
}
