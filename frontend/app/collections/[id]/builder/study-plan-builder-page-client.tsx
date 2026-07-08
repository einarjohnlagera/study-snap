"use client";

import type { CSSProperties, FormEvent } from "react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  closestCenter,
  DndContext,
  DragOverlay,
  type DragEndEvent,
  type DropAnimation,
  PointerSensor,
  TouchSensor,
  useDroppable,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import { restrictToVerticalAxis } from "@dnd-kit/modifiers";
import { SortableContext, arrayMove, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { ArrowDown, ArrowUp, ChevronDown, ChevronRight, GripVertical, Plus, RefreshCw, Search, Trash2, X } from "lucide-react";
import { AppModal } from "@/components/ui/app-modal";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";
import { getAuthUser, type AuthUser } from "@/lib/auth";
import { getCollectionLabels } from "@/lib/collection-labels";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { sortCollectionItemsByPosition } from "@/lib/collection-exam";
import { cn } from "@/lib/utils";
import {
  addCollectionItems,
  ApiRequestError,
  createCollection,
  deleteCollection,
  getCollection,
  getCollectionGoal,
  listNotes,
  removeCollectionItem,
  reorderCollectionChildren,
  setCollectionItemOrder,
  setCollectionParent,
  updateCollection,
  type GoalCollectionChildResponse,
  type GoalCollectionDetailResponse,
  type NoteCollectionDetail,
  type NoteCollectionItem,
  type NoteListItemResponse,
} from "@/lib/api";

type LoadState = "loading" | "ready" | "error" | "not-found";
type MutationKind = "add-notes" | "add-subject" | "delete-subject" | "move-note" | "rename-section" | "rename-subject" | "reorder-notes" | "reorder-subjects" | "remove-note" | null;
type BuilderSubject = GoalCollectionChildResponse & {
  items: NoteCollectionItem[];
};
type LeafBuilderNote = NoteCollectionItem;
type LeafBuilderSection = {
  id: string;
  name: string;
  items: LeafBuilderNote[];
};
type ActiveDrag =
  | { type: "subject"; subjectId: string; title: string }
  | { type: "note"; subjectId: string; noteId: string; title: string }
  | { type: "leaf-note"; noteId: string; title: string }
  | { type: "leaf-section"; sectionName: string; title: string }
  | null;

const TITLE_MAX_LENGTH = 150;
const UNGROUPED_SECTION_NAME = "Ungrouped";
const DND_TRANSITION_CLASS = "transition-[transform,box-shadow,border-color,background-color,opacity] duration-150 ease-out";
const DND_DROP_ANIMATION: DropAnimation = {
  duration: 160,
  easing: "cubic-bezier(0.22, 1, 0.36, 1)",
};

function getSubjectSortableId(subjectId: string) {
  return `subject:${subjectId}`;
}

function getNoteSortableId(subjectId: string, noteId: string) {
  return `note:${subjectId}:${noteId}`;
}

function getSubjectNoteDropzoneId(subjectId: string) {
  return `subject-notes:${subjectId}`;
}

function getLeafNoteSortableId(noteId: string) {
  return `leaf-note:${noteId}`;
}

function getLeafSectionDropzoneId(sectionName: string) {
  return `leaf-section:${encodeURIComponent(sectionName)}`;
}

function getNoteTitle(item: Pick<NoteCollectionItem, "title" | "noteId">): string {
  return item.title?.trim() || `Untitled note ${item.noteId.slice(0, 8)}`;
}

function getNoteMeta(item: Pick<NoteCollectionItem, "subject" | "courseProgram">): string {
  return [item.subject, item.courseProgram].filter(Boolean).join(" · ") || "No subject yet";
}

function buildOrderPayload(items: NoteCollectionItem[]) {
  return items.map((item) => ({ noteId: item.noteId, label: item.label ?? null }));
}

function getSectionName(label: string | null | undefined): string {
  return label?.trim() || UNGROUPED_SECTION_NAME;
}

function getSectionLabel(sectionName: string): string | null {
  const trimmed = sectionName.trim();
  return trimmed && trimmed !== UNGROUPED_SECTION_NAME ? trimmed : null;
}

function buildLeafSections(items: LeafBuilderNote[]): LeafBuilderSection[] {
  const sections = new Map<string, LeafBuilderNote[]>();
  for (const item of items) {
    const sectionName = getSectionName(item.label);
    sections.set(sectionName, [...(sections.get(sectionName) ?? []), item]);
  }
  return Array.from(sections.entries()).map(([name, sectionItems]) => ({
    id: getLeafSectionDropzoneId(name),
    name,
    items: sectionItems,
  }));
}

function clampPercentage(value: number): number {
  return Math.min(100, Math.max(0, value));
}

function pluralizeLabel(label: string): string {
  return label.endsWith("s") ? label : `${label}s`;
}

function filterPickerNotes(notes: NoteListItemResponse[], presentNoteIds: Set<string>, query: string): NoteListItemResponse[] {
  const normalizedQuery = query.trim().toLowerCase();
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

function toOptimisticItem(note: NoteListItemResponse, position: number): NoteCollectionItem {
  return {
    noteId: note.id,
    label: null,
    position,
    title: note.title,
    subject: note.subject,
    courseProgram: note.courseProgram,
    studyPackStatus: note.studyPackStatus,
    generatedQuizId: note.generatedQuizId ?? null,
    lastSessionCompletedAt: note.lastSessionCompletedAt ?? null,
    dueConceptCount: 0,
    dueConcepts: [],
    updatedAt: note.updatedAt,
  };
}

function makeErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

function SubjectDropzone({ subjectId, hidden }: Readonly<{ subjectId: string; hidden: boolean }>) {
  const { setNodeRef, isOver } = useDroppable({
    id: getSubjectNoteDropzoneId(subjectId),
    data: { type: "subject-dropzone", subjectId },
    disabled: hidden,
  });

  if (hidden) {
    return null;
  }

  return (
    <div
      ref={setNodeRef}
      className={cn(
        "rounded-lg border border-dashed border-border bg-muted/30 px-3 py-3 text-center text-xs font-medium text-foreground/55",
        isOver && "border-blue-400 bg-blue-50 text-blue-700 dark:bg-blue-950/20 dark:text-blue-200",
      )}
    >
      Drag notes here
    </div>
  );
}

function SortableNoteCard({
  item,
  subjectId,
  subjectTitle,
  index,
  totalCount,
  disabled,
  targetSubjects,
  activeDrag,
  onMoveWithinSubject,
  onMoveToSubject,
  onRemove,
}: Readonly<{
  item: NoteCollectionItem;
  subjectId: string;
  subjectTitle: string;
  index: number;
  totalCount: number;
  disabled: boolean;
  targetSubjects: Array<{ id: string; title: string }>;
  activeDrag: ActiveDrag;
  onMoveWithinSubject: (noteId: string, direction: "up" | "down") => void;
  onMoveToSubject: (sourceSubjectId: string, noteId: string, targetSubjectId: string) => void;
  onRemove: (subjectId: string, noteId: string) => void;
}>) {
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
    id: getNoteSortableId(subjectId, item.noteId),
    data: { type: "note", subjectId, noteId: item.noteId },
    disabled,
  });

  const style: CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
  };
  const title = getNoteTitle(item);

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={cn(
        "flex w-full min-w-0 flex-col gap-2 rounded-lg border border-border bg-background p-3 shadow-sm",
        DND_TRANSITION_CLASS,
        isDragging && "scale-[1.01] border-dashed border-blue-400/80 opacity-40 shadow-lg",
        isOver && activeDrag?.type === "note" && "border-blue-300 bg-blue-50/50 dark:bg-blue-950/20",
      )}
    >
      <div className="flex min-w-0 w-full items-start gap-3">
        <button
          ref={setActivatorNodeRef}
          type="button"
          aria-label={`Drag ${title}`}
          className="mt-0.5 inline-flex h-9 w-9 shrink-0 touch-none items-center justify-center rounded-lg border border-border text-foreground/60 hover:bg-highlight disabled:opacity-50"
          disabled={disabled}
          {...attributes}
          {...listeners}
        >
          <GripVertical className="h-4 w-4" aria-hidden="true" />
        </button>
        <div className="flex shrink-0 flex-col gap-1">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="h-6 w-8 p-0"
            aria-label={`Move ${title} up`}
            disabled={disabled || index === 0}
            onClick={() => onMoveWithinSubject(item.noteId, "up")}
          >
            <ArrowUp className="h-3.5 w-3.5" aria-hidden="true" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="h-6 w-8 p-0"
            aria-label={`Move ${title} down`}
            disabled={disabled || index === totalCount - 1}
            onClick={() => onMoveWithinSubject(item.noteId, "down")}
          >
            <ArrowDown className="h-3.5 w-3.5" aria-hidden="true" />
          </Button>
        </div>
        <div className="min-w-0 flex-1 space-y-1">
          <Link
            href={`/notes/${item.noteId}?ref=${encodeURIComponent(`/collections/${subjectId}`)}`}
            className="block rounded-md p-1 -m-1 hover:bg-highlight"
          >
            <p className="line-clamp-2 text-sm font-semibold text-foreground">{title}</p>
            <p className="text-xs text-foreground/60">{getNoteMeta(item)}</p>
          </Link>
          <div className="flex flex-wrap items-center gap-2 text-xs text-foreground/55">
            <span>{subjectTitle}</span>
            <span>Position {index + 1}</span>
          </div>
        </div>
      </div>
      <div className="flex w-full items-center justify-end gap-1">
        {targetSubjects.length > 0 ? (
          <select
            aria-label={`Move ${title} to subject`}
            value=""
            disabled={disabled}
            onChange={(event) => {
              const targetSubjectId = event.target.value;
              if (targetSubjectId) {
                onMoveToSubject(subjectId, item.noteId, targetSubjectId);
              }
            }}
            className="h-9 rounded-lg border border-border bg-background px-2 text-xs text-foreground outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
          >
            <option value="">Move</option>
            {targetSubjects.map((subject) => (
              <option key={subject.id} value={subject.id}>{subject.title}</option>
            ))}
          </select>
        ) : null}
        <Button
          type="button"
          variant="ghost"
          size="sm"
          aria-label={`Remove ${title}`}
          className="h-9 px-2 text-red-700 dark:text-red-300"
          disabled={disabled}
          onClick={() => onRemove(subjectId, item.noteId)}
        >
          <Trash2 className="h-4 w-4 sm:mr-1" aria-hidden="true" />
          <span className="hidden sm:inline">Remove</span>
        </Button>
      </div>
    </div>
  );
}


