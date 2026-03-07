import { Loader2 } from "lucide-react";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import type { ReviewResponse } from "@/lib/api";

type ReviewResultsProps = {
  loading: boolean;
  demoMode: boolean;
  reviewResult: ReviewResponse | null;
  generatedLabel: string | null;
  detectedTopic: string | null;
};

export function ReviewResults({
  loading,
  demoMode,
  reviewResult,
  generatedLabel,
  detectedTopic,
}: ReviewResultsProps) {
  if (loading) {
    return (
      <section className="space-y-4">
        <h2 className="text-2xl font-semibold text-foreground">
          Generated Study Pack
        </h2>
        <Card className="space-y-3">
          <div className="flex items-center gap-2">
            <Loader2 className="h-4 w-4 animate-spin text-blue-600" />
            <CardTitle>Creating your study pack...</CardTitle>
          </div>
          <ul className="space-y-2 text-sm text-foreground/75">
            <li>Summarizing notes</li>
            <li>Extracting key concepts</li>
            <li>Generating practice quiz</li>
          </ul>
        </Card>
      </section>
    );
  }

  if (!reviewResult) {
    return (
      <section className="space-y-4">
        <h2 className="text-2xl font-semibold text-foreground">
          Generated Study Pack
        </h2>
        <Card>
          <CardTitle className="mb-2">
            Your generated study pack will appear here
          </CardTitle>
          <CardDescription>
            Study Snap will create a summary, key concepts, and a short practice
            quiz from your notes.
          </CardDescription>
        </Card>
      </section>
    );
  }

  return (
    <section className="space-y-4">
      <h2 className="text-2xl font-semibold text-foreground">
        Generated Study Pack
      </h2>
      {demoMode ? (
        <Card className="border-blue-500/40 bg-blue-50/70 dark:bg-blue-950/20">
          <CardDescription className="text-blue-900 dark:text-blue-200">
            This is a demo example. Paste your own notes to generate your study
            pack.
          </CardDescription>
        </Card>
      ) : null}
      <p className="text-sm text-foreground/65">
        {generatedLabel} • {reviewResult.quiz.length} quiz questions • Topic
        detected: {detectedTopic}
      </p>

      <Card>
        <CardTitle className="mb-2">Title</CardTitle>
        <CardDescription>{reviewResult.title}</CardDescription>
      </Card>

      <Card>
        <CardTitle className="mb-2">Summary</CardTitle>
        <CardDescription>{reviewResult.summary}</CardDescription>
      </Card>

      <Card>
        <CardTitle className="mb-2">Key Concepts</CardTitle>
        <ul className="list-disc space-y-2 pl-5 text-base leading-relaxed text-foreground/80">
          {reviewResult.keyConcepts.map((concept) => (
            <li key={concept}>{concept}</li>
          ))}
        </ul>
      </Card>

      <Card className="border-emerald-500/40">
        <CardTitle className="mb-4">Practice Quiz</CardTitle>
        <div className="space-y-6">
          {reviewResult.quiz.map((item, index) => (
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
    </section>
  );
}
