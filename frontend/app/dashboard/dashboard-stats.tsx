import { Card } from "@/components/ui/card";
import type { StudyPackListItemResponse } from "@/lib/api";

type DashboardStatsProps = {
  studyPacks: StudyPackListItemResponse[];
};

export function DashboardStats({ studyPacks }: DashboardStatsProps) {
  const totalStudyPacks = studyPacks.length;
  const totalQuizQuestions = studyPacks.reduce((sum, item) => sum + item.quizCount, 0);
  const taggedStudyPacks = studyPacks.filter((item) => item.tags.length > 0).length;

  const cards = [
    { label: "Total Study Packs", value: totalStudyPacks },
    { label: "Quiz Questions Saved", value: totalQuizQuestions },
    { label: "Tagged Study Packs", value: taggedStudyPacks },
  ];

  return (
    <section className="grid gap-4 sm:grid-cols-3">
      {cards.map((card) => (
        <Card key={card.label} className="space-y-2">
          <p className="text-xs uppercase tracking-wide text-foreground/65">{card.label}</p>
          <p className="text-2xl font-semibold">{card.value}</p>
        </Card>
      ))}
    </section>
  );
}
