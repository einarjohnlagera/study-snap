package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Direct provenance tests exercise refusals without invoking the results calculator. */
class FrbProvenanceTest {
    private static final String TIP = "STUDY_TIP";
    private static final String DECIDED = "2026-11-16T00:02:00Z";
    private static final String EARLIER = "2026-11-16T00:01:30Z";
    private static final String UNKNOWN_HASH = "0".repeat(64);
    @TempDir Path temp;

    private FrbPipelineTest.Fixture fixture(boolean drawn) throws Exception {
        FrbPipelineTest builder = new FrbPipelineTest();
        builder.temp = temp;
        return builder.fixture(drawn, 0);
    }

    private static ResultsPipeline.FrbInput input(FrbPipelineTest.Fixture fixture,
            FrbEvidenceBundle bundle, ResultsPipeline.ProvenanceLogs logs) {
        return new ResultsPipeline.FrbInput(bundle, fixture.frb().reserveRecordFile(),
                fixture.frb().verification(), logs);
    }

    private static ResultsPipeline.FrbRunInput run(ResultsPipeline.FrbRunInput old,
            Path parameters, Map<String, ResultsPipeline.ReserveArms> reserves) {
        return new ResultsPipeline.FrbRunInput(old.repoRoot(), old.freeze(), parameters, old.mapping(),
                old.manifests(), old.baselineMini(), old.baseline41(), old.candidate(), reserves,
                old.compatibilityReports(), old.headCommitTime(), old.ownerDispositions(), old.report(), old.synthetic());
    }

    private static FrbEvidenceBundle bundle(FrbEvidenceBundle old, Map<String, Integer> widths,
            Map<String, Boolean> decisions, Set<String> awaiting) {
        return new FrbEvidenceBundle(old.evidence(), widths, decisions, awaiting);
    }

