package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** Fail-closed scan of packet metadata and nested generation-order artifacts. */
final class PacketMetadataScan {
    static final class MetadataLeak extends RuntimeException {
        private final List<String> problems;
        MetadataLeak(List<String> problems) {
            super("Packet metadata leak: " + problems);
            this.problems = List.copyOf(problems);
        }
        List<String> problems() { return problems; }
    }
    static final class InvalidScan extends RuntimeException {
        InvalidScan(String message) { super(message); }
    }
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final String K_OUTPUTS = "outputs";
    private static final String K_ITEMS = "items";
    private static final String K_ITEM_ID = "itemId";
    private static final String K_QUESTION = "question";
    private static final String K_FIXTURE = "fixture";
    private static final String K_PLACEMENT = "placement";
    private static final String K_ID = "id";
    private static final String JSONL_SUFFIX = ".jsonl";
    private static final Set<String> A_PACKET = Set.of("requestedQuestionCount", "sourceNoteTexts", K_OUTPUTS);
    private static final Set<String> A_OUTPUT = Set.of(K_PLACEMENT, K_ITEMS);
    private static final Set<String> A_ITEM = Set.of(K_ITEM_ID, K_QUESTION);
    private static final Set<String> A_QUESTION = Set.of(K_QUESTION, "choices", "correctIndex", "correctIndices",
            "concept", "explanation", "questionFormat", "questionType", "workingSolution", "questionGroup",
            "keyConcept", "acceptableAnswers", "acceptableAnswerGroups", "keyedAnswer");
    private static final Set<String> B_PACKET = Set.of(K_FIXTURE, K_OUTPUTS);
    private static final Set<String> B_OUTPUT = Set.of(K_ITEM_ID, K_PLACEMENT, "output");
    private static final Set<String> B_FIXTURE = Set.of("workflow", "stratum", "reserve", "sourceLevel",
            "incorrectQuestions", "requestedSections", "companionContext", "companion", "priorTurns",
            K_QUESTION, "answerableFromCompanion", "interviewQuestion", "selectedChoiceIndex");
    // Real class-B Case exporter across all four workflows: mentorTips.id and priorTurns.createdAt.
    // A populated createdAt timestamp still fails the value check.
    static final Set<String> B_NESTED_METADATA_NAMES = Set.of(K_ID, "createdAt");
    private PacketMetadataScan() {}

