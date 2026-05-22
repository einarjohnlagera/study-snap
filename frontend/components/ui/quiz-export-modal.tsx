"use client";

import Link from "next/link";
import { useState } from "react";
import { FileText } from "lucide-react";
import { PaywallModal } from "@/components/billing/paywall-modal";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { getSelectionCardClassName } from "@/lib/clickable-card";
import type { PlanType, QuizDocxHeaderOverride } from "@/lib/api";

export type QuizExportModalMode = "QUIZ_ONLY" | "WITH_ANSWERS";
export type QuizDocxVersionCount = 1 | 2 | 3;

type QuizExportModalProps = {
  isOpen: boolean;
  title?: string;
  description?: string;
  exporting?: boolean;
  showTeacherExportDetails?: boolean;
  schoolName?: string | null;
  teacherPlan?: PlanType | null;
  onClose: () => void;
  onSelect: (
    mode: QuizExportModalMode,
    headerOverride?: QuizDocxHeaderOverride,
    versionCount?: QuizDocxVersionCount,
  ) => void;
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
const QUIZ_DOCX_VERSION_COUNTS: QuizDocxVersionCount[] = [1, 2, 3];

export function QuizExportModal({
  isOpen,
  title = "Export",
  description = "Choose an export format.",
  exporting = false,
  showTeacherExportDetails = false,
  schoolName = null,
  teacherPlan = null,
  onClose,
  onSelect,
}: Readonly<QuizExportModalProps>) {
  const [className, setClassName] = useState("");
  const [includeDate, setIncludeDate] = useState(true);
  const [versionCount, setVersionCount] = useState<QuizDocxVersionCount>(1);
  const [versionPaywallOpen, setVersionPaywallOpen] = useState(false);

  const resetVersionSelection = () => {
    setVersionCount(1);
    setVersionPaywallOpen(false);
  };
  const handleClose = () => {
    if (!exporting) {
      resetVersionSelection();
      onClose();
    }
  };

  const headerOverride = showTeacherExportDetails
    ? {
      className: className.trim() || null,
      includeDate,
    }
    : undefined;

  return (
    <>
      <AppModal
        isOpen={isOpen}
        title={title}
        description={description}
        onClose={handleClose}
        actions={(
          <div className="flex justify-end">
            <Button type="button" variant="ghost" onClick={handleClose} disabled={exporting}>
              Cancel
            </Button>
          </div>
        )}
      >
        <div className="space-y-3">
          {showTeacherExportDetails ? (
            <>
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
              <div className="space-y-2 rounded-xl border border-border bg-muted/20 px-4 py-3">
                <p className="text-sm font-semibold text-foreground">Versions</p>
                <div
                  className="flex w-full gap-1 rounded-lg border border-border bg-background/80 p-1"
                  role="group"
                  aria-label="DOCX exam versions"
                >
                  {QUIZ_DOCX_VERSION_COUNTS.map((count) => {
                    const isLocked = teacherPlan === "FREE" && count !== 1;
                    const isSelected = versionCount === count;
                    return (
                      <button
                        key={count}
                        type="button"
                        aria-pressed={isSelected}
                        disabled={exporting}
                        onClick={() => {
                          if (isLocked) {
                            setVersionPaywallOpen(true);
                            return;
                          }
                          setVersionCount(count);
                        }}
                        className={`inline-flex h-10 flex-1 items-center justify-center gap-1.5 rounded-md px-3 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 disabled:pointer-events-none disabled:opacity-50 ${
                          isSelected
                            ? "bg-background text-foreground shadow-sm"
                            : "text-foreground/70 hover:bg-background hover:text-foreground"
                        }`}
                      >
                        <span>{count}</span>
                        {isLocked ? (
                          <span className="rounded-full border border-blue-500/20 bg-blue-500/10 px-1.5 py-0.5 text-[10px] font-semibold text-blue-700 dark:text-blue-300">
                            Plus
                          </span>
                        ) : null}
                      </button>
                    );
                  })}
                </div>
                <p className="text-xs leading-5 text-foreground/60">
                  Generates A, B, C versions with shuffled question and choice order — useful for anti-cheating.
                </p>
              </div>
            </>
          ) : null}
          {EXPORT_OPTIONS.map((option) => (
            <button
              key={option.mode}
              type="button"
              className={getSelectionCardClassName({
                disabled: exporting,
                className: "w-full rounded-2xl px-4 py-4",
              })}
              onClick={() => {
                onSelect(option.mode, headerOverride, showTeacherExportDetails ? versionCount : undefined);
                resetVersionSelection();
              }}
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
      <PaywallModal
        isOpen={versionPaywallOpen}
        variant="teacher-exam-versions"
        source="quiz_export_versions"
        onClose={() => setVersionPaywallOpen(false)}
      />
    </>
  );
}
