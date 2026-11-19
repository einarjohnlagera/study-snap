package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewRecordStoreTest {
    static final String PARAMS = "src/test/java/com/studysnap/backend/benchmark/frozen-route-gates-frb.json";
    static final String CREATED = "2026-11-16T00:04:00Z";
    static final String WORKFLOW = "STUDY_TIP";
    static final String DIGEST = "a".repeat(64);
    private static final String REVIEW_ONE = "review_1";
    private static final String REVIEW_TWO = "review_2";
    private static final String CASE_DIRECTORY = "case";
    @TempDir Path temp;
    record Fixture(Path root, Path lock, PostLockContext context, SafetyFindingStore safety,
                   ReviewRecordStore reviews) {
        ReviewRecord review(String id, ReviewRecord.Kind kind, String workflow, String subject,
                            ReviewRecord.Outcome outcome, ReviewRecord.SignerRole role) throws Exception {
            return new ReviewRecord(id, CREATED, IntegritySupport.detachedHash(lock), kind, workflow,
                    subject, outcome, "owner", role, DIGEST);
        }
        FindingsLedger ledger() {
            return new FindingsLedger(root.resolve("safety"), root.resolve("disputes"),
                    root.resolve("reviews"), context);
        }
    }
    static Fixture fixture(Path root) throws Exception {
        return fixture(root, true);
    }
    static Fixture fixture(Path root, boolean frbEnabled) throws Exception {
        Path locked = Files.createDirectories(root.resolve("locked"));
        var export = ArmKeyGateFixtures.classA(locked);
        Path seal = ArmKeyGateFixtures.seal(locked, export.armKey());
        Path finding = ArmKeyGateFixtures.findings(locked);
        Path lock = ArmKeyGateFixtures.lock(locked, seal, finding, LockRecord.Kind.PRIMARY);
        Path parameters = root.resolve("parameters.json");
        Files.copy(Path.of(PARAMS), parameters);
        Files.copy(Path.of(PARAMS + ".sha256"), IntegritySupport.detached(parameters));
        if (!frbEnabled) {
            ObjectNode node = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(parameters));
            node.remove("frb");
            Files.write(parameters, IntegritySupport.JSON.writeValueAsBytes(node));
            Files.writeString(IntegritySupport.detached(parameters), FreezeRecord.sha256(parameters) + "\n");
        }
        var context = new PostLockContext(lock, finding.getParent(), locked.resolve("packets"), seal, parameters);
        var safety = SafetyFindingStore.init(root.resolve("safety"), context, "2026-11-16T00:03:00Z");
        EvidenceDisputeStore.init(root.resolve("disputes"), context, "2026-11-16T00:03:00Z");
        var reviews = ReviewRecordStore.init(root.resolve("reviews"), context, "2026-11-16T00:03:00Z");
        return new Fixture(root, lock, context, safety, reviews);
    }
    private static ReviewRecord copy(ReviewRecord r, String id, String time, String hash,
                                     ReviewRecord.Kind kind, String workflow, String subject,
                                     ReviewRecord.Outcome outcome, String signer,
                                     ReviewRecord.SignerRole role, String digest) {
        return new ReviewRecord(id, time, hash, kind, workflow, subject, outcome, signer, role, digest);
    }
    @Test void roundTripAndDuplicateId() throws Exception {
        Fixture f = fixture(temp.resolve(CASE_DIRECTORY));
        ReviewRecordStore store = f.reviews();
        ReviewRecord good = f.review(REVIEW_ONE, ReviewRecord.Kind.CLASS_B_WORKFLOW, WORKFLOW,
                WORKFLOW, ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER);
        assertThat(store.create(good)).isEqualTo(good);
        assertThat(store.load(REVIEW_ONE)).isEqualTo(good);
        assertThat(store.records()).containsExactly(good);
        assertThatThrownBy(() -> store.create(good)).isInstanceOf(ReviewRecordStore.InvalidStore.class);
    }
    @Test void createRejectsIdentityTimeAndDigestFailures() throws Exception {
        Fixture f = fixture(temp.resolve(CASE_DIRECTORY));
        ReviewRecordStore store = f.reviews();
        ReviewRecord good = f.review(REVIEW_ONE, ReviewRecord.Kind.CLASS_B_WORKFLOW, WORKFLOW,
                WORKFLOW, ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER);
        for (ReviewRecord bad : List.of(
                copy(good, "UPPER", good.recordedAtUtc(), good.lockSha256(), good.kind(), good.workflow(), good.subjectId(), good.outcome(), good.signer(), good.signerRole(), good.evidenceSha256()),
                copy(good, "candidate_1", good.recordedAtUtc(), good.lockSha256(), good.kind(), good.workflow(), good.subjectId(), good.outcome(), good.signer(), good.signerRole(), good.evidenceSha256()),
                copy(good, good.reviewId(), ArmKeyGateFixtures.LOCKED, good.lockSha256(), good.kind(), good.workflow(), good.subjectId(), good.outcome(), good.signer(), good.signerRole(), good.evidenceSha256()),
                copy(good, good.reviewId(), "2026-11-16T00:01:00Z", good.lockSha256(), good.kind(), good.workflow(), good.subjectId(), good.outcome(), good.signer(), good.signerRole(), good.evidenceSha256()),
                copy(good, good.reviewId(), good.recordedAtUtc(), "0".repeat(64), good.kind(), good.workflow(), good.subjectId(), good.outcome(), good.signer(), good.signerRole(), good.evidenceSha256()),
                copy(good, good.reviewId(), good.recordedAtUtc(), good.lockSha256(), good.kind(), good.workflow(), good.subjectId(), good.outcome(), "ARM", good.signerRole(), good.evidenceSha256()),
                copy(good, good.reviewId(), good.recordedAtUtc(), good.lockSha256(), good.kind(), good.workflow(), good.subjectId(), good.outcome(), "candidate", good.signerRole(), good.evidenceSha256()),
                copy(good, good.reviewId(), good.recordedAtUtc(), good.lockSha256(), good.kind(), good.workflow(), good.subjectId(), good.outcome(), good.signer(), good.signerRole(), "bad")))
            assertThatThrownBy(() -> store.create(bad)).isInstanceOf(ReviewRecordStore.InvalidStore.class);
    }
    @Test void createRejectsSubjectAndRoleFailures() throws Exception {
        Fixture f = fixture(temp.resolve(CASE_DIRECTORY));
        ReviewRecordStore store = f.reviews();
        ReviewRecord good = f.review(REVIEW_ONE, ReviewRecord.Kind.CLASS_B_WORKFLOW, WORKFLOW,
                WORKFLOW, ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER);
        for (ReviewRecord bad : List.of(
                copy(good, good.reviewId(), CREATED, good.lockSha256(), good.kind(), "UNDECLARED", WORKFLOW, good.outcome(), good.signer(), good.signerRole(), DIGEST),
                copy(good, good.reviewId(), CREATED, good.lockSha256(), good.kind(), WORKFLOW, "other", good.outcome(), good.signer(), good.signerRole(), DIGEST),
                copy(good, good.reviewId(), CREATED, good.lockSha256(), good.kind(), "NOTE_ADAPTIVE", "NOTE_ADAPTIVE", good.outcome(), good.signer(), good.signerRole(), DIGEST),
                copy(good, good.reviewId(), CREATED, good.lockSha256(), ReviewRecord.Kind.KEY_DEFECT, WORKFLOW, "UPPER", good.outcome(), good.signer(), good.signerRole(), DIGEST),
                copy(good, good.reviewId(), CREATED, good.lockSha256(), ReviewRecord.Kind.ABSOLUTE_QUALITY, WORKFLOW, "candidate_1", good.outcome(), good.signer(), good.signerRole(), DIGEST),
                copy(good, good.reviewId(), CREATED, good.lockSha256(), good.kind(), WORKFLOW, WORKFLOW, good.outcome(), good.signer(), ReviewRecord.SignerRole.PRODUCT_UX, DIGEST),
                copy(good, good.reviewId(), CREATED, good.lockSha256(), ReviewRecord.Kind.ABSOLUTE_QUALITY, WORKFLOW, "quality_1", good.outcome(), good.signer(), ReviewRecord.SignerRole.PRODUCT_UX, DIGEST)))
            assertThatThrownBy(() -> store.create(bad)).isInstanceOf(ReviewRecordStore.InvalidStore.class);
        ReviewRecord concern = copy(good, "concern", CREATED, good.lockSha256(), ReviewRecord.Kind.ABSOLUTE_QUALITY,
                WORKFLOW, "quality_1", ReviewRecord.Outcome.BLOCKING_CONCERN, "ux", ReviewRecord.SignerRole.PRODUCT_UX, DIGEST);
        assertThat(store.create(concern)).isEqualTo(concern);
        ReviewRecord ownerConcern = copy(good, "owner_concern", CREATED, good.lockSha256(), good.kind(), WORKFLOW,
                WORKFLOW, ReviewRecord.Outcome.BLOCKING_CONCERN, good.signer(), ReviewRecord.SignerRole.OWNER, DIGEST);
        assertThat(store.create(ownerConcern)).isEqualTo(ownerConcern);
    }
    private static void rewrite(Fixture f, String field, String value) throws Exception {
        Path file = f.root().resolve("reviews/review_1.json");
        ObjectNode node = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(file));
        node.put(field, value);
        Files.write(file, IntegritySupport.JSON.writeValueAsBytes(node));
        Files.writeString(IntegritySupport.detached(file), FreezeRecord.sha256(file) + "\n");
        Path index = f.root().resolve("reviews/index.jsonl");
        String[] lines = Files.readString(index).split("\n");
        ObjectNode entry = (ObjectNode) IntegritySupport.JSON.readTree(lines[1]);
        entry.put("fileSha256", FreezeRecord.sha256(file));
        lines[1] = IntegritySupport.JSON.writeValueAsString(entry);
        for (int i = 2; i < lines.length; i++) {
            ObjectNode successor = (ObjectNode) IntegritySupport.JSON.readTree(lines[i]);
            successor.put("prevSha256", FreezeRecord.sha256(lines[i - 1].getBytes(StandardCharsets.UTF_8)));
            lines[i] = IntegritySupport.JSON.writeValueAsString(successor);
        }
        Files.writeString(index, String.join("\n", lines) + "\n");
    }
    @Test void readsRevalidateRoleSubjectAndTime() throws Exception {
        for (String field : List.of("signerRole", "subjectId", "recordedAtUtc")) {
            Fixture f = fixture(temp.resolve(field));
            ReviewRecordStore store = f.reviews();
            store.create(f.review(REVIEW_ONE, ReviewRecord.Kind.CLASS_B_WORKFLOW, WORKFLOW,
                    WORKFLOW, ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER));
            store.create(f.review(REVIEW_TWO, ReviewRecord.Kind.CLASS_B_WORKFLOW, WORKFLOW,
                    WORKFLOW, ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER));
            String value = switch (field) {
                case "signerRole" -> "PRODUCT_UX";
                case "subjectId" -> "wrong";
                default -> ArmKeyGateFixtures.LOCKED;
            };
            rewrite(f, field, value);
            assertThatThrownBy(() -> store.load(REVIEW_ONE)).isInstanceOf(ReviewRecordStore.InvalidStore.class);
            assertThatThrownBy(() -> store.load(REVIEW_TWO)).isInstanceOf(ReviewRecordStore.InvalidStore.class);
            assertThatThrownBy(store::records).isInstanceOf(ReviewRecordStore.InvalidStore.class);
            assertThatThrownBy(store::headSha256).isInstanceOf(ReviewRecordStore.InvalidStore.class);
        }
    }
    @Test void strictFieldsAndBrokenFilesFailEveryReadAndAppend() throws Exception {
        for (String damage : List.of("unknown", "missing", "flipped", "removed", "orphan", "hashless")) {
            Fixture f = fixture(temp.resolve(damage));
            ReviewRecordStore store = f.reviews();
            ReviewRecord good = f.review(REVIEW_ONE, ReviewRecord.Kind.CLASS_B_WORKFLOW, WORKFLOW,
                    WORKFLOW, ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER);
            store.create(good);
            Path file = f.root().resolve("reviews/review_1.json");
            switch (damage) {
                case "unknown", "missing" -> {
                    ObjectNode node = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(file));
                    if (damage.equals("unknown")) node.put("extra", "value");
                    else node.remove("signer");
                    Files.write(file, IntegritySupport.JSON.writeValueAsBytes(node));
                    Files.writeString(IntegritySupport.detached(file), FreezeRecord.sha256(file) + "\n");
                    Path index = f.root().resolve("reviews/index.jsonl");
                    String[] lines = Files.readString(index).split("\n");
                    ObjectNode entry = (ObjectNode) IntegritySupport.JSON.readTree(lines[1]);
                    entry.put("fileSha256", FreezeRecord.sha256(file));
                    Files.writeString(index, lines[0] + "\n" + entry + "\n");
                }
                case "flipped" -> Files.writeString(file, "flipped");
                case "removed" -> Files.delete(file);
                case "orphan" -> Files.writeString(f.root().resolve("reviews/orphan.json"), "{}");
                default -> Files.delete(IntegritySupport.detached(file));
            }
            assertThatThrownBy(() -> store.load(REVIEW_ONE)).isInstanceOf(ReviewRecordStore.InvalidStore.class);
            assertThatThrownBy(store::records).isInstanceOf(ReviewRecordStore.InvalidStore.class);
            assertThatThrownBy(store::headSha256).isInstanceOf(ReviewRecordStore.InvalidStore.class);
            assertThatThrownBy(() -> store.verifyPrefix(1, DIGEST)).isInstanceOf(ReviewRecordStore.InvalidStore.class);
            ReviewRecord next = f.review(REVIEW_TWO, ReviewRecord.Kind.CLASS_B_WORKFLOW, WORKFLOW,
                    WORKFLOW, ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER);
            assertThatThrownBy(() -> store.create(next)).isInstanceOf(ReviewRecordStore.InvalidStore.class);
        }
    }
    @Test void notedHeadDetectsTruncatedSuffix() throws Exception {
        Fixture f = fixture(temp.resolve(CASE_DIRECTORY));
        ReviewRecordStore store = f.reviews();
        store.create(f.review(REVIEW_ONE, ReviewRecord.Kind.CLASS_B_WORKFLOW, WORKFLOW,
                WORKFLOW, ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER));
        store.create(f.review(REVIEW_TWO, ReviewRecord.Kind.CLASS_B_WORKFLOW, WORKFLOW,
                WORKFLOW, ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER));
        var noted = store.headSha256();
        int seq = noted.seq();
        String sha = noted.sha256();
        store.verifyPrefix(seq, sha);
        Path index = f.root().resolve("reviews/index.jsonl");
        String[] lines = Files.readString(index).split("\n");
        Files.writeString(index, lines[0] + "\n" + lines[1] + "\n");
        Path tail = f.root().resolve("reviews/review_2.json");
        Files.delete(tail);
        Files.delete(IntegritySupport.detached(tail));
        assertThat(store.records()).hasSize(1);
        assertThatThrownBy(() -> store.verifyPrefix(seq, sha))
                .isInstanceOf(ReviewRecordStore.InvalidStore.class);
    }
}
