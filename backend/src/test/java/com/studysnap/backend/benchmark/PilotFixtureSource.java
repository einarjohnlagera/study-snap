package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One UTF-8 JSON file per packet: packetId, a nonempty sourceNoteTexts array of
 * nonblank strings, and a nonempty questions array. Each question entry has
 * exactly question (the delivered class-A question JSON) and planted, whose
 * fields are plantedType, authoringSource and rootCauseInput. Metadata stays
 * only in the sealed key. Duplicate JSON fields and unknown wrapper fields fail.
 */
final class PilotFixtureSource {
    static final class InvalidPilotFixture extends RuntimeException {
        InvalidPilotFixture(String message) { super(message); }
        InvalidPilotFixture(String message, Throwable cause) { super(message, cause); }
    }
    enum AuthoringSource { CODEX, CLAUDE_OR_OWNER }
    record Item(JsonNode question, PlantedType plantedType, AuthoringSource authoringSource,
                boolean rootCauseInput) {}
    record Fixture(String packetId, List<String> sourceNoteTexts, List<Item> questions) {}
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final String PACKET_ID = "packetId";
    private static final String SOURCE_TEXTS = "sourceNoteTexts";
    private static final String QUESTIONS = "questions";
    private static final String QUESTION = "question";
    private static final String PLANTED_FIELD = "planted";
    private static final String PLANTED_TYPE = "plantedType";
    private static final String AUTHORING_SOURCE = "authoringSource";
    private static final String ROOT_CAUSE_INPUT = "rootCauseInput";
    private static final Set<String> TOP = Set.of(PACKET_ID, SOURCE_TEXTS, QUESTIONS);
    private static final Set<String> ITEM = Set.of(QUESTION, PLANTED_FIELD);
    private static final Set<String> PLANTED = Set.of(PLANTED_TYPE, AUTHORING_SOURCE, ROOT_CAUSE_INPUT);
    private PilotFixtureSource() {}

    static Fixture load(Path file) {
        try {
            JsonNode root = JSON.readTree(Files.readAllBytes(file));
            if (!fields(root, TOP) || !root.path(PACKET_ID).isTextual()
                    || !root.path(PACKET_ID).asText().matches("[A-Za-z0-9_-]+")
                    || !root.path(SOURCE_TEXTS).isArray() || root.path(SOURCE_TEXTS).isEmpty()
                    || !root.path(QUESTIONS).isArray() || root.path(QUESTIONS).isEmpty())
                throw new InvalidPilotFixture("Malformed pilot fixture in " + file);
            List<String> sources = new ArrayList<>();
            for (JsonNode source : root.path(SOURCE_TEXTS)) {
                if (!source.isTextual() || source.asText().isBlank())
                    throw new InvalidPilotFixture("Invalid source note text in " + file);
                sources.add(source.asText());
            }
            List<Item> items = new ArrayList<>();
            for (JsonNode item : root.path(QUESTIONS)) {
                JsonNode planted = item.path(PLANTED_FIELD);
                if (!fields(item, ITEM) || !fields(planted, PLANTED)
                        || !planted.path(PLANTED_TYPE).isTextual()
                        || !planted.path(AUTHORING_SOURCE).isTextual()
                        || !planted.path(ROOT_CAUSE_INPUT).isBoolean())
                    throw new InvalidPilotFixture("Malformed planted item in " + file);
                PlantedType type = PlantedType.valueOf(planted.path(PLANTED_TYPE).asText());
                AuthoringSource author = AuthoringSource.valueOf(planted.path(AUTHORING_SOURCE).asText());
                boolean input = planted.path(ROOT_CAUSE_INPUT).asBoolean();
                if (input && type.severity() != PlantedType.Severity.CRITICAL)
                    throw new InvalidPilotFixture("rootCauseInput requires CRITICAL in " + file);
                JsonNode question = item.path(QUESTION);
                ClassAScoreSchemaV2.validatePacket(new ClassAScoreSchemaV2.PacketQuestion(question, sources));
                if (!question.path("explanation").isTextual())
                    throw new InvalidPilotFixture("Delivered explanation missing in " + file);
                items.add(new Item(question, type, author, input));
            }
            return new Fixture(root.path(PACKET_ID).asText(), List.copyOf(sources), List.copyOf(items));
        } catch (InvalidPilotFixture exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidPilotFixture("Cannot load pilot fixture " + file, exception);
        }
    }

    private static boolean fields(JsonNode node, Set<String> expected) {
        if (!node.isObject()) return false;
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        return actual.equals(expected);
    }
}
