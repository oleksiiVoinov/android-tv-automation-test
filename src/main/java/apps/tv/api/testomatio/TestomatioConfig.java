package apps.tv.api.testomatio;

import configs.RuntimeConfig;

import java.util.Arrays;
import java.util.List;

/**
 * Runtime configuration of the Testomat.io integration.
 *
 * <p>All values are resolved through {@link RuntimeConfig}, i.e. via the standard project chain:
 * {@code -Dkey=value} → gradle properties → env → {@code local.properties} → defaults.
 *
 * <p>Main switch is {@code testomatio} — when it is {@code false} (default) nothing is sent
 * anywhere and the whole integration is a no-op.
 */
public final class TestomatioConfig {

    /** Master switch: {@code -Dtestomatio=true} enables Run reporting. */
    public static final String ENABLED = "testomatio";
    /** Project API key from Testomat.io → Settings → Project → API key ({@code tstmt_...}). */
    public static final String API_KEY = "testomatioApiKey";
    /** Reuse an already created Run instead of creating a new one (uid of the Run). */
    public static final String RUN_ID = "testomatioRunId";
    /** Optional Run title; a generated one is used when omitted. */
    public static final String RUN_TITLE = "testomatioRunTitle";
    /** Optional Run group (shown as a group in Testomat.io Runs list). */
    public static final String RUN_GROUP = "testomatioRunGroup";
    /** Optional env label of the Run (defaults to the {@code environment} property). */
    public static final String RUN_ENV = "testomatioEnv";
    /**
     * Kind of the created Run: {@code mixed} (default) | {@code automated} | {@code manual}.
     *
     * <p>Only a {@code manual} or {@code mixed} Run can be reviewed by hand in Testomat.io —
     * such a Run has a "Continue" mode where a human may re-check a broken autotest and set the real
     * status. An {@code automated} Run is read-only in the UI, so {@code mixed} is the default here.
     */
    public static final String RUN_KIND = "testomatioRunKind";
    /**
     * Comma separated suite ids the Run must contain from the very start.
     *
     * <p>Defaults to the automation root suite from the mapping ({@code root_suite}) — that suite also
     * holds the {@code Manual} folder, so the Run contains both the automated cases and the manual
     * checks, and QA can fill the manual ones in the same Run instead of creating a second one.
     *
     * <p>{@code -DtestomatioRunSuites=none} brings back the old behaviour: the Run then contains only
     * what the autotests actually reported.
     *
     * <p>Pre-filling needs the v2 API ({@code POST /api/v2/{project}/runs}); the Reporter API cannot
     * attach suites to a Run.
     */
    public static final String RUN_SUITES = "testomatioRunSuites";
    /**
     * Close the Run automatically when the suite ends.
     * Default {@code false} — by design a human closes the Run in Testomat.io UI.
     */
    public static final String FINISH_RUN = "testomatioFinishRun";
    /**
     * Optional path to a file used to share one Run between several JVMs
     * (e.g. {@code regressionDevice1Test} + {@code regressionDevice2Test}).
     */
    public static final String RUN_FILE = "testomatioRunFile";
    /** How long a Run stored in {@link #RUN_FILE} may be reused, minutes. */
    public static final String RUN_FILE_TTL = "testomatioRunFileTtlMinutes";
    /** Base url of Testomat.io instance. */
    public static final String BASE_URL = "testomatioBaseUrl";

    private TestomatioConfig() {
    }

    public static boolean isEnabled() {
        return RuntimeConfig.getBoolean(ENABLED, false);
    }

    public static String apiKey() {
        return RuntimeConfig.getOptional(API_KEY);
    }

    public static String runId() {
        return RuntimeConfig.getOptional(RUN_ID);
    }

    public static String runTitle() {
        return RuntimeConfig.getOptional(RUN_TITLE);
    }

    public static String runGroup() {
        return RuntimeConfig.getOptional(RUN_GROUP);
    }

    public static String runEnv() {
        return RuntimeConfig.getOptional(RUN_ENV, RuntimeConfig.getOptional("environment", "dev"));
    }

    public static String runKind() {
        String kind = RuntimeConfig.getOptional(RUN_KIND, "mixed").trim().toLowerCase();
        return switch (kind) {
            case "manual", "mixed", "automated" -> kind;
            default -> {
                System.out.println("[testomatio] unknown " + RUN_KIND + "=" + kind + ", falling back to mixed");
                yield "mixed";
            }
        };
    }

    /**
     * @param defaultSuiteId suite used when the option is not set (normally
     *                       {@link TestomatioMapping#rootSuite()})
     * @return suite ids to pre-fill the Run with; empty list means "do not pre-fill"
     */
    public static List<String> runSuiteIds(String defaultSuiteId) {
        String raw = RuntimeConfig.getOptional(RUN_SUITES, defaultSuiteId == null ? "" : defaultSuiteId);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String value = raw.trim();
        if (value.equalsIgnoreCase("none") || value.equalsIgnoreCase("off") || value.equals("-")) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();
    }

    public static boolean finishRun() {
        return RuntimeConfig.getBoolean(FINISH_RUN, false);
    }

    public static String runFile() {
        return RuntimeConfig.getOptional(RUN_FILE);
    }

    public static long runFileTtlMinutes() {
        String raw = RuntimeConfig.getOptional(RUN_FILE_TTL, "240");
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return 240L;
        }
    }

    public static String baseUrl() {
        String url = RuntimeConfig.getOptional(BASE_URL, "https://app.testomat.io");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
