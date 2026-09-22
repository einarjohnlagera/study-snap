"use client";

import { Check } from "lucide-react";
import { cn } from "@/lib/utils";

type CampaignOptionRowProps = {
  checked: boolean;
  label: string;
  onToggle: () => void;
};

export function CampaignOptionRow({ checked, label, onToggle }: Readonly<CampaignOptionRowProps>) {
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={checked}
      onClick={onToggle}
      className={cn(
        "flex min-h-11 w-full items-center gap-3 rounded-xl border px-4 py-3 text-left text-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2",
        checked ? "border-blue-600 bg-blue-50 dark:bg-blue-950/30" : "border-border bg-background hover:bg-highlight",
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          "flex h-5 w-5 shrink-0 items-center justify-center rounded border",
          checked ? "border-blue-600 bg-blue-600 text-white" : "border-foreground/35 bg-background text-transparent",
        )}
      >
        <Check className="h-3.5 w-3.5" />
      </span>
      <span className="font-medium text-foreground">{label}</span>
    </button>
  );
}
