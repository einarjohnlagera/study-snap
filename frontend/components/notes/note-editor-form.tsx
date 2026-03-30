"use client";

import { useEffect, useRef, useState } from "react";
import { AlertCircle, CheckCircle2, FileText, Loader2, Sparkles, Tag, UploadCloud } from "lucide-react";
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
  importFile: File | null;
  importFileInputKey: number;
  importFlowState: "idle" | "uploading" | "extracting" | "success" | "failure";
  importStatusMessage: string | null;
  importReviewMessage?: string | null;
  onImportFileChange: (file: File | null) => void;
  disableContentEditing?: boolean;
  contentLockHint?: string | null;
  disableGenerateAction?: boolean;
  firstStudyHintVisible?: boolean;
  onDismissFirstStudyHint?: () => void;
  autoFocusContent?: boolean;
  autoFocusImport?: boolean;
  importPanelHighlighted?: boolean;
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
  importFile,
  importFileInputKey,
  importFlowState,
  importStatusMessage,
  importReviewMessage,
  onImportFileChange,
  disableContentEditing = false,
  contentLockHint = null,
  disableGenerateAction = false,
  firstStudyHintVisible = false,
  onDismissFirstStudyHint,
  autoFocusContent = false,
  autoFocusImport = false,
  importPanelHighlighted = false,
}: NoteEditorFormProps) {
  const [tagDraft, setTagDraft] = useState("");
  const [addingTag, setAddingTag] = useState(false);
  const contentRef = useRef<HTMLTextAreaElement | null>(null);
  const importInputRef = useRef<HTMLInputElement | null>(null);
  const contentEmpty = note.content.trim().length === 0;
  const actionsDisabled = contentEmpty || isSaving || isGenerating;
  const importInFlight = importFlowState === "uploading" || importFlowState === "extracting";
  const ImportStatusIcon = importFlowState === "failure"
    ? AlertCircle
    : importFlowState === "success"
      ? CheckCircle2
      : Loader2;
  const importStatusTone = importFlowState === "failure"
    ? "border-red-500/40 bg-red-50/70 text-red-800 dark:bg-red-950/30 dark:text-red-200"
    : importFlowState === "success"
      ? "border-emerald-500/40 bg-emerald-50/70 text-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-200"
      : "border-blue-500/40 bg-blue-50/70 text-blue-800 dark:bg-blue-950/30 dark:text-blue-200";

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

  useEffect(() => {
    if (!autoFocusContent || disableContentEditing) {
      return;
    }
    contentRef.current?.focus();
  }, [autoFocusContent, disableContentEditing]);

  useEffect(() => {
    if (!autoFocusImport || disableContentEditing) {
      return;
    }
    importInputRef.current?.focus();
  }, [autoFocusImport, disableContentEditing, importFileInputKey]);

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
                disabled={actionsDisabled || disableGenerateAction}
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

        {firstStudyHintVisible ? (
          <div className="rounded-lg border border-blue-500/30 bg-blue-50/80 p-4 text-sm text-blue-950 dark:bg-blue-950/30 dark:text-blue-100">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
              <div className="space-y-1">
                <p className="font-semibold">Step 1: Add your notes here.</p>
                <p>You can type, paste, or upload a file or image.</p>
              </div>
              {onDismissFirstStudyHint ? (
                <Button type="button" variant="outline" size="sm" onClick={onDismissFirstStudyHint}>
                  Skip guide
                </Button>
              ) : null}
            </div>
          </div>
        ) : null}

        <section className="space-y-2">
          <label htmlFor="note-content" className="text-sm font-medium text-foreground">Content</label>
          <textarea
            ref={contentRef}
            id="note-content"
            value={note.content}
            onChange={(event) => onContentChange(event.target.value)}
            placeholder="Write or paste your notes here..."
            readOnly={disableContentEditing}
            className={`min-h-[340px] w-full rounded-lg border border-border bg-background px-4 py-3 text-sm leading-relaxed text-foreground outline-none transition-colors placeholder:text-foreground/45 focus-visible:ring-2 focus-visible:ring-blue-600 sm:text-base ${
              disableContentEditing ? "cursor-not-allowed bg-muted/30 text-foreground/80" : ""
            } ${
              firstStudyHintVisible ? "border-blue-500 ring-2 ring-blue-500/30" : ""
            }`}
          />
          {disableContentEditing ? (
            <p className="text-xs text-foreground/60">
              {contentLockHint ?? "Note content is locked after generating a Study Pack. Make a copy to change the note itself."}
            </p>
          ) : (
            <>
              {importReviewMessage ? (
                <div className="rounded-md border border-amber-500/40 bg-amber-50/70 px-3 py-2 text-sm text-amber-900 dark:bg-amber-950/30 dark:text-amber-100">
                  <div className="flex items-start gap-2">
                    <AlertCircle className="mt-0.5 h-4 w-4" aria-hidden="true" />
                    <p>{importReviewMessage}</p>
                  </div>
                </div>
              ) : null}
              <p className="text-xs text-foreground/60">
                Keep this note focused on one topic for better Study Pack quality.
              </p>
            </>
          )}
        </section>

        <section className={`space-y-4 rounded-lg border border-dashed border-border/70 bg-muted/20 p-4 ${
          firstStudyHintVisible || importPanelHighlighted ? "border-blue-500/60 ring-2 ring-blue-500/20" : ""
        }`}>
          <div className="space-y-1">
            <p className="text-sm font-medium text-foreground">Import Notes</p>
            <p className="text-xs text-foreground/65">
              Upload an image or file to extract text into your notes, then review and edit it in Content.
            </p>
          </div>
          <div className="space-y-2 rounded-lg border border-border bg-background p-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Upload image or file</p>
            <div className="flex items-center gap-3 rounded-lg border border-border bg-background px-3 py-2">
              <UploadCloud className="h-4 w-4 text-foreground/60" />
              <FileText className="h-4 w-4 text-foreground/60" />
              <input
                ref={importInputRef}
                key={importFileInputKey}
                id="note-import-file"
                type="file"
                accept="image/png,image/jpeg,image/webp,.txt,.pdf,.docx,text/plain,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                disabled={disableContentEditing}
                onChange={(event) => {
                  const file = event.target.files?.[0] ?? null;
                  onImportFileChange(file);
                }}
                className="w-full cursor-pointer text-sm text-foreground/75 file:mr-3 file:cursor-pointer file:rounded-lg file:border-0 file:bg-blue-600 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-white hover:file:bg-blue-700"
              />
            </div>
          </div>
          {importFile ? (
            <p className="text-xs text-foreground/65">
              Selected file: {importFile.name} ({(importFile.size / (1024 * 1024)).toFixed(2)} MB)
            </p>
          ) : null}
          {importStatusMessage ? (
            <div className={`rounded-md border px-3 py-2 text-sm ${importStatusTone}`}>
              <div className="flex items-start gap-2">
                <ImportStatusIcon
                  className={`mt-0.5 h-4 w-4 ${importInFlight ? "animate-spin" : ""}`}
                  aria-hidden="true"
                />
                <p>{importStatusMessage}</p>
              </div>
            </div>
          ) : null}
          <p className="text-xs text-foreground/60">
            Supported formats: PNG, JPG, JPEG, WEBP, TXT, PDF, DOCX.
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
