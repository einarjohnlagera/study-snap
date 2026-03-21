"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  ApiRequestError,
  createNote,
  createStudyPackFromNote,
  getNote,
  type NoteResponse,
  updateNote,
} from "@/lib/api";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { ToastMessage } from "@/components/ui/toast-message";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { AiSuggestionModal } from "@/components/notes/ai-suggestion-modal";
import { NoteEditorForm, type NoteEditorDraft } from "@/components/notes/note-editor-form";

type NoteEditorPageClientProps = {
  noteId?: string;
};

type PendingSuggestion = {
  noteId: string;
  title: string;
  subject: string | null;
  tags: string[];
};

function normalizeOptional(value: string): string | null {
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : null;
}

function toDraft(note: NoteResponse): NoteEditorDraft {
  return {
    title: note.title ?? "",
    subject: note.subject ?? "",
    content: note.content,
    tags: note.tags ?? [],
  };
}

function hasExistingMetadata(note: NoteResponse): boolean {
  return Boolean(
    (note.title && note.title.trim().length > 0)
    || (note.subject && note.subject.trim().length > 0)
    || (note.tags && note.tags.length > 0),
  );
}

export function NoteEditorPageClient({ noteId }: NoteEditorPageClientProps) {
  const router = useRouter();
  const isDetailPage = Boolean(noteId);
  const [draft, setDraft] = useState<NoteEditorDraft>({
    title: "",
    subject: "",
    content: "",
    tags: [],
  });
  const [currentNoteId, setCurrentNoteId] = useState<string | null>(noteId ?? null);
  const [loadingNote, setLoadingNote] = useState(isDetailPage);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isGenerating, setIsGenerating] = useState(false);
  const [saveStateLabel, setSaveStateLabel] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [toastTone, setToastTone] = useState<"success" | "error" | "info">("info");
  const [pendingSuggestion, setPendingSuggestion] = useState<PendingSuggestion | null>(null);
  const [applyingSuggestion, setApplyingSuggestion] = useState(false);

  useEffect(() => {
    if (!toastMessage) {
      return;
    }
    const timeout = window.setTimeout(() => {
      setToastMessage(null);
    }, 3200);
    return () => window.clearTimeout(timeout);
  }, [toastMessage]);

  const showToast = useCallback((message: string, tone: "success" | "error" | "info" = "info") => {
    setToastTone(tone);
    setToastMessage(message);
  }, []);

  useEffect(() => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
    if (!noteId) {
      setLoadingNote(false);
      return;
    }

    let active = true;
    setLoadingNote(true);
    setLoadError(null);

    void getNote(noteId)
      .then((note) => {
        if (!active) {
          return;
        }
        setDraft(toDraft(note));
        setCurrentNoteId(note.id);
      })
      .catch((error) => {
        if (!active) {
          return;
        }
        const message = error instanceof Error ? error.message : "Could not load note.";
        setLoadError(message);
      })
      .finally(() => {
        if (active) {
          setLoadingNote(false);
        }
      });

    return () => {
      active = false;
    };
  }, [noteId, router]);

  const contentEmpty = useMemo(() => draft.content.trim().length === 0, [draft.content]);

  const buildRequest = useCallback(() => ({
    title: normalizeOptional(draft.title),
    subject: normalizeOptional(draft.subject),
    tags: draft.tags,
    content: draft.content,
  }), [draft.content, draft.subject, draft.tags, draft.title]);

  const upsertNote = useCallback(async (): Promise<NoteResponse | null> => {
    if (contentEmpty) {
      showToast("Please add note content first.", "info");
      return null;
    }

    const payload = buildRequest();
    const saved = currentNoteId
      ? await updateNote(currentNoteId, payload)
      : await createNote(payload);

    setCurrentNoteId(saved.id);
    setDraft(toDraft(saved));
    return saved;
  }, [buildRequest, contentEmpty, currentNoteId, showToast]);

  const handleSave = useCallback(async () => {
    if (isSaving || isGenerating || contentEmpty) {
      return;
    }

    setIsSaving(true);
    setSaveStateLabel("Saving...");
    try {
      const saved = await upsertNote();
      if (!saved) {
        return;
      }
      setSaveStateLabel("Saved");
      if (isDetailPage) {
        router.push(`/notes/${saved.id}?saved=1`);
        return;
      }
      router.push(`/notes/${saved.id}`);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Could not save note.";
      showToast(message, "error");
      setSaveStateLabel(null);
    } finally {
      setIsSaving(false);
    }
  }, [contentEmpty, isDetailPage, isGenerating, isSaving, router, showToast, upsertNote]);

  const finalizeGenerationRedirect = useCallback((noteIdToOpen: string) => {
    router.push(`/notes/${noteIdToOpen}?from=notes&created=1`);
  }, [router]);

  const handleGenerate = useCallback(async () => {
    if (isGenerating || isSaving || contentEmpty) {
      return;
    }

    setIsGenerating(true);
    try {
      const saved = await upsertNote();
      if (!saved) {
        return;
      }

      const generated = await createStudyPackFromNote(saved.id);
      const hasUserMetadata = hasExistingMetadata(saved);

      if (!hasUserMetadata) {
        const autoFillPayload = {
          title: generated.title,
          subject: generated.subject ?? null,
          tags: generated.tags ?? [],
          content: saved.content,
        };
        const updated = await updateNote(saved.id, autoFillPayload);
        setDraft(toDraft(updated));
        setCurrentNoteId(updated.id);
        finalizeGenerationRedirect(saved.id);
        return;
      }

      setPendingSuggestion({
        noteId: saved.id,
        title: generated.title,
        subject: generated.subject ?? null,
        tags: generated.tags ?? [],
      });
    } catch (error) {
      if (error instanceof ApiRequestError && error.code === "EMAIL_VERIFICATION_REQUIRED") {
        showToast("Verify your email before generating a Study Pack.", "info");
      } else {
        const message = error instanceof Error ? error.message : "Could not generate Study Pack.";
        showToast(message, "error");
      }
    } finally {
      setIsGenerating(false);
    }
  }, [contentEmpty, finalizeGenerationRedirect, isGenerating, isSaving, showToast, upsertNote]);

  const applySuggestions = useCallback(async () => {
    if (!pendingSuggestion || applyingSuggestion) {
      return;
    }

    setApplyingSuggestion(true);
    try {
      const updated = await updateNote(pendingSuggestion.noteId, {
        title: pendingSuggestion.title,
        subject: pendingSuggestion.subject,
        tags: pendingSuggestion.tags,
        content: draft.content,
      });
      setDraft(toDraft(updated));
      setCurrentNoteId(updated.id);
      setPendingSuggestion(null);
      finalizeGenerationRedirect(pendingSuggestion.noteId);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Could not apply suggestions.";
      showToast(message, "error");
    } finally {
      setApplyingSuggestion(false);
    }
  }, [applyingSuggestion, draft.content, finalizeGenerationRedirect, pendingSuggestion, showToast]);

  const keepMineAndContinue = useCallback(() => {
    if (!pendingSuggestion) {
      return;
    }
    const noteIdToOpen = pendingSuggestion.noteId;
    setPendingSuggestion(null);
    finalizeGenerationRedirect(noteIdToOpen);
  }, [finalizeGenerationRedirect, pendingSuggestion]);

  const pageTitle = isDetailPage ? "Note" : "New Note";
  const studyPackMessage = isDetailPage
    ? "Generate a Study Pack from this note when you are ready."
    : "Save your note for later, or generate immediately when the content is ready.";

  if (loadingNote) {
    return (
      <main className="mx-auto w-full max-w-3xl px-4 py-8 sm:px-6">
        <Card className="space-y-3 p-4 sm:p-6">
          <div className="h-6 w-40 animate-pulse rounded bg-foreground/10" />
          <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
          <div className="h-52 w-full animate-pulse rounded bg-foreground/10" />
        </Card>
      </main>
    );
  }

  if (loadError) {
    return (
      <main className="mx-auto w-full max-w-3xl px-4 py-8 sm:px-6">
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-lg font-semibold sm:text-xl">Could not load note</h1>
          <p className="text-sm text-foreground/75">{loadError}</p>
          <Button type="button" variant="outline" onClick={() => router.refresh()}>
            Retry
          </Button>
        </Card>
      </main>
    );
  }

  return (
    <>
      <NoteEditorForm
        pageTitle={pageTitle}
        note={draft}
        onTitleChange={(value) => setDraft((previous) => ({ ...previous, title: value }))}
        onSubjectChange={(value) => setDraft((previous) => ({ ...previous, subject: value }))}
        onContentChange={(value) => setDraft((previous) => ({ ...previous, content: value }))}
        onTagsChange={
          isDetailPage
            ? (nextTags) => setDraft((previous) => ({ ...previous, tags: nextTags }))
            : undefined
        }
        onSave={() => {
          void handleSave();
        }}
        onGenerate={() => {
          void handleGenerate();
        }}
        isSaving={isSaving}
        isGenerating={isGenerating}
        saveStateLabel={saveStateLabel}
        helperText="Save your note for later, or generate a Study Pack instantly using 1 credit."
        showTagsSection={isDetailPage}
        studyPackMessage={studyPackMessage}
      />

      <AiSuggestionModal
        open={Boolean(pendingSuggestion)}
        title={pendingSuggestion?.title ?? ""}
        subject={pendingSuggestion?.subject ?? null}
        tags={pendingSuggestion?.tags ?? []}
        applying={applyingSuggestion}
        onApply={() => {
          void applySuggestions();
        }}
        onKeepMine={keepMineAndContinue}
      />

      {toastMessage ? <ToastMessage message={toastMessage} tone={toastTone} /> : null}
    </>
  );
}
