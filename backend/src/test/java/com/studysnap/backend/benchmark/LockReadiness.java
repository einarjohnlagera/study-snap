package com.studysnap.backend.benchmark;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Required human adjudication must finish before the workflow's final LockRecord is written. */
final class LockReadiness {
    record ExpansionObligationContext(Path packetDirectory, Path freezeFile, Path manifestFile, Path planFile,
            Path registryFile, Path drawLogFile, Path pScores, Path sScores, Path hScores,
            EvidenceMapping mapping, Set<String> stems) {}
    private LockReadiness() {}

    static void check(Map<String, QuestionStatus> statuses, Set<String> reviewRequiredPairs) {
        if (statuses == null || reviewRequiredPairs == null) throw new InvalidEvidence("Lock inputs required");
        List<String> pending = new ArrayList<>();
        statuses.values().stream().filter(s -> s.state() == QuestionStatus.State.PENDING_REVIEW)
                .map(QuestionStatus::itemId).sorted().forEach(pending::add);
        if (!pending.isEmpty()) throw new InvalidEvidence("PENDING_REVIEW questions: " + pending);
    }

    static LockRecord.FindingsValidator asValidator(Supplier<Map<String, QuestionStatus>> statuses,
                                                     Supplier<Set<String>> reviewRequiredPairs) {
        if (statuses == null || reviewRequiredPairs == null) throw new InvalidEvidence("Lock suppliers required");
        return ignored -> check(statuses.get(), reviewRequiredPairs.get());
    }

    static LockRecord.FindingsValidator asValidator(Supplier<Map<String, QuestionStatus>> statuses,
            Supplier<Set<String>> reviewRequiredPairs, ExpansionObligationContext context) {
        if (context == null) throw new InvalidClassAAssembly("Expansion lock context required");
        LockRecord.FindingsValidator existing = asValidator(statuses, reviewRequiredPairs);
        return findings -> {
            try {
                Set<Path> present = new HashSet<>(findings);
                if (!present.containsAll(List.of(context.pScores(), context.sScores(), context.hScores(),
                        context.drawLogFile(), context.registryFile())))
                    throw new InvalidClassAAssembly("Missing expansion finding at lock");
                FreezeRecord freeze = FreezeRecord.load(context.freezeFile());
                if (!FreezeRecord.sha256(context.planFile()).equals(
                        freeze.manifests().get(context.planFile().toAbsolutePath().toString()))
                        || !FreezeRecord.sha256(context.manifestFile()).equals(
                        freeze.manifests().get(context.manifestFile().toAbsolutePath().toString())))
                    throw new InvalidClassAAssembly("Expansion lock plan or manifest is unpinned");
                PairMap pairs = PairMap.fromPackets(context.packetDirectory());
                var scores = ClassAScoreFilesV2.loadBlind(ClassAPacketIndex.load(context.packetDirectory()),
                        context.pScores(), context.sScores(), context.hScores(), context.mapping());
                ReviewSets sets = ReviewSets.compute(pairs, scores, context.mapping());
                var plan = ClassAEvidenceAssembler.spotPlan(context.planFile());
                var registry = SeedStreamRegistry.load(context.registryFile());
                var entries = new DrawLog(context.drawLogFile()).entries();
                ClassAEvidenceAssembler.verifyDraws(entries, plan.stems(), sets, pairs, registry, context.stems());
                Set<String> selected = new HashSet<>();
                entries.forEach(entry -> selected.addAll(entry.selectedPairIds()));
                Set<String> required = ReviewSets.reviewRequiredPairs(sets, selected);
                Map<String, QuestionStatus> actual = new HashMap<>();
                for (var item : scores.values()) actual.put(item.id(), FinalStatus.of(item, context.mapping(),
                        required.contains(pairs.pairOf(item.id()))));
                ExpansionObligation.verify(plan,
                        ClassAEvidenceAssembler.workflowStems(context.manifestFile(), context.stems()),
                        pairs, sets, actual, entries, registry);
                existing.validate(findings);
            } catch (IOException exception) {
                throw new InvalidClassAAssembly("Cannot validate expansion before lock", exception);
            }
        };
    }
}
