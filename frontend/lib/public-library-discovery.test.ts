import type { NoteListItemResponse } from "@/lib/api";
import {
  BROWSE_SUBJECTS_LIMIT,
  DISCOVERY_SECTION_LIMIT,
  PUBLIC_LIBRARY_RANKING,
  computeDiscoveryScore,
  excludeById,
  getBrowseSubjects,
  getFeaturedNotes,
  getPopularNotes,
  getRecommendedNotes,
  getRecentNotes,
  isFeaturedEligible,
  isPopularNote,
} from "@/lib/public-library-discovery";

const NOW = new Date("2026-05-12T00:00:00Z");

// Minimal note factory for discovery tests
function makeNote(
  overrides: Partial<NoteListItemResponse> & { id: string },
): NoteListItemResponse {
  return {
    ownerUserId: "user-1",
    title: overrides.id,
    courseProgram: null,
    targetProfileType: "STUDENT",
    subject: null,
    tags: [],
    contentPreview: "Content preview",
    summaryPreview: "Summary preview",
    visibility: "PUBLIC",
    studyPackId: null,
    studyPackStatus: "STUDY_PACK_READY",
    quizCount: 2,
    copyCount: 0,
    likeCount: 0,
    shareCount: 0,
    viewCount: 0,
    authorDisplayName: "Tester",
    isOfficialAuthor: false,
    isCurrentUser: false,
    createdAt: NOW.toISOString(),
    updatedAt: NOW.toISOString(),
    likedByCurrentUser: false,
    ...overrides,
  };
}

describe("computeDiscoveryScore", () => {
  it("returns 0 for a note with no engagement", () => {
    expect(computeDiscoveryScore({ viewCount: 0, copyCount: 0, likeCount: 0, createdAt: NOW.toISOString() }, NOW)).toBe(0);
  });

  it("weights copies at 3x views and likes at 2x views", () => {
    expect(computeDiscoveryScore({ viewCount: 10, copyCount: 0, likeCount: 0, createdAt: NOW.toISOString() }, NOW)).toBe(10);
    expect(computeDiscoveryScore({ viewCount: 0, copyCount: 10, likeCount: 0, createdAt: NOW.toISOString() }, NOW)).toBe(
      10 * PUBLIC_LIBRARY_RANKING.FEATURED_COPY_WEIGHT,
    );
    expect(computeDiscoveryScore({ viewCount: 0, copyCount: 0, likeCount: 10, createdAt: NOW.toISOString() }, NOW)).toBe(
      10 * PUBLIC_LIBRARY_RANKING.FEATURED_LIKE_WEIGHT,
    );
  });

  it("combines views, copies, and likes correctly", () => {
    expect(computeDiscoveryScore({ viewCount: 10, copyCount: 5, likeCount: 4, createdAt: NOW.toISOString() }, NOW)).toBe(33);
  });

  it("treats null counts as 0", () => {
    expect(computeDiscoveryScore({ viewCount: null, copyCount: null, likeCount: null, createdAt: NOW.toISOString() }, NOW)).toBe(0);
  });

  it("reduces score for a 30-day-old note by the half-life factor", () => {
    const thirtyDaysAgo = new Date(NOW.getTime() - 30 * 24 * 60 * 60 * 1000).toISOString();
    // factor = 1 / (1 + 30/30) = 0.5 → score = 10 * 0.5 = 5
    expect(computeDiscoveryScore({ viewCount: 10, copyCount: 0, likeCount: 0, createdAt: thirtyDaysAgo }, NOW)).toBeCloseTo(5, 2);
  });

  it("applies the minimum decay floor for very old notes", () => {
    const veryOld = new Date(NOW.getTime() - 365 * 10 * 24 * 60 * 60 * 1000).toISOString();
    // floor = FEATURED_DECAY_MIN_FACTOR → score = 10 * 0.1 = 1
    expect(
      computeDiscoveryScore({ viewCount: 10, copyCount: 0, likeCount: 0, createdAt: veryOld }, NOW),
    ).toBeCloseTo(10 * PUBLIC_LIBRARY_RANKING.FEATURED_DECAY_MIN_FACTOR, 4);
  });
});

