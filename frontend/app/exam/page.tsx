import type { Metadata } from "next";
import Link from "next/link";
import { PublicFooter } from "@/components/public/public-footer";
import { Card } from "@/components/ui/card";
import { EXAM_HUB_SLUGS, EXAM_HUBS } from "@/lib/exam-hub-config";
import { buildPageMetadata } from "@/lib/site-metadata";

const examIndexDescription = "Browse NoteLib exam hubs for ALE, PNLE, and LET public notes, summaries, and practice quizzes.";

export const metadata: Metadata = buildPageMetadata({
  title: "NoteLib Exam Hubs – ALE, PNLE, and LET Review Notes",
  description: examIndexDescription,
  path: "/exam",
});

export default function ExamHubIndexPage() {
  return (
    <main className="mx-auto flex min-h-screen w-full max-w-6xl flex-col px-4 py-6 sm:px-6 sm:py-10">
      <div className="flex-1 space-y-8">
        <Link href="/public/library" className="inline-flex text-sm font-medium text-blue-700 transition-colors hover:text-blue-800 hover:underline dark:text-blue-300 dark:hover:text-blue-200">
          ← Public Library
        </Link>

        <header className="space-y-3 rounded-3xl border border-blue-500/20 bg-linear-to-br from-blue-500/10 via-background to-emerald-500/10 p-6 shadow-sm sm:p-8">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">Exam Hubs</p>
          <div className="space-y-2">
            <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">Board exam review notes</h1>
            <p className="max-w-2xl text-sm leading-relaxed text-foreground/70 sm:text-base">
              Start with curated public notes for the board exam communities NoteLib supports in this launch wave.
            </p>
          </div>
        </header>

        <section className="grid gap-4 md:grid-cols-3" aria-label="Wave 1 exam hubs">
          {EXAM_HUB_SLUGS.map((slug) => {
            const exam = EXAM_HUBS[slug];
            return (
              <Link key={exam.slug} href={`/exam/${exam.slug}`} className="block h-full rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 focus-visible:ring-offset-background">
                <Card className="flex h-full flex-col justify-between gap-5 p-5 transition-colors hover:border-blue-300/80 hover:bg-blue-50/35 dark:hover:border-blue-700/70 dark:hover:bg-blue-950/15">
                  <div className="space-y-3">
                    <span className="inline-flex rounded-full border border-blue-500/25 bg-blue-500/10 px-3 py-1 text-xs font-semibold text-blue-700 dark:text-blue-300">
                      {exam.shortName}
                    </span>
                    <div className="space-y-2">
                      <h2 className="text-lg font-semibold tracking-tight">{exam.fullName}</h2>
                      <p className="text-sm leading-relaxed text-foreground/70">{exam.description}</p>
                    </div>
                  </div>
                  <span className="text-sm font-medium text-blue-700 dark:text-blue-300">Browse {exam.shortName} notes →</span>
                </Card>
              </Link>
            );
          })}
        </section>
      </div>

      <PublicFooter className="mt-10" />
    </main>
  );
}
