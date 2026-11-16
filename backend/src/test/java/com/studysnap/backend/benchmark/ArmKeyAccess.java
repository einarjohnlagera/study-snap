package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gate for new arm-key readers. Existing ScoreEvidence and other harness readers still open keys
 * directly; briefs 5e/5g must rewire them before this is structurally enforced on the results pipeline.
 * Caller timestamps prove only consistent ordering. The operator must externally anchor the lock by
 * committing its hash-only record before opening the key; byte-identical findings supply the durable check.
 */
final class ArmKeyAccess {
    static final class KeyLocked extends RuntimeException {
        KeyLocked(String message) { super(message); }
        KeyLocked(String message, Throwable cause) { super(message, cause); }
    }
    static final class InvalidAccessLog extends RuntimeException {
        InvalidAccessLog(String message) { super(message); }
        InvalidAccessLog(String message, Throwable cause) { super(message, cause); }
    }
    static final class InvalidKeyShape extends RuntimeException {
        InvalidKeyShape(String message) { super(message); }
        InvalidKeyShape(String message, Throwable cause) { super(message, cause); }
    }
    record LockBinding(Path lockFile, Path sealFile, Path findingsDirectory) {}
    record KeyRow(String itemId, String arm, String workflow, String caseId, Boolean reserve) {}
    static final class OpenedKey {
        private final List<KeyRow> rows;
        private final Map<String, KeyRow> byId;
        private OpenedKey(List<KeyRow> rows) {
            this.rows = List.copyOf(rows);
            Map<String, KeyRow> index = new HashMap<>();
            rows.forEach(row -> index.put(row.itemId(), row));
            this.byId = Map.copyOf(index);
        }
        List<KeyRow> rows() { return rows; }
        String armOf(String itemId) { return require(itemId).arm(); }
        String workflowOf(String itemId) { return require(itemId).workflow(); }
        private KeyRow require(String itemId) {
            KeyRow row = byId.get(itemId);
            if (row == null) throw new InvalidKeyShape("Unknown item id: " + itemId);
            return row;
        }
    }
    record AccessEntry(int seq, String prevSha256, String openedAtUtc, String purpose,
                       List<LockRecord.Kind> requiredKinds, List<String> lockSha256s, String armKeySha256) {}
    private static final Set<String> LOG_FIELDS = Set.of("seq", "prevSha256", "openedAtUtc", "purpose",
            "requiredKinds", "lockSha256s", "armKeySha256");
    private static final Set<String> A_FIELDS = Set.of("id", "arm", "caseId", "workflow", "questionFormat");
    private static final Set<String> B_FIELDS = Set.of("itemId", "arm", "caseId", "workflow", "reserve");
    private ArmKeyAccess() {}

