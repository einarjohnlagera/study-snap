"use client";

import Link from "next/link";
import { ChevronDown } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { AnalyticsPageViewTracker } from "@/components/analytics/page-view-tracker";
import { PageHeader } from "@/components/page-header";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  getCreatorImpact,
  getCreatorImpactSummary,
  type CreatorImpactNoteResponse,
  type CreatorImpactPageResponse,
  type CreatorImpactSummaryResponse,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";

type LoadState = "loading" | "ready" | "error";
type SectionState = "idle" | "loading" | "ready" | "error";

function ImpactNoteCard({ note }: Readonly<{ note: CreatorImpactNoteResponse }>) {
  return (
    <div className="rounded-2xl border border-border bg-background/70 p-4">
      <p className="line-clamp-2 text-sm font-semibold">{note.title?.trim() || "Untitled note"}</p>
      {note.distinctLearnersHelped > 0 ? (
        <>
          <p className="mt-3 text-2xl font-semibold text-emerald-700 dark:text-emerald-300">
            {note.distinctLearnersHelped.toLocaleString()}
          </p>
          <p className="text-xs font-medium text-foreground/75">
            {note.distinctLearnersHelped === 1 ? "learner helped" : "learners helped"}
          </p>
        </>
      ) : null}
      <p className="mt-2 text-xs text-foreground/50">
        {note.viewCount.toLocaleString()} {note.viewCount === 1 ? "page view" : "page views"}
        {" · "}
        {note.copyCount.toLocaleString()} {note.copyCount === 1 ? "copy" : "copies"}
      </p>
    </div>
  );
}

function getLoadMoreLabel(loading: boolean, failed: boolean): string {
  if (loading) {
    return "Loading...";
  }
  return failed ? "Retry" : "Load more";
}

function NoteGrid({ notes }: Readonly<{ notes: CreatorImpactNoteResponse[] }>) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      {notes.map((note) => <ImpactNoteCard key={note.noteId} note={note} />)}
    </div>
  );
}

