"use client";

type ApplicableProgramsProvenanceProps = {
  programs: string[];
  copiedFromNoteId?: string | null;
  loading?: boolean;
  error?: string | null;
  onRetry?: () => void;
};

export function ApplicableProgramsProvenance({
  programs,
  copiedFromNoteId,
  loading = false,
  error = null,
  onRetry,
}: Readonly<ApplicableProgramsProvenanceProps>) {
  if (loading) {
    return <p className="text-sm text-foreground/60">Loading Course / Program(s)...</p>;
  }
  if (error) {
    return (
      <div className="space-y-1">
        <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
        {onRetry ? (
          <button type="button" className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400" onClick={onRetry}>
            Retry
          </button>
        ) : null}
      </div>
    );
  }
  if (programs.length === 0) {
    return null;
  }
  return (
    <div className="space-y-1">
      <p className="text-sm text-foreground/75">{programs.join(" · ")}</p>
      {copiedFromNoteId ? (
        <p className="text-xs text-foreground/60">
          Set by the note this was copied from. Your own course or program is on your profile.
        </p>
      ) : null}
    </div>
  );
}
