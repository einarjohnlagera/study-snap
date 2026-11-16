package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Byte seal for an arm key and its complete packet directory; it never parses key rows. */
final class ArmKeySeal {
    static final class InvalidSeal extends RuntimeException {
        InvalidSeal(String message) { super(message); }
        InvalidSeal(String message, Throwable cause) { super(message, cause); }
    }
    static final class SealBroken extends RuntimeException {
        SealBroken(String message) { super(message); }
        SealBroken(String message, Throwable cause) { super(message, cause); }
    }
    record PacketFile(String name, String sha256, long lines) {}
    record Seal(String armKeySha256, String armKeyFileName, long lineCount,
                List<PacketFile> packetFiles, String sealedAtUtc) {
        Set<String> workflowStems() {
            return packetFiles.stream().filter(packet -> packet.lines() > 0)
                    .map(packet -> packet.name().substring(0, packet.name().length() - 6))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }
    private static final Set<String> FIELDS = Set.of("armKeySha256", "armKeyFileName", "lineCount", "packetFiles", "sealedAtUtc");
    private static final Set<String> PACKET_FIELDS = Set.of("name", "sha256", "lines");
    private ArmKeySeal() {}

    static Seal seal(Path armKey, Path packetDirectory, Path sealFile, String sealedAtUtc) {
        try {
            IntegritySupport.utc(sealedAtUtc);
            if (armKey == null || packetDirectory == null || sealFile == null
                    || armKey.toRealPath().startsWith(packetDirectory.toRealPath())
                    || Files.exists(sealFile) || Files.exists(IntegritySupport.detached(sealFile)))
                throw new InvalidSeal("Key must be separate and seal must be new");
            byte[] key = Files.readAllBytes(armKey);
            long keyLines = IntegritySupport.nonBlankLines(key);
            if (key.length == 0 || keyLines == 0) throw new InvalidSeal("Empty arm key");
            List<PacketFile> packets = new ArrayList<>();
            for (Path path : packetPaths(packetDirectory)) {
                byte[] bytes = Files.readAllBytes(path);
                packets.add(new PacketFile(path.getFileName().toString(), FreezeRecord.sha256(bytes),
                        IntegritySupport.nonBlankLines(bytes)));
            }
            if (packets.stream().noneMatch(packet -> packet.lines() > 0))
                throw new InvalidSeal("No nonempty packet file");
            Seal result = new Seal(FreezeRecord.sha256(key), armKey.getFileName().toString(), keyLines,
                    List.copyOf(packets), sealedAtUtc);
            IntegritySupport.create(sealFile, IntegritySupport.JSON.writeValueAsBytes(result));
            return result;
        } catch (InvalidSeal exception) { throw exception; }
        catch (Exception exception) { throw new InvalidSeal("Cannot seal arm key", exception); }
    }
    static Seal load(Path sealFile) {
        try {
            IntegritySupport.detachedHash(sealFile);
            JsonNode node = IntegritySupport.JSON.readTree(Files.readAllBytes(sealFile));
            IntegritySupport.fields(node, FIELDS);
            String keyHash = IntegritySupport.hash(node, "armKeySha256");
            String fileName = IntegritySupport.string(node, "armKeyFileName");
            if (!fileName.equals(Path.of(fileName).getFileName().toString()) || !node.path("lineCount").isIntegralNumber()
                    || node.path("lineCount").asLong() < 1 || !node.path("packetFiles").isArray())
                throw new IOException("Invalid seal metadata");
            List<PacketFile> packets = new ArrayList<>();
            for (JsonNode packet : node.path("packetFiles")) {
                IntegritySupport.fields(packet, PACKET_FIELDS);
                String name = IntegritySupport.string(packet, "name");
                if (!name.endsWith(".jsonl") || !name.equals(Path.of(name).getFileName().toString())
                        || !packet.path("lines").isIntegralNumber() || packet.path("lines").asLong() < 0)
                    throw new IOException("Invalid packet metadata");
                packets.add(new PacketFile(name, IntegritySupport.hash(packet, "sha256"), packet.path("lines").asLong()));
            }
            if (packets.isEmpty() || packets.stream().noneMatch(packet -> packet.lines() > 0)
                    || !packets.equals(packets.stream().sorted(Comparator.comparing(PacketFile::name)).toList())
                    || packets.stream().map(PacketFile::name).distinct().count() != packets.size())
                throw new IOException("Invalid packet list");
            String utc = IntegritySupport.string(node, "sealedAtUtc");
            IntegritySupport.utc(utc);
            return new Seal(keyHash, fileName, node.path("lineCount").asLong(), List.copyOf(packets), utc);
        } catch (Exception exception) { throw new SealBroken("Cannot verify seal", exception); }
    }
    static void verifyKeyBytes(Path armKey, Seal seal) {
        try {
            if (seal == null || !armKey.getFileName().toString().equals(seal.armKeyFileName())
                    || !FreezeRecord.sha256(armKey).equals(seal.armKeySha256()))
                throw new SealBroken("Arm key differs from seal");
        } catch (SealBroken exception) { throw exception; }
        catch (Exception exception) { throw new SealBroken("Cannot verify arm key", exception); }
    }
    static void verifyPackets(Path packetDirectory, Seal seal) {
        try {
            List<Path> paths = packetPaths(packetDirectory);
            if (!paths.stream().map(path -> path.getFileName().toString()).toList()
                    .equals(seal.packetFiles().stream().map(PacketFile::name).toList()))
                throw new SealBroken("Packet file set changed");
            for (int index = 0; index < paths.size(); index++) {
                byte[] bytes = Files.readAllBytes(paths.get(index));
                PacketFile packet = seal.packetFiles().get(index);
                if (!FreezeRecord.sha256(bytes).equals(packet.sha256())
                        || IntegritySupport.nonBlankLines(bytes) != packet.lines())
                    throw new SealBroken("Packet bytes changed: " + packet.name());
            }
        } catch (SealBroken exception) { throw exception; }
        catch (Exception exception) { throw new SealBroken("Cannot verify packets", exception); }
    }
    private static List<Path> packetPaths(Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
    }
}
