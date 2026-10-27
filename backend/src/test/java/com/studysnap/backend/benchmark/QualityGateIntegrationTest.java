package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic scored rows exercise the unblinding joins and actual gate verdicts. */
class QualityGateIntegrationTest {
    @TempDir Path temp;

    @Test void missedCriticalEscalatesAndCompleteFreshReReviewCanPass() throws Exception {
        Fixture fixture=new Fixture(false,false);
        JsonNode incomplete=fixture.run(false);
        JsonNode luna=incomplete.path("models").path("Luna");
        assertThat(incomplete.path("verdict").asText()).isEqualTo("HOLD");
        assertThat(luna.path("escalation").path("escalationRequired").asBoolean()).isTrue();
        assertThat(luna.path("escalation").path("completeArmReReview").asBoolean()).isFalse();
        assertThat(luna.path("G1").path("wordingSensitiveRegressionTypeExcess").path("AMBIGUOUS_STEM").asInt()).isZero();
        assertThat(luna.path("G1").path("proposedCritical")).hasSize(1);
        JsonNode complete=fixture.run(true);
        assertThat(complete.path("models").path("Luna").path("escalation").path("completeArmReReview").asBoolean()).isTrue();
        assertThat(complete.path("verdict").asText()).isEqualTo("PASS");
        assertThat(complete.path("models").path("Luna").path("perNoteLevel").path("COLLEGE").path("proposed").path("G4").path("N").asInt()).isEqualTo(100);
        assertThat(complete.path("models").path("Sol").path("pairedBootstrap").path("distractorPlausibility").path("pairedNotes").asInt()).isEqualTo(20);
        assertThat(complete.path("models").path("Luna").path("representativePairedNotes").path("improvement").path("packId").asText()).isEqualTo("pack-0");
    }
    @Test void wordingTypeExcessFailsWhileWrongKeyIsNeverAttributed() throws Exception {
        Fixture wording=new Fixture(true,false);
        JsonNode gates=wording.run(true);
        JsonNode luna=gates.path("models").path("Luna");
        assertThat(luna.path("G1").path("wordingSensitiveRegressionTypeExcess").path("AMBIGUOUS_STEM").asInt()).isEqualTo(1);
        assertThat(luna.path("G1").path("verdict").asText()).isEqualTo("FAIL");
        Fixture wrongKey=new Fixture(true,true);
        JsonNode other=wrongKey.run(true).path("models").path("Luna");
        assertThat(other.path("G1").path("wordingSensitiveRegressionTypeExcess").path("AMBIGUOUS_STEM").asInt()).isZero();
        assertThat(other.path("G1").path("verdict").asText()).isEqualTo("FAIL");
        assertThat(other.path("G1").path("proposedCritical")).hasSize(2);
        assertThat(other.path("G1").path("overallNoRegression").asBoolean()).isFalse();
        assertThat(Files.readString(wrongKey.out.resolve("gates.md")))
                .contains("mechanical excess-over-same-model-current-arm screen, not proof of individual causal attribution",
                        "G1 overall no-regression: false", "proposedCritical: B2-0", "proposedCritical: B2-1", "WRONG_KEY");
    }
    @Test void freshArmReviewerMustDifferFromEveryEarlierReviewer() throws Exception {
        Fixture fixture=new Fixture(false,false);
        fixture.run(true);
        List<JsonNode> review=QualityScoreFiles.lines(fixture.reviews);
        List<JsonNode> broken=new ArrayList<>();
        for (JsonNode row:review) {
            ObjectNode copy=((ObjectNode)row).deepCopy();
            if ("ARM_RE_REVIEW".equals(copy.path("round").asText())) copy.put("reviewerId","opus");
            broken.add(copy);
        }
        QualityScoreFiles.write(fixture.reviews,broken);
        assertThatThrownBy(() -> QualityScoreFiles.scores(fixture.key,fixture.primary,fixture.reviews,fixture.adjudications))
                .hasMessageContaining("previously scored arm");
    }
    @Test void recurringGiveawayRequiresThreeItemsAcrossTwoNotes() throws Exception {
        Fixture oneNote=new Fixture(false,false);
        oneNote.giveaways=Set.of(10,11,12);
        JsonNode local=oneNote.run(true).path("models").path("Luna").path("G4");
        assertThat(local.path("recurring").asBoolean()).isFalse();
        assertThat(local.path("verdict").asText()).isEqualTo("PASS");
        Fixture twoNotes=new Fixture(false,false);
        twoNotes.giveaways=Set.of(10,11,20);
        JsonNode recurring=twoNotes.run(true).path("models").path("Luna").path("G4");
        assertThat(recurring.path("recurring").asBoolean()).isTrue();
        assertThat(recurring.path("verdict").asText()).isEqualTo("FAIL");
        assertThat(recurring.path("G_hat").asDouble()).isLessThanOrEqualTo(QualityGateParameters.G4_TARGET);
    }
    @Test void independentlyFoundDefectIsListedButUnadjudicatedDisagreementHolds() throws Exception {
        Fixture fixture=new Fixture(false,false);
        JsonNode luna=fixture.run(true,false).path("models").path("Luna");
        assertThat(luna.path("G1").path("proposedCritical")).hasSize(1);
        assertThat(luna.path("proposed").path("unresolved").asInt()).isEqualTo(1);
        assertThat(luna.path("escalation").path("escalationRequired").asBoolean()).isTrue();
        assertThat(luna.path("verdict").asText()).isEqualTo("HOLD");
    }
    @Test void newDefectInCompleteArmReReviewCannotBeIgnored() throws Exception {
        Fixture fixture=new Fixture(false,false);
        fixture.run(true);
        List<JsonNode> edited=new ArrayList<>();
        for (JsonNode row:QualityScoreFiles.lines(fixture.reviews)) {
            ObjectNode copy=((ObjectNode)row).deepCopy();
            if ("B2-50".equals(copy.path("id").asText()) && "ARM_RE_REVIEW".equals(copy.path("round").asText())) {
                copy.put("critical",true); copy.put("criticalType","WRONG_KEY"); copy.put("factuallyCorrect","NO");
            }
            edited.add(copy);
        }
        QualityScoreFiles.write(fixture.reviews,edited);
        QualityGateReport.report(fixture.key,fixture.primary,fixture.reviews,fixture.adjudications,fixture.descriptors,
                fixture.plan.resolve("review-manifest.json"),fixture.plan.resolve("review-order.jsonl"),fixture.out);
        JsonNode luna=QualityScoreFiles.JSON.readTree(fixture.out.resolve("gates.json").toFile()).path("models").path("Luna");
        assertThat(luna.path("verdict").asText()).isEqualTo("HOLD");
        assertThat(luna.path("proposed").path("unresolved").asInt()).isEqualTo(1);
        assertThat(luna.path("G1").path("proposedCritical")).hasSize(2);
    }
    @Test void missingTelemetryStaysMissing() throws Exception {
        Fixture fixture=new Fixture(false,false);
        fixture.run(true);
        ObjectNode descriptor=(ObjectNode)QualityScoreFiles.JSON.readTree(fixture.descriptors.toFile());
        ObjectNode pair=(ObjectNode)descriptor.path("pairs").path("A2/B2");
        ObjectNode proposed=(ObjectNode)pair.path("pairedArms").path("B2");
        ((ObjectNode)proposed.path("g7")).put("telemetryMissingStarts",5);
        ((ObjectNode)proposed.path("g6")).put("telemetryMissingStarts",1);
        QualityScoreFiles.JSON.writeValue(fixture.descriptors.toFile(),descriptor);
        QualityGateReport.report(fixture.key,fixture.primary,fixture.reviews,fixture.adjudications,fixture.descriptors,
                fixture.plan.resolve("review-manifest.json"),fixture.plan.resolve("review-order.jsonl"),fixture.out);
        JsonNode luna=QualityScoreFiles.JSON.readTree(fixture.out.resolve("gates.json").toFile()).path("models").path("Luna");
        assertThat(luna.path("G7").path("proposedAllFormatValidatorRejectionRate").asText()).isEqualTo("MISSING");
        assertThat(luna.path("G7").path("verdict").asText()).isEqualTo("INCONCLUSIVE");
        assertThat(luna.path("G6").path("verdict").asText()).isEqualTo("PASS");
        assertThat(luna.path("verdict").asText()).isEqualTo("HOLD");
    }
    @Test void g6UsesDeliveredRatioAndSeparateResourceLimits() throws Exception {
        Fixture fixture=new Fixture(false,false);
        fixture.run(true);
        ObjectNode descriptor=(ObjectNode)QualityScoreFiles.JSON.readTree(fixture.descriptors.toFile());
        ObjectNode proposed=(ObjectNode)descriptor.path("pairs").path("A2/B2").path("pairedArms").path("B2").path("g6");
        proposed.put("deliveredTotal",205); proposed.put("p50LatencyMs",114); proposed.put("tokensPerDeliveredQuestion",11.4);
        QualityScoreFiles.JSON.writeValue(fixture.descriptors.toFile(),descriptor);
        assertThat(fixture.run(true).path("models").path("Luna").path("G6").path("verdict").asText()).isEqualTo("PASS");
        proposed.put("deliveredTotal",207);
        QualityScoreFiles.JSON.writeValue(fixture.descriptors.toFile(),descriptor);
        assertThat(fixture.run(true).path("models").path("Luna").path("G6").path("verdict").asText()).isEqualTo("FAIL");
        proposed.put("deliveredTotal",205); proposed.put("p50LatencyMs",116);
        QualityScoreFiles.JSON.writeValue(fixture.descriptors.toFile(),descriptor);
        assertThat(fixture.run(true).path("models").path("Luna").path("G6").path("verdict").asText()).isEqualTo("FAIL");
        proposed.put("p50LatencyMs",100); proposed.put("tokensPerDeliveredQuestion",11.6);
        QualityScoreFiles.JSON.writeValue(fixture.descriptors.toFile(),descriptor);
        assertThat(fixture.run(true).path("models").path("Luna").path("G6").path("verdict").asText()).isEqualTo("FAIL");
    }
    @Test void twentyPercentMissingIsAllowedButMoreIsInconclusive() throws Exception {
        Fixture fixture=new Fixture(false,false);
        fixture.run(true);
        ObjectNode descriptor=(ObjectNode)QualityScoreFiles.JSON.readTree(fixture.descriptors.toFile());
        ObjectNode g7=(ObjectNode)descriptor.path("pairs").path("A2/B2").path("pairedArms").path("B2").path("g7");
        g7.put("telemetryMissingStarts",4);
        QualityScoreFiles.JSON.writeValue(fixture.descriptors.toFile(),descriptor);
        JsonNode atBoundary=fixture.run(true).path("models").path("Luna").path("G7");
        assertThat(atBoundary.path("verdict").asText()).isEqualTo("PASS");
        g7.put("telemetryMissingStarts",5);
        QualityScoreFiles.JSON.writeValue(fixture.descriptors.toFile(),descriptor);
        JsonNode beyond=fixture.run(true).path("models").path("Luna").path("G7");
        assertThat(beyond.path("verdict").asText()).isEqualTo("INCONCLUSIVE");
        assertThat(beyond.path("telemetryVerdict").asText()).isEqualTo("INCONCLUSIVE");
        assertThat(beyond.path("multiSelectThreeCorrectShareVerdict").asText()).isEqualTo("PASS");
        assertThat(beyond.path("allFormatValidatorRejectionVerdict").asText()).isEqualTo("INCONCLUSIVE");
        assertThat(beyond.path("allFormatValidatorRetryVerdict").asText()).isEqualTo("INCONCLUSIVE");
    }
    @Test void missingnessReasonMakesModelComparisonInconclusive() throws Exception {
        Fixture fixture=new Fixture(false,false);
        ObjectNode descriptor=(ObjectNode)QualityScoreFiles.JSON.readTree(fixture.descriptors.toFile());
        ObjectNode missing=QualityScoreFiles.JSON.createObjectNode();
        missing.put("verdict","INCONCLUSIVE"); missing.putArray("reasons").add("Proposed arm has 2 more failed or spend-stopped starts");
        missing.putArray("missingPairs"); missing.putObject("byNoteLevel"); missing.putObject("byStratum");
        missing.put("proposedMinusCurrentFailedStarts",2);
        ((ObjectNode)descriptor.path("pairs").path("A2/B2")).set("missingness",missing);
        QualityScoreFiles.JSON.writeValue(fixture.descriptors.toFile(),descriptor);
        JsonNode model=fixture.run(true).path("models").path("Luna");
        assertThat(model.path("verdict").asText()).isEqualTo("INCONCLUSIVE");
        assertThat(model.path("reasons").toString()).contains("Proposed arm has 2 more");
        assertThat(Files.readString(fixture.out.resolve("gates.md"))).contains("INCONCLUSIVE: Proposed arm has 2 more");
    }
    @Test void g7UsesAbsolutePointsAndRequestedDenominator() throws Exception {
        Fixture fixture=new Fixture(false,false);
        fixture.run(true);
        ObjectNode descriptor=(ObjectNode)QualityScoreFiles.JSON.readTree(fixture.descriptors.toFile());
        ObjectNode pair=(ObjectNode)descriptor.path("pairs").path("A2/B2");
        ObjectNode current=(ObjectNode)pair.path("pairedArms").path("A2").path("g7");
        ObjectNode proposed=(ObjectNode)pair.path("pairedArms").path("B2").path("g7");
        current.put("validatorRequested",100); current.put("validatorAccepted",90); current.put("validatorRejected",10); current.put("validatorRetried",10);
        proposed.put("validatorRequested",100); proposed.put("validatorAccepted",81); proposed.put("validatorRejected",19); proposed.put("validatorRetried",19);
        current.put("multiSelectTwoCorrect",50); current.put("multiSelectThreeCorrect",50);
        proposed.put("multiSelectTwoCorrect",41); proposed.put("multiSelectThreeCorrect",59);
        QualityScoreFiles.JSON.writeValue(fixture.descriptors.toFile(),descriptor);
        JsonNode g7=fixture.run(true).path("models").path("Luna").path("G7");
        assertThat(g7.path("verdict").asText()).isEqualTo("PASS");
        assertThat(g7.path("proposedAllFormatValidatorRetryRate").asDouble()).isEqualTo(.19);
        assertThat(g7.path("proposedMultiSelectThreeCorrectShare").asDouble()).isEqualTo(.59);
        assertThat(g7.path("absolutePercentagePointDifferences").path("allFormatValidatorRejectionRate").asDouble())
                .isCloseTo(9,org.assertj.core.data.Offset.offset(.000001));
        proposed.put("validatorRetried",21);
        QualityScoreFiles.JSON.writeValue(fixture.descriptors.toFile(),descriptor);
        assertThat(fixture.run(true).path("models").path("Luna").path("G7").path("verdict").asText()).isEqualTo("FAIL");
    }
    @Test void fourteenActualPairedNotesAreInconclusive() throws Exception {
        Fixture fixture=new Fixture(false,false);
        fixture.keys.removeIf(k -> "B2".equals(k.get("arm")) && Integer.parseInt(((String)k.get("id")).substring(3))>=140);
        fixture.primaryRows.removeIf(r -> r.path("id").asText().startsWith("B2-")
                && Integer.parseInt(r.path("id").asText().substring(3))>=140);
        QualityScoreFiles.write(fixture.key,fixture.keys); QualityScoreFiles.write(fixture.primary,fixture.primaryRows);
        Files.delete(fixture.plan.resolve("review-order.jsonl")); Files.delete(fixture.plan.resolve("review-manifest.json"));
        QualityReviewPlan.plan(fixture.key,fixture.plan,176);
        ObjectNode descriptor=(ObjectNode)QualityScoreFiles.JSON.readTree(fixture.descriptors.toFile());
        ((ObjectNode)descriptor.path("arms")).set("B2",QualityScoreFiles.JSON.valueToTree(fixture.armDescriptor(14,0)));
        ObjectNode pair=(ObjectNode)descriptor.path("pairs").path("A2/B2");
        pair.put("pairedNotes",14);
        pair.set("pairedArms",QualityScoreFiles.JSON.valueToTree(Map.of("A2",fixture.armDescriptor(14,0),"B2",fixture.armDescriptor(14,0))));
        ObjectNode levels=(ObjectNode)pair.path("perNoteLevel");
        levels.set("COLLEGE",QualityScoreFiles.JSON.valueToTree(Map.of("pairedNotes",10,
                "pairedArms",Map.of("A2",fixture.armDescriptor(10,0),"B2",fixture.armDescriptor(10,0)))));
        levels.set("BOARD_EXAM_REVIEW",QualityScoreFiles.JSON.valueToTree(Map.of("pairedNotes",4,
                "pairedArms",Map.of("A2",fixture.armDescriptor(4,10),"B2",fixture.armDescriptor(4,10)))));
        QualityScoreFiles.JSON.writeValue(fixture.descriptors.toFile(),descriptor);
        JsonNode luna=fixture.run(true).path("models").path("Luna");
        assertThat(luna.path("G8").path("pairedNotes").asInt()).isEqualTo(14);
        assertThat(luna.path("G8").path("verdict").asText()).isEqualTo("INCONCLUSIVE");
        assertThat(luna.path("proposed").path("G4").path("N").asInt()).isEqualTo(140);
    }
    @Test void gateRefusesAProposedArmWithTheCurrentPromptHash() throws Exception {
        Fixture fixture=new Fixture(false,false);
        fixture.run(true);
        List<Map<String,Object>> corrupted=new ArrayList<>();
        for (Map<String,Object> row:fixture.keys) {
            Map<String,Object> copy=new LinkedHashMap<>(row);
            if ("B2".equals(copy.get("arm"))) copy.put("promptSha256","a".repeat(64));
            corrupted.add(copy);
        }
        QualityScoreFiles.write(fixture.key,corrupted);
        assertThatThrownBy(() -> QualityGateReport.report(fixture.key,fixture.primary,fixture.reviews,fixture.adjudications,
                fixture.descriptors,fixture.plan.resolve("review-manifest.json"),fixture.plan.resolve("review-order.jsonl"),fixture.out))
                .hasMessageContaining("distinct single prompt hashes");
    }
    @Test void missingOrInvalidPrimaryRowMakesGatesInconclusive() throws Exception {
        Fixture fixture=new Fixture(false,false);
        fixture.run(true);
        List<ObjectNode> rows=new ArrayList<>(fixture.primaryRows);
        rows.removeIf(row->"B2-19".equals(row.path("id").asText()));
        QualityScoreFiles.write(fixture.primary,rows);
        QualityGateReport.report(fixture.key,fixture.primary,fixture.reviews,fixture.adjudications,fixture.descriptors,
                fixture.plan.resolve("review-manifest.json"),fixture.plan.resolve("review-order.jsonl"),fixture.out);
        JsonNode report=QualityScoreFiles.JSON.readTree(fixture.out.resolve("gates.json").toFile());
        assertThat(report.path("models").path("Luna").path("G4").path("verdict").asText()).isEqualTo("INCONCLUSIVE");
        assertThat(report.path("models").path("Sol").path("verdict").asText()).isEqualTo("PASS");
        assertThat(report.path("incompleteScoring").path("B2").get(0).asText()).contains("B2-19");
        rows.add(fixture.primaryRows.stream().filter(row->"B2-19".equals(row.path("id").asText())).findFirst().orElseThrow());
        ObjectNode invalid=rows.stream().filter(row->"B2-20".equals(row.path("id").asText())).findFirst().orElseThrow();
        invalid.put("clarity",6);
        QualityScoreFiles.write(fixture.primary,rows);
        QualityGateReport.report(fixture.key,fixture.primary,fixture.reviews,fixture.adjudications,fixture.descriptors,
                fixture.plan.resolve("review-manifest.json"),fixture.plan.resolve("review-order.jsonl"),fixture.out);
        report=QualityScoreFiles.JSON.readTree(fixture.out.resolve("gates.json").toFile());
        assertThat(report.path("incompleteScoring").path("B2").get(0).asText()).contains("clarity");
        Files.writeString(fixture.primary,"{\n",java.nio.file.StandardOpenOption.APPEND);
        QualityGateReport.report(fixture.key,fixture.primary,fixture.reviews,fixture.adjudications,fixture.descriptors,
                fixture.plan.resolve("review-manifest.json"),fixture.plan.resolve("review-order.jsonl"),fixture.out);
        report=QualityScoreFiles.JSON.readTree(fixture.out.resolve("gates.json").toFile());
        assertThat(report.path("incompleteScoring").path("A3").toString()).contains("Unparseable PRIMARY row");
    }

