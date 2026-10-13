"use client";

import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { AppModal } from "@/components/ui/app-modal";
import { addExamGoal, setStudyGoal, trackAnalyticsEvent, type ExamGoalResponse } from "@/lib/api";
import { clearExamIntentCookie, getExamIntentCookie } from "@/lib/exam-intent";
import {
  getExamHubConfig,
  getExamSlugForCourseProgram,
  type ExamHubConfig,
  type ExamHubSlug,
} from "@/lib/exam-hub-config";

type GoalPromptBannerProps = {
  studyGoal: string | null;
  courseProgram: string | null;
  profileType: string | null;
  focusSubjects?: string[] | null;
  examGoals?: ExamGoalResponse[];
  userId?: string;
};

type ResolvedGoal = {
  slug: ExamHubSlug;
  exam: ExamHubConfig;
};

type SuggestedSubjectGoal = {
  courseProgram: string;
};

const EMPTY_FOCUS_SUBJECTS: string[] = [];
const EMPTY_EXAM_GOALS: ExamGoalResponse[] = [];

function resolveSuggestedGoal(courseProgram: string | null): ResolvedGoal | null {
  const cookieSlug = getExamIntentCookie();
  const suggestedSlug = cookieSlug && getExamHubConfig(cookieSlug)
    ? cookieSlug
    : getExamSlugForCourseProgram(courseProgram);
  if (!suggestedSlug) {
    return null;
  }

  const exam = getExamHubConfig(suggestedSlug);
  return exam ? { slug: exam.slug, exam } : null;
}

