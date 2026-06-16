export const STUDY_PACK_GENERATION_MESSAGES = [
  "Building your Study Pack...",
  "Turning your notes into a summary, key concepts, and quiz...",
  "Preparing your reviewer...",
  "Almost there - organizing your study material...",
  "This can take a bit longer for larger notes.",
] as const;

export const STUDY_PACK_GENERATION_POLL_INTERVAL_MS = 3000;
export const STUDY_PACK_GENERATION_MESSAGE_ROTATION_MS = 4500;

// Library auto-refresh while generation is in flight (bulk + single-note).
// The quiet window must exceed the longest inter-topic content-gen gap of a
// throttled SEQUENTIAL bulk batch — otherwise the normal pause between topics
// (zero rows generating, no new rows yet) reads as "settled" and the rest of
// the batch is truncated until a manual refresh. ~12 quiet ticks at 3s = ~36s.
export const LIBRARY_GENERATION_POLL_QUIET_TICKS = 12;
// Absolute backstop so a wedged backend can never poll forever. ~5 min at 3s.
export const LIBRARY_GENERATION_POLL_MAX_TICKS = 100;

export function resolveStudyPackGenerationMessage(index: number): string {
  const safeIndex = Number.isFinite(index) ? Math.max(0, index) : 0;
  return STUDY_PACK_GENERATION_MESSAGES[safeIndex % STUDY_PACK_GENERATION_MESSAGES.length]!;
}
