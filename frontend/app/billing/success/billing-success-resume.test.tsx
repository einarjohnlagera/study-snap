import { render, waitFor } from "@testing-library/react";
import { BillingSuccessResume } from "./billing-success-resume";
import { getMyPlan } from "@/lib/api";
import { getAuthUser, patchAuthUser } from "@/lib/auth";
import {
  loadPendingPaywallUpgradeContext,
  savePendingPaywallUpgradeContext,
} from "@/lib/paywall-upgrade-context";

const getAuthUserMock = getAuthUser as jest.Mock;
const patchAuthUserMock = patchAuthUser as jest.Mock;
const getMyPlanMock = getMyPlan as jest.Mock;

jest.mock("@/lib/api", () => ({
  getMyPlan: jest.fn(),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
  patchAuthUser: jest.fn(),
}));

describe("BillingSuccessResume", () => {
  const originalLocation = globalThis.location;
  const assignMock = jest.fn();

  beforeEach(() => {
    jest.useFakeTimers();
    window.localStorage.clear();
    getAuthUserMock.mockReset();
    patchAuthUserMock.mockReset();
    getMyPlanMock.mockReset();
    getAuthUserMock.mockReturnValue({
      id: "user-1",
      planType: "FREE",
    });
    Object.defineProperty(globalThis, "location", {
      configurable: true,
      value: {
        ...originalLocation,
        assign: assignMock,
      },
    });
    assignMock.mockReset();
  });

  afterEach(() => {
    jest.runOnlyPendingTimers();
    jest.useRealTimers();
    Object.defineProperty(globalThis, "location", {
      configurable: true,
      value: originalLocation,
    });
  });

  it("redirects back into Study Pack generation after the plan is confirmed", async () => {
    savePendingPaywallUpgradeContext("user-1", {
      type: "GENERATE_STUDY_PACK_LIMIT",
      lastAction: "GENERATE_STUDY_PACK",
      noteId: "note-1",
      returnPath: "/notes/note-1/edit",
      source: "note_editor_study_pack_limit",
      createdAtMs: Date.now(),
    });
    getMyPlanMock.mockResolvedValue({
      plan: "PRO",
      limits: {},
      usage: {},
      remaining: {},
      features: {},
    });

    render(
      <BillingSuccessResume
        fallbackReturnUrl="/notes/note-1/edit"
        shouldPreferDashboard={false}
        selectedPlan="PRO"
      />,
    );

    await waitFor(() => {
      expect(getMyPlanMock).toHaveBeenCalled();
    });

    await waitFor(async () => {
      jest.advanceTimersByTime(900);
      await Promise.resolve();
      expect(assignMock).toHaveBeenCalledWith("/notes/note-1?generate=1");
    });

    expect(patchAuthUserMock).toHaveBeenCalledWith(expect.objectContaining({
      planType: "PRO",
    }));
    expect(loadPendingPaywallUpgradeContext("user-1")).toBeNull();
  });

  it("routes settings-origin upgrades to the dashboard", async () => {
    getMyPlanMock.mockResolvedValue({
      plan: "PLUS",
      limits: {},
      usage: {},
      remaining: {},
      features: {},
    });

    render(
      <BillingSuccessResume
        fallbackReturnUrl="/settings?section=billing"
        shouldPreferDashboard
        selectedPlan="PLUS"
      />,
    );

    await waitFor(async () => {
      jest.advanceTimersByTime(900);
      await Promise.resolve();
      expect(assignMock).toHaveBeenCalledWith("/dashboard");
    });
  });
});
