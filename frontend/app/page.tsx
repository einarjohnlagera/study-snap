import type { Metadata } from "next";
import {
  ArrowRight,
  BookOpen,
  Briefcase,
  ClipboardList,
  Library,
  Sparkles,
  Target,
  Trophy,
} from "lucide-react";
import { AnalyticsPageViewTracker } from "@/components/analytics/page-view-tracker";
import { TrackedLink } from "@/components/analytics/tracked-link";
import { SimplePricingSection } from "@/components/billing/pricing-plans-section";
import { BrandFullLogo } from "@/components/branding/brand-assets";
import { ProfileLearningSection } from "@/components/landing/profile-learning-section";
import { ProductScreenshotFrame } from "@/components/public/product-screenshot-frame";
import { PublicFooter } from "@/components/public/public-footer";
import { StructuredDataScript } from "@/components/seo/structured-data-script";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { SITE_NAME } from "@/lib/site-metadata";
import { buildWebsiteStructuredData } from "@/lib/structured-data";

const landingPageDescription =
  "NoteLib is a study system that guides users from input to understanding, practice, and mastery with notes, summaries, key concepts, quizzes, and exam-ready review.";
const landingPageTitle = "NoteLib - Turn anything into a complete study flow";
const landingPageUrl = "https://www.notelib.app";
const landingPageOgImage = "https://www.notelib.app/og-image.png";

export const metadata: Metadata = {
  title: landingPageTitle,
  description: landingPageDescription,
  alternates: {
    canonical: landingPageUrl,
  },
  openGraph: {
    title: landingPageTitle,
    description: landingPageDescription,
    type: "website",
    url: landingPageUrl,
    siteName: SITE_NAME,
    images: [
      {
        url: landingPageOgImage,
        width: 1200,
        height: 630,
        alt: "Turn anything into a complete study flow with NoteLib.",
      },
    ],
  },
  twitter: {
    card: "summary_large_image",
    title: landingPageTitle,
    description: landingPageDescription,
    images: [landingPageOgImage],
  },
};

const differentiationRows = [
  {
    label: "Built around your own notes",
    generic: "Requires a fresh prompt every session.",
    noteLib: "Your notes stay in one place and drive every study tool.",
  },
  {
    label: "Designed for active recall",
    generic: "Usually ends at a summary or single answer.",
    noteLib: "Goes from note to study pack to quiz practice in one flow.",
  },
  {
    label: "Helps you review faster before exams",
    generic: "Rarely structured around your actual exam prep needs.",
    noteLib: "Summaries, key concepts, and quizzes ready from one saved note.",
  },
  {
    label: "Reuse notes anytime",
    generic: "Previous outputs are easy to lose or start over.",
    noteLib: "Notes stay reusable and ready for the next review cycle.",
  },
];

const studyModes = [
  {
    title: "Quick Review",
    description: "Lightweight concept check from your study pack.",
    icon: BookOpen,
    planChip: "Free",
  },
  {
    title: "Challenge Quiz",
    description: "Practice with stakes. Progressive question generation and mid-flight controls.",
    icon: Target,
    planChip: "All plans",
  },
  {
    title: "Adaptive Practice",
    description: "Targeted reinforcement on the concepts you keep missing.",
    icon: Sparkles,
    planChip: "Free taste",
  },
  {
    title: "Long Exam",
    description: "Comprehensive mastery test. Spans multiple notes. Fixed-length and timed.",
    icon: ClipboardList,
    planChip: "Pro",
  },
  {
    title: "Board Exam",
    description: "High-stakes simulation. Fullscreen, no feedback mid-exam.",
    icon: Trophy,
    planChip: "Pro",
  },
] as const;

const landingScreenshots = {
  noteEditor: {
    src: "/landing/feature-study-pack.jpg",
    alt: "NoteLib note detail showing summary of the note",
  },
  publicLibrary: {
    src: "/landing/feature-public-library.jpg",
    alt: "NoteLib Public Library preview showing note discovery cards and subject browsing",
  },
} as const;

const valueSummaryCards = [
  {
    title: "Built for studying",
    description: "Not just AI output — structured for real learning and exam preparation.",
    icon: BookOpen,
  },
  {
    title: "Learn from your weak points",
    description: "Adaptive practice focuses on the concepts you struggle with so you improve where it counts.",
    icon: Sparkles,
  },
  {
    title: "From notes to mastery",
    description: "Turn raw notes into summaries, quizzes, and review sessions that stick.",
    icon: Trophy,
  },
] as const;

