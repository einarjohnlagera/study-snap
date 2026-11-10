package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict offline schema and reviewer identity checks. */
final class QualityScoreFiles {
    static final ObjectMapper JSON = new ObjectMapper();
    static final Set<String> TYPES = Set.of("WRONG_KEY", "EXPLANATION_CONTRADICTS_KEY",
            "CONTRADICTS_GENERATOR_INPUTS", "CONTRADICTS_FULL_NOTE", "SOURCE_CONFLICT",
            "PARTLY_CORRECT_DISTRACTOR", "SECOND_DEFENSIBLE_ANSWER", "AMBIGUOUS_STEM",
            "COMPLEXITY_UNSUPPORTED_BY_INPUTS", "OTHER");
    static final Set<String> WORDING_TYPES = Set.of("PARTLY_CORRECT_DISTRACTOR", "SECOND_DEFENSIBLE_ANSWER",
            "AMBIGUOUS_STEM", "COMPLEXITY_UNSUPPORTED_BY_INPUTS");
    static final Set<String> PATTERNS = Set.of("LENGTH_OR_SPECIFICITY", "ABSOLUTE_WORDS",
            "GRAMMAR_AGREEMENT", "FORMAT_ODD_ONE_OUT", "DUPLICATE_CHOICES", "ALL_OR_NONE_OF_THE_ABOVE");
    private static final Set<String> FIELDS = Set.of("id", "reviewerId", "round", "factuallyCorrect",
            "grounding", "critical", "criticalType", "distractorPlausibility", "modeAlignment",
            "cognitiveDemand", "clarity", "faithfulness", "seriousGiveaway", "giveawayPattern");
    private QualityScoreFiles() {}

