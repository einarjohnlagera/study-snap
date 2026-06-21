"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { DndContext, PointerSensor, KeyboardSensor, closestCenter, useSensor, useSensors, type DragEndEvent } from "@dnd-kit/core";
import { SortableContext, arrayMove, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVertical, Globe, Lock, MoreHorizontal, Search, Settings2, X } from "lucide-react";
import { AppModal } from "@/components/ui/app-modal";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";
import { ResponsiveActionButton, ResponsiveActionContent } from "@/components/ui/action-button";
import { CourseProgramCombobox } from "@/components/metadata/course-program-combobox";
import { getAuthUser, type AuthUser } from "@/lib/auth";
import { getCollectionLabels, getCollectionTerminalAction } from "@/lib/collection-labels";
import {
  addCollectionItems,
  ApiRequestError,
  deleteCollection,
  getCollection,
  listCoursePrograms,
  listNotes,
  removeCollectionItem,
  setCollectionItemOrder,
  updateCollection,
  updateCollectionVisibility,
  updateNoteVisibility,
  type NoteCollectionDetail,
  type NoteCollectionItem,
  type NoteListItemResponse,
  type NoteVisibility,
} from "@/lib/api";
import { getStudyPlanSkippedNotice } from "@/app/dashboard/dashboard-study-plan-section";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { cn } from "@/lib/utils";
import { getUpgradeCtas, type AppPlanType } from "@/src/config/plans";

type LoadState = "loading" | "ready" | "error" | "not-found";
type MutationKind = "add" | "delete" | "edit" | "publish" | "remove" | "reorder" | null;
type NextPlanAction = {
  item: NoteCollectionItem;
  actionLabel: "Generate Study Pack" | "Study this note" | "Review due concepts";
  description: string;
};

const TITLE_MAX_LENGTH = 150;
const LABEL_MAX_LENGTH = 120;

function sortItems(items: NoteCollectionItem[]): NoteCollectionItem[] {
  return [...items].sort((left, right) => left.position - right.position);
}

function buildOrderPayload(items: NoteCollectionItem[]) {
  return items.map((item) => ({ noteId: item.noteId, label: item.label ?? null }));
}

function getNoteTitle(item: Pick<NoteCollectionItem, "title" | "noteId">): string {
  return item.title?.trim() || `Untitled note ${item.noteId.slice(0, 8)}`;
}

function getNoteMeta(item: Pick<NoteCollectionItem, "subject" | "courseProgram">): string {
  return [item.subject, item.courseProgram].filter(Boolean).join(" · ") || "No subject yet";
}

type NoteExecutionStatus = { label: string; className: string };

// Per-note execution status for the Study Plan detail rows. This is a learner
// signal — have I practiced this note yet — not exam-readiness. The steady-state
// model is Needs Study Pack -> Not started -> Practiced; the transient
// Generating / Generation failed states are kept for operational feedback.
function getNoteExecutionStatus(
  item: Pick<NoteCollectionItem, "studyPackStatus" | "lastSessionCompletedAt">,
): NoteExecutionStatus {
  if (item.studyPackStatus === "GENERATING") {
    return { label: "Generating", className: "text-foreground/60" };
  }
  if (item.studyPackStatus === "FAILED") {
    return { label: "Generation failed", className: "text-red-700 dark:text-red-300" };
  }
  if (item.studyPackStatus !== "STUDY_PACK_READY") {
    return { label: "Needs Study Pack", className: "text-amber-700 dark:text-amber-300" };
  }
  if (item.lastSessionCompletedAt !== null) {
    return { label: "Practiced", className: "text-emerald-700 dark:text-emerald-300" };
  }
  return { label: "Not started", className: "text-foreground/60" };
}

function canIncludeCollectionItemInExam(item: Pick<NoteCollectionItem, "generatedQuizId">): boolean {
  return Boolean(item.generatedQuizId);
}

function canViewConceptHealth(currentPlan: AppPlanType): boolean {
  return currentPlan === "PLUS" || currentPlan === "PRO";
}

function getNextPlanAction(items: NoteCollectionItem[], canReviewDueConcepts: boolean): NextPlanAction | null {
  const needsStudyPack = items.find((item) => item.studyPackStatus !== "STUDY_PACK_READY");
  if (needsStudyPack) {
    return {
      item: needsStudyPack,
      actionLabel: "Generate Study Pack",
      description: "Turn this note into a Study Pack before moving to the next step.",
    };
  }

  const needsPractice = items.find((item) => item.lastSessionCompletedAt === null);
  if (needsPractice) {
    return {
      item: needsPractice,
      actionLabel: "Study this note",
      description: "Practice this Study Pack before continuing through the plan.",
    };
  }

  if (canReviewDueConcepts) {
    const needsReview = items.find((item) => item.dueConceptCount > 0);
    if (needsReview) {
      return {
        item: needsReview,
        actionLabel: "Review due concepts",
        description: "Revisit the concepts that are due in this note.",
      };
    }
  }

  return null;
}