describe("isFeaturedEligible", () => {
  it("requires a public study-pack-ready note with summary, quiz content, and note preview", () => {
    expect(
      isFeaturedEligible(
        makeNote({
          id: "eligible",
          summaryPreview: "Strong summary",
          quizCount: 2,
          contentPreview: "Meaningful preview",
        }),
      ),
    ).toBe(true);
  });

  it("rejects notes missing study-ready quality signals", () => {
    expect(
      isFeaturedEligible(
        makeNote({
          id: "draft",
          studyPackStatus: "DRAFT",
          summaryPreview: "Summary",
          quizCount: 2,
          contentPreview: "Preview",
        }),
      ),
    ).toBe(false);
    expect(
      isFeaturedEligible(
        makeNote({
          id: "no-summary",
          summaryPreview: "   ",
          quizCount: 2,
          contentPreview: "Preview",
        }),
      ),
    ).toBe(false);
    expect(
      isFeaturedEligible(
        makeNote({
          id: "no-quiz",
          summaryPreview: "Summary",
          quizCount: 0,
          contentPreview: "Preview",
        }),
      ),
    ).toBe(false);
  });
});

describe("getFeaturedNotes", () => {
  it("returns only eligible notes sorted by decay-adjusted score descending", () => {
    const highScore = makeNote({
      id: "high",
      viewCount: 20,
      copyCount: 10,
      summaryPreview: "High summary",
      quizCount: 4,
      contentPreview: "High preview",
    });
    const midScore = makeNote({
      id: "mid",
      viewCount: 5,
      copyCount: 2,
      summaryPreview: "Mid summary",
      quizCount: 2,
      contentPreview: "Mid preview",
    });
    const ineligible = makeNote({
      id: "draft",
      studyPackStatus: "DRAFT",
      viewCount: 100,
      copyCount: 100,
      summaryPreview: "",
      quizCount: 0,
      contentPreview: "Draft preview",
    });

    const result = getFeaturedNotes([ineligible, midScore, highScore], DISCOVERY_SECTION_LIMIT, NOW);

    expect(result.map((n) => n.id)).toEqual(["high", "mid"]);
  });

  it("ranks fresher notes higher when raw engagement scores are equal", () => {
    const stale = makeNote({
      id: "stale",
      viewCount: 10,
      createdAt: new Date(NOW.getTime() - 90 * 24 * 60 * 60 * 1000).toISOString(),
    });
    const fresh = makeNote({
      id: "fresh",
      viewCount: 10,
      createdAt: new Date(NOW.getTime() - 24 * 60 * 60 * 1000).toISOString(),
    });

    const result = getFeaturedNotes([stale, fresh], DISCOVERY_SECTION_LIMIT, NOW);

    expect(result[0].id).toBe("fresh");
  });

  it("tiebreaks by copies then views when decay factors are equal (same createdAt)", () => {
    const sameCreatedAt = NOW.toISOString();
    const moreCopies = makeNote({
      id: "more-copies",
      viewCount: 5,
      copyCount: 3,
      createdAt: sameCreatedAt,
      summaryPreview: "Summary",
      quizCount: 2,
      contentPreview: "Preview",
    });
    const moreViews = makeNote({
      id: "more-views",
      viewCount: 11,
      copyCount: 1,
      createdAt: sameCreatedAt,
      summaryPreview: "Summary",
      quizCount: 2,
      contentPreview: "Preview",
    });
    const balanced = makeNote({
      id: "balanced",
      viewCount: 8,
      copyCount: 2,
      createdAt: sameCreatedAt,
      summaryPreview: "Summary",
      quizCount: 2,
      contentPreview: "Preview",
    });

    const result = getFeaturedNotes([balanced, moreViews, moreCopies], DISCOVERY_SECTION_LIMIT, NOW);

    // All have raw score 14 and identical decay factor → tiebreak by copies desc
    expect(result.map((n) => n.id)).toEqual(["more-copies", "balanced", "more-views"]);
  });

  it("limits to the specified count", () => {
    const notes = Array.from({ length: 10 }, (_, i) =>
      makeNote({ id: `note-${i}`, viewCount: 10 - i }),
    );

    expect(getFeaturedNotes(notes, 3, NOW)).toHaveLength(3);
    expect(getFeaturedNotes(notes, 3, NOW)[0].id).toBe("note-0");
  });

  it("defaults to DISCOVERY_SECTION_LIMIT", () => {
    const notes = Array.from({ length: 20 }, (_, i) =>
      makeNote({ id: `note-${i}`, viewCount: 20 - i }),
    );

    expect(getFeaturedNotes(notes, DISCOVERY_SECTION_LIMIT, NOW)).toHaveLength(DISCOVERY_SECTION_LIMIT);
  });

  it("returns all notes when fewer than the limit exist", () => {
    const notes = [makeNote({ id: "a" }), makeNote({ id: "b" })];

    expect(getFeaturedNotes(notes, 6, NOW)).toHaveLength(2);
  });

  it("returns an empty array for an empty input", () => {
    expect(getFeaturedNotes([], DISCOVERY_SECTION_LIMIT, NOW)).toEqual([]);
  });
});

