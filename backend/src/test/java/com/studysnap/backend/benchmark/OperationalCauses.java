package com.studysnap.backend.benchmark;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Produces a route's attested operational-case set from all supplied provenance ledgers. This
 * producer is not bound to deadline-fallback classification: ResultsPipeline still accepts a
 * caller-supplied set, and Brief 5r must pass this result through classifyFrbRoutes. The caller
 * must supply every ledger of the composition; this class cannot know whether the list is complete.
 *
 * The store verifies a record's lockSha256 against the lock file, while the frozen parameters
 * come from the caller's PostLockContext; requireReport binds the report's parameter hash to
 * that context. The store sees only a one-way case pseudonym, so it cannot validate the case id
 * against a frozen case. Matching happens here against conditional report ids; a well-formed
 * unmatched attestation is reported, not refused. An auditor must recompute pseudonyms over the
 * frozen manifest to identify the attested cases. Evidence digests are recorded but never checked
 * against a file, so adequacy here means only a well-formed digest. A stored OWNER role is a
 * claim, not authenticated identity. A resolved FIXED or ACCEPTED_WITH_RATIONALE concern permits
 * an earlier independent completion to count again; NOT_APPLICABLE permanently withdraws it.
 * A cross-workflow case-id collision is a defence in depth condition: validateFrozen rejects one
 * for computed reports, while a synthetic hand-built report can still exercise the flat-set rule.
 */
final class OperationalCauses {
    private static final String PAIR_SEPARATOR = "\u0000";
    private OperationalCauses() {}
    static final class InvalidOperationalCause extends RuntimeException {
        InvalidOperationalCause(String message) { super(message); }
        InvalidOperationalCause(String message, Throwable cause) { super(message, cause); }
    }
    enum Category {
        PROVIDER_OUTAGE("po"), OWNER_STOPPED_RUN("os"), OTHER_OPERATIONAL("ot");
        private final String code;
        Category(String code) { this.code = code; }
    }
    enum Status {
        ESTABLISHED, NO_ATTESTATION, WITHDRAWN_OPEN_CONCERN, WITHDRAWN_BY_OWNER,
        CONFLICTING_CATEGORIES, AMBIGUOUS_CASE_ID
    }
    record Finding(String workflow, String caseId, Status status, Category categoryOrNull,
                   String reviewIdOrNull) {}
    record Result(SortedSet<String> establishedCaseIds, List<Finding> findings,
                  SortedSet<String> unmatchedAttestationSubjects) {
        Result {
            if (establishedCaseIds == null || findings == null || unmatchedAttestationSubjects == null
                    || findings.contains(null)
                    || establishedCaseIds.stream().anyMatch(value -> value == null)
                    || unmatchedAttestationSubjects.stream().anyMatch(value -> value == null))
                throw new InvalidOperationalCause("Null operational result");
            establishedCaseIds = Collections.unmodifiableSortedSet(new TreeSet<>(establishedCaseIds));
            findings = List.copyOf(findings);
            unmatchedAttestationSubjects = Collections.unmodifiableSortedSet(
                    new TreeSet<>(unmatchedAttestationSubjects));
        }
    }
    private record Pair(String workflow, String caseId) implements Comparable<Pair> {
        @Override public int compareTo(Pair other) {
            int byWorkflow = workflow.compareTo(other.workflow);
            return byWorkflow == 0 ? caseId.compareTo(other.caseId) : byWorkflow;
        }
    }

