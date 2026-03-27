import type { Metadata } from "next";
import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { PremiumWaitlistButton } from "@/components/billing/premium-waitlist-button";
import { PricingPlansSection } from "@/components/billing/pricing-plans-section";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { buildPageMetadata } from "@/lib/site-metadata";

export const metadata: Metadata = buildPageMetadata({
  title: "NoteLib Pricing — Free and Premium Plans",
  description: "Choose between Free and Premium plans. Generate study packs, quizzes, and reviewers from your notes.",
  path: "/pricing",
});

export default function PricingPage() {
  return (
    <main className="mx-auto w-full max-w-6xl space-y-8 px-4 py-8 sm:px-6 sm:py-12">
      <section className="relative overflow-hidden rounded-3xl border border-blue-500/20 bg-gradient-to-br from-blue-500/10 via-background to-amber-500/10 p-6 shadow-sm sm:p-10">
        <div className="absolute right-0 top-0 h-40 w-40 rounded-full bg-blue-500/10 blur-3xl" />
        <div className="relative grid gap-6 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
          <div className="space-y-4">
            <p className="text-xs font-semibold uppercase tracking-[0.24em] text-blue-700 dark:text-blue-300">
              Pricing
            </p>
            <h1 className="max-w-3xl text-3xl font-semibold tracking-tight sm:text-4xl md:text-5xl">
              Study smarter. Pass exams faster.
            </h1>
            <p className="max-w-2xl text-sm leading-relaxed text-foreground/75 sm:text-base">
              Turn your notes into summaries, quizzes, and reviewers in seconds.
            </p>
            <div className="flex flex-col gap-3 sm:flex-row">
              <Link href="/auth" className="w-full sm:w-auto">
                <Button type="button" className="w-full sm:w-auto">
                  Start Free
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
          <Card className="space-y-3 border-blue-500/20 bg-background/90 p-5 backdrop-blur sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">
              Built for exams and mastery
            </p>
            <ul className="space-y-3 text-sm text-foreground/80">
              <li className="flex items-start gap-2">
                <ArrowRight className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />
                Challenge Quiz simulates exam pressure by hiding answers until you commit.
              </li>
              <li className="flex items-start gap-2">
                <ArrowRight className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />
                Adaptive Practice helps you revisit weak topics instead of repeating what you already know.
              </li>
              <li className="flex items-start gap-2">
                <ArrowRight className="mt-0.5 h-4 w-4 text-blue-600 dark:text-blue-400" />
                Premium gives you room to generate more Study Packs during heavy review weeks.
              </li>
            </ul>
          </Card>
        </div>
      </section>

      <PricingPlansSection showHeading={false} />
    </main>
  );
}
