import type { Metadata } from "next";
import {
  ArrowRight,
  Brain,
  CheckCircle2,
  FileText,
  ListChecks,
  ScanText,
  Sparkles,
  Target,
  Zap,
} from "lucide-react";
import { AnalyticsPageViewTracker } from "@/components/analytics/page-view-tracker";
import { TrackedLink } from "@/components/analytics/tracked-link";
import { PricingPlansSection } from "@/components/billing/pricing-plans-section";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { buildPageMetadata } from "@/lib/site-metadata";

export const metadata: Metadata = buildPageMetadata({
  title: "NoteLib – Turn Notes into Study Packs, Summaries, and Quizzes",
  description: "Turn your notes into summaries, key concepts, and practice questions so you can study smarter.",
  path: "/",
});

const howItWorksSteps = [
  {
    step: "Step 1",
    title: "Create or Import Notes",
    description: "Type, paste, or import your notes from an image or file.",
    icon: ScanText,
  },
  {
    step: "Step 2",
    title: "Generate Study Pack",
    description: "Get summary, key concepts, and quiz instantly.",
    icon: Sparkles,
  },
  {
    step: "Step 3",
    title: "Review and Practice",
    description: "Use Quick Review, Challenge Quiz, and Adaptive Practice.",
    icon: Target,
  },
];

const featureCards = [
  {
    title: "Summaries",
    description: "Understand lessons faster",
    icon: FileText,
  },
  {
    title: "Key Concepts",
    description: "Focus on important topics",
    icon: Brain,
  },
  {
    title: "Quick Review",
    description: "Fast reviewer before quiz",
    icon: ListChecks,
  },
  {
    title: "Challenge Quiz",
    description: "Exam-style quiz",
    icon: CheckCircle2,
  },
  {
    title: "Adaptive Practice",
    description: "Focus on weak topics",
    icon: Zap,
  },
];

function HeroSection() {
  return (
    <section className="relative overflow-hidden rounded-[2rem] border border-border bg-[radial-gradient(circle_at_top_left,_rgba(59,130,246,0.18),_transparent_35%),radial-gradient(circle_at_bottom_right,_rgba(245,158,11,0.16),_transparent_28%),linear-gradient(135deg,_rgba(255,255,255,0.98),_rgba(239,246,255,0.9))] p-6 shadow-sm dark:bg-[radial-gradient(circle_at_top_left,_rgba(59,130,246,0.22),_transparent_35%),radial-gradient(circle_at_bottom_right,_rgba(245,158,11,0.18),_transparent_28%),linear-gradient(135deg,_rgba(2,6,23,0.96),_rgba(15,23,42,0.94))] sm:p-8 lg:p-10">
      <div className="grid gap-8 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
        <div className="space-y-5">
          <div className="inline-flex items-center gap-2 rounded-full border border-blue-500/20 bg-background/80 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
            <Sparkles className="h-4 w-4" />
            AI study packs for students
          </div>
          <div className="space-y-3">
            <h1 className="max-w-3xl text-3xl font-semibold tracking-tight text-foreground sm:text-4xl lg:text-5xl">
              Turn your notes into summaries, quizzes, and reviewers in seconds.
            </h1>
            <p className="max-w-2xl text-sm leading-relaxed text-foreground/75 sm:text-base">
              Study smarter with AI-powered summaries, key concepts, and practice quizzes.
            </p>
          </div>
          <div className="flex flex-col gap-3 sm:flex-row">
            <TrackedLink
              href="/auth"
              className={buttonVariants({ className: "w-full sm:w-auto" })}
              eventType="LANDING_CTA_CLICKED"
              eventMetadata={{ placement: "hero_primary", destination: "/auth" }}
            >
              Get Started Free
            </TrackedLink>
            <TrackedLink
              href="/demo"
              className={buttonVariants({ variant: "outline", className: "w-full border-blue-500/30 bg-background/80 sm:w-auto" })}
              eventType="LANDING_CTA_CLICKED"
              eventMetadata={{ placement: "hero_demo", destination: "/demo" }}
            >
              Try Demo
            </TrackedLink>
          </div>
          <div className="grid gap-3 text-sm text-foreground/75 sm:grid-cols-3">
            <div className="rounded-2xl border border-border/80 bg-background/75 px-4 py-3">
              <p className="font-semibold text-foreground">Create or import notes</p>
            </div>
            <div className="rounded-2xl border border-border/80 bg-background/75 px-4 py-3">
              <p className="font-semibold text-foreground">Generate a Study Pack when you&apos;re ready</p>
            </div>
            <div className="rounded-2xl border border-border/80 bg-background/75 px-4 py-3">
              <p className="font-semibold text-foreground">Review before quizzes and exams</p>
            </div>
          </div>
        </div>

        <Card className="overflow-hidden border-blue-500/20 bg-background/85 p-0 shadow-lg backdrop-blur">
          <div className="border-b border-border bg-muted/40 px-5 py-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              From class notes to exam prep
            </p>
            <p className="mt-1 text-sm text-foreground/75">
              NoteLib turns one note into a review workflow you can actually use.
            </p>
          </div>
          <div className="grid gap-3 p-5">
            <div className="rounded-2xl border border-border bg-muted/20 p-4">
              <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">Raw Notes</p>
              <div className="mt-2 space-y-2 text-sm text-foreground/80">
                <p>Cell structure includes the nucleus, membrane, mitochondria, and ribosomes.</p>
                <p>Each part has a specific role in keeping the cell alive and functioning.</p>
              </div>
            </div>
            <div className="flex items-center justify-center">
              <div className="inline-flex items-center gap-2 rounded-full border border-blue-500/20 bg-blue-500/10 px-3 py-1 text-xs font-semibold text-blue-700 dark:text-blue-300">
                Generate Study Pack
                <ArrowRight className="h-3.5 w-3.5" />
              </div>
            </div>
            <div className="grid gap-3 sm:grid-cols-3">
              <div className="rounded-2xl border border-border bg-background p-4">
                <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">Summary</p>
                <p className="mt-2 text-sm text-foreground/80">Review the lesson quickly before class or exams.</p>
              </div>
              <div className="rounded-2xl border border-border bg-background p-4">
                <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">Key Concepts</p>
                <p className="mt-2 text-sm text-foreground/80">Spot the terms and ideas most likely to matter.</p>
              </div>
              <div className="rounded-2xl border border-border bg-background p-4">
                <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">Quiz Practice</p>
                <p className="mt-2 text-sm text-foreground/80">Use Quick Review, Challenge Quiz, and Adaptive Practice.</p>
              </div>
            </div>
          </div>
        </Card>
      </div>
    </section>
  );
}

