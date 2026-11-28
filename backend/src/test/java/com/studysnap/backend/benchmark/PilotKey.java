package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict sealed-key reader and Revision 4 fixture-composition gate. */
final class PilotKey {
    static final class InvalidPilotKey extends RuntimeException {
        InvalidPilotKey(String message) { super(message); }
        InvalidPilotKey(String message, Throwable cause) { super(message, cause); }
    }
    record Row(String id, String packetId, PlantedType plantedType, PlantedType.Severity severity,
               ModelFamily authoringFamily, boolean rootCauseInput, String canary) {}
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final String ID = "id";
    private static final String PACKET_ID = "packetId";
    private static final String PLANTED_TYPE = "plantedType";
    private static final String SEVERITY = "severity";
    private static final String AUTHORING_FAMILY = "authoringFamily";
    private static final String ROOT_CAUSE_INPUT = "rootCauseInput";
    private static final String CANARY = "canary";
    private static final Set<String> FIELDS = Set.of(ID, PACKET_ID, PLANTED_TYPE, SEVERITY,
            AUTHORING_FAMILY, ROOT_CAUSE_INPUT, CANARY);
    private static final Map<PlantedType, Integer> REVISION_4 = Map.ofEntries(
            Map.entry(PlantedType.WRONG_KEY, 6), Map.entry(PlantedType.EXPLANATION_CONTRADICTS_KEY, 3),
            Map.entry(PlantedType.SECOND_DEFENSIBLE_ANSWER, 5),
            Map.entry(PlantedType.MATERIAL_AMBIGUITY_MULTI, 2),
            Map.entry(PlantedType.MATERIAL_AMBIGUITY_ZERO, 2),
            Map.entry(PlantedType.SOURCE_CONTRADICTION, 3), Map.entry(PlantedType.MINOR_WORDING, 3),
            Map.entry(PlantedType.NUMERIC_INCONSISTENCY, 2), Map.entry(PlantedType.WEAK_DISTRACTOR, 4),
            Map.entry(PlantedType.UNHELPFUL_EXPLANATION, 3), Map.entry(PlantedType.CLEAN, 93));
    private final List<Row> rows;
    private PilotKey(List<Row> rows) { this.rows = List.copyOf(rows); }
    List<Row> rows() { return rows; }
    String canary() { return rows.getFirst().canary(); }

    static PilotKey load(Path key) {
        try {
            byte[] bytes = Files.readAllBytes(key);
            String expected = Files.readString(key.resolveSibling(key.getFileName() + ".sha256")).trim();
            if (!FreezeRecord.sha256(bytes).equals(expected))
                throw new InvalidPilotKey("Pilot key SHA-256 mismatch");
            List<Row> rows = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            String canary = null;
            String[] lines = new String(bytes, java.nio.charset.StandardCharsets.UTF_8).split("\n", -1);
            for (int index = 0; index < lines.length; index++) {
                String line = lines[index];
                if (index == lines.length - 1 && line.isEmpty() && index > 0) continue;
                if (line.isBlank()) throw new InvalidPilotKey("Blank pilot key line");
                JsonNode node = JSON.readTree(line);
                if (!fields(node) || !text(node, ID) || !text(node, PACKET_ID)
                        || !text(node, PLANTED_TYPE) || !text(node, SEVERITY)
                        || !text(node, AUTHORING_FAMILY) || !text(node, CANARY)
                        || !node.path(ROOT_CAUSE_INPUT).isBoolean())
                    throw new InvalidPilotKey("Malformed pilot key row");
                PlantedType type = PlantedType.valueOf(node.path(PLANTED_TYPE).asText());
                PlantedType.Severity severity = PlantedType.Severity.valueOf(node.path(SEVERITY).asText());
                ModelFamily author = ModelFamily.valueOf(
                        node.path(AUTHORING_FAMILY).asText());
                String rowCanary = node.path(CANARY).asText();
                if (!rowCanary.matches("CANARY-[A-Z0-9]{16}") || (canary != null && !canary.equals(rowCanary))
                        || type.severity() != severity
                        || node.path(ROOT_CAUSE_INPUT).asBoolean() && severity != PlantedType.Severity.CRITICAL
                        || !ids.add(node.path(ID).asText()))
                    throw new InvalidPilotKey("Invalid pilot key row");
                canary = rowCanary;
                rows.add(new Row(node.path(ID).asText(), node.path(PACKET_ID).asText(), type,
                        severity, author, node.path(ROOT_CAUSE_INPUT).asBoolean(), rowCanary));
            }
            if (rows.isEmpty()) throw new InvalidPilotKey("Empty pilot key");
            return new PilotKey(rows);
        } catch (InvalidPilotKey exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidPilotKey("Cannot load pilot key " + key, exception);
        }
    }
    private static boolean text(JsonNode node, String field) {
        return node.path(field).isTextual() && !node.path(field).asText().isBlank();
    }
    private static boolean fields(JsonNode node) {
        if (node == null || !node.isObject()) return false;
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        return actual.equals(FIELDS);
    }
    Map<PlantedType, Long> countsByType() {
        Map<PlantedType, Long> counts = new EnumMap<>(PlantedType.class);
        for (PlantedType type : PlantedType.values()) counts.put(type, 0L);
        for (Row row : rows) counts.merge(row.plantedType(), 1L, Long::sum);
        return Map.copyOf(counts);
    }
    Map<PlantedType.Severity, Long> countsBySeverity() {
        Map<PlantedType.Severity, Long> counts = new EnumMap<>(PlantedType.Severity.class);
        for (PlantedType.Severity severity : PlantedType.Severity.values()) counts.put(severity, 0L);
        for (Row row : rows) counts.merge(row.severity(), 1L, Long::sum);
        return Map.copyOf(counts);
    }
    List<Row> byAuthor(ModelFamily author) {
        return rows.stream().filter(row -> row.authoringFamily() == author).toList();
    }
    Map<String, Integer> packetSizes() {
        Map<String, Integer> sizes = new HashMap<>();
        for (Row row : rows) sizes.merge(row.packetId(), 1, Integer::sum);
        return Map.copyOf(sizes);
    }
    void requireRevision4Composition() {
        if (rows.size() != 126 || !packetSizes().values().stream().sorted().toList()
                .equals(List.of(7, 7, 10, 24, 30, 48)))
            throw new InvalidPilotKey("Revision 4 packet sizes differ");
        for (var entry : REVISION_4.entrySet())
            if (countsByType().get(entry.getKey()) != entry.getValue().longValue())
                throw new InvalidPilotKey("Revision 4 planted counts differ: " + entry.getKey());
        if (rows.stream().noneMatch(Row::rootCauseInput))
            throw new InvalidPilotKey("Revision 4 requires an input root cause");
        for (ModelFamily author : ModelFamily.values())
            if (byAuthor(author).stream().filter(row -> row.severity() == PlantedType.Severity.CRITICAL).count() < 8)
                throw new InvalidPilotKey("Revision 4 requires both author families among criticals");
    }
}
