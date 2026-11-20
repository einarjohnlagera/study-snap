package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
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

class ReviewResolutionAndConcernBlockTest {
    private static final String TIP = "STUDY_TIP";
    private static final String ASK = "ASK_COMPANION";
    private static final String INTERACTIVE = "INTERACTIVE_QUIZ";
    private static final String CRITIQUE = "CRITIQUE";
    private static final String PAID = "PAID_INTERACTIVE";
    private static final String T1 = "2026-11-16T00:04:00Z";
    private static final String T2 = "2026-11-16T00:05:00Z";
    private static final String A = "a".repeat(64);
    private static final String B = "b".repeat(64);
    private static final String CONCERN = "concern_1";
    private static final String CLEAR = "clear_1";
    private static final String CODE_FIELD = "resolutionCode";
    private static final String SUPERSEDES_FIELD = "supersedes";
    @TempDir Path temp;

    private static ReviewRecord review(ReviewRecordStoreTest.Fixture f, String id, String time,
                                       ReviewRecord.Kind kind, String workflow, String subject,
                                       ReviewRecord.Outcome outcome, ReviewRecord.SignerRole role,
                                       String digest, String supersedes,
                                       ReviewRecord.ResolutionCode code) throws Exception {
        return new ReviewRecord(id, time, IntegritySupport.detachedHash(f.lock()), kind,
                workflow, subject, outcome, "owner", role, digest, supersedes, code);
    }
    private static ReviewRecord concern(ReviewRecordStoreTest.Fixture f) throws Exception {
        return review(f, CONCERN, T1, ReviewRecord.Kind.KEY_DEFECT, TIP, "item_1",
                ReviewRecord.Outcome.BLOCKING_CONCERN, ReviewRecord.SignerRole.OWNER, A, null, null);
    }
    private static ReviewRecord clearing(ReviewRecordStoreTest.Fixture f,
                                         ReviewRecord.ResolutionCode code) throws Exception {
        return review(f, CLEAR, T2, ReviewRecord.Kind.KEY_DEFECT, TIP, "item_1",
                ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER, B, CONCERN, code);
    }
    private static void refusedRead(ReviewRecordStore store) {
        assertThatThrownBy(store::records).isInstanceOf(ReviewRecordStore.InvalidStore.class);
    }
    private static void rewrite(ReviewRecordStoreTest.Fixture f, String reviewId, int indexLine,
                                String field, String value, boolean remove) throws Exception {
        Path file = f.root().resolve("reviews/" + reviewId + ".json");
        ObjectNode node = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(file));
        if (remove) node.remove(field);
        else if (value == null) node.putNull(field);
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
    private static JsonNode stored(ReviewRecordStoreTest.Fixture f, String id) throws Exception {
        return IntegritySupport.JSON.readTree(Files.readAllBytes(f.root().resolve("reviews/" + id + ".json")));
    }

    @Test void eachBoundedCodeClearsAndIsStored() throws Exception {
        for (ReviewRecord.ResolutionCode code : ReviewRecord.ResolutionCode.values()) {
            var f = ReviewRecordStoreTest.fixture(temp.resolve(code.name()));
            f.reviews().create(concern(f));
            assertThat(stored(f, CONCERN).has(CODE_FIELD)).isTrue();
            assertThat(stored(f, CONCERN).get(CODE_FIELD).isNull()).isTrue();
            ReviewRecord clear = clearing(f, code);
            assertThat(f.reviews().create(clear)).isEqualTo(clear);
            assertThat(stored(f, CLEAR).get(CODE_FIELD).asText()).isEqualTo(code.name());
            assertThat(f.reviews().openConcerns()).isEmpty();
        }
    }

    @Test void missingCodeIsRefusedOnCreateAndRead() throws Exception {
        var f = ReviewRecordStoreTest.fixture(temp.resolve("missing"));
        f.reviews().create(concern(f));
        ReviewRecord noCode = clearing(f, null);
        assertThatThrownBy(() -> f.reviews().create(noCode)).isInstanceOf(ReviewRecordStore.InvalidStore.class);
        f.reviews().create(clearing(f, ReviewRecord.ResolutionCode.FIXED));
        rewrite(f, CLEAR, 2, CODE_FIELD, null, false);
        refusedRead(f.reviews());
    }

