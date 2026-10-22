import { ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { CHALLENGE_PREPARATION_COPY as COPY } from "@/lib/challenge-quiz-preparation-copy";

export function ChallengeContinuationCard({ title, meta, action, onOpen }: Readonly<{
  title: string;
  meta?: string;
  action: string;
  onOpen: () => void;
}>) {
  return (
    <Card className="space-y-3 border-blue-500/20 bg-blue-500/[0.035] p-3 sm:p-4">
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">{COPY.continueEyebrow}</p>
        <h2 className="text-base font-semibold text-foreground">{title}</h2>
        {meta ? <p className="text-sm text-foreground/70">{meta}</p> : null}
      </div>
      <Button type="button" className="gap-2" onClick={onOpen}>
        {action}<ArrowRight className="h-4 w-4" aria-hidden="true" />
      </Button>
    </Card>
  );
}
