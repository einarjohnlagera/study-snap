"use client";

import { useId, useMemo, useRef, useState } from "react";
import { AlertTriangle, Check, Globe } from "lucide-react";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { getDomainContextLabel } from "@/lib/domain-context";
import { LEARNER_LEVEL_OPTIONS } from "@/lib/learning-profile";
import type { NoteRegenerationScope, NoteResponse } from "@/lib/api";
import { cn } from "@/lib/utils";

type RegenerateScopeModalProps = {
  isOpen: boolean;
  note: NoteResponse | null;
  /**
   * Curator (TEACHER/ADMIN past onboarding) vs learner. This is the ONLY axis the product can
   * distinguish -- there is no provenance marker separating a generated note from a hand-written one
   * (plan section 6), so every learner-owned note gets the stronger warning. That is the conservative
   * fallback the plan specifies, not an approximation to be refined later with a new field.
   */
  isCurator: boolean;
  /**
   * Remaining allowance on each meter a regeneration spends, or null when the plan summary has not
   * loaded. Null renders no quota copy at all rather than a guessed "0 left".
   *
   * ⚠️ THIS IS DISCLOSURE, NOT ENFORCEMENT. The server checks quota before dispatch and again inside
   * the async worker; a burst can still pass the first check and fail the second, because the charge
   * only lands at commit. Surfacing the number is what stops a curator walking into that, but it does
   * not close the race -- see docs/claude-findings/2026-09-05-regeneration-quota-failure-attribution.md.
   */
  noteGenerationsRemaining: number | null;
  studyPacksRemaining: number | null;
  busy: boolean;
  /** Server-side rejections shown in place, so the learner keeps the scope they chose. */
  errorMessage?: string | null;
  onClose: () => void;
  onConfirm: (scope: NoteRegenerationScope) => void;
  onEditDetails: () => void;
};

const SCOPE_STUDY_PACK: NoteRegenerationScope = "STUDY_PACK";
const SCOPE_NOTE_AND_STUDY_PACK: NoteRegenerationScope = "NOTE_AND_STUDY_PACK";

function learnerLevelLabel(value: NoteResponse["learnerLevel"]): string | null {
  if (!value) {
    return null;
  }
  return LEARNER_LEVEL_OPTIONS.find((option) => option.value === value)?.label ?? null;
}

