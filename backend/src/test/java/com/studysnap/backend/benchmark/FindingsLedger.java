package com.studysnap.backend.benchmark;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Consumer rule: any InvalidLedger/integrity failure THROWS and means BLOCKED. "Open" only
 * describes an intact indexed record with absent or semantically invalid resolution.
 */
final class FindingsLedger {
    static final class InvalidLedger extends RuntimeException {
        InvalidLedger(String message, Throwable cause) { super(message, cause); }
    }
    private final SafetyFindingStore safety;
    private final EvidenceDisputeStore disputes;
    private final PostLockContext context;
    FindingsLedger(Path safetyDir, Path disputeDir, PostLockContext context) {
        try {
            PostLockContext.separate(safetyDir, disputeDir);
            this.context = context;
            safety = new SafetyFindingStore(safetyDir, context);
            disputes = new EvidenceDisputeStore(disputeDir, context);
            safety.headSha256(); disputes.headSha256();
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
        return !openSafetyFindings(route).isEmpty() || !openDisputes(route).isEmpty();
    }
    boolean blocksBenchmarkPass(String route) {
        return !openSafetyFindings(route).isEmpty() || !openDisputes(route).isEmpty();
    }
    String checklistLine(String route) {
        try {
            List<String> ids = new ArrayList<>();
            openSafetyFindings(route).forEach(f -> ids.add(f.findingId()));
            openDisputes(route).forEach(d -> ids.add(d.disputeId()));
            ids.sort(String::compareTo);
            PostLockStoreIndex.Head a = safety.headSha256(), b = disputes.headSha256();
            return (ids.isEmpty() ? "no open safety finding" : "open safety findings or disputes: " + String.join(",", ids))
                    + "\nsafety store head: " + a.seq() + ":" + a.sha256()
                    + "\ndispute store head: " + b.seq() + ":" + b.sha256();
        } catch (Exception exception) { throw new InvalidLedger("Cannot build checklist line", exception); }
    }
    private void validate(String route) { context.routeName(context.verify(), route); }
}
