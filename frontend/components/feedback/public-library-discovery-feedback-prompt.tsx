"use client";

import { useEffect, useMemo, useState } from "react";
import { Compass, SearchX } from "lucide-react";
import { SendFeedbackWidget } from "@/components/feedback/send-feedback-widget";
import { getDashboardOverview } from "@/lib/api";
import { getUserScopedGuidanceId, hasSeenTip, markTipSeen } from "@/lib/guidance";
import {
  hasPublicLibraryDiscoveryFriction,
  hasShownEarlyLifecycleFeedbackSignalThisSession,
  markEarlyLifecycleFeedbackSignalShownThisSession,
} from "@/lib/early-lifecycle-feedback-signals";

export const PUBLIC_LIBRARY_DISCOVERY_FEEDBACK_PROMPT_ID = "public-library-discovery-feedback";
const NEW_USER_NOTE_COUNT_THRESHOLD = 2;

type PublicLibraryDiscoveryFeedbackPromptProps = {
  userId: string;
};

export function PublicLibraryDiscoveryFeedbackPrompt({
  userId,
}: Readonly<PublicLibraryDiscoveryFeedbackPromptProps>) {
  const storageId = useMemo(
    () => getUserScopedGuidanceId(PUBLIC_LIBRARY_DISCOVERY_FEEDBACK_PROMPT_ID, userId),
    [userId],
  );
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    if (hasSeenTip(storageId) || hasShownEarlyLifecycleFeedbackSignalThisSession() || !hasPublicLibraryDiscoveryFriction()) {
      return;
    }
    let cancelled = false;
    void (async () => {
      try {
        const overview = await getDashboardOverview();
        if (!cancelled && overview.totalNoteCount <= NEW_USER_NOTE_COUNT_THRESHOLD) {
          setVisible(true);
          markTipSeen(storageId);
          markEarlyLifecycleFeedbackSignalShownThisSession();
        }
      } catch {
        // non-blocking — no prompt on failure, no tip marked seen so it can be reconsidered later
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [storageId]);

  if (!visible) {
    return null;
  }

  const context = "Feedback type: Public Library Discovery\nContext: Public Library\n\n";

  return (
    <SendFeedbackWidget
      variant="inline"
      title="What's making it hard to find something to study?"
      description="You've looked at a few notes without adding one to your library — tell us what's missing."
      showTriggerButton={false}
      onDismiss={() => setVisible(false)}
      quickActions={[
        {
          label: "Not enough notes for my course/program",
          icon: <SearchX className="h-4 w-4" />,
          template: `${context}What course/program or subject are you looking for?`,
        },
        {
          label: "Not sure what to search for",
          icon: <Compass className="h-4 w-4" />,
          template: `${context}What would have made it easier to know where to start?`,
        },
        {
          label: "Just browsing for now",
          onClick: () => setVisible(false),
        },
      ]}
    />
  );
}
