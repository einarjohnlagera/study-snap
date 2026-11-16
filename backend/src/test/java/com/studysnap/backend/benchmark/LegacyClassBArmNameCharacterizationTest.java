package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** Records the merged legacy class-B key namespace without changing that path. */
class LegacyClassBArmNameCharacterizationTest {
    private static final String CASE = "case-0";
    @TempDir Path temp;

    @Test void realClassBKeyAndScoresUseKeyLabelsWhilePipelineRequestsReceiptLabels() throws Exception {
        var export = BlindReviewFixtures.classB(temp, 1);
        Path score = temp.resolve("class-b-scores.jsonl");
        List<String> rows = export.manifest().stream().map(item -> """
                {"itemId":"%s","workflow":"STUDY_TIP","round":1,"scorerId":"HUMAN_A",
                "scorerKind":"HUMAN","taskFulfilled":true,"grounded":true,"actionable":true,
                "noCriticalMisleading":true,"critical":false,"reasonCode":null,
                "borderline":false,"adjudication":false}""".formatted(item.itemId()).replace("\n", "")).toList();
        Files.write(score, rows);
        Path aKey = empty("a-key.jsonl"), aPrimary = empty("a-primary.jsonl");
        Path reviews = empty("a-reviews.jsonl"), adjudications = empty("a-adjudications.jsonl");
        Path exclusions = empty("a-exclusions.jsonl");
        var loaded = ScoreEvidence.load(new ScoreEvidence.FilesA(aKey, aPrimary, reviews, adjudications, exclusions),
                new ScoreEvidence.FilesB(temp.resolve("arm-key.jsonl"), score,
                        Map.of("HUMAN_A", ClassBScoreFiles.ScorerKind.HUMAN)),
                ClassAScoreSchemaV2Test.MAPPING);
        assertThat(loaded.get(CASE, "BASELINE")).isNotNull();
        assertThat(loaded.get(CASE, "CANDIDATE")).isNotNull();
        assertThat(loaded.get(CASE, "LUNA")).isNull();
        assertThat(loaded.get(CASE, "BASELINE_MINI")).isNull();
    }

    private Path empty(String name) throws Exception {
        Path path = temp.resolve(name);
        Files.writeString(path, "");
        return path;
    }
}
