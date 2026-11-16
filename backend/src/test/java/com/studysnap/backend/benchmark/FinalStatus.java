package com.studysnap.backend.benchmark;

import com.studysnap.backend.benchmark.ClassAScoreFilesV2.ItemScores;
import com.studysnap.backend.benchmark.ClassAScoreFilesV2.Slot;
import com.studysnap.backend.benchmark.ClassAScoreFilesV2.Status;
import com.studysnap.backend.benchmark.ClassAScoreSchemaV2.Verdict;
import java.util.EnumSet;
import java.util.Set;

/** Blind question adjudication. A human verdict is final wherever present. */
final class FinalStatus {
    private FinalStatus() {}

    static QuestionStatus of(ItemScores item, EvidenceMapping mapping, boolean pairReviewRequired) {
        if (item == null || item.id() == null || item.id().isBlank() || item.p() == null || item.s() == null
                || mapping == null) throw new InvalidEvidence("Complete item and mapping required");
        Verdict p = verdict(item.p(), mapping), s = verdict(item.s(), mapping);
        EnumSet<QuestionStatus.ReviewReason> reasons = EnumSet.noneOf(QuestionStatus.ReviewReason.class);
        if (p == Verdict.CRITICAL || s == Verdict.CRITICAL)
            reasons.add(QuestionStatus.ReviewReason.CRITICAL_FLAG);
        if (borderline(item.p()) || borderline(item.s()))
            reasons.add(QuestionStatus.ReviewReason.BORDERLINE);
        if (p != s) reasons.add(QuestionStatus.ReviewReason.DISAGREEMENT);
        if (item.p().status() != Status.OK || item.s().status() != Status.OK)
            reasons.add(QuestionStatus.ReviewReason.SCORER_MISSING);
        boolean required = ClassAScoreFilesV2.needsHumanReview(item, mapping) || pairReviewRequired;
        Verdict h = item.h() == null ? null : item.h().verdict();
        QuestionStatus.State state = h == null && required
                ? QuestionStatus.State.PENDING_REVIEW : QuestionStatus.State.FINAL;
        Verdict f = h != null ? h : state == QuestionStatus.State.PENDING_REVIEW ? null
                : p == Verdict.CLEAN && s == Verdict.CLEAN ? Verdict.CLEAN
                : p == Verdict.QUALITY_FAIL && s == Verdict.QUALITY_FAIL ? Verdict.QUALITY_FAIL : null;
        if (state == QuestionStatus.State.FINAL && f == null)
            throw new InvalidEvidence("Unreviewed scorer combination cannot be final: " + item.id());
        boolean flagged = p == Verdict.CRITICAL || s == Verdict.CRITICAL;
        boolean missing = item.p().status() != Status.OK || item.s().status() != Status.OK;
        boolean agreedQ = p == Verdict.QUALITY_FAIL && s == Verdict.QUALITY_FAIL;
        return new QuestionStatus(item.id(), state, f, Set.copyOf(reasons),
                doubleMiss(item, h, flagged), h == Verdict.CRITICAL && missing && !flagged,
                h == Verdict.CLEAN && flagged, h == Verdict.QUALITY_FAIL && flagged,
                h == Verdict.CLEAN && agreedQ, f == Verdict.CRITICAL, f == Verdict.QUALITY_FAIL,
                f == Verdict.UNRESOLVED || state == QuestionStatus.State.PENDING_REVIEW,
                h == null && f == Verdict.QUALITY_FAIL && agreedQ,
                h == Verdict.QUALITY_FAIL && p == Verdict.CLEAN && s == Verdict.CLEAN,
                h != null && agreedQ);
    }

    private static boolean doubleMiss(ItemScores item, Verdict h, boolean flagged) {
        return h == Verdict.CRITICAL && item.p().status() == Status.OK
                && item.s().status() == Status.OK && !flagged;
    }

    private static Verdict verdict(Slot slot, EvidenceMapping mapping) {
        return slot.status() == Status.OK ? ClassAScoreSchemaV2.verdict(slot.row(), mapping) : null;
    }

    private static boolean borderline(Slot slot) {
        return slot.status() == Status.OK && slot.row().path("borderline").asBoolean();
    }
}
