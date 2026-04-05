"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { SharedNoteCard } from "@/components/notes/shared-note-card";
import { PageHeader } from "@/components/page-header";
import { LibraryToolbar } from "@/components/notes/library-toolbar";
import { LibrarySheetModal } from "@/components/notes/library-sheet-modal";
import { NoteStateBadge } from "@/components/notes/note-state-badge";
import { ResponsiveActionButton } from "@/components/ui/action-button";
import { getAuthUser } from "@/lib/auth";
import {
  type LearnerLevel,
  listPublicNotes,
  listSubjects,
  type NoteListItemResponse,
} from "@/lib/api";
import {
  formatLearnerLevel,
  mergeCourseProgramSuggestions,
  normalizeCourseProgram,
} from "@/lib/learning-profile";
import { resolvePublicNoteAuthorMeta } from "@/lib/public-note-author";
import { buildPublicLibraryNotePath, buildPublicProfilePath } from "@/lib/public-note-path";
import { normalizeSubject } from "@/lib/subjects";

const ALL_COURSE_PROGRAMS = "__ALL_COURSE_PROGRAMS__";
const ALL_SUBJECTS = "__ALL_SUBJECTS__";
const ALL_LEARNER_LEVELS = "__ALL_LEARNER_LEVELS__";

type PublicLibrarySortOption =
  | "NEWEST"
  | "MOST_COPIED"
  | "MOST_VIEWED"
  | "TITLE_ASC";

type PublicLibrarySourceFilter = "BY_YOU" | "OFFICIAL" | "COMMUNITY";

const PUBLIC_SORT_LABELS: Record<PublicLibrarySortOption, string> = {
  NEWEST: "Newest",
  MOST_COPIED: "Most Copied",
  MOST_VIEWED: "Most Viewed",
  TITLE_ASC: "Title A-Z",
};

function normalizeTags(tags: string[] | null | undefined): string[] {
  if (!Array.isArray(tags)) {
    return [];
  }
  return tags
    .map((tag) => tag?.trim())
    .filter((tag): tag is string => Boolean(tag && tag.length > 0));
}

function countActivePublicFilterGroups({
  courseProgram,
  learnerLevel,
  subject,
  tags,
  sourceFilters,
}: {
  courseProgram: string;
  learnerLevel: string;
  subject: string;
  tags: string[];
  sourceFilters: PublicLibrarySourceFilter[];
}) {
  return [
    courseProgram !== ALL_COURSE_PROGRAMS,
    learnerLevel !== ALL_LEARNER_LEVELS,
    subject !== ALL_SUBJECTS,
    tags.length > 0,
    sourceFilters.length > 0,
  ].filter(Boolean).length;
}

function resolveAuthorBadge(
  item: Pick<NoteListItemResponse, "ownerUserId" | "authorDisplayName" | "isCurrentUser" | "isOfficialAuthor">,
  currentUserId: string | null,
) {
  const authorMeta = resolvePublicNoteAuthorMeta({
    ownerUserId: item.ownerUserId,
    currentUserId,
    authorDisplayName: item.authorDisplayName,
    isCurrentUser: item.isCurrentUser,
    isOfficialAuthor: item.isOfficialAuthor,
  });

  if (authorMeta.label === "By You") {
    return {
      label: authorMeta.label,
      className: "text-emerald-700 dark:text-emerald-300",
      showOfficialBadge: false,
    };
  }
  if (authorMeta.showOfficialBadge) {
    return {
      label: authorMeta.label,
      className: "text-blue-700 dark:text-blue-300",
      showOfficialBadge: true,
    };
  }
  return {
    label: authorMeta.label,
    className: "text-foreground/75",
    showOfficialBadge: false,
  };
}

