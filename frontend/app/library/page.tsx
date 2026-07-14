"use client";

import Link from "next/link";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {useRouter, useSearchParams} from "next/navigation";
import {
  consumeBulkQueuedFlash,
  setBulkGenerationRetryStash,
} from "@/lib/bulk-generation-flash";
import {
  LIBRARY_GENERATION_POLL_MAX_TICKS,
  LIBRARY_GENERATION_POLL_QUIET_TICKS,
  STUDY_PACK_GENERATION_POLL_INTERVAL_MS,
} from "@/lib/study-pack-generation";
import {
  ArrowUpDown,
  Bookmark,
  ChevronDown,
  Filter,
  Globe,
  Lock,
  Trash2,
  X,
} from "lucide-react";
import {AppModal} from "@/components/ui/app-modal";
import {Button} from "@/components/ui/button";
import {Card} from "@/components/ui/card";
import {CreateMenu} from "@/components/ui/create-menu";
import {SharedNoteCard} from "@/components/notes/shared-note-card";
import {PageHeader} from "@/components/page-header";
import {LibrarySheetModal} from "@/components/notes/library-sheet-modal";
import {NoteStateBadge} from "@/components/notes/note-state-badge";
import {ResponsiveActionButton, ResponsiveActionLink} from "@/components/ui/action-button";
import {getAuthUser} from "@/lib/auth";
import {
  ApiRequestError,
  createCollection,
  createSavedLibraryFilter,
  deleteSavedLibraryFilter,
  getBulkGenerationResult,
  getSavedLibraryFilters,
  listNotes,
  listSubjects,
  type BulkGenerationResultResponse,
  type NoteListItemResponse,
  type SavedLibraryFilterResponse,
  type SavedLibraryFilterState,
  type NoteVisibility,
} from "@/lib/api";
import {getBrowsingCardClassName, getSelectionCardClassName} from "@/lib/clickable-card";
import {normalizeCourseProgram} from "@/lib/learning-profile";
import {getCollectionLabels} from "@/lib/collection-labels";
import {shouldShowQuizReadyIndicator} from "@/lib/profile-mode";
import {requireAuthenticatedOnboardedUser} from "@/lib/route-guards";
import {normalizeSubject} from "@/lib/subjects";
import {GuidanceTip} from "@/components/ui/guidance-tip";
import {pickActiveGuidance, type GuidanceRule} from "@/lib/guidance-engine";
import {getUpgradeCtas, type AppPlanType} from "@/src/config/plans";

type LibrarySortOption =
  | "RECENTLY_UPDATED"
  | "RECENTLY_REVIEWED"
  | "NEWEST"
  | "TITLE_ASC"
  | "TITLE_DESC"
  | "OLDEST";

type LibraryReadinessFilter = "ALL" | "DRAFT" | "QUIZ_READY" | "STUDY_PACK_READY";
type LibraryVisibilityFilter = "ALL" | "PUBLIC" | "PRIVATE";

const LIBRARY_PAGE_SIZE = 20;
const ALL_SUBJECTS = "__ALL_SUBJECTS__";
const ALL_COURSE_PROGRAMS = "__ALL_COURSE_PROGRAMS__";
const LIBRARY_FILTER_SYNC_DEBOUNCE_MS = 400;
const SUBJECT_FALLBACK = "General";
const POPULAR_TAG_LIMIT = 5;
const COURSE_PROGRAM_LIMIT = 10;
const BROWSE_ALL_LABEL = "Browse all";
const TAG_SELECTOR_TITLE = "Select tags";
const MORE_FILTERS_TITLE = "More Filters";
const SAVE_FILTER_NAME_MAX_LENGTH = 100;
const COLLECTION_TITLE_MAX_LENGTH = 150;
const FILTER_SAVED_TOAST = "Filter saved";
const FILTER_DELETED_TOAST = "Filter deleted";
const FILTER_SAVE_ERROR_TOAST = "Could not save filter";
const FILTER_DELETE_ERROR_TOAST = "Could not delete filter";
const BULK_RESULT_FETCH_MAX_ATTEMPTS = 5;
const BULK_RESULT_FETCH_RETRY_DELAY_MS = 800;
const TEXT_LINK_CLASS_NAME = "shrink-0 text-xs font-medium text-blue-700 hover:text-blue-800 dark:text-blue-300 dark:hover:text-blue-200";
const SORT_LABELS: Record<LibrarySortOption, string> = {
  RECENTLY_UPDATED: "Recently Updated",
  RECENTLY_REVIEWED: "Recently Reviewed",
  NEWEST: "Newest",
  TITLE_ASC: "Title (A-Z)",
  TITLE_DESC: "Title (Z-A)",
  OLDEST: "Oldest",
};
const READINESS_FILTER_LABELS: Record<LibraryReadinessFilter, string> = {
  ALL: "All",
  DRAFT: "Draft",
  QUIZ_READY: "Quiz Ready",
  STUDY_PACK_READY: "Study Pack Ready",
};
const VISIBILITY_FILTER_LABELS: Record<LibraryVisibilityFilter, string> = {
  ALL: "All",
  PUBLIC: "Public",
  PRIVATE: "Private",
};
const VISIBILITY_FILTER_KEYS = Object.keys(VISIBILITY_FILTER_LABELS) as LibraryVisibilityFilter[];

type ToastMessage = string | {
  message: string;
  href: string;
  linkLabel: string;
};

type BulkGenerationFailureBanner = Pick<
  BulkGenerationResultResponse,
  | "subject"
  | "courseProgram"
  | "targetProfileType"
  | "makePublic"
  | "requestedCount"
  | "createdCount"
  | "failedTopics"
  | "quotaBlockedTopics"
>;

function waitForBulkResultRetry() {
  return new Promise((resolve) => {
    globalThis.setTimeout(resolve, BULK_RESULT_FETCH_RETRY_DELAY_MS);
  });
}

function normalizeTags(tags: string[] | null | undefined): string[] {
  if (!Array.isArray(tags)) {
    return [];
  }
  return tags
    .map((tag) => tag?.trim())
    .filter((tag): tag is string => Boolean(tag && tag.length > 0));
}

function formatRelativeReviewTime(lastReviewedAt: string | null | undefined): string {
  if (!lastReviewedAt) {
    return "Not reviewed yet";
  }

  const timestamp = new Date(lastReviewedAt).getTime();
  if (Number.isNaN(timestamp)) {
    return "Not reviewed yet";
  }

  const dayMs = 24 * 60 * 60 * 1000;
  const diffMs = Math.max(0, Date.now() - timestamp);
  const diffDays = Math.floor(diffMs / dayMs);

  if (diffDays <= 0) {
    return "today";
  }
  if (diffDays === 1) {
    return "yesterday";
  }
  if (diffDays < 30) {
    return `${diffDays} days ago`;
  }
  return new Date(lastReviewedAt).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
  });
}

function formatVisibilityLabel(visibility: NoteVisibility) {
  return visibility === "PUBLIC" ? "Public" : "Private";
}

function renderVisibilityIcon(visibility: NoteVisibility) {
  const Icon = visibility === "PUBLIC" ? Globe : Lock;
  const label = formatVisibilityLabel(visibility);
  return (
    <span
      className={visibility === "PUBLIC" ? "text-blue-600 dark:text-blue-300" : "text-foreground/55"}
      aria-label={label}
      title={label}
    >
      <Icon className="h-4 w-4" aria-hidden="true" />
      <span className="sr-only">{label}</span>
    </span>
  );
}

function getLibrarySubject(item: Pick<NoteListItemResponse, "subject" | "courseProgram">): string {
  return normalizeSubject(item.subject)
    ?? normalizeCourseProgram(item.courseProgram)
    ?? SUBJECT_FALLBACK;
}

function getComboboxItemClassName(isSelected: boolean) {
  return `w-full px-3 py-2.5 text-left text-sm transition-colors ${
    isSelected
      ? "bg-blue-500/10 font-medium text-blue-700 dark:text-blue-300"
      : "text-foreground hover:bg-highlight"
  }`;
}

function getFilterChipClassName(isSelected: boolean) {
  return `shrink-0 rounded-full border px-3 py-1.5 text-sm font-medium transition-colors ${
    isSelected
      ? "border-blue-600 bg-blue-600 text-white dark:border-blue-400 dark:bg-blue-500 dark:text-slate-950"
      : "border-border bg-background text-foreground/75 hover:bg-highlight"
  }`;
}



function updateRecentValues(previous: string[], values: string[]) {
  const next = [...previous];
  for (const value of values) {
    const existingIndex = next.indexOf(value);
    if (existingIndex >= 0) {
      next.splice(existingIndex, 1);
    }
    next.unshift(value);
  }
  return next.slice(0, 8);
}

function buildPriorityComparator(recentValues: string[], counts: Map<string, number>) {
  return (left: string, right: string) => {
    const leftRecentIndex = recentValues.indexOf(left);
    const rightRecentIndex = recentValues.indexOf(right);
    const leftIsRecent = leftRecentIndex >= 0;
    const rightIsRecent = rightRecentIndex >= 0;

    if (leftIsRecent || rightIsRecent) {
      if (leftIsRecent && rightIsRecent && leftRecentIndex !== rightRecentIndex) {
        return leftRecentIndex - rightRecentIndex;
      }
      if (leftIsRecent) {
        return -1;
      }
      if (rightIsRecent) {
        return 1;
      }
    }

    const countDiff = (counts.get(right) ?? 0) - (counts.get(left) ?? 0);
    if (countDiff !== 0) {
      return countDiff;
    }

    return left.localeCompare(right);
  };
}

function parseReadinessFilter(value: string | null): LibraryReadinessFilter {
  if (value === "draft") return "DRAFT";
  if (value === "quiz_ready") return "QUIZ_READY";
  if (value === "study_pack_ready") return "STUDY_PACK_READY";
  return "ALL";
}

function parseVisibilityFilter(value: string | null): LibraryVisibilityFilter {
  if (value === "public") return "PUBLIC";
  if (value === "private") return "PRIVATE";
  return "ALL";
}

function parseSortOption(value: string | null): LibrarySortOption {
  if (value === "reviewed") return "RECENTLY_REVIEWED";
  if (value === "newest") return "NEWEST";
  if (value === "title_asc") return "TITLE_ASC";
  if (value === "title_desc") return "TITLE_DESC";
  if (value === "oldest") return "OLDEST";
  return "RECENTLY_UPDATED";
}

function readinessFilterToParam(filter: LibraryReadinessFilter): string | null {
  if (filter === "DRAFT") return "draft";
  if (filter === "QUIZ_READY") return "quiz_ready";
  if (filter === "STUDY_PACK_READY") return "study_pack_ready";
  return null;
}

