"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { DndContext, PointerSensor, KeyboardSensor, closestCenter, useSensor, useSensors, type DragEndEvent } from "@dnd-kit/core";
import { SortableContext, arrayMove, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { ArrowRight, ChevronDown, GripVertical, Globe, Lock, MoreHorizontal, Search, Settings2, Star, X } from "lucide-react";
import { AppModal } from "@/components/ui/app-modal";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { GuidanceTip } from "@/components/ui/guidance-tip";
import { SuggestionCombobox } from "@/components/ui/suggestion-combobox";
import { PageHeader } from "@/components/page-header";
import { ReadinessSummary } from "@/components/readiness/readiness-summary";
import { ResponsiveActionButton, ResponsiveActionContent, ResponsiveActionLink } from "@/components/ui/action-button";
import { SummaryMarkdown } from "@/components/ui/summary-markdown";
import { ToastMessage } from "@/components/ui/toast-message";
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
  clearCompanion,
  clearPrimaryCollection,
  deleteCollection,
  getCollection,
  getCollectionGoal,
  getMe,
  getNoteConceptCounts,
  getPlanReadiness,
  generateCompanion,
  listCoursePrograms,
  listNotes,
  removeCollectionItem,
  setCollectionItemOrder,
  setCompanion,
  setPrimaryCollection,
  trackAnalyticsEvent,
  updateCollection,
  updateCollectionVisibility,
  updateNoteVisibility,
  updateStudyDaysPerWeek,
  type CompanionContent,
  type CompanionMentorTip,
  type CompanionMentorTipAction,
  type CompanionMentorTipSurfacingCondition,
  type CompanionMentorTipSurfacingConditionType,
  type CompanionSection,
  type GoalCollectionDetailResponse,
  type NoteConceptCountsResponse,
  type NoteCollectionDetail,
  type NoteCollectionItem,
  type NoteListItemResponse,
  type NoteVisibility,
  type PlanReadinessResponse,
} from "@/lib/api";
import { getStudyPlanSkippedNotice } from "@/app/dashboard/dashboard-study-plan-section";
import {
  CHALLENGE_QUIZ_ENTRY_QUERY_PARAM,
  CHALLENGE_QUIZ_MODE_SELECTION_ENTRY,
} from "@/lib/challenge-quiz-entry";
import { getJustAdoptedNotice } from "@/lib/just-adopted-notice";
import { stripMarkdownForPreview } from "@/lib/public-note-text";
import { setCollectionActionNotice } from "@/lib/collection-action-notice";
import { pickActiveGuidance, type GuidanceRule } from "@/lib/guidance-engine";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { cn } from "@/lib/utils";
import {
  ASK_COMPANION_DRAFT_QUERY_PARAM,
  hasRenderableCompanionContent,
} from "@/lib/companion";
import { getUpgradeCtas, type AppPlanType } from "@/src/config/plans";
import { AskCompanionPanel } from "@/components/collections/ask-companion-panel";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";

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
const TODAY_FOCUS_EYEBROW = "Today's Focus";
const CONTINUE_STUDYING_LABEL = "Continue Studying";
const QUICK_ACTIONS_LABEL = "Quick Actions";
const REVIEW_DUE_CONCEPTS_LABEL = "Review Due Concepts";
const FOCUS_DONE_MESSAGE = "You've worked through everything here. Nice work.";
const FOCUS_NO_TARGET_MESSAGE = "Pick up where you left off — you've got this.";
const MENTOR_TIP_GUIDE_HEADING_ID = "companion-mentor-tips-heading";
const MENTOR_TIP_GUIDE_HEADING = "Quick tips";
const MENTOR_TIP_GUIDE_ICON = "💡";
const MENTOR_TIP_ACTION_LABELS: Record<CompanionMentorTipAction, string> = {
  NONE: "None",
  CONTINUE_STUDYING: CONTINUE_STUDYING_LABEL,
  REVIEW_DUE_CONCEPTS: REVIEW_DUE_CONCEPTS_LABEL,
  TERMINAL_ACTION: "Terminal Action",
};
const MENTOR_TIP_SURFACING_LABELS: Record<CompanionMentorTipSurfacingConditionType, string> = {
  DAYS_BEFORE_TARGET_DATE: "Days before target date",
  AFTER_SUBJECTS_COMPLETED: "After subjects completed",
};

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

function ExpandableDescription({
  description,
  collapsedClassName,
  className,
}: Readonly<{
  description: string;
  collapsedClassName: string;
  className?: string;
}>) {
  const descriptionRef = useRef<HTMLParagraphElement>(null);
  const [expandedDescription, setExpandedDescription] = useState<string | null>(null);
  const [isClamped, setIsClamped] = useState(false);
  const isExpanded = expandedDescription === description;

  useEffect(() => {
    if (isExpanded || !descriptionRef.current) {
      return;
    }

    const updateClampedState = () => {
      const element = descriptionRef.current;
      if (element) {
        setIsClamped(element.scrollHeight > element.clientHeight);
      }
    };

    updateClampedState();
    if (typeof ResizeObserver === "undefined") {
      return;
    }

    const resizeObserver = new ResizeObserver(updateClampedState);
    resizeObserver.observe(descriptionRef.current);
    return () => resizeObserver.disconnect();
  }, [description, isExpanded]);

  return (
    <div className="space-y-1">
      <p
        ref={descriptionRef}
        className={cn(
          "text-base leading-relaxed text-foreground/75",
          !isExpanded && collapsedClassName,
          className,
        )}
      >
        {description}
      </p>
      {isClamped || isExpanded ? (
        <button
          type="button"
          className="text-sm font-medium text-blue-700 hover:underline dark:text-blue-300"
          onClick={(event) => {
            event.preventDefault();
            event.stopPropagation();
            setExpandedDescription((expanded) => (expanded === description ? null : description));
          }}
        >
          {isExpanded ? "Show less" : "Read more"}
        </button>
      ) : null}
    </div>
  );
}

function PlanHeroCard({
  collection,
  eyebrowLabel,
  isPrimary,
  metadataLine,
  actions,
}: Readonly<{
  collection: NoteCollectionDetail;
  eyebrowLabel: string;
  isPrimary: boolean;
  metadataLine?: ReactNode;
  actions?: ReactNode;
}>) {
  // Shown only when sourcePlanId != null (adopted from a public source) — nothing renders for
  // self-created collections; unlabeled implies "yours," matching the /collections list treatment.
  const adopted = Boolean(collection.sourcePlanId);

  return (
    <Card className={cn("space-y-4 p-5 sm:p-6", isPrimary && "border-l-4 border-l-indigo-500 bg-indigo-500/[0.03] dark:border-l-indigo-400")}>
      {/* flex-col on mobile, row from sm: up. min-w-0 + flex-1 on the title column lets the browser
          shrink it toward zero width instead of wrapping the (shrink-0) actions column below it once
          both no longer fit on one row — that's what produced the word-per-line "crumpled" title on
          narrow screens. Stacking removes the row-fit constraint below sm: entirely. */}
      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-start sm:justify-between">
        <div className="min-w-0 space-y-3 sm:flex-1">
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">{eyebrowLabel}</p>
            <div className="flex flex-wrap items-center gap-2">
              <CardTitle className="text-2xl sm:text-3xl">{collection.title}</CardTitle>
              {adopted ? (
                <span className="inline-flex w-fit items-center rounded-full border border-border bg-muted/40 px-2.5 py-1 text-xs font-medium text-foreground/65">
                  Adopted
                </span>
              ) : null}
            </div>
            {isPrimary ? (
              <p className="inline-flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-indigo-700 dark:text-indigo-300">
                <Star className="h-3.5 w-3.5 fill-current" aria-hidden="true" />
                Primary
              </p>
            ) : null}
            {collection.description ? (
              <ExpandableDescription
                description={collection.description}
                collapsedClassName="line-clamp-3"
                className="text-sm sm:text-base"
              />
            ) : null}
            {metadataLine ? (
              <p className="text-sm text-foreground/60">{metadataLine}</p>
            ) : null}
          </div>
        </div>
        {actions ? <div className="shrink-0">{actions}</div> : null}
      </div>
    </Card>
  );
}

type ResolvedPrimaryAction = {
  title: string;
  description: string;
  href: string;
};

type ResolvedMentorTip = {
  tip: CompanionMentorTip;
  actionLabel: string | null;
};

