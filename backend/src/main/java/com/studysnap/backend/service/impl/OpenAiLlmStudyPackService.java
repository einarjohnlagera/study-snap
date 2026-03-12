package com.studysnap.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.config.OpenAiPromptResources;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.service.LlmStudyPackService;
import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

@Service
@ConditionalOnProperty(prefix = "studysnap.llm.api", name = "provider", havingValue = "openai", matchIfMissing = true)
@RequiredArgsConstructor
public class OpenAiLlmStudyPackService implements LlmStudyPackService {
    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmStudyPackService.class);
    private static final int MAX_STUDY_TIP_LENGTH = 280;

    private final StudySnapProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final OpenAiPromptResources promptResources;

    @Override
    public GeneratedStudyPackContent generateStudyPack(String normalizedNotesText) {
        if (properties.getLlm().getApi().getApiKey() == null || properties.getLlm().getApi().getApiKey().isBlank()) {
            throw new AppException(
                    "LLM_CONFIGURATION_ERROR",
                    "LLM API key is missing. Please configure LLM_API_KEY.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
        String model = properties.getSettings().getModelFree();
        if (model == null || model.isBlank()) {
            throw new AppException(
                    "LLM_CONFIGURATION_ERROR",
                    "LLM model is missing. Please configure LLM_MODEL_FREE.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.set("input", buildInputMessages(normalizedNotesText));
        ObjectNode textNode = requestBody.putObject("text");
        ObjectNode formatNode = textNode.putObject("format");
        formatNode.put("type", "json_schema");
        formatNode.put("name", "study_snap_study_pack");
        formatNode.set("schema", promptResources.responseSchema());
        formatNode.put("strict", true);

        try {
            String requestBodyJson = objectMapper.writeValueAsString(requestBody);
            String responseBody = restClient.post()
                    .uri("/responses")
                    .body(requestBodyJson)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw new AppException(
                        "LLM_EMPTY_RESPONSE",
                        "The study pack service returned an empty response. Please try again.",
                        HttpStatus.BAD_GATEWAY
                );
            }

            JsonNode responseJson = objectMapper.readTree(responseBody);
            String outputJson = extractOutputJson(responseJson);
            PromptStudyPack promptStudyPack = objectMapper.readValue(outputJson, PromptStudyPack.class);
            int expectedQuizCount = properties.getSettings().getQuizQuestionsFree();
            if (promptStudyPack.quiz().size() != expectedQuizCount) {
                throw new AppException(
                        "LLM_INVALID_OUTPUT",
                        "The study pack service returned an invalid quiz format. Please try again.",
                        HttpStatus.BAD_GATEWAY
                );
            }

            List<QuizItem> quizItems = new ArrayList<>();
            Set<String> normalizedQuestions = new HashSet<>();
            Set<String> normalizedConcepts = new HashSet<>();
            for (PromptQuizItem item : promptStudyPack.quiz()) {
                if (item.choices() == null || item.choices().size() != 4) {
                    throw new AppException(
                            "LLM_INVALID_OUTPUT",
                            "The study pack service returned an invalid quiz format. Please try again.",
                            HttpStatus.BAD_GATEWAY
                    );
                }
                if (item.answerIndex() < 0 || item.answerIndex() >= item.choices().size()) {
                    throw new AppException(
                            "LLM_INVALID_OUTPUT",
                            "The study pack service returned an invalid quiz answer. Please try again.",
                            HttpStatus.BAD_GATEWAY
                    );
                }
                if (isBlank(item.question()) || isBlank(item.explanation())) {
                    throw new AppException(
                            "LLM_INVALID_OUTPUT",
                            "The study pack service returned an invalid quiz format. Please try again.",
                            HttpStatus.BAD_GATEWAY
                    );
                }
                if (isBlank(item.concept())) {
                    throw new AppException(
                            "LLM_INVALID_OUTPUT",
                            "The study pack service returned an invalid quiz format. Please try again.",
                            HttpStatus.BAD_GATEWAY
                    );
                }
                if (!normalizedQuestions.add(normalizeForDuplicateCheck(item.question()))) {
                    throw new AppException(
                            "LLM_INVALID_OUTPUT",
                            "The study pack service returned repetitive quiz questions. Please try again.",
                            HttpStatus.BAD_GATEWAY
                    );
                }
                if (!normalizedConcepts.add(normalizeForDuplicateCheck(item.concept()))) {
                    throw new AppException(
                            "LLM_INVALID_OUTPUT",
                            "The study pack service returned repetitive quiz concepts. Please try again.",
                            HttpStatus.BAD_GATEWAY
                    );
                }
                if (hasBlankOrDuplicateChoices(item.choices())) {
                    throw new AppException(
                            "LLM_INVALID_OUTPUT",
                            "The study pack service returned an invalid quiz format. Please try again.",
                            HttpStatus.BAD_GATEWAY
                    );
                }

                List<String> randomizedChoices = randomizeChoices(item.choices(), item.question());
                String correctAnswer = item.choices().get(item.answerIndex());

                quizItems.add(new QuizItem(
                        item.question(),
                        randomizedChoices,
                        correctAnswer,
                        item.concept(),
                        item.explanation()
                ));
            }

            JsonNode usage = responseJson.path("usage");
            Integer inputTokens = asNullableInt(usage.get("input_tokens"));
            Integer outputTokens = asNullableInt(usage.get("output_tokens"));
            Integer cachedInputTokens = asNullableInt(usage.path("input_tokens_details").get("cached_tokens"));
            String modelUsed = responseJson.path("model").asText(model);

            return new GeneratedStudyPackContent(
                    promptStudyPack.title(),
                    promptStudyPack.summary(),
                    promptStudyPack.keyConcepts(),
                    quizItems,
                    modelUsed,
                    inputTokens,
                    outputTokens,
                    cachedInputTokens,
                    null
            );
        } catch (RestClientResponseException ex) {
            String requestId = MDC.get("requestId");
            String upstreamMessage = extractUpstreamErrorMessage(ex.getResponseBodyAsString());
            log.warn(
                    "openai_request_failed requestId={} status={} errorCode={} upstreamMessage={}",
                    requestId,
                    ex.getStatusCode().value(),
                    ex.getClass().getSimpleName(),
                    upstreamMessage
            );
            throw new AppException(
                    "LLM_REQUEST_FAILED",
                    "Study pack generation failed. Please try again in a moment.",
                    HttpStatus.BAD_GATEWAY
            );
        } catch (RestClientException | IOException ex) {
            String requestId = MDC.get("requestId");
            log.warn(
                    "openai_unavailable requestId={} errorCode={} message={}",
                    requestId,
                    ex.getClass().getSimpleName(),
                    ex.getMessage()
            );
            throw new AppException(
                    "LLM_UNAVAILABLE",
                    "Study pack generation is temporarily unavailable. Please try again.",
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    private ArrayNode buildInputMessages(String normalizedNotesText) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(buildTextMessage("system", promptResources.systemPrompt()));

        QuizMix quizMix = deriveQuizMix(properties.getSettings().getQuizQuestionsFree());
        String developerPrompt = promptResources.developerPromptTemplate()
                .replace("{QUIZ_COUNT}", String.valueOf(properties.getSettings().getQuizQuestionsFree()))
                .replace("{RECALL_COUNT}", String.valueOf(quizMix.recallCount()))
                .replace("{UNDERSTANDING_COUNT}", String.valueOf(quizMix.understandingCount()))
                .replace("{APPLICATION_COUNT}", String.valueOf(quizMix.applicationCount()));
        input.add(buildTextMessage("developer", developerPrompt));
        input.add(buildTextMessage("user", "Study notes:\n" + normalizedNotesText));

        return input;
    }

    private ArrayNode buildQuickReviewStudyTipInputMessages(String incorrectQuestionSummaries) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(buildTextMessage(
                "system",
                "You are Study Snap, a calm and supportive tutor helping users review weak concepts."
        ));
        input.add(buildTextMessage(
                "developer",
                "Generate exactly one concise Study Tip in 1-2 sentences. " +
                        "Focus only on the shared concept from the missed questions and what to review next. " +
                        "Do not use markdown, bullets, labels, or extra commentary."
        ));
        input.add(buildTextMessage(
                "user",
                "Missed Quick Review questions:\n" + incorrectQuestionSummaries
        ));
        return input;
    }

    private ArrayNode buildAdaptivePracticeInputMessages(
            String studyPackTitle,
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> weakConcepts,
            int questionCount
    ) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(buildTextMessage("system", promptResources.adaptivePracticeSystemPrompt()));
        String adaptivePracticeDeveloperPrompt = promptResources.adaptivePracticeDeveloperPromptTemplate()
                .replace("{QUESTION_COUNT}", String.valueOf(questionCount));
        input.add(buildTextMessage("developer", adaptivePracticeDeveloperPrompt));
        input.add(buildTextMessage(
                "user",
                "Study Pack title: " + studyPackTitle + "\n" +
                        "Summary: " + studyPackSummary + "\n" +
                        "Key concepts: " + String.join(", ", keyConcepts) + "\n" +
                        "Weak concepts to target: " + String.join(", ", weakConcepts)
        ));
        return input;
    }

    private JsonNode buildAdaptivePracticeSchema(int questionCount) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.putArray("required").add("quiz");

        ObjectNode properties = root.putObject("properties");
        ObjectNode quiz = properties.putObject("quiz");
        quiz.put("type", "array");
        quiz.put("minItems", questionCount);
        quiz.put("maxItems", questionCount);

        ObjectNode item = quiz.putObject("items");
        item.put("type", "object");
        ArrayNode required = item.putArray("required");
        required.add("question");
        required.add("choices");
        required.add("answerIndex");
        required.add("concept");
        required.add("explanation");

        ObjectNode itemProps = item.putObject("properties");
        itemProps.putObject("question").put("type", "string");
        itemProps.putObject("concept").put("type", "string");
        itemProps.putObject("explanation").put("type", "string");

        ObjectNode choices = itemProps.putObject("choices");
        choices.put("type", "array");
        choices.put("minItems", 4);
        choices.put("maxItems", 4);
        choices.putObject("items").put("type", "string");

        ObjectNode answerIndex = itemProps.putObject("answerIndex");
        answerIndex.put("type", "integer");
        answerIndex.put("minimum", 0);
        answerIndex.put("maximum", 3);

        return root;
    }

    private QuizMix deriveQuizMix(int quizCount) {
        int count = Math.max(1, quizCount);
        if (count == 1) {
            return new QuizMix(1, 0, 0);
        }
        if (count == 2) {
            return new QuizMix(1, 1, 0);
        }

        int recall = Math.max(1, count / 3);
        int application = Math.max(1, count / 3);
        int understanding = count - recall - application;

        if (understanding < 1) {
            if (recall >= application) {
                recall--;
            } else {
                application--;
            }
            understanding = 1;
        }

        return new QuizMix(recall, understanding, application);
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
                "The study pack service returned an unexpected format. Please try again.",
                HttpStatus.BAD_GATEWAY
        );
    }

    private Integer asNullableInt(JsonNode node) {
        return node != null && node.isNumber() ? node.intValue() : null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeForDuplicateCheck(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ").trim();
    }

    private boolean hasBlankOrDuplicateChoices(List<String> choices) {
        Set<String> normalizedChoices = new HashSet<>();
        for (String choice : choices) {
            if (isBlank(choice)) {
                return true;
            }
            if (!normalizedChoices.add(normalizeForDuplicateCheck(choice))) {
                return true;
            }
        }
        return false;
    }

    private List<String> randomizeChoices(List<String> choices, String question) {
        List<String> shuffled = new ArrayList<>(choices);
        long seed = normalizeForDuplicateCheck(question).hashCode();
        Collections.shuffle(shuffled, new Random(seed));
        return shuffled;
    }

    private String extractUpstreamErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "n/a";
        }
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            String message = node.path("error").path("message").asText();
            if (message == null || message.isBlank()) {
                return "n/a";
            }
            return message;
        } catch (IOException ex) {
            return "unparseable_upstream_error";
        }
    }

    private String sanitizeStudyTip(String rawTip) {
        if (rawTip == null) {
            return null;
        }

        String normalized = rawTip
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank()) {
            return null;
        }

        String[] sentences = normalized.split("(?<=[.!?])\\s+");
        if (sentences.length > 2) {
            normalized = String.join(" ", List.of(sentences).subList(0, 2)).trim();
        }
        if (normalized.length() > MAX_STUDY_TIP_LENGTH) {
            normalized = normalized.substring(0, MAX_STUDY_TIP_LENGTH).trim();
        }
        return normalized.isBlank() ? null : normalized;
    }

    @Override
    public String generateQuickReviewStudyTip(List<String> incorrectQuestionSummaries) {
        if (incorrectQuestionSummaries == null || incorrectQuestionSummaries.isEmpty()) {
            return null;
        }
        if (properties.getLlm().getApi().getApiKey() == null || properties.getLlm().getApi().getApiKey().isBlank()) {
            throw new AppException(
                    "LLM_CONFIGURATION_ERROR",
                    "LLM API key is missing. Please configure LLM_API_KEY.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
        String model = properties.getSettings().getModelFree();
        if (model == null || model.isBlank()) {
            throw new AppException(
                    "LLM_CONFIGURATION_ERROR",
                    "LLM model is missing. Please configure LLM_MODEL_FREE.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        String joinedSummaries = incorrectQuestionSummaries.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> "- " + value)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        if (joinedSummaries.isBlank()) {
            return null;
        }

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.set("input", buildQuickReviewStudyTipInputMessages(joinedSummaries));

        try {
            String requestBodyJson = objectMapper.writeValueAsString(requestBody);
            String responseBody = restClient.post()
                    .uri("/responses")
                    .body(requestBodyJson)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw new AppException(
                        "LLM_EMPTY_RESPONSE",
                        "The study tip service returned an empty response. Please try again.",
                        HttpStatus.BAD_GATEWAY
                );
            }

            JsonNode responseJson = objectMapper.readTree(responseBody);
            return sanitizeStudyTip(extractOutputJson(responseJson));
        } catch (RestClientResponseException ex) {
            String requestId = MDC.get("requestId");
            String upstreamMessage = extractUpstreamErrorMessage(ex.getResponseBodyAsString());
            log.warn(
                    "openai_study_tip_request_failed requestId={} status={} errorCode={} upstreamMessage={}",
                    requestId,
                    ex.getStatusCode().value(),
                    ex.getClass().getSimpleName(),
                    upstreamMessage
            );
            throw new AppException(
                    "LLM_REQUEST_FAILED",
                    "Study tip generation failed. Please try again in a moment.",
                    HttpStatus.BAD_GATEWAY
            );
        } catch (RestClientException | IOException ex) {
            String requestId = MDC.get("requestId");
            log.warn(
                    "openai_study_tip_unavailable requestId={} errorCode={} message={}",
                    requestId,
                    ex.getClass().getSimpleName(),
                    ex.getMessage()
            );
            throw new AppException(
                    "LLM_UNAVAILABLE",
                    "Study tip generation is temporarily unavailable. Please try again.",
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    @Override
    public List<QuizItem> generateAdaptivePracticeQuiz(
            String studyPackTitle,
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> weakConcepts,
            int questionCount
    ) {
        if (weakConcepts == null || weakConcepts.isEmpty()) {
            return List.of();
        }
        if (properties.getLlm().getApi().getApiKey() == null || properties.getLlm().getApi().getApiKey().isBlank()) {
            throw new AppException(
                    "LLM_CONFIGURATION_ERROR",
                    "LLM API key is missing. Please configure LLM_API_KEY.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
        String model = properties.getSettings().getModelFree();
        if (model == null || model.isBlank()) {
            throw new AppException(
                    "LLM_CONFIGURATION_ERROR",
                    "LLM model is missing. Please configure LLM_MODEL_FREE.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        int normalizedQuestionCount = Math.max(3, Math.min(5, questionCount));
        List<String> normalizedWeakConcepts = weakConcepts.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        if (normalizedWeakConcepts.isEmpty()) {
            return List.of();
        }
        List<String> normalizedKeyConcepts = keyConcepts == null ? List.of() : keyConcepts.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.set(
                "input",
                buildAdaptivePracticeInputMessages(
                        studyPackTitle == null ? "" : studyPackTitle,
                        studyPackSummary == null ? "" : studyPackSummary,
                        normalizedKeyConcepts,
                        normalizedWeakConcepts,
                        normalizedQuestionCount
                )
        );
        ObjectNode textNode = requestBody.putObject("text");
        ObjectNode formatNode = textNode.putObject("format");
        formatNode.put("type", "json_schema");
        formatNode.put("name", "study_snap_adaptive_quiz");
        formatNode.set("schema", buildAdaptivePracticeSchema(normalizedQuestionCount));
        formatNode.put("strict", true);

        try {
            String requestBodyJson = objectMapper.writeValueAsString(requestBody);
            String responseBody = restClient.post()
                    .uri("/responses")
                    .body(requestBodyJson)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw new AppException(
                        "LLM_EMPTY_RESPONSE",
                        "Adaptive quiz generation returned an empty response. Please try again.",
                        HttpStatus.BAD_GATEWAY
                );
            }

            JsonNode responseJson = objectMapper.readTree(responseBody);
            String outputJson = extractOutputJson(responseJson);
            PromptAdaptiveQuiz promptAdaptiveQuiz = objectMapper.readValue(outputJson, PromptAdaptiveQuiz.class);

            if (promptAdaptiveQuiz.quiz() == null || promptAdaptiveQuiz.quiz().size() != normalizedQuestionCount) {
                throw new AppException(
                        "LLM_INVALID_OUTPUT",
                        "Adaptive quiz generation returned an invalid format. Please try again.",
                        HttpStatus.BAD_GATEWAY
                );
            }

            List<QuizItem> quizItems = new ArrayList<>();
            Set<String> normalizedQuestions = new HashSet<>();
            for (PromptQuizItem item : promptAdaptiveQuiz.quiz()) {
                if (item.choices() == null || item.choices().size() != 4) {
                    throw new AppException(
                            "LLM_INVALID_OUTPUT",
                            "Adaptive quiz generation returned an invalid format. Please try again.",
                            HttpStatus.BAD_GATEWAY
                    );
                }
                if (item.answerIndex() < 0 || item.answerIndex() >= item.choices().size()) {
                    throw new AppException(
                            "LLM_INVALID_OUTPUT",
                            "Adaptive quiz generation returned an invalid answer. Please try again.",
                            HttpStatus.BAD_GATEWAY
                    );
                }
                if (isBlank(item.question()) || isBlank(item.explanation()) || isBlank(item.concept())) {
                    throw new AppException(
                            "LLM_INVALID_OUTPUT",
                            "Adaptive quiz generation returned an invalid format. Please try again.",
                            HttpStatus.BAD_GATEWAY
                    );
                }
                if (!normalizedQuestions.add(normalizeForDuplicateCheck(item.question()))) {
                    throw new AppException(
                            "LLM_INVALID_OUTPUT",
                            "Adaptive quiz generation returned repetitive questions. Please try again.",
                            HttpStatus.BAD_GATEWAY
                    );
                }
                if (hasBlankOrDuplicateChoices(item.choices())) {
                    throw new AppException(
                            "LLM_INVALID_OUTPUT",
                            "Adaptive quiz generation returned invalid choices. Please try again.",
                            HttpStatus.BAD_GATEWAY
                    );
                }

                List<String> randomizedChoices = randomizeChoices(item.choices(), item.question());
                String correctAnswer = item.choices().get(item.answerIndex());
                quizItems.add(new QuizItem(
                        item.question(),
                        randomizedChoices,
                        correctAnswer,
                        item.concept(),
                        item.explanation()
                ));
            }

            return quizItems;
        } catch (RestClientResponseException ex) {
            String requestId = MDC.get("requestId");
            String upstreamMessage = extractUpstreamErrorMessage(ex.getResponseBodyAsString());
            log.warn(
                    "openai_adaptive_quiz_request_failed requestId={} status={} errorCode={} upstreamMessage={}",
                    requestId,
                    ex.getStatusCode().value(),
                    ex.getClass().getSimpleName(),
                    upstreamMessage
            );
            throw new AppException(
                    "LLM_REQUEST_FAILED",
                    "Adaptive quiz generation failed. Please try again in a moment.",
                    HttpStatus.BAD_GATEWAY
            );
        } catch (RestClientException | IOException ex) {
            String requestId = MDC.get("requestId");
            log.warn(
                    "openai_adaptive_quiz_unavailable requestId={} errorCode={} message={}",
                    requestId,
                    ex.getClass().getSimpleName(),
                    ex.getMessage()
            );
            throw new AppException(
                    "LLM_UNAVAILABLE",
                    "Adaptive quiz generation is temporarily unavailable. Please try again.",
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    private record PromptStudyPack(
            String title,
            String summary,
            List<String> keyConcepts,
            List<PromptQuizItem> quiz
    ) {
        PromptStudyPack {
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
            String concept,
            String explanation
    ) {
    }

    private record PromptAdaptiveQuiz(
            List<PromptQuizItem> quiz
    ) {
    }

    private record QuizMix(int recallCount, int understandingCount, int applicationCount) {
    }
}


