package com.studysnap.backend.benchmark;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** The sealed order and first-arm rule; the bit is deliberately independent of shuffle state. */
final class PlacementPlan {
    private static final String HMAC = "HmacSHA256";
    record Placed(String caseId, boolean firstArmInA) {}
    private PlacementPlan() {}

    static List<String> order(List<String> caseIds, long seed, String groupKey) {
        if (caseIds == null || groupKey == null) throw new InvalidPlacementSeal();
        return orderWithHash(caseIds, seed, groupKey.hashCode());
    }

    static List<String> orderWithHash(List<String> caseIds, long seed, int groupHash) {
        if (caseIds == null || caseIds.stream().anyMatch(id -> id == null || id.isBlank())
                || caseIds.stream().distinct().count() != caseIds.size()) throw new InvalidPlacementSeal();
        List<String> ordered = new ArrayList<>(caseIds);
        ordered.sort(String::compareTo);
        Collections.shuffle(ordered, new Random(seed ^ groupHash));
        return List.copyOf(ordered);
    }

    static List<Placed> layout(List<String> caseIds, PlacementSecret secret, String exportId, String groupKey) {
        if (secret == null || exportId == null) throw new InvalidPlacementSeal();
        return order(caseIds, secret.seedLong(), groupKey).stream()
                .map(id -> new Placed(id, firstArmInA(secret, exportId, groupKey, id))).toList();
    }

    static boolean firstArmInA(PlacementSecret secret, String exportId, String groupKey, String caseId) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret.raw(), HMAC));
            mac.update(exportId.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            mac.update(groupKey.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            byte[] digest = mac.doFinal(caseId.getBytes(StandardCharsets.UTF_8));
            return (digest[31] & 1) == 1;
        } catch (Exception exception) { throw new InvalidPlacementSeal(); }
    }
}