function HeroSection() {
  return (
    <section className="relative overflow-hidden rounded-[2rem] border border-sky-500/20 bg-[radial-gradient(circle_at_top_left,_rgba(14,165,233,0.18),_transparent_34%),radial-gradient(circle_at_bottom_right,_rgba(245,158,11,0.18),_transparent_30%),linear-gradient(135deg,_rgba(255,255,255,0.98),_rgba(239,246,255,0.96))] p-6 shadow-sm dark:bg-[radial-gradient(circle_at_top_left,_rgba(14,165,233,0.18),_transparent_34%),radial-gradient(circle_at_bottom_right,_rgba(245,158,11,0.16),_transparent_30%),linear-gradient(135deg,_rgba(2,6,23,0.96),_rgba(15,23,42,0.94))] sm:p-8 lg:p-10">
      <div className="grid gap-8 lg:grid-cols-[1.05fr_0.95fr] lg:items-center">
        <div className="space-y-5">
          <BrandFullLogo width={224} height={48} priority />
          <div className="flex flex-wrap gap-2">
            <span className="inline-flex items-center gap-2 rounded-full border border-sky-500/20 bg-background/85 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-sky-700 dark:text-sky-300">
              5 study modes
            </span>
            <span className="inline-flex items-center gap-2 rounded-full border border-amber-500/20 bg-amber-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-amber-700 dark:text-amber-300">
              Pro: Long Exam · Board Exam · Interview Practice
            </span>
          </div>
          <div className="space-y-3">
            <h1 className="max-w-4xl text-3xl font-semibold tracking-tight text-foreground sm:text-4xl lg:text-5xl">
              Your notes become your study system.
            </h1>
            <p className="max-w-3xl text-sm leading-relaxed text-foreground/75 sm:text-base">
              Quizzes, exams, and interview practice — generated from notes you write, paste, or copy. Built around how you actually study.
            </p>
          </div>
          <div className="space-y-2">
            <div className="flex flex-col gap-3 sm:flex-row">
              <TrackedLink
                href="/signup"
                className={buttonVariants({ className: "w-full sm:w-auto" })}
                eventType="LANDING_CTA_CLICKED"
                eventMetadata={{ placement: "hero_primary", destination: "/signup" }}
              >
                Start for Free
              </TrackedLink>
              <TrackedLink
                href="/demo"
                className={buttonVariants({ variant: "outline", className: "w-full sm:w-auto" })}
                eventType="LANDING_CTA_CLICKED"
                eventMetadata={{ placement: "hero_secondary", destination: "/demo" }}
              >
                Try Demo
              </TrackedLink>
            </div>
            <p className="text-sm text-foreground/65">
              Free to start · No credit card required
            </p>
          </div>
        </div>

        <ProductScreenshotFrame
          src={landingScreenshots.noteEditor.src}
          alt={landingScreenshots.noteEditor.alt}
          priority
        />
      </div>
    </section>
  );
}

