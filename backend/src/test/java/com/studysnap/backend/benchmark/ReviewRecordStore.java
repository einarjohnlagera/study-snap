package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Append-only review claims bound to one verified lock. Every read validates all record semantics.
 * A removed suffix and its files can hide a blocking concern; callers must verify an
 * externally noted prefix before relying on the chain. Integrity failure means BLOCKED.
 */
final class ReviewRecordStore {
    static final class InvalidStore extends RuntimeException {
        InvalidStore(String message, Throwable cause) { super(message, cause); }
    }
    private static final Set<String> FIELDS = Set.of("reviewId", "recordedAtUtc", "lockSha256",
            "kind", "workflow", "subjectId", "outcome", "signer", "signerRole", "evidenceSha256", "supersedes");
    private final PostLockRecordStore core;
    ReviewRecordStore(Path dir, PostLockContext context) { core = new PostLockRecordStore(dir, context); }
    static ReviewRecordStore init(Path dir, PostLockContext context, String utc) {
        try { PostLockRecordStore.init(dir, context, utc); return new ReviewRecordStore(dir, context); }
        catch (Exception exception) { throw new InvalidStore("Cannot initialize review store", exception); }
    }
    ReviewRecord create(ReviewRecord review) {
        try {
            List<ReviewRecord> earlier = records();
            validate(review, core.verify(), earlier);
            core.create(review.reviewId(), PostLockStoreIndex.Kind.RECORD, review, review.recordedAtUtc());
            return review;
        } catch (Exception exception) { throw new InvalidStore("Cannot create review", exception); }
    }
    ReviewRecord load(String id) {
        try {
            for (ReviewRecord review : records()) if (id.equals(review.reviewId())) return review;
            throw new PostLockStoreIndex.InvalidStore("Unindexed review");
        } catch (Exception exception) { throw new InvalidStore("Cannot load review", exception); }
    }
    private ReviewRecord loadOne(String id) {
        try {
            JsonNode node = core.read(id, PostLockStoreIndex.Kind.RECORD, FIELDS);
            ReviewRecord review = IntegritySupport.JSON.treeToValue(node, ReviewRecord.class);
            if (!id.equals(review.reviewId()))
                throw new PostLockStoreIndex.InvalidStore("Review stem differs");
            return review;
        } catch (Exception exception) { throw new InvalidStore("Cannot load review", exception); }
    }
    List<ReviewRecord> records() {
        try {
            List<ReviewRecord> result = new ArrayList<>();
            for (PostLockStoreIndex.Entry entry : core.entries()) {
                if (entry.kind() == PostLockStoreIndex.Kind.GENESIS) continue;
                if (entry.kind() != PostLockStoreIndex.Kind.RECORD)
                    throw new PostLockStoreIndex.InvalidStore("Review store contains non-record entry");
                ReviewRecord review = loadOne(entry.id());
                validate(review, core.verify(), result);
                result.add(review);
            }
            return List.copyOf(result);
        } catch (Exception exception) { throw new InvalidStore("Cannot query reviews", exception); }
    }
    List<String> openConcerns() {
        List<ReviewRecord> all = records();
        Set<String> cleared = new HashSet<>();
        for (ReviewRecord review : all) if (review.supersedes() != null) cleared.add(review.supersedes());
        return all.stream().filter(review -> review.outcome() == ReviewRecord.Outcome.BLOCKING_CONCERN
                && !cleared.contains(review.reviewId())).map(ReviewRecord::reviewId).sorted().toList();
    }
    PostLockStoreIndex.Head headSha256() {
        try { records(); return core.headSha256(); }
        catch (Exception exception) { throw new InvalidStore("Cannot read review head", exception); }
    }
    void verifyPrefix(int seq, String sha) {
        try { records(); core.verifyPrefix(seq, sha); }
        catch (Exception exception) { throw new InvalidStore("Cannot verify review prefix", exception); }
    }
    private void validate(ReviewRecord review, PostLockContext.Verified verified,
                          List<ReviewRecord> earlier) {
        if (review == null) throw new PostLockStoreIndex.InvalidStore("Review required");
        PostLockContext.id(review.reviewId());
        PostLockContext.id(review.signer());
        if (!verified.lockSha256().equals(review.lockSha256())
                || !IntegritySupport.digest(review.evidenceSha256())
                || review.kind() == null || review.outcome() == null || review.signerRole() == null)
            throw new PostLockStoreIndex.InvalidStore("Invalid review fields or lock link");
        if (!PostLockContext.utc(review.recordedAtUtc())
                .isAfter(PostLockContext.utc(verified.lock().lockedAtUtc())))
            throw new PostLockStoreIndex.InvalidStore("Review must follow lock");
        core.context().route(verified, review.workflow());
        if (review.kind() == ReviewRecord.Kind.CLASS_B_WORKFLOW) {
            if (!review.workflow().equals(review.subjectId())
                    || !verified.parameters().frbClassBWorkflows.contains(review.workflow()))
                throw new PostLockStoreIndex.InvalidStore("Invalid class-B subject");
        } else PostLockContext.id(review.subjectId());
        // OWNER may record either outcome (a concern retracts an earlier completion); only an OWNER
        // COMPLETE satisfies a requirement. PRODUCT_UX may record only a concern, only for ABSOLUTE_QUALITY.
        if (review.signerRole() == ReviewRecord.SignerRole.PRODUCT_UX
                && (review.kind() != ReviewRecord.Kind.ABSOLUTE_QUALITY
                || review.outcome() != ReviewRecord.Outcome.BLOCKING_CONCERN))
            throw new PostLockStoreIndex.InvalidStore("Product UX review role invalid");
        if (review.supersedes() != null) {
            PostLockContext.id(review.supersedes());
            if (review.signerRole() != ReviewRecord.SignerRole.OWNER
                    || review.outcome() != ReviewRecord.Outcome.COMPLETE)
                throw new PostLockStoreIndex.InvalidStore("Only owner completion can supersede");
            ReviewRecord concern = earlier.stream().filter(r -> r.reviewId().equals(review.supersedes()))
                    .findFirst().orElseThrow(() -> new PostLockStoreIndex.InvalidStore("Unknown earlier concern"));
            if (!PostLockContext.utc(concern.recordedAtUtc()).isBefore(PostLockContext.utc(review.recordedAtUtc()))
                    || concern.outcome() != ReviewRecord.Outcome.BLOCKING_CONCERN
                    || concern.kind() != review.kind() || !concern.workflow().equals(review.workflow())
                    || !concern.subjectId().equals(review.subjectId())
                    || concern.evidenceSha256().equals(review.evidenceSha256())
                    || earlier.stream().anyMatch(r -> review.supersedes().equals(r.supersedes())))
                throw new PostLockStoreIndex.InvalidStore("Invalid supersession");
        }
    }
}
