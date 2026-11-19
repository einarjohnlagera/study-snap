package com.studysnap.backend.benchmark;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Verifies the decision, sealed exports, two reserve locks and every supplied findings file before
 * attribution. Primary scores remain in the primary lock's directory; reserve scores belong in a
 * separate directory and cannot be appended to the primary scores after its lock. This verifies
 * bytes and ordering, not that statuses were computed from those bytes or that a caller-built bundle
 * came from the opened keys. The latter requires a file-backed assembler at the freeze step.
 */
final class FrbProvenance {
    record Verified(ReserveDecisionRecord record, Instant minimumPrimaryOpen,
                    Map<String, String> hashes) {}
    private FrbProvenance() {}

    static Verified verify(ResultsPipeline.FrbRunInput run, ResultsPipeline.FrbInput frb) {
        try {
            return verifyFiles(run, frb);
        } catch (ResultsPipeline.InvalidInput exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResultsPipeline.InvalidInput("FRB provenance refused: " + exception.getMessage(), exception);
        }
    }

    private static Verified verifyFiles(ResultsPipeline.FrbRunInput run,
                                        ResultsPipeline.FrbInput frb) throws IOException {
        if (run == null || frb == null || frb.bundle() == null || frb.reserveRecordFile() == null
                || frb.verification() == null || frb.provenance() == null
                || frb.provenance().primaries() == null || frb.provenance().primaries().isEmpty()
                || frb.provenance().reserves() == null || run.reserves() == null)
            throw invalid("Missing FRB provenance input");
        verifyPlacement(run, frb.provenance());
        ResultsPipeline.ReserveVerification check = frb.verification();
        ResultsPipeline.ProvenanceLogs logs = frb.provenance();
        if (check.packetDirectory() == null || check.sealFile() == null || check.scoresFile() == null
                || check.scorers() == null || check.workflowNames() == null || check.primaryLockFile() == null)
            throw invalid("Incomplete reserve verification input");
        FrozenGateParameters parameters = FrozenGateParameters.load(run.parameters());
        if (!parameters.frbEnabled || !check.workflowNames().equals(parameters.frbClassBWorkflows))
            throw invalid("Class-B verification workflows differ from FRB parameters");
        ReserveDecisionRecord record = ReserveDecisionRecord.load(frb.reserveRecordFile());
        LockRecord primaryClassBLock = LockRecord.load(check.primaryLockFile());
        ClassBScoreFiles.Validated validated = ClassBScoreFiles.validate(check.scoresFile(),
                ReserveDecisionRecord.manifestFromPackets(check.packetDirectory()), check.scorers());
        ReserveDecisionRecord.verify(record, check.packetDirectory(), check.sealFile(), validated,
                EvidenceMapping.load(run.mapping()), check.workflowNames(), check.scoresFile(),
                primaryClassBLock, check.primaryLockFile());
        if (!record.reserveDrawnByWorkflow().equals(frb.bundle().reserveDrawnByWorkflow()))
            throw invalid("Reserve decisions differ from evidence bundle");
        if (!frb.bundle().workflowsAwaitingReserve().isEmpty())
            throw invalid("Workflows awaiting reserve: " + frb.bundle().workflowsAwaitingReserve());

        Set<String> drawn = new HashSet<>();
        record.reserveDrawnByWorkflow().forEach((workflow, value) -> {
            if (Boolean.TRUE.equals(value)) drawn.add(workflow);
        });
        if (!run.reserves().keySet().equals(drawn))
            throw invalid("Reserve receipt inputs differ from drawn workflows: " + drawn);
        if (!logs.reserves().keySet().equals(drawn))
            throw invalid("Reserve locks or logs differ from drawn workflows: " + drawn);
        ResultsPipeline.verifyFrbBundleCases(run, frb.bundle(), drawn);

        Map<String, String> hashes = new LinkedHashMap<>();
        hashes.put("reserveDecisionRecord", FreezeRecord.sha256(frb.reserveRecordFile()));
        hashes.put("classBPrimaryScores", FreezeRecord.sha256(check.scoresFile()));
        Map<String, LockRecord> locks = new HashMap<>();
        Map<String, ArmKeySeal.Seal> seals = new HashMap<>();
        List<ArmKeyAccess.AccessEntry> primaryEntries = new ArrayList<>();
        Instant minimum = null;
        boolean classBLockSupplied = false;
        int index = 0;
        for (ResultsPipeline.PrimaryExport export : logs.primaries()) {
            if (export == null || export.accessLog() == null || export.sealFile() == null
                    || export.lockFile() == null || export.findingsDirectory() == null)
                throw invalid("Incomplete primary export");
            String label = "primary." + index++;
            LockRecord lock = addLock(label, export.lockFile(), export.findingsDirectory(), locks, hashes);
            ArmKeySeal.Seal seal = addSeal(label, export.sealFile(), seals, hashes);
            if (lock.kind() != LockRecord.Kind.PRIMARY || !bound(lock, seal, export.sealFile()))
                throw invalid("Primary lock or seal binding differs: " + label);
            boolean classB = export.lockFile().equals(check.primaryLockFile());
            if (classB) classBLockSupplied = true;
            boolean exclusions = export.exclusionsFileOrNull() != null;
            if (exclusions != (export.expectedExclusionsSha256OrNull() != null))
                throw invalid("Exclusions file and expected hash must be supplied together: " + label);
            if (classB == exclusions)
                throw invalid("Class-A exclusions required and class-B exclusions forbidden: " + label);
            if (exclusions) {
                String actual = FreezeRecord.sha256(export.exclusionsFileOrNull());
                if (!actual.equals(export.expectedExclusionsSha256OrNull()))
                    throw invalid("Exclusions hash differs: " + label);
                hashes.put(label + ".exclusions", actual);
            }
            List<ArmKeyAccess.AccessEntry> entries = ArmKeyAccess.validateAccessLog(export.accessLog());
            hashes.put(label + ".accessLog", FreezeRecord.sha256(export.accessLog()));
            String lockHash = IntegritySupport.detachedHash(export.lockFile());
            for (ArmKeyAccess.AccessEntry entry : entries) {
                if (!entry.armKeySha256().equals(seal.armKeySha256()))
                    throw invalid("Primary log matches no supplied seal: " + label);
                if (!entry.requiredKinds().contains(LockRecord.Kind.PRIMARY)
                        || !entry.lockSha256s().contains(lockHash))
                    throw invalid("Primary log lacks its PRIMARY lock: " + label);
                Instant opened = IntegritySupport.utc(entry.openedAtUtc());
                if (!IntegritySupport.utc(lock.lockedAtUtc()).isBefore(opened))
                    throw invalid("Primary lock must precede its key opening: " + label);
                if (minimum == null || opened.isBefore(minimum)) minimum = opened;
            }
            primaryEntries.addAll(entries);
        }
        if (!classBLockSupplied) throw invalid("Class-B PRIMARY lock absent from primary exports");
        if (minimum == null || !IntegritySupport.utc(record.createdAtUtc()).isBefore(minimum))
            throw invalid("Reserve decision must strictly precede first primary key opening");

        for (String workflow : drawn) {
            ResultsPipeline.ReserveExport export = logs.reserves().get(workflow);
            if (export == null || !workflow.equals(export.workflow()) || export.sealFile() == null
                    || export.reserveKeyAccessLog() == null || export.lockForPrimaryOpening() == null
                    || export.lockForReserveOpening() == null || export.findingsDirectory() == null)
                throw invalid("Incomplete reserve export: " + workflow);
            String label = "reserve." + workflow;
            ArmKeySeal.Seal seal = addSeal(label, export.sealFile(), seals, hashes);
            LockRecord primaryOpening = addLock(label + ".forPrimary", export.lockForPrimaryOpening(),
                    export.findingsDirectory(), locks, hashes);
            LockRecord reserveOpening = addLock(label + ".forReserve", export.lockForReserveOpening(),
                    export.findingsDirectory(), locks, hashes);
            if (primaryOpening.kind() != LockRecord.Kind.RESERVE
                    || reserveOpening.kind() != LockRecord.Kind.PRIMARY
                    || !bound(primaryOpening, seal, export.sealFile())
                    || !bound(reserveOpening, seal, export.sealFile()))
                throw invalid("Two reserve locks must bind the reserve seal: " + workflow);
            if (!primaryOpening.workflows().equals(reserveOpening.workflows())
                    || !primaryOpening.findings().equals(reserveOpening.findings())
                    || !primaryOpening.workflows().contains(workflow))
                throw invalid("Two reserve locks differ in workflows or findings: " + workflow);
            for (LockRecord lock : List.of(primaryOpening, reserveOpening)) {
                Instant locked = IntegritySupport.utc(lock.lockedAtUtc());
                if (!IntegritySupport.utc(record.createdAtUtc()).isBefore(locked)
                        || !locked.isBefore(minimum))
                    throw invalid("Decision, reserve locks and first primary open out of order: " + workflow);
            }
            String forPrimaryHash = IntegritySupport.detachedHash(export.lockForPrimaryOpening());
            for (ArmKeyAccess.AccessEntry entry : primaryEntries)
                if (!entry.requiredKinds().contains(LockRecord.Kind.RESERVE)
                        || !entry.lockSha256s().contains(forPrimaryHash))
                    throw invalid("Primary opening lacks RESERVE kind or lock hash: " + workflow);
            String forReserveHash = IntegritySupport.detachedHash(export.lockForReserveOpening());
            List<ArmKeyAccess.AccessEntry> entries = ArmKeyAccess.validateAccessLog(export.reserveKeyAccessLog());
            hashes.put(label + ".accessLog", FreezeRecord.sha256(export.reserveKeyAccessLog()));
            boolean matchingOpen = entries.stream().anyMatch(entry ->
                    entry.armKeySha256().equals(seal.armKeySha256())
                    && entry.lockSha256s().contains(forReserveHash)
                    && entry.requiredKinds().contains(LockRecord.Kind.PRIMARY));
            if (!matchingOpen || entries.stream().anyMatch(entry -> !entry.armKeySha256().equals(seal.armKeySha256())))
                throw invalid("Reserve key opening lacks its PRIMARY lock or arm-key hash: " + workflow);
            for (ArmKeyAccess.AccessEntry entry : entries) {
                if (!IntegritySupport.utc(reserveOpening.lockedAtUtc())
                        .isBefore(IntegritySupport.utc(entry.openedAtUtc())))
                    throw invalid("Reserve PRIMARY lock must precede reserve key opening: " + workflow);
                verifyLogLocks(entry, locks.keySet());
            }
            verifyReserveR28(workflow, export, seal, run.synthetic(), hashes, label);
        }
        for (ArmKeyAccess.AccessEntry entry : primaryEntries) verifyLogLocks(entry, locks.keySet());
        return new Verified(record, minimum, Map.copyOf(hashes));
    }

