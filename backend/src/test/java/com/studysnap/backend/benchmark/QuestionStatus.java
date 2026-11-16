package com.studysnap.backend.benchmark;

import java.util.Set;

record QuestionStatus(String itemId, State state, ClassAScoreSchemaV2.Verdict f,
                      Set<ReviewReason> reviewReasons, boolean doubleMiss, boolean scorerFailureCritical,
                      boolean humanCleared, boolean humanDowngraded, boolean overturnedAgreedQualityFail,
                      boolean t2Event, boolean t3Event, boolean pairMissing,
                      boolean unreviewedAgreedQualityFail, boolean scorerMissNoncritical,
                      boolean agreedQualityReviewed) {
    enum State { FINAL, PENDING_REVIEW }
    enum ReviewReason { CRITICAL_FLAG, BORDERLINE, DISAGREEMENT, SCORER_MISSING }
}
