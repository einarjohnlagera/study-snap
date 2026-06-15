"use client";

import Link from "next/link";
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  ApiRequestError,
  bulkGenerateNotes,
  type BulkGenerateNotesResponse,
  type LearnerLevel,
} from "@/lib/api";
import {
  MAX_BULK_GENERATION_TITLES,
  parseBulkGenerationText,
} from "@/lib/bulk-generation";
import { LEARNER_LEVEL_OPTIONS } from "@/lib/learning-profile";
import { requireAdminUser } from "@/lib/route-guards";

const BULK_TARGET_AUDIENCE_OPTIONS = LEARNER_LEVEL_OPTIONS.filter(
  (option) => option.value !== "PERSONAL_LEARNING",
);

export function BulkGenerationPageClient() {
  const router = useRouter();
  const [rawText, setRawText] = useState("");
  const [courseProgram, setCourseProgram] = useState("");
  const [targetAudience, setTargetAudience] = useState<LearnerLevel>("BOARD_EXAM_REVIEW");
  const [makePublic, setMakePublic] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [acknowledgment, setAcknowledgment] = useState<BulkGenerateNotesResponse | null>(null);
  const parsed = useMemo(() => parseBulkGenerationText(rawText), [rawText]);

  useEffect(() => {
    requireAdminUser(router);
  }, [router]);

  const validate = (): string | null => {
    if (parsed.totalTitles === 0) {
      return "Add at least one title under a Subject: heading.";
    }
    if (parsed.totalTitles > MAX_BULK_GENERATION_TITLES) {
      return `You can bulk generate up to ${MAX_BULK_GENERATION_TITLES} titles at once.`;
    }
    if (!courseProgram.trim()) {
      return "Enter a course or program for this batch.";
    }
    return null;
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setAcknowledgment(null);
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const response = await bulkGenerateNotes({
        courseProgram: courseProgram.trim(),
        targetAudience,
        makePublic,
        groups: parsed.groups,
      });
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
          Paste titles grouped under Subject: headings. Each title will become a note and generate its Study Pack in the background.
        </p>
      </header>

      {acknowledgment ? (
        <Card className="space-y-3 border-emerald-500/40 bg-emerald-50/70 p-5 dark:bg-emerald-950/20">
          <h2 className="text-lg font-semibold text-foreground">Bulk generation queued</h2>
          <p className="text-sm text-foreground/75">
            Queued {acknowledgment.queuedTitles} note{acknowledgment.queuedTitles === 1 ? "" : "s"} across {acknowledgment.subjectCount} subject{acknowledgment.subjectCount === 1 ? "" : "s"}. They will appear in your Library and finish generating shortly.
          </p>
          <Link className="inline-flex text-sm font-medium text-blue-600 hover:underline dark:text-blue-400" href="/library">
            Back to Library
          </Link>
        </Card>
      ) : null}

      <form className="space-y-6" onSubmit={handleSubmit}>
        <Card className="space-y-5 p-5 sm:p-6">
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="space-y-2 text-sm font-medium text-foreground" htmlFor="bulk-course-program">
              <span>Course / Program</span>
              <input
                id="bulk-course-program"
                value={courseProgram}
                onChange={(event) => setCourseProgram(event.target.value)}
                placeholder="e.g. Nursing, Architecture, Education"
                className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm font-normal outline-none focus:ring-2 focus:ring-blue-600"
              />
            </label>

            <label className="space-y-2 text-sm font-medium text-foreground" htmlFor="bulk-target-audience">
              <span>Target Audience</span>
              <select
                id="bulk-target-audience"
                value={targetAudience}
                onChange={(event) => setTargetAudience(event.target.value as LearnerLevel)}
                className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm font-normal outline-none focus:ring-2 focus:ring-blue-600"
              >
                {BULK_TARGET_AUDIENCE_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
            </label>
          </div>

          <label className="flex items-start gap-3 rounded-lg border border-border bg-background p-3 text-sm" htmlFor="bulk-make-public">
            <input
              id="bulk-make-public"
              type="checkbox"
              checked={makePublic}
              onChange={(event) => setMakePublic(event.target.checked)}
              className="mt-0.5 h-4 w-4"
            />
            <span>
              <span className="block font-medium text-foreground">Public</span>
              <span className="block text-foreground/60">Make each created note public as soon as it is queued.</span>
            </span>
          </label>

          <label className="space-y-2 text-sm font-medium text-foreground" htmlFor="bulk-title-list">
            <span>Subject-grouped titles</span>
            <textarea
              id="bulk-title-list"
              value={rawText}
              onChange={(event) => setRawText(event.target.value)}
              rows={14}
              placeholder={`Subject: Maternal Health\nPrenatal Care\nStages of Labor\n\nSubject: Community Health\nEpidemiology Basics`}
              className="w-full rounded-lg border border-border bg-background px-3 py-2 font-mono text-sm font-normal leading-6 outline-none focus:ring-2 focus:ring-blue-600"
            />
          </label>

          {parsed.ignoredLineCount > 0 ? (
            <p className="text-sm text-amber-700 dark:text-amber-300">
              Ignored {parsed.ignoredLineCount} non-empty line{parsed.ignoredLineCount === 1 ? "" : "s"} that appeared before a valid Subject: heading.
            </p>
          ) : null}
          {parsed.totalTitles > MAX_BULK_GENERATION_TITLES ? (
            <p className="text-sm text-red-600 dark:text-red-400">
              You can bulk generate up to {MAX_BULK_GENERATION_TITLES} titles at once.
            </p>
          ) : null}
          {error ? <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
        </Card>

        <Card className="space-y-4 p-5 sm:p-6">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-lg font-semibold text-foreground">Parsed preview</h2>
            <span className="text-sm font-medium text-foreground/65">
              {parsed.totalTitles} / {MAX_BULK_GENERATION_TITLES} titles
            </span>
          </div>
          {parsed.groups.length === 0 ? (
            <p className="text-sm text-foreground/60">No subject groups detected yet.</p>
          ) : (
            <div className="space-y-4">
              {parsed.groups.map((group, groupIndex) => (
                <section key={`${group.subject}-${groupIndex}`} className="rounded-lg border border-border bg-background p-4">
                  <h3 className="font-semibold text-foreground">{group.subject}</h3>
                  <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-foreground/70">
                    {group.titles.map((title, titleIndex) => (
                      <li key={`${title}-${titleIndex}`}>{title}</li>
                    ))}
                  </ul>
                </section>
              ))}
            </div>
          )}
        </Card>

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Link href="/library" className="inline-flex h-10 items-center justify-center rounded-lg border border-border bg-background px-4 text-sm font-medium text-foreground hover:bg-highlight">
            Cancel
          </Link>
          <Button type="submit" loading={submitting} loadingText="Queueing notes...">
            Queue {parsed.totalTitles} note{parsed.totalTitles === 1 ? "" : "s"}
          </Button>
        </div>
      </form>
    </main>
  );
}
