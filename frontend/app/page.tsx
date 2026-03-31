import type { Metadata } from "next";
import {
  ArrowRight,
  Brain,
  FileText,
  GraduationCap,
  ListChecks,
  School,
  Stethoscope,
  Target,
} from "lucide-react";
import { AnalyticsPageViewTracker } from "@/components/analytics/page-view-tracker";
import { TrackedLink } from "@/components/analytics/tracked-link";
import { SimplePricingSection } from "@/components/billing/pricing-plans-section";
import { PublicFooter } from "@/components/public/public-footer";
import { StructuredDataScript } from "@/components/seo/structured-data-script";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { buildPageMetadata } from "@/lib/site-metadata";
import { buildWebsiteStructuredData } from "@/lib/structured-data";

const landingPageDescription =
  "NoteLib helps students, board exam reviewees, and teachers turn notes into summaries, key concepts, and quizzes so they can study and prepare for exams faster.";

export const metadata: Metadata = buildPageMetadata({
  title: "NoteLib — Turn Notes Into Quizzes",
  description: landingPageDescription,
  path: "/",
});

const workflowSteps = [
  {
    step: "Step 1",
    title: "Add Notes",
    description: "Paste notes, upload files, or scan handwritten notes.",
  },
  {
    step: "Step 2",
    title: "Generate Study Pack",
    description: "Get summary, key concepts, and quiz.",
  },
  {
    step: "Step 3",
    title: "Practice",
    description: "Take quizzes and identify weak concepts.",
  },
  {
    step: "Step 4",
    title: "Improve",
    description: "Use weak concepts and adaptive practice to improve.",
  },
];

const audienceCards = [
  {
    eyebrow: "For Students",
    title: "Study faster before quizzes and exams",
    description: "Turn your notes into reviewers and quizzes so you can review with recall instead of just rereading.",
    icon: GraduationCap,
  },
  {
    eyebrow: "For Board Exams",
    title: "Turn reviewer notes into practice questions",
    description: "Convert reviewer notes into practice questions and identify weak areas before exam day.",
    icon: Stethoscope,
  },
  {
    eyebrow: "For Teachers",
    title: "Create quiz and reviewer materials faster",
    description: "Use lesson notes to build quiz questions and reviewer material without starting from a blank page.",
    icon: School,
  },
];

const featureCards = [
  {
    title: "Summaries",
    description: "Turn long notes into faster review material.",
    icon: FileText,
  },
  {
    title: "Key Concepts",
    description: "See the ideas and terms that matter most.",
    icon: Brain,
  },
  {
    title: "Practice Quiz",
    description: "Turn notes into practice questions quickly.",
    icon: ListChecks,
  },
  {
    title: "Weak Concept Insights",
    description: "Spot the topics that need another review pass.",
    icon: Target,
  },
  {
    title: "Adaptive Practice",
    description: "Keep practicing the concepts that still feel weak.",
    icon: ArrowRight,
  },
];

function HeroSection() {
  return (
    <section className="relative overflow-hidden rounded-[2rem] border border-border bg-[radial-gradient(circle_at_top_left,_rgba(59,130,246,0.2),_transparent_35%),radial-gradient(circle_at_bottom_right,_rgba(245,158,11,0.14),_transparent_28%),linear-gradient(135deg,_rgba(255,255,255,0.98),_rgba(239,246,255,0.92))] p-6 shadow-sm dark:bg-[radial-gradient(circle_at_top_left,_rgba(59,130,246,0.24),_transparent_35%),radial-gradient(circle_at_bottom_right,_rgba(245,158,11,0.16),_transparent_28%),linear-gradient(135deg,_rgba(2,6,23,0.96),_rgba(15,23,42,0.94))] sm:p-8 lg:p-10">
      <div className="grid gap-8 lg:grid-cols-[1.05fr_0.95fr] lg:items-center">
        <div className="space-y-5">
          <div className="inline-flex items-center gap-2 rounded-full border border-blue-500/20 bg-background/80 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
            NoteLib for students, board exams, and teachers
          </div>
          <div className="space-y-3">
            <h1 className="max-w-3xl text-3xl font-semibold tracking-tight text-foreground sm:text-4xl lg:text-5xl">
              Turn Notes Into Quizzes
            </h1>
            <p className="text-xl font-medium text-foreground/80">
              Study Smarter. Not Harder.
            </p>
            <p className="max-w-2xl text-sm leading-relaxed text-foreground/75 sm:text-base">
              Generate summaries, key concepts, and practice quizzes from your notes in seconds.
            </p>
          </div>
          <div className="flex flex-col gap-3 sm:flex-row">
            <TrackedLink
              href="/signup"
              className={buttonVariants({ className: "w-full sm:w-auto" })}
              eventType="LANDING_CTA_CLICKED"
              eventMetadata={{ placement: "hero_primary", destination: "/signup" }}
            >
              Get Started Free
            </TrackedLink>
            <TrackedLink
              href="/demo"
              className={buttonVariants({ variant: "outline", className: "w-full sm:w-auto" })}
              eventType="LANDING_CTA_CLICKED"
              eventMetadata={{ placement: "hero_demo", destination: "/demo" }}
            >
              Try Demo
            </TrackedLink>
          </div>
        </div>

        <Card className="overflow-hidden border-blue-500/20 bg-background/85 p-0 shadow-lg backdrop-blur">
          <div className="border-b border-border bg-muted/30 px-5 py-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              NoteLib workflow
            </p>
            <p className="mt-1 text-sm text-foreground/75">
              Turn one note into a repeatable study loop built around recall and practice.
            </p>
          </div>
          <div className="space-y-4 p-5">
            <div className="flex flex-wrap items-center gap-2 text-sm font-medium text-foreground">
              <span className="rounded-full border border-border bg-muted/20 px-3 py-1.5">Note</span>
              <ArrowRight className="h-4 w-4 text-blue-600 dark:text-blue-400" />
              <span className="rounded-full border border-border bg-muted/20 px-3 py-1.5">Summary</span>
              <ArrowRight className="h-4 w-4 text-blue-600 dark:text-blue-400" />
              <span className="rounded-full border border-border bg-muted/20 px-3 py-1.5">Key Concepts</span>
              <ArrowRight className="h-4 w-4 text-blue-600 dark:text-blue-400" />
              <span className="rounded-full border border-border bg-muted/20 px-3 py-1.5">Quiz</span>
            </div>
            <div className="flex flex-wrap items-center gap-2 text-sm font-medium text-foreground">
              <span className="rounded-full border border-border bg-muted/20 px-3 py-1.5">Weak Concepts</span>
              <ArrowRight className="h-4 w-4 text-blue-600 dark:text-blue-400" />
              <span className="rounded-full border border-border bg-blue-500/10 px-3 py-1.5 text-blue-700 dark:text-blue-300">
                Adaptive Practice
              </span>
            </div>
            <div className="rounded-2xl border border-border bg-background p-4">
              <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">Why it works</p>
              <p className="mt-2 text-sm text-foreground/80">
                Most students reread notes. NoteLib helps you move into summaries, practice questions, weak concepts, and focused review faster.
              </p>
            </div>
          </div>
        </Card>
      </div>
    </section>
  );
}

