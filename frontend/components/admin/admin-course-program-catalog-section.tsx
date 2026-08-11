"use client";

import { useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  ApiRequestError,
  createCourseProgram,
  findSimilarCoursePrograms,
  getCourseProgramCatalog,
  type CourseProgramCatalogItem,
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

  const families = useMemo(() => {
    const uniqueFamilies = new Map<string, string>();
    catalog.forEach((program) => {
      if (program.programFamilyId && program.programFamilyName) {
        uniqueFamilies.set(program.programFamilyId, program.programFamilyName);
      }
    });
    return Array.from(uniqueFamilies, ([id, familyName]) => ({ id, name: familyName }))
      .sort((left, right) => left.name.localeCompare(right.name));
  }, [catalog]);

  const load = async () => {
    setLoading(true);
    setLoadError(null);
    try {
      setCatalog(await getCourseProgramCatalog());
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : "Could not load the course program catalog.");
    } finally {
      setLoading(false);
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
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-muted/40 text-left text-foreground/60"><tr><th className="px-4 py-3 font-medium">Course / Program</th><th className="px-4 py-3 font-medium">Family</th></tr></thead>
              <tbody>{catalog.map((program) => <tr key={program.id} className="border-t border-border/60"><td className="px-4 py-3">{program.name}</td><td className="px-4 py-3 text-foreground/70">{program.programFamilyName ?? "—"}</td></tr>)}</tbody>
            </table>
          </div>
        )}
      </Card>
    </section>
  );
}
