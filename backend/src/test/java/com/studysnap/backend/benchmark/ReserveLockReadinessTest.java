package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.dto.QuickReviewIncorrectQuestionRequest;
import com.studysnap.backend.entity.LearnerLevel;
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

class ReserveLockReadinessTest {
    private static final String TIP = "STUDY_TIP";
    private static final String HUMAN_OWNER = "HUMAN_OWNER";
    private static final String HUMAN_REVIEWER = "HUMAN_REVIEWER";
    private static final String MODEL_REVIEWER = "MODEL_REVIEWER";
    private static final String SCORES_NAME = "scores.jsonl";
    private static final String BORDERLINE_FIELD = "borderline";
    private static final String SEALED = "2026-11-16T00:03:00Z";
    private static final String LOCKED = "2026-11-16T00:04:00Z";
    private static final Map<String, ClassBScoreFiles.ScorerKind> SCORERS = Map.of(
            HUMAN_OWNER, ClassBScoreFiles.ScorerKind.HUMAN,
            HUMAN_REVIEWER, ClassBScoreFiles.ScorerKind.HUMAN,
            MODEL_REVIEWER, ClassBScoreFiles.ScorerKind.MODEL);
    @TempDir Path temp;

    private record Fixture(Path packets, Path scores, Path sealFile, ArmKeySeal.Seal seal,
            List<ClassBScoreFiles.BlindItem> manifest) {}

    private Fixture fixture() throws Exception {
        return fixture(true, temp);
    }

