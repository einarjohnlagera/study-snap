"use client";

import { SuggestionCombobox } from "@/components/ui/suggestion-combobox";

type SubjectComboboxProps = {
  id: string;
  value: string;
  suggestions: string[];
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  helperText?: string;
  maxLength?: number;
};

export function SubjectCombobox({
  id,
  value,
  suggestions,
  onChange,
  placeholder = "Choose or type a subject",
  disabled = false,
  helperText = "Select an existing subject or type your own.",
  maxLength,
}: Readonly<SubjectComboboxProps>) {
  return (
    <SuggestionCombobox
      id={id}
      value={value}
      options={suggestions.map((subject) => ({ value: subject, label: subject }))}
      onChange={onChange}
      placeholder={placeholder}
      disabled={disabled}
      helperText={helperText}
      maxLength={maxLength}
      allowCustom
      toggleLabel="Toggle subject suggestions"
      customOptionLabel="Custom"
    />
  );
}
