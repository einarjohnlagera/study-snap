import {
  buildLoginPath,
  getAuthUser,
  getCurrentPathWithQuery,
  isManualLogoutInProgress,
  LOGIN_REASON_AUTH_REQUIRED,
  needsOnboarding,
  needsProfileType,
} from "./auth";

type RouterLike = {
  replace: (href: string) => void;
};

export function redirectToLoginWithCurrentDestination(router: RouterLike): void {
  if (isManualLogoutInProgress()) {
    // Manual logout is in progress — the logout handler owns the navigation to /login.
    // Bail out to prevent the auth-change event from overwriting the clean logout redirect.
    return;
  }
  router.replace(
    buildLoginPath({
      redirectTo: getCurrentPathWithQuery(),
      reason: LOGIN_REASON_AUTH_REQUIRED,
    }),
  );
}

export function requireVerifiedOnboardedUser(
  router: RouterLike,
): boolean {
  const authUser = getAuthUser();
  if (!authUser) {
    redirectToLoginWithCurrentDestination(router);
    return false;
  }
  if (!authUser.emailVerifiedAt) {
    router.replace("/verify-email");
    return false;
  }
  if (needsOnboarding(authUser) || needsProfileType(authUser)) {
    router.replace("/onboarding");
    return false;
  }
  return true;
}

export function requireAuthenticatedOnboardedUser(
  router: RouterLike,
): boolean {
  const authUser = getAuthUser();
  if (!authUser) {
    redirectToLoginWithCurrentDestination(router);
    return false;
  }
  if (needsOnboarding(authUser) || needsProfileType(authUser)) {
    router.replace("/onboarding");
    return false;
  }
  return true;
}

export function requireAdminUser(
  router: RouterLike,
): boolean {
  const authUser = getAuthUser();
  if (!authUser) {
    redirectToLoginWithCurrentDestination(router);
    return false;
  }
  if (needsOnboarding(authUser) || needsProfileType(authUser)) {
    router.replace("/onboarding");
    return false;
  }
  if (authUser.role !== "ADMIN") {
    router.replace("/dashboard");
    return false;
  }
  return true;
}
