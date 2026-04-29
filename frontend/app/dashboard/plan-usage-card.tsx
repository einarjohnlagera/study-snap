import { PremiumUpgradeButton } from "@/components/billing/premium-upgrade-button";
import { Card } from "@/components/ui/card";
import { getUsageProgressPercent } from "@/lib/plans";

type PlanUsageCardProps = {
  usedThisMonth: number;
  monthlyLimit: number;
};

export function PlanUsageCard({ usedThisMonth, monthlyLimit }: Readonly<PlanUsageCardProps>) {
  const usagePercent = getUsageProgressPercent(usedThisMonth, monthlyLimit);
  const hasReachedLimit = monthlyLimit > 0 && usedThisMonth >= monthlyLimit;

  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Free Plan</p>
        <p className="text-sm text-foreground/80">
          {usedThisMonth} / {monthlyLimit} Study Packs used this month
        </p>
      </div>

      <div className="h-2 overflow-hidden rounded-full bg-muted">
        <div
          className="h-full rounded-full bg-blue-600 transition-all dark:bg-blue-400"
          style={{ width: `${usagePercent}%` }}
        />
      </div>

      {hasReachedLimit ? (
        <div className="space-y-3 rounded-md border border-border bg-background p-3">
          <p className="text-sm text-foreground/85">You have reached your monthly Study Pack limit.</p>
          <PremiumUpgradeButton label="Upgrade to Premium" source="dashboard_plan_usage_limit" size="sm" />
        </div>
      ) : (
        <PremiumUpgradeButton label="Upgrade" source="dashboard_plan_usage_card" variant="outline" size="sm" />
      )}
    </Card>
  );
}
