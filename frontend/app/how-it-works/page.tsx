import type { Metadata } from "next";
import { FileText, Sparkles, Trophy } from "lucide-react";
import { AnalyticsPageViewTracker } from "@/components/analytics/page-view-tracker";
import { TrackedLink } from "@/components/analytics/tracked-link";
import { BrandFullLogo } from "@/components/branding/brand-assets";
import { ProductScreenshotFrame } from "@/components/public/product-screenshot-frame";
import { PublicFooter } from "@/components/public/public-footer";
import { StructuredDataScript } from "@/components/seo/structured-data-script";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { buildPageMetadata } from "@/lib/site-metadata";
import { buildWebsiteStructuredData } from "@/lib/structured-data";
import { cn } from "@/lib/utils";

export const metadata: Metadata = buildPageMetadata({
  title: "How NoteLib Works — Notes to Study Packs and Quiz Practice",
  description: "See how NoteLib turns saved notes into Study Packs, quizzes, Board Exam practice, and review insights.",
  path: "/how-it-works",
});

const howItWorksDescription =
  "See how NoteLib turns saved notes into Study Packs, quizzes, Board Exam practice, and review insights.";

const steps = [
  {
    title: "Build your notes library",
    description: "Paste class notes, upload reviewers, or write directly into your workspace.",
    icon: FileText,
  },
  {
    title: "Generate a Study Pack",
    description: "Turn one saved note into a summary, key concepts, and quiz material when review starts.",
    icon: Sparkles,
  },
  {
    title: "Practice and improve",
    description: "Use Quick Review, Challenge Quiz, or Board Exam Mode, then return to the concepts that need more review.",
    icon: Trophy,
  },
] as const;

const walkthroughItems = [
  {
    title: "Add Notes",
    description: "Capture lecture notes, reviewers, and study references in one note so your material stays reusable.",
    src: "/landing/feature-note-editor.jpg",
    alt: "NoteLib note editor showing note writing and metadata entry",
  },
  {
    title: "Generate Study Pack",
    description: "Turn your saved note into a structured Study Pack with summary, key concepts, and quiz-ready material.",
    src: "/landing/feature-study-pack.jpg",
    alt: "NoteLib Study Pack view showing generated summary and key concepts",
  },
  {
    title: "Take Quiz",
    description: "Use Challenge Quiz and Board Exam Mode to test recall in a more focused, exam-style practice flow.",
    src: "/landing/feature-quiz.jpg",
    alt: "NoteLib Board Exam Mode and Challenge Quiz in-progress screen",
  },
  {
    title: "Review Results",
    description: "Check your score, weak concepts, and answer review so the next study session targets what matters.",
    src: "/landing/feature-results.jpg",
    alt: "NoteLib quiz results and weak concept review screen",
  },
] as const;

const valueSummaries = [
  {
    title: "From notes to structured study packs",
    description: "Move from raw notes to summaries, concepts, and quiz material without rebuilding your study flow each time.",
  },
  {
    title: "Practice like a real exam",
    description: "Board Exam Mode and Challenge Quiz give you timed, focused practice instead of one-off answer checking.",
  },
  {
    title: "Know exactly what to improve",
    description: "Results and weak-concept guidance make the next review session more intentional.",
  },
] as const;

function WalkthroughSection({
  title,
  description,
  src,
  alt,
  reverse = false,
}: Readonly<{
  title: string;
  description: string;
  src: string;
  alt: string;
  reverse?: boolean;
}>) {
  return (
    <section className="grid gap-6 lg:grid-cols-[1.02fr_0.98fr] lg:items-center lg:gap-10">
      <div className={cn(reverse ? "lg:order-2" : "")}>
        <ProductScreenshotFrame src={src} alt={alt} />
      </div>
      <div className={cn("space-y-3", reverse ? "lg:order-1" : "")}>
        <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">Workflow</p>
        <h2 className="text-2xl font-semibold sm:text-3xl">{title}</h2>
        <p className="max-w-2xl text-sm leading-relaxed text-foreground/75 sm:text-base">{description}</p>
      </div>
    </section>
  );
}

