"use client";

import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {useRouter} from "next/navigation";
import {
  ArrowUpDown,
  CheckSquare,
  ChevronDown,
  Filter,
  Globe,
  Lock,
  Square,
} from "lucide-react";
import {Button} from "@/components/ui/button";
import {Card} from "@/components/ui/card";
import {SharedNoteCard} from "@/components/notes/shared-note-card";
import {PageHeader} from "@/components/page-header";
import {LibrarySheetModal} from "@/components/notes/library-sheet-modal";
import {NoteStateBadge} from "@/components/notes/note-state-badge";
import {ResponsiveActionButton, ResponsiveActionLink} from "@/components/ui/action-button";
import {getAuthUser} from "@/lib/auth";
import {
  listNotes,
  listSubjects,
  type NoteListItemResponse,
  type NoteVisibility,
} from "@/lib/api";
import {getBrowsingCardClassName, getSelectionCardClassName} from "@/lib/clickable-card";
import {normalizeCourseProgram} from "@/lib/learning-profile";
import {shouldShowQuizReadyIndicator} from "@/lib/profile-mode";
import {requireAuthenticatedOnboardedUser} from "@/lib/route-guards";
import {normalizeSubject} from "@/lib/subjects";
import {GuidanceTip} from "@/components/ui/guidance-tip";

type LibrarySortOption =
  | "RECENTLY_UPDATED"
  | "RECENTLY_REVIEWED"
  | "NEWEST"
  | "TITLE_ASC"
  | "TITLE_DESC"
  | "OLDEST";

type LibraryReadinessFilter = "ALL" | "DRAFT" | "QUIZ_READY" | "STUDY_PACK_READY";

const LIBRARY_PAGE_SIZE = 20;
const ALL_SUBJECTS = "__ALL_SUBJECTS__";
const ALL_COURSE_PROGRAMS = "__ALL_COURSE_PROGRAMS__";
const SUBJECT_FALLBACK = "General";
const POPULAR_TAG_LIMIT = 5;
const COURSE_PROGRAM_LIMIT = 10;
const BROWSE_ALL_LABEL = "Browse all";
const TAG_SELECTOR_TITLE = "Select tags";
const MORE_FILTERS_TITLE = "More Filters";
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

