import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { DashboardCommunityNotesSection } from "./dashboard-community-notes-section";
import { listPublicNotes } from "@/lib/api";

const pushMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({
    push: pushMock,
  }),
}));

jest.mock("@/lib/api", () => ({
  listPublicNotes: jest.fn(),
}));

const COURSE_PROGRAM = "Medical – Surgical Nursing";

const publicNote = {
  id: "public-note-1",
  ownerUserId: null,
  title: "Medical-Surgical Nursing Review",
  courseProgram: COURSE_PROGRAM,
  targetProfileType: "BOARD_TAKER",
  subject: "Nursing",
  tags: ["review"],
  contentPreview: "Nursing board exam fundamentals.",
  summaryPreview: "A quick PNLE review summary.",
  visibility: "PUBLIC",
  studyPackId: "pack-1",
  studyPackStatus: "STUDY_PACK_READY",
  quizCount: 12,
  copyCount: 4,
  likeCount: 2,
  shareCount: 1,
  viewCount: 30,
  authorDisplayName: "Creator",
  authorUsername: "creator",
  isOfficialAuthor: false,
  isCurrentUser: false,
  createdAt: "2026-03-20T00:00:00Z",
  updatedAt: "2026-03-21T00:00:00Z",
  likedByCurrentUser: false,
} as const;

describe("DashboardCommunityNotesSection", () => {
  beforeEach(() => {
    pushMock.mockReset();
    (listPublicNotes as jest.Mock).mockReset();
  });

  it("renders matching public note cards and a filtered Explore link", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue({ items: [publicNote], total: 8 });

    render(<DashboardCommunityNotesSection courseProgram={COURSE_PROGRAM} viewerUserId="user-1" />);

    expect(await screen.findByRole("heading", { name: `Notes for ${COURSE_PROGRAM}` })).toBeInTheDocument();
    expect(screen.getByText("Medical-Surgical Nursing Review")).toBeInTheDocument();
    const href = screen.getByRole("link", { name: "See all in Explore →" }).getAttribute("href");
    expect(href).not.toBeNull();
    const url = new URL(href ?? "", "http://localhost");
    expect(url.pathname).toBe("/explore");
    expect(url.searchParams.get("tab")).toBe("notes");
    expect(url.searchParams.get("source")).toBe("dashboard");
    expect(url.searchParams.get("courseProgram")).toBe(COURSE_PROGRAM);
    expect(listPublicNotes).toHaveBeenCalledWith({ courseProgram: COURSE_PROGRAM, size: 4 });
  });

  it("hides itself when a course program has no matching notes", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue({ items: [], total: 8 });

    render(<DashboardCommunityNotesSection courseProgram="PNLE" viewerUserId="user-1" />);

    await waitFor(() => {
      expect(screen.queryByRole("heading", { name: "Notes for PNLE" })).not.toBeInTheDocument();
    });
  });

  it("hides itself when loading public notes fails", async () => {
    (listPublicNotes as jest.Mock).mockRejectedValue(new Error("Network error"));

    render(<DashboardCommunityNotesSection courseProgram="PNLE" viewerUserId="user-1" />);

    await waitFor(() => {
      expect(screen.queryByRole("heading", { name: "Notes for PNLE" })).not.toBeInTheDocument();
    });
  });

  it("shows a course-program prompt and routes to the learning profile section", () => {
    render(<DashboardCommunityNotesSection courseProgram={null} viewerUserId="user-1" />);

    expect(screen.getByRole("heading", { name: "Notes for your review track" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Set your Course/Program" }));

    expect(screen.getAllByText("Set your Course/Program to see public notes tailored for your review track.")).not.toHaveLength(0);
    fireEvent.click(screen.getByRole("button", { name: "Go to Learner Profile" }));

    expect(pushMock).toHaveBeenCalledWith("/profile#learning-profile");
  });
});
