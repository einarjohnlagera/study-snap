"use client";

import Link from "next/link";
import { Star } from "lucide-react";
import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";
import { ResponsiveActionButton } from "@/components/ui/action-button";
import { ToastMessage } from "@/components/ui/toast-message";
import { getAuthUser } from "@/lib/auth";
import { getCollectionActionNotice } from "@/lib/collection-action-notice";
import { getCollectionLabels } from "@/lib/collection-labels";
import { createCollection, getMe, listCollections, type NoteCollectionSummary } from "@/lib/api";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { cn } from "@/lib/utils";
import { DashboardStudyPlanSection } from "@/app/dashboard/dashboard-study-plan-section";

type LoadState = "loading" | "ready" | "error";
type CollectionExecutionStatus = "not-started" | "in-progress" | "completed";

const TITLE_MAX_LENGTH = 150;
const COLLECTION_STATUS_LABELS: Record<CollectionExecutionStatus, string> = {
  "not-started": "Not started",
  "in-progress": "In progress",
  completed: "Completed",
};
const COLLECTION_STATUS_CLASS_NAMES: Record<CollectionExecutionStatus, string> = {
  "not-started": "border-border bg-muted/60 text-foreground/65",
  "in-progress": "border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-900/70 dark:bg-blue-950/40 dark:text-blue-200",
  completed: "border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/70 dark:bg-emerald-950/40 dark:text-emerald-200",
};

function getCollectionExecutionStatus(collection: NoteCollectionSummary): CollectionExecutionStatus {
  if (collection.itemCount <= 0 || collection.notesPracticed <= 0) {
    return "not-started";
  }
  if (collection.notesPracticed >= collection.itemCount) {
    return "completed";
  }
  return "in-progress";
}

function CollectionExecutionStatusBadge({ collection }: Readonly<{ collection: NoteCollectionSummary }>) {
  const status = getCollectionExecutionStatus(collection);
  return (
    <span className={`rounded-full border px-2 py-0.5 text-xs font-medium ${COLLECTION_STATUS_CLASS_NAMES[status]}`}>
      {COLLECTION_STATUS_LABELS[status]}
    </span>
  );
}

// Shown only when sourcePlanId != null (adopted from a public source) — nothing renders for
// self-created collections; unlabeled implies "yours," matching common fork/copy UX patterns.
function AdoptedBadge() {
  return (
    <span className="rounded-full border border-border bg-muted/60 px-2 py-0.5 text-xs font-medium text-foreground/65">
      Adopted
    </span>
  );
}

// Primary is a card-level accent treatment (left border, see the Card className below), not a pill —
// only one collection can be Primary at a time, so it reads as a property of the card rather than
// another status chip. Matches the same text+Star treatment used on the detail-page hero.
function PrimaryIndicator() {
  return (
    <span className="inline-flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-indigo-700 dark:text-indigo-300">
      <Star className="h-3 w-3 fill-current" aria-hidden="true" />
      Primary
    </span>
  );
}

function formatRelativeUpdatedAt(value: string): string {
  const timestamp = new Date(value).getTime();
  if (Number.isNaN(timestamp)) {
    return "Updated recently";
  }

  const diffMs = Math.max(0, Date.now() - timestamp);
  const minuteMs = 60 * 1000;
  const hourMs = 60 * minuteMs;
  const dayMs = 24 * hourMs;
  if (diffMs < minuteMs) {
    return "Updated just now";
  }
  if (diffMs < hourMs) {
    const minutes = Math.floor(diffMs / minuteMs);
    return `Updated ${minutes} ${minutes === 1 ? "minute" : "minutes"} ago`;
  }
  if (diffMs < dayMs) {
    const hours = Math.floor(diffMs / hourMs);
    return `Updated ${hours} ${hours === 1 ? "hour" : "hours"} ago`;
  }
  const days = Math.floor(diffMs / dayMs);
  if (days < 30) {
    return `Updated ${days} ${days === 1 ? "day" : "days"} ago`;
  }
  return `Updated ${new Date(value).toLocaleDateString(undefined, { month: "short", day: "numeric" })}`;
}

function formatItemCount(count: number): string {
  return `${count} ${count === 1 ? "note" : "notes"}`;
}

