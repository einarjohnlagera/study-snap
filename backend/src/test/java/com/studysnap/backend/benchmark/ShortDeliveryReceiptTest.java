package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Receipt count compatibility and strict construction/loading controls. */
class ShortDeliveryReceiptTest {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final String CASE = "case-one";
    private static final String WORKFLOW = "NOTE_ADAPTIVE";
    private static final String HASH = "a".repeat(64);
    private static final String SHORT_DELIVERY = "SHORT_DELIVERY";
    private static final String STATE_FIELD = "state";
    private static final String FAILURE_CLASS_FIELD = "failureClass";
    @TempDir Path temp;

    private static Stage1Receipt.CaseRow row(CaseArmOutcome.State state, String failure, Integer count) {
        return new Stage1Receipt.CaseRow(CASE, WORKFLOW, HASH, state, false, false, failure,
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of("body"), null, count);
    }

    private static Stage1Receipt receipt(Stage1Receipt.CaseRow row) {
        return new Stage1Receipt("head", "freeze", "BASELINE_MINI", "model",
                StageCapLedger.Stage.STAGE_1, Instant.EPOCH, Instant.EPOCH, 0, false,
                "case-set", List.of(row), Map.of(), null);
    }

    @Test void smallSubstantialAndZeroShortfallsRoundTrip() throws Exception {
        for (int count : List.of(24, 10, 0)) {
            Path path = temp.resolve("short-" + count + ".json");
            receipt(row(CaseArmOutcome.State.FAILED, SHORT_DELIVERY, count)).write(path);
            assertThat(Stage1Receipt.read(path).cases().getFirst().deliveredQuestionCount()).isEqualTo(count);
            assertThat(Files.readString(path)).contains("\"deliveredQuestionCount\":" + count);
        }
    }

    @Test void absentCountKeepsExactlyTheLegacyTwelveRowNamesAndLegacyBytes() throws Exception {
        Stage1Receipt.CaseRow legacy = row(CaseArmOutcome.State.FAILED, SHORT_DELIVERY, null);
        String serialized = JSON.writeValueAsString(legacy);
        assertThat(JSON.readTree(serialized).properties().stream().map(Map.Entry::getKey).collect(
                java.util.stream.Collectors.toSet())).containsExactlyInAnyOrderElementsOf(Set.of(
                        "id", "workflow", "manifestRowSha256", STATE_FIELD, "firstAttemptClean",
                        "finalSuccess", FAILURE_CLASS_FIELD, "conservativeUsd", "billedUsd", "llmCalls",
                        "attempt1BodyHashes", "deliveredOutputSha256"));
        assertThat(serialized).doesNotContain("deliveredQuestionCount");
        String legacyRow = "{\"id\":\"case-one\",\"workflow\":\"NOTE_ADAPTIVE\","
                + "\"manifestRowSha256\":\"" + HASH + "\",\"state\":\"FAILED\","
                + "\"firstAttemptClean\":false,\"finalSuccess\":false,"
                + "\"failureClass\":\"SHORT_DELIVERY\",\"conservativeUsd\":0,\"billedUsd\":0,"
                + "\"llmCalls\":[],\"attempt1BodyHashes\":[\"body\"],\"deliveredOutputSha256\":null}";
        assertThat(serialized).isEqualTo(legacyRow);
        String old = "{\"head\":\"head\",\"freezeSha256\":\"freeze\",\"arm\":\"BASELINE_MINI\","
                + "\"model\":\"model\",\"stage\":\"STAGE_1\",\"startedAt\":0.0,\"endedAt\":0.0,"
                + "\"ledgerRecordCount\":0,\"unreconciled\":false,\"caseSetSha256\":\"case-set\","
                + "\"cases\":[" + legacyRow + "],\"triggers\":{},\"deliveredOutputsFileSha256\":null}";
        Path path = temp.resolve("legacy.json");
        Files.writeString(path, old);
        Stage1Receipt loaded = Stage1Receipt.read(path);
        assertThat(loaded.cases().getFirst().deliveredQuestionCount()).isNull();
        assertThat(JSON.writeValueAsString(loaded)).isEqualTo(old);
    }

    @Test void wrongStateClassOrNegativeCountRefusedAtConstructionAndLoad() throws Exception {
        assertThatThrownBy(() -> row(CaseArmOutcome.State.FAILED, "PARSE", 10))
                .isInstanceOf(Stage1Receipt.CaseRow.InvalidDeliveryCount.class);
        assertThatThrownBy(() -> row(CaseArmOutcome.State.COMPLETE, SHORT_DELIVERY, 10))
                .isInstanceOf(Stage1Receipt.CaseRow.InvalidDeliveryCount.class);
        assertThatThrownBy(() -> row(CaseArmOutcome.State.FAILED, SHORT_DELIVERY, -1))
                .isInstanceOf(Stage1Receipt.CaseRow.InvalidDeliveryCount.class);
        Path path = temp.resolve("invalid.json");
        for (String replacement : List.of("\"state\":\"COMPLETE\"", "\"failureClass\":\"PARSE\"",
                "\"deliveredQuestionCount\":-1")) {
            String valid = JSON.writeValueAsString(receipt(row(CaseArmOutcome.State.FAILED,
                    SHORT_DELIVERY, 10)));
            String invalid = replacement.contains(STATE_FIELD) ? valid.replace("\"state\":\"FAILED\"", replacement)
                    : replacement.contains(FAILURE_CLASS_FIELD) ? valid.replace("\"failureClass\":\"SHORT_DELIVERY\"", replacement)
                    : valid.replace("\"deliveredQuestionCount\":10", replacement);
            Files.writeString(path, invalid);
            assertThatThrownBy(() -> Stage1Receipt.read(path))
                    .isInstanceOf(Stage1Receipt.ProtocolDeviation.class);
        }
    }

    @Test void unknownPropertyStillRefusedAndCompareUsesBodyHashes() throws Exception {
        Stage1Receipt.CaseRow shortRow = row(CaseArmOutcome.State.FAILED, SHORT_DELIVERY, 24);
        Path path = temp.resolve("unknown.json");
        Files.writeString(path, JSON.writeValueAsString(receipt(shortRow)).replace(
                "\"deliveredQuestionCount\":24", "\"deliveredQuestionCount\":24,\"unknown\":true"));
        assertThatThrownBy(() -> Stage1Receipt.read(path)).isInstanceOf(Stage1Receipt.ProtocolDeviation.class);
        Stage1Receipt.CaseRow otherCount = row(CaseArmOutcome.State.FAILED, SHORT_DELIVERY, 10);
        assertThat(Stage1Receipt.compare(receipt(shortRow), receipt(otherCount)).getFirst().result())
                .isEqualTo(Stage1Receipt.Comparison.MATCH);
    }
}
