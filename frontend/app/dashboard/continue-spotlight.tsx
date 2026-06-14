"use client";

import { useEffect } from "react";
import { CheckCircle2, Sparkles, TrendingUp, Trophy } from "lucide-react";
import { Card } from "@/components/ui/card";
import { ResponsiveActionLink, type ActionIconName } from "@/components/ui/action-button";
import {
  trackAnalyticsEvent,
  type ContinueStudyingResponse,
  type ContinueStudyingResumeType,
} from "@/lib/api";

type ContinueSpotlightProps = {
  recommendation: ContinueStudyingResponse;
  profileType?: "STUDENT" | "BOARD_EXAM" | "TEACHER" | "PROFESSIONAL";
};

function formatScorePercentage(value: number) {
  if (Number.isInteger(value)) {
    return String(value);
  }
  return value.toFixed(2).replace(/\.?0+$/, "");
}

function formatMetadataLine(subject: string | null, courseProgram: string | null) {
  const parts = [subject?.trim(), courseProgram?.trim()].filter(
    (value): value is string => Boolean(value && value.length > 0),
  );
  return parts.join(" • ");
}

function resolveResumePresentation(
  resumeType: ContinueStudyingResumeType | null,
  profileType?: ContinueSpotlightProps["profileType"],
): {
  action: ActionIconName;
  hrefSuffix: "quick-review" | "challenge-quiz" | "adaptive-practice" | "long-exam";
  label: string;
} {
  if (resumeType === "CHALLENGE") {
    return {
      action: "challengeQuiz",
      hrefSuffix: "challenge-quiz",
      label: profileType === "PROFESSIONAL" ? "Continue Certification Review" : "Resume Challenge Quiz",
    };
  }
  if (resumeType === "LONG_EXAM") {
    return {
      action: "challengeQuiz",
      hrefSuffix: "long-exam",
      label: profileType === "PROFESSIONAL" ? "Continue Full Practice Exam" : "Resume Long Exam",
    };
  }
  if (resumeType === "ADAPTIVE") {
    return {
      action: "adaptivePractice",
      hrefSuffix: "adaptive-practice",
      label: "Resume Adaptive Practice",
    };
  }
  return {
    action: "quickReview",
    hrefSuffix: "quick-review",
    label: "Resume Quick Review",
  };
}

function getCardTone(recommendation: ContinueStudyingResponse) {
  if (recommendation.reason === "SUGGESTED_CHALLENGE") {
    return {
      label: "Try Challenge Quiz",
      icon: Trophy,
      body: "Put this note to the test with a focused Challenge Quiz.",
    };
  }

  if (recommendation.reason === "RESUME_REVIEW") {
    if (recommendation.resumeState === "RETRY_TRANSITION") {
      return {
        label: "Retry Ready",
        icon: TrendingUp,
        body: "You finished the first pass and still have missed questions ready to review.",
      };
    }

    if (recommendation.resumeState === "RETRY_IN_PROGRESS") {
      const remainingQuestions = recommendation.remainingQuestions ?? 0;
      const questionLabel = remainingQuestions === 1 ? "question" : "questions";
      return {
        label: "Retry Round",
        icon: TrendingUp,
        body: `You still have ${remainingQuestions} ${questionLabel} left in this retry round.`,
      };
    }

    const currentQuestion = recommendation.currentQuestionIndex ?? 0;
    const totalQuestions = recommendation.totalQuestions ?? 0;
    const normalizedTotal = Math.max(1, totalQuestions);
    const normalizedQuestion = Math.min(Math.max(1, currentQuestion + 1), normalizedTotal);
    const modeLabel = recommendation.resumeType === "CHALLENGE"
      ? "Challenge Quiz"
      : recommendation.resumeType === "ADAPTIVE"
        ? "Adaptive Practice"
        : recommendation.resumeType === "LONG_EXAM"
          ? "Long Exam"
          : null;
    return {
      label: "In Progress",
      icon: TrendingUp,
      body: modeLabel
        ? `You left off on Question ${normalizedQuestion} of ${normalizedTotal} in your ${modeLabel}.`
        : `You left off on Question ${normalizedQuestion} of ${normalizedTotal}.`,
    };
  }

  const latestScore = recommendation.lastScorePercentage;
  if (latestScore !== null) {
    if (latestScore >= 100) {
      return {
        label: "Perfect Score",
        icon: CheckCircle2,
        body: "You scored 100% on your latest review. Go again anytime to keep it sharp.",
      };
    }
    return {
      label: "Keep Improving",
      icon: TrendingUp,
      body: `You recently scored ${formatScorePercentage(latestScore)}% on this note. Review it again to improve your score.`,
    };
  }

  return {
    label: "Ready to Review",
    icon: Sparkles,
    body: "This note is ready for another round of practice whenever you are.",
  };
}

