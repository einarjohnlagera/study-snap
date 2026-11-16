package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PilotOutputParser {
    private static final String L_EMPTY_OUTPUT = "Empty output";
    private static final String L_ID = "id";
    private static final String L_NEWLINE = "\n";
    static final class InvalidPilotOutput extends RuntimeException {
        InvalidPilotOutput(String message) { super(message); }
        InvalidPilotOutput(String message, Throwable cause) { super(message, cause); }
    }
    enum Status { PARSED, WHOLE_OUTPUT_UNPARSEABLE }
    enum AttemptKind { SCORING, RETRY, ISOLATION_DRY_RUN }
    record Stray(JsonNode row, String reason) {}
    static final class ParseResult {
        private final Status status;
        private final List<JsonNode> accepted;
        private final List<Stray> stray;
        private final List<String> duplicateIds;
        private final String message;
        private final List<String> acceptedLines;
        private ParseResult(Status status, List<JsonNode> accepted, List<Stray> stray,
                            List<String> duplicateIds, String message, List<String> acceptedLines) {
            this.status = status;
            this.accepted = List.copyOf(accepted);
            this.stray = List.copyOf(stray);
            this.duplicateIds = List.copyOf(duplicateIds);
            this.message = message;
            this.acceptedLines = List.copyOf(acceptedLines);
        }
        Status status() { return status; }
        List<JsonNode> accepted() { return accepted; }
        List<Stray> stray() { return stray; }
        List<String> duplicateIds() { return duplicateIds; }
        String message() { return message; }
        List<String> acceptedLines() { return acceptedLines; }
        boolean retryEligible() { return status == Status.WHOLE_OUTPUT_UNPARSEABLE; }
    }
    private record SourceRow(JsonNode node, String line) {}
    record AttemptResult(AttemptKind attemptKind, ParseResult result) {}
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Pattern FENCE = Pattern.compile("(?m)^```[^\\r\\n]*\\R([\\s\\S]*?)^```[ \\t]*(?:\\R|$)");
    private PilotOutputParser() {}

    static ParseResult parse(String rawOutput, Set<String> expectedItemIds, String role) {
        if (rawOutput == null || expectedItemIds == null || !Set.of("P", "S").contains(role))
            throw new InvalidPilotOutput("Output, expected ids and P/S role required");
        String trimmed = rawOutput.trim();
        Matcher matcher = FENCE.matcher(trimmed);
        List<String> blocks = new ArrayList<>();
        while (matcher.find()) blocks.add(matcher.group(1));
        if (blocks.size() > 1) return unparseable("Multiple fenced blocks");
        String extracted = blocks.isEmpty() ? trimmed : blocks.getFirst();
        if (extracted.isBlank()) return unparseable(L_EMPTY_OUTPUT);
        List<SourceRow> rows = new ArrayList<>();
        try {
            for (String line : extracted.split("\\R")) {
                if (line.isBlank()) continue;
                JsonNode row = JSON.readTree(line);
                if (row == null || !row.isObject()) return unparseable("Non-object JSONL line");
                rows.add(new SourceRow(row, line));
            }
        } catch (Exception exception) { return unparseable("Malformed JSONL line: " + exception.getMessage()); }
        if (rows.isEmpty()) return unparseable(L_EMPTY_OUTPUT);
        Map<String, Integer> counts = new HashMap<>();
        for (SourceRow source : rows) if (source.node().path(L_ID).isTextual())
            counts.merge(source.node().path(L_ID).asText(), 1, Integer::sum);
        List<JsonNode> accepted = new ArrayList<>();
        List<String> acceptedLines = new ArrayList<>();
        List<Stray> stray = new ArrayList<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (SourceRow source : rows) {
            JsonNode row = source.node();
            JsonNode id = row.path(L_ID);
            if (!id.isTextual()) stray.add(new Stray(row, "NO_ID"));
            else if (counts.get(id.asText()) > 1) duplicates.add(id.asText());
            else if (!expectedItemIds.contains(id.asText())) stray.add(new Stray(row, "NOT_IN_PACKET"));
            else { accepted.add(row); acceptedLines.add(source.line()); }
        }
        return new ParseResult(Status.PARSED, List.copyOf(accepted), List.copyOf(stray),
                List.copyOf(duplicates), "Parsed", acceptedLines);
    }
    static void writeScorerFile(Path file, List<AttemptResult> results) {
        if (file == null || results == null) throw new InvalidPilotOutput("File and results required");
        List<String> lines = new ArrayList<>();
        try {
            for (AttemptResult attempt : results) {
                if (attempt == null || attempt.result() == null || attempt.attemptKind() == null
                        || attempt.attemptKind() == AttemptKind.ISOLATION_DRY_RUN)
                    throw new InvalidPilotOutput("Dry run or missing attempt cannot be written");
                if (attempt.result().status() == Status.PARSED)
                    lines.addAll(attempt.result().acceptedLines());
            }
            Files.writeString(file, lines.isEmpty() ? "" : String.join(L_NEWLINE, lines) + L_NEWLINE,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (InvalidPilotOutput exception) { throw exception; }
        catch (Exception exception) { throw new InvalidPilotOutput("Cannot write scorer file", exception); }
    }
    private static ParseResult unparseable(String message) {
        return new ParseResult(Status.WHOLE_OUTPUT_UNPARSEABLE, List.of(), List.of(), List.of(), message, List.of());
    }
}
