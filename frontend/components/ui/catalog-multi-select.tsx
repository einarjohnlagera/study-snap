"use client";

import { useEffect, useMemo, useRef, useState } from "react";

export type CatalogMultiSelectItem = { id: string; name: string };

export type CatalogMultiSelectProps = {
  id: string;
  label: string;
  items: readonly CatalogMultiSelectItem[];
  selectedIds: readonly string[];
  onChange: (selectedIds: string[]) => void;
  selectedSummary?: "count" | "chips";
  disabled?: boolean;
  searchPlaceholder?: string;
  emptyHint?: string;
};

const normalize = (value: string) => value.trim().replaceAll(/\s+/g, " ").toLowerCase();

export function CatalogMultiSelect({
  id,
  label,
  items,
  selectedIds,
  onChange,
  selectedSummary = "chips",
  disabled = false,
  searchPlaceholder,
  emptyHint,
}: Readonly<CatalogMultiSelectProps>) {
  const [query, setQuery] = useState("");
  const searchRef = useRef<HTMLInputElement>(null);
  const labelId = `${id}-label`;
  const selectedSet = useMemo(() => new Set(selectedIds), [selectedIds]);
  const itemNoun = label.toLowerCase().includes("famil") ? "families" : "programs";
  const filteredItems = useMemo(() => {
    const normalizedQuery = normalize(query);
    return items.filter((item) => (
      normalize(item.name).includes(normalizedQuery) || selectedSet.has(item.id)
    ));
  }, [items, query, selectedSet]);
  const selectedItems = useMemo(
    () => items.filter((item) => selectedSet.has(item.id)),
    [items, selectedSet],
  );

  useEffect(() => {
    searchRef.current?.focus();
  }, []);

  const toggle = (itemId: string, checked: boolean) => {
    onChange(checked
      ? [...selectedIds.filter((idValue) => idValue !== itemId), itemId]
      : selectedIds.filter((idValue) => idValue !== itemId));
  };

  const removeChip = (itemId: string) => {
    const remaining = selectedIds.filter((idValue) => idValue !== itemId);
    onChange(remaining);
    if (remaining.length === 0) globalThis.setTimeout(() => searchRef.current?.focus(), 0);
  };

  return (
    <div className="space-y-2">
      <label id={labelId} htmlFor={`${id}-search`} className="text-sm font-medium text-foreground">
        {label}
      </label>
      <input
        ref={searchRef}
        id={`${id}-search`}
        type="search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        disabled={disabled}
        placeholder={searchPlaceholder ?? `Search or select ${itemNoun}…`}
        className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground"
      />
      <p aria-live="polite" className="text-xs text-foreground/60">
        {filteredItems.length} {itemNoun}
      </p>
      <div
        role="group"
        aria-labelledby={labelId}
        className="max-h-64 overflow-y-auto rounded-lg border border-border"
      >
        {filteredItems.map((item) => (
          <label key={item.id} className="flex min-h-11 cursor-pointer items-start gap-3 border-b border-border/60 px-3 py-3 last:border-b-0">
            <input
              type="checkbox"
              checked={selectedSet.has(item.id)}
              onChange={(event) => toggle(item.id, event.target.checked)}
              disabled={disabled}
              className="mt-0.5 h-5 w-5 shrink-0"
            />
            <span className="min-w-0 whitespace-normal text-sm leading-5 text-foreground">{item.name}</span>
          </label>
        ))}
        {items.length === 0 ? (
          <p aria-live="polite" className="p-3 text-sm text-foreground/60">No {itemNoun} in the catalog yet.</p>
        ) : filteredItems.length === 0 ? (
          <p aria-live="polite" className="p-3 text-sm text-foreground/60">
            No {itemNoun} match &quot;{query}&quot;.
          </p>
        ) : null}
      </div>
      {selectedSummary === "count" ? (
        <div className="flex min-h-11 items-center justify-between gap-3">
          <span className="text-sm text-foreground/70">Selected {selectedIds.length}</span>
          <button
            type="button"
            aria-label="Clear all selected"
            disabled={disabled || selectedIds.length === 0}
            onClick={() => onChange([])}
            className="min-h-11 rounded-md px-3 text-sm text-foreground/70 hover:bg-muted disabled:opacity-50"
          >
            Clear all
          </button>
        </div>
      ) : (
        <div className="space-y-2" aria-label={`Selected ${label}`}>
          <p className="text-sm text-foreground/70">Selected ({selectedIds.length})</p>
          <div className="flex flex-wrap gap-2">
            {selectedItems.map((item) => (
              <span key={item.id} className="inline-flex max-w-full items-center rounded-full border border-border bg-background pl-2.5 text-xs text-foreground/80">
                <span className="whitespace-normal py-1.5">{item.name}</span>
                <button
                  type="button"
                  aria-label={`Remove ${item.name}`}
                  disabled={disabled}
                  onClick={() => removeChip(item.id)}
                  className="inline-flex min-h-11 min-w-11 shrink-0 items-center justify-center text-foreground/60 hover:text-foreground disabled:opacity-50"
                >
                  ×
                </button>
              </span>
            ))}
          </div>
          {selectedIds.length === 0 && emptyHint ? <p className="text-xs text-foreground/60">{emptyHint}</p> : null}
        </div>
      )}
      {selectedSummary === "count" && selectedIds.length === 0 && emptyHint ? (
        <p className="text-xs text-foreground/60">{emptyHint}</p>
      ) : null}
    </div>
  );
}
