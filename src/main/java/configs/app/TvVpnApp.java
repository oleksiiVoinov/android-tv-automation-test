package configs.app;

public class TvVpnApp extends App {
    public TvVpnApp() {
        this.bundleId = "mobi.mobilejump.freevpn";
        this.name = "superVpnTv";
        this.appPackage = "com.free.vpn.super.hotspot.open";
        // Leanback launcher entry (exported); it routes to the main screen below.
        this.appActivity = "com.superunlimited.androidTv.presentation.splash.TvSplashActivity";
        this.mainActivity = "com.superunlimited.androidTv.presentation.main.TvMainActivity";
    }
}
