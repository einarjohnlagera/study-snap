import {
  redirectToLoginWithCurrentDestination,
  requireAdminUser,
  requireAuthenticatedOnboardedUser,
  requireVerifiedOnboardedUser,
} from "./route-guards";
import { buildLoginPath, getAuthUser, isManualLogoutInProgress } from "./auth";

jest.mock("./auth", () => ({
  buildLoginPath: jest.fn(() => "/login"),
  getAuthUser: jest.fn(),
  getCurrentPathWithQuery: jest.fn(() => "/dashboard"),
  isManualLogoutInProgress: jest.fn(() => false),
  LOGIN_REASON_AUTH_REQUIRED: "auth_required",
  needsOnboarding: jest.fn((authUser) => Boolean(authUser?.emailVerifiedAt) && authUser?.onboardingCompletedAt === null),
  needsProfileType: jest.fn((authUser) => authUser !== null && authUser.profileType == null),
}));

describe("route guards", () => {
  const router = { replace: jest.fn() };

  beforeEach(() => {
    router.replace.mockReset();
    (buildLoginPath as jest.Mock).mockReset();
    (buildLoginPath as jest.Mock).mockReturnValue("/login");
    (getAuthUser as jest.Mock).mockReset();
    (isManualLogoutInProgress as jest.Mock).mockReturnValue(false);
  });

  it("redirects logged-out users to login with an auth-required reason", () => {
    (getAuthUser as jest.Mock).mockReturnValue(null);

    expect(requireAuthenticatedOnboardedUser(router)).toBe(false);
    expect(buildLoginPath).toHaveBeenCalledWith({
      redirectTo: "/dashboard",
      reason: "auth_required",
    });
    expect(router.replace).toHaveBeenCalledWith("/login");
  });

  it("redirects verified users without onboarding to onboarding", () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      emailVerifiedAt: "2026-03-24T00:00:00Z",
      onboardingCompletedAt: null,
      profileType: null,
    });

    expect(requireAuthenticatedOnboardedUser(router)).toBe(false);
    expect(router.replace).toHaveBeenCalledWith("/onboarding");
  });

  it("allows verified users with completed onboarding to proceed", () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      emailVerifiedAt: "2026-03-24T00:00:00Z",
      onboardingCompletedAt: "2026-03-24T00:05:00Z",
      profileType: "STUDENT",
    });

    expect(requireVerifiedOnboardedUser(router)).toBe(true);
    expect(router.replace).not.toHaveBeenCalled();
  });

  it("redirects completed users without a profile type to onboarding", () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      emailVerifiedAt: "2026-03-24T00:00:00Z",
      onboardingCompletedAt: "2026-03-24T00:05:00Z",
      profileType: null,
    });

    expect(requireVerifiedOnboardedUser(router)).toBe(false);
    expect(router.replace).toHaveBeenCalledWith("/onboarding");
  });

  it("sends null-profile admins to onboarding before admin pages", () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "admin-1",
      emailVerifiedAt: "2026-03-24T00:00:00Z",
      onboardingCompletedAt: "2026-03-24T00:05:00Z",
      profileType: null,
      role: "ADMIN",
    });

    expect(requireAdminUser(router)).toBe(false);
    expect(router.replace).toHaveBeenCalledWith("/onboarding");
  });

  it("does not redirect when manual logout is in progress", () => {
    (getAuthUser as jest.Mock).mockReturnValue(null);
    (isManualLogoutInProgress as jest.Mock).mockReturnValue(true);

    redirectToLoginWithCurrentDestination(router);

    expect(router.replace).not.toHaveBeenCalled();
  });
});
