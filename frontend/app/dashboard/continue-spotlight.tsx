import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import type { StudyPackListItemResponse } from "@/lib/api";

type ContinueSpotlightProps = {
  latestStudyPack: StudyPackListItemResponse;
};

export function ContinueSpotlight({ latestStudyPack }: ContinueSpotlightProps) {
  return (
    <Card className="space-y-4">
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Continue Studying
        </p>
        <h2 className="text-xl font-semibold">{latestStudyPack.title}</h2>
        <p className="text-sm text-foreground/75">{latestStudyPack.summaryPreview}</p>
      </div>

      <div className="flex flex-wrap gap-3 text-xs text-foreground/70">
        <span>{new Date(latestStudyPack.createdAt).toLocaleString()}</span>
        <span>{latestStudyPack.quizCount} quiz questions</span>
      </div>

      {latestStudyPack.tags.length > 0 ? (
        <div className="flex flex-wrap gap-2">
          {latestStudyPack.tags.map((tag) => (
            <span
              key={tag}
              className="rounded-full border border-border bg-background px-2 py-1 text-xs text-foreground/75"
            >
              {tag}
            </span>
          ))}
        </div>
      ) : null}

      <div className="flex flex-wrap gap-2">
        <Link href={`/study-packs/${latestStudyPack.id}`}>
          <Button type="button">Continue</Button>
        </Link>
      </div>
    </Card>
  );
}
