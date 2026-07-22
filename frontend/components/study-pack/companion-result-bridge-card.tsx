"use client";

import { Card } from "@/components/ui/card";
import { SummaryMarkdown } from "@/components/ui/summary-markdown";
import type { CompanionContent } from "@/lib/api";

export function CompanionResultBridgeCard({
  companion,
  reviewSetLabel,
}: Readonly<{ companion: CompanionContent | null; reviewSetLabel: string }>) {
  const commonMistakes = companion?.commonMistakes?.trim();
  const studyStrategy = companion?.studyStrategy?.trim();
  const excerpt = commonMistakes || studyStrategy;
  if (!excerpt) {
    return null;
  }
  const sectionLabel = commonMistakes ? "Common Mistakes" : "Study Strategy";

  return (
    <Card className="space-y-2 border-blue-500/20 bg-blue-500/5 p-4">
      <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
        From your {reviewSetLabel}&apos;s Companion — {sectionLabel}
      </p>
      <SummaryMarkdown content={excerpt} />
    </Card>
  );
}