function sortOptionToParam(sort: LibrarySortOption): string | null {
  if (sort === "RECENTLY_REVIEWED") return "reviewed";
  if (sort === "NEWEST") return "newest";
  if (sort === "TITLE_ASC") return "title_asc";
  if (sort === "TITLE_DESC") return "title_desc";
  if (sort === "OLDEST") return "oldest";
  return null;
}

function buildLibraryUrl(
  q: string,
  subject: string,
  cp: string,
  tags: string[],
  status: LibraryReadinessFilter,
  sort: LibrarySortOption,
  visibility: LibraryVisibilityFilter = "ALL",
): string {
  const params = new URLSearchParams();
  const trimmedQ = q.trim();
  if (trimmedQ) params.set("q", trimmedQ);
  if (subject !== ALL_SUBJECTS) params.set("subject", subject);
  if (cp !== ALL_COURSE_PROGRAMS) params.set("cp", cp);
  if (tags.length > 0) params.set("tags", tags.join(","));
  const statusParam = readinessFilterToParam(status);
  if (statusParam) params.set("status", statusParam);
  if (visibility === "PUBLIC") params.set("visibility", "public");
  if (visibility === "PRIVATE") params.set("visibility", "private");
  const sortParam = sortOptionToParam(sort);
  if (sortParam) params.set("sort", sortParam);
  const qs = params.toString();
  return `/library${qs ? `?${qs}` : ""}`;
}

function buildSavedFilterState(
  q: string,
  subject: string,
  cp: string,
  tags: string[],
  status: LibraryReadinessFilter,
  sort: LibrarySortOption,
  visibility: LibraryVisibilityFilter = "ALL",
): SavedLibraryFilterState {
  const filterState: SavedLibraryFilterState = {};
  const trimmedSearch = q.trim();
  if (trimmedSearch) filterState.search = trimmedSearch;
  if (subject !== ALL_SUBJECTS) filterState.subject = subject;
  if (cp !== ALL_COURSE_PROGRAMS) filterState.courseProgram = cp;
  if (tags.length > 0) filterState.tags = tags;
  if (status !== "ALL") filterState.status = status;
  if (visibility !== "ALL") filterState.visibility = visibility;
  if (sort !== "RECENTLY_UPDATED") filterState.sort = sort;
  return filterState;
}

function readSavedString(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

function readSavedTags(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .map((entry) => readSavedString(entry))
    .filter((entry): entry is string => entry !== null);
}

function readSavedReadinessFilter(value: unknown): LibraryReadinessFilter {
  if (value === "DRAFT" || value === "QUIZ_READY" || value === "STUDY_PACK_READY") {
    return value;
  }
  return "ALL";
}

function readSavedVisibilityFilter(value: unknown): LibraryVisibilityFilter {
  if (value === "PUBLIC" || value === "PRIVATE") return value;
  return "ALL";
}

function readSavedSortOption(value: unknown): LibrarySortOption {
  if (
    value === "RECENTLY_REVIEWED"
    || value === "NEWEST"
    || value === "TITLE_ASC"
    || value === "TITLE_DESC"
    || value === "OLDEST"
  ) {
    return value;
  }
  return "RECENTLY_UPDATED";
}

function LibraryLoading() {
  return (
    <div className="space-y-4">
      <Card className="space-y-3 p-4 sm:p-6">
        <div className="h-6 w-40 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-64 animate-pulse rounded bg-foreground/10" />
      </Card>
      <div className="grid gap-4 md:grid-cols-2">
        {Array.from({ length: 4 }).map((_, index) => (
          <Card key={`library-skeleton-${index}`} className="space-y-3 p-4 sm:p-6">
            <div className="h-5 w-3/4 animate-pulse rounded bg-foreground/10" />
            <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
            <div className="h-4 w-2/3 animate-pulse rounded bg-foreground/10" />
          </Card>
        ))}
      </div>
    </div>
  );
}

function canIncludeInExam(item: NoteListItemResponse): boolean {
  return Boolean(item.generatedQuizId);
}

function CreatePlanModal({
  isOpen,
  selectedNoteIds,
  singularLabel,
  onClose,
  onCreated,
}: Readonly<{
  isOpen: boolean;
  selectedNoteIds: string[];
  singularLabel: string;
  onClose: () => void;
  onCreated: (collectionId: string) => void;
}>) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [creating, setCreating] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) {
      setTitle("");
      setDescription("");
      setCreating(false);
      setSubmitError(null);
    }
  }, [isOpen]);

  const handleCreate = async () => {
    const trimmedTitle = title.trim();
    if (!trimmedTitle) {
      setSubmitError("Title is required.");
      return;
    }
    setCreating(true);
    setSubmitError(null);
    try {
      const saved = await createCollection({
        title: trimmedTitle,
        description: description.trim() || null,
        noteIds: selectedNoteIds,
      });
      onCreated(saved.id);
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : `Could not create this ${singularLabel.toLowerCase()}.`);
    } finally {
      setCreating(false);
    }
  };

  const noteCountLabel = selectedNoteIds.length === 0
    ? `an empty ${singularLabel.toLowerCase()} you can fill later`
    : `${selectedNoteIds.length} selected note${selectedNoteIds.length === 1 ? "" : "s"}`;

  return (
    <AppModal
      isOpen={isOpen}
      title={`New ${singularLabel}`}
      description={`Create a ${singularLabel.toLowerCase()} with ${noteCountLabel}.`}
      onClose={onClose}
      panelClassName="sm:max-w-lg"
      actions={(
        <Button type="button" variant="secondary" onClick={onClose}>
          Close
        </Button>
      )}
    >
      <div className="space-y-4">
        {submitError ? (
          <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-200">
            {submitError}
          </p>
        ) : null}
        <label className="block space-y-1.5">
          <span className="text-sm font-medium text-foreground">Title</span>
          <input
            value={title}
            maxLength={COLLECTION_TITLE_MAX_LENGTH}
            onChange={(event) => {
              setTitle(event.target.value);
              setSubmitError(null);
            }}
            placeholder={`${singularLabel} title`}
            className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
          />
        </label>
        <label className="block space-y-1.5">
          <span className="text-sm font-medium text-foreground">Description</span>
          <textarea
            value={description}
            onChange={(event) => {
              setDescription(event.target.value);
              setSubmitError(null);
            }}
            placeholder={`Optional context for this ${singularLabel.toLowerCase()}`}
            className="min-h-24 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
          />
        </label>
        <Button
          type="button"
          loading={creating}
          loadingText="Creating..."
          onClick={() => void handleCreate()}
        >
          Create {singularLabel}
        </Button>
      </div>
    </AppModal>
  );
}

