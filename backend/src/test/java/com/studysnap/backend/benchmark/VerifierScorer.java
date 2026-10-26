package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Offline Stage 1 scorer. Thresholds are from wording-and-pass-rules.md §4.3. */
public class VerifierScorer {
    record Config(Path solDir,Path lunaDir,Path gold,Path scoreOut,double replacementFailure,boolean r104Strict) {
        static Config system() {
            double q=Double.parseDouble(System.getProperty("llm.verifier.replacementFailure","0.15"));
            if (q<0 || q>1) throw new IllegalArgumentException("replacementFailure must be 0..1");
            String r104=System.getProperty("llm.verifier.r104","strict");
            if (!Set.of("strict","report").contains(r104)) throw new IllegalArgumentException("r104 must be strict or report");
            return new Config(StatementPairVerifierBenchmark.localPath("llm.verifier.solDir"),
                    StatementPairVerifierBenchmark.localPath("llm.verifier.lunaDir"),
                    StatementPairVerifierBenchmark.localPath("llm.verifier.gold"),
                    StatementPairVerifierBenchmark.localPath("llm.verifier.scoreOut"),q,"strict".equals(r104));
        }
    }
    @Test void score() throws Exception { assumeTrue(Boolean.getBoolean("llm.verifier.score")); score(Config.system()); }
    static ObjectNode score(Config c) throws Exception {
        ObjectMapper mapper=new ObjectMapper();
        JsonNode sm=mapper.readTree(Files.readAllBytes(c.solDir().resolve("manifest.json")));
        JsonNode lm=mapper.readTree(Files.readAllBytes(c.lunaDir().resolve("manifest.json")));
        if (!sm.path("complete").asBoolean() || !lm.path("complete").asBoolean()) throw new IllegalArgumentException("incomplete verifier run");
        for (String key:List.of("goldSha256","notesSha256","promptVersion","repeats"))
            if (!sm.path(key).equals(lm.path(key))) throw new IllegalArgumentException("manifest mismatch: "+key);
        if (!sm.path("goldSha256").asText().equals(StatementPairVerifierBenchmark.sha(c.gold()))) throw new IllegalArgumentException("goldSha256 mismatch");
        Map<String,JsonNode> sol=load(c.solDir().resolve("results.jsonl"),mapper),luna=load(c.lunaDir().resolve("results.jsonl"),mapper);
        List<JsonNode> gold=new ArrayList<>();
        for (String line:Files.readAllLines(c.gold())) gold.add(mapper.readTree(line));
        List<Row> rows=new ArrayList<>();
        for (JsonNode item:gold) {
            String id=item.path("id").asText(),stratum=item.path("stratum").asText();
            int count="P_SEEN".equals(stratum)?sm.path("repeats").asInt():1;
            for (int repeat=1;repeat<=count;repeat++) {
                String key=id+"#"+repeat;
                if (!sol.containsKey(key) || !luna.containsKey(key)) throw new IllegalArgumentException("missing gold id/repeat "+key);
                JsonNode s=sol.get(key),l=luna.get(key);
                if (!stratum.equals(s.path("stratum").asText()) || !stratum.equals(l.path("stratum").asText())) throw new IllegalArgumentException("stratum mismatch "+key);
                boolean sa=accept(s),la=accept(l);
                if (sa && la && !s.path("normalizedPair").equals(l.path("normalizedPair"))) throw new IllegalStateException("accepted normalized pairs disagree");
                rows.add(new Row(item,s,l,sa,la,sa&&la));
            }
        }
        if (sol.size()!=rows.size() || luna.size()!=rows.size()) throw new IllegalArgumentException("unexpected result rows");
        ObjectNode output=mapper.createObjectNode(); ObjectNode metrics=output.putObject("metrics");
        List<String> markdown=new ArrayList<>(); markdown.add("# Statement-pair verifier Stage 1 score\n");
        markdown.add("| Criterion | Value (numerator / denominator) | Classification |\n|---|---:|---|");
        List<String> classifications=new ArrayList<>();
        // S-A1: each seen item, independently, across all repeats.
        for (String caseId:List.of("r1-04","r1-06","r1-07")) {
            List<Row> cases=rows.stream().filter(r->"P_SEEN".equals(r.stratum()) && r.gold().path("provenance").asText().contains(caseId)).toList();
            for (String leg:List.of("F","Sol","Luna")) {
                long rejected=cases.stream().filter(r->!accepted(r,leg)).count();
                // S-A1 classifies the Option F decision only; each checker alone is judged on the two
                // clear cases by S-A9 (wording-and-pass-rules.md §4.3), so the Sol and Luna rows are reported.
                String classification=!"F".equals(leg) || (caseId.equals("r1-04") && !c.r104Strict())?"REPORT":cases.size()!=3?"GRAY":
                        caseId.equals("r1-04")?(rejected>=2?"PASS":"KILL"):(rejected==3?"PASS":"KILL");
                put(metrics,markdown,classifications,"S-A1 "+caseId+" "+leg,rejected,cases.size(),classification);
            }
        }
        metricRate(rows,metrics,markdown,classifications,"S-A2", "P_CONSTRUCTED","F",true,18,16,20);
        metricRate(rows,metrics,markdown,classifications,"S-A3", "P_UNSEEN","F",true,18,16,20);
        int nSize=(int)gold.stream().filter(g->"N".equals(g.path("stratum").asText())).count();
        int[] nt=nThresholds(nSize);
        output.put("nSize",nSize).put("nThresholdRule",nSize==78?"signed counts":"floor of signed rates (Part 10, item 3)");
        for (String leg:List.of("Sol","Luna")) metricRate(rows,metrics,markdown,classifications,"S-A4a "+leg,"N",leg,false,nt[0],nt[1],nSize);
        metricRate(rows,metrics,markdown,classifications,"S-A4b","N","F",false,nt[2],nt[3],nSize);
        metricRate(rows,metrics,markdown,classifications,"S-A4c","P_CONSTRUCTED_CONTROL","F",false,2,4,20);
        List<Row> adjudicated=stratum(rows,"P_ADJUDICATED");
        if (!adjudicated.isEmpty()) put(metrics,markdown,classifications,"P_ADJUDICATED rejected (F)",
                adjudicated.stream().filter(r->!r.fAccept()).count(),adjudicated.size(),"REPORT");
        List<Row> positives=rows.stream().filter(r->Set.of("P_CONSTRUCTED","P_UNSEEN").contains(r.stratum()) &&
                !r.gold().path("keyedPair").equals(r.gold().path("goldPair"))).toList();
        long misses=positives.stream().filter(Row::fAccept).count();
        put(metrics,markdown,classifications,"S-A5",misses,positives.size(),positives.size()==40?lower(misses,4,8):"GRAY");
        List<Row> uncertain=stratum(rows,"U"); long uReject=uncertain.stream().filter(r->!r.fAccept()).count();
        List<Row> n=stratum(rows,"N");
        long silent=0,silentCorrect=0,totalStatements=0;
        for (Row row:n) for (JsonNode result:List.of(row.sol(),row.luna())) {
            JsonNode supports=result.path("sourceSupport"); totalStatements+=2;
            for (int i=0;i<supports.size();i++) if ("SILENT".equals(supports.get(i).asText())) {
                silent++; if (result.path("normalizedPair").size()==2 &&
                        result.path("normalizedPair").get(i).asText().equals(row.gold().path("goldPair").get(i).asBoolean()?"TRUE":"FALSE")) silentCorrect++;
            }
        }
        // §4.3 sizes U at "about 6", so the pass bar is at least 5 in 6 of whatever U holds.
        String a6=!uncertain.isEmpty() && uReject*6>=uncertain.size()*5L && (silent==0 || silentCorrect*10>=silent*9)?"PASS":
                silent>0 && silentCorrect*10<silent*9?"KILL":"GRAY";
        put(metrics,markdown,classifications,"S-A6 U rejected",uReject,uncertain.size(),a6);
        put(metrics,markdown,classifications,"S-A6 SILENT rate",silent,totalStatements,"REPORT");
        put(metrics,markdown,classifications,"S-A6 SILENT accuracy",silentCorrect,silent,"REPORT");
        for (var run:List.of(new Run("Sol",sm,sol),new Run("Luna",lm,luna))) {
            int calls=run.manifest().path("logicalCalls").asInt(uniqueCalls(run.results()).size());
            int bad=run.manifest().path("failedCalls").asInt();
            put(metrics,markdown,classifications,"S-A7 "+run.name(),bad,calls,rateClass(bad,calls,.02,.05));
            long quotes=0,unverified=0;
            for (JsonNode result:run.results().values()) for (int index=0;index<2;index++) {
                JsonNode judgment=result.path("rawJudgment").path(index==0?"statement1":"statement2");
                if (Set.of("SUPPORTED","CONTRADICTED").contains(judgment.path("sourceSupport").asText())) {
                    quotes++;
                    JsonNode verified=result.path("quoteVerified").path(index);
                    if (!verified.isBoolean()) throw new IllegalArgumentException("result row lacks quoteVerified: "+result.path("id").asText());
                    if (!verified.asBoolean()) unverified++;
                }
            }
            put(metrics,markdown,classifications,"S-A8 "+run.name(),unverified,quotes,rateClass(unverified,quotes,.05,.10));
        }
        for (String leg:List.of("Sol","Luna")) {
            List<Row> clear=rows.stream().filter(r->"P_SEEN".equals(r.stratum()) &&
                    (r.gold().path("provenance").asText().contains("r1-06") || r.gold().path("provenance").asText().contains("r1-07"))).toList();
            boolean clearPass=clear.size()==6 && clear.stream().noneMatch(r->accepted(r,leg));
            List<Row> constructed=stratum(rows,"P_CONSTRUCTED"); long detected=constructed.stream().filter(r->!accepted(r,leg)).count();
            List<Row> normals=stratum(rows,"N"); long falseReject=normals.stream().filter(r->!accepted(r,leg)).count();
            String classification=!clearPass || falseReject>nt[1]?"KILL":constructed.size()==20 && normals.size()==nSize && detected>=18 && falseReject<=nt[0]?"PASS":"GRAY";
            put(metrics,markdown,classifications,"S-A9 "+leg,detected,constructed.size(),classification);
        }
        List<Row> natural=rows.stream().filter(r->Set.of("N","N_CAVEAT").contains(r.stratum())).toList();
        long naturalReject=natural.stream().filter(r->!r.fAccept()).count();
        double p=natural.isEmpty()?0:((double)naturalReject/natural.size())*c.replacementFailure();
        Map<String,Integer> starts=new LinkedHashMap<>(); for (Row r:natural) starts.merge(r.gold().path("batchId").asText(),1,Integer::sum);
        double one=0,failed=0;
        for (int count:starts.values()) { one+=count*p*Math.pow(1-p,count-1); failed+=1-Math.pow(1-p,count)-count*p*Math.pow(1-p,count-1); }
        if (!starts.isEmpty()) { one/=starts.size(); failed/=starts.size(); }
        ObjectNode projection=metrics.putObject("S-A10"); projection.put("naturalRejected",naturalReject).put("naturalItems",natural.size())
                .put("starts",starts.size()).put("replacementFailure",c.replacementFailure()).put("pOneShort",one).put("pFailed",failed);
        long naturalExpected=gold.stream().filter(g->Set.of("N","N_CAVEAT").contains(g.path("stratum").asText())).count();
        String a10=natural.size()!=naturalExpected?"GRAY":one<=.03 && failed<=.005?"PASS":failed>.01?"KILL":"GRAY";
        projection.put("classification",a10); classifications.add(a10);
        markdown.add("| S-A10 | "+naturalReject+" / "+natural.size()+"; one short "+one+"; failed "+failed+" | "+a10+" |");
        // One latency per start (call pair), not per item: a batch's items all repeat its call latency, so
        // per-item percentiles would weight larger batches more heavily.
        Map<String,Long> perCall=new LinkedHashMap<>();
        for (Row r:rows) {
            String call=r.sol().path("callId").asText(r.gold().path("batchId").asText()+"-r"+r.sol().path("repeat").asInt());
            perCall.merge(call,Math.max(r.sol().path("latencyMs").asLong(),r.luna().path("latencyMs").asLong()),Math::max);
        }
        List<Long> latencies=perCall.values().stream().sorted().toList();
        long p90=StatementPairVerifierBenchmark.percentile(latencies,.9);
        ObjectNode latency=metrics.putObject("S-A11"); latency.put("numerator",p90).put("denominator",1000).put("p50Ms",StatementPairVerifierBenchmark.percentile(latencies,.5))
                .put("p90Ms",p90).put("maxMs",latencies.isEmpty()?0:latencies.getLast());
        String a11=p90<=30000?"PASS":p90>45000?"KILL":"GRAY"; latency.put("classification",a11); classifications.add(a11);
        markdown.add("| S-A11 | "+p90+" / 1000 ms per second | "+a11+" |");
        Map<String,Double> batchCost=new HashMap<>();
        for (Row row:rows) for (JsonNode result:List.of(row.sol(),row.luna()))
            if (result.hasNonNull("callId")) batchCost.putIfAbsent(result.path("callId").asText()+"/"+(result==row.sol()?"S":"L"),result.path("callCostUsd").asDouble());
        Map<String,Double> combined=new HashMap<>();
        for (var e:batchCost.entrySet()) combined.merge(e.getKey().substring(0,e.getKey().lastIndexOf('/')),e.getValue(),Double::sum);
        List<Double> costs=combined.values().stream().sorted().toList(); double cost90=costs.isEmpty()?0:costs.get(Math.max(0,(int)Math.ceil(.9*costs.size())-1));
        String a12=cost90<=.03?"PASS":"GRAY";
        ObjectNode cost=metrics.putObject("S-A12"); cost.put("numerator",cost90).put("denominator",1).put("p90Usd",cost90).put("classification",a12); classifications.add(a12);
        markdown.add("| S-A12 | $"+cost90+" / batch | "+a12+" |");
        String overall=classifications.stream().allMatch("PASS"::equals)?"PASS":classifications.contains("KILL")?"KILL":"GRAY";
        output.put("overall",overall); markdown.add("\nOverall: **"+overall+"**\n");
        Files.createDirectories(c.scoreOut().toAbsolutePath().getParent());
        Files.writeString(c.scoreOut(),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output));
        Files.writeString(c.scoreOut().resolveSibling("score.md"),String.join("\n",markdown)+"\n");
        System.out.println(String.join("\n",markdown));
        return output;
    }
    private record Row(JsonNode gold,JsonNode sol,JsonNode luna,boolean solAccept,boolean lunaAccept,boolean fAccept) {
        String stratum(){return gold.path("stratum").asText();}
    }
    private record Run(String name,JsonNode manifest,Map<String,JsonNode> results) {}
    private static boolean accept(JsonNode r){return "ACCEPT".equals(r.path("decision").asText());}
    private static boolean accepted(Row r,String leg){return switch(leg){case "F"->r.fAccept();case "Sol"->r.solAccept();default->r.lunaAccept();};}
    private static List<Row> stratum(List<Row> rows,String name){return rows.stream().filter(r->name.equals(r.stratum())).toList();}
    private static Map<String,JsonNode> load(Path file,ObjectMapper mapper) throws Exception {
        Map<String,JsonNode> rows=new LinkedHashMap<>();
        for (String line:Files.readAllLines(file)) {JsonNode row=mapper.readTree(line);String key=row.path("id").asText()+"#"+row.path("repeat").asInt();
            if (rows.putIfAbsent(key,row)!=null) throw new IllegalArgumentException("duplicate result "+key);}
        return rows;
    }
    private static Set<String> uniqueCalls(Map<String,JsonNode> rows){Set<String> ids=new HashSet<>();for(JsonNode r:rows.values())if(r.hasNonNull("callId"))ids.add(r.path("callId").asText());return ids;}
    private static boolean contains(JsonNode array,String value){for(JsonNode n:array)if(value.equals(n.asText()))return true;return false;}
    static String lower(long value,long pass,long kill){return value<=pass?"PASS":value>kill?"KILL":"GRAY";}
    static String rateClass(long value,long total,double pass,double kill){return total==0?"GRAY":value/(double)total<=pass?"PASS":value/(double)total>kill?"KILL":"GRAY";}
    /** {alone pass, alone kill, union pass, union kill} for N. 78 keeps the signed counts; any other size uses the
     *  owner-approved floor of the signed rates 5%, 10%, 8% and 12% (wording doc Part 10, item 3). */
    static int[] nThresholds(int n) {
        if (n==78) return new int[]{4,8,6,9};
        return new int[]{(int)Math.floor(.05*n),(int)Math.floor(.10*n),(int)Math.floor(.08*n),(int)Math.floor(.12*n)};
    }
    private static void metricRate(List<Row> rows,ObjectNode metrics,List<String> markdown,List<String> classes,String name,String stratum,String leg,boolean detection,int pass,int kill,int expected) {
        List<Row> group=stratum(rows,stratum);long count=group.stream().filter(r->!accepted(r,leg)).count();
        String clazz=group.size()!=expected?"GRAY":detection?(count>=pass?"PASS":count<kill?"KILL":"GRAY"):lower(count,pass,kill);
        put(metrics,markdown,classes,name,count,group.size(),clazz);
    }
    private static void put(ObjectNode metrics,List<String> markdown,List<String> classes,String name,long numerator,long denominator,String classification) {
        ObjectNode m=metrics.putObject(name);m.put("numerator",numerator).put("denominator",denominator).put("classification",classification);
        markdown.add("| "+name+" | "+numerator+" / "+denominator+" | "+classification+" |");
        if (!"REPORT".equals(classification)) classes.add(classification);
    }
}
