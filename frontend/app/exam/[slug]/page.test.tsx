import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import ExamHubPage, { generateMetadata, generateStaticParams } from "./page";
import { EXAM_HUBS, EXAM_HUB_SLUGS } from "@/lib/exam-hub-config";
import { getServerPublicNotesByCoursePrograms } from "@/lib/server-public-notes";
import { getAuthUser } from "@/lib/auth";
import { trackAnalyticsEvent, type NoteListItemResponse } from "@/lib/api";

const notFoundMock = jest.fn(() => {
  throw new Error("NEXT_NOT_FOUND");
});

jest.mock("next/navigation", () => ({
  notFound: () => notFoundMock(),
}));

jest.mock("@/lib/server-public-notes", () => ({
  getServerPublicNotesByCoursePrograms: jest.fn(),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  trackAnalyticsEvent: jest.fn(),
}));

type TestNote = NoteListItemResponse & { slug: string };

function buildNote(overrides: Partial<TestNote> & Pick<TestNote, "id" | "title" | "slug">): TestNote {
  return {
    ownerUserId: "user-1",
    courseProgram: "Architecture",
    targetProfileType: "BOARD_TAKER",
    subject: "Architecture",
    tags: ["board exam"],
    contentPreview: "A concise public note preview for board exam reviewers.",
    summaryPreview: "A study-ready summary preview for board exam reviewers.",
    visibility: "PUBLIC",
    studyPackId: "study-pack-1",
    studyPackStatus: "STUDY_PACK_READY",
    quizCount: 5,
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

const sectionNotes = [
  buildNote({ id: "featured-1", title: "ALE Structures", slug: "ale-structures", copyCount: 12, viewCount: 120 }),
  buildNote({ id: "popular-1", title: "Building Utilities", slug: "building-utilities", summaryPreview: "", copyCount: 9, viewCount: 90 }),
  buildNote({ id: "recent-1", title: "Planning Notes", slug: "planning-notes", summaryPreview: "", createdAt: "2026-03-28T00:00:00Z" }),
];

describe("ExamHubPage", () => {
  beforeEach(() => {
    notFoundMock.mockClear();
    document.cookie = "notelib-exam-intent=; path=/; max-age=0; SameSite=Strict";
    (getServerPublicNotesByCoursePrograms as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReturnValue(null);
    (trackAnalyticsEvent as jest.Mock).mockReset();
  });

  it("defines exactly the wave-1 exam hubs and course program aliases", () => {
    expect(EXAM_HUB_SLUGS).toEqual(["ale", "pnle", "let"]);
    expect(EXAM_HUBS.ale.coursePrograms).toEqual(["Architecture"]);
    expect(EXAM_HUBS.pnle.coursePrograms).toEqual(["Nursing", "Medical – Surgical Nursing"]);
    expect(EXAM_HUBS.let.coursePrograms).toEqual(["Education"]);
    expect(generateStaticParams()).toEqual([{ slug: "ale" }, { slug: "pnle" }, { slug: "let" }]);
  });

  it.each([
    ["ale", "Architect Licensure Examination (ALE)", "Architecture"],
    ["pnle", "Philippine Nurse Licensure Examination (PNLE)", "Nursing"],
    ["let", "Licensure Examination for Teachers (LET)", "Education"],
  ])("renders /exam/%s with the configured exam copy and filters", async (slug, fullName, firstCourseProgram) => {
    (getServerPublicNotesByCoursePrograms as jest.Mock).mockResolvedValue(sectionNotes);

    render(await ExamHubPage({ params: Promise.resolve({ slug }) }));

    expect(screen.getByRole("heading", { name: fullName })).toBeInTheDocument();
    expect(screen.getByText(EXAM_HUBS[slug as keyof typeof EXAM_HUBS].description, { exact: false })).toBeInTheDocument();
    expect(screen.getAllByText(firstCourseProgram).length).toBeGreaterThanOrEqual(1);
    expect(getServerPublicNotesByCoursePrograms).toHaveBeenCalledWith(EXAM_HUBS[slug as keyof typeof EXAM_HUBS].coursePrograms);
  });

  it.each([
    ["ale", "Architect Licensure Examination (ALE) Notes and Practice Quizzes | NoteLib"],
    ["pnle", "Philippine Nurse Licensure Examination (PNLE) Notes and Practice Quizzes | NoteLib"],
    ["let", "Licensure Examination for Teachers (LET) Notes and Practice Quizzes | NoteLib"],
  ])("generates metadata for /exam/%s", async (slug, title) => {
    const metadata = await generateMetadata({ params: Promise.resolve({ slug }) });

    expect(metadata.title).toBe(title);
    expect(metadata.description).toContain(EXAM_HUBS[slug as keyof typeof EXAM_HUBS].description);
    expect(metadata.alternates?.canonical).toBe(`https://notelib.app/exam/${slug}`);
  });

  it("returns notFound for unknown slugs", async () => {
    await expect(ExamHubPage({ params: Promise.resolve({ slug: "unknown" }) })).rejects.toThrow("NEXT_NOT_FOUND");
    expect(getServerPublicNotesByCoursePrograms).not.toHaveBeenCalled();
  });

  it("renders the empty state when no public notes match", async () => {
    (getServerPublicNotesByCoursePrograms as jest.Mock).mockResolvedValue([]);

    render(await ExamHubPage({ params: Promise.resolve({ slug: "ale" }) }));

    expect(screen.getByText("No Architect Licensure Examination (ALE) notes have been shared yet. Check back soon.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Browse the Public Library" })).toHaveAttribute("href", "/public/library");
  });

  it("suppresses empty discovery section headers for small note sets", async () => {
    (getServerPublicNotesByCoursePrograms as jest.Mock).mockResolvedValue(sectionNotes.slice(0, 1));

    render(await ExamHubPage({ params: Promise.resolve({ slug: "ale" }) }));

    expect(screen.getByRole("heading", { name: "Featured Notes" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Most Popular" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Recently Added" })).not.toBeInTheDocument();
  });

  it("tracks a page view once with the exam slug", async () => {
    (getServerPublicNotesByCoursePrograms as jest.Mock).mockResolvedValue(sectionNotes);

    render(await ExamHubPage({ params: Promise.resolve({ slug: "ale" }) }));

    await waitFor(() => {
      expect(trackAnalyticsEvent).toHaveBeenCalledWith({
        eventType: "EXAM_HUB_VIEWED",
        entityId: null,
        metadata: { slug: "ale" },
      });
    });
  });

  it("renders the anonymous signup CTA, persists exam intent, and tracks CTA clicks", async () => {
    (getServerPublicNotesByCoursePrograms as jest.Mock).mockResolvedValue(sectionNotes);

    render(await ExamHubPage({ params: Promise.resolve({ slug: "ale" }) }));

    const cta = screen.getByRole("link", { name: "Start preparing for the ALE" });
    expect(cta).toHaveAttribute("href", "/auth?mode=signup&intent=exam&exam=ale");

    cta.addEventListener("click", (event) => event.preventDefault());
    fireEvent.click(cta);

    expect(document.cookie).toContain("notelib-exam-intent=ale");
    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "EXAM_HUB_CTA_CLICKED",
      metadata: { slug: "ale", destination: "signup" },
    });
  });

  it("renders an authenticated public-library CTA and tracks clicks", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (getServerPublicNotesByCoursePrograms as jest.Mock).mockResolvedValue(sectionNotes);

    render(await ExamHubPage({ params: Promise.resolve({ slug: "ale" }) }));

    const cta = await screen.findByRole("link", { name: "Browse Architecture Notes" });
    expect(cta).toHaveAttribute("href", "/public/library?courseProgram=architecture");

    cta.addEventListener("click", (event) => event.preventDefault());
    fireEvent.click(cta);

    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "EXAM_HUB_CTA_CLICKED",
      entityId: null,
      metadata: { slug: "ale", destination: "public_library" },
    });
  });
});
