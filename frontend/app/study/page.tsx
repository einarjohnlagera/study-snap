"use client";

import { useSearchParams } from "next/navigation";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { ConfirmTextCard } from "./confirm-text-card";
import { ReviewResults } from "./review-results";
import { StudyInputCard } from "./study-input-card";
import { useStudyReview } from "./use-study-review";

export default function StudyPage() {
  const searchParams = useSearchParams();
  const demoMode = searchParams.get("demo") === "true";

  const {
    notesText,
    setNotesText,
    imageFile,
    setImageFile,
    imageInputKey,
    loading,
    errorMessage,
    reviewResult,
    needsConfirmation,
    confirmedText,
    setConfirmedText,
    canGenerate,
    generatedLabel,
    detectedTopic,
    handleGenerateReview,
    handleConfirmText,
    handleClearNotes,
  } = useStudyReview(demoMode);

  return (
    <main className="mx-auto w-full max-w-3xl space-y-8 px-6 py-10">
      <section className="space-y-2">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-3xl font-semibold text-foreground md:text-4xl">
            Turn Notes Into Review Materials
          </h1>
          {demoMode ? (
            <span className="inline-flex items-center rounded-full border border-blue-500/40 bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700 dark:bg-blue-950/30 dark:text-blue-300">
              Demo mode
            </span>
          ) : null}
        </div>
        <p className="text-base leading-relaxed text-foreground/75">
          Paste your notes or upload a photo. We&apos;ll organize everything
          into a clean summary, key concepts, and a quick practice quiz.
        </p>
      </section>

      <StudyInputCard
        notesText={notesText}
        onNotesTextChange={setNotesText}
        imageFile={imageFile}
        onImageFileChange={setImageFile}
        imageInputKey={imageInputKey}
        canGenerate={canGenerate}
        loading={loading}
        onGenerate={() => {
          void handleGenerateReview();
        }}
        onClear={handleClearNotes}
      />

      {errorMessage ? (
        <Card className="border-red-500/40 bg-red-50/70 dark:bg-red-950/20">
          <CardTitle className="mb-2">Couldn&apos;t Generate Review</CardTitle>
          <CardDescription>{errorMessage}</CardDescription>
        </Card>
      ) : null}

      {needsConfirmation ? (
        <ConfirmTextCard
          loading={loading}
          needsConfirmation={needsConfirmation}
          confirmedText={confirmedText}
          onConfirmedTextChange={setConfirmedText}
          onConfirm={() => {
            void handleConfirmText();
          }}
        />
      ) : null}

      <ReviewResults
        loading={loading}
        demoMode={demoMode}
        reviewResult={reviewResult}
        generatedLabel={generatedLabel}
        detectedTopic={detectedTopic}
      />
    </main>
  );
}
