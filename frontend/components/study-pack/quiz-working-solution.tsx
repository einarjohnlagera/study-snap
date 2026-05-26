import type { QuizItem } from "@/lib/api";

type QuizWorkingSolutionProps = {
  workingSolution: string | null | undefined;
  alwaysShow?: boolean;
  planType?: string | null;
};

export function hasComputationalWorkingSolution(
  question: Pick<QuizItem, "questionType" | "workingSolution"> | null | undefined,
) {
  return question?.questionType === "COMPUTATIONAL" && Boolean(question.workingSolution?.trim());
}

export function QuizWorkingSolution({
  workingSolution,
  alwaysShow = false,
  planType = null,
}: Readonly<QuizWorkingSolutionProps>) {
  const trimmedSolution = workingSolution?.trim();

  if (!trimmedSolution) {
    return null;
  }
  if (!alwaysShow && planType !== "PRO") {
    return null;
  }

  return (
    <div className="space-y-2 rounded-md border border-blue-500/20 bg-blue-500/5 p-3">
      <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
        Working Solution
      </p>
      <pre className="whitespace-pre-wrap break-words rounded-md border border-border bg-background/70 p-3 font-mono text-xs leading-relaxed text-foreground/80">
        {trimmedSolution}
      </pre>
      <p className="text-xs text-foreground/55">AI-generated — verify calculations</p>
    </div>
  );
}