    static OpenedKey open(Path armKey, Path sealFile, List<LockBinding> locks,
                          Set<LockRecord.Kind> requiredKinds, Path accessLog,
                          String openedAtUtc, String purpose) {
        ArmKeySeal.Seal seal = ArmKeySeal.load(sealFile);
        ArmKeySeal.verifyKeyBytes(armKey, seal);
        if (locks == null || requiredKinds == null || requiredKinds.isEmpty()
                || !requiredKinds.contains(LockRecord.Kind.PRIMARY) || purpose == null || purpose.isBlank())
            throw new KeyLocked("PRIMARY and nonempty purpose required");
        Instant opened;
        try { opened = IntegritySupport.utc(openedAtUtc); }
        catch (Exception exception) { throw new KeyLocked("Invalid open time", exception); }
        if (opened.isBefore(IntegritySupport.utc(seal.sealedAtUtc()))) throw new KeyLocked("Open predates seal");
        List<String> lockHashes = new ArrayList<>();
        Set<LockRecord.Kind> present = new HashSet<>();
        boolean primaryBoundAndCovered = false;
        String openedSealHash;
        try { openedSealHash = IntegritySupport.detachedHash(sealFile); }
        catch (IOException exception) { throw new ArmKeySeal.SealBroken("Cannot verify opened seal", exception); }
        for (LockBinding binding : locks) {
            if (binding == null) throw new KeyLocked("Null lock binding");
            LockRecord lock = LockRecord.load(binding.lockFile());
            ArmKeySeal.Seal boundSeal = ArmKeySeal.load(binding.sealFile());
            String boundHash;
            try {
                boundHash = IntegritySupport.detachedHash(binding.sealFile());
                lockHashes.add(IntegritySupport.detachedHash(binding.lockFile()));
            } catch (IOException exception) { throw new LockRecord.LockBroken("Cannot verify lock binding", exception); }
            List<LockRecord.PacketDigest> packetDigests = boundSeal.packetFiles().stream()
                    .map(packet -> new LockRecord.PacketDigest(packet.name(), packet.sha256())).toList();
            if (!boundHash.equals(lock.sealSha256()) || !boundSeal.armKeySha256().equals(lock.armKeySha256())
                    || !packetDigests.equals(lock.packetFiles())
                    || !boundSeal.workflowStems().containsAll(lock.workflows()))
                throw new LockRecord.LockBroken("Lock and bound seal differ");
            LockRecord.verifyFindingsUnchanged(lock, binding.findingsDirectory());
            if (!IntegritySupport.utc(lock.lockedAtUtc()).isBefore(opened))
                throw new KeyLocked("Lock must precede open");
            present.add(lock.kind());
            if (lock.kind() == LockRecord.Kind.RESERVE
                    && boundHash.equals(openedSealHash))
                throw new KeyLocked("Reserve lock must use its own sealed key");
            if (lock.kind() == LockRecord.Kind.RESERVE
                    && !lock.workflows().containsAll(boundSeal.workflowStems()))
                throw new KeyLocked("Reserve lock lacks full key coverage");
            if (lock.kind() == LockRecord.Kind.PRIMARY && boundHash.equals(openedSealHash)
                    && lock.workflows().containsAll(seal.workflowStems())) primaryBoundAndCovered = true;
        }
        if (!present.containsAll(requiredKinds) || !primaryBoundAndCovered)
            throw new KeyLocked("Required locks or full primary coverage missing");
        append(accessLog, openedAtUtc, purpose, requiredKinds, lockHashes, seal.armKeySha256());
        return new OpenedKey(parseKey(armKey));
    }

