"use client";

import { useEffect, useRef, useState } from "react";
import { ChevronDown, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";

export type CreateMenuItem = {
  key: string;
  label: string;
  description?: string;
  onSelect: () => void;
};

type CreateMenuProps = {
  primaryLabel?: string;
  onPrimary: () => void;
  menuLabel?: string;
  items: CreateMenuItem[];
  className?: string;
};

export function CreateMenu({
  primaryLabel = "New Note",
  onPrimary,
  menuLabel = "Create options",
  items,
  className,
}: Readonly<CreateMenuProps>) {
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

    globalThis.document.addEventListener("mousedown", handlePointerDown);
    globalThis.document.addEventListener("keydown", handleEscape);
    return () => {
      globalThis.document.removeEventListener("mousedown", handlePointerDown);
      globalThis.document.removeEventListener("keydown", handleEscape);
    };
  }, [open]);

  return (
    <div className={`relative ${className ?? ""}`} ref={menuRef}>
      <div className="inline-flex w-full">
        <Button
          type="button"
          size="sm"
          className="flex-1 gap-2 rounded-r-none sm:flex-none"
          onClick={onPrimary}
        >
          <Plus className="h-4 w-4" aria-hidden="true" />
          <span>{primaryLabel}</span>
        </Button>
        <Button
          type="button"
          size="sm"
          aria-haspopup="menu"
          aria-expanded={open}
          aria-label={menuLabel}
          className="rounded-l-none border-l border-white/25 px-2"
          onClick={() => setOpen((previous) => !previous)}
        >
          <ChevronDown className="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>

      {open ? (
        <div
          role="menu"
          aria-label={menuLabel}
          className="motion-export-panel fixed inset-x-4 bottom-4 z-50 overflow-hidden rounded-2xl border border-border bg-background shadow-xl sm:absolute sm:bottom-auto sm:inset-x-auto sm:right-0 sm:top-full sm:mt-1 sm:min-w-64 sm:rounded-md sm:shadow-md"
        >
          <div className="border-b border-border px-4 py-3 sm:hidden">
            <p className="text-sm font-semibold text-foreground">{menuLabel}</p>
          </div>

          <div className="p-2 sm:p-1">
            {items.map((item) => (
              <button
                key={item.key}
                type="button"
                role="menuitem"
                className="motion-lift w-full rounded-lg px-3 py-3 text-left transition-colors hover:bg-highlight active:bg-highlight-strong sm:rounded sm:py-2"
                onClick={() => {
                  setOpen(false);
                  item.onSelect();
                }}
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
