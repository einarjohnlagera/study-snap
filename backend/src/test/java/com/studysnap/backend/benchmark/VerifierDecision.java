package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class VerifierDecision {
    private static final List<String> ORDER = List.of("UNCERTAIN","SELF_CONTRADICTORY","UNVERIFIED_EVIDENCE","MISMATCH","EXTRACTION_FAILED","CALL_FAILED");
    record Normalized(String verdict, String reason) {}
    static Normalized normalize(JsonNode judgment, String note) {
        String verdict = judgment.path("verdict").asText();
        String support = judgment.path("sourceSupport").asText();
        if ("UNCERTAIN".equals(verdict) || judgment.path("noteConflictsWithStandardKnowledge").asBoolean())
            return new Normalized("UNCERTAIN","UNCERTAIN");
        if (("SUPPORTED".equals(support) && "FALSE".equals(verdict)) ||
                ("CONTRADICTED".equals(support) && "TRUE".equals(verdict)))
            return new Normalized("UNCERTAIN","SELF_CONTRADICTORY");
        if (!"SILENT".equals(support) && (judgment.path("evidenceQuote").isNull() ||
                !canonical(note).contains(canonical(judgment.path("evidenceQuote").asText())) ||
                canonical(judgment.path("evidenceQuote").asText()).isEmpty()))
            return new Normalized("UNCERTAIN","UNVERIFIED_EVIDENCE");
        return new Normalized(verdict,null);
    }
    static boolean quoteFound(JsonNode judgment,String note) {
        JsonNode quote=judgment.path("evidenceQuote");
        if (!quote.isTextual()) return false;
        String canonicalQuote=canonical(quote.asText());
        return !canonicalQuote.isEmpty() && canonical(note).contains(canonicalQuote);
    }
    private static String canonical(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replace('‘','\'').replace('’','\'').replace('“','"').replace('”','"')
                .replace('–','-').replace('—','-').replaceAll("\\s+"," ").trim();
    }
    static JsonNode validate(String text, int count, ObjectMapper mapper) throws Exception {
        JsonNode root = mapper.readTree(text);
        if (!root.isObject() || root.size() != 1 || !root.path("results").isArray() || root.path("results").size() != count)
            throw new IllegalArgumentException("Invalid verifier result shape or count");
        Set<String> ids = new HashSet<>();
        for (JsonNode result : root.path("results")) {
            if (!result.isObject() || result.size() != 3 || !result.path("itemId").isTextual() ||
                    !ids.add(result.path("itemId").asText())) throw new IllegalArgumentException("Duplicate or invalid itemId");
            for (String key : List.of("statement1","statement2")) validateJudgment(result.path(key));
        }
        for (int i=1;i<=count;i++) if (!ids.contains(String.valueOf(i))) throw new IllegalArgumentException("Missing or extra itemId");
        return root;
    }
    private static void validateJudgment(JsonNode node) {
        if (!node.isObject() || node.size()!=5 || !Set.of("SUPPORTED","CONTRADICTED","SILENT").contains(node.path("sourceSupport").asText()) ||
                !node.has("evidenceQuote") || !(node.path("evidenceQuote").isTextual() || node.path("evidenceQuote").isNull()) ||
                !node.path("reason").isTextual() || !node.path("noteConflictsWithStandardKnowledge").isBoolean() ||
                !Set.of("TRUE","FALSE","UNCERTAIN").contains(node.path("verdict").asText()))
            throw new IllegalArgumentException("Invalid judgment");
    }
    static ObjectNode decide(StatementPairVerifierBenchmark.Item item, JsonNode result, String note, ObjectMapper mapper) {
        ObjectNode row = mapper.createObjectNode();
        row.put("id",item.id()).put("stratum",item.stratum()).put("batchId",item.batchId());
        row.put("provenance",item.provenance());
        row.set("keyedPair",item.keyedPair().deepCopy());
        row.set("goldPair",item.goldPair().deepCopy());
        List<String> reasons = new ArrayList<>();
        if (item.pair() == null) reasons.add("EXTRACTION_FAILED");
        else if (result == null) reasons.add("CALL_FAILED");
        else {
            JsonNode one = result.path("statement1"), two = result.path("statement2");
            Normalized a = normalize(one,note), b = normalize(two,note);
            var pair = row.putArray("normalizedPair"); pair.add(a.verdict()); pair.add(b.verdict());
            var supports = row.putArray("sourceSupport"); supports.add(one.path("sourceSupport").asText()); supports.add(two.path("sourceSupport").asText());
            var normalizationReasons=row.putArray("normalizationReasons");
            if (a.reason()==null) normalizationReasons.addNull(); else normalizationReasons.add(a.reason());
            if (b.reason()==null) normalizationReasons.addNull(); else normalizationReasons.add(b.reason());
            row.set("rawJudgment",result.deepCopy());
            // Recorded independently of normalize(): an UNCERTAIN or self-contradictory judgment can also carry
            // an invented quote, and S-A8 must count it even though an earlier rule decided the verdict.
            var quoteVerified=row.putArray("quoteVerified");
            for (JsonNode judgment:List.of(one,two)) {
                if ("SILENT".equals(judgment.path("sourceSupport").asText())) quoteVerified.addNull();
                else quoteVerified.add(quoteFound(judgment,note));
            }
            if (a.reason()!=null) reasons.add(a.reason()); if (b.reason()!=null && !reasons.contains(b.reason())) reasons.add(b.reason());
            if (reasons.isEmpty() && (!pair.get(0).asText().equals(item.keyedPair().get(0).asBoolean()?"TRUE":"FALSE") ||
                    !pair.get(1).asText().equals(item.keyedPair().get(1).asBoolean()?"TRUE":"FALSE"))) reasons.add("MISMATCH");
            if (!item.goldPair().isNull()) row.put("matchesGold",pair.get(0).asText().equals(item.goldPair().get(0).asBoolean()?"TRUE":"FALSE") &&
                    pair.get(1).asText().equals(item.goldPair().get(1).asBoolean()?"TRUE":"FALSE"));
            else row.putNull("matchesGold");
        }
        reasons.sort(java.util.Comparator.comparingInt(ORDER::indexOf));
        var array=row.putArray("reasons"); reasons.forEach(array::add);
        row.put("decision",reasons.isEmpty()?"ACCEPT":"REJECT");
        if (reasons.isEmpty()) row.putNull("primaryReason"); else row.put("primaryReason",reasons.getFirst());
        return row;
    }
}