function CollectionProgressSummary({ collection }: Readonly<{ collection: NoteCollectionDetail }>) {
  const { totalNotes, notesWithStudyPack, notesPracticed } = collection.progress;
  const practicedPercentage = totalNotes > 0
    ? Math.min(100, Math.max(0, Math.round((notesPracticed / totalNotes) * 100)))
    : 0;

  return (
    <Card className="space-y-3 p-4 sm:p-5">
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">Progress</p>
        {totalNotes > 0 ? (
          <p className="text-sm font-medium text-foreground">
            {notesWithStudyPack} of {totalNotes} Study Packs ready · {notesPracticed} of {totalNotes} practiced
          </p>
        ) : (
          <div className="space-y-1">
            <p className="text-sm font-medium text-foreground">No progress yet</p>
            <p className="text-xs text-foreground/60">Add notes to track Study Pack readiness and practice.</p>
          </div>
        )}
      </div>
      <div
        role="progressbar"
        aria-label="Notes practiced"
        aria-valuemin={0}
        aria-valuemax={totalNotes}
        aria-valuenow={notesPracticed}
        className="h-2 overflow-hidden rounded-full bg-muted"
      >
        <div
          className="h-full rounded-full bg-blue-600 transition-[width] dark:bg-blue-400"
          style={{ width: `${practicedPercentage}%` }}
        />
      </div>
    </Card>
  );
}

function NextInPlanCard({
  items,
  collectionId,
  canReviewDueConcepts,
}: Readonly<{
  items: NoteCollectionItem[];
  collectionId: string;
  canReviewDueConcepts: boolean;
}>) {
  if (items.length === 0) {
    return null;
  }

  const nextAction = getNextPlanAction(items, canReviewDueConcepts);
  if (!nextAction) {
    return (
      <Card className="space-y-2 border-emerald-500/25 bg-emerald-500/5 p-4 sm:p-5">
        <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">Next in this plan</p>
        <CardTitle>All caught up in this plan</CardTitle>
        <CardDescription>Every note has a Study Pack and has been practiced.</CardDescription>
      </Card>
    );
  }

  const noteHref = `/notes/${nextAction.item.noteId}?ref=${encodeURIComponent(`/collections/${collectionId}`)}`;
  return (
    <Card className="space-y-3 border-blue-500/25 bg-blue-500/5 p-4 sm:p-5">
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">Next in this plan</p>
        <CardTitle>{getNoteTitle(nextAction.item)}</CardTitle>
        <CardDescription>{nextAction.description}</CardDescription>
      </div>
      <Link
        href={noteHref}
        className="inline-flex min-h-10 items-center justify-center rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-400"
      >
        {nextAction.actionLabel}
      </Link>
    </Card>
  );
}

function normalizeNoteSearch(value: string): string {
  return value.trim().toLowerCase();
}

function filterPickerNotes(notes: NoteListItemResponse[], presentNoteIds: Set<string>, query: string): NoteListItemResponse[] {
  const normalizedQuery = normalizeNoteSearch(query);
  return notes
    .filter((note) => !presentNoteIds.has(note.id))
    .filter((note) => {
      if (!normalizedQuery) {
        return true;
      }
      return [note.title, note.subject, note.courseProgram, ...(note.tags ?? [])]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(normalizedQuery));
    });
}

function EditCollectionModal({
  collection,
  isOpen,
  onClose,
  onSaved,
}: Readonly<{
  collection: NoteCollectionDetail;
  isOpen: boolean;
  onClose: () => void;
  onSaved: (collection: NoteCollectionDetail) => void;
}>) {
  const [title, setTitle] = useState(collection.title);
  const [description, setDescription] = useState(collection.description ?? "");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      setTitle(collection.title);
      setDescription(collection.description ?? "");
      setError(null);
      setSubmitting(false);
    }
  }, [collection.description, collection.title, isOpen]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmedTitle = title.trim();
    if (!trimmedTitle) {
      setError("Title is required.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const saved = await updateCollection(collection.id, {
        title: trimmedTitle,
        description: description.trim() || null,
      });
      onSaved(saved);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Could not update this collection.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AppModal
      isOpen={isOpen}
      title="Edit details"
      description="Update the name or description for this saved set."
      onClose={onClose}
      actions={(
        <>
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" form="edit-collection-form" loading={submitting} loadingText="Saving...">Save</Button>
        </>
      )}
    >
      <form id="edit-collection-form" className="space-y-4" onSubmit={handleSubmit}>
        <label className="block space-y-1.5">
          <span className="text-sm font-medium text-foreground">Title</span>
          <input
            data-autofocus="true"
            value={title}
            maxLength={TITLE_MAX_LENGTH}
            onChange={(event) => setTitle(event.target.value)}
            className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
          />
        </label>
        <label className="block space-y-1.5">
          <span className="text-sm font-medium text-foreground">Description</span>
          <textarea
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            className="min-h-24 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
          />
        </label>
        {error ? <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-200">{error}</p> : null}
      </form>
    </AppModal>
  );
}

function DeleteCollectionModal({
  isOpen,
  title,
  onClose,
  onConfirm,
  deleting,
}: Readonly<{
  isOpen: boolean;
  title: string;
  onClose: () => void;
  onConfirm: () => void;
  deleting: boolean;
}>) {
  return (
    <AppModal
      isOpen={isOpen}
      title="Delete collection?"
      description={`Delete "${title}" from your workspace. This removes only the collection and its ordering; your notes will not be deleted.`}
      onClose={onClose}
      actions={(
        <>
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="button" className="bg-red-600 hover:bg-red-700" loading={deleting} loadingText="Deleting..." onClick={onConfirm}>
            Delete
          </Button>
        </>
      )}
    />
  );
}

