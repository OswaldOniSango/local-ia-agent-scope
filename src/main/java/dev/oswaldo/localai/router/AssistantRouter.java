package dev.oswaldo.localai.router;

import dev.oswaldo.localai.llm.LocalModelClient;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AssistantRouter {

    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{.*?}", Pattern.DOTALL);
    private static final Pattern DECISION_PATTERN = Pattern.compile("\"decision\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern REASON_PATTERN = Pattern.compile("\"reason\"\\s*:\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern WEB_INTENT_PATTERN = Pattern.compile(
            "\\b(?:hoy|ahora|actual|actuales|actualmente|reciente|recientes|ultimo|ultima|ultimos|ultimas|"
                    + "noticia|noticias|precio|precios|cotizacion|clima|pronostico|marcador|clasificacion|"
                    + "temporada|lesionado|lesionada|esta semana|este mes|este ano|today|now|current|currently|"
                    + "recent|latest|news|price|weather|forecast|score|standings|season|injured)\\b"
    );
    private static final Pattern LOCAL_INTENT_PATTERN = Pattern.compile(
            "\\b(?:que es|define|explica|explicar|como funciona|traduce|traducir|matriz|algebra|geometria|"
                    + "matematica|programacion|algoritmo|codigo|concepto|historia|gramatica|"
                    + "what is|define|explain|how does|translate|matrix|algebra|geometry|math|programming|"
                    + "algorithm|code|concept|history|grammar)\\b"
    );

    private final LocalModelClient modelClient;

    public AssistantRouter(LocalModelClient modelClient) {
        this.modelClient = modelClient;
    }

    public RouterDecision decide(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question cannot be empty.");
        }

        RouterDecision clearIntent = classifyClearIntent(question);
        if (clearIntent != null) {
            return clearIntent;
        }

        try {
            return parseRouterOutput(modelClient.ask(buildPrompt(question)));
        } catch (RuntimeException exception) {
            return new RouterDecision(
                    RouterDecision.Decision.WEB,
                    "Router model failed, defaulting to web search."
            );
        }
    }

    static RouterDecision classifyClearIntent(String question) {
        String normalized = normalize(question);
        if (WEB_INTENT_PATTERN.matcher(normalized).find()) {
            return new RouterDecision(
                    RouterDecision.Decision.WEB,
                    "Question contains a time-sensitive or live-information cue."
            );
        }
        if (LOCAL_INTENT_PATTERN.matcher(normalized).find()) {
            return new RouterDecision(
                    RouterDecision.Decision.LOCAL,
                    "Question is a stable concept that can be answered locally."
            );
        }
        return null;
    }

    private static String normalize(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    public static RouterDecision parseRouterOutput(String rawOutput) {
        Matcher jsonMatcher = JSON_OBJECT_PATTERN.matcher(rawOutput == null ? "" : rawOutput);
        if (!jsonMatcher.find()) {
            return fallbackDecision(rawOutput);
        }

        String json = jsonMatcher.group();
        Matcher decisionMatcher = DECISION_PATTERN.matcher(json);
        if (!decisionMatcher.find()) {
            return fallbackDecision(rawOutput);
        }

        String decisionValue = decisionMatcher.group(1).trim().toUpperCase(Locale.ROOT);
        String reason = "No reason given.";
        Matcher reasonMatcher = REASON_PATTERN.matcher(json);
        if (reasonMatcher.find() && !reasonMatcher.group(1).isBlank()) {
            reason = reasonMatcher.group(1).trim();
        }

        try {
            return new RouterDecision(RouterDecision.Decision.valueOf(decisionValue), reason);
        } catch (IllegalArgumentException exception) {
            return fallbackDecision(rawOutput);
        }
    }

    private static RouterDecision fallbackDecision(String rawOutput) {
        String normalizedOutput = rawOutput == null ? "" : rawOutput.trim().toUpperCase(Locale.ROOT);
        if (normalizedOutput.contains("LOCAL") && !normalizedOutput.contains("WEB")) {
            return new RouterDecision(
                    RouterDecision.Decision.LOCAL,
                    "Model answered LOCAL without valid JSON."
            );
        }
        return new RouterDecision(
                RouterDecision.Decision.WEB,
                "Could not parse router output, defaulting to web search."
        );
    }

    private static String buildPrompt(String question) {
        return "You are a decision router for a local AI assistant.\n"
                + "Your job is to decide whether the assistant needs web search.\n\n"
                + "Return only valid JSON:\n"
                + "{\"decision\": \"WEB\" or \"LOCAL\", \"reason\": \"short reason\"}\n\n"
                + "Choose WEB when the answer depends on:\n"
                + "- current or recent facts\n"
                + "- sports, injuries, standings, schedules\n"
                + "- news\n"
                + "- prices\n"
                + "- laws or regulations\n"
                + "- software versions or releases\n"
                + "- company/person status\n"
                + "- anything happening today, now, this week, this season, recently, currently\n\n"
                + "Choose LOCAL when the answer is about:\n"
                + "- explaining code\n"
                + "- programming concepts\n"
                + "- math\n"
                + "- general knowledge\n"
                + "- writing help\n"
                + "- translations\n"
                + "- stable concepts\n\n"
                + "User question: " + question + "\n";
    }
}
