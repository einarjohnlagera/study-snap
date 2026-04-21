"use client";

import type { CSSProperties } from "react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import {
  closestCenter,
  DndContext,
  DragOverlay,
  type DragEndEvent,
  type DragOverEvent,
  KeyboardSensor,
  PointerSensor,
  TouchSensor,
  useDroppable,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import { restrictToVerticalAxis } from "@dnd-kit/modifiers";
import {
  sortableKeyboardCoordinates,
  SortableContext,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { ArrowDown, ArrowUp, GripVertical, Plus, Trash2 } from "lucide-react";
import { AppModal } from "@/components/ui/app-modal";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";
import { getAuthUser } from "@/lib/auth";
import {
  exportCombinedGeneratedQuizDocx,
  listNotes,
  type NoteListItemResponse,
} from "@/lib/api";
import { normalizeCourseProgram } from "@/lib/learning-profile";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { normalizeSubject } from "@/lib/subjects";
import {
  buildDefaultSectionTitle,
  createExamSection,
  createDefaultExamSections,
  deleteExamSection,
  flattenExamSectionNoteIds,
  type ExamBuilderSection,
  moveNoteToSection,
  moveNoteWithinSection,
  removeNoteFromExamSections,
  renameExamSection,
  reorderExamSections,
} from "../exam-builder-order";

type ExamBuilderSelection = {
  noteId: string;
  title: string;
  subject: string;
  questionCount: number | null;
};

const SUBJECT_FALLBACK = "General";

function canIncludeInExam(item: NoteListItemResponse): boolean {
  return Boolean(item.generatedQuizId);
}

function getLibrarySubject(item: Pick<NoteListItemResponse, "subject" | "courseProgram">): string {
  return normalizeSubject(item.subject)
    ?? normalizeCourseProgram(item.courseProgram)
    ?? SUBJECT_FALLBACK;
}

function getSectionSortableId(sectionId: string) {
  return `section:${sectionId}`;
}

function getNoteSortableId(noteId: string) {
  return `note:${noteId}`;
}

function getSectionNoteDropzoneId(sectionId: string) {
  return `section-notes:${sectionId}`;
}

function parseSelectedNoteIds(value: string | null): string[] {
  if (!value) {
    return [];
  }
  return value
    .split(",")
    .map((noteId) => noteId.trim())
    .filter((noteId, index, allIds) => noteId.length > 0 && allIds.indexOf(noteId) === index);
}

type SortableExamBuilderItemProps = {
  sectionId: string;
  note: ExamBuilderSelection;
  index: number;
  totalCount: number;
  exporting: boolean;
  activeDragNoteId: string | null;
  onMove: (noteId: string, direction: "up" | "down") => void;
  onRemove: (noteId: string) => void;
};

function SortableExamBuilderItem({
  sectionId,
  note,
  index,
  totalCount,
  exporting,
  activeDragNoteId,
  onMove,
  onRemove,
}: Readonly<SortableExamBuilderItemProps>) {
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({
    id: getNoteSortableId(note.noteId),
    data: {
      type: "note",
      noteId: note.noteId,
      sectionId,
    },
  });

  const style: CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={`flex w-full min-w-0 items-start justify-between gap-3 rounded-2xl border border-border p-3 transition-shadow ${
        isDragging
          ? "opacity-40 border-dashed border-blue-400 bg-blue-50/40 shadow-sm dark:bg-blue-950/20"
          : activeDragNoteId === note.noteId
            ? "bg-background shadow-md ring-1 ring-blue-500/30"
            : "bg-background shadow-sm"
      }`}
    >
      <div className="flex min-w-0 flex-1 items-start gap-3">
        <button
          ref={setActivatorNodeRef}
          type="button"
          className="mt-0.5 inline-flex h-9 w-9 shrink-0 cursor-grab touch-none items-center justify-center rounded-lg border border-border bg-background text-foreground/65 transition-colors hover:bg-highlight hover:text-foreground active:cursor-grabbing focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 disabled:pointer-events-none disabled:opacity-50"
          aria-label={`Drag ${note.title}`}
          disabled={exporting}
          {...attributes}
          {...listeners}
        >
          <GripVertical className="h-4 w-4" aria-hidden="true" />
        </button>
        <div className="min-w-0 flex-1 space-y-1">
          <p className="text-xs font-semibold uppercase tracking-wide text-foreground/50">Note {index + 1}</p>
          <p className="block truncate text-sm font-medium text-foreground">{note.title}</p>
          <div className="flex flex-wrap items-center gap-2 text-xs text-foreground/60">
            <p className="truncate">{note.subject}</p>
            {typeof note.questionCount === "number" ? (
              <span className="rounded-full border border-border bg-muted/50 px-2 py-0.5 text-[11px] font-medium text-foreground/70">
                {note.questionCount} question{note.questionCount === 1 ? "" : "s"}
              </span>
            ) : null}
          </div>
        </div>
      </div>
      <div className="flex shrink-0 items-center gap-1">
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="h-9 w-9 p-0"
          onClick={() => onMove(note.noteId, "up")}
          disabled={index === 0 || exporting}
          aria-label={`Move ${note.title} up`}
        >
          <ArrowUp className="h-4 w-4" aria-hidden="true" />
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="h-9 w-9 p-0"
          onClick={() => onMove(note.noteId, "down")}
          disabled={index === totalCount - 1 || exporting}
          aria-label={`Move ${note.title} down`}
        >
          <ArrowDown className="h-4 w-4" aria-hidden="true" />
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="h-9 w-9 p-0"
          onClick={() => onRemove(note.noteId)}
          disabled={exporting}
          aria-label={`Remove ${note.title}`}
        >
          <Trash2 className="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>
    </div>
  );
}

