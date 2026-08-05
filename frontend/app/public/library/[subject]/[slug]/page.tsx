import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { HashScrollListener } from "@/components/navigation/hash-scroll-listener";
import { PublicMiniQuizPreview } from "@/components/notes/public-mini-quiz-preview";
import { PublicFlashcardsPreview } from "@/components/notes/public-flashcards-preview";
import { PublicPracticeModeTeaser } from "@/components/notes/public-practice-mode-teaser";
import { PublicLibraryBackLink } from "@/components/notes/public-library-back-link";
import { PublicLibraryReturnLink } from "@/components/notes/public-library-return-link";
import { PublicNoteAuthorCard } from "@/components/notes/public-note-author-card";
import { PublicNoteAuthorLine, PublicNoteOwnershipActions } from "@/components/notes/public-note-ownership-actions";
import { PublicSeoCopyCta } from "@/components/notes/public-seo-copy-cta";
import { SharedNoteCard } from "@/components/notes/shared-note-card";
import { StructuredDataScript } from "@/components/seo/structured-data-script";
import { Card } from "@/components/ui/card";
import { SummaryMarkdown } from "@/components/ui/summary-markdown";
import { buildPublicLibraryNotePathFromDetail, buildPublicLibraryNotePath, buildPublicLibrarySubjectPath } from "@/lib/public-note-path";
import { buildPublicLibraryUrl, slugifyPublicLibraryFilterValue } from "@/lib/public-library-url";
import { buildPublicNoteHook, normalizePublicNoteText, splitPublicNoteBlocks } from "@/lib/public-note-text";
import { getServerPublicNoteBySeoPath, getServerPublicNotesBySubject, getServerPublicNotesBySubjectSlug, getServerPublicNotesByCourseProgram } from "@/lib/server-public-notes";
import { absoluteUrl, buildPageMetadata, truncateDescription } from "@/lib/site-metadata";
import { buildArticleStructuredData, buildBreadcrumbStructuredData } from "@/lib/structured-data";
import { EXAM_HUBS } from "@/lib/exam-hub-config";
import { getServerExamSlugForCourseProgram } from "@/lib/server-exam-goal-course-programs";

type PublicLibrarySeoPageProps = {
  params: Promise<{
    subject: string;
    slug: string;
  }>;
};

function buildDescription(title: string, summary?: string | null) {
  const normalizedSummary = normalizePublicNoteText(summary);
  if (normalizedSummary) {
    return truncateDescription(normalizedSummary, 160);
  }
  return `Study ${title} with summaries, key concepts, and practice questions on NoteLib.`;
}

export async function generateMetadata({ params }: PublicLibrarySeoPageProps): Promise<Metadata> {
  const { subject, slug } = await params;
  const note = await getServerPublicNoteBySeoPath(subject, slug);
  if (!note) {
    return {
      title: "Public Note Not Found | NoteLib",
      robots: { index: false, follow: false },
    };
  }

  const title = normalizePublicNoteText(note.title?.trim()) || "Untitled note";
  const description = buildDescription(title, note.summary);
  const path = buildPublicLibraryNotePathFromDetail(note);
  const url = absoluteUrl(path);
  const metadata = buildPageMetadata({
    title: `${title} | NoteLib`,
    description,
    path,
    type: "article",
  });

  return {
    ...metadata,
    openGraph: {
      ...metadata.openGraph,
      url,
      // Drop the static default so the per-note `opengraph-image.tsx` card wins.
      images: undefined,
    },
    twitter: {
      ...metadata.twitter,
      // No twitter-image file convention; Twitter falls back to the dynamic og:image.
      images: undefined,
    },
  };
}

