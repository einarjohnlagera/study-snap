"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { AppModal } from "@/components/ui/app-modal";
import {
  ApiRequestError,
  createCourseProgram,
  createProgramFamily,
  findSimilarCoursePrograms,
  getCourseProgramCatalog,
  listProgramFamilies,
  updateCourseProgram,
  type CourseProgramCatalogItem,
  type ProgramFamily,
} from "@/lib/api";

export function AdminCourseProgramCatalogSection() {
  const [catalog, setCatalog] = useState<CourseProgramCatalogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [programFamilyId, setProgramFamilyId] = useState("");
  const [examGoalSlug, setExamGoalSlug] = useState("");
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [existingName, setExistingName] = useState<string | null>(null);
  const [nearMatches, setNearMatches] = useState<CourseProgramCatalogItem[]>([]);
  const [checkingNearMatches, setCheckingNearMatches] = useState(false);

  // Membership pickers use the families endpoint so an empty family remains assignable. Expansion
  // shortcuts separately derive their member lists from the catalog's programFamilies arrays.
  const [families, setFamilies] = useState<ProgramFamily[]>([]);
  const [familyName, setFamilyName] = useState("");
  const [creatingFamily, setCreatingFamily] = useState(false);
  const [familyError, setFamilyError] = useState<string | null>(null);
  const [editingProgram, setEditingProgram] = useState<CourseProgramCatalogItem | null>(null);
  const [editFamilyIds, setEditFamilyIds] = useState<string[]>([]);
  const [savingEdit, setSavingEdit] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const [catalogResult, familiesResult] = await Promise.allSettled([
        getCourseProgramCatalog(),
        listProgramFamilies(),
      ]);
      if (catalogResult.status === "rejected") throw catalogResult.reason;
      setCatalog(catalogResult.value);
      if (familiesResult.status === "fulfilled") {
        setFamilies(familiesResult.value);
        setFamilyError(null);
      } else {
        setFamilies([]);
        setFamilyError("Program Families could not be loaded.");
      }
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : "Could not load the course program catalog.");
    } finally {
      setLoading(false);
    }
  };

  const addFamily = async () => {
    const trimmed = familyName.trim();
    if (!trimmed || creatingFamily) return;
    setCreatingFamily(true);
    setFamilyError(null);
    try {
      const created = await createProgramFamily(trimmed);
      setFamilies((current) => [...current, created].sort((left, right) => left.name.localeCompare(right.name)));
      // Select it immediately: the curator almost always creates a family in order to put the program
      // they are adding into it.
      setProgramFamilyId(created.id);
      setFamilyName("");
    } catch (error) {
      setFamilyError(error instanceof Error ? error.message : "Could not add the Program Family.");
    } finally {
      setCreatingFamily(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  useEffect(() => {
    const trimmedName = name.trim();
    if (!trimmedName) {
      setNearMatches([]);
      setCheckingNearMatches(false);
      return;
    }
    let active = true;
    setCheckingNearMatches(true);
    const timeoutId = globalThis.setTimeout(() => {
      void findSimilarCoursePrograms(trimmedName)
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
  }, [name]);

  const create = async () => {
    if (!name.trim() || creating) return;
    setCreating(true);
    setCreateError(null);
    setExistingName(null);
    try {
      const created = await createCourseProgram({
        name,
        programFamilyId: programFamilyId || null,
        examGoalSlug: examGoalSlug ? examGoalSlug as "ale" | "pnle" | "let" | "cpale" : null,
      });
      setCatalog((current) => [...current, created].sort((left, right) => left.name.localeCompare(right.name)));
      setName("");
      setProgramFamilyId("");
      setExamGoalSlug("");
    } catch (error) {
      if (error instanceof ApiRequestError && error.code === "COURSE_PROGRAM_CATALOG_NAME_CONFLICT") {
        setExistingName(error.details);
        setCreateError(error.message);
      } else {
        setCreateError(error instanceof Error ? error.message : "Could not add the Course / Program.");
      }
    } finally {
      setCreating(false);
    }
  };

  const openEdit = (program: CourseProgramCatalogItem) => {
    setEditingProgram(program);
    setEditFamilyIds((program.programFamilies ?? (
      program.programFamilyId && program.programFamilyName
        ? [{ id: program.programFamilyId, name: program.programFamilyName }]
        : []
    )).map((family) => family.id));
    setEditError(null);
  };

  const saveEdit = async () => {
    if (!editingProgram || savingEdit) return;
    setSavingEdit(true);
    setEditError(null);
    try {
      const updated = await updateCourseProgram(editingProgram.id, { programFamilyIds: editFamilyIds });
      setCatalog((current) => current.map((program) => program.id === updated.id ? updated : program));
      setEditingProgram(null);
    } catch (error) {
      setEditError(error instanceof Error ? error.message : "Could not update the Course / Program.");
    } finally {
      setSavingEdit(false);
    }
  };

  const familyChips = (program: CourseProgramCatalogItem) => (
    (program.programFamilies ?? []).length === 0 ? <span className="text-foreground/60">—</span> : (
      <div className="flex flex-wrap gap-1.5">
        {(program.programFamilies ?? []).map((family) => (
          <span key={family.id} className="rounded-full border border-border bg-muted/30 px-2 py-1 text-xs">{family.name}</span>
        ))}
      </div>
    )
  );

  return (
    <section className="space-y-3">
      <div>
        <h2 className="text-lg font-semibold text-foreground">Course / Program Catalog</h2>
        <p className="text-sm text-foreground/65">Add a program only when a canonical note is genuinely applicable to it.</p>
      </div>
      <Card className="space-y-5 p-5">
        <div className="grid gap-4 md:grid-cols-3">
          <div className="space-y-2">
            <label htmlFor="catalog-program-name" className="text-sm font-medium text-foreground">Name</label>
            <input
              id="catalog-program-name"
              value={name}
              maxLength={120}
              onChange={(event) => setName(event.target.value)}
              disabled={creating}
              className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground"
            />
          </div>
          <div className="space-y-2">
            <label htmlFor="catalog-program-family" className="text-sm font-medium text-foreground">Program Family (optional)</label>
            <select id="catalog-program-family" value={programFamilyId} onChange={(event) => setProgramFamilyId(event.target.value)} disabled={creating} className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground">
              <option value="">No family</option>
              {families.map((family) => <option key={family.id} value={family.id}>{family.name}</option>)}
            </select>
          </div>
          <div className="space-y-2">
            <label htmlFor="catalog-program-exam-goal" className="text-sm font-medium text-foreground">Exam goal (optional)</label>
            <select id="catalog-program-exam-goal" value={examGoalSlug} onChange={(event) => setExamGoalSlug(event.target.value)} disabled={creating} className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground">
              <option value="">No exam goal</option>
              <option value="ale">ALE</option><option value="pnle">PNLE</option><option value="let">LET</option><option value="cpale">CPALE</option>
            </select>
          </div>
        </div>
        <p className="text-xs text-foreground/60">Assigning a family makes the new program participate in that family’s authoring expansion.</p>
        <div className="space-y-2 rounded-lg border border-border/60 bg-muted/20 p-3">
          <label htmlFor="catalog-family-name" className="text-sm font-medium text-foreground">New Program Family</label>
          <p className="text-xs text-foreground/60">
            A family starts empty — create it here, then assign programs to it above. Before this existed, a new family needed a database migration.
          </p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <input
              id="catalog-family-name"
              value={familyName}
              maxLength={120}
              onChange={(event) => setFamilyName(event.target.value)}
              disabled={creatingFamily}
              placeholder="e.g. Health Sciences"
              className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground"
            />
            <Button
              type="button"
              variant="outline"
              onClick={() => void addFamily()}
              disabled={!familyName.trim()}
              loading={creatingFamily}
              loadingText="Adding..."
            >
              Add family
            </Button>
          </div>
          {familyError ? <p role="alert" className="text-sm text-red-600 dark:text-red-400">{familyError}</p> : null}
        </div>
        {checkingNearMatches ? <p className="text-xs text-foreground/60">Checking for similar programs...</p> : null}
        {!checkingNearMatches && nearMatches.length > 0 ? (
          <div className="rounded-lg border border-amber-300 bg-amber-50/70 p-3 text-sm text-amber-900 dark:border-amber-700 dark:bg-amber-950/30 dark:text-amber-100">
            <p className="font-medium">Similar catalog programs already exist:</p>
            <p>{nearMatches.map((program) => program.name).join(", ")}</p>
          </div>
        ) : null}
        {createError ? <p role="alert" className="text-sm text-red-600 dark:text-red-400">{createError}</p> : null}
        {existingName ? <p className="text-sm text-foreground/70">Use the existing catalog program: <strong>{existingName}</strong>.</p> : null}
        <Button type="button" onClick={() => void create()} disabled={!name.trim()} loading={creating} loadingText="Adding...">Add to catalog</Button>
      </Card>
      <Card className="overflow-hidden">
        {loading ? <p className="p-5 text-sm text-foreground/65">Loading catalog...</p> : loadError ? (
          <div className="space-y-3 p-5 text-sm text-red-600"><p>{loadError}</p><Button type="button" size="sm" variant="outline" onClick={() => void load()}>Retry</Button></div>
        ) : (
          <>
          <div className="hidden overflow-x-auto sm:block">
            <table className="min-w-full text-sm">
              <thead className="bg-muted/40 text-left text-foreground/60"><tr><th className="px-4 py-3 font-medium">Course / Program</th><th className="px-4 py-3 font-medium">Program Families</th><th className="px-4 py-3 font-medium">Actions</th></tr></thead>
              <tbody>{catalog.map((program) => <tr key={program.id} className="border-t border-border/60"><td className="px-4 py-3">{program.name}</td><td className="px-4 py-3 text-foreground/70">{familyChips(program)}</td><td className="px-4 py-3"><Button type="button" size="sm" variant="outline" onClick={() => openEdit(program)}>Edit</Button></td></tr>)}</tbody>
            </table>
          </div>
          <div className="divide-y divide-border/60 sm:hidden">
            {catalog.map((program) => (
              <article key={program.id} className="space-y-3 p-4">
                <h3 className="font-medium">{program.name}</h3>
                {familyChips(program)}
                <Button type="button" variant="outline" className="w-full" onClick={() => openEdit(program)}>Edit</Button>
              </article>
            ))}
          </div>
          </>
        )}
      </Card>
      <AppModal
        isOpen={editingProgram !== null}
        onClose={() => { if (!savingEdit) setEditingProgram(null); }}
        title="Edit Program Families"
        actions={<div className="flex flex-col gap-2 sm:flex-row sm:justify-end"><Button type="button" variant="outline" disabled={savingEdit} onClick={() => setEditingProgram(null)}>Cancel</Button><Button type="button" loading={savingEdit} loadingText="Saving..." onClick={() => void saveEdit()}>Save changes</Button></div>}
      >
        <div className="space-y-4">
          <div><p className="text-xs text-foreground/60">Course / Program</p><p className="font-medium">{editingProgram?.name}</p></div>
          <div className="space-y-2">
            <label htmlFor="edit-program-families" className="text-sm font-medium">Program Families</label>
            <select id="edit-program-families" multiple value={editFamilyIds}
              onChange={(event) => setEditFamilyIds(Array.from(event.target.selectedOptions, (option) => option.value))}
              disabled={savingEdit} className="min-h-28 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm">
              {families.map((family) => <option key={family.id} value={family.id}>{family.name}</option>)}
            </select>
            <div className="flex flex-wrap gap-2" aria-label="Selected Program Families">
              {families.filter((family) => editFamilyIds.includes(family.id)).map((family) => (
                <span key={family.id} className="inline-flex items-center gap-1 rounded-full border border-border px-2.5 py-1 text-xs">{family.name}<button type="button" aria-label={`Remove ${family.name}`} onClick={() => setEditFamilyIds((current) => current.filter((id) => id !== family.id))}>×</button></span>
              ))}
            </div>
          </div>
          {editError ? <p role="alert" className="text-sm text-red-600 dark:text-red-400">{editError}</p> : null}
        </div>
      </AppModal>
    </section>
  );
}
