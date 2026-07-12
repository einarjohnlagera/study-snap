"use client";

import Link from "next/link";
import { Check, Minus, Sparkles } from "lucide-react";
import type { PaidPlanType } from "@/lib/api";
import { PremiumUpgradeButton } from "@/components/billing/premium-upgrade-button";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { useBillingPricing } from "@/hooks/use-billing-pricing";
import { formatBillingAmount, getBillingCyclePriceLabel, getExamCyclePriceLabel, resolveCyclePricing } from "@/lib/billing-pricing";
import { pricingConfig, resolvePricingDisplayRegion } from "@/lib/pricing-config";
import {
  type ComparisonValue,
  PASS_ALL_ACCESS_NOTE,
  PASS_DATA_PERMANENCE_NOTE,
  PASS_MODEL_TAGLINE,
  PASS_NO_AUTO_CHARGE_FOOTER,
  PASS_QUOTA_REFRESH_NOTE,
  PLAN_COMPARISON_ROWS,
  TEACHER_PLUS_EXPORT_CALLOUT,
  getPaidPlanCtaLabel,
  getPlanConfig,
} from "@/src/config/plans";

type PricingPlansSectionProps = {
  showHeading?: boolean;
};

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

function FeatureList({ features }: Readonly<{ features: ReadonlyArray<{ label: string; helper?: string }> }>) {
  return (
    <ul className="space-y-2 text-sm text-foreground/80">
      {features.map((feature) => (
        <li key={feature.label} className="flex items-start gap-2">
          <Check className="mt-0.5 h-4 w-4 shrink-0 text-blue-600 dark:text-blue-400" />
          <span>
            {feature.label}
            {feature.helper ? <span className="block text-xs text-foreground/50">{feature.helper}</span> : null}
          </span>
        </li>
      ))}
    </ul>
  );
}

function resolveFallbackCycleLabel(planType: PaidPlanType, region: ReturnType<typeof resolvePricingDisplayRegion>) {
  const regionPricing = pricingConfig.price[region];
  const introPricing = pricingConfig.intro[region];
  const planPricing = planType === "PLUS" ? regionPricing.plus : regionPricing.pro;
  const introAmount = planType === "PLUS" ? introPricing.plus.monthly : introPricing.pro.monthly;

  return {
    monthly: introAmount === null
      ? `${formatBillingAmount(planPricing.monthly, regionPricing.currency)} / 1 month`
      : `${formatBillingAmount(introAmount, regionPricing.currency)} for your first 1-month pass · ${formatBillingAmount(planPricing.monthly, regionPricing.currency)} after`,
    yearly: planPricing.yearly === null
      ? null
      : `${formatBillingAmount(planPricing.yearly, regionPricing.currency)} / 1 year`,
  };
}