export function GoalPromptBanner({ studyGoal, courseProgram, profileType, focusSubjects = EMPTY_FOCUS_SUBJECTS, examGoals = EMPTY_EXAM_GOALS, userId }: Readonly<GoalPromptBannerProps>) {
  const [resolvedGoal, setResolvedGoal] = useState<ResolvedGoal | null>(null);
  const [subjectGoal, setSubjectGoal] = useState<SuggestedSubjectGoal | null>(null);
  const [dismissed, setDismissed] = useState(() => userId != null
    && globalThis.localStorage?.getItem(`notelib-goal-prompt-dismissed:${userId}`) === "1");
  const [confirmClearSubjects, setConfirmClearSubjects] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const intentSlug = getExamIntentCookie();
    const activeIntent = Boolean(intentSlug && getExamHubConfig(intentSlug) && intentSlug !== studyGoal);
    if (!activeIntent && (studyGoal || (focusSubjects?.length ?? 0) > 0 || dismissed)) {
      setResolvedGoal(null);
      setSubjectGoal(null);
      return;
    }
    const nextResolvedGoal = resolveSuggestedGoal(courseProgram);
    setResolvedGoal(nextResolvedGoal);

    const normalizedCourseProgram = courseProgram?.trim();
    if (!nextResolvedGoal && normalizedCourseProgram && profileType !== "BOARD_EXAM") {
      setSubjectGoal({ courseProgram: normalizedCourseProgram });
    } else {
      setSubjectGoal(null);
    }
  }, [courseProgram, dismissed, studyGoal, profileType, focusSubjects]);

  const handleSetGoal = useCallback(async () => {
    const goalValue = resolvedGoal?.slug ?? subjectGoal?.courseProgram ?? null;
    if (!goalValue || saving) {
      return;
    }
    if (resolvedGoal && (focusSubjects?.length ?? 0) > 0 && !confirmClearSubjects) {
      setConfirmClearSubjects(true);
      return;
    }

    setSaving(true);
    setError(null);
    try {
      if (resolvedGoal) {
        await addExamGoal(goalValue);
        void trackAnalyticsEvent({ eventType: examGoals.some((goal) => goal.slug === goalValue)
          ? "EXAM_GOAL_ACTIVATED" : "EXAM_GOAL_ADDED", metadata: { examGoalSlug: goalValue, source: "exam_hub_intent" } });
      } else {
        await setStudyGoal(goalValue);
      }
      clearExamIntentCookie();
      void trackAnalyticsEvent({
        eventType: "STUDY_GOAL_SET",
        metadata: { studyGoal: goalValue },
      });
      setDismissed(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not set your study focus. Please try again.");
    } finally {
      setSaving(false);
    }
  }, [resolvedGoal, saving, subjectGoal, focusSubjects, confirmClearSubjects, examGoals]);

  const handleDismiss = useCallback(() => {
    const goalValue = resolvedGoal?.slug ?? subjectGoal?.courseProgram ?? null;
    if (!goalValue) {
      return;
    }
    const hadIntent = Boolean(getExamIntentCookie());
    clearExamIntentCookie();
    void trackAnalyticsEvent({
      eventType: "STUDY_GOAL_DISMISSED",
      metadata: { studyGoal: goalValue },
    });
    if (!hadIntent && userId) globalThis.localStorage?.setItem(`notelib-goal-prompt-dismissed:${userId}`, "1");
    setDismissed(true);
  }, [resolvedGoal, subjectGoal, userId]);

  if (!resolvedGoal && !subjectGoal) {
    return null;
  }

  if (subjectGoal) {
    return (
      <Card className="overflow-hidden border-emerald-500/25 bg-linear-to-r from-emerald-500/10 via-background to-blue-500/10 p-4 sm:p-6">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-emerald-700 dark:text-emerald-300">
              Suggested Focus
            </p>
            <div className="space-y-1">
              <h2 className="text-lg font-semibold tracking-tight sm:text-xl">
                Track your progress in {subjectGoal.courseProgram}.
              </h2>
              <p className="max-w-2xl text-sm leading-relaxed text-foreground/70">
                Set it as your study focus so your progress report can show mastery toward that goal.
              </p>
            </div>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row lg:shrink-0">
            <Button type="button" onClick={() => void handleSetGoal()} loading={saving} loadingText="Setting focus...">
              Set as my focus
            </Button>
            <Button type="button" variant="ghost" onClick={handleDismiss} disabled={saving}>
              Dismiss
            </Button>
          </div>
        </div>
        {error ? (
          <p className="mt-3 text-sm text-red-600 dark:text-red-300" role="alert">
            {error}
          </p>
        ) : null}
      </Card>
    );
  }

  if (!resolvedGoal) {
    return null;
  }

  return (
    <>
    <Card className="overflow-hidden border-blue-500/25 bg-linear-to-r from-blue-500/10 via-background to-emerald-500/10 p-4 sm:p-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
            Suggested Goal: {resolvedGoal.exam.shortName}
          </p>
          <div className="space-y-1">
            <h2 className="text-lg font-semibold tracking-tight sm:text-xl">
              Add {resolvedGoal.exam.fullName} as your active exam goal?
            </h2>
            <p className="max-w-2xl text-sm leading-relaxed text-foreground/70">
              We will use this to summarize your exam mastery and suggest what to study next on your progress report.
            </p>
          </div>
        </div>
        <div className="flex flex-col gap-2 sm:flex-row lg:shrink-0">
          <Button type="button" onClick={() => void handleSetGoal()} loading={saving} loadingText="Setting goal...">
            Set as my goal
          </Button>
          <Button type="button" variant="ghost" onClick={handleDismiss} disabled={saving}>
            Dismiss
          </Button>
        </div>
      </div>
      {error ? (
        <p className="mt-3 text-sm text-red-600 dark:text-red-300" role="alert">
          {error}
        </p>
      ) : null}
    </Card>
    <AppModal isOpen={confirmClearSubjects} title="Replace your subject focus?"
      onClose={() => setConfirmClearSubjects(false)}
      actions={<div className="flex justify-end gap-2">
        <Button type="button" variant="outline" onClick={() => setConfirmClearSubjects(false)}>Cancel</Button>
        <Button type="button" onClick={() => void handleSetGoal()} loading={saving}>Add exam goal</Button>
      </div>}>
      <p className="text-sm">Adding this exam goal will clear your {focusSubjects?.length ?? 0} subject focus selections.</p>
    </AppModal>
    </>
  );
}
