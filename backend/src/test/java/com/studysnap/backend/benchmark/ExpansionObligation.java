package com.studysnap.backend.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Key-free verification of the frozen workflow expansion. Operators build a WorkflowScope
 * only from positive allocations, in plan priority order, and make no draw call when all
 * allocations are zero. Every draw uses round one and an empty already-reviewed list.
 */
final class ExpansionObligation {
    static final int K_REQ = 3;
    private static final List<String> PLACEMENTS = List.of("A", "B");
    record Disclosure(String workflow, Set<String> triggerPairs, Map<String, Integer> allocationByStem,
                      List<String> drawnPairs, boolean frameExhausted, int requiredPairs) {
        Disclosure {
            triggerPairs = Set.copyOf(triggerPairs);
            allocationByStem = Map.copyOf(allocationByStem);
            drawnPairs = List.copyOf(drawnPairs);
        }
    }
    private ExpansionObligation() {}

    static Map<String, Disclosure> verify(ClassAEvidenceAssembler.SpotPlan plan,
            Map<String, Set<String>> workflowStems, PairMap pairs, ReviewSets sets,
            Map<String, QuestionStatus> statuses, List<DrawLog.Entry> entries, SeedStreamRegistry registry) {
        if (plan == null || workflowStems == null || pairs == null || sets == null
                || statuses == null || entries == null || registry == null)
            throw new InvalidClassAAssembly("Expansion obligation inputs required");
        if (!plan.expansion().keySet().equals(workflowStems.keySet()))
            throw new InvalidClassAAssembly("Expansion plan workflows differ from export");
        Set<String> spotSelections = new HashSet<>();
        for (DrawLog.Entry entry : entries) if (entry.purpose().equals(SeedStreamRegistry.SPOT_CHECK))
            spotSelections.addAll(entry.selectedPairIds());
        Map<String, PairEvidence> evidence = PairEvidence.build(pairs, statuses);
        Map<String, Disclosure> result = new LinkedHashMap<>();
        for (var workflow : workflowStems.entrySet()) {
            String name = workflow.getKey();
            ClassAEvidenceAssembler.ExpansionPlan frozen = plan.expansion().get(name);
            if (frozen == null || !new HashSet<>(frozen.stemPriority()).equals(workflow.getValue())
                    || frozen.stemPriority().size() != workflow.getValue().size()
                    || !frozen.initialAllocation().keySet().equals(workflow.getValue()))
                throw new InvalidClassAAssembly("Expansion plan stems differ from export: " + name);
            Map<String, Integer> capacity = new LinkedHashMap<>();
            for (String stem : frozen.stemPriority()) {
                Set<String> frame = sets.frameByStem().get(stem);
                if (frame == null) throw new InvalidClassAAssembly("Missing expansion frame: " + stem);
                capacity.put(stem, (int) frame.stream().filter(id -> !spotSelections.contains(id)).count());
            }
            Map<String, Integer> allocation;
            try {
                allocation = ExpansionAllocation.allocate(frozen.initialAllocation(), frozen.stemPriority(),
                        capacity, K_REQ);
            } catch (InvalidExpansionAllocation exception) {
                throw new InvalidClassAAssembly("Invalid expansion plan allocation", exception);
            }
            Set<String> initialPairs = new LinkedHashSet<>(sets.mandatory());
            initialPairs.addAll(spotSelections);
            Set<String> triggers = new LinkedHashSet<>();
            for (String pairId : initialPairs) {
                PairEvidence pair = evidence.get(pairId);
                if (pair != null && workflow.getValue().contains(pair.stem())
                        && fullyReviewed(pairId, pairs, statuses)
                        && Expansion.PairReview.from(pair).hasDoubleMiss()) triggers.add(pairId);
            }
            List<DrawLog.Entry> drawn = entries.stream().filter(e -> e.purpose().equals(SeedStreamRegistry.EXPANSION)
                    && workflow.getValue().contains(e.workflow())).toList();
            if (triggers.isEmpty() && !drawn.isEmpty())
                throw new InvalidClassAAssembly("Expansion draw without trigger: " + name);
            if (triggers.size() >= 2 && drawn.stream().anyMatch(e -> e.selectedPairIds().stream()
                    .anyMatch(id -> reviewedAny(id, pairs, statuses))))
                throw new InvalidClassAAssembly("Expansion reviewed after initial second double miss: " + name);
            List<String> expectedOrder = frozen.stemPriority().stream()
                    .filter(stem -> allocation.get(stem) > 0).toList();
            if (triggers.size() == 1 || !drawn.isEmpty()) {
                if (!drawn.stream().map(DrawLog.Entry::workflow).toList().equals(expectedOrder))
                    throw new InvalidClassAAssembly("Expansion entries differ from priority allocation: " + name);
                for (DrawLog.Entry entry : drawn) {
                    String stem = entry.workflow();
                    int expected = allocation.get(stem);
                    if (entry.round() != 1 || entry.count() != expected
                            || entry.selectedPairIds().size() != expected || entry.exhausted())
                        throw new InvalidClassAAssembly("Expansion count or round differs from plan: " + stem);
                    if (!entry.registrySha256().equals(registry.sha256())
                            || !registry.isDeclared(stem, SeedStreamRegistry.EXPANSION, 1))
                        throw new InvalidClassAAssembly("Expansion registry differs: " + stem);
                    List<String> remaining = sets.frameByStem().get(stem).stream()
                            .filter(id -> !spotSelections.contains(id)).toList();
                    DrawLog.Entry replay = replay(registry, pairs, stem, remaining, entry);
                    if (!entry.frameSha256().equals(replay.frameSha256())
                            || entry.frameSize() != replay.frameSize()
                            || !entry.selectedPairIds().equals(replay.selectedPairIds()))
                        throw new InvalidClassAAssembly("Expansion replay differs: " + stem);
                }
            }
            List<String> selected = drawn.stream().flatMap(e -> e.selectedPairIds().stream()).toList();
            int totalCapacity = capacity.values().stream().mapToInt(Integer::intValue).sum();
            int required = Math.min(K_REQ, totalCapacity);
            if (triggers.size() == 1 && selected.size() != required)
                throw new InvalidClassAAssembly("Expansion total differs from required pairs: " + name);
            result.put(name, new Disclosure(name, triggers, allocation, selected,
                    totalCapacity < K_REQ, required));
        }
        for (DrawLog.Entry entry : entries) if (entry.purpose().equals(SeedStreamRegistry.EXPANSION)
                && workflowStems.values().stream().noneMatch(stems -> stems.contains(entry.workflow())))
            throw new InvalidClassAAssembly("Extra expansion entry: " + entry.workflow());
        return Map.copyOf(result);
    }

