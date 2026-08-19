"use client";

import type { LearnerLevel } from "@/lib/api";
import { SuggestionCombobox } from "@/components/ui/suggestion-combobox";
import { getCourseProgramHelperText, type CourseProgramFieldContext } from "@/lib/learning-profile";

type CourseProgramComboboxProps = {
  id: string;
  value: string;
  suggestions: string[];
  onChange: (value: string) => void;
  onInputValueChange?: (value: string) => void;
  learnerLevel?: LearnerLevel | "" | null;
  ariaLabel?: string;
  placeholder?: string;
  disabled?: boolean;
  context?: CourseProgramFieldContext;
  errorText?: string | null;
  allowCustom?: boolean;
  inlineDropdown?: boolean;
  /**
   * Pass null to suppress the default helper text. The default says "Choose or type", which is wrong
   * wherever `allowCustom` is false — and a caller that already explains the field (the curator
   * multi-select) should not stack a third helper under one input.
   */
  helperText?: string | null;
};

export function CourseProgramCombobox({
  id,
  value,
  suggestions,
  onChange,
  onInputValueChange,
  learnerLevel = null,
  ariaLabel,
  placeholder,
  disabled = false,
  context = "profile",
  errorText = null,
  allowCustom = true,
  inlineDropdown = false,
  helperText,
}: Readonly<CourseProgramComboboxProps>) {
  const resolvedHelperText = helperText === undefined
    ? getCourseProgramHelperText(learnerLevel, context)
    : helperText;
  // L1: a caller's placeholder wins in BOTH modes. The default previously lived on the prop, so
  // `placeholder` was never undefined and the allowCustom={false} branch overwrote whatever the caller
  // passed -- silently discarding states like "Loading course programs...", which a caller supplies
  // precisely because it knows something the component does not.
  const resolvedPlaceholder = placeholder
    ?? (allowCustom ? "Choose or type a course/program" : "Choose a course/program");
  return (
    <div className="space-y-2">
      <SuggestionCombobox
        id={id}
        value={value}
        options={suggestions.map((courseProgram) => ({ value: courseProgram, label: courseProgram }))}
        onChange={(nextValue) => onChange(nextValue.slice(0, 120))}
        onInputValueChange={(nextValue) => onInputValueChange?.(nextValue.slice(0, 120))}
        ariaLabel={ariaLabel}
        placeholder={resolvedPlaceholder}
        disabled={disabled}
        helperText={resolvedHelperText ?? undefined}
        allowCustom={allowCustom}
        toggleLabel="Toggle course program suggestions"
        customOptionLabel="Custom"
        inlineDropdown={inlineDropdown}
        maxLength={120}
      />
      {errorText ? <p className="text-xs text-red-600 dark:text-red-400">{errorText}</p> : null}
    </div>
  );
}
