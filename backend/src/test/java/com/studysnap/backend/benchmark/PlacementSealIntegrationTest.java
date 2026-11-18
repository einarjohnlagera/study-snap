package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.dto.QuickReviewIncorrectQuestionRequest;
import com.studysnap.backend.entity.LearnerLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlacementSealIntegrationTest {
    private static final String EXPORT_B = "CLASS_B_PRIMARY";
    private static final String EXPORT_A = "CLASS_A:BASELINE_MINI-LUNA";
    private static final String COMMITMENT = "96338e3030a7a3c8f41ff582863afe9256a88c89821cc0669203dddd91a083e7";
    private static final Rational ALPHA = Rational.of(1, 20);
    @TempDir Path temp;

    private record BInput(List<ClassBCaseManifest.Case> fixtures, List<ClassBBlindPacketExport.Output> outputs) {}
    private BInput bInput(boolean reserve) {
        List<ClassBCaseManifest.Case> fixtures = new ArrayList<>();
        List<ClassBBlindPacketExport.Output> outputs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String id = "case-" + i;
            fixtures.add(new ClassBCaseManifest.Case(id, ClassBCaseManifest.Workflow.STUDY_TIP,
                    "synthetic", reserve, LearnerLevel.COLLEGE,
                    List.of(new QuickReviewIncorrectQuestionRequest("Question?", "Answer", "Explanation.")),
                    null, null, null, null, null, null, null, null));
            outputs.add(new ClassBBlindPacketExport.Output(id, ClassBScoreAggregator.Arm.BASELINE, "Answer one."));
            outputs.add(new ClassBBlindPacketExport.Output(id, ClassBScoreAggregator.Arm.CANDIDATE, "Answer two."));
        }
        return new BInput(fixtures, outputs);
    }
    private ClassBBlindPacketExport.Export exportB(Path root, BInput input) {
        return ClassBBlindPacketExport.export(input.fixtures(), input.outputs(), root.resolve("packets"),
                root.resolve("key.jsonl"), EXPORT_B, PlacementSecretTest.fixed(), COMMITMENT);
    }
    private static void bAttempt(BInput input, Path root, String keyName, String exportId,
                                 PlacementSecret secret, String commitment) {
        ClassBBlindPacketExport.export(input.fixtures(), input.outputs(), root.resolve("packets"),
                root.resolve(keyName), exportId, secret, commitment);
    }
    private static void bWriterAttempt(BInput input, Path root, PlacementSidecar.Writer writer) {
        ClassBBlindPacketExport.export(input.fixtures(), input.outputs(), root.resolve("packets"),
                root.resolve("key.jsonl"), EXPORT_B, PlacementSecretTest.fixed(), COMMITMENT, null, writer);
    }
    private record AInput(List<ClassACaseManifest.Case> fixtures, List<ClassABlindPacketExport.Output> outputs) {}
    private AInput aInput(boolean reserve, boolean shortLast) {
        List<ClassACaseManifest.Case> fixtures = new ArrayList<>();
        List<ClassABlindPacketExport.Output> outputs = new ArrayList<>();
        var source = new ClassACaseManifest.Source("Cells", "Cells have membranes.", "Membrane",
                List.of("membrane"), List.of("cells"), "Biology", "Biology", null, LearnerLevel.COLLEGE);
        ObjectNode question = IntegritySupport.JSON.createObjectNode().put("question", "What surrounds cells?")
                .put("explanation", "The membrane surrounds cells.").put("correctIndex", 0);
        question.putArray("choices").add("membrane").add("nucleus").add("wall").add("cytoplasm");
        for (int i = 0; i < 10; i++) {
            String id = "case-" + i;
            fixtures.add(new ClassACaseManifest.Case(id, ClassACaseManifest.Workflow.NOTE_ADAPTIVE,
                    "synthetic", LearnerLevel.COLLEGE, null, "Biology", false,
                    List.of(source), 1, null, reserve, null));
            JsonNode full = IntegritySupport.JSON.createArrayNode().add(question);
            JsonNode maybeShort = shortLast && i == 9 ? IntegritySupport.JSON.createArrayNode() : full;
            outputs.add(new ClassABlindPacketExport.Output(id, "BASELINE_MINI", full));
            outputs.add(new ClassABlindPacketExport.Output(id, "LUNA", maybeShort));
        }
        return new AInput(fixtures, outputs);
    }
    private ClassABlindPacketExport.Export exportA(Path root, AInput input) {
        return ClassABlindPacketExport.export(input.fixtures(), input.outputs(), "BASELINE_MINI", "LUNA",
                root.resolve("packets"), root.resolve("key.jsonl"), EXPORT_A,
                PlacementSecretTest.fixed(), COMMITMENT);
    }
    private static void aAttempt(AInput input, Path root, String firstArm, String secondArm,
                                 PlacementSidecar.Writer writer) {
        ClassABlindPacketExport.export(input.fixtures(), input.outputs(), firstArm, secondArm,
                root.resolve("packets"), root.resolve("key.jsonl"), EXPORT_A,
                PlacementSecretTest.fixed(), COMMITMENT, null, writer);
    }
    private static ArmKeyAccess.OpenedKey reopen(Path key, Path seal,
                                                  List<ArmKeyAccess.LockBinding> bindings, Path log) {
        return ArmKeyAccess.open(key, seal, bindings, Set.of(LockRecord.Kind.PRIMARY),
                log, ArmKeyGateFixtures.OPENED, "placement retry");
    }
    private static ArmKeyAccess.OpenedKey reopen(Opened opened, List<ArmKeyAccess.LockBinding> bindings) {
        return reopen(opened.key(), opened.seal(), bindings, opened.log());
    }
    private record Opened(Path packets, Path key, Path sidecar, Path seal, Path log,
                          ArmKeyAccess.OpenedKey opened) {}
    private Opened sealLockOpen(Path root) throws Exception {
        Path key = root.resolve("key.jsonl");
        Path packets = root.resolve("packets");
        Path sidecar = PlacementSidecar.beside(key);
        Path seal = root.resolve("seal.json");
        ArmKeySeal.seal(key, packets, seal, ArmKeyGateFixtures.SEALED, sidecar);
        Path finding = ArmKeyGateFixtures.findings(root);
        Path lock = ArmKeyGateFixtures.lock(root, seal, finding, LockRecord.Kind.PRIMARY);
        Path log = root.resolve("access.jsonl");
        var opened = ArmKeyAccess.open(key, seal, List.of(ArmKeyGateFixtures.binding(lock, seal, finding)),
                Set.of(LockRecord.Kind.PRIMARY), log, ArmKeyGateFixtures.OPENED, "placement verification");
        return new Opened(packets, key, sidecar, seal, log, opened);
    }

    @Test void classBVectorExportsAndAuditsThroughLockedReveal() throws Exception {
        Path root = temp.resolve("b");
        BInput input = bInput(false);
        var exported = exportB(root, input);
        Path sidecar = PlacementSidecar.beside(root.resolve("key.jsonl"));
        var reveal = PlacementSidecar.load(sidecar);
        assertThat(reveal.groups()).containsExactly(new PlacementSidecar.Group("STUDY_TIP",
                input.fixtures().stream().map(ClassBCaseManifest.Case::fixtureId).toList()));
        assertThat(reveal.javaVersion()).isEqualTo(System.getProperty("java.version"));
        try {
            assertThat(Files.getPosixFilePermissions(sidecar)).containsExactlyInAnyOrder(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
        } catch (UnsupportedOperationException ignored) { /* Non-POSIX platform. */ }
        assertThat(Files.exists(root.resolve("packets/key.jsonl.placement.json"))).isFalse();
        assertThat(exported.armKey().stream().filter(row -> row.arm() == ClassBScoreAggregator.Arm.BASELINE)
                .map(ClassBScoreAggregator.Key::caseId)).containsExactlyInAnyOrderElementsOf(
                        input.fixtures().stream().map(ClassBCaseManifest.Case::fixtureId).toList());
        List<String> expectedOrder = List.of("case-1", "case-8", "case-6", "case-3", "case-5",
                "case-4", "case-9", "case-2", "case-7", "case-0");
        List<ClassBScoreAggregator.Arm> expectedA = List.of(ClassBScoreAggregator.Arm.CANDIDATE,
                ClassBScoreAggregator.Arm.BASELINE, ClassBScoreAggregator.Arm.BASELINE,
                ClassBScoreAggregator.Arm.BASELINE, ClassBScoreAggregator.Arm.BASELINE,
                ClassBScoreAggregator.Arm.CANDIDATE, ClassBScoreAggregator.Arm.BASELINE,
                ClassBScoreAggregator.Arm.BASELINE, ClassBScoreAggregator.Arm.BASELINE,
                ClassBScoreAggregator.Arm.BASELINE);
        List<String> actualOrder = new ArrayList<>();
        List<ClassBScoreAggregator.Arm> actualA = new ArrayList<>();
        for (int i = 0; i < exported.armKey().size(); i += 2) {
            actualOrder.add(exported.armKey().get(i).caseId());
            actualA.add(exported.armKey().get(i).arm());
        }
        assertThat(actualOrder).containsExactlyElementsOf(expectedOrder);
        assertThat(actualA).containsExactlyElementsOf(expectedA);
        List<String> packetLines = Files.readAllLines(root.resolve("packets/STUDY_TIP.jsonl"));
        for (int i = 0; i < packetLines.size(); i++) {
            JsonNode line = IntegritySupport.JSON.readTree(packetLines.get(i));
            assertThat(line.path("outputs").get(0).path("itemId").asText())
                    .isEqualTo(exported.armKey().get(i * 2).itemId());
        }
        Opened opened = sealLockOpen(root);
        assertThat(opened.opened().placement().exportId()).isEqualTo(EXPORT_B);
        assertThat(ArmKeyAccess.validateAccessLog(opened.log())).hasSize(1);
        var report = PlacementAudit.run(opened.packets(), opened.seal(), null, opened.opened(), ALPHA,
                COMMITMENT, EXPORT_B, Map.of("STUDY_TIP", Set.copyOf(expectedOrder)));
        assertThat(report.seedCheck()).isEqualTo("VERIFIED");
        assertThat(report.commitmentAnchor()).isEqualTo("CALLER_SUPPLIED");
        assertThat(PlacementAudit.run(opened.packets(), opened.seal(), null, opened.opened(), ALPHA,
                null, EXPORT_B, Map.of()).seedCheck()).isEqualTo("COMMITMENT_NOT_PROVIDED");
        String secret = PlacementSecretTest.fixed().hex();
        String decimal = Long.toString(PlacementSecretTest.fixed().seedLong());
        String hexSeed = Long.toHexString(PlacementSecretTest.fixed().seedLong());
        for (Path path : List.of(opened.packets().resolve("STUDY_TIP.jsonl"), opened.key(), opened.seal(),
                IntegritySupport.detached(opened.seal()), opened.log()))
            assertThat(Files.readString(path)).doesNotContain(secret, decimal, hexSeed);
        for (String text : List.of(exported.toString(), reveal.toString(), opened.opened().placement().toString(),
                report.toString(), IntegritySupport.JSON.writeValueAsString(report)))
            assertThat(text).doesNotContain(secret, decimal, hexSeed);
        assertThat(report.toString()).doesNotContain("case-", "blind-");
        assertThat(IntegritySupport.JSON.writeValueAsString(report)).doesNotContain("case-", "blind-");
        assertThat(Files.readString(opened.seal())).contains(COMMITMENT, EXPORT_B)
                .doesNotContain(secret, decimal, hexSeed);
    }

    @Test void preflightRefusalsWriteNothingAndWriterFailureRollsBack() throws Exception {
        BInput primary = bInput(false), reserve = bInput(true);
        Path root = temp.resolve("wrong");
        PlacementSecret fixed = PlacementSecretTest.fixed();
        String wrongCommitment = "0".repeat(64);
        assertThatThrownBy(() -> bAttempt(primary, root, "key.jsonl", EXPORT_B, fixed, wrongCommitment))
                .isInstanceOf(InvalidPlacementSeal.class);
        assertThat(Files.exists(root)).isFalse();
        Path symlinkRoot = temp.resolve("symlink");
        Files.createDirectories(symlinkRoot.resolve("packets"));
        Files.createSymbolicLink(symlinkRoot.resolve("private-alias"), symlinkRoot.resolve("packets"));
        assertThatThrownBy(() -> bAttempt(primary, symlinkRoot, "private-alias/key.jsonl", EXPORT_B,
                fixed, COMMITMENT)).isInstanceOf(InvalidPlacementSeal.class);
        try (var files = Files.list(symlinkRoot.resolve("packets"))) {
            assertThat(files.toList()).isEmpty();
        }
        assertThatThrownBy(() -> bAttempt(primary, root, "key.jsonl", EXPORT_B, fixed, null))
                .isInstanceOf(InvalidPlacementSeal.class);
        assertThatThrownBy(() -> bAttempt(primary, root, "key.jsonl", EXPORT_B, fixed, "bad"))
                .isInstanceOf(InvalidPlacementSeal.class);
        byte[] otherBytes = PlacementSecretTest.fixed().raw();
        otherBytes[0] ^= 1;
        PlacementSecret other = new PlacementSecret(otherBytes);
        assertThatThrownBy(() -> bAttempt(primary, root, "key.jsonl", EXPORT_B, other, COMMITMENT))
                .isInstanceOf(InvalidPlacementSeal.class).hasNoCause();
        assertThatThrownBy(() -> bAttempt(reserve, root, "key.jsonl", EXPORT_B, fixed, COMMITMENT))
                .isInstanceOf(InvalidPlacementSeal.class);
        String reserveId = "CLASS_B_RESERVE:STUDY_TIP";
        assertThatThrownBy(() -> bAttempt(primary, root, "key.jsonl", reserveId, fixed, COMMITMENT))
                .isInstanceOf(InvalidPlacementSeal.class);
        assertThat(Files.exists(root)).isFalse();
        AInput a = aInput(false, false);
        assertThatThrownBy(() -> aAttempt(a, root, "LUNA", "BASELINE_MINI", PlacementSidecar.FILE_WRITER))
                .isInstanceOf(InvalidPlacementSeal.class);
        AInput aReserve = aInput(true, false);
        assertThatThrownBy(() -> aAttempt(aReserve, root, "BASELINE_MINI", "LUNA",
                PlacementSidecar.FILE_WRITER)).isInstanceOf(InvalidPlacementSeal.class);
        assertThat(Files.exists(root)).isFalse();
        Path preexisting = PlacementSidecar.beside(root.resolve("key.jsonl"));
        Files.createDirectories(root);
        Files.writeString(preexisting, "occupied");
        assertThatThrownBy(() -> exportB(root, primary)).isInstanceOf(InvalidPlacementSeal.class);
        try (var files = Files.list(root)) {
            assertThat(files.toList()).containsExactly(preexisting);
        }
        Path fail = temp.resolve("writer-failure");
        PlacementSidecar.Writer writer = (sidecar, bytes) -> {
            Files.write(sidecar, bytes, StandardOpenOption.CREATE_NEW);
            throw new java.io.IOException("synthetic writer failure");
        };
        assertThatThrownBy(() -> bWriterAttempt(primary, fail, writer))
                .isInstanceOf(InvalidPlacementSeal.class).hasNoCause();
        assertThat(Files.exists(fail)).isFalse();
        AInput classA = aInput(false, false);
        Path failA = temp.resolve("writer-failure-a");
        assertThatThrownBy(() -> aAttempt(classA, failA, "BASELINE_MINI", "LUNA", writer))
                .isInstanceOf(InvalidPlacementSeal.class).hasNoCause();
        assertThat(Files.exists(failA)).isFalse();
        Path reserveRoot = temp.resolve("reserve");
        ClassBBlindPacketExport.export(reserve.fixtures(), reserve.outputs(), reserveRoot.resolve("packets"),
                reserveRoot.resolve("key.jsonl"), "CLASS_B_RESERVE:STUDY_TIP",
                PlacementSecretTest.fixed(), COMMITMENT);
        assertThat(PlacementSidecar.load(PlacementSidecar.beside(reserveRoot.resolve("key.jsonl"))).exportId())
                .isEqualTo("CLASS_B_RESERVE:STUDY_TIP");
    }

    @Test void classAExclusionSetAndLegacyOrderAreReproduced() throws Exception {
        AInput input = aInput(false, true);
        Path root = temp.resolve("a");
        var exported = exportA(root, input);
        var reveal = PlacementSidecar.load(PlacementSidecar.beside(exported.armKey()));
        assertThat(reveal.groups()).containsExactly(new PlacementSidecar.Group("NOTE_ADAPTIVE",
                input.fixtures().stream().map(ClassACaseManifest.Case::caseId).filter(id -> !id.equals("case-9"))
                        .toList()));
        assertThat(reveal.excluded()).containsExactly(new PlacementSidecar.Excluded("case-9", "SHORT_DELIVERY"));
        Path allExcludedRoot = temp.resolve("all-excluded");
        AInput one = aInput(false, true);
        var allExcluded = ClassABlindPacketExport.export(List.of(one.fixtures().get(9)),
                one.outputs().subList(18, 20), "BASELINE_MINI", "LUNA",
                allExcludedRoot.resolve("packets"), allExcludedRoot.resolve("key.jsonl"),
                EXPORT_A, PlacementSecretTest.fixed(), COMMITMENT);
        assertThat(allExcluded.packets()).isEmpty();
        assertThat(PlacementSidecar.load(PlacementSidecar.beside(allExcluded.armKey())).groups()).isEmpty();
        assertThat(PlacementSidecar.load(PlacementSidecar.beside(allExcluded.armKey())).excluded())
                .containsExactly(new PlacementSidecar.Excluded("case-9", "SHORT_DELIVERY"));
        Opened opened = sealLockOpen(root);
        Set<String> frozen = input.fixtures().stream().map(ClassACaseManifest.Case::caseId)
                .collect(java.util.stream.Collectors.toSet());
        var verified = PlacementAudit.run(opened.packets(), opened.seal(), "LUNA", opened.opened(), ALPHA,
                COMMITMENT, EXPORT_A, Map.of("NOTE_ADAPTIVE", frozen));
        assertThat(verified.seedCheck()).isEqualTo("VERIFIED");
        Set<String> dropped = new java.util.HashSet<>(frozen);
        dropped.remove("case-9");
        assertThat(PlacementAudit.run(opened.packets(), opened.seal(), "LUNA", opened.opened(), ALPHA,
                COMMITMENT, EXPORT_A, Map.of("NOTE_ADAPTIVE", dropped)).seedCheck()).isEqualTo("MISMATCH");
        Set<String> added = new java.util.HashSet<>(frozen);
        added.add("extra");
        assertThat(PlacementAudit.run(opened.packets(), opened.seal(), "LUNA", opened.opened(), ALPHA,
                COMMITMENT, EXPORT_A, Map.of("NOTE_ADAPTIVE", added)).seedCheck()).isEqualTo("MISMATCH");
        Path legacy = temp.resolve("legacy-a");
        ClassABlindPacketExport.export(aInput(false, false).fixtures(), aInput(false, false).outputs(),
                "BASELINE_MINI", "LUNA", legacy.resolve("packets"), legacy.resolve("key.jsonl"),
                PlacementSecretTest.fixed().seedLong());
        List<String> keyLines = Files.readAllLines(legacy.resolve("key.jsonl"));
        List<String> actualOrder = new ArrayList<>();
        for (int i = 0; i < keyLines.size(); i += 2)
            actualOrder.add(IntegritySupport.JSON.readTree(keyLines.get(i)).path("caseId").asText());
        List<String> ids = input.fixtures().stream().map(ClassACaseManifest.Case::caseId).toList();
        assertThat(PlacementPlan.order(ids, PlacementSecretTest.fixed().seedLong(), "NOTE_ADAPTIVE"))
                .containsExactlyElementsOf(actualOrder);
    }

    @Test void sidecarTamperFailsBeforeAccessLogAppendAndPartialSealFails() throws Exception {
        Path root = temp.resolve("tamper");
        exportB(root, bInput(false));
        Opened opened = sealLockOpen(root);
        byte[] before = Files.readAllBytes(opened.log());
        Files.writeString(opened.sidecar(), Files.readString(opened.sidecar()) + " ");
        Path finding = root.resolve("findings/human.jsonl");
        Path lock = root.resolve("primary-lock.json");
        List<ArmKeyAccess.LockBinding> bindings = List.of(ArmKeyGateFixtures.binding(lock, opened.seal(), finding));
        assertThatThrownBy(() -> reopen(opened, bindings))
                .isInstanceOf(ArmKeySeal.SealBroken.class).hasNoCause();
        assertThat(Files.readAllBytes(opened.log())).containsExactly(before);
        Files.delete(opened.sidecar());
        assertThatThrownBy(() -> reopen(opened, bindings))
                .isInstanceOf(ArmKeySeal.SealBroken.class).hasNoCause();
        assertThat(Files.readAllBytes(opened.log())).containsExactly(before);
        ArmKeySeal.Seal good = ArmKeySeal.load(opened.seal());
        for (int parts : List.of(1, 2)) {
            ObjectNode partial = (ObjectNode) IntegritySupport.JSON.valueToTree(good);
            if (parts == 1) partial.remove("placementSidecarSha256");
            partial.remove("placementExportId");
            Path partialFile = temp.resolve("partial-" + parts + ".json");
            IntegritySupport.create(partialFile, IntegritySupport.JSON.writeValueAsBytes(partial));
            assertThatThrownBy(() -> ArmKeySeal.load(partialFile)).isInstanceOf(ArmKeySeal.SealBroken.class);
        }
    }

    @Test void packetOrderAndPlacementTamperYieldCountsOnlyMismatchAfterReseal() throws Exception {
        for (boolean swapLines : List.of(false, true)) {
            Path root = temp.resolve(swapLines ? "line-order" : "placement");
            exportB(root, bInput(false));
            Path packet = root.resolve("packets/STUDY_TIP.jsonl");
            List<String> lines = new ArrayList<>(Files.readAllLines(packet));
            // These adjacent cases have the same first-arm bit. Only the order check can catch the swap.
            if (swapLines) java.util.Collections.swap(lines, 1, 2);
            else {
                ObjectNode node = (ObjectNode) IntegritySupport.JSON.readTree(lines.getFirst());
                for (JsonNode output : node.path("outputs")) {
                    ObjectNode shown = (ObjectNode) output;
                    shown.put("placement", "A".equals(shown.path("placement").asText()) ? "B" : "A");
                }
                lines.set(0, node.toString());
            }
            Files.writeString(packet, String.join("\n", lines) + "\n");
            Opened opened = sealLockOpen(root);
            var report = PlacementAudit.run(opened.packets(), opened.seal(), null, opened.opened(), ALPHA,
                    COMMITMENT, EXPORT_B, Map.of("STUDY_TIP", Set.copyOf(
                            bInput(false).fixtures().stream().map(ClassBCaseManifest.Case::fixtureId).toList())));
            assertThat(report.seedCheck()).isEqualTo("MISMATCH");
            assertThat(report.seedReason()).matches("Placement mismatch count=[0-9]+");
            assertThat(report.toString()).doesNotContain("case-", "blind-", PlacementSecretTest.fixed().hex());
        }
    }

    @Test void forgedSidecarCommitmentAndExportIdFailBeforeLogAppend() throws Exception {
        for (boolean changeCommitment : List.of(true, false)) {
            Path root = temp.resolve(changeCommitment ? "forged-commitment" : "forged-export-id");
            exportB(root, bInput(false));
            Path key = root.resolve("key.jsonl");
            Path sidecar = PlacementSidecar.beside(key);
            Path seal = root.resolve("seal.json");
            ArmKeySeal.seal(key, root.resolve("packets"), seal, ArmKeyGateFixtures.SEALED, sidecar);
            ObjectNode privateRow = (ObjectNode) IntegritySupport.JSON.readTree(Files.readString(sidecar));
            if (changeCommitment) {
                byte[] otherBytes = PlacementSecretTest.fixed().raw();
                otherBytes[0] ^= 1;
                PlacementSecret other = new PlacementSecret(otherBytes);
                privateRow.put("secret", other.hex()).put("commitment", other.commitment());
            } else privateRow.put("exportId", "CLASS_B_RESERVE:STUDY_TIP");
            Files.writeString(sidecar, privateRow.toString());
            ObjectNode publicRow = (ObjectNode) IntegritySupport.JSON.readTree(Files.readString(seal));
            publicRow.put("placementSidecarSha256", FreezeRecord.sha256(sidecar));
            Files.writeString(seal, publicRow.toString());
            Files.writeString(IntegritySupport.detached(seal), FreezeRecord.sha256(seal) + "\n");
            Path finding = ArmKeyGateFixtures.findings(root);
            Path lock = ArmKeyGateFixtures.lock(root, seal, finding, LockRecord.Kind.PRIMARY);
            Path log = root.resolve("access.jsonl");
            List<ArmKeyAccess.LockBinding> bindings = List.of(ArmKeyGateFixtures.binding(lock, seal, finding));
            assertThatThrownBy(() -> reopen(key, seal, bindings, log))
                    .isInstanceOf(ArmKeySeal.SealBroken.class).hasNoCause();
            assertThat(Files.exists(log)).isFalse();
        }
    }

    @Test void strictSidecarParserRedactsContentAndNeverChainsParserErrors() throws Exception {
        Path root = temp.resolve("invalid-sidecar");
        exportB(root, bInput(false));
        Path sidecar = PlacementSidecar.beside(root.resolve("key.jsonl"));
        String secret = PlacementSecretTest.fixed().hex();
        String original = Files.readString(sidecar);
        for (String malformed : List.of(original.replaceFirst("\\\"schemaVersion\\\":1",
                "\"schemaVersion\":1,\"unexpected\":\"" + secret + "\""),
                "{\"secret\":\"" + secret + "\",bad-json")) {
            Files.writeString(sidecar, malformed);
            Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() -> PlacementSidecar.load(sidecar));
            assertThat(failure).isInstanceOf(InvalidPlacementSeal.class).hasNoCause();
            assertFailureRedacted(failure);
        }
    }

    @Test void existingSidecarCannotBeOverwrittenEvenByAWriterHook() throws Exception {
        Path root = temp.resolve("overwrite-sidecar");
        exportB(root, bInput(false));
        Path sidecar = PlacementSidecar.beside(root.resolve("key.jsonl"));
        byte[] before = Files.readAllBytes(sidecar);
        PlacementSidecar.Reveal reveal = PlacementSidecar.load(sidecar);
        PlacementSidecar.Writer overwrite = (path, bytes) -> Files.write(path, bytes,
                StandardOpenOption.TRUNCATE_EXISTING);
        assertThatThrownBy(() -> PlacementSidecar.write(sidecar, reveal, overwrite))
                .isInstanceOf(InvalidPlacementSeal.class).hasNoCause();
        assertThat(Files.readAllBytes(sidecar)).containsExactly(before);
    }

    @Test void failureCauseChainsNeverExposeSecretOrDerivedSeed() throws Exception {
        BInput input = bInput(false);
        Path root = temp.resolve("redacted-failure");
        PlacementSecret fixed = PlacementSecretTest.fixed();
        String wrongCommitment = "0".repeat(64);
        Throwable mismatch = org.assertj.core.api.Assertions.catchThrowable(() ->
                bAttempt(input, root, "key.jsonl", EXPORT_B, fixed, wrongCommitment));
        assertThat(mismatch).isInstanceOf(InvalidPlacementSeal.class);
        assertFailureRedacted(mismatch);
        exportB(root, input);
        Opened opened = sealLockOpen(root);
        Files.writeString(opened.sidecar(), Files.readString(opened.sidecar()) + " ");
        Path finding = root.resolve("findings/human.jsonl");
        Path lock = root.resolve("primary-lock.json");
        List<ArmKeyAccess.LockBinding> bindings = List.of(ArmKeyGateFixtures.binding(lock, opened.seal(), finding));
        Throwable revealFailure = org.assertj.core.api.Assertions.catchThrowable(() -> reopen(opened, bindings));
        assertThat(revealFailure).isInstanceOf(ArmKeySeal.SealBroken.class);
        assertFailureRedacted(revealFailure);
    }

    @Test void legacySealStillReportsUnverifiableWithoutAPlacementReveal() throws Exception {
        Path root = temp.resolve("legacy-no-placement");
        BlindReviewFixtures.classB(root, 1);
        Path key = root.resolve("arm-key.jsonl");
        Path seal = ArmKeyGateFixtures.seal(root, key);
        Path finding = ArmKeyGateFixtures.findings(root);
        Path lock = ArmKeyGateFixtures.lock(root, seal, finding, LockRecord.Kind.PRIMARY);
        Path log = root.resolve("access.jsonl");
        List<ArmKeyAccess.LockBinding> bindings = List.of(ArmKeyGateFixtures.binding(lock, seal, finding));
        ArmKeyAccess.OpenedKey opened = reopen(key, seal, bindings, log);
        var oldReport = PlacementAudit.run(root.resolve("packets"), seal, null, opened, ALPHA);
        var newReport = PlacementAudit.run(root.resolve("packets"), seal, null, opened, ALPHA,
                COMMITMENT, EXPORT_B, Map.of("STUDY_TIP", Set.of("case-0")));
        assertThat(oldReport.seedCheck()).isEqualTo("UNVERIFIABLE_NO_SEALED_SEED");
        assertThat(newReport.seedCheck()).isEqualTo("UNVERIFIABLE_NO_SEALED_SEED");
        assertThat(oldReport.commitmentAnchor()).isEqualTo("CALLER_SUPPLIED");
        assertThat(newReport.commitmentAnchor()).isEqualTo("CALLER_SUPPLIED");
        assertThat(opened.placement()).isNull();
    }

    private static void assertFailureRedacted(Throwable failure) {
        String secret = PlacementSecretTest.fixed().hex();
        String decimal = Long.toString(PlacementSecretTest.fixed().seedLong());
        String hexSeed = Long.toHexString(PlacementSecretTest.fixed().seedLong());
        for (Throwable cursor = failure; cursor != null; cursor = cursor.getCause())
            assertThat(cursor.getMessage()).doesNotContain(secret, decimal, hexSeed);
    }
}