    static void verifyPlacement(ResultsPipeline.FrbRunInput run,
                                        ResultsPipeline.ProvenanceLogs logs) {
        if (logs.reserves() == null) throw invalid("Missing FRB provenance input");
        List<Path> paths = new ArrayList<>();
        for (ResultsPipeline.PrimaryExport export : logs.primaries()) {
            if (export == null || export.sealFile() == null) throw invalid("Incomplete primary export");
            paths.add(export.sealFile());
        }
        for (ResultsPipeline.ReserveExport export : logs.reserves().values()) {
            if (export == null || export.sealFile() == null) throw invalid("Incomplete reserve export");
            paths.add(export.sealFile());
        }
        FreezeRecord freeze = null;
        Set<String> exportIds = new HashSet<>();
        for (Path path : paths) {
            ArmKeySeal.Seal seal = ArmKeySeal.load(path);
            if (seal.placementExportId() == null) {
                if (!run.synthetic()) throw invalid("Placement seed not sealed");
                continue;
            }
            if (freeze == null) freeze = FreezeRecord.load(run.freeze());
            if (!exportIds.add(seal.placementExportId()))
                throw invalid("Duplicate placement export id");
            String expected = freeze.placementCommitments() == null ? null
                    : freeze.placementCommitments().get(seal.placementExportId());
            if (!Objects.equals(expected, seal.placementCommitmentSha256()))
                throw invalid("Placement commitment differs from freeze");
        }
    }

