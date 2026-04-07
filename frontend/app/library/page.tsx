"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Globe, Lock } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { SharedNoteCard } from "@/components/notes/shared-note-card";
import { PageHeader } from "@/components/page-header";
import { LibraryToolbar } from "@/components/notes/library-toolbar";
import { LibrarySheetModal } from "@/components/notes/library-sheet-modal";
import { NoteStateBadge } from "@/components/notes/note-state-badge";
import { ResponsiveActionButton, ResponsiveActionLink } from "@/components/ui/action-button";
import {
  getQuickReviewPerformanceSummary,
  listNotes,
  listSubjects,
  type NoteListItemResponse,
  type NoteStudyPackStatus,
  type NoteVisibility,
} from "@/lib/api";
import { mergeCourseProgramSuggestions, normalizeCourseProgram } from "@/lib/learning-profile";
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
const ALL_COURSE_PROGRAMS = "__ALL_COURSE_PROGRAMS__";
const ALL_SUBJECTS = "__ALL_SUBJECTS__";
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

function countActiveFilterGroups({
  courseProgram,
  subject,
  tags,
  statuses,
  visibilities,
}: {
  courseProgram: string;
  subject: string;
  tags: string[];
  statuses: NoteStudyPackStatus[];
  visibilities: NoteVisibility[];
}) {
  return [
    courseProgram !== ALL_COURSE_PROGRAMS,
    subject !== ALL_SUBJECTS,
    tags.length > 0,
    statuses.length > 0,
    visibilities.length > 0,
  ].filter(Boolean).length;
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
  const [selectedCourseProgram, setSelectedCourseProgram] = useState<string>(ALL_COURSE_PROGRAMS);
  const [selectedSubject, setSelectedSubject] = useState<string>(ALL_SUBJECTS);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [selectedStatuses, setSelectedStatuses] = useState<NoteStudyPackStatus[]>([]);
  const [selectedVisibilities, setSelectedVisibilities] = useState<NoteVisibility[]>([]);
  const [tagSearchQuery, setTagSearchQuery] = useState("");
  const [sortBy, setSortBy] = useState<LibrarySortOption>("RECENTLY_UPDATED");
  const [reviewSummaryByNoteId, setReviewSummaryByNoteId] = useState<Record<string, ReviewSummaryMeta>>({});
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [subjectSuggestions, setSubjectSuggestions] = useState<string[]>([]);
  const [visibleCount, setVisibleCount] = useState(LIBRARY_PAGE_SIZE);
  const [filterSheetOpen, setFilterSheetOpen] = useState(false);
  const [sortSheetOpen, setSortSheetOpen] = useState(false);

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
      const subject = normalizeSubject(item.subject);
      if (subject) {
        subjectSet.add(subject);
      }
    }
    return Array.from(subjectSet).sort((left, right) => left.localeCompare(right));
  }, [items]);

  const availableSubjects = subjectSuggestions.length > 0 ? subjectSuggestions : derivedSubjects;

  const availableCoursePrograms = useMemo(() => {
    return mergeCourseProgramSuggestions(items.map((item) => item.courseProgram));
  }, [items]);

  const availableTags = useMemo(() => {
    const tagSet = new Set<string>();
    for (const item of items) {
      for (const tag of normalizeTags(item.tags)) {
        tagSet.add(tag);
      }
    }
    return Array.from(tagSet).sort((left, right) => left.localeCompare(right));
  }, [items]);

  const visibleTagOptions = useMemo(() => {
    const query = tagSearchQuery.trim().toLowerCase();
    if (query.length === 0) {
      return availableTags;
    }
    return availableTags.filter((tag) => tag.toLowerCase().includes(query));
  }, [availableTags, tagSearchQuery]);

  useEffect(() => {
    if (selectedCourseProgram !== ALL_COURSE_PROGRAMS && !availableCoursePrograms.includes(selectedCourseProgram)) {
      setSelectedCourseProgram(ALL_COURSE_PROGRAMS);
    }
  }, [availableCoursePrograms, selectedCourseProgram]);

  useEffect(() => {
    if (selectedSubject !== ALL_SUBJECTS && !availableSubjects.includes(selectedSubject)) {
      setSelectedSubject(ALL_SUBJECTS);
    }
  }, [availableSubjects, selectedSubject]);

  useEffect(() => {
    setSelectedTags((previous) => previous.filter((tag) => availableTags.includes(tag)));
  }, [availableTags]);

  const toggleTag = useCallback((tag: string) => {
    setSelectedTags((previous) => (
      previous.includes(tag)
        ? previous.filter((selectedTag) => selectedTag !== tag)
        : [...previous, tag]
    ));
  }, []);

  const toggleStatus = useCallback((status: NoteStudyPackStatus) => {
    setSelectedStatuses((previous) => (
      previous.includes(status)
        ? previous.filter((selectedStatus) => selectedStatus !== status)
        : [...previous, status]
    ));
  }, []);

  const toggleVisibility = useCallback((visibility: NoteVisibility) => {
    setSelectedVisibilities((previous) => (
      previous.includes(visibility)
        ? previous.filter((selectedVisibility) => selectedVisibility !== visibility)
        : [...previous, visibility]
    ));
  }, []);

  const clearFilters = useCallback(() => {
    setSearchQuery("");
    setSelectedCourseProgram(ALL_COURSE_PROGRAMS);
    setSelectedSubject(ALL_SUBJECTS);
    setSelectedTags([]);
    setSelectedStatuses([]);
    setSelectedVisibilities([]);
    setTagSearchQuery("");
  }, []);

  const hasActiveFilters = searchQuery.trim().length > 0
    || selectedCourseProgram !== ALL_COURSE_PROGRAMS
    || selectedSubject !== ALL_SUBJECTS
    || selectedTags.length > 0
    || selectedStatuses.length > 0
    || selectedVisibilities.length > 0;

  const activeFilterCount = countActiveFilterGroups({
    courseProgram: selectedCourseProgram,
    subject: selectedSubject,
    tags: selectedTags,
    statuses: selectedStatuses,
    visibilities: selectedVisibilities,
  });

  const sortedFilteredItems = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    const selectedCourseProgramLookup = selectedCourseProgram === ALL_COURSE_PROGRAMS
      ? null
      : normalizeCourseProgram(selectedCourseProgram)?.toLowerCase() ?? null;
    const filtered = items.filter((item) => {
      const itemTitle = item.title?.trim() || "Untitled note";
      const itemTags = normalizeTags(item.tags);
      const itemCourseProgram = normalizeCourseProgram(item.courseProgram);
      const itemCourseProgramLookup = itemCourseProgram?.toLowerCase() ?? null;
      const itemSubject = normalizeSubject(item.subject);

      const titleMatch = query.length === 0
        || itemTitle.toLowerCase().includes(query)
        || (itemCourseProgram?.toLowerCase().includes(query) ?? false)
        || (itemSubject?.toLowerCase().includes(query) ?? false)
        || itemTags.some((tag) => tag.toLowerCase().includes(query))
        || item.contentPreview.toLowerCase().includes(query);
      const courseProgramMatch = selectedCourseProgramLookup === null
        || itemCourseProgramLookup === selectedCourseProgramLookup;
      const subjectMatch = selectedSubject === ALL_SUBJECTS
        || itemSubject === selectedSubject;
      const tagMatch = selectedTags.length === 0
        || selectedTags.some((selectedTag) => itemTags.includes(selectedTag));
      const statusMatch = selectedStatuses.length === 0 || selectedStatuses.includes(item.studyPackStatus);
      const visibilityMatch = selectedVisibilities.length === 0 || selectedVisibilities.includes(item.visibility);

      return titleMatch && courseProgramMatch && subjectMatch && tagMatch && statusMatch && visibilityMatch;
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
  }, [
    items,
    reviewSummaryByNoteId,
    searchQuery,
    selectedCourseProgram,
    selectedSubject,
    selectedTags,
    selectedStatuses,
    selectedVisibilities,
    sortBy,
  ]);

  const visibleItems = useMemo(
    () => sortedFilteredItems.slice(0, visibleCount),
    [sortedFilteredItems, visibleCount],
  );
  const hasMore = visibleCount < sortedFilteredItems.length;

  const activeFilterSummary = hasActiveFilters ? (
    <div className="flex flex-wrap items-center gap-2">
      {selectedCourseProgram !== ALL_COURSE_PROGRAMS ? (
        <span className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1 text-xs">
          Course: {selectedCourseProgram}
          <button
            type="button"
            className="text-foreground/65 hover:text-foreground"
            onClick={() => setSelectedCourseProgram(ALL_COURSE_PROGRAMS)}
            aria-label="Clear course program filter"
          >
            x
          </button>
        </span>
      ) : null}

      {selectedSubject !== ALL_SUBJECTS ? (
        <span className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1 text-xs">
          Subject: {selectedSubject}
          <button
            type="button"
            className="text-foreground/65 hover:text-foreground"
            onClick={() => setSelectedSubject(ALL_SUBJECTS)}
            aria-label="Clear subject filter"
          >
            x
          </button>
        </span>
      ) : null}

      {selectedStatuses.map((status) => (
        <span key={status} className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1 text-xs">
          {status === "STUDY_PACK_READY" ? "Study Pack Ready" : "Draft"}
          <button
            type="button"
            className="text-foreground/65 hover:text-foreground"
            onClick={() => setSelectedStatuses((previous) => previous.filter((value) => value !== status))}
            aria-label={`Remove ${status === "STUDY_PACK_READY" ? "Study Pack Ready" : "Draft"} filter`}
          >
            x
          </button>
        </span>
      ))}

      {selectedVisibilities.map((visibility) => (
        <span key={visibility} className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1 text-xs">
          {formatVisibilityLabel(visibility)}
          <button
            type="button"
            className="text-foreground/65 hover:text-foreground"
            onClick={() => setSelectedVisibilities((previous) => previous.filter((value) => value !== visibility))}
            aria-label={`Remove ${formatVisibilityLabel(visibility)} filter`}
          >
            x
          </button>
        </span>
      ))}

      {selectedTags.map((tag) => (
        <span key={tag} className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1 text-xs">
          {tag}
          <button
            type="button"
            className="text-foreground/65 hover:text-foreground"
            onClick={() => setSelectedTags((previous) => previous.filter((value) => value !== tag))}
            aria-label={`Remove tag filter ${tag}`}
          >
            x
          </button>
        </span>
      ))}

      <Button type="button" variant="outline" size="sm" className="h-8" onClick={clearFilters}>
        Clear all
      </Button>
    </div>
  ) : null;

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
          <LibraryToolbar
            searchId="library-search"
            searchPlaceholder="Search notes..."
            searchValue={searchQuery}
            onSearchValueChange={setSearchQuery}
            onOpenFilters={() => setFilterSheetOpen(true)}
            onOpenSort={() => setSortSheetOpen(true)}
            activeFilterCount={activeFilterCount}
            sortSummaryLabel={SORT_LABELS[sortBy]}
            activeFilterSummary={activeFilterSummary}
          />

          {visibleItems.length === 0 ? (
            <Card className="space-y-3 p-4 sm:p-6">
              <h2 className="text-base font-semibold sm:text-lg">
                No notes match your current filters.
              </h2>
              <p className="text-sm text-foreground/75">
                Try adjusting your search or filters.
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
                    className="flex h-full cursor-pointer flex-col justify-between space-y-4 p-4 transition-colors hover:bg-muted/40 hover:shadow-md sm:p-6"
                  >
                    <SharedNoteCard
                      title={item.title}
                      courseProgram={normalizeCourseProgram(item.courseProgram)}
                      subject={item.subject}
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
        isOpen={filterSheetOpen}
        title="Filter notes"
        onClose={() => setFilterSheetOpen(false)}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={clearFilters}>
              Clear filters
            </Button>
            <Button type="button" onClick={() => setFilterSheetOpen(false)}>
              Done
            </Button>
          </div>
        )}
      >
        <div className="space-y-2">
          <label htmlFor="library-filter-course-program" className="text-sm font-medium">
            Course / Program
          </label>
          <select
            id="library-filter-course-program"
            value={selectedCourseProgram}
            onChange={(event) => setSelectedCourseProgram(event.target.value)}
            className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus:ring-2 focus:ring-blue-600"
          >
            <option value={ALL_COURSE_PROGRAMS}>All course/programs</option>
            {availableCoursePrograms.map((courseProgram) => (
              <option key={courseProgram} value={courseProgram}>
                {courseProgram}
              </option>
            ))}
          </select>
        </div>

        <div className="space-y-2">
          <label htmlFor="library-filter-subject" className="text-sm font-medium">
            Subject
          </label>
          <select
            id="library-filter-subject"
            value={selectedSubject}
            onChange={(event) => setSelectedSubject(event.target.value)}
            className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus:ring-2 focus:ring-blue-600"
          >
            <option value={ALL_SUBJECTS}>All subjects</option>
            {availableSubjects.map((subject) => (
              <option key={subject} value={subject}>
                {subject}
              </option>
            ))}
          </select>
        </div>

        <div className="space-y-2">
          <p className="text-sm font-medium">Tags</p>
          <input
            type="search"
            value={tagSearchQuery}
            onChange={(event) => setTagSearchQuery(event.target.value)}
            placeholder="Search tags..."
            className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
          />
          {availableTags.length === 0 ? (
            <p className="text-sm text-foreground/65">No tags available yet.</p>
          ) : visibleTagOptions.length === 0 ? (
            <p className="text-sm text-foreground/65">No tags match your search.</p>
          ) : (
            <div className="max-h-44 space-y-1 overflow-y-auto rounded-lg border border-border p-2">
              {visibleTagOptions.map((tag) => (
                <label key={tag} className="flex cursor-pointer items-center gap-2 rounded px-2 py-1 text-sm hover:bg-muted/50">
                  <input
                    type="checkbox"
                    checked={selectedTags.includes(tag)}
                    onChange={() => toggleTag(tag)}
                    className="h-4 w-4 rounded border-border"
                  />
                  <span>{tag}</span>
                </label>
              ))}
            </div>
          )}
        </div>

        <div className="space-y-2">
          <p className="text-sm font-medium">Status</p>
          <div className="space-y-1 rounded-lg border border-border p-2">
            {(["STUDY_PACK_READY", "DRAFT"] as const).map((status) => (
              <label key={status} className="flex cursor-pointer items-center gap-2 rounded px-2 py-1 text-sm hover:bg-muted/50">
                <input
                  type="checkbox"
                  checked={selectedStatuses.includes(status)}
                  onChange={() => toggleStatus(status)}
                  className="h-4 w-4 rounded border-border"
                />
                <span>{status === "STUDY_PACK_READY" ? "Study Pack Ready" : "Draft"}</span>
              </label>
            ))}
          </div>
        </div>

        <div className="space-y-2">
          <p className="text-sm font-medium">Visibility</p>
          <div className="space-y-1 rounded-lg border border-border p-2">
            {(["PRIVATE", "PUBLIC"] as const).map((visibility) => (
              <label key={visibility} className="flex cursor-pointer items-center gap-2 rounded px-2 py-1 text-sm hover:bg-muted/50">
                <input
                  type="checkbox"
                  checked={selectedVisibilities.includes(visibility)}
                  onChange={() => toggleVisibility(visibility)}
                  className="h-4 w-4 rounded border-border"
                />
                <span>{formatVisibilityLabel(visibility)}</span>
              </label>
            ))}
          </div>
        </div>
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
                    : "border-border bg-background hover:bg-muted/50"
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
