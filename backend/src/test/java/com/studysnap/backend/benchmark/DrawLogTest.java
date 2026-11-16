package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DrawLogTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;
    private DrawLog log() throws Exception {
        BlindReviewFixtures.classA(temp, 3);
        PairMap pairs = PairMap.fromPackets(temp.resolve("packets"));
        SeedStreamRegistry registry = SeedStreamRegistry.load(BlindReviewFixtures.registry(temp));
        DrawLog log = new DrawLog(temp.resolve("draw.jsonl"));
        PairSampler.draw(registry, log, pairs, BlindReviewFixtures.WORKFLOW_A, "SPOT_CHECK", 1,
                pairs.pairs().stream().map(PairMap.Pair::id).toList(), 2, BlindReviewFixtures.CLOCK);
        return log;
    }
    private ObjectNode first(DrawLog log) throws Exception {
        return (ObjectNode) JSON.readTree(Files.readAllLines(log.path()).getFirst());
    }
    @Test void validateRejectsDuplicateDrawEvenWhenSequenceAndChainAreValid() throws Exception {
        DrawLog log = log();
        String firstLine = Files.readAllLines(log.path()).getFirst();
        ObjectNode duplicate = first(log);
        duplicate.put("seq", 1).put("prevSha256", FreezeRecord.sha256(firstLine.getBytes(StandardCharsets.UTF_8)));
        Files.writeString(log.path(), firstLine + "\n" + duplicate + "\n");
        Path logPath = log.path();
        assertThatThrownBy(() -> DrawLog.validate(logPath))
                .isInstanceOf(DrawLog.InvalidDrawLog.class).hasMessageContaining("Duplicate draw stream");
    }
    @Test void validateRejectsUnknownFieldDuplicateIdsAndNonUtcTime() throws Exception {
        DrawLog log = log();
        Path logPath = log.path();
        ObjectNode row = first(log);
        row.put("unknown", "value");
        Files.writeString(log.path(), row + "\n");
        assertThatThrownBy(() -> DrawLog.validate(logPath)).isInstanceOf(DrawLog.InvalidDrawLog.class);
        row.remove("unknown");
        row.putArray("selectedPairIds").add("NOTE_ADAPTIVE#1").add("NOTE_ADAPTIVE#1");
        Files.writeString(log.path(), row + "\n");
        assertThatThrownBy(() -> DrawLog.validate(logPath)).isInstanceOf(DrawLog.InvalidDrawLog.class);
        row.putArray("selectedPairIds").add("NOTE_ADAPTIVE#1");
        row.put("clockUtc", "2026-11-16T08:00:00+08:00");
        Files.writeString(log.path(), row + "\n");
        assertThatThrownBy(() -> DrawLog.validate(logPath)).isInstanceOf(DrawLog.InvalidDrawLog.class);
    }
    @Test void appendRefusesBrokenExistingChain() throws Exception {
        DrawLog log = log();
        ObjectNode second = first(log);
        second.put("round", 2);
        log.append(second);
        List<String> lines = Files.readAllLines(log.path());
        Files.writeString(log.path(), lines.get(0).replace("NOTE_ADAPTIVE", "STUDY_TIP")
                + "\n" + lines.get(1) + "\n");
        ObjectNode third = second.deepCopy();
        third.put("purpose", "EXPANSION");
        assertThatThrownBy(() -> log.append(third)).isInstanceOf(DrawLog.InvalidDrawLog.class);
    }
}
