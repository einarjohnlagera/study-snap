import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import type { LearnGuide } from "@/lib/learn-guides";

type LearnArticleLayoutProps = {
  guide: LearnGuide;
};

export function LearnArticleLayout({ guide }: Readonly<LearnArticleLayoutProps>) {
  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-4 py-8 sm:px-6 sm:py-12">
      <Link
        href="/learn"
        className="inline-flex w-fit items-center gap-2 text-sm font-medium text-foreground/80 transition-colors hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to Learn
      </Link>

      <article className="space-y-8 rounded-[2rem] border border-border bg-background p-6 shadow-sm sm:p-8">
        <header className="space-y-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Learn
          </p>
          <h1 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
            {guide.title}
          </h1>
          <p className="max-w-3xl text-base leading-relaxed text-foreground/75">
            {guide.intro}
          </p>
        </header>

        <div className="space-y-8">
          {guide.sections.map((section) => (
            <section key={section.heading} className="space-y-3">
              <h2 className="text-xl font-semibold text-foreground sm:text-2xl">
                {section.heading}
              </h2>
              <div className="space-y-3 text-base leading-relaxed text-foreground/80">
                {section.paragraphs.map((paragraph) => (
                  <p key={paragraph}>{paragraph}</p>
                ))}
              </div>
            </section>
          ))}
        </div>
      </article>

      <Card className="space-y-4 p-6 sm:p-8">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Try NoteLib
          </p>
          <CardTitle>Turn your notes into a study workflow you can repeat.</CardTitle>
          <CardDescription>
            Create a note, generate a Study Pack, review weak areas, and keep practicing.
          </CardDescription>
        </div>
        <div>
          <Link href="/signup" className={buttonVariants({ className: "w-full sm:w-auto" })}>
            Try NoteLib for Free
          </Link>
        </div>
      </Card>
    </main>
  );
}
