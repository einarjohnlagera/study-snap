"use client";

import { useEffect, useMemo, useState } from "react";
import { CheckCircle2, HelpCircle } from "lucide-react";
import { SendFeedbackWidget } from "@/components/feedback/send-feedback-widget";
import { getUserScopedGuidanceId, hasSeenTip, hasSeenTipThisSession, markTipSeen } from "@/lib/guidance";
import { FIRST_QUIZ_FEEDBACK_PROMPT_ID } from "@/components/feedback/quiz-feedback-panel";
import {
  hasShownEarlyLifecycleFeedbackSignalThisSession,
  markEarlyLifecycleFeedbackSignalShownThisSession,
} from "@/lib/early-lifecycle-feedback-signals";

export const STUDY_PACK_GENERATED_FEEDBACK_PROMPT_ID = "study-pack-generated-feedback";

type StudyPackGeneratedFeedbackPromptProps = {
  userId: string;
  noteTitle?: string | null;
};

export function StudyPackGeneratedFeedbackPrompt({
  userId,
  noteTitle,
}: Readonly<StudyPackGeneratedFeedbackPromptProps>) {
  const storageId = useMemo(
    () => getUserScopedGuidanceId(STUDY_PACK_GENERATED_FEEDBACK_PROMPT_ID, userId),
    [userId],
  );
  const firstQuizStorageId = useMemo(
    () => getUserScopedGuidanceId(FIRST_QUIZ_FEEDBACK_PROMPT_ID, userId),
    [userId],
  );
  const [visible] = useState(
    () => !hasSeenTip(storageId)
      && !hasShownEarlyLifecycleFeedbackSignalThisSession()
      && !hasSeenTipThisSession(firstQuizStorageId),
  );
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    if (visible) {
      markTipSeen(storageId);
      markEarlyLifecycleFeedbackSignalShownThisSession();
    }
  }, [storageId, visible]);

  if (!visible || dismissed) {
    return null;
  }

  const context = [
    "Feedback type: Generation Quality",
    "Context: Note Detail (non-onboarding)",
    noteTitle ? `Note: ${noteTitle}` : "Note: Unknown",
    "",
  ].join("\n");

  return (
    <SendFeedbackWidget
      variant="inline"
      title="Does this match what you needed?"
      description="You just generated a new Study Pack — tell us if the summary and quiz feel useful."
      showTriggerButton={false}
      onDismiss={() => setDismissed(true)}
      quickActions={[
        {
          label: "Yes, this is useful",
          icon: <CheckCircle2 className="h-4 w-4" />,
          onClick: () => setDismissed(true),
        },
        {
          label: "Summary missed the point",
          icon: <HelpCircle className="h-4 w-4" />,
          template: `${context}What did the summary miss?`,
        },
        {
          label: "Quiz doesn't match the note",
          icon: <HelpCircle className="h-4 w-4" />,
          template: `${context}What felt off about the quiz?`,
        },
      ]}
    />
  );
}
