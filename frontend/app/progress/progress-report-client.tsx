"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { BackLink } from "@/components/ui/back-link";
import { HelpLink } from "@/components/ui/help-link";
import { PageHeader } from "@/components/page-header";
import {
  ApiRequestError,
  getCollectionGoal,
  getMe,
  getProgressReport,
  getPlanReadiness,
  listCollections,
  trackAnalyticsEvent,
  type GoalCollectionDetailResponse,
  type GoalSummaryResponse,
  type NoteCollectionSummary,
  type PlanReadinessResponse,
  type ProgressReportResponse,
  type SubjectProgressEntry,
} from "@/lib/api";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { getExamSlugForCourseProgram } from "@/lib/exam-hub-config";
import { getAuthUser, type AuthUser } from "@/lib/auth";
import { getCollectionLabels } from "@/lib/collection-labels";
import { ReadinessBar, ReadinessSummary } from "@/components/readiness/readiness-summary";

type LoadState = "loading" | "ready" | "error" | "not-found";

const MASTERY_QUARTER_THRESHOLD = 25;
const MASTERY_HALF_THRESHOLD = 50;
const MASTERY_STRONG_THRESHOLD = 70;
const MASTERY_COMPLETE_THRESHOLD = 100;

type GoalMilestone = {
  label: string;
  reached: (goalSummary: GoalSummaryResponse) => boolean;
};

export const MILESTONES: GoalMilestone[] = [
  {
    label: "First concept mastered",
    reached: (goalSummary) => goalSummary.masteredConcepts >= 1,
  },
  {
    label: "25% mastered",
    reached: (goalSummary) => goalSummary.masteryPercentage >= MASTERY_QUARTER_THRESHOLD,
  },
  {
    label: "All concepts reviewed",
    reached: (goalSummary) => (goalSummary.notPracticedConcepts ?? 1) === 0 && goalSummary.totalConcepts > 0,
  },
  {
    label: "50% mastered",
    reached: (goalSummary) => goalSummary.masteryPercentage >= MASTERY_HALF_THRESHOLD,
  },
  {
    label: "70% mastered",
    reached: (goalSummary) => goalSummary.masteryPercentage >= MASTERY_STRONG_THRESHOLD,
  },
  {
    label: "All concepts mastered",
    reached: (goalSummary) => goalSummary.masteryPercentage >= MASTERY_COMPLETE_THRESHOLD,
  },
];

function ProgressHeader() {
  return (
    <div className="space-y-4">
      <BackLink href="/dashboard" label="Dashboard" />
      <PageHeader
        eyebrow="MY PROGRESS"
        title="My Progress"
        description="Concept mastery across your subjects, based on your recent practice."
      />
    </div>
  );
}

function GoalSummaryHeader({ goalSummary }: Readonly<{ goalSummary: GoalSummaryResponse }>) {
  const eyebrowLabel = goalSummary.goalType === "SUBJECT_FOCUS" ? "Study Focus" : `${goalSummary.goalName} Goal`;

  return (
    <Card className="overflow-hidden border-blue-500/25 bg-linear-to-br from-blue-500/10 via-background to-emerald-500/10 p-4 sm:p-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
            {eyebrowLabel}
          </p>
          <div className="space-y-1">
            <h2 className="text-xl font-semibold tracking-tight sm:text-2xl">{goalSummary.goalLabel}</h2>
            <p className="text-sm text-foreground/70">
              {goalSummary.masteredConcepts} of {goalSummary.totalConcepts} goal concepts mastered
            </p>
          </div>
        </div>
        <div className="flex flex-col items-start gap-2 sm:items-end">
          <div className="text-left sm:text-right">
            <p className="text-4xl font-semibold tracking-tight text-foreground">{goalSummary.masteryPercentage}%</p>
            <p className="text-xs font-medium uppercase tracking-wide text-foreground/55">mastered</p>
          </div>
          <Link
            href="/profile#study-focus"
            className="text-xs font-medium text-blue-700 hover:underline dark:text-blue-300"
          >
            Edit in Profile
          </Link>
        </div>
      </div>
    </Card>
  );
}

function getMilestoneMarkerClasses(reached: boolean, isNext: boolean): string {
  if (reached) {
    return "border-blue-600 bg-blue-600";
  }
  if (isNext) {
    return "border-blue-600 bg-background ring-2 ring-blue-500 ring-offset-2 ring-offset-background";
  }
  return "border-muted bg-muted";
}

