"use client";

import Link from "next/link";
import { Check, Minus, Sparkles } from "lucide-react";
import type { PaidPlanType } from "@/lib/api";
import { PremiumUpgradeButton } from "@/components/billing/premium-upgrade-button";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { useBillingPricing } from "@/hooks/use-billing-pricing";
import { formatBillingAmount, getBillingCyclePriceLabel, resolveCyclePricing } from "@/lib/billing-pricing";
import { pricingConfig, resolvePricingDisplayRegion } from "@/lib/pricing-config";

type PricingPlansSectionProps = {
  showHeading?: boolean;
};

type ComparisonValue = "check" | string | null;
type ComparisonRow = {
  label: string;
  free: ComparisonValue;
  plus: ComparisonValue;
  pro: ComparisonValue;
};

const COMPARISON_ROWS: ComparisonRow[] = [
  {
    label: "Study Packs / month",
    free: String(pricingConfig.free.studyPacksPerMonth),
    plus: String(pricingConfig.plus.studyPacksPerMonth),
    pro: String(pricingConfig.pro.studyPacksPerMonth),
  },
  {
    label: "Quizzes / month",
    free: String(pricingConfig.free.challengeQuizzesPerMonth),
    plus: String(pricingConfig.plus.challengeQuizzesPerMonth),
    pro: String(pricingConfig.pro.challengeQuizzesPerMonth),
  },
  {
    label: "Exports / month",
    free: String(pricingConfig.free.exportsPerMonth),
    plus: String(pricingConfig.plus.exportsPerMonth),
    pro: "Unlimited",
  },
  {
    label: "Summary + Key Concepts",
    free: "check",
    plus: "check",
    pro: "check",
  },
  {
    label: "Topic note generation",
    free: "Limited",
    plus: "Higher",
    pro: "Highest",
  },
  {
    label: "Adaptive Practice",
    free: null,
    plus: null,
    pro: "check",
  },
  {
    label: "Difficulty selection",
    free: null,
    plus: null,
    pro: "check",
  },
  {
    label: "Board Exam Mode",
    free: null,
    plus: null,
    pro: "check",
  },
];

