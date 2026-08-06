"use client";

import Link from "next/link";
import {type ReactNode, type RefObject, useEffect, useRef, useState} from "react";
import {
    AlertCircle,
    CheckCircle2,
    ChevronDown,
    Copy,
    FileText,
    Loader2,
    Sparkles,
    Tag,
    UploadCloud
} from "lucide-react";
import type {CourseProgramCatalogItem, DomainContext, LearnerLevel, NoteTargetProfileType} from "@/lib/api";
import {CourseProgramCombobox} from "@/components/metadata/course-program-combobox";
import {ApplicableProgramsCombobox} from "@/components/metadata/applicable-programs-combobox";
import {SubjectCombobox} from "@/components/notes/subject-combobox";
import {BackLink} from "@/components/ui/back-link";
import {Button} from "@/components/ui/button";
import {Card} from "@/components/ui/card";
import {getNoteTargetProfileLabel, SELECTABLE_NOTE_TARGET_PROFILE_TYPES} from "@/lib/note-target-profile";
import { GuidanceTip } from "@/components/ui/guidance-tip";
import { IMPORT_ACCEPT_VALUE } from "@/lib/note-import";
import { DOMAIN_CONTEXT_OPTIONS, isSubjectSameAsDomainContext } from "@/lib/domain-context";
import { LEARNER_LEVEL_OPTIONS } from "@/lib/learning-profile";

const OPTIONAL_DETAILS_SCROLL_DELAY_MS = 140;

export type NoteEditorDraft = {
    title: string;
    subject: string;
    courseProgram: string;
    domainContext: DomainContext | "";
    learnerLevel: LearnerLevel | "";
    targetProfileType: NoteTargetProfileType | "";
    content: string;
    tags: string[];
};

export type NoteEditorEntryOption = "write" | "generate" | "import";

