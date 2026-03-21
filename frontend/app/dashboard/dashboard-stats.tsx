import { Card } from "@/components/ui/card";
import type { NoteListItemResponse } from "@/lib/api";

type DashboardStatsProps = {
  notes: NoteListItemResponse[];
  totalQuizQuestions: number;
};

export function DashboardStats({ notes, totalQuizQuestions }: DashboardStatsProps) {
  const totalStudyPacks = notes.filter((item) => item.studyPackStatus === "STUDY_PACK_READY").length;
  const taggedNotes = notes.filter((item) => item.tags.length > 0).length;

  const cards = [
    { label: "Study Packs Created", value: totalStudyPacks },
    { label: "Total Quiz Questions", value: totalQuizQuestions },
    { label: "Notes with Tags", value: taggedNotes },
  ];

  return (
    <section className="space-y-4">
      <h2 className="text-lg font-semibold sm:text-xl">Your Stats</h2>
      <div className="grid gap-3 sm:grid-cols-3 sm:gap-4">
        {cards.map((card) => (
          <Card key={card.label} className="space-y-2 p-4 sm:p-6">
            <p className="text-xs uppercase tracking-wide text-foreground/65">{card.label}</p>
            <p className="text-xl font-semibold sm:text-2xl">{card.value}</p>
          </Card>
        ))}
      </div>
    </section>
  );
}
