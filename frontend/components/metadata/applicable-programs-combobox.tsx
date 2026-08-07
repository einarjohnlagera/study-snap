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

type AvailableProgramFamily = {
  id: string;
  name: string;
  memberIds: string[];
  unselectedCount: number;
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
  const availableProgramFamilies = useMemo(() => {
    const families = new Map<string, Omit<AvailableProgramFamily, "unselectedCount">>();

    catalog.forEach((program) => {
      if (!program.programFamilyId || !program.programFamilyName) {
        return;
      }
      const family = families.get(program.programFamilyId);
      if (family) {
        family.memberIds.push(program.id);
        return;
      }
      families.set(program.programFamilyId, {
        id: program.programFamilyId,
        name: program.programFamilyName,
        memberIds: [program.id],
      });
    });

    return Array.from(families.values())
      .map((family) => ({
        ...family,
        unselectedCount: family.memberIds.filter((id) => !selectedIdSet.has(id)).length,
      }))
      .filter((family) => family.unselectedCount > 0);
  }, [catalog, selectedIdSet]);
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

  // ADR-001 ruling 4, binding: family expansion is UNCONDITIONAL. It must never depend on the note's
  // Subject, Domain Context, learner level, or anything else about the note -- doing so would make
  // Program Families a second curriculum taxonomy and re-couple the axes ADR-001 separated. This
  // component deliberately receives no note context, which is the structural guard; do not add one.
  // Over-selection is intended and safe: the author sees the explicit rows and trims them.
  const handleFamilyExpansion = (memberIds: string[]) => {
    const nextIds = [...selectedIds];
    const nextIdSet = new Set(nextIds);
    memberIds.forEach((memberId) => {
      if (!nextIdSet.has(memberId)) {
        nextIds.push(memberId);
        nextIdSet.add(memberId);
      }
    });
    onChange(nextIds);
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
        // The caller already explains this field above the input, and the default helper says
        // "Choose or type" — which is wrong here, since curators select from the catalog only.
        helperText={null}
        ariaLabel="Add a course or program"
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
      {!controlDisabled && availableProgramFamilies.length > 0 ? (
        <div className="flex flex-wrap gap-2" aria-label="Program family shortcuts">
          {availableProgramFamilies.map((family) => (
            <Button
              key={family.id}
              type="button"
              size="sm"
              variant="outline"
              onClick={() => handleFamilyExpansion(family.memberIds)}
            >
              Add all {family.unselectedCount} {family.name} {family.unselectedCount === 1 ? "program" : "programs"}
            </Button>
          ))}
        </div>
      ) : null}
      {!error && !loading ? (
        <div className="flex min-h-8 flex-wrap gap-2" aria-label="Selected course programs">
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
            <span className="text-xs text-foreground/55">No course programs selected.</span>
          )}
        </div>
      ) : null}
      <p className="text-xs text-foreground/60">
        These programs decide who finds the note. A program list is never sent to the AI — only a single program can inform the writing domain, and Domain Context overrides it.
      </p>
    </div>
  );
}