function PlanCard({
  label,
  eyebrow,
  title,
  description,
  monthlyLabel,
  yearlyLabel,
  examCycleLabel,
  introHint,
  highlights,
  ctaLabel,
  planType,
  callout,
  accent = false,
}: Readonly<{
  label: string;
  eyebrow?: string;
  title: string;
  description: string;
  monthlyLabel: string;
  yearlyLabel?: string | null;
  examCycleLabel?: string | null;
  introHint?: string | null;
  highlights: ReadonlyArray<{ label: string; helper?: string }>;
  ctaLabel: string;
  planType: PaidPlanType;
  callout?: string;
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
        <p className="text-xl font-semibold text-foreground">{examCycleLabel ?? monthlyLabel}</p>
        <p className="text-xs text-foreground/60">{PASS_MODEL_TAGLINE}</p>
        <p className="text-xs text-foreground/60">{PASS_QUOTA_REFRESH_NOTE}</p>
        {!examCycleLabel && introHint ? <p className="text-xs text-foreground/60">{introHint}</p> : null}
      </div>
      <FeatureList features={highlights} />
      {callout ? (
        <p className="rounded-md border border-blue-500/20 bg-blue-500/10 px-3 py-2 text-xs font-medium text-blue-700 dark:text-blue-300">
          {callout}
        </p>
      ) : null}
      <div className="space-y-2">
        <PremiumUpgradeButton
          label={examCycleLabel ? `${ctaLabel} — ${examCycleLabel}` : ctaLabel}
          source={examCycleLabel ? "pricing_plans_section_pro_exam_cycle" : `pricing_plans_section_${planType.toLowerCase()}_monthly`}
          planType={planType}
          billingCycle={examCycleLabel ? "EXAM_CYCLE" : "MONTHLY"}
          className="w-full"
        />
        <p className="text-center text-xs text-foreground/60">{PASS_NO_AUTO_CHARGE_FOOTER}</p>
        {examCycleLabel || yearlyLabel ? (
          <div className="flex flex-wrap items-center gap-x-1.5 text-xs text-foreground/60">
            <span>Also available:</span>
            {examCycleLabel ? (
              <PremiumUpgradeButton
                label={`1 month — ${monthlyLabel}`}
                source={`pricing_plans_section_${planType.toLowerCase()}_monthly`}
                planType={planType}
                billingCycle="MONTHLY"
                variant="ghost"
                size="sm"
                className="h-auto px-1 py-0 text-xs font-medium text-blue-700 hover:underline dark:text-blue-300"
              />
            ) : null}
            {yearlyLabel ? (
              <>
                {examCycleLabel ? <span aria-hidden="true">·</span> : null}
                <PremiumUpgradeButton
                  label={`1 year — ${yearlyLabel}`}
                  source={`pricing_plans_section_${planType.toLowerCase()}_yearly`}
                  planType={planType}
                  billingCycle="YEARLY"
                  variant="ghost"
                  size="sm"
                  className="h-auto px-1 py-0 text-xs font-medium text-blue-700 hover:underline dark:text-blue-300"
                />
              </>
            ) : null}
          </div>
        ) : null}
      </div>
    </Card>
  );
}