function GoalMilestonesCard({ goalSummary }: Readonly<{ goalSummary: GoalSummaryResponse }>) {
  const milestoneStates = MILESTONES.map((milestone) => ({
    ...milestone,
    reached: milestone.reached(goalSummary),
  }));
  const nextMilestoneIndex = milestoneStates.findIndex((milestone) => !milestone.reached);
  const reachedCount = milestoneStates.filter((milestone) => milestone.reached).length;
  const progressWidth = (reachedCount / MILESTONES.length) * 100;

  return (
    <Card className="space-y-5 p-4 sm:p-6">
      <div className="flex items-start justify-between gap-3">
        <div className="space-y-1">
          <h2 className="text-lg font-semibold tracking-tight">Goal Milestones</h2>
          <p className="text-sm text-foreground/65">
            Checkpoints toward goal mastery. They fill as you master concepts — and a concept stays mastered only
            while it&apos;s fresh, so review keeps your progress up.
          </p>
        </div>
        <HelpLink guideId="progress-focus" label="How milestones work" className="mt-1 shrink-0" />
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {milestoneStates.map((milestone, index) => {
          const isNext = index === nextMilestoneIndex;
          return (
            <div key={milestone.label} className="flex items-center gap-3">
              <span
                aria-hidden="true"
                className={`size-3.5 shrink-0 rounded-full border ${getMilestoneMarkerClasses(milestone.reached, isNext)}`}
              />
              <span className={`text-sm ${milestone.reached || isNext ? "text-foreground" : "text-foreground/50"}`}>
                {milestone.label}
              </span>
            </div>
          );
        })}
      </div>

      <div
        role="progressbar"
        aria-label="Goal milestone progress"
        aria-valuemin={0}
        aria-valuemax={MILESTONES.length}
        aria-valuenow={reachedCount}
        className="h-3 overflow-hidden rounded-full bg-muted"
      >
        <div
          className="h-full rounded-full bg-blue-600 transition-all dark:bg-blue-400"
          style={{ width: `${progressWidth}%` }}
        />
      </div>
    </Card>
  );
}

function NextStudyCard({ goalSummary }: Readonly<{ goalSummary: GoalSummaryResponse }>) {
  if (goalSummary.goalType === "SUBJECT_FOCUS") {
    const weakestGoalSubject = goalSummary.weakestGoalSubject;
    const href = weakestGoalSubject
      ? `/public/library?subject=${encodeURIComponent(weakestGoalSubject)}`
      : "/public/library";
    const message = weakestGoalSubject
      ? `Focus on ${weakestGoalSubject} — you have concepts left to practice.`
      : "Browse community notes to build your knowledge.";
    const linkLabel = weakestGoalSubject
      ? `Browse ${weakestGoalSubject} notes in the community`
      : "Browse notes in the community";

    return (
      <Card className="space-y-3 p-4 sm:p-6">
        <div className="space-y-1">
          <h2 className="text-lg font-semibold tracking-tight">What to study next</h2>
          <p className="text-sm leading-relaxed text-foreground/75">{message}</p>
        </div>
        <Link href={href} className="inline-flex text-sm font-medium text-blue-700 hover:underline dark:text-blue-300">
          {linkLabel} &rarr;
        </Link>
      </Card>
    );
  }

  const examSlug = goalSummary.goalType === "EXAM"
    ? goalSummary.studyGoal
    : getExamSlugForCourseProgram(goalSummary.studyGoal);
  const href = examSlug
    ? `/exam/${examSlug}`
    : `/public/library?courseProgram=${encodeURIComponent(goalSummary.studyGoal)}`;
  const message = goalSummary.weakestGoalSubject
    ? `Focus on ${goalSummary.weakestGoalSubject} — you have concepts left to practice.`
    : `Browse community ${goalSummary.goalName} notes to build your knowledge.`;
  const linkLabel = examSlug
    ? `Browse ${goalSummary.goalName} notes`
    : `Browse ${goalSummary.goalName} notes in the community`;

  return (
    <Card className="space-y-3 p-4 sm:p-6">
      <div className="space-y-1">
        <h2 className="text-lg font-semibold tracking-tight">What to study next</h2>
        <p className="text-sm leading-relaxed text-foreground/75">{message}</p>
      </div>
      <Link href={href} className="inline-flex text-sm font-medium text-blue-700 hover:underline dark:text-blue-300">
        {linkLabel} &rarr;
      </Link>
    </Card>
  );
}

