import {
  QUALITY_THRESHOLDS,
  computeQualityBadges,
  type QualityBadgeDef,
} from "@/lib/note-quality-badges";

// Fixed reference point for "now" in all tests
const NOW_ISO = "2026-04-07T12:00:00.000Z";

function withFakeNow(fn: () => void) {
  const realNow = Date.now;
  Date.now = () => new Date(NOW_ISO).getTime();
  try {
    fn();
  } finally {
    Date.now = realNow;
  }
}

function daysAgo(days: number): string {
  const ms = new Date(NOW_ISO).getTime() - days * 24 * 60 * 60 * 1000;
  return new Date(ms).toISOString();
}

function kinds(badges: QualityBadgeDef[]) {
  return badges.map((b) => b.kind);
}

describe("computeQualityBadges", () => {
  // --- zero engagement ---

  it("returns no badges for a note with zero views and zero copies", () => {
    withFakeNow(() => {
      expect(computeQualityBadges({ copyCount: 0, viewCount: 0, createdAt: daysAgo(30) })).toHaveLength(0);
    });
  });

  it("returns no badges when counts are null", () => {
    withFakeNow(() => {
      expect(computeQualityBadges({ copyCount: null, viewCount: null, createdAt: daysAgo(30) })).toHaveLength(0);
    });
  });

  it("returns no badges when counts are undefined", () => {
    withFakeNow(() => {
      expect(computeQualityBadges({})).toHaveLength(0);
    });
  });

  // --- High Quality ---

  it("shows High Quality badge when copies >= threshold AND views >= threshold", () => {
    withFakeNow(() => {
      const badges = computeQualityBadges({
        copyCount: QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_COPIES,
        viewCount: QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_VIEWS,
        createdAt: daysAgo(30),
      });
      expect(kinds(badges)).toContain("high-quality");
    });
  });

  it("does NOT show High Quality when copies are below threshold", () => {
    withFakeNow(() => {
      const badges = computeQualityBadges({
        copyCount: QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_COPIES - 1,
        viewCount: QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_VIEWS,
        createdAt: daysAgo(30),
      });
      expect(kinds(badges)).not.toContain("high-quality");
    });
  });

  it("does NOT show High Quality when views are below threshold", () => {
    withFakeNow(() => {
      const badges = computeQualityBadges({
        copyCount: QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_COPIES,
        viewCount: QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_VIEWS - 1,
        createdAt: daysAgo(30),
      });
      expect(kinds(badges)).not.toContain("high-quality");
    });
  });

  // --- Popular ---

  it("shows Popular badge when copies alone meet the threshold", () => {
    withFakeNow(() => {
      const badges = computeQualityBadges({
        copyCount: QUALITY_THRESHOLDS.POPULAR_MIN_COPIES,
        viewCount: 0,
        createdAt: daysAgo(30),
      });
      expect(kinds(badges)).toContain("popular");
    });
  });

  it("shows Popular badge when views alone meet the threshold", () => {
    withFakeNow(() => {
      const badges = computeQualityBadges({
        copyCount: 0,
        viewCount: QUALITY_THRESHOLDS.POPULAR_MIN_VIEWS,
        createdAt: daysAgo(30),
      });
      expect(kinds(badges)).toContain("popular");
    });
  });

  it("does NOT show Popular when neither copies nor views meet their thresholds", () => {
    withFakeNow(() => {
      const badges = computeQualityBadges({
        copyCount: QUALITY_THRESHOLDS.POPULAR_MIN_COPIES - 1,
        viewCount: QUALITY_THRESHOLDS.POPULAR_MIN_VIEWS - 1,
        createdAt: daysAgo(30),
      });
      expect(kinds(badges)).not.toContain("popular");
    });
  });

  // High Quality suppresses Popular (they don't both appear)
  it("does NOT show Popular when High Quality is already shown", () => {
    withFakeNow(() => {
      const badges = computeQualityBadges({
        // Meets both High Quality AND Popular thresholds
        copyCount: Math.max(QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_COPIES, QUALITY_THRESHOLDS.POPULAR_MIN_COPIES),
        viewCount: Math.max(QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_VIEWS, QUALITY_THRESHOLDS.POPULAR_MIN_VIEWS),
        createdAt: daysAgo(30),
      });
      expect(kinds(badges)).toContain("high-quality");
      expect(kinds(badges)).not.toContain("popular");
    });
  });

  // --- New ---

  it("shows New badge when note was created within NEW_WITHIN_DAYS days", () => {
    withFakeNow(() => {
      const badges = computeQualityBadges({
        copyCount: 0,
        viewCount: 0,
        createdAt: daysAgo(QUALITY_THRESHOLDS.NEW_WITHIN_DAYS - 1),
      });
      expect(kinds(badges)).toContain("new");
    });
  });

  it("shows New badge on the exact boundary day", () => {
    withFakeNow(() => {
      const badges = computeQualityBadges({
        copyCount: 0,
        viewCount: 0,
        createdAt: daysAgo(QUALITY_THRESHOLDS.NEW_WITHIN_DAYS),
      });
      expect(kinds(badges)).toContain("new");
    });
  });

  it("does NOT show New badge when note is older than NEW_WITHIN_DAYS", () => {
    withFakeNow(() => {
      const badges = computeQualityBadges({
        copyCount: 0,
        viewCount: 0,
        createdAt: daysAgo(QUALITY_THRESHOLDS.NEW_WITHIN_DAYS + 1),
      });
      expect(kinds(badges)).not.toContain("new");
    });
  });

  it("does NOT show New badge when createdAt is null", () => {
    withFakeNow(() => {
      const badges = computeQualityBadges({ copyCount: 0, viewCount: 0, createdAt: null });
      expect(kinds(badges)).not.toContain("new");
    });
  });

  it("does NOT show New badge when createdAt is undefined", () => {
    withFakeNow(() => {
      const badges = computeQualityBadges({ copyCount: 0, viewCount: 0 });
      expect(kinds(badges)).not.toContain("new");
    });
  });

  it("does NOT show New badge for an invalid date string", () => {
    withFakeNow(() => {
      const badges = computeQualityBadges({ copyCount: 0, viewCount: 0, createdAt: "not-a-date" });
      expect(kinds(badges)).not.toContain("new");
    });
  });

  // --- combinations and max-2 cap ---

  it("shows both High Quality and New when both conditions are met (max 2)", () => {
    withFakeNow(() => {
      const badges = computeQualityBadges({
        copyCount: QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_COPIES,
        viewCount: QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_VIEWS,
        createdAt: daysAgo(1),
      });
      expect(kinds(badges)).toEqual(["high-quality", "new"]);
    });
  });

  it("shows both Popular and New when both conditions are met", () => {
    withFakeNow(() => {
      const badges = computeQualityBadges({
        // Popular by copies alone, below High Quality view threshold
        copyCount: QUALITY_THRESHOLDS.POPULAR_MIN_COPIES,
        viewCount: QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_VIEWS - 1,
        createdAt: daysAgo(1),
      });
      expect(kinds(badges)).toEqual(["popular", "new"]);
    });
  });

  it("never returns more than 2 badges", () => {
    withFakeNow(() => {
      const badges = computeQualityBadges({
        copyCount: QUALITY_THRESHOLDS.POPULAR_MIN_COPIES * 10,
        viewCount: QUALITY_THRESHOLDS.POPULAR_MIN_VIEWS * 10,
        createdAt: daysAgo(1),
      });
      expect(badges.length).toBeLessThanOrEqual(2);
    });
  });

  // --- badge label content ---

  it("High Quality badge has correct label", () => {
    withFakeNow(() => {
      const badges = computeQualityBadges({
        copyCount: QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_COPIES,
        viewCount: QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_VIEWS,
        createdAt: daysAgo(30),
      });
      const badge = badges.find((b) => b.kind === "high-quality");
      expect(badge?.label).toBe("High Quality");
    });
  });

  it("Popular badge has correct label", () => {
    withFakeNow(() => {
      const badges = computeQualityBadges({
        copyCount: QUALITY_THRESHOLDS.POPULAR_MIN_COPIES,
        viewCount: 0,
        createdAt: daysAgo(30),
      });
      const badge = badges.find((b) => b.kind === "popular");
      expect(badge?.label).toBe("Popular");
    });
  });

  it("New badge has correct label", () => {
    withFakeNow(() => {
      const badges = computeQualityBadges({
        copyCount: 0,
        viewCount: 0,
        createdAt: daysAgo(1),
      });
      const badge = badges.find((b) => b.kind === "new");
      expect(badge?.label).toBe("New");
    });
  });
});
