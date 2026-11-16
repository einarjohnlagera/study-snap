package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.dto.QuizItem;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Delivered-content and content-free receipt mutants for preregistration section 7. */
class DeliveredOutputsTest {
    private static final String ARM = "BASELINE_MINI";
    private static final String MODEL = "gpt-4.1-mini";
    private static final String QUESTION = "private-question-marker";
    private static final String EXPLANATION = "private-explanation-marker";
    private static final String NOTE = "private-note-marker";
    private static final String HASH = "a".repeat(64);
    @TempDir Path temp;

    @Test void persistsFullQuizItemAndEveryFailedOrInconclusiveRowWithLinkedHashes() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        QuizItem item = json.readValue("{\"question\":\"" + QUESTION
                + "\",\"choices\":[\"B. Smith\",\"Wrong B\",\"Wrong C\",\"Wrong D\"],\"correctIndex\":0,"
                + "\"explanation\":\"" + EXPLANATION + "\",\"questionFormat\":\"MCQ\"}", QuizItem.class);
        List<CaseArmOutcome> outcomes = List.of(
                CaseArmOutcome.classify("complete", List.of(), true, false, null),
                CaseArmOutcome.classify("failed", List.of(), false, false, "SHORT_DELIVERY"),
                CaseArmOutcome.classify("inconclusive", List.of(), false, true, "INCONCLUSIVE_UNMETERED"));
        Map<String, String> workflows = Map.of("complete", "NOTE_ADAPTIVE", "failed", "NOTE_ADAPTIVE",
                "inconclusive", "NOTE_ADAPTIVE");
        Map<String, String> hashes = Map.of("complete", HASH, "failed", HASH, "inconclusive", HASH);
        Path output = temp.resolve("delivered-outputs.jsonl");
        DeliveredOutputs.Written written = DeliveredOutputs.write(output, ARM, 0, outcomes,
                workflows, hashes, Map.of("complete", List.of(item)));
        var run = new StageRunner.RunSummary(outcomes,
                new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.TEN)),
                Map.of(), Map.of("complete", List.of(item)));
        Stage1Receipt receipt = Stage1Receipt.fromRun(
                FreezeGuard.dryEvidence(null, HASH, "b".repeat(40)), ARM, MODEL,
                StageCapLedger.Stage.STAGE_1, Instant.now(), HASH, hashes, workflows, run, written);
        Path receiptPath = temp.resolve("receipt.json");
        receipt.write(receiptPath);
        List<DeliveredOutputs.Line> lines = DeliveredOutputs.read(output, receipt);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0).content().get(0).path("correctIndex").asInt()).isZero();
        assertThat(lines.get(0).content().get(0).path("explanation").asText()).isEqualTo(EXPLANATION);
        assertThat(lines.get(0).content().get(0).path("choices").get(0).asText()).isEqualTo("B. Smith");
        assertThat(lines.get(1).content().isNull()).isTrue();
        assertThat(lines.get(2).content().isNull()).isTrue();
        assertThat(lines.get(1).failureClass()).isEqualTo("SHORT_DELIVERY");
        String receiptText = Files.readString(receiptPath);
        assertThat(receiptText).contains("deliveredOutputSha256", "deliveredOutputsFileSha256");
        assertThat(receiptText).doesNotContain(QUESTION, EXPLANATION, NOTE);
        Files.writeString(output, Files.readString(output).replace(QUESTION, NOTE));
        assertThatThrownBy(() -> DeliveredOutputs.read(output, receipt))
                .isInstanceOf(DeliveredOutputs.InvalidOutput.class);
    }
}
