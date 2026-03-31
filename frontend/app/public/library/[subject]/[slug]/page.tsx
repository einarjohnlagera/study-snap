import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { PublicLibraryBackLink } from "@/components/notes/public-library-back-link";
import { PublicNoteOwnershipActions } from "@/components/notes/public-note-ownership-actions";
import { StructuredDataScript } from "@/components/seo/structured-data-script";
import { Card } from "@/components/ui/card";
import { buildPublicLibraryNotePathFromDetail } from "@/lib/public-note-path";
import { getServerPublicNoteBySeoPath } from "@/lib/server-public-notes";
import { absoluteUrl, buildPageMetadata, truncateDescription } from "@/lib/site-metadata";
import { buildArticleStructuredData } from "@/lib/structured-data";

type PublicLibrarySeoPageProps = {
  params: Promise<{
    subject: string;
    slug: string;
  }>;
};

function buildDescription(title: string, summary?: string | null) {
  if (summary && summary.trim()) {
    return truncateDescription(summary, 160);
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

  const title = note.title?.trim() || "Untitled note";
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
    },
  };
}

export default async function PublicLibrarySeoPage({ params }: PublicLibrarySeoPageProps) {
  const { subject, slug } = await params;
  const note = await getServerPublicNoteBySeoPath(subject, slug);
  if (!note) {
    notFound();
  }

  const title = note.title?.trim() || "Untitled note";
  const subjectLabel = note.subject?.trim() || "General";
  const hasGeneratedStudyPack = note.studyPackStatus === "STUDY_PACK_READY";
  const quizPreview = note.quiz.slice(0, 3);
  const description = buildDescription(title, note.summary);
  const canonicalUrl = absoluteUrl(buildPublicLibraryNotePathFromDetail(note));

  return (
    <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
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
      <article className="space-y-6">
        <header className="rounded-3xl border border-blue-500/20 bg-gradient-to-br from-blue-500/10 via-background to-amber-500/10 p-6 shadow-sm sm:p-8">
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
            <PublicNoteOwnershipActions
              noteId={note.id}
              ownerUserId={note.ownerUserId}
              official={note.official}
              subjectLabel={subjectLabel}
            />
            <div className="flex flex-wrap gap-2">
              {note.tags.length > 0 ? note.tags.map((tag) => (
                <span
                  key={`${note.id}-${tag}`}
                  className="rounded-full border border-border bg-background px-3 py-1 text-xs text-foreground/75"
                >
                  {tag}
                </span>
              )) : (
                <span className="rounded-full border border-dashed border-border px-3 py-1 text-xs text-foreground/55">
                  No tags
                </span>
              )}
            </div>
          </div>
        </header>

        <section aria-labelledby="public-note-summary">
          <Card className="space-y-3 p-4 sm:p-6">
            <h2 id="public-note-summary" className="text-xl font-semibold">
              Summary
            </h2>
            <p className="text-sm leading-relaxed text-foreground/80">
              {hasGeneratedStudyPack && note.summary
                ? note.summary
                : "This public note does not have a generated summary yet."}
            </p>
          </Card>
        </section>

        <section aria-labelledby="public-note-concepts">
          <Card className="space-y-3 p-4 sm:p-6">
            <h2 id="public-note-concepts" className="text-xl font-semibold">
              Key Concepts
            </h2>
            {hasGeneratedStudyPack && note.keyConcepts.length > 0 ? (
              <ul className="list-disc space-y-2 pl-5 text-sm leading-relaxed text-foreground/85">
                {note.keyConcepts.map((concept) => (
                  <li key={concept}>{concept}</li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-foreground/75">No generated key concepts yet.</p>
            )}
          </Card>
        </section>

        <section aria-labelledby="public-note-whats-inside">
          <Card className="space-y-3 border-blue-500/20 bg-blue-500/5 p-4 sm:p-6">
            <h2 id="public-note-whats-inside" className="text-xl font-semibold">
              What you can do with this note in NoteLib
            </h2>
            <ul className="list-disc space-y-2 pl-5 text-sm leading-relaxed text-foreground/85">
              <li>Review summary</li>
              <li>Study key concepts</li>
              <li>Take Quick Review quiz</li>
              <li>Try Challenge Quiz (Exam Mode)</li>
              <li>Practice weak topics with Adaptive Practice</li>
            </ul>
          </Card>
        </section>

        <section aria-labelledby="public-note-quiz">
          <Card className="space-y-3 p-4 sm:p-6">
            <h2 id="public-note-quiz" className="text-xl font-semibold">
              Practice Questions Preview
            </h2>
            <p className="text-sm text-foreground/70">
              Copy this note or open your own version to try the interactive quiz, see answers, and track your score.
            </p>
            {hasGeneratedStudyPack && quizPreview.length > 0 ? (
              <ol className="space-y-4">
                {quizPreview.map((item, index) => (
                  <li key={`${note.id}-quiz-${index}`} className="space-y-2 text-sm text-foreground/85">
                    <p className="font-medium text-foreground">
                      {index + 1}. {item.question}
                    </p>
                    <ul className="list-disc space-y-1 pl-5">
                      {item.choices.slice(0, 4).map((choice) => (
                        <li key={`${item.question}-${choice}`}>{choice}</li>
                      ))}
                    </ul>
                  </li>
                ))}
              </ol>
            ) : (
              <p className="text-sm text-foreground/75">No quiz preview available yet.</p>
            )}
            <div className="rounded-2xl border border-dashed border-border bg-muted/30 p-3 text-sm text-foreground/70">
              Preview only. The full quiz experience, answer reveal, and score tracking are available from your own
              note in the app workspace.
            </div>
          </Card>
        </section>
      </article>

      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <PublicLibraryBackLink className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400" />
        <p className="text-xs text-foreground/55">
          Public pages are read-only. Open your own note in the app workspace to edit, review, or manage it.
        </p>
      </div>
    </main>
  );
}
