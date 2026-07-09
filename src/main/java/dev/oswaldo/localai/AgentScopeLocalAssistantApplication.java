package dev.oswaldo.localai;

import io.agentscope.runtime.app.AgentApp;
import io.agentscope.runtime.engine.services.agent_state.InMemoryStateService;
import io.agentscope.runtime.engine.services.memory.persistence.memory.service.InMemoryMemoryService;
import io.agentscope.runtime.engine.services.memory.persistence.session.InMemorySessionHistoryService;

public class AgentScopeLocalAssistantApplication {

    private static final int DEFAULT_PORT = 10001;

    public static void main(String[] args) {
        LocalAiAgentHandler agentHandler = new LocalAiAgentHandler();
        agentHandler.setStateService(new InMemoryStateService());
        agentHandler.setSessionHistoryService(new InMemorySessionHistoryService());
        agentHandler.setMemoryService(new InMemoryMemoryService());

        AgentApp agentApp = new AgentApp(agentHandler);
        agentApp.cors(registry -> registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowCredentials(true));
        agentApp.run(resolvePort());
    }

    private static int resolvePort() {
        String rawPort = System.getenv("AGENTSCOPE_PORT");
        if (rawPort == null || rawPort.isBlank()) {
            return DEFAULT_PORT;
        }
        return Integer.parseInt(rawPort);
    }
}
