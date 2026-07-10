package dev.oswaldo.localai.web;

import dev.oswaldo.localai.PromptPolicy;
import dev.oswaldo.localai.llm.LocalModelClient;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class QueryPlanner {

    private static final Pattern PREFIX_PATTERN = Pattern.compile("^\\s*(?:[-*]\\s*)?(?:\\d+[.)]\\s*)?");

    private final LocalModelClient modelClient;

    public QueryPlanner(LocalModelClient modelClient) {
        this.modelClient = modelClient;
    }

    public List<String> generateSearchQueries(String question, int limit) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question cannot be empty.");
        }
        String rawOutput = modelClient.ask(PromptPolicy.queryPlannerPrompt(question));
        return finalizeQueries(parseQueryLines(rawOutput), question, limit);
    }

    static List<String> parseQueryLines(String rawOutput) {
        List<String> queries = new ArrayList<>();
        for (String line : rawOutput.split("\\R")) {
            String cleaned = PREFIX_PATTERN.matcher(line).replaceFirst("").trim();
            if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() > 1) {
                cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
            }
            if (!cleaned.isBlank()) {
                queries.add(cleaned);
            }
        }
        return queries;
    }

    static List<String> finalizeQueries(List<String> rawQueries, String fallbackQuery, int limit) {
        Set<String> deduped = new LinkedHashSet<>();
        for (String query : rawQueries) {
            String normalized = normalize(query);
            if (!normalized.isBlank()) {
                deduped.add(normalized);
            }
        }
        String fallback = normalize(fallbackQuery);
        if (!fallback.isBlank()) {
            deduped.add(fallback);
        }
        return deduped.stream().limit(limit).toList();
    }

    private static String normalize(String query) {
        return query == null ? "" : query.replaceAll("\\s+", " ").trim();
    }
}
