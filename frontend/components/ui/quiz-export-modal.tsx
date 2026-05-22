"use client";

import Link from "next/link";
import { useState } from "react";
import { FileText } from "lucide-react";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { getSelectionCardClassName } from "@/lib/clickable-card";
import type { QuizDocxHeaderOverride } from "@/lib/api";

export type QuizExportModalMode = "QUIZ_ONLY" | "WITH_ANSWERS";

type QuizExportModalProps = {
  isOpen: boolean;
  title?: string;
  description?: string;
  exporting?: boolean;
  showTeacherExportDetails?: boolean;
  schoolName?: string | null;
  onClose: () => void;
  onSelect: (mode: QuizExportModalMode, headerOverride?: QuizDocxHeaderOverride) => void;
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
  showTeacherExportDetails = false,
  schoolName = null,
  onClose,
  onSelect,
}: Readonly<QuizExportModalProps>) {
  const [className, setClassName] = useState("");
  const [includeDate, setIncludeDate] = useState(true);
  const headerOverride = showTeacherExportDetails
    ? {
      className: className.trim() || null,
      includeDate,
    }
    : undefined;

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
        {showTeacherExportDetails ? (
          <details className="rounded-xl border border-border bg-muted/20 px-4 py-3" open>
            <summary className="cursor-pointer text-sm font-semibold text-foreground">Export details</summary>
            <div className="mt-3 space-y-3 border-t border-border/70 pt-3">
              {schoolName?.trim() ? (
                <p className="text-sm text-foreground/70">
                  From your profile: <span className="font-medium text-foreground">{schoolName.trim()}</span>{" "}
                  <Link href="/profile" className="text-blue-600 hover:underline dark:text-blue-400">Edit</Link>
                </p>
              ) : (
                <p className="text-sm text-foreground/65">
                  Add your school name in Settings → Profile to include it in headers.{" "}
                  <Link href="/profile" className="text-blue-600 hover:underline dark:text-blue-400">Edit</Link>
                </p>
              )}
              <label className="block space-y-2">
                <span className="text-sm font-medium text-foreground">Class or section (optional)</span>
                <input
                  value={className}
                  onChange={(event) => setClassName(event.target.value.slice(0, 120))}
                  disabled={exporting}
                  maxLength={120}
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus-visible:ring-2 focus-visible:ring-blue-600"
                />
              </label>
              <label className="flex items-start gap-2 text-sm text-foreground/75">
                <input
                  type="checkbox"
                  checked={includeDate}
                  onChange={(event) => setIncludeDate(event.target.checked)}
                  disabled={exporting}
                  className="mt-0.5 h-4 w-4 rounded border-border text-blue-600 focus:ring-blue-600"
                />
                <span>Include today&apos;s date in the header</span>
              </label>
            </div>
          </details>
        ) : null}
        {EXPORT_OPTIONS.map((option) => (
          <button
            key={option.mode}
            type="button"
            className={getSelectionCardClassName({
              disabled: exporting,
              className: "w-full rounded-2xl px-4 py-4",
            })}
            onClick={() => onSelect(option.mode, headerOverride)}
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
