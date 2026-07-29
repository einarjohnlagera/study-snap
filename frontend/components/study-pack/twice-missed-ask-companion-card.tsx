"use client";

import Link from "next/link";
import { AskCompanionUpgradeNudge } from "@/components/collections/ask-companion-panel";
import { Card } from "@/components/ui/card";
import type { CompanionContent } from "@/lib/api";
import {
  buildAskCompanionCollectionHref,
  hasRenderableCompanionContent,
} from "@/lib/companion";
import type { AppPlanType } from "@/src/config/plans";

// Backend concept normalizers fall back to a placeholder label ("Unknown" / "Uncategorized")
// when a quiz item has no concept tag. Neither is a real, askable concept name.
const UNNAMED_CONCEPT_LABELS = new Set(["unknown", "uncategorized"]);

export function TwiceMissedAskCompanionCard({
  twiceMissedConcepts,
  currentPlan,
  primaryCollectionId,
  companion,
}: Readonly<{
  twiceMissedConcepts: string[];
  currentPlan: AppPlanType;
  primaryCollectionId: string | null;
  companion: CompanionContent | null;
}>) {
  const concept = twiceMissedConcepts
    .map((value) => value.trim())
    .find((value) => value && !UNNAMED_CONCEPT_LABELS.has(value.toLowerCase()));
  if (!concept) {
    return null;
  }
  if (currentPlan === "FREE") {
    return <AskCompanionUpgradeNudge currentPlan={currentPlan} />;
  }
  if (!primaryCollectionId || !hasRenderableCompanionContent(companion)) {
    return null;
  }

  return (
    <Card className="space-y-3 border-blue-500/25 bg-blue-500/5 p-4">
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
          Still working on {concept}?
        </p>
        <p className="text-sm text-foreground/75">
          Ask your Review Set&apos;s Companion to explain this concept another way.
        </p>
      </div>
      <Link
        href={buildAskCompanionCollectionHref(primaryCollectionId, concept)}
        className="inline-flex text-sm font-semibold text-blue-700 hover:underline dark:text-blue-300"
      >
        Ask Companion about this
      </Link>
    </Card>
  );
}
