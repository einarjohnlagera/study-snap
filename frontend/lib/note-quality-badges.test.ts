import {
  QUALITY_THRESHOLDS,
  computeQualityBadges,
  type QualityBadgeDef,
} from "@/lib/note-quality-badges";

function kinds(badges: QualityBadgeDef[]) {
  return badges.map((b) => b.kind);
}

describe("computeQualityBadges", () => {
  // --- zero engagement ---

  it("returns no badges for a note with zero views and zero copies", () => {
    expect(computeQualityBadges({ copyCount: 0, likeCount: 0, viewCount: 0 })).toHaveLength(0);
  });

  it("returns no badges when counts are null", () => {
    expect(computeQualityBadges({ copyCount: null, likeCount: null, viewCount: null })).toHaveLength(0);
  });

  it("returns no badges when counts are undefined", () => {
    expect(computeQualityBadges({})).toHaveLength(0);
  });

  // --- High Quality ---

  it("shows High Quality badge when copies >= threshold AND views >= threshold", () => {
    const badges = computeQualityBadges({
      copyCount: QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_COPIES,
      likeCount: 0,
      viewCount: QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_VIEWS,
    });
    expect(kinds(badges)).toContain("high-quality");
  });

  it("does NOT show High Quality when copies are below threshold", () => {
    const badges = computeQualityBadges({
      copyCount: QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_COPIES - 1,
      likeCount: 0,
      viewCount: QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_VIEWS,
    });
    expect(kinds(badges)).not.toContain("high-quality");
  });

  it("does NOT show High Quality when views are below threshold", () => {
    const badges = computeQualityBadges({
      copyCount: QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_COPIES,
      likeCount: 0,
      viewCount: QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_VIEWS - 1,
    });
    expect(kinds(badges)).not.toContain("high-quality");
  });

  it("shows Well liked badge when likes meet the threshold", () => {
    const badges = computeQualityBadges({
      copyCount: 0,
      likeCount: QUALITY_THRESHOLDS.WELL_LIKED_MIN_LIKES,
      viewCount: 0,
    });
    expect(kinds(badges)).toContain("well-liked");
  });

  // --- Popular ---

  it("shows Popular badge when copies alone meet the threshold", () => {
    const badges = computeQualityBadges({
      copyCount: QUALITY_THRESHOLDS.POPULAR_MIN_COPIES,
      likeCount: 0,
      viewCount: 0,
    });
    expect(kinds(badges)).toContain("popular");
  });

  it("shows Popular badge when views alone meet the threshold", () => {
    const badges = computeQualityBadges({
      copyCount: 0,
      likeCount: 0,
      viewCount: QUALITY_THRESHOLDS.POPULAR_MIN_VIEWS,
    });
    expect(kinds(badges)).toContain("popular");
  });

  it("does NOT show Popular when neither copies nor views meet their thresholds", () => {
    const badges = computeQualityBadges({
      copyCount: QUALITY_THRESHOLDS.POPULAR_MIN_COPIES - 1,
      likeCount: 0,
      viewCount: QUALITY_THRESHOLDS.POPULAR_MIN_VIEWS - 1,
    });
    expect(kinds(badges)).not.toContain("popular");
  });

  // High Quality suppresses Popular (they don't both appear)
  it("does NOT show Popular when High Quality is already shown", () => {
    const badges = computeQualityBadges({
      // Meets both High Quality AND Popular thresholds
      copyCount: Math.max(QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_COPIES, QUALITY_THRESHOLDS.POPULAR_MIN_COPIES),
      likeCount: 0,
      viewCount: Math.max(QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_VIEWS, QUALITY_THRESHOLDS.POPULAR_MIN_VIEWS),
    });
    expect(kinds(badges)).toContain("high-quality");
    expect(kinds(badges)).not.toContain("popular");
  });

  // --- badge label content ---

  it("High Quality badge has correct label", () => {
    const badges = computeQualityBadges({
      copyCount: QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_COPIES,
      likeCount: 0,
      viewCount: QUALITY_THRESHOLDS.HIGH_QUALITY_MIN_VIEWS,
    });
    const badge = badges.find((b) => b.kind === "high-quality");
    expect(badge?.label).toBe("High Quality");
  });

  it("Popular badge has correct label", () => {
    const badges = computeQualityBadges({
      copyCount: QUALITY_THRESHOLDS.POPULAR_MIN_COPIES,
      likeCount: 0,
      viewCount: 0,
    });
    const badge = badges.find((b) => b.kind === "popular");
    expect(badge?.label).toBe("Popular");
  });

  it("Well liked badge has correct label", () => {
    const badges = computeQualityBadges({
      copyCount: 0,
      likeCount: QUALITY_THRESHOLDS.WELL_LIKED_MIN_LIKES,
      viewCount: 0,
    });
    const badge = badges.find((b) => b.kind === "well-liked");
    expect(badge?.label).toBe("Well liked");
  });
});
