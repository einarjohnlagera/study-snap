import { useEffect, useMemo } from "react";
import Link from "next/link";
import { AlertCircle, CheckCircle2, FileImage, Loader2, Sparkles, UploadCloud } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

type StudyInputCardProps = {
  noteEditorMode: boolean;
  isSampleNote: boolean;
  saveState: "saving" | "saved";
  focusUpload: boolean;
  noteTitle: string;
  onNoteTitleChange: (value: string) => void;
  noteSubject: string;
  onNoteSubjectChange: (value: string) => void;
  noteTags: string;
  onNoteTagsChange: (value: string) => void;
  notesText: string;
  onNotesTextChange: (value: string) => void;
  imageFile: File | null;
  onImageFileChange: (file: File | null) => void;
  imageInputKey: number;
  hasStudyPack: boolean;
  currentStudyPackId: string | null;
  canGenerate: boolean;
  loading: boolean;
  ocrFlowState: "idle" | "uploading" | "extracting" | "success" | "failure";
  ocrStatusMessage: string | null;
  onGenerate: () => void;
  onClear: () => void;
};

export function StudyInputCard({
  noteEditorMode,
  isSampleNote,
  saveState,
  focusUpload,
  noteTitle,
  onNoteTitleChange,
  noteSubject,
  onNoteSubjectChange,
  noteTags,
  onNoteTagsChange,
  notesText,
  onNotesTextChange,
  imageFile,
  onImageFileChange,
  imageInputKey,
  hasStudyPack,
  currentStudyPackId,
  canGenerate,
  loading,
  ocrFlowState,
  ocrStatusMessage,
  onGenerate,
  onClear,
}: StudyInputCardProps) {
  const imagePreviewUrl = useMemo(() => {
    if (!imageFile) {
      return null;
    }
    return URL.createObjectURL(imageFile);
  }, [imageFile]);

  useEffect(() => {
    return () => {
      if (imagePreviewUrl) {
        URL.revokeObjectURL(imagePreviewUrl);
      }
    };
  }, [imagePreviewUrl]);

  useEffect(() => {
    if (!focusUpload) {
      return;
    }
    const uploadSection = document.getElementById("study-upload-section");
    uploadSection?.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [focusUpload]);

  const statusTone = ocrFlowState === "failure"
    ? "border-red-500/40 bg-red-50/70 text-red-800 dark:bg-red-950/30 dark:text-red-200"
    : ocrFlowState === "success"
      ? "border-emerald-500/40 bg-emerald-50/70 text-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-200"
      : "border-blue-500/40 bg-blue-50/70 text-blue-800 dark:bg-blue-950/30 dark:text-blue-200";

  const StatusIcon = ocrFlowState === "failure"
    ? AlertCircle
    : ocrFlowState === "success"
      ? CheckCircle2
      : Loader2;
  const actionLabel = hasStudyPack ? "Regenerate Study Pack" : "Generate Study Pack";
  const helperText = hasStudyPack
    ? "Current Study Pack is linked to this note."
    : isSampleNote
      ? "This is a sample note. Try generating a Study Pack."
      : "This note doesn\u2019t have a Study Pack yet.";
  const highlightedGenerateCta = isSampleNote && !hasStudyPack;
  const showOpenStudyPack = Boolean(hasStudyPack && currentStudyPackId);

  return (
    <Card className="space-y-6 p-4 sm:p-6">
      {noteEditorMode ? (
        <div className="space-y-4">
          <div className="space-y-2 sm:col-span-2">
            <div className="flex items-center justify-between gap-3">
              <label htmlFor="note-title" className="text-sm font-medium text-foreground">
                Title
              </label>
              <span className="text-xs text-foreground/60">
                {saveState === "saving" ? "Saving..." : "Saved"}
              </span>
            </div>
            <input
              id="note-title"
              type="text"
              value={noteTitle}
              onChange={(event) => onNoteTitleChange(event.target.value)}
              placeholder="Enter note title"
              className="h-11 w-full rounded-lg border border-border bg-background px-3 text-base font-medium text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-blue-600"
            />
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-2">
              <label htmlFor="note-subject" className="text-sm font-medium text-foreground">
                Subject
              </label>
              <input
                id="note-subject"
                type="text"
                value={noteSubject}
                onChange={(event) => onNoteSubjectChange(event.target.value)}
                placeholder="e.g. Biology"
                className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-blue-600"
              />
            </div>
            <div className="space-y-2">
              <label htmlFor="note-tags" className="text-sm font-medium text-foreground">
                Tags
              </label>
              <input
                id="note-tags"
                type="text"
                value={noteTags}
                onChange={(event) => onNoteTagsChange(event.target.value)}
                placeholder="e.g. photosynthesis, chapter 3"
                className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-blue-600"
              />
            </div>
          </div>
        </div>
      ) : null}

      <div className="space-y-2">
        <label htmlFor="study-notes" className="text-sm font-medium text-foreground">
          {noteEditorMode ? "Content" : "Study Notes"}
        </label>
        <textarea
          id="study-notes"
          value={notesText}
          onChange={(event) => onNotesTextChange(event.target.value)}
          placeholder={noteEditorMode ? "Write or paste your note content..." : "Paste your class notes here..."}
          className={`w-full rounded-lg border border-border bg-background px-3 py-2 text-base leading-relaxed text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-blue-600 ${noteEditorMode ? "min-h-72" : "min-h-40"}`}
        />
        <p className="text-sm text-foreground/70">
          {noteEditorMode
            ? "Keep it simple. You can refine this note later and regenerate any time."
            : "Paste lecture notes, lesson notes text, or interview preparation notes. Best results when the notes focus on a single topic."}
        </p>
      </div>

      <div className="space-y-2">
        <label htmlFor="study-image" className="text-sm font-medium text-foreground">
          Notes Photo (OCR, optional)
        </label>
        <div
          id="study-upload-section"
          className={`space-y-3 rounded-lg border bg-background p-3 ${focusUpload ? "border-blue-500/60 ring-2 ring-blue-500/20" : "border-border"}`}
        >
          <div className="flex items-start gap-2 text-sm text-foreground/75">
            <UploadCloud className="mt-0.5 h-4 w-4 text-foreground/60" />
            <p>Upload a clear image of your notes. You can review and edit extracted text before generating.</p>
          </div>
          <div className="flex items-center gap-3 rounded-lg border border-border bg-background px-3 py-2">
            <FileImage className="h-4 w-4 text-foreground/60" />
            <input
              key={imageInputKey}
              id="study-image"
              type="file"
              accept="image/png,image/jpeg,image/webp"
              onChange={(event) => {
                const file = event.target.files?.[0] ?? null;
                onImageFileChange(file);
              }}
              className="w-full cursor-pointer text-sm text-foreground/75 file:mr-3 file:cursor-pointer file:rounded-lg file:border-0 file:bg-blue-600 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-white hover:file:bg-blue-700"
            />
          </div>
          {imagePreviewUrl ? (
            <div className="space-y-2">
              <img
                src={imagePreviewUrl}
                alt="Selected notes preview"
                className="max-h-56 w-full rounded-lg border border-border object-contain"
              />
              <p className="text-sm text-foreground/70">
                Selected: {imageFile?.name} ({((imageFile?.size ?? 0) / (1024 * 1024)).toFixed(2)} MB)
              </p>
            </div>
          ) : null}
          {imageFile && ocrStatusMessage ? (
            <div className={`rounded-md border px-3 py-2 text-sm ${statusTone}`}>
              <div className="flex items-start gap-2">
                <StatusIcon
                  className={`mt-0.5 h-4 w-4 ${ocrFlowState === "uploading" || ocrFlowState === "extracting" ? "animate-spin" : ""}`}
                  aria-hidden="true"
                />
                <p>{ocrStatusMessage}</p>
              </div>
            </div>
          ) : null}
        </div>
        <p className="text-xs text-foreground/65">
          Supported formats: PNG, JPEG, WEBP. Max file size: 5 MB. Uploaded images are processed and deleted after generation.
        </p>
      </div>

      {noteEditorMode ? (
        <div className="space-y-3 rounded-lg border border-border bg-muted/20 p-3">
          <p className="text-sm text-foreground/80">{helperText}</p>
          {hasStudyPack ? (
            <p className="text-xs text-foreground/60">
              Regenerating will replace the current Study Pack for this note.
            </p>
          ) : null}
          <div className="hidden flex-wrap gap-3 sm:flex">
            {showOpenStudyPack ? (
              <Link href={`/study-packs/${currentStudyPackId}`} className="w-full sm:w-auto">
                <Button type="button" variant="outline" className="w-full sm:w-auto">
                  Open Study Pack
                </Button>
              </Link>
            ) : null}
            <Button
              type="button"
              disabled={!canGenerate || loading}
              onClick={onGenerate}
              className={`w-full sm:w-auto ${highlightedGenerateCta ? "ring-2 ring-blue-300 dark:ring-blue-700" : ""}`}
            >
              {loading ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Generating your study pack...
                </>
              ) : (
                <>
                  <Sparkles className="mr-2 h-4 w-4" />
                  {actionLabel}
                </>
              )}
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={onClear}
              disabled={loading}
              className="w-full sm:w-auto"
            >
              Clear Inputs
            </Button>
          </div>
          {showOpenStudyPack ? (
            <div className="sm:hidden">
              <Link href={`/study-packs/${currentStudyPackId}`} className="w-full">
                <Button type="button" variant="outline" className="w-full">
                  Open Study Pack
                </Button>
              </Link>
            </div>
          ) : null}
        </div>
      ) : null}

      {!noteEditorMode ? (
        <div className="flex flex-wrap gap-3">
          <Button
            type="button"
            disabled={!canGenerate || loading}
            onClick={onGenerate}
            className="w-full sm:w-auto"
          >
            {loading ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Generating study pack materials...
              </>
            ) : (
              <>
                <Sparkles className="mr-2 h-4 w-4" />
                Generate Study Pack
              </>
            )}
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={onClear}
            disabled={loading}
            className="w-full sm:w-auto"
          >
            Clear Inputs
          </Button>
        </div>
      ) : null}

      {noteEditorMode ? (
        <div className="h-16 sm:hidden" aria-hidden="true" />
      ) : null}

      {noteEditorMode ? (
        <div className="fixed inset-x-0 bottom-0 z-40 border-t border-border bg-background/95 p-3 backdrop-blur sm:hidden">
          <Button
            type="button"
            disabled={!canGenerate || loading}
            onClick={onGenerate}
            className={`w-full ${highlightedGenerateCta ? "ring-2 ring-blue-300 dark:ring-blue-700" : ""}`}
          >
            {loading ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Generating your study pack...
              </>
            ) : (
              <>
                <Sparkles className="mr-2 h-4 w-4" />
                {actionLabel}
              </>
            )}
          </Button>
        </div>
      ) : null}
    </Card>
  );
}

