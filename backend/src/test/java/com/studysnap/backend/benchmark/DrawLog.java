package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Append-only review draw receipt, chained over exact line bytes. */
final class DrawLog {
    static final class InvalidDrawLog extends RuntimeException {
        InvalidDrawLog(String message) { super(message); }
        InvalidDrawLog(String message, Throwable cause) { super(message, cause); }
    }
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    static final String F_WORKFLOW = "workflow";
    static final String F_PURPOSE = "purpose";
    static final String F_ROUND = "round";
    static final String F_FRAMESHA256 = "frameSha256";
    static final String F_FRAMESIZE = "frameSize";
    static final String F_COUNT = "count";
    static final String F_SELECTEDPAIRIDS = "selectedPairIds";
    static final String F_EXHAUSTED = "exhausted";
    static final String F_REGISTRYSHA256 = "registrySha256";
    static final String F_CLOCKUTC = "clockUtc";
    static final String F_SEQ = "seq";
    static final String F_PREVSHA256 = "prevSha256";
    private static final String INVALID_UTC = "clockUtc must be ISO-8601 UTC";
    private static final Set<String> FIELDS = Set.of(F_WORKFLOW, F_PURPOSE, F_ROUND, F_FRAMESHA256,
            F_FRAMESIZE, F_COUNT, F_SELECTEDPAIRIDS, F_EXHAUSTED, F_REGISTRYSHA256, F_CLOCKUTC, F_SEQ, F_PREVSHA256);
    private final Path file;
    DrawLog(Path file) { this.file = file; }
    Path path() { return file; }
    record Entry(String workflow, String purpose, int round, String frameSha256, int frameSize, int count,
                 List<String> selectedPairIds, boolean exhausted, String registrySha256, String clockUtc,
                 int seq, String prevSha256) {}
    static void checkUtc(String value) {
        try {
            if (value == null || !value.endsWith("Z") || !OffsetDateTime.parse(value).getOffset().equals(ZoneOffset.UTC))
                throw new InvalidDrawLog(INVALID_UTC);
        } catch (InvalidDrawLog exception) { throw exception; }
        catch (Exception exception) { throw new InvalidDrawLog(INVALID_UTC, exception); }
    }
    List<Entry> entries() { return Files.exists(file) ? validate(file) : List.of(); }
    void append(ObjectNode candidate) {
        if (candidate == null) throw new InvalidDrawLog("Draw entry required");
        List<Entry> prior = entries();
        ObjectNode node = candidate.deepCopy();
        node.put(F_SEQ, prior.size());
        if (prior.isEmpty()) node.putNull(F_PREVSHA256);
        else {
            try {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                node.put(F_PREVSHA256, FreezeRecord.sha256(lines.getLast().getBytes(StandardCharsets.UTF_8)));
            } catch (Exception exception) { throw new InvalidDrawLog("Cannot read previous line", exception); }
        }
        try {
            String line = JSON.writeValueAsString(node);
            NoArmFields.check(line);
            Set<String> priorKeys = new HashSet<>();
            for (Entry entry : prior) priorKeys.add(entry.workflow() + SeedStreamRegistry.DELIMITER
                    + entry.purpose() + SeedStreamRegistry.DELIMITER + entry.round());
            validateEntry(node, prior.size(), prior.isEmpty() ? null : node.path(F_PREVSHA256).asText(), priorKeys);
            Files.writeString(file, line + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (InvalidDrawLog | NoArmFields.ArmFieldDetected exception) { throw exception; }
        catch (Exception exception) { throw new InvalidDrawLog("Cannot append draw log", exception); }
    }
    static List<Entry> validate(Path file) {
        try {
            String body = Files.readString(file, StandardCharsets.UTF_8);
            if (body.isEmpty() || !body.endsWith("\n") || body.contains("\r"))
                throw new InvalidDrawLog("Log must be nonempty LF-terminated JSONL");
            String[] lines = body.substring(0, body.length() - 1).split("\n", -1);
            List<Entry> result = new ArrayList<>();
            Set<String> keys = new HashSet<>();
            String previous = null;
            for (int i = 0; i < lines.length; i++) {
                JsonNode node = JSON.readTree(lines[i]);
                if (node == null || !node.isObject()) throw new InvalidDrawLog("Invalid log line " + i);
                String expected = previous == null ? null : FreezeRecord.sha256(previous.getBytes(StandardCharsets.UTF_8));
                result.add(validateEntry(node, i, expected, keys));
                previous = lines[i];
            }
            return List.copyOf(result);
        } catch (InvalidDrawLog exception) { throw exception; }
        catch (Exception exception) { throw new InvalidDrawLog("Cannot validate draw log", exception); }
    }
    private static Entry validateEntry(JsonNode n, int seq, String prev, Set<String> keys) {
        Set<String> names = new HashSet<>();
        n.fieldNames().forEachRemaining(names::add);
        if (!names.equals(FIELDS) || !n.path(F_SEQ).isInt() || n.path(F_SEQ).asInt() != seq
                || (prev == null ? !n.path(F_PREVSHA256).isNull() : !prev.equals(n.path(F_PREVSHA256).asText()))
                || !text(n, F_WORKFLOW) || !Set.of(SeedStreamRegistry.SPOT_CHECK, SeedStreamRegistry.EXPANSION).contains(n.path(F_PURPOSE).asText())
                || !n.path(F_ROUND).isInt() || n.path(F_ROUND).asInt() < 1
                || !digest(n, F_FRAMESHA256) || !digest(n, F_REGISTRYSHA256)
                || !n.path(F_FRAMESIZE).isInt() || n.path(F_FRAMESIZE).asInt() < 0
                || !n.path(F_COUNT).isInt() || n.path(F_COUNT).asInt() < 1
                || !n.path(F_EXHAUSTED).isBoolean() || !n.path(F_SELECTEDPAIRIDS).isArray())
            throw new InvalidDrawLog("Invalid draw log fields at seq " + seq);
        checkUtc(n.path(F_CLOCKUTC).asText());
        List<String> selected = new ArrayList<>();
        for (JsonNode id : n.path(F_SELECTEDPAIRIDS)) {
            if (!id.isTextual() || id.asText().isBlank())
                throw new InvalidDrawLog("Invalid selected pair id");
            selected.add(id.asText());
        }
        if (selected.size() > n.path(F_COUNT).asInt() || selected.size() > n.path(F_FRAMESIZE).asInt()
                || new HashSet<>(selected).size() != selected.size())
            throw new InvalidDrawLog("Invalid selected pair count");
        String key = n.path(F_WORKFLOW).asText() + SeedStreamRegistry.DELIMITER
                + n.path(F_PURPOSE).asText() + SeedStreamRegistry.DELIMITER + n.path(F_ROUND).asInt();
        if (!keys.add(key)) throw new InvalidDrawLog("Duplicate draw stream " + key);
        return new Entry(n.path(F_WORKFLOW).asText(), n.path(F_PURPOSE).asText(), n.path(F_ROUND).asInt(),
                n.path(F_FRAMESHA256).asText(), n.path(F_FRAMESIZE).asInt(), n.path(F_COUNT).asInt(),
                List.copyOf(selected), n.path(F_EXHAUSTED).asBoolean(), n.path(F_REGISTRYSHA256).asText(),
                n.path(F_CLOCKUTC).asText(), seq, prev);
    }
    private static boolean text(JsonNode node, String field) {
        return node.path(field).isTextual() && !node.path(field).asText().isBlank();
    }
    private static boolean digest(JsonNode node, String field) {
        return node.path(field).isTextual() && node.path(field).asText().matches("[0-9a-f]{64}");
    }
}
