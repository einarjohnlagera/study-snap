package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** Replays an exact external ledger snapshot against independently approved caps. */
final class CostEvidenceProducer {
    static final String CAPS_DIFFER = "COST_CAPS_DIFFER_FROM_APPROVAL:";
    static final String CAP_AMENDED = "COST_CAP_AMENDED:";
    static final String STOP_RECORDED = "COST_STOP_RECORDED:";
    static final String RESUMED = "COST_RESUMED";
    static final String UNRECONCILED = "COST_UNRECONCILED_RESERVATION";
    static final String SPEND_OVER_CAP = "COST_SPEND_OVER_CAP:";
    static final String IN_FLIGHT = "COST_IN_FLIGHT_AT_END:";
    private static final String TYPE = "type";
    private static final String INIT = "INIT";
    private static final String COPY_FILE = "ledger.jsonl";
    private static final String CAPS = "caps";
    private static final String NEW = "new";
    private static final String REASON = "reason";
    private static final String STAGE = "stage";
    private static final String RESERVATION_ID = "reservationId";
    private static final String PARENT_RESERVATION_ID = "parentReservationId";
    private static final String FORECAST = "forecast";
    private static final String PROVIDER_BOUND_EXCEEDED = "PROVIDER_BOUND_EXCEEDED";
    private static final String UNMETERED = "UNMETERED";
    private static final String OTHER = "OTHER";
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private CostEvidenceProducer() {}

