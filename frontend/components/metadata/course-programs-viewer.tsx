"use client";

import { useState } from "react";
import { AppModal } from "@/components/ui/app-modal";

type CourseProgramsViewerProps = {
  programs: string[];
};

export function CourseProgramsViewer({ programs }: Readonly<CourseProgramsViewerProps>) {
  const [viewer, setViewer] = useState<"desktop" | "mobile" | null>(null);

  if (programs.length === 0) return null;
  if (programs.length === 1) return <p className="text-sm text-foreground/65">{programs[0]}</p>;

  const openViewer = () => {
    const isDesktop = typeof globalThis.matchMedia === "function"
      && globalThis.matchMedia("(min-width: 640px)").matches;
    setViewer(isDesktop ? "desktop" : "mobile");
  };

  return (
    <>
      <div className="relative w-fit">
        <button
          type="button"
          className="text-left text-sm text-foreground/65 underline-offset-2 hover:text-foreground hover:underline"
          onClick={openViewer}
          aria-expanded={viewer !== null}
          aria-haspopup="dialog"
        >
          Applies to {programs.length} programs
        </button>
        {viewer === "desktop" ? (
          <div role="dialog" aria-label="Applicable programs" className="absolute left-0 top-7 z-30 hidden w-64 rounded-lg border border-border bg-background p-3 shadow-lg sm:block">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Course / Program(s)</p>
            <ul className="mt-2 space-y-1 text-sm text-foreground/80">
              {programs.map((program) => <li key={program}>{program}</li>)}
            </ul>
            <button type="button" className="mt-3 text-xs font-medium text-blue-600 hover:underline dark:text-blue-400" onClick={() => setViewer(null)}>
              Close
            </button>
          </div>
        ) : null}
      </div>
      <AppModal isOpen={viewer === "mobile"} onClose={() => setViewer(null)} title="Course / Program(s)" variant="sheet">
        <ul className="space-y-2 text-sm text-foreground/80">
          {programs.map((program) => <li key={program}>{program}</li>)}
        </ul>
      </AppModal>
    </>
  );
}
