"use client";

import { Card } from "@/components/ui/card";
import { SummaryMarkdown } from "@/components/ui/summary-markdown";

type NoteDetailSummaryCardProps = Readonly<{
  summary: string;
  onViewFullNotes: () => void;
}>;

export function NoteDetailSummaryCard({
  summary,
  onViewFullNotes,
}: NoteDetailSummaryCardProps) {
  return (
    <Card id="study-pack-summary" className="space-y-3 p-4 sm:p-6">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <h2 className="text-lg font-semibold sm:text-xl">Summary</h2>
        <button
          type="button"
          onClick={onViewFullNotes}
          className="inline-flex w-fit text-sm font-medium text-blue-600 transition-colors hover:underline dark:text-blue-400"
        >
          View Full Notes →
        </button>
      </div>
      <SummaryMarkdown content={summary} />
    </Card>
  );
}
