import Link from "next/link";
import { CheckCircle2, Sparkles, TrendingUp } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import type { ContinueStudyingResponse } from "@/lib/api";

type ContinueSpotlightProps = {
  recommendation: ContinueStudyingResponse;
};

function formatScorePercentage(value: number) {
  if (Number.isInteger(value)) {
    return String(value);
  }
  return value.toFixed(2).replace(/\.?0+$/, "");
}

function getScoreAwareCopy(recommendation: ContinueStudyingResponse) {
  if (recommendation.reason === "RESUME_REVIEW") {
    if (recommendation.resumeState === "RETRY_TRANSITION") {
      return {
        label: "Retry Ready",
        icon: TrendingUp,
        heading: "Resume Quick Review",
        body: "You finished the first pass and still have some missed questions to review.",
        cta: "Resume Review",
      };
    }

    if (recommendation.resumeState === "RETRY_IN_PROGRESS") {
      const remainingQuestions = recommendation.remainingQuestions ?? 0;
      const questionLabel = remainingQuestions === 1 ? "question" : "questions";
      return {
        label: "Retry Round",
        icon: TrendingUp,
        heading: "Resume Retry Round",
        body: `You still have ${remainingQuestions} ${questionLabel} to review.`,
        cta: "Resume Review",
      };
    }

    const currentQuestion = recommendation.currentQuestionIndex ?? 0;
    const totalQuestions = recommendation.totalQuestions ?? 0;
    const normalizedTotal = Math.max(1, totalQuestions);
    const normalizedQuestion = Math.min(Math.max(1, currentQuestion + 1), normalizedTotal);
    return {
      label: "In Progress",
      icon: TrendingUp,
      heading: "Resume Quick Review",
      body: `You left off on Question ${normalizedQuestion} of ${normalizedTotal}. Continue your Quick Review.`,
      cta: "Resume Review",
    };
  }

  const latestScore = recommendation.lastScorePercentage;
  if (latestScore !== null) {
    if (latestScore >= 100) {
      return {
        label: "Perfect Score",
        icon: CheckCircle2,
        heading: "Nice work on this pack",
        body: "You scored 100% on your latest Quick Review. Practice again anytime to keep it sharp.",
        cta: "Practice Again",
      };
    }
    return {
      label: "Keep Improving",
      icon: TrendingUp,
      heading: "Continue studying",
      body: `You recently scored ${formatScorePercentage(latestScore)}% on this Study Pack. Review it again to improve your score.`,
      cta: "Continue Review",
    };
  }

  return {
    label: "Get Started",
    icon: Sparkles,
    heading: "Start studying",
    body: "You created this Study Pack recently. Start your first Quick Review.",
    cta: "Start Review",
  };
}

export function ContinueSpotlight({ recommendation }: ContinueSpotlightProps) {
  const copy = getScoreAwareCopy(recommendation);
  const FeedbackIcon = copy.icon;
  if (!recommendation.studyPackId) {
    return null;
  }

  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          KEEP IT SHARP
        </p>
        <div className="space-y-2 rounded-md border border-border bg-muted/40 p-3">
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-2 py-1 text-[11px] font-medium text-foreground/80">
              <FeedbackIcon className="h-3.5 w-3.5" aria-hidden="true" />
              {copy.label}
            </span>
          </div>
          <h2 className="text-lg font-semibold sm:text-xl">{copy.heading}</h2>
          <p className="text-sm font-medium leading-relaxed text-foreground/85">{copy.body}</p>
        </div>
        {recommendation.summaryPreview ? (
          <div className="space-y-1">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">
              About this Study Pack
            </p>
            <p className="text-sm leading-relaxed text-foreground/70">{recommendation.summaryPreview}</p>
          </div>
        ) : null}
      </div>

      <div className="space-y-1 text-xs text-foreground/65">
        {recommendation.lastReviewedAt ? (
          <p className="break-words">Last reviewed · {new Date(recommendation.lastReviewedAt).toLocaleString()}</p>
        ) : null}
        {recommendation.lastOpenedAt ? (
          <p className="break-words">Last opened · {new Date(recommendation.lastOpenedAt).toLocaleString()}</p>
        ) : null}
        {recommendation.createdAt ? (
          <p className="break-words">Created · {new Date(recommendation.createdAt).toLocaleDateString()}</p>
        ) : null}
      </div>

      <div className="flex flex-col gap-2 sm:flex-row">
        <Link href={`/study-packs/${recommendation.studyPackId}/quick-review`} className="w-full sm:w-auto">
          <Button type="button" className="w-full sm:w-auto">
            {copy.cta}
          </Button>
        </Link>
      </div>
    </Card>
  );
}
