"use client";

import { useEffect, useState } from "react";

const ROTATING_MESSAGES = [
  "Analyzing your notes...",
  "Identifying key concepts...",
  "Generating questions...",
  "Balancing difficulty...",
  "Almost ready...",
] as const;
const MESSAGE_ROTATION_INTERVAL_MS = 2500;

type QuizGenerationOverlayProps = {
  title?: string;
  message?: string;
  helperText?: string;
};

export function QuizGenerationOverlay({
  title = "Generating your quiz...",
  message = "Creating personalized questions from your notes",
  helperText = "Please keep this page open",
}: QuizGenerationOverlayProps) {
  const [messageIndex, setMessageIndex] = useState(0);

  useEffect(() => {
    const intervalId = globalThis.setInterval(() => {
      setMessageIndex((currentIndex) => (currentIndex + 1) % ROTATING_MESSAGES.length);
    }, MESSAGE_ROTATION_INTERVAL_MS);

    return () => {
      globalThis.clearInterval(intervalId);
    };
  }, []);

  return (
    <div
      aria-label={title}
      aria-live="assertive"
      aria-modal="true"
      role="alertdialog"
      className="fixed inset-0 z-[100] flex items-center justify-center bg-background/85 px-4 backdrop-blur-sm"
    >
      <div className="w-full max-w-md rounded-2xl border border-border bg-card p-6 text-center shadow-2xl sm:p-7">
        <div className="mx-auto mb-5 flex h-10 items-center justify-center gap-2" aria-hidden="true">
          {[0, 1, 2].map((index) => (
            <span
              key={`quiz-generation-dot-${index}`}
              className="h-2.5 w-2.5 animate-bounce rounded-full bg-primary"
              style={{ animationDelay: `${index * 160}ms` }}
            />
          ))}
        </div>
        <h2 className="text-lg font-semibold text-foreground">{title}</h2>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">{message}</p>
        <p className="mt-4 min-h-5 text-sm font-medium text-foreground" aria-live="polite">
          {ROTATING_MESSAGES[messageIndex]}
        </p>
        <p className="mt-3 text-xs text-muted-foreground">
          {helperText}
        </p>
      </div>
    </div>
  );
}