function HowItWorksSection() {
  const flowItems = ["Notes", "Summary", "Quiz", "Weak Concepts", "Practice"];

  return (
    <section className="space-y-5">
      <div className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          How It Works
        </p>
        <h2 className="text-2xl font-semibold sm:text-3xl">From notes to practice in four steps</h2>
      </div>
      <div className="flex flex-col gap-2 rounded-2xl border border-border bg-muted/25 p-4 sm:flex-row sm:flex-wrap sm:items-center sm:gap-3">
        {flowItems.map((item, index) => (
          <div key={item} className="flex items-center gap-3">
            <span className="inline-flex items-center rounded-full border border-border bg-background px-4 py-2 text-sm font-medium text-foreground">
              {item}
            </span>
            {index < flowItems.length - 1 ? (
              <ArrowRight className="hidden h-4 w-4 text-blue-600 dark:text-blue-400 sm:block" />
            ) : null}
          </div>
        ))}
      </div>
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {workflowSteps.map((item) => (
          <Card key={item.title} className="space-y-3 p-5 sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              {item.step}
            </p>
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

function AudienceSection() {
  return (
    <section className="space-y-5">
      <div className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Who It&apos;s For
        </p>
        <h2 className="text-2xl font-semibold sm:text-3xl">Built for real study and quiz prep workflows</h2>
      </div>
      <div className="grid gap-4 lg:grid-cols-3">
        {audienceCards.map((card) => (
          <Card key={card.eyebrow} className="space-y-4 p-5 sm:p-6">
            <card.icon className="h-5 w-5 text-blue-600 dark:text-blue-400" />
            <div className="space-y-2">
              <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
                {card.eyebrow}
              </p>
              <CardTitle className="text-xl">{card.title}</CardTitle>
              <CardDescription className="text-sm">{card.description}</CardDescription>
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
          Features Overview
        </p>
        <h2 className="text-2xl font-semibold sm:text-3xl">A study workflow built around outcomes</h2>
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

function FinalCtaSection() {
  return (
    <section className="rounded-[2rem] border border-blue-500/20 bg-[linear-gradient(135deg,_rgba(219,234,254,0.75),_rgba(255,255,255,0.96))] p-6 shadow-sm dark:bg-[linear-gradient(135deg,_rgba(30,64,175,0.26),_rgba(15,23,42,0.94))] sm:p-8">
      <div className="space-y-4 text-center">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Start Today
          </p>
          <h2 className="text-2xl font-semibold sm:text-3xl">Start Turning Your Notes Into Quizzes Today</h2>
        </div>
        <div className="flex justify-center">
          <TrackedLink
            href="/signup"
            className={buttonVariants({ className: "w-full sm:w-auto" })}
            eventType="LANDING_CTA_CLICKED"
            eventMetadata={{ placement: "bottom_cta_primary", destination: "/signup" }}
          >
            Get Started Free
          </TrackedLink>
        </div>
      </div>
    </section>
  );
}

export default function Home() {
  return (
    <main className="mx-auto w-full max-w-6xl space-y-10 px-4 py-8 sm:px-6 sm:py-12">
      <StructuredDataScript
        id="landing-page-structured-data"
        data={buildWebsiteStructuredData(landingPageDescription)}
      />
      <AnalyticsPageViewTracker eventType="LANDING_PAGE_VIEWED" metadata={{ page: "landing" }} />
      <HeroSection />
      <HowItWorksSection />
      <AudienceSection />
      <FeaturesSection />
      <SimplePricingSection />
      <FinalCtaSection />
      <PublicFooter />
    </main>
  );
}
