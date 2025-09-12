package Utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StripeCheckoutHelper {

    // ===== Configuration =====
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration CLI_TIMEOUT  = Duration.ofSeconds(30);
    // Allow overriding the stripe binary path via env (e.g., /usr/local/bin/stripe)
    private static final String  STRIPE_BIN    =
            System.getenv().getOrDefault("STRIPE_BIN", "stripe");

    /** Small result object with extra feedback from the CLI run. */
    public static final class TriggerResult {
        public final boolean ok;
        public final String eventId;        // evt_...
        public final String requestLogUrl;  // https://dashboard.stripe.com/test/logs/req_...
        public final String stdout;         // full aggregated output
        TriggerResult(boolean ok, String eventId, String requestLogUrl, String stdout) {
            this.ok = ok; this.eventId = eventId; this.requestLogUrl = requestLogUrl; this.stdout = stdout;
        }
    }

    /** Returns a valid test-mode API key (rk_test_ or sk_test_). */
    private static String resolveStripeKey() {
        String k = Config.getStripeSecretKey(); // supports ENV and UPPERCASE properties
        if (k == null || k.isBlank()) {
            throw new IllegalStateException(
                    "Missing Stripe test key. Set STRIPE_TEST_SECRET_KEY or STRIPE_SECRET_KEY (config.local.properties or ENV)."
            );
        }
        if (!(k.startsWith("rk_test_") || k.startsWith("sk_test_"))) {
            String head = k.substring(0, Math.min(10, k.length()));
            throw new IllegalStateException("Stripe key must be rk_test_ or sk_test_. Got: " + head + "…");
        }
        return k;
    }

    // ===== 1) Retrieve metadata.body from Checkout Session (expand payment_intent) =====
    public static String fetchCheckoutBodyFromStripe(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        String key = resolveStripeKey();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(HTTP_TIMEOUT)
                    .build();

            String url = "https://api.stripe.com/v1/checkout/sessions/" + sessionId + "?expand[]=payment_intent";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(HTTP_TIMEOUT)
                    .header("Authorization", "Bearer " + key)
                    .GET()
                    .build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new RuntimeException("Stripe GET session failed: " + res.statusCode() + " " + res.body());
            }

            JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();

            // Prefer session.metadata.body
            if (json.has("metadata")) {
                JsonObject md = json.getAsJsonObject("metadata");
                if (md != null && md.has("body") && md.get("body").isJsonPrimitive()) {
                    return md.get("body").getAsString();
                }
            }

            // Fallback: payment_intent.metadata.body
            if (json.has("payment_intent")) {
                JsonElement piEl = json.get("payment_intent");
                if (piEl != null && piEl.isJsonObject()) {
                    JsonObject pi = piEl.getAsJsonObject();
                    if (pi.has("metadata")) {
                        JsonObject md = pi.getAsJsonObject("metadata");
                        if (md != null && md.has("body") && md.get("body").isJsonPrimitive()) {
                            return md.get("body").getAsString();
                        }
                    }
                }
            }

            return null; // no body set
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while fetching Checkout Session from Stripe", ie);
        } catch (IOException ioe) {
            throw new RuntimeException("IO error fetching Checkout Session from Stripe", ioe);
        }
    }

    // ===== 2) Trigger checkout.session.completed with exact body JSON (metadata[body]) =====
    public static TriggerResult triggerCheckoutCompletedWithBody(String bodyJson) {
        if (bodyJson == null || bodyJson.isBlank()) {
            throw new IllegalArgumentException("bodyJson is empty");
        }
        String key = resolveStripeKey();

        // Compact to single-line JSON to avoid CLI parsing issues with newlines/whitespace
        String compact = JsonParser.parseString(bodyJson).toString();

        List<String> cmd = List.of(
                STRIPE_BIN, "trigger", "checkout.session.completed",
                "--log-level", "info", "--color", "off",
                "--api-key", key,
                "--override", "checkout_session:metadata[body]=" + compact
        );
        return runStripe(cmd, /*expectTrigger*/ true);
    }

    /** Simulate a successful Checkout and set checkout_session.metadata[order_id]. */
    public static TriggerResult simulateCheckoutSuccess(String orderId) {
        Objects.requireNonNull(orderId, "orderId");
        String key = resolveStripeKey();
        List<String> cmd = List.of(
                STRIPE_BIN, "trigger", "checkout.session.completed",
                "--log-level", "info", "--color", "off",
                "--api-key", key,
                "--override", "checkout_session:metadata[order_id]=" + orderId
        );
        return runStripe(cmd, /*expectTrigger*/ true);
    }

    /** Simulate a failed payment and set payment_intent.metadata[order_id]. */
    public static TriggerResult simulateCheckoutFailure(String orderId) {
        Objects.requireNonNull(orderId, "orderId");
        String key = resolveStripeKey();
        List<String> cmd = List.of(
                STRIPE_BIN, "trigger", "payment_intent.payment_failed",
                "--log-level", "info", "--color", "off",
                "--api-key", key,
                "--override", "payment_intent:metadata[order_id]=" + orderId
        );
        return runStripe(cmd, /*expectTrigger*/ true);
    }

    // ===== Process runner: streams output live + parses event id & request log URL =====
    private static TriggerResult runStripe(List<String> command, boolean expectTrigger) {
        Process p = null;
        try {
            // Log sanitized command (hide key)
            System.out.println("[StripeCLI] Running: " + String.join(" ", sanitize(command)));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            p = pb.start();

            StringBuilder all = new StringBuilder();
            String eventId = null;
            String reqUrl  = null;

            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    System.out.println("[StripeCLI] " + line); // live feedback
                    all.append(line).append('\n');

                    if (eventId == null) {
                        Matcher m = Pattern.compile("(evt_[A-Za-z0-9]+)").matcher(line);
                        if (m.find()) eventId = m.group(1);
                    }
                    if (reqUrl == null && line.contains("dashboard.stripe.com")) {
                        Matcher m = Pattern.compile("(https://dashboard\\.stripe\\.com/\\S+)").matcher(line);
                        if (m.find()) reqUrl = m.group(1);
                    }
                }
            }

            boolean finished = p.waitFor(CLI_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new RuntimeException("Stripe CLI timed out after " + CLI_TIMEOUT.getSeconds() + "s.");
            }

            int code = p.exitValue();
            String out = all.toString();
            boolean ok = code == 0 && (!expectTrigger || out.toLowerCase().contains("trigger succeeded"));
            if (!ok) {
                throw new RuntimeException("Stripe CLI failed (exit " + code + ")\n" + out);
            }

            if (eventId != null) System.out.println("[StripeCLI] eventId=" + eventId);
            if (reqUrl  != null) System.out.println("[StripeCLI] request log: " + reqUrl);

            return new TriggerResult(true, eventId, reqUrl, out);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for Stripe CLI", ie);
        } catch (IOException ioe) {
            throw new RuntimeException("Failed running Stripe CLI (" + String.join(" ", command) + "): " + ioe.getMessage(), ioe);
        } finally {
            if (p != null) p.destroy();
        }
    }

    private static List<String> sanitize(List<String> cmd) {
        // Hide the value following --api-key
        java.util.ArrayList<String> c = new java.util.ArrayList<>(cmd);
        for (int i = 0; i < c.size(); i++) {
            if ("--api-key".equals(c.get(i)) && i + 1 < c.size()) {
                String v = c.get(i + 1);
                String masked = (v.length() > 10) ? v.substring(0, 8) + "********" : "********";
                c.set(i + 1, masked);
            }
        }
        return c;
    }

    private static void pump(InputStream in, ByteArrayOutputStream out) throws IOException {
        byte[] b = new byte[8192];
        int r;
        while ((r = in.read(b)) != -1) out.write(b, 0, r);
    }
}
