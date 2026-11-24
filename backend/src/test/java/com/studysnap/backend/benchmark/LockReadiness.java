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
    private static final String LOCK_SUPPLIERS_REQUIRED = "Lock suppliers required";
    private static final String UNLISTED_DECISION = "Unlisted expansion decision file";
    record ExpansionObligationContext(Path packetDirectory, Path freezeFile, Path manifestFile, Path planFile,
            Path registryFile, Path drawLogFile, Path pScores, Path sScores, Path hScores,
            EvidenceMapping mapping, Set<String> stems, Path parametersFile, Path sealFile) {
        ExpansionObligationContext(Path packetDirectory, Path freezeFile, Path manifestFile, Path planFile,
                Path registryFile, Path drawLogFile, Path pScores, Path sScores, Path hScores,
                EvidenceMapping mapping, Set<String> stems) {
            this(packetDirectory, freezeFile, manifestFile, planFile, registryFile, drawLogFile,
                    pScores, sScores, hScores, mapping, stems, null, null);
        }
    }
    private LockReadiness() {}

    static void check(Map<String, QuestionStatus> statuses, Set<String> reviewRequiredPairs) {
        check(statuses, reviewRequiredPairs, ExpansionDecision.Declared.NONE);
    }

    static void check(Map<String, QuestionStatus> statuses, Set<String> reviewRequiredPairs,
            ExpansionDecision.Declared declared) {
        if (statuses == null || reviewRequiredPairs == null) throw new InvalidEvidence("Lock inputs required");
        if (declared == null) throw new InvalidExpansionDecision("Verified declaration required");
        if (statuses.values().stream().anyMatch(java.util.Objects::isNull))
            throw new InvalidEvidence("Null question status at lock");
        List<String> pending = new ArrayList<>();
        statuses.values().stream().filter(s -> s.state() == QuestionStatus.State.PENDING_REVIEW)
                .map(QuestionStatus::itemId).filter(id -> !declared.itemIds().contains(id))
                .sorted().forEach(pending::add);
        if (!pending.isEmpty()) throw new InvalidEvidence("PENDING_REVIEW questions: " + pending);
        for (String id : declared.itemIds()) {
            QuestionStatus status = statuses.get(id);
            if (status == null || status.state() != QuestionStatus.State.PENDING_REVIEW || status.f() != null)
                throw new InvalidExpansionDecision("Declared item is not pending: " + id);
        }
    }

    static LockRecord.FindingsValidator asValidator(Supplier<Map<String, QuestionStatus>> statuses,
                                                     Supplier<Set<String>> reviewRequiredPairs) {
        if (statuses == null || reviewRequiredPairs == null) throw new InvalidEvidence(LOCK_SUPPLIERS_REQUIRED);
        return ignored -> check(statuses.get(), reviewRequiredPairs.get());
    }

    static LockRecord.FindingsValidator asValidator(Supplier<Map<String, QuestionStatus>> statuses,
            Supplier<Set<String>> reviewRequiredPairs, ExpansionObligationContext context) {
        return obligationValidator(statuses, reviewRequiredPairs, context, null);
    }

    static LockRecord.FindingsValidator asValidator(Supplier<Map<String, QuestionStatus>> statuses,
            Supplier<Set<String>> reviewRequiredPairs, ExpansionObligationContext context,
            ExpansionDecision.Context decisionContext) {
        if (decisionContext == null) throw new InvalidExpansionDecision("Decision context required");
        return obligationValidator(statuses, reviewRequiredPairs, context, decisionContext);
    }

    static LockRecord.FindingsValidator asValidator(Supplier<Map<String, QuestionStatus>> statuses,
            Supplier<Set<String>> reviewRequiredPairs, ExpansionDecision.Context context) {
        if (statuses == null || reviewRequiredPairs == null || context == null
                || context.decisionFile() == null || context.pairs() == null
                || context.sets() == null || context.entries() == null || context.workflowStems() == null)
            throw new InvalidExpansionDecision("Decision validator inputs required");
        return findings -> {
            Map<String, QuestionStatus> supplied = statuses.get();
            ExpansionDecision.Declared declared = ExpansionDecision.Declared.NONE;
            boolean listed = findings.contains(context.decisionFile());
            if (java.nio.file.Files.exists(context.decisionFile()) && !listed)
                throw new InvalidExpansionDecision(UNLISTED_DECISION);
            if (listed)
                declared = ExpansionDecision.verify(context, supplied, context.pairs(), context.sets(),
                        context.entries(), context.workflowStems(), new HashSet<>(findings));
            check(supplied, reviewRequiredPairs.get(), declared);
        };
    }

    private static LockRecord.FindingsValidator obligationValidator(Supplier<Map<String, QuestionStatus>> statuses,
            Supplier<Set<String>> reviewRequiredPairs, ExpansionObligationContext context,
            ExpansionDecision.Context decisionContext) {
        if (context == null) throw new InvalidClassAAssembly("Expansion lock context required");
        if (statuses == null || reviewRequiredPairs == null)
            throw new InvalidEvidence(LOCK_SUPPLIERS_REQUIRED);
        if (decisionContext != null && (decisionContext.decisionFile() == null
                || decisionContext.decisionFile().getParent() == null
                || decisionContext.freezeFile() == null || decisionContext.parametersFile() == null
                || decisionContext.sealFile() == null || decisionContext.drawLogFile() == null
                || decisionContext.registryFile() == null || context.parametersFile() == null
                || context.sealFile() == null))
            throw new InvalidExpansionDecision("Decision path required");
        if (decisionContext != null && (!context.freezeFile().toAbsolutePath().normalize()
                .equals(decisionContext.freezeFile().toAbsolutePath().normalize())
                || !context.parametersFile().toAbsolutePath().normalize()
                    .equals(decisionContext.parametersFile().toAbsolutePath().normalize())
                || !context.sealFile().toAbsolutePath().normalize()
                    .equals(decisionContext.sealFile().toAbsolutePath().normalize())
                || !context.drawLogFile().toAbsolutePath().normalize()
                    .equals(decisionContext.drawLogFile().toAbsolutePath().normalize())
                || !context.registryFile().toAbsolutePath().normalize()
                    .equals(decisionContext.registryFile().toAbsolutePath().normalize())
                || !context.pScores().getParent().toAbsolutePath().normalize()
                    .equals(decisionContext.decisionFile().getParent().toAbsolutePath().normalize())))
            throw new InvalidExpansionDecision("Decision and obligation contexts differ");
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
                Map<String, Set<String>> workflowStems = ClassAEvidenceAssembler.workflowStems(
                        context.manifestFile(), context.stems());
                ExpansionObligation.verify(plan, workflowStems, pairs, sets, actual, entries, registry);
                Map<String, QuestionStatus> supplied = statuses.get();
                if (supplied != null && !supplied.isEmpty() && !supplied.equals(actual))
                    throw new InvalidClassAAssembly("Caller statuses differ from file-derived statuses");
                ExpansionDecision.Declared declared = ExpansionDecision.Declared.NONE;
                boolean listed = decisionContext != null && present.contains(decisionContext.decisionFile());
                if (decisionContext != null && java.nio.file.Files.exists(decisionContext.decisionFile()) && !listed)
                    throw new InvalidExpansionDecision(UNLISTED_DECISION);
                if (listed)
                    declared = ExpansionDecision.verify(decisionContext, actual, pairs, sets, entries,
                            workflowStems, present);
                check(actual, required, declared);
            } catch (IOException exception) {
                throw new InvalidClassAAssembly("Cannot validate expansion before lock", exception);
            }
        };
    }

}
