package apps.tv.regression;

import apps.common.CommandsADB;
import configs.RuntimeConfig;
import configs.app.VpnApp;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.time.Duration;

/**
 * Reinstalls the TV app from the APK in {@code src/main/java/apps/installation}.
 * Standalone (pure adb, no Appium session) — reinstalling wipes login, so this is NOT part of the
 * regression suite; run it on demand. After it, sign in again (the API precondition handles that).
 */
@Epic("Android TV")
@Feature("1. Installation")
public class ReinstallTest {

    private static final String APK_DIR = "src/main/java/apps/installation";

    @Test(priority = 1, description = "reinstall the TV app from the local APK")
    @Story("1. Reinstall app")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Objective: reinstall the app via adb from the local APK

            Steps:
            1. ensure the device is online
            2. uninstall the app if it is installed
            3. install the APK from src/main/java/apps/installation
            4. verify the app is installed""")
    public void reinstallApp() throws InterruptedException {
        String udid = RuntimeConfig.getRequired("udid");
        String appPackage = new VpnApp().appPackage;
        CommandsADB adb = new CommandsADB();

        adb.ensureDeviceOnline(udid, Duration.ofSeconds(30));

        if (adb.isAppInstalled(appPackage, udid)) {
            adb.removeApp(appPackage, udid);
        }
        Assert.assertFalse(adb.isAppInstalled(appPackage, udid),
                "App is still installed after uninstall");

        File apk = resolveApk();
        System.out.println("📦 Installing APK: " + apk.getName());
        adb.installApp(apk.getAbsolutePath(), udid);

        Thread.sleep(10000);
        Assert.assertTrue(adb.isAppInstalled(appPackage, udid),
                "App is not installed after install");
    }

    /** Picks the (single) APK from the installation folder — resilient to version-named files. */
    private File resolveApk() {
        File dir = new File(APK_DIR);
        File[] apks = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".apk"));
        if (apks == null || apks.length == 0) {
            throw new IllegalStateException("No APK found in " + dir.getAbsolutePath());
        }
        return apks[0];
    }
}
