"use client";

import Link from "next/link";
import { Check, Crown, Minus } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { useBillingPricing } from "@/hooks/use-billing-pricing";
import { trackAnalyticsEvent } from "@/lib/api";
import { formatBillingAmount, getBillingCyclePriceLabel } from "@/lib/billing-pricing";
import { PLAN_BILLING_PATH } from "@/lib/plans";

type PricingPlansSectionProps = {
  showHeading?: boolean;
};

type ComparisonValue = "check" | string | null;

const COMPARISON_ROWS = [
  {
    label: "Create Notes",
    free: "check",
    premium: "check",
  },
  {
    label: "Save Notes",
    free: "check",
    premium: "check",
  },
  {
    label: "Study Packs per month",
    free: "5",
    premium: "100",
  },
  {
    label: "Quick Review",
    free: "check",
    premium: "check",
  },
  {
    label: "Public Library Access",
    free: "check",
    premium: "check",
  },
  {
    label: "Challenge Quiz (Exam Mode)",
    free: null,
    premium: "check",
  },
  {
    label: "Adaptive Practice",
    free: null,
    premium: "check",
  },
  {
    label: "Priority AI generation",
    free: null,
    premium: "check",
  },
];

function ComparisonCell({ value, emphasize = false }: { value: ComparisonValue; emphasize?: boolean }) {
  const baseClassName = emphasize
    ? "text-foreground"
    : "text-foreground/85";

  if (value === null) {
    return (
      <span className={`inline-flex min-h-6 min-w-6 items-center justify-center ${baseClassName}`} aria-label="Not included">
        <Minus className="h-4 w-4 text-foreground/55" aria-hidden="true" />
      </span>
    );
  }

  if (value === "check") {
    return (
      <span className={`inline-flex min-h-6 min-w-6 items-center justify-center ${baseClassName}`} aria-label="Included">
        <Check className="h-4 w-4 text-blue-600 dark:text-blue-400" aria-hidden="true" />
      </span>
    );
  }

  return (
    <span className={`inline-flex min-h-6 items-center justify-center font-medium tabular-nums ${baseClassName}`}>
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
          <Link
            href={PLAN_BILLING_PATH}
            className={buttonVariants({ className: "w-full sm:w-auto" })}
            onClick={() => {
              void trackAnalyticsEvent({
                eventType: "UPGRADE_CLICKED",
                metadata: {
                  source: "pricing_plans_section",
                },
              });
            }}
          >
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
          <table className="min-w-[40rem] text-sm">
            <thead>
              <tr className="border-b border-border bg-muted/30 text-left">
                <th className="px-4 py-3 font-semibold sm:px-6">Feature</th>
                <th className="px-4 py-3 text-center font-semibold sm:px-6">Free</th>
                <th className="bg-blue-500/8 px-4 py-3 text-center font-semibold text-foreground sm:px-6 dark:bg-blue-500/12">
                  Premium
                </th>
              </tr>
            </thead>
            <tbody>
              {COMPARISON_ROWS.map((row) => (
                <tr key={row.label} className="border-b border-border last:border-b-0">
                  <td className="px-4 py-3 font-medium sm:px-6">{row.label}</td>
                  <td className="px-4 py-3 text-center align-middle sm:px-6">
                    <ComparisonCell value={row.free} />
                  </td>
                  <td className="bg-blue-500/8 px-4 py-3 text-center align-middle sm:px-6 dark:bg-blue-500/12">
                    <ComparisonCell value={row.premium} emphasize />
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
