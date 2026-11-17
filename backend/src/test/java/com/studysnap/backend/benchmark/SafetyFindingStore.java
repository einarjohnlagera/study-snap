package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * An integrity failure throws InvalidStore and means BLOCKED. "Open" means an intact indexed
 * finding with no resolution or a resolution that fails a semantic rule. No locked file is written.
 */
final class SafetyFindingStore {
    static final class InvalidStore extends RuntimeException {
        InvalidStore(String message, Throwable cause) { super(message, cause); }
    }
    private static final Set<String> FIELDS = Set.of("findingId", "recordedAtUtc", "lockSha256",
            "itemId", "workflow", "caseId", "affectedRoutes", "evidence", "discoveredBy",
            "attribution", "materialAbsoluteQualityConcern");
    private static final Set<String> RESOLUTION = Set.of("findingId", "findingSha256", "resolvedAtUtc",
            "ownerDisposition", "recordedBy", "productUxReview");
    private final PostLockRecordStore core;
    SafetyFindingStore(Path dir, PostLockContext context) { core = new PostLockRecordStore(dir, context); }
    static SafetyFindingStore init(Path dir, PostLockContext context, String utc) {
        try { PostLockRecordStore.init(dir, context, utc); return new SafetyFindingStore(dir, context); }
        catch (Exception exception) { throw new InvalidStore("Cannot initialize safety store", exception); }
    }
    SafetyFinding create(SafetyFinding finding, ArmKeyAccess.OpenedKey opened) {
        try {
            PostLockContext.Verified v = core.verify();
            validate(finding, v);
            core.context().caseId(finding.itemId(), finding.caseId(), opened);
            if (!PostLockContext.utc(finding.recordedAtUtc()).isAfter(PostLockContext.utc(v.lock().lockedAtUtc())))
                throw new PostLockStoreIndex.InvalidStore("Finding must follow lock");
            core.create(finding.findingId(), PostLockStoreIndex.Kind.RECORD, finding, finding.recordedAtUtc());
            return finding;
        } catch (Exception exception) { throw new InvalidStore("Cannot create safety finding", exception); }
    }
    void resolve(SafetyFinding.Resolution resolution) {
        try {
            SafetyFinding finding = load(resolution.findingId());
            if (core.has(resolution.findingId(), PostLockStoreIndex.Kind.RESOLUTION)
                    || !validResolution(resolution, finding))
                throw new PostLockStoreIndex.InvalidStore("Invalid or duplicate resolution");
            core.create(resolution.findingId(), PostLockStoreIndex.Kind.RESOLUTION,
                    resolution, resolution.resolvedAtUtc());
        } catch (Exception exception) { throw new InvalidStore("Cannot resolve safety finding", exception); }
    }
    SafetyFinding load(String id) {
        try {
            JsonNode node = core.read(id, PostLockStoreIndex.Kind.RECORD, FIELDS);
            SafetyFinding finding = IntegritySupport.JSON.treeToValue(node, SafetyFinding.class);
            if (!id.equals(finding.findingId())) throw new PostLockStoreIndex.InvalidStore("Stem and finding id differ");
            PostLockContext.Verified verified = core.verify();
            validate(finding, verified);
            if (!PostLockContext.utc(finding.recordedAtUtc()).isAfter(PostLockContext.utc(verified.lock().lockedAtUtc())))
                throw new PostLockStoreIndex.InvalidStore("Finding predates lock");
            return finding;
        } catch (Exception exception) { throw new InvalidStore("Cannot load safety finding", exception); }
    }
    List<SafetyFinding> open(String route) {
        try {
            PostLockContext.Verified v = core.verify();
            core.context().routeName(v, route);
            List<SafetyFinding> result = new ArrayList<>();
            for (PostLockStoreIndex.Entry entry : core.entries()) if (entry.kind() == PostLockStoreIndex.Kind.RECORD) {
                SafetyFinding finding = load(entry.id());
                SafetyFinding.Resolution resolution = null;
                if (core.has(entry.id(), PostLockStoreIndex.Kind.RESOLUTION)) {
                    JsonNode node = core.read(entry.id(), PostLockStoreIndex.Kind.RESOLUTION, RESOLUTION);
                    resolution = IntegritySupport.JSON.treeToValue(node, SafetyFinding.Resolution.class);
                    if (!entry.id().equals(resolution.findingId())
                            || !core.hash(entry.id()).equals(resolution.findingSha256()))
                        throw new PostLockStoreIndex.InvalidStore("Resolution stem or hash link differs");
                }
                if (!finding.affectedRoutes().contains("ALL_ROUTES") && !finding.affectedRoutes().contains(route)) continue;
                if (resolution == null || !validResolution(resolution, finding)) result.add(finding);
            }
            result.sort(java.util.Comparator.comparing(SafetyFinding::findingId));
            return List.copyOf(result);
        } catch (Exception exception) { throw new InvalidStore("Cannot query safety store", exception); }
    }
    PostLockStoreIndex.Head headSha256() { try { return core.headSha256(); }
        catch (Exception exception) { throw new InvalidStore("Cannot read safety head", exception); } }
    void verifyPrefix(int seq, String sha) { try { core.verifyPrefix(seq, sha); }
        catch (Exception exception) { throw new InvalidStore("Cannot verify safety prefix", exception); } }
    private void validate(SafetyFinding f, PostLockContext.Verified v) {
        if (f == null) throw new PostLockStoreIndex.InvalidStore("Finding required");
        PostLockContext.id(f.findingId());
        if (!v.lockSha256().equals(f.lockSha256()) || f.itemId() == null || f.itemId().isBlank()
                || f.evidence() == null || f.evidence().isBlank() || f.discoveredBy() == null
                || f.discoveredBy().isBlank() || f.attribution() == null)
            throw new PostLockStoreIndex.InvalidStore("Invalid finding fields or lock link");
        PostLockContext.utc(f.recordedAtUtc());
        core.context().routes(v, f.workflow(), f.affectedRoutes());
        PairMap packets = PairMap.fromPackets(core.context().packetDirectory());
        String pair = packets.pairOf(f.itemId());
        if (pair == null || !f.workflow().equals(packets.workflowOf(pair)))
            throw new PostLockStoreIndex.InvalidStore("Finding item absent from workflow packets");
    }
    private boolean validResolution(SafetyFinding.Resolution r, SafetyFinding f) {
        try {
            return r != null && f.findingId().equals(r.findingId()) && core.hash(f.findingId()).equals(r.findingSha256())
                    && PostLockContext.utc(r.resolvedAtUtc()).isAfter(PostLockContext.utc(f.recordedAtUtc()))
                    && r.ownerDisposition() != null && !r.ownerDisposition().isBlank()
                    && r.recordedBy() != null && !r.recordedBy().isBlank()
                    && (f.materialAbsoluteQualityConcern() ? r.productUxReview() != null && !r.productUxReview().isBlank()
                    : r.productUxReview() == null);
        } catch (Exception exception) { return false; }
    }
    String findingSha256(String id) { return core.hash(id); }
}
