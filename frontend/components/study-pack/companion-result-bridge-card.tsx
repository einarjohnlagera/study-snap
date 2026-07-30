"use client";

import { Card } from "@/components/ui/card";
import { SummaryMarkdown } from "@/components/ui/summary-markdown";
import type { CompanionContent } from "@/lib/api";

export function hasCompanionResultBridgeExcerpt(companion: CompanionContent | null): boolean {
  return Boolean(companion?.commonMistakes?.trim() || companion?.studyStrategy?.trim());
}

export function CompanionResultBridgeCard({
  companion,
  reviewSetLabel,
  contained = false,
}: Readonly<{ companion: CompanionContent | null; reviewSetLabel: string; contained?: boolean }>) {
  const commonMistakes = companion?.commonMistakes?.trim();
  const studyStrategy = companion?.studyStrategy?.trim();
  const excerpt = commonMistakes || studyStrategy;
  if (!excerpt) {
    return null;
  }
  const sectionLabel = commonMistakes ? "Common Mistakes" : "Study Strategy";

  const content = (
    <>
      <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
        From your {reviewSetLabel}&apos;s Companion — {sectionLabel}
      </p>
      <SummaryMarkdown content={excerpt} />
    </>
  );

  return contained ? (
    <section className="space-y-2 p-4" data-result-guidance-item="companion-excerpt">
      {content}
    </section>
  ) : (
    <Card className="space-y-2 border-blue-500/20 bg-blue-500/5 p-4">
      {content}
    </Card>
  );
}