function LeafSortableNoteCard({
  item,
  sectionName,
  collectionId,
  index,
  totalCount,
  disabled,
  targetSections,
  activeDrag,
  onMoveWithinSection,
  onMoveToSection,
  onRemove,
}: Readonly<{
  item: LeafBuilderNote;
  sectionName: string;
  collectionId: string;
  index: number;
  totalCount: number;
  disabled: boolean;
  targetSections: string[];
  activeDrag: ActiveDrag;
  onMoveWithinSection: (noteId: string, direction: "up" | "down") => void;
  onMoveToSection: (noteId: string, targetSectionName: string) => void;
  onRemove: (noteId: string) => void;
}>) {
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
    id: getLeafNoteSortableId(item.noteId),
    data: { type: "leaf-note", noteId: item.noteId },
    disabled,
  });

  const style: CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
  };
  const title = getNoteTitle(item);

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={cn(
        "flex w-full min-w-0 flex-col gap-2 rounded-lg border border-border bg-background p-3 shadow-sm",
        DND_TRANSITION_CLASS,
        isDragging && "scale-[1.01] border-dashed border-blue-400/80 opacity-40 shadow-lg",
        isOver && activeDrag?.type === "leaf-note" && "border-blue-300 bg-blue-50/50 dark:bg-blue-950/20",
      )}
    >
      <div className="flex min-w-0 w-full items-start gap-3">
        <button
          ref={setActivatorNodeRef}
          type="button"
          aria-label={`Drag ${title}`}
          className="mt-0.5 inline-flex h-9 w-9 shrink-0 touch-none items-center justify-center rounded-lg border border-border text-foreground/60 hover:bg-highlight disabled:opacity-50"
          disabled={disabled}
          {...attributes}
          {...listeners}
        >
          <GripVertical className="h-4 w-4" aria-hidden="true" />
        </button>
        <div className="flex shrink-0 flex-col gap-1">
          <Button type="button" variant="ghost" size="sm" className="h-6 w-8 p-0" aria-label={`Move ${title} up`} disabled={disabled || index === 0} onClick={() => onMoveWithinSection(item.noteId, "up")}>
            <ArrowUp className="h-3.5 w-3.5" aria-hidden="true" />
          </Button>
          <Button type="button" variant="ghost" size="sm" className="h-6 w-8 p-0" aria-label={`Move ${title} down`} disabled={disabled || index === totalCount - 1} onClick={() => onMoveWithinSection(item.noteId, "down")}>
            <ArrowDown className="h-3.5 w-3.5" aria-hidden="true" />
          </Button>
        </div>
        <div className="min-w-0 flex-1 space-y-1">
          <Link
            href={`/notes/${item.noteId}?ref=${encodeURIComponent(`/collections/${collectionId}/builder`)}`}
            className="-m-1 block rounded-md p-1 hover:bg-highlight"
          >
            <p className="line-clamp-2 text-sm font-semibold text-foreground">{title}</p>
            <p className="text-xs text-foreground/60">{getNoteMeta(item)}</p>
          </Link>
          <div className="flex flex-wrap items-center gap-2 text-xs text-foreground/55">
            <span>{sectionName}</span>
            <span>Position {index + 1}</span>
          </div>
        </div>
      </div>
      <div className="flex w-full items-center justify-end gap-1">
        {targetSections.length > 0 ? (
          <select
            aria-label={`Move ${title} to section`}
            value=""
            disabled={disabled}
            onChange={(event) => {
              if (event.target.value) {
                onMoveToSection(item.noteId, event.target.value);
              }
            }}
            className="h-9 rounded-lg border border-border bg-background px-2 text-xs text-foreground outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
          >
            <option value="">Move</option>
            {targetSections.map((section) => (
              <option key={section} value={section}>{section}</option>
            ))}
          </select>
        ) : null}
        <Button type="button" variant="ghost" size="sm" aria-label={`Remove ${title}`} className="h-9 px-2 text-red-700 dark:text-red-300" disabled={disabled} onClick={() => onRemove(item.noteId)}>
          <Trash2 className="h-4 w-4 sm:mr-1" aria-hidden="true" />
          <span className="hidden sm:inline">Remove</span>
        </Button>
      </div>
    </div>
  );
}

function LeafSectionBlock({
  section,
  collectionId,
  disabled,
  allSections,
  activeDrag,
  onRename,
  onMoveWithinSection,
  onMoveToSection,
  onRemove,
}: Readonly<{
  section: LeafBuilderSection;
  collectionId: string;
  disabled: boolean;
  allSections: LeafBuilderSection[];
  activeDrag: ActiveDrag;
  onRename: (oldName: string, newName: string) => void;
  onMoveWithinSection: (sectionName: string, noteId: string, direction: "up" | "down") => void;
  onMoveToSection: (noteId: string, targetSectionName: string) => void;
  onRemove: (noteId: string) => void;
}>) {
  const [nameValue, setNameValue] = useState(section.name);
  const [mobileCollapsed, setMobileCollapsed] = useState(true);
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
    id: section.id,
    data: { type: "leaf-section", sectionName: section.name },
    disabled,
  });

  const style: CSSProperties = { transform: CSS.Transform.toString(transform), transition };
  const targetSections = allSections
    .map((candidate) => candidate.name)
    .filter((name) => name !== section.name);

  const commitRename = () => {
    const trimmedName = nameValue.trim();
    if (trimmedName && trimmedName !== section.name) {
      onRename(section.name, trimmedName);
      return;
    }
    setNameValue(section.name);
  };

  return (
    <section
      ref={setNodeRef}
      style={style}
      className={cn(
        "rounded-xl border border-border bg-surface-alt p-4 shadow-sm",
        DND_TRANSITION_CLASS,
        isDragging && "scale-[1.006] border-dashed border-blue-400/80 opacity-45 shadow-lg",
        isOver && activeDrag?.type === "leaf-note" && "border-blue-300 bg-blue-50/40 dark:bg-blue-950/20",
      )}
    >
      <div className="flex items-start gap-3">
        <button
          ref={setActivatorNodeRef}
          type="button"
          aria-label={`Drag section ${section.name}`}
          className="mt-0.5 inline-flex h-9 w-9 shrink-0 touch-none items-center justify-center rounded-lg border border-border bg-background text-foreground/60 hover:bg-highlight disabled:opacity-50"
          disabled={disabled}
          {...attributes}
          {...listeners}
        >
          <GripVertical className="h-4 w-4" aria-hidden="true" />
        </button>
        <div className="min-w-0 flex-1 space-y-1">
          <input
            aria-label={`Section name ${section.name}`}
            value={nameValue}
            maxLength={TITLE_MAX_LENGTH}
            disabled={disabled}
            onChange={(event) => setNameValue(event.target.value)}
            onBlur={commitRename}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                event.currentTarget.blur();
              }
            }}
            className="min-w-0 w-full rounded-lg border border-transparent bg-transparent px-2 py-1 text-lg font-semibold text-foreground outline-none hover:border-border focus:border-blue-500 focus:bg-background focus:ring-2 focus:ring-blue-500/20"
          />
          <p className="px-2 text-xs text-foreground/60">
            {section.items.length} {section.items.length === 1 ? "note" : "notes"}
          </p>
        </div>
        <button
          type="button"
          aria-label={`${mobileCollapsed ? "Expand" : "Collapse"} section ${section.name}`}
          className="mt-0.5 inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-border bg-background text-foreground/60 hover:bg-highlight sm:hidden"
          onClick={() => setMobileCollapsed((prev) => !prev)}
        >
          {mobileCollapsed ? <ChevronRight className="h-4 w-4" aria-hidden="true" /> : <ChevronDown className="h-4 w-4" aria-hidden="true" />}
        </button>
      </div>

      <div className={cn("mt-4 space-y-3", mobileCollapsed && "hidden sm:block")}>
        {section.items.length > 0 ? (
          <SortableContext items={section.items.map((item) => getLeafNoteSortableId(item.noteId))} strategy={verticalListSortingStrategy}>
            <div className="space-y-3">
              {section.items.map((item, noteIndex) => (
                <LeafSortableNoteCard
                  key={getLeafNoteSortableId(item.noteId)}
                  item={item}
                  sectionName={section.name}
                  collectionId={collectionId}
                  index={noteIndex}
                  totalCount={section.items.length}
                  disabled={disabled}
                  targetSections={targetSections}
                  activeDrag={activeDrag}
                  onMoveWithinSection={(noteId, direction) => onMoveWithinSection(section.name, noteId, direction)}
                  onMoveToSection={onMoveToSection}
                  onRemove={onRemove}
                />
              ))}
            </div>
          </SortableContext>
        ) : (
          <div className={cn(
            "rounded-lg border border-dashed border-border px-4 py-5 text-center",
            isOver && activeDrag?.type === "leaf-note" && "border-blue-400 bg-blue-50 dark:bg-blue-950/20",
          )}>
            <p className="text-sm font-medium text-foreground">No notes in this section yet.</p>
            <p className="mt-1 text-xs text-foreground/60">Drag notes here from another section.</p>
          </div>
        )}
      </div>
    </section>
  );
}

