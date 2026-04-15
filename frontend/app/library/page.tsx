"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowUpDown, Globe, Lock } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { SharedNoteCard } from "@/components/notes/shared-note-card";
import { PageHeader } from "@/components/page-header";
import { LibrarySheetModal } from "@/components/notes/library-sheet-modal";
import { NoteStateBadge } from "@/components/notes/note-state-badge";
import { ResponsiveActionButton, ResponsiveActionLink } from "@/components/ui/action-button";
import {
  getQuickReviewPerformanceSummary,
  listNotes,
  listSubjects,
  type NoteListItemResponse,
  type NoteVisibility,
} from "@/lib/api";
import { normalizeCourseProgram } from "@/lib/learning-profile";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { normalizeSubject } from "@/lib/subjects";

type LibrarySortOption =
  | "RECENTLY_UPDATED"
  | "RECENTLY_REVIEWED"
  | "NEWEST"
  | "TITLE_ASC"
  | "TITLE_DESC"
  | "OLDEST";

type ReviewSummaryMeta = {
  lastReviewedAt: string | null;
};

const LIBRARY_PAGE_SIZE = 20;
const ALL_SUBJECTS = "__ALL_SUBJECTS__";
const SUBJECT_FALLBACK = "General";
const POPULAR_TAG_LIMIT = 5;
const MORE_TAGS_LABEL = "+ More";
const MORE_SUBJECTS_LABEL = "+ More";
const TAG_SELECTOR_TITLE = "Select tags";
const SUBJECT_SELECTOR_TITLE = "Select subject";
const SORT_LABELS: Record<LibrarySortOption, string> = {
  RECENTLY_UPDATED: "Recently Updated",
  RECENTLY_REVIEWED: "Recently Reviewed",
  NEWEST: "Newest",
  TITLE_ASC: "Title (A-Z)",
  TITLE_DESC: "Title (Z-A)",
  OLDEST: "Oldest",
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

function getFilterChipClassName(isSelected: boolean) {
  return `shrink-0 rounded-full border px-3 py-1.5 text-sm font-medium transition-colors ${
    isSelected
      ? "border-blue-600 bg-blue-600 text-white dark:border-blue-400 dark:bg-blue-500 dark:text-slate-950"
      : "border-border bg-background text-foreground/75 hover:bg-highlight"
  }`;
}

function getScrollRailClassName() {
  return "flex flex-nowrap gap-2 overflow-x-auto pb-1 [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden";
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

export default function LibraryPage() {
  const router = useRouter();
  const initialLoadStartedRef = useRef(false);
  const [items, setItems] = useState<NoteListItemResponse[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedSubject, setSelectedSubject] = useState<string>(ALL_SUBJECTS);
  const [subjectDraft, setSubjectDraft] = useState<string>(ALL_SUBJECTS);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [tagDraft, setTagDraft] = useState<string[]>([]);
  const [sortBy, setSortBy] = useState<LibrarySortOption>("RECENTLY_UPDATED");
  const [reviewSummaryByNoteId, setReviewSummaryByNoteId] = useState<Record<string, ReviewSummaryMeta>>({});
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [subjectSuggestions, setSubjectSuggestions] = useState<string[]>([]);
  const [visibleCount, setVisibleCount] = useState(LIBRARY_PAGE_SIZE);
  const [sortSheetOpen, setSortSheetOpen] = useState(false);
  const [tagSelectorOpen, setTagSelectorOpen] = useState(false);
  const [subjectSelectorOpen, setSubjectSelectorOpen] = useState(false);
  const [tagSearchQuery, setTagSearchQuery] = useState("");
  const [subjectSearchQuery, setSubjectSearchQuery] = useState("");
  const [recentTags, setRecentTags] = useState<string[]>([]);
  const [recentSubjects, setRecentSubjects] = useState<string[]>([]);

  const hydrateLastReviewed = useCallback(async (notes: NoteListItemResponse[]) => {
    if (notes.length === 0) {
      setReviewSummaryByNoteId({});
      return;
    }

    const entries = await Promise.all(
      notes.map(async (note) => {
        if (!note.studyPackId) {
          return [note.id, { lastReviewedAt: null }] as const;
        }
        try {
          const summary = await getQuickReviewPerformanceSummary(note.id);
          return [note.id, { lastReviewedAt: summary.lastReviewedAt }] as const;
        } catch {
          return [note.id, { lastReviewedAt: null }] as const;
        }
      }),
    );

    setReviewSummaryByNoteId(Object.fromEntries(entries));
  }, []);

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
      void hydrateLastReviewed(notes);
    } catch (loadError) {
      const message = loadError instanceof Error ? loadError.message : "Could not load your notes.";
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [hydrateLastReviewed, router]);

  useEffect(() => {
    if (initialLoadStartedRef.current) {
      return;
    }
    initialLoadStartedRef.current = true;
    void loadLibrary();
  }, [loadLibrary]);

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

  const availableTags = useMemo(() => {
    return Array.from(tagCounts.keys());
  }, [tagCounts]);

  useEffect(() => {
    if (selectedSubject !== ALL_SUBJECTS && !availableSubjects.includes(selectedSubject)) {
      setSelectedSubject(ALL_SUBJECTS);
    }
    if (subjectDraft !== ALL_SUBJECTS && !availableSubjects.includes(subjectDraft)) {
      setSubjectDraft(ALL_SUBJECTS);
    }
  }, [availableSubjects, selectedSubject, subjectDraft]);

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
    if (subjectSelectorOpen) {
      setSubjectDraft(selectedSubject);
      setSubjectSearchQuery("");
    }
  }, [selectedSubject, subjectSelectorOpen]);

  useEffect(() => {
    setVisibleCount(LIBRARY_PAGE_SIZE);
  }, [searchQuery, selectedSubject, selectedTags, sortBy]);

  const clearFilters = useCallback(() => {
    setSearchQuery("");
    setSelectedSubject(ALL_SUBJECTS);
    setSubjectDraft(ALL_SUBJECTS);
    setSelectedTags([]);
    setTagDraft([]);
    setSubjectSearchQuery("");
    setTagSearchQuery("");
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

  const visiblePopularTags = useMemo(() => {
    const ordered = [
      ...selectedTags,
      ...displayedTags.filter((tag) => !selectedTags.includes(tag)),
    ];
    const deduped = Array.from(new Set(ordered));
    return deduped.slice(0, Math.max(POPULAR_TAG_LIMIT, selectedTags.length));
  }, [displayedTags, selectedTags]);

  const remainingTagCount = useMemo(() => {
    const visible = new Set(visiblePopularTags);
    return displayedTags.filter((tag) => !visible.has(tag)).length;
  }, [displayedTags, visiblePopularTags]);

  const hasActiveFilters = searchQuery.trim().length > 0
    || selectedSubject !== ALL_SUBJECTS
    || selectedTags.length > 0;

  const sortedFilteredItems = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    const filtered = items.filter((item) => {
      const itemTitle = item.title?.trim() || "Untitled note";
      const itemTags = normalizeTags(item.tags);
      const itemSubject = getLibrarySubject(item);

      const searchMatch = query.length === 0
        || itemTitle.toLowerCase().includes(query)
        || itemTags.some((tag) => tag.toLowerCase().includes(query));
      const subjectMatch = selectedSubject === ALL_SUBJECTS || itemSubject === selectedSubject;
      const tagMatch = selectedTags.length === 0 || selectedTags.some((selectedTag) => itemTags.includes(selectedTag));

      return searchMatch && subjectMatch && tagMatch;
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
            reviewSummaryByNoteId[left.id]?.lastReviewedAt,
            reviewSummaryByNoteId[right.id]?.lastReviewedAt,
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
  }, [items, reviewSummaryByNoteId, searchQuery, selectedSubject, selectedTags, sortBy]);

  const visibleItems = useMemo(
    () => sortedFilteredItems.slice(0, visibleCount),
    [sortedFilteredItems, visibleCount],
  );
  const hasMore = visibleCount < sortedFilteredItems.length;

  return (
    <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <PageHeader
        eyebrow="LIBRARY"
        title="Library"
        description="Browse and revisit all of your saved notes."
        actions={(
          <ResponsiveActionLink href="/notes/new" action="create" label="Create Note" className="block w-full sm:w-auto" />
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
          <h2 className="text-xl font-semibold">You don&apos;t have any notes yet.</h2>
          <p className="text-sm text-foreground/75">
            Save your first note to start building Study Packs.
          </p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <ResponsiveActionLink href="/notes/new" action="create" label="Create Your First Note" className="w-full sm:w-auto" />
            <ResponsiveActionLink href="/demo" action="open" label="Try Demo" variant="outline" className="w-full sm:w-auto" />
          </div>
        </Card>
      ) : (
        <div className="space-y-4">
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

            <div className="space-y-2">
              <div className="flex items-center justify-between gap-3">
                <p className="text-sm font-medium">Subjects</p>
                {selectedSubject !== ALL_SUBJECTS ? (
                  <button
                    type="button"
                    className="shrink-0 text-xs font-medium text-blue-700 hover:text-blue-800 dark:text-blue-300 dark:hover:text-blue-200"
                    onClick={() => setSelectedSubject(ALL_SUBJECTS)}
                  >
                    Reset
                  </button>
                ) : null}
              </div>
              <div className={getScrollRailClassName()}>
                <button
                  type="button"
                  className={getFilterChipClassName(selectedSubject === ALL_SUBJECTS)}
                  onClick={() => setSelectedSubject(ALL_SUBJECTS)}
                  aria-pressed={selectedSubject === ALL_SUBJECTS}
                >
                  All
                </button>
                {displayedSubjects.map((subject) => (
                  <button
                    key={subject}
                    type="button"
                    className={getFilterChipClassName(selectedSubject === subject)}
                    onClick={() => {
                      setSelectedSubject(subject);
                      setRecentSubjects((previous) => updateRecentValues(previous, [subject]));
                    }}
                    aria-pressed={selectedSubject === subject}
                  >
                    {subject}
                  </button>
                ))}
                <button
                  type="button"
                  className={getFilterChipClassName(false)}
                  onClick={() => setSubjectSelectorOpen(true)}
                >
                  {MORE_SUBJECTS_LABEL}
                </button>
              </div>
            </div>

            {availableTags.length > 0 ? (
              <div className="space-y-2">
                <div className="flex items-center justify-between gap-3">
                  <p className="text-sm font-medium">Popular Tags</p>
                  {selectedTags.length > 0 ? (
                    <button
                      type="button"
                      className="shrink-0 text-xs font-medium text-blue-700 hover:text-blue-800 dark:text-blue-300 dark:hover:text-blue-200"
                      onClick={() => {
                        setSelectedTags([]);
                        setTagDraft([]);
                      }}
                    >
                      Clear tags
                    </button>
                  ) : null}
                </div>
                <div className={getScrollRailClassName()}>
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
                  {remainingTagCount > 0 ? (
                    <button
                      type="button"
                      className={getFilterChipClassName(false)}
                      onClick={() => setTagSelectorOpen(true)}
                    >
                      {MORE_TAGS_LABEL}
                    </button>
                  ) : null}
                </div>
              </div>
            ) : null}

            <div className="flex flex-wrap items-center gap-2 border-t border-border pt-3">
              <p className="text-xs text-foreground/60">Sorted by {SORT_LABELS[sortBy]}</p>
              {selectedSubject !== ALL_SUBJECTS ? (
                <span className="rounded-full border border-border bg-background px-2.5 py-1 text-xs text-foreground/70">
                  Subject: {selectedSubject}
                </span>
              ) : null}
              {selectedTags.length > 0 ? (
                <span className="rounded-full border border-border bg-background px-2.5 py-1 text-xs text-foreground/70">
                  {selectedTags.length} tag{selectedTags.length === 1 ? "" : "s"} selected
                </span>
              ) : null}
              {hasActiveFilters ? (
                <Button type="button" variant="outline" size="sm" className="h-8" onClick={clearFilters}>
                  Clear filters
                </Button>
              ) : null}
            </div>
          </Card>

          {visibleItems.length === 0 ? (
            <Card className="space-y-3 p-4 sm:p-6">
              <h2 className="text-base font-semibold sm:text-lg">No study packs found</h2>
              <p className="text-sm text-foreground/75">Try adjusting your filters</p>
              <div className="flex justify-start">
                <Button type="button" variant="outline" onClick={clearFilters}>
                  Clear filters
                </Button>
              </div>
            </Card>
          ) : (
            <div className="grid gap-4 md:grid-cols-2">
              {visibleItems.map((item) => {
                const reviewSummary = reviewSummaryByNoteId[item.id] ?? { lastReviewedAt: null };
                const itemTags = normalizeTags(item.tags);

                return (
                  <Card
                    key={item.id}
                    role="link"
                    tabIndex={0}
                    onClick={() => router.push(`/notes/${item.id}?from=library`)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        router.push(`/notes/${item.id}?from=library`);
                      }
                    }}
                    className="flex h-full cursor-pointer flex-col justify-between space-y-4 p-4 transition-colors hover:bg-highlight hover:shadow-md sm:p-6"
                  >
                    <SharedNoteCard
                      title={item.title}
                      courseProgram={normalizeCourseProgram(item.courseProgram)}
                      subject={getLibrarySubject(item)}
                      tags={itemTags}
                      contentPreview={item.contentPreview}
                      summaryPreview={item.summaryPreview}
                      titleTrailing={renderVisibilityIcon(item.visibility)}
                      stateBadge={<NoteStateBadge status={item.studyPackStatus} />}
                      footer={(
                        <div className="space-y-1">
                          <p className="text-xs text-foreground/65">
                            Updated {new Date(item.updatedAt).toLocaleString()}
                          </p>
                          <p className="text-xs text-foreground/65">
                            {reviewSummary.lastReviewedAt
                              ? `Last reviewed ${formatRelativeReviewTime(reviewSummary.lastReviewedAt)}`
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
        isOpen={subjectSelectorOpen}
        title={SUBJECT_SELECTOR_TITLE}
        onClose={() => setSubjectSelectorOpen(false)}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={() => setSubjectDraft(ALL_SUBJECTS)}>
              Clear
            </Button>
            <Button
              type="button"
              onClick={() => {
                setSelectedSubject(subjectDraft);
                if (subjectDraft !== ALL_SUBJECTS) {
                  setRecentSubjects((previous) => updateRecentValues(previous, [subjectDraft]));
                }
                setSubjectSelectorOpen(false);
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
            value={subjectSearchQuery}
            onChange={(event) => setSubjectSearchQuery(event.target.value)}
            placeholder="Search subjects..."
            className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
          />
          {subjectDraft !== ALL_SUBJECTS ? (
            <div className="space-y-2">
              <p className="text-xs font-medium uppercase tracking-wide text-foreground/55">Selected subject</p>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  className={getFilterChipClassName(true)}
                  onClick={() => setSubjectDraft(ALL_SUBJECTS)}
                >
                  {subjectDraft}
                </button>
              </div>
            </div>
          ) : null}
        </div>
        {filteredModalSubjects.length === 0 ? (
          <p className="text-sm text-foreground/65">No subjects match your search.</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              className={getFilterChipClassName(subjectDraft === ALL_SUBJECTS)}
              onClick={() => setSubjectDraft(ALL_SUBJECTS)}
              aria-pressed={subjectDraft === ALL_SUBJECTS}
            >
              All
            </button>
            {filteredModalSubjects.map((subject) => (
              <button
                key={subject}
                type="button"
                className={getFilterChipClassName(subjectDraft === subject)}
                onClick={() => setSubjectDraft(subject)}
                aria-pressed={subjectDraft === subject}
              >
                {subject}
              </button>
            ))}
          </div>
        )}
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
    </main>
  );
}