export function PublicLibraryPageClient() {
  const router = useRouter();
  const [currentUserId, setCurrentUserId] = useState<string | null>(() => getAuthUser()?.id ?? null);
  const [items, setItems] = useState<NoteListItemResponse[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedCourseProgram, setSelectedCourseProgram] = useState<string>(ALL_COURSE_PROGRAMS);
  const [selectedLearnerLevel, setSelectedLearnerLevel] = useState<string>(ALL_LEARNER_LEVELS);
  const [selectedSubject, setSelectedSubject] = useState<string>(ALL_SUBJECTS);
  const [selectedSort, setSelectedSort] = useState<PublicLibrarySortOption>("NEWEST");
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [selectedSourceFilters, setSelectedSourceFilters] = useState<PublicLibrarySourceFilter[]>([]);
  const [tagSearchQuery, setTagSearchQuery] = useState("");
  const [subjectSuggestions, setSubjectSuggestions] = useState<string[]>([]);
  const [filterSheetOpen, setFilterSheetOpen] = useState(false);
  const [sortSheetOpen, setSortSheetOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadNotes = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [notesResult, subjectsResult] = await Promise.allSettled([
        listPublicNotes(),
        listSubjects("public"),
      ]);
      if (notesResult.status !== "fulfilled") {
        throw notesResult.reason;
      }
      setItems(notesResult.value);
      setSubjectSuggestions(subjectsResult.status === "fulfilled" ? subjectsResult.value : []);
    } catch (loadError) {
      const message = loadError instanceof Error ? loadError.message : "Could not load public notes.";
      setError(message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadNotes();
  }, [loadNotes]);

  useEffect(() => {
    const syncAuth = () => {
      setCurrentUserId(getAuthUser()?.id ?? null);
    };

    syncAuth();
    globalThis.addEventListener("studysnap-auth-change", syncAuth);
    return () => {
      globalThis.removeEventListener("studysnap-auth-change", syncAuth);
    };
  }, []);

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

  const availableLearnerLevels = useMemo(() => {
    const learnerLevels = new Set<LearnerLevel>();
    for (const item of items) {
      if (item.learnerLevel) {
        learnerLevels.add(item.learnerLevel);
      }
    }
    return Array.from(learnerLevels).sort((left, right) => formatLearnerLevel(left)?.localeCompare(formatLearnerLevel(right) ?? "") ?? 0);
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
    if (selectedLearnerLevel !== ALL_LEARNER_LEVELS && !availableLearnerLevels.includes(selectedLearnerLevel as LearnerLevel)) {
      setSelectedLearnerLevel(ALL_LEARNER_LEVELS);
    }
  }, [availableLearnerLevels, selectedLearnerLevel]);

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

  const toggleSourceFilter = useCallback((filter: PublicLibrarySourceFilter) => {
    setSelectedSourceFilters((previous) => (
      previous.includes(filter)
        ? previous.filter((selectedFilter) => selectedFilter !== filter)
        : [...previous, filter]
    ));
  }, []);

  const clearFilters = useCallback(() => {
    setSearchQuery("");
    setSelectedCourseProgram(ALL_COURSE_PROGRAMS);
    setSelectedLearnerLevel(ALL_LEARNER_LEVELS);
    setSelectedSubject(ALL_SUBJECTS);
    setSelectedTags([]);
    setSelectedSourceFilters([]);
    setTagSearchQuery("");
  }, []);

  const activeFilterCount = countActivePublicFilterGroups({
    courseProgram: selectedCourseProgram,
    learnerLevel: selectedLearnerLevel,
    subject: selectedSubject,
    tags: selectedTags,
    sourceFilters: selectedSourceFilters,
  });

  const hasActiveFilters = searchQuery.trim().length > 0
    || selectedCourseProgram !== ALL_COURSE_PROGRAMS
    || selectedLearnerLevel !== ALL_LEARNER_LEVELS
    || selectedSubject !== ALL_SUBJECTS
    || selectedTags.length > 0
    || selectedSourceFilters.length > 0;

  const filteredItems = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    const selectedCourseProgramLookup = selectedCourseProgram === ALL_COURSE_PROGRAMS
      ? null
      : normalizeCourseProgram(selectedCourseProgram)?.toLowerCase() ?? null;
    return items.filter((item) => {
      const title = item.title?.trim() || "Untitled note";
      const tags = normalizeTags(item.tags);
      const courseProgram = normalizeCourseProgram(item.courseProgram);
      const courseProgramLookup = courseProgram?.toLowerCase() ?? null;
      const learnerLevelLabel = formatLearnerLevel(item.learnerLevel);
      const normalizedSubject = normalizeSubject(item.subject);
      const titleMatch = query.length === 0
        || title.toLowerCase().includes(query)
        || (courseProgram?.toLowerCase().includes(query) ?? false)
        || (learnerLevelLabel?.toLowerCase().includes(query) ?? false)
        || (normalizedSubject?.toLowerCase().includes(query) ?? false)
        || item.contentPreview.toLowerCase().includes(query)
        || tags.some((tag) => tag.toLowerCase().includes(query));
      const courseProgramMatch = selectedCourseProgramLookup === null
        || courseProgramLookup === selectedCourseProgramLookup;
      const learnerLevelMatch = selectedLearnerLevel === ALL_LEARNER_LEVELS
        || item.learnerLevel === selectedLearnerLevel;
      const subjectMatch = selectedSubject === ALL_SUBJECTS
        || normalizedSubject === selectedSubject;
      const tagMatch = selectedTags.length === 0
        || selectedTags.some((selectedTag) => tags.includes(selectedTag));
      const sourceMatch = selectedSourceFilters.length === 0
        || selectedSourceFilters.some((filter) => (
          filter === "BY_YOU"
            ? item.ownerUserId === currentUserId || item.isCurrentUser
            : filter === "OFFICIAL"
              ? item.isOfficialAuthor
              : !(item.ownerUserId === currentUserId || item.isCurrentUser || item.isOfficialAuthor)
        ));

      return titleMatch && courseProgramMatch && learnerLevelMatch && subjectMatch && tagMatch && sourceMatch;
    });
  }, [currentUserId, items, searchQuery, selectedCourseProgram, selectedLearnerLevel, selectedSourceFilters, selectedSubject, selectedTags]);

  const sortedItems = useMemo(() => {
    const byNewest = (left: NoteListItemResponse, right: NoteListItemResponse) => (
      new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime()
    );
    const metricValue = (
      item: NoteListItemResponse,
      key: "copyCount" | "viewCount",
    ) => item[key] ?? 0;

    return [...filteredItems].sort((left, right) => {
      switch (selectedSort) {
        case "TITLE_ASC":
          return (left.title ?? "Untitled note").localeCompare(right.title ?? "Untitled note");
        case "MOST_COPIED": {
          const primaryDelta = metricValue(right, "copyCount") - metricValue(left, "copyCount");
          if (primaryDelta !== 0) {
            return primaryDelta;
          }
          return byNewest(left, right);
        }
        case "MOST_VIEWED": {
          const primaryDelta = metricValue(right, "viewCount") - metricValue(left, "viewCount");
          if (primaryDelta !== 0) {
            return primaryDelta;
          }
          return byNewest(left, right);
        }
        case "NEWEST":
        default:
          return byNewest(left, right);
      }
    });
  }, [filteredItems, selectedSort]);

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

      {selectedLearnerLevel !== ALL_LEARNER_LEVELS ? (
        <span className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1 text-xs">
          Level: {formatLearnerLevel(selectedLearnerLevel)}
          <button
            type="button"
            className="text-foreground/65 hover:text-foreground"
            onClick={() => setSelectedLearnerLevel(ALL_LEARNER_LEVELS)}
            aria-label="Clear learner level filter"
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

      {selectedSourceFilters.map((filter) => (
        <span key={filter} className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1 text-xs">
          {filter === "BY_YOU" ? "By You" : filter === "OFFICIAL" ? "Official" : "Community"}
          <button
            type="button"
            className="text-foreground/65 hover:text-foreground"
            onClick={() => setSelectedSourceFilters((previous) => previous.filter((value) => value !== filter))}
            aria-label={`Remove ${filter === "BY_YOU" ? "By You" : filter === "OFFICIAL" ? "Official" : "Community"} filter`}
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
        title="Public Library"
        description="Explore public notes from you, the community, and official NoteLib examples. Copy a note into your library when you want to study it in your own workspace."
        brandLogo
      />

      {loading ? (
        <div className="grid gap-4 md:grid-cols-2">
          {Array.from({ length: 4 }).map((_, index) => (
            <Card key={`public-library-loading-${index}`} className="space-y-3 p-4 sm:p-6">
              <div className="h-5 w-2/3 animate-pulse rounded bg-foreground/10" />
              <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
              <div className="h-4 w-1/2 animate-pulse rounded bg-foreground/10" />
            </Card>
          ))}
        </div>
      ) : error ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h2 className="text-xl font-semibold">Could not load public notes</h2>
          <p className="text-sm text-foreground/75">{error}</p>
          <ResponsiveActionButton type="button" variant="outline" onClick={() => void loadNotes()} action="retry" label="Retry" />
        </Card>
      ) : (
        <div className="space-y-4">
          <LibraryToolbar
            searchId="public-library-search"
            searchPlaceholder="Search public notes..."
            searchValue={searchQuery}
            onSearchValueChange={setSearchQuery}
            onOpenFilters={() => setFilterSheetOpen(true)}
            onOpenSort={() => setSortSheetOpen(true)}
            activeFilterCount={activeFilterCount}
            sortSummaryLabel={PUBLIC_SORT_LABELS[selectedSort]}
            activeFilterSummary={activeFilterSummary}
          />

          {sortedItems.length === 0 ? (
            <Card className="space-y-3 p-4 sm:p-6">
              <h2 className="text-base font-semibold sm:text-lg">No public notes match your filters.</h2>
              <p className="text-sm text-foreground/75">Try adjusting search or filters.</p>
              <Button type="button" variant="outline" onClick={clearFilters} className="w-full sm:w-auto">
                Clear filters
              </Button>
            </Card>
          ) : (
            <div className="grid gap-4 md:grid-cols-2">
              {sortedItems.map((item) => {
                const itemTags = normalizeTags(item.tags);
                const authorBadge = resolveAuthorBadge(item, currentUserId);

                return (
                  <Card
                    key={item.id}
                    role="link"
                    tabIndex={0}
                    onClick={() => router.push(buildPublicLibraryNotePath({ subject: item.subject, title: item.title }))}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        router.push(buildPublicLibraryNotePath({ subject: item.subject, title: item.title }));
                      }
                    }}
                    className="flex h-full cursor-pointer flex-col justify-between space-y-4 p-4 transition-colors hover:bg-muted/40 hover:shadow-md sm:p-6"
                  >
                    <SharedNoteCard
                      title={item.title}
                      metaLine={normalizeCourseProgram(item.courseProgram)}
                      subject={item.subject}
                      tags={itemTags}
                      contentPreview={item.contentPreview}
                      summaryPreview={item.summaryPreview}
                      copyCount={typeof item.copyCount === "number" && item.copyCount > 0 ? item.copyCount : null}
                      viewCount={typeof item.viewCount === "number" && item.viewCount > 0 ? item.viewCount : null}
                      metadataBadges={<NoteStateBadge status={item.studyPackStatus} />}
                      footer={(
                        <div className="flex flex-wrap items-center gap-2 text-xs text-foreground/65">
                          {item.ownerUserId ? (
                            <Link
                              href={buildPublicProfilePath(item.ownerUserId)}
                              onClick={(event) => event.stopPropagation()}
                              onKeyDown={(event) => event.stopPropagation()}
                              className={`font-medium hover:underline ${authorBadge.className}`}
                            >
                              {authorBadge.label}
                            </Link>
                          ) : (
                            <span className={authorBadge.className}>{authorBadge.label}</span>
                          )}
                          {authorBadge.showOfficialBadge ? (
                            <span className="inline-flex items-center rounded-full border border-blue-500/35 bg-blue-500/10 px-2 py-1 text-[11px] font-medium text-blue-700 dark:text-blue-300">
                              Official
                            </span>
                          ) : null}
                        </div>
                      )}
                    />
                  </Card>
                );
              })}
            </div>
          )}
        </div>
      )}

      <LibrarySheetModal
        isOpen={filterSheetOpen}
        title="Filter public notes"
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
          <label htmlFor="public-library-filter-course-program" className="text-sm font-medium">
            Course / Program
          </label>
          <select
            id="public-library-filter-course-program"
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
          <label htmlFor="public-library-filter-learner-level" className="text-sm font-medium">
            Learner Level
          </label>
          <select
            id="public-library-filter-learner-level"
            value={selectedLearnerLevel}
            onChange={(event) => setSelectedLearnerLevel(event.target.value)}
            className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus:ring-2 focus:ring-blue-600"
          >
            <option value={ALL_LEARNER_LEVELS}>All learner levels</option>
            {availableLearnerLevels.map((learnerLevel) => (
              <option key={learnerLevel} value={learnerLevel}>
                {formatLearnerLevel(learnerLevel)}
              </option>
            ))}
          </select>
        </div>

        <div className="space-y-2">
          <label htmlFor="public-library-filter-subject" className="text-sm font-medium">
            Subject
          </label>
          <select
            id="public-library-filter-subject"
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
          <p className="text-sm font-medium">Source</p>
          <div className="space-y-1 rounded-lg border border-border p-2">
            {([
              { value: "BY_YOU" as const, label: "By You" },
              { value: "OFFICIAL" as const, label: "Official" },
              { value: "COMMUNITY" as const, label: "Community" },
            ]).map((option) => (
              <label key={option.value} className="flex cursor-pointer items-center gap-2 rounded px-2 py-1 text-sm hover:bg-muted/50">
                <input
                  type="checkbox"
                  checked={selectedSourceFilters.includes(option.value)}
                  onChange={() => toggleSourceFilter(option.value)}
                  className="h-4 w-4 rounded border-border"
                />
                <span>{option.label}</span>
              </label>
            ))}
          </div>
        </div>
      </LibrarySheetModal>

      <LibrarySheetModal
        isOpen={sortSheetOpen}
        title="Sort public notes"
        onClose={() => setSortSheetOpen(false)}
      >
        <div className="space-y-2">
          {(Object.entries(PUBLIC_SORT_LABELS) as Array<[PublicLibrarySortOption, string]>).map(([value, label]) => {
            const isSelected = selectedSort === value;
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
                  setSelectedSort(value);
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
