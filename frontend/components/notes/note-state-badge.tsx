"use client";

import type { NoteStudyPackStatus } from "@/lib/api";

export function getNoteStateMeta(status: NoteStudyPackStatus) {
  if (status === "STUDY_PACK_READY") {
    return {
      label: "Study Pack Ready",
      className: "border-blue-500/40 bg-blue-500/10 text-blue-700 dark:text-blue-300",
    };
  }
  if (status === "GENERATING") {
    return {
      label: "Generating",
      className: "border-blue-500/40 bg-blue-500/10 text-blue-700 dark:text-blue-300",
    };
  }
  if (status === "FAILED") {
    return {
      label: "Generation Failed",
      className: "border-red-500/40 bg-red-500/10 text-red-700 dark:text-red-300",
    };
  }

  return {
    label: "Draft",
    className: "border-border bg-muted/50 text-foreground/70",
  };
}

export function NoteStateBadge({
  status,
}: Readonly<{
  status: NoteStudyPackStatus;
}>) {
  const meta = getNoteStateMeta(status);

  return (
    <span className={`inline-flex items-center rounded-full border px-2 py-1 text-xs font-medium ${meta.className}`}>
      {meta.label}
    </span>
  );
}