function AddNotesModal({
  isOpen,
  presentNoteIds,
  onClose,
  onAdd,
}: Readonly<{
  isOpen: boolean;
  presentNoteIds: Set<string>;
  onClose: () => void;
  onAdd: (noteIds: string[]) => Promise<void>;
}>) {
  const [notes, setNotes] = useState<NoteListItemResponse[]>([]);
  const [query, setQuery] = useState("");
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) {
      setQuery("");
      setSelectedIds(new Set());
      setError(null);
      return;
    }
    let mounted = true;
    setLoading(true);
    setError(null);
    void listNotes()
      .then((result) => {
        if (mounted) {
          setNotes(result);
        }
      })
      .catch((loadError) => {
        if (mounted) {
          setError(loadError instanceof Error ? loadError.message : "Could not load your notes.");
        }
      })
      .finally(() => {
        if (mounted) {
          setLoading(false);
        }
      });
    return () => {
      mounted = false;
    };
  }, [isOpen]);

  const availableNotes = useMemo(() => filterPickerNotes(notes, presentNoteIds, query), [notes, presentNoteIds, query]);
  const hasAnyAvailableNotes = notes.some((note) => !presentNoteIds.has(note.id));

  const toggleSelected = (noteId: string) => {
    setSelectedIds((previous) => {
      const next = new Set(previous);
      if (next.has(noteId)) {
        next.delete(noteId);
      } else {
        next.add(noteId);
      }
      return next;
    });
  };

  const handleAdd = async () => {
    const noteIds = Array.from(selectedIds);
    if (noteIds.length === 0) {
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await onAdd(noteIds);
      onClose();
    } catch (addError) {
      setError(addError instanceof Error ? addError.message : "Could not add notes.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AppModal
      isOpen={isOpen}
      title="Add notes"
      description="Choose from your existing notes. Notes already in this collection are hidden."
      onClose={onClose}
      panelClassName="sm:max-w-2xl"
      actions={(
        <>
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="button" loading={submitting} loadingText="Adding..." disabled={selectedIds.size === 0} onClick={handleAdd}>
            Add selected
          </Button>
        </>
      )}
    >
      <div className="space-y-4">
        <label className="flex items-center gap-2 rounded-lg border border-border bg-background px-3 py-2">
          <Search className="h-4 w-4 text-foreground/50" aria-hidden="true" />
          <span className="sr-only">Search notes</span>
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search notes"
            className="w-full bg-transparent text-sm outline-none"
          />
        </label>

        {loading ? <p className="text-sm text-foreground/60">Loading notes...</p> : null}
        {error ? <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-200">{error}</p> : null}
        {!loading && !error && !hasAnyAvailableNotes ? (
          <p className="rounded-lg bg-muted px-3 py-3 text-sm text-foreground/70">
            You do not have any other notes to add yet.
          </p>
        ) : null}
        {!loading && !error && hasAnyAvailableNotes && availableNotes.length === 0 ? (
          <p className="rounded-lg bg-muted px-3 py-3 text-sm text-foreground/70">No matching notes found.</p>
        ) : null}
        <div className="max-h-80 space-y-2 overflow-y-auto pr-1">
          {availableNotes.map((note) => (
            <label key={note.id} className="flex cursor-pointer items-start gap-3 rounded-lg border border-border p-3 hover:bg-highlight">
              <input
                type="checkbox"
                checked={selectedIds.has(note.id)}
                onChange={() => toggleSelected(note.id)}
                className="mt-1"
              />
              <span className="space-y-1">
                <span className="block text-sm font-medium text-foreground">{note.title || "Untitled note"}</span>
                <span className="block text-xs text-foreground/60">
                  {[note.subject, note.courseProgram].filter(Boolean).join(" · ") || "No subject yet"}
                </span>
              </span>
            </label>
          ))}
        </div>
      </div>
    </AppModal>
  );
}

function PublishStudyPlanModal({
  collection,
  isOpen,
  privateNoteIds,
  onClose,
  onSaved,
  onNotesPublished,
}: Readonly<{
  collection: NoteCollectionDetail;
  isOpen: boolean;
  privateNoteIds: string[];
  onClose: () => void;
  onSaved: (collection: NoteCollectionDetail) => void;
  onNotesPublished: () => Promise<void> | void;
}>) {
  const [courseProgram, setCourseProgram] = useState(collection.courseProgram ?? "");
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [suggestionsError, setSuggestionsError] = useState(false);
  const [savingCourseProgram, setSavingCourseProgram] = useState(false);
  const [togglingVisibility, setTogglingVisibility] = useState(false);
  const [makingPublic, setMakingPublic] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isPublic = collection.visibility === "PUBLIC";
  const trimmedCourseProgram = courseProgram.trim();
  const courseProgramDirty = trimmedCourseProgram !== (collection.courseProgram ?? "").trim();
  const busy = savingCourseProgram || togglingVisibility || makingPublic;
  const privateCount = privateNoteIds.length;
  const blockedByPrivateNotes = privateCount > 0;

  // Publishing is a constrained surface: lock the field to known buckets, but keep
  // the plan's existing value selectable even if the suggestion fetch omits it.
  const courseProgramOptions = useMemo(() => {
    const existing = (collection.courseProgram ?? "").trim();
    return existing && !suggestions.includes(existing) ? [existing, ...suggestions] : suggestions;
  }, [collection.courseProgram, suggestions]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    setCourseProgram(collection.courseProgram ?? "");
    setError(null);
  }, [collection.courseProgram, collection.id, isOpen]);

  const loadSuggestions = useCallback(async () => {
    setSuggestionsError(false);
    try {
      setSuggestions(await listCoursePrograms("public"));
    } catch {
      setSuggestionsError(true);
    }
  }, []);

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    void loadSuggestions();
  }, [isOpen, loadSuggestions]);

  const persistCourseProgram = async (): Promise<NoteCollectionDetail | null> => {
    setSavingCourseProgram(true);
    setError(null);
    try {
      const saved = await updateCollection(collection.id, { courseProgram: trimmedCourseProgram || null });
      onSaved(saved);
      return saved;
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Could not save the course/program.");
      return null;
    } finally {
      setSavingCourseProgram(false);
    }
  };

  const handlePublish = async () => {
    if (!trimmedCourseProgram) {
      setError("Add a course/program so matching learners can find this plan.");
      return;
    }
    if (blockedByPrivateNotes) {
      setError("Make every note public before publishing this plan.");
      return;
    }
    if (courseProgramDirty && !(await persistCourseProgram())) {
      return;
    }
    setTogglingVisibility(true);
    setError(null);
    try {
      const saved = await updateCollectionVisibility(collection.id, "PUBLIC");
      onSaved(saved);
    } catch (publishError) {
      setError(publishError instanceof Error ? publishError.message : "Could not publish this plan.");
    } finally {
      setTogglingVisibility(false);
    }
  };

  const handleUnpublish = async () => {
    setTogglingVisibility(true);
    setError(null);
    try {
      const saved = await updateCollectionVisibility(collection.id, "PRIVATE");
      onSaved(saved);
    } catch (unpublishError) {
      setError(unpublishError instanceof Error ? unpublishError.message : "Could not unpublish this plan.");
    } finally {
      setTogglingVisibility(false);
    }
  };

  const handleMakePublic = async () => {
    setMakingPublic(true);
    setError(null);
    try {
      await Promise.all(privateNoteIds.map((noteId) => updateNoteVisibility(noteId, "PUBLIC")));
      await onNotesPublished();
    } catch (makePublicError) {
      setError(makePublicError instanceof Error ? makePublicError.message : "Could not make these notes public.");
    } finally {
      setMakingPublic(false);
    }
  };

  return (
    <AppModal
      isOpen={isOpen}
      title="Publish study plan"
      description="Published plans are discoverable by matching learners and can be adopted into their library."
      onClose={onClose}
      actions={(
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:items-center sm:justify-end">
          {isPublic ? (
            <>
              <Button type="button" variant="outline" loading={togglingVisibility} loadingText="Unpublishing..." disabled={busy} onClick={() => void handleUnpublish()}>
                Unpublish
              </Button>
              <Button type="button" loading={savingCourseProgram} loadingText="Saving..." disabled={busy || !courseProgramDirty} onClick={() => void persistCourseProgram()}>
                Save
              </Button>
            </>
          ) : (
            <Button type="button" loading={savingCourseProgram || togglingVisibility} loadingText="Publishing..." disabled={busy || blockedByPrivateNotes} onClick={() => void handlePublish()}>
              Publish
            </Button>
          )}
        </div>
      )}
    >
      <div className="space-y-4">
        <span
          className={`inline-flex w-fit items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium ${
            isPublic
              ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300"
              : "border-border bg-muted/40 text-foreground/70"
          }`}
        >
          {isPublic ? <Globe className="h-3.5 w-3.5" aria-hidden="true" /> : <Lock className="h-3.5 w-3.5" aria-hidden="true" />}
          {isPublic ? "Published" : "Private"}
        </span>

        <div className="space-y-1.5">
          <span className="text-sm font-medium text-foreground">Course / Program</span>
          <CourseProgramCombobox
            id="publish-course-program"
            value={courseProgram}
            suggestions={courseProgramOptions}
            onChange={setCourseProgram}
            ariaLabel="Course / Program"
            context="profile"
            allowCustom={false}
            inlineDropdown
          />
          {suggestionsError && courseProgramOptions.length === 0 ? (
            <p className="text-xs text-red-600 dark:text-red-400">
              Could not load course/programs.{" "}
              <button type="button" className="font-semibold underline" onClick={() => void loadSuggestions()}>
                Retry
              </button>
            </p>
          ) : (
            <p className="text-xs text-foreground/60">Learners with this course/program will see the plan on their dashboard.</p>
          )}
        </div>

        {privateCount > 0 ? (
          <div className="space-y-2 rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-3">
            <p className="text-sm font-medium text-amber-800 dark:text-amber-200">
              {privateCount} {privateCount === 1 ? "note is" : "notes are"} still private
            </p>
            <p className="text-xs text-foreground/70">
              Adopters copy the notes in this plan, so private notes will be skipped. Make them public so the full plan can be adopted.
            </p>
            <Button type="button" size="sm" variant="outline" loading={makingPublic} loadingText="Making public..." disabled={busy} onClick={() => void handleMakePublic()}>
              {`Make ${privateCount} public`}
            </Button>
          </div>
        ) : null}

        {error ? (
          <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-200">{error}</p>
        ) : null}
      </div>
    </AppModal>
  );
}

