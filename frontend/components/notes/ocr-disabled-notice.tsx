"use client";

import { useEffect, useRef, useState } from "react";
import { PauseCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { trackAnalyticsEvent } from "@/lib/api";
import { cn } from "@/lib/utils";

const DEFAULT_OCR_DISABLED_MESSAGE =
  "Image and scanned-document reading is temporarily unavailable. Try a PDF or document with selectable text instead.";
const NOTICE_TITLE = "Image reading is temporarily unavailable";
const FEEDBACK_BUTTON_LABEL = "Yes, I'd like this back";
const FEEDBACK_CONFIRMATION_LABEL = "Thanks - noted!";

type OcrDisabledNoticeProps = {
  message?: string | null;
  source: string;
  className?: string;
};

export function OcrDisabledNotice({ message, source, className }: Readonly<OcrDisabledNoticeProps>) {
  const [feedbackSent, setFeedbackSent] = useState(false);
  const hasTrackedNoticeRef = useRef(false);
  const resolvedMessage = message?.trim() || DEFAULT_OCR_DISABLED_MESSAGE;

  useEffect(() => {
    if (hasTrackedNoticeRef.current) {
      return;
    }
    hasTrackedNoticeRef.current = true;
    void trackAnalyticsEvent({
      eventType: "OCR_DISABLED_NOTICE_SHOWN",
      metadata: { source },
    });
  }, [source]);

  const handleFeedbackClick = () => {
    if (feedbackSent) {
      return;
    }
    setFeedbackSent(true);
    void trackAnalyticsEvent({
      eventType: "OCR_DISABLED_FEEDBACK_INTERESTED",
      metadata: { source },
    });
  };

  return (
    <div
      role="status"
      className={cn(
        "rounded-lg border border-amber-300/70 bg-amber-50 p-3 text-amber-950 dark:border-amber-700/60 dark:bg-amber-950/30 dark:text-amber-100",
        className,
      )}
    >
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex min-w-0 items-start gap-3">
          <span className="mt-0.5 rounded-full bg-amber-100 p-1 text-amber-700 dark:bg-amber-900/60 dark:text-amber-200">
            <PauseCircle className="h-4 w-4" aria-hidden="true" />
          </span>
          <div className="min-w-0 space-y-1">
            <p className="text-sm font-semibold">{NOTICE_TITLE}</p>
            <p className="text-sm leading-relaxed text-amber-900/85 dark:text-amber-100/80">{resolvedMessage}</p>
          </div>
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={feedbackSent}
          className="w-full shrink-0 border-amber-400/70 bg-background text-amber-900 hover:bg-amber-100 dark:border-amber-700 dark:text-amber-100 dark:hover:bg-amber-900/50 sm:w-auto"
          onClick={handleFeedbackClick}
        >
          {feedbackSent ? FEEDBACK_CONFIRMATION_LABEL : FEEDBACK_BUTTON_LABEL}
        </Button>
      </div>
    </div>
  );
}
