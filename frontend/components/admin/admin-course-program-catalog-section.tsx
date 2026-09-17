"use client";

import { useEffect, useState } from "react";
import { CourseProgramCreateModal } from "@/components/metadata/course-program-create-modal";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { CatalogMultiSelect } from "@/components/ui/catalog-multi-select";
import { getCourseProgramCatalog, listProgramFamilies, updateCourseProgram,
  type CourseProgramCatalogItem, type ProgramFamily } from "@/lib/api";

export function AdminCourseProgramCatalogSection() {
  const [catalog, setCatalog] = useState<CourseProgramCatalogItem[]>([]);
  const [families, setFamilies] = useState<ProgramFamily[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [familiesError, setFamiliesError] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [editingProgram, setEditingProgram] = useState<CourseProgramCatalogItem | null>(null);
  const [editFamilyIds, setEditFamilyIds] = useState<string[]>([]);
  const [savingEdit, setSavingEdit] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setLoadError(null);
    const [catalogResult, familiesResult] = await Promise.allSettled([
      getCourseProgramCatalog(), listProgramFamilies(),
    ]);
    if (catalogResult.status === "fulfilled") setCatalog(catalogResult.value);
    else setLoadError(catalogResult.reason instanceof Error
      ? catalogResult.reason.message : "Could not load the course program catalog.");
    if (familiesResult.status === "fulfilled") {
      setFamilies(familiesResult.value);
      setFamiliesError(null);
    } else {
      setFamilies([]);
      setFamiliesError("Program Families could not be loaded. You can still create or edit a program without changing families.");
    }
    setLoading(false);
  };

  useEffect(() => { void load(); }, []);

  const openEdit = (program: CourseProgramCatalogItem) => {
    setEditingProgram(program);
    setEditFamilyIds(program.programFamilies.map((family) => family.id));
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
    } finally { setSavingEdit(false); }
  };

  const familyChips = (program: CourseProgramCatalogItem) => program.programFamilies.length === 0
    ? <span className="text-foreground/60">—</span>
    : <div className="flex flex-wrap gap-1.5">{program.programFamilies.map((family) => (
      <span key={family.id} className="rounded-full border border-border bg-muted/30 px-2 py-1 text-xs">{family.name}</span>
    ))}</div>;

  return (
    <section className="space-y-3" aria-labelledby="course-programs-heading">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div><h2 id="course-programs-heading" className="text-lg font-semibold">Course / Programs</h2>
          <p className="text-sm text-foreground/65">Manage the programs available when curating notes.</p></div>
        <Button type="button" onClick={() => setCreateOpen(true)}>+ New program</Button>
      </div>
      {familiesError ? <p className="text-sm text-foreground/65">{familiesError}</p> : null}
      <Card className="overflow-hidden">
        {loading ? <p className="p-5 text-sm text-foreground/65">Loading catalog...</p> : loadError ? (
          <div className="space-y-3 p-5 text-sm text-red-600"><p>{loadError}</p><Button type="button" size="sm" variant="outline" onClick={() => void load()}>Retry</Button></div>
        ) : <>
          <div className="hidden overflow-x-auto sm:block"><table className="min-w-full text-sm">
            <thead className="bg-muted/40 text-left text-foreground/60"><tr><th className="px-4 py-3 font-medium">Course / Program</th><th className="px-4 py-3 font-medium">Program Families</th><th className="px-4 py-3 font-medium">Actions</th></tr></thead>
            <tbody>{catalog.map((program) => <tr key={program.id} className="border-t border-border/60"><td className="px-4 py-3">{program.name}</td><td className="px-4 py-3 text-foreground/70">{familyChips(program)}</td><td className="px-4 py-3"><Button type="button" size="sm" variant="outline" onClick={() => openEdit(program)}>Edit</Button></td></tr>)}</tbody>
          </table></div>
          <div className="divide-y divide-border/60 sm:hidden">{catalog.map((program) => (
            <article key={program.id} className="space-y-3 p-4"><h3 className="font-medium">{program.name}</h3>{familyChips(program)}<Button type="button" variant="outline" className="w-full" onClick={() => openEdit(program)}>Edit</Button></article>
          ))}</div>
        </>}
      </Card>
      <CourseProgramCreateModal isOpen={createOpen} onClose={() => setCreateOpen(false)}
        families={families} familiesError={familiesError} knownPrograms={catalog}
        onSelectExisting={() => setCreateOpen(false)}
        onCreated={(created) => setCatalog((current) => [...current, created]
          .sort((left, right) => left.name.localeCompare(right.name)))} />
      <AppModal isOpen={editingProgram !== null}
        onClose={() => { if (!savingEdit) setEditingProgram(null); }} title="Edit Program Families"
        actions={<div className="flex flex-col gap-2 sm:flex-row sm:justify-end"><Button type="button" variant="outline" disabled={savingEdit} onClick={() => setEditingProgram(null)}>Cancel</Button><Button type="button" loading={savingEdit} loadingText="Saving..." onClick={() => void saveEdit()}>Save changes</Button></div>}>
        <div className="space-y-4">
          <div><p className="text-xs text-foreground/60">Course / Program</p><p className="font-medium">{editingProgram?.name}</p></div>
          <CatalogMultiSelect id="edit-program-families" label="Program Families" items={families}
            selectedIds={editFamilyIds} onChange={setEditFamilyIds} selectedSummary="chips"
            disabled={savingEdit} searchPlaceholder="Search or select families…" />
          {familiesError ? <p className="text-xs text-foreground/60">{familiesError}</p> : null}
          {editError ? <p role="alert" className="text-sm text-red-600 dark:text-red-400">{editError}</p> : null}
        </div>
      </AppModal>
    </section>
  );
}
