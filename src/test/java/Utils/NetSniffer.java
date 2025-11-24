package Utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v142.network.Network;
import org.openqa.selenium.devtools.v142.network.model.Headers;
import org.openqa.selenium.devtools.v142.network.model.RequestId;



import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class NetSniffer {

    public static class JsonResponse {
        public final String url;
        public final String body;
        public final JsonObject json;

        JsonResponse(String url, String body, JsonObject json) {
            this.url = url;
            this.body = body;
            this.json = json;
        }
    }

    /**
     * Starts Network capture and returns the first JSON response whose URL matches urlMatcher.
     * Call BEFORE clicking the button that triggers the request.
     */
    public static JsonResponse waitForJsonResponse(org.openqa.selenium.WebDriver driver,
                                                   Predicate<String> urlMatcher,
                                                   Duration timeout) {
        DevTools devTools = ((HasDevTools) driver).getDevTools();
        devTools.createSession();
        devTools.send(
                Network.enable(
                        Optional.empty(),   // maxTotalBufferSize
                        Optional.empty(),   // maxResourceBufferSize
                        Optional.empty(),   // maxPostDataSize
                        Optional.of(true),  // captureNetworkRequests
                        Optional.of(true)   // reportRawHeaders
                )

        );

        CompletableFuture<JsonResponse> future = new CompletableFuture<>();

        // Attach listener for responseReceived
        devTools.addListener(Network.responseReceived(), rr -> {
            try {
                String url = rr.getResponse().getUrl();
                if (!urlMatcher.test(url)) return;

                Headers headers = rr.getResponse().getHeaders();
                String contentType = Optional.ofNullable(headers.toJson())
                        .map(j -> String.valueOf(j.getOrDefault("content-type", "")))
                        .orElse("");
                if (!contentType.toLowerCase().contains("application/json")) return;

                RequestId reqId = rr.getRequestId();
                // Fetch body
                Network.GetResponseBodyResponse bodyResp =
                        devTools.send(Network.getResponseBody(reqId));

                String text = bodyResp.getBody();
                if (Boolean.TRUE.equals(bodyResp.getBase64Encoded())) {
                    text = new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
                }
                JsonObject json = JsonParser.parseString(text).getAsJsonObject();

                if (!future.isDone()) {
                    future.complete(new JsonResponse(url, text, json));
                }
            } catch (Throwable ignored) {
            }
        });

        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Timed out waiting for matching JSON response", e);
        }
    }
}
