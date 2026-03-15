import Link from "next/link";
import Image from "next/image";
import {
  ArrowDown,
  ArrowRight,
  Bot,
  Brain,
  Compass,
  FileImage,
  ListChecks,
  ScanText,
  Sparkles,
  Target,
  TrendingUp,
} from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";

const coreOutputs = [
  {
    title: "Summary",
    description: "Understand the topic quickly with a clear summary of your notes.",
    icon: Sparkles,
  },
  {
    title: "Key Concepts",
    description: "See the most important ideas and terms in a simple, scannable list.",
    icon: Brain,
  },
  {
    title: "Quick Review",
    description: "Practice active recall with quiz questions, feedback, and retry support.",
    icon: ListChecks,
  },
];

const highlights = [
  { label: "OCR from Notes Images", icon: FileImage },
  { label: "AI Summaries", icon: Sparkles },
  { label: "Weak Concept Detection", icon: Target },
  { label: "Adaptive Practice", icon: TrendingUp },
  { label: "AI Study Coach", icon: Bot },
  { label: "Today's Focus Guidance", icon: Compass },
];

function HeroTransformationPreview() {
  return (
    <div className="rounded-xl border border-border/80 bg-background/80 p-3 shadow-sm backdrop-blur sm:p-4">
      <div className="space-y-3 sm:grid sm:grid-cols-[1fr_auto_1fr] sm:items-stretch sm:gap-3 sm:space-y-0">
        <Card className="space-y-3 p-3 sm:p-4">
          <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/60">Raw Notes</p>
          <div className="space-y-2 text-xs leading-relaxed text-foreground/80">
            <p>Photosynthesis is how plants make food using sunlight.</p>
            <p>Chlorophyll absorbs light energy in the leaves.</p>
            <p>Water + carbon dioxide help produce glucose.</p>
            <p>Oxygen is released. Key stages: light-dependent reactions and Calvin cycle.</p>
          </div>
        </Card>

        <div className="flex items-center justify-center">
          <div className="inline-flex items-center gap-2 rounded-full border border-blue-200 bg-blue-50 px-3 py-1 text-[11px] font-medium text-blue-700 dark:border-blue-800 dark:bg-blue-950/40 dark:text-blue-300 sm:flex-col sm:rounded-xl sm:px-2.5 sm:py-2">
            <span>AI transforms</span>
            <ArrowRight className="hidden h-3.5 w-3.5 sm:block" />
            <ArrowDown className="h-3.5 w-3.5 sm:hidden" />
          </div>
        </div>

        <Card className="space-y-3 p-3 sm:p-4">
          <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/60">Study Pack</p>
          <div className="space-y-2 text-xs text-foreground/85">
            <p className="font-semibold text-foreground">Photosynthesis</p>
            <div>
              <p className="font-medium text-foreground/80">Summary</p>
              <p>Plants convert light energy into chemical energy to produce glucose.</p>
            </div>
            <div>
              <p className="font-medium text-foreground/80">Key Concepts</p>
              <ul className="list-disc pl-4">
                <li>Chlorophyll</li>
                <li>Calvin cycle</li>
                <li>Light-dependent reactions</li>
              </ul>
            </div>
            <div>
              <p className="font-medium text-foreground/80">Quiz Preview</p>
              <p>What is the main role of chlorophyll?</p>
            </div>
          </div>
        </Card>
      </div>
    </div>
  );
}