    private static boolean fullyReviewed(String pairId, PairMap pairs, Map<String, QuestionStatus> statuses) {
        for (String placement : PLACEMENTS) for (String id : pairs.itemsOf(pairId, placement))
            if (statuses.get(id).state() != QuestionStatus.State.FINAL) return false;
        return true;
    }

    private static boolean reviewedAny(String pairId, PairMap pairs, Map<String, QuestionStatus> statuses) {
        for (String placement : PLACEMENTS) for (String id : pairs.itemsOf(pairId, placement))
            if (statuses.get(id).state() == QuestionStatus.State.FINAL) return true;
        return false;
    }

    private static DrawLog.Entry replay(SeedStreamRegistry registry, PairMap pairs, String stem,
                                         List<String> frame, DrawLog.Entry entry) {
        Path file = null;
        try {
            file = Files.createTempFile("expansion-replay-", ".jsonl");
            Files.delete(file);
            return PairSampler.draw(registry, new DrawLog(file), pairs, stem,
                    SeedStreamRegistry.EXPANSION, 1, frame, entry.count(), entry.clockUtc());
        } catch (IOException exception) {
            throw new InvalidClassAAssembly("Cannot replay expansion draw", exception);
        } finally {
            if (file != null) try { Files.deleteIfExists(file); }
            catch (IOException exception) { throw new InvalidClassAAssembly("Cannot remove replay log", exception); }
        }
    }
}
