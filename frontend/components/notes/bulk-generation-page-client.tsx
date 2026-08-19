"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { CourseProgramCombobox } from "@/components/metadata/course-program-combobox";
import { ApplicableProgramsCombobox } from "@/components/metadata/applicable-programs-combobox";
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
  getCourseProgramCatalog,
  getMyPlan,
  listCoursePrograms,
  listCollections,
  listSubjects,
  type BulkGenerateNotesRequest,
  type CourseProgramCatalogItem,
  type DomainContext,
  type LearnerLevel,
  type NoteCollectionSummary,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { consumeBulkGenerationRetryStash, setBulkQueuedFlash } from "@/lib/bulk-generation-flash";
import { MAX_TOPIC_LENGTH, parsePastedTopics } from "@/lib/bulk-topics";
import {
  isTeacherSelectableNoteTarget,
} from "@/lib/note-target-profile";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { DOMAIN_CONTEXT_OPTIONS, getDomainContextDescription } from "@/lib/domain-context";
import { LEARNER_LEVEL_OPTIONS } from "@/lib/learning-profile";
import { getCollectionLabels, isReservedSectionName, UNGROUPED_SECTION_NAME } from "@/lib/collection-labels";

export const MAX_BULK_GENERATION_TOPICS = 50;
const SECTION_MAX_LENGTH = 120;

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
  // This selector renders only for teachers/admins, and a TEACHER profile calls these
  // "Lesson Plans" everywhere else in the app — hardcoding "Review Set" here would split
  // the vocabulary on a primary curator surface.
  const collectionLabels = getCollectionLabels(authUser?.profileType);
  const nextTopicId = useRef(2);
  const [subject, setSubject] = useState("");
  const [courseProgram, setCourseProgram] = useState("");
  const [courseProgramIds, setCourseProgramIds] = useState<string[]>([]);
  const [courseProgramCatalog, setCourseProgramCatalog] = useState<CourseProgramCatalogItem[]>([]);
  const [courseProgramCatalogLoading, setCourseProgramCatalogLoading] = useState(false);
  const [courseProgramCatalogError, setCourseProgramCatalogError] = useState<string | null>(null);
  const [courseProgramCatalogRetry, setCourseProgramCatalogRetry] = useState(0);
  const [domainContext, setDomainContext] = useState<DomainContext | "">("");
  const [learnerLevel, setLearnerLevel] = useState<LearnerLevel | "">("");
  // Authored Depth precedence, ADR-001's chain: Review Set -> author profile -> explicit
  // override. These are REFS, not state, for two reasons. (1) Nothing renders from them.
  // (2) The profile load is async, so a state closure would capture provenance as it was
  // when its effect ran — and the retry stash resolves SYNCHRONOUSLY on mount, before
  // getMe settles. Reading a stale "none" there is exactly how a stashed level got
  // overwritten by the profile.
  //
  // The rule these encode, stated once so it is testable:
  //   - "user"       an explicit curator choice, INCLUDING an explicit blank. Never overwritten.
  //   - "collection" came from the selected Review Set.
  //   - "profile"    came from the author's own level.
  //   - "none"       nothing has set it.
  // A Review Set selection replaces anything that is not "user" — including clearing to
  // the profile level when the newly selected set carries no depth of its own.
  const learnerLevelSourceRef = useRef<"none" | "profile" | "collection" | "user">("none");
  const profileLearnerLevelRef = useRef<LearnerLevel | "">("");

  // Single writer for the depth control: sets value and provenance together, as plain
  // values. Never call a setter inside another setter's updater — React requires updaters
  // to be pure, and this logic carries the whole precedence guarantee.
  const applyLearnerLevel = useCallback(
    (next: LearnerLevel | "", source: "none" | "profile" | "collection" | "user") => {
      learnerLevelSourceRef.current = source;
      setLearnerLevel(next);
    },
    [],
  );
  const [collectionId, setCollectionId] = useState("");
  const [sectionLabel, setSectionLabel] = useState("");
  const [sectionTouched, setSectionTouched] = useState(false);
  const [sectionError, setSectionError] = useState<string | null>(null);
  const [subjectError, setSubjectError] = useState<string | null>(null);
  const [courseProgramError, setCourseProgramError] = useState<string | null>(null);
  const [collections, setCollections] = useState<NoteCollectionSummary[]>([]);
  const [collectionsLoading, setCollectionsLoading] = useState(false);
  const [collectionsError, setCollectionsError] = useState<string | null>(null);
  const [collectionsRetry, setCollectionsRetry] = useState(0);
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
    // A retry stash holds what the curator already submitted, so it counts as explicit —
    // UNCONDITIONALLY, including a stashed blank. Marking provenance only for a non-empty
    // value made a deliberate "no depth" indistinguishable from untouched, so the profile
    // pre-fill overwrote it and the retried notes were authored at a different depth than
    // the batch they replaced.
    applyLearnerLevel((stash.learnerLevel ?? "") as LearnerLevel | "", "user");
    setCollectionId(stash.collectionId ?? "");
    // Never fall back to the subject here. No producer writes sectionLabel today, so a
    // subject fallback would section every retried batch by subject -- including batches whose
    // curator deliberately left the section blank, inventing an assignment they never made.
    // Restoring empty keeps the retry honest; an explicitly carried section suppresses subject
    // tracking so editing the subject cannot overwrite it.
    setSectionLabel(typeof stash.sectionLabel === "string" ? stash.sectionLabel.slice(0, SECTION_MAX_LENGTH) : "");
    setSectionTouched(typeof stash.sectionLabel === "string");
    setMakePublic(stash.makePublic);
    setTopics(stash.topics.map((value, index) => ({ id: index + 1, value })));
    nextTopicId.current = stash.topics.length + 1;
  }, [applyLearnerLevel]);

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
        setCourseProgram((current) => current || meResult.value.courseProgram || "");
        // Authored Depth falls back to the author's own profile level (ADR-001's weak leg).
        // Pre-fill only, never a server-side default: it lands in a visible control the
        // curator can change before submitting. Remembered so that deselecting a Review
        // Set falls back down the chain rather than clearing to nothing.
        const profileLearnerLevel = (meResult.value.learnerLevel ?? "") as LearnerLevel | "";
        profileLearnerLevelRef.current = profileLearnerLevel;
        // Gate on PROVENANCE, not on the control being non-empty: an explicit blank is a
        // real choice and must not read as "untouched".
        if (profileLearnerLevel && learnerLevelSourceRef.current === "none") {
          applyLearnerLevel(profileLearnerLevel, "profile");
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
  }, [applyLearnerLevel, isAdmin, isTeacherOrAdmin]);

  useEffect(() => {
    if (!isTeacherOrAdmin) {
      return;
    }
    let active = true;
    setCourseProgramCatalogLoading(true);
    setCourseProgramCatalogError(null);
    void getCourseProgramCatalog()
      .then((catalog) => {
        if (active) setCourseProgramCatalog(catalog);
      })
      .catch((catalogError) => {
        if (active) setCourseProgramCatalogError(catalogError instanceof Error ? catalogError.message : "Could not load course programs.");
      })
      .finally(() => {
        if (active) setCourseProgramCatalogLoading(false);
      });
    return () => { active = false; };
  }, [courseProgramCatalogRetry, isTeacherOrAdmin]);

  useEffect(() => {
    if (!isTeacherOrAdmin) {
      return;
    }
    let active = true;
    setCollectionsLoading(true);
    setCollectionsError(null);
    void listCollections({ noteAccepting: true })
      .then((loadedCollections) => {
        if (active) setCollections(loadedCollections);
      })
      .catch((collectionsLoadError) => {
        if (active) {
          setCollectionsError(
            collectionsLoadError instanceof Error
              ? collectionsLoadError.message
              : `Could not load ${collectionLabels.plural}.`,
          );
        }
      })
      .finally(() => {
        if (active) setCollectionsLoading(false);
      });
    return () => { active = false; };
  }, [collectionLabels.plural, collectionsRetry, isTeacherOrAdmin]);

  const handleCollectionChange = (selectedCollectionId: string) => {
    setCollectionId(selectedCollectionId);
    // An explicit curator choice outranks every inference, so leave it alone entirely.
    if (learnerLevelSourceRef.current === "user") {
      return;
    }
    // Otherwise re-resolve the whole chain rather than only filling an empty control.
    // Only acting when the NEW set has a level left the PREVIOUS set's depth displayed
    // and submitted after a switch or a clear — the batch would be authored at a depth
    // belonging to a Review Set it was no longer going into.
    const selectedCollection = collections.find((collection) => collection.id === selectedCollectionId);
    const fromCollection = selectedCollection?.resolvedLearnerLevel ?? "";
    if (fromCollection) {
      applyLearnerLevel(fromCollection, "collection");
      return;
    }
    const fromProfile = profileLearnerLevelRef.current;
    applyLearnerLevel(fromProfile, fromProfile ? "profile" : "none");
  };

  const handleSubjectChange = (nextSubject: string) => {
    setSubject(nextSubject);
    if (!sectionTouched) {
      setSectionLabel(nextSubject.slice(0, SECTION_MAX_LENGTH));
    }
  };

  useEffect(() => {
    if (!isTeacherOrAdmin || courseProgramIds.length > 0 || !courseProgram.trim()) {
      return;
    }
    const matchingProgram = courseProgramCatalog.find((program) => program.name === courseProgram.trim());
    if (matchingProgram) {
      setCourseProgramIds([matchingProgram.id]);
    }
  }, [courseProgram, courseProgramCatalog, courseProgramIds.length, isTeacherOrAdmin]);

  const validate = (): string | null => {
    if (collectionId && sectionLabel.trim() && isReservedSectionName(sectionLabel)) {
      // The bucket's display string doubles as the reserved name. Without this guard a batch could
      // mint a real section that renders identically to the synthetic bucket -- the exact collision
      // the Builder already refuses, reachable through the path this release added.
      return `"${UNGROUPED_SECTION_NAME}" is reserved for notes without a ${collectionLabels.sectionSingular.toLowerCase()}.`;
    }
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
    if (isTeacherOrAdmin && courseProgramIds.length === 0) {
      return "Choose at least one course or program for this batch.";
    }
    if (!isTeacherOrAdmin && !courseProgram.trim()) {
      return "Enter a course or program for this batch.";
    }
    if (isTeacherOrAdmin && courseProgramIds.length > 1 && !domainContext) {
      return "A note shared across several programs needs a Domain Context, so the AI knows which academic domain to write in.";
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
            courseProgramIds,
            domainContext: domainContext || null,
            learnerLevel: learnerLevel || null,
            ...(collectionId ? { collectionId } : {}),
            ...(collectionId && sectionLabel.trim() ? { sectionLabel: sectionLabel.trim() } : {}),
          }
        : { courseProgramText: courseProgram.trim() }),
    };

    setSubmitting(true);
    setError(null);
    setSectionError(null);
    setSubjectError(null);
    setCourseProgramError(null);
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
      const message = submitError instanceof Error ? submitError.message : "Could not queue these notes.";
      if (message.includes("Subject must be 64 characters or less")) {
        setSubjectError(message);
      } else if (message.includes("Course/program must be 120 characters or less")) {
        setCourseProgramError(message);
      } else if (message.includes("Section must be 120 characters or less")) {
        setSectionError(message);
      } else {
        setError(message);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const validationError = validate();
    if (validationError) {
      // Route a section-field problem to the field, not the page banner -- the banner sits far from
      // the input and does not tell the curator which control to fix.
      if (collectionId && sectionLabel.trim() && isReservedSectionName(sectionLabel)) {
        setSectionError(validationError);
      } else {
        setError(validationError);
      }
      return;
    }
    setError(null);
    setSectionError(null);
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
              onChange={(value) => {
                setSubjectError(null);
                handleSubjectChange(value);
              }}
              maxLength={64}
            />
            {subjectError ? <p role="alert" className="text-xs text-red-600 dark:text-red-400">{subjectError}</p> : null}
          </div>

          <div data-testid="bulk-metadata-grid" className="grid gap-4 empty:hidden sm:grid-cols-2">
            {isTeacherOrAdmin ? (
              <div className="space-y-2">
                <label htmlFor="bulk-review-set" className="text-sm font-medium text-foreground">
                  {collectionLabels.singular} (optional)
                </label>
                <select
                  id="bulk-review-set"
                  value={collectionId}
                  disabled={collectionsLoading}
                  onChange={(event) => handleCollectionChange(event.target.value)}
                  className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus-visible:ring-2 focus-visible:ring-blue-600 disabled:opacity-60"
                >
                  <option value="">{collectionsLoading ? `Loading ${collectionLabels.plural}...` : `No ${collectionLabels.singular}`}</option>
                  {collections.map((collection) => (
                    <option key={collection.id} value={collection.id}>{collection.title}</option>
                  ))}
                </select>
                <p className="text-xs text-foreground/60">
                  Generated notes will be added here when the batch finishes. Its authored depth can pre-fill the level below.
                </p>
                {collectionsError ? (
                  <div className="space-y-1" role="alert">
                    <p className="text-xs text-red-600 dark:text-red-400">{collectionsError}</p>
                    <button
                      type="button"
                      onClick={() => setCollectionsRetry((value) => value + 1)}
                      className="text-xs font-medium text-blue-600 hover:underline dark:text-blue-400"
                    >
                      Retry {collectionLabels.plural}
                    </button>
                  </div>
                ) : null}
              </div>
            ) : null}
            {isTeacherOrAdmin && collectionId ? (
              <div className="space-y-2">
                <label htmlFor="bulk-section" className="text-sm font-medium text-foreground">
                  {collectionLabels.sectionSingular} (optional)
                </label>
                <input
                  id="bulk-section"
                  value={sectionLabel}
                  maxLength={SECTION_MAX_LENGTH}
                  onChange={(event) => {
                    setSectionTouched(true);
                    setSectionError(null);
                    setSectionLabel(event.target.value);
                  }}
                  className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus-visible:ring-2 focus-visible:ring-blue-600"
                  placeholder={`Add to a ${collectionLabels.sectionSingular.toLowerCase()}`}
                />
                <p className="text-xs text-foreground/60">
                  Pre-filled from the subject. Edit it to choose where these notes appear.
                </p>
                {sectionError ? <p role="alert" className="text-xs text-red-600 dark:text-red-400">{sectionError}</p> : null}
              </div>
            ) : null}
            {isTeacherOrAdmin ? (
              <div className="space-y-2">
                <label htmlFor="bulk-course-program" className="text-sm font-medium text-foreground">
                  Course / Program(s) <span className="text-red-500" aria-hidden="true">*</span>
                </label>
                <p className="text-xs text-foreground/60">
                  Choose one or more programs this note applies to. Adding multiple programs lets one note serve several curricula instead of creating duplicates.
                </p>
                <ApplicableProgramsCombobox
                  id="bulk-course-program"
                  catalog={courseProgramCatalog}
                  selectedIds={courseProgramIds}
                  onChange={setCourseProgramIds}
                  canCreateCatalogProgram={isAdmin}
                  onCatalogProgramCreated={(program) => setCourseProgramCatalog((current) => [...current, program])}
                  loading={courseProgramCatalogLoading}
                  error={courseProgramCatalogError}
                  onRetry={() => setCourseProgramCatalogRetry((value) => value + 1)}
                />
              </div>
            ) : (
              <div className="space-y-2">
                <label htmlFor="bulk-course-program" className="text-sm font-medium text-foreground">
                  Course / Program(s) <span className="text-red-500" aria-hidden="true">*</span>
                </label>
                <CourseProgramCombobox
                  id="bulk-course-program"
                  value={courseProgram}
                  suggestions={courseProgramSuggestions}
                  onChange={(value) => {
                    setCourseProgramError(null);
                    setCourseProgram(value);
                  }}
                />
                {courseProgramError ? <p role="alert" className="text-xs text-red-600 dark:text-red-400">{courseProgramError}</p> : null}
              </div>
            )}

            {isTeacherOrAdmin ? (
              <div className="space-y-2">
                <label htmlFor="bulk-domain-context" className="text-sm font-medium text-foreground">
                  Domain Context {courseProgramIds.length > 1 ? <span className="text-red-500" aria-hidden="true">*</span> : "(optional)"}
                </label>
                <select
                  id="bulk-domain-context"
                  value={domainContext}
                  onChange={(event) => setDomainContext(event.target.value as DomainContext | "")}
                  className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus-visible:ring-2 focus-visible:ring-blue-600"
                >
                  <option value="">Automatic — based on the program</option>
                  {DOMAIN_CONTEXT_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
                {getDomainContextDescription(domainContext) ? (
                  <p className="text-xs text-foreground/70">
                    {getDomainContextDescription(domainContext)}
                  </p>
                ) : null}
                <p className="text-xs text-foreground/60">
                  {courseProgramIds.length > 1
                    ? "You've added more than one program. Choose the academic domain this note should be written in — it tells the AI how to write it, while the programs decide who finds it."
                    : "Required when this note applies to more than one program."}
                </p>
              </div>
            ) : null}

            {isTeacherOrAdmin ? (
              <div className="space-y-2">
                <label htmlFor="bulk-learner-level" className="text-sm font-medium text-foreground">
                  Authored Depth (optional)
                </label>
                <select
                  id="bulk-learner-level"
                  value={learnerLevel}
                  onChange={(event) => applyLearnerLevel(event.target.value as LearnerLevel | "", "user")}
                  className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus-visible:ring-2 focus-visible:ring-blue-600"
                >
                  <option value="">Automatic — based on the reader</option>
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
