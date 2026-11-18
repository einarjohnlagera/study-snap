package com.studysnap.backend.benchmark;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

/** Independent of SeedStreamRegistry's spot-check and expansion purposes. */
final class PlacementSecret {
    private static final String COMMIT_DOMAIN = "NOTELIB-PLACEMENT-COMMIT-V1";
    private static final String SEED_DOMAIN = "NOTELIB-PLACEMENT-SEED-V1";
    private final byte[] bytes;

    PlacementSecret(byte[] bytes) {
        if (bytes == null || bytes.length != 32) throw new InvalidPlacementSeal();
        this.bytes = bytes.clone();
    }

    static String create(Path secretFile) {
        try {
            if (secretFile == null) throw new InvalidPlacementSeal();
            Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize()).toRealPath();
            Path absolute = secretFile.toAbsolutePath().normalize();
            Path ancestor = absolute;
            while (!Files.exists(ancestor)) ancestor = ancestor.getParent();
            Path resolved = ancestor.toRealPath().resolve(ancestor.relativize(absolute)).normalize();
            if (resolved.startsWith(repo)) throw new InvalidPlacementSeal();
            byte[] secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            boolean created = false;
            try {
                try {
                    Files.createFile(secretFile, java.nio.file.attribute.PosixFilePermissions
                            .asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
                } catch (UnsupportedOperationException ignored) {
                    Files.createFile(secretFile);
                }
                created = true;
                Files.write(secretFile, secret, StandardOpenOption.WRITE);
                return new PlacementSecret(secret).commitment();
            } catch (Exception exception) {
                if (created) Files.deleteIfExists(secretFile);
                throw new InvalidPlacementSeal();
            } finally { Arrays.fill(secret, (byte) 0); }
        } catch (Exception exception) { throw new InvalidPlacementSeal(); }
    }

    static PlacementSecret load(Path secretFile) {
        try { return new PlacementSecret(Files.readAllBytes(secretFile)); }
        catch (Exception exception) { throw new InvalidPlacementSeal(); }
    }

    static String operatorLine(String exportId, String commitment) {
        if (exportId == null || exportId.isBlank() || !IntegritySupport.digest(commitment))
            throw new InvalidPlacementSeal();
        return exportId + " " + commitment;
    }

    String commitment() { return HexFormat.of().formatHex(digest(COMMIT_DOMAIN)); }
    long seedLong() { return ByteBuffer.wrap(digest(SEED_DOMAIN)).getLong(); }
    String hex() { return HexFormat.of().formatHex(bytes); }
    byte[] raw() { return bytes.clone(); }

    private byte[] digest(String domain) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update(domain.getBytes(StandardCharsets.UTF_8));
            hash.update((byte) 0);
            return hash.digest(bytes);
        } catch (Exception exception) { throw new InvalidPlacementSeal(); }
    }

    @Override public String toString() { return "PlacementSecret[REDACTED]"; }
}
