package dev.oswaldo.localai.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class QueryPlannerTest {

    @Test
    void generatedQueriesComeBeforeFallback() {
        List<String> queries = QueryPlanner.finalizeQueries(
                List.of("Aaron Judge injury latest"),
                "de que esta lesionado Aaron Judge?",
                5
        );

        assertEquals(List.of("Aaron Judge injury latest", "de que esta lesionado Aaron Judge?"), queries);
    }

    @Test
    void deduplicatesFallback() {
        List<String> queries = QueryPlanner.finalizeQueries(
                List.of("who is Aaron Judge"),
                "who is Aaron Judge",
                5
        );

        assertEquals(List.of("who is Aaron Judge"), queries);
    }
}
