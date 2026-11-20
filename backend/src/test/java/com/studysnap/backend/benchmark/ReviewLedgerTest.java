package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewLedgerTest {
    private static final String INTERACTIVE = "INTERACTIVE_QUIZ";
    private static final String CRITIQUE = "CRITIQUE";
    private static final String STUDY_TIP = "STUDY_TIP";
    private static final String ASK = "ASK_COMPANION";
    private static final String INTERVIEW = "INTERVIEW_CRITIQUE";
    private static final String SUBJECT = "quality_1";
    private static final String ITEM_ONE = "item_1";
    private static final Set<FindingsLedger.ReviewSubject> NO_EXTRAS = Set.of();
    @TempDir Path temp;
    private static FindingsLedger old(ReviewRecordStoreTest.Fixture f) {
        return new FindingsLedger(f.root().resolve("safety"), f.root().resolve("disputes"), f.context());
    }
    private static ReviewRecordStoreTest.Fixture fixture(Path root) throws Exception {
        return ReviewRecordStoreTest.fixture(root);
    }
    private static void add(ReviewRecordStoreTest.Fixture f, String id, ReviewRecord.Kind kind,
                            String workflow, String subject, ReviewRecord.Outcome outcome,
                            ReviewRecord.SignerRole role) throws Exception {
        f.reviews().create(f.review(id, kind, workflow, subject, outcome, role));
    }
    private static void complete(ReviewRecordStoreTest.Fixture f, String id, String workflow) throws Exception {
        add(f, id, ReviewRecord.Kind.CLASS_B_WORKFLOW, workflow, workflow,
                ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER);
    }
    @Test void noReviewStoreIsUnknownAndLegacyBlockingIsUnchanged() throws Exception {
        var f = fixture(temp.resolve("case"));
        FindingsLedger legacy = old(f);
        assertThat(legacy.missingRequiredReviews(INTERACTIVE, Set.of())).isNull();
        assertThat(legacy.blocksBenchmarkPass(INTERACTIVE)).isFalse();
        assertThat(legacy.blocksProductionSwitch(INTERACTIVE)).isFalse();
        assertThat(legacy.headHashes()).containsOnlyKeys("safety", "disputes");
        assertThat(legacy.checklistLine(INTERACTIVE)).doesNotContain("review store head:");
        assertThatThrownBy(() -> legacy.verifyReviewPrefix(0, ReviewRecordStoreTest.DIGEST))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
    }
    @Test void requiredWorkflowsAreRouteScopedAndDerived() throws Exception {
        var f = fixture(temp.resolve("case"));
        FindingsLedger ledger = f.ledger();
        assertThat(ledger.missingRequiredReviews(CRITIQUE, Set.of()))
                .containsExactly("CLASS_B_WORKFLOW:ASK_COMPANION:ASK_COMPANION",
                        "CLASS_B_WORKFLOW:INTERVIEW_CRITIQUE:INTERVIEW_CRITIQUE");
        assertThat(ledger.missingRequiredReviews(INTERACTIVE, Set.of()))
                .containsExactly("CLASS_B_WORKFLOW:STUDY_TIP:STUDY_TIP");
        complete(f, "tip_1", STUDY_TIP);
        assertThat(ledger.missingRequiredReviews(INTERACTIVE, Set.of())).isEmpty();
        assertThat(ledger.missingRequiredReviews(CRITIQUE, Set.of())).hasSize(2);
        complete(f, "ask_1", ASK);
        assertThat(ledger.missingRequiredReviews(CRITIQUE, Set.of()))
                .containsExactly("CLASS_B_WORKFLOW:INTERVIEW_CRITIQUE:INTERVIEW_CRITIQUE");
        complete(f, "interview_1", INTERVIEW);
        assertThat(ledger.missingRequiredReviews(CRITIQUE, Set.of())).isEmpty();
    }
    @Test void latestRecordWinsAndProductUxConcernNeedsOwnerCompletion() throws Exception {
        var f = fixture(temp.resolve("case"));
        FindingsLedger ledger = f.ledger();
        var extra = new FindingsLedger.ReviewSubject(ReviewRecord.Kind.ABSOLUTE_QUALITY, STUDY_TIP, SUBJECT);
        Set<FindingsLedger.ReviewSubject> required = Set.of(extra);
        complete(f, "tip_1", STUDY_TIP);
        add(f, "quality_1", ReviewRecord.Kind.ABSOLUTE_QUALITY, STUDY_TIP, SUBJECT,
                ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER);
        assertThat(ledger.missingRequiredReviews(INTERACTIVE, required)).isEmpty();
        add(f, "quality_2", ReviewRecord.Kind.ABSOLUTE_QUALITY, STUDY_TIP, SUBJECT,
                ReviewRecord.Outcome.BLOCKING_CONCERN, ReviewRecord.SignerRole.PRODUCT_UX);
        assertThat(ledger.missingRequiredReviews(INTERACTIVE, required))
                .containsExactly("ABSOLUTE_QUALITY:STUDY_TIP:quality_1");
        add(f, "quality_3", ReviewRecord.Kind.ABSOLUTE_QUALITY, STUDY_TIP, SUBJECT,
                ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER);
        assertThat(ledger.missingRequiredReviews(INTERACTIVE, required))
                .containsExactly("ABSOLUTE_QUALITY:STUDY_TIP:quality_1");
        f.reviews().create(new ReviewRecord("quality_3_resolution", "2026-11-16T00:05:00Z",
                IntegritySupport.detachedHash(f.lock()), ReviewRecord.Kind.ABSOLUTE_QUALITY,
                STUDY_TIP, SUBJECT, ReviewRecord.Outcome.COMPLETE, "owner",
                ReviewRecord.SignerRole.OWNER, "b".repeat(64), "quality_2", ReviewRecord.ResolutionCode.FIXED));
        assertThat(ledger.missingRequiredReviews(INTERACTIVE, required)).isEmpty();
        add(f, "quality_4", ReviewRecord.Kind.ABSOLUTE_QUALITY, STUDY_TIP, SUBJECT,
                ReviewRecord.Outcome.BLOCKING_CONCERN, ReviewRecord.SignerRole.PRODUCT_UX);
        assertThat(ledger.missingRequiredReviews(INTERACTIVE, required)).containsExactly("ABSOLUTE_QUALITY:STUDY_TIP:quality_1");
    }
    @Test void ownerConcernRetractsAnOwnerCompletionUntilCompletedAgain() throws Exception {
        var f = fixture(temp.resolve("case"));
        FindingsLedger ledger = f.ledger();
        complete(f, "tip_1", STUDY_TIP);
        assertThat(ledger.missingRequiredReviews(INTERACTIVE, Set.of())).isEmpty();
        assertThat(ledger.blocksBenchmarkPass(INTERACTIVE)).isFalse();
        add(f, "tip_2", ReviewRecord.Kind.CLASS_B_WORKFLOW, STUDY_TIP, STUDY_TIP,
                ReviewRecord.Outcome.BLOCKING_CONCERN, ReviewRecord.SignerRole.OWNER);
        assertThat(ledger.missingRequiredReviews(INTERACTIVE, Set.of()))
                .containsExactly("CLASS_B_WORKFLOW:STUDY_TIP:STUDY_TIP");
        assertThat(ledger.blocksBenchmarkPass(INTERACTIVE)).isTrue();
        complete(f, "tip_3", STUDY_TIP);
        assertThat(ledger.missingRequiredReviews(INTERACTIVE, Set.of()))
                .containsExactly("CLASS_B_WORKFLOW:STUDY_TIP:STUDY_TIP");
        f.reviews().create(new ReviewRecord("tip_4", "2026-11-16T00:05:00Z",
                IntegritySupport.detachedHash(f.lock()), ReviewRecord.Kind.CLASS_B_WORKFLOW,
                STUDY_TIP, STUDY_TIP, ReviewRecord.Outcome.COMPLETE, "owner",
                ReviewRecord.SignerRole.OWNER, "b".repeat(64), "tip_2", ReviewRecord.ResolutionCode.FIXED));
        assertThat(ledger.missingRequiredReviews(INTERACTIVE, Set.of())).isEmpty();
    }
    @Test void extrasRequireExactOwnerCompleteAndRejectOtherKindsOrRoutes() throws Exception {
        var f = fixture(temp.resolve("case"));
        FindingsLedger ledger = f.ledger();
        complete(f, "tip_1", STUDY_TIP);
        var defect = new FindingsLedger.ReviewSubject(ReviewRecord.Kind.KEY_DEFECT, STUDY_TIP, ITEM_ONE);
        Set<FindingsLedger.ReviewSubject> required = Set.of(defect);
        assertThat(ledger.missingRequiredReviews(INTERACTIVE, required))
                .containsExactly("KEY_DEFECT:STUDY_TIP:item_1");
        add(f, "wrong_1", ReviewRecord.Kind.KEY_DEFECT, STUDY_TIP, "item_2",
                ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER);
        assertThat(ledger.missingRequiredReviews(INTERACTIVE, required)).containsExactly("KEY_DEFECT:STUDY_TIP:item_1");
        add(f, "defect_1", ReviewRecord.Kind.KEY_DEFECT, STUDY_TIP, ITEM_ONE,
                ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER);
        assertThat(ledger.missingRequiredReviews(INTERACTIVE, required)).isEmpty();
        Set<FindingsLedger.ReviewSubject> wrongKind = Set.of(new FindingsLedger.ReviewSubject(
                ReviewRecord.Kind.CLASS_B_WORKFLOW, STUDY_TIP, STUDY_TIP));
        assertThatThrownBy(() -> ledger.missingRequiredReviews(INTERACTIVE, wrongKind))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        Set<FindingsLedger.ReviewSubject> wrongRoute = Set.of(new FindingsLedger.ReviewSubject(
                ReviewRecord.Kind.KEY_DEFECT, ASK, ITEM_ONE));
        assertThatThrownBy(() -> ledger.missingRequiredReviews(INTERACTIVE, wrongRoute))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
    }
    @Test void frbDisabledAndUnknownRouteThrow() throws Exception {
        var f = fixture(temp.resolve("case"));
        FindingsLedger ledger = f.ledger();
        assertThatThrownBy(() -> ledger.missingRequiredReviews("UNKNOWN", NO_EXTRAS))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        var disabled = ReviewRecordStoreTest.fixture(temp.resolve("disabled"), false);
        FindingsLedger disabledLedger = disabled.ledger();
        PostLockContext context = disabled.context();
        PostLockContext.Verified verified = context.verify();
        assertThatThrownBy(() -> context.requiredClassBWorkflows(verified, INTERACTIVE))
                .isInstanceOf(PostLockStoreIndex.InvalidStore.class);
        assertThatThrownBy(() -> disabledLedger.missingRequiredReviews(INTERACTIVE, NO_EXTRAS))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
    }
    @Test void missingReviewBlocksBothGatesAndOpenFindingStillBlocks() throws Exception {
        var f = fixture(temp.resolve("case"));
        FindingsLedger ledger = f.ledger();
        assertThat(ledger.blocksBenchmarkPass(INTERACTIVE)).isTrue();
        assertThat(ledger.blocksProductionSwitch(INTERACTIVE)).isTrue();
        complete(f, "tip_1", STUDY_TIP);
        assertThat(ledger.blocksBenchmarkPass(INTERACTIVE)).isFalse();
        assertThat(ledger.blocksProductionSwitch(INTERACTIVE)).isFalse();
        // The safety store's own intact, open finding remains an independent block.
        // The fixture's already sealed packet item is recoverable from its packet map.
        String item = PairMap.fromPackets(f.context().packetDirectory()).pairs().getFirst().items().get("A").getFirst();
        SafetyFinding finding = new SafetyFinding("finding_1", ReviewRecordStoreTest.CREATED,
                IntegritySupport.detachedHash(f.lock()), item, "NOTE_ADAPTIVE", null,
                List.of(INTERACTIVE), "incorrect answer", "reviewer", SafetyFinding.Attribution.GENERATOR, false);
        f.safety().create(finding, null);
        assertThat(ledger.blocksBenchmarkPass(INTERACTIVE)).isTrue();
        assertThat(ledger.blocksProductionSwitch(INTERACTIVE)).isTrue();
    }
    @Test void headsChecklistTamperingAndPairwiseSeparation() throws Exception {
        var f = fixture(temp.resolve("case"));
        FindingsLedger ledger = f.ledger();
        assertThat(ledger.headHashes()).containsOnlyKeys("safety", "disputes", "reviews");
        assertThat(ledger.checklistLine(INTERACTIVE)).contains("\nreview store head: 0:");
        String head = ledger.headHashes().get("reviews");
        String[] parts = head.split(":");
        ledger.verifyReviewPrefix(Integer.parseInt(parts[0]), parts[1]);
        Path safetyDir = f.root().resolve("safety");
        Path disputeDir = f.root().resolve("disputes");
        Path reviewDir = f.root().resolve("reviews");
        PostLockContext context = f.context();
        assertThatThrownBy(() -> new FindingsLedger(safetyDir, disputeDir, safetyDir, context))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        assertThatThrownBy(() -> new FindingsLedger(safetyDir, disputeDir, disputeDir, context))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        assertThatThrownBy(() -> new FindingsLedger(reviewDir, reviewDir, disputeDir, context))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        Files.writeString(reviewDir.resolve("orphan.json"), "{}");
        assertThatThrownBy(() -> ledger.missingRequiredReviews(INTERACTIVE, NO_EXTRAS))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        assertThatThrownBy(ledger::headHashes).isInstanceOf(FindingsLedger.InvalidLedger.class);
    }
}
