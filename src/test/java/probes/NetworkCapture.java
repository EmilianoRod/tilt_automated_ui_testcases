package probes;

import io.qameta.allure.Allure;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.Command;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v142.network.Network;              // ← DevTools v142
import org.openqa.selenium.devtools.v142.network.model.RequestId;
import org.openqa.selenium.devtools.v142.network.model.Response;

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

    /**
     * Start CDP capture for filtered URLs; no-op (empty Optional) if driver doesn’t support DevTools.
     */
    public static Optional<NetworkCapture> start(WebDriver driver, Predicate<String> urlFilter) {
        if (!(driver instanceof HasDevTools)) {
            return Optional.empty();
        }

        DevTools dt = ((HasDevTools) driver).getDevTools();
        dt.createSession();

        // Version-agnostic enable: resolve a suitable Network.enable(…) at runtime
        dt.send(enableNetworkCmd());

        NetworkCapture cap = new NetworkCapture(dt, urlFilter);
        cap.enabled = true;

        dt.addListener(Network.responseReceived(), evt -> {
            Response r = evt.getResponse();
            if (!cap.urlFilter.test(r.getUrl())) {
                return;
            }

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
            } catch (Exception ignored) {
                // Some resources may not have retrievable bodies (e.g. images, redirects, etc.).
            }
        });

        return Optional.of(cap);
    }

    /**
     * Wait until any filtered response arrives within the given timeout.
     */
    public boolean waitForAny(Duration timeout) {
        Instant end = Instant.now().plus(timeout);
        while (Instant.now().isBefore(end)) {
            if (!responses.isEmpty()) {
                return true;
            }
            sleep(120);
        }
        return !responses.isEmpty();
    }

    /**
     * True if any captured body (case-insensitive) contains the given text.
     */
    public boolean anyBodyContainsIgnoreCase(String needle) {
        String n = needle.toLowerCase();
        return bodies.values().stream().anyMatch(b -> b != null && b.toLowerCase().contains(n));
    }

    @Override
    public void close() {
        if (!enabled) {
            return;
        }
        try {
            dt.send(Network.disable());
        } catch (Exception ignore) {
        }
        enabled = false;
    }

    // ---------- internals ----------

    /**
     * Tries to resolve a suitable Network.enable(…) method for the current DevTools version.
     * We don't assume a specific signature – instead we:
     *  - find any static method named "enable" that returns Command<?>
     *  - build default arguments (Optional.empty(), false, null, etc.) based on parameter types
     */
    private static Command<?> enableNetworkCmd() {
        try {
            for (var m : Network.class.getMethods()) {
                if (!m.getName().equals("enable")) {
                    continue;
                }
                if (!Command.class.isAssignableFrom(m.getReturnType())) {
                    continue;
                }

                Class<?>[] paramTypes = m.getParameterTypes();
                Object[] args = new Object[paramTypes.length];

                for (int i = 0; i < paramTypes.length; i++) {
                    Class<?> p = paramTypes[i];

                    if (Optional.class.isAssignableFrom(p)) {
                        // Most devtools enable() params are Optional<Something>; empty is a safe default
                        args[i] = Optional.empty();
                    } else if (p == boolean.class || p == Boolean.class) {
                        args[i] = Boolean.FALSE;
                    } else if (Number.class.isAssignableFrom(p) || p.isPrimitive()
                            && (p == int.class || p == long.class || p == double.class)) {
                        // Default numeric params to zero
                        args[i] = 0;
                    } else {
                        // Fallback: null for anything else
                        args[i] = null;
                    }
                }

                return (Command<?>) m.invoke(null, args);
            }

            throw new RuntimeException("No suitable Network.enable method found in DevTools Network class");
        } catch (Exception e) {
            throw new RuntimeException("Cannot resolve Network.enable for this DevTools version", e);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