    static void scan(Path packetDirectory, Set<String> forbiddenTokens) {
        if (forbiddenTokens == null || forbiddenTokens.isEmpty()
                || forbiddenTokens.stream().anyMatch(s -> s == null || s.length() < 4))
            throw new InvalidScan("Forbidden tokens must contain at least four characters");
        List<String> problems = new ArrayList<>();
        try {
            List<Path> files;
            try (Stream<Path> paths = Files.list(packetDirectory)) {
                files = paths.filter(path -> path.getFileName().toString().endsWith(JSONL_SUFFIX)).sorted().toList();
            }
            if (files.isEmpty()) problems.add("No packet JSONL files");
            int nonempty = 0;
            for (Path file : files) if (Files.size(file) > 0) nonempty++;
            if (!files.isEmpty() && nonempty == 0) problems.add("No packet rows");
            for (Path file : files) scanFile(file, forbiddenTokens, problems);
        } catch (Exception exception) { problems.add("Cannot read packet directory: " + exception.getMessage()); }
        if (!problems.isEmpty()) throw new MetadataLeak(problems);
    }
    private static void scanFile(Path file, Set<String> tokens, List<String> problems) {
        String name = file.getFileName().toString();
        String stem = name.substring(0, name.length() - JSONL_SUFFIX.length());
        if (!stem.matches("[A-Za-z0-9_-]+")) problems.add(name + " non-neutral file name");
        checkWords(stem, name, tokens, problems);
        try {
            List<String> lines = Files.readAllLines(file);
            // Class-B export writes zero-byte files for workflows absent from a fixture set.
            if (lines.isEmpty()) return;
            String previousId = null;
            for (int number = 0; number < lines.size(); number++) {
                String location = name + ":" + (number + 1);
                try {
                    JsonNode packet = JSON.readTree(lines.get(number));
                    if (packet == null || !packet.isObject()) { problems.add(location + " invalid packet"); continue; }
                    boolean a = packet.has("requestedQuestionCount") || packet.has("sourceNoteTexts");
                    structural(packet, a ? A_PACKET : B_PACKET, location, tokens, problems);
                    JsonNode outputs = packet.path(K_OUTPUTS);
                    if (!outputs.isArray() || outputs.size() != 2) problems.add(location + " invalid outputs");
                    else for (int outputIndex = 0; outputIndex < outputs.size(); outputIndex++) {
                        JsonNode output = outputs.get(outputIndex);
                        String outputPath = location + ".outputs[" + outputIndex + "]";
                        structural(output, a ? A_OUTPUT : B_OUTPUT, outputPath, tokens, problems);
                        if (a) {
                            JsonNode items = output.path(K_ITEMS);
                            if (!items.isArray()) problems.add(outputPath + " invalid items");
                            else for (JsonNode item : items) {
                                structural(item, A_ITEM, outputPath + ".item", tokens, problems);
                                String id = item.path(K_ITEM_ID).asText();
                                previousId = id(id, previousId, outputPath, problems);
                                nested(item.path(K_QUESTION), outputPath + ".question", A_QUESTION,
                                        tokens, problems, true);
                            }
                        } else {
                            previousId = id(output.path(K_ITEM_ID).asText(), previousId, outputPath, problems);
                        }
                    }
                    if (!a) {
                        JsonNode fixture = packet.path(K_FIXTURE);
                        structural(fixture, B_FIXTURE, location + ".fixture", tokens, problems);
                        nested(fixture, location + ".fixture", B_NESTED_METADATA_NAMES, tokens, problems, false);
                    }
                    // The complete packet must carry neither known arm fields nor exporter LEAK terms.
                    NoArmFields.check(lines.get(number));
                } catch (NoArmFields.ArmFieldDetected exception) { problems.add(location + " " + exception.getMessage()); }
                catch (Exception exception) { problems.add(location + " invalid JSON: " + exception.getMessage()); }
            }
        } catch (Exception exception) { problems.add(name + " unreadable: " + exception.getMessage()); }
    }
    private static String id(String id, String previous, String path, List<String> problems) {
        if (!id.matches("(?:blind|pilot)-[0-9]{5}")) problems.add(path + " non-neutral item id " + id);
        if (previous != null && previous.compareTo(id) >= 0) problems.add(path + " item ids out of order");
        return id;
    }
    private static void structural(JsonNode node, Set<String> allowed, String path, Set<String> tokens,
                                   List<String> problems) {
        if (!node.isObject()) { problems.add(path + " expected object"); return; }
        node.fields().forEachRemaining(entry -> {
            String child = path + "." + entry.getKey();
            if (!allowed.contains(entry.getKey())) problems.add(child + " unknown structural key");
            if (timestamp(entry.getKey()) || timestamp(entry.getValue())) problems.add(child + " timestamp");
            checkWords(entry.getKey(), child, tokens, problems);
            scanValues(entry.getValue(), child, tokens, problems);
        });
    }
    private static void nested(JsonNode node, String path, Set<String> allowed, Set<String> tokens,
                               List<String> problems, boolean closed) {
        if (node.isObject()) node.fields().forEachRemaining(entry -> {
            String key = entry.getKey(), child = path + "." + key;
            if (closed && !allowed.contains(key)) problems.add(child + " unknown question key");
            if (metadataName(key) && !allowed.contains(key)) problems.add(child + " unlisted metadata key");
            if (timestamp(entry.getValue())) problems.add(child + " timestamp");
            checkWords(key, child, tokens, problems);
            nested(entry.getValue(), child, allowed, tokens, problems, closed);
        });
        else if (node.isArray()) for (int i = 0; i < node.size(); i++)
            nested(node.get(i), path + "[" + i + "]", allowed, tokens, problems, closed);
        else if (timestamp(node)) problems.add(path + " timestamp");
        else if (node.isTextual()) checkWords(node.asText(), path, tokens, problems);
    }
    private static boolean metadataName(String key) {
        return key.equals(K_ID) || key.endsWith("Id") || key.endsWith("At")
                || key.endsWith("Time") || key.endsWith("Date");
    }
    private static boolean timestamp(JsonNode node) {
        if (node.isIntegralNumber()) return node.asText().replace("-", "").length() >= 12;
        // The real class-B exporter serializes OffsetDateTime as epoch seconds in scientific notation.
        if (node.isFloatingPointNumber()) return Math.abs(node.asDouble()) >= 1_000_000_000d;
        return node.isTextual() && timestamp(node.asText());
    }
    private static boolean timestamp(String text) {
        try { OffsetDateTime.parse(text); return true; } catch (Exception ignored) { /* try date */ }
        try { LocalDate.parse(text); return true; } catch (Exception ignored) { return text.matches("-?\\d{12,}"); }
    }
    private static void scanValues(JsonNode node, String path, Set<String> tokens, List<String> problems) {
        if (node.isTextual()) checkWords(node.asText(), path, tokens, problems);
        else if (node.isArray()) for (int i = 0; i < node.size(); i++)
            scanValues(node.get(i), path + "[" + i + "]", tokens, problems);
        else if (node.isObject()) node.fields().forEachRemaining(entry -> {
            checkWords(entry.getKey(), path + "." + entry.getKey(), tokens, problems);
            scanValues(entry.getValue(), path + "." + entry.getKey(), tokens, problems);
        });
    }
    private static void checkWords(String value, String path, Set<String> tokens, List<String> problems) {
        String lower = value.toLowerCase(Locale.ROOT);
        for (String token : tokens) if (lower.contains(token.toLowerCase(Locale.ROOT)))
            problems.add(path + " forbidden token " + token);
        if (ClassBBlindPacketExport.LEAK.matcher(value).find()) problems.add(path + " LEAK term");
    }
}
