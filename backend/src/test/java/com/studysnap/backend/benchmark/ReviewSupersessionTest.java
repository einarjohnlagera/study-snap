package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewSupersessionTest {
    private static final String TIP = "STUDY_TIP";
    private static final String ASK = "ASK_COMPANION";
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String T1 = "2026-11-16T00:04:00Z";
    private static final String T2 = "2026-11-16T00:05:00Z";
    private static final String T3 = "2026-11-16T00:06:00Z";
    private static final String A = "a".repeat(64);
    private static final String B = "b".repeat(64);
    private static final String CONCERN = "concern_1";
    private static final String CLEAR = "clear_1";
    private static final String MISSING_TIP = "CLASS_B_WORKFLOW:STUDY_TIP:STUDY_TIP";
    private static final String MISSING_DEFECT = "KEY_DEFECT:STUDY_TIP:item_1";
    @TempDir Path temp;

    private ReviewRecord record(ReviewRecordStoreTest.Fixture f, String id, String time,
                                ReviewRecord.Kind kind, String workflow, String subject,
                                ReviewRecord.Outcome outcome, ReviewRecord.SignerRole role,
                                String digest, String supersedes) throws Exception {
        return new ReviewRecord(id, time, IntegritySupport.detachedHash(f.lock()), kind,
                workflow, subject, outcome, "owner", role, digest, supersedes,
                supersedes == null ? null : ReviewRecord.ResolutionCode.FIXED);
    }
    private ReviewRecord concern(ReviewRecordStoreTest.Fixture f) throws Exception {
        return record(f, CONCERN, T1, ReviewRecord.Kind.KEY_DEFECT, TIP, "item_1",
                ReviewRecord.Outcome.BLOCKING_CONCERN, ReviewRecord.SignerRole.OWNER, A, null);
    }
    private ReviewRecord clearing(ReviewRecordStoreTest.Fixture f) throws Exception {
        return record(f, CLEAR, T2, ReviewRecord.Kind.KEY_DEFECT, TIP, "item_1",
                ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER, B, CONCERN);
    }
    private void refusedCreate(ReviewRecordStore store, ReviewRecord candidate) {
        List<ReviewRecord> before = store.records();
        assertThatThrownBy(() -> store.create(candidate)).isInstanceOf(ReviewRecordStore.InvalidStore.class);
        assertThat(store.records()).isEqualTo(before);
    }
    private static void rewrite(ReviewRecordStoreTest.Fixture f, String reviewId, int indexLine,
                                String field, String value, boolean remove, boolean nullValue) throws Exception {
        Path file = f.root().resolve("reviews/" + reviewId + ".json");
        ObjectNode node = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(file));
        if (remove) node.remove(field);
        else if (nullValue) node.putNull(field);
        else node.put(field, value);
        Files.write(file, IntegritySupport.JSON.writeValueAsBytes(node));
        Files.writeString(IntegritySupport.detached(file), FreezeRecord.sha256(file) + "\n");
        Path index = f.root().resolve("reviews/index.jsonl");
        String[] lines = Files.readString(index).split("\n");
        ObjectNode entry = (ObjectNode) IntegritySupport.JSON.readTree(lines[indexLine]);
        entry.put("fileSha256", FreezeRecord.sha256(file));
        lines[indexLine] = IntegritySupport.JSON.writeValueAsString(entry);
        for (int i = indexLine + 1; i < lines.length; i++) {
            ObjectNode successor = (ObjectNode) IntegritySupport.JSON.readTree(lines[i]);
            successor.put("prevSha256", FreezeRecord.sha256(lines[i - 1].getBytes(StandardCharsets.UTF_8)));
            lines[i] = IntegritySupport.JSON.writeValueAsString(successor);
        }
        Files.writeString(index, String.join("\n", lines) + "\n");
    }
    private static void refusedRead(ReviewRecordStore store) {
        assertThatThrownBy(store::records).isInstanceOf(ReviewRecordStore.InvalidStore.class);
        assertThatThrownBy(() -> store.load(CLEAR)).isInstanceOf(ReviewRecordStore.InvalidStore.class);
        assertThatThrownBy(store::headSha256).isInstanceOf(ReviewRecordStore.InvalidStore.class);
        assertThatThrownBy(store::openConcerns).isInstanceOf(ReviewRecordStore.InvalidStore.class);
    }

    @Test void validControlAndNullFieldRoundTrip() throws Exception {
        var f = ReviewRecordStoreTest.fixture(temp.resolve("valid"));
        ReviewRecord concern = concern(f);
        ReviewRecord clearing = clearing(f);
        f.reviews().create(concern);
        assertThat(f.reviews().openConcerns()).containsExactly(CONCERN);
        assertThat(f.reviews().create(clearing)).isEqualTo(clearing);
        assertThat(f.reviews().records()).containsExactly(concern, clearing);
        assertThat(f.reviews().openConcerns()).isEmpty();
        Path file = f.root().resolve("reviews/concern_1.json");
        assertThat(IntegritySupport.JSON.readTree(Files.readAllBytes(file)).get("supersedes").isNull()).isTrue();
    }

    @Test void createRefusesEveryRuleBeforeAppend() throws Exception {
        var f = ReviewRecordStoreTest.fixture(temp.resolve("create"));
        ReviewRecordStore store = f.reviews();
        store.create(concern(f));
        ReviewRecord good = clearing(f);
        refusedCreate(store, copy(good, good.kind(), good.workflow(), good.subjectId(),
                ReviewRecord.Outcome.BLOCKING_CONCERN, good.signerRole(), B, CONCERN));
        // PRODUCT_UX COMPLETE is already refused by the original Product UX role rule.
        refusedCreate(store, copy(good, good.kind(), good.workflow(), good.subjectId(),
                good.outcome(), ReviewRecord.SignerRole.PRODUCT_UX, B, CONCERN));
        refusedCreate(store, copy(good, good.kind(), good.workflow(), good.subjectId(),
                good.outcome(), good.signerRole(), B, "unknown_1"));
        refusedCreate(store, copy(good, good.kind(), good.workflow(), good.subjectId(),
                good.outcome(), good.signerRole(), B, "UPPER"));
        refusedCreate(store, copy(good, ReviewRecord.Kind.ABSOLUTE_QUALITY, TIP, "item_1",
                good.outcome(), good.signerRole(), B, CONCERN));
        refusedCreate(store, copy(good, good.kind(), ASK, "item_1",
                good.outcome(), good.signerRole(), B, CONCERN));
        refusedCreate(store, copy(good, good.kind(), TIP, "item_2",
                good.outcome(), good.signerRole(), B, CONCERN));
        refusedCreate(store, copy(good, good.kind(), TIP, "item_1",
                good.outcome(), good.signerRole(), A, CONCERN));
        ReviewRecord early = record(f, CLEAR, T1, good.kind(), TIP, "item_1",
                good.outcome(), good.signerRole(), B, CONCERN);
        refusedCreate(store, early);
        store.create(record(f, "complete_1", T1, good.kind(), TIP, "item_1",
                good.outcome(), good.signerRole(), A, null));
        refusedCreate(store, copy(good, good.kind(), TIP, "item_1",
                good.outcome(), good.signerRole(), B, "complete_1"));
        store.create(record(f, "quality_concern", T1, ReviewRecord.Kind.ABSOLUTE_QUALITY,
                TIP, "quality_1", ReviewRecord.Outcome.BLOCKING_CONCERN,
                ReviewRecord.SignerRole.OWNER, A, null));
        refusedCreate(store, record(f, "ux_concern", T2, ReviewRecord.Kind.ABSOLUTE_QUALITY,
                TIP, "quality_1", ReviewRecord.Outcome.BLOCKING_CONCERN,
                ReviewRecord.SignerRole.PRODUCT_UX, B, "quality_concern"));
        store.create(record(f, "concern_2", T1, good.kind(), TIP, "item_1",
                ReviewRecord.Outcome.BLOCKING_CONCERN, ReviewRecord.SignerRole.OWNER, A, null));
        store.create(good);
        ReviewRecord validSecond = record(f, "clear_2", T3, good.kind(), TIP, "item_1",
                good.outcome(), good.signerRole(), B, "concern_2");
        ReviewRecord duplicate = new ReviewRecord(validSecond.reviewId(), validSecond.recordedAtUtc(),
                validSecond.lockSha256(), validSecond.kind(), validSecond.workflow(), validSecond.subjectId(),
                validSecond.outcome(), validSecond.signer(), validSecond.signerRole(),
                validSecond.evidenceSha256(), CONCERN, ReviewRecord.ResolutionCode.FIXED);
        refusedCreate(store, duplicate);
        assertThat(store.create(validSecond)).isEqualTo(validSecond);
    }
    private static ReviewRecord copy(ReviewRecord r, ReviewRecord.Kind kind, String workflow,
                                     String subject, ReviewRecord.Outcome outcome,
                                     ReviewRecord.SignerRole role, String digest, String supersedes) {
        return new ReviewRecord(r.reviewId(), r.recordedAtUtc(), r.lockSha256(), kind,
                workflow, subject, outcome, r.signer(), role, digest, supersedes,
                supersedes == null ? null : ReviewRecord.ResolutionCode.FIXED);
    }

    @Test void everySemanticRuleIsRecheckedOnRead() throws Exception {
        for (String fault : List.of("outcome", "role", "unknown", "bad_id", "kind", "workflow",
                "subjectId", "digest", "time", "complete_target", "missing_field", "extra_field")) {
            var f = ReviewRecordStoreTest.fixture(temp.resolve(fault));
            ReviewRecordStore store = f.reviews();
            store.create(concern(f));
            store.create(record(f, "complete_1", T1, ReviewRecord.Kind.KEY_DEFECT, TIP, "item_1",
                    ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER, A, null));
            store.create(clearing(f));
            switch (fault) {
                case "outcome" -> rewrite(f, CLEAR, 3, "outcome", "BLOCKING_CONCERN", false, false);
                case "role" -> rewrite(f, CLEAR, 3, "signerRole", "PRODUCT_UX", false, false);
                case "unknown" -> rewrite(f, CLEAR, 3, "supersedes", "unknown_1", false, false);
                case "bad_id" -> rewrite(f, CLEAR, 3, "supersedes", "UPPER", false, false);
                case "kind" -> rewrite(f, CLEAR, 3, "kind", "ABSOLUTE_QUALITY", false, false);
                case "workflow" -> rewrite(f, CLEAR, 3, "workflow", ASK, false, false);
                case "subjectId" -> rewrite(f, CLEAR, 3, "subjectId", "item_2", false, false);
                case "digest" -> rewrite(f, CLEAR, 3, "evidenceSha256", A, false, false);
                case "time" -> rewrite(f, CLEAR, 3, "recordedAtUtc", T1, false, false);
                case "complete_target" -> rewrite(f, CLEAR, 3, "supersedes", "complete_1", false, false);
                case "missing_field" -> rewrite(f, CLEAR, 3, "supersedes", null, true, false);
                default -> rewrite(f, CLEAR, 3, "unknownField", "value", false, false);
            }
            refusedRead(store);
        }
    }

    @Test void readRejectsForwardReferenceAndDuplicateClearing() throws Exception {
        var f = ReviewRecordStoreTest.fixture(temp.resolve("forward"));
        ReviewRecordStore store = f.reviews();
        store.create(record(f, "review_1", T2, ReviewRecord.Kind.KEY_DEFECT, TIP, "item_1",
                ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER, B, null));
        store.create(record(f, "review_2", T1, ReviewRecord.Kind.KEY_DEFECT, TIP, "item_1",
                ReviewRecord.Outcome.BLOCKING_CONCERN, ReviewRecord.SignerRole.OWNER, A, null));
        rewrite(f, "review_1", 1, "supersedes", "review_2", false, false);
        refusedRead(store);

        var second = ReviewRecordStoreTest.fixture(temp.resolve("duplicate"));
        ReviewRecordStore other = second.reviews();
        other.create(concern(second));
        other.create(record(second, "concern_2", T1, ReviewRecord.Kind.KEY_DEFECT, TIP, "item_1",
                ReviewRecord.Outcome.BLOCKING_CONCERN, ReviewRecord.SignerRole.OWNER, A, null));
        other.create(clearing(second));
        other.create(record(second, "clear_2", T3, ReviewRecord.Kind.KEY_DEFECT, TIP, "item_1",
                ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER, B, "concern_2"));
        rewrite(second, "clear_2", 4, "supersedes", CONCERN, false, false);
        refusedRead(other);
    }

    @Test void strictNullFieldAndConcernCannotCarrySupersedes() throws Exception {
        var f = ReviewRecordStoreTest.fixture(temp.resolve("null"));
        ReviewRecordStore store = f.reviews();
        store.create(concern(f));
        rewrite(f, CONCERN, 1, "supersedes", null, false, true);
        assertThat(store.records()).hasSize(1);
        rewrite(f, CONCERN, 1, "supersedes", CONCERN, false, false);
        refusedRead(store);

        var owner = ReviewRecordStoreTest.fixture(temp.resolve("owner_concern"));
        owner.reviews().create(concern(owner));
        owner.reviews().create(record(owner, "owner_concern", T2, ReviewRecord.Kind.KEY_DEFECT,
                TIP, "item_1", ReviewRecord.Outcome.BLOCKING_CONCERN,
                ReviewRecord.SignerRole.OWNER, B, null));
        rewrite(owner, "owner_concern", 2, "supersedes", CONCERN, false, false);
        refusedRead(owner.reviews());

        var ux = ReviewRecordStoreTest.fixture(temp.resolve("product_ux"));
        ux.reviews().create(record(ux, "quality_concern", T1, ReviewRecord.Kind.ABSOLUTE_QUALITY,
                TIP, "quality_1", ReviewRecord.Outcome.BLOCKING_CONCERN,
                ReviewRecord.SignerRole.OWNER, A, null));
        ux.reviews().create(record(ux, "ux_concern", T2, ReviewRecord.Kind.ABSOLUTE_QUALITY,
                TIP, "quality_1", ReviewRecord.Outcome.BLOCKING_CONCERN,
                ReviewRecord.SignerRole.PRODUCT_UX, B, null));
        rewrite(ux, "ux_concern", 2, "supersedes", "quality_concern", false, false);
        refusedRead(ux.reviews());
    }

    @Test void requiredSubjectTruthTableAndRouteScope() throws Exception {
        var f = ReviewRecordStoreTest.fixture(temp.resolve("ledger"));
        ReviewRecordStore store = f.reviews();
        FindingsLedger ledger = f.ledger();
        var defect = new FindingsLedger.ReviewSubject(ReviewRecord.Kind.KEY_DEFECT, TIP, "item_1");
        Set<FindingsLedger.ReviewSubject> extras = Set.of(defect);
        assertThat(ledger.missingRequiredReviews(ROUTE, extras)).containsExactly(MISSING_TIP, MISSING_DEFECT);
        store.create(record(f, "tip_1", T1, ReviewRecord.Kind.CLASS_B_WORKFLOW, TIP, TIP,
                ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER, A, null));
        store.create(concern(f));
        assertThat(ledger.missingRequiredReviews(ROUTE, extras)).containsExactly(MISSING_DEFECT);
        assertThat(store.openConcerns()).containsExactly(CONCERN);
        store.create(record(f, "unnamed_1", T2, ReviewRecord.Kind.KEY_DEFECT, TIP, "item_1",
                ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER, B, null));
        assertThat(ledger.missingRequiredReviews(ROUTE, extras)).containsExactly(MISSING_DEFECT);
        assertThat(ledger.blocksBenchmarkPass(ROUTE)).isTrue();
        assertThat(ledger.blocksProductionSwitch(ROUTE)).isTrue();
        store.create(clearing(f));
        assertThat(ledger.missingRequiredReviews(ROUTE, extras)).isEmpty();
        assertThat(store.openConcerns()).isEmpty();
        store.create(record(f, "concern_2", T2, ReviewRecord.Kind.KEY_DEFECT, TIP, "item_1",
                ReviewRecord.Outcome.BLOCKING_CONCERN, ReviewRecord.SignerRole.OWNER, A, null));
        assertThat(ledger.missingRequiredReviews(ROUTE, extras)).containsExactly(MISSING_DEFECT);
        assertThat(store.openConcerns()).containsExactly("concern_2");
        store.create(record(f, "unrelated_1", T3, ReviewRecord.Kind.KEY_DEFECT, TIP, "item_2",
                ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER, B, null));
        assertThat(ledger.missingRequiredReviews(ROUTE, extras)).containsExactly(MISSING_DEFECT);
        store.create(record(f, "nonrequired_1", T1, ReviewRecord.Kind.KEY_DEFECT, ASK, "item_9",
                ReviewRecord.Outcome.BLOCKING_CONCERN, ReviewRecord.SignerRole.OWNER, A, null));
        assertThat(store.openConcerns()).containsExactly("concern_2", "nonrequired_1");
        store.create(record(f, "clear_2", T3, ReviewRecord.Kind.KEY_DEFECT, TIP, "item_1",
                ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER, B, "concern_2"));
        assertThat(ledger.missingRequiredReviews(ROUTE, extras)).isEmpty();
        assertThat(ledger.missingRequiredReviews(ROUTE, Set.of())).isEmpty();
        assertThat(store.openConcerns()).containsExactly("nonrequired_1");
    }

    @Test void openClassBConcernBlocksBothFourArgumentGates() throws Exception {
        var f = ReviewRecordStoreTest.fixture(temp.resolve("gates"));
        FindingsLedger ledger = f.ledger();
        f.reviews().create(record(f, "tip_1", T1, ReviewRecord.Kind.CLASS_B_WORKFLOW, TIP, TIP,
                ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER, A, null));
        assertThat(ledger.blocksBenchmarkPass(ROUTE)).isFalse();
        assertThat(ledger.blocksProductionSwitch(ROUTE)).isFalse();
        f.reviews().create(record(f, "tip_2", T2, ReviewRecord.Kind.CLASS_B_WORKFLOW, TIP, TIP,
                ReviewRecord.Outcome.BLOCKING_CONCERN, ReviewRecord.SignerRole.OWNER, A, null));
        assertThat(ledger.blocksBenchmarkPass(ROUTE)).isTrue();
        assertThat(ledger.blocksProductionSwitch(ROUTE)).isTrue();
    }

    @Test void unrelatedCompletionAndNonRequiredConcernDoNotChangeRequiredResult() throws Exception {
        var f = ReviewRecordStoreTest.fixture(temp.resolve("unrelated"));
        FindingsLedger ledger = f.ledger();
        var defect = new FindingsLedger.ReviewSubject(ReviewRecord.Kind.KEY_DEFECT, TIP, "item_1");
        Set<FindingsLedger.ReviewSubject> required = Set.of(defect);
        f.reviews().create(record(f, "tip_1", T1, ReviewRecord.Kind.CLASS_B_WORKFLOW, TIP, TIP,
                ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER, A, null));
        f.reviews().create(record(f, "defect_complete", T1, ReviewRecord.Kind.KEY_DEFECT, TIP, "item_1",
                ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER, A, null));
        f.reviews().create(concern(f));
        f.reviews().create(record(f, "unrelated_1", T2, ReviewRecord.Kind.KEY_DEFECT, TIP, "item_2",
                ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER, B, null));
        assertThat(ledger.missingRequiredReviews(ROUTE, required)).containsExactly(MISSING_DEFECT);
        f.reviews().create(clearing(f));
        f.reviews().create(record(f, "quality_concern", T3, ReviewRecord.Kind.ABSOLUTE_QUALITY,
                TIP, "quality_1", ReviewRecord.Outcome.BLOCKING_CONCERN,
                ReviewRecord.SignerRole.PRODUCT_UX, A, null));
        assertThat(f.reviews().openConcerns()).containsExactly("quality_concern");
        assertThat(ledger.missingRequiredReviews(ROUTE, required)).isEmpty();
        assertThat(ledger.missingRequiredReviews(ROUTE, Set.of())).isEmpty();
    }
}
