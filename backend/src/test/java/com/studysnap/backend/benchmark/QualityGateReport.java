package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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

/** Deterministic, offline quality gates. All score identities stay in private artifacts. */
public final class QualityGateReport {
    @Test void reportWhenRequested() throws Exception {
        assumeTrue(Boolean.getBoolean("llm.benchmark.qualityGates"));
        Path blind=Path.of(required("llm.benchmark.blindOutDir"));
        Path plan=Path.of(required("llm.benchmark.reviewOutDir"));
        report(blind.resolve("blind-key.jsonl"),Path.of(required("llm.benchmark.scoresCodex")),
                Path.of(required("llm.benchmark.reviewsOpus")),Path.of(required("llm.benchmark.adjudications")),
                Path.of(required("llm.benchmark.descriptors")),plan.resolve("review-manifest.json"),
                plan.resolve("review-order.jsonl"),Path.of(required("llm.benchmark.gatesOutDir")));
    }
    private static String required(String name) {
        String value=System.getProperty(name);
        if (value==null || value.isBlank()) throw new IllegalArgumentException(name+" is required");
        return value;
    }
    record G4(@JsonProperty("N") int n, @JsonProperty("C_F") int confirmedFlagged,
              @JsonProperty("U") int unflagged, @JsonProperty("sum_n") int reviewed, @JsonProperty("sum_k") int found,
              @JsonProperty("confirmedMinimumRate") double minimum, @JsonProperty("G_hat") double estimate,
              @JsonProperty("p_up") double pUp, @JsonProperty("UL") double upperLimit,
              double ci95Lower, double ci95Upper,
              boolean recurring, String verdict, Map<String,Map<String,Integer>> strata) {}

    static G4 g4(int n, int flaggedConfirmed, Map<String,int[]> strata, boolean recurring) {
        int unflagged=0, reviewed=0, found=0;
        for (int[] v : strata.values()) { unflagged+=v[0]; reviewed+=v[1]; found+=v[2]; }
        double pooled=reviewed==0 ? 0 : (double)found/reviewed;
        double estimated=flaggedConfirmed;
        Map<String,Map<String,Integer>> detail=new TreeMap<>();
        for (var e : strata.entrySet()) {
            int[] v=e.getValue();
            if (v[1]>v[0] || v[2]>v[1] || v[0]<0 || v[1]<0 || v[2]<0) throw new IllegalArgumentException("Invalid G4 stratum");
            estimated+=v[0]*(v[1]==0 ? pooled : (double)v[2]/v[1]);
            detail.put(e.getKey(),Map.of("U_h",v[0],"n_h",v[1],"k_h",v[2]));
        }
        if (n<0 || flaggedConfirmed<0 || flaggedConfirmed+unflagged>n) throw new IllegalArgumentException("Invalid G4 population");
        double minimum=n==0 ? 0 : (double)(flaggedConfirmed+found)/n;
        double estimate=n==0 ? 0 : estimated/n;
        double pUp=reviewed==0 ? 1 : cpUpper(found,reviewed,QualityGateParameters.G4_ONE_SIDED_CONFIDENCE);
        double upper=reviewed==0 || n==0 ? 1 : (flaggedConfirmed+unflagged*pUp)/n;
        double lower95=reviewed==0 || n==0 ? 0 : (flaggedConfirmed+unflagged*cpLower(found,reviewed,QualityGateParameters.G4_TRANSPARENCY_CONFIDENCE))/n;
        double upper95=reviewed==0 || n==0 ? 1 : (flaggedConfirmed+unflagged*cpUpperTwoSided(found,reviewed,QualityGateParameters.G4_TRANSPARENCY_CONFIDENCE))/n;
        String verdict=reviewed==0 ? "INCONCLUSIVE" : estimate>QualityGateParameters.G4_TARGET || recurring ? "FAIL"
                : estimate<=QualityGateParameters.G4_TARGET && upper<=QualityGateParameters.G4_UPPER_LIMIT
                && n>=QualityGateParameters.G4_MIN_CHOICE_ITEMS && reviewed>=QualityGateParameters.UNFLAGGED_REVIEW_FLOOR
                ? "PASS" : "INCONCLUSIVE";
        return new G4(n,flaggedConfirmed,unflagged,reviewed,found,minimum,estimate,pUp,upper,lower95,upper95,recurring,verdict,detail);
    }
    static double cpUpper(int k,int n,double confidence) { return quantile(k,n,1-confidence,true); }
    private static double cpUpperTwoSided(int k,int n,double confidence) { return quantile(k,n,(1-confidence)/2,true); }
    private static double cpLower(int k,int n,double confidence) { return quantile(k-1,n,1-(1-confidence)/2,false); }
    private static double quantile(int k,int n,double tail,boolean upper) {
        if (n<=0 || k>n || k < -1) throw new IllegalArgumentException("Invalid binomial sample");
        if (upper && k==n) return 1;
        if (!upper && k<0) return 0;
        double lo=0,hi=1;
        for (int i=0;i<70;i++) {
            double mid=(lo+hi)/2;
            // CDF is decreasing in p. Lower bound solves CDF(k-1,p)=1-alpha/2.
            if (cdf(k,n,mid)>tail) lo=mid; else hi=mid;
        }
        return (lo+hi)/2;
    }
    private static double cdf(int k,int n,double p) {
        if (k<0) return 0;
        if (p==0) return 1;
        if (p==1) return k==n ? 1 : 0;
        double sum=0,logChoose=0,logP=Math.log(p),logOneMinusP=Math.log1p(-p);
        for (int x=0;x<=k;x++) {
            sum+=Math.exp(logChoose+x*logP+(n-x)*logOneMinusP);
            if (x<k) logChoose+=Math.log(n-x)-Math.log(x+1);
        }
        return Math.min(1,sum);
    }

