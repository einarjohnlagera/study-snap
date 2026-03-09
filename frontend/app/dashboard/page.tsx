"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { deleteMyStudyPack, listMyStudyPacks, type StudyPackListItemResponse } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { DashboardHero } from "./dashboard-hero";
import { ContinueSpotlight } from "./continue-spotlight";
import { DashboardStats } from "./dashboard-stats";
import { StudyPackGrid } from "./study-pack-grid";
import { DashboardLoading } from "./dashboard-loading";
import { DashboardEmpty } from "./dashboard-empty";
import { DashboardError } from "./dashboard-error";
import { StudyConsistencyCard } from "./study-consistency-card";

export default function DashboardPage() {
  const router = useRouter();
  const [items, setItems] = useState<StudyPackListItemResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [contentVisible, setContentVisible] = useState(false);

  const loadStudyPacks = useCallback(async () => {
    const authUser = getAuthUser();
    if (!authUser) {
      setError("Please log in to access your dashboard.");
      setLoading(false);
      return;
    }
    if (!authUser.emailVerifiedAt) {
      router.replace("/verify-email");
      return;
    }
    if (!authUser.profileType) {
      router.replace("/onboarding");
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const data = await listMyStudyPacks();
      setItems(data);
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
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not delete the Study Pack.";
      setError(message);
    }
  };

  const latestStudyPack = useMemo(() => (items.length > 0 ? items[0] : null), [items]);

  return (
    <div className="mx-auto w-full max-w-5xl space-y-6 px-6 py-10">
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
          {latestStudyPack ? <ContinueSpotlight latestStudyPack={latestStudyPack} /> : null}
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
