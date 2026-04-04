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
                        "Challenge quiz system prompt",
                        "Challenge quiz developer prompt for {QUESTION_COUNT} at {DIFFICULTY} for {LEARNER_LEVEL}. {LEARNER_LEVEL_GUIDANCE} {COMPUTATION_GUIDANCE} {TIME_EXPECTATION}",
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
        assertThat(content.summary()).isEqualTo("Cell respiration turns glucose into ATP through glycolysis and aerobic pathways.");
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
        assertThat(content.quiz().getFirst().explanation()).isEqualTo("ATP is the usable energy output of cell respiration.");
        assertThat(content.modelUsed()).isEqualTo("gpt-4.1-mini");
        assertThat(content.inputTokens()).isEqualTo(42);
        assertThat(content.outputTokens()).isEqualTo(84);
        assertThat(content.cachedInputTokens()).isEqualTo(7);
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
                    assertThat(appException.getMessage()).isEqualTo("Study pack generation failed. Please try again in a moment.");
                });
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
                    assertThat(appException.getMessage()).isEqualTo("The study tip service returned an unexpected format. Please try again.");
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
                    assertThat(appException.getMessage()).isEqualTo("The study pack service returned repetitive quiz questions. Please try again.");
                });
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
        assertThat(quizItems.getFirst().explanation()).isEqualTo("The electron transport chain produces most ATP during aerobic respiration.");
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

    private ObjectNode generatedQuizItem(String question, List<String> choices, String answer, String explanation, String concept) {
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
