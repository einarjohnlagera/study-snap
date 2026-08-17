import { render, waitFor } from "@testing-library/react";
import { DiscoveryIntentConsumer } from "./discovery-intent-consumer";
import { adoptGoal, adoptStudyPlan } from "@/lib/api";
import {
  clearDiscoveryIntentCookie,
  getDiscoveryIntentCookie,
  setDiscoveryIntentCookie,
} from "@/lib/discovery-intent";

const replaceMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock }),
}));

jest.mock("@/lib/api", () => ({
  adoptGoal: jest.fn(),
  adoptStudyPlan: jest.fn(),
}));

describe("DiscoveryIntentConsumer", () => {
  beforeEach(() => {
    replaceMock.mockReset();
    (adoptGoal as jest.Mock).mockReset();
    (adoptStudyPlan as jest.Mock).mockReset();
    clearDiscoveryIntentCookie();
    document.cookie = "notelib-exam-intent=; path=/; max-age=0; SameSite=Strict";
  });

  it("resumes a saved adoption once, clears it, and does not replay on a second pass", async () => {
    setDiscoveryIntentCookie({ planId: "source-plan-1", planType: "study-plan", returnPath: "/explore" });
    (adoptStudyPlan as jest.Mock).mockResolvedValue({
      collectionId: "personal-plan-1",
      copiedCount: 3,
      skippedCount: 0,
      alreadyAdopted: false,
    });

    const firstPass = render(<DiscoveryIntentConsumer />);

    await waitFor(() => expect(adoptStudyPlan).toHaveBeenCalledWith("source-plan-1"));
    expect(replaceMock).toHaveBeenCalledWith("/collections/personal-plan-1");
    expect(getDiscoveryIntentCookie()).toBeNull();

    firstPass.unmount();
    render(<DiscoveryIntentConsumer />);
    await Promise.resolve();
    expect(adoptStudyPlan).toHaveBeenCalledTimes(1);
  });

  it("gives discovery adoption priority over an exam intent", async () => {
    document.cookie = "notelib-exam-intent=ale; path=/; max-age=1800; SameSite=Strict";
    setDiscoveryIntentCookie({ planId: "source-goal-1", planType: "goal", returnPath: "/explore" });
    (adoptGoal as jest.Mock).mockResolvedValue({
      goalCollectionId: "personal-goal-1",
      adoptedSubjectCount: 2,
      skippedSubjectCount: 0,
      totalNotesCopied: 4,
      totalNotesSkipped: 0,
      alreadyAdopted: false,
    });

    render(<DiscoveryIntentConsumer />);

    await waitFor(() => expect(adoptGoal).toHaveBeenCalledWith("source-goal-1"));
    expect(document.cookie).not.toContain("notelib-exam-intent=ale");
    expect(replaceMock).toHaveBeenCalledWith("/collections/personal-goal-1");
  });

  it("clears an unavailable plan and returns to a normal Explore notice state", async () => {
    setDiscoveryIntentCookie({
      planId: "deleted-plan",
      planType: "study-plan",
      returnPath: "/explore?source=dashboard",
    });
    (adoptStudyPlan as jest.Mock).mockRejectedValue(new Error("Not found"));

    render(<DiscoveryIntentConsumer />);

    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith(
      "/explore?source=dashboard&discoveryNotice=adopt-unavailable",
    ));
    expect(getDiscoveryIntentCookie()).toBeNull();
  });
});
