package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Post-unlock descriptive feature check. Exact two-sided SIGN tests drop ties; Bonferroni divides
 * alpha across features only, not workflows or the overall test. Thresholds are unapproved and no
 * verdict changes. Any integrity failure throws InvalidAudit and means BLOCKED.
 */
final class PacketFeatureAudit {
    static final class InvalidAudit extends RuntimeException {
        InvalidAudit(String message, Throwable cause) { super(message, cause); }
    }
    record Feature(int pairs, int positive, int negative, int ties, Rational twoSidedP,
                   BigDecimal candidateMean, BigDecimal baselineMean, boolean flagged) {}
    record Report(Map<String, Map<String, Feature>> workflows, Map<String, Feature> overall,
                  Map<String, Map<String, Feature>> reserveWorkflows, Map<String, Feature> reserveOverall,
                  Rational alpha, String limitation) {}
    private record Values(Map<String, Integer> candidate, Map<String, Integer> baseline) {}
    private PacketFeatureAudit() {}
    static Report run(Path packets, Path seal, String candidateArm,
                      ArmKeyAccess.OpenedKey opened, Rational alpha) {
        try {
            List<BlindAuditSupport.Pair> pairs = BlindAuditSupport.pairs(packets, seal, candidateArm, opened, alpha);
            Map<String, List<BlindAuditSupport.Pair>> primary = new TreeMap<>(), reserve = new TreeMap<>();
            for (BlindAuditSupport.Pair pair : pairs)
                (pair.reserve() ? reserve : primary).computeIfAbsent(pair.workflow(), unused -> new ArrayList<>()).add(pair);
            return new Report(groups(primary, alpha), features(flat(primary), alpha),
                    groups(reserve, alpha), features(flat(reserve), alpha), alpha,
                    "Post-unlock packet features only; significance thresholds are unapproved and no verdict changes");
        } catch (Exception exception) { throw new InvalidAudit("Cannot run packet-feature audit", exception); }
    }
    static void write(Path reportFile, Report report) {
        try {
            if (report == null) throw new InvalidAudit("Report required", null);
            IntegritySupport.create(reportFile, IntegritySupport.JSON.writeValueAsBytes(report));
        } catch (InvalidAudit exception) { throw exception; }
        catch (Exception exception) { throw new InvalidAudit("Cannot write detached feature report", exception); }
    }
    private static List<BlindAuditSupport.Pair> flat(Map<String, List<BlindAuditSupport.Pair>> groups) {
        return groups.values().stream().flatMap(List::stream).toList();
    }
    private static Map<String, Map<String, Feature>> groups(Map<String, List<BlindAuditSupport.Pair>> grouped,
                                                              Rational alpha) {
        Map<String, Map<String, Feature>> result = new TreeMap<>();
        grouped.forEach((name, pairs) -> result.put(name, features(pairs, alpha)));
        return java.util.Collections.unmodifiableMap(result);
    }
    private static Map<String, Feature> features(List<BlindAuditSupport.Pair> pairs, Rational alpha) {
        Map<String, List<Integer>> candidates = new TreeMap<>(), baselines = new TreeMap<>();
        for (BlindAuditSupport.Pair pair : pairs) {
            Values values = values(pair);
            for (String name : values.candidate().keySet()) {
                candidates.computeIfAbsent(name, unused -> new ArrayList<>()).add(values.candidate().get(name));
                baselines.computeIfAbsent(name, unused -> new ArrayList<>()).add(values.baseline().get(name));
            }
        }
        Map<String, Feature> result = new TreeMap<>();
        int featureCount = candidates.size();
        for (String name : candidates.keySet()) {
            List<Integer> c = candidates.get(name), b = baselines.get(name);
            int positive = 0, negative = 0, ties = 0;
            long cSum = 0, bSum = 0;
            for (int i = 0; i < c.size(); i++) {
                int relation = Integer.compare(c.get(i), b.get(i));
                if (relation > 0) positive++; else if (relation < 0) negative++; else ties++;
                cSum += c.get(i); bSum += b.get(i);
            }
            BlindAuditSupport.Binomial p = BlindAuditSupport.binomial(positive, positive + negative);
            result.put(name, new Feature(c.size(), positive, negative, ties, p.p(),
                    mean(cSum, c.size()), mean(bSum, b.size()),
                    !p.undefined() && BlindAuditSupport.atMost(p.p(), alpha.dividedBy(featureCount))));
        }
        return java.util.Collections.unmodifiableMap(result);
    }
    private static BigDecimal mean(long total, int n) {
        return n == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
    }
    private static Values values(BlindAuditSupport.Pair pair) {
        if (pair.classB()) return new Values(classB(pair.candidate()), classB(pair.baseline()));
        return new Values(classA(pair.candidate()), classA(pair.baseline()));
    }
    private static Map<String, Integer> classB(JsonNode output) {
        JsonNode text = output.path("output");
        if (!text.isTextual()) throw new InvalidAudit("Class-B output text required", null);
        return Map.of("outputChars", text.asText().length(), "newlines", newlines(text.asText()));
    }
    private static Map<String, Integer> classA(JsonNode output) {
        JsonNode items = output.path("items");
        if (!items.isArray() || items.isEmpty()) throw new InvalidAudit("Class-A items required", null);
        int chars = 0, choices = 0, newlines = 0;
        for (JsonNode item : items) {
            JsonNode question = item.path("question");
            JsonNode prompt = question.path("question"), explanation = question.path("explanation");
            JsonNode options = question.path("choices");
            if (!prompt.isTextual() || !explanation.isTextual()
                    || !options.isMissingNode() && !options.isArray())
                throw new InvalidAudit("Invalid class-A feature fields", null);
            chars = Math.addExact(chars, prompt.asText().length() + explanation.asText().length());
            choices = Math.addExact(choices, options.isArray() ? options.size() : 0);
            newlines = Math.addExact(newlines, newlines(prompt.asText()) + newlines(explanation.asText()));
        }
        return Map.of("questionExplanationChars", chars, "questions", items.size(),
                "choices", choices, "newlines", newlines);
    }
    private static int newlines(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') count++;
        return count;
    }
}
