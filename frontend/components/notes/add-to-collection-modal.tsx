"use client";

import { useCallback, useEffect, useState } from "react";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import {
  addCollectionItems,
  createCollection,
  listCollections,
  type NoteCollectionSummary,
} from "@/lib/api";

const COLLECTION_TITLE_MAX_LENGTH = 150;

export type CollectionSuccess = {
  id: string;
  title: string;
};

export function AddToCollectionModal({
  isOpen,
  noteIds,
  singularLabel,
  itemNoun,
  onClose,
  onAdded,
}: Readonly<{
  isOpen: boolean;
  noteIds: string[];
  singularLabel: string;
  itemNoun: string;
  onClose: () => void;
  onAdded: (collection: CollectionSuccess) => void;
}>) {
  const [collections, setCollections] = useState<NoteCollectionSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submittingCollectionId, setSubmittingCollectionId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [title, setTitle] = useState("");

  const loadAvailableCollections = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      setCollections(await listCollections());
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : `Could not load your ${singularLabel.toLowerCase()}s.`);
    } finally {
      setLoading(false);
    }
  }, [singularLabel]);

  useEffect(() => {
    if (!isOpen) {
      setCollections([]);
      setLoadError(null);
      setSubmitError(null);
      setSubmittingCollectionId(null);
      setCreating(false);
      setTitle("");
      return;
    }
    void loadAvailableCollections();
  }, [isOpen, loadAvailableCollections]);

  const handleAddToExisting = async (collectionId: string) => {
    setSubmittingCollectionId(collectionId);
    setSubmitError(null);
    try {
      const saved = await addCollectionItems(collectionId, noteIds);
      onAdded({ id: saved.id, title: saved.title });
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : `Could not add to this ${singularLabel.toLowerCase()}.`);
    } finally {
      setSubmittingCollectionId(null);
    }
  };

  const handleCreate = async () => {
    const trimmedTitle = title.trim();
    if (!trimmedTitle) {
      setSubmitError("Title is required.");
      return;
    }
    setCreating(true);
    setSubmitError(null);
    try {
      const saved = await createCollection({ title: trimmedTitle, noteIds });
      onAdded({ id: saved.id, title: saved.title });
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : `Could not create this ${singularLabel.toLowerCase()}.`);
    } finally {
      setCreating(false);
    }
  };

  return (
    <AppModal
      isOpen={isOpen}
      title={`Add to a ${singularLabel}`}
      description={`Add ${noteIds.length} ${itemNoun}${noteIds.length === 1 ? "" : "s"} to an existing ${singularLabel.toLowerCase()} or create a new one.`}
      onClose={onClose}
      panelClassName="sm:max-w-2xl"
      actions={<Button type="button" variant="secondary" onClick={onClose}>Close</Button>}
    >
      <div className="space-y-5">
        {submitError ? (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-200">
            {submitError}
          </p>
        ) : null}

        {loading ? <p className="text-sm text-foreground/60">Loading your {singularLabel.toLowerCase()}s...</p> : null}

        {loadError ? (
          <div className="space-y-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950/30 dark:text-red-200">
            <p>{loadError}</p>
            <Button type="button" variant="outline" size="sm" onClick={() => void loadAvailableCollections()}>
              Retry
            </Button>
          </div>
        ) : null}

        {!loading && !loadError && collections.length > 0 ? (
          <section className="space-y-2">
            <h3 className="text-sm font-semibold">Existing {singularLabel.toLowerCase()}s</h3>
            <div className="max-h-64 space-y-2 overflow-y-auto pr-1">
              {collections.map((collection) => (
                <div key={collection.id} className="flex flex-col gap-3 rounded-lg border border-border p-3 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{collection.title}</p>
                    <p className="text-xs text-foreground/60">
                      {collection.itemCount} {collection.itemCount === 1 ? "note" : "notes"}
                    </p>
                  </div>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    loading={submittingCollectionId === collection.id}
                    loadingText="Adding..."
                    disabled={creating || (submittingCollectionId !== null && submittingCollectionId !== collection.id)}
                    onClick={() => void handleAddToExisting(collection.id)}
                  >
                    Add here
                  </Button>
                </div>
              ))}
            </div>
          </section>
        ) : null}

        {!loading && !loadError && collections.length === 0 ? (
          <p className="rounded-lg bg-muted px-3 py-3 text-sm text-foreground/70">
            Create your first {singularLabel.toLowerCase()} and add {noteIds.length === 1 ? "this" : "these"} {noteIds.length === 1 ? itemNoun : `${itemNoun}s`} to it.
          </p>
        ) : null}

        {!loadError ? (
          <section className="space-y-3 border-t border-border pt-4">
            <h3 className="text-sm font-semibold">Create new {singularLabel}</h3>
            <label className="block space-y-1.5">
              <span className="text-sm font-medium">Title</span>
              <input
                value={title}
                maxLength={COLLECTION_TITLE_MAX_LENGTH}
                onChange={(event) => {
                  setTitle(event.target.value);
                  setSubmitError(null);
                }}
                placeholder={`${singularLabel} title`}
                className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
              />
            </label>
            <Button
              type="button"
              loading={creating}
              loadingText="Creating..."
              disabled={submittingCollectionId !== null}
              onClick={() => void handleCreate()}
            >
              Create new {singularLabel}
            </Button>
          </section>
        ) : null}
      </div>
    </AppModal>
  );
}
