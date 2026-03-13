"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { MoreHorizontal } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";
import { deleteMyStudyPack, listMyStudyPacks, type StudyPackListItemResponse } from "@/lib/api";
import { requireVerifiedOnboardedUser } from "@/lib/route-guards";

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
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [menuOpenId, setMenuOpenId] = useState<string | null>(null);
  const [pendingDeleteItem, setPendingDeleteItem] = useState<StudyPackListItemResponse | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const menuRef = useRef<HTMLDivElement | null>(null);

  const loadLibrary = useCallback(async () => {
    if (!requireVerifiedOnboardedUser(router, {
      onUnauthenticated: () => {
        setError("Please log in to view your Study Library.");
        setLoading(false);
      },
    })) {
      return;
    }

    setLoading(true);
    setError(null);
    setActionError(null);
    try {
      const data = await listMyStudyPacks();
      setItems(data);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load your Study Packs.";
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [router]);

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
  const sortedItems = useMemo(() => {
    return [...items].sort((left, right) => {
      return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
    });
  }, [items]);

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
          <h2 className="text-xl font-semibold">No Study Packs yet</h2>
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
          {actionError ? (
            <Card className="space-y-3 p-4 sm:p-6">
              <h2 className="text-base font-semibold sm:text-lg">Could not delete Study Pack</h2>
              <p className="text-sm text-foreground/75">{actionError}</p>
            </Card>
          ) : null}
          <div className="grid gap-4 md:grid-cols-2">
            {sortedItems.map((item) => {
              const isDeleting = deletingId === item.id;
              const menuOpen = menuOpenId === item.id;
              return (
                <Card key={item.id} className="flex h-full flex-col justify-between space-y-4 p-4 sm:p-6">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0 space-y-2">
                      <Link href={`/study-packs/${item.id}`} className="group block">
                        <h3 className="text-base font-semibold transition-colors group-hover:text-foreground sm:text-lg">
                          {item.title}
                        </h3>
                      </Link>
                      <p className="text-sm leading-relaxed text-foreground/75">{item.summaryPreview}</p>
                    </div>
                    <div
                      className="relative shrink-0"
                      ref={menuOpen ? menuRef : undefined}
                    >
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="h-9 w-9 px-0"
                        aria-label={`Open actions for ${item.title}`}
                        aria-haspopup="menu"
                        aria-expanded={menuOpen}
                        onClick={() => {
                          setMenuOpenId((previous) => (previous === item.id ? null : item.id));
                          setActionError(null);
                        }}
                        disabled={isDeleting}
                      >
                        <MoreHorizontal className="h-4 w-4" />
                      </Button>
                      {menuOpen ? (
                        <div className="absolute right-0 top-10 z-20 w-44 rounded-md border border-border bg-background p-1 shadow-sm">
                          <Link
                            href={`/study-packs/${item.id}`}
                            className="block rounded px-3 py-2 text-sm text-foreground/85 hover:bg-muted/70 hover:text-foreground"
                            onClick={() => setMenuOpenId(null)}
                          >
                            Open Study Pack
                          </Link>
                          <button
                            type="button"
                            className="block w-full rounded px-3 py-2 text-left text-sm text-red-600 hover:bg-red-50 dark:text-red-300 dark:hover:bg-red-950/40"
                            onClick={() => {
                              setPendingDeleteItem(item);
                              setMenuOpenId(null);
                            }}
                            disabled={isDeleting}
                          >
                            Delete Study Pack
                          </button>
                        </div>
                      ) : null}
                    </div>
                  </div>

                  <p className="text-xs text-foreground/65">
                    {new Date(item.createdAt).toLocaleString()}
                  </p>
                </Card>
              );
            })}
          </div>

          {pendingDeleteItem ? (
            <>
              <button
                type="button"
                className="fixed inset-0 z-30 bg-black/50"
                aria-label="Close delete confirmation"
                onClick={() => {
                  if (deletingId) {
                    return;
                  }
                  setPendingDeleteItem(null);
                }}
              />
              <div className="fixed inset-0 z-40 flex items-center justify-center p-4">
                <Card role="alertdialog" aria-modal="true" className="w-full max-w-md space-y-4 p-5 sm:p-6">
                  <div className="space-y-2">
                    <h2 className="text-lg font-semibold sm:text-xl">Delete Study Pack</h2>
                    <p className="text-sm text-foreground/75">
                      Are you sure you want to delete this Study Pack?
                      <br />
                      This action cannot be undone.
                    </p>
                    <p className="text-sm font-medium text-foreground">{pendingDeleteItem.title}</p>
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
                </Card>
              </div>
            </>
          ) : null}
        </div>
      )}
    </main>
  );
}