function ComparisonCell({ value, emphasize = false }: Readonly<{ value: ComparisonValue; emphasize?: boolean }>) {
  const baseClassName = emphasize ? "text-foreground" : "text-foreground/85";

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

function resolveFallbackCycleLabel(planType: PaidPlanType, region: ReturnType<typeof resolvePricingDisplayRegion>) {
  const regionPricing = pricingConfig.price[region];
  const introPricing = pricingConfig.intro[region];
  const planPricing = planType === "PLUS" ? regionPricing.plus : regionPricing.pro;
  const introAmount = planType === "PLUS" ? introPricing.plus.monthly : introPricing.pro.monthly;

  return {
    monthly: introAmount === null
      ? `${formatBillingAmount(planPricing.monthly, regionPricing.currency)}/month`
      : `First month ${formatBillingAmount(introAmount, regionPricing.currency)}, then ${formatBillingAmount(planPricing.monthly, regionPricing.currency)}/month`,
    yearly: planPricing.yearly === null
      ? null
      : `${formatBillingAmount(planPricing.yearly, regionPricing.currency)}/year`,
  };
}

function PlanCard({
  label,
  eyebrow,
  title,
  description,
  monthlyLabel,
  yearlyLabel,
  introHint,
  highlights,
  planType,
  accent = false,
}: Readonly<{
  label: string;
  eyebrow?: string;
  title: string;
  description: string;
  monthlyLabel: string;
  yearlyLabel?: string | null;
  introHint?: string | null;
  highlights: string[];
  planType: PaidPlanType;
  accent?: boolean;
}>) {
  return (
    <Card className={`space-y-5 p-4 transition-[transform,box-shadow,border-color,background-color] duration-150 ease-out hover:-translate-y-0.5 hover:shadow-lg sm:p-6 ${
      accent ? "border-blue-300 bg-blue-50/35 shadow-sm dark:border-blue-700 dark:bg-blue-950/18" : ""
    }`}>
      <div className="space-y-2">
        {eyebrow ? (
          <div className="inline-flex w-fit items-center gap-2 rounded-full border border-blue-500/20 bg-blue-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
            <Sparkles className="h-3.5 w-3.5" />
            {eyebrow}
          </div>
        ) : (
          <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">{label}</p>
        )}
        <CardTitle>{title}</CardTitle>
        <CardDescription className="leading-relaxed">{description}</CardDescription>
      </div>
      <div className="space-y-1">
        <p className="text-xl font-semibold text-foreground">{monthlyLabel}</p>
        {yearlyLabel ? <p className="text-sm text-foreground/70">{yearlyLabel}</p> : null}
        {introHint ? <p className="text-xs text-foreground/60">{introHint}</p> : null}
      </div>
      <ul className="space-y-2 text-sm text-foreground/80">
        {highlights.map((feature) => (
          <li key={feature} className="flex items-start gap-2">
            <Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />
            {feature}
          </li>
        ))}
      </ul>
      <div className="space-y-2">
        <PremiumUpgradeButton
          label={planType === "PLUS" ? "Choose Plus" : "Choose Pro"}
          source={`pricing_plans_section_${planType.toLowerCase()}_monthly`}
          planType={planType}
          billingCycle="MONTHLY"
          className="w-full"
        />
        {yearlyLabel ? (
          <PremiumUpgradeButton
            label={planType === "PLUS" ? "Choose Plus Yearly" : "Choose Pro Yearly"}
            source={`pricing_plans_section_${planType.toLowerCase()}_yearly`}
            planType={planType}
            billingCycle="YEARLY"
            variant="outline"
            className="w-full"
          />
        ) : null}
      </div>
    </Card>
  );
}

export function PricingPlansSection({ showHeading = true }: Readonly<PricingPlansSectionProps>) {
  const { billingPricing } = useBillingPricing(true);
  const displayRegion = resolvePricingDisplayRegion(billingPricing?.region);
  const freeFallback = pricingConfig.free;
  const regionFallback = pricingConfig.price[displayRegion];
  const plusFallback = resolveFallbackCycleLabel("PLUS", displayRegion);
  const proFallback = resolveFallbackCycleLabel("PRO", displayRegion);

  const plusMonthlyLabel = billingPricing
    ? getBillingCyclePriceLabel(billingPricing, "PLUS", "MONTHLY")
    : plusFallback.monthly;
  const plusYearlyLabel = billingPricing
    ? (resolveCyclePricing(billingPricing, "PLUS", "YEARLY")?.available ? getBillingCyclePriceLabel(billingPricing, "PLUS", "YEARLY") : null)
    : plusFallback.yearly;
  const proMonthlyLabel = billingPricing
    ? getBillingCyclePriceLabel(billingPricing, "PRO", "MONTHLY")
    : proFallback.monthly;
  const proYearlyLabel = billingPricing
    ? (resolveCyclePricing(billingPricing, "PRO", "YEARLY")?.available ? getBillingCyclePriceLabel(billingPricing, "PRO", "YEARLY") : null)
    : proFallback.yearly;

  const plusIntroHint = billingPricing?.plus.monthly.introEligible && billingPricing.plus.monthly.introAmount !== null
    ? `Intro offer: ${formatBillingAmount(billingPricing.plus.monthly.introAmount, billingPricing.currency)} for your first monthly checkout.`
    : pricingConfig.intro[displayRegion].plus.monthly !== null
      ? `Intro offer: ${formatBillingAmount(pricingConfig.intro[displayRegion].plus.monthly, regionFallback.currency)} for your first monthly checkout.`
      : null;
  const proIntroHint = billingPricing?.pro.monthly.introEligible && billingPricing.pro.monthly.introAmount !== null
    ? `Intro offer: ${formatBillingAmount(billingPricing.pro.monthly.introAmount, billingPricing.currency)} for your first monthly checkout.`
    : pricingConfig.intro[displayRegion].pro.monthly !== null
      ? `Intro offer: ${formatBillingAmount(pricingConfig.intro[displayRegion].pro.monthly, regionFallback.currency)} for your first monthly checkout.`
      : null;

  return (
    <section className="space-y-4">
      {showHeading ? (
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Pricing
          </p>
          <h2 className="text-2xl font-semibold sm:text-3xl">Free to start. Plus for consistency. Pro for serious review.</h2>
          <p className="max-w-3xl text-sm leading-relaxed text-foreground/75">
            Choose the plan that fits your study pace. Free covers the core note-to-study loop, Plus raises your limits, and Pro unlocks the full exam-prep toolkit.
          </p>
        </div>
      ) : null}

      <div className="grid gap-4 xl:grid-cols-3">
        <Card className="space-y-5 p-4 transition-[transform,box-shadow,border-color] duration-150 ease-out hover:-translate-y-0.5 hover:shadow-md sm:p-6">
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Free</p>
            <CardTitle>For getting started</CardTitle>
            <CardDescription className="leading-relaxed">
              Start building your note library, generate Study Packs, and review without paying upfront.
            </CardDescription>
          </div>
          <p className="text-3xl font-semibold">Free</p>
          <ul className="space-y-2 text-sm text-foreground/80">
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />{freeFallback.studyPacksPerMonth} Study Packs / month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />{freeFallback.challengeQuizzesPerMonth} Quizzes / month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />{freeFallback.exportsPerMonth} exports / month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Summary + Key Concepts</li>
          </ul>
          <div className="space-y-2 rounded-lg border border-dashed border-border bg-background/70 p-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Paid upgrades</p>
            <ul className="space-y-2 text-sm text-foreground/75">
              <li className="flex items-start gap-2"><Minus className="mt-0.5 h-4 w-4 text-foreground/55" />Adaptive Practice</li>
              <li className="flex items-start gap-2"><Minus className="mt-0.5 h-4 w-4 text-foreground/55" />Difficulty selection</li>
              <li className="flex items-start gap-2"><Minus className="mt-0.5 h-4 w-4 text-foreground/55" />Board Exam Mode</li>
              <li className="flex items-start gap-2"><Minus className="mt-0.5 h-4 w-4 text-foreground/55" />Higher note generation limits</li>
            </ul>
          </div>
          <Link href="/auth" className={buttonVariants({ variant: "outline", className: "w-full sm:w-auto" })}>
            Start for Free
          </Link>
        </Card>

        <PlanCard
          label="Plus"
          title="For regular study"
          description="Raise your monthly limits, export more, and keep your day-to-day review flowing."
          monthlyLabel={plusMonthlyLabel}
          yearlyLabel={plusYearlyLabel}
          introHint={plusIntroHint}
          highlights={[
            `${pricingConfig.plus.studyPacksPerMonth} Study Packs / month`,
            `${pricingConfig.plus.challengeQuizzesPerMonth} Quizzes / month`,
            `${pricingConfig.plus.exportsPerMonth} exports / month`,
            "Higher topic note generation limits",
          ]}
          planType="PLUS"
        />

        <PlanCard
          label="Pro"
          eyebrow="Most popular"
          title="Best for exam prep"
          description="Get the highest limits plus Adaptive Practice, difficulty selection, Board Exam Mode, and unlimited exports."
          monthlyLabel={proMonthlyLabel}
          yearlyLabel={proYearlyLabel}
          introHint={proIntroHint}
          highlights={[
            `${pricingConfig.pro.studyPacksPerMonth} Study Packs / month`,
            `${pricingConfig.pro.challengeQuizzesPerMonth} Quizzes / month`,
            "Unlimited exports",
            "Adaptive Practice",
            "Difficulty selection",
            "Board Exam Mode",
          ]}
          planType="PRO"
          accent
        />
      </div>

      <Card className="overflow-hidden p-0">
        <div className="border-b border-border px-4 py-4 sm:px-6">
          <h3 className="text-lg font-semibold sm:text-xl">Plan comparison</h3>
          <p className="mt-1 text-sm text-foreground/70">
            Free covers the core study loop. Plus expands your limits. Pro adds the full exam-prep toolkit.
          </p>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-[720px] text-sm">
            <thead>
              <tr className="border-b border-border bg-muted/30 text-left">
                <th className="px-4 py-3 font-semibold sm:px-6">Feature</th>
                <th className="px-4 py-3 text-center font-semibold sm:px-6">Free</th>
                <th className="px-4 py-3 text-center font-semibold sm:px-6">Plus</th>
                <th className="bg-blue-500/8 px-4 py-3 text-center font-semibold text-foreground sm:px-6 dark:bg-blue-500/12">Pro</th>
              </tr>
            </thead>
            <tbody>
              {COMPARISON_ROWS.map((row, rowIndex) => (
                <tr key={row.label} className={`border-b border-border ${rowIndex === COMPARISON_ROWS.length - 1 ? "border-b-0" : ""}`}>
                  <td className="px-4 py-3 font-medium sm:px-6">{row.label}</td>
                  <td className="px-4 py-3 text-center align-middle sm:px-6"><ComparisonCell value={row.free} /></td>
                  <td className="px-4 py-3 text-center align-middle sm:px-6"><ComparisonCell value={row.plus} /></td>
                  <td className="bg-blue-500/8 px-4 py-3 text-center align-middle sm:px-6 dark:bg-blue-500/12"><ComparisonCell value={row.pro} emphasize /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <Card className="space-y-4 p-4 sm:p-6">
        <div className="space-y-1">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">Regional Pricing</p>
          <h3 className="text-lg font-semibold">Pricing shown clearly</h3>
          <p className="text-sm leading-relaxed text-foreground/70">Prices are shown for Philippines (PHP) and international (USD) using backend pricing data when available.</p>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2 rounded-2xl border border-border bg-muted/20 p-4">
            <p className="text-sm font-semibold text-foreground">🇵🇭 Philippines pricing (PHP)</p>
            <p className="text-sm text-foreground/80">Plus: ₱{pricingConfig.price.PH.plus.monthly}/month</p>
            <p className="text-sm text-foreground/80">Pro: ₱{pricingConfig.price.PH.pro.monthly}/month</p>
            <p className="text-sm text-foreground/70">Pro yearly: ₱{pricingConfig.price.PH.pro.yearly?.toLocaleString()}</p>
            <p className="text-xs text-foreground/60">Intro monthly offers: Plus ₱{pricingConfig.intro.PH.plus.monthly}, Pro ₱{pricingConfig.intro.PH.pro.monthly}</p>
          </div>
          <div className="space-y-2 rounded-2xl border border-border bg-muted/20 p-4">
            <p className="text-sm font-semibold text-foreground">🌍 International pricing</p>
            <p className="text-sm text-foreground/80">Plus: ${pricingConfig.price.DEFAULT.plus.monthly}/month</p>
            <p className="text-sm text-foreground/80">Pro: ${pricingConfig.price.DEFAULT.pro.monthly}/month</p>
            <p className="text-sm text-foreground/70">Pro yearly: ${pricingConfig.price.DEFAULT.pro.yearly}/year</p>
          </div>
        </div>
      </Card>

      <Card className="space-y-4 p-4 sm:p-6">
        <div className="space-y-1">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">FAQ</p>
          <h3 className="text-lg font-semibold">Common questions</h3>
        </div>
        <dl className="space-y-4 text-sm">
          {[
            { q: "Is NoteLib free?", a: "Yes. You can use the core note-to-study workflow for free up to the monthly limits." },
            { q: "Who is Plus for?", a: "Plus is for regular study sessions when Free limits start to feel tight but you do not need the advanced Pro features yet." },
            { q: "Who is Pro for?", a: "Pro is built for serious review and exam prep with Adaptive Practice, difficulty selection, Board Exam Mode, and the highest limits." },
            { q: "Do prices vary by country?", a: "Yes. Backend pricing resolves your region and keeps PHP visibility clear for Xendit checkout." },
          ].map(({ q, a }) => (
            <div key={q} className="space-y-1">
              <dt className="font-medium text-foreground">{q}</dt>
              <dd className="text-foreground/70">{a}</dd>
            </div>
          ))}
        </dl>
      </Card>
    </section>
  );
}

export function SimplePricingSection() {
  return (
    <section className="space-y-5">
      <div className="grid gap-4 xl:grid-cols-3">
        <Card className="flex flex-col space-y-4 p-4 transition-[transform,box-shadow,border-color] duration-150 ease-out hover:-translate-y-0.5 hover:shadow-md sm:p-6">
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Free</p>
            <CardTitle>For getting started</CardTitle>
          </div>
          <p className="text-3xl font-semibold">Free</p>
          <ul className="grow space-y-2 text-sm text-foreground/80">
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />{pricingConfig.free.studyPacksPerMonth} Study Packs / month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />{pricingConfig.free.challengeQuizzesPerMonth} Quizzes / month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />{pricingConfig.free.exportsPerMonth} exports / month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Summary + Key Concepts</li>
          </ul>
          <Link href="/signup" className={buttonVariants({ className: "w-full" })}>
            Get Started Free
          </Link>
        </Card>

        <Card className="flex flex-col space-y-4 p-4 transition-[transform,box-shadow,border-color] duration-150 ease-out hover:-translate-y-0.5 hover:shadow-md sm:p-6">
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Plus</p>
            <CardTitle>For regular study</CardTitle>
          </div>
          <div className="space-y-0.5">
            <p className="text-xl font-semibold">₱{pricingConfig.intro.PH.plus.monthly} first month</p>
            <p className="text-sm text-foreground/60">then ₱{pricingConfig.price.PH.plus.monthly}/month</p>
          </div>
          <ul className="grow space-y-2 text-sm text-foreground/80">
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />{pricingConfig.plus.studyPacksPerMonth} Study Packs / month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />{pricingConfig.plus.challengeQuizzesPerMonth} Quizzes / month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />{pricingConfig.plus.exportsPerMonth} exports / month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Higher note generation limits</li>
          </ul>
          <Link href="/signup" className={buttonVariants({ variant: "outline", className: "w-full" })}>
            Upgrade to Plus
          </Link>
        </Card>

        <Card className="flex flex-col space-y-4 border-blue-300 bg-blue-50/35 p-4 shadow-sm transition-[transform,box-shadow,border-color,background-color] duration-150 ease-out hover:-translate-y-0.5 hover:shadow-lg dark:border-blue-700 dark:bg-blue-950/18 sm:p-6">
          <div className="space-y-2">
            <div className="inline-flex w-fit items-center gap-2 rounded-full border border-blue-500/20 bg-blue-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
              <Sparkles className="h-3.5 w-3.5" />
              Most Popular
            </div>
            <CardTitle>Best for exam prep</CardTitle>
          </div>
          <div className="space-y-0.5">
            <p className="text-xl font-semibold">₱{pricingConfig.intro.PH.pro.monthly} first month</p>
            <p className="text-sm text-foreground/60">then ₱{pricingConfig.price.PH.pro.monthly}/month</p>
          </div>
          <ul className="grow space-y-2 text-sm text-foreground/80">
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />{pricingConfig.pro.studyPacksPerMonth} Study Packs / month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />{pricingConfig.pro.challengeQuizzesPerMonth} Quizzes / month</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Adaptive Practice</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Difficulty selection</li>
            <li className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />Board Exam Mode</li>
          </ul>
          <Link href="/signup" className={buttonVariants({ className: "w-full" })}>
            Go Pro
          </Link>
        </Card>
      </div>
      <p className="text-sm text-foreground/60">
        Manual renewal. No automatic charges. Intro pricing applies to your first monthly checkout.
      </p>
    </section>
  );
}
