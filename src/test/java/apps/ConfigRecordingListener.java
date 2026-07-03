package apps;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.AndroidStartScreenRecordingOptions;
import io.qameta.allure.Allure;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Android TV port of the phone framework's recording listener.
 * <p>
 * Before each method (config + test) it starts screen recording and an app-filtered logcat stream.
 * After the method it stops recording and, on failure:
 * <ul>
 *   <li>attaches the full app-only logcat captured during the test</li>
 *   <li>dumps the crash buffer (FATAL EXCEPTION / native tombstone)</li>
 *   <li>dumps the system buffer (contains "ANR in ...")</li>
 *   <li>extracts the ANR context block (Reason / Load / CPU) — works without root</li>
 *   <li>auto-diagnoses the failure (CRASH / ANR / ASSERTION) and builds a BUG REPORT</li>
 *   <li>attaches a failure screenshot and the video</li>
 * </ul>
 * On success the video is attached only when {@code -DvideoRecord=true}.
 * <p>
 * Differences from the phone version: video size is landscape (TV), and there is no Slack
 * notification (the TV project has no SlackNotifier/Protocols/Server dependencies).
 */
public class ConfigRecordingListener implements IInvokedMethodListener {
    private static final Duration VIDEO_LIMIT = Duration.ofMinutes(25);
    private static final int VIDEO_BIT_RATE = 1_500_000;
    private static final String VIDEO_SIZE = "1280x720"; // landscape for Android TV
    private static final String SYSTEM_LOGCAT_LINES = "5000";
    private static final boolean RECORD_ALL_TESTS =
            Boolean.parseBoolean(System.getProperty("videoRecord"));

    private static final ThreadLocal<Boolean> RECORDING_STARTED = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Process> LOGCAT_PROCESS = new ThreadLocal<>();
    private static final ThreadLocal<ByteArrayOutputStream> LOGCAT_BUFFER = new ThreadLocal<>();
    private static final ThreadLocal<Thread> LOGCAT_DRAINER = new ThreadLocal<>();

    private enum BugType { CRASH, ANR, ASSERTION }

    private static final Pattern CRASH_PATTERN = Pattern.compile(
            "FATAL EXCEPTION|Fatal signal \\d+|signal \\d+ \\(SIG|>>> .+ <<<");
    private static final Pattern ANR_PATTERN = Pattern.compile(
            "ANR in |Application Not Responding|Input dispatching timed out");

    @Override
    public void beforeInvocation(IInvokedMethod invokedMethod, ITestResult testResult) {
        Object instance = testResult.getInstance();
        if (!(instance instanceof BaseTest base)) return;

        AndroidDriver driver = (AndroidDriver) base.appiumDriver;
        if (driver == null || driver.getSessionId() == null) return;

        try {
            driver.startRecordingScreen(
                    new AndroidStartScreenRecordingOptions()
                            .withTimeLimit(VIDEO_LIMIT)
                            .withBitRate(VIDEO_BIT_RATE)
                            .withVideoSize(VIDEO_SIZE)
            );
            RECORDING_STARTED.set(true);
        } catch (Exception e) {
            RECORDING_STARTED.set(false);
            System.out.println("[video] failed to start recording");
            e.printStackTrace(System.out);
        }

        startLogcat(base.device.uDID, base.device.app.appPackage);
    }

