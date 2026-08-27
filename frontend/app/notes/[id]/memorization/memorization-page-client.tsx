"use client";

import { useRouter } from "next/navigation";
import { Brain, CheckCircle2, Clock, RotateCcw } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  getMemorizationCards,
  getNote,
  gradeMemorizationCard,
  type MemorizationCardResponse,
  type MemorizationGrade,
  type NoteResponse,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { normalizeConceptKey } from "@/lib/concepts";
import { buildMatchedFlashcards, type MatchedFlashcard } from "@/lib/flashcards";
import { buildNoteDetailPathWithTab } from "@/lib/note-entry";
import { cn } from "@/lib/utils";
import { renderMathText } from "@/components/study-pack/quiz-working-solution";

type LoadState = "loading" | "ready" | "error";

type ReviewCard = MatchedFlashcard & {
  key: string;
  dueAt: Date;
  schedule: MemorizationCardResponse | null;
};

const GRADE_LABELS: Record<MemorizationGrade, string> = {
  AGAIN: "Again",
  HARD: "Hard",
  GOOD: "Good",
  EASY: "Easy",
};

const GRADE_HELP: Record<MemorizationGrade, string> = {
  AGAIN: "Review now",
  HARD: "Short interval",
  GOOD: "Normal interval",
  EASY: "Longer interval",
};

function MemorizationGuard({ title, message }: Readonly<{ title: string; message: string }>) {
  return (
    <Card className="space-y-4 p-5 sm:p-6">
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">{title}</h1>
        <p className="text-sm leading-relaxed text-foreground/75">{message}</p>
      </div>
    </Card>
  );
}

function buildScheduleByConcept(cards: MemorizationCardResponse[]): Record<string, MemorizationCardResponse> {
  return Object.fromEntries(cards.map((card) => [normalizeConceptKey(card.concept), card]));
}

function buildReviewCards(
  note: NoteResponse | null,
  scheduleByConcept: Record<string, MemorizationCardResponse>,
  now: Date,
): ReviewCard[] {
  if (!note) {
    return [];
  }
  return buildMatchedFlashcards(note.keyConcepts, note.quiz)
    .map((card) => {
      const key = normalizeConceptKey(card.concept);
      const schedule = scheduleByConcept[key] ?? null;
      return {
        ...card,
        key,
        schedule,
        dueAt: schedule ? new Date(schedule.dueAt) : now,
      };
    })
    .sort((a, b) => a.dueAt.getTime() - b.dueAt.getTime() || a.concept.localeCompare(b.concept));
}

function formatDueDate(value: Date): string {
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(value);
}

