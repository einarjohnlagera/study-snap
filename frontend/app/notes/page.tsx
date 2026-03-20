"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";
import { listNotes, type NoteListItemResponse, type NoteStudyPackStatus } from "@/lib/api";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";

function getStatusMeta(status: NoteStudyPackStatus) {
  if (status === "STUDY_PACK_READY") {
    return {
      label: "Study Pack Ready",
      className: "border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300",
    };
  }
  if (status === "NEEDS_REGENERATION") {
    return {
      label: "Needs Regeneration",
      className: "border-amber-500/40 bg-amber-500/10 text-amber-700 dark:text-amber-300",
    };
  }
  return {
    label: "No Study Pack",
    className: "border-border bg-muted/40 text-foreground/70",
  };
}

function formatUpdatedAt(isoDate: string): string {
  const timestamp = new Date(isoDate);
  if (Number.isNaN(timestamp.getTime())) {
    return "Unknown";
  }
  return `${timestamp.toISOString().slice(0, 16).replace("T", " ")} UTC`;
}

export default function NotesLibraryPage() {
  const router = useRouter();
  const [notes, setNotes] = useState<NoteListItemResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadNotes = useCallback(async () => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const response = await listNotes();
      setNotes(response);
    } catch (loadError) {
      const message = loadError instanceof Error ? loadError.message : "Could not load notes.";
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    void loadNotes();
  }, [loadNotes]);

  const hasNotes = notes.length > 0;
  const sortedNotes = useMemo(
    () => [...notes].sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime()),
    [notes],
  );

  return (
    <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <PageHeader
        eyebrow="NOTES"
        title="Notes"
        description="Write, organize, and generate Study Packs from your saved notes."
      />

      <div className="flex justify-end">
        <Link href="/notes/new">
          <Button type="button">Create Note</Button>
        </Link>
      </div>

      {loading ? (
        <div className="grid gap-4 md:grid-cols-2">
          {Array.from({ length: 4 }).map((_, index) => (
            <Card key={`notes-loading-${index}`} className="space-y-3 p-4 sm:p-6">
              <div className="h-5 w-2/3 animate-pulse rounded bg-foreground/10" />
              <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
              <div className="h-4 w-1/2 animate-pulse rounded bg-foreground/10" />
            </Card>
          ))}
        </div>
      ) : error ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h2 className="text-xl font-semibold">Could not load notes</h2>
          <p className="text-sm text-foreground/75">{error}</p>
          <Button type="button" variant="outline" onClick={() => void loadNotes()}>
            Retry
          </Button>
        </Card>
      ) : !hasNotes ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h2 className="text-xl font-semibold">No notes yet</h2>
          <p className="text-sm text-foreground/75">
            Start with a note, then generate a Study Pack when you are ready.
          </p>
          <Link href="/notes/new" className="w-full sm:w-auto">
            <Button type="button" className="w-full sm:w-auto">Create Note</Button>
          </Link>
        </Card>
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {sortedNotes.map((note) => {
            const status = getStatusMeta(note.studyPackStatus);
            const title = note.title?.trim() || "Untitled note";
            const subject = note.subject?.trim() || "No subject";
            return (
              <Card
                key={note.id}
                role="link"
                tabIndex={0}
                onClick={() => router.push(`/notes/${note.id}`)}
                onKeyDown={(event) => {
                  if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    router.push(`/notes/${note.id}`);
                  }
                }}
                className="flex h-full cursor-pointer flex-col justify-between space-y-4 p-4 transition-colors hover:bg-muted/40 hover:shadow-md sm:p-6"
              >
                <div className="space-y-2">
                  <span className="inline-flex items-center rounded-full border border-blue-500/35 bg-blue-500/10 px-2 py-1 text-xs font-medium text-blue-700 dark:text-blue-300">
                    {subject}
                  </span>
                  <h3 className="text-base font-semibold sm:text-lg">{title}</h3>
                  <span className={`inline-flex items-center rounded-full border px-2 py-1 text-xs font-medium ${status.className}`}>
                    {status.label}
                  </span>
                  <p className="text-sm leading-relaxed text-foreground/75">{note.contentPreview}</p>
                </div>

                <div className="flex flex-wrap gap-2">
                  {note.tags.length > 0 ? (
                    note.tags.map((tag) => (
                      <span
                        key={`${note.id}-${tag}`}
                        className="rounded-full border border-border bg-background px-2 py-1 text-xs text-foreground/75"
                      >
                        {tag}
                      </span>
                    ))
                  ) : (
                    <span className="rounded-full border border-dashed border-border px-2 py-1 text-xs text-foreground/55">
                      No tags
                    </span>
                  )}
                </div>

                <div className="space-y-1">
                  <p className="text-xs text-foreground/65">Updated {formatUpdatedAt(note.updatedAt)}</p>
                  {note.studyPackId ? (
                    <Link
                      href={`/study-packs/${note.studyPackId}?from=notes`}
                      onClick={(event) => event.stopPropagation()}
                      className="inline-block text-xs text-blue-600 hover:underline dark:text-blue-400"
                    >
                      Open Study Pack
                    </Link>
                  ) : null}
                </div>
              </Card>
            );
          })}
        </div>
      )}
    </main>
  );
}
