import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { AnalyticsPageViewTracker } from "@/components/analytics/page-view-tracker";
import { ExamHubCta } from "@/components/exam-hub/exam-hub-cta";
import { ExamHubOfficialReviewSets } from "@/components/exam-hub/exam-hub-official-review-sets";
import { PublicLibraryReturnLink } from "@/components/notes/public-library-return-link";
import { SharedNoteCard } from "@/components/notes/shared-note-card";
import { StructuredDataScript } from "@/components/seo/structured-data-script";
import { Card } from "@/components/ui/card";
import type { NoteListItemResponse } from "@/lib/api";
import { EXAM_HUB_SLUGS, getExamHubConfig, type ExamHubConfig } from "@/lib/exam-hub-config";
import { buildLearnCategoryPath } from "@/lib/learn-guides";
import {
  type BrowseSubjectEntry,
  DISCOVERY_SECTION_LIMIT,
  excludeById,
  getBrowseSubjects,
  getFeaturedNotes,
  getPopularNotes,
  getRecentNotes,
} from "@/lib/public-library-discovery";
import { buildPublicLibraryUrl, slugifyPublicLibraryFilterValue } from "@/lib/public-library-url";
import {
  buildPublicLibraryNotePathFromSlug,
  buildPublicLibrarySubjectPath,
  getPublicTitleSlug,
} from "@/lib/public-note-path";
import { getServerPublicNotesByCoursePrograms } from "@/lib/server-public-notes";
import { getServerPublicStudyPlansByCoursePrograms } from "@/lib/server-public-study-plans";
import { getServerExamGoalCoursePrograms } from "@/lib/server-exam-goal-course-programs";
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
  const shownIds = new Set([...featured, ...popular, ...recent].map((note) => note.id));
  const remaining = getRecentNotes(excludeById(notes, shownIds), notes.length) as ExamHubNote[];

  return { featured, popular, recent, remaining };
}

function buildNoteCanonicalUrl(note: ExamHubNote) {
  return buildPublicLibraryNotePathFromSlug({
    subject: note.subject,
    slug: getNoteSlug(note),
  });
}