function formatCollectionScope(collection: NoteCollectionSummary): string {
  if (collection.childCount > 0) {
    return `${collection.childCount} ${collection.childCount === 1 ? "plan" : "plans"}`;
  }
  return formatItemCount(collection.itemCount);
}

function CollectionSkeletonGrid() {
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" aria-label="Loading collections">
      {Array.from({ length: 3 }, (_, index) => (
        <Card key={index} className="space-y-4 p-5">
          <div className="h-5 w-2/3 animate-pulse rounded bg-muted" />
          <div className="h-4 w-full animate-pulse rounded bg-muted" />
          <div className="h-4 w-1/2 animate-pulse rounded bg-muted" />
        </Card>
      ))}
    </div>
  );
}

function CreateCollectionModal({
  isOpen,
  singular,
  onClose,
  onCreated,
}: Readonly<{
  isOpen: boolean;
  singular: string;
  onClose: () => void;
  onCreated: (collection: NoteCollectionSummary | { id: string }) => void;
}>) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) {
      setTitle("");
      setDescription("");
      setSubmitting(false);
      setError(null);
    }
  }, [isOpen]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmedTitle = title.trim();
    if (!trimmedTitle) {
      setError("Title is required.");
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const created = await createCollection({
        title: trimmedTitle,
        description: description.trim() || null,
      });
      onCreated(created);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Could not create this collection.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AppModal
      isOpen={isOpen}
      title={`New ${singular}`}
      description={`Name this ${singular.toLowerCase()} so you can return to it later.`}
      onClose={onClose}
      actions={(
        <>
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" form="create-collection-form" loading={submitting} loadingText="Creating...">
            Create
          </Button>
        </>
      )}
    >
      <form id="create-collection-form" className="space-y-4" onSubmit={handleSubmit}>
        <label className="block space-y-1.5">
          <span className="text-sm font-medium text-foreground">Title</span>
          <input
            data-autofocus="true"
            value={title}
            maxLength={TITLE_MAX_LENGTH}
            onChange={(event) => setTitle(event.target.value)}
            className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
            placeholder={`${singular} title`}
          />
        </label>
        <label className="block space-y-1.5">
          <span className="text-sm font-medium text-foreground">Description</span>
          <textarea
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            className="min-h-24 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
            placeholder="Optional context for this set of notes"
          />
        </label>
        {error ? <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-200">{error}</p> : null}
      </form>
    </AppModal>
  );
}

