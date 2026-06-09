import type { Metadata } from "next";
import Link from "next/link";
import { ArrowRight, GraduationCap, Heart, PenTool } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { PublicFooter } from "@/components/public/public-footer";
import { BackLink } from "@/components/ui/back-link";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { EXAM_HUB_SLUGS, EXAM_HUBS } from "@/lib/exam-hub-config";
import type { ExamHubSlug } from "@/lib/exam-hub-config";
import { buildPageMetadata } from "@/lib/site-metadata";

const EXAM_HUB_ICONS: Record<ExamHubSlug, LucideIcon> = {
  ale: PenTool,
  pnle: Heart,
  let: GraduationCap,
};

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
        <BackLink href="/public/library" label="Public Library" />

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
            const Icon = EXAM_HUB_ICONS[slug];
            return (
              <Link key={exam.slug} href={`/exam/${exam.slug}`} className="group block h-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 focus-visible:ring-offset-background">
                <Card className="flex h-full flex-col gap-4 p-5 transition-colors hover:bg-highlight sm:p-6">
                  <div className="flex items-start gap-3">
                    <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-border bg-muted/40">
                      <Icon className="h-4 w-4 text-foreground/70" aria-hidden="true" />
                    </span>
                    <div className="min-w-0 space-y-1">
                      <CardTitle className="text-sm">{exam.fullName}</CardTitle>
                      <CardDescription className="text-xs leading-relaxed">{exam.description}</CardDescription>
                    </div>
                  </div>
                  <div className="mt-auto flex items-center gap-1 text-xs font-medium text-blue-600 dark:text-blue-400">
                    Browse {exam.shortName} notes
                    <ArrowRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
                  </div>
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
