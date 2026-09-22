package apps.tv.api;

import configs.RuntimeConfig;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Posts a message into Slack through an incoming webhook.
 *
 * <p>The webhook is resolved through the standard project chain ({@code -DslackWebhook=…} → gradle
 * property → env {@code SLACK_WEBHOOK_URL} → {@code local.properties}) and has no built-in default, so
 * a caller that does not pass one explicitly needs {@code slackWebhook=…} in the git-ignored
 * {@code local.properties}; without it nothing is posted and the reason is logged.
 *
 * <p>The Testomat.io run link passes {@link apps.tv.api.testomatio.TestomatioConfig#slackWebhook()},
 * which prefers {@code testomatioSlackWebhook} and falls back to {@code slackWebhook}. It has no
 * hardcoded default either — a webhook url is a secret, so the #android-qa hook lives only in
 * {@code local.properties}.
 *
 * <p>Twin of {@code apps.multiplatform.api.SlackNotifier} in the phone project — fix a bug in both.
 */
public class SlackNotifier {

    /** Config key for the incoming-webhook URL (the webhook decides the channel). */
    public static final String WEBHOOK = "slackWebhook";

    private final String webhook;

    public SlackNotifier() {
        this(RuntimeConfig.getOptional(WEBHOOK));
    }

    public SlackNotifier(String webhook) {
        this.webhook = webhook;
    }

    /**
     * @return {@code true} when Slack accepted the message. Never throws: a failed notification must
     *         not fail a test run.
     */
    public boolean sendToSlack(String message) {
        if (webhook == null || webhook.isBlank()) {
            System.out.println("[slack] no webhook configured — add " + WEBHOOK
                    + "=https://hooks.slack.com/services/... to local.properties (message not sent)");
            return false;
        }
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(webhook).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setDoOutput(true);

            String payload = "{\"text\": \"" + escape(message) + "\"}";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                System.out.println("[slack] notification failed, response code: " + responseCode);
                return false;
            }
            return true;
        } catch (Exception e) {
            System.out.println("[slack] notification failed: " + e);
            return false;
        }
    }

    /** JSON-escapes the text: a raw newline or backslash inside a JSON string is invalid. */
    private static String escape(String message) {
        return message
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
