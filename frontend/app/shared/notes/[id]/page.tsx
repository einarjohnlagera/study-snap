"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { BackLink } from "@/components/ui/back-link";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  ApiRequestError,
  copySharedNote,
  getSharedNote,
  isNoteGenerationLimitReachedError,
  type SharedNoteResponse,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { formatLearnerLevel } from "@/lib/learning-profile";
import { requireVerifiedOnboardedUser } from "@/lib/route-guards";
import { getUpgradeCtas, type AppPlanType } from "@/src/config/plans";

const NO_LONGER_SHARED_MESSAGE = "This note is no longer shared with you.";
const COULD_NOT_LOAD_MESSAGE = "We could not load this note. Check your connection and try again.";

export default function SharedNotePage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const noteId = useMemo(() => Array.isArray(params.id) ? params.id[0] : params.id, [params.id]);
  const [note, setNote] = useState<SharedNoteResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [copying, setCopying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copyError, setCopyError] = useState<string | null>(null);

  const loadNote = useCallback(async () => {
    if (!noteId || !requireVerifiedOnboardedUser(router)) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setNote(await getSharedNote(noteId));
    } catch (loadFailure) {
      setNote(null);
      // Only a 404 means the share is gone. Reporting a transient failure as "no longer shared" tells the
      // recipient something untrue about the owner's intent, and leaves them no reason to retry.
      setError(
        loadFailure instanceof ApiRequestError && loadFailure.status === 404
          ? NO_LONGER_SHARED_MESSAGE
          : COULD_NOT_LOAD_MESSAGE,
      );
    } finally {
      setLoading(false);
    }
  }, [noteId, router]);

  useEffect(() => {
    void loadNote();
  }, [loadNote]);

  const handleCopy = async () => {
    if (!note || copying) return;
    setCopying(true);
    setCopyError(null);
    try {
      const copied = await copySharedNote(note.id);
      router.push(`/notes/${copied.id}?copied=1`);
    } catch (copyFailure) {
      if (copyFailure instanceof ApiRequestError && copyFailure.status === 404) {
        await loadNote();
        setCopyError(NO_LONGER_SHARED_MESSAGE);
      } else if (isNoteGenerationLimitReachedError(copyFailure)) {
        const currentPlan = (getAuthUser()?.planType ?? "FREE") as AppPlanType;
        const upgradeCtas = getUpgradeCtas(currentPlan);
        setCopyError(
          upgradeCtas.primary
            ? `${copyFailure.message} ${upgradeCtas.primary.label} from Plans to keep building your Library.`
            : copyFailure.message,
        );
      } else {
        setCopyError(copyFailure instanceof Error ? copyFailure.message : "Could not copy this shared note.");
      }
    } finally {
      setCopying(false);
    }
  };

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <BackLink href="/library" label="Library" />
      {loading ? (
        <Card className="space-y-3 p-6">
          <div className="h-6 w-2/3 animate-pulse rounded bg-foreground/10" />
          <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
          <div className="h-4 w-4/5 animate-pulse rounded bg-foreground/10" />
        </Card>
      ) : error || !note ? (
        <Card className="space-y-4 p-6">
          <h1 className="text-xl font-semibold">{error ?? NO_LONGER_SHARED_MESSAGE}</h1>
          <p className="text-sm text-foreground/70">
            {error === COULD_NOT_LOAD_MESSAGE
              ? "This is usually temporary."
              : "The owner may have removed access or deleted the note."}
          </p>
          <div className="flex flex-wrap gap-2">
            {error === COULD_NOT_LOAD_MESSAGE ? (
              <Button type="button" variant="outline" onClick={() => void loadNote()}>Try again</Button>
            ) : null}
            <Link href="/library" className={buttonVariants({ variant: "outline" })}>Back to Library</Link>
          </div>
        </Card>
      ) : (
        <>
          <Card className="space-y-5 p-5 sm:p-7">
            <div className="space-y-2">
              <p className="text-sm font-medium text-blue-700 dark:text-blue-300">Shared by {note.ownerDisplayName}</p>
              <h1 className="text-2xl font-semibold sm:text-3xl">{note.title?.trim() || "Untitled note"}</h1>
              <div className="flex flex-wrap gap-2 text-xs text-foreground/65">
                {note.subject ? <span>{note.subject}</span> : null}
                {note.courseProgram ? <span>· {note.courseProgram}</span> : null}
                {note.learnerLevel ? <span>· {formatLearnerLevel(note.learnerLevel)}</span> : null}
              </div>
              {note.tags.length > 0 ? (
                <div className="flex flex-wrap gap-2 pt-1">
                  {note.tags.map((tag) => <span key={tag} className="rounded-full bg-muted px-2.5 py-1 text-xs">{tag}</span>)}
                </div>
              ) : null}
            </div>
            <div className="whitespace-pre-wrap text-sm leading-7 text-foreground/85">{note.content}</div>
          </Card>
          <div className="flex flex-col gap-2 sm:flex-row">
            {note.studyPackId ? (
              <Link href={`/shared/study-packs/${note.studyPackId}`} className={buttonVariants({})}>Open Study Pack</Link>
            ) : null}
            {note.canCopy ? (
              <Button type="button" variant="outline" onClick={() => void handleCopy()} disabled={copying}>
                {copying ? "Copying…" : "Copy to my Library"}
              </Button>
            ) : null}
          </div>
          {copyError ? <p className="text-sm text-red-700 dark:text-red-300">{copyError}</p> : null}
        </>
      )}
    </main>
  );
}
