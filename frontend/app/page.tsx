import type { Metadata } from "next";
import {
  ArrowRight,
  BookOpen,
  Brain,
  FileText,
  Globe,
  Library,
  ListChecks,
  Sparkles,
  Target,
} from "lucide-react";
import { AnalyticsPageViewTracker } from "@/components/analytics/page-view-tracker";
import { TrackedLink } from "@/components/analytics/tracked-link";
import { SimplePricingSection } from "@/components/billing/pricing-plans-section";
import { BrandFullLogo, BrandProductIcon } from "@/components/branding/brand-assets";
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
    title: "Create a Note",
    description: "Paste notes, upload images, or write directly.",
    icon: FileText,
  },
  {
    title: "Build Your Library",
    description: "Organize notes by subject and tags.",
    icon: Library,
  },
  {
    title: "Generate Study Pack",
    description: "Get summary, key concepts, and quiz.",
    icon: Sparkles,
  },
  {
    title: "Review & Practice",
    description: "Use Quick Review, Challenge Quiz, and Adaptive Practice.",
    icon: Brain,
  },
];

const libraryHighlights = [
  {
    title: "Notes library first",
    description: "Keep subjects, tags, and study material in one place instead of generating one-off outputs.",
    icon: BookOpen,
  },
  {
    title: "Study Pack when ready",
    description: "Turn saved notes into summaries, key concepts, and quizzes when you want to review.",
    icon: Sparkles,
  },
  {
    title: "Active recall built in",
    description: "Move from reading to self-testing with quizzes, weak concepts, and focused practice.",
    icon: Target,
  },
];

