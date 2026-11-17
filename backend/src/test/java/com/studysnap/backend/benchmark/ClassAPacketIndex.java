package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Index of arm-blind packet items and their source text, with no arm-key access. */
final class ClassAPacketIndex {
    static final class InvalidPacket extends RuntimeException {
        InvalidPacket(String message) { super(message); }
        InvalidPacket(String message, Throwable cause) { super(message, cause); }
    }
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final Map<String, ClassAScoreSchemaV2.PacketQuestion> items;
    private ClassAPacketIndex(Map<String, ClassAScoreSchemaV2.PacketQuestion> items) { this.items = Map.copyOf(items); }
    ClassAScoreSchemaV2.PacketQuestion get(String id) { return items.get(id); }
    int size() { return items.size(); }
    java.util.Set<String> itemIds() { return items.keySet(); }

    static ClassAPacketIndex load(Path packetDirectory) {
        return load(packetDirectory, 2);
    }

    static ClassAPacketIndex loadPilot(Path packetDirectory) {
        return load(packetDirectory, 1);
    }

    private static ClassAPacketIndex load(Path packetDirectory, int expectedOutputs) {
        try {
            Map<String, ClassAScoreSchemaV2.PacketQuestion> items = new LinkedHashMap<>();
            List<Path> files;
            try (Stream<Path> paths = Files.list(packetDirectory)) {
                files = paths.filter(path -> path.getFileName().toString().endsWith(".jsonl")).sorted().toList();
            }
            if (files.isEmpty()) throw new InvalidPacket("No packet JSONL files in " + packetDirectory);
            for (Path file : files) for (String line : Files.readAllLines(file)) {
                if (line.isBlank()) throw new InvalidPacket("Blank packet line in " + file);
                JsonNode packet = JSON.readTree(line);
                if (packet == null || !packet.isObject() || !packet.path("outputs").isArray()
                        || packet.path("outputs").size() != expectedOutputs || !packet.path("sourceNoteTexts").isArray()
                        || packet.path("sourceNoteTexts").isEmpty()
                        || !packet.path("requestedQuestionCount").isInt()
                        || packet.path("requestedQuestionCount").asInt() < 1)
                    throw new InvalidPacket("Malformed packet in " + file);
                List<String> sources = new ArrayList<>();
                for (JsonNode source : packet.path("sourceNoteTexts")) {
                    if (!source.isTextual()) throw new InvalidPacket("Invalid source note text in " + file);
                    sources.add(source.asText());
                }
                java.util.Set<String> placements = new java.util.HashSet<>();
                for (JsonNode output : packet.path("outputs")) {
                    if (!output.isObject() || !output.path("items").isArray()
                            || output.path("items").size() < packet.path("requestedQuestionCount").asInt()
                            || !(expectedOutputs == 1 ? "A".equals(output.path("placement").asText())
                                    : java.util.Set.of("A", "B").contains(output.path("placement").asText()))
                            || !placements.add(output.path("placement").asText()))
                        throw new InvalidPacket("Malformed packet output in " + file);
                    for (JsonNode item : output.path("items")) {
                        JsonNode id = item.path("itemId");
                        if (!item.isObject() || !id.isTextual() || id.asText().isBlank())
                            throw new InvalidPacket("Malformed packet item in " + file);
                        var question = new ClassAScoreSchemaV2.PacketQuestion(item.path("question"), List.copyOf(sources));
                        ClassAScoreSchemaV2.validatePacket(question);
                        if (items.putIfAbsent(id.asText(), question) != null)
                            throw new InvalidPacket("Duplicate packet item id " + id.asText());
                    }
                }
            }
            return new ClassAPacketIndex(items);
        } catch (InvalidPacket exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidPacket("Cannot index class-A packets: " + exception.getMessage(), exception);
        }
    }
}
