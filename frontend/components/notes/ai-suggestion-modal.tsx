"use client";

import { useEffect, useState } from "react";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import {
  applyAiSuggestionSelection,
  mergeNoteTags,
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

function ChoiceButton({
  selected,
  label,
  onClick,
  disabled = false,
}: Readonly<{
  selected: boolean;
  label: string;
  onClick: () => void;
  disabled?: boolean;
}>) {
  return (
    <Button
      type="button"
      variant={selected ? "default" : "outline"}
      size="sm"
      onClick={onClick}
      disabled={disabled}
      className="justify-start"
    >
      {label}
    </Button>
  );
}

function formatValue(value: string | null | undefined, fallback: string) {
  return value && value.trim().length > 0 ? value : fallback;
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

  useEffect(() => {
    if (!open) {
      return;
    }
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
  const mergedTagsPreview = mergeNoteTags(currentTags, suggestedTags);
  const hasSuggestedSubject = Boolean(suggestedSubject && suggestedSubject.trim().length > 0);
  const hasSuggestedTags = suggestedTags.length > 0;

  return (
    <AppModal
      isOpen={open}
      title="AI Suggestions"
      description="Choose which AI suggestions to apply. Your note metadata will only change for the fields you select."
      onClose={onSkip}
      panelClassName="max-w-2xl"
      actions={(
        <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" onClick={onSkip} disabled={applying}>
            Skip
          </Button>
          <Button
            type="button"
            onClick={() => onApply(selection)}
            disabled={applying}
          >
            {applying ? "Applying..." : "Apply Selected Changes"}
          </Button>
        </div>
      )}
    >
      <div className="space-y-4 text-sm text-foreground/85">
        <section className="space-y-3 rounded-xl border border-border p-4">
          <div className="space-y-1">
            <h3 className="font-semibold text-foreground">Title</h3>
            <p className="text-xs text-foreground/65">Current: {formatValue(currentTitle, "No title yet.")}</p>
            <p className="text-xs text-foreground/65">AI: {formatValue(suggestedTitle, "No title suggested.")}</p>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <ChoiceButton
              selected={selection.titleChoice === "keep"}
              label="Keep My Title"
              onClick={() => setSelection((current) => ({ ...current, titleChoice: "keep" }))}
            />
            <ChoiceButton
              selected={selection.titleChoice === "use-ai"}
              label="Use AI Title"
              onClick={() => setSelection((current) => ({ ...current, titleChoice: "use-ai" }))}
            />
          </div>
        </section>

        <section className="space-y-3 rounded-xl border border-border p-4">
          <div className="space-y-1">
            <h3 className="font-semibold text-foreground">Subject</h3>
            <p className="text-xs text-foreground/65">Current: {formatValue(currentSubject, "No subject yet.")}</p>
            <p className="text-xs text-foreground/65">AI: {formatValue(suggestedSubject, "No subject suggested.")}</p>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <ChoiceButton
              selected={selection.subjectChoice === "keep"}
              label="Keep My Subject"
              onClick={() => setSelection((current) => ({ ...current, subjectChoice: "keep" }))}
            />
            <ChoiceButton
              selected={selection.subjectChoice === "use-ai"}
              label="Use AI Subject"
              onClick={() => setSelection((current) => ({ ...current, subjectChoice: "use-ai" }))}
              disabled={!hasSuggestedSubject}
            />
          </div>
        </section>

        <section className="space-y-3 rounded-xl border border-border p-4">
          <div className="space-y-1">
            <h3 className="font-semibold text-foreground">Tags</h3>
            <p className="text-xs text-foreground/65">
              Current: {currentTags.length > 0 ? currentTags.join(", ") : "No tags yet."}
            </p>
            <p className="text-xs text-foreground/65">
              AI: {hasSuggestedTags ? suggestedTags.join(", ") : "No tags suggested."}
            </p>
          </div>
          <div className="flex flex-col gap-2">
            <ChoiceButton
              selected={selection.tagsChoice === "keep"}
              label="Keep My Tags"
              onClick={() => setSelection((current) => ({ ...current, tagsChoice: "keep" }))}
            />
            <ChoiceButton
              selected={selection.tagsChoice === "merge"}
              label={`Merge Tags${mergedTagsPreview.length > 0 ? ` (${mergedTagsPreview.join(", ")})` : ""}`}
              onClick={() => setSelection((current) => ({ ...current, tagsChoice: "merge" }))}
              disabled={!hasSuggestedTags}
            />
            <ChoiceButton
              selected={selection.tagsChoice === "use-ai"}
              label="Use AI Tags"
              onClick={() => setSelection((current) => ({ ...current, tagsChoice: "use-ai" }))}
              disabled={!hasSuggestedTags}
            />
          </div>
        </section>

        <section className="rounded-xl border border-border bg-muted/30 p-4">
          <h3 className="font-semibold text-foreground">Preview</h3>
          <div className="mt-2 space-y-1 text-xs text-foreground/70">
            <p>Title: {formatValue(preview.title, "No title")}</p>
            <p>Subject: {formatValue(preview.subject, "No subject")}</p>
            <p>Tags: {preview.tags.length > 0 ? preview.tags.join(", ") : "No tags"}</p>
          </div>
        </section>
      </div>
    </AppModal>
  );
}
