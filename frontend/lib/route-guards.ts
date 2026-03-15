import { buildLoginPath, getAuthUser, getCurrentPathWithQuery } from "./auth";

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
  if (!authUser.profileType) {
    router.replace("/onboarding");
    return false;
  }
  return true;
}
