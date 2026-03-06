package com.studysnap.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.config.OpenAiPromptResources;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.service.LlmReviewService;
import com.studysnap.backend.service.model.GeneratedReviewContent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@ConditionalOnProperty(prefix = "studysnap.llm.api", name = "provider", havingValue = "openai", matchIfMissing = true)
@RequiredArgsConstructor
public class OpenAiLlmReviewService implements LlmReviewService {
    private final StudySnapProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final OpenAiPromptResources promptResources;

    @Override
    public GeneratedReviewContent generateReview(String normalizedNotesText) {
        if (properties.getLlm().getApi().getApiKey() == null || properties.getLlm().getApi().getApiKey().isBlank()) {
            throw new AppException(
                    "LLM_CONFIGURATION_ERROR",
                    "LLM API key is missing. Please configure LLM_API_KEY.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", properties.getSettings().getModelFree());
        requestBody.set("input", buildInputMessages(normalizedNotesText));
        ObjectNode textNode = requestBody.putObject("text");
        ObjectNode formatNode = textNode.putObject("format");
        formatNode.put("type", "json_schema");
        formatNode.put("name", "study_snap_review");
        formatNode.set("schema", promptResources.responseSchema());
        formatNode.put("strict", true);

        try {
            String responseBody = restClient.post()
                    .uri("/responses")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw new AppException(
                        "LLM_EMPTY_RESPONSE",
                        "The review service returned an empty response. Please try again.",
                        HttpStatus.BAD_GATEWAY
                );
            }

            JsonNode responseJson = objectMapper.readTree(responseBody);
            String outputJson = extractOutputJson(responseJson);
            PromptReview promptReview = objectMapper.readValue(outputJson, PromptReview.class);

            List<QuizItem> quizItems = new ArrayList<>();
            for (PromptQuizItem item : promptReview.quiz()) {
                if (item.choices() == null || item.choices().size() != 4) {
                    throw new AppException(
                            "LLM_INVALID_OUTPUT",
                            "The review service returned an invalid quiz format. Please try again.",
                            HttpStatus.BAD_GATEWAY
                    );
                }
                if (item.answerIndex() < 0 || item.answerIndex() >= item.choices().size()) {
                    throw new AppException(
                            "LLM_INVALID_OUTPUT",
                            "The review service returned an invalid quiz answer. Please try again.",
                            HttpStatus.BAD_GATEWAY
                    );
                }

                quizItems.add(new QuizItem(
                        item.question(),
                        item.choices(),
                        item.choices().get(item.answerIndex()),
                        item.explanation()
                ));
            }

            JsonNode usage = responseJson.path("usage");
            Integer inputTokens = asNullableInt(usage.get("input_tokens"));
            Integer outputTokens = asNullableInt(usage.get("output_tokens"));
            Integer cachedInputTokens = asNullableInt(usage.path("input_tokens_details").get("cached_tokens"));
            String modelUsed = responseJson.path("model").asText(properties.getSettings().getModelFree());

            return new GeneratedReviewContent(
                    promptReview.title(),
                    promptReview.summary(),
                    promptReview.keyConcepts(),
                    quizItems,
                    modelUsed,
                    inputTokens,
                    outputTokens,
                    cachedInputTokens,
                    null
            );
        } catch (RestClientResponseException ex) {
            throw new AppException(
                    "LLM_REQUEST_FAILED",
                    "Review generation failed. Please try again in a moment.",
                    ex.getResponseBodyAsString(),
                    HttpStatus.BAD_GATEWAY
            );
        } catch (RestClientException | IOException ex) {
            throw new AppException(
                    "LLM_UNAVAILABLE",
                    "Review generation is temporarily unavailable. Please try again.",
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    private ArrayNode buildInputMessages(String normalizedNotesText) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(buildTextMessage("system", promptResources.systemPrompt()));

        String developerPrompt = promptResources.developerPromptTemplate().replace(
                "{QUIZ_COUNT}",
                String.valueOf(properties.getSettings().getQuizQuestionsFree())
        );
        input.add(buildTextMessage("developer", developerPrompt));
        input.add(buildTextMessage("user", "Study notes:\n" + normalizedNotesText));

        return input;
    }

    private ObjectNode buildTextMessage(String role, String text) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", role);
        ArrayNode content = message.putArray("content");
        ObjectNode contentItem = content.addObject();
        contentItem.put("type", "input_text");
        contentItem.put("text", text);
        return message;
    }

    private String extractOutputJson(JsonNode responseJson) {
        JsonNode outputTextNode = responseJson.get("output_text");
        if (outputTextNode != null && outputTextNode.isTextual()) {
            return outputTextNode.asText();
        }

        for (JsonNode outputNode : responseJson.path("output")) {
            for (JsonNode contentNode : outputNode.path("content")) {
                if ("output_text".equals(contentNode.path("type").asText()) && contentNode.hasNonNull("text")) {
                    return contentNode.path("text").asText();
                }
            }
        }

        throw new AppException(
                "LLM_INVALID_OUTPUT",
                "The review service returned an unexpected format. Please try again.",
                HttpStatus.BAD_GATEWAY
        );
    }

    private Integer asNullableInt(JsonNode node) {
        return node != null && node.isNumber() ? node.intValue() : null;
    }

    private record PromptReview(
            String title,
            String summary,
            List<String> keyConcepts,
            List<PromptQuizItem> quiz
    ) {
        PromptReview {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(summary, "summary");
            Objects.requireNonNull(keyConcepts, "keyConcepts");
            Objects.requireNonNull(quiz, "quiz");
        }
    }

    private record PromptQuizItem(
            String question,
            List<String> choices,
            int answerIndex,
            String explanation
    ) {
    }
}
