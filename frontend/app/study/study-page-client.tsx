"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { createNote, updateNote } from "@/lib/api";
import { getSampleNotePreset } from "@/lib/sample-notes";
import { ConfirmTextCard } from "./confirm-text-card";
import { StudyPackResults } from "./study-pack-results";
import { StudyInputCard } from "./study-input-card";
import { useStudyPack } from "./use-study-pack";

type StudyPageClientProps = {
  forcedDemoMode?: boolean;
};

type NoteEditorSaveState = "saving" | "saved";
type NoteEditorDraft = {
  id: string | null;
  title: string;
  subject: string;
  tags: string;
  content: string;
  updatedAt: string;
};

const NOTE_EDITOR_DRAFT_KEY = "notelib:note-editor-draft:v1";

function readNoteEditorDraft(): NoteEditorDraft | null {
  if (typeof window === "undefined") {
    return null;
  }

  const raw = window.localStorage.getItem(NOTE_EDITOR_DRAFT_KEY);
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as Partial<NoteEditorDraft>;
    if (
      typeof parsed.title !== "string"
      || typeof parsed.subject !== "string"
      || typeof parsed.tags !== "string"
      || typeof parsed.content !== "string"
    ) {
      return null;
    }
    return {
      id: typeof parsed.id === "string" ? parsed.id : null,
      title: parsed.title,
      subject: parsed.subject,
      tags: parsed.tags,
      content: parsed.content,
      updatedAt: typeof parsed.updatedAt === "string" ? parsed.updatedAt : "",
    };
  } catch {
    return null;
  }
}

function normalizeTagList(rawTags: string): string[] {
  const uniqueByKey = new Map<string, string>();
  rawTags
    .split(",")
    .map((tag) => tag.trim())
    .filter((tag) => tag.length > 0)
    .forEach((tag) => {
      const key = tag.toLowerCase();
      if (!uniqueByKey.has(key)) {
        uniqueByKey.set(key, tag);
      }
    });
  return Array.from(uniqueByKey.values());
}

