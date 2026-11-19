package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Key-free, pre-attribution status for drawn class-B reserves. A separately established primary
 * FAIL cannot exist here: the primary opening is permitted only after both reserve locks.
 */
final class FrbReserveStatus {
    static final String NOT_SCORED = "RESERVE_NOT_SCORED";
    record ReserveArtifacts(Path packetDirectory, Path sealFile, Path scoresFile,
            Map<String, ClassBScoreFiles.ScorerKind> scorers, Path lockForPrimaryOpening,
            Path lockForReserveOpening, Path findingsDirectory) {}
    record Input(Path reserveDecisionRecordFile, ResultsPipeline.ReserveVerification verification,
            Path mappingFile, Path parametersFile, Map<String, ReserveArtifacts> reserves) {}
    record Workflow(boolean drawn, boolean scored, boolean locked) {}
    record Result(String status, Map<String, List<String>> causes, boolean attributable,
            List<String> awaitingLock, String reserveDecisionRecordSha256,
            Map<String, Boolean> reserveDrawnByWorkflow, Map<String, Workflow> workflows,
            CauseTable.Kind causeClass) {}

    private FrbReserveStatus() {}

    static Result write(Input input, Path statusFile) {
        try {
            return writeVerified(input, statusFile);
        } catch (ResultsPipeline.InvalidInput exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResultsPipeline.InvalidInput("FRB reserve status refused: " + exception.getMessage(), exception);
        }
    }

    private static Result writeVerified(Input input, Path statusFile) throws IOException {
        if (input == null || statusFile == null || input.reserveDecisionRecordFile() == null
                || input.verification() == null || input.mappingFile() == null
                || input.parametersFile() == null || input.reserves() == null)
            throw invalid("Missing reserve status input");
        ResultsPipeline.ReserveVerification check = input.verification();
        if (check.packetDirectory() == null || check.sealFile() == null || check.scoresFile() == null
                || check.scorers() == null || check.workflowNames() == null || check.primaryLockFile() == null)
            throw invalid("Incomplete reserve verification input");
        FrozenGateParameters parameters = FrozenGateParameters.load(input.parametersFile());
        if (!parameters.frbEnabled || !check.workflowNames().equals(parameters.frbClassBWorkflows))
            throw invalid("Class-B verification workflows differ from FRB parameters");
        ReserveDecisionRecord record = ReserveDecisionRecord.load(input.reserveDecisionRecordFile());
        LockRecord primary = LockRecord.load(check.primaryLockFile());
        ClassBScoreFiles.Validated validated = ClassBScoreFiles.validate(check.scoresFile(),
                ReserveDecisionRecord.manifestFromPackets(check.packetDirectory()), check.scorers());
        ReserveDecisionRecord.verify(record, check.packetDirectory(), check.sealFile(), validated,
                EvidenceMapping.load(input.mappingFile()), check.workflowNames(), check.scoresFile(),
                primary, check.primaryLockFile());

        Map<String, Boolean> decisions = sorted(record.reserveDrawnByWorkflow());
        Set<String> drawn = decisions.entrySet().stream().filter(entry -> entry.getValue())
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
        if (!drawn.containsAll(input.reserves().keySet()))
            throw invalid("Reserve artifacts differ from drawn workflows");
        safeOutput(statusFile, input);

        Map<String, Workflow> workflows = new LinkedHashMap<>();
        Map<String, List<String>> causes = new LinkedHashMap<>();
        List<String> awaitingLock = new ArrayList<>();
        for (String name : decisions.keySet()) {
            if (!drawn.contains(name)) {
                workflows.put(name, new Workflow(false, false, false));
                continue;
            }
            ReserveArtifacts artifacts = input.reserves().get(name);
            boolean scored = verifyScores(name, artifacts);
            boolean locked = verifyLocks(name, artifacts, record, scored);
            workflows.put(name, new Workflow(true, scored, locked));
            if (!scored) causes.put(name, List.of(NOT_SCORED));
            else if (!locked) awaitingLock.add(name);
        }
        Result result = new Result(causes.isEmpty() ? null : "INCONCLUSIVE", Map.copyOf(causes),
                workflows.values().stream().allMatch(state -> !state.drawn() || state.scored() && state.locked()),
                List.copyOf(awaitingLock),
                FreezeRecord.sha256(input.reserveDecisionRecordFile()), Map.copyOf(decisions),
                Map.copyOf(workflows), CauseTable.kind(NOT_SCORED));
        ObjectNode json = IntegritySupport.JSON.createObjectNode();
        if (result.status() == null) json.putNull("status");
        else json.put("status", result.status());
        ObjectNode causeNode = json.putObject("causes");
        causes.forEach((name, values) -> causeNode.putArray(name).add(values.getFirst()));
        json.put("attributable", result.attributable());
        var awaitingNode = json.putArray("awaitingLock");
        awaitingLock.forEach(awaitingNode::add);
        json.put("reserveDecisionRecordSha256", result.reserveDecisionRecordSha256());
        ObjectNode drawnNode = json.putObject("reserveDrawnByWorkflow");
        decisions.forEach(drawnNode::put);
        ObjectNode workflowNode = json.putObject("workflows");
        workflows.forEach((name, state) -> workflowNode.putObject(name)
                .put("drawn", state.drawn()).put("scored", state.scored()).put("locked", state.locked()));
        json.put("causeClass", result.causeClass().name());
        Files.write(statusFile, IntegritySupport.JSON.writeValueAsBytes(json), StandardOpenOption.CREATE_NEW);
        return result;
    }

