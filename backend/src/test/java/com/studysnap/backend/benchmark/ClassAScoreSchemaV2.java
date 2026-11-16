package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict, role-specific class-A score rows. The v1 validator remains independent. */
final class ClassAScoreSchemaV2 {
    enum Code { SCHEMA, STRUCTURE, QUOTE_NOT_FOUND, PACKET }
    static final class InvalidRow extends RuntimeException {
        private final Code code;
        InvalidRow(Code code, String message) { super(message); this.code = code; }
        Code code() { return code; }
    }
    record PacketQuestion(JsonNode question, List<String> sourceNoteTexts) {}
    enum Verdict { CLEAN, QUALITY_FAIL, CRITICAL, UNRESOLVED }

    static final Set<String> ALL_TYPES;
    private static final Set<String> STRUCTURAL_TYPES = Set.of("WRONG_KEY", "KEY_NOT_DEFENSIBLE", "SECOND_DEFENSIBLE_ANSWER");
    private static final Set<String> SCORER_FIELDS = Set.of("schemaVersion", "id", "reviewerId", "invocationId", "scorerRole",
            "factuallyCorrect", "grounding", "critical", "criticalType", "modeAlignment", "cognitiveDemand",
            "clarity", "faithfulness", "distractorPlausibility", "seriousGiveaway", "giveawayPattern",
            "defensibleChoiceIds", "defensibleAnswerCount", "keyDefensible", "ambiguityClass", "borderline",
            "rationale", "rationaleQuote", "rootCause", "inputSpanQuote");
    private static final Set<String> HUMAN_FIELDS = Set.of("schemaVersion", "id", "reviewerId", "scorerRole",
            "humanVerdict", "humanClearReason", "criticalType", "defensibleChoiceIds", "defensibleAnswerCount",
            "keyDefensible", "ambiguityClass", "borderline", "rationale", "rationaleQuote", "rootCause", "inputSpanQuote");
    private static final Pattern FRAGMENTS = Pattern.compile("\\.\\.\\.|…");
    static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    static {
        Set<String> types = new HashSet<>(QualityScoreFiles.TYPES);
        types.add("KEY_NOT_DEFENSIBLE");
        ALL_TYPES = Set.copyOf(types);
    }
    private ClassAScoreSchemaV2() {}

    static JsonNode parse(String json) {
        try {
            JsonNode row = JSON.readTree(json);
            if (row == null || !row.isObject()) fail(Code.SCHEMA, "Score row must be an object");
            return row;
        } catch (InvalidRow exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidRow(Code.SCHEMA, "Cannot parse score row: " + exception.getMessage());
        }
    }

