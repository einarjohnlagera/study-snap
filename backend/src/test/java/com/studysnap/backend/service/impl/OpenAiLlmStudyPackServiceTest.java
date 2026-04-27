package com.studysnap.backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.config.OpenAiPromptResources;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiLlmStudyPackServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private StudySnapProperties properties;
    private ObjectMapper objectMapper;
    private OpenAiLlmStudyPackService service;

    @BeforeEach
    void setUp() {
        properties = new StudySnapProperties();
        properties.getLlm().getApi().setApiKey("test-api-key");
        properties.getSettings().setModelFree("gpt-4.1-mini");

        objectMapper = new ObjectMapper();
        service = new OpenAiLlmStudyPackService(
            properties,
            objectMapper,
            restClient,
            new OpenAiPromptResources(
                "System prompt",
                "Developer prompt with {QUIZ_COUNT} questions for {LEARNER_LEVEL}. {LEARNER_LEVEL_GUIDANCE} {COMPUTATION_GUIDANCE} {TIME_EXPECTATION}",
                objectMapper.createObjectNode(),
                "Note generation system prompt",
                "Note generation developer prompt for {LEARNER_LEVEL}. {LEARNER_LEVEL_GUIDANCE} Built for studying, not just exploring information. Max {MAX_WORDS} words.",
                "Challenge quiz system prompt",
                "Challenge quiz developer prompt for {QUESTION_COUNT} at {DIFFICULTY} for {LEARNER_LEVEL}. {LEARNER_LEVEL_GUIDANCE} {COMPUTATION_GUIDANCE} {TIME_EXPECTATION}",
                "Teacher quiz system prompt",
                "Teacher quiz developer prompt for {QUESTION_COUNT} for {LEARNER_LEVEL}. {LEARNER_LEVEL_GUIDANCE} {COMPUTATION_GUIDANCE}",
                "Adaptive practice system prompt",
                "Adaptive practice developer prompt for {QUESTION_COUNT} for {LEARNER_LEVEL}. {LEARNER_LEVEL_GUIDANCE} {COMPUTATION_GUIDANCE} {TIME_EXPECTATION}"
            )
        );

    }

    @Test
    void generateStudyPack_returnsValidatedContentAndUsage() throws JsonProcessingException {
        stubResponsesCall();
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(buildValidStudyPackPayload()));

        GeneratedStudyPackContent content = service.generateStudyPack(
            "Cell respiration notes",
            new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                "Biology",
                "Biology",
                List.of("cells", "respiration")
            )
        );

        assertThat(content.title()).isEqualTo("Cell Respiration Review");
        assertThat(content.summary()).isEqualTo(
            "Cell respiration turns glucose into ATP through glycolysis and aerobic pathways.");
        assertThat(content.subject()).isEqualTo("Biology");
        assertThat(content.tags()).containsExactly("cells", "energy", "respiration");
        assertThat(content.keyConcepts()).hasSize(8);
        assertThat(content.quiz()).hasSize(5);
        assertThat(content.quiz().stream().map(QuizItem::question))
            .containsExactly(
                "What is the main goal of cell respiration?",
                "Which stage produces the most ATP?",
                "Where does glycolysis happen?",
                "What molecule carries electrons to the ETC?",
                "What final molecule accepts electrons in aerobic respiration?"
            );
        assertThat(content.quiz().getFirst().answer()).isEqualTo("Produce ATP");
        assertThat(content.quiz().getFirst().explanation()).isEqualTo(
            "ATP is the usable energy output of cell respiration.");
        assertThat(content.modelUsed()).isEqualTo("gpt-4.1-mini");
        assertThat(content.inputTokens()).isEqualTo(42);
        assertThat(content.outputTokens()).isEqualTo(84);
        assertThat(content.cachedInputTokens()).isEqualTo(7);
    }

    @Test
    void generateStudyPack_includesLearnerAndSubjectSpecificGuidanceInPrompt() throws JsonProcessingException {
        stubResponsesCall();
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(buildValidStudyPackPayload()));

        service.generateStudyPack(
            "Beam design notes",
            new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                "Civil Engineering",
                "Engineering",
                List.of("beams", "load")
            )
        );

        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).body(requestCaptor.capture());
        String requestBody = requestCaptor.getValue();

        assertThat(requestBody).contains("Course / Program: Civil Engineering")
            .contains("Current subject: Engineering")
            .contains("Subject guidance: use a broad academic domain or curriculum category")
            .contains("domain only, no topic suffix");
    }

    @Test
    void generateStudyPack_stripsSubtopicFromCombinedSubject() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildValidStudyPackPayload();
        payload.put("subject", "Criminal Law – Crimes Against Persons");
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

        GeneratedStudyPackContent content = service.generateStudyPack(
            "Crimes against persons notes",
            new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                "Law",
                null,
                List.of("criminal law")
            )
        );

        // Subtopic stripped — domain only
        assertThat(content.subject()).isEqualTo("Criminal Law");
    }

    @Test
    void generateStudyPack_acceptsBroadDomainSubjects() throws JsonProcessingException {
        // Domain-level subjects like "Engineering" and "Medicine" are now valid — no retry needed
        stubResponsesCall();
        for (String domain : List.of("Engineering", "Medicine", "Law", "Business", "Education")) {
            ObjectNode payload = buildValidStudyPackPayload();
            payload.put("subject", domain);
            when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

            GeneratedStudyPackContent content = service.generateStudyPack(
                "Domain notes",
                new StudyPackGenerationContext(LearnerLevel.COLLEGE, null, null, List.of())
            );

            assertThat(content.subject()).isEqualTo(domain);
        }
        // Only one API call per domain — no retry
        verify(responseSpec, times(5)).body(String.class);
    }

    @Test
    void generateStudyPack_throwsConfigurationErrorWhenApiKeyIsMissing() {
        properties.getLlm().getApi().setApiKey(" ");

        StudyPackGenerationContext context = new StudyPackGenerationContext(null, null, null, List.of());
        assertThatThrownBy(() -> service.generateStudyPack(
            "Cell respiration notes",
            context
        ))
            .isInstanceOf(AppException.class)
            .extracting(error -> ((AppException) error).getCode())
            .isEqualTo("LLM_CONFIGURATION_ERROR");

        verifyNoInteractions(restClient);
    }

    @Test
    void generateStudyPack_mapsRestClientResponseExceptionToRequestFailed() {
        stubResponsesCall();
        when(responseSpec.body(String.class)).thenThrow(HttpServerErrorException.create(
            HttpStatus.BAD_GATEWAY,
            "Bad Gateway",
            HttpHeaders.EMPTY,
            """
                {"error":{"message":"upstream unavailable"}}
                """.getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8
        ));

        StudyPackGenerationContext context = new StudyPackGenerationContext(null, null, null, List.of());
        assertThatThrownBy(() -> service.generateStudyPack(
            "Cell respiration notes",
            context
        ))
            .isInstanceOf(AppException.class)
            .satisfies(error -> {
                AppException appException = (AppException) error;
                assertThat(appException.getCode()).isEqualTo("LLM_REQUEST_FAILED");
                assertThat(appException.getMessage()).isEqualTo(
                    "Study pack generation failed. Please try again in a moment.");
            });
    }

    @Test
    void generateNoteFromTopic_usesLearnerContextAndReturnsContent() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("content", "Overview\nNewton's Laws of Motion explain how force and motion relate.");
        when(responseSpec.body(String.class)).thenReturn(generatedQuizResponseJson(payload));

        String content = service.generateNoteFromTopic(
                "Newton's Laws of Motion",
                new StudyPackGenerationContext(
                        LearnerLevel.SENIOR_HIGH,
                        "Senior High – STEM",
                        null,
                        List.of("physics")
                )
        );

        assertThat(content).contains("Newton's Laws of Motion");

        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).body(requestCaptor.capture());
        String requestBody = requestCaptor.getValue();

        assertThat(requestBody).contains("Topic: Newton's Laws of Motion")
                .contains("Learner level: Senior High School")
                .contains("Course / Program: Senior High – STEM")
                .contains("Built for studying, not just exploring information.");
    }

    @Test
    void generateQuickReviewStudyTip_usesOperationSpecificInvalidFormatMessage() {
        stubResponsesCall();
        when(responseSpec.body(String.class)).thenReturn("{invalid");

        List<String> incorrectQuestionSummaries = List.of("Missed ATP question");
        assertThatThrownBy(() -> service.generateQuickReviewStudyTip(incorrectQuestionSummaries))
            .isInstanceOf(AppException.class)
            .satisfies(error -> {
                AppException appException = (AppException) error;
                assertThat(appException.getCode()).isEqualTo("LLM_INVALID_OUTPUT");
                assertThat(appException.getMessage()).isEqualTo(
                    "The study tip service returned an unexpected format. Please try again.");
            });
    }

    @Test
    void generateStudyPack_rejectsDuplicateQuizQuestions() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildValidStudyPackPayload();
        ArrayNode quiz = (ArrayNode) payload.get("quiz");
        ((ObjectNode) quiz.get(1)).put("question", "What is the main goal of cell respiration?");
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

        StudyPackGenerationContext context = new StudyPackGenerationContext(null, null, null, List.of());
        assertThatThrownBy(() -> service.generateStudyPack(
            "Cell respiration notes",
            context
        ))
            .isInstanceOf(AppException.class)
            .satisfies(error -> {
                AppException appException = (AppException) error;
                assertThat(appException.getCode()).isEqualTo("LLM_INVALID_OUTPUT");
                assertThat(appException.getMessage()).isEqualTo(
                    "The study pack service returned repetitive quiz questions. Please try again.");
            });
    }

    // ── Quiz concept edge cases ──────────────────────────────────────────────

    @Test
    void generateStudyPack_repairsOverlongConceptWithFillerPrefix() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildValidStudyPackPayload();
        ((ObjectNode) payload.get("quiz").get(0)).put("concept", "Relationship between voltage and current");
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

        GeneratedStudyPackContent content = service.generateStudyPack(
            "Physics notes on electricity",
            new StudyPackGenerationContext(LearnerLevel.COLLEGE, null, null, List.of())
        );

        assertThat(content.quiz().getFirst().concept()).isEqualTo("voltage and current");
    }

    @Test
    void generateStudyPack_repairsOverlongConceptByTruncating() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildValidStudyPackPayload();
        ((ObjectNode) payload.get("quiz").get(0)).put("concept", "Electrical power using Ohms Law");
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

        GeneratedStudyPackContent content = service.generateStudyPack(
            "Ohm's Law notes",
            new StudyPackGenerationContext(LearnerLevel.COLLEGE, null, null, List.of())
        );

        assertThat(content.quiz().getFirst().concept()).isEqualTo("Electrical power using Ohms");
    }

    @Test
    void generateStudyPack_rejectsNullConcept() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildValidStudyPackPayload();
        ((ObjectNode) payload.get("quiz").get(0)).putNull("concept");
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

        StudyPackGenerationContext context = new StudyPackGenerationContext(null, null, null, List.of());
        assertThatThrownBy(() -> service.generateStudyPack(
            "Cell respiration notes",
            context
        ))
            .isInstanceOf(AppException.class)
            .extracting(e -> ((AppException) e).getCode())
            .isEqualTo("LLM_INVALID_OUTPUT");
    }

    @Test
    void generateStudyPack_rejectsWhitespaceOnlyConcept() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildValidStudyPackPayload();
        ((ObjectNode) payload.get("quiz").get(0)).put("concept", "   ");
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

        StudyPackGenerationContext context = new StudyPackGenerationContext(null, null, null, List.of());
        assertThatThrownBy(() -> service.generateStudyPack(
            "Cell respiration notes",
            context
        ))
            .isInstanceOf(AppException.class)
            .extracting(e -> ((AppException) e).getCode())
            .isEqualTo("LLM_INVALID_OUTPUT");
    }

    @Test
    void generateStudyPack_normalizesRepeatedSpacesInConcept() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildValidStudyPackPayload();
        ((ObjectNode) payload.get("quiz").get(0)).put("concept", "ATP  production");
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

        GeneratedStudyPackContent content = service.generateStudyPack(
            "Cell respiration notes",
            new StudyPackGenerationContext(LearnerLevel.COLLEGE, null, null, List.of())
        );

        assertThat(content.quiz().getFirst().concept()).isEqualTo("ATP production");
    }

    @Test
    void generateStudyPack_acceptsShortValidConcepts() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildValidStudyPackPayload();
        ((ObjectNode) payload.get("quiz").get(0)).put("concept", "Ohm's Law");
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

        GeneratedStudyPackContent content = service.generateStudyPack(
            "Electrical circuit notes",
            new StudyPackGenerationContext(LearnerLevel.COLLEGE, null, null, List.of())
        );

        assertThat(content.quiz().getFirst().concept()).isEqualTo("Ohm's Law");
    }

    // ── Subject edge cases ───────────────────────────────────────────────────

    @Test
    void generateStudyPack_stripsSubtopicFromOverlongCombinedSubject() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildValidStudyPackPayload();
        payload.put("subject", "Electrical Engineering – Voltage Current Resistance and Power");
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

        GeneratedStudyPackContent content = service.generateStudyPack(
            "Electrical engineering notes",
            new StudyPackGenerationContext(LearnerLevel.COLLEGE, null, null, List.of())
        );

        // Subtopic stripped — domain only
        assertThat(content.subject()).isEqualTo("Electrical Engineering");
    }

    @Test
    void generateStudyPack_rejectsEmptySubject() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildValidStudyPackPayload();
        payload.put("subject", "   ");
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

        StudyPackGenerationContext context = new StudyPackGenerationContext(null, null, null, List.of());
        assertThatThrownBy(() -> service.generateStudyPack(
            "Cell respiration notes",
            context
        ))
            .isInstanceOf(AppException.class)
            .extracting(e -> ((AppException) e).getCode())
            .isEqualTo("LLM_INVALID_OUTPUT");
    }

    @Test
    void generateStudyPack_stripsCombinedSubjectToDomainOnly() throws JsonProcessingException {
        stubResponsesCall();
        for (String[] pair : new String[][]{
            {"Electrical Engineering – Ohm's Law", "Electrical Engineering"},
            {"Mathematics – Calculus", "Mathematics"},
            {"Physics – Electrical Power", "Physics"},
            {"Electrical Engineering – Circuit Fundamentals", "Electrical Engineering"}
        }) {
            ObjectNode payload = buildValidStudyPackPayload();
            payload.put("subject", pair[0]);
            when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

            GeneratedStudyPackContent content = service.generateStudyPack(
                "Technical notes",
                new StudyPackGenerationContext(LearnerLevel.COLLEGE, null, null, List.of())
            );

            assertThat(content.subject()).isEqualTo(pair[1]);
        }
    }

    @Test
    void generateStudyPack_ohmsLawRegressionScenario() throws JsonProcessingException {
        // Regression: technical notes like Ohm's Law should not fail due to metadata drift
        stubResponsesCall();
        ObjectNode payload = buildValidStudyPackPayload();
        payload.put("title", "Ohm's Law Study Pack");
        payload.put("summary", "Ohm's Law states V equals IR where V is voltage, I is current, and R is resistance.");
        payload.put("subject", "Electrical Engineering – Ohm's Law");
        ArrayNode tags = objectMapper.createArrayNode();
        tags.add("voltage");
        tags.add("current");
        tags.add("resistance");
        payload.set("tags", tags);
        ArrayNode quiz = (ArrayNode) payload.get("quiz");
        ((ObjectNode) quiz.get(0)).put("concept", "Ohm's Law");
        ((ObjectNode) quiz.get(1)).put("concept", "Voltage");
        ((ObjectNode) quiz.get(2)).put("concept", "Current");
        ((ObjectNode) quiz.get(3)).put("concept", "Resistance");
        ((ObjectNode) quiz.get(4)).put("concept", "Power formula");
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

        GeneratedStudyPackContent content = service.generateStudyPack(
            "Ohm's Law: V = IR. R is resistance in ohms. I is current in amperes. V is voltage in volts.",
            new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                "Electrical Engineering",
                "Electrical Engineering – Ohm's Law",
                List.of("voltage", "current", "resistance")
            )
        );

        // Subtopic stripped — "Electrical Engineering – Ohm's Law" → "Electrical Engineering"
        assertThat(content.subject()).isEqualTo("Electrical Engineering");
        assertThat(content.quiz()).hasSize(5);
        assertThat(content.quiz().get(0).concept()).isEqualTo("Ohm's Law");
        assertThat(content.quiz().get(4).concept()).isEqualTo("Power formula");
    }

    @Test
    void generateChallengeQuiz_mapsAnswerLetterAndExplanation() throws JsonProcessingException {
        stubResponsesCall();
        when(responseSpec.body(String.class)).thenReturn(generatedQuizResponseJson(buildGeneratedQuizPayload()));

        List<QuizItem> quizItems = service.generateChallengeQuiz(
            "Cell Respiration Review",
            "Cell respiration summary",
            List.of("ATP production"),
            List.of("What is the main goal of cell respiration?"),
            2,
            "hard",
            new StudyPackGenerationContext(
                LearnerLevel.BOARD_EXAM_REVIEW,
                "Biology",
                "Biology",
                List.of("cells", "respiration")
            )
        );

        assertThat(quizItems).hasSize(2);
        assertThat(quizItems.getFirst().answer()).isEqualTo("Electron transport chain");
        assertThat(quizItems.getFirst().concept()).isEqualTo("ATP production");
        assertThat(quizItems.getFirst().explanation()).isEqualTo(
            "The electron transport chain produces most ATP during aerobic respiration.");
    }

    @Test
    void generateChallengeQuiz_allowsDistinctMathExpressionChoices() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildGeneratedQuizPayload();
        ArrayNode questions = (ArrayNode) payload.get("questions");
        questions.set(0, generatedQuizItem(
            "What is the derivative of uv?",
            List.of("u'v + uv'", "u'v - uv'", "(u/v)^2", "uv' - u'v"),
            "A",
            "Use the product rule: d(uv)/dx = u'v + uv'.",
            "Product rule"
        ));
        when(responseSpec.body(String.class)).thenReturn(generatedQuizResponseJson(payload));

        List<QuizItem> quizItems = service.generateChallengeQuiz(
            "Calculus Review",
            "Derivative rules",
            List.of("Product rule"),
            List.of(),
            2,
            "medium",
            new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                "Engineering",
                "Calculus",
                List.of("derivatives")
            )
        );

        assertThat(quizItems).hasSize(2);
        assertThat(quizItems.getFirst().choices())
            .containsExactly("u'v + uv'", "u'v - uv'", "(u/v)^2", "uv' - u'v");
    }

    @Test
    void generateChallengeQuiz_retriesOnceWhenFirstPayloadHasInvalidChoices() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode invalidPayload = buildGeneratedQuizPayload();
        ArrayNode invalidQuestions = (ArrayNode) invalidPayload.get("questions");
        invalidQuestions.set(0, generatedQuizItem(
            "What is the derivative of uv?",
            List.of("Derivative", "Derivative ", "Integral", "Limit"),
            "A",
            "Use the product rule.",
            "Product rule"
        ));
        when(responseSpec.body(String.class)).thenReturn(
            generatedQuizResponseJson(invalidPayload),
            generatedQuizResponseJson(buildGeneratedQuizPayload())
        );

        List<QuizItem> quizItems = service.generateChallengeQuiz(
            "Calculus Review",
            "Derivative rules",
            List.of("Product rule"),
            List.of(),
            2,
            "medium",
            new StudyPackGenerationContext(LearnerLevel.COLLEGE, null, "Calculus", List.of("derivatives"))
        );

        assertThat(quizItems).hasSize(2);
        verify(responseSpec, times(2)).body(String.class);
    }

    @Test
    void generateChallengeQuiz_failsAfterSecondInvalidPayload() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode invalidPayload = buildGeneratedQuizPayload();
        ArrayNode invalidQuestions = (ArrayNode) invalidPayload.get("questions");
        invalidQuestions.set(0, generatedQuizItem(
            "What is the derivative of uv?",
            List.of("Derivative", "Derivative ", "Integral", "Limit"),
            "A",
            "Use the product rule.",
            "Product rule"
        ));
        when(responseSpec.body(String.class)).thenReturn(
            generatedQuizResponseJson(invalidPayload),
            generatedQuizResponseJson(invalidPayload)
        );

        List<String> keyConcepts = List.of("Product rule");
        List<String> disallowed = List.of();
        StudyPackGenerationContext context = new StudyPackGenerationContext(LearnerLevel.COLLEGE,
            null,
            "Calculus",
            List.of("derivatives"));
        assertThatThrownBy(() -> service.generateChallengeQuiz(
            "Calculus Review",
            "Derivative rules",
            keyConcepts,
            disallowed,
            2,
            "medium",
            context
        ))
            .isInstanceOf(AppException.class)
            .satisfies(error -> {
                AppException appException = (AppException) error;
                assertThat(appException.getCode()).isEqualTo("LLM_INVALID_OUTPUT");
                assertThat(appException.getMessage()).isEqualTo(
                    "Challenge quiz generation returned invalid choices. Please try again.");
            });
        verify(responseSpec, times(2)).body(String.class);
    }

    @Test
    void generateChallengeQuiz_rejectsThreeChoices() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildGeneratedQuizPayload();
        ArrayNode questions = (ArrayNode) payload.get("questions");
        questions.set(0, generatedQuizItem(
            "What is the derivative of uv?",
            List.of("u'v + uv'", "u'v - uv'", "(u/v)^2"),
            "A",
            "Use the product rule.",
            "Product rule"
        ));
        when(responseSpec.body(String.class)).thenReturn(
            generatedQuizResponseJson(payload),
            generatedQuizResponseJson(payload)
        );

        List<String> keyConcepts = List.of("Product rule");
        List<String> disallowed = List.of();
        StudyPackGenerationContext context = new StudyPackGenerationContext(LearnerLevel.COLLEGE,
            null,
            "Calculus",
            List.of("derivatives"));
        assertThatThrownBy(() -> service.generateChallengeQuiz(
            "Calculus Review",
            "Derivative rules",
            keyConcepts,
            disallowed,
            2,
            "medium",
            context
        ))
            .isInstanceOf(AppException.class)
            .extracting(error -> ((AppException) error).getCode())
            .isEqualTo("LLM_INVALID_OUTPUT");
    }

    @Test
    void generateChallengeQuiz_rejectsInvalidAnswerLetter() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildGeneratedQuizPayload();
        ArrayNode questions = (ArrayNode) payload.get("questions");
        ((ObjectNode) questions.get(0)).put("answer", "E");
        when(responseSpec.body(String.class)).thenReturn(
            generatedQuizResponseJson(payload),
            generatedQuizResponseJson(payload)
        );

        List<String> keyConcepts = List.of("ATP production");
        List<String> disallowed = List.of();
        StudyPackGenerationContext context = new StudyPackGenerationContext(LearnerLevel.COLLEGE,
            null,
            "Biology",
            List.of("cells"));
        assertThatThrownBy(() -> service.generateChallengeQuiz(
            "Cell Respiration Review",
            "Cell respiration summary",
            keyConcepts,
            disallowed,
            2,
            "hard",
            context
        ))
            .isInstanceOf(AppException.class)
            .extracting(error -> ((AppException) error).getCode())
            .isEqualTo("LLM_INVALID_OUTPUT");
    }

    @Test
    void generateChallengeQuiz_rejectsEmptyExplanation() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildGeneratedQuizPayload();
        ArrayNode questions = (ArrayNode) payload.get("questions");
        ((ObjectNode) questions.get(0)).put("explanation", " ");
        when(responseSpec.body(String.class)).thenReturn(
            generatedQuizResponseJson(payload),
            generatedQuizResponseJson(payload)
        );

        List<String> keyConcepts = List.of("ATP production");
        List<String> disallowed = List.of();
        StudyPackGenerationContext context = new StudyPackGenerationContext(LearnerLevel.COLLEGE,
            null,
            "Biology",
            List.of("cells"));
        assertThatThrownBy(() -> service.generateChallengeQuiz(
            "Cell Respiration Review",
            "Cell respiration summary",
            keyConcepts,
            disallowed,
            2,
            "hard",
            context
        ))
            .isInstanceOf(AppException.class)
            .extracting(error -> ((AppException) error).getCode())
            .isEqualTo("LLM_INVALID_OUTPUT");
    }

    @Test
    void generateChallengeQuiz_rejectsEmptyConcept() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildGeneratedQuizPayload();
        ArrayNode questions = (ArrayNode) payload.get("questions");
        ((ObjectNode) questions.get(0)).put("concept", " ");
        when(responseSpec.body(String.class)).thenReturn(
            generatedQuizResponseJson(payload),
            generatedQuizResponseJson(payload)
        );

        List<String> keyConcepts = List.of("ATP production");
        List<String> disallowed = List.of();
        StudyPackGenerationContext context = new StudyPackGenerationContext(LearnerLevel.COLLEGE,
            null,
            "Biology",
            List.of("cells"));
        assertThatThrownBy(() -> service.generateChallengeQuiz(
            "Cell Respiration Review",
            "Cell respiration summary",
            keyConcepts,
            disallowed,
            2,
            "hard",
            context
        ))
            .isInstanceOf(AppException.class)
            .extracting(error -> ((AppException) error).getCode())
            .isEqualTo("LLM_INVALID_OUTPUT");
    }

    private String studyPackResponseJson(ObjectNode payload) throws JsonProcessingException {
        ObjectNode responseJson = objectMapper.createObjectNode();
        responseJson.put("model", "gpt-4.1-mini");
        responseJson.put("output_text", objectMapper.writeValueAsString(payload));
        ObjectNode usage = responseJson.putObject("usage");
        usage.put("input_tokens", 42);
        usage.put("output_tokens", 84);
        usage.putObject("input_tokens_details").put("cached_tokens", 7);
        return objectMapper.writeValueAsString(responseJson);
    }

    private String generatedQuizResponseJson(ObjectNode payload) throws JsonProcessingException {
        ObjectNode responseJson = objectMapper.createObjectNode();
        responseJson.put("model", "gpt-4.1-mini");
        responseJson.put("output_text", objectMapper.writeValueAsString(payload));
        return objectMapper.writeValueAsString(responseJson);
    }

    private void stubResponsesCall() {
        when(restClient.post()).thenReturn(requestSpec);
        when(requestSpec.uri("/responses")).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
    }

    private ObjectNode buildValidStudyPackPayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", "Cell Respiration Review");
        payload.put("summary", "Cell respiration turns glucose into ATP through glycolysis and aerobic pathways.");
        payload.put("subject", "Biology");

        ArrayNode tags = payload.putArray("tags");
        tags.add("cells");
        tags.add("energy");
        tags.add("respiration");

        ArrayNode keyConcepts = payload.putArray("keyConcepts");
        keyConcepts.add("Glycolysis");
        keyConcepts.add("Pyruvate oxidation");
        keyConcepts.add("Citric acid cycle");
        keyConcepts.add("Electron transport chain");
        keyConcepts.add("ATP synthesis");
        keyConcepts.add("NADH");
        keyConcepts.add("Oxygen");
        keyConcepts.add("Mitochondria");

        ArrayNode quiz = payload.putArray("quiz");
        quiz.add(promptQuizItem(
            "What is the main goal of cell respiration?",
            List.of("Store oxygen", "Produce ATP", "Build proteins", "Copy DNA"),
            "B",
            "ATP production"
        ));
        quiz.add(promptQuizItem(
            "Which stage produces the most ATP?",
            List.of("Glycolysis", "Citric acid cycle", "Electron transport chain", "Fermentation"),
            "C",
            "Electron transport chain"
        ));
        quiz.add(promptQuizItem(
            "Where does glycolysis happen?",
            List.of("Nucleus", "Cytoplasm", "Mitochondria", "Ribosome"),
            "B",
            "Glycolysis"
        ));
        quiz.add(promptQuizItem(
            "What molecule carries electrons to the ETC?",
            List.of("ATP", "DNA", "NADH", "Water"),
            "C",
            "NADH"
        ));
        quiz.add(promptQuizItem(
            "What final molecule accepts electrons in aerobic respiration?",
            List.of("Carbon dioxide", "Glucose", "Oxygen", "Pyruvate"),
            "C",
            "Oxygen"
        ));
        return payload;
    }

    private ObjectNode buildGeneratedQuizPayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode questions = payload.putArray("questions");
        questions.add(generatedQuizItem(
            "Which stage generates the most ATP in aerobic respiration?",
            List.of("Glycolysis", "Citric acid cycle", "Electron transport chain", "Fermentation"),
            "C",
            "The electron transport chain produces most ATP during aerobic respiration.",
            "ATP production"
        ));
        questions.add(generatedQuizItem(
            "What is the immediate product of glycolysis before aerobic processing continues?",
            List.of("Acetyl-CoA", "Pyruvate", "Carbon dioxide", "Water"),
            "B",
            "Glycolysis ends with pyruvate, which is then processed further when oxygen is available.",
            "Glycolysis"
        ));
        return payload;
    }

    private ObjectNode promptQuizItem(String question, List<String> choices, String answer, String concept) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("question", question);
        ArrayNode choiceArray = item.putArray("choices");
        choices.forEach(choiceArray::add);
        item.put("answer", answer);
        item.put("concept", concept);
        item.put("explanation", question.equals("What is the main goal of cell respiration?")
            ? "ATP is the usable energy output of cell respiration."
            : "This question checks the " + concept + " concept.");
        return item;
    }

    private ObjectNode generatedQuizItem(String question, List<String> choices, String answer, String explanation,
        String concept) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("question", question);
        ArrayNode choiceArray = item.putArray("choices");
        choices.forEach(choiceArray::add);
        item.put("answer", answer);
        item.put("explanation", explanation);
        item.put("concept", concept);
        return item;
    }
}
