"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  getMe,
  recordReviewCommitmentPrompted,
  trackAnalyticsEvent,
  updateReviewCommitment,
  type ReviewDay,
} from "@/lib/api";

const REVIEW_DAY_OPTIONS: ReadonlyArray<{ value: ReviewDay; label: string }> = [
  { value: "MONDAY", label: "Mon" },
  { value: "TUESDAY", label: "Tue" },
  { value: "WEDNESDAY", label: "Wed" },
  { value: "THURSDAY", label: "Thu" },
  { value: "FRIDAY", label: "Fri" },
  { value: "SATURDAY", label: "Sat" },
  { value: "SUNDAY", label: "Sun" },
];
const DEFAULT_REVIEW_DAYS: ReviewDay[] = ["MONDAY", "WEDNESDAY", "FRIDAY"];

// ⚠️ `globalThis.sessionStorage` THROWS — it does not merely return null — when site data is blocked
// (Chrome "block all cookies", some embedded webviews, restricted iOS contexts). Optional chaining
// guards a NULL storage object, not a THROWING accessor, so `globalThis.sessionStorage?.getItem(k)`
// is not safe. `lib/guidance.ts:9-33` already wraps every access for exactly this reason; these two
// helpers keep this component consistent with it.
//
// ⚠️ THIS IS THE v0.139.0 PRE-SIGNOFF FINDING, AND IT IS A CROSS-PR ONE. Item 1 added a state
// machine whose guarded block, at that time, contained nothing that could throw. Item 2 then added
// the impression bookkeeping INSIDE that block, after `shownTrackedRef` had already been advanced to
// "shown" and `promptVisibleRef` to true, but BEFORE `REVIEW_COMMITMENT_PROMPT_SHOWN` fires. A throw
// there left the machine claiming the prompt was shown while the outer `.catch` reset only `visible`
// -- so a later pagehide/unmount emitted REVIEW_COMMITMENT_DISMISSED FOR A PROMPT NOBODY SAW, with
// no matching PROMPT_SHOWN, inflating the very abandonment funnel item 1 exists to build.
// Neither PR's own review could see it: one supplied the guard, the other supplied the throw.
function hasRecordedImpression(key: string): boolean {
  try {
    return globalThis.sessionStorage?.getItem(key) === "1";
  } catch {
    // Unreadable storage means "not yet recorded": re-POSTing is harmless (the server re-checks
    // eligibility and the cooldown makes a repeat a no-op), whereas treating it as ALREADY recorded
    // would silently drop the impression.
    return false;
  }
}

function markImpressionRecorded(key: string): void {
  try {
    globalThis.sessionStorage?.setItem(key, "1");
  } catch {
    // ignore
  }
}

type ReviewCommitmentPromptProps = { noteId: string | null };

