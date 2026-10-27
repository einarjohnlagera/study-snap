package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Builds scorer-facing batches from a frozen order and primary scores. */
public final class QualityReviewBatches {
    @Test void batchesWhenRequested() throws Exception {
        assumeTrue(Boolean.getBoolean("llm.benchmark.reviewBatches"));
        Path blind=Path.of(required("llm.benchmark.blindOutDir"));
        Path plan=Path.of(required("llm.benchmark.reviewOutDir"));
        batches(blind,plan,Path.of(required("llm.benchmark.scoresCodex")),
                Path.of(required("llm.benchmark.batchesOutDir")));
    }
    private static String required(String name) {
        String value=System.getProperty(name);
        if (value==null || value.isBlank()) throw new IllegalArgumentException(name+" is required");
        return value;
    }
    static void batches(Path blind, Path plan, Path scoresPath, Path out) throws Exception {
        Path orderPath=plan.resolve("review-order.jsonl");
        QualityReviewPlan.verify(orderPath,plan.resolve("review-manifest.json"));
        Map<String,JsonNode> keys=QualityScoreFiles.keyed(blind.resolve("blind-key.jsonl"));
        Map<String,JsonNode> items=new LinkedHashMap<>();
        JsonNode blindManifest=QualityScoreFiles.JSON.readTree(blind.resolve("blind-manifest.json").toFile());
        for (JsonNode chunk : blindManifest.path("chunks")) items.putAll(QualityScoreFiles.keyed(blind.resolve(chunk.asText())));
        Map<String,JsonNode> scores=QualityScoreFiles.keyed(scoresPath);
        if (!scores.keySet().equals(keys.keySet())) throw new IllegalArgumentException("Primary scores must cover every blind item");
        for (var entry:scores.entrySet()) {
            QualityScoreFiles.validate(entry.getValue(),QualityScoreFiles.choice(keys.get(entry.getKey())));
            if (!"PRIMARY".equals(QualityScoreFiles.text(entry.getValue(),"round")))
                throw new IllegalArgumentException("Expected PRIMARY score for " + entry.getKey());
        }
        Map<String,String> reasons=selection(QualityScoreFiles.lines(orderPath),keys,scores);
        Map<String,Integer> unflaggedPerArm=new TreeMap<>();
        for (var entry:reasons.entrySet()) if (!"FLAGGED".equals(entry.getValue()))
            unflaggedPerArm.merge(QualityScoreFiles.text(keys.get(entry.getKey()),"arm"),1,Integer::sum);
        // Non-choice critical flags also receive independent review; only choice items enter G4 sampling.
        for (var entry:keys.entrySet()) if (!QualityScoreFiles.choice(entry.getValue())
                && scores.get(entry.getKey()).path("critical").asBoolean())
            reasons.put(entry.getKey(),"FLAGGED");
        // Shuffle all categories together; no position encodes the selection reason.
        long seed=QualityScoreFiles.JSON.readTree(plan.resolve("review-manifest.json").toFile()).path("seed").asLong();
        List<String> ids=new ArrayList<>(reasons.keySet());
        Collections.shuffle(ids,new Random(seed ^ 0x52379b71L));
        Files.createDirectories(out);
        List<String> batchNames=new ArrayList<>();
        for (int offset=0;offset<ids.size();offset+=QualityGateParameters.REVIEW_BATCH_SIZE) {
            String name="opus-batch-%03d.jsonl".formatted(batchNames.size()+1);
            List<JsonNode> rows=new ArrayList<>();
            for (String id : ids.subList(offset,Math.min(offset+QualityGateParameters.REVIEW_BATCH_SIZE,ids.size()))) {
                JsonNode item=items.get(id);
                if (item==null || item.size()!=7) throw new IllegalArgumentException("Missing or non-blind item " + id);
                rows.add(item);
            }
            QualityScoreFiles.write(out.resolve(name),rows); batchNames.add(name);
        }
        Map<String,Object> manifest=new LinkedHashMap<>();
        manifest.put("reviewOrderSha256",QualityScoreFiles.text(QualityScoreFiles.JSON.readTree(plan.resolve("review-manifest.json").toFile()),"sha256"));
        manifest.put("batchFiles",batchNames); manifest.put("reasonById",reasons);
        manifest.put("unflaggedPerArm",unflaggedPerArm);
        QualityScoreFiles.JSON.writerWithDefaultPrettyPrinter().writeValue(out.resolve("opus-batch-manifest.json").toFile(),manifest);
    }
    static Map<String,String> selection(List<JsonNode> order,Map<String,JsonNode> keys,Map<String,JsonNode> scores) {
        Map<String,List<String>> eligible=new TreeMap<>();
        Map<String,Integer> sampled=new TreeMap<>();
        Map<String,String> reasons=new LinkedHashMap<>();
        for (JsonNode row:order) {
            String id=QualityScoreFiles.text(row,"id"),arm=QualityScoreFiles.text(row,"arm");
            JsonNode key=keys.get(id),score=scores.get(id);
            if (key==null || score==null || !QualityScoreFiles.choice(key))
                throw new IllegalArgumentException("Invalid review order id "+id);
            QualityScoreFiles.validate(score,true);
            if (!"PRIMARY".equals(QualityScoreFiles.text(score,"round"))) throw new IllegalArgumentException("Expected PRIMARY score");
            if (row.path("sampled").asBoolean()) sampled.merge(arm,1,Integer::sum);
            if (score.path("critical").asBoolean() || score.path("seriousGiveaway").asBoolean()) reasons.put(id,"FLAGGED");
            else eligible.computeIfAbsent(arm,ignored->new ArrayList<>()).add(id);
        }
        for (var arm:eligible.entrySet()) {
            int count=Math.max(QualityGateParameters.UNFLAGGED_REVIEW_FLOOR,sampled.getOrDefault(arm.getKey(),0));
            count=Math.min(count,arm.getValue().size());
            for (int i=0;i<count;i++) reasons.put(arm.getValue().get(i),i<sampled.getOrDefault(arm.getKey(),0)?"SAMPLED":"TOPPED_UP");
        }
        return reasons;
    }
}
