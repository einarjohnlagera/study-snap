package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

/** Strict JSON and detached-digest primitives shared by the blind lock records. */
final class IntegritySupport {
    static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private IntegritySupport() {}

    static Instant utc(String value) {
        try {
            if (value == null || !value.endsWith("Z")) throw new IllegalArgumentException();
            OffsetDateTime parsed = OffsetDateTime.parse(value);
            if (!parsed.getOffset().equals(ZoneOffset.UTC)) throw new IllegalArgumentException();
            return parsed.toInstant();
        } catch (Exception exception) { throw new IllegalArgumentException("ISO-8601 UTC timestamp required", exception); }
    }
    static Path detached(Path file) { return file.resolveSibling(file.getFileName() + ".sha256"); }
    static String detachedHash(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String declared = Files.readString(detached(file), StandardCharsets.UTF_8).trim();
        if (!digest(declared) || !FreezeRecord.sha256(bytes).equals(declared))
            throw new IOException("Detached SHA-256 mismatch: " + file);
        return declared;
    }
    static void create(Path file, byte[] bytes) throws IOException {
        if (Files.exists(file) || Files.exists(detached(file))) throw new IOException("Record already exists: " + file);
        Files.write(file, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        Files.writeString(detached(file), FreezeRecord.sha256(bytes) + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
    static void fields(JsonNode node, Set<String> expected) throws IOException {
        if (node == null || !node.isObject()) throw new IOException("Expected JSON object");
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw new IOException("Unexpected JSON fields");
    }
    static String string(JsonNode node, String name) throws IOException {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual() || value.asText().isBlank()) throw new IOException("Invalid " + name);
        return value.asText();
    }
    static String hash(JsonNode node, String name) throws IOException {
        String value = string(node, name);
        if (!digest(value)) throw new IOException("Invalid SHA-256 " + name);
        return value;
    }
    static boolean digest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    static long nonBlankLines(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8).lines().filter(line -> !line.isBlank()).count();
    }
}
