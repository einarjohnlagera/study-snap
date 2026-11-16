package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Structural and content guard shared by packet consumers and draw writers. */
final class NoArmFields {
    static final class ArmFieldDetected extends RuntimeException {
        private final List<String> paths;
        ArmFieldDetected(List<String> paths) {
            super("Arm fields: " + paths);
            this.paths = List.copyOf(paths);
        }
        List<String> paths() { return paths; }
    }
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> DENIED = Set.of("arm", "armkey", "armlabel", "baseline", "candidate",
            "model", "modelname", "modelid", "route", "tier", "planted", "plantedtype",
            "authoringsource", "canary", "severity");
    private NoArmFields() {}

    static void check(String json) {
        try {
            JsonNode root = JSON.readTree(json);
            if (root == null) throw new ArmFieldDetected(List.of("$ invalid JSON"));
            List<String> hits = new ArrayList<>();
            walk(root, "$", hits);
            if (!hits.isEmpty()) throw new ArmFieldDetected(hits);
        } catch (ArmFieldDetected exception) { throw exception; }
        catch (Exception exception) { throw new ArmFieldDetected(List.of("$ invalid JSON: " + exception.getMessage())); }
    }
    static void check(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            if (lines.isEmpty()) throw new ArmFieldDetected(List.of("$ empty file"));
            for (String line : lines) check(line);
        } catch (ArmFieldDetected exception) { throw exception; }
        catch (Exception exception) { throw new ArmFieldDetected(List.of("$ unreadable file: " + file)); }
    }
    private static void walk(JsonNode node, String path, List<String> hits) {
        if (node.isObject()) node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            String child = path + "." + key;
            String lower = key.toLowerCase(Locale.ROOT);
            if (DENIED.contains(lower) || lower.matches(".*(?:token|cost|usage).*")
                    || ClassBBlindPacketExport.LEAK.matcher(key).find()) hits.add(child);
            walk(entry.getValue(), child, hits);
        });
        else if (node.isArray()) for (int i = 0; i < node.size(); i++) walk(node.get(i), path + "[" + i + "]", hits);
        else if (node.isTextual() && ClassBBlindPacketExport.LEAK.matcher(node.asText()).find()) hits.add(path);
    }
}
