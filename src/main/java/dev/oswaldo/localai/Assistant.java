package dev.oswaldo.localai;

import dev.oswaldo.localai.llm.LocalModelClient;
import dev.oswaldo.localai.project.ProjectFile;
import dev.oswaldo.localai.project.ProjectRagPipeline;
import dev.oswaldo.localai.router.AssistantRouter;
import dev.oswaldo.localai.router.RouterDecision;
import dev.oswaldo.localai.web.SearchResult;
import dev.oswaldo.localai.web.WebRagPipeline;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class Assistant {

    private final LocalModelClient modelClient;
    private final AssistantRouter router;
    private final WebRagPipeline webPipeline;
    private final ProjectRagPipeline projectPipeline;

    public Assistant(Path projectRoot) {
        this.modelClient = new LocalModelClient();
        this.router = new AssistantRouter(modelClient);
        this.webPipeline = new WebRagPipeline(modelClient);
        this.projectPipeline = new ProjectRagPipeline(projectRoot, modelClient);
    }

    public String answer(String question) {
        RouterDecision routing = router.decide(question);
        System.out.println("Decision: " + routing.decision());
        System.out.println("Reason: " + routing.reason());

        if (routing.decision() == RouterDecision.Decision.WEB) {
            return answerWithWebContext(question);
        }
        return answerLocally(question);
    }

    public String answerLocally(String question) {
        return modelClient.ask(PromptPolicy.directAnswerPrompt(question));
    }

    public List<SearchResult> searchWeb(String query) {
        return webPipeline.search(query, 5);
    }

    public String answerWithWebContext(String question) {
        WebRagPipeline.WebAnswer webAnswer = webPipeline.answerQuestion(question, 4, 3, 4);
        if (webAnswer.documents().isEmpty() || webAnswer.isInsufficientContext()) {
            return answerLocally(question);
        }
        String sources = webAnswer.sourcesText(3);
        if (sources.isBlank()) {
            return webAnswer.answer();
        }
        return webAnswer.answer() + "\n\nSources:\n" + sources;
    }

    public String answerWithProjectContext(String question) {
        ProjectRagPipeline.ProjectAnswer projectAnswer = projectPipeline.answerQuestion(question, 5);
        if (projectAnswer.files().isEmpty()) {
            return "I do not have enough information in the project files to answer with certainty.";
        }
        String files = projectAnswer.files().stream()
                .limit(5)
                .map(ProjectFile::path)
                .map(path -> "- " + path)
                .collect(Collectors.joining("\n"));
        return projectAnswer.answer() + "\n\nProject files:\n" + files;
    }
}
