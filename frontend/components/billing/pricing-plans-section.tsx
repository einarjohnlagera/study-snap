"use client";

import Link from "next/link";
import { Check, Crown, Minus } from "lucide-react";
import { PremiumWaitlistButton } from "@/components/billing/premium-waitlist-button";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { useBillingPricing } from "@/hooks/use-billing-pricing";
import { formatBillingAmount, getBillingCyclePriceLabel } from "@/lib/billing-pricing";
import { pricingConfig, resolvePricingDisplayRegion } from "@/lib/pricing-config";

type PricingPlansSectionProps = {
  showHeading?: boolean;
};

type ComparisonValue = "check" | string | null;
type ComparisonRow = {
  label: string;
  free: ComparisonValue;
  premium: ComparisonValue;
};

const FREE_FEATURES = [
  `${pricingConfig.free.studyPacksPerMonth} Study Packs / month`,
  `${pricingConfig.free.challengeQuizzesPerMonth} Challenge Quizzes / month`,
  "AI Summary + Key Concepts",
  "Weak Concepts tracking",
];

const FREE_LIMITATIONS = [
  "Adaptive Practice (Premium)",
  "Difficulty selection (Premium)",
  "Board Exam Mode (Premium)",
];

const PREMIUM_FEATURES = [
  `${pricingConfig.premium.studyPacksPerMonth} Study Packs / month`,
  `${pricingConfig.premium.challengeQuizzesPerMonth} Challenge Quizzes / month`,
  `${pricingConfig.premium.adaptivePracticePerMonth} Adaptive Practice sessions / month`,
  "Difficulty selection",
  "Board Exam Mode",
];

const COMPARISON_ROWS: ComparisonRow[] = [
  {
    label: "Study Packs / month",
    free: String(pricingConfig.free.studyPacksPerMonth),
    premium: String(pricingConfig.premium.studyPacksPerMonth),
  },
  {
    label: "Challenge Quizzes / month",
    free: String(pricingConfig.free.challengeQuizzesPerMonth),
    premium: String(pricingConfig.premium.challengeQuizzesPerMonth),
  },
  {
    label: "AI Summary + Key Concepts",
    free: "check",
    premium: "check",
  },
  {
    label: "Weak Concepts tracking",
    free: "check",
    premium: "check",
  },
  {
    label: "Adaptive Practice",
    free: null,
    premium: String(pricingConfig.premium.adaptivePracticePerMonth),
  },
  {
    label: "Difficulty selection",
    free: null,
    premium: "check",
  },
  {
    label: "Board Exam Mode",
    free: null,
    premium: "check",
  },
];

