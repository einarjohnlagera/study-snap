import type { NoteListItemResponse } from "@/lib/api";
import {
  BROWSE_SUBJECTS_LIMIT,
  DISCOVERY_SECTION_LIMIT,
  computeDiscoveryScore,
  excludeById,
  getBrowseSubjects,
  getFeaturedNotes,
  getPopularNotes,
  getRecentNotes,
} from "@/lib/public-library-discovery";

// Minimal note factory for discovery tests
function makeNote(
  overrides: Partial<NoteListItemResponse> & { id: string },
): NoteListItemResponse {
  return {
    ownerUserId: "user-1",
    title: overrides.id,
    courseProgram: null,
    learnerLevel: null,
    subject: null,
    tags: [],
    contentPreview: "",
    summaryPreview: "",
    visibility: "PUBLIC",
    studyPackId: null,
    studyPackStatus: "DRAFT",
    quizCount: null,
    copyCount: 0,
    shareCount: 0,
    viewCount: 0,
    authorDisplayName: "Tester",
    isOfficialAuthor: false,
    isCurrentUser: false,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

describe("computeDiscoveryScore", () => {
  it("returns 0 for a note with no engagement", () => {
    expect(computeDiscoveryScore({ viewCount: 0, copyCount: 0, shareCount: 0 })).toBe(0);
  });

  it("weights copies highest (0.5), then views (0.4), then shares (0.1)", () => {
    expect(computeDiscoveryScore({ viewCount: 10, copyCount: 0, shareCount: 0 })).toBeCloseTo(4.0);
    expect(computeDiscoveryScore({ viewCount: 0, copyCount: 10, shareCount: 0 })).toBeCloseTo(5.0);
    expect(computeDiscoveryScore({ viewCount: 0, copyCount: 0, shareCount: 10 })).toBeCloseTo(1.0);
  });

  it("combines all signals correctly", () => {
    // 10 views × 0.4 + 5 copies × 0.5 + 2 shares × 0.1 = 4 + 2.5 + 0.2 = 6.7
    expect(computeDiscoveryScore({ viewCount: 10, copyCount: 5, shareCount: 2 })).toBeCloseTo(6.7);
  });

  it("treats null counts as 0", () => {
    expect(computeDiscoveryScore({ viewCount: null, copyCount: null, shareCount: null })).toBe(0);
  });
});

describe("getFeaturedNotes", () => {
  it("returns notes sorted by discovery score descending", () => {
    const highScore = makeNote({ id: "high", viewCount: 20, copyCount: 10, shareCount: 5 });
    const midScore = makeNote({ id: "mid", viewCount: 5, copyCount: 2, shareCount: 1 });
    const lowScore = makeNote({ id: "low", viewCount: 0, copyCount: 0, shareCount: 0 });

    const result = getFeaturedNotes([lowScore, midScore, highScore]);

    expect(result.map((n) => n.id)).toEqual(["high", "mid", "low"]);
  });

  it("tiebreaks by newest createdAt when scores are equal", () => {
    const older = makeNote({ id: "older", viewCount: 5, createdAt: "2026-01-01T00:00:00Z" });
    const newer = makeNote({ id: "newer", viewCount: 5, createdAt: "2026-03-01T00:00:00Z" });

    const result = getFeaturedNotes([older, newer]);

    expect(result.map((n) => n.id)).toEqual(["newer", "older"]);
  });

  it("limits to the specified count", () => {
    const notes = Array.from({ length: 10 }, (_, i) =>
      makeNote({ id: `note-${i}`, viewCount: 10 - i }),
    );

    expect(getFeaturedNotes(notes, 3)).toHaveLength(3);
    expect(getFeaturedNotes(notes, 3)[0].id).toBe("note-0");
  });

  it("defaults to DISCOVERY_SECTION_LIMIT", () => {
    const notes = Array.from({ length: 20 }, (_, i) =>
      makeNote({ id: `note-${i}`, viewCount: 20 - i }),
    );

    expect(getFeaturedNotes(notes)).toHaveLength(DISCOVERY_SECTION_LIMIT);
  });

  it("returns all notes when fewer than the limit exist", () => {
    const notes = [makeNote({ id: "a" }), makeNote({ id: "b" })];

    expect(getFeaturedNotes(notes, 6)).toHaveLength(2);
  });

  it("returns an empty array for an empty input", () => {
    expect(getFeaturedNotes([])).toEqual([]);
  });
});

describe("getPopularNotes", () => {
  it("sorts by copy count descending", () => {
    const mostCopied = makeNote({ id: "most-copied", copyCount: 15, viewCount: 1 });
    const leastCopied = makeNote({ id: "least-copied", copyCount: 2, viewCount: 50 });

    const result = getPopularNotes([leastCopied, mostCopied]);

    expect(result[0].id).toBe("most-copied");
  });

  it("uses view count as secondary sort when copy counts are equal", () => {
    const moreViewed = makeNote({ id: "more-viewed", copyCount: 5, viewCount: 20 });
    const lessViewed = makeNote({ id: "less-viewed", copyCount: 5, viewCount: 3 });

    const result = getPopularNotes([lessViewed, moreViewed]);

    expect(result[0].id).toBe("more-viewed");
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

    expect(result[0]).toBe("Physics");
    expect(result[1]).toBe("Biology");
    expect(result[2]).toBe("Chemistry");
  });

  it("normalizes subject dash formatting", () => {
    const notes = [
      makeNote({ id: "1", subject: "Biology-Cell Division" }),
      makeNote({ id: "2", subject: "Biology – Cell Division" }),
    ];

    const result = getBrowseSubjects(notes);

    // Both collapse into the same normalized subject
    expect(result).toHaveLength(1);
    expect(result[0]).toBe("Biology – Cell Division");
  });

  it("excludes notes with null or empty subjects", () => {
    const notes = [
      makeNote({ id: "1", subject: null }),
      makeNote({ id: "2", subject: "  " }),
      makeNote({ id: "3", subject: "Biology" }),
    ];

    const result = getBrowseSubjects(notes);

    expect(result).toEqual(["Biology"]);
  });

  it("tiebreaks equal-count subjects alphabetically", () => {
    const notes = [
      makeNote({ id: "1", subject: "Physics" }),
      makeNote({ id: "2", subject: "Biology" }),
    ];

    const result = getBrowseSubjects(notes);

    expect(result).toEqual(["Biology", "Physics"]);
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
