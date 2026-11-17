package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Shared file and chain mechanics; all reads re-verify locked evidence and the complete index. */
final class PostLockRecordStore {
    private final Path dir;
    private final PostLockContext context;
    private final PostLockStoreIndex index;
    PostLockRecordStore(Path dir, PostLockContext context) {
        this.dir = dir; this.context = context; this.index = new PostLockStoreIndex(dir);
    }
    static void init(Path dir, PostLockContext context, String utc) {
        context.layout(dir);
        PostLockContext.Verified verified = context.verify();
        if (!PostLockContext.utc(utc).isAfter(PostLockContext.utc(verified.lock().lockedAtUtc())))
            throw new PostLockStoreIndex.InvalidStore("Store genesis must follow lock");
        PostLockStoreIndex.init(dir, verified.lockSha256(), verified.parameters().sha256, utc);
    }
    PostLockContext.Verified verify() {
        context.layout(dir);
        PostLockContext.Verified verified = context.verify();
        List<PostLockStoreIndex.Entry> entries = index.verify();
        PostLockStoreIndex.Entry genesis = entries.getFirst();
        if (!genesis.lockSha256().equals(verified.lockSha256())
                || !genesis.parametersSha256().equals(verified.parameters().sha256)
                || !PostLockContext.utc(genesis.writtenAtUtc()).isAfter(PostLockContext.utc(verified.lock().lockedAtUtc())))
            throw new PostLockStoreIndex.InvalidStore("Store genesis differs from locked context");
        return verified;
    }
    List<PostLockStoreIndex.Entry> entries() { verify(); return index.verify(); }
    Path path(String id, PostLockStoreIndex.Kind kind) {
        PostLockContext.id(id);
        return dir.resolve(PostLockStoreIndex.fileName(id, kind));
    }
    void create(String id, PostLockStoreIndex.Kind kind, Object value, String utc) {
        try {
            verify();
            Path file = path(id, kind);
            if (Files.exists(file) || Files.exists(IntegritySupport.detached(file)))
                throw new PostLockStoreIndex.InvalidStore("Record already exists");
            IntegritySupport.create(file, IntegritySupport.JSON.writeValueAsBytes(value));
            index.append(kind, id, file, utc);
        } catch (PostLockStoreIndex.InvalidStore exception) { throw exception; }
        catch (Exception exception) { throw new PostLockStoreIndex.InvalidStore("Cannot create indexed file", exception); }
    }
    JsonNode read(String id, PostLockStoreIndex.Kind kind, Set<String> fields) {
        try {
            if (entries().stream().noneMatch(e -> e.kind() == kind && id.equals(e.id())))
                throw new PostLockStoreIndex.InvalidStore("Unindexed record");
            JsonNode node = IntegritySupport.JSON.readTree(Files.readAllBytes(path(id, kind)));
            IntegritySupport.fields(node, fields);
            return node;
        } catch (PostLockStoreIndex.InvalidStore exception) { throw exception; }
        catch (Exception exception) { throw new PostLockStoreIndex.InvalidStore("Cannot read indexed record", exception); }
    }
    boolean has(String id, PostLockStoreIndex.Kind kind) {
        return entries().stream().anyMatch(e -> e.kind() == kind && id.equals(e.id()));
    }
    String hash(String id) {
        try { return IntegritySupport.detachedHash(path(id, PostLockStoreIndex.Kind.RECORD)); }
        catch (Exception exception) { throw new PostLockStoreIndex.InvalidStore("Cannot hash finding", exception); }
    }
    PostLockStoreIndex.Head headSha256() { verify(); return index.headSha256(); }
    void verifyPrefix(int seq, String sha) { verify(); index.verifyPrefix(seq, sha); }
    PostLockContext context() { return context; }
}
