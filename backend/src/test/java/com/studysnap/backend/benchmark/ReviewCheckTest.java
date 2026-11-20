package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.assertj.core.api.SoftAssertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewCheckTest {
    private static final String PARAMS = ReviewRecordStoreTest.PARAMS;
    private static final String INTERACTIVE = "INTERACTIVE_QUIZ";
    private static final String CRITIQUE = "CRITIQUE";
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String CREATED = "2026-11-16T00:04:00Z";
    private static final String DIGEST = "a".repeat(64);
    private static final Set<FindingsLedger.ReviewSubject> NO_EXTRAS = Set.of();
    @TempDir Path temp;

    record Fixture(Path root, PostLockContext context, Path lock, SafetyFindingStore safety,
                   EvidenceDisputeStore disputes, ReviewRecordStore reviews, FindingsLedger ledger,
                   String itemId, ConfirmedCriticalSource critical, ArmKeyAccess.OpenedKey opened) {
        FindingsLedger.ExternalHeads heads() {
            return new FindingsLedger.ExternalHeads(external(safety.headSha256()),
                    external(disputes.headSha256()), external(reviews.headSha256()));
        }
        FindingsLedger.ReviewCheck check(String route) { return ledger.checkLedger(route, Set.of(), heads()); }
        String hash() throws Exception { return IntegritySupport.detachedHash(lock); }
        void finding(String id) throws Exception {
            safety.create(new SafetyFinding(id, CREATED, hash(), itemId, NOTE, null,
                    List.of(INTERACTIVE), "Incorrect answer", "reviewer",
                    SafetyFinding.Attribution.GENERATOR, false), null);
        }
        void dispute(String id) throws Exception {
            disputes.create(new EvidenceDispute(id, CREATED, hash(), NOTE, itemId, null,
                    "reviewer", "Critical answer disputed", List.of(INTERACTIVE)), critical, opened);
        }
        void review(String id, ReviewRecord.Outcome outcome, String supersedes) throws Exception {
            reviews.create(new ReviewRecord(id, supersedes == null ? CREATED : "2026-11-16T00:05:00Z",
                    hash(), ReviewRecord.Kind.KEY_DEFECT,
                    NOTE, "defect", outcome, "owner", ReviewRecord.SignerRole.OWNER,
                    supersedes == null ? DIGEST : "b".repeat(64), supersedes));
        }
    }
    /** Static package-visible factory reusable by the next brief's tests. */
    static final class Fixtures {
        private Fixtures() {}
        static Fixture classA(Path root) throws Exception {
            Path locked = Files.createDirectories(root.resolve("locked"));
            var export = ArmKeyGateFixtures.classA(locked);
            String item = PairMap.fromPackets(locked.resolve("packets")).blindItemIds().stream()
                    .sorted().findFirst().orElseThrow();
            Path seal = ArmKeyGateFixtures.seal(locked, export.armKey());
            Path humanFile = ArmKeyGateFixtures.findings(locked);
            ObjectNode human = ClassAScoreSchemaV2Test.human(item);
            ClassAScoreSchemaV2Test.defect(human, "WRONG_KEY", 1);
            human.remove("critical");
            human.put("humanVerdict", "CRITICAL");
            Files.writeString(humanFile, human + "\n");
            Path lock = ArmKeyGateFixtures.lock(locked, seal, humanFile, LockRecord.Kind.PRIMARY);
            var opened = ArmKeyAccess.open(export.armKey(), seal,
                    List.of(ArmKeyGateFixtures.binding(lock, seal, humanFile)),
                    Set.of(LockRecord.Kind.PRIMARY), locked.resolve("access.jsonl"),
                    ArmKeyGateFixtures.OPENED, "review check test");
            return finish(root, locked, lock, seal, humanFile, item,
                    new ConfirmedCriticalSource.ClassA(ClassAScoreSchemaV2Test.MAPPING), opened);
        }
        static Fixture classB(Path root) throws Exception {
            Path locked = Files.createDirectories(root.resolve("locked"));
            var export = BlindReviewFixtures.classBAll(locked, false);
            Path seal = ArmKeyGateFixtures.seal(locked, locked.resolve("arm-key.jsonl"));
            Path findings = Files.createDirectories(locked.resolve("findings"));
            Path scores = findings.resolve("scores.jsonl");
            List<String> rows = new ArrayList<>();
            for (ClassBScoreAggregator.Key row : export.armKey())
                rows.add(score(row.itemId(), row.workflow().name()).toString());
            Files.writeString(scores, String.join("\n", rows) + "\n");
            Path lock = ArmKeyGateFixtures.lock(locked, seal, scores, LockRecord.Kind.PRIMARY);
            String item = export.armKey().stream().filter(row -> row.workflow() == ClassBCaseManifest.Workflow.STUDY_TIP
                    && row.arm() == ClassBScoreAggregator.Arm.CANDIDATE).findFirst().orElseThrow().itemId();
            return finish(root, locked, lock, seal, scores, item,
                    new ConfirmedCriticalSource.ClassB(Map.of("HUMAN_ONE", ClassBScoreFiles.ScorerKind.HUMAN)), null);
        }
        private static ObjectNode score(String item, String workflow) {
            ObjectNode row = IntegritySupport.JSON.createObjectNode();
            row.put("itemId", item).put("workflow", workflow).put("round", 1)
                    .put("scorerId", "HUMAN_ONE").put("scorerKind", "HUMAN")
                    .put("taskFulfilled", true).put("grounded", true).put("actionable", true)
                    .put("noCriticalMisleading", true).put("critical", false)
                    .put("borderline", false).put("adjudication", false).putNull("reasonCode");
            return row;
        }
        private static Fixture finish(Path root, Path locked, Path lock, Path seal,
                                      Path finding, String item, ConfirmedCriticalSource critical,
                                      ArmKeyAccess.OpenedKey opened) throws Exception {
            Path parameters = root.resolve("parameters.json");
            Files.copy(Path.of(PARAMS), parameters);
            Files.copy(Path.of(PARAMS + ".sha256"), IntegritySupport.detached(parameters));
            var context = new PostLockContext(lock, finding.getParent(), locked.resolve("packets"), seal, parameters);
            var safety = SafetyFindingStore.init(root.resolve("safety"), context, "2026-11-16T00:03:00Z");
            var disputes = EvidenceDisputeStore.init(root.resolve("disputes"), context, "2026-11-16T00:03:00Z");
            var reviews = ReviewRecordStore.init(root.resolve("reviews"), context, "2026-11-16T00:03:00Z");
            var ledger = new FindingsLedger(root.resolve("safety"), root.resolve("disputes"),
                    root.resolve("reviews"), context);
            return new Fixture(root, context, lock, safety, disputes, reviews, ledger, item, critical, opened);
        }
    }
    private static FindingsLedger.ExternalHead external(PostLockStoreIndex.Head head) {
        return new FindingsLedger.ExternalHead(head.seq(), head.sha256());
    }
    private static FindingsLedger.ExternalHeads replace(FindingsLedger.ExternalHeads heads,
                                                         String store, FindingsLedger.ExternalHead changed) {
        return new FindingsLedger.ExternalHeads(store.equals("safety") ? changed : heads.safety(),
                store.equals("disputes") ? changed : heads.disputes(),
                store.equals("reviews") ? changed : heads.reviews());
    }
    private static FindingsLedger.ExternalHead selected(FindingsLedger.ExternalHeads heads, String store) {
        return switch (store) {
            case "safety" -> heads.safety();
            case "disputes" -> heads.disputes();
            default -> heads.reviews();
        };
    }
    private static RouteVerdictCalculator.RouteReport report(String sha, Map<String, RouteVerdictCalculator.WorkflowReport> rows) {
        return new RouteVerdictCalculator.RouteReport(null, Set.of(), Set.of(), 0, false, sha, rows);
    }
    @Test void currentHeadsMintImmutableTokenWithLockCoverage() throws Exception {
        Fixture f = Fixtures.classA(temp.resolve("a"));
        var noted = f.heads();
        var token = f.ledger().checkLedger(INTERACTIVE, Set.of(), noted);
        assertThat(token.route()).isEqualTo(INTERACTIVE);
        assertThat(token.coveredWorkflows()).containsExactly(NOTE);
        assertThat(token.missing()).containsExactly("CLASS_B_WORKFLOW:STUDY_TIP:STUDY_TIP");
        assertThat(token.extras()).isEmpty();
        assertThat(token.openSafetyFindingIds()).isEmpty();
        assertThat(token.openDisputeIds()).isEmpty();
        assertThat(token.openConcernIds()).isEmpty();
        assertThat(token.heads()).containsEntry("safety", "0:" + noted.safety().sha256())
                .containsEntry("disputes", "0:" + noted.disputes().sha256())
                .containsEntry("reviews", "0:" + noted.reviews().sha256());
        assertThat(token.lockSha256()).isEqualTo(f.hash());
        assertThat(token).isEqualTo(f.ledger().checkLedger(INTERACTIVE, Set.of(), noted)).hasSameHashCodeAs(
                f.ledger().checkLedger(INTERACTIVE, Set.of(), noted));
        assertThat(f.check(CRITIQUE).coveredWorkflows()).isEmpty();
    }
    @Test void allThreeHeadsRequireExactExternalNote() throws Exception {
        Fixture f = Fixtures.classA(temp.resolve("a"));
        FindingsLedger ledger = f.ledger();
        var genesis = f.heads();
        f.finding("finding_1");
        f.dispute("dispute_1");
        f.review("review_1", ReviewRecord.Outcome.COMPLETE, null);
        var current = f.heads();
        SoftAssertions softly = new SoftAssertions();
        for (String store : List.of("safety", "disputes", "reviews")) {
            var head = selected(current, store);
            Map<String, FindingsLedger.ExternalHead> wrong = Map.of(
                    "stale", selected(genesis, store),
                    "ahead", new FindingsLedger.ExternalHead(head.seq() + 1, head.sha256()),
                    "other-seq", new FindingsLedger.ExternalHead(head.seq() + 2, head.sha256()),
                    "wrong-digest", new FindingsLedger.ExternalHead(head.seq(), "0".repeat(64)));
            for (var variant : wrong.entrySet()) {
                var noted = replace(current, store, variant.getValue());
                Throwable failure = org.assertj.core.api.Assertions.catchThrowable(
                        () -> ledger.checkLedger(INTERACTIVE, NO_EXTRAS, noted));
                softly.assertThat(failure).as(store + " " + variant.getKey())
                        .isInstanceOf(FindingsLedger.InvalidLedger.class);
            }
        }
        softly.assertAll();
        assertThat(ledger.checkLedger(INTERACTIVE, Set.of(), current)).isNotNull();
    }
    @Test void everyAppendRequiresANewNote() throws Exception {
        Fixture f = Fixtures.classA(temp.resolve("a"));
        FindingsLedger ledger = f.ledger();
        var noted = f.heads();
        f.finding("finding_1");
        var beforeFinding = noted;
        assertThatThrownBy(() -> ledger.checkLedger(INTERACTIVE, NO_EXTRAS, beforeFinding))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        noted = f.heads();
        f.dispute("dispute_1");
        var beforeDispute = noted;
        assertThatThrownBy(() -> ledger.checkLedger(INTERACTIVE, NO_EXTRAS, beforeDispute))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        noted = f.heads();
        f.review("review_1", ReviewRecord.Outcome.COMPLETE, null);
        var beforeReview = noted;
        assertThatThrownBy(() -> ledger.checkLedger(INTERACTIVE, NO_EXTRAS, beforeReview))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        assertThat(f.check(INTERACTIVE)).isNotNull();
    }
    @Test void openRecordsAreReportedBeyondRequiredSubjectsAndClearedConcernIsRemoved() throws Exception {
        Fixture f = Fixtures.classA(temp.resolve("a"));
        f.finding("finding_1");
        f.dispute("dispute_1");
        f.review("concern_1", ReviewRecord.Outcome.BLOCKING_CONCERN, null);
        var token = f.check(INTERACTIVE);
        assertThat(token.openSafetyFindingIds()).containsExactly("finding_1");
        assertThat(token.openDisputeIds()).containsExactly("dispute_1");
        assertThat(token.openConcernIds()).containsExactly("concern_1");
        assertThat(token.missing()).containsExactly("CLASS_B_WORKFLOW:STUDY_TIP:STUDY_TIP");
        f.review("clear_1", ReviewRecord.Outcome.COMPLETE, "concern_1");
        assertThat(f.check(INTERACTIVE).openConcernIds()).isEmpty();
    }
    @Test void extrasAreCopiedAndEveryAccessorIsUnmodifiable() throws Exception {
        Fixture f = Fixtures.classA(temp.resolve("a"));
        f.finding("finding_1");
        f.dispute("dispute_1");
        f.review("concern_1", ReviewRecord.Outcome.BLOCKING_CONCERN, null);
        var extra = new FindingsLedger.ReviewSubject(ReviewRecord.Kind.KEY_DEFECT, NOTE, "defect");
        var quality = new FindingsLedger.ReviewSubject(ReviewRecord.Kind.ABSOLUTE_QUALITY, NOTE, "quality");
        Set<FindingsLedger.ReviewSubject> extras = new HashSet<>(Set.of(extra, quality));
        var token = f.ledger().checkLedger(INTERACTIVE, extras, f.heads());
        extras.clear();
        assertThat(token.extras()).containsExactly(quality, extra);
        assertThat(token.missing()).contains("KEY_DEFECT:NOTE_ADAPTIVE:defect");
        Set<String> coverage = token.coveredWorkflows(), missing = token.missing();
        Set<FindingsLedger.ReviewSubject> savedExtras = token.extras();
        Set<String> safety = token.openSafetyFindingIds(), disputes = token.openDisputeIds();
        Set<String> concerns = token.openConcernIds();
        Map<String, String> heads = token.heads();
        assertThatThrownBy(coverage::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(missing::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(savedExtras::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(safety::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(disputes::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(concerns::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(heads::clear).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void tokenConstructorIsPrivateAndClassIsNotARecord() {
        assertThat(FindingsLedger.ReviewCheck.class.isRecord()).isFalse();
        assertThat(FindingsLedger.ReviewCheck.class.getDeclaredConstructors())
                .allSatisfy(constructor -> assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue());
    }
    @Test void everyTokenComponentParticipatesInEqualityAndHash() throws Exception {
        Fixture f = Fixtures.classA(temp.resolve("a"));
        var original = f.check(INTERACTIVE);
        var constructor = FindingsLedger.ReviewCheck.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] components = {original.route(), original.coveredWorkflows(), original.missing(),
                original.extras(), original.openSafetyFindingIds(), original.openDisputeIds(),
                original.openConcernIds(), original.heads(), original.lockSha256()};
        Object[] alternatives = {CRITIQUE, Set.of("STUDY_TIP"), Set.of("other_missing"),
                Set.of(new FindingsLedger.ReviewSubject(ReviewRecord.Kind.KEY_DEFECT, NOTE, "defect")),
                Set.of("finding_1"), Set.of("dispute_1"), Set.of("concern_1"),
                Map.of("safety", "0:" + DIGEST), "0".repeat(64)};
        for (int index = 0; index < components.length; index++) {
            Object[] changedComponents = components.clone();
            changedComponents[index] = alternatives[index];
            var changed = (FindingsLedger.ReviewCheck) constructor.newInstance(changedComponents);
            assertThat(changed).as("equality component " + index).isNotEqualTo(original);
            assertThat(changed.hashCode()).as("hash component " + index).isNotEqualTo(original.hashCode());
        }
    }
    @Test void noReviewStoreAndMissingNotesAreRefused() throws Exception {
        Fixture f = Fixtures.classA(temp.resolve("a"));
        FindingsLedger ledger = f.ledger();
        var legacy = new FindingsLedger(f.root().resolve("safety"), f.root().resolve("disputes"), f.context());
        var noted = f.heads();
        assertThatThrownBy(() -> legacy.checkLedger(INTERACTIVE, NO_EXTRAS, noted))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        assertThatThrownBy(() -> ledger.checkLedger(INTERACTIVE, NO_EXTRAS, null))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        var notedDisputes = noted.disputes();
        var notedReviews = noted.reviews();
        assertThatThrownBy(() -> new FindingsLedger.ExternalHeads(null, notedDisputes, notedReviews))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        assertThatThrownBy(() -> new FindingsLedger.ExternalHead(-1, DIGEST))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        assertThatThrownBy(() -> new FindingsLedger.ExternalHead(0, "bad"))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
    }
    @Test void damagedStoresAreRefused() throws Exception {
        for (String store : List.of("safety", "disputes", "reviews"))
            for (String damage : List.of("tamper", "truncate", "orphan")) {
                Fixture f = Fixtures.classA(temp.resolve(store + damage));
                FindingsLedger ledger = f.ledger();
                var noted = f.heads();
                assertThat(ledger.checkLedger(INTERACTIVE, NO_EXTRAS, noted)).isNotNull();
                Path dir = f.root().resolve(store);
                switch (damage) {
                    case "tamper" -> Files.writeString(dir.resolve("index.jsonl"), "tampered\n");
                    case "truncate" -> Files.writeString(dir.resolve("index.jsonl"), "");
                    default -> Files.writeString(dir.resolve("orphan.json"), "{}");
                }
                assertThatThrownBy(() -> ledger.checkLedger(INTERACTIVE, NO_EXTRAS, noted))
                        .as(store + " " + damage).isInstanceOf(FindingsLedger.InvalidLedger.class);
            }
    }
    @Test void classBLockFullyCoversCritiqueAndPartlyCoversMixedRoutes() throws Exception {
        Fixture f = Fixtures.classB(temp.resolve("b"));
        assertThat(f.check(CRITIQUE).coveredWorkflows()).containsExactly("ASK_COMPANION", "INTERVIEW_CRITIQUE");
        assertThat(f.check(INTERACTIVE).coveredWorkflows()).containsExactly("STUDY_TIP");
        assertThat(f.check("PAID_INTERACTIVE").coveredWorkflows()).containsExactly("COLLECTION_COMPANION");
        String hash = f.hash();
        for (String workflow : List.of("ASK_COMPANION", "INTERVIEW_CRITIQUE"))
            f.reviews().create(new ReviewRecord("complete_" + workflow.toLowerCase(), CREATED, hash,
                    ReviewRecord.Kind.CLASS_B_WORKFLOW, workflow, workflow,
                    ReviewRecord.Outcome.COMPLETE, "owner", ReviewRecord.SignerRole.OWNER, DIGEST));
        assertThat(f.check(CRITIQUE).missing()).isEmpty();
    }
    @Test void openConcernsAreScopedToTheRouteOfTheirWorkflow() throws Exception {
        Fixture f = Fixtures.classB(temp.resolve("b"));
        f.reviews().create(new ReviewRecord("concern_tip", CREATED, f.hash(), ReviewRecord.Kind.KEY_DEFECT,
                "STUDY_TIP", "defect", ReviewRecord.Outcome.BLOCKING_CONCERN, "owner",
                ReviewRecord.SignerRole.OWNER, DIGEST));
        assertThat(f.check(INTERACTIVE).openConcernIds()).containsExactly("concern_tip");
        assertThat(f.check(CRITIQUE).openConcernIds()).isEmpty();
        assertThat(f.check("PAID_INTERACTIVE").openConcernIds()).isEmpty();
    }
    @Test void reportBindingRequiresExactRouteKeysAndParametersHash() throws Exception {
        Fixture f = Fixtures.classB(temp.resolve("b"));
        FindingsLedger ledger = f.ledger();
        String sha = f.context().verify().parameters().sha256;
        Set<String> declared = Set.copyOf(f.context().verify().parameters().routes.get(CRITIQUE).workflows());
        // Placeholder rows are sufficient: requireReport binds keys and trusts runFrb's row contents.
        var rows = new java.util.HashMap<String, RouteVerdictCalculator.WorkflowReport>();
        declared.forEach(name -> rows.put(name, null));
        ledger.requireReport(CRITIQUE, report(sha, rows));
        var badHashReport = report(DIGEST, rows);
        assertThatThrownBy(() -> ledger.requireReport(CRITIQUE, badHashReport))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        var missing = new java.util.HashMap<>(rows);
        missing.remove("ASK_COMPANION");
        var missingReport = report(sha, missing);
        assertThatThrownBy(() -> ledger.requireReport(CRITIQUE, missingReport))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        var added = new java.util.HashMap<>(rows);
        added.put("UNDECLARED", null);
        var addedReport = report(sha, added);
        assertThatThrownBy(() -> ledger.requireReport(CRITIQUE, addedReport))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        var other = new java.util.HashMap<>(rows);
        other.remove("ASK_COMPANION");
        other.put("STUDY_TIP", null);
        var otherReport = report(sha, other);
        assertThatThrownBy(() -> ledger.requireReport(CRITIQUE, otherReport))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        var emptyReport = report(sha, Map.of());
        assertThatThrownBy(() -> ledger.requireReport(CRITIQUE, emptyReport))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        var nullReport = report(sha, null);
        assertThatThrownBy(() -> ledger.requireReport(CRITIQUE, nullReport))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
    }
}
