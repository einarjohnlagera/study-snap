package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A pre-lock finding, authenticated by its listing and hash in the owner-attended lock.
 * The approved larger cap assertion is only as authentic as that lock. */
final class ExpansionDecision {
    static final String FILE_NAME = "expansion-decision.json";
    /** The preregistered cap, preserved by owner ruling R4. */
    static final int PREREGISTERED_EXPANSION_CAP_MINUTES = 120;
    private static final String STOP = "SECOND_DOUBLE_MISS";
    private static final String SCHEMA = "schema";
    private static final String DECISIONS = "decisions";
    private static final String WORKFLOW = "workflow";
    private static final String FREEZE_HASH = "freezeSha256";
    private static final String PARAMETERS_HASH = "parametersSha256";
    private static final String SEAL_HASH = "sealSha256";
    private static final String DRAW_HASH = "drawLogSha256";
    private static final String CAP = "capMinutes";
    private static final String APPROVED = "approvedLargerCap";
    private static final String DECLINED = "declinedOrExhausted";
    private static final String STOP_REASON = "stopReason";
    private static final String UNREVIEWED = "unreviewedPairsByStem";
    private static final String A = "A";
    private static final String B = "B";
    private static final List<String> PLACEMENTS = List.of(A, B);
    private static final Set<String> ROOT_FIELDS = Set.of(SCHEMA, DECISIONS);
    private static final Set<String> ENTRY_FIELDS = Set.of(WORKFLOW, FREEZE_HASH, PARAMETERS_HASH,
            SEAL_HASH, DRAW_HASH, CAP, APPROVED, DECLINED, STOP_REASON, UNREVIEWED);
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    record Context(Path decisionFile, Path freezeFile, Path parametersFile, Path sealFile,
            Path drawLogFile, Path registryFile, PairMap pairs, ReviewSets sets,
            List<DrawLog.Entry> entries, Map<String, Set<String>> workflowStems) {
        Context(Path decisionFile, Path freezeFile, Path parametersFile, Path sealFile,
                Path drawLogFile, Path registryFile) {
            this(decisionFile, freezeFile, parametersFile, sealFile, drawLogFile, registryFile,
                    null, null, null, null);
        }
    }
    record Choice(boolean declinedOrExhausted, boolean approvedLargerCap, int capMinutes,
            String stopReason, Map<String, List<String>> unreviewedPairsByStem) {
        Choice { unreviewedPairsByStem = Map.copyOf(unreviewedPairsByStem); }
    }

    static final class Declared {
        static final Declared NONE = new Declared(Map.of(), Set.of(), null);
        private final Map<String, Choice> byWorkflow;
        private final Set<String> itemIds;
        private final String sha256;
        private Declared(Map<String, Choice> byWorkflow, Set<String> itemIds, String sha256) {
            this.byWorkflow = Map.copyOf(byWorkflow);
            this.itemIds = Set.copyOf(itemIds);
            this.sha256 = sha256;
        }
        Map<String, Choice> byWorkflow() { return byWorkflow; }
        Set<String> itemIds() { return itemIds; }
        String sha256() { return sha256; }
    }

    private ExpansionDecision() {}

