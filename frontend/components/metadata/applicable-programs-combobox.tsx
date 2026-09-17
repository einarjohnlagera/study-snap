"use client";

import { useMemo, useState } from "react";
import { CourseProgramCombobox } from "@/components/metadata/course-program-combobox";
import { CourseProgramCreateModal } from "@/components/metadata/course-program-create-modal";
import { Button } from "@/components/ui/button";
import {
  listProgramFamilies,
  type CourseProgramCatalogItem,
  type ProgramFamily,
} from "@/lib/api";

type ApplicableProgramsComboboxProps = {
  id: string;
  catalog: CatalogProgram[];
  selectedIds: string[];
  onChange: (selectedIds: string[]) => void;
  loading?: boolean;
  error?: string | null;
  onRetry?: () => void;
  disabled?: boolean;
  canCreateCatalogProgram?: boolean;
  /**
   * The author's own profile programme, used only to explain an empty selection (C8). When it is set
   * but absent from the shared catalog, "No course programs selected." is not just terse -- it is
   * mystifying, because the author has a programme and cannot see why it counts for nothing here.
   */
  profileCourseProgram?: string | null;
  onCatalogProgramCreated?: (program: CourseProgramCatalogItem) => void;
};

// The API parser always supplies programFamilies. Keeping the scalar fallback at this component seam
// lets an independently deployed old backend remain usable during the rollout window.
type CatalogProgram = Omit<CourseProgramCatalogItem, "programFamilies"> & {
  programFamilies?: CourseProgramCatalogItem["programFamilies"];
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
  profileCourseProgram = null,
}: Readonly<ApplicableProgramsComboboxProps>) {
  const [selectionDraft, setSelectionDraft] = useState("");
  const [createdPrograms, setCreatedPrograms] = useState<CourseProgramCatalogItem[]>([]);
  const [createModalOpen, setCreateModalOpen] = useState(false);

  // Only names the programme when it is genuinely absent from the catalog. A profile programme that IS
  // in the catalog needs no explanation -- the author simply has not picked anything yet.
  const profileOffCatalogName = useMemo(() => {
    const trimmed = profileCourseProgram?.trim() ?? "";
    if (!trimmed) {
      return null;
    }
    const inCatalog = catalog.some((item) => item.name.trim().toLowerCase() === trimmed.toLowerCase());
    return inCatalog ? null : trimmed;
  }, [catalog, profileCourseProgram]);
  const [programFamilies, setProgramFamilies] = useState<ProgramFamily[] | null>(null);
  const [programFamiliesError, setProgramFamiliesError] = useState(false);
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
    () => mergedCatalog.filter((program) => (
      !selectedIdSet.has(program.id) && program.isActive !== false
    )),
    [mergedCatalog, selectedIdSet],
  );
  const availableProgramFamilies = useMemo(() => {
    const families = new Map<string, Omit<AvailableProgramFamily, "unselectedCount">>();

    mergedCatalog.forEach((program) => {
      if (program.isActive === false) return;
      const memberships = program.programFamilies ?? (
        program.programFamilyId && program.programFamilyName
          ? [{ id: program.programFamilyId, name: program.programFamilyName }]
          : []
      );
      memberships.forEach((membership) => {
        const family = families.get(membership.id);
        if (family) {
          family.memberIds.push(program.id);
          return;
        }
        families.set(membership.id, {
          id: membership.id,
          name: membership.name,
          memberIds: [program.id],
        });
      });
    });

    return Array.from(families.values())
      .map((family) => ({
        ...family,
        unselectedCount: family.memberIds.filter((id) => !selectedIdSet.has(id)).length,
      }));
  }, [mergedCatalog, selectedIdSet]);
  const controlDisabled = disabled || loading || Boolean(error);
  const normalizedDraft = selectionDraft.trim().replaceAll(/\s+/g, " ").toLowerCase();
  const exactCatalogMatch = mergedCatalog.find(
    (program) => program.name.trim().replaceAll(/\s+/g, " ").toLowerCase() === normalizedDraft,
  );
  const handleSelect = (programName: string) => {
    setSelectionDraft(programName);
    const selectedProgram = availablePrograms.find((program) => program.name === programName);
    if (!selectedProgram) {
      return;
    }
    onChange([...selectedIds, selectedProgram.id]);
    setSelectionDraft("");
  };

  const selectProgram = (program: CatalogProgram) => {
    if (!selectedIdSet.has(program.id) && program.isActive !== false) {
      onChange([...selectedIds, program.id]);
    }
    setSelectionDraft("");
    setCreateModalOpen(false);
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
          <Button type="button" size="sm" variant="outline" onClick={() => {
              setCreateModalOpen(true);
              if (programFamilies === null) {
                setProgramFamiliesError(false);
                setProgramFamilies([]);
                void listProgramFamilies()
                  .then(setProgramFamilies)
                  .catch(() => {
                    setProgramFamilies([]);
                    setProgramFamiliesError(true);
                  });
              }
            }}>
            {`Add “${selectionDraft.trim()}” to the catalog`}
          </Button>
        </div>
      ) : null}
      {!canCreateCatalogProgram && normalizedDraft.length > 0 && !exactCatalogMatch && !controlDisabled ? (
        <p className="text-xs text-foreground/60">
          Can&apos;t find your program? You can still continue without selecting one.
        </p>
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
          {availableProgramFamilies.map((family) => {
            if (family.unselectedCount === 0) {
              return (
                <span
                  key={family.id}
                  aria-label={`${family.name} — all ${family.memberIds.length} programs added`}
                  className="inline-flex h-9 items-center rounded-md border border-border bg-muted px-3 text-sm text-foreground/70"
                >
                  ✓ {family.name} · {family.memberIds.length}
                </span>
              );
            }
            const noneSelected = family.unselectedCount === family.memberIds.length;
            return (
              <Button
                key={family.id}
                type="button"
                size="sm"
                variant="outline"
                onClick={() => handleFamilyExpansion(family.memberIds)}
              >
                {family.name} · {noneSelected
                  ? family.memberIds.length
                  : `${family.unselectedCount} remaining`}
              </Button>
            );
          })}
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
            <span className="text-xs text-foreground/55">
              {profileOffCatalogName
                ? `No course programs selected. Your profile programme, ${profileOffCatalogName}, is not in the shared catalog, so it cannot be used here — pick the programs this note should appear under.`
                : "No course programs selected."}
            </span>
          )}
        </div>
      ) : null}
      {/*
        ⚠️ THIS TEXT DELIBERATELY DOES NOT EXPLAIN THE RESOLVER. It used to say "only a single program
        can inform the writing domain, and Domain Context overrides it" -- true, but it describes
        backend mechanics a curator cannot act on, and it invited the reading that picking one program
        is how you steer the writing. Keep the CONCEPTUAL separation (discovery vs. authoring); do not
        reintroduce resolution rules here.
      */}
      <p className="text-xs text-foreground/60">
        Choose the programs this note genuinely applies to — they decide who finds it, never how it is
        written. Use a program family to quickly add related programs.
      </p>
      <CourseProgramCreateModal
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        initialName={selectionDraft.trim()}
        families={programFamilies ?? []}
        familiesError={programFamiliesError ? "Program Families could not be loaded." : null}
        knownPrograms={mergedCatalog as CourseProgramCatalogItem[]}
        title="Add Course / Program"
        submitLabel="Add and select"
        onCreated={(createdProgram) => {
          setCreatedPrograms((current) => [...current, createdProgram]);
          onCatalogProgramCreated?.(createdProgram);
          selectProgram(createdProgram);
        }}
        onSelectExisting={selectProgram}
      />
    </div>
  );
}
