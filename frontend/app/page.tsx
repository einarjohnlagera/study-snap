import type { Metadata } from "next";
import {
  ArrowRight,
  BookOpen,
  FileText,
  Library,
  Sparkles,
  Trophy,
} from "lucide-react";
import { AnalyticsPageViewTracker } from "@/components/analytics/page-view-tracker";
import { TrackedLink } from "@/components/analytics/tracked-link";
import { SimplePricingSection } from "@/components/billing/pricing-plans-section";
import { BrandFullLogo } from "@/components/branding/brand-assets";
import { ProductScreenshotFrame } from "@/components/public/product-screenshot-frame";
import { PublicFooter } from "@/components/public/public-footer";
import { StructuredDataScript } from "@/components/seo/structured-data-script";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { SITE_NAME } from "@/lib/site-metadata";
import { buildWebsiteStructuredData } from "@/lib/structured-data";

const landingPageDescription =
  "NoteLib is a notes library where you can organize notes and turn them into summaries, key concepts, and practice quizzes to review more effectively.";
const landingPageTitle = "NoteLib — Build your notes library and turn notes into quizzes";
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
        alt: "Build your notes library. Turn your notes into summaries and quizzes.",
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

const howItWorksSteps = [
  {
    title: "Add your notes",
    description: "Paste notes, upload reviewers, or write directly.",
    icon: FileText,
  },
  {
    title: "Generate a study pack",
    description: "Get a summary, key concepts, and quiz from a single note.",
    icon: Sparkles,
  },
  {
    title: "Review actively",
    description: "Practice using quizzes and exam-style formats.",
    icon: Trophy,
  },
];

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

const targetUsers = [
  {
    title: "Students",
    description: "Stay organized and review smarter.",
    icon: BookOpen,
  },
  {
    title: "Board Exam Takers",
    description: "Practice topics and test your understanding.",
    icon: Trophy,
  },
  {
    title: "Teachers",
    description: "Turn notes into ready-to-use quizzes and materials.",
    icon: Library,
  },
];

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
    title: "Turn one note into a full study session",
    description: "Turn a saved note into a summary, key concepts, and quiz material without rebuilding your workflow.",
    icon: Sparkles,
  },
  {
    title: "Practice like the real exam",
    description: "Use Quiz and Board Exam Mode for timed, focused self-testing that feels closer to real review conditions.",
    icon: Trophy,
  },
  {
    title: "Know exactly what to improve",
    description: "Use results and weak-concept guidance to focus your next study session on what you still miss.",
    icon: ArrowRight,
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
              Notes to active recall
            </span>
            <span className="inline-flex items-center gap-2 rounded-full border border-amber-500/20 bg-amber-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-amber-700 dark:text-amber-300">
              Board Exam Mode · Premium
            </span>
          </div>
          <div className="space-y-3">
            <h1 className="max-w-4xl text-3xl font-semibold tracking-tight text-foreground sm:text-4xl lg:text-5xl">
              Turn your notes into real study tools
            </h1>
            <p className="text-base font-medium text-foreground/85 sm:text-lg">
              Stop rereading. Start remembering.
            </p>
            <p className="max-w-3xl text-sm leading-relaxed text-foreground/75 sm:text-base">
              Write or upload your notes, then turn them into summaries, key concepts, and quizzes when it&apos;s time to review.
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

function HowItWorksSection() {
  return (
    <section className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">How It Works</p>
          <h2 className="text-2xl font-semibold sm:text-3xl">Go from notes to self-testing in three steps</h2>
          <p className="text-sm leading-relaxed text-foreground/70">
            From notes → to quiz → to exam-ready review
          </p>
        </div>
        <TrackedLink
          href="/how-it-works"
          className="inline-flex items-center gap-2 text-sm font-medium text-sky-700 transition hover:text-sky-800 dark:text-sky-300 dark:hover:text-sky-200"
          eventType="LANDING_CTA_CLICKED"
          eventMetadata={{ placement: "how_it_works_section", destination: "/how-it-works" }}
        >
          View the full walkthrough
          <ArrowRight className="h-4 w-4" />
        </TrackedLink>
      </div>
      <div className="grid gap-4 md:grid-cols-3">
        {howItWorksSteps.map((step, index) => (
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
      <p className="text-sm text-foreground/65">
        Not ready to sign up?{" "}
        <TrackedLink
          href="/demo"
          className="font-medium text-sky-700 underline-offset-2 hover:underline dark:text-sky-300"
          eventType="LANDING_CTA_CLICKED"
          eventMetadata={{ placement: "how_it_works_demo", destination: "/demo" }}
        >
          Try the demo first — no account needed.
        </TrackedLink>
      </p>
    </section>
  );
}

function ValueSummarySection() {
  return (
    <section className="space-y-5">
      <div className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">Features</p>
        <h2 className="text-2xl font-semibold sm:text-3xl">Built for quick understanding and repeated review</h2>
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

function TargetUsersSection() {
  return (
    <section className="space-y-5">
      <div className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">Who It&apos;s For</p>
        <h2 className="text-2xl font-semibold sm:text-3xl">Made for learners who need more than passive notes</h2>
      </div>
      <div className="grid gap-4 md:grid-cols-3">
        {targetUsers.map((user) => (
          <Card key={user.title} className="space-y-4 p-5">
            <user.icon className="h-5 w-5 text-sky-600 dark:text-sky-400" />
            <div className="space-y-2">
              <CardTitle>{user.title}</CardTitle>
              <CardDescription className="text-sm">{user.description}</CardDescription>
            </div>
          </Card>
        ))}
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
            Turn saved notes into summaries, quizzes, and Board Exam Mode sessions that keep you reviewing with
            purpose.
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
      <HowItWorksSection />
      <ValueSummarySection />
      <PublicLibrarySection />
      <DifferentiationSection />
      <TargetUsersSection />
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
