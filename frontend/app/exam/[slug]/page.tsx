import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { AnalyticsPageViewTracker } from "@/components/analytics/page-view-tracker";
import { ExamHubCta } from "@/components/exam-hub/exam-hub-cta";
import { StructuredDataScript } from "@/components/seo/structured-data-script";
import { Card } from "@/components/ui/card";
import type { NoteListItemResponse } from "@/lib/api";
import { EXAM_HUB_SLUGS, getExamHubConfig, type ExamHubConfig } from "@/lib/exam-hub-config";
import {
  DISCOVERY_SECTION_LIMIT,
  excludeById,
  getFeaturedNotes,
  getPopularNotes,
  getRecentNotes,
} from "@/lib/public-library-discovery";
import { buildPublicLibraryNotePathFromSlug, getPublicTitleSlug } from "@/lib/public-note-path";
import { stripMarkdownForPreview } from "@/lib/public-note-text";
import { getServerPublicNotesByCoursePrograms } from "@/lib/server-public-notes";
import { absoluteUrl, buildPageMetadata } from "@/lib/site-metadata";
import { buildCollectionPageStructuredData } from "@/lib/structured-data";

export const revalidate = 300;

type ExamHubPageProps = {
  params: Promise<{
    slug: string;
  }>;
};

type ExamHubNote = NoteListItemResponse & {
  slug?: string | null;
};

type ExamSectionProps = {
  title: string;
  description: string;
  notes: ExamHubNote[];
};

function buildExamPath(slug: string) {
  return `/exam/${slug}`;
}

function buildExamMetadataDescription(exam: ExamHubConfig) {
  return `${exam.description}. Browse curated public notes, summaries, and quiz-ready study packs from NoteLib.`;
}

function getNoteSlug(note: ExamHubNote) {
  return note.slug?.trim() || getPublicTitleSlug(note.title);
}

function getSectionedNotes(notes: ExamHubNote[]) {
  const featured = getFeaturedNotes(notes, DISCOVERY_SECTION_LIMIT) as ExamHubNote[];
  const featuredIds = new Set(featured.map((note) => note.id));
  const popular = getPopularNotes(excludeById(notes, featuredIds), DISCOVERY_SECTION_LIMIT) as ExamHubNote[];
  const visibleIds = new Set([...featured, ...popular].map((note) => note.id));
  const recent = getRecentNotes(excludeById(notes, visibleIds), DISCOVERY_SECTION_LIMIT) as ExamHubNote[];

  return { featured, popular, recent };
}

function ExamNoteCard({ note }: Readonly<{ note: ExamHubNote }>) {
  const title = note.title?.trim() || "Untitled note";
  const summary = stripMarkdownForPreview(note.summaryPreview?.trim() || note.contentPreview?.trim()) || "No preview available yet.";
  const href = buildPublicLibraryNotePathFromSlug({
    subject: note.subject,
    slug: getNoteSlug(note),
  });
  const hasMetrics = typeof note.viewCount === "number" || typeof note.copyCount === "number";

  return (
    <Link href={href} className="block h-full rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 focus-visible:ring-offset-background">
      <Card className="flex h-full flex-col justify-between gap-4 p-4 transition-colors hover:border-blue-300/80 hover:bg-blue-50/35 dark:hover:border-blue-700/70 dark:hover:bg-blue-950/15 sm:p-5">
        <div className="space-y-3">
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-full border border-blue-500/25 bg-blue-500/10 px-2 py-1 text-xs font-medium text-blue-700 dark:text-blue-300">
              {note.subject?.trim() || "General"}
            </span>
            {note.courseProgram?.trim() ? (
              <span className="rounded-full border border-border bg-muted/50 px-2 py-1 text-xs font-medium text-foreground/70">
                {note.courseProgram.trim()}
              </span>
            ) : null}
          </div>
          <div className="space-y-2">
            <h2 className="line-clamp-2 text-base font-semibold text-foreground sm:text-lg">{title}</h2>
            <p className="line-clamp-3 text-sm leading-relaxed text-foreground/70">{summary}</p>
          </div>
        </div>
        <div className="space-y-3 text-xs text-foreground/60">
          <div className="flex flex-wrap items-center gap-2">
            <span>By {note.authorDisplayName || "NoteLib learner"}</span>
            {note.isOfficialAuthor ? (
              <span className="rounded-full border border-blue-500/35 bg-blue-500/10 px-2 py-0.5 text-[11px] font-medium text-blue-700 dark:text-blue-300">
                Official
              </span>
            ) : null}
          </div>
          {hasMetrics ? (
            <div className="flex flex-wrap gap-3">
              {typeof note.viewCount === "number" ? <span>{note.viewCount.toLocaleString()} {note.viewCount === 1 ? "view" : "views"}</span> : null}
              {typeof note.copyCount === "number" ? <span>{note.copyCount.toLocaleString()} {note.copyCount === 1 ? "copy" : "copies"}</span> : null}
            </div>
          ) : null}
        </div>
      </Card>
    </Link>
  );
}

