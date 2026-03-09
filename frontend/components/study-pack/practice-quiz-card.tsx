import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import type { QuizItem } from "@/lib/api";

type PracticeQuizCardProps = {
  quiz: QuizItem[];
};

export function PracticeQuizCard({ quiz }: PracticeQuizCardProps) {
  return (
    <Card className="border-emerald-500/40">
      <CardTitle className="mb-4">Practice Quiz</CardTitle>
      <div className="space-y-6">
        {quiz.map((item, index) => (
          <Card key={`${item.question}-${index}`} className="space-y-3">
            <CardTitle className="text-base">
              {index + 1}. {item.question}
            </CardTitle>
            {item.choices.length > 0 ? (
              <ul className="space-y-2 text-sm">
                {item.choices.map((choice) => {
                  const isCorrect = choice === item.answer;
                  return (
                    <li
                      key={choice}
                      className={`rounded-md border px-3 py-2 ${
                        isCorrect
                          ? "border-emerald-500/50 bg-emerald-500/10 text-foreground"
                          : "border-border text-foreground/75"
                      }`}
                    >
                      {choice}
                      {isCorrect ? (
                        <span className="ml-2 text-xs font-medium text-emerald-700 dark:text-emerald-300">
                          Correct answer
                        </span>
                      ) : null}
                    </li>
                  );
                })}
              </ul>
            ) : null}
            <CardDescription>
              <span className="font-medium text-foreground">Explanation:</span>{" "}
              {item.explanation}
            </CardDescription>
          </Card>
        ))}
      </div>
    </Card>
  );
}
