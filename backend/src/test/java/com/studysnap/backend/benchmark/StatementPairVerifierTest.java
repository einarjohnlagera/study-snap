package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatementPairVerifierTest {
    @TempDir Path temp;
    ObjectMapper mapper=new ObjectMapper();
    @Test void extractsFormatsAndFailsClosed() {
        assertThat(StatementPairExtractor.extract("Statement 1: Alpha.\nStatement 2: Beta.\nWhich is correct?"))
                .isEqualTo(new StatementPairExtractor.Pair("Alpha.","Beta."));
        assertThat(StatementPairExtractor.extract("statement i: Alpha. statement ii: Beta. Which of the statements is/are correct?"))
                .isEqualTo(new StatementPairExtractor.Pair("Alpha.","Beta."));
        assertThat(StatementPairExtractor.extract("Statement 1: A Statement 2: B"))
                .isEqualTo(new StatementPairExtractor.Pair("A","B"));
        for(String stem:List.of("Statement 1: A","Statement 2: B Statement 1: A","Statement 1: Statement 2: B","Statement 1: A Statement 2: Which is correct?"))
            assertThat(StatementPairExtractor.extract(stem)).isNull();
    }
    @Test void extractsStemsWithLiteralBackslashNSeparators() {
        // Seen in 2 of 31 Luna stems: the model wrote the two characters backslash and n, not a newline.
        var pair=StatementPairExtractor.extract("Statement 1: Alpha holds.\\nStatement 2: Beta holds.\\nWhich is correct?");
        assertThat(pair).isNotNull();
        assertThat(pair.statement1()).isEqualTo("Alpha holds.");
        assertThat(pair.statement2()).isEqualTo("Beta holds.");
    }
    @Test void quoteRuleStripsEnclosingMarksAndVerifiesEveryFragment() throws Exception {
        String note="Officials must uphold trust in public institutions. They support transparent and accountable governance.";
        var m=new com.fasterxml.jackson.databind.ObjectMapper();
        assertThat(VerifierDecision.quoteFound(m.readTree("{\"evidenceQuote\":\"“uphold trust in public institutions”\"}"),note)).isTrue();
        assertThat(VerifierDecision.quoteFound(m.readTree("{\"evidenceQuote\":\"“uphold trust in public institutions” ; “transparent and accountable governance”\"}"),note)).isTrue();
        assertThat(VerifierDecision.quoteFound(m.readTree("{\"evidenceQuote\":\"uphold trust in public institutions ; promotes open data\"}"),note)).isFalse();
        assertThat(VerifierDecision.quoteFound(m.readTree("{\"evidenceQuote\":\"upholds public trust in institutions\"}"),note)).isFalse();
        assertThat(VerifierDecision.quoteFound(m.readTree("{\"evidenceQuote\":\" ; \"}"),note)).isFalse();
    }
    @Test void normalizesInRuleOrderAndQuotes() throws Exception {
        String note="The ‘crystal’ – phase uses  two steps.";
        assertThat(VerifierDecision.normalize(j("SUPPORTED","TRUE",false,"the 'CRYSTAL' - phase uses two steps."),note).verdict()).isEqualTo("TRUE");
        assertThat(VerifierDecision.normalize(j("SILENT","UNCERTAIN",false,null),note).reason()).isEqualTo("UNCERTAIN");
        assertThat(VerifierDecision.normalize(j("SUPPORTED","FALSE",true,"absent"),note).reason()).isEqualTo("UNCERTAIN");
        assertThat(VerifierDecision.normalize(j("SUPPORTED","FALSE",false,"absent"),note).reason()).isEqualTo("SELF_CONTRADICTORY");
        assertThat(VerifierDecision.normalize(j("CONTRADICTED","TRUE",false,"absent"),note).reason()).isEqualTo("SELF_CONTRADICTORY");
        assertThat(VerifierDecision.normalize(j("SUPPORTED","TRUE",false,"\"marker\" - value"),"A “marker” — value").verdict()).isEqualTo("TRUE");
        assertThat(VerifierDecision.normalize(j("SUPPORTED","TRUE",false,"absent"),note).reason()).isEqualTo("UNVERIFIED_EVIDENCE");
        assertThat(VerifierDecision.normalize(j("CONTRADICTED","FALSE",false,null),note).reason()).isEqualTo("UNVERIFIED_EVIDENCE");
    }
    @Test void itemDecisionMatchesKeyAndOrdersReasons() throws Exception {
        JsonNode gold=gold("A","N","b","note","Statement 1: A Statement 2: B","p");
        var item=new StatementPairVerifierBenchmark.Item("A","N","b","note","p",gold.path("keyedPair"),gold.path("goldPair"),new StatementPairExtractor.Pair("A","B"));
        JsonNode exact=mapper.readTree(payload(1)).path("results").get(0);
        assertThat(VerifierDecision.decide(item,exact,"Quartz is blue. Iron is red.",mapper).path("decision").asText()).isEqualTo("ACCEPT");
        ObjectNode mismatched=(ObjectNode)exact.deepCopy();
        ((ObjectNode)mismatched.path("statement2")).put("sourceSupport","SILENT").put("verdict","FALSE").putNull("evidenceQuote");
        assertThat(VerifierDecision.decide(item,mismatched,"Quartz is blue. Iron is red.",mapper).path("primaryReason").asText()).isEqualTo("MISMATCH");
        ((ObjectNode)mismatched.path("statement1")).put("sourceSupport","SUPPORTED").put("verdict","FALSE");
        assertThat(VerifierDecision.decide(item,mismatched,"Quartz is blue. Iron is red.",mapper).path("primaryReason").asText()).isEqualTo("SELF_CONTRADICTORY");
        ((ObjectNode)mismatched.path("statement2")).put("verdict","UNCERTAIN");
        assertThat(VerifierDecision.decide(item,mismatched,"Quartz is blue. Iron is red.",mapper).path("primaryReason").asText()).isEqualTo("UNCERTAIN");
    }
    @Test void validatesAllWholeCallFailures() throws Exception {
        String good=payload(1);
        assertThat(VerifierDecision.validate(good,1,mapper).path("results")).hasSize(1);
        for(String invalid:List.of("not json", "{\"results\":[]}",payload(2),
                good.replace("\"itemId\":\"1\"","\"itemId\":\"3\""),
                payload(2).replace("\"itemId\":\"2\"","\"itemId\":\"1\""),
                good.replace("\"verdict\":\"TRUE\"","\"verdict\":\"MAYBE\"")))
            assertThatThrownBy(()->VerifierDecision.validate(invalid,1,mapper)).isInstanceOf(Exception.class);
    }
    @Test void blindnessBatchingAndRepeats() throws Exception {
        var fixture=fixture(List.of(gold("SENTINEL_ID", "P_SEEN","batch","note", "SENTINEL_STEM Statement 1: Quartz is blue. Statement 2: Iron is red. Which is correct? A. CHOICE_SENTINEL B. other", "SENTINEL_PROVENANCE"),
                gold("N2","N","batch","note","Statement 1: Quartz is blue. Statement 2: Iron is red. Which is correct?","p")));
        List<MockClientHttpRequest> requests=new ArrayList<>();AtomicInteger calls=new AtomicInteger();
        run(fixture,3,requests,calls,List.of(payload(2),payload(1),payload(1)));
        assertThat(requests).hasSize(3);
        JsonNode request=mapper.readTree(requests.getFirst().getBodyAsBytes());
        String text=new String(requests.getFirst().getBodyAsBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(text).contains("Quartz is blue","Iron is red").doesNotContain("SENTINEL_ID","SENTINEL_STEM","SENTINEL_PROVENANCE","CHOICE_SENTINEL","keyedPair","goldPair","P_SEEN");
        assertThat(request.has("reasoning")).isFalse();
        assertThat(request.path("text").path("format").path("strict").asBoolean()).isTrue();
        assertThat(Files.readAllLines(fixture.out().resolve("results.jsonl"))).hasSize(4);
    }
    @Test void failuresRetryOnceAndFailClosed() throws Exception {
        for(String bad:List.of("not json","{\"results\":[]}",payload(2),
                payload(1).replace("\"itemId\":\"1\"","\"itemId\":\"9\""),
                payload(1).replace("\"verdict\":\"TRUE\"","\"verdict\":\"MAYBE\""),
                payload(2).replace("\"itemId\":\"2\"","\"itemId\":\"1\""),
                "ZERO_OUTPUT","MULTIPLE_OUTPUT","REFUSAL","HTTP500")) {
            var f=fixture(List.of(gold("A","N","b","note","Statement 1: Quartz is blue. Statement 2: Iron is red.","p")));
            AtomicInteger calls=new AtomicInteger();
            run(f,1,new ArrayList<>(),calls,List.of(bad,bad));
            assertThat(calls.get()).isEqualTo(2);
            assertThat(result(f).path("primaryReason").asText()).isEqualTo("CALL_FAILED");
        }
        var f=fixture(List.of(gold("B","N","b","note","Statement 1: Quartz is blue. Statement 2: Iron is red.","p")));
        AtomicInteger calls=new AtomicInteger();run(f,1,new ArrayList<>(),calls,List.of("not json",payload(1)));
        assertThat(calls.get()).isEqualTo(2); assertThat(result(f).path("decision").asText()).isEqualTo("ACCEPT");
    }
    @Test void preflightAndSpendSafetyUseZeroHttp() throws Exception {
        var f=fixture(List.of(gold("A","N","b","note","Statement 1: A Statement 2: B","p")));
        var c=config(f,1);
        AtomicInteger calls=new AtomicInteger();
        assertThatThrownBy(()->StatementPairVerifierBenchmark.run(new StatementPairVerifierBenchmark.Config(c.model(),c.gold(),c.notes(),c.outDir(),c.promptDir(),1,10,c.inPrice(),c.outPrice(),c.spendStop()," "),
                (uri,method)->{calls.incrementAndGet();throw new AssertionError();})).hasMessageContaining("LLM_API_KEY");
        Files.writeString(f.gold(),"{oops\n");
        assertThatThrownBy(()->StatementPairVerifierBenchmark.run(c,(uri,method)->{calls.incrementAndGet();throw new AssertionError();})).hasMessageContaining("preflight");
        Files.createDirectories(f.out()); Files.writeString(f.out().resolve("results.jsonl"),"");
        assertThatThrownBy(()->StatementPairVerifierBenchmark.run(c,(uri,method)->{calls.incrementAndGet();throw new AssertionError();})).hasMessageContaining("results.jsonl");
        assertThat(calls.get()).isZero();
        assertThatThrownBy(()->StatementPairVerifierBenchmark.checkedStop("3.51")).hasMessageContaining("3.50");
    }
    private record Fixture(Path gold,Path notes,Path out) {}
    private Fixture fixture(List<ObjectNode> rows) throws Exception {
        Path dir=Files.createTempDirectory(temp,"case");Path gold=dir.resolve("gold.jsonl"),notes=dir.resolve("notes.json");
        StringBuilder data=new StringBuilder();for(var r:rows)data.append(mapper.writeValueAsString(r)).append('\n');Files.writeString(gold,data.toString());
        Files.writeString(notes,"{\"note\":{\"noteText\":\"Quartz is blue. Iron is red.\",\"summary\":\"Invented facts\",\"domainContext\":null,\"subject\":\"Minerals\",\"learnerLevel\":\"COLLEGE\"}}");
        return new Fixture(gold,notes,dir.resolve("out"));
    }
    private ObjectNode gold(String id,String stratum,String batch,String note,String question,String provenance) {
        ObjectNode g=mapper.createObjectNode().put("id",id).put("stratum",stratum).put("batchId",batch).put("noteId",note).put("question",question).put("provenance",provenance);
        g.putArray("keyedPair").add(true).add(true);g.putArray("goldPair").add(true).add(true);return g;
    }
    private StatementPairVerifierBenchmark.Config config(Fixture f,int repeats) {
        return new StatementPairVerifierBenchmark.Config("test",f.gold(),f.notes(),f.out(),Path.of("src/test/resources/benchmark/verifier"),repeats,100,BigDecimal.ONE,BigDecimal.ONE,new BigDecimal("3.50"),"fake-key");
    }
    private void run(Fixture f,int repeats,List<MockClientHttpRequest> requests,AtomicInteger calls,List<String> responses) throws Exception {
        var factory=(org.springframework.http.client.ClientHttpRequestFactory)(uri,method)->{
            int index=calls.getAndIncrement();MockClientHttpRequest req=new MockClientHttpRequest(method,uri);requests.add(req);
            String response=responses.get(index);int status=response.equals("HTTP500")?500:200;
            String body; try { body=status==500?"{\"error\":{\"message\":\"failure\"}}":envelope(response); } catch(Exception ex) { throw new java.io.IOException(ex); }
            req.setResponse(new MockClientHttpResponse(body.getBytes(java.nio.charset.StandardCharsets.UTF_8),status));return req;
        };
        StatementPairVerifierBenchmark.run(config(f,repeats),factory);
    }
    private String envelope(String response) throws Exception {
        ObjectNode n=mapper.createObjectNode();
        if (response.equals("ZERO_OUTPUT")) n.putArray("output");
        else if (response.equals("MULTIPLE_OUTPUT")) { var output=n.putArray("output"); output.addObject().putArray("content").addObject().put("type","output_text").put("text","{}"); output.addObject().putArray("content").addObject().put("type","output_text").put("text","{}"); }
        else if (response.equals("REFUSAL")) n.putArray("output").addObject().putArray("content").addObject().put("type","refusal");
        else n.put("output_text",response);
        n.putObject("usage").put("input_tokens",100).put("output_tokens",100);return mapper.writeValueAsString(n);
    }
    private String payload(int count) throws Exception {
        ObjectNode root=mapper.createObjectNode();var results=root.putArray("results");
        for(int i=1;i<=count;i++){ObjectNode row=results.addObject().put("itemId",String.valueOf(i));row.set("statement1",j("SUPPORTED","TRUE",false,"Quartz is blue."));row.set("statement2",j("SUPPORTED","TRUE",false,"Iron is red."));}
        return mapper.writeValueAsString(root);
    }
    private ObjectNode j(String support,String verdict,boolean conflict,String quote) {
        ObjectNode n=mapper.createObjectNode().put("sourceSupport",support).put("reason","Invented principle.").put("noteConflictsWithStandardKnowledge",conflict).put("verdict",verdict);
        if(quote==null)n.putNull("evidenceQuote");else n.put("evidenceQuote",quote);return n;
    }
    private JsonNode result(Fixture f) throws Exception{return mapper.readTree(Files.readAllLines(f.out().resolve("results.jsonl")).getFirst());}
}
