"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Check, ChevronDown } from "lucide-react";

type SubjectComboboxProps = {
  id: string;
  value: string;
  suggestions: string[];
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  helperText?: string;
};

function normalize(value: string): string {
  return value.trim().toLowerCase();
}

export function SubjectCombobox({
  id,
  value,
  suggestions,
  onChange,
  placeholder = "Choose or type a subject",
  disabled = false,
  helperText = "Select an existing subject or type your own.",
}: Readonly<SubjectComboboxProps>) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!open) {
      return;
    }

    const handlePointerDown = (event: MouseEvent) => {
      const target = event.target as Node | null;
      if (!rootRef.current?.contains(target)) {
        setOpen(false);
      }
    };

    globalThis.addEventListener("mousedown", handlePointerDown);
    return () => {
      globalThis.removeEventListener("mousedown", handlePointerDown);
    };
  }, [open]);

  const normalizedValue = normalize(value);

  const exactMatchExists = useMemo(
    () => suggestions.some((subject) => normalize(subject) === normalizedValue),
    [normalizedValue, suggestions],
  );

  const filteredSuggestions = useMemo(() => {
    if (normalizedValue.length === 0 || exactMatchExists) {
      return suggestions;
    }
    return suggestions.filter((subject) => subject.toLowerCase().includes(normalizedValue));
  }, [exactMatchExists, normalizedValue, suggestions]);

  const showCreateOption = normalizedValue.length > 0 && !exactMatchExists;
  const showDropdown = open && !disabled && (filteredSuggestions.length > 0 || showCreateOption);

  return (
    <div ref={rootRef} className="space-y-2">
      <div className="relative">
        <input
          id={id}
          type="text"
          value={value}
          disabled={disabled}
          onFocus={() => setOpen(true)}
          onChange={(event) => {
            onChange(event.target.value);
            setOpen(true);
          }}
          onKeyDown={(event) => {
            if (event.key === "Escape") {
              setOpen(false);
            }
          }}
          placeholder={placeholder}
          autoComplete="off"
          aria-autocomplete="list"
          aria-expanded={showDropdown}
          aria-controls={`${id}-options`}
          className="h-11 w-full rounded-lg border border-border bg-background px-3 pr-10 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus-visible:ring-2 focus-visible:ring-blue-600 disabled:cursor-not-allowed disabled:opacity-60"
        />
        <button
          type="button"
          onClick={() => setOpen((previous) => !previous)}
          disabled={disabled}
          aria-label="Toggle subject suggestions"
          className="absolute inset-y-0 right-0 flex w-10 items-center justify-center text-foreground/60 transition-colors hover:text-foreground disabled:cursor-not-allowed disabled:opacity-60"
        >
          <ChevronDown className={`h-4 w-4 transition-transform ${showDropdown ? "rotate-180" : ""}`} />
        </button>
        {showDropdown ? (
          <div
            id={`${id}-options`}
            role="listbox"
            className="absolute z-30 mt-2 max-h-60 w-full overflow-y-auto rounded-lg border border-border bg-background p-1 shadow-lg"
          >
            {showCreateOption ? (
              <button
                type="button"
                className="flex w-full items-center justify-between rounded-md px-3 py-2 text-left text-sm text-foreground hover:bg-muted/60"
                onClick={() => {
                  onChange(value.trim());
                  setOpen(false);
                }}
              >
                <span>{`Use "${value.trim()}"`}</span>
                <span className="text-xs text-foreground/55">Custom</span>
              </button>
            ) : null}
            {filteredSuggestions.map((subject) => {
              const isSelected = normalize(subject) === normalizedValue;
              return (
                <button
                  key={subject}
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  className="flex w-full items-center justify-between rounded-md px-3 py-2 text-left text-sm text-foreground hover:bg-muted/60"
                  onClick={() => {
                    onChange(subject);
                    setOpen(false);
                  }}
                >
                  <span>{subject}</span>
                  {isSelected ? <Check className="h-4 w-4 text-blue-600 dark:text-blue-400" /> : null}
                </button>
              );
            })}
          </div>
        ) : null}
      </div>
      <p className="text-xs text-foreground/60">{helperText}</p>
    </div>
  );
}
