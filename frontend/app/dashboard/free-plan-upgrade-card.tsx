import Link from "next/link";
import { ArrowRight, Crown } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PLAN_BILLING_PATH } from "@/lib/plans";

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
            Upgrade to Premium to unlock Challenge Quiz and Adaptive Practice and generate up to 100 Study Packs per month.
          </p>
        </div>
        <Link href={PLAN_BILLING_PATH} className="w-full sm:w-auto">
          <Button type="button" className="w-full sm:w-auto">
            Upgrade to Premium
            <ArrowRight className="h-4 w-4" />
          </Button>
        </Link>
      </div>
    </Card>
  );
}
