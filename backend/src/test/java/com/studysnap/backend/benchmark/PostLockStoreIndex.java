package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Append-only byte chain. A file and its detached hash are written before its index entry. A crash
 * between those writes, or concurrent writers, fails closed; there is no in-place repair. Truncating
 * a suffix together with its files remains undetectable without an externally noted seq:head.
 * Every integrity failure throws InvalidStore and consumers must treat that as BLOCKED. An open
 * record is returned only when its indexed bytes are intact and its resolution is absent or semantically invalid.
 */
final class PostLockStoreIndex {
    static final class InvalidStore extends RuntimeException {
        InvalidStore(String message) { super(message); }
        InvalidStore(String message, Throwable cause) { super(message, cause); }
    }
    enum Kind { GENESIS, RECORD, RESOLUTION }
    record Entry(int seq, String prevSha256, Kind kind, String id, String fileSha256,
                 String lockSha256, String parametersSha256, String writtenAtUtc) {}
    record Head(int seq, String sha256) {}
    private static final String INDEX = "index.jsonl";
    private static final Set<String> FIELDS = Set.of("seq", "prevSha256", "kind", "id", "fileSha256",
            "lockSha256", "parametersSha256", "writtenAtUtc");
    private final Path dir;
    PostLockStoreIndex(Path dir) { this.dir = dir; }

