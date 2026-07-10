package dev.oswaldo.localai.web;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WebContentExtractor {

    private static final int MAX_CONTENT_CHARS = 1_500;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public List<RetrievedDocument> extractDocuments(List<SearchResult> results, int limit) {
        List<RetrievedDocument> documents = new ArrayList<>();
        for (SearchResult result : results) {
            if (documents.size() >= limit) {
                break;
            }
            String content = fetchText(result.url());
            if (content.isBlank()) {
                content = result.snippet();
            }
            if (!content.isBlank()) {
                documents.add(new RetrievedDocument(result.title(), result.url(), truncate(content)));
            }
        }
        return documents;
    }

    private String fetchText(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "";
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            if (!contentType.isBlank() && !contentType.contains("text") && !contentType.contains("html") && !contentType.contains("json")) {
                return "";
            }
            return htmlToText(response.body());
        } catch (IOException | InterruptedException | IllegalArgumentException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "";
        }
    }

    private static String htmlToText(String html) {
        return html.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String truncate(String text) {
        if (text.length() <= MAX_CONTENT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_CONTENT_CHARS);
    }
}
