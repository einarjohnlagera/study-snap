"use client";

import { Button } from "@/components/ui/button";
import { AppModal } from "@/components/ui/app-modal";

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
    <AppModal
      isOpen={open}
      title="AI Suggestions"
      onClose={onKeepMine}
      panelClassName="max-w-lg"
      actions={(
        <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" onClick={onKeepMine} disabled={applying}>
            Keep mine
          </Button>
          <Button type="button" onClick={onApply} disabled={applying}>
            {applying ? "Applying..." : "Apply suggestions"}
          </Button>
        </div>
      )}
    >
      <div className="space-y-3 text-sm text-foreground/85">
        <p><span className="font-medium text-foreground">Title:</span> {title}</p>
        <p><span className="font-medium text-foreground">Subject:</span> {subject || "No subject suggested"}</p>
        <p>
          <span className="font-medium text-foreground">Tags:</span>{" "}
          {tags.length > 0 ? tags.join(", ") : "No tags suggested"}
        </p>
      </div>
    </AppModal>
  );
}
