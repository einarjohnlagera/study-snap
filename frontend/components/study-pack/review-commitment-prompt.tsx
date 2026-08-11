"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  getMe,
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

type ReviewCommitmentPromptProps = {
  isFirstCompletedSessionEver?: boolean;
  noteId: string | null;
};

export function ReviewCommitmentPrompt({
  isFirstCompletedSessionEver,
  noteId,
}: Readonly<ReviewCommitmentPromptProps>) {
  const [visible, setVisible] = useState(false);
  const [examDate, setExamDate] = useState("");
  const [showExamDate, setShowExamDate] = useState(false);
  const [reviewDays, setReviewDays] = useState<ReviewDay[]>(DEFAULT_REVIEW_DAYS);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const shownTrackedRef = useRef(false);

  useEffect(() => {
    if (isFirstCompletedSessionEver !== true) {
      setVisible(false);
      return;
    }
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
        const shouldShow = me.reviewCommitmentOutstanding;
        // The exam-date sub-field stays where the field already lives, rather than generalising it.
        setShowExamDate(me.examDate !== null || me.profileType === "BOARD_EXAM");
        setExamDate(me.examDate ?? "");
        setReviewDays(me.reviewDays?.length > 0 ? me.reviewDays : DEFAULT_REVIEW_DAYS);
        setVisible(shouldShow);
        if (shouldShow && !shownTrackedRef.current) {
          shownTrackedRef.current = true;
          void trackAnalyticsEvent({
            eventType: "REVIEW_COMMITMENT_PROMPT_SHOWN",
            entityId: noteId,
            metadata: { hasExamDate: me.examDate !== null, profileType: me.profileType },
          });
        }
      })
      .catch(() => setVisible(false));
    return () => {
      active = false;
    };
  }, [isFirstCompletedSessionEver, noteId]);

  const toggleReviewDay = (day: ReviewDay) => {
    setReviewDays((current) => current.includes(day)
      ? current.filter((value) => value !== day)
      : [...current, day]);
    setError(null);
  };

  const save = async (declined: boolean) => {
    // Only require the date where the field is rendered; a STUDENT cannot have one and would be
    // permanently blocked from committing otherwise.
    if (!declined && showExamDate && !examDate) {
      setError("Choose your exam date before setting your review plan.");
      return;
    }
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
        <p className="text-sm text-foreground/75">Choose the days you want NoteLib to remind you when concepts are due.</p>
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
