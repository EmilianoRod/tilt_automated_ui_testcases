package probes;

import io.qameta.allure.Allure;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.Command;
import org.openqa.selenium.devtools.v138.network.Network;              // ← change vXXX if needed
import org.openqa.selenium.devtools.v138.network.model.RequestId;     // ← change vXXX if needed
import org.openqa.selenium.devtools.v138.network.model.Response;      // ← change vXXX if needed

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class NetworkCapture implements AutoCloseable {
    private final DevTools dt;
    private final Predicate<String> urlFilter;
    private final Map<RequestId, Response> responses = new ConcurrentHashMap<>();
    private final Map<RequestId, String> bodies     = new ConcurrentHashMap<>();
    private volatile boolean enabled = false;

    private NetworkCapture(DevTools dt, Predicate<String> urlFilter) {
        this.dt = dt;
        this.urlFilter = urlFilter;
    }

    /** Start CDP capture for filtered URLs; no-op if driver doesn’t support DevTools. */
    public static Optional<NetworkCapture> start(WebDriver driver, Predicate<String> urlFilter) {
        if (!(driver instanceof HasDevTools)) return Optional.empty();

        DevTools dt = ((HasDevTools) driver).getDevTools();
        dt.createSession();

        // Version-agnostic enable (4-arg first, then 3-arg)
        dt.send(enableNetworkCmd());

        NetworkCapture cap = new NetworkCapture(dt, urlFilter);
        cap.enabled = true;

        dt.addListener(Network.responseReceived(), evt -> {
            Response r = evt.getResponse();
            if (!cap.urlFilter.test(r.getUrl())) return;

            cap.responses.put(evt.getRequestId(), r);

            try {
                var bodyResp = dt.send(Network.getResponseBody(evt.getRequestId()));
                String body = bodyResp.getBase64Encoded()
                        ? new String(Base64.getDecoder().decode(bodyResp.getBody()), StandardCharsets.UTF_8)
                        : bodyResp.getBody();

                cap.bodies.put(evt.getRequestId(), body);

                String meta = "[API] " + r.getStatus() + " " + r.getUrl();
                System.out.println(meta);
                Allure.addAttachment("network: " + meta, "application/json", body, ".json");
            } catch (Exception ignored) { /* body may not be retrievable for some resources */ }
        });

        return Optional.of(cap);
    }

    /** Wait until any filtered response arrives within timeout. */
    public boolean waitForAny(Duration timeout) {
        Instant end = Instant.now().plus(timeout);
        while (Instant.now().isBefore(end)) {
            if (!responses.isEmpty()) return true;
            sleep(120);
        }
        return !responses.isEmpty();
    }

    /** True if any captured body (case-insensitive) contains the needle. */
    public boolean anyBodyContainsIgnoreCase(String needle) {
        String n = needle.toLowerCase();
        return bodies.values().stream().anyMatch(b -> b != null && b.toLowerCase().contains(n));
    }

    @Override public void close() {
        if (!enabled) return;
        try { dt.send(Network.disable()); } catch (Exception ignore) {}
        enabled = false;
    }

    // ---------- internals ----------
    private static Command<?> enableNetworkCmd() {
        try {
            // Newer DevTools builds: 4 args
            var m = Network.class.getMethod("enable",
                    Optional.class, Optional.class, Optional.class, Optional.class);
            return (Command<?>) m.invoke(null,
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        } catch (NoSuchMethodException e) {
            try {
                // Older DevTools builds: 3 args
                var m3 = Network.class.getMethod("enable",
                        Optional.class, Optional.class, Optional.class);
                return (Command<?>) m3.invoke(null,
                        Optional.empty(), Optional.empty(), Optional.empty());
            } catch (Exception inner) {
                throw new RuntimeException("Cannot resolve Network.enable(…) for this DevTools version", inner);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to enable Network domain", e);
        }
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); } }
}
