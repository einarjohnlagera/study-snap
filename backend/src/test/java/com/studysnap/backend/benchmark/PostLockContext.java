package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Verified, route-aware lock binding for post-lock records. Integrity errors block all consumers. */
record PostLockContext(Path lockFile, Path findingsDirectory, Path packetDirectory,
                       Path sealFile, Path parametersFile) {
    private static final Set<String> CLASS_B = Set.of("STUDY_TIP", "COLLECTION_COMPANION",
            "ASK_COMPANION", "INTERVIEW_CRITIQUE");
    record Verified(LockRecord lock, String lockSha256, FrozenGateParameters parameters) {}
    Verified verify() {
        try {
            LockRecord lock = LockRecord.load(lockFile);
            LockRecord.verifyFindingsUnchanged(lock, findingsDirectory);
            ArmKeySeal.Seal seal = ArmKeySeal.load(sealFile);
            ArmKeySeal.verifyPackets(packetDirectory, seal);
            List<LockRecord.PacketDigest> digests = seal.packetFiles().stream()
                    .map(file -> new LockRecord.PacketDigest(file.name(), file.sha256())).toList();
            if (!lock.sealSha256().equals(IntegritySupport.detachedHash(sealFile))
                    || !lock.armKeySha256().equals(seal.armKeySha256())
                    || !lock.packetFiles().equals(digests)
                    || !seal.workflowStems().containsAll(lock.workflows())
                    || PostLockContext.utc(lock.lockedAtUtc()).isBefore(PostLockContext.utc(seal.sealedAtUtc())))
                throw new PostLockStoreIndex.InvalidStore("Lock and seal differ");
            return new Verified(lock, IntegritySupport.detachedHash(lockFile),
                    FrozenGateParameters.load(parametersFile));
        } catch (PostLockStoreIndex.InvalidStore exception) { throw exception; }
        catch (Exception exception) { throw new PostLockStoreIndex.InvalidStore("Cannot verify locked context", exception); }
    }
    void layout(Path store) {
        try {
            Path target = realOrParent(store);
            for (Path excluded : List.of(findingsDirectory, packetDirectory, lockFile.getParent(), sealFile.getParent())) {
                Path other = excluded.toRealPath();
                if (target.startsWith(other) || other.startsWith(target))
                    throw new PostLockStoreIndex.InvalidStore("Store overlaps locked directory");
            }
        } catch (PostLockStoreIndex.InvalidStore exception) { throw exception; }
        catch (Exception exception) { throw new PostLockStoreIndex.InvalidStore("Cannot validate store layout", exception); }
    }
    static void separate(Path first, Path second) {
        try {
            Path a = realOrParent(first), b = realOrParent(second);
            if (a.startsWith(b) || b.startsWith(a))
                throw new PostLockStoreIndex.InvalidStore("Stores overlap");
        } catch (PostLockStoreIndex.InvalidStore exception) { throw exception; }
        catch (Exception exception) { throw new PostLockStoreIndex.InvalidStore("Cannot compare stores", exception); }
    }
    private static Path realOrParent(Path path) throws java.io.IOException {
        Path absolute = path.toAbsolutePath().normalize();
        if (Files.exists(absolute)) return absolute.toRealPath();
        return absolute.getParent().toRealPath().resolve(absolute.getFileName());
    }
    static Instant utc(String value) {
        try { return IntegritySupport.utc(value); }
        catch (Exception exception) { throw new PostLockStoreIndex.InvalidStore("ISO-8601 UTC required", exception); }
    }
    String route(Verified verified, String workflow) {
        FrozenGateParameters.Workflow declaration = verified.parameters().workflows.get(workflow);
        if (declaration == null) throw new PostLockStoreIndex.InvalidStore("Unknown workflow");
        String route = declaration.route();
        if (CLASS_B.contains(workflow)) {
            String actual = switch (workflow) {
                case "STUDY_TIP" -> "INTERACTIVE_QUIZ";
                case "COLLECTION_COMPANION" -> "PAID_INTERACTIVE";
                default -> "CRITIQUE";
            };
            if (!actual.equals(route)) throw new PostLockStoreIndex.InvalidStore("Class-B route mapping differs");
        }
        return route;
    }
    void routes(Verified verified, String workflow, List<String> affected) {
        String own = route(verified, workflow);
        if (affected == null || affected.isEmpty() || affected.stream().anyMatch(java.util.Objects::isNull)
                || !affected.equals(affected.stream().distinct().sorted().toList()))
            throw new PostLockStoreIndex.InvalidStore("Affected routes must be explicit, unique and sorted");
        if (affected.equals(List.of("ALL_ROUTES"))) return;
        if (affected.contains("ALL_ROUTES") || !verified.parameters().routes.keySet().containsAll(affected)
                || !affected.contains(own))
            throw new PostLockStoreIndex.InvalidStore("Unknown or under-declared affected route");
    }
    void routeName(Verified verified, String route) {
        if (route == null || !verified.parameters().routes.containsKey(route))
            throw new PostLockStoreIndex.InvalidStore("Unknown route: " + route);
    }
    Set<String> requiredClassBWorkflows(Verified verified, String route) {
        routeName(verified, route);
        if (!verified.parameters().frbEnabled)
            throw new PostLockStoreIndex.InvalidStore("FRB review parameters unavailable");
        Set<String> result = new java.util.TreeSet<>(verified.parameters().frbClassBWorkflows);
        result.retainAll(verified.parameters().routes.get(route).workflows());
        if (result.isEmpty()) throw new PostLockStoreIndex.InvalidStore("Route has no class-B review workflows");
        for (String workflow : result)
            if (!route.equals(route(verified, workflow)))
                throw new PostLockStoreIndex.InvalidStore("Class-B review route differs");
        return Set.copyOf(result);
    }
    static void id(String id) {
        if (!PostLockStoreIndex.validId(id) || java.util.Arrays.stream(id.split("[-_]"))
                .anyMatch(token -> Set.of("candidate", "baseline", "luna", "sol", "arm").contains(token)))
            throw new PostLockStoreIndex.InvalidStore("Invalid or arm-revealing id");
        try { NoArmFields.check("{\"id\":\"" + id + "\"}"); }
        catch (Exception exception) { throw new PostLockStoreIndex.InvalidStore("Arm-revealing id", exception); }
    }
    void caseId(String itemId, String caseId, ArmKeyAccess.OpenedKey opened) {
        if (caseId == null || opened == null) return;
        ArmKeyAccess.KeyRow row = opened.rows().stream().filter(value -> value.itemId().equals(itemId))
                .findFirst().orElseThrow(() -> new PostLockStoreIndex.InvalidStore("Item absent from opened key"));
        if (!caseId.equals(row.caseId())) throw new PostLockStoreIndex.InvalidStore("Case id differs from opened key");
    }
}