    static void report(Path keyFile,Path primary,Path reviews,Path adjudications,Path descriptorsFile,
                       Path reviewManifest,Path reviewOrder,Path out) throws Exception {
        QualityReviewPlan.verify(reviewOrder,reviewManifest);
        Map<String,JsonNode> keys=QualityScoreFiles.keyed(keyFile);
        Map<String,List<String>> incomplete=incompletePrimary(keys,primary);
        JsonNode descriptors=QualityScoreFiles.JSON.readTree(descriptorsFile.toFile());
        Map<String,Object> models=new LinkedHashMap<>(); List<String> holdReasons=new ArrayList<>();
        for (String[] pair : new String[][]{{"A2","B2","Luna"},{"A3","B3","Sol"}}) {
            if (incomplete.containsKey(pair[0]) || incomplete.containsKey(pair[1])) {
                models.put(pair[2],incompleteModel(incomplete,pair,descriptors));
                holdReasons.add(pair[2]+": incomplete primary scoring");
                continue;
            }
            Set<String> arms=Set.of(pair[0],pair[1]);
            Map<String,JsonNode> pairKeys=new LinkedHashMap<>();
            for (var entry:keys.entrySet()) if (arms.contains(QualityScoreFiles.text(entry.getValue(),"arm")))
                pairKeys.put(entry.getKey(),entry.getValue());
            Map<String,List<JsonNode>> scores=QualityScoreFiles.scores(keyFile,primary,reviews,adjudications,arms);
            Map<String,Item> items=new LinkedHashMap<>();
            for (var entry:pairKeys.entrySet()) items.put(entry.getKey(),new Item(entry.getValue(),scores.get(entry.getKey())));
            Map<String,JsonNode> primaries=new LinkedHashMap<>();
            for (Item item:items.values()) primaries.put(item.id,item.primary);
            List<JsonNode> pairOrder=QualityScoreFiles.lines(reviewOrder).stream()
                    .filter(row->arms.contains(row.path("arm").asText())).toList();
            Set<String> requiredReview=new HashSet<>(QualityReviewBatches.selection(pairOrder,pairKeys,primaries).keySet());
            for (Item item:items.values()) if (item.primary.path("critical").asBoolean() || item.primary.path("seriousGiveaway").asBoolean())
                requiredReview.add(item.id);
            Map<String,Object> result=model(pair[0],pair[1],items,descriptors);
            List<String> unreviewed=new ArrayList<>();
            for (String id:requiredReview) if (items.get(id).review==null) unreviewed.add(id);
            if (!unreviewed.isEmpty()) {
                List<String> reasons=(List<String>)result.get("reasons"); reasons.add("Required independent reviews missing: "+unreviewed.size());
                result.put("verdict","HOLD"); result.put("unreviewedRequiredIds",unreviewed);
            }
            models.put(pair[2],result);
            if (!"PASS".equals(result.get("verdict"))) holdReasons.add(pair[2]+": "+result.get("reasons"));
        }
        Map<String,Object> output=new LinkedHashMap<>();
        output.put("verdict",holdReasons.isEmpty()?"PASS":"HOLD"); output.put("reasons",holdReasons);
        output.put("incompleteScoring",incomplete);
        output.put("reviewOrderSha256",QualityScoreFiles.text(QualityScoreFiles.JSON.readTree(reviewManifest.toFile()),"sha256"));
        output.put("models",models);
        Files.createDirectories(out);
        QualityScoreFiles.JSON.writerWithDefaultPrettyPrinter().writeValue(out.resolve("gates.json").toFile(),output);
        StringBuilder md=new StringBuilder("# Challenge quality gates\n\nVerdict: **"+output.get("verdict")+"**\n\n"
                +"Wording-sensitive regression is a mechanical excess-over-same-model-current-arm screen, not proof of individual causal attribution.\n\n");
        for (String reason:holdReasons) md.append("- ").append(reason).append('\n');
        for (var entry:models.entrySet()) {
            Map<String,Object> model=(Map<String,Object>)entry.getValue();
            md.append("\n## ").append(entry.getKey()).append("\n\n");
            if (model.containsKey("incompleteScoring")) {
                JsonNode missing=(JsonNode)model.get("missingness");
                md.append("### Missingness\n\n| Note | Level | Stratum | Current | Proposed |\n| --- | --- | --- | --- | --- |\n");
                String current=entry.getKey().equals("Luna")?"A2":"A3",proposed=entry.getKey().equals("Luna")?"B2":"B3";
                for (JsonNode row:missing.path("missingPairs")) md.append("| ").append(row.path("packId").asText())
                        .append(" | ").append(row.path("noteLevel").asText()).append(" | ").append(row.path("stratum").asText())
                        .append(" | ").append(row.path(current).asText()).append(" | ").append(row.path(proposed).asText()).append(" |\n");
                appendDistributions(md,missing);
                md.append("Failed-start difference (proposed minus current): ")
                        .append(missing.path("proposedMinusCurrentFailedStarts")).append("\n\n");
                md.append("Incomplete primary scoring. G1–G8 are INCONCLUSIVE for this model.\n\n```json\n")
                        .append(QualityScoreFiles.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(model)).append("\n```\n");
                continue;
            }
            md
                    .append("BOARD_EXAM_REVIEW: ").append(model.get("boardExamReviewFinding")).append("\n\n");
            JsonNode missing=(JsonNode)model.get("missingness");
            md.append("### Missingness\n\n| Note | Level | Stratum | Current | Proposed |\n| --- | --- | --- | --- | --- |\n");
            for (JsonNode row:missing.path("missingPairs")) {
                String current=entry.getKey().equals("Luna")?"A2":"A3",proposed=entry.getKey().equals("Luna")?"B2":"B3";
                md.append("| ").append(row.path("packId").asText()).append(" | ").append(row.path("noteLevel").asText())
                        .append(" | ").append(row.path("stratum").asText()).append(" | ").append(row.path(current).asText())
                        .append(" | ").append(row.path(proposed).asText()).append(" |\n");
            }
            appendDistributions(md,missing);
            md.append("Failed-start difference (proposed minus current): ")
                    .append(missing.path("proposedMinusCurrentFailedStarts")).append("\n\n");
            for (JsonNode reason:missing.path("reasons")) md.append("- INCONCLUSIVE: ").append(reason.asText()).append('\n');
            JsonNode g7data=QualityScoreFiles.JSON.valueToTree(model.get("G7"));
            md.append("\nG7 discarded attempts (current/proposed): ").append(g7data.path("currentDiscardedAttempts"))
                    .append(" / ").append(g7data.path("proposedDiscardedAttempts"))
                    .append(". Verified starts: ").append(g7data.path("currentVerifiedStarts"))
                    .append(" / ").append(g7data.path("proposedVerifiedStarts"))
                    .append(". retried == rejected: ").append(g7data.path("retriedEqualsRejected"));
            if (!g7data.path("retriedEqualsRejected").asBoolean()) md.append("; mismatch starts: ")
                    .append(g7data.path("currentRetryMismatchStarts")).append(" / ")
                    .append(g7data.path("proposedRetryMismatchStarts"));
            md.append("\n\n");
            Map<String,Object> g1=(Map<String,Object>)model.get("G1");
            md.append("G1 overall no-regression: ").append(g1.get("overallNoRegression")).append("\n\n");
            md.append("Confirmed critical defects (each listed regardless of type):\n\n");
            for (String arm:List.of("currentCritical","proposedCritical"))
                for (Map<String,String> defect:(List<Map<String,String>>)g1.get(arm))
                    md.append("- ").append(arm).append(": ").append(defect.get("id"))
                            .append(" · ").append(defect.get("noteRef")).append(" · ").append(defect.get("type")).append('\n');
            md.append("\n```json\n")
                    .append(QualityScoreFiles.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(model)).append("\n```\n");
        }
        Files.writeString(out.resolve("gates.md"),md.toString());
    }
    private static void appendDistributions(StringBuilder md,JsonNode missing) {
        for (String dimension:List.of("byNoteLevel","byStratum")) {
            md.append("\n| ").append(dimension.equals("byNoteLevel")?"Note level":"Stratum")
                    .append(" | Paired | Unpaired |\n| --- | ---: | ---: |\n");
            JsonNode rows=missing.path(dimension);
            if (rows.isObject()) rows.fields().forEachRemaining(entry->md.append("| ").append(entry.getKey())
                    .append(" | ").append(entry.getValue().path("paired").asInt())
                    .append(" | ").append(entry.getValue().path("unpaired").asInt()).append(" |\n"));
            md.append('\n');
        }
    }
    private static Map<String,List<String>> incompletePrimary(Map<String,JsonNode> keys,Path primary) throws Exception {
        Map<String,List<String>> problems=new TreeMap<>();
        Set<String> seen=new HashSet<>();
        List<String> lines=Files.readAllLines(primary);
        for (int line=0;line<lines.size();line++) {
            if (lines.get(line).isBlank()) continue;
            JsonNode row;
            try { row=QualityScoreFiles.JSON.readTree(lines.get(line)); }
            catch (Exception e) {
                for (String arm:keys.values().stream().map(key->QualityScoreFiles.text(key,"arm")).distinct().toList())
                    problems.computeIfAbsent(arm,ignored->new ArrayList<>())
                        .add("Unparseable PRIMARY row at line "+(line+1));
                continue;
            }
            if (row==null || !row.isObject()) {
                for (String arm:keys.values().stream().map(key->QualityScoreFiles.text(key,"arm")).distinct().toList())
                    problems.computeIfAbsent(arm,ignored->new ArrayList<>())
                        .add("Invalid PRIMARY row at line "+(line+1));
                continue;
            }
            String id=row.path("id").asText();
            JsonNode key=keys.get(id);
            if (key==null) {
                for (String arm:keys.values().stream().map(value->QualityScoreFiles.text(value,"arm")).distinct().toList())
                    problems.computeIfAbsent(arm,ignored->new ArrayList<>()).add("Unknown PRIMARY score id "+id);
                continue;
            }
            String arm=QualityScoreFiles.text(key,"arm");
            if (!seen.add(id)) problems.computeIfAbsent(arm,ignored->new ArrayList<>()).add(id+": duplicate row");
            try {
                QualityScoreFiles.validate(row,QualityScoreFiles.choice(key));
                if (!"PRIMARY".equals(QualityScoreFiles.text(row,"round")))
                    throw new IllegalArgumentException("not a PRIMARY row");
            } catch (IllegalArgumentException e) {
                problems.computeIfAbsent(arm,ignored->new ArrayList<>()).add(id+": "+e.getMessage());
            }
        }
        for (var key:keys.entrySet()) if (!seen.contains(key.getKey()))
            problems.computeIfAbsent(QualityScoreFiles.text(key.getValue(),"arm"),ignored->new ArrayList<>())
                    .add(key.getKey()+": missing PRIMARY row");
        return problems;
    }
    private static Map<String,Object> incompleteModel(Map<String,List<String>> incomplete,String[] pair,JsonNode descriptors) {
        Map<String,Object> model=new LinkedHashMap<>();
        model.put("verdict","HOLD");
        model.put("reasons",List.of("Incomplete primary scoring"));
        model.put("incompleteScoring",Map.of("current",incomplete.getOrDefault(pair[0],List.of()),
                "proposed",incomplete.getOrDefault(pair[1],List.of())));
        model.put("missingness",descriptors.path("pairs").path(pair[0]+"/"+pair[1]).path("missingness"));
        for (int gate=1;gate<=8;gate++) model.put("G"+gate,Map.of("verdict","INCONCLUSIVE"));
        return model;
    }
    private static Map<String,Object> model(String a,String b,Map<String,Item> all,JsonNode descriptors) {
        List<Item> allA=arm(all,a,null),allB=arm(all,b,null);
        Set<String> modelsA=new HashSet<>(),modelsB=new HashSet<>(),hashesA=new HashSet<>(),hashesB=new HashSet<>(),packsA=new HashSet<>(),packsB=new HashSet<>();
        for (Item item:allA) { modelsA.add(item.model); hashesA.add(item.promptHash); packsA.add(item.pack); }
        for (Item item:allB) { modelsB.add(item.model); hashesB.add(item.promptHash); packsB.add(item.pack); }
        if (modelsA.size()>1 || modelsB.size()>1 || !modelsA.isEmpty() && !modelsB.isEmpty() && !modelsA.equals(modelsB))
            throw new IllegalArgumentException("Current and proposed arms must have the same single model: "+a+"/"+b);
        if (hashesA.size()>1 || hashesB.size()>1 || !hashesA.isEmpty() && hashesA.equals(hashesB))
            throw new IllegalArgumentException("Current and proposed arms must have distinct single prompt hashes: "+a+"/"+b);
        packsA.retainAll(packsB);
        List<Item> baseline=allA.stream().filter(item->packsA.contains(item.pack)).toList();
        List<Item> proposed=allB.stream().filter(item->packsA.contains(item.pack)).toList();
        JsonNode pair=descriptors.path("pairs").path(a+"/"+b);
        JsonNode da=pair.path("pairedArms").path(a), db=pair.path("pairedArms").path(b);
        Map<String,Object> result=new LinkedHashMap<>(), levels=new LinkedHashMap<>(); List<String> reasons=new ArrayList<>();
        JsonNode missingness=pair.path("missingness");
        result.put("missingness",missingness);
        if ("INCONCLUSIVE".equals(missingness.path("verdict").asText()))
            for (JsonNode reason:missingness.path("reasons")) reasons.add("Missingness: "+reason.asText());
        Metrics base=metrics(baseline,da);
        Metrics next=metrics(proposed,db);
        result.put("current",base.data); result.put("proposed",next.data);
        Map<String,Integer> typeExcess=new TreeMap<>();
        for (String type:QualityScoreFiles.WORDING_TYPES) {
            int excess=Math.max(0,next.types.getOrDefault(type,0)-base.types.getOrDefault(type,0));
            typeExcess.put(type,excess);
        }
        boolean noRegression=next.critical.size()<=base.critical.size();
        boolean g1=noRegression && typeExcess.values().stream().allMatch(v->v==0);
        gate(reasons,"G1",g1); result.put("G1",Map.of("verdict",g1?"PASS":"FAIL","overallNoRegression",noRegression,"wordingSensitiveRegressionTypeExcess",typeExcess,
                "currentCritical",base.critical,"proposedCritical",next.critical));
        boolean g2=next.mean("distractorPlausibility")>=QualityGateParameters.G2_MIN_MEAN
                && next.mean("distractorPlausibility")-base.mean("distractorPlausibility")>=QualityGateParameters.G2_MIN_GAIN;
        gate(reasons,"G2",g2); result.put("G2",g2?"PASS":"FAIL");
        boolean g3=next.mean("modeAlignment")>=QualityGateParameters.G3_MIN_MEAN;
        gate(reasons,"G3",g3); result.put("G3",g3?"PASS":"FAIL");
        gate(reasons,"G4", "PASS".equals(next.g4.verdict())); result.put("G4",next.g4);
        boolean g5=next.formatMeans.values().stream().allMatch(m -> (int)m.get("count")<QualityGateParameters.G5_FORMAT_MIN_ITEMS
                || ((Map<String,Double>)m.get("means")).values().stream().filter(Double::isFinite)
                .allMatch(v->v>=QualityGateParameters.G5_FORMAT_MIN_MEAN));
        gate(reasons,"G5",g5); result.put("G5",g5?"PASS":"FAIL");
        Map<String,Object> g6=collateral(base,next,da.path("g6"),db.path("g6"));
        gate(reasons,"G6","PASS".equals(g6.get("verdict"))); result.put("G6",g6);
        Map<String,Object> g7=multi(da.path("g7"),db.path("g7"));
        gate(reasons,"G7","PASS".equals(g7.get("verdict"))); result.put("G7",g7);
        int paired=Math.min(pair.path("pairedNotes").asInt(-1),packsA.size());
        double duplicateRate=nearDuplicateRate(db);
        String g8=paired<QualityGateParameters.G8_MIN_PAIRED_NOTES || !Double.isFinite(duplicateRate) ? "INCONCLUSIVE"
                : duplicateRate<QualityGateParameters.NEAR_DUPLICATE_START_LIMIT ? "PASS":"FAIL";
        gate(reasons,"G8","PASS".equals(g8)); result.put("G8",Map.of("verdict",g8,"pairedNotes",paired,
                "nearDuplicateStartRate",duplicateRate,"missingOrFailedSides",pair.path("missingOrFailedSides")));
        boolean escalation=next.missedCriticalAny>0;
        boolean complete=completeReReview(allB);
        if (escalation && !complete) reasons.add("Incomplete required ARM_RE_REVIEW of "+b);
        if (base.unresolved>0) reasons.add("Unresolved current-arm scores: "+base.unresolved);
        if (next.unresolved>0) reasons.add("Unresolved proposed-arm scores: "+next.unresolved);
        Map<String,Object> escalationData=new LinkedHashMap<>();
        escalationData.put("currentMissedDefectRate",base.missedRate()); escalationData.put("proposedMissedDefectRate",next.missedRate());
        escalationData.put("escalationRequired",escalation); escalationData.put("completeArmReReview",complete);
        result.put("escalation",escalationData);
        for (String level:List.of("COLLEGE","BOARD_EXAM_REVIEW")) {
            JsonNode pairLevel=pair.path("perNoteLevel").path(level);
            JsonNode lda=pairLevel.path("pairedArms").path(a),ldb=pairLevel.path("pairedArms").path(b);
            List<Item> levelBaseline=baseline.stream().filter(item->level.equals(item.level)).toList();
            List<Item> levelProposed=proposed.stream().filter(item->level.equals(item.level)).toList();
            Metrics lbase=metrics(levelBaseline,lda);
            Metrics lnext=metrics(levelProposed,ldb);
            Map<String,Object> report=new LinkedHashMap<>();
            report.put("interpretation","DESCRIPTIVE_ONLY"); report.put("current",lbase.data); report.put("proposed",lnext.data);
            Map<String,Integer> levelExcess=new TreeMap<>();
            for (String type:QualityScoreFiles.WORDING_TYPES)
                levelExcess.put(type,Math.max(0,lnext.types.getOrDefault(type,0)-lbase.types.getOrDefault(type,0)));
            report.put("G1",Map.of("currentCritical",lbase.critical,"proposedCritical",lnext.critical,"overallNoRegression",lnext.critical.size()<=lbase.critical.size(),"wordingSensitiveRegressionTypeExcess",levelExcess));
            report.put("G2",Map.of("currentMean",lbase.mean("distractorPlausibility"),"proposedMean",lnext.mean("distractorPlausibility"),
                    "difference",lnext.mean("distractorPlausibility")-lbase.mean("distractorPlausibility")));
            report.put("G3",Map.of("currentMean",lbase.mean("modeAlignment"),"proposedMean",lnext.mean("modeAlignment")));
            report.put("G4",Map.of("current",lbase.g4,"proposed",lnext.g4));
            report.put("G5",Map.of("current",lbase.formatMeans,"proposed",lnext.formatMeans));
            report.put("G6",collateral(lbase,lnext,lda.path("g6"),ldb.path("g6")));
            report.put("G7",multi(lda.path("g7"),ldb.path("g7")));
            report.put("G8",Map.of("pairedNotes",pair.path("perNoteLevel").path(level).path("pairedNotes").asInt(),
                    "nearDuplicateStartRate",nearDuplicateRate(ldb)));
            report.put("pairedBootstrap",pairedBootstrap(levelBaseline,levelProposed,lda,ldb));
            if ("BOARD_EXAM_REVIEW".equals(level)) {
                double distractorDelta=lnext.mean("distractorPlausibility")-lbase.mean("distractorPlausibility");
                double modeDelta=lnext.mean("modeAlignment")-lbase.mean("modeAlignment");
                result.put("boardExamReviewFinding",distractorDelta<=0 || modeDelta<=0
                        ? "Flat or negative BOARD_EXAM_REVIEW change: distractor="+distractorDelta+", mode="+modeDelta
                        : "Positive BOARD_EXAM_REVIEW change: distractor="+distractorDelta+", mode="+modeDelta);
            }
            levels.put(level,report);
        }
        result.put("perNoteLevel",levels);
        result.put("pairedBootstrap",pairedBootstrap(baseline,proposed,da,db));
        result.put("representativePairedNotes",representativePairedNotes(baseline,proposed));
        result.put("reasons",reasons);
        result.put("verdict","INCONCLUSIVE".equals(missingness.path("verdict").asText())
                ? "INCONCLUSIVE" : reasons.isEmpty()?"PASS":"HOLD");
        return result;
    }
    private static void gate(List<String> reasons,String name,boolean pass) { if (!pass) reasons.add(name+" did not pass"); }
    private static List<Item> arm(Map<String,Item> all,String arm,String level) {
        List<Item> rows=new ArrayList<>();
        for (Item item:all.values()) if (arm.equals(item.arm) && (level==null || level.equals(item.level))) rows.add(item);
        return rows;
    }
    private static boolean completeReReview(List<Item> items) {
        if (items.isEmpty()) return false;
        String reviewer=null;
        for (Item item:items) {
            JsonNode score=QualityScoreFiles.find(item.scores,"ARM_RE_REVIEW");
            if (score==null) return false;
            String id=QualityScoreFiles.text(score,"reviewerId");
            if (reviewer==null) reviewer=id; else if (!reviewer.equals(id)) return false;
        }
        return true;
    }
    private static final class Item {
        final String id,arm,model,promptHash,level,format,pack,note;
        final boolean choice;
        final List<JsonNode> scores;
        final JsonNode primary,review,reReview,finalScore;
        final boolean unresolved,independentFound;
        Item(JsonNode key,List<JsonNode> scores) {
            this.id=QualityScoreFiles.text(key,"id"); this.arm=QualityScoreFiles.text(key,"arm");
            this.model=QualityScoreFiles.text(key,"model");
            this.promptHash=QualityScoreFiles.text(key,"promptSha256");
            this.level=QualityScoreFiles.text(key,"noteLevel"); this.format=QualityScoreFiles.text(key,"questionFormat");
            this.pack=QualityScoreFiles.text(key,"packId"); this.note=QualityScoreFiles.text(key,"noteRef");
            this.choice=QualityScoreFiles.choice(key); this.scores=scores;
            this.primary=QualityScoreFiles.find(scores,"PRIMARY"); this.review=QualityScoreFiles.find(scores,"INDEPENDENT_REVIEW");
            this.reReview=QualityScoreFiles.find(scores,"ARM_RE_REVIEW");
            JsonNode adjudication=QualityScoreFiles.find(scores,"ADJUDICATION");
            this.unresolved=adjudication==null && ((review!=null && disagreement(primary,review))
                    || (reReview!=null && disagreement(review==null?primary:review,reReview)));
            this.independentFound=review!=null && ((!primary.path("critical").asBoolean() && review.path("critical").asBoolean())
                    || (!primary.path("seriousGiveaway").asBoolean() && review.path("seriousGiveaway").asBoolean()))
                    || reReview!=null && ((!primary.path("critical").asBoolean() && reReview.path("critical").asBoolean())
                    || (!primary.path("seriousGiveaway").asBoolean() && reReview.path("seriousGiveaway").asBoolean()));
            this.finalScore=adjudication!=null ? adjudication
                    : reReview!=null && (!unresolved || independentFound) ? reReview
                    : review!=null && (!unresolved || independentFound) ? review : primary;
        }
        private static boolean disagreement(JsonNode a,JsonNode b) {
            if (a.path("critical").asBoolean()!=b.path("critical").asBoolean()) return true;
            if (a.path("seriousGiveaway").asBoolean()!=b.path("seriousGiveaway").asBoolean()) return true;
            for (String field:List.of("distractorPlausibility","modeAlignment","cognitiveDemand","clarity","faithfulness"))
                if (a.path(field).isInt() && b.path(field).isInt() && Math.abs(a.path(field).asInt()-b.path(field).asInt())>=2) return true;
            return false;
        }
    }
    private record Metrics(Map<String,Object> data,Map<String,Integer> types,List<Map<String,String>> critical,
                           Map<String,Map<String,Object>> formatMeans,G4 g4,int missed,int missedCriticalAny,
                           int reviewedUnflagged,int unresolved,
                           Map<String,Double> means) {
        double mean(String name) { return means.getOrDefault(name,Double.NaN); }
        double missedRate() { return reviewedUnflagged==0 ? Double.NaN : (double)missed/reviewedUnflagged; }
    }
    private static Metrics metrics(List<Item> items,JsonNode descriptors) {
        Map<String,Integer> types=new TreeMap<>(); List<Map<String,String>> defects=new ArrayList<>();
        Map<String,List<Item>> formats=new TreeMap<>(); Map<String,int[]> strata=new TreeMap<>();
        Map<String,Map<String,Set<String>>> patternNotes=new HashMap<>();
        int flaggedConfirmed=0,missed=0,missedCriticalAny=0,reviewedUnflagged=0,unresolved=0,choice=0;
        for (Item item:items) {
            if (item.unresolved) unresolved++;
            JsonNode score=item.finalScore;
            boolean adjudicated=QualityScoreFiles.find(item.scores,"ADJUDICATION")!=null;
            boolean anyIndependent=item.review!=null || item.reReview!=null;
            boolean confirmedCritical=score.path("critical").asBoolean()
                    && (adjudicated || item.review!=null && item.review.path("critical").asBoolean()
                    || item.reReview!=null && item.reReview.path("critical").asBoolean());
            boolean confirmedGiveaway=item.choice && score.path("seriousGiveaway").asBoolean()
                    && (adjudicated || item.review!=null && item.review.path("seriousGiveaway").asBoolean()
                    || item.reReview!=null && item.reReview.path("seriousGiveaway").asBoolean());
            if (confirmedCritical) {
                String type=QualityScoreFiles.text(score,"criticalType"); types.merge(type,1,Integer::sum);
                defects.add(Map.of("id",item.id,"noteRef",item.note,"type",type,"arm",item.arm));
            }
            if (anyIndependent && !item.primary.path("critical").asBoolean()) {
                if (confirmedCritical) missedCriticalAny++;
                if (!item.primary.path("seriousGiveaway").asBoolean()) {
                    reviewedUnflagged++;
                    if (confirmedCritical) missed++;
                }
            }
            formats.computeIfAbsent(item.format,ignored->new ArrayList<>()).add(item);
            if (!item.choice) continue;
            choice++;
            int[] v=strata.computeIfAbsent(item.format,ignored->new int[3]);
            if (item.primary.path("seriousGiveaway").asBoolean() || item.primary.path("critical").asBoolean()) {
                if (anyIndependent && confirmedGiveaway) flaggedConfirmed++;
            } else {
                v[0]++;
                if (anyIndependent) { v[1]++; if (confirmedGiveaway) v[2]++; }
            }
            if (anyIndependent && confirmedGiveaway)
                patternNotes.computeIfAbsent(QualityScoreFiles.text(score,"giveawayPattern"),ignored->new HashMap<>())
                        .computeIfAbsent(item.note,ignored->new HashSet<>()).add(item.id);
        }
        List<String> recurringPatterns=new ArrayList<>();
        for (var pattern:patternNotes.entrySet()) {
            int count=pattern.getValue().values().stream().mapToInt(Set::size).sum();
            if (count>=QualityGateParameters.G4_RECURRING_ITEMS && pattern.getValue().size()>=QualityGateParameters.G4_RECURRING_NOTES)
                recurringPatterns.add(pattern.getKey());
        }
        recurringPatterns.sort(String::compareTo);
        G4 g4=g4(choice,flaggedConfirmed,strata,!recurringPatterns.isEmpty());
        Map<String,Double> means=means(items); Map<String,Map<String,Object>> formatMeans=new TreeMap<>();
        for (var format:formats.entrySet()) formatMeans.put(format.getKey(),Map.of("count",format.getValue().size(),"means",means(format.getValue())));
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("items",items.size()); data.put("means",means); data.put("formatMeans",formatMeans);
        data.put("criticalDefects",defects); data.put("confirmedCriticalCount",defects.size());
        data.put("G4",g4); data.put("recurringGiveawayPatterns",recurringPatterns);
        data.put("missedDefects",missed); data.put("independentlyFoundCritical",missedCriticalAny);
        data.put("reviewedUnflagged",reviewedUnflagged);
        data.put("missedDefectRate",reviewedUnflagged==0?"MISSING":(double)missed/reviewedUnflagged);
        data.put("unresolved",unresolved); data.put("descriptorsG6",descriptors.path("g6"));
        data.put("descriptorsG7",descriptors.path("g7")); data.put("nearDuplicateStartRate",nearDuplicateRate(descriptors));
        return new Metrics(data,types,defects,formatMeans,g4,missed,missedCriticalAny,reviewedUnflagged,unresolved,means);
    }
    private static Map<String,Double> means(List<Item> items) {
        Map<String,Double> result=new LinkedHashMap<>();
        for (String field:List.of("distractorPlausibility","modeAlignment","cognitiveDemand","clarity","faithfulness")) {
            double sum=0; int n=0;
            for (Item item:items) if (item.finalScore.path(field).isInt()) { sum+=item.finalScore.path(field).asInt(); n++; }
            result.put(field,n==0?Double.NaN:sum/n);
        }
        return result;
    }
    private static Map<String,Object> collateral(Metrics a,Metrics b,JsonNode da,JsonNode db) {
        Map<String,Object> deltas=new LinkedHashMap<>(); boolean pass=true, missing=false;
        for (String field:List.of("clarity","faithfulness")) {
            double delta=b.mean(field)-a.mean(field); deltas.put(field,delta);
            if (!Double.isFinite(delta)) missing=true; else if (Math.abs(delta)>QualityGateParameters.G6_SCORE_TOLERANCE) pass=false;
        }
        double delivered=rate(db,"deliveredTotal",db.path("starts").asDouble()*QualityGateParameters.REQUESTED_PER_START)
                -rate(da,"deliveredTotal",da.path("starts").asDouble()*QualityGateParameters.REQUESTED_PER_START);
        deltas.put("deliveredRate",delivered);
        if (!Double.isFinite(delivered)) missing=true; else if (Math.abs(delivered)>QualityGateParameters.G6_COUNT_RATE_POINTS) pass=false;
        for (String field:List.of("p50LatencyMs","tokensPerDeliveredQuestion")) {
            double old=da.path(field).asDouble(0), now=db.path(field).asDouble(0);
            double relative=old<=0?Double.NaN:(now-old)/old;
            deltas.put(field,Double.isFinite(relative)?relative:"MISSING");
            if (!Double.isFinite(relative)) missing=true; else if (Math.abs(relative)>QualityGateParameters.G6_RESOURCE_RELATIVE_TOLERANCE) pass=false;
        }
        deltas.put("verdict",missing?"INCONCLUSIVE":pass?"PASS":"FAIL"); return deltas;
    }
    private static double rate(JsonNode row,String numerator,double denominator) { return denominator<=0?Double.NaN:row.path(numerator).asDouble()/denominator; }
    private static double retryRate(JsonNode row) {
        return validatorRate(row,"validatorRetried",QualityGateParameters.G7_DENOMINATOR_REQUESTED);
    }
    private static Map<String,Object> multi(JsonNode a,JsonNode b) {
        double splitA=split(a),splitB=split(b),rejectA=rejection(a),rejectB=rejection(b),retryA=retryRate(a),retryB=retryRate(b);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("telemetryScope","ALL_FORMAT; generation-count log has no per-format rejection or retry breakdown");
        out.put("currentDiscardedAttempts",a.path("discardedAttempts").asInt());
        out.put("proposedDiscardedAttempts",b.path("discardedAttempts").asInt());
        out.put("currentVerifiedStarts",a.path("verifiedStarts"));
        out.put("proposedVerifiedStarts",b.path("verifiedStarts"));
        out.put("currentRetryMismatchStarts",a.path("retryMismatchStarts"));
        out.put("proposedRetryMismatchStarts",b.path("retryMismatchStarts"));
        out.put("retriedEqualsRejected",a.path("retryMismatchStarts").isEmpty() && b.path("retryMismatchStarts").isEmpty());
        out.put("currentSuccessfulStarts",a.path("starts").asInt());
        out.put("proposedSuccessfulStarts",b.path("starts").asInt());
        out.put("currentTelemetryMissingStarts",a.path("telemetryMissingStarts").asInt());
        out.put("proposedTelemetryMissingStarts",b.path("telemetryMissingStarts").asInt());
        out.put("currentMultiSelectThreeCorrectShare",Double.isFinite(splitA)?splitA:"MISSING");
        out.put("proposedMultiSelectThreeCorrectShare",Double.isFinite(splitB)?splitB:"MISSING");
        boolean excessiveMissing=missingTelemetry(a)||missingTelemetry(b);
        out.put("currentAllFormatValidatorRejectionRate",excessiveMissing||!Double.isFinite(rejectA)?"MISSING":rejectA);
        out.put("proposedAllFormatValidatorRejectionRate",excessiveMissing||!Double.isFinite(rejectB)?"MISSING":rejectB);
        out.put("currentAllFormatValidatorRetryRate",excessiveMissing||!Double.isFinite(retryA)?"MISSING":retryA);
        out.put("proposedAllFormatValidatorRetryRate",excessiveMissing||!Double.isFinite(retryB)?"MISSING":retryB);
        out.put("telemetryVerdict",excessiveMissing?"INCONCLUSIVE":"OBSERVED");
        String splitVerdict=pointVerdict(splitA,splitB,false);
        String rejectionVerdict=pointVerdict(rejectA,rejectB,excessiveMissing);
        String retryVerdict=pointVerdict(retryA,retryB,excessiveMissing);
        out.put("multiSelectThreeCorrectShareVerdict",splitVerdict);
        out.put("allFormatValidatorRejectionVerdict",rejectionVerdict);
        out.put("allFormatValidatorRetryVerdict",retryVerdict);
        out.put("absolutePercentagePointDifferences",Map.of("multiSelectThreeCorrectShare",Math.abs(splitA-splitB)*100,
                "allFormatValidatorRejectionRate",Math.abs(rejectA-rejectB)*100,
                "allFormatValidatorRetryRate",Math.abs(retryA-retryB)*100));
        List<String> verdicts=List.of(splitVerdict,rejectionVerdict,retryVerdict);
        out.put("verdict",verdicts.contains("FAIL")?"FAIL":verdicts.contains("INCONCLUSIVE")?"INCONCLUSIVE":"PASS");
        return out;
    }
    private static String pointVerdict(double current,double proposed,boolean missing) {
        if (missing || !Double.isFinite(current) || !Double.isFinite(proposed)) return "INCONCLUSIVE";
        return Math.abs(current-proposed)<=QualityGateParameters.G7_NOISE_POINTS+1e-12?"PASS":"FAIL";
    }
    private static double split(JsonNode row) {
        double two=row.path("multiSelectTwoCorrect").asDouble(0),three=row.path("multiSelectThreeCorrect").asDouble(0);
        return two+three==0?Double.NaN:three/(two+three);
    }
    private static double rejection(JsonNode row) {
        return validatorRate(row,"validatorRejected",QualityGateParameters.G7_DENOMINATOR_REQUESTED);
    }
    static double validatorRate(JsonNode row,String numerator,boolean requestedDenominator) {
        double denominator=requestedDenominator?row.path("validatorRequested").asDouble(0)
                :row.path("validatorAccepted").asDouble(0)+row.path("validatorRejected").asDouble(0);
        return denominator==0?Double.NaN:row.path(numerator).asDouble(0)/denominator;
    }
    private static boolean missingTelemetry(JsonNode row) {
        int starts=row.path("starts").asInt(0);
        return starts==0 || (double)row.path("telemetryMissingStarts").asInt()/starts>.20;
    }
    private static double nearDuplicateRate(JsonNode descriptor) {
        JsonNode per=descriptor.path("perNote"); int starts=0,duplicate=0;
        if (per.isObject()) for (JsonNode note:per) {
            starts++; if (note.path("nearDuplicateQuestions").asInt()>0) duplicate++;
        }
        return starts==0?Double.NaN:(double)duplicate/starts;
    }
    private static Map<String,Object> pairedBootstrap(List<Item> a,List<Item> b,JsonNode da,JsonNode db) {
        Map<String,Object> result=new LinkedHashMap<>();
        for (String field:List.of("distractorPlausibility","modeAlignment","cognitiveDemand","clarity","faithfulness")) {
            Map<String,List<Item>> left=new TreeMap<>(),right=new TreeMap<>();
            for (Item item:a) left.computeIfAbsent(item.pack,ignored->new ArrayList<>()).add(item);
            for (Item item:b) right.computeIfAbsent(item.pack,ignored->new ArrayList<>()).add(item);
            List<Double> differences=new ArrayList<>();
            for (String pack:left.keySet()) if (right.containsKey(pack)) {
                double lm=means(left.get(pack)).get(field),rm=means(right.get(pack)).get(field);
                if (Double.isFinite(lm)&&Double.isFinite(rm)) differences.add(rm-lm);
            }
            result.put(field,bootstrap(differences,field));
        }
        JsonNode left=da.path("perNote"),right=db.path("perNote");
        for (String field:List.of("deliveredRate","allFormatValidatorRetryRate","latencyMs","tokensPerDeliveredQuestion",
                "multiSelectThreeShare","allFormatValidatorRejectionRate","nearDuplicateStart")) {
            List<Double> differences=new ArrayList<>();
            if (left.isObject()) left.fieldNames().forEachRemaining(pack -> {
                JsonNode x=left.path(pack).path(field),y=right.path(pack).path(field);
                if (x.isNumber() && y.isNumber()) differences.add(y.asDouble()-x.asDouble());
            });
            result.put(field,bootstrap(differences,field));
        }
        return result;
    }
    private static Map<String,Object> bootstrap(List<Double> differences,String field) {
        if (differences.isEmpty()) return Map.of("pairedNotes",0,"interval","MISSING");
        double[] values=new double[QualityGateParameters.BOOTSTRAP_REPLICATES];
        Random random=new Random(QualityGateParameters.BOOTSTRAP_SEED ^ field.hashCode());
        for (int i=0;i<values.length;i++) {
            double sum=0; for (int j=0;j<differences.size();j++) sum+=differences.get(random.nextInt(differences.size()));
            values[i]=sum/differences.size();
        }
        Arrays.sort(values);
        double alpha=(1-QualityGateParameters.BOOTSTRAP_CONFIDENCE)/2;
        return Map.of("pairedNotes",differences.size(),"meanDifference",differences.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
                "bootstrap95Lower",values[(int)(values.length*alpha)],"bootstrap95Upper",values[(int)(values.length*(1-alpha))]);
    }
    private static Map<String,Object> representativePairedNotes(List<Item> baseline,List<Item> proposed) {
        Map<String,List<Item>> left=new TreeMap<>(),right=new TreeMap<>();
        for (Item item:baseline) left.computeIfAbsent(item.pack,ignored->new ArrayList<>()).add(item);
        for (Item item:proposed) right.computeIfAbsent(item.pack,ignored->new ArrayList<>()).add(item);
        String best=null,worst=null; double largest=0,smallest=0;
        for (String pack:left.keySet()) if (right.containsKey(pack)) {
            double difference=means(right.get(pack)).get("distractorPlausibility")
                    -means(left.get(pack)).get("distractorPlausibility");
            if (Double.isFinite(difference) && difference>largest) { largest=difference; best=pack; }
            if (Double.isFinite(difference) && difference<smallest) { smallest=difference; worst=pack; }
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("selectionRule","Largest positive and largest negative paired per-note distractor-mean difference; packId lexical tie-break");
        result.put("improvement",representative(best,largest,left,right));
        result.put("failure",representative(worst,smallest,left,right));
        return result;
    }
    private static Object representative(String pack,double difference,Map<String,List<Item>> left,Map<String,List<Item>> right) {
        if (pack==null) return "NONE";
        return Map.of("packId",pack,"noteRef",left.get(pack).getFirst().note,"difference",difference,
                "currentIds",left.get(pack).stream().map(item->item.id).toList(),
                "proposedIds",right.get(pack).stream().map(item->item.id).toList());
    }
}
