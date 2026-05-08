package com.studysnap.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.config.OpenAiPromptResources;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.service.LlmStudyPackService;
import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.KeyConceptSanitizer;
import com.studysnap.backend.util.LlmResponseUtils;
import com.studysnap.backend.util.QuizValidationUtils;
import com.studysnap.backend.util.StringNormalizationUtils;
import com.studysnap.backend.util.SubjectNormalizationUtils;
import com.studysnap.backend.util.SubjectSanitizer;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

@Service
@ConditionalOnProperty(prefix = "studysnap.llm.api", name = "provider", havingValue = "openai", matchIfMissing = true)
@RequiredArgsConstructor
public class OpenAiLlmStudyPackService implements LlmStudyPackService {
    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmStudyPackService.class);
    private static final int STUDY_PACK_QUIZ_QUESTION_COUNT = 5;
    private static final int MAX_SUMMARY_WORDS = 120;
    private static final int MAX_STUDY_TIP_WORDS = 20;
    private static final int MAX_GENERATED_NOTE_WORDS = 700;
    private static final int MAX_GENERATED_NOTE_TITLE_WORDS = 12;
    private static final int MAX_GENERATED_NOTE_OVERVIEW_WORDS = 90;
    private static final int MAX_GENERATED_NOTE_KEY_IDEA_WORDS = 40;
    private static final int MAX_GENERATED_NOTE_ITEM_WORDS = 28;
    private static final int MAX_INVALID_OUTPUT_ATTEMPTS = 2;
    private static final LearnerLevel DEFAULT_LEARNER_LEVEL = LearnerLevel.COLLEGE;
    private static final String INVALID_OUTPUT_CODE = "LLM_INVALID_OUTPUT";
    private static final int MAX_LOG_VALUE_LENGTH = 80;
    private static final List<String> DISALLOWED_NOTE_GENERATION_PHRASES = List.of(
            "important study topic",
            "important topic",
            "use this as a starting point",
            "starting point for",
            "this topic is",
            "this note is",
            "you can use this",
            "remember to",
            "help you understand"
    );
    private static final List<String> QUANTITATIVE_KEYWORDS = List.of(
            "accounting", "algebra", "algorithm", "algorithms", "amortization", "analysis", "anatomy",
            "balance", "calculus", "cash flow", "chemistry", "circuit", "circuits", "computation",
            "compute", "current", "derivative", "derivatives", "differential", "electric", "electrical",
            "engineering", "equation", "equations", "finance", "formula", "formulas", "geometry",
            "interest", "integral", "kinematics", "laws of motion", "math", "mathematics", "mechanics",
            "numerical", "ohm", "physics", "probability", "ratio", "resistance", "solve", "statistics",
            "stoichiometry", "thermodynamics", "unit conversion", "units", "variance", "voltage"
    );

    private final StudySnapProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final OpenAiPromptResources promptResources;

    @Override
    public GeneratedStudyPackContent generateStudyPack(String normalizedNotesText, StudyPackGenerationContext context) {
        Objects.requireNonNull(context, "context");
        return retryOnceOnInvalidOutput(() -> generateStudyPackOnce(normalizedNotesText, context));
    }

    @Override
    public String generateNoteFromTopic(String topic, StudyPackGenerationContext context) {
        return retryOnceOnInvalidOutput(() -> generateNoteFromTopicOnce(topic, context));
    }

    private String generateNoteFromTopicOnce(String topic, StudyPackGenerationContext context) {
        String normalizedTopic = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(topic);
        if (normalizedTopic == null) {
            throw invalidOutput("The note generation service returned invalid content. Please try again.");
        }
        String model = requireConfiguredModel();
        JsonSchemaResponse<PromptGeneratedNote> response = executeJsonSchemaOperation(
                model,
                buildGenerateNoteInputMessages(normalizedTopic, context),
                noteGenerationOperation(),
                buildGeneratedNoteSchema(),
                PromptGeneratedNote.class
        );
        String generatedContent = buildGeneratedNoteContent(response.payload(), normalizedTopic);
        String normalizedGeneratedContent = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(generatedContent);
        if (!StringNormalizationUtils.containsAlphaNumeric(normalizedGeneratedContent)) {
            throw invalidOutput("The note generation service returned invalid content. Please try again.");
        }
        return generatedContent;
    }

    private GeneratedStudyPackContent generateStudyPackOnce(
            String normalizedNotesText,
            StudyPackGenerationContext context
    ) {
        String model = requireConfiguredModel();
        JsonSchemaResponse<PromptStudyPack> response = executeJsonSchemaOperation(
                model,
                buildInputMessages(normalizedNotesText, context),
                studyPackOperation(),
                promptResources.responseSchema(),
                PromptStudyPack.class
        );
        return toGeneratedStudyPackContent(response.payload(), response.responseJson(), model, context);
    }

    private ArrayNode buildInputMessages(String normalizedNotesText, StudyPackGenerationContext context) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(buildTextMessage("system", promptResources.systemPrompt()));

        String developerPrompt = buildStudyPackDeveloperPrompt(context);
        input.add(buildTextMessage("developer", developerPrompt));
        input.add(buildTextMessage(
                "user",
                buildStudyPackUserPrompt(normalizedNotesText, context)
        ));

        return input;
    }

    private String buildStudyPackDeveloperPrompt(StudyPackGenerationContext context) {
        return promptResources.developerPromptTemplate()
                .replace("{QUIZ_COUNT}", String.valueOf(STUDY_PACK_QUIZ_QUESTION_COUNT))
                .replace("{LEARNER_LEVEL}", toLearnerLevelLabel(resolveLearnerLevel(context)))
                .replace("{LEARNER_LEVEL_GUIDANCE}", buildLearnerLevelGuidance(resolveLearnerLevel(context), QuizMode.QUICK_REVIEW))
                .replace("{COMPUTATION_GUIDANCE}", buildComputationGuidance(isQuantitativeContext(context, List.of(), null), QuizMode.QUICK_REVIEW))
                .replace("{TIME_EXPECTATION}", buildTimeExpectation(QuizMode.QUICK_REVIEW));
    }

    private String buildStudyPackUserPrompt(String normalizedNotesText, StudyPackGenerationContext context) {
        return buildLearnerContextBlock(context)
                + "\n"
                + buildSubjectSuggestionGuidanceBlock(context)
                + "\nStudy notes:\n"
                + normalizedNotesText;
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
            String fallbackModel,
            StudyPackGenerationContext context
    ) {
        validatePromptStudyPack(promptStudyPack);
        String normalizedSubject = normalizeAndValidateSubject(promptStudyPack.subject(), context);
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
        for (int i = 0; i < promptQuizItems.size(); i++) {
            PromptQuizItem item = promptQuizItems.get(i);
            validatePromptQuizItem(item, normalizedQuestions, normalizedConcepts, i);
            String normalizedConcept = normalizeAndValidateConcept(item.concept(), i);
            String normalizedQuestionKey = StringNormalizationUtils.normalizeForDuplicateCheck(item.question());
            if (!normalizedQuestions.add(normalizedQuestionKey)) {
                throw invalidOutput("The study pack service returned repetitive quiz questions. Please try again.");
            }
            String normalizedConceptKey = StringNormalizationUtils.normalizeForDuplicateCheck(normalizedConcept);
            if (!normalizedConcepts.add(normalizedConceptKey)) {
                throw invalidOutput("The study pack service returned repetitive quiz concepts. Please try again.");
            }

            int answerIndex = resolveAnswerIndex(item.answer(), item.choices().size(), "The study pack service returned an invalid quiz answer. Please try again.");
            quizItems.add(new QuizItem(
                    item.question(),
                    item.choices(),
                    answerIndex,
                    normalizedConcept,
                    normalizeAndValidateExplanation(item.explanation(), "The study pack service returned an invalid quiz explanation. Please try again.")
            ));
        }
        return quizItems;
    }

    private void validatePromptQuizItem(
            PromptQuizItem item,
            Set<String> normalizedQuestions,
            Set<String> normalizedConcepts,
            int quizIndex
    ) {
        if (StringNormalizationUtils.isBlank(item.question())) {
            throw invalidOutput("The study pack service returned an invalid quiz format. Please try again.");
        }
        normalizeAndValidateConcept(item.concept(), quizIndex);
        normalizeAndValidateExplanation(item.explanation(), "The study pack service returned an invalid quiz explanation. Please try again.");
        if (QuizValidationUtils.hasInvalidChoices(item.choices())) {
            throw invalidOutput("The study pack service returned an invalid quiz format. Please try again.");
        }
        resolveAnswerIndex(item.answer(), item.choices().size(), "The study pack service returned an invalid quiz answer. Please try again.");
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

    private ArrayNode buildGenerateNoteInputMessages(String topic, StudyPackGenerationContext context) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(buildTextMessage("system", promptResources.noteGenerationSystemPrompt()));
        input.add(buildTextMessage(
                "developer",
                buildNoteGenerationDeveloperPrompt(context)
        ));
        input.add(buildTextMessage(
                "user",
                buildLearnerContextBlock(context) + "\n"
                        + "Topic: " + topic + "\n"
                        + "Create a study note draft for this topic."
        ));
        return input;
    }

    private String buildNoteGenerationDeveloperPrompt(StudyPackGenerationContext context) {
        LearnerLevel learnerLevel = resolveLearnerLevel(context);
        return promptResources.noteGenerationDeveloperPromptTemplate()
                .replace("{MAX_WORDS}", String.valueOf(MAX_GENERATED_NOTE_WORDS))
                .replace("{LEARNER_LEVEL}", toLearnerLevelLabel(learnerLevel))
                .replace("{LEARNER_LEVEL_GUIDANCE}", buildLearnerLevelNoteGuidance(learnerLevel));
    }

    private ArrayNode buildAdaptivePracticeInputMessages(
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> weakConcepts,
            List<String> disallowedQuestions,
            int questionCount,
            StudyPackGenerationContext context
    ) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(buildTextMessage("system", promptResources.adaptivePracticeSystemPrompt()));
        boolean quantitativeContext = isQuantitativeContext(context, combineLists(keyConcepts, weakConcepts), studyPackSummary);
        String adaptivePracticeDeveloperPrompt = promptResources.adaptivePracticeDeveloperPromptTemplate()
                .replace("{QUESTION_COUNT}", String.valueOf(questionCount))
                .replace("{LEARNER_LEVEL}", toLearnerLevelLabel(resolveLearnerLevel(context)))
                .replace("{LEARNER_LEVEL_GUIDANCE}", buildLearnerLevelGuidance(resolveLearnerLevel(context), QuizMode.ADAPTIVE_PRACTICE))
                .replace("{COMPUTATION_GUIDANCE}", buildComputationGuidance(quantitativeContext, QuizMode.ADAPTIVE_PRACTICE))
                .replace("{TIME_EXPECTATION}", buildTimeExpectation(QuizMode.ADAPTIVE_PRACTICE));
        input.add(buildTextMessage("developer", adaptivePracticeDeveloperPrompt));
        input.add(buildTextMessage(
                "user",
                buildLearnerContextBlock(context) + "\n" +
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
            List<String> existingConcepts,
            int questionCount,
            String difficulty,
            StudyPackGenerationContext context
    ) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(buildTextMessage("system", promptResources.challengeQuizSystemPrompt()));
        boolean quantitativeContext = isQuantitativeContext(context, keyConcepts, studyPackSummary);
        String challengeQuizDeveloperPrompt = promptResources.challengeQuizDeveloperPromptTemplate()
                .replace("{QUESTION_COUNT}", String.valueOf(questionCount))
                .replace("{DIFFICULTY}", difficulty)
                .replace("{LEARNER_LEVEL}", toLearnerLevelLabel(resolveLearnerLevel(context)))
                .replace("{LEARNER_LEVEL_GUIDANCE}", buildLearnerLevelGuidance(resolveLearnerLevel(context), QuizMode.CHALLENGE))
                .replace("{COMPUTATION_GUIDANCE}", buildComputationGuidance(quantitativeContext, QuizMode.CHALLENGE))
                .replace("{TIME_EXPECTATION}", buildTimeExpectation(QuizMode.CHALLENGE));
        input.add(buildTextMessage("developer", challengeQuizDeveloperPrompt));
        StringBuilder userMessage = new StringBuilder();
        userMessage.append(buildLearnerContextBlock(context)).append("\n");
        userMessage.append("Summary: ").append(studyPackSummary).append("\n");
        userMessage.append("Key concepts: ").append(String.join(", ", keyConcepts)).append("\n");
        userMessage.append("Excluded questions (must not be repeated): ").append(String.join(" || ", disallowedQuestions));
        if (existingConcepts != null && !existingConcepts.isEmpty()) {
            userMessage.append("\nConcepts already covered (prioritize other concepts when available): ")
                    .append(String.join(", ", existingConcepts));
        }
        input.add(buildTextMessage("user", userMessage.toString()));
        return input;
    }

    private ArrayNode buildTeacherQuizInputMessages(
            String noteContent,
            List<String> disallowedQuestions,
            int questionCount,
            StudyPackGenerationContext context
    ) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(buildTextMessage("system", promptResources.teacherQuizSystemPrompt()));
        boolean quantitativeContext = isQuantitativeContext(context, context == null ? List.of() : context.tags(), noteContent);
        String teacherQuizDeveloperPrompt = promptResources.teacherQuizDeveloperPromptTemplate()
                .replace("{QUESTION_COUNT}", String.valueOf(questionCount))
                .replace("{LEARNER_LEVEL}", toLearnerLevelLabel(resolveLearnerLevel(context)))
                .replace("{LEARNER_LEVEL_GUIDANCE}", buildLearnerLevelGuidance(resolveLearnerLevel(context), QuizMode.TEACHER_PREVIEW))
                .replace("{COMPUTATION_GUIDANCE}", buildComputationGuidance(quantitativeContext, QuizMode.TEACHER_PREVIEW));
        input.add(buildTextMessage("developer", teacherQuizDeveloperPrompt));
        input.add(buildTextMessage(
                "user",
                buildLearnerContextBlock(context) + "\n" +
                        "Note content: " + noteContent + "\n" +
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
        required.add("explanation");
        required.add("concept");

        ObjectNode itemProps = item.putObject("properties");
        itemProps.putObject("question").put("type", "string");
        ObjectNode answer = itemProps.putObject("answer");
        answer.put("type", "string");
        ArrayNode answerEnum = answer.putArray("enum");
        answerEnum.add("A");
        answerEnum.add("B");
        answerEnum.add("C");
        answerEnum.add("D");
        itemProps.putObject("explanation").put("type", "string").put("minLength", 1);
        itemProps.putObject("concept").put("type", "string").put("minLength", 1);

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

    private JsonNode buildGeneratedNoteSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        ArrayNode required = root.putArray("required");
        required.add("title");
        required.add("overview");
        required.add("keyIdea");
        required.add("coreDetails");
        required.add("whyItMatters");
        required.add("quickRecall");

        ObjectNode properties = root.putObject("properties");
        properties.putObject("title")
                .put("type", "string")
                .put("minLength", 1)
                .put("maxLength", 160);
        properties.putObject("overview")
                .put("type", "string")
                .put("minLength", 1)
                .put("maxLength", 1200);
        properties.putObject("keyIdea")
                .put("type", "string")
                .put("minLength", 1)
                .put("maxLength", 500);
        properties.set("coreDetails", buildGeneratedNoteArraySchema(3, 6));
        properties.set("whyItMatters", buildGeneratedNoteArraySchema(2, 4));
        properties.set("quickRecall", buildGeneratedNoteArraySchema(3, 5));
        return root;
    }

    private ObjectNode buildGeneratedNoteArraySchema(int minItems, int maxItems) {
        ObjectNode arraySchema = objectMapper.createObjectNode();
        arraySchema.put("type", "array");
        arraySchema.put("minItems", minItems);
        arraySchema.put("maxItems", maxItems);
        arraySchema.putObject("items")
                .put("type", "string")
                .put("minLength", 1)
                .put("maxLength", 240);
        return arraySchema;
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

    private String normalizeAndValidateSubject(String subject, StudyPackGenerationContext context) {
        String normalized = SubjectNormalizationUtils.normalizeForStorage(subject);
        // Strip subtopic suffix — subjects must be domain-only (e.g. "Biology", not "Biology – Cell Division")
        if (normalized != null) {
            String stripped = SubjectSanitizer.stripSubtopicSuffix(normalized);
            if (stripped != null && !stripped.equals(normalized)) {
                log.info("requestId={} field=subject value='{}' reason='combined domain-topic stripped' repairedTo='{}'",
                        MDC.get("requestId"), truncateForLog(normalized), stripped);
                normalized = stripped;
            }
        }
        boolean hasContent = StringNormalizationUtils.containsAlphaNumeric(normalized);
        boolean withinWordLimit = hasContent && StringNormalizationUtils.hasWordCountBetween(normalized, 1, SubjectSanitizer.MAX_SUBJECT_WORDS);
        if (!hasContent || !withinWordLimit) {
            int wordCount = hasContent ? StringNormalizationUtils.countWords(normalized) : 0;
            String reason = !hasContent ? "no alphanumeric content"
                    : wordCount > SubjectSanitizer.MAX_SUBJECT_WORDS ? "more than " + SubjectSanitizer.MAX_SUBJECT_WORDS + " words" : "empty";
            if (hasContent && wordCount > SubjectSanitizer.MAX_SUBJECT_WORDS) {
                String repaired = SubjectSanitizer.tryRepair(normalized, SubjectSanitizer.MAX_SUBJECT_WORDS);
                if (repaired != null) {
                    log.info("requestId={} field=subject value='{}' reason='{}' repairedTo='{}'",
                            MDC.get("requestId"), truncateForLog(normalized), reason, repaired);
                    return repaired;
                }
            }
            log.warn("requestId={} field=subject value='{}' reason='{}'",
                    MDC.get("requestId"), truncateForLog(normalized), reason);
            throw invalidOutput("The study pack service returned invalid subject metadata. Please try again.");
        }
        return normalized;
    }

    private String truncateForLog(String value) {
        if (value == null) {
            return "null";
        }
        String sanitized = value.trim().replaceAll("\\s+", " ");
        return sanitized.length() > MAX_LOG_VALUE_LENGTH
                ? sanitized.substring(0, MAX_LOG_VALUE_LENGTH) + "..."
                : sanitized;
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

            // Trim overlong key concepts rather than rejecting outright — always safe to trim.
            if (StringNormalizationUtils.countWords(normalized) > KeyConceptSanitizer.MAX_KEY_CONCEPT_WORDS) {
                String repaired = KeyConceptSanitizer.tryRepair(normalized, KeyConceptSanitizer.MAX_KEY_CONCEPT_WORDS);
                if (repaired != null) {
                    log.info("requestId={} field=keyConcepts value='{}' reason='more than {} words' repairedTo='{}'",
                            MDC.get("requestId"), truncateForLog(normalized), KeyConceptSanitizer.MAX_KEY_CONCEPT_WORDS, repaired);
                    normalized = repaired;
                } else {
                    // Hard-truncate as a last resort so the study pack is never blocked by word-count alone
                    String[] words = normalized.split("\\s+");
                    normalized = String.join(" ", Arrays.copyOf(words, KeyConceptSanitizer.MAX_KEY_CONCEPT_WORDS));
                    log.warn("requestId={} field=keyConcepts value='{}' reason='hard-trimmed to {} words'",
                            MDC.get("requestId"), truncateForLog(keyConcept), KeyConceptSanitizer.MAX_KEY_CONCEPT_WORDS);
                }
            }

            String duplicateKey = StringNormalizationUtils.normalizeForDuplicateCheck(normalized);
            if (duplicateKey.isBlank() || !normalizedSeen.add(duplicateKey)) {
                throw invalidOutput("The study pack service returned repetitive key concepts. Please try again.");
            }
            normalizedConcepts.add(normalized);
        }

        return normalizedConcepts;
    }

    private String normalizeAndValidateConcept(String concept, int quizIndex) {
        String normalized = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(concept);
        if (normalized == null) {
            log.warn("requestId={} field=quiz[{}].concept value=null reason=blank",
                    MDC.get("requestId"), quizIndex);
            throw invalidOutput("The study pack service returned an invalid quiz concept. Please try again.");
        }
        int wordCount = StringNormalizationUtils.countWords(normalized);
        if (!StringNormalizationUtils.hasWordCountBetween(normalized, 1, KeyConceptSanitizer.MAX_QUIZ_CONCEPT_WORDS)) {
            String reason = wordCount > KeyConceptSanitizer.MAX_QUIZ_CONCEPT_WORDS ? "more than " + KeyConceptSanitizer.MAX_QUIZ_CONCEPT_WORDS + " words" : "empty";
            if (wordCount > KeyConceptSanitizer.MAX_QUIZ_CONCEPT_WORDS) {
                String repaired = KeyConceptSanitizer.tryRepair(normalized, KeyConceptSanitizer.MAX_QUIZ_CONCEPT_WORDS);
                if (repaired != null) {
                    log.info("requestId={} field=quiz[{}].concept value='{}' reason='{}' repairedTo='{}'",
                            MDC.get("requestId"), quizIndex, truncateForLog(normalized), reason, repaired);
                    return repaired;
                }
            }
            log.warn("requestId={} field=quiz[{}].concept value='{}' reason='{}'",
                    MDC.get("requestId"), quizIndex, truncateForLog(normalized), reason);
            throw invalidOutput("The study pack service returned an invalid quiz concept. Please try again.");
        }
        return normalized;
    }

    private String normalizeAndValidateConceptOrFallback(String concept, String fallbackConcept) {
        String normalized = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(concept);
        if (normalized != null && StringNormalizationUtils.hasWordCountBetween(normalized, 1, 4)) {
            return normalized;
        }
        if (fallbackConcept != null && !fallbackConcept.isBlank()) {
            return fallbackConcept;
        }
        throw invalidOutput("The quiz service returned an invalid concept. Please try again.");
    }

    private String normalizeAndValidateExplanation(String explanation, String invalidMessage) {
        String normalized = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(explanation);
        if (!StringNormalizationUtils.containsAlphaNumeric(normalized)) {
            throw invalidOutput(invalidMessage);
        }
        return normalized;
    }

    private int resolveAnswerIndex(String answer, int choiceCount, String invalidMessage) {
        if (choiceCount != 4) {
            throw invalidOutput(invalidMessage);
        }
        String normalizedAnswer = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(answer);
        if (normalizedAnswer == null) {
            throw invalidOutput(invalidMessage);
        }
        return switch (normalizedAnswer.toUpperCase()) {
            case "A" -> 0;
            case "B" -> 1;
            case "C" -> 2;
            case "D" -> 3;
            default -> throw invalidOutput(invalidMessage);
        };
    }

    private LearnerLevel resolveLearnerLevel(StudyPackGenerationContext context) {
        if (context == null || context.learnerLevel() == null) {
            return DEFAULT_LEARNER_LEVEL;
        }
        return context.learnerLevel();
    }

    private String toLearnerLevelLabel(LearnerLevel learnerLevel) {
        return switch (learnerLevel) {
            case GRADE_SCHOOL -> "Grade School";
            case JUNIOR_HIGH -> "Junior High School";
            case SENIOR_HIGH -> "Senior High School";
            case COLLEGE -> "College";
            case BOARD_EXAM_REVIEW -> "Board Exam Review";
            case PROFESSIONAL -> "Professional";
            case PERSONAL_LEARNING -> "Personal Learning";
        };
    }

    private String buildLearnerLevelGuidance(LearnerLevel learnerLevel, QuizMode quizMode) {
        return switch (learnerLevel) {
            case GRADE_SCHOOL -> "Keep questions very simple. Focus on basic identification, clear definitions, and direct understanding. Avoid trick questions, subtle distractors, and complex computations.";
            case JUNIOR_HIGH -> "Focus on concept understanding, direct application, and simple problem solving. Basic computations are allowed only when they are straightforward.";
            case SENIOR_HIGH -> "Use concept understanding, moderate application, and simple to moderate computations when appropriate. Keep wording clear and fair.";
            case COLLEGE -> quizMode == QuizMode.QUICK_REVIEW
                    ? "Target college-level understanding with concise concept checks, moderate application, and occasional straightforward computations when clearly supported by the notes."
                    : "Target college-level depth with analysis, situational reasoning, and moderate computations when appropriate.";
            case BOARD_EXAM_REVIEW -> quizMode == QuizMode.ADAPTIVE_PRACTICE
                    ? "Focus on board-exam weak areas using reinforcement questions that still feel exam-relevant, with focused scenarios, selective trick-resistant distractors, and computations when the topic requires them."
                    : "Use board-exam style questions with situational framing, plausible distractors, multi-step thinking, and computations when the topic requires them.";
            case PROFESSIONAL -> "Use applied knowledge, case-based framing, and real-world scenarios. Computations are appropriate when the topic is quantitative or formula-based.";
            case PERSONAL_LEARNING -> "Use practical, accessible explanations with clear wording and real-world relevance. Keep difficulty around a solid college foundation unless the notes clearly suggest otherwise.";
        };
    }

    private String buildLearnerLevelNoteGuidance(LearnerLevel learnerLevel) {
        return switch (learnerLevel) {
            case GRADE_SCHOOL -> "Use simple definitions, concrete wording, and familiar examples without heavy jargon.";
            case JUNIOR_HIGH -> "Keep the note foundational and direct. Highlight the main ideas, basic relationships, and a few concrete examples.";
            case SENIOR_HIGH -> "Use clear academic language with moderate detail, cause-and-effect links, and key terms the learner should remember.";
            case COLLEGE -> "Assume basic subject familiarity and include deeper concepts, distinctions, mechanisms, or formulas when they are genuinely relevant.";
            case BOARD_EXAM_REVIEW -> "Prioritize high-yield distinctions, mechanisms, and review-worthy details that matter for exam preparation.";
            case PROFESSIONAL -> "Use concise applied language, emphasizing practical implications, systems, and real-world decision points when relevant.";
            case PERSONAL_LEARNING -> "Keep the note accessible and practical while still preserving the essential terminology and structure of the topic.";
        };
    }

    private String buildComputationGuidance(boolean quantitativeContext, QuizMode quizMode) {
        if (!quantitativeContext) {
            return "Prefer concept understanding, interpretation, and scenario reasoning over forced numerical questions unless the notes clearly support a computation.";
        }
        return switch (quizMode) {
            case QUICK_REVIEW -> "The material appears quantitative. Include at most one simple numerical or formula-based question if it is clearly supported by the notes, and keep the rest fast concept checks.";
            case CHALLENGE -> "The material appears quantitative. Include computation, formula-based, or problem-solving multiple-choice questions when appropriate. Use numbers, word problems, or applied calculations when the notes support them. Explanations for computation questions must show clear step-by-step solution flow.";
            case ADAPTIVE_PRACTICE -> "The material appears quantitative. Focus weak-concept reinforcement on targeted numerical or formula-based questions when appropriate. Explanations for computation questions must show clear step-by-step solution flow.";
            case TEACHER_PREVIEW -> "The material appears quantitative. Include computation or formula-based questions only when the notes clearly support them. Explanations should show the reasoning or steps a teacher would want to review before export.";
        };
    }

    private String buildTimeExpectation(QuizMode quizMode) {
        return switch (quizMode) {
            case QUICK_REVIEW -> "Each question should feel answerable in about 30 to 60 seconds.";
            case CHALLENGE -> "Each question should feel answerable in about 1 to 2 minutes.";
            case ADAPTIVE_PRACTICE -> "Each question should feel answerable in about 45 to 90 seconds.";
            case TEACHER_PREVIEW -> "Each question should feel classroom-ready rather than speed-focused, with enough substance for teachers to review answers and explanations before export.";
        };
    }

    private String buildLearnerContextBlock(StudyPackGenerationContext context) {
        List<String> lines = new ArrayList<>();
        lines.add("Learner level: " + toLearnerLevelLabel(resolveLearnerLevel(context)));
        if (context != null && context.courseProgram() != null && !context.courseProgram().isBlank()) {
            lines.add("Course / Program: " + context.courseProgram().trim());
            lines.add("Domain constraint: treat the course/program above as the authoritative academic domain. All content, terminology, examples, and question framing must belong to that domain. Do not blend in material from unrelated disciplines.");
        }
        if (context != null && context.subject() != null && !context.subject().isBlank()) {
            lines.add("Current subject: " + context.subject().trim());
        }
        if (context != null && context.tags() != null && !context.tags().isEmpty()) {
            lines.add("Tags: " + String.join(", ", sanitizeStringList(context.tags())));
        }
        return String.join("\n", lines);
    }

    private String buildSubjectSuggestionGuidanceBlock(StudyPackGenerationContext context) {
        List<String> lines = new ArrayList<>();
        lines.add("Subject guidance: use a broad academic domain or curriculum category — domain only, no topic suffix.");
        lines.add("Examples of correct subjects: Biology, Physics, Mathematics, Computer Science, English, History, Engineering, Medicine, Law.");
        lines.add("Do not combine domain and topic. Incorrect: \"Biology – Cell Division\", \"Physics: Ohm's Law\", \"Math – Derivatives\".");
        lines.add("Topic-level specificity belongs in tags and key concepts, not in subject.");
        if (context != null && context.courseProgram() != null && !context.courseProgram().isBlank()) {
            lines.add("Use the course/program as context when it helps identify the right academic domain.");
        }
        return String.join("\n", lines);
    }

    private boolean isQuantitativeContext(StudyPackGenerationContext context, List<String> conceptHints, String summary) {
        StringBuilder haystack = new StringBuilder();
        if (context != null) {
            if (context.courseProgram() != null) {
                haystack.append(context.courseProgram()).append(' ');
            }
            if (context.subject() != null) {
                haystack.append(context.subject()).append(' ');
            }
            if (context.tags() != null) {
                context.tags().forEach(tag -> haystack.append(tag).append(' '));
            }
        }
        if (conceptHints != null) {
            conceptHints.forEach(concept -> haystack.append(concept).append(' '));
        }
        if (summary != null) {
            haystack.append(summary);
        }

        String normalized = haystack.toString().toLowerCase();
        for (String keyword : QUANTITATIVE_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }


    private List<String> combineLists(List<String> primary, List<String> secondary) {
        List<String> combined = new ArrayList<>();
        combined.addAll(sanitizeStringList(primary));
        combined.addAll(sanitizeStringList(secondary));
        return combined;
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
            int questionCount,
            StudyPackGenerationContext context
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
                        questionCount,
                        context
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
            String difficulty,
            StudyPackGenerationContext context
    ) {
        List<String> normalizedKeyConcepts = sanitizeConceptList(keyConcepts);
        List<String> normalizedDisallowedQuestions = sanitizeQuestionList(disallowedQuestions);
        return generateQuizWithSchema(
                buildChallengeQuizInputMessages(
                        studyPackSummary == null ? "" : studyPackSummary,
                        normalizedKeyConcepts,
                        normalizedDisallowedQuestions,
                        List.of(),
                        questionCount,
                        difficulty == null || difficulty.isBlank() ? "medium" : difficulty,
                        context
                ),
                questionCount,
                "note_lib_challenge_quiz",
                "Challenge quiz generation",
                normalizedKeyConcepts
        );
    }

    @Override
    public List<QuizItem> generateMoreChallengeQuiz(
            String studyPackTitle,
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> disallowedQuestions,
            List<String> existingConcepts,
            int questionCount,
            String difficulty,
            StudyPackGenerationContext context
    ) {
        List<String> normalizedKeyConcepts = sanitizeConceptList(keyConcepts);
        List<String> normalizedDisallowedQuestions = sanitizeQuestionList(disallowedQuestions);
        List<String> normalizedExistingConcepts = sanitizeConceptList(existingConcepts);
        return generateQuizWithSchema(
                buildChallengeQuizInputMessages(
                        studyPackSummary == null ? "" : studyPackSummary,
                        normalizedKeyConcepts,
                        normalizedDisallowedQuestions,
                        normalizedExistingConcepts,
                        questionCount,
                        difficulty == null || difficulty.isBlank() ? "medium" : difficulty,
                        context
                ),
                questionCount,
                "note_lib_challenge_quiz",
                "Challenge quiz generation (more)",
                normalizedKeyConcepts
        );
    }

    @Override
    public List<QuizItem> generateTeacherQuiz(
            String noteTitle,
            String noteContent,
            List<String> disallowedQuestions,
            int questionCount,
            StudyPackGenerationContext context
    ) {
        List<String> normalizedDisallowedQuestions = sanitizeQuestionList(disallowedQuestions);
        List<String> conceptFallbackPool = sanitizeConceptList(context == null ? List.of() : context.tags());
        return generateQuizWithSchema(
                buildTeacherQuizInputMessages(
                        noteContent == null ? "" : noteContent,
                        normalizedDisallowedQuestions,
                        questionCount,
                        context
                ),
                questionCount,
                "note_lib_teacher_quiz",
                "Teacher quiz generation",
                conceptFallbackPool
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
        return retryOnceOnInvalidOutput(() -> generateQuizWithSchemaOnce(
                model,
                inputMessages,
                questionCount,
                schemaName,
                operationLabel,
                conceptFallbackPool
        ));
    }

    private List<QuizItem> generateQuizWithSchemaOnce(
            String model,
            ArrayNode inputMessages,
            int questionCount,
            String schemaName,
            String operationLabel,
            List<String> conceptFallbackPool
    ) {
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
            int answerIndex = resolveAnswerIndex(item.answer(), item.choices().size(), operationLabel + " returned an invalid answer mapping. Please try again.");
            String conceptFallback = conceptFallbackPool.isEmpty()
                    ? null
                    : conceptFallbackPool.get(conceptIndex % conceptFallbackPool.size());
            conceptIndex += 1;
            quizItems.add(new QuizItem(
                    item.question().trim(),
                    item.choices().stream().map(String::trim).toList(),
                    answerIndex,
                    normalizeAndValidateConceptOrFallback(item.concept(), conceptFallback),
                    normalizeAndValidateExplanation(item.explanation(), operationLabel + " returned an invalid explanation. Please try again.")
            ));
        }
        return quizItems;
    }

    private void validateGeneratedQuizItem(PromptGeneratedQuizItem item, String operationLabel) {
        if (StringNormalizationUtils.isBlank(item.question()) || StringNormalizationUtils.isBlank(item.answer())) {
            throw invalidOutput(operationLabel + " returned an invalid question. Please try again.");
        }
        if (StringNormalizationUtils.isBlank(item.explanation())) {
            throw invalidOutput(operationLabel + " returned an invalid explanation. Please try again.");
        }
        if (StringNormalizationUtils.isBlank(item.concept())) {
            throw invalidOutput(operationLabel + " returned an invalid concept. Please try again.");
        }
        if (QuizValidationUtils.hasInvalidChoices(item.choices())) {
            throw invalidOutput(operationLabel + " returned invalid choices. Please try again.");
        }
        resolveAnswerIndex(item.answer(), item.choices().size(), operationLabel + " returned an invalid answer mapping. Please try again.");
    }

    private <T> T retryOnceOnInvalidOutput(Supplier<T> operation) {
        AppException lastInvalidOutput = null;
        for (int attempt = 1; attempt <= MAX_INVALID_OUTPUT_ATTEMPTS; attempt++) {
            try {
                return operation.get();
            } catch (AppException ex) {
                if (!INVALID_OUTPUT_CODE.equals(ex.getCode())) {
                    throw ex;
                }
                lastInvalidOutput = ex;
                if (attempt == MAX_INVALID_OUTPUT_ATTEMPTS) {
                    throw ex;
                }
                log.info("Retrying OpenAI response validation after invalid output on attempt {}", attempt);
            }
        }
        throw Objects.requireNonNull(lastInvalidOutput, "lastInvalidOutput");
    }

    private String buildGeneratedNoteContent(PromptGeneratedNote generatedNote, String normalizedTopic) {
        String title = normalizeGeneratedNoteText(
                generatedNote.title(),
                1,
                MAX_GENERATED_NOTE_TITLE_WORDS,
                "The note generation service returned an invalid title. Please try again."
        );
        String overview = normalizeGeneratedNoteText(
                generatedNote.overview(),
                8,
                MAX_GENERATED_NOTE_OVERVIEW_WORDS,
                "The note generation service returned an invalid overview. Please try again."
        );
        String keyIdea = normalizeGeneratedNoteText(
                generatedNote.keyIdea(),
                4,
                MAX_GENERATED_NOTE_KEY_IDEA_WORDS,
                "The note generation service returned an invalid key idea. Please try again."
        );
        List<String> coreDetails = normalizeGeneratedNoteItems(
                generatedNote.coreDetails(),
                3,
                "The note generation service returned invalid core details. Please try again."
        );
        List<String> whyItMatters = normalizeGeneratedNoteItems(
                generatedNote.whyItMatters(),
                2,
                "The note generation service returned invalid why-it-matters content. Please try again."
        );
        List<String> quickRecall = normalizeGeneratedNoteItems(
                generatedNote.quickRecall(),
                3,
                "The note generation service returned invalid quick recall content. Please try again."
        );

        assertGeneratedNoteAvoidsFiller(normalizedTopic, title, overview, keyIdea, coreDetails, whyItMatters, quickRecall);

        StringBuilder builder = new StringBuilder();
        builder.append(title).append("\n\n");
        builder.append("📘 Overview\n");
        builder.append(overview).append("\n\n");
        builder.append("🧠 Key Idea\n");
        builder.append(keyIdea).append("\n\n");
        appendGeneratedNoteSection(builder, "⚔️ Core Details", coreDetails);
        builder.append("\n\n");
        appendGeneratedNoteSection(builder, "🎯 Why It Matters", whyItMatters);
        builder.append("\n\n");
        appendGeneratedNoteSection(builder, "🧠 Quick Recall", quickRecall);

        String content = builder.toString().trim();
        if (StringNormalizationUtils.countWords(content) > MAX_GENERATED_NOTE_WORDS) {
            throw invalidOutput("The note generation service returned an overly long note. Please try again.");
        }
        return content;
    }

    private void appendGeneratedNoteSection(StringBuilder builder, String heading, List<String> items) {
        builder.append(heading).append(":\n");
        for (String item : items) {
            builder.append("- ").append(item).append('\n');
        }
        if (!items.isEmpty()) {
            builder.setLength(builder.length() - 1);
        }
    }

    private String normalizeGeneratedNoteText(String value, int minWords, int maxWords, String errorMessage) {
        String normalized = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(value);
        if (normalized == null || !StringNormalizationUtils.hasWordCountBetween(normalized, minWords, maxWords)) {
            throw invalidOutput(errorMessage);
        }
        return normalized;
    }

    private List<String> normalizeGeneratedNoteItems(List<String> values, int minItems, String errorMessage) {
        List<String> normalized = sanitizeStringList(values).stream()
                .map(value -> normalizeGeneratedNoteText(value, 1, MAX_GENERATED_NOTE_ITEM_WORDS, errorMessage))
                .toList();
        if (normalized.size() < minItems) {
            throw invalidOutput(errorMessage);
        }
        return normalized;
    }

    private void assertGeneratedNoteAvoidsFiller(
            String topic,
            String title,
            String overview,
            String keyIdea,
            List<String> coreDetails,
            List<String> whyItMatters,
            List<String> quickRecall
    ) {
        String normalizedTopic = topic.toLowerCase(Locale.ROOT);
        String noteText = String.join(
                "\n",
                List.of(
                        title,
                        overview,
                        keyIdea,
                        String.join("\n", coreDetails),
                        String.join("\n", whyItMatters),
                        String.join("\n", quickRecall)
                )
        ).toLowerCase(Locale.ROOT);
        for (String phrase : DISALLOWED_NOTE_GENERATION_PHRASES) {
            if (noteText.contains(phrase)) {
                throw invalidOutput("The note generation service returned generic filler. Please try again.");
            }
        }
        if (noteText.contains("create a note about")) {
            throw invalidOutput("The note generation service returned prompt-like filler. Please try again.");
        }
        if (!title.toLowerCase(Locale.ROOT).contains(normalizedTopic) && title.split("\\s+").length <= 2) {
            throw invalidOutput("The note generation service returned a weak title. Please try again.");
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

    private JsonSchemaOperation noteGenerationOperation() {
        return new JsonSchemaOperation(
                "note_lib_generated_note",
                "openai_generated_note_request_failed",
                "openai_generated_note_unavailable",
                "The note generation service returned an empty response. Please try again.",
                "The note generation service returned an unexpected format. Please try again.",
                "Note generation failed. Please try again in a moment.",
                "Note generation is temporarily unavailable. Please try again."
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
            String answer,
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
            String answer,
            String explanation,
            String concept
    ) {
    }

    private record PromptStudyTip(String tip) {
    }

    private record PromptGeneratedNote(
            String title,
            String overview,
            String keyIdea,
            List<String> coreDetails,
            List<String> whyItMatters,
            List<String> quickRecall
    ) {
        PromptGeneratedNote {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(overview, "overview");
            Objects.requireNonNull(keyIdea, "keyIdea");
            Objects.requireNonNull(coreDetails, "coreDetails");
            Objects.requireNonNull(whyItMatters, "whyItMatters");
            Objects.requireNonNull(quickRecall, "quickRecall");
        }
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

    private enum QuizMode {
        QUICK_REVIEW,
        CHALLENGE,
        ADAPTIVE_PRACTICE,
        TEACHER_PREVIEW
    }
}