function SortableSubjectBlock({
  subject,
  index,
  totalCount,
  collapsed,
  disabled,
  labels,
  allSubjects,
  activeDrag,
  onToggle,
  onRename,
  onDelete,
  onAddNotes,
  onMoveSubject,
  onMoveNoteWithinSubject,
  onMoveNoteToSubject,
  onRemoveNote,
}: Readonly<{
  subject: BuilderSubject;
  index: number;
  totalCount: number;
  collapsed: boolean;
  disabled: boolean;
  labels: ReturnType<typeof getCollectionLabels>;
  allSubjects: BuilderSubject[];
  activeDrag: ActiveDrag;
  onToggle: (subjectId: string) => void;
  onRename: (subjectId: string, title: string) => void;
  onDelete: (subject: BuilderSubject) => void;
  onAddNotes: (subjectId: string) => void;
  onMoveSubject: (subjectId: string, direction: "up" | "down") => void;
  onMoveNoteWithinSubject: (subjectId: string, noteId: string, direction: "up" | "down") => void;
  onMoveNoteToSubject: (sourceSubjectId: string, noteId: string, targetSubjectId: string) => void;
  onRemoveNote: (subjectId: string, noteId: string) => void;
}>) {
  const [titleValue, setTitleValue] = useState(subject.title);
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
    id: getSubjectSortableId(subject.collectionId),
    data: { type: "subject", subjectId: subject.collectionId },
    disabled,
  });

  const style: CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
  };
  const targetSubjects = allSubjects
    .filter((candidate) => candidate.collectionId !== subject.collectionId)
    .map((candidate) => ({ id: candidate.collectionId, title: candidate.title }));

  const commitRename = () => {
    const trimmedTitle = titleValue.trim();
    if (trimmedTitle && trimmedTitle !== subject.title) {
      onRename(subject.collectionId, trimmedTitle);
      return;
    }
    setTitleValue(subject.title);
  };

  return (
    <section
      ref={setNodeRef}
      style={style}
      className={cn(
        "rounded-xl border border-border bg-surface-alt p-4 shadow-sm",
        DND_TRANSITION_CLASS,
        isDragging && "scale-[1.006] border-dashed border-blue-400/80 opacity-45 shadow-lg",
        isOver && activeDrag?.type === "subject" && "border-blue-300 bg-blue-50/40 dark:bg-blue-950/20",
      )}
    >
      <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
        <div className="flex min-w-0 flex-1 items-start gap-3">
          <button
            ref={setActivatorNodeRef}
            type="button"
            aria-label={`Drag ${subject.title}`}
            className="inline-flex h-10 w-10 shrink-0 touch-none items-center justify-center rounded-lg border border-border bg-background text-foreground/60 hover:bg-highlight disabled:opacity-50"
            disabled={disabled}
            {...attributes}
            {...listeners}
          >
            <GripVertical className="h-4 w-4" aria-hidden="true" />
          </button>
          <div className="flex shrink-0 flex-col gap-1">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="h-6 w-8 p-0"
              aria-label={`Move ${subject.title} up`}
              disabled={disabled || index === 0}
              onClick={() => onMoveSubject(subject.collectionId, "up")}
            >
              <ArrowUp className="h-3.5 w-3.5" aria-hidden="true" />
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="h-6 w-8 p-0"
              aria-label={`Move ${subject.title} down`}
              disabled={disabled || index === totalCount - 1}
              onClick={() => onMoveSubject(subject.collectionId, "down")}
            >
              <ArrowDown className="h-3.5 w-3.5" aria-hidden="true" />
            </Button>
          </div>
          <div className="min-w-0 flex-1 space-y-2">
            <div className="flex min-w-0 items-center gap-2">
              <button
                type="button"
                aria-label={`${collapsed ? "Expand" : "Collapse"} ${subject.title}`}
                className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg hover:bg-highlight"
                onClick={() => onToggle(subject.collectionId)}
              >
                {collapsed ? <ChevronRight className="h-4 w-4" aria-hidden="true" /> : <ChevronDown className="h-4 w-4" aria-hidden="true" />}
              </button>
              <input
                aria-label={`${labels.subjectSingular} title`}
                value={titleValue}
                maxLength={TITLE_MAX_LENGTH}
                disabled={disabled}
                onChange={(event) => setTitleValue(event.target.value)}
                onBlur={commitRename}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    event.currentTarget.blur();
                  }
                }}
                className="min-w-0 flex-1 rounded-lg border border-transparent bg-transparent px-2 py-1 text-lg font-semibold text-foreground outline-none hover:border-border focus:border-blue-500 focus:bg-background focus:ring-2 focus:ring-blue-500/20"
              />
            </div>
            <div className="flex flex-wrap items-center gap-2 text-xs text-foreground/60">
              {subject.items.length === 0 ? (
                <span className="rounded-full border border-amber-300 bg-amber-50 px-2 py-0.5 font-medium text-amber-700 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-300">
                  No notes yet
                </span>
              ) : (
                <>
                  <span>{subject.items.length} {subject.items.length === 1 ? "note" : "notes"}</span>
                  <span>{subject.overallReadinessPercentage}% ready</span>
                  <span>{subject.masteredConcepts}/{subject.totalConcepts} mastered</span>
                </>
              )}
            </div>
            <div
              role="progressbar"
              aria-label={`${subject.title} readiness`}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={subject.overallReadinessPercentage}
              className="h-2 max-w-sm overflow-hidden rounded-full bg-muted"
            >
              <div
                className="h-full rounded-full bg-blue-600 transition-[width] dark:bg-blue-400"
                style={{ width: `${clampPercentage(subject.overallReadinessPercentage)}%` }}
              />
            </div>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <Button type="button" variant="outline" size="sm" disabled={disabled} onClick={() => onAddNotes(subject.collectionId)}>
            <Plus className="mr-1.5 h-4 w-4" aria-hidden="true" />
            Add notes
          </Button>
          <Button type="button" variant="ghost" size="sm" disabled={disabled} onClick={() => onDelete(subject)}>
            <Trash2 className="mr-1.5 h-4 w-4" aria-hidden="true" />
            Delete
          </Button>
        </div>
      </div>

      {!collapsed ? (
        <div className="mt-4 space-y-3">
          <SubjectDropzone subjectId={subject.collectionId} hidden={subject.items.length > 0} />
          {subject.items.length > 0 ? (
            <SortableContext
              items={subject.items.map((item) => getNoteSortableId(subject.collectionId, item.noteId))}
              strategy={verticalListSortingStrategy}
            >
              <div className="space-y-3">
                {subject.items.map((item, noteIndex) => (
                  <SortableNoteCard
                    key={getNoteSortableId(subject.collectionId, item.noteId)}
                    item={item}
                    subjectId={subject.collectionId}
                    subjectTitle={subject.title}
                    index={noteIndex}
                    totalCount={subject.items.length}
                    disabled={disabled}
                    targetSubjects={targetSubjects}
                    activeDrag={activeDrag}
                    onMoveWithinSubject={(noteId, direction) => onMoveNoteWithinSubject(subject.collectionId, noteId, direction)}
                    onMoveToSubject={onMoveNoteToSubject}
                    onRemove={onRemoveNote}
                  />
                ))}
              </div>
            </SortableContext>
          ) : (
            <div className="rounded-lg border border-dashed border-border px-4 py-5 text-center">
              <p className="text-sm font-medium text-foreground">No notes in this {labels.subjectSingular} yet.</p>
              <p className="mt-1 text-xs text-foreground/60">Add notes or drag them from another {labels.subjectSingular}.</p>
            </div>
          )}
        </div>
      ) : null}
    </section>
  );
}