describe("getRecommendedNotes", () => {
  it("ranks all notes by discovery score without applying Featured eligibility", () => {
    const highestScore = makeNote({
      id: "highest-score",
      studyPackStatus: "DRAFT",
      viewCount: 8,
      copyCount: 4,
    });
    const newestWithoutEngagement = makeNote({
      id: "newest",
      createdAt: NOW.toISOString(),
    });

    expect(getRecommendedNotes([newestWithoutEngagement, highestScore], NOW).map((note) => note.id))
      .toEqual(["highest-score", "newest"]);
  });

  it("uses a stable fallback for malformed creation dates", () => {
    const malformedDate = makeNote({ id: "malformed-date", createdAt: "not-a-date" });
    const validDate = makeNote({ id: "valid-date", createdAt: NOW.toISOString() });

    expect(getRecommendedNotes([malformedDate, validDate], NOW).map((note) => note.id))
      .toEqual(["valid-date", "malformed-date"]);
  });
});

describe("getPopularNotes", () => {
  it("keeps only notes that meet the Popular threshold", () => {
    const byCopies = makeNote({ id: "by-copies", copyCount: PUBLIC_LIBRARY_RANKING.POPULAR_MIN_COPIES });
    const byViews = makeNote({ id: "by-views", viewCount: PUBLIC_LIBRARY_RANKING.POPULAR_MIN_VIEWS });
    const belowThreshold = makeNote({
      id: "below-threshold",
      copyCount: PUBLIC_LIBRARY_RANKING.POPULAR_MIN_COPIES - 1,
      viewCount: PUBLIC_LIBRARY_RANKING.POPULAR_MIN_VIEWS - 1,
    });

    expect(getPopularNotes([byCopies, byViews, belowThreshold]).map((note) => note.id)).toEqual([
      "by-copies",
      "by-views",
    ]);
  });

  it("sorts by copy count descending", () => {
    const mostCopied = makeNote({ id: "most-copied", copyCount: 15, viewCount: 1 });
    const leastCopied = makeNote({ id: "least-copied", copyCount: 3, viewCount: 50 });

    const result = getPopularNotes([leastCopied, mostCopied]);

    expect(result[0].id).toBe("most-copied");
  });

  it("uses view count as secondary sort when copy counts are equal", () => {
    const moreViewed = makeNote({ id: "more-viewed", copyCount: 5, viewCount: 20 });
    const lessViewed = makeNote({ id: "less-viewed", copyCount: 5, viewCount: 3 });

    const result = getPopularNotes([lessViewed, moreViewed]);

    expect(result[0].id).toBe("more-viewed");
  });

  it("uses likes as tertiary sort when copies and views are equal", () => {
    const moreLiked = makeNote({ id: "more-liked", copyCount: 5, viewCount: 20, likeCount: 12 });
    const lessLiked = makeNote({ id: "less-liked", copyCount: 5, viewCount: 20, likeCount: 2 });

    const result = getPopularNotes([lessLiked, moreLiked]);

    expect(result[0].id).toBe("more-liked");
  });

  it("uses newest createdAt as tertiary tiebreak", () => {
    const older = makeNote({ id: "older", copyCount: 5, viewCount: 5, createdAt: "2026-01-01T00:00:00Z" });
    const newer = makeNote({ id: "newer", copyCount: 5, viewCount: 5, createdAt: "2026-03-01T00:00:00Z" });

    expect(getPopularNotes([older, newer])[0].id).toBe("newer");
  });

  it("limits to the specified count", () => {
    const notes = Array.from({ length: 10 }, (_, i) =>
      makeNote({ id: `note-${i}`, copyCount: 10 - i }),
    );

    expect(getPopularNotes(notes, 4)).toHaveLength(4);
  });

  it("returns an empty array for an empty input", () => {
    expect(getPopularNotes([])).toEqual([]);
  });
});

describe("isPopularNote", () => {
  it("returns true when a note meets either the copy or view threshold", () => {
    expect(isPopularNote({ copyCount: PUBLIC_LIBRARY_RANKING.POPULAR_MIN_COPIES, viewCount: 0 })).toBe(true);
    expect(isPopularNote({ copyCount: 0, viewCount: PUBLIC_LIBRARY_RANKING.POPULAR_MIN_VIEWS })).toBe(true);
  });

  it("returns false when a note misses both thresholds", () => {
    expect(
      isPopularNote({
        copyCount: PUBLIC_LIBRARY_RANKING.POPULAR_MIN_COPIES - 1,
        viewCount: PUBLIC_LIBRARY_RANKING.POPULAR_MIN_VIEWS - 1,
      }),
    ).toBe(false);
  });
});

