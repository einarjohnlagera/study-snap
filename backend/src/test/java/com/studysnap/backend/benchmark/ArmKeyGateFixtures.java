package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.entity.LearnerLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Tiny synthetic exports and findings for seal/lock/access tests. */
final class ArmKeyGateFixtures {
    static final String SEALED = "2026-11-16T00:00:00Z";
    static final String LOCKED = "2026-11-16T00:01:00Z";
    static final String OPENED = "2026-11-16T00:02:00Z";
    private ArmKeyGateFixtures() {}

    static ClassABlindPacketExport.Export classA(Path root) {
        var source = new ClassACaseManifest.Source("Cells", ClassAScoreSchemaV2Test.SOURCE, "Boundary",
                List.of("membrane"), List.of("cells"), "Biology", "Biology", null, LearnerLevel.COLLEGE);
        var fixture = new ClassACaseManifest.Case("case-a", ClassACaseManifest.Workflow.NOTE_ADAPTIVE,
                "synthetic", LearnerLevel.COLLEGE, null, "Biology", false, List.of(source), 1, null, false, null);
        ObjectNode question = ClassAScoreSchemaV2Test.packet("MCQ").question().deepCopy();
        var questions = ClassAScoreSchemaV2Test.JSON.createArrayNode().add(question);
        return ClassABlindPacketExport.export(List.of(fixture), List.of(
                new ClassABlindPacketExport.Output("case-a", "BASELINE", questions),
                new ClassABlindPacketExport.Output("case-a", "CANDIDATE", questions)),
                "BASELINE", "CANDIDATE", root.resolve("packets"), root.resolve("key.jsonl"), 17L);
    }
    static Path seal(Path root, Path key) {
        return seal(root, key, root.resolve("packets"));
    }
    static Path seal(Path root, Path key, Path packets) {
        Path file = root.resolve("seal.json");
        ArmKeySeal.seal(key, packets, file, SEALED);
        return file;
    }
    static Path findings(Path root) throws Exception {
        Path dir = root.resolve("findings");
        Files.createDirectories(dir);
        Path file = dir.resolve("human.jsonl");
        Files.writeString(file, "");
        return file;
    }
    static Path lock(Path root, Path sealFile, Path finding, LockRecord.Kind kind) {
        ArmKeySeal.Seal seal = ArmKeySeal.load(sealFile);
        Path file = root.resolve(kind.name().toLowerCase() + "-lock.json");
        LockRecord.create(file, kind, seal.workflowStems(), List.of(finding), seal, sealFile,
                files -> {}, LOCKED);
        return file;
    }
    static ArmKeyAccess.LockBinding binding(Path lock, Path seal, Path finding) {
        return new ArmKeyAccess.LockBinding(lock, seal, finding.getParent());
    }
    static LockRecord.FindingsValidator classAValidator(Path packetDir, Path idOnly,
                                                         Path p, Path s) {
        return files -> ClassAScoreFilesV2.load(idOnly, ClassAPacketIndex.load(packetDir), p, s,
                files.getFirst(), ClassAScoreSchemaV2Test.MAPPING);
    }
    static void idOnlyAndEmptyScorers(Path root, Path idOnly, Path p, Path s) throws Exception {
        PairMap.fromPackets(root.resolve("packets")).idOnlyKey(idOnly);
        Files.writeString(p, "");
        Files.writeString(s, "");
    }
}