function HowItWorksSection() {
  return (
    <section className="space-y-5">
      <div className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          How It Works
        </p>
        <h2 className="text-2xl font-semibold sm:text-3xl">From messy notes to a study routine in three steps</h2>
      </div>
      <div className="grid gap-4 md:grid-cols-3">
        {howItWorksSteps.map((item) => (
          <Card key={item.title} className="space-y-4 p-5 sm:p-6">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
                {item.step}
              </span>
              <item.icon className="h-5 w-5 text-blue-600 dark:text-blue-400" />
            </div>
            <div className="space-y-2">
              <CardTitle>{item.title}</CardTitle>
              <CardDescription className="text-sm">{item.description}</CardDescription>
            </div>
          </Card>
        ))}
      </div>
    </section>
  );
}

function FeaturesSection() {
  return (
    <section className="space-y-5">
      <div className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Features
        </p>
        <h2 className="text-2xl font-semibold sm:text-3xl">Built for review weeks, quizzes, and exam prep</h2>
      </div>
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        {featureCards.map((feature) => (
          <Card key={feature.title} className="space-y-4 p-5">
            <feature.icon className="h-5 w-5 text-blue-600 dark:text-blue-400" />
            <div className="space-y-2">
              <CardTitle className="text-lg">{feature.title}</CardTitle>
              <CardDescription className="text-sm">{feature.description}</CardDescription>
            </div>
          </Card>
        ))}
      </div>
    </section>
  );
}

function DemoSection() {
  return (
    <section className="rounded-[2rem] border border-amber-500/20 bg-[linear-gradient(135deg,_rgba(254,243,199,0.55),_rgba(255,255,255,0.92))] p-6 shadow-sm dark:bg-[linear-gradient(135deg,_rgba(120,53,15,0.32),_rgba(15,23,42,0.92))] sm:p-8">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-amber-700 dark:text-amber-300">
            Demo
          </p>
          <h2 className="text-2xl font-semibold sm:text-3xl">Try a demo Study Pack now — no signup required.</h2>
          <p className="max-w-2xl text-sm text-foreground/75 sm:text-base">
            Explore the NoteLib workflow before creating an account. Open the demo, review the generated outputs, and see how fast note review can feel.
          </p>
        </div>
        <TrackedLink
          href="/demo"
          className={buttonVariants({ className: "w-full sm:w-auto" })}
          eventType="LANDING_CTA_CLICKED"
          eventMetadata={{ placement: "demo_section", destination: "/demo" }}
        >
          Try Demo
        </TrackedLink>
      </div>
    </section>
  );
}

function BottomCtaSection() {
  return (
    <section className="rounded-[2rem] border border-blue-500/20 bg-[linear-gradient(135deg,_rgba(219,234,254,0.75),_rgba(255,255,255,0.96))] p-6 shadow-sm dark:bg-[linear-gradient(135deg,_rgba(30,64,175,0.26),_rgba(15,23,42,0.94))] sm:p-8">
      <div className="space-y-4 text-center">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Start Today
          </p>
          <h2 className="text-2xl font-semibold sm:text-3xl">Start studying smarter today.</h2>
        </div>
        <div className="flex justify-center">
          <TrackedLink
            href="/auth"
            className={buttonVariants({ className: "w-full sm:w-auto" })}
            eventType="LANDING_CTA_CLICKED"
            eventMetadata={{ placement: "bottom_cta", destination: "/auth" }}
          >
            Create Free Account
          </TrackedLink>
        </div>
      </div>
    </section>
  );
}

export default function Home() {
  return (
    <main className="mx-auto w-full max-w-6xl space-y-10 px-4 py-8 sm:px-6 sm:py-12">
      <AnalyticsPageViewTracker eventType="LANDING_PAGE_VIEWED" metadata={{ page: "landing" }} />
      <HeroSection />
      <HowItWorksSection />
      <FeaturesSection />
      <PricingPlansSection />
      <DemoSection />
      <BottomCtaSection />
    </main>
  );
}
