import { Loader2 } from "lucide-react";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { PracticeQuizCard } from "@/components/study-pack/practice-quiz-card";
import type { StudyPackResponse } from "@/lib/api";

type StudyPackResultsProps = {
  loading: boolean;
  demoMode: boolean;
  studyPackResult: StudyPackResponse | null;
  generatedLabel: string | null;
  detectedTopic: string | null;
};

export function StudyPackResults({
  loading,
  demoMode,
  studyPackResult,
  generatedLabel,
  detectedTopic,
}: StudyPackResultsProps) {
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

  if (!studyPackResult) {
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
        {generatedLabel} • {studyPackResult.quiz.length} quiz questions • Topic
        detected: {detectedTopic}
      </p>

      <Card>
        <CardTitle className="mb-2">Title</CardTitle>
        <CardDescription>{studyPackResult.title}</CardDescription>
      </Card>

      <Card>
        <CardTitle className="mb-2">Summary</CardTitle>
        <CardDescription>{studyPackResult.summary}</CardDescription>
      </Card>

      <Card>
        <CardTitle className="mb-2">Key Concepts</CardTitle>
        <ul className="list-disc space-y-2 pl-5 text-base leading-relaxed text-foreground/80">
          {studyPackResult.keyConcepts.map((concept) => (
            <li key={concept}>{concept}</li>
          ))}
        </ul>
      </Card>

      <PracticeQuizCard quiz={studyPackResult.quiz} />
    </section>
  );
}

