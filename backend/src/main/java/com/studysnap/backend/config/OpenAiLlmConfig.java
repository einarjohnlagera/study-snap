package com.studysnap.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(prefix = "studysnap.llm.api", name = "provider", havingValue = "openai", matchIfMissing = true)
public class OpenAiLlmConfig {
    @Bean
    public RestClient openAiRestClient(StudySnapProperties properties) {
        return RestClient.builder()
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
        JsonNode responseSchema = readResourceAsJson(promptDir + "/schema.json", objectMapper);
        return new OpenAiPromptResources(systemPrompt, developerPromptTemplate, responseSchema);
    }

    private String normalizePromptDir(String promptDir) {
        if (promptDir == null || promptDir.isBlank()) {
            return "prompts/review-v1";
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