function ComparisonCell({ value, emphasize = false }: Readonly<{ value: ComparisonValue; emphasize?: boolean }>) {
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

export function PricingPlansSection({ showHeading = true }: Readonly<PricingPlansSectionProps>) {
  const { billingPricing } = useBillingPricing(true);
  const displayRegion = resolvePricingDisplayRegion(billingPricing?.region);
  const fallbackPrice = pricingConfig.price[displayRegion];
  const monthlyLabel = billingPricing
    ? getBillingCyclePriceLabel(billingPricing, "MONTHLY")
    : `${formatBillingAmount(fallbackPrice.monthly, fallbackPrice.currency)}/month`;
  const yearlyLabel = billingPricing
    ? getBillingCyclePriceLabel(billingPricing, "YEARLY")
    : `${formatBillingAmount(fallbackPrice.yearly, fallbackPrice.currency)}/year`;

  return (
    <section className="space-y-4">
      {showHeading ? (
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Pricing
          </p>
          <h2 className="text-2xl font-semibold sm:text-3xl">Free for everyday study. Premium for deeper exam prep.</h2>
          <p className="max-w-3xl text-sm text-foreground/75">
            Compare the core NoteLib workflow with the higher limits and premium practice tools designed for heavier review weeks.
          </p>
        </div>
      ) : null}
      <div className="grid gap-4 md:grid-cols-2">
        <Card className="space-y-5 p-4 sm:p-6">
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Free</p>
            <CardTitle>Start with the core NoteLib study flow.</CardTitle>
            <CardDescription>
              Build notes, generate Study Packs, and review with quizzes without paying upfront.
            </CardDescription>
          </div>
          <p className="text-3xl font-semibold">Free</p>
          <ul className="space-y-2 text-sm text-foreground/80">
            {FREE_FEATURES.map((feature) => (
              <li key={feature} className="flex items-start gap-2">
                <Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />
                {feature}
              </li>
            ))}
          </ul>
          <div className="space-y-2 rounded-lg border border-dashed border-border bg-background/70 p-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Premium-only extras</p>
            <ul className="space-y-2 text-sm text-foreground/75">
              {FREE_LIMITATIONS.map((feature) => (
                <li key={feature} className="flex items-start gap-2">
                  <Minus className="mt-0.5 h-4 w-4 text-foreground/55" />
                  {feature}
                </li>
              ))}
            </ul>
          </div>
          <Link href="/auth" className={buttonVariants({ variant: "outline", className: "w-full sm:w-auto" })}>
            Start for Free
          </Link>
        </Card>

        <Card className="space-y-5 border-blue-300 p-4 sm:p-6 dark:border-blue-700">
          <div className="space-y-2">
            <div className="inline-flex w-fit items-center gap-2 rounded-full border border-blue-500/20 bg-blue-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
              <Crown className="h-3.5 w-3.5" />
              Premium
            </div>
            <CardTitle>Move into deeper, exam-focused practice.</CardTitle>
            <CardDescription>
              Premium adds higher limits and targeted quiz tools for the weeks when review gets serious.
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
            {PREMIUM_FEATURES.map((feature) => (
              <li key={feature} className="flex items-start gap-2">
                <Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />
                {feature}
              </li>
            ))}
          </ul>
          <div className="space-y-2">
            <PremiumWaitlistButton
              label="Upgrade to Premium"
              source="pricing_plans_section"
              className="w-full sm:w-auto"
            />
            <p className="text-sm text-foreground/65">
              Premium pricing is shown now, but upgrades currently join the waitlist while checkout is still being prepared.
            </p>
          </div>
        </Card>
      </div>

      <Card className="overflow-hidden p-0">
        <div className="border-b border-border px-4 py-4 sm:px-6">
          <h3 className="text-lg font-semibold sm:text-xl">Plan comparison</h3>
          <p className="mt-1 text-sm text-foreground/70">
            Free covers the core study loop. Premium expands limits and unlocks deeper quiz training.
          </p>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-[36rem] text-sm">
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
              {COMPARISON_ROWS.map((row, rowIndex) => (
                <tr
                  key={row.label}
                  className={`border-b border-border ${rowIndex === COMPARISON_ROWS.length - 1 ? "border-b-0" : ""}`}
                >
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

export function SimplePricingSection() {
  return (
    <section className="space-y-5">
      <div className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Pricing
        </p>
        <h2 className="text-2xl font-semibold sm:text-3xl">Two plans. Clear upgrade path.</h2>
        <p className="max-w-3xl text-sm text-foreground/75">
          Start with Free for the full note-to-study-pack workflow, then move to Premium when you need more practice
          and deeper quiz control.
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <Card className="space-y-4 p-4 sm:p-6">
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Free</p>
            <CardTitle>For everyday review</CardTitle>
          </div>
          <ul className="space-y-2 text-sm text-foreground/80">
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />{pricingConfig.free.studyPacksPerMonth} Study Packs / month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />{pricingConfig.free.challengeQuizzesPerMonth} Challenge Quizzes / month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />AI Summary + Key Concepts</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Weak Concepts tracking</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Board Exam Mode (Free for limited time)</li>
          </ul>
          <Link href="/signup" className={buttonVariants({ className: "w-full sm:w-auto" })}>
            Start for Free
          </Link>
        </Card>

        <Card className="space-y-4 border-blue-300 p-4 sm:p-6 dark:border-blue-700">
          <div className="space-y-2">
            <div className="inline-flex w-fit items-center gap-2 rounded-full border border-blue-500/20 bg-blue-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
              <Crown className="h-3.5 w-3.5" />
              Premium
            </div>
            <CardTitle>For deeper exam prep</CardTitle>
          </div>
          <ul className="space-y-2 text-sm text-foreground/80">
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Higher monthly limits</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Adaptive Practice</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Difficulty selection</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Board Exam Mode</li>
          </ul>
          <Link href="/pricing" className={buttonVariants({ variant: "outline", className: "w-full sm:w-auto" })}>
            View Pricing
          </Link>
        </Card>
      </div>
    </section>
  );
}