    private static boolean verifyScores(String name, ReserveArtifacts artifacts) {
        if (artifacts == null) return false;
        boolean packets = exists(artifacts.packetDirectory());
        boolean seal = exists(artifacts.sealFile());
        boolean scores = exists(artifacts.scoresFile());
        boolean first = exists(artifacts.lockForPrimaryOpening());
        boolean second = exists(artifacts.lockForReserveOpening());
        if (seal && !packets || scores && !packets || first && !scores || second && !scores)
            throw invalid("Incomplete reserve artifact chain: " + name);
        if (packets) {
            List<ClassBScoreFiles.BlindItem> manifest = ReserveDecisionRecord.manifestFromPackets(artifacts.packetDirectory());
            if (manifest.isEmpty() || manifest.stream().anyMatch(item -> !item.reserve()
                    || !item.workflow().name().equals(name)))
                throw invalid("Reserve manifest differs from drawn workflow: " + name);
            if (seal) {
                ArmKeySeal.Seal loaded = ArmKeySeal.load(artifacts.sealFile());
                ArmKeySeal.verifyPackets(artifacts.packetDirectory(), loaded);
                if (!loaded.workflowStems().equals(Set.of(name)))
                    throw invalid("Reserve seal workflows differ: " + name);
            }
            if (scores) {
                if (artifacts.scorers() == null) throw invalid("Reserve scorers missing: " + name);
                ClassBScoreFiles.Validated validated = ClassBScoreFiles.validate(artifacts.scoresFile(),
                        manifest, artifacts.scorers());
                return validated.scores().stream().anyMatch(row -> row.scorerKind() == ClassBScoreFiles.ScorerKind.HUMAN
                        && row.workflow().name().equals(name));
            }
        }
        if (artifacts.scorers() != null && !packets && !seal && !scores && !first && !second)
            throw invalid("Orphan reserve scorers: " + name);
        return false;
    }

    private static boolean verifyLocks(String name, ReserveArtifacts artifacts,
            ReserveDecisionRecord record, boolean scored) throws IOException {
        if (artifacts == null) return false;
        boolean firstExists = exists(artifacts.lockForPrimaryOpening());
        boolean secondExists = exists(artifacts.lockForReserveOpening());
        if (!firstExists && !secondExists) return false;
        if (!scored || !exists(artifacts.sealFile()) || artifacts.findingsDirectory() == null)
            throw invalid("Reserve lock lacks scored and sealed findings: " + name);
        Path directory = artifacts.findingsDirectory().toAbsolutePath().normalize();
        if (!directory.equals(artifacts.scoresFile().toAbsolutePath().normalize().getParent()))
            throw invalid("Reserve findings directory differs from scores: " + name);
        ArmKeySeal.Seal seal = ArmKeySeal.load(artifacts.sealFile());
        LockRecord first = firstExists ? verifyLock(name, artifacts.lockForPrimaryOpening(),
                LockRecord.Kind.RESERVE, artifacts, record, seal) : null;
        LockRecord second = secondExists ? verifyLock(name, artifacts.lockForReserveOpening(),
                LockRecord.Kind.PRIMARY, artifacts, record, seal) : null;
        if (first != null && second != null && (!first.workflows().equals(second.workflows())
                || !first.findings().equals(second.findings())))
            throw invalid("Two reserve locks differ in workflows or findings: " + name);
        return first != null && second != null;
    }

