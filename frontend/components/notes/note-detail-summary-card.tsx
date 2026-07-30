"use client";

import { Card } from "@/components/ui/card";
import { SummaryMarkdown } from "@/components/ui/summary-markdown";
import {formatStudyPackScope, getStudyPackScope} from "@/lib/study-pack-scope";

type NoteDetailSummaryCardProps = Readonly<{
  summary: string;
  studyPackReady: boolean;
  keyConceptCount: number | null | undefined;
  quizCount: number | null | undefined;
  onViewFullNotes: () => void;
}>;

export function NoteDetailSummaryCard({
  summary,
  studyPackReady,
  keyConceptCount,
  quizCount,
  onViewFullNotes,
}: NoteDetailSummaryCardProps) {
  const scope = studyPackReady ? getStudyPackScope({ keyConceptCount, quizCount }) : null;

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
      {scope ? (
        <p data-testid="study-pack-scope" className="text-sm text-foreground/65">
          {formatStudyPackScope(scope)}
        </p>
      ) : null}
      <SummaryMarkdown content={summary} />
    </Card>
  );
}
