package dev.oswaldo.localai.project;

import dev.oswaldo.localai.PromptPolicy;
import dev.oswaldo.localai.config.AppConfig;
import dev.oswaldo.localai.llm.LocalModelClient;
import java.nio.file.Path;
import java.util.List;

public class ProjectRagPipeline {

    private final ProjectReaderService reader;
    private final LocalModelClient modelClient;
    private final int maxContextChars;

    public ProjectRagPipeline(Path root, LocalModelClient modelClient) {
        this.reader = new ProjectReaderService(root);
        this.modelClient = modelClient;
        this.maxContextChars = AppConfig.intValue("PROJECT_CONTEXT_MAX_CHARS", 7_000);
    }

    public ProjectAnswer answerQuestion(String question, int fileLimit) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question cannot be empty.");
        }
        List<ProjectFile> files = reader.search(question, fileLimit);
        if (files.isEmpty()) {
            return new ProjectAnswer("", files);
        }
        String answer = modelClient.ask(PromptPolicy.projectAnswerPrompt(question, buildProjectContext(files, maxContextChars)));
        return new ProjectAnswer(answer, files);
    }

    public static String buildProjectContext(List<ProjectFile> files) {
        return buildProjectContext(files, 7_000);
    }

    public static String buildProjectContext(List<ProjectFile> files, int maxContextChars) {
        if (files.isEmpty()) {
            return "No project files were found.";
        }
        StringBuilder context = new StringBuilder();
        for (ProjectFile file : files) {
            String header = "Path: " + file.path() + "\nLanguage: " + file.language() + "\nContent:\n";
            int remaining = maxContextChars - context.length() - header.length() - 2;
            if (remaining <= 0) {
                break;
            }
            if (context.length() > 0) {
                context.append("\n\n");
            }
            context.append(header).append(truncate(file.content(), remaining));
        }
        return context.toString();
    }

    private static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, Math.max(0, maxChars)) + "...";
    }

    public record ProjectAnswer(String answer, List<ProjectFile> files) {
    }
}
