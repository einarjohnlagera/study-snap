import { FileImage, Loader2, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

type StudyInputCardProps = {
  notesText: string;
  onNotesTextChange: (value: string) => void;
  imageFile: File | null;
  onImageFileChange: (file: File | null) => void;
  imageInputKey: number;
  canGenerate: boolean;
  loading: boolean;
  onGenerate: () => void;
  onClear: () => void;
};

export function StudyInputCard({
  notesText,
  onNotesTextChange,
  imageFile,
  onImageFileChange,
  imageInputKey,
  canGenerate,
  loading,
  onGenerate,
  onClear,
}: StudyInputCardProps) {
  return (
    <Card className="space-y-6">
      <div className="space-y-2">
        <label htmlFor="study-notes" className="text-sm font-medium text-foreground">
          Study Notes
        </label>
        <textarea
          id="study-notes"
          value={notesText}
          onChange={(event) => onNotesTextChange(event.target.value)}
          placeholder="Paste your class notes here..."
          className="min-h-40 w-full rounded-lg border border-border bg-background px-3 py-2 text-base leading-relaxed text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-blue-600"
        />
        <p className="text-sm text-foreground/70">
          Paste lecture notes, reviewer text, or interview preparation notes.
          Best results when the notes focus on a single topic.
        </p>
      </div>

      <div className="space-y-2">
        <label htmlFor="study-image" className="text-sm font-medium text-foreground">
          Notes Photo (optional)
        </label>
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
        <p className="text-xs text-foreground/65">
          Uploaded images are processed and deleted after review generation.
        </p>
        {imageFile ? (
          <p className="text-sm text-foreground/70">Selected: {imageFile.name}</p>
        ) : null}
      </div>

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
              Generating review materials...
            </>
          ) : (
            <>
              <Sparkles className="mr-2 h-4 w-4" />
              Generate Review
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
          Clear Notes
        </Button>
      </div>
    </Card>
  );
}
