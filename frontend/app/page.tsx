import Link from "next/link";
import Image from "next/image";
import {
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
    description: "Get a concise explanation of your notes so you can start reviewing quickly.",
    icon: Sparkles,
  },
  {
    title: "Key Concepts",
    description: "See the important ideas and terms pulled into a clean, scannable list.",
    icon: Brain,
  },
  {
    title: "Quick Review",
    description: "Reinforce learning with short quizzes, feedback, and retry support.",
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

export default function Home() {
  return (
    <main className="mx-auto w-full max-w-6xl space-y-10 px-4 py-8 sm:px-6 sm:py-12">
      <section className="relative overflow-hidden rounded-2xl border border-border bg-gradient-to-br from-blue-50 to-white p-6 shadow-sm dark:from-blue-950/30 dark:to-gray-950 sm:p-10">
        <div className="absolute -right-10 -top-10 h-40 w-40 rounded-full bg-blue-500/10 blur-3xl" />
        <div className="relative space-y-5">
          <Image
            src="/study-snap-logo-full.svg"
            alt="Study Snap"
            width={240}
            height={48}
            priority
          />
          <div className="inline-flex items-center gap-2 rounded-lg border border-border bg-background px-3 py-1 text-xs text-foreground/80 sm:text-sm">
            <ScanText className="h-4 w-4 text-blue-600 dark:text-blue-400" />
            AI study support for notes, reviews, and practice
          </div>
          <h1 className="max-w-3xl text-3xl font-semibold tracking-tight text-foreground sm:text-4xl md:text-5xl">
            Turn your notes into study packs in seconds.
          </h1>
          <p className="max-w-2xl text-sm leading-relaxed text-foreground/75 sm:text-base">
            Study Snap helps you convert raw notes into a structured Study Pack with summaries, key concepts,
            and quiz questions. Then it guides what to review next with Quick Review, weak-area insights, and adaptive practice.
          </p>
          <div className="flex flex-col gap-3 sm:flex-row">
            <Link href="/auth" className={buttonVariants({ className: "w-full sm:w-auto" })}>
              Get Started Free
            </Link>
            <Link
              href="/study?demo=true"
              className={buttonVariants({ variant: "outline", className: "w-full sm:w-auto" })}
            >
              Try Demo
            </Link>
          </div>
        </div>
      </section>

      <section className="space-y-4">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Study Pack Preview
          </p>
          <h2 className="text-2xl font-semibold sm:text-3xl">Everything you need for focused review</h2>
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
          <h2 className="text-2xl font-semibold sm:text-3xl">From notes to smarter review in 3 steps</h2>
        </div>
        <div className="grid gap-4 md:grid-cols-3">
          <Card className="space-y-2 p-4 sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Step 1</p>
            <h3 className="text-lg font-semibold">Upload notes</h3>
            <p className="text-sm text-foreground/75">Paste text or upload an image for OCR extraction.</p>
          </Card>
          <Card className="space-y-2 p-4 sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Step 2</p>
            <h3 className="text-lg font-semibold">Generate Study Pack</h3>
            <p className="text-sm text-foreground/75">Get summary, concepts, and quiz questions instantly.</p>
          </Card>
          <Card className="space-y-2 p-4 sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Step 3</p>
            <h3 className="text-lg font-semibold">Review smarter</h3>
            <p className="text-sm text-foreground/75">Use Quick Review, weak areas, and adaptive practice to improve.</p>
          </Card>
        </div>
      </section>

      <section className="space-y-4">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Feature Highlights
          </p>
          <h2 className="text-2xl font-semibold sm:text-3xl">Built to guide your next study action</h2>
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
          <h2 className="text-2xl font-semibold sm:text-3xl">Start free, upgrade when you need more</h2>
        </div>
        <div className="grid gap-4 md:grid-cols-2">
          <Card className="space-y-3 p-4 sm:p-6">
            <h3 className="text-xl font-semibold">Free</h3>
            <p className="text-sm text-foreground/75">Great for getting started and daily study sessions.</p>
            <ul className="space-y-1 text-sm text-foreground/80">
              <li>Up to 3 Study Packs per day</li>
              <li>Quick Review and Study Library access</li>
              <li>OCR and AI-powered study generation</li>
            </ul>
          </Card>
          <Card className="space-y-3 border-blue-300 p-4 sm:p-6 dark:border-blue-700">
            <h3 className="text-xl font-semibold">Premium</h3>
            <p className="text-sm text-foreground/75">For heavier usage and more advanced study workflows.</p>
            <ul className="space-y-1 text-sm text-foreground/80">
              <li>Higher monthly Study Pack limits</li>
              <li>More room for continuous review practice</li>
              <li>Future advanced features as they launch</li>
            </ul>
          </Card>
        </div>
      </section>

      <section className="rounded-2xl border border-border bg-gray-50 p-6 text-center shadow-sm dark:bg-gray-950/40 sm:p-10">
        <div className="mx-auto max-w-2xl space-y-4">
          <h2 className="text-2xl font-semibold sm:text-3xl">Ready to study with structure and momentum?</h2>
          <p className="text-sm text-foreground/75 sm:text-base">
            Create your account and turn your next set of notes into a reusable Study Pack.
          </p>
          <div className="flex flex-col justify-center gap-3 sm:flex-row">
            <Link href="/auth" className={buttonVariants({ className: "w-full sm:w-auto" })}>
              Create Free Account
            </Link>
            <Link
              href="/study?demo=true"
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
