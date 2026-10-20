package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerifierScorerTest {
    @TempDir Path temp;
    ObjectMapper mapper=new ObjectMapper();
    @Test void optionFJointMissAndR104Report() throws Exception {
        Fixture f=fixture();
        JsonNode strict=VerifierScorer.score(config(f,true));
        assertThat(strict.path("metrics").path("S-A4a Sol").path("numerator").asInt()).isEqualTo(1);
        assertThat(strict.path("metrics").path("S-A4a Luna").path("numerator").asInt()).isZero();
        assertThat(strict.path("metrics").path("S-A4b").path("numerator").asInt()).isEqualTo(1);
        assertThat(strict.path("metrics").path("S-A5").path("numerator").asInt()).isEqualTo(1);
        assertThat(strict.path("metrics").path("S-A1 r1-04 F").path("classification").asText()).isEqualTo("KILL");
        JsonNode report=VerifierScorer.score(config(f,false));
        assertThat(report.path("metrics").path("S-A1 r1-04 F").path("classification").asText()).isEqualTo("REPORT");
        assertThat(Files.readString(f.score().resolveSibling("score.md"))).contains("Overall:","S-A10");
    }
    @Test void exactUnderfillProjectionAndBoundaries() throws Exception {
        Fixture f=fixture();JsonNode score=VerifierScorer.score(config(f,false));
        // N starts have one and two items. One of three N items is rejected by F; q=.3, so p=.1.
        assertThat(score.path("metrics").path("S-A10").path("pOneShort").asDouble()).isCloseTo(.14,org.assertj.core.data.Offset.offset(1e-9));
        assertThat(score.path("metrics").path("S-A10").path("pFailed").asDouble()).isCloseTo(.005,org.assertj.core.data.Offset.offset(1e-9));
        assertThat(VerifierScorer.lower(4,4,8)).isEqualTo("PASS");
        assertThat(VerifierScorer.lower(6,4,8)).isEqualTo("GRAY");
        assertThat(VerifierScorer.lower(9,4,8)).isEqualTo("KILL");
        assertThat(VerifierScorer.rateClass(2,100,.02,.05)).isEqualTo("PASS");
        assertThat(VerifierScorer.rateClass(3,100,.02,.05)).isEqualTo("GRAY");
        assertThat(VerifierScorer.rateClass(6,100,.02,.05)).isEqualTo("KILL");
    }
    @Test void completeSyntheticSetPassesAndUnionBoundaryGradesGrayAndKill() throws Exception {
        Fixture f=completeFixture();
        assertThat(VerifierScorer.score(config(f,true)).path("overall").asText()).isEqualTo("PASS");
        // Split false rejections across the checkers: their individual counts stay within pass,
        // while F's union crosses the 6/78 pass boundary.
        for(int i=0;i<7;i++) reject(f,i,i<4?"sol":"luna");
        JsonNode gray=VerifierScorer.score(config(f,true));
        assertThat(gray.path("metrics").path("S-A4b").path("classification").asText()).isEqualTo("GRAY");
        assertThat(gray.path("overall").asText()).isEqualTo("GRAY");
        for(int i=7;i<10;i++) reject(f,i,i<9?"sol":"luna");
        JsonNode kill=VerifierScorer.score(config(f,true));
        assertThat(kill.path("metrics").path("S-A4b").path("classification").asText()).isEqualTo("KILL");
        assertThat(kill.path("overall").asText()).isEqualTo("KILL");
    }
    private Fixture completeFixture() throws Exception { return completeFixture(6); }
    private Fixture completeFixture(int ambiguous) throws Exception {
        Path gold=temp.resolve("complete-gold.jsonl"),sol=temp.resolve("complete-sol"),luna=temp.resolve("complete-luna");
        Files.createDirectories(sol);Files.createDirectories(luna);
        StringBuilder g=new StringBuilder(),s=new StringBuilder(),l=new StringBuilder();
        for(int i=0;i<78;i++)add(g,s,l,"n"+i,"N","n-start"+(i/2),true,true,true,1);
        for(int i=0;i<10;i++)add(g,s,l,"c"+i,"N_CAVEAT","c-start"+(i/2),true,true,true,1);
        for(int i=0;i<20;i++)add(g,s,l,"p"+i,"P_CONSTRUCTED","p"+i,false,false,false,1);
        for(int i=0;i<20;i++)add(g,s,l,"u"+i,"P_UNSEEN","u"+i,false,false,false,1);
        for(int i=0;i<20;i++)add(g,s,l,"t"+i,"P_CONSTRUCTED_CONTROL","t"+i,true,true,true,1);
        for(int i=0;i<ambiguous;i++)add(g,s,l,"amb"+i,"U","amb"+i,true,false,false,1);
        for(String id:new String[]{"r1-04","r1-06","r1-07"})add(g,s,l,id,"P_SEEN",id,true,false,false,3);
        Files.writeString(gold,g.toString());Files.writeString(sol.resolve("results.jsonl"),s.toString());Files.writeString(luna.resolve("results.jsonl"),l.toString());
        String sha=StatementPairVerifierBenchmark.sha(gold);
        for(Path dir:new Path[]{sol,luna})Files.writeString(dir.resolve("manifest.json"),mapper.writeValueAsString(mapper.createObjectNode()
                .put("goldSha256",sha).put("notesSha256","synthetic").put("promptVersion","draft").put("repeats",3).put("complete",true).put("invalidCalls",0).put("failedCalls",0)));
        return new Fixture(gold,sol,luna,temp.resolve("complete-score.json"));
    }
    @Test void singleCheckerAcceptingR104IsReportedNotFailedWhenOptionFRejectsIt() throws Exception {
        Fixture f=completeFixture();
        acceptForAllRepeats(f,"r1-04","luna");
        JsonNode score=VerifierScorer.score(config(f,true));
        assertThat(score.path("metrics").path("S-A1 r1-04 Luna").path("classification").asText()).isEqualTo("REPORT");
        assertThat(score.path("metrics").path("S-A1 r1-04 F").path("classification").asText()).isEqualTo("PASS");
        assertThat(score.path("overall").asText()).isEqualTo("PASS");
    }
    @Test void ambiguousStratumIsGradedOnItsActualSize() throws Exception {
        JsonNode score=VerifierScorer.score(config(completeFixture(5),true));
        assertThat(score.path("metrics").path("S-A6 U rejected").path("denominator").asInt()).isEqualTo(5);
        assertThat(score.path("metrics").path("S-A6 U rejected").path("classification").asText()).isEqualTo("PASS");
    }
    @Test void unverifiedQuoteIsCountedEvenWhenAnEarlierRuleDecidedTheVerdict() throws Exception {
        Fixture f=completeFixture();
        // Six N rows carry an invented quote but were normalized as UNCERTAIN, so no UNVERIFIED_EVIDENCE reason.
        rewrite(f.sol(),row->{ if(row.path("id").asText().matches("n[0-5]")){
            ((com.fasterxml.jackson.databind.node.ArrayNode)row.path("quoteVerified")).set(0,mapper.getNodeFactory().booleanNode(false));
            ((com.fasterxml.jackson.databind.node.ArrayNode)row.path("normalizationReasons")).set(0,mapper.getNodeFactory().textNode("UNCERTAIN"));}});
        JsonNode score=VerifierScorer.score(config(f,true));
        assertThat(score.path("metrics").path("S-A8 Sol").path("numerator").asInt()).isEqualTo(6);
    }
    @Test void latencyPercentileIsPerCallNotPerItem() throws Exception {
        Fixture f=completeFixture();
        // Nine slow two-item starts (n0..n17): 9 of 119 calls (7.6%, under the p90 line) but 18 of 163 result
        // rows (11%, over it). Per-call weighting keeps p90 fast; per-item weighting would report 60 s.
        rewrite(f.sol(),row->{ String id=row.path("id").asText();
            if(id.matches("n\\d+") && Integer.parseInt(id.substring(1))<18) row.put("latencyMs",60000);});
        JsonNode score=VerifierScorer.score(config(f,true));
        assertThat(score.path("metrics").path("S-A11").path("maxMs").asLong()).isEqualTo(60000);
        assertThat(score.path("metrics").path("S-A11").path("p90Ms").asLong()).isEqualTo(100);
        assertThat(score.path("metrics").path("S-A11").path("classification").asText()).isEqualTo("PASS");
        // Quote matching used by quoteVerified, independent of normalization.
        String note="The ‘crystal’ – phase uses  two steps.";
        assertThat(VerifierDecision.quoteFound(mapper.readTree("{\"evidenceQuote\":\"'crystal' - phase\"}"),note)).isTrue();
        assertThat(VerifierDecision.quoteFound(mapper.readTree("{\"evidenceQuote\":\"invented claim\"}"),note)).isFalse();
        assertThat(VerifierDecision.quoteFound(mapper.readTree("{\"evidenceQuote\":null}"),note)).isFalse();
    }
    private void rewrite(Path dir,java.util.function.Consumer<ObjectNode> change) throws Exception {
        Path file=dir.resolve("results.jsonl");
        StringBuilder lines=new StringBuilder();
        for(String line:Files.readAllLines(file)){ObjectNode row=(ObjectNode)mapper.readTree(line);change.accept(row);lines.append(mapper.writeValueAsString(row)).append('\n');}
        Files.writeString(file,lines.toString());
    }
    private void acceptForAllRepeats(Fixture f,String id,String model) throws Exception {
        Path file=(model.equals("sol")?f.sol():f.luna()).resolve("results.jsonl");
        StringBuilder lines=new StringBuilder();
        for(String line:Files.readAllLines(file)) {
            ObjectNode row=(ObjectNode)mapper.readTree(line);
            if(row.path("id").asText().equals(id))row.put("decision","ACCEPT");
            lines.append(mapper.writeValueAsString(row)).append('\n');
        }
        Files.writeString(file,lines.toString());
    }
    private void reject(Fixture f,int index,String model) throws Exception {
        Path file=(model.equals("sol")?f.sol():f.luna()).resolve("results.jsonl");
        StringBuilder lines=new StringBuilder();
        for(String line:Files.readAllLines(file)) {
            ObjectNode row=(ObjectNode)mapper.readTree(line);
            if(row.path("id").asText().equals("n"+index))row.put("decision","REJECT");
            lines.append(mapper.writeValueAsString(row)).append('\n');
        }
        Files.writeString(file,lines.toString());
    }
    @Test void refusesMismatchIncompleteAndMissingResults() throws Exception {
        Fixture f=fixture();
        ObjectNode manifest=(ObjectNode)mapper.readTree(Files.readString(f.luna().resolve("manifest.json")));
        manifest.put("promptVersion","different");Files.writeString(f.luna().resolve("manifest.json"),mapper.writeValueAsString(manifest));
        assertThatThrownBy(()->VerifierScorer.score(config(f,true))).hasMessageContaining("manifest mismatch");
        manifest.put("promptVersion","draft").put("complete",false);Files.writeString(f.luna().resolve("manifest.json"),mapper.writeValueAsString(manifest));
        assertThatThrownBy(()->VerifierScorer.score(config(f,true))).hasMessageContaining("incomplete");
        manifest.put("complete",true);Files.writeString(f.luna().resolve("manifest.json"),mapper.writeValueAsString(manifest));
        Files.writeString(f.luna().resolve("results.jsonl"),"");
        assertThatThrownBy(()->VerifierScorer.score(config(f,true))).hasMessageContaining("missing gold id");
    }
    private record Fixture(Path gold,Path sol,Path luna,Path score) {}
    private VerifierScorer.Config config(Fixture f,boolean strict){return new VerifierScorer.Config(f.sol(),f.luna(),f.gold(),f.score(),.3,strict);}
    private Fixture fixture() throws Exception {
        Path gold=temp.resolve("gold.jsonl"),sol=temp.resolve("sol"),luna=temp.resolve("luna");Files.createDirectories(sol);Files.createDirectories(luna);
        StringBuilder g=new StringBuilder(),s=new StringBuilder(),l=new StringBuilder();
        add(g,s,l,"n1","N","one",true,true,true,1);
        add(g,s,l,"n2","N","two",true,false,true,1);
        add(g,s,l,"n3","N","two",true,true,true,1);
        add(g,s,l,"p1","P_CONSTRUCTED","p",false,true,true,1);
        add(g,s,l,"r1-04","P_SEEN","seen",true,true,true,3);
        Files.writeString(gold,g.toString());Files.writeString(sol.resolve("results.jsonl"),s.toString());Files.writeString(luna.resolve("results.jsonl"),l.toString());
        String sha=StatementPairVerifierBenchmark.sha(gold);
        for(Path dir:new Path[]{sol,luna})Files.writeString(dir.resolve("manifest.json"),mapper.writeValueAsString(mapper.createObjectNode()
                .put("goldSha256",sha).put("notesSha256","synthetic").put("promptVersion","draft").put("repeats",3).put("complete",true).put("invalidCalls",0).put("failedCalls",0)));
        return new Fixture(gold,sol,luna,temp.resolve("score.json"));
    }
    private void add(StringBuilder gold,StringBuilder sol,StringBuilder luna,String id,String stratum,String batch,boolean keyEqualsGold,boolean sa,boolean la,int repeats) throws Exception {
        ObjectNode item=mapper.createObjectNode().put("id",id).put("stratum",stratum).put("batchId",batch).put("provenance",id);
        item.putArray("keyedPair").add(true).add(true);if ("U".equals(stratum)) item.putNull("goldPair"); else item.putArray("goldPair").add(keyEqualsGold).add(true);gold.append(mapper.writeValueAsString(item)).append('\n');
        for(int r=1;r<=repeats;r++) {sol.append(mapper.writeValueAsString(result(item,r,sa,"S"))).append('\n');luna.append(mapper.writeValueAsString(result(item,r,la,"L"))).append('\n');}
    }
    private ObjectNode result(JsonNode gold,int repeat,boolean accept,String model) {
        ObjectNode n=mapper.createObjectNode().put("id",gold.path("id").asText()).put("stratum",gold.path("stratum").asText())
                .put("repeat",repeat).put("decision",accept?"ACCEPT":"REJECT").put("latencyMs",100)
                .put("callId",gold.path("batchId").asText()+"-r"+repeat).put("callCostUsd",.001);
        n.putArray("normalizedPair").add("TRUE").add("TRUE");n.putArray("sourceSupport").add("SILENT").add("SILENT");
        n.putArray("normalizationReasons").addNull().addNull();
        n.putArray("quoteVerified").add(true).add(true);
        ObjectNode raw=n.putObject("rawJudgment"); for(String part:new String[]{"statement1","statement2"})raw.putObject(part).put("sourceSupport","SUPPORTED");
        return n;
    }
}
