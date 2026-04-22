"use client";

import { FileText } from "lucide-react";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { getSelectionCardClassName } from "@/lib/clickable-card";

export type QuizExportModalMode = "QUIZ_ONLY" | "WITH_ANSWERS";

type QuizExportModalProps = {
  isOpen: boolean;
  title?: string;
  description?: string;
  exporting?: boolean;
  onClose: () => void;
  onSelect: (mode: QuizExportModalMode) => void;
};

const EXPORT_OPTIONS: Array<{
  mode: QuizExportModalMode;
  label: string;
  description: string;
}> = [
  {
    mode: "QUIZ_ONLY",
    label: "Quiz Only",
    description: "Questions and choices only. Ready for student distribution.",
  },
  {
    mode: "WITH_ANSWERS",
    label: "Quiz + Answers",
    description: "Includes answer key and explanations for teacher review.",
  },
];

export function QuizExportModal({
  isOpen,
  title = "Export",
  description = "Choose an export format.",
  exporting = false,
  onClose,
  onSelect,
}: Readonly<QuizExportModalProps>) {
  return (
    <AppModal
      isOpen={isOpen}
      title={title}
      description={description}
      onClose={() => {
        if (!exporting) {
          onClose();
        }
      }}
      actions={(
        <div className="flex justify-end">
          <Button type="button" variant="ghost" onClick={onClose} disabled={exporting}>
            Cancel
          </Button>
        </div>
      )}
    >
      <div className="space-y-3">
        {EXPORT_OPTIONS.map((option) => (
          <button
            key={option.mode}
            type="button"
            className={getSelectionCardClassName({
              disabled: exporting,
              className: "w-full rounded-2xl px-4 py-4",
            })}
            onClick={() => onSelect(option.mode)}
            disabled={exporting}
          >
            <div className="flex items-start gap-3">
              <span className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-border bg-muted/40 text-foreground/70 transition-colors duration-150 ease-out">
                <FileText className="h-4 w-4" aria-hidden="true" />
              </span>
              <span className="min-w-0 space-y-1">
                <span className="block text-sm font-semibold text-foreground">{option.label}</span>
                <span className="block text-sm leading-6 text-foreground/70">{option.description}</span>
              </span>
            </div>
          </button>
        ))}
      </div>
    </AppModal>
  );
}
