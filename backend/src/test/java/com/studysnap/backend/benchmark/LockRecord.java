package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Immutable findings lock. The caller's validator owns completeness of the mandatory human-review set:
 * a validator that accepts an empty human file certifies no review completeness. Briefs 5e/5g must
 * supply a complete validator before this lock can support a final verdict.
 */
record LockRecord(int schemaVersion, Kind kind, List<String> workflows, List<Finding> findings,
                  String armKeySha256, List<PacketDigest> packetFiles, String lockedAtUtc, String sealSha256) {
    enum Kind { PRIMARY, RESERVE }
    record Finding(String name, String sha256) {}
    record PacketDigest(String name, String sha256) {}
    @FunctionalInterface interface FindingsValidator { void validate(List<Path> findingsFiles); }
    static final class InvalidLock extends RuntimeException {
        InvalidLock(String message) { super(message); }
        InvalidLock(String message, Throwable cause) { super(message, cause); }
    }
    static final class LockBroken extends RuntimeException {
        LockBroken(String message) { super(message); }
        LockBroken(String message, Throwable cause) { super(message, cause); }
    }
    private static final Set<String> FIELDS = Set.of("schemaVersion", "kind", "workflows", "findings",
            "armKeySha256", "packetFiles", "lockedAtUtc", "sealSha256");
    private static final Set<String> DIGEST_FIELDS = Set.of("name", "sha256");

    static LockRecord create(Path lockFile, Kind kind, Set<String> workflows, List<Path> findingsFiles,
                             ArmKeySeal.Seal seal, Path sealFile, FindingsValidator validator, String lockedAtUtc) {
        if (validator == null) throw new InvalidLock("Validator required");
        // Validation is deliberately first: even a malformed request cannot leave a partial lock.
        try { validator.validate(findingsFiles); }
        catch (Exception exception) { throw new InvalidLock("Findings validation failed", exception); }
        try {
            if (kind == null || workflows == null || workflows.isEmpty() || seal == null || sealFile == null
                    || findingsFiles == null || findingsFiles.isEmpty() || lockFile == null)
                throw new InvalidLock("Incomplete lock inputs");
            if (workflows.stream().anyMatch(name -> name == null || !name.matches("[A-Za-z0-9_-]+"))
                    || !seal.workflowStems().containsAll(workflows))
                throw new InvalidLock("Invalid workflow stem");
            if (IntegritySupport.utc(lockedAtUtc).isBefore(IntegritySupport.utc(seal.sealedAtUtc())))
                throw new InvalidLock("Lock predates seal");
            String sealHash = IntegritySupport.detachedHash(sealFile);
            if (!ArmKeySeal.load(sealFile).equals(seal)) throw new InvalidLock("Seal argument differs from file");
            Path directory = findingsFiles.getFirst().toAbsolutePath().normalize().getParent();
            Set<String> names = new HashSet<>();
            List<Finding> findings = new ArrayList<>();
            for (Path path : findingsFiles) {
                if (path == null || !path.toAbsolutePath().normalize().getParent().equals(directory)
                        || !names.add(path.getFileName().toString()) || !Files.isRegularFile(path))
                    throw new InvalidLock("Findings must be distinct files in one directory");
                findings.add(new Finding(path.getFileName().toString(), FreezeRecord.sha256(path)));
            }
            findings.sort(Comparator.comparing(Finding::name));
            List<PacketDigest> packets = seal.packetFiles().stream()
                    .map(packet -> new PacketDigest(packet.name(), packet.sha256())).toList();
            LockRecord result = new LockRecord(1, kind, workflows.stream().sorted().toList(),
                    List.copyOf(findings), seal.armKeySha256(), packets, lockedAtUtc, sealHash);
            IntegritySupport.create(lockFile, IntegritySupport.JSON.writeValueAsBytes(result));
            return result;
        } catch (InvalidLock exception) { throw exception; }
        catch (Exception exception) { throw new InvalidLock("Cannot create lock", exception); }
    }
    static LockRecord load(Path lockFile) {
        try {
            IntegritySupport.detachedHash(lockFile);
            JsonNode node = IntegritySupport.JSON.readTree(Files.readAllBytes(lockFile));
            IntegritySupport.fields(node, FIELDS);
            if (!node.path("schemaVersion").isInt() || node.path("schemaVersion").asInt() != 1
                    || !node.path("workflows").isArray() || !node.path("findings").isArray()
                    || !node.path("packetFiles").isArray()) throw new IOException("Invalid lock structure");
            Kind kind = Kind.valueOf(IntegritySupport.string(node, "kind"));
            List<String> workflows = new ArrayList<>();
            for (JsonNode value : node.path("workflows")) {
                if (!value.isTextual() || !value.asText().matches("[A-Za-z0-9_-]+"))
                    throw new IOException("Invalid workflow");
                workflows.add(value.asText());
            }
            if (workflows.isEmpty() || !workflows.equals(workflows.stream().distinct().sorted().toList()))
                throw new IOException("Noncanonical workflows");
            List<Finding> findings = new ArrayList<>();
            for (JsonNode value : node.path("findings")) {
                IntegritySupport.fields(value, DIGEST_FIELDS);
                String name = IntegritySupport.string(value, "name");
                if (!name.equals(Path.of(name).getFileName().toString())) throw new IOException("Invalid finding name");
                findings.add(new Finding(name, IntegritySupport.hash(value, "sha256")));
            }
            List<PacketDigest> packets = new ArrayList<>();
            for (JsonNode value : node.path("packetFiles")) {
                IntegritySupport.fields(value, DIGEST_FIELDS);
                String name = IntegritySupport.string(value, "name");
                if (!name.endsWith(".jsonl") || !name.equals(Path.of(name).getFileName().toString()))
                    throw new IOException("Invalid packet name");
                packets.add(new PacketDigest(name, IntegritySupport.hash(value, "sha256")));
            }
            if (findings.isEmpty() || packets.isEmpty()
                    || !findings.equals(findings.stream().distinct().sorted(Comparator.comparing(Finding::name)).toList())
                    || !packets.equals(packets.stream().distinct().sorted(Comparator.comparing(PacketDigest::name)).toList()))
                throw new IOException("Noncanonical lock files");
            String utc = IntegritySupport.string(node, "lockedAtUtc");
            IntegritySupport.utc(utc);
            return new LockRecord(1, kind, List.copyOf(workflows), List.copyOf(findings),
                    IntegritySupport.hash(node, "armKeySha256"), List.copyOf(packets), utc,
                    IntegritySupport.hash(node, "sealSha256"));
        } catch (Exception exception) { throw new LockBroken("Cannot verify lock", exception); }
    }
    static void verifyFindingsUnchanged(LockRecord lock, Path directory) {
        try {
            Set<String> names = new HashSet<>();
            for (Finding finding : lock.findings()) {
                Path file = directory.resolve(finding.name());
                if (!Files.isRegularFile(file) || !FreezeRecord.sha256(file).equals(finding.sha256()))
                    throw new LockBroken("Findings changed: " + finding.name());
                names.add(finding.name());
            }
            try (Stream<Path> paths = Files.list(directory)) {
                if (paths.filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                        .anyMatch(path -> !names.contains(path.getFileName().toString())))
                    throw new LockBroken("Extra findings JSONL file");
            }
        } catch (LockBroken exception) { throw exception; }
        catch (Exception exception) { throw new LockBroken("Cannot verify findings", exception); }
    }
}