type SectionNoteDropzoneProps = {
  sectionId: string;
  isActive: boolean;
  children: React.ReactNode;
};

function SectionNoteDropzone({ sectionId, isActive, children }: Readonly<SectionNoteDropzoneProps>) {
  const { isOver, setNodeRef } = useDroppable({
    id: getSectionNoteDropzoneId(sectionId),
    data: {
      type: "section-dropzone",
      sectionId,
    },
  });

  return (
    <div
      ref={setNodeRef}
      className={`w-full space-y-3 rounded-2xl border p-3 transition-colors ${
        isOver
          ? "border-dashed border-blue-400 bg-blue-50/40 dark:bg-blue-950/20"
          : isActive
            ? "border-dashed border-border/50 bg-transparent"
            : "border-transparent bg-transparent"
      }`}
    >
      {children}
    </div>
  );
}

type SortableExamBuilderSectionProps = {
  section: ExamBuilderSection;
  sectionIndex: number;
  notes: ExamBuilderSelection[];
  exporting: boolean;
  activeDragNoteId: string | null;
  activeDragSectionId: string | null;
  onRename: (sectionId: string, title: string) => void;
  onDelete: (sectionId: string) => void;
  onAddBelow: (sectionId: string) => void;
  onMoveNote: (sectionId: string, noteId: string, direction: "up" | "down") => void;
  onRemoveNote: (noteId: string) => void;
};

