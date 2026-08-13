"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  trackAnalyticsEvent,
  type PostSessionNextStepResponse,
  type QuizSessionHistoryMode,
} from "@/lib/api";
import { getUpgradeCtas, type AppPlanType } from "@/src/config/plans";

type PostSessionNextStepProps = {
  response: PostSessionNextStepResponse | null;
  currentPlan: string;
  noteId: string | null;
  onOpenPaywall: () => void;
  originatingQuizMode: QuizSessionHistoryMode;
  contained?: boolean;
};

function isChallengeAction(href: string | null | undefined): boolean {
  return href?.includes("/challenge-quiz") === true;
}

function trackAnalyticsSafely(payload: Parameters<typeof trackAnalyticsEvent>[0]): void {
  try {
    void Promise.resolve(trackAnalyticsEvent(payload)).catch(() => undefined);
  } catch {
    // Analytics must never interrupt result guidance or navigation.
  }
}

function normalizePlan(plan: string): AppPlanType {
  return plan === "PLUS" || plan === "PRO" ? plan : "FREE";
}

export function PostSessionNextStep({
  response,
  currentPlan,
  noteId,
  onOpenPaywall,
  originatingQuizMode,
  contained = false,
}: Readonly<PostSessionNextStepProps>) {
  const challengeActionSignature = useMemo(() => {
    if (!response) {
      return null;
    }
    const challengeHref = isChallengeAction(response.actionHref)
      ? response.actionHref
      : isChallengeAction(response.secondaryAction?.actionHref)
        ? response.secondaryAction?.actionHref
        : null;
    return challengeHref ? `${response.studyPackId}:${originatingQuizMode}:${challengeHref}` : null;
  }, [originatingQuizMode, response]);
  const impressedChallengeActionRef = useRef<string | null>(null);

  useEffect(() => {
    if (!challengeActionSignature) {
      impressedChallengeActionRef.current = null;
      return;
    }
    if (impressedChallengeActionRef.current === challengeActionSignature) {
      return;
    }
    impressedChallengeActionRef.current = challengeActionSignature;
    trackAnalyticsSafely({
      eventType: "POST_SESSION_CHALLENGE_CTA_IMPRESSION",
      entityId: response?.studyPackId ?? null,
      metadata: { originatingQuizMode },
    });
  }, [challengeActionSignature, originatingQuizMode, response?.studyPackId]);

  if (!response) {
    return null;
  }

  const normalizedPlan = normalizePlan(currentPlan);
  const upgradeCta = getUpgradeCtas(normalizedPlan, "adaptive-practice").primary;
  const adaptiveQuotaExhausted = response.adaptivePracticeAvailable
    && response.adaptivePracticeRemaining === 0
    && upgradeCta !== null;
  const shouldShowPrimaryUpgradeCta = response.type === "PRACTICE_WEAK_CONCEPT"
    && adaptiveQuotaExhausted;
  const shouldShowSecondaryUpgradeCta = response.secondaryAction?.adaptivePractice === true
    && adaptiveQuotaExhausted;
  const actionHref = response.actionHref || (noteId ? `/notes/${noteId}` : "/library");
  const secondaryAction = response.secondaryAction;
  const trackChallengeClick = (href: string) => {
    if (!isChallengeAction(href)) {
      return;
    }
    trackAnalyticsSafely({
      eventType: "POST_SESSION_CHALLENGE_CTA_CLICKED",
      entityId: response.studyPackId,
      metadata: { originatingQuizMode },
    });
  };

  const content = (
    <>
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
          Recommended next step
        </p>
        <h2 className="text-lg font-semibold text-foreground">{response.title}</h2>
        <p className="text-sm text-foreground/75">{response.message}</p>
      </div>
      {response.concepts.length > 0 ? (
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Focus areas</p>
          <div className="flex flex-wrap gap-2">
            {response.concepts.map((concept) => (
              <span
                key={concept}
                className="rounded-full border border-blue-600/30 bg-background px-3 py-1 text-xs font-medium text-blue-700 dark:text-blue-300"
              >
                {concept}
              </span>
            ))}
          </div>
        </div>
      ) : null}
      <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
        {shouldShowPrimaryUpgradeCta ? (
          <Button type="button" className="w-full sm:w-auto" onClick={onOpenPaywall}>
            {upgradeCta?.label}
          </Button>
        ) : (
          <Link href={actionHref} className="block w-full sm:w-fit" onClick={() => trackChallengeClick(actionHref)}>
            <Button type="button" className="w-full sm:w-auto">
              {response.actionLabel}
            </Button>
          </Link>
        )}
        {secondaryAction ? (
          shouldShowSecondaryUpgradeCta ? (
            <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={onOpenPaywall}>
              {upgradeCta?.label}
            </Button>
          ) : (
            <Link
              href={secondaryAction.actionHref}
              className="block w-full sm:w-fit"
              onClick={() => trackChallengeClick(secondaryAction.actionHref)}
            >
              <Button type="button" variant="outline" className="w-full sm:w-auto">
                {secondaryAction.actionLabel}
              </Button>
            </Link>
          )
        ) : null}
      </div>
    </>
  );

  return contained ? (
    <section className="space-y-4 p-4" data-result-guidance-item="next-step">
      {content}
    </section>
  ) : (
    <Card className="space-y-4 border-blue-500/25 bg-blue-500/5 p-4">
      {content}
    </Card>
  );
}