export function MemorizationPageClient({ noteId }: Readonly<{ noteId: string }>) {
  const { replace } = useRouter();
  const noteHref = buildNoteDetailPathWithTab(noteId, "key-concepts");
  const isTeacherMode = getAuthUser()?.profileType === "TEACHER";
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [note, setNote] = useState<NoteResponse | null>(null);
  const [scheduleByConcept, setScheduleByConcept] = useState<Record<string, MemorizationCardResponse>>({});
  const [error, setError] = useState<string | null>(null);
  const [currentKey, setCurrentKey] = useState<string | null>(null);
  const [revealed, setRevealed] = useState(false);
  const [gradingGrade, setGradingGrade] = useState<MemorizationGrade | null>(null);
  const [gradeError, setGradeError] = useState<string | null>(null);
  const [referenceNow, setReferenceNow] = useState<Date>(() => new Date());

  useEffect(() => {
    if (isTeacherMode) {
      replace(noteHref);
      return;
    }

    let active = true;
    getNote(noteId)
      .then(async (loadedNote) => {
        if (!active) return;
        setNote(loadedNote);
        setReferenceNow(new Date());
        if (loadedNote.studyPackStatus === "STUDY_PACK_READY" && loadedNote.studyPackId) {
          const memorizationCards = await getMemorizationCards(loadedNote.studyPackId);
          if (!active) return;
          setScheduleByConcept(buildScheduleByConcept(memorizationCards));
        }
        setLoadState("ready");
      })
      .catch((err: unknown) => {
        if (!active) return;
        setError(err instanceof Error ? err.message : "Could not load memorization review.");
        setLoadState("error");
      });

    return () => {
      active = false;
    };
  }, [isTeacherMode, noteHref, noteId, replace]);

  const reviewCards = useMemo(
    () => buildReviewCards(note, scheduleByConcept, referenceNow),
    [note, referenceNow, scheduleByConcept],
  );
  const dueCards = useMemo(
    () => reviewCards.filter((card) => card.dueAt.getTime() <= referenceNow.getTime()),
    [referenceNow, reviewCards],
  );
  const currentCard = dueCards.find((card) => card.key === currentKey) ?? dueCards[0] ?? null;
  const nextDueCard = reviewCards.find((card) => card.dueAt.getTime() > referenceNow.getTime()) ?? null;
  const studyPackStatus = note?.studyPackStatus ?? "DRAFT";
  const hasGeneratedStudyPack = studyPackStatus === "STUDY_PACK_READY";

  useEffect(() => {
    if (loadState !== "ready") {
      return;
    }
    if (dueCards.length === 0) {
      setCurrentKey(null);
      return;
    }
    if (!currentKey || !dueCards.some((card) => card.key === currentKey)) {
      setCurrentKey(dueCards[0].key);
    }
  }, [currentKey, dueCards, loadState]);

  const handleGrade = async (grade: MemorizationGrade) => {
    if (!currentCard || !note?.studyPackId) {
      return;
    }
    setGradingGrade(grade);
    setGradeError(null);
    try {
      const updated = await gradeMemorizationCard(note.studyPackId, currentCard.concept, grade);
      const now = new Date();
      const nextScheduleByConcept = {
        ...scheduleByConcept,
        [normalizeConceptKey(updated.concept)]: updated,
      };
      const nextReviewCards = buildReviewCards(note, nextScheduleByConcept, now);
      const nextDueCards = nextReviewCards.filter((card) => card.dueAt.getTime() <= now.getTime());
      const nextDifferentCard = nextDueCards.find((card) => card.key !== currentCard.key);
      setScheduleByConcept(nextScheduleByConcept);
      setReferenceNow(now);
      setCurrentKey(nextDifferentCard?.key ?? nextDueCards[0]?.key ?? null);
      setRevealed(false);
    } catch (err: unknown) {
      setGradeError(err instanceof Error ? err.message : "Could not save memorization grade.");
    } finally {
      setGradingGrade(null);
    }
  };

  if (isTeacherMode) {
    return (
      <main className="mx-auto w-full max-w-3xl space-y-4 px-4 py-6 sm:px-6 sm:py-10">
        <BackLink href={noteHref} label="Note" />
      </main>
    );
  }

  return (
    <main className="mx-auto w-full max-w-3xl space-y-5 px-4 py-6 sm:px-6 sm:py-10">
      <BackLink href={noteHref} label="Note" />

      {loadState === "loading" ? (
        <MemorizationGuard
          title="Memorization"
          message="Loading your spaced-repetition schedule from this Study Pack."
        />
      ) : null}

      {loadState === "error" ? (
        <MemorizationGuard
          title="Memorization unavailable"
          message={error ?? "Could not load memorization review."}
        />
      ) : null}

      {loadState === "ready" && note ? (
        <>
          <header className="space-y-3">
            <div className="inline-flex items-center gap-2 rounded-full border border-border bg-background px-3 py-1 text-xs font-medium text-foreground/70">
              <Brain className="h-3.5 w-3.5" aria-hidden="true" />
              <span>Memorization</span>
            </div>
            <div className="space-y-2">
              <h1 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
                {note.title ?? "Untitled note"}
              </h1>
              <p className="max-w-2xl text-base leading-relaxed text-foreground/70">
                Review due concepts one at a time, then grade your recall to schedule the next review.
              </p>
            </div>
          </header>

          {studyPackStatus === "GENERATING" ? (
            <MemorizationGuard
              title="Key concepts are being generated"
              message="Key concepts are being generated from your note."
            />
          ) : null}

          {studyPackStatus === "FAILED" ? (
            <MemorizationGuard
              title="Memorization is not available yet"
              message="Generation did not complete, so key concepts are not available yet. Retry generation when you are ready."
            />
          ) : null}

          {!hasGeneratedStudyPack && studyPackStatus !== "GENERATING" && studyPackStatus !== "FAILED" ? (
            <MemorizationGuard
              title="No key concepts yet"
              message="No key concepts yet. Generate a Study Pack to extract the most important ideas from this note."
            />
          ) : null}

          {hasGeneratedStudyPack && reviewCards.length === 0 ? (
            <MemorizationGuard
              title="Nothing to memorize yet"
              message="This Study Pack does not have key concepts with matched quiz explanations yet."
            />
          ) : null}

          {hasGeneratedStudyPack && reviewCards.length > 0 && dueCards.length === 0 ? (
            <Card className="space-y-4 p-5 sm:p-6">
              <div className="flex items-start gap-3">
                <CheckCircle2 className="mt-1 h-5 w-5 text-emerald-600" aria-hidden="true" />
                <div className="space-y-2">
                  <h2 className="text-2xl font-semibold tracking-tight text-foreground">You&apos;re all caught up</h2>
                  <p className="text-sm leading-relaxed text-foreground/75">
                    No memorization cards are due right now.
                    {nextDueCard ? ` Next due: ${formatDueDate(nextDueCard.dueAt)}.` : ""}
                  </p>
                </div>
              </div>
            </Card>
          ) : null}

          {hasGeneratedStudyPack && currentCard ? (
            <section className="space-y-4" aria-label="Memorization review">
              <div className="flex items-center justify-between gap-3 text-sm text-foreground/65">
                <span>{dueCards.length} due</span>
                <span className="inline-flex items-center gap-1">
                  <Clock className="h-3.5 w-3.5" aria-hidden="true" />
                  {currentCard.schedule ? `Interval: ${currentCard.schedule.intervalDays}d` : "New card"}
                </span>
              </div>
              <Card className="space-y-6 p-5 sm:p-8">
                <div className="space-y-4 text-center">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-foreground/50">
                    Concept
                  </p>
                  <h2 className="text-3xl font-semibold leading-tight text-foreground sm:text-5xl">
                    {currentCard.concept}
                  </h2>
                </div>

                {revealed ? (
                  <div className="rounded-xl border border-border bg-muted/30 p-4 text-center">
                    <p className="text-lg font-medium leading-relaxed text-foreground sm:text-xl">
                      {renderMathText(currentCard.explanation)}
                    </p>
                  </div>
                ) : (
                  <Button type="button" className="w-full" onClick={() => setRevealed(true)}>
                    Reveal definition
                  </Button>
                )}

                {revealed ? (
                  <div className="space-y-3">
                    <p className="text-center text-sm font-medium text-foreground/70">How well did you remember it?</p>
                    <div className="grid gap-2 sm:grid-cols-4">
                      {(Object.keys(GRADE_LABELS) as MemorizationGrade[]).map((grade) => (
                        <Button
                          key={grade}
                          type="button"
                          variant={grade === "AGAIN" ? "destructive" : "outline"}
                          onClick={() => void handleGrade(grade)}
                          disabled={gradingGrade !== null}
                          className={cn("h-auto flex-col gap-1 py-3", grade === "GOOD" ? "border-primary/50" : "")}
                        >
                          {gradingGrade === grade ? (
                            <RotateCcw className="h-4 w-4 animate-spin" aria-hidden="true" />
                          ) : null}
                          <span>{GRADE_LABELS[grade]}</span>
                          <span className="text-xs font-normal opacity-75">{GRADE_HELP[grade]}</span>
                        </Button>
                      ))}
                    </div>
                    {gradeError ? (
                      <p role="alert" className="text-center text-sm font-medium text-red-700 dark:text-red-300">{gradeError}</p>
                    ) : null}
                  </div>
                ) : null}
              </Card>
            </section>
          ) : null}
        </>
      ) : null}
    </main>
  );
}
