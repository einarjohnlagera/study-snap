package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
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

/** Offline blind adjudication export from one or more completed model directories. */
public final class BlindExport {
    @Test void exportWhenRequested() throws Exception {
        assumeTrue(Boolean.getBoolean("llm.benchmark.blind"), "opt-in blind export skipped");
        String dirs = System.getProperty("llm.benchmark.blindDirs");
        if (dirs == null || dirs.isBlank()) throw new IllegalArgumentException("llm.benchmark.blindDirs is required");
        List<Path> paths = new ArrayList<>();
        for (String dir : dirs.split(",")) paths.add(Path.of(dir.trim()));
        Path output = Path.of(System.getProperty("llm.benchmark.blindOutDir", paths.getFirst().toString()));
        export(paths, output, Integer.getInteger("llm.benchmark.blindCount", 20),
                Long.getLong("llm.benchmark.blindSeed", 174L));
    }

    static void export(List<Path> dirs, Path out, int count, long seed) throws Exception {
        if (count < 1) throw new IllegalArgumentException("blindCount must be positive");
        ObjectMapper mapper = new ObjectMapper();
        Map<String,List<Map<String,Object>>> byModel = new HashMap<>();
        for (Path dir : dirs) try (BufferedReader reader = Files.newBufferedReader(dir.resolve("results.jsonl"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JsonNode result = mapper.readTree(line);
                if (!result.path("success").asBoolean(false)) continue;
                String model = result.path("model").asText();
                for (JsonNode question : result.path("questions")) {
                    Map<String,Object> item = new LinkedHashMap<>();
                    item.put("sourceNoteText", result.path("sourceNoteText").isNull() ? null : result.path("sourceNoteText").asText());
                    item.put("question", question.path("question").asText());
                    item.put("choices", question.path("choices"));
                    Object answer = question.path("correctIndex").isInt()
                            ? question.path("choices").path(question.path("correctIndex").asInt()).asText()
                            : question.path("correctIndices").isArray() && !question.path("correctIndices").isEmpty()
                            ? question.path("correctIndices")
                            : question.path("acceptableAnswers").isArray() && !question.path("acceptableAnswers").isEmpty()
                            ? question.path("acceptableAnswers") : question.path("acceptableAnswerGroups");
                    item.put("keyedAnswer", answer);
                    item.put("explanation", question.path("explanation").asText());
                    byModel.computeIfAbsent(model, ignored -> new ArrayList<>()).add(item);
                }
            }
        }
        List<Map.Entry<String,Map<String,Object>>> selected = new ArrayList<>();
        for (var entry : byModel.entrySet()) {
            List<Map<String,Object>> questions = entry.getValue();
            Collections.shuffle(questions, new Random(seed));
            for (int i = 0; i < Math.min(count, questions.size()); i++)
                selected.add(Map.entry(entry.getKey(), questions.get(i)));
        }
        Collections.shuffle(selected, new Random(seed + 1));
        Files.createDirectories(out);
        try (BufferedWriter items = Files.newBufferedWriter(out.resolve("blind-items.jsonl"));
             BufferedWriter key = Files.newBufferedWriter(out.resolve("blind-key.jsonl"))) {
            for (int i = 0; i < selected.size(); i++) {
                String id = "blind-" + (i + 1);
                Map<String,Object> item = new LinkedHashMap<>(selected.get(i).getValue());
                item.put("id", id);
                items.write(mapper.writeValueAsString(item)); items.newLine(); items.flush();
                key.write(mapper.writeValueAsString(Map.of("id",id,"model",selected.get(i).getKey())));
                key.newLine(); key.flush();
            }
        }
    }
}
