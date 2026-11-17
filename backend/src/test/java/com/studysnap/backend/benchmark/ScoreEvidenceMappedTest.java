package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreEvidenceMappedTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CASE = "case-0";
    private static final String LUNA = "LUNA";
    private static final String MINI = "BASELINE_MINI";
    private static final String HUMAN = "HUMAN_A";
    @TempDir Path temp;

    private record Fixture(ScoreEvidence.FilesA a, ScoreEvidence.FilesB b, EvidenceMapping mapping,
                           ScoreEvidence.ClassBArmNames names) {}

    @Test void mappedFactsUseReceiptArmsWhileLegacyOverloadKeepsKeyLabels() throws Exception {
        Fixture fixture = fixture();
        ScoreEvidence.Loaded mapped = ScoreEvidence.load(fixture.a(), fixture.b(), fixture.mapping(), fixture.names());
        assertThat(mapped.get(CASE, LUNA)).isNotNull();
        assertThat(mapped.get(CASE, MINI)).isNotNull();
        assertThat(mapped.get(CASE, "CANDIDATE")).isNull();
        ScoreEvidence.Loaded legacy = ScoreEvidence.load(fixture.a(), fixture.b(), fixture.mapping());
        assertThat(legacy.get(CASE, "CANDIDATE")).isNotNull();
        assertThat(legacy.get(CASE, LUNA)).isNull();
    }

    @Test void unknownKeyArmIsInvalidScores() throws Exception {
        Fixture fixture = fixture();
        String source = Files.readString(fixture.b().key());
        Files.writeString(fixture.b().key(), source.replace("CANDIDATE", "UNKNOWN"));
        assertThatThrownBy(() -> ScoreEvidence.load(fixture.a(), fixture.b(), fixture.mapping(), fixture.names()))
                .isInstanceOf(ScoreEvidence.InvalidScores.class);
    }

    @Test void duplicateCaseArmIsInvalidScores() throws Exception {
        Fixture fixture = fixture();
        List<String> keys = new ArrayList<>(Files.readAllLines(fixture.b().key()));
        List<String> scores = new ArrayList<>(Files.readAllLines(fixture.b().scores()));
        for (int i = 0; i < 2; i++) {
            ObjectNode key = (ObjectNode) JSON.readTree(keys.get(i));
            String originalId = key.path("itemId").asText();
            String newId = "blind-" + (i + 3);
            key.put("itemId", newId).put("workflow", "ASK_COMPANION");
            keys.add(key.toString());
            ObjectNode score = (ObjectNode) JSON.readTree(scores.stream()
                    .filter(line -> line.contains(originalId)).findFirst().orElseThrow());
            score.put("itemId", newId).put("workflow", "ASK_COMPANION");
            scores.add(score.toString());
        }
        Files.write(fixture.b().key(), keys);
        Files.write(fixture.b().scores(), scores);
        assertThatThrownBy(() -> ScoreEvidence.load(fixture.a(), fixture.b(), fixture.mapping(), fixture.names()))
                .isInstanceOf(ScoreEvidence.InvalidScores.class).hasMessageContaining("Duplicate class-B case arm");
    }

    @Test void candidateAndBaselineLabelsMustBeDistinctAndPresent() {
        assertThatThrownBy(() -> new ScoreEvidence.ClassBArmNames(null, ignored -> MINI))
                .isInstanceOf(ScoreEvidence.InvalidScores.class);
        assertThatThrownBy(() -> new ScoreEvidence.ClassBArmNames(" ", ignored -> MINI))
                .isInstanceOf(ScoreEvidence.InvalidScores.class);
        assertThatThrownBy(() -> new ScoreEvidence.ClassBArmNames(LUNA, ignored -> null))
                .isInstanceOf(ScoreEvidence.InvalidScores.class);
        assertThatThrownBy(() -> new ScoreEvidence.ClassBArmNames(LUNA, ignored -> " "))
                .isInstanceOf(ScoreEvidence.InvalidScores.class);
        assertThatThrownBy(() -> new ScoreEvidence.ClassBArmNames(LUNA, ignored -> LUNA))
                .isInstanceOf(ScoreEvidence.InvalidScores.class);
    }

    @Test void classAKeyingIsUnchangedByTheMappedOverload() throws Exception {
        Fixture fixture = fixture();
        Files.writeString(fixture.a().key(), JSON.writeValueAsString(Map.of("id", "blind-a", "arm", LUNA,
                "caseId", "class-a", "workflow", "TEACHER_QUIZ", "questionFormat", "MCQ")) + "\n");
        ObjectNode score = JSON.createObjectNode();
        score.put("id", "blind-a").put("reviewerId", HUMAN).put("round", "PRIMARY")
                .put("factuallyCorrect", "YES").put("grounding", "GROUNDED")
                .put("critical", false).putNull("criticalType");
        score.put("distractorPlausibility", 5).put("modeAlignment", 5).put("cognitiveDemand", 5)
                .put("clarity", 5).put("faithfulness", 5).put("seriousGiveaway", false).putNull("giveawayPattern");
        Files.writeString(fixture.a().primary(), score + "\n");
        ScoreEvidence.Loaded loaded = ScoreEvidence.load(fixture.a(), fixture.b(), fixture.mapping(), fixture.names());
        assertThat(loaded.get("class-a", LUNA)).isNotNull();
        assertThat(loaded.get("class-a", "CANDIDATE")).isNull();
    }

    private Fixture fixture() throws Exception {
        Path exportRoot = temp.resolve("blind");
        Files.createDirectories(exportRoot);
        ClassBBlindPacketExport.Export export = BlindReviewFixtures.classB(exportRoot, 1);
        Path scores = exportRoot.resolve("scores.jsonl");
        List<String> rows = new ArrayList<>();
        for (ClassBScoreAggregator.Key key : export.armKey()) {
            ObjectNode row = JSON.createObjectNode();
            row.put("itemId", key.itemId()).put("workflow", key.workflow().name()).put("round", 1)
                    .put("scorerId", HUMAN).put("scorerKind", "HUMAN")
                    .put("taskFulfilled", true).put("grounded", true).put("actionable", true)
                    .put("noCriticalMisleading", true).put("critical", false)
                    .put("borderline", false).put("adjudication", false).putNull("reasonCode");
            rows.add(row.toString());
        }
        Files.write(scores, rows);
        ScoreEvidence.FilesA a = new ScoreEvidence.FilesA(empty("a-key"), empty("a-primary"),
                empty("a-reviews"), empty("a-adjudications"), empty("a-exclusions"));
        ScoreEvidence.FilesB b = new ScoreEvidence.FilesB(exportRoot.resolve("arm-key.jsonl"), scores,
                Map.of(HUMAN, ClassBScoreFiles.ScorerKind.HUMAN));
        return new Fixture(a, b, ClassAScoreSchemaV2Test.MAPPING,
                new ScoreEvidence.ClassBArmNames(LUNA, ignored -> MINI));
    }

    private Path empty(String name) throws Exception {
        Path path = temp.resolve(name + ".jsonl");
        Files.writeString(path, "");
        return path;
    }
}
