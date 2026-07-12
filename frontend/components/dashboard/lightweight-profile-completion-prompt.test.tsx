import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ComponentProps } from "react";
import { LightweightProfileCompletionPrompt } from "./lightweight-profile-completion-prompt";
import { completeOnboarding, updateLearningProfileContext } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  completeOnboarding: jest.fn(),
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
    (updateLearningProfileContext as jest.Mock).mockResolvedValue(completedProfile);
    (completeOnboarding as jest.Mock).mockResolvedValue(completedProfile);
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
});
