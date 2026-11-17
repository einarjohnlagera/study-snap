package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceDisputePostLockTest {
    private static final String PARAMS = "src/test/java/com/studysnap/backend/benchmark/frozen-route-gates-frb.json";
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String CREATED = "2026-11-16T00:04:00Z";
    private static final String RESOLVED = "2026-11-16T00:05:00Z";
    @TempDir Path temp;
    private record Fixture(Path finding, EvidenceDisputeStore store, FindingsLedger ledger,
                           String itemId, String workflow, String lockHash,
                           ConfirmedCriticalSource source, ArmKeyAccess.OpenedKey opened) {
        EvidenceDispute dispute(String id) {
            return new EvidenceDispute(id, CREATED, lockHash, workflow, itemId, null,
                    "reviewer", "I disagree with the critical", List.of(ROUTE));
        }
    }
    private Fixture classA(boolean critical) throws Exception {
        Path root = temp.resolve("a"), locked = Files.createDirectories(root.resolve("locked"));
        var export = ArmKeyGateFixtures.classA(locked);
        String itemId = PairMap.fromPackets(locked.resolve("packets")).blindItemIds().stream().sorted().findFirst().orElseThrow();
        Path seal = ArmKeyGateFixtures.seal(locked, export.armKey());
        Path finding = ArmKeyGateFixtures.findings(locked);
        ObjectNode human = ClassAScoreSchemaV2Test.human(itemId);
        if (critical) {
            ClassAScoreSchemaV2Test.defect(human, "WRONG_KEY", 1);
            human.remove("critical");
            human.put("humanVerdict", "CRITICAL");
        }
        Files.writeString(finding, human + "\n");
        Path lock = ArmKeyGateFixtures.lock(locked, seal, finding, LockRecord.Kind.PRIMARY);
        var opened = ArmKeyAccess.open(export.armKey(), seal,
                List.of(ArmKeyGateFixtures.binding(lock, seal, finding)), Set.of(LockRecord.Kind.PRIMARY),
                locked.resolve("access.jsonl"), ArmKeyGateFixtures.OPENED, "dispute test");
        return setup(root, locked, lock, seal, finding, itemId, "NOTE_ADAPTIVE",
                new ConfirmedCriticalSource.ClassA(ClassAScoreSchemaV2Test.MAPPING), opened);
    }
    private Fixture classB(boolean critical, boolean disagree) throws Exception {
        Path root = temp.resolve("b"), locked = Files.createDirectories(root.resolve("locked"));
        var export = BlindReviewFixtures.classB(locked, 1);
        String itemId = export.armKey().stream().filter(row -> row.arm() == ClassBScoreAggregator.Arm.CANDIDATE)
                .findFirst().orElseThrow().itemId();
        Path seal = ArmKeyGateFixtures.seal(locked, locked.resolve("arm-key.jsonl"));
        Path findings = Files.createDirectories(locked.resolve("findings"));
        Path scores = findings.resolve("scores.jsonl");
        List<String> rows = new ArrayList<>();
        for (ClassBScoreAggregator.Key key : export.armKey()) {
            boolean isCritical = critical && key.itemId().equals(itemId);
            rows.add(score(key.itemId(), isCritical, "HUMAN_ONE", 1, false).toString());
            if (disagree && isCritical) rows.add(score(key.itemId(), false, "HUMAN_TWO", 1, false).toString());
        }
        Files.writeString(scores, String.join("\n", rows) + "\n");
        Path lock = ArmKeyGateFixtures.lock(locked, seal, scores, LockRecord.Kind.PRIMARY);
        var opened = ArmKeyAccess.open(locked.resolve("arm-key.jsonl"), seal,
                List.of(ArmKeyGateFixtures.binding(lock, seal, scores)), Set.of(LockRecord.Kind.PRIMARY),
                locked.resolve("access.jsonl"), ArmKeyGateFixtures.OPENED, "dispute test");
        return setup(root, locked, lock, seal, scores, itemId, "STUDY_TIP",
                new ConfirmedCriticalSource.ClassB(Map.of("HUMAN_ONE", ClassBScoreFiles.ScorerKind.HUMAN,
                        "HUMAN_TWO", ClassBScoreFiles.ScorerKind.HUMAN)), opened);
    }
    private static ObjectNode score(String id, boolean critical, String scorer, int round, boolean adjudication) {
        ObjectNode row = IntegritySupport.JSON.createObjectNode();
        row.put("itemId", id).put("workflow", "STUDY_TIP").put("round", round)
                .put("scorerId", scorer).put("scorerKind", "HUMAN").put("taskFulfilled", true)
                .put("grounded", true).put("actionable", true).put("noCriticalMisleading", !critical)
                .put("critical", critical).put("borderline", false).put("adjudication", adjudication);
        if (critical) row.put("reasonCode", "CRITICAL_MISLEADING"); else row.putNull("reasonCode");
        return row;
    }
    private Fixture setup(Path root, Path locked, Path lock, Path seal, Path finding, String item,
                          String workflow, ConfirmedCriticalSource source,
                          ArmKeyAccess.OpenedKey opened) throws Exception {
        Path params = root.resolve("parameters.json");
        Files.copy(Path.of(PARAMS), params);
        Files.copy(Path.of(PARAMS + ".sha256"), IntegritySupport.detached(params));
        var context = new PostLockContext(lock, finding.getParent(), locked.resolve("packets"), seal, params);
        EvidenceDisputeStore store = EvidenceDisputeStore.init(root.resolve("disputes"), context,
                "2026-11-16T00:03:00Z");
        SafetyFindingStore.init(root.resolve("safety"), context, "2026-11-16T00:03:00Z");
        return new Fixture(finding, store,
                new FindingsLedger(root.resolve("safety"), root.resolve("disputes"), context),
                item, workflow, IntegritySupport.detachedHash(lock), source, opened);
    }
    @Test void classACriticalDisputeBlocksUntilUpheldAndPreservesLockedHuman() throws Exception {
        Fixture f = classA(true);
        String hash = FreezeRecord.sha256(f.finding());
        f.store().create(f.dispute("d_1"), f.source(), f.opened());
        assertThat(f.ledger().blocksBenchmarkPass(ROUTE)).isTrue();
        f.store().resolve(new EvidenceDispute.Resolution("d_1", f.store().disputeSha256("d_1"), RESOLVED,
                EvidenceDispute.Outcome.UPHELD_LOCKED_EVIDENCE, "The lock stands", "owner"));
        assertThat(f.ledger().blocksBenchmarkPass(ROUTE)).isFalse();
        assertThat(FreezeRecord.sha256(f.finding())).isEqualTo(hash);
    }
    @Test void acceptedForOwnerDecisionRemainsBlocking() throws Exception {
        Fixture f = classB(true, false);
        String hash = FreezeRecord.sha256(f.finding());
        f.store().create(f.dispute("d_1"), f.source(), f.opened());
        f.store().resolve(new EvidenceDispute.Resolution("d_1", f.store().disputeSha256("d_1"), RESOLVED,
                EvidenceDispute.Outcome.DISPUTE_ACCEPTED_FOR_OWNER_DECISION, "Needs owner", "reviewer"));
        assertThat(f.ledger().blocksBenchmarkPass(ROUTE)).isTrue();
        assertThat(f.ledger().blocksProductionSwitch(ROUTE)).isTrue();
        assertThat(FreezeRecord.sha256(f.finding())).isEqualTo(hash);
    }
    @Test void classBNoncriticalAndDisagreedWithoutAdjudicationAreRefused() throws Exception {
        Fixture noncritical = classB(false, false);
        EvidenceDispute d = noncritical.dispute("d_1");
        assertThatThrownBy(() -> noncritical.store().create(d, noncritical.source(), null))
                .isInstanceOf(EvidenceDisputeStore.InvalidStore.class);
        Path original = temp;
        temp = Files.createDirectory(original.resolve("second"));
        Fixture disagreed = classB(true, true);
        EvidenceDispute unresolved = disagreed.dispute("d_1");
        assertThatThrownBy(() -> disagreed.store().create(unresolved, disagreed.source(), null))
                .isInstanceOf(EvidenceDisputeStore.InvalidStore.class);
    }
    @Test void classAHumanCleanIsNotConfirmed() throws Exception {
        Fixture f = classA(false);
        EvidenceDispute d = f.dispute("d_1");
        assertThatThrownBy(() -> f.store().create(d, f.source(), null))
                .isInstanceOf(EvidenceDisputeStore.InvalidStore.class);
    }
    @Test void disputeResolutionIsHashLinkedOrderedAndSingleUse() throws Exception {
        Fixture f = classB(true, false);
        f.store().create(f.dispute("d_1"), f.source(), null);
        var wrongHash = new EvidenceDispute.Resolution("d_1", "0".repeat(64), RESOLVED,
                EvidenceDispute.Outcome.UPHELD_LOCKED_EVIDENCE, "written", "owner");
        assertThatThrownBy(() -> f.store().resolve(wrongHash)).isInstanceOf(EvidenceDisputeStore.InvalidStore.class);
        var early = new EvidenceDispute.Resolution("d_1", f.store().disputeSha256("d_1"), CREATED,
                EvidenceDispute.Outcome.UPHELD_LOCKED_EVIDENCE, "written", "owner");
        assertThatThrownBy(() -> f.store().resolve(early)).isInstanceOf(EvidenceDisputeStore.InvalidStore.class);
        var blank = new EvidenceDispute.Resolution("d_1", f.store().disputeSha256("d_1"), RESOLVED,
                EvidenceDispute.Outcome.UPHELD_LOCKED_EVIDENCE, " ", "owner");
        assertThatThrownBy(() -> f.store().resolve(blank)).isInstanceOf(EvidenceDisputeStore.InvalidStore.class);
        var valid = new EvidenceDispute.Resolution("d_1", f.store().disputeSha256("d_1"), RESOLVED,
                EvidenceDispute.Outcome.UPHELD_LOCKED_EVIDENCE, "written", "owner");
        f.store().resolve(valid);
        assertThatThrownBy(() -> f.store().resolve(valid)).isInstanceOf(EvidenceDisputeStore.InvalidStore.class);
    }
    @Test void unknownItemWrongLockAndUnderdeclaredRoutesRefuseDispute() throws Exception {
        Fixture f = classB(true, false);
        EvidenceDispute good = f.dispute("d_1");
        EvidenceDispute unknown = new EvidenceDispute(good.disputeId(), CREATED, good.lockSha256(),
                good.workflow(), "blind-99999", null, good.disputedBy(), good.reason(), good.affectedRoutes());
        assertThatThrownBy(() -> f.store().create(unknown, f.source(), null)).isInstanceOf(EvidenceDisputeStore.InvalidStore.class);
        EvidenceDispute wrongLock = new EvidenceDispute(good.disputeId(), CREATED, "0".repeat(64),
                good.workflow(), good.itemId(), null, good.disputedBy(), good.reason(), good.affectedRoutes());
        assertThatThrownBy(() -> f.store().create(wrongLock, f.source(), null)).isInstanceOf(EvidenceDisputeStore.InvalidStore.class);
        EvidenceDispute wrongRoute = new EvidenceDispute(good.disputeId(), CREATED, good.lockSha256(),
                good.workflow(), good.itemId(), null, good.disputedBy(), good.reason(), List.of("CRITIQUE"));
        assertThatThrownBy(() -> f.store().create(wrongRoute, f.source(), null)).isInstanceOf(EvidenceDisputeStore.InvalidStore.class);
    }
}