function SortableExamBuilderSection({
  section,
  sectionIndex,
  notes,
  exporting,
  activeDragNoteId,
  activeDragSectionId,
  onRename,
  onDelete,
  onAddBelow,
  onMoveNote,
  onRemoveNote,
}: Readonly<SortableExamBuilderSectionProps>) {
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
    isOver,
  } = useSortable({
    id: getSectionSortableId(section.id),
    data: {
      type: "section",
      sectionId: section.id,
    },
  });

  const style: CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={`w-full min-w-0 space-y-4 rounded-2xl border border-border bg-background p-4 shadow-sm transition-all ${
        isDragging
          ? "opacity-40 shadow-lg ring-1 ring-blue-500/30"
          : isOver && activeDragSectionId !== null
            ? "ring-2 ring-blue-500/40"
            : ""
      }`}
    >
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex min-w-0 flex-1 items-start gap-3">
          <button
            ref={setActivatorNodeRef}
            type="button"
            className="mt-0.5 inline-flex h-9 w-9 shrink-0 cursor-grab touch-none items-center justify-center rounded-lg border border-border bg-background text-foreground/65 transition-colors hover:bg-highlight hover:text-foreground active:cursor-grabbing focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 disabled:pointer-events-none disabled:opacity-50"
            aria-label={`Drag ${section.title}`}
            disabled={exporting}
            {...attributes}
            {...listeners}
          >
            <GripVertical className="h-4 w-4" aria-hidden="true" />
          </button>
          <div className="min-w-0 flex-1 space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/50">Section {sectionIndex + 1}</p>
            <input
              type="text"
              value={section.title}
              onChange={(event) => onRename(section.id, event.target.value)}
              disabled={exporting}
              aria-label={`Section title ${sectionIndex + 1}`}
              className="h-10 w-full min-w-0 rounded-lg border border-border bg-background px-3 text-sm font-medium text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
              placeholder={buildDefaultSectionTitle(sectionIndex)}
            />
          </div>
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-2 sm:flex-nowrap">
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="gap-2"
            onClick={() => onAddBelow(section.id)}
            disabled={exporting}
          >
            <Plus className="h-4 w-4" aria-hidden="true" />
            <span>Add Section</span>
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="h-9 w-9 p-0"
            onClick={() => onDelete(section.id)}
            disabled={exporting}
            aria-label={`Delete ${section.title}`}
          >
            <Trash2 className="h-4 w-4" aria-hidden="true" />
          </Button>
        </div>
      </div>

      <SectionNoteDropzone sectionId={section.id} isActive={activeDragNoteId !== null}>
        {notes.length === 0 ? (
          <p className="rounded-xl border border-dashed border-border bg-muted/20 px-3 py-4 text-sm text-foreground/65">
            Drag notes here
          </p>
        ) : (
          <SortableContext items={notes.map((note) => getNoteSortableId(note.noteId))} strategy={verticalListSortingStrategy}>
            {notes.map((note, index) => (
              <SortableExamBuilderItem
                key={note.noteId}
                sectionId={section.id}
                note={note}
                index={index}
                totalCount={notes.length}
                exporting={exporting}
                activeDragNoteId={activeDragNoteId}
                onMove={(noteId, direction) => onMoveNote(section.id, noteId, direction)}
                onRemove={onRemoveNote}
              />
            ))}
          </SortableContext>
        )}
      </SectionNoteDropzone>
    </div>
  );
}

