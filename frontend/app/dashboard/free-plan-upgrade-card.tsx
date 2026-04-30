import { Crown } from "lucide-react";
import { PremiumUpgradeButton } from "@/components/billing/premium-upgrade-button";
import { Card } from "@/components/ui/card";

export function FreePlanUpgradeCard() {
  return (
    <Card className="overflow-hidden border-blue-500/25 bg-gradient-to-br from-blue-500/10 via-background to-amber-500/10 p-4 sm:p-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-2">
          <div className="inline-flex items-center gap-2 rounded-full border border-blue-500/20 bg-blue-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
            <Crown className="h-3.5 w-3.5" />
            Free Plan
          </div>
          <h2 className="text-lg font-semibold sm:text-xl">
            You are using the Free Plan.
          </h2>
          <p className="max-w-2xl text-sm leading-relaxed text-foreground/80">
            Upgrade to Plus for higher limits, or go Pro for Adaptive Practice, difficulty selection, and Board Exam Mode.
          </p>
        </div>
        <div className="w-full sm:w-auto">
          <PremiumUpgradeButton
            label="Upgrade to Plus"
            source="dashboard_free_plan_card"
            planType="PLUS"
            className="w-full sm:w-auto"
          />
        </div>
      </div>
    </Card>
  );
}
