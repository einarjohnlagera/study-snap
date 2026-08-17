import { render, waitFor } from "@testing-library/react";
import { StrictMode } from "react";
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

  it("adopts once under StrictMode double-invocation", async () => {
    // The one-shot guarantee lives entirely in clearing the cookie BEFORE the await: a synchronous
    // cookie write means the second invocation re-reads null and returns early. The existing
    // remount test cannot see this — it re-renders only after waitFor has resolved the request, so
    // it exercises sequential remount, never concurrent double-invoke.
    //
    // Verified by mutation: moving either clear after the await produces two adoptions, and before
    // this test existed that mutation left the whole suite green.
    (adoptStudyPlan as jest.Mock).mockResolvedValue({ collectionId: "collection-1", skippedCount: 0 });
    setDiscoveryIntentCookie({ planId: "plan-1", planType: "study-plan", returnPath: "/explore" });

    render(
      <StrictMode>
        <DiscoveryIntentConsumer />
      </StrictMode>,
    );

    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/collections/collection-1"));
    expect(adoptStudyPlan).toHaveBeenCalledTimes(1);
  });

  it("keeps an exam intent when the adoption fails", async () => {
    // Clearing exam intent up front over-applied the collision rule: if the plan is gone there is
    // no competing action to suppress, so discarding it costs the visitor a prompt they earned.
    document.cookie = "notelib-exam-intent=ale; path=/; SameSite=Strict";
    (adoptStudyPlan as jest.Mock).mockRejectedValue(new Error("gone"));
    setDiscoveryIntentCookie({ planId: "plan-1", planType: "study-plan", returnPath: "/explore" });

    render(<DiscoveryIntentConsumer />);

    await waitFor(() => expect(replaceMock).toHaveBeenCalled());
    expect(document.cookie).toContain("notelib-exam-intent=ale");
    expect(getDiscoveryIntentCookie()).toBeNull();
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
