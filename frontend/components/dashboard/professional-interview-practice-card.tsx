import { BriefcaseBusiness } from "lucide-react";
import { ResponsiveActionLink } from "@/components/ui/action-button";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import type { MePlanResponse } from "@/lib/api";
import type { AppPlanType } from "@/src/config/plans";
import { getUpgradeCtas } from "@/src/config/plans";

type ProfessionalInterviewPracticeCardProps = {
  currentPlan: AppPlanType;
  usageSummary: MePlanResponse | null;
  readyNoteId?: string | null;
  onUpgrade: () => void;
};

export function ProfessionalInterviewPracticeCard({
  currentPlan,
  usageSummary,
  readyNoteId,
  onUpgrade,
}: Readonly<ProfessionalInterviewPracticeCardProps>) {
  const isPro = currentPlan === "PRO";
  const used = usageSummary?.usage.interviewPracticeUsed ?? 0;
  const limit = usageSummary?.limits.interviewPracticePerMonth ?? 10;
  const upgradeCtas = getUpgradeCtas(currentPlan, "interview-practice");
  const startHref = readyNoteId ? `/notes/${readyNoteId}/interview-practice` : "/library";

  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <div className="flex items-start gap-3">
        <span className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-primary/20 bg-primary/10 text-primary">
          <BriefcaseBusiness className="h-5 w-5" aria-hidden="true" />
        </span>
        <div className="min-w-0 space-y-1">
          <h2 className="text-lg font-semibold sm:text-xl">Interview Practice</h2>
          <p className="text-sm text-foreground/75">
            Answer scenario-style prompts and get senior interviewer critique after each choice.
          </p>
        </div>
      </div>
      {isPro ? (
        <p className="text-sm text-foreground/65">
          {used} of {limit} sessions used this month.
        </p>
      ) : (
        <p className="text-sm text-foreground/65">
          Pro unlocks coached interview sessions with per-answer feedback and readiness reports.
        </p>
      )}
      <div className="flex flex-col gap-2 sm:flex-row">
        {isPro ? (
          <ResponsiveActionLink
            href={startHref}
            action="adaptivePractice"
            label={readyNoteId ? "Start Interview Practice" : "Choose a Ready Note"}
            className="w-full sm:w-auto"
          />
        ) : (
          <Button type="button" className="w-full sm:w-auto" onClick={onUpgrade}>
            {upgradeCtas.primary?.label ?? "Unlock Interview Practice"}
          </Button>
        )}
        <ResponsiveActionLink href="/library" action="library" label="Open Library" variant="outline" className="w-full sm:w-auto" />
      </div>
    </Card>
  );
}