    private static LockRecord addLock(String label, Path file, Path directory,
            Map<String, LockRecord> locks, Map<String, String> hashes) throws IOException {
        LockRecord lock = LockRecord.load(file);
        LockRecord.verifyFindingsUnchanged(lock, directory);
        String hash = IntegritySupport.detachedHash(file);
        locks.put(hash, lock);
        hashes.put(label + ".lock", hash);
        for (LockRecord.Finding finding : lock.findings())
            hashes.put(label + ".finding." + finding.name(), finding.sha256());
        return lock;
    }

    private static ArmKeySeal.Seal addSeal(String label, Path file,
            Map<String, ArmKeySeal.Seal> seals, Map<String, String> hashes) throws IOException {
        ArmKeySeal.Seal seal = ArmKeySeal.load(file);
        String hash = IntegritySupport.detachedHash(file);
        seals.put(hash, seal);
        hashes.put(label + ".seal", hash);
        return seal;
    }

    private static boolean bound(LockRecord lock, ArmKeySeal.Seal seal, Path sealFile) throws IOException {
        return Objects.equals(lock.sealSha256(), IntegritySupport.detachedHash(sealFile))
                && Objects.equals(lock.armKeySha256(), seal.armKeySha256())
                && lock.packetFiles().equals(seal.packetFiles().stream()
                        .map(item -> new LockRecord.PacketDigest(item.name(), item.sha256())).toList());
    }

