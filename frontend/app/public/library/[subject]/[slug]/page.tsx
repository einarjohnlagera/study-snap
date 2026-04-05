import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { PublicNoteDetailTabbedContent } from "@/components/notes/public-note-detail-tabbed-content";
import { PublicLibraryBackLink } from "@/components/notes/public-library-back-link";
import { PublicNoteAuthorLine, PublicNoteOwnershipActions} from "@/components/notes/public-note-ownership-actions";
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
            <PublicNoteOwnershipActions
              noteId={note.id}
              ownerUserId={note.ownerUserId}
              isCurrentUser={note.isCurrentUser}
              subject={note.subject}
              title={note.title}
            />
          </div>
        </header>

        <PublicNoteDetailTabbedContent
          studyPackStatus={note.studyPackStatus}
          summary={note.summary}
          keyConcepts={note.keyConcepts}
          quiz={note.quiz}
          content={note.content}
          quizMode="preview"
        />

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
