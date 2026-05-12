"use client";

import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

type StickyAssessmentFooterProps = {
  children: ReactNode;
  hint?: ReactNode;
  variant?: "default" | "board-exam";
  "data-testid"?: string;
};

export function StickyAssessmentFooter({
  children,
  hint,
  variant = "default",
  "data-testid": dataTestId,
}: Readonly<StickyAssessmentFooterProps>) {
  return (
    <div
      data-testid={dataTestId}
      className={cn(
        "fixed inset-x-0 bottom-0 z-20 border-t bg-background/95 backdrop-blur",
        "pb-[env(safe-area-inset-bottom,0px)]",
        variant === "board-exam" ? "border-foreground/15" : "border-border",
      )}
    >
      <div className="mx-auto w-full max-w-3xl px-4 py-3 sm:px-6">
        {hint ? (
          <div className="mb-2 text-xs text-foreground/55">{hint}</div>
        ) : null}
        {children}
      </div>
    </div>
  );
}