export function ImpactPageClient() {
  const router = useRouter();
  const impactRequestIdRef = useRef(0);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [summary, setSummary] = useState<CreatorImpactSummaryResponse | null>(null);
  const [impacted, setImpacted] = useState<CreatorImpactPageResponse | null>(null);
  const [impactedNotes, setImpactedNotes] = useState<CreatorImpactNoteResponse[]>([]);
  const [impactedPage, setImpactedPage] = useState(0);
  const [loadingMoreImpacted, setLoadingMoreImpacted] = useState(false);
  const [impactedPageFailed, setImpactedPageFailed] = useState(false);
  const [zeroExpanded, setZeroExpanded] = useState(false);
  const [zeroState, setZeroState] = useState<SectionState>("idle");
  const [zeroNotes, setZeroNotes] = useState<CreatorImpactNoteResponse[]>([]);
  const [zeroPage, setZeroPage] = useState(0);

  const loadInitial = useCallback(async () => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
    const requestId = impactRequestIdRef.current + 1;
    impactRequestIdRef.current = requestId;
    setLoadState("loading");
    try {
      const [nextSummary, nextImpacted] = await Promise.all([
        getCreatorImpactSummary(),
        getCreatorImpact(true),
      ]);
      if (impactRequestIdRef.current !== requestId) {
        return;
      }
      setSummary(nextSummary);
      setImpacted(nextImpacted);
      setImpactedNotes(nextImpacted.notes);
      setImpactedPage(0);
      setImpactedPageFailed(false);
      setLoadState("ready");
    } catch {
      if (impactRequestIdRef.current === requestId) {
        setLoadState("error");
      }
    }
  }, [router]);

  useEffect(() => {
    void loadInitial();
    return () => {
      impactRequestIdRef.current += 1;
    };
  }, [loadInitial]);

  const loadZeroPage = useCallback(async (page: number) => {
    setZeroState("loading");
    try {
      const response = await getCreatorImpact(false, page);
      setZeroNotes((current) => page === 0 ? response.notes : [...current, ...response.notes]);
      setZeroPage(page);
      setZeroState("ready");
    } catch {
      setZeroPage(page);
      setZeroState("error");
    }
  }, []);

  const toggleZeroImpact = () => {
    const nextExpanded = !zeroExpanded;
    setZeroExpanded(nextExpanded);
    if (nextExpanded && zeroState === "idle") {
      void loadZeroPage(0);
    }
  };

  const loadMoreImpacted = async () => {
    const nextPage = impactedPage + 1;
    setLoadingMoreImpacted(true);
    setImpactedPageFailed(false);
    try {
      const response = await getCreatorImpact(true, nextPage);
      setImpactedNotes((current) => [...current, ...response.notes]);
      setImpactedPage(nextPage);
    } catch {
      setImpactedPageFailed(true);
    } finally {
      setLoadingMoreImpacted(false);
    }
  };

  if (loadState === "loading") {
    return <main className="mx-auto w-full max-w-5xl px-4 py-6 sm:px-6 sm:py-10">Loading your impact...</main>;
  }

  if (loadState === "error" || !summary || !impacted) {
    return (
      <main className="mx-auto w-full max-w-5xl space-y-4 px-4 py-6 sm:px-6 sm:py-10">
        <PageHeader eyebrow="PRIVATE TO YOU" title="Your Impact" description="See how your published notes are helping people learn." />
        <Card className="space-y-3 p-4 sm:p-6">
          <p className="font-medium">Could not load your impact.</p>
          <Button type="button" variant="outline" onClick={() => void loadInitial()}>Retry</Button>
        </Card>
      </main>
    );
  }

  const hasMoreImpacted = impactedNotes.length < impacted.totalImpacted;
  const hasMoreZero = zeroNotes.length < impacted.totalZeroImpact;

  return (
    <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <AnalyticsPageViewTracker
        eventType="KNOWLEDGE_IMPACT_DASHBOARD_VIEWED"
        entityId={getAuthUser()?.id}
      />
      <PageHeader
        eyebrow="PRIVATE TO YOU"
        title="Your Impact"
        description="See how your published notes are helping people learn."
      />

      {summary.publicNoteCount === 0 ? (
        <Card className="space-y-3 border-emerald-500/20 bg-emerald-500/5 p-5 sm:p-6">
          <h2 className="text-xl font-semibold">Share what you know.</h2>
          <p className="text-sm text-foreground/65">Publish a note when you&apos;re ready to make it discoverable.</p>
          <Link href="/library" className="inline-flex text-sm font-semibold text-emerald-700 hover:underline dark:text-emerald-300">
            Go to Library →
          </Link>
        </Card>
      ) : (
        <>
          <Card className="space-y-3 border-emerald-500/20 bg-emerald-500/5 p-5 sm:p-6">
            {summary.distinctLearnersHelped > 0 ? (
              <>
                <p className="text-4xl font-semibold tracking-tight text-emerald-700 dark:text-emerald-300">
                  {summary.distinctLearnersHelped.toLocaleString()}
                </p>
                <p className="text-sm font-semibold">
                  {summary.distinctLearnersHelped === 1 ? "learner helped" : "distinct learners helped"}
                </p>
              </>
            ) : (
              <>
                <h2 className="text-xl font-semibold">Your shared notes are ready to help someone learn.</h2>
                <p className="text-sm text-foreground/65">
                  Impact appears when learners study from your published notes.
                </p>
              </>
            )}
            <p className="text-xs leading-relaxed text-foreground/55">
              Each learner is counted once in this headline, even if they studied more than one of your notes.
              Per-note learner counts can therefore add up to more than the headline total.
            </p>
          </Card>

          {impacted.totalImpacted > 0 ? (
            <section aria-labelledby="impacted-notes-heading" className="space-y-3">
              <h2 id="impacted-notes-heading" className="text-lg font-semibold">
                Notes helping learners · {impacted.totalImpacted.toLocaleString()}
              </h2>
              <NoteGrid notes={impactedNotes} />
              {impactedPageFailed ? (
                <p className="text-sm font-medium">Could not load more notes helping learners.</p>
              ) : null}
              {hasMoreImpacted ? (
                <Button type="button" variant="outline" disabled={loadingMoreImpacted} onClick={() => void loadMoreImpacted()}>
                  {getLoadMoreLabel(loadingMoreImpacted, impactedPageFailed)}
                </Button>
              ) : null}
            </section>
          ) : null}

          {impacted.totalZeroImpact > 0 ? (
            <section aria-labelledby="other-notes-heading" className="rounded-2xl border border-border bg-card">
              <button
                type="button"
                aria-expanded={zeroExpanded}
                aria-controls="other-published-notes"
                onClick={toggleZeroImpact}
                className="flex w-full items-center justify-between gap-3 rounded-2xl p-4 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring sm:p-5"
              >
                <span>
                  <span id="other-notes-heading" className="block font-semibold">Other published notes</span>
                  <span className="mt-1 block text-sm text-foreground/60">These haven&apos;t recorded learning activity yet.</span>
                </span>
                <ChevronDown aria-hidden="true" className={`size-5 shrink-0 transition-transform ${zeroExpanded ? "rotate-180" : ""}`} />
              </button>
              {zeroExpanded ? (
                <div id="other-published-notes" className="space-y-3 border-t border-border p-4 sm:p-5">
                  {zeroState === "loading" && zeroNotes.length === 0 ? <p className="text-sm">Loading notes...</p> : null}
                  {zeroState === "error" ? (
                    <div className="space-y-2">
                      <p className="text-sm font-medium">Could not load your other published notes.</p>
                      <Button type="button" variant="outline" onClick={() => void loadZeroPage(zeroPage)}>Retry</Button>
                    </div>
                  ) : null}
                  {zeroNotes.length > 0 ? <NoteGrid notes={zeroNotes} /> : null}
                  {zeroState === "ready" && hasMoreZero ? (
                    <Button type="button" variant="outline" onClick={() => void loadZeroPage(zeroPage + 1)}>Load more</Button>
                  ) : null}
                </div>
              ) : null}
            </section>
          ) : null}
        </>
      )}
    </main>
  );
}