function SortableCollectionItemRow({
  item,
  index,
  itemCount,
  disabled,
  collectionId,
  showWeakAreas,
  isPrivate,
  onMove,
  onRemove,
  onLabelChange,
}: Readonly<{
  item: NoteCollectionItem;
  index: number;
  itemCount: number;
  disabled: boolean;
  collectionId: string;
  showWeakAreas: boolean;
  isPrivate: boolean;
  onMove: (noteId: string, direction: "up" | "down") => void;
  onRemove: (noteId: string) => void;
  onLabelChange: (noteId: string, label: string) => void;
}>) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: item.noteId });
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  const [labelValue, setLabelValue] = useState(item.label ?? "");

  const commitLabel = () => {
    if ((item.label ?? "") !== labelValue.trim()) {
      onLabelChange(item.noteId, labelValue.trim());
    }
  };

  return (
    <li
      ref={setNodeRef}
      style={style}
      className={cn(
        "rounded-xl border border-border bg-background p-4 shadow-sm",
        isDragging && "opacity-70 ring-2 ring-blue-400",
      )}
    >
      <div className="grid gap-4 lg:grid-cols-[auto_1fr_auto] lg:items-start">
        <button
          type="button"
          className="inline-flex h-10 w-10 items-center justify-center rounded-lg border border-border text-foreground/55 hover:bg-highlight"
          aria-label={`Drag ${getNoteTitle(item)}`}
          disabled={disabled}
          {...attributes}
          {...listeners}
        >
          <GripVertical className="h-4 w-4" aria-hidden="true" />
        </button>

        <div className="space-y-3">
          <Link href={`/notes/${item.noteId}?ref=${encodeURIComponent(`/collections/${collectionId}`)}`} className="block rounded-lg p-1 -m-1 hover:bg-highlight">
            <h2 className="text-base font-semibold text-foreground">{getNoteTitle(item)}</h2>
            <p className="text-sm text-foreground/60">{getNoteMeta(item)}</p>
            <div className="mt-1 flex flex-wrap items-center gap-2">
              <p className={`text-xs font-medium ${getNoteExecutionStatus(item).className}`}>{getNoteExecutionStatus(item).label}</p>
              {isPrivate ? (
                <span className="inline-flex items-center gap-1 rounded-full border border-amber-500/30 bg-amber-500/10 px-2 py-0.5 text-xs font-medium text-amber-800 dark:text-amber-200">
                  <Lock className="h-3 w-3" aria-hidden="true" />
                  Private
                </span>
              ) : null}
            </div>
          </Link>
          {showWeakAreas && item.dueConceptCount > 0 ? (
            <div className="rounded-lg border border-amber-500/25 bg-amber-500/10 px-3 py-2">
              <p className="text-sm font-medium text-amber-800 dark:text-amber-200">
                {item.dueConceptCount} {item.dueConceptCount === 1 ? "concept" : "concepts"} due
              </p>
              {item.dueConcepts.length > 0 ? (
                <p className="mt-1 text-xs text-foreground/65">{item.dueConcepts.join(" · ")}</p>
              ) : null}
            </div>
          ) : null}
          <label className="block space-y-1.5">
            <span className="text-xs font-medium uppercase tracking-wide text-foreground/50">Label</span>
            <input
              value={labelValue}
              maxLength={LABEL_MAX_LENGTH}
              disabled={disabled}
              onChange={(event) => setLabelValue(event.target.value)}
              onBlur={commitLabel}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.currentTarget.blur();
                }
              }}
              placeholder="Week, topic, or section"
              className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
            />
          </label>
        </div>

        <div className="flex flex-wrap gap-2 lg:justify-end">
          <Button type="button" variant="outline" size="sm" disabled={disabled || index === 0} onClick={() => onMove(item.noteId, "up")}>
            Move up
          </Button>
          <Button type="button" variant="outline" size="sm" disabled={disabled || index === itemCount - 1} onClick={() => onMove(item.noteId, "down")}>
            Move down
          </Button>
          <Button type="button" variant="ghost" size="sm" disabled={disabled} onClick={() => onRemove(item.noteId)}>
            Remove
          </Button>
        </div>
      </div>
    </li>
  );
}