function HeroSection() {
  return (
    <section className="relative overflow-hidden rounded-[2rem] border border-border bg-[radial-gradient(circle_at_top_left,_rgba(14,165,233,0.16),_transparent_34%),radial-gradient(circle_at_bottom_right,_rgba(16,185,129,0.12),_transparent_28%),linear-gradient(135deg,_rgba(255,255,255,0.98),_rgba(239,246,255,0.94))] p-6 shadow-sm dark:bg-[radial-gradient(circle_at_top_left,_rgba(14,165,233,0.18),_transparent_34%),radial-gradient(circle_at_bottom_right,_rgba(16,185,129,0.12),_transparent_28%),linear-gradient(135deg,_rgba(2,6,23,0.96),_rgba(15,23,42,0.94))] sm:p-8 lg:p-10">
      <div className="grid gap-8 lg:grid-cols-[1.05fr_0.95fr] lg:items-center">
        <div className="space-y-5">
          <BrandFullLogo width={220} height={48} priority />
          <div className="inline-flex items-center gap-2 rounded-full border border-sky-500/20 bg-background/85 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-sky-700 dark:text-sky-300">
            Notes library + study workspace
          </div>
          <div className="space-y-3">
            <h1 className="max-w-4xl text-3xl font-semibold tracking-tight text-foreground sm:text-4xl lg:text-5xl">
              Build your own library of notes. Turn them into summaries and quizzes when you&apos;re ready to
              review.
            </h1>
            <p className="max-w-3xl text-sm leading-relaxed text-foreground/75 sm:text-base">
              NoteLib helps you organize your notes, generate summaries, extract key concepts, and practice with
              quizzes all in one study workspace.
            </p>
          </div>
          <div className="flex flex-col gap-3 sm:flex-row">
            <TrackedLink
              href="/signup"
              className={buttonVariants({ className: "w-full sm:w-auto" })}
              eventType="LANDING_CTA_CLICKED"
              eventMetadata={{ placement: "hero_primary", destination: "/signup" }}
            >
              Get Started
            </TrackedLink>
            <TrackedLink
              href="/public/library"
              className={buttonVariants({ variant: "outline", className: "w-full sm:w-auto" })}
              eventType="LANDING_CTA_CLICKED"
              eventMetadata={{ placement: "hero_secondary", destination: "/public/library" }}
            >
              View Public Library
            </TrackedLink>
          </div>
          <div className="flex flex-wrap items-center gap-3 text-sm text-foreground/70">
            <span className="inline-flex items-center gap-2">
              <BookOpen className="h-4 w-4 text-sky-600 dark:text-sky-400" />
              Save and organize notes by subject
            </span>
            <span className="inline-flex items-center gap-2">
              <Brain className="h-4 w-4 text-sky-600 dark:text-sky-400" />
              Practice with active recall
            </span>
          </div>
          <TrackedLink
            href="/demo"
            className="inline-flex items-center gap-2 text-sm font-medium text-sky-700 transition hover:text-sky-800 dark:text-sky-300 dark:hover:text-sky-200"
            eventType="LANDING_CTA_CLICKED"
            eventMetadata={{ placement: "hero_demo_link", destination: "/demo" }}
          >
            Try demo access
            <ArrowRight className="h-4 w-4" />
          </TrackedLink>
        </div>

        <Card className="overflow-hidden border-sky-500/20 bg-background/90 p-0 shadow-lg backdrop-blur">
          <div className="border-b border-border bg-muted/30 px-5 py-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">
              Inside your workspace
            </p>
            <p className="mt-1 text-sm text-foreground/75">
              Build a reusable library of notes first, then switch into review mode when you need summaries,
              key concepts, and practice.
            </p>
          </div>
          <div className="space-y-4 p-5">
            <div className="rounded-2xl border border-border bg-background p-4">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">
                    Notes library
                  </p>
                  <div className="mt-3 flex flex-wrap gap-2">
                    <span className="rounded-full border border-border bg-muted/20 px-3 py-1.5 text-sm font-medium">
                      Med Surg
                    </span>
                    <span className="rounded-full border border-border bg-muted/20 px-3 py-1.5 text-sm font-medium">
                      Pharmacology
                    </span>
                    <span className="rounded-full border border-border bg-muted/20 px-3 py-1.5 text-sm font-medium">
                      Board Review
                    </span>
                  </div>
                </div>
                <div className="rounded-2xl border border-sky-500/20 bg-sky-500/10 p-3">
                  <BrandProductIcon size={72} className="h-14 w-14 sm:h-[72px] sm:w-[72px]" priority />
                </div>
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2 text-sm font-medium text-foreground">
              <span className="rounded-full border border-border bg-muted/20 px-3 py-1.5">Note</span>
              <ArrowRight className="h-4 w-4 text-sky-600 dark:text-sky-400" />
              <span className="rounded-full border border-border bg-muted/20 px-3 py-1.5">Summary</span>
              <ArrowRight className="h-4 w-4 text-sky-600 dark:text-sky-400" />
              <span className="rounded-full border border-border bg-muted/20 px-3 py-1.5">Key Concepts</span>
              <ArrowRight className="h-4 w-4 text-sky-600 dark:text-sky-400" />
              <span className="rounded-full border border-border bg-muted/20 px-3 py-1.5">Quiz</span>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="rounded-2xl border border-border bg-background p-4">
                <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">Review modes</p>
                <p className="mt-2 text-sm text-foreground/80">
                  Quick Review, Challenge Quiz, and Adaptive Practice help you shift from reading to active recall.
                </p>
              </div>
              <div className="rounded-2xl border border-border bg-background p-4">
                <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">Public Library</p>
                <p className="mt-2 text-sm text-foreground/80">
                  Discover public notes, copy useful reviewers, and share your own notes when you want to help others.
                </p>
              </div>
            </div>
          </div>
        </Card>
      </div>
    </section>
  );
}

function WhatIsNoteLibSection() {
  return (
    <section className="grid gap-5 lg:grid-cols-[0.95fr_1.05fr] lg:items-start">
      <Card className="space-y-4 p-6">
        <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">
          What Is NoteLib
        </p>
        <div className="space-y-3">
          <CardTitle className="text-2xl sm:text-3xl">Your Notes. Your Library. Your Review Tool.</CardTitle>
          <CardDescription className="text-sm leading-relaxed sm:text-base">
            NoteLib is a study workspace where you build your own library of notes. When you&apos;re ready to review,
            you can turn your notes into summaries, key concepts, and practice quizzes to test your understanding and
            focus on weak areas.
          </CardDescription>
        </div>
      </Card>
      <div className="grid gap-4 sm:grid-cols-3">
        {libraryHighlights.map((item) => (
          <Card key={item.title} className="space-y-4 p-5">
            <item.icon className="h-5 w-5 text-sky-600 dark:text-sky-400" />
            <div className="space-y-2">
              <CardTitle className="text-lg">{item.title}</CardTitle>
              <CardDescription className="text-sm">{item.description}</CardDescription>
            </div>
          </Card>
        ))}
      </div>
    </section>
  );
}

function HowItWorksSection() {
  return (
    <section className="space-y-5">
      <div className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">
          How It Works
        </p>
        <h2 className="text-2xl font-semibold sm:text-3xl">From note capture to active recall in four steps</h2>
      </div>
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
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
    </section>
  );
}

