"use client";

import { useEffect, useRef, useState } from "react";
import { ChevronDown, FileText } from "lucide-react";
import { Button } from "@/components/ui/button";

export type ExportDropdownItem = {
  key: string;
  label: string;
  description?: string;
  disabled?: boolean;
  onSelect?: () => void;
};

type ExportDropdownMenuProps = {
  buttonLabel?: string;
  menuTitle?: string;
  menuDescription?: string;
  groupLabel?: string;
  exporting?: boolean;
  disabled?: boolean;
  items: ExportDropdownItem[];
};

export function ExportDropdownMenu({
  buttonLabel = "Export",
  menuTitle = "Export",
  menuDescription = "Choose what to export",
  groupLabel = "Export Options",
  exporting = false,
  disabled = false,
  items,
}: Readonly<ExportDropdownMenuProps>) {
  const menuRef = useRef<HTMLDivElement | null>(null);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open) {
      return undefined;
    }

    const handlePointerDown = (event: MouseEvent) => {
      if (!menuRef.current || menuRef.current.contains(event.target as Node)) {
        return;
      }
      setOpen(false);
    };
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
      }
    };

    document.addEventListener("mousedown", handlePointerDown);
    document.addEventListener("keydown", handleEscape);
    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      document.removeEventListener("keydown", handleEscape);
    };
  }, [open]);

  return (
    <div className="relative" ref={menuRef}>
      <Button
        type="button"
        variant="outline"
        size="sm"
        className="gap-2"
        onClick={() => setOpen((previous) => !previous)}
        disabled={disabled || exporting}
        aria-haspopup="menu"
        aria-expanded={open}
      >
        <FileText className="h-4 w-4" aria-hidden="true" />
        <span>{exporting ? "Exporting..." : buttonLabel}</span>
        <ChevronDown className="h-3 w-3" aria-hidden="true" />
      </Button>

      {open ? (
        <div
          role="menu"
          aria-label={menuTitle}
          className="motion-export-panel fixed inset-x-4 bottom-4 z-50 overflow-hidden rounded-2xl border border-border bg-background shadow-xl sm:absolute sm:bottom-auto sm:inset-x-auto sm:right-0 sm:top-full sm:mt-1 sm:min-w-50 sm:rounded-md sm:shadow-md"
        >
          <div className="border-b border-border px-4 py-3 sm:hidden">
            <p className="text-sm font-semibold text-foreground">{menuTitle}</p>
            <p className="text-xs text-foreground/60">{menuDescription}</p>
          </div>

          <p className="hidden px-3 pb-1 pt-2.5 text-[10px] font-semibold uppercase tracking-wide text-foreground/50 sm:block">
            {groupLabel}
          </p>

          <div className="p-2 sm:p-1">
            {items.map((item) => (
              <button
                key={item.key}
                type="button"
                className={`w-full rounded-lg px-3 py-3 text-left sm:rounded sm:py-2 ${
                  item.disabled
                    ? "cursor-not-allowed opacity-50"
                    : "motion-lift transition-colors hover:bg-highlight active:bg-highlight-strong"
                }`}
                onClick={() => {
                  if (item.disabled || !item.onSelect) {
                    return;
                  }
                  setOpen(false);
                  item.onSelect();
                }}
                disabled={item.disabled}
              >
                <span className="block text-sm font-medium text-foreground">{item.label}</span>
                {item.description ? (
                  <span className="block text-xs text-foreground/55">{item.description}</span>
                ) : null}
              </button>
            ))}
          </div>

          <div className="border-t border-border p-2 sm:hidden">
            <button
              type="button"
              className="motion-lift w-full rounded-lg px-3 py-2.5 text-center text-sm font-medium text-foreground/60 transition-colors hover:bg-highlight active:bg-highlight-strong"
              onClick={() => setOpen(false)}
            >
              Cancel
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
