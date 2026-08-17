"use client";

import Link from "next/link";
import { useMemo } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { PublishedPlansPageClient } from "@/app/collections/published/published-plans-page-client";
import { AnalyticsPageViewTracker } from "@/components/analytics/page-view-tracker";
import { PageHeader } from "@/components/page-header";
import { PublicLibraryPageClient } from "@/components/notes/public-library-page-client";
import { Card } from "@/components/ui/card";
import { trackAnalyticsEvent } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { getCollectionLabels } from "@/lib/collection-labels";
import {
  DISCOVERY_ADOPT_UNAVAILABLE_NOTICE,
  DISCOVERY_NOTICE_QUERY_PARAM,
} from "@/lib/discovery-intent";
import {
  EXPLORE_PATH,
  EXPLORE_SOURCE_QUERY_PARAM,
  EXPLORE_TAB_QUERY_PARAM,
  resolveExploreTab,
  type ExploreTab,
} from "@/lib/explore-url";
import { cn } from "@/lib/utils";

export function ExplorePageClient() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const activeTab = resolveExploreTab(searchParams.get(EXPLORE_TAB_QUERY_PARAM));
  const source = searchParams.get(EXPLORE_SOURCE_QUERY_PARAM);
  const authUser = useMemo(() => getAuthUser(), []);
  const collectionProfileType = authUser === null ? "STUDENT" : authUser.profileType;
  const collectionLabels = useMemo(() => getCollectionLabels(collectionProfileType), [collectionProfileType]);
  const adoptUnavailable = searchParams.get(DISCOVERY_NOTICE_QUERY_PARAM) === DISCOVERY_ADOPT_UNAVAILABLE_NOTICE;
  const pageViewMetadata = useMemo(
    () => source === null ? undefined : { source },
    [source],
  );
  // Keyed `pointerSource`, not `source` — PublicStudyPlanCard's analytics events already use
  // `source` for the discovery surface ("explore"/"exam_hub"); reusing that key here would let
  // its `{ ...discoveryMetadata, source: discoverySource }` merge silently overwrite this value.
  const discoveryMetadata = useMemo(
    () => source === null ? undefined : { pointerSource: source },
    [source],
  );
  const tabs = useMemo<Array<{ id: ExploreTab; label: string }>>(() => [
    { id: "review-sets", label: `Official ${collectionLabels.plural}` },
    { id: "notes", label: "Notes" },
  ], [collectionLabels]);

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

  return (
    <main className="mx-auto w-full max-w-6xl space-y-6 px-4 py-6 sm:px-6 sm:py-10 lg:px-8">
      <AnalyticsPageViewTracker eventType="EXPLORE_VIEWED" metadata={pageViewMetadata} />
      <PageHeader
        eyebrow="DISCOVER"
        title="Explore"
        description="Find curated Official Review Sets and useful notes shared by the NoteLib community."
      />

      {adoptUnavailable ? (
        <Card className="border-amber-500/30 bg-amber-500/10 p-4 text-sm text-foreground/75" role="status">
          {`We couldn't start that ${collectionLabels.singular.toLowerCase()} — it may no longer be available. You can browse the current catalog below.`}
        </Card>
      ) : null}

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
                "min-h-10 rounded-lg px-3 py-2 text-xs font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 sm:text-sm",
                selected
                  ? "bg-blue-600 text-white shadow-sm dark:bg-blue-500 dark:text-slate-950"
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
        <PublishedPlansPageClient embedded discoverySource="explore" discoveryMetadata={discoveryMetadata} />
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
