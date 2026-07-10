package dev.oswaldo.localai.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebSearchService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern RESULT_PATTERN = Pattern.compile("(?s)<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>.*?(?:<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>|<div[^>]*class=\"result__snippet\"[^>]*>(.*?)</div>)");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public List<SearchResult> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query cannot be empty.");
        }
        List<SearchResult> htmlResults = Relevance.filterRelevantResults(query, searchDuckDuckGoHtml(query, limit));
        if (!htmlResults.isEmpty()) {
            return htmlResults.stream().limit(limit).toList();
        }
        List<SearchResult> instantResults = Relevance.filterRelevantResults(query, searchDuckDuckGoInstant(query, limit));
        if (!instantResults.isEmpty()) {
            return instantResults.stream().limit(limit).toList();
        }
        return List.of();
    }

    private List<SearchResult> searchDuckDuckGoInstant(String query, int limit) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://api.duckduckgo.com/?q=" + encoded + "&format=json&no_redirect=1&no_html=1";
        try {
            JsonNode root = MAPPER.readTree(get(url));
            List<SearchResult> results = new ArrayList<>();
            String abstractText = root.path("AbstractText").asText("").trim();
            if (!abstractText.isBlank()) {
                results.add(new SearchResult(root.path("Heading").asText(""), root.path("AbstractURL").asText(""), abstractText));
            }
            collectRelatedTopics(root.path("RelatedTopics"), results, limit);
            return results.stream().limit(limit).toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<SearchResult> searchDuckDuckGoHtml(String query, int limit) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://html.duckduckgo.com/html/?q=" + encoded;
        try {
            String page = get(url);
            Matcher matcher = RESULT_PATTERN.matcher(page);
            List<SearchResult> results = new ArrayList<>();
            while (matcher.find() && results.size() < limit) {
                String href = decodeDuckDuckGoUrl(unescape(matcher.group(1)));
                String title = stripTags(unescape(matcher.group(2)));
                String snippet = stripTags(unescape(matcher.group(3) != null ? matcher.group(3) : matcher.group(4)));
                if (!title.isBlank() && !href.isBlank()) {
                    results.add(new SearchResult(title, href, snippet));
                }
            }
            return results;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        return response.body();
    }

    private static void collectRelatedTopics(JsonNode node, List<SearchResult> results, int limit) {
        if (!node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            if (results.size() >= limit) {
                return;
            }
            if (item.path("Topics").isArray()) {
                collectRelatedTopics(item.path("Topics"), results, limit);
                continue;
            }
            String text = item.path("Text").asText("").trim();
            String url = item.path("FirstURL").asText("").trim();
            if (text.isBlank() || url.isBlank()) {
                continue;
            }
            String title = text.contains(" - ") ? text.substring(0, text.indexOf(" - ")).trim() : text;
            String snippet = text.contains(" - ") ? text.substring(text.indexOf(" - ") + 3).trim() : text;
            results.add(new SearchResult(title, url, snippet));
        }
    }

    private static String decodeDuckDuckGoUrl(String href) {
        if (!href.contains("uddg=")) {
            return href;
        }
        int start = href.indexOf("uddg=") + 5;
        int end = href.indexOf('&', start);
        String encoded = end >= 0 ? href.substring(start, end) : href.substring(start);
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    private static String stripTags(String value) {
        return value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private static String unescape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#x27;", "'")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }
}
