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
import com.studysnap.backend.util.LlmResponseUtils;
import com.studysnap.backend.util.QuizValidationUtils;
import com.studysnap.backend.util.StringNormalizationUtils;
import com.studysnap.backend.util.SubjectNormalizationUtils;
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
    private static final int MAX_INVALID_OUTPUT_ATTEMPTS = 2;
    private static final int MAX_SUBJECT_WORDS = 6;
    private static final LearnerLevel DEFAULT_LEARNER_LEVEL = LearnerLevel.COLLEGE;
    private static final String INVALID_OUTPUT_CODE = "LLM_INVALID_OUTPUT";
    private static final Set<String> OVERLY_BROAD_SUBJECT_LABELS = Set.of(
            "business",
            "education",
            "engineering",
            "law",
            "medicine"
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
            Set<String> normalizedConcepts
    ) {
        if (StringNormalizationUtils.isBlank(item.question())) {
            throw invalidOutput("The study pack service returned an invalid quiz format. Please try again.");
        }
        normalizeAndValidateConcept(item.concept());
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
        input.add(buildTextMessage(
                "user",
                buildLearnerContextBlock(context) + "\n" +
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
        if (!StringNormalizationUtils.containsAlphaNumeric(normalized)
                || !StringNormalizationUtils.hasWordCountBetween(normalized, 1, MAX_SUBJECT_WORDS)) {
            throw invalidOutput("The study pack service returned invalid subject metadata. Please try again.");
        }
        if (isOverlyBroadSubjectLabel(normalized) || matchesBroadCourseProgram(normalized, context)) {
            throw invalidOutput("The study pack service returned subject metadata that is too broad. Please try again.");
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

    private String buildComputationGuidance(boolean quantitativeContext, QuizMode quizMode) {
        if (!quantitativeContext) {
            return "Prefer concept understanding, interpretation, and scenario reasoning over forced numerical questions unless the notes clearly support a computation.";
        }
        return switch (quizMode) {
            case QUICK_REVIEW -> "The material appears quantitative. Include at most one simple numerical or formula-based question if it is clearly supported by the notes, and keep the rest fast concept checks.";
            case CHALLENGE -> "The material appears quantitative. Include computation, formula-based, or problem-solving multiple-choice questions when appropriate. Use numbers, word problems, or applied calculations when the notes support them. Explanations for computation questions must show clear step-by-step solution flow.";
            case ADAPTIVE_PRACTICE -> "The material appears quantitative. Focus weak-concept reinforcement on targeted numerical or formula-based questions when appropriate. Explanations for computation questions must show clear step-by-step solution flow.";
        };
    }

    private String buildTimeExpectation(QuizMode quizMode) {
        return switch (quizMode) {
            case QUICK_REVIEW -> "Each question should feel answerable in about 30 to 60 seconds.";
            case CHALLENGE -> "Each question should feel answerable in about 1 to 2 minutes.";
            case ADAPTIVE_PRACTICE -> "Each question should feel answerable in about 45 to 90 seconds.";
        };
    }

    private String buildLearnerContextBlock(StudyPackGenerationContext context) {
        List<String> lines = new ArrayList<>();
        lines.add("Learner level: " + toLearnerLevelLabel(resolveLearnerLevel(context)));
        if (context != null && context.courseProgram() != null && !context.courseProgram().isBlank()) {
            lines.add("Course / Program: " + context.courseProgram().trim());
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
        lines.add("Subject guidance: choose a specific academic library subject, not a broad umbrella field.");
        lines.add("Prefer a label like \"Primary field – subtopic\" when that helps group similar notes.");
        lines.add("Examples: Nursing – Pharmacology; Biology – Cell Division; Criminal Law – Crimes Against Persons; Software Engineering – Data Structures.");
        if (context != null && context.subject() != null && !context.subject().isBlank()) {
            lines.add("If the current subject is already specific, stay close to it. If it is broad, refine it into a more specific academic subject.");
        } else if (context != null && context.courseProgram() != null && !context.courseProgram().isBlank()) {
            lines.add("Use the course/program as context when it helps choose the best specific subject.");
        }
        lines.add("Avoid generic labels like Medicine, Engineering, Education, Law, or Business when the notes support a more useful filterable subject.");
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

    private boolean isOverlyBroadSubjectLabel(String subject) {
        return OVERLY_BROAD_SUBJECT_LABELS.contains(subject.toLowerCase(Locale.ROOT));
    }

    private boolean matchesBroadCourseProgram(String subject, StudyPackGenerationContext context) {
        if (context == null) {
            return false;
        }
        String normalizedCourseProgram = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(context.courseProgram());
        if (normalizedCourseProgram == null) {
            return false;
        }
        if (!subject.equalsIgnoreCase(normalizedCourseProgram)) {
            return false;
        }
        return !subject.contains(" - ") && !subject.contains(" – ");
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
                log.info("Retrying OpenAI quiz validation after invalid output on attempt {}", attempt);
            }
        }
        throw Objects.requireNonNull(lastInvalidOutput, "lastInvalidOutput");
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
        ADAPTIVE_PRACTICE
    }
}
