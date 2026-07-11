package dev.oswaldo.localai.web;

import dev.oswaldo.localai.PromptPolicy;
import dev.oswaldo.localai.config.AppConfig;
import dev.oswaldo.localai.llm.LocalModelClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WebRagPipeline {

    private final QueryPlanner queryPlanner;
    private final WebSearchService searchService;
    private final WebContentExtractor contentExtractor;
    private final LocalModelClient modelClient;
    private final int maxContextChars;

    public WebRagPipeline(LocalModelClient modelClient) {
        this.queryPlanner = new QueryPlanner(modelClient);
        this.searchService = new WebSearchService();
        this.contentExtractor = new WebContentExtractor();
        this.modelClient = modelClient;
        this.maxContextChars = AppConfig.intValue("WEB_CONTEXT_MAX_CHARS", 6_000);
    }

    public WebAnswer answerQuestion(String question, int queryLimit, int resultsPerQuery, int documentLimit) {
        List<String> queries = queryPlanner.generateSearchQueries(question, queryLimit);
        List<SearchResult> searchResults = collectResults(queries, resultsPerQuery);
        List<RetrievedDocument> documents = contentExtractor.extractDocuments(searchResults, documentLimit);
        if (documents.isEmpty()) {
            return new WebAnswer("", searchResults, documents, queries);
        }
        String answer = modelClient.ask(PromptPolicy.webAnswerPrompt(question, buildSearchContext(documents, maxContextChars)));
        return new WebAnswer(answer, searchResults, documents, queries);
    }

    public List<SearchResult> search(String query, int limit) {
        return searchService.search(query, limit);
    }

    private List<SearchResult> collectResults(List<String> queries, int resultsPerQuery) {
        Map<String, SearchResult> deduped = new LinkedHashMap<>();
        for (String query : queries) {
            for (SearchResult result : searchService.search(query, resultsPerQuery)) {
                deduped.putIfAbsent(normalizeUrl(result.url()), result);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private static String buildSearchContext(List<RetrievedDocument> documents, int maxContextChars) {
        if (documents.isEmpty()) {
            return "No relevant web documents were found.";
        }
        List<String> blocks = new ArrayList<>();
        int remainingChars = maxContextChars;
        for (int index = 0; index < documents.size() && remainingChars > 0; index++) {
            RetrievedDocument document = documents.get(index);
            String header = "[Source " + (index + 1) + "]\nTitle: " + document.title() + "\nURL: " + document.url() + "\nContent: ";
            int contentBudget = Math.max(0, remainingChars - header.length() - 2);
            if (contentBudget == 0) {
                break;
            }
            String content = truncate(document.content(), contentBudget);
            String block = header + content;
            blocks.add(block);
            remainingChars -= block.length() + 2;
        }
        return String.join("\n\n", blocks);
    }

    private static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, Math.max(0, maxChars)) + "...";
    }

    private static String normalizeUrl(String url) {
        return url == null ? "" : url.replaceFirst("^https?://", "").replaceFirst("^www\\.", "").replaceAll("/+$", "");
    }

    public record WebAnswer(String answer, List<SearchResult> searchResults, List<RetrievedDocument> documents, List<String> queries) {
        public boolean isInsufficientContext() {
            return answer != null && answer.trim().toUpperCase().contains(PromptPolicy.insufficientContextToken());
        }

        public String sourcesText(int limit) {
            return searchResults.stream()
                    .limit(limit)
                    .map(result -> "- " + (result.title().isBlank() ? "Untitled" : result.title()) + ": " + result.url())
                    .collect(Collectors.joining("\n"));
        }
    }
}
