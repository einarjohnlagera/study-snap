"use client";

import { useEffect, useMemo, useState } from "react";
import { MessageCircleQuestion, ThumbsDown, ThumbsUp } from "lucide-react";
import { SendFeedbackWidget, type FeedbackQuickAction } from "@/components/feedback/send-feedback-widget";
import {
  getUserScopedGuidanceId,
  hasSeenTip,
  markTipSeen,
  markTipSeenThisSession,
} from "@/lib/guidance";

export const FIRST_QUIZ_FEEDBACK_PROMPT_ID = "first-quiz-feedback";

type QuizFeedbackPanelProps = {
  quizLabel: string;
  noteTitle?: string | null;
  section: "results" | "review";
  isFirstCompletedSessionEver?: boolean;
  userId?: string | null;
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
  isFirstCompletedSessionEver,
  userId,
}: Readonly<QuizFeedbackPanelProps>) {
  const [helpfulAcknowledged, setHelpfulAcknowledged] = useState(false);
  const firstQuizPromptStorageId = useMemo(
    () => userId ? getUserScopedGuidanceId(FIRST_QUIZ_FEEDBACK_PROMPT_ID, userId) : null,
    [userId],
  );
  const [showFirstQuizPrompt] = useState(
    () => isFirstCompletedSessionEver === true
      && firstQuizPromptStorageId !== null
      && !hasSeenTip(firstQuizPromptStorageId),
  );
  const [firstQuizPromptDismissed, setFirstQuizPromptDismissed] = useState(false);

  useEffect(() => {
    if (showFirstQuizPrompt && firstQuizPromptStorageId) {
      markTipSeen(firstQuizPromptStorageId);
      markTipSeenThisSession(firstQuizPromptStorageId);
    }
  }, [firstQuizPromptStorageId, showFirstQuizPrompt]);

  if (firstQuizPromptDismissed) {
    return null;
  }

  if (showFirstQuizPrompt) {
    const context = [
      "Feedback type: First Quiz Experience",
      `Quiz: ${quizLabel}`,
      `Context: ${section === "review" ? "Review Answers" : "Quiz Results"}`,
      noteTitle ? `Note: ${noteTitle}` : "Note: Unknown",
      "",
    ].join("\n");

    return (
      <SendFeedbackWidget
        variant="inline"
        title="How did your first quiz go?"
        description="Your first impression is especially useful. Tell us what felt clear or what made the quiz harder to use."
        showTriggerButton={false}
        onDismiss={() => setFirstQuizPromptDismissed(true)}
        quickActions={[
          {
            label: "That was clear",
            icon: <ThumbsUp className="h-4 w-4" />,
            template: `${context}What felt clear?`,
          },
          {
            label: "That was confusing",
            icon: <MessageCircleQuestion className="h-4 w-4" />,
            template: `${context}What felt confusing?`,
          },
        ]}
      />
    );
  }

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
        showTriggerButton={false}
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