export default function StudyPageClient({ forcedDemoMode = false }: StudyPageClientProps) {
  const searchParams = useSearchParams();
  const demoMode = forcedDemoMode || searchParams.get("demo") === "true";
  const noteEditorMode = searchParams.get("editor") === "note";
  const focusUpload = searchParams.get("focus") === "upload";
  const samplePreset = getSampleNotePreset(searchParams.get("sample"));
  const isSampleNote = Boolean(samplePreset);
  const [initialDraft] = useState<NoteEditorDraft | null>(() => readNoteEditorDraft());
  const [noteTitle, setNoteTitle] = useState(() => samplePreset?.title ?? initialDraft?.title ?? "");
  const [noteSubject, setNoteSubject] = useState(() => samplePreset?.subject ?? initialDraft?.subject ?? "");
  const [noteTags, setNoteTags] = useState(() => samplePreset?.tags.join(", ") ?? initialDraft?.tags ?? "");
  const [noteId, setNoteId] = useState<string | null>(() => samplePreset ? null : initialDraft?.id ?? null);
  const [saveState, setSaveState] = useState<NoteEditorSaveState>("saved");
  const isMountedRef = useRef(true);
  const noteIdRef = useRef<string | null>(noteId);
  const pendingDraftRef = useRef<Omit<NoteEditorDraft, "updatedAt"> | null>(null);
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    noteIdRef.current = noteId;
  }, [noteId]);

  const persistDraftToLocalStorage = useCallback((draft: Omit<NoteEditorDraft, "updatedAt">, persistedNoteId: string | null) => {
    if (typeof window === "undefined") {
      return;
    }
    try {
      window.localStorage.setItem(
        NOTE_EDITOR_DRAFT_KEY,
        JSON.stringify({
          ...draft,
          id: persistedNoteId,
          updatedAt: new Date().toISOString(),
        } satisfies NoteEditorDraft),
      );
    } catch {
      // Ignore local storage failures and keep editing flow alive.
    }
  }, []);

  const persistDraft = useCallback(async (
    draft: Omit<NoteEditorDraft, "updatedAt">,
    keepalive = false,
  ) => {
    persistDraftToLocalStorage(draft, noteIdRef.current);

    const normalizedContent = draft.content.trim();
    if (normalizedContent.length === 0) {
      if (isMountedRef.current) {
        setSaveState("saved");
      }
      return;
    }

    try {
      const request = {
        title: draft.title.trim().length > 0 ? draft.title.trim() : null,
        subject: draft.subject.trim().length > 0 ? draft.subject.trim() : null,
        tags: normalizeTagList(draft.tags),
        content: normalizedContent,
      };
      const savedNote = noteIdRef.current
        ? await updateNote(noteIdRef.current, request, { keepalive })
        : await createNote(request, { keepalive });

      noteIdRef.current = savedNote.id;
      if (isMountedRef.current) {
        setNoteId(savedNote.id);
      }
      persistDraftToLocalStorage(draft, savedNote.id);
    } catch {
      // Keep local draft fallback if remote save fails.
    } finally {
      if (isMountedRef.current) {
        setSaveState("saved");
      }
    }
  }, [persistDraftToLocalStorage]);

  const flushPendingSave = useCallback((keepalive = false) => {
    if (!noteEditorMode || demoMode) {
      return;
    }
    if (saveTimerRef.current) {
      clearTimeout(saveTimerRef.current);
      saveTimerRef.current = null;
    }
    const pendingDraft = pendingDraftRef.current;
    if (!pendingDraft) {
      return;
    }
    pendingDraftRef.current = null;
    void persistDraft(pendingDraft, keepalive);
  }, [demoMode, noteEditorMode, persistDraft]);

  useEffect(() => {
    if (!noteEditorMode || demoMode) {
      return;
    }

    const handleBeforeUnload = () => {
      flushPendingSave(true);
    };
    window.addEventListener("beforeunload", handleBeforeUnload);

    return () => {
      window.removeEventListener("beforeunload", handleBeforeUnload);
      isMountedRef.current = false;
      flushPendingSave(false);
      if (saveTimerRef.current) {
        clearTimeout(saveTimerRef.current);
      }
    };
  }, [demoMode, flushPendingSave, noteEditorMode]);

  const queueDraftSave = useCallback((draft: Omit<NoteEditorDraft, "updatedAt">) => {
    if (!noteEditorMode || demoMode) {
      return;
    }

    if (saveTimerRef.current) {
      clearTimeout(saveTimerRef.current);
      saveTimerRef.current = null;
    }

    setSaveState("saving");
    pendingDraftRef.current = draft;
    saveTimerRef.current = setTimeout(() => {
      const pending = pendingDraftRef.current;
      pendingDraftRef.current = null;
      if (!pending) {
        if (isMountedRef.current) {
          setSaveState("saved");
        }
        return;
      }
      saveTimerRef.current = null;
      void persistDraft(pending, false);
    }, 1200);
  }, [demoMode, noteEditorMode, persistDraft]);

  const {
    notesText,
    setNotesText,
    imageFile,
    setImageFile,
    imageInputKey,
    loading,
    errorMessage,
    studyPackResult,
    needsConfirmation,
    confirmedText,
    setConfirmedText,
    canGenerate,
    generatedLabel,
    detectedTopic,
    ocrFlowState,
    ocrStatusMessage,
    handleGenerateStudyPack,
    handleConfirmText,
    handleClearNotes,
  } = useStudyPack(demoMode, noteEditorMode, samplePreset?.content ?? initialDraft?.content ?? "");

  const handleNoteTitleChange = useCallback((value: string) => {
    setNoteTitle(value);
    queueDraftSave({
      id: noteIdRef.current,
      title: value,
      subject: noteSubject,
      tags: noteTags,
      content: notesText,
    });
  }, [noteSubject, noteTags, notesText, queueDraftSave]);

  const handleNoteSubjectChange = useCallback((value: string) => {
    setNoteSubject(value);
    queueDraftSave({
      id: noteIdRef.current,
      title: noteTitle,
      subject: value,
      tags: noteTags,
      content: notesText,
    });
  }, [noteTags, noteTitle, notesText, queueDraftSave]);

  const handleNoteTagsChange = useCallback((value: string) => {
    setNoteTags(value);
    queueDraftSave({
      id: noteIdRef.current,
      title: noteTitle,
      subject: noteSubject,
      tags: value,
      content: notesText,
    });
  }, [noteSubject, noteTitle, notesText, queueDraftSave]);

  const handleNotesTextChange = useCallback((value: string) => {
    setNotesText(value);
    queueDraftSave({
      id: noteIdRef.current,
      title: noteTitle,
      subject: noteSubject,
      tags: noteTags,
      content: value,
    });
  }, [noteSubject, noteTags, noteTitle, queueDraftSave, setNotesText]);

  const handleClearAll = () => {
    handleClearNotes();
    setNoteTitle("");
    setNoteSubject("");
    setNoteTags("");
    queueDraftSave({
      id: noteIdRef.current,
      title: "",
      subject: "",
      tags: "",
      content: "",
    });
  };

  return (
    <main className="mx-auto w-full max-w-3xl space-y-8 px-4 py-6 sm:px-6 sm:py-10">
      <section className="space-y-2">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold text-foreground sm:text-3xl md:text-4xl">Create Note</h1>
          {demoMode ? (
            <span className="inline-flex items-center rounded-full border border-blue-500/40 bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700 dark:bg-blue-950/30 dark:text-blue-300">
              Demo mode
            </span>
          ) : null}
        </div>
        <p className="text-base leading-relaxed text-foreground/75">
          Write or paste your notes, then generate a Study Pack when you're ready.
        </p>
      </section>

      <StudyInputCard
        noteEditorMode={noteEditorMode}
        isSampleNote={isSampleNote}
        saveState={saveState}
        focusUpload={focusUpload}
        noteTitle={noteTitle}
        onNoteTitleChange={handleNoteTitleChange}
        noteSubject={noteSubject}
        onNoteSubjectChange={handleNoteSubjectChange}
        noteTags={noteTags}
        onNoteTagsChange={handleNoteTagsChange}
        notesText={notesText}
        onNotesTextChange={handleNotesTextChange}
        imageFile={imageFile}
        onImageFileChange={setImageFile}
        imageInputKey={imageInputKey}
        hasStudyPack={Boolean(studyPackResult)}
        currentStudyPackId={studyPackResult?.id ?? null}
        canGenerate={canGenerate}
        loading={loading}
        ocrFlowState={ocrFlowState}
        ocrStatusMessage={ocrStatusMessage}
        onGenerate={() => {
          void handleGenerateStudyPack();
        }}
        onClear={handleClearAll}
      />

      {errorMessage ? (
        <Card className="border-red-500/40 bg-red-50/70 p-4 dark:bg-red-950/20 sm:p-6">
          <CardTitle className="mb-2">
            {ocrFlowState === "failure" ? "Couldn't Process Notes Image" : "Couldn't Generate Study Pack"}
          </CardTitle>
          <CardDescription>{errorMessage}</CardDescription>
        </Card>
      ) : null}

      {needsConfirmation ? (
        <ConfirmTextCard
          loading={loading}
          needsConfirmation={needsConfirmation}
          confirmedText={confirmedText}
          onConfirmedTextChange={setConfirmedText}
          onConfirm={() => {
            void handleConfirmText();
          }}
        />
      ) : null}

      <StudyPackResults
        loading={loading}
        demoMode={demoMode}
        studyPackResult={studyPackResult}
        generatedLabel={generatedLabel}
        detectedTopic={detectedTopic}
      />
    </main>
  );
}

