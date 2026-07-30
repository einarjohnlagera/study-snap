"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { PublishedPlansPageClient } from "@/app/collections/published/published-plans-page-client";
import { PageHeader } from "@/components/page-header";
import { PublicLibraryPageClient } from "@/components/notes/public-library-page-client";
import { Card } from "@/components/ui/card";
import { trackAnalyticsEvent } from "@/lib/api";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { cn } from "@/lib/utils";

const EXPLORE_PATH = "/explore";
const EXPLORE_TAB_QUERY_PARAM = "tab";

type ExploreTab = "notes" | "review-sets";

function resolveExploreTab(value: string | null): ExploreTab {
  return value === "notes" ? "notes" : "review-sets";
}

export function ExplorePageClient() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [routeReady, setRouteReady] = useState(false);
  const activeTab = resolveExploreTab(searchParams.get(EXPLORE_TAB_QUERY_PARAM));
  const tabs = useMemo<Array<{ id: ExploreTab; label: string }>>(() => [
    { id: "review-sets", label: "Review Sets" },
    { id: "notes", label: "Notes" },
  ], []);

  useEffect(() => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
    void Promise.resolve().then(() => setRouteReady(true));
  }, [router]);

  const selectTab = (nextTab: ExploreTab) => {
    if (nextTab === activeTab) {
      return;
    }
    const nextParams = new URLSearchParams(searchParams.toString());
    if (nextTab === "review-sets") {
      nextParams.delete(EXPLORE_TAB_QUERY_PARAM);
    } else {
      nextParams.set(EXPLORE_TAB_QUERY_PARAM, nextTab);
    }
    const query = nextParams.toString();
    router.replace(query ? `${EXPLORE_PATH}?${query}` : EXPLORE_PATH, { scroll: false });
    void trackAnalyticsEvent({
      eventType: "EXPLORE_TAB_SWITCHED",
      metadata: { tab: nextTab },
    });
  };

  if (!routeReady) {
    return null;
  }

  return (
    <main className="mx-auto w-full max-w-6xl space-y-6 px-4 py-6 sm:px-6 sm:py-10 lg:px-8">
      <PageHeader
        eyebrow="DISCOVER"
        title="Explore"
        description="Find curated Official Review Sets and useful notes shared by the NoteLib community."
      />

      <div
        className="grid grid-cols-2 rounded-xl border border-border bg-surface-alt p-1"
        role="tablist"
        aria-label="Explore content"
      >
        {tabs.map((tab) => {
          const selected = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              id={`explore-tab-${tab.id}`}
              type="button"
              role="tab"
              aria-controls={`explore-panel-${tab.id}`}
              aria-selected={selected}
              className={cn(
                "min-h-10 rounded-lg px-3 py-2 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600",
                selected
                  ? "bg-background text-foreground shadow-sm"
                  : "text-foreground/60 hover:text-foreground",
              )}
              onClick={() => selectTab(tab.id)}
            >
              {tab.label}
            </button>
          );
        })}
      </div>

      <section
        id="explore-panel-review-sets"
        role="tabpanel"
        aria-labelledby="explore-tab-review-sets"
        hidden={activeTab !== "review-sets"}
      >
        <PublishedPlansPageClient embedded discoverySource="explore" />
      </section>

      <section
        id="explore-panel-notes"
        role="tabpanel"
        aria-labelledby="explore-tab-notes"
        hidden={activeTab !== "notes"}
      >
        <PublicLibraryPageClient embedded basePath={EXPLORE_PATH} />
      </section>

      <Card className="flex flex-col gap-3 border-blue-500/20 bg-blue-500/5 p-5 sm:flex-row sm:items-center sm:justify-between">
        <div className="space-y-1">
          <h2 className="font-semibold">Preparing for a licensure exam?</h2>
          <p className="text-sm text-foreground/70">
            Visit an Exam Hub for notes and official sets matched by course or program.
          </p>
        </div>
        <Link
          href="/exam"
          className="inline-flex min-h-10 shrink-0 items-center text-sm font-semibold text-blue-700 transition-colors hover:underline dark:text-blue-300"
        >
          Browse Exam Hubs
        </Link>
      </Card>
    </main>
  );
}
