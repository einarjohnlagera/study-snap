package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.dto.CompanionSection;
import com.studysnap.backend.dto.QuickReviewIncorrectQuestionRequest;
import com.studysnap.backend.entity.AskCompanionTurn;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.service.model.CompanionGenerationContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict private class-B fixture contract for preregistration sections 6 and 7, Revision 2.2. */
final class ClassBCaseManifest {
    enum Workflow { STUDY_TIP, COLLECTION_COMPANION, ASK_COMPANION, INTERVIEW_CRITIQUE }
    static final class InvalidManifest extends RuntimeException {
        InvalidManifest(String message) { super(message); }
        InvalidManifest(String message, Throwable cause) { super(message, cause); }
    }
    record Question(String question, List<String> choices, int correctIndex, String concept,
                    String explanation) {}
    record Case(String fixtureId, Workflow workflow, String stratum, boolean reserve,
                LearnerLevel sourceLevel, List<QuickReviewIncorrectQuestionRequest> incorrectQuestions,
                List<CompanionSection> requestedSections, CompanionGenerationContext companionContext,
                CompanionContent companion, List<AskCompanionTurn> priorTurns, String question,
                Boolean answerableFromCompanion, Question interviewQuestion, Integer selectedChoiceIndex) {}

    static final String PRIVATE_PATH = "backend/target/llm-benchmark";
    private static final String PRIVATE_DIRECTORY = "Private evaluation directory is unavailable to this loader";
    private static final ObjectMapper JSON = new ObjectMapper()
            .findAndRegisterModules()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private ClassBCaseManifest() {}

    static List<Case> load(Path path, FrozenGateParameters parameters) {
        List<Case> cases = loadRows(path);
        Map<Workflow, int[]> counts = new HashMap<>();
        for (Case item : cases)
            counts.computeIfAbsent(item.workflow(), ignored -> new int[2])[item.reserve() ? 1 : 0]++;
        for (Workflow workflow : Workflow.values()) {
            FrozenGateParameters.Workflow frozen = parameters.workflows.get(workflow.name());
            if (frozen == null) throw new InvalidManifest("Unknown frozen workflow: " + workflow);
            int[] count = counts.getOrDefault(workflow, new int[2]);
            if (count[0] != frozen.primarySize() || count[1] != parameters.reserveSize)
                throw new InvalidManifest("Fixture count differs from frozen parameters: " + workflow);
        }
        return cases;
    }

