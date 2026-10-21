package com.studysnap.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.config.OpenAiPromptResources;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The deterministic half of the independent Challenge statement-pair check. */
public final class StatementPairVerification {
    private static final List<String> REASON_ORDER = List.of("UNCERTAIN", "SELF_CONTRADICTORY",
            "UNVERIFIED_EVIDENCE", "CHECKERS_DISAGREE", "MISMATCH", "EXTRACTION_FAILED", "CALL_FAILED");
    private static final Pattern FIRST = Pattern.compile("(?i)statement\\s*(?:1|i)\\s*:");
    private static final Pattern SECOND = Pattern.compile("(?i)statement\\s*(?:2|ii)\\s*:");
    private static final Pattern TRAILING_LITERAL_NEWLINES = Pattern.compile("(?:\\\\n|\\s)+$");
    private static final Pattern CLOSING = Pattern.compile(
            "(?is)(?:^|(?<=[.!?])|(?<=\\n)|(?<=\\\\n))\\s*which\\b[^.!?]*\\?(?:\\\\n|\\s)*$");
    private static final Pattern FRAGMENT_SEPARATOR = Pattern.compile("\\s;\\s");
    private static final Pattern ENCLOSING_QUOTES = Pattern.compile("^[\\s\"'“”‘’]+|[\\s\"'“”‘’]+$");
    private static final Pattern USER_PLACEHOLDER = Pattern.compile("\\{[A-Z_]+\\}");

    private StatementPairVerification() { }

    public record Pair(String statement1, String statement2) { }
    public record Item(String itemId, Pair pair, boolean statement1Correct, boolean statement2Correct) { }
    public record Decision(boolean accepted, String reason) { }
    public record Normalized(String verdict, String reason) { }

    static boolean containsStatementMarker(String question) {
        return question != null && (FIRST.matcher(question).find() || SECOND.matcher(question).find());
    }

    public static Pair extract(String stem) {
        if (stem == null) return null;
        var one = FIRST.matcher(stem);
        var two = SECOND.matcher(stem);
        if (!one.find() || !two.find() || one.start() >= two.start() || one.end() > two.start()) return null;
        String first = trimLiteralNewlines(stem.substring(one.end(), two.start()));
        String tail = stem.substring(two.end());
        var closing = CLOSING.matcher(tail);
        String second = trimLiteralNewlines(closing.find() ? tail.substring(0, closing.start()) : tail);
        return first.isEmpty() || second.isEmpty() ? null : new Pair(first, second);
    }

    private static String trimLiteralNewlines(String text) {
        return TRAILING_LITERAL_NEWLINES.matcher(text.trim()).replaceAll("").trim();
    }

    public static ObjectNode request(ObjectMapper mapper, OpenAiPromptResources prompts, String model,
            String domainContext, String subject, String learnerLevel, String noteText, String summary,
            List<Item> items) {
        ArrayNode itemArray = mapper.createArrayNode();
        for (Item item : items) {
            itemArray.addObject().put("itemId", item.itemId())
                    .put("statement1", item.pair().statement1()).put("statement2", item.pair().statement2());
        }
        String user = renderUserPrompt(prompts.statementPairVerifierUserPrompt(), domainContext, subject,
                learnerLevel, noteText, summary, itemArray.toString());
        ObjectNode body = mapper.createObjectNode().put("model", model);
        ArrayNode input = body.putArray("input");
        message(input, "system", prompts.statementPairVerifierSystemPrompt());
        message(input, "developer", prompts.statementPairVerifierDeveloperPrompt());
        message(input, "user", user);
        ObjectNode format = body.putObject("text").putObject("format");
        format.put("type", "json_schema").put("name", "note_lib_statement_pair_verifier");
        format.set("schema", prompts.statementPairVerifierSchema());
        format.put("strict", true);
        return body;
    }

    private static String provided(String value) {
        return value == null ? "(not provided)" : value;
    }