export default function ExamBuilderPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const authUser = getAuthUser();
  const isTeacherExamBuilderEnabled = authUser?.profileType === "TEACHER";
  const notesParam = searchParams.get("notes");
  const selectedNoteIds = useMemo(() => parseSelectedNoteIds(notesParam), [notesParam]);
  const [items, setItems] = useState<NoteListItemResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [examSections, setExamSections] = useState<ExamBuilderSection[]>([]);
  const [pendingDeleteSectionId, setPendingDeleteSectionId] = useState<string | null>(null);
  const [includeAnswerKey, setIncludeAnswerKey] = useState(true);
  const [includeExplanations, setIncludeExplanations] = useState(true);
  const [exportingExam, setExportingExam] = useState(false);
  const [activeDragNoteId, setActiveDragNoteId] = useState<string | null>(null);
  const [activeDragSectionId, setActiveDragSectionId] = useState<string | null>(null);
  const [nextSectionIndex, setNextSectionIndex] = useState(1);
  const [toast, setToast] = useState<string | null>(null);
  const examBuilderSensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 200, tolerance: 8 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  useEffect(() => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
    if (!isTeacherExamBuilderEnabled) {
      router.replace("/library");
      return;
    }
    setExamSections(createDefaultExamSections(selectedNoteIds));
    setNextSectionIndex(1);
    setPendingDeleteSectionId(null);
    setIncludeAnswerKey(true);
    setIncludeExplanations(true);
  }, [isTeacherExamBuilderEnabled, router, selectedNoteIds]);

  useEffect(() => {
    if (!requireAuthenticatedOnboardedUser(router) || !isTeacherExamBuilderEnabled) {
      return;
    }
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const notes = await listNotes();
        if (!cancelled) {
          setItems(notes);
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : "Could not load selected notes.");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [isTeacherExamBuilderEnabled, router]);

  useEffect(() => {
    if (!toast) {
      return undefined;
    }
    const timeoutId = globalThis.setTimeout(() => setToast(null), 2200);
    return () => globalThis.clearTimeout(timeoutId);
  }, [toast]);

  const itemsById = useMemo(
    () => new Map(items.filter((item) => canIncludeInExam(item)).map((item) => [item.id, item])),
    [items],
  );
  const selectedNoteMetaById = useMemo(() => {
    return Object.fromEntries(
      selectedNoteIds
        .map((noteId) => itemsById.get(noteId))
        .filter((item): item is NoteListItemResponse => Boolean(item))
        .map((item) => [item.id, {
          noteId: item.id,
          title: item.title?.trim() || "Untitled note",
          subject: getLibrarySubject(item),
          questionCount: item.generatedQuizQuestionCount ?? null,
        } satisfies ExamBuilderSelection]),
    ) as Record<string, ExamBuilderSelection>;
  }, [itemsById, selectedNoteIds]);
  const exportableExamSections = useMemo(() => {
    return examSections
      .map((section, index) => ({
        ...section,
        title: section.title.trim() || buildDefaultSectionTitle(index),
        noteIds: section.noteIds.filter((noteId) => Boolean(selectedNoteMetaById[noteId])),
      }))
      .filter((section) => section.noteIds.length > 0);
  }, [examSections, selectedNoteMetaById]);
  const pendingDeleteSection = useMemo(
    () => examSections.find((section) => section.id === pendingDeleteSectionId) ?? null,
    [examSections, pendingDeleteSectionId],
  );
  const builderNoteCount = useMemo(
    () => flattenExamSectionNoteIds(exportableExamSections).length,
    [exportableExamSections],
  );

  const updateExamSections = useCallback((nextSections: ExamBuilderSection[]) => {
    setExamSections(nextSections);
  }, []);

  const handleAddSectionBelow = useCallback((sectionId: string) => {
    setExamSections((previous) => {
      const sectionIndex = previous.findIndex((section) => section.id === sectionId);
      const insertIndex = sectionIndex < 0 ? previous.length : sectionIndex + 1;
      const nextSection = createExamSection(nextSectionIndex);
      const next = [...previous];
      next.splice(insertIndex, 0, nextSection);
      return next;
    });
    setNextSectionIndex((previous) => previous + 1);
  }, [nextSectionIndex]);

  const handleDeleteSectionRequest = useCallback((sectionId: string) => {
    const section = examSections.find((candidate) => candidate.id === sectionId);
    if (!section) {
      return;
    }
    if (section.noteIds.length === 0) {
      updateExamSections(deleteExamSection(examSections, sectionId, "delete_notes"));
      return;
    }
    setPendingDeleteSectionId(sectionId);
  }, [examSections, updateExamSections]);

  const handleConfirmDeleteSection = useCallback((strategy: "move_notes" | "delete_notes") => {
    if (!pendingDeleteSectionId) {
      return;
    }
    updateExamSections(deleteExamSection(examSections, pendingDeleteSectionId, strategy));
    setPendingDeleteSectionId(null);
  }, [examSections, pendingDeleteSectionId, updateExamSections]);

  const handleExportExam = useCallback(async () => {
    if (exportableExamSections.length === 0 || exportingExam) {
      return;
    }
    setExportingExam(true);
    setError(null);
    try {
      await exportCombinedGeneratedQuizDocx({
        sections: exportableExamSections.map((section) => ({
          title: section.title,
          noteIds: section.noteIds,
        })),
        includeAnswerKey,
        includeExplanations,
      });
      setToast("Exam DOCX ready.");
    } catch (exportError) {
      setError(exportError instanceof Error ? exportError.message : "Could not export exam.");
    } finally {
      setExportingExam(false);
    }
  }, [exportableExamSections, exportingExam, includeAnswerKey, includeExplanations]);

  const handleExamBuilderDragOver = useCallback((event: DragOverEvent) => {
    const activeData = event.active.data.current;
    const overData = event.over?.data.current;
    if (activeData?.type !== "note" || !overData) {
      return;
    }
    const activeNoteId = String(activeData.noteId);
    const targetSectionId = String(overData.sectionId);
    if (!activeNoteId || !targetSectionId) {
      return;
    }
    const targetSection = examSections.find((section) => section.id === targetSectionId);
    if (!targetSection) {
      return;
    }
    const targetIndex = overData.type === "note"
      ? targetSection.noteIds.findIndex((noteId) => noteId === String(overData.noteId))
      : targetSection.noteIds.length;
    if (targetIndex < 0) {
      return;
    }
    updateExamSections(moveNoteToSection(examSections, activeNoteId, targetSectionId, targetIndex));
  }, [examSections, updateExamSections]);

  const handleExamBuilderDragEnd = useCallback((event: DragEndEvent) => {
    const activeData = event.active.data.current;
    const overData = event.over?.data.current;
    setActiveDragNoteId(null);
    setActiveDragSectionId(null);
    if (!activeData) {
      return;
    }
    if (activeData.type === "note") {
      if (!overData) {
        return;
      }
      const targetSectionId = String(overData.sectionId);
      const targetSection = examSections.find((section) => section.id === targetSectionId);
      if (!targetSection) {
        return;
      }
      const targetIndex = overData.type === "note"
        ? targetSection.noteIds.findIndex((noteId) => noteId === String(overData.noteId))
        : targetSection.noteIds.length;
      if (targetIndex < 0) {
        return;
      }
      updateExamSections(moveNoteToSection(
        examSections,
        String(activeData.noteId),
        targetSectionId,
        targetIndex,
      ));
      return;
    }
    if (activeData.type === "section") {
      if (!overData || overData.type !== "section") {
        return;
      }
      updateExamSections(reorderExamSections(
        examSections,
        String(activeData.sectionId),
        String(overData.sectionId),
      ));
    }
  }, [examSections, updateExamSections]);

  const handleBackToLibrary = useCallback(() => {
    router.push("/library");
  }, [router]);

  if (!isTeacherExamBuilderEnabled) {
    return null;
  }

  return (
    <main className="mx-auto w-full max-w-6xl space-y-6 px-4 py-6 pb-32 sm:px-6 sm:py-10 sm:pb-36">
      <BackLink href="/library" label="Library" />
      <PageHeader
        eyebrow="EXAM BUILDER"
        title="Exam Builder"
        description="Create a structured exam from selected notes."
      />

      {loading ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <div className="h-6 w-40 animate-pulse rounded bg-foreground/10" />
          <div className="h-4 w-72 animate-pulse rounded bg-foreground/10" />
          <div className="space-y-3">
            {Array.from({ length: 2 }).map((_, index) => (
              <div key={`builder-skeleton-${index}`} className="h-28 rounded-2xl border border-border bg-background/80" />
            ))}
          </div>
        </Card>
      ) : error ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h2 className="text-xl font-semibold">Could not load exam builder</h2>
          <p className="text-sm text-foreground/75">{error}</p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" onClick={() => globalThis.location.reload()}>
              Retry
            </Button>
            <Button type="button" variant="outline" onClick={handleBackToLibrary}>
              Back to Library
            </Button>
          </div>
        </Card>
      ) : builderNoteCount === 0 ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h2 className="text-xl font-semibold">No quiz-ready notes selected</h2>
          <p className="text-sm text-foreground/75">
            Select quiz-ready notes from your library to start building an exam.
          </p>
          <Button type="button" onClick={handleBackToLibrary}>
            Back to Library
          </Button>
        </Card>
      ) : (
        <>
          <section className="w-full space-y-3 rounded-2xl border border-border bg-muted/20 p-4 shadow-sm sm:p-5">
            <div className="space-y-1">
              <h2 className="text-base font-semibold text-foreground sm:text-lg">Selected Notes</h2>
              <p className="text-sm text-foreground/70">Drag to reorder sections and move notes across sections.</p>
            </div>

            <DndContext
              sensors={examBuilderSensors}
              collisionDetection={closestCenter}
              modifiers={[restrictToVerticalAxis]}
              onDragStart={(event) => {
                const data = event.active.data.current;
                if (data?.type === "note") {
                  setActiveDragNoteId(String(data.noteId));
                  setActiveDragSectionId(null);
                  return;
                }
                if (data?.type === "section") {
                  setActiveDragSectionId(String(data.sectionId));
                  setActiveDragNoteId(null);
                }
              }}
              onDragCancel={() => {
                setActiveDragNoteId(null);
                setActiveDragSectionId(null);
              }}
              onDragOver={handleExamBuilderDragOver}
              onDragEnd={handleExamBuilderDragEnd}
            >
              <SortableContext items={examSections.map((section) => getSectionSortableId(section.id))} strategy={verticalListSortingStrategy}>
                <div className={`w-full space-y-4 rounded-2xl border border-border/70 bg-background/80 p-3 transition-colors sm:p-4 ${
                  activeDragNoteId || activeDragSectionId ? "border-blue-400 bg-blue-50/40 dark:bg-blue-950/20" : ""
                }`}>
                  {examSections.map((section, sectionIndex) => (
                    <SortableExamBuilderSection
                      key={section.id}
                      section={section}
                      sectionIndex={sectionIndex}
                      notes={section.noteIds
                        .map((noteId) => selectedNoteMetaById[noteId])
                        .filter((note): note is ExamBuilderSelection => Boolean(note))}
                      exporting={exportingExam}
                      activeDragNoteId={activeDragNoteId}
                      activeDragSectionId={activeDragSectionId}
                      onRename={(sectionId, title) => updateExamSections(renameExamSection(examSections, sectionId, title))}
                      onDelete={handleDeleteSectionRequest}
                      onAddBelow={handleAddSectionBelow}
                      onMoveNote={(sectionId, noteId, direction) => updateExamSections(
                        moveNoteWithinSection(examSections, sectionId, noteId, direction),
                      )}
                      onRemoveNote={(noteId) => updateExamSections(removeNoteFromExamSections(examSections, noteId))}
                    />
                  ))}
                </div>
              </SortableContext>
              <DragOverlay>
                {activeDragNoteId ? (
                  <div className="flex min-w-[280px] scale-[1.02] items-start justify-between gap-3 rounded-2xl border border-blue-400 bg-background p-3 shadow-xl">
                    <div className="flex min-w-0 flex-1 items-start gap-3">
                      <span className="mt-0.5 inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-border bg-background text-foreground/65">
                        <GripVertical className="h-4 w-4" aria-hidden="true" />
                      </span>
                      <div className="min-w-0 flex-1 space-y-1">
                        <p className="text-xs font-semibold uppercase tracking-wide text-foreground/50">Note</p>
                        <p className="truncate text-sm font-medium text-foreground">
                          {selectedNoteMetaById[activeDragNoteId]?.title}
                        </p>
                      </div>
                    </div>
                  </div>
                ) : activeDragSectionId ? (
                  <div className="min-w-[280px] scale-[1.02] rounded-2xl border border-blue-400 bg-background p-4 shadow-xl">
                    <p className="text-xs font-semibold uppercase tracking-wide text-foreground/50">Section</p>
                    <p className="truncate text-sm font-medium text-foreground">
                      {examSections.find((section) => section.id === activeDragSectionId)?.title}
                    </p>
                  </div>
                ) : null}
              </DragOverlay>
            </DndContext>
          </section>

          <section className="w-full space-y-3">
            <div>
              <h2 className="text-base font-semibold text-foreground sm:text-lg">Exam Options</h2>
            </div>
            <div className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-background shadow-sm">
              <label className="flex cursor-pointer items-start gap-3 px-4 py-3">
                <input
                  type="checkbox"
                  checked={includeAnswerKey}
                  onChange={(event) => setIncludeAnswerKey(event.target.checked)}
                  className="mt-0.5 h-4 w-4 rounded border-border text-blue-600 focus:ring-2 focus:ring-blue-600"
                />
                <span className="space-y-0.5">
                  <span className="block text-sm font-medium text-foreground">Include Answer Key</span>
                  <span className="block text-xs text-foreground/65">Add the correct answers after the exam.</span>
                </span>
              </label>
              <label className="flex cursor-pointer items-start gap-3 px-4 py-3">
                <input
                  type="checkbox"
                  checked={includeExplanations}
                  onChange={(event) => setIncludeExplanations(event.target.checked)}
                  className="mt-0.5 h-4 w-4 rounded border-border text-blue-600 focus:ring-2 focus:ring-blue-600"
                />
                <span className="space-y-0.5">
                  <span className="block text-sm font-medium text-foreground">Include Explanations</span>
                  <span className="block text-xs text-foreground/65">Append brief teaching explanations on a separate page.</span>
                </span>
              </label>
            </div>
          </section>
        </>
      )}

      <div className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-background/95 backdrop-blur">
        <div className="mx-auto flex w-full max-w-6xl flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <div className="space-y-1">
            <p className="text-sm font-medium text-foreground">
              {builderNoteCount === 0
                ? "Select notes from your library to start building an exam."
                : `${builderNoteCount} note${builderNoteCount === 1 ? "" : "s"} across ${exportableExamSections.length} section${exportableExamSections.length === 1 ? "" : "s"}`}
            </p>
            <p className="text-xs text-foreground/65">Keep editing, then export the combined DOCX when everything is ready.</p>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" variant="ghost" onClick={handleBackToLibrary} disabled={exportingExam}>
              Cancel
            </Button>
            <Button
              type="button"
              className="w-full sm:w-auto"
              onClick={() => void handleExportExam()}
              loading={exportingExam}
              loadingText="Exporting..."
              disabled={builderNoteCount === 0}
            >
              Export Exam
            </Button>
          </div>
        </div>
      </div>

      <AppModal
        isOpen={pendingDeleteSection !== null}
        title="Delete section?"
        description={
          pendingDeleteSection?.noteIds.length
            ? "This section still has notes. Move them to the previous section or remove them from the exam."
            : "Remove this empty section from the exam builder."
        }
        onClose={() => {
          if (!exportingExam) {
            setPendingDeleteSectionId(null);
          }
        }}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="ghost" onClick={() => setPendingDeleteSectionId(null)} disabled={exportingExam}>
              Cancel
            </Button>
            {pendingDeleteSection?.noteIds.length ? (
              <Button
                type="button"
                variant="outline"
                onClick={() => handleConfirmDeleteSection("move_notes")}
                disabled={exportingExam}
              >
                Move Notes
              </Button>
            ) : null}
            <Button
              type="button"
              variant={pendingDeleteSection?.noteIds.length ? "outline" : "default"}
              className={pendingDeleteSection?.noteIds.length ? "text-red-700 hover:text-red-800 dark:text-red-300 dark:hover:text-red-200" : undefined}
              onClick={() => handleConfirmDeleteSection("delete_notes")}
              disabled={exportingExam}
            >
              {pendingDeleteSection?.noteIds.length ? "Delete Notes" : "Delete Section"}
            </Button>
          </div>
        )}
      >
        <p className="text-sm text-foreground/70">
          {pendingDeleteSection?.noteIds.length
            ? `${pendingDeleteSection.noteIds.length} note${pendingDeleteSection.noteIds.length === 1 ? "" : "s"} will be affected.`
            : "This section has no notes, so it can be removed immediately."}
        </p>
      </AppModal>

      {toast ? (
        <div role="status" aria-live="polite" className="fixed right-4 top-4 z-40 rounded-md border border-border bg-background px-3 py-2 text-sm shadow-sm">
          {toast}
        </div>
      ) : null}
    </main>
  );
}