    @Override
    public void afterInvocation(IInvokedMethod invokedMethod, ITestResult testResult) {
        Object instance = testResult.getInstance();
        if (!(instance instanceof BaseTest base)) return;

        AndroidDriver driver = (AndroidDriver) base.appiumDriver;
        if (driver == null || driver.getSessionId() == null) return;

        String videoBase64 = null;
        if (Boolean.TRUE.equals(RECORDING_STARTED.get())) {
            try {
                videoBase64 = driver.stopRecordingScreen();
            } catch (Exception e) {
                System.out.println("[video] failed to stop recording");
                e.printStackTrace(System.out);
            } finally {
                RECORDING_STARTED.remove();
            }
        }

        boolean isFailed = testResult.getStatus() == ITestResult.FAILURE;

        String methodName = invokedMethod != null && invokedMethod.getTestMethod() != null
                ? invokedMethod.getTestMethod().getMethodName()
                : "method";

        if (!isFailed) {
            if (RECORD_ALL_TESTS && videoBase64 != null && !videoBase64.isEmpty()) {
                attachVideo(methodName + " - video", videoBase64);
            }
            stopAndAttachLogcat(methodName + " - logcat");
            return;
        }

        // =========================== FAILURE PATH ===========================
        String udid = base.device.uDID;

        String appLog = stopLogcatAndGet();
        String crashLog = runAdb(5, "-s", udid, "logcat", "-b", "crash", "-d");
        String systemLog = runAdb(6, "-s", udid, "logcat", "-b", "main,system", "-d", "-t", SYSTEM_LOGCAT_LINES);
        String anrBlock = extractAnrBlock(systemLog);

        String pageSource = "";
        try {
            pageSource = driver.getPageSource();
        } catch (Exception ignored) {
        }

        BugType type = detect(appLog, crashLog, systemLog);
        String reason = extractReason(type, crashLog, systemLog);
        String label = "[" + type + "]";

        try {
            byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Allure.addAttachment(methodName + " " + label + " - failure screenshot",
                    "image/png", new ByteArrayInputStream(screenshot), ".png");
        } catch (Exception e) {
            System.out.println("[screenshot] failed to attach");
            e.printStackTrace(System.out);
        }

        if (videoBase64 != null && !videoBase64.isEmpty()) {
            attachVideo(methodName + " " + label + " - failure video", videoBase64);
        }

        String report = buildBugReport(methodName, type, reason,
                base.device.app.appPackage, crashLog, anrBlock);
        attachText(methodName + " " + label + " - BUG REPORT", report);

        if (!appLog.isBlank()) {
            attachText(methodName + " - app logcat (full test)", appLog);
        }
        if (!systemLog.isBlank()) {
            attachText(methodName + " - system logcat", systemLog);
        }
        if (!pageSource.isBlank()) {
            attachText(methodName + " - UI page source", pageSource);
        }
    }

    // -------------------------------------------------------------------------
    //  Auto-diagnosis
    // -------------------------------------------------------------------------

    private BugType detect(String appLog, String crashLog, String systemLog) {
        String haystack = (crashLog + "\n" + systemLog + "\n" + appLog);
        if (CRASH_PATTERN.matcher(haystack).find()) return BugType.CRASH;
        if (ANR_PATTERN.matcher(haystack).find()) return BugType.ANR;
        return BugType.ASSERTION;
    }

