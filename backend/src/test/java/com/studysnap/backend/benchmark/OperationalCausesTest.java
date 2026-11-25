package com.studysnap.backend.benchmark;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** File-backed ledger and lock helpers copied into private types from OwnerAttestationTest;
 * conditional report builder copied from FrbClassificationTest; foreign-route lock helper copied
 * from ReviewRecordStoreTest. */
class OperationalCausesTest {
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String TIP = "STUDY_TIP";
    private static final String CASE = "case-1";
    private static final String SECOND = "case-2";
    private static final String HEX_A = "a";
    private static final String OWNER_SIGNER = "owner";
    private static final String SAFETY_STORE = "safety";
    private static final String DISPUTES_STORE = "disputes";
    private static final String REVIEWS_STORE = "reviews";
    private static final String HEAD = "0:head";
    private static final String BLANK = " ";
    private static final String CAUSE_REVIEW_ID = "cause";
    private static final String FIRST_REVIEW_ID = "first";
    private static final String SECOND_REVIEW_ID = "second";
    private static final String BEFORE_REVIEW_ID = "before";
    private static final String INDEPENDENT_REVIEW_ID = "independent";
    private static final String WITHDRAW_REVIEW_ID = "withdraw";
    private static final String DISPUTE_GATE = "dispute";
    private static final String VETO_GATE = "veto";
    private static final String REVIEW_GATE = "review";
    private static final String FIRST_CONCERN_ID = "first_concern";
    private static final String SECOND_CONCERN_ID = "second_concern";
    private static final String THIRD_CONCERN_ID = "third_concern";
    private static final String ASK_COMPANION = "ASK_COMPANION";
    private static final String SAFETY_VETO_ID = "safety_veto";
    private static final String FOREIGN_PARAMETERS =
            "src/test/java/com/studysnap/backend/benchmark/frozen-route-gates-frb.json";
    private static final String CREATED = "2026-11-16T00:07:00Z";
    private static final String LATER = "2026-11-16T00:08:00Z";
    private static final String GENESIS = "2026-11-16T00:05:00Z";
    private static final String EVIDENCE = HEX_A.repeat(64);
    private static final String RESOLUTION = "b".repeat(64);
    private static final String MISSING = "MISSING_RUN_DATA";
    private static final String SHORTFALL = "SUBGROUP_SHORTFALL";
    private static final String NOT_TRIGGERED = "NOT_TRIGGERED";
    private static final OperationalCauses.Category OUTAGE = OperationalCauses.Category.PROVIDER_OUTAGE;
    private static final OperationalCauses.Category STOPPED = OperationalCauses.Category.OWNER_STOPPED_RUN;
    private static final OperationalCauses.Category OTHER = OperationalCauses.Category.OTHER_OPERATIONAL;
    private static final KeyDefectWorkload.Parameters WORKLOAD = new KeyDefectWorkload.Parameters(
            BigDecimal.valueOf(30), BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(4));
    @TempDir Path temp;
    private int harnessNumber;
    private Path activeRoot;

