package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.util.LlmResponseUtils;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in, local verifier leg. No Spring context or database. */
public class StatementPairVerifierBenchmark {
    static final String VERSION = "v0.175.0-draft-1";
    static final Set<String> STRATA = Set.of("N","N_CAVEAT","P_SEEN","P_UNSEEN","P_CONSTRUCTED","P_CONSTRUCTED_CONTROL","U");
    record Item(String id,String stratum,String batchId,String noteId,String provenance,
                JsonNode keyedPair,JsonNode goldPair,StatementPairExtractor.Pair pair) {}
    record Config(String model,Path gold,Path notes,Path outDir,Path promptDir,int repeats,int maxCalls,
                  BigDecimal inPrice,BigDecimal outPrice,BigDecimal spendStop,String key) {
        static Config system() {
            String key=requiredKey(System.getenv("LLM_API_KEY"));
            int repeats=Integer.parseInt(System.getProperty("llm.verifier.repeats","1"));
            int maxCalls=Integer.parseInt(System.getProperty("llm.verifier.maxCalls","200"));
            if (repeats<1 || maxCalls<1) throw new IllegalArgumentException("repeats and maxCalls must be positive");
            BigDecimal stop=checkedStop(required("llm.verifier.spendStopUsd"));
            BigDecimal in=new BigDecimal(required("llm.verifier.priceInPerM"));
            BigDecimal out=new BigDecimal(required("llm.verifier.priceOutPerM"));
            if (in.signum()<0 || out.signum()<0) throw new IllegalArgumentException("prices must be nonnegative");
            Path gold=localPath("llm.verifier.gold"); Path notes=localPath("llm.verifier.notes"); Path outDir=localPath("llm.verifier.outDir");
            return new Config(required("llm.verifier.model"),gold,notes,outDir,
                    Path.of(System.getProperty("llm.verifier.promptDir","src/test/resources/benchmark/verifier")),
                    repeats,maxCalls,in,out,stop,key);
        }
    }
    static Path localPath(String property) {
        Path path=Path.of(required(property)).toAbsolutePath().normalize();
        Path root=Path.of("target/llm-benchmark/verifier").toAbsolutePath().normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException(property+" must be under "+root);
        return path;
    }
    static String required(String property) {
        String value=System.getProperty(property);
        if (value==null || value.isBlank()) throw new IllegalArgumentException(property+" is required");
        return value;
    }
    static String requiredKey(String key) {
        if (key==null || key.isBlank()) throw new IllegalArgumentException("LLM_API_KEY is required");
        return key;
    }
    static BigDecimal checkedStop(String raw) {
        BigDecimal stop=new BigDecimal(raw);
        if (stop.signum()<=0 || stop.compareTo(new BigDecimal("3.50"))>0)
            throw new IllegalArgumentException("llm.verifier.spendStopUsd must be > 0 and <= 3.50");
        return stop;
    }
    @Test void benchmark() throws Exception {
        assumeTrue(Boolean.getBoolean("llm.verifier"));
        run(Config.system(),null);
    }
    static String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    static void run(Config c, ClientHttpRequestFactory factory) throws Exception {
        requiredKey(c.key()); checkedStop(c.spendStop().toPlainString());
        if (Files.exists(c.outDir().resolve("results.jsonl"))) throw new IllegalArgumentException("outDir already contains results.jsonl");
        ObjectMapper mapper=new ObjectMapper();
        VerifierRequestBuilder builder=new VerifierRequestBuilder(mapper,c.promptDir());
        JsonNode notes=mapper.readTree(Files.readAllBytes(c.notes()));
        if (!notes.isObject()) throw new IllegalArgumentException("notes must be an object");
        Map<String,List<Item>> batches=new LinkedHashMap<>();
        Set<String> ids=new java.util.HashSet<>();
        int lineNo=0;
        for (String line:Files.readAllLines(c.gold())) {
            lineNo++;
            try {
                JsonNode node=mapper.readTree(line);
                if (!node.isObject()) throw new IllegalArgumentException("not an object");
                String id=string(node,"id"),stratum=string(node,"stratum"),batch=string(node,"batchId"),noteId=string(node,"noteId");
                String question=string(node,"question"),provenance=string(node,"provenance");
                if (!STRATA.contains(stratum) || !ids.add(id)) throw new IllegalArgumentException("unknown stratum or duplicate id");
                pair(node.path("keyedPair"));
                JsonNode gold=node.path("goldPair");
                if (gold.isNull() != "U".equals(stratum)) throw new IllegalArgumentException("goldPair/U mismatch");
                if (!gold.isNull()) pair(gold);
                JsonNode note=notes.path(noteId);
                if (!note.isObject() || !note.path("noteText").isTextual()) throw new IllegalArgumentException("missing noteId or noteText");
                for (String field:List.of("summary","domainContext","subject","learnerLevel"))
                    if (note.has(field) && !(note.path(field).isNull() || note.path(field).isTextual())) throw new IllegalArgumentException("invalid note field "+field);
                var item=new Item(id,stratum,batch,noteId,provenance,node.path("keyedPair"),gold,StatementPairExtractor.extract(question));
                List<Item> group=batches.computeIfAbsent(batch,k->new ArrayList<>());
                if (!group.isEmpty() && !group.getFirst().noteId().equals(noteId)) throw new IllegalArgumentException("mixed noteId in batchId");
                group.add(item);
            } catch (Exception ex) { throw new IllegalArgumentException("gold preflight line "+lineNo+": "+ex.getMessage(),ex); }
        }
        if (batches.isEmpty()) throw new IllegalArgumentException("gold file is empty");
        Files.createDirectories(c.outDir());
        MeteringInterceptor meter=new MeteringInterceptor(mapper,c.outDir(),c.model(),c.inPrice(),c.outPrice(),c.spendStop(),c.maxCalls());
        StudySnapProperties props=new StudySnapProperties();
        SimpleClientHttpRequestFactory productionFactory=new SimpleClientHttpRequestFactory();
        productionFactory.setConnectTimeout(java.time.Duration.ofSeconds(props.getLlm().getApi().getConnectTimeoutSeconds()));
        productionFactory.setReadTimeout(java.time.Duration.ofSeconds(props.getLlm().getApi().getReadTimeoutSeconds()));
        RestClient client=RestClient.builder().requestFactory(factory==null?productionFactory:factory)
                .baseUrl(props.getLlm().getApi().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION,"Bearer "+c.key())
                .defaultHeader(HttpHeaders.CONTENT_TYPE,MediaType.APPLICATION_JSON_VALUE).requestInterceptor(meter).build();
        ObjectNode manifest=mapper.createObjectNode().put("harnessVersion",VERSION).put("model",c.model())
                .put("goldSha256",sha(c.gold())).put("notesSha256",sha(c.notes()))
                .put("promptVersion",builder.promptVersion()).put("repeats",c.repeats()).put("startTime",Instant.now().toString());
        List<ObjectNode> rows=new ArrayList<>(); boolean complete=true; int consecutiveHttp=0,invalidCalls=0,failedCalls=0,logicalCalls=0;
        outer: for (int repeat=1;repeat<=c.repeats();repeat++) for (var entry:batches.entrySet()) {
            int currentRepeat=repeat;
            List<Item> due=entry.getValue().stream().filter(i->currentRepeat==1 || "P_SEEN".equals(i.stratum())).toList();
            if (due.isEmpty()) continue;
            List<Item> sent=due.stream().filter(i->i.pair()!=null).toList();
            for (Item item:due) if (item.pair()==null) {
                ObjectNode row=VerifierDecision.decide(item,null,"",mapper); row.put("repeat",repeat);
                row.put("latencyMs",0).put("callAttempts",0).put("callCostUsd",0); rows.add(row);
            }
            if (sent.isEmpty()) continue;
            ObjectNode request=builder.build(c.model(),notes.path(sent.getFirst().noteId()),sent);
            JsonNode answer=null; int attempts=0; long latency=0; BigDecimal cost=BigDecimal.ZERO; boolean invalidThisCall=false;
            logicalCalls++;
            meter.begin(entry.getKey()+"-r"+repeat,"V");
            for (int attempt=1;attempt<=2;attempt++) {
                attempts++;
                int before=meter.calls().size();
                try {
                    String response=client.post().uri("/responses").body(mapper.writeValueAsString(request)).retrieve().body(String.class);
                    JsonNode envelope=mapper.readTree(response);
                    String output=singleOutput(envelope);
                    answer=VerifierDecision.validate(output,sent.size(),mapper);
                    consecutiveHttp=0;
                } catch (MeteringInterceptor.SpendStop stop) { complete=false; break outer; }
                catch (com.fasterxml.jackson.core.JsonProcessingException ex) { consecutiveHttp=0; invalidThisCall=true; }
                catch (org.springframework.web.client.RestClientException | java.io.IOException ex) {
                    consecutiveHttp++; if (consecutiveHttp>=3) { complete=false; break outer; }
                } catch (IllegalArgumentException ex) { consecutiveHttp=0; invalidThisCall=true; }
                for (var call:meter.calls().subList(before,meter.calls().size())) {
                    latency+=call.latencyMs(); cost=cost.add(call.conservativeUsd());
                }
                if (answer!=null) break;
            }
            if (answer==null) { failedCalls++; if (invalidThisCall) invalidCalls++; }
            for (int i=0;i<sent.size();i++) {
                Item item=sent.get(i); JsonNode judgment=null;
                if (answer!=null) for (JsonNode result:answer.path("results"))
                    if (result.path("itemId").asText().equals(String.valueOf(i+1))) judgment=result;
                ObjectNode row=VerifierDecision.decide(item,judgment,notes.path(item.noteId()).path("noteText").asText(),mapper);
                row.put("repeat",repeat).put("latencyMs",latency).put("callAttempts",attempts).put("callCostUsd",cost);
                row.put("callId",entry.getKey()+"-r"+repeat); rows.add(row);
            }
        }
        if (meter.unmetered()) complete=false;
        manifest.put("endTime",Instant.now().toString()).put("complete",complete).put("invalidCalls",invalidCalls).put("failedCalls",failedCalls).put("logicalCalls",logicalCalls);
        Files.writeString(c.outDir().resolve("manifest.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
        StringBuilder jsonl=new StringBuilder(); for (var row:rows) jsonl.append(mapper.writeValueAsString(row)).append('\n');
        Files.writeString(c.outDir().resolve("results.jsonl"),jsonl.toString());
        writeSummary(c.outDir(),rows,meter,invalidCalls,failedCalls);
    }
    private static void pair(JsonNode node) {
        if (!node.isArray() || node.size()!=2 || !node.get(0).isBoolean() || !node.get(1).isBoolean())
            throw new IllegalArgumentException("pair must contain exactly two booleans");
    }
    private static String string(JsonNode node,String key) {
        if (!node.path(key).isTextual() || node.path(key).asText().isBlank()) throw new IllegalArgumentException("missing "+key);
        return node.path(key).asText();
    }
    static String singleOutput(JsonNode envelope) {
        if (envelope.path("output").isArray()) for (JsonNode item:envelope.path("output"))
            for (JsonNode part:item.path("content")) if ("refusal".equals(part.path("type").asText())) throw new IllegalArgumentException("refusal");
        int texts=0;
        for (JsonNode item:envelope.path("output")) for (JsonNode part:item.path("content"))
            if ("output_text".equals(part.path("type").asText())) texts++;
        if (texts>1) throw new IllegalArgumentException("multiple output texts");
        return LlmResponseUtils.findOutputJson(envelope).orElseThrow(()->new IllegalArgumentException("zero output texts"));
    }
    private static void writeSummary(Path dir,List<ObjectNode> rows,MeteringInterceptor meter,int invalid,int failed) throws Exception {
        StringBuilder csv=new StringBuilder("stratum,accept,reject,UNCERTAIN,SELF_CONTRADICTORY,UNVERIFIED_EVIDENCE,MISMATCH,EXTRACTION_FAILED,CALL_FAILED,invalidCalls,failedCalls,latencyP50Ms,latencyP90Ms,latencyMaxMs,conservativeUsd\n");
        for (String stratum:STRATA) {
            List<ObjectNode> group=rows.stream().filter(r->stratum.equals(r.path("stratum").asText())).toList();
            if (group.isEmpty()) continue;
            long accept=group.stream().filter(r->"ACCEPT".equals(r.path("decision").asText())).count();
            csv.append(stratum).append(',').append(accept).append(',').append(group.size()-accept);
            for (String reason:List.of("UNCERTAIN","SELF_CONTRADICTORY","UNVERIFIED_EVIDENCE","MISMATCH","EXTRACTION_FAILED","CALL_FAILED"))
                csv.append(',').append(group.stream().filter(r->reason.equals(r.path("primaryReason").asText())).count());
            Map<String,ObjectNode> callsById=new LinkedHashMap<>();
            for (ObjectNode row:group) if (row.hasNonNull("callId")) callsById.putIfAbsent(row.path("callId").asText(),row);
            List<Long> times=callsById.values().stream().map(r->r.path("latencyMs").asLong()).sorted().toList();
            csv.append(',').append(invalid).append(',').append(failed).append(',').append(percentile(times,.5))
                    .append(',').append(percentile(times,.9)).append(',').append(times.isEmpty()?0:times.getLast()).append(',');
            BigDecimal cost=callsById.values().stream().map(r->r.path("callCostUsd").decimalValue()).reduce(BigDecimal.ZERO,BigDecimal::add);
            csv.append(cost).append('\n');
        }
        Files.writeString(dir.resolve("summary.csv"),csv.toString());
    }
    static long percentile(List<Long> sorted,double p) { return sorted.isEmpty()?0:sorted.get(Math.max(0,(int)Math.ceil(p*sorted.size())-1)); }
}
