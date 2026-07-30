"use client";

import { useMemo } from "react";
import { Card } from "@/components/ui/card";
import type { NoteCollectionSummary } from "@/lib/api";

export type PlanPickerLoadState = "loading" | "ready" | "error";

type PlanPickerProps = {
  collections: NoteCollectionSummary[];
  selectedCollectionId: string | null;
  collectionsState: PlanPickerLoadState;
  onChange: (collectionId: string | null) => void;
  id?: string;
  label?: string;
  description?: string;
  emptyOptionLabel?: string;
  includeParentCollections?: boolean;
  showEmptyOption?: boolean;
};

export function PlanPicker({
  collections,
  selectedCollectionId,
  collectionsState,
  onChange,
  id = "progress-plan-picker",
  label = "Progress view",
  description = "Switch between all subjects and one saved study plan.",
  emptyOptionLabel = "All subjects",
  includeParentCollections = false,
  showEmptyOption = true,
}: Readonly<PlanPickerProps>) {
  const selectableCollections = useMemo(
    () => includeParentCollections
      ? collections
      : collections.filter((collection) => collection.childCount === 0),
    [collections, includeParentCollections],
  );
  const selectedCollection = selectedCollectionId
    ? collections.find((collection) => collection.id === selectedCollectionId)
    : null;
  const selectedSelectableCollection = selectedCollectionId
    ? selectableCollections.find((collection) => collection.id === selectedCollectionId)
    : null;

  return (
    <Card className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5">
      <div className="space-y-1">
        <label htmlFor={id} className="text-sm font-semibold text-foreground">
          {label}
        </label>
        <p className="text-sm text-foreground/65">
          {description}
        </p>
      </div>
      <div className="flex flex-col gap-1 sm:min-w-72">
        <select
          id={id}
          value={selectedCollectionId ?? ""}
          onChange={(event) => onChange(event.target.value || null)}
          className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground shadow-xs focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          {showEmptyOption ? <option value="">{emptyOptionLabel}</option> : null}
          {selectedCollectionId && !selectedSelectableCollection ? (
            <option value={selectedCollectionId}>{selectedCollection?.title ?? "Selected plan"}</option>
          ) : null}
          {selectableCollections.map((collection) => (
            <option key={collection.id} value={collection.id}>
              {collection.title}
            </option>
          ))}
        </select>
        {collectionsState === "loading" ? (
          <span className="text-xs text-foreground/50">Loading study plans...</span>
        ) : null}
        {collectionsState === "error" ? (
          <span className="text-xs text-rose-600 dark:text-rose-400">
            Could not load study plans for the picker.
          </span>
        ) : null}
      </div>
    </Card>
  );
}