    @Test void codeOnNonClearingRecordsIsRefusedOnCreateAndRead() throws Exception {
        for (ReviewRecord.Outcome outcome : ReviewRecord.Outcome.values()) {
            var f = ReviewRecordStoreTest.fixture(temp.resolve(outcome.name()));
            ReviewRecord control = review(f, "review_1", T1, ReviewRecord.Kind.KEY_DEFECT, TIP,
                    "item_1", outcome, ReviewRecord.SignerRole.OWNER, A, null, null);
            ReviewRecord coded = review(f, "review_1", T1, ReviewRecord.Kind.KEY_DEFECT, TIP,
                    "item_1", outcome, ReviewRecord.SignerRole.OWNER, A, null,
                    ReviewRecord.ResolutionCode.FIXED);
            assertThatThrownBy(() -> f.reviews().create(coded)).isInstanceOf(ReviewRecordStore.InvalidStore.class);
            f.reviews().create(control);
            rewrite(f, "review_1", 1, CODE_FIELD, "FIXED", false);
            refusedRead(f.reviews());
        }
    }

    @Test void unknownCodeAndMissingFieldAreRefusedOnRead() throws Exception {
        for (String fault : List.of("unknown", "absent")) {
            var f = ReviewRecordStoreTest.fixture(temp.resolve(fault));
            f.reviews().create(concern(f));
            f.reviews().create(clearing(f, ReviewRecord.ResolutionCode.FIXED));
            rewrite(f, CLEAR, 2, CODE_FIELD, "UNKNOWN_CODE", fault.equals("absent"));
            refusedRead(f.reviews());
        }
    }

    @Test void equalEvidenceDigestStillCannotClear() throws Exception {
        var f = ReviewRecordStoreTest.fixture(temp.resolve("digest"));
        f.reviews().create(concern(f));
        ReviewRecord sameDigest = review(f, CLEAR, T2, ReviewRecord.Kind.KEY_DEFECT, TIP,
                "item_1", ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER,
                A, CONCERN, ReviewRecord.ResolutionCode.FIXED);
        assertThatThrownBy(() -> f.reviews().create(sameDigest)).isInstanceOf(ReviewRecordStore.InvalidStore.class);
    }

    @Test void forwardReferenceIsRefusedOnReadWithValidCode() throws Exception {
        semanticRefusal("forward");
    }
    @Test void selfSupersessionIsRefusedOnReadWithValidCode() throws Exception {
        semanticRefusal("self");
    }
    @Test void ownerConcernCannotSupersedeOnReadWithValidCode() throws Exception {
        semanticRefusal("owner_concern");
    }
    @Test void productUxConcernCannotSupersedeOnReadWithValidCode() throws Exception {
        semanticRefusal("ux_concern");
    }
    private void semanticRefusal(String fault) throws Exception {
            var f = ReviewRecordStoreTest.fixture(temp.resolve(fault));
            if (fault.equals("ux_concern"))
                f.reviews().create(review(f, CONCERN, T1, ReviewRecord.Kind.ABSOLUTE_QUALITY,
                        TIP, "quality_1", ReviewRecord.Outcome.BLOCKING_CONCERN,
                        ReviewRecord.SignerRole.OWNER, A, null, null));
            else f.reviews().create(concern(f));
            if (fault.equals("forward")) {
                f.reviews().create(review(f, CLEAR, T2, ReviewRecord.Kind.KEY_DEFECT, TIP,
                        "item_1", ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER,
                        B, null, null));
                f.reviews().create(review(f, "later_1", T1, ReviewRecord.Kind.KEY_DEFECT, TIP,
                        "item_1", ReviewRecord.Outcome.BLOCKING_CONCERN,
                        ReviewRecord.SignerRole.OWNER, A, null, null));
                rewrite(f, CLEAR, 2, SUPERSEDES_FIELD, "later_1", false);
                rewrite(f, CLEAR, 2, CODE_FIELD, "FIXED", false);
            } else if (fault.equals("self")) {
                f.reviews().create(review(f, CLEAR, T2, ReviewRecord.Kind.KEY_DEFECT, TIP,
                        "item_1", ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER,
                        B, null, null));
                rewrite(f, CLEAR, 2, SUPERSEDES_FIELD, CLEAR, false);
                rewrite(f, CLEAR, 2, CODE_FIELD, "FIXED", false);
            } else {
                ReviewRecord.SignerRole role = fault.equals("ux_concern")
                        ? ReviewRecord.SignerRole.PRODUCT_UX : ReviewRecord.SignerRole.OWNER;
                ReviewRecord.Kind kind = role == ReviewRecord.SignerRole.PRODUCT_UX
                        ? ReviewRecord.Kind.ABSOLUTE_QUALITY : ReviewRecord.Kind.KEY_DEFECT;
                String subject = role == ReviewRecord.SignerRole.PRODUCT_UX ? "quality_1" : "item_1";
                f.reviews().create(review(f, CLEAR, T2, kind, TIP, subject,
                        ReviewRecord.Outcome.BLOCKING_CONCERN, role, B, null, null));
                rewrite(f, CLEAR, 2, SUPERSEDES_FIELD, CONCERN, false);
                rewrite(f, CLEAR, 2, CODE_FIELD, "FIXED", false);
            }
            refusedRead(f.reviews());
    }

