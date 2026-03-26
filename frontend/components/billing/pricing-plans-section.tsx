"use client";

import Link from "next/link";
import { Fragment } from "react";
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

type ComparisonSection = {
  title: string;
  rows: ComparisonRow[];
};

const COMPARISON_SECTIONS: ComparisonSection[] = [
  {
    title: "Core Features",
    rows: [
      {
        label: "Unlimited Notes",
        free: "check",
        premium: "check",
      },
      {
        label: "File Uploads (PDF, DOCX, TXT)",
        free: "check",
        premium: "check",
      },
      {
        label: "Public Library Access",
        free: "check",
        premium: "check",
      },
      {
        label: "Weak Concepts Insights",
        free: "check",
        premium: "check",
      },
    ],
  },
  {
    title: "Monthly Limits",
    rows: [
      {
        label: "AI Study Packs / month",
        free: String(pricingConfig.free.studyPacksPerMonth),
        premium: String(pricingConfig.premium.studyPacksPerMonth),
      },
      {
        label: "Image to Text (OCR)",
        free: "Limited",
        premium: "Higher Limits",
      },
      {
        label: "Challenge Quizzes / month",
        free: String(pricingConfig.free.challengeQuizzesPerMonth),
        premium: String(pricingConfig.premium.challengeQuizzesPerMonth),
      },
    ],
  },
  {
    title: "Premium Features",
    rows: [
      {
        label: "Adaptive Practice",
        free: null,
        premium: String(pricingConfig.premium.adaptivePracticePerMonth),
      },
      {
        label: "Choose Quiz Difficulty",
        free: null,
        premium: "check",
      },
      {
        label: "Priority AI Processing",
        free: null,
        premium: "check",
      },
    ],
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
            <CardTitle>Start studying for free.</CardTitle>
            <CardDescription>Free gives you enough room to build notes, generate reviewers, and spot weak areas before exams.</CardDescription>
          </div>
          <p className="text-3xl font-semibold">Free</p>
          <ul className="space-y-2 text-sm text-foreground/80">
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Unlimited Notes</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />{pricingConfig.free.studyPacksPerMonth} AI Study Packs / month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />File Uploads (PDF, DOCX, TXT)</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Image to Text (OCR) - Limited</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />{pricingConfig.free.challengeQuizzesPerMonth} Challenge Quizzes / month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Public Library Access</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Weak Concepts Insights</li>
          </ul>
          <Link href="/auth" className={buttonVariants({ variant: "outline", className: "w-full sm:w-auto" })}>
            Start for Free
          </Link>
        </Card>

        <Card className="space-y-4 border-blue-300 p-4 sm:p-6 dark:border-blue-700">
          <div className="space-y-2">
            <div className="inline-flex w-fit items-center gap-2 rounded-full border border-blue-500/20 bg-blue-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
              <Crown className="h-3.5 w-3.5" />
              Premium
            </div>
            <CardTitle>Unlock adaptive practice and advanced quizzes.</CardTitle>
            <CardDescription>
              Premium is for serious review weeks when you want more practice, stronger quiz control, and higher limits.
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
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />{pricingConfig.premium.studyPacksPerMonth} AI Study Packs / month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Everything in Free</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Higher OCR Limits</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />{pricingConfig.premium.challengeQuizzesPerMonth} Challenge Quizzes / month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />{pricingConfig.premium.adaptivePracticePerMonth} Adaptive Practice / month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Choose Quiz Difficulty</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Priority AI Processing</li>
          </ul>
          <div className="space-y-2">
            <PremiumWaitlistButton
              label="Upgrade to Premium"
              source="pricing_plans_section"
              className="w-full sm:w-auto"
            />
            <p className="text-sm text-foreground/65">More practice. Better results.</p>
          </div>
        </Card>
      </div>

      <Card className="overflow-hidden p-0">
        <div className="border-b border-border px-4 py-4 sm:px-6">
          <h3 className="text-lg font-semibold sm:text-xl">Plan comparison</h3>
          <p className="mt-1 text-sm text-foreground/70">
            Free helps you study consistently. Premium unlocks deeper practice and higher limits when exams get serious.
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
              {COMPARISON_SECTIONS.map((section) => (
                <Fragment key={section.title}>
                  <tr className="bg-muted/20">
                    <td className="px-4 pb-2 pt-5 text-xs font-semibold uppercase tracking-wide text-foreground/55 sm:px-6">
                      {section.title}
                    </td>
                    <td className="px-4 pb-2 pt-5 sm:px-6" />
                    <td className="bg-blue-500/8 px-4 pb-2 pt-5 sm:px-6 dark:bg-blue-500/12" />
                  </tr>
                  {section.rows.map((row, rowIndex) => (
                    <tr
                      key={row.label}
                      className={`border-b border-border ${rowIndex === section.rows.length - 1 ? "last:border-b-0" : ""}`}
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
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </section>
  );
}