function AddSubjectModal({
  isOpen,
  labels,
  submitting,
  onClose,
  onAdd,
}: Readonly<{
  isOpen: boolean;
  labels: ReturnType<typeof getCollectionLabels>;
  submitting: boolean;
  onClose: () => void;
  onAdd: (title: string, description: string | null) => Promise<void>;
}>) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);

  const handleClose = () => {
    setTitle("");
    setDescription("");
    setError(null);
    onClose();
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmedTitle = title.trim();
    if (!trimmedTitle) {
      setError(`${labels.subjectSingular} title is required.`);
      return;
    }
    setError(null);
    try {
      await onAdd(trimmedTitle, description.trim() || null);
      handleClose();
    } catch (addError) {
      setError(makeErrorMessage(addError, `Could not add this ${labels.subjectSingular}.`));
    }
  };

  return (
    <AppModal
      isOpen={isOpen}
      title={`Add ${labels.subjectSingular}`}
      description={`Create a child ${labels.singular.toLowerCase()} and nest it under this ${labels.goalSingular.toLowerCase()}.`}
      onClose={handleClose}
      actions={(
        <>
          <Button type="button" variant="secondary" onClick={handleClose}>Cancel</Button>
          <Button type="submit" form="add-subject-form" loading={submitting} loadingText="Adding...">Add {labels.subjectSingular}</Button>
        </>
      )}
    >
      <form id="add-subject-form" className="space-y-4" onSubmit={handleSubmit}>
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
            placeholder={`Optional context for this ${labels.subjectSingular.toLowerCase()}`}
          />
        </label>
        {error ? <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-200">{error}</p> : null}
      </form>
    </AppModal>
  );
}

