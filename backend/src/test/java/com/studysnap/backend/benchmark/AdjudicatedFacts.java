package com.studysnap.backend.benchmark;

/** Locked class-A question counts for one case and one opened arm. */
record AdjudicatedFacts(int scoredQuestions, int criticalQuestions, int qualityFailQuestions,
                        int unresolvedQuestions, int pendingQuestions, int doubleMisses,
                        int scorerFailureCriticals, int humanCleared, String subgroup) {
    boolean critical() { return criticalQuestions > 0; }
    boolean quality() { return qualityFailQuestions > 0; }
    ScoreEvidence.Facts toFacts() {
        return new ScoreEvidence.Facts(critical(), quality(), scoredQuestions, subgroup);
    }
}