    static void validate(JsonNode row, PacketQuestion packet, EvidenceMapping mapping) {
        if (row == null || !row.isObject()) fail(Code.SCHEMA, "Score row must be an object");
        if (packet == null || packet.question() == null || !packet.question().isObject()
                || packet.sourceNoteTexts() == null || packet.sourceNoteTexts().stream().anyMatch(s -> s == null))
            fail(Code.PACKET, "Question and source note texts required");
        validatePacket(packet);
        if (mapping == null) fail(Code.SCHEMA, "Evidence mapping required");
        String role = requiredText(row, "scorerRole");
        boolean human = "H".equals(role);
        if (!human && !Set.of("P", "S").contains(role)) fail(Code.SCHEMA, "Unknown scorerRole");
        Set<String> names = new HashSet<>();
        row.fieldNames().forEachRemaining(names::add);
        if (!names.equals(human ? HUMAN_FIELDS : SCORER_FIELDS)) fail(Code.SCHEMA, "Fields differ for " + role + ": " + names);
        if (!row.path("schemaVersion").isInt() || row.path("schemaVersion").asInt() != 2)
            fail(Code.SCHEMA, "schemaVersion must be integer 2");
        requiredText(row, "id");
        requiredText(row, "reviewerId");
        if (!human) requiredText(row, "invocationId");
        JsonNode q = packet.question();
        String format = QualityBlindExport.format(q);
        boolean choice = choice(format);
        Set<Integer> keyed = choice ? keyed(q, format) : Set.of();
        JsonNode ids = row.path("defensibleChoiceIds");
        Set<Integer> defensible = new HashSet<>();
        if (choice) {
            if (!ids.isArray()) fail(Code.SCHEMA, "Choice format requires defensibleChoiceIds array");
            for (JsonNode id : ids) {
                if (!id.isInt() || id.asInt() < 0 || id.asInt() >= q.path("choices").size()
                        || !defensible.add(id.asInt())) fail(Code.SCHEMA, "Invalid or duplicate defensible choice id");
            }
        } else if (!ids.isNull()) fail(Code.SCHEMA, "Non-choice defensibleChoiceIds must be null");
        JsonNode countNode = row.path("defensibleAnswerCount");
        if (!countNode.isInt() || countNode.asInt() < 0) fail(Code.SCHEMA, "Invalid defensibleAnswerCount");
        int count = countNode.asInt();
        if (choice && count != defensible.size()) fail(Code.STRUCTURE, "Defensible count differs from choice ids");
        requireBoolean(row, "keyDefensible");
        boolean keyDefensible = row.path("keyDefensible").asBoolean();
        if (choice && keyDefensible != defensible.containsAll(keyed))
            fail(Code.STRUCTURE, "keyDefensible disagrees with packet key");
        if (!choice && count == 0 && keyDefensible) fail(Code.STRUCTURE, "Zero defensible answers cannot defend key");
        String structural = count == 0 ? "KEY_NOT_DEFENSIBLE" : !keyDefensible ? "WRONG_KEY"
                : (choice ? defensible.size() > keyed.size() : count >= 2) ? "SECOND_DEFENSIBLE_ANSWER" : "NONE";
        String ambiguity = requiredEnum(row, "ambiguityClass", Set.of("NONE", "MINOR_WORDING", "MATERIAL"));
        boolean material = "KEY_NOT_DEFENSIBLE".equals(structural) || "SECOND_DEFENSIBLE_ANSWER".equals(structural)
                || ("WRONG_KEY".equals(structural) && count >= 2);
        if (material != "MATERIAL".equals(ambiguity)) fail(Code.STRUCTURE, "MATERIAL ambiguity disagrees with structure");
        if ("MINOR_WORDING".equals(ambiguity) && !"NONE".equals(structural))
            fail(Code.STRUCTURE, "MINOR_WORDING requires no structural defect");
        requireBoolean(row, "borderline");
        nullableText(row, "rationale"); nullableText(row, "rationaleQuote");
        nullableText(row, "inputSpanQuote");
        String cause = nullableEnum(row, "rootCause", Set.of("GENERATOR", "INPUT"));
        String type = nullableEnum(row, "criticalType", ALL_TYPES);
        if (!"NONE".equals(structural) && !(human && "UNRESOLVED".equals(row.path("humanVerdict").asText()))
                && !structural.equals(type))
            fail(Code.STRUCTURE, "Structural criticalType must be " + structural);
        if (type != null && STRUCTURAL_TYPES.contains(type) && !type.equals(structural))
            fail(Code.STRUCTURE, "Structural criticalType disagrees with question");
        if ("AMBIGUOUS_STEM".equals(type) && !"MINOR_WORDING".equals(ambiguity))
            fail(Code.STRUCTURE, "AMBIGUOUS_STEM requires MINOR_WORDING");
        Verdict verdict;
        if (human) {
            verdict = Verdict.valueOf(requiredEnum(row, "humanVerdict", Set.of("CLEAN", "QUALITY_FAIL", "CRITICAL", "UNRESOLVED")));
            String reason = nullableEnum(row, "humanClearReason", Set.of("REVIEWED_NOT_DEFECT", "DOWNGRADED_NONCRITICAL"));
            if (reason != null && !("REVIEWED_NOT_DEFECT".equals(reason) && verdict == Verdict.CLEAN)
                    && !("DOWNGRADED_NONCRITICAL".equals(reason) && verdict == Verdict.QUALITY_FAIL))
                fail(Code.STRUCTURE, "humanClearReason disagrees with verdict");
            if (!"NONE".equals(structural) && verdict != Verdict.CRITICAL && verdict != Verdict.UNRESOLVED)
                fail(Code.STRUCTURE, "Structural defect requires critical or unresolved human verdict");
            if (verdict == Verdict.CRITICAL && (type == null || !mapping.classACriticalTypes.contains(type)))
                fail(Code.STRUCTURE, "CRITICAL requires mapped criticalType");
            if (verdict == Verdict.QUALITY_FAIL && type != null && mapping.classACriticalTypes.contains(type)
                    && !"MINOR_WORDING".equals(ambiguity))
                fail(Code.STRUCTURE, "QUALITY_FAIL has mapped criticalType");
            if (verdict == Verdict.CLEAN && (type != null || !"NONE".equals(ambiguity) || !"NONE".equals(structural)))
                fail(Code.STRUCTURE, "CLEAN human row has defect");
            if (verdict == Verdict.UNRESOLVED && type != null) fail(Code.STRUCTURE, "UNRESOLVED criticalType must be null");
        } else {
            validateGraded(row, choice);
            if (!"NONE".equals(structural) && !row.path("critical").asBoolean())
                fail(Code.STRUCTURE, "Structural defect must be critical");
            verdict = verdict(row, mapping);
        }
        boolean nonClean = verdict != Verdict.CLEAN || row.path("borderline").asBoolean();
        if (nonClean || (human && !row.path("humanClearReason").isNull())) {
            String rationale = nonBlank(row, "rationale");
            String quote = nonBlank(row, "rationaleQuote");
            if (rationale.isBlank()) fail(Code.SCHEMA, "rationale required");
            List<String> evidence = new ArrayList<>();
            evidence.add(requiredPacketText(q, "question"));
            if (q.path("choices").isArray()) for (JsonNode item : q.path("choices")) evidence.add(item.asText());
            if (q.path("explanation").isTextual()) evidence.add(q.path("explanation").asText());
            evidence.addAll(packet.sourceNoteTexts());
            if (!found(quote, evidence)) fail(Code.QUOTE_NOT_FOUND, "rationaleQuote not found in packet or source");
            if (cause == null && nonClean) fail(Code.SCHEMA, "rootCause required");
            if ("INPUT".equals(cause)) {
                String sourceQuote = nonBlank(row, "inputSpanQuote");
                if (!found(sourceQuote, packet.sourceNoteTexts()))
                    fail(Code.QUOTE_NOT_FOUND, "inputSpanQuote not found in source note");
            } else if (!row.path("inputSpanQuote").isNull()) fail(Code.SCHEMA, "GENERATOR inputSpanQuote must be null");
        } else {
            if (!"NONE".equals(ambiguity) || !keyDefensible || count != (choice ? keyed.size() : 1)
                    || !allNull(row, "rationale", "rationaleQuote", "rootCause", "inputSpanQuote"))
                fail(Code.STRUCTURE, "Unflagged clean row has non-clean facts");
        }
    }