function AddNotesModal({
  isOpen,
  subject,
  notes,
  submitting,
  onClose,
  onAdd,
  onRefresh,
  refreshing,
}: Readonly<{
  isOpen: boolean;
  subject: { collectionId: string; items: NoteCollectionItem[]; title?: string } | null;
  notes: NoteListItemResponse[];
  submitting: boolean;
  onClose: () => void;
  onAdd: (subjectId: string, noteIds: string[]) => Promise<void>;
  onRefresh: () => Promise<void>;
  refreshing: boolean;
}>) {
  const [query, setQuery] = useState("");
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);

  const handleClose = () => {
    setQuery("");
    setSelectedIds(new Set());
    setError(null);
    onClose();
  };

  const presentNoteIds = useMemo(
    () => new Set(subject?.items.map((item) => item.noteId) ?? []),
    [subject?.items],
  );

  const noteById = useMemo(() => new Map(notes.map((n) => [n.id, n])), [notes]);

  const selectedNotes = useMemo(
    () => Array.from(selectedIds).map((id) => noteById.get(id)).filter((n): n is NoteListItemResponse => n !== undefined),
    [selectedIds, noteById],
  );

  const resultNotes = useMemo(
    () => filterPickerNotes(notes, new Set([...presentNoteIds, ...selectedIds]), query),
    [notes, presentNoteIds, selectedIds, query],
  );

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
    if (!subject || selectedIds.size === 0) {
      return;
    }
    setError(null);
    try {
      await onAdd(subject.collectionId, Array.from(selectedIds));
      handleClose();
    } catch (addError) {
      setError(makeErrorMessage(addError, "Could not add notes."));
    }
  };

  const hasResults = resultNotes.length > 0;
  const hasSelection = selectedNotes.length > 0;

  return (
    <AppModal
      isOpen={isOpen}
      title={subject?.title ? `Add notes to ${subject.title}` : "Add notes"}
      description="Choose from your existing notes. Notes already in this subject are hidden."
      onClose={handleClose}
      panelClassName="sm:max-w-2xl"
      actions={(
        <>
          <Button type="button" variant="secondary" onClick={handleClose}>Cancel</Button>
          <Button type="button" loading={submitting} loadingText="Adding..." disabled={selectedIds.size === 0 || !subject} onClick={() => void handleAdd()}>
            {selectedIds.size > 0 ? `Add selected (${selectedIds.size})` : "Add selected"}
          </Button>
        </>
      )}
    >
      <div className="space-y-4">
        <div className="flex items-center gap-2">
          <label className="flex flex-1 items-center gap-2 rounded-lg border border-border bg-background px-3 py-2">
            <Search className="h-4 w-4 text-foreground/50" aria-hidden="true" />
            <span className="sr-only">Search notes</span>
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search notes"
              className="w-full bg-transparent text-sm outline-none"
            />
          </label>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            title="Pull in notes created since this list loaded"
            disabled={refreshing}
            onClick={() => void onRefresh()}
          >
            <RefreshCw className={cn("h-4 w-4", refreshing && "animate-spin")} aria-hidden="true" />
            <span className="sr-only">Refresh notes</span>
          </Button>
        </div>
        {error ? <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-200">{error}</p> : null}
        <div className="space-y-3">
          <div className="space-y-1.5">
            {hasResults ? (
              <div className="max-h-64 space-y-2 overflow-y-auto pr-1">
                {resultNotes.map((note) => (
                  <label key={note.id} className="flex cursor-pointer items-start gap-3 rounded-lg border border-border p-3 hover:bg-highlight">
                    <input
                      type="checkbox"
                      checked={false}
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
            ) : (
              <p className="rounded-lg bg-muted px-3 py-3 text-sm text-foreground/70">
                {query ? "No matching notes." : (hasSelection ? "No more notes to add." : "No notes available.")}
              </p>
            )}
          </div>
          {hasSelection ? (
            <div className="space-y-1.5 border-t border-border pt-3">
              <div className="flex items-center justify-between px-1">
                <span className="text-xs font-semibold uppercase tracking-wide text-foreground/50">
                  Selected ({selectedNotes.length})
                </span>
                <button
                  type="button"
                  className="text-xs text-foreground/50 hover:text-foreground/80"
                  onClick={() => setSelectedIds(new Set())}
                >
                  Clear all
                </button>
              </div>
              <div className="max-h-48 space-y-2 overflow-y-auto pr-1">
                {selectedNotes.map((note) => (
                  <label key={note.id} className="flex cursor-pointer items-start gap-3 rounded-lg border border-blue-300 bg-blue-50/60 p-3 hover:bg-blue-50 dark:border-blue-800/60 dark:bg-blue-950/20 dark:hover:bg-blue-950/30">
                    <input
                      type="checkbox"
                      checked
                      onChange={() => toggleSelected(note.id)}
                      className="mt-1 accent-blue-600"
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
          ) : null}
        </div>
      </div>
    </AppModal>
  );
}

function DeleteSubjectModal({
  subject,
  deleting,
  labels,
  onClose,
  onConfirm,
}: Readonly<{
  subject: BuilderSubject | null;
  deleting: boolean;
  labels: ReturnType<typeof getCollectionLabels>;
  onClose: () => void;
  onConfirm: () => void;
}>) {
  return (
    <AppModal
      isOpen={subject !== null}
      title={`Delete ${labels.subjectSingular}?`}
      description={subject ? `Delete "${subject.title}" from this builder. This removes only the child plan; your notes will not be deleted.` : undefined}
      onClose={onClose}
      actions={(
        <>
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="button" variant="destructive" loading={deleting} loadingText="Deleting..." onClick={onConfirm}>
            Delete
          </Button>
        </>
      )}
    />
  );
}

function buildSubjects(goal: GoalCollectionDetailResponse, childDetails: NoteCollectionDetail[]): BuilderSubject[] {
  const detailsById = new Map(childDetails.map((detail) => [detail.id, detail]));
  return goal.children.map((child) => ({
    ...child,
    items: sortCollectionItemsByPosition(detailsById.get(child.collectionId)?.items ?? []),
  }));
}

export function StudyPlanBuilderPageClient({ collectionId }: Readonly<{ collectionId: string }>) {
  const router = useRouter();
  const [authUser, setAuthUser] = useState<AuthUser | null>(null);
  const labels = useMemo(() => getCollectionLabels(authUser?.profileType), [authUser?.profileType]);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [loadError, setLoadError] = useState<string | null>(null);
  const [refreshingBuilder, setRefreshingBuilder] = useState(false);
  const [collection, setCollection] = useState<NoteCollectionDetail | null>(null);
  const [goal, setGoal] = useState<GoalCollectionDetailResponse | null>(null);
  const [subjects, setSubjects] = useState<BuilderSubject[]>([]);
  const [leafItems, setLeafItems] = useState<LeafBuilderNote[]>([]);
  const [notes, setNotes] = useState<NoteListItemResponse[]>([]);
  const [refreshingNotes, setRefreshingNotes] = useState(false);
  const [collapsedSubjectIds, setCollapsedSubjectIds] = useState<Set<string>>(new Set());
  // Tracks which collectionId the initial collapse-seed has already run for. A plain
  // "has seeded" boolean isn't enough: loadBuilder depends on labels.goalSingular,
  // which changes once for TEACHER profiles when auth resolves ("Goal" -> "Course"),
  // re-firing loadBuilder a second time for the *same* plan. Keying the guard by
  // collectionId makes that second fire a no-op while still correctly reseeding if
  // the user navigates to a different Goal plan without a full remount.
  const seededCollapseForCollectionIdRef = useRef<string | null>(null);
  const [mutationKind, setMutationKind] = useState<MutationKind>(null);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [addSubjectOpen, setAddSubjectOpen] = useState(false);
  const [addNotesSubjectId, setAddNotesSubjectId] = useState<string | null>(null);
  const [leafAddNotesOpen, setLeafAddNotesOpen] = useState(false);
  const [deleteSubject, setDeleteSubject] = useState<BuilderSubject | null>(null);
  const [activeDrag, setActiveDrag] = useState<ActiveDrag>(null);

  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(TouchSensor),
  );

  useEffect(() => {
    setAuthUser(getAuthUser());
  }, []);

  const refreshNotes = useCallback(async () => {
    setRefreshingNotes(true);
    try {
      setNotes(await listNotes());
    } finally {
      setRefreshingNotes(false);
    }
  }, []);

  const refreshBuilder = useCallback(async (options?: { seedCollapsed?: boolean }) => {
    setRefreshingBuilder(true);
    try {
      const [collectionResult, notesResult] = await Promise.all([
        getCollection(collectionId),
        listNotes(),
      ]);
      setCollection(collectionResult);
      setNotes(notesResult);
      if (collectionResult.childCount === 0) {
        setGoal(null);
        setSubjects([]);
        setLeafItems(sortCollectionItemsByPosition(collectionResult.items));
        return;
      }
      const goalResult = await getCollectionGoal(collectionId);
      const childDetails = await Promise.all(goalResult.children.map((child) => getCollection(child.collectionId)));
      const nextSubjects = buildSubjects(goalResult, childDetails);
      setGoal(goalResult);
      setSubjects(nextSubjects);
      setLeafItems([]);
      // Collapse every section on the initial load only (see loadBuilder) so a plan
      // with many Subject Plans opens as a scannable header list. Later refreshes
      // (after add/remove/rename) never reseed, so manual expand/collapse state
      // survives mutations; a newly created Subject Plan is simply absent from the
      // set, so it renders expanded by default. Guarded per collectionId (not a
      // plain boolean) so it still reseeds correctly if the user navigates to a
      // different Goal plan without a full remount.
      if (options?.seedCollapsed && seededCollapseForCollectionIdRef.current !== collectionId) {
        seededCollapseForCollectionIdRef.current = collectionId;
        setCollapsedSubjectIds(new Set(nextSubjects.map((subject) => subject.collectionId)));
      }
    } finally {
      setRefreshingBuilder(false);
    }
  }, [collectionId]);

  const loadBuilder = useCallback(async () => {
    setLoadState("loading");
    setLoadError(null);
    setMutationError(null);
    try {
      await refreshBuilder({ seedCollapsed: true });
      setLoadState("ready");
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 404) {
        setLoadState("not-found");
        return;
      }
      setLoadError(makeErrorMessage(error, `Could not load this ${labels.goalSingular.toLowerCase()}.`));
      setLoadState("error");
    }
  }, [labels.goalSingular, refreshBuilder]);

  useEffect(() => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
    void Promise.resolve().then(loadBuilder);
  }, [loadBuilder, router]);

  const recoverAfterFailure = async (error: unknown, fallback: string, previousSubjects: BuilderSubject[]) => {
    setSubjects(previousSubjects);
    setMutationError(makeErrorMessage(error, fallback));
    try {
      await refreshBuilder();
    } catch {
      // Keep the inline error visible; the page retry can recover if refresh also fails.
    }
  };

  const recoverLeafAfterFailure = async (error: unknown, fallback: string, previousItems: LeafBuilderNote[]) => {
    setLeafItems(previousItems);
    setMutationError(makeErrorMessage(error, fallback));
    try {
      await refreshBuilder();
    } catch {
      // Keep the inline error visible; the page retry can recover if refresh also fails.
    }
  };

  const selectedAddNotesSubject = useMemo(
    () => subjects.find((subject) => subject.collectionId === addNotesSubjectId) ?? null,
    [addNotesSubjectId, subjects],
  );
  const leafSections = useMemo(() => buildLeafSections(leafItems), [leafItems]);
  const mutationInProgress = mutationKind !== null;
  // A collection nested under a Goal can never itself become a parent (backend
  // rejects nesting under a non-top-level collection), so only an undecided
  // top-level collection can still choose to become a Goal.
  const canBecomeGoal = leafItems.length === 0 && collection?.parentCollectionId == null;

  const persistLeafItems = async (nextItems: LeafBuilderNote[], previousItems: LeafBuilderNote[], fallback: string, kind: MutationKind = "reorder-notes") => {
    setMutationKind(kind);
    setMutationError(null);
    setLeafItems(nextItems);
    try {
      await setCollectionItemOrder(collectionId, buildOrderPayload(nextItems));
      await refreshBuilder();
    } catch (error) {
      await recoverLeafAfterFailure(error, fallback, previousItems);
    } finally {
      setMutationKind(null);
    }
  };

  const moveLeafNote = (noteId: string, targetSectionName: string, targetIndex: number) => {
    const previousItems = leafItems;
    const movedItem = leafItems.find((item) => item.noteId === noteId);
    if (!movedItem) {
      return;
    }
    const targetLabel = getSectionLabel(targetSectionName);
    const withoutMoved = leafItems.filter((item) => item.noteId !== noteId);
    const targetSectionItems = withoutMoved.filter((item) => getSectionName(item.label) === targetSectionName);
    const boundedTargetIndex = Math.max(0, Math.min(targetSectionItems.length, targetIndex));
    let seenInTargetSection = 0;
    let insertionIndex = withoutMoved.length;
    for (let index = 0; index < withoutMoved.length; index += 1) {
      if (getSectionName(withoutMoved[index].label) !== targetSectionName) {
        continue;
      }
      if (seenInTargetSection === boundedTargetIndex) {
        insertionIndex = index;
        break;
      }
      seenInTargetSection += 1;
      insertionIndex = index + 1;
    }
    const nextItems = [
      ...withoutMoved.slice(0, insertionIndex),
      { ...movedItem, label: targetLabel },
      ...withoutMoved.slice(insertionIndex),
    ].map((item, index) => ({ ...item, position: index }));
    void persistLeafItems(nextItems, previousItems, "Could not save note order.", "reorder-notes");
  };

  const handleRenameLeafSection = (oldName: string, newName: string) => {
    const previousItems = leafItems;
    const trimmedName = newName.trim();
    if (!trimmedName || trimmedName === oldName) {
      return;
    }
    const nextLabel = getSectionLabel(trimmedName);
    const nextItems = leafItems.map((item, index) => (
      getSectionName(item.label) === oldName ? { ...item, label: nextLabel, position: index } : item
    ));
    void persistLeafItems(nextItems, previousItems, "Could not rename this section.", "rename-section");
  };

  const handleMoveLeafWithinSection = (sectionName: string, noteId: string, direction: "up" | "down") => {
    const section = leafSections.find((candidate) => candidate.name === sectionName);
    const currentIndex = section?.items.findIndex((item) => item.noteId === noteId) ?? -1;
    const targetIndex = direction === "up" ? currentIndex - 1 : currentIndex + 1;
    if (!section || currentIndex < 0 || targetIndex < 0 || targetIndex >= section.items.length) {
      return;
    }
    moveLeafNote(noteId, sectionName, targetIndex);
  };

  const handleMoveLeafToSection = (noteId: string, targetSectionName: string) => {
    const section = leafSections.find((candidate) => candidate.name === targetSectionName);
    moveLeafNote(noteId, targetSectionName, section?.items.length ?? 0);
  };

  const handleRemoveLeafNote = async (noteId: string) => {
    const previousItems = leafItems;
    setMutationKind("remove-note");
    setMutationError(null);
    setLeafItems((current) => current.filter((item) => item.noteId !== noteId));
    try {
      await removeCollectionItem(collectionId, noteId);
      await refreshBuilder();
    } catch (error) {
      await recoverLeafAfterFailure(error, "Could not remove this note.", previousItems);
    } finally {
      setMutationKind(null);
    }
  };

  const handleAddLeafNotes = async (targetId: string, noteIds: string[]) => {
    const previousItems = leafItems;
    const noteById = new Map(notes.map((n) => [n.id, n]));
    setMutationKind("add-notes");
    setMutationError(null);
    const optimisticItems = noteIds
      .map((id, offset) => {
        const n = noteById.get(id);
        return n ? toOptimisticItem(n, leafItems.length + offset) : null;
      })
      .filter((item): item is NoteCollectionItem => item !== null);
    setLeafItems((prev) => [...prev, ...optimisticItems]);
    try {
      await addCollectionItems(targetId, noteIds);
      await refreshBuilder();
    } catch (error) {
      await recoverLeafAfterFailure(error, "Could not add notes.", previousItems);
      throw error;
    } finally {
      setMutationKind(null);
    }
  };

  const handleAddSubject = async (title: string, description: string | null) => {
    const previousSubjects = subjects;
    const temporarySubject: BuilderSubject = {
      collectionId: `temporary:${Date.now()}`,
      title,
      description,
      itemCount: 0,
      overallReadinessPercentage: 0,
      masteredConcepts: 0,
      dueConcepts: 0,
      notPracticedConcepts: 0,
      totalConcepts: 0,
      todaysConceptBudget: null,
      items: [],
    };
    setMutationKind("add-subject");
    setMutationError(null);
    setSubjects([...subjects, temporarySubject]);
    try {
      const created = await createCollection({ title, description });
      await setCollectionParent(created.id, collectionId);
      await refreshBuilder();
    } catch (error) {
      await recoverAfterFailure(error, `Could not add this ${labels.subjectSingular}.`, previousSubjects);
      throw error;
    } finally {
      setMutationKind(null);
    }
  };

  const handleRenameSubject = async (subjectId: string, title: string) => {
    const previousSubjects = subjects;
    setMutationKind("rename-subject");
    setMutationError(null);
    setSubjects((current) => current.map((subject) => (
      subject.collectionId === subjectId ? { ...subject, title } : subject
    )));
    try {
      await updateCollection(subjectId, { title });
      await refreshBuilder();
    } catch (error) {
      await recoverAfterFailure(error, `Could not rename this ${labels.subjectSingular}.`, previousSubjects);
    } finally {
      setMutationKind(null);
    }
  };

  const persistSubjectOrder = async (nextSubjects: BuilderSubject[], previousSubjects: BuilderSubject[]) => {
    setMutationKind("reorder-subjects");
    setMutationError(null);
    setSubjects(nextSubjects);
    try {
      await reorderCollectionChildren(collectionId, nextSubjects.map((subject) => subject.collectionId));
      await refreshBuilder();
    } catch (error) {
      await recoverAfterFailure(error, "Could not save subject order.", previousSubjects);
    } finally {
      setMutationKind(null);
    }
  };

  const handleMoveSubject = (subjectId: string, direction: "up" | "down") => {
    const currentIndex = subjects.findIndex((subject) => subject.collectionId === subjectId);
    const targetIndex = direction === "up" ? currentIndex - 1 : currentIndex + 1;
    if (currentIndex < 0 || targetIndex < 0 || targetIndex >= subjects.length) {
      return;
    }
    void persistSubjectOrder(arrayMove(subjects, currentIndex, targetIndex), subjects);
  };

  const handleDeleteSubject = async () => {
    if (!deleteSubject) {
      return;
    }
    const previousSubjects = subjects;
    setMutationKind("delete-subject");
    setMutationError(null);
    setSubjects((current) => current.filter((subject) => subject.collectionId !== deleteSubject.collectionId));
    try {
      await deleteCollection(deleteSubject.collectionId);
      setDeleteSubject(null);
      await refreshBuilder();
    } catch (error) {
      await recoverAfterFailure(error, `Could not delete this ${labels.subjectSingular}.`, previousSubjects);
    } finally {
      setMutationKind(null);
    }
  };

  const handleAddNotes = async (subjectId: string, noteIds: string[]) => {
    const previousSubjects = subjects;
    const noteById = new Map(notes.map((note) => [note.id, note]));
    setMutationKind("add-notes");
    setMutationError(null);
    setSubjects((current) => current.map((subject) => {
      if (subject.collectionId !== subjectId) {
        return subject;
      }
      const optimisticItems = noteIds
        .map((noteId, offset) => {
          const note = noteById.get(noteId);
          return note ? toOptimisticItem(note, subject.items.length + offset) : null;
        })
        .filter((item): item is NoteCollectionItem => item !== null);
      return { ...subject, items: [...subject.items, ...optimisticItems] };
    }));
    try {
      await addCollectionItems(subjectId, noteIds);
      await refreshBuilder();
    } catch (error) {
      await recoverAfterFailure(error, "Could not add notes.", previousSubjects);
      throw error;
    } finally {
      setMutationKind(null);
    }
  };

  const persistNoteMove = async (sourceSubjectId: string, noteId: string, targetSubjectId: string, targetIndex: number) => {
    const previousSubjects = subjects;
    const sourceSubject = subjects.find((subject) => subject.collectionId === sourceSubjectId);
    const targetSubject = subjects.find((subject) => subject.collectionId === targetSubjectId);
    const movedItem = sourceSubject?.items.find((item) => item.noteId === noteId);
    if (!sourceSubject || !targetSubject || !movedItem) {
      return;
    }

    let targetItemsAfterMove: NoteCollectionItem[] = [];
    const nextSubjects = subjects.map((subject) => {
      if (sourceSubjectId === targetSubjectId && subject.collectionId === sourceSubjectId) {
        const currentIndex = subject.items.findIndex((item) => item.noteId === noteId);
        const boundedTargetIndex = Math.min(subject.items.length - 1, Math.max(0, targetIndex));
        targetItemsAfterMove = arrayMove(subject.items, currentIndex, boundedTargetIndex)
          .map((item, index) => ({ ...item, position: index }));
        return { ...subject, items: targetItemsAfterMove };
      }
      if (subject.collectionId === sourceSubjectId) {
        return {
          ...subject,
          items: subject.items.filter((item) => item.noteId !== noteId).map((item, index) => ({ ...item, position: index })),
        };
      }
      if (subject.collectionId === targetSubjectId) {
        const insertionIndex = Math.min(subject.items.length, Math.max(0, targetIndex));
        targetItemsAfterMove = [
          ...subject.items.slice(0, insertionIndex),
          { ...movedItem, position: insertionIndex },
          ...subject.items.slice(insertionIndex),
        ].map((item, index) => ({ ...item, position: index }));
        return { ...subject, items: targetItemsAfterMove };
      }
      return subject;
    });

    setMutationKind(sourceSubjectId === targetSubjectId ? "reorder-notes" : "move-note");
    setMutationError(null);
    setSubjects(nextSubjects);
    try {
      if (sourceSubjectId === targetSubjectId) {
        await setCollectionItemOrder(sourceSubjectId, buildOrderPayload(targetItemsAfterMove));
      } else {
        await removeCollectionItem(sourceSubjectId, noteId);
        await addCollectionItems(targetSubjectId, [noteId]);
        await setCollectionItemOrder(targetSubjectId, buildOrderPayload(targetItemsAfterMove));
      }
      await refreshBuilder();
    } catch (error) {
      await recoverAfterFailure(error, sourceSubjectId === targetSubjectId ? "Could not save note order." : "Could not move this note.", previousSubjects);
    } finally {
      setMutationKind(null);
    }
  };

  const handleMoveNoteWithinSubject = (subjectId: string, noteId: string, direction: "up" | "down") => {
    const subject = subjects.find((candidate) => candidate.collectionId === subjectId);
    const currentIndex = subject?.items.findIndex((item) => item.noteId === noteId) ?? -1;
    const targetIndex = direction === "up" ? currentIndex - 1 : currentIndex + 1;
    if (!subject || currentIndex < 0 || targetIndex < 0 || targetIndex >= subject.items.length) {
      return;
    }
    void persistNoteMove(subjectId, noteId, subjectId, targetIndex);
  };

  const handleRemoveNote = async (subjectId: string, noteId: string) => {
    const previousSubjects = subjects;
    setMutationKind("remove-note");
    setMutationError(null);
    setSubjects((current) => current.map((subject) => (
      subject.collectionId === subjectId
        ? { ...subject, items: subject.items.filter((item) => item.noteId !== noteId) }
        : subject
    )));
    try {
      await removeCollectionItem(subjectId, noteId);
      await refreshBuilder();
    } catch (error) {
      await recoverAfterFailure(error, "Could not remove this note.", previousSubjects);
    } finally {
      setMutationKind(null);
    }
  };

  const handleDragStart = (event: { active: { data: { current?: Record<string, unknown> } } }) => {
    const data = event.active.data.current;
    if (data?.type === "subject" && typeof data.subjectId === "string") {
      const subject = subjects.find((candidate) => candidate.collectionId === data.subjectId);
      setActiveDrag(subject ? { type: "subject", subjectId: subject.collectionId, title: subject.title } : null);
      return;
    }
    if (data?.type === "note" && typeof data.subjectId === "string" && typeof data.noteId === "string") {
      const subject = subjects.find((candidate) => candidate.collectionId === data.subjectId);
      const item = subject?.items.find((candidate) => candidate.noteId === data.noteId);
      setActiveDrag(item && subject ? { type: "note", subjectId: subject.collectionId, noteId: item.noteId, title: getNoteTitle(item) } : null);
      return;
    }
    if (data?.type === "leaf-note" && typeof data.noteId === "string") {
      const item = leafItems.find((candidate) => candidate.noteId === data.noteId);
      setActiveDrag(item ? { type: "leaf-note", noteId: item.noteId, title: getNoteTitle(item) } : null);
      return;
    }
    if (data?.type === "leaf-section" && typeof data.sectionName === "string") {
      const section = leafSections.find((candidate) => candidate.name === data.sectionName);
      setActiveDrag(section ? { type: "leaf-section", sectionName: section.name, title: section.name } : null);
    }
  };

  const handleLeafDragEnd = (event: DragEndEvent) => {
    const activeData = event.active.data.current;
    const overData = event.over?.data.current;
    setActiveDrag(null);
    if (!activeData || !overData) {
      return;
    }

    // Section reorder
    if (activeData.type === "leaf-section" && typeof activeData.sectionName === "string") {
      const overSectionName = overData.type === "leaf-section" && typeof overData.sectionName === "string"
        ? overData.sectionName
        : null;
      if (!overSectionName || overSectionName === activeData.sectionName) {
        return;
      }
      const currentOrder = leafSections.map((s) => s.name);
      const fromIdx = currentOrder.indexOf(activeData.sectionName);
      const toIdx = currentOrder.indexOf(overSectionName);
      if (fromIdx < 0 || toIdx < 0) {
        return;
      }
      const newOrder = arrayMove(currentOrder, fromIdx, toIdx);
      const nextItems = newOrder
        .flatMap((sName) => leafSections.find((s) => s.name === sName)?.items ?? [])
        .map((item, index) => ({ ...item, position: index }));
      void persistLeafItems(nextItems, leafItems, "Could not save section order.", "reorder-notes");
      return;
    }

    // Note reorder / move between sections
    if (activeData.type !== "leaf-note" || typeof activeData.noteId !== "string") {
      return;
    }

    let targetSectionName: string | null = null;
    let targetIndex = -1;
    if (overData.type === "leaf-note" && typeof overData.noteId === "string") {
      const targetItem = leafItems.find((item) => item.noteId === overData.noteId);
      targetSectionName = targetItem ? getSectionName(targetItem.label) : null;
      const targetSection = targetSectionName ? leafSections.find((section) => section.name === targetSectionName) : null;
      targetIndex = targetSection?.items.findIndex((item) => item.noteId === overData.noteId) ?? -1;
    } else if (overData.type === "leaf-section" && typeof overData.sectionName === "string") {
      targetSectionName = overData.sectionName;
      const targetSection = leafSections.find((section) => section.name === targetSectionName);
      targetIndex = targetSection?.items.length ?? 0;
    }

    if (!targetSectionName || targetIndex < 0) {
      return;
    }
    const sourceItem = leafItems.find((item) => item.noteId === activeData.noteId);
    const sourceSectionName = sourceItem ? getSectionName(sourceItem.label) : null;
    const sourceSection = sourceSectionName ? leafSections.find((section) => section.name === sourceSectionName) : null;
    const currentIndex = sourceSection?.items.findIndex((item) => item.noteId === activeData.noteId) ?? -1;
    if (sourceSectionName === targetSectionName && currentIndex === targetIndex) {
      return;
    }
    moveLeafNote(activeData.noteId, targetSectionName, targetIndex);
  };

  const handleDragEnd = (event: DragEndEvent) => {
    const activeData = event.active.data.current;
    const overData = event.over?.data.current;
    setActiveDrag(null);
    if (!overData) {
      return;
    }

    if (activeData?.type === "subject" && typeof activeData.subjectId === "string") {
      const overSubjectId = typeof overData.subjectId === "string" ? overData.subjectId : null;
      if (!overSubjectId || activeData.subjectId === overSubjectId) {
        return;
      }
      const activeIndex = subjects.findIndex((subject) => subject.collectionId === activeData.subjectId);
      const overIndex = subjects.findIndex((subject) => subject.collectionId === overSubjectId);
      if (activeIndex >= 0 && overIndex >= 0) {
        void persistSubjectOrder(arrayMove(subjects, activeIndex, overIndex), subjects);
      }
      return;
    }

    if (activeData?.type !== "note" || typeof activeData.subjectId !== "string" || typeof activeData.noteId !== "string") {
      return;
    }

    const sourceSubjectId = activeData.subjectId;
    let targetSubjectId: string | null = null;
    let targetIndex = -1;
    if (overData.type === "note" && typeof overData.subjectId === "string" && typeof overData.noteId === "string") {
      targetSubjectId = overData.subjectId;
      const targetSubject = subjects.find((subject) => subject.collectionId === targetSubjectId);
      targetIndex = targetSubject?.items.findIndex((item) => item.noteId === overData.noteId) ?? -1;
    } else if ((overData.type === "subject-dropzone" || overData.type === "subject") && typeof overData.subjectId === "string") {
      targetSubjectId = overData.subjectId;
      const targetSubject = subjects.find((subject) => subject.collectionId === targetSubjectId);
      targetIndex = targetSubject?.items.length ?? -1;
    }
    if (!targetSubjectId || targetIndex < 0) {
      return;
    }
    const sourceSubject = subjects.find((subject) => subject.collectionId === sourceSubjectId);
    const currentIndex = sourceSubject?.items.findIndex((item) => item.noteId === activeData.noteId) ?? -1;
    if (sourceSubjectId === targetSubjectId && currentIndex === targetIndex) {
      return;
    }
    void persistNoteMove(sourceSubjectId, activeData.noteId, targetSubjectId, targetIndex);
  };

  if (loadState === "loading" || (refreshingBuilder && (collection?.childCount ?? 0) > 0 && !goal)) {
    return (
      <main className="mx-auto flex w-full max-w-7xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
        <BackLink href={`/collections/${collectionId}`} label="Back to plan" />
        <PageHeader eyebrow={labels.goalSingular.toUpperCase()} title="Loading builder..." description="Loading this study plan canvas." />
        <Card className="space-y-4 p-6">
          <div className="h-5 w-1/3 animate-pulse rounded bg-muted" />
          <div className="h-40 animate-pulse rounded bg-muted" />
        </Card>
      </main>
    );
  }

  if (loadState === "not-found") {
    return (
      <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
        <BackLink href="/collections" label={labels.plural} />
        <Card className="space-y-4 p-6">
          <CardTitle>{labels.goalSingular} not found</CardTitle>
          <CardDescription>This saved set may have been deleted or may not belong to your account.</CardDescription>
          <Link className="inline-flex text-sm font-medium text-blue-600 hover:underline dark:text-blue-400" href="/collections">
            {labels.plural}
          </Link>
        </Card>
      </main>
    );
  }

  if (loadState === "error" || !collection || (collection.childCount > 0 && !goal)) {
    return (
      <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
        <BackLink href="/collections" label={labels.plural} />
        <Card className="space-y-4 p-6">
          <CardTitle>Could not load builder</CardTitle>
          <CardDescription>{loadError ?? "Please try again."}</CardDescription>
          <Button type="button" variant="outline" onClick={() => void loadBuilder()}>Retry</Button>
        </Card>
      </main>
    );
  }

  if (collection.childCount === 0) {
    return (
      <main className="mx-auto flex w-full max-w-7xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
        <BackLink href={`/collections/${collectionId}`} label="Back to plan" />
        <PageHeader
          eyebrow={`${labels.singular.toUpperCase()} BUILDER`}
          title={collection.title}
          description={collection.description ?? "Organize notes and sections on one canvas."}
          actions={canBecomeGoal ? (
            <Button type="button" onClick={() => setAddSubjectOpen(true)} disabled={mutationInProgress}>
              <Plus className="mr-1.5 h-4 w-4" aria-hidden="true" />
              Add {labels.subjectSingular}
            </Button>
          ) : undefined}
        />

        {mutationError ? (
          <Card className="flex flex-col gap-3 border-red-200 bg-red-50 p-4 text-red-800 dark:border-red-900 dark:bg-red-950/30 dark:text-red-200 sm:flex-row sm:items-start sm:justify-between">
            <p className="text-sm">{mutationError}</p>
            <div className="flex shrink-0 items-center gap-2">
              <Button type="button" variant="outline" size="sm" onClick={() => void refreshBuilder()}>
                Refresh
              </Button>
              <button type="button" aria-label="Dismiss error" onClick={() => setMutationError(null)}>
                <X className="h-4 w-4" aria-hidden="true" />
              </button>
            </div>
          </Card>
        ) : null}

        <Card className="space-y-4 p-4 sm:p-6">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <CardTitle>Your notes</CardTitle>
              <CardDescription>
                {leafItems.length} {leafItems.length === 1 ? "note" : "notes"} grouped by section.
              </CardDescription>
            </div>
            <div className="flex items-center gap-3">
              <p className="text-xs text-foreground/55">Drag notes or sections to reorganize.</p>
              <Button type="button" onClick={() => setLeafAddNotesOpen(true)} disabled={mutationInProgress}>
                <Plus className="mr-1.5 h-4 w-4" aria-hidden="true" />
                Add notes
              </Button>
            </div>
          </div>

          {leafItems.length === 0 ? (
            <div className="rounded-xl border border-dashed border-border p-8 text-center">
              <CardTitle>No notes yet</CardTitle>
              <CardDescription className="mt-2">
                {canBecomeGoal
                  ? `Add notes for a single-plan canvas, or add a ${labels.subjectSingular} to build a Goal.`
                  : "Add your existing notes to get started."}
              </CardDescription>
              <div className="mt-4 flex flex-col items-center justify-center gap-2 sm:flex-row">
                <Button type="button" onClick={() => setLeafAddNotesOpen(true)} disabled={mutationInProgress}>
                  <Plus className="mr-1.5 h-4 w-4" aria-hidden="true" />
                  Add notes
                </Button>
                {canBecomeGoal ? (
                  <Button type="button" variant="outline" onClick={() => setAddSubjectOpen(true)} disabled={mutationInProgress}>
                    <Plus className="mr-1.5 h-4 w-4" aria-hidden="true" />
                    Add {labels.subjectSingular}
                  </Button>
                ) : null}
              </div>
            </div>
          ) : (
            <DndContext
              sensors={sensors}
              collisionDetection={closestCenter}
              modifiers={[restrictToVerticalAxis]}
              onDragStart={handleDragStart}
              onDragCancel={() => setActiveDrag(null)}
              onDragEnd={handleLeafDragEnd}
            >
              <SortableContext items={leafSections.map((s) => s.id)} strategy={verticalListSortingStrategy}>
                <div className="space-y-4">
                  {leafSections.map((section) => (
                    <LeafSectionBlock
                      key={`${section.id}:${section.name}`}
                      section={section}
                      collectionId={collectionId}
                      disabled={mutationInProgress}
                      allSections={leafSections}
                      activeDrag={activeDrag}
                      onRename={handleRenameLeafSection}
                      onMoveWithinSection={handleMoveLeafWithinSection}
                      onMoveToSection={handleMoveLeafToSection}
                      onRemove={(noteId) => void handleRemoveLeafNote(noteId)}
                    />
                  ))}
                </div>
              </SortableContext>
              <DragOverlay dropAnimation={DND_DROP_ANIMATION}>
                {activeDrag ? (
                  activeDrag.type === "leaf-section" ? (() => {
                    const draggedSection = leafSections.find((s) => s.name === activeDrag.sectionName);
                    if (!draggedSection) {
                      return (
                        <div className="rounded-lg border border-blue-300 bg-background px-4 py-3 text-sm font-semibold text-foreground shadow-lg">
                          {activeDrag.title}
                        </div>
                      );
                    }
                    return (
                      <div className="rounded-xl border border-blue-300 bg-surface-alt p-4 shadow-xl opacity-95">
                        <div className="flex items-center gap-3">
                          <div className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-border bg-background text-foreground/60">
                            <GripVertical className="h-4 w-4" aria-hidden="true" />
                          </div>
                          <div className="min-w-0 flex-1">
                            <p className="text-base font-semibold text-foreground">{draggedSection.name}</p>
                            <p className="text-xs text-foreground/60">
                              {draggedSection.items.length} {draggedSection.items.length === 1 ? "note" : "notes"}
                            </p>
                          </div>
                        </div>
                        <div className="mt-3 space-y-2">
                          {draggedSection.items.map((item) => (
                            <div key={item.noteId} className="rounded-lg border border-border bg-background px-3 py-2">
                              <p className="line-clamp-1 text-sm font-medium text-foreground">{getNoteTitle(item)}</p>
                              <p className="text-xs text-foreground/60">{getNoteMeta(item)}</p>
                            </div>
                          ))}
                        </div>
                      </div>
                    );
                  })()
                  : (
                    <div className="rounded-lg border border-blue-300 bg-background px-4 py-3 text-sm font-semibold text-foreground shadow-lg">
                      {activeDrag.title}
                    </div>
                  )
                ) : null}
              </DragOverlay>
            </DndContext>
          )}
        </Card>

        <AddNotesModal
          isOpen={leafAddNotesOpen}
          subject={leafAddNotesOpen ? { collectionId, items: leafItems } : null}
          notes={notes}
          submitting={mutationKind === "add-notes"}
          onClose={() => setLeafAddNotesOpen(false)}
          onAdd={handleAddLeafNotes}
          onRefresh={refreshNotes}
          refreshing={refreshingNotes}
        />
        <AddSubjectModal
          isOpen={addSubjectOpen}
          labels={labels}
          submitting={mutationKind === "add-subject"}
          onClose={() => setAddSubjectOpen(false)}
          onAdd={handleAddSubject}
        />
      </main>
    );
  }

  return (
    <main className="mx-auto flex w-full max-w-7xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
      <BackLink href={`/collections/${collectionId}`} label="Back to plan" />
      <PageHeader
        eyebrow={`${labels.goalSingular.toUpperCase()} BUILDER`}
        title={goal!.title}
        description={goal!.description || `Organize ${labels.subjectSingular}s and notes on one canvas.`}
        actions={(
          <Button type="button" onClick={() => setAddSubjectOpen(true)} disabled={mutationInProgress}>
            <Plus className="mr-1.5 h-4 w-4" aria-hidden="true" />
            Add {labels.subjectSingular}
          </Button>
        )}
      />

      {mutationError ? (
        <Card className="flex items-start justify-between gap-4 border-red-200 bg-red-50 p-4 text-red-800 dark:border-red-900 dark:bg-red-950/30 dark:text-red-200">
          <p className="text-sm">{mutationError}</p>
          <button type="button" aria-label="Dismiss error" onClick={() => setMutationError(null)}>
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </Card>
      ) : null}

      <Card className="space-y-4 p-4 sm:p-6">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <CardTitle>{labels.subjectSingular} canvas</CardTitle>
            <CardDescription>
              {subjects.length} {subjects.length === 1 ? labels.subjectSingular : pluralizeLabel(labels.subjectSingular)} in this {labels.goalSingular.toLowerCase()}.
            </CardDescription>
          </div>
          <p className="text-xs text-foreground/55">Drag {pluralizeLabel(labels.subjectSingular)} to reorder; drag notes within or across them.</p>
        </div>

        {subjects.length > 0 ? (
          <div className="flex items-center justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setCollapsedSubjectIds(new Set())}
            >
              Expand all
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setCollapsedSubjectIds(new Set(subjects.map((subject) => subject.collectionId)))}
            >
              Collapse all
            </Button>
          </div>
        ) : null}

        {subjects.length === 0 ? (
          <div className="rounded-xl border border-dashed border-border p-8 text-center">
            <CardTitle>No {pluralizeLabel(labels.subjectSingular)} yet</CardTitle>
            <CardDescription className="mt-2">Start this {labels.goalSingular.toLowerCase()} by adding the first child plan.</CardDescription>
            <Button type="button" className="mt-4" onClick={() => setAddSubjectOpen(true)}>
              <Plus className="mr-1.5 h-4 w-4" aria-hidden="true" />
              Add {labels.subjectSingular}
            </Button>
          </div>
        ) : (
          <DndContext
            sensors={sensors}
            collisionDetection={closestCenter}
            modifiers={[restrictToVerticalAxis]}
            onDragStart={handleDragStart}
            onDragCancel={() => setActiveDrag(null)}
            onDragEnd={handleDragEnd}
          >
            <SortableContext items={subjects.map((subject) => getSubjectSortableId(subject.collectionId))} strategy={verticalListSortingStrategy}>
              <div className="space-y-4">
                {subjects.map((subject, index) => (
                  <SortableSubjectBlock
                    key={`${subject.collectionId}:${subject.title}`}
                    subject={subject}
                    index={index}
                    totalCount={subjects.length}
                    collapsed={collapsedSubjectIds.has(subject.collectionId)}
                    disabled={mutationInProgress}
                    labels={labels}
                    allSubjects={subjects}
                    activeDrag={activeDrag}
                    onToggle={(subjectId) => setCollapsedSubjectIds((previous) => {
                      const next = new Set(previous);
                      if (next.has(subjectId)) {
                        next.delete(subjectId);
                      } else {
                        next.add(subjectId);
                      }
                      return next;
                    })}
                    onRename={(subjectId, title) => void handleRenameSubject(subjectId, title)}
                    onDelete={setDeleteSubject}
                    onAddNotes={setAddNotesSubjectId}
                    onMoveSubject={handleMoveSubject}
                    onMoveNoteWithinSubject={(subjectId, noteId, direction) => handleMoveNoteWithinSubject(subjectId, noteId, direction)}
                    onMoveNoteToSubject={(sourceSubjectId, noteId, targetSubjectId) => void persistNoteMove(sourceSubjectId, noteId, targetSubjectId, subjects.find((subject) => subject.collectionId === targetSubjectId)?.items.length ?? 0)}
                    onRemoveNote={(subjectId, noteId) => void handleRemoveNote(subjectId, noteId)}
                  />
                ))}
              </div>
            </SortableContext>
            <DragOverlay dropAnimation={DND_DROP_ANIMATION}>
              {activeDrag ? (
                <div className="rounded-lg border border-blue-300 bg-background px-4 py-3 text-sm font-semibold text-foreground shadow-lg">
                  {activeDrag.title}
                </div>
              ) : null}
            </DragOverlay>
          </DndContext>
        )}
      </Card>

      <AddSubjectModal
        isOpen={addSubjectOpen}
        labels={labels}
        submitting={mutationKind === "add-subject"}
        onClose={() => setAddSubjectOpen(false)}
        onAdd={handleAddSubject}
      />
      <AddNotesModal
        isOpen={addNotesSubjectId !== null}
        subject={selectedAddNotesSubject}
        notes={notes}
        submitting={mutationKind === "add-notes"}
        onClose={() => setAddNotesSubjectId(null)}
        onAdd={handleAddNotes}
        onRefresh={refreshNotes}
        refreshing={refreshingNotes}
      />
      <DeleteSubjectModal
        subject={deleteSubject}
        labels={labels}
        deleting={mutationKind === "delete-subject"}
        onClose={() => setDeleteSubject(null)}
        onConfirm={() => void handleDeleteSubject()}
      />
    </main>
  );
}
