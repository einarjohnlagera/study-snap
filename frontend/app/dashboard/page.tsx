"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { deleteMyStudyPack, listMyStudyPacks, type StudyPackListItemResponse } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

export default function DashboardPage() {
  const [items, setItems] = useState<StudyPackListItemResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const authUser = getAuthUser();
    if (!authUser) {
      setError("Please log in to access your dashboard.");
      setLoading(false);
      return;
    }

    void (async () => {
      try {
        const data = await listMyStudyPacks();
        setItems(data);
      } catch (err) {
        const message = err instanceof Error ? err.message : "Could not load your Study Packs.";
        setError(message);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const handleDelete = async (id: string) => {
    try {
      await deleteMyStudyPack(id);
      setItems((prev) => prev.filter((item) => item.id !== id));
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not delete the Study Pack.";
      setError(message);
    }
  };

  return (
    <div className="mx-auto w-full max-w-3xl space-y-4 px-6 py-10">
      <h1 className="text-2xl font-semibold">Dashboard</h1>
      {loading ? <p className="text-foreground/75">Loading your Study Packs...</p> : null}
      {error ? <p className="text-red-600 dark:text-red-400">{error}</p> : null}

      {!loading && !error && items.length === 0 ? (
        <p className="text-foreground/75">You do not have any saved Study Packs yet.</p>
      ) : null}

      {items.map((item) => (
        <Card key={item.id} className="space-y-3">
          <h2 className="text-lg font-medium">{item.title}</h2>
          <p className="text-sm text-foreground/75">{item.summaryPreview}</p>
          <p className="text-xs text-foreground/65">
            {item.quizCount} quiz questions · {new Date(item.createdAt).toLocaleString()}
          </p>
          <Button type="button" variant="outline" onClick={() => void handleDelete(item.id)}>
            Delete
          </Button>
        </Card>
      ))}
    </div>
  );
}
