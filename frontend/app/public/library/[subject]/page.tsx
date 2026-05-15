import type { Metadata } from "next";
import Link from "next/link";
import { StructuredDataScript } from "@/components/seo/structured-data-script";
import { Card } from "@/components/ui/card";
import type { NoteListItemResponse } from "@/lib/api";
import {
  DISCOVERY_SECTION_LIMIT,
  excludeById,
  getFeaturedNotes,
  getPopularNotes,
  getRecentNotes,
} from "@/lib/public-library-discovery";
import {
  buildPublicLibraryNotePathFromSlug,
  buildPublicLibrarySubjectPath,
  getPublicTitleSlug,
} from "@/lib/public-note-path";
import { getServerPublicNotesBySubjectSlug, getServerPublicSubjects } from "@/lib/server-public-notes";
import { absoluteUrl, buildPageMetadata } from "@/lib/site-metadata";
import { buildCollectionPageStructuredData } from "@/lib/structured-data";

export const revalidate = 300;

type SubjectLandingPageProps = {
  params: Promise<{
    subject: string;
  }>;
};

type SubjectNote = NoteListItemResponse & {
  slug?: string | null;
};

type SubjectSectionProps = {
  title: string;
  description: string;
  notes: SubjectNote[];
};

function formatSubjectFallback(subjectSlug: string) {
  const fallback = subjectSlug.replaceAll("-", " ").trim() || "general";
  return fallback.replaceAll(/\b\w/g, (letter) => letter.toUpperCase());
}

function resolveSubjectLabel(subjectSlug: string, notes: SubjectNote[]) {
  return notes.find((note) => note.subject?.trim())?.subject?.trim() || formatSubjectFallback(subjectSlug);
}

function buildSubjectDescription(subjectLabel: string) {
  return `Explore public study notes and practice quizzes for ${subjectLabel}. Browse community-contributed notes, summaries, and quiz sets.`;
}

function getNoteSlug(note: SubjectNote) {
  return note.slug?.trim() || getPublicTitleSlug(note.title);
}

function getSectionedNotes(notes: SubjectNote[]) {
  const featured = getFeaturedNotes(notes, DISCOVERY_SECTION_LIMIT) as SubjectNote[];
  const featuredIds = new Set(featured.map((note) => note.id));
  const popular = getPopularNotes(excludeById(notes, featuredIds), DISCOVERY_SECTION_LIMIT) as SubjectNote[];
  const visibleIds = new Set([...featured, ...popular].map((note) => note.id));
  const recent = getRecentNotes(excludeById(notes, visibleIds), DISCOVERY_SECTION_LIMIT) as SubjectNote[];

  return { featured, popular, recent };
}

function SubjectNoteCard({ note }: Readonly<{ note: SubjectNote }>) {
  const title = note.title?.trim() || "Untitled note";
  const summary = note.summaryPreview?.trim() || note.contentPreview?.trim() || "No preview available yet.";
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

function SubjectSection({ title, description, notes }: Readonly<SubjectSectionProps>) {
  if (notes.length === 0) {
    return null;
  }

  return (
    <section className="space-y-3" aria-labelledby={`${title.toLowerCase().replaceAll(/[^a-z0-9]+/g, "-")}-heading`}>
      <div className="space-y-1">
        <h2 id={`${title.toLowerCase().replaceAll(/[^a-z0-9]+/g, "-")}-heading`} className="text-xl font-semibold tracking-tight">
          {title}
        </h2>
        <p className="text-sm text-foreground/65">{description}</p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {notes.map((note) => (
          <SubjectNoteCard key={note.id} note={note} />
        ))}
      </div>
    </section>
  );
}

export async function generateStaticParams() {
  try {
    const subjects = await getServerPublicSubjects();
    return subjects.map((entry) => ({ subject: entry.slug }));
  } catch {
    // Backend unreachable at build time (e.g. Vercel build); ISR will generate pages on first request.
    return [];
  }
}

export async function generateMetadata({ params }: SubjectLandingPageProps): Promise<Metadata> {
  const { subject } = await params;
  const notes = await getServerPublicNotesBySubjectSlug(subject) as SubjectNote[];
  const subjectLabel = resolveSubjectLabel(subject, notes);
  const description = buildSubjectDescription(subjectLabel);

  return buildPageMetadata({
    title: `NoteLib – ${subjectLabel} Study Notes, Summaries, and Quizzes`,
    description,
    path: buildPublicLibrarySubjectPath(subject),
  });
}

export default async function SubjectLandingPage({ params }: Readonly<SubjectLandingPageProps>) {
  const { subject } = await params;
  const notes = await getServerPublicNotesBySubjectSlug(subject) as SubjectNote[];
  const subjectLabel = resolveSubjectLabel(subject, notes);
  const description = buildSubjectDescription(subjectLabel);
  const subjectPath = buildPublicLibrarySubjectPath(subject);
  const { featured, popular, recent } = getSectionedNotes(notes);
  const hasAnyNotes = featured.length > 0 || popular.length > 0 || recent.length > 0;

  return (
    <main className="mx-auto w-full max-w-6xl space-y-8 px-4 py-6 sm:px-6 sm:py-10">
      <StructuredDataScript
        id="public-library-subject-structured-data"
        data={buildCollectionPageStructuredData({
          name: `${subjectLabel} – NoteLib Public Library`,
          url: absoluteUrl(subjectPath),
          description,
        })}
      />

      <Link href="/public/library" className="inline-flex text-sm font-medium text-blue-700 transition-colors hover:text-blue-800 hover:underline dark:text-blue-300 dark:hover:text-blue-200">
        ← Public Library
      </Link>

      <header className="space-y-3 rounded-3xl border border-blue-500/20 bg-linear-to-br from-blue-500/10 via-background to-amber-500/10 p-6 shadow-sm sm:p-8">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">Subject Library</p>
        <div className="space-y-2">
          <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">{subjectLabel}</h1>
          <p className="max-w-2xl text-sm leading-relaxed text-foreground/70 sm:text-base">
            Explore community study notes and practice quizzes.
          </p>
        </div>
      </header>

      {hasAnyNotes ? (
        <div className="space-y-8">
          <SubjectSection
            title="Featured Notes"
            description="Quality public notes ranked by engagement and freshness."
            notes={featured}
          />
          <SubjectSection
            title="Most Popular"
            description="Subject notes with the strongest copy and view signals."
            notes={popular}
          />
          <SubjectSection
            title="Recently Added"
            description="Newest public notes for this subject."
            notes={recent}
          />
        </div>
      ) : (
        <Card className="space-y-3 p-6 text-center sm:p-8">
          <h2 className="text-lg font-semibold">No notes yet for this subject.</h2>
          <p className="text-sm text-foreground/65">
            Browse the full Public Library to find related study notes, summaries, and quizzes.
          </p>
          <Link href="/public/library" className="inline-flex text-sm font-medium text-blue-700 transition-colors hover:text-blue-800 hover:underline dark:text-blue-300 dark:hover:text-blue-200">
            ← Browse all subjects
          </Link>
        </Card>
      )}
    </main>
  );
}
