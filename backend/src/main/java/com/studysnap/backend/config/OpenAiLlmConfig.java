package com.studysnap.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(prefix = "studysnap.llm.api", name = "provider", havingValue = "openai", matchIfMissing = true)
public class OpenAiLlmConfig {
    @Bean
    public RestClient openAiRestClient(StudySnapProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(180));
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(properties.getLlm().getApi().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getLlm().getApi().getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public OpenAiPromptResources openAiPromptResources(
            ObjectMapper objectMapper,
            StudySnapProperties properties
    ) {
        String promptDir = normalizePromptDir(properties.getSettings().getPromptDir());
        String systemPrompt = readResourceAsString(promptDir + "/system.txt");
        String developerPromptTemplate = readResourceAsString(promptDir + "/developer.txt");
        String noteGenerationSystemPrompt = readResourceAsString(promptDir + "/note-generation-system.txt");
        String noteGenerationDeveloperPromptTemplate = readResourceAsString(promptDir + "/note-generation-developer.txt");
        String challengeQuizSystemPrompt = readResourceAsString(promptDir + "/challenge-quiz-system.txt");
        String challengeQuizDeveloperPromptTemplate = readResourceAsString(promptDir + "/challenge-quiz-developer.txt");
        String boardExamSystemPrompt = readResourceAsString(promptDir + "/board-exam-system.txt");
        String boardExamDeveloperPromptTemplate = readResourceAsString(promptDir + "/board-exam-developer.txt");
        String teacherQuizSystemPrompt = readResourceAsString(promptDir + "/teacher-quiz-system.txt");
        String teacherQuizDeveloperPromptTemplate = readResourceAsString(promptDir + "/teacher-quiz-developer.txt");
        String adaptivePracticeSystemPrompt = readResourceAsString(promptDir + "/adaptive-practice-system.txt");
        String adaptivePracticeDeveloperPromptTemplate = readResourceAsString(promptDir + "/adaptive-practice-developer.txt");
        String interviewPracticeSystemPrompt = readResourceAsString(promptDir + "/interview-practice-system.txt");
        String interviewPracticeDeveloperPromptTemplate = readResourceAsString(promptDir + "/interview-practice-developer.txt");
        String interviewCritiqueSystemPrompt = readResourceAsString(promptDir + "/interview-critique-system.txt");
        String interviewCritiqueDeveloperPromptTemplate = readResourceAsString(promptDir + "/interview-critique-developer.txt");
        String longExamSystemPrompt = readResourceAsString(promptDir + "/long-exam-system.txt");
        String longExamDeveloperPromptTemplate = readResourceAsString(promptDir + "/long-exam-developer.txt");
        JsonNode responseSchema = readResourceAsJson(promptDir + "/schema.json", objectMapper);
        return new OpenAiPromptResources(
                systemPrompt,
                developerPromptTemplate,
                responseSchema,
                noteGenerationSystemPrompt,
                noteGenerationDeveloperPromptTemplate,
                challengeQuizSystemPrompt,
                challengeQuizDeveloperPromptTemplate,
                boardExamSystemPrompt,
                boardExamDeveloperPromptTemplate,
                teacherQuizSystemPrompt,
                teacherQuizDeveloperPromptTemplate,
                adaptivePracticeSystemPrompt,
                adaptivePracticeDeveloperPromptTemplate,
                interviewPracticeSystemPrompt,
                interviewPracticeDeveloperPromptTemplate,
                interviewCritiqueSystemPrompt,
                interviewCritiqueDeveloperPromptTemplate,
                longExamSystemPrompt,
                longExamDeveloperPromptTemplate
        );
    }

    private String normalizePromptDir(String promptDir) {
        if (promptDir == null || promptDir.isBlank()) {
            return "prompts/study-pack-v1";
        }
        if (promptDir.endsWith("/")) {
            return promptDir.substring(0, promptDir.length() - 1);
        }
        return promptDir;
    }

    private String readResourceAsString(String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            byte[] bytes = resource.getInputStream().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read resource: " + resourcePath, ex);
        }
    }

    private JsonNode readResourceAsJson(String resourcePath, ObjectMapper objectMapper) {
        try {
            return objectMapper.readTree(readResourceAsString(resourcePath));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse JSON resource: " + resourcePath, ex);
        }
    }
}
