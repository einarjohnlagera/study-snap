"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  getMe,
  getMasterySnapshot,
  getTodayFocus,
  getQuickReviewPerformanceSummary,
  listNotes,
  type MasterySnapshotResponse,
  type NoteListItemResponse,
  type TodayFocusResponse,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { DashboardHero } from "./dashboard-hero";
import { DashboardStats } from "./dashboard-stats";
import { StudyPackGrid } from "./study-pack-grid";
import { DashboardLoading } from "./dashboard-loading";
import { DashboardEmpty } from "./dashboard-empty";
import { DashboardError } from "./dashboard-error";
import { TodayFocusCard } from "./today-focus-card";
import { MasterySnapshotCard } from "./mastery-snapshot-card";

export default function DashboardPage() {
  const router = useRouter();
  const [items, setItems] = useState<NoteListItemResponse[]>([]);
  const [recentNoteMetaById, setRecentNoteMetaById] = useState<Record<string, { lastReviewedAt: string | null; quizCount: number | null }>>({});
  const [greetingName, setGreetingName] = useState("there");
  const [todayFocus, setTodayFocus] = useState<TodayFocusResponse | null>(null);
  const [masterySnapshot, setMasterySnapshot] = useState<MasterySnapshotResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [contentVisible, setContentVisible] = useState(false);
  const [showWelcomeMessage, setShowWelcomeMessage] = useState(false);

  const loadDashboard = useCallback(async () => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const notes = await listNotes();
      setItems(notes);

      const recentStudyPackNotes = [...notes]
        .sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
        .slice(0, 4)
        .filter((note) => note.studyPackStatus === "STUDY_PACK_READY" && Boolean(note.studyPackId));

      if (recentStudyPackNotes.length > 0) {
        const entries = await Promise.all(
          recentStudyPackNotes.map(async (note) => {
            const quickReviewResult = await getQuickReviewPerformanceSummary(note.id).catch(() => null);
            return [
              note.id,
              {
                lastReviewedAt: quickReviewResult?.lastReviewedAt ?? null,
                quizCount: note.quizCount,
              },
            ] as const;
          }),
        );
        setRecentNoteMetaById(Object.fromEntries(entries));
      } else {
        setRecentNoteMetaById({});
      }

      const [meResult, todayFocusResult, masterySnapshotResult] = await Promise.allSettled([
        getMe(),
        getTodayFocus(),
        getMasterySnapshot(),
      ]);

      if (meResult.status === "fulfilled") {
        const preferredName = meResult.value.firstName?.trim()
          || meResult.value.displayName?.trim()
          || "there";
        setGreetingName(preferredName);
      }
      setTodayFocus(todayFocusResult.status === "fulfilled" ? todayFocusResult.value : null);
      setMasterySnapshot(masterySnapshotResult.status === "fulfilled" ? masterySnapshotResult.value : null);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load your notes.";
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    void loadDashboard();
  }, [loadDashboard]);

  useEffect(() => {
    if (loading) {
      setContentVisible(false);
      return;
    }
    const timer = setTimeout(() => setContentVisible(true), 20);
    return () => clearTimeout(timer);
  }, [loading]);

  useEffect(() => {
    const authUser = getAuthUser();
    if (!authUser?.emailVerifiedAt) {
      setShowWelcomeMessage(false);
      return;
    }

    const welcomeStorageKey = `notelib-dashboard-welcome-shown-${authUser.id}`;
    const alreadyShown = window.localStorage.getItem(welcomeStorageKey) === "1";
    setShowWelcomeMessage(!alreadyShown);
  }, []);

  const dismissWelcomeMessage = useCallback(() => {
    const authUser = getAuthUser();
    if (authUser) {
      const welcomeStorageKey = `notelib-dashboard-welcome-shown-${authUser.id}`;
      window.localStorage.setItem(welcomeStorageKey, "1");
    }
    setShowWelcomeMessage(false);
  }, []);

  const recentNotes = useMemo(
    () => [...items]
      .sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
      .slice(0, 4),
    [items],
  );
  const totalQuizQuestions = useMemo(
    () => items.reduce((sum, item) => sum + (item.quizCount ?? 0), 0),
    [items],
  );

  return (
    <div className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <DashboardHero greetingName={greetingName} />

      {loading ? (
        <DashboardLoading />
      ) : error ? (
        <div
          className="space-y-6"
          style={{ opacity: contentVisible ? 1 : 0, transition: "opacity 220ms ease-out" }}
        >
          <DashboardError message={error} onRetry={loadDashboard} />
        </div>
      ) : (
        <div
          className="space-y-6"
          style={{ opacity: contentVisible ? 1 : 0, transition: "opacity 220ms ease-out" }}
        >
          {showWelcomeMessage ? (
            <Card className="space-y-3 p-4 sm:p-6">
              <p className="text-sm text-foreground/80">
                Welcome to NoteLib! Start by creating a note, then generate your first Study Pack.
              </p>
              <div className="flex flex-col gap-2 sm:flex-row">
                <Link href="/notes/new" className="w-full sm:w-auto">
                  <Button type="button" className="w-full sm:w-auto">
                    Create Note
                  </Button>
                </Link>
                <Button
                  type="button"
                  variant="outline"
                  className="w-full sm:w-auto"
                  onClick={dismissWelcomeMessage}
                >
                  Dismiss
                </Button>
              </div>
            </Card>
          ) : null}
          {todayFocus ? <TodayFocusCard focus={todayFocus} /> : null}
          {items.length === 0 ? (
            <DashboardEmpty />
          ) : (
            <StudyPackGrid notes={recentNotes} totalNotes={items.length} recentNoteMetaById={recentNoteMetaById} />
          )}
          <MasterySnapshotCard snapshot={masterySnapshot} />
          <DashboardStats notes={items} totalQuizQuestions={totalQuizQuestions} />
        </div>
      )}
    </div>
  );
}