function ExamNoteCard({ note }: Readonly<{ note: ExamHubNote }>) {
  const href = buildNoteCanonicalUrl(note);
  const returnUrl = buildPublicLibraryUrl({ courseProgram: slugifyPublicLibraryFilterValue(note.courseProgram) });

  return (
    <PublicLibraryReturnLink href={href} returnUrl={returnUrl} className="block h-full rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 focus-visible:ring-offset-background">
      <Card className="motion-pressable h-full p-4 transition-colors hover:border-blue-500/50 hover:bg-blue-500/5 sm:p-5">
        <SharedNoteCard
          title={note.title}
          courseProgram={note.courseProgram}
          applicablePrograms={note.applicablePrograms}
          subject={note.subject}
          tags={note.tags}
          contentPreview={note.contentPreview}
          summaryPreview={note.summaryPreview}
          copyCount={typeof note.copyCount === "number" && note.copyCount > 0 ? note.copyCount : null}
          viewCount={typeof note.viewCount === "number" && note.viewCount > 0 ? note.viewCount : null}
          tagDisplayLimit={3}
          footer={(
            <div className="flex flex-wrap items-center gap-2 text-xs text-foreground/60">
              <span>By {note.authorDisplayName || "NoteLib learner"}</span>
              {note.isOfficialAuthor ? (
                <span className="rounded-full border border-blue-500/35 bg-blue-500/10 px-2 py-0.5 text-[11px] font-medium text-blue-700 dark:text-blue-300">
                  Official
                </span>
              ) : null}
            </div>
          )}
        />
      </Card>
    </PublicLibraryReturnLink>
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

function BrowseSubjectsSection({ subjects }: Readonly<{ subjects: BrowseSubjectEntry[] }>) {
  if (subjects.length === 0) {
    return null;
  }

  return (
    <section className="space-y-3" aria-labelledby="browse-by-subject-heading">
      <div className="space-y-1">
        <h2 id="browse-by-subject-heading" className="text-xl font-semibold tracking-tight">
          Browse by Subject
        </h2>
        <p className="text-sm text-foreground/65">Jump straight to notes for a specific subject.</p>
      </div>
      <div className="flex flex-wrap gap-2">
        {subjects.map(({ subject, count }) => (
          <Link
            key={subject}
            href={buildPublicLibrarySubjectPath(subject)}
            className="inline-flex items-center gap-1.5 rounded-full border border-border bg-background/70 px-3 py-1.5 text-sm font-medium text-foreground/80 transition-colors hover:border-blue-500/50 hover:bg-blue-500/5 hover:text-blue-700 dark:hover:text-blue-300"
          >
            {subject}
            <span className="text-xs text-foreground/50">({count})</span>
          </Link>
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
    title: `Free ${exam.shortName} Reviewer Notes & Practice Quizzes — ${exam.fullName} | NoteLib`,
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

  const coursePrograms = await getServerExamGoalCoursePrograms(exam.slug);
  const resolvedExam = { ...exam, coursePrograms };
  const [notes, officialReviewSets] = await Promise.all([
    getServerPublicNotesByCoursePrograms(coursePrograms) as Promise<ExamHubNote[]>,
    getServerPublicStudyPlansByCoursePrograms(coursePrograms),
  ]);
  const description = buildExamMetadataDescription(exam);
  const examPath = buildExamPath(exam.slug);
  const { featured, popular, recent, remaining } = getSectionedNotes(notes);
  const hasAnyNotes = featured.length > 0 || popular.length > 0 || recent.length > 0;
  const browseSubjects = getBrowseSubjects(notes);

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
          items: notes.map((note) => ({
            name: note.title?.trim() || "Untitled note",
            url: absoluteUrl(buildNoteCanonicalUrl(note)),
          })),
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
              {coursePrograms.map((courseProgram) => (
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
              <ExamHubCta exam={resolvedExam} />
            </div>
          </div>
        </div>
      </header>

      <p className="text-sm leading-relaxed text-foreground/70">
        NoteLib turns {exam.shortName} notes into a free, interactive reviewer — summaries, key concepts, and quiz
        practice — so you can track the weak areas that need review before exam day, not just read through material
        once.
      </p>

      {officialReviewSets.length > 0 ? (
        <ExamHubOfficialReviewSets exam={resolvedExam} plans={officialReviewSets} />
      ) : null}

      {hasAnyNotes ? (
        <div className="space-y-8">
          <BrowseSubjectsSection subjects={browseSubjects} />
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
          <ExamSection
            title={`More ${exam.shortName} Notes`}
            description={`Every other public ${exam.shortName} note, so nothing stays hidden — ${notes.length} in total across this hub.`}
            notes={remaining}
          />
        </div>
      ) : (
        <Card className="space-y-3 p-6 text-center sm:p-8">
          <h2 className="text-lg font-semibold">No {exam.fullName} notes have been shared yet.</h2>
          <p className="text-sm text-foreground/65">
            Start preparing with your own notes, or explore related study notes, summaries, and quizzes.
          </p>
          <div className="flex flex-col items-center justify-center gap-3 sm:flex-row">
            <ExamHubCta exam={resolvedExam} />
            {/* Stage 1: retargeted from /public/library to /explore in v0.84.0, once /explore became
                anonymous. A signed-out visitor reaching this empty state is exactly the audience
                Explore was opened for; sending them to one source of the Discovery System instead of
                its front door was the older behaviour. /public/library keeps its canonical route. */}
            <Link href="/explore" className="inline-flex min-h-11 items-center text-sm font-medium text-blue-700 transition-colors hover:text-blue-800 hover:underline dark:text-blue-300 dark:hover:text-blue-200">
              Explore notes and study plans
            </Link>
            <Link href={buildLearnCategoryPath("board-exams")} className="inline-flex min-h-11 items-center text-sm font-medium text-blue-700 transition-colors hover:text-blue-800 hover:underline dark:text-blue-300 dark:hover:text-blue-200">
              Read Board Exam study guides
            </Link>
          </div>
        </Card>
      )}
    </main>
  );
}