    static Verdict verdict(JsonNode row, EvidenceMapping mapping) {
        if ("H".equals(row.path("scorerRole").asText()))
            return Verdict.valueOf(row.path("humanVerdict").asText());
        if (mapping.criticalA(row)) return Verdict.CRITICAL;
        if (mapping.qualityA(row) || "MINOR_WORDING".equals(row.path("ambiguityClass").asText())
                || row.path("critical").asBoolean()) return Verdict.QUALITY_FAIL;
        return Verdict.CLEAN;
    }

    static void validatePacket(PacketQuestion packet) {
        if (packet == null || packet.question() == null || !packet.question().isObject()
                || packet.sourceNoteTexts() == null || packet.sourceNoteTexts().stream().anyMatch(s -> s == null))
            fail(Code.PACKET, "Malformed packet question or source note texts");
        JsonNode q = packet.question();
        requiredPacketText(q, "question");
        String format = QualityBlindExport.format(q);
        if (choice(format)) keyed(q, format);
        else {
            if (!q.path("choices").isArray()) fail(Code.PACKET, "Non-choice packet lacks choices array");
            JsonNode index = q.path("correctIndex");
            if (!index.isMissingNode()) {
                if (!index.isInt() || index.asInt() < 0 || index.asInt() >= q.path("choices").size())
                    fail(Code.PACKET, "Invalid non-choice packet key index");
            } else if (!q.path("acceptableAnswers").isArray()
                    && !q.path("acceptableAnswerGroups").isArray())
                fail(Code.PACKET, "Non-choice packet lacks answer key");
        }
    }