    static List<Case> loadRows(Path path) {
        try {
            Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize());
            if (path.toAbsolutePath().normalize().startsWith(repo.resolve(PRIVATE_PATH)))
                throw new InvalidManifest(PRIVATE_DIRECTORY);
            LlmModelBenchmark.Config.rejectInsideRepoForTest(path, repo);
            if (path.toRealPath().startsWith(repo.resolve(PRIVATE_PATH)))
                throw new InvalidManifest(PRIVATE_DIRECTORY);
            List<Case> cases = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (String line : Files.readAllLines(path)) {
                if (line.isBlank()) throw new InvalidManifest("Blank class-B fixture row");
                com.fasterxml.jackson.databind.JsonNode raw = JSON.readTree(line);
                if (raw == null || !raw.isObject() || !raw.path("reserve").isBoolean())
                    throw new InvalidManifest("Missing or invalid reserve field");
                if (Workflow.INTERVIEW_CRITIQUE.name().equals(raw.path("workflow").asText())
                        && !raw.path("interviewQuestion").path("correctIndex").isIntegralNumber())
                    throw new InvalidManifest("Missing or invalid interview correctIndex");
                Case item = JSON.readValue(line, Case.class);
                validate(item);
                if (!ids.add(item.fixtureId())) throw new InvalidManifest("Duplicate fixtureId");
                cases.add(item);
            }
            return List.copyOf(cases);
        } catch (IOException | IllegalArgumentException ex) {
            if (ex instanceof InvalidManifest invalid) throw invalid;
            throw new InvalidManifest("Cannot load class-B fixtures", ex);
        }
    }

    private static void validate(Case item) {
        if (item.fixtureId() == null || item.fixtureId().isBlank() || item.workflow() == null
                || item.stratum() == null || item.stratum().isBlank())
            throw new InvalidManifest("Incomplete fixture identity");
        switch (item.workflow()) {
            case STUDY_TIP -> {
                if (item.sourceLevel() != LearnerLevel.COLLEGE
                        && item.sourceLevel() != LearnerLevel.BOARD_EXAM_REVIEW
                        || item.incorrectQuestions() == null || item.incorrectQuestions().isEmpty()
                        || item.incorrectQuestions().size() > 3
                        || item.incorrectQuestions().stream().anyMatch(q -> q == null || blank(q.question())
                            || blank(q.correctAnswer()) || blank(q.explanation()))
                        || item.requestedSections() != null || item.companionContext() != null
                        || item.companion() != null || item.priorTurns() != null || item.question() != null
                        || item.answerableFromCompanion() != null || item.interviewQuestion() != null
                        || item.selectedChoiceIndex() != null) throw new InvalidManifest("Invalid study-tip fixture");
            }
            case COLLECTION_COMPANION -> {
                if (item.requestedSections() == null || item.requestedSections().isEmpty()
                        || item.requestedSections().contains(null)
                        || Set.copyOf(item.requestedSections()).size() != item.requestedSections().size()
                        || item.companionContext() == null || blank(item.companionContext().title())
                        || item.companionContext().subjectPlans() == null
                        || item.companionContext().notes() == null
                        || item.companionContext().subjectPlans().size()
                            + item.companionContext().notes().size() == 0
                        || java.util.stream.Stream.concat(item.companionContext().subjectPlans().stream(),
                                item.companionContext().notes().stream())
                            .anyMatch(child -> child == null || blank(child.title()) || blank(child.description()))
                        || item.companionContext().routing() != null
                        || item.sourceLevel() != null || item.incorrectQuestions() != null || item.companion() != null
                        || item.priorTurns() != null || item.question() != null
                        || item.answerableFromCompanion() != null || item.interviewQuestion() != null
                        || item.selectedChoiceIndex() != null) throw new InvalidManifest("Invalid collection fixture");
            }
            case ASK_COMPANION -> {
                if (item.companion() == null || item.priorTurns() == null || item.priorTurns().size() > 5
                        || item.priorTurns().stream().anyMatch(t -> t == null || blank(t.question())
                            || blank(t.answer()) || t.createdAt() == null)
                        || blank(item.question()) || item.answerableFromCompanion() == null
                        || item.sourceLevel() != null || item.incorrectQuestions() != null || item.requestedSections() != null
                        || item.companionContext() != null || item.interviewQuestion() != null
                        || item.selectedChoiceIndex() != null) throw new InvalidManifest("Invalid Ask Companion fixture");
            }
            case INTERVIEW_CRITIQUE -> {
                Question q = item.interviewQuestion();
                if (q == null || blank(q.question()) || q.choices() == null || q.choices().size() != 4
                        || q.choices().stream().anyMatch(ClassBCaseManifest::blank)
                        || q.correctIndex() < 0 || q.correctIndex() >= q.choices().size()
                        || blank(q.concept()) || blank(q.explanation())
                        || item.selectedChoiceIndex() == null || item.selectedChoiceIndex() < 0
                        || item.selectedChoiceIndex() >= q.choices().size()
                        || item.sourceLevel() != null || item.incorrectQuestions() != null || item.requestedSections() != null
                        || item.companionContext() != null || item.companion() != null
                        || item.priorTurns() != null || item.question() != null
                        || item.answerableFromCompanion() != null) throw new InvalidManifest("Invalid critique fixture");
            }
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
