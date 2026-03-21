import { useEffect, useMemo } from "react";
import { AlertCircle, CheckCircle2, FileImage, Loader2, Sparkles, UploadCloud } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

type StudyInputCardProps = {
  isSampleNote: boolean;
  focusUpload: boolean;
  notesText: string;
  onNotesTextChange: (value: string) => void;
  imageFile: File | null;
  onImageFileChange: (file: File | null) => void;
  imageInputKey: number;
  canSaveNote: boolean;
  canGenerate: boolean;
  saving: boolean;
  loading: boolean;
  ocrFlowState: "idle" | "uploading" | "extracting" | "success" | "failure";
  ocrStatusMessage: string | null;
  onSaveNote: () => void;
  onGenerate: () => void;
};

export function StudyInputCard({
  isSampleNote,
  focusUpload,
  notesText,
  onNotesTextChange,
  imageFile,
  onImageFileChange,
  imageInputKey,
  canSaveNote,
  saving,
  canGenerate,
  loading,
  ocrFlowState,
  ocrStatusMessage,
  onSaveNote,
  onGenerate,
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

  return (
    <Card className="space-y-6 p-4 sm:p-6">
      <div className="space-y-2">
        <label htmlFor="study-notes" className="text-sm font-medium text-foreground">
          Study Notes
        </label>
        <textarea
          id="study-notes"
          value={notesText}
          onChange={(event) => onNotesTextChange(event.target.value)}
          placeholder="Paste your notes here..."
          className="min-h-64 w-full rounded-lg border border-border bg-background px-4 py-3 text-base leading-relaxed text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-blue-600"
        />
        <p className="text-sm text-foreground/70">
          Paste lecture notes, textbook excerpts, or study summaries. Best results come from one focused topic.
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

      <div className="flex flex-wrap gap-3">
        <Button
          type="button"
          variant="outline"
          disabled={!canSaveNote || saving || loading}
          onClick={onSaveNote}
          className="w-full sm:w-auto"
        >
          {saving ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              Saving Note...
            </>
          ) : (
            "Save Note"
          )}
        </Button>
        <Button
          type="button"
          disabled={!canGenerate || loading || saving}
          onClick={onGenerate}
          className={`w-full sm:w-auto ${isSampleNote ? "ring-2 ring-blue-300 dark:ring-blue-700" : ""}`}
        >
          {loading ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              Creating Study Pack...
            </>
          ) : (
            <>
              <Sparkles className="mr-2 h-4 w-4" />
              Generate Study Pack
            </>
          )}
        </Button>
      </div>
    </Card>
  );
}