    private static LockRecord verifyLock(String name, Path file, LockRecord.Kind kind,
            ReserveArtifacts artifacts, ReserveDecisionRecord record, ArmKeySeal.Seal seal) throws IOException {
        LockRecord lock = LockRecord.load(file);
        if (lock.kind() != kind || !Objects.equals(lock.sealSha256(), IntegritySupport.detachedHash(artifacts.sealFile()))
                || !Objects.equals(lock.armKeySha256(), seal.armKeySha256())
                || !lock.packetFiles().equals(seal.packetFiles().stream()
                    .map(item -> new LockRecord.PacketDigest(item.name(), item.sha256())).toList())
                || !lock.workflows().contains(name))
            throw invalid("Reserve lock or seal binding differs: " + name);
        LockRecord.verifyFindingsUnchanged(lock, artifacts.findingsDirectory());
        String scoreHash = FreezeRecord.sha256(artifacts.scoresFile());
        if (lock.findings().stream().noneMatch(item -> item.name().equals(artifacts.scoresFile().getFileName().toString())
                && item.sha256().equals(scoreHash)))
            throw invalid("Reserve scores absent from lock findings: " + name);
        if (!IntegritySupport.utc(record.createdAtUtc()).isBefore(IntegritySupport.utc(lock.lockedAtUtc())))
            throw invalid("Reserve lock must follow decision: " + name);
        return lock;
    }

    private static void safeOutput(Path output, Input input) {
        Path absolute = output.toAbsolutePath().normalize();
        Path privateDirectory = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize())
                .resolve(ClassBCaseManifest.PRIVATE_PATH).toAbsolutePath().normalize();
        if (absolute.startsWith(privateDirectory)) throw invalid("Status path overlaps private evaluation data");
        List<Path> forbidden = new ArrayList<>();
        ResultsPipeline.ReserveVerification check = input.verification();
        forbidden.add(check.packetDirectory());
        forbidden.add(check.scoresFile().getParent());
        forbidden.add(check.sealFile());
        forbidden.add(check.primaryLockFile());
        forbidden.add(input.reserveDecisionRecordFile());
        forbidden.add(check.scoresFile());
        forbidden.add(input.mappingFile());
        forbidden.add(input.parametersFile());
        for (ReserveArtifacts artifacts : input.reserves().values()) if (artifacts != null) {
            if (artifacts.packetDirectory() != null) {
                forbidden.add(artifacts.packetDirectory());
                forbidden.add(artifacts.packetDirectory().getParent());
            }
            if (artifacts.findingsDirectory() != null) forbidden.add(artifacts.findingsDirectory());
            if (artifacts.scoresFile() != null) forbidden.add(artifacts.scoresFile().getParent());
            if (artifacts.sealFile() != null) forbidden.add(artifacts.sealFile());
            if (artifacts.lockForPrimaryOpening() != null) forbidden.add(artifacts.lockForPrimaryOpening());
            if (artifacts.lockForReserveOpening() != null) forbidden.add(artifacts.lockForReserveOpening());
        }
        if (forbidden.stream().filter(Objects::nonNull).map(path -> path.toAbsolutePath().normalize())
                .anyMatch(absolute::startsWith)) throw invalid("Status path overlaps evidence input");
    }

    private static boolean exists(Path path) { return path != null && Files.exists(path); }
    private static <T> Map<String, T> sorted(Map<String, T> input) {
        Map<String, T> result = new LinkedHashMap<>();
        input.keySet().stream().sorted().forEach(name -> result.put(name, input.get(name)));
        return result;
    }
    private static ResultsPipeline.InvalidInput invalid(String message) {
        return new ResultsPipeline.InvalidInput(message);
    }
}