function formatConceptCount(count: number): string {
  return `${count} ${count === 1 ? "concept" : "concepts"}`;
}

function normalizeMentorTipText(value: string | null | undefined): string {
  return value?.trim() ?? "";
}

function hasRenderableMentorTip(tip: CompanionMentorTip | null | undefined): boolean {
  return Boolean(normalizeMentorTipText(tip?.title) || normalizeMentorTipText(tip?.body));
}

function localDateToStartOfDay(isoDate: string): Date {
  const [year, month, day] = isoDate.split("-").map(Number);
  return new Date(year, month - 1, day);
}

function daysUntilLocalDate(targetCompletionDate: string): number {
  const target = localDateToStartOfDay(targetCompletionDate);
  const today = new Date();
  const todayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  return Math.round((target.getTime() - todayStart.getTime()) / 86_400_000);
}

function isMentorTipEligible(
  tip: CompanionMentorTip,
  targetCompletionDate: string | null,
  completedSubjectCount: number | null,
): boolean {
  if (!hasRenderableMentorTip(tip)) {
    return false;
  }
  const condition = tip.surfacingCondition;
  if (!condition) {
    return true;
  }
  if (condition.threshold < 0) {
    return false;
  }
  if (condition.type === "DAYS_BEFORE_TARGET_DATE") {
    return targetCompletionDate !== null && daysUntilLocalDate(targetCompletionDate) <= condition.threshold;
  }
  if (condition.type === "AFTER_SUBJECTS_COMPLETED") {
    return completedSubjectCount !== null && completedSubjectCount >= condition.threshold;
  }
  return false;
}

// Labels only — never a second href/node. The action a tip points to (Continue Studying,
// Review Due Concepts, the terminal action) already renders as its own clickable CTA elsewhere
// in TodaysFocusCard; resolving a duplicate clickable target here would just repeat it.
function resolveMentorTipAction(tip: CompanionMentorTip, terminalActionLabel: string | null): ResolvedMentorTip {
  const linkedAction = tip.linkedAction ?? "NONE";
  if (linkedAction === "CONTINUE_STUDYING") {
    return { tip, actionLabel: CONTINUE_STUDYING_LABEL };
  }
  if (linkedAction === "REVIEW_DUE_CONCEPTS") {
    return { tip, actionLabel: REVIEW_DUE_CONCEPTS_LABEL };
  }
  if (linkedAction === "TERMINAL_ACTION") {
    return { tip, actionLabel: terminalActionLabel ?? MENTOR_TIP_ACTION_LABELS.TERMINAL_ACTION };
  }
  return { tip, actionLabel: null };
}

function getFirstEligibleMentorTip(
  companion: CompanionContent | null,
  targetCompletionDate: string | null,
  completedSubjectCount: number | null,
  terminalActionLabel: string | null,
): ResolvedMentorTip | null {
  const eligibleTip = (companion?.mentorTips ?? []).find((tip) => (
    isMentorTipEligible(tip, targetCompletionDate, completedSubjectCount)
  ));
  return eligibleTip
    ? resolveMentorTipAction(eligibleTip, terminalActionLabel)
    : null;
}

function buildCountdownLine(
  targetCompletionDate: string | null | undefined,
  weeksRemaining: number | null | undefined,
  conceptsRemaining: number | null | undefined,
): ReactNode {
  if (!targetCompletionDate || weeksRemaining === null || weeksRemaining === undefined || conceptsRemaining === null || conceptsRemaining === undefined) {
    return null;
  }
  const weekLabel = weeksRemaining === 1 ? "1 week" : `${weeksRemaining} weeks`;
  return `${weekLabel} until ${formatLocalDate(targetCompletionDate)} · ${formatConceptCount(conceptsRemaining)} remaining`;
}

function TodaysFocusCard({
  action,
  terminalAction,
  dueConceptReviewHref,
  todaysConceptBudget,
  hasTargetDate,
  mentorTip,
}: Readonly<{
  action: ResolvedPrimaryAction | null;
  terminalAction?: ReactNode;
  dueConceptReviewHref: string | null;
  todaysConceptBudget: number | null;
  hasTargetDate: boolean;
  mentorTip?: ResolvedMentorTip | null;
}>) {
  const coachingSentence = action
    ? hasTargetDate
      ? todaysConceptBudget === null
        ? null
        : todaysConceptBudget > 0
          ? `Study about ${formatConceptCount(todaysConceptBudget)} today to stay on pace.`
          : "You're on pace — no new concepts scheduled today."
      : FOCUS_NO_TARGET_MESSAGE
    : null;
  const hasQuickActions = dueConceptReviewHref !== null || Boolean(terminalAction);

  return (
    <Card className="space-y-4 border-blue-500/25 bg-blue-500/5 p-4 sm:p-5">
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">{TODAY_FOCUS_EYEBROW}</p>
        {action ? (
          <>
            <CardTitle>{action.title}</CardTitle>
            <CardDescription>{action.description}</CardDescription>
            {coachingSentence ? <p className="pt-1 text-sm text-foreground/75">{coachingSentence}</p> : null}
          </>
        ) : (
          <p className="text-sm font-medium text-foreground/80">{FOCUS_DONE_MESSAGE}</p>
        )}
      </div>
      {mentorTip ? (
        <div className="space-y-3 rounded-lg border border-blue-500/20 bg-background/70 p-3">
          <div className="space-y-1">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">Mentor Tip</p>
            {normalizeMentorTipText(mentorTip.tip.title) ? (
              <h3 className="text-sm font-semibold text-foreground">{normalizeMentorTipText(mentorTip.tip.title)}</h3>
            ) : null}
            {normalizeMentorTipText(mentorTip.tip.body) ? (
              <p className="text-sm text-foreground/75">{normalizeMentorTipText(mentorTip.tip.body)}</p>
            ) : null}
          </div>
          {mentorTip.actionLabel ? (
            // Informational only, not a link/button: the same action already renders as its own
            // clickable CTA below (Continue Studying) or in Quick Actions (Review Due Concepts,
            // terminal action) — a second clickable element here would just duplicate it.
            <p className="text-sm font-semibold text-foreground/55">{mentorTip.actionLabel}</p>
          ) : null}
        </div>
      ) : null}
      <div className="flex flex-col items-start gap-2">
        {action ? (
          <Link
            href={action.href}
            className="inline-flex w-full min-h-10 items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-400 sm:w-auto"
          >
            {CONTINUE_STUDYING_LABEL}
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Link>
        ) : null}
        {hasQuickActions ? (
          <div className="w-full space-y-2 pt-1">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">{QUICK_ACTIONS_LABEL}</p>
            <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-start">
              {dueConceptReviewHref ? (
                <ResponsiveActionLink
                  href={dueConceptReviewHref}
                  action="quickReview"
                  label={REVIEW_DUE_CONCEPTS_LABEL}
                  variant="outline"
                  size="sm"
                  className="w-full sm:w-auto"
                />
              ) : null}
              {terminalAction}
            </div>
          </div>
        ) : null}
      </div>
    </Card>
  );
}

// Rendered as ReadinessSummary's `footer` slot so the readiness stats and the
// deep progress action read as one card, not two stacked elements.
function ReadinessCardFooter({
  collectionId,
}: Readonly<{
  collectionId: string;
}>) {
  return (
    <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
      <Link
        href={`/progress?collectionId=${collectionId}`}
        className="text-sm font-semibold text-blue-700 hover:underline dark:text-blue-300"
      >
        View full progress
      </Link>
    </div>
  );
}

