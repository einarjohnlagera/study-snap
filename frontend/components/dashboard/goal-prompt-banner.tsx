"use client";

import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { setExamGoal, trackAnalyticsEvent } from "@/lib/api";
import { clearExamIntentCookie, getExamIntentCookie } from "@/lib/exam-intent";
import {
  getExamHubConfig,
  getExamSlugForCourseProgram,
  type ExamHubConfig,
  type ExamHubSlug,
} from "@/lib/exam-hub-config";

type GoalPromptBannerProps = {
  examGoal: string | null;
  courseProgram: string | null;
};

type ResolvedGoal = {
  slug: ExamHubSlug;
  exam: ExamHubConfig;
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

export function GoalPromptBanner({ examGoal, courseProgram }: Readonly<GoalPromptBannerProps>) {
  const [resolvedGoal, setResolvedGoal] = useState<ResolvedGoal | null>(null);
  const [dismissed, setDismissed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (examGoal || dismissed) {
      setResolvedGoal(null);
      return;
    }
    setResolvedGoal(resolveSuggestedGoal(courseProgram));
  }, [courseProgram, dismissed, examGoal]);

  const handleSetGoal = useCallback(async () => {
    if (!resolvedGoal || saving) {
      return;
    }

    setSaving(true);
    setError(null);
    try {
      await setExamGoal(resolvedGoal.slug);
      clearExamIntentCookie();
      void trackAnalyticsEvent({
        eventType: "EXAM_GOAL_SET",
        metadata: { examGoal: resolvedGoal.slug },
      });
      setDismissed(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not set your exam goal. Please try again.");
    } finally {
      setSaving(false);
    }
  }, [resolvedGoal, saving]);

  const handleDismiss = useCallback(() => {
    if (!resolvedGoal) {
      return;
    }
    clearExamIntentCookie();
    void trackAnalyticsEvent({
      eventType: "EXAM_GOAL_DISMISSED",
      metadata: { examGoal: resolvedGoal.slug },
    });
    setDismissed(true);
  }, [resolvedGoal]);

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
