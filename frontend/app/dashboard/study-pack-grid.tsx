import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import type { StudyPackListItemResponse } from "@/lib/api";

type StudyPackGridProps = {
  studyPacks: StudyPackListItemResponse[];
};

export function StudyPackGrid({ studyPacks }: StudyPackGridProps) {
  return (
    <section className="space-y-4">
      <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
        <h2 className="text-lg font-semibold sm:text-xl">Your Study Packs</h2>
        <p className="text-xs text-foreground/65">{studyPacks.length} saved</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {studyPacks.map((item) => (
          <Card key={item.id} className="space-y-4 p-4 sm:p-6">
            <div className="space-y-2">
              <h3 className="text-base font-semibold sm:text-lg">{item.title}</h3>
              <p className="text-sm leading-relaxed text-foreground/75">{item.summaryPreview}</p>
            </div>

            <p className="break-words text-xs text-foreground/65">
              {new Date(item.createdAt).toLocaleString()} · {item.quizCount} quiz questions
            </p>

            {item.tags.length > 0 ? (
              <div className="flex flex-wrap gap-2">
                {item.tags.map((tag) => (
                  <span
                    key={`${item.id}-${tag}`}
                    className="rounded-full border border-border bg-background px-2 py-1 text-xs text-foreground/75"
                  >
                    {tag}
                  </span>
                ))}
              </div>
            ) : null}

            <div className="flex flex-col gap-2 sm:flex-row">
              <Link href={`/study-packs/${item.id}`} className="w-full sm:w-auto">
                <Button type="button" variant="outline" className="w-full sm:w-auto">
                  Open
                </Button>
              </Link>
            </div>
          </Card>
        ))}
      </div>
    </section>
  );
}
