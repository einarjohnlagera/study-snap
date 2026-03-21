"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  getMe,
  getMasterySnapshot,
  getMyStudyPack,
  getTodayFocus,
  getQuickReviewPerformanceSummary,
  listMyStudyPacks,
  listNotes,
  type MasterySnapshotResponse,
  type NoteListItemResponse,
  type StudyPackListItemResponse,
  type TodayFocusResponse,
} from "@/lib/api";
import { requireVerifiedOnboardedUser } from "@/lib/route-guards";
import { DashboardHero } from "./dashboard-hero";
import { DashboardStats } from "./dashboard-stats";
import { StudyPackGrid } from "./study-pack-grid";
import { DashboardLoading } from "./dashboard-loading";
import { DashboardEmpty } from "./dashboard-empty";
import { DashboardError } from "./dashboard-error";
import { TodayFocusCard } from "./today-focus-card";
import { MasterySnapshotCard } from "./mastery-snapshot-card";
import Link from "next/link";

export default function DashboardPage() {
  const router = useRouter();
  const [items, setItems] = useState<NoteListItemResponse[]>([]);
  const [studyPackItems, setStudyPackItems] = useState<StudyPackListItemResponse[]>([]);
  const [recentNoteMetaById, setRecentNoteMetaById] = useState<Record<string, { lastReviewedAt: string | null; quizCount: number | null }>>({});
  const [greetingName, setGreetingName] = useState("there");
  const [todayFocus, setTodayFocus] = useState<TodayFocusResponse | null>(null);
  const [masterySnapshot, setMasterySnapshot] = useState<MasterySnapshotResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [contentVisible, setContentVisible] = useState(false);

  const loadDashboard = useCallback(async () => {
    if (!requireVerifiedOnboardedUser(router)) {
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
            const studyPackId = note.studyPackId as string;
            const [reviewResult, studyPackResult] = await Promise.allSettled([
              getQuickReviewPerformanceSummary(studyPackId),
              getMyStudyPack(studyPackId),
            ]);
            return [
              note.id,
              {
                lastReviewedAt: reviewResult.status === "fulfilled" ? reviewResult.value.lastReviewedAt : null,
                quizCount: studyPackResult.status === "fulfilled" ? studyPackResult.value.quiz.length : null,
              },
            ] as const;
          }),
        );
        setRecentNoteMetaById(Object.fromEntries(entries));
      } else {
        setRecentNoteMetaById({});
      }

      const [meResult, todayFocusResult, masterySnapshotResult, studyPackResult] = await Promise.allSettled([
        getMe(),
        getTodayFocus(),
        getMasterySnapshot(),
        listMyStudyPacks(),
      ]);

      if (meResult.status === "fulfilled") {
        const preferredName = meResult.value.firstName?.trim()
          || meResult.value.displayName?.trim()
          || "there";
        setGreetingName(preferredName);
      }
      setTodayFocus(todayFocusResult.status === "fulfilled" ? todayFocusResult.value : null);
      setMasterySnapshot(masterySnapshotResult.status === "fulfilled" ? masterySnapshotResult.value : null);
      setStudyPackItems(studyPackResult.status === "fulfilled" ? studyPackResult.value : []);
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

  const recentNotes = useMemo(
    () => [...items]
      .sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
      .slice(0, 4),
    [items],
  );
  const totalQuizQuestions = useMemo(
    () => studyPackItems.reduce((sum, item) => sum + item.quizCount, 0),
    [studyPackItems],
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
          {todayFocus ? <TodayFocusCard focus={todayFocus} /> : null}
          {items.length === 0 ? (
            <DashboardEmpty />
          ) : (
            <StudyPackGrid notes={recentNotes} totalNotes={items.length} recentNoteMetaById={recentNoteMetaById} />
          )}
          <MasterySnapshotCard snapshot={masterySnapshot} />
          <DashboardStats notes={items} totalQuizQuestions={totalQuizQuestions} />
          <section>
            <Link href="/library" className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400">
              View All in Library &rarr;
            </Link>
          </section>
        </div>
      )}
    </div>
  );
}