function ExamSection({ title, description, notes }: Readonly<ExamSectionProps>) {
  if (notes.length === 0) {
    return null;
  }

  const headingId = `${title.toLowerCase().replaceAll(/[^a-z0-9]+/g, "-")}-heading`;

  return (
    <section className="space-y-3" aria-labelledby={headingId}>
      <div className="space-y-1">
        <h2 id={headingId} className="text-xl font-semibold tracking-tight">
          {title}
        </h2>
        <p className="text-sm text-foreground/65">{description}</p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {notes.map((note) => (
          <ExamNoteCard key={note.id} note={note} />
        ))}
      </div>
    </section>
  );
}

export function generateStaticParams() {
  return EXAM_HUB_SLUGS.map((slug) => ({ slug }));
}

export async function generateMetadata({ params }: ExamHubPageProps): Promise<Metadata> {
  const { slug } = await params;
  const exam = getExamHubConfig(slug);
  if (!exam) {
    return {};
  }

  return buildPageMetadata({
    title: `${exam.fullName} Notes and Practice Quizzes | NoteLib`,
    description: buildExamMetadataDescription(exam),
    path: buildExamPath(exam.slug),
  });
}

export default async function ExamHubPage({ params }: Readonly<ExamHubPageProps>) {
  const { slug } = await params;
  const exam = getExamHubConfig(slug);
  if (!exam) {
    notFound();
  }

  const notes = await getServerPublicNotesByCoursePrograms(exam.coursePrograms) as ExamHubNote[];
  const description = buildExamMetadataDescription(exam);
  const examPath = buildExamPath(exam.slug);
  const { featured, popular, recent } = getSectionedNotes(notes);
  const hasAnyNotes = featured.length > 0 || popular.length > 0 || recent.length > 0;

  return (
    <main className="mx-auto w-full max-w-6xl space-y-8 px-4 py-6 sm:px-6 sm:py-10">
      <AnalyticsPageViewTracker
        eventType="EXAM_HUB_VIEWED"
        metadata={{ slug: exam.slug }}
      />
      <StructuredDataScript
        id="exam-hub-structured-data"
        data={buildCollectionPageStructuredData({
          name: `${exam.fullName} – NoteLib Exam Hub`,
          url: absoluteUrl(examPath),
          description,
        })}
      />

      <Link href="/exam" className="inline-flex text-sm font-medium text-blue-700 transition-colors hover:text-blue-800 hover:underline dark:text-blue-300 dark:hover:text-blue-200">
        ← Exam Hubs
      </Link>

      <header className="overflow-hidden rounded-3xl border border-blue-500/20 bg-linear-to-br from-sky-500/10 via-background to-emerald-500/10 p-6 shadow-sm sm:p-8">
        <div className="grid gap-6 lg:grid-cols-[1fr_auto] lg:items-center">
          <div className="space-y-4">
            <div className="space-y-2">
              <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">Board Exam Hub</p>
              <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">{exam.fullName}</h1>
              <p className="max-w-2xl text-sm leading-relaxed text-foreground/70 sm:text-base">
                {exam.description}. Browse public notes, summaries, and practice-ready study packs for your review.
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              {exam.coursePrograms.map((courseProgram) => (
                <span key={courseProgram} className="rounded-full border border-border bg-background/70 px-3 py-1 text-xs font-medium text-foreground/75">
                  {courseProgram}
                </span>
              ))}
            </div>
          </div>
          <div className="rounded-2xl border border-border bg-background/80 p-4 shadow-sm lg:w-72">
            <p className="text-sm font-medium text-foreground">Turn these notes into practice.</p>
            <p className="mt-1 text-xs leading-relaxed text-foreground/65">
              Sign up to save notes, generate Study Packs, and start quiz practice from this exam set.
            </p>
            <div className="mt-4">
              <ExamHubCta exam={exam} />
            </div>
          </div>
        </div>
      </header>

      {hasAnyNotes ? (
        <div className="space-y-8">
          <ExamSection
            title="Featured Notes"
            description={`High-signal ${exam.shortName} notes ranked by engagement and freshness.`}
            notes={featured}
          />
          <ExamSection
            title="Most Popular"
            description="Public notes with the strongest copy and view signals from other learners."
            notes={popular}
          />
          <ExamSection
            title="Recently Added"
            description={`Newest public notes for ${exam.shortName} reviewers.`}
            notes={recent}
          />
        </div>
      ) : (
        <Card className="space-y-3 p-6 text-center sm:p-8">
          <h2 className="text-lg font-semibold">No {exam.fullName} notes have been shared yet.</h2>
          <p className="text-sm text-foreground/65">
            Start preparing with your own notes, or browse the full Public Library for related study notes, summaries, and quizzes.
          </p>
          <div className="flex flex-col items-center justify-center gap-3 sm:flex-row">
            <ExamHubCta exam={exam} />
            <Link href="/public/library" className="inline-flex min-h-11 items-center text-sm font-medium text-blue-700 transition-colors hover:text-blue-800 hover:underline dark:text-blue-300 dark:hover:text-blue-200">
              Browse the Public Library
            </Link>
          </div>
        </Card>
      )}
    </main>
  );
}
