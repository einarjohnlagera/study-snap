"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import { BackLink } from "@/components/ui/back-link";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { getSharedStudyPack, type SharedStudyPackResponse } from "@/lib/api";
import { requireVerifiedOnboardedUser } from "@/lib/route-guards";

const NO_LONGER_SHARED_MESSAGE = "This note is no longer shared with you.";
const COULD_NOT_LOAD_MESSAGE = "We could not load this Study Pack. Check your connection and try again.";

export default function SharedStudyPackPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const studyPackId = useMemo(() => Array.isArray(params.id) ? params.id[0] : params.id, [params.id]);
  const [studyPack, setStudyPack] = useState<SharedStudyPackResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadStudyPack = useCallback(async () => {
    if (!studyPackId || !requireVerifiedOnboardedUser(router)) return;
    setLoading(true);
    setError(null);
    try {
      setStudyPack(await getSharedStudyPack(studyPackId));
    } catch (loadFailure) {
      setStudyPack(null);
      // Only a 404 means access is gone; a transient failure must not be reported as an unshare.
      setError(
        loadFailure instanceof ApiRequestError && loadFailure.status === 404
          ? NO_LONGER_SHARED_MESSAGE
          : COULD_NOT_LOAD_MESSAGE,
      );
    } finally {
      setLoading(false);
    }
  }, [router, studyPackId]);

  useEffect(() => { void loadStudyPack(); }, [loadStudyPack]);

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <BackLink href={studyPack ? `/shared/notes/${studyPack.noteId}` : "/library"} label="Shared note" />
      {loading ? (
        <Card className="space-y-3 p-6"><div className="h-7 w-2/3 animate-pulse rounded bg-foreground/10" /><div className="h-4 w-full animate-pulse rounded bg-foreground/10" /></Card>
      ) : error || !studyPack ? (
        <Card className="space-y-4 p-6">
          <h1 className="text-xl font-semibold">{error ?? NO_LONGER_SHARED_MESSAGE}</h1>
          <div className="flex flex-wrap gap-2">
            {error === COULD_NOT_LOAD_MESSAGE ? (
              <Button type="button" variant="outline" onClick={() => void loadStudyPack()}>Try again</Button>
            ) : null}
            <Link href="/library" className={buttonVariants({ variant: "outline" })}>Back to Library</Link>
          </div>
        </Card>
      ) : (
        <>
          <Card className="space-y-4 p-5 sm:p-7">
            <div className="space-y-1">
              <p className="text-sm font-medium text-blue-700 dark:text-blue-300">Shared by {studyPack.ownerDisplayName}</p>
              <h1 className="text-2xl font-semibold sm:text-3xl">{studyPack.title}</h1>
            </div>
            <section className="space-y-2">
              <h2 className="text-lg font-semibold">Summary</h2>
              <p className="whitespace-pre-wrap text-sm leading-7 text-foreground/80">{studyPack.summary}</p>
            </section>
            {studyPack.keyConcepts.length > 0 ? (
              <section className="space-y-2">
                <h2 className="text-lg font-semibold">Key concepts</h2>
                <ul className="list-disc space-y-1 pl-5 text-sm leading-6 text-foreground/80">
                  {studyPack.keyConcepts.map((concept) => <li key={concept}>{concept}</li>)}
                </ul>
              </section>
            ) : null}
            {studyPack.fullNotes ? (
              <section className="space-y-2">
                <h2 className="text-lg font-semibold">Full notes</h2>
                <div className="whitespace-pre-wrap text-sm leading-7 text-foreground/80">{studyPack.fullNotes}</div>
              </section>
            ) : null}
          </Card>
          {studyPack.quiz.length > 0 ? (
            <Link
              href={`/notes/${studyPack.noteId}/quick-review?source=shared`}
              className={buttonVariants({})}
            >
              Start Quick Review
            </Link>
          ) : null}
        </>
      )}
    </main>
  );
}
