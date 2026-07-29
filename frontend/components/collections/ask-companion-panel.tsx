"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import {
  ApiRequestError,
  askCompanionQuestion,
  getActiveAskCompanionSession,
  startAskCompanionSession,
  type AskCompanionSessionResponse,
} from "@/lib/api";
import { getUpgradeCtas, type AppPlanType } from "@/src/config/plans";

type LoadState = "loading" | "ready" | "error";
type AskErrorKind = "quota" | "turn-limit" | "unavailable" | "generic" | null;

const QUOTA_ERROR_CODE = "ASK_COMPANION_QUOTA_EXHAUSTED";
const TURN_LIMIT_ERROR_CODE = "ASK_COMPANION_TURN_LIMIT_REACHED";
const COMPANION_NOT_AVAILABLE_ERROR_CODE = "COMPANION_NOT_AVAILABLE";

function classifyError(error: unknown): AskErrorKind {
  if (!(error instanceof ApiRequestError)) {
    return "generic";
  }
  if (error.code === QUOTA_ERROR_CODE) {
    return "quota";
  }
  if (error.code === TURN_LIMIT_ERROR_CODE) {
    return "turn-limit";
  }
  if (error.code === COMPANION_NOT_AVAILABLE_ERROR_CODE) {
    return "unavailable";
  }
  return "generic";
}

