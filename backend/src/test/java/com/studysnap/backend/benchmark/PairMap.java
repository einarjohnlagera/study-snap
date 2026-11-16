package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Packet-line pairs, indexed without an arm key. */
final class PairMap {
    static final class InvalidPairMap extends RuntimeException {
        InvalidPairMap(String message) { super(message); }
        InvalidPairMap(String message, Throwable cause) { super(message, cause); }
    }
    record Pair(String id, String workflow, Map<String, List<String>> items) {}
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final String JSONL = ".jsonl";
    private static final String OUTPUTS = "outputs";
    private static final String PLACEMENT = "placement";
    private static final String ITEMS = "items";
    private static final String ITEM_ID = "itemId";
    private static final String ID = "id";
    private static final String A = "A";
    private static final String B = "B";
    private final Map<String, Pair> pairs;
    private final Map<String, String> itemPairs;
    private final Map<String, String> placements;
    private PairMap(Map<String, Pair> pairs, Map<String, String> itemPairs, Map<String, String> placements) {
        this.pairs = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(pairs));
        this.itemPairs = Map.copyOf(itemPairs);
        this.placements = Map.copyOf(placements);
    }
    static PairMap fromPackets(Path packetDirectory) {
        try {
            List<Path> files;
            try (Stream<Path> paths = Files.list(packetDirectory)) {
                files = paths.filter(path -> path.getFileName().toString().endsWith(JSONL)).sorted().toList();
            }
            if (files.isEmpty()) throw new InvalidPairMap("No packet JSONL files");
            Map<String, Pair> pairs = new LinkedHashMap<>();
            Map<String, String> itemPairs = new HashMap<>(), placements = new HashMap<>();
            for (Path file : files) {
                String name = file.getFileName().toString();
                String workflow = name.substring(0, name.length() - JSONL.length());
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                // The class-B exporter creates empty files for workflows absent from the fixture.
                if (lines.isEmpty()) continue;
                for (int lineNumber = 0; lineNumber < lines.size(); lineNumber++) {
                    String line = lines.get(lineNumber);
                    if (line.isBlank() || ClassBBlindPacketExport.LEAK.matcher(line).find())
                        throw new InvalidPairMap("Blank or leaking packet line in " + file);
                    JsonNode packet = JSON.readTree(line);
                    JsonNode outputs = packet == null ? null : packet.path(OUTPUTS);
                    if (packet == null || !packet.isObject() || !outputs.isArray() || outputs.size() != 2)
                        throw new InvalidPairMap("Packet is not a pair in " + file);
                    boolean classA = outputs.get(0).has(ITEMS);
                    String pairId = workflow + "#" + (lineNumber + 1);
                    Map<String, List<String>> items = new LinkedHashMap<>();
                    for (JsonNode output : outputs) {
                        String placement = output.path(PLACEMENT).asText();
                        if (!output.isObject() || !Set.of(A, B).contains(placement)
                                || items.containsKey(placement) || output.has(ITEMS) != classA)
                            throw new InvalidPairMap("Invalid placement in " + pairId);
                        List<String> ids = new ArrayList<>();
                        if (output.has(ITEMS)) {
                            if (!output.path(ITEMS).isArray() || output.path(ITEMS).isEmpty())
                                throw new InvalidPairMap("Invalid class-A items in " + pairId);
                            for (JsonNode item : output.path(ITEMS)) {
                                if (!item.isObject() || !item.path(ITEM_ID).isTextual())
                                    throw new InvalidPairMap("Invalid class-A item id in " + pairId);
                                ids.add(item.path(ITEM_ID).asText());
                            }
                        } else {
                            if (!output.path(ITEM_ID).isTextual())
                                throw new InvalidPairMap("Invalid class-B item id in " + pairId);
                            ids.add(output.path(ITEM_ID).asText());
                        }
                        for (String id : ids) {
                            if (id.isBlank() || itemPairs.putIfAbsent(id, pairId) != null)
                                throw new InvalidPairMap("Missing or duplicate item id " + id);
                            placements.put(id, placement);
                        }
                        items.put(placement, List.copyOf(ids));
                    }
                    if (!items.keySet().equals(Set.of(A, B))) throw new InvalidPairMap("Missing placement in " + pairId);
                    pairs.put(pairId, new Pair(pairId, workflow, Map.copyOf(items)));
                }
            }
            if (pairs.isEmpty()) throw new InvalidPairMap("No packet pairs");
            return new PairMap(pairs, itemPairs, placements);
        } catch (InvalidPairMap exception) { throw exception; }
        catch (Exception exception) { throw new InvalidPairMap("Cannot read packet directory", exception); }
    }
    List<Pair> pairs() { return List.copyOf(pairs.values()); }
    String pairOf(String itemId) { return itemPairs.get(itemId); }
    String placementOf(String itemId) { return placements.get(itemId); }
    List<String> itemsOf(String pairId, String placement) {
        Pair pair = pairs.get(pairId);
        return pair == null ? List.of() : pair.items().getOrDefault(placement, List.of());
    }
    String workflowOf(String pairId) {
        Pair pair = pairs.get(pairId);
        return pair == null ? null : pair.workflow();
    }
    Set<String> blindItemIds() { return Set.copyOf(itemPairs.keySet()); }
    void idOnlyKey(Path out) {
        try {
            List<String> lines = new ArrayList<>();
            for (Pair pair : pairs.values()) for (String placement : List.of(A, B))
                for (String id : pair.items().get(placement)) {
                    String line = JSON.writeValueAsString(Map.of(ID, id));
                    NoArmFields.check(line);
                    lines.add(line);
                }
            Files.writeString(out, String.join("\n", lines) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (Exception exception) { throw new InvalidPairMap("Cannot write id-only key", exception); }
    }
}
