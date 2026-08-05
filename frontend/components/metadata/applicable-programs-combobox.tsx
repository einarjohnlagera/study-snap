"use client";

import { useMemo, useState } from "react";
import { CourseProgramCombobox } from "@/components/metadata/course-program-combobox";
import { Button } from "@/components/ui/button";
import type { CourseProgramCatalogItem } from "@/lib/api";

type ApplicableProgramsComboboxProps = {
  id: string;
  catalog: CourseProgramCatalogItem[];
  selectedIds: string[];
  onChange: (selectedIds: string[]) => void;
  loading?: boolean;
  error?: string | null;
  onRetry?: () => void;
  disabled?: boolean;
};

export function ApplicableProgramsCombobox({
  id,
  catalog,
  selectedIds,
  onChange,
  loading = false,
  error = null,
  onRetry,
  disabled = false,
}: Readonly<ApplicableProgramsComboboxProps>) {
  const [selectionDraft, setSelectionDraft] = useState("");
  const selectedIdSet = useMemo(() => new Set(selectedIds), [selectedIds]);
  const selectedPrograms = useMemo(
    () => catalog.filter((program) => selectedIdSet.has(program.id)),
    [catalog, selectedIdSet],
  );
  const availablePrograms = useMemo(
    () => catalog.filter((program) => !selectedIdSet.has(program.id)),
    [catalog, selectedIdSet],
  );
  const controlDisabled = disabled || loading || Boolean(error);

  const handleSelect = (programName: string) => {
    setSelectionDraft(programName);
    const selectedProgram = availablePrograms.find((program) => program.name === programName);
    if (!selectedProgram) {
      return;
    }
    onChange([...selectedIds, selectedProgram.id]);
    setSelectionDraft("");
  };

  return (
    <div className="space-y-2">
      <CourseProgramCombobox
        id={id}
        value={selectionDraft}
        suggestions={availablePrograms.map((program) => program.name)}
        onChange={handleSelect}
        placeholder={loading ? "Loading course programs..." : "Add a course program"}
        disabled={controlDisabled}
        context="note"
        allowCustom={false}
        ariaLabel="Add an applicable program"
      />
      {error ? (
        <div className="flex flex-wrap items-center gap-2 text-xs text-red-600 dark:text-red-400">
          <span>{error}</span>
          {onRetry ? (
            <Button type="button" size="sm" variant="outline" onClick={onRetry} disabled={disabled}>
              Retry
            </Button>
          ) : null}
        </div>
      ) : null}
      {!error && !loading ? (
        <div className="flex min-h-8 flex-wrap gap-2" aria-label="Selected applicable programs">
          {selectedPrograms.length > 0 ? selectedPrograms.map((program) => (
            <span
              key={program.id}
              className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-2.5 py-1 text-xs text-foreground/80"
            >
              {program.name}
              <button
                type="button"
                aria-label={`Remove ${program.name}`}
                disabled={disabled}
                className="text-foreground/60 hover:text-foreground disabled:opacity-50"
                onClick={() => onChange(selectedIds.filter((selectedId) => selectedId !== program.id))}
              >
                ×
              </button>
            </span>
          )) : (
            <span className="text-xs text-foreground/55">No applicable programs selected.</span>
          )}
        </div>
      ) : null}
      <p className="text-xs text-foreground/60">
        Discovery only. These programs never affect Study Pack generation.
      </p>
    </div>
  );
}
