package com.studysnap.backend.benchmark;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Consumer rule: any InvalidLedger/integrity failure THROWS and means BLOCKED. "Open" only
 * describes an intact indexed record with absent or semantically invalid resolution. A later
 * finding or dispute does not invalidate an earlier review; an open one blocks independently.
 * Review queries do not check an external head, so a removed suffix can reveal an older COMPLETE.
 * Callers must verifyReviewPrefix against the externally noted head before an attended save.
 */
final class FindingsLedger {
    static final class InvalidLedger extends RuntimeException {
        InvalidLedger(String message, Throwable cause) { super(message, cause); }
    }
    private final SafetyFindingStore safety;
    private final EvidenceDisputeStore disputes;
    private final ReviewRecordStore reviews;
    private final PostLockContext context;
    record ReviewSubject(ReviewRecord.Kind kind, String workflow, String subjectId) {}
    FindingsLedger(Path safetyDir, Path disputeDir, PostLockContext context) {
        try {
            PostLockContext.separate(safetyDir, disputeDir);
            this.context = context;
            safety = new SafetyFindingStore(safetyDir, context);
            disputes = new EvidenceDisputeStore(disputeDir, context);
            reviews = null;
            safety.headSha256(); disputes.headSha256();
        } catch (Exception exception) { throw new InvalidLedger("Cannot initialize findings ledger", exception); }
    }
    FindingsLedger(Path safetyDir, Path disputeDir, Path reviewDir, PostLockContext context) {
        try {
            PostLockContext.separate(safetyDir, disputeDir);
            PostLockContext.separate(safetyDir, reviewDir);
            PostLockContext.separate(disputeDir, reviewDir);
            this.context = context;
            safety = new SafetyFindingStore(safetyDir, context);
            disputes = new EvidenceDisputeStore(disputeDir, context);
            reviews = new ReviewRecordStore(reviewDir, context);
            safety.headSha256(); disputes.headSha256(); reviews.headSha256();
        } catch (Exception exception) { throw new InvalidLedger("Cannot initialize findings ledger", exception); }
    }
    List<SafetyFinding> openSafetyFindings(String route) {
        try { validate(route); return safety.open(route); }
        catch (Exception exception) { throw new InvalidLedger("Cannot query safety findings", exception); }
    }
    List<EvidenceDispute> openDisputes(String route) {
        try { validate(route); return disputes.open(route); }
        catch (Exception exception) { throw new InvalidLedger("Cannot query evidence disputes", exception); }
    }
    boolean blocksProductionSwitch(String route) {
        boolean findingOpen = !openSafetyFindings(route).isEmpty();
        boolean disputeOpen = !openDisputes(route).isEmpty();
        boolean reviewMissing = reviews != null && !missingRequiredReviews(route, Set.of()).isEmpty();
        return findingOpen || disputeOpen || reviewMissing;
    }
    boolean blocksBenchmarkPass(String route) {
        boolean findingOpen = !openSafetyFindings(route).isEmpty();
        boolean disputeOpen = !openDisputes(route).isEmpty();
        boolean reviewMissing = reviews != null && !missingRequiredReviews(route, Set.of()).isEmpty();
        return findingOpen || disputeOpen || reviewMissing;
    }
    /** Null means no review store was supplied, never that reviews are satisfied. */
    Set<String> missingRequiredReviews(String route, Set<ReviewSubject> extraRequired) {
        try {
            if (reviews == null) return null;
            PostLockContext.Verified verified = context.verify();
            Set<ReviewSubject> required = new java.util.HashSet<>();
            for (String workflow : context.requiredClassBWorkflows(verified, route))
                required.add(new ReviewSubject(ReviewRecord.Kind.CLASS_B_WORKFLOW, workflow, workflow));
            if (extraRequired == null) throw new PostLockStoreIndex.InvalidStore("Required reviews absent");
            for (ReviewSubject extra : extraRequired) {
                if (extra == null || extra.kind() == null || extra.kind() == ReviewRecord.Kind.CLASS_B_WORKFLOW
                        || !route.equals(context.route(verified, extra.workflow())))
                    throw new PostLockStoreIndex.InvalidStore("Invalid extra review subject");
                PostLockContext.id(extra.subjectId());
                required.add(extra);
            }
            Map<ReviewSubject, ReviewRecord> latest = new java.util.HashMap<>();
            for (ReviewRecord review : reviews.records())
                latest.put(new ReviewSubject(review.kind(), review.workflow(), review.subjectId()), review);
            Set<String> missing = new TreeSet<>();
            for (ReviewSubject subject : required) {
                ReviewRecord record = latest.get(subject);
                if (record == null || record.outcome() != ReviewRecord.Outcome.COMPLETE)
                    missing.add(subject.kind() + ":" + subject.workflow() + ":" + subject.subjectId());
            }
            return java.util.Collections.unmodifiableSet(missing);
        } catch (Exception exception) { throw new InvalidLedger("Cannot query required reviews", exception); }
    }
    Map<String, String> headHashes() {
        try {
            Map<String, String> heads = new TreeMap<>();
            heads.put("safety", format(safety.headSha256()));
            heads.put("disputes", format(disputes.headSha256()));
            if (reviews != null) heads.put("reviews", format(reviews.headSha256()));
            return Map.copyOf(heads);
        } catch (Exception exception) { throw new InvalidLedger("Cannot query ledger heads", exception); }
    }
    void verifyReviewPrefix(int seq, String sha) {
        try {
            if (reviews == null) throw new PostLockStoreIndex.InvalidStore("Review store unavailable");
            reviews.verifyPrefix(seq, sha);
        } catch (Exception exception) { throw new InvalidLedger("Cannot verify review prefix", exception); }
    }
    private static String format(PostLockStoreIndex.Head head) { return head.seq() + ":" + head.sha256(); }
    String checklistLine(String route) {
        try {
            List<String> ids = new ArrayList<>();
            openSafetyFindings(route).forEach(f -> ids.add(f.findingId()));
            openDisputes(route).forEach(d -> ids.add(d.disputeId()));
            ids.sort(String::compareTo);
            PostLockStoreIndex.Head a = safety.headSha256(), b = disputes.headSha256();
            return (ids.isEmpty() ? "no open safety finding" : "open safety findings or disputes: " + String.join(",", ids))
                    + "\nsafety store head: " + a.seq() + ":" + a.sha256()
                    + "\ndispute store head: " + b.seq() + ":" + b.sha256()
                    + (reviews == null ? "" : "\nreview store head: " + format(reviews.headSha256()));
        } catch (Exception exception) { throw new InvalidLedger("Cannot build checklist line", exception); }
    }
    private void validate(String route) { context.routeName(context.verify(), route); }
}
