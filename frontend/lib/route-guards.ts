import { buildLoginPath, getAuthUser, getCurrentPathWithQuery, needsOnboarding } from "./auth";

type RouterLike = {
  replace: (href: string) => void;
};

export function redirectToLoginWithCurrentDestination(router: RouterLike): void {
  router.replace(
    buildLoginPath({
      redirectTo: getCurrentPathWithQuery(),
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
  if (needsOnboarding(authUser)) {
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
  if (needsOnboarding(authUser)) {
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
  if (authUser.role !== "ADMIN") {
    router.replace("/dashboard");
    return false;
  }
  return true;
}