export function CollectionDetailPageClient({ collectionId }: Readonly<{ collectionId: string }>) {
  const router = useRouter();
  const [authUser, setAuthUser] = useState<AuthUser | null>(null);
  useEffect(() => {
    setAuthUser(getAuthUser());
  }, []);
  const currentPlan = (authUser?.planType ?? "FREE") as AppPlanType;
  const isAdmin = authUser?.role === "ADMIN";
  const showWeakAreas = canViewConceptHealth(currentPlan);
  const upgradeCtas = useMemo(() => getUpgradeCtas(currentPlan), [currentPlan]);
  const labels = useMemo(() => getCollectionLabels(authUser?.profileType), [authUser?.profileType]);
  const terminalAction = useMemo(() => getCollectionTerminalAction(authUser?.profileType), [authUser?.profileType]);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [collection, setCollection] = useState<NoteCollectionDetail | null>(null);
  const [items, setItems] = useState<NoteCollectionItem[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [mutationKind, setMutationKind] = useState<MutationKind>(null);
  const [editOpen, setEditOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [publishOpen, setPublishOpen] = useState(false);
  const [actionsMenuOpen, setActionsMenuOpen] = useState(false);
  const actionsMenuRef = useRef<HTMLDivElement | null>(null);
  const [noteVisibility, setNoteVisibility] = useState<Map<string, NoteVisibility>>(new Map());
  const [skippedNoticeCount, setSkippedNoticeCount] = useState<number | null>(null);

  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const loadCollection = useCallback(async () => {
    setLoadState("loading");
    setLoadError(null);
    try {
      const result = await getCollection(collectionId);
      setCollection(result);
      setItems(sortItems(result.items));
      setLoadState("ready");
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 404) {
        setLoadState("not-found");
        return;
      }
      setLoadError(error instanceof Error ? error.message : "Could not load this collection.");
      setLoadState("error");
    }
  }, [collectionId]);

  useEffect(() => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
    void Promise.resolve().then(loadCollection);
  }, [loadCollection, router]);

  useEffect(() => {
    setSkippedNoticeCount(getStudyPlanSkippedNotice(collectionId));
  }, [collectionId]);

  const loadNoteVisibility = useCallback(async () => {
    try {
      const notes = await listNotes();
      setNoteVisibility(new Map(notes.map((note) => [note.id, note.visibility])));
    } catch {
      // Visibility badges are admin-only progressive enhancement; ignore failures.
    }
  }, []);

  useEffect(() => {
    if (!isAdmin) {
      return;
    }
    void loadNoteVisibility();
  }, [isAdmin, loadNoteVisibility]);

  useEffect(() => {
    if (!actionsMenuOpen) {
      return;
    }
    const handleOutsideClick = (event: MouseEvent) => {
      const target = event.target as Node;
      if (actionsMenuRef.current && !actionsMenuRef.current.contains(target)) {
        setActionsMenuOpen(false);
      }
    };
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setActionsMenuOpen(false);
      }
    };
    globalThis.addEventListener("mousedown", handleOutsideClick);
    globalThis.addEventListener("keydown", handleEscape);
    return () => {
      globalThis.removeEventListener("mousedown", handleOutsideClick);
      globalThis.removeEventListener("keydown", handleEscape);
    };
  }, [actionsMenuOpen]);

  const refetchAfterFailure = async (message: string) => {
    setMutationError(message);
    try {
      const result = await getCollection(collectionId);
      setCollection(result);
      setItems(sortItems(result.items));
    } catch {
      // Keep the visible error; the page-level retry can recover if this fails too.
    }
  };

  const persistOrder = async (nextItems: NoteCollectionItem[], kind: MutationKind = "reorder") => {
    setMutationKind(kind);
    setMutationError(null);
    const previousItems = items;
    setItems(nextItems.map((item, position) => ({ ...item, position })));
    try {
      const saved = await setCollectionItemOrder(collectionId, buildOrderPayload(nextItems));
      setCollection(saved);
      setItems(sortItems(saved.items));
    } catch (error) {
      setItems(previousItems);
      await refetchAfterFailure(error instanceof Error ? error.message : "Could not save this collection order.");
    } finally {
      setMutationKind(null);
    }
  };

  const handleDragEnd = (event: DragEndEvent) => {
    const activeId = String(event.active.id);
    const overId = event.over?.id ? String(event.over.id) : null;
    if (!overId || activeId === overId) {
      return;
    }
    const activeIndex = items.findIndex((item) => item.noteId === activeId);
    const overIndex = items.findIndex((item) => item.noteId === overId);
    if (activeIndex < 0 || overIndex < 0) {
      return;
    }
    void persistOrder(arrayMove(items, activeIndex, overIndex));
  };

  const handleMove = (noteId: string, direction: "up" | "down") => {
    const currentIndex = items.findIndex((item) => item.noteId === noteId);
    const targetIndex = direction === "up" ? currentIndex - 1 : currentIndex + 1;
    if (currentIndex < 0 || targetIndex < 0 || targetIndex >= items.length) {
      return;
    }
    void persistOrder(arrayMove(items, currentIndex, targetIndex));
  };

  const handleLabelChange = (noteId: string, label: string) => {
    const nextItems = items.map((item) => (
      item.noteId === noteId ? { ...item, label: label || null } : item
    ));
    void persistOrder(nextItems);
  };

  const handleRemove = async (noteId: string) => {
    setMutationKind("remove");
    setMutationError(null);
    try {
      await removeCollectionItem(collectionId, noteId);
      const result = await getCollection(collectionId);
      setCollection(result);
      setItems(sortItems(result.items));
    } catch (error) {
      await refetchAfterFailure(error instanceof Error ? error.message : "Could not remove this note.");
    } finally {
      setMutationKind(null);
    }
  };

  const handleAdd = async (noteIds: string[]) => {
    setMutationKind("add");
    setMutationError(null);
    try {
      const result = await addCollectionItems(collectionId, noteIds);
      setCollection(result);
      setItems(sortItems(result.items));
    } catch (error) {
      await refetchAfterFailure(error instanceof Error ? error.message : "Could not add notes.");
      throw error;
    } finally {
      setMutationKind(null);
    }
  };

  const handleDelete = async () => {
    setMutationKind("delete");
    setMutationError(null);
    try {
      await deleteCollection(collectionId);
      router.push("/collections");
    } catch (error) {
      setMutationError(error instanceof Error ? error.message : "Could not delete this collection.");
      setMutationKind(null);
    }
  };

  const presentNoteIds = useMemo(() => new Set(items.map((item) => item.noteId)), [items]);
  const privateNoteIds = useMemo(
    () => (isAdmin ? items.filter((item) => noteVisibility.get(item.noteId) === "PRIVATE").map((item) => item.noteId) : []),
    [isAdmin, items, noteVisibility],
  );
  const itemIds = useMemo(() => items.map((item) => item.noteId), [items]);
  const quizReadyNoteIds = useMemo(
    () => items.filter(canIncludeCollectionItemInExam).map((item) => item.noteId),
    [items],
  );
  const hasNonQuizReadyItems = quizReadyNoteIds.length < items.length;
  const mutationInProgress = mutationKind !== null;

  const openCollectionExamBuilder = useCallback(() => {
    if (quizReadyNoteIds.length === 0) {
      return;
    }
    const params = new URLSearchParams({
      collectionId: collectionId,
      notes: quizReadyNoteIds.join(","),
    });
    router.push(`/library/exam-builder?${params.toString()}`);
  }, [collectionId, quizReadyNoteIds, router]);

  if (loadState === "loading") {
    return (
      <main className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
        <BackLink href="/collections" label={labels.plural} />
        <PageHeader eyebrow={labels.singular.toUpperCase()} title="Loading..." description="Loading this saved set of notes." />
        <Card className="space-y-4 p-6">
          <div className="h-5 w-1/2 animate-pulse rounded bg-muted" />
          <div className="h-20 w-full animate-pulse rounded bg-muted" />
        </Card>
      </main>
    );
  }

  if (loadState === "not-found") {
    return (
      <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
        <BackLink href="/collections" label={labels.plural} />
        <Card className="space-y-4 p-6">
          <CardTitle>{labels.singular} not found</CardTitle>
          <CardDescription>This saved set may have been deleted or may not belong to your account.</CardDescription>
          <Link className="inline-flex text-sm font-medium text-blue-600 hover:underline dark:text-blue-400" href="/collections">
            {labels.plural}
          </Link>
        </Card>
      </main>
    );
  }

  if (loadState === "error" || !collection) {
    return (
      <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
        <BackLink href="/collections" label={labels.plural} />
        <Card className="space-y-4 p-6">
          <CardTitle>Could not load {labels.singular.toLowerCase()}</CardTitle>
          <CardDescription>{loadError ?? "Please try again."}</CardDescription>
          <Button type="button" variant="outline" onClick={() => void loadCollection()}>Retry</Button>
        </Card>
      </main>
    );
  }

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
      <BackLink href="/collections" label={labels.plural} />
      <PageHeader
        eyebrow={labels.singular.toUpperCase()}
        title={collection.title}
        description={collection.description || undefined}
        meta={isAdmin ? (
          <button
            type="button"
            onClick={() => setPublishOpen(true)}
            aria-label="Publish settings"
            title="Publish settings"
            className="motion-lift inline-flex shrink-0 cursor-pointer items-center gap-1.5 rounded-full border border-border bg-muted/40 px-2.5 py-1.5 text-xs font-medium text-foreground/70 transition-colors hover:bg-highlight"
          >
            {collection.visibility === "PUBLIC" ? (
              <><Globe className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" aria-hidden="true" />Published</>
            ) : (
              <><Lock className="h-3.5 w-3.5" aria-hidden="true" />Private</>
            )}
            <Settings2 className="h-3 w-3 opacity-60" aria-hidden="true" />
          </button>
        ) : undefined}
        actions={(
          <div className="flex items-center justify-end gap-2">
            <div className="relative shrink-0" ref={actionsMenuRef}>
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="h-10 w-10 rounded-full px-0"
                aria-label="Open study plan actions"
                aria-haspopup="menu"
                aria-expanded={actionsMenuOpen}
                onClick={() => setActionsMenuOpen((open) => !open)}
              >
                <MoreHorizontal className="h-4 w-4" aria-hidden="true" />
              </Button>
              {actionsMenuOpen ? (
                <div
                  role="menu"
                  aria-label="Study plan actions"
                  className="motion-dropdown-panel absolute right-0 top-12 z-20 w-44 rounded-xl border border-border bg-background p-1.5 shadow-sm"
                >
                  <button
                    type="button"
                    role="menuitem"
                    className="motion-lift flex w-full items-center rounded-lg px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-highlight active:bg-highlight-strong"
                    onClick={() => { setActionsMenuOpen(false); setEditOpen(true); }}
                  >
                    <ResponsiveActionContent action="edit" label="Edit" showTextOnMobile iconClassName="h-4 w-4" />
                  </button>
                  <button
                    type="button"
                    role="menuitem"
                    className="motion-lift flex w-full items-center rounded-lg px-3 py-2 text-left text-sm text-red-700 transition-colors hover:bg-red-50 active:bg-red-100 dark:text-red-400 dark:hover:bg-red-950/40 dark:active:bg-red-950/60"
                    onClick={() => { setActionsMenuOpen(false); setDeleteOpen(true); }}
                  >
                    <ResponsiveActionContent action="delete" label="Delete" showTextOnMobile iconClassName="h-4 w-4" />
                  </button>
                </div>
              ) : null}
            </div>
          </div>
        )}
        footer={terminalAction?.kind === "exam-builder" ? (
          <div className="flex flex-col items-start gap-1">
            <ResponsiveActionButton
              action="open"
              label={terminalAction.label}
              disabled={quizReadyNoteIds.length === 0}
              onClick={openCollectionExamBuilder}
            />
            {quizReadyNoteIds.length === 0 ? (
              <p className="text-xs text-foreground/60">
                Generate a quiz for at least one note to build an exam.
              </p>
            ) : hasNonQuizReadyItems ? (
              <p className="text-xs text-foreground/60">
                Only quiz-ready notes will be included.
              </p>
            ) : null}
          </div>
        ) : undefined}
      />

      <CollectionProgressSummary collection={collection} />

      <NextInPlanCard
        items={items}
        collectionId={collectionId}
        canReviewDueConcepts={showWeakAreas}
      />

      {skippedNoticeCount ? (
        <Card className="border-amber-500/25 bg-amber-500/10 p-4 text-sm text-foreground/75">
          {skippedNoticeCount} {skippedNoticeCount === 1 ? "item is" : "items are"} no longer available and were left out.
        </Card>
      ) : null}

      {mutationError ? (
        <Card className="flex items-start justify-between gap-4 border-red-200 bg-red-50 p-4 text-red-800 dark:border-red-900 dark:bg-red-950/30 dark:text-red-200">
          <p className="text-sm">{mutationError}</p>
          <button type="button" aria-label="Dismiss error" onClick={() => setMutationError(null)}>
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </Card>
      ) : null}

      <Card className="space-y-4 p-4 sm:p-6">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <CardTitle>Notes</CardTitle>
            <CardDescription>{items.length} {items.length === 1 ? "note" : "notes"} in saved order.</CardDescription>
          </div>
          <ResponsiveActionButton action="create" label="Add notes" onClick={() => setAddOpen(true)} />
        </div>

        {!showWeakAreas && items.length > 0 && upgradeCtas.primary ? (
          <div className="flex flex-col gap-2 rounded-lg border border-border bg-muted/40 px-3 py-3 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm text-foreground/65">See which concepts are due for review in each note.</p>
            <Link
              href="/settings?section=plans"
              className="text-sm font-semibold text-blue-700 hover:underline dark:text-blue-300"
            >
              {upgradeCtas.primary.label}
            </Link>
          </div>
        ) : null}

        {items.length === 0 ? (
          <div className="rounded-xl border border-dashed border-border p-6 text-center">
            <p className="text-sm text-foreground/70">Add notes to start organizing this {labels.singular.toLowerCase()}.</p>
          </div>
        ) : (
          <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
            <SortableContext items={itemIds} strategy={verticalListSortingStrategy}>
              <ol className="space-y-3">
                {items.map((item, index) => (
                  <SortableCollectionItemRow
                    key={`${item.noteId}:${item.label ?? ""}`}
                    item={item}
                    index={index}
                    itemCount={items.length}
                    disabled={mutationInProgress}
                    collectionId={collectionId}
                    showWeakAreas={showWeakAreas}
                    isPrivate={isAdmin && noteVisibility.get(item.noteId) === "PRIVATE"}
                    onMove={handleMove}
                    onRemove={(noteId) => void handleRemove(noteId)}
                    onLabelChange={handleLabelChange}
                  />
                ))}
              </ol>
            </SortableContext>
          </DndContext>
        )}
      </Card>

      <EditCollectionModal
        collection={collection}
        isOpen={editOpen}
        onClose={() => setEditOpen(false)}
        onSaved={(saved) => {
          setCollection(saved);
          setItems(sortItems(saved.items));
          setEditOpen(false);
        }}
      />
      <DeleteCollectionModal
        isOpen={deleteOpen}
        title={collection.title}
        deleting={mutationKind === "delete"}
        onClose={() => setDeleteOpen(false)}
        onConfirm={() => void handleDelete()}
      />
      <AddNotesModal
        isOpen={addOpen}
        presentNoteIds={presentNoteIds}
        onClose={() => setAddOpen(false)}
        onAdd={handleAdd}
      />
      {isAdmin ? (
        <PublishStudyPlanModal
          collection={collection}
          isOpen={publishOpen}
          privateNoteIds={privateNoteIds}
          onClose={() => setPublishOpen(false)}
          onSaved={(saved) => {
            setCollection(saved);
            setItems(sortItems(saved.items));
          }}
          onNotesPublished={loadNoteVisibility}
        />
      ) : null}
    </main>
  );
}
