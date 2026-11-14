package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Preregistration sections 7 and 8: validated blind rows reduced to case-arm facts. */
final class ScoreEvidence {
    static final class InvalidScores extends RuntimeException {
        InvalidScores(String message, Throwable cause) { super(message, cause); }
        InvalidScores(String message) { super(message); }
    }
    record Facts(boolean critical, boolean quality, int scoredQuestions, String subgroup) {}
    record FilesA(Path key, Path primary, Path reviews, Path adjudications, Path exclusions) {}
    record FilesB(Path key, Path scores, Map<String, ClassBScoreFiles.ScorerKind> scorers) {}
    record Loaded(Map<String, Facts> byCaseArm, Set<String> excluded, Map<String, String> hashes) {
        Facts get(String caseId, String arm) { return byCaseArm.get(caseId + KEY_SEPARATOR + arm); }
    }
    private static final String KEY_SEPARATOR = "\u0000";
    private static final String CASE_ID = "caseId";
    private static final String LONG_EXAM_PREFIX = "LONG_EXAM_";
    private static final ObjectMapper JSON = new ObjectMapper();
    private ScoreEvidence() {}

    static Loaded load(FilesA a, FilesB b, EvidenceMapping mapping) {
        if (a == null || b == null || mapping == null) throw new InvalidScores("Score inputs required");
        try {
            Map<String, Facts> facts = new LinkedHashMap<>();
            Map<String, String> hashes = new LinkedHashMap<>();
            Map<String, Path> scorePaths = Map.of("classAKey", a.key(), "classAPrimary", a.primary(),
                    "classAReviews", a.reviews(), "classAAdjudications", a.adjudications(),
                    "classAExclusions", a.exclusions(), "classBKey", b.key(), "classBScores", b.scores());
            for (var item : scorePaths.entrySet()) {
                DeliveredOutputs.guard(item.getValue());
                hashes.put(item.getKey(), FreezeRecord.sha256(item.getValue()));
            }
            Set<String> excluded = new HashSet<>();
            for (JsonNode row : QualityScoreFiles.lines(a.exclusions())) {
                String id = QualityScoreFiles.text(row, CASE_ID);
                QualityScoreFiles.text(row, "reason");
                if (!excluded.add(id)) throw new InvalidScores("Duplicate excluded case: " + id);
            }
            Map<String, JsonNode> keys = QualityScoreFiles.keyed(a.key());
            Map<String, List<JsonNode>> scores = QualityScoreFiles.scores(a.key(), a.primary(),
                    a.reviews(), a.adjudications());
            Map<String, List<JsonNode>> questions = new HashMap<>();
            Map<String, String> subgroups = new HashMap<>();
            for (var item : keys.entrySet()) {
                JsonNode key = item.getValue();
                String caseId = QualityScoreFiles.text(key, CASE_ID);
                String arm = QualityScoreFiles.text(key, "arm");
                String identity = caseId + KEY_SEPARATOR + arm;
                List<JsonNode> rows = scores.get(item.getKey());
                if (rows == null) throw new InvalidScores("Missing score for " + item.getKey());
                JsonNode selected = QualityScoreFiles.find(rows, "ADJUDICATION");
                if (selected == null && rows.stream().map(ScoreEvidence::judgement).distinct().count() > 1)
                    throw new InvalidScores("Unresolved class-A score disagreement: " + item.getKey());
                if (selected == null) selected = QualityScoreFiles.find(rows, "INDEPENDENT_REVIEW");
                if (selected == null) selected = QualityScoreFiles.find(rows, "PRIMARY");
                questions.computeIfAbsent(identity, ignored -> new ArrayList<>()).add(selected);
                String group = QualityScoreFiles.text(key, "workflow");
                if (group.startsWith(LONG_EXAM_PREFIX)) subgroups.put(identity, group.substring(LONG_EXAM_PREFIX.length()));
            }
            questions.forEach((identity, rows) -> facts.put(identity, new Facts(
                    rows.stream().anyMatch(mapping::criticalA), rows.stream().anyMatch(mapping::qualityA),
                    rows.size(), subgroups.get(identity))));

            List<ClassBScoreAggregator.Key> armKey = new ArrayList<>();
            for (String line : Files.readAllLines(b.key())) if (!line.isBlank())
                armKey.add(JSON.readValue(line, ClassBScoreAggregator.Key.class));
            List<ClassBScoreFiles.BlindItem> manifest = armKey.stream().map(key ->
                    new ClassBScoreFiles.BlindItem(key.itemId(), key.workflow(), key.reserve())).toList();
            ClassBScoreFiles.Validated validated = ClassBScoreFiles.validate(b.scores(), manifest, b.scorers());
            validated.requireFullHumanCoverage();
            Map<String, RouteVerdictCalculator.ArmResult> structural = new HashMap<>();
            for (ClassBScoreAggregator.Key key : armKey)
                structural.put(key.itemId(), new RouteVerdictCalculator.ArmResult(
                        RouteVerdictCalculator.Evidence.SCORED, Set.of(), false, null,
                        List.of(), false, false));
            if (!ClassBScoreAggregator.aggregate(validated, armKey, structural).inconclusiveItems().isEmpty())
                throw new InvalidScores("Class-B human score disagreement remains unresolved");
            Map<String, ClassBScoreFiles.Score> human = validated.finalHumanScores();
            for (ClassBScoreAggregator.Key key : armKey) {
                ClassBScoreFiles.Score score = human.get(key.itemId());
                if (score == null) throw new InvalidScores("Unresolved human score: " + key.itemId());
                String identity = key.caseId() + KEY_SEPARATOR + key.arm().name();
                if (facts.putIfAbsent(identity, new Facts(mapping.criticalB(score), mapping.qualityB(score), 1, null)) != null)
                    throw new InvalidScores("Duplicate class-B case arm: " + identity);
            }
            return new Loaded(Map.copyOf(facts), Set.copyOf(excluded), Map.copyOf(hashes));
        } catch (Exception exception) {
            if (exception instanceof InvalidScores invalid) throw invalid;
            throw new InvalidScores("Cannot validate score files: " + a + " / " + b
                    + ": " + exception.getMessage(), exception);
        }
    }

    private static JsonNode judgement(JsonNode row) {
        com.fasterxml.jackson.databind.node.ObjectNode copy = row.deepCopy();
        copy.remove(List.of("id", "reviewerId", "round"));
        return copy;
    }
}
