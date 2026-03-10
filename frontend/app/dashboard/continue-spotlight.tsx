import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import type { ContinueStudyingResponse } from "@/lib/api";

type ContinueSpotlightProps = {
  recommendation: ContinueStudyingResponse;
};

function getReasonCopy(recommendation: ContinueStudyingResponse) {
  const title = recommendation.title ?? "Continue Studying";
  if (recommendation.reason === "LOW_SCORE_RECENT") {
    const scoreText = recommendation.lastScorePercentage === null ? "" : ` (${recommendation.lastScorePercentage}%)`;
    return {
      heading: title,
      body: `You recently completed Quick Review${scoreText}. A short revisit can strengthen this topic.`,
      cta: "Improve Score",
    };
  }
  if (recommendation.reason === "RECENTLY_OPENED") {
    return {
      heading: title,
      body: "You recently opened this Study Pack. Continue reviewing while it is still fresh.",
      cta: "Continue Review",
    };
  }
  return {
    heading: title,
    body: "This Study Pack is newly created and ready for your first focused review.",
    cta: "Start Review",
  };
}

export function ContinueSpotlight({ recommendation }: ContinueSpotlightProps) {
  const copy = getReasonCopy(recommendation);
  if (!recommendation.studyPackId) {
    return null;
  }

  return (
    <Card className="space-y-4">
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Continue Studying
        </p>
        <h2 className="text-xl font-semibold">{copy.heading}</h2>
        <p className="text-sm text-foreground/75">{copy.body}</p>
        {recommendation.summaryPreview ? (
          <p className="text-sm text-foreground/75">{recommendation.summaryPreview}</p>
        ) : null}
      </div>

      <div className="flex flex-wrap gap-3 text-xs text-foreground/70">
        {recommendation.lastReviewedAt ? (
          <span>Last reviewed: {new Date(recommendation.lastReviewedAt).toLocaleString()}</span>
        ) : null}
        {recommendation.lastOpenedAt ? (
          <span>Last opened: {new Date(recommendation.lastOpenedAt).toLocaleString()}</span>
        ) : null}
        {recommendation.createdAt ? (
          <span>Created: {new Date(recommendation.createdAt).toLocaleString()}</span>
        ) : null}
      </div>

      <div className="flex flex-wrap gap-2">
        <Link href={`/study-packs/${recommendation.studyPackId}`}>
          <Button type="button">{copy.cta}</Button>
        </Link>
      </div>
    </Card>
  );
}
