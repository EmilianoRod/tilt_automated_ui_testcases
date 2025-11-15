package pages.SignUp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Helper to create Tilt users directly against the API (no UI).
 *
 * It replicates the request you captured:
 *
 * POST {apiBaseUrl}/api/v2/users
 * Body:
 * {
 *   "user": {
 *     "email":      "...",
 *     "password":   "...",
 *     "first_name": "...",
 *     "last_name":  "..."
 *   }
 * }
 */
public class TiltSignUpApi {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static class ApiUserResult {
        public final int statusCode;
        public final String email;
        public final String password;
        public final String firstName;
        public final String lastName;
        public final String authToken;   // from response header "authorization" (if any)
        public final String rawBody;

        public ApiUserResult(int statusCode,
                             String email,
                             String password,
                             String firstName,
                             String lastName,
                             String authToken,
                             String rawBody) {
            this.statusCode = statusCode;
            this.email = email;
            this.password = password;
            this.firstName = firstName;
            this.lastName = lastName;
            this.authToken = authToken;
            this.rawBody = rawBody;
        }

        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }

        @Override
        public String toString() {
            return "ApiUserResult{" +
                    "statusCode=" + statusCode +
                    ", email='" + email + '\'' +
                    ", firstName='" + firstName + '\'' +
                    ", lastName='" + lastName + '\'' +
                    ", authToken='" + authToken + '\'' +
                    '}';
        }
    }

    /**
     * Creates a user using the same payload & headers as your curl.
     *
     * @param apiBaseUrl e.g. "https://tilt-api-dev.tilt365.com"
     */
    public static ApiUserResult createUser(String apiBaseUrl,
                                           String firstName,
                                           String lastName,
                                           String email,
                                           String password) {
        try {
            String endpoint = apiBaseUrl + "/api/v2/users";

            // EXACT body shape from your payload
            String jsonBody = """
                {
                  "user": {
                    "email":      "%s",
                    "password":   "%s",
                    "first_name": "%s",
                    "last_name":  "%s"
                  }
                }
                """.formatted(email, password, firstName, lastName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "*/*")
                    .header("Content-Type", "application/json")
                    // This is exactly what your browser sent. You can drop it if backend doesn't care.
                    .header("Authorization", "Bearer null")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response =
                    CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            String body = response.body();

            // backend sends JWT in "authorization" response header
            String token = response.headers()
                    .firstValue("authorization")
                    .orElse(null);

            System.out.println("[TiltSignUpApi] Status " + status +
                    " authHeader=" + token +
                    " bodyPreview=" + body.substring(0, Math.min(body.length(), 300)));

            return new ApiUserResult(
                    status,
                    email,
                    password,
                    firstName,
                    lastName,
                    token,
                    body
            );
        } catch (Exception e) {
            System.err.println("[TiltSignUpApi] Error creating user: " + e.getMessage());
            return new ApiUserResult(0, email, password, firstName, lastName, null, "");
        }
    }
}
