package com.studysnap.backend.benchmark;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        InvalidLedger(String message) { super(message); }
        InvalidLedger(String message, Throwable cause) { super(message, cause); }
    }
    record ExternalHead(int seq, String sha256) {
        ExternalHead {
            if (seq < 0 || !IntegritySupport.digest(sha256))
                throw new InvalidLedger("Invalid externally noted head");
        }
    }
    record ExternalHeads(ExternalHead safety, ExternalHead disputes, ExternalHead reviews) {
        ExternalHeads {
            if (safety == null || disputes == null || reviews == null)
                throw new InvalidLedger("All externally noted heads are required");
        }
    }
    /**
     * Evidence from this ledger's one verified lock only. A clean token cannot establish that
     * another lock's ledger has no route-affecting record. Reserve exports have separate seals,
     * locks and ledgers. Mixed routes need more than one lock; only CRITIQUE can be fully covered
     * here. Every append to any of the three stores requires a new external note. That note is
     * only as external as the operator's recording: a caller can pass headHashes() straight back.
     * Reflection can bypass this same-JVM constructor boundary.
     */
    static final class ReviewCheck {
        private final String route;
        private final Set<String> coveredWorkflows;
        private final Set<String> missing;
        private final Set<ReviewSubject> extras;
        private final Set<String> openSafetyFindingIds;
        private final Set<String> openDisputeIds;
        private final Set<String> openConcernIds;
        private final Map<String, String> heads;
        private final String lockSha256;
        private final String parametersSha256;

        private ReviewCheck(String route, Set<String> coveredWorkflows, Set<String> missing,
                            Set<ReviewSubject> extras, Set<String> openSafetyFindingIds,
                            Set<String> openDisputeIds, Set<String> openConcernIds,
                            Map<String, String> heads, String lockSha256, String parametersSha256) {
            this.route = route;
            this.coveredWorkflows = immutableSorted(coveredWorkflows);
            this.missing = immutableSorted(missing);
            TreeSet<ReviewSubject> sortedExtras = new TreeSet<>(Comparator
                    .comparing((ReviewSubject subject) -> subject.kind().name())
                    .thenComparing(ReviewSubject::workflow).thenComparing(ReviewSubject::subjectId));
            sortedExtras.addAll(extras);
            this.extras = Collections.unmodifiableSortedSet(sortedExtras);
            this.openSafetyFindingIds = immutableSorted(openSafetyFindingIds);
            this.openDisputeIds = immutableSorted(openDisputeIds);
            this.openConcernIds = immutableSorted(openConcernIds);
            this.heads = Map.copyOf(heads);
            this.lockSha256 = lockSha256;
            this.parametersSha256 = parametersSha256;
        }
        private static Set<String> immutableSorted(Set<String> values) {
            return Collections.unmodifiableSortedSet(new TreeSet<>(values));
        }
        String route() { return route; }
        Set<String> coveredWorkflows() { return coveredWorkflows; }
        Set<String> missing() { return missing; }
        Set<ReviewSubject> extras() { return extras; }
        Set<String> openSafetyFindingIds() { return openSafetyFindingIds; }
        Set<String> openDisputeIds() { return openDisputeIds; }
        Set<String> openConcernIds() { return openConcernIds; }
        Map<String, String> heads() { return heads; }
        String lockSha256() { return lockSha256; }
        String parametersSha256() { return parametersSha256; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof ReviewCheck that)) return false;
            return Objects.equals(route, that.route)
                    && coveredWorkflows.equals(that.coveredWorkflows) && missing.equals(that.missing)
                    && extras.equals(that.extras) && openSafetyFindingIds.equals(that.openSafetyFindingIds)
                    && openDisputeIds.equals(that.openDisputeIds) && openConcernIds.equals(that.openConcernIds)
                    && heads.equals(that.heads) && Objects.equals(lockSha256, that.lockSha256)
                    && Objects.equals(parametersSha256, that.parametersSha256);
        }
        @Override public int hashCode() {
            return Objects.hash(route, coveredWorkflows, missing, extras, openSafetyFindingIds,
                    openDisputeIds, openConcernIds, heads, lockSha256, parametersSha256);
        }
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
        boolean concernOpen = reviews != null && !openConcernIds(context.verify(), route).isEmpty();
        return findingOpen || disputeOpen || reviewMissing || concernOpen;
    }
    boolean blocksBenchmarkPass(String route) {
        boolean findingOpen = !openSafetyFindings(route).isEmpty();
        boolean disputeOpen = !openDisputes(route).isEmpty();
        boolean reviewMissing = reviews != null && !missingRequiredReviews(route, Set.of()).isEmpty();
        boolean concernOpen = reviews != null && !openConcernIds(context.verify(), route).isEmpty();
        return findingOpen || disputeOpen || reviewMissing || concernOpen;
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
            Set<ReviewSubject> completed = new java.util.HashSet<>();
            Set<String> cleared = new java.util.HashSet<>();
            List<ReviewRecord> records = reviews.records();
            for (ReviewRecord review : records) {
                if (review.outcome() == ReviewRecord.Outcome.COMPLETE)
                    completed.add(new ReviewSubject(review.kind(), review.workflow(), review.subjectId()));
                if (review.supersedes() != null) cleared.add(review.supersedes());
            }
            Set<ReviewSubject> open = new java.util.HashSet<>();
            for (ReviewRecord review : records)
                if (review.outcome() == ReviewRecord.Outcome.BLOCKING_CONCERN
                        && !cleared.contains(review.reviewId()))
                    open.add(new ReviewSubject(review.kind(), review.workflow(), review.subjectId()));
            Set<String> missing = new TreeSet<>();
            for (ReviewSubject subject : required) {
                if (!completed.contains(subject) || open.contains(subject))
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
    ReviewCheck checkLedger(String route, Set<ReviewSubject> extras, ExternalHeads noted) {
        try {
            if (reviews == null || noted == null)
                throw new InvalidLedger("Review store and external notes are required");
            PostLockContext.Verified verified = context.verify();
            context.routeName(verified, route);
            PostLockStoreIndex.Head safetyHead = checkedHead(safety.headSha256(), noted.safety());
            safety.verifyPrefix(noted.safety().seq(), noted.safety().sha256());
            PostLockStoreIndex.Head disputeHead = checkedHead(disputes.headSha256(), noted.disputes());
            disputes.verifyPrefix(noted.disputes().seq(), noted.disputes().sha256());
            PostLockStoreIndex.Head reviewHead = checkedHead(reviews.headSha256(), noted.reviews());
            reviews.verifyPrefix(noted.reviews().seq(), noted.reviews().sha256());
            Set<String> coverage = new TreeSet<>(verified.lock().workflows());
            coverage.retainAll(verified.parameters().routes.get(route).workflows());
            Set<String> findings = new TreeSet<>();
            openSafetyFindings(route).forEach(value -> findings.add(value.findingId()));
            Set<String> openDisputes = new TreeSet<>();
            openDisputes(route).forEach(value -> openDisputes.add(value.disputeId()));
            Set<String> concerns = openConcernIds(verified, route);
            Set<String> missing = missingRequiredReviews(route, extras);
            if (!safetyHead.equals(safety.headSha256()) || !disputeHead.equals(disputes.headSha256())
                    || !reviewHead.equals(reviews.headSha256()))
                throw new InvalidLedger("Ledger head changed during review check");
            return new ReviewCheck(route, coverage, missing, extras, findings, openDisputes, concerns,
                    Map.of("safety", format(safetyHead), "disputes", format(disputeHead),
                            "reviews", format(reviewHead)), verified.lockSha256(), verified.parameters().sha256);
        } catch (InvalidLedger exception) { throw exception; }
        catch (Exception exception) { throw new InvalidLedger("Cannot check ledger", exception); }
    }
    private Set<String> openConcernIds(PostLockContext.Verified verified, String route) {
        try {
            Set<String> concerns = new TreeSet<>();
            Set<String> openIds = Set.copyOf(reviews.openConcerns());
            for (ReviewRecord review : reviews.records())
                if (openIds.contains(review.reviewId()) && route.equals(context.route(verified, review.workflow())))
                    concerns.add(review.reviewId());
            return concerns;
        } catch (Exception exception) { throw new InvalidLedger("Cannot query open review concerns", exception); }
    }
    private static PostLockStoreIndex.Head checkedHead(PostLockStoreIndex.Head current, ExternalHead noted) {
        if (noted == null || current.seq() != noted.seq() || !current.sha256().equals(noted.sha256()))
            throw new InvalidLedger("Externally noted head is not current");
        return current;
    }
    /** Binds a trusted runFrb report to this ledger's verified frozen route declaration. */
    void requireReport(String route, RouteVerdictCalculator.RouteReport report) {
        try {
            PostLockContext.Verified verified = context.verify();
            context.routeName(verified, route);
            if (report == null || report.workflows() == null
                    || !report.workflows().keySet().equals(Set.copyOf(verified.parameters().routes.get(route).workflows()))
                    || !verified.parameters().sha256.equals(report.parameterSha256()))
                throw new InvalidLedger("Report does not match frozen route parameters");
        } catch (InvalidLedger exception) { throw exception; }
        catch (Exception exception) { throw new InvalidLedger("Cannot bind report", exception); }
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
