import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { BackLink } from "@/components/ui/back-link";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import type { LearnGuide } from "@/lib/learn-guides";

type LearnArticleLayoutProps = {
  guide: LearnGuide;
};

export function LearnArticleLayout({ guide }: Readonly<LearnArticleLayoutProps>) {
  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-4 py-8 sm:px-6 sm:py-12">
      <BackLink href="/learn" label="Learn" />

      <article className="space-y-8 rounded-[2rem] border border-border bg-background p-6 shadow-sm sm:p-8">
        <header className="space-y-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Learn
          </p>
          <h1 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
            {guide.title}
          </h1>
          <p className="max-w-3xl text-base leading-relaxed text-foreground/75">
            {guide.description}
          </p>
        </header>

        <div className="space-y-8">
          <section className="space-y-3">
            <h2 className="text-xl font-semibold text-foreground sm:text-2xl">
              Short Introduction
            </h2>
            <div className="space-y-3 text-base leading-relaxed text-foreground/80">
              <p>{guide.intro}</p>
            </div>
          </section>

          <section className="space-y-3">
            <h2 className="text-xl font-semibold text-foreground sm:text-2xl">
              Summary
            </h2>
            <div className="space-y-3 text-base leading-relaxed text-foreground/80">
              {guide.summary.map((paragraph) => (
                <p key={paragraph}>{paragraph}</p>
              ))}
            </div>
          </section>

          <section className="space-y-4">
            <h2 className="text-xl font-semibold text-foreground sm:text-2xl">
              Key Concepts
            </h2>
            <div className="grid gap-4 md:grid-cols-2">
              {guide.sections.map((section) => (
                <Card key={section.heading} className="space-y-3 p-5">
                  <CardTitle className="text-lg">{section.heading}</CardTitle>
                  <div className="space-y-3 text-sm leading-relaxed text-foreground/80">
                    {section.paragraphs.map((paragraph) => (
                      <p key={paragraph}>{paragraph}</p>
                    ))}
                  </div>
                </Card>
              ))}
            </div>
          </section>

          <section className="space-y-4">
            <h2 className="text-xl font-semibold text-foreground sm:text-2xl">
              Practice Questions
            </h2>
            <p className="text-sm leading-relaxed text-foreground/70 sm:text-base">
              Use these sample prompts to test yourself before you move on to the next lesson or review session.
            </p>
            <div className="space-y-4">
              {guide.practiceQuestions.map((practiceQuestion, index) => (
                <Card key={practiceQuestion.question} className="space-y-3 p-5">
                  <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
                    Question {index + 1}
                  </p>
                  <CardTitle className="text-lg">{practiceQuestion.question}</CardTitle>
                  <div className="space-y-2 text-sm leading-relaxed text-foreground/80">
                    <p className="font-medium text-foreground">Sample answer</p>
                    <p>{practiceQuestion.answer}</p>
                  </div>
                </Card>
              ))}
            </div>
          </section>
        </div>
      </article>

      <Card className="space-y-4 p-6 sm:p-8">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Try NoteLib
          </p>
          <CardTitle>Want to turn your own notes into summaries and quizzes?</CardTitle>
          <CardDescription>Try NoteLib for free.</CardDescription>
        </div>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <p className="inline-flex items-center gap-2 text-sm font-medium text-foreground/75">
            Try NoteLib for free
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </p>
          <Link href="/signup" className={buttonVariants({ className: "w-full sm:w-auto" })}>
            Create Free Account
          </Link>
        </div>
      </Card>
    </main>
  );
}