export default function LibraryPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const initialLoadStartedRef = useRef(false);
  const courseProgramDropdownRef = useRef<HTMLDivElement>(null);
  const bulkGraceUntilRef = useRef(0);
  // Tracks note ids already shown so rows that arrive later (via the generation
  // poller) can animate in. null until the first load seeds it, so the initial
  // list does not animate wholesale.
  const seenNoteIdsRef = useRef<Set<string> | null>(null);
  const [items, setItems] = useState<NoteListItemResponse[]>([]);
  const [autoRefreshActive, setAutoRefreshActive] = useState(false);
  const [enteringNoteIds, setEnteringNoteIds] = useState<Set<string>>(() => new Set());
  const [searchQuery, setSearchQuery] = useState(() => searchParams.get("q") ?? "");
  const [selectedSubject, setSelectedSubject] = useState<string>(() => searchParams.get("subject") ?? ALL_SUBJECTS);
  const [selectedCourseProgram, setSelectedCourseProgram] = useState<string>(() => searchParams.get("cp") ?? ALL_COURSE_PROGRAMS);
  const [selectedTags, setSelectedTags] = useState<string[]>(() => {
    const raw = searchParams.get("tags");
    return raw ? raw.split(",").filter(Boolean) : [];
  });
  const [tagDraft, setTagDraft] = useState<string[]>([]);
  const [sortBy, setSortBy] = useState<LibrarySortOption>(() => parseSortOption(searchParams.get("sort")));
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [subjectSuggestions, setSubjectSuggestions] = useState<string[]>([]);
  const [visibleCount, setVisibleCount] = useState(LIBRARY_PAGE_SIZE);
  const [sortSheetOpen, setSortSheetOpen] = useState(false);
  const [moreFiltersOpen, setMoreFiltersOpen] = useState(false);
  const [tagSelectorOpen, setTagSelectorOpen] = useState(false);
  const [subjectComboOpen, setSubjectComboOpen] = useState(false);
  const [courseProgramComboOpen, setCourseProgramComboOpen] = useState(false);
  const [subjectComboTyped, setSubjectComboTyped] = useState(false);
  const [courseProgramComboTyped, setCourseProgramComboTyped] = useState(false);
  const [tagSearchQuery, setTagSearchQuery] = useState("");
  const [subjectSearchQuery, setSubjectSearchQuery] = useState("");
  const [courseProgramSearchQuery, setCourseProgramSearchQuery] = useState("");
  const [recentTags, setRecentTags] = useState<string[]>([]);
  const [recentSubjects, setRecentSubjects] = useState<string[]>([]);
  const [readinessFilter, setReadinessFilter] = useState<LibraryReadinessFilter>(() => parseReadinessFilter(searchParams.get("status")));
  const [visibilityFilter, setVisibilityFilter] = useState<LibraryVisibilityFilter>(() => parseVisibilityFilter(searchParams.get("visibility")));
  const [selectionMode, setSelectionMode] = useState(false);
  const [selectedNoteIds, setSelectedNoteIds] = useState<string[]>([]);
  const [createPlanOpen, setCreatePlanOpen] = useState(false);
  const [toast, setToast] = useState<ToastMessage | null>(null);
  const [pendingBulkResultId, setPendingBulkResultId] = useState<string | null>(null);
  const [bulkFailureBanner, setBulkFailureBanner] = useState<BulkGenerationFailureBanner | null>(null);
  const [savedFilters, setSavedFilters] = useState<SavedLibraryFilterResponse[]>([]);
  const [savedFiltersLoading, setSavedFiltersLoading] = useState(true);
  const [savedFiltersUnavailable, setSavedFiltersUnavailable] = useState(false);
  const [savedFiltersOpen, setSavedFiltersOpen] = useState(false);
  const [saveFilterOpen, setSaveFilterOpen] = useState(false);
  const [saveFilterName, setSaveFilterName] = useState("");
  const [saveFilterError, setSaveFilterError] = useState<string | null>(null);
  const [saveFilterSubmitting, setSaveFilterSubmitting] = useState(false);
  const [deletingSavedFilterId, setDeletingSavedFilterId] = useState<string | null>(null);

  const authUser = getAuthUser();
  const isTeacherExamBuilderEnabled = authUser?.profileType === "TEACHER";
  const isTeacherProfile = authUser?.profileType === "TEACHER";
  const collectionLabels = getCollectionLabels(authUser?.profileType);
  const showQuizReadyIndicators = shouldShowQuizReadyIndicator(
    authUser?.profileType,
    "PRIVATE_LIBRARY",
    authUser?.role,
  );

  const visibleReadinessFilters = useMemo(() => (
    (Object.keys(READINESS_FILTER_LABELS) as LibraryReadinessFilter[])
      .filter((filterKey) => showQuizReadyIndicators || filterKey !== "QUIZ_READY")
  ), [showQuizReadyIndicators]);

  const loadLibrary = useCallback(async () => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const [notesResult, subjectsResult] = await Promise.allSettled([
        listNotes(),
        listSubjects("mine"),
      ]);
      if (notesResult.status !== "fulfilled") {
        throw notesResult.reason;
      }
      const notes = notesResult.value;
      // Seed the seen-set from the full load so only later (poller) arrivals animate.
      seenNoteIdsRef.current = new Set(notes.map((note) => note.id));
      setItems(notes);
      setSubjectSuggestions(subjectsResult.status === "fulfilled" ? subjectsResult.value : []);
      setVisibleCount(LIBRARY_PAGE_SIZE);
    } catch (loadError) {
      const message = loadError instanceof Error ? loadError.message : "Could not load your notes.";
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [router]);

  // Silent refresh used by the generation poller: updates the note list only,
  // without toggling the loading skeleton or resetting pagination.
  const fetchNotesSilently = useCallback(async (): Promise<NoteListItemResponse[] | null> => {
    if (!getAuthUser()) {
      return null;
    }
    try {
      const notes = await listNotes();
      setItems(notes);
      return notes;
    } catch {
      // Keep the current list; the next poll can recover.
      return null;
    }
  }, []);

  const loadSavedFilters = useCallback(async () => {
    setSavedFiltersLoading(true);
    setSavedFiltersUnavailable(false);
    try {
      const filters = await getSavedLibraryFilters();
      setSavedFilters(filters);
    } catch {
      setSavedFilters([]);
      setSavedFiltersUnavailable(true);
    } finally {
      setSavedFiltersLoading(false);
    }
  }, []);

  useEffect(() => {
    if (initialLoadStartedRef.current) {
      return;
    }
    initialLoadStartedRef.current = true;
    void loadLibrary();
  }, [loadLibrary]);

  useEffect(() => {
    void loadSavedFilters();
  }, [loadSavedFilters]);

  useEffect(() => {
    if (!toast) {
      return undefined;
    }
    const timeoutId = globalThis.setTimeout(() => setToast(null), 2200);
    return () => globalThis.clearTimeout(timeoutId);
  }, [toast]);

  useEffect(() => {
    const queuedFlash = consumeBulkQueuedFlash();
    if (queuedFlash) {
      const queuedCount = queuedFlash.queuedCount;
      setToast(`Queued ${queuedCount} note${queuedCount === 1 ? "" : "s"} — they'll appear here as they finish generating.`);
      setPendingBulkResultId(queuedFlash.resultId);
      // Bulk rows do not exist yet at redirect, so seed a grace window during
      // which the poller will not stop even though nothing is generating yet.
      bulkGraceUntilRef.current = Date.now()
        + LIBRARY_GENERATION_POLL_QUIET_TICKS * STUDY_PACK_GENERATION_POLL_INTERVAL_MS;
      setAutoRefreshActive(true);
    }
  }, []);

  useEffect(() => {
    if (!pendingBulkResultId || autoRefreshActive) {
      return undefined;
    }

    let active = true;
    const fetchBulkResult = async () => {
      for (let attempt = 1; attempt <= BULK_RESULT_FETCH_MAX_ATTEMPTS; attempt += 1) {
        try {
          const result = await getBulkGenerationResult(pendingBulkResultId);
          if (!active) {
            return;
          }
          setPendingBulkResultId(null);
          setBulkFailureBanner(
            result.failedTopics.length > 0 || result.quotaBlockedTopics.length > 0 ? result : null,
          );
          return;
        } catch (resultError) {
          if (!active) {
            return;
          }
          const shouldRetry = resultError instanceof ApiRequestError
            && resultError.status === 404
            && attempt < BULK_RESULT_FETCH_MAX_ATTEMPTS;
          if (!shouldRetry) {
            setPendingBulkResultId(null);
            return;
          }
          await waitForBulkResultRetry();
        }
      }
      if (active) {
        setPendingBulkResultId(null);
      }
    };

    void fetchBulkResult();
    return () => {
      active = false;
    };
  }, [autoRefreshActive, pendingBulkResultId]);

  // Keep auto-refresh running whenever a visible note is still generating its
  // Study Pack — this covers single-note generation too, not just bulk.
  const hasGeneratingNote = useMemo(
    () => items.some((item) => item.studyPackStatus === "GENERATING"),
    [items],
  );

  useEffect(() => {
    if (hasGeneratingNote) {
      setAutoRefreshActive(true);
    }
  }, [hasGeneratingNote]);

  useEffect(() => {
    if (!autoRefreshActive) {
      return undefined;
    }

    let active = true;
    let inFlight = false;
    let quietTicks = 0;
    let totalTicks = 0;
    // Seed below zero so the first appearance of any row registers as growth.
    let highWatermark = -1;
    const graceUntil = bulkGraceUntilRef.current;

    const tick = async () => {
      if (!active || inFlight) {
        return;
      }
      inFlight = true;
      totalTicks += 1;
      try {
        const notes = await fetchNotesSilently();
        if (!active || notes === null) {
          return;
        }
        const anyGenerating = notes.some((item) => item.studyPackStatus === "GENERATING");
        const grew = notes.length > highWatermark;
        highWatermark = Math.max(highWatermark, notes.length);
        const withinGrace = Date.now() < graceUntil;
        if (anyGenerating || grew || withinGrace) {
          quietTicks = 0;
        } else {
          quietTicks += 1;
        }
        if (quietTicks >= LIBRARY_GENERATION_POLL_QUIET_TICKS || totalTicks >= LIBRARY_GENERATION_POLL_MAX_TICKS) {
          bulkGraceUntilRef.current = 0;
          setAutoRefreshActive(false);
        }
      } finally {
        inFlight = false;
      }
    };

    const intervalId = globalThis.setInterval(() => void tick(), STUDY_PACK_GENERATION_POLL_INTERVAL_MS);
    return () => {
      active = false;
      globalThis.clearInterval(intervalId);
    };
  }, [autoRefreshActive, fetchNotesSilently]);

  // Animate rows that arrive after the initial load (e.g. bulk-generated notes
  // surfaced by the poller) so they do not just pop into the list.
  useEffect(() => {
    if (seenNoteIdsRef.current === null) {
      return undefined;
    }
    const newIds = items
      .map((item) => item.id)
      .filter((id) => !seenNoteIdsRef.current!.has(id));
    if (newIds.length === 0) {
      return undefined;
    }
    for (const id of newIds) {
      seenNoteIdsRef.current.add(id);
    }
    setEnteringNoteIds(new Set(newIds));
    const timeoutId = globalThis.setTimeout(() => setEnteringNoteIds(new Set()), 600);
    return () => globalThis.clearTimeout(timeoutId);
  }, [items]);

  useEffect(() => {
    const timeoutId = globalThis.setTimeout(() => {
      router.replace(
        buildLibraryUrl(searchQuery, selectedSubject, selectedCourseProgram, selectedTags, readinessFilter, sortBy, visibilityFilter),
        { scroll: false },
      );
    }, LIBRARY_FILTER_SYNC_DEBOUNCE_MS);
    return () => globalThis.clearTimeout(timeoutId);
  }, [searchQuery, selectedSubject, selectedCourseProgram, selectedTags, readinessFilter, sortBy, visibilityFilter, router]);

  const hasItems = items.length > 0;

  const derivedSubjects = useMemo(() => {
    const subjectSet = new Set<string>();
    for (const item of items) {
      subjectSet.add(getLibrarySubject(item));
    }
    return Array.from(subjectSet).sort((left, right) => left.localeCompare(right));
  }, [items]);

  const availableSubjects = useMemo(() => {
    return Array.from(
      new Set([
        ...subjectSuggestions
          .map((subject) => normalizeSubject(subject))
          .filter((subject): subject is string => Boolean(subject)),
        ...derivedSubjects,
      ]),
    ).sort((left, right) => left.localeCompare(right));
  }, [derivedSubjects, subjectSuggestions]);

  const subjectCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const item of items) {
      const subject = getLibrarySubject(item);
      counts.set(subject, (counts.get(subject) ?? 0) + 1);
    }
    return counts;
  }, [items]);

  const tagCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const item of items) {
      for (const tag of normalizeTags(item.tags)) {
        counts.set(tag, (counts.get(tag) ?? 0) + 1);
      }
    }
    return counts;
  }, [items]);

  const courseProgramCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const item of items) {
      const courseProgram = normalizeCourseProgram(item.courseProgram);
      if (courseProgram) {
        counts.set(courseProgram, (counts.get(courseProgram) ?? 0) + 1);
      }
    }
    return counts;
  }, [items]);

  const availableCoursePrograms = useMemo(() => {
    return Array.from(courseProgramCounts.keys()).sort((left, right) => {
      const countDiff = (courseProgramCounts.get(right) ?? 0) - (courseProgramCounts.get(left) ?? 0);
      return countDiff !== 0 ? countDiff : left.localeCompare(right);
    });
  }, [courseProgramCounts]);

  const availableTags = useMemo(() => {
    return Array.from(tagCounts.keys());
  }, [tagCounts]);

  useEffect(() => {
    if (loading) return;
    if (selectedSubject !== ALL_SUBJECTS && !availableSubjects.includes(selectedSubject)) {
      setSelectedSubject(ALL_SUBJECTS);
    }
  }, [availableSubjects, loading, selectedSubject]);

  useEffect(() => {
    if (loading) return;
    if (selectedCourseProgram !== ALL_COURSE_PROGRAMS && !availableCoursePrograms.includes(selectedCourseProgram)) {
      setSelectedCourseProgram(ALL_COURSE_PROGRAMS);
    }
  }, [availableCoursePrograms, loading, selectedCourseProgram]);

  useEffect(() => {
    if (loading) return;
    setSelectedTags((previous) => previous.filter((tag) => availableTags.includes(tag)));
    setTagDraft((previous) => previous.filter((tag) => availableTags.includes(tag)));
  }, [availableTags, loading]);

  useEffect(() => {
    if (tagSelectorOpen) {
      setTagDraft(selectedTags);
      setTagSearchQuery("");
    }
  }, [selectedTags, tagSelectorOpen]);

  useEffect(() => {
    if (moreFiltersOpen) {
      setSubjectSearchQuery("");
      setCourseProgramSearchQuery("");
      setSubjectComboOpen(false);
      setCourseProgramComboOpen(false);
    }
  }, [moreFiltersOpen]);

  useEffect(() => {
    if (!courseProgramComboOpen) return;
    const id = globalThis.setTimeout(() => {
      courseProgramDropdownRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }, 0);
    return () => globalThis.clearTimeout(id);
  }, [courseProgramComboOpen]);

  useEffect(() => {
    setVisibleCount(LIBRARY_PAGE_SIZE);
  }, [readinessFilter, visibilityFilter, searchQuery, selectedCourseProgram, selectedSubject, selectedTags, sortBy]);

  useEffect(() => {
    if (!showQuizReadyIndicators && readinessFilter === "QUIZ_READY") {
      setReadinessFilter("ALL");
    }
  }, [readinessFilter, showQuizReadyIndicators]);

  const clearFilters = useCallback(() => {
    setSearchQuery("");
    setSelectedSubject(ALL_SUBJECTS);
    setSelectedCourseProgram(ALL_COURSE_PROGRAMS);
    setSelectedTags([]);
    setTagDraft([]);
    setSubjectSearchQuery("");
    setCourseProgramSearchQuery("");
    setTagSearchQuery("");
    setReadinessFilter("ALL");
    setVisibilityFilter("ALL");
  }, []);

  const toggleDraftTag = useCallback((tag: string) => {
    setTagDraft((previous) => (
      previous.includes(tag)
        ? previous.filter((selectedTag) => selectedTag !== tag)
        : [...previous, tag]
    ));
  }, []);

  const subjectPriorityComparator = useMemo(
    () => buildPriorityComparator(recentSubjects, subjectCounts),
    [recentSubjects, subjectCounts],
  );
  const tagPriorityComparator = useMemo(
    () => buildPriorityComparator(recentTags, tagCounts),
    [recentTags, tagCounts],
  );

  const displayedSubjects = useMemo(() => {
    return [...availableSubjects].sort(subjectPriorityComparator);
  }, [availableSubjects, subjectPriorityComparator]);

  const filteredModalSubjects = useMemo(() => {
    const query = subjectComboTyped ? subjectSearchQuery.trim().toLowerCase() : "";
    return displayedSubjects.filter((subject) => (
      query.length === 0 || subject.toLowerCase().includes(query)
    ));
  }, [displayedSubjects, subjectComboTyped, subjectSearchQuery]);

  const displayedTags = useMemo(() => {
    return [...availableTags].sort(tagPriorityComparator);
  }, [availableTags, tagPriorityComparator]);

  const filteredModalTags = useMemo(() => {
    const query = tagSearchQuery.trim().toLowerCase();
    return displayedTags.filter((tag) => (
      query.length === 0 || tag.toLowerCase().includes(query)
    ));
  }, [displayedTags, tagSearchQuery]);

  const filteredModalCoursePrograms = useMemo(() => {
    const query = courseProgramComboTyped ? courseProgramSearchQuery.trim().toLowerCase() : "";
    const source = query.length === 0
      ? availableCoursePrograms.slice(0, COURSE_PROGRAM_LIMIT)
      : availableCoursePrograms;
    return source.filter((courseProgram) => (
      query.length === 0 || courseProgram.toLowerCase().includes(query)
    ));
  }, [availableCoursePrograms, courseProgramComboTyped, courseProgramSearchQuery]);

  const visiblePopularTags = useMemo(() => {
    const ordered = [
      ...selectedTags,
      ...displayedTags.filter((tag) => !selectedTags.includes(tag)),
    ];
    const deduped = Array.from(new Set(ordered));
    return deduped.slice(0, Math.max(POPULAR_TAG_LIMIT, selectedTags.length));
  }, [displayedTags, selectedTags]);

  const hasActiveFilters = searchQuery.trim().length > 0
    || readinessFilter !== "ALL"
    || visibilityFilter !== "ALL"
    || selectedCourseProgram !== ALL_COURSE_PROGRAMS
    || selectedSubject !== ALL_SUBJECTS
    || selectedTags.length > 0;
  const currentSavedFilterState = useMemo(() => buildSavedFilterState(
    searchQuery,
    selectedSubject,
    selectedCourseProgram,
    selectedTags,
    readinessFilter,
    sortBy,
    visibilityFilter,
  ), [readinessFilter, visibilityFilter, searchQuery, selectedCourseProgram, selectedSubject, selectedTags, sortBy]);
  const hasSavableFilter = Object.keys(currentSavedFilterState).length > 0;
  const selectedNotes = useMemo(
    () => selectedNoteIds
      .map((noteId) => items.find((item) => item.id === noteId))
      .filter((item): item is NoteListItemResponse => Boolean(item)),
    [items, selectedNoteIds],
  );
  const selectedQuizReadyCount = useMemo(
    () => selectedNotes.filter(canIncludeInExam).length,
    [selectedNotes],
  );
  const selectedHasNonQuizReadyNotes = selectedNotes.length > selectedQuizReadyCount;

  const applySavedFilter = useCallback((filterState: SavedLibraryFilterState) => {
    const nextSearch = readSavedString(filterState.search) ?? "";
    const nextSubject = readSavedString(filterState.subject) ?? ALL_SUBJECTS;
    const nextCourseProgram = readSavedString(filterState.courseProgram) ?? ALL_COURSE_PROGRAMS;
    const nextTags = readSavedTags(filterState.tags);
    const nextReadinessFilter = readSavedReadinessFilter(filterState.status);
    const nextVisibilityFilter = readSavedVisibilityFilter(filterState.visibility);
    const nextSort = readSavedSortOption(filterState.sort);

    setSearchQuery(nextSearch);
    setSelectedSubject(nextSubject);
    setSelectedCourseProgram(nextCourseProgram);
    setSelectedTags(nextTags);
    setTagDraft(nextTags);
    setReadinessFilter(nextReadinessFilter);
    setVisibilityFilter(nextVisibilityFilter);
    setSortBy(nextSort);
    setSavedFiltersOpen(false);
    router.replace(
      buildLibraryUrl(nextSearch, nextSubject, nextCourseProgram, nextTags, nextReadinessFilter, nextSort, nextVisibilityFilter),
      { scroll: false },
    );
  }, [router]);

  const resetSaveFilterDialog = useCallback(() => {
    setSaveFilterName("");
    setSaveFilterError(null);
    setSaveFilterOpen(false);
  }, []);

  const handleSaveFilter = useCallback(async () => {
    const name = saveFilterName.trim();
    if (!name) {
      setSaveFilterError("Name is required");
      return;
    }
    if (name.length > SAVE_FILTER_NAME_MAX_LENGTH) {
      setSaveFilterError("Name must be 100 characters or fewer");
      return;
    }

    setSaveFilterSubmitting(true);
    setSaveFilterError(null);
    try {
      const created = await createSavedLibraryFilter(name, currentSavedFilterState);
      setSavedFilters((previous) => [created, ...previous]);
      setSavedFiltersUnavailable(false);
      setToast(FILTER_SAVED_TOAST);
      resetSaveFilterDialog();
    } catch (saveError) {
      if (saveError instanceof ApiRequestError && saveError.status === 400) {
        setSaveFilterError(saveError.message);
      } else {
        setToast(FILTER_SAVE_ERROR_TOAST);
      }
    } finally {
      setSaveFilterSubmitting(false);
    }
  }, [currentSavedFilterState, resetSaveFilterDialog, saveFilterName]);

  const handleDeleteSavedFilter = useCallback(async (filterId: string) => {
    setDeletingSavedFilterId(filterId);
    setSavedFilters((previous) => previous.filter((filter) => filter.id !== filterId));
    try {
      await deleteSavedLibraryFilter(filterId);
      setToast(FILTER_DELETED_TOAST);
    } catch {
      setToast(FILTER_DELETE_ERROR_TOAST);
    } finally {
      setDeletingSavedFilterId(null);
    }
  }, []);

  const sortedFilteredItems = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    const filtered = items.filter((item) => {
      const itemTitle = item.title?.trim() || "Untitled note";
      const itemTags = normalizeTags(item.tags);
      const itemSubject = getLibrarySubject(item);
      const itemCourseProgram = normalizeCourseProgram(item.courseProgram);

      const searchMatch = query.length === 0
        || itemTitle.toLowerCase().includes(query)
        || itemTags.some((tag) => tag.toLowerCase().includes(query));
      const effectiveReadinessFilter = !showQuizReadyIndicators && readinessFilter === "QUIZ_READY"
        ? "ALL"
        : readinessFilter;
      const readinessMatch = effectiveReadinessFilter === "ALL"
        || (effectiveReadinessFilter === "DRAFT" && item.studyPackStatus === "DRAFT")
        || (effectiveReadinessFilter === "QUIZ_READY" && Boolean(item.generatedQuizId))
        || (effectiveReadinessFilter === "STUDY_PACK_READY" && item.studyPackStatus === "STUDY_PACK_READY");
      const courseProgramMatch = selectedCourseProgram === ALL_COURSE_PROGRAMS
        || itemCourseProgram === selectedCourseProgram;
      const subjectMatch = selectedSubject === ALL_SUBJECTS || itemSubject === selectedSubject;
      const tagMatch = selectedTags.length === 0 || selectedTags.some((selectedTag) => itemTags.includes(selectedTag));
      const visibilityMatch = visibilityFilter === "ALL"
        || (visibilityFilter === "PUBLIC" && item.visibility === "PUBLIC")
        || (visibilityFilter === "PRIVATE" && item.visibility === "PRIVATE");

      return searchMatch && readinessMatch && courseProgramMatch && subjectMatch && tagMatch && visibilityMatch;
    });

    const byDateDesc = (leftDate: string | null | undefined, rightDate: string | null | undefined) => {
      const leftTime = leftDate ? new Date(leftDate).getTime() : 0;
      const rightTime = rightDate ? new Date(rightDate).getTime() : 0;
      return rightTime - leftTime;
    };

    const byDateAsc = (leftDate: string | null | undefined, rightDate: string | null | undefined) => {
      const leftTime = leftDate ? new Date(leftDate).getTime() : 0;
      const rightTime = rightDate ? new Date(rightDate).getTime() : 0;
      return leftTime - rightTime;
    };

    return [...filtered].sort((left, right) => {
      switch (sortBy) {
        case "TITLE_ASC":
          return (left.title ?? "Untitled note").localeCompare(right.title ?? "Untitled note");
        case "TITLE_DESC":
          return (right.title ?? "Untitled note").localeCompare(left.title ?? "Untitled note");
        case "OLDEST":
          return byDateAsc(left.createdAt, right.createdAt);
        case "NEWEST":
          return byDateDesc(left.createdAt, right.createdAt);
        case "RECENTLY_REVIEWED": {
          const reviewedDiff = byDateDesc(
            left.lastSessionCompletedAt,
            right.lastSessionCompletedAt,
          );
          if (reviewedDiff !== 0) {
            return reviewedDiff;
          }
          return byDateDesc(left.updatedAt, right.updatedAt);
        }
        case "RECENTLY_UPDATED":
        default:
          return byDateDesc(left.updatedAt, right.updatedAt);
      }
    });
  }, [items, readinessFilter, visibilityFilter, searchQuery, selectedCourseProgram, selectedSubject, selectedTags, showQuizReadyIndicators, sortBy]);

  const visibleItems = useMemo(
    () => sortedFilteredItems.slice(0, visibleCount),
    [sortedFilteredItems, visibleCount],
  );
  const hasMore = visibleCount < sortedFilteredItems.length;

  // Subject facet counts for the stats strip, computed over the notes matching
  // every active filter EXCEPT subject — so the chips reflect the current view
  // (e.g. only Nursing subjects when a Nursing course/program filter is active).
  const subjectFacets = useMemo(() => {
    const SUBJECT_FACET_LIMIT = 6;
    const query = searchQuery.trim().toLowerCase();
    const effectiveReadinessFilter = !showQuizReadyIndicators && readinessFilter === "QUIZ_READY"
      ? "ALL"
      : readinessFilter;
    const counts = new Map<string, number>();
    let total = 0;
    for (const item of items) {
      const itemTitle = item.title?.trim() || "Untitled note";
      const itemTags = normalizeTags(item.tags);
      const itemCourseProgram = normalizeCourseProgram(item.courseProgram);
      const searchMatch = query.length === 0
        || itemTitle.toLowerCase().includes(query)
        || itemTags.some((tag) => tag.toLowerCase().includes(query));
      const readinessMatch = effectiveReadinessFilter === "ALL"
        || (effectiveReadinessFilter === "DRAFT" && item.studyPackStatus === "DRAFT")
        || (effectiveReadinessFilter === "QUIZ_READY" && Boolean(item.generatedQuizId))
        || (effectiveReadinessFilter === "STUDY_PACK_READY" && item.studyPackStatus === "STUDY_PACK_READY");
      const courseProgramMatch = selectedCourseProgram === ALL_COURSE_PROGRAMS
        || itemCourseProgram === selectedCourseProgram;
      const tagMatch = selectedTags.length === 0 || selectedTags.some((selectedTag) => itemTags.includes(selectedTag));
      const visibilityMatch = visibilityFilter === "ALL"
        || (visibilityFilter === "PUBLIC" && item.visibility === "PUBLIC")
        || (visibilityFilter === "PRIVATE" && item.visibility === "PRIVATE");
      if (!(searchMatch && readinessMatch && courseProgramMatch && tagMatch && visibilityMatch)) {
        continue;
      }
      total += 1;
      const itemSubject = getLibrarySubject(item);
      counts.set(itemSubject, (counts.get(itemSubject) ?? 0) + 1);
    }
    const sorted = [...counts.entries()].sort(
      (left, right) => right[1] - left[1] || left[0].localeCompare(right[0]),
    );
    const topSubjects = sorted.slice(0, SUBJECT_FACET_LIMIT).map(([subject, count]) => ({ subject, count }));
    const otherSubjectsCount = sorted.slice(SUBJECT_FACET_LIMIT).reduce((sum, [, count]) => sum + count, 0);
    return { topSubjects, otherSubjectsCount, total };
  }, [items, readinessFilter, visibilityFilter, searchQuery, selectedCourseProgram, selectedTags, showQuizReadyIndicators]);

  const showSubjectStatsStrip = !loading
    && selectedSubject === ALL_SUBJECTS
    && subjectFacets.total >= 5
    && subjectFacets.topSubjects.length >= 2;

  const applySubjectStatsFilter = useCallback((subject: string) => {
    setSelectedSubject(subject);
    setRecentSubjects((previous) => updateRecentValues(previous, [subject]));
    router.replace(
      buildLibraryUrl(searchQuery, subject, selectedCourseProgram, selectedTags, readinessFilter, sortBy, visibilityFilter),
      { scroll: false },
    );
  }, [readinessFilter, visibilityFilter, router, searchQuery, selectedCourseProgram, selectedTags, sortBy]);

  const resetSelectionMode = useCallback(() => {
    setSelectionMode(false);
    setSelectedNoteIds([]);
  }, []);

  const startPlanSelection = useCallback(() => {
    setSelectedNoteIds([]);
    setSelectionMode(true);
  }, []);

  const handleNoteNavigate = useCallback((noteId: string) => {
    const returnUrl = buildLibraryUrl(searchQuery, selectedSubject, selectedCourseProgram, selectedTags, readinessFilter, sortBy, visibilityFilter);
    const params = new URLSearchParams({ from: "library", ref: returnUrl });
    router.push(`/notes/${noteId}?${params.toString()}`);
  }, [readinessFilter, visibilityFilter, router, searchQuery, selectedCourseProgram, selectedSubject, selectedTags, sortBy]);

  const toggleNoteSelection = useCallback((item: NoteListItemResponse) => {
    setSelectedNoteIds((previous) => (
      previous.includes(item.id)
        ? previous.filter((noteId) => noteId !== item.id)
        : [...previous, item.id]
    ));
  }, []);

  const allFilteredSelected = sortedFilteredItems.length > 0
    && sortedFilteredItems.every((item) => selectedNoteIds.includes(item.id));

  const toggleSelectAllFiltered = useCallback(() => {
    const filteredIds = sortedFilteredItems.map((item) => item.id);
    setSelectedNoteIds((previous) => {
      const allSelected = filteredIds.length > 0 && filteredIds.every((id) => previous.includes(id));
      if (allSelected) {
        const remove = new Set(filteredIds);
        return previous.filter((id) => !remove.has(id));
      }
      const merged = new Set(previous);
      filteredIds.forEach((id) => merged.add(id));
      return Array.from(merged);
    });
  }, [sortedFilteredItems]);

  const openExamBuilder = useCallback(() => {
    if (selectedNoteIds.length === 0 || selectedQuizReadyCount === 0) {
      return;
    }
    const params = new URLSearchParams({
      notes: selectedNoteIds.join(","),
    });
    router.push(`/library/exam-builder?${params.toString()}`);
  }, [router, selectedNoteIds, selectedQuizReadyCount]);

  const retryBulkFailures = useCallback(() => {
    if (!bulkFailureBanner || bulkFailureBanner.failedTopics.length === 0) {
      return;
    }
    setBulkGenerationRetryStash({
      subject: bulkFailureBanner.subject,
      courseProgram: bulkFailureBanner.courseProgram,
      targetProfileType: bulkFailureBanner.targetProfileType,
      makePublic: bulkFailureBanner.makePublic,
      topics: bulkFailureBanner.failedTopics,
    });
    setBulkFailureBanner(null);
    router.push("/library/bulk-generate");
  }, [bulkFailureBanner, router]);

  const currentPlan = (authUser?.planType ?? "FREE") as AppPlanType;
  const upgradeCtas = getUpgradeCtas(currentPlan, { profileType: authUser?.profileType ?? null });

  const handlePlanCreated = useCallback((collectionId: string) => {
    setCreatePlanOpen(false);
    resetSelectionMode();
    router.push(`/collections/${collectionId}`);
  }, [resetSelectionMode, router]);

  // Single-tip selection so library nudges never stack; Study Plan grouping is
  // prioritized for non-teachers as the v0.28.0 activation lever.
  const libraryGuidanceRules: GuidanceRule[] = [
    {
      id: "teacher-library-multi-note-select",
      priority: 1,
      condition: () => isTeacherProfile && !selectionMode && items.length >= 1,
      message: "Select multiple notes with the checkboxes, then add them to a lesson plan or build an exam from quiz-ready notes.",
    },
    {
      id: "library-study-plan-grouping",
      priority: 2,
      condition: () => !isTeacherProfile && !selectionMode && items.length >= 3,
      message: `Group related notes into a ${collectionLabels.singular} you can study as one set.`,
    },
    {
      id: "library-organization-habits",
      priority: 3,
      condition: () => items.length >= 5,
      message: "You're building a solid library. Try filtering by subject to find related notes quickly.",
    },
    {
      id: "library-first-note-organization",
      priority: 4,
      condition: () => items.length >= 1 && items.length <= 3,
      message: "Add a subject and tags when editing a note — it makes filtering your library much easier as it grows.",
    },
  ];
  const activeLibraryTip = pickActiveGuidance(libraryGuidanceRules);

  return (
    <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <PageHeader
        eyebrow="LIBRARY"
        title="Library"
        description="Browse and revisit all of your saved notes."
        actions={(
          <div className="flex w-full flex-col gap-2 sm:w-auto sm:flex-row">
            <CreateMenu
              className="w-full sm:w-auto"
              primaryLabel="New Note"
              onPrimary={() => router.push("/notes/new")}
              items={[
                { key: "note", label: "Note", description: "Write, import, or generate a note", onSelect: () => router.push("/notes/new") },
                { key: "import", label: "Import files", description: "Upload several files as draft notes", onSelect: () => router.push("/notes/import?from=library") },
                {
                  key: "bulk-generate",
                  label: "Bulk generate",
                  description: "Generate notes and Study Packs from a list of topics",
                  onSelect: () => router.push("/library/bulk-generate"),
                },
                { key: "collection", label: collectionLabels.singular, description: `Pick notes for a new ${collectionLabels.singular.toLowerCase()}`, onSelect: startPlanSelection },
              ]}
            />
          </div>
        )}
      />

      {bulkFailureBanner ? (
        <Card className="space-y-4 border-amber-300 bg-amber-50 p-4 text-amber-950 dark:border-amber-500/60 dark:bg-amber-950/30 dark:text-amber-100 sm:p-5">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div className="min-w-0 space-y-3">
              {bulkFailureBanner.quotaBlockedTopics.length > 0 ? (
                <section className="space-y-2">
                  <p className="text-sm font-semibold">
                    You&apos;ve used this month&apos;s note generations — {bulkFailureBanner.quotaBlockedTopics.length} topic{bulkFailureBanner.quotaBlockedTopics.length === 1 ? "" : "s"} weren&apos;t generated.
                  </p>
                  <ul className="list-disc space-y-1 pl-5 text-sm leading-relaxed">
                    {bulkFailureBanner.quotaBlockedTopics.map((topic) => (
                      <li key={topic} className="break-words">{topic}</li>
                    ))}
                  </ul>
                </section>
              ) : null}
              {bulkFailureBanner.failedTopics.length > 0 ? (
                <section className="space-y-2">
                  <p className="text-sm font-semibold">
                    {bulkFailureBanner.createdCount} of {bulkFailureBanner.requestedCount} notes generated. These couldn&apos;t be generated — try again:
                  </p>
                  <ul className="list-disc space-y-1 pl-5 text-sm leading-relaxed">
                    {bulkFailureBanner.failedTopics.map((topic) => (
                      <li key={topic} className="break-words">{topic}</li>
                    ))}
                  </ul>
                </section>
              ) : null}
            </div>
            <div className="flex shrink-0 flex-col gap-2 sm:flex-row">
              {bulkFailureBanner.quotaBlockedTopics.length > 0 && upgradeCtas.primary ? (
                <Button type="button" onClick={() => router.push("/settings?section=plans")}>
                  {upgradeCtas.primary.label}
                </Button>
              ) : null}
              {bulkFailureBanner.failedTopics.length > 0 ? (
                <Button type="button" onClick={retryBulkFailures}>
                  Retry these
                </Button>
              ) : null}
              <Button type="button" variant="outline" onClick={() => setBulkFailureBanner(null)}>
                Dismiss
              </Button>
            </div>
          </div>
        </Card>
      ) : null}

      {loading ? (
        <LibraryLoading />
      ) : error ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h2 className="text-xl font-semibold">Could not load notes</h2>
          <p className="text-sm text-foreground/75">{error}</p>
          <ResponsiveActionButton type="button" className="w-full sm:w-auto" onClick={() => void loadLibrary()} action="retry" label="Retry" />
        </Card>
      ) : !hasItems ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h2 className="text-xl font-semibold">Your note library is empty</h2>
          <p className="text-sm text-foreground/75">
            {isTeacherProfile
              ? "Create a note, generate a quiz, then export it as DOCX for your class. Build multi-note exams with Exam Builder."
              : "Create a note to get started — generate a Study Pack and quiz yourself in minutes."}
          </p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <ResponsiveActionLink href="/notes/new" action="create" label="Create Your First Note" className="w-full sm:w-auto" />
            {!isTeacherProfile ? (
              <ResponsiveActionLink href="/demo" action="open" label="Try Demo" variant="outline" className="w-full sm:w-auto" />
            ) : null}
          </div>
        </Card>
      ) : (
        <div className="space-y-4">
          {activeLibraryTip ? (
            <GuidanceTip
              tipId={activeLibraryTip.id}
              message={activeLibraryTip.message}
              trackAnalytics
              action={
                activeLibraryTip.id === "library-study-plan-grouping"
                  ? { label: `Create ${collectionLabels.singular}`, onClick: startPlanSelection }
                  : undefined
              }
            />
          ) : null}
          {selectionMode ? (
            <Card className="space-y-3 p-4 sm:p-5">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="space-y-1">
                  <h2 className="text-lg font-semibold">Pick notes for your new {collectionLabels.singular}</h2>
                  <p className="text-sm text-foreground/70">
                    {selectedNoteIds.length} note{selectedNoteIds.length === 1 ? "" : "s"} selected · filter and select, or create an empty {collectionLabels.singular.toLowerCase()}.
                  </p>
                  {isTeacherExamBuilderEnabled && selectedHasNonQuizReadyNotes && selectedQuizReadyCount > 0 ? (
                    <p className="text-xs font-medium text-amber-700 dark:text-amber-300">
                      Only quiz-ready notes will be added to the exam.
                    </p>
                  ) : null}
                  {isTeacherExamBuilderEnabled && selectedNoteIds.length > 0 && selectedQuizReadyCount === 0 ? (
                    <p className="text-xs font-medium text-amber-700 dark:text-amber-300">
                      Generate a quiz for at least one note to build an exam.
                    </p>
                  ) : null}
                </div>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={toggleSelectAllFiltered}
                    disabled={sortedFilteredItems.length === 0}
                  >
                    {allFilteredSelected ? "Deselect all" : `Select all (${sortedFilteredItems.length})`}
                  </Button>
                  <Button type="button" variant="outline" onClick={resetSelectionMode}>
                    Cancel
                  </Button>
                  <Button
                    type="button"
                    onClick={() => setCreatePlanOpen(true)}
                  >
                    Create {collectionLabels.singular}
                  </Button>
                  {isTeacherExamBuilderEnabled ? (
                    <Button
                      type="button"
                      variant="outline"
                      onClick={openExamBuilder}
                      disabled={selectedNoteIds.length === 0 || selectedQuizReadyCount === 0}
                    >
                      Build exam
                    </Button>
                  ) : null}
                </div>
              </div>
            </Card>
          ) : null}

          <Card className="sticky top-16 z-20 space-y-4 border-b border-border bg-background/95 p-4 backdrop-blur sm:p-5">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
              <div className="min-w-0 flex-1 space-y-2">
                <label htmlFor="library-search" className="text-sm font-medium">
                  Search
                </label>
                <div className="relative">
                  <input
                    id="library-search"
                    type="search"
                    value={searchQuery}
                    onChange={(event) => setSearchQuery(event.target.value)}
                    placeholder="Search titles and tags..."
                    className={`h-10 w-full rounded-lg border border-border bg-background pl-3 ${
                      searchQuery ? "pr-10" : "pr-3"
                    } text-base text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600 sm:text-sm [&::-webkit-search-cancel-button]:hidden`}
                  />
                  {searchQuery ? (
                    <button
                      type="button"
                      onClick={() => setSearchQuery("")}
                      aria-label="Clear search"
                      className="absolute inset-y-0 right-0 flex w-9 items-center justify-center text-foreground/60 transition-colors hover:text-foreground"
                    >
                      <X className="h-4 w-4" />
                    </button>
                  ) : null}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2 sm:flex sm:shrink-0">
                <Button
                  type="button"
                  variant="outline"
                  className="relative w-full sm:w-auto"
                  onClick={() => setMoreFiltersOpen(true)}
                  aria-label="Open more filters"
                >
                  <span className="inline-flex items-center gap-2">
                    <Filter className="h-4 w-4" aria-hidden="true" />
                    <span>More Filters</span>
                    {(readinessFilter !== "ALL" || selectedCourseProgram !== ALL_COURSE_PROGRAMS || selectedSubject !== ALL_SUBJECTS || selectedTags.length > 0) ? (
                      <span className="absolute right-2 top-2 h-2 w-2 rounded-full bg-blue-600 dark:bg-blue-400" aria-hidden="true" />
                    ) : null}
                  </span>
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  className="w-full sm:w-auto"
                  onClick={() => setSortSheetOpen(true)}
                  aria-label="Open sorting"
                >
                  <span className="inline-flex items-center gap-2">
                    <ArrowUpDown className="h-4 w-4" aria-hidden="true" />
                    <span>Sort</span>
                  </span>
                </Button>
              </div>
            </div>

            <p className="border-t border-border pt-3 text-sm text-foreground/60">
              {hasActiveFilters
                ? `${sortedFilteredItems.length} of ${items.length} notes`
                : `${items.length} notes`}
            </p>

            {(hasSavableFilter || (!savedFiltersUnavailable && (savedFiltersLoading || savedFilters.length > 0))) ? (
              <div className="flex flex-col gap-3 border-t border-border pt-3 sm:flex-row sm:items-start sm:justify-between">
                <div className="relative">
                  {!savedFiltersUnavailable ? (
                    <>
                      <Button
                        type="button"
                        variant="outline"
                        className="gap-2"
                        onClick={() => setSavedFiltersOpen((previous) => !previous)}
                        disabled={savedFiltersLoading}
                      >
                        <Bookmark className="h-4 w-4" aria-hidden="true" />
                        <span>{savedFiltersLoading ? "Loading filters..." : "Saved filters"}</span>
                      </Button>
                      {savedFiltersOpen ? (
                        <div className="absolute left-0 top-12 z-20 w-72 rounded-lg border border-border bg-background p-2 shadow-lg">
                          {savedFilters.length === 0 ? (
                            <p className="px-2 py-2 text-sm text-foreground/65">No saved filters yet.</p>
                          ) : (
                            <div className="max-h-72 space-y-1 overflow-y-auto">
                              {savedFilters.map((filter) => (
                                <div key={filter.id} className="flex items-center gap-2 rounded-md hover:bg-highlight">
                                  <button
                                    type="button"
                                    className="min-w-0 flex-1 px-3 py-2 text-left text-sm"
                                    onClick={() => applySavedFilter(filter.filterState)}
                                  >
                                    <span className="block truncate font-medium">{filter.name}</span>
                                  </button>
                                  <button
                                    type="button"
                                    className="rounded-md p-2 text-foreground/60 hover:bg-background hover:text-red-600 disabled:opacity-50"
                                    onClick={(event) => {
                                      event.stopPropagation();
                                      void handleDeleteSavedFilter(filter.id);
                                    }}
                                    disabled={deletingSavedFilterId === filter.id}
                                    aria-label={`Delete saved filter ${filter.name}`}
                                  >
                                    <Trash2 className="h-4 w-4" aria-hidden="true" />
                                  </button>
                                </div>
                              ))}
                            </div>
                          )}
                        </div>
                      ) : null}
                    </>
                  ) : null}
                </div>

                {hasSavableFilter ? (
                  <Button
                    type="button"
                    variant="outline"
                    className="gap-2"
                    onClick={() => {
                      setSaveFilterName("");
                      setSaveFilterError(null);
                      setSaveFilterOpen(true);
                    }}
                  >
                    <Bookmark className="h-4 w-4" aria-hidden="true" />
                    <span>Save filter</span>
                  </Button>
                ) : null}
              </div>
            ) : null}

            {hasActiveFilters ? (
              <div className="flex flex-wrap items-center gap-2 border-t border-border pt-3">
                <p className="text-xs text-foreground/60">Active filters:</p>
                {readinessFilter !== "ALL" ? (
                  <button
                    type="button"
                    className="inline-flex items-center gap-1 rounded-full border border-blue-500/30 bg-blue-500/10 px-2.5 py-1 text-xs font-medium text-blue-700 hover:bg-blue-500/20 dark:text-blue-300"
                    onClick={() => setReadinessFilter("ALL")}
                  >
                    {READINESS_FILTER_LABELS[readinessFilter]}
                    <span aria-hidden="true">×</span>
                  </button>
                ) : null}
                {visibilityFilter !== "ALL" ? (
                  <button
                    type="button"
                    className="inline-flex items-center gap-1 rounded-full border border-blue-500/30 bg-blue-500/10 px-2.5 py-1 text-xs font-medium text-blue-700 hover:bg-blue-500/20 dark:text-blue-300"
                    onClick={() => setVisibilityFilter("ALL")}
                  >
                    {VISIBILITY_FILTER_LABELS[visibilityFilter]}
                    <span aria-hidden="true">×</span>
                  </button>
                ) : null}
                {selectedSubject !== ALL_SUBJECTS ? (
                  <button
                    type="button"
                    className="inline-flex items-center gap-1 rounded-full border border-blue-500/30 bg-blue-500/10 px-2.5 py-1 text-xs font-medium text-blue-700 hover:bg-blue-500/20 dark:text-blue-300"
                    onClick={() => setSelectedSubject(ALL_SUBJECTS)}
                  >
                    {selectedSubject}
                    <span aria-hidden="true">×</span>
                  </button>
                ) : null}
                {selectedCourseProgram !== ALL_COURSE_PROGRAMS ? (
                  <button
                    type="button"
                    className="inline-flex items-center gap-1 rounded-full border border-blue-500/30 bg-blue-500/10 px-2.5 py-1 text-xs font-medium text-blue-700 hover:bg-blue-500/20 dark:text-blue-300"
                    onClick={() => setSelectedCourseProgram(ALL_COURSE_PROGRAMS)}
                  >
                    {selectedCourseProgram}
                    <span aria-hidden="true">×</span>
                  </button>
                ) : null}
                {selectedTags.map((tag) => (
                  <button
                    key={`chip-${tag}`}
                    type="button"
                    className="inline-flex items-center gap-1 rounded-full border border-blue-500/30 bg-blue-500/10 px-2.5 py-1 text-xs font-medium text-blue-700 hover:bg-blue-500/20 dark:text-blue-300"
                    onClick={() => setSelectedTags((previous) => previous.filter((t) => t !== tag))}
                  >
                    {tag}
                    <span aria-hidden="true">×</span>
                  </button>
                ))}
                <Button type="button" variant="outline" size="sm" className="h-7" onClick={clearFilters}>
                  Clear all
                </Button>
              </div>
            ) : (
              <p className="border-t border-border pt-3 text-xs text-foreground/50">
                Sorted by {SORT_LABELS[sortBy]}
              </p>
            )}

            {showSubjectStatsStrip ? (
              <div className="flex flex-wrap items-center gap-2 border-t border-border pt-3">
                <p className="mr-1 text-xs font-medium uppercase tracking-wide text-foreground/55">
                  Subjects
                </p>
                {subjectFacets.topSubjects.map((subjectCount) => (
                  <button
                    key={subjectCount.subject}
                    type="button"
                    className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1.5 text-sm font-medium text-foreground/75 transition-colors hover:bg-highlight active:bg-highlight-strong"
                    onClick={() => applySubjectStatsFilter(subjectCount.subject)}
                  >
                    <span>{subjectCount.subject}</span>
                    <span className="text-foreground/50">{subjectCount.count}</span>
                  </button>
                ))}
                {subjectFacets.otherSubjectsCount > 0 ? (
                  <span className="inline-flex items-center gap-1 rounded-full border border-border bg-surface-alt px-3 py-1.5 text-sm font-medium text-foreground/55">
                    Other {subjectFacets.otherSubjectsCount}
                  </span>
                ) : null}
              </div>
            ) : null}
          </Card>

          {visibleItems.length === 0 ? (
            <Card className="space-y-3 p-4 sm:p-6">
              <h2 className="text-base font-semibold sm:text-lg">
                {readinessFilter === "DRAFT" ? "No draft notes" : "No notes match these filters"}
              </h2>
              <p className="text-sm text-foreground/75">
                {readinessFilter === "DRAFT"
                  ? "No draft notes — you've generated Study Packs for everything in your library."
                  : "Try adjusting your filters"}
              </p>
              <div className="flex justify-start">
                <Button type="button" variant="outline" onClick={clearFilters}>
                  Clear filters
                </Button>
              </div>
            </Card>
          ) : (
            <div className="grid gap-4 md:grid-cols-2">
              {visibleItems.map((item) => {
                const itemTags = normalizeTags(item.tags);
                const examReady = canIncludeInExam(item);
                const isSelected = selectedNoteIds.includes(item.id);

                return (
                  <Card
                    key={item.id}
                    role={selectionMode ? "button" : "link"}
                    tabIndex={0}
                    onClick={() => {
                      if (selectionMode) {
                        toggleNoteSelection(item);
                        return;
                      }
                      handleNoteNavigate(item.id);
                    }}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        if (selectionMode) {
                          toggleNoteSelection(item);
                          return;
                        }
                        handleNoteNavigate(item.id);
                      }
                    }}
                    aria-pressed={selectionMode ? isSelected : undefined}
                    className={`flex h-full flex-col justify-between space-y-4 p-4 sm:p-6 ${
                      enteringNoteIds.has(item.id) ? "motion-fade-enter " : ""
                    }${
                      selectionMode
                        ? getSelectionCardClassName({
                            selected: isSelected,
                            className: "h-full rounded-2xl",
                          })
                        : getBrowsingCardClassName("h-full")
                    }`}
                  >
                    <SharedNoteCard
                      title={item.title}
                      courseProgram={normalizeCourseProgram(item.courseProgram)}
                      subject={getLibrarySubject(item)}
                      tags={itemTags}
                      contentPreview={item.contentPreview}
                      summaryPreview={item.summaryPreview}
                      titleTrailing={selectionMode ? (
                        <span className="inline-flex items-center">
                          <input
                            type="checkbox"
                            checked={isSelected}
                            onChange={() => toggleNoteSelection(item)}
                            onClick={(event) => event.stopPropagation()}
                            aria-label={`Select ${item.title?.trim() || "Untitled note"}`}
                            className="h-4 w-4 rounded border-border text-blue-600 focus:ring-2 focus:ring-blue-600"
                          />
                        </span>
                      ) : renderVisibilityIcon(item.visibility)}
                      stateBadge={<NoteStateBadge status={item.studyPackStatus} />}
                      metadataBadges={showQuizReadyIndicators && examReady ? (
                        <span className="rounded-full border border-emerald-500/30 bg-emerald-500/10 px-2.5 py-1 text-xs font-medium text-emerald-700 dark:text-emerald-300">
                          Quiz Ready
                        </span>
                      ) : null}
                      footer={(
                        <div className="space-y-1">
                          <p className="text-xs text-foreground/65">
                            Updated {new Date(item.updatedAt).toLocaleString()}
                          </p>
                          <p className="text-xs text-foreground/65">
                            {item.lastSessionCompletedAt
                              ? `Last reviewed ${formatRelativeReviewTime(item.lastSessionCompletedAt)}`
                              : "Not reviewed yet"}
                          </p>
                        </div>
                      )}
                    />
                  </Card>
                );
              })}
            </div>
          )}

          {hasMore ? (
            <div className="flex justify-center">
              <Button
                type="button"
                variant="outline"
                onClick={() => setVisibleCount((previous) => previous + LIBRARY_PAGE_SIZE)}
                className="w-full sm:w-auto"
              >
                Load more
              </Button>
            </div>
          ) : null}
        </div>
      )}

      <AppModal
        isOpen={saveFilterOpen}
        title="Save filter"
        onClose={() => {
          if (!saveFilterSubmitting) {
            resetSaveFilterDialog();
          }
        }}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              onClick={resetSaveFilterDialog}
              disabled={saveFilterSubmitting}
            >
              Cancel
            </Button>
            <Button type="button" onClick={() => void handleSaveFilter()} disabled={saveFilterSubmitting}>
              {saveFilterSubmitting ? "Saving..." : "Save"}
            </Button>
          </div>
        )}
      >
        <div className="space-y-2">
          <label htmlFor="saved-filter-name" className="text-sm font-medium">
            Filter name
          </label>
          <input
            id="saved-filter-name"
            type="text"
            value={saveFilterName}
            onChange={(event) => {
              setSaveFilterName(event.target.value);
              setSaveFilterError(null);
            }}
            maxLength={SAVE_FILTER_NAME_MAX_LENGTH}
            placeholder="PNLE Notes"
            className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
          />
          {saveFilterError ? (
            <p className="text-sm text-red-600 dark:text-red-300">{saveFilterError}</p>
          ) : null}
        </div>
      </AppModal>

      <LibrarySheetModal
        isOpen={moreFiltersOpen}
        title={MORE_FILTERS_TITLE}
        onClose={() => setMoreFiltersOpen(false)}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={clearFilters}>
              Clear all
            </Button>
            <Button type="button" onClick={() => setMoreFiltersOpen(false)}>
              Apply
            </Button>
          </div>
        )}
      >
        <div className="space-y-6">
          <div className="space-y-3">
            <p className="text-sm font-medium">Status</p>
            <div className="flex flex-wrap gap-2">
              {visibleReadinessFilters.map((filterKey) => (
                <button
                  key={filterKey}
                  type="button"
                  className={getFilterChipClassName(readinessFilter === filterKey)}
                  onClick={() => setReadinessFilter(filterKey)}
                  aria-pressed={readinessFilter === filterKey}
                >
                  {READINESS_FILTER_LABELS[filterKey]}
                </button>
              ))}
            </div>
          </div>

          <div className="space-y-3">
            <p className="text-sm font-medium">Visibility</p>
            <div className="flex flex-wrap gap-2">
              {VISIBILITY_FILTER_KEYS.map((filterKey) => (
                <button
                  key={filterKey}
                  type="button"
                  className={getFilterChipClassName(visibilityFilter === filterKey)}
                  onClick={() => setVisibilityFilter(filterKey)}
                  aria-pressed={visibilityFilter === filterKey}
                >
                  {VISIBILITY_FILTER_LABELS[filterKey]}
                </button>
              ))}
            </div>
          </div>

          {availableSubjects.length > 0 ? (
            <div className="space-y-2">
              <p className="text-sm font-medium">Subjects</p>
              <div className="relative">
                <input
                  type="text"
                  value={subjectComboOpen ? subjectSearchQuery : (selectedSubject !== ALL_SUBJECTS ? selectedSubject : "")}
                  onChange={(event) => { setSubjectSearchQuery(event.target.value); setSubjectComboTyped(true); }}
                  placeholder={subjectComboOpen ? "Search subjects..." : "All"}
                  onFocus={() => { setSubjectComboOpen(true); setSubjectComboTyped(false); setSubjectSearchQuery(selectedSubject !== ALL_SUBJECTS ? selectedSubject : ""); }}
                  onBlur={() => globalThis.setTimeout(() => setSubjectComboOpen(false), 150)}
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 pr-14 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
                />
                {selectedSubject !== ALL_SUBJECTS ? (
                  <button
                    type="button"
                    aria-label="Clear subject filter"
                    onMouseDown={(event) => { event.preventDefault(); setSelectedSubject(ALL_SUBJECTS); setSubjectSearchQuery(""); setSubjectComboTyped(false); setSubjectComboOpen(false); }}
                    className="absolute right-8 top-1/2 flex h-6 w-6 -translate-y-1/2 items-center justify-center text-foreground/45 transition-colors hover:text-foreground"
                  >
                    <X className="h-4 w-4" aria-hidden="true" />
                  </button>
                ) : null}
                <ChevronDown
                  className={`pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-foreground/45 transition-transform duration-200 ${subjectComboOpen ? "rotate-180" : ""}`}
                  aria-hidden="true"
                />
              </div>
              {subjectComboOpen ? (
                <div className="max-h-44 overflow-y-auto rounded-md border border-border shadow-sm">
                  <button
                    type="button"
                    className={getComboboxItemClassName(selectedSubject === ALL_SUBJECTS)}
                    onMouseDown={(event) => { event.preventDefault(); setSelectedSubject(ALL_SUBJECTS); setSubjectSearchQuery(""); setSubjectComboOpen(false); }}
                  >
                    All
                  </button>
                  {filteredModalSubjects.length === 0 ? (
                    <p className="px-3 py-2.5 text-sm text-foreground/65">No subjects match your search.</p>
                  ) : (
                    filteredModalSubjects.map((subject) => (
                      <button
                        key={subject}
                        type="button"
                        className={getComboboxItemClassName(selectedSubject === subject)}
                        onMouseDown={(event) => {
                          event.preventDefault();
                          setSelectedSubject(subject);
                          setRecentSubjects((previous) => updateRecentValues(previous, [subject]));
                          setSubjectSearchQuery("");
                          setSubjectComboOpen(false);
                        }}
                      >
                        {subject}
                      </button>
                    ))
                  )}
                </div>
              ) : null}
            </div>
          ) : null}

          {availableTags.length > 0 ? (
            <div className="space-y-3">
              <div className="flex items-center justify-between gap-2">
                <p className="text-sm font-medium">Popular Tags</p>
                <button
                  type="button"
                  className={TEXT_LINK_CLASS_NAME}
                  onClick={() => setTagSelectorOpen(true)}
                >
                  {BROWSE_ALL_LABEL}
                </button>
              </div>
              <div className="flex flex-wrap gap-2">
                {visiblePopularTags.map((tag) => (
                  <button
                    key={tag}
                    type="button"
                    className={getFilterChipClassName(selectedTags.includes(tag))}
                    onClick={() => {
                      setSelectedTags((previous) => {
                        const next = previous.includes(tag)
                          ? previous.filter((selectedTag) => selectedTag !== tag)
                          : [...previous, tag];
                        if (!previous.includes(tag)) {
                          setRecentTags((recentPrevious) => updateRecentValues(recentPrevious, [tag]));
                        }
                        return next;
                      });
                    }}
                    aria-pressed={selectedTags.includes(tag)}
                  >
                    {tag}
                  </button>
                ))}
              </div>
            </div>
          ) : null}

          {availableCoursePrograms.length > 0 ? (
            <div className="space-y-2">
              <p className="text-sm font-medium">Course / Program</p>
              <div className="relative">
                <input
                  type="text"
                  value={courseProgramComboOpen ? courseProgramSearchQuery : (selectedCourseProgram !== ALL_COURSE_PROGRAMS ? selectedCourseProgram : "")}
                  onChange={(event) => { setCourseProgramSearchQuery(event.target.value); setCourseProgramComboTyped(true); }}
                  placeholder={courseProgramComboOpen ? "Search course or program..." : "All"}
                  onFocus={() => { setCourseProgramComboOpen(true); setCourseProgramComboTyped(false); setCourseProgramSearchQuery(selectedCourseProgram !== ALL_COURSE_PROGRAMS ? selectedCourseProgram : ""); }}
                  onBlur={() => globalThis.setTimeout(() => setCourseProgramComboOpen(false), 150)}
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 pr-14 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
                />
                {selectedCourseProgram !== ALL_COURSE_PROGRAMS ? (
                  <button
                    type="button"
                    aria-label="Clear course/program filter"
                    onMouseDown={(event) => { event.preventDefault(); setSelectedCourseProgram(ALL_COURSE_PROGRAMS); setCourseProgramSearchQuery(""); setCourseProgramComboTyped(false); setCourseProgramComboOpen(false); }}
                    className="absolute right-8 top-1/2 flex h-6 w-6 -translate-y-1/2 items-center justify-center text-foreground/45 transition-colors hover:text-foreground"
                  >
                    <X className="h-4 w-4" aria-hidden="true" />
                  </button>
                ) : null}
                <ChevronDown
                  className={`pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-foreground/45 transition-transform duration-200 ${courseProgramComboOpen ? "rotate-180" : ""}`}
                  aria-hidden="true"
                />
              </div>
              {courseProgramComboOpen ? (
                <div ref={courseProgramDropdownRef} className="max-h-44 overflow-y-auto rounded-md border border-border shadow-sm">
                  <button
                    type="button"
                    className={getComboboxItemClassName(selectedCourseProgram === ALL_COURSE_PROGRAMS)}
                    onMouseDown={(event) => { event.preventDefault(); setSelectedCourseProgram(ALL_COURSE_PROGRAMS); setCourseProgramSearchQuery(""); setCourseProgramComboOpen(false); }}
                  >
                    All
                  </button>
                  {filteredModalCoursePrograms.length === 0 ? (
                    <p className="px-3 py-2.5 text-sm text-foreground/65">No course/programs match your search.</p>
                  ) : (
                    filteredModalCoursePrograms.map((courseProgram) => (
                      <button
                        key={courseProgram}
                        type="button"
                        className={getComboboxItemClassName(selectedCourseProgram === courseProgram)}
                        onMouseDown={(event) => {
                          event.preventDefault();
                          setSelectedCourseProgram(courseProgram);
                          setCourseProgramSearchQuery("");
                          setCourseProgramComboOpen(false);
                        }}
                      >
                        {courseProgram}
                      </button>
                    ))
                  )}
                </div>
              ) : null}
            </div>
          ) : null}
        </div>
      </LibrarySheetModal>

      <LibrarySheetModal
        isOpen={tagSelectorOpen}
        title={TAG_SELECTOR_TITLE}
        onClose={() => setTagSelectorOpen(false)}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={() => setTagDraft([])}>
              Clear
            </Button>
            <Button
              type="button"
              onClick={() => {
                setSelectedTags(tagDraft);
                if (tagDraft.length > 0) {
                  setRecentTags((previous) => updateRecentValues(previous, [...tagDraft].reverse()));
                }
                setTagSelectorOpen(false);
              }}
            >
              Apply
            </Button>
          </div>
        )}
      >
        <div className="sticky top-0 z-10 space-y-3 bg-background pb-3">
          <input
            type="search"
            value={tagSearchQuery}
            onChange={(event) => setTagSearchQuery(event.target.value)}
            placeholder="Search tags..."
            className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
          />
          {tagDraft.length > 0 ? (
            <div className="space-y-2">
              <div className="flex items-center justify-between gap-2">
                <p className="text-xs font-medium uppercase tracking-wide text-foreground/55">Selected tags</p>
                <button
                  type="button"
                  className="text-xs font-medium text-blue-700 hover:text-blue-800 dark:text-blue-300 dark:hover:text-blue-200"
                  onClick={() => setTagDraft([])}
                >
                  Clear all
                </button>
              </div>
              <div className="flex flex-wrap gap-2">
                {tagDraft.map((tag) => (
                  <button
                    key={`selected-${tag}`}
                    type="button"
                    className={getFilterChipClassName(true)}
                    onClick={() => toggleDraftTag(tag)}
                  >
                    {tag}
                  </button>
                ))}
              </div>
            </div>
          ) : null}
        </div>
        {filteredModalTags.length === 0 ? (
          <p className="text-sm text-foreground/65">No tags match your search.</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {filteredModalTags.map((tag) => (
              <button
                key={tag}
                type="button"
                className={getFilterChipClassName(tagDraft.includes(tag))}
                onClick={() => toggleDraftTag(tag)}
                aria-pressed={tagDraft.includes(tag)}
              >
                {tag}
              </button>
            ))}
          </div>
        )}
      </LibrarySheetModal>

      <LibrarySheetModal
        isOpen={sortSheetOpen}
        title="Sort notes"
        onClose={() => setSortSheetOpen(false)}
      >
        <div className="space-y-2">
          {(Object.entries(SORT_LABELS) as Array<[LibrarySortOption, string]>).map(([value, label]) => {
            const isSelected = sortBy === value;
            return (
              <button
                key={value}
                type="button"
                className={`w-full rounded-lg border px-3 py-3 text-left text-sm transition-colors ${
                  isSelected
                    ? "border-blue-600 bg-blue-50 text-blue-700 dark:border-blue-400 dark:bg-blue-950/40 dark:text-blue-200"
                    : "border-border bg-background hover:bg-highlight"
                }`}
                onClick={() => {
                  setSortBy(value);
                  setSortSheetOpen(false);
                }}
              >
                {label}
              </button>
            );
          })}
        </div>
      </LibrarySheetModal>

      <CreatePlanModal
        isOpen={createPlanOpen}
        selectedNoteIds={selectedNoteIds}
        singularLabel={collectionLabels.singular}
        onClose={() => setCreatePlanOpen(false)}
        onCreated={handlePlanCreated}
      />

      {toast ? (
        <div role="status" aria-live="polite" className="fixed bottom-4 right-4 z-50 rounded-md border border-border bg-background px-3 py-2 text-sm shadow-sm">
          {typeof toast === "string" ? toast : (
            <span className="inline-flex items-center gap-2">
              <span>{toast.message}</span>
              <Link href={toast.href} className="font-medium text-blue-600 hover:underline dark:text-blue-400">
                {toast.linkLabel}
              </Link>
            </span>
          )}
        </div>
      ) : null}
    </main>
  );
}
