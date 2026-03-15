"use client";

import Link from "next/link";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {useRouter} from "next/navigation";
import {ChevronDown, MoreHorizontal} from "lucide-react";
import {Button} from "@/components/ui/button";
import {Card} from "@/components/ui/card";
import {PageHeader} from "@/components/page-header";
import {
  deleteMyStudyPack,
  getQuickReviewPerformanceSummary,
  listMyStudyPacksPage,
  type StudyPackListItemResponse,
} from "@/lib/api";
import {requireVerifiedOnboardedUser} from "@/lib/route-guards";

type LibrarySortOption = "RECENTLY_CREATED" | "RECENTLY_REVIEWED" | "TITLE";
const LIBRARY_PAGE_SIZE = 20;
const ALL_SUBJECTS = "__ALL_SUBJECTS__";

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

function toSummaryPreview(summary: string, maxLength = 160) {
  const clean = summary.trim();
  if (clean.length <= maxLength) {
    return clean;
  }
  return `${clean.slice(0, maxLength - 3)}...`;
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
  const [items, setItems] = useState<StudyPackListItemResponse[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedSubject, setSelectedSubject] = useState<string>(ALL_SUBJECTS);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [sortBy, setSortBy] = useState<LibrarySortOption>("RECENTLY_CREATED");
  const [lastReviewedByPackId, setLastReviewedByPackId] = useState<Record<string, string | null>>({});
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [menuOpenId, setMenuOpenId] = useState<string | null>(null);
  const [tagFilterOpen, setTagFilterOpen] = useState(false);
  const [pendingDeleteItem, setPendingDeleteItem] = useState<StudyPackListItemResponse | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const menuRef = useRef<HTMLDivElement | null>(null);
  const tagFilterRef = useRef<HTMLDivElement | null>(null);

  const hydrateLastReviewed = useCallback(async (packs: StudyPackListItemResponse[], append = false) => {
    if (packs.length === 0) {
      if (!append) {
        setLastReviewedByPackId({});
      }
      return;
    }

    const entries = await Promise.all(
      packs.map(async (pack) => {
        try {
          const summary = await getQuickReviewPerformanceSummary(pack.id);
          return [pack.id, summary.lastReviewedAt] as const;
        } catch {
          return [pack.id, null] as const;
        }
      }),
    );

    const entriesByPackId = Object.fromEntries(entries);
    setLastReviewedByPackId((previous) => (
      append
        ? { ...previous, ...entriesByPackId }
        : entriesByPackId
    ));
  }, []);

  const loadLibrary = useCallback(async () => {
    if (!requireVerifiedOnboardedUser(router)) {
      return;
    }

    setLoading(true);
    setLoadingMore(false);
    setError(null);
    setActionError(null);
    try {
      const page = await listMyStudyPacksPage({ limit: LIBRARY_PAGE_SIZE });
      setItems(page.items);
      setNextCursor(page.nextCursor);
      setHasMore(page.hasMore);
      void hydrateLastReviewed(page.items);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load your Study Packs.";
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [hydrateLastReviewed, router]);

  const loadMore = useCallback(async () => {
    if (!hasMore || !nextCursor || loadingMore) {
      return;
    }

    setLoadingMore(true);
    setActionError(null);
    try {
      const page = await listMyStudyPacksPage({ limit: LIBRARY_PAGE_SIZE, cursor: nextCursor });
      setItems((previous) => [...previous, ...page.items]);
      setNextCursor(page.nextCursor);
      setHasMore(page.hasMore);
      void hydrateLastReviewed(page.items, true);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load more Study Packs.";
      setActionError(message);
    } finally {
      setLoadingMore(false);
    }
  }, [hasMore, hydrateLastReviewed, loadingMore, nextCursor]);

  useEffect(() => {
    void loadLibrary();
  }, [loadLibrary]);

  useEffect(() => {
    if (!menuOpenId) {
      return;
    }
    const handleOutsideClick = (event: MouseEvent) => {
      if (!menuRef.current) {
        return;
      }
      if (!menuRef.current.contains(event.target as Node)) {
        setMenuOpenId(null);
      }
    };
    window.addEventListener("mousedown", handleOutsideClick);
    return () => {
      window.removeEventListener("mousedown", handleOutsideClick);
    };
  }, [menuOpenId]);

  useEffect(() => {
    if (!tagFilterOpen) {
      return;
    }
    const handleOutsideClick = (event: MouseEvent) => {
      if (!tagFilterRef.current) {
        return;
      }
      if (!tagFilterRef.current.contains(event.target as Node)) {
        setTagFilterOpen(false);
      }
    };
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setTagFilterOpen(false);
      }
    };
    window.addEventListener("mousedown", handleOutsideClick);
    window.addEventListener("keydown", handleEscape);
    return () => {
      window.removeEventListener("mousedown", handleOutsideClick);
      window.removeEventListener("keydown", handleEscape);
    };
  }, [tagFilterOpen]);

  const handleConfirmDelete = useCallback(async () => {
    if (!pendingDeleteItem) {
      return;
    }
    const targetId = pendingDeleteItem.id;
    setDeletingId(targetId);
    setActionError(null);
    try {
      await deleteMyStudyPack(targetId);
      setItems((previous) => previous.filter((item) => item.id !== targetId));
      setLastReviewedByPackId((previous) => {
        const next = { ...previous };
        delete next[targetId];
        return next;
      });
      setPendingDeleteItem(null);
      setMenuOpenId((previous) => (previous === targetId ? null : previous));
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not delete this Study Pack.";
      setActionError(message);
    } finally {
      setDeletingId(null);
    }
  }, [pendingDeleteItem]);

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

  const visibleItems = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    const filtered = items.filter((item) => {
      const itemTags = normalizeTags(item.tags);
      const titleMatch = query.length === 0
        || item.title.toLowerCase().includes(query)
        || itemTags.some((tag) => tag.toLowerCase().includes(query));
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
        return left.title.localeCompare(right.title);
      }
      if (sortBy === "RECENTLY_REVIEWED") {
        const reviewedDiff = byDateDesc(lastReviewedByPackId[left.id], lastReviewedByPackId[right.id]);
        if (reviewedDiff !== 0) {
          return reviewedDiff;
        }
      }
      return byDateDesc(left.createdAt, right.createdAt);
    });
  }, [items, lastReviewedByPackId, searchQuery, selectedSubject, selectedTags, sortBy]);

  return (
    <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <PageHeader
        eyebrow="LIBRARY"
        title="Study Library"
        description="Browse and revisit all of your saved Study Packs."
      />

      {loading ? (
        <LibraryLoading />
      ) : error ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h2 className="text-xl font-semibold">Could not load Study Library</h2>
          <p className="text-sm text-foreground/75">{error}</p>
          <Button type="button" className="w-full sm:w-auto" onClick={() => void loadLibrary()}>
            Retry
          </Button>
        </Card>
      ) : !hasItems ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h2 className="text-xl font-semibold">Your study library is empty</h2>
          <p className="text-sm text-foreground/75">
            Create your first Study Pack to start studying.
          </p>
          <Link href="/study" className="w-full sm:w-auto">
            <Button type="button" className="w-full sm:w-auto">
              Create Study Pack
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
                  placeholder="Search study packs..."
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
              <div className="relative space-y-2 lg:col-span-1" ref={tagFilterRef}>
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
                    {availableTags.length === 0 ? (
                      <p className="px-2 py-1 text-sm text-foreground/65">No tags available yet.</p>
                    ) : (
                      <div className="max-h-56 space-y-1 overflow-y-auto">
                        {availableTags.map((tag) => {
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
                  <option value="RECENTLY_CREATED">Recently created</option>
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
                  {hasActiveFilters ? (
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="h-8"
                      onClick={clearFilters}
                    >
                      Clear all
                    </Button>
                  ) : null}
                </div>
              </div>
            ) : null}
          </Card>

          {actionError ? (
            <Card className="space-y-3 p-4 sm:p-6">
              <h2 className="text-base font-semibold sm:text-lg">Library action failed</h2>
              <p className="text-sm text-foreground/75">{actionError}</p>
            </Card>
          ) : null}
          {visibleItems.length === 0 ? (
            <Card className="space-y-3 p-4 sm:p-6">
              <h2 className="text-base font-semibold sm:text-lg">
                No Study Packs match your current filters.
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
                const isDeleting = deletingId === item.id;
                const menuOpen = menuOpenId === item.id;
                const lastReviewed = lastReviewedByPackId[item.id];
                const itemTags = normalizeTags(item.tags);

                return (
                  <Card
                    key={item.id}
                    role="link"
                    tabIndex={0}
                    onClick={() => router.push(`/study-packs/${item.id}`)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        router.push(`/study-packs/${item.id}`);
                      }
                    }}
                    className="flex h-full cursor-pointer flex-col justify-between space-y-4 p-4 transition-colors hover:bg-muted/40 hover:shadow-md sm:p-6"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0 space-y-2">
                        <h3 className="text-base font-semibold transition-colors sm:text-lg">
                          {item.title}
                        </h3>
                        <p className="text-sm leading-relaxed text-foreground/75">
                          {toSummaryPreview(item.summaryPreview)}
                        </p>
                      </div>
                      <div className="relative shrink-0" ref={menuOpen ? menuRef : undefined}>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          className="h-9 w-9 px-0"
                          aria-label={`Open actions for ${item.title}`}
                          aria-haspopup="menu"
                          aria-expanded={menuOpen}
                          onClick={(event) => {
                            event.stopPropagation();
                            setMenuOpenId((previous) => (previous === item.id ? null : item.id));
                            setActionError(null);
                          }}
                          onKeyDown={(event) => event.stopPropagation()}
                          disabled={isDeleting}
                        >
                          <MoreHorizontal className="h-4 w-4" />
                        </Button>
                        {menuOpen ? (
                          <div
                            className="absolute right-0 top-10 z-20 w-44 rounded-md border border-border bg-background p-1 shadow-sm"
                            onClick={(event) => event.stopPropagation()}
                          >
                            <button
                              type="button"
                              className="block w-full rounded px-3 py-2 text-left text-sm text-red-600 hover:bg-red-50 dark:text-red-300 dark:hover:bg-red-950/40"
                              onClick={(event) => {
                                event.stopPropagation();
                                setPendingDeleteItem(item);
                                setMenuOpenId(null);
                              }}
                              onKeyDown={(event) => event.stopPropagation()}
                              disabled={isDeleting}
                            >
                              Delete Study Pack
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
                        Created {new Date(item.createdAt).toLocaleString()}
                      </p>
                      <p className="text-xs text-foreground/65">
                        Last reviewed {lastReviewed ? new Date(lastReviewed).toLocaleString() : "Not yet"}
                      </p>
                    </div>

                    <div className="flex flex-col gap-2 sm:flex-row">
                      <Button
                        type="button"
                        size="sm"
                        className="w-full sm:w-auto"
                        onClick={(event) => {
                          event.stopPropagation();
                          router.push(`/study-packs/${item.id}/quick-review`);
                        }}
                        onKeyDown={(event) => event.stopPropagation()}
                      >
                        Quick Review
                      </Button>
                    </div>
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
                onClick={() => void loadMore()}
                disabled={loadingMore}
                className="w-full sm:w-auto"
              >
                {loadingMore ? "Loading..." : "Load More"}
              </Button>
            </div>
          ) : null}

          {pendingDeleteItem ? (
            <>
              <button
                type="button"
                className="fixed inset-0 z-30 bg-black/70 backdrop-blur-[1px]"
                aria-label="Close delete confirmation"
                onClick={() => {
                  if (deletingId) {
                    return;
                  }
                  setPendingDeleteItem(null);
                }}
              />
              <div className="fixed inset-0 z-40 flex items-center justify-center p-4">
                <div
                  role="alertdialog"
                  aria-modal="true"
                  aria-labelledby="delete-study-pack-title"
                  className="w-full max-w-md space-y-4 rounded-xl border border-border bg-background p-5 text-foreground shadow-2xl dark:border-gray-700 dark:bg-gray-900 sm:p-6"
                >
                  <div className="space-y-2">
                    <h2 id="delete-study-pack-title" className="text-lg font-semibold sm:text-xl">
                      Delete Study Pack
                    </h2>
                    <p className="text-sm text-foreground/90">
                      Are you sure you want to delete this Study Pack?
                      <br />
                      This action cannot be undone.
                    </p>
                    <p className="text-sm font-medium text-foreground/95">{pendingDeleteItem.title}</p>
                  </div>
                  <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => setPendingDeleteItem(null)}
                      disabled={Boolean(deletingId)}
                    >
                      Cancel
                    </Button>
                    <Button
                      type="button"
                      className="bg-red-600 text-white hover:bg-red-700 dark:bg-red-600 dark:hover:bg-red-700"
                      onClick={() => void handleConfirmDelete()}
                      disabled={Boolean(deletingId)}
                    >
                      {deletingId ? "Deleting..." : "Delete"}
                    </Button>
                  </div>
                </div>
              </div>
            </>
          ) : null}
        </div>
      )}
    </main>
  );
}
