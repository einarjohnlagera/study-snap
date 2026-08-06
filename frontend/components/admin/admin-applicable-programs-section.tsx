"use client";

import { useCallback, useEffect, useState } from "react";
import { ApplicableProgramsCombobox } from "@/components/metadata/applicable-programs-combobox";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ToastMessage } from "@/components/ui/toast-message";
import {
  getAdminNoteApplicablePrograms,
  getCourseProgramCatalog,
  replaceNoteApplicablePrograms,
  type AdminNoteApplicableProgramsItem,
  type AdminNoteApplicableProgramsPage,
  type CourseProgramCatalogItem,
} from "@/lib/api";

const PAGE_SIZE = 25;

export function AdminApplicableProgramsSection() {
  const [pageIndex, setPageIndex] = useState(0);
  const [data, setData] = useState<AdminNoteApplicableProgramsPage | null>(null);
  const [catalog, setCatalog] = useState<CourseProgramCatalogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editingNote, setEditingNote] = useState<AdminNoteApplicableProgramsItem | null>(null);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const [saveFailure, setSaveFailure] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [nextData, nextCatalog] = await Promise.all([
        getAdminNoteApplicablePrograms(pageIndex, PAGE_SIZE),
        getCourseProgramCatalog(),
      ]);
      setData(nextData);
      setCatalog(nextCatalog);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Could not load Applicable Programs.");
    } finally {
      setLoading(false);
    }
  }, [pageIndex]);

  useEffect(() => {
    void load();
  }, [load]);

  const openEditor = (item: AdminNoteApplicableProgramsItem) => {
    const ids = item.applicablePrograms.map((program) => program.id);
    setEditingNote(item);
    setSelectedIds(ids);
    setSaveFailure(null);
  };

  const closeEditor = () => {
    if (saving) {
      return;
    }
    setEditingNote(null);
    setSaveFailure(null);
  };

  const save = async () => {
    if (!editingNote || saving) {
      return;
    }
    // C7. This screen has no Domain Context control, and the endpoint validates the new program set
    // against the note's already-persisted domainContext -- so expanding a blank-domain note past one
    // program 400s with no way to act on it from here. Catch it before the request and say where the
    // field lives, instead of surfacing a raw API error on a field this screen does not show.
    if (selectedIds.length > 1 && !editingNote.domainContext) {
      setSaveFailure(
        "A note shared across several programs needs a Domain Context. This screen cannot set it — "
        + "open the note and set Domain Context under \"Generation & discovery\", then reapply these programs.",
      );
      return;
    }
    setSaving(true);
    setSaveFailure(null);
    try {
      const applicablePrograms = await replaceNoteApplicablePrograms(editingNote.noteId, selectedIds);
      setData((current) => current ? {
        ...current,
        items: current.items.map((item) => item.noteId === editingNote.noteId
          ? { ...item, applicablePrograms }
          : item),
      } : current);
      setEditingNote(null);
    } catch (saveError) {
      // Deliberately do NOT reset the selection here. A failed save used to restore the persisted set,
      // wiping every new pick, so a curator who family-expanded to several programs had to re-choose all
      // of them before they could even retry. Keep the work; the error explains what to change.
      setSaveFailure(saveError instanceof Error ? saveError.message : "Could not save Applicable Programs.");
    } finally {
      setSaving(false);
    }
  };

  const hasNextPage = data ? (data.page + 1) * data.size < data.totalElements : false;

  return (
    <section className="space-y-3">
      {saveFailure ? <ToastMessage message={saveFailure} tone="error" /> : null}
      <div>
        <h2 className="text-lg font-semibold text-foreground">Note Applicable Programs</h2>
        <p className="text-sm text-foreground/65">
          Curate where a canonical note can be discovered without changing its generation context.
        </p>
      </div>
      <Card className="overflow-hidden">
        {loading ? (
          <p className="p-5 text-sm text-foreground/65">Loading notes and Applicable Programs...</p>
        ) : error ? (
          <div className="space-y-3 p-5 text-sm text-red-700 dark:text-red-300">
            <p>{error}</p>
            <Button type="button" size="sm" variant="outline" onClick={() => void load()}>
              Retry
            </Button>
          </div>
        ) : data ? (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead className="bg-muted/40 text-left text-foreground/60">
                  <tr>
                    <th className="px-4 py-3 font-medium">Note</th>
                    <th className="px-4 py-3 font-medium">Owner</th>
                    <th className="px-4 py-3 font-medium">Legacy Course / Program</th>
                    <th className="px-4 py-3 font-medium">Applicable Programs</th>
                    <th className="px-4 py-3 font-medium">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((item) => (
                    <tr key={item.noteId} className="border-t border-border/60 align-top">
                      <td className="px-4 py-3 text-foreground/80">{item.title ?? "Untitled note"}</td>
                      <td className="px-4 py-3 text-foreground/70">{item.ownerEmail ?? "Unknown owner"}</td>
                      <td className="px-4 py-3 text-foreground/70">{item.courseProgram ?? "—"}</td>
                      <td className="px-4 py-3 text-foreground/70">
                        {item.applicablePrograms.length > 0
                          ? item.applicablePrograms.map((program) => program.name).join(", ")
                          : "None"}
                      </td>
                      <td className="px-4 py-3">
                        <Button type="button" size="sm" variant="outline" onClick={() => openEditor(item)}>
                          Edit
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {data.items.length === 0 ? (
              <p className="border-t border-border/60 p-5 text-sm text-foreground/65">No notes found.</p>
            ) : null}
            <div className="flex items-center justify-between border-t border-border/60 px-4 py-3">
              <p className="text-xs text-foreground/60">
                Page {data.page + 1} · {data.totalElements} notes
              </p>
              <div className="flex gap-2">
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={data.page === 0}
                  onClick={() => setPageIndex((value) => Math.max(0, value - 1))}
                >
                  Previous
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={!hasNextPage}
                  onClick={() => setPageIndex((value) => value + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          </>
        ) : null}
      </Card>

      <AppModal
        isOpen={Boolean(editingNote)}
        title="Edit Applicable Programs"
        description={editingNote?.title ?? "Untitled note"}
        onClose={closeEditor}
        panelClassName="max-w-[620px]"
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={closeEditor} disabled={saving}>
              Cancel
            </Button>
            <Button type="button" onClick={() => void save()} loading={saving} loadingText="Saving...">
              Save Applicable Programs
            </Button>
          </div>
        )}
      >
        <ApplicableProgramsCombobox
          id="admin-note-applicable-programs"
          catalog={catalog}
          selectedIds={selectedIds}
          onChange={setSelectedIds}
          disabled={saving}
        />
        {saveFailure ? <p className="text-sm text-red-600 dark:text-red-400">{saveFailure}</p> : null}
      </AppModal>
    </section>
  );
}