    static String subjectId(Category category, String workflow, String caseId) {
        if (category == null || workflow == null || workflow.isBlank()
                || caseId == null || caseId.isBlank())
            throw new InvalidOperationalCause("Category, workflow and case id are required");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (workflow + PAIR_SEPARATOR + caseId).getBytes(StandardCharsets.UTF_8));
            String subject = "oc-" + category.code + "-" + HexFormat.of().formatHex(digest).substring(0, 32);
            PostLockContext.id(subject);
            return subject;
        } catch (NoSuchAlgorithmException exception) {
            throw new InvalidOperationalCause("SHA-256 unavailable", exception);
        }
    }

    static Result derive(List<FindingsLedger> routeLedgers, String route,
            RouteVerdictCalculator.RouteReport report) {
        if (routeLedgers == null || routeLedgers.isEmpty() || routeLedgers.stream().anyMatch(ledger -> ledger == null)
                || new HashSet<>(routeLedgers).size() != routeLedgers.size()
                || report == null)
            throw new InvalidOperationalCause("Distinct ledgers and report are required");
        routeLedgers.getFirst().requireReport(route, report);
        TreeSet<Pair> needed = new TreeSet<>();
        report.workflows().forEach((workflow, row) -> {
            if (row.unresolvedCaseIdsByCause() == null) return;
            row.unresolvedCaseIdsByCause().forEach((cause, ids) -> {
                if (CauseTable.kind(cause) == CauseTable.Kind.CONDITIONAL_FALLBACK)
                    for (String id : ids) needed.add(new Pair(workflow, id));
            });
        });
        Map<Pair, Map<Category, List<ReviewRecord>>> completed = new TreeMap<>();
        Set<Pair> open = new HashSet<>();
        Set<Pair> withdrawn = new HashSet<>();
        SortedSet<String> unmatched = new TreeSet<>();
        Map<String, Pair> subjects = new HashMap<>();
        Map<String, Category> categories = new HashMap<>();
        for (Pair pair : needed) for (Category category : Category.values()) {
            String subject = subjectId(category, pair.workflow(), pair.caseId());
            String key = pair.workflow() + PAIR_SEPARATOR + subject;
            subjects.put(key, pair);
            categories.put(key, category);
        }
        for (FindingsLedger ledger : routeLedgers) {
            FindingsLedger.OperationalCauseReviews claims = ledger.operationalCauseReviews(route);
            for (ReviewRecord record : claims.records()) {
                if (record.supersedes() != null || record.outcome() != ReviewRecord.Outcome.COMPLETE) continue;
                String key = record.workflow() + PAIR_SEPARATOR + record.subjectId();
                Pair pair = subjects.get(key);
                if (pair == null) unmatched.add(record.subjectId());
                else completed.computeIfAbsent(pair, ignored -> new EnumMap<>(Category.class))
                        .computeIfAbsent(categories.get(key), ignored -> new ArrayList<>()).add(record);
            }
            for (FindingsLedger.ReviewSubject subject : claims.openSubjects()) {
                Pair pair = subjects.get(subject.workflow() + PAIR_SEPARATOR + subject.subjectId());
                if (pair != null) open.add(pair);
            }
            for (FindingsLedger.ResolvedOperationalConcern resolution : claims.resolvedConcerns()) {
                FindingsLedger.ReviewSubject subject = resolution.subject();
                Pair pair = subjects.get(subject.workflow() + PAIR_SEPARATOR + subject.subjectId());
                if (pair != null && resolution.resolutionCode() == ReviewRecord.ResolutionCode.NOT_APPLICABLE)
                    withdrawn.add(pair);
            }
        }
        Map<Pair, Finding> byPair = new TreeMap<>();
        for (Pair pair : needed) {
            Map<Category, List<ReviewRecord>> byCategory = completed.getOrDefault(pair, Map.of());
            Status status;
            Category category = null;
            String reviewId = null;
            if (withdrawn.contains(pair)) status = Status.WITHDRAWN_BY_OWNER;
            else if (open.contains(pair)) status = Status.WITHDRAWN_OPEN_CONCERN;
            else if (byCategory.size() > 1) status = Status.CONFLICTING_CATEGORIES;
            else if (byCategory.size() == 1) {
                status = Status.ESTABLISHED;
                category = byCategory.keySet().iterator().next();
                reviewId = byCategory.get(category).getFirst().reviewId();
            } else status = Status.NO_ATTESTATION;
            byPair.put(pair, new Finding(pair.workflow(), pair.caseId(), status, category, reviewId));
        }
        Map<String, List<Pair>> byId = new TreeMap<>();
        for (Pair pair : needed) byId.computeIfAbsent(pair.caseId(), ignored -> new ArrayList<>()).add(pair);
        SortedSet<String> established = new TreeSet<>();
        for (var entry : byId.entrySet()) {
            if (entry.getValue().stream().allMatch(pair -> byPair.get(pair).status() == Status.ESTABLISHED))
                established.add(entry.getKey());
            else if (entry.getValue().size() > 1)
                for (Pair pair : entry.getValue()) {
                    Finding finding = byPair.get(pair);
                    if (finding.status() == Status.ESTABLISHED || finding.status() == Status.NO_ATTESTATION)
                        byPair.put(pair, new Finding(pair.workflow(), pair.caseId(),
                                Status.AMBIGUOUS_CASE_ID, null, null));
                }
        }
        return new Result(established, new ArrayList<>(byPair.values()), unmatched);
    }
}
