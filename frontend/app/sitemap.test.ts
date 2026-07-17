import sitemap from "./sitemap";
import { getServerPublicNotes } from "@/lib/server-public-notes";

jest.mock("@/lib/server-public-notes", () => {
  const actual = jest.requireActual("@/lib/server-public-notes");
  return {
    ...actual,
    getServerPublicNotes: jest.fn(),
  };
});

describe("sitemap metadata route", () => {
  beforeEach(() => {
    (getServerPublicNotes as jest.Mock).mockReset();
  });

  function buildScienceNote(overrides: Partial<Record<string, unknown>> = {}) {
    return {
      id: "note-science",
      title: "Cell Structure",
      subject: "Science",
      tags: [],
      contentPreview: "Cells",
      summaryPreview: "Cell summary",
      visibility: "PUBLIC",
      studyPackId: "pack-1",
      studyPackStatus: "STUDY_PACK_READY",
      quizCount: 5,
      authorDisplayName: "Study Buddy",
      isOfficialAuthor: false,
      isCurrentUser: false,
      updatedAt: "2026-03-24T08:30:00Z",
      ...overrides,
    };
  }

  it("includes only public SEO-safe pages, subject pages at or above the depth threshold, and public note URLs", async () => {
    (getServerPublicNotes as jest.Mock).mockResolvedValue([
      // 6 Science notes — meets SUBJECT_PAGE_INDEX_THRESHOLD, subject page appears in the sitemap.
      buildScienceNote({ id: "note-science-1", title: "Cell Structure" }),
      buildScienceNote({ id: "note-science-2", title: "Photosynthesis" }),
      buildScienceNote({ id: "note-science-3", title: "Genetics" }),
      buildScienceNote({ id: "note-science-4", title: "Evolution" }),
      buildScienceNote({ id: "note-science-5", title: "Ecosystems" }),
      buildScienceNote({ id: "note-science-6", title: "Chemical Reactions" }),
      // 1 Accounting note — below the threshold, subject page must NOT appear, but its own note URL still does.
      {
        id: "note-accounting",
        title: "Journal Entries",
        subject: "Accounting",
        tags: [],
        contentPreview: "Entries",
        summaryPreview: "Entries summary",
        visibility: "PUBLIC",
        studyPackId: "pack-2",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 5,
        authorDisplayName: "Study Buddy",
        isOfficialAuthor: false,
        isCurrentUser: false,
        updatedAt: "2026-03-22T08:30:00Z",
      },
    ]);

    const entries = await sitemap();

    expect(entries).toEqual(expect.arrayContaining([
      {
        url: "https://notelib.app/",
        changeFrequency: "weekly",
        priority: 1,
      },
      {
        url: "https://notelib.app/pricing",
        changeFrequency: "weekly",
        priority: 0.9,
      },
      {
        url: "https://notelib.app/how-it-works",
        changeFrequency: "weekly",
        priority: 0.9,
      },
      {
        url: "https://notelib.app/learn",
        changeFrequency: "weekly",
        priority: 0.9,
      },
      {
        url: "https://notelib.app/privacy",
        changeFrequency: "monthly",
        priority: 0.3,
      },
      {
        url: "https://notelib.app/terms",
        changeFrequency: "monthly",
        priority: 0.3,
      },
      {
        url: "https://notelib.app/refund",
        changeFrequency: "monthly",
        priority: 0.3,
      },
      {
        url: "https://notelib.app/public/library",
        changeFrequency: "daily",
        priority: 0.9,
      },
      {
        url: "https://notelib.app/exam",
        changeFrequency: "weekly",
        priority: 0.9,
      },
      {
        url: "https://notelib.app/exam/ale",
        changeFrequency: "daily",
        priority: 0.9,
      },
      {
        url: "https://notelib.app/exam/pnle",
        changeFrequency: "daily",
        priority: 0.9,
      },
      {
        url: "https://notelib.app/exam/let",
        changeFrequency: "daily",
        priority: 0.9,
      },
      {
        url: "https://notelib.app/public/library/science",
        lastModified: "2026-03-24T08:30:00Z",
        changeFrequency: "daily",
        priority: 0.8,
      },
      {
        url: "https://notelib.app/public/library/accounting/journal-entries",
        lastModified: "2026-03-22T08:30:00Z",
        changeFrequency: "monthly",
        priority: 0.7,
      },
      {
        url: "https://notelib.app/public/library/science/cell-structure",
        lastModified: "2026-03-24T08:30:00Z",
        changeFrequency: "monthly",
        priority: 0.7,
      },
    ]));
    expect(entries).toEqual(expect.arrayContaining([
      expect.objectContaining({
        url: expect.stringMatching(/^https:\/\/notelib\.app\/learn\//),
        changeFrequency: "monthly",
        priority: 0.8,
      }),
    ]));

    // Accounting has only 1 note — below SUBJECT_PAGE_INDEX_THRESHOLD, so its subject-collection page is
    // excluded from the sitemap even though its individual note page still appears above.
    expect(entries.some((entry) => entry.url === "https://notelib.app/public/library/accounting")).toBe(false);
  });
});
