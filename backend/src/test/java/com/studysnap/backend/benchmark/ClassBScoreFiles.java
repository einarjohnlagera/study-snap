package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Blind class-B scoring contract for preregistration section 7 and Revision 2.2. */
final class ClassBScoreFiles {
    enum ScorerKind { HUMAN, MODEL }
    enum ReasonCode { TASK_NOT_FULFILLED, UNGROUNDED, UNSUPPORTED_OR_NOT_ACTIONABLE,
        CRITICAL_MISLEADING, MULTIPLE_DEFICITS }
    static final class InvalidScore extends RuntimeException {
        InvalidScore(String message) { super(message); }
        InvalidScore(String message, Throwable cause) { super(message, cause); }
    }
    static final class MissingHumanCoverage extends RuntimeException {
        MissingHumanCoverage(String message) { super(message); }
    }
    record BlindItem(String itemId, ClassBCaseManifest.Workflow workflow, boolean reserve) {}
    record Score(String itemId, ClassBCaseManifest.Workflow workflow, int round,
                 String scorerId, ScorerKind scorerKind, boolean taskFulfilled, boolean grounded,
                 boolean actionable, boolean noCriticalMisleading, boolean critical,
                 ReasonCode reasonCode, boolean borderline, boolean adjudication) {
        boolean qualityDeficit() { return !taskFulfilled || !grounded || !actionable; }
        List<Boolean> dimensions() {
            return List.of(taskFulfilled, grounded, actionable, noCriticalMisleading);
        }
    }
    record Validated(List<BlindItem> manifest, List<Score> scores) {
        Validated { manifest = List.copyOf(manifest); scores = List.copyOf(scores); }

        void requireFullHumanCoverage() {
            requireFullHumanCoverage(manifest);
        }

        void requireFullHumanCoverage(List<BlindItem> frozenManifest) {
            if (!manifest.equals(frozenManifest)) throw new InvalidScore("Human coverage manifest differs from frozen manifest");
            Set<String> covered = new HashSet<>();
            scores.stream().filter(row -> row.scorerKind() == ScorerKind.HUMAN)
                    .forEach(row -> covered.add(row.itemId()));
            for (BlindItem item : manifest) if (!item.reserve() && !covered.contains(item.itemId()))
                throw new MissingHumanCoverage("Missing primary human score: " + item.workflow());
        }

        Map<String, Score> finalHumanScores() {
            Map<String, List<Score>> byItem = new LinkedHashMap<>();
            scores.stream().filter(row -> row.scorerKind() == ScorerKind.HUMAN)
                    .forEach(row -> byItem.computeIfAbsent(row.itemId(), unused -> new ArrayList<>()).add(row));
            Map<String, Score> resolved = new LinkedHashMap<>();
            for (Map.Entry<String, List<Score>> entry : byItem.entrySet()) {
                List<Score> rows = entry.getValue();
                List<Score> adjudications = rows.stream().filter(Score::adjudication).toList();
                if (adjudications.size() > 1) throw new InvalidScore("Multiple adjudications: " + entry.getKey());
                if (!adjudications.isEmpty()) {
                    Score winner = adjudications.getFirst();
                    if (rows.stream().anyMatch(row -> row != winner && row.round() >= winner.round()))
                        throw new InvalidScore("Adjudication must be later: " + entry.getKey());
                    resolved.put(entry.getKey(), winner);
                } else if (rows.stream().map(Score::dimensions).distinct().count() == 1) {
                    resolved.put(entry.getKey(), rows.stream()
                            .max(java.util.Comparator.comparingInt(Score::round)).orElseThrow());
                }
            }
            return Map.copyOf(resolved);
        }
    }

    private static final String ITEM_ID = "itemId";
    private static final String WORKFLOW = "workflow";
    private static final String ROUND = "round";
    private static final String SCORER_ID = "scorerId";
    private static final String SCORER_KIND = "scorerKind";
    private static final String TASK_FULFILLED = "taskFulfilled";
    private static final String GROUNDED = "grounded";
    private static final String ACTIONABLE = "actionable";
    private static final String NO_CRITICAL = "noCriticalMisleading";
    private static final String CRITICAL = "critical";
    private static final String REASON_CODE = "reasonCode";
    private static final String BORDERLINE = "borderline";
    private static final String ADJUDICATION = "adjudication";
    static final String KEY_SEPARATOR = "\u0000";
    private static final String PRIVATE_DIRECTORY = "Private evaluation directory is unavailable to this validator";
    private static final Set<String> FIELDS = Set.of(ITEM_ID, WORKFLOW, ROUND, SCORER_ID, SCORER_KIND,
            TASK_FULFILLED, GROUNDED, ACTIONABLE, NO_CRITICAL, CRITICAL, REASON_CODE,
            BORDERLINE, ADJUDICATION);
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private ClassBScoreFiles() {}

