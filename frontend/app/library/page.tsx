"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";
import { listMyStudyPacks, type StudyPackListItemResponse } from "@/lib/api";
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
  const [loading, setLoading] = useState(true);

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
            Generate your first Study Pack to build your study library.
          </p>
          <Link href="/study" className="w-full sm:w-auto">
            <Button type="button" className="w-full sm:w-auto">
              Create Study Pack
            </Button>
          </Link>
        </Card>
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {sortedItems.map((item) => (
            <Link key={item.id} href={`/study-packs/${item.id}`} className="group">
              <Card className="flex h-full flex-col justify-between space-y-4 p-4 transition-shadow sm:p-6">
                <div className="space-y-2">
                  <h3 className="text-base font-semibold transition-colors group-hover:text-foreground sm:text-lg">
                    {item.title}
                  </h3>
                  <p className="text-sm leading-relaxed text-foreground/75">{item.summaryPreview}</p>
                </div>
                <p className="text-xs text-foreground/65">
                  {new Date(item.createdAt).toLocaleString()}
                </p>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </main>
  );
}
