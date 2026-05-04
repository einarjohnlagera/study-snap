import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { PublicMiniQuizPreview } from "@/components/notes/public-mini-quiz-preview";
import { PublicLibraryBackLink } from "@/components/notes/public-library-back-link";
import { PublicNoteAuthorLine, PublicNoteOwnershipActions } from "@/components/notes/public-note-ownership-actions";
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
  if (summary?.trim()) {
    return truncateDescription(summary, 160);
  }
  return `Study ${title} with summaries, key concepts, and practice questions on NoteLib.`;
}

function buildHook(summary: string | null | undefined, title: string): string | null {
  if (!summary?.trim()) return null;
  const sentences = summary.split(/(?<=[.!?])\s+/);
  const hook = sentences.slice(0, 2).join(" ").trim();
  if (!hook || hook.toLowerCase() === title.toLowerCase()) return null;
  return hook;
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

export default async function PublicLibrarySeoPage({ params }: Readonly<PublicLibrarySeoPageProps>) {
  const { subject, slug } = await params;
  const note = await getServerPublicNoteBySeoPath(subject, slug);
  if (!note) {
    notFound();
  }

  const title = note.title?.trim() || "Untitled note";
  const description = buildDescription(title, note.summary);
  const canonicalUrl = absoluteUrl(buildPublicLibraryNotePathFromDetail(note));
  const currentPath = buildPublicLibraryNotePathFromDetail(note);
  const hook = buildHook(note.summary, title);
  const isDraft = note.studyPackStatus !== "STUDY_PACK_READY";
  const fullContent = note.content?.trim() || "No content yet.";

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
      <PublicLibraryBackLink />

      <article className="space-y-6">
        {/* Header — title, hook, tags, author */}
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

            {hook ? (
              <p className="text-sm leading-relaxed text-foreground/70 sm:text-base">{hook}</p>
            ) : null}

            <PublicNoteAuthorLine
              ownerUserId={note.ownerUserId}
              authorDisplayName={note.authorDisplayName}
              isOfficialAuthor={note.isOfficialAuthor}
              isCurrentUser={note.isCurrentUser}
              subject={note.subject}
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

        {/* Summary — always visible */}
        <Card className="space-y-3 p-4 sm:p-6">
          <h2 className="text-lg font-semibold sm:text-xl">Summary</h2>
          <p className="text-sm leading-relaxed text-foreground/80">
            {isDraft
              ? "This public note does not have a generated summary yet."
              : (note.summary?.trim() || "No summary available yet.")}
          </p>
          {!isDraft && note.content?.trim() ? (
            <a
              href="#full-notes"
              className="inline-flex w-fit text-sm font-medium text-blue-600 transition-colors hover:underline dark:text-blue-400"
            >
              View Full Notes →
            </a>
          ) : null}
        </Card>

        {/* Key Concepts — always visible */}
        <Card className="space-y-3 p-4 sm:p-6">
          <h2 className="text-lg font-semibold sm:text-xl">🧠 Key Concepts</h2>
          {isDraft || note.keyConcepts.length === 0 ? (
            <p className="text-sm text-foreground/75">No key concepts generated yet.</p>
          ) : (
            <ul className="space-y-2 pl-5 list-disc text-sm leading-relaxed text-foreground/85">
              {note.keyConcepts.map((concept) => (
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
            currentPath={currentPath}
          />
        ) : null}

        {/* Soft conversion CTA */}
        <Card className="space-y-3 border-primary/20 bg-primary/5 p-4 sm:p-6">
          <h2 className="text-base font-semibold sm:text-lg">Struggling with a topic?</h2>
          <p className="text-sm text-foreground/75">
            NoteLib turns your notes into summaries, key concepts, and practice quizzes — so you can study smarter, not longer.
          </p>
          <ul className="space-y-1 pl-4 text-sm text-foreground/70 list-disc">
            <li>Break notes into key concepts</li>
            <li>Test your understanding</li>
            <li>Focus on your weak areas</li>
          </ul>
          <Link
            href="/signup"
            className="inline-flex items-center rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary-hover active:bg-primary-active"
          >
            Create your own Study Pack →
          </Link>
        </Card>

        {/* Full Notes */}
        <Card id="full-notes" className="space-y-3 p-4 sm:p-6">
          <h2 className="text-lg font-semibold sm:text-xl">Full Notes</h2>
          <div className="rounded-2xl border border-border bg-background px-4 py-4">
            <p className="whitespace-pre-wrap text-sm leading-7 text-foreground/85">
              {fullContent}
            </p>
          </div>
        </Card>

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
