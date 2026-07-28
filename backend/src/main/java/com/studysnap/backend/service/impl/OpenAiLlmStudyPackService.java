package com.studysnap.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.config.OpenAiPromptResources;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.dto.CompanionFaqItem;
import com.studysnap.backend.dto.CompanionMentorTip;
import com.studysnap.backend.dto.CompanionMentorTipAction;
import com.studysnap.backend.dto.CompanionSection;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.service.LlmStudyPackService;
import com.studysnap.backend.service.model.CompanionGenerationContext;
import com.studysnap.backend.service.model.GeneratedChallengeQuizContent;
import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import com.studysnap.backend.service.model.InterviewPracticeCritique;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.KeyConceptSanitizer;
import com.studysnap.backend.util.LlmResponseUtils;
import com.studysnap.backend.util.QuizDeduplicationUtils;
import com.studysnap.backend.util.QuizValidationUtils;
import com.studysnap.backend.util.StringNormalizationUtils;
import com.studysnap.backend.util.SubjectNormalizationUtils;
import com.studysnap.backend.util.SubjectSanitizer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@ConditionalOnProperty(prefix = "studysnap.llm.api", name = "provider", havingValue = "openai", matchIfMissing = true)
@RequiredArgsConstructor
public class OpenAiLlmStudyPackService implements LlmStudyPackService {
    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmStudyPackService.class);
    private static final int STUDY_PACK_QUIZ_QUESTION_COUNT = 5;
    private static final int MAX_SUMMARY_WORDS = 350;
    private static final int MAX_STUDY_TIP_WORDS = 20;
    private static final int MAX_GENERATED_NOTE_WORDS = 700;
    private static final int MAX_GENERATED_NOTE_TITLE_WORDS = 12;
    private static final int MAX_GENERATED_NOTE_OVERVIEW_WORDS = 90;
    private static final int MAX_GENERATED_NOTE_KEY_IDEA_WORDS = 40;
    private static final int MAX_GENERATED_NOTE_ITEM_WORDS = 28;
    private static final int COMPANION_FAQ_MIN_ITEMS = 3;
    private static final int COMPANION_FAQ_MAX_ITEMS = 6;
    private static final int COMPANION_MENTOR_TIP_MIN_ITEMS = 1;
    private static final int COMPANION_MENTOR_TIP_MAX_ITEMS = 3;
    private static final int MAX_INVALID_OUTPUT_ATTEMPTS = 2;
    private static final int PARALLEL_BUFFER = 2;
    private static final LearnerLevel DEFAULT_LEARNER_LEVEL = LearnerLevel.COLLEGE;
    private static final String INVALID_OUTPUT_CODE = "LLM_INVALID_OUTPUT";
    private static final int MAX_LOG_VALUE_LENGTH = 80;
    private static final String LONG_EXAM_FIRST_HALF_HINT = "Generate questions covering the first half of the material.";
    private static final String LONG_EXAM_SECOND_HALF_HINT = "Generate questions covering the second half of the material.";
    private static final String PARALLEL_LONG_EXAM_FALLBACK_LOG =
            "Parallel long exam generation failed, falling back to sequential: {}";
    private static final String CRITIQUE_VERDICT_STRONG = "STRONG";
    private static final String CRITIQUE_VERDICT_WORKABLE = "WORKABLE";
    private static final String CRITIQUE_VERDICT_RECONSIDER = "RECONSIDER";
    private static final String MCQ_FORMAT = "MCQ";
    private static final String MULTI_SELECT_FORMAT = "MULTI_SELECT";
    private static final String MATCHING_FORMAT = "MATCHING";
    private static final String IDENTIFICATION_FORMAT = "IDENTIFICATION";
    private static final String ENUMERATION_FORMAT = "ENUMERATION";
    private static final String CHALLENGE_QUIZ_SCHEMA_NAME = "note_lib_challenge_quiz";
    private static final String COMPANION_SCHEMA_NAME = "note_lib_companion_draft";
    private static final String COMPANION_INVALID_OUTPUT_MESSAGE =
            "The Companion generation service returned an invalid format. Please try again.";
    private static final String TRUE_FALSE_GUIDANCE = """
            Mix in True/False questions where appropriate. Use TRUE_FALSE ONLY for a single declarative statement that the learner judges true or false (e.g. "Ohm's Law states that voltage is directly proportional to current — True or False?"). Do NOT use True/False for questions that require nuance, calculation, best-answer judgment, or choosing among statement combinations.

            NEVER use TRUE_FALSE for a question that asks the learner to choose, such as "Which is correct?", "Which of the following...", "Which statement...", "Which one...", or "Which of these...". NEVER use TRUE_FALSE for a stem that references multiple statements, such as "Statement 1: ... Statement 2: ... Which is correct?"

            Assertion-style or multi-statement "Which is correct?" questions MUST be MCQ with four options, such as "Both statements are correct", "Only Statement 1 is correct", "Only Statement 2 is correct", and "Neither statement is correct". They must never use ["True", "False"] choices.

            For True/False questions:
            - Set `questionFormat` to "TRUE_FALSE"
            - Use exactly ["True", "False"] as the choices (in that order)
            - Set `answer` to "A" when True is correct, "B" when False is correct

            For standard MCQ questions, set `questionFormat` to "MCQ" or omit it.

            Aim for at most 25% True/False in any quiz set. Do not force True/False — only use it when the concept genuinely suits a binary statement.
            """;
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
    public String regenerateSummary(String normalizedNoteContent, StudyPackGenerationContext context) {
        return retryOnceOnInvalidOutput(() -> regenerateSummaryOnce(normalizedNoteContent, context));
    }

    @Override
    public String generateNoteFromTopic(String topic, StudyPackGenerationContext context) {
        return retryOnceOnInvalidOutput(() -> generateNoteFromTopicOnce(topic, context));
    }

    @Override
    public CompanionContent generateCompanion(CompanionGenerationContext context, Set<CompanionSection> sections) {
        Objects.requireNonNull(context, "context");
        Set<CompanionSection> requestedSections = normalizeCompanionSections(sections);
        return retryOnceOnInvalidOutput(() -> generateCompanionOnce(context, requestedSections));
    }

    private String regenerateSummaryOnce(String normalizedNoteContent, StudyPackGenerationContext context) {
        String model = requireConfiguredModel();
        JsonSchemaResponse<PromptRegeneratedSummary> response = executeJsonSchemaOperation(
                model,
                buildSummaryRegenerationInputMessages(normalizedNoteContent, context),
                summaryRegenerationOperation(),
                buildRegeneratedSummarySchema(),
                PromptRegeneratedSummary.class
        );
        String summary = response.payload().summary();
        if (StringNormalizationUtils.countWords(summary) > MAX_SUMMARY_WORDS) {
            throw invalidOutput("The study pack service returned an invalid summary format. Please try again.");
        }
        return summary;
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

    private CompanionContent generateCompanionOnce(
            CompanionGenerationContext context,
            Set<CompanionSection> requestedSections
    ) {
        // Companion drafts are curated, human-reviewed prose shown to every learner on a plan,
        // so they use the premium generation tier rather than the lower-latency critique tier.
        String model = requireConfiguredPremiumModel();
        JsonSchemaResponse<PromptCompanionContent> response = executeJsonSchemaOperation(
                model,
                buildCompanionInputMessages(context, requestedSections),
                companionOperation(),
                buildCompanionSchema(),
                PromptCompanionContent.class
        );
        return toCompanionContent(response.payload(), requestedSections);
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
        return toGeneratedStudyPackContent(response.payload(), response.responseJson(), model);
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
                .replace("{TRUE_FALSE_GUIDANCE}", buildTrueFalseGuidance(true))
                .replace("{COMPUTATION_GUIDANCE}", buildComputationGuidance(isQuantitativeContext(context, List.of(), null), QuizMode.QUICK_REVIEW))
                .replace("{TIME_EXPECTATION}", buildTimeExpectation(QuizMode.QUICK_REVIEW));
    }

    private String buildStudyPackUserPrompt(String normalizedNotesText, StudyPackGenerationContext context) {
        return buildContentContextBlock(context)
                + "\n"
                + buildSubjectSuggestionGuidanceBlock(context)
                + "\nStudy notes:\n"
                + normalizedNotesText;
    }

    private ArrayNode buildSummaryRegenerationInputMessages(String normalizedNoteContent, StudyPackGenerationContext context) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(buildTextMessage("system", promptResources.systemPrompt()));
        input.add(buildTextMessage(
                "developer",
                buildStudyPackDeveloperPrompt(context)
                        + "\n\nSummary-only regeneration: return JSON only with this structure: {\"summary\": string}. "
                        + "Regenerate only the summary using the Summary rules above. Do not return title, subject, tags, keyConcepts, quiz, or any other keys."
        ));
        input.add(buildTextMessage(
                "user",
                buildStudyPackUserPrompt(normalizedNoteContent, context)
        ));
        return input;
    }

    private String requireConfiguredModel() {
        return requireConfiguredModel(properties.getSettings().getModelFree(), "LLM model is missing. Please configure LLM_MODEL_FREE.");
    }

    private String requireConfiguredPremiumModel() {
        return requireConfiguredModel(properties.getSettings().getModelPremium(), "LLM premium model is missing. Please configure LLM_MODEL_PREMIUM.");
    }

    private String requireConfiguredCritiqueModel() {
        return requireConfiguredModel(properties.getSettings().getModelCritique(), "LLM critique model is missing. Please configure LLM_MODEL_CRITIQUE.");
    }

    private String requireConfiguredModel(String configuredModel, String missingModelMessage) {
        if (properties.getLlm().getApi().getApiKey() == null || properties.getLlm().getApi().getApiKey().isBlank()) {
            throw configurationError("LLM API key is missing. Please configure LLM_API_KEY.");
        }
        if (configuredModel == null || configuredModel.isBlank()) {
            throw configurationError(missingModelMessage);
        }
        return configuredModel;
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
        String normalizedSubject = normalizeGeneratedSubject(promptStudyPack.subject());
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
            validatePromptQuizItem(item, normalizedQuestions, i);
            String normalizedConcept = normalizeAndValidateConcept(item.concept(), i);
            String normalizedQuestionKey = StringNormalizationUtils.normalizeForDuplicateCheck(item.question());
            if (!normalizedQuestions.add(normalizedQuestionKey)) {
                throw invalidOutput("The study pack service returned repetitive quiz questions. Please try again.");
            }
            String normalizedConceptKey = StringNormalizationUtils.normalizeForDuplicateCheck(normalizedConcept);
            if (!normalizedConcepts.add(normalizedConceptKey)) {
                log.warn("Duplicate quiz concept detected and allowed: concept={}", normalizedConcept);
            }

            int answerIndex = resolveAnswerIndex(item.answer(), item.choices().size(), "The study pack service returned an invalid quiz answer. Please try again.");
            quizItems.add(new QuizItem(
                    item.question(),
                    item.choices(),
                    MULTI_SELECT_FORMAT.equals(item.questionFormat()) ? null : answerIndex,
                    normalizedConcept,
                    normalizeAndValidateExplanation(item.explanation(), "The study pack service returned an invalid quiz explanation. Please try again."),
                    null,
                    item.questionFormat(),
                    item.questionType(),
                    item.workingSolution(),
                    item.correctIndices(),
                    item.questionGroup(),
                    null,
                    item.acceptableAnswers(),
                    item.acceptableAnswerGroups()
            ));
        }
        return normalizeMatchingGroups(quizItems, "study_pack_quiz");
    }

    private void validatePromptQuizItem(
            PromptQuizItem item,
            Set<String> normalizedQuestions,
            int quizIndex
    ) {
        if (StringNormalizationUtils.isBlank(item.question())) {
            throw invalidOutput("The study pack service returned an invalid quiz format. Please try again.");
        }
        normalizeAndValidateConcept(item.concept(), quizIndex);
        normalizeAndValidateExplanation(item.explanation(), "The study pack service returned an invalid quiz explanation. Please try again.");
        if (QuizValidationUtils.hasInvalidChoices(item.choices(), item.questionFormat())) {
            throw invalidOutput("The study pack service returned an invalid quiz format. Please try again.");
        }
        if (QuizValidationUtils.isFormatStemMismatch(item.question(), item.choices(), item.questionFormat())) {
            throw invalidOutput("The study pack service returned an invalid quiz format. Please try again.");
        }
        if (QuizValidationUtils.hasInvalidCorrectIndices(item.correctIndices(), item.choices(), item.questionFormat())) {
            throw invalidOutput("The study pack service returned an invalid multi-select answer. Please try again.");
        }
        resolveAnswerIndex(item.answer(), item.choices().size(), "The study pack service returned an invalid quiz answer. Please try again.");
        String normalizedQuestionKey = StringNormalizationUtils.normalizeForDuplicateCheck(item.question());
        if (normalizedQuestions.contains(normalizedQuestionKey)) {
            throw invalidOutput("The study pack service returned repetitive quiz questions. Please try again.");
        }
    }

    private List<QuizItem> normalizeMatchingGroups(List<QuizItem> quizItems, String operationLabel) {
        if (quizItems == null || quizItems.isEmpty()) {
            return List.of();
        }
        Map<String, List<Integer>> groupedIndexes = new LinkedHashMap<>();
        for (int index = 0; index < quizItems.size(); index++) {
            String questionGroup = quizItems.get(index).questionGroup();
            if (questionGroup != null && !questionGroup.isBlank()) {
                groupedIndexes.computeIfAbsent(questionGroup, ignored -> new ArrayList<>()).add(index);
            }
        }
        if (groupedIndexes.isEmpty()) {
            return quizItems;
        }

        Set<Integer> demotedIndexes = new HashSet<>();
        groupedIndexes.forEach((questionGroup, indexes) -> {
            String reason = resolveInvalidMatchingGroupReason(indexes.stream().map(quizItems::get).toList());
            if (reason != null) {
                log.warn(
                        "{} matching group demoted to MCQ: group={} reason={}",
                        operationLabel,
                        questionGroup,
                        reason
                );
                demotedIndexes.addAll(indexes);
            }
        });

        if (demotedIndexes.isEmpty()) {
            return quizItems;
        }
        List<QuizItem> normalized = new ArrayList<>(quizItems.size());
        for (int index = 0; index < quizItems.size(); index++) {
            QuizItem item = quizItems.get(index);
            normalized.add(demotedIndexes.contains(index) ? copyQuizItemWithQuestionGroup(item, null, MCQ_FORMAT) : item);
        }
        return normalized;
    }

    private String resolveInvalidMatchingGroupReason(List<QuizItem> groupItems) {
        if (groupItems.size() < 2 || groupItems.size() > 4) {
            return "invalid_size";
        }
        List<String> sharedChoices = groupItems.getFirst().choices();
        Set<Integer> correctIndexes = new HashSet<>();
        for (QuizItem item : groupItems) {
            if (!MATCHING_FORMAT.equals(item.questionFormat())) {
                return "invalid_format";
            }
            if (!Objects.equals(sharedChoices, item.choices())) {
                return "different_choices";
            }
            Integer correctIndex = item.correctIndex();
            if (correctIndex == null || correctIndex < 0 || correctIndex >= item.choices().size()) {
                return "invalid_correct_index";
            }
            if (!correctIndexes.add(correctIndex)) {
                return "duplicate_correct_index";
            }
        }
        return null;
    }

    private QuizItem copyQuizItemWithQuestionGroup(QuizItem item, String questionGroup, String questionFormat) {
        return new QuizItem(
                item.question(),
                item.choices(),
                item.correctIndex(),
                item.concept(),
                item.explanation(),
                null,
                questionFormat,
                item.questionType(),
                item.workingSolution(),
                item.correctIndices(),
                questionGroup,
                item.keyConcept(),
                item.acceptableAnswers(),
                item.acceptableAnswerGroups()
        );
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

    private UsageMetadata extractUsageMetadataOrNull(
            JsonNode responseJson,
            String fallbackModel,
            String operationLabel
    ) {
        try {
            UsageMetadata usageMetadata = extractUsageMetadata(responseJson, fallbackModel);
            if (usageMetadata.inputTokens() == null
                    && usageMetadata.outputTokens() == null
                    && usageMetadata.cachedInputTokens() == null) {
                return null;
            }
            return usageMetadata;
        } catch (RuntimeException exception) {
            log.warn("Skipping LLM usage recording because {} returned unexpected usage metadata.", operationLabel, exception);
            return null;
        }
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
                buildNoteGenerationDeveloperPrompt()
        ));
        input.add(buildTextMessage(
                "user",
                buildContentContextBlock(context) + "\n"
                        + "Topic: " + topic + "\n"
                        + "Create a study note draft for this topic."
        ));
        return input;
    }

    private ArrayNode buildCompanionInputMessages(
            CompanionGenerationContext context,
            Set<CompanionSection> requestedSections
    ) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(buildTextMessage("system", promptResources.companionSystemPrompt()));
        input.add(buildTextMessage(
                "developer",
                buildCompanionDeveloperPrompt(context, requestedSections)
        ));
        return input;
    }

    private String buildCompanionDeveloperPrompt(
            CompanionGenerationContext context,
            Set<CompanionSection> requestedSections
    ) {
        return promptResources.companionDeveloperPromptTemplate()
                .replace("{REQUESTED_SECTIONS}", buildCompanionSectionList(requestedSections))
                .replace("{COLLECTION_TITLE}", promptValue(context.title()))
                .replace("{COLLECTION_DESCRIPTION}", promptValue(context.description()))
                .replace("{COURSE_PROGRAM}", promptValue(context.courseProgram()))
                .replace("{STRUCTURE_CONTEXT}", buildCompanionStructureContext(context));
    }

    private String buildCompanionSectionList(Set<CompanionSection> requestedSections) {
        return requestedSections.stream()
                .map(CompanionSection::name)
                .toList()
                .toString();
    }

    private String buildCompanionStructureContext(CompanionGenerationContext context) {
        if (!context.subjectPlans().isEmpty()) {
            return buildCompanionContextItems("Subject Plans", context.subjectPlans());
        }
        if (!context.notes().isEmpty()) {
            return buildCompanionContextItems("Notes", context.notes());
        }
        return "No subject plans or notes are attached yet.";
    }

    private String buildCompanionContextItems(
            String label,
            List<CompanionGenerationContext.CompanionContextItem> items
    ) {
        StringBuilder builder = new StringBuilder(label).append(":\n");
        for (CompanionGenerationContext.CompanionContextItem item : items) {
            builder.append("- ").append(promptValue(item.title()));
            String description = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(item.description());
            if (description != null) {
                builder.append(": ").append(description);
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private String promptValue(String value) {
        String normalized = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(value);
        return normalized == null ? "Not provided" : normalized;
    }

    private String buildNoteGenerationDeveloperPrompt() {
        return promptResources.noteGenerationDeveloperPromptTemplate()
                .replace("{MAX_WORDS}", String.valueOf(MAX_GENERATED_NOTE_WORDS));
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
                .replace("{TRUE_FALSE_GUIDANCE}", buildTrueFalseGuidance(true))
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

    private ArrayNode buildInterviewPracticeInputMessages(
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> disallowedQuestions,
            int questionCount,
            StudyPackGenerationContext context
    ) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(buildTextMessage("system", promptResources.interviewPracticeSystemPrompt()));
        boolean quantitativeContext = isQuantitativeContext(context, keyConcepts, studyPackSummary);
        String developerPrompt = promptResources.interviewPracticeDeveloperPromptTemplate()
                .replace("{QUESTION_COUNT}", String.valueOf(questionCount))
                .replace("{LEARNER_LEVEL}", toLearnerLevelLabel(resolveLearnerLevel(context)))
                .replace("{LEARNER_LEVEL_GUIDANCE}", buildLearnerLevelGuidance(resolveLearnerLevel(context), QuizMode.INTERVIEW_PRACTICE))
                .replace("{COMPUTATION_GUIDANCE}", buildComputationGuidance(quantitativeContext, QuizMode.INTERVIEW_PRACTICE));
        input.add(buildTextMessage("developer", developerPrompt));
        input.add(buildTextMessage(
                "user",
                buildLearnerContextBlock(context) + "\n" +
                        "Summary: " + studyPackSummary + "\n" +
                        "Key concepts: " + String.join(", ", keyConcepts) + "\n" +
                        "Excluded questions (must not be repeated): " + String.join(" || ", disallowedQuestions)
        ));
        return input;
    }

    private ArrayNode buildInterviewCritiqueInputMessages(QuizItem question, int selectedChoiceIndex) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(buildTextMessage("system", promptResources.interviewCritiqueSystemPrompt()));
        String selectedChoice = resolveChoice(question, selectedChoiceIndex);
        String correctChoice = question == null ? "" : question.answer();
        String developerPrompt = promptResources.interviewCritiqueDeveloperPromptTemplate()
                .replace("{QUESTION}", question == null ? "" : question.question())
                .replace("{CHOICES}", buildChoiceList(question))
                .replace("{SELECTED_CHOICE}", selectedChoice)
                .replace("{CORRECT_CHOICE}", correctChoice == null ? "" : correctChoice)
                .replace("{CONCEPT}", question == null || question.concept() == null ? "" : question.concept())
                .replace("{EXPLANATION}", question == null || question.explanation() == null ? "" : question.explanation());
        input.add(buildTextMessage("developer", developerPrompt));
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
                .replace("{TRUE_FALSE_GUIDANCE}", buildTrueFalseGuidance(true))
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

    private ArrayNode buildBoardExamInputMessages(
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> disallowedQuestions,
            int questionCount,
            String difficulty,
            StudyPackGenerationContext context
    ) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(buildTextMessage("system", promptResources.boardExamSystemPrompt()));
        boolean quantitativeContext = isQuantitativeContext(context, keyConcepts, studyPackSummary);
        String boardExamDeveloperPrompt = promptResources.boardExamDeveloperPromptTemplate()
                .replace("{QUESTION_COUNT}", String.valueOf(questionCount))
                .replace("{DIFFICULTY}", difficulty)
                .replace("{LEARNER_LEVEL}", toLearnerLevelLabel(resolveLearnerLevel(context)))
                .replace("{LEARNER_LEVEL_GUIDANCE}", buildLearnerLevelGuidance(resolveLearnerLevel(context), QuizMode.BOARD_EXAM))
                .replace("{COMPUTATION_GUIDANCE}", buildComputationGuidance(quantitativeContext, QuizMode.BOARD_EXAM))
                .replace("{TIME_EXPECTATION}", buildTimeExpectation(QuizMode.BOARD_EXAM));
        input.add(buildTextMessage("developer", boardExamDeveloperPrompt));
        input.add(buildTextMessage(
                "user",
                buildLearnerContextBlock(context) + "\n" +
                        "Summary: " + studyPackSummary + "\n" +
                        "Key concepts: " + String.join(", ", keyConcepts) + "\n" +
                        "Excluded questions (must not be repeated): " + String.join(" || ", disallowedQuestions)
        ));
        return input;
    }

    private ArrayNode buildLongExamInputMessages(
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> disallowedQuestions,
            int questionCount,
            String difficulty,
            StudyPackGenerationContext context
    ) {
        return buildLongExamInputMessages(
                studyPackSummary,
                keyConcepts,
                disallowedQuestions,
                questionCount,
                difficulty,
                context,
                ""
        );
    }

    private ArrayNode buildLongExamInputMessages(
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> disallowedQuestions,
            int questionCount,
            String difficulty,
            StudyPackGenerationContext context,
            String batchHint
    ) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(buildTextMessage("system", promptResources.longExamSystemPrompt()));
        boolean quantitativeContext = isQuantitativeContext(context, keyConcepts, studyPackSummary);
        String longExamDeveloperPrompt = promptResources.longExamDeveloperPromptTemplate()
                .replace("{QUESTION_COUNT}", String.valueOf(questionCount))
                .replace("{BATCH_HINT}", batchHint == null ? "" : batchHint)
                .replace("{DIFFICULTY}", difficulty)
                .replace("{LEARNER_LEVEL}", toLearnerLevelLabel(resolveLearnerLevel(context)))
                .replace("{LEARNER_LEVEL_GUIDANCE}", buildLearnerLevelGuidance(resolveLearnerLevel(context), QuizMode.LONG_EXAM))
                .replace("{TRUE_FALSE_GUIDANCE}", buildTrueFalseGuidance(true))
                .replace("{COMPUTATION_GUIDANCE}", buildComputationGuidance(quantitativeContext, QuizMode.LONG_EXAM))
                .replace("{TIME_EXPECTATION}", buildTimeExpectation(QuizMode.LONG_EXAM));
        input.add(buildTextMessage("developer", longExamDeveloperPrompt));
        input.add(buildTextMessage(
                "user",
                buildLearnerContextBlock(context) + "\n" +
                        "Summary: " + studyPackSummary + "\n" +
                        "Key concepts: " + String.join(", ", keyConcepts) + "\n" +
                        "Excluded questions (must not be repeated): " + String.join(" || ", disallowedQuestions)
        ));
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
                .replace("{TRUE_FALSE_GUIDANCE}", buildTrueFalseGuidance(true))
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

    private JsonNode buildGeneratedQuizSchema(
            int questionCount,
            boolean allowTrueFalse,
            List<String> keyConceptEnum,
            boolean allowIdentification,
            boolean allowEnumeration
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        root.putArray("required").add("questions");
        List<String> normalizedKeyConceptEnum = sanitizeConceptList(keyConceptEnum);

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
        required.add("questionType");
        required.add("workingSolution");
        if (!normalizedKeyConceptEnum.isEmpty()) {
            required.add("keyConcept");
        }
        if (allowIdentification) {
            required.add("acceptableAnswers");
        }
        if (allowEnumeration) {
            required.add("acceptableAnswerGroups");
        }
        if (allowTrueFalse) {
            required.add("questionFormat");
            required.add("questionGroup");
            required.add("correctIndices");
        }

        ObjectNode itemProps = item.putObject("properties");
        itemProps.putObject("question").put("type", "string");
        ObjectNode answer = itemProps.putObject("answer");
        if (allowIdentification || allowEnumeration) {
            ArrayNode answerTypes = answer.putArray("type");
            answerTypes.add("string");
            answerTypes.add("null");
        } else {
            answer.put("type", "string");
            ArrayNode answerEnum = answer.putArray("enum");
            answerEnum.add("A");
            answerEnum.add("B");
            answerEnum.add("C");
            answerEnum.add("D");
        }
        itemProps.putObject("explanation").put("type", "string").put("minLength", 1);
        itemProps.putObject("concept").put("type", "string").put("minLength", 1);
        if (!normalizedKeyConceptEnum.isEmpty()) {
            ObjectNode keyConcept = itemProps.putObject("keyConcept");
            keyConcept.put("type", "string");
            ArrayNode keyConceptValues = keyConcept.putArray("enum");
            normalizedKeyConceptEnum.forEach(keyConceptValues::add);
        }
        if (allowTrueFalse) {
            ObjectNode questionFormat = itemProps.putObject("questionFormat");
            ArrayNode questionFormatTypes = questionFormat.putArray("type");
            questionFormatTypes.add("string");
            questionFormatTypes.add("null");
            ArrayNode questionFormatEnum = questionFormat.putArray("enum");
            questionFormatEnum.add(MCQ_FORMAT);
            questionFormatEnum.add("TRUE_FALSE");
            questionFormatEnum.add(MULTI_SELECT_FORMAT);
            questionFormatEnum.add(MATCHING_FORMAT);
            if (allowIdentification) {
                questionFormatEnum.add(IDENTIFICATION_FORMAT);
            }
            if (allowEnumeration) {
                questionFormatEnum.add(ENUMERATION_FORMAT);
            }
            questionFormatEnum.addNull();
        }
        if (allowTrueFalse) {
            ObjectNode questionGroup = itemProps.putObject("questionGroup");
            ArrayNode questionGroupTypes = questionGroup.putArray("type");
            questionGroupTypes.add("string");
            questionGroupTypes.add("null");
            questionGroup.put("maxLength", 32);

            ObjectNode correctIndices = itemProps.putObject("correctIndices");
            ArrayNode correctIndicesAnyOf = correctIndices.putArray("anyOf");
            correctIndicesAnyOf.addObject()
                    .put("type", "array")
                    .putObject("items")
                    .put("type", "integer");
            correctIndicesAnyOf.addObject().put("type", "null");
        }
        ObjectNode questionType = itemProps.putObject("questionType");
        ArrayNode questionTypeTypes = questionType.putArray("type");
        questionTypeTypes.add("string");
        questionTypeTypes.add("null");
        ArrayNode questionTypeEnum = questionType.putArray("enum");
        questionTypeEnum.add("CONCEPTUAL");
        questionTypeEnum.add("COMPUTATIONAL");
        questionTypeEnum.addNull();
        ObjectNode workingSolution = itemProps.putObject("workingSolution");
        ArrayNode workingSolutionTypes = workingSolution.putArray("type");
        workingSolutionTypes.add("string");
        workingSolutionTypes.add("null");
        if (allowIdentification) {
            ObjectNode acceptableAnswers = itemProps.putObject("acceptableAnswers");
            ArrayNode acceptableAnswersTypes = acceptableAnswers.putArray("type");
            acceptableAnswersTypes.add("array");
            acceptableAnswersTypes.add("null");
            acceptableAnswers.putObject("items").put("type", "string");
        }
        if (allowEnumeration) {
            ObjectNode acceptableAnswerGroups = itemProps.putObject("acceptableAnswerGroups");
            ArrayNode acceptableAnswerGroupsTypes = acceptableAnswerGroups.putArray("type");
            acceptableAnswerGroupsTypes.add("array");
            acceptableAnswerGroupsTypes.add("null");
            ObjectNode acceptableAnswerGroupItems = acceptableAnswerGroups.putObject("items");
            acceptableAnswerGroupItems.put("type", "array");
            acceptableAnswerGroupItems.putObject("items").put("type", "string");
        }

        ObjectNode choices = itemProps.putObject("choices");
        choices.put("type", "array");
        choices.put("minItems", (allowIdentification || allowEnumeration) ? 0 : allowTrueFalse ? 2 : 4);
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

    private JsonNode buildRegeneratedSummarySchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        root.putArray("required").add("summary");
        root.putObject("properties")
                .putObject("summary")
                .put("type", "string")
                .put("minLength", 1);
        return root;
    }

    private JsonNode buildInterviewCritiqueSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        root.putArray("required")
                .add("verdict")
                .add("rationale")
                .add("followUp");
        ObjectNode properties = root.putObject("properties");
        ObjectNode verdict = properties.putObject("verdict");
        verdict.put("type", "string");
        verdict.putArray("enum")
                .add(CRITIQUE_VERDICT_STRONG)
                .add(CRITIQUE_VERDICT_WORKABLE)
                .add(CRITIQUE_VERDICT_RECONSIDER);
        properties.putObject("rationale").put("type", "string").put("minLength", 1);
        properties.putObject("followUp").put("type", "string").put("minLength", 1);
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

    private JsonNode buildCompanionSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        root.putArray("required")
                .add("overview")
                .add("studyStrategy")
                .add("commonMistakes")
                .add("faq")
                .add("mentorTips");

        ObjectNode properties = root.putObject("properties");
        properties.set("overview", buildNullableStringSchema());
        properties.set("studyStrategy", buildNullableStringSchema());
        properties.set("commonMistakes", buildNullableStringSchema());

        ObjectNode faq = properties.putObject("faq");
        ArrayNode faqTypes = faq.putArray("type");
        faqTypes.add("array");
        faqTypes.add("null");
        faq.put("minItems", COMPANION_FAQ_MIN_ITEMS);
        faq.put("maxItems", COMPANION_FAQ_MAX_ITEMS);

        ObjectNode faqItem = faq.putObject("items");
        faqItem.put("type", "object");
        faqItem.put("additionalProperties", false);
        faqItem.putArray("required").add("question").add("answer");
        ObjectNode faqItemProperties = faqItem.putObject("properties");
        faqItemProperties.putObject("question").put("type", "string");
        faqItemProperties.putObject("answer").put("type", "string");

        ObjectNode mentorTips = properties.putObject("mentorTips");
        ArrayNode mentorTipTypes = mentorTips.putArray("type");
        mentorTipTypes.add("array");
        mentorTipTypes.add("null");
        mentorTips.put("minItems", COMPANION_MENTOR_TIP_MIN_ITEMS);
        mentorTips.put("maxItems", COMPANION_MENTOR_TIP_MAX_ITEMS);

        ObjectNode mentorTip = mentorTips.putObject("items");
        mentorTip.put("type", "object");
        mentorTip.put("additionalProperties", false);
        mentorTip.putArray("required").add("title").add("body");
        ObjectNode mentorTipProperties = mentorTip.putObject("properties");
        mentorTipProperties.putObject("title").put("type", "string");
        mentorTipProperties.putObject("body").put("type", "string");
        return root;
    }

    private ObjectNode buildNullableStringSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        ArrayNode types = schema.putArray("type");
        types.add("string");
        types.add("null");
        return schema;
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

    private String normalizeGeneratedSubject(String subject) {
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
        if (hasContent && SubjectSanitizer.isOverlyBroad(normalized)) {
            log.warn("requestId={} field=subject value='{}' reason='overly broad ai suggestion ignored'",
                    MDC.get("requestId"), truncateForLog(normalized));
            return null;
        }
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
            return null;
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
        if (tags == null || tags.isEmpty()) {
            log.warn("requestId={} field=tags reason='missing ai tag suggestions ignored'", MDC.get("requestId"));
            return List.of();
        }

        String normalizedTitle = StringNormalizationUtils.normalizeForDuplicateCheck(title);
        Set<String> normalizedSeenTags = new HashSet<>();
        List<String> normalizedTags = new ArrayList<>();
        for (String tag : tags) {
            String normalizedTag = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(tag);
            if (!StringNormalizationUtils.containsAlphaNumeric(normalizedTag)
                    || !StringNormalizationUtils.hasWordCountBetween(normalizedTag, 1, 3)) {
                log.warn("requestId={} field=tags value='{}' reason='invalid ai tag ignored'",
                        MDC.get("requestId"), truncateForLog(normalizedTag));
                continue;
            }

            String normalizedTagForComparison = StringNormalizationUtils.normalizeForDuplicateCheck(normalizedTag);
            if (normalizedTagForComparison.isBlank()
                    || normalizedTagForComparison.equals(normalizedTitle)) {
                log.warn("requestId={} field=tags value='{}' reason='title-matching ai tag ignored'",
                        MDC.get("requestId"), truncateForLog(normalizedTag));
                continue;
            }
            if (!normalizedSeenTags.add(normalizedTagForComparison)) {
                log.warn("requestId={} field=tags value='{}' reason='duplicate ai tag ignored'",
                        MDC.get("requestId"), truncateForLog(normalizedTag));
                continue;
            }
            normalizedTags.add(normalizedTag);
        }

        return normalizedTags;
    }

    private static final int MIN_VALID_KEY_CONCEPTS = 5;

    private List<String> normalizeAndValidateKeyConcepts(List<String> keyConcepts) {
        if (keyConcepts == null || keyConcepts.isEmpty()) {
            throw invalidOutput("The study pack service returned invalid key concepts. Please try again.");
        }

        List<String> input = keyConcepts;
        if (keyConcepts.size() > 10) {
            // LLM over-generated; trim silently rather than failing the whole study pack.
            log.warn("requestId={} field=keyConcepts count={} reason='over-generated; trimming to 10'",
                    MDC.get("requestId"), keyConcepts.size());
            input = keyConcepts.subList(0, 10);
        } else if (keyConcepts.size() < 8) {
            log.warn("requestId={} field=keyConcepts count={} reason='fewer than expected 8; accepting if >= {}'",
                    MDC.get("requestId"), keyConcepts.size(), MIN_VALID_KEY_CONCEPTS);
        }

        Set<String> normalizedSeen = new HashSet<>();
        List<String> normalizedConcepts = new ArrayList<>();
        for (String keyConcept : input) {
            String normalized = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(keyConcept);
            if (!StringNormalizationUtils.containsAlphaNumeric(normalized)) {
                log.warn("requestId={} field=keyConcepts value='{}' reason='no alphanumeric content; skipping'",
                        MDC.get("requestId"), truncateForLog(keyConcept));
                continue;
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
                log.warn("requestId={} field=keyConcepts value='{}' reason='duplicate; skipping'",
                        MDC.get("requestId"), truncateForLog(normalized));
                continue;
            }
            normalizedConcepts.add(normalized);
        }

        if (normalizedConcepts.size() < MIN_VALID_KEY_CONCEPTS) {
            throw invalidOutput("The study pack service returned too few valid key concepts. Please try again.");
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

    private String normalizeKeyConceptOrNull(String keyConcept, List<String> allowedKeyConcepts) {
        if (allowedKeyConcepts == null || allowedKeyConcepts.isEmpty()) {
            return null;
        }
        if (keyConcept == null) {
            return null;
        }
        String normalized = keyConcept.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return allowedKeyConcepts.contains(normalized) ? normalized : null;
    }

    private String normalizeAndValidateExplanation(String explanation, String invalidMessage) {
        String normalized = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(explanation);
        if (!StringNormalizationUtils.containsAlphaNumeric(normalized)) {
            throw invalidOutput(invalidMessage);
        }
        return normalized;
    }

    private int resolveAnswerIndex(String answer, int choiceCount, String invalidMessage) {
        if (choiceCount != 2 && choiceCount != 4) {
            throw invalidOutput(invalidMessage);
        }
        String normalizedAnswer = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(answer);
        if (normalizedAnswer == null) {
            throw invalidOutput(invalidMessage);
        }
        return switch (normalizedAnswer.toUpperCase()) {
            case "A" -> 0;
            case "B" -> 1;
            case "C" -> {
                if (choiceCount != 4) {
                    throw invalidOutput(invalidMessage);
                }
                yield 2;
            }
            case "D" -> {
                if (choiceCount != 4) {
                    throw invalidOutput(invalidMessage);
                }
                yield 3;
            }
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

    private String buildComputationGuidance(boolean quantitativeContext, QuizMode quizMode) {
        if (!quantitativeContext) {
            return "";
        }
        String guidance = switch (quizMode) {
            case QUICK_REVIEW -> "The material appears quantitative. Include at most one simple numerical or formula-based question if it is clearly supported by the notes, and keep the rest fast concept checks.";
            case CHALLENGE -> "The material appears quantitative. Include computation, formula-based, or problem-solving multiple-choice questions when appropriate. Use numbers, word problems, or applied calculations when the notes support them. Explanations for computation questions must show clear step-by-step solution flow.";
            case BOARD_EXAM -> "The material appears quantitative. Include exam-relevant computations, formula-based judgment, and applied interpretation when the notes support them. Explanations for computation questions must show clear step-by-step solution flow.";
            case LONG_EXAM -> "The material appears quantitative. Include a balanced set of computation, formula-based, and conceptual interpretation questions when the notes support them. Explanations for computation questions must show clear step-by-step solution flow.";
            case ADAPTIVE_PRACTICE -> "The material appears quantitative. Focus weak-concept reinforcement on targeted numerical or formula-based questions when appropriate. Explanations for computation questions must show clear step-by-step solution flow.";
            case INTERVIEW_PRACTICE -> "The material appears quantitative. Frame computations as interview scenarios about choosing, explaining, or validating an approach. Explanations must show the reasoning a strong candidate would articulate.";
            case TEACHER_PREVIEW -> "The material appears quantitative. Include computation or formula-based questions only when the notes clearly support them. Explanations should show the reasoning or steps a teacher would want to review before export.";
        };
        if (quizMode == QuizMode.BOARD_EXAM) {
            return guidance;
        }
        return guidance + " For each question, set questionType to \"COMPUTATIONAL\" for computation or formula-based questions, or \"CONCEPTUAL\" for all others. For COMPUTATIONAL questions, include a workingSolution field with a concise step-by-step derivation (show formula, substitution, and final result). Format all mathematical expressions in workingSolution using LaTeX: wrap inline formulas with $...$ (e.g. $P = IV$) and standalone equations with $$...$$ on their own line (e.g. $$P = \\\\frac{V^2}{R}$$). Use plain text for narrative steps and labels; only the math expressions themselves should be in LaTeX delimiters. For CONCEPTUAL questions, omit workingSolution or set it to null.";
    }

    private String buildTrueFalseGuidance(boolean allowTrueFalse) {
        return allowTrueFalse ? TRUE_FALSE_GUIDANCE : "";
    }

    private String buildTimeExpectation(QuizMode quizMode) {
        return switch (quizMode) {
            case QUICK_REVIEW -> "Each question should feel answerable in about 30 to 60 seconds.";
            case CHALLENGE -> "Each question should feel answerable in about 1 to 2 minutes.";
            case BOARD_EXAM -> "Each question should feel answerable in about 1 minute under high-stakes exam pacing.";
            case LONG_EXAM -> "Each question should feel answerable in about 1 to 2 minutes as part of a longer mastery exam.";
            case ADAPTIVE_PRACTICE -> "Each question should feel answerable in about 45 to 90 seconds.";
            case INTERVIEW_PRACTICE -> "Each question should feel answerable in about 2 minutes as part of interview prep.";
            case TEACHER_PREVIEW -> "Each question should feel classroom-ready rather than speed-focused, with enough substance for teachers to review answers and explanations before export.";
        };
    }

    private String buildChoiceList(QuizItem question) {
        if (question == null || question.choices() == null || question.choices().isEmpty()) {
            return "";
        }
        List<String> labels = List.of("A", "B", "C", "D");
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < question.choices().size() && index < labels.size(); index++) {
            lines.add(labels.get(index) + ". " + question.choices().get(index));
        }
        return String.join("\n", lines);
    }

    private String resolveChoice(QuizItem question, int selectedChoiceIndex) {
        if (question == null || question.choices() == null
                || selectedChoiceIndex < 0 || selectedChoiceIndex >= question.choices().size()) {
            return "";
        }
        return question.choices().get(selectedChoiceIndex);
    }

    private String buildLearnerContextBlock(StudyPackGenerationContext context) {
        return buildGenerationContextBlock(context, true);
    }

    private String buildContentContextBlock(StudyPackGenerationContext context) {
        return buildGenerationContextBlock(context, false);
    }

    private String buildGenerationContextBlock(
            StudyPackGenerationContext context,
            boolean includeLearnerLevel
    ) {
        List<String> lines = new ArrayList<>();
        if (includeLearnerLevel) {
            lines.add("Learner level: " + toLearnerLevelLabel(resolveLearnerLevel(context)));
        }
        if (context != null && context.courseProgram() != null && !context.courseProgram().isBlank()) {
            lines.add("Course / Program: " + context.courseProgram().trim());
            lines.add("Domain constraint: treat the course/program above as the authoritative academic domain. All content, terminology, examples, and question framing must belong to that domain. Do not blend in material from unrelated disciplines.");
            if (!includeLearnerLevel) {
                lines.add("Content calibration: use the Course / Program above to set depth, vocabulary, terminology, and examples. Do not use learner level to calibrate static note or Study Pack content.");
            }
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
        lines.add("Subject guidance: use the specific academic subject or professional discipline covered by the note — label only, no topic suffix.");
        lines.add("For K-12 learners, use the curriculum subject the note belongs to: Biology, Physics, Chemistry, Mathematics, History, English, Economics, Filipino, Science, Social Studies.");
        lines.add("For college and professional learners, use the specific field of study: Civil Engineering, Electrical Engineering, Mechanical Engineering, Nursing, Anatomy, Pharmacology, Clinical Chemistry, Accountancy, Marketing, Business Finance, Computer Science, Pharmacy, Architecture, Constitutional Law, Criminal Law, Civil Law, Legal Ethics.");
        lines.add("Do not suggest overly broad subjects such as Business, Medicine, Engineering, or Law.");
        lines.add("If no specific subject is clear, return null/no subject suggestion; do not guess a broad subject.");
        lines.add("Do not combine subject and topic. Incorrect: \"Biology – Cell Division\", \"Physics: Ohm's Law\", \"Math – Derivatives\".");
        lines.add("Topic-level specificity belongs in tags and key concepts, not in subject.");
        if (context != null && context.courseProgram() != null && !context.courseProgram().isBlank()) {
            lines.add("Course / Program context is: " + context.courseProgram().trim() + ".");
            lines.add("Do not echo the course/program name as the subject. Derive the subject from the note title and content — it should be the specific sub-field this note covers. Examples: a note in 'Mechanical Engineering Licensure' about fluid machinery → 'Fluid Machinery'; a note in 'BS Nursing' covering pharmacology → 'Pharmacology'; a note in 'CPA Licensure' on auditing theory → 'Auditing Theory'.");
            lines.add("If the course/program is a K-12 strand or track (e.g. STEM, ABM, HUMSS, GAS, General Education, Senior High – STEM), derive the subject from the note content instead (e.g. Biology, Physics, Economics, English).");
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
    public List<QuizItem> generateInterviewPracticeQuiz(
            String studyPackTitle,
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> disallowedQuestions,
            int questionCount,
            StudyPackGenerationContext context
    ) {
        List<String> normalizedKeyConcepts = sanitizeConceptList(keyConcepts);
        List<String> normalizedDisallowedQuestions = sanitizeQuestionList(disallowedQuestions);
        return generateQuizWithSchema(
                requireConfiguredPremiumModel(),
                buildInterviewPracticeInputMessages(
                        studyPackSummary == null ? "" : studyPackSummary,
                        normalizedKeyConcepts,
                        normalizedDisallowedQuestions,
                        questionCount,
                        context
                ),
                questionCount,
                "note_lib_interview_practice",
                "Interview practice generation",
                normalizedKeyConcepts,
                normalizedKeyConcepts,
                false
        );
    }

    @Override
    public InterviewPracticeCritique generateInterviewCritique(QuizItem question, int selectedChoiceIndex) {
        JsonSchemaResponse<PromptInterviewCritique> response = executeJsonSchemaOperation(
                requireConfiguredCritiqueModel(),
                buildInterviewCritiqueInputMessages(question, selectedChoiceIndex),
                quizOperation("note_lib_interview_critique", "Interview critique generation"),
                buildInterviewCritiqueSchema(),
                PromptInterviewCritique.class
        );
        PromptInterviewCritique critique = response.payload();
        return new InterviewPracticeCritique(
                critique.verdict(),
                critique.rationale(),
                critique.followUp()
        );
    }

    @Override
    public GeneratedChallengeQuizContent generateChallengeQuiz(
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
        return generateChallengeQuizWithSchema(
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
    public GeneratedChallengeQuizContent generateMoreChallengeQuiz(
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
        return generateChallengeQuizWithSchema(
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
    public List<QuizItem> generateBoardExamQuiz(
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
                buildBoardExamInputMessages(
                        studyPackSummary == null ? "" : studyPackSummary,
                        normalizedKeyConcepts,
                        normalizedDisallowedQuestions,
                        questionCount,
                        difficulty == null || difficulty.isBlank() ? "medium" : difficulty,
                        context
                ),
                questionCount,
                "note_lib_board_exam",
                "Board exam quiz generation",
                normalizedKeyConcepts,
                false
        );
    }

    @Override
    public List<QuizItem> generateLongExam(
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
                buildLongExamInputMessages(
                        studyPackSummary == null ? "" : studyPackSummary,
                        normalizedKeyConcepts,
                        normalizedDisallowedQuestions,
                        questionCount,
                        difficulty == null || difficulty.isBlank() ? "medium" : difficulty,
                        context
                ),
                questionCount,
                "note_lib_long_exam",
                "Long exam generation",
                normalizedKeyConcepts,
                normalizedKeyConcepts
        );
    }

    @Override
    public List<QuizItem> generateLongExamParallel(
            String studyPackTitle,
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> disallowedQuestions,
            int totalQuestions,
            String difficulty,
            StudyPackGenerationContext context,
            AsyncTaskExecutor taskExecutor
    ) {
        int batchSize = (int) Math.ceil(totalQuestions / 2.0) + PARALLEL_BUFFER;
        CompletableFuture<List<QuizItem>> firstBatch = CompletableFuture.supplyAsync(
                () -> generateLongExamBatch(
                    studyPackSummary,
                        keyConcepts,
                        disallowedQuestions,
                        batchSize,
                        difficulty,
                        context,
                        LONG_EXAM_FIRST_HALF_HINT
                ),
            taskExecutor
        );
        CompletableFuture<List<QuizItem>> secondBatch = CompletableFuture.supplyAsync(
                () -> generateLongExamBatch(
                    studyPackSummary,
                        keyConcepts,
                        disallowedQuestions,
                        batchSize,
                        difficulty,
                        context,
                        LONG_EXAM_SECOND_HALF_HINT
                ),
            taskExecutor
        );
        try {
            CompletableFuture.allOf(firstBatch, secondBatch).orTimeout(240, TimeUnit.SECONDS).join();
            List<QuizItem> batch1 = firstBatch.get();
            List<QuizItem> batch2 = secondBatch.get();
            List<QuizItem> uniqueBatch2 = QuizDeduplicationUtils.uniqueQuestions(
                    batch2,
                    QuizDeduplicationUtils.toNormalizedQuestionSet(batch1)
            );
            List<QuizItem> merged = new ArrayList<>(batch1);
            merged.addAll(uniqueBatch2);
            return merged.subList(0, Math.min(merged.size(), totalQuestions));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn(PARALLEL_LONG_EXAM_FALLBACK_LOG, ex.getMessage());
            return generateLongExam(
                    studyPackTitle,
                    studyPackSummary,
                    keyConcepts,
                    disallowedQuestions,
                    totalQuestions,
                    difficulty,
                    context
            );
        } catch (Exception ex) {
            log.warn(PARALLEL_LONG_EXAM_FALLBACK_LOG, ex.getMessage());
            boolean firstSucceeded = firstBatch.isDone() && !firstBatch.isCompletedExceptionally();
            boolean secondSucceeded = secondBatch.isDone() && !secondBatch.isCompletedExceptionally();
            if (firstSucceeded || secondSucceeded) {
                try {
                    List<QuizItem> salvaged = firstSucceeded ? firstBatch.getNow(List.of()) : secondBatch.getNow(List.of());
                    int remaining = totalQuestions - salvaged.size();
                    if (remaining <= 0) {
                        return salvaged.subList(0, Math.min(salvaged.size(), totalQuestions));
                    }
                    List<String> extendedDisallowed = new ArrayList<>(sanitizeQuestionList(disallowedQuestions));
                    salvaged.stream().map(QuizItem::question).filter(Objects::nonNull).forEach(extendedDisallowed::add);
                    List<QuizItem> fallback = generateLongExam(studyPackTitle, studyPackSummary, keyConcepts, extendedDisallowed, remaining, difficulty, context);
                    List<QuizItem> uniqueFallback = QuizDeduplicationUtils.uniqueQuestions(fallback, QuizDeduplicationUtils.toNormalizedQuestionSet(salvaged));
                    List<QuizItem> merged = new ArrayList<>(salvaged);
                    merged.addAll(uniqueFallback);
                    return merged.subList(0, Math.min(merged.size(), totalQuestions));
                } catch (Exception salvageEx) {
                    log.warn("Parallel long exam batch salvage failed, retrying full sequential: {}", salvageEx.getMessage());
                }
            }
            return generateLongExam(
                    studyPackTitle,
                    studyPackSummary,
                    keyConcepts,
                    disallowedQuestions,
                    totalQuestions,
                    difficulty,
                    context
            );
        }
    }

    private List<QuizItem> generateLongExamBatch(
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> disallowedQuestions,
            int questionCount,
            String difficulty,
            StudyPackGenerationContext context,
            String batchHint
    ) {
        List<String> normalizedKeyConcepts = sanitizeConceptList(keyConcepts);
        List<String> normalizedDisallowedQuestions = sanitizeQuestionList(disallowedQuestions);
        return generateQuizWithSchema(
                buildLongExamInputMessages(
                        studyPackSummary == null ? "" : studyPackSummary,
                        normalizedKeyConcepts,
                        normalizedDisallowedQuestions,
                        questionCount,
                        difficulty == null || difficulty.isBlank() ? "medium" : difficulty,
                        context,
                        batchHint
                ),
                questionCount,
                "note_lib_long_exam",
                "Long exam generation batch",
                normalizedKeyConcepts,
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
        return generateQuizWithSchema(inputMessages, questionCount, schemaName, operationLabel, conceptFallbackPool, List.of(), true);
    }

    private List<QuizItem> generateQuizWithSchema(
            ArrayNode inputMessages,
            int questionCount,
            String schemaName,
            String operationLabel,
            List<String> conceptFallbackPool,
            List<String> keyConceptEnum
    ) {
        return generateQuizWithSchema(inputMessages, questionCount, schemaName, operationLabel, conceptFallbackPool, keyConceptEnum, true);
    }

    private List<QuizItem> generateQuizWithSchema(
            ArrayNode inputMessages,
            int questionCount,
            String schemaName,
            String operationLabel,
            List<String> conceptFallbackPool,
            boolean allowTrueFalse
    ) {
        return generateQuizWithSchema(inputMessages, questionCount, schemaName, operationLabel, conceptFallbackPool, List.of(), allowTrueFalse);
    }

    private List<QuizItem> generateQuizWithSchema(
            ArrayNode inputMessages,
            int questionCount,
            String schemaName,
            String operationLabel,
            List<String> conceptFallbackPool,
            List<String> keyConceptEnum,
            boolean allowTrueFalse
    ) {
        String model = requireConfiguredModel();
        return generateQuizWithSchema(
                model,
                inputMessages,
                questionCount,
                schemaName,
                operationLabel,
                conceptFallbackPool,
                keyConceptEnum,
                allowTrueFalse
        );
    }

    private List<QuizItem> generateQuizWithSchema(
            String model,
            ArrayNode inputMessages,
            int questionCount,
            String schemaName,
            String operationLabel,
            List<String> conceptFallbackPool
    ) {
        return generateQuizWithSchema(
                model,
                inputMessages,
                questionCount,
                schemaName,
                operationLabel,
                conceptFallbackPool,
                List.of(),
                true
        );
    }

    private List<QuizItem> generateQuizWithSchema(
            String model,
            ArrayNode inputMessages,
            int questionCount,
            String schemaName,
            String operationLabel,
            List<String> conceptFallbackPool,
            boolean allowTrueFalse
    ) {
        return generateQuizWithSchema(model, inputMessages, questionCount, schemaName, operationLabel, conceptFallbackPool, List.of(), allowTrueFalse);
    }

    private List<QuizItem> generateQuizWithSchema(
            String model,
            ArrayNode inputMessages,
            int questionCount,
            String schemaName,
            String operationLabel,
            List<String> conceptFallbackPool,
            List<String> keyConceptEnum,
            boolean allowTrueFalse
    ) {
        return generateQuizWithSchemaResponse(
                model,
                inputMessages,
                questionCount,
                schemaName,
                operationLabel,
                conceptFallbackPool,
                keyConceptEnum,
                allowTrueFalse
        ).quizItems();
    }

    private GeneratedChallengeQuizContent generateChallengeQuizWithSchema(
            ArrayNode inputMessages,
            int questionCount,
            String schemaName,
            String operationLabel,
            List<String> conceptFallbackPool
    ) {
        String model = requireConfiguredModel();
        GeneratedQuizResponse generated = generateQuizWithSchemaResponse(
                model,
                inputMessages,
                questionCount,
                schemaName,
                operationLabel,
                conceptFallbackPool,
                List.of(),
                true
        );
        UsageMetadata usageMetadata = extractUsageMetadataOrNull(
                generated.responseJson(),
                model,
                operationLabel
        );
        if (usageMetadata == null) {
            return GeneratedChallengeQuizContent.withoutUsage(generated.quizItems());
        }
        return new GeneratedChallengeQuizContent(
                generated.quizItems(),
                usageMetadata.modelUsed(),
                usageMetadata.inputTokens(),
                usageMetadata.outputTokens(),
                usageMetadata.cachedInputTokens()
        );
    }

    private GeneratedQuizResponse generateQuizWithSchemaResponse(
            String model,
            ArrayNode inputMessages,
            int questionCount,
            String schemaName,
            String operationLabel,
            List<String> conceptFallbackPool,
            List<String> keyConceptEnum,
            boolean allowTrueFalse
    ) {
        return retryOnceOnInvalidOutput(() -> generateQuizWithSchemaOnce(
                model,
                inputMessages,
                questionCount,
                schemaName,
                operationLabel,
                conceptFallbackPool,
                keyConceptEnum,
                allowTrueFalse
        ));
    }

    private GeneratedQuizResponse generateQuizWithSchemaOnce(
            String model,
            ArrayNode inputMessages,
            int questionCount,
            String schemaName,
            String operationLabel,
            List<String> conceptFallbackPool,
            List<String> keyConceptEnum,
            boolean allowTrueFalse
    ) {
        List<String> normalizedKeyConceptEnum = sanitizeConceptList(keyConceptEnum);
        JsonSchemaResponse<PromptGeneratedQuiz> response = executeJsonSchemaOperation(
                model,
                inputMessages,
                quizOperation(schemaName, operationLabel),
                buildGeneratedQuizSchema(
                        questionCount,
                        allowTrueFalse,
                        normalizedKeyConceptEnum,
                        CHALLENGE_QUIZ_SCHEMA_NAME.equals(schemaName),
                        CHALLENGE_QUIZ_SCHEMA_NAME.equals(schemaName)
                ),
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
            boolean isFreeTextFormat = IDENTIFICATION_FORMAT.equals(item.questionFormat())
                    || ENUMERATION_FORMAT.equals(item.questionFormat());
            Integer answerIndex = isFreeTextFormat
                    ? null
                    : resolveAnswerIndex(item.answer(), item.choices().size(), operationLabel + " returned an invalid answer mapping. Please try again.");
            String conceptFallback = conceptFallbackPool.isEmpty()
                    ? null
                    : conceptFallbackPool.get(conceptIndex % conceptFallbackPool.size());
            conceptIndex += 1;
            quizItems.add(new QuizItem(
                    item.question().trim(),
                    item.choices() == null ? List.of() : item.choices().stream().map(String::trim).toList(),
                    MULTI_SELECT_FORMAT.equals(item.questionFormat()) ? null : answerIndex,
                    normalizeAndValidateConceptOrFallback(item.concept(), conceptFallback),
                    normalizeAndValidateExplanation(item.explanation(), operationLabel + " returned an invalid explanation. Please try again."),
                    null,
                    item.questionFormat(),
                    item.questionType(),
                    item.workingSolution(),
                    item.correctIndices(),
                    item.questionGroup(),
                    normalizeKeyConceptOrNull(item.keyConcept(), normalizedKeyConceptEnum),
                    item.acceptableAnswers(),
                    item.acceptableAnswerGroups()
            ));
        }
        return new GeneratedQuizResponse(
                normalizeMatchingGroups(quizItems, operationLabel),
                response.responseJson()
        );
    }

    private void validateGeneratedQuizItem(PromptGeneratedQuizItem item, String operationLabel) {
        if (StringNormalizationUtils.isBlank(item.question())) {
            throw invalidOutput(operationLabel + " returned an invalid question. Please try again.");
        }
        if (StringNormalizationUtils.isBlank(item.explanation())) {
            throw invalidOutput(operationLabel + " returned an invalid explanation. Please try again.");
        }
        if (StringNormalizationUtils.isBlank(item.concept())) {
            throw invalidOutput(operationLabel + " returned an invalid concept. Please try again.");
        }
        if (QuizValidationUtils.hasInvalidChoices(item.choices(), item.questionFormat())) {
            throw invalidOutput(operationLabel + " returned invalid choices. Please try again.");
        }
        if (QuizValidationUtils.isFormatStemMismatch(item.question(), item.choices(), item.questionFormat())) {
            throw invalidOutput(operationLabel + " returned an invalid quiz format. Please try again.");
        }
        if (QuizValidationUtils.hasInvalidCorrectIndices(item.correctIndices(), item.choices(), item.questionFormat())) {
            throw invalidOutput(operationLabel + " returned invalid multi-select answers. Please try again.");
        }
        if (IDENTIFICATION_FORMAT.equals(item.questionFormat())) {
            if (item.choices() == null || !item.choices().isEmpty()
                    || item.acceptableAnswers() == null || item.acceptableAnswers().isEmpty()) {
                throw invalidOutput(operationLabel + " returned an invalid identification question. Please try again.");
            }
        } else if (ENUMERATION_FORMAT.equals(item.questionFormat())) {
            List<List<String>> groups = item.acceptableAnswerGroups();
            if (item.choices() == null || !item.choices().isEmpty()
                    || groups == null || groups.isEmpty()
                    || groups.stream().anyMatch(group -> group == null || group.isEmpty())) {
                throw invalidOutput(operationLabel + " returned an invalid enumeration question. Please try again.");
            }
        } else if (StringNormalizationUtils.isBlank(item.answer())) {
            throw invalidOutput(operationLabel + " returned an invalid question. Please try again.");
        } else {
            resolveAnswerIndex(item.answer(), item.choices().size(), operationLabel + " returned an invalid answer mapping. Please try again.");
        }
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
                log.warn("Retrying OpenAI response validation after invalid output on attempt {}: {}", attempt, ex.getMessage());
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

    private Set<CompanionSection> normalizeCompanionSections(Set<CompanionSection> sections) {
        if (sections == null || sections.isEmpty()) {
            throw invalidOutput(COMPANION_INVALID_OUTPUT_MESSAGE);
        }
        Set<CompanionSection> normalized = EnumSet.noneOf(CompanionSection.class);
        sections.stream()
                .filter(Objects::nonNull)
                .forEach(normalized::add);
        if (normalized.isEmpty()) {
            throw invalidOutput(COMPANION_INVALID_OUTPUT_MESSAGE);
        }
        return normalized;
    }

    private CompanionContent toCompanionContent(
            PromptCompanionContent content,
            Set<CompanionSection> requestedSections
    ) {
        List<CompanionFaqItem> faq = List.of();
        if (requestedSections.contains(CompanionSection.FAQ)) {
            faq = normalizeCompanionFaq(content.faq());
        }
        List<CompanionMentorTip> mentorTips = List.of();
        if (requestedSections.contains(CompanionSection.MENTOR_TIPS)) {
            mentorTips = normalizeCompanionMentorTips(content.mentorTips());
        }
        return new CompanionContent(
                requestedSections.contains(CompanionSection.OVERVIEW) ? content.overview() : null,
                requestedSections.contains(CompanionSection.STUDY_STRATEGY) ? content.studyStrategy() : null,
                requestedSections.contains(CompanionSection.COMMON_MISTAKES) ? content.commonMistakes() : null,
                null,
                faq,
                mentorTips
        );
    }

    private List<CompanionFaqItem> normalizeCompanionFaq(List<PromptCompanionFaqItem> faq) {
        if (faq == null || faq.size() < COMPANION_FAQ_MIN_ITEMS || faq.size() > COMPANION_FAQ_MAX_ITEMS) {
            throw invalidOutput(COMPANION_INVALID_OUTPUT_MESSAGE);
        }
        return faq.stream()
                .map(item -> new CompanionFaqItem(item.question(), item.answer()))
                .toList();
    }

    private List<CompanionMentorTip> normalizeCompanionMentorTips(List<PromptCompanionMentorTip> mentorTips) {
        if (mentorTips == null
                || mentorTips.size() < COMPANION_MENTOR_TIP_MIN_ITEMS
                || mentorTips.size() > COMPANION_MENTOR_TIP_MAX_ITEMS) {
            throw invalidOutput(COMPANION_INVALID_OUTPUT_MESSAGE);
        }
        return mentorTips.stream()
                .map(item -> new CompanionMentorTip(
                        null,
                        item.title(),
                        item.body(),
                        CompanionMentorTipAction.NONE,
                        null
                ))
                .toList();
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

    private JsonSchemaOperation summaryRegenerationOperation() {
        return new JsonSchemaOperation(
                "note_lib_regenerated_summary",
                "openai_summary_regeneration_request_failed",
                "openai_summary_regeneration_unavailable",
                "The summary regeneration service returned an empty response. Please try again.",
                "The summary regeneration service returned an unexpected format. Please try again.",
                "Summary regeneration failed. Please try again in a moment.",
                "Summary regeneration is temporarily unavailable. Please try again."
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

    private JsonSchemaOperation companionOperation() {
        return new JsonSchemaOperation(
                COMPANION_SCHEMA_NAME,
                "openai_companion_request_failed",
                "openai_companion_unavailable",
                "The Companion generation service returned an empty response. Please try again.",
                COMPANION_INVALID_OUTPUT_MESSAGE,
                "Companion generation failed. Please try again in a moment.",
                "Companion generation is temporarily unavailable. Please try again."
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
            @JsonAlias("key_concepts") List<String> keyConcepts,
            List<PromptQuizItem> quiz
    ) {
        PromptStudyPack {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(summary, "summary");
            Objects.requireNonNull(tags, "tags");
            Objects.requireNonNull(keyConcepts, "keyConcepts");
            Objects.requireNonNull(quiz, "quiz");
        }
    }

    private record PromptRegeneratedSummary(String summary) {
        PromptRegeneratedSummary {
            Objects.requireNonNull(summary, "summary");
        }
    }

    private record PromptQuizItem(
            String question,
            List<String> choices,
            String answer,
            String concept,
            String explanation,
            String questionFormat,
            String questionType,
            String workingSolution,
            List<Integer> correctIndices,
            String questionGroup,
            List<String> acceptableAnswers,
            List<List<String>> acceptableAnswerGroups
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
            String concept,
            String questionFormat,
            String questionType,
            String workingSolution,
            List<Integer> correctIndices,
            String questionGroup,
            String keyConcept,
            List<String> acceptableAnswers,
            List<List<String>> acceptableAnswerGroups
    ) {
    }

    private record PromptInterviewCritique(
            String verdict,
            String rationale,
            String followUp
    ) {
    }

    private record PromptCompanionContent(
            String overview,
            String studyStrategy,
            String commonMistakes,
            List<PromptCompanionFaqItem> faq,
            List<PromptCompanionMentorTip> mentorTips
    ) {
    }

    private record PromptCompanionFaqItem(
            String question,
            String answer
    ) {
    }

    private record PromptCompanionMentorTip(
            String title,
            String body
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

    private record GeneratedQuizResponse(
            List<QuizItem> quizItems,
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
        BOARD_EXAM,
        LONG_EXAM,
        ADAPTIVE_PRACTICE,
        INTERVIEW_PRACTICE,
        TEACHER_PREVIEW
    }
}