// ⚠️ THIS COMPONENT NO LONGER GATES ITSELF ON SESSION COMPLETION, AND THAT IS A CONTRACT CHANGE.
// It used to take `isFirstCompletedSessionEver` and hide itself when the prop was not true, so it was
// safe to render anywhere. Server-owned eligibility replaced that prop, and the server's rule is
// deliberately about the ASK (unanswered, under the cap, outside the cooldown) -- it says nothing
// about whether a session just finished. All five current call sites render inside a completion
// branch (`isComplete`, a `masteryReport`, or a `result`), so the behaviour is unchanged today.
// ⚠️ RENDERING THIS OUTSIDE A COMPLETION BRANCH WOULD SHOW THE PROMPT TO SOMEONE WHO HAS JUST OPENED
// THE PAGE, and would burn one of their three lifetime impressions doing it. Keep it in a completion
// branch, or reintroduce an explicit gate -- do not rely on this comment alone.
export function ReviewCommitmentPrompt({
  noteId,
}: Readonly<ReviewCommitmentPromptProps>) {
  const [visible, setVisible] = useState(false);
  const [examDate, setExamDate] = useState("");
  const [showExamDate, setShowExamDate] = useState(false);
  const [reviewDays, setReviewDays] = useState<ReviewDay[]>(DEFAULT_REVIEW_DAYS);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const shownTrackedRef = useRef<"unseen" | "shown" | "resolved" | "dismissed">("unseen");
  const promptVisibleRef = useRef(false);

  // ⚠️ noteId is read through a ref, NOT closed over, and the effect below owns an EMPTY dep array.
  // Four of the five call sites pass `note?.id ?? null`, so noteId transitions null -> value while
  // this component is mounted. If the abandonment effect depended on noteId (directly or through a
  // useCallback), that transition would run the effect's CLEANUP -- firing a dismissal the learner
  // never performed, with the stale entityId, and latching the state machine to "dismissed" so the
  // real abandonment could never be recorded afterwards. Today the ordering makes that unlikely
  // (the prompt only renders after a completed session, by which point the note has loaded), but it
  // is ordering, not a guarantee. Subscribe once per mount and keep the payload in refs.
  const noteIdRef = useRef(noteId);
  noteIdRef.current = noteId;

  const trackDismissed = useCallback((exit: "pagehide" | "unmount") => {
    if (!promptVisibleRef.current || shownTrackedRef.current !== "shown") {
      return;
    }
    shownTrackedRef.current = "dismissed";
    void trackAnalyticsEvent({
      eventType: "REVIEW_COMMITMENT_DISMISSED",
      entityId: noteIdRef.current,
      metadata: { exit },
    });
  }, []);

  useEffect(() => {
    const handlePageHide = () => trackDismissed("pagehide");
    globalThis.addEventListener("pagehide", handlePageHide);
    return () => {
      globalThis.removeEventListener("pagehide", handlePageHide);
      trackDismissed("unmount");
    };
  }, [trackDismissed]);

  useEffect(() => {
    let active = true;
    void getMe()
      .then((me) => {
        if (!active) {
          return;
        }
        // No profile gate: the commitment being asked for is the review DAYS, and gating them behind an
        // exam date would exclude every STUDENT (~27% of accounts) because onboarding only collects that
        // date for BOARD_EXAM. The post-session surface is itself the filter -- only people who study
        // reach it -- and the digest self-limits later when nothing is due.
        const shouldShow = me.reviewCommitmentPromptEligible;
        // The exam-date sub-field stays where the field already lives, rather than generalising it.
        setShowExamDate(me.examDate !== null || me.profileType === "BOARD_EXAM");
        setExamDate(me.examDate ?? "");
        setReviewDays(me.reviewDays?.length > 0 ? me.reviewDays : DEFAULT_REVIEW_DAYS);
        promptVisibleRef.current = shouldShow;
        setVisible(shouldShow);
        if (shouldShow && shownTrackedRef.current === "unseen") {
          shownTrackedRef.current = "shown";
          const impressionKey = `notelib-review-commitment-prompted-${me.id}-${me.reviewCommitmentPromptCount}`;
          if (!hasRecordedImpression(impressionKey)) {
            void recordReviewCommitmentPrompted()
              .then(() => markImpressionRecorded(impressionKey))
              .catch(() => undefined);
          }
          void trackAnalyticsEvent({
            eventType: "REVIEW_COMMITMENT_PROMPT_SHOWN",
            entityId: noteIdRef.current,
            metadata: { hasExamDate: me.examDate !== null, profileType: me.profileType },
          });
        }
      })
      .catch(() => setVisible(false));
    return () => {
      active = false;
    };
  }, []);

  const toggleReviewDay = (day: ReviewDay) => {
    setReviewDays((current) => current.includes(day)
      ? current.filter((value) => value !== day)
      : [...current, day]);
    setError(null);
  };

  const save = async (declined: boolean) => {
    if (!declined && reviewDays.length === 0) {
      setError("Choose at least one review day.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await updateReviewCommitment({
        examDate: examDate || null,
        reviewDays: declined ? [] : reviewDays,
      });
      shownTrackedRef.current = "resolved";
      promptVisibleRef.current = false;
      void trackAnalyticsEvent({
        eventType: declined ? "REVIEW_COMMITMENT_DECLINED" : "REVIEW_COMMITMENT_COMMITTED",
        entityId: noteId,
        metadata: declined ? null : { reviewDays, hasExamDate: examDate !== "" },
      });
      setVisible(false);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Could not save your review plan. Please try again.");
    } finally {
      setSaving(false);
    }
  };

  if (!visible) {
    return null;
  }

  return (
    <section className="space-y-4 rounded-md border border-blue-500/30 bg-blue-500/10 p-4" data-testid="review-commitment-prompt">
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">Plan your next chapter</p>
        <h2 className="text-lg font-semibold">When will you come back?</h2>
        {/* ⚠️ This sentence is unconditional ON PURPOSE, and it holds because the SERVER refuses to
            render this prompt when the due-concepts digest preference is off (see
            AuthService#isReviewCommitmentPromptEligible). It was briefly conditional, after the
            v0.139.0 cold agent found it false for 252 of 396 accounts; the owner then chose not to
            ask those learners at all, which makes the other branch dead.
            ⚠️ BUT "true by construction" WOULD BE OVERSTATED, and the pre-signoff pressure test said
            so: the digest audience is preference AND emailVerifiedAt NOT NULL AND status ACTIVE
            (RetentionService#findDueConceptsDigestUsers), while eligibility checks only the
            PREFERENCE. The other two clauses are a snapshot, not an invariant -- 3 accounts today
            have the preference on with no verified email, and they are believed unable to reach a
            completed session at all. Treat this line as true-in-practice, not proven.
            ⚠️ If eligibility ever stops checking the preference, it becomes a lie outright --
            change them together or not at all. */}
        <p className="text-sm text-foreground/75">
          You already get a weekly nudge when concepts are due. Choose your review days to get a nudge on every selected day when there is something to review.
        </p>
      </div>
      {showExamDate ? (
      <label className="block space-y-1 text-sm font-medium">
        <span>Exam date</span>
        <input
          aria-label="Exam date"
          className="w-full rounded-md border border-border bg-background px-3 py-2 font-normal sm:max-w-xs"
          type="date"
          value={examDate}
          onChange={(event) => {
            setExamDate(event.target.value);
            setError(null);
          }}
          disabled={saving}
        />
      </label>
      ) : null}
      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">Review days</legend>
        <div className="flex flex-wrap gap-2">
          {REVIEW_DAY_OPTIONS.map((option) => {
            const selected = reviewDays.includes(option.value);
            return (
              <button
                key={option.value}
                type="button"
                aria-pressed={selected}
                className={`rounded-full border px-3 py-1.5 text-sm ${selected ? "border-blue-600 bg-blue-600 text-white" : "border-border bg-background"}`}
                onClick={() => toggleReviewDay(option.value)}
                disabled={saving}
              >
                {option.label}
              </button>
            );
          })}
        </div>
      </fieldset>
      {error ? <p role="alert" className="text-sm text-red-600 dark:text-red-300">{error}</p> : null}
      <div className="flex flex-col gap-2 sm:flex-row">
        <Button type="button" onClick={() => void save(false)} disabled={saving}>
          {saving ? "Saving..." : "Set my review plan"}
        </Button>
        <Button type="button" variant="ghost" onClick={() => void save(true)} disabled={saving}>
          Not now
        </Button>
      </div>
    </section>
  );
}
