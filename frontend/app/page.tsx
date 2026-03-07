import Link from "next/link";
import Image from "next/image";
import { BookText, Lightbulb, ListChecks, Sparkles } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";

export default function Home() {
  return (
    <main className="mx-auto w-full max-w-3xl space-y-8 px-6 py-10">
      <section className="space-y-4 rounded-xl border border-border bg-gray-50 p-6 shadow-sm dark:bg-gray-950/40">
        <Image
          src="/study-snap-logo-full.svg"
          alt="Study Snap"
          width={220}
          height={44}
          priority
        />
        <div className="inline-flex items-center gap-2 rounded-lg border border-border bg-background px-3 py-1 text-sm text-foreground/80">
          <Sparkles className="h-4 w-4 text-emerald-500" />
          Calm, structured study support
        </div>
        <h1 className="text-3xl font-semibold tracking-tight text-foreground md:text-4xl">
          Turn messy notes into a clear study pack in seconds.
        </h1>
        <p className="text-base leading-relaxed text-foreground/75">
          Paste your notes or upload a photo.
          <br />
          Study Snap creates:
          <br />
          • a concise summary
          <br />
          • key concepts
          <br />
          • a practice quiz
          <br />
          in seconds.
        </p>
        <p className="text-sm text-foreground/65">
          Built for students, exam preparation, and interview review.
        </p>
        <Link
          href="/study?demo=true"
          className={buttonVariants({ className: "w-fit" })}
        >
          Try Demo
        </Link>
      </section>

      <section className="space-y-4">
        <h2 className="text-2xl font-semibold text-foreground">Features</h2>
        <div className="grid gap-4 md:grid-cols-3">
          <Card>
            <BookText className="mb-3 h-5 w-5 text-blue-600 dark:text-blue-400" />
            <CardTitle className="mb-2">Summary</CardTitle>
            <CardDescription>
              Get a clean, high-signal summary of your notes before you dive
              into details.
            </CardDescription>
          </Card>
          <Card>
            <Lightbulb className="mb-3 h-5 w-5 text-blue-600 dark:text-blue-400" />
            <CardTitle className="mb-2">Key concepts</CardTitle>
            <CardDescription>
              Highlight the most important ideas, terms, and relationships to
              focus your review.
            </CardDescription>
          </Card>
          <Card>
            <ListChecks className="mb-3 h-5 w-5 text-blue-600 dark:text-blue-400" />
            <CardTitle className="mb-2">Practice quiz</CardTitle>
            <CardDescription>
              Check understanding with a short quiz generated from your study
              material.
            </CardDescription>
          </Card>
        </div>
      </section>

      <section className="space-y-4">
        <h2 className="text-2xl font-semibold text-foreground">How it works</h2>
        <Card className="space-y-4">
          <div>
            <p className="text-sm font-semibold text-blue-600 dark:text-blue-400">
              Step 1
            </p>
            <p className="text-base text-foreground/80">
              Paste notes or upload an image from your notebook or slides.
            </p>
          </div>
          <div>
            <p className="text-sm font-semibold text-blue-600 dark:text-blue-400">
              Step 2
            </p>
            <p className="text-base text-foreground/80">
              Study Snap extracts the content and builds your review set.
            </p>
          </div>
          <div>
            <p className="text-sm font-semibold text-blue-600 dark:text-blue-400">
              Step 3
            </p>
            <p className="text-base text-foreground/80">
              Review the summary, concepts, and quiz at your own pace.
            </p>
          </div>
        </Card>
      </section>

      <footer className="border-t border-border pt-6 text-sm text-foreground/65">
        Study Snap MVP. Let&apos;s work through this step by step.
      </footer>
    </main>
  );
}
