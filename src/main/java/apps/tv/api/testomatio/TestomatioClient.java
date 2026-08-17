package apps.tv.api.testomatio;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Thin HTTP client over the Testomat.io Reporter API.
 *
 * <pre>
 * POST /api/reporter                  → create Run,      returns {uid, url}
 * POST /api/reporter/{uid}/testrun    → report one test result
 * PUT  /api/reporter/{uid}            → finish Run  (the automated half of a mixed Run)
 * </pre>
 *
 * Plus two calls of the <b>v2 API</b>, needed only to create a Run that already contains suites
 * (the Reporter API cannot do that):
 *
 * <pre>
 * POST   /api/v2/{project}/sessions        → open a write session, returns {data: {hash}}
 * POST   /api/v2/{project}/runs            → create Run with suite_ids  (needs X-Session-Hash)
 * DELETE /api/v2/{project}/sessions/{hash} → close the session
 * </pre>
 *
 * Both APIs authenticate with the same project key ({@code tstmt_…}): the Reporter API takes it as the
 * {@code api_key} query parameter, the v2 API as an {@code Authorization: Bearer} header.
 *
 * The client never throws on network/API problems: reporting must never break a test run.
 */
public class TestomatioClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final String baseUrl;
    private final String apiKey;

    public TestomatioClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    /**
     * Creates a new Run.
     *
     * @param kind {@code mixed} | {@code manual} | {@code automated}; {@code mixed} keeps the Run
     *             editable by hand in Testomat.io (see {@link TestomatioConfig#RUN_KIND})
     * @return created Run as {@code {uid, url}} or {@code null} when the request failed
     */
    public JSONObject createRun(String title, String env, String groupTitle, String kind) {
        JSONObject body = new JSONObject();
        if (title != null) {
            body.put("title", title);
        }
        if (env != null) {
            body.put("env", env);
        }
        if (groupTitle != null) {
            body.put("group_title", groupTitle);
        }
        if (kind != null && !kind.isBlank()) {
            body.put("kind", kind);
        }
        // keep the run open for parallel device suites reporting into it
        body.put("parallel", true);

        String response = post("/api/reporter", body.toString());
        if (response == null) {
            return null;
        }
        try {
            return new JSONObject(response);
        } catch (RuntimeException e) {
            log("unexpected createRun response: " + response);
            return null;
        }
    }

    /**
     * Creates a Run that already contains the given suites — used so that the manual checks stored in
     * Testomat.io show up in the Run next to the automated results.
     *
     * <p>Goes through the v2 API, because {@code POST /api/reporter} accepts no suites. The Run is a
     * normal Run afterwards: the reporter fills the automated results in by its uid, exactly like for a
     * Run created by hand in the UI.
     *
     * @param suiteIds suite ids to put into the Run (usually the single automation root suite)
     * @return {@code {uid, url, tests_count}} or {@code null} when the v2 API refused — the caller then
     *         falls back to {@link #createRun(String, String, String, String)}
     */
    public JSONObject createRunWithSuites(String projectId,
                                          String title,
                                          String env,
                                          String kind,
                                          List<String> suiteIds) {
        if (projectId == null || projectId.isBlank()) {
            log("no project id in the mapping — cannot pre-fill the run with suites");
            return null;
        }
        if (suiteIds == null || suiteIds.isEmpty()) {
            return null;
        }
        String session = openSession(projectId);
        if (session == null) {
            return null;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("title", title == null || title.isBlank() ? "Android regression" : title);
            if (env != null && !env.isBlank()) {
                body.put("env", env);
            }
            if (kind != null && !kind.isBlank()) {
                body.put("kind", kind);
            }
            body.put("suite_ids", new JSONArray(suiteIds));

            String response = sendV2("POST", "/api/v2/" + projectId + "/runs", body.toString(), session);
            if (response == null) {
                return null;
            }
            JSONObject data = new JSONObject(response).optJSONObject("data");
            String uid = data == null ? null : data.optString("id", null);
            if (uid == null || uid.isBlank()) {
                log("v2 run created without an id: " + truncate(response, 300));
                return null;
            }
            return new JSONObject()
                    .put("uid", uid)
                    .put("url", data.optString("url", runUrl(projectId, uid)))
                    .put("tests_count", data.optInt("tests_count", 0));
        } catch (RuntimeException e) {
            log("unexpected v2 createRun response: " + e);
            return null;
        } finally {
            closeSession(projectId, session);
        }
    }

    /** Opens a v2 API write session; its hash has to accompany every mutating v2 request. */
    private String openSession(String projectId) {
        String response = sendV2("POST", "/api/v2/" + projectId + "/sessions",
                new JSONObject().put("description", "android-automation-test").toString(), null);
        if (response == null) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(response);
            JSONObject data = json.optJSONObject("data");
            String hash = data == null ? null : data.optString("hash", null);
            if (hash == null || hash.isBlank()) {
                hash = json.optString("hash", null);
            }
            if (hash == null || hash.isBlank()) {
                log("v2 session without a hash: " + truncate(response, 300));
                return null;
            }
            return hash;
        } catch (RuntimeException e) {
            log("unexpected v2 session response: " + truncate(response, 300));
            return null;
        }
    }

    private void closeSession(String projectId, String sessionHash) {
        sendV2("DELETE", "/api/v2/" + projectId + "/sessions/" + sessionHash, null, sessionHash);
    }

    /**
     * Reports a single test result into the Run.
     *
     * @param runUid    Run uid
     * @param testId    Testomat.io test id without prefix (e.g. {@code b44678f9}); may be null
     * @param title     test title (used by Testomat.io when {@code testId} is null)
     * @param suite     suite (file) title
     * @param status    {@code passed} | {@code failed} | {@code skipped}
     * @param runTimeMs duration in milliseconds
     * @param message   short error message, may be null
     * @param stack     stack trace / details, may be null
     * @param example   data-provider parameters as JSON, may be null
     */
    public boolean reportTest(String runUid,
                              String testId,
                              String title,
                              String suite,
                              String status,
                              long runTimeMs,
                              String message,
                              String stack,
                              JSONObject example) {
        JSONObject body = new JSONObject();
        body.put("status", status);
        body.put("title", title);
        if (suite != null) {
            body.put("suite_title", suite);
        }
        if (testId != null && !testId.isBlank()) {
            body.put("test_id", testId.startsWith("@T") ? testId : "@T" + testId);
        }
        body.put("run_time", runTimeMs / 1000.0d);
        if (message != null && !message.isBlank()) {
            body.put("message", truncate(message, 2000));
        }
        if (stack != null && !stack.isBlank()) {
            body.put("stack", truncate(stack, 20000));
        }
        if (example != null && !example.isEmpty()) {
            body.put("example", example);
        }
        // never let a reporter create new tests in the project: the tree is managed explicitly
        body.put("create", false);

        return post("/api/reporter/" + runUid + "/testrun", body.toString()) != null;
    }

    /**
     * Finishes the Run. Called only when {@code -DtestomatioFinishRun=true}.
     */
    public boolean finishRun(String runUid, long durationMs) {
        JSONObject body = new JSONObject()
                .put("status_event", "finish")
                .put("duration", durationMs / 1000.0d);
        return put("/api/reporter/" + runUid, body.toString()) != null;
    }

    public String runUrl(String projectId, String runUid) {
        return baseUrl + "/projects/" + projectId + "/runs/" + runUid;
    }

    private String post(String path, String json) {
        return send("POST", path, json);
    }

    private String put(String path, String json) {
        return send("PUT", path, json);
    }

    private String send(String method, String path, String json) {
        if (apiKey == null || apiKey.isBlank()) {
            log("no API key (-D" + TestomatioConfig.API_KEY + "), request skipped: " + method + " " + path);
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path + "?api_key=" + apiKey))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            log(method + " " + path + " → HTTP " + response.statusCode() + " " + truncate(response.body(), 500));
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log("interrupted on " + method + " " + path);
            return null;
        } catch (Exception e) {
            log(method + " " + path + " failed: " + e);
            return null;
        }
    }

    /** Same transport as {@link #send(String, String, String)}, but with the v2 API authentication. */
    private String sendV2(String method, String path, String json, String sessionHash) {
        if (apiKey == null || apiKey.isBlank()) {
            log("no API key (-D" + TestomatioConfig.API_KEY + "), request skipped: " + method + " " + path);
            return null;
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json");
            if (sessionHash != null && !sessionHash.isBlank()) {
                builder.header("X-Session-Hash", sessionHash);
            }
            builder.method(method, json == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));

            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            log(method + " " + path + " → HTTP " + response.statusCode() + " " + truncate(response.body(), 500));
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log("interrupted on " + method + " " + path);
            return null;
        } catch (Exception e) {
            log(method + " " + path + " failed: " + e);
            return null;
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private static void log(String message) {
        System.out.println("[testomatio] " + message);
    }
}
