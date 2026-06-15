"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { CourseProgramCombobox } from "@/components/metadata/course-program-combobox";
import { SubjectCombobox } from "@/components/notes/subject-combobox";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Toggle } from "@/components/ui/toggle";
import {
  ApiRequestError,
  bulkGenerateNotes,
  getMe,
  listCoursePrograms,
  listSubjects,
  type BulkGenerateNotesRequest,
  type BulkGenerateNotesResponse,
  type LearnerLevel,
  type NoteTargetProfileType,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { LEARNER_LEVEL_OPTIONS } from "@/lib/learning-profile";
import {
  getNoteTargetProfileLabel,
  isTeacherSelectableNoteTarget,
  mapProfileTypeToNoteTargetProfile,
  SELECTABLE_NOTE_TARGET_PROFILE_TYPES,
} from "@/lib/note-target-profile";
import { requireAdminUser } from "@/lib/route-guards";

export const MAX_BULK_GENERATION_TITLES = 50;

const BULK_LEARNER_LEVEL_OPTIONS = LEARNER_LEVEL_OPTIONS.filter(
  (option) => option.value !== "PERSONAL_LEARNING",
);

type TitleRow = {
  id: number;
  value: string;
};

export function BulkGenerationPageClient() {
  const router = useRouter();
  const authUser = getAuthUser();
  const isAdmin = authUser?.role === "ADMIN";
  const isTeacherOrAdmin = isTeacherSelectableNoteTarget(authUser?.profileType, authUser?.role);
  const nextTitleId = useRef(2);
  const [subject, setSubject] = useState("");
  const [courseProgram, setCourseProgram] = useState("");
  const [targetProfileType, setTargetProfileType] = useState<NoteTargetProfileType | "">(
    mapProfileTypeToNoteTargetProfile(authUser?.profileType),
  );
  const [learnerLevel, setLearnerLevel] = useState<LearnerLevel | "">("COLLEGE");
  const [titles, setTitles] = useState<TitleRow[]>([{ id: 1, value: "" }]);
  const [makePublic, setMakePublic] = useState(false);
  const [subjectSuggestions, setSubjectSuggestions] = useState<string[]>([]);
  const [courseProgramSuggestions, setCourseProgramSuggestions] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [acknowledgment, setAcknowledgment] = useState<BulkGenerateNotesResponse | null>(null);
  const normalizedTitles = useMemo(
    () => titles.map((title) => title.value.trim()).filter(Boolean),
    [titles],
  );

  useEffect(() => {
    requireAdminUser(router);
  }, [router]);

  useEffect(() => {
    let active = true;
    void Promise.allSettled([
      listSubjects("mine"),
      listCoursePrograms("mine"),
      getMe(),
    ]).then(([subjectsResult, courseProgramsResult, meResult]) => {
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
        if (
          isAdmin
          && meResult.value.learnerLevel
          && meResult.value.learnerLevel !== "PERSONAL_LEARNING"
        ) {
          setLearnerLevel(meResult.value.learnerLevel);
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
    if (normalizedTitles.length === 0) {
      return "Add at least one title.";
    }
    if (normalizedTitles.length > MAX_BULK_GENERATION_TITLES) {
      return `You can bulk generate up to ${MAX_BULK_GENERATION_TITLES} titles at once.`;
    }
    if (isTeacherOrAdmin && !courseProgram.trim()) {
      return "Enter a course or program for this batch.";
    }
    if (isTeacherOrAdmin && !targetProfileType) {
      return "Select a target audience for this batch.";
    }
    if (isAdmin && !learnerLevel) {
      return "Select a learner level for this batch.";
    }
    return null;
  };

  const updateTitle = (id: number, value: string) => {
    setTitles((current) => current.map((title) => (title.id === id ? { ...title, value } : title)));
  };

  const addTitle = () => {
    setTitles((current) => [...current, { id: nextTitleId.current++, value: "" }]);
  };

  const removeTitle = (id: number) => {
    setTitles((current) => current.length === 1 ? current : current.filter((title) => title.id !== id));
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setAcknowledgment(null);
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    const request: BulkGenerateNotesRequest = {
      subject: subject.trim(),
      titles: normalizedTitles,
      makePublic,
      ...(isTeacherOrAdmin
        ? {
            courseProgram: courseProgram.trim(),
            targetProfileType: targetProfileType as NoteTargetProfileType,
          }
        : {}),
      ...(isAdmin ? { learnerLevel: learnerLevel as LearnerLevel } : {}),
    };

    setSubmitting(true);
    setError(null);
    try {
      const response = await bulkGenerateNotes(request);
      setAcknowledgment(response);
    } catch (submitError) {
      if (submitError instanceof ApiRequestError && submitError.status === 403) {
        router.replace("/dashboard");
        return;
      }
      setError(submitError instanceof Error ? submitError.message : "Could not queue these notes.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <BackLink href="/library" label="Library" />

      <header className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Admin tool
        </p>
        <h1 className="text-3xl font-semibold text-foreground">Bulk generate notes</h1>
        <p className="max-w-3xl text-sm leading-relaxed text-foreground/70">
          Add one subject and the note titles to generate. Each title becomes a note with its own Study Pack in the background.
        </p>
      </header>

      {acknowledgment ? (
        <Card className="space-y-3 border-emerald-500/40 bg-emerald-50/70 p-5 dark:bg-emerald-950/20">
          <h2 className="text-lg font-semibold text-foreground">Bulk generation queued</h2>
          <p className="text-sm text-foreground/75">
            Queued {acknowledgment.queuedTitles} note{acknowledgment.queuedTitles === 1 ? "" : "s"} for {subject.trim()}. They will appear in your Library and finish generating shortly.
          </p>
          <Link className="inline-flex text-sm font-medium text-blue-600 hover:underline dark:text-blue-400" href="/library">
            Back to Library
          </Link>
        </Card>
      ) : null}

      <form className="space-y-6" onSubmit={handleSubmit}>
        <Card className="space-y-6 p-5 sm:p-6">
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
                learnerLevel={learnerLevel}
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

          {isAdmin ? (
            <div className="space-y-2">
              <label htmlFor="bulk-learner-level" className="text-sm font-medium text-foreground">
                Learner Level <span className="text-red-500" aria-hidden="true">*</span>
              </label>
              <select
                id="bulk-learner-level"
                value={learnerLevel}
                onChange={(event) => setLearnerLevel(event.target.value as LearnerLevel | "")}
                className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus-visible:ring-2 focus-visible:ring-blue-600"
              >
                <option value="">Select a learner level</option>
                {BULK_LEARNER_LEVEL_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
              <p className="text-xs text-foreground/60">
                Sets the generation difficulty for note content and Study Packs.
              </p>
            </div>
          ) : null}

          <section className="space-y-3" aria-labelledby="bulk-titles-heading">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <h2 id="bulk-titles-heading" className="text-sm font-medium text-foreground">
                  Titles <span className="text-red-500" aria-hidden="true">*</span>
                </h2>
                <p className="mt-1 text-xs text-foreground/60">Each title becomes a separate note.</p>
              </div>
              <span className={`text-sm font-medium ${
                normalizedTitles.length > MAX_BULK_GENERATION_TITLES
                  ? "text-red-600 dark:text-red-400"
                  : "text-foreground/65"
              }`}>
                {normalizedTitles.length} / {MAX_BULK_GENERATION_TITLES}
              </span>
            </div>

            <div className="space-y-3">
              {titles.map((title, index) => (
                <div key={title.id} className="flex items-center gap-2">
                  <label htmlFor={`bulk-title-${title.id}`} className="sr-only">Title {index + 1}</label>
                  <input
                    id={`bulk-title-${title.id}`}
                    value={title.value}
                    onChange={(event) => updateTitle(title.id, event.target.value)}
                    maxLength={160}
                    placeholder={`Note title ${index + 1}`}
                    className="h-11 min-w-0 flex-1 rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus-visible:ring-2 focus-visible:ring-blue-600"
                  />
                  <button
                    type="button"
                    aria-label={`Remove title ${index + 1}`}
                    disabled={titles.length === 1}
                    onClick={() => removeTitle(title.id)}
                    className="inline-flex h-11 w-11 items-center justify-center rounded-lg border border-border text-lg text-foreground/65 transition-colors hover:bg-highlight hover:text-foreground disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    ×
                  </button>
                </div>
              ))}
            </div>

            <button
              type="button"
              onClick={addTitle}
              className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400"
            >
              + Add title
            </button>
          </section>

          <div className="flex items-center justify-between gap-4 rounded-lg border border-border bg-background p-4">
            <div>
              <label htmlFor="bulk-make-public" className="text-sm font-medium text-foreground">Public</label>
              <p className="mt-1 text-xs text-foreground/60">
                Make each created note public as soon as it is queued.
              </p>
            </div>
            <Toggle
              id="bulk-make-public"
              checked={makePublic}
              onChange={setMakePublic}
              ariaLabel="Make generated notes public"
            />
          </div>

          {normalizedTitles.length > MAX_BULK_GENERATION_TITLES ? (
            <p className="text-sm text-red-600 dark:text-red-400">
              You can bulk generate up to {MAX_BULK_GENERATION_TITLES} titles at once.
            </p>
          ) : null}
          {error ? <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
        </Card>

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Link href="/library" className="inline-flex h-10 items-center justify-center rounded-lg border border-border bg-background px-4 text-sm font-medium text-foreground hover:bg-highlight">
            Cancel
          </Link>
          <Button type="submit" loading={submitting} loadingText="Queueing notes...">
            Queue {normalizedTitles.length} note{normalizedTitles.length === 1 ? "" : "s"}
          </Button>
        </div>
      </form>
    </main>
  );
}