function PublicLibrarySection() {
  return (
    <section className="space-y-5 sm:space-y-6">
      <div className="rounded-[2rem] border border-border/80 bg-[linear-gradient(135deg,_rgba(248,250,252,0.98),_rgba(241,245,249,0.9))] p-5 shadow-sm dark:bg-[linear-gradient(135deg,_rgba(15,23,42,0.92),_rgba(15,23,42,0.82))] sm:p-6 lg:p-8">
        <div className="grid gap-8 lg:grid-cols-[0.92fr_1.08fr] lg:items-center">
          <div className="space-y-5">
            <div className="space-y-3">
              <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">Public Library</p>
              <h2 className="text-2xl font-semibold sm:text-3xl">Explore notes worth studying</h2>
              <p className="max-w-xl text-sm leading-relaxed text-foreground/75 sm:text-base">
                Browse notes shared by others. Copy them into your library and turn them into summaries, key concepts, and quizzes.
              </p>
              <p className="text-sm font-medium text-foreground/75">
                Start even if you don&apos;t have notes yet.
              </p>
            </div>
            <ul className="space-y-2 text-sm text-foreground/75">
              {[
                "Discover curated public notes",
                "Copy notes into your own library",
                "Turn them into summaries, concepts, and quizzes",
              ].map((item) => (
                <li key={item} className="flex items-start gap-3">
                  <span className="mt-1 inline-flex h-2 w-2 shrink-0 rounded-full bg-sky-500" aria-hidden="true" />
                  <span>{item}</span>
                </li>
              ))}
            </ul>
            <TrackedLink
              href="/public/library"
              className="inline-flex items-center gap-2 text-sm font-medium text-sky-700 transition hover:text-sky-800 dark:text-sky-300 dark:hover:text-sky-200"
              eventType="LANDING_CTA_CLICKED"
              eventMetadata={{ placement: "public_library_section", destination: "/public/library" }}
            >
              Browse Public Library
              <ArrowRight className="h-4 w-4" />
            </TrackedLink>
          </div>

          <div className="w-full lg:justify-self-end">
            <div className="mx-auto w-full max-w-xl rounded-[1.75rem] border border-border/80 bg-background/80 p-3 shadow-[0_18px_40px_rgba(15,23,42,0.08)] backdrop-blur-sm sm:p-4">
              <div className="mb-4 inline-flex items-center gap-2 rounded-full border border-sky-500/15 bg-sky-500/8 px-3 py-1 text-[11px] font-semibold uppercase tracking-wide text-sky-700 dark:text-sky-300">
                <Library className="h-3.5 w-3.5" />
                Live product preview
              </div>
              <ProductScreenshotFrame
                src={landingScreenshots.publicLibrary.src}
                alt={landingScreenshots.publicLibrary.alt}
                className="rounded-[1.35rem] border border-border/70 bg-muted/20 shadow-[0_14px_28px_rgba(15,23,42,0.08)]"
                imageClassName="max-h-[320px] object-contain object-top sm:max-h-[360px] lg:max-h-[400px]"
                sizes="(min-width: 1280px) 520px, (min-width: 1024px) 46vw, (min-width: 768px) 88vw, 100vw"
              />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

function ValueSummarySection() {
  return (
    <section className="space-y-5">
      <div className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">Why NoteLib</p>
        <h2 className="text-2xl font-semibold sm:text-3xl">Built for serious study</h2>
        <p className="max-w-3xl text-sm text-foreground/75">
          Every feature is designed to move you from reading to remembering.
        </p>
      </div>
      <div className="grid gap-4 md:grid-cols-3">
        {valueSummaryCards.map((item) => (
          <Card key={item.title} className="space-y-4 p-5">
            <item.icon className="h-5 w-5 text-sky-600 dark:text-sky-400" />
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

function DifferentiationSection() {
  return (
    <section className="space-y-5">
      <div className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">
          Why NoteLib
        </p>
        <h2 className="text-2xl font-semibold sm:text-3xl">Built for study, not just answers</h2>
      </div>
      <Card className="overflow-hidden p-0">
        <div className="grid border-b border-border bg-muted/20 text-sm font-medium sm:grid-cols-[0.9fr_1fr_1fr]">
          <div className="px-4 py-3 sm:px-6">What matters</div>
          <div className="border-t border-border px-4 py-3 text-foreground/70 sm:border-l sm:border-t-0 sm:px-6">
            Generic AI tools
          </div>
          <div className="border-t border-border bg-sky-500/8 px-4 py-3 sm:border-l sm:border-t-0 sm:px-6 dark:bg-sky-500/12">
            NoteLib
          </div>
        </div>
        {differentiationRows.map((row, index) => (
          <div
            key={row.label}
            className={`grid text-sm sm:grid-cols-[0.9fr_1fr_1fr] ${index === differentiationRows.length - 1 ? "" : "border-b border-border"}`}
          >
            <div className="px-4 py-4 font-medium sm:px-6">{row.label}</div>
            <div className="border-t border-border px-4 py-4 text-foreground/70 sm:border-l sm:border-t-0 sm:px-6">
              {row.generic}
            </div>
            <div className="border-t border-border bg-sky-500/8 px-4 py-4 text-foreground sm:border-l sm:border-t-0 sm:px-6 dark:bg-sky-500/12">
              {row.noteLib}
            </div>
          </div>
        ))}
      </Card>
    </section>
  );
}

function ModeShowcaseSection() {
  return (
    <section className="space-y-5">
      <div className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">Study Modes</p>
        <h2 className="text-2xl font-semibold sm:text-3xl">Five study modes, one workspace</h2>
        <p className="max-w-3xl text-sm text-foreground/75">
          Every mode runs on your notes. Pick the one that matches the moment.
        </p>
      </div>
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-5">
        {studyModes.map((mode) => (
          <Card key={mode.title} className="flex h-full flex-col gap-4 p-5">
            <mode.icon className="h-5 w-5 text-sky-600 dark:text-sky-400" />
            <div className="space-y-2">
              <CardTitle>{mode.title}</CardTitle>
              <CardDescription className="text-sm">{mode.description}</CardDescription>
            </div>
            <span className="mt-auto inline-flex w-fit items-center rounded-full border border-border bg-muted/40 px-2.5 py-0.5 text-[11px] font-semibold uppercase tracking-wide text-foreground/70">
              {mode.planChip}
            </span>
          </Card>
        ))}
      </div>
      <div className="rounded-2xl border border-amber-500/20 bg-amber-500/8 p-4 dark:bg-amber-500/12 sm:p-5">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:gap-4">
          <span className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-amber-500/30 bg-background/80 text-amber-700 dark:text-amber-300">
            <Briefcase className="h-4 w-4" />
          </span>
          <div className="space-y-1">
            <p className="text-sm font-semibold text-foreground">
              Also: Interview Practice
              <span className="ml-2 inline-flex items-center rounded-full border border-amber-500/30 bg-background/80 px-2 py-0.5 text-[11px] font-semibold uppercase tracking-wide text-amber-700 dark:text-amber-300">
                Pro · Professional profile
              </span>
            </p>
            <p className="text-sm text-foreground/75">
              Scenario-based MCQ plus per-answer AI critique. Built for professionals preparing for job interviews.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}

function PricingPreviewSection() {
  return (
    <section className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">
            Pricing
          </p>
          <h2 className="text-2xl font-semibold sm:text-3xl">Simple pricing. Start free.</h2>
          <p className="max-w-3xl text-sm text-foreground/75">
            Use NoteLib for free, and upgrade only when your review gets serious.
          </p>
        </div>
        <TrackedLink
          href="/pricing"
          className="inline-flex items-center gap-2 text-sm font-medium text-sky-700 transition hover:text-sky-800 dark:text-sky-300 dark:hover:text-sky-200"
          eventType="LANDING_CTA_CLICKED"
          eventMetadata={{ placement: "pricing_preview", destination: "/pricing" }}
        >
          See full pricing
          <ArrowRight className="h-4 w-4" />
        </TrackedLink>
      </div>
      <SimplePricingSection />
    </section>
  );
}

function FinalCtaSection() {
  return (
    <section className="rounded-[2rem] border border-sky-500/20 bg-[linear-gradient(135deg,_rgba(224,242,254,0.78),_rgba(255,255,255,0.96))] p-6 shadow-sm dark:bg-[linear-gradient(135deg,_rgba(12,74,110,0.36),_rgba(15,23,42,0.94))] sm:p-8">
      <div className="space-y-4 text-center">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">
            Start Today
          </p>
          <h2 className="text-2xl font-semibold sm:text-3xl">Start building your study system today</h2>
          <p className="mx-auto max-w-2xl text-sm leading-relaxed text-foreground/75 sm:text-base">
            Guide every topic from note input to understanding, practice, challenge, and improvement in one study loop.
          </p>
        </div>
        <div className="flex flex-col justify-center gap-3 sm:flex-row">
          <TrackedLink
            href="/signup"
            className={buttonVariants({ className: "w-full sm:w-auto" })}
            eventType="LANDING_CTA_CLICKED"
            eventMetadata={{ placement: "bottom_cta_primary", destination: "/signup" }}
          >
            Start for Free
          </TrackedLink>
        </div>
        <p className="text-sm text-foreground/65">Takes less than a minute.</p>
      </div>
    </section>
  );
}

export default function Home() {
  return (
    <main className="mx-auto w-full max-w-6xl space-y-10 px-4 py-8 pb-28 sm:px-6 sm:py-12 sm:pb-12">
      <StructuredDataScript
        id="landing-page-structured-data"
        data={buildWebsiteStructuredData(landingPageDescription)}
      />
      <AnalyticsPageViewTracker eventType="LANDING_PAGE_VIEWED" metadata={{ page: "landing" }} />
      <HeroSection />
      <ModeShowcaseSection />
      <ProfileLearningSection />
      <ValueSummarySection />
      <PublicLibrarySection />
      <DifferentiationSection />
      <PricingPreviewSection />
      <FinalCtaSection />
      <PublicFooter />
      <div className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-background/95 px-4 py-3 backdrop-blur sm:hidden">
        <TrackedLink
          href="/signup"
          className={buttonVariants({ className: "w-full" })}
          eventType="LANDING_CTA_CLICKED"
          eventMetadata={{ placement: "mobile_sticky_cta", destination: "/signup" }}
        >
          Start for Free
        </TrackedLink>
      </div>
    </main>
  );
}
