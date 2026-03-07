"use client";

import { useMemo, useState } from "react";
import { FileImage, Loader2, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import {
  confirmReviewText,
  createReviewFromImage,
  createReviewFromText,
  isNeedsTextConfirmationResponse,
  type NeedsTextConfirmationResponse,
  type ReviewResponse,
} from "@/lib/api";

export default function StudyPage() {
  const [notesText, setNotesText] = useState("");
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imageInputKey, setImageInputKey] = useState(0);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [reviewResult, setReviewResult] = useState<ReviewResponse | null>(null);
  const [generatedAt, setGeneratedAt] = useState<Date | null>(null);
  const [needsConfirmation, setNeedsConfirmation] =
    useState<NeedsTextConfirmationResponse | null>(null);
  const [confirmedText, setConfirmedText] = useState("");

  const canGenerate = useMemo(
    () => notesText.trim().length > 0 || imageFile !== null,
    [imageFile, notesText],
  );

  const handleGenerateReview = async () => {
    if (!canGenerate || loading) {
      return;
    }

    setLoading(true);
    setErrorMessage(null);
    setReviewResult(null);
    setNeedsConfirmation(null);

    try {
      if (imageFile) {
        const response = await createReviewFromImage(imageFile);
        if (isNeedsTextConfirmationResponse(response)) {
          setNeedsConfirmation(response);
          setConfirmedText(response.extractedText);
          return;
        }
        setReviewResult(response);
        setGeneratedAt(new Date());
        return;
      }

      const response = await createReviewFromText(notesText);
      setReviewResult(response);
      setGeneratedAt(new Date());
    } catch (error) {
      const message =
        error instanceof Error
          ? error.message
          : "We could not generate your review right now. Please try again.";
      setErrorMessage(message);
    } finally {
      setLoading(false);
    }
  };

  const handleConfirmText = async () => {
    if (!needsConfirmation || confirmedText.trim().length === 0 || loading) {
      return;
    }

    setLoading(true);
    setErrorMessage(null);
    setReviewResult(null);

    try {
      const response = await confirmReviewText(needsConfirmation.id, confirmedText);
      setReviewResult(response);
      setGeneratedAt(new Date());
      setNeedsConfirmation(null);
    } catch (error) {
      const message =
        error instanceof Error
          ? error.message
          : "We could not generate your review right now. Please try again.";
      setErrorMessage(message);
    } finally {
      setLoading(false);
    }
  };

  const handleClearNotes = () => {
    setNotesText("");
    setImageFile(null);
    setImageInputKey((prev) => prev + 1);
    setReviewResult(null);
    setNeedsConfirmation(null);
    setConfirmedText("");
    setErrorMessage(null);
    setGeneratedAt(null);
  };

  const generatedLabel = useMemo(() => {
    if (!generatedAt) {
      return null;
    }
    const seconds = Math.floor((Date.now() - generatedAt.getTime()) / 1000);
    if (seconds < 60) {
      return "Generated just now";
    }
    const minutes = Math.floor(seconds / 60);
    return `Generated ${minutes}m ago`;
  }, [generatedAt]);

  const detectedTopic = useMemo(() => {
    if (!reviewResult) {
      return null;
    }
    const firstConcept = reviewResult.keyConcepts.find(
      (concept) => concept.trim().length > 0,
    );
    if (firstConcept) {
      return firstConcept;
    }
    return reviewResult.title;
  }, [reviewResult]);

  return (
    <main className="mx-auto w-full max-w-3xl space-y-8 px-6 py-10">
      <section className="space-y-2">
        <h1 className="text-3xl font-semibold text-foreground md:text-4xl">
          Turn Notes Into Review Materials
        </h1>
        <p className="text-base leading-relaxed text-foreground/75">
          Paste your notes or upload a photo. We&apos;ll organize everything
          into a clean summary, key concepts, and a quick practice quiz.
        </p>
      </section>

      <Card className="space-y-6">
        <div className="space-y-2">
          <label
            htmlFor="study-notes"
            className="text-sm font-medium text-foreground"
          >
            Study Notes
          </label>
          <textarea
            id="study-notes"
            value={notesText}
            onChange={(event) => setNotesText(event.target.value)}
            placeholder="Paste your class notes here..."
            className="min-h-40 w-full rounded-lg border border-border bg-background px-3 py-2 text-base leading-relaxed text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-blue-600"
          />
          <p className="text-sm text-foreground/70">
            Paste lecture notes, reviewer text, or interview preparation notes.
            Best results when the notes focus on a single topic.
          </p>
        </div>

        <div className="space-y-2">
          <label
            htmlFor="study-image"
            className="text-sm font-medium text-foreground"
          >
            Notes Photo (optional)
          </label>
          <div className="flex items-center gap-3 rounded-lg border border-border bg-background px-3 py-2">
            <FileImage className="h-4 w-4 text-foreground/60" />
            <input
              key={imageInputKey}
              id="study-image"
              type="file"
              accept="image/png,image/jpeg,image/webp"
              onChange={(event) => {
                const file = event.target.files?.[0] ?? null;
                setImageFile(file);
              }}
              className="w-full cursor-pointer text-sm text-foreground/75 file:mr-3 file:cursor-pointer file:rounded-lg file:border-0 file:bg-blue-600 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-white hover:file:bg-blue-700"
            />
          </div>
          <p className="text-xs text-foreground/65">
            Uploaded images are processed and deleted after review generation.
          </p>
          {imageFile ? (
            <p className="text-sm text-foreground/70">Selected: {imageFile.name}</p>
          ) : null}
        </div>

        <div className="flex flex-wrap gap-3">
          <Button
            type="button"
            disabled={!canGenerate || loading}
            onClick={handleGenerateReview}
            className="w-full sm:w-auto"
          >
            {loading ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Generating review materials...
              </>
            ) : (
              <>
                <Sparkles className="mr-2 h-4 w-4" />
                Generate Review
              </>
            )}
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={handleClearNotes}
            disabled={loading}
            className="w-full sm:w-auto"
          >
            Clear Notes
          </Button>
        </div>
      </Card>

      {errorMessage ? (
        <Card className="border-red-500/40 bg-red-50/70 dark:bg-red-950/20">
          <CardTitle className="mb-2">Couldn&apos;t Generate Review</CardTitle>
          <CardDescription>{errorMessage}</CardDescription>
        </Card>
      ) : null}

      {needsConfirmation ? (
        <Card className="space-y-4 border-amber-500/40">
          <div>
            <CardTitle className="mb-2">Confirm Extracted Text</CardTitle>
            <CardDescription>
              The uploaded image was a little unclear. Please edit the extracted
              text below, then continue.
            </CardDescription>
          </div>
          <textarea
            value={confirmedText}
            onChange={(event) => setConfirmedText(event.target.value)}
            className="min-h-40 w-full rounded-lg border border-border bg-background px-3 py-2 text-base leading-relaxed text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-blue-600"
          />
          <p className="text-sm text-foreground/70">
            OCR confidence:{" "}
            {needsConfirmation.meta.ocrConfidence !== null
              ? `${Math.round(needsConfirmation.meta.ocrConfidence * 100)}%`
              : "n/a"}
          </p>
          <Button
            type="button"
            onClick={handleConfirmText}
            disabled={loading || confirmedText.trim().length === 0}
          >
            {loading ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Creating your review materials...
              </>
            ) : (
              "Continue With Edited Text"
            )}
          </Button>
        </Card>
      ) : null}

      {loading ? (
        <section className="space-y-4">
          <h2 className="text-2xl font-semibold text-foreground">
            Generated Study Pack
          </h2>
          <Card className="space-y-3">
            <div className="flex items-center gap-2">
              <Loader2 className="h-4 w-4 animate-spin text-blue-600" />
              <CardTitle>Creating your study pack...</CardTitle>
            </div>
            <ul className="space-y-2 text-sm text-foreground/75">
              <li>Summarizing notes</li>
              <li>Extracting key concepts</li>
              <li>Generating practice quiz</li>
            </ul>
          </Card>
        </section>
      ) : null}

      {!loading && !reviewResult ? (
        <section className="space-y-4">
          <h2 className="text-2xl font-semibold text-foreground">
            Generated Study Pack
          </h2>
          <Card>
            <CardTitle className="mb-2">
              Your generated study pack will appear here
            </CardTitle>
            <CardDescription>
              Study Snap will create a summary, key concepts, and a short
              practice quiz from your notes.
            </CardDescription>
          </Card>
        </section>
      ) : null}

      {reviewResult && !loading ? (
        <section className="space-y-4">
          <h2 className="text-2xl font-semibold text-foreground">
            Generated Study Pack
          </h2>
          <p className="text-sm text-foreground/65">
            {generatedLabel} • {reviewResult.quiz.length} quiz questions • Topic
            detected: {detectedTopic}
          </p>

          <Card>
            <CardTitle className="mb-2">Title</CardTitle>
            <CardDescription>{reviewResult.title}</CardDescription>
          </Card>

          <Card>
            <CardTitle className="mb-2">Summary</CardTitle>
            <CardDescription>{reviewResult.summary}</CardDescription>
          </Card>

          <Card>
            <CardTitle className="mb-2">Key Concepts</CardTitle>
            <ul className="list-disc space-y-2 pl-5 text-base leading-relaxed text-foreground/80">
              {reviewResult.keyConcepts.map((concept) => (
                <li key={concept}>{concept}</li>
              ))}
            </ul>
          </Card>

          <Card className="border-emerald-500/40">
            <CardTitle className="mb-4">Practice Quiz</CardTitle>
            <div className="space-y-6">
              {reviewResult.quiz.map((item, index) => (
                <Card key={`${item.question}-${index}`} className="space-y-3">
                  <CardTitle className="text-base">
                    {index + 1}. {item.question}
                  </CardTitle>
                  {item.choices.length > 0 ? (
                    <ul className="space-y-2 text-sm">
                      {item.choices.map((choice) => {
                        const isCorrect = choice === item.answer;
                        return (
                          <li
                            key={choice}
                            className={`rounded-md border px-3 py-2 ${
                              isCorrect
                                ? "border-emerald-500/50 bg-emerald-500/10 text-foreground"
                                : "border-border text-foreground/75"
                            }`}
                          >
                            {choice}
                            {isCorrect ? (
                              <span className="ml-2 text-xs font-medium text-emerald-700 dark:text-emerald-300">
                                Correct answer
                              </span>
                            ) : null}
                          </li>
                        );
                      })}
                    </ul>
                  ) : null}
                  <CardDescription>
                    <span className="font-medium text-foreground">
                      Explanation:
                    </span>{" "}
                    {item.explanation}
                  </CardDescription>
                </Card>
              ))}
            </div>
          </Card>
        </section>
      ) : null}
    </main>
  );
}
