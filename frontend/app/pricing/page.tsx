import type { Metadata } from "next";
import Link from "next/link";
import { PremiumUpgradeButton } from "@/components/billing/premium-upgrade-button";
import { PricingPlansSection } from "@/components/billing/pricing-plans-section";
import { BrandFullLogo } from "@/components/branding/brand-assets";
import { Button } from "@/components/ui/button";
import { buildPageMetadata } from "@/lib/site-metadata";

export const metadata: Metadata = buildPageMetadata({
  title: "NoteLib Pricing — Free, Plus, and Pro Plans",
  description: "Simple plans for everyday study and serious review. Start free, move to Plus for higher limits, or choose Pro for exam prep.",
  path: "/pricing",
});

export default function PricingPage() {
  return (
    <main className="mx-auto w-full max-w-6xl space-y-8 px-4 py-8 pb-28 sm:px-6 sm:py-12 sm:pb-12">
      <section className="relative overflow-hidden rounded-3xl border border-blue-500/20 bg-gradient-to-br from-blue-500/10 via-background to-amber-500/10 p-6 shadow-sm sm:p-10">
        <div className="absolute right-0 top-0 h-40 w-40 rounded-full bg-blue-500/10 blur-3xl" />
        <div className="relative space-y-5">
          <BrandFullLogo width={208} height={44} priority />
          <p className="text-xs font-semibold uppercase tracking-[0.24em] text-blue-700 dark:text-blue-300">
            Pricing
          </p>
          <h1 className="max-w-3xl text-3xl font-semibold tracking-tight sm:text-4xl md:text-5xl">
            Simple plans for everyday study and serious review.
          </h1>
          <p className="max-w-3xl text-sm leading-relaxed text-foreground/75 sm:text-base">
            Start free for core features. Move to Plus for higher limits, or choose Pro for deeper practice and exam-ready tools.
          </p>
          <p className="max-w-2xl text-sm leading-relaxed text-foreground/70 sm:text-base">
            Most users stay on Free while studying casually. Plus fits regular review, and Pro is built for serious exam prep.
          </p>
          <div className="flex flex-col gap-3 sm:flex-row">
            <Link href="/signup" className="w-full sm:w-auto">
              <Button type="button" className="w-full sm:w-auto">
                Get Started Free
              </Button>
            </Link>
            <PremiumUpgradeButton
              label="Go Pro"
              source="pricing_hero"
              planType="PRO"
              variant="outline"
              className="w-full sm:w-auto"
            />
          </div>
        </div>
      </section>

      <section className="space-y-3">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Free, Plus, and Pro
          </p>
          <h2 className="text-2xl font-semibold sm:text-3xl">Free for everyday study. Plus for regular review. Pro for deeper exam prep.</h2>
          <p className="max-w-3xl text-sm leading-relaxed text-foreground/75 sm:text-base">
            Free covers the core note-to-study-pack workflow. Plus expands your monthly limits. Pro adds the highest limits and advanced practice tools.
          </p>
          <p className="max-w-3xl text-sm leading-relaxed text-foreground/70 sm:text-base">
            Most users stay on Free while studying casually. Move to Plus when you want more room, then Pro when your review gets serious.
          </p>
        </div>
        <PricingPlansSection showHeading={false} />
      </section>
      <div className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-background/95 px-4 py-3 backdrop-blur sm:hidden">
        <Link href="/signup" className="block">
          <Button type="button" className="w-full">
            Get Started Free
          </Button>
        </Link>
      </div>
    </main>
  );
}
