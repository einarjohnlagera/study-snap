"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { DndContext, PointerSensor, KeyboardSensor, closestCenter, useSensor, useSensors, type DragEndEvent } from "@dnd-kit/core";
import { SortableContext, arrayMove, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVertical, Search, X } from "lucide-react";
import { AppModal } from "@/components/ui/app-modal";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";
import { ResponsiveActionButton } from "@/components/ui/action-button";
import { getAuthUser } from "@/lib/auth";
import { getCollectionLabels, getCollectionTerminalAction } from "@/lib/collection-labels";
import {
  addCollectionItems,
  ApiRequestError,
  deleteCollection,
  getCollection,
  listNotes,
  removeCollectionItem,
  setCollectionItemOrder,
  updateCollection,
  type NoteCollectionDetail,
  type NoteCollectionItem,
  type NoteListItemResponse,
} from "@/lib/api";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { cn } from "@/lib/utils";

type LoadState = "loading" | "ready" | "error" | "not-found";
type MutationKind = "add" | "delete" | "edit" | "remove" | "reorder" | null;

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

function getQuizReadinessHint(item: Pick<NoteCollectionItem, "studyPackStatus" | "generatedQuizId">): string {
  if (item.generatedQuizId) {
    return "Quiz ready";
  }
  if (item.studyPackStatus === "STUDY_PACK_READY") {
    return "Study Pack ready";
  }
  if (item.studyPackStatus === "GENERATING") {
    return "Generating";
  }
  if (item.studyPackStatus === "FAILED") {
    return "Generation failed";
  }
  return "Draft";
}

function canIncludeCollectionItemInExam(item: Pick<NoteCollectionItem, "generatedQuizId">): boolean {
  return Boolean(item.generatedQuizId);
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

function SortableCollectionItemRow({
  item,
  index,
  itemCount,
  disabled,
  onMove,
  onRemove,
  onLabelChange,
}: Readonly<{
  item: NoteCollectionItem;
  index: number;
  itemCount: number;
  disabled: boolean;
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
          <Link href={`/notes/${item.noteId}`} className="block rounded-lg p-1 -m-1 hover:bg-highlight">
            <h2 className="text-base font-semibold text-foreground">{getNoteTitle(item)}</h2>
            <p className="text-sm text-foreground/60">{getNoteMeta(item)}</p>
            <p className="mt-1 text-xs font-medium text-blue-700 dark:text-blue-300">{getQuizReadinessHint(item)}</p>
          </Link>
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
  const authUser = getAuthUser();
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
    const params = new URLSearchParams({ notes: quizReadyNoteIds.join(",") });
    router.push(`/library/exam-builder?${params.toString()}`);
  }, [quizReadyNoteIds, router]);

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
        description={collection.description || `Organize the notes in this ${labels.singular.toLowerCase()}.`}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            {terminalAction?.kind === "exam-builder" ? (
              <div className="max-w-xs space-y-1">
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
            ) : null}
            <ResponsiveActionButton action="edit" label="Edit" variant="outline" onClick={() => setEditOpen(true)} />
          </div>
        )}
      />

      <div className="flex justify-end">
        <ResponsiveActionButton action="delete" label={`Delete ${labels.singular}`} variant="ghost" onClick={() => setDeleteOpen(true)} />
      </div>

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
    </main>
  );
}
