package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.LearnerLevel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict, ordered private class-A input contract for preregistration sections 3 and 6. */
final class ClassACaseManifest {
    enum Workflow { NOTE_ADAPTIVE, PLAN_ADAPTIVE, TEACHER_QUIZ, BOARD_EXAM_DIRECT, INTERVIEW_PRACTICE }

    record Source(String title, String noteText, String summary, List<String> keyConcepts,
                  List<String> tags, String courseProgram, String subject,
                  DomainContext domainContext, LearnerLevel noteLevel) {}

    record Case(String caseId, Workflow workflow, String stratum, LearnerLevel noteLevel,
                DomainContext domainContext, String subject, boolean quantitative,
                List<Source> sources, int requestedQuestionCount, String difficulty, boolean reserve) {}

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    private static final String PRIVATE_DIRECTORY = "Private evaluation directory is unavailable to this loader";

    private ClassACaseManifest() {}

    static List<Case> load(Path path) throws IOException {
        Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize());
        if (path.toAbsolutePath().normalize().startsWith(repo.resolve("backend/target/llm-benchmark")))
            throw new IllegalArgumentException(PRIVATE_DIRECTORY);
        LlmModelBenchmark.Config.rejectInsideRepoForTest(path, repo);
        if (path.toRealPath().startsWith(repo.resolve("backend/target/llm-benchmark")))
            throw new IllegalArgumentException(PRIVATE_DIRECTORY);
        Set<String> ids = new HashSet<>();
        List<Case> cases = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) throw new IllegalArgumentException("Blank class-A manifest row");
            Case item = JSON.readValue(line, Case.class);
            if (item.caseId() == null || item.caseId().isBlank() || !ids.add(item.caseId()))
                throw new IllegalArgumentException("Missing or duplicate caseId");
            if (item.workflow() == null || item.stratum() == null || item.stratum().isBlank()
                    || item.noteLevel() == null || item.sources() == null || item.sources().isEmpty()
                    || item.sources().stream().anyMatch(source -> source == null
                        || source.title() == null || source.noteText() == null
                        || source.keyConcepts() == null || source.keyConcepts().isEmpty())
                    || item.requestedQuestionCount() < 1)
                throw new IllegalArgumentException("Incomplete class-A case: " + item.caseId());
            if ((item.workflow() == Workflow.TEACHER_QUIZ || item.workflow() == Workflow.NOTE_ADAPTIVE)
                    && item.sources().size() != 1)
                throw new IllegalArgumentException("Workflow accepts one source: " + item.caseId());
            if (item.workflow() == Workflow.BOARD_EXAM_DIRECT
                    ? !"mixed".equals(item.difficulty()) : item.difficulty() != null)
                throw new IllegalArgumentException("Difficulty differs from production path: " + item.caseId());
            cases.add(item);
        }
        if (cases.isEmpty()) throw new IllegalArgumentException("Empty class-A manifest");
        return List.copyOf(cases);
    }
}
