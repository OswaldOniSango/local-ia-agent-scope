package dev.oswaldo.localai;

import dev.oswaldo.localai.config.AppConfig;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.formatter.openai.OpenAIChatFormatter;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.runtime.adapters.agentscope.AgentScopeAgentHandler;
import io.agentscope.runtime.adapters.agentscope.memory.LongTermMemoryAdapter;
import io.agentscope.runtime.adapters.agentscope.memory.MemoryAdapter;
import io.agentscope.runtime.engine.schemas.AgentRequest;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

public class LocalAiAgentHandler extends AgentScopeAgentHandler {

    private static final Logger logger = LoggerFactory.getLogger(LocalAiAgentHandler.class);
    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:8080/v1";
    private static final String DEFAULT_API_KEY = "local";
    private static final String DEFAULT_MODEL = "qwen-local";

    @Override
    public Flux streamQuery(AgentRequest request, Object messages) {
        String sessionId = request.getSessionId();
        String userId = request.getUserId();

        try {
            Toolkit toolkit = new Toolkit();
            MemoryAdapter memory = buildSessionMemory(userId, sessionId);
            LongTermMemoryAdapter longTermMemory = buildLongTermMemory(userId, sessionId);

            ReActAgent.Builder agentBuilder = ReActAgent.builder()
                    .name("LocalAiAssistant")
                    .sysPrompt(PromptPolicy.systemPrompt())
                    .toolkit(toolkit)
                    .model(OpenAIChatModel.builder()
                            .baseUrl(resolveBaseUrl())
                            .apiKey(resolveApiKey())
                            .modelName(resolveModelName())
                            .stream(true)
                            .formatter(new OpenAIChatFormatter())
                            .build());

            if (memory != null) {
                agentBuilder.memory(memory);
            }
            if (longTermMemory != null) {
                agentBuilder.longTermMemory(longTermMemory)
                        .longTermMemoryMode(LongTermMemoryMode.BOTH);
            }

            ReActAgent agent = agentBuilder.build();

            Msg queryMessage = lastUserMessage(agent, messages);
            StreamOptions streamOptions = StreamOptions.builder()
                    .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
                    .incremental(true)
                    .build();

            return agent.stream(queryMessage, streamOptions)
                    .doOnError(error -> logger.error("Error in agent stream: {}", error.getMessage(), error));
        } catch (Exception exception) {
            logger.error("Error handling AgentScope query: {}", exception.getMessage(), exception);
            return Flux.error(exception);
        }
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    @Override
    public String getName() {
        return "LocalAiAssistant";
    }

    @Override
    public String getDescription() {
        return "Local AI assistant implemented with AgentScope Runtime Java.";
    }

    private Map exportState(String userId, String sessionId) {
        if (stateService == null) {
            return null;
        }
        try {
            return stateService.exportState(userId, sessionId, null).join();
        } catch (Exception exception) {
            logger.warn("Failed to export state: {}", exception.getMessage());
            return null;
        }
    }

    private MemoryAdapter buildSessionMemory(String userId, String sessionId) {
        if (sessionHistoryService == null) {
            return null;
        }
        return new MemoryAdapter(sessionHistoryService, userId, sessionId);
    }

    private LongTermMemoryAdapter buildLongTermMemory(String userId, String sessionId) {
        if (memoryService == null) {
            return null;
        }
        return new LongTermMemoryAdapter(memoryService, userId, sessionId);
    }

    private Msg lastUserMessage(ReActAgent agent, Object messages) {
        List agentMessages;
        if (messages instanceof List) {
            agentMessages = (List) messages;
        } else if (messages instanceof Msg) {
            agentMessages = List.of((Msg) messages);
        } else {
            logger.warn("Unexpected messages type: {}", messages != null ? messages.getClass().getName() : "null");
            agentMessages = List.of();
        }

        if (agentMessages.isEmpty()) {
            return Msg.builder().role(MsgRole.USER).build();
        }
        if (agentMessages.size() == 1) {
            return (Msg) agentMessages.get(0);
        }

        for (int index = 0; index < agentMessages.size() - 1; index++) {
            agent.getMemory().addMessage((Msg) agentMessages.get(index));
        }
        return (Msg) agentMessages.get(agentMessages.size() - 1);
    }

    private void saveState(String userId, String sessionId, ReActAgent agent) {
        if (stateService == null) {
            return;
        }
        try {
            Map finalState = agent.stateDict();
            if (finalState != null && !finalState.isEmpty()) {
                stateService.saveState(userId, finalState, sessionId, null)
                        .exceptionally(error -> {
                            logger.error("Failed to save state: {}", String.valueOf(error), error);
                            return null;
                        });
            }
        } catch (Exception exception) {
            logger.error("Error saving state: {}", exception.getMessage(), exception);
        }
    }

    private String resolveBaseUrl() {
        return AppConfig.value("LOCAL_LLM_BASE_URL", DEFAULT_BASE_URL);
    }

    private String resolveApiKey() {
        return AppConfig.value("LOCAL_LLM_API_KEY", DEFAULT_API_KEY);
    }

    private String resolveModelName() {
        return AppConfig.value("LOCAL_LLM_MODEL", DEFAULT_MODEL);
    }
}
