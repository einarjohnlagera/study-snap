"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { getSampleNotePreset } from "@/lib/sample-notes";
import { ConfirmTextCard } from "./confirm-text-card";
import { StudyPackResults } from "./study-pack-results";
import { StudyInputCard } from "./study-input-card";
import { useStudyPack } from "./use-study-pack";

type StudyPageClientProps = {
  forcedDemoMode?: boolean;
};

export default function StudyPageClient({ forcedDemoMode = false }: StudyPageClientProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const demoMode = forcedDemoMode || searchParams.get("demo") === "true";
  const focusUpload = searchParams.get("focus") === "upload";
  const samplePreset = getSampleNotePreset(searchParams.get("sample"));
  const isSampleNote = Boolean(samplePreset);
  const [redirecting, setRedirecting] = useState(false);

  const {
    notesText,
    setNotesText,
    imageFile,
    setImageFile,
    imageInputKey,
    loading,
    errorMessage,
    studyPackResult,
    needsConfirmation,
    confirmedText,
    setConfirmedText,
    canGenerate,
    generatedLabel,
    detectedTopic,
    ocrFlowState,
    ocrStatusMessage,
    handleGenerateStudyPack,
    handleConfirmText,
  } = useStudyPack(demoMode, samplePreset?.content ?? "");

  const handleGenerate = async () => {
    if (redirecting) {
      return;
    }
    const created = await handleGenerateStudyPack();
    if (!demoMode && created) {
      setRedirecting(true);
      router.push(`/study-packs/${created.id}?from=study&created=1`);
    }
  };

  const handleConfirm = async () => {
    if (redirecting) {
      return;
    }
    const created = await handleConfirmText();
    if (!demoMode && created) {
      setRedirecting(true);
      router.push(`/study-packs/${created.id}?from=study&created=1`);
    }
  };

  return (
    <main className="mx-auto w-full max-w-3xl space-y-8 px-4 py-6 sm:px-6 sm:py-10">
      <section className="space-y-2">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold text-foreground sm:text-3xl md:text-4xl">Create Study Pack</h1>
          {demoMode ? (
            <span className="inline-flex items-center rounded-full border border-blue-500/40 bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700 dark:bg-blue-950/30 dark:text-blue-300">
              Demo mode
            </span>
          ) : null}
        </div>
        <p className="text-base leading-relaxed text-foreground/75">
          Paste your notes, then generate a Study Pack. You can edit title, subject, and tags after generation.
        </p>
      </section>

      <StudyInputCard
        isSampleNote={isSampleNote}
        focusUpload={focusUpload}
        notesText={notesText}
        onNotesTextChange={setNotesText}
        imageFile={imageFile}
        onImageFileChange={setImageFile}
        imageInputKey={imageInputKey}
        canGenerate={canGenerate}
        loading={loading || redirecting}
        ocrFlowState={ocrFlowState}
        ocrStatusMessage={ocrStatusMessage}
        onGenerate={() => {
          void handleGenerate();
        }}
      />

      {errorMessage ? (
        <Card className="border-red-500/40 bg-red-50/70 p-4 dark:bg-red-950/20 sm:p-6">
          <CardTitle className="mb-2">
            {ocrFlowState === "failure" ? "Couldn't Process Notes Image" : "Couldn't Generate Study Pack"}
          </CardTitle>
          <CardDescription>{errorMessage}</CardDescription>
        </Card>
      ) : null}

      {needsConfirmation ? (
        <ConfirmTextCard
          loading={loading || redirecting}
          needsConfirmation={needsConfirmation}
          confirmedText={confirmedText}
          onConfirmedTextChange={setConfirmedText}
          onConfirm={() => {
            void handleConfirm();
          }}
        />
      ) : null}

      {demoMode ? (
        <StudyPackResults
          loading={loading}
          demoMode={demoMode}
          studyPackResult={studyPackResult}
          generatedLabel={generatedLabel}
          detectedTopic={detectedTopic}
        />
      ) : null}
    </main>
  );
}
