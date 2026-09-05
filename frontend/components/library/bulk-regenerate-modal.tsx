"use client";

import { useCallback, useEffect, useId, useRef, useState } from "react";
import type { KeyboardEvent as ReactKeyboardEvent, RefObject } from "react";
import { AlertTriangle, Globe, Link2Off, Loader2 } from "lucide-react";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import {
  bulkRegenerateNotes,
  getBulkRegenerationReceipt,
  preflightNoteRegeneration,
  type NoteBulkRegenerationReceiptResponse,
  type NoteRegenerationPreflightResponse,
  type NoteRegenerationScopeValue,
} from "@/lib/api";
import { cn } from "@/lib/utils";

const SCOPE_STUDY_PACK: NoteRegenerationScopeValue = "STUDY_PACK";
const SCOPE_NOTE_AND_STUDY_PACK: NoteRegenerationScopeValue = "NOTE_AND_STUDY_PACK";
const POLL_INTERVAL_MS = 3000;

type BulkRegenerateModalProps = {
  isOpen: boolean;
  noteIds: string[];
  onClose: () => void;
  /** Lets the Library clear its selection once a batch is genuinely running. */
  onBatchStarted?: () => void;
};

function plural(count: number, singular: string, pluralForm?: string): string {
  return count === 1 ? singular : (pluralForm ?? `${singular}s`);
}

