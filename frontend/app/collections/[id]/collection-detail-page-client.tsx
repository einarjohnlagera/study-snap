"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { DndContext, PointerSensor, KeyboardSensor, closestCenter, useSensor, useSensors, type DragEndEvent } from "@dnd-kit/core";
import { SortableContext, arrayMove, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { ArrowRight, ChevronDown, Clock, GripVertical, Globe, Lock, MoreHorizontal, Search, Settings2, X } from "lucide-react";
import { AppModal } from "@/components/ui/app-modal";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { GuidanceTip } from "@/components/ui/guidance-tip";
import { SuggestionCombobox } from "@/components/ui/suggestion-combobox";
import { PageHeader } from "@/components/page-header";
import { ReadinessSummary } from "@/components/readiness/readiness-summary";
import { ResponsiveActionButton, ResponsiveActionContent, ResponsiveActionLink } from "@/components/ui/action-button";
import { CourseProgramCombobox } from "@/components/metadata/course-program-combobox";
import { getAuthUser, type AuthUser } from "@/lib/auth";
import { getCollectionLabels, getCollectionTerminalAction } from "@/lib/collection-labels";
import {
  canIncludeCollectionItemInPremiumExam,
  getCollectionPremiumExamReadyNoteIds,
  getCollectionPrimaryPremiumExamItem,
  getCollectionQuizReadyNoteIds,
  sortCollectionItemsByPosition,
} from "@/lib/collection-exam";
import {
  addCollectionItems,
  ApiRequestError,
  clearCollectionTargetDate,
  deleteCollection,
  getCollection,
  getCollectionGoal,
  getMe,
  getNoteConceptCounts,
  getPlanReadiness,
  listCoursePrograms,
  listNotes,
  removeCollectionItem,
  setCollectionItemOrder,
  updateCollection,
  updateCollectionVisibility,
  updateNoteVisibility,
  updateStudyDaysPerWeek,
  type GoalCollectionDetailResponse,
  type NoteConceptCountsResponse,
  type NoteCollectionDetail,
  type NoteCollectionItem,
  type NoteListItemResponse,
  type NoteVisibility,
  type PlanReadinessResponse,
} from "@/lib/api";
import { getStudyPlanSkippedNotice } from "@/app/dashboard/dashboard-study-plan-section";
import { getJustAdoptedNotice } from "@/lib/just-adopted-notice";
import { pickActiveGuidance, type GuidanceRule } from "@/lib/guidance-engine";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { cn } from "@/lib/utils";
import { getUpgradeCtas, type AppPlanType } from "@/src/config/plans";

type LoadState = "loading" | "ready" | "error" | "not-found";
type ReadinessLoadState = "idle" | "loading" | "ready" | "error";
type MutationKind = "add" | "delete" | "edit" | "publish" | "remove" | "reorder" | null;
type NextPlanAction = {
  item: NoteCollectionItem;
  actionLabel: "Generate Study Pack" | "Study this note" | "Review due concepts";
  description: string;
};
type CollectionItemSection = {
  id: string;
  name: string;
  items: NoteCollectionItem[];
};
export type SectionReadiness = {
  mastered: number;
  total: number;
  due: number;
};
type ContinuePlanAction = {
  item: NoteCollectionItem;
  actionLabel: "Generate Study Pack" | "Study this note" | "Review due concepts";
  href: string;
};

const TITLE_MAX_LENGTH = 150;
const LABEL_MAX_LENGTH = 120;
const UNGROUPED_SECTION_NAME = "Ungrouped";
const LARGE_VIEWPORT_MIN_WIDTH = 1024;
const CONTINUE_DISMISS_STORAGE_PREFIX = "dismissed-continue-";

function buildOrderPayload(items: NoteCollectionItem[]) {
  return items.map((item) => ({ noteId: item.noteId, label: item.label ?? null }));
}

function normalizeSectionName(label: string | null | undefined): string {
  return label?.trim() ?? "";
}

function getCollectionItemSections(items: NoteCollectionItem[]): { hasSections: boolean; sections: CollectionItemSection[]; sectionNames: string[] } {
  const groupedSections = new Map<string, CollectionItemSection>();
  const ungroupedItems: NoteCollectionItem[] = [];

  sortCollectionItemsByPosition(items).forEach((item) => {
    const sectionName = normalizeSectionName(item.label);
    if (!sectionName) {
      ungroupedItems.push(item);
      return;
    }

    const existingSection = groupedSections.get(sectionName);
    if (existingSection) {
      existingSection.items.push(item);
      return;
    }

    groupedSections.set(sectionName, {
      id: `section:${sectionName}`,
      name: sectionName,
      items: [item],
    });
  });

  const sections = Array.from(groupedSections.values());
  if (ungroupedItems.length > 0) {
    sections.push({
      id: "section:ungrouped",
      name: UNGROUPED_SECTION_NAME,
      items: ungroupedItems,
    });
  }

  return {
    hasSections: groupedSections.size > 0,
    sections,
    sectionNames: Array.from(groupedSections.keys()),
  };
}

function getNoteTitle(item: Pick<NoteCollectionItem, "title" | "noteId">): string {
  return item.title?.trim() || `Untitled note ${item.noteId.slice(0, 8)}`;
}

function getNoteMeta(item: Pick<NoteCollectionItem, "subject" | "courseProgram">): string {
  return [item.subject, item.courseProgram].filter(Boolean).join(" · ") || "No subject yet";
}

function getSectionReadinessKey(label: string | null | undefined): string {
  return normalizeSectionName(label) || UNGROUPED_SECTION_NAME;
}

export function aggregateSectionReadiness(
  items: NoteCollectionItem[],
  countsByNoteId: Record<string, NoteConceptCountsResponse>,
): Map<string, SectionReadiness> {
  const sectionCounts = new Map<string, SectionReadiness>();
  items.forEach((item) => {
    const counts = countsByNoteId[item.noteId];
    if (!counts) {
      return;
    }
    const sectionName = getSectionReadinessKey(item.label);
    const previous = sectionCounts.get(sectionName) ?? { mastered: 0, total: 0, due: 0 };
    sectionCounts.set(sectionName, {
      mastered: previous.mastered + counts.masteredConceptCount,
      total: previous.total + counts.totalConceptCount,
      due: previous.due + counts.dueConceptCount,
    });
  });
  return sectionCounts;
}

export function getLatestPracticedCollectionItem(items: NoteCollectionItem[]): NoteCollectionItem | null {
  return items.reduce<NoteCollectionItem | null>((latest, item) => {
    if (item.lastSessionCompletedAt === null) {
      return latest;
    }
    if (latest === null || item.lastSessionCompletedAt > (latest.lastSessionCompletedAt ?? "")) {
      return item;
    }
    return latest;
  }, null);
}

function getContinuePlanAction(
  item: NoteCollectionItem,
  collectionId: string,
  canReviewDueConcepts: boolean,
): ContinuePlanAction {
  const href = `/notes/${item.noteId}?ref=${encodeURIComponent(`/collections/${collectionId}`)}`;
  if (item.studyPackStatus !== "STUDY_PACK_READY") {
    return { item, actionLabel: "Generate Study Pack", href };
  }
  if (canReviewDueConcepts && item.dueConceptCount > 0) {
    return { item, actionLabel: "Review due concepts", href };
  }
  return { item, actionLabel: "Study this note", href };
}

type SectionCardHeaderProps = {
  section: CollectionItemSection;
  isExpanded: boolean;
  organizeMode: boolean;
  sectionReadiness?: SectionReadiness | null;
  editingSectionId?: string | null;
  editingSectionName?: string;
  headingId: string;
  onToggle: () => void;
  onRenameStart?: (section: CollectionItemSection) => void;
  onRenameInput?: (value: string) => void;
  onRenameCommit?: (section: CollectionItemSection, name: string) => void;
  onRenameCancel?: () => void;
};

function SectionCardHeader({
  section,
  isExpanded,
  organizeMode,
  sectionReadiness = null,
  editingSectionId = null,
  editingSectionName = "",
  headingId,
  onToggle,
  onRenameStart,
  onRenameInput,
  onRenameCommit,
  onRenameCancel,
}: Readonly<SectionCardHeaderProps>) {
  const cancelingRef = useRef(false);
  const isEditing = editingSectionId === section.id;
  const isUngrouped = section.name === UNGROUPED_SECTION_NAME;
  const readinessStat = !organizeMode && sectionReadiness && sectionReadiness.total > 0 ? (
    <span className="rounded-full border border-blue-500/20 bg-blue-500/10 px-2 py-0.5 text-xs font-semibold text-blue-700 dark:text-blue-200">
      {Math.round((sectionReadiness.mastered / sectionReadiness.total) * 100)}% · {sectionReadiness.due} due
    </span>
  ) : null;

  const titlePeek = !isExpanded && section.items.length > 0 ? (
    <span className="mt-0.5 truncate text-xs text-foreground/45">
      {section.items.slice(0, 3).map((i) => getNoteTitle(i)).join(" · ")}
      {section.items.length > 3 ? ` +${section.items.length - 3} more` : ""}
    </span>
  ) : null;

  const chevron = (
    <ChevronDown
      className={cn("h-4 w-4 shrink-0 text-foreground/70 transition-transform", !isExpanded && "-rotate-90")}
      aria-hidden="true"
    />
  );

  if (organizeMode) {
    return (
      <div className="flex items-center justify-between gap-3">
        <div className="flex min-w-0 flex-1 flex-col">
          <div className="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1">
            {isUngrouped ? (
              <span id={headingId} data-testid="collection-section-heading" className="text-sm font-semibold uppercase tracking-wider text-foreground/55">
                {section.name}
              </span>
            ) : (
              <input
                id={headingId}
                type="text"
                maxLength={LABEL_MAX_LENGTH}
                value={isEditing ? editingSectionName : section.name}
                data-testid="collection-section-heading"
                className="min-w-0 max-w-[200px] bg-transparent text-sm font-semibold uppercase tracking-wider text-foreground/80 outline-none focus:rounded focus:ring-1 focus:ring-ring"
                aria-label={`Rename section ${section.name}`}
                onFocus={() => onRenameStart?.(section)}
                onChange={(e) => onRenameInput?.(e.target.value)}
                onBlur={() => {
                  if (!cancelingRef.current) {
                    onRenameCommit?.(section, isEditing ? editingSectionName : section.name);
                  }
                  cancelingRef.current = false;
                }}
                onKeyDown={(e) => {
                  if (e.key === "Enter") e.currentTarget.blur();
                  if (e.key === "Escape") {
                    cancelingRef.current = true;
                    e.currentTarget.blur();
                    onRenameCancel?.();
                  }
                }}
              />
            )}
            <span className="text-xs font-medium text-foreground/55">
              {section.items.length} {section.items.length === 1 ? "note" : "notes"}
            </span>
          </div>
          {titlePeek}
        </div>
        <button
          type="button"
          className="shrink-0 rounded-md p-1 text-foreground/70 transition-colors hover:bg-muted/50"
          aria-expanded={isExpanded}
          onClick={onToggle}
        >
          {chevron}
          <span className="sr-only">{isExpanded ? "Collapse" : "Expand"} section</span>
        </button>
      </div>
    );
  }

  return (
    <button
      type="button"
      className="flex w-full cursor-pointer items-center justify-between gap-3 rounded-lg text-left transition-colors hover:bg-muted/50"
      aria-expanded={isExpanded}
      onClick={onToggle}
    >
      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1">
          <span id={headingId} data-testid="collection-section-heading" className="text-sm font-semibold uppercase tracking-wider text-foreground/80">
            {section.name}
          </span>
          <span className="text-xs font-medium text-foreground/55">
            {section.items.length} {section.items.length === 1 ? "note" : "notes"}
          </span>
          {readinessStat}
        </div>
        {titlePeek}
      </div>
      {chevron}
    </button>
  );
}

function ContinuePlanBanner({
  action,
  onDismiss,
}: Readonly<{
  action: ContinuePlanAction;
  onDismiss: () => void;
}>) {
  return (
    <Card className="flex flex-col gap-3 border-blue-500/25 bg-blue-500/5 p-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">Continue where you left off</p>
        <p className="mt-1 truncate text-sm text-foreground/75">
          Continue from <span className="font-semibold text-foreground">{getNoteTitle(action.item)}</span>
        </p>
      </div>
      <div className="flex shrink-0 items-center gap-2">
        <Link
          href={action.href}
          className="inline-flex min-h-10 items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-400"
        >
          {action.actionLabel}
          <ArrowRight className="h-4 w-4" aria-hidden="true" />
        </Link>
        <Button type="button" variant="ghost" size="sm" className="h-10 px-2" onClick={onDismiss}>
          Dismiss
        </Button>
      </div>
    </Card>
  );
}

function PlanHeroCard({
  collection,
  eyebrowLabel,
  statusBadge,
  isAdmin,
  actions,
  terminalFooter,
  onPublishClick,
}: Readonly<{
  collection: NoteCollectionDetail;
  eyebrowLabel: string;
  statusBadge: ReactNode;
  isAdmin: boolean;
  actions: ReactNode;
  terminalFooter?: ReactNode;
  onPublishClick: () => void;
}>) {
  const estimatedStudyHours = collection.estimatedStudyHours && collection.estimatedStudyHours > 0
    ? collection.estimatedStudyHours
    : null;

  return (
    <Card className="space-y-5 p-5 sm:p-6">
      <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0 flex-1 space-y-3">
          <div className="flex flex-wrap items-center gap-2">
            {collection.courseProgram ? (
              <span className="inline-flex w-fit items-center rounded-full border border-border bg-muted/40 px-2.5 py-1 text-xs font-semibold uppercase tracking-wide text-foreground/65">
                {collection.courseProgram}
              </span>
            ) : null}
            {estimatedStudyHours ? (
              <span className="inline-flex w-fit items-center gap-1.5 rounded-full border border-blue-500/20 bg-blue-500/10 px-2.5 py-1 text-xs font-semibold text-blue-700 dark:text-blue-200">
                <Clock className="h-3.5 w-3.5" aria-hidden="true" />
                ~{estimatedStudyHours} hrs
              </span>
            ) : null}
            {statusBadge}
            {isAdmin ? (
              <button
                type="button"
                onClick={onPublishClick}
                aria-label="Publish settings"
                title="Publish settings"
                className="motion-lift inline-flex w-fit items-center gap-1.5 rounded-full border border-border bg-muted/40 px-2.5 py-1 text-xs font-medium text-foreground/70 transition-colors hover:bg-highlight"
              >
                {collection.visibility === "PUBLIC" ? (
                  <><Globe className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" aria-hidden="true" />Published</>
                ) : (
                  <><Lock className="h-3.5 w-3.5" aria-hidden="true" />Private</>
                )}
                <Settings2 className="h-3 w-3 opacity-60" aria-hidden="true" />
              </button>
            ) : null}
          </div>
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">{eyebrowLabel}</p>
            <CardTitle className="text-2xl sm:text-3xl">{collection.title}</CardTitle>
            {collection.description ? (
              <CardDescription className="line-clamp-3 text-sm sm:text-base">{collection.description}</CardDescription>
            ) : null}
          </div>
        </div>
        <div className="flex shrink-0 flex-col items-end gap-3">
          {actions}
          {terminalFooter}
        </div>
      </div>
    </Card>
  );
}

type NoteExecutionStatus = { label: string; className: string };

// Per-note execution status for the Study Plan detail rows. This is a learner
// signal — have I practiced this note yet — not exam-readiness. The steady-state
// model is Needs Study Pack -> Not started -> Practiced; the transient
// Generating / Generation failed states are kept for operational feedback.
function getNoteExecutionStatus(
  item: Pick<NoteCollectionItem, "studyPackStatus" | "lastSessionCompletedAt">,
): NoteExecutionStatus {
  if (item.studyPackStatus === "GENERATING") {
    return { label: "Generating", className: "text-foreground/60" };
  }
  if (item.studyPackStatus === "FAILED") {
    return { label: "Generation failed", className: "text-red-700 dark:text-red-300" };
  }
  if (item.studyPackStatus !== "STUDY_PACK_READY") {
    return { label: "Needs Study Pack", className: "text-amber-700 dark:text-amber-300" };
  }
  if (item.lastSessionCompletedAt !== null) {
    return { label: "Practiced", className: "text-emerald-700 dark:text-emerald-300" };
  }
  return { label: "Not started", className: "text-foreground/60" };
}

function canViewConceptHealth(currentPlan: AppPlanType): boolean {
  return currentPlan === "PLUS" || currentPlan === "PRO";
}

function getNextPlanAction(items: NoteCollectionItem[], canReviewDueConcepts: boolean): NextPlanAction | null {
  const needsStudyPack = items.find((item) => item.studyPackStatus !== "STUDY_PACK_READY");
  if (needsStudyPack) {
    return {
      item: needsStudyPack,
      actionLabel: "Generate Study Pack",
      description: "Turn this note into a Study Pack before moving to the next step.",
    };
  }

  const needsPractice = items.find((item) => item.lastSessionCompletedAt === null);
  if (needsPractice) {
    return {
      item: needsPractice,
      actionLabel: "Study this note",
      description: "Practice this Study Pack before continuing through the plan.",
    };
  }

  if (canReviewDueConcepts) {
    const needsReview = items.find((item) => item.dueConceptCount > 0);
    if (needsReview) {
      return {
        item: needsReview,
        actionLabel: "Review due concepts",
        description: "Revisit the concepts that are due in this note.",
      };
    }
  }

  return null;
}

function CollectionProgressSummary({ collection }: Readonly<{ collection: NoteCollectionDetail }>) {
  const { totalNotes, notesPracticed } = collection.progress;
  const practicedPercentage = totalNotes > 0
    ? Math.min(100, Math.max(0, Math.round((notesPracticed / totalNotes) * 100)))
    : 0;

  return (
    <Card className="space-y-3 p-4 sm:p-5">
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">Progress</p>
        {totalNotes > 0 ? (
          <p className="text-sm font-medium text-foreground">
            {notesPracticed} of {totalNotes} practiced
          </p>
        ) : (
          <div className="space-y-1">
            <p className="text-sm font-medium text-foreground">No progress yet</p>
            <p className="text-xs text-foreground/60">Add notes to start tracking practice.</p>
          </div>
        )}
      </div>
      <div
        role="progressbar"
        aria-label="Notes practiced"
        aria-valuemin={0}
        aria-valuemax={totalNotes}
        aria-valuenow={notesPracticed}
        className="h-2 overflow-hidden rounded-full bg-muted"
      >
        <div
          className="h-full rounded-full bg-blue-600 transition-[width] dark:bg-blue-400"
          style={{ width: `${practicedPercentage}%` }}
        />
      </div>
    </Card>
  );
}

function formatPlanCount(count: number): string {
  return `${count} ${count === 1 ? "plan" : "plans"}`;
}

function clampPercentage(value: number): number {
  return Math.min(100, Math.max(0, value));
}

// A LocalDate-only string like "2026-12-01" must not go through `new Date(isoDate)` for display —
// that parses as UTC midnight, which can shift a day backward once formatted in a timezone behind
// UTC. Splitting and constructing via the local-time Date constructor avoids that shift.
function formatLocalDate(isoDate: string): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  return new Date(year, month - 1, day).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function GoalWeeklyCountdownCard({
  targetCompletionDate,
  weeksRemaining,
  conceptsRemaining,
  todaysConceptBudget,
}: Readonly<{
  targetCompletionDate: string | null;
  weeksRemaining: number | null;
  conceptsRemaining: number | null;
  todaysConceptBudget: number | null;
}>) {
  if (!targetCompletionDate || weeksRemaining === null || conceptsRemaining === null || todaysConceptBudget === null) {
    return null;
  }
  const weekLabel = weeksRemaining === 1 ? "1 week" : `${weeksRemaining} weeks`;
  const conceptLabel = conceptsRemaining === 1 ? "1 concept" : `${conceptsRemaining} concepts`;
  const budgetLabel = todaysConceptBudget === 1 ? "1 concept" : `${todaysConceptBudget} concepts`;

  return (
    <Card className="space-y-2 p-4 sm:p-5">
      <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">This Week</p>
      <h2 className="text-lg font-semibold tracking-tight">
        {weekLabel} until {formatLocalDate(targetCompletionDate)}
      </h2>
      <p className="text-sm font-medium text-foreground/80">
        {conceptLabel} remaining · {budgetLabel} today
      </p>
    </Card>
  );
}

function GoalDetailView({
  goal,
  labels,
}: Readonly<{
  goal: GoalCollectionDetailResponse;
  labels: ReturnType<typeof getCollectionLabels>;
}>) {
  return (
    <div className="space-y-6">
      <GoalWeeklyCountdownCard
        targetCompletionDate={goal.targetCompletionDate}
        weeksRemaining={goal.weeksRemaining}
        conceptsRemaining={goal.conceptsRemaining}
        todaysConceptBudget={goal.todaysConceptBudget}
      />

      <ReadinessSummary
        variant="compact"
        title={`${goal.title} readiness`}
        eyebrow={`${labels.goalSingular} readiness`}
        overallReadinessPercentage={goal.overallReadinessPercentage}
        totalConcepts={goal.totalConcepts}
        masteredConcepts={goal.masteredConcepts}
        dueConcepts={goal.dueConcepts}
        notPracticedConcepts={goal.notPracticedConcepts}
        subjects={[]}
        emptyTitle="No readiness yet"
        emptyDescription={`Add ${labels.subjectSingular.toLowerCase()}s with ready Study Packs to see this ${labels.goalSingular.toLowerCase()} readiness.`}
      />

      <Card className="space-y-4 p-4 sm:p-6">
        <div>
          <CardTitle>{labels.subjectSingular}s</CardTitle>
          <CardDescription>{formatPlanCount(goal.children.length)} in this {labels.goalSingular.toLowerCase()}.</CardDescription>
        </div>

        {goal.children.length === 0 ? (
          <div className="rounded-xl border border-dashed border-border p-6 text-center">
            <p className="text-sm text-foreground/70">
              Nest {labels.singular.toLowerCase()}s under this {labels.goalSingular.toLowerCase()} to build the curriculum.
            </p>
          </div>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            {goal.children.map((child) => (
              <Link key={child.collectionId} href={`/collections/${child.collectionId}`} className="group block">
                <Card className="h-full space-y-4 p-4 transition-colors group-hover:border-blue-300 group-hover:bg-blue-50/50 dark:group-hover:border-blue-800 dark:group-hover:bg-blue-950/20">
                  <div className="space-y-1">
                    <CardTitle className="line-clamp-2 text-base">{child.title}</CardTitle>
                    {child.description ? (
                      <CardDescription className="line-clamp-2 text-sm">{child.description}</CardDescription>
                    ) : (
                      <p className="text-sm text-foreground/55">No description yet.</p>
                    )}
                  </div>
                  <div className="space-y-2">
                    <div className="flex items-center justify-between gap-3 text-sm">
                      <span className="text-foreground/60">{child.itemCount} {child.itemCount === 1 ? "note" : "notes"}</span>
                      <span className="font-semibold text-blue-700 dark:text-blue-300">
                        {child.overallReadinessPercentage}% ready
                      </span>
                    </div>
                    <div
                      role="progressbar"
                      aria-label={`${child.title} readiness`}
                      aria-valuemin={0}
                      aria-valuemax={100}
                      aria-valuenow={child.overallReadinessPercentage}
                      className="h-2 overflow-hidden rounded-full bg-muted"
                    >
                      <div
                        className="h-full rounded-full bg-blue-600 transition-[width] dark:bg-blue-400"
                        style={{ width: `${clampPercentage(child.overallReadinessPercentage)}%` }}
                      />
                    </div>
                    <p className="text-xs text-foreground/60">
                      {child.masteredConcepts}/{child.totalConcepts} mastered · {child.dueConcepts} due · {child.notPracticedConcepts} not started
                    </p>
                  </div>
                </Card>
              </Link>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}

function NextInPlanCard({
  items,
  collectionId,
  canReviewDueConcepts,
}: Readonly<{
  items: NoteCollectionItem[];
  collectionId: string;
  canReviewDueConcepts: boolean;
}>) {
  if (items.length === 0) {
    return null;
  }

  const nextAction = getNextPlanAction(items, canReviewDueConcepts);
  if (!nextAction) {
    return (
      <Card className="space-y-2 border-emerald-500/25 bg-emerald-500/5 p-4 sm:p-5">
        <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">Next in this plan</p>
        <CardTitle>All caught up in this plan</CardTitle>
        <CardDescription>Every note has a Study Pack and has been practiced.</CardDescription>
      </Card>
    );
  }

  const noteHref = `/notes/${nextAction.item.noteId}?ref=${encodeURIComponent(`/collections/${collectionId}`)}`;
  return (
    <Card className="space-y-3 border-blue-500/25 bg-blue-500/5 p-4 sm:p-5">
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">Next in this plan</p>
        <CardTitle>{getNoteTitle(nextAction.item)}</CardTitle>
        <CardDescription>{nextAction.description}</CardDescription>
      </div>
      <Link
        href={noteHref}
        className="inline-flex min-h-10 items-center justify-center rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-400"
      >
        {nextAction.actionLabel}
      </Link>
    </Card>
  );
}

function normalizeNoteSearch(value: string): string {
  return value.trim().toLowerCase();
}

function filterPickerNotes(notes: NoteListItemResponse[], presentNoteIds: Set<string>, query: string): NoteListItemResponse[] {
  const normalizedQuery = normalizeNoteSearch(query);
  return notes
    .filter((note) => !presentNoteIds.has(note.id))
    .filter((note) => {
      if (!normalizedQuery) {
        return true;
      }
      return [note.title, note.subject, note.courseProgram, ...(note.tags ?? [])]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(normalizedQuery));
    });
}

function EditCollectionModal({
  collection,
  isOpen,
  onClose,
  onSaved,
}: Readonly<{
  collection: NoteCollectionDetail;
  isOpen: boolean;
  onClose: () => void;
  onSaved: (collection: NoteCollectionDetail) => void;
}>) {
  const isTopLevelGoal = collection.parentCollectionId === null;
  const [title, setTitle] = useState(collection.title);
  const [description, setDescription] = useState(collection.description ?? "");
  const [estimatedStudyHours, setEstimatedStudyHours] = useState<string>(
    collection.estimatedStudyHours === null ? "" : String(collection.estimatedStudyHours),
  );
  const [targetCompletionDate, setTargetCompletionDate] = useState<string>(collection.targetCompletionDate ?? "");
  const [studyDaysPerWeek, setStudyDaysPerWeek] = useState<string>("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      setTitle(collection.title);
      setDescription(collection.description ?? "");
      setEstimatedStudyHours(collection.estimatedStudyHours === null ? "" : String(collection.estimatedStudyHours));
      setTargetCompletionDate(collection.targetCompletionDate ?? "");
      setError(null);
      setSubmitting(false);
      if (isTopLevelGoal) {
        // studyDaysPerWeek is a user-level attribute, not a collection field, so it isn't part of
        // `collection` — asked on this same screen per the target-date UX, but sourced separately.
        void getMe()
          .then((me) => setStudyDaysPerWeek(me.studyDaysPerWeek === null ? "" : String(me.studyDaysPerWeek)))
          .catch(() => setStudyDaysPerWeek(""));
      } else {
        setStudyDaysPerWeek("");
      }
    }
  }, [
    collection.description,
    collection.estimatedStudyHours,
    collection.targetCompletionDate,
    collection.title,
    isOpen,
    isTopLevelGoal,
  ]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmedTitle = title.trim();
    if (!trimmedTitle) {
      setError("Title is required.");
      return;
    }
    const trimmedStudyDaysPerWeek = studyDaysPerWeek.trim();
    if (trimmedStudyDaysPerWeek && (Number(trimmedStudyDaysPerWeek) < 1 || Number(trimmedStudyDaysPerWeek) > 7)) {
      setError("Study days per week must be between 1 and 7.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const trimmedTargetCompletionDate = targetCompletionDate.trim();
      let saved = await updateCollection(collection.id, {
        title: trimmedTitle,
        // Send an empty string (not null) to clear the description: updateMetadata now preserves
        // fields omitted (null) from the request and only clears a text field on an explicit "".
        description: description.trim(),
        estimatedStudyHours: estimatedStudyHours ? Number(estimatedStudyHours) : null,
        // targetCompletionDate uses the same omit-preserves semantics — a null/omitted value here
        // leaves the existing date untouched, it does not clear it. Clearing goes through the
        // dedicated clearCollectionTargetDate call below instead.
        ...(isTopLevelGoal && trimmedTargetCompletionDate ? { targetCompletionDate: trimmedTargetCompletionDate } : {}),
      });
      if (isTopLevelGoal && !trimmedTargetCompletionDate && collection.targetCompletionDate) {
        saved = await clearCollectionTargetDate(collection.id);
      }
      if (isTopLevelGoal) {
        await updateStudyDaysPerWeek(trimmedStudyDaysPerWeek ? Number(trimmedStudyDaysPerWeek) : null);
      }
      onSaved(saved);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Could not update this collection.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AppModal
      isOpen={isOpen}
      title="Edit details"
      description="Update the name or description for this saved set."
      onClose={onClose}
      actions={(
        <>
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" form="edit-collection-form" loading={submitting} loadingText="Saving...">Save</Button>
        </>
      )}
    >
      <form id="edit-collection-form" className="space-y-4" onSubmit={handleSubmit} noValidate>
        <label className="block space-y-1.5">
          <span className="text-sm font-medium text-foreground">Title</span>
          <input
            data-autofocus="true"
            value={title}
            maxLength={TITLE_MAX_LENGTH}
            onChange={(event) => setTitle(event.target.value)}
            className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
          />
        </label>
        <label className="block space-y-1.5">
          <span className="text-sm font-medium text-foreground">Description</span>
          <textarea
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            className="min-h-24 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
          />
        </label>
        <label className="block space-y-1.5">
          <span className="text-sm font-medium text-foreground">Estimated study time (hours)</span>
          <input
            aria-label="Estimated study time (hours)"
            type="number"
            min="1"
            step="1"
            value={estimatedStudyHours}
            onChange={(event) => setEstimatedStudyHours(event.target.value)}
            className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
          />
          <span className="block text-xs text-foreground/60">Optional. Shown to learners on adoption.</span>
        </label>
        {isTopLevelGoal ? (
          <>
            <label className="block space-y-1.5">
              <span className="text-sm font-medium text-foreground">Target completion date</span>
              <input
                aria-label="Target completion date"
                type="date"
                value={targetCompletionDate}
                onChange={(event) => setTargetCompletionDate(event.target.value)}
                className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
              />
              <span className="block text-xs text-foreground/60">Optional. When are you aiming to be ready by?</span>
            </label>
            <label className="block space-y-1.5">
              <span className="text-sm font-medium text-foreground">Study days per week</span>
              <input
                aria-label="Study days per week"
                type="number"
                min="1"
                max="7"
                step="1"
                value={studyDaysPerWeek}
                onChange={(event) => setStudyDaysPerWeek(event.target.value)}
                className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
              />
              <span className="block text-xs text-foreground/60">Optional — assumes every day if left blank.</span>
            </label>
          </>
        ) : null}
        {error ? <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-200">{error}</p> : null}
      </form>
    </AppModal>
  );
}

function DeleteCollectionModal({
  isOpen,
  title,
  onClose,
  onConfirm,
  deleting,
}: Readonly<{
  isOpen: boolean;
  title: string;
  onClose: () => void;
  onConfirm: () => void;
  deleting: boolean;
}>) {
  return (
    <AppModal
      isOpen={isOpen}
      title="Delete collection?"
      description={`Delete "${title}" from your workspace. This removes only the collection and its ordering; your notes will not be deleted.`}
      onClose={onClose}
      actions={(
        <>
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="button" className="bg-red-600 hover:bg-red-700" loading={deleting} loadingText="Deleting..." onClick={onConfirm}>
            Delete
          </Button>
        </>
      )}
    />
  );
}

function AddNotesModal({
  isOpen,
  presentNoteIds,
  onClose,
  onAdd,
}: Readonly<{
  isOpen: boolean;
  presentNoteIds: Set<string>;
  onClose: () => void;
  onAdd: (noteIds: string[]) => Promise<void>;
}>) {
  const [notes, setNotes] = useState<NoteListItemResponse[]>([]);
  const [query, setQuery] = useState("");
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) {
      setQuery("");
      setSelectedIds(new Set());
      setError(null);
      return;
    }
    let mounted = true;
    setLoading(true);
    setError(null);
    void listNotes()
      .then((result) => {
        if (mounted) {
          setNotes(result);
        }
      })
      .catch((loadError) => {
        if (mounted) {
          setError(loadError instanceof Error ? loadError.message : "Could not load your notes.");
        }
      })
      .finally(() => {
        if (mounted) {
          setLoading(false);
        }
      });
    return () => {
      mounted = false;
    };
  }, [isOpen]);

  const availableNotes = useMemo(() => filterPickerNotes(notes, presentNoteIds, query), [notes, presentNoteIds, query]);
  const hasAnyAvailableNotes = notes.some((note) => !presentNoteIds.has(note.id));

  const toggleSelected = (noteId: string) => {
    setSelectedIds((previous) => {
      const next = new Set(previous);
      if (next.has(noteId)) {
        next.delete(noteId);
      } else {
        next.add(noteId);
      }
      return next;
    });
  };

  const allVisibleSelected = availableNotes.length > 0 && availableNotes.every((note) => selectedIds.has(note.id));

  const toggleSelectAllVisible = () => {
    setSelectedIds((previous) => {
      const next = new Set(previous);
      if (allVisibleSelected) {
        availableNotes.forEach((note) => next.delete(note.id));
      } else {
        availableNotes.forEach((note) => next.add(note.id));
      }
      return next;
    });
  };

  const handleAdd = async () => {
    const noteIds = Array.from(selectedIds);
    if (noteIds.length === 0) {
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await onAdd(noteIds);
      onClose();
    } catch (addError) {
      setError(addError instanceof Error ? addError.message : "Could not add notes.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AppModal
      isOpen={isOpen}
      title="Add notes"
      description="Choose from your existing notes. Notes already in this collection are hidden."
      onClose={onClose}
      panelClassName="sm:max-w-2xl"
      actions={(
        <>
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="button" loading={submitting} loadingText="Adding..." disabled={selectedIds.size === 0} onClick={handleAdd}>
            Add selected
          </Button>
        </>
      )}
    >
      <div className="space-y-4">
        <label className="flex items-center gap-2 rounded-lg border border-border bg-background px-3 py-2">
          <Search className="h-4 w-4 text-foreground/50" aria-hidden="true" />
          <span className="sr-only">Search notes</span>
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search notes"
            className="w-full bg-transparent text-sm outline-none"
          />
        </label>

        {loading ? <p className="text-sm text-foreground/60">Loading notes...</p> : null}
        {error ? <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-200">{error}</p> : null}
        {!loading && !error && !hasAnyAvailableNotes ? (
          <p className="rounded-lg bg-muted px-3 py-3 text-sm text-foreground/70">
            You do not have any other notes to add yet.
          </p>
        ) : null}
        {!loading && !error && hasAnyAvailableNotes && availableNotes.length === 0 ? (
          <p className="rounded-lg bg-muted px-3 py-3 text-sm text-foreground/70">No matching notes found.</p>
        ) : null}
        {!loading && !error && availableNotes.length > 0 ? (
          <div className="flex items-center justify-between px-1">
            <button
              type="button"
              onClick={toggleSelectAllVisible}
              className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400"
            >
              {allVisibleSelected ? "Deselect all" : `Select all${query.trim() ? " matching" : ""} (${availableNotes.length})`}
            </button>
            <span className="text-xs text-foreground/50">{selectedIds.size} selected</span>
          </div>
        ) : null}
        <div className="max-h-80 space-y-2 overflow-y-auto pr-1">
          {availableNotes.map((note) => (
            <label key={note.id} className="flex cursor-pointer items-start gap-3 rounded-lg border border-border p-3 hover:bg-highlight">
              <input
                type="checkbox"
                checked={selectedIds.has(note.id)}
                onChange={() => toggleSelected(note.id)}
                className="mt-1"
              />
              <span className="space-y-1">
                <span className="block text-sm font-medium text-foreground">{note.title || "Untitled note"}</span>
                <span className="block text-xs text-foreground/60">
                  {[note.subject, note.courseProgram].filter(Boolean).join(" · ") || "No subject yet"}
                </span>
              </span>
            </label>
          ))}
        </div>
      </div>
    </AppModal>
  );
}

function PublishStudyPlanModal({
  collection,
  isOpen,
  privateNoteIds,
  onClose,
  onSaved,
  onNotesPublished,
}: Readonly<{
  collection: NoteCollectionDetail;
  isOpen: boolean;
  privateNoteIds: string[];
  onClose: () => void;
  onSaved: (collection: NoteCollectionDetail) => void;
  onNotesPublished: () => Promise<void> | void;
}>) {
  const [courseProgram, setCourseProgram] = useState(collection.courseProgram ?? "");
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [suggestionsError, setSuggestionsError] = useState(false);
  const [savingCourseProgram, setSavingCourseProgram] = useState(false);
  const [togglingVisibility, setTogglingVisibility] = useState(false);
  const [makingPublic, setMakingPublic] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isPublic = collection.visibility === "PUBLIC";
  const trimmedCourseProgram = courseProgram.trim();
  const courseProgramDirty = trimmedCourseProgram !== (collection.courseProgram ?? "").trim();
  const busy = savingCourseProgram || togglingVisibility || makingPublic;
  const privateCount = privateNoteIds.length;
  const blockedByPrivateNotes = privateCount > 0;

  // Publishing is a constrained surface: lock the field to known buckets, but keep
  // the plan's existing value selectable even if the suggestion fetch omits it.
  const courseProgramOptions = useMemo(() => {
    const existing = (collection.courseProgram ?? "").trim();
    return existing && !suggestions.includes(existing) ? [existing, ...suggestions] : suggestions;
  }, [collection.courseProgram, suggestions]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    setCourseProgram(collection.courseProgram ?? "");
    setError(null);
  }, [collection.courseProgram, collection.id, isOpen]);

  const loadSuggestions = useCallback(async () => {
    setSuggestionsError(false);
    try {
      setSuggestions(await listCoursePrograms("public"));
    } catch {
      setSuggestionsError(true);
    }
  }, []);

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    void loadSuggestions();
  }, [isOpen, loadSuggestions]);

  const persistCourseProgram = async (): Promise<NoteCollectionDetail | null> => {
    setSavingCourseProgram(true);
    setError(null);
    try {
      const saved = await updateCollection(collection.id, {
        // updateMetadata preserves fields omitted (null) from the request, so we only send the field
        // this editor changes. An empty string clears the course/program; null would leave it unchanged.
        courseProgram: trimmedCourseProgram,
      });
      onSaved(saved);
      return saved;
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Could not save the course/program.");
      return null;
    } finally {
      setSavingCourseProgram(false);
    }
  };

  const handlePublish = async () => {
    // Persist the course/program first so a blocked publish never discards what the user typed.
    // Saving metadata is decoupled from publishing: the field is kept even when the plan can't go public yet.
    if (courseProgramDirty && !(await persistCourseProgram())) {
      return;
    }
    if (!trimmedCourseProgram) {
      setError("Add a course/program so matching learners can find this plan.");
      return;
    }
    if (blockedByPrivateNotes) {
      setError("Course/program saved. Make every note public before publishing this plan.");
      return;
    }
    setTogglingVisibility(true);
    setError(null);
    try {
      const saved = await updateCollectionVisibility(collection.id, "PUBLIC");
      onSaved(saved);
    } catch (publishError) {
      setError(publishError instanceof Error ? publishError.message : "Could not publish this plan.");
    } finally {
      setTogglingVisibility(false);
    }
  };

  const handleUnpublish = async () => {
    setTogglingVisibility(true);
    setError(null);
    try {
      const saved = await updateCollectionVisibility(collection.id, "PRIVATE");
      onSaved(saved);
    } catch (unpublishError) {
      setError(unpublishError instanceof Error ? unpublishError.message : "Could not unpublish this plan.");
    } finally {
      setTogglingVisibility(false);
    }
  };

  const handleMakePublic = async () => {
    setMakingPublic(true);
    setError(null);
    try {
      await Promise.all(privateNoteIds.map((noteId) => updateNoteVisibility(noteId, "PUBLIC")));
      await onNotesPublished();
    } catch (makePublicError) {
      setError(makePublicError instanceof Error ? makePublicError.message : "Could not make these notes public.");
    } finally {
      setMakingPublic(false);
    }
  };

  return (
    <AppModal
      isOpen={isOpen}
      title="Publish study plan"
      description="Published plans are discoverable by matching learners and can be adopted into their library."
      onClose={onClose}
      actions={(
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:items-center sm:justify-end">
          {isPublic ? (
            <>
              <Button type="button" variant="outline" loading={togglingVisibility} loadingText="Unpublishing..." disabled={busy} onClick={() => void handleUnpublish()}>
                Unpublish
              </Button>
              <Button type="button" loading={savingCourseProgram} loadingText="Saving..." disabled={busy || !courseProgramDirty} onClick={() => void persistCourseProgram()}>
                Save
              </Button>
            </>
          ) : (
            <>
              <Button type="button" variant="outline" loading={savingCourseProgram && !togglingVisibility} loadingText="Saving..." disabled={busy || !courseProgramDirty} onClick={() => void persistCourseProgram()}>
                Save
              </Button>
              <Button type="button" loading={togglingVisibility} loadingText="Publishing..." disabled={busy || blockedByPrivateNotes} onClick={() => void handlePublish()}>
                Publish
              </Button>
            </>
          )}
        </div>
      )}
    >
      <div className="space-y-4">
        <span
          className={`inline-flex w-fit items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium ${
            isPublic
              ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300"
              : "border-border bg-muted/40 text-foreground/70"
          }`}
        >
          {isPublic ? <Globe className="h-3.5 w-3.5" aria-hidden="true" /> : <Lock className="h-3.5 w-3.5" aria-hidden="true" />}
          {isPublic ? "Published" : "Private"}
        </span>

        <div className="space-y-1.5">
          <span className="text-sm font-medium text-foreground">Course / Program</span>
          <CourseProgramCombobox
            id="publish-course-program"
            value={courseProgram}
            suggestions={courseProgramOptions}
            onChange={setCourseProgram}
            ariaLabel="Course / Program"
            context="profile"
            allowCustom={false}
            inlineDropdown
          />
          {suggestionsError && courseProgramOptions.length === 0 ? (
            <p className="text-xs text-red-600 dark:text-red-400">
              Could not load course/programs.{" "}
              <button type="button" className="font-semibold underline" onClick={() => void loadSuggestions()}>
                Retry
              </button>
            </p>
          ) : (
            <p className="text-xs text-foreground/60">Learners with this course/program will see the plan on their dashboard.</p>
          )}
        </div>

        {privateCount > 0 ? (
          <div className="space-y-2 rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-3">
            <p className="text-sm font-medium text-amber-800 dark:text-amber-200">
              {privateCount} {privateCount === 1 ? "note is" : "notes are"} still private
            </p>
            <p className="text-xs text-foreground/70">
              Adopters copy the notes in this plan, so private notes will be skipped. Make them public so the full plan can be adopted.
            </p>
            <Button type="button" size="sm" variant="outline" loading={makingPublic} loadingText="Making public..." disabled={busy} onClick={() => void handleMakePublic()}>
              {`Make ${privateCount} public`}
            </Button>
          </div>
        ) : null}

        {error ? (
          <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-200">{error}</p>
        ) : null}
      </div>
    </AppModal>
  );
}

function SortableCollectionItemRow({
  item,
  index,
  itemCount,
  disabled,
  collectionId,
  organizeMode,
  showWeakAreas,
  isPrivate,
  sectionNames,
  onMove,
  onRemove,
  onLabelChange,
}: Readonly<{
  item: NoteCollectionItem;
  index: number;
  itemCount: number;
  disabled: boolean;
  collectionId: string;
  organizeMode: boolean;
  showWeakAreas: boolean;
  isPrivate: boolean;
  sectionNames: string[];
  onMove: (noteId: string, direction: "up" | "down") => void;
  onRemove: (noteId: string) => void;
  onLabelChange: (noteId: string, label: string) => void;
}>) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: item.noteId,
    disabled: disabled || !organizeMode,
  });
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  const [labelValue, setLabelValue] = useState(item.label ?? "");
  const sectionOptions = useMemo(
    () => sectionNames.map((name) => ({ value: name, label: name })),
    [sectionNames],
  );

  // Auto-save the section a short beat after the last change (typing or selecting),
  // so the combobox behaves like the rest of the inline plan editing.
  useEffect(() => {
    if (disabled) {
      return;
    }
    const nextLabel = normalizeSectionName(labelValue);
    if (normalizeSectionName(item.label) === nextLabel) {
      return;
    }
    const handle = globalThis.setTimeout(() => onLabelChange(item.noteId, nextLabel), 500);
    return () => globalThis.clearTimeout(handle);
  }, [labelValue, item.label, item.noteId, onLabelChange, disabled]);

  return (
    <li
      ref={setNodeRef}
      style={style}
      className={cn(
        "rounded-xl border border-border bg-background p-4 shadow-sm",
        isDragging && "opacity-70 ring-2 ring-blue-400",
      )}
    >
      <div className={cn("grid gap-4", organizeMode && "lg:grid-cols-[auto_1fr_auto] lg:items-start")}>
        {organizeMode ? (
          <button
            type="button"
            className="inline-flex h-10 w-10 items-center justify-center rounded-lg border border-border text-foreground/55 hover:bg-highlight"
            aria-label={`Drag ${getNoteTitle(item)}`}
            disabled={disabled}
            {...attributes}
            {...listeners}
          >
            <GripVertical className="h-4 w-4" aria-hidden="true" />
          </button>
        ) : null}

        <div className="space-y-3">
          <Link href={`/notes/${item.noteId}?ref=${encodeURIComponent(`/collections/${collectionId}`)}`} className="block rounded-lg p-1 -m-1 hover:bg-highlight">
            <h2 className="text-base font-semibold text-foreground">{getNoteTitle(item)}</h2>
            <p className="text-sm text-foreground/60">{getNoteMeta(item)}</p>
            <div className="mt-1 flex flex-wrap items-center gap-2">
              <p className={`text-xs font-medium ${getNoteExecutionStatus(item).className}`}>{getNoteExecutionStatus(item).label}</p>
              {isPrivate ? (
                <span className="inline-flex items-center gap-1 rounded-full border border-amber-500/30 bg-amber-500/10 px-2 py-0.5 text-xs font-medium text-amber-800 dark:text-amber-200">
                  <Lock className="h-3 w-3" aria-hidden="true" />
                  Private
                </span>
              ) : null}
            </div>
          </Link>
          {showWeakAreas && item.dueConceptCount > 0 ? (
            <div className="rounded-lg border border-amber-500/25 bg-amber-500/10 px-3 py-2">
              <p className="text-sm font-medium text-amber-800 dark:text-amber-200">
                {item.dueConceptCount} {item.dueConceptCount === 1 ? "concept" : "concepts"} due
              </p>
              {item.dueConcepts.length > 0 ? (
                <p className="mt-1 text-xs text-foreground/65">{item.dueConcepts.join(" · ")}</p>
              ) : null}
            </div>
          ) : null}
          {organizeMode ? (
            <div className="block space-y-1.5">
              <span className="text-xs font-medium uppercase tracking-wide text-foreground/50">Section</span>
              <SuggestionCombobox
                id={`section-${item.noteId}`}
                value={labelValue}
                options={sectionOptions}
                onChange={(next) => setLabelValue(next.slice(0, LABEL_MAX_LENGTH))}
                ariaLabel="Section"
                placeholder="Choose or type a section"
                disabled={disabled}
              />
            </div>
          ) : null}
        </div>

        {organizeMode ? (
          <div className="flex flex-wrap gap-2 lg:justify-end">
            <Button type="button" variant="outline" size="sm" disabled={disabled || index === 0} onClick={() => onMove(item.noteId, "up")}>
              Move up
            </Button>
            <Button type="button" variant="outline" size="sm" disabled={disabled || index === itemCount - 1} onClick={() => onMove(item.noteId, "down")}>
              Move down
            </Button>
            <Button type="button" variant="ghost" size="sm" disabled={disabled} onClick={() => onRemove(item.noteId)}>
              Remove
            </Button>
          </div>
        ) : null}
      </div>
    </li>
  );
}

export function CollectionDetailPageClient({ collectionId }: Readonly<{ collectionId: string }>) {
  const router = useRouter();
  const [authUser, setAuthUser] = useState<AuthUser | null>(null);
  useEffect(() => {
    setAuthUser(getAuthUser());
  }, []);
  const currentPlan = (authUser?.planType ?? "FREE") as AppPlanType;
  const isAdmin = authUser?.role === "ADMIN";
  const showWeakAreas = canViewConceptHealth(currentPlan);
  const upgradeCtas = useMemo(() => getUpgradeCtas(currentPlan), [currentPlan]);
  const labels = useMemo(() => getCollectionLabels(authUser?.profileType), [authUser?.profileType]);
  const terminalAction = useMemo(() => getCollectionTerminalAction(authUser?.profileType), [authUser?.profileType]);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [collection, setCollection] = useState<NoteCollectionDetail | null>(null);
  const [goalDetail, setGoalDetail] = useState<GoalCollectionDetailResponse | null>(null);
  const [items, setItems] = useState<NoteCollectionItem[]>([]);
  const [sectionCounts, setSectionCounts] = useState<Map<string, SectionReadiness> | null>(null);
  const [planReadiness, setPlanReadiness] = useState<PlanReadinessResponse | null>(null);
  const [planReadinessLoadState, setPlanReadinessLoadState] = useState<ReadinessLoadState>("idle");
  const [continueDismissed, setContinueDismissed] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [mutationKind, setMutationKind] = useState<MutationKind>(null);
  const [editOpen, setEditOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [publishOpen, setPublishOpen] = useState(false);
  const [actionsMenuOpen, setActionsMenuOpen] = useState(false);
  const actionsMenuRef = useRef<HTMLDivElement | null>(null);
  const [organizeMode] = useState(false);
  const [defaultSectionExpanded, setDefaultSectionExpanded] = useState<boolean | null>(null);
  const [sectionExpandedById, setSectionExpandedById] = useState<Record<string, boolean>>({});
  const [editingSectionId, setEditingSectionId] = useState<string | null>(null);
  const [editingSectionName, setEditingSectionName] = useState("");
  const [pendingSectionRename, setPendingSectionRename] = useState<{ oldName: string; newName: string } | null>(null);
  const [noteVisibility, setNoteVisibility] = useState<Map<string, NoteVisibility>>(new Map());
  const [noteListItems, setNoteListItems] = useState<NoteListItemResponse[]>([]);
  const [noteListLoadFailed, setNoteListLoadFailed] = useState(false);
  const [showReviewFirstModal, setShowReviewFirstModal] = useState(false);
  const [skippedNoticeCount, setSkippedNoticeCount] = useState<number | null>(null);
  const [justAdopted, setJustAdopted] = useState(false);
  const [parentTitle, setParentTitle] = useState<string | null>(null);
  const backLinkHref = collection?.parentCollectionId && parentTitle
    ? `/collections/${collection.parentCollectionId}`
    : "/collections";
  const backLinkLabel = collection?.parentCollectionId && parentTitle ? parentTitle : labels.plural;

  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const loadCollection = useCallback(async () => {
    setLoadState("loading");
    setLoadError(null);
    setNoteListLoadFailed(false);
    try {
      const [result, notesResult] = await Promise.allSettled([
        getCollection(collectionId),
        listNotes(),
      ]);
      if (result.status === "rejected") {
        throw result.reason;
      }
      if (notesResult.status === "fulfilled") {
        setNoteListItems(notesResult.value);
      } else {
        setNoteListItems([]);
        setNoteListLoadFailed(true);
      }
      const collectionResult = result.value;
      const goalResult = collectionResult.childCount > 0
        ? await getCollectionGoal(collectionId)
        : null;
      setCollection(collectionResult);
      setGoalDetail(goalResult);
      setItems(sortCollectionItemsByPosition(collectionResult.items));
      setLoadState("ready");
      if (collectionResult.parentCollectionId) {
        getCollection(collectionResult.parentCollectionId)
          .then((parent) => setParentTitle(parent.title))
          .catch(() => setParentTitle(null));
      } else {
        setParentTitle(null);
      }
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 404) {
        setLoadState("not-found");
        return;
      }
      setLoadError(error instanceof Error ? error.message : "Could not load this collection.");
      setLoadState("error");
    }
  }, [collectionId]);

  useEffect(() => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
    void Promise.resolve().then(loadCollection);
  }, [loadCollection, router]);

  useEffect(() => {
    setDefaultSectionExpanded((globalThis.innerWidth ?? 0) >= LARGE_VIEWPORT_MIN_WIDTH);
  }, []);

  useEffect(() => {
    setSkippedNoticeCount(getStudyPlanSkippedNotice(collectionId));
  }, [collectionId]);

  useEffect(() => {
    setJustAdopted(getJustAdoptedNotice(collectionId));
  }, [collectionId]);

  useEffect(() => {
    setContinueDismissed(false);
    try {
      setContinueDismissed(globalThis.sessionStorage?.getItem(`${CONTINUE_DISMISS_STORAGE_PREFIX}${collectionId}`) === "true");
    } catch {
      setContinueDismissed(false);
    }
  }, [collectionId]);

  useEffect(() => {
    if (loadState !== "ready" || goalDetail || items.length === 0) {
      setSectionCounts(null);
      return;
    }
    let mounted = true;
    void getNoteConceptCounts(collectionId)
      .then((counts) => {
        if (mounted) {
          setSectionCounts(aggregateSectionReadiness(items, counts));
        }
      })
      .catch(() => {
        if (mounted) {
          setSectionCounts(null);
        }
      });
    return () => {
      mounted = false;
    };
  }, [collectionId, goalDetail, items, loadState]);

  useEffect(() => {
    if (loadState !== "ready" || goalDetail) {
      setPlanReadiness(null);
      setPlanReadinessLoadState("idle");
      return;
    }
    let mounted = true;
    setPlanReadinessLoadState("loading");
    void getPlanReadiness(collectionId)
      .then((readiness) => {
        if (mounted) {
          setPlanReadiness(readiness);
          setPlanReadinessLoadState("ready");
        }
      })
      .catch(() => {
        if (mounted) {
          setPlanReadiness(null);
          setPlanReadinessLoadState("error");
        }
      });
    return () => {
      mounted = false;
    };
  }, [collectionId, goalDetail, loadState]);

  const loadNoteVisibility = useCallback(async () => {
    try {
      const notes = await listNotes();
      setNoteVisibility(new Map(notes.map((note) => [note.id, note.visibility])));
    } catch {
      // Visibility badges are admin-only progressive enhancement; ignore failures.
    }
  }, []);

  useEffect(() => {
    if (!isAdmin) {
      return;
    }
    void loadNoteVisibility();
  }, [isAdmin, loadNoteVisibility]);

  useEffect(() => {
    if (!actionsMenuOpen) {
      return;
    }
    const handleOutsideClick = (event: MouseEvent) => {
      const target = event.target as Node;
      if (actionsMenuRef.current && !actionsMenuRef.current.contains(target)) {
        setActionsMenuOpen(false);
      }
    };
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setActionsMenuOpen(false);
      }
    };
    globalThis.addEventListener("mousedown", handleOutsideClick);
    globalThis.addEventListener("keydown", handleEscape);
    return () => {
      globalThis.removeEventListener("mousedown", handleOutsideClick);
      globalThis.removeEventListener("keydown", handleEscape);
    };
  }, [actionsMenuOpen]);

  const refetchAfterFailure = async (message: string) => {
    setMutationError(message);
    try {
      const result = await getCollection(collectionId);
      setCollection(result);
      setGoalDetail(result.childCount > 0 ? await getCollectionGoal(collectionId) : null);
      setItems(sortCollectionItemsByPosition(result.items));
    } catch {
      // Keep the visible error; the page-level retry can recover if this fails too.
    }
  };

  const persistOrder = async (nextItems: NoteCollectionItem[], kind: MutationKind = "reorder") => {
    setMutationKind(kind);
    setMutationError(null);
    const previousItems = items;
    setItems(nextItems.map((item, position) => ({ ...item, position })));
    try {
      const saved = await setCollectionItemOrder(collectionId, buildOrderPayload(nextItems));
      setCollection(saved);
      setItems(sortCollectionItemsByPosition(saved.items));
    } catch (error) {
      setItems(previousItems);
      await refetchAfterFailure(error instanceof Error ? error.message : "Could not save this collection order.");
    } finally {
      setMutationKind(null);
    }
  };

  // Reorder against the grouped display order (sections contiguous) so a within-section
  // move never renumbers another section's positions or reorders the sections themselves.
  const reorderTargetItems = () => (hasSections ? itemSections.flatMap((section) => section.items) : items);

  const isSameSection = (a: NoteCollectionItem, b: NoteCollectionItem) =>
    !hasSections || normalizeSectionName(a.label) === normalizeSectionName(b.label);

  const handleDragEnd = (event: DragEndEvent) => {
    const activeId = String(event.active.id);
    const overId = event.over?.id ? String(event.over.id) : null;
    if (!overId || activeId === overId) {
      return;
    }
    const orderedItems = reorderTargetItems();
    const activeIndex = orderedItems.findIndex((item) => item.noteId === activeId);
    const overIndex = orderedItems.findIndex((item) => item.noteId === overId);
    if (activeIndex < 0 || overIndex < 0) {
      return;
    }
    // Cross-section drag is a no-op; change a note's section with the Section control instead.
    if (!isSameSection(orderedItems[activeIndex], orderedItems[overIndex])) {
      return;
    }
    void persistOrder(arrayMove(orderedItems, activeIndex, overIndex));
  };

  const handleMove = (noteId: string, direction: "up" | "down") => {
    const orderedItems = reorderTargetItems();
    const currentIndex = orderedItems.findIndex((item) => item.noteId === noteId);
    if (currentIndex < 0) {
      return;
    }
    const targetIndex = direction === "up" ? currentIndex - 1 : currentIndex + 1;
    if (targetIndex < 0 || targetIndex >= orderedItems.length) {
      return;
    }
    // Stay within the section; boundaries are also disabled on the Move buttons.
    if (!isSameSection(orderedItems[currentIndex], orderedItems[targetIndex])) {
      return;
    }
    void persistOrder(arrayMove(orderedItems, currentIndex, targetIndex));
  };

  const handleLabelChange = (noteId: string, label: string) => {
    const nextItems = items.map((item) => (
      item.noteId === noteId ? { ...item, label: label || null } : item
    ));
    void persistOrder(nextItems);
  };

  const handleRemove = async (noteId: string) => {
    setMutationKind("remove");
    setMutationError(null);
    try {
      await removeCollectionItem(collectionId, noteId);
      const result = await getCollection(collectionId);
      setCollection(result);
      setGoalDetail(result.childCount > 0 ? await getCollectionGoal(collectionId) : null);
      setItems(sortCollectionItemsByPosition(result.items));
    } catch (error) {
      await refetchAfterFailure(error instanceof Error ? error.message : "Could not remove this note.");
    } finally {
      setMutationKind(null);
    }
  };

  const handleAdd = async (noteIds: string[]) => {
    setMutationKind("add");
    setMutationError(null);
    try {
      const result = await addCollectionItems(collectionId, noteIds);
      setCollection(result);
      setItems(sortCollectionItemsByPosition(result.items));
    } catch (error) {
      await refetchAfterFailure(error instanceof Error ? error.message : "Could not add notes.");
      throw error;
    } finally {
      setMutationKind(null);
    }
  };

  const handleDelete = async () => {
    setMutationKind("delete");
    setMutationError(null);
    try {
      await deleteCollection(collectionId);
      router.push("/collections");
    } catch (error) {
      setMutationError(error instanceof Error ? error.message : "Could not delete this collection.");
      setMutationKind(null);
    }
  };

  const presentNoteIds = useMemo(() => new Set(items.map((item) => item.noteId)), [items]);
  const privateNoteIds = useMemo(
    () => (isAdmin ? items.filter((item) => noteVisibility.get(item.noteId) === "PRIVATE").map((item) => item.noteId) : []),
    [isAdmin, items, noteVisibility],
  );
  const itemIds = useMemo(() => items.map((item) => item.noteId), [items]);
  const { hasSections, sections: itemSections, sectionNames } = useMemo(
    () => getCollectionItemSections(items),
    [items],
  );

  useEffect(() => {
    if (defaultSectionExpanded === null) {
      return;
    }
    if (!hasSections) {
      setSectionExpandedById({});
      return;
    }
    setSectionExpandedById((previous) => {
      const next: Record<string, boolean> = {};
      itemSections.forEach((section) => {
        next[section.id] = previous[section.id] ?? defaultSectionExpanded;
      });
      return next;
    });
  }, [defaultSectionExpanded, hasSections, itemSections]);

  const toggleSectionExpanded = (sectionId: string) => {
    setSectionExpandedById((previous) => ({
      ...previous,
      [sectionId]: !(previous[sectionId] ?? defaultSectionExpanded ?? false),
    }));
  };

  const handleSectionRenameStart = (section: CollectionItemSection) => {
    setEditingSectionId(section.id);
    setEditingSectionName(section.name);
  };

  const handleSectionRenameInput = (value: string) => {
    setEditingSectionName(value);
  };

  const handleSectionRenameCancel = () => {
    setEditingSectionId(null);
    setEditingSectionName("");
  };

  const doSectionRename = (oldName: string, newName: string) => {
    const nextItems = items.map((item) =>
      normalizeSectionName(item.label) === oldName ? { ...item, label: newName } : item,
    );
    void persistOrder(nextItems, "edit");
  };

  const handleSectionRenameCommit = (section: CollectionItemSection, newName: string) => {
    const trimmed = newName.trim();
    setEditingSectionId(null);
    setEditingSectionName("");
    if (!trimmed || trimmed === section.name || trimmed === UNGROUPED_SECTION_NAME) return;
    const targetExists = sectionNames.some((name) => name === trimmed && name !== section.name);
    if (targetExists) {
      setPendingSectionRename({ oldName: section.name, newName: trimmed });
      return;
    }
    doSectionRename(section.name, trimmed);
  };

  const quizReadyNoteIds = useMemo(
    () => getCollectionQuizReadyNoteIds(items),
    [items],
  );
  const premiumExamReadyNoteIds = useMemo(() => getCollectionPremiumExamReadyNoteIds(items), [items]);
  const unpracticedExamNoteCount = useMemo(
    () => items.filter((item) => canIncludeCollectionItemInPremiumExam(item) && item.lastSessionCompletedAt === null).length,
    [items],
  );
  const primaryExamItem = useMemo(() => getCollectionPrimaryPremiumExamItem(items), [items]);
  const noteStudyPackIdByNoteId = useMemo(
    () => new Map(noteListItems.map((noteItem) => [noteItem.id, noteItem.studyPackId])),
    [noteListItems],
  );
  const primaryExamStudyPackId = primaryExamItem ? noteStudyPackIdByNoteId.get(primaryExamItem.noteId) ?? null : null;
  const hasNonQuizReadyItems = quizReadyNoteIds.length < items.length;
  const hasNonPremiumReadyItems = premiumExamReadyNoteIds.length < items.length;
  const mutationInProgress = mutationKind !== null;
  const premiumExamDisabled = noteListLoadFailed
    || !primaryExamItem
    || (terminalAction?.kind === "premium-exam" && terminalAction.mode === "board_exam" && !primaryExamStudyPackId);
  const continueAction = useMemo(() => {
    const latestPracticedItem = getLatestPracticedCollectionItem(items);
    return latestPracticedItem ? getContinuePlanAction(latestPracticedItem, collectionId, showWeakAreas) : null;
  }, [collectionId, items, showWeakAreas]);
  const dueConceptReviewItem = useMemo(() => {
    if (!showWeakAreas || !planReadiness || planReadiness.dueConcepts <= 0) {
      return null;
    }
    return items.find((item) => item.dueConceptCount > 0) ?? null;
  }, [items, planReadiness, showWeakAreas]);
  const dueConceptReviewHref = dueConceptReviewItem
    ? `/notes/${dueConceptReviewItem.noteId}?ref=${encodeURIComponent(`/collections/${collectionId}`)}`
    : null;
  const hasReadinessActions = dueConceptReviewHref !== null || terminalAction?.kind === "premium-exam";
  const postAdoptGuidanceRules: GuidanceRule[] = [
    {
      id: "post-adopt-target-date",
      priority: 1,
      condition: () => justAdopted && !!goalDetail && !goalDetail.targetCompletionDate,
      message: "Set a target completion date to see your weekly countdown and daily study budget.",
    },
  ];
  const activePostAdoptTip = pickActiveGuidance(postAdoptGuidanceRules);

  const dismissContinueBanner = () => {
    setContinueDismissed(true);
    try {
      globalThis.sessionStorage?.setItem(`${CONTINUE_DISMISS_STORAGE_PREFIX}${collectionId}`, "true");
    } catch {
      // Session dismissal is progressive enhancement.
    }
  };

  const openCollectionExamBuilder = useCallback(() => {
    if (quizReadyNoteIds.length === 0) {
      return;
    }
    const params = new URLSearchParams({
      collectionId: collectionId,
      notes: quizReadyNoteIds.join(","),
    });
    router.push(`/library/exam-builder?${params.toString()}`);
  }, [collectionId, quizReadyNoteIds, router]);

  const openCollectionPremiumExam = useCallback(() => {
    if (terminalAction?.kind !== "premium-exam" || premiumExamDisabled || !primaryExamItem) {
      return;
    }
    const params = new URLSearchParams({ collectionId });
    if (terminalAction.mode === "long_exam") {
      router.push(`/notes/${primaryExamItem.noteId}/long-exam?${params.toString()}`);
      return;
    }
    if (terminalAction.mode === "interview") {
      router.push(`/notes/${primaryExamItem.noteId}/interview-practice?${params.toString()}`);
      return;
    }
    if (!primaryExamStudyPackId) {
      return;
    }
    router.push(`/study-packs/${primaryExamStudyPackId}/challenge-quiz?${params.toString()}`);
  }, [collectionId, premiumExamDisabled, primaryExamItem, primaryExamStudyPackId, router, terminalAction]);

  // Advise a quick review before examining a plan whose notes haven't been practiced yet.
  const handlePremiumExamCta = useCallback(() => {
    if (premiumExamDisabled || !primaryExamItem) {
      return;
    }
    if (unpracticedExamNoteCount > 0) {
      setShowReviewFirstModal(true);
      return;
    }
    openCollectionPremiumExam();
  }, [openCollectionPremiumExam, premiumExamDisabled, primaryExamItem, unpracticedExamNoteCount]);

  if (loadState === "loading") {
    return (
      <main className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
        <BackLink href={backLinkHref} label={backLinkLabel} />
        <PageHeader eyebrow={labels.singular.toUpperCase()} title="Loading..." description="Loading this saved set of notes." />
        <Card className="space-y-4 p-6">
          <div className="h-5 w-1/2 animate-pulse rounded bg-muted" />
          <div className="h-20 w-full animate-pulse rounded bg-muted" />
        </Card>
      </main>
    );
  }

  if (loadState === "not-found") {
    return (
      <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
        <BackLink href={backLinkHref} label={backLinkLabel} />
        <Card className="space-y-4 p-6">
          <CardTitle>{labels.singular} not found</CardTitle>
          <CardDescription>This saved set may have been deleted or may not belong to your account.</CardDescription>
          <Link className="inline-flex text-sm font-medium text-blue-600 hover:underline dark:text-blue-400" href="/collections">
            {labels.plural}
          </Link>
        </Card>
      </main>
    );
  }

  if (loadState === "error" || !collection) {
    return (
      <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
        <BackLink href={backLinkHref} label={backLinkLabel} />
        <Card className="space-y-4 p-6">
          <CardTitle>Could not load {labels.singular.toLowerCase()}</CardTitle>
          <CardDescription>{loadError ?? "Please try again."}</CardDescription>
          <Button type="button" variant="outline" onClick={() => void loadCollection()}>Retry</Button>
        </Card>
      </main>
    );
  }

  if (goalDetail) {
    return (
      <main className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
        <BackLink href={backLinkHref} label={backLinkLabel} />
        <PlanHeroCard
          collection={collection}
          eyebrowLabel={labels.goalSingular}
          statusBadge={(
            <span className="inline-flex w-fit items-center rounded-full border border-border bg-background px-2.5 py-1 text-xs font-semibold text-foreground/70">
              {goalDetail.childCount} {labels.subjectSingular}{goalDetail.childCount === 1 ? "" : "s"}
            </span>
          )}
          isAdmin={isAdmin}
          onPublishClick={() => setPublishOpen(true)}
          actions={(
            <div className="flex items-center justify-end gap-2">
              <ResponsiveActionLink
                href={`/collections/${collectionId}/builder`}
                action="build"
                label="Build"
                variant="outline"
                size="sm"
              />
              <div className="relative shrink-0" ref={actionsMenuRef}>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="h-10 w-10 rounded-full px-0"
                  aria-label="Open study plan actions"
                  aria-haspopup="menu"
                  aria-expanded={actionsMenuOpen}
                  onClick={() => setActionsMenuOpen((open) => !open)}
                >
                  <MoreHorizontal className="h-4 w-4" aria-hidden="true" />
                </Button>
                {actionsMenuOpen ? (
                  <div
                    role="menu"
                    aria-label="Study plan actions"
                    className="motion-dropdown-panel absolute right-0 top-12 z-20 w-44 rounded-xl border border-border bg-background p-1.5 shadow-sm"
                  >
                    <button
                      type="button"
                      role="menuitem"
                      className="motion-lift flex w-full items-center rounded-lg px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-highlight active:bg-highlight-strong"
                      onClick={() => { setActionsMenuOpen(false); setEditOpen(true); }}
                    >
                      <ResponsiveActionContent action="edit" label="Edit" showTextOnMobile iconClassName="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      role="menuitem"
                      className="motion-lift flex w-full items-center rounded-lg px-3 py-2 text-left text-sm text-red-700 transition-colors hover:bg-red-50 active:bg-red-100 dark:text-red-400 dark:hover:bg-red-950/40 dark:active:bg-red-950/60"
                      onClick={() => { setActionsMenuOpen(false); setDeleteOpen(true); }}
                    >
                      <ResponsiveActionContent action="delete" label="Delete" showTextOnMobile iconClassName="h-4 w-4" />
                    </button>
                  </div>
                ) : null}
              </div>
            </div>
          )}
        />

        {mutationError ? (
          <Card className="flex items-start justify-between gap-4 border-red-200 bg-red-50 p-4 text-red-800 dark:border-red-900 dark:bg-red-950/30 dark:text-red-200">
            <p className="text-sm">{mutationError}</p>
            <button type="button" aria-label="Dismiss error" onClick={() => setMutationError(null)}>
              <X className="h-4 w-4" aria-hidden="true" />
            </button>
          </Card>
        ) : null}

        {activePostAdoptTip ? (
          <GuidanceTip
            tipId={activePostAdoptTip.id}
            message={activePostAdoptTip.message}
            trackAnalytics
            action={{ label: "Set target date", onClick: () => setEditOpen(true) }}
          />
        ) : null}

        <GoalDetailView goal={goalDetail} labels={labels} />

        <EditCollectionModal
          collection={collection}
          isOpen={editOpen}
          onClose={() => setEditOpen(false)}
          onSaved={(saved) => {
            setCollection(saved);
            // The weekly countdown fields (weeksRemaining/conceptsRemaining/todaysConceptBudget) only
            // exist on GoalCollectionDetailResponse, not on the NoteCollectionDetail this modal saves —
            // a client-side field copy can't refresh them. Refetch the Goal view so an edited target
            // date is reflected immediately instead of going stale until the next page load.
            void getCollectionGoal(collectionId)
              .then(setGoalDetail)
              .catch(() => {
                setGoalDetail((previous) => previous ? {
                  ...previous,
                  title: saved.title,
                  description: saved.description,
                  visibility: saved.visibility,
                  courseProgram: saved.courseProgram,
                  targetCompletionDate: saved.targetCompletionDate,
                  updatedAt: saved.updatedAt,
                } : previous);
              });
            setItems(sortCollectionItemsByPosition(saved.items));
            setEditOpen(false);
          }}
        />
        <DeleteCollectionModal
          isOpen={deleteOpen}
          title={collection.title}
          deleting={mutationKind === "delete"}
          onClose={() => setDeleteOpen(false)}
          onConfirm={() => void handleDelete()}
        />
        {isAdmin ? (
          <PublishStudyPlanModal
            collection={collection}
            isOpen={publishOpen}
            privateNoteIds={privateNoteIds}
            onClose={() => setPublishOpen(false)}
            onSaved={(saved) => {
              setCollection(saved);
              setGoalDetail((previous) => previous ? {
                ...previous,
                visibility: saved.visibility,
                courseProgram: saved.courseProgram,
                updatedAt: saved.updatedAt,
              } : previous);
              setItems(sortCollectionItemsByPosition(saved.items));
            }}
            onNotesPublished={loadNoteVisibility}
          />
        ) : null}
      </main>
    );
  }

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
      <BackLink href={backLinkHref} label={backLinkLabel} />
      <PlanHeroCard
        collection={collection}
        eyebrowLabel={labels.singular}
        statusBadge={(
          <span className="inline-flex w-fit items-center rounded-full border border-border bg-background px-2.5 py-1 text-xs font-semibold text-foreground/70">
            {collection.progress.notesWithStudyPack}/{collection.progress.totalNotes} notes ready
          </span>
        )}
        isAdmin={isAdmin}
        onPublishClick={() => setPublishOpen(true)}
        actions={(
          <div className="flex items-center justify-end gap-2">
            <ResponsiveActionLink
              href={`/collections/${collectionId}/builder`}
              action="build"
              label="Build"
              variant="outline"
              size="sm"
            />
            <ResponsiveActionLink
              href={`/progress?collectionId=${collectionId}`}
              action="progress"
              label="Check readiness"
              variant="outline"
              size="sm"
            />
            <div className="relative shrink-0" ref={actionsMenuRef}>
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="h-10 w-10 rounded-full px-0"
                aria-label="Open study plan actions"
                aria-haspopup="menu"
                aria-expanded={actionsMenuOpen}
                onClick={() => setActionsMenuOpen((open) => !open)}
              >
                <MoreHorizontal className="h-4 w-4" aria-hidden="true" />
              </Button>
              {actionsMenuOpen ? (
                <div
                  role="menu"
                  aria-label="Study plan actions"
                  className="motion-dropdown-panel absolute right-0 top-12 z-20 w-44 rounded-xl border border-border bg-background p-1.5 shadow-sm"
                >
                  <button
                    type="button"
                    role="menuitem"
                    className="motion-lift flex w-full items-center rounded-lg px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-highlight active:bg-highlight-strong"
                    onClick={() => { setActionsMenuOpen(false); setEditOpen(true); }}
                  >
                    <ResponsiveActionContent action="edit" label="Edit" showTextOnMobile iconClassName="h-4 w-4" />
                  </button>
                  <button
                    type="button"
                    role="menuitem"
                    className="motion-lift flex w-full items-center rounded-lg px-3 py-2 text-left text-sm text-red-700 transition-colors hover:bg-red-50 active:bg-red-100 dark:text-red-400 dark:hover:bg-red-950/40 dark:active:bg-red-950/60"
                    onClick={() => { setActionsMenuOpen(false); setDeleteOpen(true); }}
                  >
                    <ResponsiveActionContent action="delete" label="Delete" showTextOnMobile iconClassName="h-4 w-4" />
                  </button>
                </div>
              ) : null}
            </div>
          </div>
        )}
        terminalFooter={terminalAction ? (
          <div className="flex flex-col items-end gap-1">
            <ResponsiveActionButton
              action="open"
              label={terminalAction.label}
              disabled={terminalAction.kind === "exam-builder" ? quizReadyNoteIds.length === 0 : premiumExamDisabled}
              onClick={terminalAction.kind === "exam-builder" ? openCollectionExamBuilder : handlePremiumExamCta}
            />
            {terminalAction.kind === "exam-builder" ? (
              quizReadyNoteIds.length === 0 ? (
                <p className="text-xs text-foreground/60">
                  Generate a quiz for at least one note to build an exam.
                </p>
              ) : hasNonQuizReadyItems ? (
                <p className="text-xs text-foreground/60">
                  Only quiz-ready notes will be included.
                </p>
              ) : null
            ) : premiumExamReadyNoteIds.length === 0 ? (
              <p className="text-xs text-foreground/60">
                Generate a Study Pack for at least one note to start an exam.
              </p>
            ) : hasNonPremiumReadyItems ? (
              <p className="text-xs text-foreground/60">
                Only Study Pack-ready notes will be included.
              </p>
            ) : null}
          </div>
        ) : undefined}
      />

      <section className={cn("grid gap-4", hasReadinessActions ? "lg:grid-cols-[minmax(0,1fr)_320px]" : "")}>
        <ReadinessSummary
          variant="compact"
          title={`${collection.title} readiness`}
          eyebrow={`${labels.singular} readiness`}
          overallReadinessPercentage={planReadiness?.overallReadinessPercentage ?? 0}
          totalConcepts={planReadiness?.totalConcepts ?? 0}
          masteredConcepts={planReadiness?.masteredConcepts ?? 0}
          dueConcepts={planReadiness?.dueConcepts ?? 0}
          notPracticedConcepts={planReadiness?.notPracticedConcepts ?? 0}
          subjects={planReadiness?.subjects ?? []}
          unavailable={planReadinessLoadState === "error"}
          unavailableDescription="Readiness is unavailable right now. Try refreshing this plan."
          emptyTitle="No readiness yet"
          emptyDescription={planReadinessLoadState === "loading" ? "Loading readiness..." : "Generate Study Packs and practice to see readiness."}
        />

        {hasReadinessActions ? (
          <Card className="flex flex-col justify-center gap-3 p-4 sm:p-5">
            <div className="space-y-1">
              <CardTitle className="text-base">Next mastery steps</CardTitle>
              <CardDescription>Act on what the readiness check found.</CardDescription>
            </div>
            <div className="flex flex-col gap-2">
              {dueConceptReviewHref ? (
                <ResponsiveActionLink
                  href={dueConceptReviewHref}
                  action="quickReview"
                  label="Review due concepts"
                  variant="outline"
                />
              ) : null}
              {terminalAction?.kind === "premium-exam" ? (
                <ResponsiveActionButton
                  action="open"
                  label={`Prove it - ${terminalAction.label}`}
                  disabled={premiumExamDisabled}
                  onClick={handlePremiumExamCta}
                />
              ) : null}
            </div>
          </Card>
        ) : null}
      </section>

      <CollectionProgressSummary collection={collection} />

      <NextInPlanCard
        items={items}
        collectionId={collectionId}
        canReviewDueConcepts={showWeakAreas}
      />

      {skippedNoticeCount ? (
        <Card className="border-amber-500/25 bg-amber-500/10 p-4 text-sm text-foreground/75">
          {skippedNoticeCount} {skippedNoticeCount === 1 ? "item is" : "items are"} no longer available and were left out.
        </Card>
      ) : null}

      {mutationError ? (
        <Card className="flex items-start justify-between gap-4 border-red-200 bg-red-50 p-4 text-red-800 dark:border-red-900 dark:bg-red-950/30 dark:text-red-200">
          <p className="text-sm">{mutationError}</p>
          <button type="button" aria-label="Dismiss error" onClick={() => setMutationError(null)}>
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </Card>
      ) : null}

      {continueAction && !continueDismissed ? (
        <ContinuePlanBanner action={continueAction} onDismiss={dismissContinueBanner} />
      ) : null}

      <Card className="space-y-4 p-4 sm:p-6">
        <div>
          <CardTitle>Notes</CardTitle>
          <CardDescription>{items.length} {items.length === 1 ? "note" : "notes"} in saved order.</CardDescription>
        </div>

        {!showWeakAreas && items.length > 0 && upgradeCtas.primary ? (
          <div className="flex flex-col gap-2 rounded-lg border border-border bg-muted/40 px-3 py-3 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm text-foreground/65">See which concepts are due for review in each note.</p>
            <Link
              href="/settings?section=plans"
              className="text-sm font-semibold text-blue-700 hover:underline dark:text-blue-300"
            >
              {upgradeCtas.primary.label}
            </Link>
          </div>
        ) : null}

        {items.length === 0 ? (
          <div className="rounded-xl border border-dashed border-border p-6 text-center">
            <p className="text-sm text-foreground/70">Add notes to start organizing this {labels.singular.toLowerCase()}.</p>
          </div>
        ) : (
          <>
            {organizeMode ? (
              <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
                {hasSections ? (
                  <div className="space-y-5">
                    {itemSections.map((section, sectionIndex) => {
                      const isExpanded = sectionExpandedById[section.id] ?? defaultSectionExpanded ?? false;
                      const sectionContentId = `collection-section-${sectionIndex}`;
                      return (
                        <section key={section.id} aria-labelledby={`${sectionContentId}-heading`} className="rounded-xl border border-border bg-background p-4 shadow-sm sm:p-5">
                          <SectionCardHeader
                            section={section}
                            isExpanded={isExpanded}
                            organizeMode={true}
                            editingSectionId={editingSectionId}
                            editingSectionName={editingSectionName}
                            headingId={`${sectionContentId}-heading`}
                            onToggle={() => toggleSectionExpanded(section.id)}
                            onRenameStart={handleSectionRenameStart}
                            onRenameInput={handleSectionRenameInput}
                            onRenameCommit={handleSectionRenameCommit}
                            onRenameCancel={handleSectionRenameCancel}
                          />
                          {isExpanded ? (
                            <SortableContext items={section.items.map((item) => item.noteId)} strategy={verticalListSortingStrategy}>
                              <div className="mt-3 border-t border-border pt-3">
                                <ol id={sectionContentId} className="space-y-3">
                                  {section.items.map((item, localIndex) => (
                                    <SortableCollectionItemRow
                                      key={`${item.noteId}:${item.label ?? ""}`}
                                      item={item}
                                      index={localIndex}
                                      itemCount={section.items.length}
                                      disabled={mutationInProgress}
                                      collectionId={collectionId}
                                      organizeMode={organizeMode}
                                      showWeakAreas={showWeakAreas}
                                      isPrivate={isAdmin && noteVisibility.get(item.noteId) === "PRIVATE"}
                                      sectionNames={sectionNames}
                                      onMove={handleMove}
                                      onRemove={(noteId) => void handleRemove(noteId)}
                                      onLabelChange={handleLabelChange}
                                    />
                                  ))}
                                </ol>
                              </div>
                            </SortableContext>
                          ) : null}
                        </section>
                      );
                    })}
                  </div>
                ) : (
                  <SortableContext items={itemIds} strategy={verticalListSortingStrategy}>
                    <ol className="space-y-3">
                      {items.map((item, index) => (
                        <SortableCollectionItemRow
                          key={`${item.noteId}:${item.label ?? ""}`}
                          item={item}
                          index={index}
                          itemCount={items.length}
                          disabled={mutationInProgress}
                          collectionId={collectionId}
                          organizeMode={organizeMode}
                          showWeakAreas={showWeakAreas}
                          isPrivate={isAdmin && noteVisibility.get(item.noteId) === "PRIVATE"}
                          sectionNames={sectionNames}
                          onMove={handleMove}
                          onRemove={(noteId) => void handleRemove(noteId)}
                          onLabelChange={handleLabelChange}
                        />
                      ))}
                    </ol>
                  </SortableContext>
                )}
              </DndContext>
            ) : hasSections ? (
              <div className="space-y-5">
                {itemSections.map((section, sectionIndex) => {
                  const isExpanded = sectionExpandedById[section.id] ?? defaultSectionExpanded ?? false;
                  const sectionContentId = `collection-section-${sectionIndex}`;
                  return (
                    <section key={section.id} aria-labelledby={`${sectionContentId}-heading`} className="rounded-xl border border-border bg-background p-4 shadow-sm sm:p-5">
                      <SectionCardHeader
                        section={section}
                        isExpanded={isExpanded}
                        organizeMode={false}
                        sectionReadiness={sectionCounts?.get(section.name) ?? null}
                        headingId={`${sectionContentId}-heading`}
                        onToggle={() => toggleSectionExpanded(section.id)}
                      />
                      {isExpanded ? (
                        <div className="mt-3 border-t border-border pt-3">
                          <ol id={sectionContentId} className="space-y-3">
                            {section.items.map((item, localIndex) => (
                              <SortableCollectionItemRow
                                key={`${item.noteId}:${item.label ?? ""}`}
                                item={item}
                                index={localIndex}
                                itemCount={section.items.length}
                                disabled={mutationInProgress}
                                collectionId={collectionId}
                                organizeMode={organizeMode}
                                showWeakAreas={showWeakAreas}
                                isPrivate={isAdmin && noteVisibility.get(item.noteId) === "PRIVATE"}
                                sectionNames={sectionNames}
                                onMove={handleMove}
                                onRemove={(noteId) => void handleRemove(noteId)}
                                onLabelChange={handleLabelChange}
                              />
                            ))}
                          </ol>
                        </div>
                      ) : null}
                    </section>
                  );
                })}
              </div>
            ) : (
              <ol className="space-y-3">
                {items.map((item, index) => (
                  <SortableCollectionItemRow
                    key={`${item.noteId}:${item.label ?? ""}`}
                    item={item}
                    index={index}
                    itemCount={items.length}
                    disabled={mutationInProgress}
                    collectionId={collectionId}
                    organizeMode={organizeMode}
                    showWeakAreas={showWeakAreas}
                    isPrivate={isAdmin && noteVisibility.get(item.noteId) === "PRIVATE"}
                    sectionNames={sectionNames}
                    onMove={handleMove}
                    onRemove={(noteId) => void handleRemove(noteId)}
                    onLabelChange={handleLabelChange}
                  />
                ))}
              </ol>
            )}
          </>
        )}
      </Card>

      <EditCollectionModal
        collection={collection}
        isOpen={editOpen}
        onClose={() => setEditOpen(false)}
        onSaved={(saved) => {
          setCollection(saved);
          setItems(sortCollectionItemsByPosition(saved.items));
          setEditOpen(false);
        }}
      />
      <DeleteCollectionModal
        isOpen={deleteOpen}
        title={collection.title}
        deleting={mutationKind === "delete"}
        onClose={() => setDeleteOpen(false)}
        onConfirm={() => void handleDelete()}
      />
      <AddNotesModal
        isOpen={addOpen}
        presentNoteIds={presentNoteIds}
        onClose={() => setAddOpen(false)}
        onAdd={handleAdd}
      />
      {isAdmin ? (
        <PublishStudyPlanModal
          collection={collection}
          isOpen={publishOpen}
          privateNoteIds={privateNoteIds}
          onClose={() => setPublishOpen(false)}
          onSaved={(saved) => {
            setCollection(saved);
            setItems(sortCollectionItemsByPosition(saved.items));
          }}
          onNotesPublished={loadNoteVisibility}
        />
      ) : null}
      <AppModal
        isOpen={pendingSectionRename !== null}
        title={`Merge into "${pendingSectionRename?.newName ?? ""}"`}
        description={`Section "${pendingSectionRename?.newName ?? ""}" already exists. All notes from "${pendingSectionRename?.oldName ?? ""}" will be moved into it.`}
        onClose={() => setPendingSectionRename(null)}
        actions={(
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={() => setPendingSectionRename(null)}>
              Cancel
            </Button>
            <Button
              type="button"
              onClick={() => {
                if (pendingSectionRename) {
                  doSectionRename(pendingSectionRename.oldName, pendingSectionRename.newName);
                  setPendingSectionRename(null);
                }
              }}
            >
              Merge sections
            </Button>
          </div>
        )}
      />
      <AppModal
        isOpen={showReviewFirstModal}
        title="Review before the exam?"
        description={`You haven't practiced ${unpracticedExamNoteCount} of ${premiumExamReadyNoteIds.length} ${premiumExamReadyNoteIds.length === 1 ? "note" : "notes"} in this ${labels.singular.toLowerCase()} yet. A quick review or Challenge Quiz first usually makes the exam more useful — but you can jump straight in.`}
        onClose={() => setShowReviewFirstModal(false)}
        panelClassName="max-w-[440px]"
        actions={(
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={() => setShowReviewFirstModal(false)}>
              Review first
            </Button>
            <Button
              type="button"
              onClick={() => {
                setShowReviewFirstModal(false);
                openCollectionPremiumExam();
              }}
            >
              Start the exam anyway
            </Button>
          </div>
        )}
      />
    </main>
  );
}
