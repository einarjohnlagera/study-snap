"use client";

import type { ReactNode } from "react";
import { ArrowUpDown, Filter } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

type LibraryToolbarProps = {
  searchId: string;
  searchPlaceholder: string;
  searchValue: string;
  onSearchValueChange: (value: string) => void;
  onOpenFilters: () => void;
  onOpenSort: () => void;
  activeFilterCount?: number;
  sortSummaryLabel: string;
  activeFilterSummary?: ReactNode;
};

export function LibraryToolbar({
  searchId,
  searchPlaceholder,
  searchValue,
  onSearchValueChange,
  onOpenFilters,
  onOpenSort,
  activeFilterCount = 0,
  sortSummaryLabel,
  activeFilterSummary,
}: Readonly<LibraryToolbarProps>) {
  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <div className="min-w-0 flex-1 space-y-2">
          <label htmlFor={searchId} className="text-sm font-medium">
            Search
          </label>
          <input
            id={searchId}
            type="search"
            value={searchValue}
            onChange={(event) => onSearchValueChange(event.target.value)}
            placeholder={searchPlaceholder}
            className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
          />
        </div>

        <div className="grid grid-cols-2 gap-2 sm:flex sm:shrink-0">
          <Button
            type="button"
            variant="outline"
            className="w-full sm:min-w-[120px]"
            onClick={onOpenFilters}
            aria-label="Open filters"
          >
            <span className="inline-flex items-center gap-2">
              <Filter className="h-4 w-4" aria-hidden="true" />
              <span>Filter</span>
              {activeFilterCount > 0 ? (
                <span className="inline-flex min-w-5 items-center justify-center rounded-full bg-blue-600 px-1.5 py-0.5 text-[11px] font-semibold text-white dark:bg-blue-500">
                  {activeFilterCount}
                </span>
              ) : null}
            </span>
          </Button>

          <Button
            type="button"
            variant="outline"
            className="w-full sm:min-w-[120px]"
            onClick={onOpenSort}
            aria-label="Open sorting"
          >
            <span className="inline-flex items-center gap-2">
              <ArrowUpDown className="h-4 w-4" aria-hidden="true" />
              <span>Sort</span>
            </span>
          </Button>
        </div>
      </div>

      <div className="flex flex-col gap-3 border-t border-border pt-3">
        <p className="text-xs text-foreground/60">Sorted by {sortSummaryLabel}</p>
        {activeFilterSummary}
      </div>
    </Card>
  );
}