    private static void rewriteLog(Path file, java.util.function.Consumer<ObjectNode> edit) throws Exception {
        ObjectNode node = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllLines(file).getFirst());
        edit.accept(node);
        Files.writeString(file, node + "\n");
    }

    private static ResultsPipeline.ProvenanceLogs primaries(ResultsPipeline.ProvenanceLogs old,
            List<ResultsPipeline.PrimaryExport> exports) {
        return new ResultsPipeline.ProvenanceLogs(exports, old.reserves());
    }

    @Test void verifiedFixtureContainsEveryNamedProvenanceHash() throws Exception {
        var fixture = fixture(true);
        var verified = FrbProvenance.verify(fixture.run(), fixture.frb());
        assertThat(verified.hashes()).containsKeys("reserveDecisionRecord", "classBPrimaryScores",
                "primary.0.lock", "primary.0.finding.human.jsonl", "primary.0.exclusions",
                "reserve.STUDY_TIP.forPrimary.lock", "reserve.STUDY_TIP.forReserve.lock",
                "reserve.STUDY_TIP.forReserve.finding.scores.jsonl");
    }

    @Test void tamperedDecisionRecordRefuses() throws Exception {
        var fixture = fixture(true);
        Files.writeString(fixture.frb().reserveRecordFile(), "{}\n");
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
    }

    @Test void decisionRebuildsValidatedScoresFromCurrentFile() throws Exception {
        var fixture = fixture(true);
        Path root = fixture.primaryScores().getParent().getParent();
        String candidateItem = null;
        for (String line : Files.readAllLines(root.resolve("arm-key.jsonl"))) {
            var row = IntegritySupport.JSON.readTree(line);
            if (row.path("arm").asText().equals("CANDIDATE")) candidateItem = row.path("itemId").asText();
        }
        List<String> changedScores = new ArrayList<>();
        for (String line : Files.readAllLines(fixture.primaryScores())) {
            ObjectNode row = (ObjectNode) IntegritySupport.JSON.readTree(line);
            if (row.path("itemId").asText().equals(candidateItem)) {
                row.put("critical", false).put("noCriticalMisleading", true).putNull("reasonCode");
            }
            changedScores.add(row.toString());
        }
        Files.write(fixture.primaryScores(), changedScores);
        Path lock = fixture.frb().verification().primaryLockFile();
        ObjectNode lockJson = (ObjectNode) IntegritySupport.JSON.readTree(lock.toFile());
        ((ObjectNode) lockJson.path("findings").get(0)).put("sha256", FreezeRecord.sha256(fixture.primaryScores()));
        Files.writeString(lock, lockJson.toString());
        Files.writeString(IntegritySupport.detached(lock), FreezeRecord.sha256(lock));
        Path record = fixture.frb().reserveRecordFile();
        ObjectNode recordJson = (ObjectNode) IntegritySupport.JSON.readTree(record.toFile());
        recordJson.put("findingsSha256", FreezeRecord.sha256(fixture.primaryScores()));
        recordJson.put("lockSha256", IntegritySupport.detachedHash(lock));
        Files.writeString(record, recordJson.toString());
        Files.writeString(IntegritySupport.detached(record), FreezeRecord.sha256(record));
        Path access = fixture.frb().provenance().primaries().getLast().accessLog();
        rewriteLog(access, node -> ((ArrayNode) node.get("lockSha256s")).set(0,
                IntegritySupport.JSON.getNodeFactory().textNode(recordJson.path("lockSha256").asText())));
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("locked primary evidence");
    }

    @Test void bundleDecisionMismatchRefuses() throws Exception {
        var fixture = fixture(true);
        var old = fixture.frb().bundle();
        var changed = bundle(old, old.requestedQuestionsByCaseId(), Map.of(TIP, false), Set.of());
        var input = input(fixture, changed, fixture.frb().provenance());
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), input))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("decisions");
    }

    @Test void awaitingReserveRefusesWithWorkflowName() throws Exception {
        var fixture = fixture(true);
        var old = fixture.frb().bundle();
        var changed = bundle(old, old.requestedQuestionsByCaseId(), old.reserveDrawnByWorkflow(), Set.of(TIP));
        var input = input(fixture, changed, fixture.frb().provenance());
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), input))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining(TIP);
    }

    @Test void equalDecisionAndPrimaryOpenTimeRefuses() throws Exception {
        var fixture = fixture(false);
        Path log = fixture.frb().provenance().primaries().getFirst().accessLog();
        rewriteLog(log, node -> node.put("openedAtUtc", DECIDED));
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("strictly precede");
    }

    @Test void laterDecisionThanPrimaryOpenRefuses() throws Exception {
        var fixture = fixture(false);
        Path log = fixture.frb().provenance().primaries().getFirst().accessLog();
        rewriteLog(log, node -> node.put("openedAtUtc", EARLIER));
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("strictly precede");
    }

    @Test void minimumAcrossAllEntriesControlsDecisionOrder() throws Exception {
        var fixture = fixture(false);
        Path log = fixture.frb().provenance().primaries().getLast().accessLog();
        ObjectNode laterEntry = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllLines(log).getFirst());
        laterEntry.put("openedAtUtc", DECIDED);
        ArmKeyAccess.append(log, laterEntry);
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("strictly precede");
    }

    @Test void primaryLogRequiresReserveKindAndEachDrawnLockHash() throws Exception {
        var fixture = fixture(true);
        Path log = fixture.frb().provenance().primaries().getFirst().accessLog();
        rewriteLog(log, node -> ((ArrayNode) node.get("requiredKinds")).remove(1));
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("RESERVE kind");
    }

    @Test void primaryLogMissingDrawnReserveLockHashRefuses() throws Exception {
        var fixture = fixture(true);
        Path log = fixture.frb().provenance().primaries().getFirst().accessLog();
        rewriteLog(log, node -> ((ArrayNode) node.get("lockSha256s")).remove(1));
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("RESERVE kind");
    }

    @Test void reserveKeyLogRequiresOwnPrimaryLockHash() throws Exception {
        var fixture = fixture(true);
        Path log = fixture.frb().provenance().reserves().get(TIP).reserveKeyAccessLog();
        String otherSuppliedLock = IntegritySupport.detachedHash(
                fixture.frb().provenance().reserves().get(TIP).lockForPrimaryOpening());
        rewriteLog(log, node -> ((ArrayNode) node.get("lockSha256s")).set(0,
                IntegritySupport.JSON.getNodeFactory().textNode(otherSuppliedLock)));
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("Reserve key opening");
    }

    @Test void reserveKeyLogRequiresItsSealedArmHash() throws Exception {
        var fixture = fixture(true);
        Path log = fixture.frb().provenance().reserves().get(TIP).reserveKeyAccessLog();
        rewriteLog(log, node -> node.put("armKeySha256", UNKNOWN_HASH));
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("arm-key hash");
    }

    @Test void undrawnReserveLogOrInputRefuses() throws Exception {
        var fixture = fixture(false);
        ResultsPipeline.PrimaryExport primary = fixture.frb().provenance().primaries().getLast();
        var fake = new ResultsPipeline.ReserveExport(TIP, primary.accessLog(), primary.sealFile(),
                primary.lockFile(), primary.lockFile(), primary.findingsDirectory());
        var logs = new ResultsPipeline.ProvenanceLogs(fixture.frb().provenance().primaries(), Map.of(TIP, fake));
        var changed = input(fixture, fixture.frb().bundle(), logs);
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), changed))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("drawn workflows");
        var arms = new ResultsPipeline.ReserveArms(fixture.run().baseline41(), fixture.run().candidate());
        var badRun = run(fixture.run(), fixture.run().parameters(), Map.of(TIP, arms));
        assertThatThrownBy(() -> FrbProvenance.verify(badRun, fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("drawn workflows");
    }

    @Test void classAReserveInputRefusesEvenWhenClassBDrawExists() throws Exception {
        var fixture = fixture(true);
        var arms = fixture.run().reserves().get(TIP);
        Map<String, ResultsPipeline.ReserveArms> inputs = new LinkedHashMap<>(fixture.run().reserves());
        inputs.put("NOTE_ADAPTIVE", arms);
        var changed = run(fixture.run(), fixture.run().parameters(), inputs);
        assertThatThrownBy(() -> FrbProvenance.verify(changed, fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("drawn workflows");
    }

    @Test void drawnReserveNeedsFactsForEveryFrozenReserveCase() throws Exception {
        var fixture = fixture(true);
        var old = fixture.frb().bundle();
        Map<String, AdjudicatedFacts> facts = new LinkedHashMap<>(old.evidence().byCaseArm());
        facts.remove("reserve-0\u0000BASELINE_41");
        facts.remove("reserve-0\u0000LUNA");
        AdjudicatedLoaded reduced = new AdjudicatedLoaded(facts, old.evidence().excludedCaseIds(),
                old.evidence().requestedQuestionsByCaseId());
        FrbEvidenceBundle changedBundle = new FrbEvidenceBundle(reduced,
                old.requestedQuestionsByCaseId(), old.reserveDrawnByWorkflow(), Set.of());
        var changed = input(fixture, changedBundle, fixture.frb().provenance());
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), changed))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("facts differ");
    }

    @Test void classAFindingsChangedAfterLockRefuses() throws Exception {
        var fixture = fixture(false);
        Path finding = fixture.frb().provenance().primaries().getFirst().findingsDirectory().resolve("human.jsonl");
        Files.writeString(finding, "changed\n");
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
    }

    @Test void primaryScoresCannotGainReserveFileInSameDirectory() throws Exception {
        var fixture = fixture(true);
        Files.writeString(fixture.primaryFindings().resolve("reserve-scores.jsonl"), "{}\n");
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
    }

    @Test void frozenCaseIdsAndWidthsMustEqualBundle() throws Exception {
        var fixture = fixture(false);
        var old = fixture.frb().bundle();
        Map<String, Integer> widths = new LinkedHashMap<>(old.requestedQuestionsByCaseId());
        widths.remove("a-one");
        var changed = bundle(old, widths, old.reserveDrawnByWorkflow(), Set.of());
        var input = input(fixture, changed, fixture.frb().provenance());
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), input))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("frozen manifests");
    }

    @Test void primaryLogWithNoMatchingSealRefuses() throws Exception {
        var fixture = fixture(false);
        Path log = fixture.frb().provenance().primaries().getFirst().accessLog();
        rewriteLog(log, node -> node.put("armKeySha256", UNKNOWN_HASH));
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("no supplied seal");
    }

    @Test void accessLogCannotNameUnsuppliedLock() throws Exception {
        var fixture = fixture(false);
        Path log = fixture.frb().provenance().primaries().getFirst().accessLog();
        rewriteLog(log, node -> ((ArrayNode) node.get("lockSha256s")).add(UNKNOWN_HASH));
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("unsupplied lock");
    }

    @Test void reserveLocksMustNameSameWorkflows() throws Exception {
        var fixture = fixture(true);
        Path lock = fixture.frb().provenance().reserves().get(TIP).lockForReserveOpening();
        ObjectNode node = (ObjectNode) IntegritySupport.JSON.readTree(lock.toFile());
        ArrayNode workflows = (ArrayNode) node.get("workflows");
        workflows.removeAll();
        workflows.add("OTHER");
        Files.writeString(lock, node.toString());
        Files.writeString(IntegritySupport.detached(lock), FreezeRecord.sha256(lock));
        Path access = fixture.frb().provenance().reserves().get(TIP).reserveKeyAccessLog();
        String hash = IntegritySupport.detachedHash(lock);
        rewriteLog(access, entry -> ((ArrayNode) entry.get("lockSha256s")).set(0,
                IntegritySupport.JSON.getNodeFactory().textNode(hash)));
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("workflows or findings");
    }

    @Test void decisionMustPrecedeBothReserveLocks() throws Exception {
        var fixture = fixture(true);
        Path lock = fixture.frb().provenance().reserves().get(TIP).lockForPrimaryOpening();
        ObjectNode node = (ObjectNode) IntegritySupport.JSON.readTree(lock.toFile());
        node.put("lockedAtUtc", DECIDED);
        Files.writeString(lock, node.toString());
        Files.writeString(IntegritySupport.detached(lock), FreezeRecord.sha256(lock));
        String hash = IntegritySupport.detachedHash(lock);
        for (ResultsPipeline.PrimaryExport export : fixture.frb().provenance().primaries())
            rewriteLog(export.accessLog(), entry -> ((ArrayNode) entry.get("lockSha256s")).set(1,
                    IntegritySupport.JSON.getNodeFactory().textNode(hash)));
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("out of order");
    }

    @Test void reserveLockMustPrecedeFirstPrimaryOpening() throws Exception {
        var fixture = fixture(true);
        Path lock = fixture.frb().provenance().reserves().get(TIP).lockForReserveOpening();
        ObjectNode node = (ObjectNode) IntegritySupport.JSON.readTree(lock.toFile());
        node.put("lockedAtUtc", "2026-11-16T00:05:00Z");
        Files.writeString(lock, node.toString());
        Files.writeString(IntegritySupport.detached(lock), FreezeRecord.sha256(lock));
        Path access = fixture.frb().provenance().reserves().get(TIP).reserveKeyAccessLog();
        String hash = IntegritySupport.detachedHash(lock);
        rewriteLog(access, entry -> ((ArrayNode) entry.get("lockSha256s")).set(0,
                IntegritySupport.JSON.getNodeFactory().textNode(hash)));
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("out of order");
    }

    @Test void bundleWidthMustMatchFrozenWidthEvenWhenCaseIdsMatch() throws Exception {
        var fixture = fixture(false);
        var old = fixture.frb().bundle();
        Map<String, Integer> widths = new LinkedHashMap<>(old.requestedQuestionsByCaseId());
        widths.put("a-one", 2);
        var changed = bundle(old, widths, old.reserveDrawnByWorkflow(), Set.of());
        var input = input(fixture, changed, fixture.frb().provenance());
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), input))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("widths");
    }

    @Test void exclusionsRequireFileAndCorrectExportHash() throws Exception {
        var fixture = fixture(false);
        List<ResultsPipeline.PrimaryExport> exports = new ArrayList<>(fixture.frb().provenance().primaries());
        ResultsPipeline.PrimaryExport first = exports.getFirst();
        exports.set(0, new ResultsPipeline.PrimaryExport(first.accessLog(), first.sealFile(), first.lockFile(),
                first.findingsDirectory(), first.exclusionsFileOrNull(), null));
        var changed = input(fixture, fixture.frb().bundle(), primaries(fixture.frb().provenance(), exports));
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), changed))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("supplied together");
        Files.writeString(first.exclusionsFileOrNull(), "changed\n");
        assertThatThrownBy(() -> FrbProvenance.verify(fixture.run(), fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("Exclusions hash");
    }

    @Test void legacyParametersRefuseBeforeProvenance() throws Exception {
        var fixture = fixture(false);
        Path legacy = temp.resolve("legacy-parameters.json");
        Files.copy(GateTestFixture.SOURCE, legacy);
        Files.writeString(IntegritySupport.detached(legacy), FreezeRecord.sha256(legacy));
        var changed = run(fixture.run(), legacy, fixture.run().reserves());
        assertThatThrownBy(() -> ResultsPipeline.runFrb(changed, fixture.frb()))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessage("FRB parameters are required");
    }
}
