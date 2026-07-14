export function buildWeeklyPacingEchoLine(
  weeksRemaining: number | null | undefined,
  goalLabel: string,
): string | null {
  if (weeksRemaining === null || weeksRemaining === undefined) {
    return null;
  }
  const weekLabel = weeksRemaining === 1 ? "1 week" : `${weeksRemaining} weeks`;
  return `That's another session toward this week's target — ${weekLabel} left on your ${goalLabel} countdown.`;
}
