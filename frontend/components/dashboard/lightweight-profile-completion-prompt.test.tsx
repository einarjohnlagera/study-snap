import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import type { ComponentProps } from "react";
import { LightweightProfileCompletionPrompt } from "./lightweight-profile-completion-prompt";
import {
  completeOnboarding,
  getCourseProgramCatalog,
  trackAnalyticsEvent,
  updateLearningProfileContext,
} from "@/lib/api";

jest.mock("@/lib/api", () => ({
  completeOnboarding: jest.fn(),
  getCourseProgramCatalog: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
  updateLearningProfileContext: jest.fn(),
}));

const completedProfile = {
  id: "user-1",
  displayName: "Note",
  profileType: "STUDENT",
  emailVerifiedAt: "2026-07-12T00:00:00Z",
  onboardingCompletedAt: "2026-07-12T00:05:00Z",
  productOnboardingCompletedAt: null,
};

function renderPrompt(overrides: Partial<ComponentProps<typeof LightweightProfileCompletionPrompt>> = {}) {
  return render(
    <LightweightProfileCompletionPrompt
      initialProfileType="STUDENT"
      initialLearnerLevel="COLLEGE"
      initialCourseProgram="Nursing"
      initialExamDate={null}
      onDismiss={jest.fn()}
      onComplete={jest.fn()}
      {...overrides}
    />,
  );
}

