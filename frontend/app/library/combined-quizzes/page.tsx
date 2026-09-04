"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";
import {
  listCombinedQuizzes,
  type CombinedQuizSummaryResponse,
} from "@/lib/api";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";

function formatCreatedAt(createdAt: string): string {
  return new globalThis.Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new globalThis.Date(createdAt));
}

function sharingLabel(sharing: CombinedQuizSummaryResponse["sharing"]): string {
  if (sharing === "SHARING_ON") {
    return "Sharing on";
  }
  if (sharing === "SHARING_OFF") {
    return "Sharing off";
  }
  return "No share link";
}

export default function CombinedQuizzesPage() {
  const router = useRouter();
  const [quizzes, setQuizzes] = useState<CombinedQuizSummaryResponse[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const loadQuizzes = useCallback(async () => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
    try {
      const summaries = await listCombinedQuizzes();
      setLoadError(null);
      setQuizzes(summaries);
    } catch (error) {
      setQuizzes(null);
      setLoadError(error instanceof Error ? error.message : "Could not load combined quizzes.");
    }
  }, [router]);

  const retryLoad = useCallback(() => {
    setLoadError(null);
    setQuizzes(null);
    void loadQuizzes();
  }, [loadQuizzes]);

  useEffect(() => {
    // This starts an external read; loadQuizzes updates state only after that request settles.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadQuizzes();
  }, [loadQuizzes]);

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <BackLink href="/library" label="Library" />
      <PageHeader
        eyebrow="LIBRARY"
        title="Combined Quizzes"
        description="Open a combined quiz to manage its share link."
      />

      {loadError ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h2 className="text-xl font-semibold">Could not load combined quizzes</h2>
          <p className="text-sm text-foreground/75">{loadError}</p>
          <Button type="button" onClick={retryLoad}>Retry</Button>
        </Card>
      ) : quizzes === null ? (
        <Card className="space-y-3 p-4 sm:p-6" aria-label="Loading combined quizzes">
          <div className="h-6 w-48 animate-pulse rounded bg-foreground/10" />
          <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
        </Card>
      ) : quizzes.length === 0 ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h2 className="text-xl font-semibold">No combined quizzes yet</h2>
          <p className="text-sm leading-6 text-foreground/75">A combined quiz brings the generated quizzes from several notes into one immutable quiz you can share.</p>
          <Button type="button" onClick={() => router.push("/library")}>Build quiz</Button>
        </Card>
      ) : (
        <div className="space-y-3">
          {quizzes.map((quiz) => (
            <Link
              key={quiz.id}
              href={`/library/combined-quiz/${quiz.id}`}
              className="block rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-2"
            >
              <Card className="space-y-2 p-4 transition-colors hover:bg-highlight/40 sm:p-5">
                <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                  <div className="min-w-0">
                    <h2 className="truncate text-lg font-semibold">{quiz.title}</h2>
                    <p className="mt-1 text-sm text-foreground/70">Made {formatCreatedAt(quiz.createdAt)}</p>
                  </div>
                  <span className="shrink-0 text-sm font-medium text-foreground/75">{sharingLabel(quiz.sharing)}</span>
                </div>
                <p className="text-sm text-foreground/70">
                  {quiz.sectionCount} section{quiz.sectionCount === 1 ? "" : "s"} · {quiz.questionCount} question{quiz.questionCount === 1 ? "" : "s"}
                </p>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </main>
  );
}
