import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PublicStudyPlanCard } from "./public-study-plan-card";
import { adoptGoal, adoptStudyPlan, getPublicStudyPlanDetail, type NoteCollectionSummary } from "@/lib/api";
import { setJustAdoptedNotice } from "@/lib/just-adopted-notice";

const pushMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock }),
}));

jest.mock("@/lib/api", () => ({
  adoptGoal: jest.fn(),
  adoptStudyPlan: jest.fn(),
  getPublicStudyPlanDetail: jest.fn(),
}));

jest.mock("@/lib/just-adopted-notice", () => ({
  setJustAdoptedNotice: jest.fn(),
}));

const leafPlan: NoteCollectionSummary = {
  id: "source-plan-1",
  title: "LET Reviewer Plan",
  description: "A curated LET review sequence.",
  visibility: "PUBLIC",
  courseProgram: "LET",
  sourcePlanId: null,
  parentCollectionId: null,
  itemCount: 3,
  readyCount: 1,
  childCount: 0,
  notesPracticed: 0,
  createdAt: "2026-06-01T00:00:00Z",
  updatedAt: "2026-06-02T00:00:00Z",
};

describe("PublicStudyPlanCard", () => {
  beforeEach(() => {
    pushMock.mockReset();
    globalThis.sessionStorage.clear();
    (adoptGoal as jest.Mock).mockReset();
    (adoptStudyPlan as jest.Mock).mockReset();
    (getPublicStudyPlanDetail as jest.Mock).mockReset();
    (setJustAdoptedNotice as jest.Mock).mockReset();
  });

  it("records the just-adopted notice for recursive Goal adoption only", async () => {
    (adoptGoal as jest.Mock).mockResolvedValue({
      goalCollectionId: "personal-goal-1",
      adoptedSubjectCount: 2,
      skippedSubjectCount: 1,
      totalNotesCopied: 8,
      totalNotesSkipped: 0,
      alreadyAdopted: false,
    });

    render(<PublicStudyPlanCard plan={{ ...leafPlan, id: "source-goal-1", childCount: 2 }} adoptedCollection={null} />);

    fireEvent.click(screen.getByRole("button", { name: "Start this Goal" }));

    await waitFor(() => {
      expect(adoptGoal).toHaveBeenCalledWith("source-goal-1");
      expect(setJustAdoptedNotice).toHaveBeenCalledWith("personal-goal-1");
      expect(pushMock).toHaveBeenCalledWith("/collections/personal-goal-1");
    });
  });

  it("does not record the just-adopted notice for leaf plan adoption", async () => {
    (adoptStudyPlan as jest.Mock).mockResolvedValue({
      collectionId: "personal-plan-1",
      copiedCount: 3,
      skippedCount: 0,
      alreadyAdopted: false,
    });

    render(<PublicStudyPlanCard plan={leafPlan} adoptedCollection={null} />);

    fireEvent.click(screen.getByRole("button", { name: "Start this Collection" }));

    await waitFor(() => {
      expect(adoptStudyPlan).toHaveBeenCalledWith("source-plan-1");
      expect(setJustAdoptedNotice).not.toHaveBeenCalled();
      expect(pushMock).toHaveBeenCalledWith("/collections/personal-plan-1");
    });
  });

  it("resolves the CTA and subject-count labels through profile-aware terminology, not a hardcoded generic word", () => {
    render(
      <PublicStudyPlanCard
        plan={{ ...leafPlan, id: "source-goal-1", childCount: 3 }}
        adoptedCollection={null}
        profileType="STUDENT"
      />,
    );

    expect(screen.getByText(/3 Subject Plans/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start this Goal" })).toBeInTheDocument();
    expect(screen.queryByText(/3 Subject plans\b/)).not.toBeInTheDocument();
  });

  it("renders practice readiness as metadata and falls back when an older response omits it", () => {
    const { rerender } = render(<PublicStudyPlanCard plan={leafPlan} adoptedCollection={null} />);

    expect(screen.getByText("1 of 3 notes practice-ready")).toHaveClass("text-foreground/70");
    expect(screen.queryByText("1 of 3 notes practice-ready")).not.toHaveClass("rounded-full");

    rerender(<PublicStudyPlanCard plan={{ ...leafPlan, readyCount: undefined }} adoptedCollection={null} />);

    expect(screen.queryByText(/notes practice-ready/)).not.toBeInTheDocument();
    expect(screen.getByText("3 notes curated for this track.")).toBeInTheDocument();
  });

  it("loads a public preview with the plan's real notes, metadata, and readiness", async () => {
    (getPublicStudyPlanDetail as jest.Mock).mockResolvedValue({
      ...leafPlan,
      estimatedStudyHours: 4,
      readyCount: 1,
      progress: { totalNotes: 2, notesWithStudyPack: 1, notesPracticed: 0 },
      items: [
        { noteId: "note-1", title: "Educational Psychology", subject: "Professional Education", label: "Foundations", courseProgram: "LET" },
        { noteId: "note-2", title: "Assessment Basics", subject: "Professional Education", label: null, courseProgram: "LET" },
      ],
    });

    render(<PublicStudyPlanCard plan={leafPlan} adoptedCollection={null} />);

    fireEvent.click(screen.getByRole("button", { name: "Preview this plan" }));

    expect(await screen.findByText("Educational Psychology")).toBeInTheDocument();
    expect(screen.getByText("Assessment Basics")).toBeInTheDocument();
    expect(screen.getByText("Professional Education · Section: Foundations")).toBeInTheDocument();
    expect(screen.getByText("Estimated study time: 4 hours")).toBeInTheDocument();
    expect(screen.getByText("1 of 2 notes practice-ready")).toBeInTheDocument();
    expect(getPublicStudyPlanDetail).toHaveBeenCalledWith("source-plan-1");
    expect(screen.getByRole("button", { name: "Start this Collection" })).toBeEnabled();
  });

  it("keeps adoption available when the public preview cannot load", async () => {
    (getPublicStudyPlanDetail as jest.Mock).mockRejectedValue(new Error("Not found"));

    render(<PublicStudyPlanCard plan={leafPlan} adoptedCollection={null} />);

    fireEvent.click(screen.getByRole("button", { name: "Preview this plan" }));

    expect(await screen.findByText("Couldn’t load this plan preview. Please try again.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start this Collection" })).toBeEnabled();
  });

  it("states when the public preview has no available notes", async () => {
    (getPublicStudyPlanDetail as jest.Mock).mockResolvedValue({
      ...leafPlan,
      estimatedStudyHours: null,
      readyCount: 0,
      progress: { totalNotes: 0, notesWithStudyPack: 0, notesPracticed: 0 },
      items: [],
    });

    render(<PublicStudyPlanCard plan={leafPlan} adoptedCollection={null} />);

    fireEvent.click(screen.getByRole("button", { name: "Preview this plan" }));

    expect(await screen.findByText("This plan does not have any available notes yet.")).toBeInTheDocument();
    expect(screen.getByText("0 of 0 notes practice-ready")).toBeInTheDocument();
  });
});
