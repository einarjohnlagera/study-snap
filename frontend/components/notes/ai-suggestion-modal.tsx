"use client";

import { useEffect, useId, useState } from "react";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import {
  applyAiSuggestionSelection,
  partitionAiSuggestedTags,
  resolveAiSuggestionSelectionDefaults,
  type AiSuggestionSelection,
} from "@/lib/note-metadata";

type AiSuggestionModalProps = {
  open: boolean;
  currentTitle: string;
  currentSubject: string | null;
  currentTags: string[];
  suggestedTitle: string;
  suggestedSubject: string | null;
  suggestedTags: string[];
  applying: boolean;
  onApply: (selection: AiSuggestionSelection) => void;
  onSkip: () => void;
};

function formatValue(value: string | null | undefined, fallback: string) {
  return value && value.trim().length > 0 ? value : fallback;
}

function TagChips({
  tags,
  emptyLabel,
}: Readonly<{
  tags: string[];
  emptyLabel: string;
}>) {
  if (tags.length === 0) {
    return <p className="text-xs text-foreground/55">{emptyLabel}</p>;
  }

  return (
    <div className="flex flex-wrap gap-2">
      {tags.map((tag) => (
        <span
          key={tag}
          className="inline-flex items-center rounded-full border border-border bg-muted/35 px-2.5 py-1 text-xs font-medium text-foreground/80"
        >
          {tag}
        </span>
      ))}
    </div>
  );
}

function SuggestedTagSummary({
  newTags,
  overlappingTags,
}: Readonly<{
  newTags: string[];
  overlappingTags: string[];
}>) {
  return (
    <div className="space-y-3">
      {newTags.length > 0 ? (
        <TagChips tags={newTags} emptyLabel="No new tag suggestions." />
      ) : (
        <p className="text-xs text-foreground/55">No new tag suggestions.</p>
      )}
      {overlappingTags.length > 0 ? (
        <div className="space-y-2">
          <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">Already on your note</p>
          <TagChips tags={overlappingTags} emptyLabel="" />
        </div>
      ) : null}
    </div>
  );
}

function ComparisonBlock({
  label,
  value,
}: Readonly<{
  label: string;
  value: React.ReactNode;
}>) {
  return (
    <div className="space-y-2 rounded-xl border border-border bg-muted/20 p-3">
      <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">{label}</p>
      <div className="text-sm text-foreground/85">{value}</div>
    </div>
  );
}

function RadioOption({
  name,
  label,
  checked,
  onChange,
  disabled = false,
}: Readonly<{
  name: string;
  label: string;
  checked: boolean;
  onChange: () => void;
  disabled?: boolean;
}>) {
  return (
    <label
      className={`flex items-start gap-3 rounded-xl border px-3 py-2.5 text-sm transition-colors ${
        checked
          ? "border-blue-500 bg-blue-500/10 text-foreground"
          : "border-border bg-background text-foreground/85"
      } ${disabled ? "cursor-not-allowed opacity-55" : "cursor-pointer hover:border-blue-300 hover:bg-highlight active:bg-highlight-strong"}`}
    >
      <input
        type="radio"
        name={name}
        checked={checked}
        onChange={onChange}
        disabled={disabled}
        className="mt-0.5 h-4 w-4 border-border text-blue-600"
      />
      <span className="leading-relaxed">{label}</span>
    </label>
  );
}

function SuggestionSection({
  title,
  currentLabel,
  currentValue,
  suggestedLabel,
  suggestedValue,
  children,
}: Readonly<{
  title: string;
  currentLabel: string;
  currentValue: React.ReactNode;
  suggestedLabel: string;
  suggestedValue: React.ReactNode;
  children: React.ReactNode;
}>) {
  return (
    <section className="space-y-4 rounded-2xl border border-border p-4">
      <div className="space-y-1">
        <h3 className="text-sm font-semibold text-foreground">{title}</h3>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <ComparisonBlock label={currentLabel} value={currentValue} />
        <ComparisonBlock label={suggestedLabel} value={suggestedValue} />
      </div>
      <div className="space-y-2">{children}</div>
    </section>
  );
}

