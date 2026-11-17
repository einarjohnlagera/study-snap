package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Integrity failure throws InvalidStore and means BLOCKED. An intact indexed dispute is open when
 * unresolved, semantically invalid, or accepted for owner decision. Only UPHELD_LOCKED_EVIDENCE closes.
 */
final class EvidenceDisputeStore {
    static final class InvalidStore extends RuntimeException {
        InvalidStore(String message, Throwable cause) { super(message, cause); }
    }
    private static final Set<String> FIELDS = Set.of("disputeId", "recordedAtUtc", "lockSha256",
            "workflow", "itemId", "caseId", "disputedBy", "reason", "affectedRoutes");
    private static final Set<String> RESOLUTION = Set.of("disputeId", "disputeSha256", "resolvedAtUtc",
            "resolution", "text", "recordedBy");
    private final PostLockRecordStore core;
    EvidenceDisputeStore(Path dir, PostLockContext context) { core = new PostLockRecordStore(dir, context); }
    static EvidenceDisputeStore init(Path dir, PostLockContext context, String utc) {
        try { PostLockRecordStore.init(dir, context, utc); return new EvidenceDisputeStore(dir, context); }
        catch (Exception exception) { throw new InvalidStore("Cannot initialize dispute store", exception); }
    }
    EvidenceDispute create(EvidenceDispute dispute, ConfirmedCriticalSource critical,
                           ArmKeyAccess.OpenedKey opened) {
        try {
            PostLockContext.Verified v = core.verify();
            validate(dispute, v);
            if (!PostLockContext.utc(dispute.recordedAtUtc()).isAfter(PostLockContext.utc(v.lock().lockedAtUtc())))
                throw new PostLockStoreIndex.InvalidStore("Dispute must follow lock");
            core.context().caseId(dispute.itemId(), dispute.caseId(), opened);
            if (critical == null) throw new PostLockStoreIndex.InvalidStore("Critical source required");
            critical.requireCritical(core.context(), v, dispute.workflow(), dispute.itemId());
            core.create(dispute.disputeId(), PostLockStoreIndex.Kind.RECORD, dispute, dispute.recordedAtUtc());
            return dispute;
        } catch (Exception exception) { throw new InvalidStore("Cannot create evidence dispute", exception); }
    }
    void resolve(EvidenceDispute.Resolution resolution) {
        try {
            EvidenceDispute dispute = load(resolution.disputeId());
            if (core.has(resolution.disputeId(), PostLockStoreIndex.Kind.RESOLUTION)
                    || !validResolution(resolution, dispute))
                throw new PostLockStoreIndex.InvalidStore("Invalid or duplicate resolution");
            core.create(resolution.disputeId(), PostLockStoreIndex.Kind.RESOLUTION,
                    resolution, resolution.resolvedAtUtc());
        } catch (Exception exception) { throw new InvalidStore("Cannot resolve dispute", exception); }
    }
    EvidenceDispute load(String id) {
        try {
            JsonNode node = core.read(id, PostLockStoreIndex.Kind.RECORD, FIELDS);
            EvidenceDispute dispute = IntegritySupport.JSON.treeToValue(node, EvidenceDispute.class);
            if (!id.equals(dispute.disputeId())) throw new PostLockStoreIndex.InvalidStore("Stem and dispute id differ");
            PostLockContext.Verified verified = core.verify();
            validate(dispute, verified);
            if (!PostLockContext.utc(dispute.recordedAtUtc()).isAfter(PostLockContext.utc(verified.lock().lockedAtUtc())))
                throw new PostLockStoreIndex.InvalidStore("Dispute predates lock");
            return dispute;
        } catch (Exception exception) { throw new InvalidStore("Cannot load dispute", exception); }
    }
    List<EvidenceDispute> open(String route) {
        try {
            PostLockContext.Verified v = core.verify();
            core.context().routeName(v, route);
            List<EvidenceDispute> result = new ArrayList<>();
            for (PostLockStoreIndex.Entry entry : core.entries()) if (entry.kind() == PostLockStoreIndex.Kind.RECORD) {
                EvidenceDispute dispute = load(entry.id());
                EvidenceDispute.Resolution resolution = null;
                if (core.has(entry.id(), PostLockStoreIndex.Kind.RESOLUTION)) {
                    JsonNode node = core.read(entry.id(), PostLockStoreIndex.Kind.RESOLUTION, RESOLUTION);
                    resolution = IntegritySupport.JSON.treeToValue(node, EvidenceDispute.Resolution.class);
                    if (!entry.id().equals(resolution.disputeId())
                            || !core.hash(entry.id()).equals(resolution.disputeSha256()))
                        throw new PostLockStoreIndex.InvalidStore("Resolution stem or hash link differs");
                }
                if (!dispute.affectedRoutes().contains("ALL_ROUTES") && !dispute.affectedRoutes().contains(route)) continue;
                if (resolution == null || !validResolution(resolution, dispute)
                        || resolution.resolution() != EvidenceDispute.Outcome.UPHELD_LOCKED_EVIDENCE) result.add(dispute);
            }
            result.sort(java.util.Comparator.comparing(EvidenceDispute::disputeId));
            return List.copyOf(result);
        } catch (Exception exception) { throw new InvalidStore("Cannot query dispute store", exception); }
    }
    PostLockStoreIndex.Head headSha256() { try { return core.headSha256(); }
        catch (Exception exception) { throw new InvalidStore("Cannot read dispute head", exception); } }
    void verifyPrefix(int seq, String sha) { try { core.verifyPrefix(seq, sha); }
        catch (Exception exception) { throw new InvalidStore("Cannot verify dispute prefix", exception); } }
    String disputeSha256(String id) { return core.hash(id); }
    private void validate(EvidenceDispute d, PostLockContext.Verified v) {
        if (d == null) throw new PostLockStoreIndex.InvalidStore("Dispute required");
        PostLockContext.id(d.disputeId());
        if (!v.lockSha256().equals(d.lockSha256()) || d.itemId() == null || d.itemId().isBlank()
                || d.disputedBy() == null || d.disputedBy().isBlank()
                || d.reason() == null || d.reason().isBlank())
            throw new PostLockStoreIndex.InvalidStore("Invalid dispute fields or lock link");
        PostLockContext.utc(d.recordedAtUtc());
        core.context().routes(v, d.workflow(), d.affectedRoutes());
    }
    private boolean validResolution(EvidenceDispute.Resolution r, EvidenceDispute d) {
        try {
            return r != null && d.disputeId().equals(r.disputeId())
                    && core.hash(d.disputeId()).equals(r.disputeSha256())
                    && PostLockContext.utc(r.resolvedAtUtc()).isAfter(PostLockContext.utc(d.recordedAtUtc()))
                    && r.resolution() != null && r.text() != null && !r.text().isBlank()
                    && r.recordedBy() != null && !r.recordedBy().isBlank();
        } catch (Exception exception) { return false; }
    }
}