export function PricingPlansSection({ showHeading = true }: Readonly<PricingPlansSectionProps>) {
  const { billingPricing } = useBillingPricing(true);
  const displayRegion = resolvePricingDisplayRegion(billingPricing?.region);
  const regionFallback = pricingConfig.price[displayRegion];
  const freePlan = getPlanConfig("FREE");
  const plusPlan = getPlanConfig("PLUS");
  const proPlan = getPlanConfig("PRO");
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
  const proExamCycleLabel = billingPricing && resolveCyclePricing(billingPricing, "PRO", "EXAM_CYCLE")?.available
    ? getExamCyclePriceLabel(billingPricing, "PRO")
    : null;

  const plusIntroHint = billingPricing?.plus.monthly.introEligible && billingPricing.plus.monthly.introAmount !== null
    ? `Intro offer: ${formatBillingAmount(billingPricing.plus.monthly.introAmount, billingPricing.currency)} on your first 1-month pass.`
    : pricingConfig.intro[displayRegion].plus.monthly !== null
      ? `Intro offer: ${formatBillingAmount(pricingConfig.intro[displayRegion].plus.monthly, regionFallback.currency)} on your first 1-month pass.`
      : null;
  const proIntroHint = billingPricing?.pro.monthly.introEligible && billingPricing.pro.monthly.introAmount !== null
    ? `Intro offer: ${formatBillingAmount(billingPricing.pro.monthly.introAmount, billingPricing.currency)} on your first 1-month pass.`
    : pricingConfig.intro[displayRegion].pro.monthly !== null
      ? `Intro offer: ${formatBillingAmount(pricingConfig.intro[displayRegion].pro.monthly, regionFallback.currency)} on your first 1-month pass.`
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
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">{freePlan.name}</p>
            <CardTitle>{freePlan.title}</CardTitle>
            <CardDescription className="leading-relaxed">{freePlan.description}</CardDescription>
          </div>
          <p className="text-3xl font-semibold">{freePlan.name}</p>
          <FeatureList features={freePlan.features} />
          <div className="space-y-2 rounded-lg border border-dashed border-border bg-background/70 p-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Paid upgrades</p>
            <ul className="space-y-2 text-sm text-foreground/75">
              {freePlan.upgradeHighlights?.map((feature) => (
                <li key={feature} className="flex items-start gap-2">
                  <Minus className="mt-0.5 h-4 w-4 text-foreground/55" />
                  {feature}
                </li>
              ))}
            </ul>
          </div>
          <Link href="/auth" className={buttonVariants({ variant: "outline", className: "w-full sm:w-auto" })}>
            {freePlan.ctaLabel}
          </Link>
        </Card>

        <PlanCard
          label={plusPlan.name}
          title={plusPlan.title}
          description={plusPlan.description}
          monthlyLabel={plusMonthlyLabel}
          yearlyLabel={plusYearlyLabel}
          introHint={plusIntroHint}
          highlights={plusPlan.features}
          ctaLabel={getPaidPlanCtaLabel("PLUS")}
          planType="PLUS"
          callout={TEACHER_PLUS_EXPORT_CALLOUT}
        />

        <PlanCard
          label={proPlan.name}
          eyebrow={proPlan.eyebrow}
          title={proPlan.title}
          description={proPlan.description}
          monthlyLabel={proMonthlyLabel}
          yearlyLabel={proYearlyLabel}
          examCycleLabel={proExamCycleLabel}
          introHint={proIntroHint}
          highlights={proPlan.features}
          ctaLabel={getPaidPlanCtaLabel("PRO")}
          planType="PRO"
          accent
        />
      </div>

      <p className="text-sm leading-relaxed text-foreground/65">
        {PASS_DATA_PERMANENCE_NOTE} {PASS_ALL_ACCESS_NOTE} {PASS_NO_AUTO_CHARGE_FOOTER}
      </p>

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
              {PLAN_COMPARISON_ROWS.map((row, rowIndex) => (
                <tr key={row.label} className={`border-b border-border ${rowIndex === PLAN_COMPARISON_ROWS.length - 1 ? "border-b-0" : ""}`}>
                  <td className="px-4 py-3 font-medium sm:px-6">{row.label}</td>
                  <td className="px-4 py-3 text-center align-middle sm:px-6"><ComparisonCell value={row.values.FREE} /></td>
                  <td className="px-4 py-3 text-center align-middle sm:px-6"><ComparisonCell value={row.values.PLUS} /></td>
                  <td className="bg-blue-500/8 px-4 py-3 text-center align-middle sm:px-6 dark:bg-blue-500/12"><ComparisonCell value={row.values.PRO} emphasize /></td>
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
            <p className="text-sm text-foreground/80">Plus: ₱{pricingConfig.price.PH.plus.monthly} / 1-month pass</p>
            <p className="text-sm text-foreground/80">Pro: ₱{pricingConfig.price.PH.pro.monthly} / 1-month pass</p>
            <p className="text-sm text-foreground/70">Pro 1-year pass: ₱{pricingConfig.price.PH.pro.yearly?.toLocaleString()}</p>
            <p className="text-xs text-foreground/60">First-pass intro: Plus ₱{pricingConfig.intro.PH.plus.monthly}, Pro ₱{pricingConfig.intro.PH.pro.monthly}</p>
          </div>
          <div className="space-y-2 rounded-2xl border border-border bg-muted/20 p-4">
            <p className="text-sm font-semibold text-foreground">🌍 International pricing</p>
            <p className="text-sm text-foreground/80">Plus: ${pricingConfig.price.DEFAULT.plus.monthly} / 1-month pass</p>
            <p className="text-sm text-foreground/80">Pro: ${pricingConfig.price.DEFAULT.pro.monthly} / 1-month pass</p>
            <p className="text-sm text-foreground/70">Pro 1-year pass: ${pricingConfig.price.DEFAULT.pro.yearly}</p>
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
            { q: "Will I be charged again?", a: "No. Plus and Pro are one-time, time-boxed passes — they never auto-renew. Buy another pass when your next exam is near. Your usage limits refresh each month while a pass is active." },
            { q: "What happens when my pass ends?", a: "Your notes, Study Packs, and progress stay in your library. You simply return to Free limits until you grab another pass." },
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
  const freePlan = getPlanConfig("FREE");
  const plusPlan = getPlanConfig("PLUS");
  const proPlan = getPlanConfig("PRO");

  return (
    <section className="space-y-5">
      <div className="grid gap-4 xl:grid-cols-3">
        {/* Free */}
        <Card className="flex flex-col space-y-4 p-4 transition-[transform,box-shadow,border-color] duration-150 ease-out hover:-translate-y-0.5 hover:shadow-md sm:p-6">
          <div className="space-y-1">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">{freePlan.name}</p>
            <CardTitle>{freePlan.title}</CardTitle>
            <CardDescription className="text-sm leading-relaxed">{freePlan.description}</CardDescription>
          </div>
          <p className="text-3xl font-semibold">{freePlan.name}</p>
          <div className="grow">
            <FeatureList features={freePlan.features} />
          </div>
          <Link href="/signup" className={buttonVariants({ className: "w-full" })}>
            {freePlan.ctaLabel}
          </Link>
        </Card>

        {/* Plus */}
        <Card className="flex flex-col space-y-4 p-4 transition-[transform,box-shadow,border-color] duration-150 ease-out hover:-translate-y-0.5 hover:shadow-md sm:p-6">
          <div className="space-y-1">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">{plusPlan.name}</p>
            <CardTitle>{plusPlan.title}</CardTitle>
            <CardDescription className="text-sm leading-relaxed">{plusPlan.description}</CardDescription>
          </div>
          <div className="space-y-0.5">
            <p className="text-xl font-semibold">₱{pricingConfig.intro.PH.plus.monthly} first 1-month pass</p>
            <p className="text-sm text-foreground/60">then ₱{pricingConfig.price.PH.plus.monthly} after · {PASS_MODEL_TAGLINE}</p>
          </div>
          <div className="grow">
            <FeatureList features={plusPlan.features} />
          </div>
          <p className="rounded-md border border-blue-500/20 bg-blue-500/10 px-3 py-2 text-xs font-medium text-blue-700 dark:text-blue-300">
            {TEACHER_PLUS_EXPORT_CALLOUT}
          </p>
          <p className="text-xs text-foreground/55">{plusPlan.adaptivePracticeMessage}</p>
          <Link href="/signup" className={buttonVariants({ variant: "outline", className: "w-full" })}>
            {plusPlan.ctaLabel}
          </Link>
        </Card>

        {/* Pro */}
        <Card className="flex flex-col space-y-4 border-blue-300 bg-blue-50/35 p-4 shadow-sm transition-[transform,box-shadow,border-color,background-color] duration-150 ease-out hover:-translate-y-0.5 hover:shadow-lg dark:border-blue-700 dark:bg-blue-950/18 sm:p-6">
          <div className="space-y-1">
            <div className="inline-flex w-fit items-center gap-2 rounded-full border border-blue-500/20 bg-blue-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
              <Sparkles className="h-3.5 w-3.5" />
              {proPlan.eyebrow}
            </div>
            <CardTitle>{proPlan.title}</CardTitle>
            <CardDescription className="text-sm leading-relaxed">{proPlan.description}</CardDescription>
          </div>
          <div className="space-y-0.5">
            <p className="text-xl font-semibold">₱{pricingConfig.intro.PH.pro.monthly} first 1-month pass</p>
            <p className="text-sm text-foreground/60">then ₱{pricingConfig.price.PH.pro.monthly} after · {PASS_MODEL_TAGLINE}</p>
          </div>
          <div className="grow">
            <FeatureList features={proPlan.features} />
          </div>
          <p className="text-xs text-foreground/55">{proPlan.adaptivePracticeMessage}</p>
          <Link href="/signup" className={buttonVariants({ className: "w-full" })}>
            {proPlan.ctaLabel}
          </Link>
        </Card>
      </div>
      <p className="text-sm text-foreground/60">
        {PASS_QUOTA_REFRESH_NOTE} {PASS_DATA_PERMANENCE_NOTE} {PASS_NO_AUTO_CHARGE_FOOTER}
      </p>
    </section>
  );
}
