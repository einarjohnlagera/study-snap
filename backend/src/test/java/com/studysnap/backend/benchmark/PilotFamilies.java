package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Owner-declared pilot identities. This records declarations; it cannot prove authorship,
 * session isolation, tool honesty, or when scoring happened. The digest only binds the
 * bytes presented at evaluation to the bytes copied during assembly.
 */
final class PilotFamilies {
    static final class InvalidPilotFamilies extends RuntimeException {
        InvalidPilotFamilies(String message) { super(message); }
        InvalidPilotFamilies(String message, Throwable cause) { super(message, cause); }
    }
    record Packet(ModelFamily authorFamily, String authorTool, String authorModelId, String authorSessionId,
                  ModelFamily verifierFamily, String verifierTool, String verifierModelId, String verifierSessionId) {}
    record Scorer(ModelFamily family, String tool, String modelId) {}
    private static final String P = "P";
    private static final String S = "S";
    private static final String PACKETS = "packets";
    private static final String SCORERS = "scorers";
    private static final String AUTHOR_FAMILY = "authorFamily";
    private static final String AUTHOR_TOOL = "authorTool";
    private static final String AUTHOR_MODEL = "authorModelId";
    private static final String AUTHOR_SESSION = "authorSessionId";
    private static final String VERIFIER_FAMILY = "verifierFamily";
    private static final String VERIFIER_TOOL = "verifierTool";
    private static final String VERIFIER_MODEL = "verifierModelId";
    private static final String VERIFIER_SESSION = "verifierSessionId";
    private static final String FAMILY = "family";
    private static final String TOOL = "tool";
    private static final String MODEL = "modelId";
    private static final Set<String> ROOT_FIELDS = Set.of("schema", PACKETS, SCORERS);
    private static final Set<String> PACKET_FIELDS = Set.of(AUTHOR_FAMILY, AUTHOR_TOOL, AUTHOR_MODEL,
            AUTHOR_SESSION, VERIFIER_FAMILY, VERIFIER_TOOL, VERIFIER_MODEL, VERIFIER_SESSION);
    private static final Set<String> SCORER_FIELDS = Set.of(FAMILY, TOOL, MODEL);
    private static final Set<String> SIX_PACKETS = Set.of("pilot-01", "pilot-02", "pilot-03",
            "pilot-04", "pilot-05", "pilot-06");
    private final Map<String, Packet> packets;
    private final Map<String, Scorer> scorers;
    private final String sha256;
    private PilotFamilies(Map<String, Packet> packets, Map<String, Scorer> scorers, String sha256) {
        this.packets = Map.copyOf(packets);
        this.scorers = Map.copyOf(scorers);
        this.sha256 = sha256;
    }
    Map<String, Packet> packets() { return packets; }
    Map<String, Scorer> scorers() { return scorers; }
    String sha256OfBytes() { return sha256; }
    static PilotFamilies load(Path file) {
        try {
            String digest = IntegritySupport.detachedHash(file);
            byte[] bytes = Files.readAllBytes(file);
            if (!FreezeRecord.sha256(bytes).equals(digest))
                throw new InvalidPilotFamilies("Families changed while loading");
            JsonNode root = IntegritySupport.JSON.readTree(bytes);
            fields(root, ROOT_FIELDS, "root");
            if (!root.path("schema").isInt() || root.path("schema").asInt() != 1)
                throw new InvalidPilotFamilies("Invalid schema");
            JsonNode packetNodes = root.path(PACKETS);
            fields(packetNodes, SIX_PACKETS, PACKETS);
            JsonNode scorerNodes = root.path(SCORERS);
            fields(scorerNodes, Set.of(P, S), SCORERS);
            Map<String, Packet> packets = new LinkedHashMap<>();
            for (String id : SIX_PACKETS) {
                JsonNode node = packetNodes.path(id);
                fields(node, PACKET_FIELDS, id);
                packets.put(id, new Packet(family(node, AUTHOR_FAMILY), text(node, AUTHOR_TOOL),
                        text(node, AUTHOR_MODEL), text(node, AUTHOR_SESSION), family(node, VERIFIER_FAMILY),
                        text(node, VERIFIER_TOOL), text(node, VERIFIER_MODEL), text(node, VERIFIER_SESSION)));
            }
            Map<String, Scorer> scorers = new LinkedHashMap<>();
            for (String role : Set.of(P, S)) {
                JsonNode node = scorerNodes.path(role);
                fields(node, SCORER_FIELDS, role);
                scorers.put(role, new Scorer(family(node, FAMILY), text(node, TOOL), text(node, MODEL)));
            }
            for (String id : SIX_PACKETS) {
                ModelFamily expected = Set.of("pilot-01", "pilot-02", "pilot-06").contains(id)
                        ? ModelFamily.OPENAI : ModelFamily.ANTHROPIC;
                if (packets.get(id).authorFamily() != expected)
                    throw new InvalidPilotFamilies("Author family allocation differs: " + id);
            }
            for (String id : SIX_PACKETS) {
                Packet packet = packets.get(id);
                if (packet.verifierFamily() == packet.authorFamily())
                    throw new InvalidPilotFamilies("Verifier family equals author family: " + id);
            }
            if (scorers.get(P).family() != ModelFamily.OPENAI || scorers.get(S).family() != ModelFamily.ANTHROPIC)
                throw new InvalidPilotFamilies("Scorer family allocation differs");
            Map<String, String> sessionRoles = new HashMap<>();
            for (String id : SIX_PACKETS) {
                Packet packet = packets.get(id);
                session(sessionRoles, packet.authorSessionId(), "author:" + packet.authorFamily());
                session(sessionRoles, packet.verifierSessionId(), "verifier:" + packet.verifierFamily());
            }
            for (Packet packet : packets.values()) {
                tool(packet.authorTool(), true);
                tool(packet.verifierTool(), true);
            }
            for (Scorer scorer : scorers.values()) tool(scorer.tool(), false);
            return new PilotFamilies(packets, scorers, digest);
        } catch (InvalidPilotFamilies exception) { throw exception; }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new InvalidPilotFamilies("Malformed or duplicate families JSON: " + file, exception);
        }
        catch (IOException exception) { throw new InvalidPilotFamilies("Cannot load families or detached SHA-256: " + file, exception); }
        catch (Exception exception) { throw new InvalidPilotFamilies("Invalid families JSON: " + file, exception); }
    }
    private static void fields(JsonNode node, Set<String> expected, String level) {
        if (node == null || !node.isObject()) throw new InvalidPilotFamilies("Expected object: " + level);
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw new InvalidPilotFamilies("Missing or unknown fields at " + level + ": " + actual);
    }
    private static String text(JsonNode node, String name) {
        JsonNode field = node.path(name);
        if (!field.isTextual()) throw new InvalidPilotFamilies("Invalid " + name);
        String value = field.asText();
        if (value.isBlank() || value.length() > 200 || value.chars().anyMatch(Character::isISOControl))
            throw new InvalidPilotFamilies("Invalid " + name);
        return value;
    }
    private static ModelFamily family(JsonNode node, String name) {
        String value = text(node, name);
        try { return ModelFamily.valueOf(value); }
        catch (IllegalArgumentException exception) { throw new InvalidPilotFamilies("Unknown family: " + value, exception); }
    }
    private static void session(Map<String, String> roles, String id, String role) {
        String prior = roles.putIfAbsent(id, role);
        if (prior != null && !prior.equals(role)) throw new InvalidPilotFamilies("Session shared across roles: " + id);
    }
    private static void tool(String value, boolean authorOrVerifier) {
        if (value.equalsIgnoreCase("owner") || value.equalsIgnoreCase("human"))
            throw new InvalidPilotFamilies("Human tool declaration refused");
        if (authorOrVerifier && value.toLowerCase(java.util.Locale.ROOT).contains("codex"))
            throw new InvalidPilotFamilies("Codex author/verifier tool declaration refused");
    }
}
