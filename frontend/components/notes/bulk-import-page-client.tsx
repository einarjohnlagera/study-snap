"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { AlertCircle, CheckCircle2, FileText, Trash2, Upload } from "lucide-react";
import { PageHeader } from "@/components/page-header";
import { NearLimitBanner } from "@/components/billing/near-limit-banner";
import { AppModal } from "@/components/ui/app-modal";
import { BackLink } from "@/components/ui/back-link";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  addCollectionItems,
  createCollection,
  getMyPlan,
  importNotesBatch,
  listCollections,
  OCR_DISABLED_ERROR_CODE,
  type BulkImportResult,
  type NoteCollectionSummary,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { getCollectionLabels } from "@/lib/collection-labels";
import { formatStudyPackResetDate } from "@/lib/plans";
import { IMPORT_ACCEPT_VALUE } from "@/lib/note-import";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { OcrDisabledNotice } from "@/components/notes/ocr-disabled-notice";

export const MAX_BATCH_IMPORT_FILES = 20;
export const BATCH_IMPORT_CAP_MESSAGE = `You can import up to ${MAX_BATCH_IMPORT_FILES} files at once.`;

const COLLECTION_TITLE_MAX_LENGTH = 150;
const MEGABYTE_BYTES = 1024 * 1024;

type CollectionSuccess = {
  id: string;
  title: string;
};

function formatFileSize(size: number): string {
  if (size < MEGABYTE_BYTES) {
    return `${Math.max(1, Math.round(size / 1024))} KB`;
  }
  return `${(size / MEGABYTE_BYTES).toFixed(2)} MB`;
}

function getResultSummary(result: BulkImportResult, totalFiles: number): string {
  if (result.created.length === totalFiles) {
    return `Imported all ${totalFiles} ${totalFiles === 1 ? "file" : "files"}.`;
  }
  if (result.created.length === 0) {
    return `No files were imported. ${result.failed.length} ${result.failed.length === 1 ? "file" : "files"} failed.`;
  }
  return `Imported ${result.created.length} of ${totalFiles} files.`;
}

function AddImportedDraftsModal({
  isOpen,
  noteIds,
  singularLabel,
  onClose,
  onAdded,
}: Readonly<{
  isOpen: boolean;
  noteIds: string[];
  singularLabel: string;
  onClose: () => void;
  onAdded: (collection: CollectionSuccess) => void;
}>) {
  const [collections, setCollections] = useState<NoteCollectionSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submittingCollectionId, setSubmittingCollectionId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [title, setTitle] = useState("");

  const loadAvailableCollections = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      setCollections(await listCollections());
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : `Could not load your ${singularLabel.toLowerCase()}s.`);
    } finally {
      setLoading(false);
    }
  }, [singularLabel]);

  useEffect(() => {
    if (!isOpen) {
      setCollections([]);
      setLoadError(null);
      setSubmitError(null);
      setSubmittingCollectionId(null);
      setCreating(false);
      setTitle("");
      return;
    }
    void loadAvailableCollections();
  }, [isOpen, loadAvailableCollections]);

  const handleAddToExisting = async (collectionId: string) => {
    setSubmittingCollectionId(collectionId);
    setSubmitError(null);
    try {
      const saved = await addCollectionItems(collectionId, noteIds);
      onAdded({ id: saved.id, title: saved.title });
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : `Could not add drafts to this ${singularLabel.toLowerCase()}.`);
    } finally {
      setSubmittingCollectionId(null);
    }
  };

  const handleCreate = async () => {
    const trimmedTitle = title.trim();
    if (!trimmedTitle) {
      setSubmitError("Title is required.");
      return;
    }
    setCreating(true);
    setSubmitError(null);
    try {
      const saved = await createCollection({ title: trimmedTitle, noteIds });
      onAdded({ id: saved.id, title: saved.title });
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : `Could not create this ${singularLabel.toLowerCase()}.`);
    } finally {
      setCreating(false);
    }
  };

  return (
    <AppModal
      isOpen={isOpen}
      title={`Add to a ${singularLabel}`}
      description={`Add ${noteIds.length} imported draft${noteIds.length === 1 ? "" : "s"} to an existing ${singularLabel.toLowerCase()} or create a new one.`}
      onClose={onClose}
      panelClassName="sm:max-w-2xl"
      actions={<Button type="button" variant="secondary" onClick={onClose}>Close</Button>}
    >
      <div className="space-y-5">
        {submitError ? (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-200">
            {submitError}
          </p>
        ) : null}

        {loading ? <p className="text-sm text-foreground/60">Loading your {singularLabel.toLowerCase()}s...</p> : null}

        {loadError ? (
          <div className="space-y-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950/30 dark:text-red-200">
            <p>{loadError}</p>
            <Button type="button" variant="outline" size="sm" onClick={() => void loadAvailableCollections()}>
              Retry
            </Button>
          </div>
        ) : null}

        {!loading && !loadError && collections.length > 0 ? (
          <section className="space-y-2">
            <h3 className="text-sm font-semibold">Existing {singularLabel.toLowerCase()}s</h3>
            <div className="max-h-64 space-y-2 overflow-y-auto pr-1">
              {collections.map((collection) => (
                <div key={collection.id} className="flex flex-col gap-3 rounded-lg border border-border p-3 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{collection.title}</p>
                    <p className="text-xs text-foreground/60">
                      {collection.itemCount} {collection.itemCount === 1 ? "note" : "notes"}
                    </p>
                  </div>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    loading={submittingCollectionId === collection.id}
                    loadingText="Adding..."
                    disabled={creating || (submittingCollectionId !== null && submittingCollectionId !== collection.id)}
                    onClick={() => void handleAddToExisting(collection.id)}
                  >
                    Add here
                  </Button>
                </div>
              ))}
            </div>
          </section>
        ) : null}

        {!loading && !loadError && collections.length === 0 ? (
          <p className="rounded-lg bg-muted px-3 py-3 text-sm text-foreground/70">
            Create your first {singularLabel.toLowerCase()} and add these drafts to it.
          </p>
        ) : null}

        {!loadError ? (
          <section className="space-y-3 border-t border-border pt-4">
            <h3 className="text-sm font-semibold">Create new {singularLabel}</h3>
            <label className="block space-y-1.5">
              <span className="text-sm font-medium">Title</span>
              <input
                value={title}
                maxLength={COLLECTION_TITLE_MAX_LENGTH}
                onChange={(event) => {
                  setTitle(event.target.value);
                  setSubmitError(null);
                }}
                placeholder={`${singularLabel} title`}
                className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
              />
            </label>
            <Button
              type="button"
              loading={creating}
              loadingText="Creating..."
              disabled={submittingCollectionId !== null}
              onClick={() => void handleCreate()}
            >
              Create new {singularLabel}
            </Button>
          </section>
        ) : null}
      </div>
    </AppModal>
  );
}

