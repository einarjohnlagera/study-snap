package com.studysnap.backend.benchmark;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/** Counter-mode SHA-256 stream with unbiased bounded draws. */
final class SeedStream {
    static final class UndeclaredStream extends RuntimeException {
        UndeclaredStream(String message) { super(message); }
    }
    private final byte[] state;
    private int counter;
    private byte[] block = new byte[0];
    private int offset;
    private SeedStream(byte[] state) { this.state = state; }
    static SeedStream of(SeedStreamRegistry registry, String workflow, String purpose, int round) {
        if (registry == null || !registry.isDeclared(workflow, purpose, round))
            throw new UndeclaredStream("Undeclared stream " + workflow + "|" + purpose + "|" + round);
        String delimiter = SeedStreamRegistry.DELIMITER;
        return new SeedStream(hash((registry.seed() + delimiter + workflow + delimiter + purpose + delimiter + round)
                .getBytes(StandardCharsets.UTF_8)));
    }
    String firstBlockSha256() {
        return java.util.HexFormat.of().formatHex(block(0));
    }
    private byte[] block(int index) {
        ByteBuffer input = ByteBuffer.allocate(state.length + Integer.BYTES);
        input.put(state).putInt(index);
        return hash(input.array());
    }
    private int word() {
        if (offset == block.length) {
            block = block(counter++);
            offset = 0;
        }
        int value = ByteBuffer.wrap(block, offset, Integer.BYTES).getInt();
        offset += Integer.BYTES;
        return value;
    }
    int nextInt(int bound) {
        if (bound < 1) throw new InvalidBound("Bound must be positive");
        long range = 1L << 32;
        long limit = range - range % bound;
        long value;
        do { value = Integer.toUnsignedLong(word()); } while (value >= limit);
        return (int) (value % bound);
    }
    <T> void shuffle(List<T> values) {
        for (int i = values.size() - 1; i > 0; i--)
            java.util.Collections.swap(values, i, nextInt(i + 1));
    }
    private static byte[] hash(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    static final class InvalidBound extends RuntimeException {
        InvalidBound(String message) { super(message); }
    }
}
