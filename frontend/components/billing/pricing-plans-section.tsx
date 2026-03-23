"use client";

import Link from "next/link";
import { Check, Crown, Minus } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { useBillingPricing } from "@/hooks/use-billing-pricing";
import { formatBillingAmount, getBillingCyclePriceLabel } from "@/lib/billing-pricing";
import { PLAN_BILLING_PATH } from "@/lib/plans";

type PricingPlansSectionProps = {
  showHeading?: boolean;
};

const COMPARISON_ROWS = [
  {
    label: "Create Notes",
    free: "Included",
    premium: "Included",
  },
  {
    label: "Save Notes",
    free: "Included",
    premium: "Included",
  },
  {
    label: "Study Packs per month",
    free: "5",
    premium: "100",
  },
  {
    label: "Quick Review",
    free: "Included",
    premium: "Included",
  },
  {
    label: "Public Library Access",
    free: "Included",
    premium: "Included",
  },
  {
    label: "Challenge Quiz (Exam Mode)",
    free: null,
    premium: "Included",
  },
  {
    label: "Adaptive Practice (Focus on weak topics)",
    free: null,
    premium: "Included",
  },
  {
    label: "Priority AI generation",
    free: null,
    premium: "Included",
  },
];

function ComparisonCell({ value }: { value: string | null }) {
  if (value === null) {
    return (
      <span className="inline-flex items-center gap-2 text-foreground/55">
        <Minus className="h-4 w-4" />
        Not included
      </span>
    );
  }

  return (
    <span className="inline-flex items-center gap-2 text-foreground/85">
      <Check className="h-4 w-4 text-blue-600 dark:text-blue-400" />
      {value}
    </span>
  );
}

export function PricingPlansSection({ showHeading = true }: PricingPlansSectionProps) {
  const { billingPricing } = useBillingPricing(true);
  const monthlyLabel = getBillingCyclePriceLabel(billingPricing, "MONTHLY");
  const yearlyLabel = getBillingCyclePriceLabel(billingPricing, "YEARLY");

  return (
    <section className="space-y-4">
      {showHeading ? (
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Pricing
          </p>
          <h2 className="text-2xl font-semibold sm:text-3xl">Move from note-taking to exam prep</h2>
          <p className="max-w-3xl text-sm text-foreground/75">
            Free helps you get started. Premium adds the exam-style practice and extra generation room students need during serious review weeks.
          </p>
        </div>
      ) : null}
      <div className="grid gap-4 md:grid-cols-2">
        <Card className="space-y-4 p-4 sm:p-6">
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Free</p>
            <CardTitle>Build your study routine</CardTitle>
            <CardDescription>Create notes, generate a few Study Packs, and review with Quick Review.</CardDescription>
          </div>
          <p className="text-3xl font-semibold">Free</p>
          <ul className="space-y-2 text-sm text-foreground/80">
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Create Notes</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Save Notes</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />5 Study Packs per month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Quick Review</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Public Library Access</li>
          </ul>
          <Link href="/auth" className={buttonVariants({ variant: "outline", className: "w-full sm:w-auto" })}>
            Start Free
          </Link>
        </Card>

        <Card className="space-y-4 border-blue-300 p-4 sm:p-6 dark:border-blue-700">
          <div className="space-y-2">
            <div className="inline-flex w-fit items-center gap-2 rounded-full border border-blue-500/20 bg-blue-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
              <Crown className="h-3.5 w-3.5" />
              Premium
            </div>
            <CardTitle>Train for exams with purpose</CardTitle>
            <CardDescription>
              Premium is built for exam preparation, weak-topic practice, and heavier study pack generation during crunch time.
            </CardDescription>
          </div>
          <div className="space-y-1">
            <p className="text-xl font-semibold text-foreground">{monthlyLabel}</p>
            <p className="text-sm text-foreground/70">{yearlyLabel}</p>
            {billingPricing ? (
              <p className="text-xs text-foreground/60">
                {billingPricing.introEligible && billingPricing.introMonthlyPrice !== null
                  ? `First month ${formatBillingAmount(billingPricing.introMonthlyPrice, billingPricing.currency)}`
                  : `Monthly base ${formatBillingAmount(billingPricing.monthlyPrice, billingPricing.currency)}`}{" "}
                · Region {billingPricing.region}
              </p>
            ) : null}
          </div>
          <ul className="space-y-2 text-sm text-foreground/80">
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Everything in Free</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />100 Study Packs per month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Challenge Quiz (Exam Mode)</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Adaptive Practice (Focus on weak topics)</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Priority AI generation</li>
          </ul>
          <Link href={PLAN_BILLING_PATH} className={buttonVariants({ className: "w-full sm:w-auto" })}>
            Upgrade to Premium
          </Link>
        </Card>
      </div>

      <Card className="overflow-hidden p-0">
        <div className="border-b border-border px-4 py-4 sm:px-6">
          <h3 className="text-lg font-semibold sm:text-xl">Plan comparison</h3>
          <p className="mt-1 text-sm text-foreground/70">
            Premium is positioned as the exam preparation plan, not just a bigger AI quota.
          </p>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="border-b border-border bg-muted/30 text-left">
                <th className="px-4 py-3 font-semibold sm:px-6">Feature</th>
                <th className="px-4 py-3 font-semibold sm:px-6">Free</th>
                <th className="px-4 py-3 font-semibold sm:px-6">Premium</th>
              </tr>
            </thead>
            <tbody>
              {COMPARISON_ROWS.map((row) => (
                <tr key={row.label} className="border-b border-border last:border-b-0">
                  <td className="px-4 py-3 font-medium sm:px-6">{row.label}</td>
                  <td className="px-4 py-3 sm:px-6">
                    <ComparisonCell value={row.free} />
                  </td>
                  <td className="px-4 py-3 sm:px-6">
                    <ComparisonCell value={row.premium} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </section>
  );
}
