package dev.oswaldo.localai.router;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.oswaldo.localai.llm.LocalModelClient;
import org.junit.jupiter.api.Test;

class AssistantRouterTest {

    @Test
    void parsesCleanJsonWeb() {
        RouterDecision result = AssistantRouter.parseRouterOutput("{\"decision\": \"WEB\", \"reason\": \"asks about current standings\"}");

        assertEquals(RouterDecision.Decision.WEB, result.decision());
        assertEquals("asks about current standings", result.reason());
    }

    @Test
    void parsesCleanJsonLocal() {
        RouterDecision result = AssistantRouter.parseRouterOutput("{\"decision\": \"LOCAL\", \"reason\": \"programming concept\"}");

        assertEquals(RouterDecision.Decision.LOCAL, result.decision());
    }

    @Test
    void parsesJsonWithExtraTextAroundIt() {
        RouterDecision result = AssistantRouter.parseRouterOutput("Sure! {\"decision\": \"WEB\", \"reason\": \"news\"} done");

        assertEquals(RouterDecision.Decision.WEB, result.decision());
    }

    @Test
    void lowerCaseDecisionIsNormalized() {
        RouterDecision result = AssistantRouter.parseRouterOutput("{\"decision\": \"local\", \"reason\": \"stable concept\"}");

        assertEquals(RouterDecision.Decision.LOCAL, result.decision());
    }

    @Test
    void plainWordLocalWithoutJson() {
        RouterDecision result = AssistantRouter.parseRouterOutput("LOCAL");

        assertEquals(RouterDecision.Decision.LOCAL, result.decision());
    }

    @Test
    void unparseableOutputDefaultsToWeb() {
        RouterDecision result = AssistantRouter.parseRouterOutput("I am not sure what to answer here.");

        assertEquals(RouterDecision.Decision.WEB, result.decision());
    }

    @Test
    void invalidDecisionValueDefaultsToWeb() {
        RouterDecision result = AssistantRouter.parseRouterOutput("{\"decision\": \"MAYBE\", \"reason\": \"unsure\"}");

        assertEquals(RouterDecision.Decision.WEB, result.decision());
    }

    @Test
    void routesStableSpanishConceptLocallyWithoutCallingModel() {
        AssistantRouter router = new AssistantRouter(modelThatMustNotBeCalled());

        RouterDecision result = router.decide("¿qué es una matriz?");

        assertEquals(RouterDecision.Decision.LOCAL, result.decision());
    }

    @Test
    void routesCurrentQuestionToWebWithoutCallingModel() {
        AssistantRouter router = new AssistantRouter(modelThatMustNotBeCalled());

        RouterDecision result = router.decide("¿Cuál es el precio de Bitcoin hoy?");

        assertEquals(RouterDecision.Decision.WEB, result.decision());
    }

    @Test
    void ambiguousQuestionStillDefaultsToWebWhenModelFails() {
        LocalModelClient unavailableModel = new LocalModelClient("http://127.0.0.1:1/v1", "local", "test", 16) {
            @Override
            public String ask(String prompt) {
                throw new IllegalStateException("model unavailable");
            }
        };

        RouterDecision result = new AssistantRouter(unavailableModel).decide("¿Y entonces?");

        assertEquals(RouterDecision.Decision.WEB, result.decision());
    }

    private static LocalModelClient modelThatMustNotBeCalled() {
        return new LocalModelClient("http://127.0.0.1:1/v1", "local", "test", 16) {
            @Override
            public String ask(String prompt) {
                throw new AssertionError("The model should not be called for a clear routing intent.");
            }
        };
    }
}