    static List<AccessEntry> validateAccessLog(Path file) {
        try {
            String body = Files.readString(file, StandardCharsets.UTF_8);
            if (body.isEmpty() || !body.endsWith("\n") || body.contains("\r"))
                throw new InvalidAccessLog("Log must be LF-terminated JSONL");
            String[] lines = body.substring(0, body.length() - 1).split("\n", -1);
            List<AccessEntry> entries = new ArrayList<>();
            String previous = null;
            for (int i = 0; i < lines.length; i++) {
                JsonNode node = IntegritySupport.JSON.readTree(lines[i]);
                IntegritySupport.fields(node, LOG_FIELDS);
                String expected = previous == null ? null : FreezeRecord.sha256(previous.getBytes(StandardCharsets.UTF_8));
                if (!node.path("seq").isInt() || node.path("seq").asInt() != i
                        || (expected == null ? !node.path("prevSha256").isNull()
                                : !expected.equals(node.path("prevSha256").asText())))
                    throw new InvalidAccessLog("Broken access-log chain");
                String utc = IntegritySupport.string(node, "openedAtUtc");
                IntegritySupport.utc(utc);
                String purpose = IntegritySupport.string(node, "purpose");
                List<LockRecord.Kind> kinds = new ArrayList<>();
                if (!node.path("requiredKinds").isArray()) throw new IOException("Invalid requiredKinds");
                for (JsonNode value : node.path("requiredKinds")) {
                    if (!value.isTextual()) throw new IOException("Invalid kind");
                    kinds.add(LockRecord.Kind.valueOf(value.asText()));
                }
                if (kinds.isEmpty() || !kinds.contains(LockRecord.Kind.PRIMARY)
                        || !kinds.equals(kinds.stream().distinct().sorted().toList())
                        || !node.path("lockSha256s").isArray() || node.path("lockSha256s").isEmpty())
                    throw new IOException("Invalid access-log lists");
                List<String> hashes = new ArrayList<>();
                for (JsonNode value : node.path("lockSha256s")) {
                    if (!value.isTextual() || !IntegritySupport.digest(value.asText()))
                        throw new IOException("Invalid lock digest");
                    hashes.add(value.asText());
                }
                entries.add(new AccessEntry(i, expected, utc, purpose, List.copyOf(kinds), List.copyOf(hashes),
                        IntegritySupport.hash(node, "armKeySha256")));
                previous = lines[i];
            }
            return List.copyOf(entries);
        } catch (InvalidAccessLog exception) { throw exception; }
        catch (Exception exception) { throw new InvalidAccessLog("Cannot verify access log", exception); }
    }
    static void append(Path file, ObjectNode candidate) {
        try {
            if (candidate == null) throw new InvalidAccessLog("Entry required");
            List<AccessEntry> prior = Files.exists(file) ? validateAccessLog(file) : List.of();
            ObjectNode node = candidate.deepCopy();
            node.put("seq", prior.size());
            if (prior.isEmpty()) node.putNull("prevSha256");
            else {
                String body = Files.readString(file, StandardCharsets.UTF_8);
                String[] lines = body.substring(0, body.length() - 1).split("\n");
                node.put("prevSha256", FreezeRecord.sha256(lines[lines.length - 1].getBytes(StandardCharsets.UTF_8)));
            }
            IntegritySupport.fields(node, LOG_FIELDS);
            // Validate the candidate using the same strict reader before appending it.
            Path probe = Files.createTempFile("arm-key-access", ".jsonl");
            try {
                String prefix = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
                Files.writeString(probe, prefix + IntegritySupport.JSON.writeValueAsString(node) + "\n");
                validateAccessLog(probe);
            } finally { Files.deleteIfExists(probe); }
            Files.writeString(file, IntegritySupport.JSON.writeValueAsString(node) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (InvalidAccessLog exception) { throw exception; }
        catch (Exception exception) { throw new InvalidAccessLog("Cannot append access log", exception); }
    }
    private static void append(Path file, String openedAtUtc, String purpose,
                               Set<LockRecord.Kind> kinds, List<String> lockHashes, String keyHash) {
        ObjectNode node = IntegritySupport.JSON.createObjectNode();
        node.put("openedAtUtc", openedAtUtc).put("purpose", purpose);
        var required = node.putArray("requiredKinds");
        kinds.stream().sorted().forEach(kind -> required.add(kind.name()));
        var hashes = node.putArray("lockSha256s");
        lockHashes.forEach(hashes::add);
        node.put("armKeySha256", keyHash);
        append(file, node);
    }
    private static List<KeyRow> parseKey(Path file) {
        try {
            List<KeyRow> rows = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) throw new InvalidKeyShape("Blank arm-key line");
                JsonNode node = IntegritySupport.JSON.readTree(line);
                Set<String> names = new HashSet<>();
                if (node == null || !node.isObject()) throw new InvalidKeyShape("Invalid arm-key row");
                node.fieldNames().forEachRemaining(names::add);
                boolean classA = names.equals(A_FIELDS), classB = names.equals(B_FIELDS);
                if (!classA && !classB) throw new InvalidKeyShape("Unknown arm-key shape");
                String id = IntegritySupport.string(node, classA ? "id" : "itemId");
                String arm = IntegritySupport.string(node, "arm");
                String workflow = IntegritySupport.string(node, "workflow");
                String caseId = IntegritySupport.string(node, "caseId");
                if (!armLabel(classB, arm) || !ids.add(id)
                        || (classA && !node.path("questionFormat").isTextual())
                        || (classB && !node.path("reserve").isBoolean()))
                    throw new InvalidKeyShape("Invalid arm-key values");
                rows.add(new KeyRow(id, arm, workflow, caseId, classB ? node.path("reserve").asBoolean() : null));
            }
            if (rows.isEmpty()) throw new InvalidKeyShape("Empty arm key");
            return List.copyOf(rows);
        } catch (InvalidKeyShape exception) { throw exception; }
        catch (Exception exception) { throw new InvalidKeyShape("Cannot parse arm key", exception); }
    }
    /** Class A keys carry the run's own arm labels (the Stage 1 arms are LUNA, BASELINE_MINI and BASELINE_41);
     * class B keys are the two-valued {@code ClassBScoreAggregator.Arm}. The gate never interprets the label. */
    private static boolean armLabel(boolean classB, String arm) {
        return classB ? Set.of("BASELINE", "CANDIDATE").contains(arm) : arm.matches("[A-Z][A-Z0-9_]*");
    }
}