type NoteEditorFormProps = {
    pageTitle: string;
    note: NoteEditorDraft;
    onTitleChange: (value: string) => void;
    onSubjectChange: (value: string) => void;
    onCourseProgramChange: (value: string) => void;
    onDomainContextChange?: (value: DomainContext | "") => void;
    onLearnerLevelChange?: (value: LearnerLevel | "") => void;
    onTargetProfileTypeChange?: (value: NoteTargetProfileType | "") => void;
    onContentChange: (value: string) => void;
    onTagsChange?: (nextTags: string[]) => void;
    onSave: () => void;
    onGenerate: () => void;
    onCancel?: () => void;
    isSaving: boolean;
    isGenerating: boolean;
    isCopying?: boolean;
    saveStateLabel?: string | null;
    helperText: string;
    showTagsSection: boolean;
    studyPackMessage?: string | null;
    formError?: string | null;
    importFile: File | null;
    importFileInputKey: number;
    importFlowState: "idle" | "uploading" | "extracting" | "success" | "failure";
    importStatusMessage: string | null;
    importReviewMessage?: string | null;
    importNotice?: ReactNode;
    onImportFileChange: (file: File | null) => void;
    entryOption?: NoteEditorEntryOption;
    onEntryOptionChange?: (value: NoteEditorEntryOption) => void;
    generateTopic?: string;
    onGenerateTopicChange?: (value: string) => void;
    onGenerateNote?: () => void;
    isGeneratingNote?: boolean;
    disableGenerateNote?: boolean;
    generateNoteLabel?: string;
    generateNoteLoadingLabel?: string;
    generateNoteFooter?: ReactNode;
    showGenerateNoteEntry?: boolean;
    showGenerateNoteTip?: boolean;
    collapseOptionalDetailsByDefault?: boolean;
    revealOptionalDetailsSignal?: number;
    contentSectionRef?: RefObject<HTMLElement | null>;
    contentAnimationKey?: string | number;
    contentStatusText?: string | null;
    disableContentEditing?: boolean;
    contentLockHint?: string | null;
    disableGenerateAction?: boolean;
    firstStudyHintVisible?: boolean;
    onDismissFirstStudyHint?: () => void;
    autoFocusContent?: boolean;
    autoFocusImport?: boolean;
    importPanelHighlighted?: boolean;
    saveLabel?: string;
    cancelLabel?: string;
    actionLabel: string;
    actionHelperText: string;
    actionLoadingLabel: string;
    actionIcon?: "generate" | "copy";
    actionVariant?: "default" | "outline";
    disableAction?: boolean;
    subjectSuggestions?: string[];
    courseProgramSuggestions?: string[];
    resolvedCourseProgram?: string | null;
    showTargetProfileTypeField?: boolean;
    showAuthoringMetadataFields?: boolean;
    applicableProgramCatalog?: CourseProgramCatalogItem[];
    applicableProgramIds?: string[];
    onApplicableProgramIdsChange?: (selectedIds: string[]) => void;
    applicableProgramsLoading?: boolean;
    applicableProgramsError?: string | null;
    onRetryApplicablePrograms?: () => void;
    targetProfileTypeHelperText?: string;
    backHref?: string;
    backLabel?: string;
    isTeacherCreateMode?: boolean;
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
                                   onCourseProgramChange,
                                   onDomainContextChange,
                                   onLearnerLevelChange,
                                   onTargetProfileTypeChange,
                                   onContentChange,
                                   onTagsChange,
                                   onSave,
                                   onGenerate,
                                   isSaving,
                                   isGenerating,
                                   isCopying = false,
                                   saveStateLabel,
                                   helperText,
                                   showTagsSection,
                                   studyPackMessage,
                                   formError,
                                   importFile,
                                   importFileInputKey,
                                   importFlowState,
                                   importStatusMessage,
                                   importReviewMessage,
                                   importNotice = null,
                                   onImportFileChange,
                                   entryOption = "write",
                                   onEntryOptionChange,
                                   generateTopic = "",
                                   onGenerateTopicChange,
                                   onGenerateNote,
                                   isGeneratingNote = false,
                                   disableGenerateNote = false,
                                   generateNoteLabel = "Create a Note",
                                   generateNoteLoadingLabel = "Creating...",
                                   generateNoteFooter = null,
                                   showGenerateNoteEntry = false,
                                   showGenerateNoteTip = false,
                                   collapseOptionalDetailsByDefault = false,
                                   revealOptionalDetailsSignal = 0,
                                   contentSectionRef,
                                   contentAnimationKey,
                                   contentStatusText = null,
                                   disableContentEditing = false,
                                   contentLockHint = null,
                                   disableGenerateAction = false,
                                   firstStudyHintVisible = false,
                                   onDismissFirstStudyHint,
                                   autoFocusContent = false,
                                   autoFocusImport = false,
                                   importPanelHighlighted = false,
                                   saveLabel = "Save",
                                   actionLabel,
                                   actionHelperText,
                                   actionLoadingLabel,
                                   actionIcon = "generate",
                                   actionVariant = "default",
                                   disableAction = false,
                                   subjectSuggestions = [],
                                   courseProgramSuggestions = [],
                                   resolvedCourseProgram = null,
                                   showTargetProfileTypeField = false,
                                   showAuthoringMetadataFields = false,
                                   applicableProgramCatalog = [],
                                   applicableProgramIds = [],
                                   onApplicableProgramIdsChange,
                                   applicableProgramsLoading = false,
                                   applicableProgramsError = null,
                                   onRetryApplicablePrograms,
                                   targetProfileTypeHelperText = "Choose the learner audience for this note.",
                                   backHref,
                                   backLabel,
                                   isTeacherCreateMode = false,
                               }: Readonly<NoteEditorFormProps>) {
    const [tagDraft, setTagDraft] = useState("");
    const [addingTag, setAddingTag] = useState(false);
    const [optionalDetailsOpen, setOptionalDetailsOpen] = useState(!collapseOptionalDetailsByDefault);
    const contentRef = useRef<HTMLTextAreaElement | null>(null);
    const importInputRef = useRef<HTMLInputElement | null>(null);
    const optionalDetailsSectionRef = useRef<HTMLElement | null>(null);
    const contentEmpty = note.content.trim().length === 0;
    const actionsDisabled = contentEmpty || isSaving || isGenerating || isCopying;
    const actionInFlight = isGenerating || isCopying;
    const importInFlight = importFlowState === "uploading" || importFlowState === "extracting";
    const showImportStatusMessage = importStatusMessage && importFlowState !== "success";
    const generateNoteDisabled = disableGenerateNote || isGeneratingNote || isSaving || isGenerating || isCopying;
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

    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setOptionalDetailsOpen(!collapseOptionalDetailsByDefault);
    }, [collapseOptionalDetailsByDefault]);

    useEffect(() => {
        if (revealOptionalDetailsSignal <= 0) {
            return;
        }
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setOptionalDetailsOpen(true);
        const timeoutId = globalThis.setTimeout(() => {
            const optionalDetailsSection = optionalDetailsSectionRef.current;
            if (optionalDetailsSection && typeof optionalDetailsSection.scrollIntoView === "function") {
                optionalDetailsSection.scrollIntoView({
                    behavior: "smooth",
                    block: "start",
                });
            }
        }, OPTIONAL_DETAILS_SCROLL_DELAY_MS);
        return () => {
            globalThis.clearTimeout(timeoutId);
        };
    }, [revealOptionalDetailsSignal]);

    const handleRevealOptionalDetails = () => {
        setOptionalDetailsOpen(true);
        globalThis.setTimeout(() => {
            const optionalDetailsSection = optionalDetailsSectionRef.current;
            if (optionalDetailsSection && typeof optionalDetailsSection.scrollIntoView === "function") {
                optionalDetailsSection.scrollIntoView({
                    behavior: "smooth",
                    block: "start",
                });
            }
        }, OPTIONAL_DETAILS_SCROLL_DELAY_MS);
    };

    const renderSaveButton = (className: string) => (
        <Button
            type="button"
            onClick={onSave}
            disabled={actionsDisabled}
            variant="outline"
            className={className}
        >
            {isSaving ? (
                <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin"/>
                    Saving...
                </>
            ) : (
                saveLabel
            )}
        </Button>
    );

    const renderPrimaryAction = (
        buttonClassName: string,
        containerClassName: string,
        options: { showHelperText?: boolean } = {},
    ) => (
        <div className={containerClassName}>
            <Button
                type="button"
                onClick={onGenerate}
                disabled={(actionIcon === "generate" ? actionsDisabled : isSaving || isGenerating || isCopying) || disableGenerateAction || disableAction}
                variant={actionVariant}
                className={buttonClassName}
            >
                {actionInFlight ? (
                    <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin"/>
                        {actionLoadingLabel}
                    </>
                ) : (
                    <>
                        {actionIcon === "copy" ? <Copy className="mr-2 h-4 w-4"/> :
                            <Sparkles className="mr-2 h-4 w-4"/>}
                        {actionLabel}
                    </>
                )}
            </Button>
            {options.showHelperText !== false && !actionInFlight ? (
                <p className="text-center text-[11px] text-foreground/60 sm:text-right">
                    {actionHelperText}
                </p>
            ) : null}
        </div>
    );

    const renderMetadataFields = () => (
        <div className="space-y-5">
            <div className="space-y-2">
                <label htmlFor="note-title" className="text-sm font-medium text-foreground">Title (optional)</label>
                <input
                    id="note-title"
                    type="text"
                    value={note.title}
                    onChange={(event) => onTitleChange(event.target.value)}
                    placeholder="Untitled note"
                    disabled={isCopying}
                    className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus-visible:ring-2 focus-visible:ring-blue-600"
                />
            </div>

            <div className="space-y-2">
                <label htmlFor="note-subject" className="text-sm font-medium text-foreground">Subject
                    (optional)</label>
                <SubjectCombobox
                    id="note-subject"
                    value={note.subject}
                    suggestions={subjectSuggestions}
                    onChange={onSubjectChange}
                    disabled={isCopying}
                />
                {/* Live region stays mounted so the advisory is announced when it appears, not just rendered. */}
                <div aria-live="polite">
                    {showAuthoringMetadataFields && isSubjectSameAsDomainContext(note.subject, note.domainContext) ? (
                        <p className="text-xs text-amber-700 dark:text-amber-300">
                            Subject matches the Domain Context, so it is probably too broad to be a useful
                            library shelf. Consider a specific subject like Algebra or Pharmacology and let
                            Domain Context carry the domain. Saving anyway is fine.
                        </p>
                    ) : null}
                </div>
            </div>

            {/* Course / Program(s) keeps its original position in the main metadata grid for BOTH
                authoring modes. This is not a new field — it is the same field gaining several values
                for curators — so moving it into the gated fieldset would misfile it and disorient
                returning authors. Only the control differs: learners get one free-text value, curators
                get a catalog-backed multi-select. */}
            <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2 sm:col-span-2">
                    <label
                        htmlFor={showAuthoringMetadataFields ? "note-applicable-programs" : "note-course-program"}
                        className="text-sm font-medium text-foreground"
                    >
                        Course / Program(s) <span className="text-red-500" aria-hidden="true">*</span>
                    </label>
                    {showAuthoringMetadataFields ? (
                        <>
                            <p className="text-xs text-foreground/60">
                                Choose one or more programs this note applies to. Adding multiple programs lets one
                                note serve several curricula instead of creating duplicates.
                            </p>
                            <ApplicableProgramsCombobox
                                id="note-applicable-programs"
                                catalog={applicableProgramCatalog}
                                selectedIds={applicableProgramIds}
                                onChange={(selectedIds) => onApplicableProgramIdsChange?.(selectedIds)}
                                loading={applicableProgramsLoading}
                                error={applicableProgramsError}
                                onRetry={onRetryApplicablePrograms}
                                disabled={isCopying}
                            />
                        </>
                    ) : (
                        <CourseProgramCombobox
                            id="note-course-program"
                            value={note.courseProgram}
                            suggestions={courseProgramSuggestions}
                            onChange={onCourseProgramChange}
                            disabled={isCopying}
                            context="note"
                        />
                    )}
                </div>
            </div>

            {showTargetProfileTypeField || showAuthoringMetadataFields ? (
                <fieldset className="space-y-4 rounded-xl border border-border/80 bg-muted/15 p-4">
                    <legend className="px-1 text-sm font-semibold text-foreground">Generation &amp; discovery</legend>
                    <div className="grid gap-4 sm:grid-cols-2">
                        {showTargetProfileTypeField ? (
                            <div className="space-y-2 sm:col-span-2">
                                <label htmlFor="note-target-profile-type" className="text-sm font-medium text-foreground">
                                    Who is this note for? <span className="text-red-500" aria-hidden="true">*</span>
                                </label>
                                <select
                                    id="note-target-profile-type"
                                    aria-label="Who is this note for?"
                                    value={note.targetProfileType}
                                    onChange={(event) => onTargetProfileTypeChange?.(event.target.value as NoteTargetProfileType | "")}
                                    disabled={isCopying}
                                    className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus-visible:ring-2 focus-visible:ring-blue-600"
                                >
                                    <option value="">Select an audience</option>
                                    {SELECTABLE_NOTE_TARGET_PROFILE_TYPES.map((targetProfileType) => (
                                        <option key={targetProfileType} value={targetProfileType}>
                                            {getNoteTargetProfileLabel(targetProfileType)}
                                        </option>
                                    ))}
                                </select>
                                <p className="text-xs text-foreground/60">{targetProfileTypeHelperText}</p>
                            </div>
                        ) : null}

                        {showAuthoringMetadataFields ? (
                            <>
                                <div className="space-y-2">
                                    <label htmlFor="note-domain-context" className="text-sm font-medium text-foreground">
                                        Domain Context {applicableProgramIds.length > 1 ? <span className="text-red-500" aria-hidden="true">*</span> : "(optional)"}
                                    </label>
                                    <select
                                        id="note-domain-context"
                                        value={note.domainContext}
                                        onChange={(event) => onDomainContextChange?.(event.target.value as DomainContext | "")}
                                        disabled={isCopying}
                                        className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus-visible:ring-2 focus-visible:ring-blue-600"
                                    >
                                        <option value="">Automatic — based on the program</option>
                                        {DOMAIN_CONTEXT_OPTIONS.map((option) => (
                                            <option key={option.value} value={option.value}>{option.label}</option>
                                        ))}
                                    </select>
                                    <p className="text-xs text-foreground/60">
                                        {applicableProgramIds.length > 1
                                            ? "You've added more than one program. Choose the academic domain this note should be written in — it tells the AI how to write it, while the programs decide who finds it."
                                            : "Required when this note applies to more than one program."}
                                    </p>
                                </div>

                                <div className="space-y-2">
                                    <label htmlFor="note-learner-level" className="text-sm font-medium text-foreground">
                                        Note Learner Level (optional)
                                    </label>
                                    <select
                                        id="note-learner-level"
                                        value={note.learnerLevel}
                                        onChange={(event) => onLearnerLevelChange?.(event.target.value as LearnerLevel | "")}
                                        disabled={isCopying}
                                        className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus-visible:ring-2 focus-visible:ring-blue-600"
                                    >
                                        <option value="">Automatic — based on the reader</option>
                                        {LEARNER_LEVEL_OPTIONS.map((option) => (
                                            <option key={option.value} value={option.value}>{option.label}</option>
                                        ))}
                                    </select>
                                    <p className="text-xs text-foreground/60">
                                        Sets how deeply this note is written, independent of who reads it. A lower-level
                                        reader gets gentler wording, never easier material.
                                    </p>
                                </div>
                            </>
                        ) : null}
                    </div>
                </fieldset>
            ) : null}

            {showTagsSection ? (
                <div className="space-y-2">
                    <div className="flex items-center justify-between">
                        <p className="text-sm font-medium text-foreground">Tags</p>
                        {!addingTag && !isCopying ? (
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
                            <Button type="button" size="sm" variant="outline" onClick={handleAddTag}
                                    disabled={isCopying}>
                                Add
                            </Button>
                        </div>
                    ) : null}
                    <p className="text-xs text-foreground/60">
                        Tags help you organize and find your notes later. Add 3-5 keywords like: formulas, anatomy,
                        derivatives, grammar.
                    </p>
                    <div className="flex min-h-9 flex-wrap gap-2">
                        {note.tags.length > 0 ? (
                            note.tags.map((tag) => (
                                <span
                                    key={tag}
                                    className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-2.5 py-1 text-xs text-foreground/80"
                                >
                  <Tag className="h-3 w-3"/>
                                    {tag}
                                    <button
                                        type="button"
                                        aria-label={`Remove ${tag}`}
                                        className="text-foreground/60 hover:text-foreground"
                                        disabled={isCopying}
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
    );

    const renderImportPanel = (className = "") => (
        <div
            className={`motion-note-content-enter space-y-4 rounded-xl border border-dashed border-border/70 bg-muted/20 p-4 ${
                firstStudyHintVisible || importPanelHighlighted ? "border-blue-500/60 ring-2 ring-blue-500/20" : ""
            } ${className}`}
        >
            <div className="space-y-1">
                <p className="text-sm font-medium text-foreground">Import notes</p>
                <p className="text-xs text-foreground/65">
                    Upload an image or file. NoteLib will extract the text into your note so you can review and edit it
                    before saving or generating a Study Pack.
                </p>
            </div>
            <div className="space-y-2 rounded-lg border border-border bg-background p-3">
                <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Upload image or file</p>
                <div className="rounded-lg border border-border border-dashed bg-background px-3 py-3">
                    <div className="flex items-start gap-3">
                        <div className="mt-0.5 flex shrink-0 items-center gap-2 text-foreground/60">
                            <UploadCloud className="h-4 w-4"/>
                            <FileText className="h-4 w-4"/>
                        </div>
                        <div className="min-w-0 flex-1 space-y-2">
                            <p className="text-sm text-foreground/75">
                                Choose a file to extract text into your note.
                            </p>
                            <input
                                ref={importInputRef}
                                key={importFileInputKey}
                                id="note-import-file"
                                type="file"
                                accept={IMPORT_ACCEPT_VALUE}
                                disabled={disableContentEditing || importInFlight}
                                onChange={(event) => {
                                    const file = event.target.files?.[0] ?? null;
                                    onImportFileChange(file);
                                }}
                                className="w-full cursor-pointer text-sm text-foreground/75 file:mr-3 file:cursor-pointer file:rounded-lg file:border-0 file:bg-blue-600 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-white hover:file:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                            />
                        </div>
                    </div>
                </div>
            </div>
            {importFile ? (
                <p className="text-xs text-foreground/65">
                    Selected file: {importFile.name} ({(importFile.size / (1024 * 1024)).toFixed(2)} MB)
                </p>
            ) : null}
            {showImportStatusMessage ? (
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
            {importNotice}
            <p className="text-xs text-foreground/60">
                Supported formats: PNG, JPG, JPEG, WEBP, TXT, PDF, DOCX.
            </p>
            <p className="text-xs text-foreground/65">
                Importing several files at once?{" "}
                <Link href="/notes/import?from=new" className="font-medium text-blue-600 hover:underline dark:text-blue-400">
                    Bulk import multiple files
                </Link>
            </p>
        </div>
    );

    const renderGeneratePanel = (className = "") => (
        <div
            className={`motion-note-content-enter space-y-3 rounded-xl border border-sky-500/20 bg-background p-4 ${className}`}>
            <div className="space-y-1">
                <label htmlFor="generate-note-topic" className="text-sm font-medium text-foreground">
                    Topic
                </label>
                <p className="text-xs text-foreground/65">
                    Built for studying, not just exploring information.
                </p>
            </div>
            {showGenerateNoteTip ? (
                <div
                    className="rounded-xl border border-blue-200 bg-blue-50 px-3 py-2.5 text-xs text-blue-800 dark:border-blue-900/50 dark:bg-blue-950/30 dark:text-blue-300">
                    Tip: You can create notes instantly by typing a topic above.
                </div>
            ) : null}
            <div className="flex flex-col gap-2 sm:flex-row">
                <input
                    id="generate-note-topic"
                    type="text"
                    value={generateTopic}
                    onChange={(event) => onGenerateTopicChange?.(event.target.value)}
                    placeholder="Create a note about Newton's Laws of Motion..."
                    className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus-visible:ring-2 focus-visible:ring-blue-600"
                />
                <Button
                    type="button"
                    onClick={onGenerateNote}
                    disabled={generateNoteDisabled}
                    className="w-full sm:w-auto"
                >
                    {isGeneratingNote ? (
                        <>
                            <Loader2 className="mr-2 h-4 w-4 animate-spin"/>
                            {generateNoteLoadingLabel}
                        </>
                    ) : (
                        <>
                            <Sparkles className="mr-2 h-4 w-4"/>
                            {generateNoteLabel}
                        </>
                    )}
                </Button>
            </div>
            <p className="text-xs text-foreground/60">
                Create a first draft here, then review and edit the content below before saving or generating a Study
                Pack.
            </p>
            <p className="text-xs text-foreground/65">
                Have a list of topics?{" "}
                <Link href="/library/bulk-generate" className="font-medium text-blue-600 hover:underline dark:text-blue-400">
                    Generate them all at once &rarr;
                </Link>
            </p>
            <p className="text-xs text-foreground/50">
                Your Learning Profile helps tailor the new note&apos;s depth, terminology, and examples.
            </p>
            {generateNoteFooter}
            {note.content.trim().length > 0 ? (
                <p className="text-xs text-foreground/55">
                    Creating a note will replace the current content in the editor.
                </p>
            ) : null}
        </div>
    );

    const renderOptionalDetailsSection = () => (
        <section ref={optionalDetailsSectionRef} className="rounded-2xl border border-border/80 bg-muted/15">
            <button
                type="button"
                aria-label="Add details"
                aria-controls="note-optional-details"
                aria-expanded={optionalDetailsOpen}
                onClick={() => setOptionalDetailsOpen((previous) => !previous)}
                className="flex w-full items-start justify-between gap-4 px-4 py-4 text-left sm:px-5"
            >
                <div className="space-y-1">
                    <p className="text-sm font-semibold text-foreground">Add details</p>
                    <p className="max-w-2xl text-xs text-foreground/65">
                        Course / Program is required. Title, subject, and tags are optional.
                    </p>
                </div>
                <ChevronDown
                    className={`mt-0.5 h-4 w-4 shrink-0 text-foreground/55 transition-transform ${
                        optionalDetailsOpen ? "rotate-180" : ""
                    }`}
                    aria-hidden="true"
                />
            </button>
            {optionalDetailsOpen ? (
                <div id="note-optional-details"
                     className="motion-note-content-enter border-t border-border/70 px-4 py-4 sm:px-5">
                    {renderMetadataFields()}
                </div>
            ) : null}
        </section>
    );

    return (
        <main className="mx-auto w-full max-w-4xl space-y-6 px-4 py-6 pb-36 sm:px-6 sm:py-8 sm:pb-40">
            {backHref && backLabel ? <BackLink href={backHref} label={backLabel}/> : null}
            <header className="space-y-3">
                <Card className="space-y-3 border-border/80 bg-background/95 p-4 shadow-sm backdrop-blur sm:p-5">
                    <div className="space-y-2">
                        <h1 className="text-2xl font-semibold text-foreground sm:text-3xl">{pageTitle}</h1>
                        <p className="max-w-xl text-xs text-foreground/70">{helperText}</p>
                    </div>
                </Card>
                {saveStateLabel ? (
                    <p className="text-xs text-foreground/60">{saveStateLabel}</p>
                ) : null}
                {formError ? (
                    <p role="alert" className="text-sm text-red-600 dark:text-red-400">{formError}</p>
                ) : null}
            </header>

            <Card className="space-y-6 p-4 sm:p-6">
                {showGenerateNoteEntry ? (
                    <section className="space-y-4 rounded-2xl border border-border/80 bg-muted/20 p-4 sm:p-5">
                        <div className="space-y-1">
                            <p className="text-sm font-medium text-foreground">Choose how to start</p>
                            <p className="text-xs text-foreground/65">
                                Start from a blank note, generate a first draft, or import material into Content.
                            </p>
                        </div>
                        <div className="grid gap-3 md:grid-cols-3">
                            <div>
                                <button
                                    type="button"
                                    aria-pressed={entryOption === "write"}
                                    onClick={() => onEntryOptionChange?.("write")}
                                    className={`w-full rounded-xl border px-4 py-3 text-left transition ${
                                        entryOption === "write"
                                            ? "border-sky-500 bg-sky-50 text-sky-950 shadow-sm dark:bg-sky-950/30 dark:text-sky-100"
                                            : "border-border bg-background text-foreground/80 hover:border-sky-300"
                                    }`}
                                >
                                    <div className="flex items-center gap-2 text-sm font-semibold">
                                        <FileText className="h-4 w-4"/>
                                        Write your own note
                                    </div>
                                    <p className="mt-1 text-xs text-current/75">
                                        Start from scratch and write or paste what you already know.
                                    </p>
                                </button>
                            </div>
                            <div>
                                <button
                                    type="button"
                                    aria-pressed={entryOption === "generate"}
                                    onClick={() => onEntryOptionChange?.("generate")}
                                    className={`w-full rounded-xl border px-4 py-3 text-left transition ${
                                        entryOption === "generate"
                                            ? "border-sky-500 bg-sky-50 text-sky-950 shadow-sm dark:bg-sky-950/30 dark:text-sky-100"
                                            : "border-border bg-background text-foreground/80 hover:border-sky-300"
                                    }`}
                                >
                                    <div className="flex items-center gap-2 text-sm font-semibold">
                                        <Sparkles className="h-4 w-4"/>
                                        Create from topic
                                    </div>
                                    <p className="mt-1 text-xs text-current/75">
                                        Create a study-ready first draft, then edit it before saving.
                                    </p>
                                </button>
                            </div>
                            <div>
                                <button
                                    type="button"
                                    aria-pressed={entryOption === "import"}
                                    onClick={() => onEntryOptionChange?.("import")}
                                    className={`w-full rounded-xl border px-4 py-3 text-left transition ${
                                        entryOption === "import"
                                            ? "border-sky-500 bg-sky-50 text-sky-950 shadow-sm dark:bg-sky-950/30 dark:text-sky-100"
                                            : "border-border bg-background text-foreground/80 hover:border-sky-300"
                                    }`}
                                >
                                    <div className="flex items-center gap-2 text-sm font-semibold">
                                        <UploadCloud className="h-4 w-4"/>
                                        Import notes
                                    </div>
                                    <p className="mt-1 text-xs text-current/75">
                                        Upload an image or file to extract text into your note, then review and edit it.
                                    </p>
                                </button>
                            </div>
                        </div>
                        {entryOption === "generate" ? renderGeneratePanel() : null}
                        {entryOption === "import" ? renderImportPanel() : null}
                    </section>
                ) : (
                    <section className="space-y-5">
                        {renderMetadataFields()}
                    </section>
                )}

                {firstStudyHintVisible ? (
                    <div
                        className="rounded-lg border border-blue-500/30 bg-blue-50/80 p-4 text-sm text-blue-950 dark:bg-blue-950/30 dark:text-blue-100">
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

                <section
                    ref={contentSectionRef}
                    key={contentAnimationKey === undefined ? undefined : `note-content-section-${contentAnimationKey}`}
                    className={`space-y-2 ${
                        contentAnimationKey !== undefined && Number(contentAnimationKey) > 0
                            ? "motion-note-content-enter"
                            : ""
                    }`}
                >
                    <label htmlFor="note-content" className="text-sm font-medium text-foreground">Content</label>
                    {isTeacherCreateMode ? (
                      <GuidanceTip
                        tipId="teacher-note-content-quality"
                        message="The more detail in your notes, the better the quiz questions. Paste a full lesson outline, not just bullet headers."
                      />
                    ) : null}
                    <textarea
                        ref={contentRef}
                        id="note-content"
                        value={note.content}
                        onChange={(event) => onContentChange(event.target.value)}
                        placeholder="Write or paste your notes here..."
                        readOnly={disableContentEditing}
                        className={`min-h-85 w-full rounded-lg border border-border bg-background px-4 py-3 text-sm leading-relaxed text-foreground outline-none transition-colors placeholder:text-foreground/45 focus-visible:ring-2 focus-visible:ring-blue-600 sm:text-base ${
                            disableContentEditing ? "cursor-not-allowed bg-muted/30 text-foreground/80" : ""
                        } ${
                            firstStudyHintVisible ? "border-blue-500 ring-2 ring-blue-500/30" : ""
                        }`}
                    />
                    {disableContentEditing ? (
                        <p className="text-xs text-foreground/60">
                            {contentLockHint ?? "Content editing is unavailable right now."}
                        </p>
                    ) : (
                        <>
                            {importReviewMessage ? (
                                <div
                                    className="rounded-md border border-amber-500/40 bg-amber-50/70 px-3 py-2 text-sm text-amber-900 dark:bg-amber-950/30 dark:text-amber-100">
                                    <div className="flex items-start gap-2">
                                        <AlertCircle className="mt-0.5 h-4 w-4" aria-hidden="true"/>
                                        <p>{importReviewMessage}</p>
                                    </div>
                                </div>
                            ) : null}
                            <p className="text-xs text-foreground/60">
                                Keep this note focused on one topic for better Study Pack quality.
                            </p>
                            {contentStatusText ? (
                                <p className="text-xs text-foreground/55">
                                    {contentStatusText}
                                </p>
                            ) : null}
                        </>
                    )}
                </section>

                {showGenerateNoteEntry ? renderOptionalDetailsSection() : null}
            </Card>

            <Card className="space-y-2 border-border p-4 sm:p-5">
                <p className="text-sm text-foreground/75">
                    {studyPackMessage ?? "This note doesn't have a Study Pack yet."}
                </p>
                <p className="text-xs text-foreground/60">
                    {actionIcon === "copy"
                        ? actionHelperText
                        : "Generate when you're ready. To create a new version later, make a copy first."}
                </p>
            </Card>

            <div
                className="fixed inset-x-0 bottom-0 z-30 border-t border-border/80 bg-background/95 px-4 pb-[calc(env(safe-area-inset-bottom)+0.75rem)] pt-3 shadow-[0_-10px_24px_rgba(15,23,42,0.08)] backdrop-blur">
                <div
                    className="mx-auto flex w-full max-w-4xl flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div className="min-w-0 space-y-0.5">
                        <p className="text-xs font-medium text-foreground/75">{saveStateLabel ?? "Save your note or generate a Study Pack when ready."}</p>
                        {(() => {
                            const parts = [resolvedCourseProgram?.trim() || null].filter(Boolean);
                            if (parts.length === 0) return null;
                            return (
                                <p className="text-xs text-foreground/55">
                                    Tailored for: {parts.join(" · ")}
                                    <span className="mx-2 text-foreground/25">|</span>
                                    <button
                                        type="button"
                                        onClick={handleRevealOptionalDetails}
                                        className="text-blue-600 underline-offset-4 transition hover:underline dark:text-blue-400"
                                    >
                                        Adjust
                                    </button>
                                </p>
                            );
                        })()}
                        {showGenerateNoteEntry && !resolvedCourseProgram ? (
                            <button
                                type="button"
                                onClick={handleRevealOptionalDetails}
                                className="text-left text-xs text-foreground/60 underline-offset-4 transition hover:text-foreground hover:underline"
                            >
                                Add details like title, subject, course, or tags anytime.
                            </button>
                        ) : null}
                    </div>
                    <div className="grid w-full grid-cols-2 gap-2 sm:w-auto sm:min-w-70">
                        {renderSaveButton("h-11 w-full")}
                        {renderPrimaryAction("h-11 w-full", "w-full", {showHelperText: false})}
                    </div>
                </div>
            </div>
        </main>
    );
}
