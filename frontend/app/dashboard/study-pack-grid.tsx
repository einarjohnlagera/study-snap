import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import type { StudyPackListItemResponse } from "@/lib/api";

type StudyPackGridProps = {
  studyPacks: StudyPackListItemResponse[];
  onDelete: (id: string) => Promise<void>;
};

export function StudyPackGrid({ studyPacks, onDelete }: StudyPackGridProps) {
  return (
    <section className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold">Your Study Packs</h2>
        <p className="text-xs text-foreground/65">{studyPacks.length} saved</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {studyPacks.map((item) => (
          <Card key={item.id} className="space-y-4">
            <div className="space-y-2">
              <h3 className="text-lg font-semibold">{item.title}</h3>
              <p className="text-sm text-foreground/75">{item.summaryPreview}</p>
            </div>

            <p className="text-xs text-foreground/65">
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

            <div className="flex gap-2">
              <Link href={`/study-packs/${item.id}`}>
                <Button type="button" variant="outline">
                  Open
                </Button>
              </Link>
              <Button type="button" variant="outline" onClick={() => void onDelete(item.id)}>
                Delete
              </Button>
            </div>
          </Card>
        ))}
      </div>
    </section>
  );
}
