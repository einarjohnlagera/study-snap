package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class PilotInvocationLog {
    private static final String L_KIND = "kind";
    private static final String L_SEQ = "seq";
    private static final String L_PREVSHA256 = "prevSha256";
    private static final String L_BARSSHA256 = "barsSha256";
    private static final String L_KEYSHA256 = "keySha256";
    private static final String L_STARTEDAT = "startedAt";
    private static final String L_ROLE = "role";
    private static final String L_PACKETID = "packetId";
    private static final String L_ATTEMPTKIND = "attemptKind";
    private static final String L_REQUESTEDMODEL = "requestedModel";
    private static final String L_REPORTEDMODEL = "reportedModel";
    private static final String L_REPORTEDMODELSOURCE = "reportedModelSource";
    private static final String L_RENDEREDPROMPTSHA256 = "renderedPromptSha256";
    private static final String L_INPUTTOKENS = "inputTokens";
    private static final String L_OUTPUTTOKENS = "outputTokens";
    private static final String L_OUTCOME = "outcome";
    private static final String L_LEAKHITS = "leakHits";
    private static final String L_PARSED = "PARSED";
    private static final String L_WHOLE_OUTPUT_UNPARSEABLE = "WHOLE_OUTPUT_UNPARSEABLE";
    private static final String L_REFUSAL = "REFUSAL";
    private static final String L_LIMIT_REACHED = "LIMIT_REACHED";
    private static final String L_SCORING = "SCORING";
    private static final String L_RETRY = "RETRY";
    private static final String L_ISOLATION_DRY_RUN = "ISOLATION_DRY_RUN";
    private static final String L_HEADER = "HEADER";
    private static final String L_INVOCATION = "INVOCATION";
    private static final String L_NEWLINE = "\n";
    private static final String L_BARS_HASH_MISMATCH = "BARS_HASH_MISMATCH";
    private static final String L_P = "P";
    private static final String L_S = "S";
    private static final String L_NONE = "NONE";
    private static final String L_INVALID_LINE = "INVALID_LINE";
    static final class InvalidPilotLog extends RuntimeException {
        InvalidPilotLog(String message) { super(message); }
        InvalidPilotLog(String message, Throwable cause) { super(message, cause); }
    }
    record RolePacket(String role, String packetId) {}
    record Header(String barsSha256, String keySha256, String startedAt) implements Entry {}
    record Invocation(String role, String packetId, String attemptKind, String requestedModel,
                      String reportedModel, String reportedModelSource, String renderedPromptSha256,
                      Long inputTokens, Long outputTokens, String outcome, int leakHits, String startedAt) implements Entry {}
    sealed interface Entry permits Header, Invocation {}
    record LogVerdict(boolean valid, Map<String, String> stoppedByRole, List<String> violations,
                      Map<String, Integer> invocationsByRole, Map<String, Long> cumulativeTokensByRole) {}
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> HEADER_FIELDS = Set.of(L_KIND, L_SEQ, L_PREVSHA256, L_BARSSHA256, L_KEYSHA256, L_STARTEDAT);
    private static final Set<String> INVOCATION_FIELDS = Set.of(L_KIND, L_SEQ, L_PREVSHA256, L_ROLE, L_PACKETID,
            L_ATTEMPTKIND, L_REQUESTEDMODEL, L_REPORTEDMODEL, L_REPORTEDMODELSOURCE, L_RENDEREDPROMPTSHA256,
            L_INPUTTOKENS, L_OUTPUTTOKENS, L_OUTCOME, L_LEAKHITS, L_STARTEDAT);
    private static final Set<String> OUTCOMES = Set.of(L_PARSED, L_WHOLE_OUTPUT_UNPARSEABLE, L_REFUSAL, L_LIMIT_REACHED, "ERROR");
    private static final Set<String> ATTEMPTS = Set.of(L_SCORING, L_RETRY, L_ISOLATION_DRY_RUN);
    private PilotInvocationLog() {}

    static void append(Path log, Entry entry) {
        if (log == null || entry == null) throw new InvalidPilotLog("Log and entry required");
        try {
            List<String> prior = Files.exists(log) ? readLines(log) : List.of();
            List<String> errors = prior.isEmpty() ? List.of() : structural(prior);
            if (!errors.isEmpty()) throw new InvalidPilotLog("Broken log: " + errors);
            if (prior.isEmpty() != (entry instanceof Header)) throw new InvalidPilotLog("Header must be first and unique");
            ObjectNode node = JSON.createObjectNode();
            node.put(L_KIND, entry instanceof Header ? L_HEADER : L_INVOCATION);
            node.put(L_SEQ, prior.size());
            if (prior.isEmpty()) node.putNull(L_PREVSHA256);
            else node.put(L_PREVSHA256, FreezeRecord.sha256(prior.getLast().getBytes(StandardCharsets.UTF_8)));
            if (entry instanceof Header header) {
                node.put(L_BARSSHA256, header.barsSha256()).put(L_KEYSHA256, header.keySha256())
                        .put(L_STARTEDAT, header.startedAt());
            } else if (entry instanceof Invocation item) {
                node.put(L_ROLE, item.role()).put(L_PACKETID, item.packetId()).put(L_ATTEMPTKIND, item.attemptKind())
                        .put(L_REQUESTEDMODEL, item.requestedModel());
                putNullable(node, L_REPORTEDMODEL, item.reportedModel());
                node.put(L_REPORTEDMODELSOURCE, item.reportedModelSource())
                        .put(L_RENDEREDPROMPTSHA256, item.renderedPromptSha256());
                putNullable(node, L_INPUTTOKENS, item.inputTokens());
                putNullable(node, L_OUTPUTTOKENS, item.outputTokens());
                node.put(L_OUTCOME, item.outcome()).put(L_LEAKHITS, item.leakHits()).put(L_STARTEDAT, item.startedAt());
            }
            List<String> candidate = new ArrayList<>(prior);
            candidate.add(JSON.writeValueAsString(node));
            errors = structural(candidate);
            if (!errors.isEmpty()) throw new InvalidPilotLog("Invalid entry: " + errors);
            Files.writeString(log, candidate.getLast() + L_NEWLINE, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (InvalidPilotLog exception) { throw exception; }
        catch (Exception exception) { throw new InvalidPilotLog("Cannot append invocation log", exception); }
    }
    static LogVerdict validate(Path log, PilotBars bars,
                               Map<RolePacket, ScorerPromptRender.PromptRecord> promptRecords, String barsFileSha256) {
        if (bars == null || promptRecords == null || barsFileSha256 == null)
            throw new InvalidPilotLog("Validation inputs required");
        try {
            List<String> lines = readLines(log);
            List<String> violations = new ArrayList<>(structural(lines));
            if (!bars.sha256().equals(barsFileSha256)) violations.add(L_BARS_HASH_MISMATCH);
            Map<String, String> stopped = new LinkedHashMap<>();
            Map<String, Integer> calls = new HashMap<>(Map.of(L_P, 0, L_S, 0));
            Map<String, Long> tokens = new HashMap<>(Map.of(L_P, 0L, L_S, 0L));
            if (!violations.isEmpty()) return verdict(stopped, violations, calls, tokens);
            JsonNode header = JSON.readTree(lines.getFirst());
            if (!barsFileSha256.equals(header.path(L_BARSSHA256).asText())) violations.add(L_BARS_HASH_MISMATCH);
            Map<RolePacket, Integer> scoring = new HashMap<>(), retries = new HashMap<>(), dryRuns = new HashMap<>();
            Map<String, JsonNode> previousByRole = new HashMap<>();
            for (int index = 1; index < lines.size(); index++) {
                JsonNode line = JSON.readTree(lines.get(index));
                String role = line.path(L_ROLE).asText();
                String packet = line.path(L_PACKETID).asText();
                RolePacket pair = new RolePacket(role, packet);
                PilotBars.Limits limit = bars.limits(role);
                if (stopped.containsKey(role)) violations.add("INVOCATION_AFTER_STOP");
                int count = calls.merge(role, 1, Integer::sum);
                if (count > limit.maxInvocations()) violations.add("INVOCATION_CAP_EXCEEDED");
                String kind = line.path(L_ATTEMPTKIND).asText();
                if (L_SCORING.equals(kind) && scoring.merge(pair, 1, Integer::sum) > 1)
                    violations.add("DUPLICATE_SCORING");
                if (L_RETRY.equals(kind)) {
                    JsonNode previous = previousByRole.get(role);
                    if (previous == null
                            || !packet.equals(previous.path(L_PACKETID).asText())
                            || !L_WHOLE_OUTPUT_UNPARSEABLE.equals(previous.path(L_OUTCOME).asText()))
                        violations.add("RETRY_NOT_AFTER_WHOLE_OUTPUT_FAILURE");
                    if (retries.merge(pair, 1, Integer::sum) > limit.maxRetriesPerPacket())
                        violations.add("RETRY_CAP_EXCEEDED");
                }
                if (L_ISOLATION_DRY_RUN.equals(kind)) {
                    if (!packet.equals(limit.isolationDryRunPacketId())) violations.add("DRY_RUN_PACKET_MISMATCH");
                    if (dryRuns.merge(new RolePacket(role, "DRY"), 1, Integer::sum) > limit.isolationDryRuns())
                        violations.add("DRY_RUN_CAP_EXCEEDED");
                }
                if (!limit.model().equals(line.path(L_REQUESTEDMODEL).asText())) violations.add("REQUESTED_MODEL_MISMATCH");
                String reported = line.path(L_REPORTEDMODEL).isTextual() ? line.path(L_REPORTEDMODEL).asText() : null;
                boolean completed = Set.of(L_PARSED, L_WHOLE_OUTPUT_UNPARSEABLE).contains(line.path(L_OUTCOME).asText());
                if (completed && (reported == null || L_NONE.equals(line.path(L_REPORTEDMODELSOURCE).asText())))
                    violations.add("MODEL_UNVERIFIABLE");
                if (reported != null && !reported.equals(limit.model())) stop(stopped, "MODEL_MISMATCH", true, role);
                Long input = number(line.path(L_INPUTTOKENS)), output = number(line.path(L_OUTPUTTOKENS));
                if (completed && (input == null || output == null)) violations.add("TOKEN_STOP_UNVERIFIABLE");
                if (input != null && output != null) {
                    long total = tokens.get(role);
                    try { total = Math.addExact(total, Math.addExact(input, output)); }
                    catch (ArithmeticException exception) { violations.add("TOKEN_OVERFLOW"); }
                    tokens.put(role, total);
                }
                ScorerPromptRender.PromptRecord record = promptRecords.get(pair);
                if (record == null || !line.path(L_RENDEREDPROMPTSHA256).asText().equals(record.renderedSha256())
                        || !role.equals(record.role())) violations.add("PROMPT_HASH_MISMATCH");
                if (line.path(L_LEAKHITS).asInt() > 0) stop(stopped, "LEAK", true, role);
                if (L_REFUSAL.equals(line.path(L_OUTCOME).asText())) stop(stopped, L_REFUSAL, false, role);
                if (L_LIMIT_REACHED.equals(line.path(L_OUTCOME).asText())) stop(stopped, L_LIMIT_REACHED, false, role);
                if (tokens.get(role) > limit.tokenStop()) stop(stopped, "TOKEN_STOP", false, role);
                if (count >= limit.maxInvocations()) stop(stopped, "INVOCATION_CAP", false, role);
                previousByRole.put(role, line);
            }
            return verdict(stopped, violations, calls, tokens);
        } catch (InvalidPilotLog exception) { throw exception; }
        catch (Exception exception) { throw new InvalidPilotLog("Cannot validate invocation log", exception); }
    }
    private static LogVerdict verdict(Map<String, String> stopped, List<String> errors,
                                      Map<String, Integer> calls, Map<String, Long> tokens) {
        return new LogVerdict(errors.isEmpty(), Map.copyOf(stopped), List.copyOf(errors), Map.copyOf(calls), Map.copyOf(tokens));
    }
    private static void stop(Map<String, String> stopped, String reason, boolean both, String role) {
        stopped.putIfAbsent(role, reason);
        if (both) stopped.putIfAbsent(L_P.equals(role) ? L_S : L_P, reason);
    }
    private static Long number(JsonNode node) { return node.isIntegralNumber() && node.canConvertToLong() ? node.longValue() : null; }
    private static void putNullable(ObjectNode node, String name, String value) {
        if (value == null) node.putNull(name); else node.put(name, value);
    }
    private static void putNullable(ObjectNode node, String name, Long value) {
        if (value == null) node.putNull(name); else node.put(name, value);
    }
    private static List<String> readLines(Path log) throws Exception {
        byte[] bytes = Files.readAllBytes(log);
        if (bytes.length == 0) return List.of();
        String content = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        if (!content.endsWith(L_NEWLINE) || content.contains("\r"))
            throw new InvalidPilotLog("Log must be LF-terminated JSONL");
        String[] rows = content.split(L_NEWLINE, -1);
        return List.of(rows).subList(0, rows.length - 1);
    }
    private static List<String> structural(List<String> lines) {
        List<String> errors = new ArrayList<>();
        if (lines.isEmpty()) return List.of("HEADER_MISSING");
        String previous = null;
        for (int index = 0; index < lines.size(); index++) {
            try {
                JsonNode node = JSON.readTree(lines.get(index));
                if (node == null || !node.isObject()) { errors.add(L_INVALID_LINE); continue; }
                String kind = node.path(L_KIND).asText();
                Set<String> expected = index == 0 ? HEADER_FIELDS : INVOCATION_FIELDS;
                Set<String> actual = new HashSet<>();
                node.fieldNames().forEachRemaining(actual::add);
                if (!actual.equals(expected) || !(index == 0 ? L_HEADER : L_INVOCATION).equals(kind)) errors.add("FIELD_SET_OR_KIND");
                if (!node.path(L_SEQ).isInt() || node.path(L_SEQ).asInt() != index) errors.add("SEQ_BROKEN");
                String expectedHash = previous == null ? null : FreezeRecord.sha256(previous.getBytes(StandardCharsets.UTF_8));
                if (previous == null ? !node.path(L_PREVSHA256).isNull()
                        : !Objects.equals(expectedHash, node.path(L_PREVSHA256).asText())) errors.add("CHAIN_BROKEN");
                if (index == 0) {
                    if (!digest(node.path(L_BARSSHA256)) || !digest(node.path(L_KEYSHA256))) errors.add("INVALID_HEADER");
                } else {
                    if (!Set.of(L_P, L_S).contains(node.path(L_ROLE).asText())
                            || !text(node, L_PACKETID) || !ATTEMPTS.contains(node.path(L_ATTEMPTKIND).asText())
                            || !text(node, L_REQUESTEDMODEL) || !digest(node.path(L_RENDEREDPROMPTSHA256))
                            || !OUTCOMES.contains(node.path(L_OUTCOME).asText())
                            || !Set.of("AGENT_RESULT", "CODEX_JSON", L_NONE).contains(node.path(L_REPORTEDMODELSOURCE).asText())
                            || !(node.path(L_REPORTEDMODEL).isNull() || text(node, L_REPORTEDMODEL))
                            || !nullableNonnegative(node.path(L_INPUTTOKENS)) || !nullableNonnegative(node.path(L_OUTPUTTOKENS))
                            || !node.path(L_LEAKHITS).isInt() || node.path(L_LEAKHITS).asInt() < 0)
                        errors.add("INVALID_INVOCATION");
                }
                try { OffsetDateTime.parse(node.path(L_STARTEDAT).asText()); }
                catch (Exception exception) { errors.add("INVALID_TIMESTAMP"); }
            } catch (Exception exception) { errors.add(L_INVALID_LINE); }
            previous = lines.get(index);
        }
        return errors;
    }
    private static boolean digest(JsonNode node) { return node.isTextual() && node.asText().matches("[0-9a-f]{64}"); }
    private static boolean text(JsonNode node, String field) {
        return node.path(field).isTextual() && !node.path(field).asText().isBlank();
    }
    private static boolean nullableNonnegative(JsonNode node) {
        return node.isNull() || node.isIntegralNumber() && node.canConvertToLong() && node.longValue() >= 0;
    }
}
