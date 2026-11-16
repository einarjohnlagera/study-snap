package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Draws whole packet-line pairs from a canonical frame without any key input. */
final class PairSampler {
    static final class RedrawWithDifferentFrame extends RuntimeException {
        RedrawWithDifferentFrame(String message) { super(message); }
    }
    private static final ObjectMapper JSON = new ObjectMapper();
    private PairSampler() {}
    static DrawLog.Entry draw(SeedStreamRegistry registry, DrawLog log, PairMap pairs, String workflow,
                              String purpose, int round, List<String> framePairIds, int count, String clockUtc) {
        if (count < 1 || framePairIds == null || pairs == null || log == null)
            throw new DrawLog.InvalidDrawLog("Draw inputs and positive count required");
        DrawLog.checkUtc(clockUtc);
        SeedStream stream = SeedStream.of(registry, workflow, purpose, round);
        List<String> frame = new ArrayList<>(framePairIds);
        Set<String> unique = new HashSet<>(frame);
        if (unique.size() != frame.size()) throw new DrawLog.InvalidDrawLog("Duplicate frame pair id");
        for (String id : frame) {
            String actual = pairs.workflowOf(id);
            if (actual == null || !actual.equals(workflow))
                throw new DrawLog.InvalidDrawLog("Unknown or wrong-workflow frame pair: " + id);
        }
        frame.sort(String::compareTo);
        String hash = FreezeRecord.sha256(String.join("\n", frame).getBytes(StandardCharsets.UTF_8));
        for (DrawLog.Entry entry : log.entries()) if (entry.workflow().equals(workflow)
                && entry.purpose().equals(purpose) && entry.round() == round) {
            List<String> differences = new ArrayList<>();
            if (!entry.frameSha256().equals(hash)) differences.add(DrawLog.F_FRAMESHA256);
            if (entry.count() != count) differences.add(DrawLog.F_COUNT);
            if (!entry.registrySha256().equals(registry.sha256())) differences.add(DrawLog.F_REGISTRYSHA256);
            if (!differences.isEmpty()) throw new RedrawWithDifferentFrame("Redraw differs in " + differences);
            return entry;
        }
        stream.shuffle(frame);
        List<String> chosen = frame.subList(0, Math.min(count, frame.size()));
        ObjectNode node = JSON.createObjectNode();
        node.put(DrawLog.F_WORKFLOW, workflow).put(DrawLog.F_PURPOSE, purpose).put(DrawLog.F_ROUND, round)
                .put(DrawLog.F_FRAMESHA256, hash).put(DrawLog.F_FRAMESIZE, frame.size()).put(DrawLog.F_COUNT, count);
        node.set(DrawLog.F_SELECTEDPAIRIDS, JSON.valueToTree(chosen));
        node.put(DrawLog.F_EXHAUSTED, frame.size() < count).put(DrawLog.F_REGISTRYSHA256, registry.sha256())
                .put(DrawLog.F_CLOCKUTC, clockUtc);
        NoArmFields.check(node.toString());
        log.append(node);
        return log.entries().getLast();
    }
}
