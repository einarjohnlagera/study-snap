"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { CourseProgramCombobox } from "@/components/metadata/course-program-combobox";
import { SubjectCombobox } from "@/components/notes/subject-combobox";
import { NearLimitBanner } from "@/components/billing/near-limit-banner";
import { AppModal } from "@/components/ui/app-modal";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Toggle } from "@/components/ui/toggle";
import { formatStudyPackResetDate } from "@/lib/plans";
import {
  ApiRequestError,
  bulkGenerateNotes,
  getMe,
  getMyPlan,
  listCoursePrograms,
  listSubjects,
  type BulkGenerateNotesRequest,
  type DomainContext,
  type LearnerLevel,
  type NoteTargetProfileType,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { consumeBulkGenerationRetryStash, setBulkQueuedFlash } from "@/lib/bulk-generation-flash";
import { MAX_TOPIC_LENGTH, parsePastedTopics } from "@/lib/bulk-topics";
import {
  getNoteTargetProfileLabel,
  isTeacherSelectableNoteTarget,
  mapProfileTypeToNoteTargetProfile,
  SELECTABLE_NOTE_TARGET_PROFILE_TYPES,
} from "@/lib/note-target-profile";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { DOMAIN_CONTEXT_OPTIONS } from "@/lib/domain-context";
import { LEARNER_LEVEL_OPTIONS } from "@/lib/learning-profile";

export const MAX_BULK_GENERATION_TOPICS = 50;

type TopicRow = {
  id: number;
  value: string;
};

type BulkGenerationQuota = {
  noteGenRemaining: number;
  studyPackRemaining: number;
  resetLabel: string;
};

export function BulkGenerationPageClient() {
  const router = useRouter();
  const authUser = getAuthUser();
  const isTeacherOrAdmin = isTeacherSelectableNoteTarget(authUser?.profileType, authUser?.role);
  const isAdmin = authUser?.role === "ADMIN";
  const nextTopicId = useRef(2);
  const [subject, setSubject] = useState("");
  const [courseProgram, setCourseProgram] = useState("");
  const [domainContext, setDomainContext] = useState<DomainContext | "">("");
  const [learnerLevel, setLearnerLevel] = useState<LearnerLevel | "">("");
  const [targetProfileType, setTargetProfileType] = useState<NoteTargetProfileType | "">(
    mapProfileTypeToNoteTargetProfile(authUser?.profileType),
  );
  const [topics, setTopics] = useState<TopicRow[]>([{ id: 1, value: "" }]);
  const [makePublic, setMakePublic] = useState(false);
  const [subjectSuggestions, setSubjectSuggestions] = useState<string[]>([]);
  const [courseProgramSuggestions, setCourseProgramSuggestions] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pasteNotice, setPasteNotice] = useState<string | null>(null);
  const [quota, setQuota] = useState<BulkGenerationQuota | null>(null);
  const [studyPackConfirmOpen, setStudyPackConfirmOpen] = useState(false);
  const currentPlan = authUser?.planType ?? "FREE";
  const normalizedTopics = useMemo(
    () => topics.map((topic) => topic.value.trim()).filter(Boolean),
    [topics],
  );
  const effectiveTopicCap = quota
    ? Math.min(MAX_BULK_GENERATION_TOPICS, quota.noteGenRemaining)
    : MAX_BULK_GENERATION_TOPICS;
  const isNoteGenCapped = quota !== null && effectiveTopicCap < MAX_BULK_GENERATION_TOPICS;
  const overCap = normalizedTopics.length > effectiveTopicCap;
  const studyPackShortfall = quota
    ? Math.max(0, normalizedTopics.length - quota.studyPackRemaining)
    : 0;

  useEffect(() => {
    requireAuthenticatedOnboardedUser(router);
  }, [router]);

  useEffect(() => {
    const stash = consumeBulkGenerationRetryStash();
    if (!stash) {
      return;
    }
    setSubject(stash.subject);
    setCourseProgram(stash.courseProgram ?? "");
    setDomainContext(stash.domainContext ?? "");
    setLearnerLevel(stash.learnerLevel ?? "");
    setTargetProfileType(stash.targetProfileType as NoteTargetProfileType);
    setMakePublic(stash.makePublic);
    setTopics(stash.topics.map((value, index) => ({ id: index + 1, value })));
    nextTopicId.current = stash.topics.length + 1;
  }, []);

  useEffect(() => {
    let active = true;
    void Promise.allSettled([
      listSubjects("mine"),
      listCoursePrograms("mine"),
      getMe(),
      isAdmin ? Promise.resolve(null) : getMyPlan(),
    ]).then(([subjectsResult, courseProgramsResult, meResult, planResult]) => {
      if (!active) {
        return;
      }
      setSubjectSuggestions(subjectsResult.status === "fulfilled" ? subjectsResult.value : []);
      setCourseProgramSuggestions(
        courseProgramsResult.status === "fulfilled" ? courseProgramsResult.value : [],
      );
      if (meResult.status === "fulfilled") {
        if (isTeacherOrAdmin) {
          setCourseProgram((current) => current || meResult.value.courseProgram || "");
        }
      }
      if (!isAdmin && planResult.status === "fulfilled" && planResult.value) {
        const noteGenRemaining = planResult.value.remaining.noteGenerationsRemaining;
        const studyPackRemaining = planResult.value.remaining.studyPacksRemaining;
        if (typeof noteGenRemaining === "number" && typeof studyPackRemaining === "number") {
          setQuota({
            noteGenRemaining,
            studyPackRemaining,
            resetLabel: formatStudyPackResetDate(planResult.value.usageCycle?.endsAt),
          });
        }
      }
    });

    return () => {
      active = false;
    };
  }, [isAdmin, isTeacherOrAdmin]);

  const validate = (): string | null => {
    if (!subject.trim()) {
      return "Enter a subject for this batch.";
    }
    if (normalizedTopics.length === 0) {
      return "Add at least one topic.";
    }
    if (normalizedTopics.length > MAX_BULK_GENERATION_TOPICS) {
      return `You can bulk generate up to ${MAX_BULK_GENERATION_TOPICS} topics at once.`;
    }
    if (quota && normalizedTopics.length > quota.noteGenRemaining) {
      const excess = normalizedTopics.length - quota.noteGenRemaining;
      return `You have ${quota.noteGenRemaining} topic note${quota.noteGenRemaining === 1 ? "" : "s"} left this cycle. Remove ${excess} topic${excess === 1 ? "" : "s"} to continue.`;
    }
    if (isTeacherOrAdmin && !courseProgram.trim()) {
      return "Enter a course or program for this batch.";
    }
    if (isTeacherOrAdmin && !targetProfileType) {
      return "Select a target audience for this batch.";
    }
    return null;
  };

  const updateTopic = (id: number, value: string) => {
    setTopics((current) => current.map((topic) => (topic.id === id ? { ...topic, value } : topic)));
  };

  const addTopic = () => {
    setTopics((current) => [...current, { id: nextTopicId.current++, value: "" }]);
  };

  const removeTopic = (id: number) => {
    setTopics((current) => current.length === 1 ? current : current.filter((topic) => topic.id !== id));
  };

  const handleTopicPaste = (id: number, event: React.ClipboardEvent<HTMLInputElement>) => {
    const parsed = parsePastedTopics(event.clipboardData.getData("text"));
    const targetIsEmpty = (topics.find((topic) => topic.id === id)?.value.trim() ?? "") === "";
    // Let the browser handle an ordinary single-line paste into a non-empty
    // field (preserves cursor position); only take over for multi-topic pastes
    // or a single bulleted line dropped into an empty row.
    if (parsed.length === 0 || (parsed.length === 1 && !targetIsEmpty)) {
      return;
    }
    event.preventDefault();
    setPasteNotice(null);
    setTopics((current) => {
      const index = current.findIndex((topic) => topic.id === id);
      if (index === -1) {
        return current;
      }
      const replaceTarget = current[index]!.value.trim() === "";
      const existingCount = current
        .filter((topic) => topic.value.trim() && !(replaceTarget && topic.id === id))
        .length;
      const remainingSlots = Math.max(0, effectiveTopicCap - existingCount);
      const accepted = parsed.slice(0, remainingSlots);
      const dropped = parsed.length - accepted.length;
      if (dropped > 0) {
        const capReason = isNoteGenCapped
          ? `your ${effectiveTopicCap} topic note${effectiveTopicCap === 1 ? "" : "s"} left this cycle`
          : `the ${MAX_BULK_GENERATION_TOPICS} max`;
        setPasteNotice(
          `Added ${accepted.length} topic${accepted.length === 1 ? "" : "s"} up to ${capReason} — ${dropped} more weren't added.`,
        );
      }
      if (accepted.length === 0) {
        return current;
      }
      const newRows: TopicRow[] = accepted.map((value) => ({ id: nextTopicId.current++, value }));
      const removeCount = replaceTarget ? 1 : 0;
      const insertAt = replaceTarget ? index : index + 1;
      return [
        ...current.slice(0, insertAt),
        ...newRows,
        ...current.slice(insertAt + removeCount),
      ];
    });
  };

  const refreshQuota = useCallback(async () => {
    if (isAdmin) {
      return;
    }
    try {
      const plan = await getMyPlan();
      const noteGenRemaining = plan.remaining.noteGenerationsRemaining;
      const studyPackRemaining = plan.remaining.studyPacksRemaining;
      if (typeof noteGenRemaining === "number" && typeof studyPackRemaining === "number") {
        setQuota({
          noteGenRemaining,
          studyPackRemaining,
          resetLabel: formatStudyPackResetDate(plan.usageCycle?.endsAt),
        });
      }
    } catch {
      // Non-blocking: keep the last known quota if the refresh fails.
    }
  }, [isAdmin]);

  const doSubmit = async () => {
    const request: BulkGenerateNotesRequest = {
      subject: subject.trim(),
      topics: normalizedTopics,
      makePublic,
      ...(isTeacherOrAdmin
        ? {
            courseProgram: courseProgram.trim(),
            domainContext: domainContext || null,
            learnerLevel: learnerLevel || null,
            targetProfileType: targetProfileType as NoteTargetProfileType,
          }
        : {}),
    };

    setSubmitting(true);
    setError(null);
    try {
      const response = await bulkGenerateNotes(request);
      setBulkQueuedFlash(response.queuedTopics, response.resultId);
      router.push("/library");
    } catch (submitError) {
      if (submitError instanceof ApiRequestError && submitError.status === 403) {
        router.replace("/dashboard");
        return;
      }
      // The backend re-checks the note-generation quota at submit time and rejects
      // when a stale client over-queues; surface that message and refresh the quota.
      void refreshQuota();
      setError(submitError instanceof Error ? submitError.message : "Could not queue these notes.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }
    setError(null);
    if (studyPackShortfall > 0) {
      setStudyPackConfirmOpen(true);
      return;
    }
    void doSubmit();
  };

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <BackLink href="/library" label="Library" />

      <header className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Library tool
        </p>
        <h1 className="text-3xl font-semibold text-foreground">Bulk generate notes</h1>
        <p className="max-w-3xl text-sm leading-relaxed text-foreground/70">
          Add one subject and a list of topics. Each topic becomes its own note and Study Pack in the background, then appears in your Library as it finishes.
        </p>
      </header>

      <form className="space-y-6" onSubmit={handleSubmit}>
        {quota && quota.noteGenRemaining <= 2 ? (
          <NearLimitBanner
            planType={currentPlan}
            remainingCredits={quota.noteGenRemaining}
            resetDateLabel={quota.resetLabel}
            creditLabel="topic note"
            ctaContext="note-generation-limit"
            analyticsSource="bulk_generation_note_generation_near_limit"
            onUpgrade={() => router.push("/settings?section=plans")}
          />
        ) : null}

        <Card className="space-y-5 p-5 sm:p-6">
          <div className="space-y-2">
            <label htmlFor="bulk-subject" className="text-sm font-medium text-foreground">
              Subject <span className="text-red-500" aria-hidden="true">*</span>
            </label>
            <SubjectCombobox
              id="bulk-subject"
              value={subject}
              suggestions={subjectSuggestions}
              onChange={setSubject}
            />
          </div>

          <div data-testid="bulk-metadata-grid" className="grid gap-4 empty:hidden sm:grid-cols-2">
            {isTeacherOrAdmin ? (
              <div className="space-y-2">
                <label htmlFor="bulk-course-program" className="text-sm font-medium text-foreground">
                  Course / Program <span className="text-red-500" aria-hidden="true">*</span>
                </label>
                <CourseProgramCombobox
                  id="bulk-course-program"
                  value={courseProgram}
                  suggestions={courseProgramSuggestions}
                  onChange={setCourseProgram}
                  context="note"
                />
              </div>
            ) : null}

            {isTeacherOrAdmin ? (
              <div className="space-y-2">
                <label htmlFor="bulk-target-profile-type" className="text-sm font-medium text-foreground">
                  Target Audience <span className="text-red-500" aria-hidden="true">*</span>
                </label>
                <select
                  id="bulk-target-profile-type"
                  value={targetProfileType}
                  onChange={(event) => setTargetProfileType(event.target.value as NoteTargetProfileType | "")}
                  className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus-visible:ring-2 focus-visible:ring-blue-600"
                >
                  <option value="">Select an audience</option>
                  {SELECTABLE_NOTE_TARGET_PROFILE_TYPES.map((value) => (
                    <option key={value} value={value}>{getNoteTargetProfileLabel(value)}</option>
                  ))}
                </select>
                <p className="text-xs text-foreground/60">
                  Categorizes these notes for Library and Public Library audiences.
                </p>
              </div>
            ) : null}

            {isTeacherOrAdmin ? (
              <div className="space-y-2">
                <label htmlFor="bulk-domain-context" className="text-sm font-medium text-foreground">
                  Domain Context (optional)
                </label>
                <select
                  id="bulk-domain-context"
                  value={domainContext}
                  onChange={(event) => setDomainContext(event.target.value as DomainContext | "")}
                  className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus-visible:ring-2 focus-visible:ring-blue-600"
                >
                  <option value="">Use Course / Program fallback</option>
                  {DOMAIN_CONTEXT_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
                <p className="text-xs text-foreground/60">
                  Controls the AI&apos;s academic domain and framing for every note in this batch.
                </p>
              </div>
            ) : null}

            {isTeacherOrAdmin ? (
              <div className="space-y-2">
                <label htmlFor="bulk-learner-level" className="text-sm font-medium text-foreground">
                  Note Learner Level (optional)
                </label>
                <select
                  id="bulk-learner-level"
                  value={learnerLevel}
                  onChange={(event) => setLearnerLevel(event.target.value as LearnerLevel | "")}
                  className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus-visible:ring-2 focus-visible:ring-blue-600"
                >
                  <option value="">Use reader level fallback</option>
                  {LEARNER_LEVEL_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
                <p className="text-xs text-foreground/60">
                  Controls the authored depth for every note, independent of who reads it.
                </p>
              </div>
            ) : null}

          </div>

          <div className="space-y-1">
            <div className="flex items-center gap-3">
              <label htmlFor="bulk-make-public" className="text-sm font-medium text-foreground">Public</label>
              <Toggle
                id="bulk-make-public"
                checked={makePublic}
                onChange={setMakePublic}
                ariaLabel="Make generated notes public"
              />
            </div>
            <p className="text-xs text-foreground/60">
              Make each created note public as soon as it is queued.
            </p>
          </div>

          <section className="space-y-3" aria-labelledby="bulk-topics-heading">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <h2 id="bulk-topics-heading" className="text-sm font-medium text-foreground">
                  Topics <span className="text-red-500" aria-hidden="true">*</span>
                </h2>
                <p className="mt-1 text-xs text-foreground/60">Each topic becomes a separate note. Each note&apos;s title and tags are generated automatically — the subject and other batch details apply to every note. Paste a list to add several at once — one topic per line.</p>
              </div>
              <span className={`text-sm font-medium ${
                overCap ? "text-red-600 dark:text-red-400" : "text-foreground/65"
              }`}>
                {normalizedTopics.length} / {effectiveTopicCap}
              </span>
            </div>
            {isNoteGenCapped ? (
              <p className="text-xs text-foreground/60">
                Capped by your {effectiveTopicCap} topic note{effectiveTopicCap === 1 ? "" : "s"} left this cycle.
              </p>
            ) : null}

            <div className="space-y-3">
              {topics.map((topic, index) => (
                <div key={topic.id} className="flex items-center gap-2">
                  <label htmlFor={`bulk-topic-${topic.id}`} className="sr-only">Topic {index + 1}</label>
                  <input
                    id={`bulk-topic-${topic.id}`}
                    value={topic.value}
                    onChange={(event) => updateTopic(topic.id, event.target.value)}
                    onPaste={(event) => handleTopicPaste(topic.id, event)}
                    maxLength={MAX_TOPIC_LENGTH}
                    placeholder={index === 0 ? "e.g. Newton's Laws of Motion" : `Topic ${index + 1}`}
                    className="h-11 min-w-0 flex-1 rounded-lg border border-border bg-background px-3 text-base text-foreground outline-none transition-colors placeholder:text-foreground/45 focus-visible:ring-2 focus-visible:ring-blue-600 sm:text-sm"
                  />
                  <button
                    type="button"
                    aria-label={`Remove topic ${index + 1}`}
                    disabled={topics.length === 1}
                    onClick={() => removeTopic(topic.id)}
                    className="inline-flex h-11 w-11 items-center justify-center rounded-lg border border-border text-lg text-foreground/65 transition-colors hover:bg-highlight hover:text-foreground disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    ×
                  </button>
                </div>
              ))}
            </div>

            <button
              type="button"
              onClick={addTopic}
              disabled={topics.length >= effectiveTopicCap}
              className="text-sm font-medium text-blue-600 hover:underline disabled:cursor-not-allowed disabled:text-foreground/40 disabled:no-underline dark:text-blue-400"
            >
              + Add topic
            </button>
          </section>

          {pasteNotice ? (
            <p className="text-sm text-amber-600 dark:text-amber-400">{pasteNotice}</p>
          ) : null}
          {overCap ? (
            <p className="text-sm text-red-600 dark:text-red-400">
              {isNoteGenCapped
                ? `You have ${effectiveTopicCap} topic note${effectiveTopicCap === 1 ? "" : "s"} left this cycle. Remove ${normalizedTopics.length - effectiveTopicCap} topic${normalizedTopics.length - effectiveTopicCap === 1 ? "" : "s"} to continue.`
                : `You can bulk generate up to ${MAX_BULK_GENERATION_TOPICS} topics at once.`}
            </p>
          ) : null}
          {error ? <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
        </Card>

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Link href="/library" className="inline-flex h-10 items-center justify-center rounded-lg border border-border bg-background px-4 text-sm font-medium text-foreground hover:bg-highlight">
            Cancel
          </Link>
          <Button type="submit" loading={submitting} loadingText="Queueing notes..." disabled={normalizedTopics.length === 0 || overCap}>
            Generate
          </Button>
        </div>
      </form>

      <AppModal
        isOpen={studyPackConfirmOpen}
        title="Some notes won’t get Study Packs"
        description={quota
          ? `You have ${quota.studyPackRemaining} Study Pack${quota.studyPackRemaining === 1 ? "" : "s"} left this cycle, but you’re queueing ${normalizedTopics.length}. ${studyPackShortfall} note${studyPackShortfall === 1 ? "" : "s"} will be created as drafts you can generate later. Continue?`
          : undefined}
        onClose={() => setStudyPackConfirmOpen(false)}
        actions={(
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:items-center sm:justify-end">
            <Button type="button" variant="secondary" onClick={() => setStudyPackConfirmOpen(false)}>Cancel</Button>
            <Button
              type="button"
              loading={submitting}
              loadingText="Queueing notes..."
              onClick={() => { setStudyPackConfirmOpen(false); void doSubmit(); }}
            >
              Generate anyway
            </Button>
          </div>
        )}
      />
    </main>
  );
}
