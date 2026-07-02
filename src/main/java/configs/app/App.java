package configs.app;

public abstract class App {
    public String bundleId;
    public String appPackage;
    public String appActivity;   // exported launcher entry point (what Appium starts)
    public String mainActivity;  // the real screen we wait for after launch
    public String name;

    @Override
    public String toString() {
        return "'" + name + "'";
    }
}
