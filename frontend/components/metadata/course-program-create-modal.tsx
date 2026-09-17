"use client";

import { useEffect, useMemo, useState } from "react";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { CatalogMultiSelect } from "@/components/ui/catalog-multi-select";
import {
  ApiRequestError,
  createCourseProgram,
  findSimilarCoursePrograms,
  type CourseProgramCatalogItem,
  type ProgramFamily,
} from "@/lib/api";

type CourseProgramCreateModalProps = {
  isOpen: boolean;
  onClose: () => void;
  initialName?: string;
  families: readonly ProgramFamily[];
  familiesError?: string | null;
  knownPrograms?: readonly CourseProgramCatalogItem[];
  onCreated: (program: CourseProgramCatalogItem) => void;
  onSelectExisting?: (program: CourseProgramCatalogItem) => void;
  title?: string;
  submitLabel?: string;
};

export function CourseProgramCreateModal({
  isOpen,
  onClose,
  initialName = "",
  families,
  familiesError = null,
  knownPrograms = [],
  onCreated,
  onSelectExisting,
  title = "New Course / Program",
  submitLabel = "Create program",
}: Readonly<CourseProgramCreateModalProps>) {
  const [name, setName] = useState(initialName);
  const [programFamilyIds, setProgramFamilyIds] = useState<string[]>([]);
  const [examGoalSlug, setExamGoalSlug] = useState("");
  const [nearMatches, setNearMatches] = useState<CourseProgramCatalogItem[]>([]);
  const [checkingNearMatches, setCheckingNearMatches] = useState(false);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [duplicateExisting, setDuplicateExisting] = useState<CourseProgramCatalogItem | null>(null);
  const normalizedName = useMemo(
    () => name.trim().replaceAll(/\s+/g, " ").toLowerCase(),
    [name],
  );

  useEffect(() => {
    if (!isOpen) return;
    setName(initialName);
    setProgramFamilyIds([]);
    setExamGoalSlug("");
    setError(null);
    setDuplicateExisting(null);
  }, [initialName, isOpen]);

  useEffect(() => {
    if (!isOpen || !normalizedName) {
      setNearMatches([]);
      setCheckingNearMatches(false);
      return;
    }
    let active = true;
    setCheckingNearMatches(true);
    const timeoutId = globalThis.setTimeout(() => {
      void findSimilarCoursePrograms(name.trim())
        .then((matches) => { if (active) setNearMatches(matches.filter((item) => item.isActive !== false)); })
        .catch(() => { if (active) setNearMatches([]); })
        .finally(() => { if (active) setCheckingNearMatches(false); });
    }, 250);
    return () => { active = false; globalThis.clearTimeout(timeoutId); };
  }, [isOpen, name, normalizedName]);

  const create = async () => {
    if (!name.trim() || creating) return;
    setCreating(true);
    setError(null);
    setDuplicateExisting(null);
    try {
      const created = await createCourseProgram({
        name,
        programFamilyIds,
        examGoalSlug: examGoalSlug ? examGoalSlug as "ale" | "pnle" | "let" | "cpale" : null,
      });
      onCreated(created);
      onClose();
    } catch (creationError) {
      if (creationError instanceof ApiRequestError
        && creationError.code === "COURSE_PROGRAM_CATALOG_NAME_CONFLICT") {
        const existing = knownPrograms.find((program) => (
          program.name.trim().replaceAll(/\s+/g, " ").toLowerCase() === normalizedName
          || program.name === creationError.details
        )) ?? nearMatches.find((program) => program.name === creationError.details) ?? null;
        setDuplicateExisting(existing?.isActive === false ? null : existing);
        setError(existing
          ? `“${existing.name}” already exists. Select the existing program instead.`
          : creationError.message);
      } else {
        setError(creationError instanceof Error
          ? creationError.message
          : "Could not add the Course / Program to the catalog.");
      }
    } finally {
      setCreating(false);
    }
  };

  return (
    <AppModal
      isOpen={isOpen}
      onClose={() => { if (!creating) onClose(); }}
      title={title}
      actions={(
        <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" disabled={creating} onClick={onClose}>Cancel</Button>
          <Button type="button" loading={creating} loadingText="Creating..." disabled={!name.trim()} onClick={() => void create()}>
            {submitLabel}
          </Button>
        </div>
      )}
    >
      <div className="space-y-4">
        <div className="space-y-2">
          <label htmlFor="new-course-program-name" className="text-sm font-medium">Name</label>
          <input id="new-course-program-name" value={name} maxLength={120}
            onChange={(event) => setName(event.target.value)} disabled={creating}
            className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm" />
          {checkingNearMatches ? <p className="text-xs text-foreground/60">Checking for similar programs...</p> : null}
          {!checkingNearMatches && nearMatches.length > 0 ? (
            <div className="rounded-lg border border-amber-300 bg-amber-50/70 p-3 text-sm text-amber-900 dark:border-amber-700 dark:bg-amber-950/30 dark:text-amber-100">
              <p className="font-medium">Similar catalog programs already exist:</p>
              <div className="mt-2 flex flex-wrap gap-2">
                {nearMatches.map((program) => (
                  <Button key={program.id} type="button" size="sm" variant="outline"
                    onClick={() => onSelectExisting?.(program)}>Select {program.name}</Button>
                ))}
              </div>
            </div>
          ) : null}
        </div>
        <CatalogMultiSelect id="new-course-program-families" label="Program Families (optional)"
          items={families} selectedIds={programFamilyIds} onChange={setProgramFamilyIds}
          disabled={creating} selectedSummary="chips" searchPlaceholder="Search or select families…"
          emptyHint="A program can be created without a family." />
        {familiesError ? <p className="text-xs text-foreground/60">{familiesError}</p> : null}
        <div className="space-y-2">
          <label htmlFor="new-course-program-exam-goal" className="text-sm font-medium">Exam goal (optional)</label>
          <select id="new-course-program-exam-goal" value={examGoalSlug}
            onChange={(event) => setExamGoalSlug(event.target.value)} disabled={creating}
            className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm">
            <option value="">No exam goal</option>
            <option value="ale">ALE</option><option value="pnle">PNLE</option>
            <option value="let">LET</option><option value="cpale">CPALE</option>
          </select>
        </div>
        {error ? <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
        {duplicateExisting && onSelectExisting ? (
          <Button type="button" size="sm" variant="outline" onClick={() => onSelectExisting(duplicateExisting)}>
            Select {duplicateExisting.name}
          </Button>
        ) : null}
      </div>
    </AppModal>
  );
}
