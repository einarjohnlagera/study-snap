"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import {
  clearLinkedLearnerInvitationIntentCookie,
  getLinkedLearnerInvitationIntentPath,
} from "@/lib/linked-learner-invitation-intent";

/** Resumes a link opened before login/signup without coupling the intent to onboarding itself. */
export function LinkedLearnerInvitationIntentConsumer() {
  const router = useRouter();

  useEffect(() => {
    const invitationPath = getLinkedLearnerInvitationIntentPath();
    if (!invitationPath) return;

    // One-shot before navigation, so StrictMode cannot schedule two resumptions.
    clearLinkedLearnerInvitationIntentCookie();
    router.replace(invitationPath);
  }, [router]);

  return null;
}

