package com.studysnap.backend.benchmark;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic file-backed fixtures copied from ReviewCompositionTest and ProductionSwitchEligibilityTest. */
class OwnerAttestationTest {
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String OTHER_ROUTE = "CRITIQUE";
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String TIP = "STUDY_TIP";
    private static final String CREATED = "2026-11-16T00:07:00Z";
    private static final String LATER = "2026-11-16T00:08:00Z";
    private static final String GENESIS = "2026-11-16T00:05:00Z";
    private static final String EVIDENCE = "a".repeat(64);
    private static final String RESOLUTION = "b".repeat(64);
    private static final String FINDING = "finding_one";
    private static final String VETO = "veto_one";
    private static final String NO_VETO_NOTE = "no_veto_note";
    private static final String NO_VETO_TIP = "no_veto_tip";
    private static final String BASELINE_MINI = "BASELINE_MINI";
    private static final String BASELINE_41 = "BASELINE_41";
    private static final String OWNER = "owner";
    private static final String SAFETY = "safety";
    private static final String DISPUTES = "disputes";
    private static final String REVIEWS = "reviews";
    private static final String REVIEWER = "reviewer";
    private static final String INCORRECT_ANSWER = "Incorrect answer";
    private static final String NO_VETO_PREFIX = "NO_VETO:";
    private static final String SEPARATOR = ":";
    private static final String MISSING_COMPLETION = "Required review lacks a covering completion";
    private static final String UNKNOWN = "UNKNOWN";
    private static final String CLEAR_ONE = "clear_one";
    private static final String ASK_COMPANION = "ASK_COMPANION";
    private static final String VETO_TWO = "veto_two";
    private static final String NEWLINE = "\n";
    private static final String JSON_SUFFIX = ".json";
    private static final KeyDefectWorkload.Parameters WORKLOAD = new KeyDefectWorkload.Parameters(
            BigDecimal.valueOf(30), BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(4));
    private static final FindingsLedger.ReviewSubject NOTE_ATTESTATION = new FindingsLedger.ReviewSubject(
            ReviewRecord.Kind.NO_VETO, NOTE, ROUTE);
    private static final FindingsLedger.ReviewSubject TIP_ATTESTATION = new FindingsLedger.ReviewSubject(
            ReviewRecord.Kind.NO_VETO, TIP, ROUTE);
    @TempDir Path temp;

    private record Ledger(Path root, PostLockContext context, SafetyFindingStore safety,
            EvidenceDisputeStore disputes, ReviewRecordStore reviews, FindingsLedger ledger) {
        String hash() { return context.verify().lockSha256(); }
        FindingsLedger.ExternalHeads heads() {
            return new FindingsLedger.ExternalHeads(external(safety.headSha256()),
                    external(disputes.headSha256()), external(reviews.headSha256()));
        }
        FindingsLedger.ReviewCheck token(Set<FindingsLedger.ReviewSubject> extras) {
            return ledger.checkLedger(ROUTE, extras, heads());
        }
        ReviewRecord record(String id, ReviewRecord.Kind kind, String workflow, String subject,
                ReviewRecord.Outcome outcome, ReviewRecord.SignerRole role) {
            return new ReviewRecord(id, CREATED, hash(), kind, workflow, subject, outcome,
                    OWNER, role, EVIDENCE);
        }
        ReviewRecord complete(String id, FindingsLedger.ReviewSubject subject) {
            return reviews.create(record(id, subject.kind(), subject.workflow(), subject.subjectId(),
                    ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER));
        }
        ReviewRecord veto(String id, String subject) {
            return reviews.create(record(id, ReviewRecord.Kind.OWNER_VETO, NOTE, subject,
                    ReviewRecord.Outcome.BLOCKING_CONCERN, ReviewRecord.SignerRole.OWNER));
        }
        ReviewRecord clear(String id, String earlier, String workflow, String subject) {
            return reviews.create(new ReviewRecord(id, LATER, hash(), ReviewRecord.Kind.OWNER_VETO,
                    workflow, subject, ReviewRecord.Outcome.COMPLETE, OWNER,
                    ReviewRecord.SignerRole.OWNER, RESOLUTION, earlier, ReviewRecord.ResolutionCode.FIXED));
        }
        String firstItem() {
            return PairMap.fromPackets(context.packetDirectory()).blindItemIds().stream()
                    .sorted().findFirst().orElseThrow();
        }
        void finding(String id, List<String> routes) {
            safety.create(new SafetyFinding(id, CREATED, hash(), firstItem(), NOTE, null,
                    routes, INCORRECT_ANSWER, REVIEWER, SafetyFinding.Attribution.GENERATOR,
                    false), null);
        }
    }

