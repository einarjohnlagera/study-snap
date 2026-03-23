"use client";

import Link from "next/link";
import { Check, Crown } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { useBillingPricing } from "@/hooks/use-billing-pricing";
import { formatBillingAmount, getBillingCyclePriceLabel } from "@/lib/billing-pricing";
import { PLAN_BILLING_PATH } from "@/lib/plans";

export function PricingPlansSection() {
  const { billingPricing } = useBillingPricing(true);
  const monthlyLabel = getBillingCyclePriceLabel(billingPricing, "MONTHLY");
  const yearlyLabel = getBillingCyclePriceLabel(billingPricing, "YEARLY");

  return (
    <section className="space-y-4">
      <div className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Pricing
        </p>
        <h2 className="text-2xl font-semibold sm:text-3xl">Start free. Upgrade when you need more.</h2>
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        <Card className="space-y-4 p-4 sm:p-6">
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Free</p>
            <CardTitle>Core study workflow</CardTitle>
            <CardDescription>Build notes, generate Study Packs, and review with Quick Review.</CardDescription>
          </div>
          <p className="text-3xl font-semibold">Free</p>
          <ul className="space-y-2 text-sm text-foreground/80">
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />5 Study Packs per month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Quick Review and note saving</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Public Library access</li>
          </ul>
          <Link href="/auth" className={buttonVariants({ variant: "outline", className: "w-full sm:w-auto" })}>
            Create free account
          </Link>
        </Card>

        <Card className="space-y-4 border-blue-300 p-4 sm:p-6 dark:border-blue-700">
          <div className="space-y-2">
            <div className="inline-flex w-fit items-center gap-2 rounded-full border border-blue-500/20 bg-blue-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
              <Crown className="h-3.5 w-3.5" />
              Premium
            </div>
            <CardTitle>Exam-ready study tools</CardTitle>
            <CardDescription>
              Pricing is localized by region and returned by the billing API, so checkout and display stay in sync.
            </CardDescription>
          </div>
          <div className="space-y-1">
            <p className="text-xl font-semibold text-foreground">{monthlyLabel}</p>
            <p className="text-sm text-foreground/70">{yearlyLabel}</p>
            {billingPricing ? (
              <p className="text-xs text-foreground/60">
                Region {billingPricing.region} · currency {billingPricing.currency} · base monthly{" "}
                {formatBillingAmount(billingPricing.monthlyPrice, billingPricing.currency)}
              </p>
            ) : null}
          </div>
          <ul className="space-y-2 text-sm text-foreground/80">
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />100 Study Packs per month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Challenge Quiz and Adaptive Practice</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Higher limits and Premium review tools</li>
          </ul>
          <Link href={PLAN_BILLING_PATH} className={buttonVariants({ className: "w-full sm:w-auto" })}>
            Upgrade to Premium
          </Link>
        </Card>
      </div>
    </section>
  );
}
