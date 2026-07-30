import { fireEvent, render, screen } from "@testing-library/react";
import SubjectLandingPage, { generateMetadata, generateStaticParams } from "./page";
import { getServerPublicNotesBySubjectSlug, getServerPublicSubjects } from "@/lib/server-public-notes";
import { PUBLIC_LIBRARY_RETURN_URL_STORAGE_KEY } from "@/lib/public-library-url";
import type { NoteListItemResponse } from "@/lib/api";

jest.mock("@/lib/server-public-notes", () => ({
  ...jest.requireActual("@/lib/server-public-notes"),
  getServerPublicNotesBySubjectSlug: jest.fn(),
  getServerPublicSubjects: jest.fn(),
}));

type TestNote = NoteListItemResponse & { slug: string };

function buildNote(overrides: Partial<TestNote> & Pick<TestNote, "id" | "title" | "slug">): TestNote {
  return {
    ownerUserId: "user-1",
    courseProgram: "Biology",
    targetProfileType: "STUDENT",
    subject: "Biology",
    tags: ["cells"],
    contentPreview: "A concise public note preview about biology.",
    summaryPreview: "A study-ready summary preview for biology learners.",
    visibility: "PUBLIC",
    studyPackId: "study-pack-1",
    studyPackStatus: "STUDY_PACK_READY",
    quizCount: 3,
    keyConceptCount: 3,
    copyCount: 0,
    likeCount: 0,
    shareCount: 0,
    viewCount: 0,
    authorDisplayName: "Study Buddy",
    authorUsername: "studybuddy",
    isOfficialAuthor: false,
    isCurrentUser: false,
    createdAt: "2026-03-20T00:00:00Z",
    updatedAt: "2026-03-21T00:00:00Z",
    copiedFromNoteId: null,
    copiedFromPublic: false,
    likedByCurrentUser: false,
    ...overrides,
  };
}

const subjectNotes = [
  buildNote({
    id: "featured-1",
    title: "Cell Structure",
    slug: "cell-structure",
    copyCount: 12,
    likeCount: 4,
    viewCount: 120,
    createdAt: "2026-03-25T00:00:00Z",
  }),
  buildNote({
    id: "popular-1",
    title: "Genetics Basics",
    slug: "genetics-basics",
    summaryPreview: "",
    copyCount: 9,
    viewCount: 90,
    createdAt: "2026-03-24T00:00:00Z",
  }),
  buildNote({
    id: "recent-1",
    title: "Photosynthesis Notes",
    slug: "photosynthesis-notes",
    summaryPreview: "",
    copyCount: 0,
    viewCount: 1,
    createdAt: "2026-03-26T00:00:00Z",
  }),
];

