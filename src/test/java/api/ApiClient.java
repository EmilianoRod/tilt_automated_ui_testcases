package api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.Duration;

public final class ApiClient {
    private final Retrofit retrofit;

    public ApiClient(ApiConfig cfg) {
        ObjectMapper om = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        OkHttpClient.Builder ok = new OkHttpClient.Builder()
            .callTimeout(Duration.ofMillis(cfg.callTimeout.toMillis()));

        ok.addInterceptor(new Interceptor() {
            @Override public Response intercept(Chain chain) throws IOException {
                Request original = chain.request();
                Request.Builder b = original.newBuilder();
                if (cfg.bearerToken != null && !cfg.bearerToken.isBlank()) {
                    b.header("Authorization", "Bearer " + cfg.bearerToken);
                }
                if (cfg.apiKey != null && !cfg.apiKey.isBlank()) {
                    b.header("X-API-Key", cfg.apiKey);
                }
                return chain.proceed(b.build());
            }
        });

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        String lvl = System.getProperty("API_HTTP_LOG", System.getenv().getOrDefault("API_HTTP_LOG", "BASIC"));
        logging.setLevel(HttpLoggingInterceptor.Level.valueOf(lvl));
        ok.addInterceptor(logging);

        retrofit = new Retrofit.Builder()
            .baseUrl(cfg.baseUrl.endsWith("/") ? cfg.baseUrl : cfg.baseUrl + "/")
            .addConverterFactory(JacksonConverterFactory.create(om))
            .client(ok.build())
            .build();
    }

    public <T> T create(Class<T> api) { return retrofit.create(api); }
}
