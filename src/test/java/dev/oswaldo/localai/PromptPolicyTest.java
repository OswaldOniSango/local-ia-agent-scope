package dev.oswaldo.localai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PromptPolicyTest {

    @Test
    void systemPromptKeepsUserLanguage() {
        String prompt = PromptPolicy.systemPrompt();

        assertTrue(prompt.contains("same language"));
        assertTrue(prompt.contains("Spanish"));
        assertTrue(prompt.contains("English"));
    }
}
