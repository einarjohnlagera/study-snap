package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArmKeyAccessTest {
    private static final Set<LockRecord.Kind> PRIMARY = Set.of(LockRecord.Kind.PRIMARY);
    private static final Set<LockRecord.Kind> BOTH = Set.of(LockRecord.Kind.PRIMARY, LockRecord.Kind.RESERVE);
    private static final String PURPOSE = "post-lock attribution";
    @TempDir Path temp;
    private record Setup(Path key, Path seal, Path finding, Path lock, Path log) {
        List<ArmKeyAccess.LockBinding> bindings() {
            return List.of(ArmKeyGateFixtures.binding(lock, seal, finding));
        }
    }
    private Setup invalidKey(Path root) throws Exception {
        Files.createDirectories(root);
        Path packets = Files.createDirectory(root.resolve("packets"));
        Files.writeString(packets.resolve("ONE.jsonl"), "{}\n");
        Path key = root.resolve("key.jsonl");
        Files.writeString(key, "NOT JSON\n");
        Path seal = ArmKeyGateFixtures.seal(root, key);
        Path finding = ArmKeyGateFixtures.findings(root);
        Path lock = ArmKeyGateFixtures.lock(root, seal, finding, LockRecord.Kind.PRIMARY);
        return new Setup(key, seal, finding, lock, root.resolve("access.jsonl"));
    }
    private ArmKeyAccess.OpenedKey open(Setup setup) {
        return openWith(setup, setup.bindings(), PRIMARY);
    }
    private ArmKeyAccess.OpenedKey openWith(Setup setup, List<ArmKeyAccess.LockBinding> bindings,
                                            Set<LockRecord.Kind> kinds) {
        return ArmKeyAccess.open(setup.key(), setup.seal(), bindings, kinds,
                setup.log(), ArmKeyGateFixtures.OPENED, PURPOSE);
    }
    @Test void realClassAKeyNormalizesAndLogsBeforeReturning() throws Exception {
        var export = ArmKeyGateFixtures.classA(temp);
        Path seal = ArmKeyGateFixtures.seal(temp, export.armKey());
        Path finding = ArmKeyGateFixtures.findings(temp);
        Path lock = ArmKeyGateFixtures.lock(temp, seal, finding, LockRecord.Kind.PRIMARY);
        Setup setup = new Setup(export.armKey(), seal, finding, lock, temp.resolve("access.jsonl"));
        var opened = open(setup);
        assertThat(opened.rows()).hasSize(2);
        assertThat(opened.rows()).extracting(ArmKeyAccess.KeyRow::arm).containsExactlyInAnyOrder("BASELINE", "CANDIDATE");
        assertThat(opened.rows()).allSatisfy(row -> {
            assertThat(row.workflow()).isEqualTo("NOTE_ADAPTIVE");
            assertThat(row.reserve()).isNull();
        });
        assertThat(opened.armOf(opened.rows().getFirst().itemId())).isEqualTo(opened.rows().getFirst().arm());
        assertThat(opened.workflowOf(opened.rows().getFirst().itemId())).isEqualTo("NOTE_ADAPTIVE");
        assertThatThrownBy(() -> opened.armOf("missing-id")).isInstanceOf(ArmKeyAccess.InvalidKeyShape.class);
        assertThat(ArmKeyAccess.validateAccessLog(setup.log())).hasSize(1);
        open(setup);
        assertThat(ArmKeyAccess.validateAccessLog(setup.log())).hasSize(2);
    }
    @Test void realClassBKeyNormalizesWithEmptyPacketFilesExcluded() throws Exception {
        var export = BlindReviewFixtures.classB(temp, 1);
        Path key = temp.resolve("arm-key.jsonl"), seal = ArmKeyGateFixtures.seal(temp, key);
        Path finding = ArmKeyGateFixtures.findings(temp);
        Path lock = ArmKeyGateFixtures.lock(temp, seal, finding, LockRecord.Kind.PRIMARY);
        var opened = open(new Setup(key, seal, finding, lock, temp.resolve("access.jsonl")));
        assertThat(opened.rows()).hasSize(export.armKey().size());
        assertThat(opened.rows()).allSatisfy(row -> {
            assertThat(row.workflow()).isEqualTo("STUDY_TIP");
            assertThat(row.reserve()).isFalse();
            assertThat(row.caseId()).isEqualTo("case-0");
        });
    }
    @Test void noLockOrMissingPrimaryRefusesBeforeParsing() throws Exception {
        Setup setup = invalidKey(temp.resolve("case"));
        List<ArmKeyAccess.LockBinding> noLocks = List.of();
        assertThatThrownBy(() -> openWith(setup, noLocks, PRIMARY)).isInstanceOf(ArmKeyAccess.KeyLocked.class);
        assertThat(Files.exists(setup.log())).isFalse();
    }
    @Test void partialPrimaryCoverageRefusesBeforeParsing() throws Exception {
        Setup setup = invalidKey(temp.resolve("case"));
        Path second = temp.resolve("case/packets/TWO.jsonl");
        Files.writeString(second, "{}\n");
        // A new seal covers both stems; its one-stem lock cannot open the whole key.
        Path newSeal = temp.resolve("case/seal2.json");
        var seal = ArmKeySeal.seal(setup.key(), second.getParent(), newSeal, ArmKeyGateFixtures.SEALED);
        Path partial = temp.resolve("case/partial.json");
        LockRecord.create(partial, LockRecord.Kind.PRIMARY, Set.of("ONE"), List.of(setup.finding()),
                seal, newSeal, files -> {}, ArmKeyGateFixtures.LOCKED);
        Setup partialSetup = new Setup(setup.key(), newSeal, setup.finding(), partial, setup.log());
        assertThatThrownBy(() -> open(partialSetup)).isInstanceOf(ArmKeyAccess.KeyLocked.class);
    }
    @Test void wrongKindCannotSatisfyPrimary() throws Exception {
        Setup setup = invalidKey(temp.resolve("case"));
        Path reserve = ArmKeyGateFixtures.lock(temp.resolve("case"), setup.seal(), setup.finding(),
                LockRecord.Kind.RESERVE);
        var binding = ArmKeyGateFixtures.binding(reserve, setup.seal(), setup.finding());
        List<ArmKeyAccess.LockBinding> locks = List.of(binding);
        assertThatThrownBy(() -> openWith(setup, locks, PRIMARY)).isInstanceOf(ArmKeyAccess.KeyLocked.class);
    }
    @Test void atOrAfterOpenTimeLockRefusesBeforeParsing() throws Exception {
        Setup setup = invalidKey(temp.resolve("case"));
        var seal = ArmKeySeal.load(setup.seal());
        Path at = temp.resolve("case/at.json"), after = temp.resolve("case/after.json");
        LockRecord.create(at, LockRecord.Kind.PRIMARY, seal.workflowStems(), List.of(setup.finding()),
                seal, setup.seal(), files -> {}, ArmKeyGateFixtures.OPENED);
        LockRecord.create(after, LockRecord.Kind.PRIMARY, seal.workflowStems(), List.of(setup.finding()),
                seal, setup.seal(), files -> {}, "2026-11-16T00:03:00Z");
        var atBinding = ArmKeyGateFixtures.binding(at, setup.seal(), setup.finding());
        var afterBinding = ArmKeyGateFixtures.binding(after, setup.seal(), setup.finding());
        List<ArmKeyAccess.LockBinding> atLocks = List.of(atBinding), afterLocks = List.of(afterBinding);
        assertThatThrownBy(() -> openWith(setup, atLocks, PRIMARY)).isInstanceOf(ArmKeyAccess.KeyLocked.class);
        assertThatThrownBy(() -> openWith(setup, afterLocks, PRIMARY)).isInstanceOf(ArmKeyAccess.KeyLocked.class);
    }
    @Test void editedOrExtraFindingsRefuseBeforeParsing() throws Exception {
        Setup setup = invalidKey(temp.resolve("case"));
        Files.writeString(setup.finding(), "changed\n");
        assertThatThrownBy(() -> open(setup)).isInstanceOf(LockRecord.LockBroken.class);
        Files.writeString(setup.finding(), "");
        Files.writeString(setup.finding().resolveSibling("extra.jsonl"), "{}\n");
        assertThatThrownBy(() -> open(setup)).isInstanceOf(LockRecord.LockBroken.class);
    }
    @Test void editedKeyRefusesAtSealBeforeParsing() throws Exception {
        Setup setup = invalidKey(temp.resolve("case"));
        Files.writeString(setup.key(), "changed\n");
        assertThatThrownBy(() -> open(setup)).isInstanceOf(ArmKeySeal.SealBroken.class);
    }
    @Test void differentSealAndTamperedLockRefuseBeforeParsing() throws Exception {
        Setup setup = invalidKey(temp.resolve("case"));
        Path otherSeal = temp.resolve("case/other-seal.json");
        ArmKeySeal.seal(setup.key(), temp.resolve("case/packets"), otherSeal, "2026-11-16T00:00:01Z");
        var wrong = ArmKeyGateFixtures.binding(setup.lock(), otherSeal, setup.finding());
        List<ArmKeyAccess.LockBinding> wrongLocks = List.of(wrong);
        assertThatThrownBy(() -> openWith(setup, wrongLocks, PRIMARY)).isInstanceOf(LockRecord.LockBroken.class);
        Files.writeString(IntegritySupport.detached(setup.lock()), "0".repeat(64));
        assertThatThrownBy(() -> open(setup)).isInstanceOf(LockRecord.LockBroken.class);
    }
    @Test void lockHashFieldsMustMatchOwnSealEvenWithValidDetachedHash() throws Exception {
        for (String field : List.of("armKeySha256", "sealSha256", "packetFiles")) {
            Setup setup = invalidKey(Files.createTempDirectory(temp, "digest-"));
            ObjectNode node = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(setup.lock()));
            if (field.equals("packetFiles"))
                ((ObjectNode) node.path("packetFiles").get(0)).put("sha256", "0".repeat(64));
            else node.put(field, "0".repeat(64));
            Files.write(setup.lock(), IntegritySupport.JSON.writeValueAsBytes(node));
            Files.writeString(IntegritySupport.detached(setup.lock()), FreezeRecord.sha256(setup.lock()));
            assertThatThrownBy(() -> open(setup)).isInstanceOf(LockRecord.LockBroken.class);
        }
    }
    @Test void forgedWorkflowOutsideBoundSealRefusesBeforeParsing() throws Exception {
        Setup setup = invalidKey(temp.resolve("case"));
        ObjectNode node = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(setup.lock()));
        ((com.fasterxml.jackson.databind.node.ArrayNode) node.path("workflows")).set(0,
                IntegritySupport.JSON.getNodeFactory().textNode("OTHER"));
        Files.write(setup.lock(), IntegritySupport.JSON.writeValueAsBytes(node));
        Files.writeString(IntegritySupport.detached(setup.lock()), FreezeRecord.sha256(setup.lock()));
        assertThatThrownBy(() -> open(setup)).isInstanceOf(LockRecord.LockBroken.class);
    }
    @Test void accessLogFailureRefusesBeforeParsing() throws Exception {
        Setup setup = invalidKey(temp.resolve("case"));
        Files.createDirectory(setup.log());
        assertThatThrownBy(() -> open(setup)).isInstanceOf(ArmKeyAccess.InvalidAccessLog.class);
    }
    @Test void validGateLogsBeforeInvalidJsonIsParsed() throws Exception {
        Setup setup = invalidKey(temp.resolve("case"));
        assertThatThrownBy(() -> open(setup)).isInstanceOf(ArmKeyAccess.InvalidKeyShape.class);
        assertThat(ArmKeyAccess.validateAccessLog(setup.log())).hasSize(1);
    }
    @Test void reserveNeedsOwnLockAndOwnSeal() throws Exception {
        Setup primary = invalidKey(temp.resolve("primary"));
        Path reserveKey = temp.resolve("reserve/key.jsonl");
        // A distinct invalid key ensures the two independently sealed rounds have distinct digests.
        Setup reserve = invalidKey(temp.resolve("reserve"));
        Files.writeString(reserveKey, "NOT JSON RESERVE\n");
        Path distinctReserveSeal = temp.resolve("reserve/distinct-seal.json");
        ArmKeySeal.seal(reserveKey, temp.resolve("reserve/packets"), distinctReserveSeal, ArmKeyGateFixtures.SEALED);
        List<ArmKeyAccess.LockBinding> primaryLocks = primary.bindings();
        assertThatThrownBy(() -> openWith(primary, primaryLocks, BOTH)).isInstanceOf(ArmKeyAccess.KeyLocked.class);
        var reserveOnly = ArmKeyGateFixtures.binding(reserve.lock(), reserve.seal(), reserve.finding());
        List<ArmKeyAccess.LockBinding> reserveOnlyLocks = List.of(reserveOnly);
        assertThatThrownBy(() -> openWith(primary, reserveOnlyLocks, BOTH)).isInstanceOf(ArmKeyAccess.KeyLocked.class);
        Path reserveLock = temp.resolve("reserve/reserve-lock.json");
        LockRecord.create(reserveLock, LockRecord.Kind.RESERVE, ArmKeySeal.load(distinctReserveSeal).workflowStems(),
                List.of(reserve.finding()), ArmKeySeal.load(distinctReserveSeal), distinctReserveSeal, files -> {},
                ArmKeyGateFixtures.LOCKED);
        var own = ArmKeyGateFixtures.binding(reserveLock, distinctReserveSeal, reserve.finding());
        var wrong = ArmKeyGateFixtures.binding(reserveLock, primary.seal(), reserve.finding());
        List<ArmKeyAccess.LockBinding> wrongReserveLocks = List.of(primaryLocks.getFirst(), wrong);
        List<ArmKeyAccess.LockBinding> validLocks = List.of(primaryLocks.getFirst(), own);
        assertThatThrownBy(() -> openWith(primary, wrongReserveLocks, BOTH)).isInstanceOf(LockRecord.LockBroken.class);
        assertThatThrownBy(() -> openWith(primary, validLocks, BOTH)).isInstanceOf(ArmKeyAccess.InvalidKeyShape.class);
        assertThat(ArmKeyAccess.validateAccessLog(primary.log()).getFirst().requiredKinds()).containsExactly(
                LockRecord.Kind.PRIMARY, LockRecord.Kind.RESERVE);
    }
    @Test void primaryMustBeBoundToOpenedSealAndReserveCannotReuseIt() throws Exception {
        Setup primary = invalidKey(temp.resolve("primary"));
        Setup other = invalidKey(temp.resolve("other"));
        Files.writeString(other.key(), "DIFFERENT INVALID JSON\n");
        Path otherSeal = temp.resolve("other/distinct-seal.json");
        ArmKeySeal.seal(other.key(), temp.resolve("other/packets"), otherSeal, ArmKeyGateFixtures.SEALED);
        Path otherPrimary = temp.resolve("other/distinct-primary.json");
        LockRecord.create(otherPrimary, LockRecord.Kind.PRIMARY, ArmKeySeal.load(otherSeal).workflowStems(),
                List.of(other.finding()), ArmKeySeal.load(otherSeal), otherSeal, files -> {}, ArmKeyGateFixtures.LOCKED);
        var wrongPrimary = ArmKeyGateFixtures.binding(otherPrimary, otherSeal, other.finding());
        List<ArmKeyAccess.LockBinding> wrongPrimaryLocks = List.of(wrongPrimary);
        assertThatThrownBy(() -> openWith(primary, wrongPrimaryLocks, PRIMARY)).isInstanceOf(ArmKeyAccess.KeyLocked.class);
        Path sameReserve = temp.resolve("primary/same-reserve.json");
        LockRecord.create(sameReserve, LockRecord.Kind.RESERVE, ArmKeySeal.load(primary.seal()).workflowStems(),
                List.of(primary.finding()), ArmKeySeal.load(primary.seal()), primary.seal(), files -> {},
                ArmKeyGateFixtures.LOCKED);
        var reserveBinding = ArmKeyGateFixtures.binding(sameReserve, primary.seal(), primary.finding());
        List<ArmKeyAccess.LockBinding> sameKeyReserveLocks = List.of(primary.bindings().getFirst(), reserveBinding);
        assertThatThrownBy(() -> openWith(primary, sameKeyReserveLocks, BOTH)).isInstanceOf(ArmKeyAccess.KeyLocked.class);
    }
    @Test void reserveLockMustCoverEveryNonemptyStemOfItsOwnSeal() throws Exception {
        Setup primary = invalidKey(temp.resolve("primary"));
        Setup reserve = invalidKey(temp.resolve("reserve"));
        Files.writeString(temp.resolve("reserve/packets/TWO.jsonl"), "{}\n");
        Path reserveSeal = temp.resolve("reserve/two-stem-seal.json");
        var seal = ArmKeySeal.seal(reserve.key(), temp.resolve("reserve/packets"), reserveSeal,
                ArmKeyGateFixtures.SEALED);
        Path partial = temp.resolve("reserve/partial-reserve.json");
        LockRecord.create(partial, LockRecord.Kind.RESERVE, Set.of("ONE"), List.of(reserve.finding()),
                seal, reserveSeal, files -> {}, ArmKeyGateFixtures.LOCKED);
        var binding = ArmKeyGateFixtures.binding(partial, reserveSeal, reserve.finding());
        List<ArmKeyAccess.LockBinding> partialReserveLocks = List.of(primary.bindings().getFirst(), binding);
        assertThatThrownBy(() -> openWith(primary, partialReserveLocks, BOTH)).isInstanceOf(ArmKeyAccess.KeyLocked.class);
    }
    @Test void emptyKindsAndReserveOnlyKindsRefused() throws Exception {
        Setup setup = invalidKey(temp.resolve("case"));
        List<ArmKeyAccess.LockBinding> locks = setup.bindings();
        Set<LockRecord.Kind> noKinds = Set.of(), reserveOnly = Set.of(LockRecord.Kind.RESERVE);
        assertThatThrownBy(() -> openWith(setup, locks, noKinds)).isInstanceOf(ArmKeyAccess.KeyLocked.class);
        assertThatThrownBy(() -> openWith(setup, locks, reserveOnly)).isInstanceOf(ArmKeyAccess.KeyLocked.class);
    }
    @Test void classAKeyWithTheRealStageOneArmLabelsOpens() throws Exception {
        Path root = Files.createTempDirectory(temp, "labels-");
        Path packets = Files.createDirectory(root.resolve("packets"));
        Files.writeString(packets.resolve("ONE.jsonl"), "{}\n");
        Path key = root.resolve("key.jsonl");
        Files.writeString(key, "{\"id\":\"a\",\"arm\":\"LUNA\",\"caseId\":\"c\",\"workflow\":\"ONE\",\"questionFormat\":\"MCQ\"}\n"
                + "{\"id\":\"b\",\"arm\":\"BASELINE_MINI\",\"caseId\":\"c\",\"workflow\":\"ONE\",\"questionFormat\":\"MCQ\"}\n"
                + "{\"id\":\"c\",\"arm\":\"BASELINE_41\",\"caseId\":\"c\",\"workflow\":\"ONE\",\"questionFormat\":\"MCQ\"}\n");
        Path seal = ArmKeyGateFixtures.seal(root, key);
        Path finding = ArmKeyGateFixtures.findings(root);
        Path lock = ArmKeyGateFixtures.lock(root, seal, finding, LockRecord.Kind.PRIMARY);
        Setup setup = new Setup(key, seal, finding, lock, root.resolve("access.jsonl"));
        ArmKeyAccess.OpenedKey opened = open(setup);
        assertThat(opened.armOf("a")).isEqualTo("LUNA");
        assertThat(opened.armOf("b")).isEqualTo("BASELINE_MINI");
        assertThat(opened.armOf("c")).isEqualTo("BASELINE_41");
    }
    @Test void invalidShapesAndDuplicateIdsRejectedAfterLogging() throws Exception {
        for (String body : List.of(
                "{\"id\":\"one\",\"arm\":\"not a label\",\"caseId\":\"c\",\"workflow\":\"ONE\",\"questionFormat\":\"MCQ\"}\n",
                "{\"itemId\":\"one\",\"workflow\":\"ONE\",\"caseId\":\"c\",\"arm\":\"LUNA\",\"reserve\":false}\n",
                "{\"id\":\"one\",\"arm\":\"BASELINE\",\"caseId\":\"c\",\"workflow\":\"ONE\",\"questionFormat\":\"MCQ\"}\n{\"id\":\"one\",\"arm\":\"CANDIDATE\",\"caseId\":\"c\",\"workflow\":\"ONE\",\"questionFormat\":\"MCQ\"}\n",
                "{\"unknown\":1}\n", "\n{\"id\":\"one\"}\n")) {
            Path root = Files.createTempDirectory(temp, "shape-");
            Path packets = Files.createDirectory(root.resolve("packets"));
            Files.writeString(packets.resolve("ONE.jsonl"), "{}\n");
            Path key = root.resolve("key.jsonl");
            Files.writeString(key, body);
            Path seal = ArmKeyGateFixtures.seal(root, key);
            Path finding = ArmKeyGateFixtures.findings(root);
            Path lock = ArmKeyGateFixtures.lock(root, seal, finding, LockRecord.Kind.PRIMARY);
            Setup setup = new Setup(key, seal, finding, lock, root.resolve("access.jsonl"));
            assertThatThrownBy(() -> open(setup)).isInstanceOf(ArmKeyAccess.InvalidKeyShape.class);
            assertThat(ArmKeyAccess.validateAccessLog(setup.log())).hasSize(1);
        }
    }
    @Test void constructorIsPrivateAndChainTamperDetected() throws Exception {
        assertThat(ArmKeyAccess.OpenedKey.class.getDeclaredConstructors())
                .allSatisfy(constructor -> assertThat(Modifier.isPublic(constructor.getModifiers())).isFalse());
        Setup setup = invalidKey(temp.resolve("case"));
        assertThatThrownBy(() -> open(setup)).isInstanceOf(ArmKeyAccess.InvalidKeyShape.class);
        assertThatThrownBy(() -> open(setup)).isInstanceOf(ArmKeyAccess.InvalidKeyShape.class);
        Path log = setup.log();
        Files.writeString(log, Files.readString(log).replaceFirst(PURPOSE, "altered"));
        assertThatThrownBy(() -> ArmKeyAccess.validateAccessLog(log))
                .isInstanceOf(ArmKeyAccess.InvalidAccessLog.class);
    }
    @Test void appendComputesSeqAndPreviousHashItself() throws Exception {
        Setup setup = invalidKey(temp.resolve("case"));
        assertThatThrownBy(() -> open(setup)).isInstanceOf(ArmKeyAccess.InvalidKeyShape.class);
        ObjectNode candidate = IntegritySupport.JSON.createObjectNode();
        candidate.put("seq", 999).put("prevSha256", "wrong").put("openedAtUtc", ArmKeyGateFixtures.OPENED)
                .put("purpose", PURPOSE).put("armKeySha256", ArmKeySeal.load(setup.seal()).armKeySha256());
        candidate.putArray("requiredKinds").add("PRIMARY");
        candidate.putArray("lockSha256s").add(IntegritySupport.detachedHash(setup.lock()));
        ArmKeyAccess.append(setup.log(), candidate);
        assertThat(ArmKeyAccess.validateAccessLog(setup.log()).get(1).seq()).isEqualTo(1);
    }
}