    private Fixture fixture(boolean reserve, Path root) throws Exception {
        Files.createDirectories(root);
        List<ClassBCaseManifest.Case> cases = new ArrayList<>();
        List<ClassBBlindPacketExport.Output> outputs = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            String id = "reserve-" + index;
            cases.add(new ClassBCaseManifest.Case(id, ClassBCaseManifest.Workflow.STUDY_TIP,
                    "synthetic", reserve, LearnerLevel.COLLEGE,
                    List.of(new QuickReviewIncorrectQuestionRequest("Question?", "Answer", "Explanation.")),
                    null, null, null, null, null, null, null, null));
            outputs.add(new ClassBBlindPacketExport.Output(id, ClassBScoreAggregator.Arm.BASELINE, "Answer one."));
            outputs.add(new ClassBBlindPacketExport.Output(id, ClassBScoreAggregator.Arm.CANDIDATE, "Answer two."));
        }
        Path packets = root.resolve("packets");
        Path syntheticSealInput = root.resolve("synthetic-seal-input.jsonl");
        var export = ClassBBlindPacketExport.export(cases, outputs, packets, syntheticSealInput, 19L);
        Path sealFile = root.resolve("seal.json");
        ArmKeySeal.Seal seal = ArmKeySeal.seal(syntheticSealInput, packets, sealFile, SEALED);
        Files.delete(syntheticSealInput);
        return new Fixture(packets, root.resolve("findings").resolve(SCORES_NAME), sealFile,
                seal, export.manifest());
    }

    private static ObjectNode row(ClassBScoreFiles.BlindItem item, String scorer, int round,
            boolean grounded, boolean adjudication) {
        ObjectNode result = IntegritySupport.JSON.createObjectNode();
        result.put("itemId", item.itemId()).put("workflow", item.workflow().name())
                .put("round", round).put("scorerId", scorer)
                .put("scorerKind", scorer.startsWith("HUMAN_") ? "HUMAN" : "MODEL")
                .put("taskFulfilled", true).put("grounded", grounded).put("actionable", true)
                .put("noCriticalMisleading", true).put("critical", false)
                .put(BORDERLINE_FIELD, false).put("adjudication", adjudication);
        if (grounded) result.putNull("reasonCode");
        else result.put("reasonCode", "UNGROUNDED");
        return result;
    }

    private ClassBScoreFiles.Validated validated(Fixture fixture, List<ObjectNode> rows) throws Exception {
        Files.createDirectories(fixture.scores().getParent());
        Files.write(fixture.scores(), rows.stream().map(ObjectNode::toString).toList());
        return ClassBScoreFiles.validate(fixture.scores(), fixture.manifest(), SCORERS);
    }

    private static List<ObjectNode> complete(List<ClassBScoreFiles.BlindItem> manifest) {
        return manifest.stream().map(item -> row(item, HUMAN_OWNER, 1, true, false)).toList();
    }

    private static void check(ClassBScoreFiles.Validated validated) {
        ReserveLockReadiness.check(validated, validated.manifest());
    }

    @Test void twoAgreeingHumanRowsResolveEveryReserveItem() throws Exception {
        Fixture fixture = fixture();
        List<ObjectNode> rows = new ArrayList<>(complete(fixture.manifest()));
        rows.getFirst().put(BORDERLINE_FIELD, true);
        fixture.manifest().forEach(item -> rows.add(row(item, HUMAN_REVIEWER, 1, true, false)));
        check(validated(fixture, rows));
    }

    @Test void oneLaterAdjudicationResolvesDisagreement() throws Exception {
        Fixture fixture = fixture();
        List<ObjectNode> rows = new ArrayList<>(complete(fixture.manifest()));
        ClassBScoreFiles.BlindItem first = fixture.manifest().getFirst();
        rows.add(row(first, HUMAN_REVIEWER, 1, false, false));
        rows.add(row(first, HUMAN_OWNER, 2, true, true));
        check(validated(fixture, rows));
    }

    @Test void noHumanRowAndModelOnlyRowAreUnscored() throws Exception {
        Fixture fixture = fixture();
        List<ObjectNode> rows = new ArrayList<>(complete(fixture.manifest()));
        ClassBScoreFiles.BlindItem absent = fixture.manifest().get(1);
        rows.remove(1);
        ClassBScoreFiles.Validated noHuman = validated(fixture, rows);
        assertThatThrownBy(() -> check(noHuman)).isInstanceOf(InvalidEvidence.class)
                .hasMessageContaining("unscored=1").hasMessageContaining(absent.itemId());
        rows.add(row(absent, MODEL_REVIEWER, 1, true, false));
        ClassBScoreFiles.Validated modelOnly = validated(fixture, rows);
        assertThatThrownBy(() -> check(modelOnly)).isInstanceOf(InvalidEvidence.class)
                .hasMessageContaining("unscored=1").hasMessageContaining(absent.itemId());
    }

    @Test void disagreementWithoutAdjudicationIsPending() throws Exception {
        Fixture fixture = fixture();
        List<ObjectNode> rows = new ArrayList<>(complete(fixture.manifest()));
        rows.add(row(fixture.manifest().getFirst(), HUMAN_REVIEWER, 1, false, false));
        ClassBScoreFiles.Validated pending = validated(fixture, rows);
        assertThatThrownBy(() -> check(pending)).isInstanceOf(InvalidEvidence.class)
                .hasMessageContaining("pending=1").hasMessageContaining(fixture.manifest().getFirst().itemId());
    }

    @Test void malformedAdjudicationPropagatesInvalidScore() throws Exception {
        Fixture fixture = fixture();
        List<ObjectNode> rows = new ArrayList<>(complete(fixture.manifest()));
        ClassBScoreFiles.BlindItem first = fixture.manifest().getFirst();
        rows.add(row(first, HUMAN_REVIEWER, 2, false, true));
        rows.add(row(first, HUMAN_OWNER, 3, true, true));
        ClassBScoreFiles.Validated two = validated(fixture, rows);
        assertThatThrownBy(() -> check(two)).isInstanceOf(ClassBScoreFiles.InvalidScore.class)
                .hasMessageContaining("Multiple adjudications");
        rows.remove(5);
        rows.set(0, row(first, HUMAN_OWNER, 2, true, false));
        ClassBScoreFiles.Validated notLater = validated(fixture, rows);
        assertThatThrownBy(() -> check(notLater)).isInstanceOf(ClassBScoreFiles.InvalidScore.class)
                .hasMessageContaining("Adjudication must be later");
    }

    @Test void emptyNonReserveAndMixedWorkflowManifestsAreRefused() throws Exception {
        Fixture fixture = fixture();
        ClassBScoreFiles.Validated full = validated(fixture, complete(fixture.manifest()));
        List<ClassBScoreFiles.BlindItem> empty = List.of();
        ClassBScoreFiles.Validated emptyScores = new ClassBScoreFiles.Validated(empty, List.of());
        assertThatThrownBy(() -> ReserveLockReadiness.check(emptyScores, empty))
                .isInstanceOf(InvalidEvidence.class);
        ClassBScoreFiles.BlindItem first = fixture.manifest().getFirst();
        List<ClassBScoreFiles.BlindItem> primary = List.of(new ClassBScoreFiles.BlindItem(
                first.itemId(), first.workflow(), false));
        ClassBScoreFiles.Validated primaryScores = new ClassBScoreFiles.Validated(primary, full.scores());
        assertThatThrownBy(() -> ReserveLockReadiness.check(primaryScores, primary))
                .isInstanceOf(InvalidEvidence.class);
        List<ClassBScoreFiles.BlindItem> mixed = List.of(first, new ClassBScoreFiles.BlindItem(
                fixture.manifest().get(1).itemId(), ClassBCaseManifest.Workflow.ASK_COMPANION, true));
        ClassBScoreFiles.Validated mixedScores = new ClassBScoreFiles.Validated(mixed, full.scores());
        assertThatThrownBy(() -> ReserveLockReadiness.check(mixedScores, mixed))
                .isInstanceOf(InvalidEvidence.class);
        check(full);
    }

    @Test void bothPlacementsOfEveryPairNeedFinalHumanStatus() throws Exception {
        Fixture fixture = fixture();
        for (int placement = 0; placement < 2; placement++) {
            List<ObjectNode> rows = new ArrayList<>(complete(fixture.manifest()));
            ClassBScoreFiles.BlindItem missing = fixture.manifest().get(placement);
            rows.remove(placement);
            ClassBScoreFiles.Validated partial = validated(fixture, rows);
            assertThatThrownBy(() -> check(partial)).isInstanceOf(InvalidEvidence.class)
                    .hasMessageContaining(missing.itemId());
        }
    }

    @Test void messageSeparatesSortedUnscoredAndPendingWithoutArmNames() throws Exception {
        Fixture fixture = fixture();
        List<ClassBScoreFiles.BlindItem> manifest = fixture.manifest();
        List<ObjectNode> rows = new ArrayList<>();
        for (int index : List.of(2, 0)) {
            rows.add(row(manifest.get(index), HUMAN_OWNER, 1, true, false));
            rows.add(row(manifest.get(index), HUMAN_REVIEWER, 1, false, false));
        }
        ClassBScoreFiles.Validated partial = validated(fixture, rows);
        assertThatThrownBy(() -> check(partial)).isInstanceOf(InvalidEvidence.class)
                .hasMessageContaining("unscored=2 [" + manifest.get(1).itemId() + ", "
                        + manifest.get(3).itemId() + "]")
                .hasMessageContaining("pending=2 [" + manifest.get(0).itemId() + ", "
                        + manifest.get(2).itemId() + "]")
                .hasMessageNotContaining("BASELINE").hasMessageNotContaining("CANDIDATE")
                .hasMessageNotContaining("arm");
    }

    @Test void primaryValidatedWithCollidingBlindIdsCannotCertifyReserve() throws Exception {
        Fixture fixture = fixture();
        Fixture primary = fixture(false, temp.resolve("primary"));
        ClassBScoreFiles.Validated primaryValidated = validated(primary, complete(primary.manifest()));
        List<ClassBScoreFiles.BlindItem> reserveManifest = fixture.manifest();
        assertThat(primaryValidated.manifest().stream().map(ClassBScoreFiles.BlindItem::itemId).toList())
                .isEqualTo(reserveManifest.stream().map(ClassBScoreFiles.BlindItem::itemId).toList());
        assertThatThrownBy(() -> ReserveLockReadiness.check(primaryValidated, reserveManifest))
                .isInstanceOf(InvalidEvidence.class).hasMessageContaining("manifest differs");
    }

    @Test void validatorCreatesBothKindsOnlyWithCompleteScores() throws Exception {
        Fixture fixture = fixture();
        validated(fixture, complete(fixture.manifest()));
        LockRecord.FindingsValidator validator = ReserveLockReadiness.asValidator(fixture.packets(), SCORERS, SCORES_NAME);
        Path reserveLock = temp.resolve("reserve-lock.json");
        Path primaryLock = temp.resolve("primary-lock.json");
        LockRecord.create(reserveLock, LockRecord.Kind.RESERVE, Set.of(TIP), List.of(fixture.scores()),
                fixture.seal(), fixture.sealFile(), validator, LOCKED);
        LockRecord.create(primaryLock, LockRecord.Kind.PRIMARY, Set.of(TIP), List.of(fixture.scores()),
                fixture.seal(), fixture.sealFile(), validator, LOCKED);
        assertThat(LockRecord.load(reserveLock).kind()).isEqualTo(LockRecord.Kind.RESERVE);
        assertThat(LockRecord.load(primaryLock).kind()).isEqualTo(LockRecord.Kind.PRIMARY);
    }

    @Test void validatorRefusesIncompleteScoringBeforeAnyLockIsWritten() throws Exception {
        Fixture fixture = fixture();
        validated(fixture, List.of(row(fixture.manifest().getFirst(), HUMAN_OWNER, 1, true, false)));
        LockRecord.FindingsValidator validator = ReserveLockReadiness.asValidator(fixture.packets(), SCORERS, SCORES_NAME);
        Path lock = temp.resolve("reserve-lock.json");
        Set<String> workflows = Set.of(TIP);
        List<Path> findings = List.of(fixture.scores());
        ArmKeySeal.Seal seal = fixture.seal();
        Path sealFile = fixture.sealFile();
        assertThatThrownBy(() -> LockRecord.create(lock, LockRecord.Kind.RESERVE, workflows,
                findings, seal, sealFile, validator, LOCKED))
                .isInstanceOf(LockRecord.InvalidLock.class).hasCauseInstanceOf(InvalidEvidence.class);
        assertThat(Files.exists(lock)).isFalse();
        assertThat(Files.exists(IntegritySupport.detached(lock))).isFalse();
    }

    @Test void validatorRequiresExactlyOneNamedScoreFile() throws Exception {
        Fixture fixture = fixture();
        validated(fixture, complete(fixture.manifest()));
        LockRecord.FindingsValidator validator = ReserveLockReadiness.asValidator(fixture.packets(), SCORERS, SCORES_NAME);
        Path other = temp.resolve("findings/other.txt");
        Files.writeString(other, "review");
        List<Path> absent = List.of(other);
        assertThatThrownBy(() -> validator.validate(absent)).isInstanceOf(InvalidEvidence.class);
        Path duplicate = temp.resolve("other/scores.jsonl");
        Files.createDirectories(duplicate.getParent());
        Files.copy(fixture.scores(), duplicate);
        List<Path> duplicated = List.of(fixture.scores(), duplicate);
        assertThatThrownBy(() -> validator.validate(duplicated)).isInstanceOf(InvalidEvidence.class);
    }

    @Test void malformedScoreFilePropagatesInvalidScoreFromValidator() throws Exception {
        Fixture fixture = fixture();
        Files.createDirectories(fixture.scores().getParent());
        Files.writeString(fixture.scores(), "not-json\n");
        LockRecord.FindingsValidator validator = ReserveLockReadiness.asValidator(fixture.packets(), SCORERS, SCORES_NAME);
        List<Path> findings = List.of(fixture.scores());
        assertThatThrownBy(() -> validator.validate(findings)).isInstanceOf(ClassBScoreFiles.InvalidScore.class);
    }
}
