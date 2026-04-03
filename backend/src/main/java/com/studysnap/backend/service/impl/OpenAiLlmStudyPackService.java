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
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.LlmResponseUtils;
import com.studysnap.backend.util.QuizValidationUtils;
import com.studysnap.backend.util.StringNormalizationUtils;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@ConditionalOnProperty(prefix = "studysnap.llm.api", name = "provider", havingValue = "openai", matchIfMissing = true)
@RequiredArgsConstructor
public class OpenAiLlmStudyPackService implements LlmStudyPackService {
    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmStudyPackService.class);
    private static final int STUDY_PACK_QUIZ_QUESTION_COUNT = 5;
    private static final int MAX_SUMMARY_WORDS = 120;
    private static final int MAX_STUDY_TIP_WORDS = 20;

    private final StudySnapProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final OpenAiPromptResources promptResources;

    @Override
    public GeneratedStudyPackContent generateStudyPack(String normalizedNotesText, StudyPackGenerationContext context) {
        Objects.requireNonNull(context, "context");
        String model = requireConfiguredModel();
        JsonSchemaResponse<PromptStudyPack> response = executeJsonSchemaOperation(
                model,
                buildInputMessages(normalizedNotesText),
                studyPackOperation(),
                promptResources.responseSchema(),
                PromptStudyPack.class
        );
        return toGeneratedStudyPackContent(response.payload(), response.responseJson(), model);
    }

    private ArrayNode buildInputMessages(String normalizedNotesText) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(buildTextMessage("system", promptResources.systemPrompt()));

        String developerPrompt = promptResources.developerPromptTemplate()
                .replace("{QUIZ_COUNT}", String.valueOf(STUDY_PACK_QUIZ_QUESTION_COUNT));
        input.add(buildTextMessage("developer", developerPrompt));
        input.add(buildTextMessage("user", "Study notes:\n" + normalizedNotesText));

        return input;
    }

    private String requireConfiguredModel() {
        if (properties.getLlm().getApi().getApiKey() == null || properties.getLlm().getApi().getApiKey().isBlank()) {
            throw configurationError("LLM API key is missing. Please configure LLM_API_KEY.");
        }
        String model = properties.getSettings().getModelFree();
        if (model == null || model.isBlank()) {
            throw configurationError("LLM model is missing. Please configure LLM_MODEL_FREE.");
        }
        return model;
    }

    private ObjectNode buildJsonSchemaRequest(String model, ArrayNode inputMessages, String schemaName, JsonNode schema) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.set("input", inputMessages);
        ObjectNode textNode = requestBody.putObject("text");
        ObjectNode formatNode = textNode.putObject("format");
        formatNode.put("type", "json_schema");
        formatNode.put("name", schemaName);
        formatNode.set("schema", schema);
        formatNode.put("strict", true);
        return requestBody;
    }

    private <T> JsonSchemaResponse<T> executeJsonSchemaOperation(
            String model,
            ArrayNode inputMessages,
            JsonSchemaOperation operation,
            JsonNode schema,
            Class<T> payloadType
    ) {
        JsonNode responseJson = executeJsonSchemaRequest(
                buildJsonSchemaRequest(model, inputMessages, operation.schemaName(), schema),
                operation
        );
        T payload = parseOutputPayload(responseJson, payloadType, operation.invalidFormatMessage());
        return new JsonSchemaResponse<>(payload, responseJson);
    }

    private JsonNode executeJsonSchemaRequest(
            ObjectNode requestBody,
            JsonSchemaOperation operation
    ) {
        try {
            String requestBodyJson = objectMapper.writeValueAsString(requestBody);
            String responseBody = restClient.post()
                    .uri("/responses")
                    .body(requestBodyJson)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw emptyResponse(operation.emptyResponseMessage());
            }

            return objectMapper.readTree(responseBody);
        } catch (RestClientResponseException ex) {
            String requestId = MDC.get("requestId");
            String upstreamMessage = LlmResponseUtils.extractUpstreamErrorMessage(ex.getResponseBodyAsString(), objectMapper);
            log.warn(
                    "{} requestId={} status={} errorCode={} upstreamMessage={}",
                    operation.requestFailedLogKey(),
                    requestId,
                    ex.getStatusCode().value(),
                    ex.getClass().getSimpleName(),
                    upstreamMessage
            );
            throw requestFailed(operation.requestFailedMessage());
        } catch (IOException ex) {
            throw invalidOutput(operation.invalidFormatMessage());
        } catch (RestClientException ex) {
            String requestId = MDC.get("requestId");
            log.warn(
                    "{} requestId={} errorCode={} message={}",
                    operation.unavailableLogKey(),
                    requestId,
                    ex.getClass().getSimpleName(),
                    ex.getMessage()
            );
            throw unavailable(operation.unavailableMessage());
        }
    }

    private <T> T parseOutputPayload(JsonNode responseJson, Class<T> payloadType, String invalidFormatMessage) {
        try {
            String outputJson = LlmResponseUtils.findOutputJson(responseJson)
                    .orElseThrow(() -> invalidOutput(invalidFormatMessage));
            return objectMapper.readValue(outputJson, payloadType);
        } catch (IOException ex) {
            throw invalidOutput(invalidFormatMessage);
        }
    }

    private GeneratedStudyPackContent toGeneratedStudyPackContent(
            PromptStudyPack promptStudyPack,
            JsonNode responseJson,
            String fallbackModel
    ) {
        validatePromptStudyPack(promptStudyPack);
        String normalizedSubject = normalizeAndValidateSubject(promptStudyPack.subject());
        List<String> normalizedKeyConcepts = normalizeAndValidateKeyConcepts(promptStudyPack.keyConcepts());
        List<String> normalizedTags = normalizeAndValidateTags(promptStudyPack.tags(), promptStudyPack.title());
        List<QuizItem> quizItems = buildStudyPackQuizItems(promptStudyPack.quiz());

        UsageMetadata usageMetadata = extractUsageMetadata(responseJson, fallbackModel);
        return new GeneratedStudyPackContent(
                promptStudyPack.title(),
                promptStudyPack.summary(),
                normalizedSubject,
                normalizedTags,
                normalizedKeyConcepts,
                quizItems,
                usageMetadata.modelUsed(),
                usageMetadata.inputTokens(),
                usageMetadata.outputTokens(),
                usageMetadata.cachedInputTokens(),
                null
        );
    }

    private void validatePromptStudyPack(PromptStudyPack promptStudyPack) {
        if (promptStudyPack.quiz().size() != STUDY_PACK_QUIZ_QUESTION_COUNT) {
            throw invalidOutput("The study pack service returned an invalid quiz format. Please try again.");
        }
        if (StringNormalizationUtils.countWords(promptStudyPack.summary()) > MAX_SUMMARY_WORDS) {
            throw invalidOutput("The study pack service returned an invalid summary format. Please try again.");
        }
    }

    private List<QuizItem> buildStudyPackQuizItems(List<PromptQuizItem> promptQuizItems) {
        List<QuizItem> quizItems = new ArrayList<>();
        Set<String> normalizedQuestions = new HashSet<>();
        Set<String> normalizedConcepts = new HashSet<>();
        for (PromptQuizItem item : promptQuizItems) {
            validatePromptQuizItem(item, normalizedQuestions, normalizedConcepts);
            String normalizedConcept = normalizeAndValidateConcept(item.concept());
            String normalizedQuestionKey = StringNormalizationUtils.normalizeForDuplicateCheck(item.question());
            if (!normalizedQuestions.add(normalizedQuestionKey)) {
                throw invalidOutput("The study pack service returned repetitive quiz questions. Please try again.");
            }
            String normalizedConceptKey = StringNormalizationUtils.normalizeForDuplicateCheck(normalizedConcept);
            if (!normalizedConcepts.add(normalizedConceptKey)) {
                throw invalidOutput("The study pack service returned repetitive quiz concepts. Please try again.");
            }

            List<String> randomizedChoices = QuizValidationUtils.randomizeChoices(item.choices(), item.question());
            String correctAnswer = item.choices().get(item.answerIndex());
            quizItems.add(new QuizItem(
                    item.question(),
                    randomizedChoices,
                    correctAnswer,
                    normalizedConcept,
                    QuizValidationUtils.buildFallbackExplanation(normalizedConcept)
            ));
        }
        return quizItems;
    }

    private void validatePromptQuizItem(
            PromptQuizItem item,
            Set<String> normalizedQuestions,
            Set<String> normalizedConcepts
    ) {
        if (item.choices() == null || item.choices().size() != 4) {
            throw invalidOutput("The study pack service returned an invalid quiz format. Please try again.");
        }
        if (item.answerIndex() < 0 || item.answerIndex() >= item.choices().size()) {
            throw invalidOutput("The study pack service returned an invalid quiz answer. Please try again.");
        }
        if (StringNormalizationUtils.isBlank(item.question())) {
            throw invalidOutput("The study pack service returned an invalid quiz format. Please try again.");
        }
        normalizeAndValidateConcept(item.concept());
        if (QuizValidationUtils.hasBlankOrDuplicateChoices(item.choices())) {
            throw invalidOutput("The study pack service returned an invalid quiz format. Please try again.");
        }
        String normalizedQuestionKey = StringNormalizationUtils.normalizeForDuplicateCheck(item.question());
        if (normalizedQuestions.contains(normalizedQuestionKey)) {
            throw invalidOutput("The study pack service returned repetitive quiz questions. Please try again.");
        }
        String normalizedConceptKey = StringNormalizationUtils.normalizeForDuplicateCheck(
                StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(item.concept())
        );
        if (normalizedConcepts.contains(normalizedConceptKey)) {
            throw invalidOutput("The study pack service returned repetitive quiz concepts. Please try again.");
        }
    }

    private UsageMetadata extractUsageMetadata(JsonNode responseJson, String fallbackModel) {
        JsonNode usage = responseJson.path("usage");
        return new UsageMetadata(
                responseJson.path("model").asText(fallbackModel),
                LlmResponseUtils.asNullableInt(usage.get("input_tokens")),
                LlmResponseUtils.asNullableInt(usage.get("output_tokens")),
                LlmResponseUtils.asNullableInt(usage.path("input_tokens_details").get("cached_tokens"))
        );
    }

    private ArrayNode buildQuickReviewStudyTipInputMessages(String incorrectQuestionSummaries) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(buildTextMessage(
                "system",
                "You are NoteLib, a calm and supportive tutor helping users review weak concepts."
        ));
        input.add(buildTextMessage(
                "developer",
                "Return JSON only with this structure: {\"tip\": string}. " +
                        "Generate exactly one sentence under 20 words. " +
                        "Focus only on what concept to review next. " +
                        "No markdown, no bullets, no extra keys."
        ));
        input.add(buildTextMessage(
                "user",
                "Missed Quick Review questions:\n" + incorrectQuestionSummaries
        ));
        return input;
    }

    private ArrayNode buildAdaptivePracticeInputMessages(
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> weakConcepts,
            List<String> disallowedQuestions,
            int questionCount
    ) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(buildTextMessage("system", promptResources.adaptivePracticeSystemPrompt()));
        String adaptivePracticeDeveloperPrompt = promptResources.adaptivePracticeDeveloperPromptTemplate()
                .replace("{QUESTION_COUNT}", String.valueOf(questionCount));
        input.add(buildTextMessage("developer", adaptivePracticeDeveloperPrompt));
        input.add(buildTextMessage(
                "user",
                "Summary: " + studyPackSummary + "\n" +
                        "Key concepts: " + String.join(", ", keyConcepts) + "\n" +
                        "Weak concepts to target: " + String.join(", ", weakConcepts) + "\n" +
                        "Excluded questions (must not be repeated): " + String.join(" || ", disallowedQuestions)
        ));
        return input;
    }

    private ArrayNode buildChallengeQuizInputMessages(
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> disallowedQuestions,
            int questionCount,
            String difficulty
    ) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(buildTextMessage("system", promptResources.challengeQuizSystemPrompt()));
        String challengeQuizDeveloperPrompt = promptResources.challengeQuizDeveloperPromptTemplate()
                .replace("{QUESTION_COUNT}", String.valueOf(questionCount))
                .replace("{DIFFICULTY}", difficulty);
        input.add(buildTextMessage("developer", challengeQuizDeveloperPrompt));
        input.add(buildTextMessage(
                "user",
                "Summary: " + studyPackSummary + "\n" +
                        "Key concepts: " + String.join(", ", keyConcepts) + "\n" +
                        "Excluded questions (must not be repeated): " + String.join(" || ", disallowedQuestions)
        ));
        return input;
    }

    private JsonNode buildGeneratedQuizSchema(int questionCount) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        root.putArray("required").add("questions");

        ObjectNode properties = root.putObject("properties");
        ObjectNode quiz = properties.putObject("questions");
        quiz.put("type", "array");
        quiz.put("minItems", questionCount);
        quiz.put("maxItems", questionCount);

        ObjectNode item = quiz.putObject("items");
        item.put("type", "object");
        item.put("additionalProperties", false);
        ArrayNode required = item.putArray("required");
        required.add("question");
        required.add("choices");
        required.add("answer");

        ObjectNode itemProps = item.putObject("properties");
        itemProps.putObject("question").put("type", "string");
        itemProps.putObject("answer").put("type", "string");

        ObjectNode choices = itemProps.putObject("choices");
        choices.put("type", "array");
        choices.put("minItems", 4);
        choices.put("maxItems", 4);
        choices.putObject("items").put("type", "string");

        return root;
    }

    private JsonNode buildStudyTipSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        root.putArray("required").add("tip");
        root.putObject("properties")
                .putObject("tip")
                .put("type", "string")
                .put("minLength", 1)
                .put("maxLength", 180);
        return root;
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

    private String normalizeAndValidateSubject(String subject) {
        String normalized = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(subject);
        if (!StringNormalizationUtils.containsAlphaNumeric(normalized) || !StringNormalizationUtils.hasWordCountBetween(normalized, 1, 4)) {
            throw invalidOutput("The study pack service returned invalid subject metadata. Please try again.");
        }
        return normalized;
    }

    private List<String> normalizeAndValidateTags(List<String> tags, String title) {
        if (tags == null || tags.size() < 3 || tags.size() > 6) {
            throw invalidOutput("The study pack service returned invalid tag metadata. Please try again.");
        }

        String normalizedTitle = StringNormalizationUtils.normalizeForDuplicateCheck(title);
        Set<String> normalizedSeenTags = new HashSet<>();
        List<String> normalizedTags = new ArrayList<>();
        for (String tag : tags) {
            String normalizedTag = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(tag);
            if (!StringNormalizationUtils.containsAlphaNumeric(normalizedTag)
                    || !StringNormalizationUtils.hasWordCountBetween(normalizedTag, 1, 3)) {
                throw invalidOutput("The study pack service returned invalid tag metadata. Please try again.");
            }

            String normalizedTagForComparison = StringNormalizationUtils.normalizeForDuplicateCheck(normalizedTag);
            if (normalizedTagForComparison.isBlank()
                    || normalizedTagForComparison.equals(normalizedTitle)
                    || !normalizedSeenTags.add(normalizedTagForComparison)) {
                throw invalidOutput("The study pack service returned invalid tag metadata. Please try again.");
            }
            normalizedTags.add(normalizedTag);
        }

        return normalizedTags;
    }

    private List<String> normalizeAndValidateKeyConcepts(List<String> keyConcepts) {
        if (keyConcepts == null || keyConcepts.size() < 8 || keyConcepts.size() > 10) {
            throw invalidOutput("The study pack service returned invalid key concepts. Please try again.");
        }

        Set<String> normalizedSeen = new HashSet<>();
        List<String> normalizedConcepts = new ArrayList<>();
        for (String keyConcept : keyConcepts) {
            String normalized = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(keyConcept);
            if (!StringNormalizationUtils.containsAlphaNumeric(normalized)) {
                throw invalidOutput("The study pack service returned invalid key concepts. Please try again.");
            }

            String duplicateKey = StringNormalizationUtils.normalizeForDuplicateCheck(normalized);
            if (duplicateKey.isBlank() || !normalizedSeen.add(duplicateKey)) {
                throw invalidOutput("The study pack service returned repetitive key concepts. Please try again.");
            }
            normalizedConcepts.add(normalized);
        }

        return normalizedConcepts;
    }

    private String normalizeAndValidateConcept(String concept) {
        String normalized = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(concept);
        if (normalized == null || !StringNormalizationUtils.hasWordCountBetween(normalized, 1, 4)) {
            throw invalidOutput("The study pack service returned an invalid quiz concept. Please try again.");
        }
        return normalized;
    }

    @Override
    public String generateQuickReviewStudyTip(List<String> incorrectQuestionSummaries) {
        if (incorrectQuestionSummaries == null || incorrectQuestionSummaries.isEmpty()) {
            return null;
        }
        String model = requireConfiguredModel();

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

        JsonSchemaResponse<PromptStudyTip> response = executeJsonSchemaOperation(
                model,
                buildQuickReviewStudyTipInputMessages(joinedSummaries),
                studyTipOperation(),
                buildStudyTipSchema(),
                PromptStudyTip.class
        );
        return LlmResponseUtils.sanitizeStudyTip(response.payload().tip(), MAX_STUDY_TIP_WORDS);
    }

    @Override
    public List<QuizItem> generateAdaptivePracticeQuiz(
            String studyPackTitle,
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> weakConcepts,
            List<String> disallowedQuestions,
            int questionCount
    ) {
        List<String> normalizedWeakConcepts = sanitizeConceptList(weakConcepts);
        if (normalizedWeakConcepts.isEmpty()) {
            return List.of();
        }
        List<String> normalizedDisallowedQuestions = sanitizeQuestionList(disallowedQuestions);
        List<String> normalizedKeyConcepts = sanitizeConceptList(keyConcepts);
        return generateQuizWithSchema(
                buildAdaptivePracticeInputMessages(
                        studyPackSummary == null ? "" : studyPackSummary,
                        normalizedKeyConcepts,
                        normalizedWeakConcepts,
                        normalizedDisallowedQuestions,
                        questionCount
                ),
                questionCount,
                "note_lib_adaptive_quiz",
                "Adaptive practice quiz generation",
                normalizedWeakConcepts
        );
    }

    @Override
    public List<QuizItem> generateChallengeQuiz(
            String studyPackTitle,
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> disallowedQuestions,
            int questionCount,
            String difficulty
    ) {
        List<String> normalizedKeyConcepts = sanitizeConceptList(keyConcepts);
        List<String> normalizedDisallowedQuestions = sanitizeQuestionList(disallowedQuestions);
        return generateQuizWithSchema(
                buildChallengeQuizInputMessages(
                        studyPackSummary == null ? "" : studyPackSummary,
                        normalizedKeyConcepts,
                        normalizedDisallowedQuestions,
                        questionCount,
                        difficulty == null || difficulty.isBlank() ? "medium" : difficulty
                ),
                questionCount,
                "note_lib_challenge_quiz",
                "Challenge quiz generation",
                normalizedKeyConcepts
        );
    }

    private List<QuizItem> generateQuizWithSchema(
            ArrayNode inputMessages,
            int questionCount,
            String schemaName,
            String operationLabel,
            List<String> conceptFallbackPool
    ) {
        String model = requireConfiguredModel();
        JsonSchemaResponse<PromptGeneratedQuiz> response = executeJsonSchemaOperation(
                model,
                inputMessages,
                quizOperation(schemaName, operationLabel),
                buildGeneratedQuizSchema(questionCount),
                PromptGeneratedQuiz.class
        );
        PromptGeneratedQuiz promptGeneratedQuiz = response.payload();

        if (promptGeneratedQuiz.questions() == null || promptGeneratedQuiz.questions().size() != questionCount) {
            throw invalidOutput(operationLabel + " returned an invalid format. Please try again.");
        }

        List<QuizItem> quizItems = new ArrayList<>();
        int conceptIndex = 0;
        for (PromptGeneratedQuizItem item : promptGeneratedQuiz.questions()) {
            validateGeneratedQuizItem(item, operationLabel);
            String answer = item.answer().trim();
            String conceptFallback = conceptFallbackPool.isEmpty()
                    ? null
                    : conceptFallbackPool.get(conceptIndex % conceptFallbackPool.size());
            conceptIndex += 1;
            quizItems.add(new QuizItem(
                    item.question().trim(),
                    item.choices().stream().map(String::trim).toList(),
                    answer,
                    conceptFallback,
                    QuizValidationUtils.buildFallbackExplanation(conceptFallback)
            ));
        }
        return quizItems;
    }

    private void validateGeneratedQuizItem(PromptGeneratedQuizItem item, String operationLabel) {
        if (item.choices() == null || item.choices().size() != 4) {
            throw invalidOutput(operationLabel + " returned invalid choices. Please try again.");
        }
        if (StringNormalizationUtils.isBlank(item.question()) || StringNormalizationUtils.isBlank(item.answer())) {
            throw invalidOutput(operationLabel + " returned an invalid question. Please try again.");
        }
        if (QuizValidationUtils.hasBlankOrDuplicateChoices(item.choices())) {
            throw invalidOutput(operationLabel + " returned invalid choices. Please try again.");
        }

        String answer = item.answer().trim();
        boolean answerInChoices = item.choices().stream().anyMatch(choice -> choice.trim().equals(answer));
        if (!answerInChoices) {
            throw invalidOutput(operationLabel + " returned an invalid answer mapping. Please try again.");
        }
    }

    private List<String> sanitizeConceptList(List<String> values) {
        return sanitizeStringList(values);
    }

    private List<String> sanitizeQuestionList(List<String> values) {
        return sanitizeStringList(values);
    }

    private List<String> sanitizeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private JsonSchemaOperation studyPackOperation() {
        return new JsonSchemaOperation(
                "note_lib_study_pack",
                "openai_request_failed",
                "openai_unavailable",
                "The study pack service returned an empty response. Please try again.",
                "The study pack service returned an unexpected format. Please try again.",
                "Study pack generation failed. Please try again in a moment.",
                "Study pack generation is temporarily unavailable. Please try again."
        );
    }

    private JsonSchemaOperation studyTipOperation() {
        return new JsonSchemaOperation(
                "note_lib_study_tip",
                "openai_study_tip_request_failed",
                "openai_study_tip_unavailable",
                "The study tip service returned an empty response. Please try again.",
                "The study tip service returned an unexpected format. Please try again.",
                "Study tip generation failed. Please try again in a moment.",
                "Study tip generation is temporarily unavailable. Please try again."
        );
    }

    private JsonSchemaOperation quizOperation(String schemaName, String operationLabel) {
        return new JsonSchemaOperation(
                schemaName,
                "openai_quiz_request_failed",
                "openai_quiz_unavailable",
                operationLabel + " returned an empty response. Please try again.",
                operationLabel + " returned an unexpected format. Please try again.",
                operationLabel + " failed. Please try again in a moment.",
                operationLabel + " is temporarily unavailable. Please try again."
        );
    }

    private AppException configurationError(String message) {
        return new AppException("LLM_CONFIGURATION_ERROR", message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private AppException emptyResponse(String message) {
        return new AppException("LLM_EMPTY_RESPONSE", message, HttpStatus.BAD_GATEWAY);
    }

    private AppException invalidOutput(String message) {
        return new AppException("LLM_INVALID_OUTPUT", message, HttpStatus.BAD_GATEWAY);
    }

    private AppException requestFailed(String message) {
        return new AppException("LLM_REQUEST_FAILED", message, HttpStatus.BAD_GATEWAY);
    }

    private AppException unavailable(String message) {
        return new AppException("LLM_UNAVAILABLE", message, HttpStatus.BAD_GATEWAY);
    }

    private record PromptStudyPack(
            String title,
            String summary,
            String subject,
            List<String> tags,
            List<String> keyConcepts,
            List<PromptQuizItem> quiz
    ) {
        PromptStudyPack {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(summary, "summary");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(tags, "tags");
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

    private record PromptGeneratedQuiz(
            List<PromptGeneratedQuizItem> questions
    ) {
    }

    private record PromptGeneratedQuizItem(
            String question,
            List<String> choices,
            String answer
    ) {
    }

    private record PromptStudyTip(String tip) {
    }

    private record JsonSchemaOperation(
            String schemaName,
            String requestFailedLogKey,
            String unavailableLogKey,
            String emptyResponseMessage,
            String invalidFormatMessage,
            String requestFailedMessage,
            String unavailableMessage
    ) {
    }

    private record JsonSchemaResponse<T>(
            T payload,
            JsonNode responseJson
    ) {
    }

    private record UsageMetadata(
            String modelUsed,
            Integer inputTokens,
            Integer outputTokens,
            Integer cachedInputTokens
    ) {
    }
}
