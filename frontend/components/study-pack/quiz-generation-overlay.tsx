type QuizGenerationOverlayProps = {
  title?: string;
  message?: string;
};

export function QuizGenerationOverlay({
  title = "Generating your quiz...",
  message = "Preparing your questions...",
}: QuizGenerationOverlayProps) {
  return (
    <div
      aria-label={title}
      aria-live="assertive"
      aria-modal="true"
      role="alertdialog"
      className="fixed inset-0 z-[100] flex items-center justify-center bg-background/85 px-4 backdrop-blur-sm"
    >
      <div className="w-full max-w-md rounded-2xl border border-border bg-card p-6 text-center shadow-2xl">
        <div className="mx-auto mb-4 h-10 w-10 animate-spin rounded-full border-2 border-primary/25 border-t-primary" />
        <h2 className="text-lg font-semibold text-foreground">{title}</h2>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">{message}</p>
        <p className="mt-4 text-xs text-muted-foreground">
          Please keep this page open while we prepare the quiz.
        </p>
      </div>
    </div>
  );
}