export function BulkImportPageClient() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const cameFromNewNote = searchParams.get("from") === "new";
  const backLink = cameFromNewNote
    ? { href: "/notes/new", label: "New Note" }
    : { href: "/library", label: "Library" };
  const [files, setFiles] = useState<File[]>([]);
  const [importing, setImporting] = useState(false);
  const [requestError, setRequestError] = useState<string | null>(null);
  const [result, setResult] = useState<BulkImportResult | null>(null);
  const [submittedFileCount, setSubmittedFileCount] = useState(0);
  const [collectionModalOpen, setCollectionModalOpen] = useState(false);
  const [collectionSuccess, setCollectionSuccess] = useState<CollectionSuccess | null>(null);
  const [ocrQuota, setOcrQuota] = useState<{ remaining: number; resetLabel: string } | null>(null);

  useEffect(() => {
    requireAuthenticatedOnboardedUser(router);
  }, [router]);

  const isAdmin = getAuthUser()?.role === "ADMIN";
  const currentPlan = getAuthUser()?.planType ?? "FREE";

  useEffect(() => {
    if (isAdmin) {
      return;
    }
    let mounted = true;
    void getMyPlan()
      .then((plan) => {
        if (mounted && typeof plan.remaining.ocrRemaining === "number") {
          setOcrQuota({
            remaining: plan.remaining.ocrRemaining,
            resetLabel: formatStudyPackResetDate(plan.usageCycle?.endsAt),
          });
        }
      })
      .catch(() => {
        // Awareness-only; the import flow works without the quota readout.
      });
    return () => {
      mounted = false;
    };
  }, [isAdmin]);

  const collectionLabels = getCollectionLabels(getAuthUser()?.profileType);
  const capExceeded = files.length > MAX_BATCH_IMPORT_FILES;
  const createdNoteIds = useMemo(() => result?.created.map((note) => note.noteId) ?? [], [result]);

  const handleImport = useCallback(async () => {
    if (files.length === 0 || files.length > MAX_BATCH_IMPORT_FILES) {
      return;
    }
    setImporting(true);
    setRequestError(null);
    try {
      const importResult = await importNotesBatch(files);
      setSubmittedFileCount(files.length);
      setResult(importResult);
    } catch (error) {
      setRequestError(error instanceof Error ? error.message : "We could not import these files right now. Please try again.");
    } finally {
      setImporting(false);
    }
  }, [files]);

  const resetImport = () => {
    setFiles([]);
    setRequestError(null);
    setResult(null);
    setSubmittedFileCount(0);
    setCollectionModalOpen(false);
    setCollectionSuccess(null);
  };

  return (
    <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <BackLink href={backLink.href} label={backLink.label} />
      <PageHeader
        eyebrow="Workspace"
        title="Import files"
        description="Upload up to 20 files to create one reviewable draft per file without generating Study Packs."
      />

      {ocrQuota ? (
        ocrQuota.remaining <= 2 ? (
          <NearLimitBanner
            planType={currentPlan}
            remainingCredits={ocrQuota.remaining}
            resetDateLabel={ocrQuota.resetLabel}
            creditLabel="image scan"
            ctaContext="general"
            analyticsSource="bulk_import_ocr_near_limit"
            onUpgrade={() => router.push("/settings?section=plans")}
          />
        ) : (
          <p className="text-xs text-foreground/60">
            {ocrQuota.remaining} image scan{ocrQuota.remaining === 1 ? "" : "s"} (OCR) left this month — used only for photos and scanned PDFs.
          </p>
        )
      ) : null}

      {!result ? (
        <Card className="space-y-5 p-4 sm:p-6">
          <div className="space-y-1">
            <h2 className="text-xl font-semibold">Choose files</h2>
            <p className="text-sm text-foreground/70">
              Supported formats: PNG, JPG, JPEG, WEBP, TXT, PDF, and DOCX. You can import up to {MAX_BATCH_IMPORT_FILES} files at once.
            </p>
          </div>

          <label className="block space-y-2">
            <span className="text-sm font-medium">Files</span>
            <span className="flex min-h-32 cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-border bg-background px-4 py-6 text-center">
              <Upload className="h-6 w-6 text-blue-600 dark:text-blue-400" aria-hidden="true" />
              <span className="text-sm font-medium">Select files to import</span>
              <span className="text-xs text-foreground/60">You can add more files before importing.</span>
            </span>
            <input
              type="file"
              multiple
              accept={IMPORT_ACCEPT_VALUE}
              disabled={importing}
              aria-label="Files"
              className="sr-only"
              onChange={(event) => {
                const nextFiles = Array.from(event.target.files ?? []);
                if (nextFiles.length > 0) {
                  setFiles((current) => [...current, ...nextFiles]);
                  setRequestError(null);
                }
                event.target.value = "";
              }}
            />
          </label>

          {files.length > 0 ? (
            <section className="space-y-3" aria-labelledby="selected-files-heading">
              <div className="flex items-center justify-between gap-3">
                <h3 id="selected-files-heading" className="text-sm font-semibold">
                  Selected files ({files.length})
                </h3>
                <Button type="button" variant="ghost" size="sm" disabled={importing} onClick={() => setFiles([])}>
                  Clear all
                </Button>
              </div>
              <ul className="divide-y divide-border rounded-xl border border-border bg-background">
                {files.map((file, index) => (
                  <li key={`${file.name}-${file.size}-${file.lastModified}-${index}`} className="flex items-center gap-3 px-3 py-3">
                    <FileText className="h-4 w-4 shrink-0 text-foreground/55" aria-hidden="true" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{file.name}</p>
                      <p className="text-xs text-foreground/60">{formatFileSize(file.size)}</p>
                    </div>
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      aria-label={`Remove ${file.name}`}
                      disabled={importing}
                      onClick={() => setFiles((current) => current.filter((_, fileIndex) => fileIndex !== index))}
                    >
                      <Trash2 className="h-4 w-4" aria-hidden="true" />
                    </Button>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}

          {capExceeded ? (
            <p role="alert" className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:border-amber-900 dark:bg-amber-950/30 dark:text-amber-200">
              {BATCH_IMPORT_CAP_MESSAGE}
            </p>
          ) : null}

          {requestError ? (
            <div role="alert" className="space-y-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950/30 dark:text-red-200">
              <p>{requestError}</p>
              <Button type="button" variant="outline" size="sm" onClick={() => void handleImport()}>
                Retry import
              </Button>
            </div>
          ) : null}

          <Button
            type="button"
            loading={importing}
            loadingText={`Importing ${files.length} ${files.length === 1 ? "file" : "files"}...`}
            disabled={files.length === 0 || capExceeded}
            onClick={() => void handleImport()}
          >
            Import {files.length > 0 ? `${files.length} ${files.length === 1 ? "file" : "files"}` : "files"}
          </Button>
        </Card>
      ) : (
        <div className="space-y-6">
          <Card className="space-y-5 p-4 sm:p-6">
            <div className="space-y-1">
              <h2 className="text-xl font-semibold">Import results</h2>
              <p className="text-sm text-foreground/70">{getResultSummary(result, submittedFileCount)}</p>
            </div>

            {result.created.length > 0 ? (
              <section className="space-y-3" aria-labelledby="created-notes-heading">
                <h3 id="created-notes-heading" className="text-sm font-semibold text-emerald-700 dark:text-emerald-300">
                  Created ({result.created.length})
                </h3>
                <ul className="space-y-2">
                  {result.created.map((note) => (
                    <li key={note.noteId} className="flex flex-col gap-3 rounded-lg border border-emerald-200 bg-emerald-50/60 p-3 dark:border-emerald-900 dark:bg-emerald-950/20 sm:flex-row sm:items-center sm:justify-between">
                      <div className="min-w-0 space-y-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <CheckCircle2 className="h-4 w-4 text-emerald-600 dark:text-emerald-400" aria-hidden="true" />
                          <p className="font-medium">{note.title || note.fileName}</p>
                          {note.lowConfidence ? (
                            <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800 dark:bg-amber-950/50 dark:text-amber-200">
                              Low confidence — review recommended
                            </span>
                          ) : null}
                        </div>
                        <p className="text-xs text-foreground/60">{note.fileName}</p>
                      </div>
                      <Link href={`/notes/${note.noteId}`} className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400">
                        Review draft
                      </Link>
                    </li>
                  ))}
                </ul>
              </section>
            ) : null}

            {result.failed.length > 0 ? (
              <section className="space-y-3" aria-labelledby="failed-files-heading">
                <h3 id="failed-files-heading" className="text-sm font-semibold text-red-700 dark:text-red-300">
                  Failed ({result.failed.length})
                </h3>
                <ul className="space-y-2">
                  {result.failed.map((failure, index) => {
                    const isOcrDisabledFailure = failure.errorCode === OCR_DISABLED_ERROR_CODE;
                    return (
                      <li
                        key={`${failure.fileName}-${failure.errorCode}-${index}`}
                        className={isOcrDisabledFailure
                          ? "space-y-2"
                          : "rounded-lg border border-red-200 bg-red-50 p-3 dark:border-red-900 dark:bg-red-950/20"}
                      >
                        {isOcrDisabledFailure ? (
                          <>
                            <p className="text-sm font-medium text-foreground">{failure.fileName}</p>
                            <OcrDisabledNotice message={failure.message} source="bulk_import_result" />
                          </>
                        ) : (
                          <div className="flex items-start gap-2">
                            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-red-600 dark:text-red-400" aria-hidden="true" />
                            <div className="space-y-1">
                              <p className="text-sm font-medium">{failure.fileName}</p>
                              <p className="text-sm text-red-700 dark:text-red-200">{failure.message}</p>
                            </div>
                          </div>
                        )}
                      </li>
                    );
                  })}
                </ul>
              </section>
            ) : null}

            <div className="flex flex-col gap-2 border-t border-border pt-4 sm:flex-row">
              <Link href="/library" className={buttonVariants({ className: "w-full sm:w-auto" })}>
                Review in Library
              </Link>
              <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={resetImport}>
                Import more files
              </Button>
            </div>
          </Card>

          {result.created.length > 0 ? (
            <Card className="space-y-4 p-4 sm:p-6">
              <div className="space-y-1">
                <h2 className="text-lg font-semibold">Organize these drafts</h2>
                <p className="text-sm text-foreground/70">
                  Add the imported drafts to a {collectionLabels.singular.toLowerCase()} now, or skip this step and organize them later.
                </p>
              </div>
              {collectionSuccess ? (
                <div role="status" className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/30 dark:text-emerald-200">
                  Added to {collectionSuccess.title}.{" "}
                  <Link href={`/collections/${collectionSuccess.id}`} className="font-medium text-blue-600 hover:underline dark:text-blue-400">
                    View {collectionLabels.singular}
                  </Link>
                </div>
              ) : (
                <Button type="button" variant="outline" onClick={() => setCollectionModalOpen(true)}>
                  Add these {result.created.length} drafts to a {collectionLabels.singular}
                </Button>
              )}
            </Card>
          ) : null}
        </div>
      )}

      <AddImportedDraftsModal
        isOpen={collectionModalOpen}
        noteIds={createdNoteIds}
        singularLabel={collectionLabels.singular}
        onClose={() => setCollectionModalOpen(false)}
        onAdded={(collection) => {
          setCollectionSuccess(collection);
          setCollectionModalOpen(false);
        }}
      />
    </main>
  );
}