function PublicLibrarySection() {
  return (
    <section className="rounded-[2rem] border border-border bg-[linear-gradient(135deg,_rgba(240,249,255,0.92),_rgba(255,255,255,0.98))] p-6 shadow-sm dark:bg-[linear-gradient(135deg,_rgba(8,47,73,0.46),_rgba(15,23,42,0.95))] sm:p-8">
      <div className="grid gap-6 lg:grid-cols-[1fr_auto] lg:items-end">
        <div className="space-y-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">
            Public Library
          </p>
          <h2 className="text-2xl font-semibold sm:text-3xl">Explore Public Notes and Reviewers</h2>
          <p className="max-w-3xl text-sm leading-relaxed text-foreground/75 sm:text-base">
            Browse public notes shared by other students and turn them into quizzes instantly. You can also share your
            own notes to help others.
          </p>
          <div className="flex flex-wrap gap-3 text-sm text-foreground/75">
            <span className="inline-flex items-center gap-2 rounded-full border border-border bg-background/80 px-3 py-1.5">
              <Globe className="h-4 w-4 text-sky-600 dark:text-sky-400" />
              Discover shared notes
            </span>
            <span className="inline-flex items-center gap-2 rounded-full border border-border bg-background/80 px-3 py-1.5">
              <ListChecks className="h-4 w-4 text-sky-600 dark:text-sky-400" />
              Copy useful reviewers into your library
            </span>
          </div>
        </div>
        <TrackedLink
          href="/public/library"
          className={buttonVariants({ variant: "outline", className: "w-full sm:w-auto" })}
          eventType="LANDING_CTA_CLICKED"
          eventMetadata={{ placement: "public_library_section", destination: "/public/library" }}
        >
          Browse Public Library
        </TrackedLink>
      </div>
    </section>
  );
}

function StudyMethodSection() {
  return (
    <section className="grid gap-5 lg:grid-cols-[1.05fr_0.95fr] lg:items-center">
      <Card className="space-y-4 p-6">
        <p className="text-xs font-semibold uppercase tracking-wide text-sky-600 dark:text-sky-400">
          Study Method
        </p>
        <div className="space-y-3">
          <CardTitle className="text-2xl sm:text-3xl">Study Smarter with Active Recall</CardTitle>
          <CardDescription className="text-sm leading-relaxed sm:text-base">
            NoteLib is built around active recall, a study method where you test yourself instead of just re-reading
            notes. This helps you remember more and prepare better for exams and board exams.
          </CardDescription>
        </div>
        <TrackedLink
          href="/learn"
          className={buttonVariants({ variant: "outline", className: "w-full sm:w-auto" })}
          eventType="LANDING_CTA_CLICKED"
          eventMetadata={{ placement: "study_method", destination: "/learn" }}
        >
          Learn How to Study Using Active Recall
        </TrackedLink>
      </Card>
      <Card className="space-y-4 p-6">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">Why this matters</p>
        <div className="space-y-3 text-sm text-foreground/80">
          <p>
            Re-reading notes can feel productive without showing what you actually remember. Active recall closes that
            gap by forcing your brain to retrieve information, not just recognize it.
          </p>
          <p>
            NoteLib keeps that method practical by connecting your saved notes to summaries, key concepts, quiz
            practice, and weak-area follow-up in the same workspace.
          </p>
        </div>
      </Card>
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
          <h2 className="text-2xl font-semibold sm:text-3xl">Start building your notes library today.</h2>
        </div>
        <div className="flex flex-col justify-center gap-3 sm:flex-row">
          <TrackedLink
            href="/signup"
            className={buttonVariants({ className: "w-full sm:w-auto" })}
            eventType="LANDING_CTA_CLICKED"
            eventMetadata={{ placement: "bottom_cta_primary", destination: "/signup" }}
          >
            Get Started
          </TrackedLink>
          <TrackedLink
            href="/public/library"
            className={buttonVariants({ variant: "outline", className: "w-full sm:w-auto" })}
            eventType="LANDING_CTA_CLICKED"
            eventMetadata={{ placement: "bottom_cta_secondary", destination: "/public/library" }}
          >
            View Public Library
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
      <WhatIsNoteLibSection />
      <HowItWorksSection />
      <PublicLibrarySection />
      <StudyMethodSection />
      <SimplePricingSection />
      <FinalCtaSection />
      <PublicFooter />
    </main>
  );
}
