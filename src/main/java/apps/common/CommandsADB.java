package apps.common;

import io.qameta.allure.Step;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

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

    private boolean isNetworkDevice(String udid) {
        return udid != null && udid.contains(":");
    }

    /** Runs an adb command with a timeout and returns its combined stdout (empty on error/timeout). */
    private String runAdbOutput(int seconds, String... args) {
        Process process = null;
        try {
            String[] cmd = new String[args.length + 1];
            cmd[0] = "adb";
            System.arraycopy(args, 0, cmd, 1, args.length);
            process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor(seconds, TimeUnit.SECONDS);
            return out;
        } catch (Exception e) {
            System.err.println("❌ adb output command failed: " + e.getMessage());
            return "";
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * Ground-truth egress geolocation: makes an HTTP request FROM the device (raw HTTP over
     * toybox `nc`, since curl/wget aren't present) to ip-api.com. Because the app's VPN covers
     * all UIDs, the shell's traffic egresses through the tunnel too — so the returned IP/country
     * is what the outside world actually sees for the device. Returns the raw JSON body.
     * <p>
     * HTTP (not HTTPS) because nc can't do TLS and ip-api.com's free tier is HTTP-only; this is a
     * non-sensitive geo lookup, and the point is exactly that it travels through the tunnel.
     */
    @Step("Read device egress geo (through the tunnel) via on-device HTTP")
    public String deviceEgressJson(String udid) {
        String remote = "printf 'GET /json HTTP/1.0\\r\\nHost: ip-api.com\\r\\nUser-Agent: curl\\r\\n\\r\\n'"
                + " | nc -w 8 ip-api.com 80";
        String out = runAdbOutput(20, "-s", udid, "shell", remote);
        int start = out.indexOf('{');
        int end = out.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Could not read device egress JSON from ip-api.com. Raw output:\n" + out);
        }
        return out.substring(start, end + 1);
    }

    /** Runs a command with a hard timeout so a flaky adb call can't hang the run forever. */
    private void execWithTimeout(String command, int seconds, String successMessage) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(command);
            if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                System.err.println("⏱️ adb command timed out (" + seconds + "s): " + command);
                return;
            }
            logSuccess(successMessage);
        } catch (Exception e) {
            System.err.println("❌ Exception during command: " + command + " → " + e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * Clean restart of the local adb server + reconnect a network device.
     * Fixes the common "protocol fault / Connection reset by peer" broken-server state.
     */
    @Step("Reconnect network adb device {udid}")
    public CommandsADB reconnect(String udid) {
        execWithTimeout("adb kill-server", 10, "adb server killed");
        execWithTimeout("adb start-server", 15, "adb server started");
        if (isNetworkDevice(udid)) {
            execWithTimeout("adb connect " + udid, 10, "adb connect " + udid);
        }
        return this;
    }

    /**
     * Ensures the device is online, self-healing a dropped network adb connection.
     * Cheap path first (plain connect), then a full server restart, polling until the timeout.
     */
    @Step("Ensure device {udid} is online")
    public void ensureDeviceOnline(String udid, Duration timeout) {
        if (isDeviceAlive(udid)) {
            return;
        }
        if (isNetworkDevice(udid)) {
            execWithTimeout("adb connect " + udid, 10, "adb connect " + udid);
            if (isDeviceAlive(udid)) {
                return;
            }
            reconnect(udid); // escalate: kill/start-server + connect
        }

        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (isDeviceAlive(udid)) {
                return;
            }
            if (isNetworkDevice(udid)) {
                execWithTimeout("adb connect " + udid, 10, "retry adb connect " + udid);
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        throw new IllegalStateException(
                "Device " + udid + " is not online after " + timeout.getSeconds() + "s. "
                        + "Is the box awake / on the network? Check its IP in Settings → Network.");
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
