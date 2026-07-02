package apps.common;

import io.qameta.allure.Step;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class CommandsADB {

    private void logSuccess(String message) {
        System.out.println("✅ " + message);
    }

    private void logFailure(String command, int exitCode) {
        System.err.println("❌ ADB command failed with exit code " + exitCode + ": " + command);
    }

    public CommandsADB executeADBCommand(String command, String successMessage) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logSuccess(successMessage);
            } else {
                logFailure(command, exitCode);
            }
        } catch (Exception e) {
            System.err.println("❌ Exception during command execution: " + command);
            e.printStackTrace();
        }
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Step("Clear app data (start from a clean slate)")
    public CommandsADB clearAppData(String packageName, String udid) {
        String command = "adb -s " + udid + " shell pm clear " + packageName + " --user 0";
        return executeADBCommand(command, "🧹 Cleared app data for " + packageName);
    }

    @Step("Grant POST_NOTIFICATIONS permission")
    public CommandsADB grantPermissionNotification(String packageName, String udid) {
        String command = "adb -s " + udid + " shell pm grant " + packageName + " android.permission.POST_NOTIFICATIONS";
        return executeADBCommand(command, "🔔 Notification permission granted for " + packageName);
    }

    @Step("Allow VPN connection request (skip the system consent dialog)")
    public CommandsADB allowVpnConnection(String packageName, String udid) {
        String command = "adb -s " + udid + " shell appops set " + packageName + " ACTIVATE_VPN allow";
        return executeADBCommand(command, "🔗 VPN permission granted for " + packageName);
    }

    @Step("Force-stop app")
    public CommandsADB closeApp(String packageName, String udid) {
        String command = "adb -s " + udid + " shell am force-stop " + packageName;
        return executeADBCommand(command, "🛑 App " + packageName + " was closed");
    }

    public boolean isAppInstalled(String packageName, String udid) {
        try {
            Process process = Runtime.getRuntime().exec("adb -s " + udid + " shell pm list packages");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(packageName)) {
                    return true;
                }
            }
            process.waitFor();
            return false;
        } catch (Exception e) {
            System.err.println("❌ Error checking app installation: " + e.getMessage());
            return false;
        }
    }

    public boolean isDeviceAlive(String udid) {
        Process process = null;
        try {
            process = new ProcessBuilder("adb", "devices")
                    .redirectErrorStream(true)
                    .start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(udid) && line.contains("device")) {
                    logSuccess("📺 Device " + udid + " is ONLINE");
                    return true;
                }
            }
            System.err.println("❌ Device " + udid + " NOT ready in adb devices");
            return false;
        } catch (Exception e) {
            System.err.println("❌ ADB is not responding");
            e.printStackTrace();
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