export default function LibraryPage() {
  const router = useRouter();
  const initialLoadStartedRef = useRef(false);
  const [items, setItems] = useState<NoteListItemResponse[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedSubject, setSelectedSubject] = useState<string>(ALL_SUBJECTS);
  const [selectedCourseProgram, setSelectedCourseProgram] = useState<string>(ALL_COURSE_PROGRAMS);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [tagDraft, setTagDraft] = useState<string[]>([]);
  const [sortBy, setSortBy] = useState<LibrarySortOption>("RECENTLY_UPDATED");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [subjectSuggestions, setSubjectSuggestions] = useState<string[]>([]);
  const [visibleCount, setVisibleCount] = useState(LIBRARY_PAGE_SIZE);
  const [sortSheetOpen, setSortSheetOpen] = useState(false);
  const [moreFiltersOpen, setMoreFiltersOpen] = useState(false);
  const [tagSelectorOpen, setTagSelectorOpen] = useState(false);
  const [subjectComboOpen, setSubjectComboOpen] = useState(false);
  const [courseProgramComboOpen, setCourseProgramComboOpen] = useState(false);
  const [tagSearchQuery, setTagSearchQuery] = useState("");
  const [subjectSearchQuery, setSubjectSearchQuery] = useState("");
  const [courseProgramSearchQuery, setCourseProgramSearchQuery] = useState("");
  const [recentTags, setRecentTags] = useState<string[]>([]);
  const [recentSubjects, setRecentSubjects] = useState<string[]>([]);
  const [readinessFilter, setReadinessFilter] = useState<LibraryReadinessFilter>("ALL");
  const [selectionMode, setSelectionMode] = useState(false);
  const [selectedNoteIds, setSelectedNoteIds] = useState<string[]>([]);
  const [toast, setToast] = useState<string | null>(null);

  const authUser = getAuthUser();
  const isTeacherExamBuilderEnabled = authUser?.profileType === "TEACHER";
  const isTeacherProfile = authUser?.profileType === "TEACHER";
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

  useEffect(() => {
    if (initialLoadStartedRef.current) {
      return;
    }
    initialLoadStartedRef.current = true;
    void loadLibrary();
  }, [loadLibrary]);

  useEffect(() => {
    if (!toast) {
      return undefined;
    }
    const timeoutId = globalThis.setTimeout(() => setToast(null), 2200);
    return () => globalThis.clearTimeout(timeoutId);
  }, [toast]);

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
    if (selectedSubject !== ALL_SUBJECTS && !availableSubjects.includes(selectedSubject)) {
      setSelectedSubject(ALL_SUBJECTS);
    }
  }, [availableSubjects, selectedSubject]);

  useEffect(() => {
    if (selectedCourseProgram !== ALL_COURSE_PROGRAMS && !availableCoursePrograms.includes(selectedCourseProgram)) {
      setSelectedCourseProgram(ALL_COURSE_PROGRAMS);
    }
  }, [availableCoursePrograms, selectedCourseProgram]);

  useEffect(() => {
    setSelectedTags((previous) => previous.filter((tag) => availableTags.includes(tag)));
    setTagDraft((previous) => previous.filter((tag) => availableTags.includes(tag)));
  }, [availableTags]);

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
    setVisibleCount(LIBRARY_PAGE_SIZE);
  }, [readinessFilter, searchQuery, selectedCourseProgram, selectedSubject, selectedTags, sortBy]);

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
    const query = subjectSearchQuery.trim().toLowerCase();
    return displayedSubjects.filter((subject) => (
      query.length === 0 || subject.toLowerCase().includes(query)
    ));
  }, [displayedSubjects, subjectSearchQuery]);

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
    const query = courseProgramSearchQuery.trim().toLowerCase();
    const source = query.length === 0
      ? availableCoursePrograms.slice(0, COURSE_PROGRAM_LIMIT)
      : availableCoursePrograms;
    return source.filter((courseProgram) => (
      query.length === 0 || courseProgram.toLowerCase().includes(query)
    ));
  }, [availableCoursePrograms, courseProgramSearchQuery]);

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
    || selectedCourseProgram !== ALL_COURSE_PROGRAMS
    || selectedSubject !== ALL_SUBJECTS
    || selectedTags.length > 0;

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

      return searchMatch && readinessMatch && courseProgramMatch && subjectMatch && tagMatch;
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
  }, [items, readinessFilter, searchQuery, selectedCourseProgram, selectedSubject, selectedTags, showQuizReadyIndicators, sortBy]);

  const visibleItems = useMemo(
    () => sortedFilteredItems.slice(0, visibleCount),
    [sortedFilteredItems, visibleCount],
  );
  const hasMore = visibleCount < sortedFilteredItems.length;

  const resetSelectionMode = useCallback(() => {
    setSelectionMode(false);
    setSelectedNoteIds([]);
  }, []);

  const toggleNoteSelection = useCallback((item: NoteListItemResponse) => {
    if (!canIncludeInExam(item)) {
      setToast("Generate a quiz for this note before adding it to an exam.");
      return;
    }
    setSelectedNoteIds((previous) => (
      previous.includes(item.id)
        ? previous.filter((noteId) => noteId !== item.id)
        : [...previous, item.id]
    ));
  }, []);

  const openExamBuilder = useCallback(() => {
    if (selectedNoteIds.length === 0) {
      return;
    }
    const params = new URLSearchParams({
      notes: selectedNoteIds.join(","),
    });
    router.push(`/library/exam-builder?${params.toString()}`);
  }, [router, selectedNoteIds]);

  return (
    <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <PageHeader
        eyebrow="LIBRARY"
        title="Library"
        description="Browse and revisit all of your saved notes."
        actions={(
          <div className="flex w-full flex-col gap-2 sm:w-auto sm:flex-row">
            {isTeacherExamBuilderEnabled ? (
              <Button
                type="button"
                variant={selectionMode ? "default" : "outline"}
                className="gap-2"
                onClick={() => {
                  if (selectionMode) {
                    resetSelectionMode();
                    return;
                  }
                  setSelectionMode(true);
                }}
              >
                {selectionMode ? <CheckSquare className="h-4 w-4" aria-hidden="true" /> : <Square className="h-4 w-4" aria-hidden="true" />}
                <span>Select</span>
              </Button>
            ) : null}
            <ResponsiveActionLink href="/notes/new" action="create" label="Create Note" className="block w-full sm:w-auto" />
          </div>
        )}
      />

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
          {items.length >= 1 && items.length <= 3 ? (
            <GuidanceTip
              tipId="library-first-note-organization"
              message="Add a subject and tags when editing a note — it makes filtering your library much easier as it grows."
            />
          ) : null}
          {items.length >= 5 ? (
            <GuidanceTip
              tipId="library-organization-habits"
              message="You're building a solid library. Try filtering by subject to find related notes quickly."
            />
          ) : null}
          {isTeacherProfile && !selectionMode && items.length >= 1 ? (
            <GuidanceTip
              tipId="teacher-library-multi-note-select"
              message="Select multiple notes with the checkboxes, then use 'Generate Quiz' from the toolbar."
            />
          ) : null}
          {selectionMode ? (
            <Card className="space-y-3 p-4 sm:p-5">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="space-y-1">
                  <h2 className="text-lg font-semibold">{selectedNoteIds.length} note{selectedNoteIds.length === 1 ? "" : "s"} selected</h2>
                  <p className="text-sm text-foreground/70">
                    Select quiz-ready notes to build one combined exam. Notes without generated quizzes stay disabled.
                  </p>
                </div>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <Button type="button" variant="outline" onClick={resetSelectionMode}>
                    Cancel
                  </Button>
                  <Button
                    type="button"
                    onClick={openExamBuilder}
                    disabled={selectedNoteIds.length === 0}
                  >
                    Create Exam
                  </Button>
                </div>
              </div>
            </Card>
          ) : null}

          <Card className="space-y-4 p-4 sm:p-5">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
              <div className="min-w-0 flex-1 space-y-2">
                <label htmlFor="library-search" className="text-sm font-medium">
                  Search
                </label>
                <input
                  id="library-search"
                  type="search"
                  value={searchQuery}
                  onChange={(event) => setSearchQuery(event.target.value)}
                  placeholder="Search titles and tags..."
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
                />
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
                      router.push(`/notes/${item.id}?from=library`);
                    }}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        if (selectionMode) {
                          toggleNoteSelection(item);
                          return;
                        }
                        router.push(`/notes/${item.id}?from=library`);
                      }
                    }}
                    aria-pressed={selectionMode ? isSelected : undefined}
                    className={`flex h-full flex-col justify-between space-y-4 p-4 sm:p-6 ${
                      selectionMode
                        ? examReady
                          ? getSelectionCardClassName({
                              selected: isSelected,
                              className: "h-full rounded-2xl",
                            })
                          : "cursor-not-allowed opacity-70"
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
                            disabled={!examReady}
                            aria-label={`Select ${item.title?.trim() || "Untitled note"} for exam export`}
                            className="h-4 w-4 rounded border-border text-blue-600 focus:ring-2 focus:ring-blue-600 disabled:cursor-not-allowed"
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
                          {selectionMode && !examReady ? (
                            <p className="text-xs font-medium text-amber-700 dark:text-amber-300">
                              Generate a quiz first to include this note in an exam.
                            </p>
                          ) : null}
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
              Done
            </Button>
          </div>
        )}
      >
        <div className="space-y-6">
          <div className="space-y-3">
            <p className="text-sm font-medium">Filter</p>
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

          {availableSubjects.length > 0 ? (
            <div className="space-y-2">
              <p className="text-sm font-medium">Subjects</p>
              <div className="relative">
                <input
                  type="text"
                  value={subjectComboOpen ? subjectSearchQuery : (selectedSubject !== ALL_SUBJECTS ? selectedSubject : "")}
                  onChange={(event) => setSubjectSearchQuery(event.target.value)}
                  placeholder={subjectComboOpen ? "Search subjects..." : "All"}
                  onFocus={() => { setSubjectComboOpen(true); setSubjectSearchQuery(""); }}
                  onBlur={() => globalThis.setTimeout(() => setSubjectComboOpen(false), 150)}
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 pr-8 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
                />
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
                  onChange={(event) => setCourseProgramSearchQuery(event.target.value)}
                  placeholder={courseProgramComboOpen ? "Search course or program..." : "All"}
                  onFocus={() => { setCourseProgramComboOpen(true); setCourseProgramSearchQuery(""); }}
                  onBlur={() => globalThis.setTimeout(() => setCourseProgramComboOpen(false), 150)}
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 pr-8 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
                />
                <ChevronDown
                  className={`pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-foreground/45 transition-transform duration-200 ${courseProgramComboOpen ? "rotate-180" : ""}`}
                  aria-hidden="true"
                />
              </div>
              {courseProgramComboOpen ? (
                <div className="max-h-44 overflow-y-auto rounded-md border border-border shadow-sm">
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

      {toast ? (
        <div role="status" aria-live="polite" className="fixed bottom-4 right-4 z-50 rounded-md border border-border bg-background px-3 py-2 text-sm shadow-sm">
          {toast}
        </div>
      ) : null}
    </main>
  );
}
