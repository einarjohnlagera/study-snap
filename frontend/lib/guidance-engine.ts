import { hasSeenTip } from "@/lib/guidance";

export type GuidanceRule = {
  id: string;
  priority: number;
  condition: () => boolean;
  message: string;
};

export function pickActiveGuidance(rules: GuidanceRule[]): GuidanceRule | null {
  return (
    [...rules]
      .sort((a, b) => a.priority - b.priority)
      .find((rule) => !hasSeenTip(rule.id) && rule.condition()) ?? null
  );
}
