"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  deleteMyStudyPack,
  getContinueStudyingRecommendation,
  getTodayFocus,
  listMyStudyPacks,
  type ContinueStudyingResponse,
  type StudyPackListItemResponse,
  type TodayFocusResponse,
} from "@/lib/api";
import { requireVerifiedOnboardedUser } from "@/lib/route-guards";
import { DashboardHero } from "./dashboard-hero";
import { ContinueSpotlight } from "./continue-spotlight";
import { DashboardStats } from "./dashboard-stats";
import { StudyPackGrid } from "./study-pack-grid";
import { DashboardLoading } from "./dashboard-loading";
import { DashboardEmpty } from "./dashboard-empty";
import { DashboardError } from "./dashboard-error";
import { StudyConsistencyCard } from "./study-consistency-card";
import { TodayFocusCard } from "./today-focus-card";

export default function DashboardPage() {
  const router = useRouter();
  const [items, setItems] = useState<StudyPackListItemResponse[]>([]);
  const [continueRecommendation, setContinueRecommendation] = useState<ContinueStudyingResponse | null>(null);
  const [todayFocus, setTodayFocus] = useState<TodayFocusResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [contentVisible, setContentVisible] = useState(false);

  const loadStudyPacks = useCallback(async () => {
    if (!requireVerifiedOnboardedUser(router, {
      onUnauthenticated: () => {
        setError("Please log in to access your dashboard.");
        setLoading(false);
      },
    })) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const data = await listMyStudyPacks();
      setItems(data);
      const [continueResult, todayFocusResult] = await Promise.allSettled([
        getContinueStudyingRecommendation(),
        getTodayFocus(),
      ]);
      setContinueRecommendation(continueResult.status === "fulfilled" ? continueResult.value : null);
      setTodayFocus(todayFocusResult.status === "fulfilled" ? todayFocusResult.value : null);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load your Study Packs.";
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    void loadStudyPacks();
  }, [loadStudyPacks]);

  useEffect(() => {
    if (loading) {
      setContentVisible(false);
      return;
    }
    const timer = setTimeout(() => setContentVisible(true), 20);
    return () => clearTimeout(timer);
  }, [loading]);

  const handleDelete = async (id: string) => {
    try {
      await deleteMyStudyPack(id);
      setItems((prev) => prev.filter((item) => item.id !== id));
      const [continueResult, todayFocusResult] = await Promise.allSettled([
        getContinueStudyingRecommendation(),
        getTodayFocus(),
      ]);
      setContinueRecommendation(continueResult.status === "fulfilled" ? continueResult.value : null);
      setTodayFocus(todayFocusResult.status === "fulfilled" ? todayFocusResult.value : null);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not delete the Study Pack.";
      setError(message);
    }
  };

  const hasContinueRecommendation = useMemo(
    () => Boolean(continueRecommendation?.studyPackId),
    [continueRecommendation],
  );

  return (
    <div className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <DashboardHero />

      {loading ? (
        <DashboardLoading />
      ) : error ? (
        <div
          className="space-y-6"
          style={{ opacity: contentVisible ? 1 : 0, transition: "opacity 220ms ease-out" }}
        >
          <DashboardError message={error} onRetry={loadStudyPacks} />
        </div>
      ) : (
        <div
          className="space-y-6"
          style={{ opacity: contentVisible ? 1 : 0, transition: "opacity 220ms ease-out" }}
        >
          {todayFocus ? <TodayFocusCard focus={todayFocus} /> : null}
          {hasContinueRecommendation && continueRecommendation ? (
            <ContinueSpotlight recommendation={continueRecommendation} />
          ) : null}
          <StudyConsistencyCard />
          <DashboardStats studyPacks={items} />
          {items.length === 0 ? (
            <DashboardEmpty />
          ) : (
            <StudyPackGrid studyPacks={items} onDelete={handleDelete} />
          )}
        </div>
      )}
    </div>
  );
}