    static List<JsonNode> lines(Path path) throws Exception {
        List<JsonNode> rows = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) throw new IllegalArgumentException("Blank JSONL row in " + path);
            JsonNode row = JSON.readTree(line);
            if (!row.isObject()) throw new IllegalArgumentException("Object required in " + path);
            rows.add(row);
        }
        return rows;
    }
    static void write(Path path, List<?> rows) throws Exception {
        List<String> lines = new ArrayList<>();
        for (Object row : rows) lines.add(JSON.writeValueAsString(row));
        Files.write(path,lines);
    }
    static String text(JsonNode row, String name) {
        JsonNode value = row.get(name);
        if (value == null || !value.isTextual() || value.asText().isBlank())
            throw new IllegalArgumentException("Missing or invalid " + name + " in " + row);
        return value.asText();
    }
    static boolean choice(JsonNode key) {
        return switch (text(key,"questionFormat")) {
            case "MCQ", "MULTI_SELECT", "MATCHING", "ASSERTION_REASON" -> true;
            case "TRUE_FALSE", "IDENTIFICATION", "ENUMERATION", "SHORT_ANSWER", "FILL_IN_THE_BLANK", "FREE_TEXT" -> false;
            default -> throw new IllegalArgumentException("Unknown questionFormat for " + text(key,"id"));
        };
    }
    static Map<String,JsonNode> keyed(Path path) throws Exception {
        Map<String,JsonNode> result = new LinkedHashMap<>();
        for (JsonNode row : lines(path)) if (result.putIfAbsent(text(row,"id"),row) != null)
            throw new IllegalArgumentException("Duplicate id in " + path + ": " + text(row,"id"));
        return result;
    }
    static Map<String,List<JsonNode>> scores(Path keyPath, Path primary, Path reviews, Path adjudications) throws Exception {
        return scores(keyPath,primary,reviews,adjudications,null);
    }
    static Map<String,List<JsonNode>> scores(Path keyPath, Path primary, Path reviews, Path adjudications,
                                              Set<String> includedArms) throws Exception {
        Map<String,JsonNode> allKeys = keyed(keyPath);
        Map<String,JsonNode> keys = new LinkedHashMap<>();
        for (var entry:allKeys.entrySet()) if (includedArms==null || includedArms.contains(text(entry.getValue(),"arm")))
            keys.put(entry.getKey(),entry.getValue());
        Map<String,List<JsonNode>> byId = new LinkedHashMap<>();
        readScores(primary, "PRIMARY", keys, allKeys, byId);
        readScores(reviews, "INDEPENDENT_REVIEW", keys, allKeys, byId);
        readScores(adjudications, "ADJUDICATION", keys, allKeys, byId);
        for (var entry:byId.entrySet()) if (find(entry.getValue(),"ADJUDICATION")!=null
                && find(entry.getValue(),"INDEPENDENT_REVIEW")==null && find(entry.getValue(),"ARM_RE_REVIEW")==null)
            throw new IllegalArgumentException("Adjudication requires an independent review for " + entry.getKey());
        Map<String,Set<String>> earlierByArm = new HashMap<>();
        Map<String,Set<String>> preAdjudicatorsByArm = new HashMap<>();
        for (var entry : byId.entrySet()) for (JsonNode score : entry.getValue()) {
            if (!"ARM_RE_REVIEW".equals(text(score,"round")))
                earlierByArm.computeIfAbsent(text(keys.get(entry.getKey()),"arm"), ignored -> new HashSet<>())
                        .add(text(score,"reviewerId"));
            if (!"ADJUDICATION".equals(text(score,"round")))
                preAdjudicatorsByArm.computeIfAbsent(text(keys.get(entry.getKey()),"arm"), ignored -> new HashSet<>())
                        .add(text(score,"reviewerId"));
        }
        Map<String,String> reReviewerByArm = new HashMap<>();
        for (var entry : byId.entrySet()) for (JsonNode score : entry.getValue()) {
            String round = text(score,"round"), reviewer = text(score,"reviewerId"), arm = text(keys.get(entry.getKey()),"arm");
            if ("INDEPENDENT_REVIEW".equals(round)) {
                JsonNode first = find(entry.getValue(),"PRIMARY");
                if (first == null || reviewer.equals(text(first,"reviewerId")))
                    throw new IllegalArgumentException("Independent reviewer must differ from primary for " + entry.getKey());
            }
            if ("ADJUDICATION".equals(round) && preAdjudicatorsByArm.getOrDefault(arm,Set.of()).contains(reviewer))
                throw new IllegalArgumentException("Adjudicator previously scored arm " + arm);
            if ("ARM_RE_REVIEW".equals(round)) {
                if (earlierByArm.getOrDefault(arm,Set.of()).contains(reviewer))
                    throw new IllegalArgumentException("Arm re-reviewer previously scored arm " + arm);
                String previous = reReviewerByArm.putIfAbsent(arm,reviewer);
                if (previous != null && !previous.equals(reviewer))
                    throw new IllegalArgumentException("Arm re-review must use one fresh reviewer for " + arm);
            }
        }
        return byId;
    }
    private static void readScores(Path path, String normalRound, Map<String,JsonNode> keys,Map<String,JsonNode> allKeys,
                                   Map<String,List<JsonNode>> byId) throws Exception {
        for (JsonNode score : lines(path)) {
            String id = text(score,"id"), round = text(score,"round");
            JsonNode key = keys.get(id);
            if (key == null) {
                if (allKeys.containsKey(id)) continue;
                throw new IllegalArgumentException("Unknown score id " + id);
            }
            if (!round.equals(normalRound) && !("INDEPENDENT_REVIEW".equals(normalRound) && "ARM_RE_REVIEW".equals(round)))
                throw new IllegalArgumentException("Unexpected round " + round + " in " + path);
            validate(score,choice(key));
            List<JsonNode> scores = byId.computeIfAbsent(id,ignored -> new ArrayList<>());
            if (find(scores,round) != null) throw new IllegalArgumentException("Duplicate " + round + " for " + id);
            scores.add(score);
        }
        if ("PRIMARY".equals(normalRound) && byId.size() != keys.size())
            throw new IllegalArgumentException("Primary scores must cover every blind item: " + byId.size() + "/" + keys.size());
    }
    static JsonNode find(List<JsonNode> scores, String round) {
        if (scores == null) return null;
        for (JsonNode score : scores) if (round.equals(score.path("round").asText())) return score;
        return null;
    }
    static void validate(JsonNode score, boolean choice) {
        Set<String> actual = new HashSet<>(); score.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(FIELDS)) throw new IllegalArgumentException("Score fields differ: " + score.path("id").asText() + " " + actual);
        text(score,"id"); text(score,"reviewerId");
        if (!Set.of("PRIMARY","INDEPENDENT_REVIEW","ARM_RE_REVIEW","ADJUDICATION").contains(text(score,"round"))) throw new IllegalArgumentException("Unknown round");
        if (!Set.of("YES","NO","UNVERIFIABLE").contains(text(score,"factuallyCorrect"))) throw new IllegalArgumentException("Unknown factual judgement");
        if (!Set.of("GROUNDED","UNSUPPORTED","CONTRADICTED").contains(text(score,"grounding"))) throw new IllegalArgumentException("Unknown grounding");
        requireBoolean(score,"critical");
        nullableEnum(score,"criticalType",TYPES,score.path("critical").asBoolean());
        score1to5(score,"modeAlignment"); score1to5(score,"cognitiveDemand");
        score1to5(score,"clarity"); score1to5(score,"faithfulness");
        if (choice) {
            score1to5(score,"distractorPlausibility"); requireBoolean(score,"seriousGiveaway");
            nullableEnum(score,"giveawayPattern",PATTERNS,score.path("seriousGiveaway").asBoolean());
        } else if (!score.path("distractorPlausibility").isNull() || !score.path("seriousGiveaway").isNull()
                || !score.path("giveawayPattern").isNull())
            throw new IllegalArgumentException("Choice-only fields must be null for " + text(score,"id"));
        if ("CONTRADICTED".equals(text(score,"grounding")) && !score.path("critical").asBoolean())
            throw new IllegalArgumentException("Contradicted grounding is critical for " + text(score,"id"));
    }
    private static void requireBoolean(JsonNode row,String field) {
        if (!row.path(field).isBoolean()) throw new IllegalArgumentException("Boolean required: " + field);
    }
    private static void score1to5(JsonNode row,String field) {
        if (!row.path(field).isInt() || row.path(field).asInt() < 1 || row.path(field).asInt() > 5)
            throw new IllegalArgumentException("Score 1-5 required: " + field);
    }
    private static void nullableEnum(JsonNode row,String field,Set<String> values,boolean required) {
        JsonNode value = row.path(field);
        if (required ? !value.isTextual() || !values.contains(value.asText()) : !value.isNull())
            throw new IllegalArgumentException("Invalid " + field + " for " + text(row,"id"));
    }
}