    static void init(Path dir, String lockSha256, String parametersSha256, String utc) {
        try {
            if (dir == null || !IntegritySupport.digest(lockSha256) || !IntegritySupport.digest(parametersSha256))
                throw new InvalidStore("Invalid genesis inputs");
            IntegritySupport.utc(utc);
            Files.createDirectory(dir);
            Entry genesis = new Entry(0, null, Kind.GENESIS, null, null, lockSha256, parametersSha256, utc);
            Files.writeString(dir.resolve(INDEX), IntegritySupport.JSON.writeValueAsString(genesis) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (InvalidStore exception) { throw exception; }
        catch (Exception exception) { throw new InvalidStore("Cannot initialize store", exception); }
    }

    List<Entry> verify() { return verify(null); }
    private List<Entry> verify(Path pending) {
        try {
            if (!Files.isDirectory(dir)) throw new InvalidStore("Store directory missing");
            String body = Files.readString(dir.resolve(INDEX), StandardCharsets.UTF_8);
            if (body.isEmpty() || !body.endsWith("\n") || body.contains("\r"))
                throw new InvalidStore("Missing genesis or malformed index");
            String[] lines = body.substring(0, body.length() - 1).split("\n", -1);
            List<Entry> entries = new ArrayList<>();
            Set<String> expectedFiles = new HashSet<>(Set.of(INDEX));
            Set<String> records = new HashSet<>(), resolutions = new HashSet<>();
            String previous = null;
            String lockHash = null, paramsHash = null;
            for (int i = 0; i < lines.length; i++) {
                JsonNode node = IntegritySupport.JSON.readTree(lines[i]);
                IntegritySupport.fields(node, FIELDS);
                String expectedPrev = previous == null ? null : FreezeRecord.sha256(previous.getBytes(StandardCharsets.UTF_8));
                if (!node.path("seq").isInt() || node.path("seq").asInt() != i
                        || (expectedPrev == null ? !node.path("prevSha256").isNull()
                        : !expectedPrev.equals(node.path("prevSha256").asText())))
                    throw new InvalidStore("Broken index chain");
                Kind kind = Kind.valueOf(IntegritySupport.string(node, "kind"));
                String id = node.path("id").isNull() ? null : IntegritySupport.string(node, "id");
                String fileHash = node.path("fileSha256").isNull() ? null : IntegritySupport.hash(node, "fileSha256");
                String entryLock = IntegritySupport.hash(node, "lockSha256");
                String entryParams = IntegritySupport.hash(node, "parametersSha256");
                String utc = IntegritySupport.string(node, "writtenAtUtc");
                IntegritySupport.utc(utc);
                if (i == 0) {
                    if (kind != Kind.GENESIS || id != null || fileHash != null)
                        throw new InvalidStore("Invalid genesis");
                    lockHash = entryLock; paramsHash = entryParams;
                } else {
                    if (kind == Kind.GENESIS || !validId(id) || fileHash == null
                            || !entryLock.equals(lockHash) || !entryParams.equals(paramsHash))
                        throw new InvalidStore("Invalid index entry");
                    if (kind == Kind.RECORD ? !records.add(id) : !records.contains(id) || !resolutions.add(id))
                        throw new InvalidStore("Duplicate or orphan index entry");
                    String name = fileName(id, kind);
                    Path record = dir.resolve(name);
                    if (!fileHash.equals(IntegritySupport.detachedHash(record)))
                        throw new InvalidStore("Indexed file hash differs");
                    expectedFiles.add(name);
                    expectedFiles.add(name + ".sha256");
                }
                entries.add(new Entry(i, expectedPrev, kind, id, fileHash, entryLock, entryParams, utc));
                previous = lines[i];
            }
            if (pending != null) {
                expectedFiles.add(pending.getFileName().toString());
                expectedFiles.add(pending.getFileName() + ".sha256");
            }
            try (var files = Files.list(dir)) {
                Set<String> actual = files.map(path -> path.getFileName().toString())
                        .collect(java.util.stream.Collectors.toSet());
                if (!actual.equals(expectedFiles)) throw new InvalidStore("Orphan, hash-less or index-less file");
            }
            return List.copyOf(entries);
        } catch (InvalidStore exception) { throw exception; }
        catch (Exception exception) { throw new InvalidStore("Cannot verify store index", exception); }
    }

    void append(Kind kind, String id, Path record, String utc) {
        try {
            List<Entry> entries = verify(record);
            if (kind == Kind.GENESIS || !validId(id) || !record.equals(dir.resolve(fileName(id, kind))))
                throw new InvalidStore("Invalid append target");
            if (kind == Kind.RECORD && entries.stream().anyMatch(e -> e.kind() == kind && id.equals(e.id()))
                    || kind == Kind.RESOLUTION && (entries.stream().noneMatch(e -> e.kind() == Kind.RECORD && id.equals(e.id()))
                    || entries.stream().anyMatch(e -> e.kind() == kind && id.equals(e.id()))))
                throw new InvalidStore("Duplicate or orphan append");
            IntegritySupport.utc(utc);
            String hash = IntegritySupport.detachedHash(record);
            String body = Files.readString(dir.resolve(INDEX), StandardCharsets.UTF_8);
            String[] lines = body.substring(0, body.length() - 1).split("\n");
            Entry genesis = entries.getFirst();
            Entry next = new Entry(entries.size(), FreezeRecord.sha256(lines[lines.length - 1].getBytes(StandardCharsets.UTF_8)),
                    kind, id, hash, genesis.lockSha256(), genesis.parametersSha256(), utc);
            Files.writeString(dir.resolve(INDEX), IntegritySupport.JSON.writeValueAsString(next) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            verify();
        } catch (InvalidStore exception) { throw exception; }
        catch (Exception exception) { throw new InvalidStore("Cannot append store index", exception); }
    }

    Head headSha256() {
        verify();
        try {
            String body = Files.readString(dir.resolve(INDEX), StandardCharsets.UTF_8);
            String[] lines = body.substring(0, body.length() - 1).split("\n");
            return new Head(lines.length - 1, FreezeRecord.sha256(lines[lines.length - 1].getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new InvalidStore("Cannot read index head", exception); }
    }

    void verifyPrefix(int seq, String sha) {
        verify();
        try {
            String body = Files.readString(dir.resolve(INDEX), StandardCharsets.UTF_8);
            String[] lines = body.substring(0, body.length() - 1).split("\n");
            if (seq < 0 || seq >= lines.length || !IntegritySupport.digest(sha)
                    || !sha.equals(FreezeRecord.sha256(lines[seq].getBytes(StandardCharsets.UTF_8))))
                throw new InvalidStore("Externally noted prefix absent");
        } catch (InvalidStore exception) { throw exception; }
        catch (Exception exception) { throw new InvalidStore("Cannot verify index prefix", exception); }
    }
    static boolean validId(String id) {
        return id != null && id.matches("[a-z0-9_-]+")
                && !id.endsWith(".resolution") && !id.endsWith(".sha256");
    }
    static String fileName(String id, Kind kind) {
        return id + (kind == Kind.RESOLUTION ? ".resolution.json" : ".json");
    }
}