// Compact chrome: authoring/curation controls mounted in the hero's top-right corner, not a
// dedicated page section — these change the {label} itself, they are not a study action, so they
// stay visually minimal wherever they sit (see docs/features/collections.md).
function CollectionActionsMenu({
  collection,
  labels,
  isAdmin,
  canManageCompanion,
  primaryActionLabel,
  onEditClick,
  onPrimaryClick,
  onCompanionClick,
  onDeleteClick,
  onPublishClick,
}: Readonly<{
  collection: NoteCollectionDetail;
  labels: ReturnType<typeof getCollectionLabels>;
  isAdmin: boolean;
  canManageCompanion: boolean;
  primaryActionLabel: string;
  onEditClick: () => void;
  onPrimaryClick: () => void;
  onCompanionClick: () => void;
  onDeleteClick: () => void;
  onPublishClick: () => void;
}>) {
  const [actionsMenuOpen, setActionsMenuOpen] = useState(false);
  const actionsMenuRef = useRef<HTMLDivElement | null>(null);

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

  return (
    <div className="flex flex-wrap items-center gap-2">
      {isAdmin ? (
        <button
          type="button"
          onClick={onPublishClick}
          aria-label="Publish settings"
          title="Publish settings"
          className="motion-lift inline-flex w-fit items-center gap-1.5 rounded-full border border-border bg-background px-2.5 py-1 text-xs font-medium text-foreground/70 transition-colors hover:bg-highlight"
        >
          {collection.visibility === "PUBLIC" ? (
            <><Globe className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" aria-hidden="true" />Published</>
          ) : (
            <><Lock className="h-3.5 w-3.5" aria-hidden="true" />Private</>
          )}
          <Settings2 className="h-3 w-3 opacity-60" aria-hidden="true" />
        </button>
      ) : null}
      <ResponsiveActionLink
        href={`/collections/${collection.id}/builder`}
        action="build"
        label="Build"
        variant="outline"
        size="sm"
        showTextOnMobile={false}
      />
      <div className="relative shrink-0" ref={actionsMenuRef}>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="h-9 w-9 rounded-full px-0"
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
            className="motion-dropdown-panel absolute right-0 top-11 z-20 w-44 rounded-xl border border-border bg-background p-1.5 shadow-sm"
          >
            <button
              type="button"
              role="menuitem"
              className="motion-lift flex w-full items-center rounded-lg px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-highlight active:bg-highlight-strong"
              onClick={() => { setActionsMenuOpen(false); onEditClick(); }}
            >
              <ResponsiveActionContent action="edit" label="Edit" showTextOnMobile iconClassName="h-4 w-4" />
            </button>
            {collection.parentCollectionId === null ? (
              <button
                type="button"
                role="menuitem"
                className="motion-lift flex w-full items-center rounded-lg px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-highlight active:bg-highlight-strong"
                onClick={() => { setActionsMenuOpen(false); onPrimaryClick(); }}
              >
                <ResponsiveActionContent action="primary" label={primaryActionLabel} showTextOnMobile iconClassName="h-4 w-4" />
              </button>
            ) : null}
            {isAdmin && canManageCompanion ? (
              <button
                type="button"
                role="menuitem"
                className="motion-lift flex w-full items-center rounded-lg px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-highlight active:bg-highlight-strong"
                onClick={() => { setActionsMenuOpen(false); onCompanionClick(); }}
              >
                <ResponsiveActionContent action="companion" label={`Manage ${labels.companionSingular}`} showTextOnMobile iconClassName="h-4 w-4" />
              </button>
            ) : null}
            <button
              type="button"
              role="menuitem"
              className="motion-lift flex w-full items-center rounded-lg px-3 py-2 text-left text-sm text-red-700 transition-colors hover:bg-red-50 active:bg-red-100 dark:text-red-400 dark:hover:bg-red-950/40 dark:active:bg-red-950/60"
              onClick={() => { setActionsMenuOpen(false); onDeleteClick(); }}
            >
              <ResponsiveActionContent action="delete" label="Delete" showTextOnMobile iconClassName="h-4 w-4" />
            </button>
          </div>
        ) : null}
      </div>
    </div>
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

function renderableCompanionText(value: string | null | undefined): string {
  return value?.trim() ?? "";
}

// Coach-voice framing over the curator-authored Companion sections. Purely presentational —
// same text, same author, same order; this only swaps the heading copy, it does not
// reorder or select which sections render (see docs/product/ROADMAP.md's Coach Experience section).
const COMPANION_COACH_HEADINGS = {
  overview: { icon: "🗺️", label: "What this covers" },
  studyStrategy: { icon: "🧭", label: "How to study this" },
  commonMistakes: { icon: "⚠️", label: "Avoid these traps" },
  faq: { icon: "💬", label: "Common questions" },
  resources: { icon: "📎", label: "Extra resources" },
} as const;

type CompanionHeadingKey = keyof typeof COMPANION_COACH_HEADINGS;

function CompanionGuideSection({
  headingKey,
  headingId,
  children,
}: Readonly<{
  headingKey: CompanionHeadingKey;
  headingId: string;
  children: ReactNode;
}>) {
  const heading = COMPANION_COACH_HEADINGS[headingKey];
  const isWarning = headingKey === "commonMistakes";

  return (
    <section
      className={cn(
        "space-y-3 rounded-lg border border-border bg-muted/30 p-4",
        isWarning && "border-amber-500/25 bg-amber-500/10",
      )}
      aria-labelledby={headingId}
    >
      <div className="flex items-center gap-3">
        <span
          className={cn(
            "inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-background text-base shadow-sm",
            isWarning && "bg-amber-100 text-amber-800 dark:bg-amber-950/50 dark:text-amber-200",
          )}
          aria-hidden="true"
        >
          {heading.icon}
        </span>
        <h3 id={headingId} className="text-sm font-semibold text-foreground">{heading.label}</h3>
      </div>
      <div className="space-y-3 pl-0 sm:pl-11">{children}</div>
    </section>
  );
}

function MentorTipsGuideSection({
  mentorTips,
}: Readonly<{
  mentorTips: CompanionMentorTip[];
}>) {
  const renderableTips = mentorTips.filter(hasRenderableMentorTip);
  if (renderableTips.length === 0) {
    return null;
  }

  return (
    <section
      className="space-y-3 rounded-lg border border-border bg-muted/30 p-4"
      aria-labelledby={MENTOR_TIP_GUIDE_HEADING_ID}
    >
      <div className="flex items-center gap-3">
        <span
          className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-background text-base shadow-sm"
          aria-hidden="true"
        >
          {MENTOR_TIP_GUIDE_ICON}
        </span>
        <h3 id={MENTOR_TIP_GUIDE_HEADING_ID} className="text-sm font-semibold text-foreground">{MENTOR_TIP_GUIDE_HEADING}</h3>
      </div>
      <div className="space-y-3 pl-0 sm:pl-11">
        {renderableTips.map((tip, index) => (
          <div key={tip.id ?? `${normalizeMentorTipText(tip.title)}:${index}`} className="space-y-1.5 rounded-md border border-border/60 bg-background/70 p-3">
            {normalizeMentorTipText(tip.title) ? (
              <p className="text-sm font-semibold text-foreground">{normalizeMentorTipText(tip.title)}</p>
            ) : null}
            {normalizeMentorTipText(tip.body) ? (
              <p className="text-sm text-foreground/75">{normalizeMentorTipText(tip.body)}</p>
            ) : null}
          </div>
        ))}
      </div>
    </section>
  );
}