    private static void completeClassB(ReviewCheckTest.Fixture f, String workflow) throws Exception {
        f.reviews().create(new ReviewRecord("complete_" + workflow.toLowerCase(), T1, f.hash(),
                ReviewRecord.Kind.CLASS_B_WORKFLOW, workflow, workflow, ReviewRecord.Outcome.COMPLETE,
                "owner", ReviewRecord.SignerRole.OWNER, A));
    }
    private static void assertGates(FindingsLedger ledger, String route, boolean blocked) {
        assertThat(ledger.blocksBenchmarkPass(route)).isEqualTo(blocked);
        assertThat(ledger.blocksProductionSwitch(route)).isEqualTo(blocked);
    }
    private static void assertTokenAgrees(ReviewCheckTest.Fixture f, String route) {
        Set<String> ids = f.check(route).openConcernIds();
        assertThat(f.ledger().blocksBenchmarkPass(route)).isEqualTo(!ids.isEmpty());
        assertThat(f.ledger().blocksProductionSwitch(route)).isEqualTo(!ids.isEmpty());
    }

    @Test void unrequiredConcernBlocksOnlyItsRouteAndBothTokenAndGatesAgree() throws Exception {
        var f = ReviewCheckTest.Fixtures.classB(temp.resolve("routes"));
        for (String workflow : List.of(TIP, ASK, "INTERVIEW_CRITIQUE", "COLLECTION_COMPANION"))
            completeClassB(f, workflow);
        for (String route : List.of(INTERACTIVE, CRITIQUE, PAID)) {
            assertThat(f.ledger().missingRequiredReviews(route, Set.of())).isEmpty();
            assertGates(f.ledger(), route, false);
            assertTokenAgrees(f, route);
        }
        FindingsLedger legacy = new FindingsLedger(f.root().resolve("safety"),
                f.root().resolve("disputes"), f.context());
        f.reviews().create(new ReviewRecord(CONCERN, T1, f.hash(), ReviewRecord.Kind.KEY_DEFECT,
                TIP, "defect_1", ReviewRecord.Outcome.BLOCKING_CONCERN, "owner",
                ReviewRecord.SignerRole.OWNER, A));
        assertThat(f.ledger().missingRequiredReviews(INTERACTIVE, Set.of())).isEmpty();
        assertGates(f.ledger(), INTERACTIVE, true);
        assertThat(f.check(INTERACTIVE).openConcernIds()).containsExactly(CONCERN);
        assertTokenAgrees(f, INTERACTIVE);
        assertGates(f.ledger(), CRITIQUE, false);
        assertGates(f.ledger(), PAID, false);
        assertTokenAgrees(f, CRITIQUE);
        assertTokenAgrees(f, PAID);
        assertGates(legacy, INTERACTIVE, false);
        f.reviews().create(new ReviewRecord(CLEAR, T2, f.hash(), ReviewRecord.Kind.KEY_DEFECT,
                TIP, "defect_1", ReviewRecord.Outcome.COMPLETE, "owner",
                ReviewRecord.SignerRole.OWNER, B, CONCERN, ReviewRecord.ResolutionCode.FIXED));
        assertGates(f.ledger(), INTERACTIVE, false);
        assertThat(f.check(INTERACTIVE).openConcernIds()).isEmpty();
        assertTokenAgrees(f, INTERACTIVE);
        f.reviews().create(new ReviewRecord("concern_ask", T1, f.hash(), ReviewRecord.Kind.KEY_DEFECT,
                ASK, "defect_2", ReviewRecord.Outcome.BLOCKING_CONCERN, "owner",
                ReviewRecord.SignerRole.OWNER, A));
        assertGates(f.ledger(), INTERACTIVE, false);
        assertGates(f.ledger(), CRITIQUE, true);
        assertGates(f.ledger(), PAID, false);
        assertThat(f.check(CRITIQUE).openConcernIds()).containsExactly("concern_ask");
        for (String route : List.of(INTERACTIVE, CRITIQUE, PAID)) assertTokenAgrees(f, route);
        assertGates(legacy, CRITIQUE, false);
    }

    @Test void damagedReviewQueryThrowsNamedLedgerError() throws Exception {
        var f = ReviewCheckTest.Fixtures.classB(temp.resolve("damaged"));
        Files.writeString(f.root().resolve("reviews/index.jsonl"), "damaged\n");
        FindingsLedger ledger = f.ledger();
        assertThatThrownBy(() -> ledger.blocksBenchmarkPass(INTERACTIVE))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
        assertThatThrownBy(() -> ledger.blocksProductionSwitch(INTERACTIVE))
                .isInstanceOf(FindingsLedger.InvalidLedger.class);
    }
}
