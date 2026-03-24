import { requireAuthenticatedOnboardedUser, requireVerifiedOnboardedUser } from "./route-guards";
import { getAuthUser } from "./auth";

jest.mock("./auth", () => ({
  buildLoginPath: jest.fn(() => "/login"),
  getAuthUser: jest.fn(),
  getCurrentPathWithQuery: jest.fn(() => "/dashboard"),
  needsOnboarding: jest.fn((authUser) => Boolean(authUser?.emailVerifiedAt) && authUser?.onboardingCompletedAt === null),
}));

describe("route guards", () => {
  const router = { replace: jest.fn() };

  beforeEach(() => {
    router.replace.mockReset();
    (getAuthUser as jest.Mock).mockReset();
  });

  it("redirects verified users without onboarding to onboarding", () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      emailVerifiedAt: "2026-03-24T00:00:00Z",
      onboardingCompletedAt: null,
    });

    expect(requireAuthenticatedOnboardedUser(router)).toBe(false);
    expect(router.replace).toHaveBeenCalledWith("/onboarding");
  });

  it("allows verified users with completed onboarding to proceed", () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      emailVerifiedAt: "2026-03-24T00:00:00Z",
      onboardingCompletedAt: "2026-03-24T00:05:00Z",
    });

    expect(requireVerifiedOnboardedUser(router)).toBe(true);
    expect(router.replace).not.toHaveBeenCalled();
  });
});
