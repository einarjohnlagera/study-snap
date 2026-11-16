package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Hashed, predeclared review draws. Export placement and rationale sampling are separate. */
final class SeedStreamRegistry {
    static final class InvalidSeedRegistry extends RuntimeException {
        InvalidSeedRegistry(String message) { super(message); }
        InvalidSeedRegistry(String message, Throwable cause) { super(message, cause); }
    }
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final String SEED = "seed";
    private static final String STREAMS = "streams";
    private static final String WORKFLOW = "workflow";
    private static final String PURPOSE = "purpose";
    private static final String ROUNDS = "rounds";
    static final String SPOT_CHECK = "SPOT_CHECK";
    static final String EXPANSION = "EXPANSION";
    static final String DELIMITER = "|";
    private static final Set<String> ROOT_FIELDS = Set.of(SEED, STREAMS);
    private static final Set<String> STREAM_FIELDS = Set.of(WORKFLOW, PURPOSE, ROUNDS);
    private final String seed;
    private final String sha256;
    private final Map<String, Integer> rounds;

    private SeedStreamRegistry(String seed, String sha256, Map<String, Integer> rounds) {
        this.seed = seed;
        this.sha256 = sha256;
        this.rounds = Map.copyOf(rounds);
    }
    static SeedStreamRegistry load(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            String hash = FreezeRecord.sha256(bytes);
            String detached = Files.readString(file.resolveSibling(file.getFileName() + ".sha256")).trim();
            if (!hash.equals(detached)) throw new InvalidSeedRegistry("Detached SHA-256 mismatch");
            JsonNode root = JSON.readTree(bytes);
            if (root == null || !root.isObject() || !fields(root).equals(ROOT_FIELDS)
                    || !root.path(SEED).isTextual()
                    || root.path(SEED).asText().codePointCount(0, root.path(SEED).asText().length()) < 16
                    || !root.path(STREAMS).isArray())
                throw new InvalidSeedRegistry("Invalid seed registry fields");
            Map<String, Integer> declared = new HashMap<>();
            for (JsonNode stream : root.path(STREAMS)) {
                if (!stream.isObject() || !fields(stream).equals(STREAM_FIELDS)
                        || !stream.path(WORKFLOW).isTextual()
                        || !stream.path(WORKFLOW).asText().matches("[A-Za-z0-9_-]+")
                        || !stream.path(PURPOSE).isTextual()
                        || !Set.of(SPOT_CHECK, EXPANSION).contains(stream.path(PURPOSE).asText())
                        || !stream.path(ROUNDS).isInt() || stream.path(ROUNDS).asInt() < 1)
                    throw new InvalidSeedRegistry("Invalid stream declaration");
                String key = stream.path(WORKFLOW).asText() + DELIMITER + stream.path(PURPOSE).asText();
                if (declared.putIfAbsent(key, stream.path(ROUNDS).asInt()) != null)
                    throw new InvalidSeedRegistry("Duplicate stream declaration: " + key);
            }
            return new SeedStreamRegistry(root.path(SEED).asText(), hash, declared);
        } catch (InvalidSeedRegistry exception) { throw exception; }
        catch (Exception exception) { throw new InvalidSeedRegistry("Cannot load seed registry", exception); }
    }
    private static Set<String> fields(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
    boolean isDeclared(String workflow, String purpose, int round) {
        Integer limit = rounds.get(workflow + DELIMITER + purpose);
        return limit != null && round >= 1 && round <= limit;
    }
    String sha256() { return sha256; }
    String seed() { return seed; }
}
