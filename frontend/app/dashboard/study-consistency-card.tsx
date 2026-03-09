import { Card } from "@/components/ui/card";

export function StudyConsistencyCard() {
  return (
    <Card className="space-y-4">
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Study Consistency
        </p>
        <h2 className="text-xl font-semibold">Build your study habit</h2>
        <p className="text-sm text-foreground/75">
          Create or review at least one Study Pack today to keep your learning momentum.
        </p>
      </div>
    </Card>
  );
}
