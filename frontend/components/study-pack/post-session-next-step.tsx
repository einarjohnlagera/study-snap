"use client";

import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import type { PostSessionNextStepResponse } from "@/lib/api";
import { getUpgradeCtas, type AppPlanType } from "@/src/config/plans";

type PostSessionNextStepProps = {
  response: PostSessionNextStepResponse | null;
  currentPlan: string;
  noteId: string | null;
  onOpenPaywall: () => void;
};

function normalizePlan(plan: string): AppPlanType {
  return plan === "PLUS" || plan === "PRO" ? plan : "FREE";
}

export function PostSessionNextStep({
  response,
  currentPlan,
  noteId,
  onOpenPaywall,
}: Readonly<PostSessionNextStepProps>) {
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

  return (
    <Card className="space-y-4 border-blue-500/25 bg-blue-500/5 p-4">
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
          <Link href={actionHref} className="block w-full sm:w-fit">
            <Button type="button" className="w-full sm:w-auto">
              {response.actionLabel}
            </Button>
          </Link>
        )}
        {response.secondaryAction ? (
          shouldShowSecondaryUpgradeCta ? (
            <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={onOpenPaywall}>
              {upgradeCta?.label}
            </Button>
          ) : (
            <Link href={response.secondaryAction.actionHref} className="block w-full sm:w-fit">
              <Button type="button" variant="outline" className="w-full sm:w-auto">
                {response.secondaryAction.actionLabel}
              </Button>
            </Link>
          )
        ) : null}
      </div>
    </Card>
  );
}
