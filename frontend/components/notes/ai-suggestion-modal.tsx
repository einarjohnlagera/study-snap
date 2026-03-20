"use client";

import { Button } from "@/components/ui/button";

type AiSuggestionModalProps = {
  open: boolean;
  title: string;
  subject: string | null;
  tags: string[];
  applying: boolean;
  onApply: () => void;
  onKeepMine: () => void;
};

export function AiSuggestionModal({
  open,
  title,
  subject,
  tags,
  applying,
  onApply,
  onKeepMine,
}: AiSuggestionModalProps) {
  if (!open) {
    return null;
  }

  return (
    <>
      <button
        type="button"
        className="fixed inset-0 z-40 bg-black/45"
        aria-label="Close AI suggestions"
        onClick={onKeepMine}
      />
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div className="w-full max-w-lg space-y-4 rounded-xl border border-border bg-background p-5 shadow-2xl sm:p-6">
          <h2 className="text-lg font-semibold text-foreground sm:text-xl">AI Suggestions</h2>
          <div className="space-y-3 text-sm text-foreground/85">
            <p><span className="font-medium text-foreground">Title:</span> {title}</p>
            <p><span className="font-medium text-foreground">Subject:</span> {subject || "No subject suggested"}</p>
            <p>
              <span className="font-medium text-foreground">Tags:</span>{" "}
              {tags.length > 0 ? tags.join(", ") : "No tags suggested"}
            </p>
          </div>
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={onKeepMine} disabled={applying}>
              Keep mine
            </Button>
            <Button type="button" onClick={onApply} disabled={applying}>
              {applying ? "Applying..." : "Apply suggestions"}
            </Button>
          </div>
        </div>
      </div>
    </>
  );
}
