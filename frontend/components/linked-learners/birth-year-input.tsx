"use client";

import { ChevronDown, ChevronUp } from "lucide-react";

const INPUT_CLASSES = "h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary";
const MINIMUM_BIRTH_YEAR = 1900;

/**
 * The one birth-year control for every connection-formation path. Steppers remain disabled until
 * four digits exist, seed no declaration, and avoid the scroll-wheel mutation of type=number.
 */
export function BirthYearInput({
  id,
  value,
  onChange,
  inputRef,
  invalid,
  describedBy,
  required,
}: Readonly<{
  id: string;
  value: string;
  onChange: (next: string) => void;
  inputRef?: React.RefObject<HTMLInputElement | null>;
  invalid?: boolean;
  describedBy?: string;
  required?: boolean;
}>) {
  const maxYear = new Date().getFullYear();
  const stepDisabled = value.trim().length !== 4;

  const step = (delta: number) => {
    const parsed = Number(value.trim());
    if (!Number.isFinite(parsed)) return;
    onChange(String(Math.min(Math.max(parsed + delta, MINIMUM_BIRTH_YEAR), maxYear)));
  };

  return (
    <div className="relative w-32">
      <input
        id={id}
        ref={inputRef}
        className={`${INPUT_CLASSES} pr-9 tabular-nums`}
        value={value}
        onChange={(event) => onChange(event.target.value.replace(/\D/g, "").slice(0, 4))}
        inputMode="numeric"
        autoComplete="off"
        maxLength={4}
        placeholder="YYYY"
        required={required}
        aria-invalid={invalid ? true : undefined}
        aria-describedby={describedBy}
      />
      <div className="absolute inset-y-0 right-1 flex flex-col justify-center">
        <button type="button" aria-label="Increase birth year" disabled={stepDisabled} onClick={() => step(1)} className="flex h-4 w-6 items-center justify-center rounded text-foreground/50 transition-colors hover:bg-highlight hover:text-foreground disabled:pointer-events-none disabled:opacity-30">
          <ChevronUp className="h-3 w-3" aria-hidden="true" />
        </button>
        <button type="button" aria-label="Decrease birth year" disabled={stepDisabled} onClick={() => step(-1)} className="flex h-4 w-6 items-center justify-center rounded text-foreground/50 transition-colors hover:bg-highlight hover:text-foreground disabled:pointer-events-none disabled:opacity-30">
          <ChevronDown className="h-3 w-3" aria-hidden="true" />
        </button>
      </div>
    </div>
  );
}
