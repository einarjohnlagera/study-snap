"use client";

import Link from "next/link";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {useRouter} from "next/navigation";
import {MoreHorizontal} from "lucide-react";
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
  const [sortBy, setSortBy] = useState<LibrarySortOption>("RECENTLY_CREATED");
  const [lastReviewedByPackId, setLastReviewedByPackId] = useState<Record<string, string | null>>({});
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [menuOpenId, setMenuOpenId] = useState<string | null>(null);
  const [pendingDeleteItem, setPendingDeleteItem] = useState<StudyPackListItemResponse | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const menuRef = useRef<HTMLDivElement | null>(null);

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
  const visibleItems = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    const filtered = query.length === 0
      ? items
      : items.filter((item) => {
          const titleMatch = item.title.toLowerCase().includes(query);
          const tagMatch = item.tags.some((tag) => tag.toLowerCase().includes(query));
          return titleMatch || tagMatch;
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
  }, [items, lastReviewedByPackId, searchQuery, sortBy]);

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
            <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
              <div className="w-full space-y-2 sm:max-w-md">
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
              <div className="w-full space-y-2 sm:w-56">
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
          </Card>

          {actionError ? (
            <Card className="space-y-3 p-4 sm:p-6">
              <h2 className="text-base font-semibold sm:text-lg">Library action failed</h2>
              <p className="text-sm text-foreground/75">{actionError}</p>
            </Card>
          ) : null}
          {visibleItems.length === 0 ? (
            <Card className="space-y-3 p-4 sm:p-6">
              <h2 className="text-base font-semibold sm:text-lg">No Study Packs found</h2>
              <p className="text-sm text-foreground/75">
                Try a different title or tag keyword.
              </p>
              <div className="flex justify-start">
                <Button type="button" variant="outline" onClick={() => setSearchQuery("")}>
                  Clear search
                </Button>
              </div>
            </Card>
          ) : (
            <div className="grid gap-4 md:grid-cols-2">
              {visibleItems.map((item) => {
                const isDeleting = deletingId === item.id;
                const menuOpen = menuOpenId === item.id;
                const lastReviewed = lastReviewedByPackId[item.id];

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
                      {item.tags.length > 0 ? (
                        item.tags.map((tag) => (
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