export function AiSuggestionModal({
  open,
  currentTitle,
  currentSubject,
  currentTags,
  suggestedTitle,
  suggestedSubject,
  suggestedTags,
  applying,
  onApply,
  onSkip,
}: Readonly<AiSuggestionModalProps>) {
  const [selection, setSelection] = useState<AiSuggestionSelection>(() => resolveAiSuggestionSelectionDefaults(
    { title: currentTitle, subject: currentSubject, tags: currentTags },
    { title: suggestedTitle, subject: suggestedSubject, tags: suggestedTags },
  ));

  const groupBaseId = useId();

  useEffect(() => {
    if (!open) {
      return;
    }
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setSelection(resolveAiSuggestionSelectionDefaults(
      { title: currentTitle, subject: currentSubject, tags: currentTags },
      { title: suggestedTitle, subject: suggestedSubject, tags: suggestedTags },
    ));
  }, [currentSubject, currentTags, currentTitle, open, suggestedSubject, suggestedTags, suggestedTitle]);

  if (!open) {
    return null;
  }

  const preview = applyAiSuggestionSelection(
    { title: currentTitle, subject: currentSubject, tags: currentTags },
    { title: suggestedTitle, subject: suggestedSubject, tags: suggestedTags },
    selection,
  );
  const suggestedTagPartition = partitionAiSuggestedTags(currentTags, suggestedTags);
  const hasSuggestedSubject = Boolean(suggestedSubject && suggestedSubject.trim().length > 0);
  const hasSuggestedTags = suggestedTags.length > 0;
  const hasNewSuggestedTags = suggestedTagPartition.newTags.length > 0;

  return (
    <AppModal
      isOpen={open}
      title="AI Suggestions"
      description="Review each suggestion, compare it with your note, and apply only the changes you want."
      onClose={onSkip}
      panelClassName="flex h-[100dvh] max-h-[100dvh] w-full max-w-none flex-col overflow-hidden rounded-none border-0 p-0 sm:h-auto sm:max-h-[80vh] sm:max-w-[640px] sm:rounded-2xl sm:border"
      headerClassName="border-b border-border px-4 pt-4 pb-3 sm:px-6 sm:pt-6 sm:pb-4"
      contentClassName="mt-0 flex-1 min-h-0 overflow-y-auto px-4 py-4 sm:px-6 sm:py-5"
      actionsClassName="mt-0 shrink-0 border-t border-border bg-background/95 p-4 backdrop-blur sm:px-6"
      actions={(
        <div className="flex gap-3">
          <Button type="button" variant="outline" onClick={onSkip} disabled={applying} className="flex-1 sm:flex-none">
            Skip
          </Button>
          <Button
            type="button"
            onClick={() => onApply(selection)}
            disabled={applying}
            className="flex-1 sm:flex-none"
          >
            {applying ? "Applying..." : "Apply Changes"}
          </Button>
        </div>
      )}
    >
      <div className="space-y-4 text-foreground/85">
        <SuggestionSection
          title="Title"
          currentLabel="Your Title"
          currentValue={formatValue(currentTitle, "No title")}
          suggestedLabel="AI Title"
          suggestedValue={formatValue(suggestedTitle, "No title")}
        >
          <fieldset className="space-y-2">
            <legend className="sr-only">Choose which title to keep</legend>
            <RadioOption
              name={`${groupBaseId}-title`}
              label="Keep My Title"
              checked={selection.titleChoice === "keep"}
              onChange={() => setSelection((current) => ({ ...current, titleChoice: "keep" }))}
            />
            <RadioOption
              name={`${groupBaseId}-title`}
              label="Use AI Title"
              checked={selection.titleChoice === "use-ai"}
              onChange={() => setSelection((current) => ({ ...current, titleChoice: "use-ai" }))}
            />
          </fieldset>
        </SuggestionSection>

        <SuggestionSection
          title="Subject"
          currentLabel="Your Subject"
          currentValue={formatValue(currentSubject, "No subject")}
          suggestedLabel="AI Subject"
          suggestedValue={formatValue(suggestedSubject, "No subject")}
        >
          <fieldset className="space-y-2">
            <legend className="sr-only">Choose which subject to keep</legend>
            <RadioOption
              name={`${groupBaseId}-subject`}
              label="Keep My Subject"
              checked={selection.subjectChoice === "keep"}
              onChange={() => setSelection((current) => ({ ...current, subjectChoice: "keep" }))}
            />
            <RadioOption
              name={`${groupBaseId}-subject`}
              label="Use AI Subject"
              checked={selection.subjectChoice === "use-ai"}
              onChange={() => setSelection((current) => ({ ...current, subjectChoice: "use-ai" }))}
              disabled={!hasSuggestedSubject}
            />
          </fieldset>
        </SuggestionSection>

        <SuggestionSection
          title="Tags"
          currentLabel="Your Tags"
          currentValue={<TagChips tags={currentTags} emptyLabel="No tags" />}
          suggestedLabel="AI Tags"
          suggestedValue={(
            <SuggestedTagSummary
              newTags={suggestedTagPartition.newTags}
              overlappingTags={suggestedTagPartition.overlappingTags}
            />
          )}
        >
          <fieldset className="space-y-2">
            <legend className="sr-only">Choose how to apply tags</legend>
            <RadioOption
              name={`${groupBaseId}-tags`}
              label="Keep My Tags"
              checked={selection.tagsChoice === "keep"}
              onChange={() => setSelection((current) => ({ ...current, tagsChoice: "keep" }))}
            />
            <RadioOption
              name={`${groupBaseId}-tags`}
              label="Merge My Tags + AI Tags"
              checked={selection.tagsChoice === "merge"}
              onChange={() => setSelection((current) => ({ ...current, tagsChoice: "merge" }))}
              disabled={!hasNewSuggestedTags}
            />
            <RadioOption
              name={`${groupBaseId}-tags`}
              label="Use AI Tags Only"
              checked={selection.tagsChoice === "use-ai"}
              onChange={() => setSelection((current) => ({ ...current, tagsChoice: "use-ai" }))}
              disabled={!hasSuggestedTags}
            />
          </fieldset>
        </SuggestionSection>

        <section className="space-y-3 rounded-2xl border border-border bg-muted/25 p-4">
          <div className="space-y-1">
            <h3 className="text-sm font-semibold text-foreground">Preview</h3>
            <p className="text-xs text-foreground/60">This is what will be saved if you apply the current selections.</p>
          </div>
          <div className="space-y-3">
            <div className="space-y-1">
              <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">Title</p>
              <p className="text-sm text-foreground/85">{formatValue(preview.title, "No title")}</p>
            </div>
            <div className="space-y-1">
              <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">Subject</p>
              <p className="text-sm text-foreground/85">{formatValue(preview.subject, "No subject")}</p>
            </div>
            <div className="space-y-2">
              <p className="text-[11px] font-semibold uppercase tracking-wide text-foreground/55">Tags</p>
              <TagChips tags={preview.tags} emptyLabel="No tags" />
            </div>
          </div>
        </section>
      </div>
    </AppModal>
  );
}