function SetStudyFocusCard({ subjects }: Readonly<{ subjects: SubjectProgressEntry[] }>) {
  const focusSubjects = subjects.slice(0, 5);

  return (
    <Card className="space-y-4 border-dashed p-4 sm:p-5">
      <div className="space-y-1">
        <h2 className="text-lg font-semibold tracking-tight">Set your study focus</h2>
        <p className="text-sm text-foreground/70">
          Pick subjects to track mastery toward. Your current subjects:
        </p>
      </div>
      <div className="flex flex-wrap gap-2">
        {focusSubjects.map((subject) => (
          <Link
            key={subject.subject}
            href="/profile#study-focus"
            className="rounded-full border border-blue-500/30 bg-blue-600/10 px-3 py-1 text-xs font-medium text-blue-700 hover:bg-blue-600/15 dark:text-blue-300"
          >
            {subject.subject}
          </Link>
        ))}
      </div>
      <Link href="/profile#study-focus" className="inline-flex text-sm font-medium text-blue-700 hover:underline dark:text-blue-300">
        Or set from Profile &rarr;
      </Link>
    </Card>
  );
}

function PlanPicker({
  collections,
  selectedCollectionId,
  collectionsState,
  onChange,
}: Readonly<{
  collections: NoteCollectionSummary[];
  selectedCollectionId: string | null;
  collectionsState: "loading" | "ready" | "error";
  onChange: (collectionId: string | null) => void;
}>) {
  const leafCollections = useMemo(
    () => collections.filter((collection) => collection.childCount === 0),
    [collections],
  );
  const selectedCollection = selectedCollectionId
    ? collections.find((collection) => collection.id === selectedCollectionId)
    : null;
  const selectedLeafCollection = selectedCollectionId
    ? leafCollections.find((collection) => collection.id === selectedCollectionId)
    : null;

  return (
    <Card className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5">
      <div className="space-y-1">
        <label htmlFor="progress-plan-picker" className="text-sm font-semibold text-foreground">
          Progress view
        </label>
        <p className="text-sm text-foreground/65">
          Switch between all subjects and one saved study plan.
        </p>
      </div>
      <div className="flex flex-col gap-1 sm:min-w-72">
        <select
          id="progress-plan-picker"
          value={selectedCollectionId ?? ""}
          onChange={(event) => onChange(event.target.value || null)}
          className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground shadow-xs focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <option value="">All subjects</option>
          {selectedCollectionId && !selectedLeafCollection ? (
            <option value={selectedCollectionId}>{selectedCollection?.title ?? "Selected plan"}</option>
          ) : null}
          {leafCollections.map((collection) => (
            <option key={collection.id} value={collection.id}>
              {collection.title}
            </option>
          ))}
        </select>
        {collectionsState === "loading" ? (
          <span className="text-xs text-foreground/50">Loading study plans...</span>
        ) : null}
        {collectionsState === "error" ? (
          <span className="text-xs text-rose-600 dark:text-rose-400">
            Could not load study plans for the picker.
          </span>
        ) : null}
      </div>
    </Card>
  );
}