    private String extractReason(BugType type, String crashLog, String systemLog) {
        if (type == BugType.ANR) {
            Matcher m = Pattern.compile("ANR in [^\\n]+").matcher(systemLog);
            if (m.find()) return m.group().trim();
            m = Pattern.compile("Input dispatching timed out[^\\n]*").matcher(systemLog);
            if (m.find()) return m.group().trim();
            return "Application Not Responding (dialog detected)";
        }
        if (type == BugType.CRASH) {
            String[] lines = (crashLog.isBlank() ? systemLog : crashLog).split("\\r?\\n");
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains("FATAL EXCEPTION")) {
                    for (int j = i + 1; j < Math.min(i + 4, lines.length); j++) {
                        String s = stripLogPrefix(lines[j]);
                        if (!s.isBlank() && !s.startsWith("Process:")) return s;
                    }
                    return stripLogPrefix(lines[i]);
                }
            }
            Matcher m = Pattern.compile("Fatal signal \\d+[^\\n]*").matcher(crashLog + systemLog);
            if (m.find()) return m.group().trim();
        }
        return "";
    }

    private String stripLogPrefix(String line) {
        int idx = line.indexOf(": ");
        return (idx >= 0 && idx < line.length() - 2) ? line.substring(idx + 2).trim() : line.trim();
    }

    private String buildBugReport(String methodName, BugType type, String reason, String appPackage,
                                  String crashLog, String anrBlock) {
        return "==================== BUG REPORT ====================\n" +
                "Test:      " + methodName + "\n" +
                "Detected:  " + type + "\n" +
                "Package:   " + appPackage + "\n" +
                "Reason:    " + (reason.isBlank() ? "(assertion failure — see TestNG stacktrace in Allure)" : reason) + "\n" +
                "Generated: " + java.time.LocalDateTime.now() + "\n" +
                "Full logs: see attachments '" + methodName + " - app logcat (full test)' and '" + methodName + " - system logcat'\n" +
                "===================================================\n\n" +
                section("CRASH BUFFER (FATAL EXCEPTION / native tombstone)", crashLog) +
                section("ANR (system_server: Reason / Load / CPU usage)", anrBlock);
    }

    private String extractAnrBlock(String systemLog) {
        if (systemLog == null || systemLog.isBlank()) return "";
        String[] lines = systemLog.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("ANR in ") || lines[i].contains("Application Not Responding")) {
                int end = Math.min(i + 40, lines.length);
                for (int j = i; j < end; j++) sb.append(lines[j]).append('\n');
                sb.append('\n');
            }
        }
        if (sb.length() == 0) return grep(systemLog, ANR_PATTERN);
        return sb.toString();
    }

    private String section(String title, String body) {
        if (body == null || body.isBlank()) return "----- " + title + " -----\n(none)\n\n";
        return "----- " + title + " -----\n" + body.trim() + "\n\n";
    }

    private String grep(String text, Pattern pattern) {
        if (text == null || text.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\\r?\\n")) {
            if (pattern.matcher(line).find()) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    //  Attachment helpers
    // -------------------------------------------------------------------------

    private void attachText(String name, String content) {
        if (content == null || content.isBlank()) return;
        Allure.addAttachment(name, "text/plain",
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), ".txt");
    }

    private void attachVideo(String name, String videoBase64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(videoBase64);
            Allure.addAttachment(name, "video/mp4", new ByteArrayInputStream(decoded), ".mp4");
        } catch (Exception e) {
            System.out.println("[video] failed to attach video");
            e.printStackTrace(System.out);
        }
    }

    // -------------------------------------------------------------------------
    //  adb helpers
    // -------------------------------------------------------------------------

    private String runAdb(int timeoutSeconds, String... args) {
        try {
            String[] cmd = new String[args.length + 1];
            cmd[0] = "adb";
            System.arraycopy(args, 0, cmd, 1, args.length);
            Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            byte[] out = proc.getInputStream().readAllBytes();
            proc.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            return new String(out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("[adb] command failed: " + e.getMessage());
            return "";
        }
    }

    // -------------------------------------------------------------------------
    //  logcat stream lifecycle
    // -------------------------------------------------------------------------

    private void startLogcat(String udid, String appPackage) {
        try {
            new ProcessBuilder("adb", "-s", udid, "logcat", "-b", "main,system,crash", "-c")
                    .start()
                    .waitFor(2, TimeUnit.SECONDS);

            Process pidProc = new ProcessBuilder("adb", "-s", udid, "shell", "pidof", appPackage).start();
            pidProc.waitFor(2, TimeUnit.SECONDS);
            String pid = new String(pidProc.getInputStream().readAllBytes()).trim();

            ProcessBuilder pb;
            if (!pid.isEmpty() && pid.matches("\\d+.*")) {
                String firstPid = pid.split("\\s+")[0];
                pb = new ProcessBuilder("adb", "-s", udid, "logcat", "-v", "threadtime", "--pid=" + firstPid);
            } else {
                pb = new ProcessBuilder("adb", "-s", udid, "logcat", "-v", "threadtime");
            }
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            LOGCAT_PROCESS.set(proc);

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            LOGCAT_BUFFER.set(buffer);
            Thread drainer = new Thread(() -> {
                try {
                    proc.getInputStream().transferTo(buffer);
                } catch (Exception ignored) {
                }
            }, "logcat-drainer");
            drainer.setDaemon(true);
            drainer.start();
            LOGCAT_DRAINER.set(drainer);

        } catch (Exception e) {
            System.out.println("[logcat] failed to start: " + e.getMessage());
        }
    }

    private String stopLogcatAndGet() {
        Process proc = LOGCAT_PROCESS.get();
        Thread drainer = LOGCAT_DRAINER.get();
        ByteArrayOutputStream buffer = LOGCAT_BUFFER.get();
        try {
            if (proc != null) {
                proc.destroy();
                proc.waitFor(3, TimeUnit.SECONDS);
            }
            if (drainer != null) drainer.join(3_000);
            return buffer != null ? buffer.toString(StandardCharsets.UTF_8) : "";
        } catch (Exception e) {
            return buffer != null ? buffer.toString(StandardCharsets.UTF_8) : "";
        } finally {
            LOGCAT_PROCESS.remove();
            LOGCAT_DRAINER.remove();
            LOGCAT_BUFFER.remove();
        }
    }

    private void stopAndAttachLogcat(String attachmentName) {
        String log = stopLogcatAndGet();
        attachText(attachmentName, log);
    }
}
