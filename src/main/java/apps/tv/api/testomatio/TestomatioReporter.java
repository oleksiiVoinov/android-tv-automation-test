package apps.tv.api.testomatio;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Run lifecycle of the Testomat.io integration.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>disabled unless {@code -Dtestomatio=true};</li>
 *   <li>{@code -DtestomatioRunId=<uid>} reuses an existing Run instead of creating a new one;</li>
 *   <li>{@code -DtestomatioRunFile=<path>} lets several JVMs (device 1 / device 2) share one Run:
 *       the first process creates the Run and writes its uid, the others read it;</li>
 *   <li>the Run is <b>not</b> closed when the suite ends — a human finishes it in Testomat.io UI.
 *       {@code -DtestomatioFinishRun=true} restores the automatic close.</li>
 * </ul>
 *
 * Any API failure is logged and ignored: reporting must never fail a test run.
 */
public final class TestomatioReporter {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private static volatile TestomatioReporter instance;

    private final TestomatioClient client;
    private final String runUid;
    private final String runUrl;
    private final Instant startedAt = Instant.now();

    private TestomatioReporter(TestomatioClient client, String runUid, String runUrl) {
        this.client = client;
        this.runUid = runUid;
        this.runUrl = runUrl;
    }

    /**
     * Initializes reporting for the given suite. Safe to call several times — only the first call works.
     *
     * @param suiteName TestNG suite name, used in the generated Run title
     * @return active reporter or {@code null} when the integration is disabled / could not start
     */
    public static TestomatioReporter start(String suiteName) {
        if (!TestomatioConfig.isEnabled()) {
            return null;
        }
        if (!INITIALIZED.compareAndSet(false, true)) {
            return instance;
        }

        String apiKey = TestomatioConfig.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log("-D" + TestomatioConfig.ENABLED + "=true but -D" + TestomatioConfig.API_KEY
                    + " is missing — reporting is off for this run");
            return null;
        }

        TestomatioClient client = new TestomatioClient(TestomatioConfig.baseUrl(), apiKey);
        String project = TestomatioMapping.projectId();

        String uid = TestomatioConfig.runId();
        if (uid != null && !uid.isBlank()) {
            instance = new TestomatioReporter(client, uid, client.runUrl(project, uid));
            log("reporting into existing run: " + instance.runUrl);
            return instance;
        }

        Path sharedFile = sharedRunFile();
        if (sharedFile != null) {
            uid = readSharedRun(sharedFile);
            if (uid != null) {
                instance = new TestomatioReporter(client, uid, client.runUrl(project, uid));
                log("joined shared run from " + sharedFile + ": " + instance.runUrl);
                return instance;
            }
        }

        String title = TestomatioConfig.runTitle();
        if (title == null || title.isBlank()) {
            title = generateTitle(suiteName);
        }
        JSONObject run = createRun(client, title);
        if (run == null) {
            log("could not create run — reporting is off for this run");
            return null;
        }
        uid = run.optString("uid", null);
        if (uid == null || uid.isBlank()) {
            log("run created without uid — reporting is off for this run");
            return null;
        }
        String url = run.optString("url", client.runUrl(project, uid));
        if (sharedFile != null) {
            writeSharedRun(sharedFile, uid, url);
        }
        instance = new TestomatioReporter(client, uid, url);
        log("run created: " + url);
        log("run stays OPEN on purpose — finish it manually in Testomat.io when the regression is reviewed");
        return instance;
    }

    public static TestomatioReporter current() {
        return instance;
    }

    public String runUid() {
        return runUid;
    }

    public String runUrl() {
        return runUrl;
    }

    public void report(String className,
                       String methodName,
                       int parameterCount,
                       String parameterValue,
                       String fallbackTitle,
                       String suiteTitle,
                       String status,
                       long runTimeMs,
                       String message,
                       String stack,
                       JSONObject example) {
        TestomatioMapping.Entry entry = TestomatioMapping.find(className, methodName, parameterCount, parameterValue);
        String title = entry != null && entry.title() != null ? entry.title() : fallbackTitle;
        String testId = entry != null ? entry.id() : null;
        String suite = entry != null && entry.suiteTitle() != null ? entry.suiteTitle() : suiteTitle;
        if (entry == null) {
            log("not mapped: " + className + "#" + methodName + " — reported by title \"" + title + "\"");
        }
        boolean ok = client.reportTest(runUid, testId, title, suite, status, runTimeMs, message, stack, example);
        if (!ok) {
            log("failed to report " + className + "#" + methodName + " (" + status + ")");
        }
    }

    /**
     * Called at the very end of the suite. Closes the Run only when explicitly asked to.
     */
    public void complete() {
        if (TestomatioConfig.finishRun()) {
            long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
            boolean ok = client.finishRun(runUid, durationMs);
            log(ok ? "run finished automatically: " + runUrl : "could not finish run automatically: " + runUrl);
        } else {
            log("run left open for manual review: " + runUrl);
        }
    }

    /**
     * Creates the Run, pre-filled with the suites from {@link TestomatioConfig#RUN_SUITES} so that the
     * manual checks are part of the Run from the start. Falls back to the plain Reporter API call when
     * the v2 API is not available — reporting is more important than a complete Run.
     */
    static JSONObject createRun(TestomatioClient client, String title) {
        java.util.List<String> suites = TestomatioConfig.runSuiteIds(TestomatioMapping.rootSuite());
        if (!suites.isEmpty()) {
            JSONObject run = client.createRunWithSuites(TestomatioMapping.projectId(), title,
                    TestomatioConfig.runEnv(), TestomatioConfig.runKind(), suites);
            if (run != null) {
                log("run pre-filled from suites " + suites + ": " + run.optInt("tests_count")
                        + " cases, manual checks included");
                if (TestomatioConfig.runGroup() != null && !TestomatioConfig.runGroup().isBlank()) {
                    log("note: " + TestomatioConfig.RUN_GROUP + " is ignored for a pre-filled run"
                            + " (the v2 API takes a rungroup id, not a title)");
                }
                return run;
            }
            log("could not create a pre-filled run via the v2 API — falling back to the reporter API,"
                    + " the run will contain automated results only");
        }
        return client.createRun(title, TestomatioConfig.runEnv(), TestomatioConfig.runGroup(),
                TestomatioConfig.runKind());
    }

    private static String generateTitle(String suiteName) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
        String suite = suiteName == null || suiteName.isBlank() ? "Automation" : suiteName;
        return suite + " — " + stamp;
    }

    private static Path sharedRunFile() {
        String path = TestomatioConfig.runFile();
        return path == null || path.isBlank() ? null : Path.of(path);
    }

    private static synchronized String readSharedRun(Path file) {
        try {
            if (!Files.exists(file)) {
                return null;
            }
            long ageMinutes = Duration.between(Files.getLastModifiedTime(file).toInstant(), Instant.now()).toMinutes();
            if (ageMinutes > TestomatioConfig.runFileTtlMinutes()) {
                log("shared run file is stale (" + ageMinutes + " min) — creating a new run");
                return null;
            }
            JSONObject stored = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
            String uid = stored.optString("uid", null);
            return uid == null || uid.isBlank() ? null : uid;
        } catch (Exception e) {
            log("could not read shared run file " + file + ": " + e);
            return null;
        }
    }

    private static synchronized void writeSharedRun(Path file, String uid, String url) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            String json = new JSONObject().put("uid", uid).put("url", url).toString();
            Files.writeString(file, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            log("could not write shared run file " + file + ": " + e);
        }
    }

    private static void log(String message) {
        System.out.println("[testomatio] " + message);
    }
}
