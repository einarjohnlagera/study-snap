"use client";

import { useEffect, useMemo, useState } from "react";
import { MessageCircleQuestion, Route } from "lucide-react";
import { SendFeedbackWidget } from "@/components/feedback/send-feedback-widget";
import { FIRST_QUIZ_FEEDBACK_PROMPT_ID } from "@/components/feedback/quiz-feedback-panel";
import {
  getUserScopedGuidanceId,
  hasSeenTip,
  hasSeenTipThisSession,
  markTipSeen,
} from "@/lib/guidance";

const WELCOME_BACK_FEEDBACK_PROMPT_ID = "welcome-back-feedback";

type WelcomeBackFeedbackPromptProps = {
  userId: string;
  returningAfterInactivity: boolean;
  hasCompletedQuizSession: boolean;
};

export function WelcomeBackFeedbackPrompt({
  userId,
  returningAfterInactivity,
  hasCompletedQuizSession,
}: Readonly<WelcomeBackFeedbackPromptProps>) {
  const storageId = useMemo(
    () => getUserScopedGuidanceId(WELCOME_BACK_FEEDBACK_PROMPT_ID, userId),
    [userId],
  );
  const firstQuizStorageId = useMemo(
    () => getUserScopedGuidanceId(FIRST_QUIZ_FEEDBACK_PROMPT_ID, userId),
    [userId],
  );
  const [visible, setVisible] = useState(
    () => returningAfterInactivity
      && hasCompletedQuizSession
      && !hasSeenTip(storageId)
      && !hasSeenTipThisSession(firstQuizStorageId),
  );

  useEffect(() => {
    if (visible) {
      markTipSeen(storageId);
    }
  }, [storageId, visible]);

  if (!visible) {
    return null;
  }

  const context = "Feedback type: Welcome Back Experience\nContext: Dashboard Return\n\n";
  return (
    <SendFeedbackWidget
      variant="inline"
      title="Welcome back — what got in the way?"
      description="If anything made studying harder to return to, tell us while it is still fresh."
      showTriggerButton={false}
      onDismiss={() => setVisible(false)}
      quickActions={[
        {
          label: "Something got in the way",
          icon: <MessageCircleQuestion className="h-4 w-4" />,
          template: `${context}What got in the way?`,
        },
        {
          label: "I wasn't sure what to do next",
          icon: <Route className="h-4 w-4" />,
          template: `${context}What would have made the next step clearer?`,
        },
      ]}
    />
  );
}
