import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import type { QuizItem } from "@/lib/api";
import { resolveQuizCorrectIndex } from "@/lib/quiz";
import { QuizChoiceList } from "./quiz-choice-list";

type PracticeQuizCardProps = {
  quiz: QuizItem[];
};

export function PracticeQuizCard({ quiz }: PracticeQuizCardProps) {
  return (
    <Card className="border-emerald-500/40 p-4 sm:p-6">
      <CardTitle className="mb-4">Practice Quiz</CardTitle>
      <div className="space-y-6">
        {quiz.map((item, index) => (
          <Card key={`${item.question}-${index}`} className="space-y-3 p-4 sm:p-6">
            <CardTitle className="text-base">
              {index + 1}. {item.question}
            </CardTitle>
            <QuizChoiceList
              questionKey={item.question}
              choices={item.choices}
              correctIndex={resolveQuizCorrectIndex(item)}
              revealAnswer
            />
            <CardDescription className="text-sm">
              <span className="font-medium text-foreground">Explanation:</span>{" "}
              {item.explanation}
            </CardDescription>
          </Card>
        ))}
      </div>
    </Card>
  );
}
