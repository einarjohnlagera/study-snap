"use client";

import Link from "next/link";
import { Check } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { CampaignOptionRow } from "@/components/feedback/campaign-option-row";
import {
  ApiRequestError,
  getCampaignFeedbackStatus,
  submitCampaignFeedback,
  type CampaignPlanIssue,
  type CampaignPrimaryBlocker,
  type CampaignQuizIssue,
  type SubmitCampaignFeedbackRequest,
} from "@/lib/api";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { cn } from "@/lib/utils";

type PageState = "loading" | "form" | "thank-you" | "already-responded" | "closed";
type ErrorTarget = "primary" | "quiz" | "plan" | "missingFeature" | "contentSubject" | "freeText" | "form";
type FormError = { target: ErrorTarget; message: string };

const PRIMARY_OPTIONS: ReadonlyArray<{ value: CampaignPrimaryBlocker; label: string }> = [
  { value: "HARD_TO_KNOW_WHAT_TO_STUDY", label: "It's hard to know what to study or do next" },
  { value: "TOO_MANY_STEPS", label: "Studying takes too many steps" },
  { value: "QUIZ_QUALITY", label: "The quiz questions could be better" },
  { value: "WANT_MORE_PRACTICE", label: "I want more practice questions" },
  { value: "CANT_FIND_CONTENT", label: "I can't find enough content for what I'm studying" },
  { value: "MISSING_FEATURE", label: "A feature I need is missing" },
  { value: "PRICING", label: "The paid plans aren't right for me" },
  { value: "SOMETHING_ELSE", label: "Something else" },
  { value: "NOTHING_MAJOR", label: "Nothing major — NoteLib works well for me" },
];

const QUIZ_OPTIONS: ReadonlyArray<{ value: CampaignQuizIssue; label: string }> = [
  { value: "ANSWERS_SEEM_INCORRECT", label: "Some questions or answers seem incorrect" },
  { value: "TOO_EASY", label: "Too easy" },
  { value: "TOO_DIFFICULT", label: "Too difficult" },
  { value: "NOT_RELEVANT", label: "Not relevant enough" },
  { value: "TOO_REPETITIVE", label: "Too repetitive" },
  { value: "EXPLANATIONS_NOT_HELPFUL", label: "The explanations aren't helpful enough" },
  { value: "SOMETHING_ELSE", label: "Something else" },
];

const PLAN_OPTIONS: ReadonlyArray<{ value: CampaignPlanIssue; label: string }> = [
  { value: "CANT_AFFORD", label: "I can't comfortably afford it" },
  { value: "NOT_ENOUGH_VALUE", label: "I don't see enough value to pay for it" },
  { value: "HAPPY_WITH_FREE", label: "I'm happy with the free version" },
  { value: "DONT_UNDERSTAND_UPGRADE", label: "I don't understand what I'd get by upgrading" },
  { value: "SOMETHING_ELSE", label: "Something else" },
];

function validationTarget(message: string): ErrorTarget {
  if (message.includes("quizIssues")) return "quiz";
  if (message.includes("planIssue")) return "plan";
  if (message.includes("missingFeatureText")) return "missingFeature";
  if (message.includes("contentSubjectText")) return "contentSubject";
  if (message.includes("freeText")) return "freeText";
  if (message.includes("primaryBlocker")) return "primary";
  return "form";
}

function FieldError({ error, target }: Readonly<{ error: FormError | null; target: ErrorTarget }>) {
  if (error?.target !== target) return null;
  return <p role="alert" className="text-sm text-red-700 dark:text-red-300">{error.message}</p>;
}

function TerminalState({ state }: Readonly<{ state: Exclude<PageState, "loading" | "form"> }>) {
  const copy = state === "thank-you"
    ? {
        title: "Thanks for helping us improve NoteLib 💙",
        body: "Your feedback has been sent. We read these responses when deciding what to improve next.",
      }
    : state === "already-responded"
      ? {
          title: "You've already shared your feedback 💙",
          body: "Thanks — we've got your response. If something else comes up, you can always use Send Feedback.",
        }
      : {
          title: "This feedback campaign has ended.",
          body: "Thanks to everyone who shared feedback. If there's something you'd like us to know, you can still use Send Feedback.",
        };
  return (
    <Card className="mx-auto max-w-2xl space-y-5 p-6 text-center sm:p-8">
      <div className="space-y-2">
        <h2 className="text-xl font-semibold text-foreground">{copy.title}</h2>
        <p className="text-sm leading-relaxed text-foreground/70">{copy.body}</p>
      </div>
      <Link href="/dashboard" className={buttonVariants({ variant: "default" })}>Back to studying</Link>
    </Card>
  );
}

function FormSkeleton() {
  return (
    <Card className="mx-auto max-w-2xl space-y-4 p-4 sm:p-6" aria-label="Loading feedback form">
      <Skeleton className="h-7 w-4/5" />
      <Skeleton className="h-4 w-40" />
      {Array.from({ length: 6 }, (_, index) => <Skeleton key={index} className="h-12 w-full" />)}
      <Skeleton className="h-32 w-full" />
    </Card>
  );
}

