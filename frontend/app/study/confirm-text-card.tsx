import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import type { NeedsTextConfirmationResponse } from "@/lib/api";

type ConfirmTextCardProps = {
  loading: boolean;
  needsConfirmation: NeedsTextConfirmationResponse;
  confirmedText: string;
  onConfirmedTextChange: (value: string) => void;
  onConfirm: () => void;
};

export function ConfirmTextCard({
  loading,
  needsConfirmation,
  confirmedText,
  onConfirmedTextChange,
  onConfirm,
}: ConfirmTextCardProps) {
  return (
    <Card className="space-y-4 border-amber-500/40">
      <div>
        <CardTitle className="mb-2">Confirm Extracted Text</CardTitle>
        <CardDescription>
          The uploaded image was a little unclear. Please edit the extracted text
          below, then continue.
        </CardDescription>
      </div>
      <textarea
        value={confirmedText}
        onChange={(event) => onConfirmedTextChange(event.target.value)}
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
        onClick={onConfirm}
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
  );
}
