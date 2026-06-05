"use client";

import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { setStudyGoal, trackAnalyticsEvent } from "@/lib/api";
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
};

type ResolvedGoal = {
  slug: ExamHubSlug;
  exam: ExamHubConfig;
};

type SuggestedSubjectGoal = {
  courseProgram: string;
};

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

export function GoalPromptBanner({ studyGoal, courseProgram, profileType }: Readonly<GoalPromptBannerProps>) {
  const [resolvedGoal, setResolvedGoal] = useState<ResolvedGoal | null>(null);
  const [subjectGoal, setSubjectGoal] = useState<SuggestedSubjectGoal | null>(null);
  const [dismissed, setDismissed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (studyGoal || dismissed) {
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
  }, [courseProgram, dismissed, studyGoal, profileType]);

  const handleSetGoal = useCallback(async () => {
    const goalValue = resolvedGoal?.slug ?? subjectGoal?.courseProgram ?? null;
    if (!goalValue || saving) {
      return;
    }

    setSaving(true);
    setError(null);
    try {
      await setStudyGoal(goalValue);
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
  }, [resolvedGoal, saving, subjectGoal]);

  const handleDismiss = useCallback(() => {
    const goalValue = resolvedGoal?.slug ?? subjectGoal?.courseProgram ?? null;
    if (!goalValue) {
      return;
    }
    clearExamIntentCookie();
    void trackAnalyticsEvent({
      eventType: "STUDY_GOAL_DISMISSED",
      metadata: { studyGoal: goalValue },
    });
    setDismissed(true);
  }, [resolvedGoal, subjectGoal]);

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
    <Card className="overflow-hidden border-blue-500/25 bg-linear-to-r from-blue-500/10 via-background to-emerald-500/10 p-4 sm:p-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
            Suggested Goal: {resolvedGoal.exam.shortName}
          </p>
          <div className="space-y-1">
            <h2 className="text-lg font-semibold tracking-tight sm:text-xl">
              Make {resolvedGoal.exam.fullName} your study goal?
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
  );
}
