package dev.oswaldo.localai;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.oswaldo.localai.config.AppConfig;
import dev.oswaldo.localai.web.SearchResult;
import java.util.List;

public class LocalAiCli {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            printUsage();
            return;
        }

        String command = args[0];
        String argument = joinArguments(args);
        Assistant assistant = new Assistant(AppConfig.projectReaderRoot());

        try {
            switch (command) {
                case "chat" -> System.out.println(assistant.answerLocally(argument));
                case "ask" -> System.out.println(assistant.answer(argument));
                case "search" -> printSearchResults(assistant.searchWeb(argument));
                case "search-answer" -> System.out.println(assistant.answerWithWebContext(argument));
                case "project-answer" -> System.out.println(assistant.answerWithProjectContext(argument));
                default -> printUsage();
            }
        } catch (RuntimeException exception) {
            System.err.println("Error: " + exception.getMessage());
        }
    }

    private static String joinArguments(String[] args) {
        StringBuilder builder = new StringBuilder();
        for (int index = 1; index < args.length; index++) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString().trim();
    }

    private static void printSearchResults(List<SearchResult> results) throws Exception {
        if (results.isEmpty()) {
            System.out.println("No results found.");
            return;
        }
        System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(results));
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  ./scripts/assistant.sh chat \"Explain what Snowflake is\"");
        System.out.println("  ./scripts/assistant.sh ask \"Is Aaron Judge injured right now?\"");
        System.out.println("  ./scripts/assistant.sh search \"Who is Aaron Judge?\"");
        System.out.println("  ./scripts/assistant.sh search-answer \"Who is Aaron Judge?\"");
        System.out.println("  ./scripts/assistant.sh project-answer \"Explain this project\"");
    }
}