export function RegenerateScopeModal({
  isOpen,
  note,
  isCurator,
  noteGenerationsRemaining,
  studyPacksRemaining,
  busy,
  errorMessage,
  onClose,
  onConfirm,
  onEditDetails,
}: Readonly<RegenerateScopeModalProps>) {
  // Always opens on the safe scope. The reset comes from the caller UNMOUNTING this component when the
  // modal closes, not from an effect -- leaving a previous destructive choice selected would let a
  // learner get it again from a control they only glanced at.
  const [scope, setScope] = useState<NoteRegenerationScope>(SCOPE_STUDY_PACK);
  const groupLabelId = useId();
  const studyPackRef = useRef<HTMLButtonElement | null>(null);
  const combinedRef = useRef<HTMLButtonElement | null>(null);

  const title = note?.title?.trim() ?? "";
  // The backend rejects both of these before any LLM call (NoteRegenerationTopicRequiredException /
  // NoteRegenerationStudyPackRequiredException). Surfacing them as a disabled card with a reason is the
  // difference between an explained limit and a 400 after the learner commits.
  // Out of allowance for the meter a scope spends. Only ever true on a loaded summary.
  const outOfStudyPacks = studyPacksRemaining !== null && studyPacksRemaining <= 0;
  const outOfNoteGenerations = noteGenerationsRemaining !== null && noteGenerationsRemaining <= 0;

  const missingTitle = title.length === 0;
  const missingStudyPack = !note?.studyPackId;
  const combinedDisabled = missingTitle || missingStudyPack || outOfNoteGenerations || outOfStudyPacks;
  let combinedDisabledReason: string;
  if (missingStudyPack) {
    combinedDisabledReason = "This note has no Study Pack to regenerate yet.";
  } else if (missingTitle) {
    combinedDisabledReason = "Add a title first -- the title is the topic we write from.";
  } else if (outOfNoteGenerations) {
    combinedDisabledReason = "You have no topic note allowance left this cycle.";
  } else {
    // ⚠️ THE STUDY PACK METER NEEDS ITS OWN BRANCH. Falling through to the topic-note wording told a
    // learner with 25 topic notes left that they had none, while the banner directly beneath said the
    // Study Pack meter was the empty one -- two contradictory sentences on the surface built to stop
    // exactly that. Reachable on PLUS, where the two meters differ and many other paths spend this one.
    combinedDisabledReason = "You have no Study Pack allowance left this cycle.";
  }

  const isPublic = note?.visibility === "PUBLIC";
  const combinedSelected = scope === SCOPE_NOTE_AND_STUDY_PACK;
  // Plan section 17: the strong overwrite state is (combined scope AND learner-owned). A curator's
  // canonical note keeps the routine framing and the plain CTA.
  const strongOverwrite = combinedSelected && !isCurator;

  const metadata = useMemo(() => {
    if (!note) {
      return [];
    }
    // Applicable Programs appears NOWHERE, not even read-only (plan section 4): it is discovery
    // metadata that never reaches a prompt, and showing it beside "we use your Note details" would
    // misrepresent it as a generation input.
    return [
      { label: "Topic", value: title || null },
      { label: "Subject", value: note.subject?.trim() || null },
      { label: "Writing context", value: note.domainContext ? getDomainContextLabel(note.domainContext) : null },
      { label: "Depth", value: learnerLevelLabel(note.learnerLevel) },
    ].filter((entry): entry is { label: string; value: string } => entry.value !== null);
  }, [note, title]);

  if (!note) {
    return null;
  }

  const selectScope = (next: NoteRegenerationScope) => {
    if (next === SCOPE_NOTE_AND_STUDY_PACK && combinedDisabled) {
      return;
    }
    setScope(next);
  };

  // Radiogroup keyboard behaviour, written out because the repo has no radiogroup to copy --
  // quiz-choice-list uses aria-pressed, which is a toggle semantic, not a single-choice one.
  const handleGroupKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    const forward = event.key === "ArrowRight" || event.key === "ArrowDown";
    const backward = event.key === "ArrowLeft" || event.key === "ArrowUp";
    if (!forward && !backward && event.key !== "Home" && event.key !== "End") {
      return;
    }
    event.preventDefault();
    const next = event.key === "Home"
      ? SCOPE_STUDY_PACK
      : event.key === "End"
        ? SCOPE_NOTE_AND_STUDY_PACK
        : scope === SCOPE_STUDY_PACK
          ? SCOPE_NOTE_AND_STUDY_PACK
          : SCOPE_STUDY_PACK;
    if (next === SCOPE_NOTE_AND_STUDY_PACK && combinedDisabled) {
      return;
    }
    selectScope(next);
    (next === SCOPE_STUDY_PACK ? studyPackRef : combinedRef).current?.focus();
  };

  const renderCard = (
    value: NoteRegenerationScope,
    ref: React.RefObject<HTMLButtonElement | null>,
    heading: string,
    body: string,
    disabled: boolean,
  ) => {
    const selected = scope === value;
    return (
      <button
        ref={ref}
        type="button"
        role="radio"
        aria-checked={selected}
        // Roving tabindex. The SELECTED card carries 0 from first render, not on first interaction:
        // AppModal's focus trap filters out tabIndex === -1, so two -1 cards would drop the whole
        // group out of the Tab cycle and make the selector unreachable by keyboard.
        tabIndex={selected ? 0 : -1}
        disabled={disabled || busy}
        aria-describedby={disabled ? `${groupLabelId}-${value}-reason` : undefined}
        onClick={() => selectScope(value)}
        className={cn(
          "flex min-h-11 w-full flex-col gap-1 rounded-lg border p-3 text-left transition-colors",
          selected ? "border-foreground/40 bg-muted/60 ring-1 ring-foreground/15" : "border-border",
          disabled ? "cursor-not-allowed opacity-55" : "cursor-pointer hover:bg-highlight",
        )}
      >
        <span className="flex items-center gap-2 text-sm font-semibold text-foreground">
          {/* Selection is conveyed by border AND an icon, never by colour alone. */}
          <span
            aria-hidden="true"
            className={cn(
              "flex h-4 w-4 shrink-0 items-center justify-center rounded-full border text-[10px] leading-none",
              selected ? "border-foreground bg-foreground text-background" : "border-foreground/35 text-transparent",
            )}
          >
            <Check className="h-3 w-3" />
          </span>
          {heading}
        </span>
        <span className="text-xs leading-relaxed text-foreground/70">{body}</span>
        {disabled ? (
          <span id={`${groupLabelId}-${value}-reason`} className="text-xs text-foreground/60">
            {combinedDisabledReason}
          </span>
        ) : null}
      </button>
    );
  };

  return (
    <AppModal
      isOpen={isOpen}
      variant="sheet"
      title="Regenerate this note?"
      onClose={onClose}
      panelClassName="sm:max-w-[560px]"
      actions={(
        <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          {/* Gated on the meter THIS scope spends: Study-Pack-only never spends a topic note unit,
              so an exhausted note-generation allowance must not block it. */}
          <Button
            type="button"
            onClick={() => onConfirm(scope)}
            disabled={busy || outOfStudyPacks || (combinedSelected && outOfNoteGenerations)}
          >
            {busy
              ? "Regenerating..."
              : strongOverwrite
                ? "Regenerate Note + Study Pack"
                : "Regenerate"}
          </Button>
        </div>
      )}
    >
      <div className="space-y-4">
        <p id={groupLabelId} className="text-sm text-foreground/80">
          Choose what to replace.
        </p>
        <div
          role="radiogroup"
          aria-labelledby={groupLabelId}
          onKeyDown={handleGroupKeyDown}
          className="grid gap-2 sm:grid-cols-2"
        >
          {renderCard(
            SCOPE_STUDY_PACK,
            studyPackRef,
            "Study Pack",
            "Rewrites the summary, key concepts and quiz. Your note stays exactly as it is.",
            false,
          )}
          {renderCard(
            SCOPE_NOTE_AND_STUDY_PACK,
            combinedRef,
            "Note + Study Pack",
            "Rewrites the note itself from its topic, then builds a new Study Pack from it.",
            combinedDisabled,
          )}
        </div>

        {/* What you have LEFT, not merely what this costs. The pre-dispatch check and the charge are
            separated by the LLM call, so a burst can pass the check and still run out mid-flight;
            seeing the number beforehand is what prevents walking into that. Hidden entirely until the
            plan summary loads -- a guessed "0 left" would be worse than silence. */}
        {noteGenerationsRemaining !== null || studyPacksRemaining !== null ? (
          <p className="text-xs text-foreground/60">
            {combinedSelected && noteGenerationsRemaining !== null
              ? `${noteGenerationsRemaining} topic note ${noteGenerationsRemaining === 1 ? "generation" : "generations"} and `
              : ""}
            {studyPacksRemaining !== null
              ? `${studyPacksRemaining} Study Pack ${studyPacksRemaining === 1 ? "generation" : "generations"} left this cycle.`
              : ""}
          </p>
        ) : null}

        {outOfStudyPacks || (combinedSelected && outOfNoteGenerations) ? (
          <p className="text-xs font-medium text-amber-700 dark:text-amber-300">
            {outOfStudyPacks
              ? "You have no Study Pack allowance left this cycle, so nothing can be regenerated right now."
              : "You have no topic note allowance left this cycle. Study Pack only still works."}
          </p>
        ) : null}

        {/* One live region, kept mounted across scope changes -- swapping the subtree wholesale can
            fail to announce the new consequence. */}
        <div aria-live="polite" className="space-y-3">
          {combinedSelected ? (
            <div className="space-y-3">
              <div className="rounded-lg border border-border bg-muted/40 p-3">
                <p className="text-xs font-medium text-foreground/70">We write from these Note details</p>
                <dl className="mt-2 grid gap-x-4 gap-y-1 sm:grid-cols-2">
                  {metadata.map((entry) => (
                    <div key={entry.label} className="flex min-w-0 gap-2 text-xs">
                      <dt className="shrink-0 text-foreground/60">{entry.label}</dt>
                      <dd className="min-w-0 truncate font-medium text-foreground">{entry.value}</dd>
                    </div>
                  ))}
                </dl>
                <button
                  type="button"
                  onClick={onEditDetails}
                  disabled={busy}
                  className="mt-2 text-xs font-medium text-blue-600 hover:underline dark:text-blue-400"
                >
                  Edit Note details →
                </button>
              </div>

              {/* Warning hierarchy, most severe first: learner overwrite, then public, then routine. */}
              {strongOverwrite ? (
                <div className="flex gap-2 rounded-lg border border-amber-500/40 bg-amber-500/10 p-3">
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400" aria-hidden="true" />
                  <div className="space-y-1 text-xs leading-relaxed text-foreground/85">
                    <p className="font-semibold text-foreground">This replaces everything written in this note</p>
                    <p>
                      Anything you wrote or edited here will be gone, and it cannot be brought back. The new
                      note is written from the topic and details above.
                    </p>
                  </div>
                </div>
              ) : null}
            </div>
          ) : (
            <p className="text-xs leading-relaxed text-foreground/70">
              Your note won&apos;t change. Quiz history is preserved.
            </p>
          )}

          {isPublic ? (
            <div className="flex gap-2 rounded-lg border border-border bg-muted/40 p-3">
              <Globe className="mt-0.5 h-4 w-4 shrink-0 text-foreground/60" aria-hidden="true" />
              <div className="space-y-1 text-xs leading-relaxed text-foreground/85">
                <p className="font-semibold text-foreground">This Note is public</p>
                <p>
                  Regenerating will replace the content people see on this Note. Existing learner copies
                  won&apos;t change.
                </p>
              </div>
            </div>
          ) : null}

          <p className="text-xs text-foreground/60">
            {combinedSelected ? "Uses 1 topic note and 1 Study Pack" : "Uses 1 Study Pack"}
          </p>

          {errorMessage ? (
            <p role="alert" className="text-xs font-medium text-red-600 dark:text-red-400">
              {errorMessage}
            </p>
          ) : null}
        </div>
      </div>
    </AppModal>
  );
}
