package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PilotInvocationLogTest {
    private static final String L_P = "P";
    private static final String L_TEST_PRIMARY = "test-primary";
    private static final String L_TEST_SECONDARY = "test-secondary";
    private static final String L_AGENT_RESULT = "AGENT_RESULT";
    private static final String L_LOG_JSONL = "log.jsonl";
    private static final String L_PACKET_0 = "packet-0";
    private static final String L_ISOLATION_DRY_RUN = "ISOLATION_DRY_RUN";
    private static final String L_PARSED = "PARSED";
    private static final String L_PACKET_1 = "packet-1";
    private static final String L_SCORING = "SCORING";
    private static final String L_WHOLE_OUTPUT_UNPARSEABLE = "WHOLE_OUTPUT_UNPARSEABLE";
    private static final String L_RETRY = "RETRY";
    private static final String L_S = "S";
    private static final String L_PACKET_2 = "packet-2";
    private static final String L_RETRY_NOT_AFTER_WHOLE_OUTPUT_FAILURE = "RETRY_NOT_AFTER_WHOLE_OUTPUT_FAILURE";
    private static final String L_REFUSAL = "REFUSAL";
    private static final String L_WRONG = "wrong";
    private static final String L_MODEL_MISMATCH = "MODEL_MISMATCH";
    private static final String L_MODEL_UNVERIFIABLE = "MODEL_UNVERIFIABLE";
    private static final String L_NONE = "NONE";
    private static final String L_INVOCATION_AFTER_STOP = "INVOCATION_AFTER_STOP";
    private static final String STAMP = "2026-11-16T00:00:00Z";
    private static final String DIGEST = FreezeRecord.sha256("prompt".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final String KEY_HASH = FreezeRecord.sha256("key".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    @TempDir Path temp;
    private PilotBars bars() throws Exception { return PilotTestKeys.bars(temp); }
    private PilotInvocationLog.Invocation call(String role, String packet, String kind, String outcome) {
        return new PilotInvocationLog.Invocation(role, packet, kind, L_P.equals(role) ? L_TEST_PRIMARY : L_TEST_SECONDARY,
                L_P.equals(role) ? L_TEST_PRIMARY : L_TEST_SECONDARY, L_AGENT_RESULT, DIGEST,
                100L, 20L, outcome, 0, STAMP);
    }
    private PilotInvocationLog.LogVerdict validate(List<PilotInvocationLog.Invocation> entries) throws Exception {
        PilotBars bars = bars();
        Path file = temp.resolve(L_LOG_JSONL);
        Files.deleteIfExists(file);
        PilotInvocationLog.append(file, new PilotInvocationLog.Header(bars.sha256(), KEY_HASH, STAMP));
        Map<PilotInvocationLog.RolePacket, ScorerPromptRender.PromptRecord> prompts = new HashMap<>();
        for (var entry : entries) {
            prompts.put(new PilotInvocationLog.RolePacket(entry.role(), entry.packetId()),
                    new ScorerPromptRender.PromptRecord(entry.role(), DIGEST, DIGEST, temp.resolve("unused"), 0));
            PilotInvocationLog.append(file, entry);
        }
        return PilotInvocationLog.validate(file, bars, prompts, bars.sha256());
    }
    private void invalid(String violation, PilotInvocationLog.Invocation... entries) throws Exception {
        assertThat(validate(List.of(entries)).violations()).contains(violation);
    }
    @Test void validLogAndAppendGeneratedChain() throws Exception {
        var verdict = validate(List.of(call(L_P, L_PACKET_0, L_ISOLATION_DRY_RUN, L_PARSED),
                call(L_P, L_PACKET_1, L_SCORING, L_WHOLE_OUTPUT_UNPARSEABLE),
                call(L_P, L_PACKET_1, L_RETRY, L_PARSED), call(L_S, L_PACKET_0, L_SCORING, L_PARSED)));
        assertThat(verdict.valid()).isTrue();
        List<String> lines = Files.readAllLines(temp.resolve(L_LOG_JSONL));
        assertThat(lines.get(1)).contains("\"seq\":1");
        assertThat(lines.get(1)).contains(FreezeRecord.sha256(lines.get(0).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> PilotInvocationLog.append(temp.resolve(L_LOG_JSONL),
                new PilotInvocationLog.Header(DIGEST, KEY_HASH, STAMP))).isInstanceOf(PilotInvocationLog.InvalidPilotLog.class);
    }
    @Test void editDeleteReorderAndInsertBreakChainAndAppendRefusesBrokenLog() throws Exception {
        validate(List.of(call(L_P, L_PACKET_1, L_SCORING, L_PARSED),
                call(L_S, L_PACKET_2, L_SCORING, L_PARSED)));
        Path file = temp.resolve(L_LOG_JSONL);
        List<String> original = Files.readAllLines(file);
        List<List<String>> mutations = new ArrayList<>();
        List<String> edit = new ArrayList<>(original); edit.set(1, edit.get(1).replace(L_PACKET_1, "packet-9")); mutations.add(edit);
        List<String> delete = new ArrayList<>(original); delete.remove(1); mutations.add(delete);
        List<String> reorder = new ArrayList<>(original); java.util.Collections.swap(reorder, 1, 2); mutations.add(reorder);
        List<String> insert = new ArrayList<>(original); insert.add(1, original.get(1)); mutations.add(insert);
        PilotBars bars = bars();
        for (List<String> lines : mutations) {
            Files.write(file, lines);
            var verdict = PilotInvocationLog.validate(file, bars, Map.of(), bars.sha256());
            assertThat(verdict.valid()).isFalse();
            assertThat(verdict.violations()).anyMatch(value -> value.contains("BROKEN"));
            assertThatThrownBy(() -> PilotInvocationLog.append(file, call(L_P, "packet-3", L_SCORING, L_PARSED)))
                    .isInstanceOf(PilotInvocationLog.InvalidPilotLog.class);
        }
    }
    @Test void retryRulesAndCaps() throws Exception {
        invalid(L_RETRY_NOT_AFTER_WHOLE_OUTPUT_FAILURE, call(L_P, L_PACKET_1, L_SCORING, L_PARSED),
                call(L_P, L_PACKET_1, L_RETRY, L_PARSED));
        invalid(L_RETRY_NOT_AFTER_WHOLE_OUTPUT_FAILURE, call(L_P, L_PACKET_1, L_SCORING, L_REFUSAL),
                call(L_P, L_PACKET_1, L_RETRY, L_PARSED));
        invalid(L_RETRY_NOT_AFTER_WHOLE_OUTPUT_FAILURE, call(L_P, L_PACKET_1, L_SCORING, "ERROR"),
                call(L_P, L_PACKET_1, L_RETRY, L_PARSED));
        invalid(L_RETRY_NOT_AFTER_WHOLE_OUTPUT_FAILURE, call(L_P, L_PACKET_1, L_RETRY, L_PARSED));
        invalid(L_RETRY_NOT_AFTER_WHOLE_OUTPUT_FAILURE, call(L_P, L_PACKET_1, L_SCORING, L_WHOLE_OUTPUT_UNPARSEABLE),
                call(L_P, L_PACKET_2, L_SCORING, L_PARSED), call(L_P, L_PACKET_1, L_RETRY, L_PARSED));
        assertThat(validate(List.of(call(L_P, L_PACKET_1, L_SCORING, L_WHOLE_OUTPUT_UNPARSEABLE),
                call(L_S, L_PACKET_2, L_SCORING, L_PARSED), call(L_P, L_PACKET_1, L_RETRY, L_PARSED))).violations())
                .doesNotContain(L_RETRY_NOT_AFTER_WHOLE_OUTPUT_FAILURE);
        invalid("RETRY_CAP_EXCEEDED", call(L_P, L_PACKET_1, L_SCORING, L_WHOLE_OUTPUT_UNPARSEABLE),
                call(L_P, L_PACKET_1, L_RETRY, L_WHOLE_OUTPUT_UNPARSEABLE),
                call(L_P, L_PACKET_1, L_RETRY, L_PARSED));
        invalid("DUPLICATE_SCORING", call(L_P, L_PACKET_1, L_SCORING, L_PARSED),
                call(L_P, L_PACKET_1, L_SCORING, L_PARSED));
    }
    @Test void dryRunModelPromptAndMetadataAreChecked() throws Exception {
        invalid("DRY_RUN_PACKET_MISMATCH", call(L_P, L_PACKET_1, L_ISOLATION_DRY_RUN, L_PARSED));
        var base = call(L_P, L_PACKET_1, L_SCORING, L_PARSED);
        invalid("REQUESTED_MODEL_MISMATCH", new PilotInvocationLog.Invocation(L_P, L_PACKET_1, L_SCORING, L_WRONG,
                base.reportedModel(), base.reportedModelSource(), DIGEST, 1L, 1L, L_PARSED, 0, STAMP));
        var mismatch = new PilotInvocationLog.Invocation(L_P, L_PACKET_1, L_SCORING, base.requestedModel(),
                "other-model", L_AGENT_RESULT, DIGEST, 1L, 1L, L_PARSED, 0, STAMP);
        assertThat(validate(List.of(mismatch)).stoppedByRole()).containsEntry(L_P, L_MODEL_MISMATCH)
                .containsEntry(L_S, L_MODEL_MISMATCH);
        invalid(L_MODEL_UNVERIFIABLE, new PilotInvocationLog.Invocation(L_P, L_PACKET_1, L_SCORING,
                base.requestedModel(), null, L_NONE, DIGEST, 1L, 1L, L_PARSED, 0, STAMP));
        invalid(L_MODEL_UNVERIFIABLE, new PilotInvocationLog.Invocation(L_P, L_PACKET_1, L_SCORING,
                base.requestedModel(), null, L_AGENT_RESULT, DIGEST, 1L, 1L, L_PARSED, 0, STAMP));
        invalid(L_MODEL_UNVERIFIABLE, new PilotInvocationLog.Invocation(L_P, L_PACKET_1, L_SCORING,
                base.requestedModel(), base.reportedModel(), L_NONE, DIGEST, 1L, 1L, L_PARSED, 0, STAMP));
        invalid("TOKEN_STOP_UNVERIFIABLE", new PilotInvocationLog.Invocation(L_P, L_PACKET_1, L_SCORING,
                base.requestedModel(), base.reportedModel(), L_AGENT_RESULT, DIGEST, null, 1L, L_PARSED, 0, STAMP));
        invalid("PROMPT_HASH_MISMATCH", new PilotInvocationLog.Invocation(L_P, L_PACKET_1, L_SCORING,
                base.requestedModel(), base.reportedModel(), L_AGENT_RESULT, KEY_HASH, 1L, 1L, L_PARSED, 0, STAMP));
    }
    @Test void capTokenStopAndRoleLocalStops() throws Exception {
        List<PilotInvocationLog.Invocation> entries = new ArrayList<>();
        for (int i = 0; i < 14; i++) entries.add(call(L_P, "packet-" + i, L_SCORING, L_PARSED));
        assertThat(validate(entries).violations()).contains(L_INVOCATION_AFTER_STOP, "INVOCATION_CAP_EXCEEDED");
        var base = call(L_P, L_PACKET_1, L_SCORING, L_PARSED);
        var crossing = new PilotInvocationLog.Invocation(L_P, L_PACKET_1, L_SCORING, base.requestedModel(),
                base.reportedModel(), L_AGENT_RESULT, DIGEST, 150001L, 0L, L_PARSED, 0, STAMP);
        var stopped = validate(List.of(crossing));
        assertThat(stopped.valid()).isTrue();
        assertThat(stopped.stoppedByRole()).containsEntry(L_P, "TOKEN_STOP").doesNotContainKey(L_S);
        invalid(L_INVOCATION_AFTER_STOP, crossing, call(L_P, L_PACKET_2, L_SCORING, L_PARSED));
        assertThat(validate(List.of(crossing, call(L_S, L_PACKET_2, L_SCORING, L_PARSED))).valid()).isTrue();
    }
    @Test void refusalLimitLeakAndMismatchStopFurtherInvocations() throws Exception {
        for (String outcome : List.of(L_REFUSAL, "LIMIT_REACHED"))
            invalid(L_INVOCATION_AFTER_STOP, call(L_P, L_PACKET_1, L_SCORING, outcome),
                    call(L_P, L_PACKET_2, L_SCORING, L_PARSED));
        var base = call(L_P, L_PACKET_1, L_SCORING, L_PARSED);
        var leak = new PilotInvocationLog.Invocation(L_P, L_PACKET_1, L_SCORING, base.requestedModel(),
                base.reportedModel(), L_AGENT_RESULT, DIGEST, 1L, 1L, L_PARSED, 1, STAMP);
        invalid(L_INVOCATION_AFTER_STOP, leak, call(L_S, L_PACKET_2, L_SCORING, L_PARSED));
        var mismatch = new PilotInvocationLog.Invocation(L_P, L_PACKET_1, L_SCORING, base.requestedModel(),
                L_WRONG, L_AGENT_RESULT, DIGEST, 1L, 1L, L_PARSED, 0, STAMP);
        invalid(L_INVOCATION_AFTER_STOP, mismatch, call(L_S, L_PACKET_2, L_SCORING, L_PARSED));
    }
    @Test void headerHashMustMatchFrozenBars() throws Exception {
        PilotBars bars = bars();
        Path file = temp.resolve("wrong-header.jsonl");
        PilotInvocationLog.append(file, new PilotInvocationLog.Header(KEY_HASH, KEY_HASH, STAMP));
        var verdict = PilotInvocationLog.validate(file, bars, Map.of(), bars.sha256());
        assertThat(verdict.violations()).contains("BARS_HASH_MISMATCH");
    }
}
