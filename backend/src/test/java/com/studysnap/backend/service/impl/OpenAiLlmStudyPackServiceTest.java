package com.studysnap.backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.config.OpenAiPromptResources;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.dto.CompanionMentorTipAction;
import com.studysnap.backend.dto.CompanionSection;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.service.model.GeneratedChallengeQuizContent;
import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import com.studysnap.backend.service.model.CompanionGenerationContext;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiLlmStudyPackServiceTest {
    private static final String COLLEGE_CURRICULUM_LINE = "Curriculum level: College";
    private static final String DOMAIN_LINE_PREFIX = "Domain:";
    private static final String CURRICULUM_LINE_PREFIX = "Curriculum level:";
    private static final String ENGINEERING_MATHEMATICS_LABEL = "Engineering Mathematics";
    private static final String READER_SCAFFOLDING_PREFIX = "Reader scaffolding:";

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
        properties.getSettings().setModelPremium("gpt-4.1");

        objectMapper = new ObjectMapper();
        service = new OpenAiLlmStudyPackService(
            properties,
            objectMapper,
            restClient,
            new OpenAiPromptResources(
                "System prompt",
                "Developer prompt with {QUIZ_COUNT} questions. Use Domain for terminology and Note learner level for depth. {TRUE_FALSE_GUIDANCE} {COMPUTATION_GUIDANCE} {TIME_EXPECTATION}",
                objectMapper.createObjectNode(),
                "Note generation system prompt",
                "Note generation developer prompt. Use Domain for terminology and Note learner level for depth. Built for studying, not just exploring information. Max {MAX_WORDS} words. Quick Recall bullets: at or under {MAX_ITEM_CHARS} characters.",
                "Companion system prompt",
                "Companion developer prompt {REQUESTED_SECTIONS} {COLLECTION_TITLE} {COLLECTION_DESCRIPTION} {COURSE_PROGRAM} {STRUCTURE_CONTEXT}",
                "Challenge quiz system prompt",
                "Challenge quiz developer prompt for {QUESTION_COUNT} at {DIFFICULTY} for {LEARNER_LEVEL}. {LEARNER_LEVEL_GUIDANCE} {TRUE_FALSE_GUIDANCE} {COMPUTATION_GUIDANCE} {TIME_EXPECTATION}",
                "Board exam system prompt",
                "Board exam developer prompt for {QUESTION_COUNT} at {DIFFICULTY} for {LEARNER_LEVEL}. {LEARNER_LEVEL_GUIDANCE} {COMPUTATION_GUIDANCE} {TIME_EXPECTATION}",
                "Teacher quiz system prompt",
                "Teacher quiz developer prompt for {QUESTION_COUNT} for {LEARNER_LEVEL}. {LEARNER_LEVEL_GUIDANCE} {TRUE_FALSE_GUIDANCE} {COMPUTATION_GUIDANCE}",
                "Adaptive practice system prompt",
                "Adaptive practice developer prompt for {QUESTION_COUNT} for {LEARNER_LEVEL}. {LEARNER_LEVEL_GUIDANCE} {TRUE_FALSE_GUIDANCE} {COMPUTATION_GUIDANCE} {TIME_EXPECTATION}",
                "Interview practice system prompt",
                "Interview practice developer prompt for {QUESTION_COUNT} for {LEARNER_LEVEL}. {LEARNER_LEVEL_GUIDANCE} {COMPUTATION_GUIDANCE}",
                "Interview critique system prompt",
                "Interview critique developer prompt {QUESTION} {CHOICES} {SELECTED_CHOICE} {CORRECT_CHOICE} {CONCEPT} {EXPLANATION}",
                "Ask Companion system prompt",
                "Ask Companion developer prompt {COMPANION_CONTENT}",
                "Long exam system prompt",
                "Long exam developer prompt for {QUESTION_COUNT}. {BATCH_HINT} at {DIFFICULTY} for {LEARNER_LEVEL}. {LEARNER_LEVEL_GUIDANCE} {TRUE_FALSE_GUIDANCE} {COMPUTATION_GUIDANCE} {TIME_EXPECTATION}"
            )
        );

    }

    @Test
    void buildComputationGuidance_returnsEmptyForNonQuantitativeContext() throws Exception {
        String guidance = invokeBuildComputationGuidance(false, "CHALLENGE");

        assertThat(guidance).isEmpty();
    }

    @Test
    void buildComputationGuidance_excludesSchemaExtensionForBoardExam() throws Exception {
        String guidance = invokeBuildComputationGuidance(true, "BOARD_EXAM");

        assertThat(guidance)
                .contains("The material appears quantitative.")
                .doesNotContain("questionType")
                .doesNotContain("workingSolution");
    }

    @Test
    void buildComputationGuidance_includesSchemaExtensionForChallengeQuiz() throws Exception {
        String guidance = invokeBuildComputationGuidance(true, "CHALLENGE");

        assertThat(guidance)
                .contains("questionType")
                .contains("workingSolution")
                .contains("\"COMPUTATIONAL\"")
                .contains("\"CONCEPTUAL\"");
    }

    @Test
    void buildTrueFalseGuidance_returnsEmptyWhenNotAllowed() throws Exception {
        String guidance = invokeBuildTrueFalseGuidance(false);

        assertThat(guidance).isEmpty();
    }

    @Test
    void buildTrueFalseGuidance_includesTrueFalseSchemaInstructionWhenAllowed() throws Exception {
        String guidance = invokeBuildTrueFalseGuidance(true);

        assertThat(guidance)
                .contains("questionFormat")
                .contains("\"TRUE_FALSE\"")
                .contains("[\"True\", \"False\"]")
                .contains("at most 25% True/False")
                .contains("single declarative statement")
                .contains("Which is correct?")
                .contains("Both statements are correct")
                .contains("Only Statement 1 is correct")
                .contains("Only Statement 2 is correct")
                .contains("Neither statement is correct");
    }

    @Test
    void buildGeneratedQuizSchema_includesRequiredKeyConceptEnumWhenProvided() throws Exception {
        JsonNode schema = invokeBuildGeneratedQuizSchema(
                2,
                true,
                List.of("ATP synthesis", "Electron transport chain"),
                false
        );

        JsonNode item = schema.path("properties").path("questions").path("items");
        assertThat(jsonArrayValues(item.path("required")))
                .contains("keyConcept");
        assertThat(jsonArrayValues(item.path("properties").path("keyConcept").path("enum")))
                .containsExactly("ATP synthesis", "Electron transport chain");
    }

    @Test
    void buildGeneratedQuizSchema_omitsKeyConceptWhenEnumMissing() throws Exception {
        JsonNode schema = invokeBuildGeneratedQuizSchema(2, true, List.of(), false);

        JsonNode item = schema.path("properties").path("questions").path("items");
        assertThat(jsonArrayValues(item.path("required")))
                .doesNotContain("keyConcept");
        assertThat(item.path("properties").has("keyConcept")).isFalse();
    }

    @Test
    void buildGeneratedQuizSchema_allowsIdentificationOnlyWhenRequested() throws Exception {
        JsonNode schema = invokeBuildGeneratedQuizSchema(2, true, List.of(), true);

        JsonNode item = schema.path("properties").path("questions").path("items");
        JsonNode itemProps = item.path("properties");
        assertThat(jsonArrayValues(item.path("required")))
                .contains("acceptableAnswers");
        assertThat(jsonArrayValues(itemProps.path("questionFormat").path("enum")))
                .contains("IDENTIFICATION");
        assertThat(jsonArrayValues(itemProps.path("answer").path("type")))
                .containsExactly("string", "null");
        assertThat(itemProps.path("choices").path("minItems").asInt()).isZero();
        assertThat(jsonArrayValues(itemProps.path("acceptableAnswers").path("type")))
                .containsExactly("array", "null");
    }

    @Test
    void buildGeneratedQuizSchema_allowsEnumerationOnlyWhenRequested() throws Exception {
        JsonNode schema = invokeBuildGeneratedQuizSchema(2, true, List.of(), false, true);

        JsonNode item = schema.path("properties").path("questions").path("items");
        JsonNode itemProps = item.path("properties");
        assertThat(jsonArrayValues(item.path("required")))
                .contains("acceptableAnswerGroups");
        assertThat(jsonArrayValues(itemProps.path("questionFormat").path("enum")))
                .contains("ENUMERATION");
        assertThat(jsonArrayValues(itemProps.path("answer").path("type")))
                .containsExactly("string", "null");
        assertThat(itemProps.path("choices").path("minItems").asInt()).isZero();
        assertThat(jsonArrayValues(itemProps.path("acceptableAnswerGroups").path("type")))
                .containsExactly("array", "null");
        assertThat(itemProps.path("acceptableAnswerGroups").path("items").path("type").asText())
                .isEqualTo("array");
        assertThat(itemProps.path("acceptableAnswerGroups").path("items").path("items").path("type").asText())
                .isEqualTo("string");
    }

    @Test
    void buildCompanionSchema_boundsFaqAndAllowsUnrequestedSectionsToBeNull() throws Exception {
        JsonNode schema = invokeBuildCompanionSchema();

        JsonNode properties = schema.path("properties");
        assertThat(jsonArrayValues(properties.path("overview").path("type")))
                .containsExactly("string", "null");
        assertThat(jsonArrayValues(properties.path("faq").path("type")))
                .containsExactly("array", "null");
        assertThat(properties.path("faq").path("minItems").asInt()).isEqualTo(3);
        assertThat(properties.path("faq").path("maxItems").asInt()).isEqualTo(6);
        JsonNode faqItem = properties.path("faq").path("items");
        assertThat(jsonArrayValues(faqItem.path("required")))
                .containsExactly("question", "answer");
        assertThat(jsonArrayValues(properties.path("mentorTips").path("type")))
                .containsExactly("array", "null");
        assertThat(properties.path("mentorTips").path("minItems").asInt()).isEqualTo(1);
        assertThat(properties.path("mentorTips").path("maxItems").asInt()).isEqualTo(3);
        JsonNode mentorTipItem = properties.path("mentorTips").path("items");
        assertThat(jsonArrayValues(mentorTipItem.path("required")))
                .containsExactly("title", "body");
    }

    @Test
    void contentPromptTemplates_splitDomainAndNoteLearnerLevelWithoutReaderPlaceholders() throws IOException {
        for (String resourcePath : List.of(
                "prompts/study-pack-v1/note-generation-developer.txt",
                "prompts/study-pack-v1/developer.txt"
        )) {
            String template = new ClassPathResource(resourcePath).getContentAsString(StandardCharsets.UTF_8);

            assertThat(template)
                    .contains("Domain")
                    .contains("Note learner level")
                    .doesNotContain("shared academic level and domain signal")
                    .doesNotContain("{LEARNER_LEVEL}")
                    .doesNotContain("{LEARNER_LEVEL_GUIDANCE}");
        }
    }

    @Test
    void quizAndExamPromptTemplates_labelLearnerPlaceholderAsCurriculumLevel() throws IOException {
        for (String resourcePath : List.of(
                "prompts/study-pack-v1/challenge-quiz-developer.txt",
                "prompts/study-pack-v1/adaptive-practice-developer.txt",
                "prompts/study-pack-v1/long-exam-developer.txt",
                "prompts/study-pack-v1/board-exam-developer.txt",
                "prompts/study-pack-v1/interview-practice-developer.txt",
                "prompts/study-pack-v1/teacher-quiz-developer.txt"
        )) {
            String template = new ClassPathResource(resourcePath).getContentAsString(StandardCharsets.UTF_8);

            assertThat(template)
                    .contains("Curriculum level")
                    .contains("{LEARNER_LEVEL}");
        }

        String longExamSystem = new ClassPathResource(
                "prompts/study-pack-v1/long-exam-system.txt"
        ).getContentAsString(StandardCharsets.UTF_8);
        assertThat(longExamSystem).contains("authoritative curriculum level");
    }

    @Test
    void buildGenerationContextBlock_omitsDomainWhenNothingResolves() throws Exception {
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                null,
                null,
                null,
                List.of(),
                null,
                null
        );

        // Static content (ADR-001 rule 1): no domain resolves and the note claims no level, so emit
        // neither. Defaulting the level to College here would assert a depth the note never claimed —
        // the same "a blank constraint beats a false one" rule the domain line follows.
        assertThat(invokeBuildGenerationContextBlock(context, false))
                .doesNotContain(CURRICULUM_LINE_PREFIX)
                .doesNotContain(DOMAIN_LINE_PREFIX)
                .doesNotContain("Unknown")
                .doesNotContain("Not provided");
        assertThat(invokeBuildGenerationContextBlock(null, false))
                .doesNotContain(CURRICULUM_LINE_PREFIX)
                .doesNotContain(DOMAIN_LINE_PREFIX);

        // Quizzes (ADR-001 rule 2) legitimately fall back note -> reader -> College, so the
        // curriculum level is always present there even when nothing else resolves.
        assertThat(invokeBuildGenerationContextBlock(context, true))
                .contains(COLLEGE_CURRICULUM_LINE)
                .doesNotContain(DOMAIN_LINE_PREFIX);
        assertThat(invokeBuildGenerationContextBlock(null, true))
                .contains(COLLEGE_CURRICULUM_LINE);
    }

    @Test
    void buildGenerationContextBlock_omitsScaffoldingWithoutALowerReader() throws Exception {
        StudyPackGenerationContext noReader = new StudyPackGenerationContext(
                null,
                "General Education",
                null,
                List.of(),
                DomainContext.GENERAL_EDUCATION,
                LearnerLevel.SENIOR_HIGH
        );
        StudyPackGenerationContext equalReader = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                ENGINEERING_MATHEMATICS_LABEL,
                null,
                List.of(),
                DomainContext.ENGINEERING_MATHEMATICS,
                LearnerLevel.COLLEGE
        );

        assertThat(invokeBuildGenerationContextBlock(noReader, true))
                .contains("Curriculum level: Senior High School")
                .doesNotContain(READER_SCAFFOLDING_PREFIX);
        assertThat(invokeBuildGenerationContextBlock(equalReader, true))
                .contains(COLLEGE_CURRICULUM_LINE)
                .doesNotContain(READER_SCAFFOLDING_PREFIX);
    }

    @Test
    void isQuantitativeContext_usesDeclaredDomainPropertyInsteadOfLegacyProgram() throws Exception {
        StudyPackGenerationContext quantitativeDomain = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                "Nursing",
                null,
                List.of(),
                DomainContext.ENGINEERING_MATHEMATICS,
                LearnerLevel.COLLEGE
        );
        StudyPackGenerationContext nonQuantitativeDomain = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                ENGINEERING_MATHEMATICS_LABEL,
                null,
                List.of(),
                DomainContext.GENERAL_EDUCATION,
                LearnerLevel.COLLEGE
        );

        assertThat(invokeIsQuantitativeContext(quantitativeDomain)).isTrue();
        assertThat(invokeIsQuantitativeContext(nonQuantitativeDomain)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = DomainContext.class, names = {
            "ENGINEERING_MATHEMATICS",
            "ENGINEERING_SCIENCES",
            "CIVIL_ENGINEERING"
    })
    void isQuantitativeContext_preservesEveryPreviouslyQuantitativeEngineeringDomain(
            DomainContext domainContext
    ) throws Exception {
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                null,
                null,
                List.of(),
                domainContext,
                LearnerLevel.COLLEGE
        );

        assertThat(invokeIsQuantitativeContext(context)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = DomainContext.class, names = {"ACCOUNTANCY", "NURSING"})
    void declaredQuantitativeDomainsProduceComputationGuidance(DomainContext domainContext) throws Exception {
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                LearnerLevel.BOARD_EXAM_REVIEW,
                null,
                null,
                List.of(),
                domainContext,
                LearnerLevel.BOARD_EXAM_REVIEW
        );

        String guidance = invokeBuildComputationGuidance(
                invokeIsQuantitativeContext(context),
                "QUICK_REVIEW"
        );

        assertThat(guidance).isNotEmpty();
    }

    @Test
    void keywordScanStillRescuesQuantitativeSubjectUnderNonQuantitativeDomain() throws Exception {
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                null,
                "Biostatistics",
                List.of(),
                DomainContext.GENERAL_EDUCATION,
                LearnerLevel.COLLEGE
        );

        assertThat(invokeIsQuantitativeContext(context)).isTrue();
    }

    @Test
    void keywordScanStillUsesCourseProgramWhenDomainContextIsNull() throws Exception {
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                "Electrical Engineering",
                null,
                List.of(),
                null,
                LearnerLevel.COLLEGE
        );

        assertThat(invokeIsQuantitativeContext(context)).isTrue();
    }

    @Test
    void buildSubjectSuggestionGuidance_usesSchoolBranchForSchoolCurriculumLevels() throws Exception {
        for (LearnerLevel schoolLevel : List.of(
                LearnerLevel.GRADE_SCHOOL,
                LearnerLevel.JUNIOR_HIGH,
                LearnerLevel.SENIOR_HIGH
        )) {
            StudyPackGenerationContext context = new StudyPackGenerationContext(
                    LearnerLevel.PROFESSIONAL,
                    ENGINEERING_MATHEMATICS_LABEL,
                    null,
                    List.of(),
                    DomainContext.GENERAL_EDUCATION,
                    schoolLevel
            );

            assertThat(invokeBuildSubjectSuggestionGuidanceBlock(context))
                    .contains("For a school-level curriculum")
                    .contains("Because the note is authored at a school-level curriculum")
                    .doesNotContain("For college, board-review, professional, or personal-learning curricula");
        }
    }

    @Test
    void buildSubjectSuggestionGuidance_isIndependentOfTheReaderLevelWhenTheNoteHasNone() throws Exception {
        // This block is concatenated into the STATIC user prompt, so the reader must not influence it.
        // Before the fix it called effectiveCurriculumLevel, which falls back reader -> COLLEGE, so two
        // users generating from byte-identical notes received different subject guidance. Every existing
        // test supplied a note level, where old and new code agree -- the defect lived entirely in the
        // null case, which is ~every production row.
        String schoolReader = invokeBuildSubjectSuggestionGuidanceBlock(new StudyPackGenerationContext(
                LearnerLevel.GRADE_SCHOOL, "Civil Engineering", null, List.of(), null, null));
        String collegeReader = invokeBuildSubjectSuggestionGuidanceBlock(new StudyPackGenerationContext(
                LearnerLevel.COLLEGE, "Civil Engineering", null, List.of(), null, null));

        assertThat(schoolReader).isEqualTo(collegeReader);
        // With no authored level, neither list may be silently chosen for the note -- emit both.
        assertThat(schoolReader)
                .contains("For a school-level curriculum")
                .contains("For college, board-review, professional, or personal-learning curricula")
                .doesNotContain("Because the note is authored at a school-level curriculum");
    }

    @Test
    void buildSubjectSuggestionGuidance_keepsTheStrandGuardOnTheDomainNotTheReaderLevel() throws Exception {
        // A legacy 'Senior High – STEM' note with no authored level, read by a COLLEGE user, previously
        // lost the strand guard entirely and would echo "STEM" as its subject -- exactly the row class
        // V104/V105 exist to fix. The guard is data-driven and must survive any reader.
        String block = invokeBuildSubjectSuggestionGuidanceBlock(new StudyPackGenerationContext(
                LearnerLevel.COLLEGE, "Senior High – STEM", null, List.of(), null, null));

        assertThat(block).contains("If the domain above is a K-12 strand or track");
    }

    @Test
    void buildSubjectSuggestionGuidance_usesFieldOfStudyBranchOtherwise() throws Exception {
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                LearnerLevel.GRADE_SCHOOL,
                "Senior High – STEM",
                null,
                List.of(),
                DomainContext.ENGINEERING_SCIENCES,
                LearnerLevel.COLLEGE
        );

        assertThat(invokeBuildSubjectSuggestionGuidanceBlock(context))
                .contains("Domain context is: Engineering Sciences")
                .contains("For college, board-review, professional, or personal-learning curricula")
                .doesNotContain("Because the curriculum level is school-level");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String invokeBuildComputationGuidance(boolean quantitativeContext, String quizModeName) throws Exception {
        Class<?> quizModeClass = Class.forName(
                "com.studysnap.backend.service.impl.OpenAiLlmStudyPackService$QuizMode"
        );
        Object quizMode = Enum.valueOf((Class<? extends Enum>) quizModeClass.asSubclass(Enum.class), quizModeName);
        Method method = OpenAiLlmStudyPackService.class.getDeclaredMethod(
                "buildComputationGuidance",
                boolean.class,
                quizModeClass
        );
        method.setAccessible(true);
        return (String) method.invoke(service, quantitativeContext, quizMode);
    }

    private String invokeBuildTrueFalseGuidance(boolean allowTrueFalse) throws Exception {
        Method method = OpenAiLlmStudyPackService.class.getDeclaredMethod(
                "buildTrueFalseGuidance",
                boolean.class
        );
        method.setAccessible(true);
        return (String) method.invoke(service, allowTrueFalse);
    }

    private String invokeBuildGenerationContextBlock(
            StudyPackGenerationContext context,
            boolean includeLearnerLevel
    ) throws Exception {
        Method method = OpenAiLlmStudyPackService.class.getDeclaredMethod(
                "buildGenerationContextBlock",
                StudyPackGenerationContext.class,
                boolean.class
        );
        method.setAccessible(true);
        return (String) method.invoke(service, context, includeLearnerLevel);
    }

    private String invokeBuildSubjectSuggestionGuidanceBlock(StudyPackGenerationContext context) throws Exception {
        Method method = OpenAiLlmStudyPackService.class.getDeclaredMethod(
                "buildSubjectSuggestionGuidanceBlock",
                StudyPackGenerationContext.class
        );
        method.setAccessible(true);
        return (String) method.invoke(service, context);
    }

    private boolean invokeIsQuantitativeContext(StudyPackGenerationContext context) throws Exception {
        Method method = OpenAiLlmStudyPackService.class.getDeclaredMethod(
                "isQuantitativeContext",
                StudyPackGenerationContext.class,
                List.class,
                String.class
        );
        method.setAccessible(true);
        return (boolean) method.invoke(service, context, List.of(), null);
    }

    private JsonNode invokeBuildGeneratedQuizSchema(
            int questionCount,
            boolean allowTrueFalse,
            List<String> keyConceptEnum,
            boolean allowIdentification
    ) throws Exception {
        return invokeBuildGeneratedQuizSchema(questionCount, allowTrueFalse, keyConceptEnum, allowIdentification, false);
    }

    private JsonNode invokeBuildGeneratedQuizSchema(
            int questionCount,
            boolean allowTrueFalse,
            List<String> keyConceptEnum,
            boolean allowIdentification,
            boolean allowEnumeration
    ) throws Exception {
        Method method = OpenAiLlmStudyPackService.class.getDeclaredMethod(
                "buildGeneratedQuizSchema",
                int.class,
                boolean.class,
                List.class,
                boolean.class,
                boolean.class
        );
        method.setAccessible(true);
        return (JsonNode) method.invoke(service, questionCount, allowTrueFalse, keyConceptEnum, allowIdentification, allowEnumeration);
    }

    private JsonNode invokeBuildCompanionSchema() throws Exception {
        Method method = OpenAiLlmStudyPackService.class.getDeclaredMethod("buildCompanionSchema");
        method.setAccessible(true);
        return (JsonNode) method.invoke(service);
    }

    private List<String> jsonArrayValues(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new java.util.ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return values;
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
    void generateStudyPack_acceptsSnakeCaseKeyConceptsAlias() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildValidStudyPackPayload();
        ArrayNode keyConcepts = (ArrayNode) payload.remove("keyConcepts");
        payload.set("key_concepts", keyConcepts);
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

        GeneratedStudyPackContent content = service.generateStudyPack(
                "Cell respiration notes",
                new StudyPackGenerationContext(
                        LearnerLevel.COLLEGE,
                        "Biology",
                        "Biology",
                        List.of("cells", "respiration")
                )
        );

        assertThat(content.keyConcepts()).contains("Glycolysis");
    }

    @Test
    void generateStudyPack_preservesValidMatchingGroup() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildValidStudyPackPayload();
        ArrayNode quiz = (ArrayNode) payload.get("quiz");
        List<String> sharedChoices = List.of(
                "Bernoulli's Principle",
                "Pascal's Law",
                "Archimedes' Principle",
                "Continuity Equation"
        );
        setMatchingItem((ObjectNode) quiz.get(0), sharedChoices, "B", "group-1");
        setMatchingItem((ObjectNode) quiz.get(1), sharedChoices, "C", "group-1");
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

        GeneratedStudyPackContent content = service.generateStudyPack(
                "Fluid mechanics notes",
                new StudyPackGenerationContext(LearnerLevel.COLLEGE, "Engineering", "Physics", List.of())
        );

        assertThat(content.quiz().get(0).questionFormat()).isEqualTo("MATCHING");
        assertThat(content.quiz().get(0).questionGroup()).isEqualTo("group-1");
        assertThat(content.quiz().get(1).questionGroup()).isEqualTo("group-1");
    }

    @Test
    void generateStudyPack_demotesMatchingGroupWithDifferingChoices() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildValidStudyPackPayload();
        ArrayNode quiz = (ArrayNode) payload.get("quiz");
        ((ObjectNode) quiz.get(0)).put("questionFormat", "MATCHING").put("questionGroup", "group-1");
        ((ObjectNode) quiz.get(1)).put("questionFormat", "MATCHING").put("questionGroup", "group-1");
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

        GeneratedStudyPackContent content = service.generateStudyPack(
                "Cell respiration notes",
                new StudyPackGenerationContext(LearnerLevel.COLLEGE, "Biology", "Biology", List.of())
        );

        assertThat(content.quiz().get(0).questionFormat()).isEqualTo("MCQ");
        assertThat(content.quiz().get(0).questionGroup()).isNull();
        assertThat(content.quiz().get(1).questionGroup()).isNull();
    }

    @Test
    void generateStudyPack_demotesMatchingGroupWithDuplicateCorrectIndexes() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildValidStudyPackPayload();
        ArrayNode quiz = (ArrayNode) payload.get("quiz");
        List<String> sharedChoices = List.of(
                "Bernoulli's Principle",
                "Pascal's Law",
                "Archimedes' Principle",
                "Continuity Equation"
        );
        setMatchingItem((ObjectNode) quiz.get(0), sharedChoices, "B", "group-1");
        setMatchingItem((ObjectNode) quiz.get(1), sharedChoices, "B", "group-1");
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

        GeneratedStudyPackContent content = service.generateStudyPack(
                "Fluid mechanics notes",
                new StudyPackGenerationContext(LearnerLevel.COLLEGE, "Engineering", "Physics", List.of())
        );

        assertThat(content.quiz().get(0).questionFormat()).isEqualTo("MCQ");
        assertThat(content.quiz().get(0).questionGroup()).isNull();
        assertThat(content.quiz().get(1).questionGroup()).isNull();
    }

    @Test
    void generateStudyPack_usesNoteDomainAndLevelForStaticContentInsteadOfReaderLevel() throws JsonProcessingException {
        stubResponsesCall();
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(buildValidStudyPackPayload()));

        service.generateStudyPack(
            "Beam design notes",
            new StudyPackGenerationContext(
                LearnerLevel.GRADE_SCHOOL,
                "Civil Engineering",
                "Engineering",
                List.of("beams", "load"),
                DomainContext.CIVIL_ENGINEERING,
                LearnerLevel.BOARD_EXAM_REVIEW
            )
        );

        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).body(requestCaptor.capture());
        String requestBody = requestCaptor.getValue();

        assertThat(requestBody).contains("Domain: Civil Engineering")
            .contains("Use Domain for terminology and Note learner level for depth")
            .contains("Curriculum level: Board Exam Review")
            .contains("Content calibration: use the Domain and Curriculum level above")
            .contains("Current subject: Engineering")
            .contains("Subject guidance: use the specific academic subject or professional discipline")
            .contains("Do not suggest overly broad subjects such as Business, Medicine, Engineering, or Law")
            .contains("label only, no topic suffix")
            .doesNotContain("Grade School")
            .doesNotContain(READER_SCAFFOLDING_PREFIX)
            .doesNotContain("{LEARNER_LEVEL}")
            .doesNotContain("{LEARNER_LEVEL_GUIDANCE}");
    }

    @Test
    void generateStudyPack_omitsCurriculumLevelForStaticContentWhenNoteHasNoAuthoredLevel()
            throws JsonProcessingException {
        stubResponsesCall();
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(buildValidStudyPackPayload()));

        // ADR-001 rule 1: static content is never calibrated by the reader's level. A note with no
        // authored learner level — the state of every note until PR 4's backfill — must emit no
        // curriculum level at all, rather than silently borrowing the reader's.
        service.generateStudyPack(
            "Beam design notes",
            new StudyPackGenerationContext(
                LearnerLevel.GRADE_SCHOOL,
                "Civil Engineering",
                "Engineering",
                List.of("beams", "load"),
                DomainContext.CIVIL_ENGINEERING,
                null
            )
        );

        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).body(requestCaptor.capture());
        String requestBody = requestCaptor.getValue();

        assertThat(requestBody).contains("Domain: Civil Engineering")
            .contains("Content calibration: use the Domain above")
            .contains("This note has no authored learner level, so do not infer one")
            .doesNotContain("Curriculum level:")
            .doesNotContain("Grade School")
            .doesNotContain(READER_SCAFFOLDING_PREFIX);
    }

    @Test
    void generateChallengeQuiz_noteCollegeAndGradeSchoolReaderKeepsCollegeWithScaffolding()
            throws JsonProcessingException {
        stubResponsesCall();
        when(responseSpec.body(String.class)).thenReturn(generatedQuizResponseJson(buildGeneratedQuizPayload()));

        service.generateChallengeQuiz(
                "Engineering Algebra",
                "Algebra summary",
                List.of("Linear equations"),
                List.of(),
                2,
                "medium",
                new StudyPackGenerationContext(
                        LearnerLevel.GRADE_SCHOOL,
                        "Civil Engineering",
                        "Mathematics",
                        List.of("algebra"),
                        DomainContext.ENGINEERING_MATHEMATICS,
                        LearnerLevel.COLLEGE
                )
        );

        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).body(requestCaptor.capture());
        assertThat(requestCaptor.getValue())
                .contains("Domain: Engineering Mathematics")
                .contains(COLLEGE_CURRICULUM_LINE)
                .contains("for College")
                .contains("Reader scaffolding: the reader's level is Grade School, below the note's College level")
                .contains("do not lower the curriculum, terminology, or difficulty below the note's level")
                .doesNotContain("Curriculum level: Grade School");
    }

    @Test
    void generateChallengeQuiz_noteJuniorHighAndProfessionalReaderDoesNotEscalateDifficulty()
            throws JsonProcessingException {
        stubResponsesCall();
        when(responseSpec.body(String.class)).thenReturn(generatedQuizResponseJson(buildGeneratedQuizPayload()));

        service.generateChallengeQuiz(
                "School Science",
                "Science summary",
                List.of("Matter"),
                List.of(),
                2,
                "medium",
                new StudyPackGenerationContext(
                        LearnerLevel.PROFESSIONAL,
                        "Professional Practice",
                        "Science",
                        List.of(),
                        DomainContext.GENERAL_EDUCATION,
                        LearnerLevel.JUNIOR_HIGH
                )
        );

        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).body(requestCaptor.capture());
        assertThat(requestCaptor.getValue())
                .contains("Curriculum level: Junior High School")
                .contains("for Junior High School")
                .doesNotContain(READER_SCAFFOLDING_PREFIX)
                .doesNotContain("Curriculum level: Professional")
                .doesNotContain("for Professional");
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
    void generateStudyPack_ignoresOverlyBroadSubjectSuggestions() throws JsonProcessingException {
        stubResponsesCall();
        for (String domain : List.of("Engineering", "Medicine", "Law", "Business", "Education")) {
            ObjectNode payload = buildValidStudyPackPayload();
            payload.put("subject", domain);
            when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

            GeneratedStudyPackContent content = service.generateStudyPack(
                "Domain notes",
                new StudyPackGenerationContext(LearnerLevel.COLLEGE, null, null, List.of())
            );

            assertThat(content.subject()).isNull();
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
    void generateNoteFromTopic_acceptsAFormulaQuickRecallItemThatExceedsTheProseWordCeiling() throws JsonProcessingException {
        // Reproduces the reported LLM_INVALID_OUTPUT failure verbatim (Civil Engineering / "Weirs",
        // logged 2026-08-03). This item is 199 characters — valid under the schema — but 38
        // whitespace-delimited "words", which the shared 28-word prose ceiling used to reject.
        // It is a formula followed by definitions of its variables, i.e. correct content.
        String formulaItem = "Discharge formula — Q = (2/3) * C_d * L * sqrt(2g) * H^(3/2) for sharp-crested weirs, "
                + "where Q is flow, C_d is discharge coefficient, L is crest length, g is gravity acceleration, "
                + "H is head over crest";
        assertThat(formulaItem.length()).isLessThanOrEqualTo(240);
        assertThat(formulaItem.split("\\s+")).hasSizeGreaterThan(28);

        stubResponsesCall();
        when(responseSpec.body(String.class))
                .thenReturn(generatedQuizResponseJson(generatedNotePayloadWithQuickRecall(formulaItem)));

        String content = service.generateNoteFromTopic(
                "Weirs",
                new StudyPackGenerationContext(null, "Civil Engineering", null, List.of("hydraulics"))
        );

        assertThat(content).contains("Discharge formula");
    }

    @Test
    void generateNoteFromTopic_rejectsAQuickRecallItemOverTheCharacterCeiling() throws JsonProcessingException {
        // The character bound still has to bite, otherwise dropping the word count would leave
        // Quick Recall unbounded on the backend side.
        String overlong = "Term — " + "x".repeat(240);
        assertThat(overlong.length()).isGreaterThan(240);

        stubResponsesCall();
        when(responseSpec.body(String.class))
                .thenReturn(generatedQuizResponseJson(generatedNotePayloadWithQuickRecall(overlong)));

        assertThatThrownBy(() -> service.generateNoteFromTopic(
                "Weirs",
                new StudyPackGenerationContext(null, "Civil Engineering", null, List.of("hydraulics"))
        )).hasMessageContaining("invalid quick recall content");
    }

    @Test
    void generateNoteFromTopic_stillRejectsAnOverlongCoreDetailsItem() throws JsonProcessingException {
        // coreDetails and whyItMatters are prose and deliberately keep the 28-word ceiling.
        String wordyProse = "word ".repeat(40).trim();
        ObjectNode payload = generatedNotePayloadWithQuickRecall("First Law — law of inertia");
        payload.putArray("coreDetails")
                .add(wordyProse)
                .add("Second Law: net force equals mass times acceleration.")
                .add("Third Law: for every action there is an equal and opposite reaction.");

        stubResponsesCall();
        when(responseSpec.body(String.class)).thenReturn(generatedQuizResponseJson(payload));

        assertThatThrownBy(() -> service.generateNoteFromTopic(
                "Weirs",
                new StudyPackGenerationContext(null, "Civil Engineering", null, List.of("hydraulics"))
        )).isInstanceOf(AppException.class);
    }

    @Test
    void noteGenerationPromptStatesTheQuickRecallCharacterBound() throws JsonProcessingException {
        // The 4-of-5 sampled-failure rate came from the model never being told the bound it was
        // judged against. If this placeholder stops being substituted, the prompt silently ships
        // a literal "{MAX_ITEM_CHARS}" and the bound becomes guesswork again.
        stubResponsesCall();
        when(responseSpec.body(String.class))
                .thenReturn(generatedQuizResponseJson(generatedNotePayloadWithQuickRecall("First Law — law of inertia")));

        service.generateNoteFromTopic(
                "Weirs",
                new StudyPackGenerationContext(null, "Civil Engineering", null, List.of("hydraulics"))
        );

        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).body(requestCaptor.capture());
        assertThat(requestCaptor.getValue())
                .contains("240 characters")
                .doesNotContain("{MAX_ITEM_CHARS}");
    }

    @Test
    void noteGenerationPromptResourceDeclaresTheQuickRecallCharacterPlaceholder() throws Exception {
        // The test above proves substitution works against a stubbed template. This one proves the
        // real prompt actually asks for the bound -- without it, deleting the line from the
        // resource file would leave the model uninstructed and every test still green.
        String template = new String(
                new ClassPathResource("prompts/study-pack-v1/note-generation-developer.txt")
                        .getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );
        assertThat(template).contains("{MAX_ITEM_CHARS}");
    }

    private ObjectNode generatedNotePayloadWithQuickRecall(String firstQuickRecallItem) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", "Discharge Over Sharp-Crested Weirs");
        payload.put("overview", "Weirs are hydraulic structures used to measure and control open-channel flow. Their discharge depends on head and crest geometry.");
        payload.put("keyIdea", "Discharge over a weir varies with the three-halves power of the head.");
        payload.putArray("coreDetails")
                .add("Sharp-crested weirs are common flow-measurement structures.")
                .add("Discharge coefficient depends on crest geometry and approach conditions.")
                .add("Head is measured upstream of the drawdown zone.");
        payload.putArray("whyItMatters")
                .add("Weir sizing governs channel capacity and flood safety.")
                .add("Board exams test discharge computation directly.");
        payload.putArray("quickRecall")
                .add(firstQuickRecallItem)
                .add("Head — depth of flow over the crest")
                .add("Crest length — L in the discharge equation");
        return payload;
    }

    @Test
    void generateNoteFromTopic_usesProgramDomainFallbackAndCollegeLevelFallback() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", "Newton's Laws of Motion");
        payload.put("overview", "Newton's Laws of Motion describe how forces affect the motion of objects. They are the foundation of classical mechanics.");
        payload.put("keyIdea", "A net force is required to change the state of motion of an object.");
        payload.putArray("coreDetails")
                .add("First Law: an object at rest stays at rest unless acted on by a net force.")
                .add("Second Law: net force equals mass times acceleration.")
                .add("Third Law: for every action there is an equal and opposite reaction.");
        payload.putArray("whyItMatters")
                .add("Explains motion in everyday systems: vehicles, sports, machinery.")
                .add("Foundation for engineering, aerospace, and physics.");
        payload.putArray("quickRecall")
                .add("First Law — law of inertia")
                .add("Second Law — F = ma")
                .add("Third Law — equal and opposite reactions");
        when(responseSpec.body(String.class)).thenReturn(generatedQuizResponseJson(payload));

        String content = service.generateNoteFromTopic(
                "Newton's Laws of Motion",
                new StudyPackGenerationContext(
                        null,
                        "Senior High – STEM",
                        null,
                        List.of("physics")
                )
        );

        assertThat(content)
                .contains("Newton's Laws of Motion")
                .contains("📘 Overview")
                .contains("🧠 Key Idea")
                .contains("⚔️ Core Details")
                .contains("🎯 Why It Matters")
                .contains("🧠 Quick Recall");

        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).body(requestCaptor.capture());
        String requestBody = requestCaptor.getValue();

        // Note generation is static content (ADR-001 rule 1): the legacy program supplies the domain,
        // but with no authored note level there is no level signal at all — not a College default and
        // certainly not the reader's level.
        assertThat(requestBody).contains("Topic: Newton's Laws of Motion")
                .contains("Domain: Senior High – STEM")
                .contains("Use Domain for terminology and Note learner level for depth")
                .doesNotContain(CURRICULUM_LINE_PREFIX)
                .contains("Content calibration: use the Domain above")
                .contains("This note has no authored learner level, so do not infer one")
                .doesNotContain("{LEARNER_LEVEL}")
                .doesNotContain("{LEARNER_LEVEL_GUIDANCE}")
                .contains("Built for studying, not just exploring information.");
    }

    @Test
    void generateCompanion_keepsReviewSetCourseProgramAndReturnsOnlyRequestedSections() throws JsonProcessingException {
        stubResponsesCall();
        when(responseSpec.body(String.class)).thenReturn(generatedQuizResponseJson(buildCompanionPayload()));

        CompanionContent content = service.generateCompanion(
                companionContext(),
                Set.of(CompanionSection.OVERVIEW)
        );

        assertThat(content.overview()).isEqualTo("This plan covers cell biology foundations.");
        assertThat(content.studyStrategy()).isNull();
        assertThat(content.commonMistakes()).isNull();
        assertThat(content.resources()).isNull();
        assertThat(content.faq()).isEmpty();
        assertThat(content.mentorTips()).isEmpty();
        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).body(requestCaptor.capture());
        JsonNode request = objectMapper.readTree(requestCaptor.getValue());
        assertThat(request.path("model").asText()).isEqualTo("gpt-4.1");
        assertThat(request.path("text").path("format").path("name").asText()).isEqualTo("note_lib_companion_draft");
        assertThat(request.path("input").toString())
                .contains("OVERVIEW")
                .contains("Cell Biology Plan")
                .contains("Nursing")
                .contains("Subject Plans");
    }

    @Test
    void generateCompanion_retriesOnceWhenRequestedFaqIsOutOfBounds() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode invalidPayload = buildCompanionPayload();
        ArrayNode invalidFaq = invalidPayload.putArray("faq");
        invalidFaq.addObject().put("question", "One?").put("answer", "One.");
        invalidFaq.addObject().put("question", "Two?").put("answer", "Two.");
        when(responseSpec.body(String.class)).thenReturn(
                generatedQuizResponseJson(invalidPayload),
                generatedQuizResponseJson(buildCompanionPayload())
        );

        CompanionContent content = service.generateCompanion(
                companionContext(),
                Set.of(CompanionSection.FAQ)
        );

        assertThat(content.faq()).hasSize(3);
        verify(responseSpec, times(2)).body(String.class);
    }

    @Test
    void generateCompanion_returnsMentorTipsWithoutActionOrSurfacingCondition() throws JsonProcessingException {
        stubResponsesCall();
        when(responseSpec.body(String.class)).thenReturn(generatedQuizResponseJson(buildCompanionPayload()));

        CompanionContent content = service.generateCompanion(
                companionContext(),
                Set.of(CompanionSection.MENTOR_TIPS)
        );

        assertThat(content.mentorTips()).hasSize(1);
        assertThat(content.mentorTips().getFirst().title()).isEqualTo("Use the next action as your anchor");
        assertThat(content.mentorTips().getFirst().linkedAction()).isEqualTo(CompanionMentorTipAction.NONE);
        assertThat(content.mentorTips().getFirst().surfacingCondition()).isNull();
        assertThat(content.overview()).isNull();
        assertThat(content.faq()).isEmpty();
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

    @Test
    void generateStudyPack_retriesWhenTrueFalseQuestionHasMcqIntentStem() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode invalidPayload = buildValidStudyPackPayload();
        ArrayNode invalidQuiz = (ArrayNode) invalidPayload.get("quiz");
        setMalformedTrueFalseItem((ObjectNode) invalidQuiz.get(0));
        when(responseSpec.body(String.class)).thenReturn(
                studyPackResponseJson(invalidPayload),
                studyPackResponseJson(buildValidStudyPackPayload())
        );

        GeneratedStudyPackContent content = service.generateStudyPack(
                "Cell respiration notes",
                new StudyPackGenerationContext(LearnerLevel.COLLEGE, null, "Biology", List.of())
        );

        assertThat(content.quiz()).hasSize(5);
        verify(responseSpec, times(2)).body(String.class);
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
    void generateStudyPack_ignoresEmptySubject() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildValidStudyPackPayload();
        payload.put("subject", "   ");
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

        GeneratedStudyPackContent content = service.generateStudyPack(
            "Cell respiration notes",
            new StudyPackGenerationContext(null, null, null, List.of())
        );

        assertThat(content.subject()).isNull();
    }

    @Test
    void generateStudyPack_acceptsNullSubjectSuggestion() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildValidStudyPackPayload();
        payload.putNull("subject");
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

        GeneratedStudyPackContent content = service.generateStudyPack(
            "Cell respiration notes",
            new StudyPackGenerationContext(null, null, null, List.of())
        );

        assertThat(content.subject()).isNull();
    }

    @Test
    void generateStudyPack_repairsOverlongSubjectWithoutFailing() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildValidStudyPackPayload();
        payload.put("subject", "This Is A Very Long Unclear Subject Label");
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

        GeneratedStudyPackContent content = service.generateStudyPack(
            "Cell respiration notes",
            new StudyPackGenerationContext(null, null, null, List.of())
        );

        assertThat(content.subject()).isEqualTo("This Is A Very Long Unclear");
    }

    @Test
    void generateStudyPack_keepsValidSpecificSubjectSuggestion() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildValidStudyPackPayload();
        payload.put("subject", "Electrical Engineering");
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

        GeneratedStudyPackContent content = service.generateStudyPack(
            "Circuit notes",
            new StudyPackGenerationContext(LearnerLevel.COLLEGE, "Engineering", null, List.of())
        );

        assertThat(content.subject()).isEqualTo("Electrical Engineering");
    }

    @Test
    void generateStudyPack_ignoresDuplicateAndInvalidTagSuggestions() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = buildValidStudyPackPayload();
        ArrayNode tags = objectMapper.createArrayNode();
        tags.add("circuits");
        tags.add("circuits");
        tags.add("Cell Respiration Review");
        tags.add("relationship between voltage and resistance");
        tags.add("Ohm's Law");
        payload.set("tags", tags);
        when(responseSpec.body(String.class)).thenReturn(studyPackResponseJson(payload));

        GeneratedStudyPackContent content = service.generateStudyPack(
            "Circuit notes",
            new StudyPackGenerationContext(null, null, null, List.of())
        );

        assertThat(content.tags()).containsExactly("circuits", "Ohm's Law");
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

        GeneratedChallengeQuizContent generated = service.generateChallengeQuiz(
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
        List<QuizItem> quizItems = generated.quizItems();

        assertThat(quizItems).hasSize(2);
        assertThat(quizItems.getFirst().answer()).isEqualTo("Electron transport chain");
        assertThat(quizItems.getFirst().concept()).isEqualTo("ATP production");
        assertThat(quizItems.getFirst().explanation()).isEqualTo(
            "The electron transport chain produces most ATP during aerobic respiration.");
        assertThat(generated.modelUsed()).isNull();
        assertThat(generated.inputTokens()).isNull();
        assertThat(generated.outputTokens()).isNull();
        assertThat(generated.cachedInputTokens()).isNull();

        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).body(requestCaptor.capture());
        assertThat(requestCaptor.getValue())
                .contains("Curriculum level: Board Exam Review")
                .contains("for Board Exam Review")
                .contains("questionFormat")
                .contains("\"TRUE_FALSE\"")
                .contains("[\\\"True\\\", \\\"False\\\"]")
                .doesNotContain("{LEARNER_LEVEL}");
    }

    @Test
    void generateChallengeQuiz_extractsLlmUsageMetadata() throws JsonProcessingException {
        stubResponsesCall();
        when(responseSpec.body(String.class)).thenReturn(
                generatedQuizResponseJsonWithUsage(buildGeneratedQuizPayload())
        );

        GeneratedChallengeQuizContent generated = service.generateChallengeQuiz(
                "Cell Respiration Review",
                "Cell respiration summary",
                List.of("ATP production"),
                List.of(),
                2,
                "hard",
                new StudyPackGenerationContext(LearnerLevel.COLLEGE, "Biology", "Biology", List.of())
        );

        assertThat(generated.quizItems()).hasSize(2);
        assertThat(generated.modelUsed()).isEqualTo("gpt-4.1-mini");
        assertThat(generated.inputTokens()).isEqualTo(42);
        assertThat(generated.outputTokens()).isEqualTo(84);
        assertThat(generated.cachedInputTokens()).isEqualTo(7);
    }

    @Test
    void generateChallengeQuiz_acceptsTrueFalseQuestionFormat() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode questions = payload.putArray("questions");
        ObjectNode trueFalseQuestion = generatedQuizItem(
                "Ohm's Law states that voltage is directly proportional to current.",
                List.of("True", "False"),
                "A",
                "Ohm's Law relates voltage, current, and resistance.",
                "Ohm's Law"
        );
        trueFalseQuestion.put("questionFormat", "TRUE_FALSE");
        questions.add(trueFalseQuestion);
        when(responseSpec.body(String.class)).thenReturn(generatedQuizResponseJson(payload));

        List<QuizItem> quizItems = service.generateChallengeQuiz(
                "Ohm's Law Review",
                "Ohm's Law summary",
                List.of("Ohm's Law"),
                List.of(),
                1,
                "medium",
                new StudyPackGenerationContext(LearnerLevel.COLLEGE, "Electrical Engineering", "Physics", List.of())
        ).quizItems();

        assertThat(quizItems).hasSize(1);
        assertThat(quizItems.getFirst().questionFormat()).isEqualTo("TRUE_FALSE");
        assertThat(quizItems.getFirst().correctIndex()).isZero();
        assertThat(quizItems.getFirst().choices()).containsExactly("True", "False");
    }

    @Test
    void generateBoardExamQuiz_usesBoardExamPrompts() throws JsonProcessingException {
        stubResponsesCall();
        when(responseSpec.body(String.class)).thenReturn(generatedQuizResponseJson(buildGeneratedQuizPayload()));

        service.generateBoardExamQuiz(
            "Cell Respiration Review",
            "Cell respiration summary",
            List.of("ATP production"),
            List.of("What is the main goal of cell respiration?"),
            2,
            "mixed",
            new StudyPackGenerationContext(LearnerLevel.BOARD_EXAM_REVIEW, "Biology", "Biology", List.of())
        );

        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).body(requestCaptor.capture());
        assertThat(requestCaptor.getValue())
            .contains("Board exam system prompt")
            .contains("Board exam developer prompt")
            .doesNotContain("questionFormat")
            .doesNotContain("TRUE_FALSE")
            .doesNotContain("\"keyConcept\"")
            .doesNotContain("Challenge quiz system prompt")
            .doesNotContain("Challenge quiz developer prompt");
    }

    @Test
    void generateTeacherQuiz_substitutesRequestedQuestionCount() throws JsonProcessingException {
        stubResponsesCall();
        when(responseSpec.body(String.class)).thenReturn(generatedQuizResponseJson(buildGeneratedQuizPayload("Teacher", 30)));

        List<QuizItem> quizItems = service.generateTeacherQuiz(
            "Cell Respiration Review",
            "Cell respiration notes",
            List.of(),
            30,
            new StudyPackGenerationContext(LearnerLevel.COLLEGE, "Biology", "Biology", List.of("cells"))
        );

        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).body(requestCaptor.capture());
        assertThat(quizItems).hasSize(30);
        assertThat(requestCaptor.getValue())
            .contains("Teacher quiz system prompt")
            .contains("Teacher quiz developer prompt for 30")
            .doesNotContain("{QUESTION_COUNT}");
    }

    @Test
    void generateTeacherQuiz_explicitSeniorHighCurriculumHasNoReaderScaffolding() throws JsonProcessingException {
        stubResponsesCall();
        when(responseSpec.body(String.class)).thenReturn(
                generatedQuizResponseJson(buildGeneratedQuizPayload("Teacher", 10))
        );

        service.generateTeacherQuiz(
                "Engineering Algebra",
                "Algebra notes",
                List.of(),
                10,
                new StudyPackGenerationContext(
                        null,
                        "Civil Engineering",
                        "Mathematics",
                        List.of("algebra"),
                        DomainContext.ENGINEERING_MATHEMATICS,
                        LearnerLevel.SENIOR_HIGH
                )
        );

        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).body(requestCaptor.capture());
        assertThat(requestCaptor.getValue())
                .contains("Curriculum level: Senior High School")
                .contains("for Senior High School")
                .doesNotContain(READER_SCAFFOLDING_PREFIX)
                .doesNotContain(COLLEGE_CURRICULUM_LINE);
    }

    @Test
    void generateLongExam_threadsKeyConceptEnumAndMapsKeyConcept() throws Exception {
        stubResponsesCall();
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode questions = payload.putArray("questions");
        questions.add(generatedQuizItem(
                "Which stage generates most ATP?",
                List.of("Glycolysis", "Citric acid cycle", "Electron transport chain", "Fermentation"),
                "C",
                "The electron transport chain produces most ATP.",
                "Energy production",
                "Electron transport chain"
        ));
        when(responseSpec.body(String.class)).thenReturn(generatedQuizResponseJson(payload));

        List<QuizItem> quizItems = service.generateLongExam(
                "Cell Respiration Review",
                "Cell respiration summary",
                List.of("ATP synthesis", "Electron transport chain"),
                List.of(),
                1,
                "medium",
                new StudyPackGenerationContext(LearnerLevel.COLLEGE, "Biology", "Biology", List.of())
        );

        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).body(requestCaptor.capture());
        JsonNode keyConcept = generatedQuizItemSchema(requestCaptor.getValue())
                .path("properties")
                .path("keyConcept");
        assertThat(quizItems.getFirst().concept()).isEqualTo("Energy production");
        assertThat(quizItems.getFirst().keyConcept()).isEqualTo("Electron transport chain");
        assertThat(jsonArrayValues(keyConcept.path("enum")))
                .containsExactly("ATP synthesis", "Electron transport chain");
    }

    @Test
    void generateInterviewPracticeQuiz_nullsKeyConceptOutsideEnum() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode questions = payload.putArray("questions");
        questions.add(generatedQuizItem(
                "You are reviewing an outage after a race condition caused stale reads. What is the strongest next step?",
                List.of("Ignore it", "Add a concurrency test", "Remove logging", "Skip the review"),
                "B",
                "A strong candidate would add coverage for the race condition.",
                "Race-condition prevention",
                "Invented label"
        ));
        when(responseSpec.body(String.class)).thenReturn(generatedQuizResponseJson(payload));

        List<QuizItem> quizItems = service.generateInterviewPracticeQuiz(
                "Backend Interview Prep",
                "Concurrency summary",
                List.of("Concurrency controls"),
                List.of(),
                1,
                new StudyPackGenerationContext(LearnerLevel.PROFESSIONAL, "Software Engineering", "Backend", List.of())
        );

        assertThat(quizItems.getFirst().concept()).isEqualTo("Race-condition prevention");
        assertThat(quizItems.getFirst().keyConcept()).isNull();
    }

    @Test
    void generateLongExamParallel_mergesAndDeduplicatesBatchResults() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode batch1 = buildGeneratedQuizPayload("Batch A", 9);
        ObjectNode batch2 = buildGeneratedQuizPayload("Batch B", 9);
        ((ArrayNode) batch2.get("questions")).set(0, generatedQuizItem(
            "Batch A question 0",
            List.of("Correct 0", "Choice B", "Choice C", "Choice D"),
            "A",
            "Explanation 0",
            "Concept 0"
        ));
        when(responseSpec.body(String.class)).thenReturn(
            generatedQuizResponseJson(batch1),
            generatedQuizResponseJson(batch2)
        );

        List<QuizItem> quizItems = service.generateLongExamParallel(
            "Long Exam Review",
            "Summary",
            List.of("Concept"),
            List.of(),
            14,
            "medium",
            new StudyPackGenerationContext(LearnerLevel.COLLEGE, "Biology", "Biology", List.of()),
            new DirectAsyncTaskExecutor()
        );

        assertThat(quizItems).hasSize(14);
        assertThat(quizItems.stream().map(QuizItem::question).distinct()).hasSize(14);
        assertThat(quizItems.stream().map(QuizItem::question))
            .contains("Batch A question 0", "Batch B question 1");

        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec, times(2)).body(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
            .anySatisfy(body -> assertThat(body).contains("Generate questions covering the first half of the material."))
            .anySatisfy(body -> assertThat(body).contains("Generate questions covering the second half of the material."));
    }

    @Test
    void generateLongExamParallelFallsBackToSequentialWhenBatchFails() throws JsonProcessingException {
        stubResponsesCall();
        when(responseSpec.body(String.class)).thenThrow(new RuntimeException("batch failed"))
            .thenReturn(generatedQuizResponseJson(buildGeneratedQuizPayload("Batch B", 9)))
            .thenReturn(generatedQuizResponseJson(buildGeneratedQuizPayload("Sequential", 14)));

        List<QuizItem> quizItems = service.generateLongExamParallel(
            "Long Exam Review",
            "Summary",
            List.of("Concept"),
            List.of(),
            14,
            "medium",
            new StudyPackGenerationContext(LearnerLevel.COLLEGE, "Biology", "Biology", List.of()),
            new DirectAsyncTaskExecutor()
        );

        assertThat(quizItems).hasSize(14);
        assertThat(quizItems.getFirst().question()).isEqualTo("Sequential question 0");
        verify(responseSpec, times(5)).body(String.class);
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
        ).quizItems();

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
        ).quizItems();

        assertThat(quizItems).hasSize(2);
        verify(responseSpec, times(2)).body(String.class);
    }

    @Test
    void generateChallengeQuiz_retriesWhenTrueFalseQuestionHasMcqIntentStem() throws JsonProcessingException {
        stubResponsesCall();
        ObjectNode invalidPayload = buildGeneratedQuizPayload();
        ArrayNode invalidQuestions = (ArrayNode) invalidPayload.get("questions");
        ObjectNode invalidQuestion = generatedQuizItem(
                "Statement 1: ATP stores cellular energy. Statement 2: ATP is reused after hydrolysis. Which is correct?",
                List.of("True", "False"),
                "A",
                "This item asks the learner to choose between statements, not judge one statement true or false.",
                "ATP production"
        );
        invalidQuestion.put("questionFormat", "TRUE_FALSE");
        invalidQuestions.set(0, invalidQuestion);
        when(responseSpec.body(String.class)).thenReturn(
                generatedQuizResponseJson(invalidPayload),
                generatedQuizResponseJson(buildGeneratedQuizPayload())
        );

        List<QuizItem> quizItems = service.generateChallengeQuiz(
            "Cell Respiration Review",
                "Cell respiration summary",
                List.of("ATP production"),
                List.of(),
                2,
                "medium",
            new StudyPackGenerationContext(LearnerLevel.COLLEGE, null, "Biology", List.of("cells"))
        ).quizItems();

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

    private String generatedQuizResponseJsonWithUsage(ObjectNode payload) throws JsonProcessingException {
        ObjectNode responseJson = objectMapper.readValue(generatedQuizResponseJson(payload), ObjectNode.class);
        ObjectNode usage = responseJson.putObject("usage");
        usage.put("input_tokens", 42);
        usage.put("output_tokens", 84);
        usage.putObject("input_tokens_details").put("cached_tokens", 7);
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

    private ObjectNode buildCompanionPayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("overview", "This plan covers cell biology foundations.");
        payload.put("studyStrategy", "Study one subject plan at a time, then use mixed review.");
        payload.put("commonMistakes", "Learners often read passively without retrieval practice.");
        ArrayNode faq = payload.putArray("faq");
        faq.addObject().put("question", "Where should I start?").put("answer", "Start with cell structure.");
        faq.addObject().put("question", "How should I review?").put("answer", "Use short recall blocks.");
        faq.addObject().put("question", "When am I ready?").put("answer", "When you can explain each concept.");
        ArrayNode mentorTips = payload.putArray("mentorTips");
        mentorTips.addObject()
                .put("title", "Use the next action as your anchor")
                .put("body", "Start with the top action, then write down one concept that still needs practice.");
        return payload;
    }

    private CompanionGenerationContext companionContext() {
        return new CompanionGenerationContext(
                "Cell Biology Plan",
                "Foundations for the unit exam",
                "Nursing",
                List.of(new CompanionGenerationContext.CompanionContextItem(
                        "Cell Structure",
                        "Organelles and membranes"
                )),
                List.of()
        );
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

    private ObjectNode buildGeneratedQuizPayload(String prefix, int count) {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode questions = payload.putArray("questions");
        for (int index = 0; index < count; index++) {
            questions.add(generatedQuizItem(
                prefix + " question " + index,
                List.of("Correct " + index, "Choice B", "Choice C", "Choice D"),
                "A",
                "Explanation " + index,
                "Concept " + index
            ));
        }
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

    private void setMalformedTrueFalseItem(ObjectNode item) {
        item.put("question", "Statement 1: Cells use ATP. Statement 2: ATP stores energy. Which is correct?");
        item.putArray("choices").removeAll();
        ArrayNode choiceArray = (ArrayNode) item.get("choices");
        choiceArray.add("True");
        choiceArray.add("False");
        item.put("answer", "A");
        item.put("questionFormat", "TRUE_FALSE");
        item.put("concept", "ATP production");
        item.put("explanation", "This item asks the learner to choose between statements.");
    }

    private void setMatchingItem(ObjectNode item, List<String> choices, String answer, String questionGroup) {
        item.putArray("choices").removeAll();
        ArrayNode choiceArray = (ArrayNode) item.get("choices");
        choices.forEach(choiceArray::add);
        item.put("answer", answer);
        item.put("questionFormat", "MATCHING");
        item.put("questionGroup", questionGroup);
        item.putNull("correctIndices");
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

    private ObjectNode generatedQuizItem(
            String question,
            List<String> choices,
            String answer,
            String explanation,
            String concept,
            String keyConcept
    ) {
        ObjectNode item = generatedQuizItem(question, choices, answer, explanation, concept);
        item.put("keyConcept", keyConcept);
        return item;
    }

    private JsonNode generatedQuizItemSchema(String requestBody) throws JsonProcessingException {
        return objectMapper.readTree(requestBody)
                .path("text")
                .path("format")
                .path("schema")
                .path("properties")
                .path("questions")
                .path("items");
    }

    private static final class DirectAsyncTaskExecutor implements AsyncTaskExecutor {
        @Override
        public void execute(Runnable task, long startTimeout) {
            task.run();
        }

        @Override
        public void execute(Runnable task) {
            task.run();
        }

        @Override
        public Future<?> submit(Runnable task) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            try {
                return CompletableFuture.completedFuture(task.call());
            } catch (Exception ex) {
                return CompletableFuture.failedFuture(ex);
            }
        }
    }
}