    static Declared verify(Context context, Map<String, QuestionStatus> statuses, PairMap pairs,
            ReviewSets sets, List<DrawLog.Entry> entries, Map<String, Set<String>> workflowStems,
            Set<Path> findings) {
        if (context == null || context.decisionFile() == null || statuses == null || pairs == null
                || sets == null || entries == null || workflowStems == null || findings == null)
            throw new InvalidExpansionDecision("Complete decision context and evidence required");
        if (!findings.stream().map(ExpansionDecision::normalized).toList().contains(normalized(context.decisionFile())))
            throw new InvalidExpansionDecision("Decision file is not a lock finding");
        try {
            JsonNode root = JSON.readTree(Files.readAllBytes(context.decisionFile()));
            fields(root, ROOT_FIELDS);
            if (!root.path(SCHEMA).isInt() || root.path(SCHEMA).asInt() != 1
                    || !root.path(DECISIONS).isArray())
                throw new InvalidExpansionDecision("Invalid decision schema");
            Map<String, Choice> choices = new LinkedHashMap<>();
            Set<String> itemIds = new LinkedHashSet<>();
            for (JsonNode row : root.path(DECISIONS)) {
                fields(row, ENTRY_FIELDS);
                String workflow = text(row, WORKFLOW);
                Set<String> stems = workflowStems.get(workflow);
                if (stems == null || stems.isEmpty() || choices.containsKey(workflow))
                    throw new InvalidExpansionDecision("Unknown or duplicate decision workflow: " + workflow);
                bind(row, FREEZE_HASH, context.freezeFile());
                bind(row, PARAMETERS_HASH, context.parametersFile());
                bind(row, SEAL_HASH, context.sealFile());
                bind(row, DRAW_HASH, context.drawLogFile());
                JsonNode cap = row.path(CAP);
                JsonNode approved = row.path(APPROVED);
                JsonNode declined = row.path(DECLINED);
                if (!cap.isInt() || !approved.isBoolean() || !declined.isBoolean()
                        || (approved.asBoolean() ? cap.asInt() <= PREREGISTERED_EXPANSION_CAP_MINUTES
                            : cap.asInt() != PREREGISTERED_EXPANSION_CAP_MINUTES))
                    throw new InvalidExpansionDecision("Invalid expansion cap decision");
                JsonNode reason = row.get(STOP_REASON);
                if (reason == null || !(reason.isNull() || reason.isTextual() && STOP.equals(reason.asText())))
                    throw new InvalidExpansionDecision("Invalid stop reason");
                String stopReason = reason.isNull() ? null : reason.asText();
                JsonNode lists = row.path(UNREVIEWED);
                if (!lists.isObject()) throw new InvalidExpansionDecision("Pair lists required");
                Map<String, List<String>> declared = new LinkedHashMap<>();
                var fields = lists.fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    if (!stems.contains(field.getKey()) || !field.getValue().isArray())
                        throw new InvalidExpansionDecision("Foreign decision stem");
                    List<String> ids = new ArrayList<>();
                    for (JsonNode id : field.getValue()) {
                        if (!id.isTextual() || id.asText().isBlank() || ids.contains(id.asText()))
                            throw new InvalidExpansionDecision("Invalid or duplicate declared pair");
                        ids.add(id.asText());
                    }
                    declared.put(field.getKey(), List.copyOf(ids));
                }
                List<String> drawn = entries.stream().filter(e -> e.purpose().equals(SeedStreamRegistry.EXPANSION)
                        && stems.contains(e.workflow())).flatMap(e -> e.selectedPairIds().stream()).toList();
                if (drawn.isEmpty()) throw new InvalidExpansionDecision("Decision needs a drawn expansion");
                for (String stem : declared.keySet()) if (entries.stream().noneMatch(e ->
                        e.purpose().equals(SeedStreamRegistry.EXPANSION) && stem.equals(e.workflow())))
                    throw new InvalidExpansionDecision("Decision stem has no expansion draw");
                Set<String> spots = new HashSet<>();
                entries.stream().filter(e -> e.purpose().equals(SeedStreamRegistry.SPOT_CHECK))
                        .forEach(e -> spots.addAll(e.selectedPairIds()));
                Set<String> pre = new HashSet<>(sets.mandatory());
                pre.addAll(spots);
                List<String> preMisses = pre.stream().filter(id -> stems.contains(pairs.workflowOf(id)))
                        .filter(id -> complete(id, pairs, statuses) && doubleMiss(id, pairs, statuses)).toList();
                if (preMisses.isEmpty()) throw new InvalidExpansionDecision("Expansion needs a confirmed pre-expansion trigger");
                List<String> listed = new ArrayList<>();
                declared.forEach((stem, ids) -> {
                    for (String id : ids) {
                        if (!stem.equals(pairs.workflowOf(id)) || !drawn.contains(id) || pre.contains(id))
                            throw new InvalidExpansionDecision("Declared pair is not a drawn expansion pair");
                        listed.add(id);
                    }
                });
                if (new HashSet<>(listed).size() != listed.size())
                    throw new InvalidExpansionDecision("Duplicate declared pair");
                List<String> pendingDrawn = drawn.stream().filter(id -> pending(id, pairs, statuses)).toList();
                boolean anyDeclared = !listed.isEmpty();
                if (declined.asBoolean()) {
                    if (stopReason != null || !anyDeclared || preMisses.size() != 1)
                        throw new InvalidExpansionDecision("Invalid declined expansion basis");
                    for (String id : drawn) if (doubleMiss(id, pairs, statuses))
                        throw new InvalidExpansionDecision("Second double miss needs stop basis");
                    for (List<String> ids : declared.values()) if (ids.isEmpty())
                        throw new InvalidExpansionDecision("Empty declined stem");
                    exactPending(drawn, pendingDrawn, listed);
                } else if (stopReason != null) {
                    if (!anyDeclared) throw new InvalidExpansionDecision("Stop needs unfinished expansion pairs");
                    exactPending(drawn, pendingDrawn, listed);
                    if (preMisses.size() == 2) {
                        for (String id : drawn) if (reviewedAny(id, pairs, statuses))
                            throw new InvalidExpansionDecision("Expansion reviewed after pre-expansion second miss");
                    } else if (preMisses.size() == 1) {
                        int second = -1;
                        for (int i = 0; i < drawn.size(); i++) {
                            String id = drawn.get(i);
                            if (doubleMiss(id, pairs, statuses)) {
                                if (!complete(id, pairs, statuses) || second != -1)
                                    throw new InvalidExpansionDecision("Invalid second double-miss pair");
                                second = i;
                            }
                        }
                        if (second < 0) throw new InvalidExpansionDecision("Distinct second double miss required");
                        for (int i = 0; i < drawn.size(); i++) {
                            String id = drawn.get(i);
                            if (i < second && pending(id, pairs, statuses)
                                    || i > second && reviewedAny(id, pairs, statuses))
                                throw new InvalidExpansionDecision("Review order crosses second double miss");
                        }
                    } else throw new InvalidExpansionDecision("Invalid pre-expansion double-miss count");
                } else {
                    if (anyDeclared || !pendingDrawn.isEmpty() || stems.stream().anyMatch(stem ->
                            pairs.pairs().stream().filter(pair -> stem.equals(pair.workflow()))
                                    .anyMatch(pair -> pending(pair.id(), pairs, statuses))))
                        throw new InvalidExpansionDecision("Completion has pending review");
                }
                for (String id : listed) for (String placement : PLACEMENTS)
                    for (String item : pairs.itemsOf(id, placement))
                        if (status(item, statuses).state() == QuestionStatus.State.PENDING_REVIEW) itemIds.add(item);
                choices.put(workflow, new Choice(declined.asBoolean(), approved.asBoolean(), cap.asInt(),
                        stopReason, declared));
            }
            return new Declared(choices, itemIds, FreezeRecord.sha256(context.decisionFile()));
        } catch (InvalidExpansionDecision exception) { throw exception; }
        catch (IOException exception) { throw new InvalidExpansionDecision("Cannot verify expansion decision", exception); }
    }

    private static void exactPending(List<String> drawn, List<String> pending, List<String> listed) {
        if (!new HashSet<>(pending).equals(new HashSet<>(listed)) || pending.isEmpty())
            throw new InvalidExpansionDecision("Declared pairs differ from pending expansion pairs");
        if (!drawn.subList(drawn.size() - listed.size(), drawn.size()).equals(
                drawn.stream().filter(new HashSet<>(listed)::contains).toList()))
            throw new InvalidExpansionDecision("Declared pairs are not a workflow-wide suffix");
    }

    private static boolean pending(String id, PairMap pairs, Map<String, QuestionStatus> statuses) {
        for (String placement : PLACEMENTS) for (String item : pairs.itemsOf(id, placement))
            if (status(item, statuses).state() == QuestionStatus.State.PENDING_REVIEW) return true;
        return false;
    }
    private static boolean complete(String id, PairMap pairs, Map<String, QuestionStatus> statuses) {
        return !pending(id, pairs, statuses);
    }
    private static boolean reviewedAny(String id, PairMap pairs, Map<String, QuestionStatus> statuses) {
        for (String placement : PLACEMENTS) for (String item : pairs.itemsOf(id, placement))
            if (status(item, statuses).state() == QuestionStatus.State.FINAL) return true;
        return false;
    }
    private static boolean doubleMiss(String id, PairMap pairs, Map<String, QuestionStatus> statuses) {
        for (String placement : PLACEMENTS) for (String item : pairs.itemsOf(id, placement))
            if (status(item, statuses).doubleMiss()) return true;
        return false;
    }
    private static QuestionStatus status(String id, Map<String, QuestionStatus> statuses) {
        QuestionStatus value = statuses.get(id);
        if (value == null || !id.equals(value.itemId()))
            throw new InvalidExpansionDecision("Missing decision status: " + id);
        return value;
    }
    private static void bind(JsonNode row, String field, Path file) throws IOException {
        if (file == null || !row.path(field).isTextual()
                || !row.path(field).asText().equals(FreezeRecord.sha256(file)))
            throw new InvalidExpansionDecision("Decision hash differs: " + field);
    }
    private static String text(JsonNode row, String field) {
        JsonNode value = row.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank())
            throw new InvalidExpansionDecision("Invalid decision field: " + field);
        return value.asText();
    }
    private static void fields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject()) throw new InvalidExpansionDecision("Decision object required");
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw new InvalidExpansionDecision("Invalid decision fields");
    }
    private static Path normalized(Path path) { return path.toAbsolutePath().normalize(); }
}

final class InvalidExpansionDecision extends RuntimeException {
    InvalidExpansionDecision(String message) { super(message); }
    InvalidExpansionDecision(String message, Throwable cause) { super(message, cause); }
}
