import { getAuthUser } from "./auth";

type RouterLike = {
  replace: (href: string) => void;
};

type RequireVerifiedOnboardedUserOptions = {
  onUnauthenticated?: () => void;
};

export function requireVerifiedOnboardedUser(
  router: RouterLike,
  options?: RequireVerifiedOnboardedUserOptions,
): boolean {
  const authUser = getAuthUser();
  if (!authUser) {
    if (options?.onUnauthenticated) {
      options.onUnauthenticated();
    } else {
      router.replace("/login");
    }
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
