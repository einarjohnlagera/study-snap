package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LockAnchorsTest {
    private static final String PURPOSE = "anchor test";
    private static final String ONE = "one";
    private static final String RESERVE_NAME = "reserve";
    private static final String SECOND = "second";
    private static final String SECOND_FINDINGS = "second-findings";
    private static final String NOT_ANCHORED = "not in the noted anchors";
    private static final String SEQ_ONE = "seq 1";
    private static final String SEQ_ZERO = "seq 0";
    private static final String SHORTER = "shorter";
    private static final String HISTORY_CHANGED = "history changed";
    private static final String THIRD = "third";
    private static final String THIRD_FINDINGS = "third-findings";
    private static final String ACCESS = "access.jsonl";
    private static final String HUMAN = "human.jsonl";
    private static final String OTHER_HASH = "0".repeat(64);
    private static final Set<LockRecord.Kind> PRIMARY = Set.of(LockRecord.Kind.PRIMARY);
    private static final Set<LockRecord.Kind> BOTH = Set.of(LockRecord.Kind.PRIMARY, LockRecord.Kind.RESERVE);
    @TempDir Path temp;

    // Copies ArmKeyAccessTest.Setup's binding/open convenience and ArmKeyGateFixtures' seal/lock
    // assembly; the real fixture still writes the synthetic export and the first lock.
    private record Setup(Path root, Path file, Path seal, Path finding, Path lock, Path log) {
        ArmKeyAccess.LockBinding binding() { return ArmKeyGateFixtures.binding(lock, seal, finding); }
        LockAnchors.Anchor anchor() { return LockAnchors.anchorOf(lock, seal); }
        LockAnchors.Noted noted() {
            return new LockAnchors.Noted(List.of(anchor()), new LockAnchors.LogHead(0, null));
        }
    }
    private Setup setup(Path root) throws Exception {
        return setup(root, false);
    }
    private Setup setup(Path root, boolean distinctSeal) throws Exception {
        Files.createDirectories(root);
        var export = ArmKeyGateFixtures.classA(root);
        if (distinctSeal) {
            Path packet;
            try (var paths = Files.list(root.resolve("packets"))) { packet = paths.findFirst().orElseThrow(); }
            Files.writeString(packet, "\n", java.nio.file.StandardOpenOption.APPEND);
        }
        Path seal = ArmKeyGateFixtures.seal(root, export.armKey());
        Path finding = ArmKeyGateFixtures.findings(root);
        Path lock = ArmKeyGateFixtures.lock(root, seal, finding, LockRecord.Kind.PRIMARY);
        return new Setup(root, export.armKey(), seal, finding, lock, root.resolve(ACCESS));
    }
    private static void open(Setup setup, List<ArmKeyAccess.LockBinding> bindings,
                             Set<LockRecord.Kind> kinds) {
        ArmKeyAccess.open(setup.file(), setup.seal(), bindings, kinds, setup.log(),
                ArmKeyGateFixtures.OPENED, PURPOSE);
    }
    private static void open(Setup setup) { open(setup, List.of(setup.binding()), PRIMARY); }
    private static LockAnchors.Noted note(Setup setup, LockAnchors.LogHead head) {
        return new LockAnchors.Noted(List.of(setup.anchor()), head);
    }
    private Path secondLock(Setup setup, String name, LockRecord.Kind kind) throws Exception {
        Path directory = Files.createDirectory(setup.root().resolve(name + "-findings"));
        Path finding = directory.resolve(HUMAN);
        Files.writeString(finding, "review complete\n");
        Path lock = setup.root().resolve(name + ".json");
        ArmKeySeal.Seal seal = ArmKeySeal.load(setup.seal());
        LockRecord.create(lock, kind, seal.workflowStems(), List.of(finding), seal, setup.seal(),
                files -> {}, ArmKeyGateFixtures.LOCKED);
        return lock;
    }
    private static void rewriteLock(Path lock) throws Exception {
        ObjectNode row = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(lock));
        row.put("lockedAtUtc", "2026-11-16T00:01:01Z");
        Files.write(lock, IntegritySupport.JSON.writeValueAsBytes(row));
        Files.writeString(IntegritySupport.detached(lock), FreezeRecord.sha256(lock));
    }
    private static void rewriteLog(Path log, String oldHash, String newHash) throws Exception {
        String body = Files.readString(log);
        Files.writeString(log, body.replace(oldHash, newHash));
    }

    @Test void anchorBindsKindLockAndSeal() throws Exception {
        Setup one = setup(temp.resolve(ONE));
        Setup two = setup(temp.resolve("two"), true);
        assertThat(one.anchor()).isEqualTo(new LockAnchors.Anchor(LockRecord.Kind.PRIMARY,
                IntegritySupport.detachedHash(one.seal()), IntegritySupport.detachedHash(one.lock())));
        assertThatThrownBy(() -> LockAnchors.anchorOf(one.lock(), two.seal()))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class).hasMessageContaining("Lock and seal differ");
    }

    @Test void strictCanonicalNoteAndFingerprint() throws Exception {
        Setup one = setup(temp.resolve(ONE));
        Path second = secondLock(one, RESERVE_NAME, LockRecord.Kind.RESERVE);
        var secondAnchor = LockAnchors.anchorOf(second, one.seal());
        var noted = new LockAnchors.Noted(List.of(secondAnchor, one.anchor()), new LockAnchors.LogHead(0, null));
        String text = LockAnchors.render(noted);
        assertThat(LockAnchors.parse(text)).isEqualTo(noted);
        assertThat(text).isEqualTo(LockAnchors.render(new LockAnchors.Noted(List.of(one.anchor(), secondAnchor), noted.head())));
        assertThat(LockAnchors.fingerprint(noted)).isNotEqualTo(LockAnchors.fingerprint(one.noted()));
        assertThat(LockAnchors.fingerprint(noted)).isNotEqualTo(LockAnchors.fingerprint(
                new LockAnchors.Noted(noted.anchors(), new LockAnchors.LogHead(1, OTHER_HASH))));
        var differentAnchor = new LockAnchors.Anchor(one.anchor().kind(), one.anchor().sealSha256(), OTHER_HASH);
        assertThat(LockAnchors.fingerprint(one.noted())).isNotEqualTo(LockAnchors.fingerprint(
                new LockAnchors.Noted(List.of(differentAnchor), one.noted().head())));
        String changed = text.replace("\"entries\":0", "\"entries\":1");
        assertThatThrownBy(() -> LockAnchors.parse(changed)).isInstanceOf(LockAnchors.InvalidLockAnchor.class);
        String unknown = text.replace("\"head\":{", "\"extra\":1,\"head\":{");
        String duplicate = text.replace("\"head\":{", "\"head\":{},\"head\":{");
        String missing = text.replace("\"entries\":0,", "");
        String trailing = text + " {}";
        String noncanonical = text.replace("\"anchors\":[", "\"anchors\": [");
        assertThatThrownBy(() -> LockAnchors.parse(unknown)).isInstanceOf(LockAnchors.InvalidLockAnchor.class);
        assertThatThrownBy(() -> LockAnchors.parse(duplicate)).isInstanceOf(LockAnchors.InvalidLockAnchor.class);
        assertThatThrownBy(() -> LockAnchors.parse(missing)).isInstanceOf(LockAnchors.InvalidLockAnchor.class);
        assertThatThrownBy(() -> LockAnchors.parse(trailing)).isInstanceOf(LockAnchors.InvalidLockAnchor.class);
        assertThatThrownBy(() -> LockAnchors.parse(noncanonical)).isInstanceOf(LockAnchors.InvalidLockAnchor.class);
        String first = IntegritySupport.JSON.writeValueAsString(IntegritySupport.JSON.valueToTree(secondAnchor));
        String last = IntegritySupport.JSON.writeValueAsString(IntegritySupport.JSON.valueToTree(one.anchor()));
        String order = text.indexOf(first) < text.indexOf(last)
                ? text.replace(first + "," + last, last + "," + first)
                : text.replace(last + "," + first, first + "," + last);
        assertThatThrownBy(() -> LockAnchors.parse(order)).isInstanceOf(LockAnchors.InvalidLockAnchor.class);
    }

    @Test void noteRejectsAmbiguityDuplicatesAndInvalidHeads() throws Exception {
        Setup setup = setup(temp.resolve(ONE));
        Path second = secondLock(setup, SECOND, LockRecord.Kind.PRIMARY);
        var other = LockAnchors.anchorOf(second, setup.seal());
        List<LockAnchors.Anchor> ambiguous = List.of(setup.anchor(), other);
        assertThatThrownBy(() -> new LockAnchors.Noted(ambiguous, new LockAnchors.LogHead(0, null)))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class).hasMessageContaining("Ambiguous");
        List<LockAnchors.Anchor> duplicate = List.of(setup.anchor(), setup.anchor());
        assertThatThrownBy(() -> new LockAnchors.Noted(duplicate, new LockAnchors.LogHead(0, null)))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class);
        assertThatThrownBy(() -> new LockAnchors.Noted(List.of(), new LockAnchors.LogHead(0, null)))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class);
        assertThatThrownBy(() -> new LockAnchors.LogHead(0, OTHER_HASH)).isInstanceOf(LockAnchors.InvalidLockAnchor.class);
        assertThatThrownBy(() -> new LockAnchors.LogHead(1, null)).isInstanceOf(LockAnchors.InvalidLockAnchor.class);
        assertThatThrownBy(() -> new LockAnchors.LogHead(-1, null)).isInstanceOf(LockAnchors.InvalidLockAnchor.class);
        assertThat(new LockAnchors.LogHead(1, OTHER_HASH).entries()).isEqualTo(1);
    }

    @Test void secondLockAtNewPathIsVisibleOnlyToAnchorChecks() throws Exception {
        Setup setup = setup(temp.resolve(ONE));
        var noted = setup.noted();
        LockAnchors.requireBeforeOpen(noted, List.of(setup.binding()), setup.log());
        open(setup);
        LockAnchors.verifyAfterOpen(noted, setup.log());
        Path second = secondLock(setup, SECOND, LockRecord.Kind.PRIMARY);
        Path secondFinding = setup.root().resolve(SECOND_FINDINGS).resolve(HUMAN);
        var binding = ArmKeyGateFixtures.binding(second, setup.seal(), secondFinding);
        List<ArmKeyAccess.LockBinding> replacement = List.of(binding);
        assertThatThrownBy(() -> LockAnchors.requireBeforeOpen(noted, replacement, setup.log()))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class).hasMessageContaining(NOT_ANCHORED);
        open(setup, replacement, PRIMARY); // Existing gate still accepts the unanchored second lock.
        assertThat(ArmKeyAccess.validateAccessLog(setup.log())).hasSize(2);
        assertThatThrownBy(() -> LockAnchors.verifyAfterOpen(noted, setup.log()))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class).hasMessageContaining(SEQ_ONE);
        assertThatThrownBy(() -> LockAnchors.requireBeforeOpen(noted, List.of(setup.binding()), setup.log()))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class).hasMessageContaining(SEQ_ONE);
    }

    @Test void rewrittenLockAndSwappedSealRefuseForAnchorReason() throws Exception {
        Setup setup = setup(temp.resolve(ONE));
        var noted = setup.noted();
        rewriteLock(setup.lock());
        assertThat(LockRecord.load(setup.lock()).kind()).isEqualTo(LockRecord.Kind.PRIMARY);
        assertThatThrownBy(() -> LockAnchors.verifyLocksOnDisk(noted, Map.of(setup.lock(), setup.seal())))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class).hasMessageContaining(NOT_ANCHORED);
        assertThatThrownBy(() -> LockAnchors.requireBeforeOpen(noted, List.of(setup.binding()), setup.log()))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class).hasMessageContaining(NOT_ANCHORED);
        Setup other = setup(temp.resolve("other"), true);
        assertThatThrownBy(() -> LockAnchors.verifyLocksOnDisk(noted, Map.of(setup.lock(), other.seal())))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class);
    }

    @Test void logHeadsPermitExtensionsAndMatchRealAppendChain() throws Exception {
        Setup setup = setup(temp.resolve(ONE));
        var zero = setup.noted();
        assertThat(LockAnchors.headOf(setup.log())).isEqualTo(zero.head());
        open(setup);
        var headOne = LockAnchors.headOf(setup.log());
        var notedOne = note(setup, headOne);
        LockAnchors.requireBeforeOpen(notedOne, List.of(setup.binding()), setup.log());
        LockAnchors.verifyAfterOpen(notedOne, setup.log());
        open(setup);
        assertThat(ArmKeyAccess.validateAccessLog(setup.log()).get(1).prevSha256())
                .isEqualTo(headOne.lastLineSha256OrNull());
        LockAnchors.requireBeforeOpen(notedOne, List.of(setup.binding()), setup.log());
        var headTwo = LockAnchors.headOf(setup.log());
        open(setup);
        assertThat(ArmKeyAccess.validateAccessLog(setup.log()).get(2).prevSha256())
                .isEqualTo(headTwo.lastLineSha256OrNull());
        LockAnchors.requireBeforeOpen(notedOne, List.of(setup.binding()), setup.log());
        LockAnchors.verifyAfterOpen(notedOne, setup.log());
        Files.delete(setup.log());
        LockAnchors.requireBeforeOpen(zero, List.of(setup.binding()), setup.log());
        assertThatThrownBy(() -> LockAnchors.requireBeforeOpen(notedOne, List.of(setup.binding()), setup.log()))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class);
        assertThatThrownBy(() -> LockAnchors.verifyAfterOpen(zero, setup.log()))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class);
    }

    @Test void emptyShortRewrittenAndBrokenLogsRefuse() throws Exception {
        Setup setup = setup(temp.resolve(ONE));
        var zero = setup.noted();
        Files.writeString(setup.log(), "");
        assertThatThrownBy(() -> LockAnchors.requireBeforeOpen(zero, List.of(setup.binding()), setup.log()))
                .isInstanceOf(ArmKeyAccess.InvalidAccessLog.class);
        assertThatThrownBy(() -> LockAnchors.verifyAfterOpen(zero, setup.log()))
                .isInstanceOf(ArmKeyAccess.InvalidAccessLog.class);
        Files.delete(setup.log());
        open(setup);
        var one = note(setup, LockAnchors.headOf(setup.log()));
        open(setup);
        var two = note(setup, LockAnchors.headOf(setup.log()));
        String full = Files.readString(setup.log());
        Files.writeString(setup.log(), full.substring(0, full.indexOf('\n') + 1));
        assertThatThrownBy(() -> LockAnchors.requireBeforeOpen(two, List.of(setup.binding()), setup.log()))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class).hasMessageContaining(SHORTER);
        assertThatThrownBy(() -> LockAnchors.verifyAfterOpen(two, setup.log()))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class).hasMessageContaining(SHORTER);
        String changed = Files.readString(setup.log()).replace(PURPOSE, "changed purpose");
        Files.writeString(setup.log(), changed);
        assertThatThrownBy(() -> LockAnchors.requireBeforeOpen(one, List.of(setup.binding()), setup.log()))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class).hasMessageContaining(HISTORY_CHANGED);
        assertThatThrownBy(() -> LockAnchors.verifyAfterOpen(one, setup.log()))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class).hasMessageContaining(HISTORY_CHANGED);
        Files.writeString(setup.log(), full.replaceFirst("\"prevSha256\":\"[0-9a-f]{64}\"",
                "\"prevSha256\":\"" + OTHER_HASH + "\""));
        assertThatThrownBy(() -> LockAnchors.requireBeforeOpen(zero, List.of(setup.binding()), setup.log()))
                .isInstanceOf(ArmKeyAccess.InvalidAccessLog.class);
        assertThatThrownBy(() -> LockAnchors.verifyAfterOpen(zero, setup.log()))
                .isInstanceOf(ArmKeyAccess.InvalidAccessLog.class);
    }

    @Test void everyLockHashInEveryEntryMustBeAnchored() throws Exception {
        Setup setup = setup(temp.resolve(ONE));
        var noted = setup.noted();
        open(setup);
        String original = IntegritySupport.detachedHash(setup.lock());
        rewriteLog(setup.log(), original, OTHER_HASH);
        assertThatThrownBy(() -> LockAnchors.verifyAfterOpen(noted, setup.log()))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class).hasMessageContaining(SEQ_ZERO);
        Files.delete(setup.log());
        open(setup);
        Path second = secondLock(setup, SECOND, LockRecord.Kind.PRIMARY);
        Path finding = setup.root().resolve(SECOND_FINDINGS).resolve(HUMAN);
        open(setup, List.of(ArmKeyGateFixtures.binding(second, setup.seal(), finding)), PRIMARY);
        assertThatThrownBy(() -> LockAnchors.verifyAfterOpen(noted, setup.log()))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class).hasMessageContaining(SEQ_ONE);
    }

    @Test void unanchoredEntryAtNotedHeadIsStillRefused() throws Exception {
        Setup setup = setup(temp.resolve(ONE));
        Path second = secondLock(setup, SECOND, LockRecord.Kind.PRIMARY);
        Path finding = setup.root().resolve(SECOND_FINDINGS).resolve(HUMAN);
        open(setup, List.of(ArmKeyGateFixtures.binding(second, setup.seal(), finding)), PRIMARY);
        var noted = note(setup, LockAnchors.headOf(setup.log()));
        assertThatThrownBy(() -> LockAnchors.verifyAfterOpen(noted, setup.log()))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class).hasMessageContaining(SEQ_ZERO);
        assertThatThrownBy(() -> LockAnchors.requireBeforeOpen(noted, List.of(setup.binding()), setup.log()))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class).hasMessageContaining(SEQ_ZERO);
    }

    @Test void primaryAndReserveLogsUseDifferentAnchoredSets() throws Exception {
        Setup primary = setup(temp.resolve("primary"));
        Setup reserve = setup(temp.resolve(RESERVE_NAME), true);
        Path forPrimary = secondLock(reserve, "for-primary", LockRecord.Kind.RESERVE);
        Path reserveFinding = reserve.root().resolve("for-primary-findings").resolve(HUMAN);
        var forPrimaryBinding = ArmKeyGateFixtures.binding(forPrimary, reserve.seal(), reserveFinding);
        var reserveAnchor = LockAnchors.anchorOf(forPrimary, reserve.seal());
        var both = new LockAnchors.Noted(List.of(primary.anchor(), reserveAnchor), new LockAnchors.LogHead(0, null));
        List<ArmKeyAccess.LockBinding> primaryBindings = List.of(primary.binding(), forPrimaryBinding);
        LockAnchors.requireBeforeOpen(both, primaryBindings, primary.log());
        open(primary, primaryBindings, BOTH);
        LockAnchors.verifyAfterOpen(both, primary.log());
        assertThatThrownBy(() -> LockAnchors.verifyAfterOpen(primary.noted(), primary.log()))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class).hasMessageContaining(SEQ_ZERO);
        LockAnchors.requireBeforeOpen(reserve.noted(), List.of(reserve.binding()), reserve.log());
        open(reserve);
        LockAnchors.verifyAfterOpen(reserve.noted(), reserve.log());
        var wrongReserveNote = new LockAnchors.Noted(List.of(reserveAnchor), new LockAnchors.LogHead(0, null));
        assertThatThrownBy(() -> LockAnchors.verifyAfterOpen(wrongReserveNote, reserve.log()))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class).hasMessageContaining(SEQ_ZERO);
        Path thirdPrimary = secondLock(primary, THIRD, LockRecord.Kind.PRIMARY);
        Path thirdFinding = primary.root().resolve(THIRD_FINDINGS).resolve(HUMAN);
        open(primary, List.of(ArmKeyGateFixtures.binding(thirdPrimary, primary.seal(), thirdFinding),
                forPrimaryBinding), BOTH);
        assertThatThrownBy(() -> LockAnchors.verifyAfterOpen(both, primary.log()))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class).hasMessageContaining(SEQ_ONE);
        Path thirdReserve = secondLock(reserve, THIRD, LockRecord.Kind.PRIMARY);
        Path thirdReserveFinding = reserve.root().resolve(THIRD_FINDINGS).resolve(HUMAN);
        open(reserve, List.of(ArmKeyGateFixtures.binding(thirdReserve, reserve.seal(), thirdReserveFinding)), PRIMARY);
        assertThatThrownBy(() -> LockAnchors.verifyAfterOpen(reserve.noted(), reserve.log()))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class).hasMessageContaining(SEQ_ONE);
    }

    @Test void anchorChecksAreReadOnly() throws Exception {
        Setup setup = setup(temp.resolve(ONE));
        open(setup);
        var noted = note(setup, LockAnchors.headOf(setup.log()));
        List<Path> files;
        try (var paths = Files.list(setup.root())) { files = paths.toList(); }
        byte[] lock = Files.readAllBytes(setup.lock());
        byte[] seal = Files.readAllBytes(setup.seal());
        byte[] log = Files.readAllBytes(setup.log());
        LockAnchors.anchorOf(setup.lock(), setup.seal());
        LockAnchors.headOf(setup.log());
        LockAnchors.parse(LockAnchors.render(noted));
        LockAnchors.fingerprint(noted);
        LockAnchors.requireBeforeOpen(noted, List.of(setup.binding()), setup.log());
        LockAnchors.verifyAfterOpen(noted, setup.log());
        LockAnchors.verifyLocksOnDisk(noted, Map.of(setup.lock(), setup.seal()));
        assertThat(Files.readAllBytes(setup.lock())).isEqualTo(lock);
        assertThat(Files.readAllBytes(setup.seal())).isEqualTo(seal);
        assertThat(Files.readAllBytes(setup.log())).isEqualTo(log);
        try (var paths = Files.list(setup.root())) { assertThat(paths.toList()).containsExactlyInAnyOrderElementsOf(files); }
    }

    // ---- Audit addition (release session): a seal file that is not a valid seal ----

    @Test void aSealFileThatIsNotAValidSealIsRefusedEvenWhenTheLockPointsAtItsHash() throws Exception {
        Setup setup = setup(temp.resolve("badseal"));
        Files.writeString(setup.seal(), "{}");
        Files.writeString(IntegritySupport.detached(setup.seal()), FreezeRecord.sha256(setup.seal()));
        ObjectNode row = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(setup.lock()));
        row.put("sealSha256", IntegritySupport.detachedHash(setup.seal()));
        Files.write(setup.lock(), IntegritySupport.JSON.writeValueAsBytes(row));
        Files.writeString(IntegritySupport.detached(setup.lock()), FreezeRecord.sha256(setup.lock()));
        assertThat(LockRecord.load(setup.lock()).sealSha256()).isEqualTo(IntegritySupport.detachedHash(setup.seal()));
        assertThatThrownBy(() -> LockAnchors.anchorOf(setup.lock(), setup.seal()))
                .isInstanceOf(LockAnchors.InvalidLockAnchor.class).hasMessageContaining("Cannot verify lock and seal");
    }
}
