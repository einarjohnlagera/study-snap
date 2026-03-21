"use client";

import { useState } from "react";
import { Loader2, Sparkles, Tag } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

export type NoteEditorDraft = {
  title: string;
  subject: string;
  content: string;
  tags: string[];
};

type NoteEditorFormProps = {
  pageTitle: string;
  note: NoteEditorDraft;
  onTitleChange: (value: string) => void;
  onSubjectChange: (value: string) => void;
  onContentChange: (value: string) => void;
  onTagsChange?: (nextTags: string[]) => void;
  onSave: () => void;
  onGenerate: () => void;
  isSaving: boolean;
  isGenerating: boolean;
  saveStateLabel?: string | null;
  helperText: string;
  showTagsSection: boolean;
  studyPackMessage?: string | null;
};

function normalizeTagInput(value: string): string | null {
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : null;
}

export function NoteEditorForm({
  pageTitle,
  note,
  onTitleChange,
  onSubjectChange,
  onContentChange,
  onTagsChange,
  onSave,
  onGenerate,
  isSaving,
  isGenerating,
  saveStateLabel,
  helperText,
  showTagsSection,
  studyPackMessage,
}: NoteEditorFormProps) {
  const [tagDraft, setTagDraft] = useState("");
  const [addingTag, setAddingTag] = useState(false);
  const contentEmpty = note.content.trim().length === 0;
  const actionsDisabled = contentEmpty || isSaving || isGenerating;

  const handleAddTag = () => {
    if (!onTagsChange) {
      return;
    }
    const candidate = normalizeTagInput(tagDraft);
    if (!candidate) {
      return;
    }
    const duplicate = note.tags.some((tag) => tag.toLowerCase() === candidate.toLowerCase());
    if (duplicate) {
      setTagDraft("");
      setAddingTag(false);
      return;
    }
    onTagsChange([...note.tags, candidate]);
    setTagDraft("");
    setAddingTag(false);
  };

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-4 py-6 sm:px-6 sm:py-8">
      <header className="space-y-3">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <h1 className="text-2xl font-semibold text-foreground sm:text-3xl">{pageTitle}</h1>
          <div className="space-y-2">
            <div className="flex flex-col gap-2 sm:flex-row">
              <Button
                type="button"
                onClick={onSave}
                disabled={actionsDisabled}
                variant="outline"
                className="w-full sm:w-auto"
              >
                {isSaving ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Saving...
                  </>
                ) : (
                  "Save Note"
                )}
              </Button>
              <Button
                type="button"
                onClick={onGenerate}
                disabled={actionsDisabled}
                className="w-full sm:w-auto"
              >
                {isGenerating ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Generating Study Pack...
                  </>
                ) : (
                  <>
                    <Sparkles className="mr-2 h-4 w-4" />
                    Generate Study Pack (1 credit)
                  </>
                )}
              </Button>
            </div>
            <p className="max-w-xl text-xs text-foreground/70">{helperText}</p>
          </div>
        </div>
        {saveStateLabel ? (
          <p className="text-xs text-foreground/60">{saveStateLabel}</p>
        ) : null}
      </header>

      <Card className="space-y-6 p-4 sm:p-6">
        <section className="space-y-4">
          <div className="space-y-2">
            <label htmlFor="note-title" className="text-sm font-medium text-foreground">Title (optional)</label>
            <input
              id="note-title"
              type="text"
              value={note.title}
              onChange={(event) => onTitleChange(event.target.value)}
              placeholder="Untitled note"
              className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus-visible:ring-2 focus-visible:ring-blue-600"
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <label htmlFor="note-subject" className="text-sm font-medium text-foreground">Subject (optional)</label>
              <input
                id="note-subject"
                type="text"
                value={note.subject}
                onChange={(event) => onSubjectChange(event.target.value)}
                placeholder="Biology"
                className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus-visible:ring-2 focus-visible:ring-blue-600"
              />
            </div>
            {showTagsSection ? (
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <p className="text-sm font-medium text-foreground">Tags</p>
                  {!addingTag ? (
                    <button
                      type="button"
                      onClick={() => setAddingTag(true)}
                      className="text-sm text-blue-600 hover:underline dark:text-blue-400"
                    >
                      + Add Tag
                    </button>
                  ) : null}
                </div>
                {addingTag ? (
                  <div className="flex items-center gap-2">
                    <input
                      type="text"
                      value={tagDraft}
                      onChange={(event) => setTagDraft(event.target.value)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter") {
                          event.preventDefault();
                          handleAddTag();
                        }
                        if (event.key === "Escape") {
                          setAddingTag(false);
                          setTagDraft("");
                        }
                      }}
                      placeholder="Add a tag"
                      className="h-9 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus-visible:ring-2 focus-visible:ring-blue-600"
                    />
                    <Button type="button" size="sm" variant="outline" onClick={handleAddTag}>
                      Add
                    </Button>
                  </div>
                ) : null}
                <div className="flex min-h-9 flex-wrap gap-2">
                  {note.tags.length > 0 ? (
                    note.tags.map((tag) => (
                      <span
                        key={tag}
                        className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-2.5 py-1 text-xs text-foreground/80"
                      >
                        <Tag className="h-3 w-3" />
                        {tag}
                        <button
                          type="button"
                          aria-label={`Remove ${tag}`}
                          className="text-foreground/60 hover:text-foreground"
                          onClick={() => {
                            if (!onTagsChange) {
                              return;
                            }
                            onTagsChange(note.tags.filter((value) => value !== tag));
                          }}
                        >
                          x
                        </button>
                      </span>
                    ))
                  ) : (
                    <p className="text-xs text-foreground/55">No tags yet.</p>
                  )}
                </div>
              </div>
            ) : null}
          </div>
        </section>

        <section className="space-y-2">
          <label htmlFor="note-content" className="text-sm font-medium text-foreground">Content</label>
          <textarea
            id="note-content"
            value={note.content}
            onChange={(event) => onContentChange(event.target.value)}
            placeholder="Write or paste your notes here..."
            className="min-h-[340px] w-full rounded-lg border border-border bg-background px-4 py-3 text-sm leading-relaxed text-foreground outline-none transition-colors placeholder:text-foreground/45 focus-visible:ring-2 focus-visible:ring-blue-600 sm:text-base"
          />
          <p className="text-xs text-foreground/60">
            Keep this note focused on one topic for better Study Pack quality.
          </p>
        </section>
      </Card>

      <Card className="space-y-2 border-border p-4 sm:p-5">
        <p className="text-sm text-foreground/75">
          {studyPackMessage ?? "This note doesn't have a Study Pack yet."}
        </p>
        <p className="text-xs text-foreground/60">
          Generate when you&apos;re ready. To create a new version later, make a copy first.
        </p>
      </Card>
    </main>
  );
}
