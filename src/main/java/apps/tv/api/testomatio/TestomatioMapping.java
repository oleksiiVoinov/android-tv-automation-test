package apps.tv.api.testomatio;

import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps a TestNG method to a test in Testomat.io.
 *
 * <p>The mapping lives in {@code src/test/resources/testomatio-mapping.json} and is generated from the
 * Allure annotations of the regression suites. Key format is
 * {@code <fully.qualified.ClassName>#<methodName>/<parameterCount>} — the parameter count keeps
 * overloaded test methods (e.g. {@code ProtocolsTest#testFreeServerWithProtocols}) apart.
 *
 * <p>Nothing here is required for a test to run: an unmapped test is simply reported by title.
 */
public final class TestomatioMapping {

    private static final String RESOURCE = "/testomatio-mapping.json";

    private static Map<String, Entry> entries;
    /** {@code Class#method/1} → data-provider value → test. Used for per-parameter cases (protocols, Android versions). */
    private static Map<String, Map<String, Entry>> parametrized;
    private static String projectId;
    /** Suite that holds the whole automation tree (and the manual checks nested in it). */
    private static String rootSuite;

    private TestomatioMapping() {
    }

    public record Entry(String id, String title, String suiteId, String suiteTitle, String feature) {
    }

    public static synchronized void load() {
        if (entries != null) {
            return;
        }
        Map<String, Entry> parsed = new HashMap<>();
        Map<String, Map<String, Entry>> parsedParams = new HashMap<>();
        try (InputStream in = TestomatioMapping.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                System.out.println("[testomatio] " + RESOURCE + " not found on classpath — tests will be reported by title");
                entries = Collections.emptyMap();
                parametrized = Collections.emptyMap();
                return;
            }
            JSONObject root = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            projectId = root.optString("project", null);
            rootSuite = root.optString("root_suite", null);
            JSONObject tests = root.getJSONObject("tests");
            for (String key : tests.keySet()) {
                JSONObject value = tests.getJSONObject(key);
                parsed.put(key, new Entry(
                        value.optString("id", null),
                        value.optString("title", null),
                        value.optString("suite_id", null),
                        value.optString("suite_title", null),
                        value.optString("feature", null)));
            }
            JSONObject params = root.optJSONObject("parametrized");
            if (params != null) {
                for (String key : params.keySet()) {
                    JSONObject byValue = params.getJSONObject(key);
                    Map<String, Entry> values = new HashMap<>();
                    for (String value : byValue.keySet()) {
                        values.put(value, toEntry(byValue.getJSONObject(value)));
                    }
                    parsedParams.put(key, values);
                }
            }
            entries = parsed;
            parametrized = parsedParams;
            System.out.println("[testomatio] mapping loaded: " + entries.size() + " tests + "
                    + parametrized.values().stream().mapToInt(Map::size).sum()
                    + " parametrized cases, project " + projectId);
        } catch (Exception e) {
            System.out.println("[testomatio] failed to read " + RESOURCE + ": " + e);
            entries = Collections.emptyMap();
            parametrized = Collections.emptyMap();
        }
    }

    private static Entry toEntry(JSONObject value) {
        return new Entry(
                value.optString("id", null),
                value.optString("title", null),
                value.optString("suite_id", null),
                value.optString("suite_title", null),
                value.optString("feature", null));
    }

    public static String projectId() {
        load();
        return projectId;
    }

    /**
     * @return id of the automation root suite ({@code root_suite} in the mapping). A Run created with
     *         this suite id already contains every automated case <b>and</b> the manual checks that
     *         live in the {@code Manual} folder inside it.
     */
    public static String rootSuite() {
        load();
        return rootSuite;
    }

    /**
     * @param className      fully qualified test class name
     * @param methodName     test method name
     * @param parameterCount number of method parameters (0 for non data-driven tests)
     * @param parameterValue first data-provider value as string (e.g. {@code V2Ray}, {@code 15}); may be null
     * @return mapping entry or {@code null} when the test is not in the mapping
     */
    public static Entry find(String className, String methodName, int parameterCount, String parameterValue) {
        load();
        // data-driven tests split into one case per value (protocols, Android versions)
        if (parameterValue != null) {
            Map<String, Entry> byValue = parametrized.get(className + "#" + methodName + "/" + parameterCount);
            if (byValue != null) {
                Entry byExactValue = byValue.get(parameterValue);
                if (byExactValue != null) {
                    return byExactValue;
                }
                for (Map.Entry<String, Entry> candidate : byValue.entrySet()) {
                    if (candidate.getKey().equalsIgnoreCase(parameterValue)) {
                        return candidate.getValue();
                    }
                }
                System.out.println("[testomatio] no case for " + className + "#" + methodName
                        + " with value \"" + parameterValue + "\" — known values: " + byValue.keySet());
                return null;
            }
        }
        return find(className, methodName, parameterCount);
    }

    public static Entry find(String className, String methodName, int parameterCount) {
        load();
        Entry exact = entries.get(className + "#" + methodName + "/" + parameterCount);
        if (exact != null) {
            return exact;
        }
        // tolerate signature changes: fall back to a unique match by class#method
        Entry single = null;
        String prefix = className + "#" + methodName + "/";
        for (Map.Entry<String, Entry> candidate : entries.entrySet()) {
            if (candidate.getKey().startsWith(prefix)) {
                if (single != null) {
                    return null;
                }
                single = candidate.getValue();
            }
        }
        return single;
    }

    public static int size() {
        load();
        return entries.size();
    }
}