    private record Ledger(PostLockContext context, ReviewRecordStore reviews, FindingsLedger ledger,
                          SafetyFindingStore safety, EvidenceDisputeStore disputes, Path reviewsPath) {
        String hash() { return context.verify().lockSha256(); }
        ReviewRecord record(String id, String workflow, String subject, ReviewRecord.Outcome outcome) {
            return new ReviewRecord(id, CREATED, hash(), ReviewRecord.Kind.OPERATIONAL_CAUSE,
                    workflow, subject, outcome, OWNER_SIGNER, ReviewRecord.SignerRole.OWNER, EVIDENCE);
        }
        ReviewRecord attest(String id, OperationalCauses.Category category, String workflow, String caseId) {
            return reviews.create(record(id, workflow, OperationalCauses.subjectId(category, workflow, caseId),
                    ReviewRecord.Outcome.COMPLETE));
        }
        ReviewRecord concern(String id, OperationalCauses.Category category, String workflow, String caseId) {
            return reviews.create(record(id, workflow, OperationalCauses.subjectId(category, workflow, caseId),
                    ReviewRecord.Outcome.BLOCKING_CONCERN));
        }
        ReviewRecord resolve(String id, String prior, OperationalCauses.Category category,
                String workflow, String caseId, ReviewRecord.ResolutionCode code) {
            return reviews.create(new ReviewRecord(id, LATER, hash(), ReviewRecord.Kind.OPERATIONAL_CAUSE,
                    workflow, OperationalCauses.subjectId(category, workflow, caseId),
                    ReviewRecord.Outcome.COMPLETE, OWNER_SIGNER, ReviewRecord.SignerRole.OWNER,
                    RESOLUTION, prior, code));
        }
        void openOwnerVeto() {
            String item = PairMap.fromPackets(context.packetDirectory()).blindItemIds().stream()
                    .sorted().findFirst().orElseThrow();
            safety.create(new SafetyFinding(SAFETY_VETO_ID, CREATED, hash(), item, NOTE, null,
                    List.of(ROUTE), "Incorrect answer", "reviewer", SafetyFinding.Attribution.GENERATOR,
                    false), null);
            reviews.create(new ReviewRecord("owner_veto", CREATED, hash(), ReviewRecord.Kind.OWNER_VETO,
                    NOTE, SAFETY_VETO_ID, ReviewRecord.Outcome.BLOCKING_CONCERN,
                    OWNER_SIGNER, ReviewRecord.SignerRole.OWNER, EVIDENCE));
        }
        FindingsLedger.ExternalHeads heads() {
            return new FindingsLedger.ExternalHeads(external(safety.headSha256()),
                    external(disputes.headSha256()), external(reviews.headSha256()));
        }
    }
    private record Harness(FrbPipelineTest.Fixture fixture, List<Ledger> ledgers, String sha,
                           Set<FindingsLedger.ReviewSubject> derived) {
        List<FindingsLedger> routeLedgers() { return ledgers.stream().map(Ledger::ledger).toList(); }
        OperationalCauses.Result derive(RouteVerdictCalculator.RouteReport report) {
            return OperationalCauses.derive(routeLedgers(), ROUTE, report);
        }
        ReviewComposition.ComposedReviewCheck compose(RouteVerdictCalculator.RouteReport report,
                boolean requireOwner) {
            List<FindingsLedger.ReviewCheck> tokens = ledgers.stream()
                    .map(item -> item.ledger().checkLedger(ROUTE, derived, item.heads())).toList();
            String candidate = Stage1Receipt.read(fixture.run().candidate().receipt()).arm();
            return ReviewComposition.compose(ROUTE, fixture.run(), fixture.frb(), report, tokens,
                    WORKLOAD, Set.of(candidate), Set.of("BASELINE_MINI", "BASELINE_41"), requireOwner);
        }
    }
    private record ForeignRoute(PostLockContext context, ReviewRecordStore reviews, FindingsLedger ledger) {}
    private static FindingsLedger.ExternalHead external(PostLockStoreIndex.Head head) {
        return new FindingsLedger.ExternalHead(head.seq(), head.sha256());
    }
    private Harness harness() throws Exception {
        FrbPipelineTest builder = new FrbPipelineTest();
        activeRoot = Files.createDirectories(temp.resolve("harness-" + ++harnessNumber));
        builder.temp = activeRoot;
        FrbPipelineTest.Fixture fixture = builder.fixture(true, 0, 1, true);
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
            ledgers.add(ledger(export.lockForPrimaryOpening(), export.sealFile(), export.findingsDirectory(),
                    ledgers.size(), fixture));
            ledgers.add(ledger(export.lockForReserveOpening(), export.sealFile(), export.findingsDirectory(),
                    ledgers.size(), fixture));
        }
        Ledger reserve = ledgers.get(2);
        reserve.reviews().create(new ReviewRecord("class_b_complete", CREATED, reserve.hash(),
                ReviewRecord.Kind.CLASS_B_WORKFLOW, TIP, TIP, ReviewRecord.Outcome.COMPLETE,
                OWNER_SIGNER, ReviewRecord.SignerRole.OWNER, EVIDENCE));
        int number = 0;
        for (FindingsLedger.ReviewSubject subject : derived) {
            Ledger primary = ledgers.getFirst();
            primary.reviews().create(new ReviewRecord("defect_complete_" + ++number, CREATED, primary.hash(),
                    subject.kind(), subject.workflow(), subject.subjectId(), ReviewRecord.Outcome.COMPLETE,
                    OWNER_SIGNER, ReviewRecord.SignerRole.OWNER, EVIDENCE));
        }
        return new Harness(fixture, List.copyOf(ledgers), parameters.sha256, derived);
    }
    private Ledger ledger(Path lock, Path seal, Path findings, int index,
            FrbPipelineTest.Fixture fixture) throws Exception {
        PostLockContext context = new PostLockContext(lock, findings,
                seal.getParent().resolve("packets"), seal, fixture.run().parameters());
        Path root = Files.createDirectories(activeRoot.resolve("ledger-" + index));
        Path safety = root.resolve(SAFETY_STORE), disputes = root.resolve(DISPUTES_STORE),
                reviews = root.resolve(REVIEWS_STORE);
        SafetyFindingStore safetyStore = SafetyFindingStore.init(safety, context, GENESIS);
        EvidenceDisputeStore disputeStore = EvidenceDisputeStore.init(disputes, context, GENESIS);
        ReviewRecordStore reviewStore = ReviewRecordStore.init(reviews, context, GENESIS);
        return new Ledger(context, reviewStore, new FindingsLedger(safety, disputes, reviews, context),
                safetyStore, disputeStore, reviews);
    }
    private ForeignRoute foreignRoute() throws Exception {
        Path root = temp.resolve("foreign");
        Path locked = Files.createDirectories(root.resolve("locked"));
        var export = ArmKeyGateFixtures.classA(locked);
        Path seal = ArmKeyGateFixtures.seal(locked, export.armKey());
        Path finding = ArmKeyGateFixtures.findings(locked);
        Path lock = ArmKeyGateFixtures.lock(locked, seal, finding, LockRecord.Kind.PRIMARY);
        Path parameters = root.resolve("parameters.json");
        Files.copy(Path.of(FOREIGN_PARAMETERS), parameters);
        Files.copy(Path.of(FOREIGN_PARAMETERS + ".sha256"), IntegritySupport.detached(parameters));
        PostLockContext context = new PostLockContext(lock, finding.getParent(),
                locked.resolve("packets"), seal, parameters);
        Path safety = root.resolve(SAFETY_STORE), disputes = root.resolve(DISPUTES_STORE),
                reviews = root.resolve(REVIEWS_STORE);
        SafetyFindingStore.init(safety, context, GENESIS);
        EvidenceDisputeStore.init(disputes, context, GENESIS);
        ReviewRecordStore reviewStore = ReviewRecordStore.init(reviews, context, GENESIS);
        return new ForeignRoute(context, reviewStore, new FindingsLedger(safety, disputes, reviews, context));
    }
    private static RouteVerdictCalculator.WorkflowReport row(String sha,
            RouteVerdictCalculator.Verdict verdict, Set<RouteVerdictCalculator.Tier> failed,
            Map<String, SortedSet<String>> causes, Set<String> undisposed) {
        return new RouteVerdictCalculator.WorkflowReport(verdict, failed, Set.of(),
                1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false,
                Map.of(), Map.of(), Map.of(), sha, 0, undisposed, null,
                causes == null ? null : List.copyOf(causes.keySet()), null, null, causes,
                null, null, null);
    }
    private static RouteVerdictCalculator.RouteReport report(String sha,
            RouteVerdictCalculator.Verdict verdict, Set<RouteVerdictCalculator.Tier> failed,
            Map<String, SortedSet<String>> noteCauses, Map<String, SortedSet<String>> tipCauses,
            Set<String> undisposed) {
        return new RouteVerdictCalculator.RouteReport(verdict, failed, Set.of(), 0, false, sha,
                Map.of(NOTE, row(sha, verdict, failed, noteCauses, undisposed),
                        TIP, row(sha, verdict, failed, tipCauses, Set.of())));
    }
    private static Map<String, SortedSet<String>> ids(String cause, String... cases) {
        return Map.of(cause, new TreeSet<>(List.of(cases)));
    }
    private static OperationalCauses.Finding finding(OperationalCauses.Result result, String workflow, String id) {
        return result.findings().stream().filter(value -> value.workflow().equals(workflow)
                && value.caseId().equals(id)).findFirst().orElseThrow();
    }
    private static FrbClassification classify(RouteVerdictCalculator.RouteReport report,
            Set<String> established, Set<String> safety, Set<String> disputes, Set<String> vetoes,
            Set<String> missing) {
        return DeadlineRule.classifyFrb(report, new FrbClassificationInput(safety, disputes, missing,
                Set.of(), established, false, false, vetoes, Map.of(NOTE, NOT_TRIGGERED),
                Map.of(SAFETY_STORE, HEAD)));
    }
    private static Map<String, String> snapshot(Path directory) throws Exception {
        Map<String, String> bytes = new TreeMap<>();
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList())
                bytes.put(directory.relativize(path).toString(),
                        Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
        }
        return Map.copyOf(bytes);
    }

    @Test void pseudonymIsStableScopedAndValid() {
        String subject = OperationalCauses.subjectId(OUTAGE, NOTE, CASE);
        assertThat(subject).matches("oc-po-[0-9a-f]{32}");
        assertThat(subject).isEqualTo(OperationalCauses.subjectId(OUTAGE, NOTE, CASE));
        assertThat(subject).isNotEqualTo(OperationalCauses.subjectId(OUTAGE, TIP, CASE))
                .isNotEqualTo(OperationalCauses.subjectId(STOPPED, NOTE, CASE));
        PostLockContext.id(subject);
        // The NUL separator keeps a workflow/case boundary shift from colliding.
        assertThat(OperationalCauses.subjectId(OUTAGE, "AB", "C"))
                .isNotEqualTo(OperationalCauses.subjectId(OUTAGE, "A", "BC"));
        assertThatThrownBy(() -> OperationalCauses.subjectId(OUTAGE, NOTE, BLANK))
                .isInstanceOf(OperationalCauses.InvalidOperationalCause.class);
        assertThatThrownBy(() -> OperationalCauses.subjectId(null, NOTE, CASE))
                .isInstanceOf(OperationalCauses.InvalidOperationalCause.class);
        assertThatThrownBy(() -> OperationalCauses.subjectId(OUTAGE, BLANK, CASE))
                .isInstanceOf(OperationalCauses.InvalidOperationalCause.class);
    }

    @Test void storeAcceptsThreeCategoriesAndRejectsMalformedOrForeignClaims() throws Exception {
        Harness h = harness();
        Ledger ledger = h.ledgers().getFirst();
        ReviewRecordStore store = ledger.reviews();
        int number = 0;
        for (OperationalCauses.Category category : OperationalCauses.Category.values())
            ledger.attest("attest_" + ++number, category, NOTE, CASE);
        assertThat(ledger.reviews().records()).filteredOn(r -> r.kind() == ReviewRecord.Kind.OPERATIONAL_CAUSE)
                .hasSize(3);
        String valid = OperationalCauses.subjectId(OUTAGE, NOTE, CASE);
        for (String bad : List.of(valid.replace("oc-po", "oc-xx"), valid.toUpperCase(),
                valid.substring(0, valid.length() - 1), valid + HEX_A, CASE, "CATEGORY|" + CASE,
                "oc-arm-" + HEX_A.repeat(32))) {
            ReviewRecord claim = ledger.record("bad_subject", NOTE, bad, ReviewRecord.Outcome.COMPLETE);
            assertThatThrownBy(() -> store.create(claim))
                    .isInstanceOf(ReviewRecordStore.InvalidStore.class);
        }
        ReviewRecord ux = new ReviewRecord("bad_role", CREATED, ledger.hash(),
                ReviewRecord.Kind.OPERATIONAL_CAUSE, NOTE, valid, ReviewRecord.Outcome.BLOCKING_CONCERN,
                OWNER_SIGNER, ReviewRecord.SignerRole.PRODUCT_UX, EVIDENCE);
        assertThatThrownBy(() -> store.create(ux)).isInstanceOf(ReviewRecordStore.InvalidStore.class);
        ReviewRecord otherLock = new ReviewRecord("bad_lock", CREATED, h.ledgers().get(1).hash(),
                ReviewRecord.Kind.OPERATIONAL_CAUSE, NOTE, valid, ReviewRecord.Outcome.COMPLETE,
                OWNER_SIGNER, ReviewRecord.SignerRole.OWNER, EVIDENCE);
        assertThatThrownBy(() -> store.create(otherLock))
                .isInstanceOf(ReviewRecordStore.InvalidStore.class);
        ReviewRecord badDigest = new ReviewRecord("bad_digest", CREATED, ledger.hash(),
                ReviewRecord.Kind.OPERATIONAL_CAUSE, NOTE, valid, ReviewRecord.Outcome.COMPLETE,
                OWNER_SIGNER, ReviewRecord.SignerRole.OWNER, "bad");
        assertThatThrownBy(() -> store.create(badDigest))
                .isInstanceOf(ReviewRecordStore.InvalidStore.class);
        ReviewRecord early = new ReviewRecord("bad_time", ledger.context().verify().lock().lockedAtUtc(), ledger.hash(),
                ReviewRecord.Kind.OPERATIONAL_CAUSE, NOTE, valid, ReviewRecord.Outcome.COMPLETE,
                OWNER_SIGNER, ReviewRecord.SignerRole.OWNER, EVIDENCE);
        assertThatThrownBy(() -> store.create(early)).isInstanceOf(ReviewRecordStore.InvalidStore.class);
        ReviewRecord workflow = ledger.record("bad_workflow", "UNKNOWN", valid, ReviewRecord.Outcome.COMPLETE);
        assertThatThrownBy(() -> store.create(workflow))
                .isInstanceOf(ReviewRecordStore.InvalidStore.class);
        ReviewRecord unknown = new ReviewRecord("bad_prior", LATER, ledger.hash(),
                ReviewRecord.Kind.OPERATIONAL_CAUSE, NOTE, valid, ReviewRecord.Outcome.COMPLETE,
                OWNER_SIGNER, ReviewRecord.SignerRole.OWNER, RESOLUTION, "missing_prior",
                ReviewRecord.ResolutionCode.FIXED);
        assertThatThrownBy(() -> store.create(unknown))
                .isInstanceOf(ReviewRecordStore.InvalidStore.class);
    }

    @Test void missingAndThreeCategoriesDeriveFromRealLedger() throws Exception {
        for (OperationalCauses.Category category : OperationalCauses.Category.values()) {
            Harness h = harness();
            var report = report(h.sha(), RouteVerdictCalculator.Verdict.INCONCLUSIVE,
                    Set.of(), ids(MISSING, CASE), null, Set.of());
            assertThat(finding(h.derive(report), NOTE, CASE).status())
                    .isEqualTo(OperationalCauses.Status.NO_ATTESTATION);
            h.ledgers().get(2).attest(CAUSE_REVIEW_ID, category, NOTE, CASE);
            var result = h.derive(report);
            assertThat(result.establishedCaseIds()).containsExactly(CASE);
            assertThat(finding(result, NOTE, CASE).categoryOrNull()).isEqualTo(category);
            assertThat(finding(result, NOTE, CASE).reviewIdOrNull()).isEqualTo(CAUSE_REVIEW_ID);
        }
    }

    @Test void shortfallAndDuplicateCauseIdsAreDeduplicatedButRequireEachCase() throws Exception {
        Harness h = harness();
        h.ledgers().getFirst().attest("one", OUTAGE, NOTE, CASE);
        var report = report(h.sha(), RouteVerdictCalculator.Verdict.INCONCLUSIVE, Set.of(),
                Map.of(MISSING, new TreeSet<>(Set.of(CASE)),
                        SHORTFALL, new TreeSet<>(Set.of(CASE, SECOND))), null, Set.of());
        var result = h.derive(report);
        assertThat(result.findings()).hasSize(2);
        assertThat(result.establishedCaseIds()).containsExactly(CASE);
        assertThat(finding(result, NOTE, SECOND).status()).isEqualTo(OperationalCauses.Status.NO_ATTESTATION);
        h.ledgers().getFirst().attest("two", STOPPED, NOTE, SECOND);
        assertThat(h.derive(report).establishedCaseIds()).containsExactly(CASE, SECOND);
    }

    @Test void categoriesConflictButSameCategoryCompletionsDoNot() throws Exception {
        Harness h = harness();
        var report = report(h.sha(), RouteVerdictCalculator.Verdict.INCONCLUSIVE,
                Set.of(), ids(MISSING, CASE), null, Set.of());
        h.ledgers().getFirst().attest(FIRST_REVIEW_ID, OUTAGE, NOTE, CASE);
        h.ledgers().get(1).attest(SECOND_REVIEW_ID, OUTAGE, NOTE, CASE);
        assertThat(h.derive(report).establishedCaseIds()).containsExactly(CASE);
        assertThat(finding(h.derive(report), NOTE, CASE).reviewIdOrNull()).isEqualTo(FIRST_REVIEW_ID);
        h.ledgers().get(2).attest("third", OTHER, NOTE, CASE);
        assertThat(finding(h.derive(report), NOTE, CASE).status())
                .isEqualTo(OperationalCauses.Status.CONFLICTING_CATEGORIES);
        assertThat(h.derive(report).establishedCaseIds()).isEmpty();
    }

    @Test void laterOpenConcernInAnotherLedgerAndCategoryWithdrawsAndBlocksComposition() throws Exception {
        Harness h = harness();
        var report = report(h.sha(), RouteVerdictCalculator.Verdict.INCONCLUSIVE,
                Set.of(), ids(MISSING, CASE), null, Set.of());
        h.ledgers().getFirst().attest(FIRST_REVIEW_ID, OUTAGE, NOTE, CASE);
        h.ledgers().get(2).concern("later_concern", OTHER, NOTE, CASE);
        assertThat(finding(h.derive(report), NOTE, CASE).status())
                .isEqualTo(OperationalCauses.Status.WITHDRAWN_OPEN_CONCERN);
        assertThat(h.derive(report).establishedCaseIds()).isEmpty();
        assertThatThrownBy(() -> h.compose(report, false))
                .isInstanceOf(ReviewComposition.InvalidComposition.class)
                .hasMessageContaining("Lock has open records");
    }

    @Test void laterConcernInSameStoreWinsOverCompletionAndCategoryConflict() throws Exception {
        Harness h = harness();
        var report = report(h.sha(), RouteVerdictCalculator.Verdict.INCONCLUSIVE,
                Set.of(), ids(MISSING, CASE), null, Set.of());
        Ledger ledger = h.ledgers().getFirst();
        ledger.attest(FIRST_REVIEW_ID, OUTAGE, NOTE, CASE);
        ledger.attest(SECOND_REVIEW_ID, STOPPED, NOTE, CASE);
        ledger.concern("later_same_store", OUTAGE, NOTE, CASE);
        assertThat(finding(h.derive(report), NOTE, CASE).status())
                .isEqualTo(OperationalCauses.Status.WITHDRAWN_OPEN_CONCERN);
        assertThat(h.derive(report).establishedCaseIds()).isEmpty();
    }

    @Test void independentCompletionsInDifferentLedgersUnionForDifferentCases() throws Exception {
        Harness h = harness();
        var report = report(h.sha(), RouteVerdictCalculator.Verdict.INCONCLUSIVE,
                Set.of(), ids(MISSING, CASE, SECOND), null, Set.of());
        h.ledgers().getFirst().attest(FIRST_REVIEW_ID, OUTAGE, NOTE, CASE);
        h.ledgers().get(2).attest(SECOND_REVIEW_ID, OTHER, NOTE, SECOND);
        assertThat(h.derive(report).establishedCaseIds()).containsExactly(CASE, SECOND);
    }

    @Test void resolvedConcernRestoresIndependentCompletionButCannotCreateOne() throws Exception {
        for (ReviewRecord.ResolutionCode code : List.of(ReviewRecord.ResolutionCode.FIXED,
                ReviewRecord.ResolutionCode.ACCEPTED_WITH_RATIONALE)) {
            Harness h = harness();
            var report = report(h.sha(), RouteVerdictCalculator.Verdict.INCONCLUSIVE,
                    Set.of(), ids(MISSING, CASE), null, Set.of());
            Ledger ledger = h.ledgers().getFirst();
            ledger.concern(BEFORE_REVIEW_ID, OUTAGE, NOTE, CASE);
            ledger.resolve("resolved", BEFORE_REVIEW_ID, OUTAGE, NOTE, CASE, code);
            assertThat(finding(h.derive(report), NOTE, CASE).status())
                    .isEqualTo(OperationalCauses.Status.NO_ATTESTATION);
            ledger.attest(INDEPENDENT_REVIEW_ID, OUTAGE, NOTE, CASE);
            assertThat(h.derive(report).establishedCaseIds()).containsExactly(CASE);
        }
    }

    @Test void notApplicablePermanentlyWithdrawsEvenAfterNewCompletionAndWinsPrecedence() throws Exception {
        Harness h = harness();
        var report = report(h.sha(), RouteVerdictCalculator.Verdict.INCONCLUSIVE,
                Set.of(), ids(MISSING, CASE), null, Set.of());
        Ledger ledger = h.ledgers().getFirst();
        ledger.attest(FIRST_REVIEW_ID, OUTAGE, NOTE, CASE);
        ledger.concern(WITHDRAW_REVIEW_ID, OUTAGE, NOTE, CASE);
        ledger.resolve("resolution", WITHDRAW_REVIEW_ID, OUTAGE, NOTE, CASE,
                ReviewRecord.ResolutionCode.NOT_APPLICABLE);
        ledger.attest("again", OUTAGE, NOTE, CASE);
        ledger.concern("open_other", OTHER, NOTE, CASE);
        assertThat(finding(h.derive(report), NOTE, CASE).status())
                .isEqualTo(OperationalCauses.Status.WITHDRAWN_BY_OWNER);
        assertThat(h.derive(report).establishedCaseIds()).isEmpty();
    }

    @Test void flatCaseCollisionIsAmbiguousUntilBothWorkflowsAttested() throws Exception {
        Harness h = harness();
        var report = report(h.sha(), RouteVerdictCalculator.Verdict.INCONCLUSIVE,
                Set.of(), ids(MISSING, CASE), ids(MISSING, CASE), Set.of());
        h.ledgers().getFirst().attest("note", OUTAGE, NOTE, CASE);
        var partial = h.derive(report);
        assertThat(partial.establishedCaseIds()).isEmpty();
        assertThat(partial.findings()).extracting(OperationalCauses.Finding::status)
                .containsOnly(OperationalCauses.Status.AMBIGUOUS_CASE_ID);
        h.ledgers().get(2).attest("tip", OTHER, TIP, CASE);
        assertThat(h.derive(report).establishedCaseIds()).containsExactly(CASE);
    }

    @Test void unmatchedSubjectCannotEstablishAndResultsAreImmutable() throws Exception {
        Harness h = harness();
        Ledger ledger = h.ledgers().getFirst();
        ledger.attest("unmatched", OUTAGE, NOTE, SECOND);
        var report = report(h.sha(), RouteVerdictCalculator.Verdict.INCONCLUSIVE,
                Set.of(), ids(MISSING, CASE), null, Set.of());
        var result = h.derive(report);
        assertThat(result.establishedCaseIds()).isEmpty();
        assertThat(result.unmatchedAttestationSubjects())
                .containsExactly(OperationalCauses.subjectId(OUTAGE, NOTE, SECOND));
        assertThat(result.findings()).doesNotContainNull();
        assertThat(result.establishedCaseIds()).doesNotContainNull();
        assertThat(result.unmatchedAttestationSubjects()).doesNotContainNull();
        SortedSet<String> established = result.establishedCaseIds();
        List<OperationalCauses.Finding> findings = result.findings();
        SortedSet<String> unmatched = result.unmatchedAttestationSubjects();
        assertThatThrownBy(() -> established.add(SECOND))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(findings::clear)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> unmatched.add(CASE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void preconditionsAndReportBindingFailClosed() throws Exception {
        Harness h = harness();
        var report = report(h.sha(), RouteVerdictCalculator.Verdict.INCONCLUSIVE,
                Set.of(), ids(MISSING, CASE), null, Set.of());
        List<FindingsLedger> empty = List.of();
        assertThatThrownBy(() -> OperationalCauses.derive(empty, ROUTE, report))
                .isInstanceOf(OperationalCauses.InvalidOperationalCause.class);
        assertThatThrownBy(() -> OperationalCauses.derive(null, ROUTE, report))
                .isInstanceOf(OperationalCauses.InvalidOperationalCause.class);
        List<FindingsLedger> nullElement = new ArrayList<>();
        nullElement.add(null);
        assertThatThrownBy(() -> OperationalCauses.derive(nullElement, ROUTE, report))
                .isInstanceOf(OperationalCauses.InvalidOperationalCause.class);
        List<FindingsLedger> duplicate = List.of(h.routeLedgers().getFirst(), h.routeLedgers().getFirst());
        assertThatThrownBy(() -> OperationalCauses.derive(duplicate, ROUTE, report))
                .isInstanceOf(OperationalCauses.InvalidOperationalCause.class);
        List<FindingsLedger> routeLedgers = h.routeLedgers();
        assertThatThrownBy(() -> OperationalCauses.derive(routeLedgers, ROUTE, null))
                .isInstanceOf(OperationalCauses.InvalidOperationalCause.class);
        var wrong = report("0".repeat(64), RouteVerdictCalculator.Verdict.INCONCLUSIVE,
                Set.of(), ids(MISSING, CASE), null, Set.of());
        assertThatThrownBy(() -> h.derive(wrong)).isInstanceOf(FindingsLedger.InvalidLedger.class);
    }

    @Test void classificationUsesDerivedSetAndCallerCanStillForgeIt() throws Exception {
        Harness h = harness();
        var report = report(h.sha(), RouteVerdictCalculator.Verdict.INCONCLUSIVE,
                Set.of(), ids(MISSING, CASE), null, Set.of());
        var absent = h.derive(report).establishedCaseIds();
        assertThat(classify(report, absent, Set.of(), Set.of(), Set.of(), Set.of()).classification())
                .isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        assertThat(classify(report, absent, Set.of(), Set.of(), Set.of(), Set.of()).causes())
                .contains("UNATTRIBUTED_INCONCLUSIVE");
        h.ledgers().getFirst().attest(CAUSE_REVIEW_ID, OUTAGE, NOTE, CASE);
        var established = h.derive(report).establishedCaseIds();
        assertThat(classify(report, established, Set.of(), Set.of(), Set.of(), Set.of()).classification())
                .isEqualTo(DeadlineRule.Classification.LUNA_FALLBACK_ELIGIBLE);
        var forgedInput = new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(),
                Set.of(CASE), false, false, Set.of(), Map.of(NOTE, NOT_TRIGGERED),
                Map.of(SAFETY_STORE, HEAD));
        var emptyInput = new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(),
                Set.of(), false, false, Set.of(), Map.of(NOTE, NOT_TRIGGERED),
                Map.of(SAFETY_STORE, HEAD));
        assertThat(ResultsPipeline.classifyFrbRoutes(Map.of(ROUTE, report),
                Map.of(ROUTE, forgedInput), Set.of()).get(ROUTE).classification())
                .isEqualTo(DeadlineRule.Classification.LUNA_FALLBACK_ELIGIBLE);
        assertThat(ResultsPipeline.classifyFrbRoutes(Map.of(ROUTE, report),
                Map.of(ROUTE, emptyInput), Set.of()).get(ROUTE).classification())
                .isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
    }

    @Test void establishedCauseNeverOverridesOwnerGatesOrBecomesPass() throws Exception {
        Harness h = harness();
        h.ledgers().getFirst().attest(CAUSE_REVIEW_ID, OUTAGE, NOTE, CASE);
        var report = report(h.sha(), RouteVerdictCalculator.Verdict.INCONCLUSIVE,
                Set.of(), ids(MISSING, CASE), null, Set.of());
        Set<String> set = h.derive(report).establishedCaseIds();
        for (String gate : List.of(SAFETY_STORE, DISPUTE_GATE, VETO_GATE, REVIEW_GATE)) {
            Set<String> singleton = Set.of(gate);
            var result = classify(report, set, gate.equals(SAFETY_STORE) ? singleton : Set.of(),
                    gate.equals(DISPUTE_GATE) ? singleton : Set.of(),
                    gate.equals(VETO_GATE) ? singleton : Set.of(),
                    gate.equals(REVIEW_GATE) ? singleton : Set.of());
            assertThat(result.classification()).isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        }
        h.ledgers().getFirst().openOwnerVeto();
        var liveVetoes = h.ledgers().getFirst().ledger().openOwnerVetoFindingIds(ROUTE);
        assertThat(liveVetoes).containsExactly(SAFETY_VETO_ID);
        assertThat(classify(report, set, Set.of(), Set.of(), liveVetoes, Set.of()).classification())
                .isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        for (RouteVerdictCalculator.Tier tier : List.of(RouteVerdictCalculator.Tier.T1,
                RouteVerdictCalculator.Tier.T2, RouteVerdictCalculator.Tier.T3)) {
            var failed = report(h.sha(), RouteVerdictCalculator.Verdict.FAIL,
                    Set.of(tier), ids(MISSING, CASE), null, Set.of());
            assertThat(classify(failed, set, Set.of(), Set.of(), Set.of(), Set.of()).classification())
                    .isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        }
        var undisposed = report(h.sha(), RouteVerdictCalculator.Verdict.INCONCLUSIVE,
                Set.of(), ids(MISSING, CASE), null, Set.of(CASE));
        assertThat(classify(undisposed, set, Set.of(), Set.of(), Set.of(), Set.of()).classification())
                .isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        var correctness = report(h.sha(), RouteVerdictCalculator.Verdict.INCONCLUSIVE,
                Set.of(), Map.of(MISSING, new TreeSet<>(Set.of(CASE)),
                        "CONFIRMED_T1", new TreeSet<>()), null, Set.of());
        assertThat(classify(correctness, set, Set.of(), Set.of(), Set.of(), Set.of()).classification())
                .isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        var passLooking = report(h.sha(), RouteVerdictCalculator.Verdict.PASS,
                Set.of(), ids(MISSING, CASE), null, Set.of());
        assertThat(classify(passLooking, set, Set.of(), Set.of(), Set.of(), Set.of()).classification())
                .isNotEqualTo(DeadlineRule.Classification.BENCHMARK_PASS);
    }

    @Test void cleanAndAttestedCompositionNeverRequiresOperationalCause() throws Exception {
        Harness h = harness();
        var report = report(h.sha(), RouteVerdictCalculator.Verdict.INCONCLUSIVE,
                Set.of(), ids(MISSING, CASE), null, Set.of());
        assertThat(h.compose(report, false)).isNotNull();
        h.ledgers().getFirst().attest(FIRST_REVIEW_ID, OUTAGE, NOTE, CASE);
        h.ledgers().get(2).attest(SECOND_REVIEW_ID, STOPPED, NOTE, SECOND);
        assertThat(h.compose(report, false)).isNotNull();
        assertThat(ReviewComposition.requiredSubjects(
                FrozenGateParameters.load(h.fixture().run().parameters()),
                FrozenGateParameters.load(h.fixture().run().parameters()).routes.get(ROUTE), h.derived()))
                .noneMatch(subject -> subject.kind() == ReviewRecord.Kind.OPERATIONAL_CAUSE);
        assertThatThrownBy(() -> h.compose(report, true))
                .isInstanceOf(ReviewComposition.InvalidComposition.class);
    }

    @Test void independentConcernCannotEstablishAndReraisedConcernWithdrawsAgain() throws Exception {
        Harness h = harness();
        var report = report(h.sha(), RouteVerdictCalculator.Verdict.INCONCLUSIVE,
                Set.of(), ids(MISSING, CASE), null, Set.of());
        Ledger ledger = h.ledgers().getFirst();
        ledger.concern(FIRST_CONCERN_ID, OUTAGE, NOTE, CASE);
        assertThat(finding(h.derive(report), NOTE, CASE).status())
                .isEqualTo(OperationalCauses.Status.WITHDRAWN_OPEN_CONCERN);
        ledger.resolve("first_resolution", FIRST_CONCERN_ID, OUTAGE, NOTE, CASE,
                ReviewRecord.ResolutionCode.FIXED);
        assertThat(finding(h.derive(report), NOTE, CASE).status())
                .isEqualTo(OperationalCauses.Status.NO_ATTESTATION);
        ledger.attest(INDEPENDENT_REVIEW_ID, OUTAGE, NOTE, CASE);
        assertThat(h.derive(report).establishedCaseIds()).containsExactly(CASE);
        ledger.concern(SECOND_CONCERN_ID, OUTAGE, NOTE, CASE);
        ledger.concern(THIRD_CONCERN_ID, OUTAGE, NOTE, CASE);
        ledger.resolve("second_resolution", SECOND_CONCERN_ID, OUTAGE, NOTE, CASE,
                ReviewRecord.ResolutionCode.ACCEPTED_WITH_RATIONALE);
        assertThat(finding(h.derive(report), NOTE, CASE).status())
                .isEqualTo(OperationalCauses.Status.WITHDRAWN_OPEN_CONCERN);
        ledger.resolve("third_resolution", THIRD_CONCERN_ID, OUTAGE, NOTE, CASE,
                ReviewRecord.ResolutionCode.FIXED);
        assertThat(h.derive(report).establishedCaseIds()).containsExactly(CASE);
        assertThat(ledger.ledger().operationalCauseReviews(ROUTE).resolvedConcerns())
                .extracting(FindingsLedger.ResolvedOperationalConcern::concernReviewId)
                .containsExactly(FIRST_CONCERN_ID, SECOND_CONCERN_ID, THIRD_CONCERN_ID);
        assertThat(ledger.ledger().operationalCauseReviews(ROUTE).resolvedConcerns())
                .extracting(FindingsLedger.ResolvedOperationalConcern::resolutionCode)
                .containsExactly(ReviewRecord.ResolutionCode.FIXED,
                        ReviewRecord.ResolutionCode.ACCEPTED_WITH_RATIONALE,
                        ReviewRecord.ResolutionCode.FIXED);
    }

    @Test void emptyIdsAndReadOnlyQuery() throws Exception {
        Harness h = harness();
        Ledger ledger = h.ledgers().getFirst();
        ledger.attest("unneeded", OUTAGE, NOTE, CASE);
        var report = report(h.sha(), RouteVerdictCalculator.Verdict.INCONCLUSIVE,
                Set.of(), ids(MISSING), null, Set.of());
        Map<String, String> before = snapshot(ledger.reviewsPath());
        var result = h.derive(report);
        Map<String, String> after = snapshot(ledger.reviewsPath());
        assertThat(after).isEqualTo(before);
        assertThat(result.findings()).isEmpty();
        assertThat(result.establishedCaseIds()).isEmpty();
        assertThat(result.unmatchedAttestationSubjects())
                .containsExactly(OperationalCauses.subjectId(OUTAGE, NOTE, CASE));
        assertThat(ledger.ledger().operationalCauseReviews(ROUTE).records()).hasSize(1);
    }

    @Test void ledgerQueryExcludesValidRecordFromAnotherRoute() throws Exception {
        ForeignRoute fixture = foreignRoute();
        String subject = OperationalCauses.subjectId(OUTAGE, ASK_COMPANION, CASE);
        fixture.reviews().create(new ReviewRecord("foreign_route", CREATED,
                fixture.context().verify().lockSha256(), ReviewRecord.Kind.OPERATIONAL_CAUSE,
                ASK_COMPANION, subject, ReviewRecord.Outcome.COMPLETE, OWNER_SIGNER,
                ReviewRecord.SignerRole.OWNER, EVIDENCE));
        assertThat(fixture.ledger().operationalCauseReviews(ROUTE).records()).isEmpty();
        assertThat(fixture.ledger().operationalCauseReviews("CRITIQUE").records()).hasSize(1);
    }

    @Test void tamperedReviewFailsLedgerQueryInsteadOfAppearingClean() throws Exception {
        Harness h = harness();
        Ledger ledger = h.ledgers().getFirst();
        ledger.attest("tamper_me", OUTAGE, NOTE, CASE);
        Path record = ledger.reviewsPath().resolve("tamper_me.json");
        Files.writeString(record, Files.readString(record) + BLANK);
        FindingsLedger query = ledger.ledger();
        assertThatThrownBy(() -> query.operationalCauseReviews(ROUTE))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
    }

    @Test void theLedgerQueryRefusesAnUnknownRouteInsteadOfReturningEmpty() throws Exception {
        Harness h = harness();
        FindingsLedger query = h.ledgers().getFirst().ledger();
        assertThatThrownBy(() -> query.operationalCauseReviews("NO_SUCH_ROUTE"))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
    }

    @Test void switchDecisionIsIdenticalForEmptyAndBogusOperationalSet() throws Exception {
        Harness h = harness();
        var report = report(h.sha(), RouteVerdictCalculator.Verdict.INCONCLUSIVE,
                Set.of(), ids(MISSING, CASE), null, Set.of());
        var composed = h.compose(report, false);
        Map<String, FindingsLedger> ledgers = new HashMap<>();
        Map<String, FindingsLedger.ExternalHeads> heads = new HashMap<>();
        for (Ledger ledger : h.ledgers()) {
            ledgers.put(ledger.hash(), ledger.ledger());
            heads.put(ledger.hash(), ledger.heads());
        }
        var empty = new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                false, false, Set.of(), Map.of(NOTE, NOT_TRIGGERED), composed.heads(), composed);
        var bogus = new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(CASE),
                false, false, Set.of(), Map.of(NOTE, NOT_TRIGGERED), composed.heads(), composed);
        var first = ProductionSwitchEligibility.check(ROUTE, report, empty, h.fixture().run(),
                h.fixture().frb(), ledgers, heads, Set.of());
        var second = ProductionSwitchEligibility.check(ROUTE, report, bogus, h.fixture().run(),
                h.fixture().frb(), ledgers, heads, Set.of());
        assertThat(first).isEqualTo(second);
        assertThat(first.eligible()).isFalse();
        assertThat(first.reasons()).contains(ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED,
                ProductionSwitchEligibility.CLASSIFICATION_BLOCKS_SWITCH);
    }
}
