"use client";

import { Check } from "lucide-react";

type CheckboxProps = {
  id?: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  ariaLabel: string;
  disabled?: boolean;
};

export function Checkbox({
  id,
  checked,
  onChange,
  ariaLabel,
  disabled = false,
}: Readonly<CheckboxProps>) {
  return (
    <button
      id={id}
      type="button"
      role="checkbox"
      aria-checked={checked}
      aria-label={ariaLabel}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-md border transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 ${
        checked
          ? "border-blue-600 bg-blue-600 text-white"
          : "border-border bg-background text-transparent hover:border-blue-600/60"
      }`}
    >
      <Check aria-hidden="true" className="h-3.5 w-3.5" strokeWidth={3} />
    </button>
  );
}
