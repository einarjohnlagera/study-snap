import type { Metadata } from "next";
import Link from "next/link";
import { PremiumWaitlistButton } from "@/components/billing/premium-waitlist-button";
import { PricingPlansSection } from "@/components/billing/pricing-plans-section";
import { BrandFullLogo } from "@/components/branding/brand-assets";
import { Button } from "@/components/ui/button";
import { buildPageMetadata } from "@/lib/site-metadata";

export const metadata: Metadata = buildPageMetadata({
  title: "NoteLib Pricing — Free and Premium Plans",
  description: "Compare Free and Premium plans for Study Packs, Quiz, Adaptive Practice, and Board Exam Mode.",
  path: "/pricing",
});

export default function PricingPage() {
  return (
    <main className="mx-auto w-full max-w-6xl space-y-8 px-4 py-8 sm:px-6 sm:py-12">
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
            Start with Free to turn notes into Study Packs, summaries, key concepts, and quizzes.
          </p>
          <p className="max-w-3xl text-sm leading-relaxed text-foreground/75 sm:text-base">
            Upgrade to Premium when you need higher limits, Adaptive Practice, and more control during heavy exam weeks.
          </p>
          <p className="max-w-3xl rounded-2xl border border-blue-500/15 bg-background/80 px-4 py-3 text-sm text-foreground/75 backdrop-blur">
            Board Exam Mode is included with Premium for stricter exam-style practice.
          </p>
          <div className="flex flex-col gap-3 sm:flex-row">
            <Link href="/auth" className="w-full sm:w-auto">
              <Button type="button" className="w-full sm:w-auto">
                Start for Free
              </Button>
            </Link>
            <PremiumWaitlistButton
              label="Upgrade to Premium"
              source="pricing_hero"
              variant="outline"
              className="w-full sm:w-auto"
            />
          </div>
        </div>
      </section>

      <section className="space-y-3">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Free vs Premium
          </p>
          <h2 className="text-2xl font-semibold sm:text-3xl">Choose the study flow that fits your review season.</h2>
          <p className="max-w-3xl text-sm leading-relaxed text-foreground/75 sm:text-base">
            Free covers the core NoteLib workflow. Premium is positioned for students who need deeper quiz practice and higher monthly limits.
          </p>
        </div>
        <PricingPlansSection showHeading={false} />
      </section>
    </main>
  );
}
