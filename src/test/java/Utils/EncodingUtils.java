package Utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EncodingUtils {

    /** Base64URL decode with padding fix (for JWT payloads). */
    public static String decodeBase64Url(String base64Url) {
        try {
            String s = base64Url;
            int rem = s.length() % 4;
            if (rem == 2) s += "==";
            else if (rem == 3) s += "=";
            else if (rem != 0) return null;

            byte[] bytes = Base64.getUrlDecoder().decode(s);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