    /**
     * Rechecks final human coverage of the sealed reserve manifest, not scoring quality. Byte binding
     * of the score file relies on {@code addLock} having verified both locks against
     * {@code export.findingsDirectory()}. A synthetic run with all three R28 inputs absent skips this
     * check, including an export built with the six-argument constructor. The caller must supply the
     * same scorer registry used at lock time; another registry is detected only if validation fails.
     * No operator entry point currently assembles these inputs.
     */
    static void verifyReserveR28(String workflow, ResultsPipeline.ReserveExport export,
            ArmKeySeal.Seal seal, boolean synthetic, Map<String, String> hashes, String label) {
        boolean packets = export.reservePacketDirectory() != null;
        boolean scores = export.scoresFile() != null;
        boolean scorers = export.scorers() != null;
        if (!packets && !scores && !scorers && synthetic) return;
        if (!packets || !scores || !scorers)
            throw invalid("Incomplete reserve R28 inputs: " + workflow);
        try {
            ArmKeySeal.verifyPackets(export.reservePacketDirectory(), seal);
            Path directory = export.findingsDirectory().toAbsolutePath().normalize();
            Path scoreFile = export.scoresFile().toAbsolutePath().normalize();
            if (!directory.equals(scoreFile.getParent()))
                throw new LockRecord.LockBroken("Reserve scores outside findings directory");
            String scoreName = scoreFile.getFileName().toString();
            LockRecord primary = LockRecord.load(export.lockForPrimaryOpening());
            LockRecord reserve = LockRecord.load(export.lockForReserveOpening());
            if (primary.findings().stream().noneMatch(item -> item.name().equals(scoreName))
                    || reserve.findings().stream().noneMatch(item -> item.name().equals(scoreName)))
                throw new LockRecord.LockBroken("Reserve scores absent from lock findings");
        } catch (ArmKeySeal.SealBroken | LockRecord.LockBroken exception) {
            throw new ResultsPipeline.InvalidInput(
                    "Reserve R28 evidence not bound to the reserve locks: " + workflow, exception);
        }
        try {
            List<ClassBScoreFiles.BlindItem> manifest =
                    ReserveDecisionRecord.manifestFromPackets(export.reservePacketDirectory());
            ClassBScoreFiles.Validated validated =
                    ClassBScoreFiles.validate(export.scoresFile(), manifest, export.scorers());
            ReserveLockReadiness.check(validated, manifest);
            hashes.put(label + ".scores", FreezeRecord.sha256(export.scoresFile()));
        } catch (InvalidEvidence | ClassBScoreFiles.InvalidScore
                | InvalidReserveRecord | IOException exception) {
            throw new ResultsPipeline.InvalidInput("Reserve R28 evidence incomplete: " + workflow, exception);
        }
    }

    private static void verifyLogLocks(ArmKeyAccess.AccessEntry entry, Set<String> supplied) {
        if (!supplied.containsAll(entry.lockSha256s()))
            throw invalid("Access log names an unsupplied lock");
    }

    private static ResultsPipeline.InvalidInput invalid(String message) {
        return new ResultsPipeline.InvalidInput(message);
    }
}
