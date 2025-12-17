package Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EmailLinkUtils {

    private EmailLinkUtils() {}

    public static List<String> extractAllLinks(String body) {
        if (body == null) return List.of();
        Pattern p = Pattern.compile("(https?://[^\\s\"'<>]+)");
        Matcher m = p.matcher(body);
        List<String> out = new ArrayList<>();
        while (m.find()) out.add(m.group(1));
        return out;
    }

    public static List<String> filterLinksByHost(List<String> links, String baseUrl) {
        String host = stripProtocolHost(baseUrl);
        return links.stream()
                .filter(l -> l.contains(host))
                .toList();
    }

    public static String pickPrimaryAppLink(List<String> links, String baseUrl) {
        String host = stripProtocolHost(baseUrl);
        // Prefer link on our host and containing common “start” patterns, else longest on host.
        return links.stream()
                .filter(l -> l.contains(host))
                .sorted((a,b) -> Integer.compare(b.length(), a.length()))
                .findFirst()
                .orElse(null);
    }

    public static String stripProtocolHost(String baseUrl) {
        // "https://example.com" -> "example.com"
        return baseUrl.replaceFirst("^https?://", "").replaceAll("/+$", "");
    }




    public static String htmlToText(String html) {
        if (html == null) return "";
        return html
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</p>", "\n")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n\\s*\\n+", "\n")
                .trim();
    }

    /** Finds <a href="...">Complete Assessment</a> (case-insensitive, whitespace tolerant). */
    public static String extractHrefForAnchorText(String html, String anchorText) {
        if (html == null || anchorText == null) return null;

        Pattern p = Pattern.compile(
                "(?is)<a\\s+[^>]*href\\s*=\\s*(['\"])(.*?)\\1[^>]*>\\s*"
                        + Pattern.quote(anchorText) + "\\s*</a>"
        );

        Matcher m = p.matcher(html);
        return m.find() ? m.group(2) : null;
    }

    // Optional: follow redirects (use only if you want to validate final landing page)
    public static String followRedirects(String url, int maxHops) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .build();

        String current = url;
        for (int i = 0; i < maxHops; i++) {
            var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(current)).GET().build();
            var res = client.send(req, java.net.http.HttpResponse.BodyHandlers.discarding());

            int code = res.statusCode();
            if (code >= 300 && code < 400) {
                String loc = res.headers().firstValue("location").orElse(null);
                if (loc == null) return current;
                current = java.net.URI.create(current).resolve(loc).toString();
            } else {
                return current;
            }
        }
        return current;
    }

}
