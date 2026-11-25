package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Key-free checks against an owner-noted lock and access-log anchor. The owner must note the
 * rendered text or fingerprint outside repository files before the first open; code cannot prove
 * when that happened. Only the supplied log is checked: neither seal nor lock names a log path,
 * and the ordinary key file is logged but not protected from direct reads. An open into another
 * caller-chosen log, a direct file read, or entries removed after the noted head cannot be
 * detected. At the designed
 * zero-entry head, the history-prefix check is vacuous; every supplied entry must still name only
 * anchored locks. A second lock used as evidence is detectable only when the caller supplies the
 * same locks and logs to these checks as to FrbProvenance (FrbProvenance.java:171,175,282).
 * No current caller does that: the second-lock gap is verifiable by LockAnchors once
 * Brief 5r-2 wires it. ReserveDecisionRecord binds a caller-written decision, not an external note.
 * This class does not compare access-entry key hashes with sealed hashes.
 */
final class LockAnchors {
    static final class InvalidLockAnchor extends RuntimeException {
        InvalidLockAnchor(String message) { super(message); }
        InvalidLockAnchor(String message, Throwable cause) { super(message, cause); }
    }

    record Anchor(LockRecord.Kind kind, String sealSha256, String lockSha256) {
        Anchor {
            if (kind == null || !IntegritySupport.digest(sealSha256) || !IntegritySupport.digest(lockSha256))
                throw new InvalidLockAnchor("Invalid anchor");
        }
    }
    record LogHead(int entries, String lastLineSha256OrNull) {
        LogHead {
            if (entries < 0 || (entries == 0 ? lastLineSha256OrNull != null
                    : !IntegritySupport.digest(lastLineSha256OrNull)))
                throw new InvalidLockAnchor("Invalid log head");
        }
    }
    record Noted(List<Anchor> anchors, LogHead head) {
        Noted {
            if (anchors == null || anchors.isEmpty() || head == null)
                throw new InvalidLockAnchor("Nonempty anchors and log head required");
            Set<Anchor> distinct = new HashSet<>();
            Set<String> pairs = new HashSet<>();
            for (Anchor anchor : anchors) {
                if (anchor == null || !distinct.add(anchor))
                    throw new InvalidLockAnchor("Duplicate or null anchor");
                if (!pairs.add(anchor.kind() + ":" + anchor.sealSha256()))
                    throw new InvalidLockAnchor("Ambiguous second lock for kind and seal");
            }
            anchors = anchors.stream().sorted(ORDER).toList();
        }
    }

    private static final String ANCHORS = "anchors";
    private static final String HEAD = "head";
    private static final String KIND = "kind";
    private static final String SEAL_SHA = "sealSha256";
    private static final String LOCK_SHA = "lockSha256";
    private static final String ENTRIES = "entries";
    private static final String LAST_LINE_SHA = "lastLineSha256OrNull";
    private static final String CANNOT_READ_LOG = "Cannot read access log";
    private static final Set<String> NOTE_FIELDS = Set.of(ANCHORS, HEAD);
    private static final Set<String> ANCHOR_FIELDS = Set.of(KIND, SEAL_SHA, LOCK_SHA);
    private static final Set<String> HEAD_FIELDS = Set.of(ENTRIES, LAST_LINE_SHA);
    private static final Comparator<Anchor> ORDER = Comparator.comparing((Anchor a) -> a.kind().name())
            .thenComparing(Anchor::sealSha256).thenComparing(Anchor::lockSha256);
    private static final String ANCHOR_MISMATCH = "Bound lock is not in the noted anchors";
    private static final String LOG_REQUIRED = "Access log required";
    private LockAnchors() {}

    static Anchor anchorOf(Path lockFile, Path sealFile) {
        try {
            String lockHash = IntegritySupport.detachedHash(lockFile);
            String sealHash = IntegritySupport.detachedHash(sealFile);
            LockRecord lock = LockRecord.load(lockFile);
            ArmKeySeal.load(sealFile);
            if (!lock.sealSha256().equals(sealHash))
                throw new InvalidLockAnchor("Lock and seal differ");
            return new Anchor(lock.kind(), sealHash, lockHash);
        } catch (InvalidLockAnchor exception) { throw exception; }
        catch (Exception exception) { throw new InvalidLockAnchor("Cannot verify lock and seal", exception); }
    }

    static LogHead headOf(Path logFile) {
        if (logFile == null) throw new InvalidLockAnchor(LOG_REQUIRED);
        if (!Files.exists(logFile)) return new LogHead(0, null);
        List<ArmKeyAccess.AccessEntry> entries = ArmKeyAccess.validateAccessLog(logFile);
        try { return new LogHead(entries.size(), lineHashes(logFile).getLast()); }
        catch (IOException exception) { throw new InvalidLockAnchor(CANNOT_READ_LOG, exception); }
    }

