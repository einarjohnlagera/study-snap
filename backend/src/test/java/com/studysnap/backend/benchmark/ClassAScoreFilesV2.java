package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Role-separated v2 rows; scorer failures are retained for mandatory human review. */
final class ClassAScoreFilesV2 {
    static final class InvalidScoreFiles extends RuntimeException {
        InvalidScoreFiles(String message) { super(message); }
        InvalidScoreFiles(String message, Throwable cause) { super(message, cause); }
    }
    enum Status { OK, MISSING, INVALID }
    record Slot(Status status, JsonNode row, ClassAScoreSchemaV2.Code code, String message) {
        static Slot missing() { return new Slot(Status.MISSING, null, null, "Scorer row missing"); }
        static Slot ok(JsonNode row) { return new Slot(Status.OK, row, null, null); }
        static Slot invalid(ClassAScoreSchemaV2.InvalidRow error) {
            return new Slot(Status.INVALID, null, error.code(), error.getMessage());
        }
    }
    record HumanRow(JsonNode row) {
        ClassAScoreSchemaV2.Verdict verdict() { return ClassAScoreSchemaV2.Verdict.valueOf(row.path("humanVerdict").asText()); }
    }
    record ItemScores(String id, Slot p, Slot s, HumanRow h) {}
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private ClassAScoreFilesV2() {}

    static Map<String, ItemScores> load(Path key, ClassAPacketIndex packets, Path pFile, Path sFile,
                                        Path hFile, EvidenceMapping mapping) {
        if (packets == null || mapping == null) throw new InvalidScoreFiles("Packet index and mapping required");
        Map<String, JsonNode> keys = readKey(key);
        Map<String, String> rolesByReviewer = new HashMap<>();
        Map<String, Slot> p = readScorers(pFile, "P", keys, packets, mapping, rolesByReviewer);
        Map<String, Slot> s = readScorers(sFile, "S", keys, packets, mapping, rolesByReviewer);
        Map<String, HumanRow> h = readHumans(hFile, keys, packets, mapping, rolesByReviewer);
        Map<String, ItemScores> result = new LinkedHashMap<>();
        for (String id : keys.keySet()) {
            ItemScores item = new ItemScores(id, p.getOrDefault(id, Slot.missing()),
                    s.getOrDefault(id, Slot.missing()), h.get(id));
            if (item.h() != null) crossCheck(item, mapping);
            result.put(id, item);
        }
        return Map.copyOf(result);
    }

    static boolean needsHumanReview(ItemScores item, EvidenceMapping mapping) {
        if (item.p().status() != Status.OK || item.s().status() != Status.OK) return true;
        JsonNode p = item.p().row(), s = item.s().row();
        return ClassAScoreSchemaV2.verdict(p, mapping) != ClassAScoreSchemaV2.verdict(s, mapping)
                || p.path("borderline").asBoolean() || s.path("borderline").asBoolean()
                || ClassAScoreSchemaV2.verdict(p, mapping) == ClassAScoreSchemaV2.Verdict.CRITICAL
                || ClassAScoreSchemaV2.verdict(s, mapping) == ClassAScoreSchemaV2.Verdict.CRITICAL;
    }

