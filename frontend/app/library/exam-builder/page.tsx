"use client";

import type { CSSProperties, ReactNode } from "react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import {
  closestCenter,
  DndContext,
  DragOverlay,
  type DragEndEvent,
  type DragOverEvent,
  PointerSensor,
  TouchSensor,
  useDroppable,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import { restrictToVerticalAxis } from "@dnd-kit/modifiers";
import {
  SortableContext,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { ArrowDown, ArrowUp, GripVertical, Plus, Shuffle, Trash2 } from "lucide-react";
import { AppModal } from "@/components/ui/app-modal";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";
import { getAuthUser } from "@/lib/auth";
import {
  exportCombinedGeneratedQuizDocx,
  getGeneratedQuiz,
  listNotes,
  type GeneratedQuizResponse,
  type NoteListItemResponse,
} from "@/lib/api";
import { normalizeCourseProgram } from "@/lib/learning-profile";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { normalizeSubject } from "@/lib/subjects";
import {
  buildDefaultSectionTitle,
  countExamSectionQuestions,
  createExamSection,
  createExamQuestionRefKey,
  deleteExamSection,
  evenBalanceExamSections,
  type ExamBalanceMode,
  type ExamBuilderSection,
  flattenExamSectionQuestionRefs,
  moveEntryToSection,
  moveEntryWithinSection,
  removeEntryFromExamSections,
  renameExamSection,
  reorderExamSections,
  smartBalanceExamSections,
} from "../exam-builder-order";
import {
  buildTemplateSections,
  EXAM_TEMPLATES,
  getExamTemplateById,
  type ExamTemplateId,
} from "./templates";

type ExamBuilderSelection = {
  entryId: string;
  noteId: string;
  title: string;
  subject: string;
  assignedQuestionCount: number;
  totalQuestionCount: number;
};

const SUBJECT_FALLBACK = "General";
const EXAM_EXPORT_READY_MESSAGE = "Exam DOCX ready.";
const SMART_BALANCE_MODE: ExamBalanceMode = "SMART";
const EVEN_BALANCE_MODE: ExamBalanceMode = "EVEN";

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

function getEntrySortableId(entryId: string) {
  return `entry:${entryId}`;
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

function buildQuestionCountLabel(assignedQuestionCount: number, totalQuestionCount: number): string {
  if (assignedQuestionCount === totalQuestionCount) {
    return `${assignedQuestionCount} question${assignedQuestionCount === 1 ? "" : "s"}`;
  }
  return `${assignedQuestionCount} assigned`;
}

type SortableExamBuilderItemProps = {
  sectionId: string;
  entry: ExamBuilderSelection;
  index: number;
  totalCount: number;
  exporting: boolean;
  activeDragEntryId: string | null;
  onMove: (entryId: string, direction: "up" | "down") => void;
  onRemove: (entryId: string) => void;
};

function SortableExamBuilderItem({
  sectionId,
  entry,
  index,
  totalCount,
  exporting,
  activeDragEntryId,
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
    id: getEntrySortableId(entry.entryId),
    data: {
      type: "entry",
      entryId: entry.entryId,
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
          ? "border-dashed border-blue-400 bg-blue-50/40 opacity-40 shadow-sm dark:bg-blue-950/20"
          : activeDragEntryId === entry.entryId
            ? "bg-background shadow-md ring-1 ring-blue-500/30"
            : "bg-background shadow-sm"
      }`}
    >
      <div className="flex min-w-0 flex-1 items-start gap-3">
        <button
          ref={setActivatorNodeRef}
          type="button"
          className="mt-0.5 inline-flex h-9 w-9 shrink-0 cursor-grab touch-none items-center justify-center rounded-lg border border-border bg-background text-foreground/65 transition-colors hover:bg-highlight hover:text-foreground active:cursor-grabbing focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 disabled:pointer-events-none disabled:opacity-50"
          aria-label={`Drag ${entry.title}`}
          disabled={exporting}
          {...attributes}
          {...listeners}
        >
          <GripVertical className="h-4 w-4" aria-hidden="true" />
        </button>
        <div className="min-w-0 flex-1 space-y-1">
          <p className="text-xs font-semibold uppercase tracking-wide text-foreground/50">Note {index + 1}</p>
          <p className="block truncate text-sm font-medium text-foreground">{entry.title}</p>
          <div className="flex flex-wrap items-center gap-2 text-xs text-foreground/60">
            <p className="truncate">{entry.subject}</p>
            <span className="rounded-full border border-border bg-muted/50 px-2 py-0.5 text-[11px] font-medium text-foreground/70">
              {buildQuestionCountLabel(entry.assignedQuestionCount, entry.totalQuestionCount)}
            </span>
          </div>
        </div>
      </div>
      <div className="flex shrink-0 items-center gap-1">
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="h-9 w-9 p-0"
          onClick={() => onMove(entry.entryId, "up")}
          disabled={index === 0 || exporting}
          aria-label={`Move ${entry.title} up`}
        >
          <ArrowUp className="h-4 w-4" aria-hidden="true" />
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="h-9 w-9 p-0"
          onClick={() => onMove(entry.entryId, "down")}
          disabled={index === totalCount - 1 || exporting}
          aria-label={`Move ${entry.title} down`}
        >
          <ArrowDown className="h-4 w-4" aria-hidden="true" />
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="h-9 w-9 p-0"
          onClick={() => onRemove(entry.entryId)}
          disabled={exporting}
          aria-label={`Remove ${entry.title}`}
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
  children: ReactNode;
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
  entries: ExamBuilderSelection[];
  exporting: boolean;
  activeDragEntryId: string | null;
  activeDragSectionId: string | null;
  onRename: (sectionId: string, title: string) => void;
  onDelete: (sectionId: string) => void;
  onAddBelow: (sectionId: string) => void;
  onMoveEntry: (sectionId: string, entryId: string, direction: "up" | "down") => void;
  onRemoveEntry: (entryId: string) => void;
};

function SortableExamBuilderSection({
  section,
  sectionIndex,
  entries,
  exporting,
  activeDragEntryId,
  activeDragSectionId,
  onRename,
  onDelete,
  onAddBelow,
  onMoveEntry,
  onRemoveEntry,
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

      <SectionNoteDropzone sectionId={section.id} isActive={activeDragEntryId !== null}>
        {entries.length === 0 ? (
          <p className="rounded-xl border border-dashed border-border bg-muted/20 px-3 py-4 text-sm text-foreground/65">
            Drag notes here
          </p>
        ) : (
          <SortableContext items={entries.map((entry) => getEntrySortableId(entry.entryId))} strategy={verticalListSortingStrategy}>
            {entries.map((entry, index) => (
              <SortableExamBuilderItem
                key={entry.entryId}
                sectionId={section.id}
                entry={entry}
                index={index}
                totalCount={entries.length}
                exporting={exporting}
                activeDragEntryId={activeDragEntryId}
                onMove={(entryId, direction) => onMoveEntry(section.id, entryId, direction)}
                onRemove={onRemoveEntry}
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
  const [generatedQuizByNoteId, setGeneratedQuizByNoteId] = useState<Record<string, GeneratedQuizResponse>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [examSections, setExamSections] = useState<ExamBuilderSection[]>([]);
  const [pendingDeleteSectionId, setPendingDeleteSectionId] = useState<string | null>(null);
  const [pendingTemplateId, setPendingTemplateId] = useState<ExamTemplateId | null>(null);
  const [selectedTemplateId, setSelectedTemplateId] = useState<ExamTemplateId | null>(null);
  const [templateDirty, setTemplateDirty] = useState(false);
  const [showTemplateSelector, setShowTemplateSelector] = useState(true);
  const [includeAnswerKey, setIncludeAnswerKey] = useState(true);
  const [includeExplanations, setIncludeExplanations] = useState(true);
  const [exportingExam, setExportingExam] = useState(false);
  const [activeDragEntryId, setActiveDragEntryId] = useState<string | null>(null);
  const [activeDragSectionId, setActiveDragSectionId] = useState<string | null>(null);
  const [nextSectionIndex, setNextSectionIndex] = useState(1);
  const [toast, setToast] = useState<string | null>(null);
  const [pendingBalanceMode, setPendingBalanceMode] = useState<ExamBalanceMode | null>(null);
  const examBuilderSensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 200, tolerance: 8 } }),
  );

  useEffect(() => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
    if (!isTeacherExamBuilderEnabled) {
      router.replace("/library");
      return;
    }
    setExamSections([]);
    setGeneratedQuizByNoteId({});
    setNextSectionIndex(1);
    setPendingDeleteSectionId(null);
    setPendingTemplateId(null);
    setSelectedTemplateId(null);
    setTemplateDirty(false);
    setShowTemplateSelector(true);
    setIncludeAnswerKey(true);
    setIncludeExplanations(true);
    setPendingBalanceMode(null);
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
        const selectedQuizReadyNotes = notes
          .filter((item) => selectedNoteIds.includes(item.id) && canIncludeInExam(item));
        const generatedQuizzes = await Promise.all(
          selectedQuizReadyNotes.map(async (item) => [item.id, await getGeneratedQuiz(item.id)] as const),
        );
        if (cancelled) {
          return;
        }
        setItems(notes);
        setGeneratedQuizByNoteId(Object.fromEntries(generatedQuizzes));
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : "Could not load selected quizzes.");
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
  }, [isTeacherExamBuilderEnabled, router, selectedNoteIds]);

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

  const questionCountsByNoteId = useMemo(() => {
    return Object.fromEntries(
      Object.entries(generatedQuizByNoteId).map(([noteId, generatedQuiz]) => [noteId, generatedQuiz.questions.length]),
    ) as Record<string, number>;
  }, [generatedQuizByNoteId]);

  const selectedNoteMetaById = useMemo(() => {
    return Object.fromEntries(
      selectedNoteIds
        .map((noteId) => itemsById.get(noteId))
        .filter((item): item is NoteListItemResponse => Boolean(item))
        .map((item) => ({
          item,
          generatedQuiz: generatedQuizByNoteId[item.id],
        }))
        .filter((value): value is { item: NoteListItemResponse; generatedQuiz: GeneratedQuizResponse } => Boolean(value.generatedQuiz))
        .map(({ item, generatedQuiz }) => [item.id, {
          noteId: item.id,
          title: item.title?.trim() || "Untitled note",
          subject: getLibrarySubject(item),
          totalQuestionCount: generatedQuiz.questions.length,
        }]),
    ) as Record<string, { noteId: string; title: string; subject: string; totalQuestionCount: number }>;
  }, [generatedQuizByNoteId, itemsById, selectedNoteIds]);

  const selectedReadyNoteIds = useMemo(
    () => selectedNoteIds.filter((noteId) => Boolean(selectedNoteMetaById[noteId])),
    [selectedNoteIds, selectedNoteMetaById],
  );

  const exportableExamSections = useMemo(() => {
    return examSections
      .map((section, index) => ({
        ...section,
        title: section.title.trim() || buildDefaultSectionTitle(index),
        entries: section.entries.filter((entry) => entry.questionRefs.length > 0 && Boolean(selectedNoteMetaById[entry.noteId])),
      }))
      .filter((section) => section.entries.length > 0);
  }, [examSections, selectedNoteMetaById]);

  const pendingDeleteSection = useMemo(
    () => examSections.find((section) => section.id === pendingDeleteSectionId) ?? null,
    [examSections, pendingDeleteSectionId],
  );

  const selectedTemplate = useMemo(
    () => (selectedTemplateId ? getExamTemplateById(selectedTemplateId) : null),
    [selectedTemplateId],
  );

  const builderQuestionCount = useMemo(
    () => countExamSectionQuestions(exportableExamSections),
    [exportableExamSections],
  );

  const builderUniqueNoteCount = useMemo(
    () => new Set(flattenExamSectionQuestionRefs(exportableExamSections).map((questionRef) => questionRef.noteId)).size,
    [exportableExamSections],
  );

  const questionBalanceMetadataByRefKey = useMemo(() => {
    return Object.fromEntries(
      Object.entries(generatedQuizByNoteId).flatMap(([noteId, generatedQuiz]) => (
        generatedQuiz.questions.map((question, questionIndex) => {
          const difficulty = typeof (question as { difficulty?: unknown }).difficulty === "string"
            ? (question as { difficulty?: string }).difficulty ?? null
            : null;
          return [createExamQuestionRefKey(noteId, questionIndex), {
            concept: question.concept ?? null,
            difficulty,
          }];
        })
      )),
    );
  }, [generatedQuizByNoteId]);

  const canBalanceSections = selectedTemplate !== null
    && examSections.length > 1
    && countExamSectionQuestions(examSections) > 0;

  const updateExamSections = useCallback((nextSections: ExamBuilderSection[]) => {
    setExamSections(nextSections);
    setTemplateDirty(true);
  }, []);

  const applyTemplate = useCallback((templateId: ExamTemplateId) => {
    const template = getExamTemplateById(templateId);
    if (!template) {
      return;
    }
    setExamSections(buildTemplateSections(template, selectedReadyNoteIds, questionCountsByNoteId));
    setSelectedTemplateId(templateId);
    setTemplateDirty(false);
    setShowTemplateSelector(false);
    setPendingTemplateId(null);
    setPendingDeleteSectionId(null);
    setPendingBalanceMode(null);
    setNextSectionIndex(template.sectionTitles.length);
  }, [questionCountsByNoteId, selectedReadyNoteIds]);

  const handleTemplateSelection = useCallback((templateId: ExamTemplateId) => {
    if (selectedTemplateId && templateDirty) {
      setPendingTemplateId(templateId);
      return;
    }
    applyTemplate(templateId);
  }, [applyTemplate, selectedTemplateId, templateDirty]);

  const handleAddSectionBelow = useCallback((sectionId: string) => {
    setExamSections((previous) => {
      const sectionIndex = previous.findIndex((section) => section.id === sectionId);
      const insertIndex = sectionIndex < 0 ? previous.length : sectionIndex + 1;
      const nextSection = createExamSection(nextSectionIndex);
      const next = [...previous];
      next.splice(insertIndex, 0, nextSection);
      return next;
    });
    setTemplateDirty(true);
    setNextSectionIndex((previous) => previous + 1);
  }, [nextSectionIndex]);

  const handleDeleteSectionRequest = useCallback((sectionId: string) => {
    const section = examSections.find((candidate) => candidate.id === sectionId);
    if (!section) {
      return;
    }
    if (section.entries.length === 0) {
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

  const handleApplyBalance = useCallback((mode: ExamBalanceMode) => {
    if (!canBalanceSections) {
      return;
    }
    const nextSections = mode === SMART_BALANCE_MODE
      ? smartBalanceExamSections(examSections, {
        questionMetadataByRefKey: questionBalanceMetadataByRefKey,
        sectionIntents: selectedTemplate?.sectionIntents,
      })
      : evenBalanceExamSections(examSections, {
        questionMetadataByRefKey: questionBalanceMetadataByRefKey,
      });
    updateExamSections(nextSections);
    setPendingBalanceMode(null);
    setToast(
      mode === SMART_BALANCE_MODE
        ? `Rebalanced ${countExamSectionQuestions(nextSections)} questions across ${nextSections.length} sections. Balanced by section size and topic coverage.`
        : `Rebalanced ${countExamSectionQuestions(nextSections)} questions across ${nextSections.length} sections. Balanced by section size only.`,
    );
  }, [canBalanceSections, examSections, questionBalanceMetadataByRefKey, selectedTemplate?.sectionIntents, updateExamSections]);

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
          questionRefs: section.entries.flatMap((entry) => entry.questionRefs.map((questionRef) => ({
            noteId: questionRef.noteId,
            questionIndex: questionRef.questionIndex,
          }))),
        })),
        includeAnswerKey,
        includeExplanations,
      });
      setToast(EXAM_EXPORT_READY_MESSAGE);
    } catch (exportError) {
      setError(exportError instanceof Error ? exportError.message : "Could not export exam.");
    } finally {
      setExportingExam(false);
    }
  }, [exportableExamSections, exportingExam, includeAnswerKey, includeExplanations]);

  const handleExamBuilderDragOver = useCallback((event: DragOverEvent) => {
    const activeData = event.active.data.current;
    const overData = event.over?.data.current;
    if (activeData?.type !== "entry" || !overData) {
      return;
    }
    const activeEntryId = String(activeData.entryId);
    const targetSectionId = String(overData.sectionId);
    if (!activeEntryId || !targetSectionId) {
      return;
    }
    const targetSection = examSections.find((section) => section.id === targetSectionId);
    if (!targetSection) {
      return;
    }
    const targetIndex = overData.type === "entry"
      ? targetSection.entries.findIndex((entry) => entry.id === String(overData.entryId))
      : targetSection.entries.length;
    if (targetIndex < 0) {
      return;
    }
    updateExamSections(moveEntryToSection(examSections, activeEntryId, targetSectionId, targetIndex));
  }, [examSections, updateExamSections]);

  const handleExamBuilderDragEnd = useCallback((event: DragEndEvent) => {
    const activeData = event.active.data.current;
    const overData = event.over?.data.current;
    setActiveDragEntryId(null);
    setActiveDragSectionId(null);
    if (!activeData) {
      return;
    }
    if (activeData.type === "entry") {
      if (!overData) {
        return;
      }
      const targetSectionId = String(overData.sectionId);
      const targetSection = examSections.find((section) => section.id === targetSectionId);
      if (!targetSection) {
        return;
      }
      const targetIndex = overData.type === "entry"
        ? targetSection.entries.findIndex((entry) => entry.id === String(overData.entryId))
        : targetSection.entries.length;
      if (targetIndex < 0) {
        return;
      }
      updateExamSections(moveEntryToSection(
        examSections,
        String(activeData.entryId),
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

  const examSectionSelectionsById = useMemo(() => {
    return Object.fromEntries(
      exportableExamSections.map((section) => [section.id, section.entries
        .map((entry) => {
          const noteMeta = selectedNoteMetaById[entry.noteId];
          if (!noteMeta) {
            return null;
          }
          return {
            entryId: entry.id,
            noteId: entry.noteId,
            title: noteMeta.title,
            subject: noteMeta.subject,
            assignedQuestionCount: entry.questionRefs.length,
            totalQuestionCount: noteMeta.totalQuestionCount,
          } satisfies ExamBuilderSelection;
        })
        .filter((entry): entry is ExamBuilderSelection => Boolean(entry))]),
    ) as Record<string, ExamBuilderSelection[]>;
  }, [exportableExamSections, selectedNoteMetaById]);

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
      ) : selectedReadyNoteIds.length === 0 ? (
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
          {showTemplateSelector || !selectedTemplate ? (
            <section className="space-y-4">
              <Card className="space-y-4 p-4 sm:p-6">
                <div className="space-y-1">
                  <h2 className="text-xl font-semibold">
                    {selectedTemplate ? "Change template" : "Choose a template"}
                  </h2>
                  <p className="text-sm text-foreground/75">
                    Pick a section preset to structure this exam. You can rename, reorder, add, or remove sections anytime after applying it.
                  </p>
                </div>
                <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                  {EXAM_TEMPLATES.map((template) => {
                    const isSelected = template.id === selectedTemplateId;
                    return (
                      <button
                        key={template.id}
                        type="button"
                        onClick={() => handleTemplateSelection(template.id)}
                        className={`rounded-2xl border p-4 text-left transition-colors ${
                          isSelected && !showTemplateSelector
                            ? "border-blue-600 bg-blue-50/70 dark:border-blue-400 dark:bg-blue-950/30"
                            : "border-border bg-background hover:bg-highlight"
                        }`}
                      >
                        <p className="text-sm font-semibold text-foreground">{template.title}</p>
                        <p className="mt-1 text-sm text-foreground/70">{template.description}</p>
                      </button>
                    );
                  })}
                </div>
              </Card>
            </section>
          ) : null}

          {selectedTemplate ? (
            <Card className="space-y-3 p-4 sm:p-5">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="space-y-1">
                  <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">Template</p>
                  <h2 className="text-base font-semibold text-foreground sm:text-lg">{selectedTemplate.title}</h2>
                  <p className="text-sm text-foreground/70">{selectedTemplate.description}</p>
                </div>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setShowTemplateSelector((previous) => !previous)}
                >
                  {showTemplateSelector ? "Hide Templates" : "Change Template"}
                </Button>
              </div>
            </Card>
          ) : null}

          {selectedTemplate ? (
            <section className="w-full space-y-3 rounded-2xl border border-border bg-muted/20 p-4 shadow-sm sm:p-5">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div className="space-y-1">
                  <h2 className="text-base font-semibold text-foreground sm:text-lg">Selected Notes</h2>
                  <p className="text-sm text-foreground/70">Drag to reorder sections and move notes across sections.</p>
                </div>
                <div className="w-full max-w-md rounded-2xl border border-border bg-background p-3 shadow-sm sm:self-start">
                  <div className="space-y-1">
                    <h3 className="text-sm font-semibold text-foreground">Balance Sections</h3>
                    <p className="text-xs text-foreground/65">Reorganize existing questions without changing their content.</p>
                  </div>
                  <div className="mt-3 grid gap-2 sm:grid-cols-2">
                    <Button
                      type="button"
                      variant="outline"
                      className="gap-2"
                      onClick={() => handleApplyBalance(EVEN_BALANCE_MODE)}
                      disabled={!canBalanceSections || exportingExam}
                    >
                      <Shuffle className="h-4 w-4" aria-hidden="true" />
                      <span>Even Balance</span>
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      className="gap-2"
                      onClick={() => setPendingBalanceMode(SMART_BALANCE_MODE)}
                      disabled={!canBalanceSections || exportingExam}
                    >
                      <Shuffle className="h-4 w-4" aria-hidden="true" />
                      <span>Smart Balance</span>
                    </Button>
                  </div>
                  <div className="mt-3 space-y-1 text-xs text-foreground/65">
                    <p><span className="font-medium text-foreground/80">Even Balance:</span> balances question counts only.</p>
                    <p><span className="font-medium text-foreground/80">Smart Balance:</span> balances counts, topics, and section mix.</p>
                  </div>
                </div>
              </div>

              <DndContext
                sensors={examBuilderSensors}
                collisionDetection={closestCenter}
                modifiers={[restrictToVerticalAxis]}
                onDragStart={(event) => {
                  const data = event.active.data.current;
                  if (data?.type === "entry") {
                    setActiveDragEntryId(String(data.entryId));
                    setActiveDragSectionId(null);
                    return;
                  }
                  if (data?.type === "section") {
                    setActiveDragSectionId(String(data.sectionId));
                    setActiveDragEntryId(null);
                  }
                }}
                onDragCancel={() => {
                  setActiveDragEntryId(null);
                  setActiveDragSectionId(null);
                }}
                onDragOver={handleExamBuilderDragOver}
                onDragEnd={handleExamBuilderDragEnd}
              >
                <SortableContext items={examSections.map((section) => getSectionSortableId(section.id))} strategy={verticalListSortingStrategy}>
                  <div className={`w-full space-y-4 rounded-2xl border border-border/70 bg-background/80 p-3 transition-colors sm:p-4 ${
                    activeDragEntryId || activeDragSectionId ? "border-blue-400 bg-blue-50/40 dark:bg-blue-950/20" : ""
                  }`}>
                    {examSections.map((section, sectionIndex) => (
                      <SortableExamBuilderSection
                        key={section.id}
                        section={section}
                        sectionIndex={sectionIndex}
                        entries={examSectionSelectionsById[section.id] ?? []}
                        exporting={exportingExam}
                        activeDragEntryId={activeDragEntryId}
                        activeDragSectionId={activeDragSectionId}
                        onRename={(sectionId, title) => updateExamSections(renameExamSection(examSections, sectionId, title))}
                        onDelete={handleDeleteSectionRequest}
                        onAddBelow={handleAddSectionBelow}
                        onMoveEntry={(sectionId, entryId, direction) => updateExamSections(
                          moveEntryWithinSection(examSections, sectionId, entryId, direction),
                        )}
                        onRemoveEntry={(entryId) => updateExamSections(removeEntryFromExamSections(examSections, entryId))}
                      />
                    ))}
                  </div>
                </SortableContext>
                <DragOverlay>
                  {activeDragEntryId ? (
                    <div className="flex min-w-[280px] scale-[1.02] items-start justify-between gap-3 rounded-2xl border border-blue-400 bg-background p-3 shadow-xl">
                      <div className="flex min-w-0 flex-1 items-start gap-3">
                        <span className="mt-0.5 inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-border bg-background text-foreground/65">
                          <GripVertical className="h-4 w-4" aria-hidden="true" />
                        </span>
                        <div className="min-w-0 flex-1 space-y-1">
                          <p className="text-xs font-semibold uppercase tracking-wide text-foreground/50">Note</p>
                          <p className="truncate text-sm font-medium text-foreground">
                            {Object.values(examSectionSelectionsById).flat().find((entry) => entry.entryId === activeDragEntryId)?.title}
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
          ) : null}

          {selectedTemplate ? (
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
          ) : null}
        </>
      )}

      <div className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-background/95 backdrop-blur">
        <div className="mx-auto flex w-full max-w-6xl flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <div className="space-y-1">
            <p className="text-sm font-medium text-foreground">
              {selectedTemplate === null
                ? "Choose a template to start building this exam."
                : builderQuestionCount === 0
                  ? "Select notes from your library to start building an exam."
                  : `${builderQuestionCount} question${builderQuestionCount === 1 ? "" : "s"} from ${builderUniqueNoteCount} note${builderUniqueNoteCount === 1 ? "" : "s"} across ${exportableExamSections.length} section${exportableExamSections.length === 1 ? "" : "s"}`}
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
              disabled={builderQuestionCount === 0 || selectedTemplate === null}
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
          pendingDeleteSection?.entries.length
            ? "This section still has note groups. Move them to the previous section or remove them from the exam."
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
            {pendingDeleteSection?.entries.length ? (
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
              variant={pendingDeleteSection?.entries.length ? "outline" : "default"}
              className={pendingDeleteSection?.entries.length ? "text-red-700 hover:text-red-800 dark:text-red-300 dark:hover:text-red-200" : undefined}
              onClick={() => handleConfirmDeleteSection("delete_notes")}
              disabled={exportingExam}
            >
              {pendingDeleteSection?.entries.length ? "Delete Notes" : "Delete Section"}
            </Button>
          </div>
        )}
      >
        <p className="text-sm text-foreground/70">
          {pendingDeleteSection?.entries.length
            ? `${pendingDeleteSection.entries.length} note group${pendingDeleteSection.entries.length === 1 ? "" : "s"} will be affected.`
            : "This section has no notes, so it can be removed immediately."}
        </p>
      </AppModal>

      <AppModal
        isOpen={pendingBalanceMode === SMART_BALANCE_MODE}
        title="Apply Smart Balance?"
        description="Smart Balance will reorganize your current section assignments. Continue?"
        onClose={() => setPendingBalanceMode(null)}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="ghost" onClick={() => setPendingBalanceMode(null)} disabled={exportingExam}>
              Cancel
            </Button>
            <Button type="button" onClick={() => handleApplyBalance(SMART_BALANCE_MODE)} disabled={exportingExam || !canBalanceSections}>
              Apply Smart Balance
            </Button>
          </div>
        )}
      >
        <p className="text-sm text-foreground/70">
          Questions stay deterministic while Smart Balance evens out section size and spreads topic coverage where possible.
        </p>
      </AppModal>

      <AppModal
        isOpen={pendingTemplateId !== null}
        title="Apply new template?"
        description="Applying a new template will replace your current section layout. Continue?"
        onClose={() => setPendingTemplateId(null)}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="ghost" onClick={() => setPendingTemplateId(null)}>
              Cancel
            </Button>
            <Button
              type="button"
              onClick={() => {
                if (pendingTemplateId) {
                  applyTemplate(pendingTemplateId);
                }
              }}
            >
              Apply Template
            </Button>
          </div>
        )}
      >
        <p className="text-sm text-foreground/70">
          Your current section names and note placement will be replaced by the new template preset.
        </p>
      </AppModal>

      {toast ? (
        <div className="fixed right-4 top-4 z-40 rounded-xl border border-border bg-background px-4 py-3 text-sm shadow-lg">
          {toast}
        </div>
      ) : null}
    </main>
  );
}
