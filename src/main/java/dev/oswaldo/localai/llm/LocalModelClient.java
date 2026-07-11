package dev.oswaldo.localai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.oswaldo.localai.config.AppConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class LocalModelClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public LocalModelClient() {
        this(
                AppConfig.value("LOCAL_LLM_BASE_URL", "http://127.0.0.1:8080/v1"),
                AppConfig.value("LOCAL_LLM_API_KEY", "local"),
                AppConfig.value("LOCAL_LLM_MODEL", "qwen-local"),
                AppConfig.intValue("QWEN_MAX_TOKENS", 512)
        );
    }

    public LocalModelClient(String baseUrl, String apiKey, String model, int maxTokens) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    public String ask(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt cannot be empty.");
        }

        try {
            Map<String, Object> payload = Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "temperature", 0.2,
                    "max_tokens", maxTokens,
                    "stream", false
            );
            String body = MAPPER.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Model request failed with HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = MAPPER.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new IllegalStateException("Model response did not contain assistant content: " + response.body());
            }
            return content.asText().trim();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to call local model endpoint at " + baseUrl, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling local model endpoint.", exception);
        }
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