    static void requireExternal(Path file) {
        try {
            if (file == null) throw new InvalidCostEvidence("Missing cost file path");
            Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize()).toRealPath();
            Path path = file.toAbsolutePath().normalize();
            LlmModelBenchmark.Config.rejectInsideRepoForTest(path, repo);
            Path ancestor = path;
            while (!Files.exists(ancestor)) ancestor = ancestor.getParent();
            Path candidate = ancestor.toRealPath().resolve(ancestor.relativize(path));
            if (candidate.startsWith(repo)) throw new InvalidCostEvidence("Cost file inside repository");
        } catch (InvalidCostEvidence exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidCostEvidence("Cannot validate cost file path", exception);
        }
    }

    static CostEvidence produce(Path ledgerFile, ApprovedCaps approved) {
        Path directory = null;
        try {
            requireExternal(ledgerFile);
            if (approved == null) throw new InvalidCostEvidence("Missing approved caps");
            byte[] bytes = Files.readAllBytes(ledgerFile);
            if (bytes.length == 0 || bytes[bytes.length - 1] != '\n')
                throw new InvalidCostEvidence("Corrupt or truncated ledger");
            String digest = FreezeRecord.sha256(bytes);
            directory = Files.createTempDirectory("cost-evidence-");
            Path copy = directory.resolve(COPY_FILE);
            Files.write(copy, bytes);
            Scan scan = scan(copy);
            try (StageCapLedger ledger = StageCapLedger.open(copy, scan.finalCaps, false)) {
                SortedSet<String> flags = new TreeSet<>(scan.flags);
                EnumMap<StageCapLedger.Stage, BigDecimal> spent = new EnumMap<>(StageCapLedger.Stage.class);
                EnumMap<StageCapLedger.Stage, BigDecimal> billed = new EnumMap<>(StageCapLedger.Stage.class);
                Set<StageCapLedger.Stage> stages = new HashSet<>(scan.finalCaps.keySet());
                stages.addAll(approved.caps().keySet());
                for (StageCapLedger.Stage stage : StageCapLedger.Stage.values()) {
                    BigDecimal actual = ledger.spent(stage);
                    BigDecimal charged = ledger.billed(stage);
                    spent.put(stage, actual);
                    billed.put(stage, charged);
                    if (charged.compareTo(actual) > 0)
                        throw new InvalidCostEvidence("Billed exceeds conservative spend: " + stage);
                    if (scan.inFlight(stage).signum() != 0) flags.add(IN_FLIGHT + stage);
                    BigDecimal cap = approved.caps().get(stage);
                    if (cap != null && actual.compareTo(cap) > 0) flags.add(SPEND_OVER_CAP + stage);
                }
                for (StageCapLedger.Stage stage : stages) {
                    BigDecimal finalCap = scan.finalCaps.get(stage);
                    BigDecimal approvedCap = approved.caps().get(stage);
                    if (finalCap == null || approvedCap == null || finalCap.compareTo(approvedCap) != 0)
                        flags.add(CAPS_DIFFER + stage);
                }
                if (ledger.unreconciled()) flags.add(UNRECONCILED);
                return new CostEvidence(flags, scan.finalCaps, spent, billed, ledger.recordCount(), digest);
            }
        } catch (InvalidCostEvidence exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidCostEvidence("Cannot produce ledger-backed cost evidence", exception);
        } finally {
            if (directory != null) {
                try {
                    Files.deleteIfExists(directory.resolve(COPY_FILE));
                    Files.deleteIfExists(directory);
                } catch (Exception exception) {
                    throw new InvalidCostEvidence("Cannot delete cost evidence copy", exception);
                }
            }
        }
    }

    private static Scan scan(Path copy) throws Exception {
        Map<StageCapLedger.Stage, BigDecimal> finalCaps = new EnumMap<>(StageCapLedger.Stage.class);
        Map<String, Pending> pending = new HashMap<>();
        SortedSet<String> flags = new TreeSet<>();
        String[] lines = Files.readString(copy, StandardCharsets.UTF_8).split("\n", -1);
        for (int i = 0; i < lines.length - 1; i++) {
            JsonNode row = JSON.readTree(lines[i]);
            if (row == null || !row.isObject()) throw new InvalidCostEvidence("Malformed ledger record");
            String type = text(row, TYPE);
            if (i == 0 && !INIT.equals(type)) throw new InvalidCostEvidence("Ledger INIT missing");
            if (i > 0 && INIT.equals(type)) throw new InvalidCostEvidence("Duplicate INIT");
            switch (type) {
                case INIT -> finalCaps.putAll(caps(row.get(CAPS)));
                case "AMEND" -> {
                    Map<StageCapLedger.Stage, BigDecimal> next = caps(row.get(NEW));
                    Set<StageCapLedger.Stage> stages = new HashSet<>(finalCaps.keySet());
                    stages.addAll(next.keySet());
                    for (StageCapLedger.Stage stage : stages) {
                        BigDecimal before = finalCaps.get(stage);
                        BigDecimal after = next.get(stage);
                        if (before == null || after == null || before.compareTo(after) != 0)
                            flags.add(CAP_AMENDED + stage);
                    }
                    finalCaps.clear(); finalCaps.putAll(next);
                }
                case "RESERVE" -> pending.put(text(row, RESERVATION_ID),
                        new Pending(StageCapLedger.Stage.valueOf(text(row, STAGE)), money(row, FORECAST)));
                case "TRANSFER" -> {
                    String parentId = text(row, PARENT_RESERVATION_ID);
                    Pending parent = pending.get(parentId);
                    if (parent == null) throw new InvalidCostEvidence("Unknown transferred reservation");
                    BigDecimal transfer = money(row, FORECAST);
                    pending.put(parentId, new Pending(parent.stage, parent.amount.subtract(transfer)));
                    pending.put(text(row, RESERVATION_ID), new Pending(parent.stage, transfer));
                }
                case "SETTLE", "ABANDON", "RECONCILE" -> pending.remove(text(row, RESERVATION_ID));
                case UNMETERED -> {
                    pending.remove(text(row, RESERVATION_ID));
                    flags.add(STOP_RECORDED + UNMETERED);
                }
                case "STOP" -> flags.add(STOP_RECORDED + reason(text(row, REASON)));
                case "RESUME" -> flags.add(RESUMED);
                default -> throw new InvalidCostEvidence("Unknown ledger record type");
            }
        }
        return new Scan(Map.copyOf(finalCaps), Map.copyOf(pending), flags);
    }

    private static String reason(String raw) {
        if (raw.startsWith("UNMETERED response")) return UNMETERED;
        if (PROVIDER_BOUND_EXCEEDED.equals(raw)) return PROVIDER_BOUND_EXCEEDED;
        return OTHER;
    }

    private static String text(JsonNode row, String field) {
        JsonNode value = row.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank())
            throw new InvalidCostEvidence("Missing ledger field " + field);
        return value.asText();
    }

    private static BigDecimal money(JsonNode row, String field) {
        JsonNode value = row.get(field);
        if (value == null || !value.isNumber()) throw new InvalidCostEvidence("Missing ledger amount " + field);
        return value.decimalValue();
    }

    private static Map<StageCapLedger.Stage, BigDecimal> caps(JsonNode node) {
        if (node == null || !node.isObject()) throw new InvalidCostEvidence("Missing ledger caps");
        EnumMap<StageCapLedger.Stage, BigDecimal> result = new EnumMap<>(StageCapLedger.Stage.class);
        node.fields().forEachRemaining(entry -> result.put(StageCapLedger.Stage.valueOf(entry.getKey()),
                entry.getValue().decimalValue()));
        return result;
    }

    private record Pending(StageCapLedger.Stage stage, BigDecimal amount) {}
    private record Scan(Map<StageCapLedger.Stage, BigDecimal> finalCaps,
                        Map<String, Pending> pending, SortedSet<String> flags) {
        BigDecimal inFlight(StageCapLedger.Stage stage) {
            return pending.values().stream().filter(item -> item.stage == stage)
                    .map(Pending::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}
