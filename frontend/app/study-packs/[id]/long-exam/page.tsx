"use client";

import { useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { getMyStudyPack } from "@/lib/api";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";

export default function LegacyStudyPackLongExamPage() {
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const [error, setError] = useState<string | null>(null);
  const studyPackId = useMemo(() => {
    if (!params?.id) {
      return "";
    }
    return Array.isArray(params.id) ? params.id[0] : params.id;
  }, [params]);

  useEffect(() => {
    const redirectToNoteLongExam = async () => {
      if (!studyPackId) {
        setError("Study Pack not found.");
        return;
      }
      if (!requireAuthenticatedOnboardedUser(router)) {
        return;
      }
      try {
        const studyPack = await getMyStudyPack(studyPackId);
        if (!studyPack.noteId) {
          setError("This Study Pack is not attached to a note.");
          return;
        }
        router.replace(`/notes/${studyPack.noteId}/long-exam`);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Could not open Long Exam.");
      }
    };

    void redirectToNoteLongExam();
  }, [router, studyPackId]);

  return (
    <main className="mx-auto max-w-2xl space-y-4 p-4 sm:p-6">
      <BackLink href="/library" label="Back to Library" />
      <Card className="space-y-4 p-4 sm:p-6">
        <div className="space-y-1">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Long Exam Mode
          </p>
          <h1 className="text-xl font-semibold">Opening Long Exam...</h1>
        </div>
        {error ? (
          <>
            <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
            <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => router.push("/library")}>
              Back to Library
            </Button>
          </>
        ) : (
          <p className="text-sm text-foreground/70">Redirecting to the note-based Long Exam page.</p>
        )}
      </Card>
    </main>
  );
}
