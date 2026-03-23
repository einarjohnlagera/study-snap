import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { PublicSeoCopyCta } from "@/components/notes/public-seo-copy-cta";
import { Card } from "@/components/ui/card";
import { buildPublicLibraryNotePathFromDetail } from "@/lib/public-note-path";
import { getServerPublicNoteBySeoPath } from "@/lib/server-public-notes";

type PublicLibrarySeoPageProps = {
  params: Promise<{
    subject: string;
    slug: string;
  }>;
};

function getAppBaseUrl() {
  return process.env.NEXT_PUBLIC_APP_URL ?? "http://localhost:3000";
}

function buildDescription(title: string) {
  return `Study ${title} with summary, key concepts, and quiz reviewer. Free study pack from NoteLib.`;
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
  const description = buildDescription(title);
  const url = `${getAppBaseUrl()}${buildPublicLibraryNotePathFromDetail(note)}`;

  return {
    title: `${title} Summary and Reviewer | NoteLib`,
    description,
    openGraph: {
      title: `${title} Summary and Reviewer | NoteLib`,
      description,
      type: "article",
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

  return (
    <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
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
            <p className="text-sm text-foreground/75 sm:text-base">Subject: {subjectLabel}</p>
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
            <p className="text-sm text-foreground/80">By {note.authorDisplayName}</p>
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
              Make a copy to try the interactive quiz, see answers, and track your score.
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
              Preview only. The full quiz experience, answer reveal, and score tracking are available after you make a
              copy to your library.
            </div>
            <PublicSeoCopyCta noteId={note.id} />
          </Card>
        </section>
      </article>

      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <Link href="/library/public" className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400">
          Back to Public Library
        </Link>
        <p className="text-xs text-foreground/55">
          Public pages are read-only. Make a copy to keep studying in your own library.
        </p>
      </div>
    </main>
  );
}