describe("LightweightProfileCompletionPrompt", () => {
  beforeEach(() => {
    (updateLearningProfileContext as jest.Mock).mockReset();
    (completeOnboarding as jest.Mock).mockReset();
    (getCourseProgramCatalog as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (updateLearningProfileContext as jest.Mock).mockResolvedValue(completedProfile);
    (completeOnboarding as jest.Mock).mockResolvedValue(completedProfile);
    (getCourseProgramCatalog as jest.Mock).mockResolvedValue([
      { id: "program-pharmacy", name: "Pharmacy", programFamilyId: null, programFamilyName: null },
      { id: "program-nursing", name: "Nursing", programFamilyId: null, programFamilyName: null },
    ]);
    (trackAnalyticsEvent as jest.Mock).mockResolvedValue(undefined);
  });

  it.each([
    ["STUDENT", null],
    ["BOARD_EXAM", null],
    ["BOARD_EXAM", "2026-10-15"],
    ["TEACHER", null],
    ["PROFESSIONAL", null],
  ] as const)("saves learning context before completing %s profile setup", async (profileType, examDate) => {
    const onComplete = jest.fn();
    renderPrompt({
      initialProfileType: profileType,
      initialExamDate: examDate,
      onComplete,
    });

    fireEvent.click(screen.getByRole("button", { name: "Save Study Profile" }));

    await waitFor(() => {
      expect(updateLearningProfileContext).toHaveBeenCalledWith("COLLEGE", "Nursing");
      expect(completeOnboarding).toHaveBeenCalledWith({ profileType, examDate });
      expect(onComplete).toHaveBeenCalledWith(completedProfile);
    });
    expect((updateLearningProfileContext as jest.Mock).mock.invocationCallOrder[0]).toBeLessThan(
      (completeOnboarding as jest.Mock).mock.invocationCallOrder[0],
    );
  });

  it("retries only profile completion after learning profile save succeeds", async () => {
    (completeOnboarding as jest.Mock)
      .mockRejectedValueOnce(new Error("Completion unavailable"))
      .mockResolvedValueOnce(completedProfile);

    renderPrompt();

    fireEvent.click(screen.getByRole("button", { name: "Save Study Profile" }));

    expect(await screen.findByText("Your study preferences are saved. Please retry confirming your profile type.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Confirm Profile Type" })).toBeInTheDocument();
    expect(updateLearningProfileContext).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole("button", { name: "Confirm Profile Type" }));

    await waitFor(() => {
      expect(completeOnboarding).toHaveBeenCalledTimes(2);
      expect(updateLearningProfileContext).toHaveBeenCalledTimes(1);
    });
  });

  it("lists catalog names first, retains an off-catalog value, and allows custom text", async () => {
    renderPrompt({ initialCourseProgram: "Professional / Board Exam Review" });

    await waitFor(() => expect(getCourseProgramCatalog).toHaveBeenCalled());
    fireEvent.click(screen.getByRole("button", { name: "Toggle course program suggestions" }));
    const listbox = screen.getByRole("listbox");
    await within(listbox).findByRole("option", { name: "Pharmacy" });
    const options = within(listbox).getAllByRole("option").map((option) => option.textContent);
    expect(options.slice(0, 2)).toEqual(["Pharmacy", "Nursing"]);
    expect(options).toContain("Professional / Board Exam Review");

    const input = screen.getByLabelText("Course / Program");
    expect(input).toHaveValue("Professional / Board Exam Review");
    fireEvent.change(input, { target: { value: "Marine Biology" } });
    expect(input).toHaveValue("Marine Biology");
  });

  it("falls back without blocking and omits analytics when the catalog fetch fails", async () => {
    (getCourseProgramCatalog as jest.Mock).mockRejectedValue(new Error("offline"));
    renderPrompt();

    fireEvent.click(screen.getByRole("button", { name: "Toggle course program suggestions" }));
    expect(screen.getByRole("option", { name: "Software Engineering" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Save Study Profile" }));

    await waitFor(() => expect(completeOnboarding).toHaveBeenCalled());
    expect(trackAnalyticsEvent).not.toHaveBeenCalled();
  });

  it("does not track a save that left the Course / Program unchanged", async () => {
    // Saves here also persist learner level and profile type, so a save is not a selection. Counting
    // unchanged values would fill the metric with re-saves of pre-existing (mostly off-catalog) strings.
    renderPrompt();

    fireEvent.click(screen.getByRole("button", { name: "Toggle course program suggestions" }));
    await within(screen.getByRole("listbox")).findByRole("option", { name: "Pharmacy" });
    fireEvent.click(screen.getByRole("button", { name: "Toggle course program suggestions" }));
    fireEvent.click(screen.getByRole("button", { name: "Save Study Profile" }));

    await waitFor(() => expect(completeOnboarding).toHaveBeenCalled());
    expect(trackAnalyticsEvent).not.toHaveBeenCalledWith(
      expect.objectContaining({ eventType: "COURSE_PROGRAM_VALUE_SELECTED" }),
    );
  });

  it("tracks one committed save with only the dashboard surface and catalog match", async () => {
    renderPrompt();

    fireEvent.click(screen.getByRole("button", { name: "Toggle course program suggestions" }));
    const option = await within(screen.getByRole("listbox")).findByRole("option", { name: "Pharmacy" });
    fireEvent.click(option);
    fireEvent.click(screen.getByRole("button", { name: "Save Study Profile" }));

    await waitFor(() => {
      expect(trackAnalyticsEvent).toHaveBeenCalledTimes(1);
      expect(trackAnalyticsEvent).toHaveBeenCalledWith({
        eventType: "COURSE_PROGRAM_VALUE_SELECTED",
        metadata: { surface: "dashboard-prompt", matchedCatalog: true },
      });
      expect(completeOnboarding).toHaveBeenCalled();
    });
  });

  it("still completes the save when analytics rejects", async () => {
    (trackAnalyticsEvent as jest.Mock).mockRejectedValue(new Error("analytics unavailable"));
    renderPrompt();

    fireEvent.click(screen.getByRole("button", { name: "Toggle course program suggestions" }));
    await within(screen.getByRole("listbox")).findByRole("option", { name: "Pharmacy" });
    fireEvent.click(screen.getByRole("button", { name: "Save Study Profile" }));

    await waitFor(() => {
      expect(updateLearningProfileContext).toHaveBeenCalledTimes(1);
      expect(completeOnboarding).toHaveBeenCalledTimes(1);
    });
  });
});
