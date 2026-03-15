"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  getMe,
  getContinueStudyingRecommendation,
  getMasterySnapshot,
  getStudyEngagement,
  getTodayFocus,
  listMyStudyPacks,
  type ContinueStudyingResponse,
  type MasterySnapshotResponse,
  type PlanType,
  type StudyEngagementResponse,
  type StudyPackListItemResponse,
  type TodayFocusResponse,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { getCurrentMonthStudyPackUsage, getMonthlyStudyPackLimit } from "@/lib/plans";
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
import { PlanUsageCard } from "./plan-usage-card";
import { MasterySnapshotCard } from "./mastery-snapshot-card";

export default function DashboardPage() {
  const router = useRouter();
  const [items, setItems] = useState<StudyPackListItemResponse[]>([]);
  const [planType, setPlanType] = useState<PlanType>(() => getAuthUser()?.planType ?? "FREE");
  const [greetingName, setGreetingName] = useState("there");
  const [continueRecommendation, setContinueRecommendation] = useState<ContinueStudyingResponse | null>(null);
  const [todayFocus, setTodayFocus] = useState<TodayFocusResponse | null>(null);
  const [studyEngagement, setStudyEngagement] = useState<StudyEngagementResponse | null>(null);
  const [masterySnapshot, setMasterySnapshot] = useState<MasterySnapshotResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [contentVisible, setContentVisible] = useState(false);

  const loadStudyPacks = useCallback(async () => {
    if (!requireVerifiedOnboardedUser(router)) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const data = await listMyStudyPacks();
      setItems(data);
      const [meResult, continueResult, todayFocusResult, engagementResult, masterySnapshotResult] = await Promise.allSettled([
        getMe(),
        getContinueStudyingRecommendation(),
        getTodayFocus(),
        getStudyEngagement(),
        getMasterySnapshot(),
      ]);
      if (meResult.status === "fulfilled") {
        setPlanType(meResult.value.planType);
        const preferredName = meResult.value.firstName?.trim()
          || meResult.value.displayName?.trim()
          || "there";
        setGreetingName(preferredName);
      }
      setContinueRecommendation(continueResult.status === "fulfilled" ? continueResult.value : null);
      setTodayFocus(todayFocusResult.status === "fulfilled" ? todayFocusResult.value : null);
      setStudyEngagement(engagementResult.status === "fulfilled" ? engagementResult.value : null);
      setMasterySnapshot(masterySnapshotResult.status === "fulfilled" ? masterySnapshotResult.value : null);
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

  const hasContinueRecommendation = useMemo(
    () => Boolean(continueRecommendation?.studyPackId),
    [continueRecommendation],
  );
  const usedThisMonth = useMemo(() => getCurrentMonthStudyPackUsage(items), [items]);
  const monthlyStudyPackLimit = useMemo(() => getMonthlyStudyPackLimit(planType), [planType]);
  const isFreePlan = planType === "FREE";
  const recentStudyPacks = useMemo(() => items.slice(0, 4), [items]);

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
          <DashboardError message={error} onRetry={loadStudyPacks} />
        </div>
      ) : (
        <div
          className="space-y-6"
          style={{ opacity: contentVisible ? 1 : 0, transition: "opacity 220ms ease-out" }}
        >
          {isFreePlan ? (
            <PlanUsageCard usedThisMonth={usedThisMonth} monthlyLimit={monthlyStudyPackLimit} />
          ) : null}
          {todayFocus ? <TodayFocusCard focus={todayFocus} /> : null}
          {studyEngagement ? <StudyConsistencyCard engagement={studyEngagement} /> : null}
          <MasterySnapshotCard snapshot={masterySnapshot} />
          {hasContinueRecommendation && continueRecommendation ? (
            <ContinueSpotlight recommendation={continueRecommendation} />
          ) : null}
          <DashboardStats studyPacks={items} />
          {items.length === 0 ? (
            <DashboardEmpty />
          ) : (
            <StudyPackGrid studyPacks={recentStudyPacks} totalStudyPacks={items.length} />
          )}
        </div>
      )}
    </div>
  );
}
