package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Seal-verified post-unlock packet/key join. The opened key's hash is not exposed by OpenedKey. */
final class BlindAuditSupport {
    static final class InvalidAudit extends RuntimeException {
        InvalidAudit(String message) { super(message); }
        InvalidAudit(String message, Throwable cause) { super(message, cause); }
    }
    record Pair(String id, String workflow, boolean classB, boolean reserve, String candidatePlacement,
                JsonNode candidate, JsonNode baseline) {}
    record Binomial(int n, int candidateInA, Rational p, boolean undefined) {}
    private BlindAuditSupport() {}
    static void alpha(Rational value) {
        if (value == null || value.num().signum() <= 0 || value.num().compareTo(value.den()) >= 0)
            throw new InvalidAudit("Explicit alpha in (0,1) required");
    }
    static Binomial binomial(int candidateInA, int n) {
        if (n < 0 || candidateInA < 0 || candidateInA > n) throw new InvalidAudit("Invalid binomial counts");
        if (n == 0) return new Binomial(0, 0, Rational.of(1, 1), true);
        int tail = Math.min(candidateInA, n - candidateInA);
        BigInteger sum = BigInteger.ZERO;
        BigInteger term = BigInteger.ONE;
        for (int i = 0; i <= tail; i++) {
            sum = sum.add(term);
            if (i < tail) term = term.multiply(BigInteger.valueOf(n - i)).divide(BigInteger.valueOf(i + 1));
        }
        BigInteger denominator = BigInteger.ONE.shiftLeft(n);
        BigInteger twice = sum.shiftLeft(1);
        BigInteger numerator = twice.min(denominator);
        BigInteger gcd = numerator.gcd(denominator);
        return new Binomial(n, candidateInA,
                new Rational(numerator.divide(gcd), denominator.divide(gcd)), false);
    }
    static boolean atMost(Rational p, Rational alpha) {
        return p.num().multiply(alpha.den()).compareTo(alpha.num().multiply(p.den())) <= 0;
    }
    static List<Pair> pairs(Path packets, Path sealFile, String candidateArm,
                            ArmKeyAccess.OpenedKey opened, Rational alpha) {
        try {
            alpha(alpha);
            if (opened == null) throw new InvalidAudit("Opened key required");
            ArmKeySeal.Seal seal = ArmKeySeal.load(sealFile);
            ArmKeySeal.verifyPackets(packets, seal);
            PairMap map = PairMap.fromPackets(packets);
            Map<String, ArmKeyAccess.KeyRow> rows = new HashMap<>();
            for (ArmKeyAccess.KeyRow row : opened.rows())
                if (rows.putIfAbsent(row.itemId(), row) != null) throw new InvalidAudit("Duplicate opened item");
            if (!rows.keySet().equals(map.blindItemIds()))
                throw new InvalidAudit("Opened item set differs from sealed packets");
            List<Pair> result = new ArrayList<>();
            for (ArmKeySeal.PacketFile packetFile : seal.packetFiles()) {
                String workflow = packetFile.name().substring(0, packetFile.name().length() - 6);
                List<String> lines = Files.readAllLines(packets.resolve(packetFile.name()));
                for (int i = 0; i < lines.size(); i++) {
                    JsonNode packet = IntegritySupport.JSON.readTree(lines.get(i));
                    String pairId = workflow + "#" + (i + 1);
                    JsonNode outputs = packet.path("outputs");
                    if (!outputs.isArray() || outputs.size() != 2) throw new InvalidAudit("Invalid pair outputs");
                    boolean classB = outputs.get(0).has("itemId");
                    if (!classB && (candidateArm == null || candidateArm.isBlank()))
                        throw new InvalidAudit("Class-A candidate arm required");
                    String expectedCandidate = classB ? "CANDIDATE" : candidateArm;
                    Map<String, String> arms = new HashMap<>();
                    Map<String, JsonNode> placed = new HashMap<>();
                    String commonCase = null;
                    Boolean reserve = null;
                    for (JsonNode output : outputs) {
                        String placement = output.path("placement").asText();
                        List<String> ids = map.itemsOf(pairId, placement);
                        if (!Set.of("A", "B").contains(placement) || ids.isEmpty()
                                || placed.putIfAbsent(placement, output) != null)
                            throw new InvalidAudit("Invalid placement");
                        String arm = null;
                        for (String id : ids) {
                            ArmKeyAccess.KeyRow row = rows.get(id);
                            if (row == null || !workflow.equals(row.workflow()))
                                throw new InvalidAudit("Key workflow differs from packet stem");
                            if (arm == null) arm = row.arm();
                            else if (!arm.equals(row.arm())) throw new InvalidAudit("Mixed arms in class-A output");
                            if (commonCase == null) commonCase = row.caseId();
                            else if (row.caseId() != null && !commonCase.equals(row.caseId()))
                                throw new InvalidAudit("Pair case ids differ");
                            if (classB) {
                                if (row.reserve() == null || reserve != null && !reserve.equals(row.reserve()))
                                    throw new InvalidAudit("Reserve flag differs");
                                reserve = row.reserve();
                            } else if (row.reserve() != null) throw new InvalidAudit("Class-A key shape differs");
                        }
                        arms.put(placement, arm);
                    }
                    if (!placed.keySet().equals(Set.of("A", "B"))
                            || arms.get("A").equals(arms.get("B"))
                            || !Set.of(arms.get("A"), arms.get("B")).contains(expectedCandidate)
                            || classB && !Set.of(arms.get("A"), arms.get("B")).equals(Set.of("CANDIDATE", "BASELINE")))
                        throw new InvalidAudit("Pair lacks candidate and one other arm");
                    if (classB && (packet.path("fixture").path("reserve").asBoolean() != reserve
                            || !workflow.equals(packet.path("fixture").path("workflow").asText())))
                        throw new InvalidAudit("Class-B fixture and key differ");
                    String candidatePlacement = expectedCandidate.equals(arms.get("A")) ? "A" : "B";
                    result.add(new Pair(pairId, workflow, classB, classB && reserve,
                            candidatePlacement, placed.get(candidatePlacement),
                            placed.get("A".equals(candidatePlacement) ? "B" : "A")));
                }
            }
            if (result.isEmpty()) throw new InvalidAudit("No pairs");
            return List.copyOf(result);
        } catch (InvalidAudit exception) { throw exception; }
        catch (Exception exception) { throw new InvalidAudit("Cannot audit sealed packets", exception); }
    }
}
