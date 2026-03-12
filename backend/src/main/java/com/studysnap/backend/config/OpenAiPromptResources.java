package com.studysnap.backend.config;

import com.fasterxml.jackson.databind.JsonNode;

public record OpenAiPromptResources(
        String systemPrompt,
        String developerPromptTemplate,
        JsonNode responseSchema,
        String adaptivePracticeSystemPrompt,
        String adaptivePracticeDeveloperPromptTemplate
) {
}
