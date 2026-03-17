"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
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
  const [saveState, setSaveState] = useState<NoteEditorSaveState>("saved");
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return () => {
      if (saveTimerRef.current) {
        clearTimeout(saveTimerRef.current);
      }
    };
  }, []);

  const queueDraftSave = useCallback((draft: Omit<NoteEditorDraft, "updatedAt">) => {
    if (!noteEditorMode || demoMode) {
      return;
    }

    if (saveTimerRef.current) {
      clearTimeout(saveTimerRef.current);
      saveTimerRef.current = null;
    }

    setSaveState("saving");
    saveTimerRef.current = setTimeout(() => {
      if (typeof window !== "undefined") {
        try {
          window.localStorage.setItem(
            NOTE_EDITOR_DRAFT_KEY,
            JSON.stringify({
              ...draft,
              updatedAt: new Date().toISOString(),
            } satisfies NoteEditorDraft),
          );
        } catch {
          // Ignore storage write failures and keep editor usable.
        }
      }
      setSaveState("saved");
      saveTimerRef.current = null;
    }, 400);
  }, [demoMode, noteEditorMode]);

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
      title: value,
      subject: noteSubject,
      tags: noteTags,
      content: notesText,
    });
  }, [noteSubject, noteTags, notesText, queueDraftSave]);

  const handleNoteSubjectChange = useCallback((value: string) => {
    setNoteSubject(value);
    queueDraftSave({
      title: noteTitle,
      subject: value,
      tags: noteTags,
      content: notesText,
    });
  }, [noteTags, noteTitle, notesText, queueDraftSave]);

  const handleNoteTagsChange = useCallback((value: string) => {
    setNoteTags(value);
    queueDraftSave({
      title: noteTitle,
      subject: noteSubject,
      tags: value,
      content: notesText,
    });
  }, [noteSubject, noteTitle, notesText, queueDraftSave]);

  const handleNotesTextChange = useCallback((value: string) => {
    setNotesText(value);
    queueDraftSave({
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
          <h1 className="text-2xl font-semibold text-foreground sm:text-3xl md:text-4xl">
            {noteEditorMode ? "Create your first note" : "Turn Notes Into Study Pack Materials"}
          </h1>
          {demoMode ? (
            <span className="inline-flex items-center rounded-full border border-blue-500/40 bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700 dark:bg-blue-950/30 dark:text-blue-300">
              Demo mode
            </span>
          ) : null}
        </div>
        <p className="text-base leading-relaxed text-foreground/75">
          {noteEditorMode
            ? "Write your note, then generate your first Study Pack when you're ready."
            : "Paste your notes or upload a photo. We'll organize everything into a clean summary, key concepts, and a quick practice quiz."}
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