export function BulkRegenerateModal({
  isOpen,
  noteIds,
  onClose,
  onBatchStarted,
}: Readonly<BulkRegenerateModalProps>) {
  // Always opens on the safe scope. Reset comes from the caller UNMOUNTING this component when the
  // modal closes, never from an effect -- leaving a previous destructive choice selected would let a
  // curator get it again from a control they only glanced at.
  const [scope, setScope] = useState<NoteRegenerationScopeValue>(SCOPE_STUDY_PACK);
  const [preflight, setPreflight] = useState<NoteRegenerationPreflightResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [batchId, setBatchId] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<NoteBulkRegenerationReceiptResponse | null>(null);
  const [showBlocked, setShowBlocked] = useState(false);
  const groupLabelId = useId();
  const studyPackRef = useRef<HTMLButtonElement | null>(null);
  const combinedRef = useRef<HTMLButtonElement | null>(null);
  // Guards against a late preflight response for a scope the curator has already moved off.
  const preflightTokenRef = useRef(0);

  const runPreflight = useCallback(async (nextScope: NoteRegenerationScopeValue) => {
    if (noteIds.length === 0) {
      return;
    }
    const token = ++preflightTokenRef.current;
    setLoading(true);
    setError(null);
    try {
      const response = await preflightNoteRegeneration(noteIds, nextScope);
      if (token !== preflightTokenRef.current) {
        return;
      }
      setPreflight(response);
    } catch (caught) {
      if (token !== preflightTokenRef.current) {
        return;
      }
      setPreflight(null);
      setError(caught instanceof Error ? caught.message : "Could not check these notes.");
    } finally {
      if (token === preflightTokenRef.current) {
        setLoading(false);
      }
    }
  }, [noteIds]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    void runPreflight(scope);
  }, [isOpen, runPreflight, scope]);

  // Polls until the batch reports itself finished. `finished` is derived server-side from "no item is
  // still pending", never a stored end-of-batch flag, so a driver killed mid-batch does not leave this
  // polling forever -- it comes back `stale` instead.
  useEffect(() => {
    if (!batchId || receipt?.finished) {
      return;
    }
    let cancelled = false;
    const poll = async () => {
      try {
        const next = await getBulkRegenerationReceipt(batchId);
        if (!cancelled) {
          setReceipt(next);
        }
      } catch {
        // A transient read failure must not kill the batch view; the next tick retries.
      }
    };
    void poll();
    const timer = globalThis.setInterval(() => void poll(), POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      globalThis.clearInterval(timer);
    };
  }, [batchId, receipt?.finished]);

  const handleStart = useCallback(async () => {
    if (!preflight || preflight.readyCount === 0 || preflight.quotaExceeded) {
      return;
    }
    setStarting(true);
    setError(null);
    try {
      const response = await bulkRegenerateNotes(noteIds, scope);
      setBatchId(response.batchId);
      onBatchStarted?.();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Could not start regeneration.");
    } finally {
      setStarting(false);
    }
  }, [noteIds, onBatchStarted, preflight, scope]);

  const handleGroupKeyDown = useCallback((event: ReactKeyboardEvent<HTMLDivElement>) => {
    const keys = ["ArrowRight", "ArrowDown", "ArrowLeft", "ArrowUp", "Home", "End"];
    if (!keys.includes(event.key)) {
      return;
    }
    event.preventDefault();
    let next: NoteRegenerationScopeValue;
    if (event.key === "Home") {
      next = SCOPE_STUDY_PACK;
    } else if (event.key === "End") {
      next = SCOPE_NOTE_AND_STUDY_PACK;
    } else {
      next = scope === SCOPE_STUDY_PACK ? SCOPE_NOTE_AND_STUDY_PACK : SCOPE_STUDY_PACK;
    }
    setScope(next);
    (next === SCOPE_STUDY_PACK ? studyPackRef : combinedRef).current?.focus();
  }, [scope]);

  const combined = scope === SCOPE_NOTE_AND_STUDY_PACK;
  const blockedItems = preflight?.items.filter((item) => item.readiness !== "READY") ?? [];

  const renderScopeCard = (
    value: NoteRegenerationScopeValue,
    ref: RefObject<HTMLButtonElement | null>,
    heading: string,
    body: string,
    cost: string,
  ) => {
    const selected = scope === value;
    return (
      <button
        type="button"
        ref={ref}
        role="radio"
        aria-checked={selected}
        // AppModal's focus trap filters out tabIndex === -1, so the selected card must carry 0 from
        // first render or the whole group would be unreachable by keyboard.
        tabIndex={selected ? 0 : -1}
        onClick={() => setScope(value)}
        className={cn(
          "flex w-full flex-col gap-1 rounded-lg border p-3 text-left transition",
          selected ? "border-primary bg-primary/5" : "border-border hover:border-foreground/30",
        )}
      >
        <span className="text-sm font-semibold">{heading}</span>
        <span className="text-xs text-foreground/70">{body}</span>
        <span className="text-xs font-medium text-foreground/60">{cost}</span>
      </button>
    );
  };

  const renderPreflight = () => (
    <div className="space-y-4">
      <div
        role="radiogroup"
        aria-labelledby={groupLabelId}
        onKeyDown={handleGroupKeyDown}
        className="grid gap-2 sm:grid-cols-2"
      >
        <span id={groupLabelId} className="sr-only">What to regenerate</span>
        {renderScopeCard(
          SCOPE_STUDY_PACK,
          studyPackRef,
          "Study Pack only",
          "Rewrites the summary, key ideas and quiz. Each note's own text is left exactly as it is.",
          "One Study Pack unit per note.",
        )}
        {renderScopeCard(
          SCOPE_NOTE_AND_STUDY_PACK,
          combinedRef,
          "Note + Study Pack",
          "Rewrites each note itself from its topic, then its Study Pack. The old text cannot be brought back.",
          "One topic note unit and one Study Pack unit per note.",
        )}
      </div>

      {loading ? (
        <p className="text-sm text-foreground/70">Checking {noteIds.length} {plural(noteIds.length, "note")}…</p>
      ) : null}

      {preflight && !loading ? (
        <div className="space-y-3">
          <p className="text-sm">
            <span className="font-semibold">{preflight.readyCount} ready</span>
            {preflight.blockedCount > 0 ? ` · ${preflight.blockedCount} blocked` : ""}
            {preflight.notEligibleCount > 0 ? ` · ${preflight.notEligibleCount} not eligible` : ""}
          </p>

          {combined && preflight.publicNotesAffected > 0 ? (
            <p className="flex items-start gap-2 text-xs text-foreground/70">
              <Globe aria-hidden className="mt-0.5 h-3.5 w-3.5 shrink-0" />
              <span>
                {preflight.publicNotesAffected} public {plural(preflight.publicNotesAffected, "note")} will change for
                everyone who can see {plural(preflight.publicNotesAffected, "it", "them")}. Existing learner copies won&apos;t change.
              </span>
            </p>
          ) : null}

          {combined && preflight.sharedQuizzesToDeactivate > 0 ? (
            <p className="flex items-start gap-2 text-xs text-foreground/70">
              <Link2Off aria-hidden className="mt-0.5 h-3.5 w-3.5 shrink-0" />
              <span>
                {preflight.sharedQuizzesToDeactivate} active shared {plural(preflight.sharedQuizzesToDeactivate, "quiz", "quizzes")} will be
                turned off, so nobody is graded against replaced material.
              </span>
            </p>
          ) : null}

          {preflight.quotaExceeded ? (
            <p className="flex items-start gap-2 rounded-md bg-amber-50 p-2 text-xs font-medium text-amber-900 dark:bg-amber-950/40 dark:text-amber-100">
              <AlertTriangle aria-hidden className="mt-0.5 h-3.5 w-3.5 shrink-0" />
              <span>
                This batch needs {preflight.noteGenerationUnitsRequired} topic note {plural(preflight.noteGenerationUnitsRequired, "unit")} and
                you have {preflight.noteGenerationUnitsRemaining} left. Remove {preflight.itemsToRemove}{" "}
                {plural(preflight.itemsToRemove, "note")} to continue.
              </span>
            </p>
          ) : null}

          {blockedItems.length > 0 ? (
            <div>
              <button
                type="button"
                onClick={() => setShowBlocked((previous) => !previous)}
                className="text-xs font-medium text-primary underline-offset-2 hover:underline"
                aria-expanded={showBlocked}
              >
                {showBlocked ? "Hide" : "Show"} the {blockedItems.length} that won&apos;t run
              </button>
              {showBlocked ? (
                <ul className="mt-2 space-y-1 text-xs text-foreground/70">
                  {blockedItems.map((item) => (
                    <li key={item.noteId}>
                      <span className="font-medium">{item.title ?? "Untitled note"}</span>
                      {item.reason ? ` — ${item.reason}` : null}
                    </li>
                  ))}
                </ul>
              ) : null}
            </div>
          ) : null}
        </div>
      ) : null}

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}
    </div>
  );

  const renderProgress = () => {
    if (!receipt) {
      return <p className="text-sm text-foreground/70">Starting…</p>;
    }
    const done = receipt.totalCount - receipt.pendingCount;
    const unresolved = receipt.items.filter(
      (item) => item.state === "FAILED" || item.state === "BLOCKED" || item.state === "NOT_RUN",
    );
    return (
      <div className="space-y-3">
        <p className="text-sm font-semibold">
          {receipt.finished
            ? `Finished · ${receipt.regeneratedCount} of ${receipt.totalCount} regenerated`
            : `${done} of ${receipt.totalCount} done`}
          {receipt.failedCount > 0 ? ` · ${receipt.failedCount} failed` : ""}
          {receipt.blockedCount > 0 ? ` · ${receipt.blockedCount} blocked` : ""}
          {receipt.notRunCount > 0 ? ` · ${receipt.notRunCount} skipped` : ""}
        </p>

        {!receipt.finished && !receipt.stale ? (
          <p className="flex items-center gap-2 text-xs text-foreground/70">
            <Loader2 aria-hidden className="h-3.5 w-3.5 animate-spin" />
            You can close this and come back — it keeps running.
          </p>
        ) : null}

        {receipt.stale ? (
          <p className="flex items-start gap-2 rounded-md bg-amber-50 p-2 text-xs font-medium text-amber-900 dark:bg-amber-950/40 dark:text-amber-100">
            <AlertTriangle aria-hidden className="mt-0.5 h-3.5 w-3.5 shrink-0" />
            <span>
              This batch stopped before finishing, most likely because the app restarted. Everything
              already regenerated is saved. Re-select the notes that didn&apos;t run to finish them.
            </span>
          </p>
        ) : null}

        {unresolved.length > 0 ? (
          <ul className="space-y-1 text-xs text-foreground/70">
            {unresolved.map((item) => (
              <li key={item.noteId}>
                <span className="font-medium">{item.title ?? "Untitled note"}</span>
                {item.reason ? ` — ${item.reason}` : ` — ${item.state.toLowerCase()}`}
              </li>
            ))}
          </ul>
        ) : null}
      </div>
    );
  };

  const actions = batchId ? (
    <Button type="button" onClick={onClose}>Done</Button>
  ) : (
    <>
      <Button type="button" variant="outline" onClick={onClose} disabled={starting}>Cancel</Button>
      <Button
        type="button"
        onClick={() => void handleStart()}
        disabled={loading || starting || !preflight || preflight.readyCount === 0 || preflight.quotaExceeded}
      >
        {starting
          ? "Starting…"
          : `Regenerate ${preflight?.readyCount ?? 0} ${plural(preflight?.readyCount ?? 0, "note")}`}
      </Button>
    </>
  );

  return (
    <AppModal
      isOpen={isOpen}
      variant="sheet"
      panelClassName="sm:max-w-[560px]"
      title={batchId ? "Regenerating" : `Regenerate ${noteIds.length} ${plural(noteIds.length, "note")}`}
      description={batchId
        ? undefined
        : "Replaces generated content for the notes you picked. Nothing is written until you confirm."}
      onClose={onClose}
      actions={actions}
    >
      {batchId ? renderProgress() : renderPreflight()}
    </AppModal>
  );
}
