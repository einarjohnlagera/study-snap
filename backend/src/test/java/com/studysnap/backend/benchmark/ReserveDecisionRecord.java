package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Key-free class-B reserve decision, committed between the primary lock and key opening. */
record ReserveDecisionRecord(int schemaVersion, List<ReserveDecisionRecord.WorkflowDecision> decisions,
        String findingsSha256, String lockSha256, String createdAtUtc) {
    private static final Set<String> FIELDS = Set.of("schemaVersion", "decisions", "findingsSha256",
            "lockSha256", "createdAtUtc");
    private static final Set<String> DECISION_FIELDS = Set.of("workflow", "primaryPairs", "reserveDrawn",
            "primaryUnresolved", "pairDigestSha256");
    private static final String A = "A";
    private static final String B = "B";
    private static final String JSONL = ".jsonl";

    record WorkflowDecision(String workflow, int primaryPairs, boolean reserveDrawn,
            boolean primaryUnresolved, String pairDigestSha256) {}

    static List<ClassBScoreFiles.BlindItem> manifestFromPackets(Path classBPacketDirectory) {
        try {
            PairMap pairs = PairMap.fromPackets(classBPacketDirectory);
            List<ClassBScoreFiles.BlindItem> items = new ArrayList<>();
            try (Stream<Path> paths = Files.list(classBPacketDirectory)) {
                for (Path file : paths.filter(path -> path.getFileName().toString().endsWith(JSONL))
                        .sorted().toList()) {
                    String workflow = file.getFileName().toString().replaceFirst("\\.jsonl$", "");
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    for (int index = 0; index < lines.size(); index++) {
                        JsonNode packet = IntegritySupport.JSON.readTree(lines.get(index));
                        JsonNode fixture = packet == null ? null : packet.get("fixture");
                        if (fixture == null || !fixture.isObject() || !fixture.path("workflow").isTextual()
                                || !fixture.path("reserve").isBoolean()
                                || !workflow.equals(fixture.path("workflow").asText()))
                            throw new InvalidReserveRecord("Invalid blind fixture in " + file);
                        JsonNode outputs = packet.path("outputs");
                        if (!outputs.isArray() || outputs.size() != 2)
                            throw new InvalidReserveRecord("Invalid class-B outputs in " + file);
                        for (JsonNode output : outputs)
                            if (!output.isObject() || !output.path("itemId").isTextual() || output.has("items"))
                                throw new InvalidReserveRecord("Class-B output must carry one blind item id");
                        ClassBCaseManifest.Workflow kind = ClassBCaseManifest.Workflow.valueOf(workflow);
                        String pairId = workflow + "#" + (index + 1);
                        PairMap.Pair pair = pairs.pairs().stream()
                                .filter(value -> value.id().equals(pairId))
                                .findFirst().orElseThrow(() -> new InvalidReserveRecord("Missing packet pair"));
                        for (String placement : List.of(A, B)) {
                            List<String> ids = pair.items().get(placement);
                            if (ids == null || ids.size() != 1 || !ids.getFirst().matches("blind-[0-9]+"))
                                throw new InvalidReserveRecord("Class-B pair needs one item per placement");
                            items.add(new ClassBScoreFiles.BlindItem(ids.getFirst(), kind,
                                    fixture.path("reserve").asBoolean()));
                        }
                    }
                }
            }
            if (!pairs.blindItemIds().equals(items.stream().map(ClassBScoreFiles.BlindItem::itemId)
                    .collect(java.util.stream.Collectors.toSet())))
                throw new InvalidReserveRecord("Packet and manifest item sets differ");
            items.sort(Comparator.comparingInt(item -> Integer.parseInt(item.itemId().substring(6))));
            return List.copyOf(items);
        } catch (InvalidReserveRecord exception) { throw exception; }
        catch (Exception exception) { throw new InvalidReserveRecord("Cannot build blind class-B manifest", exception); }
    }

    static List<WorkflowDecision> compute(Path classBPacketDirectory, ClassBScoreFiles.Validated validated,
            EvidenceMapping mapping, Set<String> classBWorkflowNames) {
        try {
            if (validated == null || mapping == null || classBWorkflowNames == null
                    || classBWorkflowNames.isEmpty() || classBWorkflowNames.stream().anyMatch(java.util.Objects::isNull))
                throw new InvalidReserveRecord("Incomplete reserve decision inputs");
            PairMap pairs = PairMap.fromPackets(classBPacketDirectory);
            List<ClassBScoreFiles.BlindItem> packetManifest = manifestFromPackets(classBPacketDirectory);
            Map<String, ClassBScoreFiles.BlindItem> byId = new HashMap<>();
            for (ClassBScoreFiles.BlindItem item : packetManifest)
                if (byId.putIfAbsent(item.itemId(), item) != null)
                    throw new InvalidReserveRecord("Duplicate blind item");
            if (validated.manifest().size() != packetManifest.size()
                    || !new HashSet<>(validated.manifest()).equals(new HashSet<>(packetManifest)))
                throw new InvalidReserveRecord("Validated manifest differs from blind packets");
            Map<String, ClassBScoreFiles.Score> finalScores = validated.finalHumanScores();
            List<WorkflowDecision> result = new ArrayList<>();
            for (String workflow : classBWorkflowNames.stream().sorted().toList()) {
                if (!workflow.equals(ClassBCaseManifest.Workflow.valueOf(workflow).name()))
                    throw new InvalidReserveRecord("Unknown class-B workflow");
                List<ClassBReserve.PairCritical> criticals = new ArrayList<>();
                List<String> digestLines = new ArrayList<>();
                boolean unresolved = false;
                int count = 0;
                for (PairMap.Pair pair : pairs.pairs().stream().filter(p -> p.workflow().equals(workflow))
                        .sorted(Comparator.comparing(PairMap.Pair::id)).toList()) {
                    String idA = onlyItem(pair, A), idB = onlyItem(pair, B);
                    ClassBScoreFiles.BlindItem itemA = byId.get(idA), itemB = byId.get(idB);
                    if (itemA == null || itemB == null || itemA.workflow() != itemB.workflow()
                            || !workflow.equals(itemA.workflow().name()) || itemA.reserve() != itemB.reserve())
                        throw new InvalidReserveRecord("Mixed or unmapped pair: " + pair.id());
                    if (itemA.reserve()) continue;
                    count++;
                    ClassBScoreFiles.Score scoreA = finalScores.get(idA), scoreB = finalScores.get(idB);
                    int criticalA = scoreA == null ? -1 : mapping.criticalB(scoreA) ? 1 : 0;
                    int criticalB = scoreB == null ? -1 : mapping.criticalB(scoreB) ? 1 : 0;
                    unresolved |= criticalA < 0 || criticalB < 0;
                    digestLines.add(pair.id() + "|" + criticalA + "|" + criticalB);
                    if (criticalA >= 0 && criticalB >= 0)
                        criticals.add(new ClassBReserve.PairCritical(criticalA, criticalB));
                }
                if (count == 0) throw new InvalidReserveRecord("No primary pairs for " + workflow);
                String digest = FreezeRecord.sha256(String.join("\n", digestLines).getBytes(StandardCharsets.UTF_8));
                result.add(new WorkflowDecision(workflow, count,
                        !unresolved && ClassBReserve.decideReserve(criticals), unresolved, digest));
            }
            return List.copyOf(result);
        } catch (InvalidReserveRecord exception) { throw exception; }
        catch (ClassBScoreFiles.InvalidScore exception) { throw exception; }
        catch (Exception exception) { throw new InvalidReserveRecord("Cannot compute class-B reserve", exception); }
    }

    private static String onlyItem(PairMap.Pair pair, String placement) {
        List<String> ids = pair.items().get(placement);
        if (ids == null || ids.size() != 1) throw new InvalidReserveRecord("Invalid class-B placement: " + pair.id());
        return ids.getFirst();
    }

    static ReserveDecisionRecord create(Path recordFile, List<WorkflowDecision> decisions,
            LockRecord primaryLock, Path primaryLockFile, Path classBScoresFile,
            Path classBPacketDirectory, Path classBSealFile, String createdAtUtc) {
        try {
            validateLock(primaryLock, primaryLockFile, classBScoresFile, classBPacketDirectory, classBSealFile);
            Instant created = IntegritySupport.utc(createdAtUtc);
            if (!created.isAfter(IntegritySupport.utc(primaryLock.lockedAtUtc())))
                throw new InvalidReserveRecord("Decision must follow primary lock");
            List<WorkflowDecision> sorted = canonical(decisions);
            Set<String> decisionWorkflows = sorted.stream().map(WorkflowDecision::workflow)
                    .collect(java.util.stream.Collectors.toSet());
            if (!decisionWorkflows.equals(Set.copyOf(primaryLock.workflows())))
                throw new InvalidReserveRecord("Decision workflows differ from primary lock");
            ReserveDecisionRecord record = new ReserveDecisionRecord(1, sorted,
                    FreezeRecord.sha256(classBScoresFile), IntegritySupport.detachedHash(primaryLockFile), createdAtUtc);
            IntegritySupport.create(recordFile, IntegritySupport.JSON.writeValueAsBytes(record));
            return record;
        } catch (InvalidReserveRecord exception) { throw exception; }
        catch (Exception exception) { throw new InvalidReserveRecord("Cannot create reserve decision", exception); }
    }

    static ReserveDecisionRecord load(Path recordFile) {
        try {
            IntegritySupport.detachedHash(recordFile);
            JsonNode root = IntegritySupport.JSON.readTree(Files.readAllBytes(recordFile));
            IntegritySupport.fields(root, FIELDS);
            if (!root.path("schemaVersion").isInt() || root.path("schemaVersion").asInt() != 1
                    || !root.path("decisions").isArray()) throw new InvalidReserveRecord("Invalid record shape");
            List<WorkflowDecision> decisions = new ArrayList<>();
            for (JsonNode row : root.path("decisions")) {
                IntegritySupport.fields(row, DECISION_FIELDS);
                if (!row.path("primaryPairs").isInt() || row.path("primaryPairs").asInt() <= 0
                        || !row.path("reserveDrawn").isBoolean() || !row.path("primaryUnresolved").isBoolean())
                    throw new InvalidReserveRecord("Invalid workflow decision shape");
                decisions.add(new WorkflowDecision(IntegritySupport.string(row, "workflow"),
                        row.path("primaryPairs").asInt(), row.path("reserveDrawn").asBoolean(),
                        row.path("primaryUnresolved").asBoolean(), IntegritySupport.hash(row, "pairDigestSha256")));
            }
            if (!decisions.equals(canonical(decisions))) throw new InvalidReserveRecord("Noncanonical decisions");
            String utc = IntegritySupport.string(root, "createdAtUtc");
            IntegritySupport.utc(utc);
            return new ReserveDecisionRecord(1, List.copyOf(decisions),
                    IntegritySupport.hash(root, "findingsSha256"), IntegritySupport.hash(root, "lockSha256"), utc);
        } catch (InvalidReserveRecord exception) { throw exception; }
        catch (Exception exception) { throw new InvalidReserveRecord("Cannot load reserve decision", exception); }
    }

    static void verify(ReserveDecisionRecord record, Path classBPacketDirectory, Path classBSealFile,
            ClassBScoreFiles.Validated validated, EvidenceMapping mapping, Set<String> classBWorkflowNames,
            Path classBScoresFile, LockRecord primaryLock, Path primaryLockFile) {
        try {
            if (record == null) throw new InvalidReserveRecord("Missing reserve decision record");
            validateLock(primaryLock, primaryLockFile, classBScoresFile, classBPacketDirectory, classBSealFile);
            if (record.schemaVersion() != 1 || !record.decisions().equals(canonical(record.decisions()))
                    || !record.decisions().equals(compute(classBPacketDirectory, validated, mapping, classBWorkflowNames))
                    || !record.findingsSha256().equals(FreezeRecord.sha256(classBScoresFile))
                    || !record.lockSha256().equals(IntegritySupport.detachedHash(primaryLockFile))
                    || !IntegritySupport.utc(record.createdAtUtc()).isAfter(IntegritySupport.utc(primaryLock.lockedAtUtc())))
                throw new InvalidReserveRecord("Reserve decision differs from locked primary evidence");
        } catch (InvalidReserveRecord exception) { throw exception; }
        catch (Exception exception) { throw new InvalidReserveRecord("Cannot verify reserve decision", exception); }
    }

    Map<String, Boolean> reserveDrawnByWorkflow() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (WorkflowDecision decision : canonical(decisions)) result.put(decision.workflow(), decision.reserveDrawn());
        return Map.copyOf(result);
    }

    static boolean decisionFromArmCounts(List<Integer> candidateCriticalPerPair,
            List<Integer> baselineCriticalPerPair) {
        if (candidateCriticalPerPair == null || baselineCriticalPerPair == null
                || candidateCriticalPerPair.size() != baselineCriticalPerPair.size())
            throw new InvalidReserveRecord("Arm count vectors differ");
        try {
            List<ClassBReserve.PairCritical> pairs = new ArrayList<>();
            for (int index = 0; index < candidateCriticalPerPair.size(); index++)
                pairs.add(new ClassBReserve.PairCritical(candidateCriticalPerPair.get(index),
                        baselineCriticalPerPair.get(index)));
            return ClassBReserve.decideReserve(pairs);
        } catch (Exception exception) { throw new InvalidReserveRecord("Invalid arm counts", exception); }
    }

    private static List<WorkflowDecision> canonical(List<WorkflowDecision> values) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(java.util.Objects::isNull))
            throw new InvalidReserveRecord("Missing workflow decisions");
        List<WorkflowDecision> sorted = values.stream().sorted(Comparator.comparing(WorkflowDecision::workflow)).toList();
        Set<String> seen = new HashSet<>();
        for (WorkflowDecision item : sorted)
            if (item.workflow() == null || !seen.add(item.workflow()) || item.primaryPairs() <= 0
                    || !IntegritySupport.digest(item.pairDigestSha256())
                    || item.primaryUnresolved() && item.reserveDrawn())
                throw new InvalidReserveRecord("Invalid workflow decision");
        return List.copyOf(sorted);
    }

    private static void validateLock(LockRecord lock, Path lockFile, Path scoresFile,
            Path packetDirectory, Path sealFile) throws IOException {
        if (lock == null || lock.kind() != LockRecord.Kind.PRIMARY
                || !LockRecord.load(lockFile).equals(lock))
            throw new InvalidReserveRecord("PRIMARY lock required");
        LockRecord.verifyFindingsUnchanged(lock, scoresFile.getParent());
        String scoreHash = FreezeRecord.sha256(scoresFile);
        if (lock.findings().stream().noneMatch(item -> item.name().equals(scoresFile.getFileName().toString())
                && item.sha256().equals(scoreHash)))
            throw new InvalidReserveRecord("Scores file absent from primary lock");
        ArmKeySeal.Seal seal = ArmKeySeal.load(sealFile);
        ArmKeySeal.verifyPackets(packetDirectory, seal);
        List<LockRecord.PacketDigest> packets = seal.packetFiles().stream()
                .map(item -> new LockRecord.PacketDigest(item.name(), item.sha256())).toList();
        if (!packets.equals(lock.packetFiles())
                || !IntegritySupport.detachedHash(sealFile).equals(lock.sealSha256())
                || !seal.armKeySha256().equals(lock.armKeySha256())
                || !Set.copyOf(lock.workflows()).equals(seal.workflowStems()))
            throw new InvalidReserveRecord("Packet seal differs from primary lock");
    }
}