    static Validated validate(Path path, List<BlindItem> manifest, Map<String, ScorerKind> scorers) {
        try {
            Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize());
            if (path.toAbsolutePath().normalize().startsWith(repo.resolve(ClassBCaseManifest.PRIVATE_PATH)))
                throw new InvalidScore(PRIVATE_DIRECTORY);
            LlmModelBenchmark.Config.rejectInsideRepoForTest(path, repo);
            if (path.toRealPath().startsWith(repo.resolve(ClassBCaseManifest.PRIVATE_PATH)))
                throw new InvalidScore(PRIVATE_DIRECTORY);
            Map<String, BlindItem> items = new LinkedHashMap<>();
            for (BlindItem item : manifest) {
                if (item == null || item.itemId() == null || !item.itemId().matches("blind-[0-9]+")
                        || item.workflow() == null || items.putIfAbsent(item.itemId(), item) != null)
                    throw new InvalidScore("Invalid blinded manifest");
            }
            if (scorers == null || scorers.isEmpty()) throw new InvalidScore("Scorer registry required");
            for (Map.Entry<String, ScorerKind> entry : scorers.entrySet())
                if (entry.getKey() == null || !entry.getKey().matches("(?:HUMAN|MODEL)_[A-Z0-9_]+")
                        || entry.getValue() == null
                        || !entry.getKey().startsWith(entry.getValue().name() + "_"))
                    throw new InvalidScore("Invalid scorer role label");
            List<Score> rows = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String line : Files.readAllLines(path)) {
                if (line.isBlank()) throw new InvalidScore("Blank score row");
                JsonNode node = JSON.readTree(line);
                if (node == null || !node.isObject() || !fieldNames(node).equals(FIELDS))
                    throw new InvalidScore("Unknown or missing score field");
                String id = text(node, ITEM_ID);
                BlindItem item = items.get(id);
                if (item == null || !item.workflow().name().equals(text(node, WORKFLOW)))
                    throw new InvalidScore("Unmapped blinded item");
                JsonNode roundNode = node.get(ROUND);
                if (!roundNode.isIntegralNumber() || !roundNode.canConvertToInt() || roundNode.asInt() <= 0)
                    throw new InvalidScore("Invalid score round");
                int round = roundNode.asInt();
                String scorerId = text(node, SCORER_ID);
                ScorerKind kind = enumValue(ScorerKind.class, text(node, SCORER_KIND));
                if (kind != scorers.get(scorerId)) throw new InvalidScore("Unknown or mismatched scorer");
                if (!seen.add(id + KEY_SEPARATOR + scorerId + KEY_SEPARATOR + round))
                    throw new InvalidScore("Duplicate item, scorer and round");
                boolean task = bool(node, TASK_FULFILLED), grounded = bool(node, GROUNDED);
                boolean actionable = bool(node, ACTIONABLE), noCritical = bool(node, NO_CRITICAL);
                boolean critical = bool(node, CRITICAL);
                boolean borderline = bool(node, BORDERLINE), adjudication = bool(node, ADJUDICATION);
                if (critical == noCritical) throw new InvalidScore("Critical/fourth-dimension mismatch");
                boolean anyFailed = !task || !grounded || !actionable || !noCritical;
                JsonNode reasonNode = node.get(REASON_CODE);
                ReasonCode reason = anyFailed ? enumValue(ReasonCode.class, text(node, REASON_CODE)) : null;
                if (!anyFailed && !reasonNode.isNull()) throw new InvalidScore("Unexpected reason code");
                if (adjudication && (kind != ScorerKind.HUMAN || round == 1))
                    throw new InvalidScore("Invalid adjudication round");
                rows.add(new Score(id, item.workflow(), round, scorerId, kind, task, grounded,
                        actionable, noCritical, critical, reason, borderline, adjudication));
            }
            return new Validated(manifest, rows);
        } catch (IOException | IllegalArgumentException ex) {
            if (ex instanceof InvalidScore invalid) throw invalid;
            throw new InvalidScore("Cannot validate class-B score file", ex);
        }
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
    private static String text(JsonNode row, String field) {
        JsonNode value = row.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank())
            throw new InvalidScore("Missing or invalid " + field);
        return value.asText();
    }
    private static boolean bool(JsonNode row, String field) {
        if (!row.get(field).isBoolean()) throw new InvalidScore("Boolean required: " + field);
        return row.get(field).asBoolean();
    }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException ex) { throw new InvalidScore("Unknown " + type.getSimpleName(), ex); }
    }
}