function ProgressContent({
  report,
  state,
}: Readonly<{
  report: ProgressReportResponse | null;
  state: LoadState;
}>) {
  if (state === "error") {
    return (
      <Card className="p-4 text-sm text-foreground/75 sm:p-6">
        Could not load your progress report. Try refreshing.
      </Card>
    );
  }

  if (state === "loading") {
    return (
      <Card className="p-4 text-sm text-foreground/70 sm:p-6">
        Loading your progress report...
      </Card>
    );
  }

  const subjects = [...(report?.subjects ?? [])].sort(
    (a, b) => a.masteryPercentage - b.masteryPercentage,
  );
  const goalSummary = report?.goalSummary ?? null;

  return (
    <div className="space-y-6">
      {goalSummary ? <GoalSummaryHeader goalSummary={goalSummary} /> : null}
      {!goalSummary && subjects.length > 0 ? <SetStudyFocusCard subjects={subjects} /> : null}

      {goalSummary && goalSummary.totalConcepts > 0 ? <GoalMilestonesCard goalSummary={goalSummary} /> : null}
      {goalSummary ? <NextStudyCard goalSummary={goalSummary} /> : null}

      {subjects.length === 0 ? (
        <Card className="p-4 text-sm leading-relaxed text-foreground/75 sm:p-6">
          No study packs with concepts yet. Generate a Study Pack to start tracking your progress.
        </Card>
      ) : (
        <section className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-foreground/60">
              Concept Mastery
            </h2>
            <span className="text-xs text-foreground/50">{subjects.length} {subjects.length === 1 ? "subject" : "subjects"}</span>
          </div>
          <div className="grid gap-4">
            {subjects.map((entry) => (
              <ReadinessBar key={entry.subject} entry={entry} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

function PlanReadinessContent({
  readiness,
  state,
  loadError,
  onRetry,
  labels,
}: Readonly<{
  readiness: PlanReadinessResponse | null;
  state: LoadState;
  loadError: string | null;
  onRetry: () => void;
  labels: ReturnType<typeof getCollectionLabels>;
}>) {
  if (state === "loading") {
    return (
      <Card className="space-y-4 p-6">
        <div className="h-5 w-1/2 animate-pulse rounded bg-muted" />
        <div className="h-24 w-full animate-pulse rounded bg-muted" />
      </Card>
    );
  }

  if (state === "not-found") {
    return (
      <Card className="space-y-4 p-6">
        <CardTitle>{labels.singular} not found</CardTitle>
        <CardDescription>This saved set may have been deleted or may not belong to your account.</CardDescription>
        <Link className="inline-flex text-sm font-medium text-blue-600 hover:underline dark:text-blue-400" href="/collections">
          {labels.plural}
        </Link>
      </Card>
    );
  }

  if (state === "error" || !readiness) {
    return (
      <Card className="space-y-4 p-6">
        <CardTitle>Could not load readiness</CardTitle>
        <CardDescription>{loadError ?? "Please try again."}</CardDescription>
        <Button type="button" variant="outline" onClick={onRetry}>Retry</Button>
      </Card>
    );
  }

  const notesMissingStudyPack = readiness.totalNotes - readiness.notesWithStudyPack;

  return (
    <div className="space-y-6">
      {notesMissingStudyPack > 0 ? (
        <Card className="p-4 text-sm text-foreground/70 sm:p-5">
          {notesMissingStudyPack} of {readiness.totalNotes} notes in this {labels.singular.toLowerCase()} don&apos;t have a Study Pack yet, so they aren&apos;t reflected below.{" "}
          <Link
            href={`/collections/${readiness.collectionId}`}
            className="font-medium text-blue-700 hover:underline dark:text-blue-300"
          >
            Generate Study Packs &rarr;
          </Link>
        </Card>
      ) : null}

      <ReadinessSummary
        overallReadinessPercentage={readiness.overallReadinessPercentage}
        totalConcepts={readiness.totalConcepts}
        masteredConcepts={readiness.masteredConcepts}
        dueConcepts={readiness.dueConcepts}
        notPracticedConcepts={readiness.notPracticedConcepts}
        subjects={readiness.subjects}
        emptyTitle="No readiness yet"
        emptyDescription="Generate Study Packs and practice to see readiness."
      />
    </div>
  );
}

function GoalReadinessContent({
  goalReadiness,
  state,
  loadError,
  onRetry,
  labels,
}: Readonly<{
  goalReadiness: GoalCollectionDetailResponse | null;
  state: LoadState;
  loadError: string | null;
  onRetry: () => void;
  labels: ReturnType<typeof getCollectionLabels>;
}>) {
  if (state === "loading") {
    return (
      <Card className="space-y-4 p-6">
        <div className="h-5 w-1/2 animate-pulse rounded bg-muted" />
        <div className="h-24 w-full animate-pulse rounded bg-muted" />
      </Card>
    );
  }

  if (state === "not-found") {
    return (
      <Card className="space-y-4 p-6">
        <CardTitle>{labels.goalSingular} not found</CardTitle>
        <CardDescription>This saved set may have been deleted or may not belong to your account.</CardDescription>
        <Link className="inline-flex text-sm font-medium text-blue-600 hover:underline dark:text-blue-400" href="/collections">
          {labels.plural}
        </Link>
      </Card>
    );
  }

  if (state === "error" || !goalReadiness) {
    return (
      <Card className="space-y-4 p-6">
        <CardTitle>Could not load readiness</CardTitle>
        <CardDescription>{loadError ?? "Please try again."}</CardDescription>
        <Button type="button" variant="outline" onClick={onRetry}>Retry</Button>
      </Card>
    );
  }

  return (
    <ReadinessSummary
      variant="compact"
      title={`${goalReadiness.title} readiness`}
      eyebrow={`${labels.goalSingular} readiness`}
      overallReadinessPercentage={goalReadiness.overallReadinessPercentage}
      totalConcepts={goalReadiness.totalConcepts}
      masteredConcepts={goalReadiness.masteredConcepts}
      dueConcepts={goalReadiness.dueConcepts}
      notPracticedConcepts={goalReadiness.notPracticedConcepts}
      subjects={[]}
      emptyTitle="No readiness yet"
      emptyDescription={`Add ${labels.subjectSingular.toLowerCase()}s with ready Study Packs to see this ${labels.goalSingular.toLowerCase()} readiness.`}
    />
  );
}

export function ProgressReportClient({
  initialCollectionId = null,
}: Readonly<{
  initialCollectionId?: string | null;
}>) {
  const router = useRouter();
  const [authUser] = useState<AuthUser | null>(() => getAuthUser());
  const labels = useMemo(() => getCollectionLabels(authUser?.profileType), [authUser?.profileType]);
  const [routeReady, setRouteReady] = useState(false);
  const [selectedCollectionId, setSelectedCollectionId] = useState<string | null>(initialCollectionId);
  const [collections, setCollections] = useState<NoteCollectionSummary[]>([]);
  const [collectionsState, setCollectionsState] = useState<"loading" | "ready" | "error">("loading");
  const [primaryCollectionId, setPrimaryCollectionId] = useState<string | null | undefined>(undefined);
  const [report, setReport] = useState<ProgressReportResponse | null>(null);
  const [readiness, setReadiness] = useState<PlanReadinessResponse | null>(null);
  const [goalReadiness, setGoalReadiness] = useState<GoalCollectionDetailResponse | null>(null);
  const [state, setState] = useState<LoadState>("loading");
  const [loadError, setLoadError] = useState<string | null>(null);
  const [retryKey, setRetryKey] = useState(0);
  const requestIdRef = useRef(0);
  const trackedPlanIdsRef = useRef<Set<string>>(new Set());
  const primaryDefaultResolvedRef = useRef(false);
  const applyingPrimaryDefaultRef = useRef(false);

  useEffect(() => {
    setSelectedCollectionId(initialCollectionId);
  }, [initialCollectionId]);

  useEffect(() => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
    setRouteReady(true);
  }, [router]);

  useEffect(() => {
    if (!routeReady) {
      return;
    }
    let cancelled = false;
    setCollectionsState("loading");
    listCollections()
      .then((payload) => {
        if (!cancelled) {
          setCollections(payload);
          setCollectionsState("ready");
        }
      })
      .catch(() => {
        if (!cancelled) {
          setCollectionsState("error");
        }
      });

    return () => {
      cancelled = true;
    };
  }, [routeReady]);

  useEffect(() => {
    if (!routeReady) {
      return;
    }
    let cancelled = false;
    setPrimaryCollectionId(undefined);
    getMe()
      .then((me) => {
        if (!cancelled) {
          setPrimaryCollectionId(me.primaryCollectionId ?? null);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setPrimaryCollectionId(null);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [routeReady]);

  useEffect(() => {
    if (!routeReady || primaryDefaultResolvedRef.current || primaryCollectionId === undefined) {
      return;
    }
    primaryDefaultResolvedRef.current = true;
    if (initialCollectionId) {
      return;
    }
    if (primaryCollectionId) {
      applyingPrimaryDefaultRef.current = true;
      setSelectedCollectionId(primaryCollectionId);
    }
  }, [initialCollectionId, primaryCollectionId, routeReady]);

  useEffect(() => {
    if (!routeReady) {
      return;
    }
    if (primaryCollectionId === undefined) {
      return;
    }
    if (
      applyingPrimaryDefaultRef.current
      && primaryCollectionId
      && selectedCollectionId !== primaryCollectionId
    ) {
      return;
    }

    let cancelled = false;
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    setState("loading");
    setLoadError(null);

    if (selectedCollectionId) {
      if (selectedCollectionId === primaryCollectionId) {
        applyingPrimaryDefaultRef.current = false;
        getCollectionGoal(selectedCollectionId)
          .then((payload) => {
            if (cancelled || requestIdRef.current !== requestId) {
              return;
            }
            setGoalReadiness(payload);
            setReadiness(null);
            setReport(null);
            setState("ready");
            if (!trackedPlanIdsRef.current.has(payload.collectionId)) {
              trackedPlanIdsRef.current.add(payload.collectionId);
              void trackAnalyticsEvent({
                eventType: "PLAN_READINESS_VIEWED",
                entityId: payload.collectionId,
                metadata: {
                  totalConcepts: payload.totalConcepts,
                  overallReadinessPercentage: payload.overallReadinessPercentage,
                },
              });
            }
          })
          .catch((error) => {
            if (cancelled || requestIdRef.current !== requestId) {
              return;
            }
            setGoalReadiness(null);
            setReadiness(null);
            setReport(null);
            if (error instanceof ApiRequestError && error.status === 404) {
              setState("not-found");
              return;
            }
            setLoadError(error instanceof Error ? error.message : "Could not load readiness.");
            setState("error");
          });
        return () => {
          cancelled = true;
        };
      }

      getPlanReadiness(selectedCollectionId)
        .then((payload) => {
          if (cancelled || requestIdRef.current !== requestId) {
            return;
          }
          setReadiness(payload);
          setGoalReadiness(null);
          setReport(null);
          setState("ready");
          if (!trackedPlanIdsRef.current.has(payload.collectionId)) {
            trackedPlanIdsRef.current.add(payload.collectionId);
            void trackAnalyticsEvent({
              eventType: "PLAN_READINESS_VIEWED",
              entityId: payload.collectionId,
              metadata: {
                totalNotes: payload.totalNotes,
                notesWithStudyPack: payload.notesWithStudyPack,
                totalConcepts: payload.totalConcepts,
                overallReadinessPercentage: payload.overallReadinessPercentage,
              },
            });
          }
        })
        .catch((error) => {
          if (cancelled || requestIdRef.current !== requestId) {
            return;
          }
          setReadiness(null);
          setGoalReadiness(null);
          setReport(null);
          if (error instanceof ApiRequestError && error.status === 404) {
            setState("not-found");
            return;
          }
          setLoadError(error instanceof Error ? error.message : "Could not load readiness.");
          setState("error");
        });
      return () => {
        cancelled = true;
      };
    }

    getProgressReport()
      .then((payload) => {
        if (cancelled || requestIdRef.current !== requestId) {
          return;
        }
        setReport(payload);
        setReadiness(null);
        setGoalReadiness(null);
        setState("ready");
      })
      .catch(() => {
        if (cancelled || requestIdRef.current !== requestId) {
          return;
        }
        setReport(null);
        setReadiness(null);
        setGoalReadiness(null);
        setState("error");
      });

    return () => {
      cancelled = true;
    };
  }, [primaryCollectionId, routeReady, selectedCollectionId, retryKey]);

  function handlePlanChange(collectionId: string | null) {
    applyingPrimaryDefaultRef.current = false;
    setSelectedCollectionId(collectionId);
    router.push(collectionId ? `/progress?collectionId=${encodeURIComponent(collectionId)}` : "/progress");
  }

  function retryLoad() {
    setRetryKey((current) => current + 1);
  }

  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
      <ProgressHeader />
      <PlanPicker
        collections={collections}
        selectedCollectionId={selectedCollectionId}
        collectionsState={collectionsState}
        onChange={handlePlanChange}
      />
      {selectedCollectionId && selectedCollectionId === primaryCollectionId ? (
        <GoalReadinessContent
          goalReadiness={goalReadiness}
          state={state}
          loadError={loadError}
          onRetry={retryLoad}
          labels={labels}
        />
      ) : selectedCollectionId ? (
        <PlanReadinessContent
          readiness={readiness}
          state={state}
          loadError={loadError}
          onRetry={retryLoad}
          labels={labels}
        />
      ) : (
        <ProgressContent report={report} state={state} />
      )}
    </main>
  );
}