function CompanionDisplayCard({
  companion,
  labels,
}: Readonly<{ companion: CompanionContent | null; labels: ReturnType<typeof getCollectionLabels> }>) {
  // Collapsed by default, regardless of viewport — unlike the note-list sections above, which
  // default to expanded on large screens. The authored Companion is reference material now (see
  // docs/product/ROADMAP.md's "Coach vs. Companion" refinement): the live-signal cluster is what a
  // learner sees first, and the full guide is opt-in, not the page's default reading experience.
  const [isGuideExpanded, setIsGuideExpanded] = useState(false);

  if (!companion || !hasRenderableCompanionContent(companion)) {
    return null;
  }

  const overview = renderableCompanionText(companion.overview);
  const studyStrategy = renderableCompanionText(companion.studyStrategy);
  const commonMistakes = renderableCompanionText(companion.commonMistakes);
  const resources = renderableCompanionText(companion.resources);
  const mentorTips = companion.mentorTips ?? [];
  const faqItems = (companion.faq ?? [])
    .map((item) => ({
      question: renderableCompanionText(item.question),
      answer: renderableCompanionText(item.answer),
    }))
    .filter((item) => item.question || item.answer);
  const collapsedTeaser = stripMarkdownForPreview(overview || studyStrategy || commonMistakes);

  return (
    <Card className="space-y-4 p-4 sm:p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">{labels.companionSingular}</p>
          <h2 className="text-lg font-semibold tracking-tight">Learning {labels.companionSingular}</h2>
        </div>
        <button
          type="button"
          className="inline-flex shrink-0 items-center gap-1 text-sm font-semibold text-blue-700 hover:underline dark:text-blue-300"
          aria-expanded={isGuideExpanded}
          onClick={() => setIsGuideExpanded((previous) => !previous)}
        >
          {isGuideExpanded ? "Hide Full Guide" : "View Full Guide"}
          <ChevronDown
            className={cn("h-4 w-4 shrink-0 transition-transform", !isGuideExpanded && "-rotate-90")}
            aria-hidden="true"
          />
        </button>
      </div>

      {isGuideExpanded ? (
        <div className="space-y-4">
          {overview ? (
            <CompanionGuideSection headingKey="overview" headingId="companion-overview-heading">
              <SummaryMarkdown content={overview} />
            </CompanionGuideSection>
          ) : null}

          {studyStrategy ? (
            <CompanionGuideSection headingKey="studyStrategy" headingId="companion-study-strategy-heading">
              <SummaryMarkdown content={studyStrategy} />
            </CompanionGuideSection>
          ) : null}

          {commonMistakes ? (
            <CompanionGuideSection headingKey="commonMistakes" headingId="companion-common-mistakes-heading">
              <SummaryMarkdown content={commonMistakes} />
            </CompanionGuideSection>
          ) : null}

          {faqItems.length > 0 ? (
            <CompanionGuideSection headingKey="faq" headingId="companion-faq-display-heading">
              <div className="space-y-3">
                {faqItems.map((item, index) => (
                  <div key={`${item.question}:${index}`} className="space-y-1.5">
                    {item.question ? <p className="text-sm font-semibold text-foreground">{item.question}</p> : null}
                    {item.answer ? <SummaryMarkdown content={item.answer} /> : null}
                  </div>
                ))}
              </div>
            </CompanionGuideSection>
          ) : null}

          {resources ? (
            <CompanionGuideSection headingKey="resources" headingId="companion-resources-heading">
              <SummaryMarkdown content={resources} />
            </CompanionGuideSection>
          ) : null}

          <MentorTipsGuideSection mentorTips={mentorTips} />
        </div>
      ) : collapsedTeaser ? (
        <p className="line-clamp-1 text-sm text-foreground/60">{collapsedTeaser}</p>
      ) : null}
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
                    <ExpandableDescription
                      description={child.description}
                      collapsedClassName="line-clamp-2"
                      className="text-sm"
                    />
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

type CompanionFaqDraft = {
  question: string;
  answer: string;
};
type CompanionMentorTipDraft = {
  id: string;
  title: string;
  body: string;
  linkedAction: CompanionMentorTipAction;
  surfacingCondition: CompanionMentorTipSurfacingCondition | null;
};
type CompanionGenerationTarget = CompanionSection | "ALL";

const COMPANION_SECTIONS: CompanionSection[] = ["OVERVIEW", "STUDY_STRATEGY", "COMMON_MISTAKES", "FAQ", "MENTOR_TIPS"];
const COMPANION_SECTION_LABELS: Record<CompanionSection, string> = {
  OVERVIEW: "Overview",
  STUDY_STRATEGY: "Study Strategy",
  COMMON_MISTAKES: "Common Mistakes",
  FAQ: "FAQ",
  MENTOR_TIPS: "Mentor Tips",
};

function companionInputValue(value: string | null | undefined): string {
  return value ?? "";
}

function companionPayloadValue(value: string): string | null {
  const trimmedValue = value.trim();
  return trimmedValue ? trimmedValue : null;
}

function toCompanionFaqDrafts(content: CompanionContent | null): CompanionFaqDraft[] {
  return (content?.faq ?? []).map((item) => ({
    question: companionInputValue(item.question),
    answer: companionInputValue(item.answer),
  }));
}

function createCompanionDraftId(): string {
  return globalThis.crypto.randomUUID();
}

function normalizeMentorTipAction(value: CompanionMentorTipAction | null | undefined): CompanionMentorTipAction {
  return value ?? "NONE";
}

function toCompanionMentorTipDrafts(content: CompanionContent | null): CompanionMentorTipDraft[] {
  return (content?.mentorTips ?? []).map((tip) => ({
    id: tip.id ?? createCompanionDraftId(),
    title: companionInputValue(tip.title),
    body: companionInputValue(tip.body),
    linkedAction: normalizeMentorTipAction(tip.linkedAction),
    surfacingCondition: tip.surfacingCondition ?? null,
  }));
}

function companionFaqHasContent(faq: CompanionFaqDraft[]): boolean {
  return faq.some((item) => item.question.trim().length > 0 || item.answer.trim().length > 0);
}

function companionMentorTipsHaveContent(mentorTips: CompanionMentorTipDraft[]): boolean {
  return mentorTips.some((tip) => tip.title.trim().length > 0 || tip.body.trim().length > 0);
}

function buildCompanionContent(
  overview: string,
  studyStrategy: string,
  commonMistakes: string,
  resources: string,
  faq: CompanionFaqDraft[],
  mentorTips: CompanionMentorTipDraft[],
): CompanionContent {
  return {
    overview: companionPayloadValue(overview),
    studyStrategy: companionPayloadValue(studyStrategy),
    commonMistakes: companionPayloadValue(commonMistakes),
    resources: companionPayloadValue(resources),
    faq: faq.map((item) => ({
      question: companionPayloadValue(item.question),
      answer: companionPayloadValue(item.answer),
    })),
    mentorTips: mentorTips.map((tip) => ({
      id: tip.id,
      title: companionPayloadValue(tip.title),
      body: companionPayloadValue(tip.body),
      linkedAction: tip.linkedAction,
      surfacingCondition: tip.surfacingCondition,
    })),
  };
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
  // Tracks the value actually loaded from getMe(), distinct from the field's current (possibly
  // edited, possibly still-unloaded) contents. undefined means "not resolved yet" — either the
  // async prefill hasn't returned or it failed. Save must never send studyDaysPerWeek while this
  // is undefined, or a save-before-prefill race (or a getMe() failure) would silently wipe the
  // user's real intensity to null. See docs/features/collections.md.
  const [studyDaysPerWeekBaseline, setStudyDaysPerWeekBaseline] = useState<string | undefined>(undefined);
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
        setStudyDaysPerWeekBaseline(undefined);
        void getMe()
          .then((me) => {
            const loadedValue = me.studyDaysPerWeek === null ? "" : String(me.studyDaysPerWeek);
            setStudyDaysPerWeek(loadedValue);
            setStudyDaysPerWeekBaseline(loadedValue);
          })
          .catch(() => setStudyDaysPerWeek(""));
      } else {
        setStudyDaysPerWeek("");
        setStudyDaysPerWeekBaseline(undefined);
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
      // Only send an intensity update when the loaded baseline actually resolved AND the value
      // changed from it — never send while the baseline is still undefined (prefill in flight or
      // failed), and never send a no-op write when the user didn't touch the field.
      if (isTopLevelGoal && studyDaysPerWeekBaseline !== undefined && trimmedStudyDaysPerWeek !== studyDaysPerWeekBaseline) {
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

function CompanionEditorModal({
  collection,
  labels,
  companionMayBeOutdated,
  terminalActionLabel,
  isOpen,
  onClose,
  onSaved,
}: Readonly<{
  collection: NoteCollectionDetail;
  labels: ReturnType<typeof getCollectionLabels>;
  companionMayBeOutdated: boolean;
  terminalActionLabel: string | null;
  isOpen: boolean;
  onClose: () => void;
  onSaved: (collection: NoteCollectionDetail) => void;
}>) {
  const [overview, setOverview] = useState(companionInputValue(collection.companion?.overview));
  const [studyStrategy, setStudyStrategy] = useState(companionInputValue(collection.companion?.studyStrategy));
  const [commonMistakes, setCommonMistakes] = useState(companionInputValue(collection.companion?.commonMistakes));
  const [resources, setResources] = useState(companionInputValue(collection.companion?.resources));
  const [faq, setFaq] = useState<CompanionFaqDraft[]>(toCompanionFaqDrafts(collection.companion));
  const [mentorTips, setMentorTips] = useState<CompanionMentorTipDraft[]>(toCompanionMentorTipDrafts(collection.companion));
  const [submitting, setSubmitting] = useState(false);
  const [clearing, setClearing] = useState(false);
  const [generating, setGenerating] = useState<CompanionGenerationTarget | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [generationNotice, setGenerationNotice] = useState<string | null>(null);
  const hasCompanion = collection.companion !== null;
  const busy = submitting || clearing || generating !== null;

  useEffect(() => {
    if (isOpen) {
      setOverview(companionInputValue(collection.companion?.overview));
      setStudyStrategy(companionInputValue(collection.companion?.studyStrategy));
      setCommonMistakes(companionInputValue(collection.companion?.commonMistakes));
      setResources(companionInputValue(collection.companion?.resources));
      setFaq(toCompanionFaqDrafts(collection.companion));
      setMentorTips(toCompanionMentorTipDrafts(collection.companion));
      setSubmitting(false);
      setClearing(false);
      setGenerating(null);
      setError(null);
      setGenerationNotice(null);
    }
  }, [collection.companion, isOpen]);

  const sectionHasContent = (section: CompanionSection): boolean => {
    if (section === "OVERVIEW") {
      return overview.trim().length > 0;
    }
    if (section === "STUDY_STRATEGY") {
      return studyStrategy.trim().length > 0;
    }
    if (section === "COMMON_MISTAKES") {
      return commonMistakes.trim().length > 0;
    }
    if (section === "MENTOR_TIPS") {
      return companionMentorTipsHaveContent(mentorTips);
    }
    return companionFaqHasContent(faq);
  };

  const confirmOverwriteIfNeeded = (sections: CompanionSection[]): boolean => {
    if (!sections.some(sectionHasContent)) {
      return true;
    }
    const sectionText = sections.length === 1 ? COMPANION_SECTION_LABELS[sections[0]!] : "these sections";
    const mentorTipNote = sections.includes("MENTOR_TIPS") ? " Mentor Tip drafts will be appended." : "";
    return globalThis.confirm(`Generate draft content for ${sectionText}? This will replace current unsaved section text.${mentorTipNote}`);
  };

  const applyCompanionDraft = (draft: CompanionContent, sections: CompanionSection[]) => {
    if (sections.includes("OVERVIEW")) {
      setOverview(companionInputValue(draft.overview));
    }
    if (sections.includes("STUDY_STRATEGY")) {
      setStudyStrategy(companionInputValue(draft.studyStrategy));
    }
    if (sections.includes("COMMON_MISTAKES")) {
      setCommonMistakes(companionInputValue(draft.commonMistakes));
    }
    if (sections.includes("FAQ")) {
      setFaq(toCompanionFaqDrafts(draft));
    }
    if (sections.includes("MENTOR_TIPS")) {
      setMentorTips((current) => [...current, ...toCompanionMentorTipDrafts(draft)]);
    }
  };

  const handleGenerate = async (sections: CompanionSection[], target: CompanionGenerationTarget) => {
    if (busy || !confirmOverwriteIfNeeded(sections)) {
      return;
    }
    setGenerating(target);
    setError(null);
    setGenerationNotice(null);
    try {
      const draft = await generateCompanion(collection.id, sections);
      applyCompanionDraft(draft, sections);
      setGenerationNotice("Draft generated. Review and edit it before saving.");
      void trackAnalyticsEvent({
        eventType: "COMPANION_GENERATED",
        entityId: collection.id,
        metadata: { sections },
      }).catch(() => undefined);
    } catch (generateError) {
      setError(generateError instanceof Error ? generateError.message : `Could not generate this ${labels.companionSingular}.`);
    } finally {
      setGenerating(null);
    }
  };

  const updateFaqItem = (index: number, field: keyof CompanionFaqDraft, value: string) => {
    setFaq((current) => current.map((item, itemIndex) => (
      itemIndex === index ? { ...item, [field]: value } : item
    )));
  };

  const addFaqItem = () => {
    setFaq((current) => [...current, { question: "", answer: "" }]);
  };

  const removeFaqItem = (index: number) => {
    setFaq((current) => current.filter((_, itemIndex) => itemIndex !== index));
  };

  const updateMentorTip = <K extends keyof CompanionMentorTipDraft>(
    index: number,
    field: K,
    value: CompanionMentorTipDraft[K],
  ) => {
    setMentorTips((current) => current.map((tip, tipIndex) => (
      tipIndex === index ? { ...tip, [field]: value } : tip
    )));
  };

  const addMentorTip = () => {
    setMentorTips((current) => [
      ...current,
      {
        id: createCompanionDraftId(),
        title: "",
        body: "",
        linkedAction: "NONE",
        surfacingCondition: null,
      },
    ]);
  };

  const removeMentorTip = (index: number) => {
    setMentorTips((current) => current.filter((_, tipIndex) => tipIndex !== index));
  };

  const updateMentorTipSurfacingType = (index: number, value: "ALWAYS" | CompanionMentorTipSurfacingConditionType) => {
    updateMentorTip(
      index,
      "surfacingCondition",
      value === "ALWAYS" ? null : { type: value, threshold: 0 },
    );
  };

  const updateMentorTipThreshold = (index: number, value: string) => {
    const currentCondition = mentorTips[index]?.surfacingCondition;
    if (!currentCondition) {
      return;
    }
    updateMentorTip(index, "surfacingCondition", {
      ...currentCondition,
      threshold: Number(value),
    });
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setGenerationNotice(null);
    try {
      const saved = await setCompanion(collection.id, buildCompanionContent(overview, studyStrategy, commonMistakes, resources, faq, mentorTips));
      onSaved(saved);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : `Could not save this ${labels.companionSingular}.`);
    } finally {
      setSubmitting(false);
    }
  };

  const handleClear = async () => {
    setClearing(true);
    setError(null);
    setGenerationNotice(null);
    try {
      const saved = await clearCompanion(collection.id);
      onSaved(saved);
    } catch (clearError) {
      setError(clearError instanceof Error ? clearError.message : `Could not remove this ${labels.companionSingular}.`);
    } finally {
      setClearing(false);
    }
  };

  return (
    <AppModal
      isOpen={isOpen}
      title={`Manage ${labels.companionSingular}`}
      description={`Author curated guidance for this ${labels.singular.toLowerCase()}.`}
      onClose={onClose}
      panelClassName="sm:max-w-2xl"
      actions={(
        <div className="flex w-full flex-col-reverse gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            {hasCompanion ? (
              <Button
                type="button"
                variant="destructiveOutline"
                loading={clearing}
                loadingText="Removing..."
                disabled={submitting || generating !== null}
                onClick={() => void handleClear()}
              >
                Remove {labels.companionSingular}
              </Button>
            ) : null}
          </div>
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              loading={generating === "ALL"}
              loadingText="Generating..."
              disabled={submitting || clearing || generating !== null}
              onClick={() => void handleGenerate(COMPANION_SECTIONS, "ALL")}
            >
              Generate all sections
            </Button>
            <Button type="button" variant="secondary" disabled={busy} onClick={onClose}>Cancel</Button>
            <Button type="submit" form="companion-editor-form" loading={submitting} loadingText="Saving..." disabled={clearing || generating !== null}>
              Save
            </Button>
          </div>
        </div>
      )}
    >
      <form id="companion-editor-form" className="space-y-5" onSubmit={handleSubmit} noValidate>
        {companionMayBeOutdated ? (
          <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:border-amber-900 dark:bg-amber-950/30 dark:text-amber-200">
            This {labels.companionSingular} may be outdated because this {labels.singular.toLowerCase()} structure changed since it was last saved.
          </p>
        ) : null}
        <div className="block space-y-1.5">
          <span className="flex items-center justify-between gap-3">
            <span className="text-sm font-medium text-foreground">Overview</span>
            <Button
              type="button"
              variant="outline"
              size="sm"
              loading={generating === "OVERVIEW"}
              loadingText="Generating..."
              disabled={busy}
              onClick={() => void handleGenerate(["OVERVIEW"], "OVERVIEW")}
            >
              Generate Overview
            </Button>
          </span>
          <textarea
            aria-label="Overview"
            data-autofocus="true"
            value={overview}
            onChange={(event) => setOverview(event.target.value)}
            className="min-h-28 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
          />
        </div>
        <div className="block space-y-1.5">
          <span className="flex items-center justify-between gap-3">
            <span className="text-sm font-medium text-foreground">Study Strategy</span>
            <Button
              type="button"
              variant="outline"
              size="sm"
              loading={generating === "STUDY_STRATEGY"}
              loadingText="Generating..."
              disabled={busy}
              onClick={() => void handleGenerate(["STUDY_STRATEGY"], "STUDY_STRATEGY")}
            >
              Generate Study Strategy
            </Button>
          </span>
          <textarea
            aria-label="Study Strategy"
            value={studyStrategy}
            onChange={(event) => setStudyStrategy(event.target.value)}
            className="min-h-28 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
          />
        </div>
        <div className="block space-y-1.5">
          <span className="flex items-center justify-between gap-3">
            <span className="text-sm font-medium text-foreground">Common Mistakes</span>
            <Button
              type="button"
              variant="outline"
              size="sm"
              loading={generating === "COMMON_MISTAKES"}
              loadingText="Generating..."
              disabled={busy}
              onClick={() => void handleGenerate(["COMMON_MISTAKES"], "COMMON_MISTAKES")}
            >
              Generate Common Mistakes
            </Button>
          </span>
          <textarea
            aria-label="Common Mistakes"
            value={commonMistakes}
            onChange={(event) => setCommonMistakes(event.target.value)}
            className="min-h-28 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
          />
        </div>

        <section className="space-y-3" aria-labelledby="companion-faq-heading">
          <div className="flex items-center justify-between gap-3">
            <h3 id="companion-faq-heading" className="text-sm font-medium text-foreground">FAQ</h3>
            <div className="flex flex-wrap justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                loading={generating === "FAQ"}
                loadingText="Generating..."
                disabled={busy}
                onClick={() => void handleGenerate(["FAQ"], "FAQ")}
              >
                Generate FAQ
              </Button>
              <Button type="button" variant="outline" size="sm" disabled={busy} onClick={addFaqItem}>
                Add question
              </Button>
            </div>
          </div>
          {faq.length > 0 ? (
            <div className="space-y-3">
              {faq.map((item, index) => (
                <div key={index} className="space-y-3 rounded-lg border border-border bg-muted/30 p-3">
                  <label className="block space-y-1.5">
                    <span className="text-sm font-medium text-foreground">Question {index + 1}</span>
                    <input
                      value={item.question}
                      onChange={(event) => updateFaqItem(index, "question", event.target.value)}
                      className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                    />
                  </label>
                  <label className="block space-y-1.5">
                    <span className="text-sm font-medium text-foreground">Answer {index + 1}</span>
                    <input
                      value={item.answer}
                      onChange={(event) => updateFaqItem(index, "answer", event.target.value)}
                      className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                    />
                  </label>
                  <Button type="button" variant="outline" size="sm" disabled={busy} onClick={() => removeFaqItem(index)}>
                    Remove
                  </Button>
                </div>
              ))}
            </div>
          ) : null}
        </section>

        <label className="block space-y-1.5">
          <span className="text-sm font-medium text-foreground">Resources</span>
          <textarea
            aria-label="Resources"
            value={resources}
            onChange={(event) => setResources(event.target.value)}
            className="min-h-28 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
          />
        </label>

        <section className="space-y-3" aria-labelledby="companion-mentor-tips-editor-heading">
          <div className="flex items-center justify-between gap-3">
            <h3 id="companion-mentor-tips-editor-heading" className="text-sm font-medium text-foreground">Mentor Tips</h3>
            <div className="flex flex-wrap justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                loading={generating === "MENTOR_TIPS"}
                loadingText="Generating..."
                disabled={busy}
                onClick={() => void handleGenerate(["MENTOR_TIPS"], "MENTOR_TIPS")}
              >
                Generate tips
              </Button>
              <Button type="button" variant="outline" size="sm" disabled={busy} onClick={addMentorTip}>
                Add tip
              </Button>
            </div>
          </div>
          {mentorTips.length > 0 ? (
            <div className="space-y-3">
              {mentorTips.map((tip, index) => (
                <div key={tip.id} className="space-y-3 rounded-lg border border-border bg-muted/30 p-3">
                  <label className="block space-y-1.5">
                    <span className="text-sm font-medium text-foreground">Tip title {index + 1}</span>
                    <input
                      aria-label={`Tip title ${index + 1}`}
                      value={tip.title}
                      onChange={(event) => updateMentorTip(index, "title", event.target.value)}
                      className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                    />
                  </label>
                  <label className="block space-y-1.5">
                    <span className="text-sm font-medium text-foreground">Tip body {index + 1}</span>
                    <textarea
                      aria-label={`Tip body ${index + 1}`}
                      value={tip.body}
                      onChange={(event) => updateMentorTip(index, "body", event.target.value)}
                      className="min-h-20 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                    />
                  </label>
                  <div className="grid gap-3 sm:grid-cols-2">
                    <label className="block space-y-1.5">
                      <span className="text-sm font-medium text-foreground">Linked action</span>
                      <select
                        aria-label={`Linked action ${index + 1}`}
                        value={tip.linkedAction}
                        onChange={(event) => updateMentorTip(index, "linkedAction", event.target.value as CompanionMentorTipAction)}
                        className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                      >
                        <option value="NONE">{MENTOR_TIP_ACTION_LABELS.NONE}</option>
                        <option value="CONTINUE_STUDYING">{MENTOR_TIP_ACTION_LABELS.CONTINUE_STUDYING}</option>
                        <option value="REVIEW_DUE_CONCEPTS">{MENTOR_TIP_ACTION_LABELS.REVIEW_DUE_CONCEPTS}</option>
                        <option value="TERMINAL_ACTION">{terminalActionLabel ?? MENTOR_TIP_ACTION_LABELS.TERMINAL_ACTION}</option>
                      </select>
                    </label>
                    <label className="block space-y-1.5">
                      <span className="text-sm font-medium text-foreground">Surfacing condition</span>
                      <select
                        aria-label={`Surfacing condition ${index + 1}`}
                        value={tip.surfacingCondition?.type ?? "ALWAYS"}
                        onChange={(event) => updateMentorTipSurfacingType(index, event.target.value as "ALWAYS" | CompanionMentorTipSurfacingConditionType)}
                        className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                      >
                        <option value="ALWAYS">Always show</option>
                        <option value="DAYS_BEFORE_TARGET_DATE">{MENTOR_TIP_SURFACING_LABELS.DAYS_BEFORE_TARGET_DATE}</option>
                        <option value="AFTER_SUBJECTS_COMPLETED">{MENTOR_TIP_SURFACING_LABELS.AFTER_SUBJECTS_COMPLETED}</option>
                      </select>
                    </label>
                  </div>
                  {tip.surfacingCondition ? (
                    <label className="block space-y-1.5">
                      <span className="text-sm font-medium text-foreground">Threshold</span>
                      <input
                        aria-label={`Surfacing threshold ${index + 1}`}
                        type="number"
                        min="0"
                        step="1"
                        value={String(tip.surfacingCondition.threshold)}
                        onChange={(event) => updateMentorTipThreshold(index, event.target.value)}
                        className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                      />
                    </label>
                  ) : null}
                  <Button type="button" variant="outline" size="sm" disabled={busy} onClick={() => removeMentorTip(index)}>
                    Remove tip
                  </Button>
                </div>
              ))}
            </div>
          ) : null}
        </section>

        {generationNotice ? <p className="rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-200">{generationNotice}</p> : null}
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
  const searchParams = useSearchParams();
  const initialAskCompanionDraft = searchParams.get(ASK_COMPANION_DRAFT_QUERY_PARAM)?.trim() || undefined;
  const [authUser, setAuthUser] = useState<AuthUser | null>(null);
  useEffect(() => {
    setAuthUser(getAuthUser());
  }, []);
  const { usageSummary } = useBillingUsageSummary();
  const currentPlan = (usageSummary?.plan ?? authUser?.planType ?? "FREE") as AppPlanType;
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
  // A top-level collection can either have child Subject plans (a "Goal") or hold notes directly (a
  // "leaf" plan) — the two are mutually exclusive. A childless top-level collection can still carry a
  // target date, so `goalDetail` is fetched for it too (see loadCollection below), but it renders as
  // the leaf view, not the Goal view — this flag is the single source of truth for that branch choice.
  const isGoalView = Boolean(goalDetail) && (collection?.childCount ?? 0) > 0;
  const expectsGoalView = collection !== null
    && collection.parentCollectionId === null
    && (collection.childCount ?? 0) > 0;
  const collectionLoaded = collection !== null;
  const [loadError, setLoadError] = useState<string | null>(null);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [mutationKind, setMutationKind] = useState<MutationKind>(null);
  const [actionToast, setActionToast] = useState<string | null>(null);
  const actionToastTimerRef = useRef<ReturnType<typeof globalThis.setTimeout> | null>(null);

  const showActionToast = useCallback((message: string) => {
    if (actionToastTimerRef.current) {
      globalThis.clearTimeout(actionToastTimerRef.current);
    }
    setActionToast(message);
    actionToastTimerRef.current = globalThis.setTimeout(() => {
      setActionToast(null);
    }, 4000);
  }, []);

  useEffect(() => {
    const ref = actionToastTimerRef;
    return () => {
      if (ref.current) {
        globalThis.clearTimeout(ref.current);
      }
    };
  }, []);

  const [editOpen, setEditOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [publishOpen, setPublishOpen] = useState(false);
  const [companionOpen, setCompanionOpen] = useState(false);
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
  const [primaryCollectionId, setPrimaryCollectionId] = useState<string | null>(null);
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
        setNoteVisibility(new Map(notesResult.value.map((note) => [note.id, note.visibility])));
      } else {
        setNoteListItems([]);
        setNoteVisibility(new Map());
        setNoteListLoadFailed(true);
      }
      const collectionResult = result.value;
      setCollection(collectionResult);
      setItems(sortCollectionItemsByPosition(collectionResult.items));
      // Fetch the Goal endpoint for any top-level collection, not just ones with children today — a
      // childless top-level ("leaf") collection can still carry a target completion date, and the
      // countdown fields it needs are surfaced in the leaf view below (see isGoalView).
      const goalResult = collectionResult.parentCollectionId === null
        ? await getCollectionGoal(collectionId)
        : null;
      setGoalDetail(goalResult);
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
    let mounted = true;
    setPrimaryCollectionId(null);
    void getMe()
      .then((me) => {
        if (mounted) {
          setPrimaryCollectionId(me.primaryCollectionId ?? null);
        }
      })
      .catch(() => {
        if (mounted) {
          setPrimaryCollectionId(null);
        }
      });
    return () => {
      mounted = false;
    };
  }, [collectionId]);

  useEffect(() => {
    if (!collectionLoaded || expectsGoalView || items.length === 0) {
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
  }, [collectionId, collectionLoaded, expectsGoalView, items]);

  useEffect(() => {
    if (!collectionLoaded || expectsGoalView) {
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
  }, [collectionId, collectionLoaded, expectsGoalView]);

  const loadNoteVisibility = useCallback(async () => {
    try {
      const notes = await listNotes();
      setNoteVisibility(new Map(notes.map((note) => [note.id, note.visibility])));
    } catch {
      // Visibility badges are admin-only progressive enhancement; ignore failures.
    }
  }, []);

  const refetchAfterFailure = async (message: string) => {
    setMutationError(message);
    try {
      const result = await getCollection(collectionId);
      setCollection(result);
      setGoalDetail(result.parentCollectionId === null ? await getCollectionGoal(collectionId) : null);
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
      setGoalDetail(result.parentCollectionId === null ? await getCollectionGoal(collectionId) : null);
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
      setCollectionActionNotice(`${labels.singular} deleted.`);
      router.push("/collections");
    } catch (error) {
      setMutationError(error instanceof Error ? error.message : "Could not delete this collection.");
      setMutationKind(null);
    }
  };

  const handlePrimaryToggle = async () => {
    if (!collection) {
      return;
    }
    setMutationError(null);
    const isCurrentlyPrimary = collection.id === primaryCollectionId;
    try {
      if (isCurrentlyPrimary) {
        await clearPrimaryCollection(collection.id);
        setPrimaryCollectionId(null);
        showActionToast("Removed as primary.");
        return;
      }
      await setPrimaryCollection(collection.id);
      setPrimaryCollectionId(collection.id);
      showActionToast("Set as primary.");
    } catch {
      setMutationError(
        isCurrentlyPrimary
          ? "Could not remove this as your primary review set."
          : "Could not set this as your primary review set.",
      );
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
  const isPrimaryCollection = collection?.id === primaryCollectionId;
  const primaryActionLabel = isPrimaryCollection ? "Remove as primary" : "Set as primary";
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
  const primaryStudyAction = useMemo<ResolvedPrimaryAction | null>(() => {
    if (continueAction) {
      return {
        title: getNoteTitle(continueAction.item),
        description: `Continue from this note. Next step: ${continueAction.actionLabel}.`,
        href: continueAction.href,
      };
    }

    const nextAction = getNextPlanAction(items, showWeakAreas);
    if (nextAction) {
      return {
        title: getNoteTitle(nextAction.item),
        description: nextAction.description,
        href: `/notes/${nextAction.item.noteId}?ref=${encodeURIComponent(`/collections/${collectionId}`)}`,
      };
    }

    const firstChild = isGoalView ? goalDetail?.children[0] : null;
    if (firstChild) {
      return {
        title: firstChild.title,
        description: `Open the next ${labels.subjectSingular.toLowerCase()} in this ${labels.goalSingular.toLowerCase()}.`,
        href: `/collections/${firstChild.collectionId}`,
      };
    }

    return null;
  }, [
    collectionId,
    continueAction,
    goalDetail?.children,
    isGoalView,
    items,
    labels.goalSingular,
    labels.subjectSingular,
    showWeakAreas,
  ]);
  const postAdoptGuidanceRules: GuidanceRule[] = [
    {
      id: "post-adopt-target-date",
      priority: 1,
      // isGoalView (not raw goalDetail) preserves the pre-existing rule that leaf-plan adoption never
      // shows this tip — goalDetail is now also populated for childless top-level collections, but the
      // post-adopt nudge is Goal-adoption-specific (adoptGoal always produces a Goal with children).
      condition: () => justAdopted && isGoalView && !goalDetail?.targetCompletionDate,
      message: "Set a target completion date to see your weekly countdown and daily study budget.",
    },
  ];
  const activePostAdoptTip = pickActiveGuidance(postAdoptGuidanceRules);

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
    params.set(CHALLENGE_QUIZ_ENTRY_QUERY_PARAM, CHALLENGE_QUIZ_MODE_SELECTION_ENTRY);
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
  const terminalSecondaryAction = terminalAction ? (
    <div className="flex w-full flex-col gap-1 sm:w-auto">
      <ResponsiveActionButton
        action="open"
        label={terminalAction.label}
        variant="outline"
        size="sm"
        className="w-full sm:w-auto"
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
  ) : null;
  const currentTargetCompletionDate = goalDetail?.targetCompletionDate ?? null;
  const hasTargetDate = currentTargetCompletionDate !== null;
  const countdownLine = hasTargetDate
    ? buildCountdownLine(currentTargetCompletionDate, goalDetail?.weeksRemaining, goalDetail?.conceptsRemaining)
    : null;
  const todaysConceptBudget = hasTargetDate ? goalDetail?.todaysConceptBudget ?? null : null;
  const completedSubjectCount = isGoalView && goalDetail
    ? goalDetail.children.filter((child) => child.totalConcepts > 0 && child.masteredConcepts === child.totalConcepts).length
    : planReadiness
      ? planReadiness.subjects.filter((subject) => subject.totalConcepts > 0 && subject.masteredConcepts === subject.totalConcepts).length
      : null;
  const surfacedMentorTip = getFirstEligibleMentorTip(
    collection?.companion ?? null,
    currentTargetCompletionDate,
    completedSubjectCount,
    terminalAction?.label ?? null,
  );

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

  if (isGoalView && goalDetail) {
    return (
      <main className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
        <BackLink href={backLinkHref} label={backLinkLabel} />
        <PlanHeroCard
          collection={collection}
          eyebrowLabel={labels.goalSingular}
          isPrimary={isPrimaryCollection}
          metadataLine={(
            <>
              {collection.courseProgram ? <span>{collection.courseProgram}</span> : null}
              {collection.courseProgram ? <span aria-hidden="true"> · </span> : null}
              <span>{goalDetail.childCount} {labels.subjectSingular}{goalDetail.childCount === 1 ? "" : "s"}</span>
              <span aria-hidden="true"> · </span>
              <span>{collection.estimatedStudyHours && collection.estimatedStudyHours > 0 ? `~${collection.estimatedStudyHours} hrs` : "No study estimate"}</span>
            </>
          )}
          actions={(
            <CollectionActionsMenu
              collection={collection}
              labels={labels}
              isAdmin={isAdmin}
              canManageCompanion={collection.parentCollectionId === null}
              primaryActionLabel={primaryActionLabel}
              onEditClick={() => setEditOpen(true)}
              onPrimaryClick={() => void handlePrimaryToggle()}
              onCompanionClick={() => setCompanionOpen(true)}
              onDeleteClick={() => setDeleteOpen(true)}
              onPublishClick={() => setPublishOpen(true)}
            />
          )}
        />

        <section aria-label="Plan focus and readiness" data-testid="goal-focus-readiness-stack" className="space-y-3">
          <TodaysFocusCard
            action={primaryStudyAction}
            terminalAction={terminalSecondaryAction}
            dueConceptReviewHref={dueConceptReviewHref}
            todaysConceptBudget={todaysConceptBudget}
            hasTargetDate={hasTargetDate}
            mentorTip={surfacedMentorTip}
          />

          <ReadinessSummary
            variant="compact"
            title={`${goalDetail.title} readiness`}
            eyebrow={`${labels.goalSingular} readiness`}
            overallReadinessPercentage={goalDetail.overallReadinessPercentage}
            totalConcepts={goalDetail.totalConcepts}
            masteredConcepts={goalDetail.masteredConcepts}
            dueConcepts={goalDetail.dueConcepts}
            notPracticedConcepts={goalDetail.notPracticedConcepts}
            subjects={[]}
            emptyTitle="No readiness yet"
            emptyDescription={`Add ${labels.subjectSingular.toLowerCase()}s with ready Study Packs to see this ${labels.goalSingular.toLowerCase()} readiness.`}
            countdown={countdownLine}
            footer={<ReadinessCardFooter collectionId={collectionId} />}
          />

          {activePostAdoptTip ? (
            <GuidanceTip
              tipId={activePostAdoptTip.id}
              message={activePostAdoptTip.message}
              trackAnalytics
              action={{ label: "Set target date", onClick: () => setEditOpen(true) }}
            />
          ) : null}
        </section>
        {hasRenderableCompanionContent(collection.companion) ? (
          <section aria-label="Companion guidance" data-testid="goal-companion-guidance-stack" className="space-y-3">
            <CompanionDisplayCard companion={collection.companion} labels={labels} />
            <AskCompanionPanel
              collectionId={collectionId}
              currentPlan={currentPlan}
              initialDraft={initialAskCompanionDraft}
            />
          </section>
        ) : null}

        {mutationError ? (
          <Card className="flex items-start justify-between gap-4 border-red-200 bg-red-50 p-4 text-red-800 dark:border-red-900 dark:bg-red-950/30 dark:text-red-200">
            <p className="text-sm">{mutationError}</p>
            <button type="button" aria-label="Dismiss error" onClick={() => setMutationError(null)}>
              <X className="h-4 w-4" aria-hidden="true" />
            </button>
          </Card>
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
                  companion: saved.companion,
                  updatedAt: saved.updatedAt,
                } : previous);
              });
            setItems(sortCollectionItemsByPosition(saved.items));
            setEditOpen(false);
            showActionToast("Saved.");
          }}
        />
        <CompanionEditorModal
          collection={collection}
          labels={labels}
          companionMayBeOutdated={goalDetail.companionMayBeOutdated}
          terminalActionLabel={terminalAction?.label ?? null}
          isOpen={companionOpen}
          onClose={() => setCompanionOpen(false)}
          onSaved={(saved) => {
            setCollection(saved);
            setGoalDetail((previous) => previous ? {
              ...previous,
              title: saved.title,
              description: saved.description,
              visibility: saved.visibility,
              courseProgram: saved.courseProgram,
              targetCompletionDate: saved.targetCompletionDate,
              companion: saved.companion,
              companionMayBeOutdated: false,
              updatedAt: saved.updatedAt,
            } : previous);
            setItems(sortCollectionItemsByPosition(saved.items));
            setCompanionOpen(false);
            showActionToast(saved.companion ? `${labels.companionSingular} saved.` : `${labels.companionSingular} removed.`);
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
        {actionToast ? <ToastMessage message={actionToast} tone="success" /> : null}
      </main>
    );
  }

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
      <BackLink href={backLinkHref} label={backLinkLabel} />
      <PlanHeroCard
        collection={collection}
        eyebrowLabel={labels.singular}
        isPrimary={isPrimaryCollection}
        metadataLine={(
          <>
            {collection.courseProgram ? <span>{collection.courseProgram}</span> : null}
            {collection.courseProgram ? <span aria-hidden="true"> · </span> : null}
            <span>{collection.progress.notesWithStudyPack}/{collection.progress.totalNotes} notes ready</span>
            <span aria-hidden="true"> · </span>
            <span>{collection.estimatedStudyHours && collection.estimatedStudyHours > 0 ? `~${collection.estimatedStudyHours} hrs` : "No study estimate"}</span>
          </>
        )}
        actions={(
          <CollectionActionsMenu
            collection={collection}
            labels={labels}
            isAdmin={isAdmin}
            canManageCompanion={collection.parentCollectionId === null}
            primaryActionLabel={primaryActionLabel}
            onEditClick={() => setEditOpen(true)}
            onPrimaryClick={() => void handlePrimaryToggle()}
            onCompanionClick={() => setCompanionOpen(true)}
            onDeleteClick={() => setDeleteOpen(true)}
            onPublishClick={() => setPublishOpen(true)}
          />
        )}
      />

      <TodaysFocusCard
        action={primaryStudyAction}
        terminalAction={terminalSecondaryAction}
        dueConceptReviewHref={dueConceptReviewHref}
        todaysConceptBudget={todaysConceptBudget}
        hasTargetDate={hasTargetDate}
        mentorTip={surfacedMentorTip}
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
        countdown={countdownLine}
        footer={<ReadinessCardFooter collectionId={collectionId} />}
      />

      <CompanionDisplayCard companion={collection.companion} labels={labels} />
      {hasRenderableCompanionContent(collection.companion) ? (
        <AskCompanionPanel
          collectionId={collectionId}
          currentPlan={currentPlan}
          initialDraft={initialAskCompanionDraft}
        />
      ) : null}

      <Card className="space-y-4 p-4 sm:p-6">
        <div>
          <CardTitle>Notes</CardTitle>
          <CardDescription>
            {items.length} {items.length === 1 ? "note" : "notes"} in saved order · {collection.progress.notesWithStudyPack}/{collection.progress.totalNotes} notes ready.
          </CardDescription>
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

      <CollectionProgressSummary collection={collection} />

      <EditCollectionModal
        collection={collection}
        isOpen={editOpen}
        onClose={() => setEditOpen(false)}
        onSaved={(saved) => {
          setCollection(saved);
          setItems(sortCollectionItemsByPosition(saved.items));
          setEditOpen(false);
          // Same reasoning as the Goal branch's onSaved: the weekly countdown fields only exist on
          // GoalCollectionDetailResponse, not on the NoteCollectionDetail this modal saves — refetch so
          // an edited target date on this (possibly childless) top-level collection shows immediately.
          if (saved.parentCollectionId === null) {
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
                  companion: saved.companion,
                  updatedAt: saved.updatedAt,
                } : previous);
              });
          }
          showActionToast("Saved.");
        }}
      />
      <CompanionEditorModal
        collection={collection}
        labels={labels}
        companionMayBeOutdated={goalDetail?.companionMayBeOutdated ?? false}
        terminalActionLabel={terminalAction?.label ?? null}
        isOpen={companionOpen}
        onClose={() => setCompanionOpen(false)}
        onSaved={(saved) => {
          setCollection(saved);
          setGoalDetail((previous) => previous ? {
            ...previous,
            title: saved.title,
            description: saved.description,
            visibility: saved.visibility,
            courseProgram: saved.courseProgram,
            targetCompletionDate: saved.targetCompletionDate,
            companion: saved.companion,
            companionMayBeOutdated: false,
            updatedAt: saved.updatedAt,
          } : previous);
          setItems(sortCollectionItemsByPosition(saved.items));
          setCompanionOpen(false);
          showActionToast(saved.companion ? `${labels.companionSingular} saved.` : `${labels.companionSingular} removed.`);
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
      {actionToast ? <ToastMessage message={actionToast} tone="success" /> : null}
    </main>
  );
}