export function CollectionsPageClient() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const profileType = useMemo(() => getAuthUser()?.profileType ?? null, []);
  const labels = useMemo(() => getCollectionLabels(profileType), [profileType]);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [collections, setCollections] = useState<NoteCollectionSummary[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [courseProgram, setCourseProgram] = useState<string | null>(null);
  const [primaryCollectionId, setPrimaryCollectionId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(searchParams.get("new") === "1");
  const [actionToast, setActionToast] = useState<string | null>(() => getCollectionActionNotice());

  const loadCollections = useCallback(async () => {
    setLoadState("loading");
    setLoadError(null);
    try {
      const result = await listCollections();
      setCollections(result);
      setLoadState("ready");
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : "Could not load collections.");
      setLoadState("error");
    }
  }, []);

  const loadProfile = useCallback(async () => {
    try {
      const me = await getMe();
      setCourseProgram(me.courseProgram ?? null);
      setPrimaryCollectionId(me.primaryCollectionId ?? null);
    } catch {
      setCourseProgram(null);
      setPrimaryCollectionId(null);
    }
  }, []);

  useEffect(() => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
    void Promise.resolve().then(loadCollections);
    void Promise.resolve().then(loadProfile);
  }, [loadCollections, loadProfile, router]);

  useEffect(() => {
    if (!actionToast) {
      return;
    }
    const timer = globalThis.setTimeout(() => setActionToast(null), 4000);
    return () => globalThis.clearTimeout(timer);
  }, [actionToast]);

  // Primary always leads the grid; the rest keep the backend's updatedAt-desc order. Derived
  // (not sorted at fetch) because collections/primaryCollectionId arrive from two independent
  // async calls and must re-order once both land, in either arrival order.
  const orderedCollections = useMemo(() => {
    if (!primaryCollectionId) {
      return collections;
    }
    const primaryIndex = collections.findIndex((collection) => collection.id === primaryCollectionId);
    if (primaryIndex <= 0) {
      return collections;
    }
    const primary = collections[primaryIndex];
    return [primary, ...collections.slice(0, primaryIndex), ...collections.slice(primaryIndex + 1)];
  }, [collections, primaryCollectionId]);

  const headerAction = (
    <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
      <Link
        href="/collections/published?ref=/collections"
        className="inline-flex min-h-10 items-center justify-center rounded-lg border border-border px-4 py-2 text-sm font-medium transition-colors hover:bg-muted"
      >
        Browse official plans
      </Link>
      <ResponsiveActionButton
        action="create"
        label={labels.newCtaLabel}
        className="w-full sm:w-auto"
        onClick={() => setCreateOpen(true)}
      />
    </div>
  );

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
      <PageHeader
        eyebrow="WORKSPACE"
        title={`Your ${labels.plural}`}
        description={labels.listDescription}
        actions={headerAction}
      />

      {loadState === "loading" ? <CollectionSkeletonGrid /> : null}

      {loadState === "error" ? (
        <Card className="space-y-4 p-6">
          <CardTitle>Could not load {labels.plural.toLowerCase()}</CardTitle>
          <CardDescription>{loadError ?? "Please try again."}</CardDescription>
          <Button type="button" variant="outline" onClick={() => void loadCollections()}>
            Retry
          </Button>
        </Card>
      ) : null}

      {loadState === "ready" && collections.length === 0 ? (
        <Card className="space-y-4 p-6 text-center">
          <CardTitle>{labels.emptyTitle}</CardTitle>
          <CardDescription>{labels.emptyBody}</CardDescription>
          <div>
            <ResponsiveActionButton action="create" label={labels.newCtaLabel} onClick={() => setCreateOpen(true)} />
          </div>
        </Card>
      ) : null}

      {loadState === "ready" && collections.length > 0 ? (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {orderedCollections.map((collection) => (
            <Link key={collection.id} href={`/collections/${collection.id}`} className="group block h-full">
              <Card
                className={cn(
                  "flex h-full min-h-44 flex-col justify-between gap-4 p-5 transition-colors group-hover:border-blue-300 group-hover:bg-blue-50/50 dark:group-hover:border-blue-800 dark:group-hover:bg-blue-950/20",
                  collection.id === primaryCollectionId && "border-l-4 border-l-indigo-500 bg-indigo-500/[0.03] dark:border-l-indigo-400",
                )}
              >
                <div className="space-y-2">
                  <CardTitle className="line-clamp-2">{collection.title}</CardTitle>
                  {collection.courseProgram ? (
                    <CardDescription className="line-clamp-1 text-sm">{collection.courseProgram}</CardDescription>
                  ) : null}
                  {collection.id === primaryCollectionId || collection.sourcePlanId ? (
                    <div className="flex flex-wrap items-center gap-2">
                      {collection.id === primaryCollectionId ? <PrimaryIndicator /> : null}
                      {collection.sourcePlanId ? <AdoptedBadge /> : null}
                    </div>
                  ) : null}
                  {collection.childCount === 0 ? (
                    <div>
                      <CollectionExecutionStatusBadge collection={collection} />
                    </div>
                  ) : null}
                </div>
                <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-foreground/60">
                  <span>{formatCollectionScope(collection)}</span>
                  <span>{formatRelativeUpdatedAt(collection.updatedAt)}</span>
                </div>
              </Card>
            </Link>
          ))}
        </div>
      ) : null}

      {loadState === "ready" ? (
        <DashboardStudyPlanSection
          courseProgram={courseProgram}
          profileType={profileType}
          primaryCollectionId={primaryCollectionId}
          viewAllHref="/collections/published?ref=/collections"
          browseWhenEmpty
        />
      ) : null}

      <CreateCollectionModal
        isOpen={createOpen}
        singular={labels.singular}
        onClose={() => setCreateOpen(false)}
        onCreated={(collection) => router.push(`/collections/${collection.id}/builder`)}
      />
      {actionToast ? <ToastMessage message={actionToast} tone="success" /> : null}
    </main>
  );
}
