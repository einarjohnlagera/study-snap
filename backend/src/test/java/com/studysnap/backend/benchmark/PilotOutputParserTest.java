package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PilotOutputParserTest {
    private static final String L_BLIND_00001 = "blind-00001";
    private static final String L_BLIND_00002 = "blind-00002";
    private static final String L_P = "P";
    private static final String L_NEWLINE = "\n";
    private static final String L_FENCE_START = "```json\n";
    private static final String L_FENCE_END = "\n```";
    private static final String ONE = "{\"id\":\"blind-00001\",\"value\":1}";
    private static final String TWO = "{\"id\":\"blind-00002\",\"value\":2}";
    private static final Set<String> IDS = Set.of(L_BLIND_00001, L_BLIND_00002);
    @TempDir Path temp;
    private PilotOutputParser.ParseResult parse(String raw) { return PilotOutputParser.parse(raw, IDS, L_P); }

    @Test void cleanJsonlAndSingleFencePreserveRows() {
        var clean = parse(ONE + L_NEWLINE + TWO);
        assertThat(clean.status()).isEqualTo(PilotOutputParser.Status.PARSED);
        assertThat(clean.accepted()).extracting(JsonNode::toString).containsExactly(ONE, TWO);
        assertThat(clean.retryEligible()).isFalse();
        assertThat(parse(L_FENCE_START + ONE + L_FENCE_END).accepted()).hasSize(1);
        assertThat(parse("Analysis before\n```json\n" + ONE + "\n```\nAfter").accepted()).hasSize(1);
    }
    @Test void onlyWholeOutputFailuresAreRetryEligible() {
        for (String raw : List.of("", "not json", "{}\nnot json", L_FENCE_START + ONE + "\n```\n```json\n" + TWO + L_FENCE_END,
                "{\"id\":\"blind-00001\",\"id\":\"blind-00002\"}", "[]", ONE + "{}")) {
            var result = parse(raw);
            assertThat(result.status()).isEqualTo(PilotOutputParser.Status.WHOLE_OUTPUT_UNPARSEABLE);
            assertThat(result.retryEligible()).isTrue();
        }
    }
    @Test void strayDuplicateAndMissingIdsAreData() {
        var noId = parse("{\"value\":1}");
        assertThat(noId.stray()).extracting(PilotOutputParser.Stray::reason).containsExactly("NO_ID");
        assertThat(noId.retryEligible()).isFalse();
        var stray = parse("{\"id\":\"elsewhere\"}");
        assertThat(stray.stray()).extracting(PilotOutputParser.Stray::reason).containsExactly("NOT_IN_PACKET");
        var duplicate = parse(ONE + L_NEWLINE + ONE);
        assertThat(duplicate.duplicateIds()).containsExactly(L_BLIND_00001);
        assertThat(duplicate.accepted()).isEmpty();
        assertThat(duplicate.retryEligible()).isFalse();
        assertThat(parse(ONE).accepted()).hasSize(1);
    }
    @Test void invalidRoleAndDryRunWritingRejected() {
        assertThatThrownBy(() -> PilotOutputParser.parse(ONE, IDS, "X"))
                .isInstanceOf(PilotOutputParser.InvalidPilotOutput.class);
        assertThatThrownBy(() -> PilotOutputParser.parse(null, IDS, L_P))
                .isInstanceOf(PilotOutputParser.InvalidPilotOutput.class);
        var dry = new PilotOutputParser.AttemptResult(PilotOutputParser.AttemptKind.ISOLATION_DRY_RUN, parse(ONE));
        assertThatThrownBy(() -> PilotOutputParser.writeScorerFile(temp.resolve("dry.jsonl"), List.of(dry)))
                .isInstanceOf(PilotOutputParser.InvalidPilotOutput.class);
    }
    @Test void scorerFileOmitsStraysAndNeverOverwrites() throws Exception {
        var result = parse(ONE + "\n{\"id\":\"elsewhere\"}");
        Path file = temp.resolve("scorer.jsonl");
        var attempt = new PilotOutputParser.AttemptResult(PilotOutputParser.AttemptKind.SCORING, result);
        PilotOutputParser.writeScorerFile(file, List.of(attempt));
        assertThat(Files.readString(file)).isEqualTo(ONE + L_NEWLINE);
        assertThatThrownBy(() -> PilotOutputParser.writeScorerFile(file, List.of(attempt)))
                .isInstanceOf(PilotOutputParser.InvalidPilotOutput.class);
    }
    @Test void acceptedJsonlLineIsWrittenByteForByte() throws Exception {
        String original = "{ \"id\" : \"blind-00001\", \"quoted\" : \"π $x_1$\" }";
        var parsed = parse(original);
        assertThat(parsed.acceptedLines()).containsExactly(original);
        Path file = temp.resolve("verbatim.jsonl");
        PilotOutputParser.writeScorerFile(file, List.of(new PilotOutputParser.AttemptResult(
                PilotOutputParser.AttemptKind.SCORING, parsed)));
        assertThat(Files.readString(file)).isEqualTo(original + L_NEWLINE);
    }
    @Test void unchangedLoaderTreatsPerItemInvalidAsDataAndEmptyHumanAsZeroRows() throws Exception {
        var exported = ClassAPacketIndexTest.export(temp);
        var packets = ClassAPacketIndex.load(temp.resolve("packets"));
        Path pFile = temp.resolve("p.jsonl"), sFile = temp.resolve("s.jsonl"), hFile = temp.resolve("h.jsonl");
        var validP = ClassAScoreSchemaV2Test.scorer(L_BLIND_00001, L_P);
        var invalidP = ClassAScoreSchemaV2Test.scorer(L_BLIND_00002, L_P);
        invalidP.put("keyDefensible", false);
        var parsed = PilotOutputParser.parse(validP + L_NEWLINE + invalidP,
                Set.of(L_BLIND_00001, L_BLIND_00002), L_P);
        assertThat(parsed.retryEligible()).isFalse();
        PilotOutputParser.writeScorerFile(pFile, List.of(new PilotOutputParser.AttemptResult(
                PilotOutputParser.AttemptKind.SCORING, parsed)));
        Files.writeString(sFile, ClassAScoreSchemaV2Test.scorer(L_BLIND_00001, "S") + L_NEWLINE);
        Files.write(hFile, new byte[0]);
        var loaded = ClassAScoreFilesV2.load(exported.armKey(), packets, pFile, sFile, hFile,
                ClassAScoreSchemaV2Test.MAPPING);
        assertThat(loaded.get(L_BLIND_00002).p().status()).isEqualTo(ClassAScoreFilesV2.Status.INVALID);
        assertThat(loaded.get(L_BLIND_00001).h()).isNull();
    }
}
