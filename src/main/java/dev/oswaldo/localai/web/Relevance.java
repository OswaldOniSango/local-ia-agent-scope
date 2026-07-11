package dev.oswaldo.localai.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Relevance {

    private Relevance() {
    }

    public static List<SearchResult> filterRelevantResults(String query, List<SearchResult> results) {
        Set<String> queryTokens = tokens(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }
        List<SearchResult> relevant = new ArrayList<>();
        for (SearchResult result : results) {
            String haystack = (result.title() + " " + result.snippet() + " " + result.url()).toLowerCase(Locale.ROOT);
            long matches = queryTokens.stream().filter(haystack::contains).count();
            int minimum = queryTokens.size() == 1 ? 1 : Math.min(2, queryTokens.size());
            if (matches >= minimum) {
                relevant.add(result);
            }
        }
        return relevant;
    }

    private static Set<String> tokens(String text) {
        List<String> values = new ArrayList<>();
        Matcher matcher = Pattern.compile("[a-zA-Z0-9]+").matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 3) {
                values.add(token);
            }
        }
        return Set.copyOf(values);
    }
}
