package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PairSamplerTest {
    private static final String PURPOSE = "SPOT_CHECK";
    private static final String WORKFLOW = BlindReviewFixtures.WORKFLOW_A;
    private static final String CLOCK = BlindReviewFixtures.CLOCK;
    @TempDir Path temp;
    private SeedStreamRegistry registry() throws Exception {
        return SeedStreamRegistry.load(BlindReviewFixtures.registry(temp));
    }
    private PairMap pairs() { BlindReviewFixtures.classA(temp, 8); return PairMap.fromPackets(temp.resolve("packets")); }
    private List<String> frame(PairMap pairs) { return pairs.pairs().stream().map(PairMap.Pair::id).toList(); }
    private DrawLog.Entry draw(SeedStreamRegistry registry, DrawLog log, PairMap pairs, List<String> frame,
                               int count, int round) {
        return PairSampler.draw(registry, log, pairs, WORKFLOW, PURPOSE, round, frame, count, CLOCK);
    }
    @Test void canonicalFrameAndRoundDetermineWholePairSelection() throws Exception {
        PairMap pairs = pairs(); SeedStreamRegistry registry = registry();
        List<String> frame = frame(pairs);
        DrawLog.Entry first = draw(registry, new DrawLog(temp.resolve("one.jsonl")), pairs, frame, 3, 1);
        assertThat(first.frameSha256()).isEqualTo("0fd7dc3f875d4f5e152b635751c972dd4b174ecd6296c78a5bed4935bdeae799");
        List<String> reversed = new ArrayList<>(frame); java.util.Collections.reverse(reversed);
        DrawLog.Entry same = draw(registry, new DrawLog(temp.resolve("two.jsonl")), pairs, reversed, 3, 1);
        assertThat(same.selectedPairIds()).isEqualTo(first.selectedPairIds());
        assertThat(Files.readString(temp.resolve("one.jsonl"))).isEqualTo(Files.readString(temp.resolve("two.jsonl")));
        DrawLog.Entry other = draw(registry, new DrawLog(temp.resolve("three.jsonl")), pairs, frame, 3, 2);
        assertThat(other.selectedPairIds()).isNotEqualTo(first.selectedPairIds());
        for (String id : first.selectedPairIds()) {
            assertThat(pairs.itemsOf(id, "A")).isNotEmpty();
            assertThat(pairs.itemsOf(id, "B")).isNotEmpty();
        }
    }
    @Test void exhaustedAndDrawOnceBehaveAsRecorded() throws Exception {
        PairMap pairs = pairs(); SeedStreamRegistry registry = registry();
        List<String> frame = frame(pairs);
        DrawLog log = new DrawLog(temp.resolve("draw.jsonl"));
        DrawLog.Entry first = draw(registry, log, pairs, frame, 20, 1);
        assertThat(first.exhausted()).isTrue();
        assertThat(first.selectedPairIds()).containsExactlyInAnyOrderElementsOf(frame);
        String before = Files.readString(log.path());
        assertThat(draw(registry, log, pairs, frame, 20, 1)).isEqualTo(first);
        assertThat(Files.readString(log.path())).isEqualTo(before);
        List<String> shorter = frame.subList(1, frame.size());
        assertThatThrownBy(() -> draw(registry, log, pairs, shorter, 20, 1))
                .isInstanceOf(PairSampler.RedrawWithDifferentFrame.class).hasMessageContaining("frameSha256");
        assertThatThrownBy(() -> draw(registry, log, pairs, frame, 3, 1))
                .isInstanceOf(PairSampler.RedrawWithDifferentFrame.class).hasMessageContaining("count");
    }
    @Test void wrongStemUndeclaredAndDuplicateFrameFail() throws Exception {
        PairMap a = pairs(); SeedStreamRegistry registry = registry();
        BlindReviewFixtures.classB(temp.resolve("other"), 1);
        String other = Files.readString(temp.resolve("other/packets/STUDY_TIP.jsonl"))
                .replace("blind-00001", "blind-99998").replace("blind-00002", "blind-99999");
        Files.writeString(temp.resolve("packets/STUDY_TIP.jsonl"), other);
        PairMap mixedPairs = PairMap.fromPackets(temp.resolve("packets"));
        List<String> mixed = new ArrayList<>(frame(a)); mixed.add("STUDY_TIP#1");
        DrawLog log = new DrawLog(temp.resolve("draw.jsonl"));
        assertThatThrownBy(() -> draw(registry, log, mixedPairs, mixed, 2, 1))
                .isInstanceOf(DrawLog.InvalidDrawLog.class);
        List<String> duplicates = List.of(frame(a).getFirst(), frame(a).getFirst());
        assertThatThrownBy(() -> draw(registry, log, a, duplicates, 2, 1))
                .isInstanceOf(DrawLog.InvalidDrawLog.class);
        List<String> canonical = frame(a);
        assertThatThrownBy(() -> draw(registry, log, a, canonical, 2, 3))
                .isInstanceOf(SeedStream.UndeclaredStream.class);
    }
    @Test void appendComputesSequenceAndPreviousHashAndRejectsTampering() throws Exception {
        PairMap pairs = pairs(); SeedStreamRegistry registry = registry();
        DrawLog log = new DrawLog(temp.resolve("draw.jsonl"));
        draw(registry, log, pairs, frame(pairs), 2, 1);
        ObjectNode supplied = (ObjectNode) new ObjectMapper().readTree(Files.readAllLines(log.path()).getFirst());
        supplied.put("round", 2).put("seq", 999).put("prevSha256", "wrong");
        log.append(supplied);
        List<DrawLog.Entry> valid = DrawLog.validate(log.path());
        assertThat(valid).hasSize(2);
        assertThat(valid.get(1).seq()).isEqualTo(1);
        assertThat(valid.get(1).prevSha256()).isEqualTo(FreezeRecord.sha256(Files.readAllLines(log.path()).getFirst().getBytes()));
        String original = Files.readString(log.path());
        Path logPath = log.path();
        String[] lines = original.stripTrailing().split("\n");
        Files.writeString(log.path(), lines[0].replace("NOTE_ADAPTIVE", "STUDY_TIP") + "\n" + lines[1] + "\n");
        assertThatThrownBy(() -> DrawLog.validate(logPath)).isInstanceOf(DrawLog.InvalidDrawLog.class);
        Files.writeString(log.path(), lines[1] + "\n");
        assertThatThrownBy(() -> DrawLog.validate(logPath)).isInstanceOf(DrawLog.InvalidDrawLog.class);
        Files.writeString(log.path(), lines[1] + "\n" + lines[0] + "\n");
        assertThatThrownBy(() -> DrawLog.validate(logPath)).isInstanceOf(DrawLog.InvalidDrawLog.class);
        Files.writeString(log.path(), original + lines[0]);
        assertThatThrownBy(() -> DrawLog.validate(logPath)).isInstanceOf(DrawLog.InvalidDrawLog.class);
        Files.writeString(log.path(), original);
        assertThat(DrawLog.validate(log.path())).hasSize(2);
    }
    @Test void duplicateStreamIsRejectedEvenWithValidChain() throws Exception {
        PairMap pairs = pairs(); SeedStreamRegistry registry = registry();
        DrawLog log = new DrawLog(temp.resolve("draw.jsonl"));
        draw(registry, log, pairs, frame(pairs), 2, 1);
        ObjectNode duplicate = (ObjectNode) new ObjectMapper().readTree(Files.readAllLines(log.path()).getFirst());
        assertThatThrownBy(() -> log.append(duplicate)).isInstanceOf(DrawLog.InvalidDrawLog.class);
    }
    @Test void armKeyDeletionDoesNotChangeMapDrawOrScanBytes() throws Exception {
        ClassABlindPacketExport.Export exported = BlindReviewFixtures.classA(temp, 8);
        SeedStreamRegistry registry = registry();
        PairMap before = PairMap.fromPackets(temp.resolve("packets"));
        PacketMetadataScan.scan(temp.resolve("packets"), java.util.Set.of("LUNA", "BASELINE_MINI"));
        DrawLog first = new DrawLog(temp.resolve("first.jsonl"));
        draw(registry, first, before, frame(before), 3, 1);
        String firstBytes = Files.readString(first.path());
        List<PairMap.Pair> firstMap = before.pairs();
        Files.delete(exported.armKey());
        PairMap after = PairMap.fromPackets(temp.resolve("packets"));
        PacketMetadataScan.scan(temp.resolve("packets"), java.util.Set.of("LUNA", "BASELINE_MINI"));
        DrawLog second = new DrawLog(temp.resolve("second.jsonl"));
        draw(registry, second, after, frame(after), 3, 1);
        assertThat(after.pairs()).isEqualTo(firstMap);
        assertThat(Files.readString(second.path())).isEqualTo(firstBytes);
        NoArmFields.check(second.path());
        assertThat(firstBytes).doesNotContain("arm", "LUNA", "BASELINE_MINI");
    }
    @Test void samplerSourceRunsArmGuardBeforeWriting() throws Exception {
        String source = Files.readString(Path.of("src/test/java/com/studysnap/backend/benchmark/PairSampler.java"));
        assertThat(source).contains("NoArmFields.check(node.toString());");
        assertThat(source.indexOf("NoArmFields.check(node.toString());"))
                .isLessThan(source.indexOf("log.append(node);"));
    }
}
