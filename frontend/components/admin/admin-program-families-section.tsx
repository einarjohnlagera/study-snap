"use client";

import { useEffect, useMemo, useState } from "react";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { CatalogMultiSelect } from "@/components/ui/catalog-multi-select";
import { ApiRequestError, createProgramFamily, getCourseProgramCatalog, listProgramFamilies,
  updateProgramFamily, type CourseProgramCatalogItem, type ProgramFamily } from "@/lib/api";

type FamilyDraft = { family: ProgramFamily | null; name: string; programIds: string[]; membershipDirty: boolean };

export function AdminProgramFamiliesSection() {
  const [catalog, setCatalog] = useState<CourseProgramCatalogItem[]>([]);
  const [families, setFamilies] = useState<ProgramFamily[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const [draft, setDraft] = useState<FamilyDraft | null>(null);
  const [saving, setSaving] = useState(false);
  const [nameError, setNameError] = useState<string | null>(null);
  const [operationError, setOperationError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    const [catalogResult, familiesResult] = await Promise.allSettled([
      getCourseProgramCatalog(), listProgramFamilies(),
    ]);
    if (catalogResult.status === "fulfilled") {
      setCatalog(catalogResult.value);
      setCatalogError(null);
    } else {
      setCatalog([]);
      setCatalogError("Course / Programs could not be loaded. You can still save a family with the available selection.");
    }
    if (familiesResult.status === "fulfilled") {
      setFamilies([...familiesResult.value].sort((left, right) => left.name.localeCompare(right.name)));
      setLoadError(null);
    } else {
      setFamilies([]);
      setLoadError("Program Families could not be loaded.");
    }
    setLoading(false);
  };
  useEffect(() => { void load(); }, []);

  const memberIdsByFamily = useMemo(() => {
    const map = new Map<string, string[]>();
    catalog.forEach((program) => program.programFamilies.forEach((family) => {
      map.set(family.id, [...(map.get(family.id) ?? []), program.id]);
    }));
    return map;
  }, [catalog]);

  const openCreate = () => {
    setDraft({ family: null, name: "", programIds: [], membershipDirty: false });
    setNameError(null); setOperationError(null);
  };
  const openEdit = (family: ProgramFamily) => {
    setDraft({ family, name: family.name, programIds: memberIdsByFamily.get(family.id) ?? [], membershipDirty: false });
    setNameError(null); setOperationError(null);
  };

  const save = async () => {
    if (!draft || !draft.name.trim() || saving) return;
    setSaving(true); setNameError(null); setOperationError(null);
    try {
      const saved = draft.family
        ? await updateProgramFamily(draft.family.id, {
          name: draft.name,
          programIds: draft.membershipDirty ? draft.programIds : null,
        })
        : await createProgramFamily(draft.name, draft.programIds);
      setFamilies((current) => [...current.filter((family) => family.id !== saved.id), saved]
        .sort((left, right) => left.name.localeCompare(right.name)));
      const selected = new Set(draft.programIds);
      setCatalog((current) => current.map((program) => ({
        ...program,
        programFamilies: selected.has(program.id)
          ? [...program.programFamilies.filter((family) => family.id !== saved.id), saved]
            .sort((left, right) => left.name.localeCompare(right.name))
          : program.programFamilies.filter((family) => family.id !== saved.id),
      })));
      setDraft(null);
    } catch (error) {
      if (error instanceof ApiRequestError && error.code === "PROGRAM_FAMILY_NAME_CONFLICT") {
        setNameError(error.message);
      } else {
        setOperationError(error instanceof Error ? error.message
          : `Could not ${draft.family ? "update" : "create"} the Program Family.`);
      }
    } finally { setSaving(false); }
  };

  return (
    <section className="space-y-3" aria-labelledby="program-families-heading">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div><h2 id="program-families-heading" className="text-lg font-semibold">Program Families</h2>
          <p className="max-w-3xl text-sm text-foreground/65">Group related programs for faster selection when curating notes. A program can belong to more than one family.</p></div>
        <Button type="button" onClick={openCreate}>+ New family</Button>
      </div>
      {catalogError ? <p className="text-sm text-foreground/65">{catalogError}</p> : null}
      <Card className="overflow-hidden">
        {loading ? <p className="p-5 text-sm text-foreground/65">Loading Program Families...</p> : loadError ? (
          <div className="space-y-3 p-5 text-sm text-red-600"><p>{loadError}</p><Button type="button" size="sm" variant="outline" onClick={() => void load()}>Retry</Button></div>
        ) : <>
          <div className="hidden sm:block"><table className="min-w-full text-sm">
            <thead className="bg-muted/40 text-left text-foreground/60"><tr><th className="px-4 py-3 font-medium">Program Family</th><th className="px-4 py-3 font-medium">Programs</th><th className="px-4 py-3 font-medium"><span className="sr-only">Actions</span></th></tr></thead>
            <tbody>{families.map((family) => <tr key={family.id} className="border-t border-border/60"><td className="px-4 py-3">{family.name}</td><td className="px-4 py-3">{memberIdsByFamily.get(family.id)?.length ?? 0}</td><td className="px-4 py-3"><Button type="button" size="sm" variant="outline" onClick={() => openEdit(family)}>Edit</Button></td></tr>)}</tbody>
          </table></div>
          <div className="divide-y divide-border/60 sm:hidden">{families.map((family) => (
            <article key={family.id} className="space-y-3 p-4"><h3 className="font-medium">{family.name}</h3><p className="text-sm text-foreground/65">{memberIdsByFamily.get(family.id)?.length ?? 0} programs</p><Button type="button" variant="outline" className="w-full" onClick={() => openEdit(family)}>Edit</Button></article>
          ))}</div>
        </>}
      </Card>
      <AppModal isOpen={draft !== null} onClose={() => { if (!saving) setDraft(null); }}
        title={draft?.family ? "Edit Program Family" : "New Program Family"}
        actions={<div className="flex flex-col gap-2 sm:flex-row sm:justify-end"><Button type="button" variant="outline" disabled={saving} onClick={() => setDraft(null)}>Cancel</Button><Button type="button" loading={saving} loadingText="Saving..." disabled={!draft?.name.trim()} onClick={() => void save()}>{draft?.family ? "Save changes" : "Create family"}</Button></div>}>
        {draft ? <div className="space-y-4">
          <div className="space-y-2"><label htmlFor="program-family-name" className="text-sm font-medium">Name</label><input id="program-family-name" value={draft.name} maxLength={120} disabled={saving} onChange={(event) => setDraft({ ...draft, name: event.target.value })} className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm" />{nameError ? <p role="alert" className="text-sm text-red-600 dark:text-red-400">{nameError}</p> : null}</div>
          <CatalogMultiSelect id="family-programs" label="Course / Programs" items={catalog}
            selectedIds={draft.programIds} onChange={(programIds) => setDraft({ ...draft, programIds, membershipDirty: true })}
            selectedSummary="count" disabled={saving} searchPlaceholder="Search or select programs…"
            emptyHint="A family with no programs is valid. You can add them later." />
          {catalogError ? <p className="text-xs text-foreground/60">{catalogError}</p> : null}
          {operationError ? <p role="alert" className="text-sm text-red-600 dark:text-red-400">{operationError}</p> : null}
        </div> : null}
      </AppModal>
    </section>
  );
}