export default async function PublicLibrarySeoPage({ params }: Readonly<PublicLibrarySeoPageProps>) {
  const { subject, slug } = await params;
  const note = await getServerPublicNoteBySeoPath(subject, slug);
  if (!note) {
    notFound();
  }

  const allSubjectNotes = await getServerPublicNotesBySubjectSlug(subject);
  const moreInSubject = note.subject?.trim()
    ? (await getServerPublicNotesBySubject(note.subject))
        .filter((relatedNote) => relatedNote.id !== note.id)
        .slice(0, 3)
    : [];
  const relatedNotes = allSubjectNotes
    .filter((n) => n.id !== note.id && n.studyPackStatus === "STUDY_PACK_READY")
    .sort((a, b) => {
      const scoreA = (a.viewCount ?? 0) + (a.copyCount ?? 0) * 3 + (a.likeCount ?? 0) * 2;
      const scoreB = (b.viewCount ?? 0) + (b.copyCount ?? 0) * 3 + (b.likeCount ?? 0) * 2;
      return scoreB - scoreA;
    })
    .slice(0, 3)
    .map((n) => ({ id: n.id, title: n.title, subject: n.subject, summaryPreview: n.summaryPreview, contentPreview: n.contentPreview }));

  const courseProgram = allSubjectNotes.find((n) => n.id === note.id)?.courseProgram ?? null;
  const examSlug = await getServerExamSlugForCourseProgram(courseProgram);
  const moreByCourseProgram = courseProgram
    ? (await getServerPublicNotesByCourseProgram(courseProgram))
        .filter((n) => n.id !== note.id && n.studyPackStatus === "STUDY_PACK_READY")
        .sort((a, b) => {
          const scoreA = (a.viewCount ?? 0) + (a.copyCount ?? 0) * 3 + (a.likeCount ?? 0) * 2;
          const scoreB = (b.viewCount ?? 0) + (b.copyCount ?? 0) * 3 + (b.likeCount ?? 0) * 2;
          return scoreB - scoreA;
        })
        .slice(0, 4)
    : [];

  const title = normalizePublicNoteText(note.title?.trim()) || "Untitled note";
  const subjectLabel = note.subject?.trim() || subject;
  const description = buildDescription(title, note.summary);
  const publicNotePath = buildPublicLibraryNotePathFromDetail(note);
  const canonicalUrl = absoluteUrl(publicNotePath);
  const subjectPath = buildPublicLibrarySubjectPath(subjectLabel);
  const fullNotesHref = `${publicNotePath}#full-notes`;
  const hook = buildPublicNoteHook({
    title,
    summary: note.summary,
    content: note.content,
  });
  const isDraft = note.studyPackStatus !== "STUDY_PACK_READY";
  const normalizedSummary = normalizePublicNoteText(note.summary);
  const keyConcepts = note.keyConcepts.map((concept) => normalizePublicNoteText(concept)).filter((concept) => concept.length > 0);
  const hasVisibleStudyPackPreviews = !isDraft && normalizedSummary.length > 0 && keyConcepts.length > 0 && note.quiz.length > 0;
  const fullContentBlocks = splitPublicNoteBlocks(note.content);
  const fullContent = fullContentBlocks.length > 0 ? fullContentBlocks : ["No content yet."];
  const tags = note.tags.map((tag) => normalizePublicNoteText(tag)).filter((tag) => tag.length > 0);

  return (
    <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <HashScrollListener allowedIds={["full-notes"]} />
      <StructuredDataScript
        id="public-note-structured-data"
        data={buildArticleStructuredData({
          title,
          description,
          canonicalUrl,
          authorName: note.authorDisplayName,
          updatedAt: note.updatedAt,
          tags: note.tags,
          subject: note.subject,
        })}
      />
      <StructuredDataScript
        id="public-note-breadcrumb-structured-data"
        data={buildBreadcrumbStructuredData([
          { name: "Home", url: absoluteUrl("/") },
          { name: "Public Library", url: absoluteUrl("/public/library") },
          { name: subjectLabel, url: absoluteUrl(subjectPath) },
          { name: title, url: canonicalUrl },
        ])}
      />
      <nav aria-label="Breadcrumb" className="overflow-x-auto text-sm text-foreground/65">
        <ol className="flex min-w-max items-center gap-2">
          <li><Link href="/" className="hover:text-blue-700 hover:underline dark:hover:text-blue-300">Home</Link></li>
          <li aria-hidden="true">/</li>
          <li><Link href="/public/library" className="hover:text-blue-700 hover:underline dark:hover:text-blue-300">Public Library</Link></li>
          <li aria-hidden="true">/</li>
          <li><Link href={subjectPath} className="hover:text-blue-700 hover:underline dark:hover:text-blue-300">{subjectLabel}</Link></li>
          <li aria-hidden="true">/</li>
          <li aria-current="page" className="max-w-56 truncate text-foreground/85 sm:max-w-md">{title}</li>
        </ol>
      </nav>
      <PublicLibraryBackLink />

      <article className="space-y-6">
        {/* Header — title, hook, tags, author */}
        <header className="rounded-3xl border border-blue-500/20 bg-linear-to-br from-blue-500/10 via-background to-amber-500/10 p-6 shadow-sm sm:p-8">
          <div className="space-y-4">
            <div className="flex flex-wrap items-center gap-2 text-xs font-semibold uppercase tracking-wide">
              <span className="rounded-full border border-blue-500/25 bg-blue-500/10 px-3 py-1 text-blue-700 dark:text-blue-300">
                Public Library
              </span>
              <span className="rounded-full border border-border bg-background px-3 py-1 text-foreground/70">
                Generated with NoteLib
              </span>
            </div>

            <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">{title}</h1>

            <p className="max-w-3xl text-sm leading-relaxed text-foreground/70 sm:text-base">{hook}</p>

            <PublicNoteAuthorLine
              ownerUserId={note.ownerUserId}
              authorDisplayName={note.authorDisplayName}
              authorUsername={note.authorUsername}
              isOfficialAuthor={note.isOfficialAuthor}
              isCurrentUser={note.isCurrentUser}
              subject={note.subject}
            />

            <div className="flex flex-wrap gap-2">
              {tags.length > 0 ? tags.map((tag) => (
                <Link
                  key={`${note.id}-${tag}`}
                  href={buildPublicLibraryUrl({ tags: [slugifyPublicLibraryFilterValue(tag)] })}
                  className="rounded-full border border-border bg-background px-3 py-1 text-xs text-foreground/75 transition-colors hover:border-blue-500/50 hover:bg-blue-500/5 hover:text-blue-700 dark:hover:text-blue-300"
                >
                  {tag}
                </Link>
              )) : (
                <span className="rounded-full border border-dashed border-border px-3 py-1 text-xs text-foreground/55">
                  No tags
                </span>
              )}
            </div>
          </div>
        </header>

        <PublicNoteAuthorCard
          ownerUserId={note.ownerUserId}
          authorDisplayName={note.authorDisplayName}
          authorUsername={note.authorUsername}
        />

        {hasVisibleStudyPackPreviews ? (
          <aside className="rounded-2xl border border-primary/20 bg-primary/5 px-4 py-3 text-sm leading-relaxed text-foreground/75">
            This summary, these key concepts, and this Quick Check were built from the note below. Your notes can get the same study tools.
          </aside>
        ) : null}

        {/* Summary — always visible */}
        <Card className="space-y-3 p-4 sm:p-6">
          <h2 className="text-lg font-semibold sm:text-xl">Summary</h2>
          <div className="max-w-3xl">
            {isDraft
              ? <p className="text-sm leading-relaxed text-foreground/80">This public note does not have a generated summary yet.</p>
              : <SummaryMarkdown content={normalizedSummary || "No summary available yet."} />}
          </div>
          {!isDraft && note.content?.trim() ? (
            <Link
              href={fullNotesHref}
              className="inline-flex w-fit text-sm font-medium text-blue-600 transition-colors hover:underline dark:text-blue-400"
            >
              View Full Notes →
            </Link>
          ) : null}
        </Card>

        {/* Key Concepts — always visible */}
        <Card className="space-y-3 p-4 sm:p-6">
          <h2 className="text-lg font-semibold sm:text-xl">🧠 Key Concepts</h2>
          {isDraft || keyConcepts.length === 0 ? (
            <p className="text-sm text-foreground/75">No key concepts generated yet.</p>
          ) : (
            <ul className="max-w-3xl space-y-2 pl-5 list-disc text-sm leading-relaxed text-foreground/85">
              {keyConcepts.map((concept) => (
                <li key={concept}>{concept}</li>
              ))}
            </ul>
          )}
        </Card>

        {/* Mini Quiz Preview */}
        {!isDraft && note.quiz.length > 0 ? (
          <PublicMiniQuizPreview
            quiz={note.quiz}
            noteId={note.id}
            relatedNotes={relatedNotes}
          />
        ) : null}

        {/* Flashcards Preview */}
        {!isDraft && note.quiz.length > 0 && keyConcepts.length > 0 ? (
          <PublicFlashcardsPreview
            keyConcepts={keyConcepts}
            quiz={note.quiz}
            noteId={note.id}
          />
        ) : null}

        {/* Soft conversion CTA — quiz-first; the note content above stays primary */}
        {/* Hidden for the owner: PublicNoteOwnershipActions below already sends them straight to their existing note. */}
        {!isDraft && note.quiz.length > 0 && !note.isCurrentUser ? (
          <Card className="space-y-3 border-primary/20 bg-primary/5 p-4 sm:p-6">
            <h2 className="text-base font-semibold sm:text-lg">Ready to quiz yourself?</h2>
            <p className="text-sm text-foreground/75">
              Test what you remember with a full practice quiz on this note. Create a free account and start in seconds.
            </p>
            <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
              <PublicSeoCopyCta
                noteId={note.id}
                label="Quiz yourself on this note"
                redirectTarget="quick-review"
                action="quickReview"
                analyticsEvent="PUBLIC_NOTE_QUIZ_YOURSELF_CLICKED"
                authModalTitle="Quiz yourself on this note"
                authModalBody="Create a free account or log in to quiz yourself on this note and keep practicing."
              />
            </div>
          </Card>
        ) : null}

        {/* Full Notes */}
        <section id="full-notes" aria-labelledby="full-notes-heading">
          <Card className="space-y-3 p-4 sm:p-6">
            <div className="space-y-1">
              <h2 id="full-notes-heading" className="text-lg font-semibold sm:text-xl">Full Notes</h2>
              <p className="text-sm text-foreground/65">
                Read the original note content before deciding whether to save or study from it.
              </p>
            </div>
            <div className="rounded-2xl border border-border bg-background px-4 py-4">
              <div className="max-w-3xl space-y-4 text-sm leading-7 text-foreground/85">
                {fullContent.map((block, index) => (
                  <p key={`${note.id}-full-notes-${index}`} className="whitespace-pre-wrap">
                    {block}
                  </p>
                ))}
              </div>
            </div>
          </Card>
        </section>

        {/* Practice mode teaser — before ownership actions */}
        {!isDraft ? <PublicPracticeModeTeaser /> : null}

        {/* Exam hub contextual callout — only when this note's courseProgram maps to a hub */}
        {examSlug ? (
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-blue-500/20 bg-blue-500/5 px-4 py-3">
            <p className="text-sm text-foreground/80">
              Preparing for the <strong>{EXAM_HUBS[examSlug].shortName}</strong>? Browse curated notes, summaries, and practice quizzes.
            </p>
            <Link
              href={`/exam/${examSlug}`}
              className="shrink-0 text-sm font-medium text-blue-600 transition-colors hover:underline dark:text-blue-400"
            >
              Browse {EXAM_HUBS[examSlug].shortName} hub →
            </Link>
          </div>
        ) : null}

        {/* More notes in the same Course/Program */}
        {moreByCourseProgram.length > 0 ? (
          <section aria-labelledby="more-course-program-heading">
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h2 id="more-course-program-heading" className="text-lg font-semibold sm:text-xl">
                  More {courseProgram} notes
                </h2>
                <Link
                  href={buildPublicLibraryUrl({ courseProgram: slugifyPublicLibraryFilterValue(courseProgram) })}
                  aria-label={`See all in ${courseProgram}`}
                  className="text-sm font-medium text-blue-600 transition-colors hover:underline dark:text-blue-400"
                >
                  See all →
                </Link>
              </div>
              <div className="grid gap-4 sm:grid-cols-2">
                {moreByCourseProgram.map((n) => (
                  <PublicLibraryReturnLink
                    key={n.id}
                    href={buildPublicLibraryNotePath({ subject: n.subject, title: n.title })}
                    returnUrl={buildPublicLibraryUrl({ courseProgram: slugifyPublicLibraryFilterValue(courseProgram) })}
                    className="block h-full rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 focus-visible:ring-offset-background"
                  >
                    <Card className="motion-pressable h-full p-4 transition-colors hover:border-blue-500/50 hover:bg-blue-500/5">
                      <SharedNoteCard
                        title={n.title}
                        courseProgram={n.courseProgram}
                        applicablePrograms={n.applicablePrograms}
                        subject={n.subject}
                        tags={n.tags}
                        contentPreview={n.contentPreview}
                        summaryPreview={n.summaryPreview}
                        copyCount={n.copyCount}
                        viewCount={n.viewCount}
                        tagDisplayLimit={3}
                        previewLines={2}
                      />
                    </Card>
                  </PublicLibraryReturnLink>
                ))}
              </div>
            </div>
          </section>
        ) : null}

        {moreInSubject.length >= 2 ? (
          <section className="space-y-4" aria-labelledby="more-subject-heading">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h2 id="more-subject-heading" className="text-lg font-semibold sm:text-xl">
                More in {note.subject}
              </h2>
              <Link
                href={buildPublicLibrarySubjectPath(note.subject)}
                aria-label={`See all in ${note.subject}`}
                className="text-sm font-medium text-blue-600 transition-colors hover:underline dark:text-blue-400"
              >
                See all →
              </Link>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              {moreInSubject.map((relatedNote) => (
                <PublicLibraryReturnLink
                  key={relatedNote.id}
                  href={buildPublicLibraryNotePath({ subject: relatedNote.subject, title: relatedNote.title })}
                  returnUrl={buildPublicLibrarySubjectPath(note.subject)}
                  className="block h-full rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 focus-visible:ring-offset-background"
                >
                  <Card className="motion-pressable h-full p-4 transition-colors hover:border-blue-500/50 hover:bg-blue-500/5">
                    <SharedNoteCard
                      title={relatedNote.title}
                      courseProgram={relatedNote.courseProgram}
                      applicablePrograms={relatedNote.applicablePrograms}
                      subject={relatedNote.subject}
                      tags={relatedNote.tags}
                      contentPreview={relatedNote.contentPreview}
                      summaryPreview={relatedNote.summaryPreview}
                      copyCount={relatedNote.copyCount}
                      viewCount={relatedNote.viewCount}
                      tagDisplayLimit={3}
                      previewLines={2}
                    />
                  </Card>
                </PublicLibraryReturnLink>
              ))}
            </div>
          </section>
        ) : null}

        {note.authorDisplayName && note.authorUsername ? (
          <section className="rounded-2xl border border-border bg-card px-4 py-4 sm:px-6" aria-labelledby="more-author-heading">
            <h2 id="more-author-heading" className="text-lg font-semibold sm:text-xl">More from {note.authorDisplayName}</h2>
            <Link
              href={buildPublicLibraryUrl({ creator: note.authorUsername })}
              className="mt-2 inline-flex text-sm font-medium text-blue-600 transition-colors hover:underline dark:text-blue-400"
            >
              Browse {note.authorDisplayName}&apos;s public notes →
            </Link>
          </section>
        ) : null}

        {/* Ownership actions — bottom, after content */}
        <section aria-labelledby="public-note-actions-heading">
          <PublicNoteOwnershipActions
            noteId={note.id}
            ownerUserId={note.ownerUserId}
            isCurrentUser={note.isCurrentUser}
            subject={note.subject}
            title={note.title}
          />
        </section>
      </article>

      <p className="text-xs text-foreground/55">
        Public pages are read-only. Open your own note in the app workspace to edit, review, or manage it.
      </p>
    </main>
  );
}