    static String render(Noted noted) {
        if (noted == null) throw new InvalidLockAnchor("Noted anchor required");
        try {
            ObjectNode root = IntegritySupport.JSON.createObjectNode();
            ArrayNode anchors = root.putArray(ANCHORS);
            noted.anchors().stream().sorted(ORDER).forEach(anchor -> {
                ObjectNode row = anchors.addObject();
                row.put(KIND, anchor.kind().name());
                row.put(SEAL_SHA, anchor.sealSha256());
                row.put(LOCK_SHA, anchor.lockSha256());
            });
            ObjectNode head = root.putObject(HEAD);
            head.put(ENTRIES, noted.head().entries());
            if (noted.head().entries() == 0) head.putNull(LAST_LINE_SHA);
            else head.put(LAST_LINE_SHA, noted.head().lastLineSha256OrNull());
            return IntegritySupport.JSON.writeValueAsString(root);
        } catch (Exception exception) { throw new InvalidLockAnchor("Cannot render note", exception); }
    }

    static Noted parse(String text) {
        try {
            JsonNode root = IntegritySupport.JSON.readTree(text);
            IntegritySupport.fields(root, NOTE_FIELDS);
            JsonNode rows = root.get(ANCHORS);
            if (!rows.isArray()) throw new InvalidLockAnchor("Invalid anchors array");
            List<Anchor> anchors = new ArrayList<>();
            for (JsonNode row : rows) {
                IntegritySupport.fields(row, ANCHOR_FIELDS);
                anchors.add(new Anchor(LockRecord.Kind.valueOf(IntegritySupport.string(row, KIND)),
                        IntegritySupport.hash(row, SEAL_SHA), IntegritySupport.hash(row, LOCK_SHA)));
            }
            JsonNode headNode = root.get(HEAD);
            IntegritySupport.fields(headNode, HEAD_FIELDS);
            if (!headNode.path(ENTRIES).isInt()) throw new InvalidLockAnchor("Invalid entry count");
            JsonNode hash = headNode.get(LAST_LINE_SHA);
            if (!hash.isNull() && !hash.isTextual()) throw new InvalidLockAnchor("Invalid head hash");
            Noted noted = new Noted(anchors, new LogHead(headNode.path(ENTRIES).asInt(),
                    hash.isNull() ? null : hash.asText()));
            if (!render(noted).equals(text)) throw new InvalidLockAnchor("Noncanonical anchor note");
            return noted;
        } catch (InvalidLockAnchor exception) { throw exception; }
        catch (Exception exception) { throw new InvalidLockAnchor("Cannot parse anchor note", exception); }
    }

    static String fingerprint(Noted noted) {
        return FreezeRecord.sha256(render(noted).getBytes(StandardCharsets.UTF_8));
    }

    /** Every bound lock must be anchored; some anchored locks may be absent from one open. */
    static void requireBeforeOpen(Noted noted, List<ArmKeyAccess.LockBinding> bindings, Path logFile) {
        if (noted == null || bindings == null || bindings.isEmpty() || logFile == null)
            throw new InvalidLockAnchor("Nonempty bindings and note required");
        for (ArmKeyAccess.LockBinding binding : bindings) {
            if (binding == null || !noted.anchors().contains(anchorOf(binding.lockFile(), binding.sealFile())))
                throw new InvalidLockAnchor(ANCHOR_MISMATCH);
        }
        if (!Files.exists(logFile)) {
            if (noted.head().entries() != 0) throw new InvalidLockAnchor(LOG_REQUIRED);
            return;
        }
        verifyLog(noted, logFile);
    }

    static void verifyAfterOpen(Noted noted, Path logFile) {
        if (noted == null || logFile == null || !Files.exists(logFile))
            throw new InvalidLockAnchor(LOG_REQUIRED);
        verifyLog(noted, logFile);
    }

    static void verifyLocksOnDisk(Noted noted, Map<Path, Path> lockToSeal) {
        if (noted == null || lockToSeal == null) throw new InvalidLockAnchor("Locks and note required");
        for (Map.Entry<Path, Path> entry : lockToSeal.entrySet())
            if (!noted.anchors().contains(anchorOf(entry.getKey(), entry.getValue())))
                throw new InvalidLockAnchor(ANCHOR_MISMATCH);
    }

    private static void verifyLog(Noted noted, Path logFile) {
        List<ArmKeyAccess.AccessEntry> entries = ArmKeyAccess.validateAccessLog(logFile);
        if (entries.size() < noted.head().entries())
            throw new InvalidLockAnchor("Access log shorter than noted head");
        if (noted.head().entries() > 0) {
            try {
                if (!lineHashes(logFile).get(noted.head().entries() - 1)
                        .equals(noted.head().lastLineSha256OrNull()))
                    throw new InvalidLockAnchor("Noted access-log history changed");
            } catch (IOException exception) { throw new InvalidLockAnchor(CANNOT_READ_LOG, exception); }
        }
        Set<String> allowed = new HashSet<>();
        noted.anchors().forEach(anchor -> allowed.add(anchor.lockSha256()));
        for (ArmKeyAccess.AccessEntry entry : entries)
            if (!allowed.containsAll(entry.lockSha256s()))
                throw new InvalidLockAnchor("Unanchored lock in access-log entry seq " + entry.seq());
    }

    private static List<String> lineHashes(Path logFile) throws IOException {
        String body = Files.readString(logFile, StandardCharsets.UTF_8);
        String[] lines = body.substring(0, body.length() - 1).split("\n", -1);
        List<String> hashes = new ArrayList<>(lines.length);
        for (String line : lines) hashes.add(FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8)));
        return hashes;
    }
}
