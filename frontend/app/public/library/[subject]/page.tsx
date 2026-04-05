import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { SharedNoteCard } from "@/components/notes/shared-note-card";
import { StructuredDataScript } from "@/components/seo/structured-data-script";
import { Card } from "@/components/ui/card";
import {
  buildPublicLibraryNotePath,
  buildPublicLibrarySubjectPath,
} from "@/lib/public-note-path";
import {
  getServerPublicNotesBySubjectSlug,
  getServerPublicSubjects,
} from "@/lib/server-public-notes";
import { absoluteUrl, buildPageMetadata } from "@/lib/site-metadata";
import { buildCollectionPageStructuredData } from "@/lib/structured-data";

type PublicLibrarySubjectPageProps = {
  params: Promise<{
    subject: string;
  }>;
};

function buildSubjectDescription(subjectLabel: string) {
  return `Browse public ${subjectLabel} notes, summaries, and practice questions shared by the NoteLib community.`;
}

export async function generateMetadata({ params }: PublicLibrarySubjectPageProps): Promise<Metadata> {
  const { subject } = await params;
  const subjects = await getServerPublicSubjects();
  const matchedSubject = subjects.find((entry) => entry.slug === subject);

  if (!matchedSubject) {
    return {
      title: "Public Subject Not Found | NoteLib",
      robots: { index: false, follow: false },
    };
  }

  return buildPageMetadata({
    title: `${matchedSubject.label} Notes and Study Packs | NoteLib Public Library`,
    description: buildSubjectDescription(matchedSubject.label),
    path: buildPublicLibrarySubjectPath(matchedSubject.label),
  });
}

export default async function PublicLibrarySubjectPage({ params }: Readonly<PublicLibrarySubjectPageProps>) {
  const { subject } = await params;
  const subjects = await getServerPublicSubjects();
  const matchedSubject = subjects.find((entry) => entry.slug === subject);

  if (!matchedSubject) {
    notFound();
  }

  const notes = await getServerPublicNotesBySubjectSlug(subject);

  const canonicalUrl = absoluteUrl(buildPublicLibrarySubjectPath(matchedSubject.label));
  const description = buildSubjectDescription(matchedSubject.label);

  return (
    <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <StructuredDataScript
        id="public-library-subject-structured-data"
        data={buildCollectionPageStructuredData({
          name: `${matchedSubject.label} Notes | NoteLib Public Library`,
          url: canonicalUrl,
          description,
        })}
      />

      <header className="space-y-3">
        <Link
          href="/public/library"
          className="inline-flex text-sm font-medium text-blue-600 hover:underline dark:text-blue-400"
        >
          Browse all public subjects
        </Link>
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-[0.24em] text-blue-600 dark:text-blue-400">
            Public Library
          </p>
          <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">
            {matchedSubject.label} Notes
          </h1>
          <p className="max-w-3xl text-sm leading-relaxed text-foreground/75 sm:text-base">
            {description}
          </p>
        </div>
      </header>

      <section aria-labelledby="public-library-subject-notes" className="space-y-4">
        <div className="flex items-center justify-between gap-3">
          <h2 id="public-library-subject-notes" className="text-xl font-semibold">
            Public study notes
          </h2>
          <p className="text-sm text-foreground/65">
            {notes.length} {notes.length === 1 ? "note" : "notes"}
          </p>
        </div>

        {notes.length === 0 ? (
          <Card className="space-y-3 p-4 sm:p-6">
            <h3 className="text-lg font-semibold">No public notes yet</h3>
            <p className="text-sm text-foreground/75">
              There are no public notes for {matchedSubject.label} right now.
            </p>
          </Card>
        ) : (
          <div className="grid gap-4 md:grid-cols-2">
            {notes.map((note) => (
              <Link
                key={note.id}
                href={buildPublicLibraryNotePath({ subject: note.subject, title: note.title })}
                className="block focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2"
              >
                <Card className="flex h-full flex-col justify-between space-y-4 p-4 transition-colors hover:bg-muted/40 hover:shadow-md sm:p-6">
                  <SharedNoteCard
                    title={note.title}
                    metaLine={note.courseProgram?.trim() || null}
                    subject={note.subject}
                    tags={note.tags}
                    contentPreview={note.contentPreview}
                    summaryPreview={note.summaryPreview}
                    copyCount={typeof note.copyCount === "number" && note.copyCount > 0 ? note.copyCount : null}
                    viewCount={typeof note.viewCount === "number" && note.viewCount > 0 ? note.viewCount : null}
                  />
                </Card>
              </Link>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
