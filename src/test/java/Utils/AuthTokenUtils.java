package Utils;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.Cookie;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuthTokenUtils {

    private AuthTokenUtils() {}

    public static String extractJwt(WebDriver driver) {
        if (!(driver instanceof JavascriptExecutor)) {
            throw new IllegalStateException("Driver does not support JS execution");
        }
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // 1) Log localStorage/sessionStorage keys for debugging
        @SuppressWarnings("unchecked")
        List<String> localKeys = (List<String>) js.executeScript(
                "if (!window.localStorage) return []; return Object.keys(window.localStorage);"
        );
        @SuppressWarnings("unchecked")
        List<String> sessKeys = (List<String>) js.executeScript(
                "if (!window.sessionStorage) return []; return Object.keys(window.sessionStorage);"
        );

        System.out.println("[AuthTokenUtils] localStorage keys: " + localKeys);
        System.out.println("[AuthTokenUtils] sessionStorage keys: " + sessKeys);

        // 2) Try direct known key: "jwt" in localStorage
        String raw = (String) js.executeScript(
                "if (!window.localStorage) return null; return window.localStorage.getItem('jwt');"
        );
        if (raw != null && !raw.isBlank()) {
            String token = tryExtractToken(raw);
            if (token != null) {
                System.out.println("[AuthTokenUtils] Found JWT from localStorage['jwt']");
                return token;
            }
        }

        // 3) Fallback: scan all localStorage values for something that looks like a JWT
        String scannedLocal = (String) js.executeScript(
                "if (!window.localStorage) return null;" +
                        "var ls = window.localStorage;" +
                        "var keys = Object.keys(ls);" +
                        "for (var i = 0; i < keys.length; i++) {" +
                        "  var k = keys[i];" +
                        "  var v = ls.getItem(k);" +
                        "  if (!v) continue;" +
                        "  if (k.toLowerCase().indexOf('jwt') !== -1 || k.toLowerCase().indexOf('token') !== -1) {" +
                        "    return v;" +
                        "  }" +
                        "}" +
                        "return null;"
        );
        if (scannedLocal != null && !scannedLocal.isBlank()) {
            String token = tryExtractToken(scannedLocal);
            if (token != null) {
                System.out.println("[AuthTokenUtils] Found JWT from scanned localStorage value");
                return token;
            }
        }

        // 4) Fallback: scan sessionStorage similarly (just in case)
        String scannedSession = (String) js.executeScript(
                "if (!window.sessionStorage) return null;" +
                        "var ss = window.sessionStorage;" +
                        "var keys = Object.keys(ss);" +
                        "for (var i = 0; i < keys.length; i++) {" +
                        "  var k = keys[i];" +
                        "  var v = ss.getItem(k);" +
                        "  if (!v) continue;" +
                        "  if (k.toLowerCase().indexOf('jwt') !== -1 || k.toLowerCase().indexOf('token') !== -1) {" +
                        "    return v;" +
                        "  }" +
                        "}" +
                        "return null;"
        );
        if (scannedSession != null && !scannedSession.isBlank()) {
            String token = tryExtractToken(scannedSession);
            if (token != null) {
                System.out.println("[AuthTokenUtils] Found JWT from scanned sessionStorage value");
                return token;
            }
        }

        // 5) As a last resort, look at cookies named "jwt" or containing "token"
        List<Cookie> cookies = new ArrayList<>(driver.manage().getCookies());
        for (Cookie c : cookies) {
            String name = c.getName().toLowerCase();
            String val = c.getValue();
            if (val == null || val.isBlank()) continue;

            if (name.contains("jwt") || name.contains("token")) {
                String token = tryExtractToken(val);
                if (token != null) {
                    System.out.println("[AuthTokenUtils] Found JWT from cookie: " + c.getName());
                    return token;
                }
            }
        }

        throw new IllegalStateException("Could not find JWT in localStorage/sessionStorage/cookies");
    }

    /**
     * Attempts to extract a JWT from a raw string which could be:
     *  - directly the token: "eyJhbGciOiJIUzI1NiJ9..."
     *  - JSON: {"access":"eyJhbGciOiJIUzI1NiJ9...","refresh":"..."}
     *  - JSON: {"token":"eyJhbGciOiJIUzI1NiJ9..."}
     */
    private static String tryExtractToken(String raw) {
        if (raw == null) return null;
        raw = raw.trim();

        // Case 1: already looks like a JWT (three dot-separated parts)
        if (!raw.contains("{") && raw.split("\\.").length == 3) {
            return raw;
        }

        // Case 2: JSON-like content - use regex to pull "access"/"token"/"jwt" fields
        Pattern p = Pattern.compile("\"(access|token|jwt)\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(raw);
        while (m.find()) {
            String candidate = m.group(2);
            if (candidate.split("\\.").length == 3) {
                return candidate;
            }
        }

        // Give up
        return null;
    }
}