export function CampaignFeedbackPageClient() {
  const router = useRouter();
  const primaryQuestionId = useId();
  const quizQuestionId = useId();
  const planQuestionId = useId();
  const planRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const [pageState, setPageState] = useState<PageState>("loading");
  const [primaryBlockers, setPrimaryBlockers] = useState<CampaignPrimaryBlocker[]>([]);
  const [quizIssues, setQuizIssues] = useState<CampaignQuizIssue[]>([]);
  const [planIssue, setPlanIssue] = useState<CampaignPlanIssue | null>(null);
  const [missingFeatureText, setMissingFeatureText] = useState("");
  const [contentSubjectText, setContentSubjectText] = useState("");
  const [freeText, setFreeText] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<FormError | null>(null);

  useEffect(() => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
    let active = true;
    void getCampaignFeedbackStatus()
      .then((status) => {
        if (!active) return;
        if (status.submitted) setPageState("already-responded");
        else if (!status.campaignOpen) setPageState("closed");
        else setPageState("form");
      })
      .catch(() => {
        if (active) setPageState("form");
      });
    return () => { active = false; };
  }, [router]);

  const selected = (value: CampaignPrimaryBlocker) => primaryBlockers.includes(value);
  const togglePrimary = (value: CampaignPrimaryBlocker) => {
    if (value === "NOTHING_MAJOR" && !selected(value)) {
      setQuizIssues([]);
      setPlanIssue(null);
      setMissingFeatureText("");
      setContentSubjectText("");
    }
    setPrimaryBlockers((current) => {
      if (current.includes(value)) return current.filter((item) => item !== value);
      if (value === "NOTHING_MAJOR") return [value];
      return [...current.filter((item) => item !== "NOTHING_MAJOR"), value];
    });
  };
  const toggleQuizIssue = (value: CampaignQuizIssue) => {
    setQuizIssues((current) => current.includes(value)
      ? current.filter((item) => item !== value)
      : [...current, value]);
  };

  const handlePlanKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (!["ArrowDown", "ArrowRight", "ArrowUp", "ArrowLeft"].includes(event.key)) return;
    event.preventDefault();
    const currentIndex = Math.max(0, PLAN_OPTIONS.findIndex((option) => option.value === planIssue));
    const direction = event.key === "ArrowDown" || event.key === "ArrowRight" ? 1 : -1;
    const nextIndex = (currentIndex + direction + PLAN_OPTIONS.length) % PLAN_OPTIONS.length;
    setPlanIssue(PLAN_OPTIONS[nextIndex].value);
    planRefs.current[nextIndex]?.focus();
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (primaryBlockers.length === 0 && freeText.trim().length === 0) {
      return;
    }
    setSubmitting(true);
    setError(null);
    const payload: SubmitCampaignFeedbackRequest = {
      primaryBlockers,
      quizIssues: selected("QUIZ_QUALITY") ? quizIssues : [],
      planIssue: selected("PRICING") ? planIssue : null,
      missingFeatureText: selected("MISSING_FEATURE") && missingFeatureText.trim() ? missingFeatureText.trim() : null,
      contentSubjectText: selected("CANT_FIND_CONTENT") && contentSubjectText.trim() ? contentSubjectText.trim() : null,
      freeText: freeText.trim() || null,
    };
    try {
      await submitCampaignFeedback(payload);
      setPageState("thank-you");
    } catch (submissionError) {
      if (submissionError instanceof ApiRequestError && submissionError.status === 409) {
        setPageState("closed");
      } else if (submissionError instanceof ApiRequestError && submissionError.status === 400) {
        setError({ target: validationTarget(submissionError.message), message: submissionError.message });
      } else {
        setError({
          target: "form",
          message: submissionError instanceof ApiRequestError
            ? submissionError.message
            : "Could not send feedback. Please try again.",
        });
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (pageState === "loading") return <FormSkeleton />;
  if (pageState !== "form") return <TerminalState state={pageState} />;

  return (
    <Card className="mx-auto max-w-2xl p-4 sm:p-6">
      <form className="space-y-7" onSubmit={(event) => void handleSubmit(event)} noValidate>
        <section className="space-y-3">
          <div>
            <h2 id={primaryQuestionId} className="text-lg font-semibold text-foreground">
              What gets in the way when you study with NoteLib?
            </h2>
            <p className="text-sm text-foreground/60">Choose any that apply.</p>
          </div>
          <div role="group" aria-labelledby={primaryQuestionId} className="space-y-2">
            {PRIMARY_OPTIONS.map((option) => (
              <div key={option.value} className="space-y-3">
                <CampaignOptionRow
                  checked={selected(option.value)}
                  label={option.label}
                  onToggle={() => togglePrimary(option.value)}
                />
                {option.value === "QUIZ_QUALITY" && selected(option.value) ? (
                  <div className="ml-4 space-y-3 border-l-2 border-blue-200 pl-4 dark:border-blue-900">
                    <div>
                      <h3 id={quizQuestionId} className="text-sm font-semibold">{"What's not working with the questions?"}</h3>
                      <p className="text-xs text-foreground/60">Choose any that apply.</p>
                    </div>
                    <div role="group" aria-labelledby={quizQuestionId} className="space-y-2">
                      {QUIZ_OPTIONS.map((quizOption) => (
                        <CampaignOptionRow
                          key={quizOption.value}
                          checked={quizIssues.includes(quizOption.value)}
                          label={quizOption.label}
                          onToggle={() => toggleQuizIssue(quizOption.value)}
                        />
                      ))}
                    </div>
                    <FieldError error={error} target="quiz" />
                  </div>
                ) : null}
                {option.value === "PRICING" && selected(option.value) ? (
                  <div className="ml-4 space-y-3 border-l-2 border-blue-200 pl-4 dark:border-blue-900">
                    <h3 id={planQuestionId} className="text-sm font-semibold">What best describes it?</h3>
                    <div role="radiogroup" aria-labelledby={planQuestionId} onKeyDown={handlePlanKeyDown} className="space-y-2">
                      {PLAN_OPTIONS.map((planOption, index) => {
                        const checked = planIssue === planOption.value;
                        return (
                          <button
                            key={planOption.value}
                            ref={(element) => { planRefs.current[index] = element; }}
                            type="button"
                            role="radio"
                            aria-checked={checked}
                            tabIndex={checked || (planIssue === null && index === 0) ? 0 : -1}
                            onClick={() => setPlanIssue(planOption.value)}
                            className={cn(
                              "flex min-h-11 w-full items-center gap-3 rounded-xl border px-4 py-3 text-left text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600",
                              checked ? "border-blue-600 bg-blue-50 dark:bg-blue-950/30" : "border-border bg-background",
                            )}
                          >
                            <span className={cn("flex h-5 w-5 items-center justify-center rounded-full border", checked ? "border-blue-600 bg-blue-600 text-white" : "border-foreground/35 text-transparent")}>
                              <Check className="h-3.5 w-3.5" />
                            </span>
                            {planOption.label}
                          </button>
                        );
                      })}
                    </div>
                    <FieldError error={error} target="plan" />
                  </div>
                ) : null}
                {option.value === "MISSING_FEATURE" && selected(option.value) ? (
                  <label className="ml-4 block space-y-2 border-l-2 border-blue-200 pl-4 text-sm font-medium dark:border-blue-900">
                    <span>What are you looking for?</span>
                    <input value={missingFeatureText} onChange={(event) => setMissingFeatureText(event.target.value)} maxLength={200} className="min-h-11 w-full rounded-xl border border-border bg-background px-3 py-2 font-normal outline-none focus:ring-2 focus:ring-blue-600" />
                    <FieldError error={error} target="missingFeature" />
                  </label>
                ) : null}
                {option.value === "CANT_FIND_CONTENT" && selected(option.value) ? (
                  <label className="ml-4 block space-y-2 border-l-2 border-blue-200 pl-4 text-sm font-medium dark:border-blue-900">
                    <span>What are you studying?</span>
                    <input value={contentSubjectText} onChange={(event) => setContentSubjectText(event.target.value)} maxLength={200} className="min-h-11 w-full rounded-xl border border-border bg-background px-3 py-2 font-normal outline-none focus:ring-2 focus:ring-blue-600" />
                    <FieldError error={error} target="contentSubject" />
                  </label>
                ) : null}
              </div>
            ))}
          </div>
          <FieldError error={error} target="primary" />
        </section>

        <label className="block space-y-2">
          <span className="block text-sm font-semibold">{"Anything else you'd like us to know?"}</span>
          <span className="block text-xs text-foreground/60">What would make NoteLib more useful for you?</span>
          <span className="block text-xs text-foreground/50">Optional</span>
          <textarea
            value={freeText}
            onChange={(event) => setFreeText(event.target.value)}
            maxLength={2000}
            rows={6}
            className="w-full rounded-xl border border-border bg-background px-3 py-3 text-sm outline-none focus:ring-2 focus:ring-blue-600"
          />
          <span className="block text-right text-xs tabular-nums text-foreground/50" aria-live="polite">
            {freeText.length} / 2000
          </span>
          <FieldError error={error} target="freeText" />
        </label>

        {primaryBlockers.length === 0 && freeText.trim().length === 0 ? (
          <p className="text-sm text-foreground/60">Choose at least one option or add a comment.</p>
        ) : null}
        <FieldError error={error} target="form" />
        <Button
          type="submit"
          disabled={submitting || (primaryBlockers.length === 0 && freeText.trim().length === 0)}
          loading={submitting}
          loadingText="Sending feedback..."
        >
          Send feedback
        </Button>
      </form>
    </Card>
  );
}
