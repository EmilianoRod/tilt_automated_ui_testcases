package api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;



public final class ApiClient {
    private final Retrofit retrofit;

    public ApiClient(ApiConfig cfg) {
        ObjectMapper om = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        OkHttpClient.Builder ok = new OkHttpClient.Builder()
                .callTimeout(cfg.callTimeout);

        // ---------------------------------------------------------------------
        // AUTH INTERCEPTOR (Bearer + API Key)
        // ---------------------------------------------------------------------
        ok.addInterceptor(chain -> {
            Request original = chain.request();
            Request.Builder b = original.newBuilder();

            // Add Bearer Token if present
            if (cfg.bearerToken != null && !cfg.bearerToken.isBlank()) {
                b.header("Authorization", "Bearer " + cfg.bearerToken.trim());
            }

            // Add API key if present (unused for Tilt backend but good for future APIs)
            if (cfg.apiKey != null && !cfg.apiKey.isBlank()) {
                b.header("X-API-Key", cfg.apiKey.trim());
            }

            // Add Accept header for JSON APIs (important for some endpoints)
            b.header("Accept", "application/json");

            return chain.proceed(b.build());
        });

        // ---------------------------------------------------------------------
        // HTTP LOGGING
        // ---------------------------------------------------------------------
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        String lvl = System.getProperty(
                "API_HTTP_LOG",
                System.getenv().getOrDefault("API_HTTP_LOG", "BASIC")
        );
        try {
            logging.setLevel(HttpLoggingInterceptor.Level.valueOf(lvl));
        } catch (IllegalArgumentException ex) {
            // fallback seguro si alguien pone un valor raro en API_HTTP_LOG
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
        }
        ok.addInterceptor(logging);


        // ---------------------------------------------------------------------
        // BUILD RETROFIT
        // ---------------------------------------------------------------------
        retrofit = new Retrofit.Builder()
                .baseUrl(fixBaseUrl(cfg.baseUrl))
                .addConverterFactory(JacksonConverterFactory.create(om))
                .client(ok.build())
                .build();
    }

    public <T> T create(Class<T> api) {
        return retrofit.create(api);
    }

    // Ensures valid trailing slash required by Retrofit
    private static String fixBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("API base URL cannot be null or empty");
        }

        // If user mistakenly passes dashboard URL, convert to API URL
        if (url.contains("tilt-dashboard-")) {
            url = url.replace("tilt-dashboard-", "tilt-api-");
        }
        if (url.contains("dashboard.")) {
            url = url.replace("dashboard.", "api.");
        }

        return url.endsWith("/") ? url : url + "/";
    }
}
