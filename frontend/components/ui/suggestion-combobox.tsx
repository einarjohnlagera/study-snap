"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Check, ChevronDown } from "lucide-react";

export type SuggestionComboboxOption = {
  value: string;
  label: string;
};

type SuggestionComboboxProps = {
  id: string;
  value: string;
  options: SuggestionComboboxOption[];
  onChange: (value: string) => void;
  ariaLabel?: string;
  placeholder?: string;
  disabled?: boolean;
  helperText?: string;
  allowCustom?: boolean;
  toggleLabel?: string;
  customOptionLabel?: string;
};

function normalize(value: string): string {
  return value.trim().toLowerCase();
}

function matchesOption(option: SuggestionComboboxOption, value: string): boolean {
  const normalizedValue = normalize(value);
  return normalize(option.value) === normalizedValue || normalize(option.label) === normalizedValue;
}

export function SuggestionCombobox({
  id,
  value,
  options,
  onChange,
  ariaLabel,
  placeholder,
  disabled = false,
  helperText,
  allowCustom = true,
  toggleLabel = "Toggle suggestions",
  customOptionLabel = "Custom",
}: Readonly<SuggestionComboboxProps>) {
  const [open, setOpen] = useState(false);
  const [inputValue, setInputValue] = useState("");
  const rootRef = useRef<HTMLDivElement | null>(null);

  const selectedOption = useMemo(
    () => options.find((option) => matchesOption(option, value)) ?? null,
    [options, value],
  );

  const displayValue = useMemo(() => {
    if (allowCustom) {
      return value;
    }
    return selectedOption?.label ?? "";
  }, [allowCustom, selectedOption?.label, value]);

  useEffect(() => {
    if (!open) {
      setInputValue(displayValue);
    }
  }, [displayValue, open]);

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

  const normalizedInput = normalize(inputValue);
  const exactMatchExists = useMemo(
    () => options.some((option) => matchesOption(option, inputValue)),
    [inputValue, options],
  );

  const filteredOptions = useMemo(() => {
    if (normalizedInput.length === 0 || exactMatchExists) {
      return options;
    }
    return options.filter((option) => {
      const valueLabel = `${option.label} ${option.value}`.toLowerCase();
      return valueLabel.includes(normalizedInput);
    });
  }, [exactMatchExists, normalizedInput, options]);

  const trimmedInput = inputValue.trim();
  const showCreateOption = allowCustom && trimmedInput.length > 0 && !exactMatchExists;
  const showDropdown = open && !disabled && (filteredOptions.length > 0 || showCreateOption);

  const handleInputChange = (nextValue: string) => {
    setInputValue(nextValue);
    setOpen(true);

    if (allowCustom) {
      onChange(nextValue);
      return;
    }

    if (nextValue.trim().length === 0) {
      onChange("");
      return;
    }

    const matchedOption = options.find((option) => matchesOption(option, nextValue));
    if (matchedOption) {
      onChange(matchedOption.value);
    }
  };

  return (
    <div ref={rootRef} className="space-y-2">
      <div className="relative">
        <input
          id={id}
          type="text"
          value={inputValue}
          aria-label={ariaLabel}
          disabled={disabled}
          onFocus={() => setOpen(true)}
          onChange={(event) => handleInputChange(event.target.value)}
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
          aria-label={toggleLabel}
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
                  onChange(trimmedInput);
                  setInputValue(trimmedInput);
                  setOpen(false);
                }}
              >
                <span>{`Use "${trimmedInput}"`}</span>
                <span className="text-xs text-foreground/55">{customOptionLabel}</span>
              </button>
            ) : null}
            {filteredOptions.map((option) => {
              const isSelected = selectedOption?.value === option.value;
              return (
                <button
                  key={option.value}
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  className="flex w-full items-center justify-between rounded-md px-3 py-2 text-left text-sm text-foreground hover:bg-muted/60"
                  onClick={() => {
                    onChange(option.value);
                    setInputValue(option.label);
                    setOpen(false);
                  }}
                >
                  <span>{option.label}</span>
                  {isSelected ? <Check className="h-4 w-4 text-blue-600 dark:text-blue-400" /> : null}
                </button>
              );
            })}
          </div>
        ) : null}
      </div>
      {helperText ? <p className="text-xs text-foreground/60">{helperText}</p> : null}
    </div>
  );
}
