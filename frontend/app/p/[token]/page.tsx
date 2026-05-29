"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PracticeQuizCard } from "@/components/study-pack/practice-quiz-card";
import { SummaryMarkdown } from "@/components/ui/summary-markdown";
import {
  getPublicSharedStudyPack,
  remixSharedStudyPack,
  type PublicShareResponse,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

function SharedStudyPackLoading() {
  return (
    <div className="space-y-6">
      <Card className="space-y-3 p-4 sm:p-6">
        <div className="h-4 w-32 animate-pulse rounded bg-foreground/10" />
        <div className="h-7 w-2/3 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-1/3 animate-pulse rounded bg-foreground/10" />
      </Card>
      <Card className="space-y-3 p-4 sm:p-6">
        <div className="h-6 w-24 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-4/5 animate-pulse rounded bg-foreground/10" />
      </Card>
    </div>
  );
}

export default function PublicSharePage() {
  const params = useParams<{ token: string }>();
  const router = useRouter();
  const [sharedStudyPack, setSharedStudyPack] = useState<PublicShareResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [remixing, setRemixing] = useState(false);
  const [remixError, setRemixError] = useState<string | null>(null);

  const token = useMemo(() => {
    if (!params?.token) {
      return "";
    }
    return Array.isArray(params.token) ? params.token[0] : params.token;
  }, [params]);

  useEffect(() => {
    setIsAuthenticated(Boolean(getAuthUser()));
  }, []);

  const loadSharedStudyPack = useCallback(async () => {
    if (!token) {
      setError("Share link not found.");
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    setRemixError(null);
    try {
      const response = await getPublicSharedStudyPack(token);
      setSharedStudyPack(response);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load this shared Study Pack.";
      setError(message);
      setSharedStudyPack(null);
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    void loadSharedStudyPack();
  }, [loadSharedStudyPack]);

  const handleCopyToLibrary = async () => {
    if (!token) {
      return;
    }
    if (!isAuthenticated) {
      router.push("/auth");
      return;
    }

    setRemixing(true);
    setRemixError(null);
    try {
      const remixed = await remixSharedStudyPack(token);
      if (remixed.noteId) {
        router.push(`/notes/${remixed.noteId}?copied=1`);
        return;
      }
      router.push(`/study-packs/${remixed.studyPackId}?copied=1`);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not copy this Study Pack.";
      setRemixError(message);
    } finally {
      setRemixing(false);
    }
  };

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <div className="flex items-center justify-between gap-3">
        <BackLink href="/" label="Home" />
      </div>

      {loading ? (
        <SharedStudyPackLoading />
      ) : error ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">Could not load shared Study Pack</h1>
          <p className="text-sm text-foreground/75">{error}</p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" className="w-full sm:w-auto" onClick={() => void loadSharedStudyPack()}>
              Retry
            </Button>
            <Link href="/" className="w-full sm:w-auto">
              <Button type="button" variant="outline" className="w-full sm:w-auto">
                Go to Home
              </Button>
            </Link>
          </div>
        </Card>
      ) : sharedStudyPack ? (
        <div className="space-y-6">
          <Card className="space-y-3 p-4 sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              Shared Study Pack
            </p>
            <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">{sharedStudyPack.title}</h1>
            <p className="text-sm text-foreground/75">This Study Pack is read-only.</p>
            <div className="space-y-2">
              {isAuthenticated ? (
                <Button type="button" className="w-full sm:w-auto" onClick={() => void handleCopyToLibrary()} disabled={remixing}>
                  {remixing ? "Copying..." : "Copy to Library"}
                </Button>
              ) : (
                <Link href="/auth" className="w-full sm:w-auto">
                  <Button type="button" className="w-full sm:w-auto">
                    Sign up to copy this Study Pack
                  </Button>
                </Link>
              )}
              {remixError ? <p className="text-sm text-red-600 dark:text-red-400">{remixError}</p> : null}
            </div>
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Summary</h2>
            <SummaryMarkdown content={sharedStudyPack.summary} className="text-foreground/85" />
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Key Concepts</h2>
            <ul className="list-disc space-y-2 pl-5 text-sm leading-relaxed text-foreground/85">
              {sharedStudyPack.keyConcepts.map((concept, index) => (
                <li key={`${sharedStudyPack.id}-concept-${index}`}>{concept}</li>
              ))}
            </ul>
          </Card>

          <PracticeQuizCard quiz={sharedStudyPack.quiz} />

          <Card className="space-y-3 border-blue-500/30 bg-blue-500/10 p-4 sm:p-6">
            <p className="text-sm font-medium text-foreground/85">Created with NoteLib</p>
            <p className="text-sm text-foreground/75">Turn your notes into Study Packs instantly.</p>
            <Link href="/auth" className="w-full sm:w-auto">
              <Button type="button" variant="outline" className="w-full sm:w-auto">
                Create your own Study Pack
              </Button>
            </Link>
          </Card>
        </div>
      ) : null}
    </main>
  );
}
