package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QualityGateReportTest {
    @TempDir Path temp;

    @Test void generationCountsUseReturnedAttemptsAndStrictFields() {
        String first="action=challenge_quiz_generation_counts requested=15 accepted=0 rejected=4 retried=4 omitted=0 outcome=threw verified=0 verifierRejected=99";
        String second="action=challenge_quiz_generation_counts requested=15 accepted=15 rejected=3 retried=3 omitted=0 outcome=returned verified=0 verifierRejected=8";
        Map<String,Object> captured=LlmModelBenchmark.validatorTelemetry(true,List.of(first,second));
        assertThat(captured.get("validatorTelemetry")).isEqualTo("CAPTURED");
        assertThat(captured.get("validatorRequested")).isEqualTo(15);
        assertThat(captured.get("validatorRejected")).isEqualTo(3);
        assertThat(captured.get("discardedAttempts")).isEqualTo(1);
        assertThat(LlmModelBenchmark.validatorTelemetry(true,List.of(second,second)).get("validatorRequested")).isEqualTo(30);
        assertThat(LlmModelBenchmark.validatorTelemetry(false,List.of(second)).get("validatorRequested")).isNull();
        assertThat(LlmModelBenchmark.validatorTelemetry(false,List.of(second)).get("validatorTelemetry")).isEqualTo("NOT_APPLICABLE");
        assertThat(LlmModelBenchmark.validatorTelemetry(true,List.of(first)).get("validatorTelemetry")).isEqualTo("MISSING");
        assertThat(LlmModelBenchmark.validatorTelemetry(true,List.of(first)).get("validatorRejected")).isNull();
        assertThatThrownBy(()->LlmModelBenchmark.validatorTelemetry(true,List.of(second.replace("retried=3","retried=oops"))))
                .hasMessageContaining("Missing retried");
    }

    @Test void requestedDenominatorIsReversibleAtOneSwitch() throws Exception {
        JsonNode row=QualityScoreFiles.JSON.readTree("{\"validatorRequested\":15,\"validatorAccepted\":15,\"validatorRejected\":3,\"validatorRetried\":3}");
        assertThat(QualityGateParameters.G7_DENOMINATOR_REQUESTED).isTrue();
        assertThat(QualityGateReport.validatorRate(row,"validatorRejected",true)).isEqualTo(.2);
        assertThat(QualityGateReport.validatorRate(row,"validatorRetried",true)).isEqualTo(.2);
        assertThat(QualityGateReport.validatorRate(row,"validatorRejected",false)).isCloseTo(1d/6,org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test void missingnessTriggersAndNearMissesAreMechanical() throws Exception {
        Set<String> packs=new java.util.TreeSet<>(); Map<String,String> levels=new TreeMap<>(),strata=new TreeMap<>();
        for (int i=0;i<20;i++) { String pack="p"+i; packs.add(pack);
            levels.put(pack,i<10?"COLLEGE":"BOARD_EXAM_REVIEW"); strata.put(pack,i==0?"unique":"shared"); }
        Map<String,JsonNode> a=starts(packs),b=starts(packs);
        b.put("p1",start(false,"HTTP_ERROR"));
        assertThat(missing(packs,levels,strata,a,b).path("reasons").toString()).doesNotContain("more failed");
        b.put("p2",start(false,"HTTP_ERROR"));
        assertThat(missing(packs,levels,strata,a,b).path("reasons").toString()).contains("more failed");
        b=starts(packs); a=starts(packs);
        for (int i=1;i<=4;i++) a.put("p"+i,start(false,"HTTP_ERROR"));
        assertThat(missing(packs,levels,strata,a,b).path("reasons").toString()).doesNotContain("noteLevel COLLEGE");
        a.put("p5",start(false,"HTTP_ERROR"));
        assertThat(missing(packs,levels,strata,a,b).path("reasons").toString()).contains("noteLevel COLLEGE");
        a=starts(packs); a.put("p0",start(false,"HTTP_ERROR"));
        assertThat(missing(packs,levels,strata,a,b).path("reasons").toString()).contains("Stratum unique");
        strata.put("p1","unique");
        assertThat(missing(packs,levels,strata,a,b).path("reasons").toString()).doesNotContain("Stratum unique");
        strata.put("p1","shared");
        a=starts(packs); a.put("p1",start(false,"SCHEMA_OR_PARSE"));
        a.put("p2",start(false,"MULTI_OUTPUT_TEXT")); a.put("p3",start(false,"HTTP_ERROR"));
        assertThat(missing(packs,levels,strata,a,b).path("reasons").toString()).contains("failed starts correlate");
        a.put("p2",start(false,"HTTP_ERROR"));
        assertThat(missing(packs,levels,strata,a,b).path("reasons").toString()).doesNotContain("correlate");
        a.put("p2",start(false,"MULTI_OUTPUT_TEXT")); a.put("p4",start(false,"HTTP_ERROR"));
        assertThat(missing(packs,levels,strata,a,b).path("reasons").toString()).doesNotContain("correlate"); // exactly half is not more than half
        a.put("p2",start(false,"HTTP_ERROR")); a.put("p4",start(true,"SUCCESS"));
        a.put("p1",start(false,"HTTP_ERROR"));
        assertThat(missing(packs,levels,strata,a,b).path("reasons").toString()).doesNotContain("correlate");
        a=starts(packs); b=starts(packs); a.put("p1",start(false,"SCHEMA_OR_PARSE")); b.put("p1",start(false,"SCHEMA_OR_PARSE"));
        assertThat(missing(packs,levels,strata,a,b).path("verdict").asText()).isEqualTo("CLEAR");
        assertThat(missing(packs,levels,strata,a,b).path("missingPairs")).hasSize(1);
        a.put("p0",start(false,"SCHEMA_OR_PARSE")); b.put("p0",start(false,"SCHEMA_OR_PARSE"));
        String sharedOnly=missing(packs,levels,strata,a,b).path("reasons").toString();
        assertThat(sharedOnly).contains("Stratum unique").doesNotContain("more failed").doesNotContain("correlate");
        a=starts(packs); b=starts(packs);
        a.put("p1",telemetry("SCHEMA_OR_PARSE")); a.put("p2",telemetry("MULTI_OUTPUT_TEXT"));
        assertThat(missing(packs,levels,strata,a,b).path("reasons").toString()).contains("MISSING telemetry correlates");
        a.put("p2",telemetry("HTTP_ERROR"));
        assertThat(missing(packs,levels,strata,a,b).path("reasons").toString()).doesNotContain("correlates");
    }
    private static Map<String,JsonNode> starts(Set<String> packs) throws Exception {
        Map<String,JsonNode> rows=new TreeMap<>(); for (String pack:packs) rows.put(pack,start(true,"SUCCESS")); return rows;
    }
    private static JsonNode start(boolean success,String classification) throws Exception {
        return QualityScoreFiles.JSON.readTree("{\"success\":"+success+",\"failureClass\":\""+classification+"\"}");
    }
    private static JsonNode telemetry(String classification) throws Exception {
        return QualityScoreFiles.JSON.readTree("{\"success\":true,\"validatorTelemetry\":\"MISSING\",\"validatorTelemetryFailureClass\":\""+classification+"\"}");
    }
    private static JsonNode missing(Set<String> packs,Map<String,String> levels,Map<String,String> strata,
            Map<String,JsonNode> a,Map<String,JsonNode> b) {
        return QualityScoreFiles.JSON.valueToTree(QualityDescriptors.missingness(packs,levels,strata,a,b,"A2","B2"));
    }

    @Test void rubricG4IllustrationsAndExactBinomialLimit() {
        assertThat(QualityGateReport.cpUpper(0,30,.90)).isCloseTo(.074,org.assertj.core.data.Offset.offset(.0005));
        assertThat(QualityGateReport.cpUpper(1,30,.90)).isCloseTo(.124,org.assertj.core.data.Offset.offset(.0005));
        assertThat(QualityGateReport.cpUpper(299,300,.90)).isCloseTo(Math.pow(.9,1d/300),org.assertj.core.data.Offset.offset(.0000001));
        for (int k=0;k<=2;k++) {
            var value=QualityGateReport.g4(200,4,Map.of("MCQ",new int[]{160,30,k}),false);
            assertThat(value.estimate()*100).isCloseTo(new double[]{2,4.6666667,7.3333333}[k],org.assertj.core.data.Offset.offset(.05));
            assertThat(value.upperLimit()*100).isCloseTo(new double[]{7.9,11.9,15.4}[k],org.assertj.core.data.Offset.offset(.05));
            assertThat(value.verdict()).isEqualTo(new String[]{"INCONCLUSIVE","INCONCLUSIVE","FAIL"}[k]);
        }
    }
    @Test void appendixAndG4BoundaryMutants() {
        assertThat(QualityGateReport.g4(200,2,Map.of("MCQ",new int[]{194,60,1}),false).verdict()).isEqualTo("PASS");
        assertThat(QualityGateReport.g4(200,4,Map.of("MCQ",new int[]{192,25,1}),false).verdict()).isEqualTo("FAIL");
        assertThat(QualityGateReport.g4(149,0,Map.of("MCQ",new int[]{149,30,0}),false).verdict()).isEqualTo("INCONCLUSIVE");
        assertThat(QualityGateReport.g4(200,0,Map.of("MCQ",new int[]{200,24,0}),false).verdict()).isEqualTo("INCONCLUSIVE");
        assertThat(QualityGateReport.g4(200,0,Map.of("MCQ",new int[]{200,0,0}),false).verdict()).isEqualTo("INCONCLUSIVE");
        assertThat(QualityGateReport.g4(200,1,Map.of("MCQ",new int[]{198,60,1}),true).verdict()).isEqualTo("FAIL");
        var emptyStratum=QualityGateReport.g4(200,0,Map.of("MCQ",new int[]{100,25,1},"MATCHING",new int[]{100,0,0}),false);
        assertThat(emptyStratum.estimate()).isCloseTo(.04,org.assertj.core.data.Offset.offset(.00001));
    }
    @Test void appendixAtSignedSixtyItemFloor() {
        for (Object[] cell: new Object[][]{
                {0,0,"PASS",0.0,3.7}, {0,1,"PASS",1.6,6.2}, {0,2,"PASS",3.3,8.5},
                {2,1,"PASS",2.6,7.1}, {4,0,"PASS",2.0,5.6}, {4,1,"PASS",3.6,8.1},
                {4,2,"FAIL",5.2,10.3}, {6,1,"PASS",4.6,9.0}, {6,2,"FAIL",6.2,11.2}}) {
            int flagged=(int)cell[0],found=(int)cell[1];
            var g4=QualityGateReport.g4(200,flagged,Map.of("MCQ",new int[]{196-flagged,60,found}),false);
            assertThat(g4.verdict()).isEqualTo(cell[2]);
            assertThat(g4.estimate()*100).isCloseTo((double)cell[3],org.assertj.core.data.Offset.offset(.06));
            assertThat(g4.upperLimit()*100).isCloseTo((double)cell[4],org.assertj.core.data.Offset.offset(.06));
        }
        assertThat(QualityGateReport.g4(200,0,Map.of("MCQ",new int[]{196,59,0}),false).verdict()).isEqualTo("INCONCLUSIVE");
    }
    @Test void scoreSchemaAndIndependentReviewerAreStrict() throws Exception {
        Path key=temp.resolve("key.jsonl"),primary=temp.resolve("primary.jsonl"),reviews=temp.resolve("reviews.jsonl"),adj=temp.resolve("adj.jsonl");
        QualityScoreFiles.write(key,List.of(Map.of("id","q1","arm","A2","questionFormat","MCQ")));
        ObjectNode good=score("q1","one","PRIMARY",false,null);
        QualityScoreFiles.write(primary,List.of(good)); Files.writeString(reviews,""); Files.writeString(adj,"");
        QualityScoreFiles.scores(key,primary,reviews,adj);
        ObjectNode bad=good.deepCopy(); bad.put("modeAlignment",6);
        QualityScoreFiles.write(primary,List.of(bad));
        assertThatThrownBy(() -> QualityScoreFiles.scores(key,primary,reviews,adj)).hasMessageContaining("modeAlignment");
        bad=good.deepCopy(); bad.put("criticalType","NOT_A_TYPE"); bad.put("critical",true);
        QualityScoreFiles.write(primary,List.of(bad));
        assertThatThrownBy(() -> QualityScoreFiles.scores(key,primary,reviews,adj)).hasMessageContaining("criticalType");
        bad=good.deepCopy(); bad.remove("faithfulness"); QualityScoreFiles.write(primary,List.of(bad));
        assertThatThrownBy(() -> QualityScoreFiles.scores(key,primary,reviews,adj)).hasMessageContaining("Score fields differ");
        bad=good.deepCopy(); bad.put("seriousGiveaway",true); bad.put("giveawayPattern","UNKNOWN");
        QualityScoreFiles.write(primary,List.of(bad));
        assertThatThrownBy(() -> QualityScoreFiles.scores(key,primary,reviews,adj)).hasMessageContaining("giveawayPattern");
        QualityScoreFiles.write(primary,List.of(good));
        QualityScoreFiles.write(reviews,List.of(score("q1","one","INDEPENDENT_REVIEW",false,null)));
        assertThatThrownBy(() -> QualityScoreFiles.scores(key,primary,reviews,adj)).hasMessageContaining("must differ");
        assertThat(QualityScoreFiles.choice(QualityScoreFiles.JSON.readTree("{\"id\":\"free\",\"questionFormat\":\"IDENTIFICATION\"}"))).isFalse();
        assertThat(QualityScoreFiles.choice(QualityScoreFiles.JSON.readTree("{\"id\":\"match\",\"questionFormat\":\"MATCHING\"}"))).isTrue();
    }
    @Test void reviewPlanAndBatchesAreDeterministicBlindAndTopUp() throws Exception {
        Path blind=Files.createDirectory(temp.resolve("blind"));
        Path plan=Files.createDirectory(temp.resolve("plan"));
        List<Map<String,Object>> keys=new ArrayList<>(), items=new ArrayList<>();
        List<ObjectNode> scores=new ArrayList<>();
        for (String arm:List.of("A2","B2")) for (int i=0;i<80;i++) {
            String id=arm+"-"+i,format=i%2==0?"MCQ":"MULTI_SELECT",level=i%4<2?"COLLEGE":"BOARD_EXAM_REVIEW";
            keys.add(Map.of("id",id,"arm",arm,"questionFormat",format,"noteLevel",level,"noteRef","note-"+(i/8)));
            items.add(Map.of("id",id,"noteRef","note-1","question","Question?","choices",List.of("A","B"),
                    "questionFormat",format,"keyedAnswer","A","explanation","Explanation"));
            scores.add(score(id,"codex","PRIMARY",i<5,null));
        }
        keys.add(Map.of("id","A2-tf","arm","A2","questionFormat","TRUE_FALSE","noteLevel","COLLEGE","noteRef","note-1"));
        items.add(Map.of("id","A2-tf","noteRef","note-1","question","True?","choices",List.of("True","False"),
                "questionFormat","TRUE_FALSE","keyedAnswer","True","explanation","Explanation"));
        ObjectNode nonChoice=score("A2-tf","codex","PRIMARY",true,"WRONG_KEY");
        nonChoice.putNull("distractorPlausibility"); nonChoice.putNull("seriousGiveaway");
        scores.add(nonChoice);
        QualityScoreFiles.write(blind.resolve("blind-key.jsonl"),keys);
        QualityScoreFiles.write(blind.resolve("blind-items-001.jsonl"),items);
        QualityScoreFiles.JSON.writeValue(blind.resolve("blind-manifest.json").toFile(),Map.of("chunks",List.of("blind-items-001.jsonl")));
        Path primary=temp.resolve("scores.jsonl"); QualityScoreFiles.write(primary,scores);
        QualityReviewPlan.plan(blind.resolve("blind-key.jsonl"),plan,176);
        String first=Files.readString(plan.resolve("review-order.jsonl"));
        QualityReviewPlan.plan(blind.resolve("blind-key.jsonl"),plan,176);
        assertThat(Files.readString(plan.resolve("review-order.jsonl"))).isEqualTo(first);
        assertThatThrownBy(() -> QualityReviewPlan.plan(blind.resolve("blind-key.jsonl"),plan,177))
                .hasMessageContaining("Frozen review");
        long sampled=QualityScoreFiles.lines(plan.resolve("review-order.jsonl")).stream().filter(r->r.path("sampled").asBoolean()).count();
        assertThat(sampled).isEqualTo(32);
        List<JsonNode> armOrder=QualityScoreFiles.lines(plan.resolve("review-order.jsonl")).stream()
                .filter(row->"A2".equals(row.path("arm").asText())).toList();
        for (int prefix:List.of(20,40,60,80)) {
            Map<String,Long> observed=armOrder.subList(0,prefix).stream().collect(java.util.stream.Collectors.groupingBy(
                    row->row.path("noteRef").asText()+"/"+row.path("questionFormat").asText(),java.util.stream.Collectors.counting()));
            for (int note=0;note<10;note++) for (String format:List.of("MCQ","MULTI_SELECT"))
                assertThat(Math.abs(observed.getOrDefault("note-"+note+"/"+format,0L)-prefix/20d)).isLessThanOrEqualTo(1d);
        }
        Path out=temp.resolve("batches"); QualityReviewBatches.batches(blind,plan,primary,out);
        JsonNode manifest=QualityScoreFiles.JSON.readTree(out.resolve("opus-batch-manifest.json").toFile());
        assertThat(manifest.path("unflaggedPerArm").path("A2").asInt()).isGreaterThanOrEqualTo(QualityGateParameters.UNFLAGGED_REVIEW_FLOOR);
        assertThat(manifest.path("unflaggedPerArm").path("B2").asInt()).isGreaterThanOrEqualTo(QualityGateParameters.UNFLAGGED_REVIEW_FLOOR);
        for (String arm:List.of("A2","B2")) {
            assertThat(manifest.path("unflaggedPerArm").path(arm).asInt()).isEqualTo(60);
            for (int i=0;i<5;i++) assertThat(manifest.path("reasonById").path(arm+"-"+i).asText()).isEqualTo("FLAGGED");
        }
        List<JsonNode> batch=QualityScoreFiles.lines(out.resolve("opus-batch-001.jsonl"));
        assertThat(batch).hasSize(100);
        assertThat(manifest.path("reasonById").path("A2-tf").asText()).isEqualTo("FLAGGED");
        for (JsonNode row:batch) {
            assertThat(row.size()).isEqualTo(7);
            assertThat(row.has("arm") || row.has("model") || row.has("noteLevel") || row.has("critical") || row.has("seriousGiveaway")).isFalse();
        }
        List<String> reasons=new ArrayList<>();
        for (JsonNode row:batch) reasons.add(manifest.path("reasonById").path(row.path("id").asText()).asText());
        assertThat(reasons).contains("FLAGGED","SAMPLED","TOPPED_UP");
        assertThat(reasons.subList(0,10)).contains("FLAGGED","SAMPLED");
    }
    @Test void skewedStrataKeepEveryPrefixNearProportionalIncludingTopUp() throws Exception {
        Path key=temp.resolve("skew-key.jsonl"),plan=temp.resolve("skew-plan");
        int[] sizes={1,15,15,9,18,7,18,28,17,4,22,1,4,21,13,7,4,28};
        List<Map<String,Object>> rows=new ArrayList<>();
        int total=0;
        for (int stratum=0;stratum<sizes.length;stratum++) for (int i=0;i<sizes[stratum];i++) {
            rows.add(Map.of("id","q-"+stratum+"-"+i,"arm","A2","questionFormat","MCQ",
                    "noteRef","note-"+stratum,"noteLevel","COLLEGE")); total++;
        }
        QualityScoreFiles.write(key,rows);
        QualityReviewPlan.plan(key,plan,176);
        List<JsonNode> order=QualityScoreFiles.lines(plan.resolve("review-order.jsonl"));
        int[] used=new int[sizes.length];
        for (int prefix=1;prefix<=total;prefix++) {
            int stratum=Integer.parseInt(order.get(prefix-1).path("noteRef").asText().substring(5));
            used[stratum]++;
            for (int i=0;i<sizes.length;i++)
                assertThat(Math.abs(used[i]-(double)prefix*sizes[i]/total)).isLessThanOrEqualTo(1.1);
        }
        assertThat(order.stream().filter(row->row.path("sampled").asBoolean()).count())
                .isEqualTo((long)Math.ceil(total*QualityGateParameters.REVIEW_SAMPLE_FRACTION));
    }
    private static ObjectNode score(String id,String reviewer,String round,boolean critical,String type) {
        ObjectNode row=QualityScoreFiles.JSON.createObjectNode();
        row.put("id",id); row.put("reviewerId",reviewer); row.put("round",round);
        row.put("factuallyCorrect",critical && (type==null || "WRONG_KEY".equals(type))?"NO":"YES");
        row.put("grounding","GROUNDED");
        row.put("critical",critical); if (critical) row.put("criticalType",type==null?"WRONG_KEY":type); else row.putNull("criticalType");
        row.put("distractorPlausibility",4); row.put("modeAlignment",4); row.put("cognitiveDemand",4);
        row.put("clarity",4); row.put("faithfulness",4); row.put("seriousGiveaway",false); row.putNull("giveawayPattern");
        return row;
    }
    /** The one narrow region at or above the signed floor where the estimate is under 5% but the 90% upper limit is not: INCONCLUSIVE, never PASS. */
    @Test void upperLimitMakesAnEstimateUnderTargetInconclusive() {
        var value = QualityGateReport.g4(300, 5, Map.of("MCQ", new int[]{295, 60, 2}), false);
        assertThat(value.estimate()).isLessThanOrEqualTo(0.05);
        assertThat(value.upperLimit()).isGreaterThan(0.10);
        assertThat(value.verdict()).isEqualTo("INCONCLUSIVE");
        var tighter = QualityGateReport.g4(300, 5, Map.of("MCQ", new int[]{295, 60, 1}), false);
        assertThat(tighter.upperLimit()).isLessThanOrEqualTo(0.10);
        assertThat(tighter.verdict()).isEqualTo("PASS");
    }
}