    public static String renderUserPrompt(String template, String domainContext, String subject,
            String learnerLevel, String noteText, String summary, String itemsJson) {
        Map<String, String> values = Map.of(
                "{DOMAIN_CONTEXT}", provided(domainContext),
                "{SUBJECT}", provided(subject),
                "{LEARNER_LEVEL}", provided(learnerLevel),
                "{NOTE_TEXT}", provided(noteText),
                "{STUDY_PACK_SUMMARY}", provided(summary),
                "{ITEMS_JSON}", itemsJson);
        Matcher matcher = USER_PLACEHOLDER.matcher(template);
        StringBuilder user = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(user, Matcher.quoteReplacement(
                    values.getOrDefault(matcher.group(), matcher.group())));
        }
        matcher.appendTail(user);
        return user.toString();
    }

    private static void message(ArrayNode input, String role, String content) {
        input.addObject().put("role", role).putArray("content").addObject()
                .put("type", "input_text").put("text", content);
    }

    public static JsonNode validate(String text, int count, ObjectMapper mapper) throws Exception {
        JsonNode root = mapper.readTree(text);
        if (!root.isObject() || root.size() != 1 || !root.path("results").isArray()
                || root.path("results").size() != count)
            throw new InvalidVerifierResponseException("Invalid verifier result shape or count");
        Set<String> ids = new HashSet<>();
        for (JsonNode result : root.path("results")) {
            if (!result.isObject() || result.size() != 3 || !result.path("itemId").isTextual()
                    || !ids.add(result.path("itemId").asText()))
                throw new InvalidVerifierResponseException("Duplicate or invalid itemId");
            for (String key : List.of("statement1", "statement2")) validateJudgment(result.path(key));
        }
        for (int i = 1; i <= count; i++) {
            if (!ids.contains(String.valueOf(i))) throw new InvalidVerifierResponseException("Missing or extra itemId");
        }
        return root;
    }

    private static void validateJudgment(JsonNode node) {
        if (!node.isObject() || node.size() != 5
                || !Set.of("SUPPORTED", "CONTRADICTED", "SILENT").contains(node.path("sourceSupport").asText())
                || !node.has("evidenceQuote") || !(node.path("evidenceQuote").isTextual() || node.path("evidenceQuote").isNull())
                || !node.path("reason").isTextual() || !node.path("noteConflictsWithStandardKnowledge").isBoolean()
                || !Set.of("TRUE", "FALSE", "UNCERTAIN").contains(node.path("verdict").asText()))
            throw new InvalidVerifierResponseException("Invalid judgment");
    }

    public static Decision decide(Item item, JsonNode primary, JsonNode secondary, String note) {
        if (item.pair() == null || note == null || note.isBlank()) return new Decision(false, "EXTRACTION_FAILED");
        if (primary == null || secondary == null) return new Decision(false, "CALL_FAILED");
        List<String> reasons = new ArrayList<>();
        String[] first = normalizedPair(primary, note, reasons);
        String[] second = normalizedPair(secondary, note, reasons);
        if (!first[0].equals(second[0]) || !first[1].equals(second[1])) reasons.add("CHECKERS_DISAGREE");
        if (reasons.isEmpty() && (!first[0].equals(item.statement1Correct() ? "TRUE" : "FALSE")
                || !first[1].equals(item.statement2Correct() ? "TRUE" : "FALSE"))) reasons.add("MISMATCH");
        reasons.sort(Comparator.comparingInt(REASON_ORDER::indexOf));
        return reasons.isEmpty() ? new Decision(true, null) : new Decision(false, reasons.getFirst());
    }

    private static String[] normalizedPair(JsonNode result, String note, List<String> reasons) {
        String[] pair = new String[2];
        int index = 0;
        for (String key : List.of("statement1", "statement2")) {
            Normalized normalized = normalize(result.path(key), note);
            pair[index++] = normalized.verdict();
            if (normalized.reason() != null && !reasons.contains(normalized.reason())) reasons.add(normalized.reason());
        }
        return pair;
    }

    public static Normalized normalize(JsonNode judgment, String note) {
        String verdict = judgment.path("verdict").asText();
        String support = judgment.path("sourceSupport").asText();
        if ("UNCERTAIN".equals(verdict) || judgment.path("noteConflictsWithStandardKnowledge").asBoolean())
            return new Normalized("UNCERTAIN", "UNCERTAIN");
        if (("SUPPORTED".equals(support) && "FALSE".equals(verdict))
                || ("CONTRADICTED".equals(support) && "TRUE".equals(verdict)))
            return new Normalized("UNCERTAIN", "SELF_CONTRADICTORY");
        if (!"SILENT".equals(support) && !quoteFound(judgment, note))
            return new Normalized("UNCERTAIN", "UNVERIFIED_EVIDENCE");
        return new Normalized(verdict, null);
    }

    public static boolean quoteFound(JsonNode judgment, String note) {
        JsonNode quote = judgment.path("evidenceQuote");
        if (!quote.isTextual() || note == null) return false;
        String canonicalNote = canonical(note);
        String[] fragments = FRAGMENT_SEPARATOR.split(quote.asText());
        for (String fragment : fragments) {
            String canonicalFragment = canonical(ENCLOSING_QUOTES.matcher(fragment).replaceAll(""));
            if (canonicalFragment.isEmpty() || !canonicalNote.contains(canonicalFragment)) return false;
        }
        return fragments.length > 0;
    }

    private static String canonical(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replace('‘', '\'').replace('’', '\'').replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-').replaceAll("\\s+", " ").trim();
    }

    public static final class InvalidVerifierResponseException extends RuntimeException {
        public InvalidVerifierResponseException(String message) { super(message); }
    }
}
