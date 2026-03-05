"use client";

import { useMemo, useState } from "react";
import { FileImage, Loader2, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";

export default function StudyPage() {
  const [notesText, setNotesText] = useState("");
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [hasResult, setHasResult] = useState(false);

  const canGenerate = useMemo(
    () => notesText.trim().length > 0 || imageFile !== null,
    [imageFile, notesText],
  );

  const handleGenerateReview = async () => {
    if (!canGenerate || loading) {
      return;
    }

    setLoading(true);
    setHasResult(false);

    // Placeholder generation flow until API wiring is added.
    await new Promise((resolve) => setTimeout(resolve, 1000));

    setLoading(false);
    setHasResult(true);
  };

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
          {imageFile ? (
            <p className="text-sm text-foreground/70">Selected: {imageFile.name}</p>
          ) : null}
        </div>

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
      </Card>

      {hasResult ? (
        <section className="space-y-4">
          <h2 className="text-2xl font-semibold text-foreground">Review Set</h2>

          <Card>
            <CardTitle className="mb-2">Title</CardTitle>
            <CardDescription>
              Photosynthesis and Energy Conversion
            </CardDescription>
          </Card>

          <Card>
            <CardTitle className="mb-2">Summary</CardTitle>
            <CardDescription>
              These notes explain how plants convert light energy into chemical
              energy. Chlorophyll in chloroplasts captures sunlight to power
              reactions that create glucose and release oxygen.
            </CardDescription>
          </Card>

          <Card>
            <CardTitle className="mb-2">Key Concepts</CardTitle>
            <ul className="list-disc space-y-2 pl-5 text-base leading-relaxed text-foreground/80">
              <li>Role of chlorophyll and chloroplasts</li>
              <li>Difference between light-dependent reactions and Calvin cycle</li>
              <li>How glucose and oxygen are produced</li>
            </ul>
          </Card>

          <Card className="border-emerald-500/40">
            <CardTitle className="mb-2">Practice Quiz (placeholder)</CardTitle>
            <CardDescription>
              1) What is the main purpose of chlorophyll? 2) Which stage uses
              carbon dioxide to produce sugars?
            </CardDescription>
          </Card>
        </section>
      ) : null}
    </main>
  );
}
