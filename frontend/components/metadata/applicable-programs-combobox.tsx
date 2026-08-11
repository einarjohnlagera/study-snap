"use client";

import { useEffect, useMemo, useState } from "react";
import { CourseProgramCombobox } from "@/components/metadata/course-program-combobox";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import {
  ApiRequestError,
  createCourseProgram,
  findSimilarCoursePrograms,
  type CourseProgramCatalogItem,
} from "@/lib/api";

type ApplicableProgramsComboboxProps = {
  id: string;
  catalog: CourseProgramCatalogItem[];
  selectedIds: string[];
  onChange: (selectedIds: string[]) => void;
  loading?: boolean;
  error?: string | null;
  onRetry?: () => void;
  disabled?: boolean;
  canCreateCatalogProgram?: boolean;
  onCatalogProgramCreated?: (program: CourseProgramCatalogItem) => void;
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
  canCreateCatalogProgram = false,
  onCatalogProgramCreated,
}: Readonly<ApplicableProgramsComboboxProps>) {
  const [selectionDraft, setSelectionDraft] = useState("");
  const [createdPrograms, setCreatedPrograms] = useState<CourseProgramCatalogItem[]>([]);
  const [nearMatches, setNearMatches] = useState<CourseProgramCatalogItem[]>([]);
  const [checkingNearMatches, setCheckingNearMatches] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [programFamilyId, setProgramFamilyId] = useState("");
  const [examGoalSlug, setExamGoalSlug] = useState("");
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [duplicateExisting, setDuplicateExisting] = useState<CourseProgramCatalogItem | null>(null);
  const mergedCatalog = useMemo(() => {
    const existingIds = new Set(catalog.map((program) => program.id));
    return [...catalog, ...createdPrograms.filter((program) => !existingIds.has(program.id))];
  }, [catalog, createdPrograms]);
  const selectedIdSet = useMemo(() => new Set(selectedIds), [selectedIds]);
  const selectedPrograms = useMemo(
    () => mergedCatalog.filter((program) => selectedIdSet.has(program.id)),
    [mergedCatalog, selectedIdSet],
  );
  const availablePrograms = useMemo(
    () => mergedCatalog.filter((program) => !selectedIdSet.has(program.id)),
    [mergedCatalog, selectedIdSet],
  );
  const availableProgramFamilies = useMemo(() => {
    const families = new Map<string, Omit<AvailableProgramFamily, "unselectedCount">>();

    mergedCatalog.forEach((program) => {
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
  }, [mergedCatalog, selectedIdSet]);
  const controlDisabled = disabled || loading || Boolean(error);
  const normalizedDraft = selectionDraft.trim().replaceAll(/\s+/g, " ").toLowerCase();
  const exactCatalogMatch = mergedCatalog.find(
    (program) => program.name.trim().replaceAll(/\s+/g, " ").toLowerCase() === normalizedDraft,
  );
  const programFamilies = useMemo(() => {
    const families = new Map<string, string>();
    mergedCatalog.forEach((program) => {
      if (program.programFamilyId && program.programFamilyName) {
        families.set(program.programFamilyId, program.programFamilyName);
      }
    });
    return Array.from(families, ([familyId, familyName]) => ({ id: familyId, name: familyName }))
      .sort((left, right) => left.name.localeCompare(right.name));
  }, [mergedCatalog]);

  useEffect(() => {
    if (!canCreateCatalogProgram || normalizedDraft.length === 0 || exactCatalogMatch) {
      setNearMatches([]);
      setCheckingNearMatches(false);
      return;
    }
    let active = true;
    setCheckingNearMatches(true);
    const timeoutId = globalThis.setTimeout(() => {
      void findSimilarCoursePrograms(selectionDraft.trim())
        .then((matches) => {
          if (active) setNearMatches(matches);
        })
        .catch(() => {
          if (active) setNearMatches([]);
        })
        .finally(() => {
          if (active) setCheckingNearMatches(false);
        });
    }, 250);
    return () => {
      active = false;
      globalThis.clearTimeout(timeoutId);
    };
  }, [canCreateCatalogProgram, exactCatalogMatch, normalizedDraft, selectionDraft]);

  const handleSelect = (programName: string) => {
    setSelectionDraft(programName);
    const selectedProgram = availablePrograms.find((program) => program.name === programName);
    if (!selectedProgram) {
      return;
    }
    onChange([...selectedIds, selectedProgram.id]);
    setSelectionDraft("");
  };

  const selectProgram = (program: CourseProgramCatalogItem) => {
    if (!selectedIdSet.has(program.id)) {
      onChange([...selectedIds, program.id]);
    }
    setSelectionDraft("");
    setNearMatches([]);
    setCreateModalOpen(false);
  };

  const handleCreate = async () => {
    if (creating) return;
    setCreating(true);
    setCreateError(null);
    setDuplicateExisting(null);
    try {
      const createdProgram = await createCourseProgram({
        name: selectionDraft,
        programFamilyId: programFamilyId || null,
        examGoalSlug: examGoalSlug ? examGoalSlug as "ale" | "pnle" | "let" | "cpale" : null,
      });
      setCreatedPrograms((current) => [...current, createdProgram]);
      onCatalogProgramCreated?.(createdProgram);
      selectProgram(createdProgram);
      setProgramFamilyId("");
      setExamGoalSlug("");
    } catch (creationError) {
      if (creationError instanceof ApiRequestError
        && creationError.code === "COURSE_PROGRAM_CATALOG_NAME_CONFLICT") {
        const existing = mergedCatalog.find((program) => (
          program.name.trim().replaceAll(/\s+/g, " ").toLowerCase() === normalizedDraft
          || program.name === creationError.details
        )) ?? nearMatches.find((program) => program.name === creationError.details);
        setDuplicateExisting(existing ?? null);
        setCreateError(existing
          ? `“${existing.name}” already exists. Select the existing program instead.`
          : creationError.message);
      } else {
        setCreateError(creationError instanceof Error
          ? creationError.message
          : "Could not add the Course / Program to the catalog.");
      }
    } finally {
      setCreating(false);
    }
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
        onInputValueChange={setSelectionDraft}
        placeholder={loading ? "Loading course programs..." : "Add a course program"}
        disabled={controlDisabled}
        context="note"
        allowCustom={false}
        // The caller already explains this field above the input, and the default helper says
        // "Choose or type" — which is wrong here, since curators select from the catalog only.
        helperText={null}
        ariaLabel="Add a course or program"
      />
      {canCreateCatalogProgram && normalizedDraft.length > 0 && !exactCatalogMatch && !controlDisabled ? (
        <div className="space-y-2 rounded-lg border border-border bg-muted/20 p-3 text-sm">
          {checkingNearMatches ? <p className="text-xs text-foreground/60">Checking for similar programs...</p> : null}
          {!checkingNearMatches && nearMatches.length > 0 ? (
            <div className="space-y-2">
              <p className="text-xs font-medium text-foreground/70">Similar catalog programs</p>
              <div className="flex flex-wrap gap-2">
                {nearMatches.map((program) => (
                  <Button key={program.id} type="button" size="sm" variant="outline" onClick={() => selectProgram(program)}>
                    Select {program.name}
                  </Button>
                ))}
              </div>
            </div>
          ) : null}
          {!checkingNearMatches ? (
            <Button type="button" size="sm" variant="outline" onClick={() => {
              setCreateError(null);
              setCreateModalOpen(true);
            }}>
              {`Add “${selectionDraft.trim()}” to the catalog`}
            </Button>
          ) : null}
        </div>
      ) : null}
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
      <AppModal
        isOpen={createModalOpen}
        onClose={() => {
          if (!creating) setCreateModalOpen(false);
        }}
        title="Add Course / Program"
        description={`Confirm adding “${selectionDraft.trim()}” to the shared catalog.`}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" disabled={creating} onClick={() => setCreateModalOpen(false)}>Cancel</Button>
            <Button type="button" loading={creating} loadingText="Adding..." onClick={() => void handleCreate()}>Add and select</Button>
          </div>
        )}
      >
        <div className="space-y-4">
          <div className="space-y-2">
            <label htmlFor={`${id}-new-program-family`} className="text-sm font-medium text-foreground">Program Family (optional)</label>
            <select
              id={`${id}-new-program-family`}
              value={programFamilyId}
              onChange={(event) => setProgramFamilyId(event.target.value)}
              disabled={creating}
              className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground"
            >
              <option value="">No family</option>
              {programFamilies.map((family) => <option key={family.id} value={family.id}>{family.name}</option>)}
            </select>
            <p className="text-xs text-foreground/60">Family membership makes this program part of that family’s authoring expansion.</p>
          </div>
          <div className="space-y-2">
            <label htmlFor={`${id}-new-program-exam-goal`} className="text-sm font-medium text-foreground">Exam goal (optional)</label>
            <select
              id={`${id}-new-program-exam-goal`}
              value={examGoalSlug}
              onChange={(event) => setExamGoalSlug(event.target.value)}
              disabled={creating}
              className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground"
            >
              <option value="">No exam goal</option>
              <option value="ale">ALE</option><option value="pnle">PNLE</option><option value="let">LET</option><option value="cpale">CPALE</option>
            </select>
          </div>
          {createError ? <p role="alert" className="text-sm text-red-600 dark:text-red-400">{createError}</p> : null}
          {duplicateExisting ? (
            <Button type="button" size="sm" variant="outline" onClick={() => selectProgram(duplicateExisting)}>
              Select {duplicateExisting.name}
            </Button>
          ) : null}
        </div>
      </AppModal>
    </div>
  );
}
