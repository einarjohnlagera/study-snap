import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { SubjectBadge } from "@/components/notes/subject-badge";
import { Card } from "@/components/ui/card";
import type { ProfileType } from "@/lib/api";
import {
  buildPublicLibraryNotePathFromSlug,
  buildPublicProfilePath,
} from "@/lib/public-note-path";
import { getServerPublicProfile } from "@/lib/server-public-profiles";
import { buildPageMetadata } from "@/lib/site-metadata";

type PublicProfilePageProps = {
  params: Promise<{
    userId: string;
  }>;
};

const PROFILE_TYPE_LABELS: Record<ProfileType, string> = {
  STUDENT: "Student",
  BOARD_EXAM: "Board Exam",
  TEACHER: "Teacher",
  PARENT: "Parent",
  PROFESSIONAL: "Professional",
};

function formatProfileType(profileType: string | null) {
  if (!profileType) {
    return "Community member";
  }
  return PROFILE_TYPE_LABELS[profileType as ProfileType] ?? profileType.replaceAll("_", " ");
}

function buildDescription(displayName: string, publicNotesCount: number, totalCopies: number) {
  return `${displayName} has shared ${publicNotesCount} public ${publicNotesCount === 1 ? "note" : "notes"} on NoteLib with ${totalCopies} total ${totalCopies === 1 ? "copy" : "copies"}.`;
}

export async function generateMetadata({ params }: PublicProfilePageProps): Promise<Metadata> {
  const { userId } = await params;
  const profile = await getServerPublicProfile(userId);

  if (!profile) {
    return {
      title: "Public Profile Not Found | NoteLib",
      robots: { index: false, follow: false },
    };
  }

  return {
    ...buildPageMetadata({
      title: `${profile.displayName} | NoteLib Public Profile`,
      description: buildDescription(profile.displayName, profile.publicNotesCount, profile.totalCopies),
      path: buildPublicProfilePath(userId),
    }),
    robots: { index: false, follow: true },
  };
}

export default async function PublicProfilePage({ params }: Readonly<PublicProfilePageProps>) {
  const { userId } = await params;
  const profile = await getServerPublicProfile(userId);

  if (!profile) {
    notFound();
  }

  return (
    <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <header className="space-y-4 rounded-3xl border border-blue-500/20 bg-gradient-to-br from-blue-500/10 via-background to-emerald-500/10 p-6 shadow-sm sm:p-8">
        <div className="space-y-3">
          <Link
            href="/public/library"
            className="inline-flex text-sm font-medium text-blue-600 hover:underline dark:text-blue-400"
          >
            Back to Public Library
          </Link>
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-full border border-blue-500/25 bg-blue-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
              Public Profile
            </span>
            {profile.isOfficial ? (
              <span className="rounded-full border border-blue-500/35 bg-blue-500/10 px-3 py-1 text-xs font-medium text-blue-700 dark:text-blue-300">
                Official
              </span>
            ) : null}
          </div>
          <div className="space-y-2">
            <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">{profile.displayName}</h1>
            <p className="text-sm text-foreground/75 sm:text-base">{formatProfileType(profile.profileType)}</p>
          </div>
        </div>

        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <Card className="space-y-1 p-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">Public Notes</p>
            <p className="text-2xl font-semibold">{profile.publicNotesCount}</p>
          </Card>
          <Card className="space-y-1 p-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">Total Copies</p>
            <p className="text-2xl font-semibold">{profile.totalCopies}</p>
          </Card>
          <Card className="space-y-1 p-4 sm:col-span-2 lg:col-span-1">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">Profile Type</p>
            <p className="text-lg font-semibold">{formatProfileType(profile.profileType)}</p>
          </Card>
        </div>
      </header>

      <section aria-labelledby="public-profile-notes" className="space-y-4">
        <div className="flex items-center justify-between gap-3">
          <h2 id="public-profile-notes" className="text-xl font-semibold">
            Public notes
          </h2>
          <p className="text-sm text-foreground/65">
            {profile.publicNotesCount} {profile.publicNotesCount === 1 ? "note" : "notes"}
          </p>
        </div>

        {profile.publicNotes.length === 0 ? (
          <Card className="space-y-3 p-4 sm:p-6">
            <h3 className="text-lg font-semibold">No public notes yet</h3>
            <p className="text-sm text-foreground/75">This user has no public notes yet.</p>
          </Card>
        ) : (
          <div className="grid gap-4 md:grid-cols-2">
            {profile.publicNotes.map((note) => (
              <Link
                key={note.noteId}
                href={buildPublicLibraryNotePathFromSlug({ subject: note.subject, slug: note.slug })}
                className="block focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2"
              >
                <Card className="flex h-full cursor-pointer flex-col justify-between space-y-4 p-4 transition-colors hover:bg-muted/40 hover:shadow-md sm:p-6">
                  <div className="space-y-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <SubjectBadge subject={note.subject} />
                      <span className="rounded-full border border-border bg-muted/40 px-2 py-1 text-xs text-foreground/70">
                        {note.copyCount} {note.copyCount === 1 ? "copy" : "copies"}
                      </span>
                    </div>
                    <h3 className="text-lg font-semibold">{note.title?.trim() || "Untitled note"}</h3>
                    <div className="flex flex-wrap gap-2">
                      {note.tags.length > 0 ? note.tags.map((tag) => (
                        <span
                          key={`${note.noteId}-${tag}`}
                          className="rounded-full border border-border bg-background px-2 py-1 text-xs text-foreground/75"
                        >
                          {tag}
                        </span>
                      )) : (
                        <span className="rounded-full border border-dashed border-border px-2 py-1 text-xs text-foreground/55">
                          No tags
                        </span>
                      )}
                    </div>
                  </div>
                </Card>
              </Link>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
