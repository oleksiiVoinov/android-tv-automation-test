package apps.tv.api.testomatio;

import org.json.JSONObject;

/**
 * Small CLI to manage a Testomat.io Run outside of a test suite — meant for Jenkins pipelines
 * where two device suites must report into one Run.
 *
 * <pre>
 * # create a run and print its uid (use it as -DtestomatioRunId for both device suites)
 * ./gradlew testomatioCreateRun -DtestomatioApiKey=tstmt_xxx -DtestomatioRunTitle="Release 4.2.0 regression"
 *
 * # close a run (normally done by a human in the UI)
 * ./gradlew testomatioFinishRun -DtestomatioApiKey=tstmt_xxx -DtestomatioRunId=abc123
 * </pre>
 *
 * Prints the uid as the last line prefixed with {@code TESTOMATIO_RUN_ID=} so CI can grep it.
 */
public final class TestomatioRunCli {

    private TestomatioRunCli() {
    }

    public static void main(String[] args) {
        String command = args.length > 0 ? args[0] : "create";
        String apiKey = TestomatioConfig.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("[testomatio] -D" + TestomatioConfig.API_KEY + " is required");
            System.exit(1);
        }
        TestomatioClient client = new TestomatioClient(TestomatioConfig.baseUrl(), apiKey);

        switch (command) {
            case "create" -> {
                String title = TestomatioConfig.runTitle();
                JSONObject run = TestomatioReporter.createRun(client,
                        title == null || title.isBlank() ? "Android regression" : title);
                if (run == null || run.optString("uid", "").isBlank()) {
                    System.out.println("[testomatio] run was not created");
                    System.exit(1);
                }
                String uid = run.getString("uid");
                System.out.println("[testomatio] run url: "
                        + run.optString("url", client.runUrl(TestomatioMapping.projectId(), uid)));
                System.out.println("[testomatio] run kind: " + TestomatioConfig.runKind()
                        + " (manual/mixed runs can be re-checked by hand: open the run → Continue)");
                System.out.println("TESTOMATIO_RUN_ID=" + uid);
            }
            case "finish" -> {
                String uid = TestomatioConfig.runId();
                if (uid == null || uid.isBlank()) {
                    System.out.println("[testomatio] -D" + TestomatioConfig.RUN_ID + " is required to finish a run");
                    System.exit(1);
                }
                boolean ok = client.finishRun(uid, 0L);
                System.out.println("[testomatio] finish " + uid + ": " + (ok ? "ok" : "failed"));
                if (!ok) {
                    System.exit(1);
                }
            }
            default -> {
                System.out.println("[testomatio] unknown command: " + command + " (expected: create | finish)");
                System.exit(1);
            }
        }
    }
}
