package com.studysnap.backend.config;

import com.fasterxml.jackson.databind.JsonNode;

public record OpenAiPromptResources(
        String systemPrompt,
        String developerPromptTemplate,
        JsonNode responseSchema,
        String noteGenerationSystemPrompt,
        String noteGenerationDeveloperPromptTemplate,
        String challengeQuizSystemPrompt,
        String challengeQuizDeveloperPromptTemplate,
        String boardExamSystemPrompt,
        String boardExamDeveloperPromptTemplate,
        String teacherQuizSystemPrompt,
        String teacherQuizDeveloperPromptTemplate,
        String adaptivePracticeSystemPrompt,
        String adaptivePracticeDeveloperPromptTemplate,
        String longExamSystemPrompt,
        String longExamDeveloperPromptTemplate
) {
}
