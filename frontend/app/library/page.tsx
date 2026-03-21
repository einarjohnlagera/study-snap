"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";
import {
  copyNote,
  deleteNote,
  getQuickReviewPerformanceSummary,
  listNotes,
  type NoteListItemResponse,
} from "@/lib/api";
import { requireVerifiedOnboardedUser } from "@/lib/route-guards";

type LibrarySortOption = "RECENTLY_UPDATED" | "RECENTLY_REVIEWED" | "TITLE";
const LIBRARY_PAGE_SIZE = 20;
const ALL_SUBJECTS = "__ALL_SUBJECTS__";

type ReviewSummaryMeta = {
  lastReviewedAt: string | null;
};

function normalizeSubject(subject: string | null | undefined): string | null {
  const value = subject?.trim();
  return value && value.length > 0 ? value : null;
}

function normalizeTags(tags: string[] | null | undefined): string[] {
  if (!Array.isArray(tags)) {
    return [];
  }
  return tags
    .map((tag) => tag?.trim())
    .filter((tag): tag is string => Boolean(tag && tag.length > 0));
}

function toPreview(contentPreview: string, maxLength = 160) {
  const clean = contentPreview.trim();
  if (clean.length <= maxLength) {
    return clean;
  }
  return `${clean.slice(0, maxLength - 3)}...`;
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

function getNoteStateMeta(status: NoteListItemResponse["studyPackStatus"]) {
  if (status === "STUDY_PACK_READY") {
    return {
      label: "Study Pack Ready",
      className: "border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300",
    };
  }
  return {
    label: "Draft",
    className: "border-border bg-muted/50 text-foreground/70",
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
  const [items, setItems] = useState<NoteListItemResponse[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedSubject, setSelectedSubject] = useState<string>(ALL_SUBJECTS);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [tagSearchQuery, setTagSearchQuery] = useState("");
  const [sortBy, setSortBy] = useState<LibrarySortOption>("RECENTLY_UPDATED");
  const [reviewSummaryByNoteId, setReviewSummaryByNoteId] = useState<Record<string, ReviewSummaryMeta>>({});
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [visibleCount, setVisibleCount] = useState(LIBRARY_PAGE_SIZE);
  const [tagFilterOpen, setTagFilterOpen] = useState(false);
  const [cardMenuOpenId, setCardMenuOpenId] = useState<string | null>(null);
  const [copyingNoteId, setCopyingNoteId] = useState<string | null>(null);
  const [deletingNoteId, setDeletingNoteId] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

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
          return [note.id, {
            lastReviewedAt: summary.lastReviewedAt,
          }] as const;
        } catch {
          return [note.id, { lastReviewedAt: null }] as const;
        }
      }),
    );

    setReviewSummaryByNoteId(Object.fromEntries(entries));
  }, []);

  const loadLibrary = useCallback(async () => {
    if (!requireVerifiedOnboardedUser(router)) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const notes = await listNotes();
      setItems(notes);
      setVisibleCount(LIBRARY_PAGE_SIZE);
      void hydrateLastReviewed(notes);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load your notes.";
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [hydrateLastReviewed, router]);

  useEffect(() => {
    void loadLibrary();
  }, [loadLibrary]);

  useEffect(() => {
    if (!toast) {
      return;
    }
    const timeout = window.setTimeout(() => setToast(null), 2400);
    return () => window.clearTimeout(timeout);
  }, [toast]);

  useEffect(() => {
    if (!cardMenuOpenId) {
      return;
    }
    const closeMenu = (event: MouseEvent) => {
      const target = event.target as HTMLElement;
      if (target.closest("[data-card-menu='true']")) {
        return;
      }
      setCardMenuOpenId(null);
    };
    window.addEventListener("mousedown", closeMenu);
    return () => window.removeEventListener("mousedown", closeMenu);
  }, [cardMenuOpenId]);

  useEffect(() => {
    if (!tagFilterOpen && tagSearchQuery.length > 0) {
      setTagSearchQuery("");
    }
  }, [tagFilterOpen, tagSearchQuery]);

  const hasItems = items.length > 0;
  const availableSubjects = useMemo(() => {
    const subjectSet = new Set<string>();
    items.forEach((item) => {
      const subject = normalizeSubject(item.subject);
      if (subject) {
        subjectSet.add(subject);
      }
    });
    return Array.from(subjectSet).sort((left, right) => left.localeCompare(right));
  }, [items]);

  const availableTags = useMemo(() => {
    const tagSet = new Set<string>();
    items.forEach((item) => {
      normalizeTags(item.tags).forEach((tag) => tagSet.add(tag));
    });
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
    if (selectedSubject === ALL_SUBJECTS) {
      return;
    }
    if (!availableSubjects.includes(selectedSubject)) {
      setSelectedSubject(ALL_SUBJECTS);
    }
  }, [availableSubjects, selectedSubject]);

  useEffect(() => {
    setSelectedTags((previous) => previous.filter((tag) => availableTags.includes(tag)));
  }, [availableTags]);

  const hasActiveFilters = searchQuery.trim().length > 0
    || selectedSubject !== ALL_SUBJECTS
    || selectedTags.length > 0;

  const toggleTag = useCallback((tag: string) => {
    setSelectedTags((previous) => (
      previous.includes(tag)
        ? previous.filter((selectedTag) => selectedTag !== tag)
        : [...previous, tag]
    ));
  }, []);

  const clearFilters = useCallback(() => {
    setSearchQuery("");
    setSelectedSubject(ALL_SUBJECTS);
    setSelectedTags([]);
  }, []);

  const handleMakeCopy = useCallback(async (noteId: string) => {
    if (copyingNoteId || deletingNoteId) {
      return;
    }
    setCopyingNoteId(noteId);
    setCardMenuOpenId(null);
    try {
      const copied = await copyNote(noteId);
      router.push(`/notes/${copied.id}?copied=1`);
    } catch (copyError) {
      const message = copyError instanceof Error ? copyError.message : "Could not copy note.";
      setError(message);
    } finally {
      setCopyingNoteId(null);
    }
  }, [copyingNoteId, deletingNoteId, router]);

  const handleDelete = useCallback(async (noteId: string) => {
    if (copyingNoteId || deletingNoteId) {
      return;
    }
    const confirmed = window.confirm("Delete this note? This will remove its generated Study Pack data too.");
    if (!confirmed) {
      return;
    }
    setDeletingNoteId(noteId);
    setCardMenuOpenId(null);
    try {
      await deleteNote(noteId);
      setItems((previous) => previous.filter((item) => item.id !== noteId));
      setReviewSummaryByNoteId((previous) => {
        const next = { ...previous };
        delete next[noteId];
        return next;
      });
      setToast("Note deleted.");
    } catch (deleteError) {
      const message = deleteError instanceof Error ? deleteError.message : "Could not delete note.";
      setError(message);
    } finally {
      setDeletingNoteId(null);
    }
  }, [copyingNoteId, deletingNoteId]);

  const sortedFilteredItems = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    const filtered = items.filter((item) => {
      const itemTitle = item.title?.trim() || "Untitled note";
      const itemTags = normalizeTags(item.tags);
      const titleMatch = query.length === 0
        || itemTitle.toLowerCase().includes(query)
        || itemTags.some((tag) => tag.toLowerCase().includes(query))
        || item.contentPreview.toLowerCase().includes(query);
      const subjectMatch = selectedSubject === ALL_SUBJECTS
        || normalizeSubject(item.subject) === selectedSubject;
      const tagMatch = selectedTags.length === 0
        || selectedTags.some((selectedTag) => itemTags.includes(selectedTag));
      return titleMatch && subjectMatch && tagMatch;
    });

    const byDateDesc = (leftDate: string | null | undefined, rightDate: string | null | undefined) => {
      const leftTime = leftDate ? new Date(leftDate).getTime() : 0;
      const rightTime = rightDate ? new Date(rightDate).getTime() : 0;
      return rightTime - leftTime;
    };

    return [...filtered].sort((left, right) => {
      if (sortBy === "TITLE") {
        return (left.title ?? "Untitled note").localeCompare(right.title ?? "Untitled note");
      }
      if (sortBy === "RECENTLY_REVIEWED") {
        const reviewedDiff = byDateDesc(
          reviewSummaryByNoteId[left.id]?.lastReviewedAt,
          reviewSummaryByNoteId[right.id]?.lastReviewedAt,
        );
        if (reviewedDiff !== 0) {
          return reviewedDiff;
        }
      }
      return byDateDesc(left.updatedAt, right.updatedAt);
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
        title="My Library"
        description="Browse and revisit all of your saved notes."
      />

      {loading ? (
        <LibraryLoading />
      ) : error ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h2 className="text-xl font-semibold">Could not load notes</h2>
          <p className="text-sm text-foreground/75">{error}</p>
          <Button type="button" className="w-full sm:w-auto" onClick={() => void loadLibrary()}>
            Retry
          </Button>
        </Card>
      ) : !hasItems ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h2 className="text-xl font-semibold">You don't have notes yet</h2>
          <p className="text-sm text-foreground/75">
            Save your first note to start building Study Packs.
          </p>
          <Link href="/study" className="w-full sm:w-auto">
            <Button type="button" className="w-full sm:w-auto">
              New Note
            </Button>
          </Link>
        </Card>
      ) : (
        <div className="space-y-4">
          <Card className="space-y-4 p-4 sm:p-6">
            <div className="grid gap-3 lg:grid-cols-4">
              <div className="space-y-2 lg:col-span-1">
                <label htmlFor="library-search" className="text-sm font-medium">
                  Search
                </label>
                <input
                  id="library-search"
                  type="search"
                  value={searchQuery}
                  onChange={(event) => setSearchQuery(event.target.value)}
                  placeholder="Search notes..."
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
                />
              </div>
              <div className="space-y-2 lg:col-span-1">
                <label htmlFor="library-subject" className="text-sm font-medium">
                  Subject
                </label>
                <select
                  id="library-subject"
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
              <div className="relative space-y-2 lg:col-span-1">
                <label htmlFor="library-tag-filter" className="text-sm font-medium">
                  Tags
                </label>
                <button
                  id="library-tag-filter"
                  type="button"
                  className="flex h-10 w-full items-center justify-between rounded-lg border border-border bg-background px-3 text-left text-sm text-foreground outline-none transition-colors focus:ring-2 focus:ring-blue-600"
                  aria-haspopup="listbox"
                  aria-expanded={tagFilterOpen}
                  aria-label="Select tags"
                  onClick={() => setTagFilterOpen((previous) => !previous)}
                >
                  <span className={selectedTags.length === 0 ? "text-foreground/55" : ""}>
                    {selectedTags.length === 0
                      ? "Select tags"
                      : selectedTags.length === 1
                        ? selectedTags[0]
                        : `${selectedTags.length} tags selected`}
                  </span>
                  <ChevronDown className={`h-4 w-4 text-foreground/70 transition-transform ${tagFilterOpen ? "rotate-180" : ""}`} />
                </button>
                {tagFilterOpen ? (
                  <div
                    className="absolute z-30 mt-1 w-full rounded-lg border border-border bg-background p-2 shadow-md"
                    role="listbox"
                    aria-multiselectable="true"
                  >
                    <div className="space-y-2">
                      <input
                        type="search"
                        value={tagSearchQuery}
                        onChange={(event) => setTagSearchQuery(event.target.value)}
                        placeholder="Search tags..."
                        className="h-9 w-full rounded-md border border-border bg-background px-2 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
                      />
                    </div>
                    {availableTags.length === 0 ? (
                      <p className="px-2 py-2 text-sm text-foreground/65">No tags available yet.</p>
                    ) : visibleTagOptions.length === 0 ? (
                      <p className="px-2 py-2 text-sm text-foreground/65">No tags match your search.</p>
                    ) : (
                      <div className="mt-2 max-h-56 space-y-1 overflow-y-auto">
                        {visibleTagOptions.map((tag) => {
                          const isSelected = selectedTags.includes(tag);
                          return (
                            <label
                              key={tag}
                              className="flex cursor-pointer items-center gap-2 rounded px-2 py-1 text-sm hover:bg-muted/50"
                            >
                              <input
                                type="checkbox"
                                checked={isSelected}
                                onChange={() => toggleTag(tag)}
                                className="h-4 w-4 rounded border-border"
                              />
                              <span>{tag}</span>
                            </label>
                          );
                        })}
                      </div>
                    )}
                  </div>
                ) : null}
              </div>
              <div className="space-y-2 lg:col-span-1">
                <label htmlFor="library-sort" className="text-sm font-medium">
                  Sort by
                </label>
                <select
                  id="library-sort"
                  value={sortBy}
                  onChange={(event) => setSortBy(event.target.value as LibrarySortOption)}
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus:ring-2 focus:ring-blue-600"
                >
                  <option value="RECENTLY_UPDATED">Recently updated</option>
                  <option value="RECENTLY_REVIEWED">Recently reviewed</option>
                  <option value="TITLE">Title</option>
                </select>
              </div>
            </div>

            {hasActiveFilters ? (
              <div className="space-y-2 border-t border-border pt-3">
                <div className="flex flex-wrap items-center gap-2">
                  {selectedSubject !== ALL_SUBJECTS ? (
                    <span className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1 text-xs">
                      Subject: {selectedSubject}
                      <button
                        type="button"
                        className="text-foreground/65 hover:text-foreground"
                        onClick={() => setSelectedSubject(ALL_SUBJECTS)}
                        aria-label="Clear subject filter"
                      >x</button>
                    </span>
                  ) : null}
                  {selectedTags.map((tag) => (
                    <span
                      key={`active-tag-${tag}`}
                      className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1 text-xs"
                    >
                      {tag}
                      <button
                        type="button"
                        className="text-foreground/65 hover:text-foreground"
                        onClick={() => setSelectedTags((previous) => previous.filter((value) => value !== tag))}
                        aria-label={`Remove tag filter ${tag}`}
                      >x</button>
                    </span>
                  ))}
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="h-8"
                    onClick={clearFilters}
                  >
                    Clear all
                  </Button>
                </div>
              </div>
            ) : null}
          </Card>

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
                const stateMeta = getNoteStateMeta(item.studyPackStatus);
                const itemTags = normalizeTags(item.tags);
                const subject = normalizeSubject(item.subject);
                const subjectLabel = subject ?? "Uncategorized";
                const subjectClassName = subject
                  ? "border-blue-500/35 bg-blue-500/10 text-blue-700 dark:text-blue-300"
                  : "border-border bg-muted/40 text-foreground/65";

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
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0 space-y-2">
                        <span
                          className={`inline-flex items-center rounded-full border px-2 py-1 text-xs font-medium ${subjectClassName}`}
                        >
                          {subjectLabel}
                        </span>
                        <h3 className="text-base font-semibold transition-colors sm:text-lg">
                          {item.title?.trim() || "Untitled note"}
                        </h3>
                        <span
                          className={`inline-flex items-center rounded-full border px-2 py-1 text-xs font-medium ${stateMeta.className}`}
                        >
                          {stateMeta.label}
                        </span>
                        <p className="text-sm leading-relaxed text-foreground/75">
                          {toPreview(item.contentPreview)}
                        </p>
                      </div>
                      <div className="relative" data-card-menu="true">
                        <button
                          type="button"
                          aria-label="Open note actions"
                          className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-border bg-background text-sm text-foreground/70 hover:bg-muted/60 hover:text-foreground"
                          onClick={(event) => {
                            event.stopPropagation();
                            setCardMenuOpenId((previous) => (previous === item.id ? null : item.id));
                          }}
                          onKeyDown={(event) => event.stopPropagation()}
                        >
                          ⋯
                        </button>
                        {cardMenuOpenId === item.id ? (
                          <div className="absolute right-0 top-9 z-20 w-40 rounded-md border border-border bg-background p-1 shadow-sm">
                            <button
                              type="button"
                              className="w-full rounded px-3 py-2 text-left text-sm hover:bg-muted/60"
                              onClick={(event) => {
                                event.stopPropagation();
                                void handleMakeCopy(item.id);
                              }}
                              disabled={copyingNoteId === item.id}
                            >
                              {copyingNoteId === item.id ? "Copying..." : "Make a Copy"}
                            </button>
                            <button
                              type="button"
                              className="w-full rounded px-3 py-2 text-left text-sm text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-950/40"
                              onClick={(event) => {
                                event.stopPropagation();
                                void handleDelete(item.id);
                              }}
                              disabled={deletingNoteId === item.id}
                            >
                              {deletingNoteId === item.id ? "Deleting..." : "Delete"}
                            </button>
                          </div>
                        ) : null}
                      </div>
                    </div>

                    <div className="flex flex-wrap gap-2">
                      {itemTags.length > 0 ? (
                        itemTags.map((tag) => (
                          <span
                            key={`${item.id}-${tag}`}
                            className="rounded-full border border-border bg-background px-2 py-1 text-xs text-foreground/75"
                          >
                            {tag}
                          </span>
                        ))
                      ) : (
                        <span className="rounded-full border border-dashed border-border px-2 py-1 text-xs text-foreground/55">
                          No tags
                        </span>
                      )}
                    </div>

                    <div className="space-y-1">
                      <p className="text-xs text-foreground/65">
                        Updated {new Date(item.updatedAt).toLocaleString()}
                      </p>
                      {reviewSummary.lastReviewedAt ? (
                        <p className="text-xs text-foreground/65">
                          Last reviewed {formatRelativeReviewTime(reviewSummary.lastReviewedAt)}
                        </p>
                      ) : (
                        <p className="text-xs text-foreground/65">
                          Not reviewed yet
                        </p>
                      )}
                    </div>

                    {item.studyPackId ? (
                      <div className="flex flex-col gap-2 sm:flex-row">
                        <Button
                          type="button"
                          size="sm"
                          className="w-full sm:w-auto"
                          onClick={(event) => {
                            event.stopPropagation();
                            router.push(`/notes/${item.id}/quick-review`);
                          }}
                          onKeyDown={(event) => event.stopPropagation()}
                        >
                          Quick Review
                        </Button>
                      </div>
                    ) : null}
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
                Load More
              </Button>
            </div>
          ) : null}
        </div>
      )}
      {toast ? (
        <div role="status" aria-live="polite" className="fixed bottom-4 right-4 z-50 rounded-md border border-border bg-background px-3 py-2 text-sm shadow-sm">
          {toast}
        </div>
      ) : null}
    </main>
  );
}
