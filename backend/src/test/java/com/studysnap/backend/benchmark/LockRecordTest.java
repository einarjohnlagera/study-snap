package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LockRecordTest {
    @TempDir Path temp;

    @Test void realClassAScoreValidatorLocksIdOnlyFindings() throws Exception {
        var export = ArmKeyGateFixtures.classA(temp);
        Path sealFile = ArmKeyGateFixtures.seal(temp, export.armKey());
        Path finding = ArmKeyGateFixtures.findings(temp);
        Path idOnly = temp.resolve("id-only.jsonl"), p = temp.resolve("p.jsonl"), s = temp.resolve("s.jsonl");
        ArmKeyGateFixtures.idOnlyAndEmptyScorers(temp, idOnly, p, s);
        Path lockFile = temp.resolve("lock.json");
        var record = LockRecord.create(lockFile, LockRecord.Kind.PRIMARY, Set.of("NOTE_ADAPTIVE"),
                List.of(finding), ArmKeySeal.load(sealFile), sealFile,
                ArmKeyGateFixtures.classAValidator(temp.resolve("packets"), idOnly, p, s), ArmKeyGateFixtures.LOCKED);
        assertThat(LockRecord.load(lockFile)).isEqualTo(record);
        LockRecord.verifyFindingsUnchanged(record, finding.getParent());
        assertThat(Files.readString(lockFile)).doesNotContain("BASELINE", "CANDIDATE", "blind-00001");
    }
    @Test void validatorFailureLeavesNoLock() throws Exception {
        var export = ArmKeyGateFixtures.classA(temp);
        Path sealFile = ArmKeyGateFixtures.seal(temp, export.armKey());
        Path finding = ArmKeyGateFixtures.findings(temp), lockFile = temp.resolve("lock.json");
        LockRecord.FindingsValidator fail = files -> { throw new LockRecord.InvalidLock("incomplete"); };
        Set<String> workflows = Set.of("NOTE_ADAPTIVE");
        List<Path> findings = List.of(finding);
        ArmKeySeal.Seal seal = ArmKeySeal.load(sealFile);
        assertThatThrownBy(() -> LockRecord.create(lockFile, LockRecord.Kind.PRIMARY, workflows,
                findings, seal, sealFile, fail, ArmKeyGateFixtures.LOCKED))
                .isInstanceOf(LockRecord.InvalidLock.class);
        assertThat(Files.exists(lockFile)).isFalse();
        assertThat(Files.exists(IntegritySupport.detached(lockFile))).isFalse();
    }
    @Test void createNeverReadsArmKey() throws Exception {
        var export = ArmKeyGateFixtures.classA(temp);
        Path sealFile = ArmKeyGateFixtures.seal(temp, export.armKey());
        Path finding = ArmKeyGateFixtures.findings(temp);
        Set<PosixFilePermission> original = Files.getPosixFilePermissions(export.armKey());
        Files.setPosixFilePermissions(export.armKey(), Set.of());
        try {
            assertThat(Files.isReadable(export.armKey())).isFalse();
            LockRecord record = LockRecord.create(temp.resolve("lock.json"), LockRecord.Kind.PRIMARY,
                    Set.of("NOTE_ADAPTIVE"), List.of(finding), ArmKeySeal.load(sealFile), sealFile,
                    files -> {}, ArmKeyGateFixtures.LOCKED);
            assertThat(record.armKeySha256()).isEqualTo(ArmKeySeal.load(sealFile).armKeySha256());
        } finally {
            Files.setPosixFilePermissions(export.armKey(), original);
        }
    }
    @Test void invalidWorkflowAndEarlierTimeRefused() throws Exception {
        var export = ArmKeyGateFixtures.classA(temp);
        Path sealFile = ArmKeyGateFixtures.seal(temp, export.armKey());
        Path finding = ArmKeyGateFixtures.findings(temp), lock = temp.resolve("lock.json");
        var seal = ArmKeySeal.load(sealFile);
        Set<String> otherWorkflow = Set.of("OTHER"), validWorkflow = Set.of("NOTE_ADAPTIVE");
        List<Path> findings = List.of(finding);
        assertThatThrownBy(() -> LockRecord.create(lock, LockRecord.Kind.PRIMARY, otherWorkflow,
                findings, seal, sealFile, files -> {}, ArmKeyGateFixtures.LOCKED))
                .isInstanceOf(LockRecord.InvalidLock.class);
        assertThatThrownBy(() -> LockRecord.create(lock, LockRecord.Kind.PRIMARY, validWorkflow,
                findings, seal, sealFile, files -> {}, "2026-11-15T23:59:59Z"))
                .isInstanceOf(LockRecord.InvalidLock.class);
    }
    @Test void twoDirectoriesAndDuplicateNameRefused() throws Exception {
        var export = ArmKeyGateFixtures.classA(temp);
        Path sealFile = ArmKeyGateFixtures.seal(temp, export.armKey());
        var seal = ArmKeySeal.load(sealFile);
        Path one = ArmKeyGateFixtures.findings(temp);
        Path other = temp.resolve("other/human.jsonl");
        Files.createDirectories(other.getParent());
        Files.writeString(other, "");
        Path lock = temp.resolve("lock.json");
        Set<String> workflows = seal.workflowStems();
        List<Path> splitDirectories = List.of(one, other), duplicateNames = List.of(one, one);
        assertThatThrownBy(() -> LockRecord.create(lock, LockRecord.Kind.PRIMARY, workflows,
                splitDirectories, seal, sealFile, files -> {}, ArmKeyGateFixtures.LOCKED))
                .isInstanceOf(LockRecord.InvalidLock.class);
        assertThatThrownBy(() -> LockRecord.create(lock, LockRecord.Kind.PRIMARY, workflows,
                duplicateNames, seal, sealFile, files -> {}, ArmKeyGateFixtures.LOCKED))
                .isInstanceOf(LockRecord.InvalidLock.class);
    }
    @Test void overwriteAndDetachedTamperRefused() throws Exception {
        var export = ArmKeyGateFixtures.classA(temp);
        Path sealFile = ArmKeyGateFixtures.seal(temp, export.armKey());
        Path finding = ArmKeyGateFixtures.findings(temp);
        Path lock = ArmKeyGateFixtures.lock(temp, sealFile, finding, LockRecord.Kind.PRIMARY);
        var seal = ArmKeySeal.load(sealFile);
        Set<String> workflows = seal.workflowStems();
        List<Path> findings = List.of(finding);
        assertThatThrownBy(() -> LockRecord.create(lock, LockRecord.Kind.PRIMARY, workflows,
                findings, seal, sealFile, files -> {}, ArmKeyGateFixtures.LOCKED))
                .isInstanceOf(LockRecord.InvalidLock.class);
        Files.writeString(IntegritySupport.detached(lock), "0".repeat(64));
        assertThatThrownBy(() -> LockRecord.load(lock)).isInstanceOf(LockRecord.LockBroken.class);
    }
    @Test void changedMissingAndExtraFindingsRefused() throws Exception {
        var export = ArmKeyGateFixtures.classA(temp);
        Path sealFile = ArmKeyGateFixtures.seal(temp, export.armKey());
        Path finding = ArmKeyGateFixtures.findings(temp);
        LockRecord record = LockRecord.load(ArmKeyGateFixtures.lock(temp, sealFile, finding, LockRecord.Kind.PRIMARY));
        Path directory = finding.getParent();
        Files.writeString(finding, "x");
        assertThatThrownBy(() -> LockRecord.verifyFindingsUnchanged(record, directory))
                .isInstanceOf(LockRecord.LockBroken.class);
        Files.delete(finding);
        assertThatThrownBy(() -> LockRecord.verifyFindingsUnchanged(record, directory))
                .isInstanceOf(LockRecord.LockBroken.class);
        Files.writeString(finding, "");
        Files.writeString(directory.resolve("extra.jsonl"), "{}");
        assertThatThrownBy(() -> LockRecord.verifyFindingsUnchanged(record, directory))
                .isInstanceOf(LockRecord.LockBroken.class);
    }
    @Test void sortedInputsGiveIdenticalLockBytesAndReserveKind() throws Exception {
        var export = ArmKeyGateFixtures.classA(temp);
        Path sealFile = ArmKeyGateFixtures.seal(temp, export.armKey());
        Path first = ArmKeyGateFixtures.findings(temp), second = first.resolveSibling("second.jsonl");
        Files.writeString(second, "");
        var seal = ArmKeySeal.load(sealFile);
        Path one = temp.resolve("one.json"), two = temp.resolve("two.json");
        LockRecord.create(one, LockRecord.Kind.RESERVE, seal.workflowStems(), List.of(second, first),
                seal, sealFile, files -> {}, ArmKeyGateFixtures.LOCKED);
        LockRecord.create(two, LockRecord.Kind.RESERVE, seal.workflowStems(), List.of(first, second),
                seal, sealFile, files -> {}, ArmKeyGateFixtures.LOCKED);
        assertThat(Files.readAllBytes(one)).isEqualTo(Files.readAllBytes(two));
        assertThat(LockRecord.load(one).kind()).isEqualTo(LockRecord.Kind.RESERVE);
    }
}
