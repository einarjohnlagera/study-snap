package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QualityReviewBatchesTest {
    @TempDir Path temp;

    @Test void allValidFilesMatchLegacyBytes() throws Exception {
        Fixture fixture=new Fixture();
        Map<String,JsonNode> keys=QualityScoreFiles.keyed(fixture.key);
        Map<String,JsonNode> scores=QualityScoreFiles.keyed(fixture.primary);
        Map<String,String> reasons=QualityReviewBatches.selection(
                QualityScoreFiles.lines(fixture.plan.resolve("review-order.jsonl")),keys,scores);
        Map<String,Integer> unflagged=new TreeMap<>();
        for (var entry:reasons.entrySet()) if (!"FLAGGED".equals(entry.getValue()))
            unflagged.merge(QualityScoreFiles.text(keys.get(entry.getKey()),"arm"),1,Integer::sum);
        List<String> ids=new ArrayList<>(reasons.keySet());
        Collections.shuffle(ids,new Random(176L ^ 0x52379b71L));
        List<String> names=new ArrayList<>();
        Path expected=Files.createDirectory(temp.resolve("expected"));
        Map<String,JsonNode> items=QualityScoreFiles.keyed(fixture.blind.resolve("blind-items-001.jsonl"));
        for (int offset=0;offset<ids.size();offset+=QualityGateParameters.REVIEW_BATCH_SIZE) {
            String name="opus-batch-%03d.jsonl".formatted(names.size()+1);
            List<JsonNode> rows=new ArrayList<>();
            for (String id:ids.subList(offset,Math.min(offset+QualityGateParameters.REVIEW_BATCH_SIZE,ids.size())))
                rows.add(items.get(id));
            QualityScoreFiles.write(expected.resolve(name),rows);
            names.add(name);
        }
        Map<String,Object> oldManifest=new LinkedHashMap<>();
        oldManifest.put("reviewOrderSha256",QualityScoreFiles.text(
                QualityScoreFiles.JSON.readTree(fixture.plan.resolve("review-manifest.json").toFile()),"sha256"));
        oldManifest.put("batchFiles",names); oldManifest.put("reasonById",reasons);
        oldManifest.put("unflaggedPerArm",unflagged);
        QualityScoreFiles.JSON.writerWithDefaultPrettyPrinter()
                .writeValue(expected.resolve("opus-batch-manifest.json").toFile(),oldManifest);

        Path actual=temp.resolve("actual");
        fixture.batches(actual);
        for (String name:names) assertThat(Files.readAllBytes(actual.resolve(name)))
                .isEqualTo(Files.readAllBytes(expected.resolve(name)));
        assertThat(Files.readAllBytes(actual.resolve("opus-batch-manifest.json")))
                .isEqualTo(Files.readAllBytes(expected.resolve("opus-batch-manifest.json")));
    }

    @Test void invalidChoiceIsFlaggedAtItsOrderPositionAndNeverEligible() throws Exception {
        Fixture fixture=new Fixture();
        List<JsonNode> order=QualityScoreFiles.lines(fixture.plan.resolve("review-order.jsonl"));
        String first=order.stream().filter(row->"A1".equals(row.path("arm").asText()))
                .findFirst().orElseThrow().path("id").asText();
        String middle=order.stream().filter(row->"A1".equals(row.path("arm").asText()))
                .skip(1).findFirst().orElseThrow().path("id").asText();
        String second=order.stream().filter(row->"A1".equals(row.path("arm").asText()))
                .skip(2).findFirst().orElseThrow().path("id").asText();
        fixture.mutate(first,row->{ row.putNull("distractorPlausibility"); row.putNull("seriousGiveaway"); row.putNull("giveawayPattern"); });
        fixture.mutate(middle,row->{ row.put("critical",true); row.put("criticalType","WRONG_KEY"); row.put("factuallyCorrect","NO"); });
        fixture.mutate(second,row->{ row.putNull("critical"); });
        Path out=temp.resolve("invalid"); fixture.batches(out);
        JsonNode manifest=QualityScoreFiles.JSON.readTree(out.resolve("opus-batch-manifest.json").toFile());
        assertThat(manifest.path("invalidPrimaryIds"))
                .isEqualTo(QualityScoreFiles.JSON.valueToTree(List.of(first,second)));
        assertThat(manifest.path("invalidPrimaryArms").toString()).isEqualTo("[\"A1\"]");
        assertThat(manifest.path("reasonById").path(first).asText()).isEqualTo("FLAGGED");
        assertThat(manifest.path("reasonById").path(second).asText()).isEqualTo("FLAGGED");
        assertThat(manifest.path("unflaggedPerArm").path("A1").asInt()).isEqualTo(60);
        List<String> reasonOrder=new ArrayList<>();
        manifest.path("reasonById").fieldNames().forEachRemaining(reasonOrder::add);
        List<String> flaggedInOrder=order.stream().filter(row->List.of(first,middle,second).contains(row.path("id").asText()))
                .map(row->row.path("id").asText()).toList();
        assertThat(reasonOrder.subList(0,3)).isEqualTo(flaggedInOrder);
        List<String> batchIds=new ArrayList<>();
        for (JsonNode name:manifest.path("batchFiles"))
            for (JsonNode item:QualityScoreFiles.lines(out.resolve(name.asText()))) batchIds.add(item.path("id").asText());
        assertThat(batchIds).contains(first,second);
        List<String> shuffled=new ArrayList<>(reasonOrder);
        Collections.shuffle(shuffled,new Random(176L ^ 0x52379b71L));
        assertThat(batchIds).isEqualTo(shuffled);
    }

    @Test void missingMismatchedAndNonPrimaryRowsStillAbort() throws Exception {
        Fixture fixture=new Fixture();
        List<JsonNode> original=QualityScoreFiles.lines(fixture.primary);
        QualityScoreFiles.write(fixture.primary,original.subList(1,original.size()));
        assertThatThrownBy(()->fixture.batches(temp.resolve("missing"))).hasMessageContaining("cover every blind item");
        List<JsonNode> changed=new ArrayList<>(original);
        ObjectNode wrong=((ObjectNode)changed.get(0)).deepCopy(); wrong.put("id","other"); changed.set(0,wrong);
        QualityScoreFiles.write(fixture.primary,changed);
        assertThatThrownBy(()->fixture.batches(temp.resolve("mismatch"))).hasMessageContaining("cover every blind item");
        ObjectNode round=((ObjectNode)original.get(0)).deepCopy(); round.put("round","INDEPENDENT_REVIEW");
        changed.set(0,round); QualityScoreFiles.write(fixture.primary,changed);
        assertThatThrownBy(()->fixture.batches(temp.resolve("round"))).hasMessageContaining("Expected PRIMARY score");
    }

    private final class Fixture {
        final Path blind=temp.resolve("blind"),plan=temp.resolve("plan"),key=blind.resolve("blind-key.jsonl"),
                primary=temp.resolve("primary.jsonl");
        Fixture() throws Exception {
            Files.createDirectories(blind);
            List<Map<String,Object>> keys=new ArrayList<>(),items=new ArrayList<>();
            List<ObjectNode> scores=new ArrayList<>();
            for (String arm:List.of("A1","B2")) for (int i=0;i<80;i++) {
                String id=arm+"-"+i;
                keys.add(Map.of("id",id,"arm",arm,"questionFormat","MCQ","noteRef","note-"+(i/8),
                        "noteLevel","COLLEGE"));
                items.add(Map.of("id",id,"noteRef","note-"+(i/8),"question","Synthetic question "+i,
                        "choices",List.of("A","B","C","D"),"questionFormat","MCQ","keyedAnswer","A",
                        "explanation","Synthetic explanation"));
                ObjectNode score=QualityScoreFiles.JSON.createObjectNode();
                score.put("id",id); score.put("reviewerId","codex"); score.put("round","PRIMARY");
                score.put("factuallyCorrect","YES"); score.put("grounding","GROUNDED");
                score.put("critical",false); score.putNull("criticalType");
                score.put("distractorPlausibility",4); score.put("modeAlignment",4); score.put("cognitiveDemand",4);
                score.put("clarity",4); score.put("faithfulness",4);
                score.put("seriousGiveaway",false); score.putNull("giveawayPattern");
                scores.add(score);
            }
            QualityScoreFiles.write(key,keys);
            QualityScoreFiles.write(blind.resolve("blind-items-001.jsonl"),items);
            QualityScoreFiles.JSON.writeValue(blind.resolve("blind-manifest.json").toFile(),
                    Map.of("chunks",List.of("blind-items-001.jsonl")));
            QualityScoreFiles.write(primary,scores);
            QualityReviewPlan.plan(key,plan,176);
        }
        void mutate(String id,java.util.function.Consumer<ObjectNode> edit) throws Exception {
            List<JsonNode> rows=QualityScoreFiles.lines(primary);
            for (JsonNode row:rows) if (id.equals(row.path("id").asText())) edit.accept((ObjectNode)row);
            QualityScoreFiles.write(primary,rows);
        }
        void batches(Path out) throws Exception { QualityReviewBatches.batches(blind,plan,primary,out); }
    }
}
