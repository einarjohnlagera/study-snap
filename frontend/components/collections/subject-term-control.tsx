"use client";

import { useMemo, useState } from "react";
import { SuggestionCombobox } from "@/components/ui/suggestion-combobox";
import {
  TERM_LABEL_MAX_LENGTH,
  TERM_NOT_SPECIFIED_LABEL,
  isReservedTermLabel,
  resolveTermEntry,
  type ResolvedTerm,
  type TermOption,
} from "@/lib/collection-terms";

type SubjectTermControlProps = {
  subjectId: string;
  subjectTitle: string;
  termLabel: string | null | undefined;
  termOrder: number | null | undefined;
  /** Terms already used in this Year. Picking one reuses its order; a new label gets the next order. */
  options: TermOption[];
  disabled: boolean;
  /** `null` clears the term. Only called when the resolved placement differs from the current one. */
  onCommit: (subjectId: string, term: ResolvedTerm | null) => void;
};

/**
 * Curator control for a Subject's Academic Term: a combobox over the terms already used in the Year
 * (never raw freetext), which also accepts a new term. An option click commits immediately; typing
 * commits once, when focus leaves the control, so a half-typed "First Sem" is never saved as a term.
 * The curator never types an order: the resolver assigns it.
 */
export function SubjectTermControl({
  subjectId,
  subjectTitle,
  termLabel,
  termOrder,
  options,
  disabled,
  onCommit,
}: Readonly<SubjectTermControlProps>) {
  const currentLabel = termLabel?.trim() ?? "";
  // `null` means "not editing": the field shows the saved term straight from props, so a rollback or a
  // refresh is reflected without an effect. Typing sets a draft that lives until focus leaves.
  const [draft, setDraft] = useState<string | null>(null);
  const shownValue = draft ?? currentLabel;
  const [rejection, setRejection] = useState<string | null>(null);

  const comboboxOptions = useMemo(
    () => options.map((option) => ({ value: option.label, label: option.label })),
    [options],
  );

  const commit = (entry: string) => {
    if (isReservedTermLabel(entry)) {
      // Visible rejection, never a silent clear: the reserved heading must not become a stored term.
      setDraft(null);
      setRejection(`"${TERM_NOT_SPECIFIED_LABEL}" is reserved for subjects with no term. Choose another name.`);
      return;
    }
    setRejection(null);
    const resolved = resolveTermEntry(entry, options);
    setDraft(null);
    const unchanged = resolved === null
      ? currentLabel === ""
      : resolved.termLabel === currentLabel && resolved.termOrder === (termOrder ?? null);
    if (!unchanged) {
      onCommit(subjectId, resolved);
    }
  };

  return (
    <div
      className="w-full max-w-xs space-y-1.5"
      // ⚠️ Keep focus in the input while the pointer is down on the dropdown. Without this, a browser that
      // does not focus buttons on click (Safari) blurs the input first, and the blur commit would save the
      // half-typed text as a NEW term before the option click lands.
      onMouseDown={(event) => {
        if ((event.target as HTMLElement).closest('[role="listbox"]')) {
          event.preventDefault();
        }
      }}
      onBlur={(event) => {
        if (event.currentTarget.contains(event.relatedTarget as Node | null)) {
          return;
        }
        commit(shownValue);
      }}
    >
      <span className="text-xs font-medium uppercase tracking-wide text-foreground/50">Term</span>
      <SuggestionCombobox
        id={`builder-term-${subjectId}`}
        value={shownValue}
        options={comboboxOptions}
        ariaLabel={`Term for ${subjectTitle}`}
        toggleLabel={`Toggle term suggestions for ${subjectTitle}`}
        placeholder="Choose or add a term"
        disabled={disabled}
        maxLength={TERM_LABEL_MAX_LENGTH}
        onChange={setDraft}
        onOptionSelect={commit}
      />
      {rejection ? <p role="alert" className="text-xs text-red-700 dark:text-red-300">{rejection}</p> : null}
    </div>
  );
}
