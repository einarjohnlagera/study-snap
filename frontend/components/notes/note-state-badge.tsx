"use client";

import type { NoteStudyPackStatus } from "@/lib/api";

export function getNoteStateMeta(status: NoteStudyPackStatus) {
  if (status === "STUDY_PACK_READY") {
    return {
      label: "Study Pack Ready",
      className: "border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300",
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
