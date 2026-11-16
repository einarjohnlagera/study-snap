package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Synthetic key, score rows and frozen bars. TEST-ONLY COPY OF THE REVISION 4 SECTION 9 VALUES. */
final class PilotTestKeys {
    private static final String L_NEWLINE = "\n";
    private static final String L_P = "P";
    private static final String L_S = "S";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path BARS_SOURCE = Path.of("src/test/java/com/studysnap/backend/benchmark/pilot-bars-test-only.json");
    private PilotTestKeys() {}
    static PilotBars bars(Path directory) throws Exception {
        Path file = directory.resolve("bars.json");
        Files.copy(BARS_SOURCE, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(BARS_SOURCE.resolveSibling(BARS_SOURCE.getFileName() + ".sha256"),
                file.resolveSibling("bars.json.sha256"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return PilotBars.load(file);
    }
    static PilotKey key(Path directory) throws Exception {
        Path file = directory.resolve("key.jsonl");
        List<String> lines = new ArrayList<>();
        for (ObjectNode row : PilotKeyTest.revision4()) lines.add(JSON.writeValueAsString(row));
        Files.writeString(file, String.join(L_NEWLINE, lines) + L_NEWLINE);
        Files.writeString(file.resolveSibling("key.jsonl.sha256"), FreezeRecord.sha256(Files.readAllBytes(file)));
        return PilotKey.load(file);
    }
    static Map<String, ClassAScoreFilesV2.ItemScores> scores(PilotKey key) {
        Map<String, ClassAScoreFilesV2.ItemScores> scores = new HashMap<>();
        for (PilotKey.Row row : key.rows()) {
            ObjectNode p = score(row, L_P), s = score(row, L_S);
            scores.put(row.id(), new ClassAScoreFilesV2.ItemScores(row.id(),
                    ClassAScoreFilesV2.Slot.ok(p), ClassAScoreFilesV2.Slot.ok(s), null));
        }
        return scores;
    }
    static ObjectNode score(PilotKey.Row key, String role) {
        ObjectNode row = ClassAScoreSchemaV2Test.scorer(key.id(), role);
        switch (key.plantedType()) {
            case WRONG_KEY -> ClassAScoreSchemaV2Test.defect(row, "WRONG_KEY", 1);
            case SECOND_DEFENSIBLE_ANSWER, MATERIAL_AMBIGUITY_MULTI ->
                    ClassAScoreSchemaV2Test.defect(row, "SECOND_DEFENSIBLE_ANSWER", 0, 1);
            case MATERIAL_AMBIGUITY_ZERO -> ClassAScoreSchemaV2Test.defect(row, "KEY_NOT_DEFENSIBLE");
            case EXPLANATION_CONTRADICTS_KEY -> critical(row, "EXPLANATION_CONTRADICTS_KEY");
            case SOURCE_CONTRADICTION -> critical(row, "CONTRADICTS_FULL_NOTE");
            case MINOR_WORDING -> { row.put("ambiguityClass", "MINOR_WORDING"); ClassAScoreSchemaV2Test.evidence(row); }
            case NUMERIC_INCONSISTENCY, WEAK_DISTRACTOR, UNHELPFUL_EXPLANATION -> {
                row.put("factuallyCorrect", "NO"); ClassAScoreSchemaV2Test.evidence(row);
            }
            case CLEAN -> { }
        }
        ClassAScoreSchemaV2.validate(row, ClassAScoreSchemaV2Test.packet("MCQ"), ClassAScoreSchemaV2Test.MAPPING);
        return row;
    }
    static PilotOwnerRatings ratings(PilotKey key, Map<String, ClassAScoreFilesV2.ItemScores> scores, PilotBars bars) {
        List<RationaleSample.NonCleanRow> candidates = new ArrayList<>();
        for (String role : List.of(L_P, L_S)) for (PilotKey.Row row : key.rows()) {
            ClassAScoreFilesV2.Slot slot = L_P.equals(role) ? scores.get(row.id()).p() : scores.get(row.id()).s();
            if (slot.status() == ClassAScoreFilesV2.Status.OK
                    && ClassAScoreSchemaV2.verdict(slot.row(), ClassAScoreSchemaV2Test.MAPPING)
                    != ClassAScoreSchemaV2.Verdict.CLEAN)
                candidates.add(new RationaleSample.NonCleanRow(row.id(), role));
        }
        List<OwnerTimingSheet.RationaleRating> values = RationaleSample.select(candidates,
                bars.rationaleSampleSeed, bars.ratedRationales).stream()
                .map(row -> new OwnerTimingSheet.RationaleRating(row.itemId(), row.scorerRole(), true)).toList();
        return new PilotOwnerRatings(values);
    }
    private static void critical(ObjectNode row, String type) {
        row.put("critical", true).put("criticalType", type);
        ClassAScoreSchemaV2Test.evidence(row);
    }
}
