package com.studysnap.backend.benchmark;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Derives one review obligation per class-A case with a confirmed critical in either available arm.
 * Facts do not retain critical type, so every confirmed critical is included, including nonstructural
 * types. Exclusion is case-wide: a short delivery in one arm removes both arms' facts and can hide a
 * critical in the other. This proves obligations only from supplied facts; the pipeline establishes
 * their locked provenance. A derived subject records a review requirement, not its completion.
 */
final class KeyDefectSubjects {
    private static final String SEPARATOR = "\u0000";

    private KeyDefectSubjects() {}

    static Set<FindingsLedger.ReviewSubject> derive(FrbEvidenceBundle bundle,
            Map<String, String> workflowByCaseId, Set<String> classAWorkflows,
            Set<String> classBWorkflows) {
        if (bundle == null || workflowByCaseId == null || classAWorkflows == null
                || classBWorkflows == null)
            throw new InvalidEvidence("Key-defect evidence and workflow scope required");
        for (String workflow : classAWorkflows)
            if (workflow == null || classBWorkflows.contains(workflow))
                throw new InvalidEvidence("Overlapping or null workflow scope: " + workflow);
        if (classBWorkflows.stream().anyMatch(java.util.Objects::isNull))
            throw new InvalidEvidence("Null class-B workflow");

        TreeSet<FindingsLedger.ReviewSubject> subjects = new TreeSet<>(Comparator
                .comparing(FindingsLedger.ReviewSubject::workflow)
                .thenComparing(FindingsLedger.ReviewSubject::subjectId));
        for (var entry : bundle.evidence().byCaseArm().entrySet()) {
            String identity = entry.getKey();
            int separator = identity.indexOf(SEPARATOR);
            if (separator < 1 || separator == identity.length() - 1
                    || identity.indexOf(SEPARATOR, separator + 1) >= 0)
                throw new InvalidEvidence("Malformed case-arm identity: " + identity);
            String caseId = identity.substring(0, separator);
            if (bundle.evidence().excludedCaseIds().contains(caseId))
                throw new InvalidEvidence("Excluded case has facts: " + caseId);
            String workflow = workflowByCaseId.get(caseId);
            if (workflow == null)
                throw new InvalidEvidence("No workflow for case with facts: " + caseId);
            if (!classAWorkflows.contains(workflow) && !classBWorkflows.contains(workflow))
                throw new InvalidEvidence("Workflow outside frozen class scope: " + workflow);
            AdjudicatedFacts facts = entry.getValue();
            if (facts == null || facts.scoredQuestions() < 0 || facts.criticalQuestions() < 0
                    || facts.criticalQuestions() > facts.scoredQuestions()
                    || facts.scorerFailureCriticals() < 0
                    || facts.scorerFailureCriticals() > facts.criticalQuestions())
                throw new InvalidEvidence("Malformed adjudicated facts: " + identity);
            if (classAWorkflows.contains(workflow) && facts.criticalQuestions() > 0) {
                // Only a case that must become a review subject needs a usable subject id. A class-B id
                // (no id pattern is frozen for class-B manifests) or a clean case is never rejected here.
                try { PostLockContext.id(caseId); }
                catch (PostLockStoreIndex.InvalidStore exception) {
                    throw new InvalidEvidence("Invalid review subject case id: " + caseId);
                }
                subjects.add(new FindingsLedger.ReviewSubject(
                        ReviewRecord.Kind.KEY_DEFECT, workflow, caseId));
            }
        }
        return Collections.unmodifiableSortedSet(subjects);
    }
}