    private static Map<String, JsonNode> readKey(Path key) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode row : lines(key)) {
            String id = id(row);
            if (result.putIfAbsent(id, row) != null) throw new InvalidScoreFiles("Duplicate key id " + id);
        }
        return result;
    }
    private static Map<String, Slot> readScorers(Path file, String role, Map<String, JsonNode> keys,
                                                   ClassAPacketIndex packets, EvidenceMapping mapping,
                                                   Map<String, String> rolesByReviewer) {
        Map<String, Slot> result = new HashMap<>();
        for (JsonNode row : lines(file)) {
            String id = identity(row, role, keys, rolesByReviewer);
            if (result.containsKey(id)) throw new InvalidScoreFiles("Duplicate " + role + " score for " + id);
            var packet = packets.get(id);
            if (packet == null) throw new InvalidScoreFiles("No packet item for key id " + id);
            try {
                ClassAScoreSchemaV2.validate(row, packet, mapping);
                result.put(id, Slot.ok(row));
            } catch (ClassAScoreSchemaV2.InvalidRow exception) {
                result.put(id, Slot.invalid(exception));
            }
        }
        return result;
    }
    private static Map<String, HumanRow> readHumans(Path file, Map<String, JsonNode> keys,
                                                     ClassAPacketIndex packets, EvidenceMapping mapping,
                                                     Map<String, String> rolesByReviewer) {
        Map<String, HumanRow> result = new HashMap<>();
        for (JsonNode row : lines(file)) {
            String id = identity(row, "H", keys, rolesByReviewer);
            if (result.containsKey(id)) throw new InvalidScoreFiles("Duplicate H score for " + id);
            var packet = packets.get(id);
            if (packet == null) throw new InvalidScoreFiles("No packet item for key id " + id);
            try {
                ClassAScoreSchemaV2.validate(row, packet, mapping);
            } catch (ClassAScoreSchemaV2.InvalidRow exception) {
                throw new InvalidScoreFiles("Invalid H row " + id + ": " + exception.getMessage(), exception);
            }
            result.put(id, new HumanRow(row));
        }
        return result;
    }
    private static String identity(JsonNode row, String role, Map<String, JsonNode> keys,
                                   Map<String, String> rolesByReviewer) {
        String id = id(row);
        if (!keys.containsKey(id)) throw new InvalidScoreFiles("Unknown score id " + id);
        if (!role.equals(row.path("scorerRole").asText()))
            throw new InvalidScoreFiles("scorerRole differs from " + role + " file for " + id);
        JsonNode reviewer = row.path("reviewerId");
        if (reviewer.isTextual() && !reviewer.asText().isBlank()) {
            String previous = rolesByReviewer.putIfAbsent(reviewer.asText(), role);
            if (previous != null && !previous.equals(role))
                throw new InvalidScoreFiles("reviewerId appears under two roles: " + reviewer.asText());
        }
        return id;
    }
    private static String id(JsonNode row) {
        JsonNode value = row.path("id");
        if (!value.isTextual() || value.asText().isBlank()) throw new InvalidScoreFiles("Missing score id");
        return value.asText();
    }
    private static void crossCheck(ItemScores item, EvidenceMapping mapping) {
        boolean criticalScorer = (item.p().status() == Status.OK
                && ClassAScoreSchemaV2.verdict(item.p().row(), mapping) == ClassAScoreSchemaV2.Verdict.CRITICAL)
                || (item.s().status() == Status.OK
                && ClassAScoreSchemaV2.verdict(item.s().row(), mapping) == ClassAScoreSchemaV2.Verdict.CRITICAL);
        String reason = item.h().row().path("humanClearReason").isNull()
                ? null : item.h().row().path("humanClearReason").asText();
        String expected = criticalScorer && item.h().verdict() == ClassAScoreSchemaV2.Verdict.CLEAN
                ? "REVIEWED_NOT_DEFECT" : criticalScorer && item.h().verdict() == ClassAScoreSchemaV2.Verdict.QUALITY_FAIL
                ? "DOWNGRADED_NONCRITICAL" : null;
        if (!java.util.Objects.equals(expected, reason))
            throw new InvalidScoreFiles("humanClearReason disagrees with critical scorer verdict for " + item.id());
    }
    private static java.util.List<JsonNode> lines(Path file) {
        try {
            java.util.List<JsonNode> rows = new java.util.ArrayList<>();
            for (String line : Files.readAllLines(file)) {
                if (line.isBlank()) throw new InvalidScoreFiles("Blank JSONL line in " + file);
                JsonNode row = JSON.readTree(line);
                if (row == null || !row.isObject()) throw new InvalidScoreFiles("Non-object JSONL line in " + file);
                rows.add(row);
            }
            return rows;
        } catch (InvalidScoreFiles exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidScoreFiles("Cannot parse score file " + file + ": " + exception.getMessage(), exception);
        }
    }
}
