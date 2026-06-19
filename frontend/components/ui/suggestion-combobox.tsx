"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Check, ChevronDown, X } from "lucide-react";

export type SuggestionComboboxOption = {
  value: string;
  label: string;
};

export type SuggestionComboboxOptionGroup = {
  label: string;
  options: SuggestionComboboxOption[];
};

type SuggestionComboboxProps = {
  id: string;
  value: string;
  options: SuggestionComboboxOption[];
  groupedOptions?: SuggestionComboboxOptionGroup[];
  onChange: (value: string) => void;
  ariaLabel?: string;
  placeholder?: string;
  disabled?: boolean;
  helperText?: string;
  allowCustom?: boolean;
  toggleLabel?: string;
  customOptionLabel?: string;
  inlineDropdown?: boolean;
};

function normalize(value: string): string {
  return value.trim().toLowerCase();
}

function matchesOption(option: SuggestionComboboxOption, value: string): boolean {
  const normalizedValue = normalize(value);
  return normalize(option.value) === normalizedValue || normalize(option.label) === normalizedValue;
}

function optionSearchText(option: SuggestionComboboxOption): string {
  return `${normalize(option.label)} ${normalize(option.value)}`.trim();
}

export function SuggestionCombobox({
  id,
  value,
  options,
  groupedOptions,
  onChange,
  ariaLabel,
  placeholder,
  disabled = false,
  helperText,
  allowCustom = true,
  toggleLabel = "Toggle suggestions",
  customOptionLabel = "Custom",
  inlineDropdown = false,
}: Readonly<SuggestionComboboxProps>) {
  const [open, setOpen] = useState(false);
  const [inputValue, setInputValue] = useState("");
  const [hasTypedSinceOpen, setHasTypedSinceOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);

  const effectiveOptions = useMemo(
    () => groupedOptions ? groupedOptions.flatMap((group) => group.options) : options,
    [groupedOptions, options],
  );

  const selectedOption = useMemo(
    () => effectiveOptions.find((option) => matchesOption(option, value)) ?? null,
    [effectiveOptions, value],
  );

  const displayValue = useMemo(() => {
    if (allowCustom) {
      return value;
    }
    return selectedOption?.label ?? "";
  }, [allowCustom, selectedOption?.label, value]);

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

  const activeInputValue = open ? inputValue : displayValue;
  const normalizedInput = normalize(activeInputValue);
  const exactMatch = useMemo(
    () => effectiveOptions.find((option) => matchesOption(option, activeInputValue)) ?? null,
    [activeInputValue, effectiveOptions],
  );

  const filteredOptions = useMemo(() => {
    if (!hasTypedSinceOpen || normalizedInput.length === 0) {
      return effectiveOptions;
    }
    return effectiveOptions
      .map((option, index) => {
        const normalizedLabel = normalize(option.label);
        const normalizedValue = normalize(option.value);
        const searchableText = optionSearchText(option);

        let rank = Number.POSITIVE_INFINITY;
        if (normalizedLabel === normalizedInput || normalizedValue === normalizedInput) {
          rank = 0;
        } else if (normalizedLabel.startsWith(normalizedInput) || normalizedValue.startsWith(normalizedInput)) {
          rank = 1;
        } else if (searchableText.includes(normalizedInput)) {
          rank = 2;
        }

        return {
          option,
          index,
          rank,
        };
      })
      .filter((entry) => Number.isFinite(entry.rank))
      .sort((left, right) => {
        if (left.rank !== right.rank) {
          return left.rank - right.rank;
        }
        return left.index - right.index;
      })
      .map((entry) => entry.option);
  }, [hasTypedSinceOpen, normalizedInput, effectiveOptions]);

  const trimmedInput = activeInputValue.trim();
  const showCreateOption = allowCustom && trimmedInput.length > 0 && exactMatch === null;
  const showDropdown = open && !disabled && (filteredOptions.length > 0 || showCreateOption);

  const handleInputChange = (nextValue: string) => {
    setInputValue(nextValue);
    setOpen(true);
    setHasTypedSinceOpen(true);

    const matchedOption = effectiveOptions.find((option) => matchesOption(option, nextValue)) ?? null;

    if (allowCustom) {
      onChange(matchedOption?.value ?? nextValue);
      return;
    }

    if (nextValue.trim().length === 0) {
      onChange("");
      return;
    }

    if (matchedOption) {
      onChange(matchedOption.value);
    }
  };

  const handleClear = () => {
    onChange("");
    setInputValue("");
    setHasTypedSinceOpen(false);
    inputRef.current?.focus();
  };

  const showClear = !disabled && activeInputValue.trim().length > 0;

  return (
    <div ref={rootRef} className="space-y-2">
      <div className="relative">
        <input
          id={id}
          ref={inputRef}
          type="text"
          value={activeInputValue}
          aria-label={ariaLabel}
          disabled={disabled}
          onFocus={() => {
            setInputValue(displayValue);
            setOpen(true);
            setHasTypedSinceOpen(false);
          }}
          onChange={(event) => handleInputChange(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Escape") {
              setOpen(false);
            }
          }}
          placeholder={placeholder}
          autoComplete="off"
          aria-autocomplete="list"
          aria-controls={`${id}-options`}
          className={`h-11 w-full rounded-lg border border-border bg-background px-3 ${showClear ? "pr-16" : "pr-10"} text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus-visible:ring-2 focus-visible:ring-blue-600 disabled:cursor-not-allowed disabled:opacity-60`}
        />
        <div className="absolute inset-y-0 right-0 flex items-center">
          {showClear ? (
            <button
              type="button"
              onClick={handleClear}
              aria-label="Clear selection"
              className="flex w-8 items-center justify-center text-foreground/60 transition-colors hover:text-foreground"
            >
              <X className="h-4 w-4" />
            </button>
          ) : null}
          <button
            type="button"
            onClick={() => {
              setOpen((previous) => {
                const nextOpen = !previous;
                if (nextOpen) {
                  setInputValue(displayValue);
                  setHasTypedSinceOpen(false);
                }
                return nextOpen;
              });
            }}
            disabled={disabled}
            aria-label={toggleLabel}
            className="flex w-10 items-center justify-center text-foreground/60 transition-colors hover:text-foreground disabled:cursor-not-allowed disabled:opacity-60"
          >
            <ChevronDown className={`h-4 w-4 transition-transform ${showDropdown ? "rotate-180" : ""}`} />
          </button>
        </div>
        {showDropdown ? (
          <div
            id={`${id}-options`}
            role="listbox"
            className={`motion-dropdown-panel mt-2 max-h-60 w-full overflow-y-auto rounded-lg border border-border bg-background p-1 shadow-lg ${inlineDropdown ? "relative" : "absolute z-30"}`}
          >
            {groupedOptions && !hasTypedSinceOpen
              ? groupedOptions.map((group) => (
                  <div key={group.label}>
                    <p className="px-3 pb-1 pt-2 text-xs font-medium text-foreground/50">{group.label}</p>
                    {group.options.map((option) => {
                      const isSelected = selectedOption?.value === option.value;
                      return (
                        <button
                          key={option.value}
                          type="button"
                          role="option"
                          aria-selected={isSelected}
                          className="motion-lift flex w-full items-center justify-between rounded-md px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-highlight active:bg-highlight-strong"
                          onClick={() => {
                            onChange(option.value);
                            setInputValue(option.label);
                            setOpen(false);
                            setHasTypedSinceOpen(false);
                          }}
                        >
                          <span>{option.label}</span>
                          {isSelected ? <Check className="h-4 w-4 text-blue-600 dark:text-blue-400" /> : null}
                        </button>
                      );
                    })}
                  </div>
                ))
              : filteredOptions.map((option) => {
                  const isSelected = selectedOption?.value === option.value;
                  return (
                    <button
                      key={option.value}
                      type="button"
                      role="option"
                      aria-selected={isSelected}
                      className="motion-lift flex w-full items-center justify-between rounded-md px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-highlight active:bg-highlight-strong"
                      onClick={() => {
                        onChange(option.value);
                        setInputValue(option.label);
                        setOpen(false);
                        setHasTypedSinceOpen(false);
                      }}
                    >
                      <span>{option.label}</span>
                      {isSelected ? <Check className="h-4 w-4 text-blue-600 dark:text-blue-400" /> : null}
                    </button>
                  );
                })}
            {showCreateOption ? (
              <button
                type="button"
                className="flex w-full items-center justify-between rounded-md px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-highlight active:bg-highlight-strong"
                onClick={() => {
                  onChange(trimmedInput);
                  setInputValue(trimmedInput);
                  setOpen(false);
                  setHasTypedSinceOpen(false);
                }}
              >
                <span>{`Use "${trimmedInput}"`}</span>
                <span className="text-xs text-foreground/55">{customOptionLabel}</span>
              </button>
            ) : null}
          </div>
        ) : null}
      </div>
      {helperText ? <p className="text-xs text-foreground/60">{helperText}</p> : null}
    </div>
  );
}
