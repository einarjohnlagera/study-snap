package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PilotBarsEvaluatorTest {
    private static final String L_P = "P";
    private static final String L_CRITICAL_20 = "critical=20";
    private static final String L_CRITICAL = "critical";
    private static final String L_CRITICALTYPE = "criticalType";
    private static final String L_EXPLANATION_CONTRADICTS_KEY = "EXPLANATION_CONTRADICTS_KEY";
    private static final String L_S = "S";
    private static final String L_CLEAN = "CLEAN";
    private static final String L_ITEM = "item-";
    @TempDir Path temp;
    private PilotKey key;
    private PilotBars bars;
    private Map<String, ClassAScoreFilesV2.ItemScores> scores;
    private void setup() throws Exception { key = PilotTestKeys.key(temp); bars = PilotTestKeys.bars(temp); scores = PilotTestKeys.scores(key); }
    private PilotBarsEvaluator.PilotBarReport evaluate() {
        return PilotBarsEvaluator.evaluate(key, scores, ClassAScoreSchemaV2Test.MAPPING, bars, List.of(),
                PilotTestKeys.ratings(key, scores, bars));
    }
    private PilotBarsEvaluator.PilotBarReport evaluate(List<PilotLeakCheck.Hit> hits, PilotOwnerRatings ratings) {
        return PilotBarsEvaluator.evaluate(key, scores, ClassAScoreSchemaV2Test.MAPPING, bars, hits, ratings);
    }
    private List<PilotKey.Row> rows(PlantedType type) {
        return key.rows().stream().filter(row -> row.plantedType() == type).toList();
    }
    private void p(PilotKey.Row keyRow, ObjectNode row) {
        var item = scores.get(keyRow.id());
        scores.put(keyRow.id(), new ClassAScoreFilesV2.ItemScores(keyRow.id(), ClassAScoreFilesV2.Slot.ok(row), item.s(), null));
    }
    private void pMissing(PilotKey.Row row) {
        var item = scores.get(row.id());
        scores.put(row.id(), new ClassAScoreFilesV2.ItemScores(row.id(), ClassAScoreFilesV2.Slot.missing(), item.s(), null));
    }
    private void pClean(PilotKey.Row row) { p(row, ClassAScoreSchemaV2Test.scorer(row.id(), L_P)); }
    private ObjectNode pRow(PilotKey.Row row) { return (ObjectNode) scores.get(row.id()).p().row().deepCopy(); }
    private boolean bar(int number) { return evaluate().scorers().get(L_P).bars().get(number).passed(); }
    @Test void allPassAndReportedRates() throws Exception {
        setup(); var result = evaluate();
        assertThat(result.pilotPassed()).isTrue();
        assertThat(result.scorers().get(L_P).bars()).hasSize(8);
        assertThat(result.scorers().get(L_P).bars().get(8).required()).isEqualTo("reported only");
        assertThat(result.psVerdictAgreementRate()).isEqualTo(1);
        assertThat(result.scorers().get(L_P).cleanQualityFailReference()).isEqualTo(bars.cleanQualityFailReference);
        assertThat(result.scorers().get(L_P).recallByType()).containsEntry(PlantedType.MINOR_WORDING, 1.0)
                .containsEntry(PlantedType.CLEAN, 1.0);
        assertThat(result.scorers().get(L_P).bars().get(8).observed()).contains("lower bound only");
    }
    @Test void bar1MissingAndInvalidAreNotMarkedCritical() throws Exception {
        setup(); PilotKey.Row row = rows(PlantedType.EXPLANATION_CONTRADICTS_KEY).getFirst();
        pMissing(row); assertThat(bar(1)).isFalse();
        assertThat(evaluate().scorers().get(L_P).bars().get(2).observed()).contains(L_CRITICAL_20);
        var item = scores.get(row.id());
        scores.put(row.id(), new ClassAScoreFilesV2.ItemScores(row.id(),
                new ClassAScoreFilesV2.Slot(ClassAScoreFilesV2.Status.INVALID, null,
                        ClassAScoreSchemaV2.Code.SCHEMA, "invalid"), item.s(), null));
        assertThat(bar(1)).isFalse();
        assertThat(evaluate().scorers().get(L_P).bars().get(2).observed()).contains(L_CRITICAL_20);
    }
    @Test void bar2PooledThresholdAndCrossFamilyAreIndependent() throws Exception {
        setup(); List<PilotKey.Row> own = key.rows().stream().filter(row -> row.severity() == PlantedType.Severity.CRITICAL
                && row.authoringSource() == PilotFixtureSource.AuthoringSource.CODEX
                && row.plantedType() == PlantedType.EXPLANATION_CONTRADICTS_KEY).toList();
        pClean(own.get(0)); pClean(own.get(1)); assertThat(bar(2)).isTrue();
        pClean(own.get(2)); assertThat(bar(2)).isFalse();
        setup(); List<PilotKey.Row> cross = key.rows().stream().filter(row -> row.severity() == PlantedType.Severity.CRITICAL
                && row.authoringSource() == PilotFixtureSource.AuthoringSource.CLAUDE_OR_OWNER
                && row.plantedType() == PlantedType.SOURCE_CONTRADICTION).toList();
        pClean(cross.get(0)); pClean(cross.get(1));
        var result = evaluate().scorers().get(L_P).bars().get(2);
        assertThat(result.observed()).contains("critical=19", "cross=8/10");
        assertThat(result.passed()).isFalse();
    }
    @Test void bar2RequiresMaterialAndEveryZeroItemFact() throws Exception {
        setup(); for (PilotKey.Row row : rows(PlantedType.MATERIAL_AMBIGUITY_MULTI)) {
            ObjectNode changed = pRow(row); changed.put("ambiguityClass", "NONE"); p(row, changed);
        }
        assertThat(bar(2)).isFalse();
        setup(); PilotKey.Row zero = rows(PlantedType.MATERIAL_AMBIGUITY_ZERO).getFirst();
        ObjectNode changed = pRow(zero); changed.put("keyDefensible", true); p(zero, changed);
        assertThat(bar(2)).isFalse();
    }
    @Test void bars3And4AtThresholdAndBelow() throws Exception {
        setup(); List<PilotKey.Row> minors = rows(PlantedType.MINOR_WORDING);
        pClean(minors.get(0)); assertThat(bar(3)).isTrue();
        pClean(minors.get(1)); assertThat(bar(3)).isFalse();
        setup(); List<PilotKey.Row> quality = rows(PlantedType.UNHELPFUL_EXPLANATION);
        pClean(quality.get(0)); pClean(quality.get(1)); assertThat(bar(4)).isTrue();
        pClean(quality.get(2)); assertThat(bar(4)).isFalse();
    }
    @Test void bar5CountsCriticalButReportsQualitySeparately() throws Exception {
        setup(); List<PilotKey.Row> clean = rows(PlantedType.CLEAN);
        for (int i = 0; i < 9; i++) {
            ObjectNode changed = pRow(clean.get(i)); changed.put("factuallyCorrect", "NO");
            ClassAScoreSchemaV2Test.evidence(changed); p(clean.get(i), changed);
        }
        assertThat(bar(5)).isTrue();
        assertThat(evaluate().scorers().get(L_P).cleanQualityFails()).isEqualTo(9);
        for (int i = 9; i < 13; i++) {
            ObjectNode changed = pRow(clean.get(i)); changed.put(L_CRITICAL, true)
                    .put(L_CRITICALTYPE, L_EXPLANATION_CONTRADICTS_KEY);
            ClassAScoreSchemaV2Test.evidence(changed); p(clean.get(i), changed);
        }
        assertThat(bar(5)).isTrue();
        ObjectNode fifth = pRow(clean.get(13)); fifth.put(L_CRITICAL, true).put(L_CRITICALTYPE, L_EXPLANATION_CONTRADICTS_KEY);
        ClassAScoreSchemaV2Test.evidence(fifth); p(clean.get(13), fifth);
        assertThat(bar(5)).isFalse();
    }
    @Test void bars6And7AndOverallRequireBothScorers() throws Exception {
        setup(); var hit = new PilotLeakCheck.Hit(temp, PilotLeakCheck.ArtifactClass.PROMPT, "canary", "hit");
        assertThat(evaluate(List.of(hit), PilotTestKeys.ratings(key, scores, bars)).scorers().get(L_P).bars().get(6).passed())
                .isFalse();
        setup(); PilotKey.Row row = rows(PlantedType.EXPLANATION_CONTRADICTS_KEY).getFirst();
        ObjectNode changed = pRow(row); changed.put("rationale", ""); p(row, changed);
        assertThat(bar(7)).isFalse();
        setup(); var ratings = new ArrayList<>(PilotTestKeys.ratings(key, scores, bars).ratings());
        for (int i = 0; i < 3; i++) {
            var old = ratings.get(i); ratings.set(i, new OwnerTimingSheet.RationaleRating(old.itemId(), old.scorerRole(), false));
        }
        var result = evaluate(List.of(), new PilotOwnerRatings(ratings));
        assertThat(result.scorers().get(L_P).bars().get(7).passed()).isFalse();
        assertThat(result.scorers().get(L_S).bars().get(7).passed()).isTrue();
        assertThat(result.pilotPassed()).isFalse();
    }
    @Test void compositionAndRatingsSelectionAreStrict() throws Exception {
        setup(); List<OwnerTimingSheet.RationaleRating> ratings = PilotTestKeys.ratings(key, scores, bars).ratings();
        assertThatThrownBy(() -> evaluate(List.of(), new PilotOwnerRatings(ratings.subList(1, ratings.size()))))
                .isInstanceOf(PilotBars.InvalidPilotBars.class);
        var extra = new ArrayList<>(ratings); extra.add(new OwnerTimingSheet.RationaleRating("pilot-00126", L_P, true));
        assertThatThrownBy(() -> evaluate(List.of(), new PilotOwnerRatings(extra)))
                .isInstanceOf(PilotBars.InvalidPilotBars.class);
        var keyRows = PilotKeyTest.revision4();
        keyRows.getFirst().put("plantedType", L_CLEAN).put("severity", L_CLEAN).put("rootCauseInput", false);
        Path badFile = temp.resolve("bad-key.jsonl");
        java.nio.file.Files.write(badFile, keyRows.stream().map(ObjectNode::toString).toList());
        java.nio.file.Files.writeString(badFile.resolveSibling("bad-key.jsonl.sha256"),
                FreezeRecord.sha256(java.nio.file.Files.readAllBytes(badFile)));
        PilotKey bad = PilotKey.load(badFile);
        assertThatThrownBy(() -> PilotBarsEvaluator.evaluate(bad, scores, ClassAScoreSchemaV2Test.MAPPING,
                bars, List.of(), new PilotOwnerRatings(ratings))).isInstanceOf(PilotBars.InvalidPilotBars.class);
    }
    @Test void frozenThresholdsChangeTheAnswer() throws Exception {
        setup(); List<PilotKey.Row> clean = rows(PlantedType.CLEAN);
        for (int i = 0; i < 5; i++) {
            ObjectNode changed = pRow(clean.get(i)); changed.put(L_CRITICAL, true)
                    .put(L_CRITICALTYPE, L_EXPLANATION_CONTRADICTS_KEY);
            ClassAScoreSchemaV2Test.evidence(changed); p(clean.get(i), changed);
        }
        assertThat(bar(5)).isFalse();
        var source = (ObjectNode) new com.fasterxml.jackson.databind.ObjectMapper().readTree(java.nio.file.Files.readString(Path.of(
                "src/test/java/com/studysnap/backend/benchmark/pilot-bars-test-only.json")));
        ((ObjectNode) source.path("bars")).put("maxCleanControlCriticalCount", 5);
        Path file = temp.resolve("new-bars.json"); java.nio.file.Files.writeString(file, source.toString());
        java.nio.file.Files.writeString(file.resolveSibling("new-bars.json.sha256"),
                FreezeRecord.sha256(java.nio.file.Files.readAllBytes(file)));
        bars = PilotBars.load(file);
        assertThat(bar(5)).isTrue();
        assertThatThrownBy(() -> PilotBars.load(temp.resolve("missing-bars.json")))
                .isInstanceOf(PilotBars.InvalidPilotBars.class);
    }
    @Test void rationaleSampleIsSeededPerScorer() {
        List<RationaleSample.NonCleanRow> pool = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            pool.add(new RationaleSample.NonCleanRow(L_ITEM + i, L_P));
            pool.add(new RationaleSample.NonCleanRow(L_ITEM + i, L_S));
        }
        var first = RationaleSample.select(pool, 10, 5);
        assertThat(first).isEqualTo(RationaleSample.select(pool, 10, 5));
        assertThat(first).isNotEqualTo(RationaleSample.select(pool, 11, 5));
        assertThat(first.stream().filter(row -> row.scorerRole().equals(L_P)).map(RationaleSample.NonCleanRow::itemId).toList())
                .isEqualTo(first.stream().filter(row -> row.scorerRole().equals(L_S)).map(RationaleSample.NonCleanRow::itemId).toList());
        assertThatThrownBy(() -> RationaleSample.select(pool, 10, 21))
                .isInstanceOf(PilotBars.InvalidPilotBars.class);
    }
    @Test void emptyHumanFileLoadsThroughProductionScorePathAndEvaluates() throws Exception {
        setup();
        Path packetsDir = temp.resolve("packets");
        java.nio.file.Files.createDirectory(packetsDir);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        List<String> packetLines = new ArrayList<>();
        Map<String, List<PilotKey.Row>> byPacket = new java.util.LinkedHashMap<>();
        for (PilotKey.Row row : key.rows()) byPacket.computeIfAbsent(row.packetId(), ignored -> new ArrayList<>()).add(row);
        for (var entry : byPacket.entrySet()) {
            ObjectNode packet = json.createObjectNode();
            packet.put("requestedQuestionCount", entry.getValue().size());
            packet.putArray("sourceNoteTexts").add(ClassAScoreSchemaV2Test.SOURCE);
            var output = packet.putArray("outputs").addObject();
            output.put("placement", "A");
            var items = output.putArray("items");
            for (PilotKey.Row row : entry.getValue()) {
                var item = items.addObject(); item.put("itemId", row.id());
                item.set("question", ClassAScoreSchemaV2Test.packet("MCQ").question());
            }
            packetLines.add(packet.toString());
        }
        java.nio.file.Files.write(packetsDir.resolve("pilot.jsonl"), packetLines);
        Path pFile = temp.resolve("p.jsonl"), sFile = temp.resolve("s.jsonl"), hFile = temp.resolve("h.jsonl");
        java.nio.file.Files.write(pFile, key.rows().stream().map(row -> PilotTestKeys.score(row, L_P).toString()).toList());
        java.nio.file.Files.write(sFile, key.rows().stream().map(row -> PilotTestKeys.score(row, L_S).toString()).toList());
        java.nio.file.Files.write(hFile, new byte[0]);
        var loaded = ClassAScoreFilesV2.load(temp.resolve("key.jsonl"), ClassAPacketIndex.loadPilot(packetsDir),
                pFile, sFile, hFile, ClassAScoreSchemaV2Test.MAPPING);
        assertThat(loaded.values()).allMatch(item -> item.h() == null);
        assertThat(PilotBarsEvaluator.evaluate(key, loaded, ClassAScoreSchemaV2Test.MAPPING, bars, List.of(),
                PilotTestKeys.ratings(key, loaded, bars)).pilotPassed()).isTrue();
    }

}