    private record Harness(FrbPipelineTest.Fixture fixture, RouteVerdictCalculator.RouteReport report,
            List<Ledger> ledgers, Set<FindingsLedger.ReviewSubject> derived) {
        Set<FindingsLedger.ReviewSubject> allExtras() {
            Set<FindingsLedger.ReviewSubject> result = new HashSet<>(derived);
            result.add(NOTE_ATTESTATION);
            result.add(TIP_ATTESTATION);
            return Set.copyOf(result);
        }
        List<FindingsLedger.ReviewCheck> tokens(Set<FindingsLedger.ReviewSubject> extras) {
            List<FindingsLedger.ReviewCheck> result = new ArrayList<>();
            for (Ledger ledger : ledgers) result.add(ledger.token(extras));
            return result;
        }
        ReviewComposition.ComposedReviewCheck compose(boolean required,
                List<FindingsLedger.ReviewCheck> tokens) {
            String candidate = Stage1Receipt.read(fixture.run().candidate().receipt()).arm();
            return ReviewComposition.compose(ROUTE, fixture.run(), fixture.frb(), report, tokens,
                    WORKLOAD, Set.of(candidate), Set.of(BASELINE_MINI, BASELINE_41), required);
        }
        void attest() {
            ledgers.getFirst().complete(NO_VETO_NOTE, NOTE_ATTESTATION);
            ledgers.get(2).complete(NO_VETO_TIP, TIP_ATTESTATION);
        }
        Map<String, FindingsLedger> ledgerMap() {
            Map<String, FindingsLedger> result = new HashMap<>();
            for (Ledger item : ledgers) result.put(item.hash(), item.ledger());
            return result;
        }
        Map<String, FindingsLedger.ExternalHeads> heads() {
            Map<String, FindingsLedger.ExternalHeads> result = new HashMap<>();
            for (Ledger item : ledgers) result.put(item.hash(), item.heads());
            return result;
        }
    }
    private static FindingsLedger.ExternalHead external(PostLockStoreIndex.Head head) {
        return new FindingsLedger.ExternalHead(head.seq(), head.sha256());
    }
    private Harness harness() throws Exception {
        FrbPipelineTest builder = new FrbPipelineTest();
        builder.temp = temp;
        FrbPipelineTest.Fixture fixture = builder.fixture(true, 0, 1, true);
        String sha = FrozenGateParameters.load(fixture.run().parameters()).sha256;
        RouteVerdictCalculator.WorkflowReport row = new RouteVerdictCalculator.WorkflowReport(
                RouteVerdictCalculator.Verdict.PASS, Set.of(), Set.of(),
                1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false,
                Map.of(), Map.of(), Map.of(), sha, 0, Set.of(), null, null,
                null, null, null, null, null, null);
        RouteVerdictCalculator.RouteReport report = new RouteVerdictCalculator.RouteReport(
                RouteVerdictCalculator.Verdict.PASS, Set.of(), Set.of(), 0, false, sha,
                Map.of(NOTE, row, TIP, row));
        FrozenGateParameters parameters = FrozenGateParameters.load(fixture.run().parameters());
        Set<String> classA = new HashSet<>(parameters.workflows.keySet());
        classA.removeAll(parameters.frbClassBWorkflows);
        Set<FindingsLedger.ReviewSubject> derived = KeyDefectSubjects.derive(fixture.frb().bundle(),
                ResultsPipeline.workflowByCaseId(fixture.run()), classA, parameters.frbClassBWorkflows);
        List<Ledger> ledgers = new ArrayList<>();
        for (ResultsPipeline.PrimaryExport export : fixture.frb().provenance().primaries())
            ledgers.add(ledger(export.lockFile(), export.sealFile(), export.findingsDirectory(),
                    ledgers.size(), fixture));
        for (ResultsPipeline.ReserveExport export : fixture.frb().provenance().reserves().values()) {
            ledgers.add(ledger(export.lockForPrimaryOpening(), export.sealFile(),
                    export.findingsDirectory(), ledgers.size(), fixture));
            ledgers.add(ledger(export.lockForReserveOpening(), export.sealFile(),
                    export.findingsDirectory(), ledgers.size(), fixture));
        }
        ledgers.get(2).complete("class_b_complete", new FindingsLedger.ReviewSubject(
                ReviewRecord.Kind.CLASS_B_WORKFLOW, TIP, TIP));
        int number = 0;
        for (FindingsLedger.ReviewSubject subject : derived)
            ledgers.getFirst().complete("defect_complete_" + ++number, subject);
        return new Harness(fixture, report, List.copyOf(ledgers), derived);
    }
    private Ledger ledger(Path lock, Path seal, Path findings, int index,
            FrbPipelineTest.Fixture fixture) throws Exception {
        PostLockContext context = new PostLockContext(lock, findings,
                seal.getParent().resolve("packets"), seal, fixture.run().parameters());
        Path root = Files.createDirectories(temp.resolve("ledger-" + index));
        Path safety = root.resolve(SAFETY);
        Path disputes = root.resolve(DISPUTES);
        Path reviews = root.resolve(REVIEWS);
        return new Ledger(root, context, SafetyFindingStore.init(safety, context, GENESIS),
                EvidenceDisputeStore.init(disputes, context, GENESIS),
                ReviewRecordStore.init(reviews, context, GENESIS),
                new FindingsLedger(safety, disputes, reviews, context));
    }
    private static FrbClassificationInput input(ReviewComposition.ComposedReviewCheck check,
            Set<String> vetoes) {
        return new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false,
                false, vetoes, Map.of(NOTE, "NOT_TRIGGERED"), check.heads(), check);
    }
    private static ProductionSwitchEligibility.Decision check(Harness h,
            ReviewComposition.ComposedReviewCheck composed, Set<String> vetoes, boolean require) {
        return ProductionSwitchEligibility.checkWithOptions(ROUTE, h.report(), input(composed, vetoes),
                h.fixture().run(), h.fixture().frb(), h.ledgerMap(), h.heads(), Set.of(),
                new ProductionSwitchEligibility.SwitchOptions(null, false, require));
    }

    @Test void uppercaseNoVetoSubjectIsRequiredAndAcceptedAcrossTwoWorkflows() throws Exception {
        Harness h = harness();
        Ledger a = h.ledgers().getFirst();
        Set<FindingsLedger.ReviewSubject> extras = h.allExtras();
        assertThat(a.token(extras).missing()).contains(NO_VETO_PREFIX + NOTE + SEPARATOR + ROUTE,
                NO_VETO_PREFIX + TIP + SEPARATOR + ROUTE);
        assertThatThrownBy(() -> h.compose(true, h.tokens(extras)))
                .isInstanceOf(ReviewComposition.InvalidComposition.class)
                .hasMessageContaining(MISSING_COMPLETION);
        h.attest();
        assertThat(a.reviews().load(NO_VETO_NOTE).subjectId()).isEqualTo(ROUTE);
        assertThat(a.token(extras).missing()).doesNotContain(NO_VETO_PREFIX + NOTE + SEPARATOR + ROUTE);
        assertThat(h.compose(true, h.tokens(extras)).requiredKeyDefectSubjects())
                .containsExactlyInAnyOrderElementsOf(h.derived());
        Set<FindingsLedger.ReviewSubject> onlyFirstWorkflow = new HashSet<>(h.derived());
        onlyFirstWorkflow.add(NOTE_ATTESTATION);
        assertThatThrownBy(() -> h.compose(true, h.tokens(onlyFirstWorkflow)))
                .isInstanceOf(ReviewComposition.InvalidComposition.class);
        assertThatThrownBy(() -> h.compose(true, h.tokens(h.derived())))
                .isInstanceOf(ReviewComposition.InvalidComposition.class)
                .hasMessageContaining("omitted extras");
        List<FindingsLedger.ReviewCheck> oneOmitted = h.tokens(extras);
        Set<FindingsLedger.ReviewSubject> missingOneExtra = new HashSet<>(extras);
        missingOneExtra.remove(TIP_ATTESTATION);
        oneOmitted.set(1, h.ledgers().get(1).token(missingOneExtra));
        assertThatThrownBy(() -> h.compose(true, oneOmitted))
                .isInstanceOf(ReviewComposition.InvalidComposition.class);
    }

    @Test void invalidNoVetoFormsAreRefusedByStoreAndLedger() throws Exception {
        Harness h = harness();
        Ledger a = h.ledgers().getFirst();
        for (String subject : List.of("interactive_quiz", OTHER_ROUTE)) {
            ReviewRecord bad = a.record("bad_subject", ReviewRecord.Kind.NO_VETO, NOTE, subject,
                    ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER);
            assertThatThrownBy(() -> a.reviews().create(bad))
                    .isInstanceOf(ReviewRecordStore.InvalidStore.class);
        }
        ReviewRecord unknown = a.record("bad_workflow", ReviewRecord.Kind.NO_VETO, UNKNOWN, ROUTE,
                ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER);
        assertThatThrownBy(() -> a.reviews().create(unknown)).isInstanceOf(ReviewRecordStore.InvalidStore.class);
        ReviewRecord blocking = a.record("bad_outcome", ReviewRecord.Kind.NO_VETO, NOTE, ROUTE,
                ReviewRecord.Outcome.BLOCKING_CONCERN, ReviewRecord.SignerRole.OWNER);
        assertThatThrownBy(() -> a.reviews().create(blocking)).isInstanceOf(ReviewRecordStore.InvalidStore.class);
        ReviewRecord ux = a.record("bad_role", ReviewRecord.Kind.NO_VETO, NOTE, ROUTE,
                ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.PRODUCT_UX);
        assertThatThrownBy(() -> a.reviews().create(ux)).isInstanceOf(ReviewRecordStore.InvalidStore.class);
        ReviewRecord superseding = new ReviewRecord("bad_supersession", LATER, a.hash(),
                ReviewRecord.Kind.NO_VETO, NOTE, ROUTE, ReviewRecord.Outcome.COMPLETE, OWNER,
                ReviewRecord.SignerRole.OWNER, RESOLUTION, "prior", ReviewRecord.ResolutionCode.FIXED);
        assertThatThrownBy(() -> a.reviews().create(superseding))
                .isInstanceOf(ReviewRecordStore.InvalidStore.class);
        FindingsLedger.ReviewSubject wrong = new FindingsLedger.ReviewSubject(
                ReviewRecord.Kind.NO_VETO, NOTE, OTHER_ROUTE);
        assertThatThrownBy(() -> a.ledger().missingRequiredReviews(ROUTE, Set.of(wrong)))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
    }

    @Test void vetoRemainsOpenAfterNoVetoAndExplicitResolutionPreservesHistory() throws Exception {
        Harness h = harness();
        Ledger a = h.ledgers().getFirst();
        a.finding(FINDING, List.of(ROUTE));
        a.veto(VETO, FINDING);
        a.complete(NO_VETO_NOTE, NOTE_ATTESTATION);
        assertThat(a.ledger().openOwnerVetoFindingIds(ROUTE)).containsExactly(FINDING);
        assertThat(a.reviews().openConcerns()).containsExactly(VETO);
        assertThat(a.token(h.allExtras()).openConcernIds()).contains(VETO);
        assertThatThrownBy(() -> h.compose(false, h.tokens(h.allExtras())))
                .isInstanceOf(ReviewComposition.InvalidComposition.class);
        assertThatThrownBy(() -> h.compose(true, h.tokens(h.allExtras())))
                .isInstanceOf(ReviewComposition.InvalidComposition.class);
        ReviewRecord standalone = a.record("standalone", ReviewRecord.Kind.OWNER_VETO, NOTE, FINDING,
                ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER);
        assertThatThrownBy(() -> a.reviews().create(standalone))
                .isInstanceOf(ReviewRecordStore.InvalidStore.class);
        ReviewRecord sameDigest = new ReviewRecord("same_digest", LATER, a.hash(),
                ReviewRecord.Kind.OWNER_VETO, NOTE, FINDING, ReviewRecord.Outcome.COMPLETE,
                OWNER, ReviewRecord.SignerRole.OWNER, EVIDENCE, VETO, ReviewRecord.ResolutionCode.FIXED);
        assertThatThrownBy(() -> a.reviews().create(sameDigest))
                .isInstanceOf(ReviewRecordStore.InvalidStore.class);
        a.clear(CLEAR_ONE, VETO, NOTE, FINDING);
        assertThat(a.ledger().openOwnerVetoFindingIds(ROUTE)).isEmpty();
        assertThat(a.reviews().records()).extracting(ReviewRecord::reviewId)
                .contains(VETO, NO_VETO_NOTE, CLEAR_ONE);
        assertThatThrownBy(() -> a.clear("clear_twice", VETO, NOTE, FINDING))
                .isInstanceOf(ReviewRecordStore.InvalidStore.class);
        a.veto("veto_again", FINDING);
        assertThat(a.ledger().openOwnerVetoFindingIds(ROUTE)).containsExactly(FINDING);
    }

    @Test void invalidVetoReferenceFailsEveryRouteAndIntegrityNeverReturnsClean() throws Exception {
        Harness h = harness();
        Ledger a = h.ledgers().getFirst();
        a.veto(VETO, "missing_finding");
        assertThatThrownBy(() -> a.ledger().openOwnerVetoFindingIds(ROUTE))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        assertThatThrownBy(() -> a.ledger().openOwnerVetoFindingIds(ROUTE))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        assertThatThrownBy(() -> a.ledger().checkLedger(ROUTE, Set.of(), a.heads()))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
    }

    @Test void wrongFindingRouteFailsGloballyButValidVetoIsRouteScoped() throws Exception {
        ReviewRecordStoreTest.Fixture fixture = ReviewRecordStoreTest.fixture(temp.resolve("cross-route"));
        String hash = fixture.context().verify().lockSha256();
        String item = PairMap.fromPackets(fixture.context().packetDirectory()).blindItemIds()
                .stream().sorted().findFirst().orElseThrow();
        fixture.safety().create(new SafetyFinding(FINDING, CREATED, hash, item, NOTE, null,
                List.of(ROUTE), INCORRECT_ANSWER, REVIEWER,
                SafetyFinding.Attribution.GENERATOR, false), null);
        fixture.reviews().create(new ReviewRecord(VETO, CREATED, hash, ReviewRecord.Kind.OWNER_VETO,
                NOTE, FINDING, ReviewRecord.Outcome.BLOCKING_CONCERN, OWNER,
                ReviewRecord.SignerRole.OWNER, EVIDENCE));
        FindingsLedger ledger = fixture.ledger();
        assertThat(ledger.openOwnerVetoFindingIds(ROUTE)).containsExactly(FINDING);
        assertThat(ledger.openOwnerVetoFindingIds(OTHER_ROUTE)).isEmpty();
        fixture.reviews().create(new ReviewRecord("veto_other_route", CREATED, hash,
                ReviewRecord.Kind.OWNER_VETO, ASK_COMPANION, FINDING,
                ReviewRecord.Outcome.BLOCKING_CONCERN, OWNER, ReviewRecord.SignerRole.OWNER, EVIDENCE));
        FindingsLedger.ExternalHeads noted = new FindingsLedger.ExternalHeads(
                external(fixture.safety().headSha256()),
                external(new EvidenceDisputeStore(fixture.root().resolve(DISPUTES), fixture.context()).headSha256()),
                external(fixture.reviews().headSha256()));
        assertThatThrownBy(() -> ledger.openOwnerVetoFindingIds(ROUTE))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        assertThatThrownBy(() -> ledger.openOwnerVetoFindingIds(OTHER_ROUTE))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        assertThatThrownBy(() -> ledger.checkLedger(ROUTE, Set.of(), noted))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        assertThatThrownBy(() -> ledger.checkLedger(OTHER_ROUTE, Set.of(), noted))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
    }

    @Test void vetoIdsDeduplicateAndClearedThenReopenedFindingRemainsOpen() throws Exception {
        Harness h = harness();
        Ledger a = h.ledgers().getFirst();
        a.finding(FINDING, List.of(ROUTE));
        a.veto(VETO, FINDING);
        a.veto(VETO_TWO, FINDING);
        assertThat(a.ledger().openOwnerVetoFindingIds(ROUTE)).containsExactly(FINDING);
        a.clear(CLEAR_ONE, VETO, NOTE, FINDING);
        assertThat(a.ledger().openOwnerVetoFindingIds(ROUTE)).containsExactly(FINDING);
        a.clear("clear_two", VETO_TWO, NOTE, FINDING);
        assertThat(a.ledger().openOwnerVetoFindingIds(ROUTE)).isEmpty();
        a.veto("veto_three", FINDING);
        assertThat(a.ledger().openOwnerVetoFindingIds(ROUTE)).containsExactly(FINDING);
    }

    @Test void invalidVetoResolutionVariantsAndUnknownWorkflowAreRefused() throws Exception {
        Harness h = harness();
        Ledger a = h.ledgers().getFirst();
        a.finding(FINDING, List.of(ROUTE));
        a.veto(VETO, FINDING);
        ReviewRecord unknown = a.record("unknown_workflow", ReviewRecord.Kind.OWNER_VETO,
                UNKNOWN, FINDING, ReviewRecord.Outcome.BLOCKING_CONCERN,
                ReviewRecord.SignerRole.OWNER);
        assertThatThrownBy(() -> a.reviews().create(unknown))
                .isInstanceOf(ReviewRecordStore.InvalidStore.class);
        ReviewRecord ux = a.record("ux_veto", ReviewRecord.Kind.OWNER_VETO,
                NOTE, FINDING, ReviewRecord.Outcome.BLOCKING_CONCERN,
                ReviewRecord.SignerRole.PRODUCT_UX);
        assertThatThrownBy(() -> a.reviews().create(ux))
                .isInstanceOf(ReviewRecordStore.InvalidStore.class);
        for (String workflow : List.of(TIP, ASK_COMPANION)) {
            ReviewRecord wrongWorkflow = new ReviewRecord("wrong_workflow", LATER, a.hash(),
                    ReviewRecord.Kind.OWNER_VETO, workflow, FINDING, ReviewRecord.Outcome.COMPLETE,
                    OWNER, ReviewRecord.SignerRole.OWNER, RESOLUTION, VETO,
                    ReviewRecord.ResolutionCode.FIXED);
            assertThatThrownBy(() -> a.reviews().create(wrongWorkflow))
                    .isInstanceOf(ReviewRecordStore.InvalidStore.class);
        }
        ReviewRecord wrongSubject = new ReviewRecord("wrong_subject", LATER, a.hash(),
                ReviewRecord.Kind.OWNER_VETO, NOTE, "another_finding", ReviewRecord.Outcome.COMPLETE,
                OWNER, ReviewRecord.SignerRole.OWNER, RESOLUTION, VETO,
                ReviewRecord.ResolutionCode.FIXED);
        assertThatThrownBy(() -> a.reviews().create(wrongSubject))
                .isInstanceOf(ReviewRecordStore.InvalidStore.class);
        ReviewRecord uxClear = new ReviewRecord("ux_clear", LATER, a.hash(),
                ReviewRecord.Kind.OWNER_VETO, NOTE, FINDING, ReviewRecord.Outcome.COMPLETE,
                OWNER, ReviewRecord.SignerRole.PRODUCT_UX, RESOLUTION, VETO,
                ReviewRecord.ResolutionCode.FIXED);
        assertThatThrownBy(() -> a.reviews().create(uxClear))
                .isInstanceOf(ReviewRecordStore.InvalidStore.class);
        ReviewRecord noCode = new ReviewRecord("no_code", LATER, a.hash(),
                ReviewRecord.Kind.OWNER_VETO, NOTE, FINDING, ReviewRecord.Outcome.COMPLETE,
                OWNER, ReviewRecord.SignerRole.OWNER, RESOLUTION, VETO, null);
        assertThatThrownBy(() -> a.reviews().create(noCode))
                .isInstanceOf(ReviewRecordStore.InvalidStore.class);
        ReviewRecord crossKind = new ReviewRecord("cross_kind", LATER, a.hash(),
                ReviewRecord.Kind.NO_VETO, NOTE, ROUTE, ReviewRecord.Outcome.COMPLETE,
                OWNER, ReviewRecord.SignerRole.OWNER, RESOLUTION, VETO,
                ReviewRecord.ResolutionCode.FIXED);
        assertThatThrownBy(() -> a.reviews().create(crossKind))
                .isInstanceOf(ReviewRecordStore.InvalidStore.class);
    }

    @Test void missingOneOfTwoAttestationsFailsCompositionAndSwitch() throws Exception {
        Harness h = harness();
        Set<FindingsLedger.ReviewSubject> extras = h.allExtras();
        h.ledgers().getFirst().complete(NO_VETO_NOTE, NOTE_ATTESTATION);
        assertThat(h.ledgers().get(2).token(extras).missing())
                .contains(NO_VETO_PREFIX + TIP + SEPARATOR + ROUTE);
        assertThatThrownBy(() -> h.compose(true, h.tokens(extras)))
                .isInstanceOf(ReviewComposition.InvalidComposition.class)
                .hasMessageContaining(MISSING_COMPLETION);
        var legacy = h.compose(false, h.tokens(h.derived()));
        var decision = check(h, legacy, Set.of(), true);
        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reasons()).contains(ProductionSwitchEligibility.COMPOSITION_FAILED,
                ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED);
        h.ledgers().get(2).complete(NO_VETO_TIP, TIP_ATTESTATION);
        assertThat(h.compose(true, h.tokens(extras))).isNotNull();
    }

    @Test void removedSuffixFailsExternalHeadAndMissingReviewStoreFailsClosed() throws Exception {
        Harness h = harness();
        Ledger a = h.ledgers().getFirst();
        a.complete(NO_VETO_NOTE, NOTE_ATTESTATION);
        FindingsLedger.ExternalHeads noted = a.heads();
        Path index = a.root().resolve(REVIEWS).resolve("index.jsonl");
        List<String> lines = Files.readAllLines(index);
        Files.writeString(index, String.join(NEWLINE, lines.subList(0, lines.size() - 1)) + NEWLINE);
        Path tail = a.root().resolve(REVIEWS).resolve(NO_VETO_NOTE + JSON_SUFFIX);
        Files.delete(tail);
        Files.delete(IntegritySupport.detached(tail));
        assertThatThrownBy(() -> a.ledger().checkLedger(ROUTE, Set.of(), noted))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        FindingsLedger withoutReviews = new FindingsLedger(a.root().resolve(SAFETY),
                a.root().resolve(DISPUTES), a.context());
        assertThatThrownBy(() -> withoutReviews.openOwnerVetoFindingIds(ROUTE))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
    }

    @Test void allRoutesFindingIsAcceptedAndFlippedReviewByteFailsClosed() throws Exception {
        Harness h = harness();
        Ledger a = h.ledgers().getFirst();
        a.finding(FINDING, List.of("ALL_ROUTES"));
        a.veto(VETO, FINDING);
        assertThat(a.ledger().openOwnerVetoFindingIds(ROUTE)).containsExactly(FINDING);
        FindingsLedger.ExternalHeads noted = a.heads();
        Files.writeString(a.root().resolve(REVIEWS + "/" + VETO + JSON_SUFFIX), "flipped");
        assertThatThrownBy(() -> a.ledger().openOwnerVetoFindingIds(ROUTE))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        assertThatThrownBy(() -> a.ledger().checkLedger(ROUTE, Set.of(), noted))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
    }

    @Test void eligibilityDerivesOpenVetoAndLegacyOverloadsDelegateUnchanged() throws Exception {
        Harness h = harness();
        h.attest();
        var composed = h.compose(true, h.tokens(h.allExtras()));
        var clean = check(h, composed, Set.of(), true);
        assertThat(clean.eligible()).isFalse();
        assertThat(clean.reasons()).containsExactly(
                ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED,
                ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED);
        var nullVeto = check(h, composed, null, true);
        assertThat(nullVeto.reasons()).contains(ProductionSwitchEligibility.OWNER_VETO_DIFFERS_FROM_LEDGER);
        Ledger a = h.ledgers().getFirst();
        a.finding(FINDING, List.of(ROUTE));
        a.veto(VETO, FINDING);
        var omitted = check(h, composed, Set.of(), true);
        assertThat(omitted.eligible()).isFalse();
        assertThat(omitted.reasons()).contains(ProductionSwitchEligibility.COMPOSITION_FAILED,
                ProductionSwitchEligibility.OWNER_VETO_DIFFERS_FROM_LEDGER,
                ProductionSwitchEligibility.CLASSIFICATION_BLOCKS_SWITCH,
                ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED);
        assertThat(omitted.reasons()).doesNotContain(ProductionSwitchEligibility.LEDGER_CHECK_FAILED);
        var old = ProductionSwitchEligibility.check(ROUTE, h.report(), input(composed, Set.of()),
                h.fixture().run(), h.fixture().frb(), h.ledgerMap(), h.heads(), Set.of());
        var oldOptions = check(h, composed, Set.of(), false);
        assertThat(oldOptions).isEqualTo(old);
        assertThat(ProductionSwitchEligibility.check(ROUTE, h.report(), input(composed, Set.of()),
                h.fixture().run(), h.fixture().frb(), h.ledgerMap(), h.heads(), Set.of(), null))
                .isEqualTo(old);
        assertThat(ProductionSwitchEligibility.check(ROUTE, h.report(), input(composed, Set.of()),
                h.fixture().run(), h.fixture().frb(), h.ledgerMap(), h.heads(), Set.of(), null, false))
                .isEqualTo(old);
        var recomputed = ProductionSwitchEligibility.check(ROUTE, h.report(), input(composed, Set.of()),
                h.fixture().run(), h.fixture().frb(), h.ledgerMap(), h.heads(), Set.of(), null, true);
        var recomputedOptions = ProductionSwitchEligibility.checkWithOptions(ROUTE, h.report(),
                input(composed, Set.of()), h.fixture().run(), h.fixture().frb(), h.ledgerMap(),
                h.heads(), Set.of(), new ProductionSwitchEligibility.SwitchOptions(null, true, false));
        assertThat(recomputedOptions).isEqualTo(recomputed);
    }

    @Test void openNonVetoConcernsNeverAppearAsVetoIds() throws Exception {
        Harness h = harness();
        Ledger a = h.ledgers().getFirst();
        a.reviews().create(a.record("quality_concern", ReviewRecord.Kind.ABSOLUTE_QUALITY, NOTE,
                "quality_one", ReviewRecord.Outcome.BLOCKING_CONCERN, ReviewRecord.SignerRole.PRODUCT_UX));
        assertThat(a.reviews().openConcerns()).containsExactly("quality_concern");
        assertThat(a.ledger().openOwnerVetoFindingIds(ROUTE)).isEmpty();
    }

    @Test void aCallerSupersetOfTheLedgerVetoSetIsStillADifference() throws Exception {
        Harness h = harness();
        h.attest();
        var composed = h.compose(true, h.tokens(h.allExtras()));
        assertThat(check(h, composed, Set.of(), true).reasons())
                .doesNotContain(ProductionSwitchEligibility.OWNER_VETO_DIFFERS_FROM_LEDGER);
        assertThat(check(h, composed, Set.of("spurious_finding"), true).reasons())
                .contains(ProductionSwitchEligibility.OWNER_VETO_DIFFERS_FROM_LEDGER);
    }

    @Test void aLedgerMissingFromTheMapIsReportedByThePreStep() throws Exception {
        Harness h = harness();
        h.attest();
        var composed = h.compose(true, h.tokens(h.allExtras()));
        Map<String, FindingsLedger> partial = new HashMap<>(h.ledgerMap());
        partial.remove(h.ledgers().getFirst().hash());
        var decision = ProductionSwitchEligibility.checkWithOptions(ROUTE, h.report(),
                input(composed, Set.of()), h.fixture().run(), h.fixture().frb(), partial, h.heads(), Set.of(),
                new ProductionSwitchEligibility.SwitchOptions(null, false, true));
        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reasons()).contains(ProductionSwitchEligibility.LEDGER_CHECK_FAILED,
                ProductionSwitchEligibility.EVALUATION_FAILED);
    }
}