export function ContinueSpotlight({ recommendation, profileType }: Readonly<ContinueSpotlightProps>) {
  useEffect(() => {
    if (!recommendation.noteId || !recommendation.reason) {
      return;
    }
    void trackAnalyticsEvent({
      eventType: "DASHBOARD_RECOMMENDATION_SHOWN",
      entityId: recommendation.noteId,
      metadata: {
        reason: recommendation.reason,
        resumeType: recommendation.resumeType,
      },
    });
  }, [recommendation.noteId, recommendation.reason, recommendation.resumeType]);

  if (!recommendation.noteId) {
    return null;
  }

  const cardTone = getCardTone(recommendation);
  const ToneIcon = cardTone.icon;
  const resumePresentation = resolveResumePresentation(recommendation.resumeType, profileType);
  const actionLabel = recommendation.reason === "SUGGESTED_CHALLENGE"
    ? "Try Challenge Quiz"
    : resumePresentation.label;
  const noteTitle = recommendation.noteTitle?.trim() || "Untitled note";
  const metadataLine = formatMetadataLine(recommendation.subject, recommendation.courseProgram);
  const resumeHref = `/notes/${recommendation.noteId}/${resumePresentation.hrefSuffix}`;

  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          KEEP IT SHARP
        </p>
        <div className="space-y-3 rounded-md border border-border bg-muted/40 p-3">
          <div className="flex flex-wrap items-center gap-2">
            <span className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-2 py-1 text-[11px] font-medium text-foreground/80">
              <ToneIcon className="h-3.5 w-3.5" aria-hidden="true" />
              {cardTone.label}
            </span>
            <span className="text-sm font-medium text-blue-700 dark:text-blue-300">
              {actionLabel}
            </span>
          </div>

          <div className="space-y-1">
            <h2 className="line-clamp-2 text-lg font-semibold sm:text-xl">{noteTitle}</h2>
            {metadataLine ? (
              <p className="line-clamp-1 text-sm text-foreground/65">{metadataLine}</p>
            ) : null}
          </div>

          <p className="text-sm font-medium leading-relaxed text-foreground/85">{cardTone.body}</p>

          {recommendation.lastReviewedAt ? (
            <p className="text-xs text-foreground/65">
              Last reviewed: {new Date(recommendation.lastReviewedAt).toLocaleString()}
            </p>
          ) : recommendation.lastOpenedAt ? (
            <p className="text-xs text-foreground/65">
              Last opened: {new Date(recommendation.lastOpenedAt).toLocaleString()}
            </p>
          ) : null}
        </div>
      </div>

      <ResponsiveActionLink
        href={resumeHref}
        action={resumePresentation.action}
        label={actionLabel}
        className="w-full sm:w-auto"
        onClick={() => {
          void trackAnalyticsEvent({
            eventType: "DASHBOARD_RECOMMENDATION_CTA_CLICKED",
            entityId: recommendation.noteId,
            metadata: {
              reason: recommendation.reason,
              resumeType: recommendation.resumeType,
            },
          });
        }}
      />
    </Card>
  );
}