export default function HowItWorksPage() {
  return (
    <main className="mx-auto w-full max-w-6xl space-y-10 px-4 py-8 sm:px-6 sm:py-12">
      <StructuredDataScript
        id="how-it-works-structured-data"
        data={buildWebsiteStructuredData(howItWorksDescription)}
      />
      <AnalyticsPageViewTracker eventType="LANDING_PAGE_VIEWED" metadata={{ page: "how_it_works" }} />

      <section className="relative overflow-hidden rounded-[2rem] border border-sky-500/20 bg-[radial-gradient(circle_at_top_left,_rgba(14,165,233,0.14),_transparent_34%),radial-gradient(circle_at_bottom_right,_rgba(245,158,11,0.14),_transparent_30%),linear-gradient(135deg,_rgba(255,255,255,0.98),_rgba(239,246,255,0.96))] p-6 shadow-sm dark:bg-[radial-gradient(circle_at_top_left,_rgba(14,165,233,0.18),_transparent_34%),radial-gradient(circle_at_bottom_right,_rgba(245,158,11,0.16),_transparent_30%),linear-gradient(135deg,_rgba(2,6,23,0.96),_rgba(15,23,42,0.94))] sm:p-8 lg:p-10">
        <div className="space-y-5">
          <BrandFullLogo width={224} height={48} priority />
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">How it Works</p>
            <h1 className="max-w-4xl text-3xl font-semibold tracking-tight text-foreground sm:text-4xl lg:text-5xl">
              How NoteLib Works
            </h1>
            <p className="max-w-3xl text-sm leading-relaxed text-foreground/75 sm:text-base">
              Go from notes to self-testing in a simple study workflow.
            </p>
            <p className="max-w-3xl text-sm leading-relaxed text-foreground/75 sm:text-base">
              Add notes, generate a study pack, and test yourself with quiz practice built from the material you already keep.
            </p>
          </div>
        </div>
      </section>

      <section className="space-y-5">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">Simple 3-Step Flow</p>
          <h2 className="text-2xl font-semibold sm:text-3xl">Start with your notes, then move into review</h2>
        </div>
        <div className="grid gap-4 md:grid-cols-3">
          {steps.map((step, index) => (
            <Card key={step.title} className="space-y-4 p-5 sm:p-6">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">
                  Step {index + 1}
                </span>
                <step.icon className="h-5 w-5 text-sky-600 dark:text-sky-400" />
              </div>
              <div className="space-y-2">
                <CardTitle>{step.title}</CardTitle>
                <CardDescription className="text-sm">{step.description}</CardDescription>
              </div>
            </Card>
          ))}
        </div>
      </section>

      <section className="space-y-10">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">Visual Walkthrough</p>
          <h2 className="text-2xl font-semibold sm:text-3xl">See the workflow in the real product</h2>
        </div>
        {walkthroughItems.map((item, index) => (
          <WalkthroughSection
            key={item.title}
            title={item.title}
            description={item.description}
            src={item.src}
            alt={item.alt}
            reverse={index % 2 === 1}
          />
        ))}
      </section>

      <section className="space-y-5">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">Why This Flow Works</p>
          <h2 className="text-2xl font-semibold sm:text-3xl">Study support that stays tied to your own material</h2>
        </div>
        <div className="grid gap-4 md:grid-cols-3">
          {valueSummaries.map((item) => (
            <Card key={item.title} className="space-y-3 p-5">
              <CardTitle>{item.title}</CardTitle>
              <CardDescription className="text-sm">{item.description}</CardDescription>
            </Card>
          ))}
        </div>
      </section>

      <section className="rounded-[2rem] border border-amber-500/20 bg-[linear-gradient(135deg,_rgba(255,251,235,0.95),_rgba(255,255,255,0.98))] p-6 shadow-sm dark:bg-[linear-gradient(135deg,_rgba(69,26,3,0.22),_rgba(15,23,42,0.94))] sm:p-8">
        <div className="space-y-3">
          <div className="inline-flex items-center gap-2 rounded-full border border-amber-500/20 bg-amber-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-amber-700 dark:text-amber-300">
            <Trophy className="h-3.5 w-3.5" />
            Board Exam Mode — Pro
          </div>
          <h2 className="text-2xl font-semibold sm:text-3xl">Practice under stricter exam conditions</h2>
          <p className="max-w-3xl text-sm leading-relaxed text-foreground/75 sm:text-base">
            Use timed exam-style practice, a more distraction-free session flow, and delayed results so the experience feels closer to real test conditions.
          </p>
        </div>
      </section>

      <section className="rounded-[2rem] border border-sky-500/20 bg-[linear-gradient(135deg,_rgba(224,242,254,0.78),_rgba(255,255,255,0.96))] p-6 shadow-sm dark:bg-[linear-gradient(135deg,_rgba(12,74,110,0.36),_rgba(15,23,42,0.94))] sm:p-8">
        <div className="space-y-4 text-center">
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">Start Today</p>
            <h2 className="text-2xl font-semibold sm:text-3xl">Build a study system from the notes you already have.</h2>
          </div>
          <div className="flex flex-col justify-center gap-3 sm:flex-row">
            <TrackedLink
              href="/signup"
              className={buttonVariants({ className: "w-full sm:w-auto" })}
              eventType="LANDING_CTA_CLICKED"
              eventMetadata={{ placement: "how_it_works_primary", destination: "/signup" }}
            >
              Start for Free
            </TrackedLink>
            <TrackedLink
              href="/pricing"
              className={buttonVariants({ variant: "outline", className: "w-full sm:w-auto" })}
              eventType="LANDING_CTA_CLICKED"
              eventMetadata={{ placement: "how_it_works_secondary", destination: "/pricing" }}
            >
              View Pricing
            </TrackedLink>
          </div>
        </div>
      </section>

      <PublicFooter />
    </main>
  );
}