    private final class Fixture {
        final Path key=temp.resolve("key.jsonl"), primary=temp.resolve("primary.jsonl"), reviews=temp.resolve("reviews.jsonl"),
                adjudications=temp.resolve("adjudications.jsonl"), descriptors=temp.resolve("descriptors.json"),
                plan=temp.resolve("plan"),out=temp.resolve("out");
        final boolean extra,wrongKey;
        Set<Integer> giveaways=Set.of();
        final List<Map<String,Object>> keys=new ArrayList<>();
        final List<ObjectNode> primaryRows=new ArrayList<>();
        Fixture(boolean extra,boolean wrongKey) throws Exception {
            this.extra=extra; this.wrongKey=wrongKey;
            for (String arm:List.of("A2","B2","A3","B3")) for (int i=0;i<200;i++) {
                String id=arm+"-"+i,level=i<100?"COLLEGE":"BOARD_EXAM_REVIEW";
                keys.add(Map.of("id",id,"arm",arm,"questionFormat",i%5==0?"MULTI_SELECT":"MCQ",
                        "noteLevel",level,"packId","pack-"+(i/10),"noteRef","note-"+(i/10),
                        "model",arm.endsWith("2")?"gpt-6-luna":"gpt-6.1-sol",
                        "promptSha256",arm.startsWith("A")?"a".repeat(64):"b".repeat(64)));
                boolean defect=(arm.equals("A2") && i==0) || (arm.equals("B2") && extra && i==1);
                primaryRows.add(score(id,"codex","PRIMARY",defect,wrongKey?"WRONG_KEY":"AMBIGUOUS_STEM",arm.startsWith("B")?5:4));
            }
            QualityScoreFiles.write(key,keys); QualityScoreFiles.write(primary,primaryRows);
            QualityReviewPlan.plan(key,plan,176);
            QualityScoreFiles.JSON.writerWithDefaultPrettyPrinter().writeValue(descriptors.toFile(),descriptorData());
        }
        JsonNode run(boolean complete) throws Exception {
            return run(complete,true);
        }
        JsonNode run(boolean complete,boolean adjudicate) throws Exception {
            Set<String> required=new java.util.HashSet<>();
            required.add("A2-0"); required.add("B2-0"); if (extra) required.add("B2-1");
            for (int i:giveaways) required.add("B2-"+i);
            for (ObjectNode row:primaryRows) if (row.path("id").asText().startsWith("B2-")) {
                int index=Integer.parseInt(row.path("id").asText().substring(3));
                if (giveaways.contains(index)) markGiveaway(row);
            }
            Map<String,JsonNode> scored=new LinkedHashMap<>();
            for (ObjectNode row:primaryRows) scored.put(row.path("id").asText(),row);
            required.addAll(QualityReviewBatches.selection(QualityScoreFiles.lines(plan.resolve("review-order.jsonl")),
                    QualityScoreFiles.keyed(key),scored).keySet());
            QualityScoreFiles.write(primary,primaryRows);
            List<ObjectNode> reviewRows=new ArrayList<>(),adjudicationRows=new ArrayList<>();
            for (Map<String,Object> keyRow:keys) {
                String id=(String)keyRow.get("id"), arm=(String)keyRow.get("arm");
                int score=arm.startsWith("B")?5:4;
                String type=wrongKey?"WRONG_KEY":"AMBIGUOUS_STEM";
                boolean critical=id.equals("A2-0") || id.equals("B2-0") || (extra && id.equals("B2-1"));
                if (required.contains(id)) {
                    ObjectNode reviewed=score(id,"opus","INDEPENDENT_REVIEW",critical,type,score);
                    if (arm.equals("B2") && giveaways.contains(Integer.parseInt(id.substring(3)))) markGiveaway(reviewed);
                    reviewRows.add(reviewed);
                }
                if (complete && arm.equals("B2")) {
                    ObjectNode rer=score(id,"fresh","ARM_RE_REVIEW",critical,type,score);
                    if (giveaways.contains(Integer.parseInt(id.substring(3)))) markGiveaway(rer);
                    reviewRows.add(rer);
                }
            }
            if (adjudicate) adjudicationRows.add(score("B2-0","judge","ADJUDICATION",true,wrongKey?"WRONG_KEY":"AMBIGUOUS_STEM",5));
            QualityScoreFiles.write(reviews,reviewRows); QualityScoreFiles.write(adjudications,adjudicationRows);
            QualityGateReport.report(key,primary,reviews,adjudications,descriptors,plan.resolve("review-manifest.json"),
                    plan.resolve("review-order.jsonl"),out);
            return QualityScoreFiles.JSON.readTree(out.resolve("gates.json").toFile());
        }
        private Map<String,Object> descriptorData() {
            Map<String,Object> arms=new LinkedHashMap<>(),pairs=new LinkedHashMap<>();
            for (String arm:List.of("A2","B2","A3","B3")) arms.put(arm,armDescriptor(20));
            for (String pair:List.of("A2/B2","A3/B3")) {
                String[] armsInPair=pair.split("/");
                Map<String,Object> pairedArms=Map.of(armsInPair[0],armDescriptor(20),armsInPair[1],armDescriptor(20));
                Map<String,Object> college=Map.of("pairedNotes",10,"pairedArms",Map.of(armsInPair[0],armDescriptor(10,0),armsInPair[1],armDescriptor(10,0)));
                Map<String,Object> board=Map.of("pairedNotes",10,"pairedArms",Map.of(armsInPair[0],armDescriptor(10,10),armsInPair[1],armDescriptor(10,10)));
                pairs.put(pair,Map.of("pairedNotes",20,"missingOrFailedSides",List.of(),"pairedArms",pairedArms,
                        "perNoteLevel",Map.of("COLLEGE",college,"BOARD_EXAM_REVIEW",board)));
            }
            return Map.of("arms",arms,"pairs",pairs);
        }
        private Map<String,Object> armDescriptor(int starts) { return armDescriptor(starts,0); }
        private Map<String,Object> armDescriptor(int starts,int offset) {
            Map<String,Object> perNote=new LinkedHashMap<>();
            for (int i=0;i<starts;i++) perNote.put("pack-"+(i+offset),Map.of("nearDuplicateQuestions",0));
            Map<String,Object> g6=Map.ofEntries(Map.entry("starts",starts),Map.entry("deliveredTotal",starts*10),
                    Map.entry("validatorAccepted",starts*10),Map.entry("validatorRejected",0),Map.entry("validatorRetried",0),
                    Map.entry("telemetryMissingStarts",0),Map.entry("p50LatencyMs",100),Map.entry("tokensPerDeliveredQuestion",10));
            Map<String,Object> g7=Map.of("multiSelectTwoCorrect",starts,"multiSelectThreeCorrect",starts,
                    "starts",starts,"validatorRequested",starts*10,"validatorAccepted",starts*10,"validatorRejected",0,"validatorRetried",0,"telemetryMissingStarts",0);
            Map<String,Object> result=new LinkedHashMap<>(Map.of("g6",g6,"g7",g7,"perNote",perNote));
            if (starts==20) result.put("perNoteLevel",Map.of("COLLEGE",armDescriptor(10,0),"BOARD_EXAM_REVIEW",armDescriptor(10,10)));
            return result;
        }
    }
    private static ObjectNode score(String id,String reviewer,String round,boolean critical,String type,int distractor) {
        ObjectNode row=QualityScoreFiles.JSON.createObjectNode();
        row.put("id",id); row.put("reviewerId",reviewer); row.put("round",round);
        row.put("factuallyCorrect",critical && "WRONG_KEY".equals(type)?"NO":"YES"); row.put("grounding","GROUNDED");
        row.put("critical",critical); if (critical) row.put("criticalType",type); else row.putNull("criticalType");
        row.put("distractorPlausibility",distractor); row.put("modeAlignment",4); row.put("cognitiveDemand",4);
        row.put("clarity",4); row.put("faithfulness",4); row.put("seriousGiveaway",false); row.putNull("giveawayPattern");
        return row;
    }
    private static void markGiveaway(ObjectNode row) {
        row.put("seriousGiveaway",true); row.put("giveawayPattern","LENGTH_OR_SPECIFICITY");
    }
}
