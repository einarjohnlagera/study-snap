package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Descriptive diagnostics only. It never excludes a generated or stock item. */
public final class QualityDescriptors {
    static final int MIN_PAIRED_NOTES = QualityGateParameters.G8_MIN_PAIRED_NOTES;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test void reportWhenRequested() throws Exception {
        assumeTrue(Boolean.getBoolean("llm.benchmark.qualityDescriptors"), "opt-in offline report skipped");
        String dirs = System.getProperty("llm.benchmark.blindDirs");
        String out = System.getProperty("llm.benchmark.descriptorsOutDir");
        if (dirs == null || out == null) throw new IllegalArgumentException("blindDirs and descriptorsOutDir required");
        List<Path> paths = new ArrayList<>();
        for (String dir : dirs.split(",")) paths.add(Path.of(dir.trim()));
        String input = System.getProperty("llm.benchmark.input");
        if (input == null || input.isBlank()) throw new IllegalArgumentException("llm.benchmark.input is required for pair completeness");
        report(paths, Path.of(out), Path.of(input));
    }

    static void report(List<Path> dirs, Path out) throws Exception {
        report(dirs, out, null);
    }

    static void report(List<Path> dirs, Path out, Path qualityInput) throws Exception {
        Map<String,Map<String,JsonNode>> arms = new TreeMap<>();
        Set<String> packs = new java.util.TreeSet<>();
        Map<String,String> packLevels = new TreeMap<>();
        if (qualityInput != null) {
            List<String> lines = Files.readAllLines(qualityInput);
            if (lines.size() != QualityGateParameters.EXPECTED_NOTES)
                throw new IllegalArgumentException("Quality input must contain " + QualityGateParameters.EXPECTED_NOTES + " notes");
            for (String line : lines) {
                JsonNode input = MAPPER.readTree(line);
                if (!input.path("packId").isTextual()) throw new IllegalArgumentException("Quality input lacks packId");
                packs.add(input.path("packId").asText());
                recordLevel(packLevels,input);
            }
            if (packs.size() != QualityGateParameters.EXPECTED_NOTES) throw new IllegalArgumentException("Quality input packIds must be unique");
        }
        for (Path dir : dirs) for (String line : Files.readAllLines(dir.resolve("results.jsonl"))) {
            JsonNode row = MAPPER.readTree(line);
            if (!row.isObject() || !row.path("success").isBoolean() || !row.path("questions").isArray()
                    || !row.path("arm").isTextual() || !row.path("packId").isTextual())
                throw new IllegalArgumentException("Malformed quality results row in " + dir);
            String arm = row.path("arm").asText(), pack = row.path("packId").asText();
            if (!Set.of("A1","A2","A3","B2","B3").contains(arm)) throw new IllegalArgumentException("Unexpected arm " + arm);
            if (arms.computeIfAbsent(arm, ignored -> new TreeMap<>()).putIfAbsent(pack, row) != null)
                throw new IllegalArgumentException("Duplicate start for " + arm + "/" + pack);
            recordLevel(packLevels,row);
            packs.add(pack);
        }
        Map<String,Object> armReports = new LinkedHashMap<>();
        for (var arm : arms.entrySet()) {
            Map<String,Object> armReport = describeArm(arm.getValue());
            Map<String,Object> levels = new LinkedHashMap<>();
            for (String level : List.of("COLLEGE","BOARD_EXAM_REVIEW")) {
                Map<String,JsonNode> subset = new TreeMap<>();
                for (var start : arm.getValue().entrySet()) if (level.equals(packLevels.get(start.getKey()))) subset.put(start.getKey(),start.getValue());
                Map<String,Object> metrics = describeArm(subset);
                metrics.put("interpretation","DESCRIPTIVE_ONLY; never a gate input (10 notes per level)");
                levels.put(level,metrics);
            }
            armReport.put("perNoteLevel",levels);
            armReports.put(arm.getKey(),armReport);
        }
        Map<String,Object> pairReports = new LinkedHashMap<>();
        for (String[] pair : new String[][]{{"A2","B2"},{"A3","B3"}}) {
            List<Map<String,Object>> notes = new ArrayList<>(); int complete = 0;
            Map<String,JsonNode> pairedLeft = new TreeMap<>(), pairedRight = new TreeMap<>();
            for (String pack : packs) {
                JsonNode left = arms.getOrDefault(pair[0],Map.of()).get(pack);
                JsonNode right = arms.getOrDefault(pair[1],Map.of()).get(pack);
                boolean leftOk = left != null && left.path("success").asBoolean();
                boolean rightOk = right != null && right.path("success").asBoolean();
                if (leftOk && rightOk) { complete++; pairedLeft.put(pack,left); pairedRight.put(pack,right); }
                else notes.add(Map.of("packId",pack, pair[0], side(left), pair[1], side(right)));
            }
            Map<String,Object> pairReport = new LinkedHashMap<>(Map.of("pairedNotes",complete,
                    "minimumPairedNotes", MIN_PAIRED_NOTES, "verdict",complete < MIN_PAIRED_NOTES ? "INCONCLUSIVE" : "DESCRIPTIVE",
                    "missingOrFailedSides",notes));
            pairReport.put("pairedArms",Map.of(pair[0],describeArm(pairedLeft),pair[1],describeArm(pairedRight)));
            Map<String,Object> perLevel = new LinkedHashMap<>();
            for (String level : List.of("COLLEGE","BOARD_EXAM_REVIEW")) {
                int levelComplete = 0; List<Map<String,Object>> levelMissing = new ArrayList<>();
                for (String pack : packs) if (level.equals(packLevels.get(pack))) {
                    JsonNode left = arms.getOrDefault(pair[0],Map.of()).get(pack), right = arms.getOrDefault(pair[1],Map.of()).get(pack);
                    if (left != null && right != null && left.path("success").asBoolean() && right.path("success").asBoolean()) levelComplete++;
                    else levelMissing.add(Map.of("packId",pack,pair[0],side(left),pair[1],side(right)));
                }
                perLevel.put(level,Map.of("pairedNotes",levelComplete,"missingOrFailedSides",levelMissing,
                        "interpretation","DESCRIPTIVE_ONLY; never a gate input (10 notes per level)",
                        "pairedArms",Map.of(pair[0],describeArm(pairedLeft.entrySet().stream().filter(e -> level.equals(packLevels.get(e.getKey())))
                                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue))),
                                pair[1],describeArm(pairedRight.entrySet().stream().filter(e -> level.equals(packLevels.get(e.getKey())))
                                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue))))));
            }
            pairReport.put("perNoteLevel",perLevel);
            pairReports.put(pair[0] + "/" + pair[1],pairReport);
        }
        Map<String,Object> report = new LinkedHashMap<>();
        report.put("minimumPairedNotes",MIN_PAIRED_NOTES); report.put("arms",armReports); report.put("pairs",pairReports);
        report.put("validatorRejection", "Captured from challenge_quiz_generation_counts rejected and retried fields per start; missing telemetry is reported, never inferred from calls.");
        Files.createDirectories(out);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(out.resolve("descriptors.json").toFile(), report);
        StringBuilder md = new StringBuilder("# Challenge quality descriptors\n\nMinimum paired notes: 15 of 20 overall. Fewer is INCONCLUSIVE. Per-noteLevel results are descriptive only (10 notes each), never gate inputs. Indicators are diagnostic; scripts reject no items.\n\n");
        for (var pair : pairReports.entrySet()) md.append("## ").append(pair.getKey()).append("\n\n")
                .append(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(pair.getValue())).append("\n\n");
        for (var arm : armReports.entrySet()) md.append("## ").append(arm.getKey()).append("\n\n")
                .append(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(arm.getValue())).append("\n\n");
        for (var arm : armReports.entrySet()) {
            Map<String,Object> metrics = (Map<String,Object>) arm.getValue();
            Map<String,Object> g6 = (Map<String,Object>) metrics.get("g6");
            int missing = ((Number)g6.get("telemetryMissingStarts")).intValue();
            if (missing > 0) md.append("G7 validator-rejection: MISSING for ").append(missing).append(" starts ("+arm.getKey()+")\n\n");
        }
        md.append("Validator rejection is read from the production generation-count log attached by the test harness. API calls per start is a separate retry proxy.\n");
        Files.writeString(out.resolve("descriptors.md"), md.toString());
    }

    private static String side(JsonNode row) {
        return row == null ? "MISSING" : row.path("success").asBoolean() ? "SUCCESS" : "FAILED: " + row.path("failureClass").asText();
    }

    private static void recordLevel(Map<String,String> levels, JsonNode row) {
        String level = row.path("noteLevel").asText();
        if (!Set.of("COLLEGE","BOARD_EXAM_REVIEW").contains(level)) throw new IllegalArgumentException("Missing or invalid noteLevel");
        String pack = row.path("packId").asText();
        String previous = levels.putIfAbsent(pack,level);
        if (previous != null && !previous.equals(level)) throw new IllegalArgumentException("Conflicting noteLevel for " + pack);
    }

    private static Map<String,Object> describeArm(Map<String,JsonNode> starts) {
        Map<String,Object> report = new LinkedHashMap<>();
        Map<String,Object> perNote = new LinkedHashMap<>();
        Map<String,Integer> formats = new TreeMap<>(), answers = new TreeMap<>();
        int longest = 0, absolutes = 0, duplicateChoices = 0, duplicateQuestions = 0;
        int choiceItems = 0, trueFalse = 0, multiTwo = 0, multiThree = 0, delivered = 0, calls = 0;
        int accepted = 0, rejected = 0, retried = 0, telemetryMissing = 0;
        long inputTokens = 0, outputTokens = 0;
        List<Long> latencies = new ArrayList<>();
        for (var entry : starts.entrySet()) {
            JsonNode row = entry.getValue();
            if (!row.path("success").asBoolean()) continue;
            int count = row.path("questions").size(), attempts = row.path("attempts").asInt();
            int rowAccepted = -1, rowRejected = -1, rowRetried = -1;
            delivered += count; calls += attempts; inputTokens += row.path("inputTokens").asLong();
            outputTokens += row.path("outputTokens").asLong();
            latencies.add(row.path("latencyMs").asLong());
            if (row.path("validatorAccepted").isInt() && row.path("validatorRejected").isInt() && row.path("validatorRetried").isInt()) {
                accepted += row.path("validatorAccepted").asInt();
                rejected += row.path("validatorRejected").asInt(); retried += row.path("validatorRetried").asInt();
                rowAccepted=row.path("validatorAccepted").asInt(); rowRejected=row.path("validatorRejected").asInt();
                rowRetried=row.path("validatorRetried").asInt();
            } else if (row.path("success").asBoolean()) telemetryMissing++;
            List<String> seen = new ArrayList<>(); int duplicateInStart = 0, rowMultiTwo = 0, rowMultiThree = 0;
            for (JsonNode q : row.path("questions")) {
                String format = QualityBlindExport.format(q);
                formats.merge(format,1,Integer::sum);
                if ("TRUE_FALSE".equals(format)) trueFalse++;
                if ("MULTI_SELECT".equals(format)) {
                    int size = q.path("correctIndices").size();
                    if (size == 2) { multiTwo++; rowMultiTwo++; } else if (size == 3) { multiThree++; rowMultiThree++; }
                }
                String question = normalize(q.path("question").asText());
                if (seen.stream().anyMatch(previous -> similar(previous,question))) { duplicateQuestions++; duplicateInStart++; }
                seen.add(question);
                JsonNode choices = q.path("choices");
                if (!choices.isArray() || choices.size() < 2) continue;
                choiceItems++;
                List<String> seenChoices = new ArrayList<>();
                int maxLength = 0; boolean duplicate = false, wrongAbsolute = false, correctAbsolute = false;
                for (int i=0; i<choices.size(); i++) {
                    String value = choices.get(i).asText();
                    String normalizedChoice = normalize(value);
                    if (seenChoices.stream().anyMatch(previous -> similar(previous,normalizedChoice))) duplicate = true;
                    seenChoices.add(normalizedChoice);
                    maxLength = Math.max(maxLength,value.length());
                    boolean correct = isCorrect(q,format,i);
                    boolean absolute = value.matches("(?i).*(?:\\balways\\b|\\bnever\\b|\\ball\\b|\\bnone\\b).*" );
                    if (absolute && correct) correctAbsolute = true;
                    if (absolute && !correct) wrongAbsolute = true;
                }
                if (duplicate) duplicateChoices++;
                if (wrongAbsolute && !correctAbsolute) absolutes++;
                if (q.path("correctIndex").isInt()) {
                    int index = q.path("correctIndex").asInt();
                    if (index >= 0 && index < choices.size()) {
                        answers.merge(String.valueOf(index+1),1,Integer::sum);
                        if (choices.get(index).asText().length() == maxLength) longest++;
                    }
                }
            }
            Map<String,Object> noteMetrics=new LinkedHashMap<>();
            noteMetrics.put("success",row.path("success").asBoolean()); noteMetrics.put("delivered",count);
            noteMetrics.put("deliveredRate",(double)count/QualityGateParameters.REQUESTED_PER_START);
            noteMetrics.put("apiCalls",attempts); noteMetrics.put("latencyMs",row.path("latencyMs").asLong());
            noteMetrics.put("tokensPerDeliveredQuestion",count==0 ? "MISSING" : (double)(row.path("inputTokens").asLong()+row.path("outputTokens").asLong())/count);
            noteMetrics.put("allFormatValidatorRetryRate",rowAccepted<0 || rowAccepted+rowRejected==0 ? "MISSING" : (double)rowRetried/(rowAccepted+rowRejected));
            noteMetrics.put("allFormatValidatorRejectionRate",rowAccepted<0 || rowAccepted+rowRejected==0 ? "MISSING" : (double)rowRejected/(rowAccepted+rowRejected));
            noteMetrics.put("multiSelectThreeShare",rowMultiTwo+rowMultiThree==0 ? "MISSING" : (double)rowMultiThree/(rowMultiTwo+rowMultiThree));
            noteMetrics.put("nearDuplicateQuestions",duplicateInStart);
            noteMetrics.put("nearDuplicateStart",duplicateInStart>0 ? 1 : 0);
            perNote.put(entry.getKey(),noteMetrics);
        }
        latencies.sort(Long::compare);
        int successful=(int)starts.values().stream().filter(r -> r.path("success").asBoolean()).count();
        report.put("starts",successful); report.put("failedStarts",starts.size()-successful);
        report.put("perNote",perNote);
        report.put("g4",Map.of("choiceItems",choiceItems,"correctChoiceLongest",longest,
                "absoluteOnlyIncorrect",absolutes,"answerPositions",answers,
                "nearDuplicateChoices",duplicateChoices,"nearDuplicateQuestions",duplicateQuestions,
                "trueFalseShare",delivered == 0 ? 0d : (double)trueFalse/delivered));
        report.put("g6",Map.ofEntries(Map.entry("starts",successful),Map.entry("deliveredTotal",delivered),Map.entry("apiCallsTotal",calls),
                Map.entry("meanCallsPerStart",successful==0 ? 0d : (double)calls/successful),
                Map.entry("p50LatencyMs",latencies.isEmpty() ? 0 : latencies.get((latencies.size()-1)/2)),
                Map.entry("tokensPerDeliveredQuestion",delivered == 0 ? 0d : (double)(inputTokens+outputTokens)/delivered),
                Map.entry("validatorAccepted",accepted),Map.entry("validatorRejected",rejected),Map.entry("validatorRetried",retried),Map.entry("telemetryMissingStarts",telemetryMissing),
                Map.entry("allFormatValidatorRejectionRate",accepted+rejected > 0 ? (double)rejected/(accepted+rejected) : "UNOBSERVABLE"),
                Map.entry("allFormatValidatorRetryRate",accepted+rejected > 0 ? (double)retried/(accepted+rejected) : "UNOBSERVABLE")));
        report.put("g7",Map.of("starts",successful,"formatMix",formats,"multiSelectTwoCorrect",multiTwo,"multiSelectThreeCorrect",multiThree,
                "validatorAccepted",accepted,"validatorRejected",rejected,"validatorRetried",retried,
                "telemetryMissingStarts",telemetryMissing,
                "allFormatValidatorRejectionRate",accepted+rejected > 0 ? (double)rejected/(accepted+rejected) : "UNOBSERVABLE",
                "allFormatValidatorRetryRate",accepted+rejected > 0 ? (double)retried/(accepted+rejected) : "UNOBSERVABLE"));
        return report;
    }
    private static boolean isCorrect(JsonNode q, String format, int index) {
        if ("MULTI_SELECT".equals(format)) for (JsonNode n : q.path("correctIndices")) if (n.asInt(-1) == index) return true;
        return q.path("correctIndex").asInt(-1) == index;
    }
    private static String normalize(String text) {
        return text.toLowerCase().replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
    }
    private static boolean similar(String left, String right) {
        if (left.equals(right)) return true;
        Set<String> a = new java.util.HashSet<>(List.of(left.split(" +")));
        Set<String> b = new java.util.HashSet<>(List.of(right.split(" +")));
        if (a.isEmpty() || b.isEmpty()) return false;
        long common = a.stream().filter(b::contains).count();
        return (double)common / (a.size() + b.size() - common) >= .85;
    }
}
