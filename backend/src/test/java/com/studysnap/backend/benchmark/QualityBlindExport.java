package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Offline, complete, arm-keyed export. Source texts and provenance are separate from scored items. */
public final class QualityBlindExport {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private record Entry(Map<String,Object> item, Map<String,Object> key) {}

    @Test void exportWhenRequested() throws Exception {
        assumeTrue(Boolean.getBoolean("llm.benchmark.qualityBlind"), "opt-in offline export skipped");
        String dirs = requiredProperty("llm.benchmark.blindDirs");
        List<Path> paths = new ArrayList<>();
        for (String dir : dirs.split(",")) paths.add(Path.of(dir.trim()));
        export(paths, Path.of(requiredProperty("llm.benchmark.stock")),
                Path.of(requiredProperty("llm.benchmark.blindOutDir")),
                Long.getLong("llm.benchmark.blindSeed", 176L),
                Integer.getInteger("llm.benchmark.blindChunk", 100));
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    static void export(List<Path> dirs, Path stock, Path out, long seed, int chunkSize) throws Exception {
        if (dirs.isEmpty() || chunkSize < 1) throw new IllegalArgumentException("Arm directories and positive blindChunk required");
        Map<String,String> refs = new LinkedHashMap<>();
        Map<String,String> noteTexts = new LinkedHashMap<>();
        Map<String,String> noteLevels = new LinkedHashMap<>();
        Map<String,Integer> counts = new LinkedHashMap<>();
        Map<String,Integer> stockCounts = new HashMap<>();
        Map<String,String> armIdentity = new HashMap<>();
        java.util.Set<String> starts = new java.util.HashSet<>();
        List<Entry> entries = new ArrayList<>();
        for (Path dir : dirs) for (String line : Files.readAllLines(dir.resolve("results.jsonl"))) {
            JsonNode row = parse(line);
            String arm = required(row,"arm"), model = required(row,"model"), hash = required(row,"promptSha256");
            if (!hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid promptSha256 for " + arm);
            String identity = model + "/" + hash;
            if (armIdentity.putIfAbsent(arm, identity) != null && !armIdentity.get(arm).equals(identity))
                throw new IllegalArgumentException("Arm changes model or prompt hash: " + arm);
            String pack = required(row,"packId");
            if (!starts.add(arm + "/" + pack)) throw new IllegalArgumentException("Duplicate quality start for " + arm + "/" + pack);
            String noteRef = refs.computeIfAbsent(pack, ignored -> "note-%02d".formatted(refs.size()+1));
            String noteLevel = required(row,"noteLevel");
            if (noteLevels.putIfAbsent(noteRef,noteLevel) != null && !noteLevels.get(noteRef).equals(noteLevel))
                throw new IllegalArgumentException("Note level changed for " + pack);
            String noteText = required(row,"sourceNoteText");
            if (noteTexts.putIfAbsent(noteRef, noteText) != null && !noteTexts.get(noteRef).equals(noteText))
                throw new IllegalArgumentException("Source note changed for " + pack);
            if (!row.path("success").isBoolean()) throw new IllegalArgumentException("Malformed success in " + dir);
            JsonNode questions = row.path("questions");
            if (!questions.isArray()) throw new IllegalArgumentException("Malformed questions in " + dir);
            if (!row.path("success").asBoolean()) continue;
            for (int position = 0; position < questions.size(); position++) {
                JsonNode question = questions.get(position);
                entries.add(new Entry(item(noteRef, question), key(arm, model, hash, noteRef, pack, position + 1, format(question), false, noteLevel, null)));
                counts.merge(arm, 1, Integer::sum);
            }
        }
        if (refs.size() != 20) throw new IllegalArgumentException("Expected 20 distinct packs; found " + refs.size());
        for (String line : Files.readAllLines(stock)) {
            JsonNode row = parse(line);
            String pack = required(row,"packId"), noteRef = refs.get(pack);
            if (noteRef == null) throw new IllegalArgumentException("Stock pack absent from generated input: " + pack);
            stockCounts.merge(pack, 1, Integer::sum);
            JsonNode question = row.path("question");
            if (!question.isObject()) throw new IllegalArgumentException("Malformed stock question");
            String generatedAt = required(row,"generatedAt");
            long position = entries.stream().filter(e -> Boolean.TRUE.equals(e.key().get("isStock")) && pack.equals(e.key().get("packId"))).count() + 1;
            entries.add(new Entry(item(noteRef, question), key("S", "stock", "stock", noteRef, pack, (int)position, format(question), true, noteLevels.get(noteRef), generatedAt)));
            counts.merge("S", 1, Integer::sum);
        }
        for (String pack : refs.keySet()) if (stockCounts.getOrDefault(pack,0) != 5)
            throw new IllegalArgumentException("Expected five stock items for " + pack);
        Collections.shuffle(entries, new Random(seed));
        Files.createDirectories(out);
        List<String> itemLines = new ArrayList<>(), keyLines = new ArrayList<>(), chunks = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            String id = "blind-%04d".formatted(i+1);
            Map<String,Object> item = new LinkedHashMap<>(); item.put("id", id); item.putAll(entries.get(i).item());
            Map<String,Object> key = new LinkedHashMap<>(); key.put("id", id); key.putAll(entries.get(i).key());
            itemLines.add(MAPPER.writeValueAsString(item)); keyLines.add(MAPPER.writeValueAsString(key));
        }
        for (int offset = 0; offset < itemLines.size(); offset += chunkSize) {
            String name = "blind-items-%03d.jsonl".formatted(offset/chunkSize + 1);
            chunks.add(name);
            Path target = out.resolve(name);
            if (!Files.exists(target)) writeLines(target, itemLines.subList(offset, Math.min(offset+chunkSize,itemLines.size())));
        }
        writeLines(out.resolve("blind-key.jsonl"), keyLines);
        List<String> notes = new ArrayList<>();
        for (var entry : noteTexts.entrySet()) notes.add(MAPPER.writeValueAsString(Map.of("noteRef",entry.getKey(),"sourceNoteText",entry.getValue(),"noteLevel",noteLevels.get(entry.getKey()))));
        writeLines(out.resolve("notes.jsonl"), notes);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(out.resolve("blind-manifest.json").toFile(),
                Map.of("countsPerArm", counts, "chunks", chunks, "seed", seed, "chunkSize", chunkSize, "totalItems", entries.size()));
    }

    static String format(JsonNode q) {
        String value = q.path("questionFormat").asText("");
        return value.isBlank() ? "MCQ" : value;
    }
    private static Map<String,Object> item(String ref, JsonNode q) {
        String question = required(q,"question");
        JsonNode choices = q.path("choices");
        if (!choices.isArray()) throw new IllegalArgumentException("Question choices must be an array");
        Map<String,Object> item = new LinkedHashMap<>();
        item.put("noteRef", ref); item.put("question", question); item.put("choices", choices);
        item.put("questionFormat", format(q)); item.put("keyedAnswer", answer(q));
        item.put("explanation", required(q,"explanation"));
        return item;
    }
    private static Object answer(JsonNode q) {
        if ("MULTI_SELECT".equals(format(q))) {
            JsonNode indices = q.path("correctIndices");
            if (!indices.isArray()) throw new IllegalArgumentException("MULTI_SELECT lacks correctIndices");
            return indices;
        }
        JsonNode index = q.path("correctIndex");
        if (index.isIntegralNumber()) {
            JsonNode choices = q.path("choices");
            if (index.asInt() < 0 || index.asInt() >= choices.size()) throw new IllegalArgumentException("Invalid correctIndex");
            return choices.get(index.asInt()).asText();
        }
        if (q.path("acceptableAnswers").isArray()) return q.path("acceptableAnswers");
        if (q.path("acceptableAnswerGroups").isArray()) return q.path("acceptableAnswerGroups");
        throw new IllegalArgumentException("Question has no keyed answer");
    }
    private static Map<String,Object> key(String arm, String model, String hash, String ref, String pack,
                                          int position, String format, boolean stock, String noteLevel, String generatedAt) {
        Map<String,Object> key = new LinkedHashMap<>();
        key.put("arm", arm); key.put("model", model); key.put("promptSha256", hash);
        key.put("noteRef", ref); key.put("packId", pack); key.put("positionInStart", position);
        key.put("questionFormat", format); key.put("isStock", stock);
        key.put("noteLevel", noteLevel);
        if (stock) key.put("generatedAt", generatedAt);
        return key;
    }
    private static String required(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException("Missing " + field);
        return value.asText();
    }
    private static JsonNode parse(String line) throws Exception {
        JsonNode row = MAPPER.readTree(line);
        if (!row.isObject()) throw new IllegalArgumentException("Malformed JSONL row");
        return row;
    }
    private static void writeLines(Path target, List<String> lines) throws Exception {
        Files.writeString(target, String.join("\n", lines) + (lines.isEmpty() ? "" : "\n"));
    }
}