export default function Home() {
  return (
    <main className="mx-auto w-full max-w-6xl space-y-10 px-4 py-8 sm:px-6 sm:py-12">
      <section className="relative overflow-hidden rounded-2xl border border-border bg-gradient-to-br from-blue-50 to-white p-6 shadow-sm dark:from-blue-950/30 dark:to-gray-950 sm:p-10">
        <div className="absolute -right-10 -top-10 h-40 w-40 rounded-full bg-blue-500/10 blur-3xl" />
        <div className="relative grid gap-8 lg:grid-cols-[1.05fr_0.95fr] lg:items-center">
          <div className="space-y-5">
            <Image
              src="/study-snap-logo-full.svg"
              alt="Study Snap"
              width={240}
              height={48}
              priority
            />
            <div className="inline-flex items-center gap-2 rounded-lg border border-border bg-background px-3 py-1 text-xs text-foreground/80 sm:text-sm">
              <ScanText className="h-4 w-4 text-blue-600 dark:text-blue-400" />
              AI study assistant for notes, review, and practice
            </div>
            <h1 className="max-w-3xl text-3xl font-semibold tracking-tight text-foreground sm:text-4xl md:text-5xl">
              Turn your notes into study packs in seconds
            </h1>
            <p className="max-w-2xl text-sm leading-relaxed text-foreground/75 sm:text-base">
              Upload or paste your notes and Study Snap turns them into summaries, key concepts, and quiz
              questions so you can review faster.
            </p>
            <div className="flex flex-col gap-3 sm:flex-row">
              <Link href="/demo" className={buttonVariants({ className: "w-full sm:w-auto" })}>
                Try Demo
              </Link>
              <Link
                href="/auth"
                className={buttonVariants({ variant: "outline", className: "w-full sm:w-auto" })}
              >
                Start Free
              </Link>
            </div>
            <p className="text-sm text-foreground/75">
              Perfect for lecture notes, textbook pages, and study reviewers.
            </p>
            <div className="flex flex-wrap gap-2">
              <span className="rounded-full border border-border bg-background px-3 py-1 text-xs text-foreground/80">
                AI summary
              </span>
              <span className="rounded-full border border-border bg-background px-3 py-1 text-xs text-foreground/80">
                Key concepts
              </span>
              <span className="rounded-full border border-border bg-background px-3 py-1 text-xs text-foreground/80">
                Quick Review quiz
              </span>
            </div>
          </div>
          <HeroTransformationPreview />
        </div>
      </section>

      <section className="space-y-4">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Study Pack Preview
          </p>
          <h2 className="text-2xl font-semibold sm:text-3xl">Each Study Pack includes three essentials</h2>
        </div>
        <div className="grid gap-4 md:grid-cols-3">
          {coreOutputs.map((item) => (
            <Card key={item.title} className="space-y-3 p-4 sm:p-6">
              <item.icon className="h-5 w-5 text-blue-600 dark:text-blue-400" />
              <CardTitle>{item.title}</CardTitle>
              <CardDescription className="text-sm">{item.description}</CardDescription>
            </Card>
          ))}
        </div>
      </section>

      <section className="space-y-4">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            How It Works
          </p>
          <h2 className="text-2xl font-semibold sm:text-3xl">From notes to better review in 3 steps</h2>
        </div>
        <div className="grid gap-4 md:grid-cols-3">
          <Card className="space-y-2 p-4 sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Step 1</p>
            <h3 className="text-lg font-semibold">Upload notes</h3>
            <p className="text-sm text-foreground/75">Paste notes or upload an image. OCR extracts the text for you.</p>
          </Card>
          <Card className="space-y-2 p-4 sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Step 2</p>
            <h3 className="text-lg font-semibold">Generate Study Pack</h3>
            <p className="text-sm text-foreground/75">Get a summary, key concepts, and quiz questions in one place.</p>
          </Card>
          <Card className="space-y-2 p-4 sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Step 3</p>
            <h3 className="text-lg font-semibold">Review smarter</h3>
            <p className="text-sm text-foreground/75">Use Quick Review, spot weak areas, and practice what needs more work.</p>
          </Card>
        </div>
      </section>

      <section className="space-y-4">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Feature Highlights
          </p>
          <h2 className="text-2xl font-semibold sm:text-3xl">Built to guide your next best study step</h2>
        </div>
        <Card className="p-4 sm:p-6">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {highlights.map((feature) => (
              <div key={feature.label} className="flex items-center gap-2 rounded-lg border border-border bg-background px-3 py-2">
                <feature.icon className="h-4 w-4 text-blue-600 dark:text-blue-400" />
                <span className="text-sm text-foreground/85">{feature.label}</span>
              </div>
            ))}
          </div>
        </Card>
      </section>

      <section className="space-y-4">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Pricing
          </p>
          <h2 className="text-2xl font-semibold sm:text-3xl">Start free. Upgrade when you need more.</h2>
        </div>
        <div className="grid gap-4 md:grid-cols-2">
          <Card className="space-y-3 p-4 sm:p-6">
            <h3 className="text-xl font-semibold">Free</h3>
            <ul className="space-y-1 text-sm text-foreground/80">
              <li>5 Study Packs per month</li>
              <li>AI summaries</li>
              <li>Key concepts</li>
              <li>Quick Review quizzes</li>
              <li>Retry incorrect answers</li>
              <li>Study Library</li>
              <li>Today&apos;s Focus</li>
              <li>AI Study Coach</li>
            </ul>
            <Link href="/auth" className={buttonVariants({ className: "w-full sm:w-auto" })}>
              Start Free
            </Link>
          </Card>
          <Card className="space-y-3 border-blue-300 p-4 sm:p-6 dark:border-blue-700">
            <h3 className="text-xl font-semibold">Premium</h3>
            <p className="text-sm text-foreground/75">Everything in Free plus:</p>
            <ul className="space-y-1 text-sm text-foreground/80">
              <li>100 Study Packs per month</li>
              <li>Weak concept detection</li>
              <li>Adaptive quiz generation</li>
              <li>Advanced review tools</li>
            </ul>
            <Link href="/auth" className={buttonVariants({ className: "w-full sm:w-auto" })}>
              Upgrade to Premium
            </Link>
          </Card>
        </div>
      </section>

      <section className="rounded-2xl border border-border bg-gray-50 p-6 text-center shadow-sm dark:bg-gray-950/40 sm:p-10">
        <div className="mx-auto max-w-2xl space-y-4">
          <h2 className="text-2xl font-semibold sm:text-3xl">Ready to study with more clarity and consistency?</h2>
          <p className="text-sm text-foreground/75 sm:text-base">
            Create your account and turn your next set of notes into a reusable Study Pack for active review.
          </p>
          <div className="flex flex-col justify-center gap-3 sm:flex-row">
            <Link href="/auth" className={buttonVariants({ className: "w-full sm:w-auto" })}>
              Create Free Account
            </Link>
            <Link
              href="/demo"
              className={buttonVariants({ variant: "outline", className: "w-full sm:w-auto" })}
            >
              Explore Demo
            </Link>
          </div>
        </div>
      </section>
    </main>
  );
}
