package api;

import Utils.AuthTokenUtils;
import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.util.Objects;

public final class ApiConfig {
    public final String baseUrl;
    public final String bearerToken;
    public final String apiKey;
    public final Duration callTimeout;

    private ApiConfig(String baseUrl, String bearerToken, String apiKey, Duration callTimeout) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.bearerToken = bearerToken;
        this.apiKey = apiKey;
        this.callTimeout = callTimeout != null ? callTimeout : Duration.ofSeconds(30);
    }

    public static Builder builder() { return new Builder(); }

    // -------------------------------------------------------------------------
    // ENV-BASED CONFIG (used for non-browser tests or CI)
    // -------------------------------------------------------------------------
    public static ApiConfig fromEnv() {
        String base = System.getProperty(
                "API_BASE_URL",
                System.getenv().getOrDefault("API_BASE_URL", "https://tilt-api-dev.tilt365.com")
        );

        String bearer = System.getProperty("API_BEARER", System.getenv("API_BEARER"));
        String apiKey = System.getProperty("API_KEY", System.getenv("API_KEY"));
        int timeout = Integer.getInteger("API_TIMEOUT_SEC", 30);

        return builder()
                .baseUrl(base)
                .bearerToken(bearer)
                .apiKey(apiKey)
                .callTimeoutSeconds(timeout)
                .build();
    }

    // -------------------------------------------------------------------------
    // BROWSER-BASED CONFIG (extracts JWT dynamically from Selenium session)
    // -------------------------------------------------------------------------
    public static ApiConfig fromBrowser(WebDriver driver) {
        String base = System.getProperty(
                "API_BASE_URL",
                System.getenv().getOrDefault("API_BASE_URL", "https://tilt-api-dev.tilt365.com")
        );

        // Extract JWT from localStorage/sessionStorage/cookies
        String jwt = AuthTokenUtils.extractJwt(driver);

        return builder()
                .baseUrl(base)
                .bearerToken(jwt)
                // API-key may not be needed for Tilt backend — leave null
                .callTimeoutSeconds(30)
                .build();
    }

    // -------------------------------------------------------------------------
    // BUILDER
    // -------------------------------------------------------------------------
    public static final class Builder {
        private String baseUrl;
        private String bearerToken;
        private String apiKey;
        private Duration callTimeout;

        public Builder baseUrl(String v) { this.baseUrl = v; return this; }
        public Builder bearerToken(String v) { this.bearerToken = v; return this; }
        public Builder apiKey(String v) { this.apiKey = v; return this; }
        public Builder callTimeout(Duration v) { this.callTimeout = v; return this; }
        public Builder callTimeoutSeconds(int secs) { this.callTimeout = Duration.ofSeconds(secs); return this; }

        public ApiConfig build() {
            return new ApiConfig(baseUrl, bearerToken, apiKey, callTimeout);
        }
    }
}
