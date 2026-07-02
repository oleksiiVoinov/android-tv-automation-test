package configs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class AppiumConfig {
    private final String appiumHost;
    private final String appiumPort;
    private final boolean manageAppium;
    private final boolean appiumLogsEnabled;
    private Process appiumProcess;

    public AppiumConfig(String host, String port, boolean manageAppium, boolean appiumLogsEnabled) {
        this.appiumHost = host;
        this.appiumPort = port;
        this.manageAppium = manageAppium;
        this.appiumLogsEnabled = appiumLogsEnabled;
    }

    public static AppiumConfig fromRuntimeConfig() {
        boolean manageAppium = RuntimeConfig.getBoolean("manageAppium", false);
        String configuredPort = RuntimeConfig.getOptional("appiumPort");

        return new AppiumConfig(
                RuntimeConfig.getOptional("appiumHost", "127.0.0.1"),
                resolvePort(configuredPort, manageAppium),
                manageAppium,
                RuntimeConfig.getBoolean("appiumLogs", false)
        );
    }

    private static String resolvePort(String configuredPort, boolean manageAppium) {
        if (configuredPort != null) {
            return configuredPort;
        }
        return manageAppium ? new Port().findAvailablePort() : "4732";
    }

    public String getAppiumHost() {
        return appiumHost;
    }

    public String getAppiumPort() {
        return appiumPort;
    }

    public void startIfNeeded() {
        if (!manageAppium || appiumProcess != null) {
            return;
        }

        String[] command = {
                "appium",
                "-a", this.appiumHost,
                "-p", this.appiumPort,
                "--log-timestamp",
                "--local-timezone",
                "--relaxed-security"
        };

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        try {
            appiumProcess = processBuilder.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (appiumLogsEnabled) {
            Thread logReader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(appiumProcess.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        System.out.println("[APPIUM] " + line);
                    }
                } catch (IOException ignored) {
                }
            });
            logReader.setDaemon(true);
            logReader.start();
        } else {
            System.out.println("[APPIUM] logging is DISABLED");
        }

        System.out.println("Appium server started on port " + appiumPort);

        try {
            Thread.sleep(10_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void stopIfNeeded() {
        if (manageAppium && appiumProcess != null) {
            appiumProcess.destroy();
            appiumProcess = null;
            System.out.println("Appium server stopped...");
        }
    }
}
