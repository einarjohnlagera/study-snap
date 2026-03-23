"use client";

import Link from "next/link";
import { Button } from "@/components/ui/button";
import { PLAN_BILLING_PATH } from "@/lib/plans";

type NearLimitBannerProps = {
  className?: string;
};

export function NearLimitBanner({ className }: NearLimitBannerProps) {
  return (
    <div
      role="status"
      className={`rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 text-sm text-foreground/85 ${className ?? ""}`}
    >
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <p>
          You&apos;re almost at your monthly limit. Upgrade to Premium to continue generating Study Packs and unlock
          Challenge Quiz and Adaptive Practice.
        </p>
        <Link href={PLAN_BILLING_PATH} className="w-full shrink-0 sm:w-auto">
          <Button type="button" variant="outline" size="sm" className="w-full sm:w-auto">
            Upgrade to Premium
          </Button>
        </Link>
      </div>
    </div>
  );
}
