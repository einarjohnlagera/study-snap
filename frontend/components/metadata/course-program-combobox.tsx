"use client";

import type { LearnerLevel } from "@/lib/api";
import { SuggestionCombobox } from "@/components/ui/suggestion-combobox";
import { getCourseProgramHelperText, type CourseProgramFieldContext } from "@/lib/learning-profile";

type CourseProgramComboboxProps = {
  id: string;
  value: string;
  suggestions: string[];
  onChange: (value: string) => void;
  learnerLevel?: LearnerLevel | "" | null;
  ariaLabel?: string;
  placeholder?: string;
  disabled?: boolean;
  context?: CourseProgramFieldContext;
  errorText?: string | null;
};

export function CourseProgramCombobox({
  id,
  value,
  suggestions,
  onChange,
  learnerLevel = null,
  ariaLabel,
  placeholder = "Choose or type a course/program",
  disabled = false,
  context = "profile",
  errorText = null,
}: Readonly<CourseProgramComboboxProps>) {
  return (
    <div className="space-y-2">
      <SuggestionCombobox
        id={id}
        value={value}
        options={suggestions.map((courseProgram) => ({ value: courseProgram, label: courseProgram }))}
        onChange={(nextValue) => onChange(nextValue.slice(0, 120))}
        ariaLabel={ariaLabel}
        placeholder={placeholder}
        disabled={disabled}
        helperText={getCourseProgramHelperText(learnerLevel, context)}
        allowCustom
        toggleLabel="Toggle course program suggestions"
        customOptionLabel="Custom"
      />
      {errorText ? <p className="text-xs text-red-600 dark:text-red-400">{errorText}</p> : null}
    </div>
  );
}
