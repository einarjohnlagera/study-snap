import Link from "next/link";
import { BookOpen, Camera, ListChecks, Sparkles } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";

export default function Home() {
  return (
    <main className="mx-auto w-full max-w-3xl space-y-8 px-6 py-10">
      <section className="space-y-4 rounded-xl border border-border bg-gray-50 p-6 shadow-sm dark:bg-gray-950/40">
        <div className="inline-flex items-center gap-2 rounded-lg border border-border bg-background px-3 py-1 text-sm text-foreground/80">
          <Sparkles className="h-4 w-4 text-emerald-500" />
          Calm, step-by-step tutoring
        </div>
        <h1 className="text-3xl font-semibold tracking-tight text-foreground md:text-4xl">
          Study Snap helps you understand questions, one clear step at a time.
        </h1>
        <p className="text-base leading-relaxed text-foreground/75">
          Paste a problem or upload a photo of your notes. We will restate the
          question, walk through the reasoning, and give a final answer you can
          trust.
        </p>
        <Link href="/solve" className={buttonVariants({ className: "w-fit" })}>
          Try a Question
        </Link>
      </section>

      <section className="space-y-4">
        <h2 className="text-2xl font-semibold text-foreground">Features</h2>
        <div className="grid gap-4 md:grid-cols-3">
          <Card>
            <BookOpen className="mb-3 h-5 w-5 text-blue-600 dark:text-blue-400" />
            <CardTitle className="mb-2">Clear explanations</CardTitle>
            <CardDescription>
              Get structured steps that explain the method, not just the result.
            </CardDescription>
          </Card>
          <Card>
            <Camera className="mb-3 h-5 w-5 text-blue-600 dark:text-blue-400" />
            <CardTitle className="mb-2">Image support</CardTitle>
            <CardDescription>
              Upload notes or worksheets and let OCR pull text before solving.
            </CardDescription>
          </Card>
          <Card>
            <ListChecks className="mb-3 h-5 w-5 text-blue-600 dark:text-blue-400" />
            <CardTitle className="mb-2">Final answer + steps</CardTitle>
            <CardDescription>
              See the restated question, numbered steps, and a final answer in
              one response.
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
              Paste text or upload an image of your question.
            </p>
          </div>
          <div>
            <p className="text-sm font-semibold text-blue-600 dark:text-blue-400">
              Step 2
            </p>
            <p className="text-base text-foreground/80">
              Study Snap analyzes the content and builds a clear explanation.
            </p>
          </div>
          <div>
            <p className="text-sm font-semibold text-blue-600 dark:text-blue-400">
              Step 3
            </p>
            <p className="text-base text-foreground/80">
              Review each step and the final answer at your own pace.
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