describe("getRecentNotes", () => {
  it("sorts by createdAt descending", () => {
    const oldest = makeNote({ id: "oldest", createdAt: "2026-01-01T00:00:00Z" });
    const newest = makeNote({ id: "newest", createdAt: "2026-04-01T00:00:00Z" });
    const middle = makeNote({ id: "middle", createdAt: "2026-02-15T00:00:00Z" });

    const result = getRecentNotes([oldest, middle, newest]);

    expect(result.map((n) => n.id)).toEqual(["newest", "middle", "oldest"]);
  });

  it("limits to the specified count", () => {
    const notes = Array.from({ length: 10 }, (_, i) =>
      makeNote({ id: `note-${i}`, createdAt: `2026-0${(i % 3) + 1}-01T00:00:00Z` }),
    );

    expect(getRecentNotes(notes, 3)).toHaveLength(3);
  });

  it("defaults to DISCOVERY_SECTION_LIMIT", () => {
    const notes = Array.from({ length: 20 }, (_, i) =>
      makeNote({ id: `note-${i}`, createdAt: `2026-01-${String(i + 1).padStart(2, "0")}T00:00:00Z` }),
    );

    expect(getRecentNotes(notes)).toHaveLength(DISCOVERY_SECTION_LIMIT);
  });

  it("returns an empty array for an empty input", () => {
    expect(getRecentNotes([])).toEqual([]);
  });
});

describe("getBrowseSubjects", () => {
  it("returns unique subjects sorted by note count descending", () => {
    const notes = [
      makeNote({ id: "1", subject: "Biology" }),
      makeNote({ id: "2", subject: "Biology" }),
      makeNote({ id: "3", subject: "Chemistry" }),
      makeNote({ id: "4", subject: "Physics" }),
      makeNote({ id: "5", subject: "Physics" }),
      makeNote({ id: "6", subject: "Physics" }),
    ];

    const result = getBrowseSubjects(notes);

    expect(result[0]).toEqual({ subject: "Physics", count: 3 });
    expect(result[1]).toEqual({ subject: "Biology", count: 2 });
    expect(result[2]).toEqual({ subject: "Chemistry", count: 1 });
  });

  it("normalizes subject dash formatting", () => {
    const notes = [
      makeNote({ id: "1", subject: "Biology-Cell Division" }),
      makeNote({ id: "2", subject: "Biology – Cell Division" }),
    ];

    const result = getBrowseSubjects(notes);

    // Both collapse into the same normalized subject
    expect(result).toHaveLength(1);
    expect(result[0]).toEqual({ subject: "Biology – Cell Division", count: 2 });
  });

  it("excludes notes with null or empty subjects", () => {
    const notes = [
      makeNote({ id: "1", subject: null }),
      makeNote({ id: "2", subject: "  " }),
      makeNote({ id: "3", subject: "Biology" }),
    ];

    const result = getBrowseSubjects(notes);

    expect(result).toEqual([{ subject: "Biology", count: 1 }]);
  });

  it("tiebreaks equal-count subjects alphabetically", () => {
    const notes = [
      makeNote({ id: "1", subject: "Physics" }),
      makeNote({ id: "2", subject: "Biology" }),
    ];

    const result = getBrowseSubjects(notes);

    expect(result).toEqual([{ subject: "Biology", count: 1 }, { subject: "Physics", count: 1 }]);
  });

  it("limits to BROWSE_SUBJECTS_LIMIT by default", () => {
    const notes = Array.from({ length: 20 }, (_, i) =>
      makeNote({ id: `note-${i}`, subject: `Subject ${i}` }),
    );

    expect(getBrowseSubjects(notes)).toHaveLength(BROWSE_SUBJECTS_LIMIT);
  });

  it("returns an empty array for an empty input", () => {
    expect(getBrowseSubjects([])).toEqual([]);
  });
});

describe("excludeById", () => {
  it("removes notes whose ids are in the exclude set", () => {
    const notes = [
      makeNote({ id: "keep-1" }),
      makeNote({ id: "exclude-me" }),
      makeNote({ id: "keep-2" }),
    ];

    const result = excludeById(notes, new Set(["exclude-me"]));

    expect(result.map((n) => n.id)).toEqual(["keep-1", "keep-2"]);
  });

  it("returns all notes when exclude set is empty", () => {
    const notes = [makeNote({ id: "a" }), makeNote({ id: "b" })];

    expect(excludeById(notes, new Set())).toHaveLength(2);
  });

  it("returns an empty array for an empty input", () => {
    expect(excludeById([], new Set(["x"]))).toEqual([]);
  });
});
