"use client";

import { useState } from "react";
import { ThumbsDown, ThumbsUp } from "lucide-react";
import { SendFeedbackWidget, type FeedbackQuickAction } from "@/components/feedback/send-feedback-widget";

type QuizFeedbackPanelProps = {
  quizLabel: string;
  noteTitle?: string | null;
  section: "results" | "review";
};

function buildQuizFeedbackActions(
  quizLabel: string,
  noteTitle: string | null | undefined,
  section: "results" | "review",
): FeedbackQuickAction[] {
  const noteLine = noteTitle ? `Note: ${noteTitle}` : "Note: Unknown";
  const contextLine = `Context: ${section === "review" ? "Review Answers" : "Quiz Results"}`;

  const buildTemplate = (issueType: string) =>
    `Feedback type: ${issueType}\nQuiz: ${quizLabel}\n${contextLine}\n${noteLine}\n\nWhat happened?`;

  return [
    { label: "Report Question", template: buildTemplate("Report Question") },
    { label: "Confusing Explanation", template: buildTemplate("Confusing Explanation") },
    { label: "Something is wrong", template: buildTemplate("Something is wrong") },
  ];
}

export function QuizFeedbackPanel({
  quizLabel,
  noteTitle,
  section,
}: Readonly<QuizFeedbackPanelProps>) {
  const [helpfulAcknowledged, setHelpfulAcknowledged] = useState(false);

  if (section === "results") {
    const helpfulTemplate = [
      "Feedback type: Quiz Feedback",
      `Quiz: ${quizLabel}`,
      "Context: Quiz Results",
      noteTitle ? `Note: ${noteTitle}` : "Note: Unknown",
      "",
      "What happened?",
    ].join("\n");

    return (
      <SendFeedbackWidget
        variant="inline"
        title="Was this quiz helpful?"
        description={
          helpfulAcknowledged
            ? "Thanks. Your response helps us improve future quizzes."
            : "Tell us whether this quiz felt useful, or send feedback if something felt off."
        }
        triggerLabel="Send Feedback"
        quickActions={[
          {
            label: "Yes",
            icon: <ThumbsUp className="h-4 w-4" />,
            variant: helpfulAcknowledged ? "default" : "outline",
            onClick: () => setHelpfulAcknowledged(true),
          },
          {
            label: "Give Feedback",
            icon: <ThumbsDown className="h-4 w-4" />,
            template: helpfulTemplate,
          },
        ]}
      />
    );
  }

  return (
    <SendFeedbackWidget
      variant="inline"
      title="Help improve this quiz"
      description={
        section === "review"
          ? "Found a confusing question or explanation while reviewing answers? Tell us what felt off."
          : "Spotted a quiz issue or something confusing in the results? Tell us what happened."
      }
      triggerLabel="Send Feedback"
      quickActions={buildQuizFeedbackActions(quizLabel, noteTitle, section)}
    />
  );
}