    private static void validateGraded(JsonNode row, boolean choice) {
        requiredEnum(row, "factuallyCorrect", Set.of("YES", "NO", "UNVERIFIABLE"));
        String grounding = requiredEnum(row, "grounding", Set.of("GROUNDED", "UNSUPPORTED", "CONTRADICTED"));
        requireBoolean(row, "critical");
        if (row.path("critical").asBoolean() != (row.path("criticalType").isTextual()))
            fail(Code.SCHEMA, "critical and criticalType disagree");
        for (String field : List.of("modeAlignment", "cognitiveDemand", "clarity", "faithfulness")) score(row, field);
        if (choice) {
            score(row, "distractorPlausibility");
            requireBoolean(row, "seriousGiveaway");
            nullableEnum(row, "giveawayPattern", QualityScoreFiles.PATTERNS);
            if (row.path("seriousGiveaway").asBoolean() != row.path("giveawayPattern").isTextual())
                fail(Code.SCHEMA, "seriousGiveaway and giveawayPattern disagree");
        } else if (!allNull(row, "distractorPlausibility", "seriousGiveaway", "giveawayPattern"))
            fail(Code.SCHEMA, "Non-choice graded fields must be null");
        if ("CONTRADICTED".equals(grounding) && !row.path("critical").asBoolean())
            fail(Code.SCHEMA, "CONTRADICTED grounding requires critical");
    }
    private static boolean choice(String format) {
        if (Set.of("MCQ", "MULTI_SELECT", "MATCHING", "ASSERTION_REASON").contains(format)) return true;
        if (Set.of("TRUE_FALSE", "IDENTIFICATION", "ENUMERATION", "SHORT_ANSWER", "FILL_IN_THE_BLANK", "FREE_TEXT").contains(format)) return false;
        fail(Code.PACKET, "Unknown packet questionFormat: " + format);
        return false;
    }
    private static Set<Integer> keyed(JsonNode q, String format) {
        JsonNode choices = q.path("choices");
        if (!choices.isArray() || choices.isEmpty()) fail(Code.PACKET, "Choice packet lacks choices");
        Set<Integer> result = new HashSet<>();
        if ("MULTI_SELECT".equals(format)) {
            JsonNode indices = q.path("correctIndices");
            if (!indices.isArray() || indices.isEmpty()) fail(Code.PACKET, "MULTI_SELECT lacks correctIndices");
            for (JsonNode index : indices) packetIndex(index, choices.size(), result);
        } else packetIndex(q.path("correctIndex"), choices.size(), result);
        return Set.copyOf(result);
    }
    private static void packetIndex(JsonNode index, int size, Set<Integer> result) {
        if (!index.isInt() || index.asInt() < 0 || index.asInt() >= size || !result.add(index.asInt()))
            fail(Code.PACKET, "Missing, duplicate or out-of-range packet key index");
    }
    private static String requiredPacketText(JsonNode row, String field) {
        JsonNode value = row.path(field);
        if (!value.isTextual() || value.asText().isBlank()) fail(Code.PACKET, "Packet lacks " + field);
        return value.asText();
    }
    private static void score(JsonNode row, String field) {
        JsonNode value = row.path(field);
        if (!value.isInt() || value.asInt() < 1 || value.asInt() > 5) fail(Code.SCHEMA, "Score 1-5 required: " + field);
    }
    private static boolean allNull(JsonNode row, String... fields) {
        for (String field : fields) if (!row.path(field).isNull()) return false;
        return true;
    }
    private static void requireBoolean(JsonNode row, String field) {
        if (!row.path(field).isBoolean()) fail(Code.SCHEMA, "Boolean required: " + field);
    }
    private static String requiredText(JsonNode row, String field) {
        JsonNode value = row.path(field);
        if (!value.isTextual() || value.asText().isBlank()) fail(Code.SCHEMA, "Text required: " + field);
        return value.asText();
    }
    private static String nonBlank(JsonNode row, String field) { return requiredText(row, field); }
    private static void nullableText(JsonNode row, String field) {
        JsonNode value = row.path(field);
        if (!value.isNull() && !value.isTextual()) fail(Code.SCHEMA, "Text or null required: " + field);
    }
    private static String requiredEnum(JsonNode row, String field, Set<String> values) {
        String value = requiredText(row, field);
        if (!values.contains(value)) fail(Code.SCHEMA, "Invalid " + field + ": " + value);
        return value;
    }
    private static String nullableEnum(JsonNode row, String field, Set<String> values) {
        JsonNode value = row.path(field);
        if (value.isNull()) return null;
        return requiredEnum(row, field, values);
    }
    private static boolean found(String quote, List<String> texts) {
        String normalized = normalize(quote);
        boolean fragment = false;
        for (String part : FRAGMENTS.split(normalized)) {
            String needle = part.trim();
            if (needle.length() < 8) continue;
            fragment = true;
            boolean matched = texts.stream().map(ClassAScoreSchemaV2::normalize).anyMatch(text -> text.contains(needle));
            if (!matched) return false;
        }
        return fragment;
    }
    private static String normalize(String source) {
        return Normalizer.normalize(source, Normalizer.Form.NFKC)
                .replace('‘', '\'').replace('’', '\'').replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-').replace('−', '-')
                .replace("$", "").replace("\\", "").replace("*", "").replace("`", "")
                .replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT).trim();
    }
    private static void fail(Code code, String message) { throw new InvalidRow(code, message); }
}