export function AskCompanionPanel({
  collectionId,
  currentPlan,
}: Readonly<{
  collectionId: string;
  currentPlan: AppPlanType;
}>) {
  const isAvailableOnPlan = currentPlan === "PLUS" || currentPlan === "PRO";
  const upgradeCtas = useMemo(
    () => getUpgradeCtas(currentPlan, "ask-companion"),
    [currentPlan],
  );
  const [loadState, setLoadState] = useState<LoadState>(isAvailableOnPlan ? "loading" : "ready");
  const [session, setSession] = useState<AskCompanionSessionResponse | null>(null);
  const [draft, setDraft] = useState("");
  const [isSending, setIsSending] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [errorKind, setErrorKind] = useState<AskErrorKind>(null);

  const loadActiveSession = useCallback(async () => {
    if (!isAvailableOnPlan) {
      return;
    }
    setLoadState("loading");
    setErrorMessage(null);
    setErrorKind(null);
    try {
      setSession(await getActiveAskCompanionSession(collectionId));
      setLoadState("ready");
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Could not load your conversation.");
      setLoadState("error");
    }
  }, [collectionId, isAvailableOnPlan]);

  useEffect(() => {
    void loadActiveSession();
  }, [loadActiveSession]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const question = draft.trim();
    if (!question || isSending) {
      return;
    }
    setIsSending(true);
    setErrorMessage(null);
    setErrorKind(null);
    try {
      const activeSession = session?.status === "ACTIVE"
        ? session
        : await startAskCompanionSession(collectionId);
      const updatedSession = await askCompanionQuestion(activeSession.sessionId, question);
      setSession(updatedSession);
      setDraft("");
    } catch (error) {
      setErrorKind(classifyError(error));
      setErrorMessage(error instanceof Error ? error.message : "Ask Companion could not answer right now.");
    } finally {
      setIsSending(false);
    }
  };

  if (!isAvailableOnPlan) {
    return (
      <Card className="space-y-4 border-blue-500/20 bg-blue-500/5 p-4 sm:p-5">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">Ask Companion</p>
          <CardTitle>Ask about this Review Set</CardTitle>
          <CardDescription>
            Get quick answers grounded in this Review Set&apos;s curator-authored Companion.
          </CardDescription>
        </div>
        {upgradeCtas.primary ? (
          <Link
            href="/settings?section=plans"
            className="inline-flex text-sm font-semibold text-blue-700 hover:underline dark:text-blue-300"
          >
            {upgradeCtas.primary.label}
          </Link>
        ) : null}
      </Card>
    );
  }

  if (loadState === "loading") {
    return (
      <Card className="space-y-3 p-4 sm:p-5" aria-busy="true">
        <CardTitle>Ask Companion</CardTitle>
        <CardDescription>Loading your conversation...</CardDescription>
      </Card>
    );
  }

  if (loadState === "error") {
    return (
      <Card className="space-y-4 border-red-500/25 p-4 sm:p-5">
        <div className="space-y-2">
          <CardTitle>Couldn&apos;t load Ask Companion</CardTitle>
          <CardDescription>{errorMessage}</CardDescription>
        </div>
        <Button type="button" variant="outline" onClick={() => void loadActiveSession()}>
          Retry
        </Button>
      </Card>
    );
  }

  const turnLimitReached = session?.status === "ENDED" || errorKind === "turn-limit";
  const turnsRemaining = session?.turnsRemaining ?? 6;

  return (
    <Card className="space-y-4 p-4 sm:p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">Ask Companion</p>
          <CardTitle>Ask about this Review Set</CardTitle>
          <CardDescription>
            Answers use only the authored Companion guide. Each conversation allows 6 questions.
          </CardDescription>
        </div>
        <p className="text-xs font-medium text-foreground/60">
          {turnsRemaining} {turnsRemaining === 1 ? "turn" : "turns"} remaining
        </p>
      </div>

      {session && session.turns.length > 0 ? (
        <div className="max-h-[28rem] space-y-4 overflow-y-auto rounded-lg border border-border bg-muted/20 p-3" aria-label="Ask Companion conversation">
          {session.turns.map((turn, index) => (
            <div key={`${turn.createdAt}:${index}`} className="space-y-2">
              <div className="ml-auto max-w-[90%] rounded-xl bg-blue-600 px-3 py-2 text-sm text-white">
                {turn.question}
              </div>
              <div className="max-w-[95%] whitespace-pre-wrap rounded-xl border border-border bg-background px-3 py-2 text-sm text-foreground/80">
                {turn.answer}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="rounded-lg border border-dashed border-border p-4 text-sm text-foreground/65">
          Try asking what to focus on first, which mistakes to avoid, or how to use this Review Set.
        </div>
      )}

      {turnLimitReached ? (
        <div className="rounded-lg border border-amber-500/25 bg-amber-500/10 p-3 text-sm text-amber-900 dark:text-amber-100">
          <p className="font-semibold">This conversation has reached its limit.</p>
          <p className="mt-1">Your next question will start a new 6-turn conversation and use another monthly session.</p>
        </div>
      ) : null}

      {errorKind === "quota" ? (
        <div className="rounded-lg border border-amber-500/25 bg-amber-500/10 p-3 text-sm text-amber-900 dark:text-amber-100">
          <p className="font-semibold">Monthly Ask Companion limit reached</p>
          <p className="mt-1">Plus and Pro both include 20 sessions, so come back when your monthly usage resets.</p>
        </div>
      ) : errorKind === "unavailable" ? (
        <div className="rounded-lg border border-border bg-muted/30 p-3 text-sm text-foreground/70">
          Ask Companion is not available for this Review Set.
        </div>
      ) : errorMessage ? (
        <p role="alert" className="text-sm text-red-700 dark:text-red-300">{errorMessage}</p>
      ) : null}

      {errorKind !== "quota" && errorKind !== "unavailable" ? (
        <form className="space-y-3" onSubmit={handleSubmit}>
          <label className="block space-y-1.5" htmlFor={`ask-companion-question-${collectionId}`}>
            <span className="text-sm font-medium">Your question</span>
            <textarea
              id={`ask-companion-question-${collectionId}`}
              value={draft}
              maxLength={1000}
              rows={3}
              disabled={isSending}
              onChange={(event) => setDraft(event.target.value)}
              placeholder="What should I focus on first?"
              className="w-full resize-y rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-500/40 disabled:opacity-60"
            />
          </label>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-xs text-foreground/55">
              {session ? `${session.usedThisMonth}/${session.monthlyLimit} sessions used this month` : "20 sessions per month"}
            </p>
            <Button type="submit" disabled={!draft.trim() || isSending}>
              {isSending ? "Asking..." : turnLimitReached ? "Start new conversation" : "Ask Companion"}
            </Button>
          </div>
        </form>
      ) : null}
    </Card>
  );
}
