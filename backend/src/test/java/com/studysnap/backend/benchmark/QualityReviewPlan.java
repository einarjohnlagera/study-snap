package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Private sampling order; it depends only on the blind key, never on scores. */
public final class QualityReviewPlan {
    @Test void planWhenRequested() throws Exception {
        assumeTrue(Boolean.getBoolean("llm.benchmark.reviewPlan"));
        Path blind = Path.of(required("llm.benchmark.blindOutDir"));
        plan(blind.resolve("blind-key.jsonl"), Path.of(required("llm.benchmark.reviewOutDir")),
                Long.getLong("llm.benchmark.reviewSeed",176L));
    }
    private static String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
    static boolean choice(JsonNode key) { return QualityScoreFiles.choice(key); }
    static void plan(Path keyFile, Path out, long seed) throws Exception {
        Map<String,JsonNode> keys = QualityScoreFiles.keyed(keyFile);
        Map<String,Map<String,List<JsonNode>>> byArmStratum = new TreeMap<>();
        for (JsonNode key : keys.values()) if (choice(key)) {
            String arm = QualityScoreFiles.text(key,"arm");
            String stratum = QualityScoreFiles.text(key,"noteRef") + "/" + QualityScoreFiles.text(key,"questionFormat");
            byArmStratum.computeIfAbsent(arm,ignored -> new TreeMap<>())
                    .computeIfAbsent(stratum,ignored -> new ArrayList<>()).add(key);
        }
        List<Map<String,Object>> order = new ArrayList<>();
        Map<String,Integer> counts = new TreeMap<>(), sampled = new TreeMap<>();
        for (var armGroup : byArmStratum.entrySet()) {
            Map<String,List<JsonNode>> strata=armGroup.getValue();
            int total=strata.values().stream().mapToInt(List::size).sum();
            int sampleCount=(int)Math.ceil(total*QualityGateParameters.REVIEW_SAMPLE_FRACTION);
            Map<String,Integer> used=new TreeMap<>();
            Map<String,Integer> sampleQuota=new TreeMap<>();
            int remaining=sampleCount;
            for (var stratum:strata.entrySet()) {
                stratum.getValue().sort(Comparator.comparing(k -> QualityScoreFiles.text(k,"id")));
                Collections.shuffle(stratum.getValue(),new Random(seed ^ (armGroup.getKey()+"/"+stratum.getKey()).hashCode()));
                used.put(stratum.getKey(),0);
                int floor=(int)((long)sampleCount*stratum.getValue().size()/total);
                sampleQuota.put(stratum.getKey(),floor);
                remaining-=floor;
            }
            List<String> remainders=new ArrayList<>(strata.keySet());
            remainders.sort((left,right) -> {
                long l=(long)sampleCount*strata.get(left).size()%total;
                long r=(long)sampleCount*strata.get(right).size()%total;
                int compare=Long.compare(r,l);
                return compare!=0?compare:left.compareTo(right);
            });
            for (int i=0;i<remaining;i++) sampleQuota.merge(remainders.get(i),1,Integer::sum);
            // Largest-remainder quotas fix the initial sample; largest deficit interleaves every prefix.
            for (int position=1;position<=total;position++) {
                String chosen=null;
                double largestDeficit=Double.NEGATIVE_INFINITY;
                for (String stratum:strata.keySet()) {
                    int limit=position<=sampleCount?sampleQuota.get(stratum):strata.get(stratum).size();
                    if (used.get(stratum)>=limit) continue;
                    double deficit=(double)position*strata.get(stratum).size()/total-used.get(stratum);
                    if (deficit>largestDeficit) { chosen=stratum; largestDeficit=deficit; }
                }
                if (chosen==null) throw new IllegalStateException("No stratum available at prefix "+position);
                JsonNode key=strata.get(chosen).get(used.merge(chosen,1,Integer::sum)-1);
                String arm = QualityScoreFiles.text(key,"arm"), id = QualityScoreFiles.text(key,"id");
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("id",id); row.put("arm",arm); row.put("questionFormat",QualityScoreFiles.text(key,"questionFormat"));
                row.put("noteRef",QualityScoreFiles.text(key,"noteRef"));
                row.put("noteLevel",QualityScoreFiles.text(key,"noteLevel")); row.put("sampled",position<=sampleCount);
                order.add(row); counts.merge(arm,1,Integer::sum);
                if (position<=sampleCount) sampled.merge(arm,1,Integer::sum);
            }
        }
        Files.createDirectories(out);
        Path orderFile = out.resolve("review-order.jsonl");
        Path manifestFile=out.resolve("review-manifest.json");
        if (Files.exists(manifestFile) && QualityScoreFiles.JSON.readTree(manifestFile.toFile()).path("seed").asLong()!=seed)
            throw new IllegalStateException("Frozen review seed would change: " + manifestFile);
        if (Files.exists(orderFile)) {
            List<String> existing=Files.readAllLines(orderFile);
            List<String> proposed=new ArrayList<>();
            for (Map<String,Object> row:order) proposed.add(QualityScoreFiles.JSON.writeValueAsString(row));
            if (!existing.equals(proposed)) throw new IllegalStateException("Frozen review order would change: " + orderFile);
        }
        QualityScoreFiles.write(orderFile,order);
        String sha = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(orderFile)));
        QualityScoreFiles.JSON.writerWithDefaultPrettyPrinter().writeValue(manifestFile.toFile(),
                Map.of("seed",seed,"countsPerArm",counts,"sampledPerArm",sampled,"sha256",sha));
    }
    static void verify(Path orderFile, Path manifest) throws Exception {
        JsonNode row = QualityScoreFiles.JSON.readTree(manifest.toFile());
        String sha = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(orderFile)));
        if (!sha.equals(QualityScoreFiles.text(row,"sha256"))) throw new IllegalArgumentException("Review-order SHA-256 mismatch");
    }
}