describe("SubjectLandingPage", () => {
  beforeEach(() => {
    (getServerPublicNotesBySubjectSlug as jest.Mock).mockReset();
    (getServerPublicSubjects as jest.Mock).mockReset();
    window.sessionStorage.clear();
  });

  it("generates static params from public subject slugs", async () => {
    (getServerPublicSubjects as jest.Mock).mockResolvedValue([
      { slug: "biology", label: "Biology", lastModified: "2026-03-21T00:00:00Z" },
      { slug: "chemistry", label: "Chemistry", lastModified: null },
    ]);

    await expect(generateStaticParams()).resolves.toEqual([
      { subject: "biology" },
      { subject: "chemistry" },
    ]);
    expect(getServerPublicSubjects).toHaveBeenCalledTimes(1);
  });

  it("generates per-subject metadata from the subject label", async () => {
    (getServerPublicNotesBySubjectSlug as jest.Mock).mockResolvedValue(subjectNotes);

    const metadata = await generateMetadata({
      params: Promise.resolve({ subject: "biology" }),
    });

    expect(metadata.title).toBe("Free Biology Reviewer Notes & Practice Quizzes | NoteLib");
    expect(metadata.description).toContain("Biology");
    expect(metadata.alternates?.canonical).toBe("https://notelib.app/public/library/biology");
  });

  it("marks a thin subject page noindex when it has fewer notes than the depth threshold", async () => {
    (getServerPublicNotesBySubjectSlug as jest.Mock).mockResolvedValue(subjectNotes); // 3 notes < threshold

    const metadata = await generateMetadata({
      params: Promise.resolve({ subject: "biology" }),
    });

    expect(metadata.robots).toEqual({ index: false, follow: true });
  });

  it("does not noindex a subject page once it meets the depth threshold", async () => {
    (getServerPublicNotesBySubjectSlug as jest.Mock).mockResolvedValue([
      ...subjectNotes,
      buildNote({ id: "extra-1", title: "Mitosis", slug: "mitosis" }),
      buildNote({ id: "extra-2", title: "Meiosis", slug: "meiosis" }),
      buildNote({ id: "extra-3", title: "DNA Replication", slug: "dna-replication" }),
    ]); // 6 notes total, meets threshold

    const metadata = await generateMetadata({
      params: Promise.resolve({ subject: "biology" }),
    });

    expect(metadata.robots).toBeUndefined();
  });

  it("renders ranked subject sections and canonical note links", async () => {
    (getServerPublicNotesBySubjectSlug as jest.Mock).mockResolvedValue(subjectNotes);

    render(await SubjectLandingPage({ params: Promise.resolve({ subject: "biology" }) }));

    expect(screen.getByRole("link", { name: "← Public Library" })).toHaveAttribute("href", "/public/library");
    expect(screen.getByRole("heading", { name: "Biology" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Featured Notes" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Most Popular" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Recently Added" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Cell Structure/ })).toHaveAttribute(
      "href",
      "/public/library/biology/cell-structure",
    );
    expect(screen.getByRole("link", { name: /Genetics Basics/ })).toHaveAttribute(
      "href",
      "/public/library/biology/genetics-basics",
    );
  });

  it("saves this subject-landing page as the Public Library return URL when a note card is clicked", async () => {
    (getServerPublicNotesBySubjectSlug as jest.Mock).mockResolvedValue(subjectNotes);

    render(await SubjectLandingPage({ params: Promise.resolve({ subject: "biology" }) }));

    expect(window.sessionStorage.getItem(PUBLIC_LIBRARY_RETURN_URL_STORAGE_KEY)).toBeNull();
    fireEvent.click(screen.getByRole("link", { name: /Cell Structure/ }));
    expect(window.sessionStorage.getItem(PUBLIC_LIBRARY_RETURN_URL_STORAGE_KEY)).toBe("/public/library/biology");
  });

  it("does not repeat notes across sections", async () => {
    (getServerPublicNotesBySubjectSlug as jest.Mock).mockResolvedValue(subjectNotes);

    render(await SubjectLandingPage({ params: Promise.resolve({ subject: "biology" }) }));

    expect(screen.getAllByText("Cell Structure")).toHaveLength(1);
    expect(screen.getAllByText("Genetics Basics")).toHaveLength(1);
    expect(screen.getAllByText("Photosynthesis Notes")).toHaveLength(1);
  });

  it("shows one excerpt per card, note preview first, summary as a labeled fallback", async () => {
    (getServerPublicNotesBySubjectSlug as jest.Mock).mockResolvedValue([
      buildNote({
        id: "featured-1",
        title: "Cell Structure",
        slug: "cell-structure",
        contentPreview: "A concise public note preview about biology.",
        summaryPreview: "A study-ready summary preview for biology learners.",
        createdAt: "2026-03-25T00:00:00Z",
      }),
      buildNote({
        id: "popular-1",
        title: "Genetics Basics",
        slug: "genetics-basics",
        contentPreview: "Too short",
        summaryPreview: "A study-ready summary preview for genetics learners.",
        copyCount: 9,
        createdAt: "2026-03-24T00:00:00Z",
      }),
    ]);

    render(await SubjectLandingPage({ params: Promise.resolve({ subject: "biology" }) }));

    // Note preview wins when it clears the minimum length — the summary never shows alongside it.
    expect(screen.getByText("A concise public note preview about biology.")).toBeInTheDocument();
    expect(screen.queryByText("A study-ready summary preview for biology learners.")).not.toBeInTheDocument();

    // A too-short note body falls back to the labeled summary excerpt instead.
    expect(screen.getByText("A study-ready summary preview for genetics learners.")).toBeInTheDocument();
    expect(screen.getByText("Summary")).toBeInTheDocument();
  });

  it("renders an empty state when no notes match the subject slug", async () => {
    (getServerPublicNotesBySubjectSlug as jest.Mock).mockResolvedValue([]);

    render(await SubjectLandingPage({ params: Promise.resolve({ subject: "biology" }) }));

    expect(screen.getByText("No notes yet for this subject.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "← Browse all subjects" })).toHaveAttribute("href", "/public/library");
  });
});
